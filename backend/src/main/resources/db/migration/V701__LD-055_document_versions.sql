-- LD-055 (WP5, requirements section 13; owner decision D7): versions of an archived document, its classification, and the
-- procedure that guards deletion. Additive: case_document and case_document_content keep every row, id and byte they hold.
--
-- Storage decision (D7): the files stay in PostgreSQL. The host has no encrypted volume available to the application
-- container, and the existing backup (pg_dump into LAWDESK_BACKUP_DIR) already covers the database, so moving files out of
-- it would add an unbacked copy. See docs/evidence/DOC-WP5/README.md.

-- A document may now be Word or an image: the pdf-only rule becomes a list of known media types. The formats an
-- installation actually accepts are rows of archive_format; this list is the most the database allows.
alter table case_document drop constraint case_document_pdf_only;
alter table case_document
    add constraint case_document_media_known check (media_type in ('application/pdf',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/msword',
        'image/png', 'image/jpeg', 'image/tiff'));

-- case_document keeps the metadata of the current version, so every existing query and report stays valid.
alter table case_document
    add column category_id        uuid references lookup_value (id),
    add column document_date      date,
    add column proceeding_id      uuid references proceeding (id),
    add column description        varchar(2000),
    add column current_version_no integer     not null default 1 constraint case_document_version_no_positive check (current_version_no >= 1),
    add column withdrawn_at       timestamptz,
    add column withdrawn_by       uuid,
    add column withdrawn_reason   varchar(500),
    add column updated_at         timestamptz not null default now(),
    add column updated_by         uuid,
    add constraint case_document_withdrawal_complete check
        ((withdrawn_at is null) = (withdrawn_by is null) and (withdrawn_at is null) = (withdrawn_reason is null)),
    add constraint case_document_description_visible check (description is null or length(btrim(description)) > 0);
create index case_document_category on case_document (category_id) where category_id is not null;
create index case_document_proceeding on case_document (proceeding_id) where proceeding_id is not null;

create table document_version (
    id          uuid primary key default gen_random_uuid(),
    document_id uuid         not null references case_document (id) on delete cascade,
    version_no  integer      not null constraint document_version_no_positive check (version_no >= 1),
    file_name   varchar(255) not null constraint document_version_file_name_visible check (length(btrim(file_name)) > 0),
    media_type  varchar(100) not null,
    size_bytes  integer      not null constraint document_version_size_range check (size_bytes between 1 and 10485760),
    sha256      char(64)     not null constraint document_version_sha256_hex check (sha256 ~ '^[0-9a-f]{64}$'),
    change_note varchar(500),
    uploaded_by uuid         not null,
    uploaded_at timestamptz  not null default now(),
    constraint document_version_once unique (document_id, version_no)
);

create table document_version_content (
    version_id uuid primary key references document_version (id) on delete cascade,
    content    bytea not null
);

-- Version 1 of every existing document: the same id, the same metadata and a copy of the same bytes. The original rows are
-- not touched, so rolling back to the previous release leaves them exactly as they were.
insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, change_note,
                              uploaded_by, uploaded_at)
select d.id, d.id, 1, d.file_name, d.media_type, d.size_bytes, d.sha256, null, d.created_by, d.created_at
from case_document d;
insert into document_version_content (version_id, content)
select c.document_id, c.content from case_document_content c;

-- The log of permanent deletions: what was deleted, why, and who asked and who did it. It never holds file content.
create table document_deletion_log (
    id               uuid primary key default gen_random_uuid(),
    document_id      uuid         not null,
    case_id          uuid         not null,
    title            varchar(200) not null,
    file_name        varchar(255) not null,
    media_type       varchar(100) not null,
    version_count    integer      not null,
    sha256_list      text         not null,
    withdrawn_by     uuid,
    withdrawn_at     timestamptz,
    withdrawn_reason varchar(500),
    deleted_by       uuid         not null,
    deleted_at       timestamptz  not null default now(),
    deleted_reason   varchar(500) not null constraint document_deletion_reason_visible check (length(btrim(deleted_reason)) > 0)
);
create index document_deletion_log_case on document_deletion_log (case_id, deleted_at desc);

-- Nothing is deleted from the archive outside the deletion procedure. The procedure sets lawdesk.archive_purge for its own
-- transaction; any other delete (an old release, a stray statement) is refused by the database.
create function archive_purge_guard() returns trigger language plpgsql as $$
begin
    if coalesce(current_setting('lawdesk.archive_purge', true), '') <> 'on' then
        raise exception 'documents of the archive are deleted only through the deletion procedure'
            using errcode = 'restrict_violation', constraint = 'archive_purge_guard';
    end if;
    return old;
end $$;
create trigger case_document_purge_guard before delete on case_document
    for each row execute function archive_purge_guard();
create trigger document_version_purge_guard before delete on document_version
    for each row execute function archive_purge_guard();
create trigger document_version_content_purge_guard before delete on document_version_content
    for each row execute function archive_purge_guard();
create trigger document_deletion_log_purge_guard before delete on document_deletion_log
    for each row execute function archive_purge_guard();

-- A version is never rewritten; its content row is written once.
create function document_version_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'a stored version is never changed' using errcode = 'restrict_violation';
end $$;
create trigger document_version_no_update before update on document_version
    for each row execute function document_version_append_only();
create trigger document_version_content_no_update before update on document_version_content
    for each row execute function document_version_append_only();
create trigger document_deletion_log_no_update before update on document_deletion_log
    for each row execute function document_version_append_only();
