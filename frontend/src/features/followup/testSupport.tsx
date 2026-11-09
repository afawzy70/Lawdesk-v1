import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { vi } from 'vitest';
import type { Permission } from '../../api/permissions';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { jsonResponse } from '../../test/http';
import { AuthContext } from '../auth/AuthContext';
import type { Me } from '../auth/me';
import type { CaseLink, InfoRequest, RiskAssessment } from './api';

/** Test-only helpers of the WP3 screens: a scripted backend and the providers the screens need. */

export const CASE = 'c0000000-0000-4000-8000-000000000001';

export interface Request {
  method: string;
  path: string;
  query: URLSearchParams;
  body: unknown;
  form: FormData | null;
}

export function problem(status: number, body: object = {}): Response {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

/** A fetch stub answering each request with `respond` (404 when it returns nothing); `requests` records them. */
export function serve(respond: (request: Request) => Response | undefined) {
  const requests: Request[] = [];
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(raw, 'http://localhost');
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : null;
    const form = init?.body instanceof FormData ? init.body : null;
    const request = { method: init?.method ?? 'GET', path: url.pathname, query: url.searchParams, body, form };
    requests.push(request);
    return Promise.resolve(respond(request)?.clone() ?? problem(404));
  });
  vi.stubGlobal('fetch', fetchMock);
  return { requests, fetchMock };
}

export const page = (items: readonly unknown[], extra: object = {}) =>
  jsonResponse({ items, page: 0, size: 20, totalItems: items.length, totalPages: items.length === 0 ? 0 : 1, ...extra });

export function me(permissions: Permission[]): Me {
  return {
    id: 'u1',
    username: 'mona',
    displayName: 'Mona Saleh',
    email: 'mona@example.invalid',
    locale: 'en',
    roles: [],
    permissions,
    mustChangePassword: false,
  };
}

/** Renders `ui` inside the router, the locale provider and a signed-in session holding `permissions`. */
export function mount(ui: ReactNode, permissions: Permission[] = [], path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <LocaleProvider>
        <AuthContext.Provider
          value={{
            state: { status: 'signedIn', me: me(permissions) },
            signIn: () => Promise.resolve(true),
            signOut: () => Promise.resolve(),
            refresh: () => Promise.resolve(),
          }}
        >
          {ui}
        </AuthContext.Provider>
      </LocaleProvider>
    </MemoryRouter>,
  );
}

export function link(overrides: Partial<CaseLink> = {}): CaseLink {
  return {
    id: 'l1',
    caseId: CASE,
    direction: 'OUTGOING',
    otherCaseId: 'c0000000-0000-4000-8000-000000000002',
    otherCaseReferenceNo: 'LD-2026-00002',
    otherCaseTitle: 'Second case',
    otherCaseStatus: 'OPEN',
    kind: 'SUBJECT_LINK',
    reason: 'Same decision challenged',
    requestedBy: null,
    requestedOn: null,
    courtDecision: false,
    decisionDate: null,
    operative: null,
    decisionDocumentId: null,
    status: 'ACTIVE',
    version: 0,
    createdAt: '2026-11-01T08:00:00Z',
    ...overrides,
  };
}

export function infoRequest(overrides: Partial<InfoRequest> = {}): InfoRequest {
  return {
    id: 'r1',
    caseId: CASE,
    caseReferenceNo: 'LD-2026-00001',
    requestNo: 'IR-2026-00001',
    unitId: 'unit-1',
    unitName: 'Administrative affairs',
    subject: 'Send the decision file',
    details: null,
    requiredDocuments: null,
    draftedOn: '2026-11-01',
    sentOn: null,
    letterNo: null,
    letterDate: null,
    dueOn: null,
    responsibleMemberId: null,
    responsibleMemberName: null,
    addressee: null,
    notes: null,
    status: 'DRAFT',
    delayReason: null,
    closeReason: null,
    closedOn: null,
    overdue: false,
    replyCount: 0,
    replies: [],
    followUps: [],
    version: 0,
    ...overrides,
  };
}

export function assessment(overrides: Partial<RiskAssessment> = {}): RiskAssessment {
  return {
    id: 'a1',
    caseId: CASE,
    versionNo: 1,
    level: 'MEDIUM',
    levelReasons: 'Mixed evidence',
    potentialLoss: null,
    trigger: 'MANUAL',
    triggerNote: null,
    assessedByName: 'Mona Saleh',
    assessedAt: '2026-11-02T08:00:00Z',
    elements: [{ code: 'EVIDENCE_STRENGTH', rating: 'LOW', reason: 'Complete file' }],
    remediation: [],
    ...overrides,
  };
}
