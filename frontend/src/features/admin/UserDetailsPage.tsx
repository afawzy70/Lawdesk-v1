import { type SyntheticEvent, useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { ApiError } from '../../api/http';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { getUser, listRoles, resetPassword, type RoleView, setEnabled, setRoles, updateUser, type UserView } from './api';
import { FIELD_LABELS, failureMessages, roleLabel, statusKey } from './messages';

/** LD-009 / LD-008.6: a user's details, status, roles and administrative password reset. */
export function UserDetailsPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  usePageTitle(t('admin.users.details'));
  const [user, setUser] = useState<UserView | null>(null);
  const [roles, setRolesList] = useState<RoleView[]>([]);
  const [chosen, setChosen] = useState<string[]>([]);
  const [details, setDetails] = useState({ email: '', displayName: '', locale: 'ar' as 'ar' | 'en' });
  const [temporary, setTemporary] = useState('');
  const [errors, setErrors] = useState<string[]>([]);
  const [notice, setNotice] = useState('');
  const [denied, setDenied] = useState(false);

  const show = (loaded: UserView) => {
    setUser(loaded);
    setChosen(loaded.roles);
    setDetails({ email: loaded.email, displayName: loaded.displayName, locale: loaded.locale });
  };

  useEffect(() => {
    getUser(id).then(show, (error: unknown) => {
      if (error instanceof ApiError && error.status === 403) setDenied(true);
      else setErrors([t('admin.error.unexpected')]);
    });
    listRoles().then(setRolesList, () => undefined);
  }, [id, t]);

  if (denied) return <AccessDeniedPage />;
  if (user === null) return <section>{errors.length > 0 && <p role="alert">{errors[0]}</p>}</section>;

  const act = async (work: () => Promise<UserView | undefined>, done: string) => {
    setErrors([]);
    setNotice('');
    try {
      const changed = await work();
      if (changed) show(changed);
      setNotice(done);
    } catch (error) {
      setErrors(failureMessages(error, t, FIELD_LABELS));
    }
  };

  const saveDetails = (event: SyntheticEvent) => {
    event.preventDefault();
    void act(() => updateUser(user, details), t('admin.users.detailsSaved'));
  };

  const reset = (event: SyntheticEvent) => {
    event.preventDefault();
    void act(async () => {
      await resetPassword(user.id, temporary);
      setTemporary('');
      return undefined;
    }, t('admin.users.passwordReset'));
  };

  return (
    <section>
      <h1>{user.displayName}</h1>
      <p>
        {user.username} · <span>{t(statusKey(user))}</span>
      </p>
      {errors.length > 0 && (
        <div role="alert" className="form-error">
          {errors.map((message) => (
            <p key={message}>{message}</p>
          ))}
        </div>
      )}
      {notice !== '' && <p role="status">{notice}</p>}

      <form className="admin-form" onSubmit={saveDetails} noValidate>
        <label htmlFor="details-email">{t('admin.field.email')}</label>
        <input
          id="details-email"
          type="email"
          value={details.email}
          onChange={(event) => {
            setDetails({ ...details, email: event.target.value });
          }}
        />
        <label htmlFor="details-displayName">{t('admin.field.displayName')}</label>
        <input
          id="details-displayName"
          value={details.displayName}
          onChange={(event) => {
            setDetails({ ...details, displayName: event.target.value });
          }}
        />
        <label htmlFor="details-locale">{t('admin.field.locale')}</label>
        <select
          id="details-locale"
          value={details.locale}
          onChange={(event) => {
            setDetails({ ...details, locale: event.target.value as 'ar' | 'en' });
          }}
        >
          <option value="ar">العربية</option>
          <option value="en">English</option>
        </select>
        <button type="submit">{t('admin.users.saveDetails')}</button>
      </form>

      <button type="button" onClick={() => void act(() => setEnabled(user.id, !user.enabled), t('admin.users.statusSaved'))}>
        {t(user.enabled ? 'admin.users.disable' : 'admin.users.enable')}
      </button>

      <fieldset>
        <legend>{t('admin.field.roles')}</legend>
        {roles.map((role) => (
          <label key={role.code}>
            <input
              type="checkbox"
              checked={chosen.includes(role.code)}
              onChange={(event) => {
                setChosen(event.target.checked ? [...chosen, role.code] : chosen.filter((c) => c !== role.code));
              }}
            />
            {roleLabel(role.code, t, role.name)}
          </label>
        ))}
        <button type="button" onClick={() => void act(() => setRoles(user.id, [...chosen].sort()), t('admin.users.rolesSaved'))}>
          {t('admin.users.saveRoles')}
        </button>
      </fieldset>

      <form className="admin-form" onSubmit={reset} noValidate>
        <label htmlFor="reset-password">{t('admin.users.newTemporary')}</label>
        <input
          id="reset-password"
          type="password"
          autoComplete="new-password"
          value={temporary}
          onChange={(event) => {
            setTemporary(event.target.value);
          }}
        />
        <button type="submit">{t('admin.users.resetPassword')}</button>
      </form>
    </section>
  );
}
