| Method | Path | Rule | Status | ADMIN | LEGAL_MANAGER | LEGAL_STAFF | Source |
|---|---|---|---|---|---|---|---|
| GET | `/actuator/health` | `permitAll() (filter chain)` | PUBLIC | yes | yes | yes | `backend/src/main/java/com/lawdesk/platform/SecurityConfig.java:66` |
| GET | `/actuator/health/**` | `permitAll() (filter chain)` | PUBLIC | yes | yes | yes | `backend/src/main/java/com/lawdesk/platform/SecurityConfig.java:66` |
| GET | `/actuator/info` | `permitAll() (filter chain)` | PUBLIC | yes | yes | yes | `backend/src/main/java/com/lawdesk/platform/SecurityConfig.java:66` |
| GET | `/api/v1/audit` | `hasAuthority('AUDIT_VIEW')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/audit/AuditController.java:44` |
| GET | `/api/v1/auth/csrf` | (none) | PUBLIC | yes | yes | yes | `backend/src/main/java/com/lawdesk/security/AuthController.java:52` |
| POST | `/api/v1/auth/login` | (none) | PUBLIC | yes | yes | yes | `backend/src/main/java/com/lawdesk/security/AuthController.java:59` |
| POST | `/api/v1/auth/logout` | `LogoutFilter (CSRF token required)` | FILTER | yes | yes | yes | `backend/src/main/java/com/lawdesk/platform/SecurityConfig.java:77` |
| GET | `/api/v1/cases` | `hasAuthority('CASE_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/CaseController.java:39` |
| POST | `/api/v1/cases` | `hasAuthority('CASE_CREATE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/CaseController.java:31` |
| GET | `/api/v1/cases/{caseId}/judgments` | `hasAuthority('JUDGMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/proceedings/JudgmentController.java:33` |
| GET | `/api/v1/cases/{caseId}/proceedings` | `hasAuthority('PROCEEDING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/proceedings/ProceedingController.java:33` |
| POST | `/api/v1/cases/{caseId}/proceedings` | `hasAuthority('PROCEEDING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/proceedings/ProceedingController.java:39` |
| GET | `/api/v1/cases/{caseId}/special-decisions` | `hasAuthority('JUDGMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/proceedings/SpecialDecisionController.java:33` |
| GET | `/api/v1/cases/{id}` | `hasAuthority('CASE_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/CaseController.java:45` |
| PUT | `/api/v1/cases/{id}` | `hasAuthority('CASE_EDIT')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/CaseController.java:63` |
| GET | `/api/v1/cases/{id}/parties` | `hasAuthority('CASE_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/PartyController.java:63` |
| POST | `/api/v1/cases/{id}/parties` | `hasAuthority('PARTY_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/PartyController.java:69` |
| DELETE | `/api/v1/cases/{id}/parties/{partyId}` | `hasAuthority('PARTY_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/PartyController.java:76` |
| POST | `/api/v1/cases/{id}/status` | `hasAuthority('CASE_STATUS_CHANGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/cases/CaseController.java:51` |
| GET | `/api/v1/cases/{id}/timeline` | `hasAuthority('CASE_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/CaseController.java:57` |
| GET | `/api/v1/courts` | `hasAuthority('COURT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/courts/CourtController.java:35` |
| POST | `/api/v1/courts` | `hasAuthority('COURT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/courts/CourtController.java:48` |
| GET | `/api/v1/courts/{id}` | `hasAuthority('COURT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/courts/CourtController.java:42` |
| PUT | `/api/v1/courts/{id}` | `hasAuthority('COURT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/courts/CourtController.java:57` |
| GET | `/api/v1/judgments/{id}` | `hasAuthority('JUDGMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/proceedings/JudgmentController.java:48` |
| PUT | `/api/v1/judgments/{id}` | `hasAuthority('JUDGMENT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/proceedings/JudgmentController.java:54` |
| GET | `/api/v1/me` | `isAuthenticated()` | AUTHENTICATED | yes | yes | yes | `backend/src/main/java/com/lawdesk/security/MeController.java:53` |
| POST | `/api/v1/me/password` | `isAuthenticated()` | AUTHENTICATED | yes | yes | yes | `backend/src/main/java/com/lawdesk/security/MeController.java:59` |
| GET | `/api/v1/parties` | `hasAuthority('CASE_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/PartyController.java:44` |
| POST | `/api/v1/parties` | `hasAuthority('PARTY_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/PartyController.java:36` |
| GET | `/api/v1/parties/{id}` | `hasAuthority('CASE_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/PartyController.java:50` |
| PUT | `/api/v1/parties/{id}` | `hasAuthority('PARTY_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/cases/PartyController.java:56` |
| GET | `/api/v1/proceedings/{id}` | `hasAuthority('PROCEEDING_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/proceedings/ProceedingController.java:48` |
| PUT | `/api/v1/proceedings/{id}` | `hasAuthority('PROCEEDING_MANAGE')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/proceedings/ProceedingController.java:54` |
| POST | `/api/v1/proceedings/{proceedingId}/judgment` | `hasAuthority('JUDGMENT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/proceedings/JudgmentController.java:39` |
| POST | `/api/v1/proceedings/{proceedingId}/special-decisions` | `hasAuthority('JUDGMENT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/proceedings/SpecialDecisionController.java:39` |
| GET | `/api/v1/release` | `isAuthenticated()` | AUTHENTICATED | yes | yes | yes | `backend/src/main/java/com/lawdesk/platform/release/ReleaseController.java:23` |
| GET | `/api/v1/reports/dashboard` | `hasAuthority('REPORT_VIEW')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/reports/DashboardController.java:48` |
| GET | `/api/v1/roles` | `hasAuthority('ROLE_VIEW') or hasAuthority('USER_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/RoleController.java:40` |
| POST | `/api/v1/roles` | `hasAuthority('ROLE_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/RoleController.java:52` |
| GET | `/api/v1/roles/{id}` | `hasAuthority('ROLE_VIEW') or hasAuthority('USER_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/RoleController.java:46` |
| PUT | `/api/v1/roles/{id}` | `hasAuthority('ROLE_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/RoleController.java:59` |
| GET | `/api/v1/special-decisions/{id}` | `hasAuthority('JUDGMENT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/proceedings/SpecialDecisionController.java:48` |
| PUT | `/api/v1/special-decisions/{id}` | `hasAuthority('JUDGMENT_MANAGE')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/proceedings/SpecialDecisionController.java:54` |
| GET | `/api/v1/users` | `hasAuthority('USER_VIEW')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/users/UserController.java:41` |
| POST | `/api/v1/users` | `hasAuthority('USER_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/UserController.java:53` |
| GET | `/api/v1/users/directory` | `hasAnyAuthority('TASK_VIEW', 'TASK_MANAGE', 'REPORT_VIEW')` | PERMISSION | yes | yes | yes | `backend/src/main/java/com/lawdesk/users/UserDirectoryController.java:21` |
| GET | `/api/v1/users/{id}` | `hasAuthority('USER_VIEW')` | PERMISSION | yes | yes | - | `backend/src/main/java/com/lawdesk/users/UserController.java:47` |
| PUT | `/api/v1/users/{id}` | `hasAuthority('USER_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/UserController.java:60` |
| PUT | `/api/v1/users/{id}/enabled` | `hasAuthority('USER_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/UserController.java:66` |
| POST | `/api/v1/users/{id}/password-reset` | `hasAuthority('USER_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/UserController.java:79` |
| PUT | `/api/v1/users/{id}/roles` | `hasAuthority('USER_MANAGE')` | PERMISSION | yes | - | - | `backend/src/main/java/com/lawdesk/users/UserController.java:72` |

Fallback for requests no rule names: `authenticated`. Endpoints: 52 (AUTHENTICATED 3, FILTER 1, PERMISSION 43, PUBLIC 5).
