import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { Outlet, RouterProvider, createMemoryRouter } from 'react-router';
import { vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { jsonResponse } from '../../test/http';
import type { Proceeding } from '../proceedings/proceedingsApi';
import { CaseOperations } from './CaseOperations';
import type { Hearing } from './hearings/hearingsApi';
import { operationsRoutes } from './routes';
import type { CaseDocument } from './documents/documentsApi';
import type { Expert, Referral } from './experts/expertsApi';
import type { CaseNote } from './notes/notesApi';
import type { Task } from './tasks/tasksApi';

/** Test-only helpers for the legal operations screens: their routes, a case page hosting them, a scripted backend. */

export const CASE = 'c0000000-0000-4000-8000-000000000001';

/** The operations routes inside the locale provider, as App.tsx mounts them, plus a case page with CaseOperations. */
export function renderOperations(path: string) {
  const router = createMemoryRouter(
    [
      {
        element: (
          <LocaleProvider>
            <Outlet />
          </LocaleProvider>
        ),
        children: [
          ...operationsRoutes().map((route) => ({
            path: (route.props as { path: string }).path,
            element: (route.props as { element: ReactNode }).element,
          })),
          { path: '/cases/:id', element: <CaseOperations caseId={CASE} /> },
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
  path: string;
  query: URLSearchParams;
  body: unknown;
}

/** A fetch stub answering each request with `respond` (404 when it returns nothing); `requests` records them. */
export function scriptedFetch(respond: (request: Request) => Response | undefined) {
  const requests: Request[] = [];
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(raw, 'http://localhost');
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : null;
    const request = { method: init?.method ?? 'GET', path: url.pathname, query: url.searchParams, body };
    requests.push(request);
    return Promise.resolve(respond(request) ?? problem(404));
  });
  vi.stubGlobal('fetch', fetchMock);
  return { requests, fetchMock };
}

export function problem(status: number, body: object = {}): Response {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

export const page = (items: readonly unknown[], extra: object = {}) =>
  jsonResponse({ items, page: 0, size: 20, totalItems: items.length, totalPages: items.length === 0 ? 0 : 1, ...extra });

export function hearing(overrides: Partial<Hearing> = {}): Hearing {
  return {
    id: 'h1',
    caseId: CASE,
    caseReferenceNo: 'LD-2025-00042',
    caseTitle: 'Rent dispute',
    proceedingId: 'p1',
    stage: 'FIRST_INSTANCE',
    courtId: 'court-1',
    courtCode: 'FI-1',
    courtNameAr: 'محكمة أول درجة نموذجية',
    courtNameEn: 'Sample First-Instance Court',
    courtCaseNumber: '1234',
    courtCaseYear: 2025,
    hearingDate: '2025-03-02',
    hearingTime: '09:30',
    purpose: 'PLEADING',
    notes: null,
    status: 'SCHEDULED',
    decision: null,
    adjournedFromId: null,
    nextHearingId: null,
    awaitingResult: false,
    version: 0,
    createdAt: '2025-02-01T08:00:00Z',
    updatedAt: '2025-02-01T08:00:00Z',
    ...overrides,
  };
}

export function proceeding(overrides: Partial<Proceeding> = {}): Proceeding {
  return {
    id: 'p1',
    caseId: CASE,
    stage: 'FIRST_INSTANCE',
    courtId: 'court-1',
    courtCode: 'FI-1',
    courtNameAr: 'محكمة أول درجة نموذجية',
    courtNameEn: 'Sample First-Instance Court',
    courtCaseNumber: '1234',
    courtCaseYear: 2025,
    filingDate: '2025-01-10',
    parentProceedingId: null,
    hasJudgment: false,
    version: 0,
    createdAt: '2025-01-10T08:00:00Z',
    updatedAt: '2025-01-10T08:00:00Z',
    ...overrides,
  };
}

export function task(overrides: Partial<Task> = {}): Task {
  return {
    id: 't1',
    caseId: CASE,
    caseReferenceNo: 'LD-2025-00042',
    caseTitle: 'Rent dispute',
    title: 'File the appeal memorandum',
    description: 'Attach the judgment copy',
    assigneeId: 'u1',
    assigneeName: 'Mona Saleh',
    dueDate: '2025-03-04',
    priority: 'NORMAL',
    status: 'OPEN',
    completedAt: null,
    overdue: false,
    dueSoon: false,
    version: 0,
    createdAt: '2025-02-01T08:00:00Z',
    createdBy: 'u9',
    updatedAt: '2025-02-01T08:00:00Z',
    ...overrides,
  };
}

export function caseDocument(overrides: Partial<CaseDocument> = {}): CaseDocument {
  return {
    id: 'd1',
    caseId: CASE,
    caseReferenceNo: 'LD-2025-00042',
    caseTitle: 'Rent dispute',
    title: 'Statement of claim',
    fileName: 'statement-of-claim.pdf',
    mediaType: 'application/pdf',
    sizeBytes: 12 * 1024,
    sha256: 'a'.repeat(64),
    version: 0,
    createdAt: '2025-03-01T09:00:00Z',
    createdBy: 'u9',
    ...overrides,
  };
}

export function expertRecord(overrides: Partial<Expert> = {}): Expert {
  return {
    id: 'e1',
    name: 'Eng. Samir Fathy',
    specialty: 'Civil engineering',
    office: 'Giza experts office',
    phone: '+20 2 1234 5678',
    active: true,
    version: 0,
    createdAt: '2025-01-01T08:00:00Z',
    updatedAt: '2025-01-01T08:00:00Z',
    ...overrides,
  };
}

export function referral(overrides: Partial<Referral> = {}): Referral {
  return {
    id: 'r1',
    caseId: CASE,
    caseReferenceNo: 'LD-2025-00042',
    caseTitle: 'Rent dispute',
    proceedingId: 'p1',
    stage: 'FIRST_INSTANCE',
    courtCaseNumber: '1234',
    courtCaseYear: 2025,
    expertId: 'e1',
    expertName: 'Eng. Samir Fathy',
    expertSpecialty: 'Civil engineering',
    referralDate: '2025-02-01',
    mission: 'Inspect the boundary wall',
    reportDueDate: '2025-04-01',
    status: 'OPEN',
    reportSubmittedDate: null,
    reportOverdue: false,
    sessions: [],
    version: 0,
    createdAt: '2025-02-01T08:00:00Z',
    updatedAt: '2025-02-01T08:00:00Z',
    ...overrides,
  };
}

export function caseNote(overrides: Partial<CaseNote> = {}): CaseNote {
  return {
    id: 'n1',
    caseId: CASE,
    body: 'Client prefers settlement before the appeal.',
    authorId: 'u1',
    authorName: 'Mona Saleh',
    editable: true,
    edited: false,
    version: 0,
    createdAt: '2025-03-01T09:00:00Z',
    updatedAt: '2025-03-01T09:00:00Z',
    ...overrides,
  };
}
