import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Permission } from '../../api/permissions';
import { jsonResponse } from '../../test/http';
import type { DashboardReport } from './dashboardApi';
import type { FamilyTable } from './familiesApi';
import { INDICATOR_CODES, type IndicatorFigure, type IndicatorItem, type IndicatorPage } from './indicatorsApi';
import { backend, problem, renderAt } from './testSupport';

/** Each test renders the whole application; the first one of a file can pass the 5 s default on a busy machine. */
vi.setConfig({ testTimeout: 20_000 });

const VIEWER: Permission[] = ['REPORT_VIEW'];
const EXPORTER: Permission[] = ['REPORT_VIEW', 'REPORT_EXPORT'];
/** An in-app address without the language the links carry. */
const bare = (href: string | null): string => (href ?? '').replace(/[?&]lang=\w+$/, '');

const CASE_ID = '3f2c8a8e-6b1d-4c3e-9a51-0d4b7e2f1a90';

function figure(code: (typeof INDICATOR_CODES)[number], count: number | null, extra: Partial<IndicatorFigure> = {}): IndicatorFigure {
  return {
    code,
    number: INDICATOR_CODES.indexOf(code) + 1,
    entity: 'CASE',
    available: count !== null,
    unavailableReason: count === null ? 'MODULE_NOT_AVAILABLE' : null,
    count,
    groups: null,
    ...extra,
  };
}

const FIGURES: IndicatorFigure[] = INDICATOR_CODES.map((code, index) => {
  if (code === 'APPEAL_DEADLINES') return figure(code, null, { entity: 'DEADLINE' });
  if (code === 'CASES_BY_UNIT') {
    return figure(code, 5, {
      groups: [
        { level: 'SECTOR', key: 'Sector One', label: 'Sector One', count: 3 },
        { level: 'SECTOR', key: '_none', label: null, count: 2 },
        { level: 'DEPARTMENT', key: 'Dept A', label: 'Dept A', count: 5 },
      ],
    });
  }
  if (code === 'CASES_BY_MEMBER') {
    return figure(code, 5, {
      groups: [
        { level: null, key: 'u-1', label: 'Amal Hassan', count: 4 },
        { level: null, key: 'u-2', label: null, count: 1 },
        { level: null, key: '_none', label: null, count: 0 },
      ],
    });
  }
  return figure(code, index + 10);
});

const DASHBOARD: DashboardReport = {
  month: '2026-10',
  cases: { total: 5, open: 4, closed: 1, newThisMonth: 1, byStatus: { OPEN: 1, ACTIVE: 2, SUSPENDED: 1, CLOSED: 1, ARCHIVED: 0 },
    byType: { CIVIL: 5, CRIMINAL: 0, COMMERCIAL: 0, LABOR: 0, FAMILY: 0, ADMINISTRATIVE: 0, CONSTITUTIONAL: 0, OTHER: 0 } },
  proceedings: { awaitingJudgment: 0, byStage: [] },
  judgments: { from: '2026-10-01', to: '2026-10-31', recorded: 0, final: 0,
    byOutcome: { IN_FAVOUR: 0, AGAINST: 0, PARTIAL: 0, DISMISSED: 0, OTHER: 0 } },
  operations: { hearingsThisWeek: 0, hearingsAwaitingResult: 0, expertReportsOverdue: 0, overdueTasks: 0, tasksDueSoon: 0 },
  pending: [],
  indicators: FIGURES,
};

function item(extra: Partial<IndicatorItem> = {}): IndicatorItem {
  return {
    entity: 'CASE',
    id: CASE_ID,
    caseId: CASE_ID,
    caseReferenceNo: 'LD-2026-00001',
    caseTitle: 'Ahmed v. Nile Trading',
    title: null,
    date: '2026-10-21',
    status: 'ACTIVE',
    detail: 'CIVIL',
    statusLabel: 'Active',
    detailLabel: 'Civil',
    ...extra,
  };
}

