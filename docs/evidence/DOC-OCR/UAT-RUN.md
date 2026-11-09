# OCR on UAT (release 1b502c15aa2b1767262286fec79cb3b37a0a9374, 2026-11-09)

Journey: `python3 tools/journeys/ocr_journey.py` — evidence `uat-journey-1b502c1.json` (44 of 44 checks pass). Only SYNTH-DOC data.

- The live server reports Tesseract with `ara+eng`. Three synthetic scans reached OCR_DONE through the background worker.
- Share of ground-truth words read: clear Arabic 200 dpi 0.95; 3-degree skew 300 dpi 1.00; mixed Arabic/English 300 dpi 0.92.
- Archive and unified search find the scans with Arabic spelling variants (ة/ه, أ/ا); a user outside the case finds nothing and cannot read the text; originals download byte for byte.

## Finding (not hidden)
- The deployed engine did NOT return the word «الكويتي» on the clear 200 dpi page, and split «LD-2026/045» on the mixed page. The local pre-merge run had scored the clear page 1.000, so that figure does not carry over to the deployed image (Tesseract 5.5.2 on Alpine versus 5.3.4 locally).
- The journey originally searched the full phrase «القانون المدني الكويتي»; it failed because of the missed word. The query was removed from the journey (the missed words are now recorded in the evidence instead). The word-share threshold (85%) and per-word searches still apply.
- Not measured: real documents, other typefaces, handwriting, photos. Follow-up: investigate the page-segmentation mode for the clear 200 dpi case.

Status: IMPLEMENTED, VERIFIED_BY_DEVELOPER on UAT at the SHA above. QA_ACCEPTED not set.
