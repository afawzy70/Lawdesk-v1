# Phase 7 screen coverage at the refreshed baseline

Baseline: `origin/develop` `2d07a222189f82ddeacf74fdcd28165980cea85d`. This matrix covers every implemented route or embedded screen listed in [the inventory](PHASE-7-SCREEN-INVENTORY.md). `B4` means a current Chromium render in English and Arabic at 320, 375, 768 and 1366 px with no page error or document-level overflow. `T` means developer unit/component coverage. `J` means a focused functional browser journey. `S` means a representative screenshot in the evidence folder. These are developer checks with fictional API responses, not UAT or independent QA acceptance.

| Screen / route | Current coverage | Visual status and limit |
| --- | --- | --- |
| Login | B4, T | Modern Chambers entry; axe scan in all 8 states. |
| Forced password change | B4, T, S | Shared form design under mandatory reset state. |
| Profile `/profile` | B4, T | Shared dossier treatment. |
| Dashboard `/` | B4, T, J, S | Compared with the only approved Stitch screen; live diary, case register and API metrics. Axe scan in all 8 states. |
| Case list `/cases` | B4, T, J, S | URL search, filters, paging and mobile records. |
| Case creation `/cases/new` | B4, T, S | Real form and validation tests. |
| Case detail `/cases/:id` | B4, T, J, S | Dossier identity and shell; browser fixture leaves secondary records empty. |
| Case edit `/cases/:id/edit` | B4, T | Versioned form and validation tests. |
| Case parties | T; parent B4 | Link/unlink API flow in component tests; populated browser capture pending. |
| Case status and timeline | T; parent B4 | Status rules and chronology tests; populated browser capture pending. |
| Proceedings, judgments, appeals, cassation, special decisions | T; parent B4 | Judicial lifecycle tests; populated browser capture pending. |
| Case hearings | T; parent B4 | Schedule/reschedule/result tests; populated browser capture pending. |
| Case tasks and reminders | T; parent B4 | Task mutation tests; populated browser capture pending. |
| Case documents | T; parent B4 | Upload/download/delete tests, including PR #53's 413 mapping; populated browser capture pending. |
| Case experts and referrals | T; parent B4 | Referral/session/report tests; populated browser capture pending. |
| Case notes | T; parent B4 | Note mutation tests; populated browser capture pending. |
| Courts `/courts` | B4, T | Arabic narrow capture. |
| Hearings `/hearings` | B4, T | Calendar and result controls. |
| Tasks `/tasks` | B4, T, S | Arabic narrow capture. |
| Documents `/documents` | B4, T | File register and download controls. |
| Experts `/experts` | B4, T | Registry and entry form. |
| Case inventory `/reports` | B4, T, J, S | Filter, summary, table and CSV API journey; axe scan in all 8 states. |
| Judgment report `/reports/judgments` | B4, T, S | Real report API, filter, export controls. |
| Hearing report `/reports/hearings` | B4, T | Real report API, filter, export controls. |
| Workload report `/reports/workload` | B4, T | Real report API, filter, export controls. |
| Expert report `/reports/experts` | B4, T | Real report API, filter, export controls. |
| Users `/admin` | B4, T, S | Search, paging and permission gates. |
| New user `/admin/users/new` | B4, T | Real create form. |
| User detail `/admin/users/:id` | B4, T | Roles, enablement and reset controls. |
| Roles `/admin/roles` | B4, T | Permission-aware registry. |
| New role `/admin/roles/new` | B4, T | Real role and permission form. |
| Role detail `/admin/roles/:id` | B4, T, S | Shared role detail component and permission form; visual review found crowded mobile checkboxes, corrected to one labelled permission per row and rescanned. |
| Security audit `/admin/audit` | B4, T, S | Includes PR #53's task-status event translation. |
| Planned judgments `/judgments` | B4, T | Explicit existing coming-soon page; no simulated workflow. |
| Unknown route | B4, T, S | Existing not-found state. |

Shared loading, empty, failure, forbidden and session-expired branches remain covered by component tests; the browser sweeps primarily used loaded or empty fictional responses. Populated Phase 2–6 workflows and all states still require independent UAT regression before integration. The five reports, secondary screens and mobile layouts are Modern Chambers interpretations because the approved Stitch project contains only one desktop dashboard.

Evidence: [dashboard](screenshots/after/dashboard-current-matrix.json), [primary routes](screenshots/after/primary-current-matrix.json), [case detail](screenshots/after/case-details-current-matrix.json), [secondary routes](screenshots/after/secondary-current-matrix.json), [remaining routes and auth state](screenshots/after/extras-current-matrix.json), [role permission recapture](screenshots/after/role-permissions-current-matrix.json), and [inventory report](screenshots/after/inventory-current-matrix.json). See [visual gap review](PHASE-7-VISUAL-GAP.md) for direct comparison with Stitch.
