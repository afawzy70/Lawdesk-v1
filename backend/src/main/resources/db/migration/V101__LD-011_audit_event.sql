-- LD-011: append-only security audit trail. No passwords, hashes, session ids or CSRF tokens are ever stored.
create table audit_event (
    id bigint generated always as identity primary key,
    occurred_at timestamptz not null default clock_timestamp(),
    actor_id uuid,
    actor_username varchar(64),
    action varchar(64) not null,
    target_type varchar(64),
    target_id varchar(256),
    outcome varchar(16) not null,
    reason varchar(64),
    request_id varchar(64),
    constraint audit_event_outcome check (outcome in ('SUCCESS', 'FAILURE', 'DENIED'))
);
create index audit_event_occurred on audit_event (occurred_at desc);
create index audit_event_actor on audit_event (actor_id);
create index audit_event_action on audit_event (action);

-- Append-only even for the table owner: the application has no update or delete path, and the database refuses one.
create function audit_event_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'audit_event is append-only';
end;
$$;
create trigger audit_event_no_update_or_delete before update or delete on audit_event
    for each row execute function audit_event_append_only();
