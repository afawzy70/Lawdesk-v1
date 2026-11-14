# LD-DOC-r5 — removal of «المحامون والوكلاء» (lawyers and agents)

Owner decision (2026-11-14): the section and its API are removed and the data is to be deleted permanently. The owner's later
instruction is that NO existing data is destroyed until the real row count has been reported, a verified backup and restore
procedure exists and the owner approves the irreversible deletion explicitly. Therefore **migration V880 only renames the table**
(`case_party_counsel` → `case_party_counsel_retired`); the irreversible drop is a separate later migration that is NOT in this tree.

## What was removed

| Layer | Removed |
|---|---|
| Database | table `case_party_counsel` is renamed to `case_party_counsel_retired` by `V880__LD-DOC-r5_retire_party_counsel.sql` (table created by V602); every row is kept. The migration logs the row counts with `RAISE NOTICE` |
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

## Row counts on UAT (not obtained by the developer)

The developer has no database access to UAT, so the real number is **not known yet**. The owner/operator reads it with the read-only
command in `docs/operations/COUNSEL-RETIREMENT.md` (section 1) and the number is copied here before any deletion is requested:

| Where | Total | Not marked removed | Read on | By |
|---|---|---|---|---|
| UAT | _pending_ | _pending_ | | |

## Drill on a throw-away cluster (SYNTHETIC data) — `tools/restore-drill/counsel_retire_drill.py`

Result file: `drill-result.json` (all 12 checks pass): 600 synthetic counsel rows (480 not removed) → backup `pg_dump -Fc` verified by
restoring into a side database (`--exit-on-error`, identical row count and digest) → application boot applies V880 → table renamed,
600/480 rows and digest unchanged, old name gone, cases/parties/links/audit counts unchanged → second boot applies no migration →
recovery A (rename back) and recovery B (restore from the backup) both give the original rows. This is a drill on synthetic data,
not a restore of the real UAT database.

## Irreversible deletion

Not done and not scheduled. It needs the owner's explicit approval after the count and the backup check above
(`docs/operations/COUNSEL-RETIREMENT.md`, section 4).
