-- LD-DOC restore drill: representative SYNTHETIC data (every text carries SYNTH-DOC; no real names, numbers or files).
-- THROWAWAY DATABASE ONLY. restore_drill.py runs this as the application role (lawdesk_app, search_path=lawdesk), after the
-- application itself has migrated the database, so every row passes the real constraints and triggers.
--
-- psql variables (set by the driver): scale (cases = 300 * scale), pw_hash (bcrypt hash of the persona password).
-- Deterministic: the same scale gives the same rows and the same file bytes.
\set ON_ERROR_STOP 1
select setseed(0.31);
select 'SYNTH-DOC seed, scale ' || :scale as info;

create temporary table seed_actor as select '00000000-0000-4000-8000-0000000000d0'::uuid as id;

-- --- organisation units (both trees) ---------------------------------------------------------------------------------
insert into org_unit (id, tree, parent_id, level, name_ar, name_en, created_by, updated_by)
select ('10000000-0000-4000-8000-0000000000' || lpad(to_hex(n), 2, '0'))::uuid, 'LEGAL',
       case when n = 1 then null when n <= 4 then '10000000-0000-4000-8000-000000000001'::uuid
            else ('10000000-0000-4000-8000-0000000000' || lpad(to_hex(2 + (n - 5) % 3), 2, '0'))::uuid end,
       case when n = 1 then 'SECTOR' when n <= 4 then 'DEPARTMENT' else 'SECTION' end,
       'SYNTH-DOC وحدة ' || n, 'SYNTH-DOC unit ' || n, (select id from seed_actor), (select id from seed_actor)
from generate_series(1, 10) n;

-- --- personas: manager (department-wide scope), section head (own unit subtree), staff (case-type scope, grants, tasks) ---
insert into app_user (id, username, email, display_name, password_hash, org_unit_id, created_by, updated_by) values
    ('20000000-0000-4000-8000-000000000001', 'synth.manager', 'synth.manager@example.invalid', 'SYNTH-DOC manager', :'pw_hash', null, (select id from seed_actor), (select id from seed_actor)),
    ('20000000-0000-4000-8000-000000000002', 'synth.head', 'synth.head@example.invalid', 'SYNTH-DOC section head', :'pw_hash', '10000000-0000-4000-8000-000000000002', (select id from seed_actor), (select id from seed_actor)),
    ('20000000-0000-4000-8000-000000000003', 'synth.staff', 'synth.staff@example.invalid', 'SYNTH-DOC staff', :'pw_hash', '10000000-0000-4000-8000-000000000005', (select id from seed_actor), (select id from seed_actor)),
    ('20000000-0000-4000-8000-000000000004', 'synth.archivist', 'synth.archivist@example.invalid', 'SYNTH-DOC archivist', :'pw_hash', '10000000-0000-4000-8000-000000000003', (select id from seed_actor), (select id from seed_actor));
insert into user_role (user_id, role_id)
select u.id, r.id from (values ('synth.manager', 'LEGAL_MANAGER'), ('synth.head', 'SECTION_HEAD'), ('synth.staff', 'LEGAL_STAFF'),
                               ('synth.archivist', 'ARCHIVIST')) m (username, role)
join app_user u on u.username = m.username join app_role r on r.code = m.role;
-- access scopes: the staff member also sees LABOR cases of every unit; the head sees the unit of the user record.
insert into access_scope (subject_type, subject_ref, scope_type, scope_ref, created_by) values
    ('USER', '20000000-0000-4000-8000-000000000003', 'CASE_TYPE', 'LABOR', (select id from seed_actor)),
    ('ROLE', 'ARCHIVIST', 'ORG_UNIT', '10000000-0000-4000-8000-000000000003', (select id from seed_actor));
insert into access_scope (subject_type, subject_ref, scope_type, created_by)
values ('ROLE', 'LEGAL_STAFF', 'OWN_UNIT', (select id from seed_actor)) on conflict do nothing;

