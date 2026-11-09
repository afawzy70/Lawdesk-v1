-- LD-056 (WP6; requirements sections 15 and 32): manual calendar dates and per-user notification preferences.
-- The calendar itself is derived from hearings, tasks, expert sessions and information requests; only dates that no
-- other record holds are stored here. Instants are UTC; the screens show them in Asia/Kuwait. Additive only.

create table calendar_date (
    id         uuid primary key default gen_random_uuid(),
    case_id    uuid references legal_case (id),
    kind       varchar(20)   not null constraint calendar_date_kind_known check (kind in
        ('MEETING', 'INVESTIGATION', 'MEMO_DUE', 'APPEAL_DEADLINE', 'ENFORCEMENT', 'OTHER')),
    title      varchar(200)  not null constraint calendar_date_title_visible check (length(btrim(title)) > 0),
    notes      varchar(2000) constraint calendar_date_notes_visible check (length(btrim(notes)) > 0),
    -- UTC. An all-day date starts at midnight of its Kuwait day.
    starts_at  timestamptz   not null,
    ends_at    timestamptz,
    all_day    boolean       not null default true,
    owner_id   uuid          not null,
    status     varchar(10)   not null default 'ACTIVE' constraint calendar_date_status_known
        check (status in ('ACTIVE', 'DONE', 'CANCELLED')),
    version    bigint        not null default 0,
    created_at timestamptz   not null default now(),
    created_by uuid          not null,
    updated_at timestamptz   not null default now(),
    updated_by uuid          not null,
    constraint calendar_date_end_after_start check (ends_at is null or (not all_day and ends_at > starts_at))
);
create index calendar_date_window on calendar_date (starts_at);
create index calendar_date_case on calendar_date (case_id);
create index calendar_date_owner on calendar_date (owner_id, starts_at);

-- A user may switch a kind of in-app notification off. No row means on. In-app only (decision D11).
create table notification_preference (
    user_id    uuid         not null references app_user (id),
    type       varchar(40)  not null constraint notification_preference_type_shape check (type ~ '^[A-Z][A-Z0-9_]*$'),
    enabled    boolean      not null,
    updated_at timestamptz  not null default now(),
    primary key (user_id, type)
);

create index notification_user_type on notification (user_id, type, created_at desc);

-- Permissions: whoever sees cases sees the calendar; whoever manages tasks or hearings enters manual dates.
insert into role_permission (role_id, permission)
select rp.role_id, 'CALENDAR_VIEW' from role_permission rp where rp.permission = 'CASE_VIEW'
on conflict do nothing;
insert into role_permission (role_id, permission)
select rp.role_id, 'CALENDAR_MANAGE' from role_permission rp where rp.permission in ('TASK_MANAGE', 'HEARING_MANAGE')
on conflict do nothing;
