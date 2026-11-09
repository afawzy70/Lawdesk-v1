import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { formatDate } from '../cases/format';
import { casePath } from '../cases/paths';
import { Notice } from '../pleadings/ui';
import { listEnforcement, type EnforcementPage, type EnforcementScope } from './api';
import { archiveError, statusOf } from './errors';
import './archive.css';

const SCOPES: readonly EnforcementScope[] = ['UNEXECUTED', 'LATE', 'ALL'];

/**
 * The judgments of the cases the user may see that are not executed or are late (requirement 11, second paragraph). "Late"
 * means a member's target date has passed; the system computes no legal period.
 */
export function EnforcementListPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('wp5.enfList.title'));
  const [scope, setScope] = useState<EnforcementScope>('UNEXECUTED');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<{ key: string; data: EnforcementPage | null; error: ReturnType<typeof archiveError> | null; status: number }
  | null>(null);
  const key = `${scope}#${String(page)}#${String(reload)}`;

  useEffect(() => {
    const controller = new AbortController();
    listEnforcement(scope, page, controller.signal).then(
      (data) => {
        setResult({ key, data, error: null, status: 200 });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key, data: null, error: archiveError(error), status: statusOf(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [scope, page, key]);

  const current = result?.key === key ? result : null;
  if (current?.status === 403) return <AccessDeniedPage />;
  const data = current?.data ?? null;
  return (
    <>
      <h1>{t('wp5.enfList.title')}</h1>
      <p>{t('wp5.enfList.intro')}</p>
      <div className="archive-tabs" role="group" aria-label={t('wp5.enfList.scopes')}>
        {SCOPES.map((s) => (
          <button
            key={s}
            type="button"
            aria-pressed={scope === s}
            onClick={() => {
              setScope(s);
              setPage(0);
            }}
          >
            {t(`wp5.enfList.scope.${s}`)}
          </button>
        ))}
      </div>
      {current === null ? <p>{t('wp5.loading')}</p> : null}
      {current?.error != null ? (
        <>
          <Notice failed>{t(current.error)}</Notice>
          <button type="button" onClick={() => { setReload((n) => n + 1); }}>
            {t('wp5.retry')}
          </button>
        </>
      ) : null}
      {data !== null ? (
        <>
          <p role="status">{data.items.length === 0 ? t('wp5.enfList.none') : t('wp5.enfList.count', { count: formatNumber(data.totalItems, locale) })}</p>
          {data.items.length > 0 ? (
            <div className="archive-table-wrap" role="region" tabIndex={0} aria-label={t('wp5.enfList.title')}>
              <table className="archive-table">
                <thead>
                  <tr>
                    <th scope="col">{t('wp5.enfList.case')}</th>
                    <th scope="col">{t('wp5.enfList.court')}</th>
                    <th scope="col">{t('wp5.enfList.date')}</th>
                    <th scope="col">{t('wp5.enfList.status')}</th>
                    <th scope="col">{t('wp5.enfList.due')}</th>
                    <th scope="col">{t('wp5.enfList.member')}</th>
                    <th scope="col">{t('wp5.enfList.party')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((row) => (
                    <tr key={row.judgmentId}>
                      <td>
                        <Link to={`${casePath(row.caseId)}?lang=${locale}`} aria-label={`${t('wp5.enfList.open')}: ${row.caseReferenceNo ?? row.caseId}`}>
                          <bdi dir="ltr">{row.caseReferenceNo ?? row.caseId}</bdi>
                        </Link>{' '}
                        {row.caseTitle ?? ''}
                      </td>
                      <td>
                        <bdi dir="ltr">{row.courtCaseNumber === null ? '' : `${row.courtCaseNumber}/${String(row.courtCaseYear ?? '')}`}</bdi>
                      </td>
                      <td>{formatDate(row.judgmentDate, locale)}</td>
                      <td>
                        {row.status === null ? t('wp5.enf.status.UNTRACKED') : t(`wp5.enf.status.${row.status}`)}
                        {row.late ? <span className="archive-badge" data-tone="bad"> {t('wp5.enfList.late')}</span> : null}
                      </td>
                      <td>{row.dueOn === null ? '' : formatDate(row.dueOn, locale)}</td>
                      <td>{row.responsibleMemberName ?? ''}</td>
                      <td>{row.executingParty ?? ''}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
          {data.totalPages > 1 ? (
            <nav className="archive-pager" aria-label={t('wp5.enfList.title')}>
              <button type="button" disabled={data.page === 0} onClick={() => { setPage((p) => p - 1); }}>
                {t('wp5.previous')}
              </button>
              <span>{t('wp5.page', { page: formatNumber(data.page + 1, locale), pages: formatNumber(data.totalPages, locale) })}</span>
              <button type="button" disabled={data.page + 1 >= data.totalPages} onClick={() => { setPage((p) => p + 1); }}>
                {t('wp5.next')}
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </>
  );
}
