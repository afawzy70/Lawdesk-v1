import { useCallback, useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { casePath } from '../cases/paths';
import {
  getPreferences,
  listNotifications,
  markAllRead,
  markRead,
  markUnread,
  setPreference,
  type AppNotification,
  type NotificationFilter,
  type NotificationPreference,
} from './api';
import { failureKey } from './errors';

/** The kinds in the order of the requirements (section 32), then the system notices. */
const NOTIFICATION_TYPES = [
  'CASE_NEW',
  'HEARING_UPCOMING',
  'EXPERT_SESSION_UPCOMING',
  'APPEAL_DEADLINE',
  'TASK_OVERDUE',
  'HEARING_NO_RESULT',
  'INFO_REQUEST_OVERDUE',
  'JUDGMENT_NEW',
  'DOCUMENT_MISSING',
  'DATE_CONFLICT',
  'RISK_HIGH',
  'TASK_ASSIGNED',
  'TASK_DUE_SOON',
  'INFO_REQUEST_ESCALATED',
  'HEARING_NO_NEXT_DATE',
] as const;

function textKey(type: string, params: Record<string, string> = {}): MessageKey {
  if (type === 'DOCUMENT_MISSING' && 'evidenceName' in params) return 'wp6.notification.DOCUMENT_MISSING_EVIDENCE';
  for (const prefix of ['wp6.notification.', 'wp3.notification.']) {
    const key = `${prefix}${type}`;
    if (key in en) return key as MessageKey;
  }
  return 'wp3.notification.unknown';
}

function typeLabel(type: string): MessageKey {
  const key = `wp6.notification.type.${type}`;
  return key in en ? (key as MessageKey) : 'wp3.notification.unknown';
}

/** Where the record of a notification is opened: a conflict opens its day, a task of no case the task list, else the case. */
function recordTarget(n: AppNotification): string | null {
  if (n.recordType === 'CALENDAR' && n.recordId !== null) return `/calendar?date=${n.recordId}&mode=DAY`;
  if (n.caseId !== null) return casePath(n.caseId);
  if (n.recordType === 'TASK') return '/tasks';
  return null;
}

/**
 * Requirements section 32 (in-app only, decision D11): the signed-in user's notification centre. Newest first, filtered by
 * kind and by read state, each linked to its record, read or unread for this user alone, and with the user's own choice of
 * the kinds they receive.
 */
export function NotificationsPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('wp3.notifications.title'));
  const filterId = useId();
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const [page, setPage] = useState({ number: 0, totalPages: 1 });
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [generation, setGeneration] = useState(0);
  const [filter, setFilter] = useState<NotificationFilter>({});
  const [preferences, setPreferences] = useState<NotificationPreference[] | 'closed' | 'loading'>('closed');
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listNotifications(0, controller.signal, filter).then(
      (result) => {
        if (controller.signal.aborted) return;
        setItems(result.items);
        setPage({ number: 0, totalPages: result.totalPages });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setFailure(failureKey(error));
      },
    );
    return () => {
      controller.abort();
    };
  }, [generation, filter]);

  const run = async (action: () => Promise<void>) => {
    try {
      await action();
      reload();
    } catch (error) {
      setFailure(failureKey(error));
    }
  };

  const more = async () => {
    try {
      const result = await listNotifications(page.number + 1, undefined, filter);
      setItems((current) => [...(current ?? []), ...result.items]);
      setPage({ number: page.number + 1, totalPages: result.totalPages });
    } catch (error) {
      setFailure(failureKey(error));
    }
  };

  const openPreferences = async () => {
    if (preferences !== 'closed') {
      setPreferences('closed');
      return;
    }
    setPreferences('loading');
    try {
      const loaded = await getPreferences();
      setPreferences(Array.isArray(loaded) ? loaded : 'closed');
    } catch (error) {
      setPreferences('closed');
      setFailure(failureKey(error));
    }
  };

  const toggle = async (preference: NotificationPreference) => {
    try {
      const saved = await setPreference(preference.type, !preference.enabled);
      setPreferences((current) =>
        Array.isArray(current) ? current.map((p) => (p.type === saved.type ? saved : p)) : current,
      );
    } catch (error) {
      setFailure(failureKey(error));
    }
  };

  return (
    <>
      <h1>{t('wp3.notifications.title')}</h1>
      <p className="case-field-hint">{t('wp3.notifications.inAppOnly')}</p>
      {failure === null ? null : <p className="case-alert" role="alert">{t(failure)}</p>}

      <fieldset className="ops-filters">
        <legend>{t('operations.filters')}</legend>
        <label htmlFor={`${filterId}-type`}>{t('wp6.notifications.kind')}</label>
        <select
          id={`${filterId}-type`}
          value={filter.type ?? ''}
          onChange={(event) => {
            const value = event.target.value;
            setFilter((f) => ({ ...f, type: value === '' ? undefined : value }));
          }}
        >
          <option value="">{t('operations.all')}</option>
          {NOTIFICATION_TYPES.map((type) => (
            <option key={type} value={type}>{t(typeLabel(type))}</option>
          ))}
        </select>
        <label htmlFor={`${filterId}-read`}>{t('wp6.notifications.state')}</label>
        <select
          id={`${filterId}-read`}
          value={filter.read ?? ''}
          onChange={(event) => {
            const value = event.target.value;
            setFilter((f) => ({ ...f, read: value === 'unread' || value === 'read' ? value : undefined }));
          }}
        >
          <option value="">{t('operations.all')}</option>
          <option value="unread">{t('wp6.notifications.unreadOnly')}</option>
          <option value="read">{t('wp6.notifications.readOnly')}</option>
        </select>
      </fieldset>
      <p>
        <button type="button" aria-expanded={preferences !== 'closed'} onClick={() => void openPreferences()}>
          {t('wp6.notifications.settings')}
        </button>
      </p>
      {Array.isArray(preferences) ? (
        <fieldset className="ops-filters wp6-preferences">
          <legend>{t('wp6.notifications.settingsTitle')}</legend>
          <p className="case-field-hint">{t('wp6.notifications.settingsHint')}</p>
          {preferences.map((preference) => (
            <label key={preference.type} className="wp6-preference">
              <input type="checkbox" checked={preference.enabled} onChange={() => void toggle(preference)} />
              {t(typeLabel(preference.type))}
            </label>
          ))}
        </fieldset>
      ) : null}

      {items === null ? null : items.length === 0 ? (
        <p>{t('wp3.notifications.none')}</p>
      ) : (
        <>
          <p>
            <button type="button" onClick={() => void run(markAllRead)}>{t('wp3.notifications.readAll')}</button>
          </p>
          <ul className="wp3-list">
            {items.map((n) => {
              const target = recordTarget(n);
              return (
                <li key={n.id} className="wp3-card" data-unread={n.readAt === null}>
                  <p>
                    {t(textKey(n.type, n.params), { time: '', caseReferenceNo: '', title: '', ...n.params })}
                    {n.readAt === null ? <span className="wp3-badge" data-status="OVERDUE">{t('wp3.notifications.unread')}</span> : null}
                  </p>
                  <p className="case-field-hint">
                    {t(typeLabel(n.type))}
                    {' · '}
                    {formatDateTime(n.createdAt, locale)}
                    {n.caseId === null || n.caseReferenceNo === null ? null : (
                      <>
                        {' · '}
                        <Link to={withLang(casePath(n.caseId), locale)}>{n.caseReferenceNo}</Link>
                      </>
                    )}
                    {target === null || (n.caseId !== null && n.recordType !== 'CALENDAR') ? null : (
                      <>
                        {' · '}
                        <Link to={target.includes('?') ? `${target}&lang=${locale}` : withLang(target, locale)}>{t('wp6.notifications.open')}</Link>
                      </>
                    )}
                  </p>
                  {n.readAt !== null ? (
                    <button type="button" onClick={() => void run(() => markUnread(n.id))}>{t('wp6.notifications.markUnread')}</button>
                  ) : (
                    <button type="button" onClick={() => void run(() => markRead(n.id))}>{t('wp3.notifications.markRead')}</button>
                  )}
                </li>
              );
            })}
          </ul>
          {page.number + 1 >= page.totalPages ? null : (
            <button type="button" onClick={() => void more()}>{t('wp3.notifications.more')}</button>
          )}
        </>
      )}
    </>
  );
}
