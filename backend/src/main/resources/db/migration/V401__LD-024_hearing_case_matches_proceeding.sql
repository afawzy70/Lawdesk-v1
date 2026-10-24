-- LD-024 integration review: hearing.case_id repeats its proceeding's case (V400), and the database now enforces that
-- they match. A composite foreign key would need an index on Workstream C's proceeding table, so a trigger checks it
-- instead. proceeding.case_id never changes once recorded (LD-019), so checking the hearing side is enough.
create function hearing_case_matches_proceeding() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from proceeding p where p.id = new.proceeding_id and p.case_id = new.case_id) then
        raise exception 'hearing case does not match the case of its proceeding'
            using errcode = 'check_violation', constraint = 'hearing_case_matches_proceeding';
    end if;
    return new;
end $$;

create trigger hearing_case_matches_proceeding
    before insert or update of case_id, proceeding_id on hearing
    for each row execute function hearing_case_matches_proceeding();
