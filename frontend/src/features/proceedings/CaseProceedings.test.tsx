import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { jsonResponse } from '../../test/http';
import { CaseProceedings } from './CaseProceedings';
import { formatDate } from './dates';
import type { Judgment, Proceeding, SpecialDecision } from './proceedingsApi';

/** Judicial stages of a case (LD-019…LD-023, docs/delivery/PHASE-4-ACCEPTANCE.md): QA-0152, -0160, -0168, -0176, -0184. */

const CASE = 'c0000000-0000-4000-8000-000000000001';

const proceeding = (overrides: Partial<Proceeding>): Proceeding => ({
  id: 'p1',
  caseId: CASE,
  stage: 'FIRST_INSTANCE',
  courtId: 'court-fi',
  courtCode: 'FI-1',
  courtNameAr: 'محكمة أول درجة نموذجية',
  courtNameEn: 'Sample First-Instance Court',
  courtCaseNumber: '100',
  courtCaseYear: 2023,
  filingDate: '2023-01-10',
  parentProceedingId: null,
  hasJudgment: false,
  version: 0,
  createdAt: '2023-01-10T08:00:00Z',
  updatedAt: '2023-01-10T08:00:00Z',
  ...overrides,
});

const FIRST = proceeding({ hasJudgment: true });
const APPEAL = proceeding({
  id: 'p2',
  stage: 'APPEAL',
  courtId: 'court-ap',
  courtCode: 'AP-1',
  courtNameAr: 'محكمة استئناف نموذجية',
  courtNameEn: 'Sample Appeal Court',
  courtCaseNumber: '7',
  filingDate: '2023-07-01',
  parentProceedingId: 'p1',
});

const JUDGMENT: Judgment = {
  id: 'j1',
  proceedingId: 'p1',
  caseId: CASE,
  judgmentDate: '2023-06-01',
  outcome: 'AGAINST',
  summary: 'Claim rejected.',
  final: false,
  version: 0,
  createdAt: '2023-06-01T08:00:00Z',
  updatedAt: '2023-06-01T08:00:00Z',
};

const SUSPENSION: SpecialDecision = {
  id: 'd1',
  proceedingId: 'p2',
  caseId: CASE,
  decisionType: 'ENFORCEMENT_SUSPENSION',
  decisionDate: '2023-07-15',
  effect: 'GRANTED',
  summary: null,
  judgmentId: 'j1',
  version: 0,
  createdAt: '2023-07-15T08:00:00Z',
  updatedAt: '2023-07-15T08:00:00Z',
};

const page = <T,>(items: T[]) => ({ items, page: 0, size: 100, totalItems: items.length, totalPages: 1 });