-- --- courts, parties, cases -------------------------------------------------------------------------------------------
insert into court (code, name_ar, name_en, level, governorate, created_by, updated_by)
select 'SYNTH-' || g, 'SYNTH-DOC محكمة ' || g, 'SYNTH-DOC court ' || g,
       (array['FIRST_INSTANCE', 'APPEAL', 'CASSATION'])[1 + g % 3], 'SYNTH-DOC governorate', (select id from seed_actor), (select id from seed_actor)
from generate_series(1, 12) g;

create temporary table seed_case as
select g as n, gen_random_uuid() as id, 'SYNTH-DOC-' || lpad(g::text, 6, '0') as reference_no
from generate_series(1, 300 * :scale) g;

insert into legal_case (id, reference_no, title, case_type, subject, opened_on, status, primary_member_id, alternate_member_id,
                        owning_unit_id, confidentiality, facts, claimed_amount, created_by, updated_by)
select c.id, c.reference_no, 'SYNTH-DOC ' || (array['دعوى تعويض', 'نزاع عقد', 'Contract dispute', 'Labour claim'])[1 + c.n % 4] || ' ' || c.n,
       (array['CIVIL', 'COMMERCIAL', 'LABOR', 'ADMINISTRATIVE', 'CRIMINAL'])[1 + c.n % 5],
       'SYNTH-DOC subject ' || md5(c.n::text), date '2024-01-01' + (c.n % 600),
       (array['OPEN', 'ACTIVE', 'ACTIVE', 'SUSPENDED', 'CLOSED'])[1 + c.n % 5],
       case when c.n % 7 = 0 then '20000000-0000-4000-8000-000000000003'::uuid
            when c.n % 11 = 0 then '20000000-0000-4000-8000-000000000002'::uuid
            when c.n % 13 = 0 then '20000000-0000-4000-8000-000000000004'::uuid end,
       case when c.n % 7 = 0 and c.n % 2 = 0 then '20000000-0000-4000-8000-000000000004'::uuid end,
       ('10000000-0000-4000-8000-0000000000' || lpad(to_hex(2 + c.n % 9), 2, '0'))::uuid,
       case when c.n % 9 = 0 then 'RESTRICTED' else 'NORMAL' end,
       'SYNTH-DOC facts ' || repeat('وقائع ', 20), 1000 * (1 + c.n % 50),
       (select id from seed_actor), (select id from seed_actor)
from seed_case c;
insert into case_reference_counter (year, last_number) values (2024, 300 * :scale) on conflict (year) do update set last_number = excluded.last_number;
insert into case_document_quota (case_id) select id from seed_case;

insert into party (id, kind, name, phone, created_by, updated_by)
select gen_random_uuid(), case when g % 4 = 0 then 'ORGANIZATION' else 'PERSON' end, 'SYNTH-DOC party ' || g,
       '+20 100 ' || lpad(g::text, 7, '0'), (select id from seed_actor), (select id from seed_actor)
from generate_series(1, 200 * :scale) g;
insert into case_party (case_id, party_id, role, created_by)
select c.id, p.id, (array['CLIENT', 'OPPONENT'])[1 + k], (select id from seed_actor)
from seed_case c cross join generate_series(0, 1) k
join (select id, row_number() over (order by name) rn from party) p on p.rn = ((c.n * 3 + k) % (200 * :scale)) + 1;

-- --- proceedings, judgments, hearings ---------------------------------------------------------------------------------
insert into proceeding (id, case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by, updated_by)
select gen_random_uuid(), c.id, 'FIRST_INSTANCE', (select id from court order by code limit 1 offset c.n % 12), 'SYNTH-' || c.n, 2024,
       date '2024-02-01' + (c.n % 400), (select id from seed_actor), (select id from seed_actor)
from seed_case c;
create temporary table seed_proc as select p.id, p.case_id, row_number() over (order by p.court_case_number) rn from proceeding p;

