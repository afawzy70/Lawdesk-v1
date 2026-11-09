-- LD-054 (WP4; docs/design/LD-DOC-04 sections 2 and 3): the deadline permissions, granted to the roles that work on cases.
-- Approval of a deadline is for the competent members (the case's member for a legal staff member, checked per case by
-- the case-access policy). Additive; roles an administrator changed keep their other permissions.
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'DEADLINE_VIEW'), ('ADMIN', 'DEADLINE_MANAGE'), ('ADMIN', 'DEADLINE_APPROVE'),
    ('LEGAL_MANAGER', 'DEADLINE_VIEW'), ('LEGAL_MANAGER', 'DEADLINE_MANAGE'), ('LEGAL_MANAGER', 'DEADLINE_APPROVE'),
    ('SECTION_HEAD', 'DEADLINE_VIEW'), ('SECTION_HEAD', 'DEADLINE_MANAGE'), ('SECTION_HEAD', 'DEADLINE_APPROVE'),
    ('LEGAL_STAFF', 'DEADLINE_VIEW'), ('LEGAL_STAFF', 'DEADLINE_MANAGE'), ('LEGAL_STAFF', 'DEADLINE_APPROVE'),
    ('DATA_ENTRY', 'DEADLINE_VIEW'),
    ('READ_ONLY', 'DEADLINE_VIEW')
) as p (role, permission) on p.role = r.code
on conflict do nothing;