function problem(status: number, body: object = {}): Response {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

interface Data {
  proceedings?: Proceeding[] | Response;
  judgments?: Judgment[] | Response;
  decisions?: SpecialDecision[] | Response;
  courts?: Response;
  onPost?: (path: string, body: unknown) => Response;
}

const respond = <T,>(value: T[] | Response | undefined): Response =>
  value instanceof Response ? value : jsonResponse(page(value ?? []));

function serve(data: Data) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(raw, 'http://localhost');
    const path = url.pathname;
    if (init?.method === 'POST') {
      return Promise.resolve(data.onPost?.(path, JSON.parse(init.body as string)) ?? jsonResponse({}, 201));
    }
    if (path.endsWith('/proceedings')) return Promise.resolve(respond(data.proceedings).clone());
    if (path.endsWith('/judgments')) return Promise.resolve(respond(data.judgments).clone());
    if (path.endsWith('/special-decisions')) return Promise.resolve(respond(data.decisions).clone());
    if (path === '/api/v1/courts') {
      if (data.courts) return Promise.resolve(data.courts.clone());
      const level = url.searchParams.get('level') ?? '';
      return Promise.resolve(
        jsonResponse(
          page([
            {
              id: `court-${level}`,
              code: `C-${level}`,
              nameAr: `محكمة ${level}`,
              nameEn: `Court ${level}`,
              level,
              governorate: null,
              active: true,
              version: 0,
              createdAt: '',
              updatedAt: '',
            },
          ]),
        ),
      );
    }
    return Promise.resolve(problem(404));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function calls(fetchMock: ReturnType<typeof serve>, method = 'GET') {
  return fetchMock.mock.calls
    .filter(([, init]) => (init?.method ?? 'GET') === method)
    .map(([input, init]) => ({ url: typeof input === 'string' ? input : '', body: init?.body }));
}

function renderStages(lang: 'en' | 'ar' = 'en') {
  return render(
    <MemoryRouter initialEntries={[`/?lang=${lang}`]}>
      <LocaleProvider>
        <CaseProceedings caseId={CASE} />
      </LocaleProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('the judicial ladder of a case', () => {
  it('shows each stage with its court, number, filing date, judgment and special decisions', async () => {
    const fetchMock = serve({ proceedings: [FIRST, APPEAL], judgments: [JUDGMENT], decisions: [SUSPENSION] });
    renderStages();

    const region = screen.getByRole('region', { name: 'Judicial stages' });
    const first = await within(region).findByRole('article', { name: 'First instance 100/2023' });
    expect(first).toHaveTextContent('Sample First-Instance Court');
    expect(first).toHaveTextContent(`Filed on ${formatDate('2023-01-10', 'en')}`);
    expect(first).toHaveTextContent(`Judgment of ${formatDate('2023-06-01', 'en')}: Against the client`);
    expect(first).toHaveTextContent('Not final');
    expect(first).toHaveTextContent('Claim rejected.');
    expect(within(first).queryByRole('button', { name: 'Record judgment' })).not.toBeInTheDocument();

    const appeal = within(region).getByRole('article', { name: 'Appeal 7/2023' });
    expect(appeal).toHaveTextContent('Appeal against 100/2023');
    expect(appeal).toHaveTextContent('No judgment yet.');
    expect(appeal).toHaveTextContent(
      `Enforcement suspension: granted, ${formatDate('2023-07-15', 'en')} (judgment of ${formatDate('2023-06-01', 'en')})`,
    );
    expect(within(appeal).getByRole('button', { name: 'Record judgment' })).toBeInTheDocument();

    expect(calls(fetchMock).map((c) => c.url)).toEqual(
      expect.arrayContaining([
        `/api/v1/cases/${CASE}/proceedings?size=100`,
        `/api/v1/cases/${CASE}/judgments?size=100`,
        `/api/v1/cases/${CASE}/special-decisions?size=100`,
      ]),
    );
  });

  it('says when no proceeding is recorded yet', async () => {
    serve({});
    renderStages();
    expect(await screen.findByText('No proceedings recorded yet.')).toBeInTheDocument();
  });

  it('still shows the stages when judgments may not be seen', async () => {
    serve({ proceedings: [FIRST], judgments: problem(403), decisions: problem(403) });
    renderStages();
    const first = await screen.findByRole('article', { name: 'First instance 100/2023' });
    expect(screen.getByText('You do not have permission to view judgments and special decisions.')).toBeInTheDocument();
    expect(first).not.toHaveTextContent('No judgment yet.');
    expect(within(first).queryByRole('button', { name: 'Record judgment' })).not.toBeInTheDocument();
  });

  it.each([
    { status: 403, text: 'You do not have permission to view the judicial stages of this case.' },
    { status: 404, text: 'This case was not found.' },
    { status: 401, text: 'Please sign in to continue.' },
  ])('explains a $status when loading', async ({ status, text }) => {
    serve({ proceedings: problem(status) });
    renderStages();
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
  });

  it('offers a retry when the API cannot be reached', async () => {
    const user = userEvent.setup();
    let failing = true;
    const fetchMock = serve({ proceedings: [FIRST] });
    const real = fetchMock.getMockImplementation();
    fetchMock.mockImplementation((input, init) => {
      if (failing) return Promise.reject(new TypeError('offline'));
      if (!real) throw new Error('no implementation');
      return real(input, init);
    });
    renderStages();
    expect(await screen.findByRole('alert')).toHaveTextContent('The judicial stages could not be loaded.');
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('article', { name: 'First instance 100/2023' })).toBeInTheDocument();
  });
});

describe('recording proceedings', () => {
  it('records a first-instance proceeding before a court of that level', async () => {
    const user = userEvent.setup();
    const fetchMock = serve({ proceedings: [], onPost: () => jsonResponse(FIRST, 201) });
    renderStages();
    await screen.findByText('No proceedings recorded yet.');

    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    const form = screen.getByRole('form', { name: 'Record proceeding' });
    expect(within(form).getByLabelText('Stage')).toHaveValue('FIRST_INSTANCE');
    expect(within(form).queryByLabelText('Proceeding attacked')).not.toBeInTheDocument();
    await within(form).findByRole('option', { name: 'Court FIRST_INSTANCE' });
    await user.selectOptions(within(form).getByLabelText('Court'), 'court-FIRST_INSTANCE');
    await user.type(within(form).getByLabelText('Case number at the court'), '100');
    await user.type(within(form).getByLabelText('Year'), '2023');
    await user.type(within(form).getByLabelText('Filing date'), '2023-01-10');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Saved.')).toHaveAttribute('role', 'status');
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    const post = calls(fetchMock, 'POST')[0];
    expect(post?.url).toBe(`/api/v1/cases/${CASE}/proceedings`);
    expect(JSON.parse(post?.body as string)).toEqual({
      stage: 'FIRST_INSTANCE',
      courtId: 'court-FIRST_INSTANCE',
      courtCaseNumber: '100',
      courtCaseYear: 2023,
      filingDate: '2023-01-10',
      parentProceedingId: null,
    });
    expect(calls(fetchMock).some((c) => c.url === '/api/v1/courts?active=true&level=FIRST_INSTANCE&size=100&sort=code%2Casc')).toBe(true);
  });

  it('records an appeal against a judged first-instance proceeding', async () => {
    const user = userEvent.setup();
    const unjudged = proceeding({ id: 'p3', courtCaseNumber: '300', hasJudgment: false });
    const fetchMock = serve({ proceedings: [FIRST, unjudged], judgments: [JUDGMENT], onPost: () => jsonResponse(APPEAL, 201) });
    renderStages();
    await screen.findByRole('article', { name: 'First instance 100/2023' });

    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    const form = screen.getByRole('form', { name: 'Record proceeding' });
    await user.selectOptions(within(form).getByLabelText('Stage'), 'APPEAL');
    await within(form).findByRole('option', { name: 'Court APPEAL' });
    const parent = within(form).getByLabelText('Proceeding attacked');
    expect(within(parent).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Choose…',
      'First instance 100/2023',
    ]);
    await user.selectOptions(parent, 'p1');
    await user.selectOptions(within(form).getByLabelText('Court'), 'court-APPEAL');
    await user.type(within(form).getByLabelText('Case number at the court'), '7');
    await user.type(within(form).getByLabelText('Year'), '2023');
    await user.type(within(form).getByLabelText('Filing date'), '2023-07-01');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    await screen.findByText('Saved.');
    expect(JSON.parse(calls(fetchMock, 'POST')[0]?.body as string)).toMatchObject({
      stage: 'APPEAL',
      courtId: 'court-APPEAL',
      parentProceedingId: 'p1',
    });
  });

  it('explains that a cassation needs an appeal judgment when none exists', async () => {
    const user = userEvent.setup();
    serve({ proceedings: [FIRST, APPEAL], judgments: [JUDGMENT] });
    renderStages();
    await screen.findByRole('article', { name: 'Appeal 7/2023' });
    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    const form = screen.getByRole('form', { name: 'Record proceeding' });
    await user.selectOptions(within(form).getByLabelText('Stage'), 'CASSATION');
    expect(within(form).getByText('A cassation needs an appeal of this case with a judgment.')).toBeInTheDocument();
  });

  it('shows the business rule that refused the proceeding', async () => {
    const user = userEvent.setup();
    serve({
      proceedings: [FIRST],
      onPost: () => problem(422, { rule: 'COURT_LEVEL_MISMATCH' }),
    });
    renderStages();
    await screen.findByRole('article', { name: 'First instance 100/2023' });
    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    const form = screen.getByRole('form', { name: 'Record proceeding' });
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent(
      'The court must be of the same level as the stage (first instance, appeal or cassation).',
    );
  });

  it.each([
    { response: () => problem(409), text: 'This number and year are already recorded at this court.' },
    { response: () => problem(403), text: 'You do not have permission to record this.' },
    { response: () => problem(422, { rule: 'SOMETHING_NEW' }), text: 'The court rules do not allow this entry.' },
    { response: () => problem(500), text: 'This could not be saved. Please try again.' },
  ])('explains other refusals: $text', async ({ response, text }) => {
    const user = userEvent.setup();
    serve({ proceedings: [FIRST], onPost: response });
    renderStages();
    await screen.findByRole('article', { name: 'First instance 100/2023' });
    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
  });

  it('shows validation errors next to their fields', async () => {
    const user = userEvent.setup();
    serve({
      proceedings: [],
      onPost: () =>
        problem(400, {
          errors: [
            { field: 'courtCaseNumber', code: 'NotBlank' },
            { field: 'courtCaseYear', code: 'Min' },
            { field: 'filingDate', code: 'PastOrPresent' },
            { field: 'courtId', code: 'NotNull' },
          ],
        }),
    });
    renderStages();
    await screen.findByText('No proceedings recorded yet.');
    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    const form = screen.getByRole('form', { name: 'Record proceeding' });
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Please correct the highlighted fields.');
    expect(within(form).getByLabelText('Case number at the court')).toHaveAccessibleDescription('This field is required.');
    expect(within(form).getByLabelText('Year')).toHaveAccessibleDescription('This value is out of range.');
    expect(within(form).getByLabelText('Filing date')).toHaveAccessibleDescription('The date cannot be in the future.');
    expect(within(form).getByLabelText('Court')).toHaveAttribute('aria-invalid', 'true');
  });

  it('explains when the courts cannot be listed', async () => {
    const user = userEvent.setup();
    serve({ proceedings: [], courts: problem(403) });
    renderStages();
    await screen.findByText('No proceedings recorded yet.');
    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    expect(await screen.findByText('The courts could not be listed.')).toBeInTheDocument();
  });

  it('cancelling closes the form', async () => {
    const user = userEvent.setup();
    serve({ proceedings: [] });
    renderStages();
    await screen.findByText('No proceedings recorded yet.');
    await user.click(screen.getByRole('button', { name: 'Record proceeding' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });
});

describe('recording judgments and special decisions', () => {
  it('records the judgment of a proceeding', async () => {
    const user = userEvent.setup();
    const fetchMock = serve({ proceedings: [FIRST, APPEAL], judgments: [JUDGMENT], onPost: () => jsonResponse({}, 201) });
    renderStages();
    const appeal = await screen.findByRole('article', { name: 'Appeal 7/2023' });
    await user.click(within(appeal).getByRole('button', { name: 'Record judgment' }));

    const form = within(appeal).getByRole('form', { name: 'Record judgment for 7/2023' });
    await user.type(within(form).getByLabelText('Judgment date'), '2023-12-01');
    await user.selectOptions(within(form).getByLabelText('Outcome'), 'IN_FAVOUR');
    await user.click(within(form).getByLabelText('Final judgment'));
    await user.type(within(form).getByLabelText('Summary'), 'Appeal accepted.');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    await screen.findByText('Saved.');
    const post = calls(fetchMock, 'POST')[0];
    expect(post?.url).toBe('/api/v1/proceedings/p2/judgment');
    expect(JSON.parse(post?.body as string)).toEqual({
      judgmentDate: '2023-12-01',
      outcome: 'IN_FAVOUR',
      final: true,
      summary: 'Appeal accepted.',
    });
  });

  it('explains a judgment dated before the filing', async () => {
    const user = userEvent.setup();
    serve({ proceedings: [APPEAL], onPost: () => problem(422, { rule: 'JUDGMENT_BEFORE_FILING' }) });
    renderStages();
    const appeal = await screen.findByRole('article', { name: 'Appeal 7/2023' });
    await user.click(within(appeal).getByRole('button', { name: 'Record judgment' }));
    await user.click(within(appeal).getByRole('button', { name: 'Save' }));
    expect(await within(appeal).findByRole('alert')).toHaveTextContent(
      'The date cannot be before the filing date of the proceeding.',
    );
    await user.click(within(appeal).getByRole('button', { name: 'Cancel' }));
    expect(within(appeal).queryByRole('form')).not.toBeInTheDocument();
  });

  it('records an enforcement suspension of a judgment of the case', async () => {
    const user = userEvent.setup();
    const fetchMock = serve({ proceedings: [FIRST, APPEAL], judgments: [JUDGMENT], onPost: () => jsonResponse({}, 201) });
    renderStages();
    const appeal = await screen.findByRole('article', { name: 'Appeal 7/2023' });
    await user.click(within(appeal).getByRole('button', { name: 'Record special decision' }));

    const form = within(appeal).getByRole('form', { name: 'Record special decision for 7/2023' });
    expect(within(form).getByLabelText('Decision type')).toHaveValue('ENFORCEMENT_SUSPENSION');
    await user.type(within(form).getByLabelText('Decision date'), '2023-07-15');
    await user.selectOptions(within(form).getByLabelText('Effect'), 'GRANTED');
    const judgment = within(form).getByLabelText('Judgment concerned');
    expect(within(judgment).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'None',
      `Judgment of ${formatDate('2023-06-01', 'en')} in 100/2023`,
    ]);
    await user.selectOptions(judgment, 'j1');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      expect(calls(fetchMock, 'POST')).toHaveLength(1);
    });
    const post = calls(fetchMock, 'POST')[0];
    expect(post?.url).toBe('/api/v1/proceedings/p2/special-decisions');
    expect(JSON.parse(post?.body as string)).toEqual({
      decisionType: 'ENFORCEMENT_SUSPENSION',
      decisionDate: '2023-07-15',
      effect: 'GRANTED',
      judgmentId: 'j1',
      summary: null,
    });
    expect(await screen.findByText('Saved.')).toBeInTheDocument();
  });

  it('explains a suspension without a judgment', async () => {
    const user = userEvent.setup();
    serve({ proceedings: [APPEAL], onPost: () => problem(422, { rule: 'SUSPENSION_REQUIRES_JUDGMENT' }) });
    renderStages();
    const appeal = await screen.findByRole('article', { name: 'Appeal 7/2023' });
    await user.click(within(appeal).getByRole('button', { name: 'Record special decision' }));
    await user.click(within(appeal).getByRole('button', { name: 'Save' }));
    expect(await within(appeal).findByRole('alert')).toHaveTextContent(
      'An enforcement suspension must name the judgment whose enforcement it suspends.',
    );
  });
});

describe('judicial stages in Arabic', () => {
  it('labels the stages, outcomes and decisions in Arabic', async () => {
    serve({ proceedings: [FIRST, APPEAL], judgments: [JUDGMENT], decisions: [SUSPENSION] });
    renderStages('ar');
    const region = screen.getByRole('region', { name: 'درجات التقاضي' });
    const first = await within(region).findByRole('article', { name: 'أول درجة رقم 100/2023' });
    expect(first).toHaveTextContent('محكمة أول درجة نموذجية');
    expect(first).toHaveTextContent('ضد الموكل');
    const appeal = within(region).getByRole('article', { name: 'الاستئناف رقم 7/2023' });
    expect(appeal).toHaveTextContent('وقف التنفيذ');
    expect(appeal).toHaveTextContent('مقبول');
    expect(within(appeal).getByRole('button', { name: 'تسجيل الحكم' })).toBeInTheDocument();
  });
});
