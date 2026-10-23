import { Link } from 'react-router';
import { useI18n } from '../i18n/I18nContext';
import { withLang } from '../i18n/locale';
import { usePageTitle } from '../i18n/usePageTitle';

export function NotFoundPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('notFound.title'));
  return (
    <>
      <h1>{t('notFound.title')}</h1>
      <p>{t('notFound.body')}</p>
      <p>
        <Link to={withLang('/', locale)}>{t('notFound.homeLink')}</Link>
      </p>
    </>
  );
}
