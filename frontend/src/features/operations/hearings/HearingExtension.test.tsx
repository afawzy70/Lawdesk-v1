import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { CASE, hearing, page, problem, proceeding, renderOperations, scriptedFetch, type Request } from '../testSupport';

/** LD-053, requirements section 7 (S9): the fields of the hearing record, adjournment and the "no next date" path. */

const HEARINGS = `/api/v1/cases/${CASE}/hearings`;
const MEMBERS = [{ id: 'm1', displayName: 'Hala Supervisor' }];

function backend(hearings: Response, write?: (r: Request) => Response | undefined, followUp: Response = page([])) {
  return scriptedFetch((r) => {
    if (r.method === 'GET' && r.path === HEARINGS) return hearings.clone();
    if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/proceedings`) return page([proceeding()]);
    if (r.method === 'GET' && r.path === '/api/v1/cases/members') return jsonResponse(MEMBERS);
    if (r.method === 'GET' && r.path === '/api/v1/hearings/follow-up') return followUp.clone();
    if (r.method === 'GET' && r.path === '/api/v1/hearings') return page([hearing()]);
    return write?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

const section = async () => screen.findByRole('region', { name: 'Hearings' });

describe('hearing record fields', () => {
  it('schedules a hearing with its kind, chamber, judge or expert, responsible member and required action', async () => {
    const user = userEvent.setup();
    const { requests } = backend(page([]), (r) => (r.method === 'POST' ? jsonResponse(hearing(), 201) : undefined));
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    await user.click(await within(region).findByRole('button', { name: 'Schedule hearing' }));
    const form = within(region).getByRole('form', { name: 'Schedule hearing' });
    await user.type(within(form).getByLabelText('Date'), '2025-03-02');
    await user.selectOptions(within(form).getByLabelText('Purpose'), 'EXPERT_REPORT');
    await user.selectOptions(within(form).getByLabelText('Kind of session'), 'Experts');
    await user.type(within(form).getByLabelText('Chamber'), 'Third chamber');
    await user.type(within(form).getByLabelText('Judge or expert'), 'Eng. Samir');
    await user.selectOptions(await within(form).findByRole('option', { name: 'Hala Supervisor' }).then((o) => o.parentElement as HTMLElement), 'Hala Supervisor');
    await user.type(within(form).getByLabelText('Attendees'), 'Both sides');
    await user.type(within(form).getByLabelText('Required action'), 'Prepare a memo');
    await user.click(within(form).getByRole('button', { name: 'Schedule' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({
      kind: 'EXPERT', chamber: 'Third chamber', judgeOrExpert: 'Eng. Samir', responsibleMemberId: 'm1', attendees: 'Both sides',
      requiredAction: 'Prepare a memo',
    });
  });

  it('shows the extended record, the follow-up mark and what the court decided', async () => {
    backend(page([
      hearing({
        status: 'ADJOURNED', kind: 'EXPERT', chamber: 'Third chamber', judgeOrExpert: 'Eng. Samir', responsibleMemberName: 'Hala',
        proceedingsSummary: 'Pleadings heard', opponentsRequests: 'More time', requiredAction: 'Reply', adjournmentReason: 'Service',
        noNextDate: true, noNextDateReason: 'The court set none', followUp: true,
      }),
      hearing({ id: 'h2', status: 'ADJOURNED', noNextDate: true, noNextDateReason: 'No date then', followUp: false }),
    ]));
    renderOperations(`/cases/${CASE}`);
    const items = await within(await section()).findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Experts');
    expect(items[0]).toHaveTextContent('Third chamber · Eng. Samir · Hala');
    expect(items[0]).toHaveTextContent('Pleadings heard');
    expect(items[0]).toHaveTextContent("Opponents' requests: More time");
    expect(items[0]).toHaveTextContent('Required action: Reply');
    expect(items[0]).toHaveTextContent('Adjourned because: Service');
    expect(items[0]).toHaveTextContent('No next date: on the follow-up list');
    expect(items[0]).toHaveTextContent('The court set none');
    expect(items[0]).toHaveAttribute('id', 'wp3-ref-HEARING-h1');
    expect(items[1]).toHaveTextContent('Had no next date');
    expect(within(items[1] as HTMLElement).queryByRole('button', { name: 'Schedule the next hearing' })).not.toBeInTheDocument();
  });

  it('records what happened at the hearing', async () => {
    const user = userEvent.setup();
    const { requests } = backend(page([hearing()]), (r) => (r.method === 'POST' ? jsonResponse(hearing({ status: 'HELD' })) : undefined));
    renderOperations(`/cases/${CASE}`);
    await user.click(await within(await section()).findByRole('button', { name: /Record the result of the hearing/ }));
    const form = screen.getByRole('form', { name: /Result of the hearing/ });
    await user.type(within(form).getByLabelText('Attendees'), 'Both sides');
    await user.type(within(form).getByLabelText('What took place at the hearing'), 'Pleadings');
    await user.type(within(form).getByLabelText("Opponents' requests"), 'More time');
    await user.type(within(form).getByLabelText('Required action'), 'Reply');
    await user.click(within(form).getByRole('button', { name: 'Record result' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({
      status: 'HELD', attendees: 'Both sides', proceedingsSummary: 'Pleadings', opponentsRequests: 'More time',
      requiredAction: 'Reply', adjournmentReason: null, noNextDate: null,
    });
  });

  it('adjourns with a reason and a next date, which keeps the date fields', async () => {
    const user = userEvent.setup();
    const { requests } = backend(page([hearing()]), (r) => (r.method === 'POST' ? jsonResponse(hearing({ status: 'ADJOURNED' })) : undefined));
    renderOperations(`/cases/${CASE}`);
    await user.click(await within(await section()).findByRole('button', { name: /Record the result of the hearing/ }));
    const form = screen.getByRole('form', { name: /Result of the hearing/ });
    await user.click(within(form).getByLabelText('Adjourned'));
    await user.type(within(form).getByLabelText('Reason for the adjournment'), 'Service of the claim');
    await user.type(within(form).getByLabelText('Next hearing date'), '2025-04-06');
    await user.click(within(form).getByRole('button', { name: 'Record result' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({
      status: 'ADJOURNED', adjournmentReason: 'Service of the claim', nextHearingDate: '2025-04-06', noNextDate: null,
    });
  });

  it('adjourns with no next date: the date fields go, the reason is asked and no date is sent', async () => {
    const user = userEvent.setup();
    const { requests } = backend(page([hearing()]), (r) => (r.method === 'POST' ? jsonResponse(hearing({ status: 'ADJOURNED' })) : undefined));
    renderOperations(`/cases/${CASE}`);
    await user.click(await within(await section()).findByRole('button', { name: /Record the result of the hearing/ }));
    const form = screen.getByRole('form', { name: /Result of the hearing/ });
    await user.click(within(form).getByLabelText('Adjourned'));
    await user.type(within(form).getByLabelText('Next hearing date'), '2025-04-06');
    await user.click(within(form).getByLabelText('The court set no next date'));
    expect(within(form).queryByLabelText('Next hearing date')).not.toBeInTheDocument();
    await user.type(within(form).getByLabelText('Why no date was set'), 'The court gave none');
    await user.click(within(form).getByRole('button', { name: 'Record result' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({
      status: 'ADJOURNED', noNextDate: true, noNextDateReason: 'The court gave none', nextHearingDate: null, nextHearingTime: null,
    });
  });

  it('shows the backend field error for a missing reason', async () => {
    const user = userEvent.setup();
    backend(page([hearing()]), (r) => (r.method === 'POST' ? problem(400, { errors: [{ field: 'noNextDateReason', code: 'NotNull' }] }) : undefined));
    renderOperations(`/cases/${CASE}`);
    await user.click(await within(await section()).findByRole('button', { name: /Record the result of the hearing/ }));
    const form = screen.getByRole('form', { name: /Result of the hearing/ });
    await user.click(within(form).getByLabelText('Adjourned'));
    await user.click(within(form).getByLabelText('The court set no next date'));
    await user.click(within(form).getByRole('button', { name: 'Record result' }));
    expect(await within(form).findByText('This field is required.')).toBeInTheDocument();
  });
});

describe('the next hearing after "no next date"', () => {
  const waiting = hearing({ status: 'ADJOURNED', noNextDate: true, noNextDateReason: 'None', followUp: true, version: 3 });

  it('schedules it from the case page and sends the version read', async () => {
    const user = userEvent.setup();
    const { requests } = backend(page([waiting]), (r) => (r.method === 'POST' ? jsonResponse(hearing({ id: 'h9' }), 201) : undefined));
    renderOperations(`/cases/${CASE}`);
    await user.click(await within(await section()).findByRole('button', { name: 'Schedule the next hearing' }));
    const form = screen.getByRole('form', { name: /Next hearing after/ });
    await user.type(within(form).getByLabelText('Next hearing date'), '2025-05-04');
    await user.type(within(form).getByLabelText('Next hearing time (optional)'), '10:30');
    await user.click(within(form).getByRole('button', { name: 'Schedule' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.path).toBe('/api/v1/hearings/h1/next-hearing');
    expect(post?.body).toEqual({ hearingDate: '2025-05-04', hearingTime: '10:30', version: 3 });
  });

  it('explains a refusal and can be cancelled', async () => {
    const user = userEvent.setup();
    backend(page([waiting]), (r) => (r.method === 'POST' ? problem(422, { rule: 'HEARING_NOT_AWAITING_DATE' }) : undefined));
    renderOperations(`/cases/${CASE}`);
    await user.click(await within(await section()).findByRole('button', { name: 'Schedule the next hearing' }));
    const form = screen.getByRole('form', { name: /Next hearing after/ });
    await user.type(within(form).getByLabelText('Next hearing date'), '2025-05-04');
    await user.click(within(form).getByRole('button', { name: 'Schedule' }));
    expect(await within(form).findByRole('alert')).toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form', { name: /Next hearing after/ })).not.toBeInTheDocument();
  });

  it('lists the cases on the follow-up list on the hearing calendar, and nothing when there are none', async () => {
    backend(page([]), undefined, page([waiting.caseId === CASE ? { ...waiting, caseTitle: 'Rent dispute' } : waiting]));
    const first = renderOperations('/hearings?lang=en');
    expect(await screen.findByRole('heading', { name: 'Follow-up: no next date' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'LD-2025-00042' }).length).toBeGreaterThan(0);
    expect(screen.getByText(/None/)).toBeInTheDocument();
    first.unmount();
    backend(page([]), undefined, page([]));
    renderOperations('/hearings?lang=en');
    await screen.findByRole('heading', { level: 1, name: 'Hearing calendar' });
    expect(screen.queryByRole('heading', { name: 'Follow-up: no next date' })).not.toBeInTheDocument();
  });

  it('stays out of the way when the follow-up list cannot be read', async () => {
    backend(page([]), undefined, problem(500));
    renderOperations('/hearings?lang=en');
    await screen.findByRole('heading', { level: 1, name: 'Hearing calendar' });
    expect(screen.queryByRole('heading', { name: 'Follow-up: no next date' })).not.toBeInTheDocument();
  });
});
