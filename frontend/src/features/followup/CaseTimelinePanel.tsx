import { useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime } from '../../i18n/format';
import { en } from '../../i18n/messages/en';
import { useI18n } from '../../i18n/I18nContext';
import { getTimeline, TIMELINE_KINDS, type TimelineEntry, type TimelineKind } from './api';
import { failureKey } from './errors';

interface Props {
  caseId: string;
  generation: number;
}

/** The state of the record an entry stands for, in the user's language; unknown states are not shown. */
function statusLabel(entry: TimelineEntry, t: (key: MessageKey) => string): string | null {
  if (entry.status === null) return null;
  const key = (
    {
      HEARING: `operations.hearingStatus.${entry.status}`,
      INFO_REQUEST: `wp3.requests.status.${entry.status}`,
      INFO_REQUEST_DUE: `wp3.requests.status.${entry.status}`,
      INFO_REPLY: `wp3.requests.completeness.${entry.status}`,
      RISK: `wp3.risk.level.${entry.status}`,
      LINK: `wp3.links.status.${entry.status}`,
      DEADLINE: `wp4.timelineStatus.${entry.status}`,
      JUDGMENT: `proceedings.outcome.${entry.status}`,
      EVENT: '',
    } satisfies Record<TimelineKind, string>
  )[entry.kind];
  return key in en ? t(key as MessageKey) : null;
}

/** The records an entry can lead to on the case page (each carries the anchor `wp3-ref-<type>-<id>`). */
const LINKED = new Set(['HEARING', 'INFO_REQUEST', 'RISK_ASSESSMENT', 'CASE_LINK', 'DEADLINE']);

/** Section 21.2/21.3: past and future events of the case in one list, filterable, each leading to its source record. */
export function CaseTimelinePanel({ caseId, generation }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [types, setTypes] = useState<TimelineKind[]>([]);
  const [entries, setEntries] = useState<{ key: string; items: TimelineEntry[]; page: number; totalPages: number } | null>(null);
  const [available, setAvailable] = useState(false);
  const [failure, setFailure] = useState<{ key: string; message: MessageKey } | null>(null);
  const key = `${caseId}#${String(generation)}#${types.join(',')}`;

  useEffect(() => {
    const controller = new AbortController();
    getTimeline(caseId, { types, page: 0 }, controller.signal).then(
      (page) => {
        if (controller.signal.aborted) return;
        setAvailable(page.deadlinesAvailable);
        setEntries({ key, items: page.items, page: 0, totalPages: page.totalPages });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setFailure({ key, message: failureKey(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, key, types]);

  const more = (current: NonNullable<typeof entries>) => {
    getTimeline(caseId, { types, page: current.page + 1 }).then(
      (page) => {
        setEntries({ ...current, items: [...current.items, ...page.items], page: current.page + 1 });
      },
      (error: unknown) => {
        setFailure({ key, message: failureKey(error) });
      },
    );
  };

  const toggle = (kind: TimelineKind) => {
    setTypes((current) => (current.includes(kind) ? current.filter((k) => k !== kind) : [...current, kind]));
  };

  const current = entries?.key === key ? entries : null;
  const upcoming = current?.items.filter((e) => e.isFuture) ?? [];
  const past = current?.items.filter((e) => !e.isFuture) ?? [];

  const row = (entry: TimelineEntry, index: number) => {
    const state = statusLabel(entry, t);
    return (
    <li key={`${entry.kind}-${entry.refId ?? 'x'}-${String(index)}`} data-future={entry.isFuture} data-action={entry.needsAction}>
      <time dateTime={entry.at}>{formatDateTime(entry.at, locale)}</time>{' '}
      <strong>{t(`wp3.timeline.kind.${entry.kind}`)}</strong>
      {state === null ? null : <> · {state}</>}
      {entry.summary === '' ? null : <> — {entry.summary}</>}
      {entry.needsAction ? <span className="wp3-badge" data-status="OVERDUE">{t('wp3.timeline.needsAction')}</span> : null}
      {entry.actorName === null ? null : <span className="case-field-hint"> ({entry.actorName})</span>}
      {entry.refId !== null && LINKED.has(entry.refType) ? (
        <>
          {' '}
          <a href={`#wp3-ref-${entry.refType}-${entry.refId}`}>{t('wp3.timeline.open')}</a>
        </>
      ) : null}
    </li>
    );
  };

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp3.timeline.title')}</h2>
      <fieldset className="case-fieldset wp3-filters">
        <legend>{t('wp3.timeline.filter')}</legend>
        {TIMELINE_KINDS.map((kind) => (
          <label key={kind}>
            <input type="checkbox" checked={types.includes(kind)} onChange={() => { toggle(kind); }} />{' '}
            {t(`wp3.timeline.kind.${kind}`)}
          </label>
        ))}
      </fieldset>
      {available ? null : <p className="case-field-hint">{t('wp3.timeline.deadlines')}</p>}
      {failure?.key === key ? <p className="case-alert" role="alert">{t(failure.message)}</p> : null}
      {current === null ? null : current.items.length === 0 ? (
        <p>{t('wp3.timeline.none')}</p>
      ) : (
        <>
          {upcoming.length === 0 ? null : (
            <>
              <h3>{t('wp3.timeline.upcoming')}</h3>
              <ul className="wp3-timeline">{upcoming.map(row)}</ul>
            </>
          )}
          {past.length === 0 ? null : (
            <>
              <h3>{t('wp3.timeline.past')}</h3>
              <ul className="wp3-timeline">{past.map(row)}</ul>
            </>
          )}
          {current.page + 1 >= current.totalPages ? null : (
            <button type="button" onClick={() => { more(current); }}>{t('wp3.timeline.more')}</button>
          )}
        </>
      )}
    </section>
  );
}
