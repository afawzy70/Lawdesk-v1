import type { ReactElement } from 'react';
import { Navigate, Route } from 'react-router';
import { DeadlineSettingsPage } from './DeadlineSettingsPage';

export const DEADLINES_PATH = '/deadlines';
export const DEADLINE_SETTINGS_PATH = '/admin/deadlines';

/** The routes of the legal deadlines (LD-054), mounted inside the application layout by App.tsx. Call it, do not render it. */
export function deadlineRoutes(): ReactElement[] {
  return [
    // The register was removed: deadlines are read in the calendar and in their case, the settings are in the administration.
    <Route key="deadlines" path={DEADLINES_PATH} element={<Navigate to="/calendar" replace />} />,
    <Route key="deadline-settings" path={DEADLINE_SETTINGS_PATH} element={<DeadlineSettingsPage />} />,
  ];
}
