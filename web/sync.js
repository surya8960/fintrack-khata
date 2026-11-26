'use strict';

/**
 * FinTrack Khata - web <-> Supabase sync (pure logic, no DOM access).
 *
 * Loaded by index.html in the browser (window.FinTrackSync) and by node:test via require().
 * Column names / types follow supabase/migrations/0001_initial_schema.sql exactly:
 *   - timestamps are epoch milliseconds (BIGINT)
 *   - money columns are numeric(14,2)
 *   - rows that would violate a CHECK / FK constraint are skipped (and reported) instead of
 *     making the whole sync fail forever.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.FinTrackSync = api;
})(typeof self !== 'undefined' ? self : this, function () {
  const PAGE_SIZE = 1000;   // Supabase/PostgREST returns at most 1000 rows per request
  const UPLOAD_CHUNK = 500;

  const ACCOUNT_TYPES = ['BANK', 'CASH', 'WALLET', 'CREDIT_CARD'];
  const TX_TYPES = ['INCOME', 'EXPENSE', 'TRANSFER'];
  const UDHAR_TYPES = ['GAVE', 'GOT'];
  const PAYMENT_TYPES = ['INTEREST_PAYMENT', 'PRINCIPAL_REPAYMENT'];

  // ------------------------------------------------------------------ helpers
  const money = (v) => Math.round((Number(v) + Number.EPSILON) * 100) / 100;
  const isNum = (v) => typeof v === 'number' && Number.isFinite(v);
  const str = (v) => (v === null || v === undefined ? '' : String(v));

  /** 'YYYY-MM-DD' -> epoch ms at local midnight (same as Android's startOfDay). */
  function ymdToMs(ymd) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(ymd));
    if (!m) return null;
    return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])).getTime();
  }

  /** epoch ms -> local 'YYYY-MM-DD'. */
  function msToYmd(ms) {
    const d = new Date(Number(ms));
    if (Number.isNaN(d.getTime())) return null;
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  }

  /** Accepts epoch ms (number/numeric string), 'YYYY-MM-DD' or ISO text. Returns ms or null. */
  function toMs(value) {
    if (value === null || value === undefined || value === '') return null;
    if (isNum(value)) return Math.trunc(value);
    const s = String(value).trim();
    if (/^\d+$/.test(s)) return Number(s);
    if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return ymdToMs(s);
    const parsed = Date.parse(s);
    return Number.isFinite(parsed) ? parsed : null;
  }

  const skip = (reason) => ({ skip: reason });
  /** Last-edit time of a local row (epoch ms). Rows never stamped count as 0 (= oldest). */
  const stamp = (row) => toMs(row && (row.updatedAt ?? row.updated_at)) ?? 0;
  const TOMBSTONES = 'sync_tombstones';

  // ------------------------------------------------------------------- tables
  // Parents first: foreign keys need customers/accounts to exist before their children.
  // `key` is the array name used by index.html.
  const TABLES = [
    {
      name: 'khata_customers', key: 'customers',
      toCloud(c) {
        const principal = Number(c.principal_amount);
        const rate = Number(c.monthly_interest_rate);
        if (!c.id || !str(c.name).trim()) return skip('missing id/name');
        if (!Number.isFinite(principal) || principal < 0) return skip('invalid principal');
        if (!Number.isFinite(rate) || rate < 0) return skip('invalid interest rate');
        const start = toMs(c.loan_start_date);
        if (start === null) return skip('missing loan start date');
        const paidI = Math.max(0, money(c.total_interest_paid || 0));
        const paidP = Math.max(0, money(c.total_principal_repaid || 0));
        const accrued = Math.max(0, money(c.total_accrued_interest || 0));
        return {
          id: c.id, name: str(c.name).trim(), phone: str(c.phone), address: str(c.address),
          principal_amount: money(principal), monthly_interest_rate: rate,
          loan_start_date: start, due_date: toMs(c.due_date),
          total_accrued_interest: accrued, total_interest_paid: paidI, total_principal_repaid: paidP,
          last_calculation_date: toMs(c.last_calculation_date),
          status: c.status || 'ACTIVE', notes: str(c.notes),
          created_at: toMs(c.created_at ?? c.createdAt) ?? Date.now(),
          updated_at: stamp(c)
        };
      },
      fromCloud: (r) => ({
        id: r.id, name: r.name, phone: r.phone, address: r.address,
        principal_amount: Number(r.principal_amount), monthly_interest_rate: Number(r.monthly_interest_rate),
        loan_start_date: msToYmd(r.loan_start_date),
        due_date: r.due_date == null ? null : msToYmd(r.due_date),
        total_accrued_interest: Number(r.total_accrued_interest),
        total_interest_paid: Number(r.total_interest_paid),
        total_principal_repaid: Number(r.total_principal_repaid),
        last_calculation_date: r.last_calculation_date == null ? null : msToYmd(r.last_calculation_date),
        status: r.status, notes: r.notes, created_at: Number(r.created_at),
        updatedAt: Number(r.updated_at) || 0
      })
    },
    {
      name: 'interest_accruals', key: 'accruals',
      toCloud(a, ctx) {
        if (!a.id) return skip('missing id');
        if (!ctx.customerIds.has(a.customer_id)) return skip('customer not found');
        const start = toMs(a.start_date), end = toMs(a.end_date);
        if (start === null || end === null || end <= start) return skip('invalid period');
        const calc = money(a.calculated_interest);
        const basis = money(a.principal_basis);
        const rate = Number(a.interest_rate);
        if (!Number.isFinite(calc) || calc < 0 || !Number.isFinite(basis) || !Number.isFinite(rate)) {
          return skip('invalid amounts');
        }
        return {
          id: a.id, customer_id: a.customer_id, start_date: start, end_date: end,
          interest_rate: rate, principal_basis: basis,
          completed_months: Math.trunc(a.completed_months || 0),
          partial_days: Math.trunc(a.partial_days || 0),
          days_in_partial_month: Math.trunc(a.days_in_partial_month || 30),
          monthly_interest_amount: money(a.monthly_interest_amount ?? (basis * rate) / 100),
          calculated_interest: calc, calculation_breakdown: str(a.calculation_breakdown),
          created_at: toMs(a.created_at) ?? end,
          updated_at: stamp(a)
        };
      },
      fromCloud: (r) => ({
        id: r.id, customer_id: r.customer_id,
        start_date: msToYmd(r.start_date), end_date: msToYmd(r.end_date),
        interest_rate: Number(r.interest_rate), principal_basis: Number(r.principal_basis),
        completed_months: r.completed_months, partial_days: r.partial_days,
        days_in_partial_month: r.days_in_partial_month,
        monthly_interest_amount: Number(r.monthly_interest_amount),
        calculated_interest: Number(r.calculated_interest),
        calculation_breakdown: r.calculation_breakdown, created_at: Number(r.created_at),
        updatedAt: Number(r.updated_at) || 0
      })
    },
    {
      name: 'loan_payments', key: 'payments',
      toCloud(p, ctx) {
        if (!p.id) return skip('missing id');
        if (!ctx.customerIds.has(p.customer_id)) return skip('customer not found');
        if (!PAYMENT_TYPES.includes(p.payment_type)) return skip('invalid payment type');
        const amount = money(p.amount);
        if (!Number.isFinite(amount) || amount <= 0) return skip('invalid amount');
        const date = toMs(p.payment_date);
        if (date === null) return skip('invalid payment date');
        return {
          id: p.id, customer_id: p.customer_id, payment_type: p.payment_type, amount,
          payment_date: date, payment_mode: str(p.payment_mode) || 'CASH', notes: str(p.notes),
          created_at: toMs(p.created_at) ?? date,
          updated_at: stamp(p)
        };
      },
      fromCloud: (r) => ({
        id: r.id, customer_id: r.customer_id, payment_type: r.payment_type,
        amount: Number(r.amount), payment_date: new Date(Number(r.payment_date)).toISOString(),
        payment_mode: r.payment_mode, notes: r.notes, created_at: Number(r.created_at),
        updatedAt: Number(r.updated_at) || 0
      })
    },
    {
      name: 'udhar_customers', key: 'udharCustomers',
      toCloud(c) {
        if (!c.id || !str(c.name).trim()) return skip('missing id/name');
        return {
          id: c.id, name: str(c.name).trim(), phone: str(c.phone), address: str(c.address),
          notes: str(c.notes), created_at: toMs(c.createdAt ?? c.created_at) ?? Date.now(),
          updated_at: stamp(c)
        };
      },
      fromCloud: (r) => ({
        id: r.id, name: r.name, phone: r.phone, address: r.address, notes: r.notes,
        createdAt: Number(r.created_at), updatedAt: Number(r.updated_at) || 0
      })
    },
    {
      name: 'udhar_entries', key: 'udharEntries',
      toCloud(e, ctx) {
        if (!e.id) return skip('missing id');
        const cid = e.customerId ?? e.customer_id;
        if (!ctx.udharIds.has(cid)) return skip('customer not found');
        if (!UDHAR_TYPES.includes(e.type)) return skip('invalid type');
        const amount = money(e.amount);
        if (!Number.isFinite(amount) || amount <= 0) return skip('invalid amount');
        const date = toMs(e.date);
        if (date === null) return skip('invalid date');
        return {
          id: e.id, customer_id: cid, amount, type: e.type, date,
          note: str(e.note), bill_number: str(e.billNumber ?? e.bill_number),
          created_at: toMs(e.createdAt ?? e.created_at) ?? date,
          updated_at: stamp(e)
        };
      },
      fromCloud: (r) => ({
        id: r.id, customerId: r.customer_id, amount: Number(r.amount), type: r.type,
        date: Number(r.date), note: r.note, billNumber: r.bill_number, createdAt: Number(r.created_at),
        updatedAt: Number(r.updated_at) || 0
      })
    },
    {
      name: 'accounts', key: 'accounts',
      toCloud(a) {
        if (!a.id || !str(a.name).trim()) return skip('missing id/name');
        const balance = money(a.balance || 0);
        if (!Number.isFinite(balance)) return skip('invalid balance');
        return {
          id: a.id, name: str(a.name).trim(),
          type: ACCOUNT_TYPES.includes(a.type) ? a.type : 'BANK',
          balance, account_number: str(a.accountNumber ?? a.account_number),
          color_hex: a.colorHex || a.color_hex || '#047857',
          updated_at: stamp(a)
        };
      },
      fromCloud: (r) => ({
        id: r.id, name: r.name, type: r.type, balance: Number(r.balance),
        accountNumber: r.account_number, colorHex: r.color_hex, updatedAt: Number(r.updated_at) || 0
      })
    },
    {
      name: 'transactions', key: 'transactions',
      toCloud(t, ctx) {
        if (!t.id) return skip('missing id');
        const accountId = t.accountId ?? t.account_id;
        if (!ctx.accountIds.has(accountId)) return skip('account not found');
        if (!TX_TYPES.includes(t.type)) return skip('invalid type');
        if (!str(t.title).trim()) return skip('missing title');
        const amount = money(t.amount);
        if (!Number.isFinite(amount) || amount <= 0) return skip('invalid amount');
        const target = t.targetAccountId ?? t.target_account_id ?? null;
        return {
          id: t.id, account_id: accountId,
          target_account_id: target && ctx.accountIds.has(target) ? target : null,
          title: str(t.title).trim(), amount, type: t.type,
          category: str(t.category) || 'General', note: str(t.note ?? t.notes),
          date_timestamp: toMs(t.dateTimestamp ?? t.date_timestamp) ?? Date.now(),
          updated_at: stamp(t)
        };
      },
      fromCloud: (r) => ({
        id: r.id, accountId: r.account_id, targetAccountId: r.target_account_id,
        title: r.title, amount: Number(r.amount), type: r.type, category: r.category,
        note: r.note, dateTimestamp: Number(r.date_timestamp), updatedAt: Number(r.updated_at) || 0
      })
    },
    {
      name: 'budgets', key: 'budgets',
      toCloud(b) {
        if (!b.id || !str(b.category).trim()) return skip('missing id/category');
        const limit = money(b.monthlyLimit ?? b.monthly_limit);
        if (!Number.isFinite(limit) || limit <= 0) return skip('invalid limit');
        const now = new Date();
        const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
        return {
          id: b.id, category: str(b.category).trim(), monthly_limit: limit,
          month_year: str(b.monthYear ?? b.month_year) || ym,
          updated_at: stamp(b)
        };
      },
      fromCloud: (r) => ({
        id: r.id, category: r.category, monthlyLimit: Number(r.monthly_limit), monthYear: r.month_year, updatedAt: Number(r.updated_at) || 0
      })
    },
    {
      name: 'goals', key: 'goals',
      toCloud(g) {
        if (!g.id || !str(g.name ?? g.title).trim()) return skip('missing id/name');
        const target = money(g.targetAmount ?? g.target_amount);
        const current = money(g.currentAmount ?? g.current_amount ?? 0);
        if (!Number.isFinite(target) || target <= 0) return skip('invalid target');
        if (!Number.isFinite(current) || current < 0) return skip('invalid current amount');
        return {
          id: g.id, name: str(g.name ?? g.title).trim(), target_amount: target, current_amount: current,
          deadline_days: Math.trunc(g.deadlineDays ?? g.deadline_days ?? 90) || 90,
          color_hex: g.colorHex || g.color_hex || '#4F46E5',
          updated_at: stamp(g)
        };
      },
      fromCloud: (r) => ({
        id: r.id, name: r.name, targetAmount: Number(r.target_amount),
        currentAmount: Number(r.current_amount), deadlineDays: r.deadline_days, colorHex: r.color_hex,
        updatedAt: Number(r.updated_at) || 0
      })
    }
  ];

  const TABLE_BY_NAME = Object.fromEntries(TABLES.map((t) => [t.name, t]));

  function emptyData() {
    return Object.fromEntries(TABLES.map((t) => [t.key, []]));
  }

  /** Errors worth retrying row-by-row (constraint / payload problems, not network/auth). */
  function isRowError(error) {
    const code = String((error && error.code) || '');
    const status = Number(error && error.status);
    return code.startsWith('23') || code.startsWith('22') || code.startsWith('PGRST') ||
      status === 400 || status === 409 || status === 422;
  }

  function describe(error) {
    return (error && (error.message || error.details)) || 'sync failed';
  }

  /**
   * Pushes (1) deletion tombstones the cloud does not know yet (and removes the cloud rows),
   * then (2) every valid local row that is new or newer than the cloud copy.
   *
   * @param tombstones   all local tombstones [{table, id, deletedAt}] (permanent, never cleared)
   * @param remote       result of pull(): { tombstones, stamps } so unchanged rows are not re-uploaded
   * Returns { uploaded, deleted (tombstones newly sent), skipped, errors }.
   * Never throws for per-table problems; network/auth errors are reported in `errors` and stop the run.
   */
  async function push({ client, userId, data, tombstones = [], remote = null }) {
    const report = { uploaded: 0, deleted: [], skipped: [], errors: [] };
    const knownDead = new Set(((remote && remote.tombstones) || []).map((d) => d.id));
    const fresh = tombstones.filter((d) => d && d.id && !knownDead.has(d.id));

    if (fresh.length) {
      const rows = fresh.map((d) => ({
        id: d.id, table_name: d.table, deleted_at: toMs(d.deletedAt) ?? Date.now(), user_id: userId
      }));
      for (let i = 0; i < rows.length; i += UPLOAD_CHUNK) {
        const { error } = await client.from(TOMBSTONES)
          .upsert(rows.slice(i, i + UPLOAD_CHUNK), { onConflict: 'user_id,id' });
        if (error) { report.errors.push({ table: TOMBSTONES, message: describe(error) }); return report; }
      }
      for (const d of fresh) {
        if (!TABLE_BY_NAME[d.table]) { report.deleted.push(d); continue; } // unknown table: nothing to delete
        const { error } = await client.from(d.table).delete().eq('id', d.id);
        if (error) { report.errors.push({ table: d.table, message: describe(error) }); return report; }
        report.deleted.push(d);
      }
    }

    const dead = new Set(tombstones.map((d) => d.id));
    const ctx = {
      customerIds: new Set((data.customers || []).map((x) => x.id)),
      udharIds: new Set((data.udharCustomers || []).map((x) => x.id)),
      accountIds: new Set((data.accounts || []).map((x) => x.id))
    };

    for (const table of TABLES) {
      const stamps = (remote && remote.stamps && remote.stamps[table.name]) || null;
      const rows = [];
      for (const local of data[table.key] || []) {
        if (local && dead.has(local.id)) continue;               // deleted: never upload again
        const out = table.toCloud(local, ctx);
        if (out && out.skip) { report.skipped.push({ table: table.name, id: local && local.id, reason: out.skip }); continue; }
        if (stamps && stamps.has(out.id) && out.updated_at <= stamps.get(out.id)) continue; // cloud copy is current
        rows.push({ ...out, user_id: userId });
      }
      for (let i = 0; i < rows.length; i += UPLOAD_CHUNK) {
        const chunk = rows.slice(i, i + UPLOAD_CHUNK);
        const { error } = await client.from(table.name).upsert(chunk, { onConflict: 'id' });
        if (!error) { report.uploaded += chunk.length; continue; }
        if (!isRowError(error)) {
          report.errors.push({ table: table.name, message: describe(error) });
          return report;
        }
        // One bad row must not block the rest: retry individually and report the culprits.
        for (const row of chunk) {
          const single = await client.from(table.name).upsert([row], { onConflict: 'id' });
          if (!single.error) report.uploaded += 1;
          else report.skipped.push({ table: table.name, id: row.id, reason: describe(single.error) });
        }
      }
    }
    return report;
  }

  /**
   * Downloads every table (paginated past the 1000-row API cap) plus the tombstones.
   * Returns { data (web shape), tombstones: [{table,id,deletedAt}], stamps: {table: Map(id -> updated_at)} }.
   */
  async function pull({ client, userId }) {
    const data = emptyData();
    const stamps = {};
    for (const table of TABLES) {
      stamps[table.name] = new Map();
      for (let from = 0; ; from += PAGE_SIZE) {
        const { data: rows, error } = await client.from(table.name).select('*')
          .eq('user_id', userId).order('id', { ascending: true }).range(from, from + PAGE_SIZE - 1);
        if (error) throw new Error(`${table.name}: ${describe(error)}`);
        for (const row of rows || []) {
          data[table.key].push(table.fromCloud(row));
          stamps[table.name].set(row.id, Number(row.updated_at) || 0);
        }
        if (!rows || rows.length < PAGE_SIZE) break;
      }
    }
    const tombstones = [];
    for (let from = 0; ; from += PAGE_SIZE) {
      const { data: rows, error } = await client.from(TOMBSTONES).select('*')
        .eq('user_id', userId).order('id', { ascending: true }).range(from, from + PAGE_SIZE - 1);
      if (error) throw new Error(`${TOMBSTONES}: ${describe(error)}`);
      for (const r of rows || []) tombstones.push({ table: r.table_name, id: r.id, deletedAt: Number(r.deleted_at) || 0 });
      if (!rows || rows.length < PAGE_SIZE) break;
    }
    return { data, tombstones, stamps };
  }

  /** Union of tombstones, one per id (earliest deletion time kept). */
  function mergeTombstones(a = [], b = []) {
    const byId = new Map();
    for (const d of [...a, ...b]) {
      if (!d || !d.id) continue;
      const have = byId.get(d.id);
      if (!have || (Number(d.deletedAt) || 0) < (Number(have.deletedAt) || 0)) byId.set(d.id, d);
    }
    return [...byId.values()];
  }

  /**
   * Pure merge. Per id the NEWEST updatedAt wins (local on a tie); cloud-only rows are added;
   * tombstoned ids are removed and never come back; children of vanished parents are dropped;
   * duplicate ids collapse to one row.
   * Returns { merged, tombstones, added, updated, removed }.
   */
  function mergeRemote(local, remoteData, tombstones = []) {
    const dead = new Set(tombstones.map((d) => d.id));
    const merged = {};
    let added = 0, updated = 0, removed = 0;
    for (const table of TABLES) {
      const result = new Map();
      for (const row of Array.isArray(local[table.key]) ? local[table.key] : []) {
        if (!row || !row.id) continue;
        if (dead.has(row.id)) { removed += 1; continue; }
        const have = result.get(row.id);
        if (!have || stamp(row) > stamp(have)) result.set(row.id, row);
      }
      for (const row of remoteData[table.key] || []) {
        if (dead.has(row.id)) continue;
        const mine = result.get(row.id);
        if (!mine) { result.set(row.id, row); added += 1; }
        else if (stamp(row) > stamp(mine)) { result.set(row.id, row); updated += 1; }
      }
      merged[table.key] = [...result.values()];
    }
    // Children must not outlive their parent (a customer/account deleted on another device).
    const keep = (rows, parents, fk) => rows.filter((r) => {
      const ok = parents.has(r[fk[0]] ?? r[fk[1]]);
      if (!ok) removed += 1;
      return ok;
    });
    const loanIds = new Set(merged.customers.map((x) => x.id));
    const udharIds = new Set(merged.udharCustomers.map((x) => x.id));
    const accountIds = new Set(merged.accounts.map((x) => x.id));
    merged.accruals = keep(merged.accruals, loanIds, ['customer_id', 'customerId']);
    merged.payments = keep(merged.payments, loanIds, ['customer_id', 'customerId']);
    merged.udharEntries = keep(merged.udharEntries, udharIds, ['customerId', 'customer_id']);
    merged.transactions = keep(merged.transactions, accountIds, ['accountId', 'account_id']);
    return { merged, tombstones, added, updated, removed };
  }

  return { TABLES, TOMBSTONES, ymdToMs, msToYmd, toMs, push, pull, mergeRemote, mergeTombstones, emptyData, PAGE_SIZE };
});
