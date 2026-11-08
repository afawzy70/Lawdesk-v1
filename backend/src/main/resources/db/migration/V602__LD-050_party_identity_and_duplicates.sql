-- LD-050 (WP1c, requirements sections 2 and 3, owner decisions D4 and the duplicate rule). Additive only.
--
-- D4: "الرقم المدني أو رقم المنشأة" are two distinct identities by the kind of party. The stored columns keep their
-- names and values (national_id for a person, registration_no for an organisation): only the labels change, and
-- nothing is validated beyond a visible value, so old values that do not match any format are never touched.
-- Who may read the whole value is a permission of its own (PARTY_ID_VIEW), apart from changing the party.
alter table party
    add column confidential boolean not null default false,
    add column notes        varchar(2000);

-- المحامي أو الوكيل of a party on one case.
create table case_party_counsel (
    id         uuid primary key default gen_random_uuid(),
    case_id    uuid         not null references legal_case (id),
    party_id   uuid         not null references party (id),
    kind       varchar(8)   not null constraint case_party_counsel_kind_known check (kind in ('LAWYER', 'AGENT')),
    name       varchar(200) not null constraint case_party_counsel_name_visible check (length(btrim(name)) > 0),
    phone      varchar(30)  constraint case_party_counsel_phone_format check (phone ~ '^[0-9 +()-]+$'),
    email      varchar(254),
    notes      varchar(1000),
    created_at timestamptz  not null default now(),
    created_by uuid         not null,
    removed_at timestamptz,
    removed_by uuid,
    constraint case_party_counsel_on_case foreign key (case_id, party_id) references case_party (case_id, party_id),
    constraint case_party_counsel_removal_complete check ((removed_at is null) = (removed_by is null))
);
create index case_party_counsel_case on case_party_counsel (case_id, party_id) where removed_at is null;

-- Duplicate registration (section 3): a possible duplicate is warned about, never blocked and never merged. When the
-- registrar says the case is different (a new stage, or an independent claim), the decision is kept with who made it.
create table case_duplicate_hint (
    id            uuid primary key default gen_random_uuid(),
    case_id       uuid         not null references legal_case (id),
    other_case_id uuid         not null references legal_case (id),
    kind          varchar(8)   not null constraint case_duplicate_hint_kind_known check (kind in ('EXACT', 'SIMILAR')),
    reasons       varchar(500) not null,
    decision      varchar(20)  not null constraint case_duplicate_hint_decision_known
        check (decision in ('DISTINCT', 'LINKED', 'DUPLICATE_CONFIRMED')),
    note          varchar(500),
    decided_by    uuid         not null,
    decided_at    timestamptz  not null default now(),
    constraint case_duplicate_hint_other check (case_id <> other_case_id)
);
create index case_duplicate_hint_case on case_duplicate_hint (case_id);
create index case_duplicate_hint_other on case_duplicate_hint (other_case_id);

insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'PARTY_ID_VIEW'),
    ('LEGAL_MANAGER', 'PARTY_ID_VIEW'), ('SECTION_HEAD', 'PARTY_ID_VIEW'), ('LEGAL_STAFF', 'PARTY_ID_VIEW'),
    ('DATA_ENTRY', 'PARTY_ID_VIEW')
) as p (role, permission) on p.role = r.code
on conflict do nothing;
