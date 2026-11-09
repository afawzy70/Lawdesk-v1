import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { CASE, page, problem, renderOperations, scriptedFetch, task, type Request } from '../testSupport';
import type { TaskHistoryEntry } from './tasksApi';

/** LD-056, requirements section 14: instructions, the reason for delay, reassignment and the history of a task. */

const USERS = [
  { id: 'u1', username: 'mona', displayName: 'Mona Saleh', enabled: true },
  { id: 'u2', username: 'omar', displayName: 'Omar Adel', enabled: true },
];

const line = (overrides: Partial<TaskHistoryEntry>): TaskHistoryEntry => ({
  id: 'h', changedAt: '2027-03-01T08:00:00Z', actorId: 'u9', actorName: 'Nada Hassan', action: 'CREATED', field: null,
  oldValue: null, newValue: null, reason: null, ...overrides,
});

const HISTORY: TaskHistoryEntry[] = [
  line({ id: 'h1' }),
  line({ id: 'h2', action: 'UPDATED', field: 'due_date', oldValue: '2027-03-04', newValue: '2027-03-09' }),
  line({ id: 'h3', action: 'STATUS', field: 'status', oldValue: 'NEW', newValue: 'WAITING_DOCUMENTS' }),
  line({ id: 'h4', action: 'SYSTEM_LATE', actorId: null, actorName: null, field: 'status', oldValue: 'WAITING_DOCUMENTS', newValue: 'LATE' }),
  line({ id: 'h5', action: 'SYSTEM_RESTORED', field: 'status', oldValue: 'LATE', newValue: 'WAITING_DOCUMENTS' }),
  line({ id: 'h6', action: 'REASSIGNED', field: 'assignee', oldValue: 'u1', newValue: 'u2', reason: 'Workload' }),
  line({ id: 'h7', action: 'REASSIGNED', field: 'assignee', oldValue: null, newValue: 'u-gone' }),
  line({ id: 'h8', action: 'DELAY_REASON', field: 'delay_reason', newValue: 'The registry is closed' }),
];

interface Backend {
  history?: Response;
  onWrite?: (request: Request) => Response | undefined;
  tasks?: ReturnType<typeof task>[];
}

function serve(backend: Backend = {}) {
  return scriptedFetch((r) => {
    if (r.method !== 'GET') return backend.onWrite?.(r);
    if (r.path === '/api/v1/tasks/reminders') return jsonResponse({ today: '2027-03-02', dueSoonDays: 3, overdue: 0, dueSoon: 0 });
    if (r.path === '/api/v1/tasks') return page(backend.tasks ?? [task({ instructions: 'Ask the registry first', startsOn: '2027-03-01' })]);
    if (r.path === '/api/v1/users/directory') return jsonResponse(USERS);
    if (r.path.endsWith('/history')) return (backend.history ?? jsonResponse(HISTORY)).clone();
    if (r.path === `/api/v1/cases/${CASE}/hearings` || r.path === `/api/v1/cases/${CASE}/proceedings`) return page([]);
    return undefined;
  });
}

