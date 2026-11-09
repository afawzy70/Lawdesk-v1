-- LD-058 (round 2, performance), part 2: stored search vectors for the other record types of the unified search. Same idea as V801:
-- the text is normalised and weighted once, when the record is written (a BEFORE row trigger fills search_vec), and a GIN index
-- finds the candidates; access to the case (case_visible) is still tested at query time. A record whose document includes another
-- table's text (a library judgment's principles, a settlement's offering party, a directive's addressee) is touched by a trigger on
-- that table, which recomputes it. Additive only.

alter table claim add column search_vec tsvector;
create function search_vec_claim() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.text, 'A')
        || search_part(new.notes, 'C');
    return new;
end $$;
create trigger search_vec_claim before insert or update on claim for each row execute function search_vec_claim();
create index claim_search_vec on claim using gin (search_vec);

alter table defense add column search_vec tsvector;
create function search_vec_defense() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.title, 'A')
        || search_part(new.argument, 'A')
        || search_part(new.legal_basis, 'B')
        || search_part(new.facts, 'B')
        || search_part(new.legal_texts, 'B')
        || search_part(new.notes, 'C');
    return new;
end $$;
create trigger search_vec_defense before insert or update on defense for each row execute function search_vec_defense();
create index defense_search_vec on defense using gin (search_vec);

alter table hearing add column search_vec tsvector;
create function search_vec_hearing() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.proceedings_summary, 'A')
        || search_part(new.decision, 'A')
        || search_part(new.notes, 'B')
        || search_part(new.opponents_requests, 'B')
        || search_part(new.required_action, 'B')
        || search_part(new.adjournment_reason, 'C')
        || search_part(new.attendees, 'C')
        || search_part(new.judge_or_expert, 'C')
        || search_part(new.chamber, 'C');
    return new;
end $$;
create trigger search_vec_hearing before insert or update on hearing for each row execute function search_vec_hearing();
create index hearing_search_vec on hearing using gin (search_vec);

alter table judgment add column search_vec tsvector;
create function search_vec_judgment() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.operative_text, 'A')
        || search_part(new.summary, 'A')
        || search_part(new.reasons, 'C');
    return new;
end $$;
create trigger search_vec_judgment before insert or update on judgment for each row execute function search_vec_judgment();
create index judgment_search_vec on judgment using gin (search_vec);

alter table case_document add column search_vec tsvector;
create function search_vec_case_document() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.title, 'A')
        || search_part(new.file_name, 'B');
    return new;
end $$;
create trigger search_vec_case_document before insert or update on case_document for each row execute function search_vec_case_document();
create index case_document_search_vec on case_document using gin (search_vec);

alter table case_note add column search_vec tsvector;
create function search_vec_case_note() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.body, 'A');
    return new;
end $$;
create trigger search_vec_case_note before insert or update on case_note for each row execute function search_vec_case_note();
create index case_note_search_vec on case_note using gin (search_vec);

alter table task add column search_vec tsvector;
create function search_vec_task() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.title, 'A')
        || search_part(new.description, 'B');
    return new;
end $$;
create trigger search_vec_task before insert or update on task for each row execute function search_vec_task();
create index task_search_vec on task using gin (search_vec);

alter table library_judgment add column search_vec tsvector;
create function search_vec_library_judgment() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.subject, 'A')
        || search_part(new.keywords, 'A')
        || search_part(new.summary, 'B')
        || search_part(new.legal_texts, 'B')
        || search_part((select string_agg(concat_ws(' ', lp.principle_text, lp.legal_topic, lp.legal_basis), ' ') from library_principle lp where lp.judgment_id = new.id), 'B')
        || search_part(new.operative_text, 'C')
        || search_part(new.reasons, 'D');
    return new;
end $$;
create trigger search_vec_library_judgment before insert or update on library_judgment for each row execute function search_vec_library_judgment();
create index library_judgment_search_vec on library_judgment using gin (search_vec);

alter table settlement add column search_vec tsvector;
create function search_vec_settlement() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.legal_grounds, 'A')
        || search_part(new.legal_opinion, 'A')
        || search_part(concat_ws(' ', new.offering_party_text, (select y.name from party y where y.id = new.offering_party_id)), 'B')
        || search_part(new.financial_impact_note, 'B')
        || search_part(new.approving_authority, 'C')
        || search_part(new.decision_reason, 'C');
    return new;
end $$;
create trigger search_vec_settlement before insert or update on settlement for each row execute function search_vec_settlement();
create index settlement_search_vec on settlement using gin (search_vec);

alter table directive add column search_vec tsvector;
create function search_vec_directive() returns trigger language plpgsql as $$
begin
    new.search_vec := search_part(new.title, 'A')
        || search_part(concat_ws(' ', new.directive_no, new.source_reference_no), 'A')
        || search_part(new.details, 'B')
        || search_part(new.source, 'B')
        || search_part(concat_ws(' ', new.addressee_text, (select z.display_name from app_user z where z.id = new.addressee_user_id)), 'C');
    return new;
end $$;
create trigger search_vec_directive before insert or update on directive for each row execute function search_vec_directive();
create index directive_search_vec on directive using gin (search_vec);

-- Touches: the rows whose document includes text of a row that changed are rewritten to themselves, which recomputes search_vec.
create function search_touch_library() returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        update library_judgment set id = id where id in (select judgment_id from nt);
    elsif tg_op = 'DELETE' then
        update library_judgment set id = id where id in (select judgment_id from ot);
    else
        update library_judgment set id = id where id in (select judgment_id from nt union select judgment_id from ot);
    end if;
    return null;
end $$;
create trigger search_principle_insert after insert on library_principle referencing new table as nt
    for each statement execute function search_touch_library();
create trigger search_principle_update after update on library_principle referencing old table as ot new table as nt
    for each statement execute function search_touch_library();
create trigger search_principle_delete after delete on library_principle referencing old table as ot
    for each statement execute function search_touch_library();

create function search_touch_settlement() returns trigger language plpgsql as $$
begin
    update settlement set offering_party_id = offering_party_id
    where offering_party_id in (select n.id from nt n join ot o on o.id = n.id where n.name is distinct from o.name);
    return null;
end $$;
create trigger search_settlement_party_update after update on party referencing old table as ot new table as nt
    for each statement execute function search_touch_settlement();

create function search_touch_directive() returns trigger language plpgsql as $$
begin
    update directive set addressee_user_id = addressee_user_id
    where addressee_user_id in (select n.id from nt n join ot o on o.id = n.id where n.display_name is distinct from o.display_name);
    return null;
end $$;
create trigger search_directive_member_update after update on app_user referencing old table as ot new table as nt
    for each statement execute function search_touch_directive();

-- Backfill (re-runnable: rewriting a row to itself recomputes its vector).
update claim set id = id;
update defense set id = id;
update hearing set id = id;
update judgment set id = id;
update case_document set id = id;
update case_note set id = id;
update task set id = id;
update library_judgment set id = id;
update settlement set id = id;
update directive set id = id;
