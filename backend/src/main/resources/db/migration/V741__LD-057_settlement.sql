-- LD-057 (WP9, requirements section 23): settlements, waivers and conciliations of a case. Additive only.
-- settlement_amount is the settlement's OWN amount in Kuwaiti dinars (three decimals): it is never added to, compared with or
-- derived from the case's financial impact, claimed amount, judged amount or potential loss (owner decision D5).
-- Recording, approving or executing a settlement changes nothing on the case or on any linked case (workflow 9).

create table settlement (
    id                   uuid primary key default gen_random_uuid(),
    case_id              uuid          not null references legal_case (id),
    -- A code of the managed list SETTLEMENT_KIND (تسوية، تنازل، صلح); a deactivated value stays valid for old records.
    kind                 varchar(40)   not null,
    -- ASSUMPTION (needs the owner's approval): the document names no states; each change is recorded in settlement_history.
    status               varchar(12)   not null default 'PROPOSED' constraint settlement_status_known
        check (status in ('PROPOSED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'EXECUTED', 'WITHDRAWN')),
    -- الطرف مقدم عرض التسوية: a party of the case, and/or free text for an offeror that is not recorded as a party.
    offering_party_id    uuid references party (id),
    offering_party_text  varchar(300),
    settlement_amount    numeric(15, 3) constraint settlement_amount_not_negative check (settlement_amount >= 0),
    legal_grounds        text,
    financial_impact_note text,
    legal_opinion        text,
    approving_authority  varchar(300),
    decision_reason      text,
    decided_by           uuid,
    decided_at           timestamptz,
    completed_on         date,
    version              bigint        not null default 0,
    created_at           timestamptz   not null default now(),
    created_by           uuid          not null,
    updated_at           timestamptz   not null default now(),
    updated_by           uuid          not null,
    search_vector        tsvector generated always as (
        setweight(to_tsvector('simple', library_normalize(legal_grounds)), 'A')
        || setweight(to_tsvector('simple', library_normalize(legal_opinion)), 'A')
        || setweight(to_tsvector('simple', library_normalize(offering_party_text)), 'B')
        || setweight(to_tsvector('simple', library_normalize(financial_impact_note)), 'B')
        || setweight(to_tsvector('simple', library_normalize(approving_authority)), 'C')
        || setweight(to_tsvector('simple', library_normalize(decision_reason)), 'C')) stored,
    constraint settlement_text_visible check (
        (legal_grounds is null or length(btrim(legal_grounds)) > 0) and (legal_opinion is null or length(btrim(legal_opinion)) > 0)
        and (financial_impact_note is null or length(btrim(financial_impact_note)) > 0)
        and (offering_party_text is null or length(btrim(offering_party_text)) > 0)
        and (approving_authority is null or length(btrim(approving_authority)) > 0)
        and (decision_reason is null or length(btrim(decision_reason)) > 0)),
    -- The decision of the approving authority is recorded whole or not at all.
    constraint settlement_decision_complete check ((decided_at is null) = (decided_by is null)),
    -- A settlement is executed on a date, and only an executed one has that date (تاريخ إتمام التسوية).
    constraint settlement_completed_when_executed check ((status = 'EXECUTED') = (completed_on is not null)),
    constraint settlement_executed_was_decided check (status <> 'EXECUTED' or decided_at is not null)
);
create index settlement_case on settlement (case_id, created_at);
create index settlement_status on settlement (status);
create index settlement_search on settlement using gin (search_vector);

-- The supporting documents (المستندات المؤيدة): documents of the case file, linked through the document module.
create table settlement_document (
    settlement_id uuid        not null references settlement (id),
    document_id   uuid        not null references case_document (id) on delete cascade,
    linked_at     timestamptz not null default now(),
    linked_by     uuid        not null,
    primary key (settlement_id, document_id)
);
create index settlement_document_document on settlement_document (document_id);

-- The full history: every creation, edit, status change and document link, with who and when. Append only.
create table settlement_history (
    id            uuid primary key default gen_random_uuid(),
    settlement_id uuid        not null references settlement (id),
    action        varchar(20) not null constraint settlement_history_action_known
        check (action in ('CREATED', 'UPDATED', 'STATUS_CHANGED', 'DOCUMENT_LINKED', 'DOCUMENT_UNLINKED')),
    from_status   varchar(12),
    to_status     varchar(12),
    -- Field by field: {"field": {"from": ..., "to": ...}}; the amount is a string with three decimals.
    changes       jsonb,
    note          text,
    actor_id      uuid        not null,
    occurred_at   timestamptz not null default now()
);
create index settlement_history_settlement on settlement_history (settlement_id, occurred_at, id);
