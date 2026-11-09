import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { vi } from 'vitest';
import { PERMISSIONS, type Permission } from '../../api/permissions';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { jsonResponse } from '../../test/http';
import { AuthContext } from '../auth/AuthContext';

/** Test-only helpers for the claims, defences, evidence and judgment-result screens (LD-051). */

export const CASE = 'c0000000-0000-4000-8000-000000000001';

export interface Sent {
  method: string;
  path: string;
  query: string;
  body: unknown;
}

type Handler = object | ((sent: Sent) => unknown);

/**
 * A scripted backend: `routes` maps "METHOD /path" to a value (answered as JSON, 201 for a POST), a Response, or a
 * function of the request returning either. Unknown requests answer 404 and are recorded like the others.
 */
export function serve(routes: Record<string, Handler>) {
  const sent: Sent[] = [];
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(raw, 'http://localhost');
    const method = init?.method ?? 'GET';
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : null;
    const request: Sent = { method, path: url.pathname, query: url.search, body };
    sent.push(request);
    const handler = routes[`${method} ${url.pathname}`];
    if (handler === undefined) return Promise.resolve(problem(404));
    const value: unknown = typeof handler === 'function' ? (handler as (s: Sent) => unknown)(request) : handler;
    if (value instanceof Response) return Promise.resolve(value.clone());
    return Promise.resolve(jsonResponse(value, method === 'POST' ? 201 : 200));
  });
  vi.stubGlobal('fetch', fetchMock);
  return {
    sent,
    fetchMock,
    /** The requests of one method and path. */
    to: (method: string, path: string) => sent.filter((s) => s.method === method && s.path === path),
  };
}

export function problem(status: number, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

export function signedIn(permissions: readonly Permission[]) {
  return {
    state: {
      status: 'signedIn' as const,
      me: {
        id: '00000000-0000-4000-8000-000000000001',
        username: 'maha',
        displayName: 'Maha',
        email: 'maha@example.invalid',
        locale: 'en' as const,
        roles: [],
        permissions: [...permissions],
        mustChangePassword: false,
      },
    },
    signIn: () => Promise.resolve(true),
    signOut: () => Promise.resolve(),
    refresh: () => Promise.resolve(),
  };
}

/** Renders `ui` with a signed-in user holding `permissions` (everyone by default), in English or Arabic. */
export function renderWith(ui: ReactNode, { permissions = PERMISSIONS, lang = 'en' }: { permissions?: readonly Permission[]; lang?: 'en' | 'ar' } = {}) {
  document.cookie = 'XSRF-TOKEN=test-token';
  return render(
    <MemoryRouter initialEntries={[`/?lang=${lang}`]}>
      <LocaleProvider>
        <AuthContext.Provider value={signedIn(permissions)}>{ui}</AuthContext.Provider>
      </LocaleProvider>
    </MemoryRouter>,
  );
}

export const proceeding = (id: string, stage: 'FIRST_INSTANCE' | 'APPEAL' | 'CASSATION', number: string, extra = {}) => ({
  id,
  caseId: CASE,
  stage,
  courtId: 'court',
  courtCode: 'C',
  courtNameAr: 'محكمة',
  courtNameEn: 'Court',
  courtCaseNumber: number,
  courtCaseYear: 2026,
  filingDate: '2026-01-12',
  parentProceedingId: null,
  hasJudgment: false,
  version: 0,
  createdAt: '',
  updatedAt: '',
  ...extra,
});

export const page = (items: readonly unknown[]) => ({ items, page: 0, size: 100, totalItems: items.length, totalPages: 1 });

export const claim = (id: string, seq: number, text: string, extra = {}) => ({
  id,
  caseId: CASE,
  seq,
  text,
  kind: 'PRIMARY',
  status: 'STANDING',
  submittedByPartyId: null,
  submittedByPartyName: null,
  submittedOn: null,
  stageId: null,
  sourceDocumentId: null,
  sourceDocumentTitle: null,
  notes: null,
  revision: 1,
  version: 0,
  ...extra,
});

export const DOCUMENT = {
  id: 'd1',
  caseId: CASE,
  caseReferenceNo: null,
  caseTitle: null,
  title: 'Statement of claim',
  fileName: 'claim.pdf',
  mediaType: 'application/pdf',
  sizeBytes: 100,
  sha256: 'a',
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
  createdBy: 'u',
};

/** The lists every form picks from (parties, documents, proceedings) answered as the case has them. */
export const PICKERS = {
  [`GET /api/v1/cases/${CASE}/parties`]: [
    { partyId: 'party1', kind: 'PERSON', name: 'Synthetic plaintiff', role: 'OPPONENT', addedAt: '' },
  ],
  [`GET /api/v1/cases/${CASE}/documents`]: page([DOCUMENT]),
  [`GET /api/v1/cases/${CASE}/proceedings`]: page([proceeding('p1', 'FIRST_INSTANCE', '100'), proceeding('p2', 'APPEAL', '7')]),
};
