-- LD-054 (WP4; requirements section 10, docs/design/LD-DOC-05 section 4): the deadline register. A deadline is manual
-- (typed by a user), proposed (computed from an approved rule, awaiting approval) or approved (by a competent member,
-- with the name and the time kept). The system never completes or closes a deadline. Additive only.

create table deadline (
    id                uuid primary key default gen_random_uuid(),
    case_id           uuid          not null references legal_case (id),
    -- What the period runs from: a judgment, a hearing, a proceeding or a task of the case, or free text.
    source_type       varchar(10)   not null constraint deadline_source_type_known
        check (source_type in ('JUDGMENT', 'HEARING', 'PROCEEDING', 'TASK', 'TEXT')),
    source_id         uuid,
    source_text       varchar(500),
    -- A language-neutral name of the source when the deadline was recorded (a date, a court number/year, a task title).
    source_label      varchar(300),
    starts_on         date,
    period_days       integer constraint deadline_period_positive check (period_days is null or period_days > 0),
    due_on            date,
    -- The approved rule a proposal was computed from, and the full arithmetic as it was shown to the reviewer.
    rule_id           uuid references deadline_rule (id),
    calculation       jsonb,
    action            varchar(500)  not null constraint deadline_action_visible check (length(btrim(action)) > 0),
    -- The responsible member; the service checks that the user exists (as for the other modules, no foreign key).
    owner_id          uuid,
    status            varchar(10)   not null default 'OPEN' constraint deadline_status_known
        check (status in ('OPEN', 'COMPLETED', 'MISSED')),
    completed_on      date,
    proof_document_id uuid references case_document (id) on delete set null,
    notes             varchar(2000) constraint deadline_notes_visible check (notes is null or length(btrim(notes)) > 0),
    calc_status       varchar(10)   not null constraint deadline_calc_status_known
        check (calc_status in ('MANUAL', 'PROPOSED', 'APPROVED')),
    approved_by       uuid,
    approved_at       timestamptz,
    -- Why the dates changed after a proposal or an approval (required by the service at that moment).
    change_reason     varchar(1000),
    version           bigint        not null default 0,
    created_at        timestamptz   not null default now(),
    created_by        uuid          not null,
    updated_at        timestamptz   not null default now(),
    updated_by        uuid          not null,
    constraint deadline_source_shape check (
        (source_type = 'TEXT' and source_id is null and source_text is not null and length(btrim(source_text)) > 0)
        or (source_type <> 'TEXT' and source_id is not null)),
    -- A period alone is a valid record (the due date is then not yet known); neither is not.
    constraint deadline_period_or_due check (due_on is not null or period_days is not null),
    constraint deadline_approval_recorded check (
        (calc_status = 'APPROVED') = (approved_by is not null) and (approved_by is null) = (approved_at is null)),
    constraint deadline_approved_has_due check (calc_status <> 'APPROVED' or due_on is not null),
    constraint deadline_proposal_complete check (
        calc_status <> 'PROPOSED' or (rule_id is not null and calculation is not null and due_on is not null)),
    constraint deadline_completed_dated check ((status = 'COMPLETED') = (completed_on is not null)),
    constraint deadline_proof_only_when_completed check (proof_document_id is null or status = 'COMPLETED')
);
create index deadline_case on deadline (case_id, due_on);
create index deadline_open_due on deadline (status, due_on);
create index deadline_owner on deadline (owner_id, due_on);
create index deadline_source on deadline (source_type, source_id);
