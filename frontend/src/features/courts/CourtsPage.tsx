import { useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { CourtForm } from './CourtForm';
import { COURT_LEVELS, listCourts, type Court, type CourtFilter, type CourtLevel, type CourtPage } from './courtsApi';
import './courts.css';

type Load = { state: 'loading' } | { state: 'loaded'; page: CourtPage } | { state: 'failed'; error: MessageKey };

/** The answer for one request; a result for an older request means the current one is still loading. */
type Result = { key: string; page: CourtPage } | { key: string; error: MessageKey };

/** Which form is open: none, a new court, or the court being edited. */
type Editing = null | 'new' | Court;

function loadError(error: unknown): MessageKey {
  if (error instanceof ApiError && error.status === 403) return 'courts.error.viewForbidden';
  if (error instanceof ApiError && error.status === 401) return 'courts.error.signIn';
  return 'courts.error.load';
}

/**
 * Court administration (LD-018): the reference list of courts, filtered by level and status, with create and edit.
 * The backend enforces COURT_VIEW and COURT_MANAGE; this screen only explains its answers.
 */
export function CourtsPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('courts.title'));
  const captionId = useId();
  const filterId = useId();
  const [filter, setFilter] = useState<CourtFilter>({ page: 0 });
  const [result, setResult] = useState<Result | null>(null);
  const [reload, setReload] = useState(0);
  const requestKey = `${JSON.stringify(filter)}#${String(reload)}`;
  const [editing, setEditing] = useState<Editing>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    listCourts(filter, controller.signal).then(
      (page) => {
        setResult({ key: requestKey, page });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, error: loadError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [filter, requestKey]);

  let load: Load = { state: 'loading' };
  if (result?.key === requestKey) {
    load = 'page' in result ? { state: 'loaded', page: result.page } : { state: 'failed', error: result.error };
  }

  const onSaved = () => {
    setEditing(null);
    setSaved(true);
    setReload((n) => n + 1);
  };

  const open = (target: Editing) => {
    setSaved(false);
    setEditing(target);
  };

  return (
    <>
      <h1>{t('courts.title')}</h1>
      <p>{t('courts.intro')}</p>

      <div className="courts-toolbar">
        <fieldset className="courts-filters">
          <legend>{t('courts.filters')}</legend>
          <label htmlFor={`${filterId}-level`}>{t('courts.filter.level')}</label>
          <select
            id={`${filterId}-level`}
            value={filter.level ?? ''}
            onChange={(event) => {
              const value = event.target.value;
              setFilter((f) => ({ ...f, page: 0, level: value === '' ? undefined : (value as CourtLevel) }));
            }}
          >
            <option value="">{t('courts.filter.all')}</option>
            {COURT_LEVELS.map((level) => (
              <option key={level} value={level}>
                {t(`courts.level.${level}`)}
              </option>
            ))}
          </select>
          <label htmlFor={`${filterId}-status`}>{t('courts.filter.status')}</label>
          <select
            id={`${filterId}-status`}
            value={filter.active === undefined ? '' : String(filter.active)}
            onChange={(event) => {
              const value = event.target.value;
              setFilter((f) => ({ ...f, page: 0, active: value === '' ? undefined : value === 'true' }));
            }}
          >
            <option value="">{t('courts.filter.all')}</option>
            <option value="true">{t('courts.active')}</option>
            <option value="false">{t('courts.inactive')}</option>
          </select>
        </fieldset>
        <button
          type="button"
          onClick={() => {
            open('new');
          }}
        >
          {t('courts.new')}
        </button>
      </div>

      {saved ? (
        <p role="status" className="court-saved">
          {t('courts.saved')}
        </p>
      ) : null}

      {editing === null ? null : (
        <CourtForm
          key={editing === 'new' ? 'new' : editing.id}
          court={editing === 'new' ? undefined : editing}
          onSaved={onSaved}
          onCancel={() => {
            setEditing(null);
          }}
        />
      )}

      {load.state === 'loading' ? <p>{t('courts.loading')}</p> : null}

      {load.state === 'failed' ? (
        <div role="alert" className="court-alert">
          <p>{t(load.error)}</p>
          {load.error === 'courts.error.load' ? (
            <button
              type="button"
              onClick={() => {
                setReload((n) => n + 1);
              }}
            >
              {t('courts.retry')}
            </button>
          ) : null}
        </div>
      ) : null}

      {load.state === 'loaded' && load.page.items.length === 0 ? <p>{t('courts.empty')}</p> : null}

      {load.state === 'loaded' && load.page.items.length > 0 ? (
        <>
          <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
            <table className="courts-table">
              <caption id={captionId} className="visually-hidden">
                {t('courts.title')}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('courts.code')}</th>
                  <th scope="col">{t('courts.nameAr')}</th>
                  <th scope="col">{t('courts.nameEn')}</th>
                  <th scope="col">{t('courts.level')}</th>
                  <th scope="col">{t('courts.governorate')}</th>
                  <th scope="col">{t('courts.status')}</th>
                  <th scope="col">{t('courts.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {load.page.items.map((court) => (
                  <tr key={court.id}>
                    <td dir="ltr">{court.code}</td>
                    <td dir="rtl" lang="ar">
                      {court.nameAr}
                    </td>
                    <td dir="ltr" lang="en">
                      {court.nameEn}
                    </td>
                    <td>{t(`courts.level.${court.level}`)}</td>
                    <td>{court.governorate ?? ''}</td>
                    <td>{court.active ? t('courts.active') : t('courts.inactive')}</td>
                    <td>
                      <button
                        type="button"
                        aria-label={t('courts.editLabel', { code: court.code })}
                        onClick={() => {
                          open(court);
                        }}
                      >
                        {t('courts.edit')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {load.page.totalPages > 1 ? (
            <nav className="courts-pager" aria-label={t('courts.title')}>
              <button
                type="button"
                disabled={load.page.page === 0}
                onClick={() => {
                  setFilter((f) => ({ ...f, page: f.page - 1 }));
                }}
              >
                {t('courts.previous')}
              </button>
              <span>
                {t('courts.pageOf', {
                  page: formatNumber(load.page.page + 1, locale),
                  pages: formatNumber(load.page.totalPages, locale),
                })}
              </span>
              <button
                type="button"
                disabled={load.page.page + 1 >= load.page.totalPages}
                onClick={() => {
                  setFilter((f) => ({ ...f, page: f.page + 1 }));
                }}
              >
                {t('courts.next')}
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </>
  );
}
