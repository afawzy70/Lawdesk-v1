-- LD-012: the legal case and its timeline (docs/delivery/PHASE-3-ACCEPTANCE.md). Workstream B owns these tables;
-- Workstream C references legal_case(id) and writes timeline entries only through com.lawdesk.cases.CaseLookup.
-- Actor columns hold the user id without a foreign key (architecture/CONTRACTS.md §2).
create table legal_case (
    id           uuid primary key default gen_random_uuid(),
    -- Generated LD-YYYY-NNNNN, or supplied for files migrated from elsewhere; never changed afterwards.
    reference_no varchar(40)   not null check (reference_no ~ '^[A-Za-z0-9][A-Za-z0-9/._-]*$'),
    title        varchar(200)  not null check (length(btrim(title)) > 0),
    case_type    varchar(32)   not null
        check (case_type in ('CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE')),
    subject      varchar(4000),
    opened_on    date          not null,
    status       varchar(32)   not null default 'OPEN'
        check (status in ('OPEN', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'ARCHIVED')),
    version      bigint        not null default 0,
    created_at   timestamptz   not null default now(),
    created_by   uuid          not null,
    updated_at   timestamptz   not null default now(),
    updated_by   uuid          not null
);

create unique index legal_case_reference_no_key on legal_case (lower(reference_no));

-- Last generated reference number per calendar year. Incremented with an upsert in the creating transaction, so
-- numbers are gapless and concurrent creations wait for each other instead of colliding.
create table case_reference_counter (
    year        integer primary key check (year between 2000 and 9999),
    last_number integer not null check (last_number > 0)
);

-- The case timeline (LD-014): append-only. seq orders events written in the same transaction, which share
-- occurred_at; the foreign key keeps a case with history from being deleted.
create table case_event (
    id          uuid primary key default gen_random_uuid(),
    seq         bigint generated always as identity unique,
    case_id     uuid         not null references legal_case (id),
    type        varchar(64)  not null check (type ~ '^[A-Z][A-Z0-9_]*$'),
    summary     varchar(500) not null check (length(btrim(summary)) > 0),
    occurred_at timestamptz  not null default now(),
    created_by  uuid         not null
);

create index case_event_case_seq_idx on case_event (case_id, seq desc);
