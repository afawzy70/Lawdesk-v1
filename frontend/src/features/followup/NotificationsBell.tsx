import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { getUnreadCount } from './api';

/** The notifications bell of the application bar: a link to the list with the number of unread ones. */
export function NotificationsBell() {
  const { t, locale } = useI18n();
  const { pathname } = useLocation();
  const [unread, setUnread] = useState<number | null>(null);

  // Read again whenever the user moves to another page, which is when something may have been raised or read.
  useEffect(() => {
    const controller = new AbortController();
    getUnreadCount(controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) setUnread(result);
      },
      () => {
        if (!controller.signal.aborted) setUnread(null);
      },
    );
    return () => {
      controller.abort();
    };
  }, [pathname]);

  const label =
    unread === null || unread === 0
      ? t('wp3.notifications.bell')
      : t('wp3.notifications.bellUnread', { count: formatNumber(unread, locale) });
  return (
    <Link className="wp3-bell" to={withLang('/notifications', locale)} aria-label={label} title={label}>
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">
        <path d="M12 3a6 6 0 0 0-6 6v4l-2 3h16l-2-3V9a6 6 0 0 0-6-6Zm-2 15a2 2 0 0 0 4 0" fill="none" stroke="currentColor" strokeWidth="1.6" />
      </svg>
      {unread === null || unread === 0 ? null : <span className="wp3-bell-count">{unread > 99 ? '99+' : formatNumber(unread, locale)}</span>}
    </Link>
  );
}
