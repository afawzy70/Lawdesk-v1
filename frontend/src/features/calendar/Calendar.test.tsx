import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import type { Permission } from '../../api/permissions';
import { jsonResponse } from '../../test/http';
import { CASE, mount, problem, serve, type Request } from '../followup/testSupport';
import type { CalendarEntry, CalendarView, Conflict } from './api';
import { CalendarPage } from './CalendarPage';

/** LD-056, requirements section 15 and scenario S11: the judicial calendar screen. */

const VIEW: Permission[] = ['CALENDAR_VIEW'];
const MANAGE: Permission[] = ['CALENDAR_VIEW', 'CALENDAR_MANAGE'];

function entry(overrides: Partial<CalendarEntry> = {}): CalendarEntry {
  return {
    id: 'HEARING:h1',
    type: 'HEARING',
    refType: 'HEARING',
    refId: 'h1',
    caseId: CASE,
    caseReferenceNo: 'LD-2027-00001',
    caseTitle: 'Rent dispute',
    caseStatus: 'OPEN',
    title: 'Rent dispute',
    detail: 'PLEADING',
    stage: 'FIRST_INSTANCE',
    date: '2027-03-10',
    startTime: '09:30',
    endTime: null,
    allDay: false,
    startsAt: '2027-03-10T06:30:00Z',
    endsAt: null,
    ownerId: 'u1',
    ownerName: 'Mona Saleh',
    status: 'SCHEDULED',
    alternateMemberId: 'u2',
    alternateMemberName: 'Omar Adel',
    conflict: false,
    closedCaseFuture: false,
    ...overrides,
  };
}

const task = (overrides: Partial<CalendarEntry> = {}) =>
  entry({
    id: 'TASK:t1',
    type: 'TASK',
    refType: 'TASK',
    refId: 't1',
    title: 'Prepare the memorandum',
    detail: 'NORMAL',
    stage: null,
    startTime: null,
    allDay: true,
    status: 'NEW',
    ...overrides,
  });

function view(events: CalendarEntry[], extra: Partial<CalendarView> = {}, request?: Request): CalendarView {
  return {
    view: 'LIST',
    from: request?.query.get('from') ?? '2027-03-01',
    to: request?.query.get('to') ?? '2027-03-31',
    zone: 'Asia/Kuwait',
    events,
    conflicts: [],
    days: [...new Set(events.map((e) => e.date))].map((date) => ({ date, count: events.filter((e) => e.date === date).length })),
    truncated: false,
    ...extra,
  };
}

