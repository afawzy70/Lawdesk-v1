-- LD-040: queries of the open Phase 5/6 pull requests (#34/#36 reports, #33/#37/#40 hearings, tasks, documents),
-- copied from their heads (SHAs in docs/performance/PERFORMANCE-BASELINE.md §4.2). Not on develop when written: the
-- plans are an early warning, to be re-run when each PR merges.

-- @set CASE_ID = select id::text from legal_case order by reference_no offset 5000 limit 1
-- @set DOC_CASE_ID = select case_id::text from case_document order by case_id limit 1
-- @set DOC_ID = select id::text from case_document order by case_id limit 1
-- @set USER_ID = select id::text from app_user where username = 'syn.user20'
-- @set ALL_IDS = select '{' || string_agg(id::text, ',') || '}' from legal_case
-- @set PAGE_IDS = select '{' || string_agg(id::text, ',') || '}' from (select id from legal_case order by opened_on desc, lower(reference_no), id limit 20) s

-- @rows synthetic Phase 5 volumes
select (select count(*) from hearing) as hearings,
       (select count(*) from hearing where hearing_date between current_date and current_date + 30) as hearings_next_30_days,
       (select count(*) from task) as tasks, (select count(*) from case_document) as documents,
       pg_size_pretty(pg_total_relation_size('case_document_content')) as document_content_size;

-- @query case inventory: summary counts, no filter (CaseReportQueries.counts)
select status, case_type, count(*) as n from legal_case where true group by status, case_type;

-- @query case inventory: page sorted by openedOn desc (CaseReportQueries.list)
select id, reference_no, title, case_type, status, opened_on from legal_case where true
order by opened_on desc, lower(reference_no), id limit 20 offset 0;

-- @query case inventory: every matching case id (CaseReportQueries.ids, read into the JVM for each page)
select id from legal_case where true;

-- @query case inventory: current stage counts for all 10 000 ids sent back as uuid[] (ProceedingReportQueries.currentStageCounts)
select cur.stage, count(*) as n from (
  select distinct on (p.case_id) p.case_id, p.stage, p.court_id, c.code, c.name_ar, c.name_en
  from proceeding p join court c on c.id = p.court_id
  where p.case_id = any(cast(:ALL_IDS as uuid[]))
  order by p.case_id, case p.stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 when 'CASSATION' then 2 end desc,
           p.filing_date desc, p.created_at desc, p.id desc) cur
group by cur.stage;

-- @query case inventory: positions of the 20 cases on the page (ProceedingReportQueries.positions)
select cur.*, (select count(*) from proceeding x where x.case_id = cur.case_id) as proceedings from (
  select distinct on (p.case_id) p.case_id, p.stage, p.court_id, c.code, c.name_ar, c.name_en
  from proceeding p join court c on c.id = p.court_id
  where p.case_id = any(cast(:PAGE_IDS as uuid[]))
  order by p.case_id, case p.stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 when 'CASSATION' then 2 end desc,
           p.filing_date desc, p.created_at desc, p.id desc) cur;

-- @query case inventory: stage filter APPEAL (ProceedingReportQueries.casesMatching)
select cur.case_id from (
  select distinct on (p.case_id) p.case_id, p.stage, p.court_id, c.code, c.name_ar, c.name_en
  from proceeding p join court c on c.id = p.court_id
  order by p.case_id, case p.stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 when 'CASSATION' then 2 end desc,
           p.filing_date desc, p.created_at desc, p.id desc) cur
where true and cur.stage in ('APPEAL');

-- @query judgment report: summary of 2024-2026 (JudgmentReportQueries.summarize)
select j.outcome, p.stage, count(*) as n, count(*) filter (where j.final) as final_n
from judgment j join proceeding p on p.id = j.proceeding_id join court c on c.id = p.court_id
where j.judgment_date between date '2024-01-01' and date '2026-12-31' group by j.outcome, p.stage;

-- @query judgment report: page sorted by date desc (JudgmentReportQueries.list)
select j.id, j.judgment_date, j.outcome, j.final, p.stage, p.court_case_number, p.court_case_year, p.case_id,
       c.id as court_id, c.code, c.name_ar, c.name_en
from judgment j join proceeding p on p.id = j.proceeding_id join court c on c.id = p.court_id
where j.judgment_date between date '2024-01-01' and date '2026-12-31'
order by j.judgment_date desc, j.judgment_date desc, j.id limit 20 offset 0;

-- @query hearing calendar: 30 days from today (HearingRepository.findBetween: page)
select h.* from hearing h
where h.hearing_date between current_date and current_date + 30 and (null::varchar is null or h.status = null)
order by h.hearing_date, h.hearing_time nulls first, h.created_at, h.id limit 20 offset 0;

-- @query hearing calendar: 30 days from today (count)
select count(h.id) from hearing h
where h.hearing_date between current_date and current_date + 30 and (null::varchar is null or h.status = null);

-- @query hearing calendar row lookups, once per distinct case on the page (HearingService.Lookups: CaseLookup.find)
select id, reference_no, title, case_type, subject, opened_on, status, version, created_at, created_by,
       updated_at, updated_by from legal_case where id = :CASE_ID;

-- @query hearings of a case (HearingRepository.findForCase)
select h.* from hearing h where h.case_id = :CASE_ID
order by h.hearing_date, h.hearing_time nulls first, h.created_at, h.id limit 20 offset 0;

-- @query hearings awaiting a result (HearingReportQueries: overdue count)
select count(*) from hearing where status = 'SCHEDULED' and hearing_date < current_date;

-- @query tasks of an assignee, open (TaskRepository.search)
select t.* from task t
where (cast(:USER_ID as uuid) is null or t.assignee_id = cast(:USER_ID as uuid)) and (null::uuid is null or t.case_id = null)
  and t.status in ('OPEN', 'IN_PROGRESS') and (true = true or t.due_date between current_date and current_date)
order by t.due_date asc nulls last,
         case t.priority when 'URGENT' then 0 when 'HIGH' then 1 when 'NORMAL' then 2 else 3 end, t.created_at, t.id
limit 20 offset 0;

-- @query task reminders: overdue count of an assignee (TaskRepository.countBy...DueDateBefore)
select count(*) from task where assignee_id = cast(:USER_ID as uuid) and status in ('OPEN', 'IN_PROGRESS')
  and due_date < current_date;

-- @query documents of a case (case_document_case index)
select id, case_id, title, file_name, media_type, size_bytes, sha256, version, created_at, created_by
from case_document where case_id = :DOC_CASE_ID order by created_at desc;

-- @query document download (content row)
select content from case_document_content where document_id = :DOC_ID;
