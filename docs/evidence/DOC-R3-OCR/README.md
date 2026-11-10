# DOC-R3-OCR: exact terms missed by OCR (QA-RT-001)

Synthetic pages only (SYNTH-DOC, DejaVu Sans, invented text). Nothing here says anything about real documents. QA-RT-001 stays open for Codex.

Three things are reported separately everywhere and must not be merged:

- (a) PRIMARY text accuracy: the main reading, as stored and shown as the page text, against what was drawn (character and word error rate).
- (b) Recovery through alternate readings: words another reading of the page found; they are stored apart, never shown as page text.
- (c) Search: what a search finds (primary text, or one alternate word), and false positives.

A recall of 1.000 reached through alternates is NOT OCR accuracy. The primary text is exactly as accurate as before this change (columns identical before and after in `tuned-corpus-before.tsv` / `tuned-corpus-after.tsv`).

## Decisions for the owner (separate from the fix)

1. No acceptance criterion was lowered and no test was excluded. `TesseractOcrEngineTest.readsGeneratedArabicAndEnglishPages` and `OcrQualityTest` (case `short-2-lines-ar-300dpi`) are back to their develop versions, thresholds unchanged. Both FAIL on Alpine Tesseract 5.5.2, identically on develop 820c5c0 (before this change) and on this branch (section 2), so the failures pre-date the change; they are not regressions. Their thresholds were recorded on Tesseract 5.3.4. Whether to change them (and to what) is an owner decision; no new value is proposed here. They stay red until decided.
2. Cost: recognition takes about 2.1 times as long per file and 2.2 times the CPU (section 7). The owner may prefer a lower pass count for dense pages, or a bigger worker pool; not decided here.
3. A search hit may now rest on an alternate reading; the API says so and the UI shows "Found in an uncertain alternate reading". The assistant and every other text consumer never see alternates.

## 1. Engine and how every number is reproduced

Tesseract 5.5.2, leptonica 1.87.0, Alpine 3.24.4 package repository (`tesseract-ocr~=5.5`, `tesseract-ocr-data-ara~=5.5`, `tesseract-ocr-data-eng~=5.5`): the packages the backend Dockerfile installs. The host has no Tesseract and this user has no Docker, so the Alpine minirootfs is unpacked and the engine runs inside it with `bwrap`:

```
R=$SCRATCH/rootfs; mkdir -p $R
curl -s https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/x86_64/alpine-minirootfs-3.24.4-x86_64.tar.gz | tar xz -C $R
cp /etc/resolv.conf $R/etc/resolv.conf
bwrap --bind $R / --proc /proc --dev /dev --unshare-user --uid 0 --gid 0 /bin/sh -c "apk add --no-cache 'tesseract-ocr~=5.5' 'tesseract-ocr-data-ara~=5.5' 'tesseract-ocr-data-eng~=5.5'"
# (apk prints trigger errors for glib in the sandbox; tesseract itself installs and runs)
printf '#!/bin/sh\nexec bwrap --bind %s / --proc /proc --dev /dev --bind /tmp /tmp --unshare-user --uid 0 --gid 0 --setenv OMP_THREAD_LIMIT 1 /usr/bin/tesseract "$@"\n' $R > $SCRATCH/bin/tesseract; chmod +x $SCRATCH/bin/tesseract
export JAVA_HOME=/home/aiagent/tools/jdk-21.0.12.1+1 PATH=$SCRATCH/bin:$JAVA_HOME/bin:$PATH
```

Traineddata: `/usr/share/tessdata/ara.traineddata` 2,494,806 bytes (sha256 2005976778bbc14fc56a4ea8d43c6080847aeee72fcc2201488f240daca15c5b), `eng.traineddata` 23,466,654 bytes (sha256 daa0c97d651c19fba3b25e81317cd697e9908c8208090c94c3905381c23fc047). By file size this is the official "tessdata" set, not tessdata_best (eng about 15 MB) or tessdata_fast (eng about 4 MB); Alpine ships no `best` package, so the Dockerfile is unchanged. The exact patch revision of the Alpine package in the deployed image could not be read (image not pulled); the printed version equals QA's 5.5.2.

Baseline = develop 820c5c0 in a second worktree (`git worktree add <dir> 820c5c0`), same environment, same commands. Every measurement is one command (from `backend/`, tesseract wrapper on the PATH, `pg_dump`/`pg_restore`/`psql` 18 on the PATH for the restore test):

