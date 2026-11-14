-- LD-DOC-r5 (owner decision, case view change): the «المحامون والوكلاء» section is removed together with its data
-- and API. The data is PERMANENTLY removed by owner decision; it is not archived here. The row counts at the time of
-- the drop are written to the migration log (NOTICE) and to docs/evidence/DOC-R5-COUNSEL-REMOVAL/README.md.
-- Nothing else referenced the table: CaseAccessPolicy and the visibility rules never read it, the search documents
-- (V845 triggers) do not use it, and its only foreign keys were outgoing (case_party, party, legal_case).
do $$
declare
    total bigint;
    kept  bigint;
begin
    select count(*), count(*) filter (where removed_at is null) into total, kept from case_party_counsel;
    raise notice 'LD-DOC-r5: dropping case_party_counsel with % rows (% not marked removed)', total, kept;
end $$;
drop table case_party_counsel;
