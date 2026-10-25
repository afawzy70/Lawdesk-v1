import { useEffect, useId, useState, type SyntheticEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { casePath } from '../cases/paths';
import { OUTCOMES, STAGES } from '../proceedings/proceedingsApi';
import { failureMessage, isForbidden } from './common';
import { CourtFilter } from './courts';
import { formatDay } from './format';
import {
  JUDGMENT_SORTS,
  getJudgmentReport,
  judgmentFilterFrom,
  judgmentQuery,
  type JudgmentFilter,
  type JudgmentReport,
} from './judgmentApi';
import { ExportLinks, Field, Pager, ReportSections, Summary } from './ReportParts';
import './reports.css';

type Load =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'failed'; message: MessageKey }
  | { kind: 'loaded'; report: JudgmentReport };

const DEFAULT_SORT = 'judgmentDate';
const DEFAULT_DIRECTION = 'desc';

const SORT_LABELS: Record<(typeof JUDGMENT_SORTS)[number], MessageKey> = {
  judgmentDate: 'reports.column.judgmentDate',
  outcome: 'reports.column.outcome',
  stage: 'reports.column.stage',
};

/** LD-032 (judgments part): judgments of a period with filters in the address, a summary, sorting and paging. */
export function JudgmentReportPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('reports.title'));
  const [params, setParams] = useSearchParams();
  const filter = judgmentFilterFrom((name) => params.get(name));
  const key = JSON.stringify(filter);
  const [result, setResult] = useState<{ key: string; load: Load } | null>(null);
  const load: Load = result?.key === key ? result.load : { kind: 'loading' };
  const captionId = useId();

  useEffect(() => {
    const controller = new AbortController();
    getJudgmentReport(JSON.parse(key) as JudgmentFilter, controller.signal).then(
      (report) => {
        if (controller.signal.aborted) return;
        setResult({
          key,
          load:
            Array.isArray(report.items) && typeof report.summary === 'object'
              ? { kind: 'loaded', report }
              : { kind: 'failed', message: 'reports.list.failed' },
        });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        setResult({
          key,
          load: isForbidden(error) ? { kind: 'forbidden' } : { kind: 'failed', message: failureMessage(error) },
        });
      },
    );
    return () => {
      controller.abort();
    };
  }, [key]);

  const navigate = (next: JudgmentFilter) => {
    const updated = judgmentQuery(next);
    updated.set('lang', locale);
    setParams(updated);
  };

  const onApply = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = judgmentFilterFrom((name) => {
      if (name === 'sort') {
        const sort = data.get('sortField');
        const direction = data.get('sortDirection');
        if (typeof sort !== 'string' || typeof direction !== 'string') return null;
        return sort === DEFAULT_SORT && direction === DEFAULT_DIRECTION ? null : `${sort},${direction}`;
      }
      const value = data.get(name);
      return typeof value === 'string' ? value : null;
    });
    delete next.page;
    navigate(next);
  };

  if (load.kind === 'forbidden') {
    return <AccessDeniedPage />;
  }

  const anyOption = <option value="">{t('reports.filter.any')}</option>;
  const courtName = (court: { nameAr: string; nameEn: string }) => (locale === 'ar' ? court.nameAr : court.nameEn);

  return (
    <>
      <h1>{t('reports.title')}</h1>
      <ReportSections />
      <section>
        <h2>{t('reports.section.judgments')}</h2>

        <form key={key} role="search" aria-label={t('reports.filters')} className="rpt-filters" onSubmit={onApply}>
          <Field label={t('reports.filter.from')}>
            <input type="date" name="from" defaultValue={filter.from ?? ''} />
          </Field>
          <Field label={t('reports.filter.to')}>
            <input type="date" name="to" defaultValue={filter.to ?? ''} />
          </Field>
          <Field label={t('reports.filter.outcome')}>
            <select name="outcome" defaultValue={filter.outcome ?? ''}>
              {anyOption}
              {OUTCOMES.map((outcome) => (
                <option key={outcome} value={outcome}>
                  {t(`proceedings.outcome.${outcome}`)}
                </option>
              ))}
            </select>
          </Field>
          <Field label={t('reports.filter.stageAny')}>
            <select name="stage" defaultValue={filter.stage ?? ''}>
              {anyOption}
              {STAGES.map((stage) => (
                <option key={stage} value={stage}>
                  {t(`proceedings.stage.${stage}`)}
                </option>
              ))}
            </select>
          </Field>
          <CourtFilter initial={filter.courtId ?? ''} anyLabel={t('reports.filter.any')} />
          <Field label={t('reports.filter.final')}>
            <select name="final" defaultValue={filter.final ?? ''}>
              {anyOption}
              <option value="true">{t('reports.filter.finalOnly')}</option>
              <option value="false">{t('reports.filter.notFinal')}</option>
            </select>
          </Field>
          <Field label={t('reports.filter.sort')}>
            <select name="sortField" defaultValue={filter.sort ?? DEFAULT_SORT}>
              {JUDGMENT_SORTS.map((sort) => (
                <option key={sort} value={sort}>
                  {t(SORT_LABELS[sort])}
                </option>
              ))}
            </select>
          </Field>
          <Field label={t('reports.filter.direction')}>
            <select name="sortDirection" defaultValue={filter.direction ?? DEFAULT_DIRECTION}>
              <option value="asc">{t('reports.filter.asc')}</option>
              <option value="desc">{t('reports.filter.desc')}</option>
            </select>
          </Field>
          <div className="rpt-actions">
            <button type="submit" className="primary">
              {t('reports.filter.apply')}
            </button>
            <button
              type="button"
              onClick={() => {
                navigate({});
              }}
            >
              {t('reports.filter.clear')}
            </button>
          </div>
        </form>

        <ExportLinks path="/api/v1/reports/judgments/export" params={judgmentQuery(filter)} />
        {load.kind === 'loading' ? <p role="status">{t('reports.list.loading')}</p> : null}
        {load.kind === 'failed' ? (
          <p className="rpt-alert" role="alert">
            {t(load.message)}
          </p>
        ) : null}
        {load.kind === 'loaded' ? (
          <>
            <p>
              {t('reports.judgments.period', {
                from: formatDay(load.report.from, locale),
                to: formatDay(load.report.to, locale),
              })}
            </p>
            <Summary
              items={[
                [t('reports.summary.judgments'), load.report.summary.total],
                [t('reports.summary.final'), load.report.summary.final],
                ...OUTCOMES.map(
                  (outcome) => [t(`proceedings.outcome.${outcome}`), load.report.summary.byOutcome[outcome]] as const,
                ),
                ...STAGES.map((stage) => [t(`proceedings.stage.${stage}`), load.report.summary.byStage[stage]] as const),
              ]}
            />
            {load.report.items.length === 0 ? (
              <p className="rpt-empty">{t('reports.list.emptyJudgments')}</p>
            ) : (
              <>
                <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
                  <table className="rpt-table">
                    <caption id={captionId} className="visually-hidden">
                      {t('reports.section.judgments')}
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">{t('reports.column.judgmentDate')}</th>
                        <th scope="col">{t('reports.column.case')}</th>
                        <th scope="col">{t('reports.column.stage')}</th>
                        <th scope="col">{t('reports.column.court')}</th>
                        <th scope="col">{t('reports.column.number')}</th>
                        <th scope="col">{t('reports.column.outcome')}</th>
                        <th scope="col">{t('reports.column.final')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {load.report.items.map((row) => (
                        <tr key={row.id}>
                          <td>{formatDay(row.judgmentDate, locale)}</td>
                          <td>
                            {row.caseReferenceNo === null ? null : (
                              <Link to={withLang(casePath(row.caseId), locale)}>{row.caseReferenceNo}</Link>
                            )}
                            {row.caseTitle === null ? null : <span className="rpt-subline">{row.caseTitle}</span>}
                          </td>
                          <td>{t(`proceedings.stage.${row.stage}`)}</td>
                          <td>{courtName(row.court)}</td>
                          <td>
                            {/* Number then year, as court records write it, in either language. */}
                            <bdi dir="ltr">
                              {`${row.courtCaseNumber}/${formatNumber(row.courtCaseYear, locale, { useGrouping: false })}`}
                            </bdi>
                          </td>
                          <td>{t(`proceedings.outcome.${row.outcome}`)}</td>
                          <td>{t(row.final ? 'reports.yes' : 'reports.no')}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pager
                  page={load.report.page}
                  pages={load.report.totalPages}
                  onPage={(page) => {
                    navigate({ ...filter, page });
                  }}
                />
              </>
            )}
          </>
        ) : null}
      </section>
    </>
  );
}