insert into judgment (id, proceeding_id, judgment_date, outcome, summary, final, created_by, updated_by)
select gen_random_uuid(), p.id, date '2025-03-01' + (p.rn % 300)::int, (array['IN_FAVOUR', 'AGAINST', 'PARTIAL'])[1 + p.rn % 3],
       'SYNTH-DOC judgment ' || p.rn, p.rn % 2 = 0, (select id from seed_actor), (select id from seed_actor)
from seed_proc p where p.rn % 3 <> 0;
create temporary table seed_judgment as
select j.id, p.case_id, row_number() over (order by j.id) rn from judgment j join proceeding p on p.id = j.proceeding_id;

insert into hearing (id, case_id, proceeding_id, hearing_date, purpose, status, created_by, updated_by)
select gen_random_uuid(), p.case_id, p.id, date '2025-01-05' + (p.rn % 500)::int, 'PLEADING', 'SCHEDULED',
       (select id from seed_actor), (select id from seed_actor)
from seed_proc p where p.rn % 2 = 1;
create temporary table seed_hearing as select h.id, h.case_id, row_number() over (order by h.id) rn from hearing h;

-- --- claims, defenses, tasks (every status of V720), grants -------------------------------------------------------------
insert into claim (id, case_id, seq, text, kind, created_by, updated_by)
select gen_random_uuid(), c.id, k, 'SYNTH-DOC claim ' || c.n || '/' || k, (array['PRIMARY', 'ALTERNATIVE'])[k], (select id from seed_actor), (select id from seed_actor)
from seed_case c cross join generate_series(1, 2) k;
insert into defense (id, case_id, seq, title, kind, argument, created_by, updated_by)
select gen_random_uuid(), c.id, 1, 'SYNTH-DOC defense ' || c.n, 'SUBSTANTIVE', 'SYNTH-DOC argument', (select id from seed_actor), (select id from seed_actor)
from seed_case c where c.n % 2 = 0;

insert into task (id, case_id, title, assignee_id, due_date, status, completed_at, status_before_late, created_by, updated_by)
select gen_random_uuid(), c.id, 'SYNTH-DOC task ' || c.n || '/' || k,
       (array['20000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000004'])[1 + (c.n + k) % 3]::uuid,
       date '2025-06-01' + (c.n % 200), s.status, case when s.status = 'COMPLETED' then now() end,
       case when s.status = 'LATE' then 'NEW' end, (select id from seed_actor), (select id from seed_actor)
from seed_case c cross join generate_series(1, 2) k
join (select row_number() over () - 1 as i, status from unnest(array['NEW', 'IN_PROGRESS', 'WAITING_DOCUMENTS', 'WAITING_OTHER', 'COMPLETED', 'LATE', 'CANCELLED']) status) s
  on s.i = (c.n * 2 + k) % 7;
create temporary table seed_task as select t.id, t.case_id, row_number() over (order by t.id) rn from task t;

insert into task_history (task_id, actor_id, action, field, new_value)
select t.id, (select id from seed_actor), 'CREATED', 'status', 'SYNTH-DOC' from seed_task t;

insert into case_grant (case_id, user_id, level, reason, granted_by, expires_at, revoked_at, revoked_by)
select c.id, '20000000-0000-4000-8000-000000000003', case when c.n % 2 = 0 then 'VIEW' else 'CONTRIBUTE' end, 'SYNTH-DOC grant ' || c.n,
       '20000000-0000-4000-8000-000000000001',
       case when c.n % 5 = 0 then now() - interval '1 day' end,
       case when c.n % 10 = 0 then now() end, case when c.n % 10 = 0 then '20000000-0000-4000-8000-000000000001'::uuid end
from seed_case c where c.n % 9 = 0 or c.n % 10 = 0;

-- --- archive: documents with versions (binary, incompressible, 1 KiB to ~1 MiB), links, OCR text -----------------------------------
create temporary table seed_doc as
select gen_random_uuid() as id, c.id as case_id, c.n, k, row_number() over (order by c.n, k) rn
from seed_case c cross join generate_series(1, 2) k;

