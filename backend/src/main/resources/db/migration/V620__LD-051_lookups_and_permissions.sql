-- LD-051 (WP2, requirements sections 5, 9, 17, 20; owner decision D10): managed lists and the permissions of the claims,
-- defences and evidence modules. Additive only.

-- Managed lists (قوائم قابلة للإدارة). A value is either DOCUMENT (it appears in the owner's requirements text) or
-- PROPOSED (suggested by the implementers and shown as "needs owner approval" until someone with LOOKUP_MANAGE approves
-- it). Values are deactivated, never deleted, and every change keeps the previous state in lookup_value_history.
create table lookup_value (
    id          uuid primary key default gen_random_uuid(),
    list_code   varchar(40)  not null constraint lookup_value_list_code_format check (list_code ~ '^[A-Z][A-Z0-9_]*$'),
    code        varchar(60)  not null constraint lookup_value_code_format check (code ~ '^[A-Z][A-Z0-9_]*$'),
    label_ar    varchar(200) not null constraint lookup_value_label_ar_visible check (length(btrim(label_ar)) > 0),
    label_en    varchar(200),
    origin      varchar(10)  not null constraint lookup_value_origin_known check (origin in ('DOCUMENT', 'PROPOSED')),
    approved_by uuid,
    approved_at timestamptz,
    active      boolean      not null default true,
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    created_by  uuid         not null,
    updated_at  timestamptz  not null default now(),
    updated_by  uuid         not null,
    constraint lookup_value_unique unique (list_code, code),
    constraint lookup_value_approval_complete check ((approved_at is null) = (approved_by is null))
);

create table lookup_value_history (
    id          uuid primary key default gen_random_uuid(),
    value_id    uuid         not null references lookup_value (id),
    rev         integer      not null,
    label_ar    varchar(200) not null,
    label_en    varchar(200),
    origin      varchar(10)  not null,
    active      boolean      not null,
    approved_at timestamptz,
    change      varchar(12)  not null constraint lookup_value_history_change_known
                             check (change in ('CREATED', 'EDITED', 'APPROVED', 'ACTIVATED', 'DEACTIVATED')),
    changed_by  uuid         not null,
    changed_at  timestamptz  not null default now(),
    constraint lookup_value_history_rev unique (value_id, rev)
);

-- Only values the implementers propose are seeded: the requirements text names no judgment types or judgment natures.
-- They are the examples of the approved design prototype (docs/design/prototype, screen "judgment") and wait for the
-- owner's approval; nothing is marked as coming from the document.
insert into lookup_value (list_code, code, label_ar, label_en, origin, created_by, updated_by)
select v.list_code, v.code, v.label_ar, v.label_en, 'PROPOSED', '00000000-0000-4000-8000-000000000000',
       '00000000-0000-4000-8000-000000000000'
from (values
    ('JUDGMENT_TYPE', 'SUBSTANTIVE', 'حكم موضوعي', 'Substantive judgment'),
    ('JUDGMENT_TYPE', 'PREPARATORY', 'حكم تمهيدي', 'Preparatory judgment'),
    ('JUDGMENT_TYPE', 'JURISDICTION', 'حكم في الاختصاص', 'Judgment on jurisdiction'),
    ('JUDGMENT_NATURE', 'FIRST_INSTANCE_RULING', 'ابتدائي', 'First-instance ruling'),
    ('JUDGMENT_NATURE', 'FINAL_RULING', 'نهائي', 'Final ruling')) as v (list_code, code, label_ar, label_en);
insert into lookup_value_history (value_id, rev, label_ar, label_en, origin, active, approved_at, change, changed_by)
select id, 1, label_ar, label_en, origin, active, approved_at, 'CREATED', created_by from lookup_value;

-- Permissions (LD-DOC-04 section 2). Seeded like the existing modules; roles an administrator changed keep their own.
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'CLAIM_VIEW'), ('ADMIN', 'CLAIM_MANAGE'), ('ADMIN', 'DEFENSE_VIEW'), ('ADMIN', 'DEFENSE_MANAGE'),
    ('ADMIN', 'EVIDENCE_VIEW'), ('ADMIN', 'EVIDENCE_MANAGE'), ('ADMIN', 'LOOKUP_MANAGE'),
    ('LEGAL_MANAGER', 'CLAIM_VIEW'), ('LEGAL_MANAGER', 'CLAIM_MANAGE'), ('LEGAL_MANAGER', 'DEFENSE_VIEW'),
    ('LEGAL_MANAGER', 'DEFENSE_MANAGE'), ('LEGAL_MANAGER', 'EVIDENCE_VIEW'), ('LEGAL_MANAGER', 'EVIDENCE_MANAGE'),
    ('LEGAL_MANAGER', 'LOOKUP_MANAGE'),
    ('SECTION_HEAD', 'CLAIM_VIEW'), ('SECTION_HEAD', 'CLAIM_MANAGE'), ('SECTION_HEAD', 'DEFENSE_VIEW'),
    ('SECTION_HEAD', 'DEFENSE_MANAGE'), ('SECTION_HEAD', 'EVIDENCE_VIEW'), ('SECTION_HEAD', 'EVIDENCE_MANAGE'),
    ('LEGAL_STAFF', 'CLAIM_VIEW'), ('LEGAL_STAFF', 'CLAIM_MANAGE'), ('LEGAL_STAFF', 'DEFENSE_VIEW'),
    ('LEGAL_STAFF', 'DEFENSE_MANAGE'), ('LEGAL_STAFF', 'EVIDENCE_VIEW'), ('LEGAL_STAFF', 'EVIDENCE_MANAGE'),
    ('DATA_ENTRY', 'CLAIM_VIEW'), ('DATA_ENTRY', 'DEFENSE_VIEW'), ('DATA_ENTRY', 'EVIDENCE_VIEW'),
    ('READ_ONLY', 'CLAIM_VIEW'), ('READ_ONLY', 'DEFENSE_VIEW'), ('READ_ONLY', 'EVIDENCE_VIEW')
) as p (role, permission) on p.role = r.code
on conflict do nothing;
