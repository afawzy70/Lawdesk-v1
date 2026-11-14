-- LD-DOC-r5: the «المحامون والوكلاء» section, its API and its screens are retired. THIS MIGRATION DESTROYS NOTHING:
-- the table is only renamed, so the rows stay recoverable. The owner has to approve the irreversible deletion
-- explicitly after the real row count and a verified backup have been reported (see
-- docs/evidence/DOC-R5-COUNSEL-REMOVAL/README.md); the drop is a separate, later migration that is not in this tree.
-- Nothing else read the table: CaseAccessPolicy and the visibility rules never use it, the search documents (V845
-- triggers) do not, and its foreign keys are outgoing only (case_party, party, legal_case).
do $$
declare
    total bigint;
    kept  bigint;
begin
    select count(*), count(*) filter (where removed_at is null) into total, kept from case_party_counsel;
    raise notice 'LD-DOC-r5: retiring case_party_counsel (rows kept, renamed): % rows (% not marked removed)', total, kept;
end $$;
alter table case_party_counsel rename to case_party_counsel_retired;
