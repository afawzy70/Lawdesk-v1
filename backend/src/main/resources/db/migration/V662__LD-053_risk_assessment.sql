-- LD-053 (WP3; requirements section 19; docs/design/LD-DOC-05 section 6): legal risk assessments. An assessment is
-- never edited: a change of the position is a new version, and the earlier ones stay. The assessment is a decision aid,
-- not a judicial forecast. Additive only.

create table risk_assessment (
    id                 uuid primary key default gen_random_uuid(),
    case_id            uuid          not null references legal_case (id),
    version_no         integer       not null constraint risk_version_positive check (version_no > 0),
    -- مرتفعة جدا ... منخفضة: the overall level, always with its reasons.
    level              varchar(10)   not null constraint risk_level_known check (level in ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    level_reasons      varchar(4000) not null constraint risk_level_reasons_visible check (length(btrim(level_reasons)) > 0),
    -- D5: the potential loss is its own KWD amount, never derived from or mixed with another of the five amounts.
    potential_loss     numeric(15,3) constraint risk_potential_loss_range check (potential_loss is null or potential_loss >= 0),
    -- What caused this version: a new document, a judgment, a decision, a change of position, a periodic review or by hand.
    trigger_kind       varchar(16)   not null constraint risk_trigger_known
        check (trigger_kind in ('NEW_DOCUMENT', 'JUDGMENT', 'DECISION', 'POSITION_CHANGE', 'PERIODIC', 'MANUAL')),
    trigger_note       varchar(500),
    trigger_ref_id     uuid,
    assessed_by        uuid          not null,
    assessed_at        timestamptz   not null default now(),
    constraint risk_version_per_case unique (case_id, version_no),
    constraint risk_trigger_note_visible check (trigger_note is null or length(btrim(trigger_note)) > 0)
);
create index risk_assessment_case on risk_assessment (case_id, version_no desc);
create index risk_assessment_level on risk_assessment (level);

-- The ten assessment elements of the requirements document, each with a rating and a reason.
create table risk_assessment_element (
    assessment_id uuid         not null references risk_assessment (id),
    code          varchar(40)  not null constraint risk_element_code_known check (code in (
        'LEGAL_POSITION_STRENGTH', 'EVIDENCE_STRENGTH', 'ADMINISTRATIVE_PROCEDURE_SOUNDNESS', 'POTENTIAL_PROCEDURAL_DEFECTS',
        'SIMILAR_PRECEDENTS', 'JUDICIAL_TREND_STABILITY', 'POTENTIAL_FINANCIAL_LOSS', 'ADMINISTRATIVE_IMPACT',
        'SPILLOVER_TO_OTHER_CASES', 'INFORMATION_AVAILABILITY')),
    rating        varchar(12)  not null constraint risk_element_rating_known
        check (rating in ('NOT_ASSESSED', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    reason        varchar(2000) constraint risk_element_reason_visible check (reason is null or length(btrim(reason)) > 0),
    primary key (assessment_id, code)
);

-- A version is written once: the database refuses an update of an assessment or of its elements.
create function risk_assessment_never_edited() returns trigger language plpgsql as $$
begin
    raise exception 'a risk assessment is never edited; record a new version'
        using errcode = 'check_violation', constraint = 'risk_assessment_immutable';
end $$;
create trigger risk_assessment_never_edited before update on risk_assessment
    for each row execute function risk_assessment_never_edited();
create trigger risk_element_never_edited before update on risk_assessment_element
    for each row execute function risk_assessment_never_edited();

-- The plan to reduce a high risk: what caused it, what to do, who does it and by when. The plan changes as work proceeds.
create table risk_remediation (
    id              uuid primary key default gen_random_uuid(),
    assessment_id   uuid          not null references risk_assessment (id),
    case_id         uuid          not null references legal_case (id),
    cause           varchar(1000) not null constraint risk_remediation_cause_visible check (length(btrim(cause)) > 0),
    action          varchar(1000) not null constraint risk_remediation_action_visible check (length(btrim(action)) > 0),
    documents       varchar(1000),
    defenses        varchar(1000),
    unit_to_address varchar(300),
    owner_id        uuid,
    target_date     date,
    status          varchar(12)   not null default 'OPEN' constraint risk_remediation_status_known
        check (status in ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    version         bigint        not null default 0,
    created_at      timestamptz   not null default now(),
    created_by      uuid          not null,
    updated_at      timestamptz   not null default now(),
    updated_by      uuid          not null
);
create index risk_remediation_assessment on risk_remediation (assessment_id);
create index risk_remediation_case on risk_remediation (case_id);
