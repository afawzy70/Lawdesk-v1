import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CaseProceedings } from '../proceedings/CaseProceedings';
import { CASE, claim, DOCUMENT, page, problem, proceeding, renderWith, serve } from './testSupport';

/** Sections 5, 9 and 17: recording a judgment shows every registered claim and defence and takes a result for each. */

const FIRST = proceeding('p1', 'FIRST_INSTANCE', '100', { hasJudgment: true });
const APPEAL = proceeding('p2', 'APPEAL', '7', { parentProceedingId: 'p1' });

const NO_DETAILS = {
  chamber: null,
  judgmentTypeId: null,
  judgmentTypeLabelAr: null,
  judgmentTypeLabelEn: null,
  natureId: null,
  natureLabelAr: null,
  natureLabelEn: null,
  operativeText: null,
  reasons: null,
  costsAmount: null,
  lawyerFeesAmount: null,
  appealable: null,
  notifiedOn: null,
  status: null,
  copyDocumentId: null,
};

const judgment = (extra = {}) => ({
  id: 'j1',
  proceedingId: 'p1',
  caseId: CASE,
  judgmentDate: '2026-03-01',
  outcome: 'PARTIAL',
  summary: 'Partly accepted.',
  final: false,
  details: NO_DETAILS,
  version: 5,
  createdAt: '',
  updatedAt: '',
  ...extra,
});

const TYPES = [
  { id: 'type1', listCode: 'JUDGMENT_TYPE', code: 'SUBSTANTIVE', labelAr: 'حكم موضوعي', labelEn: 'Substantive judgment', origin: 'PROPOSED', needsApproval: true, active: true, version: 0 },
  { id: 'type2', listCode: 'JUDGMENT_TYPE', code: 'OTHER', labelAr: 'آخر', labelEn: null, origin: 'PROPOSED', needsApproval: false, active: true, version: 0 },
];
const NATURES = [{ id: 'nat1', listCode: 'JUDGMENT_NATURE', code: 'FINAL_RULING', labelAr: 'نهائي', labelEn: 'Final ruling', origin: 'DOCUMENT', needsApproval: false, active: true, version: 0 }];

const claims = [claim('c1', 1, 'Compensation'), claim('c2', 2, 'Refund'), claim('c3', 3, 'Costs')];
const defenses = [{ id: 'd1', seq: 1, title: 'Late filing' }];

const base = {
  [`GET /api/v1/cases/${CASE}/proceedings`]: page([FIRST, APPEAL]),
  [`GET /api/v1/cases/${CASE}/judgments`]: page([judgment()]),
  [`GET /api/v1/cases/${CASE}/special-decisions`]: page([]),
  [`GET /api/v1/cases/${CASE}/claims`]: claims,
  [`GET /api/v1/cases/${CASE}/defenses`]: defenses,
  [`GET /api/v1/cases/${CASE}/documents`]: page([DOCUMENT]),
  'GET /api/v1/lookups/JUDGMENT_TYPE': TYPES,
  'GET /api/v1/lookups/JUDGMENT_NATURE': NATURES,
};

