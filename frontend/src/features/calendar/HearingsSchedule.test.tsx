import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import type { Permission } from '../../api/permissions';
import { jsonResponse } from '../../test/http';
import { mount, page, problem, serve, type Request } from '../followup/testSupport';
import { hearing } from '../operations/testSupport';
import { setCatalog } from '../../catalog/catalogStore';
import { CalendarPage } from './CalendarPage';

/** Requirements section 15: the hearings schedule is a table view of the calendar (owner decision, round 5). */

const VIEW: Permission[] = ['CALENDAR_VIEW'];
const WITH_HEARINGS: Permission[] = ['CALENDAR_VIEW', 'HEARING_VIEW'];

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

const EMPTY_VIEW = {
  view: 'MONTH',
  from: '2027-03-01',
  to: '2027-03-31',
  zone: 'Asia/Kuwait',
  events: [],
  conflicts: [],
  days: [],
  truncated: false,
};

function backend(hearings: (request: Request) => Response | undefined = () => page([hearing()])) {
  return serve((r) => {
    if (r.path === '/api/v1/calendar/events') return jsonResponse(EMPTY_VIEW);
    if (r.path === '/api/v1/hearings') return hearings(r);
    return undefined;
  });
}

const hearingCalls = (requests: Request[]) => requests.filter((r) => r.path === '/api/v1/hearings');

