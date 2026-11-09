import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';
import { failureKey as followUpFailure } from '../followup/errors';

/** What a refused or failed request of the deadline screens means; the text the server sent is never shown. */
export function failureKey(error: unknown): MessageKey {
  if (error instanceof ApiError && error.status === 422) {
    const key = `wp4.rule.${error.problem?.rule ?? ''}`;
    if (key in en) return key as MessageKey;
  }
  return followUpFailure(error);
}
