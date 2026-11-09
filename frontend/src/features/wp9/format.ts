import { formatNumber } from '../../i18n/format';
import type { Locale } from '../../i18n/locale';

/** A dinar amount (text with up to three decimals) with exactly three decimals in the UI language's digits. */
export function formatKwd(amount: string, locale: Locale): string {
  return formatNumber(Number(amount), locale, { minimumFractionDigits: 3, maximumFractionDigits: 3 });
}
