import { api } from '../../api/http';

/** The user administration API (LD-009, docs/delivery/PHASE-2-ACCEPTANCE.md). */
export interface UserView {
  id: string;
  username: string;
  email: string;
  displayName: string;
  locale: 'ar' | 'en';
  enabled: boolean;
  mustChangePassword: boolean;
  locked: boolean;
  roles: string[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface RoleView {
  id: string;
  code: string;
  name: string;
  system: boolean;
  permissions: string[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export const PAGE_SIZE = 20;

export interface NewUser {
  username: string;
  email: string;
  displayName: string;
  locale: 'ar' | 'en';
  roles: string[];
  temporaryPassword: string;
}

export function listUsers(query: string, page: number): Promise<Page<UserView>> {
  const params = new URLSearchParams();
  if (query !== '') params.set('q', query);
  params.set('page', String(page));
  params.set('size', String(PAGE_SIZE));
  return api(`/api/v1/users?${params.toString()}`);
}

export const getUser = (id: string): Promise<UserView> => api(`/api/v1/users/${id}`);
export const listRoles = (): Promise<RoleView[]> => api('/api/v1/roles');
export const createUser = (user: NewUser): Promise<UserView> => api('/api/v1/users', { method: 'POST', body: user });

export const updateUser = (user: UserView, changes: Pick<UserView, 'email' | 'displayName' | 'locale'>): Promise<UserView> =>
  api(`/api/v1/users/${user.id}`, { method: 'PUT', body: { ...changes, version: user.version } });

export const setEnabled = (id: string, enabled: boolean): Promise<UserView> =>
  api(`/api/v1/users/${id}/enabled`, { method: 'PUT', body: { enabled } });

export const setRoles = (id: string, roles: string[]): Promise<UserView> =>
  api(`/api/v1/users/${id}/roles`, { method: 'PUT', body: { roles } });

export const resetPassword = (id: string, temporaryPassword: string): Promise<void> =>
  api(`/api/v1/users/${id}/password-reset`, { method: 'POST', body: { temporaryPassword } });
