-- LD-040: the queries develop sends for the heaviest read paths, copied from the repositories (file:line in
-- docs/performance/PERFORMANCE-BASELINE.md §4). Parameters are bound as literals; the shapes are the application's.

-- @set CASE_ID = select id::text from legal_case order by reference_no offset 5000 limit 1
-- @set USER_ID = select id::text from app_user where username = 'syn.user20'

-- @rows synthetic volumes
select (select count(*) from legal_case) as cases, (select count(*) from party) as parties,
       (select count(*) from case_party) as case_parties, (select count(*) from proceeding) as proceedings,
       (select count(*) from judgment) as judgments, (select count(*) from special_decision) as special_decisions,
       (select count(*) from case_event) as case_events, (select count(*) from audit_event) as audit_events,
       (select count(*) from app_user) as users, (select count(*) from court) as courts,
       pg_size_pretty(pg_database_size(current_database())) as database_size;

-- @query case list, default page (CaseRepository.search: count)
select count(*) from legal_case where true;

-- @query case list, default page (CaseRepository.search: page)
select id, reference_no, title, case_type, status, opened_on, updated_at
from legal_case where true order by created_at desc, id limit 20 offset 0;

-- @query case list, page 500 of 20 (deep offset)
select id, reference_no, title, case_type, status, opened_on, updated_at
from legal_case where true order by created_at desc, id limit 20 offset 9980;

-- @query case search q=SYN 51 (count: reference, title or party name)
select count(*) from legal_case where true
  and (reference_no ilike '%SYN 51%' escape '\' or title ilike '%SYN 51%' escape '\'
       or exists (select 1 from case_party cp join party p on p.id = cp.party_id
                  where cp.case_id = legal_case.id and p.name ilike '%SYN 51%' escape '\'));

-- @query case search q=SYN 51 (page)
select id, reference_no, title, case_type, status, opened_on, updated_at from legal_case where true
  and (reference_no ilike '%SYN 51%' escape '\' or title ilike '%SYN 51%' escape '\'
       or exists (select 1 from case_party cp join party p on p.id = cp.party_id
                  where cp.case_id = legal_case.id and p.name ilike '%SYN 51%' escape '\'))
order by created_at desc, id limit 20 offset 0;

-- @query case search, status ACTIVE + opened since 2024, sorted by title
select id, reference_no, title, case_type, status, opened_on, updated_at from legal_case where true
  and status in ('ACTIVE') and opened_on >= date '2024-01-01'
order by title asc, id limit 20 offset 0;

-- @query case details (CaseRepository.find)
select id, reference_no, title, case_type, subject, opened_on, status, version, created_at, created_by,
       updated_at, updated_by from legal_case where id = :CASE_ID;

-- @query case timeline page (CaseTimeline.page: count)
select count(*) from case_event where case_id = :CASE_ID;

-- @query case timeline page (CaseTimeline.page: page)
select id, type, summary, occurred_at, created_by from case_event where case_id = :CASE_ID
order by seq desc limit 20 offset 0;

-- @query case parties (PartyRepository.ofCase)
select p.id, p.kind, p.name, cp.role, cp.created_at
from case_party cp join party p on p.id = cp.party_id where cp.case_id = :CASE_ID order by cp.created_at, p.name;

-- @query proceedings of a case (ProceedingRepository.findByCaseId, JPA)
select * from proceeding where case_id = :CASE_ID;

-- @query judgments of a case's proceedings (JudgmentRepository.findByProceedingIdIn, JPA)
select * from judgment where proceeding_id in (select id from proceeding where case_id = :CASE_ID);

-- @query party search q=SYN 12 (PartyRepository.search: count)
select count(*) from party
where name ilike '%SYN 12%' escape '\' or national_id ilike '%SYN 12%' escape '\'
   or registration_no ilike '%SYN 12%' escape '\';

-- @query dashboard: cases by status and type (CaseReportQueries.counts)
select status, case_type, count(*) as n from legal_case group by status, case_type;

-- @query dashboard: cases opened this month (CaseReportQueries.openedBetween)
select count(*) from legal_case where opened_on between date '2026-10-01' and date '2026-10-31';

-- @query dashboard: proceedings by stage awaiting judgment (ProceedingReportQueries.byStage)
select p.stage, count(*) as total, count(*) filter (where j.id is null) as awaiting
from proceeding p left join judgment j on j.proceeding_id = p.id group by p.stage;

-- @query dashboard: judgments of the period (JudgmentReportQueries.between)
select outcome, count(*) as n, count(*) filter (where final) as final_n
from judgment where judgment_date between date '2026-01-01' and date '2026-12-31' group by outcome;

-- @query sign-in: account by login (UserAccounts.findByLogin)
select id, username, email, display_name, locale, password_hash, enabled, must_change_password, locked_until,
       credential_version from app_user where lower(username) = lower('syn.user20') or lower(email) = lower('syn.user20');

-- @query every request: permissions of the signed-in user (UserAccounts, ReloadingSecurityContextRepository)
select distinct rp.permission from user_role ur join role_permission rp on rp.role_id = ur.role_id
where ur.user_id = :USER_ID;

-- @query audit log, default page (AuditController: count)
select count(*) from audit_event;

-- @query audit log, filtered by actor (AuditController: page)
select id, occurred_at, actor_id, actor_username, action, target_type, target_id, outcome, reason, request_id
from audit_event where lower(actor_username) = 'syn.user7' order by occurred_at desc, id desc limit 20 offset 0;