const open = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(await screen.findByRole('button', { name: 'Details: File the appeal memorandum' }));
  return screen.findByRole('region', { name: 'Task details: File the appeal memorandum' });
};

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('task details', () => {
  it('shows the instructions, the start date, the delay reason and the history of who changed what', async () => {
    const user = userEvent.setup();
    serve({ tasks: [task({ instructions: 'Ask the registry first', startsOn: '2027-03-01', delayReason: 'Waiting for the court', delayReasonAt: '2027-03-02T09:00:00Z' })] });
    renderOperations('/tasks');
    const details = await open(user);
    expect(within(details).getByText('Ask the registry first')).toBeInTheDocument();
    expect(details).toHaveTextContent('1 Mar 2027');
    expect(details).toHaveTextContent('Waiting for the court');
    const entries = await within(details).findAllByRole('listitem');
    const text = entries.map((e) => e.textContent);
    expect(text[0]).toContain('Nada Hassan created the task.');
    expect(text[1]).toContain('Nada Hassan changed the due date.');
    expect(text[2]).toContain('moved the task from New to Waiting for documents.');
    expect(text[3]).toContain('The system marked the task late (it was Waiting for documents).');
    expect(text[4]).toContain('returned from Late to Waiting for documents');
    expect(text[5]).toContain('reassigned the task from Mona Saleh to Omar Adel.');
    expect(text[5]).toContain('Workload');
    expect(text[6]).toContain('from nobody to u-gone');
    expect(text[7]).toContain('recorded the reason for delay: The registry is closed');
  });

  it('shows what is not recorded, and a history that cannot be loaded', async () => {
    const user = userEvent.setup();
    serve({ tasks: [task()], history: problem(500) });
    renderOperations('/tasks');
    const details = await open(user);
    expect(details).toHaveTextContent('No instructions recorded.');
    expect(details).toHaveTextContent('Not recorded.');
    expect(await within(details).findByText('The history could not be loaded.')).toBeInTheDocument();
    await user.click(within(details).getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('region', { name: /Task details/ })).not.toBeInTheDocument();
  });

  it('records the reason for delay, then shows it', async () => {
    const user = userEvent.setup();
    const { requests } = serve({ onWrite: () => jsonResponse(task({ version: 1, delayReason: 'Registry closed', delayReasonAt: '2027-03-02T09:00:00Z' })) });
    renderOperations('/tasks');
    const details = await open(user);
    const save = within(details).getByRole('button', { name: 'Save the reason' });
    expect(save).toBeDisabled();
    await user.type(within(details).getByLabelText('Record the reason for delay'), '  Registry closed ');
    await user.click(save);
    expect(await within(details).findByRole('status')).toHaveTextContent('The task was saved.');
    expect(requests.find((r) => r.method === 'POST')).toMatchObject({ path: '/api/v1/tasks/t1/delay-reason', body: { reason: 'Registry closed', version: 0 } });
    await waitFor(() => { expect(within(details).getByLabelText('Record the reason for delay')).toHaveValue(''); });
    expect(await within(details).findByText(/Registry closed \(/)).toBeInTheDocument();
  });

  it('reassigns to another user with a reason, offering everyone but the current assignee', async () => {
    const user = userEvent.setup();
    const { requests } = serve({ onWrite: () => jsonResponse(task({ version: 1, assigneeId: 'u2', assigneeName: 'Omar Adel' })) });
    renderOperations('/tasks');
    const details = await open(user);
    const select = await within(details).findByLabelText('Reassign to');
    await waitFor(() => { expect(within(select).getAllByRole('option')).toHaveLength(2); });
    expect(within(select).queryByRole('option', { name: 'Mona Saleh' })).not.toBeInTheDocument();
    const button = within(details).getByRole('button', { name: 'Reassign the task' });
    expect(button).toBeDisabled();
    await user.selectOptions(select, 'Omar Adel');
    await user.type(within(details).getByLabelText('Reason for reassigning (optional)'), 'Workload');
    await user.click(button);
    expect(await within(details).findByRole('status')).toHaveTextContent('The task was saved.');
    expect(requests.find((r) => r.method === 'POST')).toMatchObject({
      path: '/api/v1/tasks/t1/reassign', body: { assigneeId: 'u2', reason: 'Workload', version: 0 },
    });
  });

  it('explains a refused reassignment, a stale task and a refused reason', async () => {
    const user = userEvent.setup();
    const answers = [problem(422, { rule: 'ASSIGNEE_UNCHANGED' }), problem(409), problem(403)];
    serve({ onWrite: () => answers.shift() });
    renderOperations('/tasks');
    const details = await open(user);
    const select = await within(details).findByLabelText('Reassign to');
    await waitFor(() => { expect(within(select).getAllByRole('option')).toHaveLength(2); });
    await user.selectOptions(select, 'Omar Adel');
    await user.click(within(details).getByRole('button', { name: 'Reassign the task' }));
    expect(await within(details).findByRole('alert')).toHaveTextContent('The task is already assigned to that user.');
    await user.click(within(details).getByRole('button', { name: 'Reassign the task' }));
    expect(await within(details).findByRole('alert')).toHaveTextContent('Someone else changed this record meanwhile.');
    fireEvent.change(within(details).getByLabelText('Record the reason for delay'), { target: { value: 'Late' } });
    await user.click(within(details).getByRole('button', { name: 'Save the reason' }));
    expect(await within(details).findByRole('alert')).toHaveTextContent('permitted');
  });

  it('opens from the tasks of a case too', async () => {
    const user = userEvent.setup();
    serve();
    renderOperations(`/cases/${CASE}`);
    const region = await screen.findByRole('region', { name: 'Tasks' });
    await user.click(await within(region).findByRole('button', { name: 'Details: File the appeal memorandum' }));
    expect(await screen.findByRole('region', { name: 'Task details: File the appeal memorandum' })).toBeInTheDocument();
  });
});

describe('the task form', () => {
  it('sends the start date and the instructions, and shows the rule when the start is after the due date', async () => {
    const user = userEvent.setup();
    const answers = [problem(400, { errors: [{ field: 'startsOn', code: 'StartsBeforeDue' }] }), jsonResponse(task(), 201)];
    const { requests } = serve({ onWrite: () => answers.shift() });
    renderOperations('/tasks');
    await user.click(await screen.findByRole('button', { name: 'New task' }));
    const form = screen.getByRole('form', { name: 'New task' });
    await user.type(within(form).getByLabelText('Title'), 'Prepare the memo');
    await user.selectOptions(await within(form).findByLabelText('Assignee'), 'Omar Adel');
    fireEvent.change(within(form).getByLabelText('Start date (optional)'), { target: { value: '2027-03-09' } });
    fireEvent.change(within(form).getByLabelText('Due date (optional)'), { target: { value: '2027-03-05' } });
    await user.type(within(form).getByLabelText('Instructions (optional)'), 'Attach the receipt');
    await user.click(within(form).getByRole('button', { name: 'Create task' }));
    expect(await within(form).findByText('The start date must not be after the due date.')).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Create task' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The task was saved.');
    expect(requests.filter((r) => r.method === 'POST').at(-1)?.body).toMatchObject({
      startsOn: '2027-03-09', dueDate: '2027-03-05', instructions: 'Attach the receipt', assigneeId: 'u2',
    });
  });
});
