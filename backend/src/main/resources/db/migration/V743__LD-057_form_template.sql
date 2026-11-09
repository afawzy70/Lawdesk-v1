-- LD-057 (WP9, requirements section 29): the library of ready legal templates. Additive only. A template has versions
-- (all kept); a version is Arabic text with {{placeholders}} that a draft fills from the case file. A version is DRAFT,
-- then REVIEWED, then APPROVED, and once reviewed its text is never edited: a change is a new version.
create table form_template (
    id          uuid primary key default gen_random_uuid(),
    -- A code of the managed list TEMPLATE_KIND.
    kind        varchar(40)   not null,
    title       varchar(200)  not null constraint form_template_title_visible check (length(btrim(title)) > 0),
    description varchar(1000),
    -- DOCUMENT: named by the requirements text; PROPOSED: added by a member (owner decision D10). No template is seeded.
    origin      varchar(10)   not null default 'PROPOSED' constraint form_template_origin_known check (origin in ('DOCUMENT', 'PROPOSED')),
    active      boolean       not null default true,
    version     bigint        not null default 0,
    created_at  timestamptz   not null default now(),
    created_by  uuid          not null,
    updated_at  timestamptz   not null default now(),
    updated_by  uuid          not null
);
create index form_template_kind on form_template (kind);

create table form_template_version (
    id           uuid primary key default gen_random_uuid(),
    template_id  uuid         not null references form_template (id),
    version_no   integer      not null constraint form_template_version_no_positive check (version_no > 0),
    body         text         not null constraint form_template_body_visible check (length(btrim(body)) > 0),
    change_note  varchar(1000),
    status       varchar(10)  not null default 'DRAFT' constraint form_template_version_status_known
        check (status in ('DRAFT', 'REVIEWED', 'APPROVED')),
    reviewed_by  uuid,
    reviewed_at  timestamptz,
    approved_by  uuid,
    approved_at  timestamptz,
    version      bigint       not null default 0,
    created_at   timestamptz  not null default now(),
    created_by   uuid         not null,
    updated_at   timestamptz  not null default now(),
    updated_by   uuid         not null,
    constraint form_template_version_unique unique (template_id, version_no),
    constraint form_template_review_complete check ((reviewed_at is null) = (reviewed_by is null)),
    constraint form_template_approval_complete check ((approved_at is null) = (approved_by is null)),
    constraint form_template_status_matches check (
        (status = 'DRAFT' and reviewed_at is null and approved_at is null)
        or (status = 'REVIEWED' and reviewed_at is not null and approved_at is null)
        or (status = 'APPROVED' and reviewed_at is not null and approved_at is not null))
);
create index form_template_version_template on form_template_version (template_id, version_no desc);
