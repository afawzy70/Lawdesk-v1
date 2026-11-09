import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { ClassificationsPage } from './ClassificationsPage';
import { JudgmentDetailPage } from './JudgmentDetailPage';
import { JudgmentFormPage } from './JudgmentFormPage';
import { LibraryPage } from './LibraryPage';
import { LibraryReportPage } from './LibraryReportPage';

export const LIBRARY_PATH = '/library';

/**
 * The routes of the judgments library (LD-052), mounted inside the application layout by App.tsx. Fixed paths come
 * before the `:id` one; react-router ranks static segments above dynamic ones. Call it, do not render it.
 */
export function libraryRoutes(): ReactElement[] {
  return [
    <Route key="library" path={LIBRARY_PATH} element={<LibraryPage />} />,
    <Route key="new" path={`${LIBRARY_PATH}/new`} element={<JudgmentFormPage />} />,
    <Route key="reports" path={`${LIBRARY_PATH}/reports`} element={<LibraryReportPage />} />,
    <Route key="lists" path={`${LIBRARY_PATH}/classifications`} element={<ClassificationsPage />} />,
    <Route key="detail" path={`${LIBRARY_PATH}/:id`} element={<JudgmentDetailPage />} />,
    <Route key="edit" path={`${LIBRARY_PATH}/:id/edit`} element={<JudgmentFormPage />} />,
  ];
}
