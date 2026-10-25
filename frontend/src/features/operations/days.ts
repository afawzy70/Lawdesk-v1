import { intlLocale } from '../../i18n/format';
import type { Locale } from '../../i18n/locale';

/**
 * Calendar days of the legal operations screens (docs/delivery/PHASE-5-ACCEPTANCE.md, common rule 9): "today" is the
 * date in Egypt, and dates (`YYYY-MM-DD`) are shown without shifting them through the browser's time zone.
 */
export const LEGAL_TIME_ZONE = 'Africa/Cairo';

/** Today's date in Egypt as `YYYY-MM-DD`. */
export function legalToday(now: Date = new Date()): string {
  // en-CA writes dates as YYYY-MM-DD.
  return new Intl.DateTimeFormat('en-CA', { timeZone: LEGAL_TIME_ZONE }).format(now);
}

/** `date` plus `days` calendar days. */
export function addDays(date: string, days: number): string {
  const day = new Date(`${date}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + days);
  return day.toISOString().slice(0, 10);
}

/** A day with its weekday, as a hearing calendar shows it ("Sun, 2 Mar 2025"). */
export function formatDay(date: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale(locale), {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${date}T00:00:00Z`));
}

/** A time of day (`HH:mm`) in the locale's digits, on a 24-hour clock. */
export function formatTime(time: string, locale: Locale): string {
  return new Intl.DateTimeFormat(intlLocale(locale), {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZone: 'UTC',
  }).format(new Date(`1970-01-01T${time}:00Z`));
}
