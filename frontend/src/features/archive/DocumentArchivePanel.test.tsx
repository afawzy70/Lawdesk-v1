import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CASE, problem, renderWith, serve } from '../pleadings/testSupport';
import type { CaseDocument } from '../operations/documents/documentsApi';
import type { DocumentVersion, TextState } from './api';
import { DocumentArchivePanel } from './DocumentArchivePanel';

/** LD-055 (requirement 13): versions with uploader and date, the honest state of the text, classification, and links. */

const DOC: CaseDocument = {
  id: 'd1',
  caseId: CASE,
  caseReferenceNo: 'LD-2026-00007',
  caseTitle: 'Lease dispute',
  title: 'Statement of claim',
  fileName: 'claim.pdf',
  mediaType: 'application/pdf',
  sizeBytes: 2048,
  sha256: 'a',
  version: 4,
  createdAt: '2026-05-01T08:00:00Z',
  createdBy: 'u1',
  categoryId: null,
  documentDate: null,
  proceedingId: null,
  description: null,
  currentVersionNo: 2,
};

const text = (status: TextState['status'], extra: Partial<TextState> = {}): TextState => ({
  versionId: 'v',
  documentId: 'd1',
  status,
  method: null,
  engine: null,
  detail: null,
  pageCount: null,
  truncated: false,
  text: null,
  ...extra,
});

const version = (no: number, current: boolean, state: TextState | null, extra: Partial<DocumentVersion> = {}): DocumentVersion => ({
  id: `v${String(no)}`,
  versionNo: no,
  fileName: `claim-v${String(no)}.pdf`,
  mediaType: 'application/pdf',
  sizeBytes: 2048,
  sha256: 'a',
  changeNote: null,
  uploadedBy: 'abcdef12-0000-4000-8000-000000000000',
  uploadedAt: '2026-05-02T08:00:00Z',
  current,
  text: state,
  ...extra,
});

const FORMATS = [{ code: 'PDF', family: 'PDF' as const, mediaType: 'application/pdf', extension: 'pdf', enabled: true }];

const pageOf = (items: unknown[]) => ({ items, page: 0, size: 100, totalItems: items.length, totalPages: 1 });

const base = {
  'GET /api/v1/lookups/DOCUMENT_CATEGORY': [{ id: 'cat1', code: 'DEFENSE_MEMO', labelAr: 'مذكرة دفاع', labelEn: 'Defence memo', active: true, needsApproval: true }],
  [`GET /api/v1/cases/${CASE}/proceedings`]: pageOf([]),
  'GET /api/v1/documents/d1/links': [],
  [`GET /api/v1/cases/${CASE}/claims`]: [{ id: 'c1', seq: 1, text: 'Compensation' }],
  [`GET /api/v1/cases/${CASE}/defenses`]: [{ id: 'df1', seq: 1, title: 'Late filing' }],
  [`GET /api/v1/cases/${CASE}/hearings`]: pageOf([]),
  [`GET /api/v1/cases/${CASE}/judgments`]: pageOf([]),
  'GET /api/v1/tasks': pageOf([]),
};

const panel = (canManage: boolean, onChanged: () => void = () => undefined) => (
  <DocumentArchivePanel doc={DOC} canManage={canManage} formats={FORMATS} onChanged={onChanged} />
);

