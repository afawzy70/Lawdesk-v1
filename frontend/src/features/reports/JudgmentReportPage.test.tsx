import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Permission } from '../../api/permissions';
import { formatNumber } from '../../i18n/format';
import { jsonResponse } from '../../test/http';
import type { JudgmentReport, JudgmentRow } from './judgmentApi';
import { COURTS, backend, problem, renderAt } from './testSupport';

const REPORT = '/api/v1/reports/judgments';
const VIEWER: Permission[] = ['REPORT_VIEW', 'CASE_VIEW', 'COURT_VIEW'];

const AGAINST: JudgmentRow = {
  id: '00000000-0000-4000-8000-000000000301',
  judgmentDate: '2026-10-21',
  outcome: 'AGAINST',
  final: true,
  stage: 'APPEAL',
  court: { id: COURTS[1]?.id ?? '', code: 'CAI-A', nameAr: 'محكمة استئناف القاهرة', nameEn: 'Cairo Court of Appeal' },
  courtCaseNumber: '1234',
  courtCaseYear: 2025,
  caseId: '3f2c8a8e-6b1d-4c3e-9a51-0d4b7e2f1a90',
  caseReferenceNo: 'LD-2026-00001',
  caseTitle: 'Ahmed v. Nile Trading',
};
const PARTIAL: JudgmentRow = {
  ...AGAINST,
  id: '00000000-0000-4000-8000-000000000302',
  judgmentDate: '2026-10-02',
  outcome: 'PARTIAL',
  final: false,
  stage: 'FIRST_INSTANCE',
  caseReferenceNo: null,
  caseTitle: null,
};

function report(items: JudgmentRow[], extra: Partial<JudgmentReport> = {}): Response {
  const body: JudgmentReport = {
    items,
    page: 0,
    size: 20,
    totalItems: 2,
    totalPages: 1,
    from: '2026-10-01',
    to: '2026-10-31',
    summary: {
      total: 2,
      final: 1,
      byOutcome: { IN_FAVOUR: 0, AGAINST: 1, PARTIAL: 1, DISMISSED: 0, OTHER: 0 },
      byStage: { FIRST_INSTANCE: 1, APPEAL: 1, CASSATION: 0 },
    },
    ...extra,
  };
  return jsonResponse(body);
}

const table = () => screen.findByRole('table', { name: 'Judgments' });

