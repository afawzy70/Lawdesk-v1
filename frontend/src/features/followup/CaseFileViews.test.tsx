import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import type { CaseSummaryData, TimelineEntry } from './api';
import { CaseSummaryPanel } from './CaseSummaryPanel';
import { CaseTimelinePanel } from './CaseTimelinePanel';
import { CASE, mount, problem, serve } from './testSupport';

/** LD-053, requirements section 21: the executive summary and the unified timeline on the case page. */

const hearing = { id: 'h1', hearingDate: '2026-11-20', hearingTime: null, status: 'SCHEDULED', purpose: 'PLEADING', kind: 'COURT', decision: null, adjournmentReason: null, noNextDate: false };

function summary(overrides: Partial<CaseSummaryData> = {}): CaseSummaryData {
  return {
    caseId: CASE, referenceNo: 'LD-2026-00001', automatedNo: null, title: 'Case', subject: null, status: 'OPEN', authorityPosition: 'DEFENDANT',
    claimedAmount: '12500.500', primaryMember: null, alternateMember: null,
    parties: [{ name: 'Ali', role: 'OPPONENT' }], involvedUnits: [{ name: 'Services / Admin', primary: true }],
    lastProcedure: { type: 'CASE_UPDATED', summary: 'Edited', occurredAt: '2026-11-02T08:00:00Z' },
    hearings: { total: 3, held: 1, adjourned: 1, cancelled: 0, scheduled: 1, awaitingResult: 0, followUp: 0, lastHearing: { ...hearing, status: 'HELD', decision: 'Reserved', kind: 'EXPERT' }, nextHearing: hearing },
    lastJudgment: { id: 'j1', judgmentDate: '2026-11-01', outcome: 'PARTIAL', summary: 'Partly accepted' },
    risk: { versionNo: 2, level: 'HIGH', potentialLoss: '4000.125', openRemediation: 1 },
    infoRequests: { open: 1, overdue: 1, incompleteReplies: 0, pending: [{ id: 'r1', requestNo: 'IR-1', subject: 'Missing file', status: 'SENT', dueOn: '2026-11-01', overdue: true }, { id: 'r2', requestNo: 'IR-2', subject: 'No date', status: 'SENT', dueOn: null, overdue: false }] },
    tasks: { open: 1, overdue: 0, items: [{ id: 't1', title: 'Prepare memo', dueDate: null }] },
    links: { count: 1, hiddenCount: 2 },
    pendingModules: ['CLAIMS', 'DEADLINES'],
    ...overrides,
  };
}

