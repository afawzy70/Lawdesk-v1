import { useContext, useEffect, useId, useState } from 'react';
import { useSearchParams } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { AuthContext } from '../auth/AuthContext';
import { loadError } from '../operations/formErrors';
import { formatDate } from '../proceedings/dates';
import {
  ENTRY_TYPES,
  loadCalendar,
  periodOf,
  shift,
  type CalendarEntry,
  type CalendarFilter,
  type CalendarMode,
  type CalendarView,
  type Conflict,
  type EntryType,
} from './api';
import { EntryRow } from './EntryRow';
import { ManualDateForm } from './ManualDateForm';
import './calendar.css';

type Result = { key: string; view: CalendarView } | { key: string; error: MessageKey };

const MODES: readonly CalendarMode[] = ['MONTH', 'WEEK', 'DAY', 'LIST'];

/** Today's date as the user's browser reads it; the server's days are Kuwait days, so the first view is a week wide. */
function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function Day({ date, entries }: { date: string; entries: CalendarEntry[] }) {
  const { locale, t } = useI18n();
  return (
    <section className="wp6-day" aria-label={formatDate(date, locale)}>
      <h3>{formatDate(date, locale)}</h3>
      {entries.length === 0 ? (
        <p className="ops-hint">{t('wp6.calendar.noEntries')}</p>
      ) : (
        <ul className="wp6-entries">
          {entries.map((entry) => (
            <EntryRow key={entry.id} entry={entry} />
          ))}
        </ul>
      )}
    </section>
  );
}

function datesBetween(from: string, to: string): string[] {
  const days: string[] = [];
  for (let day = new Date(`${from}T00:00:00Z`); day <= new Date(`${to}T00:00:00Z`); day.setUTCDate(day.getUTCDate() + 1)) {
    days.push(day.toISOString().slice(0, 10));
  }
  return days;
}

