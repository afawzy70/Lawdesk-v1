import { render } from '@testing-library/react';
import { Outlet, RouterProvider, createMemoryRouter } from 'react-router';
import { vi } from 'vitest';
import type { Permission } from '../../api/permissions';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { AuthContext } from '../auth/AuthContext';
import { jsonResponse } from '../../test/http';
import type { LegalCase } from './api';
import { CaseCreatePage } from './CaseCreatePage';
import { CaseDetailsPage } from './CaseDetailsPage';
import { CaseEditPage } from './CaseEditPage';
import { CaseListPage } from './CaseListPage';

/** Test-only helpers for the case screens: the routes as App.tsx mounts them, and a scripted backend. */

/** `permissions` signs a user in with them; without it the screens render with no session, as before. */
export function renderCases(path: string, state?: unknown, permissions?: Permission[]) {
  const router = createMemoryRouter(
    [
      {
        element: (
          <LocaleProvider>
            {permissions === undefined ? (
              <Outlet />
            ) : (
              <AuthContext.Provider
                value={{
                  state: {
                    status: 'signedIn',
                    me: { id: 'u1', username: 'mona', displayName: 'Mona', email: 'mona@example.invalid', locale: 'en', roles: [], permissions, mustChangePassword: false },
                  },
                  signIn: () => Promise.resolve(true),
                  signOut: () => Promise.resolve(),
                  refresh: () => Promise.resolve(),
                }}
              >
                <Outlet />
              </AuthContext.Provider>
            )}
          </LocaleProvider>
        ),
        children: [
          { path: '/cases', element: <CaseListPage /> },
          { path: '/cases/new', element: <CaseCreatePage /> },
          { path: '/cases/:id', element: <CaseDetailsPage /> },
          { path: '/cases/:id/edit', element: <CaseEditPage /> },
        ],
      },
    ],
    { initialEntries: [state === undefined ? path : { pathname: path, state }] },
  );
  const result = render(<RouterProvider router={router} />);
  return { ...result, url: () => `${router.state.location.pathname}${router.state.location.search}` };
}

export interface Request {
  method: string;
  url: string;
  body: unknown;
}

/** A fetch stub answering each request with `respond`; `requests` records what the page sent. */
export function scriptedFetch(
  respond: (request: Request) => Response | Promise<Response>,
  duplicates: { visible: unknown[]; hidden: number } = { visible: [], hidden: 0 },
) {
  const requests: Request[] = [];
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : null;
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const request = { method: init?.method ?? 'GET', url, body };
    // The structure lists of LD-050 (units a case concerns, the authority's units) are not what these tests script.
    if (request.method === 'GET' && /\/duplicate-check\?/.test(url)) return Promise.resolve(jsonResponse(duplicates));
    if (request.method === 'GET' && /\/involvements$|\/parties\/counsel$|\/api\/v1\/org-units/.test(url)) return Promise.resolve(jsonResponse([]));
    // LD-053 (WP3): the sections these tests do not script answer "nothing there" instead of being recorded as requests.
    if (request.method === 'GET' && /\/links$|\/info-requests\?|\/risk-assessments$|\/unified-timeline|\/notifications/.test(url)) {
      return Promise.resolve(jsonResponse({ items: [], hiddenCount: 0, page: 0, size: 20, totalItems: 0, totalPages: 0 }));
    }
    // LD-057 (WP9): settlements and the template library answer "nothing there" in tests that do not script them.
    if (request.method === 'GET' && /\/settlements$|\/api\/v1\/templates|\/api\/v1\/lists\//.test(url)) {
      return Promise.resolve(jsonResponse([]));
    }
    if (request.method === 'GET' && /\/summary$/.test(url)) return Promise.resolve(problem(404));
    requests.push(request);
    return Promise.resolve(respond(request));
  });
  vi.stubGlobal('fetch', fetchMock);
  return { requests, fetchMock };
}

export function problem(status: number, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ type: 'about:blank', status, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

export function aCase(overrides: Partial<LegalCase> = {}): LegalCase {
  return {
    id: '3f2c8a8e-6b1d-4c3e-9a51-0d4b7e2f1a90',
    referenceNo: 'LD-2026-00001',
    title: 'Ahmed v. Nile Trading',
    caseType: 'COMMERCIAL',
    subject: 'Unpaid invoices',
    openedOn: '2026-03-01',
    status: 'OPEN',
    version: 0,
    createdAt: '2026-03-01T08:00:00Z',
    createdBy: '00000000-0000-4000-8000-000000000001',
    updatedAt: '2026-03-01T08:00:00Z',
    updatedBy: '00000000-0000-4000-8000-000000000001',
    ...overrides,
  };
}

export { jsonResponse };
