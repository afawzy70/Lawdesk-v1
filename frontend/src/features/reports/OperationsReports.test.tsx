import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Permission } from '../../api/permissions';
import { formatNumber } from '../../i18n/format';
import { jsonResponse } from '../../test/http';
import type { ExpertsReport, HearingReport, HearingRow, WorkloadReport } from './operationsApi';
import { COURTS, backend, problem, renderAt } from './testSupport';

/** Each test renders the whole application; the first one of a file can pass the 5 s default on a busy machine. */
vi.setConfig({ testTimeout: 20_000 });

const VIEWER: Permission[] = ['REPORT_VIEW', 'CASE_VIEW'];
const EXPORTER: Permission[] = [...VIEWER, 'REPORT_EXPORT'];

const HEARING: HearingRow = {
  id: '00000000-0000-4000-8000-000000000401',
  hearingDate: '2026-10-21',
  hearingTime: '09:15:00',
  purpose: 'PLEADING',
  status: 'SCHEDULED',
  awaitingResult: true,
  stage: 'APPEAL',
  court: { id: COURTS[1]?.id ?? '', code: 'CAI-A', nameAr: 'محكمة استئناف القاهرة', nameEn: 'Cairo Court of Appeal' },
  courtCaseNumber: '1234',
  courtCaseYear: 2025,
  caseId: '3f2c8a8e-6b1d-4c3e-9a51-0d4b7e2f1a90',
  caseReferenceNo: 'LD-2026-00001',
  caseTitle: 'Ahmed v. Nile Trading',
};

function hearings(items: HearingRow[], extra: Partial<HearingReport> = {}): Response {
  const body: HearingReport = {
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: items.length === 0 ? 0 : 1,
    from: '2026-10-01',
    to: '2026-10-31',
    summary: { total: items.length, byStatus: { SCHEDULED: 1, HELD: 0, ADJOURNED: 0, CANCELLED: 0 }, awaitingResult: 1 },
    ...extra,
  };
  return jsonResponse(body);
}

const WORKLOAD: WorkloadReport = {
  items: [
    { assigneeId: 'a1', username: 'amal', displayName: 'Amal Hassan', enabled: true, open: 3, inProgress: 1, overdue: 2 },
    { assigneeId: 'u9', username: null, displayName: null, enabled: null, open: 1, inProgress: 0, overdue: 1 },
  ],
  dueSoonDays: 3,
  summary: { byStatus: { OPEN: 4, IN_PROGRESS: 1, DONE: 7, CANCELLED: 0 }, overdue: 3, dueSoon: 2 },
};

const EXPERTS: ExpertsReport = {
  items: [
    { expertId: 'e1', name: 'Alia Mostafa', specialty: 'Accounting', active: true, open: 2, overdue: 1 },
    { expertId: 'e2', name: 'Zaki Fahmy', specialty: 'Medicine', active: false, open: 0, overdue: 0 },
  ],
  summary: { byStatus: { OPEN: 2, REPORT_SUBMITTED: 5, CANCELLED: 1 }, overdueReports: 1 },
};

const rowsOf = async (name: string) =>
  within(await screen.findByRole('table', { name }))
    .getAllByRole('row')
    .map((row) => within(row).queryAllByRole('cell').map((cell) => cell.textContent));

const summaryOf = () =>
  within(screen.getByRole('region', { name: 'Summary' }))
    .getAllByRole('listitem')
    .map((item) => item.textContent);

describe('hearings report (LD-032)', () => {
  it('lists the hearings of the period in calendar order with case, court and status', async () => {
    const { sent } = backend(VIEWER, { '/api/v1/reports/hearings': () => hearings([HEARING]) });
    await renderAt('/reports/hearings');
    expect((await rowsOf('Hearings'))[1]).toEqual([
      '21 Oct 2026',
      '09:15',
      'LD-2026-00001Ahmed v. Nile Trading',
      'Appeal',
      'Cairo Court of Appeal',
      '1234/2025',
      'Pleading',
      'Scheduled',
      'Yes',
    ]);
    expect(summaryOf()).toEqual([
      'Hearings1',
      'Awaiting a result1',
      'Scheduled1',
      'Held0',
      'Adjourned0',
      'Cancelled0',
    ]);
    expect(screen.getByText('Period: 1 Oct 2026 to 31 Oct 2026')).toBeInTheDocument();
    expect(sent('/api/v1/reports/hearings')[0]?.params.toString()).toBe('');
  });

  it('applies the period and status filters through the address and pages', async () => {
    const user = userEvent.setup();
    const { sent } = backend(EXPORTER, {
      '/api/v1/reports/hearings': (request) =>
        hearings([HEARING], { totalItems: 41, totalPages: 3, page: Number(request.params.get('page') ?? '0') }),
    });
    await renderAt('/reports/hearings?from=2026-10-01&to=2026-10-15&status=HELD');
    await screen.findByRole('table', { name: 'Hearings' });
    expect(Object.fromEntries(sent('/api/v1/reports/hearings')[0]?.params ?? [])).toEqual({
      from: '2026-10-01',
      to: '2026-10-15',
      status: 'HELD',
    });
    expect(screen.getByRole('combobox', { name: 'Hearing status' })).toHaveValue('HELD');
    expect(screen.getByRole('link', { name: 'Download PDF' })).toHaveAttribute(
      'href',
      '/api/v1/reports/hearings/export?from=2026-10-01&to=2026-10-15&status=HELD&format=pdf&lang=en',
    );
    await user.selectOptions(screen.getByRole('combobox', { name: 'Hearing status' }), '');
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => {
      expect(sent('/api/v1/reports/hearings')).toHaveLength(2);
    });
    expect(sent('/api/v1/reports/hearings')[1]?.params.get('status')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => {
      expect(sent('/api/v1/reports/hearings').at(-1)?.params.get('page')).toBe('1');
    });
    await user.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => {
      expect(sent('/api/v1/reports/hearings').at(-1)?.params.toString()).toBe('');
    });
  });

  it('shows an empty period, refusals and failures plainly', async () => {
    backend(VIEWER, { '/api/v1/reports/hearings': () => hearings([]) });
    await renderAt('/reports/hearings');
    expect(await screen.findByText('No hearings match these filters.')).toBeInTheDocument();
  });

  it.each([
    { answer: () => problem(422, { rule: 'DATE_RANGE_INVALID' }), text: 'The start date is after the end date.' },
    { answer: () => jsonResponse({}), text: 'The report could not be loaded.' },
  ])('explains a failed request: $text', async ({ answer, text }) => {
    backend(VIEWER, { '/api/v1/reports/hearings': answer });
    await renderAt('/reports/hearings');
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
  });

  it('shows the not-permitted page on 403', async () => {
    backend(VIEWER, { '/api/v1/reports/hearings': () => problem(403) });
    await renderAt('/reports/hearings');
    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
  });

  it('reads in Arabic with Arabic-Indic digits', async () => {
    backend(VIEWER, {
      '/api/v1/reports/hearings': () =>
        hearings([{ ...HEARING, hearingTime: null, courtCaseNumber: '١٢٣٤', caseReferenceNo: 'م-١', caseTitle: 'قضية' }]),
    });
    await renderAt('/reports/hearings?lang=ar');
    const row = (await rowsOf('الجلسات'))[1];
    expect(row?.[1]).toBe('—');
    expect(row?.[6]).toBe('مرافعة');
    expect(screen.getByRole('main').textContent).not.toMatch(/[0-9]/);
  });
});

