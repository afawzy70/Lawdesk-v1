-- LD-058 (WP8) synthetic volumes for the performance harness. THROWAWAY DATABASE ONLY: the harness creates its own
-- embedded PostgreSQL; never run this against UAT or any shared database. Deterministic (setseed); every row is marked
-- SYNTH. ${cases} cases (50 000 for the target) and about ten child rows per case (more than 500 000 for the target).
select setseed(0.58);

insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
select 'synth.member' || g, 'synth.member' || g || '@example.invalid', 'SYNTH member ' || g, 'x', true,
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from generate_series(1, 200) g;

insert into court (code, name_ar, name_en, level, created_by, updated_by)
select 'SYNTH-' || g, 'محكمة اختبارية ' || g, 'SYNTH court ' || g,
       case when g <= 30 then 'FIRST_INSTANCE' when g <= 50 then 'APPEAL' else 'CASSATION' end,
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from generate_series(1, 60) g;

create temporary table synth_member as
select id, row_number() over (order by username) as rn from app_user where username like 'synth.member%';

insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by, primary_member_id,
                        alternate_member_id, claimed_amount, confidentiality)
select 'SYNTH-' || lpad(g::text, 6, '0'),
       (array['دعوى تعويض', 'نزاع عقد إيجار', 'Contract dispute', 'Labour claim', 'Family matter', 'Tax appeal'])[1 + g % 6]
           || ' SYNTH ' || g,
       (array['CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE', 'CONSTITUTIONAL', 'OTHER'])[1 + (g * 7) % 8],
       date '2020-01-01' + (g % 2500),
       case when g % 20 < 4 then 'OPEN' when g % 20 < 13 then 'ACTIVE' when g % 20 < 14 then 'SUSPENDED'
            when g % 20 < 19 then 'CLOSED' else 'ARCHIVED' end,
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5',
       (select id from synth_member where rn = 1 + g % 200),
       case when g % 5 = 0 then (select id from synth_member where rn = 1 + (g + 1) % 200) end,
       case when g % 3 = 0 then (g % 997) * 1000 + 0.5 end,
       case when g % 50 = 0 then 'RESTRICTED' else 'NORMAL' end
from generate_series(1, ${cases}) g;

create temporary table synth_case as
select id, row_number() over (order by reference_no) as rn, opened_on, status from legal_case
where reference_no like 'SYNTH-%';
create unique index synth_case_rn on synth_case (rn);

-- Primary involvement for every case: a sector, a department, a section (names copied, as the application does).
insert into case_org_involvement (case_id, is_primary, sector_name_snapshot, department_name_snapshot,
                                  section_name_snapshot, created_by, updated_by)
select id, true, 'SYNTH sector ' || (rn % 5), 'SYNTH department ' || (rn % 25),
       case when rn % 4 = 0 then null else 'SYNTH section ' || (rn % 100) end,
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_case;

-- One first-instance proceeding per case, a second for every other case.
insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by, updated_by)
select c.id, 'FIRST_INSTANCE', (select id from court where code = 'SYNTH-' || (1 + (c.rn + k) % 30)),
       'S' || c.rn || '-' || k, 2020 + c.rn % 6, c.opened_on + k, '00000000-0000-4000-8000-0000000000e5',
       '00000000-0000-4000-8000-0000000000e5'
from synth_case c cross join generate_series(0, 1) k
where k = 0 or c.rn % 2 = 0;

create temporary table synth_proceeding as
select p.id, p.case_id, p.filing_date, row_number() over (order by p.case_id, p.court_case_number) as rn
from proceeding p join synth_case c on c.id = p.case_id;
create unique index synth_proceeding_rn on synth_proceeding (rn);

-- A judgment for four proceedings in ten.
insert into judgment (proceeding_id, judgment_date, outcome, final, created_by, updated_by)
select id, filing_date + 60, (array['IN_FAVOUR', 'AGAINST', 'PARTIAL', 'DISMISSED', 'OTHER'])[1 + rn % 5], rn % 3 = 0,
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_proceeding where rn % 10 < 4;

