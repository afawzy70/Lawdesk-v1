-- One row per application start: which build ran against this database and when.
-- Supports deployment verification and rollback forensics (LD-006).
create table app_release (
    id         bigint generated always as identity primary key,
    version    varchar(64) not null check (length(trim(version)) > 0),
    started_at timestamptz not null default now()
);
