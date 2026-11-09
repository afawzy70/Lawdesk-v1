-- LD-053 (WP3, docs/design/LD-DOC-03 and LD-DOC-05 section 2; requirements section 7): the hearing record gets the
-- fields of the requirements document. Additive only: every new column is nullable or has a neutral default, nothing
-- is dropped or renamed, no existing hearing is rewritten except that it is classified as a court hearing (the
-- conservative reading of `purpose`, correctable per hearing).

alter table hearing
    -- الدائرة, on the hearing (the case keeps its own).
    add column chamber               varchar(200),
    -- نوع الجلسة: محكمة، خبراء، تحقيق، أو إجراء آخر.
    add column kind                  varchar(14) not null default 'COURT',
    add column judge_or_expert       varchar(200),
    -- العضو القانوني المسؤول: a user (an actor column in the sense of CONTRACTS section 2, so no foreign key).
    add column responsible_member_id uuid,
    add column attendees             varchar(2000),
    add column proceedings_summary   varchar(4000),
    add column opponents_requests    varchar(4000),
    add column required_action       varchar(2000),
    add column adjournment_reason    varchar(2000),
    -- No next date: the court set none. Recorded with its reason; the hearing stays on the follow-up list until a next
    -- hearing is scheduled. A date is never guessed.
    add column no_next_date          boolean not null default false,
    add column no_next_date_reason   varchar(2000),
    add column follow_up_flag        boolean not null default false,
    add constraint hearing_kind_known check (kind in ('COURT', 'EXPERT', 'INVESTIGATION', 'OTHER')),
    add constraint hearing_text_visible check (
        (chamber is null or length(btrim(chamber)) > 0) and (judge_or_expert is null or length(btrim(judge_or_expert)) > 0)
        and (attendees is null or length(btrim(attendees)) > 0)
        and (proceedings_summary is null or length(btrim(proceedings_summary)) > 0)
        and (opponents_requests is null or length(btrim(opponents_requests)) > 0)
        and (required_action is null or length(btrim(required_action)) > 0)
        and (adjournment_reason is null or length(btrim(adjournment_reason)) > 0)),
    -- "No next date" belongs to an adjourned hearing and always carries its reason; the follow-up flag only to such a hearing.
    add constraint hearing_no_next_date_adjourned check (
        not no_next_date or (status = 'ADJOURNED' and no_next_date_reason is not null
                             and length(btrim(no_next_date_reason)) > 0)),
    add constraint hearing_follow_up_needs_no_next_date check (not follow_up_flag or no_next_date);

create index hearing_follow_up on hearing (case_id) where follow_up_flag;
