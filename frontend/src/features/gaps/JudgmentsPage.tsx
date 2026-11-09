import { useState } from 'react';
import { Link } from 'react-router';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { formatDate } from '../cases/format';
import { casePath } from '../cases/paths';
import { useLoad } from '../pleadings/useLoad';
import { listJudgments, OUTCOMES } from './api';

/** The judgments of every case the user may see (requirements section 9); the server lists only visible cases. */
export function JudgmentsPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('nav.judgments'));
  const [page, setPage] = useState(0);
  const [outcome, setOutcome] = useState('');
  const load = useLoad((signal) => listJudgments(page, outcome, signal), [page, outcome]);
  if (load.status === 403) return <AccessDeniedPage />;
  const data = load.data;
  return (
    <>
      <h1>{t('nav.judgments')}</h1>
      <p>
        <label>
          {t('gap.judgments.outcome')}{' '}
          <select
            value={outcome}
            onChange={(e) => {
              setPage(0);
              setOutcome(e.target.value);
            }}
          >
            <option value="">{t('gap.any')}</option>
            {OUTCOMES.map((o) => (
              <option key={o} value={o}>
                {o}
              </option>
            ))}
          </select>
        </label>
      </p>
      {load.status !== null ? <p role="alert">{t('gap.failed')}</p> : null}
      {data === null ? (
        load.status === null ? <p>{t('gap.loading')}</p> : null
      ) : data.items.length === 0 ? (
        <p>{t('gap.judgments.none')}</p>
      ) : (
        <>
          <table>
            <thead>
              <tr>
                <th>{t('gap.judgments.case')}</th>
                <th>{t('gap.judgments.court')}</th>
                <th>{t('gap.judgments.date')}</th>
                <th>{t('gap.judgments.outcome')}</th>
                <th>{t('gap.judgments.final')}</th>
                <th>{t('gap.judgments.appeal')}</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((j) => (
                <tr key={j.id}>
                  <td>
                    <Link to={casePath(j.caseId)}>{j.caseReferenceNo}</Link> {j.caseTitle}
                  </td>
                  <td>
                    {j.courtName} {j.courtCaseNumber ?? ''}
                    {j.courtCaseYear === null ? '' : `/${String(j.courtCaseYear)}`}
                  </td>
                  <td>{formatDate(j.judgmentDate, locale)}</td>
                  <td>{j.outcome}</td>
                  <td>{j.finalJudgment ? t('gap.yes') : t('gap.no')}</td>
                  <td>{j.appealDueOn === null ? t('gap.notSpecified') : formatDate(j.appealDueOn, locale)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            <button type="button" disabled={page === 0} onClick={() => { setPage(page - 1); }}>
              {t('gap.previous')}
            </button>{' '}
            {t('gap.pageOf', { page: String(data.page + 1), pages: String(Math.max(data.totalPages, 1)) })}{' '}
            <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => { setPage(page + 1); }}>
              {t('gap.next')}
            </button>
          </p>
        </>
      )}
    </>
  );
}
