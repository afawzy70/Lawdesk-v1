import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import {
  CASE,
  expertRecord,
  page,
  problem,
  proceeding,
  referral,
  renderOperations,
  scriptedFetch,
  type Request,
} from '../testSupport';

/** LD-025 expert referrals and sessions in the case's operations section (PHASE-5-ACCEPTANCE, QA-0200). */

const REFERRALS = `/api/v1/cases/${CASE}/expert-referrals`;

interface Backend {
  referrals?: Response;
  proceedings?: Response;
  onWrite?: (request: Request) => Response;
}

function serve(backend: Backend = {}) {
  return scriptedFetch((r) => {
    if (r.method !== 'GET') return backend.onWrite?.(r);
    if (r.path === REFERRALS) return (backend.referrals ?? page([])).clone();
    if (r.path === `/api/v1/cases/${CASE}/proceedings`) return (backend.proceedings ?? page([proceeding()])).clone();
    if (r.path === '/api/v1/experts') return page([expertRecord(), expertRecord({ id: 'e2', name: 'Hoda Adel' })]);
    if (r.path.startsWith(`/api/v1/cases/${CASE}/`) || r.path === '/api/v1/tasks') return page([]);
    return undefined;
  });
}

const section = async () => screen.findByRole('region', { name: 'Expert referrals' });

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('case expert referrals', () => {
  it('lists referrals with their expert, dates, status and sessions', async () => {
    serve({
      referrals: page([
        referral({ reportOverdue: true }),
        referral({
          id: 'r2',
          status: 'REPORT_SUBMITTED',
          reportSubmittedDate: '2025-03-20',
          sessions: [
            { id: 's1', sessionDate: '2025-02-15', sessionTime: '10:00', location: 'Site', notes: 'Parties attended', createdAt: '' },
          ],
        }),
      ]),
    });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    const items = await within(region).findAllByRole('article');
    expect(items[0]).toHaveTextContent('Eng. Samir Fathy (Civil engineering)');
    expect(items[0]).toHaveTextContent('First instance no. 1234/2025');
    expect(items[0]).toHaveTextContent('Referred on 1 Feb 2025');
    expect(items[0]).toHaveTextContent('Report due 1 Apr 2025');
    expect(items[0]).toHaveTextContent('Report overdue');
    expect(items[0]).toHaveTextContent('Inspect the boundary wall');
    expect(items[0]).toHaveTextContent('No sessions yet.');
    expect(items[1]).toHaveTextContent('Report submitted on 20 Mar 2025');
    expect(items[1]).toHaveTextContent('15 Feb 2025 · 10:00 · Site');
    expect(items[1]).toHaveTextContent('Parties attended');
    expect(within(items[1] as HTMLElement).queryByRole('button')).not.toBeInTheDocument();
  });

  it('refers a proceeding to an active expert', async () => {
    const user = userEvent.setup();
    const answers = [problem(422, { rule: 'REFERRAL_BEFORE_FILING' }), jsonResponse(referral(), 201)];
    const { requests } = serve({ onWrite: () => answers.shift() ?? problem(500) });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    expect(await within(region).findByText('No expert referrals for this case yet.')).toBeVisible();
    await user.click(within(region).getByRole('button', { name: 'Refer to an expert' }));
    const form = within(region).getByRole('form', { name: 'Refer to an expert' });
    expect(within(form).getByLabelText('Proceeding')).toHaveValue('p1');
    await user.selectOptions(await within(form).findByLabelText('Expert'), 'Hoda Adel (Civil engineering)');
    await user.type(within(form).getByLabelText('Referral date'), '2025-02-01');
    await user.type(within(form).getByLabelText('Report due (optional)'), '2025-04-01');
    await user.type(within(form).getByLabelText('Mission (optional)'), 'Inspect the wall');
    await user.click(within(form).getByRole('button', { name: 'Refer' }));
    expect(await within(form).findByText('A referral cannot be dated before the proceeding was filed.')).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Refer' }));
    expect(await within(region).findByRole('status')).toHaveTextContent('The expert referral was saved.');
    const post = requests.filter((r) => r.method === 'POST').at(-1);
    expect(post?.path).toBe('/api/v1/proceedings/p1/expert-referrals');
    expect(post?.body).toEqual({
      expertId: 'e2',
      referralDate: '2025-02-01',
      reportDueDate: '2025-04-01',
      mission: 'Inspect the wall',
    });
    const experts = requests.find((r) => r.path === '/api/v1/experts');
    expect(experts?.query.get('active')).toBe('true');
  });

  it('records a session, the report and a cancellation on an open referral', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [{ field: 'sessionDate', code: 'NotNull' }] }),
      jsonResponse(referral(), 201),
      problem(422, { rule: 'REPORT_BEFORE_REFERRAL' }),
      jsonResponse(referral()),
      problem(409),
      jsonResponse(referral()),
    ];
    const { requests } = serve({ referrals: page([referral()]), onWrite: () => answers.shift() ?? problem(500) });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    const item = (await within(region).findAllByRole('article'))[0] as HTMLElement;

    await user.click(within(item).getByRole('button', { name: 'Record session' }));
    let form = within(item).getByRole('form', { name: 'Record an expert session' });
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByText('This field is required.')).toBeVisible();
    await user.type(within(form).getByLabelText('Session date'), '2025-02-15');
    await user.type(within(form).getByLabelText('Time (optional)'), '10:00');
    await user.type(within(form).getByLabelText('Place (optional)'), 'Site');
    await user.type(within(form).getByLabelText('Notes (optional)'), 'Parties attended');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(region).findByRole('status')).toHaveTextContent('The expert referral was saved.');

    const reopened = (await within(region).findAllByRole('article'))[0] as HTMLElement;
    await user.click(within(reopened).getByRole('button', { name: 'Report submitted' }));
    form = within(reopened).getByRole('form', { name: 'Record the expert report' });
    await user.type(within(form).getByLabelText('Report submitted on'), '2025-01-20');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByText('The report cannot be dated before the referral.')).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(region).findByRole('status')).toBeInTheDocument();

    const again = (await within(region).findAllByRole('article'))[0] as HTMLElement;
    await user.click(within(again).getByRole('button', { name: 'Cancel referral' }));
    await user.click(within(again).getByRole('button', { name: 'Keep the referral' }));
    await user.click(within(again).getByRole('button', { name: 'Cancel referral' }));
    await user.click(within(again).getByRole('button', { name: 'Cancel the referral' }));
    expect(
      await within(region).findByText('Someone else changed this record meanwhile. Reload and try again.'),
    ).toBeVisible();
    await user.click(within(again).getByRole('button', { name: 'Cancel referral' }));
    await user.click(within(again).getByRole('button', { name: 'Cancel the referral' }));
    expect(await within(region).findByRole('status')).toBeInTheDocument();

    expect(requests.filter((r) => r.method === 'POST').map((r) => [r.path, r.body])).toEqual([
      ['/api/v1/expert-referrals/r1/sessions', { sessionDate: null, sessionTime: null, location: null, notes: null }],
      [
        '/api/v1/expert-referrals/r1/sessions',
        { sessionDate: '2025-02-15', sessionTime: '10:00', location: 'Site', notes: 'Parties attended' },
      ],
      ['/api/v1/expert-referrals/r1/report', { reportSubmittedDate: '2025-01-20', version: 0 }],
      ['/api/v1/expert-referrals/r1/report', { reportSubmittedDate: '2025-01-20', version: 0 }],
      ['/api/v1/expert-referrals/r1/cancel', { version: 0 }],
      ['/api/v1/expert-referrals/r1/cancel', { version: 0 }],
    ]);
  });

  it('explains why a referral cannot be made, and a refused or failed list', async () => {
    serve({ proceedings: page([]) });
    const first = renderOperations(`/cases/${CASE}`);
    expect(
      await within(await section()).findByText('Record a proceeding of this case before referring it to an expert.'),
    ).toBeVisible();
    first.unmount();

    serve({ referrals: problem(403) });
    const second = renderOperations(`/cases/${CASE}`);
    expect(
      await within(await section()).findByText('You are not permitted to view the expert referrals of this case.'),
    ).toBeVisible();
    second.unmount();

    const user = userEvent.setup();
    let referrals = problem(500);
    serve({
      get referrals() {
        return referrals;
      },
    });
    renderOperations(`/cases/${CASE}?lang=ar`);
    const region = await screen.findByRole('region', { name: 'إحالات الخبراء' });
    expect(await within(region).findByText('تعذّر تحميل البيانات. تحقّق من الاتصال وأعد المحاولة.')).toBeVisible();
    referrals = page([referral()]);
    await user.click(within(region).getByRole('button', { name: 'أعد المحاولة' }));
    expect(await within(region).findByRole('article')).toBeVisible();
  });
});
