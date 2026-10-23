import type { Locale } from './locale';
import { ar } from './messages/ar';
import { en, type MessageKey, type Messages } from './messages/en';

export type { MessageKey } from './messages/en';

export const catalogs: Record<Locale, Messages> = { en, ar };

export type MessageVars = Record<string, string>;

/**
 * Looks up `key` for `locale` and replaces `{name}` placeholders from `vars`. Placeholders without a value are
 * left as-is so that a missing variable is visible rather than silently blank.
 */
export function translate(locale: Locale, key: MessageKey, vars: MessageVars = {}): string {
  const values = new Map(Object.entries(vars));
  return catalogs[locale][key].replace(/\{(\w+)\}/g, (match, name: string) => values.get(name) ?? match);
}
