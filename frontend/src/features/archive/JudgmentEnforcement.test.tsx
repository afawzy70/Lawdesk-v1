import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Permission } from '../../api/permissions';
import { CASE, problem, renderWith, serve } from '../pleadings/testSupport';
import type { EnforcementAction, EnforcementFile, EnforcementOverview } from './api';
import { JudgmentEnforcement } from './JudgmentEnforcement';

/** LD-055 (requirement 11): the enforcement file of a judgment, its actions with dates and results, and who may see and change it. */

const URL = '/api/v1/judgments/j1/enforcement';

const file = (extra: Partial<EnforcementFile> = {}): EnforcementFile => ({
  id: 'f1',
  judgmentId: 'j1',
  caseId: CASE,
  status: 'IN_PROGRESS',
  responsibleMemberId: 'u1',
  responsibleMemberName: 'Maha',
  executingParty: 'Execution Department',
  amountRequired: '1250.500',
  obstacles: 'the address is unknown',
  dueOn: '2026-06-01',
  completedOn: null,
  notes: null,
  late: false,
  version: 2,
  updatedAt: '2026-05-01T08:00:00Z',
  ...extra,
});

const overview = (f: EnforcementFile | null, actions: EnforcementOverview['actions'] = []): EnforcementOverview => ({
  judgment: {
    id: 'j1',
    caseId: CASE,
    caseReferenceNo: 'LD-2026-00007',
    caseTitle: 'Lease dispute',
    stage: 'FIRST_INSTANCE',
    courtCaseNumber: '1055',
    courtCaseYear: 2026,
    judgmentDate: '2026-04-01',
    operativeText: 'the operative part',
    summary: null,
    finalJudgment: false,
  },
  file: f,
  actions,
});

const action = (seq: number, extra = {}): EnforcementAction => ({
  id: `a${String(seq)}`,
  enforcementId: 'f1',
  seq,
  kind: 'CORRESPONDENCE' as const,
  actionDate: '2026-04-10',
  description: 'letter to the debtor',
  result: 'no answer',
  documentId: 'doc1',
  documentTitle: 'Letter',
  version: 0,
  createdAt: '',
  createdBy: 'u1',
  ...extra,
});

const base = {
  'GET /api/v1/users/directory': [{ id: 'u1', username: 'maha', displayName: 'Maha', enabled: true }],
  [`GET /api/v1/cases/${CASE}/documents`]: { items: [{ id: 'doc1', title: 'Letter' }], page: 0, size: 100, totalItems: 1, totalPages: 1 },
};
const MANAGE: Permission[] = ['ENFORCEMENT_VIEW', 'ENFORCEMENT_MANAGE'];

