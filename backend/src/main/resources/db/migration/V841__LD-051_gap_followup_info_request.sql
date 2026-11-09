-- LD-051 (gap closure, requirements sections 18 and 20): a missing evidence item can be followed up by an information
-- request to an administration as well as by a task. info_request_id was reserved by V623 without a foreign key because the
-- table did not exist yet; it exists since V661. No existing row has a value, so the constraint validates at once.

alter table gap_followup
    add constraint gap_followup_info_request_fk foreign key (info_request_id) references info_request (id),
    add constraint gap_followup_one_target check (task_id is null or info_request_id is null);
create unique index gap_followup_info_request_once on gap_followup (evidence_id, info_request_id)
    where info_request_id is not null;
create index gap_followup_info_request on gap_followup (info_request_id) where info_request_id is not null;

-- The request and the evidence belong to the same case.
create function gap_followup_same_case() returns trigger language plpgsql as $$
begin
    if new.info_request_id is not null and not exists (
        select 1 from info_request r join evidence_item e on e.case_id = r.case_id
        where r.id = new.info_request_id and e.id = new.evidence_id) then
        raise exception 'the evidence and the information request belong to different cases'
            using errcode = 'check_violation', constraint = 'gap_followup_same_case';
    end if;
    return new;
end $$;
create trigger gap_followup_same_case before insert or update on gap_followup
    for each row execute function gap_followup_same_case();
