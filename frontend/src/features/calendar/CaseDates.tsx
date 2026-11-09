import { useContext, useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { AuthContext } from '../auth/AuthContext';
import { loadError } from '../operations/formErrors';
import { loadCaseEntries, type CalendarEntry } from './api';
import { EntryRow } from './EntryRow';
import { ManualDateForm } from './ManualDateForm';
import './calendar.css';

interface Props {
  caseId: string;
  archived: boolean;
}

type Result = { key: number; entries: CalendarEntry[] } | { key: number; error: MessageKey };

function addDays(days: number): string {
  const day = new Date();
  day.setUTCDate(day.getUTCDate() + days);
  return day.toISOString().slice(0, 10);
}

/**
 * The dates of one case (requirements section 15): what lies ahead on the calendar for the case — hearings, sessions, tasks,
 * information-request dates and manual dates — and a form for the dates the lawyer sets (memorandum, appeal, enforcement).
 * Shown to holders of CALENDAR_VIEW; the server applies the case access rules.
 */
export function CaseDates({ caseId, archived }: Props) {
  const { t } = useI18n();
  const headingId = useId();
  const state = useContext(AuthContext)?.state;
  const granted = new Set<string>(state?.status === 'signedIn' ? state.me.permissions : []);
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [adding, setAdding] = useState(false);
  const [saved, setSaved] = useState(false);
  const allowed = granted.has('CALENDAR_VIEW');

  useEffect(() => {
    if (!allowed) return undefined;
    const controller = new AbortController();
    // Today and the next hundred days, the longest period the server answers.
    loadCaseEntries(caseId, addDays(0), addDays(100), controller.signal).then(
      (view) => {
        setResult({ key: reload, entries: view.events });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: reload, error: loadError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [allowed, caseId, reload]);

  if (!allowed) return null;
  const current = result?.key === reload ? result : null;
  return (
    <section className="ops-section" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('wp6.calendar.caseTitle')}</h3>
      {saved ? <p role="status" className="ops-saved">{t('wp6.calendar.dateSaved')}</p> : null}
      {granted.has('CALENDAR_MANAGE') && !archived ? (
        <button type="button" onClick={() => { setSaved(false); setAdding(true); }}>{t('wp6.calendar.newDate')}</button>
      ) : null}
      {adding ? (
        <ManualDateForm
          caseId={caseId}
          initialDate={addDays(0)}
          onSaved={() => {
            setAdding(false);
            setSaved(true);
            setReload((n) => n + 1);
          }}
          onCancel={() => { setAdding(false); }}
        />
      ) : null}
      {current === null ? <p>{t('operations.loading')}</p> : null}
      {current !== null && 'error' in current ? <p className="ops-alert">{t(current.error)}</p> : null}
      {current !== null && 'entries' in current ? (
        current.entries.length === 0 ? (
          <p>{t('wp6.calendar.caseNone')}</p>
        ) : (
          <ul className="wp6-entries">
            {current.entries.map((entry) => (
              <EntryRow key={entry.id} entry={entry} />
            ))}
          </ul>
        )
      ) : null}
    </section>
  );
}
