# Phase 7 — Complete implemented screen inventory

Baseline: `origin/develop` at `b849b1cabcbebd4df058e4ab989d1e8c1a562899` on 2026-10-25. Source of truth: `frontend/src/App.tsx`, `features/operations/routes.tsx`, `features/reports/routes.tsx`, `architecture/CONTRACTS.md` and Phase 2–6 acceptance files. All routes use the shared English LTR and Arabic RTL catalogue and layout. “Mobile” below describes the intended redesign behavior and requires browser verification; it is not a QA verdict. Permission names are backend gates; hiding navigation is only a convenience. Tests listed are developer regression coverage, not independent QA.

| Route / state | Existing component | Real API / permission | Current workflow and redesigned screen | Mobile / AR+EN | Existing tests |
| --- | --- | --- | --- | --- | --- |
| Any path, signed out | `LoginPage` via `SessionGate` | `/api/v1/me`, session login; public entry | Credential form, refused/unavailable/expired states; editorial chambers entry | Single paper form; RTL labels | `Auth.test.tsx`, `App.test.tsx` |
| Any path, forced change | `ChangePasswordPage`, `ChangePasswordForm` via `SessionGate` | password change; authenticated | Required password replacement before app; same paper form and validation | Single column; RTL labels | `Auth.test.tsx`, `Profile.test.tsx` |
| `/profile` | `ProfilePage` | `/api/v1/me`; authenticated | Identity, roles, permissions and voluntary password change; account dossier | Stacked facts and isolated email/username | `Profile.test.tsx` |
| `/` | `DashboardPage` | `GET /api/v1/reports/dashboard`; `REPORT_VIEW` for figures | Real case, proceeding, judgment and operations counts; editorial metric register; no metrics without permission | Metrics stack at 320; bilingual labels | `DashboardPage.test.tsx`, `dashboardApi.test.ts` |
| `/cases` | `CaseListPage` | `GET /api/v1/cases`; `CASE_VIEW` | URL search, type/status filter, paging; legal registry table | Labelled local table scroll | `CaseListPage.test.tsx`, `CaseMobileLayout.test.tsx` |
| `/cases/new` | `CaseCreatePage`, `CaseForm` | `POST /api/v1/cases`; `CASE_CREATE` | Real creation/validation; dossier form | Single column | `CaseCreatePage.test.tsx` |
| `/cases/:id` | `CaseDetailsPage` | `GET /api/v1/cases/:id`; `CASE_VIEW` | Identity, status, parties, judicial lifecycle, legal operations and timeline; case dossier | Stacked facts, local tables and sections | `CaseDetailsPage.test.tsx`, `CaseMobileLayout.test.tsx` |
| `/cases/:id/edit` | `CaseEditPage`, `CaseForm` | `PUT /api/v1/cases/:id`; `CASE_EDIT` | Versioned edit and validation; dossier form | Single column | `CaseEditPage.test.tsx` |
| Case detail: parties | `CasePartiesSection` | `/api/v1/cases/:id/parties`, `/api/v1/parties`; `CASE_VIEW` + mutation gate | Find/create/link/unlink party; litigant section | Local table scroll, stacked forms | `CaseDetailsPage.test.tsx` |
| Case detail: status / timeline | `CaseStatusForm`, `CaseTimelineList` | `/api/v1/cases/:id/status`, `/timeline`; backend case gates | Allowed status moves, audit chronology; rule-based case record | Vertical sequence | `CaseDetailsPage.test.tsx`, `timelineEvents.test.ts` |
| Case detail: proceedings | `CaseProceedings`, `ProceedingForms` | `/api/v1/cases/:id/proceedings`, `/api/v1/proceedings/:id/{judgment,special-decisions}`; `PROCEEDING_VIEW/MANAGE`, `JUDGMENT_VIEW/MANAGE` | First instance, appeal, cassation, judgments and special decisions; chronological stage cards | Stacked stages/forms | `CaseProceedings.test.tsx` |
| Case detail: hearings | `CaseHearings`, `HearingForms` | `/api/v1/cases/:id/hearings`, `/api/v1/proceedings/:id/hearings`, `/api/v1/hearings/:id`; `HEARING_VIEW/MANAGE` | Schedule, reschedule, result; hearing record | Stacked, local scroll | `CaseHearings.test.tsx` |
| Case detail: tasks / reminders | `CaseTasks`, `TaskForm` | `/api/v1/tasks`, `/api/v1/tasks/:id`; `TASK_VIEW/MANAGE` | Add/edit/move case task; matter work list | Stacked controls | `CaseTasks.test.tsx` |
| Case detail: documents | `CaseDocuments` | `/api/v1/cases/:id/documents`, `/api/v1/documents/:id/content`; `DOCUMENT_VIEW/MANAGE` | Upload/download/delete actual attachments; document register | File actions remain visible | `CaseDocuments.test.tsx` |
| Case detail: experts / referrals | `CaseExperts`, `ReferralForms` | `/api/v1/cases/:id/expert-referrals`, `/api/v1/proceedings/:id/expert-referrals`; `EXPERT_VIEW/MANAGE` | Referral, session, report and cancellation; expert record | Stacked controls | `CaseExperts.test.tsx` |
| Case detail: notes | `CaseNotes` | `/api/v1/cases/:id/notes`, `/api/v1/notes/:id`; `CASE_VIEW`, `NOTE_MANAGE` | Create/edit/delete legal notes and activity | Full width text | `CaseNotes.test.tsx` |
| `/courts` | `CourtsPage`, `CourtForm` | `/api/v1/courts`; `COURT_VIEW/MANAGE` | Court lookup, filter, paging, create/edit; court registry | Local table scroll | `CourtsPage.test.tsx` |
| `/hearings` | `HearingsPage` | `/api/v1/hearings`; `HEARING_VIEW/MANAGE` | Hearing calendar/filter, reschedule and result | Stacked filters, local table scroll | `HearingsPage.test.tsx` |
| `/tasks` | `TasksPage` | `/api/v1/tasks`, `/reminders`; `TASK_VIEW/MANAGE` | Mine/all, due/status filter, reminders, CRUD/status | Wrap controls, local table scroll | `TasksPage.test.tsx` |
| `/documents` | `DocumentsPage` | `/api/v1/documents`, `/:id/content`; `DOCUMENT_VIEW` | Search, paging and attachment download | Local table scroll; isolated filenames | `DocumentsPage.test.tsx` |
| `/experts` | `ExpertsPage`, `ExpertForm` | `/api/v1/experts`; `EXPERT_VIEW/MANAGE` | Expert registry/search/create/edit/deactivate | Stacked form and table scroll | `ExpertsPage.test.tsx` |
| `/reports` | `CaseInventoryPage` | `/api/v1/reports/cases`, `/export`; `REPORT_VIEW`, `REPORT_EXPORT` | Real case summary, filters/sort/paging and CSV/XLSX/PDF | Local wide table scroll; export labels | `CaseInventoryPage.test.tsx` |
| `/reports/judgments` | `JudgmentReportPage` | `/api/v1/reports/judgments`, `/export`; same gates | Real judgment filters/sort/paging/export | Local wide table scroll | `JudgmentReportPage.test.tsx` |
| `/reports/hearings` | `HearingReportPage` | `/api/v1/reports/hearings`, `/export`; same gates | Real hearing report/filter/export | Local wide table scroll | `OperationsReports.test.tsx` |
| `/reports/workload` | `WorkloadReportPage` | `/api/v1/reports/workload`, `/export`; same gates | Real workload summary/export | Stacked summary and table | `OperationsReports.test.tsx` |
| `/reports/experts` | `ExpertReportPage` | `/api/v1/reports/experts`, `/export`; same gates | Real expert workload/export | Stacked summary and table | `OperationsReports.test.tsx` |
| `/admin` | `UsersPage` | `/api/v1/users`; `USER_VIEW/MANAGE` | User search/paging and management entry; identity registry | Local table scroll | `UsersAdmin.test.tsx` |
| `/admin/users/new` | `NewUserPage` | `POST /api/v1/users`; `USER_MANAGE` | User creation/roles/validation | Single-column form | `UsersAdmin.test.tsx` |
| `/admin/users/:id` | `UserDetailsPage` | `/api/v1/users/:id`, `/roles`, `/enabled`, `/password-reset`; `USER_VIEW/MANAGE` | Profile, enablement, roles, reset; user dossier | Stacked forms | `UsersAdmin.test.tsx` |
| `/admin/roles` | `RolesPage` | `/api/v1/roles`; `ROLE_VIEW/MANAGE` | Role registry; permission-aware actions | Local table scroll | `RolesAudit.test.tsx` |
| `/admin/roles/new` | `RoleDetailsPage` | `POST /api/v1/roles`; `ROLE_MANAGE` | Role and permission creation | Wrapped permission controls | `RolesAudit.test.tsx` |
| `/admin/roles/:id` | `RoleDetailsPage` | `/api/v1/roles/:id`; `ROLE_VIEW/MANAGE` | View/edit role and permissions | Wrapped permission controls | `RolesAudit.test.tsx` |
| `/admin/audit` | `AuditPage` | `/api/v1/audit`; `AUDIT_VIEW` | Filtered, paged security audit; chronological register | Local table scroll | `RolesAudit.test.tsx` |
| `/judgments` | `ComingSoonPage` | None; planned route | Existing placeholder only; clearly marked planned | Single column | `navigation.test.ts`, `App.test.tsx` |
| Unknown route | `NotFoundPage` | None | Not-found state | Single column | `App.test.tsx` |

## Shared states and verification gate

Every data screen has loading, failed, empty and forbidden branches where its API permits them. The shell also has session loading, expired sign-in and error boundary. All these branches receive Modern Chambers paper, rule and status treatment; the underlying copy, retries, permission checks and API calls remain unchanged. Visual verification matrix must cover 320, 375, 768 and 1366 px, both `ar` and `en`, keyboard navigation, large names, mixed Arabic/Latin references, export controls and real authenticated journeys. Current developer tests do not substitute for independent QA.
