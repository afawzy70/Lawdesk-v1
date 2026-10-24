-- LD-018 court reference data (docs/delivery/PHASE-4-ACCEPTANCE.md). Courts are deactivated, never deleted, so
-- proceedings that reference them keep their history. No courts are seeded: the real list is owner-supplied data.
create table court (
    id          uuid primary key default gen_random_uuid(),
    -- Stored upper-case, so uniqueness ignores letter case.
    code        varchar(32)  not null constraint court_code_unique unique
                             constraint court_code_format check (code ~ '^[A-Z0-9-]{2,32}$'),
    name_ar     varchar(200) not null constraint court_name_ar_visible check (length(btrim(name_ar)) > 0),
    name_en     varchar(200) not null constraint court_name_en_visible check (length(btrim(name_en)) > 0),
    level       varchar(20)  not null constraint court_level_known
                             check (level in ('FIRST_INSTANCE', 'APPEAL', 'CASSATION')),
    governorate varchar(100),
    active      boolean      not null default true,
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    created_by  uuid         not null,
    updated_at  timestamptz  not null default now(),
    updated_by  uuid         not null
);

create index court_level_active on court (level, active);
