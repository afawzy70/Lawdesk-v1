-- LD-054 (gap closure, requirements section 9 "ميعاد الطعن"): the deadline register already holds every period that runs
-- from a judgment (deadline.source_type = 'JUDGMENT'); this column lets the judgment name the one that is its appeal
-- deadline, so the judgment shows it without searching. The service checks that the deadline runs from this judgment.
-- Nullable, nothing backfilled, and no period is stated by the system (decision D6).

alter table judgment add column appeal_deadline_id uuid references deadline (id);
create index judgment_appeal_deadline on judgment (appeal_deadline_id) where appeal_deadline_id is not null;

create function judgment_appeal_deadline_runs_from_judgment() returns trigger language plpgsql as $$
begin
    if new.appeal_deadline_id is not null and not exists (
        select 1 from deadline d where d.id = new.appeal_deadline_id and d.source_type = 'JUDGMENT'
                                      and d.source_id = new.id) then
        raise exception 'the deadline does not run from this judgment'
            using errcode = 'check_violation', constraint = 'judgment_appeal_deadline_runs_from_judgment';
    end if;
    return new;
end $$;
create trigger judgment_appeal_deadline_runs_from_judgment before insert or update of appeal_deadline_id on judgment
    for each row execute function judgment_appeal_deadline_runs_from_judgment();
