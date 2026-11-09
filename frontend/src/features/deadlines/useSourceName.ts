import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import type { Deadline } from './api';

/** The name of what a deadline runs from, in the user's language. */
export function useSourceName() {
  const { t, locale } = useI18n();
  return (deadline: Pick<Deadline, 'sourceType' | 'sourceText' | 'sourceLabel'>): string => {
    if (deadline.sourceType === 'TEXT') return deadline.sourceText ?? '';
    const label = deadline.sourceLabel ?? '';
    const shown = /^\d{4}-\d{2}-\d{2}$/.test(label) ? formatDate(label, locale) : label;
    return `${t(`wp4.source.${deadline.sourceType}`)}: ${shown}`;
  };
}
