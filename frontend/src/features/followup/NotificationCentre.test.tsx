import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import type { AppNotification, NotificationPreference } from './api';
import { NotificationsPage } from './NotificationsPage';
import { CASE, mount, page, problem, serve } from './testSupport';

/** LD-056, requirements section 32 and decision D11: the notification centre (kinds, filters, links, preferences). */

const note = (overrides: Partial<AppNotification> = {}): AppNotification => ({
  id: 'n1', type: 'TASK_OVERDUE', caseId: CASE, caseReferenceNo: 'LD-2027-00001', recordRef: 'TASK:t1', recordType: 'TASK',
  recordId: 't1', params: { taskTitle: 'Prepare the memorandum', dueDate: '2027-03-01' }, createdAt: '2027-03-02T08:00:00Z',
  readAt: null, ...overrides,
});

const TYPES = [
  'CASE_NEW', 'HEARING_UPCOMING', 'EXPERT_SESSION_UPCOMING', 'APPEAL_DEADLINE', 'TASK_OVERDUE', 'HEARING_NO_RESULT',
  'INFO_REQUEST_OVERDUE', 'JUDGMENT_NEW', 'DOCUMENT_MISSING', 'DATE_CONFLICT', 'RISK_HIGH', 'TASK_ASSIGNED', 'TASK_DUE_SOON',
  'INFO_REQUEST_ESCALATED', 'HEARING_NO_NEXT_DATE',
];
const preferences = (off: string[] = []): NotificationPreference[] =>
  TYPES.map((type, index) => ({ type, core: index < 11, position: index + 1, enabled: !off.includes(type) }));

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('the kinds of notification', () => {
  it('words each kind of the requirements, with the values of the record', async () => {
    serve(() => page([
      note({ id: 'a', type: 'CASE_NEW', params: { caseReferenceNo: 'LD-2027-00001' } }),
      note({ id: 'b', type: 'HEARING_UPCOMING', params: { date: '2027-03-10', time: '09:30', caseReferenceNo: 'LD-2027-00001' } }),
      note({ id: 'c', type: 'EXPERT_SESSION_UPCOMING', params: { date: '2027-03-10', caseReferenceNo: 'LD-2027-00001' } }),
      note({ id: 'd', type: 'APPEAL_DEADLINE', params: { title: 'Appeal date', date: '2027-03-12', caseReferenceNo: 'LD-2027-00001' } }),
      note({ id: 'e' }),
      note({ id: 'f', type: 'HEARING_NO_RESULT', params: { hearingDate: '2027-03-01', caseReferenceNo: 'LD-2027-00001' } }),
      note({ id: 'g', type: 'JUDGMENT_NEW', params: { judgmentDate: '2027-03-01', caseReferenceNo: 'LD-2027-00001' } }),
      note({ id: 'h', type: 'DOCUMENT_MISSING', params: { requestNo: 'IR-1', caseReferenceNo: 'LD-2027-00001' } }),
      note({ id: 'i', type: 'DATE_CONFLICT', caseId: null, caseReferenceNo: null, recordRef: 'CALENDAR:2027-03-10', recordType: 'CALENDAR', recordId: '2027-03-10', params: { date: '2027-03-10', time: '09:00', count: '2' } }),
      note({ id: 'j', type: 'TASK_ASSIGNED', params: { taskTitle: 'Collect' } }),
      note({ id: 'k', type: 'TASK_DUE_SOON', params: { taskTitle: 'Collect', dueDate: '2027-03-12' } }),
    ]));
    mount(<NotificationsPage />);
    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(11);
    const text = items.map((item) => item.textContent);
    expect(text[0]).toContain('A new case LD-2027-00001 was assigned to you.');
    expect(text[1]).toContain('A hearing of case LD-2027-00001 is on 2027-03-10 09:30.');
    expect(text[2]).toContain('An expert session in case LD-2027-00001 is on 2027-03-10');
    expect(text[3]).toContain('The date "Appeal date" of case LD-2027-00001 is on 2027-03-12.');
    expect(text[4]).toContain('The task "Prepare the memorandum" is late (due 2027-03-01).');
    expect(text[5]).toContain('has no recorded result');
    expect(text[6]).toContain('A judgment of 2027-03-01 was recorded in case LD-2027-00001.');
    expect(text[7]).toContain('The reply to request IR-1 (case LD-2027-00001) is incomplete');
    expect(text[8]).toContain('2 of your dates conflict on 2027-03-10 at 09:00.');
    expect(text[9]).toContain('The task "Collect" was assigned to you.');
    expect(text[10]).toContain('The task "Collect" is due on 2027-03-12.');
  });

  it('leaves no raw placeholder when a value is missing', async () => {
    serve(() => page([note({ type: 'HEARING_UPCOMING', params: { date: '2027-03-10' } })]));
    mount(<NotificationsPage />);
    const item = (await screen.findAllByRole('listitem'))[0] as HTMLElement;
    expect(item.textContent).not.toMatch(/[{}]/);
  });

  it('shows the Arabic words right to left', async () => {
    serve(() => page([note()]));
    mount(<NotificationsPage />, [], '/notifications?lang=ar');
    expect(await screen.findByText(/المهمة «Prepare the memorandum» متأخرة/)).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
  });
});

