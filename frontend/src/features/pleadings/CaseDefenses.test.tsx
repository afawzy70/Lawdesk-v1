import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CaseDefenses } from './CaseDefenses';
import { CASE, PICKERS, problem, renderWith, serve } from './testSupport';

/** Requirement section 17 (S3, S13): defences, their fate in each stage, the court's position, and the library. */

const DEFENSES = `/api/v1/cases/${CASE}/defenses`;

const defense = (id: string, seq: number, title: string, extra = {}) => ({
  id,
  caseId: CASE,
  seq,
  title,
  kind: 'FORMAL',
  argument: null,
  legalBasis: null,
  facts: null,
  legalTexts: null,
  memoDocumentId: null,
  memoDocumentTitle: null,
  raisedOn: null,
  raisedStageId: null,
  preparedByName: null,
  notes: null,
  templateId: null,
  evidenceCount: 0,
  version: 0,
  ...extra,
});

const TEMPLATE = { id: 't1', title: 'Late filing', kind: 'FORMAL', text: 'Standard wording', legalBasis: 'A basis', status: 'REVIEWED' };
const base = { ...PICKERS, 'GET /api/v1/defense-templates': [TEMPLATE] };

describe('defences of a case', () => {
  it('lists the defences with their kind, memo and number of supporting documents', async () => {
    serve({
      ...base,
      [`GET ${DEFENSES}`]: [
        defense('d1', 1, 'Inadmissible: filed late', { memoDocumentTitle: 'Defence memo', evidenceCount: 2 }),
        defense('d2', 2, 'Merits', { kind: 'SUBSTANTIVE' }),
      ],
    });
    renderWith(<CaseDefenses caseId={CASE} canManage />);
    const table = await screen.findByRole('table', { name: 'Defences' });
    const rows = within(table).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Inadmissible: filed late');
    expect(rows[1]).toHaveTextContent('Defence memo');
    expect(rows[1]).toHaveTextContent('2');
    expect(rows[2]).toHaveTextContent('Substantive');
    expect(rows[2]).toHaveTextContent('—');
  });

  it('says when none is recorded and when the section may not be seen', async () => {
    serve({ ...base, [`GET ${DEFENSES}`]: [] });
    const { unmount } = renderWith(<CaseDefenses caseId={CASE} canManage={false} />);
    expect(await screen.findByText('No defences are recorded yet.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Add a defence' })).not.toBeInTheDocument();
    unmount();
    serve({ ...base, [`GET ${DEFENSES}`]: problem(403) });
    renderWith(<CaseDefenses caseId={CASE} canManage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('not allowed to see');
    serve({ ...base, [`GET ${DEFENSES}`]: problem(500) });
  });

  it('starts from the library wording the member must review, and sends what the member saved', async () => {
    const user = userEvent.setup();
    const backend = serve({ ...base, [`GET ${DEFENSES}`]: [], [`POST ${DEFENSES}`]: defense('d1', 1, 'x') });
    renderWith(<CaseDefenses caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Add a defence' }));
    const form = screen.getByRole('form', { name: 'Add a defence' });
    // Choosing a library entry only fills the form; nothing is sent until the member saves.
    await user.selectOptions(await within(form).findByLabelText('Start from the library wording (review it before saving)'), 't1');
    expect(within(form).getByLabelText('Defence title')).toHaveValue('Late filing');
    expect(within(form).getByLabelText('Explanation of the defence')).toHaveValue('Standard wording');
    expect(backend.to('POST', DEFENSES)).toHaveLength(0);
    await user.clear(within(form).getByLabelText('Explanation of the defence'));
    await user.type(within(form).getByLabelText('Explanation of the defence'), 'Adapted to the facts');
    await user.type(within(form).getByLabelText('Related facts'), 'Notified on X');
    await user.type(within(form).getByLabelText('Legal texts relied on'), 'Text');
    await user.selectOptions(within(form).getByLabelText('Memo that contains it'), 'd1');
    await user.type(within(form).getByLabelText('Date raised'), '2026-03-30');
    await user.selectOptions(within(form).getByLabelText('Stage it was raised in'), 'p1');
    await user.type(within(form).getByLabelText('Notes'), 'n');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('status')).toHaveTextContent('The defence was saved.');
    expect(backend.to('POST', DEFENSES)[0]?.body).toEqual({
      title: 'Late filing',
      kind: 'FORMAL',
      argument: 'Adapted to the facts',
      legalBasis: 'A basis',
      facts: 'Notified on X',
      legalTexts: 'Text',
      memoDocumentId: 'd1',
      raisedOn: '2026-03-30',
      raisedStageId: 'p1',
      notes: 'n',
      templateId: 't1',
    });
  });

  it('keeps the title the member typed when a template is chosen, and clears the choice when it is removed', async () => {
    const user = userEvent.setup();
    serve({ ...base, [`GET ${DEFENSES}`]: [] });
    renderWith(<CaseDefenses caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Add a defence' }));
    const form = screen.getByRole('form', { name: 'Add a defence' });
    await user.type(within(form).getByLabelText('Defence title'), 'My own title');
    const choice = await within(form).findByLabelText('Start from the library wording (review it before saving)');
    await user.selectOptions(choice, 't1');
    expect(within(form).getByLabelText('Defence title')).toHaveValue('My own title');
    await user.selectOptions(choice, '');
    expect(choice).toHaveValue('');
  });

  it('edits a defence with the version it was read at and explains a refusal', async () => {
    const user = userEvent.setup();
    const original = defense('d1', 1, 'First', { version: 4, argument: 'a' });
    const backend = serve({ ...base, [`GET ${DEFENSES}`]: [original], [`PUT /api/v1/defenses/d1`]: problem(409) });
    renderWith(<CaseDefenses caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Edit defence 1' }));
    const form = screen.getByRole('form', { name: 'Edit the defence' });
    expect(within(form).queryByLabelText('Start from the library wording (review it before saving)')).not.toBeInTheDocument();
    await user.clear(within(form).getByLabelText('Defence title'));
    await user.type(within(form).getByLabelText('Defence title'), 'Second');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
    expect(backend.to('PUT', '/api/v1/defenses/d1')[0]?.body).toMatchObject({ title: 'Second', version: 4 });
  });

  it('shows the stages of a defence, records one, and shows the court position of each judgment (S3, S13)', async () => {
    const user = userEvent.setup();
    const d1 = defense('d1', 1, 'Inadmissible', { preparedByName: 'A member', argument: 'Filed late', legalBasis: 'Article', memoDocumentTitle: 'Memo' });
    const detail = {
      defense: d1,
      assertions: [{ defenseId: 'd1', defenseSeq: 1, proceedingId: 'p1', stage: 'FIRST_INSTANCE', status: 'RAISED', onDate: null, memoDocumentTitle: null }],
      positions: [{ judgmentId: 'j1', proceedingId: 'p1', stage: 'FIRST_INSTANCE', judgmentDate: '2026-03-01', defenseId: 'd1', defenseSeq: 1, position: 'REJECTED' }],
      evidence: [{ evidenceId: 'e1', name: 'Receipt statement', role: 'SUPPORTING', importance: 'HIGH', documentId: 'd1', submittedToCourt: true }],
    };
    const backend = serve({
      ...base,
      [`GET ${DEFENSES}`]: [d1],
      'GET /api/v1/defenses/d1': detail,
      'PUT /api/v1/defenses/d1/assertions/p2': { ...detail.assertions[0], proceedingId: 'p2', stage: 'APPEAL', status: 'RE_ASSERTED' },
    });
    renderWith(<CaseDefenses caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Details of defence 1' }));
    const panel = await screen.findByRole('group', { name: 'Defence 1' });
    expect(within(panel).getByText('A member')).toBeVisible();
    const rows = within(panel).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('First instance 100/2026');
    expect(rows[1]).toHaveTextContent('Rejected (01/03/2026)');
    expect(rows[2]).toHaveTextContent('Appeal 7/2026');
    expect(rows[2]).toHaveTextContent('Not recorded');
    expect(within(panel).getByText(/Receipt statement — Supports the authority — submitted/)).toBeVisible();

    await user.selectOptions(within(panel).getByLabelText('Raised in Appeal 7/2026'), 'RE_ASSERTED');
    expect(await screen.findByRole('status')).toHaveTextContent('The stage was recorded.');
    expect(backend.to('PUT', '/api/v1/defenses/d1/assertions/p2')[0]?.body).toEqual({ status: 'RE_ASSERTED', onDate: null });
    await user.selectOptions(within(panel).getByLabelText('Raised in Appeal 7/2026'), '');
    expect(backend.to('PUT', '/api/v1/defenses/d1/assertions/p2')).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Details of defence 1' }));
    expect(screen.queryByRole('group', { name: 'Defence 1' })).not.toBeInTheDocument();
  });

  it('shows a reader the stages without controls, and explains a refused assertion', async () => {
    const user = userEvent.setup();
    const d1 = defense('d1', 1, 'Inadmissible');
    const detail = {
      defense: d1,
      assertions: [{ defenseId: 'd1', defenseSeq: 1, proceedingId: 'p1', stage: 'FIRST_INSTANCE', status: 'RAISED', onDate: null, memoDocumentTitle: null }],
      positions: [],
      evidence: [],
    };
    serve({ ...base, [`GET ${DEFENSES}`]: [d1], 'GET /api/v1/defenses/d1': detail });
    renderWith(<CaseDefenses caseId={CASE} canManage={false} />);
    await user.click(await screen.findByRole('button', { name: 'Details of defence 1' }));
    const panel = await screen.findByRole('group', { name: 'Defence 1' });
    expect(within(panel).queryByRole('combobox')).not.toBeInTheDocument();
    expect(within(panel).getByText('Raised')).toBeVisible();
    expect(within(panel).getByText('Not specified', { selector: 'td' })).toBeVisible();
    expect(within(panel).getByText('No document is linked to this defence yet.')).toBeVisible();
  });

  it('explains a refused stage assertion', async () => {
    const user = userEvent.setup();
    const d1 = defense('d1', 1, 'Inadmissible');
    serve({
      ...base,
      [`GET ${DEFENSES}`]: [d1],
      'GET /api/v1/defenses/d1': { defense: d1, assertions: [], positions: [], evidence: [] },
      'PUT /api/v1/defenses/d1/assertions/p1': problem(403),
    });
    renderWith(<CaseDefenses caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Details of defence 1' }));
    const panel = await screen.findByRole('group', { name: 'Defence 1' });
    await user.selectOptions(within(panel).getByLabelText('Raised in First instance 100/2026'), 'RAISED');
    expect(await screen.findByRole('alert')).toHaveTextContent('permission to change this case');
  });
});
