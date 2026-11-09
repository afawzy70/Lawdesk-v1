import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { mount, page, problem, serve, type Request } from '../followup/testSupport';
import type { DeadlineRule, Holiday } from './api';
import { DeadlineSettingsPage } from './DeadlineSettingsPage';
import { holiday, rule } from './testSupport';

/** LD-054, decision D6: the settings start empty; rules and holidays are typed with their source and approved by people. */

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

const MANAGE = ['DEADLINE_VIEW', 'DEADLINE_MANAGE'] as const;
const APPROVE = ['DEADLINE_VIEW', 'DEADLINE_MANAGE', 'DEADLINE_APPROVE'] as const;

function backend(
  state: { rules?: DeadlineRule[] | Response; holidays?: Holiday[] | Response; reminders?: object | Response } = {},
  write?: (r: Request) => Response | undefined,
) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === '/api/v1/deadline-rules') return state.rules instanceof Response ? state.rules : page(state.rules ?? []);
    if (r.method === 'GET' && r.path === '/api/v1/official-holidays') return state.holidays instanceof Response ? state.holidays : page(state.holidays ?? []);
    if (r.method === 'GET' && r.path === '/api/v1/deadline-settings/reminders') {
      return state.reminders instanceof Response ? state.reminders : jsonResponse(state.reminders ?? { configured: false, leadDays: null });
    }
    return write?.(r);
  });
}

