-- LD-050 (WP1, docs/design/LD-DOC-03, LD-DOC-04, owner decisions D3 of 2026-11-08): who may see which case.
-- Additive only. Nothing is dropped or renamed; every new column is nullable or has a neutral default, so existing
-- cases and users keep working. No organisational structure is invented: the tree starts empty.

-- The organisation tree. Two trees share the table: the legal department's own units (the scope of section heads and the
-- legal manager) and the authority's sectors and departments that a case involves (LD-DOC-03 case_org_involvement).
create table org_unit (
    id          uuid primary key default gen_random_uuid(),
    tree        varchar(12)  not null constraint org_unit_tree_known check (tree in ('LEGAL', 'AUTHORITY')),
    parent_id   uuid references org_unit (id),
    level       varchar(12)  not null constraint org_unit_level_known check (level in ('SECTOR', 'DEPARTMENT', 'SECTION')),
    name_ar     varchar(200) not null constraint org_unit_name_visible check (length(btrim(name_ar)) > 0),
    name_en     varchar(200),
    active      boolean      not null default true,
    valid_from  date,
    valid_to    date,
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    created_by  uuid         not null,
    updated_at  timestamptz  not null default now(),
    updated_by  uuid         not null,
    constraint org_unit_not_own_parent check (parent_id is null or parent_id <> id),
    constraint org_unit_validity check (valid_from is null or valid_to is null or valid_from <= valid_to)
);
create index org_unit_parent on org_unit (parent_id);
create unique index org_unit_sibling_name on org_unit (tree, coalesce(parent_id, '00000000-0000-0000-0000-000000000000'),
    lower(name_ar)) where active;

-- The unit a user works in (legal tree), and the unit that owns a case. Both are optional: "not specified" is a value.
alter table app_user add column org_unit_id uuid references org_unit (id);
alter table legal_case
    add column owning_unit_id uuid references org_unit (id),
    -- RESTRICTED cases are reachable only by their members, the creator and explicit grants (never by scope alone).
    add column confidentiality varchar(12) not null default 'NORMAL',
    add constraint legal_case_confidentiality_known check (confidentiality in ('NORMAL', 'RESTRICTED'));
create index legal_case_owning_unit on legal_case (owning_unit_id);
create index legal_case_created_by on legal_case (created_by);

