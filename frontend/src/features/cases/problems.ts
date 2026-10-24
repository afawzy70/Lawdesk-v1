import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';

/** Turns API failures into messages of the case screens, never showing what the server sent. */

export type CaseField = 'referenceNo' | 'title' | 'caseType' | 'subject' | 'openedOn';
export type FieldErrors = Partial<Record<CaseField, MessageKey>>;

const FIELDS: readonly string[] = ['referenceNo', 'title', 'caseType', 'subject', 'openedOn'];

const CODES: Record<string, MessageKey> = {
  NotBlank: 'cases.error.NotBlank',
  NotNull: 'cases.error.NotNull',
  Size: 'cases.error.Size',
  Pattern: 'cases.error.Pattern',
  PastOrPresent: 'cases.error.PastOrPresent',
};

function isCaseField(field: string): field is CaseField {
  return FIELDS.includes(field);
}

export function fieldErrors(error: unknown): FieldErrors {
  const result: FieldErrors = {};
  if (!(error instanceof ApiError) || error.status !== 400) {
    return result;
  }
  for (const { field, code } of error.problem?.errors ?? []) {
    if (isCaseField(field)) {
      result[field] = CODES[code] ?? 'cases.error.invalid';
    }
  }
  return result;
}

/** The message for an access failure (401/403), or `fallback` for anything else. */
export function problemMessage(error: unknown, fallback: MessageKey): MessageKey {
  if (error instanceof ApiError && error.status === 401) return 'cases.problem.unauthenticated';
  if (error instanceof ApiError && error.status === 403) return 'cases.problem.forbidden';
  return fallback;
}

export function isStatus(error: unknown, status: number, rule?: string): boolean {
  return error instanceof ApiError && error.status === status && (rule === undefined || error.problem?.rule === rule);
}
