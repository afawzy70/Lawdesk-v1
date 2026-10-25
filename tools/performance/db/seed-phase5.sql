-- LD-040 synthetic Phase 5 volumes, for the hearing, task and document tables of the open Phase 5 pull requests
-- (V400-V403; not on develop when this was written). Run after seed-develop.sql. THROWAWAY LOCAL DATABASE ONLY.
-- Volumes: 100 000 hearings (two per proceeding), 30 000 tasks, 1 000 PDF documents of 64 KiB.
select setseed(0.43);

insert into hearing (case_id, proceeding_id, hearing_date, hearing_time, purpose, status, decision,
                     result_recorded_at, result_recorded_by, created_by, updated_by)
select case_id, id, d, case when k = 0 then time '10:00' end,
       (array['FIRST_SESSION', 'PLEADING', 'EVIDENCE', 'EXPERT_REPORT', 'JUDGMENT', 'OTHER'])[1 + (k * 3 + rn) % 6],
       st, case when st in ('HELD', 'ADJOURNED') then 'SYN decision' end,
       case when st <> 'SCHEDULED' then now() end,
       case when st <> 'SCHEDULED' then '00000000-0000-0000-0000-000000000000'::uuid end,
       '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'
from (select p.case_id, p.id, k, row_number() over (order by p.id) rn,
             -- Spread over 2015-2027 so the calendar windows around "today" hold a realistic share.
             date '2015-03-01' + ((hashtext(p.id::text || k) & 2147483647) % 4700) as d
      from proceeding p cross join generate_series(0, 1) k) s
cross join lateral (select case when d >= current_date then 'SCHEDULED'
                                when random() < 0.05 then 'SCHEDULED'
                                else (array['HELD', 'ADJOURNED', 'CANCELLED'])[1 + (random() * 2)::int] end as st) x
on conflict do nothing;

insert into task (case_id, title, assignee_id, due_date, priority, status, completed_at, created_by, updated_by)
select c.id, 'SYN task ' || g, u.id, current_date + ((g * 37) % 360) - 180,
       (array['LOW', 'NORMAL', 'HIGH', 'URGENT'])[1 + g % 4], st,
       case when st = 'DONE' then now() end, '00000000-0000-0000-0000-000000000000',
       '00000000-0000-0000-0000-000000000000'
from generate_series(1, 30000) g
join (select id, row_number() over (order by reference_no) rn from legal_case) c on c.rn = 1 + g % 10000
join (select id, row_number() over (order by username) rn from app_user where username like 'syn.%') u
  on u.rn = 1 + g % 50
cross join lateral (select (array['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'])[1 + (g * 13) % 4] as st) x;

create temporary table syn_doc as
select c.id as case_id, g,
       -- A synthetic "PDF": the magic bytes, then 64 KiB of random filler (incompressible, like real PDFs).
       convert_to('%PDF-1.7 SYN', 'UTF8')
           || (select decode(string_agg(md5(random()::text || g || i), ''), 'hex') from generate_series(1, 4096) i)
           as content
from generate_series(1, 1000) g
join (select id, row_number() over (order by reference_no) rn from legal_case) c on c.rn = g * 10;

insert into case_document_quota (case_id) select case_id from syn_doc on conflict do nothing;

with docs as (
    insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
    select case_id, 'SYN document ' || g, 'syn-' || g || '.pdf', length(content), encode(sha256(content), 'hex'),
           '00000000-0000-0000-0000-000000000000'
    from syn_doc
    returning id, case_id)
insert into case_document_content (document_id, content)
select d.id, s.content from docs d join syn_doc s on s.case_id = d.case_id;

drop table syn_doc;
analyze;