describe('the archive panel of a document', () => {
  it('lists every version with who uploaded it, when, and the honest state of its text', async () => {
    serve({
      ...base,
      'GET /api/v1/documents/d1/versions': [
        version(2, true, text('OCR_UNAVAILABLE', { detail: 'engine not installed on this server', pageCount: 3, truncated: true })),
        version(1, false, text('EXTRACTED', { method: 'PDF_TEXT' }), { changeNote: 'first scan', mediaType: 'image/png' }),
      ],
    });
    renderWith(panel(false), { permissions: ['DOCUMENT_VIEW'] });
    const versions = await screen.findByRole('region', { name: 'Versions' });
    expect(within(versions).getByText('Version 2')).toBeVisible();
    expect(within(versions).getByText('(current)', { exact: false })).toBeVisible();
    expect(within(versions).getByText('Not read: no OCR engine is available on this server')).toBeVisible();
    expect(within(versions).getByText(/Not searchable by content/)).toBeVisible();
    expect(within(versions).getByText(/Engine: engine not installed on this server/)).toBeVisible();
    expect(within(versions).getByText(/Only part of the text is stored/)).toBeVisible();
    expect(within(versions).getByText('Text read from the file')).toBeVisible();
    expect(within(versions).getByText(/Note: first scan/)).toBeVisible();
    expect(within(versions).getAllByText(/abcdef12/)).toHaveLength(2);
    expect(within(versions).getByRole('link', { name: 'Download version 1' })).toHaveAttribute('href', '/api/v1/documents/d1/versions/1/content');
    expect(screen.queryByRole('form', { name: 'Add a new version' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Read the file again' })).not.toBeInTheDocument();
  });

  it('adds a version as multipart data and asks for the file again after an engine was installed', async () => {
    const user = userEvent.setup();
    const changed: string[] = [];
    const backend = serve({
      ...base,
      'GET /api/v1/documents/d1/versions': [version(2, true, text('OCR_UNAVAILABLE'))],
      'POST /api/v1/documents/d1/versions': version(3, true, text('EXTRACTED')),
      'POST /api/v1/documents/d1/text/reprocess': text('PENDING'),
    });
    renderWith(panel(true, () => { changed.push('x'); }), { permissions: ['DOCUMENT_VIEW', 'DOCUMENT_MANAGE'] });
    const form = await screen.findByRole('form', { name: 'Add a new version' });
    await user.click(within(form).getByRole('button', { name: 'Add version' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a file.');
    await user.upload(within(form).getByLabelText('New file (Statement of claim)'), new File(['%PDF-1.7'], 'v3.pdf', { type: 'application/pdf' }));
    await user.type(within(form).getByLabelText('What changed (optional)'), 'after review');
    await user.click(within(form).getByRole('button', { name: 'Add version' }));
    expect(await screen.findByText('The new version was stored. The earlier versions are kept.')).toBeVisible();
    expect(changed).toHaveLength(1);
    const call = backend.fetchMock.mock.calls.find(([, init]) => init?.method === 'POST' && init.body instanceof FormData);
    expect((call?.[1]?.body as FormData).get('note')).toBe('after review');

    await user.click(screen.getByRole('button', { name: 'Read the file again' }));
    expect(await screen.findByText('The file will be read again.')).toBeVisible();
  });

  it('explains what the backend refused when a version is added', async () => {
    const user = userEvent.setup();
    const answers = [problem(400, { errors: [{ field: 'file', code: 'UnsupportedFormat' }] }), problem(422, { rule: 'LEGAL_DOCUMENT_LOCKED' })];
    serve({ ...base, 'GET /api/v1/documents/d1/versions': [version(1, true, null)], 'POST /api/v1/documents/d1/versions': () => answers.shift() });
    renderWith(panel(true), { permissions: ['DOCUMENT_VIEW', 'DOCUMENT_MANAGE'] });
    const form = await screen.findByRole('form', { name: 'Add a new version' });
    await user.upload(within(form).getByLabelText('New file (Statement of claim)'), new File(['x'], 'a.pdf', { type: 'application/pdf' }));
    await user.click(within(form).getByRole('button', { name: 'Add version' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This kind of file is not accepted');
    await user.click(within(form).getByRole('button', { name: 'Add version' }));
    expect(await screen.findByText(/Return the memo to draft first/)).toBeVisible();
  });

  it('classifies the document with a category, a date and a stage', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      'GET /api/v1/documents/d1/versions': [],
      [`GET /api/v1/cases/${CASE}/proceedings`]: pageOf([{ id: 'p1', caseId: CASE, stage: 'FIRST_INSTANCE', courtCaseNumber: '1055', courtCaseYear: 2026 }]),
      'PUT /api/v1/documents/d1/classification': DOC,
    });
    renderWith(panel(true), { permissions: ['DOCUMENT_VIEW', 'DOCUMENT_MANAGE'] });
    const form = await screen.findByRole('form', { name: 'Classification' });
    await user.selectOptions(await within(form).findByLabelText('Category'), 'cat1');
    expect(within(form).getByRole('option', { name: /Defence memo \(needs the owner’s approval\)/ })).toBeVisible();
    fireEvent.change(within(form).getByLabelText('Document date'), { target: { value: '2026-04-30' } });
    await user.selectOptions(await within(form).findByLabelText('Judicial stage'), 'p1');
    await user.type(within(form).getByLabelText('Description'), 'filed at the hearing');
    await user.click(within(form).getByRole('button', { name: 'Save classification' }));
    expect(await screen.findByText('The classification was saved.')).toBeVisible();
    expect(backend.to('PUT', '/api/v1/documents/d1/classification')[0]?.body).toEqual({
      title: 'Statement of claim',
      categoryId: 'cat1',
      documentDate: '2026-04-30',
      proceedingId: 'p1',
      description: 'filed at the hearing',
      version: 4,
    });
  });

  it('shows the classification read-only to those who may not manage documents', async () => {
    serve({ ...base, 'GET /api/v1/documents/d1/versions': [] });
    renderWith(panel(false), { permissions: ['DOCUMENT_VIEW'] });
    const form = await screen.findByRole('form', { name: 'Classification' });
    expect(within(form).queryByRole('button', { name: 'Save classification' })).not.toBeInTheDocument();
    expect(within(form).getByLabelText('Title')).toBeDisabled();
    expect(await screen.findByText('No versions.')).toBeVisible();
    expect(screen.getByText('Not linked to any record.')).toBeVisible();
  });

  it('links the document to a claim and removes the link', async () => {
    const user = userEvent.setup();
    let links = [] as unknown[];
    const backend = serve({
      ...base,
      'GET /api/v1/documents/d1/versions': [],
      'GET /api/v1/documents/d1/links': () => links,
      'POST /api/v1/documents/d1/links': () => {
        links = [{ id: 'l1', documentId: 'd1', documentTitle: 'x', targetType: 'CLAIM', targetId: 'c1', targetLabel: '#1 Compensation', note: 'cited', createdAt: '', createdBy: 'u1' }];
        return links[0];
      },
      'DELETE /api/v1/document-links/l1': () => {
        links = [];
        return new Response(null, { status: 204 });
      },
    });
    renderWith(panel(true), { permissions: ['DOCUMENT_VIEW', 'DOCUMENT_MANAGE'] });
    const form = await screen.findByRole('form', { name: 'Link' });
    expect(within(form).getByRole('button', { name: 'Link' })).toBeDisabled();
    await user.selectOptions(await within(form).findByLabelText('Record'), 'c1');
    await user.type(within(form).getByLabelText('Note (optional)'), 'cited');
    await user.click(within(form).getByRole('button', { name: 'Link' }));
    expect(await screen.findByText('The link was added.')).toBeVisible();
    expect(await screen.findByText('#1 Compensation', { exact: false })).toBeVisible();
    expect(backend.to('POST', '/api/v1/documents/d1/links')[0]?.body).toEqual({ targetType: 'CLAIM', targetId: 'c1', note: 'cited' });

    await user.click(screen.getByRole('button', { name: 'Remove the link to #1 Compensation' }));
    expect(await screen.findByText('The link was removed.')).toBeVisible();
    expect(await screen.findByText('Not linked to any record.')).toBeVisible();
  });

  it('offers the records of each kind, says when there are none, and explains a refused link', async () => {
    const user = userEvent.setup();
    serve({
      ...base,
      'GET /api/v1/documents/d1/versions': [],
      [`GET /api/v1/cases/${CASE}/judgments`]: pageOf([{ id: 'j1', judgmentDate: '2026-04-01' }]),
      'GET /api/v1/tasks': pageOf([{ id: 't1', title: 'Collect exhibits' }]),
      'POST /api/v1/documents/d1/links': problem(422, { rule: 'LINK_NOT_IN_CASE' }),
    });
    renderWith(panel(true), { permissions: ['DOCUMENT_VIEW', 'DOCUMENT_MANAGE'] });
    const form = await screen.findByRole('form', { name: 'Link' });
    await user.selectOptions(within(form).getByLabelText('Kind of record'), 'DEFENSE');
    expect(await within(form).findByRole('option', { name: 'Late filing' })).toBeVisible();
    await user.selectOptions(within(form).getByLabelText('Kind of record'), 'HEARING');
    expect(await within(form).findByText('The case has no records of this kind.')).toBeVisible();
    await user.selectOptions(within(form).getByLabelText('Kind of record'), 'JUDGMENT');
    expect(await within(form).findByRole('option', { name: 'Judgment of 01/04/2026' })).toBeVisible();
    await user.selectOptions(within(form).getByLabelText('Kind of record'), 'TASK');
    await user.selectOptions(await within(form).findByLabelText('Record'), 't1');
    await user.click(within(form).getByRole('button', { name: 'Link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The record is not in this case.');
  });
});