-- one bytea per (document, version); the bytes are a deterministic function of the key, so a restore can be compared by hash.
create temporary table seed_file as
select d.id as document_id, v as version_no,
       convert_to('%PDF-1.7 SYNTH-DOC ' || d.rn || '/' || v || E'\n', 'UTF8')
       || (select decode(string_agg(md5(d.rn || ':' || v || ':' || i), ''), 'hex')
           from generate_series(1, 64 + (d.rn * 7919 + v * 104729) % 6000 * case when d.rn % 40 = 0 then 10 else 1 end) i) as content
from seed_doc d cross join generate_series(1, 1 + (d.rn % 3)) v;

insert into case_document (id, case_id, title, file_name, media_type, size_bytes, sha256, current_version_no, description, created_by, updated_by, category_id)
select d.id, d.case_id, 'SYNTH-DOC document ' || d.rn, 'synth-doc-' || d.rn || '.pdf', 'application/pdf', length(f.content),
       encode(sha256(f.content), 'hex'), f.version_no, 'SYNTH-DOC archived file ' || d.rn, (select id from seed_actor), (select id from seed_actor),
       (select id from lookup_value where list_code = 'DOCUMENT_CATEGORY' order by code limit 1 offset d.rn % 8)
from seed_doc d join seed_file f on f.document_id = d.id and f.version_no = (select max(version_no) from seed_file x where x.document_id = d.id);

insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, change_note, uploaded_by)
select case when f.version_no = 1 then f.document_id else gen_random_uuid() end, f.document_id, f.version_no,
       'synth-doc-' || d.rn || '-v' || f.version_no || '.pdf', 'application/pdf', length(f.content), encode(sha256(f.content), 'hex'),
       case when f.version_no > 1 then 'SYNTH-DOC revision ' || f.version_no end, (select id from seed_actor)
from seed_file f join seed_doc d on d.id = f.document_id;
insert into document_version_content (version_id, content)
select v.id, f.content from seed_file f join document_version v on v.document_id = f.document_id and v.version_no = f.version_no;
-- the head row's own bytes (the previous release's table, still written by the application)
insert into case_document_content (document_id, content)
select v.document_id, vc.content from document_version v join document_version_content vc on vc.version_id = v.id
join case_document d on d.id = v.document_id and d.current_version_no = v.version_no;

insert into document_text (version_id, document_id, status, method, engine, languages, page_count, text, processed_at)
select v.id, v.document_id, 'EXTRACTED', 'PDF_TEXT', 'SYNTH-DOC', 'ara+eng', 1 + v.version_no,
       'SYNTH-DOC نص مستخرج من الملف ' || d.rn || ' النسخة ' || v.version_no || ' text of document ' || d.rn, now()
from document_version v join seed_doc d on d.id = v.document_id;

-- links between documents and claims / defenses / hearings / judgments / tasks (all inside the same case: the trigger checks it)
insert into document_link (document_id, claim_id, note, created_by)
select d.id, (select cl.id from claim cl where cl.case_id = d.case_id order by cl.seq limit 1), 'SYNTH-DOC claim link', (select id from seed_actor)
from seed_doc d where d.rn % 2 = 1;
insert into document_link (document_id, defense_id, note, created_by)
select d.id, df.id, 'SYNTH-DOC defense link', (select id from seed_actor) from seed_doc d join defense df on df.case_id = d.case_id where d.k = 2;
insert into document_link (document_id, judgment_id, note, created_by)
select d.id, j.id, 'SYNTH-DOC judgment link', (select id from seed_actor) from seed_doc d join seed_judgment j on j.case_id = d.case_id where d.k = 1;
insert into document_link (document_id, hearing_id, note, created_by)
select d.id, h.id, 'SYNTH-DOC hearing link', (select id from seed_actor) from seed_doc d join seed_hearing h on h.case_id = d.case_id where d.rn % 4 = 0;
insert into document_link (document_id, task_id, note, created_by)
select d.id, (select t.id from task t where t.case_id = d.case_id order by t.id limit 1), 'SYNTH-DOC task link', (select id from seed_actor)
from seed_doc d where d.rn % 5 = 0;
update claim set source_document_id = (select d.id from case_document d where d.case_id = claim.case_id order by d.title limit 1) where seq = 1;
update defense set memo_document_id = (select d.id from case_document d where d.case_id = defense.case_id order by d.title desc limit 1);

