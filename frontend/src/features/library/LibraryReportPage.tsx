import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { formatDate } from '../cases/format';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { getSummary, type Count, type Summary } from './api';
import { classificationLabel, dimensionKey } from './labels';
import './library.css';

type Load = { kind: 'loading' } | { kind: 'forbidden' } | { kind: 'failed' } | { kind: 'loaded'; summary: Summary };

/** Counts by court, year, direction, finality and classification, and the legal questions named most (section 16.7). */
export function LibraryReportPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('library.report.title'));
  const id = useId();
  const [load, setLoad] = useState<Load>({ kind: 'loading' });

  useEffect(() => {
    const controller = new AbortController();
    getSummary(controller.signal).then(
      (summary) => {
        if (!controller.signal.aborted) setLoad({ kind: 'loaded', summary });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setLoad(error instanceof ApiError && error.status === 403 ? { kind: 'forbidden' } : { kind: 'failed' });
      },
    );
    return () => {
      controller.abort();
    };
  }, []);

  if (load.kind === 'forbidden') return <AccessDeniedPage />;
  if (load.kind === 'loading') return <p role="status">{t('library.loading')}</p>;
  if (load.kind === 'failed') {
    return (
      <p className="case-alert" role="alert">
        {t('library.failed')}
      </p>
    );
  }
  const s = load.summary;

  const table = (title: MessageKey, rows: Count[], label: (c: Count) => string) => (
    <section className="library-section" aria-labelledby={`${id}-${title}`}>
      <h2 id={`${id}-${title}`}>{t(title)}</h2>
      {rows.length === 0 ? (
        <p>{t('library.report.empty')}</p>
      ) : (
        <table className="library-table">
          <thead>
            <tr>
              <th scope="col">{t(title)}</th>
              <th scope="col">{t('library.report.count')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.key ?? 'none'}>
                <td>{label(row)}</td>
                <td>{row.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );

  return (
    <>
      <h1>{t('library.report.title')}</h1>
      <p>
        <Link to={withLang('/library', locale)}>{t('library.back')}</Link>
      </p>
      <p role="status">{t('library.report.totals', { active: String(s.active), withdrawn: String(s.withdrawn) })}</p>
      <p className="case-notice">{t('library.principles.notice')}</p>
      {table('library.report.byCourt', s.byCourt, (c) => c.label ?? '')}
      {table('library.report.byYear', s.byYear, (c) => c.label ?? t('library.report.noYear'))}
      {table('library.report.byDirection', s.byDirection, (c) =>
        c.key === 'NOT_SPECIFIED' ? t('library.report.NOT_SPECIFIED') : t(`library.direction.${c.key ?? ''}` as MessageKey),
      )}
      {table('library.report.byFinality', s.byFinality, (c) => t(`library.finality.${c.key ?? 'NOT_SPECIFIED'}` as MessageKey))}
      <section className="library-section" aria-labelledby={`${id}-cls`}>
        <h2 id={`${id}-cls`}>{t('library.report.byClassification')}</h2>
        {s.byClassification.length === 0 ? (
          <p>{t('library.report.empty')}</p>
        ) : (
          <table className="library-table">
            <thead>
              <tr>
                <th scope="col">{t('library.lists.dimension')}</th>
                <th scope="col">{t('library.search.classification')}</th>
                <th scope="col">{t('library.report.count')}</th>
              </tr>
            </thead>
            <tbody>
              {s.byClassification.map((row) => (
                <tr key={row.classificationId}>
                  <td>{t(dimensionKey(row.dimension))}</td>
                  <td>{classificationLabel(row, locale)}</td>
                  <td>{row.count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      <section className="library-section" aria-labelledby={`${id}-issues`}>
        <h2 id={`${id}-issues`}>{t('library.report.issues')}</h2>
        {s.frequentIssues.length === 0 ? (
          <p>{t('library.report.empty')}</p>
        ) : (
          <ul>
            {s.frequentIssues.map((issue) => (
              <li key={issue.topic}>
                <strong>{issue.topic}</strong>:{' '}
                {t('library.report.issueRow', {
                  principles: String(issue.principleCount),
                  judgments: String(issue.judgmentCount),
                  first: issue.firstJudgmentDate === null ? t('library.na') : formatDate(issue.firstJudgmentDate, locale),
                  last: issue.lastJudgmentDate === null ? t('library.na') : formatDate(issue.lastJudgmentDate, locale),
                })}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
