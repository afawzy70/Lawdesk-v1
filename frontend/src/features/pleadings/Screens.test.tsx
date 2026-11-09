import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { PERMISSIONS } from '../../api/permissions';
import { ClaimStatisticsPage } from './ClaimStatisticsPage';
import { CasePleadings } from './CasePleadings';
import { DocumentUsageList } from './DocumentUsageList';
import { LookupsPage } from './LookupsPage';
import { amountText, orNull, saveMessage } from './useLoad';
import { ApiError } from '../../api/http';
import { CASE, problem, renderWith, serve } from './testSupport';

/** Managed lists (D10), claim and defence statistics, the permission gate of the case sections, and shared helpers. */

const value = (id: string, code: string, extra = {}) => ({
  id,
  listCode: 'JUDGMENT_TYPE',
  code,
  labelAr: `قيمة ${code}`,
  labelEn: `Value ${code}`,
  origin: 'PROPOSED',
  needsApproval: true,
  active: true,
  version: 0,
  ...extra,
});

describe('managed lists', () => {
  it('lists proposed values, approves, deactivates, adds and shows the history of a value', async () => {
    const user = userEvent.setup();
    const backend = serve({
      'GET /api/v1/lookups/JUDGMENT_TYPE': [value('v1', 'SUBSTANTIVE'), value('v2', 'OLD', { needsApproval: false, active: false, labelEn: null, version: 3, origin: 'DOCUMENT' })],
      'GET /api/v1/lookups/JUDGMENT_NATURE': [],
      'POST /api/v1/lookups/JUDGMENT_TYPE': (s) => ((s.body as { code: string }).code === 'DUP' ? problem(409) : value('v3', 'NEW')),
      'POST /api/v1/lookups/values/v1/approve': value('v1', 'SUBSTANTIVE', { needsApproval: false }),
      'PUT /api/v1/lookups/values/v1': value('v1', 'SUBSTANTIVE', { active: false }),
      'PUT /api/v1/lookups/values/v2': value('v2', 'OLD', { active: true }),
      'GET /api/v1/lookups/values/v1/history': [
        { rev: 2, labelAr: 'قيمة', labelEn: 'Value SUBSTANTIVE', active: true, change: 'APPROVED', changedAt: '' },
        { rev: 1, labelAr: 'قيمة', labelEn: null, active: true, change: 'CREATED', changedAt: '' },
      ],
    });
    renderWith(<LookupsPage />);
    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Value SUBSTANTIVE');
    expect(rows[1]).toHaveTextContent('Proposed');
    expect(rows[1]).toHaveTextContent('needs owner approval');
    expect(rows[2]).toHaveTextContent('From the requirements document');
    expect(rows[2]).toHaveTextContent('Deactivated');
    expect(backend.to('GET', '/api/v1/lookups/JUDGMENT_TYPE')[0]?.query).toBe('?includeInactive=true');

    await user.click(screen.getByRole('button', { name: 'Approve Value SUBSTANTIVE' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The value was approved.');
    await user.click(screen.getByRole('button', { name: 'Deactivate Value SUBSTANTIVE' }));
    await screen.findByText('The value was changed.');
    expect(backend.to('PUT', '/api/v1/lookups/values/v1')[0]?.body).toEqual({ labelAr: 'قيمة SUBSTANTIVE', labelEn: 'Value SUBSTANTIVE', active: false, version: 0 });
    await user.click(screen.getByRole('button', { name: 'Activate قيمة OLD' }));
    await screen.findByText('The value was changed.');
    expect(backend.to('PUT', '/api/v1/lookups/values/v2')[0]?.body).toMatchObject({ active: true, version: 3 });

    await user.click(screen.getByRole('button', { name: 'History of Value SUBSTANTIVE' }));
    const history = await screen.findByRole('list', { name: 'History of Value SUBSTANTIVE' });
    expect(await within(history).findByText(/2\. Approved/)).toBeVisible();
    expect(within(history).getByText(/1\. Created — قيمة/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'History of Value SUBSTANTIVE' }));
    expect(screen.queryByRole('list', { name: 'History of Value SUBSTANTIVE' })).not.toBeInTheDocument();

    const form = screen.getByRole('form', { name: 'Add a value' });
    await user.type(within(form).getByLabelText('Code'), 'NEW');
    await user.type(within(form).getByLabelText('Name in Arabic'), 'جديد');
    await user.type(within(form).getByLabelText('Name in English'), 'New');
    await user.click(within(form).getByRole('button', { name: 'Add a value' }));
    expect(await screen.findByText('The value was added as a proposal.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/lookups/JUDGMENT_TYPE')[0]?.body).toEqual({ code: 'NEW', labelAr: 'جديد', labelEn: 'New' });
    await user.type(within(form).getByLabelText('Code'), 'DUP');
    await user.click(within(form).getByRole('button', { name: 'Add a value' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
    await user.selectOptions(screen.getByLabelText('List'), 'JUDGMENT_NATURE');
    expect(await screen.findAllByRole('row')).toHaveLength(1);
  }, 30000);

  it('marks the field the server refused and explains a failed load', async () => {
    const user = userEvent.setup();
    serve({
      'GET /api/v1/lookups/JUDGMENT_TYPE': problem(500),
      'POST /api/v1/lookups/JUDGMENT_TYPE': problem(400, { errors: [{ field: 'code', code: 'Pattern' }] }),
    });
    renderWith(<LookupsPage />, { lang: 'ar' });
    expect(await screen.findByText('تعذر تحميل هذا القسم. حاول لاحقًا.')).toBeVisible();
    const form = screen.getByRole('form', { name: 'إضافة قيمة' });
    await user.click(within(form).getByRole('button', { name: 'إضافة قيمة' }));
    expect(await screen.findByText(/بعض الحقول ناقصة/)).toBeVisible();
    expect(within(form).getByLabelText('الرمز')).toHaveAttribute('aria-invalid', 'true');
  });
});

const stats = (extra = {}) => ({
  total: 4,
  byResult: [
    { result: 'ACCEPTED', count: 2, awardedTotal: '201.000' },
    { result: 'REJECTED', count: 2, awardedTotal: '0.000' },
  ],
  byStage: [
    { stage: 'FIRST_INSTANCE', count: 2 },
    { stage: 'APPEAL', count: 2 },
  ],
  byStageAndResult: [],
  ...extra,
});
const defenseStats = (extra = {}) => ({
  total: 2,
  byPosition: [{ position: 'ACCEPTED', count: 2 }],
  byStage: [{ stage: 'FIRST_INSTANCE', count: 2 }],
  mostUsed: [{ title: 'Late filing', count: 2 }],
  ...extra,
});

describe('claim result statistics', () => {
  it('counts the results and the defence positions, and filters them', async () => {
    const user = userEvent.setup();
    const backend = serve({
      'GET /api/v1/reports/claim-results': stats(),
      'GET /api/v1/reports/defense-positions': defenseStats(),
    });
    renderWith(<ClaimStatisticsPage />);
    const claims = await screen.findByRole('region', { name: 'Results on claims' });
    expect(within(claims).getByText('Total recorded: 4')).toBeVisible();
    expect(within(claims).getByText('Accepted').closest('tr')).toHaveTextContent('201.000');
    expect(within(claims).getByText('Appeal').closest('tr')).toHaveTextContent('2');
    const defenses = await screen.findByRole('region', { name: 'Positions on defences' });
    expect(within(defenses).getByText(/Late filing \(2\)/)).toBeVisible();

    await user.selectOptions(screen.getByLabelText('Stage'), 'APPEAL');
    await user.selectOptions(screen.getByLabelText('Case type'), 'CIVIL');
    await user.type(screen.getByLabelText('Judgments from'), '2026-01-01');
    await user.type(screen.getByLabelText('Judgments to'), '2026-12-31');
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    await screen.findByRole('region', { name: 'Results on claims' });
    const queries = backend.to('GET', '/api/v1/reports/claim-results').map((s) => s.query);
    expect(queries).toContain('?stage=APPEAL&caseType=CIVIL&from=2026-01-01&to=2026-12-31');
  });

  it('says when no defence was used, and shows a refusal and a failure', async () => {
    serve({
      'GET /api/v1/reports/claim-results': stats(),
      'GET /api/v1/reports/defense-positions': defenseStats({ mostUsed: [] }),
    });
    const { unmount } = renderWith(<ClaimStatisticsPage />);
    expect(await screen.findByText('Nothing recorded.')).toBeVisible();
    unmount();
    serve({ 'GET /api/v1/reports/claim-results': problem(403), 'GET /api/v1/reports/defense-positions': problem(403) });
    const second = renderWith(<ClaimStatisticsPage />);
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeVisible();
    second.unmount();
    serve({ 'GET /api/v1/reports/claim-results': problem(500), 'GET /api/v1/reports/defense-positions': defenseStats() });
    renderWith(<ClaimStatisticsPage />, { lang: 'ar' });
    expect(await screen.findByText('تعذر تحميل هذا القسم. حاول لاحقًا.')).toBeVisible();
  });
});

describe('the sections of the case page', () => {
  const lists = {
    [`GET /api/v1/cases/${CASE}/claims`]: [],
    [`GET /api/v1/cases/${CASE}/defenses`]: [],
    [`GET /api/v1/cases/${CASE}/facts`]: [],
    [`GET /api/v1/cases/${CASE}/evidence`]: [],
    [`GET /api/v1/cases/${CASE}/claim-results`]: [],
  };

  it('shows each section to a user who may view it and offers changes to one who may manage it', async () => {
    serve(lists);
    renderWith(<CasePleadings caseId={CASE} editable />, { permissions: ['CLAIM_VIEW', 'CLAIM_MANAGE', 'DEFENSE_VIEW'] });
    expect(await screen.findByRole('heading', { name: 'Claims of the case' })).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Defences' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Evidence and supporting documents' })).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Add a claim' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Add a defence' })).not.toBeInTheDocument();
  });

  it('shows nothing to a user with none of the view permissions, and nothing to change on an archived case', async () => {
    serve(lists);
    const { unmount } = renderWith(<CasePleadings caseId={CASE} editable />, { permissions: ['CASE_VIEW'] });
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
    unmount();
    renderWith(<CasePleadings caseId={CASE} editable={false} />, { permissions: PERMISSIONS });
    expect(await screen.findByRole('heading', { name: 'Evidence and supporting documents' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Add a claim' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add evidence' })).not.toBeInTheDocument();
  });
});

