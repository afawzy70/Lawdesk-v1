# DOC-WP5 — judgment enforcement, memos and correspondence with approval, and the advanced electronic archive (LD-055)

Story LD-055, migrations V700–V705, requirements sections 11, 12 and 13 (matrix rows `LD-DOC-11-*`, `LD-DOC-12-*`,
`LD-DOC-13-*`), owner decision D7. Branch `feature/LD-DOC-wp5-archive-enforcement`, PR to `develop`.

## Status at a glance

| Part | Status |
|---|---|
| Enforcement file per judgment, actions, unexecuted / late lists | Done, tested |
| Memos and correspondence register, approval cycle, `sent_recorded` ≠ integration | Done, tested |
| Document versions (v1 for every existing document), classification, links, deletion procedure and log | Done, tested |
| Accepted formats and limits as settings | Done, tested (values are ASSUMPTIONS, below) |
| Text extraction (PDF text layer, Word) and search with Arabic normalisation, restricted by case access | Done, tested (Arabic PDFs written the way PDF printers write them, Word, spelling variants) |
| **OCR of scans and images** | **BLOCKED on this machine and in CI: no Tesseract (and no Arabic language data) is installed.** The pipeline, the port and the adapter are built and tested with a stub engine; the real engine has never read a page here, so **OCR quality is NOT verified** |
| Encrypted storage / moving files out of the database | **Not done, by decision (D7 finding below)**: files stay in PostgreSQL |

## D7 — what was checked before any file was touched

* **Encryption**: the host's root volume is plain ext4 (`lsblk -f`, no `crypt` in `/proc/mounts`); the database volume is an ordinary
  Docker named volume (`db-data` in `deploy/compose.yaml`). There is **no encrypted on-server volume available to the app container**,
  so the brief's condition for changing the storage is not met. Files therefore stay in PostgreSQL (`bytea`), as before.
* **Key handling**: the application holds no file-encryption keys (none are needed while files are in the database). Database
  credentials are Docker secrets (`deploy/compose.yaml`).
* **Backup**: `deploy/scripts/deploy.sh` runs `pg_dump -Fc` into `LAWDESK_BACKUP_DIR` (mode 0600, newest 10 kept) before every
  release; the dump therefore already contains every file. The dump is **not encrypted**; restoring is an operator action
  (`deploy/host/rollback-drill.sh` rehearses the rollback). Both are unchanged by this package (no deploy/CI file was touched).
* **Restore test**: no `pg_dump` exists in the test environment, so `ArchiveMigrationTest.archivedContentSurvivesALogicalDumpAndRestoreWithTheSameHashes`
  copies every archive table into a second freshly migrated database in PostgreSQL's binary copy format and compares the SHA-256
  of every stored version before and after. A real `pg_dump`/`pg_restore` round trip on UAT is for the integration owner
  (`deploy/host/rollback-drill.sh`).
* **No existing attachment is lost**: `ArchiveMigrationTest.everyExistingDocumentGetsVersionOneWithTheSameIdAndContentAndNothingIsLost`
  migrates a database to the last migration before V700, writes documents the way the previous release wrote them (including a
  claim that cites one as its source), applies V700–V705 and compares before/after: the same rows, ids, metadata, content hash
  (`sha256(bytea)` computed by PostgreSQL) and links; each document has `document_version` v1 with **its own id**, the same
  metadata and the same bytes; running the migrations again changes nothing. `case_document` and `case_document_content` keep every
  row (rollback to the previous release leaves them as they were; documents created after V701 live in the new tables only).

## Requirements covered

