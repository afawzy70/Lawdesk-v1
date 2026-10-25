-- QAD-033: the database itself keeps the hearing chronology and the proceeding/case relation, not only the API.
-- AUTHORIZED EXCEPTION (integration owner, architecture/CONTRACTS.md §8): the second trigger below is created on the
-- Phase 4 table `proceeding` from a Phase 5 migration, because the filing date and the case of a proceeding are
-- exactly what the hearings depend on. It adds no column and changes no row.
--
-- Nothing here rewrites or validates existing rows: only new statements are checked, so the migration cannot fail on
-- data the API accepted (every hearing was checked against its proceeding's filing date when it was written).
-- The API keeps answering its 422 business rules first, under the proceeding row lock; these triggers are the backstop
-- for any other writer. All of them refuse with a check violation (23514) that names the invariant.

-- 1. A hearing is never dated before its proceeding's filing date (cancelled hearings count too, as in the API), and
--    the next hearing of an adjournment follows the hearing it continues. The proceeding row is read FOR SHARE: a
--    concurrent filing-date correction (which locks the row exclusively) waits for this transaction to finish, and a
--    hearing written while a correction is under way waits for it and then sees the new filing date.
create function hearing_dates_are_consistent() returns trigger language plpgsql as $$
declare
    filed    date;
    previous date;
    following date;
begin
    select p.filing_date into filed from proceeding p where p.id = new.proceeding_id for share;
    if new.hearing_date < filed then
        raise exception 'a hearing cannot be dated before the filing of its proceeding'
            using errcode = 'check_violation', constraint = 'hearing_not_before_filing';
    end if;
    if new.adjourned_from_id is not null then
        select h.hearing_date into previous from hearing h where h.id = new.adjourned_from_id;
        if new.hearing_date <= previous then
            raise exception 'a hearing must follow the hearing it continues'
                using errcode = 'check_violation', constraint = 'hearing_follows_adjourned';
        end if;
    end if;
    select min(h.hearing_date) into following from hearing h where h.adjourned_from_id = new.id;
    if following is not null and following <= new.hearing_date then
        raise exception 'a hearing must precede the hearing that continues it'
            using errcode = 'check_violation', constraint = 'hearing_follows_adjourned';
    end if;
    return new;
end $$;

create trigger hearing_dates_are_consistent_insert
    before insert on hearing
    for each row execute function hearing_dates_are_consistent();

create trigger hearing_dates_are_consistent_update
    before update of hearing_date, proceeding_id, adjourned_from_id on hearing
    for each row
    when (old.hearing_date is distinct from new.hearing_date
          or old.proceeding_id is distinct from new.proceeding_id
          or old.adjourned_from_id is distinct from new.adjourned_from_id)
    execute function hearing_dates_are_consistent();

-- 2. A proceeding never changes its case (Phase 4 never does), and its filing date never moves after one of its
--    hearings. The expert referral part of the same rule is added with the expert tables (V407).
create function proceeding_keeps_dependent_dates() returns trigger language plpgsql as $$
begin
    if new.case_id is distinct from old.case_id then
        raise exception 'a proceeding never changes its case'
            using errcode = 'check_violation', constraint = 'proceeding_case_immutable';
    end if;
    if new.filing_date is distinct from old.filing_date
       and exists (select 1 from hearing h where h.proceeding_id = new.id and h.hearing_date < new.filing_date) then
        raise exception 'the filing date cannot be after a hearing of the proceeding'
            using errcode = 'check_violation', constraint = 'proceeding_filing_not_after_hearing';
    end if;
    return new;
end $$;

create trigger proceeding_keeps_dependent_dates
    before update of filing_date, case_id on proceeding
    for each row
    when (old.filing_date is distinct from new.filing_date or old.case_id is distinct from new.case_id)
    execute function proceeding_keeps_dependent_dates();
