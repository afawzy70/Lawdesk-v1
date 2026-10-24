-- LD-019…LD-021 proceedings: one row per stage of a case before a court (docs/delivery/PHASE-4-ACCEPTANCE.md).
-- case_id refers to legal_case (Workstream B). The foreign key is added by the first Phase 4 migration created after
-- B's V200 is on develop (architecture/CONTRACTS.md §2, §5); until then the application checks the case through
-- com.lawdesk.cases.CaseLookup.
create table proceeding (
    id                   uuid primary key default gen_random_uuid(),
    case_id              uuid        not null,
    stage                varchar(20) not null constraint proceeding_stage_known
                                     check (stage in ('FIRST_INSTANCE', 'APPEAL', 'CASSATION')),
    court_id             uuid        not null references court (id),
    -- رقم الدعوى / السنة at the court.
    court_case_number    varchar(32) not null constraint proceeding_number_visible
                                     check (length(btrim(court_case_number)) > 0),
    court_case_year      integer     not null constraint proceeding_year_range
                                     check (court_case_year between 1900 and 2100),
    filing_date          date        not null,
    parent_proceeding_id uuid references proceeding (id),
    version              bigint      not null default 0,
    created_at           timestamptz not null default now(),
    created_by           uuid        not null,
    updated_at           timestamptz not null default now(),
    updated_by           uuid        not null,
    -- A first-instance proceeding starts the ladder; an appeal or a cassation always names the proceeding it attacks.
    constraint proceeding_parent_by_stage check ((stage = 'FIRST_INSTANCE') = (parent_proceeding_id is null)),
    constraint proceeding_not_own_parent check (parent_proceeding_id <> id)
);

-- A number and year identify one proceeding per court and stage (ASSUMPTION in the acceptance file).
create unique index proceeding_court_number_unique
    on proceeding (court_id, court_case_year, lower(court_case_number), stage);
create index proceeding_case on proceeding (case_id);
create index proceeding_parent on proceeding (parent_proceeding_id);
