import { intlLocale } from '../../i18n/format';
import type { Locale } from '../../i18n/locale';

/** Formats a calendar date (`YYYY-MM-DD`) without shifting it through the browser's time zone. */
export function formatDate(date: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale(locale), {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${date}T00:00:00Z`));
}

/** "1234/2025", the number and year as Egyptian court records write them (kept in the digits recorded). */
export function numberOf(p: { courtCaseNumber: string; courtCaseYear: number }): string {
  return `${p.courtCaseNumber}/${String(p.courtCaseYear)}`;
}
