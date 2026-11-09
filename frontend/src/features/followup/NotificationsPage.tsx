import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { casePath } from '../cases/paths';
import { listNotifications, markAllRead, markRead, type AppNotification } from './api';
import { failureKey } from './errors';

function textKey(type: string): MessageKey {
  const key = `wp3.notification.${type}`;
  return key in en ? (key as MessageKey) : 'wp3.notification.unknown';
}

/** Requirements section 32 (in-app only, decision D11): the signed-in user's notifications, newest first. */
export function NotificationsPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('wp3.notifications.title'));
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const [page, setPage] = useState({ number: 0, totalPages: 1 });
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listNotifications(0, controller.signal).then(
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
  }, [generation]);

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
      const result = await listNotifications(page.number + 1);
      setItems((current) => [...(current ?? []), ...result.items]);
      setPage({ number: page.number + 1, totalPages: result.totalPages });
    } catch (error) {
      setFailure(failureKey(error));
    }
  };

  return (
    <>
      <h1>{t('wp3.notifications.title')}</h1>
      <p className="case-field-hint">{t('wp3.notifications.inAppOnly')}</p>
      {failure === null ? null : <p className="case-alert" role="alert">{t(failure)}</p>}
      {items === null ? null : items.length === 0 ? (
        <p>{t('wp3.notifications.none')}</p>
      ) : (
        <>
          <p>
            <button type="button" onClick={() => void run(markAllRead)}>{t('wp3.notifications.readAll')}</button>
          </p>
          <ul className="wp3-list">
            {items.map((n) => (
              <li key={n.id} className="wp3-card" data-unread={n.readAt === null}>
                <p>
                  {t(textKey(n.type), n.params)}
                  {n.readAt === null ? <span className="wp3-badge" data-status="OVERDUE">{t('wp3.notifications.unread')}</span> : null}
                </p>
                <p className="case-field-hint">
                  {formatDateTime(n.createdAt, locale)}
                  {n.caseId === null || n.caseReferenceNo === null ? null : (
                    <>
                      {' · '}
                      <Link to={withLang(casePath(n.caseId), locale)}>{n.caseReferenceNo}</Link>
                    </>
                  )}
                </p>
                {n.readAt !== null ? null : (
                  <button type="button" onClick={() => void run(() => markRead(n.id))}>{t('wp3.notifications.markRead')}</button>
                )}
              </li>
            ))}
          </ul>
          {page.number + 1 >= page.totalPages ? null : (
            <button type="button" onClick={() => void more()}>{t('wp3.notifications.more')}</button>
          )}
        </>
      )}
    </>
  );
}
