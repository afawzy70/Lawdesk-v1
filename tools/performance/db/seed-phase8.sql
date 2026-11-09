-- Phase 8 expert-report fixture. Private throwaway LawDesk database only, after seed-develop and seed-phase5.
-- Deterministic 60 experts and 5,000 referrals; dates respect V407. All names/data are synthetic.
insert into expert (name, specialty, office, active, created_by, updated_by)
select 'SYN expert ' || g, (array['Accounting', 'Engineering', 'Forensics'])[1 + g % 3],
       'SYN office ' || g, true,
       '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from generate_series(1, 60) g;

insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, mission, report_due_date,
                             status, report_submitted_date, created_by, updated_by)
select p.case_id, p.id, e.id, p.filing_date + 60, 'SYN mission ' || p.rn,
       p.filing_date + 150,
       case when p.rn % 4 = 0 then 'REPORT_SUBMITTED' else 'OPEN' end,
       case when p.rn % 4 = 0 then p.filing_date + 120 end,
       '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from (select id, case_id, filing_date, row_number() over (order by id) rn from proceeding) p
join (select id, row_number() over (order by name) rn from expert where name like 'SYN expert %') e
  on e.rn = 1 + p.rn % 60
where p.rn <= 5000;

analyze;
