import { useI18n } from '../../i18n/I18nContext';

/** Shown when the backend answers 403 for a screen the user opened (LD-010.4: the backend decides). */
export function AccessDeniedPage() {
  const { t } = useI18n();
  return (
    <section>
      <h1>{t('auth.deniedTitle')}</h1>
      <p>{t('auth.deniedBody')}</p>
    </section>
  );
}
