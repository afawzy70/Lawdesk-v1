import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Permission } from '../../api/permissions';
import { formatNumber } from '../../i18n/format';
import { jsonResponse } from '../../test/http';
import type { InventoryReport, InventoryRow } from './inventoryApi';
import { COURTS, backend, problem, renderAt } from './testSupport';

const REPORT = '/api/v1/reports/cases';
const VIEWER: Permission[] = ['REPORT_VIEW', 'CASE_VIEW', 'COURT_VIEW'];

const NILE: InventoryRow = {
  id: '3f2c8a8e-6b1d-4c3e-9a51-0d4b7e2f1a90',
  referenceNo: 'LD-2026-00001-WITH-A-VERY-LONG-REFERENCE-NUMBER',
  title: 'Ahmed v. Nile Trading',
  caseType: 'COMMERCIAL',
  status: 'ACTIVE',
  statusGroup: 'OPEN',
  openedOn: '2026-03-01',
  currentStage: 'APPEAL',
  currentCourt: { id: COURTS[1]?.id ?? '', code: 'CAI-A', nameAr: 'محكمة استئناف القاهرة', nameEn: 'Cairo Court of Appeal' },
  proceedingCount: 2,
};
const ESTATE: InventoryRow = {
  ...NILE,
  id: '9d1e7b40-2c55-4f0a-8e3b-6a7c9d0e1f22',
  referenceNo: '123/2025',
  title: 'Estate of Hassan',
  caseType: 'FAMILY',
  status: 'ARCHIVED',
  statusGroup: 'CLOSED',
  currentStage: null,
  currentCourt: null,
  proceedingCount: 0,
};

function report(items: InventoryRow[], extra: Partial<InventoryReport> = {}): Response {
  const body: InventoryReport = {
    items,
    page: 0,
    size: 20,
    totalItems: 1234,
    totalPages: 62,
    summary: {
      total: 1234,
      open: 1200,
      closed: 34,
      byStatus: { OPEN: 700, ACTIVE: 450, SUSPENDED: 50, CLOSED: 30, ARCHIVED: 4 },
      byType: { CIVIL: 600, CRIMINAL: 0, COMMERCIAL: 400, LABOR: 100, FAMILY: 120, ADMINISTRATIVE: 14 },
      byStage: { FIRST_INSTANCE: 900, APPEAL: 200, CASSATION: 30, NONE: 104 },
    },
    ...extra,
  };
  return jsonResponse(body);
}

const table = () => screen.findByRole('table', { name: 'Case inventory' });

