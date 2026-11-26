-- =============================================================================
-- FinTrack Khata - Supabase schema
--
-- * Every row belongs to exactly one user (user_id defaults to auth.uid()).
-- * Row Level Security is ON for every table; only the owner can read/write.
--   There are NO public/anonymous policies.
-- * Column names are the snake_case form of the Android model fields
--   (the app maps them automatically). Timestamps are epoch milliseconds (BIGINT)
--   exactly as the app stores them. Primary keys are TEXT because they are
--   client-generated UUID strings.
--
-- Run in: Supabase Dashboard -> SQL Editor (or `supabase db push`).
-- =============================================================================

-- ------------------------------------------------------------ interest loans
create table if not exists public.khata_customers (
    id                       text primary key,
    user_id                  uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name                     text not null,
    phone                    text not null default '',
    address                  text not null default '',
    principal_amount         numeric(14,2) not null check (principal_amount >= 0),
    monthly_interest_rate    numeric(6,3)  not null default 5 check (monthly_interest_rate >= 0),
    loan_start_date          bigint not null,
    due_date                 bigint,
    total_accrued_interest   numeric(14,2) not null default 0 check (total_accrued_interest >= 0),
    total_interest_paid      numeric(14,2) not null default 0 check (total_interest_paid >= 0),
    total_principal_repaid   numeric(14,2) not null default 0 check (total_principal_repaid >= 0),
    last_calculation_date    bigint,
    status                   text not null default 'ACTIVE',
    notes                    text not null default '',
    created_at               bigint not null
);

create table if not exists public.interest_accruals (
    id                       text primary key,
    user_id                  uuid not null default auth.uid() references auth.users(id) on delete cascade,
    customer_id              text not null references public.khata_customers(id) on delete cascade,
    start_date               bigint not null,
    end_date                 bigint not null,
    interest_rate            numeric(6,3)  not null,
    principal_basis          numeric(14,2) not null,
    completed_months         int not null default 0,
    partial_days             int not null default 0,
    days_in_partial_month    int not null default 30,
    monthly_interest_amount  numeric(14,2) not null,
    calculated_interest      numeric(14,2) not null,
    calculation_breakdown    text not null default '',
    created_at               bigint not null,
    check (end_date > start_date)
);

create table if not exists public.loan_payments (
    id            text primary key,
    user_id       uuid not null default auth.uid() references auth.users(id) on delete cascade,
    customer_id   text not null references public.khata_customers(id) on delete cascade,
    payment_type  text not null check (payment_type in ('INTEREST_PAYMENT', 'PRINCIPAL_REPAYMENT')),
    amount        numeric(14,2) not null check (amount > 0),
    payment_date  bigint not null,
    payment_mode  text not null default 'CASH',
    notes         text not null default '',
    created_at    bigint not null
);

-- --------------------------------------------------------------- udhar khata
create table if not exists public.udhar_customers (
    id          text primary key,
    user_id     uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name        text not null,
    phone       text not null default '',
    address     text not null default '',
    notes       text not null default '',
    created_at  bigint not null
);

create table if not exists public.udhar_entries (
    id           text primary key,
    user_id      uuid not null default auth.uid() references auth.users(id) on delete cascade,
    customer_id  text not null references public.udhar_customers(id) on delete cascade,
    amount       numeric(14,2) not null check (amount > 0),
    type         text not null check (type in ('GAVE', 'GOT')),
    date         bigint not null,
    note         text not null default '',
    bill_number  text not null default '',
    created_at   bigint not null
);

-- ---------------------------------------------------------- personal finance
create table if not exists public.accounts (
    id              text primary key,
    user_id         uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name            text not null,
    type            text not null default 'BANK' check (type in ('BANK', 'CASH', 'WALLET', 'CREDIT_CARD')),
    balance         numeric(14,2) not null default 0,
    account_number  text not null default '',
    color_hex       text not null default '#047857',
    updated_at      bigint not null
);

create table if not exists public.transactions (
    id                 text primary key,
    user_id            uuid not null default auth.uid() references auth.users(id) on delete cascade,
    account_id         text not null references public.accounts(id) on delete cascade,
    target_account_id  text references public.accounts(id) on delete set null,
    title              text not null,
    amount             numeric(14,2) not null check (amount > 0),
    type               text not null check (type in ('INCOME', 'EXPENSE', 'TRANSFER')),
    category           text not null default 'General',
    note               text not null default '',
    date_timestamp     bigint not null
);

create table if not exists public.budgets (
    id             text primary key,
    user_id        uuid not null default auth.uid() references auth.users(id) on delete cascade,
    category       text not null,
    monthly_limit  numeric(14,2) not null check (monthly_limit > 0),
    month_year     text not null default ''
);

create table if not exists public.goals (
    id              text primary key,
    user_id         uuid not null default auth.uid() references auth.users(id) on delete cascade,
    name            text not null,
    target_amount   numeric(14,2) not null check (target_amount > 0),
    current_amount  numeric(14,2) not null default 0 check (current_amount >= 0),
    deadline_days   int not null default 90,
    color_hex       text not null default '#4F46E5'
);

-- ------------------------------------------------------------------- indexes
create index if not exists idx_khata_customers_user  on public.khata_customers (user_id);
create index if not exists idx_interest_accruals_cust on public.interest_accruals (customer_id);
create index if not exists idx_interest_accruals_user on public.interest_accruals (user_id);
create index if not exists idx_loan_payments_cust     on public.loan_payments (customer_id);
create index if not exists idx_loan_payments_user     on public.loan_payments (user_id);
create index if not exists idx_udhar_customers_user   on public.udhar_customers (user_id);
create index if not exists idx_udhar_entries_cust     on public.udhar_entries (customer_id);
create index if not exists idx_udhar_entries_user     on public.udhar_entries (user_id);
create index if not exists idx_accounts_user          on public.accounts (user_id);
create index if not exists idx_transactions_account   on public.transactions (account_id);
create index if not exists idx_transactions_user      on public.transactions (user_id);
create index if not exists idx_budgets_user           on public.budgets (user_id);
create index if not exists idx_goals_user             on public.goals (user_id);

-- ------------------------------------------------- Row Level Security (owner)
do $$
declare
    t text;
begin
    foreach t in array array[
        'khata_customers', 'interest_accruals', 'loan_payments',
        'udhar_customers', 'udhar_entries',
        'accounts', 'transactions', 'budgets', 'goals'
    ] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('drop policy if exists "owner full access" on public.%I', t);
        execute format(
            'create policy "owner full access" on public.%I
               for all to authenticated
               using ((select auth.uid()) = user_id)
               with check ((select auth.uid()) = user_id)', t);
        -- anonymous (not signed in) visitors get nothing
        execute format('revoke all on public.%I from anon', t);
    end loop;
end $$;
