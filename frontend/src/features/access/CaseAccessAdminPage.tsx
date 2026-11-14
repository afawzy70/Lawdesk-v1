import { type SyntheticEvent, useCallback, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AdminTabs } from '../admin/AdminTabs';
import { usePermissions } from '../archive/usePermissions';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { getCase, type CaseListItem, type LegalCase, searchCases } from '../cases/api';
import { CaseAccessSection } from './CaseAccessSection';

/**
 * «الاطلاع على هذه القضية» and «المنح الصريحة» for one case, in the administration area (they are no longer on the
 * case page): search the case by reference, number or title, then set its confidentiality and manage its explicit
 * grants. The backend still decides: a case the user may not see answers 404 and nothing is shown for it.
 */
export function CaseAccessAdminPage() {
  const { t } = useI18n();
  usePageTitle(t('access.admin.title'));
  const id = useId();
  const granted = usePermissions();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<CaseListItem[] | null>(null);
  const [chosen, setChosen] = useState<LegalCase | null>(null);
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);

  const open = useCallback(async (caseId: string) => {
    setFailure(null);
    try {
      setChosen(await getCase(caseId));
    } catch (error) {
      setChosen(null);
      setFailure(error instanceof ApiError && error.status === 404 ? 'access.admin.notFound' : 'access.admin.failed');
    }
  }, []);

  if (!granted.has('CASE_GRANT_MANAGE')) return <AccessDeniedPage />;

  const search = async (event: SyntheticEvent) => {
    event.preventDefault();
    setChosen(null);
    setNotice(null);
    if (query.trim() === '') {
      setFailure('access.admin.needQuery');
      return;
    }
    setFailure(null);
    try {
      setResults((await searchCases({ q: query })).items);
    } catch {
      setResults(null);
      setFailure('access.admin.failed');
    }
  };

  return (
    <>
      <h1>{t('access.admin.title')}</h1>
      <AdminTabs />
      <p>{t('access.admin.intro')}</p>
      <form className="case-form" role="search" aria-label={t('access.admin.search')} noValidate onSubmit={(e) => void search(e)}>
        <div className="case-field">
          <label htmlFor={`${id}-q`}>{t('access.admin.query')}</label>
          <input id={`${id}-q`} type="search" maxLength={200} value={query} onChange={(e) => { setQuery(e.target.value); }} />
        </div>
        <div className="case-actions">
          <button type="submit" className="primary">{t('access.admin.searchButton')}</button>
        </div>
      </form>
      {failure === null ? null : <p className="case-alert" role="alert">{t(failure)}</p>}
      {notice === null ? null : <p className="case-notice" role="status">{t(notice)}</p>}
      {results === null ? null : results.length === 0 ? (
        <p>{t('access.admin.none')}</p>
      ) : (
        <ul aria-label={t('access.admin.results')}>
          {results.map((item) => (
            <li key={item.id}>
              <button type="button" aria-pressed={chosen?.id === item.id} onClick={() => void open(item.id)}>
                {item.referenceNo} — {item.title}
              </button>
            </li>
          ))}
        </ul>
      )}
      {chosen === null ? null : (
        <>
          <h2>{t('access.admin.caseHeading', { referenceNo: chosen.referenceNo })}</h2>
          <CaseAccessSection
            key={`${chosen.id}-${String(chosen.version)}`}
            caseId={chosen.id}
            confidentiality={chosen.confidentiality ?? 'NORMAL'}
            version={chosen.version}
            onChanged={() => {
              setNotice('access.case.saved');
              void open(chosen.id);
            }}
          />
        </>
      )}
    </>
  );
}
