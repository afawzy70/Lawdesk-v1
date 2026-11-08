import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { type ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Permission } from '../../api/permissions';
import { ADMIN } from '../../test/api';
import { AuthContext } from '../auth/AuthContext';
import type { CaseParty, LegalCase } from './api';
import { aCase, jsonResponse, problem, renderCases, scriptedFetch } from './testSupport';

/** LD-045: what the case pages show of the intake data, and the assignment control for case supervisors. */

vi.setConfig({ testTimeout: 20_000 });

const ID = aCase().id;
const SARA = { id: '00000000-0000-4000-8000-0000000000a1', displayName: 'Sara Specialist' };
const OMAR = { id: '00000000-0000-4000-8000-0000000000a2', displayName: 'Omar Alternate' };

const registered = (overrides: Partial<LegalCase> = {}): LegalCase =>
  aCase({
    automatedNo: '000123',
    authorityPosition: 'PLAINTIFF',
    financialImpact: 'YES',
    financialAmount: '15250.500',
    primaryMember: SARA,
    alternateMember: OMAR,
    ...overrides,
  });

const PARTIES: CaseParty[] = [
  { partyId: 'p-auth', kind: 'ORGANIZATION', name: 'The Authority', role: 'CLIENT', addedAt: '', side: 'PLAINTIFF', authority: true },
  { partyId: 'p-1', kind: 'PERSON', name: 'Nile Trading', role: 'OPPONENT', addedAt: '', side: 'DEFENDANT', authority: false },
];

