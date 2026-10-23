import { describe, expect, it } from 'vitest';
import type { Permission } from '../api/permissions';
import { DASHBOARD, NAV_ITEMS, type NavItem, visibleNav } from './navigation';

/** architecture/CONTRACTS.md §6: ready areas need their permission; planned areas keep the "coming later" page. */
describe('visibleNav', () => {
  const paths = (granted: Permission[]) => visibleNav(new Set(granted)).map((item) => item.path);

  it('always shows the dashboard and every planned area', () => {
    const planned = NAV_ITEMS.filter((item) => item.status === 'planned').map((item) => item.path);

    expect(paths([])).toEqual(['/', ...planned]);
  });

  it('declares a permission for every area except the dashboard', () => {
    for (const item of NAV_ITEMS.filter((nav) => nav.path !== '/')) {
      expect(item.requires, item.path).toBeDefined();
    }
  });

  it('hides a ready area from users without its permission and shows it to users with it', () => {
    const ready: NavItem = { path: '/ready', label: 'nav.cases', requires: 'CASE_VIEW', status: 'ready' };
    const items: NavItem[] = [DASHBOARD, ready];

    expect(visibleNav(new Set(), items).map((item) => item.path)).toEqual(['/']);
    expect(visibleNav(new Set<Permission>(['CASE_VIEW']), items).map((item) => item.path)).toEqual(['/', '/ready']);
  });
});
