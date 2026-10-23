-- Test fixture: creates a table, then fails. PostgreSQL runs the migration in one transaction, so nothing may remain.
create table broken_half_applied (id bigint primary key);
select 1 / 0;
