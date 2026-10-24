import { useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { codeLabel, roleLabel } from '../admin/messages';
import { useAuth } from './AuthContext';
import { ChangePasswordForm } from './ChangePasswordForm';

/** My profile (LD-007.6): who the signed-in user is, what they may do, and a voluntary password change (LD-008). */
export function ProfilePage() {
  const { t, locale } = useI18n();
  const { state } = useAuth();
  usePageTitle(t('profile.title'));
  const [changed, setChanged] = useState(false);
  if (state.status !== 'signedIn') return null;
  const { me } = state;
  const separator = locale === 'ar' ? '، ' : ', ';

  return (
    <section>
      <h1>{t('profile.title')}</h1>
      <section aria-labelledby="profile-account">
        <h2 id="profile-account">{t('profile.account')}</h2>
        <dl>
          <dt>{t('admin.field.displayName')}</dt>
          <dd>{me.displayName}</dd>
          <dt>{t('admin.field.username')}</dt>
          <dd dir="ltr">{me.username}</dd>
          <dt>{t('admin.field.email')}</dt>
          <dd dir="ltr">{me.email}</dd>
          <dt>{t('admin.field.roles')}</dt>
          <dd>{me.roles.map((code) => roleLabel(code, t)).join(separator)}</dd>
        </dl>
      </section>
      <h2 id="profile-permissions">{t('profile.permissions')}</h2>
      <ul aria-labelledby="profile-permissions">
        {me.permissions.map((permission) => (
          <li key={permission}>{codeLabel('admin.permission', permission, t)}</li>
        ))}
      </ul>
      <section aria-labelledby="profile-password">
        <h2 id="profile-password">{t('profile.changePassword')}</h2>
        {changed && <p role="status">{t('password.changed')}</p>}
        <ChangePasswordForm
          onChanged={() => {
            setChanged(true);
          }}
        />
      </section>
    </section>
  );
}
