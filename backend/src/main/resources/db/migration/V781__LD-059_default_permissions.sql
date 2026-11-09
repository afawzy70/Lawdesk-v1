-- LD-059 (WP10; docs/design/LD-DOC-04 section 3): ASSISTANT_USE for the roles that work on cases and the administrator role.
-- Additive; roles an administrator changed keep their other permissions. Holding it never adds a case to what a user may see:
-- the assistant reads through the same case-access rule as every other module (owner decision D3).
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'ASSISTANT_USE'), ('LEGAL_MANAGER', 'ASSISTANT_USE'), ('SECTION_HEAD', 'ASSISTANT_USE'), ('LEGAL_STAFF', 'ASSISTANT_USE')
) as p (role, permission) on p.role = r.code
on conflict do nothing;
