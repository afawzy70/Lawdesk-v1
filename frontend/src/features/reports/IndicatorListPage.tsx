import { useId } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { casePath } from '../cases/paths';
import { formatDay } from './format';
import {
  NONE_KEY,
  getIndicatorPage,
  isIndicatorCode,
  isIndicatorPage,
  type IndicatorCode,
  type IndicatorPage,
} from './indicatorsApi';
import { ExportLinks, Pager, ReportSections } from './ReportParts';
import { useReport } from './useReport';
import './reports.css';

/**
 * LD-058: the list a dashboard indicator opens. It asks the API for the very same indicator, with the same group and
 * period in the address, so its total is the dashboard figure. Rows link to their case.
 */
export function IndicatorListPage() {
  const { code } = useParams();
  if (!isIndicatorCode(code)) {
    return <UnknownIndicator />;
  }
  return <IndicatorList code={code} />;
}

function UnknownIndicator() {
  const { t } = useI18n();
  usePageTitle(t('wp8.indicators.title'));
  return (
    <>
      <h1>{t('wp8.indicators.title')}</h1>
      <p className="rpt-alert" role="alert">
        {t('wp8.list.unknown')}
      </p>
    </>
  );
}

function IndicatorList({ code }: { code: IndicatorCode }) {
  const { locale, t } = useI18n();
  const name = t(`wp8.indicator.${code}`);
  usePageTitle(name);
  const [params, setParams] = useSearchParams();
  const key = `${code}?${params.toString()}&${locale}`;
  const captionId = useId();
  const load = useReport<IndicatorPage>(
    key,
    (signal) => {
      const query = new URLSearchParams(params);
      query.set('lang', locale);
      return getIndicatorPage(code, query, signal);
    },
    isIndicatorPage,
  );

  if (load.kind === 'forbidden') {
    return <AccessDeniedPage />;
  }

  const group = params.get('group');
  const level = params.get('level');
  const groupName = group === NONE_KEY ? t('wp8.group.none') : (group ?? '');
  const levelName = level === 'SECTOR' || level === 'DEPARTMENT' || level === 'SECTION' ? t(`wp8.level.${level}`) : null;
  const exportParams = new URLSearchParams(params);

  return (
    <>
      <h1>{name}</h1>
      <ReportSections />
      <p>
        <Link to={withLang('/', locale)}>{t('wp8.list.back')}</Link>
      </p>
      {group === null ? null : (
        <p>
          {t('wp8.list.group', { group: levelName === null ? groupName : `${levelName} — ${groupName}` })}
        </p>
      )}
      <ExportLinks path={`/api/v1/reports/indicators/${code}/export`} params={exportParams} />
      {load.kind === 'loading' ? <p role="status">{t('reports.list.loading')}</p> : null}
      {load.kind === 'failed' ? (
        <p className="rpt-alert" role="alert">
          {t(load.message)}
        </p>
      ) : null}
      {load.kind === 'loaded' ? <Loaded report={load.report} captionId={captionId} onPage={(page) => {
        const next = new URLSearchParams(params);
        next.set('page', String(page));
        setParams(next);
      }} /> : null}
    </>
  );
}

function Loaded({ report, captionId, onPage }: { report: IndicatorPage; captionId: string; onPage: (page: number) => void }) {
  const { locale, t } = useI18n();
  if (!report.available) {
    return (
      <p className="rpt-note" role="status">
        {t('wp8.indicator.unavailableHint')}
      </p>
    );
  }
  const hasTitle = report.items.some((item) => item.title !== null);
  const hasDetail = report.items.some((item) => item.detailLabel !== null);
  const columns: MessageKey[] = ['wp8.list.col.case'];
  if (hasTitle) columns.push('wp8.list.col.item');
  columns.push('wp8.list.col.date', 'wp8.list.col.status');
  if (hasDetail) columns.push('wp8.list.col.detail');
  return (
    <>
      <p>{t('wp8.list.total', { count: formatNumber(report.totalItems, locale) })}</p>
      {report.code === 'JUDGMENTS_ISSUED' ? (
        <p>{t('wp8.list.period', { from: formatDay(report.from, locale), to: formatDay(report.to, locale) })}</p>
      ) : null}
      {report.items.length === 0 ? (
        <p className="rpt-empty">{t('wp8.list.empty')}</p>
      ) : (
        <>
          <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
            <table className="rpt-table">
              <caption id={captionId} className="visually-hidden">
                {t(`wp8.indicator.${report.code}`)}
              </caption>
              <thead>
                <tr>
                  {columns.map((column) => (
                    <th key={column} scope="col">
                      {t(column)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {report.items.map((item) => (
                  <tr key={`${item.entity}-${item.id}`}>
                    <td>
                      {item.caseId !== null && item.caseReferenceNo !== null ? (
                        <Link to={withLang(casePath(item.caseId), locale)}>
                          <bdi dir="ltr">{item.caseReferenceNo}</bdi>
                        </Link>
                      ) : null}
                      {item.caseTitle === null ? null : (
                        <span className="rpt-subline">
                          <bdi dir="auto">{item.caseTitle}</bdi>
                        </span>
                      )}
                    </td>
                    {hasTitle ? (
                      <td>
                        <bdi dir="auto">{item.title ?? ''}</bdi>
                      </td>
                    ) : null}
                    <td>{item.date === null ? '' : formatDay(item.date, locale)}</td>
                    <td>{item.statusLabel ?? ''}</td>
                    {hasDetail ? (
                      <td>
                        <bdi dir="auto">{item.detailLabel ?? ''}</bdi>
                      </td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={report.page} pages={report.totalPages} onPage={onPage} />
        </>
      )}
    </>
  );
}
