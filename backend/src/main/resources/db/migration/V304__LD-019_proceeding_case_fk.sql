-- LD-019: every proceeding belongs to an existing case. legal_case (Workstream B, V200) is on develop, so the foreign
-- key promised in V301 is added now (architecture/CONTRACTS.md §2, §5). Cases are never deleted; the default
-- "no action" keeps a case from being removed under its proceedings. If a row without its case existed, this migration
-- fails and the deployment rolls back instead of hiding it.
alter table proceeding
    add constraint proceeding_case_fk foreign key (case_id) references legal_case (id);
-- proceeding_case (V301) already indexes case_id for the key.
