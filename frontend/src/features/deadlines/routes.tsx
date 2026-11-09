import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { DeadlineSettingsPage } from './DeadlineSettingsPage';
import { DeadlinesPage } from './DeadlinesPage';

export const DEADLINES_PATH = '/deadlines';
export const DEADLINE_SETTINGS_PATH = '/admin/deadlines';

/** The routes of the legal deadlines (LD-054), mounted inside the application layout by App.tsx. Call it, do not render it. */
export function deadlineRoutes(): ReactElement[] {
  return [
    <Route key="deadlines" path={DEADLINES_PATH} element={<DeadlinesPage />} />,
    <Route key="deadline-settings" path={DEADLINE_SETTINGS_PATH} element={<DeadlineSettingsPage />} />,
  ];
}
