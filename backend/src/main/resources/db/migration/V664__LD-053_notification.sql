-- LD-053 (WP3; requirements section 32, owner decision D11): in-app notifications only, no e-mail or SMS. WP6 extends this
-- table (more types, preferences); a notification is never sent outside the system. Additive only.

create table notification (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid          not null references app_user (id),
    type       varchar(40)   not null constraint notification_type_shape check (type ~ '^[A-Z][A-Z0-9_]*$'),
    case_id    uuid references legal_case (id),
    -- The record the notification is about, as TYPE:id, for opening it from the list.
    record_ref varchar(120),
    -- The values the screen puts into the text of the type, for example the request number.
    params     jsonb         not null default '{}'::jsonb,
    -- One notification per (type, record, user): a repeated run of the follow-up never duplicates.
    dedupe_key varchar(200)  not null constraint notification_dedupe_unique unique,
    created_at timestamptz   not null default now(),
    read_at    timestamptz
);
create index notification_user_unread on notification (user_id, created_at desc) where read_at is null;
create index notification_user on notification (user_id, created_at desc);
create index notification_case on notification (case_id);
