-- LD-055 (WP5, section 11): the enforcement file of a judgment. The judgment's number, date and operative part are read from
-- the judgment itself; the file adds who must enforce it, the amount asked, the obstacles, the status and the actions taken.
-- due_on is a target date entered by the member, never a computed legal period. Additive.

create table enforcement_file (
    id                    uuid primary key default gen_random_uuid(),
    judgment_id           uuid         not null constraint enforcement_file_one_per_judgment unique references judgment (id),
    case_id               uuid         not null references legal_case (id),
    status                varchar(20)  not null default 'NOT_STARTED' constraint enforcement_file_status_known
                          check (status in ('NOT_STARTED', 'IN_PROGRESS', 'PARTIALLY_EXECUTED', 'EXECUTED', 'OBSTRUCTED', 'NOT_REQUIRED')),
    responsible_member_id uuid,
    executing_party       varchar(300),
    amount_required       numeric(15, 3) constraint enforcement_file_amount_not_negative check (amount_required >= 0),
    obstacles             text,
    due_on                date,
    completed_on          date,
    notes                 text,
    version               bigint       not null default 0,
    created_at            timestamptz  not null default now(),
    created_by            uuid         not null,
    updated_at            timestamptz  not null default now(),
    updated_by            uuid         not null,
    constraint enforcement_file_executed_has_date check (status <> 'EXECUTED' or completed_on is not null)
);
create index enforcement_file_case on enforcement_file (case_id);
create index enforcement_file_status_due on enforcement_file (status, due_on);

create table enforcement_action (
    id             uuid primary key default gen_random_uuid(),
    enforcement_id uuid         not null references enforcement_file (id),
    seq            integer      not null,
    kind           varchar(14)  not null constraint enforcement_action_kind_known
                   check (kind in ('PROCEDURE', 'CORRESPONDENCE', 'OBSTACLE', 'OTHER')),
    action_date    date         not null,
    description    text         not null constraint enforcement_action_description_visible check (length(btrim(description)) > 0),
    result         text,
    document_id    uuid references case_document (id),
    version        bigint       not null default 0,
    created_at     timestamptz  not null default now(),
    created_by     uuid         not null,
    updated_at     timestamptz  not null default now(),
    updated_by     uuid         not null,
    constraint enforcement_action_seq_once unique (enforcement_id, seq)
);
