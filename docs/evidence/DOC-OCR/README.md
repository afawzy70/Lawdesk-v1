# DOC-OCR: Arabic OCR made real and measured (requirement LD-DOC-13-004)

Branch `feature/LD-DOC-r2-ocr`. All data is synthetic (SYNTH-DOC: invented sentences drawn with the bundled DejaVu Sans).
Statuses are kept apart: **IMPLEMENTED** (image, adapter, preprocessing, search over OCR text, journey written);
**VERIFIED_BY_DEVELOPER** (the tests named below, on the machine and engine named below); **QA_ACCEPTED**: not set (only Codex).
Nothing here was run on UAT or on the built image: no Docker for the agent user on this host (see "What is not proven").

## 1. Engine in the runtime

* `backend/Dockerfile` (runtime stage, Alpine 3.24, pinned by digest): `apk add tesseract-ocr~=5.5 tesseract-ocr-data-ara~=5.5
  tesseract-ocr-data-eng~=5.5`, then `tesseract --list-langs` in the build (the build fails when a language is missing).
  Alpine 3.24 index at the time: `tesseract-ocr 5.5.2-r0`, data `5.5.2-r0`; `~=5.5` holds the 5.5 series while the base image
  digest holds the release. `OMP_THREAD_LIMIT=1` keeps one recognition on one core. The adapter runs `tesseract` from `PATH`;
  data is at `/usr/share/tessdata` (nothing to configure). The app runs read-only with `/tmp` as tmpfs; the adapter writes
  only to `/tmp` (`java.io.tmpdir`).
* CI: the existing job `Staging stack (images + compose smoke)` got one step after "Build images" that runs, on the built
  backend image as user 10001: `tesseract --list-langs` (must list `ara` and `eng`), `--version`, and `id -u` = 10001.
  No other workflow or deploy file was touched.
* Size (from the Alpine index, compressed / installed): tesseract-ocr 2.0 MB / 4.8 MB, ara data 1.7 MB / 2.5 MB, eng data
  10.8 MB / 23.5 MB, plus shared libraries (leptonica, cairo, pango, ICU, harfbuzz, ...) pulled in by tesseract: expect roughly
  +60 to +90 MB on the image (an estimate from package sizes; the image was not built here).
* Memory: one 42-line A4 page at 300 dpi (2233x4400 px) peaks at **111 MB RSS** and took 7 s wall on this loaded host
  (Ubuntu build 5.3.4). The container limit is 768 MB with the JVM allowed 75% (576 MB) of RAM; tesseract runs in the same cgroup,
  one at a time (single-threaded worker, batch of 5). A scanned PDF page is rendered at 300 dpi (about 26 MB of RGB in the JVM).
  This leaves room, but a page peak of ~110 MB plus a full JVM heap is tight: watch `docker stats` on UAT during the journey.
  `deploy/compose.yaml` was not changed.

## 2. Measured accuracy (synthetic corpus)

Measurement setup (not the deployed binary): **Tesseract 5.3.4 (Ubuntu 24.04 build, run from user space) with the exact
`ara`/`eng` traineddata of Alpine `tesseract-ocr-data-*` 5.5.2-r0** (the data the image carries). The 5.5.2 engine binary
itself was not run here; `OcrQualityTest` is the check to run where it exists. JDK 21, Java2D rendering, seeded noise
(`OcrCorpus`), `OMP_THREAD_LIMIT=1`. Per-case numbers: `corpus-results.tsv` (columns: raw page without preprocessing, and the
page the engine really gets). "dpi" is simulated: a 12 pt line is `12*dpi/72` px; images carry no dpi tag.

