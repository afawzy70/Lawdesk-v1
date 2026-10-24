-- LD-023 special judicial decisions, such as an enforcement suspension (وقف التنفيذ), taken within a proceeding
-- (docs/delivery/PHASE-4-ACCEPTANCE.md). Informational in this phase: a suspension does not change the judgment it
-- names.
create table special_decision (
    id            uuid primary key default gen_random_uuid(),
    proceeding_id uuid          not null references proceeding (id),
    decision_type varchar(32)   not null constraint special_decision_type_known
                                check (decision_type in ('ENFORCEMENT_SUSPENSION', 'URGENT_MEASURE', 'OTHER')),
    decision_date date          not null,
    effect        varchar(20)   not null constraint special_decision_effect_known
                                check (effect in ('GRANTED', 'PARTIALLY_GRANTED', 'REFUSED')),
    summary       varchar(4000),
    -- The judgment the decision affects; an enforcement suspension always names one.
    judgment_id   uuid references judgment (id),
    version       bigint        not null default 0,
    created_at    timestamptz   not null default now(),
    created_by    uuid          not null,
    updated_at    timestamptz   not null default now(),
    updated_by    uuid          not null,
    constraint special_decision_suspension_names_judgment
        check (decision_type <> 'ENFORCEMENT_SUSPENSION' or judgment_id is not null)
);

create index special_decision_proceeding on special_decision (proceeding_id);
