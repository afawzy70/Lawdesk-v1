import { useEffect, useId, useState, type SyntheticEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime, formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import {
  CASE_STATUSES,
  CASE_TYPES,
  caseQuery,
  searchCases,
  type CaseListItem,
  type CaseSearch,
  type CaseStatus,
  type CaseType,
  type PageResponse,
} from './api';
import { formatDate } from './format';
import { NEW_CASE_PATH, editCasePath } from './paths';
import { isStatus, problemMessage } from './problems';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import './cases.css';

type Load =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'failed'; message: MessageKey }
  | { kind: 'loaded'; result: PageResponse<CaseListItem> };

function oneOf<T extends string>(values: readonly T[], value: string | null): T | undefined {
  return values.find((candidate) => candidate === value);
}

/** The filters live in the address (LD-015), so a reload or a shared link shows the same page. */
function searchFrom(params: URLSearchParams): CaseSearch {
  const search: CaseSearch = {};
  const q = params.get('q')?.trim() ?? '';
  const status = oneOf(CASE_STATUSES, params.get('status'));
  const caseType = oneOf(CASE_TYPES, params.get('caseType'));
  const page = Number.parseInt(params.get('page') ?? '', 10);
  if (q !== '') search.q = q;
  if (status !== undefined) search.status = status;
  if (caseType !== undefined) search.caseType = caseType;
  if (page > 0) search.page = page;
  return search;
}

/** LD-015: the case list with search, status and type filters, and paging. */
export function CaseListPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('cases.title'));
  const [params, setParams] = useSearchParams();
  const search = searchFrom(params);
  const key = JSON.stringify(search);
  const [result, setResult] = useState<{ key: string; load: Load } | null>(null);
  const load: Load = result?.key === key ? result.load : { kind: 'loading' };
  const captionId = useId();

  useEffect(() => {
    const controller = new AbortController();
    const current = JSON.parse(key) as CaseSearch;
    searchCases(current, controller.signal).then(
      (page) => {
        if (controller.signal.aborted) return;
        setResult({
          key,
          load: Array.isArray(page.items)
            ? { kind: 'loaded', result: page }
            : { kind: 'failed', message: 'cases.list.failed' },
        });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({
            key,
            load: isStatus(error, 403)
              ? { kind: 'forbidden' }
              : { kind: 'failed', message: problemMessage(error, 'cases.list.failed') },
          });
        }
      },
    );
    return () => {
      controller.abort();
    };
  }, [key]);

  /** Shows `next` instead of the current filters and page, keeping the language. */
  const navigate = (next: CaseSearch) => {
    const updated = new URLSearchParams(caseQuery(next));
    const lang = params.get('lang');
    if (lang !== null) updated.set('lang', lang);
    setParams(updated);
  };

  const onSearch = (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const text = (name: string) => {
      const value = data.get(name);
      return typeof value === 'string' ? value : '';
    };
    const next: CaseSearch = {};
    const q = text('q').trim();
    const status = oneOf(CASE_STATUSES, text('status'));
    const caseType = oneOf(CASE_TYPES, text('caseType'));
    if (q !== '') next.q = q;
    if (status !== undefined) next.status = status;
    if (caseType !== undefined) next.caseType = caseType;
    navigate(next);
  };

  const goToPage = (page: number) => {
    navigate({ ...search, page });
  };

  if (load.kind === 'forbidden') {
    return <AccessDeniedPage />;
  }

  return (
    <>
      <div className="case-list-head">
        <h1>{t('cases.title')}</h1>
        <Link className="case-new" to={withLang(NEW_CASE_PATH, locale)}>
          {t('cases.list.new')}
        </Link>
      </div>

      {/* Uncontrolled, re-created whenever the address changes, so it always shows the active filters. */}
      <form key={key} role="search" aria-label={t('cases.list.filters')} className="case-filters" onSubmit={onSearch}>
        <label>
          {t('cases.list.search')}
          <input type="search" name="q" maxLength={100} defaultValue={search.q ?? ''} />
        </label>
        <label>
          {t('cases.field.status')}
          <select name="status" defaultValue={search.status ?? ''}>
            <option value="">{t('cases.list.anyStatus')}</option>
            {CASE_STATUSES.map((status: CaseStatus) => (
              <option key={status} value={status}>
                {t(`cases.status.${status}`)}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('cases.field.caseType')}
          <select name="caseType" defaultValue={search.caseType ?? ''}>
            <option value="">{t('cases.list.anyType')}</option>
            {CASE_TYPES.map((type: CaseType) => (
              <option key={type} value={type}>
                {t(`cases.type.${type}`)}
              </option>
            ))}
          </select>
        </label>
        <div className="case-actions">
          <button type="submit" className="primary">
            {t('cases.list.apply')}
          </button>
          <button
            type="button"
            onClick={() => {
              navigate({});
            }}
          >
            {t('cases.list.clear')}
          </button>
        </div>
      </form>

      {load.kind === 'loading' ? <p role="status">{t('cases.list.loading')}</p> : null}
      {load.kind === 'failed' ? (
        <p className="case-alert" role="alert">
          {t(load.message)}
        </p>
      ) : null}
      {load.kind === 'loaded' && load.result.items.length === 0 ? <p>{t('cases.list.empty')}</p> : null}
      {load.kind === 'loaded' && load.result.items.length > 0 ? (
        <>
          <p>{t('cases.list.total', { count: formatNumber(load.result.totalItems, locale) })}</p>
          {/* Focusable so keyboard users can scroll the table on narrow screens. */}
          <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
            <table className="case-table">
              <caption id={captionId} className="visually-hidden">
                {t('cases.title')}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('cases.field.referenceNo')}</th>
                  <th scope="col">{t('cases.field.title')}</th>
                  <th scope="col">{t('cases.field.caseType')}</th>
                  <th scope="col">{t('cases.field.status')}</th>
                  <th scope="col">{t('cases.field.openedOn')}</th>
                  <th scope="col">{t('cases.field.updatedAt')}</th>
                </tr>
              </thead>
              <tbody>
                {load.result.items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <Link to={withLang(editCasePath(item.id), locale)}>{item.referenceNo}</Link>
                    </td>
                    <td>{item.title}</td>
                    <td>{t(`cases.type.${item.caseType}`)}</td>
                    <td>{t(`cases.status.${item.status}`)}</td>
                    <td>{formatDate(item.openedOn, locale)}</td>
                    <td>{formatDateTime(item.updatedAt, locale)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>
            {t('cases.list.page', {
              page: formatNumber(load.result.page + 1, locale),
              pages: formatNumber(load.result.totalPages, locale),
            })}
          </p>
          {load.result.totalPages > 1 ? (
            <nav aria-label={t('cases.list.pagination')} className="case-pagination">
              <button
                type="button"
                disabled={load.result.page <= 0}
                onClick={() => {
                  goToPage(load.result.page - 1);
                }}
              >
                {t('cases.list.previous')}
              </button>
              <button
                type="button"
                disabled={load.result.page + 1 >= load.result.totalPages}
                onClick={() => {
                  goToPage(load.result.page + 1);
                }}
              >
                {t('cases.list.next')}
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </>
  );
}
