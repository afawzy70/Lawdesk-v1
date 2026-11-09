-- LD-052 (WP7, requirements section 16, docs/design/LD-DOC-03 WP7): the judgments and precedents library. Additive only.
-- Nothing is imported or invented: the library starts empty, and the only rows seeded are managed classification values
-- the requirements text itself lists (request types, defence types, judicial results).

-- ---- Arabic-aware search without extensions ---------------------------------------------------------------------
-- pg_trgm would need CREATE on the database, which the application role does not have, so the normalisation, the word
-- trigrams and their similarity are plain SQL functions (immutable, so generated columns and indexes can use them).

-- One normal form for Arabic text, applied to what is stored and to what is searched for: diacritics, tatweel and
-- Quranic marks removed; every alef form to bare alef; alef maqsura to yeh; taa marbuta to heh; hamza dropped with its waw/yeh carriers
-- (so مسؤولية, مسئولية and مسولية meet); Arabic-Indic and Persian digits to ASCII; Latin lower-cased; every other character a space; the
-- definite article, with the one-letter
-- prefixes that join it (و ب ك ف) or the merged للـ, stripped from words of at least three further letters. Character ranges are explicit because \w
-- does not match Arabic letters in a C-locale database.
create function library_normalize(p_text text) returns text
    language sql immutable parallel safe as $$
select btrim(regexp_replace(
           regexp_replace(
               regexp_replace(
                   translate(regexp_replace(lower(coalesce(p_text, '')), '[ً-ٰٟـۖ-ۭ]', '', 'g'),
                             'أإآٱىة٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹ؤئء',
                             'اااايه01234567890123456789'),
                   '[^a-z0-9ء-ي]+', ' ', 'g'),
               '(^| )(وال|بال|كال|فال|لل|ال)(?=[ء-ي]{3})', '\1', 'g'),
           ' +', ' ', 'g'))
$$;

-- The word trigrams of a text, padded the way pg_trgm pads them (two spaces before a word, one after).
create function library_trigrams(p_text text) returns text[]
    language sql immutable parallel safe as $$
select coalesce(array_agg(distinct substr('  ' || w || ' ', i, 3)), cast('{}' as text[]))
from unnest(string_to_array(library_normalize(p_text), ' ')) as w
         cross join lateral generate_series(1, length(w) + 1) as i
where w <> ''
$$;

-- Jaccard similarity of two trigram sets, 0 to 1.
create function library_similarity(p_a text[], p_b text[]) returns real
    language sql immutable parallel safe as $$
select case
           when coalesce(cardinality(p_a), 0) = 0 or coalesce(cardinality(p_b), 0) = 0 then 0::real
           else (shared.n::real / (cardinality(p_a) + cardinality(p_b) - shared.n)::real) end
from (select count(*) as n from (select unnest(p_a) intersect select unnest(p_b)) both_sides) shared
$$;

-- ---- Managed classification lists (owner decision D10) --------------------------------------------------------------
-- Values are edited, deactivated and added by the library managers, never deleted, so a judgment keeps the value it was
-- classified with. origin says whether the requirements document lists the value or a member proposed it.
create table library_classification (
    id         uuid primary key default gen_random_uuid(),
    dimension  varchar(16)  not null constraint library_classification_dimension_known
        check (dimension in ('CASE_KIND', 'LEGAL_TOPIC', 'REQUEST_TYPE', 'DEFENSE_TYPE', 'OUTCOME', 'SECTOR')),
    code       varchar(40)  not null constraint library_classification_code_format check (code ~ '^[A-Z0-9_]{1,40}$'),
    label_ar   varchar(200) not null constraint library_classification_label_visible check (length(btrim(label_ar)) > 0),
    label_en   varchar(200),
    origin     varchar(10)  not null constraint library_classification_origin_known check (origin in ('DOCUMENT', 'PROPOSED')),
    active     boolean      not null default true,
    sort_order integer      not null default 0,
    version    bigint       not null default 0,
    created_at timestamptz  not null default now(),
    created_by uuid,
    updated_at timestamptz  not null default now(),
    updated_by uuid,
    constraint library_classification_code_unique unique (dimension, code)
);
create unique index library_classification_label_unique on library_classification (dimension, library_normalize(label_ar))
    where active;

