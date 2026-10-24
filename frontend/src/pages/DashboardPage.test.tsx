import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../App';
import type { Me } from '../features/auth/me';
import type { DashboardReport } from '../features/reports/dashboardApi';
import { formatNumber } from '../i18n/format';
import { ADMIN, fakeApi } from '../test/api';
import { jsonResponse } from '../test/http';

const DASHBOARD = 'GET /api/v1/reports/dashboard';

const REPORTER: Me = { ...ADMIN, permissions: ['CASE_VIEW', 'REPORT_VIEW'] };
const NO_REPORTS: Me = { ...ADMIN, permissions: ADMIN.permissions.filter((p) => p !== 'REPORT_VIEW') };

const FIGURES: DashboardReport = {
  month: '2026-10',
  cases: {
    total: 1234,
    open: 1200,
    closed: 34,
    newThisMonth: 17,
    byStatus: { OPEN: 700, ACTIVE: 450, SUSPENDED: 50, CLOSED: 30, ARCHIVED: 4 },
    byType: { CIVIL: 600, CRIMINAL: 0, COMMERCIAL: 400, LABOR: 100, FAMILY: 120, ADMINISTRATIVE: 14 },
  },
  proceedings: {
    awaitingJudgment: 61,
    byStage: [
      { stage: 'FIRST_INSTANCE', total: 90, awaitingJudgment: 40 },
      { stage: 'APPEAL', total: 25, awaitingJudgment: 15 },
      { stage: 'CASSATION', total: 6, awaitingJudgment: 6 },
    ],
  },
  judgments: {
    from: '2026-10-01',
    to: '2026-10-31',
    recorded: 12,
    final: 5,
    byOutcome: { IN_FAVOUR: 6, AGAINST: 3, PARTIAL: 2, DISMISSED: 1, OTHER: 0 },
  },
  operations: {
    hearingsThisWeek: 5,
    hearingsAwaitingResult: 2,
    expertReportsOverdue: 3,
    overdueTasks: 4,
    tasksDueSoon: 6,
  },
  pending: [],
};

