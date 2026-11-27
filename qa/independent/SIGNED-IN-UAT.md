# Signed-in Phase 2–4 UAT

Target: `https://lawdesk.167-86-90-174.sslip.io`. First prepared against baseline
`81d9d03acdc6f8d6e3d11475342ac10e49f79a32`. This supplements the existing anonymous
`browser/phase234-uat.mjs` and local PostgreSQL/API suites; it does not replace their evidence.

`browser/phase234-signedin.mjs` contains 35 executable specifications, each with story IDs,
priority, preconditions, synthetic data, reproducible steps and expected results. Its
authenticated path ran on 2026-10-24: 33 PASS and two mobile-layout failures against the
deployment reported as `8b0928ba10eb9b02ac3822ac33eca5b8778195b6`. Initial harness assumptions
about timeline pagination, court-number length and the section heading were corrected;
the diagnostic runs remain preserved and excluded from final counts.

Additional live suites now cover all four approved roles (`phase234-roles-integrity.mjs`),
actual administration/judicial form submissions (`phase234-forms.mjs`), and the25 case status
transitions plus audit/session/keyboard checks (`phase234-lifecycle.mjs`). See the current
report `qa/reports/2026-10-24-signedin-uat-8b0928b.md` for counts and confirmed defects.
The prior credential BLOCKED evidence remains historical; it is not the current access state.

## Secure handoff

The owner has supplied approved qa.admin/qa.manager/qa.staff/qa.viewer accounts using private local
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

The main suite exercises browser login/password change/case creation/edit/search/logout,
live API administration and judicial lifecycle, UI display of judicial stages/roles/audit,
and en/ar layouts at 375/1366px with axe. Supplementary suites submit the judicial and
administration forms in real Chromium and test the focused four-role authorization matrix.
They do not claim all accessibility requirements, every permission combination, or legal
rules are approved. The current report explicitly lists remaining NOT_RUN scope and the
backend-identity BLOCKED check. QAD-022..026 are verified fixed in live UAT; do not ask Claude
to repeat those fixes. QAD-027/028 require remediation and an independently observed retest.
