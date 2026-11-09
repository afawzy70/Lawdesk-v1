-- LD-056 (WP6; requirements section 14): the task grows from four statuses to the seven of the source text
-- (new, in progress, waiting for documents, waiting for another body, completed, late, cancelled), gets a start date,
-- instructions and a delay reason, and keeps a history of who changed what. Additive: no column is dropped and no row
-- is lost; only the two legacy status values are renamed through the documented mapping below.

-- The documented, reversible mapping. `legacy_status` is what the previous release reads and writes. `exact` marks the
-- statuses that existed before; the new ones have no legacy twin and fall back to the legacy value shown.
-- Rollback (before redeploying the previous SHA): update task t set status = m.legacy_status
--   from task_status_map m where m.status = t.status (for LATE use task.status_before_late), then restore the old
--   check constraint task_status_known (OPEN, IN_PROGRESS, DONE, CANCELLED).
create table task_status_map (
    status        varchar(20) primary key,
    legacy_status varchar(12) not null,
    exact         boolean     not null,
    note          varchar(200) not null
);
insert into task_status_map (status, legacy_status, exact, note) values
    ('NEW', 'OPEN', true, 'renamed'),
    ('IN_PROGRESS', 'IN_PROGRESS', true, 'unchanged'),
    ('WAITING_DOCUMENTS', 'IN_PROGRESS', false, 'new: still open work, waiting for documents'),
    ('WAITING_OTHER', 'IN_PROGRESS', false, 'new: still open work, waiting for another body'),
    ('COMPLETED', 'DONE', true, 'renamed'),
    ('LATE', 'OPEN', false, 'new: set by the follow-up run; roll back to status_before_late'),
    ('CANCELLED', 'CANCELLED', true, 'unchanged');

alter table task drop constraint task_status_known;
alter table task drop constraint task_completed_when_done;
drop index task_assignee_open;
alter table task alter column status type varchar(20);
update task set status = 'NEW' where status = 'OPEN';
update task set status = 'COMPLETED' where status = 'DONE';
alter table task alter column status set default 'NEW';

alter table task
    add column starts_on         date,
    add column instructions      varchar(4000),
    add column delay_reason      varchar(2000),
    add column delay_reason_by   uuid,
    add column delay_reason_at   timestamptz,
    -- The open status a late task had when the follow-up run marked it late; restored when its due date is moved.
    add column status_before_late varchar(20),
    add constraint task_status_known check (status in ('NEW', 'IN_PROGRESS', 'WAITING_DOCUMENTS', 'WAITING_OTHER',
        'COMPLETED', 'LATE', 'CANCELLED')),
    add constraint task_completed_when_done check ((status = 'COMPLETED') = (completed_at is not null)),
    add constraint task_instructions_visible check (instructions is null or length(btrim(instructions)) > 0),
    add constraint task_delay_reason_visible check (delay_reason is null or length(btrim(delay_reason)) > 0),
    add constraint task_starts_before_due check (starts_on is null or due_date is null or starts_on <= due_date),
    add constraint task_late_remembers_status check ((status = 'LATE') = (status_before_late is not null));

create index task_assignee_open on task (assignee_id, due_date)
    where status not in ('COMPLETED', 'CANCELLED');

-- Compatibility for writers that still use the legacy words (an older client, a script, a parallel branch's fixtures):
-- they are stored under the new names, so the table never holds a legacy value.
create function task_normalise_legacy_status() returns trigger language plpgsql as $$
begin
    if new.status = 'OPEN' then
        new.status := 'NEW';
    elsif new.status = 'DONE' then
        new.status := 'COMPLETED';
    end if;
    return new;
end $$;
create trigger task_normalise_legacy_status
    before insert or update of status on task
    for each row execute function task_normalise_legacy_status();
-- The trigger runs before the check constraints; the column check only sees the new names.

-- Who changed what on a task (requirements 14: follow-up, reassignment, reasons for delay).
create table task_history (
    id         uuid primary key default gen_random_uuid(),
    task_id    uuid         not null references task (id) on delete cascade,
    changed_at timestamptz  not null default now(),
    -- null for the follow-up run (SYSTEM); a user otherwise (an actor column: no foreign key).
    actor_id   uuid,
    action     varchar(16)  not null constraint task_history_action_known
        check (action in ('CREATED', 'UPDATED', 'STATUS', 'REASSIGNED', 'DELAY_REASON', 'SYSTEM_LATE', 'SYSTEM_RESTORED')),
    -- The field that changed (title, description, instructions, starts_on, due_date, priority, assignee, status,
    -- delay_reason) and its previous and new value, truncated to 500 characters.
    field      varchar(30),
    old_value  varchar(500),
    new_value  varchar(500),
    reason     varchar(500)
);
create index task_history_task on task_history (task_id, changed_at, id);

-- case_visible (V600) named the open task statuses; "open" is now every status but the two final ones.
create or replace function case_visible(p_case uuid, p_me uuid, p_all boolean, p_units text, p_types text) returns boolean
    language plpgsql stable as $$
declare
    c record;
begin
    select primary_member_id, alternate_member_id, created_by, confidentiality, case_type, owning_unit_id
      into c from legal_case where id = p_case;
    if not found then
        return false;
    end if;
    if c.primary_member_id = p_me or c.alternate_member_id = p_me or c.created_by = p_me then
        return true;
    end if;
    if case_in_scope(c.confidentiality, c.case_type, c.owning_unit_id, p_all, p_units, p_types) then
        return true;
    end if;
    return exists (select 1 from case_grant g where g.case_id = p_case and g.user_id = p_me and g.revoked_at is null
                   and (g.expires_at is null or g.expires_at > now()))
        or exists (select 1 from task t where t.case_id = p_case and t.assignee_id = p_me
                   and t.status not in ('COMPLETED', 'CANCELLED'));
end
$$;
