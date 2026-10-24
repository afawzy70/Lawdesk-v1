import { api } from '../../api/http';
import type { Permission } from '../../api/permissions';

/** `GET /api/v1/me` (LD-007.6): the signed-in user and what they may do. */
export interface Me {
  id: string;
  username: string;
  displayName: string;
  email: string;
  locale: 'ar' | 'en';
  roles: string[];
  permissions: Permission[];
  mustChangePassword: boolean;
}

export function fetchMe(signal?: AbortSignal): Promise<Me> {
  return api<Me>('/api/v1/me', signal === undefined ? {} : { signal });
}

/** Loads the XSRF-TOKEN cookie that every unsafe request echoes (architecture/CONTRACTS.md §3). */
export function loadCsrfToken(): Promise<void> {
  return api<undefined>('/api/v1/auth/csrf');
}

export function signIn(login: string, password: string): Promise<Me> {
  return api<Me>('/api/v1/auth/login', { method: 'POST', body: { login, password } });
}

export function signOut(): Promise<void> {
  return api<undefined>('/api/v1/auth/logout', { method: 'POST' });
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return api<undefined>('/api/v1/me/password', { method: 'POST', body: { currentPassword, newPassword } });
}
