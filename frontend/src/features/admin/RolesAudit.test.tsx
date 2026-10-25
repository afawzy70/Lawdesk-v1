import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../../App';
import type { Me } from '../auth/me';
import { ADMIN, fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';

/** LD-009.1 role administration and LD-011.5 audit trail screens against the real API contract. */
const ROLE_ADMIN: Me = {
  ...ADMIN,
  permissions: ['USER_VIEW', 'USER_MANAGE', 'ROLE_VIEW', 'ROLE_MANAGE', 'AUDIT_VIEW'],
};
const READER: Me = { ...ADMIN, roles: ['LEGAL_MANAGER'], permissions: ['USER_VIEW', 'ROLE_VIEW'] };
const ROLES = [
  { id: 'r1', code: 'ADMIN', name: 'Administrator', system: true, permissions: ['AUDIT_VIEW', 'CASE_VIEW'], version: 0 },
  { id: 'r4', code: 'ARCHIVE', name: 'Archive clerk', system: false, permissions: ['CASE_VIEW'], version: 2 },
];
const EVENTS = [
  {
    id: 7,
    occurredAt: '2026-10-24T08:30:00Z',
    actorId: 'u1',
    actorUsername: 'maha',
    action: 'ACCESS_DENIED',
    targetType: 'HTTP',
    targetId: 'GET /api/v1/users',
    outcome: 'DENIED',
    reason: null,
    requestId: 'req-1',
  },
  {
    id: 6,
    occurredAt: '2026-10-24T08:00:00Z',
    actorId: null,
    actorUsername: 'omar',
    action: 'LOGIN',
    targetType: 'USER',
    targetId: null,
    outcome: 'FAILURE',
    reason: 'BAD_CREDENTIALS',
    requestId: 'req-2',
  },
];
const page = (items: unknown[], extra = {}) =>
  jsonResponse({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });
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

describe('administration tabs', () => {
  it('shows only the administration areas the user may open', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: READER, routes: { 'GET /api/v1/roles': () => jsonResponse(ROLES) } }));
    renderAt('/admin/roles');

    const tabs = await screen.findByRole('navigation', { name: 'Administration' });
    expect(within(tabs).getByRole('link', { name: 'Users' })).toBeInTheDocument();
    expect(within(tabs).getByRole('link', { name: 'Roles' })).toBeInTheDocument();
    expect(within(tabs).queryByRole('link', { name: 'Audit log' })).not.toBeInTheDocument();
  });
});

