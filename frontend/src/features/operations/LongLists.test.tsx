import { configure, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { jsonResponse } from '../../test/http';
import { CaseProceedings } from '../proceedings/CaseProceedings';
import type { Court } from '../courts/courtsApi';
import type { Judgment } from '../proceedings/proceedingsApi';
import {
  CASE,
  expertRecord,
  hearing,
  page,
  proceeding,
  referral,
  renderOperations,
  scriptedFetch,
  type Request,
} from './testSupport';

/**
 * QAD-044: the selectors and the judicial ladder reach every record beyond the first 100 through the paging contract
 * (page size at most 100, pages in order), in both languages, and never fall silent past the first page.
 */

// 100+ rows re-render on every keystroke; give the asynchronous queries room on a loaded machine.
configure({ asyncUtilTimeout: 8000 });
vi.setConfig({ testTimeout: 30000 });

const PROCEEDINGS = `/api/v1/cases/${CASE}/proceedings`;
const COUNT = 101;

/** The page of `items` the request asks for, shaped like the backend's PageResponse. */
function paged(items: readonly unknown[], request: Request): Response {
  const number = Number(request.query.get('page') ?? '0');
  const size = Number(request.query.get('size') ?? '20');
  return jsonResponse({
    items: items.slice(number * size, (number + 1) * size),
    page: number,
    size,
    totalItems: items.length,
    totalPages: Math.ceil(items.length / size),
  });
}

const many = Array.from({ length: COUNT }, (_, i) =>
  proceeding({ id: `p${String(i + 1)}`, courtCaseNumber: String(i + 1), courtCode: `FI-${String(i + 1)}`, hasJudgment: true }),
);
const judgments: Judgment[] = many.map((p, i) => ({
  id: `j${String(i + 1)}`,
  proceedingId: p.id,
  caseId: CASE,
  judgmentDate: '2025-06-01',
  outcome: 'IN_FAVOUR',
  summary: `Judgment ${String(i + 1)}`,
  final: false,
  version: 0,
  createdAt: '',
  updatedAt: '',
}));

const experts = Array.from({ length: 150 }, (_, i) =>
  expertRecord({ id: `e${String(i + 1)}`, name: `Expert ${String(i + 1).padStart(3, '0')}`, specialty: 'Civil engineering' }),
);
experts[119] = expertRecord({ id: 'e120', name: 'الخبير هدى عادل', specialty: 'الهندسة المدنية' });

const courts: Court[] = Array.from({ length: 102 }, (_, i) => ({
  id: `court-${String(i + 1)}`,
  code: `QA-EDGE-${String(i + 1).padStart(3, '0')}`,
  nameAr: `محكمة رقم ${String(i + 1)}`,
  nameEn: `Court No. ${String(i + 1)}`,
  level: 'FIRST_INSTANCE',
  governorate: null,
  active: true,
  version: 0,
  createdAt: '',
  updatedAt: '',
}));

function serve() {
  return scriptedFetch((r) => {
    if (r.method === 'POST') return jsonResponse(r.path.includes('expert-referrals') ? referral() : hearing(), 201);
    if (r.path === PROCEEDINGS) return paged(many, r);
    if (r.path === `/api/v1/cases/${CASE}/judgments`) return paged(judgments, r);
    if (r.path === '/api/v1/experts') {
      const q = r.query.get('q')?.toLowerCase();
      return paged(q ? experts.filter((e) => e.name.toLowerCase().includes(q)) : experts, r);
    }
    if (r.path === '/api/v1/courts') {
      const q = r.query.get('q')?.toLowerCase();
      return paged(q ? courts.filter((c) => `${c.code} ${c.nameAr} ${c.nameEn}`.toLowerCase().includes(q)) : courts, r);
    }
    if (r.path.startsWith(`/api/v1/cases/${CASE}/`) || r.path === '/api/v1/tasks') return page([]);
    return undefined;
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe.each([
  { lang: 'en', schedule: 'Schedule hearing', option: 'First instance no. 101/2025 (FI-101)' },
  { lang: 'ar', schedule: 'تحديد جلسة', option: undefined },
] as const)('hearing and referral selectors with $lang and more than 100 proceedings', ({ lang, schedule, option }) => {
  it('offers every proceeding, the 101st included, when scheduling a hearing', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderOperations(`/cases/${CASE}?lang=${lang}`);
    const region = await screen.findByRole('region', { name: lang === 'en' ? 'Hearings' : 'الجلسات' });
    await user.click(await within(region).findByRole('button', { name: schedule }));
    const form = within(region).getByRole('form', { name: schedule });
    const select = within(form).getByLabelText(lang === 'en' ? 'Proceeding' : 'الدعوى');
    expect(within(select).getAllByRole('option')).toHaveLength(COUNT + 1);
    await user.selectOptions(select, option ?? 'p101');
    await user.type(within(form).getByLabelText(lang === 'en' ? 'Date' : 'التاريخ'), '2025-03-02');
    await user.selectOptions(within(form).getByLabelText(lang === 'en' ? 'Purpose' : 'الغرض'), 'FIRST_SESSION');
    await user.click(within(form).getByRole('button', { name: lang === 'en' ? 'Schedule' : 'تحديد' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.path).toBe('/api/v1/proceedings/p101/hearings');
    expect(requests.filter((r) => r.path === PROCEEDINGS).map((r) => r.query.get('page'))).toContain('1');
    expect(requests.every((r) => Number(r.query.get('size') ?? '0') <= 100)).toBe(true);
  });
});

describe('expert referral selectors', () => {
  async function openReferral(lang: 'en' | 'ar') {
    const user = userEvent.setup();
    const fetch = serve();
    renderOperations(`/cases/${CASE}?lang=${lang}`);
    const region = await screen.findByRole('region', { name: lang === 'en' ? 'Expert referrals' : 'إحالات الخبراء' });
    await user.click(await within(region).findByRole('button', { name: lang === 'en' ? 'Refer to an expert' : 'إحالة إلى خبير' }));
    const form = within(region).getByRole('form', { name: lang === 'en' ? 'Refer to an expert' : 'إحالة إلى خبير' });
    return { user, form, ...fetch };
  }

  it('refers the 101st proceeding to an expert found by paging, in English', async () => {
    const { user, form, requests } = await openReferral('en');
    const proceedingSelect = within(form).getByLabelText('Proceeding');
    expect(within(proceedingSelect).getAllByRole('option')).toHaveLength(COUNT);
    await user.selectOptions(proceedingSelect, 'p101');

    const expert = await within(form).findByLabelText('Expert');
    await within(form).findByRole('option', { name: 'Expert 001 (Civil engineering)' });
    expect(within(form).getByRole('status')).toHaveTextContent('Showing 100 of 150');
    await user.click(within(form).getByRole('button', { name: 'Load more' }));
    await within(form).findByRole('option', { name: 'Expert 150 (Civil engineering)' });
    await user.selectOptions(expert, 'e150');
    await user.type(within(form).getByLabelText('Referral date'), '2025-02-01');
    await user.click(within(form).getByRole('button', { name: 'Refer' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    const post = requests.find((r) => r.method === 'POST');
    expect(post?.path).toBe('/api/v1/proceedings/p101/expert-referrals');
    expect(post?.body).toMatchObject({ expertId: 'e150' });
  });

  it('searches the registry on the server by an Arabic name', async () => {
    const { user, form, requests } = await openReferral('ar');
    await within(form).findByRole('option', { name: /Expert 001/ });
    await user.type(within(form).getByRole('searchbox'), 'هدى');
    await within(form).findByRole('option', { name: 'الخبير هدى عادل (التخصص: الهندسة المدنية)' });
    expect(requests.some((r) => r.path === '/api/v1/experts' && r.query.get('q') === 'هدى')).toBe(true);
    expect(within(form).queryByRole('option', { name: /Expert 001/ })).not.toBeInTheDocument();
  });

  it('keeps the chosen expert when the search changes', async () => {
    const { user, form } = await openReferral('en');
    const expert = await within(form).findByLabelText('Expert');
    await within(form).findByRole('option', { name: 'Expert 001 (Civil engineering)' });
    await user.selectOptions(expert, 'e1');
    await user.type(within(form).getByRole('searchbox'), 'Expert 14');
    await within(form).findByRole('option', { name: 'Expert 140 (Civil engineering)' });
    expect(expert).toHaveValue('e1');
  });
});

describe('the judicial ladder and the court selector', () => {
  function renderLadder(lang: 'en' | 'ar') {
    return render(
      <MemoryRouter initialEntries={[`/?lang=${lang}`]}>
        <LocaleProvider>
          <CaseProceedings caseId={CASE} />
        </LocaleProvider>
      </MemoryRouter>,
    );
  }

  it.each(['en', 'ar'] as const)('shows all 101 proceedings and their judgments in %s', async (lang) => {
    const { requests } = serve();
    renderLadder(lang);
    await waitFor(() => { expect(screen.getAllByRole('article')).toHaveLength(COUNT); });
    expect(screen.getByText('Judgment 101', { exact: false })).toBeInTheDocument();
    for (const what of ['proceedings', 'judgments', 'special-decisions']) {
      const pages = requests.filter((r) => r.path === `/api/v1/cases/${CASE}/${what}`).map((r) => r.query.get('page'));
      expect(pages[0]).toBe('0');
      if (what !== 'special-decisions') expect(pages).toContain('1');
    }
  });

  it('records a proceeding at the 102nd court, reached by paging', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderLadder('en');
    await waitFor(() => { expect(screen.getAllByRole('article')).toHaveLength(COUNT); });
    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    const form = screen.getByRole('form', { name: 'Record proceeding' });
    await within(form).findByRole('option', { name: 'Court No. 1' });
    expect(within(form).queryByRole('option', { name: 'Court No. 102' })).not.toBeInTheDocument();
    expect(within(form).getByRole('status')).toHaveTextContent('Showing 100 of 102');
    await user.click(within(form).getByRole('button', { name: 'Load more' }));
    await within(form).findByRole('option', { name: 'Court No. 102' });
    await user.selectOptions(within(form).getByLabelText('Court'), 'court-102');
    await user.type(within(form).getByLabelText('Case number at the court'), '5');
    await user.type(within(form).getByLabelText('Year'), '2025');
    await user.type(within(form).getByLabelText('Filing date'), '2025-01-10');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    await waitFor(() => { expect(requests.some((r) => r.method === 'POST')).toBe(true); });
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({ courtId: 'court-102' });
    const reads = requests.filter((r) => r.path === '/api/v1/courts');
    expect(reads.map((r) => r.query.get('page'))).toEqual(['0', '1']);
    expect(reads.every((r) => r.query.get('level') === 'FIRST_INSTANCE' && r.query.get('active') === 'true')).toBe(true);
  });

  it('finds an unloaded court by an Arabic name through the server, with one request for the typed text', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderLadder('ar');
    await waitFor(() => { expect(screen.getAllByRole('article')).toHaveLength(COUNT); });
    await user.click(screen.getByRole('button', { name: 'تسجيل دعوى' }));
    const form = screen.getByRole('form', { name: 'تسجيل دعوى' });
    const select = within(form).getByLabelText('المحكمة');
    await within(select).findByRole('option', { name: 'محكمة رقم 1' });
    expect(within(select).queryByRole('option', { name: 'محكمة رقم 102' })).not.toBeInTheDocument();
    await user.type(within(form).getByRole('searchbox'), 'رقم 102');
    await within(select).findByRole('option', { name: 'محكمة رقم 102' });
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual(['اختر…', 'محكمة رقم 102']);
    const searches = requests.filter((r) => r.path === '/api/v1/courts' && r.query.has('q'));
    expect(searches.map((r) => r.query.get('q'))).toEqual(['رقم 102']);
    await user.selectOptions(select, 'court-102');
    expect(select).toHaveValue('court-102');
  });
});
