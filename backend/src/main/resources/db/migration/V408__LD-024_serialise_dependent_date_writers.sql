-- QAD-038: V406 took the proceeding row only FOR SHARE, which two hearing writers hold together, so they did not
-- exclude each other: a writer of the parent hearing and a writer of its successor each read the other's OLD, still
-- uncommitted date, both passed, and both committed a successor dated on or before its parent. The functions below are
-- replaced (V406 and V407 are never edited) so that every hearing write of one proceeding takes the proceeding row
-- FOR NO KEY UPDATE as the FIRST statement of the trigger. Writers of one proceeding then run one after the other, and
-- the later writer's next statement (every statement of a VOLATILE plpgsql function takes a fresh READ COMMITTED
-- snapshot) sees the earlier writer's commit. The same lock is taken by the expert referral and session triggers, so
-- the dates of one proceeding are checked against each other under one lock.
--
-- Deadlocks. All the triggers take locks in the same order: the written row first (PostgreSQL locks it before a
-- BEFORE ROW trigger runs), then the proceeding row; no trigger waits for any other row while it holds the
-- proceeding row (the reads that follow are plain SELECTs, which never wait). So two hearing triggers cannot form a
-- cycle with each other. The API also locks the hearing first and the proceeding second when it edits a hearing; the
-- filing-date correction and the scheduling of a new hearing take the proceeding first but wait for no hearing row.
-- The one cycle left is a raw writer that already holds a hearing row against an API request that holds the proceeding
-- and wants that same hearing (for example a raw insert of a successor whose foreign-key check needs the parent row
-- while the API holds it FOR UPDATE and waits for the proceeding): PostgreSQL detects it and aborts one of the two
-- (SQLSTATE 40P01), the other commits; nothing is half written.
--
-- Nothing here rewrites or validates an existing row; the triggers themselves are unchanged (only the functions are
-- replaced), so cancelled hearings and history stay as they are and keep bounding the dates.

create or replace function hearing_dates_are_consistent() returns trigger language plpgsql as $$
declare
    filed     date;
    previous  date;
    following date;
begin
    -- First statement: serialise every hearing writer of this proceeding (and any filing-date correction).
    select p.filing_date into filed from proceeding p where p.id = new.proceeding_id for no key update;
    if new.hearing_date < filed then
        raise exception 'a hearing cannot be dated before the filing of its proceeding'
            using errcode = 'check_violation', constraint = 'hearing_not_before_filing';
    end if;
    -- The reads below start after the lock was granted, so they see everything the earlier writer committed.
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

-- The same first-statement lock for the expert referral dates (V407), so a referral, its sessions and the filing
-- date of the proceeding are checked against each other under one lock.
create or replace function expert_referral_dates_are_consistent() returns trigger language plpgsql as $$
declare
    filed date;
begin
    select p.filing_date into filed from proceeding p where p.id = new.proceeding_id for no key update;
    if new.referral_date < filed then
        raise exception 'an expert referral cannot be dated before the filing of its proceeding'
            using errcode = 'check_violation', constraint = 'expert_referral_not_before_filing';
    end if;
    if tg_op = 'UPDATE' and exists (select 1 from expert_session s
                                    where s.referral_id = new.id and s.session_date < new.referral_date) then
        raise exception 'an expert referral cannot be dated after one of its sessions'
            using errcode = 'check_violation', constraint = 'expert_session_not_before_referral';
    end if;
    return new;
end $$;

-- A session locks its proceeding first (like the referral and hearing triggers), then reads its referral's date.
create or replace function expert_session_follows_referral() returns trigger language plpgsql as $$
declare
    referred date;
begin
    perform 1 from proceeding p join expert_referral r on r.proceeding_id = p.id
        where r.id = new.referral_id for no key update of p;
    select r.referral_date into referred from expert_referral r where r.id = new.referral_id;
    if new.session_date < referred then
        raise exception 'an expert session cannot be dated before its referral'
            using errcode = 'check_violation', constraint = 'expert_session_not_before_referral';
    end if;
    return new;
end $$;