const calls = (requests: Request[]) => requests.filter((r) => r.path === '/api/v1/calendar/events');

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('month view', () => {
  it('lays the month out as rows of seven cells so the grid has the structure its role requires', async () => {
    serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([entry(), task()], {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    await screen.findByRole('gridcell', { name: '10 Mar 2027: 2 entries' });
    const grid = screen.getByRole('grid');
    const rows = within(grid).getAllByRole('row');
    // One header row plus the weeks of March 2027 (it starts on a Monday), each padded to seven cells.
    expect(rows).toHaveLength(6);
    for (const row of rows.slice(1)) expect(within(row).getAllByRole('gridcell')).toHaveLength(7);
  });

  it('asks for the month of the user and shows how many entries each day holds', async () => {
    const { requests } = serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([entry(), task()], {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    expect(await screen.findByRole('heading', { level: 1, name: 'Judicial calendar' })).toBeInTheDocument();
    expect(screen.getByText('Days and times are in Kuwait time (Asia/Kuwait).')).toBeInTheDocument();
    const busy = await screen.findByRole('gridcell', { name: '10 Mar 2027: 2 entries' });
    expect(busy).toHaveAttribute('data-busy', 'true');
    expect(screen.getByRole('gridcell', { name: '11 Mar 2027: 0 entries' })).toHaveAttribute('data-busy', 'false');
    const call = calls(requests)[0];
    expect([call?.query.get('from'), call?.query.get('to'), call?.query.get('userId')]).toEqual(['2027-03-01', '2027-03-31', 'me']);
    expect(screen.getByRole('button', { name: 'Month' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('button', { name: 'New date' })).not.toBeInTheDocument();
  });

  it('marks a day with a conflict and opens a day from the grid', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) =>
      r.path === '/api/v1/calendar/events'
        ? jsonResponse(view([entry({ conflict: true }), entry({ id: 'HEARING:h2', refId: 'h2', conflict: true })], {}, r))
        : undefined,
    );
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    const cell = await screen.findByRole('gridcell', { name: '10 Mar 2027: 2 entries' });
    expect(cell).toHaveAttribute('data-conflict', 'true');
    await user.click(cell);
    expect(await screen.findByRole('button', { name: 'Day' })).toHaveAttribute('aria-pressed', 'true');
    await waitFor(() => {
      const last = calls(requests).at(-1);
      expect([last?.query.get('from'), last?.query.get('to')]).toEqual(['2027-03-10', '2027-03-10']);
    });
  });

  it('moves to the next and previous month and back to today', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([], {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-31');
    await screen.findByRole('gridcell', { name: '31 Mar 2027: 0 entries' });
    await user.click(screen.getByRole('button', { name: 'Next period' }));
    await waitFor(() => { expect(calls(requests).at(-1)?.query.get('from')).toBe('2027-04-01'); });
    await user.click(screen.getByRole('button', { name: 'Previous period' }));
    await user.click(screen.getByRole('button', { name: 'Previous period' }));
    await waitFor(() => { expect(calls(requests).at(-1)?.query.get('from')).toBe('2027-02-01'); });
    await user.click(screen.getByRole('button', { name: 'Today' }));
    await waitFor(() => {
      const from = calls(requests).at(-1)?.query.get('from') ?? '';
      expect(from).toMatch(/^\d{4}-\d{2}-01$/);
      expect(from).not.toBe('2027-02-01');
    });
  });
});

describe('week, day and list views', () => {
  it('shows the week from Sunday to Saturday, a section for each day', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([entry(), task()], {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    await screen.findByRole('gridcell', { name: '10 Mar 2027: 2 entries' });
    await user.click(screen.getByRole('button', { name: 'Week' }));
    const section = await screen.findByRole('region', { name: '10 Mar 2027' });
    expect(within(section).getAllByRole('listitem')).toHaveLength(2);
    expect(screen.getAllByRole('region').filter((r) => r.classList.contains('wp6-day'))).toHaveLength(7);
    await waitFor(() => {
      const last = calls(requests).at(-1);
      expect([last?.query.get('from'), last?.query.get('to')]).toEqual(['2027-03-07', '2027-03-13']);
    });
    expect(screen.getByRole('heading', { level: 2, name: '7 Mar 2027 – 13 Mar 2027' })).toBeInTheDocument();
  });

  it('describes each entry: time, kind, case, owner and what needs attention', async () => {
    serve((r) =>
      r.path === '/api/v1/calendar/events'
        ? jsonResponse(
            view(
              [
                entry({ endTime: '10:30', conflict: true, closedCaseFuture: true }),
                task({ status: 'LATE' }),
                entry({ id: 'CALENDAR_DATE:d1', type: 'APPEAL_DEADLINE', refType: 'CALENDAR_DATE', title: 'Appeal date', startTime: null, allDay: true, status: 'CANCELLED', caseId: null, caseReferenceNo: null, ownerName: null }),
                entry({ id: 'EXPERT_SESSION:e1', type: 'EXPERT_SESSION', status: 'DONE' }),
                entry({ id: 'DEADLINE:d9', type: 'DEADLINE', refType: 'DEADLINE', title: 'File the appeal', startTime: null, allDay: true, status: 'MISSED' }),
              ],
              {},
              r,
            ),
          )
        : undefined,
    );
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10&mode=DAY');
    const section = await screen.findByRole('region', { name: '10 Mar 2027' });
    const items = within(section).getAllByRole('listitem');
    expect(items).toHaveLength(5);
    expect(items[0]).toHaveTextContent('09:30–10:30');
    expect(items[0]).toHaveTextContent('Hearing');
    expect(items[0]).toHaveTextContent('Rent dispute');
    expect(items[0]).toHaveTextContent('Mona Saleh');
    expect(items[0]).toHaveTextContent('Conflict');
    expect(items[0]).toHaveTextContent('Closed case: verify before cancelling');
    expect(within(items[0] as HTMLElement).getByRole('link', { name: 'LD-2027-00001' })).toHaveAttribute('href', expect.stringContaining(`/cases/${CASE}`));
    expect(items[1]).toHaveTextContent('All day');
    expect(items[1]).toHaveTextContent('Task');
    expect(items[1]).toHaveTextContent('Prepare the memorandum');
    expect(items[1]).toHaveTextContent('Late');
    expect(items[2]).toHaveTextContent('Appeal or grievance date');
    expect(items[2]).toHaveTextContent('Cancelled');
    expect(within(items[2] as HTMLElement).queryByRole('link')).not.toBeInTheDocument();
    expect(items[3]).toHaveTextContent('Done');
    expect(items[4]).toHaveTextContent('Legal deadline');
    expect(items[4]).toHaveTextContent('File the appeal');
    expect(items[4]).toHaveTextContent('Missed');
  });

  it('lists the period by date, and says when nothing is on the calendar', async () => {
    const user = userEvent.setup();
    let events = [entry(), entry({ id: 'HEARING:h3', refId: 'h3', date: '2027-03-12' })];
    serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view(events, {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10&mode=LIST');
    expect(await screen.findByRole('region', { name: '10 Mar 2027' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '12 Mar 2027' })).toBeInTheDocument();
    events = [];
    await user.click(screen.getByRole('button', { name: 'Next period' }));
    expect(await screen.findByText('Nothing on the calendar.')).toBeInTheDocument();
  });

  it('says so when a day holds nothing', async () => {
    serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([], {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10&mode=DAY');
    expect(await screen.findByText('Nothing on the calendar.')).toBeInTheDocument();
  });

  it('ignores a date or mode in the link that it does not understand', async () => {
    const { requests } = serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([], {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=tomorrow&mode=YEAR');
    expect(await screen.findByRole('button', { name: 'Month' })).toHaveAttribute('aria-pressed', 'true');
    expect(calls(requests)[0]?.query.get('from')).toMatch(/^\d{4}-\d{2}-01$/);
  });
});

describe('filters', () => {
  it('switches to the general calendar and filters by kind', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([], {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    await screen.findByRole('gridcell', { name: '10 Mar 2027: 0 entries' });
    expect(screen.getByRole('button', { name: 'My calendar' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'General calendar' }));
    await waitFor(() => { expect(calls(requests).at(-1)?.query.has('userId')).toBe(false); });
    expect(screen.getByRole('button', { name: 'General calendar' })).toHaveAttribute('aria-pressed', 'true');
    await user.selectOptions(screen.getByLabelText('Kind'), 'Task');
    await waitFor(() => { expect(calls(requests).at(-1)?.query.getAll('type')).toEqual(['TASK']); });
    await user.selectOptions(screen.getByLabelText('Kind'), 'All');
    await waitFor(() => { expect(calls(requests).at(-1)?.query.has('type')).toBe(false); });
    await user.click(screen.getByRole('button', { name: 'My calendar' }));
    await waitFor(() => { expect(calls(requests).at(-1)?.query.get('userId')).toBe('me'); });
  });
});

describe('conflicts', () => {
  it('lists conflicting dates with the alternate member of each case', async () => {
    const conflict: Conflict = {
      ownerId: 'u1',
      ownerName: 'Mona Saleh',
      date: '2027-03-10',
      startsAt: '2027-03-10T06:30:00Z',
      entries: [entry({ conflict: true }), entry({ id: 'HEARING:h2', refId: 'h2', conflict: true, alternateMemberId: null, alternateMemberName: null })],
    };
    serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view(conflict.entries, { conflicts: [conflict] }, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    const panel = await screen.findByRole('region', { name: 'Conflicting dates' });
    expect(panel).toHaveTextContent('Mona Saleh on 10 Mar 2027: 2 dates at the same time');
    expect(panel).toHaveTextContent('Alternate member: Omar Adel');
    expect(panel).toHaveTextContent('No alternate member on the case');
  });
});

describe('one-click reassignment on a conflict', () => {
  const conflict: Conflict = {
    ownerId: 'u1',
    ownerName: 'Mona Saleh',
    date: '2027-03-10',
    startsAt: '2027-03-10T06:30:00Z',
    entries: [entry({ conflict: true }), entry({ id: 'HEARING:h2', refId: 'h2', conflict: true, alternateMemberId: null, alternateMemberName: null })],
  };

  it('hands the hearing to the alternate member and reloads; a refusal is shown', async () => {
    const user = userEvent.setup();
    let refuse = false;
    const { requests } = serve((r) => {
      if (r.path.endsWith('/reassign')) return refuse ? problem(409) : jsonResponse({});
      return r.path === '/api/v1/calendar/events' ? jsonResponse(view(conflict.entries, { conflicts: [conflict] }, r)) : undefined;
    });
    mount(<CalendarPage />, [...VIEW, 'HEARING_MANAGE'], '/calendar?date=2027-03-10');
    const panel = await screen.findByRole('region', { name: 'Conflicting dates' });
    expect(within(panel).getAllByRole('button')).toHaveLength(1);
    refuse = true;
    await user.click(within(panel).getByRole('button', { name: 'Hand to Omar Adel' }));
    expect(await within(panel).findByRole('alert')).toBeInTheDocument();
    refuse = false;
    await user.click(within(panel).getByRole('button', { name: 'Hand to Omar Adel' }));
    await waitFor(() => { expect(requests.filter((r) => r.path.endsWith('/reassign') && r.method === 'POST').length).toBe(2); });
    const sent = requests.find((r) => r.path.endsWith('/reassign'))?.body as { memberId: string } | null | undefined;
    expect(typeof sent?.memberId).toBe('string');
  });

  it('offers no button without the right to manage hearings', async () => {
    serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view(conflict.entries, { conflicts: [conflict] }, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    const panel = await screen.findByRole('region', { name: 'Conflicting dates' });
    expect(within(panel).queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('manual dates', () => {
  it('adds a date of the office and reloads', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => {
      if (r.path === '/api/v1/calendar/dates') return jsonResponse({ id: 'd1', kind: 'MEETING', title: 'Meeting', caseId: null, date: '2027-03-10', status: 'ACTIVE', version: 0 }, 201);
      return r.path === '/api/v1/calendar/events' ? jsonResponse(view([], {}, r)) : undefined;
    });
    mount(<CalendarPage />, MANAGE, '/calendar?date=2027-03-10');
    await user.click(await screen.findByRole('button', { name: 'New date' }));
    const form = screen.getByRole('form', { name: 'New date' });
    await user.type(within(form).getByLabelText('Title'), 'Department meeting');
    fireEvent.change(within(form).getByLabelText('Start time (optional)'), { target: { value: '13:00' } });
    fireEvent.change(within(form).getByLabelText('End time (optional)'), { target: { value: '14:00' } });
    await user.click(within(form).getByRole('button', { name: 'Add the date' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The date was added.');
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      kind: 'MEETING', title: 'Department meeting', notes: null, caseId: null, date: '2027-03-10', startTime: '13:00', endTime: '14:00',
    });
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(calls(requests).length).toBeGreaterThanOrEqual(2);
  });

  it('explains what the server refuses and can be cancelled', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [{ field: 'endTime', code: 'RequiresStartTime' }, { field: 'title', code: 'NotBlank' }] }),
      problem(400, { errors: [{ field: 'endTime', code: 'AfterStartTime' }] }),
      problem(422, { rule: 'OWNER_NOT_ASSIGNABLE' }),
    ];
    serve((r) => {
      if (r.method === 'POST') return answers.shift();
      return r.path === '/api/v1/calendar/events' ? jsonResponse(view([], {}, r)) : undefined;
    });
    mount(<CalendarPage />, MANAGE, '/calendar?date=2027-03-10');
    await user.click(await screen.findByRole('button', { name: 'New date' }));
    const form = screen.getByRole('form', { name: 'New date' });
    await user.click(within(form).getByRole('button', { name: 'Add the date' }));
    expect(await within(form).findByText('Give a start time before an end time.')).toBeVisible();
    expect(within(form).getByText('This field is required.')).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Add the date' }));
    expect(await within(form).findByText('The end time must be after the start time.')).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Add the date' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Dates are given to enabled users only.');
    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });
});

describe('failures and languages', () => {
  it('shows the access-denied page when the server refuses the calendar', async () => {
    serve((r) => (r.path === '/api/v1/calendar/events' ? problem(403) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    expect(await screen.findByRole('heading', { level: 1, name: /access|permission/i })).toBeInTheDocument();
  });

  it('explains a failed load and tries again', async () => {
    const user = userEvent.setup();
    let fail = true;
    serve((r) => {
      if (r.path !== '/api/v1/calendar/events') return undefined;
      return fail ? problem(500) : jsonResponse(view([], {}, r));
    });
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    const alert = await screen.findByRole('alert');
    fail = false;
    await user.click(within(alert).getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('gridcell', { name: '10 Mar 2027: 0 entries' })).toBeInTheDocument();
  });

  it('says when too many entries were cut', async () => {
    serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([entry()], { truncated: true }, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10');
    expect(await screen.findByText(/Too many entries/)).toBeInTheDocument();
  });

  it('shows Arabic labels right to left', async () => {
    serve((r) => (r.path === '/api/v1/calendar/events' ? jsonResponse(view([entry()], {}, r)) : undefined));
    mount(<CalendarPage />, VIEW, '/calendar?date=2027-03-10&mode=DAY&lang=ar');
    expect(await screen.findByRole('heading', { level: 1, name: 'التقويم القضائي' })).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(await screen.findByRole('button', { name: 'يومي' })).toHaveAttribute('aria-pressed', 'true');
    expect(await screen.findByText('جلسة', { selector: 'strong' })).toBeInTheDocument();
  });
});
