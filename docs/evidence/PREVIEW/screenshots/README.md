# Preview screenshots (local headless render — NOT a deployment)

Captured 2026-10-23 with headless Chromium (Playwright 1.64.1) against `vite preview` of the built app on
127.0.0.1, at the head of `feature/preview-shell` after the table-wrapping fix. No backend was running, so the API
indicator correctly shows "API unavailable" / "الواجهة البرمجية غير متاحة". No horizontal page overflow at 390 px in
either language. `../layout-check.mjs` is the browser check behind `04-red-table-overflow.log` and
`05-green-table-overflow.log`; it is not yet part of CI (browser end-to-end tests are owned by independent QA).
