-- LD-050 (WP1b, requirements section 1, 6 and owner decisions D1, D2, D5): the rest of the case record. Additive only:
-- every new column is nullable, null means "not specified" and nothing is backfilled or guessed.
alter table legal_case
    -- الدائرة المختصة.
    add column chamber            varchar(100),
    -- تاريخ ورود القضية إلى الإدارة القانونية (opened_on stays the date the claim was filed).
    add column received_on        date,
    -- الجهة التي أحالت القضية: a unit of the authority's tree, and/or free text for a referrer outside it.
    add column referring_unit_id  uuid references org_unit (id),
    add column referred_by        varchar(200),
    -- موضوع الدعوى وملخص الوقائع.
    add column facts              text,
    -- القيمة المالية المطالب بها (D5): one of the five separate amounts, KWD with three decimals. The other four live on
    -- judgments, settlements, risk assessments and the financial impact the case already has.
    add column claimed_amount     numeric(15, 3),
    add column importance         varchar(10),
    add column internal_notes     text,
    add constraint legal_case_importance_known check (importance in ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    add constraint legal_case_claimed_amount_not_negative check (claimed_amount is null or claimed_amount >= 0),
    add constraint legal_case_chamber_visible check (chamber is null or length(btrim(chamber)) > 0),
    add constraint legal_case_facts_visible check (facts is null or length(btrim(facts)) > 0);
create index legal_case_referring_unit on legal_case (referring_unit_id) where referring_unit_id is not null;

-- D2: the nature of the dispute is separate from the track and the stage. Constitutional and other joined the natures;
-- appeal, cassation, enforcement and grievance stay stages of a proceeding, never case types.
alter table legal_case drop constraint legal_case_case_type_check;
alter table legal_case add constraint legal_case_case_type_check
    check (case_type in ('CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE', 'CONSTITUTIONAL', 'OTHER'));

-- الجهة الإدارية المعنية بالقضية (section 6): sector > department > section of the authority, one primary and any number
-- of supporting units. The names are copied when the involvement is recorded, so renaming or restructuring later
-- does not rewrite the history of the case. Removal is a mark, not a delete.
create table case_org_involvement (
    id                       uuid primary key default gen_random_uuid(),
    case_id                  uuid         not null references legal_case (id),
    org_unit_id              uuid references org_unit (id),
    is_primary               boolean      not null default false,
    sector_name_snapshot     varchar(200),
    department_name_snapshot varchar(200),
    section_name_snapshot    varchar(200),
    contact_name             varchar(200),
    contact_info             varchar(300),
    referral_date            date,
    requested_from_unit      varchar(1000),
    notes                    varchar(2000),
    version                  bigint       not null default 0,
    created_at               timestamptz  not null default now(),
    created_by               uuid         not null,
    updated_at               timestamptz  not null default now(),
    updated_by               uuid         not null,
    removed_at               timestamptz,
    removed_by               uuid,
    constraint case_org_involvement_target check (org_unit_id is not null or sector_name_snapshot is not null
        or department_name_snapshot is not null or section_name_snapshot is not null),
    constraint case_org_involvement_removal_complete check ((removed_at is null) = (removed_by is null)),
    constraint case_org_involvement_primary_active check (not is_primary or removed_at is null)
);
create unique index case_org_involvement_one_primary on case_org_involvement (case_id) where is_primary;
create index case_org_involvement_case on case_org_involvement (case_id) where removed_at is null;
create index case_org_involvement_unit on case_org_involvement (org_unit_id) where removed_at is null;
