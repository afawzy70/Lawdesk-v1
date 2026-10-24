import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { HearingsPage } from './hearings/HearingsPage';

/** The paths of the Phase 5 screens. */
export const HEARINGS_PATH = '/hearings';

/**
 * The routes of the Phase 5 legal operations screens (architecture/CONTRACTS.md §8), mounted inside the application
 * layout by App.tsx. Owned by the Phase 5 workstream, so it never edits App.tsx. Call it, do not render it.
 *
 * App.tsx mounts a "coming later" page for every navigation area still `planned`, before these routes; a screen here
 * becomes reachable when its navigation item is flipped to `ready`.
 */
export function operationsRoutes(): ReactElement[] {
  return [<Route key={HEARINGS_PATH} path={HEARINGS_PATH} element={<HearingsPage />} />];
}
