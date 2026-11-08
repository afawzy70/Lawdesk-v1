import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../../App';
import { ADMIN, fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';
import { arrange } from './orgTree';
import type { OrgUnit } from './api';

/** LD-050: the organisation tree, access scopes, case grants and the unit of a user. */
const unit = (over: Partial<OrgUnit>): OrgUnit => ({
  id: 'u1',
  tree: 'LEGAL',
  parentId: null,
  level: 'DEPARTMENT',
  nameAr: 'الإدارة القانونية',
  nameEn: 'Legal department',
  active: true,
  validFrom: null,
  validTo: null,
  version: 0,
  ...over,
});
const UNITS = [
  unit({}),
  unit({
    id: 'u2',
    parentId: 'u1',
    level: 'SECTION',
    nameAr: 'قسم القضايا',
    nameEn: null,
  }),
];
const problem = (status: number) => () =>
  new Response(JSON.stringify({ status }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

function bodyOf(fetchMock: ReturnType<typeof fakeApi>, method: string, path: string): unknown {
  const call = fetchMock.mock.calls
    .filter(([input, init]) => input === path && (init?.method ?? 'GET') === method)
    .at(-1);
  if (call === undefined) throw new Error(`no ${method} ${path}`);
  return JSON.parse((call[1] as RequestInit).body as string);
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

describe('arrange', () => {
  it('puts children under their parent and treats an unknown parent as top level', () => {
    const rows = arrange([UNITS[1], unit({ id: 'u3', parentId: 'gone', nameAr: 'يتيمة' }), UNITS[0]]);
    expect(rows.map((row) => [row.unit.id, row.depth])).toEqual([
      ['u3', 0],
      ['u1', 0],
      ['u2', 1],
    ]);
  });
});

describe('organisation structure', () => {
  it('lists the tree, adds a unit and deactivates one', async () => {
    const fetchMock = fakeApi({
      routes: {
        'GET /api/v1/org-units?tree=LEGAL&includeInactive=true': () => jsonResponse(UNITS),
        'POST /api/v1/org-units': () => jsonResponse(unit({ id: 'u9' }), 201),
        'PUT /api/v1/org-units/u2': () => jsonResponse({ ...UNITS[1], active: false, version: 1 }),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/org');

    const list = await screen.findByRole('list', { name: 'Units' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(list).toHaveTextContent('Legal department');
    expect(list).toHaveTextContent('قسم القضايا');

    await user.selectOptions(screen.getByLabelText('Level'), 'SECTION');
    await user.selectOptions(screen.getByLabelText('Parent unit'), 'u1');
    await user.type(screen.getByLabelText('Name in Arabic'), 'قسم جديد');
    await user.type(screen.getByLabelText('Name in English'), 'New section');
    await user.click(screen.getByRole('button', { name: 'Add unit' }));
    expect(await screen.findByText('Unit added.')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'POST', '/api/v1/org-units')).toEqual({
      tree: 'LEGAL',
      parentId: 'u1',
      level: 'SECTION',
      nameAr: 'قسم جديد',
      nameEn: 'New section',
    });

    await user.click(within(list).getAllByRole('button', { name: 'Deactivate' })[1]);
    expect(await screen.findByText('Unit saved.')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'PUT', '/api/v1/org-units/u2')).toMatchObject({
      active: false,
      version: 0,
    });
  });

  it('switches to the authority tree and explains refusals', async () => {
    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: {
          'GET /api/v1/org-units?tree=LEGAL&includeInactive=true': () => jsonResponse([]),
          'GET /api/v1/org-units?tree=AUTHORITY&includeInactive=true': () =>
            jsonResponse([unit({ tree: 'AUTHORITY', active: false })]),
          'POST /api/v1/org-units': problem(409),
        },
      }),
    );
    const user = userEvent.setup();
    renderAt('/admin/org');

    expect(await screen.findByText('No units yet.')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Structure'), 'AUTHORITY');
    expect(await screen.findByText(/inactive/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Add unit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('exists under the same parent');
  });

  it('shows an invalid-input message and a load failure, and denies users without the right', async () => {
    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: {
          'GET /api/v1/org-units?tree=LEGAL&includeInactive=true': () => jsonResponse([]),
          'POST /api/v1/org-units': problem(422),
        },
      }),
    );
    const user = userEvent.setup();
    const { unmount } = renderAt('/admin/org');
    await screen.findByText('No units yet.');
    await user.click(screen.getByRole('button', { name: 'Add unit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Check the name');
    unmount();

    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: {
          'GET /api/v1/org-units?tree=LEGAL&includeInactive=true': problem(500),
        },
      }),
    );
    const second = renderAt('/admin/org');
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    second.unmount();

    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: {
          'GET /api/v1/org-units?tree=LEGAL&includeInactive=true': problem(403),
        },
      }),
    );
    renderAt('/admin/org');
    expect(await screen.findByRole('heading', { name: /access/i })).toBeInTheDocument();
  });
});

