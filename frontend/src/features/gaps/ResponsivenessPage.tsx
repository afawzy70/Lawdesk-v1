import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { useLoad } from '../pleadings/useLoad';
import { getResponsiveness } from './api';

/** How each administration answered the information requests (requirements section 18, "تقارير"): counts, delays, average reply days. */
export function ResponsivenessPage() {
  const { t } = useI18n();
  usePageTitle(t('gap.responsiveness.title'));
  const load = useLoad((signal) => getResponsiveness(signal), []);
  if (load.status === 403) return <AccessDeniedPage />;
  return (
    <>
      <h1>{t('gap.responsiveness.title')}</h1>
      {load.status !== null ? <p role="alert">{t('gap.failed')}</p> : null}
      {load.data === null ? (load.status === null ? <p>{t('gap.loading')}</p> : null) : load.data.units.length === 0 ? (
        <p>{t('gap.responsiveness.none')}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>{t('gap.responsiveness.unit')}</th>
              <th>{t('gap.responsiveness.requests')}</th>
              <th>{t('gap.responsiveness.replies')}</th>
              <th>{t('gap.responsiveness.answered')}</th>
              <th>{t('gap.responsiveness.overdue')}</th>
              <th>{t('gap.responsiveness.incomplete')}</th>
              <th>{t('gap.responsiveness.cases')}</th>
              <th>{t('gap.responsiveness.average')}</th>
            </tr>
          </thead>
          <tbody>
            {load.data.units.map((u) => (
              <tr key={u.unitId ?? 'none'}>
                <td>{u.unitName ?? t('gap.notSpecified')}</td>
                <td>{u.requests}</td>
                <td>{u.replies}</td>
                <td>{u.answeredRequests}</td>
                <td>{u.overdue}</td>
                <td>{u.incompleteReplies}</td>
                <td>{u.affectedCases}</td>
                <td>{u.averageReplyDays === null ? t('gap.notSpecified') : u.averageReplyDays.toFixed(1)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
