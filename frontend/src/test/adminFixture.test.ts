import { describe, expect, it } from 'vitest';
import { PERMISSIONS } from '../api/permissions';
import { ADMIN } from './api';

/**
 * The seeded ADMIN role holds every permission (architecture/CONTRACTS.md §3, §8), so the test administrator does too:
 * a workstream flipping its navigation item to `ready` must not depend on a shared fixture edit.
 */
describe('ADMIN test fixture', () => {
  it('holds every permission, like the real administrator role', () => {
    expect([...ADMIN.permissions].sort()).toEqual([...PERMISSIONS].sort());
  });
});
