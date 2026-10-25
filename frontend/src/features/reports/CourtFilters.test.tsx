import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Permission } from '../../api/permissions';
import { jsonResponse } from '../../test/http';
import { backend, problem, renderAt, type SentRequest } from './testSupport';

/**
 * QAD-044: the court filters of the case-inventory and judgment reports reach every court (102 here), through the
 * same paged selector as the judicial forms, with the keyboard, in both languages. Filters, permission and the
 * report calls are unchanged.
 */
vi.setConfig({ testTimeout: 30_000 });

const VIEWER: Permission[] = ['REPORT_VIEW', 'CASE_VIEW', 'COURT_VIEW'];
const COURTS_PATH = '/api/v1/courts';

const courts = Array.from({ length: 102 }, (_, i) => ({
  id: `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
  code: `QA-EDGE-${String(i + 1).padStart(3, '0')}`,
  nameAr: `محكمة رقم ${String(i + 1)}`,
  nameEn: `Court No. ${String(i + 1)}`,
  level: 'FIRST_INSTANCE',
  governorate: null,
  active: i % 2 === 0,
  version: 0,
  createdAt: '',
  updatedAt: '',
}));
const LAST = courts[101]?.id ?? '';

function courtPage(request: SentRequest): Response {
  const number = Number(request.params.get('page') ?? '0');
  const size = Number(request.params.get('size') ?? '20');
  return jsonResponse({
    items: courts.slice(number * size, (number + 1) * size),
    page: number,
    size,
    totalItems: courts.length,
    totalPages: Math.ceil(courts.length / size),
  });
}

const emptyReport = (extra: object) =>
  jsonResponse({
    items: [],
    page: 0,
    size: 20,
    totalItems: 0,
    totalPages: 0,
    summary: { total: 0, open: 0, closed: 0, byStatus: {}, byType: {}, byStage: {}, final: 0, byOutcome: {} },
    from: '2026-10-01',
    to: '2026-10-31',
    ...extra,
  });

const REPORTS = [
  { name: 'case inventory', path: '/reports', api: '/api/v1/reports/cases' },
  { name: 'judgment report', path: '/reports/judgments', api: '/api/v1/reports/judgments' },
] as const;

const COPY = {
  en: { court: 'Court', search: 'Search the courts', more: 'Load more', apply: 'Apply', none: 'Court No. 102', first: 'Court No. 1' },
  ar: { court: 'المحكمة', search: 'ابحث في المحاكم', more: 'تحميل المزيد', apply: 'تطبيق', none: 'محكمة رقم 102', first: 'محكمة رقم 1' },
} as const;

describe.each(REPORTS)('court filter of the $name', ({ path, api }) => {
  const serve = (permissions: Permission[] = VIEWER) =>
    backend(permissions, {
      [api]: () => emptyReport({}),
      [COURTS_PATH]: courtPage,
      [`${COURTS_PATH}/${LAST}`]: () => jsonResponse(courts[101]),
    });

  it.each(['en', 'ar'] as const)('selects the 102nd court by keyboard and loads the report for it (%s)', async (lang) => {
    const c = COPY[lang];
    const user = userEvent.setup();
    const { sent } = serve();
    const { location } = await renderAt(`${path}?lang=${lang}`);
    const select = await screen.findByRole('combobox', { name: c.court });
    await within(select).findByRole('option', { name: c.first });
    expect(within(select).queryByRole('option', { name: c.none })).not.toBeInTheDocument();
    expect(screen.getByText(lang === 'en' ? 'Showing 100 of 102' : 'عرض ١٠٠ من ١٠٢')).toBeInTheDocument();

    await user.click(screen.getByRole('searchbox', { name: c.search }));
    await user.tab();
    expect(select).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: c.more })).toHaveFocus();
    await user.keyboard('{Enter}');
    await within(select).findByRole('option', { name: c.none });
    await user.selectOptions(select, LAST);
    await user.click(screen.getByRole('button', { name: c.apply }));

    await waitFor(() => {
      expect(sent(api).at(-1)?.params.get('courtId')).toBe(LAST);
    });
    expect(location()).toContain(`courtId=${LAST}`);
    // Applying re-creates the form (as for every filter), which reads the first page again.
    expect(sent(COURTS_PATH).map((r) => r.params.get('page'))).toEqual(['0', '1', '0']);
    expect(sent(COURTS_PATH).every((r) => Number(r.params.get('size')) <= 100)).toBe(true);
  });

  it('shows a court beyond the first page as selected when the address carries it, and keeps it on Apply', async () => {
    const user = userEvent.setup();
    const { sent } = serve();
    await renderAt(`${path}?courtId=${LAST}`);
    const select = await screen.findByRole('combobox', { name: 'Court' });
    await waitFor(() => {
      expect(select).toHaveValue(LAST);
    });
    expect(within(select).getByRole('option', { name: 'Court No. 102' })).toBeInTheDocument();
    expect(sent(api)[0]?.params.get('courtId')).toBe(LAST);
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => {
      expect(sent(api).every((r) => r.params.get('courtId') === LAST)).toBe(true);
    });
    expect(select).toHaveValue(LAST);
  });

  it('finds an Arabic court name among the loaded courts', async () => {
    const user = userEvent.setup();
    serve();
    await renderAt(`${path}?lang=ar`);
    const select = await screen.findByRole('combobox', { name: 'المحكمة' });
    await within(select).findByRole('option', { name: 'محكمة رقم 1' });
    await user.type(screen.getByRole('searchbox', { name: 'ابحث في المحاكم' }), 'رقم 77');
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual(['الكل', 'محكمة رقم 77']);
  });

  it('offers no court filter without COURT_VIEW and keeps the report usable when the courts fail', async () => {
    const { sent } = serve(['REPORT_VIEW', 'CASE_VIEW']);
    await renderAt(path);
    await waitFor(() => {
      expect(sent(api).length).toBeGreaterThan(0);
    });
    expect(screen.queryByRole('combobox', { name: 'Court' })).not.toBeInTheDocument();
    expect(sent(COURTS_PATH)).toHaveLength(0);
  });

  it('says so, and still lets the report be filtered, when the courts cannot be read', async () => {
    backend(VIEWER, { [api]: () => emptyReport({}), [COURTS_PATH]: () => problem(500) });
    await renderAt(path);
    expect(await screen.findByText('The list could not be read. Try again.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Apply' })).toBeEnabled();
  });
});
