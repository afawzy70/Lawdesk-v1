-- LD-040 synthetic volumes for the tables on develop. THROWAWAY LOCAL DATABASE ONLY: never run against UAT or any
-- shared database. Deterministic (setseed), no real names, identifiers or credentials; every row is marked SYN.
-- Volumes (docs/performance/PERFORMANCE-BASELINE.md §3): 50 users, 60 courts, 10 000 cases, 20 000 parties,
-- 30 000 case-party links, ~50 000 proceedings, ~30 000 judgments, ~5 000 special decisions, 150 000 timeline events,
-- 200 000 audit events.
select setseed(0.42);

insert into app_user (username, email, display_name, password_hash, created_by, updated_by)
select 'syn.user' || g, 'syn.user' || g || '@example.invalid', 'SYN user ' || g, 'not-a-password-hash',
       '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from generate_series(1, 50) g;

insert into user_role (user_id, role_id)
select u.id, r.id
from (select id, row_number() over (order by username) rn from app_user where username like 'syn.%') u
join app_role r on r.code = case when u.rn <= 2 then 'ADMIN' when u.rn <= 10 then 'LEGAL_MANAGER' else 'LEGAL_STAFF' end;

insert into court (code, name_ar, name_en, level, governorate, created_by, updated_by)
select 'SYN-' || g, 'محكمة تجريبية ' || g, 'SYN court ' || g,
       case when g <= 30 then 'FIRST_INSTANCE' when g <= 50 then 'APPEAL' else 'CASSATION' end,
       'SYN governorate ' || (g % 10), '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from generate_series(1, 60) g;

insert into legal_case (reference_no, title, case_type, subject, opened_on, status, created_at, created_by,
                        updated_at, updated_by)
select 'SYN-' || lpad(g::text, 6, '0'),
       (array['دعوى تعويض', 'نزاع عقد إيجار', 'Contract dispute', 'Labour claim', 'Family matter', 'Tax appeal'])
           [1 + g % 6] || ' ' || g,
       (array['CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE'])[1 + (g * 7) % 6],
       'SYN subject ' || md5(g::text),
       d,
       case when r < 0.20 then 'OPEN' when r < 0.65 then 'ACTIVE' when r < 0.70 then 'SUSPENDED'
            when r < 0.95 then 'CLOSED' else 'ARCHIVED' end,
       d + interval '9 hours', '00000000-0000-0000-0000-000000000000',
       d + interval '9 hours' + (random() * interval '300 days'), '00000000-0000-0000-0000-000000000000'
from (select g, date '2015-01-01' + (random() * 4300)::int as d, random() as r
      from generate_series(1, 10000) g) s;

insert into party (kind, name, national_id, registration_no, phone, created_by, updated_by)
select case when g % 4 = 0 then 'ORGANIZATION' else 'PERSON' end,
       case when g % 4 = 0 then 'SYN Company ' || g
            else (array['أحمد', 'محمد', 'سارة', 'Mona', 'Omar', 'Laila'])[1 + g % 6] || ' SYN ' || g end,
       case when g % 4 = 0 then null else '29' || lpad(g::text, 12, '0') end,
       case when g % 4 = 0 then 'SYN-CR-' || g end,
       '+20 100 ' || lpad(g::text, 7, '0'),
       '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from generate_series(1, 20000) g;

insert into case_party (case_id, party_id, role, created_by)
select c.id, p.id, (array['CLIENT', 'OPPONENT', 'OTHER'])[k + 1], '00000000-0000-0000-0000-000000000000'
from (select id, row_number() over (order by reference_no) rn from legal_case) c
cross join generate_series(0, 2) k
join (select id, row_number() over (order by name, id) rn from party) p on p.rn = ((c.rn * 7 + k * 1013) % 20000) + 1;

-- Three first-instance proceedings per case, appeals of most of them, cassations of a third of the appeals.
create temporary table syn_case as
select id, row_number() over (order by reference_no) rn, opened_on from legal_case;

insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by,
                        updated_by)
select c.id, 'FIRST_INSTANCE', ct.id, 'SYN' || c.rn || '-' || k, extract(year from c.opened_on)::int,
       c.opened_on + k * 30, '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from syn_case c cross join generate_series(0, 2) k
join (select id, row_number() over (order by code) rn from court where level = 'FIRST_INSTANCE') ct
  on ct.rn = ((c.rn + k) % 30) + 1;

insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                        parent_proceeding_id, created_by, updated_by)
select p.case_id, 'APPEAL', ct.id, p.court_case_number || '-A', p.court_case_year, p.filing_date + 200, p.id,
       '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from (select p.*, row_number() over (order by p.court_case_number) rn from proceeding p
      where p.stage = 'FIRST_INSTANCE') p
join (select id, row_number() over (order by code) rn from court where level = 'APPEAL') ct
  on ct.rn = (p.rn % 20) + 1
where p.rn % 2 = 0;

insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                        parent_proceeding_id, created_by, updated_by)
select p.case_id, 'CASSATION', ct.id, p.court_case_number || '-C', p.court_case_year, p.filing_date + 300, p.id,
       '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from (select p.*, row_number() over (order by p.court_case_number) rn from proceeding p where p.stage = 'APPEAL') p
join (select id, row_number() over (order by code) rn from court where level = 'CASSATION') ct
  on ct.rn = (p.rn % 10) + 1
where p.rn % 3 = 0;

insert into judgment (proceeding_id, judgment_date, outcome, summary, final, created_by, updated_by)
select p.id, p.filing_date + 120 + (random() * 300)::int,
       (array['IN_FAVOUR', 'AGAINST', 'PARTIAL', 'DISMISSED', 'OTHER'])[1 + (random() * 4)::int],
       'SYN judgment', random() < 0.3, '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from proceeding p
where random() < 0.6;

insert into special_decision (proceeding_id, decision_type, decision_date, effect, summary, created_by, updated_by)
select p.id, 'URGENT_MEASURE', p.filing_date + 20,
       (array['GRANTED', 'PARTIALLY_GRANTED', 'REFUSED'])[1 + (random() * 2)::int], 'SYN decision',
       '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from proceeding p
where random() < 0.1;

insert into case_event (case_id, type, summary, occurred_at, created_by)
select c.id, (array['CASE_CREATED', 'CASE_UPDATED', 'PROCEEDING_RECORDED', 'JUDGMENT_RECORDED'])[1 + k % 4],
       'SYN event ' || k, c.opened_on + k * interval '7 days', '00000000-0000-0000-0000-000000000000'
from syn_case c cross join generate_series(1, 15) k
order by c.opened_on + k * interval '7 days';

insert into audit_event (occurred_at, actor_id, actor_username, action, target_type, target_id, outcome, reason)
select now() - (g * interval '150 seconds'), null, 'syn.user' || (1 + g % 50),
       (array['LOGIN', 'LOGOUT', 'ACCESS_DENIED', 'PASSWORD_CHANGE', 'REPORT_EXPORT'])[1 + g % 5], 'USER', g::text,
       case when g % 17 = 0 then 'FAILURE' when g % 23 = 0 then 'DENIED' else 'SUCCESS' end, null
from generate_series(1, 200000) g;

drop table syn_case;
analyze;
