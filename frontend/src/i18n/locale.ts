/**
 * Minimal locale mechanism for the shell: English (left-to-right) and Arabic (right-to-left). The early preview
 * (docs/delivery/PREVIEW.md) adds message catalogs in `catalog.ts`; full i18n/RTL remains Phase 7 (LD-035/LD-036).
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

/**
 * In-app link that carries the UI language in the query string. The language lives in the URL (not in browser
 * storage) so it survives reloads and navigation without writing anything to localStorage/sessionStorage.
 */
export function withLang(path: string, locale: Locale): string {
  return `${path}?lang=${locale}`;
}

export function applyDocumentLocale(locale: Locale, doc: Document = document): void {
  doc.documentElement.lang = locale;
  doc.documentElement.dir = directionFor(locale);
}
