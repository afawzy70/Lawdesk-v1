import { type SyntheticEvent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ApiError } from '../../api/http';
import { PERMISSIONS } from '../../api/permissions';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { useAuth } from '../auth/AuthContext';
import { AdminTabs } from './AdminTabs';
import { createRole, getRole, type RoleView, updateRole } from './api';
import { codeLabel, failureMessages, roleLabel } from './messages';

const ROLE_FIELDS: Record<string, MessageKey> = {
  code: 'admin.field.code',
  name: 'admin.field.name',
  permissions: 'admin.field.permissions',
};

/** Role failures: the shared mapping, with the messages that only roles have (LD-009.1). */
function roleFailures(error: unknown, t: (key: MessageKey) => string): string[] {
  if (error instanceof ApiError && error.status === 409) return [t('admin.error.roleConflict')];
  if (error instanceof ApiError && error.status === 422 && error.problem?.rule === 'ADMIN_ROLE_FIXED') {
    return [t('admin.error.adminRoleFixed')];
  }
  return failureMessages(error, t, ROLE_FIELDS);
}

type Load = { kind: 'loading' } | { kind: 'loaded'; role: RoleView } | { kind: 'missing' } | { kind: 'denied' } | { kind: 'error' };

/** LD-009.1: create a role (`/admin/roles/new`), or view and change one; changing needs ROLE_MANAGE. */
export function RoleDetailsPage() {
  const { id } = useParams();
  const creating = id === undefined;
  const { t, locale } = useI18n();
  const { state } = useAuth();
  const navigate = useNavigate();
  const canManage = state.status === 'signedIn' && state.me.permissions.includes('ROLE_MANAGE');
  const [load, setLoad] = useState<Load>({ kind: 'loading' });
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [chosen, setChosen] = useState<string[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const role = load.kind === 'loaded' ? load.role : null;
  usePageTitle(role === null ? t(creating ? 'admin.roles.new' : 'admin.roles.title') : roleLabel(role.code, t, role.name));

  const show = (loaded: RoleView) => {
    setLoad({ kind: 'loaded', role: loaded });
    setName(loaded.name);
    setChosen(loaded.permissions);
  };

  useEffect(() => {
    if (id === undefined) return undefined;
    let current = true;
    getRole(id).then(
      (loaded) => {
        if (current) show(loaded);
      },
      (error: unknown) => {
        if (!current) return;
        if (error instanceof ApiError && error.status === 404) setLoad({ kind: 'missing' });
        else if (error instanceof ApiError && error.status === 403) setLoad({ kind: 'denied' });
        else setLoad({ kind: 'error' });
      },
    );
    return () => {
      current = false;
    };
  }, [id]);

  if (load.kind === 'denied') return <AccessDeniedPage />;
  if (!creating && role === null) {
    return (
      <section>
        <AdminTabs />
        {load.kind === 'loading' && <p role="status">{t('admin.roles.loading')}</p>}
        {load.kind === 'missing' && <p role="alert">{t('admin.roles.notFound')}</p>}
        {load.kind === 'error' && <p role="alert">{t('admin.error.unexpected')}</p>}
      </section>
    );
  }

  const toggle = (permission: string) => {
    setChosen((current) =>
      current.includes(permission) ? current.filter((p) => p !== permission) : [...current, permission],
    );
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setBusy(true);
    setErrors([]);
    setNotice('');
    try {
      if (role === null) {
        const created = await createRole({ code: code.trim(), name: name.trim(), permissions: chosen });
        void navigate(withLang(`/admin/roles/${created.id}`, locale));
      } else {
        show(await updateRole(role, { name: name.trim(), permissions: chosen }));
        setNotice(t('admin.roles.saved'));
      }
    } catch (error) {
      setErrors(roleFailures(error, t));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <AdminTabs />
      <h1>{role === null ? t('admin.roles.new') : roleLabel(role.code, t, role.name)}</h1>
      {role !== null && <p>{t(role.system ? 'admin.roles.builtIn' : 'admin.roles.custom')} · {role.code}</p>}
      {errors.length > 0 && (
        <div role="alert" className="form-error">
          {errors.map((message) => (
            <p key={message}>{message}</p>
          ))}
        </div>
      )}
      {notice !== '' && <p role="status">{notice}</p>}
      <form className="admin-form" onSubmit={(event) => void submit(event)} noValidate>
        {creating && (
          <>
            <label htmlFor="role-code">{t('admin.field.code')}</label>
            <input
              id="role-code"
              autoComplete="off"
              value={code}
              onChange={(event) => {
                setCode(event.target.value);
              }}
            />
          </>
        )}
        <label htmlFor="role-name">{t('admin.field.name')}</label>
        <input
          id="role-name"
          autoComplete="off"
          disabled={!canManage}
          value={name}
          onChange={(event) => {
            setName(event.target.value);
          }}
        />
        <fieldset className="role-permissions">
          <legend>{t('admin.field.permissions')}</legend>
          {PERMISSIONS.map((permission) => (
            <label key={permission} className="checkbox">
              <input
                type="checkbox"
                disabled={!canManage}
                checked={chosen.includes(permission)}
                onChange={() => {
                  toggle(permission);
                }}
              />
              {codeLabel('admin.permission', permission, t)}
            </label>
          ))}
        </fieldset>
        {canManage && (
          <button type="submit" disabled={busy}>
            {t(creating ? 'admin.roles.create' : 'admin.roles.save')}
          </button>
        )}
      </form>
    </section>
  );
}
