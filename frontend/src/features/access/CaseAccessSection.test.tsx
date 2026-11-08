import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { ADMIN, fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';
import { AuthContext } from '../auth/AuthContext';
import type { Me } from '../auth/me';
import { CaseAccessSection } from './CaseAccessSection';

/** LD-050: confidentiality and explicit grants of one case, for holders of CASE_GRANT_MANAGE. */
const CASE = 'c1';
const GRANTS = [
  {
    id: 'g1',
    caseId: CASE,
    userId: 'u1',
    userName: 'Sara',
    level: 'VIEW',
    reason: 'second opinion',
    grantedAt: '2026-11-08T08:00:00Z',
    expiresAt: null,
    revokedAt: null,
  },
  {
    id: 'g2',
    caseId: CASE,
    userId: 'u2',
    userName: 'Omar',
    level: 'CONTRIBUTE',
    reason: 'cover',
    grantedAt: '2026-11-07T08:00:00Z',
    expiresAt: null,
    revokedAt: '2026-11-08T09:00:00Z',
  },
];
const DIRECTORY = [
  { id: 'u1', username: 'sara', displayName: 'Sara', enabled: true },
  { id: 'u3', username: 'hind', displayName: 'Hind', enabled: true },
  { id: 'u4', username: 'gone', displayName: 'Gone', enabled: false },
];
const problem = (status: number) => () =>
  new Response(JSON.stringify({ status }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });

function mount(me: Me | null, routes: Record<string, () => Response>, onChanged = vi.fn()) {
  const fetchMock = fakeApi({
    routes: {
      [`GET /api/v1/cases/${CASE}/grants`]: () => jsonResponse(GRANTS),
      'GET /api/v1/users/directory': () => jsonResponse(DIRECTORY),
      ...routes,
    },
  });
  vi.stubGlobal('fetch', fetchMock);
  render(
    <MemoryRouter>
      <LocaleProvider>
        <AuthContext.Provider
          value={{
            state: me === null ? { status: 'signedOut', expired: false } : { status: 'signedIn', me },
            signIn: () => Promise.resolve(true),
            signOut: () => Promise.resolve(),
            refresh: () => Promise.resolve(),
          }}
        >
          <CaseAccessSection caseId={CASE} confidentiality="NORMAL" version={3} onChanged={onChanged} />
        </AuthContext.Provider>
      </LocaleProvider>
    </MemoryRouter>,
  );
  return { fetchMock, onChanged };
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

describe('case access section', () => {
  it('is hidden without the right to manage access', () => {
    mount({ ...ADMIN, permissions: ['CASE_VIEW'] }, {});
    expect(screen.queryByRole('heading', { name: 'Access to this case' })).not.toBeInTheDocument();
  });

  it('is hidden without a session', () => {
    mount(null, {});
    expect(screen.queryByRole('heading', { name: 'Access to this case' })).not.toBeInTheDocument();
  });

  it('lists grants, withdrawn ones marked, and grants access to an enabled user', async () => {
    const { fetchMock } = mount(ADMIN, {
      [`POST /api/v1/cases/${CASE}/grants`]: () => jsonResponse(GRANTS[0], 201),
    });
    const user = userEvent.setup();

    const list = await screen.findByRole('list');
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(list).toHaveTextContent('withdrawn');
    await screen.findByRole('option', { name: 'Hind' });
    expect(screen.queryByRole('option', { name: 'Gone' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Grant access' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter the reason');

    await user.selectOptions(screen.getByLabelText('User'), 'u3');
    await user.selectOptions(screen.getByLabelText('Level'), 'CONTRIBUTE');
    await user.type(
      within(screen.getByRole('form', { name: 'Grant access' })).getByLabelText('Reason'),
      'covering the hearing',
    );
    await user.click(screen.getByRole('button', { name: 'Grant access' }));
    expect(await screen.findByText('Access granted.')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'POST', `/api/v1/cases/${CASE}/grants`)).toEqual({
      userId: 'u3',
      level: 'CONTRIBUTE',
      reason: 'covering the hearing',
    });
  });

  it('withdraws a grant and explains a refused one', async () => {
    mount(ADMIN, {
      [`DELETE /api/v1/cases/${CASE}/grants/g1`]: () => new Response(null, { status: 204 }),
      [`POST /api/v1/cases/${CASE}/grants`]: problem(422),
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Withdraw' }));
    expect(await screen.findByText('Access withdrawn.')).toBeInTheDocument();

    await screen.findByRole('option', { name: 'Hind' });
    await user.selectOptions(screen.getByLabelText('User'), 'u3');
    await user.type(within(screen.getByRole('form', { name: 'Grant access' })).getByLabelText('Reason'), 'me');
    await user.click(screen.getByRole('button', { name: 'Grant access' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('refused');
  });

  it('reports a failed withdrawal and a failed load', async () => {
    mount(ADMIN, { [`DELETE /api/v1/cases/${CASE}/grants/g1`]: problem(500) });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Withdraw' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or saved');
  });

  it('shows the failure when the grants cannot be loaded, and an empty list', async () => {
    mount(ADMIN, { [`GET /api/v1/cases/${CASE}/grants`]: problem(500) });
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or saved');
  });

  it('says so when there are no grants', async () => {
    mount(ADMIN, {
      [`GET /api/v1/cases/${CASE}/grants`]: () => jsonResponse([]),
    });
    expect(await screen.findByText('No grants.')).toBeInTheDocument();
  });

  it('saves the confidentiality with a reason and the version it read', async () => {
    const { fetchMock, onChanged } = mount(ADMIN, {
      [`PUT /api/v1/cases/${CASE}/confidentiality`]: () => new Response(null, { status: 204 }),
    });
    const user = userEvent.setup();
    await screen.findByRole('list');

    await user.selectOptions(screen.getByRole('combobox', { name: 'Confidentiality' }), 'RESTRICTED');
    await user.click(screen.getByRole('button', { name: 'Save confidentiality' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter the reason');

    await user.type(
      within(screen.getByRole('form', { name: 'Confidentiality' })).getByLabelText('Reason'),
      'sensitive file',
    );
    await user.click(screen.getByRole('button', { name: 'Save confidentiality' }));
    expect(await screen.findByText('Confidentiality saved.')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'PUT', `/api/v1/cases/${CASE}/confidentiality`)).toEqual({
      level: 'RESTRICTED',
      reason: 'sensitive file',
      version: 3,
    });
    expect(onChanged).toHaveBeenCalledTimes(1);
  });

  it('reports a failed save of the confidentiality', async () => {
    mount(ADMIN, {
      [`PUT /api/v1/cases/${CASE}/confidentiality`]: problem(409),
    });
    const user = userEvent.setup();
    await screen.findByRole('list');
    await user.type(within(screen.getByRole('form', { name: 'Confidentiality' })).getByLabelText('Reason'), 'x');
    await user.click(screen.getByRole('button', { name: 'Save confidentiality' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or saved');
  });
});