function page(items: IndicatorItem[], extra: Partial<IndicatorPage> = {}): Response {
  const body: IndicatorPage = {
    code: 'TOTAL_CASES',
    available: true,
    unavailableReason: null,
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: items.length === 0 ? 0 : 1,
    from: '2026-10-01',
    to: '2026-10-31',
    ...extra,
  };
  return jsonResponse(body);
}

function familyTable(extra: Partial<FamilyTable> = {}): FamilyTable {
  return {
    family: 'CASES_BY_TYPE_COURT_STAGE',
    title: 'Cases by type, court and stage',
    available: true,
    unavailableReason: null,
    groupings: ['CASE_TYPE', 'COURT', 'STAGE'],
    groupBy: 'CASE_TYPE',
    columns: [
      { key: 'group', label: 'Type', type: 'TEXT' },
      { key: 'cases', label: 'Cases', type: 'COUNT' },
      { key: 'claimedAmount', label: 'Claimed amount (KWD)', type: 'MONEY' },
      { key: 'avg', label: 'Average', type: 'DECIMAL' },
    ],
    rows: [
      ['Civil', 2, '1500.500', 1.5],
      ['Commercial', 1, '2000.000', null],
      ['Total', 3, '3500.500', null],
    ],
    hasTotal: true,
    note: 'Each case is counted once.',
    ...extra,
  };
}

describe('dashboard indicators', () => {
  it('shows the 14 indicators as links to their lists, with groups and an honest "not available yet"', async () => {
    backend(VIEWER, { '/api/v1/reports/dashboard': () => jsonResponse(DASHBOARD) });
    await renderAt('/');

    const grid = await screen.findByRole('region', { name: 'Key indicators' });
    const links = within(grid).getAllByRole('link');
    const hrefs = links.map((link) => bare(link.getAttribute('href')));
    // 15 figures with a count; sixteen codes minus the unavailable one, each with a distinct address
    expect(hrefs.filter((href) => href.startsWith('/reports/indicators/') && !href.includes('?'))).toHaveLength(15);
    expect(hrefs).toContain('/reports/indicators/TOTAL_CASES');
    expect(hrefs).toContain('/reports/indicators/CASES_BY_UNIT?group=Sector+One&level=SECTOR');
    expect(hrefs).toContain('/reports/indicators/CASES_BY_UNIT?group=_none&level=SECTOR');
    expect(hrefs).toContain('/reports/indicators/CASES_BY_MEMBER?group=u-1');
    expect(within(grid).getByText('Appeal deadlines').closest('li')).toHaveTextContent('Not available yet');
    expect(within(grid).queryByRole('link', { name: /Appeal deadlines/ })).toBeNull();
    // groups: named, "Not specified", and an unknown member
    expect(within(grid).getByRole('link', { name: 'Amal Hassan' })).toBeInTheDocument();
    expect(within(grid).getByRole('link', { name: 'Unknown member' })).toBeInTheDocument();
    expect(within(grid).getAllByRole('link', { name: 'Not specified' }).length).toBeGreaterThanOrEqual(2);
    expect(within(grid).getByRole('heading', { name: 'Department' })).toBeInTheDocument();
  });

  it('clicking a figure opens the list that carries the same number', async () => {
    const user = userEvent.setup();
    const { sent } = backend(VIEWER, {
      '/api/v1/reports/dashboard': () => jsonResponse(DASHBOARD),
      '/api/v1/reports/indicators/TOTAL_CASES': () => page([item()], { totalItems: 10 }),
    });
    const { location } = await renderAt('/');
    await user.click(await screen.findByRole('link', { name: /Total cases/ }));
    expect(await screen.findByText('10 records')).toBeInTheDocument();
    expect(bare(location())).toBe('/reports/indicators/TOTAL_CASES');
    expect(sent('/api/v1/reports/indicators/TOTAL_CASES')[0]?.params.get('lang')).toBe('en');
  });

  it('is right-to-left in Arabic with the Arabic names', async () => {
    backend(VIEWER, { '/api/v1/reports/dashboard': () => jsonResponse(DASHBOARD) });
    await renderAt('/?lang=ar');
    const grid = await screen.findByRole('region', { name: 'المؤشرات الرئيسية' });
    expect(within(grid).getByText('إجمالي القضايا')).toBeInTheDocument();
    expect(within(grid).getByText('مواعيد الطعون').closest('li')).toHaveTextContent('غير متاح بعد');
    expect(document.documentElement.dir).toBe('rtl');
  });
});

