-- LD-DOC-r5 (stream A): one catalogue of the dropdown lists, managed by the administrator. Additive; no row of any
-- record table changes. The values below are the ones the screens already offer, with their CURRENT codes, so every
-- stored code stays valid. `system` marks a value that system logic depends on: its code never changes, it is never
-- deactivated, only its labels and order are edited.

alter table lookup_value
    add column sort_order integer not null default 0,
    add column system     boolean not null default false;

-- ADMIN only. LOOKUP_MANAGE (also held by the legal manager) keeps managing the three original lists.
insert into role_permission (role_id, permission)
select r.id, 'LIST_ADMIN' from app_role r where r.code = 'ADMIN'
on conflict do nothing;

-- Case importance becomes an administrator-managed list: new levels can be added, so the fixed CHECK is replaced by a
-- check against the catalogue in the case service (a deactivated level stays valid for the cases that already carry it).
alter table legal_case drop constraint legal_case_importance_known;
alter table legal_case add constraint legal_case_importance_format
    check (importance is null or importance ~ '^[A-Z][A-Z0-9_]*$');

insert into lookup_value (list_code, code, label_ar, label_en, origin, approved_by, approved_at, sort_order, system,
                          created_by, updated_by)
select v.list_code, v.code, v.label_ar, v.label_en, 'DOCUMENT', '00000000-0000-4000-8000-000000000000', now(), v.sort,
       v.sys, '00000000-0000-4000-8000-000000000000', '00000000-0000-4000-8000-000000000000'
from (values
    ('CASE_IMPORTANCE', 'LOW', 'منخفضة', 'Low', 1, false),
    ('CASE_IMPORTANCE', 'NORMAL', 'عادية', 'Normal', 2, false),
    ('CASE_IMPORTANCE', 'HIGH', 'مرتفعة', 'High', 3, false),
    ('CASE_IMPORTANCE', 'CRITICAL', 'حرجة', 'Critical', 4, false),
    ('CASE_TYPE', 'CIVIL', 'مدني', 'Civil', 1, true),
    ('CASE_TYPE', 'CRIMINAL', 'جنائي', 'Criminal', 2, true),
    ('CASE_TYPE', 'COMMERCIAL', 'تجاري', 'Commercial', 3, true),
    ('CASE_TYPE', 'LABOR', 'عمالي', 'Labour', 4, true),
    ('CASE_TYPE', 'FAMILY', 'أحوال شخصية', 'Family (personal status)', 5, true),
    ('CASE_TYPE', 'ADMINISTRATIVE', 'إداري', 'Administrative', 6, true),
    ('CASE_TYPE', 'CONSTITUTIONAL', 'دستوري', 'Constitutional', 7, true),
    ('CASE_TYPE', 'OTHER', 'أخرى', 'Other', 8, true),
    ('CASE_STATUS', 'OPEN', 'مفتوحة', 'Open', 1, true),
    ('CASE_STATUS', 'ACTIVE', 'متداولة', 'Active', 2, true),
    ('CASE_STATUS', 'SUSPENDED', 'موقوفة', 'Suspended', 3, true),
    ('CASE_STATUS', 'CLOSED', 'منتهية', 'Closed', 4, true),
    ('CASE_STATUS', 'ARCHIVED', 'مؤرشفة', 'Archived', 5, true),
    ('AUTHORITY_POSITION', 'PLAINTIFF', 'مدعي', 'Plaintiff', 1, true),
    ('AUTHORITY_POSITION', 'DEFENDANT', 'مدعى عليه', 'Defendant', 2, true),
    ('COURT_LEVEL', 'FIRST_INSTANCE', 'أول درجة', 'First instance', 1, true),
    ('COURT_LEVEL', 'APPEAL', 'الاستئناف', 'Appeal', 2, true),
    ('COURT_LEVEL', 'CASSATION', 'النقض', 'Cassation', 3, true),
    ('COURT_LEVEL', 'CONSTITUTIONAL', 'المحكمة الدستورية', 'Constitutional court', 4, true),
    ('COURT_LEVEL', 'ENFORCEMENT', 'التنفيذ', 'Enforcement', 5, true),
    ('COURT_LEVEL', 'GRIEVANCE', 'جهة التظلم', 'Grievance body', 6, true),
    ('HEARING_PURPOSE', 'FIRST_SESSION', 'أول جلسة', 'First session', 1, true),
    ('HEARING_PURPOSE', 'PLEADING', 'مرافعة', 'Pleading', 2, true),
    ('HEARING_PURPOSE', 'EVIDENCE', 'إثبات وسماع شهود', 'Evidence and witnesses', 3, true),
    ('HEARING_PURPOSE', 'EXPERT_REPORT', 'مناقشة تقرير الخبير', 'Expert report', 4, true),
    ('HEARING_PURPOSE', 'JUDGMENT', 'النطق بالحكم', 'Judgment', 5, true),
    ('HEARING_PURPOSE', 'OTHER', 'أخرى', 'Other', 6, true),
    ('HEARING_STATUS', 'SCHEDULED', 'محددة', 'Scheduled', 1, true),
    ('HEARING_STATUS', 'HELD', 'انعقدت', 'Held', 2, true),
    ('HEARING_STATUS', 'ADJOURNED', 'تأجلت', 'Adjourned', 3, true),
    ('HEARING_STATUS', 'CANCELLED', 'ألغيت', 'Cancelled', 4, true)
) as v (list_code, code, label_ar, label_en, sort, sys);

insert into lookup_value_history (value_id, rev, label_ar, label_en, origin, active, approved_at, change, changed_by)
select id, 1, label_ar, label_en, origin, active, approved_at, 'CREATED', created_by
from lookup_value lv where not exists (select 1 from lookup_value_history h where h.value_id = lv.id);

-- The three original lists get an order that matches the one they were shown in.
update lookup_value set sort_order = sub.n
from (select id, row_number() over (partition by list_code order by created_at, code) n from lookup_value
      where list_code in ('JUDGMENT_TYPE', 'JUDGMENT_NATURE', 'DOCUMENT_CATEGORY')) sub where lookup_value.id = sub.id;
