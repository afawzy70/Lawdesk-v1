import { useEffect, useId, useState, type SyntheticEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { CASE_STATUSES, CASE_TYPES } from '../cases/api';
import { casePath } from '../cases/paths';
import { STAGES } from '../proceedings/proceedingsApi';
import { formatDay } from './format';
import {
  INVENTORY_SORTS,
  STATUS_GROUPS,
  getInventory,
  inventoryFilterFrom,
  inventoryQuery,
  type InventoryFilter,
  type InventoryReport,
} from './inventoryApi';
import { failureMessage, isForbidden } from './common';
import { useReportCourts } from './courts';
import { ExportLinks, Field, Pager, ReportSections, Summary } from './ReportParts';
import './reports.css';

type Load =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'failed'; message: MessageKey }
  | { kind: 'loaded'; report: InventoryReport };

const DEFAULT_SORT = 'openedOn';
const DEFAULT_DIRECTION = 'desc';

const SORT_LABELS: Record<(typeof INVENTORY_SORTS)[number], MessageKey> = {
  openedOn: 'reports.column.openedOn',
  referenceNo: 'reports.column.referenceNo',
  title: 'reports.column.title',
  caseType: 'reports.column.caseType',
  status: 'reports.column.status',
};

/** LD-031: the case inventory report, its filters in the address, a summary over every matching case, and paging. */
export function CaseInventoryPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('reports.title'));
  const [params, setParams] = useSearchParams();
  const filter = inventoryFilterFrom((name) => params.get(name));
  const key = JSON.stringify(filter);
  const [result, setResult] = useState<{ key: string; load: Load } | null>(null);
  const load: Load = result?.key === key ? result.load : { kind: 'loading' };
  const courts = useReportCourts();
  const captionId = useId();

  useEffect(() => {
    const controller = new AbortController();
    getInventory(JSON.parse(key) as InventoryFilter, controller.signal).then(
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

  /** Shows `next` instead of the current filters, keeping the language. */
  const navigate = (next: InventoryFilter) => {
    const updated = inventoryQuery(next);
    updated.set('lang', locale);
    setParams(updated);
  };

  const onApply = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = inventoryFilterFrom((name) => {
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
        <h2>{t('reports.section.inventory')}</h2>

        {/* Uncontrolled, re-created whenever the address changes, so it always shows the active filters. */}
        <form key={key} role="search" aria-label={t('reports.filters')} className="rpt-filters" onSubmit={onApply}>
          <Field label={t('reports.filter.q')}>
            <input type="search" name="q" maxLength={100} defaultValue={filter.q ?? ''} />
          </Field>
          <Field label={t('reports.filter.status')}>
            <select name="status" defaultValue={filter.status ?? ''}>
              {anyOption}
              {CASE_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {t(`cases.status.${status}`)}
                </option>
              ))}
            </select>
          </Field>
          <Field label={t('reports.filter.statusGroup')}>
            <select name="statusGroup" defaultValue={filter.statusGroup ?? ''}>
              {anyOption}
              {STATUS_GROUPS.map((group) => (
                <option key={group} value={group}>
                  {t(`reports.group.${group}`)}
                </option>
              ))}
            </select>
          </Field>
          <Field label={t('reports.filter.caseType')}>
            <select name="caseType" defaultValue={filter.caseType ?? ''}>
              {anyOption}
              {CASE_TYPES.map((type) => (
                <option key={type} value={type}>
                  {t(`cases.type.${type}`)}
                </option>
              ))}
            </select>
          </Field>
          <Field label={t('reports.filter.openedFrom')}>
            <input type="date" name="openedFrom" defaultValue={filter.openedFrom ?? ''} />
          </Field>
          <Field label={t('reports.filter.openedTo')}>
            <input type="date" name="openedTo" defaultValue={filter.openedTo ?? ''} />
          </Field>
          <Field label={t('reports.filter.stage')}>
            <select name="stage" defaultValue={filter.stage ?? ''}>
              {anyOption}
              {STAGES.map((stage) => (
                <option key={stage} value={stage}>
                  {t(`proceedings.stage.${stage}`)}
                </option>
              ))}
            </select>
          </Field>
          {courts === null ? null : (
            <Field label={t('reports.filter.court')}>
              {/* Keyed by the list, so the chosen court is selected once the courts have arrived. */}
              <select key={courts.length} name="courtId" defaultValue={filter.courtId ?? ''}>
                {anyOption}
                {courts.map((court) => (
                  <option key={court.id} value={court.id}>
                    {courtName(court)}
                  </option>
                ))}
              </select>
            </Field>
          )}
          <Field label={t('reports.filter.sort')}>
            <select name="sortField" defaultValue={filter.sort ?? DEFAULT_SORT}>
              {INVENTORY_SORTS.map((sort) => (
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

        <ExportLinks path="/api/v1/reports/cases/export" params={inventoryQuery(filter)} />
        {load.kind === 'loading' ? <p role="status">{t('reports.list.loading')}</p> : null}
        {load.kind === 'failed' ? (
          <p className="rpt-alert" role="alert">
            {t(load.message)}
          </p>
        ) : null}
        {load.kind === 'loaded' ? (
          <>
            <Summary
              items={[
                [t('reports.summary.cases'), load.report.summary.total],
                [t('reports.summary.open'), load.report.summary.open],
                [t('reports.summary.closed'), load.report.summary.closed],
                ...STAGES.map((stage) => [t(`proceedings.stage.${stage}`), load.report.summary.byStage[stage]] as const),
                [t('reports.inventory.noProceedings'), load.report.summary.byStage.NONE],
              ]}
            />
            {load.report.items.length === 0 ? (
              <p className="rpt-empty">{t('reports.list.emptyCases')}</p>
            ) : (
              <>
                {/* Focusable so keyboard users can scroll the table on narrow screens. */}
                <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
                  <table className="rpt-table">
                    <caption id={captionId} className="visually-hidden">
                      {t('reports.section.inventory')}
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">{t('reports.column.referenceNo')}</th>
                        <th scope="col">{t('reports.column.title')}</th>
                        <th scope="col">{t('reports.column.caseType')}</th>
                        <th scope="col">{t('reports.column.status')}</th>
                        <th scope="col">{t('reports.column.openedOn')}</th>
                        <th scope="col">{t('reports.column.currentStage')}</th>
                        <th scope="col">{t('reports.column.currentCourt')}</th>
                        <th scope="col" className="rpt-proceedings">{t('reports.column.proceedingCount')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {load.report.items.map((row) => (
                        <tr key={row.id}>
                          <td className="rpt-reference">
                            <Link to={withLang(casePath(row.id), locale)}><bdi dir="ltr">{row.referenceNo}</bdi></Link>
                          </td>
                          <td>{row.title}</td>
                          <td className="rpt-type">{t(`cases.type.${row.caseType}`)}</td>
                          <td className="rpt-status">{t(`cases.status.${row.status}`)}</td>
                          <td>{formatDay(row.openedOn, locale)}</td>
                          <td>
                            {row.currentStage === null
                              ? t('reports.inventory.noProceedings')
                              : t(`proceedings.stage.${row.currentStage}`)}
                          </td>
                          <td>{row.currentCourt === null ? '—' : courtName(row.currentCourt)}</td>
                          <td className="rpt-number">{formatNumber(row.proceedingCount, locale)}</td>
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
