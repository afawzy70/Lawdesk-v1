import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';

/** What a refused or failed request of the WP3 screens means; the text the server sent is never shown. */

function isMessageKey(key: string): key is MessageKey {
  return key in en;
}

export function failureKey(error: unknown): MessageKey {
  if (!(error instanceof ApiError)) return 'wp3.error.save';
  switch (error.status) {
    case 400:
      return 'wp3.error.fields';
    case 401:
      return 'wp3.error.signIn';
    case 403:
      return 'wp3.error.forbidden';
    case 404:
      return 'wp3.error.gone';
    case 409:
      return 'wp3.error.stale';
    case 422: {
      const key = `wp3.rule.${error.problem?.rule ?? ''}`;
      return isMessageKey(key) ? key : 'wp3.rule.unknown';
    }
    default:
      return 'wp3.error.save';
  }
}

/** Whether a failed load means "the user may not open this area" (the section is then simply left out). */
export function isForbidden(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403;
}
