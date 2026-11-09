-- LD-057 (WP9; docs/design/LD-DOC-04 section 2): SETTLEMENT_MANAGE, SETTLEMENT_APPROVE, DIRECTIVE_MANAGE, TEMPLATE_MANAGE and
-- SEARCH_ALL, granted to the roles that work on cases. Additive; roles an administrator changed keep their other permissions.
-- Settlements are approved by those who supervise cases; a case's specialist proposes and prepares them.
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'SETTLEMENT_MANAGE'), ('ADMIN', 'SETTLEMENT_APPROVE'), ('ADMIN', 'DIRECTIVE_MANAGE'),
    ('ADMIN', 'TEMPLATE_MANAGE'), ('ADMIN', 'SEARCH_ALL'),
    ('LEGAL_MANAGER', 'SETTLEMENT_MANAGE'), ('LEGAL_MANAGER', 'SETTLEMENT_APPROVE'), ('LEGAL_MANAGER', 'DIRECTIVE_MANAGE'),
    ('LEGAL_MANAGER', 'TEMPLATE_MANAGE'), ('LEGAL_MANAGER', 'SEARCH_ALL'),
    ('SECTION_HEAD', 'SETTLEMENT_MANAGE'), ('SECTION_HEAD', 'SETTLEMENT_APPROVE'), ('SECTION_HEAD', 'DIRECTIVE_MANAGE'),
    ('SECTION_HEAD', 'TEMPLATE_MANAGE'), ('SECTION_HEAD', 'SEARCH_ALL'),
    ('LEGAL_STAFF', 'SETTLEMENT_MANAGE'), ('LEGAL_STAFF', 'SEARCH_ALL'),
    ('READ_ONLY', 'SEARCH_ALL')
) as p (role, permission) on p.role = r.code
on conflict do nothing;
