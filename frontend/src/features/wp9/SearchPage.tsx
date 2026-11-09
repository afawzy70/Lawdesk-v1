import { type SyntheticEvent, useEffect, useId, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { en } from '../../i18n/messages/en';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { formatDate } from '../cases/format';
import { casePath } from '../cases/paths';
import { runSearch, SEARCH_TYPES, type SearchFilters, type SearchGroup, type SearchItem, type SearchResult, type SearchType } from './api';
import './wp9.css';

const PAGE = 5;
const CASE_TYPES = ['CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE', 'CONSTITUTIONAL', 'OTHER'] as const;
const NO_FILTERS = { types: [] as SearchType[], caseType: '', from: '', to: '' };

type Extra = Partial<Record<SearchType, { items: SearchItem[]; page: number }>>;

type Load = { kind: 'idle' } | { kind: 'loading' } | { kind: 'forbidden' } | { kind: 'failed' } | { kind: 'loaded'; result: SearchResult };

function isMessageKey(key: string): key is MessageKey {
  return key in en;
}

/** Where a hit opens: a library judgment in the library, everything else in the file of its case. */
function destination(item: SearchItem): string | null {
  if (item.type === 'LIBRARY') return `/library/${encodeURIComponent(item.id)}`;
  return item.caseId === null ? null : casePath(item.caseId);
}

/**
 * The unified search (section 28): one query over cases, parties, hearings, judgments, documents' details, notes, tasks, the
 * judgments library, settlements and directives, grouped by type. The server restricts every group to what the user may see.
 */
export function SearchPage() {
  const { t } = useI18n();
  usePageTitle(t('wp9.search.title'));
  const id = useId();
  const [params, setParams] = useSearchParams();
  const q = params.get('q')?.trim() ?? '';
  const [filters, setFilters] = useState(NO_FILTERS);
  const [load, setLoad] = useState<{ key: string; state: Load } | null>(null);
  const [moreState, setMore] = useState<{ key: string; byType: Extra }>({ key: '', byType: {} });
  const key = JSON.stringify([q, filters]);
  // The further pages a user asked for belong to the query and filters they were asked under.
  const extra: Extra = moreState.key === key ? moreState.byType : {};
  const state: Load = q === '' ? { kind: 'idle' } : load?.key === key ? load.state : { kind: 'loading' };
  const request: SearchFilters = { q, ...filters };

  useEffect(() => {
    if (q === '') return undefined;
    const controller = new AbortController();
    runSearch({ q, ...filters }, { page: 0, size: PAGE }, controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) setLoad({ key, state: { kind: 'loaded', result } });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        setLoad({ key, state: error instanceof ApiError && error.status === 403 ? { kind: 'forbidden' } : { kind: 'failed' } });
      },
    );
    return () => {
      controller.abort();
    };
  }, [q, filters, key]);

  if (state.kind === 'forbidden') return <AccessDeniedPage />;

  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    const typed = new FormData(event.currentTarget as HTMLFormElement).get('q');
    const words = typeof typed === 'string' ? typed.trim() : '';
    const next = new URLSearchParams(params);
    if (words === '') next.delete('q');
    else next.set('q', words);
    setParams(next);
  };

  const toggle = (type: SearchType) => {
    setFilters((current) => ({
      ...current,
      types: current.types.includes(type) ? current.types.filter((x) => x !== type) : [...current.types, type],
    }));
  };

  const more = async (group: SearchGroup) => {
    const shown = extra[group.type] ?? { items: [], page: 0 };
    const page = shown.page + 1;
    try {
      const result = await runSearch({ ...request, types: [group.type] }, { page, size: PAGE });
      const next = result.groups.find((g) => g.type === group.type);
      setMore({ key, byType: { ...extra, [group.type]: { items: [...shown.items, ...(next?.items ?? [])], page } } });
    } catch {
      setLoad({ key, state: { kind: 'failed' } });
    }
  };

  return (
    <>
      <h1>{t('wp9.search.title')}</h1>
      <p>{t('wp9.search.intro')}</p>
      <form className="wp9-filters" role="search" aria-label={t('wp9.search.form')} noValidate onSubmit={submit}>
        <div className="case-field wp9-wide">
          <label htmlFor={`${id}-q`}>{t('wp9.search.q')}</label>
          <input
            id={`${id}-q`}
            type="search"
            dir="auto"
            maxLength={200}
            aria-describedby={`${id}-q-hint`}
            key={q}
            name="q"
            defaultValue={q}
          />
          <small id={`${id}-q-hint`}>{t('wp9.search.qHint')}</small>
        </div>
        <fieldset className="case-field wp9-wide">
          <legend>{t('wp9.search.types')}</legend>
          {SEARCH_TYPES.map((type) => (
            <label key={type} style={{ marginInlineEnd: '1rem' }}>
              <input
                type="checkbox"
                checked={filters.types.includes(type)}
                onChange={() => {
                  toggle(type);
                }}
              />{' '}
              {t(`wp9.search.type.${type}`)}
            </label>
          ))}
        </fieldset>
        <div className="case-field">
          <label htmlFor={`${id}-caseType`}>{t('wp9.search.caseType')}</label>
          <select
            id={`${id}-caseType`}
            value={filters.caseType}
            onChange={(e) => {
              setFilters({ ...filters, caseType: e.target.value });
            }}
          >
            <option value="">{t('wp9.any')}</option>
            {CASE_TYPES.map((type) => (
              <option key={type} value={type}>
                {t(`cases.type.${type}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-from`}>{t('wp9.search.from')}</label>
          <input
            id={`${id}-from`}
            type="date"
            value={filters.from}
            onChange={(e) => {
              setFilters({ ...filters, from: e.target.value });
            }}
          />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-to`}>{t('wp9.search.to')}</label>
          <input
            id={`${id}-to`}
            type="date"
            value={filters.to}
            onChange={(e) => {
              setFilters({ ...filters, to: e.target.value });
            }}
          />
        </div>
        <div className="case-actions wp9-wide">
          <button type="submit" className="primary">
            {t('wp9.search')}
          </button>
        </div>
      </form>

      {state.kind === 'loading' ? <p role="status">{t('wp9.loading')}</p> : null}
      {state.kind === 'failed' ? (
        <p className="case-alert" role="alert">
          {t('wp9.search.failed')}
        </p>
      ) : null}
      {state.kind !== 'loaded' ? null : (
        <div aria-live="polite">
          <p>{t('wp9.search.total', { count: String(state.result.total) })}</p>
          <p className="case-field-hint">
            {t('wp9.search.searched', { types: state.result.searchedTypes.map((type) => t(`wp9.search.type.${type}`)).join('، ') })}
          </p>
          {state.result.groups.length === 0 ? (
            <p>{t('wp9.search.none')}</p>
          ) : (
            <ul className="wp9-groups">
              {state.result.groups.map((group) => {
                const items = [...group.items, ...(extra[group.type]?.items ?? [])];
                return (
                  <li key={group.type}>
                    <section aria-labelledby={`${id}-${group.type}`}>
                      <h2 id={`${id}-${group.type}`}>
                        {t(`wp9.search.type.${group.type}`)} ({group.total})
                      </h2>
                      <ul className="wp9-hits">
                        {items.map((item) => (
                          <Hit key={item.id} item={item} />
                        ))}
                      </ul>
                      {items.length >= group.total ? null : (
                        <p>
                          <button
                            type="button"
                            onClick={() => {
                              void more(group);
                            }}
                          >
                            {t('wp9.search.more', { type: t(`wp9.search.type.${group.type}`) })}
                          </button>
                        </p>
                      )}
                    </section>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </>
  );
}

function Hit({ item }: { item: SearchItem }) {
  const { t, locale } = useI18n();
  const target = destination(item);
  const where = (
    <>
      {item.title}
      {item.caseReferenceNo === null ? '' : ` — ${item.caseReferenceNo}`}
    </>
  );
  const labels = item.matchedIn.map((part) => {
    const key = `wp9.search.in.${part}`;
    return isMessageKey(key) ? t(key) : part;
  });
  return (
    <li>
      <p>
        <strong>{target === null ? where : <Link to={withLang(target, locale)}>{where}</Link>}</strong>
        {item.date === null ? null : ` · ${formatDate(item.date, locale)}`}
        {item.type !== 'PARTY' || item.caseCount === null ? null : ` · ${t('wp9.search.partyCases', { count: String(item.caseCount) })}`}
      </p>
      {item.snippet === null ? null : (
        <p className="wp9-snippet" dir="auto">
          {item.snippet}
        </p>
      )}
      {labels.length === 0 ? null : <p className="case-field-hint">{t('wp9.search.matched', { parts: labels.join('، ') })}</p>}
    </li>
  );
}