describe('deadline settings', () => {
  it('starts empty and says the system proposes nothing until a rule is approved', async () => {
    backend();
    mount(<DeadlineSettingsPage />, [...MANAGE]);
    expect(await screen.findByText(/No rule has been entered/)).toBeInTheDocument();
    expect(await screen.findByText('No holiday has been entered.')).toBeInTheDocument();
    expect(await screen.findByText(/Not configured: no reminder is created/)).toBeInTheDocument();
    expect(screen.getByText(/states no statutory period/)).toBeInTheDocument();
    // Without the approval permission the lead times cannot be set.
    expect(screen.queryByRole('form', { name: 'Reminders before a deadline' })).not.toBeInTheDocument();
  });

  it('drafts a rule with its legal source and counting choices', async () => {
    const user = userEvent.setup();
    const { requests } = backend({}, (r) => (r.method === 'POST' ? jsonResponse(rule({ status: 'DRAFT' }), 201) : undefined));
    mount(<DeadlineSettingsPage />, [...MANAGE]);
    const form = await screen.findByRole('form', { name: 'New rule' });
    await user.click(within(form).getByRole('button', { name: 'Save as a draft' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Some fields are missing');
    await user.type(within(form).getByLabelText('Code'), 'TEST-1');
    await user.type(within(form).getByLabelText('Name'), 'Test-only rule');
    await user.type(within(form).getByLabelText('Period (days)'), '5');
    await user.selectOptions(within(form).getByLabelText('Counting method'), 'working days');
    await user.click(within(form).getByLabelText('The start day counts as the first day'));
    await user.click(within(form).getByLabelText('A last day on a closed day moves to the next open day'));
    await user.click(within(form).getByLabelText('Saturday'));
    await user.click(within(form).getByLabelText('Sunday'));
    await user.click(within(form).getByLabelText('Saturday'));
    await user.type(within(form).getByLabelText('Legal source (statute, article or decision)'), 'Test-only source');
    await user.type(within(form).getByLabelText('Effective from'), '2000-01-01');
    await user.click(within(form).getByRole('button', { name: 'Save as a draft' }));
    expect(await screen.findByText(/saved as a draft/)).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      code: 'TEST-1', label: 'Test-only rule', periodDays: 5, countMethod: 'WORKING', countsStartDay: true, rollForward: true,
      closedWeekdays: [7], legalSource: 'Test-only source', effectiveFrom: '2000-01-01',
    });
    await user.selectOptions(within(form).getByLabelText('Counting method'), 'calendar days');
  }, 20000);

  it('lets only a competent member approve or retire a rule, and shows who approved it', async () => {
    const user = userEvent.setup();
    const { requests } = backend(
      { rules: [rule({ id: 'a', code: 'DRAFT-1', status: 'DRAFT', approvedByName: null, approvedAt: null, countsStartDay: true, rollForward: true, closedWeekdays: [] }), rule({ id: 'b', code: 'LIVE-1' }), rule({ id: 'c', code: 'OLD-1', status: 'RETIRED' })] },
      (r) => (r.method === 'POST' ? jsonResponse(rule()) : undefined),
    );
    const first = mount(<DeadlineSettingsPage />, [...MANAGE]);
    await screen.findByText('DRAFT-1');
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    const live = screen.getByText('LIVE-1').closest('li');
    expect(live).toHaveTextContent('Hala Supervisor');
    expect(live).toHaveTextContent('Test-only source');
    expect(live).toHaveTextContent('5 working days');
    first.unmount();
    mount(<DeadlineSettingsPage />, [...APPROVE]);
    await screen.findByText('DRAFT-1');
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    expect(await screen.findByText('The rule was approved.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retire' }));
    expect(await screen.findByText('The rule was retired.')).toBeInTheDocument();
    expect(requests.filter((r) => r.method === 'POST').map((r) => r.path)).toEqual(['/api/v1/deadline-rules/a/approve', '/api/v1/deadline-rules/b/retire']);
  });

  it('explains a refused rule and a failed load', async () => {
    const user = userEvent.setup();
    backend({ rules: problem(500), holidays: problem(500) }, (r) => (r.method === 'POST' ? problem(409) : undefined));
    mount(<DeadlineSettingsPage />, [...MANAGE]);
    expect(await screen.findAllByText('The deadlines could not be loaded.')).toHaveLength(2);
    const form = await screen.findByRole('form', { name: 'New rule' });
    await user.type(within(form).getByLabelText('Code'), 'T');
    await user.type(within(form).getByLabelText('Name'), 'T');
    await user.type(within(form).getByLabelText('Period (days)'), '5');
    await user.type(within(form).getByLabelText('Legal source (statute, article or decision)'), 'S');
    await user.type(within(form).getByLabelText('Effective from'), '2000-01-01');
    await user.click(within(form).getByRole('button', { name: 'Save as a draft' }));
    expect(await screen.findByText(/Someone else changed this record/)).toBeInTheDocument();
  });

  it('enters, approves and retires holidays', async () => {
    const user = userEvent.setup();
    const { requests } = backend(
      { holidays: [holiday({ id: 'x' }), holiday({ id: 'y', label: 'Live', status: 'APPROVED', approvedByName: 'Hala Supervisor', approvedAt: '2026-11-02T08:00:00Z' })] },
      (r) => (r.method === 'POST' ? jsonResponse(holiday(), 201) : undefined),
    );
    mount(<DeadlineSettingsPage />, [...APPROVE]);
    await screen.findByText(/— Live/);
    expect(screen.getByText(/Approved by: Hala Supervisor/)).toBeInTheDocument();
    const form = screen.getByRole('form', { name: 'New holiday' });
    await user.click(within(form).getByRole('button', { name: 'Save as a draft' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Some fields are missing');
    await user.type(within(form).getByLabelText('Date'), '2031-03-05');
    await user.type(within(form).getByLabelText('Name'), 'Test holiday');
    await user.type(within(form).getByLabelText('Legal source (statute, article or decision)'), 'Test-only source');
    await user.click(within(form).getByRole('button', { name: 'Save as a draft' }));
    expect(await screen.findByText('The holiday was saved as a draft.')).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Approve' })[0] as HTMLElement);
    expect(await screen.findByText('The holiday was approved.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retire' }));
    expect(await screen.findByText('The holiday was retired.')).toBeInTheDocument();
    expect(requests.filter((r) => r.method === 'POST').map((r) => r.path)).toEqual([
      '/api/v1/official-holidays', '/api/v1/official-holidays/x/approve', '/api/v1/official-holidays/y/retire',
    ]);
  });

  it('sets the reminder lead times, with no default, and refuses what is not a number of days', async () => {
    const user = userEvent.setup();
    const { requests } = backend({}, (r) => (r.method === 'PUT' ? jsonResponse({ configured: true, leadDays: [1, 3, 7] }) : undefined));
    mount(<DeadlineSettingsPage />, [...APPROVE]);
    const form = await screen.findByRole('form', { name: 'Reminders before a deadline' });
    const field = within(form).getByLabelText('Days before the due date');
    expect(field).toHaveValue('');
    await user.type(field, '7, x');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(/Enter whole numbers of days/)).toBeInTheDocument();
    await user.clear(field);
    await user.type(field, '7، 3 1');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('The reminder days were saved.')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ leadDays: [7, 3, 1] });
  });

  it('shows the configured lead times and a refusal to save them', async () => {
    const user = userEvent.setup();
    backend({ reminders: { configured: true, leadDays: [1, 7] } }, (r) => (r.method === 'PUT' ? problem(403) : undefined));
    mount(<DeadlineSettingsPage />, [...APPROVE]);
    expect(await screen.findByText('Reminders are created 1, 7 days before the due date.')).toBeInTheDocument();
    await waitFor(() => { expect(screen.getByLabelText('Days before the due date')).toHaveValue('1, 7'); });
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(/You do not have the right/)).toBeInTheDocument();
  });

  it('shows a configuration with no reminder day as such', async () => {
    backend({ reminders: { configured: true, leadDays: [] } });
    mount(<DeadlineSettingsPage />, [...MANAGE]);
    expect(await screen.findByText('Reminders are created — days before the due date.')).toBeInTheDocument();
  });
});
