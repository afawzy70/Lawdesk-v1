-- LD-022 judgments: the judgment that ends a proceeding (docs/delivery/PHASE-4-ACCEPTANCE.md). One per proceeding;
-- interim rulings are special decisions (LD-023).
create table judgment (
    id            uuid primary key default gen_random_uuid(),
    proceeding_id uuid          not null constraint judgment_one_per_proceeding unique
                                references proceeding (id),
    judgment_date date          not null,
    -- From the client's point of view.
    outcome       varchar(20)   not null constraint judgment_outcome_known
                                check (outcome in ('IN_FAVOUR', 'AGAINST', 'PARTIAL', 'DISMISSED', 'OTHER')),
    summary       varchar(4000),
    -- حكم نهائي / بات: informational in this phase.
    final         boolean       not null default false,
    version       bigint        not null default 0,
    created_at    timestamptz   not null default now(),
    created_by    uuid          not null,
    updated_at    timestamptz   not null default now(),
    updated_by    uuid          not null
);