describe('enforcement of a judgment', () => {
  it('shows nothing to a user without the permission', () => {
    const backend = serve(base);
    const { container } = renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable />, { permissions: ['JUDGMENT_VIEW'] });
    expect(container).toBeEmptyDOMElement();
    expect(backend.to('GET', URL)).toHaveLength(1);
  });

  it('shows the file with its late warning and the amount as KWD text', async () => {
    serve({ ...base, [`GET ${URL}`]: overview(file({ late: true }), [action(1)]) });
    renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable />, { permissions: ['ENFORCEMENT_VIEW'] });
    expect(await screen.findByText('In progress')).toBeVisible();
    expect(screen.getByText('1250.500 KWD')).toBeVisible();
    expect(screen.getByText('Execution Department')).toBeVisible();
    expect(screen.getByText('the address is unknown')).toBeVisible();
    expect(screen.getByText('Late: the target date has passed')).toBeVisible();
    expect(screen.getByText('letter to the debtor')).toBeVisible();
    expect(screen.getByText('Result: no answer')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Edit the file' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record an action' })).not.toBeInTheDocument();
  });

  it('opens an enforcement file for a judgment that has none', async () => {
    const user = userEvent.setup();
    let current: EnforcementOverview = overview(null);
    const backend = serve({
      ...base,
      [`GET ${URL}`]: () => current,
      [`POST ${URL}`]: () => {
        current = overview(file());
        return current;
      },
    });
    renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable />, { permissions: MANAGE });
    expect(await screen.findByText('No enforcement file is recorded for this judgment.')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Open an enforcement file' }));
    const form = screen.getByRole('form', { name: 'Enforcement of the judgment' });
    await user.selectOptions(within(form).getByLabelText('Enforcement status'), 'IN_PROGRESS');
    await user.selectOptions(within(form).getByLabelText('Responsible member'), 'u1');
    await user.type(within(form).getByLabelText('Party charged with enforcement'), 'Execution Department');
    await user.type(within(form).getByLabelText('Amount required (KWD)'), '1250,500');
    fireEvent.change(within(form).getByLabelText('Target date'), { target: { value: '2026-06-01' } });
    await user.click(within(form).getByRole('button', { name: 'Save the enforcement file' }));
    expect(await screen.findByText('The enforcement file was saved.')).toBeVisible();
    expect(backend.to('POST', URL)[0]?.body).toEqual({
      status: 'IN_PROGRESS',
      responsibleMemberId: 'u1',
      executingParty: 'Execution Department',
      amountRequired: '1250.500',
      obstacles: null,
      dueOn: '2026-06-01',
      completedOn: null,
      notes: null,
    });
    expect(await screen.findByText('1250.500 KWD')).toBeVisible();
  });

  it('edits the file with its version and flags the fields the backend refused', async () => {
    const user = userEvent.setup();
    const answers = [problem(400, { errors: [{ field: 'completedOn', code: 'NotNull' }] }), overview(file({ status: 'EXECUTED', completedOn: '2026-05-15' }))];
    const backend = serve({ ...base, [`GET ${URL}`]: overview(file()), 'PUT /api/v1/enforcement/f1': () => answers.shift() });
    renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable />, { permissions: MANAGE });
    await user.click(await screen.findByRole('button', { name: 'Edit the file' }));
    const form = screen.getByRole('form', { name: 'Enforcement of the judgment' });
    await user.selectOptions(within(form).getByLabelText('Enforcement status'), 'EXECUTED');
    await user.click(within(form).getByRole('button', { name: 'Save the enforcement file' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Some fields are missing');
    expect(within(form).getByLabelText('Date enforcement was completed')).toHaveAttribute('aria-invalid', 'true');
    fireEvent.change(within(form).getByLabelText('Date enforcement was completed'), { target: { value: '2026-05-15' } });
    await user.click(within(form).getByRole('button', { name: 'Save the enforcement file' }));
    expect(await screen.findByText('The enforcement file was saved.')).toBeVisible();
    expect(backend.to('PUT', '/api/v1/enforcement/f1')[1]?.body).toMatchObject({ status: 'EXECUTED', completedOn: '2026-05-15', version: 2 });
  });

  it('records and corrects actions with their date, result and document', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      [`GET ${URL}`]: overview(file(), [action(1)]),
      'POST /api/v1/enforcement/f1/actions': action(2),
      'PUT /api/v1/enforcement-actions/a1': action(1),
    });
    renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable />, { permissions: MANAGE });
    await user.click(await screen.findByRole('button', { name: 'Record an action' }));
    const form = screen.getByRole('form', { name: 'Record an action' });
    await user.selectOptions(within(form).getByLabelText('Kind'), 'OBSTACLE');
    fireEvent.change(within(form).getByLabelText('Date'), { target: { value: '2026-04-20' } });
    await user.type(within(form).getByLabelText('What was done'), 'no answer');
    await user.selectOptions(within(form).getByLabelText('Document'), 'doc1');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    await screen.findByText('Action 1');
    expect(backend.to('POST', '/api/v1/enforcement/f1/actions')[0]?.body).toEqual({
      kind: 'OBSTACLE',
      actionDate: '2026-04-20',
      description: 'no answer',
      result: null,
      documentId: 'doc1',
    });

    await user.click(screen.getByRole('button', { name: 'Edit action 1' }));
    const edit = screen.getByRole('form', { name: 'Edit action 1' });
    await user.clear(within(edit).getByLabelText('Result'));
    await user.type(within(edit).getByLabelText('Result'), 'returned unopened');
    await user.click(within(edit).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form', { name: 'Edit action 1' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Edit action 1' }));
    await user.click(within(screen.getByRole('form', { name: 'Edit action 1' })).getByRole('button', { name: 'Save' }));
    await screen.findByText('Action 1');
    expect(backend.to('PUT', '/api/v1/enforcement-actions/a1')[0]?.body).toMatchObject({ kind: 'CORRESPONDENCE', version: 0 });
  });

  it('explains a refused action and a judgment whose enforcement is hidden', async () => {
    const user = userEvent.setup();
    serve({ ...base, [`GET ${URL}`]: overview(file()), 'POST /api/v1/enforcement/f1/actions': problem(403) });
    const first = renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable />, { permissions: MANAGE });
    await user.click(await screen.findByRole('button', { name: 'Record an action' }));
    await user.click(within(screen.getByRole('form', { name: 'Record an action' })).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have the right to do this on this case.');
    first.unmount();

    serve({ ...base, [`GET ${URL}`]: problem(404) });
    renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable />, { permissions: MANAGE });
    expect(await screen.findByText('You are not permitted to view the enforcement of this judgment.')).toBeVisible();
  });

  it('is read-only on an archived case and works in Arabic', async () => {
    serve({ ...base, [`GET ${URL}`]: overview(file({ status: 'OBSTRUCTED' })) });
    renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable={false} />, { permissions: MANAGE, lang: 'ar' });
    expect(await screen.findByText('متعثر')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'تعديل الملف' })).not.toBeInTheDocument();
  });

  it('says so when the enforcement cannot be loaded', async () => {
    serve({ ...base, [`GET ${URL}`]: problem(500) });
    renderWith(<JudgmentEnforcement judgmentId="j1" caseId={CASE} editable />, { permissions: MANAGE });
    expect(await screen.findByText('The data could not be loaded. Check the connection and try again.')).toBeVisible();
  });
});
