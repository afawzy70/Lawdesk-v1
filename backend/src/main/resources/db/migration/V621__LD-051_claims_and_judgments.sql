-- LD-051 (WP2, requirements sections 5 and 9; scenarios S1, S2, S13): the claims of a case with their full revision
-- history, the extended judgment and the result of the court on each claim in each judgment. Additive only: the
-- existing judgment columns (outcome, summary, final) stay valid and nothing is backfilled.

-- طلبات الدعوى. The sequence number is unique inside the case and never reused; a claim is never deleted (a withdrawn
-- claim gets the status WAIVED or ABANDONED), so the results recorded against it stay meaningful.
create table claim (
    id                   uuid primary key default gen_random_uuid(),
    case_id              uuid         not null references legal_case (id),
    seq                  integer      not null constraint claim_seq_positive check (seq > 0),
    text                 text         not null constraint claim_text_visible check (length(btrim(text)) > 0),
    kind                 varchar(12)  not null constraint claim_kind_known
        check (kind in ('PRIMARY', 'ALTERNATIVE', 'INCIDENTAL', 'ANCILLARY', 'URGENT', 'OTHER')),
    submitted_by_party_id uuid references party (id),
    submitted_on         date,
    stage_id             uuid references proceeding (id),
    source_document_id   uuid references case_document (id),
    status               varchar(10)  not null default 'STANDING' constraint claim_status_known
        check (status in ('STANDING', 'AMENDED', 'WAIVED', 'ABANDONED', 'OTHER')),
    notes                varchar(2000),
    version              bigint       not null default 0,
    created_at           timestamptz  not null default now(),
    created_by           uuid         not null,
    updated_at           timestamptz  not null default now(),
    updated_by           uuid         not null,
    constraint claim_seq_per_case unique (case_id, seq)
);
create index claim_stage on claim (stage_id) where stage_id is not null;
create index claim_document on claim (source_document_id) where source_document_id is not null;
create index claim_party on claim (submitted_by_party_id) where submitted_by_party_id is not null;

-- A full snapshot of the claim at each revision (rev 1 is the claim as first recorded). Append-only.
create table claim_revision (
    id                    uuid primary key default gen_random_uuid(),
    claim_id              uuid         not null references claim (id),
    rev                   integer      not null constraint claim_revision_rev_positive check (rev > 0),
    text                  text         not null,
    kind                  varchar(12)  not null,
    status                varchar(10)  not null,
    submitted_by_party_id uuid,
    submitted_on          date,
    stage_id              uuid,
    source_document_id    uuid,
    notes                 varchar(2000),
    reason                varchar(500),
    changed_by            uuid         not null,
    changed_at            timestamptz  not null default now(),
    constraint claim_revision_once unique (claim_id, rev)
);

create function claim_revision_is_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'claim revisions are append-only' using errcode = 'check_violation';
end $$;
create trigger claim_revision_no_change before update or delete on claim_revision
    for each row execute function claim_revision_is_append_only();

-- The extended judgment (section 9). Every column is optional: "not specified" is a value. The court is the one of the
-- proceeding the judgment belongs to; the amounts are two of the five separate amounts of decision D5.
alter table judgment
    add column chamber            varchar(100),
    add column judgment_type_id   uuid references lookup_value (id),
    add column nature_id          uuid references lookup_value (id),
    add column operative_text     text,
    add column reasons            text,
    add column costs_amount       numeric(15, 3),
    add column lawyer_fees_amount numeric(15, 3),
    add column appealable         boolean,
    add column notified_on        date,
    add column judgment_status    varchar(10),
    add column copy_document_id   uuid references case_document (id),
    add constraint judgment_chamber_visible check (chamber is null or length(btrim(chamber)) > 0),
    add constraint judgment_costs_not_negative check (costs_amount is null or costs_amount >= 0),
    add constraint judgment_fees_not_negative check (lawyer_fees_amount is null or lawyer_fees_amount >= 0),
    add constraint judgment_status_known check (judgment_status in ('ISSUED', 'NOTIFIED', 'CHALLENGED', 'OTHER'));
create index judgment_copy_document on judgment (copy_document_id) where copy_document_id is not null;

-- نتيجة كل طلب في كل حكم. One row per (judgment, claim); a later judgment (another stage) has its own rows, so no result
-- ever replaces an earlier one.
create table claim_result (
    id             uuid primary key default gen_random_uuid(),
    judgment_id    uuid         not null references judgment (id),
    claim_id       uuid         not null references claim (id),
    result         varchar(24)  not null constraint claim_result_known check (result in (
        'ACCEPTED', 'PARTIAL', 'REJECTED', 'INADMISSIBLE', 'NO_JURISDICTION', 'REFERRED', 'EXTINGUISHED',
        'WAIVER_RECORDED', 'ABANDONMENT_RECORDED', 'NOT_DECIDED', 'DEFERRED', 'OTHER')),
    operative      text,
    reasons        text,
    awarded_amount numeric(15, 3) constraint claim_result_amount_not_negative check (awarded_amount >= 0),
    notes          varchar(2000),
    version        bigint       not null default 0,
    created_at     timestamptz  not null default now(),
    created_by     uuid         not null,
    updated_at     timestamptz  not null default now(),
    updated_by     uuid         not null,
    constraint claim_result_once unique (judgment_id, claim_id)
);
create index claim_result_claim on claim_result (claim_id);

-- The claim and the judgment of a result belong to the same case (a judgment reaches its case through its proceeding).
create function claim_result_same_case() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from claim c join judgment j on j.id = new.judgment_id
                   join proceeding p on p.id = j.proceeding_id where c.id = new.claim_id and c.case_id = p.case_id) then
        raise exception 'the claim and the judgment belong to different cases'
            using errcode = 'check_violation', constraint = 'claim_result_same_case';
    end if;
    return new;
end $$;
create trigger claim_result_same_case before insert or update of judgment_id, claim_id on claim_result
    for each row execute function claim_result_same_case();