describe('access scopes', () => {
  const ROLES = [
    {
      id: 'r1',
      code: 'LEGAL_MANAGER',
      name: 'Legal manager',
      system: true,
      permissions: [],
      version: 0,
    },
    {
      id: 'r2',
      code: 'SECTION_HEAD',
      name: 'Section head',
      system: true,
      permissions: [],
      version: 0,
    },
  ];
  const SCOPES = [
    {
      id: 's1',
      subjectType: 'ROLE',
      subjectRef: 'LEGAL_MANAGER',
      scopeType: 'ALL',
      scopeRef: null,
    },
    {
      id: 's2',
      subjectType: 'ROLE',
      subjectRef: 'SECTION_HEAD',
      scopeType: 'ORG_UNIT',
      scopeRef: 'u2',
    },
    {
      id: 's3',
      subjectType: 'ROLE',
      subjectRef: 'ARCHIVIST',
      scopeType: 'CASE_TYPE',
      scopeRef: 'LABOR',
    },
  ];
  const routes = (extra: Record<string, () => Response> = {}) => ({
    'GET /api/v1/access-scopes': () => jsonResponse(SCOPES),
    'GET /api/v1/roles': () => jsonResponse(ROLES),
    'GET /api/v1/org-units?tree=LEGAL&includeInactive=false': () => jsonResponse(UNITS),
    ...extra,
  });

  it('lists scopes, adds one of each kind and removes one', async () => {
    const fetchMock = fakeApi({
      routes: routes({
        'POST /api/v1/access-scopes': () => jsonResponse({ ...SCOPES[0], id: 's9' }, 201),
        'DELETE /api/v1/access-scopes/s1': () => new Response(null, { status: 204 }),
      }),
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/access');

    expect(await screen.findByText(/قسم القضايا/)).toBeInTheDocument();
    expect(screen.getByText(/LABOR/)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Role'), 'SECTION_HEAD');
    await user.selectOptions(screen.getByLabelText('Scope'), 'ORG_UNIT');
    await user.selectOptions(screen.getByLabelText('Unit or case type'), 'u2');
    await user.click(screen.getByRole('button', { name: 'Add scope' }));
    expect(await screen.findByText('Scope added.')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'POST', '/api/v1/access-scopes')).toEqual({
      subjectType: 'ROLE',
      subjectRef: 'SECTION_HEAD',
      scopeType: 'ORG_UNIT',
      scopeRef: 'u2',
    });

    await user.selectOptions(screen.getByLabelText('Scope'), 'CASE_TYPE');
    await user.selectOptions(screen.getByLabelText('Unit or case type'), 'LABOR');
    await user.click(screen.getByRole('button', { name: 'Add scope' }));
    await screen.findByText('Scope added.');
    await user.selectOptions(screen.getByLabelText('Scope'), 'ALL');
    expect(screen.queryByLabelText('Unit or case type')).not.toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: 'Remove' })[0]);
    expect(await screen.findByText('Scope removed.')).toBeInTheDocument();
  });

  it('explains refused and failed changes', async () => {
    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: routes({
          'GET /api/v1/access-scopes': () => jsonResponse([]),
          'POST /api/v1/access-scopes': problem(422),
        }),
      }),
    );
    const user = userEvent.setup();
    renderAt('/admin/access');
    expect(await screen.findByText('No scopes.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Add scope' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('cannot be added');
  });

  it('reports load and removal failures, and denies users without the right', async () => {
    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: routes({ 'DELETE /api/v1/access-scopes/s1': problem(500) }),
      }),
    );
    const user = userEvent.setup();
    const first = renderAt('/admin/access');
    await screen.findByText(/قسم القضايا/);
    await user.click(screen.getAllByRole('button', { name: 'Remove' })[0]);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or saved');
    first.unmount();

    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: routes({ 'GET /api/v1/access-scopes': problem(500) }),
      }),
    );
    const second = renderAt('/admin/access');
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    second.unmount();

    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: routes({ 'GET /api/v1/access-scopes': problem(403) }),
      }),
    );
    renderAt('/admin/access');
    expect(await screen.findByRole('heading', { name: /access/i })).toBeInTheDocument();
  });
});