describe('roles', () => {
  it('lists roles with their permissions; readers cannot create roles', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: READER, routes: { 'GET /api/v1/roles': () => jsonResponse(ROLES) } }));
    renderAt('/admin/roles');

    const table = await screen.findByRole('table', { name: 'Roles' });
    const row = within(table).getByRole('row', { name: /Archive clerk/ });
    expect(row).toHaveTextContent('View cases');
    expect(within(table).getByRole('row', { name: /Administrator/ })).toHaveTextContent('Built-in');
    expect(screen.queryByRole('link', { name: 'New role' })).not.toBeInTheDocument();
  });

  it('creates a role with chosen permissions', async () => {
    const created = { id: 'r9', code: 'READERS', name: 'Readers', system: false, permissions: ['COURT_VIEW'], version: 0 };
    const fetchMock = fakeApi({
      me: ROLE_ADMIN,
      routes: {
        'GET /api/v1/roles': () => jsonResponse(ROLES),
        'POST /api/v1/roles': () => jsonResponse(created, 201),
        'GET /api/v1/roles/r9': () => jsonResponse(created),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/roles');

    await user.click(await screen.findByRole('link', { name: 'New role' }));
    await user.type(await screen.findByRole('textbox', { name: 'Code' }), 'READERS');
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Readers');
    await user.click(screen.getByRole('checkbox', { name: 'View courts' }));
    await user.click(screen.getByRole('button', { name: 'Create role' }));

    expect(await screen.findByRole('heading', { name: 'Readers' })).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'POST', '/api/v1/roles')).toEqual({ code: 'READERS', name: 'Readers', permissions: ['COURT_VIEW'] });
  });

  it('changes the permissions of a role with its version', async () => {
    const role = ROLES[1];
    const fetchMock = fakeApi({
      me: ROLE_ADMIN,
      routes: {
        'GET /api/v1/roles/r4': () => jsonResponse(role),
        'PUT /api/v1/roles/r4': () => jsonResponse({ ...role, permissions: ['CASE_EDIT', 'CASE_VIEW'], version: 3 }),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/roles/r4');

    expect(await screen.findByRole('checkbox', { name: 'View cases' })).toBeChecked();
    expect(screen.queryByRole('textbox', { name: 'Code' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Edit cases' }));
    await user.click(screen.getByRole('button', { name: 'Save role' }));

    expect(await screen.findByText('Role saved.')).toHaveAttribute('role', 'status');
    expect(bodyOf(fetchMock, 'PUT', '/api/v1/roles/r4')).toEqual({
      name: 'Archive clerk',
      permissions: ['CASE_VIEW', 'CASE_EDIT'],
      version: 2,
    });
  });

  it('explains refusals without server text', async () => {
    vi.stubGlobal('fetch', fakeApi({
      me: ROLE_ADMIN,
      routes: {
        'GET /api/v1/roles/r1': () => jsonResponse(ROLES[0]),
        'PUT /api/v1/roles/r1': problem(422, { rule: 'ADMIN_ROLE_FIXED', detail: 'internal text' }),
        'GET /api/v1/roles/r4': () => jsonResponse(ROLES[1]),
        'PUT /api/v1/roles/r4': problem(409, { detail: 'stale version' }),
      },
    }));
    const user = userEvent.setup();
    const first = renderAt('/admin/roles/r1');

    await user.click(await screen.findByRole('checkbox', { name: 'View cases' }));
    await user.click(screen.getByRole('button', { name: 'Save role' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The administrator role always keeps every permission.');
    first.unmount();

    renderAt('/admin/roles/r4');
    await user.click(await screen.findByRole('button', { name: 'Save role' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('This role code is already used, or the role changed meanwhile. Reload and try again.');
    expect(alert).not.toHaveTextContent('stale version');
  });

  it('shows a role read-only to readers', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: READER, routes: { 'GET /api/v1/roles/r4': () => jsonResponse(ROLES[1]) } }));
    renderAt('/admin/roles/r4');

    expect(await screen.findByRole('checkbox', { name: 'View cases' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Save role' })).not.toBeInTheDocument();
  });

  it('answers an unknown role with not found', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: ROLE_ADMIN }));
    renderAt('/admin/roles/missing');

    expect(await screen.findByRole('alert')).toHaveTextContent('This role does not exist.');
  });
});

describe('audit log', () => {
  it('lists events newest first and filters them', async () => {
    const fetchMock = fakeApi({
      me: ROLE_ADMIN,
      routes: {
        'GET /api/v1/audit?page=0&size=20': () => page(EVENTS),
        'GET /api/v1/audit?actor=omar&outcome=FAILURE&page=0&size=20': () => page([EVENTS[1]]),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/audit');

    const table = await screen.findByRole('table', { name: 'Audit log' });
    const rows = within(table).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Access denied');
    expect(rows[1]).toHaveTextContent('GET /api/v1/users');
    expect(rows[2]).toHaveTextContent('Sign-in');
    expect(rows[2]).toHaveTextContent('Failed');
    expect(rows[2]).toHaveTextContent('Wrong password');

    await user.type(screen.getByRole('textbox', { name: 'User' }), 'omar');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Outcome' }), 'FAILURE');
    await user.click(screen.getByRole('button', { name: 'Filter' }));

    await vi.waitFor(() => {
      expect(within(screen.getByRole('table', { name: 'Audit log' })).getAllByRole('row')).toHaveLength(2);
    });
  });

  it('names refused revoked sessions (LD-008.7) and offers them as a filter', async () => {
    const revoked = { ...EVENTS[0], id: 8, action: 'SESSION_REVOKED', targetType: 'USER', targetId: 'u1' };
    const fetchMock = fakeApi({
      me: ROLE_ADMIN,
      routes: {
        'GET /api/v1/audit?page=0&size=20': () => page([revoked]),
        'GET /api/v1/audit?action=SESSION_REVOKED&page=0&size=20': () => page([revoked]),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/audit');

    const table = await screen.findByRole('table', { name: 'Audit log' });
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('Revoked session refused');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Action' }), 'SESSION_REVOKED');
    await user.click(screen.getByRole('button', { name: 'Filter' }));

    await vi.waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => input === '/api/v1/audit?action=SESSION_REVOKED&page=0&size=20'))
        .toBe(true);
    });
  });

  it.each([
    { lang: 'en', label: 'Task status changed', action: 'Action' },
    { lang: 'ar', label: 'تغيير حالة مهمة', action: 'الإجراء' },
  ])('names task status changes (owner decision 2026-10-25) and offers them as a filter ($lang)', async (c) => {
    const changed = {
      ...EVENTS[0],
      id: 9,
      action: 'TASK_STATUS_CHANGED',
      targetType: 'TASK',
      targetId: 't1',
      reason: 'OPEN->DONE',
    };
    const fetchMock = fakeApi({
      me: ROLE_ADMIN,
      routes: {
        'GET /api/v1/audit?page=0&size=20': () => page([changed]),
        'GET /api/v1/audit?action=TASK_STATUS_CHANGED&page=0&size=20': () => page([changed]),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt(`/admin/audit?lang=${c.lang}`);

    const row = (await screen.findAllByRole('row'))[1];
    expect(row).toHaveTextContent(c.label);
    expect(row).toHaveTextContent('OPEN->DONE');
    await user.selectOptions(screen.getByRole('combobox', { name: c.action }), 'TASK_STATUS_CHANGED');
    await user.click(screen.getAllByRole('button').find((b) => b.getAttribute('type') === 'submit') as HTMLElement);

    await vi.waitFor(() => {
      expect(
        fetchMock.mock.calls.some(([input]) => input === '/api/v1/audit?action=TASK_STATUS_CHANGED&page=0&size=20'),
      ).toBe(true);
    });
  });

  it('pages through the trail', async () => {
    vi.stubGlobal('fetch', fakeApi({
      me: ROLE_ADMIN,
      routes: {
        'GET /api/v1/audit?page=0&size=20': () => page([EVENTS[0]], { totalItems: 21, totalPages: 2 }),
        'GET /api/v1/audit?page=1&size=20': () => page([EVENTS[1]], { page: 1, totalItems: 21, totalPages: 2 }),
      },
    }));
    const user = userEvent.setup();
    renderAt('/admin/audit');

    await user.click(await screen.findByRole('button', { name: 'Next page' }));
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });

  it('is refused to users without the audit permission', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: READER, routes: { 'GET /api/v1/audit?page=0&size=20': problem(403, {}) } }));
    renderAt('/admin/audit');

    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });
});

describe('failures and other states', () => {
  it('refuses the role list to users without access and reports other failures', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: READER, routes: { 'GET /api/v1/roles': problem(403, {}) } }));
    const denied = renderAt('/admin/roles');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    denied.unmount();

    vi.stubGlobal('fetch', fakeApi({ me: READER, routes: { 'GET /api/v1/roles': problem(500, {}) } }));
    renderAt('/admin/roles');
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Try again later.');
  });

  it('shows role names and permissions in Arabic', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: READER, routes: { 'GET /api/v1/roles': () => jsonResponse(ROLES) } }));
    renderAt('/admin/roles?lang=ar');

    const table = await screen.findByRole('table', { name: 'الأدوار' });
    expect(within(table).getByRole('row', { name: /مسؤول النظام/ })).toHaveTextContent('عرض سجل التدقيق، عرض القضايا');
  });

  it('names the fields a new role got wrong', async () => {
    vi.stubGlobal('fetch', fakeApi({
      me: ROLE_ADMIN,
      routes: { 'POST /api/v1/roles': problem(400, { errors: [{ field: 'code', code: 'Pattern' }] }) },
    }));
    const user = userEvent.setup();
    renderAt('/admin/roles/new');

    await user.type(await screen.findByRole('textbox', { name: 'Code' }), 'bad code');
    await user.click(screen.getByRole('button', { name: 'Create role' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Code: check this value.');
  });

  it('refuses a role to users without access and reports load failures', async () => {
    vi.stubGlobal('fetch', fakeApi({ me: ROLE_ADMIN, routes: { 'GET /api/v1/roles/r4': problem(403, {}) } }));
    const denied = renderAt('/admin/roles/r4');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    denied.unmount();

    vi.stubGlobal('fetch', fakeApi({ me: ROLE_ADMIN, routes: { 'GET /api/v1/roles/r4': problem(500, {}) } }));
    renderAt('/admin/roles/r4');
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Try again later.');
  });

  it('filters the audit log by action, reports no matches, failures and pages back', async () => {
    const fetchMock = fakeApi({
      me: ROLE_ADMIN,
      routes: {
        'GET /api/v1/audit?page=0&size=20': () => page([EVENTS[0]], { totalItems: 21, totalPages: 2 }),
        'GET /api/v1/audit?page=1&size=20': () => page([EVENTS[1]], { page: 1, totalItems: 21, totalPages: 2 }),
        'GET /api/v1/audit?action=ROLE_CREATE&page=0&size=20': () => page([]),
        'GET /api/v1/audit?action=LOGOUT&page=0&size=20': problem(500, {}),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/audit');

    await user.click(await screen.findByRole('button', { name: 'Next page' }));
    await user.click(await screen.findByRole('button', { name: 'Previous page' }));
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument();

    await user.selectOptions(screen.getByRole('combobox', { name: 'Action' }), 'ROLE_CREATE');
    await user.click(screen.getByRole('button', { name: 'Filter' }));
    expect(await screen.findByText('No events match.')).toBeInTheDocument();

    await user.selectOptions(screen.getByRole('combobox', { name: 'Action' }), 'LOGOUT');
    await user.click(screen.getByRole('button', { name: 'Filter' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong. Try again later.');
  });

  it('shows codes it has no translation for as they were recorded', async () => {
    const odd = { ...EVENTS[1], id: 9, action: 'FUTURE_EVENT', reason: 'NEW_REASON', actorUsername: null, targetId: null,
      targetType: null, requestId: null };
    vi.stubGlobal('fetch', fakeApi({ me: ROLE_ADMIN, routes: { 'GET /api/v1/audit?page=0&size=20': () => page([odd]) } }));
    renderAt('/admin/audit');

    const rows = within(await screen.findByRole('table', { name: 'Audit log' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('FUTURE_EVENT');
    expect(rows[1]).toHaveTextContent('NEW_REASON');
  });
});
