import { useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';

/** The constraint the backend names (architecture/CONTRACTS.md §4), explained to the user. */
function fieldMessage(code: string): MessageKey {
  switch (code) {
    case 'NotBlank':
    case 'NotNull':
      return 'proceedings.fieldError.required';
    case 'Size':
      return 'proceedings.fieldError.tooLong';
    case 'Min':
    case 'Max':
      return 'proceedings.fieldError.range';
    case 'PastOrPresent':
      return 'proceedings.fieldError.future';
    default:
      return 'proceedings.fieldError.invalid';
  }
}

function isMessageKey(key: string): key is MessageKey {
  return key in en;
}

/** What a refused save means: a 422 names its rule (docs/delivery/PHASE-4-ACCEPTANCE.md), a 409 is form-specific. */
export function saveError(error: unknown, conflict: MessageKey): MessageKey {
  if (!(error instanceof ApiError)) return 'proceedings.error.save';
  switch (error.status) {
    case 400:
      return 'proceedings.error.fields';
    case 401:
      return 'proceedings.error.signIn';
    case 403:
      return 'proceedings.error.manageForbidden';
    case 409:
      return conflict;
    case 422: {
      const key = `proceedings.rule.${error.problem?.rule ?? ''}`;
      return isMessageKey(key) ? key : 'proceedings.rule.unknown';
    }
    default:
      return 'proceedings.error.save';
  }
}

/**
 * Field-level errors of one form: `control(field)` gives a control its id and, when the backend refused it, marks it
 * invalid and points `aria-describedby` at the message rendered by `message(field)`.
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

  const reset = () => {
    setErrors({});
    setFormError(null);
  };

  const control = (field: F) => {
    const invalid = errors[field] !== undefined;
    return {
      id: `${id}-${field}`,
      'aria-invalid': invalid ? true : undefined,
      'aria-describedby': invalid ? `${id}-${field}-error` : undefined,
    };
  };

  return { errors, formError, fail, reset, control, errorId: (field: F) => `${id}-${field}-error` };
}