describe('where a document is used', () => {
  it('lists each use with what it is linked to, and nothing for an unused document', () => {
    const { container, rerender } = renderWith(
      <DocumentUsageList
        uses={[
          { documentId: 'd1', type: 'EVIDENCE', refId: 'e1', label: 'Receipt', links: [{ id: 'l1', kind: 'FACT', targetId: 'f1', label: 'Fact 1' }, { id: 'l2', kind: 'DEFENSE', targetId: 'x', label: 'Defence 1' }] },
          { documentId: 'd1', type: 'DEFENSE_MEMO', refId: 'x', label: 'Defence 1: Late filing', links: [] },
        ]}
      />,
    );
    const items = within(screen.getByRole('list', { name: 'Where this document is used' })).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Evidence: Receipt — Fact 1؛ Defence 1');
    expect(items[1]).toHaveTextContent('Defence memo: Defence 1: Late filing');
    rerender(<DocumentUsageList uses={[]} />);
    expect(container.querySelector('ul')).toBeNull();
  });
});

describe('shared helpers', () => {
  it('turns blank text into "not specified" and accepts a decimal comma', () => {
    expect(orNull('  ')).toBeNull();
    expect(orNull(' x ')).toBe('x');
    expect(amountText(' 12,5 ')).toBe('12.5');
    expect(amountText('')).toBeNull();
  });

  it('explains each kind of refusal', () => {
    const status = (code: number) => saveMessage(new ApiError(code, null));
    expect(status(400)).toBe('pl.error.fields');
    expect(status(403)).toBe('pl.error.forbidden');
    expect(status(404)).toBe('pl.error.gone');
    expect(status(409)).toBe('pl.error.stale');
    expect(status(422)).toBe('pl.error.rule');
    expect(status(500)).toBe('pl.error.save');
    expect(saveMessage(new Error('offline'))).toBe('pl.error.save');
  });
});
