-- LD-055 (WP5, sections 12 and 13; owner decision D10): the managed list of document categories. The first eight come from the
-- list in section 12 of the requirements text (origin DOCUMENT); OTHER is the implementers' proposal and waits for approval.
insert into lookup_value (list_code, code, label_ar, label_en, origin, approved_at, approved_by, created_by, updated_by)
select v.list_code, v.code, v.label_ar, v.label_en, v.origin,
       case when v.origin = 'DOCUMENT' then now() end,
       case when v.origin = 'DOCUMENT' then '00000000-0000-4000-8000-000000000000'::uuid end,
       '00000000-0000-4000-8000-000000000000', '00000000-0000-4000-8000-000000000000'
from (values
    ('DOCUMENT_CATEGORY', 'STATEMENT_OF_CLAIM', 'صحيفة دعوى', 'Statement of claim', 'DOCUMENT'),
    ('DOCUMENT_CATEGORY', 'DEFENSE_MEMO', 'مذكرة دفاع', 'Defence memo', 'DOCUMENT'),
    ('DOCUMENT_CATEGORY', 'OPPONENT_MEMO', 'مذكرة الخصم', 'Opponent memo', 'DOCUMENT'),
    ('DOCUMENT_CATEGORY', 'APPEAL_PLEADING', 'صحيفة استئناف أو تمييز', 'Appeal or cassation pleading', 'DOCUMENT'),
    ('DOCUMENT_CATEGORY', 'LETTER_TO_UNIT', 'كتاب متبادل مع إدارة', 'Letter exchanged with a unit', 'DOCUMENT'),
    ('DOCUMENT_CATEGORY', 'COURT_OR_GOVERNMENT_CORRESPONDENCE', 'مراسلة مع محكمة أو جهة حكومية', 'Correspondence with a court or government body', 'DOCUMENT'),
    ('DOCUMENT_CATEGORY', 'EXPERT_REPORT', 'تقرير خبير', 'Expert report', 'DOCUMENT'),
    ('DOCUMENT_CATEGORY', 'JUDGMENT_OR_MINUTES', 'حكم أو محضر جلسة', 'Judgment or hearing minutes', 'DOCUMENT'),
    ('DOCUMENT_CATEGORY', 'OTHER', 'غير ذلك', 'Other', 'PROPOSED')) as v (list_code, code, label_ar, label_en, origin);
insert into lookup_value_history (value_id, rev, label_ar, label_en, origin, active, approved_at, change, changed_by)
select id, 1, label_ar, label_en, origin, active, approved_at, 'CREATED', created_by
from lookup_value where list_code = 'DOCUMENT_CATEGORY';
