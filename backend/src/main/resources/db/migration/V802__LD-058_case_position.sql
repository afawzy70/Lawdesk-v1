-- LD-058 (round 2, performance), part 3: the current position of each case (its current proceeding: the highest stage of the
-- judicial ladder, then the latest filing date), kept up to date by triggers on proceeding instead of being worked out for every
-- case of a report with a look-up per case. The inventory's stage distribution and the dashboard read it with one hash join.
-- The rule is the one reports have always used (ProceedingReportQueries.CURRENT); a test compares the two on the same data.
-- Additive only; the table can be ignored by an older release.
create table case_position (
    case_id          uuid        primary key references legal_case (id) on delete cascade,
    stage            varchar(32) not null,
    court_id         uuid        not null,
    proceeding_count integer     not null
);
create index case_position_stage on case_position (stage);

create function case_position_refresh(p_ids uuid[]) returns void language sql as $$
delete from case_position cp where cp.case_id = any (p_ids)
  and not exists (select 1 from proceeding p where p.case_id = cp.case_id);
insert into case_position (case_id, stage, court_id, proceeding_count)
select x.case_id, x.stage, x.court_id, x.n
from (select distinct on (p.case_id) p.case_id, p.stage, p.court_id, count(*) over (partition by p.case_id) as n
      from proceeding p
      where p.case_id = any (p_ids)
      order by p.case_id,
               case p.stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 when 'CASSATION' then 2 when 'CONSTITUTIONAL' then 3
                            when 'ENFORCEMENT' then 4 when 'GRIEVANCE' then 5 end desc,
               p.filing_date desc, p.created_at desc, p.id desc) x
on conflict (case_id) do update
    set stage = excluded.stage, court_id = excluded.court_id, proceeding_count = excluded.proceeding_count
$$;

create function case_position_trg() returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        perform case_position_refresh(array(select distinct case_id from nt));
    elsif tg_op = 'DELETE' then
        perform case_position_refresh(array(select distinct case_id from ot));
    else
        perform case_position_refresh(array(select case_id from nt union select case_id from ot));
    end if;
    return null;
end $$;
create trigger case_position_insert after insert on proceeding referencing new table as nt
    for each statement execute function case_position_trg();
create trigger case_position_update after update on proceeding referencing old table as ot new table as nt
    for each statement execute function case_position_trg();
create trigger case_position_delete after delete on proceeding referencing old table as ot
    for each statement execute function case_position_trg();

select case_position_refresh(array(select id from legal_case));
