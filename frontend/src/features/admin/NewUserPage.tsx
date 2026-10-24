import { type SyntheticEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { createUser, listRoles, type RoleView } from './api';
import { FIELD_LABELS, failureMessages, roleLabel } from './messages';

/** LD-009: create a user with a temporary password the user must change at first sign-in (LD-008.5). */
export function NewUserPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('admin.users.new'));
  const navigate = useNavigate();
  const [roles, setRolesList] = useState<RoleView[]>([]);
  const [form, setForm] = useState({ username: '', email: '', displayName: '', locale: 'ar' as 'ar' | 'en', temporaryPassword: '' });
  const [chosen, setChosen] = useState<string[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    listRoles().then(setRolesList, () => {
      setErrors([t('admin.error.unexpected')]);
    });
  }, [t]);

  const field = (name: keyof typeof form) => ({
    id: `user-${name}`,
    value: form[name],
    onChange: (event: { target: { value: string } }) => {
      setForm({ ...form, [name]: event.target.value });
    },
  });

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setBusy(true);
    setErrors([]);
    try {
      const created = await createUser({ ...form, roles: [...chosen].sort() });
      void navigate(withLang(`/admin/users/${created.id}`, locale));
    } catch (error) {
      setErrors(failureMessages(error, t, FIELD_LABELS));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <h1>{t('admin.users.new')}</h1>
      {errors.length > 0 && (
        <div role="alert" className="form-error">
          {errors.map((message) => (
            <p key={message}>{message}</p>
          ))}
        </div>
      )}
      <form className="admin-form" onSubmit={(event) => void submit(event)} noValidate>
        <label htmlFor="user-username">{t('admin.field.username')}</label>
        <input {...field('username')} autoComplete="off" />
        <label htmlFor="user-email">{t('admin.field.email')}</label>
        <input {...field('email')} type="email" autoComplete="off" />
        <label htmlFor="user-displayName">{t('admin.field.displayName')}</label>
        <input {...field('displayName')} />
        <label htmlFor="user-locale">{t('admin.field.locale')}</label>
        <select {...field('locale')}>
          <option value="ar">العربية</option>
          <option value="en">English</option>
        </select>
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
        </fieldset>
        <label htmlFor="user-temporaryPassword">{t('admin.field.temporaryPassword')}</label>
        <input {...field('temporaryPassword')} type="password" autoComplete="new-password" />
        <button type="submit" disabled={busy}>
          {t('admin.users.create')}
        </button>
      </form>
    </section>
  );
}
