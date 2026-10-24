import { type SyntheticEvent, useState } from 'react';
import { ApiError } from '../../api/http';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import { useAuth } from './AuthContext';
import { changePassword } from './me';

/** LD-008.5: the first thing a user with a temporary password sees, until the password is changed. */
export const MIN_PASSWORD_LENGTH = 12;

const SERVER_MESSAGES: Record<string, MessageKey> = {
  CurrentPassword: 'password.currentWrong',
  PasswordPolicy: 'password.policy',
};

export function ChangePasswordPage() {
  const { t } = useI18n();
  const { refresh, signOut } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [errors, setErrors] = useState<MessageKey[]>([]);
  const [busy, setBusy] = useState(false);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    const local: MessageKey[] = [];
    if (next.length < MIN_PASSWORD_LENGTH) local.push('password.tooShort');
    if (next !== repeat) local.push('password.mismatch');
    setErrors(local);
    if (local.length > 0) return;
    setBusy(true);
    try {
      await changePassword(current, next);
      await refresh();
    } catch (error) {
      const codes = error instanceof ApiError ? (error.problem?.errors ?? []).map((e) => e.code) : [];
      const known = codes.map((code) => SERVER_MESSAGES[code]).filter((key): key is MessageKey => key !== undefined);
      setErrors(known.length > 0 ? known : ['password.failed']);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-page" id="main-content">
      <div className="auth-card">
        <h1>{t('password.title')}</h1>
        <p>{t('password.intro')}</p>
        {errors.length > 0 && (
          <div role="alert" className="form-error">
            {errors.map((key) => (
              <p key={key}>{t(key)}</p>
            ))}
          </div>
        )}
        <form onSubmit={(event) => void submit(event)} noValidate>
          <label htmlFor="current-password">{t('password.current')}</label>
          <input
            id="current-password"
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(event) => {
              setCurrent(event.target.value);
            }}
          />
          <label htmlFor="new-password">{t('password.new')}</label>
          <input
            id="new-password"
            type="password"
            autoComplete="new-password"
            value={next}
            onChange={(event) => {
              setNext(event.target.value);
            }}
          />
          <label htmlFor="repeat-password">{t('password.repeat')}</label>
          <input
            id="repeat-password"
            type="password"
            autoComplete="new-password"
            value={repeat}
            onChange={(event) => {
              setRepeat(event.target.value);
            }}
          />
          <button type="submit" disabled={busy}>
            {t('password.submit')}
          </button>
          <button type="button" className="link-button" onClick={() => void signOut()}>
            {t('auth.signOut')}
          </button>
        </form>
      </div>
    </main>
  );
}
