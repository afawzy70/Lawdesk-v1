import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { page, problem, renderOperations, scriptedFetch, task, type Request } from '../testSupport';

/** LD-027 tasks and LD-028 in-app reminders (docs/delivery/PHASE-5-ACCEPTANCE.md, QA-0216, QA-0224). */

const USERS = [
  { id: 'u1', username: 'mona', displayName: 'Mona Saleh', enabled: true },
  { id: 'u2', username: 'omar', displayName: 'Omar Adel', enabled: true },
];
const REMINDERS = { today: '2025-03-02', dueSoonDays: 3, overdue: 2, dueSoon: 1 };

interface Backend {
  tasks?: (request: Request) => Response;
  reminders?: Response;
  users?: Response;
  onWrite?: (request: Request) => Response;
}

function serve(backend: Backend = {}) {
  return scriptedFetch((r) => {
    if (r.method !== 'GET') return backend.onWrite?.(r);
    if (r.path === '/api/v1/tasks/reminders') return (backend.reminders ?? jsonResponse(REMINDERS)).clone();
    if (r.path === '/api/v1/tasks') return backend.tasks?.(r) ?? page([]);
    if (r.path === '/api/v1/users/directory') return (backend.users ?? jsonResponse(USERS)).clone();
    return undefined;
  });
}

