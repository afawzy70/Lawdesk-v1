import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { mount, page, problem, serve, type Request } from '../followup/testSupport';
import type { Deadline, DeadlineRule } from './api';
import { CaseDeadlinesSection } from './CaseDeadlinesSection';
import { calculation, CASE, deadline, rule } from './testSupport';

/** LD-054, requirements section 10 (S14): manual deadlines, approval by a competent member, proposals only from an approved rule. */

const LIST = `/api/v1/cases/${CASE}/deadlines`;
const MEMBERS = [{ id: 'm1', displayName: 'Mona Specialist' }];
const DOCUMENTS = [{ id: 'doc1', caseId: CASE, title: 'Proof of filing', fileName: 'p.pdf' }];

function backend(items: Deadline[] | Response, rules: DeadlineRule[] = [], write?: (r: Request) => Response | undefined) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === LIST) return items instanceof Response ? items : page(items);
    if (r.method === 'GET' && r.path === '/api/v1/cases/members') return jsonResponse(MEMBERS);
    if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/documents`) return page(DOCUMENTS);
    if (r.method === 'GET' && r.path === '/api/v1/deadline-rules') return page(rules);
    if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/judgments`) return page([{ id: 'j1', judgmentDate: '2026-03-01' }]);
    if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/hearings`) return page([{ id: 'h1', hearingDate: '2026-02-01' }]);
    if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/proceedings`) return page([{ id: 'p1', courtCaseNumber: '100', courtCaseYear: 2026 }]);
    if (r.method === 'GET' && r.path === '/api/v1/tasks') return page([{ id: 't1', title: 'Collect the file' }]);
    return write?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

const VIEW = ['DEADLINE_VIEW'] as const;
const MANAGE = ['DEADLINE_VIEW', 'DEADLINE_MANAGE'] as const;
const APPROVE = ['DEADLINE_VIEW', 'DEADLINE_MANAGE', 'DEADLINE_APPROVE'] as const;

