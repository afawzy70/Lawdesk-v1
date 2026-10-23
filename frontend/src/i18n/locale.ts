/**
 * Minimal locale mechanism for the shell. Phase 1 ships English only; Arabic (right-to-left) is registered so
 * that Phase 7 can add translation catalogs without changing how `<html lang dir>` is driven.
 */
const DIRECTIONS = {
  en: 'ltr',
  ar: 'rtl',
} as const;

export type Locale = keyof typeof DIRECTIONS;
export type Direction = (typeof DIRECTIONS)[Locale];

export const DEFAULT_LOCALE: Locale = 'en';

function isLocale(value: string): value is Locale {
  return Object.hasOwn(DIRECTIONS, value);
}

export function directionFor(locale: Locale): Direction {
  return DIRECTIONS[locale];
}

/** Maps a language tag such as `ar-EG` to a supported locale, falling back to the default. */
export function resolveLocale(input?: string): Locale {
  const language = (input ?? '').split('-')[0]?.toLowerCase() ?? '';
  return isLocale(language) ? language : DEFAULT_LOCALE;
}

export function applyDocumentLocale(locale: Locale, doc: Document = document): void {
  doc.documentElement.lang = locale;
  doc.documentElement.dir = directionFor(locale);
}