/** The month as a grid of weeks; each day shows how many entries it holds and opens as a day. */
function MonthGrid({ view, onOpen }: { view: CalendarView; onOpen: (date: string) => void }) {
  const { locale, t } = useI18n();
  const first = new Date(`${view.from}T00:00:00Z`);
  const lead = first.getUTCDay();
  const counts = new Map(view.days.map((d) => [d.date, d.count]));
  const conflicts = new Set(view.events.filter((e) => e.conflict).map((e) => e.date));
  const days: (string | null)[] = [...Array<null>(lead).fill(null), ...datesBetween(view.from, view.to)];
  const weeks: (string | null)[][] = [];
  for (let i = 0; i < days.length; i += 7) {
    const week = days.slice(i, i + 7);
    while (week.length < 7) week.push(null);
    weeks.push(week);
  }
  const weekdays = datesBetween('2027-01-03', '2027-01-09');
  return (
    <div className="wp6-month" role="grid" aria-label={t('wp6.calendar.monthGrid')}>
      <div role="row" className="wp6-month-head">
        {weekdays.map((d) => (
          <span key={d} role="columnheader">
            {new Intl.DateTimeFormat(locale === 'ar' ? 'ar-KW' : 'en-GB', { weekday: 'short', timeZone: 'UTC' }).format(
              new Date(`${d}T00:00:00Z`),
            )}
          </span>
        ))}
      </div>
      <div className="wp6-month-body">
        {weeks.map((week, weekIndex) => (
          <div key={weekIndex} role="row" className="wp6-week">
            {week.map((date, offset) => {
              if (date === null) {
                return <span key={`blank-${String(weekIndex)}-${String(offset)}`} className="wp6-cell wp6-cell-blank" role="gridcell" />;
              }
              const count = counts.get(date) ?? 0;
              const label = t('wp6.calendar.dayCount', { date: formatDate(date, locale), count: formatNumber(count, locale) });
              return (
                <button
                  key={date}
                  type="button"
                  role="gridcell"
                  className="wp6-cell"
                  data-busy={count > 0}
                  data-conflict={conflicts.has(date)}
                  aria-label={label}
                  onClick={() => {
                    onOpen(date);
                  }}
                >
                  <span className="wp6-cell-day">{formatNumber(Number(date.slice(8)), locale)}</span>
                  {count === 0 ? null : <span className="wp6-cell-count">{formatNumber(count, locale)}</span>}
                </button>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}

function ConflictList({ conflicts }: { conflicts: Conflict[] }) {
  const { locale, t } = useI18n();
  if (conflicts.length === 0) return null;
  return (
    <section className="case-notice wp6-conflicts" aria-label={t('wp6.calendar.conflicts')}>
      <h3>{t('wp6.calendar.conflicts')}</h3>
      <ul>
        {conflicts.map((c) => (
          <li key={`${c.ownerId}-${c.startsAt}`}>
            {t('wp6.calendar.conflictLine', {
              owner: c.ownerName ?? '',
              date: formatDate(c.date, locale),
              count: formatNumber(c.entries.length, locale),
            })}
            <ul>
              {c.entries.map((e) => (
                <li key={e.id}>
                  {t(`wp6.calendar.type.${e.type}`)}
                  {e.alternateMemberName === null
                    ? ` · ${t('wp6.calendar.noAlternate')}`
                    : ` · ${t('wp6.calendar.alternate', { name: e.alternateMemberName })}`}
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * The judicial calendar (requirements section 15): month, week, day and list views of the hearings, expert sessions,
 * tasks, information-request dates and manual dates the user may see, for the user alone or for the whole department.
 * Everything shown comes from the server, which applies the case access rules; days are Kuwait days.
 */
export function CalendarPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('wp6.calendar.title'));
  const filterId = useId();
  const state = useContext(AuthContext)?.state;
  const canManage = state?.status === 'signedIn' && state.me.permissions.includes('CALENDAR_MANAGE');
  // A link from a notification opens a given day: /calendar?date=2027-03-10&mode=DAY.
  const [search] = useSearchParams();
  const linkedDate = search.get('date');
  const linkedMode = search.get('mode');
  const [filter, setFilter] = useState<CalendarFilter>({
    mode: MODES.find((m) => m === linkedMode) ?? 'MONTH',
    anchor: linkedDate !== null && /^\d{4}-\d{2}-\d{2}$/.test(linkedDate) ? linkedDate : todayIso(),
    user: 'me',
    type: undefined,
  });
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [adding, setAdding] = useState(false);
  const [saved, setSaved] = useState(false);
  const requestKey = `${JSON.stringify(filter)}#${String(reload)}`;

  useEffect(() => {
    const controller = new AbortController();
    loadCalendar(filter, controller.signal).then(
      (view) => {
        setResult({ key: requestKey, view });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, error: loadError(error) });
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
  const period = periodOf(filter.mode, filter.anchor);
  const periodLabel =
    period.from === period.to ? formatDate(period.from, locale) : `${formatDate(period.from, locale)} – ${formatDate(period.to, locale)}`;
  const byDate = (view: CalendarView, date: string) => view.events.filter((e) => e.date === date);

  return (
    <>
      <h1>{t('wp6.calendar.title')}</h1>
      <p className="case-field-hint">{t('wp6.calendar.zoneNote')}</p>

      <div className="ops-toolbar wp6-toolbar">
        <div className="ops-actions" role="group" aria-label={t('wp6.calendar.viewMode')}>
          {MODES.map((mode) => (
            <button
              key={mode}
              type="button"
              aria-pressed={filter.mode === mode}
              onClick={() => {
                setFilter((f) => ({ ...f, mode }));
              }}
            >
              {t(`wp6.calendar.mode.${mode}`)}
            </button>
          ))}
        </div>
        <div className="ops-actions" role="group" aria-label={t('wp6.calendar.move')}>
          <button type="button" onClick={() => { setFilter((f) => ({ ...f, anchor: shift(f.mode, f.anchor, -1) })); }}>
            {t('wp6.calendar.previous')}
          </button>
          <button type="button" onClick={() => { setFilter((f) => ({ ...f, anchor: todayIso() })); }}>
            {t('wp6.calendar.today')}
          </button>
          <button type="button" onClick={() => { setFilter((f) => ({ ...f, anchor: shift(f.mode, f.anchor, 1) })); }}>
            {t('wp6.calendar.next')}
          </button>
        </div>
        <div className="ops-actions" role="group" aria-label={t('wp6.calendar.scope')}>
          <button type="button" aria-pressed={filter.user === 'me'} onClick={() => { setFilter((f) => ({ ...f, user: 'me' })); }}>
            {t('wp6.calendar.mine')}
          </button>
          <button type="button" aria-pressed={filter.user === undefined} onClick={() => { setFilter((f) => ({ ...f, user: undefined })); }}>
            {t('wp6.calendar.general')}
          </button>
        </div>
        <fieldset className="ops-filters">
          <legend>{t('operations.filters')}</legend>
          <label htmlFor={`${filterId}-type`}>{t('wp6.calendar.typeFilter')}</label>
          <select
            id={`${filterId}-type`}
            value={filter.type ?? ''}
            onChange={(event) => {
              const value = event.target.value;
              setFilter((f) => ({ ...f, type: value === '' ? undefined : (value as EntryType) }));
            }}
          >
            <option value="">{t('operations.all')}</option>
            {ENTRY_TYPES.map((type) => (
              <option key={type} value={type}>{t(`wp6.calendar.type.${type}`)}</option>
            ))}
          </select>
        </fieldset>
        {canManage ? (
          <button type="button" onClick={() => { setSaved(false); setAdding(true); }}>
            {t('wp6.calendar.newDate')}
          </button>
        ) : null}
      </div>

      <h2 className="wp6-period" aria-live="polite">{periodLabel}</h2>
      {saved ? <p role="status" className="ops-saved">{t('wp6.calendar.dateSaved')}</p> : null}
      {adding ? (
        <ManualDateForm
          caseId={null}
          initialDate={filter.anchor}
          onSaved={() => {
            setAdding(false);
            setSaved(true);
            setReload((n) => n + 1);
          }}
          onCancel={() => { setAdding(false); }}
        />
      ) : null}

      {current === null ? <p>{t('operations.loading')}</p> : null}
      {current !== null && 'error' in current ? (
        <div role="alert" className="ops-alert">
          <p>{t(current.error)}</p>
          <button type="button" onClick={() => { setReload((n) => n + 1); }}>{t('operations.retry')}</button>
        </div>
      ) : null}
      {current !== null && 'view' in current ? (
        <>
          {current.view.truncated ? <p className="ops-alert" role="status">{t('wp6.calendar.truncated')}</p> : null}
          <ConflictList conflicts={current.view.conflicts} />
          {filter.mode === 'MONTH' ? (
            <MonthGrid
              view={current.view}
              onOpen={(date) => {
                setFilter((f) => ({ ...f, mode: 'DAY', anchor: date }));
              }}
            />
          ) : null}
          {filter.mode === 'WEEK' ? (
            <div className="wp6-week">
              {datesBetween(current.view.from, current.view.to).map((date) => (
                <Day key={date} date={date} entries={byDate(current.view, date)} />
              ))}
            </div>
          ) : null}
          {filter.mode === 'DAY' ? <Day date={filter.anchor} entries={current.view.events} /> : null}
          {filter.mode === 'LIST' ? (
            current.view.events.length === 0 ? (
              <p>{t('wp6.calendar.noEntries')}</p>
            ) : (
              <div className="wp6-list">
                {[...new Set(current.view.events.map((e) => e.date))].map((date) => (
                  <Day key={date} date={date} entries={byDate(current.view, date)} />
                ))}
              </div>
            )
          ) : null}
        </>
      ) : null}
    </>
  );
}
