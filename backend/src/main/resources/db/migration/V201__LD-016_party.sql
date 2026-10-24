-- LD-016: legal parties (people and organisations) and their role on a case (docs/delivery/PHASE-3-ACCEPTANCE.md).
-- Actor columns hold the user id without a foreign key (architecture/CONTRACTS.md §2).
create table party (
    id              uuid primary key default gen_random_uuid(),
    kind            varchar(16)  not null check (kind in ('PERSON', 'ORGANIZATION')),
    name            varchar(200) not null check (length(btrim(name)) > 0),
    -- Egyptian national number of a person: 14 digits.
    national_id     varchar(14) check (national_id ~ '^[0-9]{14}$'),
    -- Commercial registration number of an organisation.
    registration_no varchar(50),
    phone           varchar(30) check (phone ~ '^[0-9 +()-]+$'),
    email           varchar(254),
    address         varchar(500),
    version         bigint       not null default 0,
    created_at      timestamptz  not null default now(),
    created_by      uuid         not null,
    updated_at      timestamptz  not null default now(),
    updated_by      uuid         not null,
    constraint party_national_id_person check (kind = 'PERSON' or national_id is null),
    constraint party_registration_no_organization check (kind = 'ORGANIZATION' or registration_no is null)
);

create unique index party_national_id_key on party (national_id) where national_id is not null;
create index party_name_idx on party (lower(name));

create table case_party (
    id         uuid primary key default gen_random_uuid(),
    case_id    uuid        not null references legal_case (id),
    party_id   uuid        not null references party (id),
    role       varchar(16) not null check (role in ('CLIENT', 'OPPONENT', 'OTHER')),
    created_at timestamptz not null default now(),
    created_by uuid        not null,
    constraint case_party_once unique (case_id, party_id)
);

create index case_party_party_idx on case_party (party_id);
