-- LD-055 (WP5, section 12): the register of memos and correspondence of a case, with their approval cycle
-- (DRAFT, IN_REVIEW, APPROVED, SENT, ARCHIVED). SENT is a user's record that the document was sent; sent_via_integration stays
-- false because no sending integration exists. Additive.

create table legal_document (
    id                   uuid primary key default gen_random_uuid(),
    case_id              uuid         not null references legal_case (id),
    category_id          uuid references lookup_value (id),
    title                varchar(300) not null constraint legal_document_title_visible check (length(btrim(title)) > 0),
    document_date        date,
    issuer               varchar(300),
    addressee            varchar(300),
    responsible_member_id uuid,
    description          varchar(2000),
    document_id          uuid references case_document (id),
    status               varchar(10)  not null default 'DRAFT' constraint legal_document_status_known
                         check (status in ('DRAFT', 'IN_REVIEW', 'APPROVED', 'SENT', 'ARCHIVED')),
    submitted_by         uuid,
    submitted_at         timestamptz,
    approved_by          uuid,
    approved_at          timestamptz,
    approval_note        varchar(500),
    sent_recorded        boolean      not null default false,
    sent_on              date,
    sent_method          varchar(100),
    sent_reference       varchar(200),
    sent_recorded_by     uuid,
    sent_recorded_at     timestamptz,
    sent_via_integration boolean      not null default false constraint legal_document_no_integration check (not sent_via_integration),
    archived_at          timestamptz,
    version              bigint       not null default 0,
    created_at           timestamptz  not null default now(),
    created_by           uuid         not null,
    updated_at           timestamptz  not null default now(),
    updated_by           uuid         not null,
    constraint legal_document_approval_complete check ((approved_at is null) = (approved_by is null)),
    constraint legal_document_sent_complete check (sent_recorded = (sent_recorded_at is not null))
);
create index legal_document_case on legal_document (case_id, created_at desc);
create index legal_document_status on legal_document (status);
create index legal_document_file on legal_document (document_id) where document_id is not null;

create table legal_document_event (
    id                uuid primary key default gen_random_uuid(),
    legal_document_id uuid        not null references legal_document (id) on delete cascade,
    from_status       varchar(10),
    to_status         varchar(10) not null,
    note              varchar(500),
    created_at        timestamptz not null default now(),
    created_by        uuid        not null
);
create index legal_document_event_doc on legal_document_event (legal_document_id, created_at);
