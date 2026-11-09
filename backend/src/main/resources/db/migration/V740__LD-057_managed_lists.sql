-- LD-057 (WP9, requirements sections 23, 25 and 29): the managed lists of settlements, directives and legal templates
-- (owner decision D10). Additive only. A value is edited, deactivated and added by the managers of its list, never
-- deleted, so a record keeps the value it was recorded with. origin says whether the requirements text names the value
-- (DOCUMENT) or a member proposed it (PROPOSED); only values the text names are seeded.

create table managed_list_value (
    id         uuid primary key default gen_random_uuid(),
    list_code  varchar(20)  not null constraint managed_list_value_list_known
        check (list_code in ('SETTLEMENT_KIND', 'DIRECTIVE_KIND', 'TEMPLATE_KIND')),
    code       varchar(40)  not null constraint managed_list_value_code_format check (code ~ '^[A-Z0-9_]{1,40}$'),
    label_ar   varchar(200) not null constraint managed_list_value_label_visible check (length(btrim(label_ar)) > 0),
    label_en   varchar(200),
    origin     varchar(10)  not null constraint managed_list_value_origin_known check (origin in ('DOCUMENT', 'PROPOSED')),
    active     boolean      not null default true,
    sort_order integer      not null default 0,
    version    bigint       not null default 0,
    created_at timestamptz  not null default now(),
    created_by uuid,
    updated_at timestamptz  not null default now(),
    updated_by uuid,
    constraint managed_list_value_code_unique unique (list_code, code)
);
create unique index managed_list_value_label_unique on managed_list_value (list_code, library_normalize(label_ar))
    where active;

-- Section 23 (line 557): "التسويات والتنازلات والصلح" — a settlement, a waiver and a conciliation.
insert into managed_list_value (list_code, code, label_ar, origin, sort_order) values
    ('SETTLEMENT_KIND', 'SETTLEMENT', 'تسوية', 'DOCUMENT', 1),
    ('SETTLEMENT_KIND', 'WAIVER', 'تنازل', 'DOCUMENT', 2),
    ('SETTLEMENT_KIND', 'CONCILIATION', 'صلح', 'DOCUMENT', 3);

-- Section 25 (line 574): assigning a member to follow a case, to prepare a memorandum, to attend a hearing, to review a
-- judgment, to address a department, to take enforcement steps. The text says "such as", so a manager may add more.
insert into managed_list_value (list_code, code, label_ar, origin, sort_order) values
    ('DIRECTIVE_KIND', 'FOLLOW_CASE', 'متابعة قضية', 'DOCUMENT', 1),
    ('DIRECTIVE_KIND', 'PREPARE_MEMO', 'إعداد مذكرة', 'DOCUMENT', 2),
    ('DIRECTIVE_KIND', 'ATTEND_HEARING', 'حضور جلسة', 'DOCUMENT', 3),
    ('DIRECTIVE_KIND', 'REVIEW_JUDGMENT', 'مراجعة حكم', 'DOCUMENT', 4),
    ('DIRECTIVE_KIND', 'ADDRESS_UNIT', 'مخاطبة إدارة', 'DOCUMENT', 5),
    ('DIRECTIVE_KIND', 'ENFORCEMENT_ACTION', 'اتخاذ إجراءات التنفيذ', 'DOCUMENT', 6);

-- Section 29 (lines 629-637): the kinds of ready template the library holds. "صحف الدعاوى والاستئناف والتمييز" names three.
insert into managed_list_value (list_code, code, label_ar, origin, sort_order) values
    ('TEMPLATE_KIND', 'DEFENSE_MEMO', 'مذكرة دفاع', 'DOCUMENT', 1),
    ('TEMPLATE_KIND', 'CLAIM_PLEADING', 'صحيفة دعوى', 'DOCUMENT', 2),
    ('TEMPLATE_KIND', 'APPEAL_PLEADING', 'صحيفة استئناف', 'DOCUMENT', 3),
    ('TEMPLATE_KIND', 'CASSATION_PLEADING', 'صحيفة تمييز', 'DOCUMENT', 4),
    ('TEMPLATE_KIND', 'DOCUMENT_FILING_REQUEST', 'طلب إيداع مستندات', 'DOCUMENT', 5),
    ('TEMPLATE_KIND', 'ADJOURNMENT_REQUEST', 'طلب تأجيل', 'DOCUMENT', 6),
    ('TEMPLATE_KIND', 'REPLY_MEMO', 'مذكرة تعقيب', 'DOCUMENT', 7),
    ('TEMPLATE_KIND', 'INFO_REQUEST_LETTER', 'كتاب طلب إفادة', 'DOCUMENT', 8),
    ('TEMPLATE_KIND', 'ENFORCEMENT_FOLLOW_UP_LETTER', 'كتاب متابعة تنفيذ حكم', 'DOCUMENT', 9),
    ('TEMPLATE_KIND', 'CASE_SUMMARY', 'ملخص قضية', 'DOCUMENT', 10),
    ('TEMPLATE_KIND', 'LEGAL_POSITION_REPORT', 'تقرير الموقف القانوني', 'DOCUMENT', 11);