-- --- enforcement files and actions (judgments of the cases), memos (legal documents) with their workflow events ------------
insert into enforcement_file (id, judgment_id, case_id, status, executing_party, amount_required, due_on, notes, created_by, updated_by)
select gen_random_uuid(), j.id, j.case_id, (array['NOT_STARTED', 'IN_PROGRESS', 'OBSTRUCTED'])[1 + j.rn % 3], 'SYNTH-DOC executing party',
       500 * (1 + j.rn % 40), date '2026-01-01' + (j.rn % 200)::int, 'SYNTH-DOC enforcement notes', (select id from seed_actor), (select id from seed_actor)
from seed_judgment j where j.rn % 2 = 0;
insert into enforcement_action (enforcement_id, seq, kind, action_date, description, result, document_id, created_by, updated_by)
select e.id, k, (array['PROCEDURE', 'CORRESPONDENCE'])[k], date '2026-02-01' + k, 'SYNTH-DOC enforcement action ' || k, 'SYNTH-DOC result',
       (select d.id from case_document d where d.case_id = e.case_id order by d.title limit 1 offset k - 1), (select id from seed_actor), (select id from seed_actor)
from enforcement_file e cross join generate_series(1, 2) k;

insert into legal_document (id, case_id, title, document_date, issuer, description, document_id, status, submitted_by, submitted_at,
                            approved_by, approved_at, created_by, updated_by)
select gen_random_uuid(), d.case_id, 'SYNTH-DOC memo ' || d.rn, date '2025-09-01' + (d.rn % 200)::int, 'SYNTH-DOC issuer', 'SYNTH-DOC memo',
       d.id, s.status,
       case when s.status in ('IN_REVIEW', 'APPROVED') then '20000000-0000-4000-8000-000000000003'::uuid end,
       case when s.status in ('IN_REVIEW', 'APPROVED') then now() end,
       case when s.status = 'APPROVED' then '20000000-0000-4000-8000-000000000001'::uuid end,
       case when s.status = 'APPROVED' then now() end, (select id from seed_actor), (select id from seed_actor)
from seed_doc d join (select row_number() over () - 1 as i, status from unnest(array['DRAFT', 'IN_REVIEW', 'APPROVED']) status) s on s.i = d.rn % 3
where d.k = 1 and d.rn % 2 = 1;
insert into legal_document_event (legal_document_id, from_status, to_status, note, created_by)
select id, null, 'DRAFT', 'SYNTH-DOC created', (select id from seed_actor) from legal_document;

-- --- notes, events, notifications, audit rows ----------------------------------------------------------------------------
insert into case_note (case_id, body, created_by, updated_by)
select c.id, 'SYNTH-DOC note ' || c.n, (select id from seed_actor), (select id from seed_actor) from seed_case c where c.n % 3 = 0;
insert into case_event (case_id, type, summary, created_by)
select c.id, 'SYNTH_DOC_EVENT', 'SYNTH-DOC event ' || c.n || '/' || k, (select id from seed_actor)
from seed_case c cross join generate_series(1, 3) k;
insert into notification (user_id, type, case_id, record_ref, dedupe_key)
select '20000000-0000-4000-8000-000000000003', 'SYNTH_DOC_NOTICE', c.id, 'SYNTH-DOC', 'synth-doc-' || c.n from seed_case c where c.n % 4 = 0;
insert into audit_event (actor_id, actor_username, action, target_type, target_id, outcome, request_id)
select '20000000-0000-4000-8000-000000000001', 'synth.manager', 'SYNTH_DOC_ACTION', 'CASE', c.id::text, 'SUCCESS', 'synth-doc-' || c.n || '-' || k
from seed_case c cross join generate_series(1, 10 * :scale) k;

