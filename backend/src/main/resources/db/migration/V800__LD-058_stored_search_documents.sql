-- LD-058 (round 2, performance): stored, indexed search documents. Additive only; every object here can be ignored by an older
-- release (new columns and tables nothing else reads). Nothing is a policy: the visibility rule (case_visible) is untouched and the
-- search still restricts every record with it at query time; only the text normalisation is done once, when a record is written,
-- instead of for every visible record on every request.
--
-- lawdesk_app has no right to create extensions, so there is no pg_trgm: the case-list text filter gets a hand-made trigram
-- array (a GIN-indexed text[] prefilter, then the same ILIKE as before).

-- ---- trigram helper for the case-list text filter ---------------------------------------------------------------------
-- The distinct three-character windows of a text, lower-cased. A text that contains a search string contains all its trigrams, so
-- "trigrams @> lawdesk_trigrams(query)" never drops a match; the ILIKE that follows removes the false ones.
create function lawdesk_trigrams(p_text text) returns text[] language sql immutable parallel safe as $$
select coalesce(array_agg(distinct substr(t.v, i, 3)), cast('{}' as text[]))
from (select lower(coalesce(p_text, '')) as v) t
         cross join lateral generate_series(1, greatest(length(t.v) - 2, 0)) as i
$$;

-- ---- the case document ------------------------------------------------------------------------------------------------
-- The weighted vector of the parts of a case that do not depend on who is looking (reference, numbers, title, subject, units,
-- members, facts, chamber). The part that depends on the viewer (the cases it is linked to, shown only when visible) is stored
-- apart, per link.
create table search_case_doc (
    case_id uuid     primary key references legal_case (id) on delete cascade,
    vec     tsvector not null,
    -- Trigrams of every text the case-list filter looks into (reference, title, subject, numbers, parties, members, units and
    -- contacts), each field apart: a necessary condition for a substring match in any of them.
    trigrams text[]  not null default '{}'
);
create index search_case_doc_vec on search_case_doc using gin (vec);
create index search_case_doc_trigrams on search_case_doc using gin (trigrams);

create function search_case_refresh(p_ids uuid[]) returns void language sql as $$
insert into search_case_doc (case_id, vec, trigrams)
select c.id,
       setweight(to_tsvector('simple', library_normalize(c.reference_no)), 'A')
       || setweight(to_tsvector('simple', library_normalize(concat_ws(' ', c.automated_no,
              (select string_agg(pr.court_case_number || ' ' || pr.court_case_year, ' ') from proceeding pr
               where pr.case_id = c.id)))), 'A')
       || setweight(to_tsvector('simple', library_normalize(c.title)), 'A')
       || setweight(to_tsvector('simple', library_normalize(c.subject)), 'B')
       || setweight(to_tsvector('simple', library_normalize(
              (select string_agg(concat_ws(' ', i.sector_name_snapshot, i.department_name_snapshot, i.section_name_snapshot,
                                           u.name_ar), ' ')
               from case_org_involvement i left join org_unit u on u.id = i.org_unit_id
               where i.case_id = c.id and i.removed_at is null))), 'B')
       || setweight(to_tsvector('simple', library_normalize(
              (select string_agg(m.display_name, ' ') from app_user m
               where m.id in (c.primary_member_id, c.alternate_member_id)))), 'B')
       || setweight(to_tsvector('simple', library_normalize(c.facts)), 'C')
       || setweight(to_tsvector('simple', library_normalize(c.chamber)), 'C'),
       lawdesk_trigrams(concat_ws(chr(10), c.reference_no, c.title, c.subject, c.automated_no,
              (select string_agg(py.name, chr(10)) from case_party cp join party py on py.id = cp.party_id
               where cp.case_id = c.id),
              (select string_agg(pr.court_case_number, chr(10)) from proceeding pr where pr.case_id = c.id),
              (select string_agg(m.display_name, chr(10)) from app_user m
               where m.id in (c.primary_member_id, c.alternate_member_id)),
              (select string_agg(concat_ws(chr(10), i.contact_name, i.sector_name_snapshot, i.department_name_snapshot,
                                           i.section_name_snapshot), chr(10))
               from case_org_involvement i where i.case_id = c.id and i.removed_at is null)))
