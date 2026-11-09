import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';
import { ApiError } from '../../api/http';

function isMessageKey(key: string): key is MessageKey {
  return key in en;
}

/** The catalog key `prefix.code` when the catalog has it, otherwise null (a code from a newer server is shown as it came). */
export function keyFor(prefix: string, code: string): MessageKey | null {
  const key = `${prefix}.${code}`;
  return isMessageKey(key) ? key : null;
}

/** Why a request of the assistant failed; the text the server sent is never shown. */
export function failureKey(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 400) return 'assistant.error.fields';
    if (error.status === 403) return 'assistant.error.forbidden';
    if (error.status === 404) return 'assistant.error.gone';
  }
  return 'assistant.error.generic';
}