-- Who sees which cases beyond their own. A subject is a role (by code) or one user; the scope kinds are
-- ALL (every non-restricted case, the department level), ORG_UNIT (the unit and its subtree), OWN_UNIT (the subtree of
-- the user's own unit) and CASE_TYPE. The system administrator role has no row here: administering the system does not
-- by itself give access to case content.
create table access_scope (
    id           uuid primary key default gen_random_uuid(),
    subject_type varchar(8)   not null constraint access_scope_subject_known check (subject_type in ('ROLE', 'USER')),
    subject_ref  varchar(64)  not null,
    scope_type   varchar(12)  not null constraint access_scope_type_known
        check (scope_type in ('ALL', 'ORG_UNIT', 'OWN_UNIT', 'CASE_TYPE')),
    scope_ref    varchar(64),
    created_at   timestamptz  not null default now(),
    created_by   uuid,
    constraint access_scope_ref_matches check ((scope_type in ('ORG_UNIT', 'CASE_TYPE')) = (scope_ref is not null)),
    constraint access_scope_unique unique (subject_type, subject_ref, scope_type, scope_ref)
);
create unique index access_scope_unique_without_ref on access_scope (subject_type, subject_ref, scope_type)
    where scope_ref is null;
create index access_scope_subject on access_scope (subject_type, subject_ref);

-- An explicit grant on one case: VIEW reads it, CONTRIBUTE also changes it. It carries its reason, who granted it,
-- an optional expiry and, once withdrawn, who withdrew it; rows are never deleted.
create table case_grant (
    id          uuid primary key default gen_random_uuid(),
    case_id     uuid         not null references legal_case (id),
    user_id     uuid         not null references app_user (id),
    level       varchar(12)  not null constraint case_grant_level_known check (level in ('VIEW', 'CONTRIBUTE')),
    reason      varchar(500) not null constraint case_grant_reason_visible check (length(btrim(reason)) > 0),
    granted_by  uuid         not null,
    granted_at  timestamptz  not null default now(),
    expires_at  timestamptz,
    revoked_at  timestamptz,
    revoked_by  uuid,
    constraint case_grant_revocation_complete check ((revoked_at is null) = (revoked_by is null))
);
create index case_grant_case on case_grant (case_id);
create index case_grant_user on case_grant (user_id) where revoked_at is null;

-- Default role scopes: the legal manager works at department level; a section head within the own unit. Other roles
-- see only the cases they are members of, created or were granted (or hold a task on). Administrators can change these.
insert into access_scope (subject_type, subject_ref, scope_type) values
    ('ROLE', 'LEGAL_MANAGER', 'ALL');

-- The four roles of the requirements document (section 30) that did not exist yet, without users.
insert into app_role (code, name, system) values
    ('SECTION_HEAD', 'Section head', true),
    ('DATA_ENTRY', 'Data entry', true),
    ('ARCHIVIST', 'Archivist', true),
    ('READ_ONLY', 'Read only', true);
insert into access_scope (subject_type, subject_ref, scope_type) values ('ROLE', 'SECTION_HEAD', 'OWN_UNIT');

-- Permissions: separating viewing (scope), editing, distributing and granting (LD-DOC-04).
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'ASSIGN_CASE'), ('ADMIN', 'CASE_EDIT_IN_SCOPE'), ('ADMIN', 'CASE_GRANT_MANAGE'), ('ADMIN', 'ORG_MANAGE'),
    ('LEGAL_MANAGER', 'ASSIGN_CASE'), ('LEGAL_MANAGER', 'CASE_EDIT_IN_SCOPE'), ('LEGAL_MANAGER', 'CASE_GRANT_MANAGE'),
    ('LEGAL_MANAGER', 'ORG_MANAGE'),
    ('SECTION_HEAD', 'ASSIGN_CASE'), ('SECTION_HEAD', 'CASE_EDIT_IN_SCOPE'),
    ('SECTION_HEAD', 'CASE_VIEW'), ('SECTION_HEAD', 'CASE_CREATE'), ('SECTION_HEAD', 'CASE_EDIT'),
    ('SECTION_HEAD', 'CASE_STATUS_CHANGE'), ('SECTION_HEAD', 'PARTY_MANAGE'), ('SECTION_HEAD', 'COURT_VIEW'),
    ('SECTION_HEAD', 'PROCEEDING_VIEW'), ('SECTION_HEAD', 'PROCEEDING_MANAGE'), ('SECTION_HEAD', 'JUDGMENT_VIEW'),
    ('SECTION_HEAD', 'JUDGMENT_MANAGE'), ('SECTION_HEAD', 'HEARING_VIEW'), ('SECTION_HEAD', 'HEARING_MANAGE'),
    ('SECTION_HEAD', 'EXPERT_VIEW'), ('SECTION_HEAD', 'EXPERT_MANAGE'), ('SECTION_HEAD', 'DOCUMENT_VIEW'),
    ('SECTION_HEAD', 'DOCUMENT_MANAGE'), ('SECTION_HEAD', 'TASK_VIEW'), ('SECTION_HEAD', 'TASK_MANAGE'),
    ('SECTION_HEAD', 'NOTE_MANAGE'), ('SECTION_HEAD', 'REPORT_VIEW'), ('SECTION_HEAD', 'REPORT_EXPORT'),
    ('DATA_ENTRY', 'CASE_VIEW'), ('DATA_ENTRY', 'CASE_CREATE'), ('DATA_ENTRY', 'CASE_EDIT'),
    ('DATA_ENTRY', 'PARTY_MANAGE'), ('DATA_ENTRY', 'COURT_VIEW'), ('DATA_ENTRY', 'PROCEEDING_VIEW'),
    ('DATA_ENTRY', 'PROCEEDING_MANAGE'), ('DATA_ENTRY', 'DOCUMENT_VIEW'), ('DATA_ENTRY', 'TASK_VIEW'),
    ('ARCHIVIST', 'CASE_VIEW'), ('ARCHIVIST', 'DOCUMENT_VIEW'), ('ARCHIVIST', 'DOCUMENT_MANAGE'),
    ('READ_ONLY', 'CASE_VIEW'), ('READ_ONLY', 'COURT_VIEW'), ('READ_ONLY', 'PROCEEDING_VIEW'),
    ('READ_ONLY', 'JUDGMENT_VIEW'), ('READ_ONLY', 'HEARING_VIEW'), ('READ_ONLY', 'EXPERT_VIEW'),
    ('READ_ONLY', 'DOCUMENT_VIEW'), ('READ_ONLY', 'TASK_VIEW'), ('READ_ONLY', 'REPORT_VIEW')
) as p (role, permission) on p.role = r.code
on conflict do nothing;

