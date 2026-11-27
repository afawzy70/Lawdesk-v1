-- LD-050 access foundation, M1-pre (QA-R6-M0-021): the case reference counter is writable by the runtime role, because ordinary intake increments it in its
-- own transaction (V200). It may only move forward one step: rewinding or jumping it would make a later registration collide with a
-- number already used. The table's owner (and a superuser) may still maintain it, for a restore or a data repair; on an installation
-- not yet split by deploy/postgres/ownership.sql the runtime role is that owner and nothing changes.
create function case_reference_counter_guard() returns trigger language plpgsql as $$
begin
    if current_user = (select tableowner from pg_tables where schemaname = tg_table_schema and tablename = tg_table_name)
       or (select rolsuper from pg_roles where rolname = current_user) then
        if tg_op = 'DELETE' then
            return old;
        end if;
        return new;
    end if;
    if tg_op = 'INSERT' and new.last_number = 1 then
        return new;
    end if;
    if tg_op = 'UPDATE' and new.year = old.year and new.last_number = old.last_number + 1 then
        return new;
    end if;
    raise exception 'the case reference counter only moves forward by one' using errcode = 'check_violation';
end
$$;

create trigger case_reference_counter_guard before insert or update or delete on case_reference_counter
    for each row execute function case_reference_counter_guard();
