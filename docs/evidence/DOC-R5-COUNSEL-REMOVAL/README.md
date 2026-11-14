# LD-DOC-r5 — removal of «المحامون والوكلاء» (lawyers and agents)

Owner decision (2026-11-14): the section, its data and its API are removed permanently. The owner confirmed the removal
after being told it is irreversible on UAT and touches rules that look at party links.

## What was removed

| Layer | Removed |
|---|---|
| Database | table `case_party_counsel` (migration `V880__LD-DOC-r5_drop_party_counsel.sql`; created by V602). The migration logs the row counts with `RAISE NOTICE` before the drop |
| Backend | `CounselService`; `GET /api/v1/cases/{id}/parties/counsel`, `POST /api/v1/cases/{id}/parties/{partyId}/counsel`, `DELETE /api/v1/cases/{id}/parties/counsel/{counselId}` in `PartyController`; `PartyRepository.hasCounsel`; the 409 refusal in `PartyService.unlink` |
| Frontend | `CaseCounselSection`, `Counsel` / `listCounsel` / `addCounsel` / `removeCounsel` in `cases/api.ts`, the `counsel.*` messages, the section on the case page |
| Tests | the counsel API test, the counsel component tests, the walk entries and fixtures in `CaseBoundEndpointsAccessTest`, the clean-up lines in the test data helpers, the unlink-with-counsel conflict test (replaced, see below) |
| Journey | the counsel steps of `tools/journeys/wp1c_parties_journey.py` (replaced by "the endpoints are gone" checks) |

Kept on purpose: the timeline event labels `COUNSEL_ADDED` / `COUNSEL_REMOVED`, so old timeline entries of existing cases
still read correctly. Rows already written to `case_timeline` and `audit_event` (`CASE_COUNSEL_REMOVE`) are history and are not touched.

## Dependency inventory (checked before the drop)

| Place | Depended on the table? | Result |
|---|---|---|
| `CaseAccessPolicy`, the visibility predicate, scopes, grants, confidentiality | No | unchanged. `CaseVisibilityPredicateEquivalenceTest`, `CaseVisibilityApiTest`, `CaseBoundEndpointsAccessTest` pass |
| Party unlink (`PartyService.unlink`) | Yes: refused with 409 when any counsel row, even a marked one, referenced the link | the refusal is gone, so the caveat "a party whose only counsel rows are removed cannot be unlinked" no longer exists. `PartyDirectoryAndDefectsTest.aSecondaryPartyCanAlwaysBeUnlinked…` |
| Search documents and the V845 statement-level triggers (`search_case_doc`) | No (they read case, proceeding, org involvement columns) | unchanged; `StoredSearchDocumentsTest`, `UnifiedSearchApiTest` pass |
| Audit | Writes only (`CASE_COUNSEL_REMOVE` action) | no reader; no code remains that writes it |
| Reports, case brief, assistant retriever, followup | No reference found (`grep -ri counsel` over `backend/src/main`) | none |
| Journeys under `tools/journeys` | `wp1c_parties_journey.py` only | updated; compiled, **not run** against UAT (no deploy yet) |
| Evidence and register documents | History files under `docs/evidence/DOC-RESTORE`, `DOC-RETEST`, `DOC-WP1`, `DOC-R3-*` and the requirement register mention the feature | left as history; the register wording is for the owner of the register to update |
| New test in this change | `CaseRequestsApiTest.whoSeesACaseIsTheSameAfterTheLawyerAndAgentTableWasDropped` | no function or view source mentions the dropped table; an in-scope user reads and lists the case, an out-of-scope user gets 404 and an empty list, a member keeps access |

## Row counts

The real counts are known only when the migration runs on UAT. They appear in the backend start-up log as
`LD-DOC-r5: dropping case_party_counsel with N rows (M not marked removed)`. The number must be copied here after the deploy.

Not run yet on UAT. Pending Codex coordination of the deploy window.