describe('case inventory report (LD-031)', () => {
  it('lists the cases with their current stage and court, linking each case', async () => {
    const { sent } = backend(VIEWER, { [REPORT]: () => report([NILE, ESTATE]) });
    await renderAt('/reports');

    expect(screen.getByRole('heading', { level: 1, name: 'Reports' })).toBeInTheDocument();
    const rows = within(await table()).getAllByRole('row');
    expect(within(rows[0] as HTMLElement).getAllByRole('columnheader').map((h) => h.textContent)).toEqual([
      'Reference',
      'Title',
      'Type',
      'Status',
      'Opened on',
      'Current stage',
      'Current court',
      'Proceedings',
    ]);
    expect(within(rows[1] as HTMLElement).getAllByRole('cell').map((c) => c.textContent)).toEqual([
      NILE.referenceNo,
      'Ahmed v. Nile Trading',
      'Commercial',
      'Active',
      '1 Mar 2026',
      'Appeal',
      'Cairo Court of Appeal',
      '2',
    ]);
    expect(within(rows[2] as HTMLElement).getAllByRole('cell').map((c) => c.textContent)).toEqual([
      '123/2025',
      'Estate of Hassan',
      'Family (personal status)',
      'Archived',
      '1 Mar 2026',
      'No proceedings',
      '—',
      '0',
    ]);
    expect(within(rows[1] as HTMLElement).getByRole('link', { name: NILE.referenceNo })).toHaveAttribute(
      'href',
      `/cases/${NILE.id}?lang=en`,
    );
    expect(sent(REPORT)[0]?.params.toString()).toBe('');
    expect(document.title).toBe('Reports — LawDesk (Preview)');
  });

  it('keeps the table in a focusable, named scroll region with wrapping references', async () => {
    backend(VIEWER, { [REPORT]: () => report([NILE]) });
    const { container } = await renderAt('/reports');
    const scroller = await screen.findByRole('region', { name: 'Case inventory' });
    expect(scroller).toHaveClass('table-scroll');
    expect(scroller).toHaveAttribute('tabindex', '0');
    expect(container.querySelector('.rpt-table')).not.toBeNull();
  });

  it('summarises every matching case, not only the page', async () => {
    backend(VIEWER, { [REPORT]: () => report([NILE]) });
    await renderAt('/reports');
    const summary = await screen.findByRole('region', { name: 'Summary' });
    expect(within(summary).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Cases1,234',
      'Open1,200',
      'Closed34',
      'First instance900',
      'Appeal200',
      'Cassation30',
      'No proceedings104',
    ]);
  });

  it('reads every filter, the sort and the page from the address and sends them', async () => {
    const court = COURTS[0]?.id ?? '';
    const { sent } = backend(VIEWER, { [REPORT]: () => report([NILE], { page: 2 }) });
    await renderAt(
      `/reports?q=nile&status=ACTIVE&statusGroup=OPEN&caseType=COMMERCIAL&openedFrom=2026-01-01&openedTo=2026-03-31&stage=APPEAL&courtId=${court}&sort=title,desc&page=2`,
    );
    await table();
    const params = sent(REPORT)[0]?.params;
    expect(Object.fromEntries(params ?? [])).toEqual({
      q: 'nile',
      status: 'ACTIVE',
      statusGroup: 'OPEN',
      caseType: 'COMMERCIAL',
      openedFrom: '2026-01-01',
      openedTo: '2026-03-31',
      stage: 'APPEAL',
      courtId: court,
      sort: 'title,desc',
      page: '2',
    });
    expect(screen.getByRole('searchbox', { name: 'Reference or title contains' })).toHaveValue('nile');
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('ACTIVE');
    expect(screen.getByRole('combobox', { name: 'Open or closed' })).toHaveValue('OPEN');
    expect(screen.getByRole('combobox', { name: 'Case type' })).toHaveValue('COMMERCIAL');
    expect(screen.getByLabelText('Opened from')).toHaveValue('2026-01-01');
    expect(screen.getByLabelText('Opened to')).toHaveValue('2026-03-31');
    expect(screen.getByRole('combobox', { name: 'Current stage' })).toHaveValue('APPEAL');
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: 'Court' })).toHaveValue(court);
    });
    expect(screen.getByRole('combobox', { name: 'Sort by' })).toHaveValue('title');
    expect(screen.getByRole('combobox', { name: 'Order' })).toHaveValue('desc');
    expect(screen.getByText('Page 3 of 62')).toBeInTheDocument();
  });

  it('drops malformed values from the address instead of sending them', async () => {
    const { sent } = backend(VIEWER, { [REPORT]: () => report([NILE]) });
    await renderAt('/reports?status=LOST&openedFrom=yesterday&courtId=x&sort=court,asc&page=-2&stage=FINAL');
    await table();
    expect(sent(REPORT)[0]?.params.toString()).toBe('');
  });

  it('applies the chosen filters and sort through the address, and clears them', async () => {
    const user = userEvent.setup();
    const { sent, requests } = backend(VIEWER, { [REPORT]: () => report([NILE]) });
    const { location } = await renderAt('/reports');
    await table();

    await user.type(screen.getByRole('searchbox', { name: 'Reference or title contains' }), 'estate');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Open or closed' }), 'CLOSED');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Case type' }), 'FAMILY');
    await user.type(screen.getByLabelText('Opened from'), '2025-01-01');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Current stage' }), 'FIRST_INSTANCE');
    await waitFor(() => {
      expect(within(screen.getByRole('combobox', { name: 'Court' })).getAllByRole('option')).toHaveLength(3);
    });
    await user.selectOptions(screen.getByRole('combobox', { name: 'Court' }), COURTS[0]?.id ?? '');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Sort by' }), 'referenceNo');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Order' }), 'asc');
    await user.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() => {
      expect(sent(REPORT)).toHaveLength(2);
    });
    expect(Object.fromEntries(sent(REPORT)[1]?.params ?? [])).toEqual({
      q: 'estate',
      statusGroup: 'CLOSED',
      caseType: 'FAMILY',
      openedFrom: '2025-01-01',
      stage: 'FIRST_INSTANCE',
      courtId: COURTS[0]?.id,
      sort: 'referenceNo,asc',
    });
    expect(location()).toContain('statusGroup=CLOSED');
    expect(location()).toContain('lang=en');

    await user.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => {
      expect(sent(REPORT)).toHaveLength(3);
    });
    expect(sent(REPORT)[2]?.params.toString()).toBe('');
    expect(requests.every((request) => request.method === 'GET')).toBe(true);
  });

  it('pages forwards and back', async () => {
    const user = userEvent.setup();
    const { sent } = backend(VIEWER, {
      [REPORT]: (request) => report([NILE], { page: Number(request.params.get('page') ?? '0') }),
    });
    await renderAt('/reports');
    await table();
    const pages = screen.getByRole('navigation', { name: 'Report pages' });
    expect(within(pages).getByRole('button', { name: 'Previous' })).toBeDisabled();
    await user.click(within(pages).getByRole('button', { name: 'Next' }));
    await waitFor(() => {
      expect(sent(REPORT).at(-1)?.params.get('page')).toBe('1');
    });
    expect(await screen.findByText('Page 2 of 62')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Previous' }));
    await waitFor(() => {
      expect(sent(REPORT).at(-1)?.params.get('page')).toBeNull();
    });
  });

  it('shows an empty result plainly', async () => {
    backend(VIEWER, {
      [REPORT]: () => report([], { totalItems: 0, totalPages: 0 }),
    });
    await renderAt('/reports?q=nothing');
    expect(await screen.findByText('No cases match these filters.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('offers the court filter only to users who may read courts', async () => {
    const { sent } = backend(['REPORT_VIEW'], { [REPORT]: () => report([NILE]) });
    await renderAt('/reports');
    await table();
    expect(screen.queryByRole('combobox', { name: 'Court' })).not.toBeInTheDocument();
    expect(sent('/api/v1/courts')).toHaveLength(0);
  });

  it('keeps the report usable when the courts cannot be loaded', async () => {
    backend(VIEWER, { [REPORT]: () => report([NILE]), '/api/v1/courts': () => problem(500) });
    await renderAt('/reports');
    await table();
    expect(screen.queryByRole('combobox', { name: 'Court' })).not.toBeInTheDocument();
  });

  it.each([
    { answer: () => problem(400, { errors: [{ field: 'sort', code: 'Pattern' }] }), text: 'Check the filters: one of the values is not valid.' },
    { answer: () => problem(422, { rule: 'DATE_RANGE_INVALID' }), text: 'The start date is after the end date.' },
    { answer: () => problem(500), text: 'The report could not be loaded.' },
    { answer: () => jsonResponse({ items: 'none' }), text: 'The report could not be loaded.' },
  ])('explains a refused or failed request: $text', async ({ answer, text }) => {
    backend(VIEWER, { [REPORT]: answer });
    await renderAt('/reports');
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
  });

  it('shows the not-permitted page on 403', async () => {
    backend(VIEWER, { [REPORT]: () => problem(403) });
    await renderAt('/reports');
    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
  });

  it('names the report sections, with the reports that wait for Phase 5 marked as not available', async () => {
    backend(VIEWER, { [REPORT]: () => report([NILE]) });
    await renderAt('/reports');
    const sections = screen.getByRole('navigation', { name: 'Report sections' });
    expect(within(sections).getByRole('link', { name: 'Case inventory' })).toHaveAttribute('aria-current', 'page');
    expect(sections).toHaveTextContent('Hearings report — not available yet');
    expect(sections).toHaveTextContent('Experts and workload reports — not available yet');
  });

  it('opens from the primary navigation for users with REPORT_VIEW', async () => {
    const user = userEvent.setup();
    backend(VIEWER, { [REPORT]: () => report([NILE]) });
    await renderAt('/');
    await user.click(within(screen.getByRole('navigation', { name: 'Primary' })).getByRole('link', { name: 'Reports' }));
    expect(await table()).toBeInTheDocument();
  });
});

describe('case inventory report in Arabic', () => {
  it('labels filters, columns and courts in Arabic with Arabic-Indic digits', async () => {
    backend(VIEWER, { [REPORT]: () => report([{ ...NILE, referenceNo: 'م-١' }]) });
    await renderAt('/reports?lang=ar');
    const inventory = await screen.findByRole('table', { name: 'جرد القضايا' });
    const cells = within(within(inventory).getAllByRole('row')[1] as HTMLElement).getAllByRole('cell');
    expect(cells[2]).toHaveTextContent('تجاري');
    expect(cells[5]).toHaveTextContent('الاستئناف');
    expect(cells[6]).toHaveTextContent('محكمة استئناف القاهرة');
    expect(cells[7]).toHaveTextContent(formatNumber(2, 'ar'));
    expect(screen.getByRole('searchbox', { name: 'المرجع أو العنوان يحتوي على' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'الملخص' }).textContent).toContain(formatNumber(1234, 'ar'));
    expect(screen.getByRole('main').textContent).not.toMatch(/[0-9]/);
  });
});

describe('case inventory export (LD-034)', () => {
  it('offers a CSV download of the filtered report, in the page language, to users with REPORT_EXPORT', async () => {
    const court = COURTS[0]?.id ?? '';
    backend([...VIEWER, 'REPORT_EXPORT'], { [REPORT]: () => report([NILE]) });
    await renderAt(`/reports?lang=ar&q=nile&courtId=${court}&sort=title,desc&page=3`);
    const link = await screen.findByRole('link', { name: 'تنزيل CSV' });
    expect(link).toHaveAttribute(
      'href',
      `/api/v1/reports/cases/export?q=nile&courtId=${court}&sort=title%2Cdesc&format=csv&lang=ar`,
    );
    expect(link).toHaveAttribute('download');
  });

  it('offers no export without REPORT_EXPORT', async () => {
    backend(VIEWER, { [REPORT]: () => report([NILE]) });
    await renderAt('/reports');
    await table();
    expect(screen.queryByRole('link', { name: 'Download CSV' })).not.toBeInTheDocument();
  });
});