describe('workload report (LD-033)', () => {
  it('lists the open work of every assignee and the task totals', async () => {
    backend(EXPORTER, { '/api/v1/reports/workload': () => jsonResponse(WORKLOAD) });
    await renderAt('/reports/workload');
    const rows = await rowsOf('Workload');
    expect(rows.slice(1)).toEqual([
      ['Amal Hassan', '3', '1', '2'],
      ['Unknown user', '1', '0', '1'],
    ]);
    expect(summaryOf()).toEqual([
      'Overdue tasks3',
      'Tasks due within 3 days2',
      'Open4',
      'In progress1',
      'Done7',
      'Cancelled0',
    ]);
    expect(screen.getByRole('link', { name: 'Download CSV' })).toHaveAttribute(
      'href',
      '/api/v1/reports/workload/export?format=csv&lang=en',
    );
  });

  it('says when nobody has open work, and handles failures', async () => {
    backend(VIEWER, { '/api/v1/reports/workload': () => jsonResponse({ ...WORKLOAD, items: [] }) });
    await renderAt('/reports/workload');
    expect(await screen.findByText('Nobody has open tasks.')).toBeInTheDocument();
  });

  it('reports a failure and a refusal', async () => {
    backend(VIEWER, { '/api/v1/reports/workload': () => problem(500) });
    await renderAt('/reports/workload');
    expect(await screen.findByRole('alert')).toHaveTextContent('The report could not be loaded.');
  });

  it('shows the not-permitted page on 403', async () => {
    backend(VIEWER, { '/api/v1/reports/workload': () => problem(403) });
    await renderAt('/reports/workload');
    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
  });
});

describe('experts report (LD-033)', () => {
  it('lists every expert with open referrals and overdue reports', async () => {
    backend(VIEWER, { '/api/v1/reports/experts': () => jsonResponse(EXPERTS) });
    await renderAt('/reports/experts');
    const rows = await rowsOf('Experts');
    expect(rows.slice(1)).toEqual([
      ['Alia Mostafa', 'Accounting', 'Yes', '2', '1'],
      ['Zaki Fahmy', 'Medicine', 'No', '0', '0'],
    ]);
    expect(summaryOf()).toEqual(['Overdue expert reports1', 'Open, report awaited2', 'Report submitted5', 'Cancelled1']);
    expect(screen.queryByRole('link', { name: 'Download CSV' })).not.toBeInTheDocument();
  });

  it('reads in Arabic with Arabic-Indic digits', async () => {
    backend(VIEWER, {
      '/api/v1/reports/experts': () =>
        jsonResponse({ ...EXPERTS, items: [{ ...EXPERTS.items[0], name: 'علياء', specialty: 'محاسبة' }] }),
    });
    await renderAt('/reports/experts?lang=ar');
    const rows = await rowsOf('الخبراء');
    expect(rows[1]).toEqual(['علياء', 'محاسبة', 'نعم', formatNumber(2, 'ar'), formatNumber(1, 'ar')]);
    expect(screen.getByRole('main').textContent).not.toMatch(/[0-9]/);
  });

  it('says when the registry is empty and handles failures', async () => {
    backend(VIEWER, { '/api/v1/reports/experts': () => jsonResponse({ ...EXPERTS, items: [] }) });
    await renderAt('/reports/experts');
    expect(await screen.findByText('The expert registry is empty.')).toBeInTheDocument();
  });

  it('reports a failed request', async () => {
    backend(VIEWER, { '/api/v1/reports/experts': () => jsonResponse({ items: 'none' }) });
    await renderAt('/reports/experts');
    expect(await screen.findByRole('alert')).toHaveTextContent('The report could not be loaded.');
  });
});
