-- LD-051 (WP2, requirement section 20; scenario S8): facts, evidence items with their role in the defence, the links
-- from evidence to facts, defences and claims, and the follow-up of evidence that is missing. Additive only; the stored
-- documents (case_document) are referenced, never moved or copied.

create table case_fact (
    id         uuid primary key default gen_random_uuid(),
    case_id    uuid        not null references legal_case (id),
    seq        integer     not null constraint case_fact_seq_positive check (seq > 0),
    text       text        not null constraint case_fact_text_visible check (length(btrim(text)) > 0),
    fact_date  date,
    version    bigint      not null default 0,
    created_at timestamptz not null default now(),
    created_by uuid        not null,
    updated_at timestamptz not null default now(),
    updated_by uuid        not null,
    constraint case_fact_seq_per_case unique (case_id, seq)
);

create table evidence_item (
    id                 uuid primary key default gen_random_uuid(),
    case_id            uuid         not null references legal_case (id),
    -- The electronic copy, when there is one; a REQUIRED or UNAVAILABLE item has none yet.
    document_id        uuid references case_document (id),
    name               varchar(300) not null constraint evidence_name_visible check (length(btrim(name)) > 0),
    doc_type           varchar(100),
    issued_on          date,
    issuer             varchar(300),
    received_on        date,
    source             varchar(300),
    role               varchar(14)  not null constraint evidence_role_known check (role in (
        'SUPPORTING', 'OPPONENT', 'DISPUTED', 'REQUIRED', 'UNAVAILABLE', 'SUPPLEMENTARY')),
    importance         varchar(10)  constraint evidence_importance_known
        check (importance in ('LOW', 'MEDIUM', 'HIGH', 'DECISIVE')),
    submitted_to_court boolean      not null default false,
    submitted_on       date,
    submitted_in       varchar(300),
    opponent_responded boolean,
    opponent_response  varchar(2000),
    court_position     varchar(2000),
    notes              varchar(2000),
    version            bigint       not null default 0,
    created_at         timestamptz  not null default now(),
    created_by         uuid         not null,
    updated_at         timestamptz  not null default now(),
    updated_by         uuid         not null,
    constraint evidence_submission_complete check (submitted_to_court or (submitted_on is null and submitted_in is null))
);
create index evidence_case on evidence_item (case_id);
create index evidence_document on evidence_item (document_id) where document_id is not null;

-- One row links one evidence item to exactly one fact, defence or claim; an item has any number of links.
create table evidence_link (
    id          uuid primary key default gen_random_uuid(),
    evidence_id uuid        not null references evidence_item (id),
    fact_id     uuid references case_fact (id),
    defense_id  uuid references defense (id),
    claim_id    uuid references claim (id),
    created_at  timestamptz not null default now(),
    created_by  uuid        not null,
    constraint evidence_link_one_target check (
        (case when fact_id is null then 0 else 1 end + case when defense_id is null then 0 else 1 end
         + case when claim_id is null then 0 else 1 end) = 1)
);
create unique index evidence_link_fact_once on evidence_link (evidence_id, fact_id) where fact_id is not null;
create unique index evidence_link_defense_once on evidence_link (evidence_id, defense_id) where defense_id is not null;
create unique index evidence_link_claim_once on evidence_link (evidence_id, claim_id) where claim_id is not null;
create index evidence_link_fact on evidence_link (fact_id) where fact_id is not null;
create index evidence_link_defense on evidence_link (defense_id) where defense_id is not null;
create index evidence_link_claim on evidence_link (claim_id) where claim_id is not null;

-- Evidence that is needed but missing is followed up by a task of the same case. info_request_id is reserved for the
-- information requests of WP3 and has no foreign key until that table exists.
create table gap_followup (
    id              uuid primary key default gen_random_uuid(),
    evidence_id     uuid        not null references evidence_item (id),
    task_id         uuid references task (id),
    info_request_id uuid,
    created_at      timestamptz not null default now(),
    created_by      uuid        not null,
    constraint gap_followup_target check (task_id is not null or info_request_id is not null),
    constraint gap_followup_task_once unique (evidence_id, task_id)
);
create index gap_followup_task on gap_followup (task_id) where task_id is not null;

create function evidence_link_same_case() returns trigger language plpgsql as $$
begin
    if not exists (
        select 1 from evidence_item e
        where e.id = new.evidence_id
          and (new.fact_id is null or exists (select 1 from case_fact f where f.id = new.fact_id and f.case_id = e.case_id))
          and (new.defense_id is null or exists (select 1 from defense d where d.id = new.defense_id and d.case_id = e.case_id))
          and (new.claim_id is null or exists (select 1 from claim c where c.id = new.claim_id and c.case_id = e.case_id))) then
        raise exception 'the evidence and what it is linked to belong to different cases'
            using errcode = 'check_violation', constraint = 'evidence_link_same_case';
    end if;
    return new;
end $$;
create trigger evidence_link_same_case before insert or update on evidence_link
    for each row execute function evidence_link_same_case();