| What | Command |
|---|---|
| Two formerly excluded tests, after | `./mvnw -B -o test -Dtest='TesseractOcrEngineTest,OcrQualityTest' -Docr.report=<tsv>` |
| Two formerly excluded tests, before | the same in the 820c5c0 worktree |
| Tuned corpus, 3 metrics, before and after | `./mvnw -B -o test -Dtest=OcrExactTermsTest -Docr.report=<tsv>` (writes `<tsv>` and `<tsv>.before`) |
| Hold-out pages | `./mvnw -B -o test -Dtest=OcrHoldoutTest -Docr.report=<tsv>` |
| Hold-out multi-page PDF through the application | `./mvnw -B -o test -Dtest=OcrHoldoutPdfApiTest -Docr.report=<tsv>` |
| Pass probe | `./mvnw -B -o test -Dtest=OcrPassProbe -Docr.probe=<tsv>` |
| Cost, 20 pages | `OCR_TIME_LOG=<log> ./mvnw -B -o test -Dtest=OcrCostTest -Docr.cost=<file>` with a tesseract wrapper that runs `/usr/bin/time -f "%e %U %S %M" -a -o $OCR_TIME_LOG` in front of bwrap |
| Connection held / request latency, after and before | `./mvnw -B -o test -Dtest=OcrConnectionHoldTest -Docr.cost=<file>` (before: copy the test into the 820c5c0 worktree) |
| Migration on old data, restore | `./mvnw -B -o test -Dtest=OcrAlternateMigrationTest` |
| Regenerate pages | `OcrFixtureExport -Docr.extra=<dir>` / `-Docr.holdout=<dir>` |

Hold-out freeze: the pass set, thresholds (uncertain below 80 %, alternate at least 60 %) and psm were committed in `FREEZE.md` (commit c22e0ce) BEFORE any hold-out page existed; the hold-out generator was committed after (ba1afd6). `git diff c22e0ce HEAD -- backend/src/main/java/com/lawdesk/documents/TesseractOcrEngine.java backend/src/main/java/com/lawdesk/documents/OcrPreprocessor.java` is empty: the engine did not change after the freeze.

## 2. The two formerly excluded tests, before and after (Tesseract 5.5.2)

| Test / case | develop 820c5c0 (before) | this branch (after) | Threshold (unchanged) | Verdict |
|---|---|---|---|---|
| `TesseractOcrEngineTest.readsGeneratedArabicAndEnglishPages`, sample «محكمة التمييز / حكم نهائي في الطعن» | similarity 0.56, test FAILS | see `after-run.txt` in this folder | 0.80 | pre-existing failure; the engine drops the first line in every pass |
| `OcrQualityTest`, case `short-2-lines-ar-300dpi` | search 0.571 (char 0.514, word 0.571), test FAILS | same figures (primary text only in this test) | search 0.80 | pre-existing failure |

Files: `baseline-820c5c0-summary.txt`, `baseline-820c5c0-quality.tsv` and `after-quality.tsv` (all 30 cases, before and after), `after-run.txt`. The accuracy columns (raw and preprocessed character, word and search figures, detected skew; columns 1-11) are identical for all 30 cases before and after (`diff`; only the timing column differs): this branch does not change the primary text of any case of that corpus, for better or worse.

## 3. Tuned corpus (16 pages, used to choose the passes), three metrics

"Before" means the old pipeline: its text is the primary text only, so its search column equals its (a) terms column. Per page tables: `tuned-corpus-after.tsv`, `tuned-corpus-before.tsv`.

| | before | after |
|---|---|---|
| (a) primary text: terms found of 109 | 95 | 95 (unchanged: the primary text is the same text) |
| (a) primary text: character / word error | unchanged page by page: 0.000 on clean small pages up to 0.396 / 0.545 on the table page and 1.000 / 1.000 on the noisy 9 pt page | identical |
| (b) terms found only through an alternate word | 0 | 9 |
| (b) alternate words stored that are not on the page (junk alternates) | 0 | 28 of 56 alternates |
| (c) search: terms found | 95 | 104 of 109 |
| (c) search: false positives (queries for words not on the page) | 0 | 0 (about 215 queries: near misses of each term and 6 absent words) |

Per page, QA's two pages: `ocr-clear-ar.png` primary still reads `الكوبتي` (char error 0.163, word error 0.250; the word is found only via an alternate, `source: ALTERNATE`); `ocr-mixed.png` primary still reads `02-5` instead of `LD-2026/045` (char error 0.212, word error 0.280; found via an alternate). Both are still PRIMARY-text errors; search finds them, and says the hit rests on an alternate reading.

