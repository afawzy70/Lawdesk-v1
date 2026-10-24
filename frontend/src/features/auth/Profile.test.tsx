import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../../App';
import { ADMIN, fakeApi } from '../../test/api';

/** My profile (LD-007.6, LD-008): who the user is, what they may do, and a voluntary password change. */
function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

const problem = (status: number, body: object) => () =>
  new Response(JSON.stringify({ status, ...body }), { status, headers: { 'Content-Type': 'application/problem+json' } });

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

describe('my profile', () => {
  it('is linked from the user menu and shows the account, roles and permissions', async () => {
    vi.stubGlobal('fetch', fakeApi());
    const user = userEvent.setup();
    renderAt('/');

    await user.click(await screen.findByRole('link', { name: 'My profile' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'My profile' })).toBeInTheDocument();
    const details = screen.getByRole('region', { name: 'Account' });
    expect(details).toHaveTextContent('maha');
    expect(details).toHaveTextContent('maha@example.invalid');
    expect(details).toHaveTextContent('Administrator');
    const permissions = screen.getByRole('list', { name: 'What you may do' });
    expect(within(permissions).getByText('Manage users')).toBeInTheDocument();
    expect(within(permissions).getByText('Create cases')).toBeInTheDocument();
  });

  it('changes the password and confirms it', async () => {
    const fetchMock = fakeApi({ routes: { 'POST /api/v1/me/password': () => new Response(null, { status: 204 }) } });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/profile');

    await user.type(await screen.findByLabelText('Current password'), 'old passphrase 1');
    await user.type(screen.getByLabelText('New password'), 'new passphrase 22');
    await user.type(screen.getByLabelText('Repeat the new password'), 'new passphrase 22');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByText('Your password was changed.')).toHaveAttribute('role', 'status');
    expect(screen.getByLabelText('Current password')).toHaveValue('');
    const call = fetchMock.mock.calls.find(([input]) => input === '/api/v1/me/password');
    expect(JSON.parse((call?.[1] as RequestInit).body as string)).toEqual({
      currentPassword: 'old passphrase 1',
      newPassword: 'new passphrase 22',
    });
  });

  it('reports a wrong current password', async () => {
    vi.stubGlobal('fetch', fakeApi({
      routes: { 'POST /api/v1/me/password': problem(400, { errors: [{ field: 'currentPassword', code: 'CurrentPassword' }] }) },
    }));
    const user = userEvent.setup();
    renderAt('/profile');

    await user.type(await screen.findByLabelText('Current password'), 'wrong passphrase');
    await user.type(screen.getByLabelText('New password'), 'new passphrase 22');
    await user.type(screen.getByLabelText('Repeat the new password'), 'new passphrase 22');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The current password is not correct.');
  });

  it('is in Arabic for Arabic users', async () => {
    vi.stubGlobal('fetch', fakeApi());
    renderAt('/profile?lang=ar');

    expect(await screen.findByRole('heading', { level: 1, name: 'ملفي الشخصي' })).toBeInTheDocument();
  });

  it('checks the new password before asking the server and reports unknown failures', async () => {
    vi.stubGlobal('fetch', fakeApi({ routes: { 'POST /api/v1/me/password': problem(500, {}) } }));
    const user = userEvent.setup();
    renderAt('/profile');

    await user.type(await screen.findByLabelText('Current password'), 'old passphrase 1');
    await user.type(screen.getByLabelText('New password'), 'short');
    await user.type(screen.getByLabelText('Repeat the new password'), 'other');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('The new password must have at least 12 characters.');
    expect(alert).toHaveTextContent('The two new passwords are not the same.');

    await user.clear(screen.getByLabelText('New password'));
    await user.type(screen.getByLabelText('New password'), 'new passphrase 22');
    await user.clear(screen.getByLabelText('Repeat the new password'));
    await user.type(screen.getByLabelText('Repeat the new password'), 'new passphrase 22');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('The password could not be changed. Try again later.')).toBeInTheDocument();
  });

  it('lists several roles with the Arabic separator', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: { ...ADMIN, roles: ['ADMIN', 'LEGAL_STAFF'] } }));
    renderAt('/profile?lang=ar');

    expect(await screen.findByRole('region', { name: 'الحساب' })).toHaveTextContent('مسؤول النظام، موظف قانوني');
  });
});
