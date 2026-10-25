-- LD-040 WHAT-IF ONLY: candidate indexes from docs/performance/PERFORMANCE-BASELINE.md §4.3, applied to the throwaway
-- local database to measure their effect. NOT a migration: each would go through its owning workstream's Flyway range
-- (architecture/CONTRACTS.md §2, §8.3) with owner review. pg_trgm needs CREATE EXTENSION rights the application role
-- does not have on UAT (deploy/postgres/app-role.sql), so it is an owner/DBA decision.
create extension if not exists pg_trgm;

-- P-01 default case list order (CaseRepository.orderBy: created_at desc, id).
create index whatif_legal_case_created on legal_case (created_at desc, id);
-- P-02 substring search on reference, title and party name (ilike '%q%').
create index whatif_legal_case_title_trgm on legal_case using gin (title gin_trgm_ops);
create index whatif_legal_case_reference_trgm on legal_case using gin (reference_no gin_trgm_ops);
create index whatif_party_name_trgm on party using gin (name gin_trgm_ops);
-- P-05 judgment report and dashboard period filter.
create index whatif_judgment_date on judgment (judgment_date);
-- P-06 hearings awaiting a result (status = 'SCHEDULED' and hearing_date < today).
create index whatif_hearing_awaiting on hearing (hearing_date) where status = 'SCHEDULED';
analyze;
