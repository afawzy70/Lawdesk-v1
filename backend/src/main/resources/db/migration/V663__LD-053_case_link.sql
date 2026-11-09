-- LD-053 (WP3; requirements section 8): linked cases. One row links two cases and is shown in both files; ending or
-- closing one case never changes the other. Additive only.

create table case_link (
    id                   uuid primary key default gen_random_uuid(),
    case_id              uuid          not null references legal_case (id),
    other_case_id        uuid          not null references legal_case (id),
    -- موضوعي، طلب ضم، قرار ضم، إحالة.
    kind                 varchar(16)   not null constraint case_link_kind_known
        check (kind in ('SUBJECT_LINK', 'JOINDER_REQUEST', 'JOINDER_DECISION', 'REFERRAL')),
    reason               varchar(2000) not null constraint case_link_reason_visible check (length(btrim(reason)) > 0),
    -- مقدم طلب الارتباط (a party, the authority or the court): free text.
    requested_by         varchar(200),
    requested_on         date,
    court_decision       boolean       not null default false,
    decision_date        date,
    operative            varchar(2000),
    decision_document_id uuid references case_document (id) on delete set null,
    -- ASSUMPTION (needs the owner's approval): the document names no states for a link.
    status               varchar(10)   not null default 'ACTIVE' constraint case_link_status_known
        check (status in ('REQUESTED', 'ACTIVE', 'REJECTED', 'ENDED')),
    version              bigint        not null default 0,
    created_at           timestamptz   not null default now(),
    created_by           uuid          not null,
    updated_at           timestamptz   not null default now(),
    updated_by           uuid          not null,
    constraint case_link_two_cases check (case_id <> other_case_id),
    constraint case_link_decision_recorded check (court_decision or (decision_date is null and operative is null
                                                                    and decision_document_id is null)),
    constraint case_link_text_visible check (
        (requested_by is null or length(btrim(requested_by)) > 0) and (operative is null or length(btrim(operative)) > 0))
);
-- The same kind of link between the same two cases is recorded once while it is in force or requested, whichever side
-- it was entered from.
create unique index case_link_pair_kind on case_link (least(case_id, other_case_id), greatest(case_id, other_case_id), kind)
    where status in ('REQUESTED', 'ACTIVE');
create index case_link_case on case_link (case_id);
create index case_link_other on case_link (other_case_id);
