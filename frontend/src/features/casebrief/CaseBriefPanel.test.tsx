import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CASE, problem, renderWith, serve } from '../pleadings/testSupport';
import type { Brief, BriefEntry, BriefSection } from './api';
import { CaseBriefPanel } from './CaseBriefPanel';

/** The case brief panel: records only, each item with its source, "not recorded" where the records are silent. */

const PATH = `/api/v1/cases/${CASE}/brief`;
const NOT_RECORDED_CODE = 'NOT_RECORDED';

const entry = (over: Partial<BriefEntry> = {}): BriefEntry => ({
  kind: 'JUDGMENT',
  title: 'PARTIAL',
  status: 'PARTIAL',
  date: '2026-03-20',
  text: null,
  source: { type: 'JUDGMENT', id: 'j1', date: '2026-03-20', documentId: null, documentTitle: null, page: null },
  fields: {},
  children: [],
  ...over,
});
const none: BriefSection = { status: 'NOT_RECORDED', items: [], omitted: [] };
const some = (...items: BriefEntry[]): BriefSection => ({ status: 'RECORDED', items, omitted: [] });

const brief = (over: Partial<Brief> = {}): Brief => ({
  caseId: CASE,
  origin: 'RECORDS',
  modelGenerated: false,
  notice: 'x',
  asOf: '2026-11-10',
  caseData: entry({ kind: 'CASE' }),
  timeline: some(entry()),
  lastAction: some(entry()),
  nextHearing: none,
  nextRequiredActions: none,
  afterLastHearing: none,
  requests: some(
    entry({
      kind: 'CLAIM',
      title: '#1',
      status: 'REJECTED',
      text: 'SYNTH-DOC request one',
      date: null,
      source: { type: 'CLAIM', id: 'c1', date: null, documentId: null, documentTitle: null, page: null },
      children: [entry({ kind: 'RULING', title: null, status: 'REJECTED' })],
    }),
  ),
  rejectedRequests: none,
  defenses: none,
  defensesNotRuledOn: none,
  openDeadlines: none,
  appealDeadlines: some(
    entry({
      kind: 'APPEAL_DEADLINE',
      status: NOT_RECORDED_CODE,
      fields: { appealable: NOT_RECORDED_CODE, dueOn: NOT_RECORDED_CODE, final: 'false' },
    }),
  ),
  missingFiles: some(
    entry({
      kind: 'JUDGMENT',
      title: null,
      status: 'NO_FILE_ATTACHED',
      source: { type: 'JUDGMENT', id: 'j1', date: '2026-03-20', documentId: 'd1', documentTitle: 'SYNTH-DOC copy', page: null },
    }),
  ),
  facts: { status: 'NOT_PERMITTED', items: [], omitted: [] },
  evidence: none,
  ourPosition: none,
  legalTexts: none,
  linkedJudgments: none,
  similarJudgments: { status: 'RECORDED', items: [], omitted: ['EVIDENCE'] },
  ...over,
});

describe('case brief panel', () => {
  it('says on its face that it is derived from records and loads nothing until asked', async () => {
    const backend = serve({ [`GET ${PATH}`]: brief() });
    renderWith(<CaseBriefPanel caseId={CASE} />);
    expect(screen.getByRole('heading', { name: 'Case brief' })).toBeVisible();
    expect(screen.getByRole('note')).toHaveTextContent('not model-generated');
    expect(backend.sent).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Show the case brief' }));
    expect(await screen.findByRole('heading', { name: 'Requests and the position of each' })).toBeVisible();
    expect(backend.to('GET', PATH)).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Refresh the brief' })).toBeVisible();
  });

  it('shows each item with its status and the record it comes from, and never a page number', async () => {
    serve({ [`GET ${PATH}`]: brief() });
    renderWith(<CaseBriefPanel caseId={CASE} />);
    await userEvent.click(screen.getByRole('button', { name: 'Show the case brief' }));
    const requests = (await screen.findByRole('heading', { name: 'Requests and the position of each' })).closest('section');
    expect(requests).not.toBeNull();
    const scope = within(requests as HTMLElement);
    expect(scope.getByText('SYNTH-DOC request one')).toBeVisible();
    expect(scope.getAllByText('Rejected').length).toBeGreaterThan(0);
    expect(scope.getAllByText(/Source: Judgment · dated/).length).toBeGreaterThan(0);
    expect(scope.getAllByText(/page: not recorded/).length).toBeGreaterThan(0);
    const missing = (screen.getByRole('heading', { name: 'Records referencing absent files' })).closest('section') as HTMLElement;
    expect(within(missing).getByText('No file attached')).toBeVisible();
    expect(within(missing).getByText(/document: SYNTH-DOC copy/)).toBeVisible();
  });

  it('prints "Not recorded" for what the records do not hold and "not shown" for what the user may not read', async () => {
    serve({ [`GET ${PATH}`]: brief() });
    renderWith(<CaseBriefPanel caseId={CASE} />);
    await userEvent.click(screen.getByRole('button', { name: 'Show the case brief' }));
    const next = (await screen.findByRole('heading', { name: 'Next hearing' })).closest('section') as HTMLElement;
    expect(within(next).getByText('Not recorded in the case records.')).toBeVisible();
    const appeal = screen.getByRole('heading', { name: 'Appeal deadlines named by judgments' }).closest('section') as HTMLElement;
    expect(within(appeal).getAllByText('Not recorded').length).toBeGreaterThanOrEqual(3);
    const facts = screen.getByRole('heading', { name: 'Case facts' }).closest('section') as HTMLElement;
    expect(within(facts).getByText('Not shown: you may not read these records.')).toBeVisible();
    expect(screen.getByText('Left out because you may not read: Evidence item')).toBeVisible();
  });

  it('reports a failure and renders nothing for a user without the case permission', async () => {
    serve({ [`GET ${PATH}`]: problem(500) });
    const view = renderWith(<CaseBriefPanel caseId={CASE} />);
    await userEvent.click(screen.getByRole('button', { name: 'Show the case brief' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    view.unmount();
    const none = renderWith(<CaseBriefPanel caseId={CASE} />, { permissions: ['CLAIM_VIEW'] });
    expect(none.container).toBeEmptyDOMElement();
  });

  it('is in Arabic with right-to-left friendly markup on request', async () => {
    serve({ [`GET ${PATH}`]: brief() });
    renderWith(<CaseBriefPanel caseId={CASE} />, { lang: 'ar' });
    expect(screen.getByRole('heading', { name: 'ملخص القضية من السجلات' })).toBeVisible();
    expect(screen.getByRole('note')).toHaveTextContent('وليس من إنتاج نموذج');
    await userEvent.click(screen.getByRole('button', { name: 'عرض ملخص القضية' }));
    expect(await screen.findByRole('heading', { name: 'الجلسة القادمة' })).toBeVisible();
    expect(screen.getAllByText('غير مسجل').length).toBeGreaterThan(0);
  });
});
