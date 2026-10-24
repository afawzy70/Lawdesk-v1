-- LD-027 tasks and assignments, LD-028 deadline reminders (docs/delivery/PHASE-5-ACCEPTANCE.md). A task may belong to a
-- case; its assignee is a user id without a foreign key, like the actor columns (architecture/CONTRACTS.md §2, §8.3).
create table task (
    id           uuid primary key default gen_random_uuid(),
    case_id      uuid references legal_case (id),
    title        varchar(200)  not null constraint task_title_visible check (length(btrim(title)) > 0),
    description  varchar(4000) constraint task_description_visible check (length(btrim(description)) > 0),
    assignee_id  uuid          not null,
    due_date     date,
    priority     varchar(10)   not null default 'NORMAL' constraint task_priority_known
        check (priority in ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    status       varchar(12)   not null default 'OPEN' constraint task_status_known
        check (status in ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    completed_at timestamptz,
    version      bigint        not null default 0,
    created_at   timestamptz   not null default now(),
    created_by   uuid          not null,
    updated_at   timestamptz   not null default now(),
    updated_by   uuid          not null,
    -- A task carries its completion time exactly while it is done.
    constraint task_completed_when_done check ((status = 'DONE') = (completed_at is not null))
);

create index task_assignee_open on task (assignee_id, due_date) where status in ('OPEN', 'IN_PROGRESS');
create index task_due on task (due_date);
create index task_case on task (case_id);
