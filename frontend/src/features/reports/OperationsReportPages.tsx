import { useId, type ReactNode, type SyntheticEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { casePath } from '../cases/paths';
import { formatDay } from './format';
import {
  HEARING_STATUSES,
  REFERRAL_STATUSES,
  TASK_STATUSES,
  getExpertsReport,
  getHearingReport,
  getWorkloadReport,
  hearingFilterFrom,
  hearingQuery,
  type ExpertsReport,
  type HearingFilter,
  type HearingReport,
  type WorkloadReport,
} from './operationsApi';
import { ExportLinks, Field, Pager, ReportSections, Summary } from './ReportParts';
import { isObject, useReport, type ReportLoad } from './useReport';
import './reports.css';

/** The frame of a report section: page heading, section links, the section's heading and its state. */
function ReportFrame<T>({
  section,
  load,
  filters,
  children,
}: {
  section: MessageKey;
  load: ReportLoad<T>;
  filters?: ReactNode;
  children: (report: T) => ReactNode;
}) {
  const { t } = useI18n();
  usePageTitle(t('reports.title'));
  if (load.kind === 'forbidden') {
    return <AccessDeniedPage />;
  }
  return (
    <>
      <h1>{t('reports.title')}</h1>
      <ReportSections />
      <section>
        <h2>{t(section)}</h2>
        {filters}
        {load.kind === 'loading' ? <p role="status">{t('reports.list.loading')}</p> : null}
        {load.kind === 'failed' ? (
          <p className="rpt-alert" role="alert">
            {t(load.message)}
          </p>
        ) : null}
        {load.kind === 'loaded' ? children(load.report) : null}
      </section>
    </>
  );
}

/** A captioned table in the focusable scroll region, so it never widens the page. */
function ReportTable({ caption, headers, children }: { caption: string; headers: string[]; children: ReactNode }) {
  const captionId = useId();
  return (
    <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
      <table className="rpt-table">
        <caption id={captionId} className="visually-hidden">
          {caption}
        </caption>
        <thead>
          <tr>
            {headers.map((header) => (
              <th key={header} scope="col">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

function validHearings(report: HearingReport): boolean {
  return isObject(report) && Array.isArray(report.items) && isObject(report.summary);
}

/** LD-032 (hearings part): the hearings of a period, by status, in calendar order. */
export function HearingReportPage() {
  const { locale, t } = useI18n();
  const [params, setParams] = useSearchParams();
  const filter = hearingFilterFrom((name) => params.get(name));
  const key = JSON.stringify(filter);
  const load = useReport(key, (signal) => getHearingReport(filter, signal), validHearings);

  const navigate = (next: HearingFilter) => {
    const updated = hearingQuery(next);
    updated.set('lang', locale);
    setParams(updated);
  };
  const onApply = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    navigate(
      hearingFilterFrom((name) => {
        const value = data.get(name);
        return typeof value === 'string' ? value : null;
      }),
    );
  };

  const filters = (
    <>
      <form key={key} role="search" aria-label={t('reports.filters')} className="rpt-filters" onSubmit={onApply}>
        <Field label={t('reports.filter.from')}>
          <input type="date" name="from" defaultValue={filter.from ?? ''} />
        </Field>
        <Field label={t('reports.filter.to')}>
          <input type="date" name="to" defaultValue={filter.to ?? ''} />
        </Field>
        <Field label={t('reports.filter.hearingStatus')}>
          <select name="status" defaultValue={filter.status ?? ''}>
            <option value="">{t('reports.filter.any')}</option>
            {HEARING_STATUSES.map((status) => (
              <option key={status} value={status}>
                {t(`operations.hearingStatus.${status}`)}
              </option>
            ))}
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
      <ExportLinks path="/api/v1/reports/hearings/export" params={hearingQuery(filter)} />
    </>
  );

  return (
    <ReportFrame section="reports.section.hearings" load={load} filters={filters}>
      {(report: HearingReport) => (
        <>
          <p>
            {t('reports.judgments.period', { from: formatDay(report.from, locale), to: formatDay(report.to, locale) })}
          </p>
          <Summary
            items={[
              [t('reports.summary.hearings'), report.summary.total],
              [t('reports.summary.awaitingResult'), report.summary.awaitingResult],
              ...HEARING_STATUSES.map(
                (status) => [t(`operations.hearingStatus.${status}`), report.summary.byStatus[status]] as const,
              ),
            ]}
          />
          {report.items.length === 0 ? (
            <p>{t('reports.list.emptyHearings')}</p>
          ) : (
            <>
              <ReportTable
                caption={t('reports.section.hearings')}
                headers={[
                  t('reports.column.hearingDate'),
                  t('reports.column.hearingTime'),
                  t('reports.column.case'),
                  t('reports.column.stage'),
                  t('reports.column.court'),
                  t('reports.column.number'),
                  t('reports.column.purpose'),
                  t('reports.column.status'),
                  t('reports.column.awaitingResult'),
                ]}
              >
                {report.items.map((row) => (
                  <tr key={row.id}>
                    <td>{formatDay(row.hearingDate, locale)}</td>
                    <td>
                      {row.hearingTime === null ? '—' : <bdi dir="ltr">{row.hearingTime.slice(0, 5)}</bdi>}
                    </td>
                    <td>
                      {row.caseReferenceNo === null ? null : (
                        <Link to={withLang(casePath(row.caseId), locale)}>{row.caseReferenceNo}</Link>
                      )}
                      {row.caseTitle === null ? null : <span className="rpt-subline">{row.caseTitle}</span>}
                    </td>
                    <td>{row.stage === null ? '—' : t(`proceedings.stage.${row.stage}`)}</td>
                    <td>{row.court === null ? '—' : locale === 'ar' ? row.court.nameAr : row.court.nameEn}</td>
                    <td>
                      {row.courtCaseNumber === null || row.courtCaseYear === null ? (
                        '—'
                      ) : (
                        <bdi dir="ltr">
                          {`${row.courtCaseNumber}/${formatNumber(row.courtCaseYear, locale, { useGrouping: false })}`}
                        </bdi>
                      )}
                    </td>
                    <td>{t(`operations.purpose.${row.purpose}`)}</td>
                    <td>{t(`operations.hearingStatus.${row.status}`)}</td>
                    <td>{t(row.awaitingResult ? 'reports.yes' : 'reports.no')}</td>
                  </tr>
                ))}
              </ReportTable>
              <Pager
                page={report.page}
                pages={report.totalPages}
                onPage={(page) => {
                  navigate({ ...filter, page });
                }}
              />
            </>
          )}
        </>
      )}
    </ReportFrame>
  );
}

/** LD-033: the open work of every assignee, and the task totals. */
export function WorkloadReportPage() {
  const { locale, t } = useI18n();
  const load = useReport(
    'workload',
    (signal) => getWorkloadReport(signal),
    (report: WorkloadReport) => isObject(report) && Array.isArray(report.items) && isObject(report.summary),
  );
  const n = (value: number) => formatNumber(value, locale);
  return (
    <ReportFrame
      section="reports.section.workload"
      load={load}
      filters={<ExportLinks path="/api/v1/reports/workload/export" params={new URLSearchParams()} />}
    >
      {(report: WorkloadReport) => (
        <>
          <Summary
            items={[
              [t('reports.summary.overdueTasks'), report.summary.overdue],
              [t('reports.summary.dueSoon', { days: n(report.dueSoonDays) }), report.summary.dueSoon],
              ...TASK_STATUSES.map(
                (status) => [t(`operations.taskStatus.${status}`), report.summary.byStatus[status]] as const,
              ),
            ]}
          />
          {report.items.length === 0 ? (
            <p>{t('reports.list.emptyWorkload')}</p>
          ) : (
            <ReportTable
              caption={t('reports.section.workload')}
              headers={[
                t('reports.column.assignee'),
                t('reports.column.open'),
                t('reports.column.inProgress'),
                t('reports.column.overdue'),
              ]}
            >
              {report.items.map((row) => (
                <tr key={row.assigneeId}>
                  <td>{row.displayName ?? t('reports.unknownUser')}</td>
                  <td className="rpt-number">{n(row.open)}</td>
                  <td className="rpt-number">{n(row.inProgress)}</td>
                  <td className="rpt-number">{n(row.overdue)}</td>
                </tr>
              ))}
            </ReportTable>
          )}
        </>
      )}
    </ReportFrame>
  );
}

/** LD-033: every expert of the registry with open referrals and overdue reports. */
export function ExpertReportPage() {
  const { locale, t } = useI18n();
  const load = useReport(
    'experts',
    (signal) => getExpertsReport(signal),
    (report: ExpertsReport) => isObject(report) && Array.isArray(report.items) && isObject(report.summary),
  );
  const n = (value: number) => formatNumber(value, locale);
  return (
    <ReportFrame
      section="reports.section.experts"
      load={load}
      filters={<ExportLinks path="/api/v1/reports/experts/export" params={new URLSearchParams()} />}
    >
      {(report: ExpertsReport) => (
        <>
          <Summary
            items={[
              [t('reports.summary.overdueReports'), report.summary.overdueReports],
              ...REFERRAL_STATUSES.map(
                (status) => [t(`operations.referralStatus.${status}`), report.summary.byStatus[status]] as const,
              ),
            ]}
          />
          {report.items.length === 0 ? (
            <p>{t('reports.list.emptyExperts')}</p>
          ) : (
            <ReportTable
              caption={t('reports.section.experts')}
              headers={[
                t('reports.column.expert'),
                t('reports.column.specialty'),
                t('reports.column.active'),
                t('reports.column.openReferrals'),
                t('reports.column.overdueReports'),
              ]}
            >
              {report.items.map((row) => (
                <tr key={row.expertId}>
                  <td>{row.name ?? '—'}</td>
                  <td>{row.specialty ?? '—'}</td>
                  <td>{t(row.active ? 'reports.yes' : 'reports.no')}</td>
                  <td className="rpt-number">{n(row.open)}</td>
                  <td className="rpt-number">{n(row.overdue)}</td>
                </tr>
              ))}
            </ReportTable>
          )}
        </>
      )}
    </ReportFrame>
  );
}
