import { type SyntheticEvent, useContext, useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { formatDate } from '../cases/format';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { AuthContext } from '../auth/AuthContext';
import {
  CASE_TYPES,
  COURT_LEVELS,
  DIRECTIONS,
  EMPTY_FILTERS,
  FINALITIES,
  listClassifications,
  searchJudgments,
  type Classification,
  type ListItem,
  type Page,
  type SearchFilters,
} from './api';
import { citation, classificationLabel, courtLevelKey, directionKey, finalityKey, relationKey, listSeparator } from './labels';
import './library.css';

type Load = { kind: 'loading' } | { kind: 'forbidden' } | { kind: 'failed' } | { kind: 'loaded'; page: Page<ListItem> };

/** The library's list and Arabic search (LD-052, requirements section 16.3). The backend decides what each user may do. */
export function LibraryPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('library.title'));
  const id = useId();
  const state = useContext(AuthContext)?.state;
  const manage = state?.status === 'signedIn' && state.me.permissions.includes('LIBRARY_MANAGE');
  const [draft, setDraft] = useState<SearchFilters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<SearchFilters>(EMPTY_FILTERS);
  const [result, setResult] = useState<{ key: string; load: Load } | null>(null);
  const [classifications, setClassifications] = useState<Classification[]>([]);
  const key = JSON.stringify(applied);
  const load: Load = result?.key === key ? result.load : { kind: 'loading' };

  useEffect(() => {
    const controller = new AbortController();
    searchJudgments(applied, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) setResult({ key, load: { kind: 'loaded', page } });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        setResult({ key, load: error instanceof ApiError && error.status === 403 ? { kind: 'forbidden' } : { kind: 'failed' } });
      },
    );
    return () => {
      controller.abort();
    };
  }, [applied, key]);

  useEffect(() => {
    listClassifications().then(setClassifications, () => {
      setClassifications([]);
    });
  }, []);

  if (load.kind === 'forbidden') return <AccessDeniedPage />;

  const set = <K extends keyof SearchFilters>(field: K, value: SearchFilters[K]) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };
  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    setApplied({ ...draft, page: 0 });
  };
  const reset = () => {
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
  };
  const go = (page: number) => {
    setApplied({ ...applied, page });
  };

  const select = (field: 'caseType' | 'courtLevel' | 'direction' | 'finality', options: readonly string[], label: (o: string) => string) => (
    <select
      id={`${id}-${field}`}
      value={draft[field]}
      onChange={(e) => {
        set(field, e.target.value);
      }}
    >
      <option value="">{t('library.search.any')}</option>
      {options.map((option) => (
        <option key={option} value={option}>
          {label(option)}
        </option>
      ))}
    </select>
  );

  return (
    <>
      <h1>{t('library.title')}</h1>
      <p>{t('library.intro')}</p>
      <div className="library-toolbar">
        {manage ? <Link to={withLang('/library/new', locale)}>{t('library.add')}</Link> : null}
        <Link to={withLang('/library/reports', locale)}>{t('library.reports')}</Link>
        {manage ? <Link to={withLang('/library/classifications', locale)}>{t('library.lists')}</Link> : null}
      </div>
      <form className="library-filters" role="search" aria-label={t('library.search.legend')} noValidate onSubmit={submit}>
        <div className="case-field library-wide">
          <label htmlFor={`${id}-q`}>{t('library.search.q')}</label>
          <input
            id={`${id}-q`}
            type="search"
            maxLength={200}
            aria-describedby={`${id}-q-hint`}
            value={draft.q}
            onChange={(e) => {
              set('q', e.target.value);
            }}
          />
          <small id={`${id}-q-hint`}>{t('library.search.qHint')}</small>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-courtName`}>{t('library.search.court')}</label>
          <input
            id={`${id}-courtName`}
            type="text"
            maxLength={200}
            value={draft.courtName}
            onChange={(e) => {
              set('courtName', e.target.value);
            }}
          />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-year`}>{t('library.search.year')}</label>
          <input
            id={`${id}-year`}
            type="number"
            inputMode="numeric"
            min={1900}
            max={2100}
            value={draft.year}
            onChange={(e) => {
              set('year', e.target.value);
            }}
          />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-caseType`}>{t('library.search.caseType')}</label>
          {select('caseType', CASE_TYPES, (o) => t(`cases.type.${o}` as MessageKey))}
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-courtLevel`}>{t('library.search.courtLevel')}</label>
          {select('courtLevel', COURT_LEVELS, (o) => t(courtLevelKey(o)))}
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-direction`}>{t('library.search.direction')}</label>
          {select('direction', DIRECTIONS, (o) => t(directionKey(o as (typeof DIRECTIONS)[number])))}
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-finality`}>{t('library.search.finality')}</label>
          {select('finality', FINALITIES, (o) => t(finalityKey(o as (typeof FINALITIES)[number])))}
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-classification`}>{t('library.search.classification')}</label>
          <select
            id={`${id}-classification`}
            value={draft.classificationId}
            onChange={(e) => {
              set('classificationId', e.target.value);
            }}
          >
            <option value="">{t('library.search.any')}</option>
            {classifications.map((item) => (
              <option key={item.id} value={item.id}>
                {classificationLabel(item, locale)}
              </option>
            ))}
          </select>
        </div>
        <div className="case-field">
          <label>
            <input
              type="checkbox"
              checked={draft.includeWithdrawn}
              onChange={(e) => {
                set('includeWithdrawn', e.target.checked);
              }}
            />{' '}
            {t('library.search.withdrawn')}
          </label>
        </div>
        <div className="library-toolbar library-wide">
          <button type="submit">{t('library.search.submit')}</button>
          <button type="button" onClick={reset}>
            {t('library.search.reset')}
          </button>
        </div>
      </form>

      <section aria-labelledby={`${id}-results`}>
        <h2 id={`${id}-results`}>{t('library.search.results')}</h2>
        {load.kind === 'loading' ? <p role="status">{t('library.loading')}</p> : null}
        {load.kind === 'failed' ? (
          <p className="case-alert" role="alert">
            {t('library.failed')}
          </p>
        ) : null}
        {load.kind === 'loaded' ? (
          <>
            <p role="status">{t('library.search.count', { count: String(load.page.totalItems) })}</p>
            {load.page.items.length === 0 ? <p>{t('library.search.none')}</p> : null}
            <ul className="library-results">
              {load.page.items.map((item) => (
                <li key={item.id} className="library-result">
                  <h3>
                    <Link to={withLang(`/library/${item.id}`, locale)}>{item.subject}</Link>
                  </h3>
                  <p>
                    {citation(item)}
                    {item.judgmentDate === null ? '' : ` - ${formatDate(item.judgmentDate, locale)}`}
                  </p>
                  {item.matchedIn.length === 0 ? null : (
                    <p>{t('library.search.matched', { where: item.matchedIn.map((m) => t(`library.match.${m}` as MessageKey)).join(listSeparator(locale)) })}</p>
                  )}
                  <ul className="library-badges">
                    <li className="library-badge">{t(finalityKey(item.finality))}</li>
                    {item.direction === null ? null : <li className="library-badge">{t(directionKey(item.direction))}</li>}
                    {item.principleCount === 0 ? null : (
                      <li className="library-badge">{t('library.badge.principles', { count: String(item.principleCount) })}</li>
                    )}
                    {item.relationTypes.length === 0 ? null : (
                      <li className="library-badge" data-tone="warn">
                        {t('library.badge.relations')}: {item.relationTypes.map((r) => t(relationKey(r))).join(listSeparator(locale))}
                      </li>
                    )}
                    {item.status === 'WITHDRAWN' ? (
                      <li className="library-badge" data-tone="warn">
                        {t('library.badge.withdrawn')}
                      </li>
                    ) : null}
                    {item.confidentiality === 'RESTRICTED' ? <li className="library-badge">{t('library.badge.restricted')}</li> : null}
                    {item.classifications.map((c) => (
                      <li key={c.id} className="library-badge">
                        {classificationLabel(c, locale)}
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
            {load.page.totalPages > 1 ? (
              <nav className="library-pager" aria-label={t('library.search.results')}>
                <button type="button" disabled={load.page.page === 0} onClick={() => { go(load.page.page - 1); }}>
                  {t('library.search.prev')}
                </button>
                <span>{t('library.search.page', { page: String(load.page.page + 1), pages: String(load.page.totalPages) })}</span>
                <button
                  type="button"
                  disabled={load.page.page + 1 >= load.page.totalPages}
                  onClick={() => { go(load.page.page + 1); }}
                >
                  {t('library.search.next')}
                </button>
              </nav>
            ) : null}
          </>
        ) : null}
      </section>
    </>
  );
}
