import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../App';
import { api, ApiError } from '../api/http';

// QA overlay: copied into src/__qa__/ only in disposable exact-SHA snapshots.
// LD-007/008/010/015: real React routes/providers and API adapter; HTTP is a double.
const profile = { id: '00000000-0000-4000-8000-000000000001', username: 'qa',
  displayName: 'QA synthetic', email: 'qa@example.invalid', locale: 'en', roles: ['LEGAL_MANAGER'],
  permissions: ['CASE_VIEW', 'CASE_CREATE', 'CASE_EDIT', 'COURT_VIEW'], mustChangePassword: false };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});
function server(me: typeof profile | null, more?: (path: string, init?: RequestInit) => Response | undefined) {
  const fetcher = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    const custom = more?.(path, init);
    if (custom) return Promise.resolve(custom);
    if (path === '/api/v1/auth/csrf') return Promise.resolve(new Response(null, { status: 204 }));
    if (path === '/api/v1/me') return Promise.resolve(json(me ?? { status: 401 }, me ? 200 : 401));
    if (path === '/actuator/health') return Promise.resolve(json({ status: 'UP' }));
    if (path.startsWith('/api/v1/cases')) return Promise.resolve(json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }));
    return Promise.resolve(json({ status: 404 }, 404));
  });
  vi.stubGlobal('fetch', fetcher);
  return fetcher;
}
function app(path: string) { render(<MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter>); }

describe('Independent Phase2–4 integration', () => {
  it('LD-007: protected case URL shows sign-in before any case fetch', async () => {
    const fetcher = server(null); app('/cases?lang=en');
    expect(await screen.findByRole('heading', { name: 'Sign in to LawDesk' })).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([p]) => String(p).startsWith('/api/v1/cases'))).toBe(false);
  });
  it('LD-008: forced-change user cannot reach cases', async () => {
    const fetcher = server({ ...profile, mustChangePassword: true }); app('/cases?lang=en');
    expect(await screen.findByLabelText('Current password')).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([p]) => String(p).startsWith('/api/v1/cases'))).toBe(false);
  });
  it('LD-007: refused login clears password without echoing server details', async () => {
    server(null, (p) => p === '/api/v1/auth/login' ? json({ status: 401, detail: 'SECRET-INTERNAL-REASON' }, 401) : undefined);
    app('/?lang=en'); const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Username or email'), 'qa');
    await user.type(screen.getByLabelText('Password'), 'synthetic-wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByRole('alert');
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(screen.queryByText('SECRET-INTERNAL-REASON')).not.toBeInTheDocument();
  });
  it('LD-007: Arabic sign-in sets document language/direction and labels password', async () => {
    server(null); app('/cases?lang=ar');
    await screen.findByRole('button', { name: /دخول/ });
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(document.querySelector('input[type=password]')).toHaveAccessibleName();
  });
  it('LD-015: integrated /cases route renders implemented list and performs API request', async () => {
    const fetcher = server(profile); app('/cases?lang=en');
    await screen.findByText(profile.displayName);
    await waitFor(() => expect(fetcher.mock.calls.some(([p]) => String(p).startsWith('/api/v1/cases'))).toBe(true));
    expect(screen.getByRole('searchbox', { name: 'Search by reference or title' })).toBeInTheDocument();
  });
  it('LD-010: unsafe adapter request echoes CSRF and sends same-origin credentials', async () => {
    document.cookie = 'XSRF-TOKEN=qa-test-token; path=/';
    const fetcher = server(profile, () => json({ ok: true }));
    await api('/api/v1/cases', { method: 'POST', body: { title: 'synthetic' } });
    const init = fetcher.mock.calls[0]?.[1];
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('qa-test-token');
    expect(init?.credentials).toBe('same-origin');
  });
  it('LD-010: HTML error body is not surfaced as a problem', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('<script>secret</script>', { status: 500, headers: { 'Content-Type': 'text/html' } }))));
    try { await api('/api/v1/cases'); throw new Error('expected refusal'); }
    catch (error) { expect(error).toBeInstanceOf(ApiError); expect((error as ApiError).problem).toBeNull(); }
  });
});