describe('the unit of a user', () => {
  const USER = {
    id: 'usr1',
    username: 'sara',
    email: 'sara@example.invalid',
    displayName: 'Sara',
    locale: 'en',
    enabled: true,
    mustChangePassword: false,
    locked: false,
    roles: [],
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-01T00:00:00Z',
    version: 0,
    orgUnitId: 'u1',
  };
  const routes = (put: () => Response) => ({
    'GET /api/v1/users/usr1': () => jsonResponse(USER),
    'GET /api/v1/roles': () => jsonResponse([]),
    'GET /api/v1/org-units?tree=LEGAL&includeInactive=false': () => jsonResponse(UNITS),
    'PUT /api/v1/users/usr1/org-unit': put,
  });

  it('places the user in a unit, or takes them out', async () => {
    const fetchMock = fakeApi({
      routes: routes(() => new Response(null, { status: 204 })),
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderAt('/admin/users/usr1');

    const select = await screen.findByRole('combobox', {
      name: 'Unit in the legal department',
    });
    await screen.findByRole('option', { name: 'قسم القضايا' });
    await user.selectOptions(select, 'u2');
    await user.click(screen.getByRole('button', { name: 'Save unit' }));
    expect(await screen.findByText('Unit saved.')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'PUT', '/api/v1/users/usr1/org-unit')).toEqual({
      orgUnitId: 'u2',
    });
    await user.selectOptions(select, '');
    await user.click(screen.getByRole('button', { name: 'Save unit' }));
    await screen.findByText('Unit saved.');
    expect(bodyOf(fetchMock, 'PUT', '/api/v1/users/usr1/org-unit')).toEqual({
      orgUnitId: null,
    });
  });

  it('reports a failure to save, and still saves when the unit list cannot load', async () => {
    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: {
          ...routes(problem(500)),
          'GET /api/v1/org-units?tree=LEGAL&includeInactive=false': problem(500),
        },
      }),
    );
    const user = userEvent.setup();
    renderAt('/admin/users/usr1');
    await user.click(await screen.findByRole('button', { name: 'Save unit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or saved');
  });

  it('is hidden from users who cannot manage the structure', async () => {
    vi.stubGlobal(
      'fetch',
      fakeApi({
        me: {
          ...ADMIN,
          permissions: ['USER_VIEW', 'USER_MANAGE', 'ROLE_VIEW'],
        },
        routes: routes(problem(500)),
      }),
    );
    renderAt('/admin/users/usr1');
    await screen.findByRole('heading', { name: 'Sara' });
    expect(screen.queryByRole('button', { name: 'Save unit' })).not.toBeInTheDocument();
  });
});
