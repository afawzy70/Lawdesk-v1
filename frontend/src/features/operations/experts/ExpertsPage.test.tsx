import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { expertRecord, page, problem, renderOperations, scriptedFetch, type Request } from '../testSupport';

/** LD-025 the expert registry (docs/delivery/PHASE-5-ACCEPTANCE.md, QA-0200). */

const listCalls = (requests: Request[]) => requests.filter((r) => r.method === 'GET' && r.path === '/api/v1/experts');

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('expert registry', () => {
  it('lists experts with specialty, office, phone and status, and filters them', async () => {
    const user = userEvent.setup();
    const { requests } = scriptedFetch((r) =>
      r.path === '/api/v1/experts'
        ? page([expertRecord(), expertRecord({ id: 'e2', name: 'Hoda Adel', office: null, phone: null, active: false })])
        : undefined,
    );
    renderOperations('/experts?lang=en');
    expect(await screen.findByRole('heading', { level: 1, name: 'Experts' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Experts' });
    const [first, second] = within(table).getAllByRole('row').slice(1);
    expect(first).toHaveTextContent('Eng. Samir Fathy');
    expect(first).toHaveTextContent('Civil engineering');
    expect(first).toHaveTextContent('Giza experts office');
    expect(first).toHaveTextContent('+20 2 1234 5678');
    expect(first).toHaveTextContent('Active');
    expect(second).toHaveTextContent('Inactive');
    expect(document.title).toBe('Experts — LawDesk (Preview)');
    expect(listCalls(requests)[0]?.query.get('size')).toBe('20');

    await user.selectOptions(screen.getByLabelText('Status'), 'true');
    await user.type(screen.getByLabelText('Search name or specialty'), 'engineer');
    await waitFor(() => {
      const last = listCalls(requests).at(-1);
      expect(last?.query.get('active')).toBe('true');
      expect(last?.query.get('q')).toBe('engineer');
    });
    await user.selectOptions(screen.getByLabelText('Status'), '');
    await user.clear(screen.getByLabelText('Search name or specialty'));
    await waitFor(() => {
      const last = listCalls(requests).at(-1);
      expect(last?.query.has('active')).toBe(false);
      expect(last?.query.has('q')).toBe(false);
    });
  });

  it('registers an expert and shows field errors', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [{ field: 'name', code: 'NotBlank' }, { field: 'phone', code: 'Pattern' }] }),
      jsonResponse(expertRecord(), 201),
    ];
    const { requests } = scriptedFetch((r) => {
      if (r.method === 'POST') return answers.shift() ?? problem(500);
      return r.path === '/api/v1/experts' ? page([]) : undefined;
    });
    renderOperations('/experts');
    expect(await screen.findByText('No experts match these filters.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'New expert' }));
    const form = screen.getByRole('form', { name: 'New expert' });
    await user.type(within(form).getByLabelText('Specialty'), 'Accounting');
    await user.type(within(form).getByLabelText('Phone (optional)'), 'call me');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByText('This field is required.')).toBeVisible();
    expect(within(form).getByText('This value is not valid.')).toBeVisible();
    await user.type(within(form).getByLabelText('Name'), 'Hoda Adel');
    await user.type(within(form).getByLabelText('Office (optional)'), 'Cairo experts office');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The expert was saved.');
    expect(requests.filter((r) => r.method === 'POST').at(-1)?.body).toEqual({
      name: 'Hoda Adel',
      specialty: 'Accounting',
      office: 'Cairo experts office',
      phone: 'call me',
    });
  });

  it('edits and deactivates an expert with its version and explains a stale one', async () => {
    const user = userEvent.setup();
    const answers = [problem(409), jsonResponse(expertRecord({ active: false }))];
    const { requests } = scriptedFetch((r) => {
      if (r.method === 'PUT') return answers.shift() ?? problem(500);
      return r.path === '/api/v1/experts' ? page([expertRecord()]) : undefined;
    });
    renderOperations('/experts');
    await user.click(await screen.findByRole('button', { name: 'Edit Eng. Samir Fathy' }));
    const form = screen.getByRole('form', { name: 'Edit expert: Eng. Samir Fathy' });
    await user.click(within(form).getByLabelText('Active'));
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(
      await within(form).findByText('Someone else changed this record meanwhile. Reload and try again.'),
    ).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The expert was saved.');
    expect(requests.filter((r) => r.method === 'PUT').at(-1)).toMatchObject({
      path: '/api/v1/experts/e1',
      body: {
        name: 'Eng. Samir Fathy',
        specialty: 'Civil engineering',
        office: 'Giza experts office',
        phone: '+20 2 1234 5678',
        active: false,
        version: 0,
      },
    });
    await user.click(screen.getByRole('button', { name: 'Edit Eng. Samir Fathy' }));
    await user.click(within(screen.getByRole('form')).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('pages, shows the access-denied page, retries, and speaks Arabic', async () => {
    const user = userEvent.setup();
    let answer: 'page' | 'denied' | 'fail' = 'page';
    scriptedFetch((r) => {
      if (r.path !== '/api/v1/experts') return undefined;
      if (answer === 'denied') return problem(403);
      if (answer === 'fail') return problem(500);
      const number = Number(r.query.get('page'));
      return page([expertRecord({ id: `e${String(number)}`, name: `Expert ${String(number)}` })], {
        page: number,
        totalItems: 21,
        totalPages: 2,
      });
    });
    const first = renderOperations('/experts');
    expect(await screen.findByText('Expert 0')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(await screen.findByText('Expert 1')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(await screen.findByText('Expert 0')).toBeInTheDocument();
    first.unmount();

    answer = 'denied';
    const second = renderOperations('/experts');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    second.unmount();

    answer = 'fail';
    const third = renderOperations('/experts');
    expect(await screen.findByRole('alert')).toHaveTextContent('The data could not be loaded.');
    answer = 'page';
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Expert 0')).toBeInTheDocument();
    third.unmount();

    renderOperations('/experts?lang=ar');
    expect(await screen.findByRole('heading', { level: 1, name: 'الخبراء' })).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(await screen.findByRole('table', { name: 'الخبراء' })).toHaveTextContent('نشط');
  });
});
