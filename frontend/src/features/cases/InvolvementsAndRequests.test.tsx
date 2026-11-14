import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { ADMIN, fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';
import { AuthContext } from '../auth/AuthContext';
import type { Me } from '../auth/me';
import { InvolvementsAndRequests } from './InvolvementsAndRequests';

/** LD-DOC-r5: one section for the administrative units concerned and the information requests addressed to them. */
const unit = (id: string, nameEn: string) => ({
  id, tree: 'AUTHORITY', parentId: null, level: 'SECTOR', nameAr: nameEn, nameEn, active: true, validFrom: null, validTo: null, version: 0,
});
const INVOLVED = {
  id: 'i1', caseId: 'c1', orgUnitId: 'u2', primary: true, sectorName: 'Finance', departmentName: null, sectionName: null,
  contactName: null, contactInfo: null, referralDate: null, requestedFromUnit: null, kind: 'EXACT', reasons: [],
};

function mount(me: Me, archived = false) {
  vi.stubGlobal(
    'fetch',
    fakeApi({
      routes: {
        'GET /api/v1/cases/c1/involvements': () => jsonResponse([INVOLVED]),
        'GET /api/v1/cases/c1/info-requests?page=0&size=100': () => jsonResponse({ items: [], page: 0, size: 100, totalItems: 0, totalPages: 0 }),
        'GET /api/v1/org-units?tree=AUTHORITY&includeInactive=false': () => jsonResponse([unit('u1', 'Legal'), unit('u2', 'Finance')]),
        'GET /api/v1/cases/members': () => jsonResponse([]),
      },
    }),
  );
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
          <InvolvementsAndRequests caseId="c1" archived={archived} />
        </AuthContext.Provider>
      </LocaleProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

describe('administrative units and information requests in one section', () => {
  it('has one level-2 heading and both parts inside it, the request addressed to the involved units first', async () => {
    mount(ADMIN);
    const section = await screen.findByRole('region', { name: 'Administrative units concerned and information requests' });
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(1);
    expect(within(section).getByRole('heading', { level: 3, name: 'Administrative units concerned' })).toBeInTheDocument();
    expect(within(section).getByRole('heading', { level: 3, name: /Information requests/ })).toBeInTheDocument();
    const select = await within(section).findByRole('combobox', { name: 'Unit asked to reply' });
    const involved = await within(select).findByRole('group', { name: 'Administrative units concerned by the case' });
    expect(within(involved).getByRole('option', { name: 'Finance' })).toBeInTheDocument();
    const others = within(select).getByRole('group', { name: 'Other units' });
    expect(within(others).getByRole('option', { name: 'Legal' })).toBeInTheDocument();
    expect(within(others).queryByRole('option', { name: 'Finance' })).not.toBeInTheDocument();
  });

  it('keeps each permission apart: without INFO_REQUEST_VIEW only the units part is shown', async () => {
    mount({ ...ADMIN, permissions: ['CASE_VIEW'] });
    const section = await screen.findByRole('region', { name: 'Administrative units concerned and information requests' });
    expect(await within(section).findByRole('heading', { level: 3, name: 'Administrative units concerned' })).toBeInTheDocument();
    expect(within(section).queryByRole('heading', { name: /Information requests/ })).not.toBeInTheDocument();
  });

  it('offers no new request on an archived case', async () => {
    mount(ADMIN, true);
    const section = await screen.findByRole('region', { name: 'Administrative units concerned and information requests' });
    await within(section).findByRole('heading', { level: 3, name: /Information requests/ });
    expect(within(section).queryByRole('combobox', { name: 'Unit asked to reply' })).not.toBeInTheDocument();
  });
});
