/**
 * Mirror of com.lawdesk.security.Permission (architecture/CONTRACTS.md §3); tools/tests/test_contracts.py keeps both
 * lists equal. Used only to decide what to show: the backend enforces every permission itself.
 */
export const PERMISSIONS = [
  'USER_VIEW',
  'USER_MANAGE',
  'ROLE_VIEW',
  'ROLE_MANAGE',
  'AUDIT_VIEW',
  'CASE_VIEW',
  'CASE_CREATE',
  'CASE_EDIT',
  'CASE_DELETE',
  'CASE_STATUS_CHANGE',
  'PARTY_MANAGE',
  'COURT_VIEW',
  'COURT_MANAGE',
  'PROCEEDING_VIEW',
  'PROCEEDING_MANAGE',
  'JUDGMENT_VIEW',
  'JUDGMENT_MANAGE',
  'HEARING_VIEW',
  'HEARING_MANAGE',
  'EXPERT_VIEW',
  'EXPERT_MANAGE',
  'DOCUMENT_VIEW',
  'DOCUMENT_MANAGE',
  'TASK_VIEW',
  'TASK_MANAGE',
  'NOTE_MANAGE',
  'REPORT_VIEW',
  'REPORT_EXPORT',
] as const;

export type Permission = (typeof PERMISSIONS)[number];
