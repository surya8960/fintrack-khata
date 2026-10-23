'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const S = require('../sync');

// Column lists copied from supabase/migrations/0001_initial_schema.sql (NOT NULL without default = required).
const SCHEMA = {
  khata_customers: { cols: ['id','user_id','name','phone','address','principal_amount','monthly_interest_rate','loan_start_date','due_date','total_accrued_interest','total_interest_paid','total_principal_repaid','last_calculation_date','status','notes','created_at'], required: ['id','name','principal_amount','loan_start_date','created_at'] },
  interest_accruals: { cols: ['id','user_id','customer_id','start_date','end_date','interest_rate','principal_basis','completed_months','partial_days','days_in_partial_month','monthly_interest_amount','calculated_interest','calculation_breakdown','created_at'], required: ['id','customer_id','start_date','end_date','interest_rate','principal_basis','monthly_interest_amount','calculated_interest','created_at'] },
  loan_payments: { cols: ['id','user_id','customer_id','payment_type','amount','payment_date','payment_mode','notes','created_at'], required: ['id','customer_id','payment_type','amount','payment_date','created_at'] },
  udhar_customers: { cols: ['id','user_id','name','phone','address','notes','created_at'], required: ['id','name','created_at'] },
  udhar_entries: { cols: ['id','user_id','customer_id','amount','type','date','note','bill_number','created_at'], required: ['id','customer_id','amount','type','date','created_at'] },
  accounts: { cols: ['id','user_id','name','type','balance','account_number','color_hex','updated_at'], required: ['id','name','updated_at'] },
  transactions: { cols: ['id','user_id','account_id','target_account_id','title','amount','type','category','note','date_timestamp'], required: ['id','account_id','title','amount','type','date_timestamp'] },
  budgets: { cols: ['id','user_id','category','monthly_limit','month_year'], required: ['id','category','monthly_limit'] },
  goals: { cols: ['id','user_id','name','target_amount','current_amount','deadline_days','color_hex'], required: ['id','name','target_amount'] }
};

/** Minimal in-memory PostgREST fake that enforces the schema columns and a few CHECKs. */
function fakeClient({ failTable = null, failCode = '23514' } = {}) {
  const db = Object.fromEntries(Object.keys(SCHEMA).map(t => [t, new Map()]));
  const calls = [];
  const check = (table, row) => {
    const def = SCHEMA[table];
    for (const k of Object.keys(row)) if (!def.cols.includes(k)) return { code: 'PGRST204', message: `Could not find the '${k}' column of '${table}'` };
    for (const k of def.required) if (row[k] === undefined || row[k] === null) return { code: '23502', message: `null value in column "${k}" of relation "${table}"` };
    if (table === 'budgets' && !(row.monthly_limit > 0)) return { code: '23514', message: 'budgets check violated' };
    if (table === 'goals' && !(row.target_amount > 0)) return { code: '23514', message: 'goals check violated' };
    if (table === failTable && row.__bad) return { code: failCode, message: 'forced failure' };
    return null;
  };
  return {
    db, calls,
    from(table) {
      return {
        upsert: async (rows) => {
          calls.push(['upsert', table, rows.length]);
          for (const r of rows) { const e = check(table, r); if (e) return { error: e }; }
          rows.forEach(r => db[table].set(r.id, r));
          return { error: null };
        },
        delete: () => ({ eq: async (_c, id) => { calls.push(['delete', table, id]); db[table].delete(id); return { error: null }; } }),
        select: () => {
          const q = { _from: 0, _to: 999 };
          q.eq = () => q; q.order = () => q;
          q.range = async (from, to) => { calls.push(['range', table, from, to]); return { data: [...db[table].values()].slice(from, to + 1), error: null }; };
          return q;
        }
      };
    }
  };
}

const sampleData = () => ({
  customers: [{ id: 'c1', name: 'Test Borrower', phone: '1', principal_amount: 10000, monthly_interest_rate: 5, loan_start_date: '2026-09-15', total_accrued_interest: 500, total_interest_paid: 0, total_principal_repaid: 0, last_calculation_date: '2026-10-15', notes: '' }],
  accruals: [{ id: 'a1', customer_id: 'c1', start_date: '2026-09-15', end_date: '2026-10-15', interest_rate: 5, principal_basis: 10000, completed_months: 1, partial_days: 0, days_in_partial_month: 30, calculated_interest: 500, calculation_breakdown: 'x' }],
  payments: [{ id: 'p1', customer_id: 'c1', payment_type: 'INTEREST_PAYMENT', amount: 100, payment_date: '2026-10-16T10:00:00.000Z', payment_mode: 'UPI', notes: '' }],
  udharCustomers: [{ id: 'u1', name: 'Amit Sharma', phone: '', address: '', notes: '', createdAt: 1700000000000 }],
  udharEntries: [{ id: 'e1', customerId: 'u1', amount: 250, type: 'GAVE', date: 1700000000000, note: '', billNumber: 'B1' }],
  accounts: [{ id: 'ac1', name: 'Cash', type: 'CASH', balance: 100, accountNumber: '' }],
  transactions: [{ id: 't1', accountId: 'ac1', targetAccountId: null, title: 'Tea', amount: 20, type: 'EXPENSE', category: 'Food', note: '', dateTimestamp: 1700000000000 }],
  budgets: [{ id: 'b1', category: 'Food', monthlyLimit: 500 }],
  goals: [{ id: 'g1', name: 'Laptop', targetAmount: 50000, currentAmount: 1000, deadlineDays: 90 }]
});

