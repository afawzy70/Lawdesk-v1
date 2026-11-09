import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CaseEvidence } from './CaseEvidence';
import { CASE, claim, PICKERS, problem, renderWith, serve } from './testSupport';

/** Requirement section 20 (S8): facts, evidence with its role, links, what was submitted, and the gaps. */

const EVIDENCE = `/api/v1/cases/${CASE}/evidence`;
const FACTS = `/api/v1/cases/${CASE}/facts`;

const item = (id: string, name: string, extra = {}) => ({
  id,
  documentId: null,
  documentTitle: null,
  name,
  docType: null,
  issuedOn: null,
  issuer: null,
  receivedOn: null,
  source: null,
  role: 'SUPPORTING',
  importance: null,
  submittedToCourt: false,
  submittedOn: null,
  submittedIn: null,
  opponentResponded: null,
  opponentResponse: null,
  courtPosition: null,
  notes: null,
  version: 0,
  links: [],
  followups: [],
  gap: null,
  ...extra,
});

const base = {
  ...PICKERS,
  [`GET ${FACTS}`]: [{ id: 'f1', seq: 1, text: 'Notification date', factDate: '2026-01-05', evidenceCount: 1, version: 0 }, { id: 'f2', seq: 2, text: 'Investigation', factDate: null, evidenceCount: 0, version: 0 }],
  [`GET /api/v1/cases/${CASE}/defenses`]: [{ id: 'd1', seq: 1, title: 'Late filing' }],
  [`GET /api/v1/cases/${CASE}/claims`]: [claim('c1', 1, 'Compensation')],
  'GET /api/v1/tasks': { items: [{ id: 't1', title: 'Ask personnel affairs' }], page: 0, size: 100, totalItems: 1, totalPages: 1 },
};

