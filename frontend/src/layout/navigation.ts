import type { MessageKey } from '../i18n/catalog';

export interface NavItem {
  path: string;
  label: MessageKey;
}

/** Primary navigation. Only the dashboard is implemented in the preview; the rest show a "coming later" page. */
export const DASHBOARD: NavItem = { path: '/', label: 'nav.dashboard' };

export const PLANNED_AREAS: readonly NavItem[] = [
  { path: '/cases', label: 'nav.cases' },
  { path: '/hearings', label: 'nav.hearings' },
  { path: '/judgments', label: 'nav.judgments' },
  { path: '/experts', label: 'nav.experts' },
  { path: '/documents', label: 'nav.documents' },
  { path: '/reports', label: 'nav.reports' },
  { path: '/admin', label: 'nav.admin' },
];

export const NAV_ITEMS: readonly NavItem[] = [DASHBOARD, ...PLANNED_AREAS];
