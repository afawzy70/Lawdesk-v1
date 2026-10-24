import { describe, expect, it } from 'vitest';
import { addDays, formatDay, formatTime, legalToday } from './days';

/** Calendar days in Egypt (docs/delivery/PHASE-5-ACCEPTANCE.md, common rule 9). */
describe('legal days', () => {
  it('takes today from the Egyptian clock, not the browser', () => {
    expect(legalToday(new Date('2025-03-01T21:59:00Z'))).toBe('2025-03-01');
    expect(legalToday(new Date('2025-03-01T22:00:00Z'))).toBe('2025-03-02');
  });

  it('adds calendar days across months and leap years', () => {
    expect(addDays('2024-02-28', 1)).toBe('2024-02-29');
    expect(addDays('2025-03-02', 30)).toBe('2025-04-01');
    expect(addDays('2025-01-01', -1)).toBe('2024-12-31');
  });

  it('formats days and times in the locale without shifting them', () => {
    expect(formatDay('2025-03-02', 'en')).toBe('Sun, 2 Mar 2025');
    expect(formatTime('09:30', 'en')).toBe('09:30');
    expect(formatTime('17:05', 'ar')).toBe('١٧:٠٥');
  });
});
