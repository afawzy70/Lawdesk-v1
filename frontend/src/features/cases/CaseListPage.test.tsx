import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { CaseListItem, PageResponse } from './api';
import { jsonResponse, problem, renderCases, scriptedFetch } from './testSupport';

const NILE: CaseListItem = {
  id: '3f2c8a8e-6b1d-4c3e-9a51-0d4b7e2f1a90',
  referenceNo: 'LD-2026-00001',
  title: 'Ahmed v. Nile Trading',
  caseType: 'COMMERCIAL',
  status: 'OPEN',
  openedOn: '2026-03-01',
  updatedAt: '2026-03-02T09:30:00Z',
};
const ESTATE: CaseListItem = {
  ...NILE,
  id: '9d1e7b40-2c55-4f0a-8e3b-6a7c9d0e1f22',
  referenceNo: '123/2025',
  title: 'Estate of Hassan',
  caseType: 'FAMILY',
  status: 'ACTIVE',
};

function page(items: CaseListItem[], extra: Partial<PageResponse<CaseListItem>> = {}) {
  return jsonResponse({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });
}

function urlParams(url: string) {
  return new URL(url, 'http://localhost').searchParams;
}

describe('case list (LD-015)', () => {
  it('lists the cases with translated type and status, linking each to its edit page', async () => {
    const { requests } = scriptedFetch(() => page([NILE, ESTATE]));
    renderCases('/cases');

    expect(screen.getByRole('heading', { level: 1, name: 'Cases' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Cases' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('Ahmed v. Nile Trading');
    expect(rows[1]).toHaveTextContent('Commercial');
    expect(rows[1]).toHaveTextContent('Open');
    expect(rows[1]).toHaveTextContent('01/03/2026');
    expect(rows[2]).toHaveTextContent('Family');
    expect(rows[2]).toHaveTextContent('Active');
    expect(within(rows[1] as HTMLElement).getByRole('link', { name: 'LD-2026-00001' })).toHaveAttribute(
      'href',
      `/cases/${NILE.id}/edit?lang=en`,
    );
    expect(screen.getByText('Results: 2')).toBeInTheDocument();
    expect(requests[0]?.url).toBe('/api/v1/cases');
    expect(document.title).toBe('Cases — LawDesk (Preview)');
  });

  it('offers a link to create a case', async () => {
    scriptedFetch(() => page([]));
    renderCases('/cases');

    expect(screen.getByRole('link', { name: 'New case' })).toHaveAttribute('href', '/cases/new?lang=en');
    expect(await screen.findByText('No cases match these filters.')).toBeInTheDocument();
  });

  it('reads the filters from the address, so a reload shows the same results', async () => {
    const { requests } = scriptedFetch(() => page([NILE], { page: 1, totalItems: 21, totalPages: 2 }));
    renderCases('/cases?q=nile&status=OPEN&caseType=COMMERCIAL&page=1');

    await screen.findByRole('table', { name: 'Cases' });
    const sent = urlParams(requests[0]?.url ?? '');
    expect(sent.get('q')).toBe('nile');
    expect(sent.get('status')).toBe('OPEN');
    expect(sent.get('caseType')).toBe('COMMERCIAL');
    expect(sent.get('page')).toBe('1');
    expect(screen.getByRole('searchbox', { name: 'Search by reference or title' })).toHaveValue('nile');
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('OPEN');
    expect(screen.getByRole('combobox', { name: 'Case type' })).toHaveValue('COMMERCIAL');
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
  });

  it('ignores unknown filter values in the address', async () => {
    const { requests } = scriptedFetch(() => page([]));
    renderCases('/cases?status=DELETED&caseType=TAX&page=-3');

    await screen.findByText('No cases match these filters.');
    expect(requests[0]?.url).toBe('/api/v1/cases');
  });

  it('searches and filters from the first page, keeping the language in the address', async () => {
    const user = userEvent.setup();
    const { requests } = scriptedFetch(() => page([ESTATE]));
    const { url: currentUrl } = renderCases('/cases?page=3&lang=en');
    await screen.findByRole('table');

    await user.type(screen.getByRole('searchbox', { name: 'Search by reference or title' }), 'hassan');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'ACTIVE');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Case type' }), 'FAMILY');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(requests).toHaveLength(2);
    });
    expect(requests[1]?.url).toBe('/api/v1/cases?q=hassan&status=ACTIVE&caseType=FAMILY');
    const url = urlParams(currentUrl());
    expect(url.get('lang')).toBe('en');
    expect(url.get('page')).toBeNull();
  });

  it('clears every filter', async () => {
    const user = userEvent.setup();
    const { requests } = scriptedFetch(() => page([NILE]));
    renderCases('/cases?q=nile&status=OPEN');
    await screen.findByRole('table');

    await user.click(screen.getByRole('button', { name: 'Clear filters' }));

    await waitFor(() => {
      expect(requests.at(-1)?.url).toBe('/api/v1/cases');
    });
    expect(screen.getByRole('searchbox', { name: 'Search by reference or title' })).toHaveValue('');
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('');
  });

  it('pages forwards and back', async () => {
    const user = userEvent.setup();
    const { requests } = scriptedFetch((request) => {
      const current = Number(urlParams(request.url).get('page') ?? '0');
      return page([NILE], { page: current, totalItems: 45, totalPages: 3 });
    });
    renderCases('/cases');
    await screen.findByText('Page 1 of 3');
    // The list re-renders after every page change, so the controls are looked up again each time.
    const button = (name: string) =>
      within(screen.getByRole('navigation', { name: 'Pagination' })).getByRole('button', { name });
    expect(button('Previous page')).toBeDisabled();

    await user.click(button('Next page'));
    await screen.findByText('Page 2 of 3');
    expect(requests.at(-1)?.url).toBe('/api/v1/cases?page=1');

    await user.click(button('Next page'));
    await screen.findByText('Page 3 of 3');
    expect(button('Next page')).toBeDisabled();
    expect(requests.at(-1)?.url).toBe('/api/v1/cases?page=2');

    await user.click(button('Previous page'));
    await screen.findByText('Page 2 of 3');
    expect(requests.at(-1)?.url).toBe('/api/v1/cases?page=1');
  });

  it('says so when the list cannot be loaded, without showing server details', async () => {
    scriptedFetch(() => problem(500, { detail: 'org.postgresql.util.PSQLException' }));
    const { container } = renderCases('/cases');

    expect(await screen.findByRole('alert')).toHaveTextContent('The cases could not be loaded. Try again later.');
    expect(container.textContent).not.toContain('PSQLException');
  });

  it('treats an answer that is not a case page as a failure', async () => {
    scriptedFetch(() => jsonResponse({ status: 'UP' }));
    renderCases('/cases');
    expect(await screen.findByRole('alert')).toHaveTextContent('The cases could not be loaded. Try again later.');
  });

  it('explains a missing permission or session', async () => {
    scriptedFetch(() => problem(403));
    renderCases('/cases');
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to do this.');
  });

  it('asks to sign in when there is no session', async () => {
    scriptedFetch(() => problem(401));
    renderCases('/cases');
    expect(await screen.findByRole('alert')).toHaveTextContent('Sign in to continue.');
  });

  it('shows a loading state until the first answer arrives', () => {
    scriptedFetch(() => new Promise<Response>(() => undefined));
    renderCases('/cases');
    expect(screen.getByRole('status')).toHaveTextContent('Loading cases…');
  });

  it('renders in Arabic with Arabic-Indic digits', async () => {
    scriptedFetch(() => page([NILE], { totalItems: 21, totalPages: 2 }));
    renderCases('/cases?lang=ar');

    expect(screen.getByRole('heading', { level: 1, name: 'القضايا' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'القضايا' });
    expect(table).toHaveTextContent('تجاري');
    expect(table).toHaveTextContent('مفتوحة');
    expect(screen.getByText('صفحة ١ من ٢')).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: 'البحث برقم القضية أو عنوانها' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'قضية جديدة' })).toHaveAttribute('href', '/cases/new?lang=ar');
  });
});
