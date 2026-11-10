-- LD-055 round 3, LD-DOC-R3 (QA-RT-001): the recognition engine reads a page more than once. The page text stays the main reading
-- (document_text.text and its generated search_text are NOT changed: every consumer of the text, the assistant and the unified search
-- included, keeps reading the main reading only). Words another reading was confident about and the main reading lacks are kept one
-- row per word in document_text_alternate with the pass, the line of that pass and the confidence, so that
--   * the archive search can say a hit relies on an alternate reading (and on which word, pass, line, confidence),
--   * no phrase can be composed from alternates (they are separate rows, never one text),
--   * a reprocess replaces them (the rows go with the text row, or are deleted before the new ones are written).
-- words_read / words_uncertain count the main reading's words and the words the engine was unsure of. Rows that existed before this
-- migration keep NULL there: "not measured", not zero. claimed_at lets the worker claim a version in one short transaction and read
-- it with no transaction open (the engine can run for minutes; a connection must not wait for it).
-- Additive; nothing is rewritten; no function is created (nothing here depends on a search path).
alter table document_text
    add column words_read      integer,
    add column words_uncertain integer,
    add column claimed_at      timestamptz;

create table document_text_alternate (
    id          bigint generated always as identity primary key,
    version_id  uuid         not null references document_text (version_id) on delete cascade,
    document_id uuid         not null references case_document (id) on delete cascade,
    page        integer      not null,
    line_no     integer      not null,
    word        varchar(200) not null,
    norm        text generated always as (library_normalize(word)) stored,
    pass        varchar(30)  not null,
    confidence  numeric(5, 2) not null
);
create index document_text_alternate_version on document_text_alternate (version_id);
create index document_text_alternate_document on document_text_alternate (document_id);
