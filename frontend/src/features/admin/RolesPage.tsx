import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/http';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { useAuth } from '../auth/AuthContext';
import { AdminTabs } from './AdminTabs';
import { listRoles, type RoleView } from './api';
import { codeLabel, roleLabel } from './messages';

/** LD-009.1: the roles and the permissions each grants. */
export function RolesPage() {
  const { t, locale } = useI18n();
  const { state } = useAuth();
  usePageTitle(t('admin.roles.title'));
  const canManage = state.status === 'signedIn' && state.me.permissions.includes('ROLE_MANAGE');
  const [roles, setRoles] = useState<RoleView[] | null>(null);
  const [failure, setFailure] = useState<'denied' | 'error' | null>(null);

  useEffect(() => {
    let current = true;
    listRoles().then(
      (loaded) => {
        if (current) setRoles(loaded);
      },
      (error: unknown) => {
        if (current) setFailure(error instanceof ApiError && error.status === 403 ? 'denied' : 'error');
      },
    );
    return () => {
      current = false;
    };
  }, []);

  if (failure === 'denied') return <AccessDeniedPage />;

  return (
    <section>
      <AdminTabs />
      <h1>{t('admin.roles.title')}</h1>
      {canManage && <Link to={withLang('/admin/roles/new', locale)}>{t('admin.roles.new')}</Link>}
      {failure === 'error' && <p role="alert">{t('admin.error.unexpected')}</p>}
      {roles !== null && (
        <div className="table-scroll">
          <table>
            <caption>{t('admin.roles.title')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('admin.field.name')}</th>
                <th scope="col">{t('admin.roles.kind')}</th>
                <th scope="col">{t('admin.field.permissions')}</th>
              </tr>
            </thead>
            <tbody>
              {roles.map((role) => (
                <tr key={role.id}>
                  <td>
                    <Link to={withLang(`/admin/roles/${role.id}`, locale)}>{roleLabel(role.code, t, role.name)}</Link>
                  </td>
                  <td>{t(role.system ? 'admin.roles.builtIn' : 'admin.roles.custom')}</td>
                  <td>{role.permissions.map((code) => codeLabel('admin.permission', code, t)).join(locale === 'ar' ? '، ' : ', ')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
