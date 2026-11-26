-- =============================================================================
-- FinTrack Khata - sync fix: no duplicates, no old data coming back
--
-- 1. updated_at on every table  -> "newest edit wins" instead of "whoever syncs last wins".
-- 2. sync_tombstones            -> every delete is remembered forever, so a device that still
--                                  has the old row can never upload it again.
--
-- Run ONCE in: Supabase Dashboard -> SQL Editor.  Safe to re-run.
-- =============================================================================

alter table public.khata_customers   add column if not exists updated_at bigint not null default 0;
alter table public.interest_accruals add column if not exists updated_at bigint not null default 0;
alter table public.loan_payments     add column if not exists updated_at bigint not null default 0;
alter table public.udhar_customers   add column if not exists updated_at bigint not null default 0;
alter table public.udhar_entries     add column if not exists updated_at bigint not null default 0;
alter table public.accounts          add column if not exists updated_at bigint not null default 0;
alter table public.transactions      add column if not exists updated_at bigint not null default 0;
alter table public.budgets           add column if not exists updated_at bigint not null default 0;
alter table public.goals             add column if not exists updated_at bigint not null default 0;

create table if not exists public.sync_tombstones (
    user_id     uuid   not null default auth.uid() references auth.users(id) on delete cascade,
    id          text   not null,
    table_name  text   not null,
    deleted_at  bigint not null,
    primary key (user_id, id)
);

alter table public.sync_tombstones enable row level security;
drop policy if exists "owner full access" on public.sync_tombstones;
create policy "owner full access" on public.sync_tombstones
    for all to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);
revoke all on public.sync_tombstones from anon;

-- Remove the fixed-id demo rows that older app versions created (they kept coming back).
delete from public.khata_customers   where id in ('c1111111-1111-1111-1111-111111111111','c2222222-2222-2222-2222-222222222222');
delete from public.udhar_customers   where id in ('uc-001','uc-002','uc-003');
delete from public.accounts          where id in ('acc-hdfc-01','acc-sbi-02','acc-cash-03');
delete from public.budgets           where id in ('bgt-01','bgt-02','bgt-03');
delete from public.goals             where id in ('gol-01','gol-02');
-- (children of the rows above are removed by ON DELETE CASCADE)
