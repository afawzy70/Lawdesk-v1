import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/http';
import { translate } from '../../i18n/catalog';
import type { MessageKey } from '../../i18n/catalog';
import { FIELD_LABELS, failureMessages, roleLabel, statusKey } from './messages';

const t = (key: MessageKey) => translate('en', key);

describe('failureMessages', () => {
  it('names each invalid field, and explains the password policy', () => {
    const error = new ApiError(400, { errors: [{ field: 'email', code: 'Email' }, { field: 'unknownField', code: 'Size' }, { field: 'temporaryPassword', code: 'PasswordPolicy' }] });

    expect(failureMessages(error, t, FIELD_LABELS)).toEqual([
      'Email: check this value.',
      'unknownField: check this value.',
      'The password must have 12 to 128 characters and must not be the username or email.',
    ]);
  });

  it('explains conflicts, rules and refusals, and nothing else', () => {
    expect(failureMessages(new ApiError(409, null), t, FIELD_LABELS)).toEqual(['This username or email is already in use.']);
    expect(failureMessages(new ApiError(422, { rule: 'SELF_CHANGE' }), t, FIELD_LABELS)).toEqual(['You cannot change your own roles or account status.']);
    expect(failureMessages(new ApiError(422, { rule: 'LAST_ADMIN' }), t, FIELD_LABELS)).toEqual(['The last active administrator must stay an administrator.']);
    expect(failureMessages(new ApiError(403, null), t, FIELD_LABELS)).toEqual(['You do not have permission for this change.']);
    expect(failureMessages(new ApiError(422, { rule: 'OTHER' }), t, FIELD_LABELS)).toEqual(['Something went wrong. Try again later.']);
    expect(failureMessages(new ApiError(400, null), t, FIELD_LABELS)).toEqual(['Something went wrong. Try again later.']);
    expect(failureMessages(new Error('boom'), t, FIELD_LABELS)).toEqual(['Something went wrong. Try again later.']);
  });
});

describe('labels', () => {
  it('translates known roles and keeps the name or code of others', () => {
    expect(roleLabel('LEGAL_STAFF', t)).toBe('Legal staff');
    expect(roleLabel('AUDITOR', t, 'Auditor')).toBe('Auditor');
    expect(roleLabel('AUDITOR', t)).toBe('AUDITOR');
  });

  it('reports disabled before locked before active', () => {
    expect(statusKey({ enabled: false, locked: true })).toBe('admin.status.disabled');
    expect(statusKey({ enabled: true, locked: true })).toBe('admin.status.locked');
    expect(statusKey({ enabled: true, locked: false })).toBe('admin.status.active');
  });
});