-- Two hearings per proceeding: one past and held, one in the coming weeks and scheduled; every twentieth is for
-- pronouncement of judgment and every thirtieth an expert session.
insert into hearing (case_id, proceeding_id, hearing_date, purpose, kind, status, result_recorded_at, result_recorded_by,
                     created_by, updated_by)
select p.case_id, p.id,
       case when k = 0 then least(current_date - 1, p.filing_date + 1 + (p.rn % 90)::int) else current_date + (p.rn % 40)::int end,
       case when p.rn % 20 = 0 then 'JUDGMENT' else (array['FIRST_SESSION', 'PLEADING', 'EVIDENCE', 'EXPERT_REPORT'])[1 + p.rn % 4] end,
       case when p.rn % 30 = 0 then 'EXPERT' else 'COURT' end,
       case when k = 0 then 'HELD' else 'SCHEDULED' end,
       case when k = 0 then now() end, case when k = 0 then '00000000-0000-4000-8000-0000000000e5'::uuid end,
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_proceeding p cross join generate_series(0, 1) k;

insert into task (case_id, title, assignee_id, due_date, status, completed_at, created_by, updated_by)
select c.id, 'SYNTH task ' || c.rn || '-' || k, (select id from synth_member where rn = 1 + (c.rn + k) % 200),
       current_date + ((c.rn + k * 7) % 60)::int - 30,
       case when (c.rn + k) % 3 = 0 then 'DONE' else 'OPEN' end,
       case when (c.rn + k) % 3 = 0 then now() end,
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_case c cross join generate_series(0, 1) k where k = 0 or c.rn % 2 = 0;

insert into claim (case_id, seq, text, kind, created_by, updated_by)
select id, 1, 'SYNTH claim ' || rn, 'PRIMARY', '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_case;

insert into defense (case_id, seq, title, kind, created_by, updated_by)
select id, 1, 'SYNTH defence ' || (rn % 40), (array['FORMAL', 'PROCEDURAL', 'SUBSTANTIVE', 'OTHER'])[1 + rn % 4],
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_case;

insert into evidence_item (case_id, name, role, created_by, updated_by)
select id, 'SYNTH evidence ' || rn, (array['SUPPORTING', 'OPPONENT', 'DISPUTED', 'REQUIRED', 'UNAVAILABLE', 'SUPPLEMENTARY'])[1 + rn % 6],
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_case;

insert into info_request (case_id, request_no, unit_name_snapshot, subject, status, due_on, sent_on, created_by, updated_by)
select id, 'IR-SYNTH-' || rn, 'SYNTH unit ' || (rn % 30), 'SYNTH request', (array['SENT', 'AWAITING', 'COMPLETE', 'DRAFT'])[1 + rn % 4],
       current_date + (rn % 40)::int - 20, current_date - 40, '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_case where rn % 5 < 2;

insert into risk_assessment (case_id, version_no, level, level_reasons, potential_loss, trigger_kind, assessed_by)
select id, v, (array['LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'])[1 + (rn + v) % 4], 'SYNTH reasons', ((rn + v) % 500) * 100 + 0.25,
       'MANUAL', '00000000-0000-4000-8000-0000000000e5'
from synth_case cross join generate_series(1, 2) v where rn % 4 = 0;

insert into case_link (case_id, other_case_id, kind, reason, status, created_by, updated_by)
select a.id, b.id, 'SUBJECT_LINK', 'SYNTH same subject', case when a.rn % 3 = 0 then 'REQUESTED' else 'ACTIVE' end,
       '00000000-0000-4000-8000-0000000000e5', '00000000-0000-4000-8000-0000000000e5'
from synth_case a join synth_case b on b.rn = a.rn + 1 where a.rn % 5 = 0;

analyze;
