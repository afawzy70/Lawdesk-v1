import { render, screen, waitFor } from '@testing-library/react';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { expect, vi } from 'vitest';
import { AppRoutes } from '../../App';
import type { Permission } from '../../api/permissions';
import { ADMIN } from '../../test/api';
import { jsonResponse } from '../../test/http';

/** Test-only helpers for the report screens: the real routes and a scripted backend keyed by path. */

export interface SentRequest {
  method: string;
  path: string;
  params: URLSearchParams;
}

export type Answer = (request: SentRequest) => Response | Promise<Response>;

export const COURTS = [
  { id: '00000000-0000-4000-8000-0000000000a1', code: 'CAI-1', nameAr: 'محكمة القاهرة الابتدائية', nameEn: 'Cairo Court of First Instance', level: 'FIRST_INSTANCE' },
  { id: '00000000-0000-4000-8000-0000000000a2', code: 'CAI-A', nameAr: 'محكمة استئناف القاهرة', nameEn: 'Cairo Court of Appeal', level: 'APPEAL' },
];

/** Stubs fetch: the session belongs to a user with `permissions`; `answers` maps an API path to its answer. */
export function backend(permissions: Permission[], answers: Record<string, Answer>) {
  const requests: SentRequest[] = [];
  const me = { ...ADMIN, permissions };
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(raw, 'http://localhost');
    const request = { method: init?.method ?? 'GET', path: url.pathname, params: url.searchParams };
    requests.push(request);
    const answer = answers[url.pathname];
    if (answer) return Promise.resolve(answer(request));
    if (url.pathname === '/api/v1/me') return Promise.resolve(jsonResponse(me));
    if (url.pathname === '/actuator/health') return Promise.resolve(jsonResponse({ status: 'UP' }));
    if (url.pathname === '/api/v1/courts') {
      return Promise.resolve(jsonResponse({ items: COURTS, page: 0, size: 100, totalItems: 2, totalPages: 1 }));
    }
    return Promise.resolve(new Response(null, { status: 204 }));
  });
  vi.stubGlobal('fetch', fetchMock);
  const sent = (path: string) => requests.filter((request) => request.path === path);
  return { requests, sent };
}

export function problem(status: number, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ type: 'about:blank', status, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

/** Renders the application at `path` and waits until the session has loaded. */
export async function renderAt(path: string) {
  const router = createMemoryRouter([{ path: '*', element: <AppRoutes /> }], { initialEntries: [path] });
  const result = render(<RouterProvider router={router} />);
  await screen.findByRole('banner');
  await waitFor(() => {
    expect(document.title).not.toBe('');
  });
  return { ...result, location: () => `${router.state.location.pathname}${router.state.location.search}` };
}
