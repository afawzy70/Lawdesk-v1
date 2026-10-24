import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../../App';
import { ADMIN, fakeApi, unauthorized } from '../../test/api';
import { jsonResponse } from '../../test/http';

/** LD-007 sign-in/sign-out and LD-008.5 forced password change, through the real shell. */
function renderApp(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

function calls(fetchMock: ReturnType<typeof fakeApi>, method: string, path: string): [unknown, RequestInit][] {
  return fetchMock.mock.calls
    .map(([input, init]) => [input, init ?? {}] as [unknown, RequestInit])
    .filter(([input, init]) => input === path && (init.method ?? 'GET') === method);
}

function firstCall(fetchMock: ReturnType<typeof fakeApi>, method: string, path: string): RequestInit {
  const call = calls(fetchMock, method, path)[0];
  if (call === undefined) throw new Error(`no ${method} ${path}`);
  return call[1];
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

describe('signing in', () => {
  it('asks a visitor without a session to sign in, and shows no application content', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: null }));

    renderApp('/cases');

    expect(await screen.findByRole('heading', { name: 'Sign in to LawDesk' })).toBeInTheDocument();
    expect(screen.getByLabelText('Username or email')).toHaveAttribute('autocomplete', 'username');
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.queryByRole('navigation', { name: 'Primary' })).not.toBeInTheDocument();
  });

  it('signs in with the CSRF header and then shows the shell with the user name', async () => {
    const fetchMock = fakeApi({ me: null, routes: { 'POST /api/v1/auth/login': () => jsonResponse(ADMIN) } });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderApp();

    await user.type(await screen.findByLabelText('Username or email'), 'maha');
    await user.type(screen.getByLabelText('Password'), 'correct horse battery');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Maha Hassan')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeInTheDocument();
    const init = firstCall(fetchMock, 'POST', '/api/v1/auth/login');
    expect(JSON.parse(init.body as string)).toEqual({ login: 'maha', password: 'correct horse battery' });
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('tok');
  });

  it('tells a refused user nothing about why, and keeps the password field empty', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: null, routes: { 'POST /api/v1/auth/login': unauthorized } }));
    const user = userEvent.setup();
    renderApp();

    await user.type(await screen.findByLabelText('Username or email'), 'maha');
    await user.type(screen.getByLabelText('Password'), 'wrong password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The username, email or password is incorrect.');
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(screen.getByLabelText('Username or email')).toHaveValue('maha');
  });

  it('reports an unreachable server without technical details', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: null, routes: { 'POST /api/v1/auth/login': () => new Response('<html>502</html>', { status: 502 }) } }));
    const user = userEvent.setup();
    renderApp();

    await user.type(await screen.findByLabelText('Username or email'), 'maha');
    await user.type(screen.getByLabelText('Password'), 'any password at all');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Signing in is not possible right now. Try again later.');
    expect(screen.getByRole('alert')).not.toHaveTextContent('502');
  });

  it('is available in Arabic', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: null }));

    renderApp('/?lang=ar');

    expect(await screen.findByRole('heading', { name: 'تسجيل الدخول إلى LawDesk' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'تسجيل الدخول' })).toBeInTheDocument();
  });
});

describe('signing out', () => {
  it('ends the session and returns to the sign-in page', async () => {
    const fetchMock = fakeApi({ routes: { 'POST /api/v1/auth/logout': () => new Response(null, { status: 204 }) } });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderApp();

    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in to LawDesk' })).toBeInTheDocument();
    expect(calls(fetchMock, 'POST', '/api/v1/auth/logout')).toHaveLength(1);
  });
});

describe('an expired session', () => {
  it('sends the user back to sign in with an explanation when the API answers 401', async () => {
    const { notifyUnauthorized } = await import('../../api/http');
    vi.stubGlobal('fetch', fakeApi());
    renderApp();
    expect(await screen.findByText('Maha Hassan')).toBeInTheDocument();

    notifyUnauthorized();

    expect(await screen.findByRole('heading', { name: 'Sign in to LawDesk' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Your session has ended. Sign in again to continue.');
  });
});

describe('a required password change', () => {
  it('shows only the password change until it succeeds', async () => {
    let mustChange = true;
    const fetchMock = fakeApi({
      routes: {
        'GET /api/v1/me': () => jsonResponse({ ...ADMIN, mustChangePassword: mustChange }),
        'POST /api/v1/me/password': () => {
          mustChange = false;
          return new Response(null, { status: 204 });
        },
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderApp('/cases');

    expect(await screen.findByRole('heading', { name: 'Choose a new password' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Primary' })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('Current password'), 'first admin passphrase');
    await user.type(screen.getByLabelText('New password'), 'a brand new passphrase');
    await user.type(screen.getByLabelText('Repeat the new password'), 'a brand new passphrase');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByRole('navigation', { name: 'Primary' })).toBeInTheDocument();
    const init = firstCall(fetchMock, 'POST', '/api/v1/me/password');
    expect(JSON.parse(init.body as string)).toEqual({
      currentPassword: 'first admin passphrase',
      newPassword: 'a brand new passphrase',
    });
  });

  it('checks the repetition and the length before asking the server', async () => {
    const fetchMock = fakeApi({ me: { ...ADMIN, mustChangePassword: true } });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderApp();

    await user.type(await screen.findByLabelText('Current password'), 'first admin passphrase');
    await user.type(screen.getByLabelText('New password'), 'short');
    await user.type(screen.getByLabelText('Repeat the new password'), 'shorter');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('The new password must have at least 12 characters.');
    expect(alert).toHaveTextContent('The two new passwords are not the same.');
    expect(calls(fetchMock, 'POST', '/api/v1/me/password')).toHaveLength(0);
  });

  it('explains the answers of the server', async () => {
    const problem = (field: string, code: string) => () =>
      new Response(JSON.stringify({ status: 400, errors: [{ field, code }] }), {
        status: 400,
        headers: { 'Content-Type': 'application/problem+json' },
      });
    vi.stubGlobal('fetch', fakeApi({
      me: { ...ADMIN, mustChangePassword: true },
      routes: { 'POST /api/v1/me/password': problem('currentPassword', 'CurrentPassword') },
    }));
    const user = userEvent.setup();
    renderApp();

    await user.type(await screen.findByLabelText('Current password'), 'not my password at all');
    await user.type(screen.getByLabelText('New password'), 'a brand new passphrase');
    await user.type(screen.getByLabelText('Repeat the new password'), 'a brand new passphrase');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The current password is not correct.');
  });
});

describe('navigation by permission', () => {
  it('shows a ready area only to users who hold its permission', async () => {
    const { visibleNav, NAV_ITEMS } = await import('../../layout/navigation');
    vi.stubGlobal('fetch', fakeApi({ me: { ...ADMIN, roles: ['LEGAL_STAFF'], permissions: ['CASE_VIEW'] } }));
    renderApp();

    const nav = await screen.findByRole('navigation', { name: 'Primary' });
    const expected = visibleNav(new Set(['CASE_VIEW']), NAV_ITEMS).map((item) => item.path);
    expect(within(nav).getAllByRole('link')).toHaveLength(expected.length);
  });
});