## 4. Hold-out (12 pages + one 4-page PDF; not used to choose passes or thresholds)

Generated after the freeze: tables, 7-8 pt type, noise, skew, dates (Gregorian `15/03/2026`, Hijri-style `1447/09/25`, Arabic-Indic `٢٠/٠٤/٢٠٢٦`, `٢٠٢٦-١٢-٠١`, ISO), mixed Arabic/Latin references (`CASE-2026/778`, `INV-00921/B`, `م/2025/3041`, `ORD-88/2026`, `٤٥٦٧/٢٠٢٥`), multi-page. Per page: `holdout-results.tsv`.

| | result on the 12 pages (102 terms, 174 negative queries) |
|---|---|
| (a) primary text: terms found | 75 of 102 |
| (a) primary error (char / word) | 0.000 / 0.000 on the two clean Arabic pages; 0.16-0.19 / 0.29-0.33 on date pages; 0.11-0.42 / 0.14-0.63 on reference pages; 0.65-0.78 / 0.80-0.92 on table pages; 1.000 / 1.000 on the 8 pt noisy page (main reading found no word) |
| (b) terms found only through an alternate word | 19 |
| (c) search: terms found | 94 of 102 |
| (c) search: false positives | 1 of 174 |

Missed (8): `الدائرة`, `الاختصاص` (8 pt, 150 dpi, noise); `2026-11-09` on all three date pages; `م/2025/3041` (300 dpi); `2026-02-10` on the 300 and 150 dpi tables. False positive (1): the 200 dpi noisy reference page reads `٤٥٦٧/٢٠٢٥` as 4567/2028, so a search for 4567/2028 finds it: a misreading of a digit, not an invented word.

Multi-page PDF (`h-multipage.pdf`, 4 pages of different quality, rendered by the application, real engine, archive search per term): 21 of 34 terms found, all as PRIMARY; 13 not found (listed in `HoldoutExpectations.PDF_STILL_MISSED`: page 3 table terms and amounts, page 4 dates and two words, page 2 `٤٥٦٧/٢٠٢٥`); the six absent words find nothing; stored file byte-identical. The lower figure than on the images is a result of the application rendering the lower-resolution pages of the PDF; it is reported, not tuned.

The hold-out numbers are worse than the tuned ones (94/102 against 104/109 on images, 21/34 on the PDF), as expected for pages the passes were not chosen on.

## 5. False results, access and consumers

- Absent words: queries for words that are on no page return nothing (`OcrAlternateReadingsApiTest.aWordNoReadingFoundIsNotFoundAndNothingIsComposedAcrossReadings`, `OcrHoldoutPdfApiTest`, and the negative queries of every measured page).
- Hit source: every archive-search hit carries `source` (`PRIMARY` or `ALTERNATE`); for ALTERNATE hits `alternateMatches` lists the query word, the alternate word, page, line of the pass, pass name and confidence. The text API returns the stored alternates (with the same fields) only with `withText=true`. The UI shows "Found in an uncertain alternate reading of the scan (...)" for ALTERNATE hits (Arabic and English).
- No phantom phrases: alternates are one row per word (`document_text_alternate`); a word of the query (a reference such as `LD-2026/045` is one word of three parts) must be in the primary text or inside ONE alternate word. Tests: `ld`+`2026` from the primary text and `045` from an alternate does not match; parts spread over two alternates do not match; one alternate word `ABC-2026/045` does. The archive search is a word-set (AND) search, not a phrase search; separate query words may be satisfied by different sources and the hit then says ALTERNATE. Word order and context are those of the primary text, which is unchanged. The unified search and its stored vector read the primary text only and are unchanged.
- Access: same rules for both (they sit behind the same case join): out-of-scope user, RESTRICTED case with a scope-ALL viewer, VIEW grant, grant revoked, member changed (`OcrAlternateReadingsApiTest.alternatesFollowTheSameAccessRulesAsTheText`).
- Consumers of document text, checked by grep (`document_text`, `search_text`, `DocumentTextService`, `DocumentReadPort`, `texts.view`): `DocumentTextService` (index, search, text view), `DocumentReadPort` (assistant: `findText` now keeps only PRIMARY hits; `text()` returns the primary text), `SearchSpecs`/V844 vector (primary text, unchanged). No case-brief or data-extraction feature reads document text. Test: `AssistantAlternateReadingsTest` (an alternate-only word never reaches an answer, a source or a quote; document analysis reads primary text only).