Metrics (after the archive's own Arabic normalisation: diacritics, alef forms, taa marbuta, alef maqsura, Arabic-Indic digits):
* **char**: 1 - Levenshtein/len on the letters and digits in reading order;
* **word**: 1 - WER on the normalised words in reading order;
* **search recall**: share of the distinct ground-truth words found anywhere in the text: what a user searching for one word gets.

Results with the shipped pipeline (deskew, Otsu, margin, sparse second reading for short pages); minimum over the cases of each group:

| Group (cases) | char acc. min | search recall min | Notes |
|---|---|---|---|
| Clear Arabic, 100 to 400 dpi, English, whole 42-line page (8) | 1.000 | 1.000 | identical text, all resolutions at or above 100 dpi |
| Clear Arabic at 72 dpi / 50 dpi | 0.967 / 0.585 | 0.824 / 0.412 | **below 100 dpi it degrades**; 50 dpi is unusable |
| Scanned look: noise, low contrast, blur (3) | 1.000 | 1.000 | preprocessing took the raw minimum from 0.978 to 1.000 |
| Scanned look: salt-and-pepper speckle at 200 dpi | 0.978 | 0.912 | raw 0.940 / 0.794 |
| Skew from -5 to +10 degrees (8) | 1.000 | 1.000 | raw engine: +5 deg 0.31 / 0.82, +10 deg 0.21 / 0.18 |
| Short pages (1 to 2 lines) (5) | 0.613 | 1.000 | char is low only where line order differs (mixed Arabic/English) |
| Mixed Arabic/English with digits, dates, e-mail (2) | 0.716 | 0.971 | line/run order is not reproduced (bidi) |
| Digits: Western and Arabic-Indic, amounts, dates (2) | 0.893 | 0.955 | Arabic-Indic numbers are misread (see limits) |
| Diacritics (tashkeel) (1) | 0.800 | 0.500 | **not supported** |

### What preprocessing was kept, and why (measured, not assumed)

Variants measured on the same corpus (first run, `OcrQualityMeasurement`, removed after use): gray, Otsu, deskew, deskew+Otsu,
2x upscale, 2x upscale + deskew. Average cost per page, raw 1.9 s vs deskew+Otsu 1.6 s (no slower).
* **Deskew + Otsu: kept.** Skew of 3 degrees or more: raw 0.89 / 0.24 / 0.00 (3, 5, 10 degrees) to 1.000; noisy scans
  0.94-0.99 to 1.000. Skew detection returned the drawn angle exactly on all 8 skew cases.
* **2x upscaling: rejected** (worse: digits 0.88 to 0.71, 300 dpi mixed 0.77 to 0.72, diacritics 0.80 to 0.25).
* **Rescaling large text down (target line height 30/40/50 px): rejected**, no average gain (char mean 0.945 raw vs 0.926/0.937/0.947).
* **White margin: kept** (cheap; a measured first-line loss was not cured by it, see next point, so its benefit is unmeasured and it is harmless).
* **Sparse second reading for pages with fewer than 8 lines: kept.** On a two-line Arabic page at 300 dpi the default layout
  analysis (`--psm 3`, and `--psm 6`) dropped the whole first line (char 0.51, search recall 0.57); `--psm 11` read both. The merge adds
  only lines that share fewer than half of their words with every line of the first reading (a second reading of a line already
  read differs by a letter; simply appending it cost 0.1-0.3 of char accuracy on clean pages, measured and rejected). It can add words,
  never remove any. Result: 1.000 / 1.000 on those cases.

### Acceptance thresholds (asserted by `OcrQualityTest`; skipped where no engine exists)

Each is below the minimum measured for its group, leaving room for the 5.3.4 to 5.5.x engine difference:
* clear (at least 100 dpi), skew (to 10 degrees), scanned look without speckle or mixed text: **char >= 0.95 and search recall >= 0.90**
  (measured minimum 1.000 in every one of these cases);
* mixed Arabic/English, digits, speckle: **search recall >= 0.80** (measured minimum 0.912), no char promise because the engine may reorder runs;
* 50/72 dpi and diacritics: **no promise**, recorded as limitations.

### Limitations and failure modes (measured or by construction)

* **Resolution**: at 72 dpi (12 pt line = 12 px) char accuracy 0.97; at 50 dpi 0.59. Scan at 150 dpi or more (300 dpi recommended).
* **Large glyphs on a small canvas**: the previous test image (48 px on 1400x400, no dpi) lost the first line of a two-line page in
  every mode we tried; halving the image cured it, but the corpus shows no gain overall from rescaling, so it is a known failure mode, not
  handled. The existing sample test now uses realistic text (28 px on 1000x260).
* **Diacritics**: vowelled Arabic reads at 0.80 char / 0.50 search recall; do not rely on OCR search for fully vowelled text.
* **Arabic-Indic digits**: `١٢٥٠٫٥٠٠` came out as `17000٠٠`, `٩٨٧٦٥` as `9/1710`. Western digits are read well (0.955 recall on digit pages).
* **Bidirectional order**: in lines mixing Arabic and Latin the run order or date order may differ (`2026-03-15` came back as
  `15-03-2026`); search recall is unaffected (tokens are the same), displayed text is not a faithful layout.
* **Not measured, so not claimed**: handwriting, stamps and signatures overlapping text, tables and multi-column layouts, forms, photographs of
  pages (perspective, shadows), non-DejaVu typefaces (Naskh, Kufi, Traditional Arabic: the corpus uses one typeface only), real scanner noise,
  EXIF-rotated photographs (orientation is not read), pages rotated 90/180 degrees (deskew searches +/-10 degrees only).
* **Page limit**: PDFs are read up to the archive setting `maxOcrPages` (50, an assumption of the archive settings); the state then says
  "only the first N of M pages were read" and `truncated` is set. Pictures above 40 megapixels and multi-page TIFFs skip preprocessing and
  go to the engine as stored (never lost, possibly less accurate).
* **Time**: 1 to 10 s per page on this loaded host; the engine has a 120 s limit per run (the output is now written to a file so the limit also
  applies to a hung engine; before, reading the pipe would have waited forever).

### Failure states and retries (UI and API)

Every stored version records `PENDING | EXTRACTED | OCR_DONE | OCR_UNAVAILABLE | NO_TEXT | UNSUPPORTED | FAILED`
(`GET /api/v1/documents/{id}/text`; badge and "searchable / not searchable" in the version list). The file is stored before
recognition and never depends on it. A failed page is retried by the worker up to 3 times (state `PENDING`, "recognition will be
retried"), then `FAILED`; a user with the manage right re-reads with `POST /api/v1/documents/{id}/text/reprocess` (button "read the file again",
shown for every state other than EXTRACTED / OCR_DONE); the attempt counter restarts. Open gap: the UI shows the engine's detail text only for
`OCR_UNAVAILABLE`, not for `FAILED` (page-limit detail is shown through the truncated flag).

## 3. End to end (tests, developer machine)

`OcrRealEngineApiTest` (real engine inside the Spring/Postgres test context, skipped where there is none): a skewed noisy synthetic scan is
uploaded, `PENDING` then `OCR_DONE`; archive search finds it by `مذكرة دفاع`, `مذكره` (taa marbuta), `القانون المدني`, `قانون مدني`
(article), `الجلسه القادمه`, `Quetzal 4455`; **unified search now finds a document by its text** (new part `DOCUMENT_TEXT` of the DOCUMENT type;
before this change the unified search read only title and file name, so OCR text was invisible there); a user without the case gets
`totalItems 0`, no id and no snippet from either search and 404 on the text; a scanned PDF is read through the rendered page; a failing
engine ends in `FAILED` while `GET .../content` still returns the exact bytes and one version row remains, and after the engine recovers
`reprocess` makes it `OCR_DONE` and searchable. Existing suites run with it: `ArchiveTextSearchApiTest`, `UnifiedSearchApiTest`,
`TesseractOcrEngineTest`, `TesseractMergeTest` (45 tests, 0 failures, one run).
Risk to watch: the `TEXT` part makes the unified search read the whole text of every visible document for each query (the same pattern as
the other parts). Not load-tested with large archives; if documents become numerous, store a tsvector column on `document_text` (additive migration).

## 4. UAT journey (written, NOT run)

`tools/journeys/ocr_journey.py` (fixtures `tools/journeys/ocr_fixtures/*`, written by `OcrFixtureExport`: a clear PNG, a skewed noisy JPEG
and a mixed Arabic/English PNG, with `manifest.json` ground truth). It creates one synthetic case, uploads the three images, polls (the
worker runs each minute) until none is `PENDING`, and checks: the server reports Tesseract `ara+eng`; each document is `OCR_DONE`
(the application's name for "OCR completed"; there is no state called `OCR_COMPLETED`) with method `OCR`; at least 85% of the ground-truth
words are in the read text; archive search and unified search hit each document with Arabic spelling variants; the unified match is in `DOCUMENT_TEXT`;
a user outside the case finds nothing and cannot read the text; the originals download byte for byte. Run it only after the integration
owner has merged and deployed.

## What is not proven

* The image was not built and `tesseract --list-langs` was not run in it (no Docker for this user): the CI step is the proof, on the PR.
* Accuracy was measured with Tesseract 5.3.4 plus the 5.5.2 data, not with the 5.5.2 binary.
* Nothing ran on UAT. Real-document accuracy is unknown: the corpus is clean, synthetic, one typeface.