from legal_case c
where c.id = any (p_ids)
on conflict (case_id) do update set vec = excluded.vec, trigrams = excluded.trigrams
$$;

-- ---- the link documents -----------------------------------------------------------------------------------------------
-- One row per link and side: the text a case gains from the case it is linked to (reference, title, reason). Which of these a
-- user may use is decided per request (the other case must be visible to them); the stored vector is only the normalised text.
create table search_link_doc (
    link_id  uuid     not null references case_link (id) on delete cascade,
    host_id  uuid     not null,
    other_id uuid     not null,
    vec      tsvector not null,
    primary key (link_id, host_id)
);
create index search_link_doc_host on search_link_doc (host_id);
create index search_link_doc_other on search_link_doc (other_id);
create index search_link_doc_vec on search_link_doc using gin (vec);

create function search_link_refresh(p_link_ids uuid[]) returns void language sql as $$
delete from search_link_doc where link_id = any (p_link_ids);
insert into search_link_doc (link_id, host_id, other_id, vec)
select l.id, s.host_id, s.other_id,
       setweight(to_tsvector('simple', library_normalize(concat_ws(' ', o.reference_no, o.title, l.reason))), 'C')
from case_link l
         cross join lateral (values (l.case_id, l.other_case_id), (l.other_case_id, l.case_id)) as s (host_id, other_id)
         join legal_case o on o.id = s.other_id
where l.id = any (p_link_ids)
$$;

-- tsvector has no aggregate; the viewer's linked text is the concatenation of the vectors of the links they may use.
create aggregate tsvector_agg (tsvector) (sfunc = tsvector_concat, stype = tsvector);

-- ---- triggers (statement level, on transition tables: a bulk load refreshes once, not once per row) ---------------------
create function search_trg_case() returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        perform search_case_refresh(array(select id from nt));
    else
        perform search_case_refresh(array(select id from nt));
        -- A renamed case changes the text of the links that point at it.
        perform search_link_refresh(array(select l.id from case_link l
            where l.case_id in (select n.id from nt n join ot o on o.id = n.id
                                where n.reference_no is distinct from o.reference_no or n.title is distinct from o.title)
               or l.other_case_id in (select n.id from nt n join ot o on o.id = n.id
                                where n.reference_no is distinct from o.reference_no or n.title is distinct from o.title)));
    end if;
    return null;
end $$;
create trigger search_case_insert after insert on legal_case referencing new table as nt
    for each statement execute function search_trg_case();
create trigger search_case_update after update on legal_case referencing old table as ot new table as nt
    for each statement execute function search_trg_case();

-- proceeding, case_party and case_org_involvement all carry case_id: any change refreshes the case they belong to.
create function search_trg_by_case_id() returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        perform search_case_refresh(array(select distinct case_id from nt));
    elsif tg_op = 'DELETE' then
        perform search_case_refresh(array(select distinct case_id from ot));
    else
        perform search_case_refresh(array(select case_id from nt union select case_id from ot));
    end if;
    return null;
end $$;
create trigger search_proceeding_insert after insert on proceeding referencing new table as nt
    for each statement execute function search_trg_by_case_id();
create trigger search_proceeding_update after update on proceeding referencing old table as ot new table as nt
    for each statement execute function search_trg_by_case_id();
create trigger search_proceeding_delete after delete on proceeding referencing old table as ot
    for each statement execute function search_trg_by_case_id();
create trigger search_involvement_insert after insert on case_org_involvement referencing new table as nt
    for each statement execute function search_trg_by_case_id();
create trigger search_involvement_update after update on case_org_involvement referencing old table as ot new table as nt
    for each statement execute function search_trg_by_case_id();
