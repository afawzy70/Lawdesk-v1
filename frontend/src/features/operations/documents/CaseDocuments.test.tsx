import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { jsonResponse } from '../../../test/http';
import { CASE, caseDocument, page, problem, renderOperations, scriptedFetch, type Request } from '../testSupport';

/** LD-026 PDF attachments in the case's operations section (docs/delivery/PHASE-5-ACCEPTANCE.md, QA-0208). */

const DOCUMENTS = `/api/v1/cases/${CASE}/documents`;

interface Backend {
  documents?: Response;
  onWrite?: (request: Request, init: RequestInit | undefined) => Response;
}


function serve(backend: Backend = {}) {
  const { fetchMock, requests } = scriptedFetch((r) => {
    if (r.method !== 'GET') return backend.onWrite?.(r, undefined);
    if (r.path === DOCUMENTS) return (backend.documents ?? page([])).clone();
    if (r.path.startsWith(`/api/v1/cases/${CASE}/`) || r.path === '/api/v1/tasks') return page([]);
    return undefined;
  });
  return { fetchMock, requests };
}

/** The FormData bodies the section sent (the scripted fetch only parses JSON). */
function sentForms(fetchMock: ReturnType<typeof serve>['fetchMock']) {
  return fetchMock.mock.calls
    .map(([, init]) => init?.body)
    .filter((body): body is FormData => body instanceof FormData);
}

