import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { casePath } from '../cases/paths';
import { ChoiceField, Notice, TextField } from '../pleadings/ui';
import { useLoad } from '../pleadings/useLoad';
import { asList, getArchiveSettings, listCategories, searchDocuments, type SearchPage } from './api';
import { archiveError, statusOf } from './errors';
import './archive.css';

/**
 * Search inside the documents of the cases the user may see (requirement 13). The backend returns only documents of visible
 * cases; the snippet is plain text taken from the file and is shown as text, never as markup.
 */
export function ArchiveSearchPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('wp5.search.title'));
  const resultsId = useId();
  const categories = useLoad((signal) => listCategories(signal), []);
  const settings = useLoad((signal) => getArchiveSettings(signal), []);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('');
  const [submitted, setSubmitted] = useState<{ q: string; category: string } | null>(null);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<{ data: SearchPage | null; error: ReturnType<typeof archiveError> | null; status: number | null } | null>(null);
  const [short, setShort] = useState(false);

  useEffect(() => {
    if (submitted === null) return;
    const controller = new AbortController();
    searchDocuments(submitted.q, submitted.category, page, controller.signal).then(
      (data) => {
        setResult({ data, error: null, status: null });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ data: null, error: archiveError(error), status: statusOf(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [submitted, page]);

  if (result?.status === 403) return <AccessDeniedPage />;
  const hits = result?.data ?? null;
  return (
    <>
      <h1>{t('wp5.search.title')}</h1>
      <p>{t('wp5.search.intro')}</p>
      {settings.data !== null && !settings.data.ocr.available ? <p className="case-notice">{t('wp5.search.engineOff')}</p> : null}
      <form
        className="case-form archive-search"
        role="search"
        aria-label={t('wp5.search.title')}
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          if (query.replace(/[^\p{L}\p{N}]/gu, '').length < 2) {
            setShort(true);
            return;
          }
          setShort(false);
          setResult(null);
          setPage(0);
          setSubmitted({ q: query.trim(), category });
        }}
      >
        <TextField label={t('wp5.search.query')} value={query} onChange={setQuery} max={200} invalid={short} />
        <ChoiceField
          label={t('wp5.search.category')}
          value={category}
          onChange={setCategory}
          options={asList(categories.data).map((c) => [c.id, locale === 'ar' ? c.labelAr : (c.labelEn ?? c.labelAr)] as const)}
          empty={t('wp5.search.anyCategory')}
        />
        {short ? <Notice failed>{t('wp5.search.tooShort')}</Notice> : null}
        <button type="submit">{t('wp5.search.submit')}</button>
      </form>
      {submitted !== null && result === null ? <p>{t('wp5.loading')}</p> : null}
      {result?.error != null ? <Notice failed>{t(result.error)}</Notice> : null}
      {hits !== null ? (
        <section aria-labelledby={resultsId}>
          <h2 id={resultsId}>{t('wp5.search.results')}</h2>
          <p role="status">{hits.totalItems === 0 ? t('wp5.search.none') : t('wp5.search.count', { count: formatNumber(hits.totalItems, locale) })}</p>
          <ul className="archive-list">
            {hits.items.map((hit) => (
              <li key={hit.documentId} className="archive-item">
                <h3>{hit.title}</h3>
                <p className="archive-meta">
                  {t('wp5.search.case')}: <bdi dir="ltr">{hit.caseReferenceNo ?? hit.caseId}</bdi> {hit.caseTitle ?? ''} · {t('wp5.search.version', { number: String(hit.versionNo) })}
                  {hit.status === 'OCR_DONE' ? ` · ${t('wp5.search.read')}` : ''}
                </p>
                {hit.snippet === null ? null : <p className="archive-snippet">{hit.snippet}</p>}
                <Link to={`${casePath(hit.caseId)}?lang=${locale}`}>{t('wp5.search.open')}</Link>
              </li>
            ))}
          </ul>
          {hits.totalPages > 1 ? (
            <nav className="archive-pager" aria-label={t('wp5.search.results')}>
              <button type="button" disabled={hits.page === 0} onClick={() => { setPage((p) => p - 1); }}>
                {t('wp5.previous')}
              </button>
              <span>{t('wp5.page', { page: formatNumber(hits.page + 1, locale), pages: formatNumber(hits.totalPages, locale) })}</span>
              <button type="button" disabled={hits.page + 1 >= hits.totalPages} onClick={() => { setPage((p) => p + 1); }}>
                {t('wp5.next')}
              </button>
            </nav>
          ) : null}
        </section>
      ) : null}
    </>
  );
}
