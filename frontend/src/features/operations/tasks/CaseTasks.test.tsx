import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { CASE, page, problem, renderOperations, scriptedFetch, task, type Request } from '../testSupport';

/** LD-027 tasks in the case's operations section (docs/delivery/PHASE-5-ACCEPTANCE.md, QA-0216). */

const USERS = [{ id: 'u1', username: 'mona', displayName: 'Mona Saleh', enabled: true }];

interface Backend {
  tasks?: Response;
  onWrite?: (request: Request) => Response;
}

function serve(backend: Backend = {}) {
  return scriptedFetch((r) => {
    if (r.method !== 'GET') return backend.onWrite?.(r);
    if (r.path === '/api/v1/tasks') return (backend.tasks ?? page([])).clone();
    if (r.path === '/api/v1/users/directory') return jsonResponse(USERS);
    if (r.path === `/api/v1/cases/${CASE}/hearings` || r.path === `/api/v1/cases/${CASE}/proceedings`) {
      return page([]);
    }
    return undefined;
  });
}

const section = async () => screen.findByRole('region', { name: 'Tasks' });

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('case tasks', () => {
  it('lists the tasks of the case with their assignee, due state and moves', async () => {
    const { requests } = serve({
      tasks: page([task({ overdue: true }), task({ id: 't2', title: 'Done task', status: 'DONE', dueDate: null })]),
    });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    const items = await within(region).findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('File the appeal memorandum');
    expect(items[0]).toHaveTextContent('Mona Saleh');
    expect(items[0]).toHaveTextContent('4 Mar 2025');
    expect(items[0]).toHaveTextContent('Overdue');
    expect(items[1]).toHaveTextContent('No due date');
    expect(within(items[1] as HTMLElement).getByRole('button', { name: 'Reopen: Done task' })).toBeVisible();
    const call = requests.find((r) => r.path === '/api/v1/tasks');
    expect(call?.query.get('caseId')).toBe(CASE);
    expect(call?.query.get('size')).toBe('100');
  });

  it('creates a task for the case and completes one', async () => {
    const user = userEvent.setup();
    let tasks = page([task()]);
    const { requests } = serve({
      get tasks() {
        return tasks;
      },
      onWrite: (r) => {
        const created = r.path === '/api/v1/tasks';
        const added = task({ id: 't2', title: 'Collect the documents' });
        tasks = page([task(created ? {} : { status: 'DONE' }), added]);
        return jsonResponse(created ? added : task({ status: 'DONE' }), created ? 201 : 200);
      },
    });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    await user.click(await within(region).findByRole('button', { name: 'New task' }));
    const form = within(region).getByRole('form', { name: 'New task' });
    await user.type(within(form).getByLabelText('Title'), 'Collect the documents');
    await user.selectOptions(await within(form).findByLabelText('Assignee'), 'u1');
    await user.click(within(form).getByRole('button', { name: 'Create task' }));
    expect(await within(region).findByRole('status')).toHaveTextContent('The task was saved.');
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({
      title: 'Collect the documents',
      assigneeId: 'u1',
      caseId: CASE,
      priority: 'NORMAL',
    });

    await user.click(within(region).getByRole('button', { name: 'Complete: File the appeal memorandum' }));
    expect(await within(region).findByRole('button', { name: 'Reopen: File the appeal memorandum' })).toBeVisible();
  });

  it('edits a case task and explains a refused move', async () => {
    const user = userEvent.setup();
    const answers = [problem(422, { rule: 'CASE_ARCHIVED' }), jsonResponse(task())];
    const { requests } = serve({ tasks: page([task()]), onWrite: () => answers.shift() ?? problem(500) });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    await user.click(await within(region).findByRole('button', { name: 'Start: File the appeal memorandum' }));
    expect(await within(region).findByText('The case is archived and can no longer change.')).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    await user.click(within(region).getByRole('button', { name: 'Edit: File the appeal memorandum' }));
    const form = within(region).getByRole('form', { name: 'Edit task: File the appeal memorandum' });
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(region).findByRole('status')).toHaveTextContent('The task was saved.');
    expect(requests.find((r) => r.method === 'PUT')?.path).toBe('/api/v1/tasks/t1');
    await user.click(within(region).getByRole('button', { name: 'Edit: File the appeal memorandum' }));
    await user.click(within(region).getByRole('button', { name: 'Cancel' }));
    expect(within(region).queryByRole('form')).not.toBeInTheDocument();
  });

  it('explains an empty, refused or failed list', async () => {
    serve();
    const { unmount } = renderOperations(`/cases/${CASE}`);
    expect(await within(await section()).findByText('No tasks for this case yet.')).toBeVisible();
    unmount();

    serve({ tasks: problem(403) });
    const second = renderOperations(`/cases/${CASE}`);
    expect(await within(await section()).findByText('You are not permitted to view the tasks of this case.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'New task' })).not.toBeInTheDocument();
    second.unmount();

    const user = userEvent.setup();
    let tasks = problem(500);
    serve({
      get tasks() {
        return tasks;
      },
    });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    expect(
      await within(region).findByText('The data could not be loaded. Check the connection and try again.'),
    ).toBeVisible();
    tasks = page([task()]);
    await user.click(within(region).getByRole('button', { name: 'Try again' }));
    expect(await within(region).findByRole('listitem')).toBeVisible();
  });
});
