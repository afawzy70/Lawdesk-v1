import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { caseDocument, page, problem, renderOperations, scriptedFetch } from '../testSupport';

/** LD-026 the latest PDF attachments of every case (docs/delivery/PHASE-5-ACCEPTANCE.md, QA-0208). */

describe('documents screen', () => {
  it('lists the latest documents with their case, size and a download link', async () => {
    const { requests } = scriptedFetch((r) =>
      r.path === '/api/v1/documents' ? page([caseDocument(), caseDocument({ id: 'd2', caseReferenceNo: null })]) : undefined,
    );
    renderOperations('/documents?lang=en');
    expect(await screen.findByRole('heading', { level: 1, name: 'Documents' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Documents' });
    const [first] = within(table).getAllByRole('row').slice(1);
    expect(first).toHaveTextContent('Statement of claim');
    expect(first).toHaveTextContent('statement-of-claim.pdf');
    expect(first).toHaveTextContent('12 kB');
    expect(first).toHaveTextContent('1 Mar 2025');
    expect(within(first as HTMLElement).getByRole('link', { name: 'LD-2025-00042' })).toHaveAttribute(
      'href',
      '/cases/c0000000-0000-4000-8000-000000000001?lang=en',
    );
    expect(within(first as HTMLElement).getByRole('link', { name: 'Download Statement of claim' })).toHaveAttribute(
      'href',
      '/api/v1/documents/d1/content',
    );
    expect(screen.getByRole('region', { name: 'Documents' })).toHaveAttribute('tabindex', '0');
    expect(requests[0]?.query.get('size')).toBe('20');
    expect(document.title).toBe('Documents — LawDesk (Preview)');
  });

  it('pages, says when nothing is attached, and retries', async () => {
    const user = userEvent.setup();
    let fail = false;
    let empty = false;
    scriptedFetch((r) => {
      if (r.path !== '/api/v1/documents') return undefined;
      if (fail) return problem(500);
      if (empty) return page([]);
      const number = Number(r.query.get('page'));
      return page([caseDocument({ id: `d${String(number)}`, title: `Doc ${String(number)}` })], {
        page: number,
        totalItems: 21,
        totalPages: 2,
      });
    });
    const { unmount } = renderOperations('/documents');
    expect(await screen.findByText('Doc 0')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(await screen.findByText('Doc 1')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(await screen.findByText('Doc 0')).toBeInTheDocument();
    unmount();

    empty = true;
    const second = renderOperations('/documents');
    expect(await screen.findByText('No documents are attached to any case yet.')).toBeInTheDocument();
    second.unmount();

    empty = false;
    fail = true;
    renderOperations('/documents');
    expect(await screen.findByRole('alert')).toHaveTextContent('The data could not be loaded.');
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Doc 0')).toBeInTheDocument();
  });

  it('shows the access-denied page without DOCUMENT_VIEW, and Arabic right to left', async () => {
    scriptedFetch((r) => (r.path === '/api/v1/documents' ? problem(403) : undefined));
    const { unmount } = renderOperations('/documents');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    unmount();

    scriptedFetch((r) => (r.path === '/api/v1/documents' ? page([caseDocument()]) : undefined));
    renderOperations('/documents?lang=ar');
    expect(await screen.findByRole('heading', { level: 1, name: 'المستندات' })).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(await screen.findByRole('link', { name: 'تنزيل Statement of claim' })).toBeInTheDocument();
  });
});
