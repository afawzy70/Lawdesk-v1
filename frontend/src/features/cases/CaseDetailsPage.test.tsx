import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { CaseEvent, CaseParty, LegalCase, Party } from './api';
import { aCase, jsonResponse, problem, renderCases, scriptedFetch, type Request } from './testSupport';

const CASE = aCase();
const PATH = `/cases/${CASE.id}`;
const API = `/api/v1/cases/${CASE.id}`;

const AHMED: CaseParty = {
  partyId: '11111111-1111-4111-8111-111111111111',
  kind: 'PERSON',
  name: 'Ahmed Hassan',
  role: 'CLIENT',
  addedAt: '2026-03-01T09:00:00Z',
};
const NILE: Party = {
  id: '22222222-2222-4222-8222-222222222222',
  kind: 'ORGANIZATION',
  name: 'Nile Trading Co.',
  version: 0,
};

function event(n: number, type = 'CASE_UPDATED'): CaseEvent {
  return {
    id: `e${String(n)}`,
    type,
    summary: `summary ${String(n)}`,
    occurredAt: '2026-03-02T10:00:00Z',
    actorId: '00000000-0000-4000-8000-000000000001',
  };
}

interface Backend {
  legalCase: LegalCase;
  parties: CaseParty[];
  events: CaseEvent[];
  proceedings?: unknown[];
  answer?: (request: Request) => Response | undefined;
}

