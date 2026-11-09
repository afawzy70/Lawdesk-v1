-- LD-052 (gap closure, requirements section 16, "stores a copy of the judgment"): the electronic copy of a library judgment
-- kept in the archive (decision D7: the files stay in PostgreSQL, behind the archive's own limits and accepted formats). A
-- judgment of the library is not case data, so the copy is not a case_document; it follows the same rules (signature
-- verified, size limit, hash) and keeps its history: a replacement marks the previous copy removed. Additive only.

create table library_judgment_file (
    id          uuid primary key default gen_random_uuid(),
    judgment_id uuid         not null references library_judgment (id),
    file_name   varchar(255) not null constraint library_judgment_file_name_visible check (length(btrim(file_name)) > 0),
    media_type  varchar(100) not null,
    size_bytes  integer      not null constraint library_judgment_file_size_range check (size_bytes between 1 and 10485760),
    sha256      char(64)     not null constraint library_judgment_file_sha256_hex check (sha256 ~ '^[0-9a-f]{64}$'),
    content     bytea        not null,
    uploaded_by uuid         not null,
    uploaded_at timestamptz  not null default now(),
    removed_at  timestamptz,
    removed_by  uuid,
    constraint library_judgment_file_removal_complete check ((removed_at is null) = (removed_by is null))
);
create unique index library_judgment_file_current on library_judgment_file (judgment_id) where removed_at is null;
create index library_judgment_file_by_judgment on library_judgment_file (judgment_id, uploaded_at desc);
