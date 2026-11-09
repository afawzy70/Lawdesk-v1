-- LD-051 (WP2, requirement section 17; scenarios S3, S13): legal defences, their assertion in each proceeding and the
-- court's position on each of them in each judgment. Additive only.

-- مكتبة الدفوع: reusable wording. A template is never copied into a case by the system; a member reads it, adapts it and
-- saves a defence of their own (template_id only records where the wording came from).
create table defense_template (
    id          uuid primary key default gen_random_uuid(),
    title       varchar(300) not null constraint defense_template_title_visible check (length(btrim(title)) > 0),
    kind        varchar(12)  not null constraint defense_template_kind_known
        check (kind in ('FORMAL', 'PROCEDURAL', 'SUBSTANTIVE', 'OTHER')),
    text        text,
    legal_basis text,
    status      varchar(10)  not null default 'DRAFT' constraint defense_template_status_known
        check (status in ('DRAFT', 'REVIEWED', 'APPROVED')),
    reviewed_by uuid,
    reviewed_at timestamptz,
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    created_by  uuid         not null,
    updated_at  timestamptz  not null default now(),
    updated_by  uuid         not null
);

create table defense (
    id               uuid primary key default gen_random_uuid(),
    case_id          uuid         not null references legal_case (id),
    seq              integer      not null constraint defense_seq_positive check (seq > 0),
    title            varchar(300) not null constraint defense_title_visible check (length(btrim(title)) > 0),
    kind             varchar(12)  not null constraint defense_kind_known
        check (kind in ('FORMAL', 'PROCEDURAL', 'SUBSTANTIVE', 'OTHER')),
    argument         text,
    legal_basis      text,
    facts            text,
    legal_texts      text,
    memo_document_id uuid references case_document (id),
    raised_on        date,
    raised_stage_id  uuid references proceeding (id),
    prepared_by      uuid,
    notes            varchar(2000),
    template_id      uuid references defense_template (id),
    version          bigint       not null default 0,
    created_at       timestamptz  not null default now(),
    created_by       uuid         not null,
    updated_at       timestamptz  not null default now(),
    updated_by       uuid         not null,
    constraint defense_seq_per_case unique (case_id, seq)
);
create index defense_memo on defense (memo_document_id) where memo_document_id is not null;
create index defense_stage on defense (raised_stage_id) where raised_stage_id is not null;
create index defense_template_ref on defense (template_id) where template_id is not null;

-- Whether the authority raised, re-asserted or did not assert the defence in each proceeding (first instance, appeal,
-- cassation): one row per (defence, proceeding), so each stage keeps its own history.
create table defense_assertion (
    id               uuid primary key default gen_random_uuid(),
    defense_id       uuid         not null references defense (id),
    proceeding_id    uuid         not null references proceeding (id),
    status           varchar(12)  not null constraint defense_assertion_status_known
        check (status in ('RAISED', 'RE_ASSERTED', 'NOT_ASSERTED')),
    on_date          date,
    memo_document_id uuid references case_document (id),
    version          bigint       not null default 0,
    created_at       timestamptz  not null default now(),
    created_by       uuid         not null,
    updated_at       timestamptz  not null default now(),
    updated_by       uuid         not null,
    constraint defense_assertion_once unique (defense_id, proceeding_id)
);
create index defense_assertion_proceeding on defense_assertion (proceeding_id);

-- The court's position on a defence in one judgment; each judgment has its own, so a later judgment never replaces it.
create table defense_position (
    id          uuid primary key default gen_random_uuid(),
    defense_id  uuid         not null references defense (id),
    judgment_id uuid         not null references judgment (id),
    position    varchar(14)  not null constraint defense_position_known check (position in (
        'ACCEPTED', 'REJECTED', 'PARTIAL', 'NOT_ADDRESSED', 'MOOT', 'DEFERRED', 'OTHER')),
    reasons     text,
    operative   text,
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    created_by  uuid         not null,
    updated_at  timestamptz  not null default now(),
    updated_by  uuid         not null,
    constraint defense_position_once unique (defense_id, judgment_id)
);
create index defense_position_judgment on defense_position (judgment_id);

create function defense_position_same_case() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from defense d join judgment j on j.id = new.judgment_id
                   join proceeding p on p.id = j.proceeding_id where d.id = new.defense_id and d.case_id = p.case_id) then
        raise exception 'the defence and the judgment belong to different cases'
            using errcode = 'check_violation', constraint = 'defense_position_same_case';
    end if;
    return new;
end $$;
create trigger defense_position_same_case before insert or update of defense_id, judgment_id on defense_position
    for each row execute function defense_position_same_case();

create function defense_assertion_same_case() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from defense d join proceeding p on p.id = new.proceeding_id
                   where d.id = new.defense_id and d.case_id = p.case_id) then
        raise exception 'the defence and the proceeding belong to different cases'
            using errcode = 'check_violation', constraint = 'defense_assertion_same_case';
    end if;
    return new;
end $$;
create trigger defense_assertion_same_case before insert or update of defense_id, proceeding_id on defense_assertion
    for each row execute function defense_assertion_same_case();
