import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { ArchiveSettingsPage } from './ArchiveSettingsPage';
import { DefenseWordingPage } from './DefenseWordingPage';
import { JudgmentsPage } from './JudgmentsPage';
import { ResponsivenessPage } from './ResponsivenessPage';

export const JUDGMENTS_PATH = '/judgments';
export const ARCHIVE_SETTINGS_PATH = '/admin/archive';
export const WORDING_PATH = '/defense-wording';
export const RESPONSIVENESS_PATH = '/info-requests/report';

/** The routes that close the gaps of the requirements matrix. Call it, do not render it. */
export function gapRoutes(): ReactElement[] {
  return [
    <Route key="judgments" path={JUDGMENTS_PATH} element={<JudgmentsPage />} />,
    <Route key="archive-settings" path={ARCHIVE_SETTINGS_PATH} element={<ArchiveSettingsPage />} />,
    <Route key="wording" path={WORDING_PATH} element={<DefenseWordingPage />} />,
    <Route key="responsiveness" path={RESPONSIVENESS_PATH} element={<ResponsivenessPage />} />,
  ];
}
