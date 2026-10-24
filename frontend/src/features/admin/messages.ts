import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { en } from '../../i18n/messages/en';

/** Turns an API failure into readable messages; never shows server text (architecture/CONTRACTS.md §4). */
function fieldLabel(field: string, t: (key: MessageKey) => string, labels: Record<string, MessageKey>): string {
  const key = labels[field];
  return key === undefined ? field : t(key);
}

export function failureMessages(error: unknown, t: (key: MessageKey) => string, fieldLabels: Record<string, MessageKey>): string[] {
  if (!(error instanceof ApiError)) return [t('admin.error.unexpected')];
  if (error.status === 400 && error.problem?.errors?.length) {
    return error.problem.errors.map((e) =>
      e.code === 'PasswordPolicy'
        ? t('admin.error.passwordPolicy')
        : `${fieldLabel(e.field, t, fieldLabels)}: ${t('admin.error.checkValue')}`,
    );
  }
  if (error.status === 409) return [t('admin.error.conflict')];
  if (error.status === 422 && error.problem?.rule === 'LAST_ADMIN') return [t('admin.error.lastAdmin')];
  if (error.status === 422 && error.problem?.rule === 'SELF_CHANGE') return [t('admin.error.selfChange')];
  if (error.status === 403) return [t('admin.error.forbidden')];
  return [t('admin.error.unexpected')];
}

/** The translated name of a role; a role without a translation keeps its own name, or its code. */
export function roleLabel(code: string, t: (key: MessageKey) => string, name?: string): string {
  const key = ROLE_LABELS[code];
  return key === undefined ? (name ?? code) : t(key);
}

export function statusKey(user: { enabled: boolean; locked: boolean }): MessageKey {
  if (!user.enabled) return 'admin.status.disabled';
  if (user.locked) return 'admin.status.locked';
  return 'admin.status.active';
}

export const ROLE_LABELS: Record<string, MessageKey> = {
  ADMIN: 'admin.role.ADMIN',
  LEGAL_MANAGER: 'admin.role.LEGAL_MANAGER',
  LEGAL_STAFF: 'admin.role.LEGAL_STAFF',
};

export const FIELD_LABELS: Record<string, MessageKey> = {
  username: 'admin.field.username',
  email: 'admin.field.email',
  displayName: 'admin.field.displayName',
  locale: 'admin.field.locale',
  roles: 'admin.field.roles',
  temporaryPassword: 'admin.field.temporaryPassword',
};

/** The translated label of a code the server sends (permissions, audit actions, outcomes, reasons); unknown codes
 * stay as sent. */
export function codeLabel(
  prefix: 'admin.permission' | 'admin.audit.action' | 'admin.audit.outcome' | 'admin.audit.reason',
  code: string,
  t: (key: MessageKey) => string): string {
  const key = `${prefix}.${code}`;
  return key in en ? t(key as MessageKey) : code;
}