describe('executive summary', () => {
  it('shows what is stored and names what is not recorded yet', async () => {
    serve(() => jsonResponse(summary()));
    mount(<CaseSummaryPanel caseId={CASE} generation={0} />);
    expect(await screen.findByText('Ali (opponent)')).toBeInTheDocument();
    expect(screen.getByText('Services / Admin')).toBeInTheDocument();
    expect(screen.getByText('Defendant')).toBeInTheDocument();
    expect(screen.getByText('12,500.500')).toBeInTheDocument();
    expect(screen.getByText(/Edited \(/)).toBeInTheDocument();
    expect(screen.getByText(/Experts — Reserved/)).toBeInTheDocument();
    expect(screen.getByText(/20\/11\/2026 · Court/)).toBeInTheDocument();
    expect(screen.getByText('3 hearings, 1 adjourned, 1 held')).toBeInTheDocument();
    expect(screen.getByText(/PARTIAL — Partly accepted/)).toBeInTheDocument();
    expect(screen.getByText(/High · potential loss 4,000.125 KWD/)).toBeInTheDocument();
    expect(screen.getByText(/1 request\(s\) waiting, 1 overdue/)).toBeInTheDocument();
    expect(screen.getByText(/IR-1 — Missing file \(01\/11\/2026\)/)).toBeInTheDocument();
    expect(screen.getByText('IR-2 — No date')).toBeInTheDocument();
    expect(screen.getByText('Prepare memo')).toBeInTheDocument();
    expect(screen.getByText('1 visible, 2 not visible to you')).toBeInTheDocument();
    expect(screen.getByText('claims، legal deadlines')).toBeInTheDocument();
  });

  it('summarises the open deadlines when the user may open them', async () => {
    serve(() => jsonResponse(summary({
      pendingModules: ['CLAIMS'],
      deadlines: {
        open: 3, overdue: 1, unapproved: 2,
        items: [
          { id: 'd1', action: 'File the appeal', dueOn: '2026-12-01', calcStatus: 'APPROVED', overdue: false },
          { id: 'd2', action: 'Reply later', dueOn: null, calcStatus: 'MANUAL', overdue: false },
        ],
      },
    })));
    mount(<CaseSummaryPanel caseId={CASE} generation={0} />);
    expect(await screen.findByText(/3 open, 1 overdue, 2 awaiting approval/)).toBeInTheDocument();
    expect(screen.getByText(/File the appeal \(01\/12\/2026\)/)).toBeInTheDocument();
    expect(screen.getByText('Reply later')).toBeInTheDocument();
  });

  it('writes "not recorded yet" for empty data and leaves out the areas the user may not open', async () => {
    serve(() => jsonResponse(summary({
      parties: [], involvedUnits: [], authorityPosition: null, claimedAmount: null, lastProcedure: null, lastJudgment: null,
      hearings: { total: 0, held: 0, adjourned: 0, cancelled: 0, scheduled: 0, awaitingResult: 0, followUp: 0, lastHearing: null, nextHearing: null },
      risk: null, infoRequests: null, tasks: null,
      })));
    mount(<CaseSummaryPanel caseId={CASE} generation={0} />);
    await screen.findByText('Parties');
    expect(screen.getAllByText('Not recorded yet').length).toBeGreaterThanOrEqual(7);
    expect(screen.queryByText('Legal risk level')).not.toBeInTheDocument();
    expect(screen.queryByText('Last judgment')).not.toBeInTheDocument();
    expect(screen.queryByText('Missing statements and documents')).not.toBeInTheDocument();
    expect(screen.queryByText('Tasks required now')).not.toBeInTheDocument();
  });

  it('points to the follow-up list when a hearing has no next date', async () => {
    serve(() => jsonResponse(summary({ hearings: { total: 1, held: 0, adjourned: 1, cancelled: 0, scheduled: 0, awaitingResult: 0, followUp: 1, lastHearing: null, nextHearing: null } })));
    mount(<CaseSummaryPanel caseId={CASE} generation={0} />);
    expect(await screen.findByText('The court has set no next date (on the follow-up list)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the follow-up list' })).toHaveAttribute('href', expect.stringContaining('/hearings'));
  });

  it('explains a failed load', async () => {
    serve(() => problem(500));
    mount(<CaseSummaryPanel caseId={CASE} generation={0} />);
    expect(await screen.findByText('The change could not be saved. Try again.')).toBeInTheDocument();
  });
});

const entry = (overrides: Partial<TimelineEntry> = {}): TimelineEntry => ({
  at: '2026-11-10T09:00:00Z', kind: 'HEARING', status: 'SCHEDULED', summary: 'PLEADING', refType: 'HEARING', refId: 'h1', isFuture: true,
  needsAction: false, actorName: null, ...overrides,
});

