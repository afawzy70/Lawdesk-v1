-- LD-059 (WP10, requirements section 22; owner decision D8): the in-system legal assistant.
--
-- NO external AI provider is approved and no case data may leave the system. ai_provider_config therefore exists and stays
-- EMPTY: nothing in the application writes to it, and the assistant reports "not operational: no approved provider" until the
-- owner approves one in a later, separate decision. The only provider shipped is a deterministic local test provider that
-- composes answers extractively from retrieved passages (no generation, no network); it is not evidence of operational
-- completeness.
--
-- The tables of this migration hold references, never copies of case text beyond the draft a user asked for:
--   * assistant_query_log - append-only, one row per question (user, case, task, minimised question, sources used, outcome);
--     it carries identifiers only (no answer, no source text), has no foreign keys so it outlives what it points at, and the
--     database refuses an update or a delete. There is no retention policy yet (D9): nothing deletes it automatically.
--   * assistant_session / assistant_output - a conversation, and the drafts the assistant produced. A draft is NEVER final:
--     is_final is fixed to false by a check, and approval by the user only means "I reviewed this draft".

create table ai_provider_config (
    id                 uuid primary key default gen_random_uuid(),
    code               varchar(40)  not null constraint ai_provider_config_code_form check (code ~ '^[A-Z][A-Z0-9_]{2,39}$'),
    display_name       varchar(120) not null constraint ai_provider_config_name_visible check (length(btrim(display_name)) > 0),
    status             varchar(16)  not null default 'PROPOSED'
        constraint ai_provider_config_status_known check (status in ('PROPOSED', 'APPROVED', 'REVOKED')),
    data_handling_note varchar(2000),
    approved_by        uuid,
    approved_at        timestamptz,
    created_at         timestamptz  not null default now(),
    created_by         uuid         not null,
    constraint ai_provider_config_code_once unique (code),
    constraint ai_provider_config_approval_complete check (status <> 'APPROVED' or (approved_by is not null and approved_at is not null))
);

create table assistant_session (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid        not null references app_user (id),
    case_id      uuid references legal_case (id),
    mode         varchar(24) not null default 'NON_PRODUCTION_TEST' constraint assistant_session_mode_known check (mode in ('NON_PRODUCTION_TEST')),
    created_at   timestamptz not null default now(),
    last_used_at timestamptz not null default now()
);
create index assistant_session_user on assistant_session (user_id, last_used_at desc);

create table assistant_output (
    id          uuid primary key default gen_random_uuid(),
    session_id  uuid references assistant_session (id),
    user_id     uuid         not null references app_user (id),
    case_id     uuid references legal_case (id),
    kind        varchar(24)  not null constraint assistant_output_kind_known check (kind in (
        'MEMO_DRAFT', 'CASE_SUMMARY', 'COMPARISON', 'DOCUMENT_ANALYSIS', 'PRECEDENT_LIST', 'ANSWER')),
    title       varchar(300) not null,
    content     jsonb        not null,
    status      varchar(16)  not null default 'DRAFT' constraint assistant_output_status_known check (status in ('DRAFT', 'USER_APPROVED', 'DISCARDED')),
    is_final    boolean      not null default false constraint assistant_output_never_final check (not is_final),
    non_production boolean   not null default true,
    reviewed_by uuid,
    reviewed_at timestamptz,
    review_note varchar(500),
    created_at  timestamptz  not null default now(),
    constraint assistant_output_review_complete check ((status = 'DRAFT') = (reviewed_at is null))
);
create index assistant_output_user on assistant_output (user_id, created_at desc);
create index assistant_output_case on assistant_output (case_id) where case_id is not null;

create table assistant_query_log (
    id             bigint generated always as identity primary key,
    occurred_at    timestamptz  not null default clock_timestamp(),
    user_id        uuid         not null,
    username       varchar(64),
    session_id     uuid,
    case_id        uuid,
    task           varchar(24)  not null,
    question       varchar(500),
    outcome        varchar(24)  not null constraint assistant_query_log_outcome_known check (outcome in (
        'ANSWERED', 'INSUFFICIENT_SOURCES', 'DENIED_NO_CASE_ACCESS', 'DENIED_PERMISSION', 'FAILED')),
    sources        jsonb        not null default '[]',
    source_count   integer      not null default 0,
    provider_code  varchar(40)  not null,
    non_production boolean      not null,
    output_id      uuid,
    request_id     varchar(64)
);
create index assistant_query_log_user on assistant_query_log (user_id, occurred_at desc);
create index assistant_query_log_case on assistant_query_log (case_id, occurred_at desc) where case_id is not null;

-- Append-only even for the table owner, as the security audit trail is.
create function assistant_query_log_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'assistant_query_log is append-only';
end;
$$;
create trigger assistant_query_log_no_update_or_delete before update or delete on assistant_query_log
    for each row execute function assistant_query_log_append_only();
