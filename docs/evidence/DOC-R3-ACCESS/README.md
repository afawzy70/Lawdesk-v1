# DOC-R3-ACCESS: the case access walk, completed

Tests: `backend/src/test/java/com/lawdesk/security/CaseBoundEndpointsAccessTest.java` (3 tests) and
`CaseBoundSurfacesAccessTest.java` (7 tests). Run in `backend` with
`./mvnw test -Dtest=CaseBoundEndpointsAccessTest,CaseBoundSurfacesAccessTest`. No application code changed: no leak that needed
an application fix was found (see "Findings and honest limits").

## 1. The ten BODY_NOT_BUILT handlers

The walk used to classify ten handlers as "body not built": their generated JSON failed validation, so the outsider got 400
before the access check and the walk accepted that as protection. The category is gone. Each handler now has a hand-built
legitimate body (`BUILT_BODIES`), and the test asserts, for each:

- the in-scope user (scope ALL, every permission) gets a 2xx with that body (so the body is legitimate, not merely "not 404");
- the out-of-scope user holding every permission gets 404/403, and a 400 anywhere in the walk fails the test;
- the same holds with the version field wrong by 1000 (`aWrongVersionNeverTellsAnOutsiderThatTheRecordExists`): never 409/422.

The ten: `AccessController#confidentiality` (PUT /cases/{caseId}/confidentiality), `AccessController#grant` (POST
/cases/{caseId}/grants), `CaseController#changeStatus` (POST /cases/{id}/status), `CaseController#update` (PUT /cases/{id}),
`CasePrecedentController#link` (POST /cases/{caseId}/precedents), `DocumentLinkController#add` (POST /documents/{id}/links),
`InfoRequestController#replyWithDocument` (POST /info-requests/{id}/replies, multipart), `PartyController#addCounsel` (POST
/cases/{id}/parties/{partyId}/counsel), `PartyController#link` (POST /cases/{id}/parties), `ProceedingController#create`
(POST /cases/{caseId}/proceedings).

## 2. The walk itself was thinner than it looked (repaired)

The seed swallowed insert errors. A court code in lower case (`court_code_format`), a case-link kind, a special-decision effect
and a duplicate e-mail made the inserts fail silently, so proceedings, judgments, hearings, enforcement files and actions,
special decisions, case links and the member were never seeded, and every handler that needs one of them was never called.
Also never resolved: grants/{grantId}, involvements, counsel removal, expert referrals, risk remediations. All are now seeded
and the test fails if any table cannot be seeded (`unseeded` must be empty) or any handler cannot be resolved (`unresolved`
must be empty; it used to be `< 1000`). Three handlers that became reachable needed bodies too
(`ProceedingController#update`, `ResultController#recordClaimResults`, `#recordDefensePositions`); they are in `BUILT_BODIES`
(13 in all) and held to the same 2xx-for-insider rule. Hand-built handlers run first, so a DELETE or withdrawal elsewhere in
the walk cannot remove the records they need. About 157 handler/method pairs are checked per walk.

## 3. Existence is not disclosed

`anOutsiderCannotTellARecordThatExistsFromOneThatDoesNot`: for every case-bound handler, the outsider calls it once with a
record that exists in a case they cannot see and once with random ids; both must be 404/403 and the status, content type and
body (ids normalised) must be identical. A wrong version never produces 409 before the access check (section 1).
Limit: there is no `If-Match` header in this API; the optimistic-lock version travels in the body (PUT/POST) or the
`?version=` query (DELETE), and that is what is varied.

## 4. Other surfaces (`CaseBoundSurfacesAccessTest`)

One secret case carries the marker `ZQXSECRETWORD` in its title, subject, hearing, judgment, note, task, party, an uploaded
document (real stored bytes), the recognised text of that document, and an audit event. The attacker holds every permission
but no membership or scope. The owner is the control: where a surface should show the case, the owner must see the marker, so
an empty answer cannot be an empty fixture.