describe('unified timeline', () => {
  const pageOf = (items: TimelineEntry[], extra: object = {}) => jsonResponse({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, deadlinesAvailable: false, ...extra });

  it('separates the upcoming from the past, marks unfinished actions and links to the source record', async () => {
    serve(() => pageOf([
      entry(),
      entry({ kind: 'EVENT', refType: 'CASE_EVENT', refId: 'e1', isFuture: false, status: null, summary: 'Case created', actorName: 'Mona', at: '2026-11-01T09:00:00Z' }),
      entry({ kind: 'INFO_REQUEST_DUE', refType: 'INFO_REQUEST', refId: 'r1', isFuture: false, needsAction: true, status: 'SENT', summary: 'File', at: '2026-11-02T09:00:00Z' }),
      entry({ kind: 'LINK', refType: 'CASE_LINK', refId: null, isFuture: false, summary: '', status: 'ACTIVE', at: '2026-11-03T09:00:00Z' }),
    ]));
    mount(<CaseTimelinePanel caseId={CASE} generation={0} />);
    expect(await screen.findByRole('heading', { name: 'Upcoming' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Past' })).toBeInTheDocument();
    const links = screen.getAllByRole('link', { name: 'Open the record' });
    expect(links.map((l) => l.getAttribute('href'))).toEqual(['#wp3-ref-HEARING-h1', '#wp3-ref-INFO_REQUEST-r1']);
    expect(screen.getByText('Action not completed')).toBeInTheDocument();
    expect(screen.getByText('(Mona)')).toBeInTheDocument();
    expect(screen.getByText('Legal deadlines will appear here once they are recorded.')).toBeInTheDocument();
  });

  it('shows legal deadlines in the timeline with their state, hides the "will appear" hint, and links to the deadline', async () => {
    serve(() => pageOf([
      entry({ kind: 'DEADLINE', refType: 'DEADLINE', refId: 'd1', status: 'APPROVED', summary: 'File the appeal', isFuture: true }),
      entry({ kind: 'DEADLINE', refType: 'DEADLINE', refId: 'd2', status: 'MANUAL', summary: 'Reply', isFuture: false, needsAction: true, at: '2026-11-01T09:00:00Z' }),
      entry({ kind: 'DEADLINE', refType: 'DEADLINE', refId: 'd3', status: 'COMPLETED', summary: 'Done', isFuture: false, at: '2026-10-01T09:00:00Z' }),
    ], { deadlinesAvailable: true }));
    mount(<CaseTimelinePanel caseId={CASE} generation={0} />);
    await screen.findByText(/File the appeal/);
    expect(screen.getAllByText('Legal deadline')).toHaveLength(4);
    const rows = Array.from(document.querySelectorAll('ul.wp3-timeline li'));
    expect(rows.map((r) => r.textContent)).toEqual([
      expect.stringContaining('approved — File the appeal'),
      expect.stringContaining('manual, awaiting approval — Reply'),
      expect.stringContaining('completed — Done'),
    ]);
    expect(screen.getByText('Action not completed')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Open the record' }).map((l) => l.getAttribute('href'))).toEqual([
      '#wp3-ref-DEADLINE-d1', '#wp3-ref-DEADLINE-d2', '#wp3-ref-DEADLINE-d3',
    ]);
    expect(screen.queryByText('Legal deadlines will appear here once they are recorded.')).not.toBeInTheDocument();
  });

  it('filters by type and reads again', async () => {
    const user = userEvent.setup();
    const { requests } = serve(() => pageOf([entry({ isFuture: false })]));
    mount(<CaseTimelinePanel caseId={CASE} generation={0} />);
    await screen.findByText('Past');
    expect(screen.queryByRole('heading', { name: 'Upcoming' })).not.toBeInTheDocument();
    const filter = screen.getByRole('group', { name: 'Show only' });
    await user.click(within(filter).getByLabelText('Hearing'));
    await waitFor(() => { expect(requests.at(-1)?.query.get('types')).toBe('HEARING'); });
    await user.click(within(filter).getByLabelText('Risk assessment'));
    await waitFor(() => { expect(requests.at(-1)?.query.get('types')).toBe('HEARING,RISK'); });
    await user.click(within(filter).getByLabelText('Hearing'));
    await waitFor(() => { expect(requests.at(-1)?.query.get('types')).toBe('RISK'); });
  });

  it('shows older entries on request and says when there are none', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) =>
      r.query.get('page') === '0'
        ? pageOf([entry({ isFuture: false })], { totalPages: 2 })
        : pageOf([entry({ isFuture: false, refId: 'h2', at: '2026-10-01T09:00:00Z' })], { page: 1, totalPages: 2 }),
    );
    mount(<CaseTimelinePanel caseId={CASE} generation={0} />);
    await user.click(await screen.findByRole('button', { name: 'Show more' }));
    await waitFor(() => { expect(screen.getAllByRole('listitem')).toHaveLength(2); });
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
    expect(requests.map((r) => r.query.get('page'))).toEqual(['0', '1']);
  });

  it('says there are no events, and explains failures to load or to read more', async () => {
    const user = userEvent.setup();
    serve(() => pageOf([]));
    const first = mount(<CaseTimelinePanel caseId={CASE} generation={0} />);
    expect(await screen.findByText('No events.')).toBeInTheDocument();
    first.unmount();
    serve(() => problem(500));
    const second = mount(<CaseTimelinePanel caseId={CASE} generation={0} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved');
    second.unmount();
    let failNext = false;
    serve((r) => (r.query.get('page') === '1' || failNext ? problem(500) : pageOf([entry({ isFuture: false })], { totalPages: 2 })));
    mount(<CaseTimelinePanel caseId={CASE} generation={0} />);
    failNext = true;
    await user.click(await screen.findByRole('button', { name: 'Show more' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
