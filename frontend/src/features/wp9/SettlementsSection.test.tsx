import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import type { HistoryEntry } from './api';
import { SettlementsSection } from './SettlementsSection';
import { CASE, mount, page, problem, serve, SETTLEMENT_KINDS, settlement, type Request } from './testSupport';

/** LD-057, requirements section 23: settlements on the case page. */

const LIST = `/api/v1/cases/${CASE}/settlements`;
const DOCS = { id: 'doc1', caseId: CASE, caseReferenceNo: null, caseTitle: null, title: 'Offer letter', fileName: 'offer.pdf', mediaType: 'application/pdf', sizeBytes: 5, sha256: 'a', version: 0, createdAt: '2026-11-01T00:00:00Z', createdBy: 'u' };

function backend(items: unknown, onOther?: (r: Request) => Response | undefined) {
  return serve((r) => {
    if (r.method === 'GET' && r.path === LIST) return items instanceof Response ? items : jsonResponse(items);
    if (r.method === 'GET' && r.path === '/api/v1/lists/SETTLEMENT_KIND') return jsonResponse(SETTLEMENT_KINDS);
    if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/parties`) return jsonResponse([{ partyId: 'p1', kind: 'ORGANIZATION', name: 'Nile Trading', role: 'OPPONENT', addedAt: '2026-01-01T00:00:00Z' }]);
    if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/documents`) return page([DOCS]);
    return onOther?.(r);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('settlements of a case', () => {
  it('shows each settlement with its kind, status, party, its own amount in KWD and the decision', async () => {
    backend([
      settlement({ legalOpinion: 'Accept', approvingAuthority: 'The manager', financialImpactNote: 'Lower liability' }),
      settlement({ id: 's2', kind: 'WAIVER', status: 'EXECUTED', settlementAmount: null, offeringPartyId: null, offeringPartyName: null, offeringPartyText: 'The plaintiff', decidedAt: '2026-11-03T08:00:00Z', decisionReason: 'Agreed', completedOn: '2026-11-04', legalGrounds: null }),
    ]);
    mount(<SettlementsSection caseId={CASE} editable={false} canApprove={false} />);
    const items = await screen.findAllByRole('listitem', { name: /Settlement:/ });
    expect(items[0]).toHaveTextContent('Settlement');
    expect(items[0]).toHaveTextContent('Proposed');
    expect(items[0]).toHaveTextContent('Nile Trading');
    expect(items[0]).toHaveTextContent('125,000.500 KWD');
    expect(items[0]).toHaveTextContent('Avoids the cost of litigation');
    expect(items[0]).toHaveTextContent('Lower liability');
    expect(items[0]).toHaveTextContent('Accept');
    expect(items[0]).toHaveTextContent('The manager');
    expect(items[1]).toHaveTextContent('Waiver');
    expect(items[1]).toHaveTextContent('Executed');
    expect(items[1]).toHaveTextContent('The plaintiff');
    expect(items[1]).toHaveTextContent('Not specified');
    expect(items[1]).toHaveTextContent('Agreed');
    expect(items[1]).toHaveTextContent('04/11/2026');
    expect(screen.getByText(/does not close the case/)).toBeInTheDocument();
    // A reader has no form and no buttons that change anything.
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit the terms' })).not.toBeInTheDocument();
  });

  it('says there is none, reports a failed load, and is left out for someone who may not open it', async () => {
    backend([]);
    const first = mount(<SettlementsSection caseId={CASE} editable={false} canApprove={false} />);
    expect(await screen.findByText('No settlement is recorded for this case.')).toBeInTheDocument();
    first.unmount();
    backend(problem(500));
    const second = mount(<SettlementsSection caseId={CASE} editable={false} canApprove={false} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
    second.unmount();
    backend(problem(403));
    mount(<SettlementsSection caseId={CASE} editable canApprove />);
    await waitFor(() => { expect(screen.queryByRole('heading', { name: /Settlements/ })).not.toBeInTheDocument(); });
  });

  it('records a settlement: the amount goes as text, the party and the kind from the lists', async () => {
    const user = userEvent.setup();
    let items: unknown[] = [];
    const { requests } = backend(items, (r) => {
      if (r.method === 'POST' && r.path === LIST) {
        items = [settlement()];
        return jsonResponse(settlement(), 201);
      }
      return undefined;
    });
    // The list is read again after the write.
    mount(<SettlementsSection caseId={CASE} editable canApprove={false} />);
    const form = await screen.findByRole('form', { name: 'Record a settlement' });
    await user.selectOptions(await within(form).findByLabelText('Kind'), 'Settlement');
    await user.selectOptions(await within(form).findByLabelText('Party who made the offer'), 'Nile Trading');
    await user.type(within(form).getByLabelText('Settlement amount (KWD)'), '125000.500');
    await user.type(within(form).getByLabelText('Legal grounds'), 'Avoids litigation');
    await user.click(within(form).getByRole('button', { name: 'Record a settlement' }));
    expect(await screen.findByText('The settlement was recorded.')).toBeInTheDocument();
    const post = requests.find((r) => r.method === 'POST' && r.path === LIST);
    expect(post?.body).toMatchObject({ kind: 'SETTLEMENT', offeringPartyId: 'p1', settlementAmount: '125000.500', legalGrounds: 'Avoids litigation', legalOpinion: null });
  });

  it('refuses a missing kind and an amount with more than three decimals before sending', async () => {
    const user = userEvent.setup();
    const { requests } = backend([]);
    mount(<SettlementsSection caseId={CASE} editable canApprove={false} />);
    const form = await screen.findByRole('form', { name: 'Record a settlement' });
    await user.click(within(form).getByRole('button', { name: 'Record a settlement' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Choose the kind');
    await user.selectOptions(await within(form).findByLabelText('Kind'), 'Waiver');
    await user.type(within(form).getByLabelText('Settlement amount (KWD)'), '1.2345');
    await user.click(within(form).getByRole('button', { name: 'Record a settlement' }));
    expect(await within(form).findByRole('alert')).toBeInTheDocument();
    expect(requests.some((r) => r.method === 'POST')).toBe(false);
  });

  it('shows the reason a rule refused the write', async () => {
    const user = userEvent.setup();
    backend([], (r) => (r.method === 'POST' ? problem(422, { rule: 'SETTLEMENT_PARTY_NOT_IN_CASE' }) : undefined));
    mount(<SettlementsSection caseId={CASE} editable canApprove={false} />);
    const form = await screen.findByRole('form', { name: 'Record a settlement' });
    await user.selectOptions(await within(form).findByLabelText('Kind'), 'Settlement');
    await user.click(within(form).getByRole('button', { name: 'Record a settlement' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('not a party of this case');
  });

  it('offers a specialist the moves they may make and an approver also approving and rejecting', async () => {
    const user = userEvent.setup();
    backend([settlement()]);
    const first = mount(<SettlementsSection caseId={CASE} editable canApprove={false} />);
    const item = await screen.findByRole('listitem', { name: /Settlement:/ });
    const options = (select: HTMLElement) => within(select).getAllByRole('option').map((o) => o.textContent);
    await user.click(within(item).getByLabelText('New status'));
    expect(options(within(item).getByLabelText('New status'))).toEqual(['Choose…', 'Under review', 'Withdrawn']);
    first.unmount();
    backend([settlement()]);
    mount(<SettlementsSection caseId={CASE} editable canApprove />);
    const again = await screen.findByRole('listitem', { name: /Settlement:/ });
    expect(options(within(again).getByLabelText('New status'))).toEqual(['Choose…', 'Under review', 'Approved', 'Rejected', 'Withdrawn']);
  });

  it('approves with a reason, executes with the completion date, and the finished ones offer no move', async () => {
    const user = userEvent.setup();
    let current = settlement();
    serve((r) => {
      if (r.method === 'GET' && r.path === LIST) return jsonResponse([current]);
      if (r.method === 'GET' && r.path === '/api/v1/lists/SETTLEMENT_KIND') return jsonResponse(SETTLEMENT_KINDS);
      if (r.method === 'POST' && r.path === '/api/v1/settlements/s1/status') {
        const body = r.body as { status: 'APPROVED' | 'EXECUTED' };
        current = settlement({ status: body.status, version: current.version + 1 });
        return jsonResponse(current);
      }
      return undefined;
    });
    mount(<SettlementsSection caseId={CASE} editable canApprove />);
    let item = await screen.findByRole('listitem', { name: /Settlement:/ });
    await user.selectOptions(within(item).getByLabelText('New status'), 'Approved');
    await user.type(within(item).getByLabelText('Reason or decision (required for a rejection)'), 'Agreed terms');
    await user.click(within(item).getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText('The settlement status was changed.')).toBeInTheDocument();
    await waitFor(() => { expect(screen.getByRole('listitem', { name: /Settlement:/ })).toHaveTextContent('Approved'); });
    item = screen.getByRole('listitem', { name: /Settlement:/ });
    await user.selectOptions(within(item).getByLabelText('New status'), 'Executed');
    await user.type(within(item).getByLabelText('Date completed'), '2026-11-05');
    await user.click(within(item).getByRole('button', { name: 'Apply' }));
    await waitFor(() => { expect(screen.getByRole('listitem', { name: /Settlement:/ })).toHaveTextContent('Executed'); });
    expect(within(screen.getByRole('listitem', { name: /Settlement:/ })).queryByLabelText('New status')).not.toBeInTheDocument();
  });

  it('reports a refused move without changing anything', async () => {
    const user = userEvent.setup();
    backend([settlement()], (r) => (r.method === 'POST' ? problem(403) : undefined));
    mount(<SettlementsSection caseId={CASE} editable canApprove={false} />);
    const item = await screen.findByRole('listitem', { name: /Settlement:/ });
    await user.selectOptions(within(item).getByLabelText('New status'), 'Withdrawn');
    await user.click(within(item).getByRole('button', { name: 'Apply' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('do not have the right');
  });

  it('edits the terms of a proposed settlement and cannot edit an approved one', async () => {
    const user = userEvent.setup();
    const { requests } = backend([settlement()], (r) => (r.method === 'PUT' ? jsonResponse(settlement({ version: 1 })) : undefined));
    const first = mount(<SettlementsSection caseId={CASE} editable canApprove={false} />);
    const item = await screen.findByRole('listitem', { name: /Settlement:/ });
    await user.click(within(item).getByRole('button', { name: 'Edit the terms' }));
    const amount = within(item).getByLabelText('Settlement amount (KWD)');
    expect(amount).toHaveValue('125000.500');
    await user.clear(amount);
    await user.type(amount, '99000');
    await user.click(within(item).getByRole('button', { name: 'Save the terms' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'PUT')).toBe(true); });
    expect(requests.find((r) => r.method === 'PUT')?.body).toMatchObject({ settlementAmount: '99000', version: 0 });
    expect(await screen.findByText('The settlement was saved.')).toBeInTheDocument();
    first.unmount();
    backend([settlement({ status: 'APPROVED' })]);
    mount(<SettlementsSection caseId={CASE} editable canApprove />);
    const approved = await screen.findByRole('listitem', { name: /Settlement:/ });
    expect(within(approved).queryByRole('button', { name: 'Edit the terms' })).not.toBeInTheDocument();
  });

  it('cancels an edit without sending it', async () => {
    const user = userEvent.setup();
    const { requests } = backend([settlement()]);
    mount(<SettlementsSection caseId={CASE} editable canApprove={false} />);
    const item = await screen.findByRole('listitem', { name: /Settlement:/ });
    await user.click(within(item).getByRole('button', { name: 'Edit the terms' }));
    await user.click(within(item).getByRole('button', { name: 'Cancel' }));
    expect(within(item).queryByRole('button', { name: 'Save the terms' })).not.toBeInTheDocument();
    expect(requests.some((r) => r.method === 'PUT')).toBe(false);
  });

  it('links a document of the case file and unlinks it again', async () => {
    const user = userEvent.setup();
    let linked = false;
    const { requests } = serve((r) => {
      if (r.method === 'GET' && r.path === LIST) return jsonResponse([settlement({ documents: linked ? [{ id: 'doc1', title: 'Offer letter', fileName: 'offer.pdf' }] : [] })]);
      if (r.method === 'GET' && r.path === '/api/v1/lists/SETTLEMENT_KIND') return jsonResponse(SETTLEMENT_KINDS);
      if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/parties`) return jsonResponse([]);
      if (r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/documents`) return page([DOCS]);
      if (r.method === 'PUT' && r.path === '/api/v1/settlements/s1/documents/doc1') {
        linked = true;
        return jsonResponse(settlement());
      }
      if (r.method === 'DELETE' && r.path === '/api/v1/settlements/s1/documents/doc1') {
        linked = false;
        return jsonResponse(settlement());
      }
      return undefined;
    });
    mount(<SettlementsSection caseId={CASE} editable canApprove={false} />);
    const item = await screen.findByRole('listitem', { name: /Settlement:/ });
    expect(within(item).getByText('No document is linked.')).toBeInTheDocument();
    const link = await within(item).findByRole('button', { name: 'Link' });
    expect(link).toBeDisabled();
    await user.selectOptions(within(item).getByLabelText('Link a document of the case file'), 'Offer letter');
    await user.click(link);
    expect(await within(await screen.findByRole('listitem', { name: /Settlement:/ })).findByRole('link', { name: 'Offer letter' })).toHaveAttribute('href', '/api/v1/documents/doc1/content');
    await user.click(await screen.findByRole('button', { name: 'Unlink Offer letter' }));
    await waitFor(() => { expect(screen.queryByRole('link', { name: 'Offer letter' })).not.toBeInTheDocument(); });
    expect(requests.filter((r) => r.method === 'DELETE')).toHaveLength(1);
  });

  it('shows the history only when asked, with who did what and the fields that changed', async () => {
    const user = userEvent.setup();
    const history: HistoryEntry[] = [
      { id: 'h1', action: 'CREATED', fromStatus: null, toStatus: 'PROPOSED', changes: null, note: null, actorId: 'u1', actorName: 'Mona Saleh', occurredAt: '2026-11-01T08:00:00Z' },
      { id: 'h2', action: 'UPDATED', fromStatus: null, toStatus: null, changes: { settlementAmount: { from: '1.000', to: '2.000' } }, note: null, actorId: 'u2', actorName: null, occurredAt: '2026-11-02T08:00:00Z' },
      { id: 'h3', action: 'STATUS_CHANGED', fromStatus: 'PROPOSED', toStatus: 'APPROVED', changes: null, note: 'Agreed', actorId: 'u1', actorName: 'Mona Saleh', occurredAt: '2026-11-03T08:00:00Z' },
    ];
    const { requests } = backend([settlement()], (r) => (r.path === '/api/v1/settlements/s1/history' ? jsonResponse(history) : undefined));
    mount(<SettlementsSection caseId={CASE} editable={false} canApprove={false} />);
    const item = await screen.findByRole('listitem', { name: /Settlement:/ });
    expect(requests.some((r) => r.path.endsWith('/history'))).toBe(false);
    await user.click(within(item).getByText('History'));
    const entries = await within(item).findAllByRole('listitem');
    expect(entries[0]).toHaveTextContent('Mona Saleh');
    expect(entries[0]).toHaveTextContent('Recorded: Proposed');
    expect(entries[1]).toHaveTextContent('u2');
    expect(entries[1]).toHaveTextContent('Terms changed');
    expect(entries[1]).toHaveTextContent('settlementAmount');
    expect(entries[2]).toHaveTextContent('Status changed: Approved — Agreed');
  });

  it('reports a history that could not be read', async () => {
    const user = userEvent.setup();
    backend([settlement()], (r) => (r.path.endsWith('/history') ? problem(500) : undefined));
    mount(<SettlementsSection caseId={CASE} editable={false} canApprove={false} />);
    const item = await screen.findByRole('listitem', { name: /Settlement:/ });
    await user.click(within(item).getByText('History'));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
