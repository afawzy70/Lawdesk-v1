import type { Locale } from './locale';

/**
 * Intl locale per UI locale. Arabic uses `ar-EG`, whose default numbering system is Arabic-Indic digits;
 * English uses `en-GB` (day-month-year, 24-hour clock), which suits the Egyptian/Arab legal context.
 */
const INTL_LOCALES: Record<Locale, string> = { en: 'en-GB', ar: 'ar-EG' };

export function intlLocale(locale: Locale): string {
  return INTL_LOCALES[locale];
}

/** Formats an instant as weekday, date and time. `timeZone` defaults to the browser's zone. */
export function formatDateTime(value: string | Date, locale: Locale, timeZone?: string): string {
  return new Intl.DateTimeFormat(intlLocale(locale), {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    ...(timeZone === undefined ? {} : { timeZone }),
  }).format(typeof value === 'string' ? new Date(value) : value);
}

export function formatNumber(value: number, locale: Locale, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(intlLocale(locale), options).format(value);
}

/** Formats a ratio (0–1) as a whole-number percentage. */
export function formatPercent(ratio: number, locale: Locale): string {
  return formatNumber(ratio, locale, { style: 'percent', maximumFractionDigits: 0 });
}
