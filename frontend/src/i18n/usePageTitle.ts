import { useEffect } from 'react';
import { useI18n } from './I18nContext';

/** Sets `document.title` to "<page> — LawDesk (Preview)" in the current language. */
export function usePageTitle(page: string): void {
  const { t } = useI18n();
  const title = `${page} — ${t('app.name')} ${t('preview.titleSuffix')}`;
  useEffect(() => {
    document.title = title;
  }, [title]);
}