-- Values listed in the requirements text: request types (section 3, line 51), defence types (section 17, line 286) and the
-- judicial result of a request (section 4, lines 61-72).
insert into library_classification (dimension, code, label_ar, origin, sort_order) values
    ('REQUEST_TYPE', 'PRIMARY', 'أصلي', 'DOCUMENT', 1),
    ('REQUEST_TYPE', 'ALTERNATIVE', 'احتياطي', 'DOCUMENT', 2),
    ('REQUEST_TYPE', 'INCIDENTAL', 'عارض', 'DOCUMENT', 3),
    ('REQUEST_TYPE', 'ANCILLARY', 'تابع', 'DOCUMENT', 4),
    ('REQUEST_TYPE', 'URGENT', 'مستعجل', 'DOCUMENT', 5),
    ('REQUEST_TYPE', 'OTHER', 'غير ذلك', 'DOCUMENT', 6),
    ('DEFENSE_TYPE', 'FORMAL', 'شكلي', 'DOCUMENT', 1),
    ('DEFENSE_TYPE', 'PROCEDURAL', 'إجرائي', 'DOCUMENT', 2),
    ('DEFENSE_TYPE', 'SUBSTANTIVE', 'موضوعي', 'DOCUMENT', 3),
    ('DEFENSE_TYPE', 'OTHER', 'غير ذلك', 'DOCUMENT', 4),
    ('OUTCOME', 'ACCEPTED', 'قبول الطلب', 'DOCUMENT', 1),
    ('OUTCOME', 'PARTIAL', 'قبول جزئي', 'DOCUMENT', 2),
    ('OUTCOME', 'REJECTED', 'رفض الطلب', 'DOCUMENT', 3),
    ('OUTCOME', 'INADMISSIBLE', 'عدم قبول الطلب', 'DOCUMENT', 4),
    ('OUTCOME', 'NO_JURISDICTION', 'عدم الاختصاص', 'DOCUMENT', 5),
    ('OUTCOME', 'REFERRED', 'الإحالة', 'DOCUMENT', 6),
    ('OUTCOME', 'EXTINGUISHED', 'انقضاء الخصومة', 'DOCUMENT', 7),
    ('OUTCOME', 'WAIVER_RECORDED', 'إثبات التنازل', 'DOCUMENT', 8),
    ('OUTCOME', 'ABANDONMENT_RECORDED', 'إثبات الترك', 'DOCUMENT', 9),
    ('OUTCOME', 'NOT_DECIDED', 'عدم الفصل في الطلب', 'DOCUMENT', 10),
    ('OUTCOME', 'DEFERRED', 'تأجيل الفصل فيه', 'DOCUMENT', 11),
    ('OUTCOME', 'OTHER', 'نتيجة أخرى', 'DOCUMENT', 12);

