import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { CASE, hearing, page, problem, proceeding, renderOperations, scriptedFetch, type Request } from '../testSupport';

/** LD-024 hearings in the case's operations section (docs/delivery/PHASE-5-ACCEPTANCE.md, QA-0192). */

const HEARINGS = `/api/v1/cases/${CASE}/hearings`;
const PROCEEDINGS = `/api/v1/cases/${CASE}/proceedings`;
const APPEAL = proceeding({ id: 'p2', stage: 'APPEAL', courtCode: 'AP-1', courtCaseNumber: '7', parentProceedingId: 'p1' });

interface Backend {
  hearings?: Response;
  proceedings?: Response;
  onWrite?: (request: Request) => Response;
}

/** Reads `backend` on every request, so a test can change its answers (getters) between requests. */
function serve(backend: Backend = {}) {
  return scriptedFetch((r) => {
    if (r.method !== 'GET') return backend.onWrite?.(r);
    if (r.path === HEARINGS) return (backend.hearings ?? page([])).clone();
    if (r.path === PROCEEDINGS) return (backend.proceedings ?? page([proceeding(), APPEAL])).clone();
    return undefined;
  });
}

const section = async () => screen.findByRole('region', { name: 'Hearings' });

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('case hearings', () => {
  it('lists the hearings of the case with their proceeding, result and notes', async () => {
    const { requests } = serve({
      hearings: page([
        hearing({ status: 'HELD', decision: 'Reserved for judgment', notes: 'Bring the lease', hearingTime: null }),
        hearing({ id: 'h2', hearingDate: '2025-04-06', awaitingResult: true }),
      ]),
    });
    renderOperations(`/cases/${CASE}`);
    expect(await screen.findByRole('heading', { level: 2, name: 'Legal operations' })).toBeInTheDocument();
    const region = await section();
    const items = await within(region).findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Sun, 2 Mar 2025 · Pleading');
    expect(items[0]).toHaveTextContent('First instance no. 1234/2025, Sample First-Instance Court');
    expect(items[0]).toHaveTextContent('Held');
    expect(items[0]).toHaveTextContent('Reserved for judgment');
    expect(items[0]).toHaveTextContent('Bring the lease');
    expect(within(items[0] as HTMLElement).queryByRole('button')).not.toBeInTheDocument();
    expect(items[1]).toHaveTextContent('09:30');
    expect(items[1]).toHaveTextContent('Awaiting result');
    expect(requests.find((r) => r.path === HEARINGS)?.query.get('size')).toBe('100');
  });

  it('schedules a hearing on a chosen proceeding', async () => {
    const user = userEvent.setup();
    let hearings = page([]);
    const { requests } = serve({
      get hearings() {
        return hearings;
      },
      onWrite: () => {
        hearings = page([hearing()]);
        return jsonResponse(hearing(), 201);
      },
    });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    expect(await within(region).findByText('No hearings recorded for this case yet.')).toBeInTheDocument();
    await user.click(within(region).getByRole('button', { name: 'Schedule hearing' }));
    const form = within(region).getByRole('form', { name: 'Schedule hearing' });

    // No proceeding chosen: nothing is sent.
    await user.click(within(form).getByRole('button', { name: 'Schedule' }));
    expect(within(form).getByLabelText('Proceeding')).toHaveAttribute('aria-invalid', 'true');
    expect(within(form).getByLabelText('Proceeding')).toHaveAccessibleDescription('This field is required.');
    expect(requests.filter((r) => r.method === 'POST')).toHaveLength(0);

    await user.selectOptions(within(form).getByLabelText('Proceeding'), 'Appeal no. 7/2025 (AP-1)');
    await user.type(within(form).getByLabelText('Date'), '2025-03-02');
    await user.type(within(form).getByLabelText('Time (optional)'), '09:30');
    await user.selectOptions(within(form).getByLabelText('Purpose'), 'FIRST_SESSION');
    await user.type(within(form).getByLabelText('Preparation notes (optional)'), '  ');
    await user.click(within(form).getByRole('button', { name: 'Schedule' }));

    expect(await within(region).findByRole('status')).toHaveTextContent('The hearing was saved.');
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.path).toBe('/api/v1/proceedings/p2/hearings');
    expect(post?.body).toEqual({ hearingDate: '2025-03-02', hearingTime: '09:30', purpose: 'FIRST_SESSION', notes: null });
    expect(await within(region).findAllByRole('listitem')).toHaveLength(1);
  });

  it('preselects the only proceeding and shows the backend field errors, 409 and 422', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [{ field: 'hearingDate', code: 'NotNull' }, { field: 'purpose', code: 'NotNull' }] }),
      problem(400, { errors: [{ field: 'hearingDate', code: 'Range' }, { field: 'notes', code: 'Size' }] }),
      problem(409),
      problem(422, { rule: 'HEARING_BEFORE_FILING' }),
      problem(404),
    ];
    serve({ proceedings: page([proceeding()]), onWrite: () => answers.shift() ?? problem(500) });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    await user.click(await within(region).findByRole('button', { name: 'Schedule hearing' }));
    const form = within(region).getByRole('form', { name: 'Schedule hearing' });
    expect(within(form).getByLabelText('Proceeding')).toHaveValue('p1');
    const submit = within(form).getByRole('button', { name: 'Schedule' });

    await user.click(submit);
    expect(await within(form).findAllByText('This field is required.')).toHaveLength(2);
    expect(within(form).getByLabelText('Purpose')).toHaveAttribute('aria-invalid', 'true');
    await user.click(submit);
    expect(await within(form).findByText('This date is out of the allowed range.')).toBeVisible();
    expect(within(form).getByText('This text is too long.')).toBeVisible();
    await user.click(submit);
    expect(await within(form).findByText('This proceeding already has a hearing on that day.')).toBeVisible();
    await user.click(submit);
    expect(await within(form).findByText('A hearing cannot be dated before the proceeding was filed.')).toBeVisible();
    await user.click(submit);
    expect(await within(form).findByText('This record no longer exists. Reload the page.')).toBeVisible();

    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(within(region).queryByRole('form')).not.toBeInTheDocument();
  });

  it('records a result and reschedules from the case', async () => {
    const user = userEvent.setup();
    const { requests } = serve({
      hearings: page([hearing()]),
      onWrite: (r) => jsonResponse(hearing({ status: r.method === 'POST' ? 'HELD' : 'SCHEDULED' })),
    });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    await user.click(await within(region).findByRole('button', { name: /^Record the result of the hearing/ }));
    await user.click(within(region).getByRole('button', { name: 'Record result' }));
    expect(await within(region).findByRole('status')).toBeInTheDocument();
    await user.click(await within(region).findByRole('button', { name: /^Reschedule the hearing/ }));
    await user.click(within(region).getByRole('button', { name: 'Save' }));
    expect(await within(region).findByRole('status')).toBeInTheDocument();
    expect(requests.filter((r) => r.method !== 'GET').map((r) => `${r.method} ${r.path}`)).toEqual([
      'POST /api/v1/hearings/h1/result',
      'PUT /api/v1/hearings/h1',
    ]);
  });

  it('explains why hearings cannot be scheduled', async () => {
    serve({ proceedings: page([]) });
    const { unmount } = renderOperations(`/cases/${CASE}`);
    expect(
      await within(await section()).findByText('Record a proceeding of this case before scheduling its hearings.'),
    ).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Schedule hearing' })).not.toBeInTheDocument();
    unmount();

    serve({ proceedings: problem(403) });
    renderOperations(`/cases/${CASE}`);
    expect(
      await within(await section()).findByText(
        'You may not view the proceedings of this case, so you cannot schedule hearings.',
      ),
    ).toBeVisible();
  });

  it('explains a refused or failed load and retries', async () => {
    const user = userEvent.setup();
    serve({ hearings: problem(403) });
    const { unmount } = renderOperations(`/cases/${CASE}`);
    expect(
      await within(await section()).findByText('You are not permitted to view the hearings of this case.'),
    ).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    unmount();

    let hearings = problem(500);
    serve({
      get hearings() {
        return hearings;
      },
    });
    renderOperations(`/cases/${CASE}?lang=ar`);
    const region = await screen.findByRole('region', { name: 'الجلسات' });
    expect(await within(region).findByText('تعذّر تحميل البيانات. تحقّق من الاتصال وأعد المحاولة.')).toBeVisible();
    hearings = page([hearing()]);
    await user.click(within(region).getByRole('button', { name: 'أعد المحاولة' }));
    expect(await within(region).findByRole('listitem')).toHaveTextContent('الأحد، ٢ مارس ٢٠٢٥ · ٠٩:٣٠ · مرافعة');
  });

  it('reports a missing case', async () => {
    serve({ hearings: problem(404) });
    renderOperations(`/cases/${CASE}`);
    expect(await within(await section()).findByText('This case no longer exists.')).toBeVisible();
  });
});
