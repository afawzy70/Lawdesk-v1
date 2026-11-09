import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { mount, page, problem, serve } from '../followup/testSupport';
import { DeadlinesPage } from './DeadlinesPage';
import { deadline } from './testSupport';

/** LD-054, requirements section 10: the register with its filters. */

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

const MEMBERS = [{ id: 'm1', displayName: 'Mona Specialist' }];

describe('deadlines register', () => {
  it('lists the deadlines of the visible cases with their state and a link to the deadline in its case', async () => {
    serve((r) => {
      if (r.path === '/api/v1/cases/members') return jsonResponse(MEMBERS);
      if (r.path === '/api/v1/deadlines') {
        return page([
          deadline({ overdue: true }),
          deadline({ id: 'd2', action: 'Reply', dueOn: null, ownerName: null, sourceType: 'HEARING', sourceLabel: '2026-02-01', calcStatus: 'APPROVED', status: 'MISSED' }),
        ]);
      }
      return undefined;
    });
    mount(<DeadlinesPage />, ['DEADLINE_VIEW']);
    await screen.findByText('File the appeal');
    const rows = screen.getAllByRole('row');
    expect(rows[1]).toHaveTextContent('LD-2026-00001');
    expect(rows[1]).toHaveTextContent('01/12/2026');
    expect(rows[1]).toHaveTextContent('Manual, awaiting approval');
    expect(rows[1]).toHaveTextContent('overdue');
    expect(rows[2]).toHaveTextContent('Hearing: 01/02/2026');
    expect(rows[2]).toHaveTextContent('Missed');
    expect(rows[2]).toHaveTextContent('Not specified');
    expect(within(rows[1] as HTMLElement).getByRole('link', { name: 'LD-2026-00001' }).getAttribute('href')).toContain('#wp3-ref-DEADLINE-d1');
    expect(screen.getByText('2 deadlines')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Deadline rules, holidays and reminders' })).toBeInTheDocument();
  });

  it('sends the chosen filters to the server and says when nothing matches', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => {
      if (r.path === '/api/v1/cases/members') return jsonResponse(MEMBERS);
      if (r.path === '/api/v1/deadlines') return page(r.query.get('overdue') === 'true' ? [] : [deadline()]);
      return undefined;
    });
    mount(<DeadlinesPage />, ['DEADLINE_VIEW']);
    await screen.findByText('File the appeal');
    const form = screen.getByRole('form', { name: 'Filter the deadlines' });
    await user.selectOptions(within(form).getByLabelText('State'), 'Open');
    await user.selectOptions(within(form).getByLabelText('Approval'), 'Approved');
    await user.selectOptions(await within(form).findByRole('option', { name: 'Mona Specialist' }).then(() => within(form).getByLabelText('Responsible member')), 'Mona Specialist');
    await user.type(within(form).getByLabelText('Due from'), '2026-11-01');
    await user.type(within(form).getByLabelText('Due until'), '2026-12-31');
    await user.selectOptions(within(form).getByLabelText('Lateness'), 'Overdue only');
    expect(await screen.findByText('No deadline matches.')).toBeInTheDocument();
    const last = requests.filter((r) => r.path === '/api/v1/deadlines').at(-1);
    expect(Object.fromEntries(last?.query ?? [])).toMatchObject({ status: 'OPEN', calcStatus: 'APPROVED', ownerId: 'm1', from: '2026-11-01', to: '2026-12-31', overdue: 'true', page: '0' });
    await user.selectOptions(within(form).getByLabelText('Lateness'), 'Not overdue');
    await user.selectOptions(within(form).getByLabelText('Lateness'), 'All');
    await screen.findByText('File the appeal');
  });

  it('shows more pages on request and reports a failure', async () => {
    const user = userEvent.setup();
    let fail = false;
    serve((r) => {
      if (r.path === '/api/v1/cases/members') return problem(403);
      if (r.path !== '/api/v1/deadlines') return undefined;
      if (fail) return problem(500);
      return jsonResponse({ items: [deadline({ id: r.query.get('page') === '0' ? 'a' : 'b', action: r.query.get('page') === '0' ? 'First' : 'Second' })], page: Number(r.query.get('page')), size: 20, totalItems: 2, totalPages: 2 });
    });
    mount(<DeadlinesPage />, ['DEADLINE_VIEW']);
    await screen.findByText('First');
    fail = true;
    await user.click(screen.getByRole('button', { name: 'Show more' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved');
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Show more' }));
    await screen.findByText('Second');
    await waitFor(() => { expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument(); });
  });

  it('reports a failed load', async () => {
    serve((r) => (r.path === '/api/v1/deadlines' ? problem(500) : problem(403)));
    mount(<DeadlinesPage />, ['DEADLINE_VIEW']);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
