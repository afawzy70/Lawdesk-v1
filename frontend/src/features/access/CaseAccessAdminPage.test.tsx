import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { ADMIN, fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';
import { AuthContext } from '../auth/AuthContext';
import type { Me } from '../auth/me';
import { CaseAccessAdminPage } from './CaseAccessAdminPage';

/** LD-DOC-r5: «الاطلاع على هذه القضية» and «المنح الصريحة» moved from the case page to the administration area. */
const CASE = {
  id: 'c1', referenceNo: 'LD-2026-00001', title: 'Ahmed v. Nile', caseType: 'COMMERCIAL', status: 'OPEN',
  openedOn: '2026-01-10', updatedAt: '2026-01-11T00:00:00Z', version: 3, confidentiality: 'NORMAL',
};
const problem = (status: number) => () =>
  new Response(JSON.stringify({ status }), { status, headers: { 'Content-Type': 'application/problem+json' } });

function mount(me: Me, routes: Record<string, () => Response>) {
  const fetchMock = fakeApi({ routes });
  vi.stubGlobal('fetch', fetchMock);
  render(
    <MemoryRouter>
      <LocaleProvider>
        <AuthContext.Provider
          value={{
            state: { status: 'signedIn', me },
            signIn: () => Promise.resolve(true),
            signOut: () => Promise.resolve(),
            refresh: () => Promise.resolve(),
          }}
        >
          <CaseAccessAdminPage />
        </AuthContext.Provider>
      </LocaleProvider>
    </MemoryRouter>,
  );
  return fetchMock;
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

describe('the case access page in the administration area', () => {
  it('is refused to a user without CASE_GRANT_MANAGE', () => {
    mount({ ...ADMIN, permissions: ['CASE_VIEW', 'USER_VIEW'] }, {});
    expect(screen.queryByRole('search')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).not.toHaveTextContent('Case access and explicit grants');
  });

  it('finds a case by its number and shows its confidentiality and explicit grants', async () => {
    const user = userEvent.setup();
    const fetchMock = mount(ADMIN, {
      'GET /api/v1/cases?q=LD-2026-00001': () =>
        jsonResponse({ items: [CASE], page: 0, size: 20, totalItems: 1, totalPages: 1 }),
      'GET /api/v1/cases/c1': () => jsonResponse(CASE),
      'GET /api/v1/cases/c1/grants': () => jsonResponse([]),
      'GET /api/v1/users/directory': () => jsonResponse([]),
    });
    await user.type(screen.getByRole('searchbox', { name: 'Case number or title' }), 'LD-2026-00001');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await user.click(await screen.findByRole('button', { name: /LD-2026-00001 — Ahmed v. Nile/ }));
    expect(await screen.findByRole('heading', { level: 2, name: 'Access to this case' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Case LD-2026-00001' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => input === '/api/v1/cases/c1/grants')).toBe(true);
  });

  it('shows nothing for a case the user may not see (404) and asks for a query first', async () => {
    const user = userEvent.setup();
    mount(ADMIN, {
      'GET /api/v1/cases?q=hidden': () => jsonResponse({ items: [{ ...CASE, id: 'c9' }], page: 0, size: 20, totalItems: 1, totalPages: 1 }),
      'GET /api/v1/cases/c9': problem(404),
    });
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Type the case number');
    await user.type(screen.getByRole('searchbox', { name: 'Case number or title' }), 'hidden');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await user.click(await screen.findByRole('button', { name: /LD-2026-00001/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('does not exist or you may not see it');
    expect(screen.queryByRole('heading', { level: 2, name: 'Access to this case' })).not.toBeInTheDocument();
  });
});
