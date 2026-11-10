-- LD-058 round 3 (write cost). The stored search document of a case was rebuilt on EVERY update of the case, the proceeding or
-- the primary involvement, including updates that touch nothing the document is made of (status, version, dates, amounts).
-- On the 50 000-case corpus a title-less update cost 27 ms with the search columns and 3 ms without them (docs/evidence/DOC-R3-PERF).
-- From now on an UPDATE refreshes a document only for the rows whose searchable columns changed; inserts and deletes are
-- unchanged. The columns listed are exactly the ones search_case_refresh (V801) reads from these tables, so the stored document
-- stays equal to a full recomputation (StoredSearchDocumentsTest).

create or replace function search_trg_case() returns trigger language plpgsql as $$
declare
    changed uuid[];
    renamed uuid[];
begin
    if tg_op = 'INSERT' then
        perform search_case_refresh(array(select id from nt));
        return null;
    end if;
    changed := array(select n.id from nt n join ot o on o.id = n.id
        where n.reference_no is distinct from o.reference_no or n.automated_no is distinct from o.automated_no
           or n.title is distinct from o.title or n.subject is distinct from o.subject
           or n.facts is distinct from o.facts or n.chamber is distinct from o.chamber
           or n.primary_member_id is distinct from o.primary_member_id
           or n.alternate_member_id is distinct from o.alternate_member_id);
    -- Calling the refresh functions with nothing to do still costs tens of milliseconds, so they are not called then.
    if cardinality(changed) > 0 then
        perform search_case_refresh(changed);
    end if;
    -- A renamed case changes the text of the links that point at it.
    renamed := array(select n.id from nt n join ot o on o.id = n.id
        where n.reference_no is distinct from o.reference_no or n.title is distinct from o.title);
    if cardinality(renamed) > 0 then
        perform search_link_refresh(array(select l.id from case_link l
            where l.case_id = any (renamed) or l.other_case_id = any (renamed)));
    end if;
    return null;
end $$;

-- proceeding: the document holds the court case number and year.
create function search_trg_proceeding_update() returns trigger language plpgsql as $$
declare
    changed uuid[];
begin
    changed := array(
        select n.case_id from nt n join ot o on o.id = n.id
        where n.case_id is distinct from o.case_id or n.court_case_number is distinct from o.court_case_number
           or n.court_case_year is distinct from o.court_case_year
        union
        select o.case_id from nt n join ot o on o.id = n.id
        where n.case_id is distinct from o.case_id);
    if cardinality(changed) > 0 then
        perform search_case_refresh(changed);
    end if;
    return null;
end $$;
drop trigger search_proceeding_update on proceeding;
create trigger search_proceeding_update after update on proceeding referencing old table as ot new table as nt
    for each statement execute function search_trg_proceeding_update();

-- case_org_involvement: the document holds the unit names, the unit and the contact name of the live involvements.
create function search_trg_involvement_update() returns trigger language plpgsql as $$
declare
    changed uuid[];
begin
    changed := array(
        select n.case_id from nt n join ot o on o.id = n.id
        where n.case_id is distinct from o.case_id or n.sector_name_snapshot is distinct from o.sector_name_snapshot
           or n.department_name_snapshot is distinct from o.department_name_snapshot
           or n.section_name_snapshot is distinct from o.section_name_snapshot
           or n.contact_name is distinct from o.contact_name or n.org_unit_id is distinct from o.org_unit_id
           or n.removed_at is distinct from o.removed_at
        union
        select o.case_id from nt n join ot o on o.id = n.id
        where n.case_id is distinct from o.case_id);
    if cardinality(changed) > 0 then
        perform search_case_refresh(changed);
    end if;
    return null;
end $$;
drop trigger search_involvement_update on case_org_involvement;
create trigger search_involvement_update after update on case_org_involvement referencing old table as ot new table as nt
    for each statement execute function search_trg_involvement_update();
