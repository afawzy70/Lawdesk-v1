/** Routes of the case screens; App.tsx mounts them. */
export const CASES_PATH = '/cases';
export const NEW_CASE_PATH = '/cases/new';
export const EDIT_CASE_ROUTE = '/cases/:id/edit';

export function editCasePath(id: string): string {
  return `/cases/${encodeURIComponent(id)}/edit`;
}