describe('facts and evidence of a case', () => {
  it('lists the facts, adds one, and refuses an empty one', async () => {
    const user = userEvent.setup();
    const backend = serve({ ...base, [`GET ${EVIDENCE}`]: [], [`POST ${FACTS}`]: (s) => (s.body as { text: string }).text === '' ? problem(400, { errors: [{ field: 'text', code: 'NotBlank' }] }) : {} });
    renderWith(<CaseEvidence caseId={CASE} canManage />);
    expect(await screen.findByText(/Notification date — 05\/01\/2026/)).toBeVisible();
    expect(screen.getByText(/Investigation \(1 document\(s\)\)|Investigation \(0 document\(s\)\)/)).toBeVisible();
    const form = screen.getByRole('form', { name: 'Add a fact' });
    await user.click(within(form).getByRole('button', { name: 'Add a fact' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Some fields are missing');
    expect(within(form).getByLabelText('Fact')).toHaveAttribute('aria-invalid', 'true');
    await user.type(within(form).getByLabelText('Fact'), 'The investigation record');
    await user.type(within(form).getByLabelText('Date of the fact'), '2026-02-01');
    await user.click(within(form).getByRole('button', { name: 'Add a fact' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The fact was added.');
    expect(backend.to('POST', FACTS)[1]?.body).toEqual({ text: 'The investigation record', factDate: '2026-02-01' });
  });

  it('says when nothing is recorded and hides the forms from a reader', async () => {
    serve({ ...base, [`GET ${FACTS}`]: [], [`GET ${EVIDENCE}`]: [] });
    renderWith(<CaseEvidence caseId={CASE} canManage={false} />);
    expect(await screen.findByText('No facts are recorded yet.')).toBeVisible();
    expect(screen.getByText('No evidence is recorded yet.')).toBeVisible();
    expect(screen.getByText(/Nothing is missing/)).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Add evidence' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Add a fact' })).not.toBeInTheDocument();
  });

  it('explains a section the user may not see or could not load', async () => {
    serve({ ...base, [`GET ${EVIDENCE}`]: problem(403) });
    const { unmount } = renderWith(<CaseEvidence caseId={CASE} canManage />);
    expect(await screen.findByText('You are not allowed to see this section of the case.')).toBeVisible();
    unmount();
    serve({ ...base, [`GET ${EVIDENCE}`]: problem(500) });
    renderWith(<CaseEvidence caseId={CASE} canManage />);
    expect(await screen.findByText('Could not load this section. Try again later.')).toBeVisible();
  });

  it('shows the matrix of documents: role, importance, links and what was submitted', async () => {
    serve({
      ...base,
      [`GET ${EVIDENCE}`]: [
        item('e1', 'Decision 118', {
          role: 'SUPPORTING',
          importance: 'DECISIVE',
          documentTitle: 'Decision.pdf',
          submittedToCourt: true,
          submittedIn: 'Memo of 2026-03-30',
          submittedOn: '2026-03-30',
          links: [
            { id: 'l1', kind: 'FACT', targetId: 'f1', label: 'Fact 1: Notification date' },
            { id: 'l2', kind: 'DEFENSE', targetId: 'd1', label: 'Defence 1: Late filing' },
          ],
        }),
        item('e2', 'Receipt statement', { role: 'REQUIRED', gap: 'NOT_OBTAINED' }),
      ],
    });
    renderWith(<CaseEvidence caseId={CASE} canManage={false} />);
    const table = await screen.findByRole('table', { name: 'Documents and their role' });
    const rows = within(table).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Decision 118');
    expect(rows[1]).toHaveTextContent('(Decision.pdf)');
    expect(rows[1]).toHaveTextContent('Supports the authority');
    expect(rows[1]).toHaveTextContent('Decisive');
    expect(rows[1]).toHaveTextContent('Fact 1: Notification date');
    expect(rows[1]).toHaveTextContent('Defence 1: Late filing');
    expect(rows[1]).toHaveTextContent('Yes — Memo of 2026-03-30 — 30/03/2026');
    expect(rows[2]).toHaveTextContent('Required');
    expect(rows[2]).toHaveTextContent('No');
    expect(within(table).queryByRole('button')).not.toBeInTheDocument();
  });

  it('adds evidence, with the court submission fields only when it was submitted', async () => {
    const user = userEvent.setup();
    const backend = serve({ ...base, [`GET ${EVIDENCE}`]: [], [`POST ${EVIDENCE}`]: item('e1', 'x') });
    renderWith(<CaseEvidence caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Add evidence' }));
    const form = screen.getByRole('form', { name: 'Add evidence' });
    expect(within(form).queryByLabelText('Memo or hearing it was submitted in')).not.toBeInTheDocument();
    await user.type(within(form).getByLabelText('Document name'), 'Decision 118');
    await user.selectOptions(within(form).getByLabelText('Role'), 'SUPPORTING');
    await user.selectOptions(within(form).getByLabelText('Importance'), 'HIGH');
    await user.selectOptions(await within(form).findByLabelText('Electronic copy'), 'd1');
    await user.type(within(form).getByLabelText('Document type'), 'decision');
    await user.type(within(form).getByLabelText('Issued by'), 'The authority');
    await user.type(within(form).getByLabelText('Date issued'), '2025-12-01');
    await user.type(within(form).getByLabelText('Date received'), '2026-01-02');
    await user.type(within(form).getByLabelText('Source'), 'Personnel affairs');
    await user.click(within(form).getByLabelText('Submitted to the court'));
    await user.type(within(form).getByLabelText('Date submitted'), '2026-03-30');
    await user.type(within(form).getByLabelText('Memo or hearing it was submitted in'), 'Memo of 2026-03-30');
    await user.selectOptions(within(form).getByLabelText('The opponent responded to it'), 'true');
    await user.type(within(form).getByLabelText('The opponent’s response'), 'denied');
    await user.type(within(form).getByLabelText('The court’s position'), 'pending');
    await user.type(within(form).getByLabelText('Notes'), 'key');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The evidence was saved.');
    expect(backend.to('POST', EVIDENCE)[0]?.body).toEqual({
      name: 'Decision 118',
      docType: 'decision',
      issuedOn: '2025-12-01',
      issuer: 'The authority',
      receivedOn: '2026-01-02',
      source: 'Personnel affairs',
      role: 'SUPPORTING',
      importance: 'HIGH',
      documentId: 'd1',
      submittedToCourt: true,
      submittedOn: '2026-03-30',
      submittedIn: 'Memo of 2026-03-30',
      opponentResponded: true,
      opponentResponse: 'denied',
      courtPosition: 'pending',
      notes: 'key',
    });
  }, 30000);

  it('edits evidence with its version, drops the submission when it is cleared, and explains a refusal', async () => {
    const user = userEvent.setup();
    const original = item('e1', 'Record', { version: 2, submittedToCourt: true, submittedOn: '2026-05-01', submittedIn: 'Hearing', opponentResponded: false, importance: 'LOW' });
    const backend = serve({ ...base, [`GET ${EVIDENCE}`]: [original], 'PUT /api/v1/evidence/e1': problem(409) });
    renderWith(<CaseEvidence caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Edit Record' }));
    const form = screen.getByRole('form', { name: 'Edit the evidence' });
    expect(within(form).getByLabelText('Memo or hearing it was submitted in')).toHaveValue('Hearing');
    await user.click(within(form).getByLabelText('Submitted to the court'));
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
    expect(backend.to('PUT', '/api/v1/evidence/e1')[0]?.body).toMatchObject({
      submittedToCourt: false,
      submittedOn: null,
      submittedIn: null,
      opponentResponded: false,
      version: 2,
    });
    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form', { name: 'Edit the evidence' })).not.toBeInTheDocument();
  });

  it('links one document to a fact, a defence and a claim, and removes a link (S8)', async () => {
    const user = userEvent.setup();
    const linked = item('e1', 'Receipt statement', { links: [{ id: 'l1', kind: 'FACT', targetId: 'f1', label: 'Fact 1: Notification date' }] });
    const backend = serve({
      ...base,
      [`GET ${EVIDENCE}`]: [linked],
      'POST /api/v1/evidence/e1/links': (s) => (s.body as { claimId?: string }).claimId === 'c1' ? problem(409) : { id: 'l9' },
      'DELETE /api/v1/evidence/e1/links/l1': new Response(null, { status: 204 }),
    });
    renderWith(<CaseEvidence caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Link Receipt statement' }));
    const group = screen.getByRole('group', { name: 'Link Receipt statement' });
    // Nothing is sent before a target is chosen.
    await user.click(within(group).getByRole('button', { name: 'Link' }));
    expect(backend.to('POST', '/api/v1/evidence/e1/links')).toHaveLength(0);
    await user.selectOptions(within(group).getByLabelText('Which one'), 'f2');
    await user.click(within(group).getByRole('button', { name: 'Link' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The link was added.');
    expect(backend.to('POST', '/api/v1/evidence/e1/links')[0]?.body).toEqual({ factId: 'f2' });

    await user.click(screen.getByRole('button', { name: 'Link Receipt statement' }));
    const again = screen.getByRole('group', { name: 'Link Receipt statement' });
    await user.selectOptions(within(again).getByLabelText('Link to a'), 'DEFENSE');
    await user.selectOptions(within(again).getByLabelText('Which one'), 'd1');
    await user.click(within(again).getByRole('button', { name: 'Link' }));
    await screen.findByText('The link was added.');
    expect(backend.to('POST', '/api/v1/evidence/e1/links')[1]?.body).toEqual({ defenseId: 'd1' });

    await user.click(screen.getByRole('button', { name: 'Link Receipt statement' }));
    const third = screen.getByRole('group', { name: 'Link Receipt statement' });
    await user.selectOptions(within(third).getByLabelText('Link to a'), 'CLAIM');
    await user.selectOptions(within(third).getByLabelText('Which one'), 'c1');
    await user.click(within(third).getByRole('button', { name: 'Link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
    await user.click(within(third).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('group', { name: 'Link Receipt statement' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Remove the link of Receipt statement to Fact 1: Notification date' }));
    expect(await screen.findByText('The link was removed.')).toBeVisible();
    expect(backend.to('DELETE', '/api/v1/evidence/e1/links/l1')).toHaveLength(1);
  });

  it('explains a link that cannot be removed', async () => {
    const user = userEvent.setup();
    serve({
      ...base,
      [`GET ${EVIDENCE}`]: [item('e1', 'R', { links: [{ id: 'l1', kind: 'CLAIM', targetId: 'c1', label: 'Claim 1' }] })],
      'DELETE /api/v1/evidence/e1/links/l1': problem(404),
    });
    renderWith(<CaseEvidence caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Remove the link of R to Claim 1' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists');
  });

  it('lists the gaps and links a follow-up task to a missing document', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      [`GET ${EVIDENCE}`]: [
        item('e1', 'Receipt statement', { role: 'REQUIRED', gap: 'NOT_OBTAINED', followups: [{ id: 'g1', taskId: 't1', taskTitle: 'Ask personnel affairs', taskStatus: 'OPEN' }] }),
        item('e2', 'Decision 118', { documentId: 'd1', gap: 'NOT_SUBMITTED', followups: [{ id: 'g2', taskId: null, taskTitle: null, taskStatus: null }] }),
        item('e3', 'Attendance register', { role: 'UNAVAILABLE', gap: 'NOT_OBTAINED' }),
      ],
      'POST /api/v1/evidence/e3/followups': (s) => ((s.body as { taskId: string }).taskId === 't1' ? { id: 'g3' } : problem(409)),
    });
    renderWith(<CaseEvidence caseId={CASE} canManage />);
    const heading = await screen.findByRole('heading', { name: 'Missing evidence' });
    const list = heading.nextElementSibling as HTMLElement;
    const entries = within(list).getAllByRole('listitem');
    expect(entries[0]).toHaveTextContent('needed but not obtained');
    expect(entries[0]).toHaveTextContent('Ask personnel affairs');
    expect(entries[1]).toHaveTextContent('obtained but not submitted to the court');
    // Nothing is sent before a task is chosen.
    await user.click(within(entries[2] as HTMLElement).getByRole('button', { name: 'Link the task' }));
    expect(backend.to('POST', '/api/v1/evidence/e3/followups')).toHaveLength(0);
    await user.selectOptions(within(entries[2] as HTMLElement).getByLabelText('Follow-up task for Attendance register'), 't1');
    await user.click(within(entries[2] as HTMLElement).getByRole('button', { name: 'Link the task' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The follow-up task was linked.');
    expect(backend.to('POST', '/api/v1/evidence/e3/followups')[0]?.body).toEqual({ taskId: 't1' });
  });

  it('is available in Arabic', async () => {
    serve({ ...base, [`GET ${EVIDENCE}`]: [item('e1', 'مستند', { role: 'OPPONENT' })] });
    renderWith(<CaseEvidence caseId={CASE} canManage />, { lang: 'ar' });
    expect(await screen.findByRole('heading', { name: 'الأدلة والمستندات المؤيدة للدفاع' })).toBeVisible();
    expect(await screen.findByText('مقدم من الخصم')).toBeVisible();
  });
});
