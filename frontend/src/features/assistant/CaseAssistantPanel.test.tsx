import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { CaseAssistantPanel } from './CaseAssistantPanel';
import { answer, CASE, mount, output, problem, serve, type Request } from './testSupport';

/** LD-059, requirements section 22: the assistant panel of the case page (a non-production test mode). */

const asking = (r: Request) => r.method === 'POST' && r.path === '/api/v1/assistant/ask';
const outputs = (r: Request) => r.method === 'GET' && r.path === '/api/v1/assistant/outputs';
const documents = (r: Request) => r.method === 'GET' && r.path === `/api/v1/cases/${CASE}/documents`;
const DOCS = { items: [{ id: 'd1', title: 'Contract' }, { id: 'd2', title: 'Annex' }], page: 0, size: 20, totalItems: 2, totalPages: 1 };

describe('the case assistant panel', () => {
  it('is not shown to someone without ASSISTANT_USE and sends nothing', () => {
    const { requests } = serve(() => undefined);
    mount(<CaseAssistantPanel caseId={CASE} />, ['CASE_VIEW']);
    expect(screen.queryByText('Legal assistant (test mode)')).not.toBeInTheDocument();
    expect(requests).toHaveLength(0);
  });

  it('says it is a non-production test mode and asks a question about the case, showing the answer with its sources', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (outputs(r) ? jsonResponse([]) : asking(r) ? jsonResponse(answer()) : undefined));
    mount(<CaseAssistantPanel caseId={CASE} />, ['ASSISTANT_USE']);
    expect(await screen.findByRole('heading', { name: 'Legal assistant (test mode)' })).toBeInTheDocument();
    expect(screen.getByText(/Non-production test mode: no AI provider is approved/)).toBeInTheDocument();
    expect(await screen.findByText('No drafts.')).toBeInTheDocument();
    const submit = screen.getByRole('button', { name: 'Ask' });
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText('Question or subject'), '  bank guarantee  ');
    await user.click(submit);
    expect(await screen.findByRole('heading', { name: 'Answer' })).toBeInTheDocument();
    expect(screen.getAllByText(/Check the bank guarantee terms/).length).toBeGreaterThan(0);
    expect(requests.find(asking)?.body).toEqual({ task: 'QUESTION', caseId: CASE, saveDraft: false, question: 'bank guarantee' });
  });

  it('asks for a summary without a question and keeps the result as a draft when asked', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (outputs(r) ? jsonResponse([]) : asking(r) ? jsonResponse(answer({ task: 'CASE_SUMMARY', outputId: 'o9' })) : undefined));
    mount(<CaseAssistantPanel caseId={CASE} />, ['ASSISTANT_USE']);
    await user.selectOptions(await screen.findByLabelText('What do you need?'), 'CASE_SUMMARY');
    expect(screen.queryByLabelText('Question or subject')).not.toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Keep the result as a draft' }));
    await user.click(screen.getByRole('button', { name: 'Ask' }));
    expect(await screen.findByText('Kept as a draft. It is not final and needs your review.')).toBeInTheDocument();
    expect(requests.find(asking)?.body).toEqual({ task: 'CASE_SUMMARY', caseId: CASE, saveDraft: true });
  });

  it('chooses documents to analyse or compare, and needs both for a comparison', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (outputs(r) ? jsonResponse([]) : documents(r) ? jsonResponse(DOCS) : asking(r) ? jsonResponse(answer({ task: 'COMPARISON' })) : undefined));
    mount(<CaseAssistantPanel caseId={CASE} />, ['ASSISTANT_USE']);
    await user.selectOptions(await screen.findByLabelText('What do you need?'), 'DOCUMENT_ANALYSIS');
    const first = await screen.findByLabelText('Document');
    const submit = screen.getByRole('button', { name: 'Ask' });
    expect(submit).toBeDisabled();
    await user.selectOptions(first, 'd1');
    expect(submit).toBeEnabled();
    await user.selectOptions(screen.getByLabelText('What do you need?'), 'COMPARISON');
    const second = await screen.findByLabelText('Document to compare with');
    expect(submit).toBeDisabled();
    await user.selectOptions(second, 'd2');
    await user.click(submit);
    await screen.findByRole('heading', { name: 'Answer' });
    expect(requests.find(asking)?.body).toEqual({ task: 'COMPARISON', caseId: CASE, saveDraft: false, documentId: 'd1', otherDocumentId: 'd2' });
    expect(requests.filter(documents)).toHaveLength(1);
  });

  it('says so when the case has no documents or they cannot be listed', async () => {
    const user = userEvent.setup();
    serve((r) => (outputs(r) ? jsonResponse([]) : documents(r) ? problem(500) : undefined));
    mount(<CaseAssistantPanel caseId={CASE} />, ['ASSISTANT_USE']);
    await user.selectOptions(await screen.findByLabelText('What do you need?'), 'DOCUMENT_ANALYSIS');
    expect(await screen.findByText('This case has no documents.')).toBeInTheDocument();
  });

  it('shows what the server refused in words, never the text it sent', async () => {
    const user = userEvent.setup();
    let status = 404;
    serve((r) => (outputs(r) ? jsonResponse([]) : asking(r) ? problem(status, { detail: 'secret detail' }) : undefined));
    mount(<CaseAssistantPanel caseId={CASE} />, ['ASSISTANT_USE']);
    await user.selectOptions(await screen.findByLabelText('What do you need?'), 'MEMO_DRAFT');
    for (const [code, text] of [[404, 'no longer exists'], [403, 'do not have the right'], [400, 'missing or not valid'], [500, 'could not be completed']] as const) {
      status = code;
      await user.click(screen.getByRole('button', { name: 'Ask' }));
      expect(await screen.findByRole('alert')).toHaveTextContent(text);
      expect(screen.queryByText(/secret detail/)).not.toBeInTheDocument();
    }
  });

  it('lists my drafts and lets me approve or discard one after my own review', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => {
      if (outputs(r)) return jsonResponse([output(), output({ id: 'o2', title: 'Old', status: 'USER_APPROVED', reviewedAt: '2026-11-02T08:00:00Z' }), output({ id: 'o3', title: 'Dropped', status: 'DISCARDED' })]);
      if (r.method === 'POST' && r.path === '/api/v1/assistant/outputs/o1/review') return jsonResponse(output({ status: 'USER_APPROVED' }));
      if (r.method === 'POST' && r.path === '/api/v1/assistant/outputs/o4/review') return problem(422, { rule: 'ASSISTANT_OUTPUT_ALREADY_REVIEWED' });
      return undefined;
    });
    mount(<CaseAssistantPanel caseId={CASE} />, ['ASSISTANT_USE']);
    const draft = (await screen.findByText('Reply memorandum')).closest('li') as HTMLElement;
    expect(draft).toHaveTextContent('Draft — awaiting your review');
    expect(screen.getByText('Old').closest('li')).toHaveTextContent('still not final');
    expect(screen.getByText('Dropped').closest('li')).not.toHaveTextContent('approve');
    await user.click(within(draft).getByRole('button', { name: 'I reviewed it: approve the draft' }));
    await waitFor(() => { expect(draft).toHaveTextContent('Reviewed and approved by you'); });
    expect(within(draft).queryByRole('button')).not.toBeInTheDocument();
    expect(requests.find((r) => r.path.endsWith('/o1/review'))?.body).toEqual({ decision: 'APPROVED' });
  });

  it('reports a review the server refused', async () => {
    const user = userEvent.setup();
    serve((r) => (outputs(r) ? jsonResponse([output({ id: 'o4' })]) : r.method === 'POST' ? problem(404) : undefined));
    mount(<CaseAssistantPanel caseId={CASE} />, ['ASSISTANT_USE']);
    await user.click(await screen.findByRole('button', { name: 'Discard' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists');
  });

  it('copes with a draft list it cannot load', async () => {
    serve(() => problem(500));
    mount(<CaseAssistantPanel caseId={CASE} />, ['ASSISTANT_USE']);
    expect(await screen.findByText('No drafts.')).toBeInTheDocument();
  });
});
