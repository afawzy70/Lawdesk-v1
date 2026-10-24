import { intlLocale } from '../../i18n/format';
import type { Locale } from '../../i18n/locale';

/** A calendar date (`yyyy-MM-dd`) as "1 Oct 2026" in the UI language; never shifted by the browser's zone. */
export function formatDay(isoDate: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale(locale), {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${isoDate}T00:00:00Z`));
}
