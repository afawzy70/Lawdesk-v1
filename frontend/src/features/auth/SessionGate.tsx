import type { ReactNode } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { useAuth } from './AuthContext';
import { ChangePasswordPage } from './ChangePasswordPage';
import { LoginPage } from './LoginPage';

/** Shows the application only with a session, and only after a required password change (LD-007, LD-008.5). */
export function SessionGate({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const { state } = useAuth();
  if (state.status === 'loading') {
    return (
      <p className="auth-loading" role="status">
        {t('auth.loading')}
      </p>
    );
  }
  if (state.status === 'signedOut') {
    return <LoginPage expired={state.expired} />;
  }
  if (state.me.mustChangePassword) {
    return <ChangePasswordPage />;
  }
  return children;
}
