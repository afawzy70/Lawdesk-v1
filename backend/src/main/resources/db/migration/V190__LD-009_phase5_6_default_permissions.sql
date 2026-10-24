-- architecture/CONTRACTS.md §8: the Phase 5 (legal operations) and Phase 6 (reporting) permissions, granted to the
-- seeded roles before either workstream starts. In the Phase 2 range because roles are Phase 2 data (it must run after
-- V100 on a new database); written by the integration owner. Additive only; roles
-- an administrator created or changed keep their other permissions.
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'HEARING_VIEW'), ('ADMIN', 'HEARING_MANAGE'), ('ADMIN', 'EXPERT_VIEW'), ('ADMIN', 'EXPERT_MANAGE'),
    ('ADMIN', 'DOCUMENT_VIEW'), ('ADMIN', 'DOCUMENT_MANAGE'), ('ADMIN', 'TASK_VIEW'), ('ADMIN', 'TASK_MANAGE'),
    ('ADMIN', 'NOTE_MANAGE'), ('ADMIN', 'REPORT_VIEW'), ('ADMIN', 'REPORT_EXPORT'),
    ('LEGAL_MANAGER', 'HEARING_VIEW'), ('LEGAL_MANAGER', 'HEARING_MANAGE'), ('LEGAL_MANAGER', 'EXPERT_VIEW'),
    ('LEGAL_MANAGER', 'EXPERT_MANAGE'), ('LEGAL_MANAGER', 'DOCUMENT_VIEW'), ('LEGAL_MANAGER', 'DOCUMENT_MANAGE'),
    ('LEGAL_MANAGER', 'TASK_VIEW'), ('LEGAL_MANAGER', 'TASK_MANAGE'), ('LEGAL_MANAGER', 'NOTE_MANAGE'),
    ('LEGAL_MANAGER', 'REPORT_VIEW'), ('LEGAL_MANAGER', 'REPORT_EXPORT'),
    ('LEGAL_STAFF', 'HEARING_VIEW'), ('LEGAL_STAFF', 'HEARING_MANAGE'), ('LEGAL_STAFF', 'EXPERT_VIEW'),
    ('LEGAL_STAFF', 'DOCUMENT_VIEW'), ('LEGAL_STAFF', 'DOCUMENT_MANAGE'), ('LEGAL_STAFF', 'TASK_VIEW'),
    ('LEGAL_STAFF', 'TASK_MANAGE'), ('LEGAL_STAFF', 'NOTE_MANAGE')
) as p (role, permission) on p.role = r.code
on conflict do nothing;
