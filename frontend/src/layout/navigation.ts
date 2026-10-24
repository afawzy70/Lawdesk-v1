import type { Permission } from '../api/permissions';
import type { MessageKey } from '../i18n/catalog';

/**
 * A primary navigation area (architecture/CONTRACTS.md §6). `ready` areas are real screens, shown only to users
 * holding `requires`; `planned` areas keep the "coming later" page. Hiding an item is a convenience, never the
 * access control: the backend checks every permission.
 */
export interface NavItem {
  path: string;
  label: MessageKey;
  requires?: Permission;
  status: 'ready' | 'planned';
}

export const DASHBOARD: NavItem = { path: '/', label: 'nav.dashboard', status: 'ready' };

export const PLANNED_AREAS: readonly NavItem[] = [
  { path: '/cases', label: 'nav.cases', requires: 'CASE_VIEW', status: 'ready' },
  { path: '/courts', label: 'nav.courts', requires: 'COURT_VIEW', status: 'ready' },
  { path: '/hearings', label: 'nav.hearings', requires: 'PROCEEDING_VIEW', status: 'planned' },
  { path: '/judgments', label: 'nav.judgments', requires: 'JUDGMENT_VIEW', status: 'planned' },
  { path: '/experts', label: 'nav.experts', requires: 'CASE_VIEW', status: 'planned' },
  { path: '/documents', label: 'nav.documents', requires: 'CASE_VIEW', status: 'planned' },
  { path: '/reports', label: 'nav.reports', requires: 'CASE_VIEW', status: 'planned' },
  { path: '/admin', label: 'nav.admin', requires: 'USER_VIEW', status: 'planned' },
];

export const NAV_ITEMS: readonly NavItem[] = [DASHBOARD, ...PLANNED_AREAS];

export function visibleNav(granted: ReadonlySet<Permission>, items: readonly NavItem[] = NAV_ITEMS): NavItem[] {
  return items.filter(
    (item) => item.status === 'planned' || item.requires === undefined || granted.has(item.requires),
  );
}