-- ---- Judgments ---------------------------------------------------------------------------------------------------------
-- A judgment kept for reference, from the authority's own cases or from elsewhere. It is not case data: it belongs to the
-- department's library and carries no case id (a link to a case is case_precedent_link). Everything but the court, the
-- subject and where the text came from may be unknown, and "not specified" is stored as null.
create table library_judgment (
    id                 uuid primary key default gen_random_uuid(),
    court_name         varchar(200)  not null constraint library_judgment_court_visible check (length(btrim(court_name)) > 0),
    court_level        varchar(16)   constraint library_judgment_court_level_known
        check (court_level in ('FIRST_INSTANCE', 'APPEAL', 'CASSATION', 'CONSTITUTIONAL', 'OTHER')),
    chamber            varchar(120),
    case_number        varchar(60),
    case_year          integer       constraint library_judgment_year_range check (case_year between 1900 and 2100),
    judgment_date      date,
    case_type          varchar(32)   constraint library_judgment_case_type_known
        check (case_type in ('CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE', 'CONSTITUTIONAL', 'OTHER')),
    subject            varchar(1000) not null constraint library_judgment_subject_visible check (length(btrim(subject)) > 0),
    -- Names or capacities of the parties as the confidentiality rules allow; hidden from readers when RESTRICTED.
    parties_description varchar(1000),
    summary            text,
    operative_text     text,
    reasons            text,
    legal_texts        text,
    requests_addressed text,
    request_results    text,
    direction          varchar(20)   constraint library_judgment_direction_known
        check (direction in ('FOR_AUTHORITY', 'AGAINST_AUTHORITY', 'OTHER')),
    keywords           text,
    finality           varchar(14)   not null default 'NOT_SPECIFIED' constraint library_judgment_finality_known
        check (finality in ('FINAL', 'NOT_FINAL', 'NOT_SPECIFIED')),
    confidentiality    varchar(12)   not null default 'NORMAL' constraint library_judgment_confidentiality_known
        check (confidentiality in ('NORMAL', 'RESTRICTED')),
    source_reference   varchar(1000) not null constraint library_judgment_source_visible check (length(btrim(source_reference)) > 0),
    verification_note  varchar(1000) not null constraint library_judgment_verification_visible check (length(btrim(verification_note)) > 0),
    -- Where the saved electronic copy is kept (the file store arrives with the documents package).
    copy_reference     varchar(500),
    status             varchar(10)   not null default 'ACTIVE' constraint library_judgment_status_known
        check (status in ('ACTIVE', 'WITHDRAWN')),
    withdrawn_reason   varchar(500),
    withdrawn_at       timestamptz,
    withdrawn_by       uuid,
    version            bigint        not null default 0,
    created_at         timestamptz   not null default now(),
    created_by         uuid          not null,
    updated_at         timestamptz   not null default now(),
    updated_by         uuid          not null,
    -- Full-text vector: subject and keywords weigh most, then the summary and legal texts, the operative part, the reasons.
    search_vector      tsvector generated always as (
        setweight(to_tsvector('simple', library_normalize(subject)), 'A')
        || setweight(to_tsvector('simple', library_normalize(keywords)), 'A')
        || setweight(to_tsvector('simple', library_normalize(summary)), 'B')
        || setweight(to_tsvector('simple', library_normalize(legal_texts)), 'B')
        || setweight(to_tsvector('simple', library_normalize(operative_text)), 'C')
        || setweight(to_tsvector('simple', library_normalize(reasons)), 'D')) stored,
    trigrams           text[] generated always as (library_trigrams(subject || ' ' || coalesce(keywords, ''))) stored,
    constraint library_judgment_withdrawal_complete
        check ((status = 'WITHDRAWN') = (withdrawn_at is not null and withdrawn_by is not null and withdrawn_reason is not null))
);
create index library_judgment_search on library_judgment using gin (search_vector);
create index library_judgment_trigrams on library_judgment using gin (trigrams);
create index library_judgment_date on library_judgment (judgment_date desc nulls last, id);
create index library_judgment_court on library_judgment (library_normalize(court_name));
create index library_judgment_citation on library_judgment (library_normalize(court_name), case_number, case_year);

create table library_judgment_class (
    judgment_id       uuid not null references library_judgment (id),
    classification_id uuid not null references library_classification (id),
    primary key (judgment_id, classification_id)
);
create index library_judgment_class_by_value on library_judgment_class (classification_id);

-- ---- Principles extracted from a judgment -------------------------------------------------------------------------------
-- Kept apart from the judgment on purpose: a principle is a member's summary and never replaces the judgment's text.
create table library_principle (
    id            uuid primary key default gen_random_uuid(),
    judgment_id   uuid         not null references library_judgment (id),
    principle_text text        not null constraint library_principle_text_visible check (length(btrim(principle_text)) > 0),
    legal_topic   varchar(300),
    legal_basis   text,
    facts_applied text,
    scope_note    text,
    member_notes  text,
    review_status varchar(10)  not null default 'DRAFT' constraint library_principle_review_known
        check (review_status in ('DRAFT', 'REVIEWED')),
    reviewed_by   uuid,
    reviewed_at   timestamptz,
    version       bigint       not null default 0,
    created_at    timestamptz  not null default now(),
    created_by    uuid         not null,
    updated_at    timestamptz  not null default now(),
    updated_by    uuid         not null,
    search_vector tsvector generated always as (
        setweight(to_tsvector('simple', library_normalize(principle_text)), 'A')
        || setweight(to_tsvector('simple', library_normalize(legal_topic)), 'A')
        || setweight(to_tsvector('simple', library_normalize(legal_basis)), 'B')
        || setweight(to_tsvector('simple', library_normalize(facts_applied)), 'C')
        || setweight(to_tsvector('simple', library_normalize(scope_note)), 'C')) stored,
    constraint library_principle_review_complete check ((review_status = 'REVIEWED') = (reviewed_at is not null and reviewed_by is not null))
);
create index library_principle_judgment on library_principle (judgment_id);
create index library_principle_search on library_principle using gin (search_vector);