const taskCalls = (requests: Request[]) => requests.filter((r) => r.method === 'GET' && r.path === '/api/v1/tasks');

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('task list', () => {
  it('opens on my tasks with the reminder counts', async () => {
    const { requests } = serve({
      tasks: () =>
        page([
          task({ overdue: true, dueDate: '2025-02-28', priority: 'URGENT' }),
          task({
            id: 't2',
            title: 'Renew the bar subscription',
            caseId: null,
            caseReferenceNo: null,
            caseTitle: null,
            dueSoon: true,
            status: 'IN_PROGRESS',
          }),
        ]),
    });
    renderOperations('/tasks?lang=en');
    expect(await screen.findByRole('heading', { level: 1, name: 'Tasks' })).toBeInTheDocument();
    const reminders = screen.getByRole('region', { name: 'Reminders' });
    expect(await within(reminders).findByRole('button', { name: '2 overdue' })).toBeInTheDocument();
    expect(within(reminders).getByRole('button', { name: '1 due within 3 days' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'My tasks' })).toHaveAttribute('aria-pressed', 'true');

    const table = await screen.findByRole('table', { name: 'Tasks' });
    const [first, second] = within(table).getAllByRole('row').slice(1);
    expect(first).toHaveTextContent('File the appeal memorandum');
    expect(first).toHaveTextContent('LD-2025-00042');
    expect(first).toHaveTextContent('Mona Saleh');
    expect(first).toHaveTextContent('28 Feb 2025');
    expect(first).toHaveTextContent('Urgent');
    expect(first).toHaveTextContent('Overdue');
    expect(second).toHaveTextContent('No case');
    expect(second).toHaveTextContent('In progress');
    expect(second).toHaveTextContent('Due soon');
    expect(screen.getByRole('region', { name: 'Tasks' })).toHaveAttribute('tabindex', '0');
    expect(document.title).toBe('Tasks — LawDesk (Preview)');
    expect(taskCalls(requests)[0]?.query.get('assignee')).toBe('me');
  });

  it('switches to all tasks, filters by status and due state, and follows a reminder', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderOperations('/tasks');
    expect(await screen.findByText('No tasks match these filters.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'All tasks' }));
    expect(screen.getByRole('button', { name: 'All tasks' })).toHaveAttribute('aria-pressed', 'true');
    await user.selectOptions(screen.getByLabelText('Status'), 'DONE');
    await user.selectOptions(screen.getByLabelText('Due'), 'soon');
    await waitFor(() => {
      const last = taskCalls(requests).at(-1);
      expect(last?.query.has('assignee')).toBe(false);
      expect(last?.query.get('status')).toBe('DONE');
      expect(last?.query.get('due')).toBe('soon');
    });

    await user.click(await screen.findByRole('button', { name: '2 overdue' }));
    await waitFor(() => {
      const last = taskCalls(requests).at(-1);
      expect(last?.query.get('assignee')).toBe('me');
      expect(last?.query.get('due')).toBe('overdue');
      expect(last?.query.has('status')).toBe(false);
    });
    expect(screen.getByLabelText('Due')).toHaveValue('overdue');
    await user.click(screen.getByRole('button', { name: '1 due within 3 days' }));
    await waitFor(() => {
      expect(taskCalls(requests).at(-1)?.query.get('due')).toBe('soon');
    });
    await user.selectOptions(screen.getByLabelText('Due'), '');
    await user.selectOptions(screen.getByLabelText('Status'), '');
    await waitFor(() => {
      const last = taskCalls(requests).at(-1);
      expect(last?.query.has('due')).toBe(false);
      expect(last?.query.has('status')).toBe(false);
    });
  });

  it('says when nothing is overdue and pages through long lists', async () => {
    const user = userEvent.setup();
    serve({
      reminders: jsonResponse({ ...REMINDERS, overdue: 0, dueSoon: 0 }),
      tasks: (r) => {
        const number = Number(r.query.get('page'));
        return page([task({ id: `t${String(number)}`, title: `Task ${String(number)}` })], {
          page: number,
          totalItems: 25,
          totalPages: 2,
        });
      },
    });
    renderOperations('/tasks');
    expect(await screen.findByText('No overdue tasks.')).toBeInTheDocument();
    expect(screen.getByText('Nothing due within 3 days.')).toBeInTheDocument();
    expect(await screen.findByText('Task 0')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(await screen.findByText('Task 1')).toBeInTheDocument();
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(await screen.findByText('Task 0')).toBeInTheDocument();
  });

  it('shows the access-denied page without TASK_VIEW and a retry when the API fails', async () => {
    serve({ tasks: () => problem(403), reminders: problem(403) });
    const { unmount } = renderOperations('/tasks');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    unmount();

    const user = userEvent.setup();
    let fail = true;
    serve({ tasks: () => (fail ? problem(500) : page([task()])), reminders: problem(500) });
    renderOperations('/tasks');
    expect(await screen.findByText('The data could not be loaded. Check the connection and try again.')).toBeVisible();
    expect(screen.getByText('Reminders are not available right now.')).toBeVisible();
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('table', { name: 'Tasks' })).toBeInTheDocument();
  });

  it('shows Arabic labels right to left', async () => {
    serve({ tasks: () => page([task()]) });
    renderOperations('/tasks?lang=ar');
    expect(await screen.findByRole('heading', { level: 1, name: 'المهام' })).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(await screen.findByRole('button', { name: 'متأخرة: ٢' })).toBeInTheDocument();
    const row = within(await screen.findByRole('table', { name: 'المهام' })).getAllByRole('row')[1];
    expect(row).toHaveTextContent('مفتوحة');
    expect(row).toHaveTextContent('عادية');
  });
});

describe('creating, editing and moving tasks', () => {
  it('creates a task assigned to a user from the directory', async () => {
    const user = userEvent.setup();
    const { requests } = serve({ onWrite: () => jsonResponse(task(), 201) });
    renderOperations('/tasks');
    await user.click(await screen.findByRole('button', { name: 'New task' }));
    const form = screen.getByRole('form', { name: 'New task' });
    await user.type(within(form).getByLabelText('Title'), 'Prepare the defence memo');
    await user.type(within(form).getByLabelText('Description (optional)'), '   ');
    await user.selectOptions(await within(form).findByLabelText('Assignee'), 'Omar Adel');
    fireEvent.change(within(form).getByLabelText('Due date (optional)'), { target: { value: '2025-03-05' } });
    await user.selectOptions(within(form).getByLabelText('Priority'), 'HIGH');
    await user.click(within(form).getByRole('button', { name: 'Create task' }));

    expect(await screen.findByRole('status')).toHaveTextContent('The task was saved.');
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      title: 'Prepare the defence memo',
      description: null,
      assigneeId: 'u2',
      dueDate: '2025-03-05',
      priority: 'HIGH',
      caseId: null,
    });
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('shows field errors and rules from the backend, and a missing directory', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [{ field: 'title', code: 'NotBlank' }, { field: 'assigneeId', code: 'NotNull' }] }),
      problem(400, { errors: [{ field: 'dueDate', code: 'FutureOrPresent' }] }),
      problem(422, { rule: 'ASSIGNEE_NOT_ASSIGNABLE' }),
    ];
    serve({ users: problem(500), onWrite: () => answers.shift() ?? problem(500) });
    renderOperations('/tasks');
    await user.click(await screen.findByRole('button', { name: 'New task' }));
    const form = screen.getByRole('form', { name: 'New task' });
    expect(await within(form).findByText('The list of users could not be loaded.')).toBeVisible();
    const submit = within(form).getByRole('button', { name: 'Create task' });
    await user.click(submit);
    expect(await within(form).findAllByText('This field is required.')).toHaveLength(2);
    expect(within(form).getByLabelText('Title')).toHaveAttribute('aria-invalid', 'true');
    await user.click(submit);
    expect(await within(form).findByText('This date cannot be in the past.')).toBeVisible();
    await user.click(submit);
    expect(await within(form).findByText('Choose an active user to assign the task to.')).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('edits a task with its version and explains a stale one', async () => {
    const user = userEvent.setup();
    const answers = [problem(409), jsonResponse(task())];
    const { requests } = serve({ tasks: () => page([task()]), onWrite: () => answers.shift() ?? problem(500) });
    renderOperations('/tasks');
    await user.click(await screen.findByRole('button', { name: 'Edit: File the appeal memorandum' }));
    const form = screen.getByRole('form', { name: 'Edit task: File the appeal memorandum' });
    expect(within(form).getByLabelText('Title')).toHaveValue('File the appeal memorandum');
    expect(await within(form).findByLabelText('Assignee')).toHaveValue('u1');
    expect(within(form).getByLabelText('Due date (optional)')).toHaveValue('2025-03-04');
    await user.clear(within(form).getByLabelText('Title'));
    await user.type(within(form).getByLabelText('Title'), 'File the memorandum');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(
      await within(form).findByText('Someone else changed this record meanwhile. Reload and try again.'),
    ).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The task was saved.');
    const put = requests.filter((r) => r.method === 'PUT').at(-1);
    expect(put?.path).toBe('/api/v1/tasks/t1');
    expect(put?.body).toEqual({
      title: 'File the memorandum',
      description: 'Attach the judgment copy',
      assigneeId: 'u1',
      dueDate: '2025-03-04',
      priority: 'NORMAL',
      version: 0,
    });
  });

  it('offers the moves each status allows and explains a refused one', async () => {
    const user = userEvent.setup();
    const answers = [problem(409), problem(422, { rule: 'TASK_STATUS_TRANSITION' }), jsonResponse(task())];
    const { requests } = serve({
      tasks: () =>
        page([
          task(),
          task({ id: 't2', title: 'Started', status: 'IN_PROGRESS' }),
          task({ id: 't3', title: 'Finished', status: 'DONE' }),
          task({ id: 't4', title: 'Dropped', status: 'CANCELLED', version: 4 }),
        ]),
      onWrite: () => answers.shift() ?? problem(500),
    });
    renderOperations('/tasks');
    const table = await screen.findByRole('table', { name: 'Tasks' });
    const rows = within(table).getAllByRole('row').slice(1);
    const names = (row: HTMLElement | undefined) =>
      within(row as HTMLElement)
        .getAllByRole('button')
        .map((b) => b.textContent);
    expect(names(rows[0])).toEqual(['Edit', 'Start', 'Complete', 'Cancel task']);
    expect(names(rows[1])).toEqual(['Edit', 'Complete', 'Back to open', 'Cancel task']);
    expect(names(rows[2])).toEqual(['Edit', 'Reopen']);
    expect(names(rows[3])).toEqual(['Edit', 'Reopen']);

    await user.click(screen.getByRole('button', { name: 'Complete: File the appeal memorandum' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Someone else changed this record meanwhile. Reload and try again.',
    );
    await user.click(screen.getByRole('button', { name: 'Start: File the appeal memorandum' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This status change is not allowed.');
    await user.click(screen.getByRole('button', { name: 'Reopen: Dropped' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The task was saved.');
    expect(requests.filter((r) => r.method === 'POST').map((r) => [r.path, r.body])).toEqual([
      ['/api/v1/tasks/t1/status', { status: 'DONE', version: 0 }],
      ['/api/v1/tasks/t1/status', { status: 'IN_PROGRESS', version: 0 }],
      ['/api/v1/tasks/t4/status', { status: 'OPEN', version: 4 }],
    ]);
  });
});
