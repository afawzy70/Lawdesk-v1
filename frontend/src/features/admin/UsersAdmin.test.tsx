import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../../App';
import { ADMIN, fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';

/** LD-009 user administration screens against the real API contract. */
const MAHA = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'maha',
  email: 'maha@example.invalid',
  displayName: 'Maha Hassan',
  locale: 'ar',
  enabled: true,
  mustChangePassword: false,
  locked: false,
  roles: ['LEGAL_STAFF'],
  createdAt: '2026-10-24T08:00:00Z',
  updatedAt: '2026-10-24T08:00:00Z',
  version: 3,
};
const ROLES = [
  { id: 'r1', code: 'ADMIN', name: 'Administrator', system: true, permissions: ['USER_MANAGE'] },
  { id: 'r2', code: 'LEGAL_MANAGER', name: 'Legal manager', system: true, permissions: ['CASE_DELETE'] },
  { id: 'r3', code: 'LEGAL_STAFF', name: 'Legal staff', system: true, permissions: ['CASE_CREATE'] },
];
const page = (items: unknown[], extra = {}) => jsonResponse({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });
const problem = (status: number, body: object) => () =>
  new Response(JSON.stringify({ status, ...body }), { status, headers: { 'Content-Type': 'application/problem+json' } });

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

function bodyOf(fetchMock: ReturnType<typeof fakeApi>, method: string, path: string): unknown {
  const call = fetchMock.mock.calls.find(([input, init]) => input === path && (init?.method ?? 'GET') === method);
  if (call === undefined) throw new Error(`no ${method} ${path}`);
  return JSON.parse((call[1] as RequestInit).body as string);
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

describe('user list', () => {
  it('lists users with their roles and status, and searches', async () => {
    const fetchMock = fakeApi({
      routes: {
        'GET /api/v1/users?page=0&size=20': () => page([MAHA]),
        'GET /api/v1/users?q=nobody&page=0&size=20': () => page([]),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin');

    const table = await screen.findByRole('table', { name: 'Users' });
    const row = within(table).getByRole('row', { name: /maha/ });
    expect(row).toHaveTextContent('Maha Hassan');
    expect(row).toHaveTextContent('Legal staff');
    expect(row).toHaveTextContent('Active');

    const userSearch = screen.getByRole('searchbox', { name: 'Search users' });
    await user.type(userSearch, 'nobody');
    await user.click(within(userSearch.closest('form') as HTMLFormElement).getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('No users match.')).toBeInTheDocument();
  });

  it('pages through long lists', async () => {
    vi.stubGlobal('fetch', fakeApi({
      routes: {
        'GET /api/v1/users?page=0&size=20': () => page([MAHA], { totalItems: 25, totalPages: 2 }),
        'GET /api/v1/users?page=1&size=20': () => page([{ ...MAHA, id: 'x', username: 'omar', displayName: 'Omar' }], { page: 1, totalItems: 25, totalPages: 2 }),
      },
    }));
    const user = userEvent.setup();
    renderAt('/admin');

    await screen.findByRole('table', { name: 'Users' });
    await user.click(screen.getByRole('button', { name: 'Next page' }));

    expect(await screen.findByText('Omar')).toBeInTheDocument();
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
  });

  it('offers creating users only to user managers', async () => {
    vi.stubGlobal('fetch', fakeApi({
      me: { ...ADMIN, permissions: ['USER_VIEW'] },
      routes: { 'GET /api/v1/users?page=0&size=20': () => page([MAHA]) },
    }));
    renderAt('/admin');

    await screen.findByRole('table', { name: 'Users' });
    expect(screen.queryByRole('link', { name: 'New user' })).not.toBeInTheDocument();
  });

  it('shows access denied when the server refuses', async () => {
    vi.stubGlobal('fetch', fakeApi({
      me: { ...ADMIN, permissions: ['CASE_VIEW'] },
      routes: { 'GET /api/v1/users?page=0&size=20': problem(403, { title: 'Forbidden' }) },
    }));
    renderAt('/admin');

    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });
});

describe('list edge cases', () => {
  it('goes back a page', async () => {
    vi.stubGlobal('fetch', fakeApi({
      routes: {
        'GET /api/v1/users?page=0&size=20': () => page([MAHA], { totalItems: 25, totalPages: 2 }),
        'GET /api/v1/users?page=1&size=20': () => page([{ ...MAHA, id: 'x', username: 'omar', displayName: 'Omar' }], { page: 1, totalItems: 25, totalPages: 2 }),
      },
    }));
    const user = userEvent.setup();
    renderAt('/admin');

    await screen.findByRole('table', { name: 'Users' });
    await user.click(screen.getByRole('button', { name: 'Next page' }));
    await screen.findByText('Page 2 of 2');
    await user.click(screen.getByRole('button', { name: 'Previous page' }));

    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument();
  });

  it('reports a failing list without details', async () => {
    vi.stubGlobal('fetch', fakeApi({ routes: { 'GET /api/v1/users?page=0&size=20': problem(500, { detail: 'secret' }) } }));
    renderAt('/admin');

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Try again later.');
    expect(screen.getByRole('alert')).not.toHaveTextContent('secret');
  });
});

describe('creating a user', () => {
  it('sends the new user with a temporary password and opens it', async () => {
    const fetchMock = fakeApi({
      routes: {
        'GET /api/v1/roles': () => jsonResponse(ROLES),
        'POST /api/v1/users': () => jsonResponse({ ...MAHA, mustChangePassword: true }, 201),
        [`GET /api/v1/users/${MAHA.id}`]: () => jsonResponse({ ...MAHA, mustChangePassword: true }),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/users/new');

    await user.type(await screen.findByLabelText('Username'), 'maha');
    await user.type(screen.getByLabelText('Email'), 'maha@example.invalid');
    await user.type(screen.getByLabelText('Display name'), 'Maha Hassan');
    await user.selectOptions(screen.getByLabelText('Language'), 'ar');
    await user.click(screen.getByRole('checkbox', { name: 'Legal staff' }));
    await user.type(screen.getByLabelText('Temporary password'), 'temporary passphrase 1');
    await user.click(screen.getByRole('button', { name: 'Create user' }));

    expect(await screen.findByRole('heading', { name: 'Maha Hassan' })).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'POST', '/api/v1/users')).toEqual({
      username: 'maha',
      email: 'maha@example.invalid',
      displayName: 'Maha Hassan',
      locale: 'ar',
      roles: ['LEGAL_STAFF'],
      temporaryPassword: 'temporary passphrase 1',
    });
  });

  it('reports roles that cannot be loaded, and lets the user pick and unpick roles', async () => {
    vi.stubGlobal('fetch', fakeApi({ routes: { 'GET /api/v1/roles': problem(500, {}) } }));
    const { unmount } = renderAt('/admin/users/new');
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Try again later.');
    unmount();

    vi.stubGlobal('fetch', fakeApi({ routes: { 'GET /api/v1/roles': () => jsonResponse(ROLES) } }));
    const user = userEvent.setup();
    renderAt('/admin/users/new');
    const box = await screen.findByRole('checkbox', { name: 'Administrator' });
    await user.click(box);
    expect(box).toBeChecked();
    await user.click(box);
    expect(box).not.toBeChecked();
  });

  it('explains duplicates and invalid fields', async () => {
    let answer = problem(409, { title: 'Conflict' });
    vi.stubGlobal('fetch', fakeApi({
      routes: { 'GET /api/v1/roles': () => jsonResponse(ROLES), 'POST /api/v1/users': () => answer() },
    }));
    const user = userEvent.setup();
    renderAt('/admin/users/new');

    await user.type(await screen.findByLabelText('Username'), 'maha');
    await user.click(screen.getByRole('button', { name: 'Create user' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This username or email is already in use.');

    answer = problem(400, { errors: [{ field: 'email', code: 'Email' }, { field: 'temporaryPassword', code: 'Size' }] });
    await user.click(screen.getByRole('button', { name: 'Create user' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Email: check this value.');
    expect(alert).toHaveTextContent('Temporary password: check this value.');
  });
});

describe('user details', () => {
  function details(extra: Record<string, () => Response> = {}) {
    return fakeApi({
      routes: {
        'GET /api/v1/roles': () => jsonResponse(ROLES),
        [`GET /api/v1/users/${MAHA.id}`]: () => jsonResponse(MAHA),
        ...extra,
      },
    });
  }

  it('disables a user', async () => {
    const fetchMock = details({ [`PUT /api/v1/users/${MAHA.id}/enabled`]: () => jsonResponse({ ...MAHA, enabled: false }) });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt(`/admin/users/${MAHA.id}`);

    await user.click(await screen.findByRole('button', { name: 'Disable account' }));

    expect(await screen.findByText('Disabled')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'PUT', `/api/v1/users/${MAHA.id}/enabled`)).toEqual({ enabled: false });
  });

  it('explains why the last administrator cannot be changed', async () => {
    vi.stubGlobal('fetch', details({ [`PUT /api/v1/users/${MAHA.id}/enabled`]: problem(422, { rule: 'LAST_ADMIN' }) }));
    const user = userEvent.setup();
    renderAt(`/admin/users/${MAHA.id}`);

    await user.click(await screen.findByRole('button', { name: 'Disable account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The last active administrator must stay an administrator.');
  });

  it('saves roles', async () => {
    const fetchMock = details({ [`PUT /api/v1/users/${MAHA.id}/roles`]: () => jsonResponse({ ...MAHA, roles: ['LEGAL_MANAGER', 'LEGAL_STAFF'] }) });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt(`/admin/users/${MAHA.id}`);

    await user.click(await screen.findByRole('checkbox', { name: 'Legal manager' }));
    await user.click(screen.getByRole('button', { name: 'Save roles' }));

    expect(await screen.findByText('Roles saved.')).toHaveAttribute('role', 'status');
    expect(bodyOf(fetchMock, 'PUT', `/api/v1/users/${MAHA.id}/roles`)).toEqual({ roles: ['LEGAL_MANAGER', 'LEGAL_STAFF'] });
  });

  it('edits the details with the current version', async () => {
    const fetchMock = details({ [`PUT /api/v1/users/${MAHA.id}`]: () => jsonResponse({ ...MAHA, displayName: 'Maha H.', version: 4 }) });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt(`/admin/users/${MAHA.id}`);

    const name = await screen.findByLabelText('Display name');
    await user.clear(name);
    await user.type(name, 'Maha H.');
    await user.click(screen.getByRole('button', { name: 'Save details' }));

    expect(await screen.findByRole('heading', { name: 'Maha H.' })).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'PUT', `/api/v1/users/${MAHA.id}`)).toEqual({
      email: MAHA.email,
      displayName: 'Maha H.',
      locale: 'ar',
      version: 3,
    });
  });

  it('resets the password to a temporary one', async () => {
    const fetchMock = details({ [`POST /api/v1/users/${MAHA.id}/password-reset`]: () => new Response(null, { status: 204 }) });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt(`/admin/users/${MAHA.id}`);

    await user.type(await screen.findByLabelText('New temporary password'), 'temporary passphrase 2');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByText('The password was reset. The user must change it at the next sign-in.')).toHaveAttribute('role', 'status');
    expect(bodyOf(fetchMock, 'POST', `/api/v1/users/${MAHA.id}/password-reset`)).toEqual({ temporaryPassword: 'temporary passphrase 2' });
  });

  it('enables a disabled user', async () => {
    const fetchMock = fakeApi({
      routes: {
        'GET /api/v1/roles': () => jsonResponse(ROLES),
        [`GET /api/v1/users/${MAHA.id}`]: () => jsonResponse({ ...MAHA, enabled: false }),
        [`PUT /api/v1/users/${MAHA.id}/enabled`]: () => jsonResponse(MAHA),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt(`/admin/users/${MAHA.id}`);

    await user.click(await screen.findByRole('button', { name: 'Enable account' }));

    expect(await screen.findByText('Active')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'PUT', `/api/v1/users/${MAHA.id}/enabled`)).toEqual({ enabled: true });
  });

  it('shows access denied or an error when the user cannot be loaded', async () => {
    vi.stubGlobal('fetch', fakeApi({ routes: { [`GET /api/v1/users/${MAHA.id}`]: problem(403, {}) } }));
    const { unmount } = renderAt(`/admin/users/${MAHA.id}`);
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    unmount();

    vi.stubGlobal('fetch', fakeApi({ routes: { [`GET /api/v1/users/${MAHA.id}`]: problem(500, {}) } }));
    renderAt(`/admin/users/${MAHA.id}`);
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Try again later.');
  });

  it('removes a role', async () => {
    const fetchMock = details({ [`PUT /api/v1/users/${MAHA.id}/roles`]: () => jsonResponse({ ...MAHA, roles: [] }) });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt(`/admin/users/${MAHA.id}`);

    await user.click(await screen.findByRole('checkbox', { name: 'Legal staff' }));
    await user.click(screen.getByRole('button', { name: 'Save roles' }));

    await screen.findByText('Roles saved.');
    expect(bodyOf(fetchMock, 'PUT', `/api/v1/users/${MAHA.id}/roles`)).toEqual({ roles: [] });
  });

  it('is available in Arabic', async () => {
    vi.stubGlobal('fetch', details());
    renderAt(`/admin/users/${MAHA.id}?lang=ar`);

    expect(await screen.findByRole('button', { name: 'تعطيل الحساب' })).toBeInTheDocument();
  });
});
