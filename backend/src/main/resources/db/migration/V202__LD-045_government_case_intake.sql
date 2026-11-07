-- LD-045: government case intake (docs/delivery/GOVERNMENT-INTAKE-ACCEPTANCE.md). Additive only: every new column is
-- nullable, and a null means "not specified" for records that predate the change; nothing is guessed or backfilled.
alter table legal_case
    -- الرقم الآلي للقضية: typed by the user, kept as text (leading zeros), the same on every stage of the case. No
    -- format or length rule comes from a documented requirement; 100 characters is only the column's technical bound.
    add column automated_no         varchar(100),
    -- موقف الهيئة من القضية.
    add column authority_position   varchar(16),
    -- الأثر المالي: one value for the whole case, in Kuwaiti dinars with three decimals (fils).
    add column financial_impact     varchar(8),
    add column financial_amount     numeric(15, 3),
    -- العضو المختص and العضو البديل. A case without a primary member keeps today's rules (any holder of the
    -- permission may edit it).
    add column primary_member_id    uuid references app_user (id),
    add column alternate_member_id  uuid references app_user (id),
    add constraint legal_case_automated_no_visible
        check (automated_no is null or length(btrim(automated_no)) > 0),
    add constraint legal_case_authority_position_known
        check (authority_position in ('PLAINTIFF', 'DEFENDANT')),
    add constraint legal_case_financial_impact_known
        check (financial_impact in ('YES', 'NO')),
    -- A positive amount exactly when there is a financial impact; "no impact" never keeps an amount. The coalesce
    -- matters: a check that evaluates to null passes, so an amount without an impact would slip through otherwise.
    add constraint legal_case_financial_consistent
        check (coalesce((financial_impact = 'YES' and financial_amount > 0)
            or (financial_impact = 'NO' and financial_amount is null)
            or (financial_impact is null and financial_amount is null), false)),
    add constraint legal_case_members_distinct
        check (alternate_member_id is null
            or (primary_member_id is not null and alternate_member_id <> primary_member_id));

create index legal_case_primary_member on legal_case (primary_member_id);
create index legal_case_alternate_member on legal_case (alternate_member_id);

-- The side of a party on a case (مدعي / مدعى عليه); null for links made before this change. The party's capacity is
-- this side, so it can never contradict it.
alter table case_party
    add column side varchar(16),
    add constraint case_party_side_known check (side in ('PLAINTIFF', 'DEFENDANT'));

-- The authority (الهيئة) as an actual party: one organisation row, marked, shared by every case.
alter table party
    add column authority boolean not null default false;
create unique index party_authority_single on party ((true)) where authority;

-- "الرقم الآلي للطرف" replaces the label of the former national number. The values stay exactly as stored; the
-- Egyptian 14-digit rule no longer describes a Kuwaiti authority's parties, so the column accepts any visible text
-- (owner assumption to review, see the acceptance file).
alter table party
    drop constraint party_national_id_check,
    alter column national_id type varchar(50),
    add constraint party_national_id_visible check (national_id is null or length(btrim(national_id)) > 0);
