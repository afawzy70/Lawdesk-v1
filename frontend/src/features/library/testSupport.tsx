import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { vi } from 'vitest';
import { PERMISSIONS, type Permission } from '../../api/permissions';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { jsonResponse } from '../../test/http';
import { AuthContext } from '../auth/AuthContext';
import type { Me } from '../auth/me';
import type { Classification, Judgment, JudgmentRef, ListItem } from './api';
import { libraryRoutes } from './routes';
import { Where } from './Where';

/** Test-only helpers of the library screens: the routes as App.tsx mounts them, a signed-in user and a scripted API. */

export interface Call {
  method: string;
  path: string;
  search: URLSearchParams;
  body: unknown;
}

export const problem = (status: number, extra: Record<string, unknown> = {}): Response =>
  new Response(JSON.stringify({ type: 'about:blank', status, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });

export function user(permissions: readonly Permission[] = PERMISSIONS): Me {
  return {
    id: 'u1',
    username: 'sara',
    displayName: 'Sara',
    email: 'sara@example.invalid',
    locale: 'en',
    roles: ['LEGAL_STAFF'],
    permissions: [...permissions],
    mustChangePassword: false,
  };
}

/** Answers each library request with `respond` (undefined answers 404) and records what the screen sent. */
export function script(respond: (call: Call) => Response | undefined) {
  const calls: Call[] = [];
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(raw, 'http://localhost');
    const call: Call = {
      method: init?.method ?? 'GET',
      path: url.pathname,
      search: url.searchParams,
      body: typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : null,
    };
    calls.push(call);
    return Promise.resolve(respond(call) ?? jsonResponse({ status: 404 }, 404));
  });
  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}

export function renderLibrary(path: string, me: Me | null = user(), caseId = 'c1') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <LocaleProvider>
        <AuthContext.Provider
          value={{
            state: me === null ? { status: 'signedOut', expired: false } : { status: 'signedIn', me },
            signIn: () => Promise.resolve(true),
            signOut: () => Promise.resolve(),
            refresh: () => Promise.resolve(),
          }}
        >
          <Routes>
            {libraryRoutes()}
            <Route path={`/cases/${caseId}`} element={<p>case page</p>} />
          </Routes>
          <Where />
        </AuthContext.Provider>
      </LocaleProvider>
    </MemoryRouter>,
  );
}

export const classification = (overrides: Partial<Classification> = {}): Classification => ({
  id: 'k1',
  dimension: 'LEGAL_TOPIC',
  code: 'LIABILITY',
  labelAr: 'المسؤولية الإدارية',
  labelEn: 'Administrative liability',
  origin: 'PROPOSED',
  active: true,
  sortOrder: 100,
  version: 0,
  updatedAt: '2026-11-01T08:00:00Z',
  ...overrides,
});

export const listItem = (overrides: Partial<ListItem> = {}): ListItem => ({
  id: 'j1',
  courtName: 'محكمة التمييز',
  courtLevel: 'CASSATION',
  chamber: null,
  caseNumber: '1234',
  caseYear: 2020,
  judgmentDate: '2021-03-02',
  caseType: 'ADMINISTRATIVE',
  subject: 'تعويض عن قرار إداري معيب',
  direction: 'AGAINST_AUTHORITY',
  finality: 'FINAL',
  confidentiality: 'NORMAL',
  status: 'ACTIVE',
  classifications: [],
  principleCount: 0,
  relationTypes: [],
  score: 0,
  matchedIn: [],
  ...overrides,
});

export const judgment = (overrides: Partial<Judgment> = {}): Judgment => ({
  id: 'j1',
  courtName: 'محكمة التمييز',
  courtLevel: 'CASSATION',
  chamber: 'الدائرة الإدارية',
  caseNumber: '1234',
  caseYear: 2020,
  judgmentDate: '2021-03-02',
  caseType: 'ADMINISTRATIVE',
  subject: 'تعويض عن قرار إداري معيب',
  partiesDescription: 'جهة إدارية ضد مواطن',
  partiesMasked: false,
  summary: 'ملخص الحكم',
  operativeText: 'حكمت المحكمة بالتعويض',
  reasons: 'لثبوت الخطأ',
  legalTexts: 'المادة 227',
  requestsAddressed: 'طلب التعويض',
  requestResults: 'قبول الطلب',
  direction: 'AGAINST_AUTHORITY',
  keywords: ['تعويض', 'قرار إداري'],
  finality: 'FINAL',
  confidentiality: 'NORMAL',
  sourceReference: 'مجموعة الأحكام',
  verificationNote: 'قوبلت بالنسخة الرسمية',
  copyReference: 'ملف 12',
  status: 'ACTIVE',
  withdrawnReason: null,
  classifications: [classification()],
  principles: [],
  relations: { outgoing: [], incoming: [] },
  validity: {
    judgmentId: 'j1',
    finality: 'FINAL',
    status: 'ACTIVE',
    relationCount: 0,
    relationTypes: [],
    warnings: [],
    legalValidityDecided: false,
    note: 'note',
  },
  linkedCases: [],
  version: 3,
  createdAt: '2026-11-01T08:00:00Z',
  createdBy: 'u9',
  updatedAt: '2026-11-01T08:00:00Z',
  ...overrides,
});

export const ref = (overrides: Partial<JudgmentRef> = {}): JudgmentRef => ({
  id: 'j1',
  courtName: 'محكمة التمييز',
  courtLevel: 'CASSATION',
  caseNumber: '1234',
  caseYear: 2020,
  judgmentDate: '2021-03-02',
  caseType: 'LABOR',
  subject: 'التعويض عن إنهاء الخدمة',
  finality: 'FINAL',
  status: 'ACTIVE',
  sourceReference: 'مجموعة الأحكام',
  hasCopy: true,
  ...overrides,
});

export const page = <T,>(items: T[], extra: Record<string, unknown> = {}) => ({
  items,
  page: 0,
  size: 20,
  totalItems: items.length,
  totalPages: items.length === 0 ? 0 : 1,
  ...extra,
});

export { jsonResponse };
