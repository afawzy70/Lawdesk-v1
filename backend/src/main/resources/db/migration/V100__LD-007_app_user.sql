-- LD-007/LD-008/LD-009: database-backed users, roles and their permission codes (architecture/CONTRACTS.md §3).
-- Permission codes are those of com.lawdesk.security.Permission; ADMIN is granted every code by the application,
-- so permissions added later never leave the administrator short.
create table app_user (
    id uuid primary key default gen_random_uuid(),
    username varchar(64) not null,
    email varchar(254) not null,
    display_name varchar(128) not null,
    password_hash varchar(255) not null,
    enabled boolean not null default true,
    must_change_password boolean not null default false,
    locale varchar(2) not null default 'ar',
    failed_attempts integer not null default 0,
    locked_until timestamptz,
    password_changed_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    created_by uuid not null,
    updated_at timestamptz not null default now(),
    updated_by uuid not null,
    version bigint not null default 0,
    constraint app_user_username_format check (username ~ '^[a-z0-9._-]{3,64}$'),
    constraint app_user_email_format check (email ~ '^[^@[:space:]]+@[^@[:space:]]+$'),
    constraint app_user_display_name_visible check (length(btrim(display_name)) > 0),
    constraint app_user_locale check (locale in ('ar', 'en')),
    constraint app_user_failed_attempts check (failed_attempts >= 0)
);
create unique index app_user_username_unique on app_user (lower(username));
create unique index app_user_email_unique on app_user (lower(email));

create table app_role (
    id uuid primary key default gen_random_uuid(),
    code varchar(64) not null unique,
    name varchar(128) not null,
    system boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint app_role_code_format check (code ~ '^[A-Z][A-Z0-9_]{1,63}$')
);

create table role_permission (
    role_id uuid not null references app_role (id) on delete cascade,
    permission varchar(64) not null,
    primary key (role_id, permission)
);

create table user_role (
    user_id uuid not null references app_user (id) on delete cascade,
    role_id uuid not null references app_role (id),
    primary key (user_id, role_id)
);
create index user_role_role on user_role (role_id);

insert into app_role (code, name, system) values
    ('ADMIN', 'Administrator', true),
    ('LEGAL_MANAGER', 'Legal manager', true),
    ('LEGAL_STAFF', 'Legal staff', true);

insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'USER_VIEW'), ('ADMIN', 'USER_MANAGE'), ('ADMIN', 'ROLE_VIEW'), ('ADMIN', 'ROLE_MANAGE'),
    ('ADMIN', 'AUDIT_VIEW'), ('ADMIN', 'CASE_VIEW'), ('ADMIN', 'CASE_CREATE'), ('ADMIN', 'CASE_EDIT'),
    ('ADMIN', 'CASE_DELETE'), ('ADMIN', 'CASE_STATUS_CHANGE'), ('ADMIN', 'PARTY_MANAGE'), ('ADMIN', 'COURT_VIEW'),
    ('ADMIN', 'COURT_MANAGE'), ('ADMIN', 'PROCEEDING_VIEW'), ('ADMIN', 'PROCEEDING_MANAGE'),
    ('ADMIN', 'JUDGMENT_VIEW'), ('ADMIN', 'JUDGMENT_MANAGE'),
    ('LEGAL_MANAGER', 'CASE_VIEW'), ('LEGAL_MANAGER', 'CASE_CREATE'), ('LEGAL_MANAGER', 'CASE_EDIT'),
    ('LEGAL_MANAGER', 'CASE_DELETE'), ('LEGAL_MANAGER', 'CASE_STATUS_CHANGE'), ('LEGAL_MANAGER', 'PARTY_MANAGE'),
    ('LEGAL_MANAGER', 'COURT_VIEW'), ('LEGAL_MANAGER', 'COURT_MANAGE'), ('LEGAL_MANAGER', 'PROCEEDING_VIEW'),
    ('LEGAL_MANAGER', 'PROCEEDING_MANAGE'), ('LEGAL_MANAGER', 'JUDGMENT_VIEW'), ('LEGAL_MANAGER', 'JUDGMENT_MANAGE'),
    ('LEGAL_MANAGER', 'USER_VIEW'), ('LEGAL_MANAGER', 'AUDIT_VIEW'),
    ('LEGAL_STAFF', 'CASE_VIEW'), ('LEGAL_STAFF', 'CASE_CREATE'), ('LEGAL_STAFF', 'CASE_EDIT'),
    ('LEGAL_STAFF', 'PARTY_MANAGE'), ('LEGAL_STAFF', 'COURT_VIEW'), ('LEGAL_STAFF', 'PROCEEDING_VIEW'),
    ('LEGAL_STAFF', 'PROCEEDING_MANAGE'), ('LEGAL_STAFF', 'JUDGMENT_VIEW')
) as p (role, permission) on p.role = r.code;