-- ---- What happened to a judgment afterwards -------------------------------------------------------------------------------
-- A recorded fact with its source, never a legal conclusion: the library shows these and warns, the legal member decides
-- what they mean. The affected judgment is judgment_id; related_judgment_id (a library judgment) and/or related_citation
-- (a decision outside the library) is the other side. Removal is a mark, not a delete.
create table library_judgment_relation (
    id                  uuid primary key default gen_random_uuid(),
    judgment_id         uuid          not null references library_judgment (id),
    relation_type       varchar(14)   not null constraint library_relation_type_known
        check (relation_type in ('APPEALED', 'UPHELD', 'MODIFIED', 'OVERTURNED', 'LATER', 'DEPARTED_FROM')),
    related_judgment_id uuid references library_judgment (id),
    related_citation    varchar(500),
    relation_date       date,
    source_reference    varchar(1000) not null constraint library_relation_source_visible check (length(btrim(source_reference)) > 0),
    notes               varchar(2000),
    created_at          timestamptz   not null default now(),
    created_by          uuid          not null,
    removed_at          timestamptz,
    removed_by          uuid,
    constraint library_relation_other_side check (related_judgment_id is not null or related_citation is not null),
    constraint library_relation_not_self check (related_judgment_id is null or related_judgment_id <> judgment_id),
    constraint library_relation_removal_complete check ((removed_at is null) = (removed_by is null))
);
create index library_relation_judgment on library_judgment_relation (judgment_id) where removed_at is null;
create index library_relation_related on library_judgment_relation (related_judgment_id) where removed_at is null;

-- ---- A library judgment used in a case -------------------------------------------------------------------------------------
-- Many to many, with the reason it helps and how alike the facts and issues are. It is the user's decision (origin says
-- whether the judgment was found by search or taken from the suggestions); removal is a mark.
create table case_precedent_link (
    id                  uuid primary key default gen_random_uuid(),
    case_id             uuid          not null references legal_case (id),
    library_judgment_id uuid          not null references library_judgment (id),
    reason              varchar(2000) not null constraint case_precedent_reason_visible check (length(btrim(reason)) > 0),
    similarity_note     varchar(2000),
    origin              varchar(12)   not null default 'MANUAL' constraint case_precedent_origin_known
        check (origin in ('MANUAL', 'SUGGESTION')),
    created_at          timestamptz   not null default now(),
    created_by          uuid          not null,
    removed_at          timestamptz,
    removed_by          uuid,
    constraint case_precedent_removal_complete check ((removed_at is null) = (removed_by is null))
);
create unique index case_precedent_link_once on case_precedent_link (case_id, library_judgment_id) where removed_at is null;
create index case_precedent_link_judgment on case_precedent_link (library_judgment_id) where removed_at is null;

-- ---- Permissions (LD-DOC-04) -------------------------------------------------------------------------------------------------
-- Everyone who works with cases may read the library; those who handle cases and supervise them may add to it.
insert into role_permission (role_id, permission)
select r.id, p.permission
from app_role r
join (values
    ('ADMIN', 'LIBRARY_VIEW'), ('ADMIN', 'LIBRARY_MANAGE'),
    ('LEGAL_MANAGER', 'LIBRARY_VIEW'), ('LEGAL_MANAGER', 'LIBRARY_MANAGE'),
    ('SECTION_HEAD', 'LIBRARY_VIEW'), ('SECTION_HEAD', 'LIBRARY_MANAGE'),
    ('LEGAL_STAFF', 'LIBRARY_VIEW'), ('LEGAL_STAFF', 'LIBRARY_MANAGE'),
    ('DATA_ENTRY', 'LIBRARY_VIEW'),
    ('ARCHIVIST', 'LIBRARY_VIEW'),
    ('READ_ONLY', 'LIBRARY_VIEW')
) as p (role, permission) on p.role = r.code
on conflict do nothing;