test('every local table uploads with schema-valid columns', async () => {
  const client = fakeClient();
  const report = await S.push({ client, userId: 'user-1', data: sampleData() });
  assert.deepStrictEqual(report.errors, []);
  assert.deepStrictEqual(report.skipped, []);
  assert.strictEqual(report.uploaded, 9);
  for (const table of Object.keys(SCHEMA)) assert.strictEqual(client.db[table].size, 1, table);
  assert.strictEqual(client.db.khata_customers.get('c1').loan_start_date, S.ymdToMs('2026-09-15'));
});

test('push -> pull round trip keeps the web shape', async () => {
  const client = fakeClient();
  const data = sampleData();
  await S.push({ client, userId: 'user-1', data });
  const remote = await S.pull({ client, userId: 'user-1' });
  assert.strictEqual(remote.customers[0].loan_start_date, '2026-09-15');
  assert.strictEqual(remote.accruals[0].end_date, '2026-10-15');
  assert.strictEqual(remote.budgets[0].monthlyLimit, 500);
  assert.strictEqual(remote.goals[0].targetAmount, 50000);
  assert.strictEqual(remote.udharEntries[0].billNumber, 'B1');
  assert.strictEqual(remote.payments[0].payment_date, '2026-10-16T10:00:00.000Z');
});

test('invalid rows are skipped and reported, valid rows still sync', async () => {
  const client = fakeClient();
  const data = sampleData();
  data.budgets.push({ id: 'b2', category: 'Zero', monthlyLimit: 0 });
  data.goals.push({ id: 'g2', name: 'Neg', targetAmount: 100, currentAmount: -5 });
  data.accruals.push({ id: 'a2', customer_id: 'ghost', start_date: '2026-01-01', end_date: '2026-02-01', interest_rate: 5, principal_basis: 1, calculated_interest: 1 });
  data.udharEntries.push({ id: 'e2', customerId: 'u1', amount: 0, type: 'GAVE', date: 1 });
  const report = await S.push({ client, userId: 'user-1', data });
  assert.deepStrictEqual(report.errors, []);
  assert.deepStrictEqual(report.skipped.map(s => s.id).sort(), ['a2', 'b2', 'e2', 'g2']);
  assert.strictEqual(report.uploaded, 9);
});

test('a row rejected by the server only drops that row', async () => {
  const client = fakeClient();
  const data = sampleData();
  const origFrom = client.from.bind(client);
  client.from = (t) => {
    const api = origFrom(t);
    const up = api.upsert;
    api.upsert = async (rows, o) => (t === 'budgets' && rows.some(r => r.id === 'bad')) && rows.length > 0
      ? (rows.length > 1 ? { error: { code: '23514', message: 'boom' } } : (rows[0].id === 'bad' ? { error: { code: '23514', message: 'boom' } } : up(rows, o)))
      : up(rows, o);
    return api;
  };
  data.budgets.push({ id: 'bad', category: 'X', monthlyLimit: 10 });
  const report = await S.push({ client, userId: 'u', data });
  assert.deepStrictEqual(report.skipped.map(s => s.id), ['bad']);
  assert.ok(client.db.budgets.has('b1'));
});

test('network/auth style errors stop the run and are reported, not retried per row', async () => {
  const client = fakeClient();
  client.from = () => ({ upsert: async () => ({ error: { status: 503, message: 'Service Unavailable' } }), delete: () => ({ eq: async () => ({ error: null }) }) });
  const report = await S.push({ client, userId: 'u', data: sampleData() });
  assert.strictEqual(report.errors.length, 1);
  assert.strictEqual(report.uploaded, 0);
});

test('pending deletions are replayed and reported as done', async () => {
  const client = fakeClient();
  await S.push({ client, userId: 'u', data: sampleData() });
  const data = sampleData(); data.budgets = [];
  const report = await S.push({ client, userId: 'u', data, pendingDeletes: [{ table: 'budgets', id: 'b1' }] });
  assert.deepStrictEqual(report.deleted, [{ table: 'budgets', id: 'b1' }]);
  assert.strictEqual(client.db.budgets.size, 0);
});

test('pull pages past the 1000 row API limit', async () => {
  const client = fakeClient();
  for (let i = 0; i < 2300; i++) {
    client.db.budgets.set('b' + String(i).padStart(5, '0'), { id: 'b' + String(i).padStart(5, '0'), category: 'C', monthly_limit: 1, month_year: '2026-10' });
  }
  const remote = await S.pull({ client, userId: 'u' });
  assert.strictEqual(remote.budgets.length, 2300);
  assert.strictEqual(client.calls.filter(c => c[0] === 'range' && c[1] === 'budgets').length, 3);
});

test('mergeRemote: local wins, cloud-only rows added, locally deleted rows not resurrected', () => {
  const local = S.emptyData();
  local.budgets = [{ id: 'b1', category: 'Local', monthlyLimit: 1 }];
  const remote = S.emptyData();
  remote.budgets = [{ id: 'b1', category: 'Cloud', monthlyLimit: 2 }, { id: 'b2', category: 'New', monthlyLimit: 3 }, { id: 'b3', category: 'Deleted', monthlyLimit: 4 }];
  const { merged, added } = S.mergeRemote(local, remote, [{ table: 'budgets', id: 'b3' }]);
  assert.deepStrictEqual(merged.budgets.map(b => b.id), ['b1', 'b2']);
  assert.strictEqual(merged.budgets[0].category, 'Local');
  assert.strictEqual(added, 1);
});

test('index.html no longer deletes real customers by name', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
  assert.ok(!/includes\(x\.name\)/.test(html), 'name based demo cleanup must not exist');
  assert.ok(html.includes('./sync.js'));
});
