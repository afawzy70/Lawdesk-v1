import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../../App';
import { NAV_ITEMS } from '../../layout/navigation';
import { ADMIN, fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';

/** The Phase 5 screens in the application shell (architecture/CONTRACTS.md §8.1). */

const EMPTY = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 };

function calendarApi(permissions = ADMIN.permissions) {
  const base = fakeApi({ me: { ...ADMIN, permissions } });
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost');
      if (url.pathname === '/api/v1/hearings') return Promise.resolve(jsonResponse(EMPTY));
      return base(input, init);
    }),
  );
}

describe('operations routes', () => {
  it('marks the hearing calendar ready behind HEARING_VIEW', () => {
    expect(NAV_ITEMS.find((item) => item.path === '/hearings')).toMatchObject({
      status: 'ready',
      requires: 'HEARING_VIEW',
    });
  });

  it('opens the hearing calendar from the primary navigation', async () => {
    calendarApi();
    render(
      <MemoryRouter initialEntries={['/hearings?lang=en']}>
        <AppRoutes />
      </MemoryRouter>,
    );
    expect(await screen.findByRole('heading', { level: 1, name: 'Hearing calendar' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Primary' });
    expect(within(nav).getByRole('link', { name: 'Hearings' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByText('No hearings in this period.')).toBeInTheDocument();
  });

  it('hides the hearing calendar link from users without HEARING_VIEW', async () => {
    calendarApi(ADMIN.permissions.filter((p) => p !== 'HEARING_VIEW'));
    render(
      <MemoryRouter initialEntries={['/?lang=en']}>
        <AppRoutes />
      </MemoryRouter>,
    );
    const nav = await screen.findByRole('navigation', { name: 'Primary' });
    expect(await within(nav).findByRole('link', { name: 'Cases' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Hearings' })).not.toBeInTheDocument();
  });
});
