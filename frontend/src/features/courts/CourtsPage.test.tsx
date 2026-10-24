import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from '../../App';
import { jsonResponse } from '../../test/http';
import type { Court, CourtPage } from './courtsApi';

/** LD-018 court administration screen (docs/delivery/PHASE-4-ACCEPTANCE.md, QA-0144). */

const court = (overrides: Partial<Court> = {}): Court => ({
  id: '11111111-1111-4111-8111-111111111111',
  code: 'GEN-FI-1',
  nameAr: 'محكمة ابتدائية نموذجية',
  nameEn: 'Generic First-Instance Court',
  level: 'FIRST_INSTANCE',
  governorate: 'Cairo',
  active: true,
  version: 0,
  createdAt: '2026-10-24T08:00:00Z',
  updatedAt: '2026-10-24T08:00:00Z',
  ...overrides,
});

const page = (items: Court[], extra: Partial<CourtPage> = {}): CourtPage => ({
  items,
  page: 0,
  size: 20,
  totalItems: items.length,
  totalPages: items.length === 0 ? 0 : 1,
  ...extra,
});

function problem(status: number, body: object = {}): Response {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function urlOf(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input;
  return input instanceof URL ? input.href : input.url;
}

type Handler = (url: URL, init: RequestInit) => Response | Promise<Response>;

/** Routes fetch calls to `handler`; the shell's health check always answers UP. */
function serve(handler: Handler) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(urlOf(input), 'http://localhost');
    if (url.pathname === '/actuator/health') return Promise.resolve(jsonResponse({ status: 'UP' }));
    return Promise.resolve(handler(url, init ?? {}));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function courtCalls(fetchMock: ReturnType<typeof serve>) {
  return fetchMock.mock.calls
    .map(([input, init]) => ({ url: urlOf(input), init: init ?? {} }))
    .filter((call) => call.url.startsWith('/api/v1/courts'));
}

function renderCourts(lang: 'en' | 'ar' = 'en') {
  return render(
    <MemoryRouter initialEntries={[`/courts?lang=${lang}`]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('court list', () => {
  it('lists courts with code, names, level, governorate and status', async () => {
    serve(() => jsonResponse(page([court(), court({ id: '2', code: 'GEN-AP-1', level: 'APPEAL', active: false, governorate: null })])));
    renderCourts();

    expect(screen.getByRole('heading', { level: 1, name: 'Courts' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Courts' });
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('GEN-FI-1');
    expect(rows[0]).toHaveTextContent('Generic First-Instance Court');
    expect(rows[0]).toHaveTextContent('محكمة ابتدائية نموذجية');
    expect(rows[0]).toHaveTextContent('First instance');
    expect(rows[0]).toHaveTextContent('Cairo');
    expect(rows[0]).toHaveTextContent('Active');
    expect(rows[1]).toHaveTextContent('Appeal');
    expect(rows[1]).toHaveTextContent('Inactive');
    expect(document.title).toBe('Courts — LawDesk (Preview)');
  });

  it('asks the API for the first page ordered by code', async () => {
    const fetchMock = serve(() => jsonResponse(page([])));
    renderCourts();
    await screen.findByText('No courts match these filters.');
    const [first] = courtCalls(fetchMock);
    expect(first?.url).toBe('/api/v1/courts?page=0&size=20&sort=code%2Casc');
    expect(first?.init.credentials).toBe('same-origin');
  });

  it('filters by level and status', async () => {
    const user = userEvent.setup();
    const fetchMock = serve(() => jsonResponse(page([court()])));
    renderCourts();
    await screen.findByRole('table', { name: 'Courts' });

    await user.selectOptions(screen.getByLabelText('Level'), 'CASSATION');
    await user.selectOptions(screen.getByLabelText('Status'), 'false');

    await waitFor(() => {
      expect(courtCalls(fetchMock).at(-1)?.url).toBe(
        '/api/v1/courts?page=0&size=20&sort=code%2Casc&level=CASSATION&active=false',
      );
    });
  });

  it('pages through long lists', async () => {
    const user = userEvent.setup();
    const fetchMock = serve((url) =>
      jsonResponse(page([court({ code: `P-${url.searchParams.get('page') ?? ''}` })], {
        page: Number(url.searchParams.get('page')),
        totalItems: 30,
        totalPages: 2,
      })),
    );
    renderCourts();
    await screen.findByText('P-0');
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Next page' }));
    await screen.findByText('P-1');
    expect(courtCalls(fetchMock).at(-1)?.url).toContain('page=1');
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Previous page' }));
    await screen.findByText('P-0');
  });

  it('says so when the user may not see courts', async () => {
    serve(() => problem(403));
    renderCourts();
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to view courts.');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('asks the user to sign in when there is no session', async () => {
    serve(() => problem(401));
    renderCourts();
    expect(await screen.findByRole('alert')).toHaveTextContent('Please sign in to continue.');
  });

  it('offers a retry when the API cannot be reached', async () => {
    const user = userEvent.setup();
    let calls = 0;
    serve(() => {
      calls += 1;
      if (calls === 1) throw new TypeError('network down');
      return jsonResponse(page([court()]));
    });
    renderCourts();
    expect(await screen.findByRole('alert')).toHaveTextContent('The courts could not be loaded.');
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('table', { name: 'Courts' })).toBeInTheDocument();
  });
});

describe('creating and editing courts', () => {
  it('creates a court and shows it in the list', async () => {
    const user = userEvent.setup();
    const created = court({ id: '3', code: 'NEW-1', nameEn: 'New Court', nameAr: 'محكمة جديدة', level: 'APPEAL' });
    let stored: Court[] = [];
    const fetchMock = serve((_url, init) => {
      if (init.method === 'POST') {
        stored = [created];
        return jsonResponse(created, 201);
      }
      return jsonResponse(page(stored));
    });
    renderCourts();
    await screen.findByText('No courts match these filters.');

    await user.click(screen.getByRole('button', { name: 'New court' }));
    const form = screen.getByRole('form', { name: 'New court' });
    await user.type(within(form).getByLabelText('Code'), 'new-1');
    await user.type(within(form).getByLabelText('Arabic name'), 'محكمة جديدة');
    await user.type(within(form).getByLabelText('English name'), 'New Court');
    await user.selectOptions(within(form).getByLabelText('Level'), 'APPEAL');
    expect(within(form).queryByLabelText('Active')).not.toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Court saved.')).toHaveAttribute('role', 'status');
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(await screen.findByText('NEW-1')).toBeInTheDocument();

    const post = courtCalls(fetchMock).find((call) => call.init.method === 'POST');
    expect(post?.url).toBe('/api/v1/courts');
    expect(JSON.parse(post?.init.body as string)).toEqual({
      code: 'new-1',
      nameAr: 'محكمة جديدة',
      nameEn: 'New Court',
      level: 'APPEAL',
      governorate: null,
    });
    expect(new Headers(post?.init.headers).get('X-XSRF-TOKEN')).toBe('test-token');
  });

  it('edits a court, sending the version it read, and can deactivate it', async () => {
    const user = userEvent.setup();
    const original = court({ version: 4 });
    const fetchMock = serve((_url, init) => {
      if (init.method === 'PUT') return jsonResponse({ ...original, nameEn: 'Renamed', active: false, version: 5 });
      return jsonResponse(page([original]));
    });
    renderCourts();
    await user.click(await screen.findByRole('button', { name: 'Edit GEN-FI-1' }));

    const form = screen.getByRole('form', { name: 'Edit court GEN-FI-1' });
    expect(within(form).getByLabelText('Code')).toHaveValue('GEN-FI-1');
    expect(within(form).getByLabelText('Governorate')).toHaveValue('Cairo');
    const name = within(form).getByLabelText('English name');
    await user.clear(name);
    await user.type(name, 'Renamed');
    await user.click(within(form).getByLabelText('Active'));
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Court saved.')).toHaveAttribute('role', 'status');
    const putCall = courtCalls(fetchMock).find((call) => call.init.method === 'PUT');
    expect(putCall?.url).toBe(`/api/v1/courts/${original.id}`);
    expect(JSON.parse(putCall?.init.body as string)).toMatchObject({
      nameEn: 'Renamed',
      active: false,
      version: 4,
      governorate: 'Cairo',
    });
  });

  it('cancelling closes the form without a request', async () => {
    const user = userEvent.setup();
    const fetchMock = serve(() => jsonResponse(page([])));
    renderCourts();
    await screen.findByText('No courts match these filters.');
    await user.click(screen.getByRole('button', { name: 'New court' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(courtCalls(fetchMock).every((call) => (call.init.method ?? 'GET') === 'GET')).toBe(true);
  });

  it('shows validation errors next to their fields', async () => {
    const user = userEvent.setup();
    serve((_url, init) =>
      init.method === 'POST'
        ? problem(400, {
            errors: [
              { field: 'code', code: 'Pattern' },
              { field: 'nameAr', code: 'NotBlank' },
              { field: 'nameEn', code: 'Size' },
              { field: 'level', code: 'NotNull' },
            ],
          })
        : jsonResponse(page([])),
    );
    renderCourts();
    await screen.findByText('No courts match these filters.');
    await user.click(screen.getByRole('button', { name: 'New court' }));
    const form = screen.getByRole('form', { name: 'New court' });
    await user.type(within(form).getByLabelText('Code'), 'x');
    await user.click(within(form).getByRole('button', { name: 'Save' }));

    const code = await within(form).findByLabelText('Code');
    expect(code).toHaveAttribute('aria-invalid', 'true');
    expect(code).toHaveAccessibleDescription('Use 2 to 32 letters, digits or hyphens.');
    expect(within(form).getByLabelText('Arabic name')).toHaveAccessibleDescription('This field is required.');
    expect(within(form).getByLabelText('English name')).toHaveAccessibleDescription('This value is too long.');
    expect(within(form).getByLabelText('Level')).toHaveAccessibleDescription('This field is required.');
    expect(within(form).getByLabelText('Governorate')).not.toHaveAttribute('aria-invalid');
    expect(within(form).getByRole('alert')).toHaveTextContent('Please correct the highlighted fields.');
  });

  it('explains a duplicate code', async () => {
    const user = userEvent.setup();
    serve((_url, init) => (init.method === 'POST' ? problem(409) : jsonResponse(page([]))));
    renderCourts();
    await screen.findByText('No courts match these filters.');
    await user.click(screen.getByRole('button', { name: 'New court' }));
    const form = screen.getByRole('form', { name: 'New court' });
    await user.type(within(form).getByLabelText('Code'), 'DUP');
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Another court already uses this code.');
  });

  it('explains a stale edit', async () => {
    const user = userEvent.setup();
    serve((_url, init) => (init.method === 'PUT' ? problem(409) : jsonResponse(page([court()]))));
    renderCourts();
    await user.click(await screen.findByRole('button', { name: 'Edit GEN-FI-1' }));
    const form = screen.getByRole('form', { name: 'Edit court GEN-FI-1' });
    await user.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent(
      'This court was changed by someone else, or its code is already used. Close the form and try again.',
    );
  });

  it('explains a missing permission when saving', async () => {
    const user = userEvent.setup();
    serve((_url, init) => (init.method === 'POST' ? problem(403) : jsonResponse(page([]))));
    renderCourts();
    await screen.findByText('No courts match these filters.');
    await user.click(screen.getByRole('button', { name: 'New court' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to manage courts.');
  });

  it('reports an unexpected failure when saving', async () => {
    const user = userEvent.setup();
    serve((_url, init) => (init.method === 'POST' ? problem(500) : jsonResponse(page([]))));
    renderCourts();
    await screen.findByText('No courts match these filters.');
    await user.click(screen.getByRole('button', { name: 'New court' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The court could not be saved. Please try again.');
  });
});

describe('court administration in Arabic', () => {
  it('labels the screen, the levels and the form in Arabic', async () => {
    const user = userEvent.setup();
    serve(() => jsonResponse(page([court({ level: 'CASSATION' })])));
    renderCourts('ar');
    expect(screen.getByRole('heading', { level: 1, name: 'المحاكم' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'المحاكم' });
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('النقض');
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('فعّالة');
    await user.click(screen.getByRole('button', { name: 'إضافة محكمة' }));
    const form = screen.getByRole('form', { name: 'إضافة محكمة' });
    expect(within(form).getByLabelText('الاسم بالعربية')).toBeInTheDocument();
    expect(within(form).getByRole('button', { name: 'حفظ' })).toBeInTheDocument();
  });
});
