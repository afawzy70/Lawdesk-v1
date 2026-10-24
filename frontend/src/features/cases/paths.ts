/** Routes of the case screens; App.tsx mounts them. */
export const CASES_PATH = '/cases';
export const NEW_CASE_PATH = '/cases/new';
export const EDIT_CASE_ROUTE = '/cases/:id/edit';
export const CASE_ROUTE = '/cases/:id';

export function casePath(id: string): string {
  return `/cases/${encodeURIComponent(id)}`;
}

export function editCasePath(id: string): string {
  return `/cases/${encodeURIComponent(id)}/edit`;
}
