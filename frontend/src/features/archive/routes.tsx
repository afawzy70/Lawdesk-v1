import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { ArchiveSearchPage } from './ArchiveSearchPage';
import { EnforcementListPage } from './EnforcementListPage';

export const ARCHIVE_PATH = '/archive';
export const ENFORCEMENT_PATH = '/enforcement';

/** The routes of the archive search and the unexecuted-judgments list (LD-055), mounted inside the layout by App.tsx. Call it, do not render it. */
export function archiveRoutes(): ReactElement[] {
  return [
    <Route key={ARCHIVE_PATH} path={ARCHIVE_PATH} element={<ArchiveSearchPage />} />,
    <Route key={ENFORCEMENT_PATH} path={ENFORCEMENT_PATH} element={<EnforcementListPage />} />,
  ];
}
