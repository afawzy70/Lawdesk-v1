-- LD-026 legal PDF attachments, up to ten per case (docs/delivery/PHASE-5-ACCEPTANCE.md, architecture/CONTRACTS.md
-- §8.3). Files live in PostgreSQL, so the existing pg_dump backs them up; the bytes sit in their own table so that
-- listing documents never reads them. Actor columns hold the user id without a foreign key (CONTRACTS §2).
create table case_document (
    id          uuid primary key default gen_random_uuid(),
    case_id     uuid         not null references legal_case (id),
    title       varchar(200) not null constraint case_document_title_visible check (length(btrim(title)) > 0),
    file_name   varchar(255) not null constraint case_document_file_name_visible check (length(btrim(file_name)) > 0),
    media_type  varchar(100) not null default 'application/pdf'
        constraint case_document_pdf_only check (media_type = 'application/pdf'),
    size_bytes  integer      not null constraint case_document_size_range check (size_bytes between 1 and 10485760),
    sha256      char(64)     not null constraint case_document_sha256_hex check (sha256 ~ '^[0-9a-f]{64}$'),
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    created_by  uuid         not null
);

create index case_document_case on case_document (case_id, created_at desc);

create table case_document_content (
    document_id uuid primary key references case_document (id) on delete cascade,
    content     bytea not null
);

-- One row per case that ever had a document. Uploads and removals lock it (select ... for update) before they count,
-- so two concurrent uploads to a case holding nine documents store exactly one (ASSUMPTION: at most ten per case).
create table case_document_quota (
    case_id uuid primary key references legal_case (id)
);
