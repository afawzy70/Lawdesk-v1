import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import type { Permission } from '../../api/permissions';
import { jsonResponse } from '../../test/http';
import { CASE, mount, problem, serve } from '../followup/testSupport';
import type { CalendarEntry } from './api';
import { CaseDates } from './CaseDates';

/** LD-056, requirements section 15: the dates of one case on the case page. */

const entry = (overrides: Partial<CalendarEntry> = {}): CalendarEntry => ({
  id: 'CALENDAR_DATE:d1', type: 'MEMO_DUE', refType: 'CALENDAR_DATE', refId: 'd1', caseId: CASE, caseReferenceNo: 'LD-2027-00001',
  caseTitle: 'Rent dispute', caseStatus: 'OPEN', title: 'File the memorandum', detail: 'MEMO_DUE', stage: null, date: '2027-03-10',
  startTime: null, endTime: null, allDay: true, startsAt: '2027-03-09T21:00:00Z', endsAt: null, ownerId: 'u1', ownerName: 'Mona Saleh',
  status: 'ACTIVE', alternateMemberId: null, alternateMemberName: null, conflict: false, closedCaseFuture: false, ...overrides,
});

const VIEW: Permission[] = ['CALENDAR_VIEW'];
const MANAGE: Permission[] = ['CALENDAR_VIEW', 'CALENDAR_MANAGE'];

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('dates of the case', () => {
  it('lists what lies ahead for the case, asking for the case alone', async () => {
    const { requests } = serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse({ events: [entry()] }) : undefined));
    mount(<CaseDates caseId={CASE} archived={false} />, VIEW);
    const region = await screen.findByRole('region', { name: 'Dates of the case' });
    expect(await within(region).findByText('File the memorandum')).toBeInTheDocument();
    const call = requests.find((r) => r.path === '/api/v1/calendar/events');
    expect(call?.query.get('caseId')).toBe(CASE);
    const [from, to] = [call?.query.get('from') ?? '', call?.query.get('to') ?? ''];
    expect((new Date(to).getTime() - new Date(from).getTime()) / 86_400_000).toBe(100);
    expect(screen.queryByRole('button', { name: 'New date' })).not.toBeInTheDocument();
  });

  it('is not shown, and asks for nothing, without the permission to see the calendar', () => {
    const { requests } = serve(() => undefined);
    mount(<CaseDates caseId={CASE} archived={false} />, []);
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
    expect(requests).toHaveLength(0);
  });

  it('says when nothing lies ahead and when the calendar cannot be read', async () => {
    serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse({ events: [] }) : undefined));
    const a = mount(<CaseDates caseId={CASE} archived={false} />, VIEW);
    expect(await screen.findByText('No dates ahead for this case.')).toBeInTheDocument();
    a.unmount();
    serve(() => problem(500));
    mount(<CaseDates caseId={CASE} archived={false} />, VIEW);
    expect(await screen.findByText(/could not be loaded|Try again/)).toBeInTheDocument();
  });

  it('adds a date to the case, unless the case is archived', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => {
      if (r.method === 'POST') return jsonResponse({ id: 'd2' }, 201);
      return r.path === '/api/v1/calendar/events' ? jsonResponse({ events: [] }) : undefined;
    });
    const view = mount(<CaseDates caseId={CASE} archived />, MANAGE);
    await screen.findByText('No dates ahead for this case.');
    expect(screen.queryByRole('button', { name: 'New date' })).not.toBeInTheDocument();
    view.unmount();

    mount(<CaseDates caseId={CASE} archived={false} />, MANAGE);
    await user.click(await screen.findByRole('button', { name: 'New date' }));
    const form = screen.getByRole('form', { name: 'New date' });
    expect(within(form).getByLabelText('Kind of date')).toHaveValue('MEMO_DUE');
    await user.selectOptions(within(form).getByLabelText('Kind of date'), 'Appeal or grievance date');
    await user.type(within(form).getByLabelText('Title'), 'Appeal date set by the lawyer');
    fireEvent.change(within(form).getByLabelText('Date'), { target: { value: '2027-04-01' } });
    await user.click(within(form).getByRole('button', { name: 'Add the date' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The date was added.');
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      kind: 'APPEAL_DEADLINE', title: 'Appeal date set by the lawyer', notes: null, caseId: CASE, date: '2027-04-01',
      startTime: null, endTime: null,
    });
  });
});
