import { useI18n } from '../../i18n/I18nContext';
import { useAuth } from './AuthContext';
import { ChangePasswordForm } from './ChangePasswordForm';

/** LD-008.5: the first thing a user with a temporary password sees, until the password is changed. */
export function ChangePasswordPage() {
  const { t } = useI18n();
  const { refresh, signOut } = useAuth();

  return (
    <main className="auth-page" id="main-content">
      <div className="auth-card">
        <h1>{t('password.title')}</h1>
        <p>{t('password.intro')}</p>
        <ChangePasswordForm onChanged={refresh}>
          <button type="button" className="link-button" onClick={() => void signOut()}>
            {t('auth.signOut')}
          </button>
        </ChangePasswordForm>
      </div>
    </main>
  );
}
