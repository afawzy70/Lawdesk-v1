-- LD-053 (WP3; docs/design/LD-DOC-04 section 2): the permissions of information requests, risk assessment and case links,
-- granted to the roles that work on cases. Additive; roles an administrator changed keep their other permissions.
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'INFO_REQUEST_VIEW'), ('ADMIN', 'INFO_REQUEST_MANAGE'), ('ADMIN', 'INFO_REQUEST_ESCALATE'),
    ('ADMIN', 'RISK_VIEW'), ('ADMIN', 'RISK_MANAGE'), ('ADMIN', 'CASE_LINK_MANAGE'),
    ('LEGAL_MANAGER', 'INFO_REQUEST_VIEW'), ('LEGAL_MANAGER', 'INFO_REQUEST_MANAGE'), ('LEGAL_MANAGER', 'INFO_REQUEST_ESCALATE'),
    ('LEGAL_MANAGER', 'RISK_VIEW'), ('LEGAL_MANAGER', 'RISK_MANAGE'), ('LEGAL_MANAGER', 'CASE_LINK_MANAGE'),
    ('SECTION_HEAD', 'INFO_REQUEST_VIEW'), ('SECTION_HEAD', 'INFO_REQUEST_MANAGE'), ('SECTION_HEAD', 'INFO_REQUEST_ESCALATE'),
    ('SECTION_HEAD', 'RISK_VIEW'), ('SECTION_HEAD', 'RISK_MANAGE'), ('SECTION_HEAD', 'CASE_LINK_MANAGE'),
    ('LEGAL_STAFF', 'INFO_REQUEST_VIEW'), ('LEGAL_STAFF', 'INFO_REQUEST_MANAGE'),
    ('LEGAL_STAFF', 'RISK_VIEW'), ('LEGAL_STAFF', 'RISK_MANAGE'), ('LEGAL_STAFF', 'CASE_LINK_MANAGE'),
    ('DATA_ENTRY', 'INFO_REQUEST_VIEW'),
    ('READ_ONLY', 'INFO_REQUEST_VIEW'), ('READ_ONLY', 'RISK_VIEW')
) as p (role, permission) on p.role = r.code
on conflict do nothing;
