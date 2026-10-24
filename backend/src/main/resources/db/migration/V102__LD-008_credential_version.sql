-- LD-008.7 (QAD-028): a per-user credential version. A session remembers the version it signed in with and is refused
-- once the stored version differs. An administrative reset, a password change and a disablement raise it; nothing
-- lowers it, so a revoked session never becomes valid again. Additive with a default, so the previous release still
-- starts against this schema (LD-006 rollback).
alter table app_user add column credential_version bigint not null default 0;
