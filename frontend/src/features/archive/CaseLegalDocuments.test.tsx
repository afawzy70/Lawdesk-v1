import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Permission } from '../../api/permissions';
import { CASE, problem, renderWith, serve } from '../pleadings/testSupport';
import type { LegalDocument } from './api';
import { CaseLegalDocuments } from './CaseLegalDocuments';

/** LD-055 (requirement 12): memos and correspondence with the cycle draft, in review, approved, sent, archived. */

const LIST = `/api/v1/cases/${CASE}/legal-documents`;

const legal = (id: string, status: LegalDocument['status'], extra: Partial<LegalDocument> = {}): LegalDocument => ({
  id,
  caseId: CASE,
  caseReferenceNo: 'LD-2026-00007',
  caseTitle: 'Lease dispute',
  categoryId: 'cat1',
  categoryLabelAr: 'مذكرة دفاع',
  categoryLabelEn: 'Defence memo',
  title: 'Reply to the claim',
  documentDate: '2026-05-01',
  issuer: 'Legal Department',
  addressee: 'Court of First Instance',
  responsibleMemberId: 'u1',
  responsibleMemberName: 'Maha',
  description: 'The reply',
  documentId: 'doc1',
  documentFileName: 'reply.pdf',
  documentVersionNo: 2,
  status,
  submittedBy: null,
  submittedAt: null,
  approvedBy: null,
  approvedByName: null,
  approvedAt: null,
  approvalNote: null,
  sentRecorded: false,
  sentOn: null,
  sentMethod: null,
  sentReference: null,
  sentRecordedBy: null,
  sentRecordedAt: null,
  sentViaIntegration: false,
  archivedAt: null,
  version: 3,
  createdAt: '2026-05-01T08:00:00Z',
  updatedAt: '2026-05-01T08:00:00Z',
  ...extra,
});

const base = {
  'GET /api/v1/lookups/DOCUMENT_CATEGORY': [{ id: 'cat1', code: 'DEFENSE_MEMO', labelAr: 'مذكرة دفاع', labelEn: 'Defence memo', active: true, needsApproval: false }],
  'GET /api/v1/users/directory': [{ id: 'u1', username: 'maha', displayName: 'Maha', enabled: true }],
  'GET /api/v1/archive/settings': {
    limits: { maxFileBytes: 10485760, maxDocumentsPerCase: 100, maxVersionsPerDocument: 20, maxOcrPages: 50, maxTextChars: 2000000 },
    formats: [{ code: 'PDF', family: 'PDF', mediaType: 'application/pdf', extension: 'pdf', enabled: true }],
    ocr: { available: true, engine: 'stub', languages: 'ara+eng', detail: null },
  },
};

const AUTHOR: Permission[] = ['DOCUMENT_VIEW', 'DOCUMENT_MANAGE'];
const APPROVER: Permission[] = ['DOCUMENT_VIEW', 'MEMO_APPROVE'];

