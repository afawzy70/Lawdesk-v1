-- LD-051 (gap closure, requirements section 17 line 290): the judgments and precedents that support a defence. A defence
-- points at library judgments (and optionally at one principle of that judgment); the link is the member's decision and a
-- removal is a mark, so the history of what supported a defence is kept. Additive only: nothing is backfilled.

create table defense_precedent (
    id                  uuid primary key default gen_random_uuid(),
    defense_id          uuid          not null references defense (id),
    library_judgment_id uuid          not null references library_judgment (id),
    principle_id        uuid references library_principle (id),
    note                varchar(2000) constraint defense_precedent_note_visible check (note is null or length(btrim(note)) > 0),
    created_at          timestamptz   not null default now(),
    created_by          uuid          not null,
    removed_at          timestamptz,
    removed_by          uuid,
    constraint defense_precedent_removal_complete check ((removed_at is null) = (removed_by is null))
);
create unique index defense_precedent_once on defense_precedent (defense_id, library_judgment_id) where removed_at is null;
create index defense_precedent_judgment on defense_precedent (library_judgment_id) where removed_at is null;

-- The principle named by a link is a principle of the linked judgment.
create function defense_precedent_principle_of_judgment() returns trigger language plpgsql as $$
begin
    if new.principle_id is not null and not exists (
        select 1 from library_principle p where p.id = new.principle_id and p.judgment_id = new.library_judgment_id) then
        raise exception 'the principle belongs to another judgment'
            using errcode = 'check_violation', constraint = 'defense_precedent_principle_of_judgment';
    end if;
    return new;
end $$;
create trigger defense_precedent_principle_of_judgment before insert or update on defense_precedent
    for each row execute function defense_precedent_principle_of_judgment();
