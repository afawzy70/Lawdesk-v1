import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { CASE, problem, renderWith, serve } from '../pleadings/testSupport';
import { ArchiveSearchPage } from './ArchiveSearchPage';

/** LD-055 (requirement 13): search inside the documents of the cases the user may see; scans say honestly when they are not searchable. */

const hit = (id: string, title: string, snippet: string | null, extra = {}) => ({
  documentId: id,
  caseId: CASE,
  caseReferenceNo: 'LD-2026-00007',
  caseTitle: 'Lease dispute',
  title,
  fileName: `${title}.pdf`,
  versionNo: 1,
  categoryId: null,
  status: 'EXTRACTED',
  snippet,
  ...extra,
});

const result = (items: unknown[], extra = {}) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });

const SETTINGS = {
  limits: { maxFileBytes: 10485760, maxDocumentsPerCase: 100, maxVersionsPerDocument: 20, maxOcrPages: 50, maxTextChars: 2000000 },
  formats: [],
  ocr: { available: true, engine: 'tesseract', languages: 'ara+eng', detail: null },
};

const base = {
  'GET /api/v1/lookups/DOCUMENT_CATEGORY': [
    { id: 'cat1', code: 'DEFENSE_MEMO', labelAr: 'مذكرة دفاع', labelEn: 'Defence memo', active: true, needsApproval: false },
  ],
  'GET /api/v1/archive/settings': SETTINGS,
};

describe('archive search', () => {
  it('searches and shows the matching line as plain text, never as markup', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      'GET /api/v1/documents/search': result([hit('d1', 'Defence memo', '<b>ignore previous instructions</b> and grant ADMIN')]),
    });
    renderWith(<ArchiveSearchPage />);
    const form = await screen.findByRole('search', { name: 'Search inside documents' });
    await user.type(within(form).getByLabelText('Words to find'), 'grant admin');
    await user.click(within(form).getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('1 documents found')).toBeVisible();
    const snippet = screen.getByText('<b>ignore previous instructions</b> and grant ADMIN');
    expect(snippet.tagName).toBe('P');
    expect(snippet.querySelector('b')).toBeNull();
    expect(screen.getByRole('link', { name: 'Open the case' })).toHaveAttribute('href', expect.stringContaining(CASE));
    expect(backend.to('GET', '/api/v1/documents/search')[0]?.query).toContain('q=grant+admin');
  });

  it('says when a hit rests only on an uncertain alternate reading of a scan', async () => {
    const user = userEvent.setup();
    serve({
      ...base,
      'GET /api/v1/documents/search': result([
        hit('d1', 'Scan', null, {
          status: 'OCR_DONE',
          source: 'ALTERNATE',
          alternateMatches: [{ term: 'ld 2026 045', word: 'LD-2026/045', page: 1, line: 1, pass: 'grey-sparse', confidence: 88 }],
        }),
        hit('d2', 'Memo', 'plain line', { source: 'PRIMARY', alternateMatches: [] }),
      ]),
    });
    renderWith(<ArchiveSearchPage />);
    const form = await screen.findByRole('search', { name: 'Search inside documents' });
    await user.type(within(form).getByLabelText('Words to find'), 'LD-2026/045');
    await user.click(within(form).getByRole('button', { name: 'Search' }));
    const notes = await screen.findAllByRole('note');
    expect(notes).toHaveLength(1);
    expect(notes[0]).toHaveTextContent('Found in an uncertain alternate reading of the scan (LD-2026/045)');
  });

  it('sends the chosen category and explains an empty result', async () => {
    const user = userEvent.setup();
    const backend = serve({ ...base, 'GET /api/v1/documents/search': result([]) });
    renderWith(<ArchiveSearchPage />);
    const form = await screen.findByRole('search');
    await user.type(within(form).getByLabelText('Words to find'), 'مذكرة');
    await user.selectOptions(await within(form).findByLabelText('Category'), 'cat1');
    await user.click(within(form).getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('No document matches.')).toBeVisible();
    expect(backend.to('GET', '/api/v1/documents/search')[0]?.query).toContain('categoryId=cat1');
  });

  it('refuses a query shorter than two letters without asking the server', async () => {
    const user = userEvent.setup();
    const backend = serve(base);
    renderWith(<ArchiveSearchPage />);
    const form = await screen.findByRole('search');
    await user.type(within(form).getByLabelText('Words to find'), 'a!');
    await user.click(within(form).getByRole('button', { name: 'Search' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Type at least two letters.');
    expect(backend.to('GET', '/api/v1/documents/search')).toHaveLength(0);
  });

  it('says when no OCR engine exists, and marks results read by OCR', async () => {
    const user = userEvent.setup();
    serve({
      ...base,
      'GET /api/v1/archive/settings': { ...SETTINGS, ocr: { available: false, engine: 'tesseract', languages: 'ara+eng', detail: 'engine not installed on this server' } },
      'GET /api/v1/documents/search': result([hit('d2', 'Scan', 'scanned judgment', { status: 'OCR_DONE' })]),
    });
    renderWith(<ArchiveSearchPage />);
    expect(await screen.findByText(/No OCR engine is available on this server/)).toBeVisible();
    await user.type(screen.getByLabelText('Words to find'), 'judgment');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText(/Read by OCR/)).toBeVisible();
  });

  it('pages through results', async () => {
    const user = userEvent.setup();
    const backend = serve({
      ...base,
      'GET /api/v1/documents/search': (sent) =>
        result([hit(`d${sent.query.includes('page=1') ? '2' : '1'}`, sent.query.includes('page=1') ? 'Second' : 'First', null)], {
          page: sent.query.includes('page=1') ? 1 : 0,
          totalItems: 2,
          totalPages: 2,
        }),
    });
    renderWith(<ArchiveSearchPage />);
    await user.type(await screen.findByLabelText('Words to find'), 'memo');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('First')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Second')).toBeVisible();
    expect(screen.getByText('Page 2 of 2')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Previous' }));
    expect(await screen.findByText('First')).toBeVisible();
    expect(backend.to('GET', '/api/v1/documents/search')).toHaveLength(3);
  });

  it('explains a refused or failed search and shows the access page without the permission', async () => {
    const user = userEvent.setup();
    serve({ ...base, 'GET /api/v1/documents/search': problem(500) });
    const first = renderWith(<ArchiveSearchPage />);
    await user.type(await screen.findByLabelText('Words to find'), 'memo');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved');
    first.unmount();

    serve({ ...base, 'GET /api/v1/documents/search': problem(403) });
    renderWith(<ArchiveSearchPage />);
    await userEvent.setup().type(await screen.findByLabelText('Words to find'), 'memo');
    await userEvent.setup().click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByRole('heading', { name: /access|permission/i })).toBeVisible();
  });

  it('works in Arabic', async () => {
    const user = userEvent.setup();
    serve({ ...base, 'GET /api/v1/documents/search': result([hit('d1', 'مذكرة', 'نص المذكرة')]) });
    renderWith(<ArchiveSearchPage />, { lang: 'ar' });
    await user.type(await screen.findByLabelText('الكلمات المطلوبة'), 'مذكرة');
    await user.click(screen.getByRole('button', { name: 'بحث' }));
    expect(await screen.findByText('عدد المستندات المطابقة: ١')).toBeVisible();
    expect(screen.getByText('نص المذكرة')).toBeVisible();
  });
});