| Test | Proves |
|---|---|
| documentFileAndTextSurfacesAnswerAnOutsiderLikeAMissingRecord | document get, content (file download), versions, version content, recognised text, links, case document lists, withdrawn list, document links, and re-reading the text: owner 200, outsider 404/403 with the same shape as for a random id |
| thereIsNoPreviewThumbnailOrLegacyRouteOutsideTheWalkedFamilies | every mapped route is under `/api/v1` (plus `/error`); none is a preview, thumbnail, OCR, download or render route except the listed content routes; a new one fails until it is proven here. There are no legacy (non-v1) routes and no thumbnail or preview endpoint at this SHA. |
| searchesAndListsNeverShowTheSecretCaseToAnOutsider | unified search (all and per type, with and without `caseId`), document text search, document list, deletion log, JSON reports (cases, judgments, hearings, workload, experts, dashboard, families, indicators): the outsider's answer carries no marker, case reference or case id; the owner sees the marker in search, document search, document list and the case inventory |
| exportsInEveryFormatNeverContainTheSecretCaseForAnOutsider | CSV, Excel (every XML part of the zip read) and PDF (text extracted) of the five report exports, plus CSV of every family and indicator export: nothing of the case; the owner's cases export holds the marker in all three formats; a 400 fails |
| auditDetailsOfTheSecretCaseAreMaskedForAnOutsider | audit queries return the event with `masked: true` and no old/new values under several filters; the owner sees the details |
| theAssistantRefusesAnOutsiderEveryWayToReachTheSecretCase | test provider only, nothing enabled. Ask for the case (question, summary, memo, precedents), ask about its document (analysis, comparison, no case id), reuse the owner's session id, question without a case (library): 404/403 and no marker; case and document answers equal those for random ids; the owner's drafts are 404 to the outsider for read and review; the owner's query log is unchanged; the owner's draft stays DRAFT |
| thePartyDirectoryNamesNoCaseToAnOutsider | see below |

## Findings and honest limits

- (Superseded by "Fixed in this PR" below.) No leak that needed an application fix was found. The tests prove what they enumerate; they are not a proof for routes added
  later (the route inventory test and the unresolved/unseeded assertions are the guard).
- **Party directory (decision for the owner).** `GET /api/v1/parties` and `/parties/{id}` are open to any CASE_VIEW holder. A party
  that is on one restricted case still appears there by name (the unified search hides it, the directory does not). The case,
  reference and title are not reachable: `/parties/{id}/cases` is filtered. This is the documented "party is shared across cases"
  design, so it was pinned by a test rather than changed; filtering the directory by case visibility would break linking an
  existing party to a new case.
- Audit events about a case stay listed for everyone with AUDIT_VIEW with actor, time, action and the case id; only the details are
  masked (LD-DOC-04 section 5). The case id is therefore visible to an auditor outside the case's scope.
- `GET /assistant/queries` returns the caller's own rows even when filtered by a case the caller cannot see (never another user's).
- In-scope defects seen while building bodies (not access issues, not changed here): unlinking a party that has counsel answers 500
  (foreign key `case_party_counsel_on_case`); `GET /documents/{id}/text` and `POST .../text/reprocess` answer 500 for a document with
  no stored version.
- Excel and PDF of families and indicators were not run (CSV only): they share the CSV row source, and the formats are proven on the
  case exports.
- The seed inserts rows through SQL; the uploaded PDF is a minimal valid header, not a rendered file.

## Fixed in this PR (LD-DOC-R3 follow-up)

The findings pinned above as current behaviour are now fixed in the application; the pinning tests assert the fixed behaviour.
Rule applied everywhere: a user outside a case's scope learns nothing about it, and a hidden record answers like a missing one.

1. **Party directory.** A party is listed (`GET /parties`), readable (`/parties/{id}`, `/parties/{id}/cases`), editable and
   linkable (`POST /cases/{id}/parties`, the intake) only for a caller who sees at least one case it is on, **or** when it is on no
   case at all (so the picker still finds an unlinked party), **or** the caller created it, **or** it is the authority party. A party
   that is only on cases the caller cannot see answers 404, identical to a random id. This is the least leaky option that keeps
   linking working: the creator rule lets a user link a party they just made; the "no case yet" rule is the one remaining
   disclosure (a party whose last case was unlinked, or not yet linked, shows its name to every CASE_VIEW holder). Grants and
   revocation take effect at once (`PartyDirectoryAndDefectsTest.aGrantMakes...`). Tests: `thePartyDirectoryNamesNoCaseToAnOutsider`,
   `PartyDirectoryAndDefectsTest` (outsider, legitimate linking of unlinked, shared and self-created parties, hidden party 404).
2. **Audit.** For an event about a case the caller cannot see, the target id is now withdrawn (null) as well as the details; the
   event stays listed with `masked: true`. This also covers events with no details (previously returned unmasked). The owner and
   anyone who sees the case still get the id. Test: `auditDetailsOfTheSecretCaseAreMaskedForAnOutsider`.
3. **Assistant query log.** `GET /assistant/queries` (both scopes) with a case id the caller cannot see answers 404, identical to a
   random id, as `/assistant/outputs?caseId=` already did. Nothing else about the assistant changed.
4. **Two 500s.** Unlinking a party that has counsel on the case now answers 409 (the counsel rows are kept, removal is a mark, so
   the link cannot be deleted; a party with only removed counsel is also kept, by design of the history). `GET /documents/{id}/text`
   on a document with no stored version answers 404 (no text, as for a version with no text row); `POST .../text/reprocess` answers
   422 `DOCUMENT_HAS_NO_VERSION`. Tests: `PartyDirectoryAndDefectsTest`.
5. **Exports.** The Excel and PDF of every family and indicator export now run in the surfaces walk (the CSV-only skip is removed):
   no leak was found, no application change needed.
