import { useI18n } from '../i18n/I18nContext';

/** Persistent, non-dismissible notice that the whole application is a mock-data preview. */
export function PreviewBanner() {
  const { t } = useI18n();
  return (
    <div className="preview-banner" role="note" aria-label={t('preview.bannerLabel')}>
      <p>{t('preview.banner')}</p>
    </div>
  );
}