/** A small in-memory backend for one case. */
function backend(state: Backend) {
  return scriptedFetch((request) => {
    const custom = state.answer?.(request);
    if (custom !== undefined) return custom;
    const url = new URL(request.url, 'http://localhost');
    if (request.method === 'GET' && url.pathname === API) return jsonResponse(state.legalCase);
    if (request.method === 'GET' && url.pathname === `${API}/parties`) return jsonResponse(state.parties);
    if (request.method === 'GET' && url.pathname === `${API}/timeline`) {
      const page = Number(url.searchParams.get('page') ?? '0');
      const items = state.events.slice(page * 20, page * 20 + 20);
      const totalPages = Math.ceil(state.events.length / 20);
      return jsonResponse({ items, page, size: 20, totalItems: state.events.length, totalPages });
    }
    if (request.method === 'GET' && [`${API}/proceedings`, `${API}/judgments`, `${API}/special-decisions`].includes(url.pathname)) {
      const items = url.pathname === `${API}/proceedings` ? (state.proceedings ?? []) : [];
      return jsonResponse({ items, page: 0, size: 100, totalItems: items.length, totalPages: 1 });
    }
    if (request.method === 'GET' && url.pathname === '/api/v1/parties') {
      return jsonResponse({ items: [NILE], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    }
    return problem(500);
  });
}

describe('case details (LD-014)', () => {
  it('shows the case, its parties and its timeline', async () => {
    backend({ legalCase: CASE, parties: [AHMED], events: [event(2, 'CASE_STATUS_CHANGED'), event(1, 'CASE_CREATED')] });
    renderCases(PATH);

    expect(await screen.findByRole('heading', { level: 1, name: 'Case LD-2026-00001' })).toBeInTheDocument();
    expect(screen.getByText('Ahmed v. Nile Trading')).toBeInTheDocument();
    expect(screen.getByText('Commercial')).toBeInTheDocument();
    expect(screen.getByText('Unpaid invoices')).toBeInTheDocument();
    expect(screen.getByText('01/03/2026')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Edit case' })).toHaveAttribute('href', `${PATH}/edit?lang=en`);
    await waitFor(() => {
      expect(document.title).toBe('Case LD-2026-00001 — LawDesk (Preview)');
    });

    const parties = await screen.findByRole('table', { name: 'Parties' });
    expect(within(parties).getByText('Ahmed Hassan')).toBeInTheDocument();
    expect(within(parties).getByText('Client')).toBeInTheDocument();
    expect(within(parties).getByText('Person')).toBeInTheDocument();

    const timeline = await screen.findByRole('list', { name: 'Timeline' });
    const entries = within(timeline).getAllByRole('listitem');
    expect(entries[0]).toHaveTextContent('Status changed');
    expect(entries[0]).toHaveTextContent('summary 2');
    expect(entries[1]).toHaveTextContent('Case created');
  });

  // Cross-phase integration: the case details carry the judicial stages of Workstream C (LD-019…LD-023).
  it('shows the judicial stages of the case', async () => {
    const first = {
      id: 'p1', caseId: CASE.id, stage: 'FIRST_INSTANCE', courtId: 'court-fi', courtCode: 'FI-1',
      courtNameAr: 'محكمة أول درجة نموذجية', courtNameEn: 'Sample First-Instance Court', courtCaseNumber: '100',
      courtCaseYear: 2023, filingDate: '2023-01-10', parentProceedingId: null, hasJudgment: false, version: 0,
      createdAt: '2023-01-10T08:00:00Z', updatedAt: '2023-01-10T08:00:00Z',
    };
    const { requests } = backend({ legalCase: CASE, parties: [], events: [], proceedings: [first] });
    renderCases(PATH);

    const stages = await screen.findByRole('region', { name: 'Judicial stages' });
    expect(await within(stages).findByText(/Sample First-Instance Court/)).toBeInTheDocument();
    expect(requests.some((r) => r.url.startsWith(`${API}/proceedings`))).toBe(true);
  });

  it('names the events other modules record, and labels unknown ones generically', async () => {
    backend({ legalCase: CASE, parties: [], events: [event(2, 'PROCEEDING_RECORDED'), event(1, 'SOMETHING_NEW')] });
    renderCases(PATH);

    const timeline = await screen.findByRole('list', { name: 'Timeline' });
    const [known, unknown] = within(timeline).getAllByRole('listitem');
    expect(known).toHaveTextContent('Proceeding recorded');
    expect(unknown).toHaveTextContent('Other event');
    expect(await screen.findByText('No parties yet.')).toBeInTheDocument();
  });

  it('loads older timeline events on request', async () => {
    const events = Array.from({ length: 25 }, (_, i) => event(25 - i));
    const { requests } = backend({ legalCase: CASE, parties: [], events });
    const user = userEvent.setup();
    renderCases(PATH);

    const timeline = await screen.findByRole('list', { name: 'Timeline' });
    expect(within(timeline).getAllByRole('listitem')).toHaveLength(20);
    await user.click(screen.getByRole('button', { name: 'Show older events' }));

    await waitFor(() => {
      expect(within(screen.getByRole('list', { name: 'Timeline' })).getAllByRole('listitem')).toHaveLength(25);
    });
    expect(requests.some((r) => r.url === `${API}/timeline?page=1`)).toBe(true);
    expect(screen.queryByRole('button', { name: 'Show older events' })).not.toBeInTheDocument();
  });

  it('confirms a case that was just created', async () => {
    backend({
      legalCase: CASE,
      parties: [],
      events: [],
      answer: (r) => (r.method === 'POST' ? jsonResponse(CASE, 201) : undefined),
    });
    const user = userEvent.setup();
    const { url } = renderCases('/cases/new');
    await user.type(screen.getByRole('textbox', { name: 'Title' }), 'Ahmed v. Nile Trading');
    await user.click(screen.getByRole('button', { name: 'Create case' }));

    expect(await screen.findByText('Case LD-2026-00001 created.')).toHaveAttribute('role', 'status');
    expect(url()).toBe(`${PATH}?lang=en`);
  });

  it('shows the not-found page for an address that cannot be a case', () => {
    const { requests } = backend({ legalCase: CASE, parties: [], events: [] });
    renderCases('/cases/does-not-exist');

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
    expect(requests).toHaveLength(0);
  });

  it.each([
    [404, 'This case does not exist.'],
    [500, 'The case could not be loaded. Try again later.'],
  ])('explains a %i answer', async (status, message) => {
    scriptedFetch(() => problem(status));
    renderCases(PATH);
    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });

  it('shows the shared access-denied page without the permission to view', async () => {
    scriptedFetch(() => problem(403));
    renderCases(PATH);
    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
  });
});

describe('case status (LD-017)', () => {
  it('offers only the allowed next statuses and records the change', async () => {
    const state: Backend = { legalCase: CASE, parties: [], events: [] };
    state.answer = (request) => {
      if (request.method !== 'POST') return undefined;
      state.legalCase = { ...CASE, status: 'ACTIVE', version: 1 };
      state.events = [event(1, 'CASE_STATUS_CHANGED')];
      return jsonResponse(state.legalCase);
    };
    const { requests } = backend(state);
    const user = userEvent.setup();
    renderCases(PATH);

    const select = await screen.findByRole('combobox', { name: 'New status' });
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Choose a status',
      'Active',
      'Closed',
    ]);
    await user.selectOptions(select, 'ACTIVE');
    await user.type(screen.getByRole('textbox', { name: 'Reason' }), 'First hearing set');
    await user.click(screen.getByRole('button', { name: 'Change status' }));

    expect(await screen.findByText('Status changed.')).toHaveAttribute('role', 'status');
    expect(requests.find((r) => r.method === 'POST')).toEqual({
      method: 'POST',
      url: `${API}/status`,
      body: { status: 'ACTIVE', reason: 'First hearing set', version: 0 },
    });
    await waitFor(() => {
      expect(within(screen.getByRole('list', { name: 'Timeline' })).getAllByRole('listitem')).toHaveLength(1);
    });
    expect(within(screen.getByRole('combobox', { name: 'New status' })).getAllByRole('option')).toHaveLength(3);
  });

  it.each([
    [problem(422, { rule: 'REASON_REQUIRED' }), 'A reason is required to reopen a closed case.'],
    [problem(422, { rule: 'STATUS_TRANSITION_NOT_ALLOWED' }), 'This status change is not allowed.'],
    [problem(409), 'Someone else changed this case. Reload it to see the latest version, then apply your changes again.'],
    [problem(403), 'You do not have permission to do this.'],
    [problem(400), 'Choose the new status.'],
  ])('explains a refused change (%#)', async (answer, message) => {
    backend({ legalCase: CASE, parties: [], events: [], answer: (r) => (r.method === 'POST' ? answer : undefined) });
    const user = userEvent.setup();
    renderCases(PATH);

    await user.selectOptions(await screen.findByRole('combobox', { name: 'New status' }), 'CLOSED');
    await user.click(screen.getByRole('button', { name: 'Change status' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });

  it('reloads the case after a conflict', async () => {
    const state: Backend = { legalCase: CASE, parties: [], events: [] };
    state.answer = (r) => (r.method === 'POST' ? problem(409) : undefined);
    const { requests } = backend(state);
    const user = userEvent.setup();
    renderCases(PATH);
    await user.selectOptions(await screen.findByRole('combobox', { name: 'New status' }), 'ACTIVE');
    await user.click(screen.getByRole('button', { name: 'Change status' }));

    state.legalCase = { ...CASE, status: 'ACTIVE', version: 3 };
    await user.click(await screen.findByRole('button', { name: 'Reload case' }));

    // ACTIVE offers SUSPENDED and CLOSED, so the options show that the reloaded case is the current one.
    await waitFor(() => {
      expect(
        within(screen.getByRole('combobox', { name: 'New status' }))
          .getAllByRole('option')
          .map((o) => o.textContent),
      ).toEqual(['Choose a status', 'Suspended', 'Closed']);
    });
    expect(requests.filter((r) => r.method === 'GET' && r.url === API)).toHaveLength(2);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows an archived case as final, without status or party changes', async () => {
    backend({ legalCase: { ...CASE, status: 'ARCHIVED' }, parties: [AHMED], events: [] });
    renderCases(PATH);

    expect(await screen.findByText('This case is archived and can no longer change.')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'New status' })).not.toBeInTheDocument();
    await screen.findByRole('table', { name: 'Parties' });
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add party' })).not.toBeInTheDocument();
  });
});

describe('case parties (LD-016)', () => {
  it('creates a new party and adds it with its role', async () => {
    const state: Backend = { legalCase: CASE, parties: [], events: [] };
    state.answer = (request) => {
      if (request.method === 'POST' && request.url === '/api/v1/parties') {
        return jsonResponse({ ...NILE, id: AHMED.partyId, kind: 'PERSON', name: 'Ahmed Hassan' }, 201);
      }
      if (request.method === 'POST' && request.url === `${API}/parties`) {
        state.parties = [AHMED];
        return jsonResponse(AHMED, 201);
      }
      return undefined;
    };
    const { requests } = backend(state);
    const user = userEvent.setup();
    renderCases(PATH);

    const form = await screen.findByRole('form', { name: 'New party' });
    await user.selectOptions(within(form).getByRole('combobox', { name: 'Kind' }), 'PERSON');
    await user.type(within(form).getByRole('textbox', { name: 'Name' }), 'Ahmed Hassan');
    await user.type(within(form).getByRole('textbox', { name: 'National ID' }), '29001011234567');
    await user.selectOptions(within(form).getByRole('combobox', { name: 'Role' }), 'CLIENT');
    await user.click(within(form).getByRole('button', { name: 'Add party' }));

    expect(await within(await screen.findByRole('table', { name: 'Parties' })).findByText('Ahmed Hassan'))
      .toBeInTheDocument();
    const posts = requests.filter((r) => r.method === 'POST');
    expect(posts[0]).toEqual({
      method: 'POST',
      url: '/api/v1/parties',
      body: { kind: 'PERSON', name: 'Ahmed Hassan', nationalId: '29001011234567', registrationNo: null },
    });
    expect(posts[1]?.body).toEqual({ partyId: AHMED.partyId, role: 'CLIENT' });
  });

  it('asks for the registration number of an organisation instead of a national ID', async () => {
    backend({ legalCase: CASE, parties: [], events: [] });
    const user = userEvent.setup();
    renderCases(PATH);

    const form = await screen.findByRole('form', { name: 'New party' });
    await user.selectOptions(within(form).getByRole('combobox', { name: 'Kind' }), 'ORGANIZATION');

    expect(within(form).getByRole('textbox', { name: 'Commercial registration no.' })).toBeInTheDocument();
    expect(within(form).queryByRole('textbox', { name: 'National ID' })).not.toBeInTheDocument();
  });

  it('adds an existing party found by search', async () => {
    const state: Backend = { legalCase: CASE, parties: [], events: [] };
    state.answer = (request) => {
      if (request.method !== 'POST') return undefined;
      state.parties = [{ ...AHMED, partyId: NILE.id, name: NILE.name, kind: 'ORGANIZATION', role: 'OPPONENT' }];
      return jsonResponse(state.parties[0], 201);
    };
    const { requests } = backend(state);
    const user = userEvent.setup();
    renderCases(PATH);

    const search = await screen.findByRole('search', { name: 'Find a party' });
    await user.type(within(search).getByRole('searchbox', { name: 'Name or ID' }), 'nile');
    await user.selectOptions(within(search).getByRole('combobox', { name: 'Role' }), 'OPPONENT');
    await user.click(within(search).getByRole('button', { name: 'Find' }));
    await user.click(await screen.findByRole('button', { name: 'Add Nile Trading Co.' }));

    await waitFor(() => {
      expect(within(screen.getByRole('table', { name: 'Parties' })).getByText('Opponent')).toBeInTheDocument();
    });
    expect(requests.some((r) => r.url === '/api/v1/parties?q=nile')).toBe(true);
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ partyId: NILE.id, role: 'OPPONENT' });
  });

  it('removes a party from the case', async () => {
    const state: Backend = { legalCase: CASE, parties: [AHMED], events: [] };
    state.answer = (request) => {
      if (request.method !== 'DELETE') return undefined;
      state.parties = [];
      return new Response(null, { status: 204 });
    };
    const { requests } = backend(state);
    const user = userEvent.setup();
    renderCases(PATH);

    await user.click(await screen.findByRole('button', { name: 'Remove Ahmed Hassan' }));

    expect(await screen.findByText('No parties yet.')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'DELETE')?.url).toBe(`${API}/parties/${AHMED.partyId}`);
  });

  it.each([
    [problem(409), 'This party is already on the case, or its national ID belongs to another party.'],
    [problem(400, { errors: [{ field: 'nationalId', code: 'Pattern' }] }), 'Please correct the party details.'],
    [problem(422, { rule: 'PARTY_KIND_MISMATCH' }), 'Please correct the party details.'],
    [problem(403), 'You do not have permission to do this.'],
    [problem(500), 'The party could not be saved. Try again later.'],
  ])('explains a refused party change (%#)', async (answer, message) => {
    backend({ legalCase: CASE, parties: [], events: [], answer: (r) => (r.method === 'POST' ? answer : undefined) });
    const user = userEvent.setup();
    renderCases(PATH);

    const form = await screen.findByRole('form', { name: 'New party' });
    await user.type(within(form).getByRole('textbox', { name: 'Name' }), 'X');
    await user.click(within(form).getByRole('button', { name: 'Add party' }));

    expect(await within(form).findByRole('alert')).toHaveTextContent(message);
  });

  it('renders in Arabic', async () => {
    backend({ legalCase: CASE, parties: [AHMED], events: [event(1, 'PARTY_ADDED')] });
    renderCases(`${PATH}?lang=ar`);

    expect(await screen.findByRole('heading', { level: 1, name: 'القضية LD-2026-00001' })).toBeInTheDocument();
    expect(await screen.findByRole('table', { name: 'أطراف القضية' })).toHaveTextContent('موكل');
    expect(await screen.findByRole('list', { name: 'السجل الزمني' })).toHaveTextContent('إضافة طرف');
    expect(screen.getByRole('button', { name: 'تغيير الحالة' })).toBeInTheDocument();
  });
});
