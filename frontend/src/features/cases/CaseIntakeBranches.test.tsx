import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ADMIN } from '../../test/api';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { AuthContext } from '../auth/AuthContext';
import type { Permission } from '../../api/permissions';
import { CaseDetailsPage } from './CaseDetailsPage';
import type { CaseParty, LegalCase } from './api';
import { aCase, jsonResponse, problem, renderCases, scriptedFetch } from './testSupport';

/** LD-045: the less common paths of the intake screens (validation, refusals, failures, older cases). */

vi.setConfig({ testTimeout: 20_000 });

const ID = aCase().id;
const SARA = { id: '00000000-0000-4000-8000-0000000000a1', displayName: 'Sara Specialist' };
const intakeCase = (o: Partial<LegalCase> = {}): LegalCase =>
  aCase({ automatedNo: '77', authorityPosition: 'PLAINTIFF', financialImpact: 'NO', primaryMember: SARA, ...o });
const AUTHORITY: CaseParty = { partyId: 'a', kind: 'ORGANIZATION', name: 'The Authority', role: 'CLIENT', addedAt: '', side: 'PLAINTIFF', authority: true };

function serve(legalCase: LegalCase, parties: CaseParty[], answer: (path: string, method: string) => Response | undefined = () => undefined) {
  return scriptedFetch((request) => {
    const url = new URL(request.url, 'http://localhost');
    const custom = answer(url.pathname, request.method);
    if (custom !== undefined) return custom;
    if (url.pathname === `/api/v1/cases/${ID}`) return jsonResponse(legalCase);
    if (url.pathname === `/api/v1/cases/${ID}/parties`) return jsonResponse(parties);
    if (url.pathname === '/api/v1/parties') {
      return jsonResponse({ items: [{ id: 'reg-1', kind: 'PERSON', name: 'Registered Person', version: 0 }], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    }
    if (url.pathname.startsWith('/api/v1/')) return jsonResponse({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    return problem(404);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('the edit form', () => {
  it('refuses a financial impact without a valid amount and sends nothing', async () => {
    const user = userEvent.setup();
    const { requests } = serve(intakeCase(), [AUTHORITY]);
    renderCases(`/cases/${ID}/edit`);
    await screen.findByRole('textbox', { name: 'Case automated number' });
    await user.click(screen.getByRole('radio', { name: 'There is a financial impact' }));
    await user.type(screen.getByRole('textbox', { name: /Financial impact amount/ }), 'abc');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Enter an amount greater than zero with at most three decimals.')).toBeInTheDocument();
    expect(requests.some((r) => r.method === 'PUT')).toBe(false);
  });
});

describe('the parties of an intake case', () => {
  it('finds a registered party and adds it on a side; refuses the authority being removed', async () => {
    const user = userEvent.setup();
    const { requests } = serve(intakeCase(), [AUTHORITY], (path, method) =>
      method === 'POST' && path === `/api/v1/cases/${ID}/parties` ? jsonResponse({ ...AUTHORITY, partyId: 'reg-1', authority: false, side: 'DEFENDANT' }, 201) : undefined);
    renderCases(`/cases/${ID}`);
    const form = await screen.findByRole('search', { name: 'Find a party' });
    await user.type(within(form).getByRole('searchbox'), 'Reg');
    await user.selectOptions(within(form).getByLabelText('Side'), 'PLAINTIFF');
    await user.click(within(form).getByRole('button', { name: 'Find' }));
    await user.click(await within(form).findByRole('button', { name: /Add Registered Person/ }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST' && r.url.endsWith('/parties'))).toBe(true); });
    expect(requests.find((r) => r.method === 'POST' && r.url.endsWith('/parties'))?.body)
      .toEqual({ partyId: 'reg-1', role: 'CLIENT', side: 'PLAINTIFF' });
  });

  it('explains a refused party addition on an intake case', async () => {
    const user = userEvent.setup();
    serve(intakeCase(), [AUTHORITY], (path, method) => (method === 'POST' && path === '/api/v1/parties' ? problem(403) : undefined));
    renderCases(`/cases/${ID}`);
    const form = await screen.findByRole('form', { name: 'New party' });
    await user.type(within(form).getByRole('textbox', { name: 'Name' }), 'X');
    await user.selectOptions(within(form).getByRole('combobox', { name: 'Kind' }), 'ORGANIZATION');
    expect(within(form).getByRole('textbox', { name: 'Commercial registration no.' })).toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Add party' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('You do not have permission to do this.');
  });
});

describe('the assignment control failures', () => {
  const SUPERVISOR: Permission[] = ['CASE_VIEW', 'CASE_EDIT', 'CASE_STATUS_CHANGE'];

  async function open(answers: Response[]) {
    const user = userEvent.setup();
    serve(intakeCase(), [AUTHORITY], (path, method) => {
      if (path === '/api/v1/cases/members') {
        return jsonResponse([{ id: SARA.id, username: 's', displayName: SARA.displayName, enabled: true }]);
      }
      return method === 'PUT' && path.endsWith('/assignment') ? answers.shift() : undefined;
    });
    const router = createMemoryRouter(
      [{ path: '/cases/:id', element: (
        <LocaleProvider>
          <AuthContext.Provider value={{ state: { status: 'signedIn', me: { ...ADMIN, permissions: SUPERVISOR } },
            signIn: () => Promise.resolve(true), signOut: () => Promise.resolve(), refresh: () => Promise.resolve() }}>
            <CaseDetailsPage />
          </AuthContext.Provider>
        </LocaleProvider>) }],
      { initialEntries: [`/cases/${ID}`] },
    );
    render(<RouterProvider router={router} />);
    const section = (await screen.findByRole('heading', { name: 'Assignment' })).closest('section') as HTMLElement;
    await within(within(section).getByRole('combobox', { name: /Specialist member/ })).findByRole('option', { name: SARA.displayName });
    return { user, section };
  }

  it.each([
    [problem(403), 'You are not allowed to change the assignment.'],
    [problem(422, { rule: 'MEMBER_NOT_ELIGIBLE' }), 'The chosen member is not an active member of the legal department.'],
    [problem(500), 'The assignment could not be saved.'],
  ])('explains a refused reassignment (%#)', async (answer, message) => {
    const { user, section } = await open([answer]);
    await user.click(within(section).getByRole('button', { name: 'Save the assignment' }));
    expect(await within(section).findByRole('alert')).toHaveTextContent(message);
  });

  it('needs a specialist before it sends anything', async () => {
    const { user, section } = await open([]);
    await user.selectOptions(within(section).getByRole('combobox', { name: /Specialist member/ }), '');
    await user.click(within(section).getByRole('button', { name: 'Save the assignment' }));
    expect(await within(section).findByRole('alert')).toHaveTextContent('required');
  });
});

describe('the registration screen paths', () => {
  const COURT = { id: 'c1', code: 'K1', nameAr: 'محكمة', nameEn: 'Court One', level: 'FIRST_INSTANCE', active: true };

  function intakeServer(onMembers: () => Response) {
    scriptedFetch((request) => {
      const url = new URL(request.url, 'http://localhost');
      if (url.pathname === '/api/v1/cases/members') return onMembers();
      if (url.pathname === '/api/v1/courts') return jsonResponse({ items: [COURT], page: 0, size: 20, totalItems: 1, totalPages: 1 });
      return jsonResponse({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    });
  }

  it('says when the members cannot be listed', async () => {
    intakeServer(() => problem(500));
    renderCases('/cases/new');
    expect(await screen.findByText('The list of members could not be read.')).toBeInTheDocument();
  });

  it('validates a party before adding it, adds with Enter, removes it and ignores the same position', async () => {
    const user = userEvent.setup();
    intakeServer(() => jsonResponse([]));
    renderCases('/cases/new');
    await user.click(await screen.findByRole('radio', { name: 'Plaintiff' }));
    await user.click(screen.getByRole('radio', { name: 'Plaintiff' }));
    const group = screen.getByRole('group', { name: 'Add a party' });
    await user.click(within(group).getByRole('button', { name: 'Add to the case' }));
    expect(within(group).getByRole('alert')).toHaveTextContent('Enter the party’s name.');
    await user.type(within(group).getByLabelText('Name'), 'Opponent{Enter}');
    const defendants = screen.getByRole('region', { name: 'Defendants' });
    expect(within(defendants).getByText('Opponent')).toBeInTheDocument();
    await user.click(within(defendants).getByRole('button', { name: 'Remove Opponent' }));
    expect(within(defendants).queryByText('Opponent')).not.toBeInTheDocument();

    await user.click(within(group).getByRole('radio', { name: 'Registered party' }));
    await user.click(within(group).getByRole('button', { name: 'Add to the case' }));
    expect(within(group).getByRole('alert')).toHaveTextContent('Choose a registered party.');
  });

  it('lets the user confirm the review without swapping', async () => {
    const user = userEvent.setup();
    intakeServer(() => jsonResponse([]));
    renderCases('/cases/new');
    await user.click(await screen.findByRole('radio', { name: 'Plaintiff' }));
    const group = screen.getByRole('group', { name: 'Add a party' });
    await user.type(within(group).getByLabelText('Name'), 'Opponent');
    await user.click(within(group).getByRole('button', { name: 'Add to the case' }));
    await user.click(screen.getByRole('radio', { name: 'Defendant' }));
    await user.click(screen.getByRole('button', { name: 'I reviewed the parties' }));
    expect(screen.queryByText(/You changed the authority’s position/)).not.toBeInTheDocument();
  });
});
