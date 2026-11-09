import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';

/** Explains what the backend refused, in the user's words (architecture/CONTRACTS.md §4: 422 names its rule). */
export function archiveError(error: unknown): MessageKey {
  if (!(error instanceof ApiError)) return 'wp5.error.save';
  switch (error.status) {
    case 400: {
      const codes = (error.problem?.errors ?? []).map((e) => e.code);
      if (codes.includes('UnsupportedFormat')) return 'wp5.error.format';
      if (codes.includes('MaxSize')) return 'wp5.error.tooLarge';
      return 'wp5.error.fields';
    }
    case 401:
      return 'wp5.error.signIn';
    case 403:
      return 'wp5.error.forbidden';
    case 404:
      return 'wp5.error.gone';
    case 409:
      return 'wp5.error.stale';
    case 413:
      return 'wp5.error.tooLarge';
    case 422: {
      const key = `wp5.rule.${error.problem?.rule ?? ''}`;
      return key in en ? (key as MessageKey) : 'wp5.rule.unknown';
    }
    default:
      return 'wp5.error.save';
  }
}

/** The HTTP status of a failed load (0 when the API cannot be reached). */
export const statusOf = (error: unknown): number => (error instanceof ApiError ? error.status : 0);
