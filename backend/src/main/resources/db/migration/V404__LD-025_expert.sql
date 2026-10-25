-- LD-025 expert referrals and sessions (docs/delivery/PHASE-5-ACCEPTANCE.md): the expert registry (reference data,
-- like courts), the referral of a proceeding to an expert (ندب خبير) and the expert's sessions. Actor columns hold the
-- user id without a foreign key (architecture/CONTRACTS.md §2).
create table expert (
    id         uuid primary key default gen_random_uuid(),
    name       varchar(200) not null constraint expert_name_visible check (length(btrim(name)) > 0),
    specialty  varchar(100) not null constraint expert_specialty_visible check (length(btrim(specialty)) > 0),
    office     varchar(200) constraint expert_office_visible check (length(btrim(office)) > 0),
    phone      varchar(32)  constraint expert_phone_format check (phone ~ '^[0-9 +()-]+$'),
    active     boolean      not null default true,
    version    bigint       not null default 0,
    created_at timestamptz  not null default now(),
    created_by uuid         not null,
    updated_at timestamptz  not null default now(),
    updated_by uuid         not null
);

create index expert_name on expert (lower(name));

create table expert_referral (
    id                    uuid primary key default gen_random_uuid(),
    case_id               uuid          not null references legal_case (id),
    proceeding_id         uuid          not null references proceeding (id),
    expert_id             uuid          not null references expert (id),
    referral_date         date          not null,
    mission               varchar(4000) constraint expert_referral_mission_visible check (length(btrim(mission)) > 0),
    report_due_date       date,
    status                varchar(20)   not null default 'OPEN' constraint expert_referral_status_known
        check (status in ('OPEN', 'REPORT_SUBMITTED', 'CANCELLED')),
    report_submitted_date date,
    version               bigint        not null default 0,
    created_at            timestamptz   not null default now(),
    created_by            uuid          not null,
    updated_at            timestamptz   not null default now(),
    updated_by            uuid          not null,
    constraint expert_referral_report_when_submitted
        check ((status = 'REPORT_SUBMITTED') = (report_submitted_date is not null)),
    constraint expert_referral_report_due_after_referral check (report_due_date >= referral_date),
    constraint expert_referral_report_after_referral check (report_submitted_date >= referral_date)
);

create index expert_referral_case on expert_referral (case_id, referral_date);
create index expert_referral_expert on expert_referral (expert_id, status);

-- As for hearings (V401): the referral's case is always its proceeding's case.
create function expert_referral_case_matches_proceeding() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from proceeding p where p.id = new.proceeding_id and p.case_id = new.case_id) then
        raise exception 'expert referral case does not match the case of its proceeding'
            using errcode = 'check_violation', constraint = 'expert_referral_case_matches_proceeding';
    end if;
    return new;
end $$;

create trigger expert_referral_case_matches_proceeding
    before insert or update of case_id, proceeding_id on expert_referral
    for each row execute function expert_referral_case_matches_proceeding();

create table expert_session (
    id           uuid primary key default gen_random_uuid(),
    referral_id  uuid          not null references expert_referral (id),
    session_date date          not null,
    session_time time,
    location     varchar(200)  constraint expert_session_location_visible check (length(btrim(location)) > 0),
    notes        varchar(2000) constraint expert_session_notes_visible check (length(btrim(notes)) > 0),
    created_at   timestamptz   not null default now(),
    created_by   uuid          not null
);

create index expert_session_referral on expert_session (referral_id, session_date);
