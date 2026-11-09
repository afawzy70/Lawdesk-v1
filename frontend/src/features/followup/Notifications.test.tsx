import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import type { AppNotification } from './api';
import { NotificationsBell } from './NotificationsBell';
import { NotificationsPage } from './NotificationsPage';
import { CASE, mount, page, problem, serve } from './testSupport';

/** LD-053, requirements section 32 and decision D11: the in-app notifications bell and list. */

const note = (overrides: Partial<AppNotification> = {}): AppNotification => ({
  id: 'n1', type: 'INFO_REQUEST_OVERDUE', caseId: CASE, caseReferenceNo: 'LD-2026-00001', recordRef: 'INFO_REQUEST:r1',
  recordType: 'INFO_REQUEST', recordId: 'r1',
  params: { requestNo: 'IR-1', caseReferenceNo: 'LD-2026-00001' }, createdAt: '2026-11-05T08:00:00Z', readAt: null, ...overrides,
});

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('notifications bell', () => {
  it('names the number of unread notifications and links to the list', async () => {
    serve(() => jsonResponse({ unread: 3 }));
    mount(<NotificationsBell />);
    const bell = await screen.findByRole('link', { name: 'Notifications, 3 unread' });
    expect(bell).toHaveAttribute('href', expect.stringContaining('/notifications'));
    expect(bell).toHaveTextContent('3');
  });

  it('shows no number when nothing is unread, caps a long count, and stays quiet when it cannot ask', async () => {
    serve(() => jsonResponse({ unread: 0 }));
    const a = mount(<NotificationsBell />);
    const plain = await screen.findByRole('link', { name: 'Notifications' });
    await waitFor(() => { expect(plain).not.toHaveTextContent(/\d/); });
    a.unmount();
    serve(() => jsonResponse({ unread: 250 }));
    const b = mount(<NotificationsBell />);
    expect(await screen.findByText('99+')).toBeInTheDocument();
    b.unmount();
    serve(() => problem(500));
    const c = mount(<NotificationsBell />);
    expect(await screen.findByRole('link', { name: 'Notifications' })).not.toHaveTextContent(/\d/);
    c.unmount();
    serve(() => new Response(null, { status: 204 }));
    mount(<NotificationsBell />);
    expect(await screen.findByRole('link', { name: 'Notifications' })).not.toHaveTextContent(/\d/);
  });
});

describe('notifications list', () => {
  it('lists the notifications with their text, case and read state', async () => {
    serve(() => page([
      note(),
      note({ id: 'n2', type: 'HEARING_NO_NEXT_DATE', params: { hearingDate: '2026-11-01', caseReferenceNo: 'LD-2026-00001' }, readAt: '2026-11-06T08:00:00Z' }),
      note({ id: 'n3', type: 'RISK_HIGH', params: { level: 'HIGH', caseReferenceNo: 'LD-2026-00001' }, caseId: null, caseReferenceNo: null }),
      note({ id: 'n4', type: 'INFO_REQUEST_ESCALATED', params: { requestNo: 'IR-2', caseReferenceNo: 'LD-2026-00001' } }),
      note({ id: 'n5', type: 'SOMETHING_NEW', params: {} }),
    ]));
    mount(<NotificationsPage />);
    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('The reply to request IR-1 (case LD-2026-00001) is overdue.');
    expect(items[0]).toHaveTextContent('unread');
    expect(within(items[0] as HTMLElement).getByRole('link', { name: 'LD-2026-00001' })).toBeInTheDocument();
    expect(items[1]).toHaveTextContent('adjourned with no next date');
    expect(within(items[1] as HTMLElement).queryByText('unread')).not.toBeInTheDocument();
    expect(items[2]).toHaveTextContent('now has a high risk level (HIGH)');
    expect(items[3]).toHaveTextContent('was escalated to you');
    expect(items[4]).toHaveTextContent('You have a notification.');
    expect(screen.getByText(/nothing is sent by e-mail or SMS/)).toBeInTheDocument();
  });

  it('words the reminders of approaching and overdue legal deadlines', async () => {
    serve(() => page([
      note({ id: 'n6', type: 'DEADLINE_APPROACHING', params: { action: 'File the appeal', caseReferenceNo: 'LD-2026-00001', dueOn: '2026-12-01', daysRemaining: '3' } }),
      note({ id: 'n7', type: 'DEADLINE_OVERDUE', params: { action: 'Reply', caseReferenceNo: 'LD-2026-00001', dueOn: '2026-11-01' } }),
    ]));
    mount(<NotificationsPage />);
    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('The deadline "File the appeal" (case LD-2026-00001) is due on 2026-12-01, in 3 days.');
    expect(items[1]).toHaveTextContent('The deadline "Reply" (case LD-2026-00001) was due on 2026-11-01 and is still open.');
  });

  it('marks one or all as read and reloads', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (r.method === 'POST' ? new Response(null, { status: 204 }) : page([note()])));
    mount(<NotificationsPage />);
    await user.click(await screen.findByRole('button', { name: 'Mark as read' }));
    await waitFor(() => { expect(requests.some((r) => r.path === '/api/v1/notifications/n1/read')).toBe(true); });
    await user.click(await screen.findByRole('button', { name: 'Mark all as read' }));
    await waitFor(() => { expect(requests.some((r) => r.path === '/api/v1/notifications/read-all')).toBe(true); });
    expect(requests.filter((r) => r.method === 'GET').length).toBeGreaterThanOrEqual(3);
  });

  it('says there are none, shows more pages, and explains failures', async () => {
    const user = userEvent.setup();
    serve(() => page([]));
    const a = mount(<NotificationsPage />);
    expect(await screen.findByText('No notifications.')).toBeInTheDocument();
    a.unmount();
    serve((r) => (r.query.get('page') === '1' ? page([note({ id: 'n9', readAt: 'x' })], { page: 1, totalPages: 2 }) : page([note()], { totalPages: 2 })));
    const b = mount(<NotificationsPage />);
    await user.click(await screen.findByRole('button', { name: 'Show more' }));
    await waitFor(() => { expect(screen.getAllByRole('listitem')).toHaveLength(2); });
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
    b.unmount();
    serve(() => problem(500));
    const c = mount(<NotificationsPage />);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    c.unmount();
    serve((r) => (r.method === 'POST' ? problem(404) : page([note()])));
    mount(<NotificationsPage />);
    await user.click(await screen.findByRole('button', { name: 'Mark as read' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists');
  });

  it('explains a failure to read more', async () => {
    const user = userEvent.setup();
    serve((r) => (r.query.get('page') === '1' ? problem(500) : page([note()], { totalPages: 2 })));
    mount(<NotificationsPage />);
    await user.click(await screen.findByRole('button', { name: 'Show more' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
