-- LD-024 hearings (الجلسات) of a proceeding (docs/delivery/PHASE-5-ACCEPTANCE.md). case_id repeats the proceeding's
-- case so case lists and reports need no join into Workstream C's tables; the application sets it from
-- ProceedingLookup. Actor columns hold the user id without a foreign key (architecture/CONTRACTS.md §2).
create table hearing (
    id                 uuid primary key default gen_random_uuid(),
    case_id            uuid          not null references legal_case (id),
    proceeding_id      uuid          not null references proceeding (id),
    hearing_date       date          not null,
    hearing_time       time,
    purpose            varchar(20)   not null constraint hearing_purpose_known
        check (purpose in ('FIRST_SESSION', 'PLEADING', 'EVIDENCE', 'EXPERT_REPORT', 'JUDGMENT', 'OTHER')),
    notes              varchar(2000) constraint hearing_notes_visible check (length(btrim(notes)) > 0),
    status             varchar(20)   not null default 'SCHEDULED' constraint hearing_status_known
        check (status in ('SCHEDULED', 'HELD', 'ADJOURNED', 'CANCELLED')),
    -- قرار الجلسة: what the court decided, recorded with the result.
    decision           varchar(2000) constraint hearing_decision_visible check (length(btrim(decision)) > 0),
    -- The hearing this one continues after an adjournment; each hearing is adjourned to at most one next hearing.
    adjourned_from_id  uuid unique references hearing (id),
    result_recorded_at timestamptz,
    result_recorded_by uuid,
    version            bigint        not null default 0,
    created_at         timestamptz   not null default now(),
    created_by         uuid          not null,
    updated_at         timestamptz   not null default now(),
    updated_by         uuid          not null,
    -- A result is recorded exactly when the hearing left SCHEDULED.
    constraint hearing_result_recorded check (
        (status = 'SCHEDULED') = (result_recorded_at is null)
        and (result_recorded_at is null) = (result_recorded_by is null)),
    constraint hearing_not_own_continuation check (adjourned_from_id <> id)
);

-- One hearing per proceeding and day (ASSUMPTION); a cancelled hearing frees its day.
create unique index hearing_proceeding_day_unique on hearing (proceeding_id, hearing_date)
    where status <> 'CANCELLED';
create index hearing_calendar on hearing (hearing_date, hearing_time);
create index hearing_case on hearing (case_id, hearing_date);
