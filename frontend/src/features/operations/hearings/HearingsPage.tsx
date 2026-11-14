import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../../api/http';
import type { MessageKey } from '../../../i18n/catalog';
import { formatNumber } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { withLang } from '../../../i18n/locale';
import { usePageTitle } from '../../../i18n/usePageTitle';
import { AccessDeniedPage } from '../../auth/AccessDeniedPage';
import { addDays, formatDay, legalToday } from '../days';
import { loadError } from '../formErrors';
import { HearingFollowUp } from './HearingFollowUp';
import { HearingResultForm, RescheduleHearingForm } from './HearingForms';
import { HearingsTable } from './HearingsTable';
import { listCalendar, type CalendarFilter, type Hearing, type HearingPage } from './hearingsApi';
import '../operations.css';

/** Hearings still marked scheduled whose day has passed are the ones waiting for a result; the API allows 366 days. */
const LOOKBACK_DAYS = 365;

type Result = { key: string; page: HearingPage } | { key: string; error: MessageKey };
type Open = null | { kind: 'result' | 'reschedule'; hearing: Hearing };

function calendarError(error: unknown): MessageKey {
  // A 400 here can only be the date range (to before from, or more than 366 days).
  return error instanceof ApiError && error.status === 400 ? 'operations.hearing.error.range' : loadError(error);
}

/**
 * Hearing results and follow-up (LD-024, LD-028, requirements section 7): the hearings whose day has passed and that
 * still wait for a result, with the result and reschedule forms, and the follow-up list of adjourned hearings with no
 * next date. The schedule itself is a table in the calendar; this page links to it. The backend checks HEARING_VIEW and
 * HEARING_MANAGE; this screen explains its answers.
 */
export function HearingsPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('operations.hearings.pageTitle'));
  const [filter, setFilter] = useState<CalendarFilter>(() => {
    const today = legalToday();
    return { from: addDays(today, -LOOKBACK_DAYS), to: today, status: 'SCHEDULED', page: 0 };
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

  const actions = {
    header: t('operations.hearing.actionsOrDecision'),
    cell: (h: Hearing) => (
      <div className="ops-actions">
        <button
          type="button"
          aria-label={t('operations.hearing.resultLabel', { date: formatDay(h.hearingDate, locale), reference: h.caseReferenceNo })}
          onClick={() => { show({ kind: 'result', hearing: h }); }}
        >
          {t('operations.hearing.recordResult')}
        </button>
        <button
          type="button"
          aria-label={t('operations.hearing.rescheduleLabel', { date: formatDay(h.hearingDate, locale), reference: h.caseReferenceNo })}
          onClick={() => { show({ kind: 'reschedule', hearing: h }); }}
        >
          {t('operations.hearing.reschedule')}
        </button>
      </div>
    ),
  };

  return (
    <>
      <h1>{t('operations.hearings.pageTitle')}</h1>
      <p>{t('operations.hearings.intro')}</p>
      <p>
        <Link to={`${withLang('/calendar', locale)}&view=hearings`}>{t('operations.hearings.toSchedule')}</Link>
      </p>

      {saved ? (
        <p role="status" className="ops-saved">
          {t('operations.hearing.saved')}
        </p>
      ) : null}

      {open?.kind === 'result' ? (
        <HearingResultForm key={open.hearing.id} hearing={open.hearing} onSaved={onSaved} onCancel={() => { setOpen(null); }} />
      ) : null}
      {open?.kind === 'reschedule' ? (
        <RescheduleHearingForm key={open.hearing.id} hearing={open.hearing} onSaved={onSaved} onCancel={() => { setOpen(null); }} />
      ) : null}

      <HearingFollowUp refresh={reload} />

      <h2>{t('operations.hearings.dueTitle')}</h2>
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

      {current !== null && 'page' in current && current.page.items.length === 0 ? <p>{t('operations.hearings.dueEmpty')}</p> : null}

      {current !== null && 'page' in current && current.page.items.length > 0 ? (
        <>
          <HearingsTable hearings={current.page.items} caption={t('operations.hearings.dueTitle')} actions={actions} />
          {current.page.totalPages > 1 ? (
            <nav className="ops-pager" aria-label={t('operations.hearings.pages')}>
              <button type="button" disabled={current.page.page === 0} onClick={() => { setFilter((f) => ({ ...f, page: f.page - 1 })); }}>
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
    </>
  );
}
