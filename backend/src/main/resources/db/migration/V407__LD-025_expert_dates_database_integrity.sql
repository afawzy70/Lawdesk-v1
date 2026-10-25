-- QAD-033 for LD-025: the database keeps the dates of expert referrals and sessions consistent, for any writer. Like
-- V406 it validates and rewrites no existing row (only new statements are checked), so it cannot fail on data the API
-- accepted. The API keeps answering its 422 rules first, under the proceeding row lock.

-- 1. A referral is never dated before its proceeding's filing date (cancelled referrals count too, as in the API) and
--    never after the first session held under it. The proceeding row is read FOR SHARE, as for hearings (V406): a
--    concurrent filing-date correction waits for this transaction, and a referral written during a correction waits
--    for it and then sees the new filing date.
create function expert_referral_dates_are_consistent() returns trigger language plpgsql as $$
declare
    filed date;
begin
    select p.filing_date into filed from proceeding p where p.id = new.proceeding_id for share;
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

create trigger expert_referral_dates_are_consistent_insert
    before insert on expert_referral
    for each row execute function expert_referral_dates_are_consistent();

create trigger expert_referral_dates_are_consistent_update
    before update of referral_date, proceeding_id on expert_referral
    for each row
    when (old.referral_date is distinct from new.referral_date or old.proceeding_id is distinct from new.proceeding_id)
    execute function expert_referral_dates_are_consistent();

-- 2. A session is never dated before its referral.
create function expert_session_follows_referral() returns trigger language plpgsql as $$
declare
    referred date;
begin
    select r.referral_date into referred from expert_referral r where r.id = new.referral_id for share;
    if new.session_date < referred then
        raise exception 'an expert session cannot be dated before its referral'
            using errcode = 'check_violation', constraint = 'expert_session_not_before_referral';
    end if;
    return new;
end $$;

create trigger expert_session_follows_referral_insert
    before insert on expert_session
    for each row execute function expert_session_follows_referral();

create trigger expert_session_follows_referral_update
    before update of session_date, referral_id on expert_session
    for each row
    when (old.session_date is distinct from new.session_date or old.referral_id is distinct from new.referral_id)
    execute function expert_session_follows_referral();

-- 3. The trigger on the Phase 4 table `proceeding` (V406, authorized exception) now also refuses a filing date after
--    an expert referral. Same trigger, the function is replaced.
create or replace function proceeding_keeps_dependent_dates() returns trigger language plpgsql as $$
begin
    if new.case_id is distinct from old.case_id then
        raise exception 'a proceeding never changes its case'
            using errcode = 'check_violation', constraint = 'proceeding_case_immutable';
    end if;
    if new.filing_date is distinct from old.filing_date then
        if exists (select 1 from hearing h where h.proceeding_id = new.id and h.hearing_date < new.filing_date) then
            raise exception 'the filing date cannot be after a hearing of the proceeding'
                using errcode = 'check_violation', constraint = 'proceeding_filing_not_after_hearing';
        end if;
        if exists (select 1 from expert_referral r
                   where r.proceeding_id = new.id and r.referral_date < new.filing_date) then
            raise exception 'the filing date cannot be after an expert referral of the proceeding'
                using errcode = 'check_violation', constraint = 'proceeding_filing_not_after_referral';
        end if;
    end if;
    return new;
end $$;
