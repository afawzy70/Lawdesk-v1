import { render } from '@testing-library/react';
import { Outlet, RouterProvider, createMemoryRouter } from 'react-router';
import { vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { jsonResponse } from '../../test/http';
import type { LegalCase } from './api';
import { CaseCreatePage } from './CaseCreatePage';
import { CaseEditPage } from './CaseEditPage';
import { CaseListPage } from './CaseListPage';

/** Test-only helpers for the case screens: the routes as App.tsx mounts them, and a scripted backend. */

export function renderCases(path: string) {
  const router = createMemoryRouter(
    [
      {
        element: (
          <LocaleProvider>
            <Outlet />
          </LocaleProvider>
        ),
        children: [
          { path: '/cases', element: <CaseListPage /> },
          { path: '/cases/new', element: <CaseCreatePage /> },
          { path: '/cases/:id/edit', element: <CaseEditPage /> },
        ],
      },
    ],
    { initialEntries: [path] },
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
export function scriptedFetch(respond: (request: Request) => Response | Promise<Response>) {
  const requests: Request[] = [];
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : null;
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const request = { method: init?.method ?? 'GET', url, body };
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
