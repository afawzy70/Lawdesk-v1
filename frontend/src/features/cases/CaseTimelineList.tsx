import { useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { getTimeline, type CaseEvent } from './api';
import { problemMessage } from './problems';

const KNOWN_EVENTS: Record<string, MessageKey> = {
  CASE_CREATED: 'cases.event.CASE_CREATED',
  CASE_UPDATED: 'cases.event.CASE_UPDATED',
  CASE_STATUS_CHANGED: 'cases.event.CASE_STATUS_CHANGED',
  PARTY_ADDED: 'cases.event.PARTY_ADDED',
  PARTY_REMOVED: 'cases.event.PARTY_REMOVED',
};

interface Loaded {
  key: string;
  events: CaseEvent[];
  page: number;
  totalPages: number;
}

interface Props {
  caseId: string;
  /** Changes whenever the case changed, so the newest events are fetched again. */
  generation: number;
}

/** LD-014: the case timeline, newest first, with older events fetched on request. */
export function CaseTimelineList({ caseId, generation }: Props) {
  const { locale, t } = useI18n();
  const headingId = useId();
  const key = `${caseId}#${String(generation)}`;
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [failure, setFailure] = useState<{ key: string; message: MessageKey } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getTimeline(caseId, 0, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          setLoaded({ key, events: page.items, page: 0, totalPages: page.totalPages });
        }
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setFailure({ key, message: problemMessage(error, 'cases.edit.loadFailed') });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, key]);

  const showOlder = (current: Loaded) => {
    getTimeline(caseId, current.page + 1).then(
      (page) => {
        setLoaded({ ...current, events: [...current.events, ...page.items], page: current.page + 1 });
      },
      (error: unknown) => {
        setFailure({ key, message: problemMessage(error, 'cases.edit.loadFailed') });
      },
    );
  };

  const current = loaded?.key === key ? loaded : null;
  return (
    <section aria-labelledby={headingId} className="case-section">
      <h2 id={headingId}>{t('cases.timeline.title')}</h2>
      {failure?.key === key ? (
        <p className="case-alert" role="alert">
          {t(failure.message)}
        </p>
      ) : null}
      {current !== null && current.events.length === 0 ? <p>{t('cases.timeline.empty')}</p> : null}
      {current !== null && current.events.length > 0 ? (
        <ol className="case-timeline" aria-labelledby={headingId}>
          {current.events.map((event) => (
            <li key={event.id}>
              <strong>{t(KNOWN_EVENTS[event.type] ?? 'cases.event.other')}</strong>{' '}
              <time dateTime={event.occurredAt}>{formatDateTime(event.occurredAt, locale)}</time>
              <p>{event.summary}</p>
            </li>
          ))}
        </ol>
      ) : null}
      {current !== null && current.page + 1 < current.totalPages ? (
        <button
          type="button"
          onClick={() => {
            showOlder(current);
          }}
        >
          {t('cases.timeline.older')}
        </button>
      ) : null}
    </section>
  );
}
