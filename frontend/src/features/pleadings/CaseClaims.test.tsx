import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CaseClaims } from './CaseClaims';
import { CASE, claim, page, PICKERS, problem, proceeding, renderWith, serve } from './testSupport';

/** Requirements section 5 (S1, S2, S13): the claims of a case, their amendment with history, and the results per judgment. */

const CLAIMS = `/api/v1/cases/${CASE}/claims`;
const base = {
  ...PICKERS,
  [`GET /api/v1/cases/${CASE}/judgments`]: page([]),
  [`GET /api/v1/cases/${CASE}/claim-results`]: [],
};

describe('claims of a case', () => {
  it('lists the claims with their kind, status, submitter, stage and source document', async () => {
    serve({
      ...base,
      [`GET ${CLAIMS}`]: [
        claim('c1', 1, 'Compensation for material damage', {
          submittedByPartyName: 'Synthetic plaintiff',
          stageId: 'p1',
          sourceDocumentTitle: 'Statement of claim',
          status: 'AMENDED',
          kind: 'ALTERNATIVE',
        }),
        claim('c2', 2, 'Costs', { kind: 'ANCILLARY' }),
      ],
    });
    renderWith(<CaseClaims caseId={CASE} canManage />);
    const table = await screen.findByRole('table', { name: 'Claims of the case' });
    const rows = within(table).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Compensation for material damage');
    expect(rows[1]).toHaveTextContent('Alternative');
    expect(rows[1]).toHaveTextContent('Amended');
    expect(rows[1]).toHaveTextContent('Synthetic plaintiff');
    expect(rows[1]).toHaveTextContent('First instance 100/2026');
    expect(rows[1]).toHaveTextContent('Statement of claim');
    expect(rows[2]).toHaveTextContent('Ancillary');
    expect(rows[2]).toHaveTextContent('—');
  });

  it('says when there are no claims, and when the section may not be seen', async () => {
    serve({ ...base, [`GET ${CLAIMS}`]: [] });
    const { unmount } = renderWith(<CaseClaims caseId={CASE} canManage={false} />);
    expect(await screen.findByText('No claims are recorded yet.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Add a claim' })).not.toBeInTheDocument();
    unmount();
    serve({ ...base, [`GET ${CLAIMS}`]: problem(403) });
    renderWith(<CaseClaims caseId={CASE} canManage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('You are not allowed to see this section');
  });

  it('shows a load failure that is not a refusal', async () => {
    serve({ ...base, [`GET ${CLAIMS}`]: problem(500) });
    renderWith(<CaseClaims caseId={CASE} canManage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load this section');
  });

  it('adds a claim with its submitter, stage, document and date', async () => {
    const user = userEvent.setup();
    const backend = serve({ ...base, [`GET ${CLAIMS}`]: [], [`POST ${CLAIMS}`]: claim('c1', 1, 'x') });
    renderWith(<CaseClaims caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Add a claim' }));
    const form = screen.getByRole('form', { name: 'Add a claim' });
    await user.type(within(form).getByLabelText('Claim text'), 'Refund of the fees');
    await user.selectOptions(within(form).getByLabelText('Kind'), 'URGENT');
    await user.selectOptions(await within(form).findByLabelText('Submitted by'), 'party1');
    await user.type(within(form).getByLabelText('Date submitted'), '2026-01-12');
    await user.selectOptions(within(form).getByLabelText('Stage'), 'p1');
    await user.selectOptions(within(form).getByLabelText('Document containing it'), 'd1');
    await user.type(within(form).getByLabelText('Notes'), 'n');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('status')).toHaveTextContent('The claim was added.');
    expect(backend.to('POST', CLAIMS)[0]?.body).toEqual({
      text: 'Refund of the fees',
      kind: 'URGENT',
      submittedByPartyId: 'party1',
      submittedOn: '2026-01-12',
      stageId: 'p1',
      sourceDocumentId: 'd1',
      notes: 'n',
    });
    expect(screen.queryByRole('form', { name: 'Add a claim' })).not.toBeInTheDocument();
  });

  it('marks the field the server refused and keeps the form open', async () => {
    const user = userEvent.setup();
    serve({
      ...base,
      [`GET ${CLAIMS}`]: [],
      [`POST ${CLAIMS}`]: problem(400, { errors: [{ field: 'text', code: 'NotBlank' }] }),
    });
    renderWith(<CaseClaims caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Add a claim' }));
    const form = screen.getByRole('form', { name: 'Add a claim' });
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Some fields are missing');
    expect(within(form).getByLabelText('Claim text')).toHaveAttribute('aria-invalid', 'true');
    await user.click(within(form).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form', { name: 'Add a claim' })).not.toBeInTheDocument();
  });

  it('amends a claim with a reason and the version it was read at (S2)', async () => {
    const user = userEvent.setup();
    const original = claim('c1', 1, 'Compensation for material and moral damage', { version: 3, revision: 2 });
    const backend = serve({ ...base, [`GET ${CLAIMS}`]: [original], [`PUT /api/v1/claims/c1`]: original });
    renderWith(<CaseClaims caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Amend claim 1' }));
    const form = screen.getByRole('form', { name: 'Amend the claim' });
    const text = within(form).getByLabelText('Claim text');
    await user.clear(text);
    await user.type(text, 'Compensation for material damage');
    await user.selectOptions(within(form).getByLabelText('Status'), 'AMENDED');
    await user.type(within(form).getByLabelText('Reason for the change'), 'moral damage dropped');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('status')).toHaveTextContent('previous version is kept');
    expect(backend.to('PUT', '/api/v1/claims/c1')[0]?.body).toMatchObject({
      text: 'Compensation for material damage',
      status: 'AMENDED',
      reason: 'moral damage dropped',
      version: 3,
    });
  });

  it('refuses an amendment someone else got to first', async () => {
    const user = userEvent.setup();
    serve({ ...base, [`GET ${CLAIMS}`]: [claim('c1', 1, 'x')], [`PUT /api/v1/claims/c1`]: problem(409) });
    renderWith(<CaseClaims caseId={CASE} canManage />);
    await user.click(await screen.findByRole('button', { name: 'Amend claim 1' }));
    await user.click(within(screen.getByRole('form', { name: 'Amend the claim' })).getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
  });

  it('shows the revisions of a claim: previous text, status, reason and date', async () => {
    const user = userEvent.setup();
    serve({
      ...base,
      [`GET ${CLAIMS}`]: [claim('c1', 1, 'Current text', { revision: 2, version: 1 })],
      [`GET /api/v1/claims/c1/revisions`]: [
        { rev: 2, text: 'Current text', kind: 'PRIMARY', status: 'AMENDED', reason: 'dropped', changedBy: 'u', changedAt: '2026-05-12T09:00:00Z' },
        { rev: 1, text: 'Original text', kind: 'PRIMARY', status: 'STANDING', reason: null, changedBy: 'u', changedAt: '2026-02-10T09:00:00Z' },
      ],
    });
    renderWith(<CaseClaims caseId={CASE} canManage={false} />);
    await user.click(await screen.findByRole('button', { name: 'History of claim 1' }));
    const revisions = await screen.findByText('Revisions of claim 1');
    const table = within(revisions.parentElement as HTMLElement).getByRole('table');
    expect(await within(table).findByText('Original text')).toBeVisible();
    expect(within(table).getByText('dropped')).toBeVisible();
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    await user.click(screen.getByRole('button', { name: 'History of claim 1' }));
    expect(screen.queryByText('Revisions of claim 1')).not.toBeInTheDocument();
  });

  it('shows each judgment on its own with the result of every claim, and says when none is recorded (S1, S13)', async () => {
    serve({
      ...base,
      [`GET ${CLAIMS}`]: [claim('c1', 1, 'Compensation'), claim('c2', 2, 'Refund')],
      [`GET /api/v1/cases/${CASE}/proceedings`]: page([proceeding('p1', 'FIRST_INSTANCE', '100'), proceeding('p2', 'APPEAL', '7')]),
      [`GET /api/v1/cases/${CASE}/judgments`]: page([
        { id: 'j1', proceedingId: 'p1', judgmentDate: '2026-03-01' },
        { id: 'j2', proceedingId: 'p2', judgmentDate: '2026-06-01' },
        { id: 'j3', proceedingId: 'unknown', judgmentDate: '2026-07-01' },
      ]),
      [`GET /api/v1/cases/${CASE}/claim-results`]: [
        { judgmentId: 'j1', claimId: 'c1', result: 'REJECTED', awardedAmount: null },
        { judgmentId: 'j1', claimId: 'c2', result: 'PARTIAL', awardedAmount: '75.125' },
        { judgmentId: 'j2', claimId: 'c1', result: 'ACCEPTED', awardedAmount: '900.000' },
      ],
    });
    renderWith(<CaseClaims caseId={CASE} canManage />);
    const heading = await screen.findByText('The result of each judgment on each claim');
    const table = within(heading.parentElement as HTMLElement).getByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(rows[0]).toHaveTextContent('First instance — 01/03/2026');
    expect(rows[0]).toHaveTextContent('Appeal — 01/06/2026');
    expect(rows[1]).toHaveTextContent('Rejected');
    expect(rows[1]).toHaveTextContent('Accepted (900.000 KWD)');
    expect(rows[1]).toHaveTextContent('Not recorded');
    expect(rows[2]).toHaveTextContent('Partly accepted (75.125 KWD)');
  });

  it('is available in Arabic', async () => {
    serve({ ...base, [`GET ${CLAIMS}`]: [claim('c1', 1, 'طلب')] });
    renderWith(<CaseClaims caseId={CASE} canManage />, { lang: 'ar' });
    expect(await screen.findByRole('heading', { name: 'طلبات الدعوى' })).toBeVisible();
    expect(await screen.findByText('قائم')).toBeVisible();
  });
});