-- The rule itself, written once. Application code passes the signed-in user's id and scope tokens (comma separated)
-- and never re-implements it, so a list, a count, a report, an export and a single read cannot disagree.
--   view   : member, alternate, creator, a user with a task on the case, an active grant, or - unless the case is
--            RESTRICTED - a scope that covers it (department level, an organisational unit with its subtree, a type).
--   change : member, alternate, the creator while no primary member is set, a CONTRIBUTE grant, or - unless RESTRICTED -
--            a scope that covers it for a holder of CASE_EDIT_IN_SCOPE.
create function case_in_scope(p_case uuid, p_all boolean, p_units text, p_types text) returns boolean
    language sql stable as $$
    select exists (
        select 1 from legal_case c
        where c.id = p_case and c.confidentiality <> 'RESTRICTED' and (
            p_all
            or c.case_type = any (string_to_array(p_types, ','))
            or c.owning_unit_id in (
                with recursive tree(id) as (
                    select u.id from org_unit u where u.id = any (cast(string_to_array(p_units, ',') as uuid[]))
                    union
                    select o.id from org_unit o join tree on o.parent_id = tree.id)
                select id from tree)
            or (c.owning_unit_id is null and exists (
                select 1 from org_unit top
                where top.id = any (cast(string_to_array(p_units, ',') as uuid[])) and top.parent_id is null))))
$$;

create function case_visible(p_case uuid, p_me uuid, p_all boolean, p_units text, p_types text) returns boolean
    language sql stable as $$
    select exists (
        select 1 from legal_case c
        where c.id = p_case and (
            c.primary_member_id = p_me or c.alternate_member_id = p_me or c.created_by = p_me
            or exists (select 1 from case_grant g where g.case_id = c.id and g.user_id = p_me and g.revoked_at is null
                       and (g.expires_at is null or g.expires_at > now()))
            or exists (select 1 from task t where t.case_id = c.id and t.assignee_id = p_me
                       and t.status in ('OPEN', 'IN_PROGRESS'))))
        or case_in_scope(p_case, p_all, p_units, p_types)
$$;

create function case_changeable(p_case uuid, p_me uuid, p_edit_in_scope boolean, p_all boolean, p_units text,
                                p_types text) returns boolean
    language sql stable as $$
    select exists (
        select 1 from legal_case c
        where c.id = p_case and (
            c.primary_member_id = p_me or c.alternate_member_id = p_me
            or (c.created_by = p_me and c.primary_member_id is null)
            or exists (select 1 from case_grant g where g.case_id = c.id and g.user_id = p_me and g.level = 'CONTRIBUTE'
                       and g.revoked_at is null and (g.expires_at is null or g.expires_at > now()))))
        or (p_edit_in_scope and case_in_scope(p_case, p_all, p_units, p_types))
$$;
