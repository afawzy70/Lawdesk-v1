import { Link } from 'react-router';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { FAMILY_CODES } from './familiesApi';
import { ReportSections } from './ReportParts';
import './reports.css';

/** The seventeen report families of requirements section 27, each opening its own filterable, downloadable report. */
export function FamiliesIndexPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('wp8.families.title'));
  return (
    <>
      <h1>{t('wp8.families.title')}</h1>
      <ReportSections />
      <p>{t('wp8.families.intro')}</p>
      <ol className="rpt-families">
        {FAMILY_CODES.map((code) => (
          <li key={code}>
            <Link to={withLang(code === 'PERFORMANCE' ? '/reports/performance' : `/reports/families/${code}`, locale)}>
              {t(`wp8.family.${code}`)}
            </Link>
          </li>
        ))}
      </ol>
    </>
  );
}