const pdfFile = (name = 'claim.pdf', size = 2048, type = 'application/pdf') => {
  const file = new File(['%PDF-1.7'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
};

const section = async () => screen.findByRole('region', { name: 'Documents' });

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('case documents', () => {
  it('lists the PDFs of the case with download links and the remaining slots', async () => {
    serve({ documents: page([caseDocument(), caseDocument({ id: 'd2', title: 'Judgment', sizeBytes: 2.5 * 1024 * 1024 })]) });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    expect(await within(region).findByText('2 of 10 documents')).toBeVisible();
    const items = within(region).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Statement of claim');
    expect(items[0]).toHaveTextContent('statement-of-claim.pdf');
    expect(items[0]).toHaveTextContent('12 kB');
    expect(items[1]).toHaveTextContent('2.5 MB');
    expect(within(region).getByRole('link', { name: 'Download Statement of claim' })).toHaveAttribute(
      'href',
      '/api/v1/documents/d1/content',
    );
  });

  it('attaches a PDF as multipart form data with its title', async () => {
    const user = userEvent.setup();
    let documents = page([]);
    const { fetchMock } = serve({
      get documents() {
        return documents;
      },
      onWrite: () => {
        documents = page([caseDocument()]);
        return jsonResponse(caseDocument(), 201);
      },
    });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    expect(await within(region).findByText('No documents attached yet.')).toBeVisible();
    const form = within(region).getByRole('form', { name: 'Attach a PDF' });
    await user.upload(within(form).getByLabelText('PDF file (at most 10 MB)'), pdfFile());
    await user.type(within(form).getByLabelText('Title (optional)'), 'Statement of claim');
    await user.click(within(form).getByRole('button', { name: 'Attach' }));

    expect(await within(region).findByRole('status')).toHaveTextContent('The document was attached.');
    const [sent] = sentForms(fetchMock);
    expect(sent?.get('title')).toBe('Statement of claim');
    expect((sent?.get('file') as File).name).toBe('claim.pdf');
    const call = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(call?.[0]).toBe(DOCUMENTS);
    expect(await within(region).findByText('1 of 10 documents')).toBeVisible();
  });

  it('checks the file in the browser before sending it', async () => {
    const user = userEvent.setup({ applyAccept: false });
    const { fetchMock } = serve();
    renderOperations(`/cases/${CASE}`);
    const form = within(await section()).getByRole('form', { name: 'Attach a PDF' });
    const input = within(form).getByLabelText('PDF file (at most 10 MB)');
    const attach = within(form).getByRole('button', { name: 'Attach' });

    await user.click(attach);
    expect(await within(form).findByText('Choose a PDF file.')).toBeVisible();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    await user.upload(input, pdfFile('photo.png', 100, 'image/png'));
    await user.click(attach);
    expect(await within(form).findByText('Only PDF files can be attached.')).toBeVisible();
    fireEvent.change(input, { target: { files: [pdfFile('big.pdf', 10 * 1024 * 1024 + 1)] } });
    await user.click(attach);
    expect(await within(form).findByText('The file is larger than 10 MB.')).toBeVisible();
    expect(sentForms(fetchMock)).toHaveLength(0);
  });

  it('explains what the backend refused', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [{ field: 'file', code: 'PdfOnly' }] }),
      problem(400, { errors: [{ field: 'file', code: 'MaxSize' }] }),
      problem(422, { rule: 'DOCUMENT_LIMIT_REACHED' }),
      // QAD-037: above the web server's transport limit nginx answers 413 with no problem document.
      problem(413),
      problem(403),
    ];
    serve({ onWrite: () => answers.shift() ?? problem(500) });
    renderOperations(`/cases/${CASE}`);
    const form = within(await section()).getByRole('form', { name: 'Attach a PDF' });
    await user.upload(within(form).getByLabelText('PDF file (at most 10 MB)'), pdfFile());
    const attach = within(form).getByRole('button', { name: 'Attach' });
    await user.click(attach);
    expect(await within(form).findByText('Only PDF files can be attached.')).toBeVisible();
    await user.click(attach);
    expect(await within(form).findByText('The file is larger than 10 MB.')).toBeVisible();
    await user.click(attach);
    expect(
      await within(form).findByText('This case already holds 10 documents. Remove one to attach another.'),
    ).toBeVisible();
    await user.click(attach);
    expect(await within(form).findByText('The file is larger than 10 MB.')).toBeVisible();
    await user.click(attach);
    expect(await within(form).findByText('You are not permitted to make this change.')).toBeVisible();
  });

  it('removes a document after confirmation and explains a stale one', async () => {
    const user = userEvent.setup();
    const answers = [problem(409), new Response(null, { status: 204 })];
    const { requests } = serve({ documents: page([caseDocument()]), onWrite: () => answers.shift() ?? problem(500) });
    renderOperations(`/cases/${CASE}`);
    const region = await section();
    await user.click(await within(region).findByRole('button', { name: 'Remove Statement of claim' }));
    expect(within(region).getByText('Remove “Statement of claim”? The file is deleted.')).toBeVisible();
    await user.click(within(region).getByRole('button', { name: 'Keep it' }));
    expect(within(region).queryByText(/The file is deleted/)).not.toBeInTheDocument();

    await user.click(within(region).getByRole('button', { name: 'Remove Statement of claim' }));
    await user.click(within(region).getByRole('button', { name: 'Remove the document' }));
    expect(
      await within(region).findByText('Someone else changed this record meanwhile. Reload and try again.'),
    ).toBeVisible();
    await user.click(within(region).getByRole('button', { name: 'Remove Statement of claim' }));
    await user.click(within(region).getByRole('button', { name: 'Remove the document' }));
    expect(await within(region).findByRole('status')).toHaveTextContent('The document was removed.');
    const removal = requests.filter((r) => r.method === 'DELETE').at(-1);
    expect(removal?.path).toBe('/api/v1/documents/d1');
    expect(removal?.query.get('version')).toBe('0');
  });

  it('hides the form when the case is full and explains a refused or failed list', async () => {
    const full = Array.from({ length: 10 }, (_, i) => caseDocument({ id: `d${String(i)}`, title: `Doc ${String(i)}` }));
    serve({ documents: page(full) });
    const first = renderOperations(`/cases/${CASE}`);
    const region = await section();
    expect(
      await within(region).findByText('This case already holds 10 documents. Remove one to attach another.'),
    ).toBeVisible();
    expect(within(region).queryByRole('form')).not.toBeInTheDocument();
    first.unmount();

    serve({ documents: problem(403) });
    const second = renderOperations(`/cases/${CASE}`);
    expect(
      await within(await section()).findByText('You are not permitted to view the documents of this case.'),
    ).toBeVisible();
    second.unmount();

    const user = userEvent.setup();
    let documents = problem(500);
    serve({
      get documents() {
        return documents;
      },
    });
    renderOperations(`/cases/${CASE}?lang=ar`);
    const arabic = await screen.findByRole('region', { name: 'المستندات' });
    expect(await within(arabic).findByText('تعذّر تحميل البيانات. تحقّق من الاتصال وأعد المحاولة.')).toBeVisible();
    documents = page([caseDocument()]);
    await user.click(within(arabic).getByRole('button', { name: 'أعد المحاولة' }));
    expect(await within(arabic).findByText('المستندات: ١ من ١٠')).toBeVisible();
  });
});
