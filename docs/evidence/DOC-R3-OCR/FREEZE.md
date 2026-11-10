# Pass-set freeze (before the hold-out corpus exists)

Frozen on the code of the commit that contains this file, BEFORE any hold-out page was generated or read:

- Passes: main (thresholded, deskewed, automatic layout), sparse layout of the thresholded page (short pages), stored picture
  (automatic layout), deskewed grey page (sparse), page enlarged to about 300 dpi (automatic and sparse layout).
- Extra passes run when the main reading holds a word under 80 % confidence or has fewer than 8 lines.
- An alternate word needs at least 60 % confidence in its pass and must not already be in the main reading as a run of words.
- Engine Tesseract 5.5.2 (Alpine 3.24.4), languages ara+eng, psm 3.

Nothing above may change after the hold-out measurement; a change would invalidate it and the hold-out would have to be regenerated.
The hold-out generator is `OcrHoldoutExport` (seeded, deterministic); the pages are in `backend/src/test/resources/ocr-holdout`.