describe('recording a judgment with a result for every claim', () => {
  it('shows every registered claim and defence, and sends the details, the results and the positions (S1)', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      'POST /api/v1/proceedings/p2/judgment': judgment({ id: 'j2', proceedingId: 'p2' }),
      'PUT /api/v1/judgments/j2/claim-results': [],
      'PUT /api/v1/judgments/j2/defense-positions': [],
    });
    renderWith(<CaseProceedings caseId={CASE} />);
    const appeal = await screen.findByRole('article', { name: 'Appeal 7/2026' });
    await user.click(within(appeal).getByRole('button', { name: 'Record judgment' }));
    const form = within(appeal).getByRole('form', { name: 'Record judgment for 7/2026' });

    await user.type(within(form).getByLabelText('Judgment date'), '2026-09-28');
    await user.selectOptions(within(form).getByLabelText('Outcome'), 'PARTIAL');
    await user.type(within(form).getByLabelText('Chamber'), 'Third chamber');
    await user.selectOptions(await within(form).findByRole('combobox', { name: 'Judgment type' }), 'type1');
    expect(within(form).getByRole('option', { name: 'Substantive judgment (needs owner approval)' })).toBeVisible();
    expect(within(form).getByRole('option', { name: 'آخر' })).toBeVisible();
    await user.selectOptions(within(form).getByLabelText('Judgment nature'), 'nat1');
    await user.type(within(form).getByLabelText('Operative text'), 'The full operative text');
    await user.type(within(form).getByLabelText('Reasons', { selector: 'textarea' }), 'The reasons');
    await user.type(within(form).getByLabelText('Costs (KWD)'), '120,5');
    await user.type(within(form).getByLabelText('Lawyer fees (KWD)'), '300.000');
    await user.selectOptions(within(form).getByLabelText('Open to appeal'), 'true');
    await user.type(within(form).getByLabelText('Date notified'), '2026-09-30');
    await user.selectOptions(within(form).getByLabelText('Judgment status'), 'NOTIFIED');
    await user.selectOptions(await within(form).findByLabelText('Copy of the judgment'), 'd1');

    // All three claims are listed; the user decides a result for two of them.
    expect(await within(form).findByRole('group', { name: 'Claim 1' })).toBeVisible();
    expect(within(form).getByRole('group', { name: 'Claim 2' })).toBeVisible();
    expect(within(form).getByRole('group', { name: 'Claim 3' })).toBeVisible();
    expect(within(within(form).getByRole('group', { name: 'Claim 1' })).getAllByRole('option')).toHaveLength(13);
    await user.selectOptions(within(form).getByLabelText('Result on claim 1'), 'ACCEPTED');
    await user.type(within(form).getByLabelText('Amount awarded on claim 1 (KWD)'), '1500,250');
    await user.type(within(form).getByLabelText('Reasons on claim 1'), 'r1');
    await user.selectOptions(within(form).getByLabelText('Result on claim 2'), 'REJECTED');
    await user.selectOptions(within(form).getByLabelText('Position on defence 1'), 'REJECTED');
    await user.type(within(form).getByLabelText('Court reasons on defence 1'), 'dr');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Saved.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/proceedings/p2/judgment')[0]?.body).toEqual({
      judgmentDate: '2026-09-28',
      outcome: 'PARTIAL',
      final: false,
      summary: null,
      details: {
        chamber: 'Third chamber',
        judgmentTypeId: 'type1',
        natureId: 'nat1',
        operativeText: 'The full operative text',
        reasons: 'The reasons',
        costsAmount: '120.5',
        lawyerFeesAmount: '300.000',
        appealable: true,
        notifiedOn: '2026-09-30',
        status: 'NOTIFIED',
        copyDocumentId: 'd1',
      },
    });
    expect(backend.to('PUT', '/api/v1/judgments/j2/claim-results')[0]?.body).toEqual({
      results: [
        { claimId: 'c1', result: 'ACCEPTED', operative: null, reasons: 'r1', awardedAmount: '1500.250', notes: null },
        { claimId: 'c2', result: 'REJECTED', operative: null, reasons: null, awardedAmount: null, notes: null },
      ],
    });
    expect(backend.to('PUT', '/api/v1/judgments/j2/defense-positions')[0]?.body).toEqual({
      positions: [{ defenseId: 'd1', position: 'REJECTED', reasons: 'dr', operative: null }],
    });
    // Typing the whole form character by character is slow under load.
  }, 30000);

  it('does not record the judgment twice when only saving the results failed', async () => {
    const user = userEvent.setup();
    let attempts = 0;
    const backend = serve({
      ...base,
      'POST /api/v1/proceedings/p2/judgment': judgment({ id: 'j2', proceedingId: 'p2' }),
      'PUT /api/v1/judgments/j2/claim-results': () => (++attempts === 1 ? problem(500) : []),
    });
    renderWith(<CaseProceedings caseId={CASE} />);
    const appeal = await screen.findByRole('article', { name: 'Appeal 7/2026' });
    await user.click(within(appeal).getByRole('button', { name: 'Record judgment' }));
    const form = within(appeal).getByRole('form', { name: 'Record judgment for 7/2026' });
    await user.type(within(form).getByLabelText('Judgment date'), '2026-09-28');
    await user.selectOptions(within(form).getByLabelText('Outcome'), 'PARTIAL');
    await user.selectOptions(await within(form).findByLabelText('Result on claim 3'), 'DEFERRED');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByRole('alert')).toBeVisible();
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Saved.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/proceedings/p2/judgment')).toHaveLength(1);
    expect(backend.to('PUT', '/api/v1/judgments/j2/claim-results')).toHaveLength(2);
  });

  it('says so when the case has no registered claim', async () => {
    const user = userEvent.setup();
    serve({ ...base, [`GET /api/v1/cases/${CASE}/claims`]: [], [`GET /api/v1/cases/${CASE}/defenses`]: [] });
    renderWith(<CaseProceedings caseId={CASE} />);
    const appeal = await screen.findByRole('article', { name: 'Appeal 7/2026' });
    await user.click(within(appeal).getByRole('button', { name: 'Record judgment' }));
    expect(await within(appeal).findByText('No claims are registered for this case.')).toBeVisible();
    expect(within(appeal).queryByText('Position on each defence')).not.toBeInTheDocument();
  });
});