## 6. Migration V850 on a database with old documents

`OcrAlternateMigrationTest` migrates a fresh database to V845, inserts a case and three documents as the previous release stored them (an OCR_DONE scan, a text-layer file, a PENDING one), migrates to the latest version: text, normalised `search_text`, status and the stored search vector of every old row are identical; old documents are still found (normalised text, vector, Arabic variants); `words_read`, `words_uncertain` and `claimed_at` are NULL for old rows ("not measured", not zero), no alternates exist; a second migration run changes nothing. `OcrAlternateReadingsApiTest.aReprocessReplaces...`: three reprocess rounds, one during an engine failure: the earlier primary text and alternates stay until the new reading replaces them, alternates are never duplicated (always 2), status returns to OCR_DONE. Restore safety: V850 creates no function and no schema-qualified name (asserted by the test); the generated column uses `library_normalize`, which V800 made restore-safe. A real `pg_dump -Fc` of the post-V850 database and `pg_restore --exit-on-error` into an empty database (PostgreSQL client 18, embedded server) returned the same text, normalised text, counts and alternates, with the generated `norm` recomputed, and empty stderr. The full `tools/restore-drill` (300 cases, about 11 minutes) was not rerun; this is the lighter check on the V850 tables only.

## 7. Cost (20-page mixed document, shared loaded host, load average 5-8)

`cost-20-pages.txt`, `engine-run-log-cost.txt` (one line per engine run: elapsed s, user s, system s, peak KB). Pages alternate between tuned and hold-out pictures, A4, one 4.5 MB PDF.

| | before | after |
|---|---|---|
| 20-page file, wall | 168 s | 351 s (2.1x) |
| per page, median | 4.2 s | 8.6 s |
| per page, slowest | 72 s | 157 s (one noisy page: 3 runs of 66-75 s) |
| engine CPU (children user+system) | 143 s | 312 s (2.2x) |
| two workers, two 5-page files at once, wall | 23.8 s | 57.7 s |
| engine runs | one or two per page | up to six per page (179 runs for the whole measurement) |
| peak memory of one engine run | | 195 MB (170 MB for the slow page; OMP_THREAD_LIMIT=1) |

Timeouts: unchanged (120 s per engine run). The slowest single run measured was 74.8 s, so one very noisy page already uses 62 % of the limit on this host; on a slower host that page would time out (the document would be retried, then FAILED). That risk exists before and after this change; this change triples the number of runs on such a page but bounds each run by the same limit.

Database connections and request latency (`OcrConnectionHoldTest`, production pool: Hikari maximum 10, connection timeout 3 s, an engine that takes 4 s):

| | before (820c5c0) | after |
|---|---|---|
| connections in use while the engine runs | 1 (the worker's transaction wrapped the engine) | 0 |
| request latency with no recognition running, p50 / p95 | 26 / 46 ms | 22 / 62 ms |
| request latency while recognition runs, p50 / p95 | 26 / 59 ms | 18 / 30 ms |

The change: `processPending` claims versions in one short transaction (`claimed_at`, `for update skip locked`, a claim older than 30 minutes is retaken), runs the engine with no transaction, and writes each result in its own short transaction. The pool of 10 was not exhausted in either case (one worker, one connection), so the "before" latency is unaffected on a quiet system; the difference shows with several workers or under load. 40 requests per case is a small sample.

## 8. Still missed (open for Codex; kept as failing-if-fixed lists)

Acceptance effect: none of these is accepted as passing; the lists make the tests fail if a term is found again (stale) or a new miss appears.

- Dates: `2026-03-15` (`ocr-mixed.png`) and `2026-11-09` (hold-out date pages), `2026-02-10` (tables): the right-to-left ordering stores the run reversed or a digit group is misread; components are present for the first, and the archive search matches by parts for it.
- Table cells: `المدعية`, `الخبير` (tuned table page); hold-out table dates; PDF page 3 (7 of 10 terms).
- Low-quality pages: 8-9 pt type with noise at 150 dpi (`x-ar-150dpi-9pt-noise.jpg`: main reading empty, `الكويتية`, `الكويتي` missed; hold-out `h-ar-150dpi-8pt-noise.jpg`: `الدائرة`, `الاختصاص` missed).
- Misread digits cause wrong-number matches (1 false positive on the hold-out).
- Alternates add junk words (28 of 56 on the tuned corpus), which can only produce hits that are labelled ALTERNATE.
- Not measured: real scans and fonts, OEM variants, tessdata_best, the full restore drill, more than one pool-sized load.
