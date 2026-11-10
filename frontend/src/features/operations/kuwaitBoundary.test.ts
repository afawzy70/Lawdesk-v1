import { describe, expect, it } from 'vitest';
import { formatDateTime } from '../../i18n/format';
import { todayIso } from '../cases/format';
import { addDays, formatDay, legalToday } from './days';

/**
 * PINNED-CLOCK tests (LD-DOC r3): every instant below is a fixed `Date` handed to the function under test; nothing here reads
 * the real clock, so these prove the rule, not the live server. The live check is `tools/journeys/kuwait_live_check.py`.
 *
 * The rule: instants arrive in UTC, people read Kuwait time (UTC+3, no daylight saving). The Kuwait day therefore starts at
 * 21:00:00Z of the previous UTC day and the window 21:00Z-23:59:59Z (00:00-02:59:59 in Kuwait) is the one where a browser
 * or server that used its own zone would show yesterday's date.
 */
const INSTANTS = [
  // instant (UTC), Kuwait date, Kuwait wall clock
  ['2026-11-09T20:59:59Z', '2026-11-09', '23:59:59'],
  ['2026-11-09T21:00:00Z', '2026-11-10', '00:00:00'],
  ['2026-11-09T23:59:59Z', '2026-11-10', '02:59:59'],
  ['2026-11-10T00:00:00Z', '2026-11-10', '03:00:00'],
  ['2026-11-10T02:59:59Z', '2026-11-10', '05:59:59'],
] as const;

describe.each(INSTANTS)('at %s (pinned clock)', (utc, kuwaitDay, kuwaitClock) => {
  const now = new Date(utc);

  it(`"today" (filters, overdue, date inputs) is ${kuwaitDay}`, () => {
    expect(legalToday(now)).toBe(kuwaitDay);
    expect(todayIso(now)).toBe(kuwaitDay);
  });

  it('overdue is decided against the Kuwait day: due yesterday is overdue, due today is not', () => {
    const today = legalToday(now);
    const overdue = (dueOn: string) => dueOn < today;
    expect(overdue(addDays(today, -1))).toBe(true);
    expect(overdue(today)).toBe(false);
    expect(overdue(addDays(today, 1))).toBe(false);
  });

  it('a date-only field (hearing date, deadline due date) is shown as stored, never shifted', () => {
    expect(formatDay(kuwaitDay, 'en')).toContain(String(Number(kuwaitDay.slice(8))));
    expect(formatDay(kuwaitDay, 'en')).toBe(formatDay(kuwaitDay, 'en'));
  });

  it('an audit/notification time is shown in Kuwait time, English', () => {
    const text = formatDateTime(utc, 'en');
    expect(text.endsWith(kuwaitClock.slice(0, 5))).toBe(true);
    expect(text).toContain(String(Number(kuwaitDay.slice(8))));
  });

  it('an audit/notification time is shown in Kuwait time, Arabic digits', () => {
    const text = formatDateTime(utc, 'ar');
    const digits = kuwaitClock.slice(0, 5).replace(/[0-9]/g, (d) => String.fromCharCode(0x0660 + Number(d)));
    expect(text).toContain(digits);
  });
});

describe('Kuwait time does not depend on the browser zone', () => {
  it('formatDateTime defaults to Kuwait and still honours an explicit zone', () => {
    expect(formatDateTime('2026-11-09T21:00:00Z', 'en')).toBe('Tue, 10 Nov 2026, 00:00');
    expect(formatDateTime('2026-11-09T21:00:00Z', 'en', 'UTC')).toBe('Mon, 9 Nov 2026, 21:00');
    expect(formatDateTime('2026-11-09T20:59:59Z', 'en')).toBe('Mon, 9 Nov 2026, 23:59');
  });

  it('legalToday differs from the UTC date exactly in the window 21:00Z-23:59:59Z', () => {
    const utcDay = (iso: string) => iso.slice(0, 10);
    expect(legalToday(new Date('2026-11-09T20:59:59Z'))).toBe(utcDay('2026-11-09T20:59:59Z'));
    expect(legalToday(new Date('2026-11-09T21:00:00Z'))).not.toBe(utcDay('2026-11-09T21:00:00Z'));
    expect(legalToday(new Date('2026-11-09T23:59:59Z'))).not.toBe(utcDay('2026-11-09T23:59:59Z'));
    expect(legalToday(new Date('2026-11-10T00:00:00Z'))).toBe(utcDay('2026-11-10T00:00:00Z'));
    expect(legalToday(new Date('2026-11-10T02:59:59Z'))).toBe(utcDay('2026-11-10T02:59:59Z'));
  });
});