describe('completing a judgment recorded before the extended fields', () => {
  it('shows what is not recorded, prefills the recorded results, and updates with the version it read (S13)', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      'GET /api/v1/judgments/j1/claim-results': [
        { claimId: 'c1', claimSeq: 1, claimText: 'Compensation', claimStatus: 'STANDING', result: 'REJECTED', operative: null, reasons: 'first-instance reasons', awardedAmount: null, notes: null },
        { claimId: 'c2', claimSeq: 2, claimText: 'Refund', claimStatus: 'STANDING', result: 'PARTIAL', operative: null, reasons: null, awardedAmount: '75.125', notes: null },
        { claimId: 'c3', claimSeq: 3, claimText: 'Costs', claimStatus: 'STANDING', result: null, operative: null, reasons: null, awardedAmount: null, notes: null },
      ],
      'GET /api/v1/judgments/j1/defense-positions': [
        { defenseId: 'd1', defenseSeq: 1, defenseTitle: 'Late filing', position: 'ACCEPTED', reasons: 'ok', operative: null },
      ],
      'PUT /api/v1/judgments/j1': judgment({ version: 6 }),
      'PUT /api/v1/judgments/j1/claim-results': [],
      'PUT /api/v1/judgments/j1/defense-positions': [],
    });
    renderWith(<CaseProceedings caseId={CASE} />);
    const first = await screen.findByRole('article', { name: 'First instance 100/2026' });
    expect(within(first).getByText('The detailed judgment (operative text, reasons, amounts) is not recorded.')).toBeVisible();
    await user.click(within(first).getByRole('button', { name: 'Complete the judgment and its results' }));
    const form = within(first).getByRole('form', { name: 'Record judgment for 100/2026' });
    expect(within(form).getByLabelText('Judgment date')).toHaveValue('2026-03-01');
    expect(await within(form).findByLabelText('Result on claim 1')).toHaveValue('REJECTED');
    expect(within(form).getByLabelText('Amount awarded on claim 2 (KWD)')).toHaveValue('75.125');
    expect(within(form).getByLabelText('Result on claim 3')).toHaveValue('');
    expect(within(form).getByLabelText('Position on defence 1')).toHaveValue('ACCEPTED');

    // The summary may be copied into the operative text, on request only.
    expect(within(form).getByLabelText('Operative text')).toHaveValue('');
    await user.click(within(form).getByRole('button', { name: 'Use the summary as the operative text' }));
    expect(within(form).getByLabelText('Operative text')).toHaveValue('Partly accepted.');
    expect(within(form).queryByRole('button', { name: 'Use the summary as the operative text' })).not.toBeInTheDocument();
    await user.selectOptions(within(form).getByLabelText('Result on claim 3'), 'NOT_DECIDED');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Saved.')).toBeVisible();
    const put = backend.to('PUT', '/api/v1/judgments/j1')[0]?.body as { version: number; details: { operativeText: string; chamber: null } };
    expect(put.version).toBe(5);
    expect(put.details.operativeText).toBe('Partly accepted.');
    expect(put.details.chamber).toBeNull();
    const sent = backend.to('PUT', '/api/v1/judgments/j1/claim-results')[0]?.body as { results: { claimId: string; result: string }[] };
    expect(sent.results.map((r) => `${r.claimId}:${r.result}`)).toEqual(['c1:REJECTED', 'c2:PARTIAL', 'c3:NOT_DECIDED']);
  });

  it('shows the recorded details of a judgment and offers nothing for the ones never recorded', async () => {
    serve({
      ...base,
      [`GET /api/v1/cases/${CASE}/judgments`]: page([
        judgment({
          details: {
            chamber: 'Third chamber',
            judgmentTypeId: 'type1',
            judgmentTypeLabelAr: 'حكم موضوعي',
            judgmentTypeLabelEn: 'Substantive judgment',
            natureId: null,
            natureLabelAr: null,
            natureLabelEn: null,
            operativeText: 'Operative',
            reasons: null,
            costsAmount: '120.000',
            lawyerFeesAmount: '300.000',
            appealable: false,
            notifiedOn: '2026-03-05',
            status: 'ISSUED',
            copyDocumentId: null,
          },
        }),
      ]),
    });
    renderWith(<CaseProceedings caseId={CASE} />);
    const first = await screen.findByRole('article', { name: 'First instance 100/2026' });
    expect(within(first).getByText('Third chamber')).toBeVisible();
    expect(within(first).getByText('Substantive judgment')).toBeVisible();
    expect(within(first).getByText('120.000 KWD')).toBeVisible();
    expect(within(first).getByText('Issued')).toBeVisible();
    expect(within(first).getByText('No')).toBeVisible();
    expect(within(first).queryByText('Reasons')).not.toBeInTheDocument();
  });

  it('shows the type of a judgment in Arabic', async () => {
    serve({
      ...base,
      [`GET /api/v1/cases/${CASE}/judgments`]: page([
        judgment({
          details: {
            chamber: null,
            judgmentTypeId: 'type1',
            judgmentTypeLabelAr: 'حكم موضوعي',
            judgmentTypeLabelEn: null,
            natureId: 'n',
            natureLabelAr: 'نهائي',
            natureLabelEn: null,
            operativeText: null,
            reasons: null,
            costsAmount: null,
            lawyerFeesAmount: null,
            appealable: true,
            notifiedOn: null,
            status: null,
            copyDocumentId: null,
          },
        }),
      ]),
    });
    renderWith(<CaseProceedings caseId={CASE} />, { lang: 'ar' });
    expect(await screen.findByText('حكم موضوعي')).toBeVisible();
    expect(screen.getByText('نهائي', { selector: 'dd' })).toBeVisible();
    expect(screen.getByText('نعم')).toBeVisible();
  });
});