describe('judgments report (LD-032)', () => {
  it('lists the judgments of the period with case, court and finality', async () => {
    const { sent } = backend(VIEWER, { [REPORT]: () => report([AGAINST, PARTIAL]) });
    await renderAt('/reports/judgments');
    const rows = within(await table()).getAllByRole('row');
    expect(within(rows[0] as HTMLElement).getAllByRole('columnheader').map((h) => h.textContent)).toEqual([
      'Judgment date',
      'Case',
      'Stage',
      'Court',
      'Number / year',
      'Outcome',
      'Final',
    ]);
    expect(within(rows[1] as HTMLElement).getAllByRole('cell').map((c) => c.textContent)).toEqual([
      '21 Oct 2026',
      'LD-2026-00001Ahmed v. Nile Trading',
      'Appeal',
      'Cairo Court of Appeal',
      '1234/2025',
      'Against the client',
      'Yes',
    ]);
    expect(within(rows[2] as HTMLElement).getAllByRole('cell')[6]).toHaveTextContent('No');
    expect(within(rows[1] as HTMLElement).getByRole('link', { name: 'LD-2026-00001' })).toHaveAttribute(
      'href',
      `/cases/${AGAINST.caseId}?lang=en`,
    );
    expect(screen.getByText('Period: 1 Oct 2026 to 31 Oct 2026')).toBeInTheDocument();
    expect(sent(REPORT)[0]?.params.toString()).toBe('');
    const scroller = screen.getByRole('region', { name: 'Judgments' });
    expect(scroller).toHaveClass('table-scroll');
    expect(scroller).toHaveAttribute('tabindex', '0');
  });

  it('summarises every matching judgment by finality, outcome and stage', async () => {
    backend(VIEWER, { [REPORT]: () => report([AGAINST]) });
    await renderAt('/reports/judgments');
    await table();
    expect(
      within(screen.getByRole('region', { name: 'Summary' }))
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual([
      'Judgments2',
      'Final judgments1',
      'In favour of the client0',
      'Against the client1',
      'Partly in favour1',
      'Dismissed0',
      'Other0',
      'First instance1',
      'Appeal1',
      'Cassation0',
    ]);
  });

  it('reads the filters from the address, sends them, and applies new ones', async () => {
    const user = userEvent.setup();
    const court = COURTS[0]?.id ?? '';
    const { sent } = backend(VIEWER, { [REPORT]: () => report([AGAINST]) });
    const { location } = await renderAt(
      `/reports/judgments?from=2026-01-01&to=2026-06-30&outcome=AGAINST&stage=APPEAL&courtId=${court}&final=true&sort=outcome,desc&page=1`,
    );
    await table();
    expect(Object.fromEntries(sent(REPORT)[0]?.params ?? [])).toEqual({
      from: '2026-01-01',
      to: '2026-06-30',
      outcome: 'AGAINST',
      stage: 'APPEAL',
      courtId: court,
      final: 'true',
      sort: 'outcome,desc',
      page: '1',
    });
    expect(screen.getByLabelText('From')).toHaveValue('2026-01-01');
    expect(screen.getByRole('combobox', { name: 'Outcome' })).toHaveValue('AGAINST');
    expect(screen.getByRole('combobox', { name: 'Finality' })).toHaveValue('true');
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: 'Court' })).toHaveValue(court);
    });

    await user.selectOptions(screen.getByRole('combobox', { name: 'Finality' }), 'false');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Outcome' }), '');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Sort by' }), 'judgmentDate');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Order' }), 'desc');
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => {
      expect(sent(REPORT)).toHaveLength(2);
    });
    expect(Object.fromEntries(sent(REPORT)[1]?.params ?? [])).toEqual({
      from: '2026-01-01',
      to: '2026-06-30',
      stage: 'APPEAL',
      courtId: court,
      final: 'false',
    });
    expect(location()).toContain('/reports/judgments?');

    await user.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => {
      expect(sent(REPORT)).toHaveLength(3);
    });
    expect(sent(REPORT)[2]?.params.toString()).toBe('');
  });

  it('pages through the result', async () => {
    const user = userEvent.setup();
    const { sent } = backend(VIEWER, {
      [REPORT]: (request) =>
        report([AGAINST], { totalItems: 41, totalPages: 3, page: Number(request.params.get('page') ?? '0') }),
    });
    await renderAt('/reports/judgments');
    await table();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => {
      expect(sent(REPORT).at(-1)?.params.get('page')).toBe('1');
    });
  });

  it('shows an empty period plainly', async () => {
    backend(VIEWER, { [REPORT]: () => report([], { totalItems: 0, totalPages: 0 }) });
    await renderAt('/reports/judgments');
    expect(await screen.findByText('No judgments match these filters.')).toBeInTheDocument();
  });

  it.each([
    { answer: () => problem(422, { rule: 'DATE_RANGE_INVALID' }), text: 'The start date is after the end date.' },
    { answer: () => problem(400), text: 'Check the filters: one of the values is not valid.' },
    { answer: () => jsonResponse({}), text: 'The report could not be loaded.' },
  ])('explains a refused or failed request: $text', async ({ answer, text }) => {
    backend(VIEWER, { [REPORT]: answer });
    await renderAt('/reports/judgments');
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
  });

  it('shows the not-permitted page on 403', async () => {
    backend(VIEWER, { [REPORT]: () => problem(403) });
    await renderAt('/reports/judgments');
    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
  });

  it('is reached from the report sections', async () => {
    const user = userEvent.setup();
    backend(VIEWER, { [REPORT]: () => report([AGAINST]), '/api/v1/reports/cases': () => problem(500) });
    await renderAt('/reports');
    const sections = screen.getByRole('navigation', { name: 'Report sections' });
    await user.click(within(sections).getByRole('link', { name: 'Judgments' }));
    expect(await table()).toBeInTheDocument();
    const current = screen.getByRole('navigation', { name: 'Report sections' });
    expect(within(current).getByRole('link', { name: 'Judgments' })).toHaveAttribute('aria-current', 'page');
    expect(within(current).getByRole('link', { name: 'Case inventory' })).not.toHaveAttribute('aria-current');
  });
});

describe('judgments report in Arabic', () => {
  it('labels the report in Arabic with Arabic-Indic digits', async () => {
    backend(VIEWER, { [REPORT]: () => report([{ ...AGAINST, courtCaseNumber: '١٢٣٤', caseReferenceNo: 'م-١' }]) });
    await renderAt('/reports/judgments?lang=ar');
    const judgments = await screen.findByRole('table', { name: 'الأحكام' });
    const cells = within(within(judgments).getAllByRole('row')[1] as HTMLElement).getAllByRole('cell');
    expect(cells[2]).toHaveTextContent('الاستئناف');
    expect(cells[3]).toHaveTextContent('محكمة استئناف القاهرة');
    expect(cells[4]).toHaveTextContent(`١٢٣٤/${formatNumber(2025, 'ar', { useGrouping: false })}`);
    expect(cells[6]).toHaveTextContent('نعم');
    expect(screen.getByRole('main').textContent).not.toMatch(/[0-9]/);
  });
});
