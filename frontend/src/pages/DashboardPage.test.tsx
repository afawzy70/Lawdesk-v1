import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../App';
import { formatDateTime, formatNumber } from '../i18n/format';
import { PREVIEW_CASES_BY_STAGE, PREVIEW_HEARINGS, PREVIEW_KPIS } from '../preview/mockData';
import { jsonResponse } from '../test/http';

function renderDashboard(lang: 'en' | 'ar' = 'en') {
  return render(
    <MemoryRouter initialEntries={[`/?lang=${lang}`]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

const kpi = (id: string) => PREVIEW_KPIS.find((k) => k.id === id)?.value ?? -1;

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'UP' })));
});

describe('dashboard preview (English)', () => {
  it('states in its introduction that every figure is invented sample data', () => {
    renderDashboard();
    expect(screen.getByRole('main')).toHaveTextContent(
      'This preview shows a possible dashboard layout. Every figure and record on this page is invented sample data.',
    );
  });

  it('labels each mock data region as sample data, visibly and in its accessible description', () => {
    renderDashboard();
    for (const name of ['Key figures', 'Upcoming hearings', 'Cases by stage']) {
      const region = screen.getByRole('region', { name });
      expect(region).toHaveAccessibleDescription('Sample data');
      expect(within(region).getByText('Sample data')).toBeVisible();
    }
  });

  it('shows the four KPI tiles from the mock data module', () => {
    renderDashboard();
    const tiles = within(screen.getByRole('region', { name: 'Key figures' })).getAllByRole('listitem');
    expect(tiles.map((t) => t.textContent)).toEqual([
      `${String(kpi('openCases'))}Open cases`,
      `${String(kpi('hearingsThisWeek'))}Hearings this week`,
      `${String(kpi('pendingJudgments'))}Pending judgments`,
      `${String(kpi('expertReportsDue'))}Expert reports due`,
    ]);
  });

  it('lists upcoming hearings in a captioned table marked as sample data', () => {
    renderDashboard();
    const table = screen.getByRole('table', { name: /Upcoming hearings/ });
    expect(table).toHaveAccessibleName('Upcoming hearings (Sample data)');
    const headers = within(table).getAllByRole('columnheader').map((h) => h.textContent);
    expect(headers).toEqual(['Case', 'Court', 'Stage', 'Date and time']);

    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(PREVIEW_HEARINGS.length);
    const first = PREVIEW_HEARINGS[0];
    if (!first) throw new Error('mock data must contain hearings');
    const cells = within(rows[0] as HTMLElement).getAllByRole('cell').map((c) => c.textContent);
    expect(cells[0]).toBe(`Sample Case ${formatNumber(first.sampleCaseNumber, 'en', { minimumIntegerDigits: 3 })}`);
    expect(cells[1]).toBe(first.court.en);
    expect(cells[3]).toBe(formatDateTime(first.startsAt, 'en'));
    expect(within(rows[0] as HTMLElement).getByText(formatDateTime(first.startsAt, 'en')).tagName).toBe('TIME');
  });

  it('breaks down cases by stage with text values and decorative bars', () => {
    renderDashboard();
    const region = screen.getByRole('region', { name: 'Cases by stage' });
    const items = within(region).getAllByRole('listitem');
    expect(items).toHaveLength(3);
    const total = PREVIEW_CASES_BY_STAGE.reduce((sum, s) => sum + s.count, 0);
    const first = PREVIEW_CASES_BY_STAGE[0];
    if (!first) throw new Error('mock data must contain stages');
    expect(items[0]).toHaveTextContent('First instance');
    expect(items[0]).toHaveTextContent(`${String(first.count)} cases (${String(Math.round((first.count / total) * 100))}%)`);
    expect(items[1]).toHaveTextContent('Appeal');
    expect(items[2]).toHaveTextContent('Cassation');

    const bar = (items[0] as HTMLElement).querySelector('.stage-bar-fill');
    expect(bar).toHaveAttribute('aria-hidden', 'true');
    expect((bar as HTMLElement).style.inlineSize).toBe(`${String((first.count / total) * 100)}%`);
  });
});

describe('dashboard preview (Arabic)', () => {
  it('labels regions and the table in Arabic, as sample data', () => {
    renderDashboard('ar');
    for (const name of ['مؤشرات رئيسية', 'الجلسات القادمة', 'القضايا حسب درجة التقاضي']) {
      expect(screen.getByRole('region', { name })).toHaveAccessibleDescription('بيانات نموذجية');
    }
    expect(screen.getByRole('table')).toHaveAccessibleName('الجلسات القادمة (بيانات نموذجية)');
  });

  it('formats figures, sample references and dates with Arabic-Indic digits', () => {
    renderDashboard('ar');
    const tiles = within(screen.getByRole('region', { name: 'مؤشرات رئيسية' })).getAllByRole('listitem');
    expect(tiles[0]).toHaveTextContent(formatNumber(kpi('openCases'), 'ar'));
    expect(tiles[0]).toHaveTextContent('القضايا المفتوحة');

    const first = PREVIEW_HEARINGS[0];
    if (!first) throw new Error('mock data must contain hearings');
    const row = within(screen.getByRole('table')).getAllByRole('row')[1] as HTMLElement;
    expect(row).toHaveTextContent(`قضية نموذجية ${formatNumber(first.sampleCaseNumber, 'ar', { minimumIntegerDigits: 3 })}`);
    expect(row).toHaveTextContent(first.court.ar);
    expect(row).toHaveTextContent(formatDateTime(first.startsAt, 'ar'));
    expect(screen.getByRole('table').textContent).not.toMatch(/[0-9]/);
  });
});

describe('hearings table scroll region (QAD-015)', () => {
  it.each([
    { lang: 'en' as const, name: 'Upcoming hearings (Sample data)' },
    { lang: 'ar' as const, name: 'الجلسات القادمة (بيانات نموذجية)' },
  ])('is a keyboard-focusable, named region ($lang)', ({ lang, name }) => {
    const { container } = renderDashboard(lang);
    const scroller = container.querySelector('.table-scroll');
    expect(scroller).not.toBeNull();
    expect(scroller).toHaveAttribute('tabindex', '0');
    expect(screen.getByRole('region', { name })).toBe(scroller);
    expect(within(scroller as HTMLElement).getByRole('table', { name })).toBeInTheDocument();
  });
});
