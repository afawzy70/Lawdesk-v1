import { NavLink } from 'react-router';
import type { Permission } from '../../api/permissions';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { useAuth } from '../auth/AuthContext';

const TABS: readonly { path: string; label: MessageKey; requires: Permission }[] = [
  { path: '/admin', label: 'admin.tabs.users', requires: 'USER_VIEW' },
  { path: '/admin/roles', label: 'admin.tabs.roles', requires: 'ROLE_VIEW' },
  { path: '/admin/audit', label: 'admin.tabs.audit', requires: 'AUDIT_VIEW' },
];

/** The administration areas the signed-in user may open; the backend still checks every request. */
export function AdminTabs() {
  const { t, locale } = useI18n();
  const { state } = useAuth();
  const granted = new Set(state.status === 'signedIn' ? state.me.permissions : []);
  return (
    <nav aria-label={t('admin.tabs')} className="admin-tabs">
      <ul>
        {TABS.filter((tab) => granted.has(tab.requires)).map((tab) => (
          <li key={tab.path}>
            <NavLink to={withLang(tab.path, locale)} end>
              {t(tab.label)}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
