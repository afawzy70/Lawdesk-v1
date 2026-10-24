import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { CaseInventoryPage } from './CaseInventoryPage';
import { JUDGMENTS_PATH, REPORTS_PATH } from './common';
import { JudgmentReportPage } from './JudgmentReportPage';

/**
 * The routes of the Phase 6 report screens (architecture/CONTRACTS.md §8), mounted inside the application layout by
 * App.tsx. Owned by the Phase 6 workstream, so it never edits App.tsx. Call it, do not render it.
 */
export function reportsRoutes(): ReactElement[] {
  return [
    <Route key="inventory" path={REPORTS_PATH} element={<CaseInventoryPage />} />,
    <Route key="judgments" path={JUDGMENTS_PATH} element={<JudgmentReportPage />} />,
  ];
}
