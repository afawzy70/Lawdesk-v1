-- LD-029 legal notes of a case (docs/delivery/PHASE-5-ACCEPTANCE.md): internal to the office, written with NOTE_MANAGE
-- and read with CASE_VIEW. Their text never enters the case timeline. Actor columns hold the user id without a foreign
-- key (architecture/CONTRACTS.md §2); created_by is the author, who alone changes or removes the note (ASSUMPTION).
create table case_note (
    id         uuid primary key default gen_random_uuid(),
    case_id    uuid          not null references legal_case (id),
    body       varchar(4000) not null constraint case_note_body_visible check (length(btrim(body)) > 0),
    version    bigint        not null default 0,
    created_at timestamptz   not null default now(),
    created_by uuid          not null,
    updated_at timestamptz   not null default now(),
    updated_by uuid          not null
);

create index case_note_case on case_note (case_id, created_at desc);
