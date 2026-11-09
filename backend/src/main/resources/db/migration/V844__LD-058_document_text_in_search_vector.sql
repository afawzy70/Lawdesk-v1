-- LD-058 round 2, regression fix: the unified search reads the text of a document's current version (read from the file or by OCR)
-- as the part DOCUMENT_TEXT, but the stored search vector of V802 held only the title and file name, so a document found only through
-- its text was never a candidate. The vector now includes that text (weight C, as the part always had), taken from the normalised
-- column document_text.search_text (no second normalisation). Same visibility rules: the case access test is applied at query time.
--
-- A text written or changed later (the OCR worker, a new version) touches the document, which recomputes its vector; a change of
-- the current version number does the same, since the trigger runs on every update of the document. Additive; re-runnable.
create or replace function search_vec_case_document() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.title, 'A')
        || search_part(new.file_name, 'B')
        || coalesce((select setweight(to_tsvector('simple', t.search_text), 'C')
                     from document_version v
                              join document_text t on t.version_id = v.id and t.status in ('EXTRACTED', 'OCR_DONE')
                     where v.document_id = new.id and v.version_no = new.current_version_no and t.search_text is not null),
                    cast('' as tsvector));
    return new;
end $$;

create function search_touch_document_text() returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' then
        update case_document set id = id where id in (select document_id from ot);
    elsif tg_op = 'INSERT' then
        update case_document set id = id where id in (select document_id from nt);
    else
        update case_document set id = id where id in (select document_id from nt union select document_id from ot);
    end if;
    return null;
end $$;
create trigger search_document_text_insert after insert on document_text referencing new table as nt
    for each statement execute function search_touch_document_text();
create trigger search_document_text_update after update on document_text referencing old table as ot new table as nt
    for each statement execute function search_touch_document_text();
create trigger search_document_text_delete after delete on document_text referencing old table as ot
    for each statement execute function search_touch_document_text();

-- A new version row can arrive before or after its text; the document is touched when a version is added too.
create function search_touch_document_version() returns trigger language plpgsql as $$
begin
    update case_document set id = id where id in (select document_id from nt);
    return null;
end $$;
create trigger search_document_version_insert after insert on document_version referencing new table as nt
    for each statement execute function search_touch_document_version();

-- Backfill (rewriting a document to itself recomputes its vector).
update case_document set id = id;
