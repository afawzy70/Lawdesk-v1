import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { hearing, page, problem, renderOperations, scriptedFetch, type Request } from '../testSupport';

/** LD-024 hearing calendar (docs/delivery/PHASE-5-ACCEPTANCE.md, QA-0192). */

// 22:30 UTC on 1 March is already 2 March in Cairo: the calendar starts on the Egyptian date.
const NOW = new Date('2025-03-01T22:30:00Z');

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  document.cookie = 'XSRF-TOKEN=test-token';
});

afterEach(() => {
  vi.useRealTimers();
});

const calendarCalls = (requests: Request[]) =>
  requests.filter((r) => r.method === 'GET' && r.path === '/api/v1/hearings');

describe('hearing calendar', () => {
  it('lists the next 30 days from the Egyptian date by default', async () => {
    const { requests } = scriptedFetch((r) =>
      r.path === '/api/v1/hearings'
        ? page([
            hearing(),
            hearing({
              id: 'h2',
              hearingTime: null,
              caseReferenceNo: 'OLD/1999/very-long-reference-number-that-wraps',
              caseTitle: 'Inheritance',
              purpose: 'JUDGMENT',
              awaitingResult: true,
            }),
          ])
        : undefined,
    );
    renderOperations('/hearings?lang=en');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hearing calendar' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Hearing calendar' });
    const [first, second] = within(table).getAllByRole('row').slice(1);
    expect(first).toHaveTextContent('Sun, 2 Mar 2025');
    expect(first).toHaveTextContent('09:30');
    expect(first).toHaveTextContent('Rent dispute');
    expect(first).toHaveTextContent('Sample First-Instance Court');
    expect(first).toHaveTextContent('1234/2025');
    expect(first).toHaveTextContent('Pleading');
    expect(first).toHaveTextContent('Scheduled');
    expect(within(first as HTMLElement).getByRole('link', { name: 'LD-2025-00042' })).toHaveAttribute(
      'href',
      '/cases/c0000000-0000-4000-8000-000000000001?lang=en',
    );
    expect(second).toHaveTextContent('No time set');
    expect(second).toHaveTextContent('Pronouncement of judgment');
    expect(second).toHaveTextContent('Awaiting result');
    expect(screen.getByRole('region', { name: 'Hearing calendar' })).toHaveAttribute('tabindex', '0');
    expect(document.title).toBe('Hearing calendar — LawDesk (Preview)');

    const [call] = calendarCalls(requests);
    expect(call?.query.get('from')).toBe('2025-03-02');
    expect(call?.query.get('to')).toBe('2025-04-01');
    expect(call?.query.get('page')).toBe('0');
    expect(call?.query.get('size')).toBe('20');
    expect(call?.query.has('status')).toBe(false);
  });

  it('filters by dates and status, and says when nothing matches', async () => {
    const user = userEvent.setup();
    const { requests } = scriptedFetch((r) => (r.path === '/api/v1/hearings' ? page([]) : undefined));
    renderOperations('/hearings');
    expect(await screen.findByText('No hearings in this period.')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2025-02-01' } });
    await user.selectOptions(screen.getByLabelText('Status'), 'ADJOURNED');
    await waitFor(() => {
      const last = calendarCalls(requests).at(-1);
      expect(last?.query.get('from')).toBe('2025-02-01');
      expect(last?.query.get('status')).toBe('ADJOURNED');
    });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2025-02-28' } });
    // A cleared date field keeps the last date asked for.
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '' } });
    expect(screen.getByLabelText('To')).toHaveValue('2025-02-28');
    await waitFor(() => {
      expect(calendarCalls(requests).at(-1)?.query.get('to')).toBe('2025-02-28');
    });
    await user.selectOptions(screen.getByLabelText('Status'), '');
    await waitFor(() => {
      expect(calendarCalls(requests).at(-1)?.query.has('status')).toBe(false);
    });
  });

  it('pages through long periods', async () => {
    const user = userEvent.setup();
    const { requests } = scriptedFetch((r) => {
      if (r.path !== '/api/v1/hearings') return undefined;
      const number = Number(r.query.get('page'));
      return page([hearing({ id: `h${String(number)}`, caseTitle: `Case ${String(number)}` })], {
        page: number,
        totalItems: 30,
        totalPages: 2,
      });
    });
    renderOperations('/hearings');
    expect(await screen.findByText('Case 0')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(await screen.findByText('Case 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(await screen.findByText('Case 0')).toBeInTheDocument();
    expect(calendarCalls(requests).map((r) => r.query.get('page'))).toEqual(['0', '1', '0']);
  });

  it('shows the access-denied page when HEARING_VIEW is refused', async () => {
    scriptedFetch((r) => (r.path === '/api/v1/hearings' ? problem(403) : undefined));
    renderOperations('/hearings');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });

  it('explains a refused date range and an expired session', async () => {
    let answer = problem(400, { errors: [{ field: 'to', code: 'Range' }] });
    scriptedFetch((r) => (r.path === '/api/v1/hearings' ? answer.clone() : undefined));
    const user = userEvent.setup();
    renderOperations('/hearings');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The end date must be on or after the start date, and at most 366 days later.',
    );
    answer = problem(401);
    await user.selectOptions(screen.getByLabelText('Status'), 'HELD');
    expect(await screen.findByText('Your session has ended. Sign in again.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('offers a retry when the API is unreachable', async () => {
    const user = userEvent.setup();
    let fail = true;
    scriptedFetch((r) => {
      if (r.path !== '/api/v1/hearings') return undefined;
      return fail ? problem(500) : page([hearing()]);
    });
    renderOperations('/hearings');
    expect(await screen.findByText('The data could not be loaded. Check the connection and try again.')).toBeVisible();
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('table', { name: 'Hearing calendar' })).toBeInTheDocument();
  });

  it('shows Arabic labels, dates and digits right to left', async () => {
    scriptedFetch((r) => (r.path === '/api/v1/hearings' ? page([hearing({ status: 'ADJOURNED' })]) : undefined));
    renderOperations('/hearings?lang=ar');
    expect(await screen.findByRole('heading', { level: 1, name: 'جدول الجلسات' })).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    const row = within(await screen.findByRole('table', { name: 'جدول الجلسات' })).getAllByRole('row')[1];
    expect(row).toHaveTextContent('محكمة أول درجة نموذجية');
    expect(row).toHaveTextContent('مرافعة');
    expect(row).toHaveTextContent('تأجلت');
    expect(row).toHaveTextContent('٠٩:٣٠');
    expect(screen.getByLabelText('من')).toBeInTheDocument();
    // A hearing with a result offers no actions.
    expect(within(row as HTMLElement).queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('recording results from the calendar', () => {
  function serveCalendar(onPost: (r: Request) => Response) {
    return scriptedFetch((r) => {
      if (r.method === 'GET' && r.path === '/api/v1/hearings') return page([hearing()]);
      if (r.method !== 'GET') return onPost(r);
      return undefined;
    });
  }

  it('records a held hearing with its decision and reloads the calendar', async () => {
    const user = userEvent.setup();
    const { requests } = serveCalendar(() => jsonResponse(hearing({ status: 'HELD' })));
    renderOperations('/hearings');
    await user.click(
      await screen.findByRole('button', { name: 'Record the result of the hearing on Sun, 2 Mar 2025, case LD-2025-00042' }),
    );
    const form = screen.getByRole('form', { name: 'Result of the hearing on Sun, 2 Mar 2025 (case LD-2025-00042)' });
    expect(within(form).getByRole('radio', { name: 'Held' })).toBeChecked();
    expect(within(form).queryByLabelText('Next hearing date')).not.toBeInTheDocument();
    await user.type(within(form).getByLabelText('Decision of the court (optional)'), ' Reserved for judgment ');
    await user.click(within(form).getByRole('button', { name: 'Record result' }));

    expect(await screen.findByRole('status')).toHaveTextContent('The hearing was saved.');
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.path).toBe('/api/v1/hearings/h1/result');
    expect(post?.body).toEqual({
      status: 'HELD',
      decision: 'Reserved for judgment',
      nextHearingDate: null,
      nextHearingTime: null,
      nextHearingPurpose: null,
      version: 0,
    });
    expect(calendarCalls(requests)).toHaveLength(2);
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('adjourns to a next hearing and shows field errors and rules from the backend', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [{ field: 'nextHearingDate', code: 'NotNull' }] }),
      problem(422, { rule: 'ADJOURNMENT_NOT_LATER' }),
      problem(409),
      jsonResponse(hearing({ status: 'ADJOURNED' })),
    ];
    const { requests } = serveCalendar(() => answers.shift() ?? problem(500));
    renderOperations('/hearings');
    await user.click(await screen.findByRole('button', { name: /^Record the result/ }));
    const form = screen.getByRole('form', { name: /^Result of the hearing/ });
    await user.click(within(form).getByRole('radio', { name: 'Adjourned' }));
    await user.click(within(form).getByRole('button', { name: 'Record result' }));

    const date = within(form).getByLabelText('Next hearing date');
    expect(await within(form).findByText('This field is required.')).toBeInTheDocument();
    expect(date).toHaveAttribute('aria-invalid', 'true');
    expect(date).toHaveAccessibleDescription('This field is required.');
    expect(within(form).getByRole('alert')).toHaveTextContent('Some fields need attention.');

    await user.type(date, '2025-03-01');
    await user.click(within(form).getByRole('button', { name: 'Record result' }));
    expect(await within(form).findByText('The next hearing must be after the hearing it continues.')).toBeVisible();

    await user.clear(date);
    await user.type(date, '2025-04-06');
    await user.type(within(form).getByLabelText('Next hearing time (optional)'), '10:00');
    await user.selectOptions(within(form).getByLabelText('Next hearing purpose'), 'EVIDENCE');
    await user.click(within(form).getByRole('button', { name: 'Record result' }));
    expect(
      await within(form).findByText(
        'The hearing was changed meanwhile, or its next hearing day is already taken. Reload and check.',
      ),
    ).toBeVisible();

    await user.click(within(form).getByRole('button', { name: 'Record result' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The hearing was saved.');
    expect(requests.filter((r) => r.method === 'POST').at(-1)?.body).toEqual({
      status: 'ADJOURNED',
      decision: null,
      nextHearingDate: '2025-04-06',
      nextHearingTime: '10:00',
      nextHearingPurpose: 'EVIDENCE',
      version: 0,
    });
  });

  it('cancels a future hearing and explains a rule it breaks', async () => {
    const user = userEvent.setup();
    const answers = [problem(422, { rule: 'CASE_ARCHIVED' }), problem(422, { rule: 'SOMETHING_NEW' }), problem(403)];
    serveCalendar(() => answers.shift() ?? problem(500));
    renderOperations('/hearings');
    await user.click(await screen.findByRole('button', { name: /^Record the result/ }));
    const form = screen.getByRole('form', { name: /^Result of the hearing/ });
    await user.click(within(form).getByRole('radio', { name: 'Cancelled' }));
    const submit = within(form).getByRole('button', { name: 'Record result' });
    await user.click(submit);
    expect(await within(form).findByText('The case is archived and can no longer change.')).toBeVisible();
    await user.click(submit);
    expect(await within(form).findByText('The change breaks a rule of the legal record and was not saved.')).toBeVisible();
    await user.click(submit);
    expect(await within(form).findByText('You are not permitted to make this change.')).toBeVisible();
    await user.click(submit);
    expect(await within(form).findByText('The change could not be saved. Try again.')).toBeVisible();

    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('reschedules a hearing with its version and explains a stale one', async () => {
    const user = userEvent.setup();
    const answers = [problem(409), jsonResponse(hearing({ hearingDate: '2025-03-09' }))];
    const { requests } = serveCalendar(() => answers.shift() ?? problem(500));
    renderOperations('/hearings');
    await user.click(
      await screen.findByRole('button', { name: 'Reschedule the hearing on Sun, 2 Mar 2025, case LD-2025-00042' }),
    );
    const form = screen.getByRole('form', { name: 'Reschedule the hearing on Sun, 2 Mar 2025 (case LD-2025-00042)' });
    expect(within(form).getByLabelText('Date')).toHaveValue('2025-03-02');
    expect(within(form).getByLabelText('Time (optional)')).toHaveValue('09:30');
    expect(within(form).getByLabelText('Purpose')).toHaveValue('PLEADING');
    await user.clear(within(form).getByLabelText('Date'));
    await user.type(within(form).getByLabelText('Date'), '2025-03-09');
    await user.clear(within(form).getByLabelText('Time (optional)'));
    await user.type(within(form).getByLabelText('Preparation notes (optional)'), 'Bring the lease');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(
      await within(form).findByText('Someone else changed this record meanwhile. Reload and try again.'),
    ).toBeVisible();

    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The hearing was saved.');
    const put = requests.filter((r) => r.method === 'PUT').at(-1);
    expect(put?.path).toBe('/api/v1/hearings/h1');
    expect(put?.body).toEqual({
      hearingDate: '2025-03-09',
      hearingTime: null,
      purpose: 'PLEADING',
      notes: 'Bring the lease',
      version: 0,
    });
  });
});
