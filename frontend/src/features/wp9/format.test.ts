import { describe, expect, it } from 'vitest';
import { formatKwd } from './format';

describe('dinar amounts', () => {
  it('shows exactly three decimals in the digits of the UI language', () => {
    expect(formatKwd('125000.5', 'en')).toBe('125,000.500');
    expect(formatKwd('0.001', 'en')).toBe('0.001');
    expect(formatKwd('1500.250', 'ar')).toMatch(/^[٠-٩٬]+٫٢٥٠$/);
  });
});