describe('filters', () => {
  it('asks for one kind and one read state, and shows what comes back', async () => {
    const user = userEvent.setup();
    const { requests } = serve(() => page([note()]));
    mount(<NotificationsPage />);
    await screen.findAllByRole('listitem');
    const kinds = screen.getByLabelText('Kind');
    expect(within(kinds).getAllByRole('option')).toHaveLength(16);
    await user.selectOptions(kinds, 'Late tasks');
    await waitFor(() => { expect(requests.at(-1)?.query.getAll('type')).toEqual(['TASK_OVERDUE']); });
    await user.selectOptions(screen.getByLabelText('State'), 'Unread');
    await waitFor(() => { expect(requests.at(-1)?.query.get('read')).toBe('false'); });
    await user.selectOptions(screen.getByLabelText('State'), 'Read');
    await waitFor(() => { expect(requests.at(-1)?.query.get('read')).toBe('true'); });
    await user.selectOptions(screen.getByLabelText('State'), 'All');
    await user.selectOptions(kinds, 'All');
    await waitFor(() => {
      const last = requests.at(-1);
      expect([last?.query.has('type'), last?.query.has('read')]).toEqual([false, false]);
    });
  });
});

describe('read and unread', () => {
  it('marks a read notification unread again', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (r.method === 'POST' ? new Response(null, { status: 204 }) : page([note({ readAt: '2027-03-03T08:00:00Z' })])));
    mount(<NotificationsPage />);
    await user.click(await screen.findByRole('button', { name: 'Mark as unread' }));
    await waitFor(() => { expect(requests.some((r) => r.path === '/api/v1/notifications/n1/unread')).toBe(true); });
    expect(screen.queryByRole('button', { name: 'Mark as read' })).not.toBeInTheDocument();
  });

  it('explains a refusal', async () => {
    const user = userEvent.setup();
    serve((r) => (r.method === 'POST' ? problem(404) : page([note({ readAt: '2027-03-03T08:00:00Z' })])));
    mount(<NotificationsPage />);
    await user.click(await screen.findByRole('button', { name: 'Mark as unread' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists');
  });
});

describe('links to the record', () => {
  it('opens the case, the day of a conflict, or the task list for a task of no case', async () => {
    serve(() => page([
      note({ id: 'a' }),
      note({ id: 'b', type: 'DATE_CONFLICT', caseId: null, caseReferenceNo: null, recordType: 'CALENDAR', recordId: '2027-03-10', params: { date: '2027-03-10', time: '09:00', count: '2' } }),
      note({ id: 'c', caseId: null, caseReferenceNo: null }),
      note({ id: 'd', type: 'RISK_HIGH', caseId: null, caseReferenceNo: null, recordType: 'RISK', recordId: 'r1', params: { level: 'HIGH', caseReferenceNo: 'X' } }),
    ]));
    mount(<NotificationsPage />);
    const items = await screen.findAllByRole('listitem');
    expect(within(items[0] as HTMLElement).getByRole('link', { name: 'LD-2027-00001' })).toHaveAttribute('href', expect.stringContaining(`/cases/${CASE}`));
    expect(within(items[0] as HTMLElement).queryByRole('link', { name: 'Open' })).not.toBeInTheDocument();
    expect(within(items[1] as HTMLElement).getByRole('link', { name: 'Open' })).toHaveAttribute('href', '/calendar?date=2027-03-10&mode=DAY&lang=en');
    expect(within(items[2] as HTMLElement).getByRole('link', { name: 'Open' })).toHaveAttribute('href', '/tasks?lang=en');
    expect(within(items[3] as HTMLElement).queryByRole('link')).not.toBeInTheDocument();
  });
});

describe('preferences', () => {
  it('shows every kind, switches one off for the user, and closes again', async () => {
    const user = userEvent.setup();
    let off: string[] = [];
    const { requests } = serve((r) => {
      if (r.path === '/api/v1/notifications/preferences' && r.method === 'GET') return jsonResponse(preferences(off));
      if (r.method === 'PUT') {
        const type = decodeURIComponent(r.path.split('/').at(-1) ?? '');
        const enabled = (r.body as { enabled: boolean }).enabled;
        off = enabled ? off.filter((o) => o !== type) : [...off, type];
        return jsonResponse({ type, core: true, position: 1, enabled });
      }
      return page([note()]);
    });
    mount(<NotificationsPage />);
    await screen.findAllByRole('listitem');
    expect(requests.some((r) => r.path.endsWith('/preferences'))).toBe(false);
    await user.click(screen.getByRole('button', { name: 'Choose the notifications I receive' }));
    const group = await screen.findByRole('group', { name: 'My notifications' });
    const boxes = within(group).getAllByRole('checkbox');
    expect(boxes).toHaveLength(15);
    boxes.forEach((box) => { expect(box).toBeChecked(); });
    expect(group).toHaveTextContent('Notifications stay inside LawDesk.');
    await user.click(within(group).getByRole('checkbox', { name: 'Late tasks' }));
    await waitFor(() => { expect(within(group).getByRole('checkbox', { name: 'Late tasks' })).not.toBeChecked(); });
    expect(requests.find((r) => r.method === 'PUT')?.path).toBe('/api/v1/notifications/preferences/TASK_OVERDUE');
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ enabled: false });
    await user.click(within(group).getByRole('checkbox', { name: 'Late tasks' }));
    await waitFor(() => { expect(within(group).getByRole('checkbox', { name: 'Late tasks' })).toBeChecked(); });
    await user.click(screen.getByRole('button', { name: 'Choose the notifications I receive' }));
    expect(screen.queryByRole('group', { name: 'My notifications' })).not.toBeInTheDocument();
  });

  it('explains when the choices cannot be read or saved', async () => {
    const user = userEvent.setup();
    serve((r) => (r.path.endsWith('/preferences') ? problem(500) : page([note()])));
    const a = mount(<NotificationsPage />);
    await screen.findAllByRole('listitem');
    await user.click(screen.getByRole('button', { name: 'Choose the notifications I receive' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    a.unmount();
    serve((r) => {
      if (r.method === 'PUT') return problem(403);
      return r.path.endsWith('/preferences') ? jsonResponse(preferences()) : page([note()]);
    });
    mount(<NotificationsPage />);
    await screen.findAllByRole('listitem');
    await user.click(screen.getByRole('button', { name: 'Choose the notifications I receive' }));
    await user.click(await screen.findByRole('checkbox', { name: 'New cases' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('ignores an answer that is not a list', async () => {
    const user = userEvent.setup();
    serve((r) => (r.path.endsWith('/preferences') ? jsonResponse({}) : page([note()])));
    mount(<NotificationsPage />);
    await screen.findAllByRole('listitem');
    await user.click(screen.getByRole('button', { name: 'Choose the notifications I receive' }));
    await waitFor(() => { expect(screen.getByRole('button', { name: 'Choose the notifications I receive' })).toHaveAttribute('aria-expanded', 'false'); });
  });
});