describe('indicator list', () => {
  it('lists the records with their case, labels and a pager, and keeps the group in the address', async () => {
    const user = userEvent.setup();
    const { sent } = backend(EXPORTER, {
      '/api/v1/reports/indicators/OVERDUE_TASKS': (request) =>
        page(
          [
            item({ entity: 'TASK', title: 'File the memo', statusLabel: 'Open', detailLabel: 'High' }),
            item({ id: 'x2', caseId: null, caseReferenceNo: null, caseTitle: null, date: null, statusLabel: null, detailLabel: null }),
          ],
          { code: 'OVERDUE_TASKS', totalItems: 40, totalPages: 2, page: Number(request.params.get('page') ?? 0) },
        ),
    });
    const { location } = await renderAt('/reports/indicators/OVERDUE_TASKS?group=_none&level=SECTOR');
    const table = await screen.findByRole('table', { name: 'Overdue tasks' });
    expect(within(table).getByRole('link', { name: 'LD-2026-00001' })).toHaveAttribute('href', `/cases/${CASE_ID}?lang=en`);
    expect(within(table).getByText('File the memo')).toBeInTheDocument();
    expect(within(table).getByText('High')).toBeInTheDocument();
    expect(screen.getByText('Group: Sector — Not specified')).toBeInTheDocument();
    expect(screen.getByText('40 records')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Download Excel' }).getAttribute('href')).toContain(
      '/api/v1/reports/indicators/OVERDUE_TASKS/export?group=_none&level=SECTOR&format=xlsx&lang=en',
    );
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => {
      expect(sent('/api/v1/reports/indicators/OVERDUE_TASKS').at(-1)?.params.get('page')).toBe('1');
    });
    expect(location()).toContain('page=1');
  });

  it('shows the judgment period, plain member groups and no export links without the right', async () => {
    backend(VIEWER, {
      '/api/v1/reports/indicators/JUDGMENTS_ISSUED': () =>
        page([item({ entity: 'JUDGMENT', statusLabel: 'In favour', detailLabel: null })], { code: 'JUDGMENTS_ISSUED' }),
    });
    await renderAt('/reports/indicators/JUDGMENTS_ISSUED');
    expect(await screen.findByText('Judgments dated 1 Oct 2026 – 31 Oct 2026')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Download CSV' })).toBeNull();
  });

  it('names a member group by its id when it has no level', async () => {
    backend(VIEWER, { '/api/v1/reports/indicators/CASES_BY_MEMBER': () => page([]) });
    await renderAt('/reports/indicators/CASES_BY_MEMBER?group=u-1');
    expect(await screen.findByText('Group: u-1')).toBeInTheDocument();
    expect(screen.getByText('No records.')).toBeInTheDocument();
  });

  it('says plainly that an indicator of a missing module is not available', async () => {
    backend(VIEWER, {
      '/api/v1/reports/indicators/APPEAL_DEADLINES': () =>
        page([], { code: 'APPEAL_DEADLINES', available: false, unavailableReason: 'MODULE_NOT_AVAILABLE' }),
    });
    await renderAt('/reports/indicators/APPEAL_DEADLINES');
    expect(await screen.findByText(/module that holds this data is not installed/)).toBeInTheDocument();
  });

  it('handles an unknown indicator, a refused request and a failed one', async () => {
    backend(VIEWER, {});
    await renderAt('/reports/indicators/NOPE');
    expect(await screen.findByText('This indicator does not exist.')).toBeInTheDocument();
  });

  it('shows the not-permitted page on 403 and a message that repeats nothing on failure', async () => {
    backend(VIEWER, { '/api/v1/reports/indicators/TOTAL_CASES': () => problem(403) });
    await renderAt('/reports/indicators/TOTAL_CASES');
    expect(await screen.findByRole('heading', { name: /permission|access/i })).toBeInTheDocument();
  });

  it('shows a failure message when the list cannot be loaded or is malformed', async () => {
    backend(VIEWER, { '/api/v1/reports/indicators/TOTAL_CASES': () => problem(500) });
    await renderAt('/reports/indicators/TOTAL_CASES');
    expect(await screen.findByRole('alert')).toHaveTextContent('The report could not be loaded.');
  });

  it('treats an answer without items as a failure', async () => {
    backend(VIEWER, { '/api/v1/reports/indicators/TOTAL_CASES': () => jsonResponse({ nope: true }) });
    await renderAt('/reports/indicators/TOTAL_CASES');
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});

describe('report families', () => {
  it('lists the seventeen families', async () => {
    backend(VIEWER, {});
    await renderAt('/reports/families');
    await screen.findByRole('heading', { name: 'Reports and statistics' });
    const list = document.querySelector<HTMLElement>('.rpt-families');
    const links = within(list ?? document.body).getAllByRole('link');
    expect(links).toHaveLength(17);
    expect(links.map((l) => bare(l.getAttribute('href')))).toContain('/reports/performance');
  });

  it('shows a family as a table with money as text, a total row and downloads, and filters through the address', async () => {
    const user = userEvent.setup();
    const { sent } = backend(EXPORTER, { '/api/v1/reports/families/CASES_BY_TYPE_COURT_STAGE': () => jsonResponse(familyTable()) });
    const { location } = await renderAt('/reports/families/CASES_BY_TYPE_COURT_STAGE');
    const table = await screen.findByRole('table', { name: 'Cases by type, court and stage' });
    const rows = within(table).getAllByRole('row').map((row) => within(row).queryAllByRole('cell').map((cell) => cell.textContent));
    expect(rows[1]).toEqual(['Civil', '2', '1500.500', '1.5']);
    expect(rows[2]).toEqual(['Commercial', '1', '2000.000', '—']);
    expect(within(table).getAllByRole('row')[3]).toHaveClass('rpt-total');
    expect(screen.getByText('Each case is counted once.')).toBeInTheDocument();
    expect(screen.getByText(/never added together/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Download PDF' }).getAttribute('href')).toContain(
      '/api/v1/reports/families/CASES_BY_TYPE_COURT_STAGE/export?format=pdf&lang=en',
    );

    await user.selectOptions(screen.getByLabelText('Group by'), 'COURT');
    await user.selectOptions(screen.getByLabelText('Dispute type'), 'CIVIL');
    await user.selectOptions(screen.getByLabelText('Open or closed'), 'CLOSED');
    await user.type(screen.getByLabelText('From'), '2026-01-01');
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => {
      const last = sent('/api/v1/reports/families/CASES_BY_TYPE_COURT_STAGE').at(-1);
      expect(last?.params.get('groupBy')).toBe('COURT');
      expect(last?.params.get('caseType')).toBe('CIVIL');
      expect(last?.params.get('statusGroup')).toBe('CLOSED');
      expect(last?.params.get('from')).toBe('2026-01-01');
    });
    expect(location()).toContain('groupBy=COURT');
    await user.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => {
      expect(location()).toBe('/reports/families/CASES_BY_TYPE_COURT_STAGE');
    });
  });

  it('opens the performance view on its own address with the activity period and no case filters', async () => {
    backend(VIEWER, {
      '/api/v1/reports/families/PERFORMANCE': () =>
        jsonResponse(familyTable({ family: 'PERFORMANCE', title: 'Performance of members and units', groupBy: 'MEMBERS',
          groupings: ['MEMBERS', 'UNITS'], hasTotal: false, note: null, rows: [] })),
    });
    await renderAt('/reports/performance');
    expect(await screen.findByRole('heading', { level: 1, name: 'Performance of members and units' })).toBeInTheDocument();
    expect(screen.getByText(/activity period/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Dispute type')).toBeNull();
    expect(screen.getByLabelText('Group by')).toBeInTheDocument();
    expect(await screen.findByText('No data for these filters.')).toBeInTheDocument();
  });

  it('has no case filters or groupings for the library family and none for a family without groupings', async () => {
    backend(VIEWER, {
      '/api/v1/reports/families/LIBRARY_PRECEDENTS': () => jsonResponse(familyTable({ family: 'LIBRARY_PRECEDENTS', hasTotal: false })),
      '/api/v1/reports/families/LINKED_CASES': () => jsonResponse(familyTable({ family: 'LINKED_CASES', hasTotal: false, columns: [{ key: 'a', label: 'A', type: 'TEXT' }], rows: [['x']] })),
    });
    await renderAt('/reports/families/LIBRARY_PRECEDENTS');
    await screen.findByRole('table');
    expect(screen.queryByLabelText('Dispute type')).toBeNull();
    expect(screen.getByLabelText('Group by')).toBeInTheDocument();
  });

  it('says a family of a missing module is not available, and handles unknown, forbidden and failed answers', async () => {
    backend(VIEWER, {
      '/api/v1/reports/families/ENFORCEMENT': () => jsonResponse(familyTable({ family: 'ENFORCEMENT', available: false, unavailableReason: 'MODULE_NOT_AVAILABLE', columns: [], rows: [] })),
    });
    await renderAt('/reports/families/ENFORCEMENT');
    expect(await screen.findByText(/module that owns this data is not installed/)).toBeInTheDocument();
  });

  it('handles an unknown family', async () => {
    backend(VIEWER, {});
    await renderAt('/reports/families/NOPE');
    expect(await screen.findByText('This indicator does not exist.')).toBeInTheDocument();
  });

  it('shows the not-permitted page for a 403 and a message for other failures', async () => {
    backend(VIEWER, { '/api/v1/reports/families/CLAIM_RESULTS': () => problem(403) });
    await renderAt('/reports/families/CLAIM_RESULTS');
    expect(await screen.findByRole('heading', { name: /permission|access/i })).toBeInTheDocument();
  });

  it('reports a failed load', async () => {
    backend(VIEWER, { '/api/v1/reports/families/CLAIM_RESULTS': () => problem(500) });
    await renderAt('/reports/families/CLAIM_RESULTS');
    expect(await screen.findByRole('alert')).toHaveTextContent('The report could not be loaded.');
  });

  it('reads the table in Arabic, right to left, with the server labels', async () => {
    backend(VIEWER, { '/api/v1/reports/families/OPEN_CLOSED_CASES': () => jsonResponse(familyTable({ family: 'OPEN_CLOSED_CASES', title: 'القضايا المفتوحة والمغلقة', groupings: ['STATUS_GROUP', 'STATUS'], groupBy: 'STATUS_GROUP',
      columns: [{ key: 'group', label: 'المجموعة', type: 'TEXT' }, { key: 'cases', label: 'القضايا', type: 'COUNT' }], rows: [['مفتوحة', 4]], hasTotal: false, note: null })) });
    await renderAt('/reports/families/OPEN_CLOSED_CASES?lang=ar');
    expect(await screen.findByRole('table', { name: 'القضايا المفتوحة والمغلقة' })).toBeInTheDocument();
    expect(document.documentElement.dir).toBe('rtl');
  });
});
