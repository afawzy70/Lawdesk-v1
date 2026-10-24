# LD-040 endpoint matrix on open PR heads (not on develop), 2026-10-24. Sources exported with the archive
# command of each head's backend/src/main into a scratch directory. Rows are the endpoints each head adds to
# develop's matrix (03-endpoint-matrix-develop.md).

## PR #40 feature/LD-026-documents ded7e3c (stacks #33 hearings, #37 tasks)
$ python3 -I tools/security/endpoint_matrix.py --root <export of pr40> --check   (exit 0)
| Method | Path | Rule | Status | ADMIN | LEGAL_MANAGER | LEGAL_STAFF | Source |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/cases/{caseId}/documents` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:79` |
| POST | `/api/v1/cases/{caseId}/documents` | `hasAuthority('DOCUMENT_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:64` |
| GET | `/api/v1/cases/{caseId}/hearings` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:52` |
| GET | `/api/v1/documents` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:89` |
| GET | `/api/v1/documents/{id}` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:97` |
| DELETE | `/api/v1/documents/{id}` | `hasAuthority('DOCUMENT_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:122` |
| GET | `/api/v1/documents/{id}/content` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:104` |
| GET | `/api/v1/hearings` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:44` |
| GET | `/api/v1/hearings/{id}` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:58` |
| PUT | `/api/v1/hearings/{id}` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:64` |
| POST | `/api/v1/hearings/{id}/result` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:70` |
| POST | `/api/v1/proceedings/{proceedingId}/hearings` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:35` |
| GET | `/api/v1/tasks` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:47` |
| POST | `/api/v1/tasks` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:38` |
| GET | `/api/v1/tasks/reminders` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:56` |
| GET | `/api/v1/tasks/{id}` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:62` |
| PUT | `/api/v1/tasks/{id}` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:68` |
| POST | `/api/v1/tasks/{id}/status` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:74` |

Fallback for requests no rule names: `authenticated`. Endpoints: 70 (AUTHENTICATED 3, FILTER 1, PERMISSION 61, PUBLIC 5).

## PR #41 feature/LD-034-xlsx-pdf 5762bee (stacks #34, #36, #39 reports and CSV)
$ python3 -I tools/security/endpoint_matrix.py --root <export of pr41> --check   (exit 0)
| Method | Path | Rule | Status | ADMIN | LEGAL_MANAGER | LEGAL_STAFF | Source |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/reports/cases` | `hasAuthority('REPORT_VIEW')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/reports/CaseInventoryController.java:24` |
| GET | `/api/v1/reports/cases/export` | `hasAuthority('REPORT_EXPORT')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/reports/ReportExportController.java:41` |
| GET | `/api/v1/reports/judgments` | `hasAuthority('REPORT_VIEW')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/reports/JudgmentReportController.java:24` |
| GET | `/api/v1/reports/judgments/export` | `hasAuthority('REPORT_EXPORT')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/reports/ReportExportController.java:48` |

Fallback for requests no rule names: `authenticated`. Endpoints: 56 (AUTHENTICATED 3, FILTER 1, PERMISSION 47, PUBLIC 5).

## PR #43 feature/LD-025-experts 0db2bef
$ python3 -I tools/security/endpoint_matrix.py --root <export of pr43> --check   (exit 0)
| Method | Path | Rule | Status | ADMIN | LEGAL_MANAGER | LEGAL_STAFF | Source |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/cases/{caseId}/documents` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:79` |
| POST | `/api/v1/cases/{caseId}/documents` | `hasAuthority('DOCUMENT_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:64` |
| GET | `/api/v1/cases/{caseId}/expert-referrals` | `hasAuthority('EXPERT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:45` |
| GET | `/api/v1/cases/{caseId}/hearings` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:52` |
| GET | `/api/v1/documents` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:89` |
| GET | `/api/v1/documents/{id}` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:97` |
| DELETE | `/api/v1/documents/{id}` | `hasAuthority('DOCUMENT_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:122` |
| GET | `/api/v1/documents/{id}/content` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:104` |
| GET | `/api/v1/expert-referrals/{id}` | `hasAuthority('EXPERT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:52` |
| PUT | `/api/v1/expert-referrals/{id}` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:58` |
| POST | `/api/v1/expert-referrals/{id}/cancel` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:78` |
| POST | `/api/v1/expert-referrals/{id}/report` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:71` |
| POST | `/api/v1/expert-referrals/{id}/sessions` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:64` |
| GET | `/api/v1/experts` | `hasAuthority('EXPERT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/experts/ExpertController.java:35` |
| POST | `/api/v1/experts` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ExpertController.java:48` |
| GET | `/api/v1/experts/{id}` | `hasAuthority('EXPERT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/experts/ExpertController.java:42` |
| PUT | `/api/v1/experts/{id}` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ExpertController.java:56` |
| GET | `/api/v1/hearings` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:44` |
| GET | `/api/v1/hearings/{id}` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:58` |
| PUT | `/api/v1/hearings/{id}` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:64` |
| POST | `/api/v1/hearings/{id}/result` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:70` |
| POST | `/api/v1/proceedings/{proceedingId}/expert-referrals` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:35` |
| POST | `/api/v1/proceedings/{proceedingId}/hearings` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:35` |
| GET | `/api/v1/tasks` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:47` |
| POST | `/api/v1/tasks` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:38` |
| GET | `/api/v1/tasks/reminders` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:56` |
| GET | `/api/v1/tasks/{id}` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:62` |
| PUT | `/api/v1/tasks/{id}` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:68` |
| POST | `/api/v1/tasks/{id}/status` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:74` |

Fallback for requests no rule names: `authenticated`. Endpoints: 81 (AUTHENTICATED 3, FILTER 1, PERMISSION 72, PUBLIC 5).

## PR #44 feature/LD-029-notes e4c8e47 (stacks #40, #43)
$ python3 -I tools/security/endpoint_matrix.py --root <export of pr44> --check   (exit 0)
| Method | Path | Rule | Status | ADMIN | LEGAL_MANAGER | LEGAL_STAFF | Source |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/cases/{caseId}/documents` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:79` |
| POST | `/api/v1/cases/{caseId}/documents` | `hasAuthority('DOCUMENT_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:64` |
| GET | `/api/v1/cases/{caseId}/expert-referrals` | `hasAuthority('EXPERT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:45` |
| GET | `/api/v1/cases/{caseId}/hearings` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:52` |
| GET | `/api/v1/cases/{caseId}/notes` | `hasAuthority('CASE_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/notes/NoteController.java:36` |
| POST | `/api/v1/cases/{caseId}/notes` | `hasAuthority('NOTE_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/notes/NoteController.java:42` |
| GET | `/api/v1/documents` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:89` |
| GET | `/api/v1/documents/{id}` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:97` |
| DELETE | `/api/v1/documents/{id}` | `hasAuthority('DOCUMENT_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:122` |
| GET | `/api/v1/documents/{id}/content` | `hasAuthority('DOCUMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/documents/DocumentController.java:104` |
| GET | `/api/v1/expert-referrals/{id}` | `hasAuthority('EXPERT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:52` |
| PUT | `/api/v1/expert-referrals/{id}` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:58` |
| POST | `/api/v1/expert-referrals/{id}/cancel` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:78` |
| POST | `/api/v1/expert-referrals/{id}/report` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:71` |
| POST | `/api/v1/expert-referrals/{id}/sessions` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:64` |
| GET | `/api/v1/experts` | `hasAuthority('EXPERT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/experts/ExpertController.java:35` |
| POST | `/api/v1/experts` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ExpertController.java:48` |
| GET | `/api/v1/experts/{id}` | `hasAuthority('EXPERT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/experts/ExpertController.java:42` |
| PUT | `/api/v1/experts/{id}` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ExpertController.java:56` |
| GET | `/api/v1/hearings` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:44` |
| GET | `/api/v1/hearings/{id}` | `hasAuthority('HEARING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:58` |
| PUT | `/api/v1/hearings/{id}` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:64` |
| POST | `/api/v1/hearings/{id}/result` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:70` |
| PUT | `/api/v1/notes/{id}` | `hasAuthority('NOTE_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/notes/NoteController.java:50` |
| DELETE | `/api/v1/notes/{id}` | `hasAuthority('NOTE_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/notes/NoteController.java:56` |
| POST | `/api/v1/proceedings/{proceedingId}/expert-referrals` | `hasAuthority('EXPERT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/experts/ReferralController.java:35` |
| POST | `/api/v1/proceedings/{proceedingId}/hearings` | `hasAuthority('HEARING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/hearings/HearingController.java:35` |
| GET | `/api/v1/tasks` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:47` |
| POST | `/api/v1/tasks` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:38` |
| GET | `/api/v1/tasks/reminders` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:56` |
| GET | `/api/v1/tasks/{id}` | `hasAuthority('TASK_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:62` |
| PUT | `/api/v1/tasks/{id}` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:68` |
| POST | `/api/v1/tasks/{id}/status` | `hasAuthority('TASK_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/tasks/TaskController.java:74` |

Fallback for requests no rule names: `authenticated`. Endpoints: 85 (AUTHENTICATED 3, FILTER 1, PERMISSION 76, PUBLIC 5).

