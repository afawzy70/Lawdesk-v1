import { useI18n } from '../i18n/I18nContext';

/** Persistent, non-dismissible notice that this is a test environment: test data only, some areas not built yet. */
export function PreviewBanner() {
  const { t } = useI18n();
  return (
    <div className="preview-banner" role="note" aria-label={t('preview.bannerLabel')}>
      <p>{t('preview.banner')}</p>
    </div>
  );
}