| Requirement IDs | What exists | Where |
|---|---|---|
| 11-001 | Enforcement file per judgment: judgment number (the court case number of its proceeding — a judgment has no number of its own), date and operative part read from the judgment; party charged with enforcement; amount required (`numeric(15,3)` KWD, a string, never mixed with the other amounts); actions and correspondence with dates and results (kinds PROCEDURE / CORRESPONDENCE / OBSTACLE / OTHER, optional document of the case); obstacles; completion date (required for EXECUTED); status NOT_STARTED / IN_PROGRESS / PARTIALLY_EXECUTED / EXECUTED / OBSTRUCTED / NOT_REQUIRED (proposed values — the source names none); responsible member | `enforcement_file`, `enforcement_action`; `/api/v1/judgments/{id}/enforcement`, `/api/v1/enforcement/{id}`, `/api/v1/enforcement/{id}/actions`; `EnforcementApiTest`; `JudgmentEnforcement` |
| 11-002 | Lists of judgments not executed (no file, or not EXECUTED / NOT_REQUIRED) and late (a member's target date `due_on` has passed; the system computes no legal period); restricted to visible cases | `/api/v1/enforcement/judgments?scope=UNEXECUTED\|LATE\|ALL`; `EnforcementListPage` at `/enforcement` |
| 12-001…12-008 | Types of memos and correspondence = the managed list `DOCUMENT_CATEGORY`, whose eight values are the eight kinds of section 12 (origin DOCUMENT) plus a proposed OTHER (D10) | V705; `/api/v1/lookups/DOCUMENT_CATEGORY` |
| 12-009 | Each memo or letter has a name, type, date, issuer, addressee, responsible member, description and electronic copy (a document of the archive, uploaded through the register and versioned) | `legal_document`; `/api/v1/cases/{id}/legal-documents`, `/api/v1/legal-documents/{id}[/file]`; `LegalDocumentApiTest`; `CaseLegalDocuments` |
| 12-010 | Approval cycle DRAFT → IN_REVIEW → APPROVED → SENT → ARCHIVED, with return to draft (reason) and reopen (reason); every step is an event with user and time; approval needs `MEMO_APPROVE` and only the right to **see** the case (viewing, changing and approving are separate rights, D3); the approver's name and date are kept; an approved memo's file is locked until the approver reopens it. **`SENT` is a member's record** (date, method, reference); `sent_via_integration` is false and the database refuses true (no integration exists) | `LegalDocumentApiTest` (cycle, separation of rights, lock, `sendingIsOnlyARecordAndTheDatabaseKeepsItThatWay`) |
| 13-001 | Accepted formats PDF, Word (docx; doc stored, text not read), images (PNG, JPEG, TIFF), recognised **by signature** and by the declared type, never by name; SVG, HTML, executables, bare archives refused; a manager may switch a format off | `FileFormats`, `archive_format`; `ArchiveFormatsAndSettingsApiTest` |
| 13-002 | Classification by type (category), date and judicial stage (proceeding of the same case) | `PUT /api/v1/documents/{id}/classification`; `DocumentClassification` |
| 13-003 | Search inside documents over the extracted text, Arabic-normalised by `library_normalize` (WP7): diacritics, alef forms, taa marbuta, alef maqsura, the definite article, presentation forms (NFKC); every word must match; a snippet (the matching line only) is returned; restricted by `CaseAccessPolicy` — a result or snippet of a case the user cannot see never appears | `document_text`; `/api/v1/documents/search`; `ArchiveTextSearchApiTest`, `ArchiveVisibilityApiTest`; `ArchiveSearchPage` at `/archive` |
| 13-004 | OCR through the `OcrEngine` port, local only (D7): a `TesseractOcrEngine` adapter runs the `tesseract` command without a shell (`ara+eng`), reports itself **unavailable** when the program or the Arabic data is missing, and every version records its honest state (PENDING / EXTRACTED / OCR_DONE / OCR_UNAVAILABLE / NO_TEXT / UNSUPPORTED / FAILED). A background worker reads waiting scans; a user can ask for a file to be read again after an engine is installed | `OcrEngine`, `TesseractOcrEngine`, `OcrSchedule`; `TesseractOcrEngineTest`; `ArchiveTextSearchApiTest` (stub engine) — **see the OCR status below** |
| 13-005 | Every version is kept (`document_version`, append-only by trigger); the current one is the head; any version can be downloaded | `ArchiveVersionsApiTest`; `DocumentVersions` |
| 13-006 | Uploader and date of every version | `document_version.uploaded_by/at`; `ArchiveVersionsApiTest` |
| 13-007 | No permanent deletion except through the procedure: the old DELETE now only **withdraws** the document from the case file (reversible, with a reason); permanent deletion needs `DOCUMENT_ARCHIVE_ADMIN`, a withdrawn document, a stated reason, and no record of the case still pointing at it; it is audited and logged **without content**; the database itself refuses any other delete of an archived document, version, content row or log row | `ArchiveDeletionApiTest`; `WithdrawnDocuments` |
| 13-008 | Log of additions, changes and deletions: case timeline events (`DOCUMENT_ATTACHED`, `_VERSION_ADDED`, `_CLASSIFIED`, `_REMOVED`, `_RESTORED`, `_LINKED`, …), audit events for every change and download, and `document_deletion_log` | tests above |
| 13-009 | Links to a claim, defence, hearing, judgment or task of the same case (also enforced by a database trigger) | `document_link`; `/api/v1/documents/{id}/links`; `DocumentLinkApiTest`; `DocumentLinks` |
| S8 | A document is linked to a fact and a defence through the existing evidence links (WP2); the archive adds document-level links and the case file shows both | WP2 + `DocumentLinkApiTest` |

Access (D3): every endpoint resolves the case of its record and uses `CaseAccessPolicy`: 404 for a user who may not see the case, 403
for one who sees it but may not change it; lists, the register, the search, the enforcement lists and the deletion log are
restricted to visible cases. `ArchiveVisibilityApiTest` walks through all 29 case-touching endpoints with an outsider (404) and a
department-wide viewer (403 on every write). New permissions (`Permission.java`, `permissions.ts`, labels in both languages):
`ENFORCEMENT_VIEW/MANAGE`, `MEMO_APPROVE`, `DOCUMENT_ARCHIVE_ADMIN`, seeded as in LD-DOC-04 (V700).

## OCR status — BLOCKED / PARTIAL

* `tesseract` is **not installed** on this machine (`which tesseract` → nothing) and not on the hosted CI runner image; no Arabic
  language data exists. Nothing in this package marks OCR as verified.
* Built and tested: the port, the adapter (unavailable-engine behaviour, missing-language behaviour), page rendering of scanned
  PDFs (`TextExtractor.render`), the worker (retries, failure after three attempts, page limit), honest statuses and the UI
  that shows them, and a **quality test that generates real sample pages in the test run** (Arabic with two lines, a court line,
  English; drawn with the bundled DejaVu Sans through Java2D text shaping) and compares the engine's reading with the text drawn
  (normalised edit distance, threshold 0.8). `GeneratedSamplesTest` proves the samples really carry shaped Arabic text.
  `TesseractOcrEngineTest.readsGeneratedArabicAndEnglishPages` is **skipped here (`BLOCKED: no Tesseract…`)**; it runs by itself on
  any machine where `tesseract --list-langs` lists `ara` and `eng`. Skipped is not passed.
* To close it: install `tesseract-ocr` and `tesseract-ocr-ara` on the app host (the app container is a deploy matter, so no
  deploy/CI file was changed) and run the test class; if the engine's Arabic quality is below 0.8 another engine is needed — the port
  is the only thing that changes. The setting screen (`GET /api/v1/archive/settings`, `ocr`) shows the engine status.
* Verified without an engine: Arabic **text layers** are read correctly from PDFs written the way PDF printers write Arabic (shaped
  presentation forms in visual order — `TextExtractorTest`), and from Word. Known limit: a PDF that stores *unshaped, logical-order*
  Arabic is read back reversed by PDFBox's bidi handling; such files are not produced by Word, PDF printers or the system's own
  exports, and are not searchable until read by OCR.

## Decisions and assumptions to confirm (owner)

* **Limits (settings, `archive_setting`)**: 10 MiB per file (the web layer's own limit is the same, so this can only be lowered), **100
  documents per case** (was 10 in LD-026, too few for a central archive), **20 versions per document**, **50 pages read by OCR per
  file**, 2,000,000 characters of text stored per version. All editable by `DOCUMENT_ARCHIVE_ADMIN` through `PUT /api/v1/archive/settings`
  without a release.
* **Formats**: PDF, DOCX, DOC (stored, text not read), PNG, JPEG, TIFF. Nothing else; SVG and HTML are refused on purpose.
* **Deletion procedure**: withdraw (any document manager, reason) → permanent deletion (`DOCUMENT_ARCHIVE_ADMIN`, reason, no
  references). The one actor may do both; a four-eyes rule was not added because the source does not ask for one.
* **Self-approval**: a holder of `MEMO_APPROVE` may approve a memo they drafted themselves (the source only requires the right);
  the history records who did what.
* **Approver's timeline entry**: an approver who may see but not change the case writes the timeline entry through the port made for
  that case (`recordTaskEvent`).
* **Enforcement status values** and **action kinds** are proposals; `due_on` is a target date typed by a member, never computed.
* **"Judgment number"**: judgments have no number of their own; the court case number and year of the proceeding are shown.
* **Categories** reuse the managed-list table (`lookup_value`, list `DOCUMENT_CATEGORY`) instead of a separate `document_category` table;
  same behaviour as D10 (origin DOCUMENT / PROPOSED, history, deactivation).
* **Search** matches words by substring of the normalised text (no index): fine for thousands of documents; a trigram index would
  need `pg_trgm`, which the application role cannot create (see V640).

## What remains

* OCR verification and, if needed, a different engine (above).
* Encrypted storage / files outside the database: only once an encrypted volume is available to the app container and backups cover it.
* A real `pg_dump` / `pg_restore` drill with archive data on UAT, and the journey below.
* Text of the old Word `.doc` format is not read (stored and versioned only).
* The detail panel shows the uploader as a short id (the API returns ids); a name lookup can be added with the user directory.

## UAT journey

`python3 tools/journeys/wp5_journey.py docs/evidence/DOC-WP5/journey-wp5.json` — run by the integration owner after the merge and
deployment (needs the QA accounts under `LAWDESK_QA_DIR`). It creates only `SYNTH-DOC` records and records the OCR engine's status
as found, never assuming it.
