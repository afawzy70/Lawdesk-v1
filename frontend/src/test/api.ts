import { vi } from 'vitest';
import { PERMISSIONS } from '../api/permissions';
import type { Me } from '../features/auth/me';
import { jsonResponse } from './http';

export const ADMIN: Me = {
  id: '00000000-0000-4000-8000-000000000001',
  username: 'maha',
  displayName: 'Maha Hassan',
  email: 'maha@example.invalid',
  locale: 'en',
  roles: ['ADMIN'],
  // Like the seeded ADMIN role: every permission (architecture/CONTRACTS.md §3, §8).
  permissions: [...PERMISSIONS],
  mustChangePassword: false,
};

export interface FakeApiOptions {
  /** The signed-in user, or null for no session. */
  me?: Me | null;
  /** Answers for further paths ("METHOD /path"), checked before the defaults. */
  routes?: Record<string, () => Response>;
}

/**
 * A fetch double for the LawDesk API (architecture/CONTRACTS.md §3): health is UP, the CSRF endpoint answers 204, and
 * /api/v1/me returns `me` or 401. Unknown paths answer 404.
 */
export function fakeApi({ me = ADMIN, routes = {} }: FakeApiOptions = {}) {
  return vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url;
    const method = init?.method ?? 'GET';
    const route = routes[`${method} ${path}`];
    if (route) return Promise.resolve(route());
    if (path === '/actuator/health') return Promise.resolve(jsonResponse({ status: 'UP' }));
    if (path === '/api/v1/auth/csrf') return Promise.resolve(new Response(null, { status: 204 }));
    if (path === '/api/v1/me' && method === 'GET') {
      return Promise.resolve(me ? jsonResponse(me) : unauthorized());
    }
    return Promise.resolve(jsonResponse({ status: 404, title: 'Not Found' }, 404));
  });
}

export function unauthorized(): Response {
  return new Response(JSON.stringify({ type: 'about:blank', title: 'Unauthorized', status: 401 }), {
    status: 401,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}
