import { describe, expect, it } from 'vitest';
import { periodOf, shift } from './api';

/** The periods of the calendar views: weeks run Sunday to Saturday, months from the first to the last day. */
describe('periodOf', () => {
  it('is the day itself for a day view', () => {
    expect(periodOf('DAY', '2027-03-10')).toEqual({ from: '2027-03-10', to: '2027-03-10' });
  });

  it('runs a week from Sunday to Saturday', () => {
    expect(periodOf('WEEK', '2027-03-10')).toEqual({ from: '2027-03-07', to: '2027-03-13' });
    expect(periodOf('WEEK', '2027-03-07')).toEqual({ from: '2027-03-07', to: '2027-03-13' });
    expect(periodOf('WEEK', '2027-03-13')).toEqual({ from: '2027-03-07', to: '2027-03-13' });
    expect(periodOf('WEEK', '2027-01-01')).toEqual({ from: '2026-12-27', to: '2027-01-02' });
  });

  it('runs a month, and a list, from its first to its last day', () => {
    expect(periodOf('MONTH', '2027-02-14')).toEqual({ from: '2027-02-01', to: '2027-02-28' });
    expect(periodOf('LIST', '2028-02-29')).toEqual({ from: '2028-02-01', to: '2028-02-29' });
  });
});

describe('shift', () => {
  it('moves a day, a week or a month, never skipping a month', () => {
    expect(shift('DAY', '2027-03-31', 1)).toBe('2027-04-01');
    expect(shift('DAY', '2027-03-01', -1)).toBe('2027-02-28');
    expect(shift('WEEK', '2027-03-10', 1)).toBe('2027-03-17');
    expect(shift('WEEK', '2027-03-03', -1)).toBe('2027-02-24');
    expect(shift('MONTH', '2027-01-31', 1)).toBe('2027-02-01');
    expect(shift('MONTH', '2027-01-15', -1)).toBe('2026-12-01');
    expect(shift('LIST', '2027-12-20', 1)).toBe('2028-01-01');
  });
});
