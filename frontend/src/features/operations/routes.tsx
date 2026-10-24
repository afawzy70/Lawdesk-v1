import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { DocumentsPage } from './documents/DocumentsPage';
import { ExpertsPage } from './experts/ExpertsPage';
import { HearingsPage } from './hearings/HearingsPage';
import { TasksPage } from './tasks/TasksPage';

/** The paths of the Phase 5 screens. */
export const HEARINGS_PATH = '/hearings';
export const TASKS_PATH = '/tasks';
export const DOCUMENTS_PATH = '/documents';
export const EXPERTS_PATH = '/experts';

/**
 * The routes of the Phase 5 legal operations screens (architecture/CONTRACTS.md §8), mounted inside the application
 * layout by App.tsx. Owned by the Phase 5 workstream, so it never edits App.tsx. Call it, do not render it.
 *
 * App.tsx mounts a "coming later" page for every navigation area still `planned`, before these routes; a screen here
 * becomes reachable when its navigation item is flipped to `ready`.
 */
export function operationsRoutes(): ReactElement[] {
  return [
    <Route key={HEARINGS_PATH} path={HEARINGS_PATH} element={<HearingsPage />} />,
    <Route key={TASKS_PATH} path={TASKS_PATH} element={<TasksPage />} />,
    <Route key={DOCUMENTS_PATH} path={DOCUMENTS_PATH} element={<DocumentsPage />} />,
    <Route key={EXPERTS_PATH} path={EXPERTS_PATH} element={<ExpertsPage />} />,
  ];
}
