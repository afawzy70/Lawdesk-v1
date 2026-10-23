import { describe, expect, it } from 'vitest';
import { formatDateTime, formatNumber, formatPercent, intlLocale } from './format';

const HEARING = '2026-11-03T09:30:00Z';

describe('intlLocale', () => {
  it('uses Egyptian Arabic (Arabic-Indic digits) and British English', () => {
    expect(intlLocale('ar')).toBe('ar-EG');
    expect(intlLocale('en')).toBe('en-GB');
  });
});

describe('formatDateTime', () => {
  it('formats a fixed instant in English for a fixed time zone', () => {
    expect(formatDateTime(HEARING, 'en', 'UTC')).toBe('Tue, 3 Nov 2026, 09:30');
  });

  it('formats the same instant in Arabic with Arabic-Indic digits', () => {
    const text = formatDateTime(HEARING, 'ar', 'UTC');
    expect(text).toContain('نوفمبر');
    expect(text).toContain('٢٠٢٦');
    expect(text).toContain('٠٩:٣٠');
    expect(text).not.toMatch(/[0-9]/);
  });

  it('respects the time zone it is given', () => {
    expect(formatDateTime(HEARING, 'en', 'Africa/Cairo')).toBe('Tue, 3 Nov 2026, 11:30');
  });

  it('accepts Date objects', () => {
    expect(formatDateTime(new Date(HEARING), 'en', 'UTC')).toBe('Tue, 3 Nov 2026, 09:30');
  });
});

describe('formatNumber', () => {
  it('groups digits per locale', () => {
    expect(formatNumber(1234, 'en')).toBe('1,234');
    expect(formatNumber(1234, 'ar')).toBe('١٬٢٣٤');
  });

  it('pads sample reference numbers', () => {
    expect(formatNumber(7, 'en', { minimumIntegerDigits: 3 })).toBe('007');
    expect(formatNumber(7, 'ar', { minimumIntegerDigits: 3 })).toBe('٠٠٧');
  });
});

describe('formatPercent', () => {
  it('formats a ratio as a whole percentage', () => {
    expect(formatPercent(0.5, 'en')).toBe('50%');
    expect(formatPercent(1 / 3, 'en')).toBe('33%');
    expect(formatPercent(0.5, 'ar')).toMatch(/^٥٠/);
  });
});