describe('the hearings schedule table in the calendar', () => {
  it('is offered only to users who may view hearings', async () => {
    backend();
    mount(<CalendarPage />, VIEW);
    expect(await screen.findByRole('heading', { name: 'Judicial calendar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Hearings schedule (table)' })).not.toBeInTheDocument();
  });

  it('does not open the table from the address without HEARING_VIEW', async () => {
    const { requests } = backend();
    mount(<CalendarPage />, VIEW, '/calendar?view=hearings');
    expect(await screen.findByRole('group', { name: 'View' })).toBeInTheDocument();
    expect(hearingCalls(requests)).toHaveLength(0);
  });

  it('shows date, time, case link, court, number, purpose and status, and sends no calendar request while open', async () => {
    const user = userEvent.setup();
    const { requests } = backend(() =>
      page([hearing(), hearing({ id: 'h2', hearingTime: null, caseTitle: 'Inheritance', purpose: 'JUDGMENT', status: 'HELD', awaitingResult: true })]),
    );
    mount(<CalendarPage />, WITH_HEARINGS);
    await user.click(await screen.findByRole('button', { name: 'Hearings schedule (table)' }));

    const table = await screen.findByRole('table', { name: 'Hearings schedule' });
    expect(screen.getByRole('button', { name: 'Hearings schedule (table)' })).toHaveAttribute('aria-pressed', 'true');
    const [first, second] = within(table).getAllByRole('row').slice(1);
    expect(first).toHaveTextContent('09:30');
    expect(first).toHaveTextContent('Rent dispute');
    expect(first).toHaveTextContent('Sample First-Instance Court');
    expect(first).toHaveTextContent('1234/2025');
    expect(first).toHaveTextContent('Pleading');
    expect(first).toHaveTextContent('Scheduled');
    expect(within(first as HTMLElement).getByRole('link', { name: 'LD-2025-00042' })).toHaveAttribute(
      'href',
      expect.stringContaining('/cases/'),
    );
    expect(second).toHaveTextContent('No time set');
    expect(second).toHaveTextContent('Held');
    // A schedule, not a place to enter or change hearings: no action buttons in the table.
    expect(within(table).queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Hearings schedule' })).toHaveAttribute('tabindex', '0');

    const [call] = hearingCalls(requests);
    expect(call?.query.get('page')).toBe('0');
    expect(call?.query.get('size')).toBe('20');
    expect(call?.query.has('status')).toBe(false);
    expect(call?.query.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('opens straight on the table from /calendar?view=hearings and goes back to the calendar', async () => {
    const user = userEvent.setup();
    backend();
    mount(<CalendarPage />, WITH_HEARINGS, '/calendar?view=hearings');
    expect(await screen.findByRole('table', { name: 'Hearings schedule' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Calendar' }));
    expect(await screen.findByRole('group', { name: 'View' })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Hearings schedule' })).not.toBeInTheDocument();
  });

  it('filters by period and status and pages through the results', async () => {
    const user = userEvent.setup();
    const { requests } = backend((r) => {
      const number = Number(r.query.get('page'));
      return page([hearing({ id: `h${String(number)}`, caseTitle: `Case ${String(number)}` })], { page: number, totalItems: 30, totalPages: 2 });
    });
    mount(<CalendarPage />, WITH_HEARINGS, '/calendar?view=hearings');
    expect(await screen.findByText('Case 0')).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(await screen.findByText('Case 1')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2027-02-01' } });
    await user.selectOptions(screen.getByLabelText('Status'), 'ADJOURNED');
    await waitFor(() => {
      const last = hearingCalls(requests).at(-1);
      expect(last?.query.get('from')).toBe('2027-02-01');
      expect(last?.query.get('status')).toBe('ADJOURNED');
      // A new filter starts again on the first page.
      expect(last?.query.get('page')).toBe('0');
    });
  });

  it('says when nothing matches, explains a refused range and a refused permission', async () => {
    const user = userEvent.setup();
    let answer: Response = page([]);
    backend(() => answer.clone());
    mount(<CalendarPage />, WITH_HEARINGS, '/calendar?view=hearings');
    expect(await screen.findByText('No hearings in this period.')).toBeInTheDocument();

    answer = problem(400, { errors: [{ field: 'to', code: 'Range' }] });
    await user.selectOptions(screen.getByLabelText('Status'), 'HELD');
    expect(await screen.findByRole('alert')).toHaveTextContent('The end date must be on or after the start date');

    // Out of scope: the backend answers 403 and the table shows nothing of any case.
    answer = problem(403);
    await user.selectOptions(screen.getByLabelText('Status'), 'CANCELLED');
    expect(await screen.findByRole('alert')).toHaveTextContent('You are not permitted to view this.');
    expect(screen.queryByRole('table', { name: 'Hearings schedule' })).not.toBeInTheDocument();
  });

  it('is written in Arabic with Arabic-Indic digits', async () => {
    backend();
    mount(<CalendarPage />, WITH_HEARINGS, '/calendar?view=hearings&lang=ar');
    const row = within(await screen.findByRole('table', { name: 'جدول الجلسات' })).getAllByRole('row')[1];
    expect(row).toHaveTextContent('محكمة أول درجة نموذجية');
    expect(row).toHaveTextContent('مرافعة');
    expect(row).toHaveTextContent('٠٩:٣٠');
    expect(screen.getByRole('button', { name: 'جدول الجلسات' })).toHaveAttribute('aria-pressed', 'true');
  });
});

describe('the hearings table shows the labels the administrator manages (LD-DOC-13)', () => {
  const entry = (code: string, labelAr: string, labelEn: string, sortOrder: number) => ({ code, labelAr, labelEn, active: true, sortOrder });

  it('uses the edited status and purpose labels, and the built-in ones again when the catalogue is empty', async () => {
    const user = userEvent.setup();
    backend(() => page([hearing()]));
    setCatalog({
      HEARING_STATUS: [entry('SCHEDULED', 'مجدولة (معدّلة)', 'Booked (edited by the administrator)', 1)],
      HEARING_PURPOSE: [entry('PLEADING', 'مرافعة (معدّلة)', 'Oral pleading (edited)', 1)],
    });
    try {
      mount(<CalendarPage />, WITH_HEARINGS);
      await user.click(await screen.findByRole('button', { name: 'Hearings schedule (table)' }));
      const table = await screen.findByRole('table', { name: 'Hearings schedule' });
      const row = within(table).getAllByRole('row')[1];
      expect(row).toHaveTextContent('Booked (edited by the administrator)');
      expect(row).toHaveTextContent('Oral pleading (edited)');
      expect(row).not.toHaveTextContent('Scheduled');
      // The status filter offers the same edited label.
      expect(within(screen.getByLabelText('Status')).getByRole('option', { name: 'Booked (edited by the administrator)' })).toBeInTheDocument();
    } finally {
      setCatalog({});
    }
  });

  it('falls back to the built-in labels when the catalogue cannot be read', async () => {
    const user = userEvent.setup();
    backend(() => page([hearing()]));
    setCatalog({});
    mount(<CalendarPage />, WITH_HEARINGS);
    await user.click(await screen.findByRole('button', { name: 'Hearings schedule (table)' }));
    const row = within(await screen.findByRole('table', { name: 'Hearings schedule' })).getAllByRole('row')[1];
    expect(row).toHaveTextContent('Scheduled');
    expect(row).toHaveTextContent('Pleading');
  });
});
