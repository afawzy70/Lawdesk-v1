-- LD-055 (WP5, section 13): links from a document to the claim, defence, hearing, judgment or task it concerns, and the text
-- extracted from each stored version (read from the file or by local OCR) that the archive search runs over. Additive.

create table document_link (
    id          uuid primary key default gen_random_uuid(),
    document_id uuid        not null references case_document (id) on delete cascade,
    claim_id    uuid references claim (id),
    defense_id  uuid references defense (id),
    hearing_id  uuid references hearing (id),
    judgment_id uuid references judgment (id),
    task_id     uuid references task (id),
    note        varchar(500),
    created_at  timestamptz not null default now(),
    created_by  uuid        not null,
    constraint document_link_one_target check (
        (claim_id is not null)::int + (defense_id is not null)::int + (hearing_id is not null)::int
        + (judgment_id is not null)::int + (task_id is not null)::int = 1)
);
create unique index document_link_claim_once on document_link (document_id, claim_id) where claim_id is not null;
create unique index document_link_defense_once on document_link (document_id, defense_id) where defense_id is not null;
create unique index document_link_hearing_once on document_link (document_id, hearing_id) where hearing_id is not null;
create unique index document_link_judgment_once on document_link (document_id, judgment_id) where judgment_id is not null;
create unique index document_link_task_once on document_link (document_id, task_id) where task_id is not null;
create index document_link_claim on document_link (claim_id) where claim_id is not null;
create index document_link_defense on document_link (defense_id) where defense_id is not null;
create index document_link_hearing on document_link (hearing_id) where hearing_id is not null;
create index document_link_judgment on document_link (judgment_id) where judgment_id is not null;
create index document_link_task on document_link (task_id) where task_id is not null;

-- The record a document is linked to belongs to the same case as the document.
create function document_link_same_case() returns trigger language plpgsql as $$
declare
    doc_case uuid;
    target_case uuid;
begin
    select case_id into doc_case from case_document where id = new.document_id;
    if new.claim_id is not null then
        select case_id into target_case from claim where id = new.claim_id;
    elsif new.defense_id is not null then
        select case_id into target_case from defense where id = new.defense_id;
    elsif new.hearing_id is not null then
        select case_id into target_case from hearing where id = new.hearing_id;
    elsif new.judgment_id is not null then
        select p.case_id into target_case from judgment j join proceeding p on p.id = j.proceeding_id where j.id = new.judgment_id;
    else
        select case_id into target_case from task where id = new.task_id;
    end if;
    if target_case is distinct from doc_case then
        raise exception 'the document and the linked record belong to different cases'
            using errcode = 'check_violation', constraint = 'document_link_same_case';
    end if;
    return new;
end $$;
create trigger document_link_same_case before insert or update on document_link
    for each row execute function document_link_same_case();

-- The text of one stored version. status says honestly what happened: EXTRACTED (read from the file), OCR_DONE (read by the
-- local engine), PENDING (waiting for the engine), OCR_UNAVAILABLE (no engine on this server; nothing was read),
-- NO_TEXT (the file has no text), UNSUPPORTED (a format whose text is not read) or FAILED. The text is data: it is never
-- interpreted, only stored, searched and shown.
create table document_text (
    version_id   uuid primary key references document_version (id) on delete cascade,
    document_id  uuid         not null references case_document (id) on delete cascade,
    status       varchar(16)  not null constraint document_text_status_known check (status in (
        'PENDING', 'EXTRACTED', 'OCR_DONE', 'OCR_UNAVAILABLE', 'NO_TEXT', 'UNSUPPORTED', 'FAILED')),
    method       varchar(10) constraint document_text_method_known check (method in ('PDF_TEXT', 'DOCX', 'OCR')),
    engine       varchar(100),
    languages    varchar(40),
    detail       varchar(500),
    page_count   integer,
    truncated    boolean      not null default false,
    text         text,
    search_text  text generated always as (library_normalize(text)) stored,
    attempts     integer      not null default 0,
    processed_at timestamptz,
    created_at   timestamptz  not null default now()
);
create index document_text_document on document_text (document_id);
create index document_text_pending on document_text (created_at) where status = 'PENDING';
