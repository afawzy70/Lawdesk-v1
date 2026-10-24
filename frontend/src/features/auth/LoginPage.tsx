import { type SyntheticEvent, useState } from 'react';
import { LocaleToggle } from '../../components/LocaleToggle';
import { useI18n } from '../../i18n/I18nContext';
import { useAuth } from './AuthContext';

/** LD-007: sign in with username or email. A refusal never says which part was wrong. */
export function LoginPage({ expired }: { expired: boolean }) {
  const { t } = useI18n();
  const { signIn } = useAuth();
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<'refused' | 'unavailable' | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (!(await signIn(login, password))) {
        setError('refused');
        setPassword('');
      }
    } catch {
      setError('unavailable');
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-page" id="main-content">
      <div className="auth-card">
        <LocaleToggle />
        <h1>{t('auth.title')}</h1>
        {expired && <p role="status">{t('auth.sessionExpired')}</p>}
        {error !== null && (
          <p role="alert" className="form-error">
            {t(error === 'refused' ? 'auth.refused' : 'auth.unavailable')}
          </p>
        )}
        <form onSubmit={(event) => void submit(event)} noValidate>
          <label htmlFor="login">{t('auth.login')}</label>
          <input
            id="login"
            name="login"
            autoComplete="username"
            required
            value={login}
            onChange={(event) => {
              setLogin(event.target.value);
            }}
          />
          <label htmlFor="password">{t('auth.password')}</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
            }}
          />
          <button type="submit" disabled={busy}>
            {t('auth.signIn')}
          </button>
        </form>
      </div>
    </main>
  );
}
