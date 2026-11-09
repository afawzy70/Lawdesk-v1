-- LD-054 (WP4; requirements section 10, owner decision D6): legal deadlines. The rules and the official holidays below are
-- entered and approved by the owner's specialists. This migration inserts NO row into either table: the system states no
-- statutory period, no counting method and no holiday of its own. Additive only.

create table deadline_rule (
    id               uuid primary key default gen_random_uuid(),
    code             varchar(60)   not null constraint deadline_rule_code_shape check (code ~ '^[A-Za-z0-9][A-Za-z0-9_.-]*$'),
    label            varchar(300)  not null constraint deadline_rule_label_visible check (length(btrim(label)) > 0),
    period_days      integer       not null constraint deadline_rule_period_range check (period_days between 1 and 3650),
    -- CALENDAR counts every day; WORKING counts only the days that are neither a closed weekday nor an approved holiday.
    count_method     varchar(10)   not null constraint deadline_rule_method_known check (count_method in ('CALENDAR', 'WORKING')),
    -- The specialist states how the period is counted; there is no default for either choice.
    counts_start_day boolean       not null,
    roll_forward     boolean       not null,
    -- ISO weekdays (1 = Monday ... 7 = Sunday) that are not working days, as a comma separated list; empty = none.
    closed_weekdays  varchar(20)   not null constraint deadline_rule_weekdays_shape check (closed_weekdays ~ '^([1-7](,[1-7]){0,6})?$'),
    -- The statute, article or decision the period comes from: mandatory.
    legal_source     varchar(1000) not null constraint deadline_rule_source_visible check (length(btrim(legal_source)) > 0),
    effective_from   date          not null,
    status           varchar(10)   not null default 'DRAFT' constraint deadline_rule_status_known
        check (status in ('DRAFT', 'APPROVED', 'RETIRED')),
    approved_by      uuid,
    approved_at      timestamptz,
    retired_by       uuid,
    retired_at       timestamptz,
    version          bigint        not null default 0,
    created_at       timestamptz   not null default now(),
    created_by       uuid          not null,
    updated_at       timestamptz   not null default now(),
    updated_by       uuid          not null,
    constraint deadline_rule_code_effective_unique unique (code, effective_from),
    -- An approved rule says who approved it and when; a draft never does.
    constraint deadline_rule_approval_recorded check (
        (status = 'DRAFT' and approved_by is null and approved_at is null)
        or (status in ('APPROVED', 'RETIRED') and approved_by is not null and approved_at is not null)),
    constraint deadline_rule_retirement_recorded check ((status = 'RETIRED') = (retired_at is not null and retired_by is not null))
);
create index deadline_rule_status on deadline_rule (status, effective_from);

create table official_holiday (
    id           uuid primary key default gen_random_uuid(),
    holiday_on   date          not null,
    label        varchar(200)  not null constraint official_holiday_label_visible check (length(btrim(label)) > 0),
    legal_source varchar(1000) not null constraint official_holiday_source_visible check (length(btrim(legal_source)) > 0),
    status       varchar(10)   not null default 'DRAFT' constraint official_holiday_status_known
        check (status in ('DRAFT', 'APPROVED', 'RETIRED')),
    approved_by  uuid,
    approved_at  timestamptz,
    retired_by   uuid,
    retired_at   timestamptz,
    version      bigint        not null default 0,
    created_at   timestamptz   not null default now(),
    created_by   uuid          not null,
    updated_at   timestamptz   not null default now(),
    updated_by   uuid          not null,
    constraint official_holiday_approval_recorded check (
        (status = 'DRAFT' and approved_by is null and approved_at is null)
        or (status in ('APPROVED', 'RETIRED') and approved_by is not null and approved_at is not null)),
    constraint official_holiday_retirement_recorded check ((status = 'RETIRED') = (retired_at is not null and retired_by is not null))
);
-- A holiday is entered once per date; a retired one makes room for a corrected entry.
create unique index official_holiday_date_active on official_holiday (holiday_on) where status <> 'RETIRED';

-- How many days before the due date the in-app reminder is raised. The document gives no lead times, so there is NO row
-- and no default: no reminder is raised until an authorised user configures this (one row at most).
create table deadline_reminder_setting (
    id         boolean primary key default true constraint deadline_reminder_setting_single check (id),
    -- Comma separated whole days before the due date (0 = on the due date); empty = configured to remind on no day.
    lead_days  varchar(60) not null constraint deadline_reminder_lead_shape check (lead_days ~ '^([0-9]{1,3}(,[0-9]{1,3}){0,9})?$'),
    updated_at timestamptz not null default now(),
    updated_by uuid        not null
);