function serve(legalCase: LegalCase, parties: CaseParty[] = PARTIES, answer?: (url: URL, method: string) => Response | undefined) {
  return scriptedFetch((request) => {
    const url = new URL(request.url, 'http://localhost');
    const custom = answer?.(url, request.method);
    if (custom !== undefined) return custom;
    if (url.pathname === `/api/v1/cases/${ID}`) return jsonResponse(legalCase);
    if (url.pathname === `/api/v1/cases/${ID}/parties`) return jsonResponse(parties);
    if (url.pathname === '/api/v1/cases/members') return jsonResponse([{ ...SARA, username: 's', enabled: true }, { ...OMAR, username: 'o', enabled: true }]);
    if (url.pathname.startsWith('/api/v1/')) return jsonResponse({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    return problem(404);
  });
}

function withSession(permissions: Permission[], ui: ReactElement) {
  return (
    <AuthContext.Provider
      value={{
        state: { status: 'signedIn', me: { ...ADMIN, permissions } },
        signIn: () => Promise.resolve(true),
        signOut: () => Promise.resolve(),
        refresh: () => Promise.resolve(),
      }}
    >
      {ui}
    </AuthContext.Provider>
  );
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('the case page after the intake', () => {
  it('shows both numbers, the position, the financial impact and the members', async () => {
    serve(registered());
    renderCases(`/cases/${ID}`);
    expect(await screen.findByRole('heading', { level: 1, name: 'Case LD-2026-00001' })).toBeInTheDocument();
    const facts = screen.getAllByRole('definition').map((d) => d.textContent);
    expect(facts).toContain('000123');
    expect(facts).toContain('LD-2026-00001');
    expect(screen.getByText('Case automated number', { selector: 'dt' })).toBeInTheDocument();
    expect(screen.getByText('Internal reference', { selector: 'dt' })).toBeInTheDocument();
    expect(facts).toContain('Plaintiff');
    expect(facts).toContain('There is a financial impact: 15,250.500 KWD');
    expect(facts).toContain('Sara Specialist');
    expect(facts).toContain('Omar Alternate');
  });

  it('shows "not specified" for a case that predates the intake, and "no impact" only when it is recorded', async () => {
    serve(aCase());
    const first = renderCases(`/cases/${ID}`);
    await screen.findByRole('heading', { level: 1, name: 'Case LD-2026-00001' });
    const facts = screen.getAllByRole('definition').map((d) => d.textContent);
    expect(facts.filter((f) => f === 'Not specified').length).toBeGreaterThanOrEqual(3);
    expect(facts).toContain('Unspecified'.replace('Unspecified', 'Not specified'));
    expect(screen.queryByText('There is no financial impact')).not.toBeInTheDocument();
    first.unmount();

    serve(registered({ financialImpact: 'NO', financialAmount: null }));
    renderCases(`/cases/${ID}`);
    await screen.findByRole('heading', { level: 1, name: 'Case LD-2026-00001' });
    expect(screen.getAllByRole('definition').map((d) => d.textContent)).toContain('There is no financial impact');
  });

  it('groups the parties by side, marks the authority and does not offer to remove it', async () => {
    serve(registered());
    renderCases(`/cases/${ID}`);
    const table = await screen.findByRole('table', { name: 'Parties' });
    const [authority, defendant] = within(table).getAllByRole('row').slice(1) as [HTMLElement, HTMLElement];
    expect(within(authority).getByText('The authority')).toBeInTheDocument();
    expect(within(authority).getByText('Plaintiff')).toBeInTheDocument();
    expect(within(authority).queryByRole('button')).not.toBeInTheDocument();
    expect(within(defendant).getByText('Defendant')).toBeInTheDocument();
    expect(within(defendant).getByRole('button', { name: 'Remove Nile Trading' })).toBeInTheDocument();
    expect(screen.getByText('The authority’s position:')).toBeInTheDocument();
  });

  it('adds a party by side on an intake case, and by role on an older one', async () => {
    const user = userEvent.setup();
    const { requests } = serve(registered(), PARTIES, (url, method) =>
      method === 'POST' && url.pathname === '/api/v1/parties' ? jsonResponse({ id: 'new-party', kind: 'PERSON', name: 'X', version: 0 }, 201)
        : method === 'POST' && url.pathname === `/api/v1/cases/${ID}/parties` ? jsonResponse(PARTIES[1], 201) : undefined);
    renderCases(`/cases/${ID}`);
    const form = await screen.findByRole('form', { name: 'New party' });
    expect(within(form).queryByLabelText('Role')).not.toBeInTheDocument();
    await user.type(within(form).getByRole('textbox', { name: 'Name' }), 'X');
    await user.selectOptions(within(form).getByLabelText('Side'), 'DEFENDANT');
    await user.click(within(form).getByRole('button', { name: 'Add party' }));
    await waitFor(() => { expect(requests.some((r) => r.url === `/api/v1/cases/${ID}/parties` && r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.url === `/api/v1/cases/${ID}/parties` && r.method === 'POST')?.body)
      .toEqual({ partyId: 'new-party', role: 'CLIENT', side: 'DEFENDANT' });
  });

  it('carries the automated number into the stages without asking for it again', async () => {
    const user = userEvent.setup();
    serve(registered());
    renderCases(`/cases/${ID}`);
    expect(await screen.findByText(/The case automated number is 000123; each stage below has its own case number\./)).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Record proceeding' }));
    const form = screen.getByRole('form', { name: 'Record proceeding' });
    expect(within(form).getByText(/The case automated number 000123 applies to this stage too/)).toBeInTheDocument();
    expect(within(form).queryByLabelText(/automated number/i)).not.toBeInTheDocument();
  });
});

describe('the assignment control', () => {
  const SUPERVISOR: Permission[] = ['CASE_VIEW', 'CASE_EDIT', 'CASE_STATUS_CHANGE', 'ASSIGN_CASE', 'PARTY_MANAGE'];

  it('is shown to case supervisors only', async () => {
    serve(registered());
    // Without a session context (the plain page) and for a member without the supervisor permission: hidden.
    renderCases(`/cases/${ID}`);
    await screen.findByRole('heading', { level: 1, name: 'Case LD-2026-00001' });
    expect(screen.queryByRole('heading', { name: 'Assignment' })).not.toBeInTheDocument();
  });

  it('reassigns with the version the page read and reports a stale one', async () => {
    const user = userEvent.setup();
    const answers = [problem(409), jsonResponse(registered({ version: 1 }))];
    const { requests } = serve(registered(), PARTIES, (url, method) =>
      method === 'PUT' && url.pathname === `/api/v1/cases/${ID}/assignment` ? answers.shift() : undefined);
    const { default: Page } = { default: (await import('./CaseDetailsPage')).CaseDetailsPage };
    const { createMemoryRouter, RouterProvider } = await import('react-router');
    const { LocaleProvider } = await import('../../i18n/LocaleProvider');
    const router = createMemoryRouter(
      [{ path: '/cases/:id', element: <LocaleProvider>{withSession(SUPERVISOR, <Page />)}</LocaleProvider> }],
      { initialEntries: [`/cases/${ID}`] },
    );
    render(<RouterProvider router={router} />);

    const section = (await screen.findByRole('heading', { name: 'Assignment' })).closest('section') as HTMLElement;
    const primary = await within(section).findByRole('combobox', { name: /Specialist member/ });
    await within(primary).findByRole('option', { name: 'Omar Alternate' });
    await user.selectOptions(within(section).getByRole('combobox', { name: /Alternate member/ }), '');
    await user.click(within(section).getByRole('button', { name: 'Save the assignment' }));
    expect(await within(section).findByRole('alert')).toHaveTextContent('The case changed. Reload it and try again.');

    await user.click(within(section).getByRole('button', { name: 'Save the assignment' }));
    await screen.findByText('The assignment was saved.');
    const puts = requests.filter((r) => r.method === 'PUT');
    expect(puts.map((r) => r.body)).toEqual([
      { primaryMemberId: SARA.id, alternateMemberId: null, version: 0 },
      { primaryMemberId: SARA.id, alternateMemberId: null, version: 0 },
    ]);
  });
});

describe('editing the intake data', () => {
  it('edits the automated number and the financial impact, and clears the amount for "no impact"', async () => {
    const user = userEvent.setup();
    const { requests } = serve(registered(), PARTIES, (url, method) =>
      method === 'PUT' && url.pathname === `/api/v1/cases/${ID}` ? jsonResponse(registered({ financialImpact: 'NO', financialAmount: null, version: 1 })) : undefined);
    renderCases(`/cases/${ID}/edit`);
    const number = await screen.findByRole('textbox', { name: 'Case automated number' });
    expect(number).toHaveValue('000123');
    expect(screen.getByRole('radio', { name: 'There is a financial impact' })).toBeChecked();
    expect(screen.getByRole('textbox', { name: /Financial impact amount/ })).toHaveValue('15250.500');

    await user.clear(number);
    await user.type(number, '000124');
    await user.click(screen.getByRole('radio', { name: 'There is no financial impact' }));
    await user.click(screen.getByRole('button', { name: 'Remove the amount' }));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await screen.findByText('Changes saved.');
    const put = requests.find((r) => r.method === 'PUT');
    expect(put?.body).toMatchObject({ automatedNo: '000124', financialImpact: 'NO', version: 0 });
    expect(put?.body).not.toHaveProperty('financialAmount');
  });

  it('leaves an unspecified impact unspecified when the user does not choose', async () => {
    const user = userEvent.setup();
    const { requests } = serve(aCase(), PARTIES, (url, method) =>
      method === 'PUT' && url.pathname === `/api/v1/cases/${ID}` ? jsonResponse(aCase({ version: 1 })) : undefined);
    renderCases(`/cases/${ID}/edit`);
    const title = await screen.findByRole('textbox', { name: /Title/ });
    await user.type(title, ' II');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'PUT')).toBe(true); });
    const body = requests.find((r) => r.method === 'PUT')?.body as Record<string, unknown>;
    expect(body).not.toHaveProperty('financialImpact');
    expect(body).not.toHaveProperty('automatedNo');
  });
});
