-- LD-DOC-R3 (QA-RT-001): the recognition engine reads a page more than once; the words the main reading lacks and another reading
-- is confident about are kept apart from the page text (alt_text: searched, never shown as the page), and the counts of words read and of
-- words the engine was unsure of are kept, so that the API and the screen can say "recognition uncertain" instead of failing silently.
-- The normalised search column is rebuilt over both texts; the search vector (V844) reads that column, so nothing else changes.
-- Additive for existing rows: alt_text and the counts are null, and search_text keeps its old value.
alter table document_text drop column search_text;
alter table document_text
    add column alt_text         text,
    add column words_read       integer,
    add column words_uncertain  integer,
    add column search_text text generated always as (library_normalize(coalesce(text, '') || ' ' || coalesce(alt_text, ''))) stored;
