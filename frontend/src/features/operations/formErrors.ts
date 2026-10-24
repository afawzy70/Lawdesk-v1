import { useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';

/** Shared form pieces of the legal operations screens. The backend validates; the forms explain its answers. */

export const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

/** The constraint the backend names (architecture/CONTRACTS.md §4), explained to the user. */
export function fieldMessage(code: string): MessageKey {
  switch (code) {
    case 'NotBlank':
    case 'NotNull':
      return 'operations.fieldError.required';
    case 'Size':
      return 'operations.fieldError.tooLong';
    case 'Range':
      return 'operations.fieldError.range';
    case 'Null':
      return 'operations.fieldError.notAllowed';
    default:
      return 'operations.fieldError.invalid';
  }
}

function isMessageKey(key: string): key is MessageKey {
  return key in en;
}

/** What a refused save means: a 422 names its rule (docs/delivery/PHASE-5-ACCEPTANCE.md), a 409 is form-specific. */
export function saveError(error: unknown, conflict: MessageKey): MessageKey {
  if (!(error instanceof ApiError)) return 'operations.error.save';
  switch (error.status) {
    case 400:
      return 'operations.error.fields';
    case 401:
      return 'operations.error.signIn';
    case 403:
      return 'operations.error.manageForbidden';
    case 404:
      return 'operations.error.gone';
    case 409:
      return conflict;
    case 422: {
      const key = `operations.rule.${error.problem?.rule ?? ''}`;
      return isMessageKey(key) ? key : 'operations.rule.unknown';
    }
    default:
      return 'operations.error.save';
  }
}

/** What a failed load means. */
export function loadError(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'operations.error.viewForbidden';
    if (error.status === 404) return 'operations.error.caseNotFound';
    if (error.status === 401) return 'operations.error.signIn';
  }
  return 'operations.error.load';
}

/**
 * Field-level errors of one form: `control(field)` gives a control its id and, when the backend refused it, marks it
 * invalid and points `aria-describedby` at the message `Field` renders.
 */
export function useFormErrors<F extends string>() {
  const id = useId();
  const [errors, setErrors] = useState<Partial<Record<F, MessageKey>>>({});
  const [formError, setFormError] = useState<MessageKey | null>(null);

  const fail = (error: unknown, conflict: MessageKey, fields: readonly F[]) => {
    const next: Partial<Record<F, MessageKey>> = {};
    if (error instanceof ApiError) {
      for (const { field, code } of error.problem?.errors ?? []) {
        const known = fields.find((f) => f === field);
        if (known !== undefined) next[known] ??= fieldMessage(code);
      }
    }
    setErrors(next);
    setFormError(saveError(error, conflict));
  };

  const control = (field: F) => {
    const invalid = errors[field] !== undefined;
    return {
      id: `${id}-${field}`,
      'aria-invalid': invalid ? true : undefined,
      'aria-describedby': invalid ? `${id}-${field}-error` : undefined,
    };
  };

  return { errors, formError, fail, control, errorId: (field: F) => `${id}-${field}-error` };
}