create trigger search_involvement_delete after delete on case_org_involvement referencing old table as ot
    for each statement execute function search_trg_by_case_id();

create trigger search_case_party_insert after insert on case_party referencing new table as nt
    for each statement execute function search_trg_by_case_id();
create trigger search_case_party_update after update on case_party referencing old table as ot new table as nt
    for each statement execute function search_trg_by_case_id();
create trigger search_case_party_delete after delete on case_party referencing old table as ot
    for each statement execute function search_trg_by_case_id();

-- A renamed party changes the document of its cases.
create function search_trg_party_name() returns trigger language plpgsql as $$
begin
    perform search_case_refresh(array(select cp.case_id from case_party cp
        where cp.party_id in (select n.id from nt n join ot o on o.id = n.id where n.name is distinct from o.name)));
    return null;
end $$;
create trigger search_party_name_update after update on party referencing old table as ot new table as nt
    for each statement execute function search_trg_party_name();

-- A renamed unit or a renamed member changes the document of the cases that carry it.
create function search_trg_unit() returns trigger language plpgsql as $$
begin
    perform search_case_refresh(array(select i.case_id from case_org_involvement i
        where i.org_unit_id in (select n.id from nt n join ot o on o.id = n.id where n.name_ar is distinct from o.name_ar)));
    return null;
end $$;
create trigger search_unit_update after update on org_unit referencing old table as ot new table as nt
    for each statement execute function search_trg_unit();

create function search_trg_member() returns trigger language plpgsql as $$
begin
    perform search_case_refresh(array(select c.id from legal_case c
        where c.primary_member_id in (select n.id from nt n join ot o on o.id = n.id
                                      where n.display_name is distinct from o.display_name)
           or c.alternate_member_id in (select n.id from nt n join ot o on o.id = n.id
                                        where n.display_name is distinct from o.display_name)));
    return null;
end $$;
create trigger search_member_update after update on app_user referencing old table as ot new table as nt
    for each statement execute function search_trg_member();

create function search_trg_link() returns trigger language plpgsql as $$
begin
    perform search_link_refresh(array(select id from nt));
    return null;
end $$;
create trigger search_link_insert after insert on case_link referencing new table as nt
    for each statement execute function search_trg_link();
create trigger search_link_update after update on case_link referencing old table as ot new table as nt
    for each statement execute function search_trg_link();

-- ---- parties ----------------------------------------------------------------------------------------------------------
-- Two vectors, because what a user may search depends on PARTY_ID_VIEW (D4): the public one (name, notes, contact details of a
-- party that is not confidential) and the full one (adds the identity numbers and a confidential party's contact details).
alter table party add column search_public tsvector, add column search_full tsvector;

create function search_party_vectors() returns trigger language plpgsql as $$
begin
    new.search_public := setweight(to_tsvector('simple', library_normalize(new.name)), 'A')
        || setweight(to_tsvector('simple', library_normalize(new.notes)), 'C')
        || setweight(to_tsvector('simple', library_normalize(case when not new.confidential
               then concat_ws(' ', new.phone, new.email, new.address) end)), 'D');
    new.search_full := setweight(to_tsvector('simple', library_normalize(new.name)), 'A')
        || setweight(to_tsvector('simple', library_normalize(concat_ws(' ', new.national_id, new.registration_no))), 'B')
        || setweight(to_tsvector('simple', library_normalize(new.notes)), 'C')
        || setweight(to_tsvector('simple', library_normalize(concat_ws(' ', new.phone, new.email, new.address))), 'D');
    return new;
end $$;
create trigger search_party_vectors before insert or update on party
    for each row execute function search_party_vectors();
create index party_search_public on party using gin (search_public);
create index party_search_full on party using gin (search_full);

-- ---- backfill (safe to run again: upserts and idempotent updates) ---------------------------------------------------
select search_case_refresh(array(select id from legal_case));
select search_link_refresh(array(select id from case_link));
update party set name = name;
