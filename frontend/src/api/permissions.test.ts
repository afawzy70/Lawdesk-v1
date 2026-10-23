import { describe, expect, it } from 'vitest';
import { PERMISSIONS } from './permissions';

/** The list itself is compared with the backend enum by tools/tests/test_contracts.py. */
describe('PERMISSIONS', () => {
  it('lists each code once, in the backend naming style', () => {
    expect(new Set(PERMISSIONS).size).toBe(PERMISSIONS.length);
    for (const code of PERMISSIONS) {
      expect(code).toMatch(/^[A-Z]+(_[A-Z]+)*$/);
    }
  });
});
