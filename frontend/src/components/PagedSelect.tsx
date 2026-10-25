import { useEffect, useRef, useState, type SelectHTMLAttributes } from 'react';
import { useI18n } from '../i18n/I18nContext';
import './pagedSelect.css';

/**
 * A selector over a list that may be longer than one page (QAD-044). It reads the server's pages on request, shows how
 * many of how many it holds, searches on the server where the API can (`serverSearch`) and otherwise filters what is
 * loaded, and keeps the chosen record selectable whatever the search shows. A plain `<select>` and buttons keep it
 * operable with the keyboard and with assistive technology.
 */
export interface PagedResult<T> {
  items: T[];
  totalItems: number;
  totalPages: number;
}

interface Props<T extends { id: string }> {
  selectProps: SelectHTMLAttributes<HTMLSelectElement>;
  value: string;
  onChange: (id: string) => void;
  load: (page: number, query: string, signal: AbortSignal) => Promise<PagedResult<T>>;
  optionLabel: (item: T) => string;
  /** Text matched by the local filter; defaults to the option label. */
  searchText?: (item: T) => string;
  serverSearch: boolean;
  searchLabel: string;
  chooseLabel: string;
}

const SEARCH_DELAY_MS = 250;

export function PagedSelect<T extends { id: string }>({
  selectProps,
  value,
  onChange,
  load,
  optionLabel,
  searchText,
  serverSearch,
  searchLabel,
  chooseLabel,
}: Props<T>) {
  const { t } = useI18n();
  const [items, setItems] = useState<T[]>([]);
  const [page, setPage] = useState(-1);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [typed, setTyped] = useState('');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [chosen, setChosen] = useState<T | null>(null);
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });
  const controller = useRef<AbortController | null>(null);

  const read = (target: number, term: string, append: boolean) => {
    controller.current?.abort();
    const mine = new AbortController();
    controller.current = mine;
    loadRef.current(target, term, mine.signal).then(
      (result) => {
        if (mine.signal.aborted) return;
        setItems((previous) => {
          if (!append) return result.items;
          const known = new Set(previous.map((i) => i.id));
          return [...previous, ...result.items.filter((i) => !known.has(i.id))];
        });
        setPage(target);
        setFailed(false);
        setTotalItems(result.totalItems);
        setTotalPages(result.totalPages);
        setLoading(false);
      },
      () => {
        if (mine.signal.aborted) return;
        setFailed(true);
        setLoading(false);
      },
    );
  };

  useEffect(() => {
    if (!serverSearch) return;
    const timer = setTimeout(() => {
      setQuery(typed.trim());
    }, SEARCH_DELAY_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [typed, serverSearch]);

  useEffect(() => {
    read(0, serverSearch ? query : '', false);
    return () => {
      controller.current?.abort();
    };
  }, [query, serverSearch]);

  const term = typed.trim().toLowerCase();
  const visible =
    serverSearch || term === ''
      ? items
      : items.filter((i) => (searchText ? searchText(i) : optionLabel(i)).toLowerCase().includes(term));
  const shown = chosen !== null && chosen.id === value && !visible.some((i) => i.id === value) ? [chosen, ...visible] : visible;
  const hasMore = page + 1 < totalPages;

  let status: string | null = null;
  if (failed) status = null;
  else if (!serverSearch && term !== '' && visible.length === 0 && items.length > 0)
    status = t('selector.noMatchLoaded', { loaded: String(items.length) });
  else if (page >= 0) status = t('selector.showing', { shown: String(serverSearch || term === '' ? items.length : visible.length), total: String(totalItems) });

  return (
    <div className="paged-select">
      <input
        type="search"
        aria-label={searchLabel}
        value={typed}
        onChange={(event) => {
          setTyped(event.target.value);
        }}
      />
      <select
        {...selectProps}
        value={value}
        onChange={(event) => {
          const id = event.target.value;
          setChosen(items.find((i) => i.id === id) ?? null);
          onChange(id);
        }}
      >
        <option value="">{chooseLabel}</option>
        {shown.map((item) => (
          <option key={item.id} value={item.id}>
            {optionLabel(item)}
          </option>
        ))}
      </select>
      {status === null ? null : (
        <p className="ops-hint" role="status">
          {status}
        </p>
      )}
      {failed ? (
        <p role="alert" className="ops-field-error">
          {t('selector.failed')}
        </p>
      ) : null}
      {hasMore ? (
        <button
          type="button"
          disabled={loading}
          onClick={() => {
            setLoading(true);
            read(page + 1, serverSearch ? query : '', true);
          }}
        >
          {t('selector.loadMore')}
        </button>
      ) : null}
    </div>
  );
}
