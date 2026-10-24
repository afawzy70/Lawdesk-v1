import { intlLocale } from '../../i18n/format';
import type { Locale } from '../../i18n/locale';

/** A calendar date (`yyyy-MM-dd`) as day/month/year in the UI language; never shifted by the browser's zone. */
export function formatDate(isoDate: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale(locale), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${isoDate}T00:00:00Z`));
}

/** Today in the browser's zone as `yyyy-MM-dd`, the value format of a date input. */
export function todayIso(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${String(now.getFullYear())}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
