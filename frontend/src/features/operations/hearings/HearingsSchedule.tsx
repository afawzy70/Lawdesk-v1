import { useEffect, useId, useState } from 'react';
import { ApiError } from '../../../api/http';
import type { MessageKey } from '../../../i18n/catalog';
import { formatNumber } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { addDays, legalToday } from '../days';
import { loadError } from '../formErrors';
import { HearingsTable } from './HearingsTable';
import { HEARING_STATUSES, listCalendar, type CalendarFilter, type HearingPage, type HearingStatus } from './hearingsApi';

/** The default window: today and the next 30 days (ASSUMPTION, PHASE-5-ACCEPTANCE LD-024.6). */
const WINDOW_DAYS = 30;

type Result = { key: string; page: HearingPage } | { key: string; error: MessageKey };

function scheduleError(error: unknown): MessageKey {
  // A 400 here can only be the date range (to before from, or more than 366 days).
  return error instanceof ApiError && error.status === 400 ? 'operations.hearing.error.range' : loadError(error);
}

/**
 * The hearings schedule as a table (requirements section 15), read from the calendar: the hearings of every case the
 * user may see in a period, filtered by period and status, page by page. The backend checks HEARING_VIEW and the case
 * access rules; this view only explains its answers. Entering hearings and recording results is on the hearings page.
 */
export function HearingsSchedule() {
  const { locale, t } = useI18n();
  const filterId = useId();
  const [filter, setFilter] = useState<CalendarFilter>(() => {
    const today = legalToday();
    return { from: today, to: addDays(today, WINDOW_DAYS), page: 0 };
  });
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const requestKey = `${JSON.stringify(filter)}#${String(reload)}`;

  useEffect(() => {
    const controller = new AbortController();
    listCalendar(filter, controller.signal).then(
      (page) => {
        setResult({ key: requestKey, page });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, error: scheduleError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [filter, requestKey]);

  const current = result?.key === requestKey ? result : null;

  return (
    <div className="wp6-hearings-schedule">
      <h2>{t('operations.hearings.title')}</h2>
      <fieldset className="ops-filters">
        <legend>{t('operations.filters')}</legend>
        <label htmlFor={`${filterId}-from`}>{t('operations.hearings.from')}</label>
        <input
          id={`${filterId}-from`}
          type="date"
          value={filter.from}
          onChange={(event) => {
            const value = event.target.value;
            if (value !== '') setFilter((f) => ({ ...f, from: value, page: 0 }));
          }}
        />
        <label htmlFor={`${filterId}-to`}>{t('operations.hearings.to')}</label>
        <input
          id={`${filterId}-to`}
          type="date"
          value={filter.to}
          onChange={(event) => {
            const value = event.target.value;
            if (value !== '') setFilter((f) => ({ ...f, to: value, page: 0 }));
          }}
        />
        <label htmlFor={`${filterId}-status`}>{t('operations.hearings.status')}</label>
        <select
          id={`${filterId}-status`}
          value={filter.status ?? ''}
          onChange={(event) => {
            const value = event.target.value;
            setFilter((f) => ({ ...f, page: 0, status: value === '' ? undefined : (value as HearingStatus) }));
          }}
        >
          <option value="">{t('operations.all')}</option>
          {HEARING_STATUSES.map((status) => (
            <option key={status} value={status}>
              {t(`operations.hearingStatus.${status}`)}
            </option>
          ))}
        </select>
      </fieldset>

      {current === null ? <p>{t('operations.loading')}</p> : null}

      {current !== null && 'error' in current ? (
        <div role="alert" className="ops-alert">
          <p>{t(current.error)}</p>
          {current.error === 'operations.error.load' ? (
            <button type="button" onClick={() => { setReload((n) => n + 1); }}>
              {t('operations.retry')}
            </button>
          ) : null}
        </div>
      ) : null}

      {current !== null && 'page' in current && current.page.items.length === 0 ? <p>{t('operations.hearings.empty')}</p> : null}

      {current !== null && 'page' in current && current.page.items.length > 0 ? (
        <>
          <HearingsTable hearings={current.page.items} caption={t('operations.hearings.title')} />
          {current.page.totalPages > 1 ? (
            <nav className="ops-pager" aria-label={t('operations.hearings.pages')}>
              <button
                type="button"
                disabled={current.page.page === 0}
                onClick={() => { setFilter((f) => ({ ...f, page: f.page - 1 })); }}
              >
                {t('operations.previous')}
              </button>
              <span>
                {t('operations.pageOf', {
                  page: formatNumber(current.page.page + 1, locale),
                  pages: formatNumber(current.page.totalPages, locale),
                })}
              </span>
              <button
                type="button"
                disabled={current.page.page + 1 >= current.page.totalPages}
                onClick={() => { setFilter((f) => ({ ...f, page: f.page + 1 })); }}
              >
                {t('operations.next')}
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
