# Signed-in Phase 2–4 UAT

Target: `https://lawdesk.167-86-90-174.sslip.io`. First prepared against baseline
`81d9d03acdc6f8d6e3d11475342ac10e49f79a32`. This supplements the existing anonymous
`browser/phase234-uat.mjs` and local PostgreSQL/API suites; it does not replace their evidence.

`browser/phase234-signedin.mjs` contains 35 executable specifications, each with story IDs,
priority, preconditions, synthetic data, reproducible steps and expected results. It is a
**draft until its authenticated path has actually run**. Syntax validation and exercising
its missing-credential guard do not verify the product or validate its browser selectors.

## Secure handoff

The owner must identify an existing approved dedicated QA account using a private local
file, owned by the QA OS user, mode `0600`, containing JSON fields `username` and `password`.
Set **only the path** in `LAWDESK_QA_CREDENTIAL_FILE`. Do not place the file in the repository,
paste its contents in chat, pass passwords on the command line, or add them to reports.
No bootstrap/admin credential discovery or password guessing is permitted. The initial
account must already have completed its password setup and hold the test permissions
listed by the LOGIN specification. The runner will not reset this account or assign it roles.

Credentials and random disposable passwords remain in memory. The runner does not enable
HAR, tracing, video, console forwarding, cookie exports or raw request/response logging.
Known passwords are redacted from exception text. Screenshots exclude password forms and
unfiltered customer lists. Evidence contains only assertion outcomes and owned fixture IDs.

## Execution

1. Confirm current develop and deployed frontend identity; record the CI deployment job
   separately from independent tests. A matching frontend does not attest backend runtime SHA.
2. Identify the approved secure credential handoff; do not reuse embedded-test password files.
3. Use the existing Playwright runtime and Chromium. Set `LAWDESK_QA_CHROMIUM` and the browser
   library path as described in the previous Phase 2–4 execution evidence.
4. Run, using a new evidence directory each time:

   ```sh
   node qa/independent/browser/phase234-signedin.mjs \
     https://lawdesk.167-86-90-174.sslip.io \
     81d9d03acdc6f8d6e3d11475342ac10e49f79a32 \
     /tmp/lawdesk-signedin-new-execution
   ```

5. Triage every failed assertion. Browser selector errors are harness diagnostics until
   independently reproduced as product defects. A failed setup blocks dependent cases.
6. Review evidence for secrets and unrelated personal data before copying to `qa/evidence/`.
   Publish defects with exact SHA, fixture IDs, steps, expected/actual behavior and severity.
7. Retest fixes only after deployment identity is refreshed. Never carry a PASS to a new SHA.

Exit codes: 0 = all registered tests PASS; 1 = at least one assertion FAIL requiring triage;
2 = at least one BLOCKED/NOT_RUN and no assertion failure. Missing credentials produces
35 BLOCKED specifications without opening the browser or sending authenticated requests.

## Isolation and scope limits

Each run generates a unique `QA<timestamp><random>` prefix. New users use `example.invalid`
emails, new parties omit national identifiers, and all resource IDs are held in an ownership
manifest. Updates target only resources created by that execution. The runner creates one
custom CASE_VIEW role and a disposable user for password and denial tests. It disables that
user at the end where the authenticated session permits; inspect DISABLE results and the
ownership manifest if execution is interrupted. Records remain identifiable as QA evidence;
there is no bulk deletion, direct database write, deployment or infrastructure mutation.

The current draft exercises browser login/password change/case creation/edit/search/logout,
live API administration and judicial lifecycle, UI display of judicial stages/roles/audit,
and en/ar layouts at 375/1366px with axe. It does **not** claim all judicial forms or all
administration forms have been submitted in the browser, all accessibility requirements
passed, all permission combinations passed, or legal rules approved. The remaining focused
specifications in the new report and the existing `phase234-next-specifications.json` stay
NOT_RUN until actually executed. The real UAT blocker is an account handoff, not a request
for Claude to reimplement already merged fixes.
