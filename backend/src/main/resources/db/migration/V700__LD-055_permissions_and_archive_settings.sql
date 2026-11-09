-- LD-055 (WP5; requirements sections 11, 12, 13; owner decision D7): the permissions of judgment enforcement, the memo approval
-- cycle and the electronic archive, and the archive's limits and accepted formats as settings. Additive only.

-- Permissions (docs/design/LD-DOC-04 sections 2 and 3). MEMO_APPROVE approves memos and letters (managers and section heads,
-- not the member who drafts them); DOCUMENT_ARCHIVE_ADMIN is the only right that can delete a document for good, through
-- the procedure of the archive (withdraw first, then delete with a reason), and changes the archive settings.
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'ENFORCEMENT_VIEW'), ('ADMIN', 'ENFORCEMENT_MANAGE'), ('ADMIN', 'MEMO_APPROVE'), ('ADMIN', 'DOCUMENT_ARCHIVE_ADMIN'),
    ('LEGAL_MANAGER', 'ENFORCEMENT_VIEW'), ('LEGAL_MANAGER', 'ENFORCEMENT_MANAGE'), ('LEGAL_MANAGER', 'MEMO_APPROVE'),
    ('SECTION_HEAD', 'ENFORCEMENT_VIEW'), ('SECTION_HEAD', 'ENFORCEMENT_MANAGE'), ('SECTION_HEAD', 'MEMO_APPROVE'),
    ('LEGAL_STAFF', 'ENFORCEMENT_VIEW'), ('LEGAL_STAFF', 'ENFORCEMENT_MANAGE'),
    ('DATA_ENTRY', 'ENFORCEMENT_VIEW'),
    ('ARCHIVIST', 'DOCUMENT_ARCHIVE_ADMIN'),
    ('READ_ONLY', 'ENFORCEMENT_VIEW')
) as p (role, permission) on p.role = r.code
on conflict do nothing;

-- Archive limits (ASSUMPTIONS, to be confirmed by the owner): the values are rows, so changing one needs no release.
create table archive_setting (
    code       varchar(40) primary key constraint archive_setting_code_format check (code ~ '^[A-Z][A-Z0-9_]*$'),
    int_value  integer     not null,
    min_value  integer     not null,
    max_value  integer     not null,
    updated_at timestamptz not null default now(),
    updated_by uuid,
    constraint archive_setting_in_range check (int_value between min_value and max_value)
);
insert into archive_setting (code, int_value, min_value, max_value) values
    -- 10 MiB per file: the multipart limit of the web layer (application.yaml) is the same, so this cannot be raised alone.
    ('MAX_FILE_BYTES', 10485760, 1048576, 10485760),
    -- The central archive of a case holds memos, letters, scans and evidence, so the old limit of ten PDFs is raised.
    ('MAX_DOCUMENTS_PER_CASE', 100, 1, 1000),
    ('MAX_VERSIONS_PER_DOCUMENT', 20, 1, 100),
    -- A scanned PDF is read page by page; pages beyond this are left unread and the text says so.
    ('MAX_OCR_PAGES', 50, 1, 500),
    ('MAX_TEXT_CHARS', 2000000, 10000, 10000000);

-- Accepted formats. The signature of each one is checked in the application; a manager may switch a format off but cannot
-- add one the application does not know how to verify.
create table archive_format (
    code       varchar(10) primary key,
    family     varchar(10) not null constraint archive_format_family_known check (family in ('PDF', 'WORD', 'IMAGE')),
    media_type varchar(100) not null unique,
    extension  varchar(10) not null,
    enabled    boolean     not null default true,
    updated_at timestamptz not null default now(),
    updated_by uuid
);
insert into archive_format (code, family, media_type, extension) values
    ('PDF', 'PDF', 'application/pdf', 'pdf'),
    ('DOCX', 'WORD', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'docx'),
    ('DOC', 'WORD', 'application/msword', 'doc'),
    ('PNG', 'IMAGE', 'image/png', 'png'),
    ('JPEG', 'IMAGE', 'image/jpeg', 'jpg'),
    ('TIFF', 'IMAGE', 'image/tiff', 'tif');
