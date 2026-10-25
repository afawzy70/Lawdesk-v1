import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';

/** Shared rules of the report screens (docs/delivery/PHASE-6-ACCEPTANCE.md, common rules 5 and 8). */

export const REPORTS_PATH = '/reports';
export const JUDGMENTS_PATH = '/reports/judgments';
export const HEARINGS_PATH = '/reports/hearings';
export const WORKLOAD_PATH = '/reports/workload';
export const EXPERTS_PATH = '/reports/experts';

/** The message for a failed report request; never shows what the server sent. */
export function failureMessage(error: unknown): MessageKey {
  if (error instanceof ApiError && error.status === 400) return 'reports.list.invalid';
  if (error instanceof ApiError && error.status === 422 && error.problem?.rule === 'DATE_RANGE_INVALID') {
    return 'reports.list.dateRange';
  }
  return 'reports.list.failed';
}

export function isForbidden(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403;
}