describe('memos and correspondence of a case', () => {
  it('shows each document with its approval and sending records, and says that sending is only a record', async () => {
    serve({
      ...base,
      [`GET ${LIST}`]: [
        legal('l1', 'APPROVED', { approvedBy: 'u2', approvedByName: 'Salem', approvedAt: '2026-05-02T08:00:00Z', approvalNote: 'as drafted' }),
        legal('l2', 'SENT', { title: 'Letter to the ministry', sentRecorded: true, sentOn: '2026-05-05', sentMethod: 'hand delivery', sentReference: 'OUT-7' }),
      ],
    });
    renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: AUTHOR });
    expect(await screen.findByText('Reply to the claim')).toBeVisible();
    expect(screen.getByText(/Approved by Salem on/)).toBeVisible();
    expect(screen.getByText(/Approval note: as drafted/)).toBeVisible();
    expect(screen.getByText(/Sent \(recorded by a member\) on 05\/05\/2026 by hand delivery/)).toBeVisible();
    expect(screen.getByText(/Reference OUT-7/)).toBeVisible();
    expect(screen.getByText(/Not sent through the system/)).toBeVisible();
    expect(screen.getAllByRole('link', { name: 'reply.pdf, version 2' })[0]).toHaveAttribute('href', '/api/v1/documents/doc1/content');
    expect(screen.getAllByText(/Defence memo/).length).toBeGreaterThan(0);
  });

  it('offers only the steps the status and the permissions allow', async () => {
    serve({
      ...base,
      [`GET ${LIST}`]: [legal('d', 'DRAFT', { title: 'Draft' }), legal('r', 'IN_REVIEW', { title: 'Review' }), legal('a', 'APPROVED', { title: 'Approved' })],
    });
    const author = renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: AUTHOR });
    expect(await screen.findByRole('button', { name: 'Send for review: Draft' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Return to draft: Review' })).toBeVisible();
    expect(screen.queryByRole('button', { name: /^Approve:/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Reopen as draft/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Record that it was sent' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Register a memo or letter' })).toBeVisible();
    author.unmount();

    renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: APPROVER });
    expect(await screen.findByRole('button', { name: 'Approve: Review' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Reopen as draft: Approved' })).toBeVisible();
    expect(screen.queryByRole('button', { name: /Send for review/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Register a memo or letter' })).not.toBeInTheDocument();
  });

  it('sends a draft for review and shows the new status', async () => {
    const user = userEvent.setup();
    let status: LegalDocument['status'] = 'DRAFT';
    const backend = serve({
      ...base,
      [`GET ${LIST}`]: () => [legal('l1', status)],
      'POST /api/v1/legal-documents/l1/submit': () => {
        status = 'IN_REVIEW';
        return legal('l1', 'IN_REVIEW');
      },
    });
    renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: AUTHOR });
    await user.click(await screen.findByRole('button', { name: 'Send for review: Reply to the claim' }));
    await user.type(screen.getByLabelText('Note'), 'please review');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('The document moved to “In review”.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/legal-documents/l1/submit')[0]?.body).toEqual({ note: 'please review', version: 3 });
    expect(await screen.findByRole('button', { name: 'Return to draft: Reply to the claim' })).toBeVisible();
  });

  it('approves, and requires a reason to return a memo to draft', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      [`GET ${LIST}`]: [legal('l1', 'IN_REVIEW')],
      'POST /api/v1/legal-documents/l1/approve': legal('l1', 'APPROVED'),
      'POST /api/v1/legal-documents/l1/return': legal('l1', 'DRAFT'),
    });
    renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: ['DOCUMENT_VIEW', 'DOCUMENT_MANAGE', 'MEMO_APPROVE'] });
    await user.click(await screen.findByRole('button', { name: 'Return to draft: Reply to the claim' }));
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Say why.');
    expect(backend.to('POST', '/api/v1/legal-documents/l1/return')).toHaveLength(0);
    await user.type(screen.getByLabelText('Note'), 'cite article 12');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('The document moved to “Draft”.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/legal-documents/l1/return')[0]?.body).toEqual({ note: 'cite article 12', version: 3 });

    await user.click(screen.getByRole('button', { name: 'Approve: Reply to the claim' }));
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('The document moved to “Approved”.')).toBeVisible();
  });

  it('records the sending with a date and a method, and explains what the backend refuses', async () => {
    const user = userEvent.setup();
    const answers = [problem(409), legal('l1', 'SENT', { sentRecorded: true })];
    const backend = serve({ ...base, [`GET ${LIST}`]: [legal('l1', 'APPROVED')], 'POST /api/v1/legal-documents/l1/record-sent': () => answers.shift() });
    renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: AUTHOR });
    await user.click(await screen.findByRole('button', { name: 'Record that it was sent' }));
    const group = screen.getByRole('group', { name: 'Record that it was sent' });
    await user.click(within(group).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Some fields are missing');
    fireEvent.change(within(group).getByLabelText('Date sent'), { target: { value: '2026-05-05' } });
    await user.type(within(group).getByLabelText('How it was sent'), 'by hand');
    await user.click(within(group).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this record');
    await user.click(within(group).getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('The document moved to “Sent (recorded)”.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/legal-documents/l1/record-sent')[1]?.body).toEqual({ sentOn: '2026-05-05', method: 'by hand', reference: null, version: 3 });
  });

  it('shows the history of a document', async () => {
    const user = userEvent.setup();
    serve({
      ...base,
      [`GET ${LIST}`]: [legal('l1', 'APPROVED')],
      'GET /api/v1/legal-documents/l1/events': [
        { id: 'e1', fromStatus: null, toStatus: 'DRAFT', note: null, createdAt: '2026-05-01T08:00:00Z', createdBy: 'u1', createdByName: 'Maha' },
        { id: 'e2', fromStatus: 'IN_REVIEW', toStatus: 'APPROVED', note: 'ok', createdAt: '2026-05-02T08:00:00Z', createdBy: 'u2', createdByName: null },
      ],
    });
    renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: AUTHOR });
    await user.click(await screen.findByRole('button', { name: 'History of Reply to the claim' }));
    expect(await screen.findByText(/Registered as Draft/)).toBeVisible();
    expect(screen.getByText(/In review → Approved/)).toBeVisible();
    expect(screen.getByText(/by a user/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'History of Reply to the claim' }));
    expect(screen.queryByText(/Registered as Draft/)).not.toBeInTheDocument();
  });

  it('registers a memo, and edits and attaches the file of a draft', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      [`GET ${LIST}`]: [legal('l1', 'DRAFT', { documentId: null, documentFileName: null, documentVersionNo: null })],
      [`POST ${LIST}`]: legal('l9', 'DRAFT'),
      'PUT /api/v1/legal-documents/l1': legal('l1', 'DRAFT'),
      'POST /api/v1/legal-documents/l1/file': legal('l1', 'DRAFT'),
    });
    renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: AUTHOR });
    await user.click(await screen.findByRole('button', { name: 'Register a memo or letter' }));
    const form = screen.getByRole('form', { name: 'Register a memo or letter' });
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(within(form).getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true');
    await user.type(within(form).getByLabelText('Name'), 'Memo no. 2');
    await user.selectOptions(within(form).getByLabelText('Type'), 'cat1');
    await user.selectOptions(within(form).getByLabelText('Responsible member'), 'u1');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    await waitFor(() => { expect(screen.queryByRole('form', { name: 'Register a memo or letter' })).not.toBeInTheDocument(); });
    expect(backend.to('POST', LIST)[0]?.body).toMatchObject({ title: 'Memo no. 2', categoryId: 'cat1', responsibleMemberId: 'u1', issuer: null });

    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const edit = screen.getByRole('form', { name: 'Edit' });
    await user.clear(within(edit).getByLabelText('Name'));
    await user.type(within(edit).getByLabelText('Name'), 'Reply (revised)');
    await user.click(within(edit).getByRole('button', { name: 'Save' }));
    await screen.findByText('Reply to the claim');
    expect(backend.to('PUT', '/api/v1/legal-documents/l1')[0]?.body).toMatchObject({ title: 'Reply (revised)', version: 3 });

    await user.click(screen.getByRole('button', { name: 'Attach the file' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a file.');
    const file = new File(['%PDF-1.7'], 'memo.pdf', { type: 'application/pdf' });
    await user.upload(screen.getByLabelText('Electronic copy of Reply to the claim'), file);
    await user.click(screen.getByRole('button', { name: 'Attach the file' }));
    expect(await screen.findByText('The electronic copy was stored.')).toBeVisible();
    expect(backend.to('POST', '/api/v1/legal-documents/l1/file')).toHaveLength(1);
  });

  it('explains a refused step, an empty register and a hidden one', async () => {
    const user = userEvent.setup();
    serve({ ...base, [`GET ${LIST}`]: [legal('l1', 'DRAFT')], 'POST /api/v1/legal-documents/l1/submit': problem(422, { rule: 'LEGAL_DOCUMENT_NEEDS_FILE' }) });
    const first = renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: AUTHOR });
    await user.click(await screen.findByRole('button', { name: 'Send for review: Reply to the claim' }));
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Attach the electronic copy before asking for approval.');
    first.unmount();

    serve({ ...base, [`GET ${LIST}`]: [] });
    const second = renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: APPROVER });
    expect(await screen.findByText('No memos or letters registered yet.')).toBeVisible();
    second.unmount();

    serve({ ...base, [`GET ${LIST}`]: problem(403) });
    renderWith(<CaseLegalDocuments caseId={CASE} editable />, { permissions: APPROVER });
    expect(await screen.findByText('You are not permitted to view the memos and letters of this case.')).toBeVisible();
  });

  it('is read-only for an archived case and works in Arabic', async () => {
    serve({ ...base, [`GET ${LIST}`]: [legal('l1', 'DRAFT')] });
    renderWith(<CaseLegalDocuments caseId={CASE} editable={false} />, { permissions: [...AUTHOR, 'MEMO_APPROVE'], lang: 'ar' });
    expect(await screen.findByText('المذكرات والمراسلات')).toBeVisible();
    expect(screen.getByText('مسودة')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'تسجيل مذكرة أو كتاب' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /إرسال للمراجعة/ })).not.toBeInTheDocument();
  });
});
