import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../../api/http';
import type { MessageKey } from '../../../i18n/catalog';
import { formatNumber } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { usePageTitle } from '../../../i18n/usePageTitle';
import { AccessDeniedPage } from '../../auth/AccessDeniedPage';
import { casePath } from '../../cases/paths';
import { numberOf } from '../../proceedings/dates';
import { addDays, formatDay, formatTime, legalToday } from '../days';
import { loadError } from '../formErrors';
import { HearingResultForm, RescheduleHearingForm } from './HearingForms';
import {
  HEARING_STATUSES,
  listCalendar,
  type CalendarFilter,
  type Hearing,
  type HearingPage,
  type HearingStatus,
} from './hearingsApi';
import '../operations.css';

/** The default calendar window: today and the next 30 days (ASSUMPTION, PHASE-5-ACCEPTANCE LD-024.6). */
const WINDOW_DAYS = 30;

type Result = { key: string; page: HearingPage } | { key: string; error: MessageKey };
type Open = null | { kind: 'result' | 'reschedule'; hearing: Hearing };

function calendarError(error: unknown): MessageKey {
  // A 400 here can only be the date range (to before from, or more than 366 days).
  return error instanceof ApiError && error.status === 400 ? 'operations.hearing.error.range' : loadError(error);
}

/**
 * The hearing calendar (LD-024, LD-028): the hearings of every case in a date range, as an agenda list, with the
 * result and reschedule forms. The backend checks HEARING_VIEW and HEARING_MANAGE; this screen explains its answers.
 */
export function HearingsPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('operations.hearings.title'));
  const captionId = useId();
  const filterId = useId();
  const [filter, setFilter] = useState<CalendarFilter>(() => {
    const today = legalToday();
    return { from: today, to: addDays(today, WINDOW_DAYS), page: 0 };
  });
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [open, setOpen] = useState<Open>(null);
  const [saved, setSaved] = useState(false);
  const requestKey = `${JSON.stringify(filter)}#${String(reload)}`;

  useEffect(() => {
    const controller = new AbortController();
    listCalendar(filter, controller.signal).then(
      (page) => {
        setResult({ key: requestKey, page });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, error: calendarError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [filter, requestKey]);

  const current = result?.key === requestKey ? result : null;

  if (current !== null && 'error' in current && current.error === 'operations.error.viewForbidden') {
    return <AccessDeniedPage />;
  }

  const onSaved = () => {
    setOpen(null);
    setSaved(true);
    setReload((n) => n + 1);
  };
  const show = (next: Open) => {
    setSaved(false);
    setOpen(next);
  };

  const row = (h: Hearing) => (
    <tr key={h.id}>
      <td>{formatDay(h.hearingDate, locale)}</td>
      <td>{h.hearingTime === null ? t('operations.hearing.noTime') : formatTime(h.hearingTime, locale)}</td>
      <td className="ops-wrap">
        <Link to={`${casePath(h.caseId)}?lang=${locale}`}>
          <bdi dir="ltr">{h.caseReferenceNo}</bdi>
        </Link>{' '}
        {h.caseTitle}
      </td>
      <td className="ops-wrap">{locale === 'ar' ? h.courtNameAr : h.courtNameEn}</td>
      <td>
        <bdi dir="ltr">{numberOf(h)}</bdi>
      </td>
      <td>{t(`operations.purpose.${h.purpose}`)}</td>
      <td>
        {t(`operations.hearingStatus.${h.status}`)}
        {h.awaitingResult ? <span className="ops-badge ops-badge-warn">{t('operations.hearing.awaiting')}</span> : null}
      </td>
      <td>
        {h.status === 'SCHEDULED' ? (
          <div className="ops-actions">
            <button
              type="button"
              aria-label={t('operations.hearing.resultLabel', {
                date: formatDay(h.hearingDate, locale),
                reference: h.caseReferenceNo,
              })}
              onClick={() => {
                show({ kind: 'result', hearing: h });
              }}
            >
              {t('operations.hearing.recordResult')}
            </button>
            <button
              type="button"
              aria-label={t('operations.hearing.rescheduleLabel', {
                date: formatDay(h.hearingDate, locale),
                reference: h.caseReferenceNo,
              })}
              onClick={() => {
                show({ kind: 'reschedule', hearing: h });
              }}
            >
              {t('operations.hearing.reschedule')}
            </button>
          </div>
        ) : (
          (h.decision ?? '')
        )}
      </td>
    </tr>
  );

  return (
    <>
      <h1>{t('operations.hearings.title')}</h1>
      <p>{t('operations.hearings.intro')}</p>

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

      {saved ? (
        <p role="status" className="ops-saved">
          {t('operations.hearing.saved')}
        </p>
      ) : null}

      {open?.kind === 'result' ? (
        <HearingResultForm
          key={open.hearing.id}
          hearing={open.hearing}
          onSaved={onSaved}
          onCancel={() => {
            setOpen(null);
          }}
        />
      ) : null}
      {open?.kind === 'reschedule' ? (
        <RescheduleHearingForm
          key={open.hearing.id}
          hearing={open.hearing}
          onSaved={onSaved}
          onCancel={() => {
            setOpen(null);
          }}
        />
      ) : null}

      {current === null ? <p>{t('operations.loading')}</p> : null}

      {current !== null && 'error' in current ? (
        <div role="alert" className="ops-alert">
          <p>{t(current.error)}</p>
          {current.error === 'operations.error.load' ? (
            <button
              type="button"
              onClick={() => {
                setReload((n) => n + 1);
              }}
            >
              {t('operations.retry')}
            </button>
          ) : null}
        </div>
      ) : null}

      {current !== null && 'page' in current && current.page.items.length === 0 ? (
        <p>{t('operations.hearings.empty')}</p>
      ) : null}

      {current !== null && 'page' in current && current.page.items.length > 0 ? (
        <>
          <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
            <table className="ops-table">
              <caption id={captionId} className="visually-hidden">
                {t('operations.hearings.title')}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('operations.hearing.date')}</th>
                  <th scope="col">{t('operations.hearing.time')}</th>
                  <th scope="col">{t('operations.hearing.case')}</th>
                  <th scope="col">{t('operations.hearing.court')}</th>
                  <th scope="col">{t('operations.hearing.number')}</th>
                  <th scope="col">{t('operations.hearing.purpose')}</th>
                  <th scope="col">{t('operations.hearing.status')}</th>
                  <th scope="col">{t('operations.hearing.actionsOrDecision')}</th>
                </tr>
              </thead>
              <tbody>{current.page.items.map(row)}</tbody>
            </table>
          </div>
          {current.page.totalPages > 1 ? (
            <nav className="ops-pager" aria-label={t('operations.hearings.pages')}>
              <button
                type="button"
                disabled={current.page.page === 0}
                onClick={() => {
                  setFilter((f) => ({ ...f, page: f.page - 1 }));
                }}
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
                onClick={() => {
                  setFilter((f) => ({ ...f, page: f.page + 1 }));
                }}
              >
                {t('operations.next')}
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </>
  );
}
