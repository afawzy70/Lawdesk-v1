-- LD-DOC-r5: «الطلبات» of a case, as text items per side (plaintiff / defendant), at case level.
-- Additive. A replaced item is marked removed, never erased, so the record of what was requested stays.
create table case_request_item (
    id         uuid primary key default gen_random_uuid(),
    case_id    uuid          not null references legal_case (id),
    side       varchar(9)    not null constraint case_request_item_side_known check (side in ('PLAINTIFF', 'DEFENDANT')),
    sort_no    integer       not null constraint case_request_item_sort_positive check (sort_no > 0),
    body       varchar(2000) not null constraint case_request_item_body_visible check (length(btrim(body)) > 0),
    created_at timestamptz   not null default now(),
    created_by uuid          not null,
    removed_at timestamptz,
    removed_by uuid,
    constraint case_request_item_removal_complete check ((removed_at is null) = (removed_by is null))
);
create index case_request_item_case on case_request_item (case_id, side, sort_no) where removed_at is null;
