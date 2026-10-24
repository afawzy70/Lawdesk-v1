import { describe, expect, it } from 'vitest';
import { ar } from '../i18n/messages/ar';
import { en } from '../i18n/messages/en';
import { PERMISSIONS } from './permissions';

/** Role administration lists every permission by name (LD-009.1), including those added for Phases 5 and 6. */
describe('permission labels', () => {
  it('names every permission in English and Arabic', () => {
    const labels = (catalog: Record<string, string>) =>
      PERMISSIONS.filter((code) => !catalog[`admin.permission.${code}`]);

    expect(labels(en)).toEqual([]);
    expect(labels(ar)).toEqual([]);
  });
});
