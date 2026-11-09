import { useId, type SyntheticEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { CASE_TYPES } from '../cases/api';
import { getFamily, isFamilyCode, isFamilyTable, type Cell, type FamilyCode, type FamilyColumn, type FamilyTable } from './familiesApi';
import { ExportLinks, Field, ReportSections } from './ReportParts';
import { useReport } from './useReport';
import './reports.css';

const GROUPINGS: Record<FamilyCode, readonly string[]> = {
  CASES_BY_TYPE_COURT_STAGE: ['CASE_TYPE', 'COURT', 'STAGE'],
  CASES_BY_ORG_UNIT: ['SECTOR', 'DEPARTMENT', 'SECTION'],
  CASES_BY_MEMBER: [],
  OPEN_CLOSED_CASES: ['STATUS_GROUP', 'STATUS'],
  JUDGMENT_OUTCOMES: [],
  CLAIM_RESULTS: [],
  DEFENSE_POSITIONS: [],
  LINKED_CASES: [],
  ADJOURNMENTS: [],
  EXPERT_SESSIONS: [],
  DEADLINES_APPEALS: [],
  ENFORCEMENT: [],
  MISSING_EVIDENCE: [],
  INFO_REQUESTS: [],
  RISK_ASSESSMENT: [],
  PERFORMANCE: ['MEMBERS', 'UNITS'],
  LIBRARY_PRECEDENTS: ['COURT', 'YEAR', 'DIRECTION', 'FINALITY', 'CLASSIFICATION', 'TOPIC'],
};

/** Families whose rows describe cases: the case filters (type, open or closed, opening period) apply to them. */
const CASE_FILTERED = (code: FamilyCode): boolean => code !== 'LIBRARY_PRECEDENTS';

/** The page of the performance view and of every other report family: filters in the address, a table, downloads. */
export function FamilyPage({ fixed }: { fixed?: FamilyCode }) {
  const { code: param } = useParams();
  const code = fixed ?? (isFamilyCode(param) ? param : undefined);
  if (code === undefined) {
    return <UnknownFamily />;
  }
  return <Family code={code} />;
}

function UnknownFamily() {
  const { t } = useI18n();
  usePageTitle(t('wp8.families.title'));
  return (
    <>
      <h1>{t('wp8.families.title')}</h1>
      <p className="rpt-alert" role="alert">
        {t('wp8.list.unknown')}
      </p>
    </>
  );
}

function cellText(cell: Cell, column: FamilyColumn, locale: 'en' | 'ar'): string {
  if (cell === null) return '—';
  if (typeof cell === 'number') {
    return formatNumber(cell, locale, column.type === 'DECIMAL' ? { minimumFractionDigits: 1, maximumFractionDigits: 1 } : {});
  }
  return cell;
}

function Family({ code }: { code: FamilyCode }) {
  const { locale, t } = useI18n();
  usePageTitle(t(`wp8.family.${code}`));
  const [params, setParams] = useSearchParams();
  const captionId = useId();
  const request = new URLSearchParams();
  for (const name of ['from', 'to', 'caseType', 'statusGroup', 'groupBy']) {
    const value = params.get(name);
    if (value !== null && value !== '') request.set(name, value);
  }
  const key = `${code}?${request.toString()}&${locale}`;
  const load = useReport<FamilyTable>(
    key,
    (signal) => {
      const query = new URLSearchParams(request);
      query.set('lang', locale);
      return getFamily(code, query, signal);
    },
    isFamilyTable,
  );

  const onApply = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const name of ['from', 'to', 'caseType', 'statusGroup', 'groupBy']) {
      const value = data.get(name);
      if (typeof value === 'string' && value !== '') next.set(name, value);
    }
    setParams(next);
  };

  if (load.kind === 'forbidden') {
    return <AccessDeniedPage />;
  }

  const groupings = GROUPINGS[code];
  const anyOption = <option value="">{t('wp8.filter.any')}</option>;
  const exportParams = new URLSearchParams(request);

  return (
    <>
      <h1>{t(`wp8.family.${code}`)}</h1>
      <ReportSections />
      <p>
        <Link to={withLang('/reports/families', locale)}>{t('wp8.families.title')}</Link>
      </p>
      <form key={key} role="search" aria-label={t('wp8.filter.label')} className="rpt-filters" onSubmit={onApply}>
        <Field label={t('wp8.filter.from')}>
          <input type="date" name="from" defaultValue={params.get('from') ?? ''} />
        </Field>
        <Field label={t('wp8.filter.to')}>
          <input type="date" name="to" defaultValue={params.get('to') ?? ''} />
        </Field>
        {code === 'PERFORMANCE' || !CASE_FILTERED(code) ? null : (
          <>
            <Field label={t('wp8.filter.caseType')}>
              <select name="caseType" defaultValue={params.get('caseType') ?? ''}>
                {anyOption}
                {CASE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {t(`cases.type.${type}`)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={t('wp8.filter.statusGroup')}>
              <select name="statusGroup" defaultValue={params.get('statusGroup') ?? ''}>
                {anyOption}
                <option value="OPEN">{t('wp8.statusGroup.OPEN')}</option>
                <option value="CLOSED">{t('wp8.statusGroup.CLOSED')}</option>
              </select>
            </Field>
          </>
        )}
        {groupings.length === 0 ? null : (
          <Field label={t('wp8.filter.groupBy')}>
            <select name="groupBy" defaultValue={params.get('groupBy') ?? groupings[0] ?? ''}>
              {groupings.map((grouping) => (
                <option key={grouping} value={grouping}>
                  {t(`wp8.groupBy.${grouping as 'CASE_TYPE'}`)}
                </option>
              ))}
            </select>
          </Field>
        )}
        <div className="rpt-actions">
          <button type="submit" className="primary">
            {t('wp8.filter.apply')}
          </button>
          <button
            type="button"
            onClick={() => {
              setParams(new URLSearchParams());
            }}
          >
            {t('wp8.filter.clear')}
          </button>
        </div>
      </form>
      <p className="rpt-note">{t(code === 'PERFORMANCE' ? 'wp8.family.activityNote' : 'wp8.family.openedNote')}</p>
      <ExportLinks path={`/api/v1/reports/families/${code}/export`} params={exportParams} />
      {load.kind === 'loading' ? <p role="status">{t('reports.list.loading')}</p> : null}
      {load.kind === 'failed' ? (
        <p className="rpt-alert" role="alert">
          {t(load.message)}
        </p>
      ) : null}
      {load.kind === 'loaded' ? <FamilyResult table={load.report} captionId={captionId} /> : null}
    </>
  );
}

function FamilyResult({ table, captionId }: { table: FamilyTable; captionId: string }) {
  const { locale, t } = useI18n();
  if (!table.available) {
    return (
      <p className="rpt-note" role="status">
        {t('wp8.family.unavailable')}
      </p>
    );
  }
  const hasMoney = table.columns.some((column) => column.type === 'MONEY');
  return (
    <>
      {table.note === null ? null : <p className="rpt-note">{table.note}</p>}
      {hasMoney ? <p className="rpt-note">{t('wp8.family.moneyNote')}</p> : null}
      {table.rows.length === 0 ? (
        <p className="rpt-empty">{t('wp8.family.empty')}</p>
      ) : (
        <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
          <table className="rpt-table">
            <caption id={captionId} className="visually-hidden">
              {table.title}
            </caption>
            <thead>
              <tr>
                {table.columns.map((column) => (
                  <th key={column.key} scope="col">
                    {column.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {table.rows.map((row, index) => (
                <tr key={index} className={table.hasTotal && index === table.rows.length - 1 ? 'rpt-total' : undefined}>
                  {table.columns.map((column, c) => {
                    const cell = row[c] ?? null;
                    const text = cellText(cell, column, locale);
                    return column.type === 'TEXT' ? (
                      <td key={column.key}>
                        <bdi dir="auto">{text}</bdi>
                      </td>
                    ) : (
                      <td key={column.key} className="rpt-number">
                        <bdi dir="ltr">{text}</bdi>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