function problem(status: number): Response {
  return new Response(JSON.stringify({ type: 'about:blank', status }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

async function renderDashboard(lang: 'en' | 'ar' = 'en') {
  const result = render(
    <MemoryRouter initialEntries={[`/?lang=${lang}`]}>
      <AppRoutes />
    </MemoryRouter>,
  );
  await screen.findByRole('banner');
  await waitFor(() => {
    expect(document.title).not.toBe('');
  });
  return result;
}

function stub(me: Me, dashboard: () => Response = () => jsonResponse(FIGURES)) {
  const fetchMock = fakeApi({ me, routes: { [DASHBOARD]: dashboard } });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function pathOf(input: RequestInfo | URL): string {
  return typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url;
}

function requested(fetchMock: ReturnType<typeof fakeApi>, path: string): number {
  return fetchMock.mock.calls.filter(([input]) => pathOf(input).startsWith(path)).length;
}

const region = (name: string) => screen.getByRole('region', { name });
const items = (name: string) => within(region(name)).getAllByRole('listitem').map((item) => item.textContent);

describe('dashboard with real figures (LD-030)', () => {
  beforeEach(() => {
    stub(REPORTER);
  });

  it('shows the case figures from the reports API, with no sample data left', async () => {
    await renderDashboard();
    expect(await screen.findByRole('region', { name: 'Cases' })).toBeInTheDocument();
    expect(items('Cases')).toEqual(['1,200Open cases', '34Closed cases', '1,234All cases', '17Opened this month']);
    expect(screen.queryByText('Sample data')).not.toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent(
      'Figures are computed from the records stored in LawDesk when the page opens.',
    );
  });

  it('breaks the cases down by status and by type, zeros included', async () => {
    await renderDashboard();
    await screen.findByRole('region', { name: 'Cases by status' });
    expect(items('Cases by status')).toEqual(['Open700', 'Active450', 'Suspended50', 'Closed30', 'Archived4']);
    expect(items('Cases by type')).toEqual([
      'Civil600',
      'Criminal0',
      'Commercial400',
      'Labour100',
      'Family (personal status)120',
      'Administrative14',
    ]);
  });

  it('shows proceedings by stage and those awaiting a judgment', async () => {
    await renderDashboard();
    await screen.findByRole('region', { name: 'Proceedings by stage' });
    expect(items('Proceedings by stage')).toEqual([
      'Awaiting judgment61',
      'First instance90 proceedings, 40 awaiting judgment',
      'Appeal25 proceedings, 15 awaiting judgment',
      'Cassation6 proceedings, 6 awaiting judgment',
    ]);
  });

  it('shows the judgments of the period with its dates and outcomes', async () => {
    await renderDashboard();
    const judgments = await screen.findByRole('region', { name: 'Judgments from 1 Oct 2026 to 31 Oct 2026' });
    expect(within(judgments).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      '12Judgments recorded',
      '5Final judgments',
      'In favour of the client6',
      'Against the client3',
      'Partly in favour2',
      'Dismissed1',
      'Other0',
    ]);
  });

  it('shows the legal-operations figures from the hearing, expert and task records', async () => {
    await renderDashboard();
    await screen.findByRole('region', { name: 'Legal operations' });
    expect(items('Legal operations')).toEqual([
      '5Hearings in the next seven days',
      '2Hearings awaiting a result',
      '3Expert reports overdue',
      '4Overdue tasks',
      '6Tasks due soon',
    ]);
    expect(screen.queryByText('Not available yet')).not.toBeInTheDocument();
  });
});

describe('dashboard in Arabic', () => {
  it('labels the figures in Arabic and writes them with Arabic-Indic digits', async () => {
    stub(REPORTER);
    await renderDashboard('ar');
    await screen.findByRole('region', { name: 'القضايا' });
    expect(items('القضايا')[0]).toBe(`${formatNumber(1200, 'ar')}القضايا المفتوحة`);
    expect(items('القضايا حسب الحالة')[0]).toBe(`مفتوحة${formatNumber(700, 'ar')}`);
    expect(items('العمليات القانونية')[0]).toBe(`${formatNumber(5, 'ar')}الجلسات خلال الأيام السبعة القادمة`);
    expect(screen.getByRole('region', { name: /^الأحكام من/ }).textContent).not.toMatch(/[0-9]/);
    expect(screen.getByRole('main').textContent).not.toMatch(/[0-9]/);
  });
});

describe('dashboard without the figures', () => {
  it('shows the page without statistics, and asks nothing, when the user lacks REPORT_VIEW', async () => {
    const fetchMock = stub(NO_REPORTS);
    await renderDashboard();
    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByText('Statistics are shown to users with the report permission.')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Cases' })).not.toBeInTheDocument();
    expect(requested(fetchMock, '/api/v1/reports')).toBe(0);
  });

  it('treats a 403 from the API like a missing permission, without an error page', async () => {
    stub(REPORTER, () => problem(403));
    await renderDashboard();
    expect(await screen.findByText('Statistics are shown to users with the report permission.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('reports a failure and loads again on retry', async () => {
    let calls = 0;
    const fetchMock = stub(REPORTER, () => {
      calls += 1;
      return calls === 1 ? problem(500) : jsonResponse(FIGURES);
    });
    await renderDashboard();
    expect(await screen.findByRole('alert')).toHaveTextContent('The figures could not be loaded.');
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('region', { name: 'Cases' })).toBeInTheDocument();
    expect(requested(fetchMock, '/api/v1/reports/dashboard')).toBe(2);
  });

  it('says it is loading while the figures are on their way', async () => {
    let answer: (response: Response) => void = () => undefined;
    const api = fakeApi({ me: REPORTER });
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
        pathOf(input) === '/api/v1/reports/dashboard'
          ? new Promise<Response>((resolve) => {
              answer = resolve;
            })
          : api(input, init),
      ),
    );
    await renderDashboard();
    expect(within(screen.getByRole('main')).getByRole('status')).toHaveTextContent('Loading figures…');
    answer(jsonResponse(FIGURES));
    expect(await screen.findByRole('region', { name: 'Cases' })).toBeInTheDocument();
    expect(within(screen.getByRole('main')).queryByRole('status')).not.toBeInTheDocument();
  });

  it('rejects an answer without figures as a failure', async () => {
    stub(REPORTER, () => jsonResponse({}));
    await renderDashboard();
    expect(await screen.findByRole('alert')).toHaveTextContent('The figures could not be loaded.');
  });
});
