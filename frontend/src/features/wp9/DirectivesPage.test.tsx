import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { DirectivesPage } from './DirectivesPage';
import { directive, DIRECTIVE_KINDS, mount, page, problem, serve, type Request } from './testSupport';

/** LD-057, requirements section 25: the register of administrative decisions and assignments. */

const CASE_ROW = { id: 'c0000000-0000-4000-8000-000000000001', referenceNo: 'LD-2026-00001', title: 'Ahmed v. Nile Trading', caseType: 'CIVIL', status: 'OPEN', openedOn: '2026-01-01', updatedAt: '2026-01-01T00:00:00Z' };
const PEOPLE = [{ id: 'u1', username: 'mona', displayName: 'Mona Saleh', enabled: true }, { id: 'u2', username: 'gone', displayName: 'Left The Team', enabled: false }];

function backend(items: unknown[], onOther?: (r: Request) => Response | undefined) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === '/api/v1/directives') return page(items, { page: Number(r.query.get('page') ?? '0'), totalPages: 2, totalItems: 21 });
    if (r.method === 'GET' && r.path === '/api/v1/lists/DIRECTIVE_KIND') return jsonResponse(DIRECTIVE_KINDS);
    if (r.method === 'GET' && r.path === '/api/v1/users/directory') return jsonResponse(PEOPLE);
    if (r.method === 'GET' && r.path === '/api/v1/cases') return page([CASE_ROW]);
    return onOther?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('the directives register', () => {
  it('lists each directive with its number, status, case, source, addressee and due date', async () => {
    backend([directive({ addresseeUserId: 'other' }), directive({ id: 'd2', addresseeUserId: 'other', directiveNo: 'DIR-2026-00002', kind: null, overdue: true, dueDate: null, status: 'IN_PROGRESS', details: 'Within a week', statusNote: 'Started', sourceReferenceNo: '12/2026' })]);
    mount(<DirectivesPage />, ['CASE_VIEW']);
    const first = await screen.findByRole('listitem', { name: 'DIR-2026-00001' });
    expect(first).toHaveTextContent('Prepare the reply memorandum');
    expect(first).toHaveTextContent('New');
    expect(first).toHaveTextContent('LD-2026-00001 Ahmed v. Nile Trading');
    expect(first).toHaveTextContent('Prepare a memorandum');
    expect(first).toHaveTextContent('Department manager');
    expect(first).toHaveTextContent('Mona Saleh');
    expect(first).toHaveTextContent('10/11/2026');
    expect(within(first).getByRole('link')).toHaveAttribute('href', expect.stringContaining('/cases/c0000000-0000-4000-8000-000000000001'));
    const second = screen.getByRole('listitem', { name: 'DIR-2026-00002' });
    expect(second).toHaveTextContent('Overdue');
    expect(second).toHaveTextContent('In progress');
    expect(second).toHaveTextContent('Not specified');
    expect(second).toHaveTextContent('(12/2026)');
    expect(second).toHaveTextContent('Within a week');
    expect(second).toHaveTextContent('Started');
    expect(screen.getByText('21 directive(s)')).toBeInTheDocument();
    // A reader may not issue, and is not the addressee, so there is no form and no status control.
    expect(screen.queryByRole('form', { name: 'Record a directive' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: /Change the status/ })).not.toBeInTheDocument();
  });

  it('applies the filters to the query', async () => {
    const user = userEvent.setup();
    const { requests } = backend([directive()]);
    mount(<DirectivesPage />, ['CASE_VIEW']);
    await screen.findByRole('listitem', { name: 'DIR-2026-00001' });
    const filters = screen.getByRole('search', { name: 'Filter the register' });
    await user.type(within(filters).getByLabelText('Search the register'), 'memo');
    await user.selectOptions(within(filters).getByLabelText('Execution status'), 'In progress');
    await user.selectOptions(await within(filters).findByLabelText('Kind'), 'Prepare a memorandum');
    await user.click(within(filters).getByLabelText('Overdue only'));
    await user.click(within(filters).getByLabelText('Addressed to me'));
    await user.click(within(filters).getByRole('button', { name: 'Search' }));
    await waitFor(() => {
      const last = requests.filter((r) => r.path === '/api/v1/directives').at(-1);
      expect(Object.fromEntries(last?.query ?? [])).toEqual({ page: '0', size: '20', q: 'memo', status: 'IN_PROGRESS', kind: 'PREPARE_MEMO', overdue: 'true', addressee: 'me' });
    });
  }, 15000);

  it('pages through the register', async () => {
    const user = userEvent.setup();
    const { requests } = backend([directive({ addresseeUserId: 'other' })]);
    mount(<DirectivesPage />, ['CASE_VIEW']);
    await screen.findByRole('listitem', { name: 'DIR-2026-00001' });
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => { expect(requests.filter((r) => r.path === '/api/v1/directives').at(-1)?.query.get('page')).toBe('1'); });
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Previous' }));
    await waitFor(() => { expect(requests.filter((r) => r.path === '/api/v1/directives').at(-1)?.query.get('page')).toBe('0'); });
  });

  it('says nothing matches, reports a failed load, and shows no register to someone who may not open it', async () => {
    backend([]);
    const first = mount(<DirectivesPage />, ['CASE_VIEW']);
    expect(await screen.findByText('No directive matches.')).toBeInTheDocument();
    first.unmount();
    serve((r) => (r.path === '/api/v1/directives' ? problem(500) : undefined));
    const second = mount(<DirectivesPage />, ['CASE_VIEW']);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    second.unmount();
    serve((r) => (r.path === '/api/v1/directives' ? problem(403) : undefined));
    mount(<DirectivesPage />, []);
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });

  it('records a directive for a case chosen by search and an addressee from the directory', async () => {
    const user = userEvent.setup();
    const { requests } = backend([], (r) => (r.method === 'POST' && r.path === '/api/v1/directives' ? jsonResponse(directive(), 201) : undefined));
    mount(<DirectivesPage />, ['CASE_VIEW', 'DIRECTIVE_MANAGE']);
    const form = await screen.findByRole('form', { name: 'Record a directive' });
    await user.click(within(form).getByRole('button', { name: 'Record a directive' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Choose the case');
    await user.selectOptions(await within(form).findByLabelText('Case'), 'LD-2026-00001 — Ahmed v. Nile Trading');
    await user.type(within(form).getByLabelText('What is asked'), 'Prepare the reply');
    await user.type(within(form).getByLabelText('Source (who issued it)'), 'Department manager');
    await user.click(within(form).getByRole('button', { name: 'Record a directive' }));
    expect(await within(form).findByRole('alert')).toBeInTheDocument();
    const addressee = within(form).getByLabelText('Addressee');
    expect(within(addressee).queryByRole('option', { name: 'Left The Team' })).not.toBeInTheDocument();
    await user.selectOptions(addressee, 'Mona Saleh');
    await user.selectOptions(within(form).getByLabelText('Kind'), 'Prepare a memorandum');
    await user.type(within(form).getByLabelText('Due date'), '2026-12-01');
    await user.click(within(form).getByRole('button', { name: 'Record a directive' }));
    expect(await screen.findByText('The directive was recorded.')).toBeInTheDocument();
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.body).toMatchObject({ caseId: CASE_ROW.id, kind: 'PREPARE_MEMO', title: 'Prepare the reply', source: 'Department manager', addresseeUserId: 'u1', addresseeText: null, dueDate: '2026-12-01' });
    expect(within(form).getByLabelText('What is asked')).toHaveValue('');
  });

  it('refuses a due date before the directive date, and an addressee typed as text is enough', async () => {
    const user = userEvent.setup();
    const { requests } = backend([], (r) => (r.method === 'POST' ? jsonResponse(directive(), 201) : undefined));
    mount(<DirectivesPage />, ['CASE_VIEW', 'DIRECTIVE_MANAGE']);
    const form = await screen.findByRole('form', { name: 'Record a directive' });
    await user.selectOptions(await within(form).findByLabelText('Case'), 'LD-2026-00001 — Ahmed v. Nile Trading');
    await user.type(within(form).getByLabelText('What is asked'), 'Write to the department');
    await user.type(within(form).getByLabelText('Source (who issued it)'), 'Section head');
    await user.type(within(form).getByLabelText('Addressee outside the system'), 'Administrative affairs');
    await user.clear(within(form).getByLabelText('Date of the directive'));
    await user.type(within(form).getByLabelText('Date of the directive'), '2026-11-10');
    await user.type(within(form).getByLabelText('Due date'), '2026-11-01');
    await user.click(within(form).getByRole('button', { name: 'Record a directive' }));
    expect(await within(form).findByRole('alert')).toBeInTheDocument();
    expect(requests.some((r) => r.method === 'POST')).toBe(false);
    await user.clear(within(form).getByLabelText('Due date'));
    await user.click(within(form).getByRole('button', { name: 'Record a directive' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({ addresseeUserId: null, addresseeText: 'Administrative affairs', dueDate: null });
  });

  it('keeps the form and says why when a rule refuses the directive', async () => {
    const user = userEvent.setup();
    backend([], (r) => (r.method === 'POST' ? problem(422, { rule: 'DIRECTIVE_ADDRESSEE_UNKNOWN' }) : undefined));
    mount(<DirectivesPage />, ['CASE_VIEW', 'DIRECTIVE_MANAGE']);
    const form = await screen.findByRole('form', { name: 'Record a directive' });
    await user.selectOptions(await within(form).findByLabelText('Case'), 'LD-2026-00001 — Ahmed v. Nile Trading');
    await user.type(within(form).getByLabelText('What is asked'), 'x');
    await user.type(within(form).getByLabelText('Source (who issued it)'), 'y');
    await user.type(within(form).getByLabelText('Addressee outside the system'), 'z');
    await user.click(within(form).getByRole('button', { name: 'Record a directive' }));
    expect(await screen.findByText('The addressee is not an enabled user.')).toBeInTheDocument();
    expect(within(form).getByLabelText('What is asked')).toHaveValue('x');
  });

  it('lets a manager and the addressee move the status, and nobody else', async () => {
    const user = userEvent.setup();
    backend([directive({ addresseeUserId: 'someone-else' })]);
    const first = mount(<DirectivesPage />, ['CASE_VIEW']);
    await screen.findByRole('listitem', { name: 'DIR-2026-00001' });
    expect(screen.queryByRole('form', { name: /Change the status/ })).not.toBeInTheDocument();
    first.unmount();
    // The addressee of the fixture is the signed-in user (id u1).
    backend([directive()], (r) => (r.method === 'POST' ? jsonResponse(directive({ status: 'DONE' })) : undefined));
    mount(<DirectivesPage />, ['CASE_VIEW']);
    const form = await screen.findByRole('form', { name: 'Change the status of DIR-2026-00001' });
    expect(within(form).queryByRole('option', { name: 'New' })).not.toBeInTheDocument();
    await user.selectOptions(within(form).getByLabelText('New status'), 'Done');
    await user.type(within(form).getByLabelText('Note'), 'Filed');
    await user.click(within(form).getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText('The directive status was changed.')).toBeInTheDocument();
  });

  it('sends the chosen status with the note and version, and reports a refusal', async () => {
    const user = userEvent.setup();
    const { requests } = backend([directive()], (r) => (r.method === 'POST' ? problem(409) : undefined));
    mount(<DirectivesPage />, ['CASE_VIEW', 'DIRECTIVE_MANAGE']);
    const form = await screen.findByRole('form', { name: 'Change the status of DIR-2026-00001' });
    await user.click(within(form).getByRole('button', { name: 'Apply' }));
    expect(requests.some((r) => r.method === 'POST')).toBe(false);
    await user.selectOptions(within(form).getByLabelText('New status'), 'Cancelled');
    await user.click(within(form).getByRole('button', { name: 'Apply' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed');
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ status: 'CANCELLED', note: null, version: 0 });
  });
});