describe('case deadlines', () => {
  it('shows a manual deadline as awaiting approval and an approved one with who and when', async () => {
    backend([
      deadline({ overdue: true, daysRemaining: -2, notes: 'Check the file', periodDays: 30 }),
      deadline({
        id: 'd2', action: 'Reply', calcStatus: 'APPROVED', official: true, approvedByName: 'Hala Supervisor',
        approvedAt: '2026-11-02T08:00:00Z', sourceType: 'JUDGMENT', sourceLabel: '2026-03-01', changeReason: 'Served later',
      }),
      deadline({ id: 'd3', action: 'Done', status: 'COMPLETED', completedOn: '2026-11-05', proofDocumentTitle: 'Proof of filing', calcStatus: 'APPROVED', dueOn: null, daysRemaining: null }),
      deadline({ id: 'd4', action: 'Also done', status: 'COMPLETED', completedOn: '2026-11-05', calcStatus: 'APPROVED' }),
    ]);
    mount(<CaseDeadlinesSection caseId={CASE} editable={false} canApprove={false} />, [...VIEW]);
    await screen.findByText('File the appeal');
    const items = Array.from(document.querySelectorAll('li.wp3-card'));
    expect(items[0]).toHaveTextContent('Manual, awaiting approval');
    expect(items[0]).toHaveTextContent('overdue');
    expect(items[0]).toHaveTextContent('This is a manual date. It is not an official deadline');
    expect(items[0]).toHaveTextContent('Notice served on the authority');
    expect(items[0]).toHaveTextContent('Check the file');
    expect(items[1]).toHaveTextContent('Approved');
    expect(items[1]).toHaveTextContent('Hala Supervisor');
    expect(items[1]).toHaveTextContent('Judgment: 01/03/2026');
    expect(items[1]).toHaveTextContent('Served later');
    expect(items[1]).not.toHaveTextContent('not an official deadline');
    expect(items[2]).toHaveTextContent('Completed');
    expect(items[2]).toHaveTextContent('Proof of filing');
    expect(items[2]).toHaveTextContent('Not specified');
    expect(items[3]).toHaveTextContent('No proof document');
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('says there are none, reports a failure, and is left out for someone who may not open it', async () => {
    backend([]);
    const first = mount(<CaseDeadlinesSection caseId={CASE} editable={false} canApprove={false} />, [...VIEW]);
    expect(await screen.findByText('No legal deadline is recorded for this case.')).toBeInTheDocument();
    first.unmount();
    backend(problem(500));
    const second = mount(<CaseDeadlinesSection caseId={CASE} editable={false} canApprove={false} />, [...VIEW]);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    second.unmount();
    backend(problem(403));
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove />, [...APPROVE]);
    await waitFor(() => { expect(screen.queryByRole('heading', { name: 'Legal deadlines' })).not.toBeInTheDocument(); });
  });

  it('offers approval only to a holder of the approval permission', async () => {
    const user = userEvent.setup();
    const approved = deadline({ calcStatus: 'APPROVED', official: true, version: 1, approvedByName: 'Hala Supervisor', approvedAt: '2026-11-02T08:00:00Z' });
    const { requests } = backend([deadline()], [], (r) => (r.method === 'POST' ? jsonResponse(approved) : undefined));
    const first = mount(<CaseDeadlinesSection caseId={CASE} editable canApprove={false} />, [...MANAGE]);
    await screen.findByText('File the appeal');
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    first.unmount();
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove />, [...APPROVE]);
    await user.click(await screen.findByRole('button', { name: 'Approve' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The deadline was approved.');
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.path).toBe('/api/v1/deadlines/d1/approve');
    expect(post?.body).toEqual({ version: 0 });
  });

  it('records a manual deadline and asks for the action and a date or period first', async () => {
    const user = userEvent.setup();
    const { requests } = backend([], [], (r) => (r.method === 'POST' ? jsonResponse(deadline(), 201) : undefined));
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove={false} />, [...MANAGE]);
    const form = await screen.findByRole('form', { name: 'Record a deadline' });
    expect(within(form).getByText(/No deadline rule has been approved yet/)).toBeInTheDocument();
    expect(within(form).queryByRole('radio')).not.toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Record the deadline' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Some fields are missing');
    await user.type(within(form).getByLabelText('Source (free text)'), 'Notice of 1 March');
    await user.type(within(form).getByLabelText('Required action'), ' File the appeal ');
    await user.type(within(form).getByLabelText('Period (days)'), '30');
    await user.selectOptions(within(form).getByLabelText('Responsible member'), 'Mona Specialist');
    await user.click(within(form).getByRole('button', { name: 'Record the deadline' }));
    expect(await screen.findByText('The deadline was recorded and is awaiting approval.')).toBeInTheDocument();
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.path).toBe(LIST);
    expect(post?.body).toEqual({
      sourceType: 'TEXT', sourceId: null, sourceText: 'Notice of 1 March', startsOn: null, periodDays: 30, dueOn: null,
      action: 'File the appeal', ownerId: 'm1', notes: null,
    });
  });

  it('runs from a record of the case when one is chosen', async () => {
    const user = userEvent.setup();
    const { requests } = backend([], [], (r) => (r.method === 'POST' ? jsonResponse(deadline(), 201) : undefined));
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove={false} />, [...MANAGE]);
    const form = await screen.findByRole('form', { name: 'Record a deadline' });
    for (const [type, option] of [['Judgment', '01/03/2026'], ['Hearing', '01/02/2026'], ['Proceeding', '100/2026'], ['Task', 'Collect the file']] as const) {
      await user.selectOptions(within(form).getByLabelText('The deadline runs from'), type);
      await user.selectOptions(await within(form).findByRole('option', { name: option }).then(() => within(form).getByLabelText('Source')), option);
    }
    await user.type(within(form).getByLabelText('Required action'), 'Follow up');
    await user.type(within(form).getByLabelText('Last day to act'), '2026-12-01');
    await user.click(within(form).getByRole('button', { name: 'Record the deadline' }));
    await screen.findByText('The deadline was recorded and is awaiting approval.');
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({ sourceType: 'TASK', sourceId: 't1', sourceText: null, dueOn: '2026-12-01' });
    await user.selectOptions(within(form).getByLabelText('The deadline runs from'), 'Other (free text)');
    expect(within(form).getByLabelText('Source (free text)')).toBeInTheDocument();
  });

  it('keeps the picker empty when the records of the case cannot be listed', async () => {
    const user = userEvent.setup();
    serve((r) => {
      if (r.path === LIST) return page([]);
      return undefined;
    });
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove={false} />, [...MANAGE]);
    const form = await screen.findByRole('form', { name: 'Record a deadline' });
    await user.selectOptions(within(form).getByLabelText('The deadline runs from'), 'Judgment');
    await waitFor(() => { expect(within(form).getByLabelText('Source')).toHaveValue(''); });
    expect(within(form).getAllByRole('option', { name: 'Choose…' })).toHaveLength(1);
  });

  it('proposes a date from an approved rule and shows the arithmetic; a refusal is explained', async () => {
    const user = userEvent.setup();
    let answer: Response = jsonResponse(
      deadline({ id: 'd9', calcStatus: 'PROPOSED', ruleId: 'r1', ruleCode: 'TEST-WORKING', dueOn: '2031-03-11', calculation: calculation() }),
      201,
    );
    const { requests } = backend([], [rule(), rule({ id: 'r2', code: 'DRAFT-ONE', status: 'DRAFT' })], (r) => (r.method === 'POST' ? answer : undefined));
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove={false} />, [...MANAGE]);
    const form = await screen.findByRole('form', { name: 'Record a deadline' });
    await user.click(await within(form).findByLabelText('Propose it from an approved rule'));
    expect(within(form).queryByLabelText('Last day to act')).not.toBeInTheDocument();
    expect(within(form).getByRole('option', { name: 'TEST-WORKING — Test-only rule' })).toBeInTheDocument();
    expect(within(form).queryByRole('option', { name: /DRAFT-ONE/ })).not.toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Propose a date from the rule' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Some fields are missing');
    await user.type(within(form).getByLabelText('Source (free text)'), 'Judgment notified');
    await user.type(within(form).getByLabelText('Required action'), 'File the appeal');
    await user.type(within(form).getByLabelText('Start date'), '2031-03-03');
    await user.selectOptions(within(form).getByLabelText('Rule'), 'TEST-WORKING — Test-only rule');
    answer = problem(422, { rule: 'NO_APPROVED_RULE' });
    await user.click(within(form).getByRole('button', { name: 'Propose a date from the rule' }));
    expect(await screen.findByText(/No approved deadline rule exists/)).toBeInTheDocument();
    answer = jsonResponse(deadline({ id: 'd9', calcStatus: 'PROPOSED', ruleId: 'r1', ruleCode: 'TEST-WORKING', dueOn: '2031-03-11', calculation: calculation() }), 201);
    // The refused input is kept, so the user can try again without typing it twice.
    expect(within(form).getByLabelText('Required action')).toHaveValue('File the appeal');
    await user.click(within(form).getByRole('button', { name: 'Propose a date from the rule' }));
    expect(await screen.findByText('The proposal was recorded and is awaiting approval.')).toBeInTheDocument();
    const posts = requests.filter((r) => r.method === 'POST');
    expect(posts[1]?.path).toBe(`${LIST}/propose`);
    expect(posts[1]?.body).toEqual({ sourceType: 'TEXT', sourceId: null, sourceText: 'Judgment notified', ruleId: 'r1', startsOn: '2031-03-03', action: 'File the appeal', ownerId: null, notes: null });
  }, 20000);

  it('shows the rule, the source, the holidays applied and the arithmetic of a proposal', async () => {
    backend([
      deadline({ calcStatus: 'PROPOSED', ruleId: 'r1', ruleCode: 'TEST-WORKING', dueOn: '2031-03-11', calculation: calculation() }),
      deadline({
        id: 'd2', calcStatus: 'PROPOSED', ruleId: 'r2', dueOn: '2031-03-10',
        calculation: calculation({ countMethod: 'CALENDAR', countsStartDay: true, rollForward: true, closedWeekdays: [], skipped: [], rolledForwardFrom: '2031-03-08' }),
      }),
    ]);
    mount(<CaseDeadlinesSection caseId={CASE} editable={false} canApprove={false} />, [...VIEW]);
    await screen.findAllByText('How the date was computed');
    const [first, second] = Array.from(document.querySelectorAll('details.wp4-calculation'));
    expect(first).toHaveTextContent('TEST-WORKING — Test-only rule');
    expect(first).toHaveTextContent('Test-only source');
    expect(first).toHaveTextContent('5 working days');
    expect(first).toHaveTextContent('counting starts the day after');
    expect(first).toHaveTextContent('closed: Saturday، Sunday');
    expect(first).toHaveTextContent('05/03/2031 — official holiday: Test holiday');
    expect(first).toHaveTextContent('08/03/2031 — closed weekday');
    expect(first).toHaveTextContent('11/03/2031');
    expect(first).toHaveTextContent('04/03/2031');
    expect(second).toHaveTextContent('the start day is counted');
    expect(second).toHaveTextContent('no weekday is closed');
    expect(second).toHaveTextContent('a date on a closed day moves to the next open day');
    expect(second).toHaveTextContent('None');
    expect(second).toHaveTextContent('Period ended on a closed day');
    expect(second).toHaveTextContent('08/03/2031');
    expect(document.querySelectorAll('li.wp3-card')[0]).toHaveTextContent('Proposed, awaiting approval');
    expect(document.querySelectorAll('li.wp3-card')[0]).toHaveTextContent('This date was proposed from a rule');
  });

  it('edits a deadline and asks for a reason when the date of a proposal or an approval changes', async () => {
    const user = userEvent.setup();
    const { requests } = backend([deadline({ calcStatus: 'APPROVED', official: true, approvedByName: 'Hala', approvedAt: '2026-11-02T08:00:00Z', periodDays: 30, notes: 'n', startsOn: null })], [], (r) =>
      r.method === 'PUT' ? jsonResponse(deadline()) : undefined);
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove />, [...APPROVE]);
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const form = await screen.findByRole('form', { name: 'Edit' });
    expect(within(form).getByLabelText('Reason for the change')).toBeInTheDocument();
    await user.clear(within(form).getByLabelText('Required action'));
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Some fields are missing');
    await user.type(within(form).getByLabelText('Required action'), 'File the appeal today');
    await user.clear(within(form).getByLabelText('Last day to act'));
    await user.type(within(form).getByLabelText('Last day to act'), '2026-12-15');
    await user.type(within(form).getByLabelText('Reason for the change'), 'Served later');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('The deadline was saved.')).toBeInTheDocument();
    const put = requests.find((r) => r.method === 'PUT');
    expect(put?.path).toBe('/api/v1/deadlines/d1');
    expect(put?.body).toEqual({ startsOn: null, periodDays: 30, dueOn: '2026-12-15', action: 'File the appeal today', ownerId: 'm1', notes: 'n', changeReason: 'Served later', version: 0 });
  });

  it('does not ask for a reason on a manual deadline and can cancel the form', async () => {
    const user = userEvent.setup();
    backend([deadline()]);
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove={false} />, [...MANAGE]);
    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const form = await screen.findByRole('form', { name: 'Edit' });
    expect(within(form).queryByLabelText('Reason for the change')).not.toBeInTheDocument();
    await user.clear(within(form).getByLabelText('Last day to act'));
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByRole('alert')).toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('records the completion with a proof document, a missed deadline, and a reopening - only by a user', async () => {
    const user = userEvent.setup();
    const done = deadline({ status: 'COMPLETED', completedOn: '2026-11-09' });
    const { requests } = backend([deadline({ calcStatus: 'APPROVED', official: true })], [], (r) => (r.method === 'POST' ? jsonResponse(done) : undefined));
    const first = mount(<CaseDeadlinesSection caseId={CASE} editable canApprove />, [...APPROVE]);
    await user.click(await screen.findByRole('button', { name: 'Record completion' }));
    const form = await screen.findByRole('form', { name: 'Record completion' });
    await user.type(within(form).getByLabelText('Completed on'), '2026-11-08');
    await user.selectOptions(await within(form).findByRole('combobox', { name: 'Proof document' }), 'Proof of filing');
    await user.click(within(form).getByRole('button', { name: 'Record completion' }));
    expect(await screen.findByText('The completion was recorded.')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({ status: 'COMPLETED', proofDocumentId: 'doc1', version: 0 });
    await user.click(screen.getByRole('button', { name: 'Record completion' }));
    await user.click(within(screen.getByRole('form', { name: 'Record completion' })).getByRole('button', { name: 'Cancel' }));
    await user.click(screen.getByRole('button', { name: 'Record as missed' }));
    expect(await screen.findByText('The deadline was recorded as missed.')).toBeInTheDocument();
    expect(requests.filter((r) => r.method === 'POST').at(-1)?.body).toMatchObject({ status: 'MISSED' });
    first.unmount();
    backend([deadline({ status: 'MISSED', calcStatus: 'APPROVED' })], [], (r) => (r.method === 'POST' ? jsonResponse(deadline()) : undefined));
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove />, [...APPROVE]);
    await user.click(await screen.findByRole('button', { name: 'Reopen' }));
    expect(await screen.findByText('The deadline was reopened.')).toBeInTheDocument();
  });

  it('cannot complete a proposal before it is approved, and explains a refusal', async () => {
    const user = userEvent.setup();
    backend([deadline({ calcStatus: 'PROPOSED', ruleId: 'r1', calculation: calculation() }), deadline({ id: 'd2', calcStatus: 'MANUAL' })], [], (r) =>
      r.method === 'POST' ? problem(409) : undefined);
    mount(<CaseDeadlinesSection caseId={CASE} editable canApprove={false} />, [...MANAGE]);
    await screen.findAllByRole('button', { name: 'Edit' });
    expect(screen.getAllByRole('button', { name: 'Record completion' })).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Record as missed' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
  });

  it('is read-only for an archived case or a user who may not change deadlines', async () => {
    backend([deadline(), deadline({ id: 'd2', status: 'COMPLETED', completedOn: '2026-11-02' })]);
    mount(<CaseDeadlinesSection caseId={CASE} editable={false} canApprove />, [...APPROVE]);
    await screen.findAllByText('File the appeal');
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reopen' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });
});
