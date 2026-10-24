-- LD-040 WHAT-IF plans: the expensive queries again after whatif-indexes.sql, plus proposed rewrites. Measurement
-- only; nothing here is implemented in the application.

-- @query P-01 case list, default page, with (created_at desc, id) index
select id, reference_no, title, case_type, status, opened_on, updated_at
from legal_case where true order by created_at desc, id limit 20 offset 0;

-- @query P-01 case list, page 500 of 20, with the index (offset still walks 10 000 rows)
select id, reference_no, title, case_type, status, opened_on, updated_at
from legal_case where true order by created_at desc, id limit 20 offset 9980;

-- @query P-02 case search q=SYN 51 with trigram indexes (count)
select count(*) from legal_case where true
  and (reference_no ilike '%SYN 51%' escape '\' or title ilike '%SYN 51%' escape '\'
       or exists (select 1 from case_party cp join party p on p.id = cp.party_id
                  where cp.case_id = legal_case.id and p.name ilike '%SYN 51%' escape '\'));

-- @query P-02 case search rewritten as a union of index-friendly branches (count)
select count(*) from (
  select id from legal_case where reference_no ilike '%SYN 51%' escape '\'
  union select id from legal_case where title ilike '%SYN 51%' escape '\'
  union select cp.case_id from case_party cp join party p on p.id = cp.party_id where p.name ilike '%SYN 51%' escape '\'
) s;

-- @query P-02 party search q=SYN 12 with trigram index on name (count)
select count(*) from party
where name ilike '%SYN 12%' escape '\' or national_id ilike '%SYN 12%' escape '\'
   or registration_no ilike '%SYN 12%' escape '\';

-- @query P-03 case inventory current-stage counts computed in SQL, without sending 10 000 ids back
select stage_rank, count(*) from (
  select p.case_id, max(case p.stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 when 'CASSATION' then 2 end)
         as stage_rank
  from proceeding p join legal_case c on c.id = p.case_id
  where true
  group by p.case_id) s
group by stage_rank;

-- @query P-04 audit log total capped at 10 001 rows ("10 000+") instead of an exact count
select count(*) from (select 1 from audit_event limit 10001) s;

-- @query P-05 judgment report summary 2024-2026 with judgment_date index
select j.outcome, p.stage, count(*) as n, count(*) filter (where j.final) as final_n
from judgment j join proceeding p on p.id = j.proceeding_id join court c on c.id = p.court_id
where j.judgment_date between date '2024-01-01' and date '2026-12-31' group by j.outcome, p.stage;

-- @query P-05 judgment report page with judgment_date index
select j.id, j.judgment_date, j.outcome, j.final, p.stage, p.court_case_number, p.court_case_year, p.case_id,
       c.id as court_id, c.code, c.name_ar, c.name_en
from judgment j join proceeding p on p.id = j.proceeding_id join court c on c.id = p.court_id
where j.judgment_date between date '2024-01-01' and date '2026-12-31'
order by j.judgment_date desc, j.judgment_date desc, j.id limit 20 offset 0;

-- @query P-06 hearings awaiting a result with the partial index
select count(*) from hearing where status = 'SCHEDULED' and hearing_date < current_date;
