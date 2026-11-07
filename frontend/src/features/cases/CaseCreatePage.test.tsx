import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { aCase, jsonResponse, problem, renderCases, scriptedFetch, type Request } from './testSupport';

/** LD-045 government case intake (docs/delivery/GOVERNMENT-INTAKE-ACCEPTANCE.md): the registration screen. */

vi.setConfig({ testTimeout: 20_000 });

const COURT = '00000000-0000-4000-8000-0000000000c1';
const SPECIALIST = '00000000-0000-4000-8000-0000000000a1';
const ALTERNATE = '00000000-0000-4000-8000-0000000000a2';
const REGISTERED = '00000000-0000-4000-8000-0000000000b1';

const MEMBERS = [
  { id: SPECIALIST, username: 'sara', displayName: 'Sara Specialist', enabled: true },
  { id: ALTERNATE, username: 'omar', displayName: 'Omar Alternate', enabled: true },
];
const COURTS = [
  { id: COURT, code: 'KW-FI-1', nameAr: 'محكمة أول درجة', nameEn: 'First Instance Court', level: 'FIRST_INSTANCE', active: true },
];
const PARTIES = [{ id: REGISTERED, kind: 'ORGANIZATION', name: 'Nile Trading Co.', version: 0, authority: false },
  { id: 'authority-id', kind: 'ORGANIZATION', name: 'The Authority', version: 0, authority: true }];

const page = (items: unknown[]) => jsonResponse({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1 });

function serve(onIntake?: (request: Request) => Response) {
  const created = aCase();
  return scriptedFetch((request) => {
    const url = new URL(request.url, 'http://localhost');
    if (request.method === 'POST' && url.pathname === '/api/v1/cases/intake') {
      return onIntake?.(request) ?? jsonResponse(created, 201);
    }
    if (url.pathname === '/api/v1/cases/members') return jsonResponse(MEMBERS);
    if (url.pathname === '/api/v1/courts') return page(COURTS);
    if (url.pathname === '/api/v1/parties') return page(PARTIES);
    if (url.pathname.endsWith('/parties') || url.pathname.endsWith('/timeline')) return page([]);
    if (url.pathname.startsWith('/api/v1/cases/')) return jsonResponse(created);
    return problem(404);
  });
}

type User = ReturnType<typeof userEvent.setup>;

async function fillRequired(user: User, lang: 'en' | 'ar' = 'en') {
  const ar = lang === 'ar';
  await user.type(screen.getByRole('textbox', { name: ar ? /الرقم الآلي للقضية/ : /Case automated number/ }), '000123');
  await user.type(screen.getByRole('textbox', { name: ar ? /عنوان القضية/ : /Title/ }), 'Ministry v. Nile Trading');
  await user.selectOptions(screen.getByRole('combobox', { name: ar ? /نوع القضية/ : /Case type/ }), 'COMMERCIAL');
  const court = await screen.findByRole('combobox', { name: ar ? /المحكمة/ : /Court/ });
  await within(court).findByRole('option', { name: ar ? 'محكمة أول درجة' : 'First Instance Court' });
  await user.selectOptions(court, COURT);
  await user.type(screen.getByRole('textbox', { name: ar ? /رقم القضية في درجة التقاضي/ : /Case number at this stage/ }), '4471');
  await user.type(screen.getByRole('textbox', { name: ar ? /السنة/ : /Year/ }), '2026');
  await user.type(screen.getByLabelText(ar ? /تاريخ القيد بالمحكمة/ : /Filing date at the court/), '2026-01-12');
  await user.click(screen.getByRole('radio', { name: ar ? 'مدعي' : 'Plaintiff' }));
  const primary = await screen.findByRole('combobox', { name: ar ? /العضو المختص/ : /Specialist member/ });
  await within(primary).findByRole('option', { name: 'Sara Specialist' });
  await user.selectOptions(primary, SPECIALIST);
  await user.click(screen.getByRole('radio', { name: ar ? 'لا يوجد أثر مالي' : 'There is no financial impact' }));
}

const submit = (lang: 'en' | 'ar' = 'en') =>
  screen.getByRole('button', { name: lang === 'ar' ? 'تسجيل القضية' : 'Register the case' });

const intakeBody = (requests: Request[]) =>
  requests.find((r) => r.method === 'POST' && r.url === '/api/v1/cases/intake')?.body as Record<string, unknown>;

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

describe('the registration screen', () => {
  it('orders the sections and has no legal operations section', async () => {
    serve();
    renderCases('/cases/new');
    const headings = (await screen.findAllByRole('heading', { level: 2 })).map((h) => h.textContent);
    expect(headings).toEqual([
      'Case details',
      'Litigation stage',
      'The authority’s position and the parties',
      'Legal department members',
      'Financial impact',
    ]);
    expect(screen.queryByText(/Legal operations/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /Hearings|Tasks|Expert|Notes|Documents/ })).not.toBeInTheDocument();
    expect(document.title).toBe('Register a new case — LawDesk (Preview)');
  });

  it('keeps the two numbers apart and marks the required fields', async () => {
    serve();
    renderCases('/cases/new');
    const caseNumber = await screen.findByRole('textbox', { name: /Case automated number/ });
    const stageNumber = screen.getByRole('textbox', { name: /Case number at this stage/ });
    expect(caseNumber).not.toBe(stageNumber);
    expect(caseNumber).toHaveAccessibleDescription(/stays the same on every litigation stage/);
    expect(stageNumber).toHaveAccessibleDescription(/belongs to this stage only/);
    for (const field of [caseNumber, stageNumber, screen.getByRole('textbox', { name: /Title/ })]) {
      expect(field).toHaveAttribute('aria-required', 'true');
    }
  });

  it('registers the case in one request, keeping the automated number as text', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    const { url } = renderCases('/cases/new');

    await fillRequired(user);
    await user.click(submit());

    expect(await screen.findByText('Case LD-2026-00001 created.')).toHaveAttribute('role', 'status');
    expect(url()).toBe(`/cases/${aCase().id}?lang=en`);
    expect(intakeBody(requests)).toEqual({
      automatedNo: '000123',
      title: 'Ministry v. Nile Trading',
      caseType: 'COMMERCIAL',
      subject: null,
      openedOn: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) as string,
      firstInstance: { courtId: COURT, courtCaseNumber: '4471', courtCaseYear: 2026, filingDate: '2026-01-12' },
      authorityPosition: 'PLAINTIFF',
      parties: [],
      primaryMemberId: SPECIALIST,
      alternateMemberId: null,
      financialImpact: 'NO',
    });
    // Registering writes nothing but the registration itself: no hearing, task, note or document is created.
    expect(requests.filter((r) => r.method !== 'GET').map((r) => r.url)).toEqual(['/api/v1/cases/intake']);
  });

  it('refuses an empty registration locally and names every missing field', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderCases('/cases/new');
    await screen.findByRole('textbox', { name: /Case automated number/ });
    await user.click(submit());

    expect(await screen.findByRole('alert')).toHaveTextContent('Please correct the fields marked below.');
    expect(screen.getByRole('textbox', { name: /Case automated number/ })).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('textbox', { name: /Case number at this stage/ })).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Choose the authority’s position.')).toBeInTheDocument();
    expect(screen.getByText('Choose one of the two options.')).toBeInTheDocument();
    expect(requests.some((r) => r.method === 'POST')).toBe(false);
  });

  it('puts the authority on its own side and lets both sides have several parties', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderCases('/cases/new');
    await fillRequired(user);

    const plaintiffs = screen.getByRole('region', { name: 'Plaintiffs' });
    const defendants = screen.getByRole('region', { name: 'Defendants' });
    expect(within(plaintiffs).getByText('The authority', { selector: 'strong' })).toBeInTheDocument();
    expect(within(defendants).queryByText('The authority', { selector: 'strong' })).not.toBeInTheDocument();

    const addParty = async (side: string, name: string, number: string) => {
      const group = screen.getByRole('group', { name: 'Add a party' });
      await user.selectOptions(within(group).getByLabelText('Side'), side);
      await user.type(within(group).getByLabelText('Name'), name);
      if (number !== '') await user.type(within(group).getByLabelText('Party automated number'), number);
      await user.click(within(group).getByRole('button', { name: 'Add to the case' }));
    };
    await addParty('PLAINTIFF', 'Co-plaintiff Agency', '000123');
    await addParty('DEFENDANT', 'First Defendant', '77');
    await addParty('DEFENDANT', 'Second Defendant', '');

    expect(within(plaintiffs).getByText('Co-plaintiff Agency')).toBeInTheDocument();
    expect(within(defendants).getAllByRole('listitem')).toHaveLength(2);

    await user.click(submit());
    await screen.findByText('Case LD-2026-00001 created.');
    const parties = intakeBody(requests).parties as { side: string; newParty: { name: string; nationalId: string | null } }[];
    expect(parties.map((p) => [p.side, p.newParty.name, p.newParty.nationalId])).toEqual([
      ['PLAINTIFF', 'Co-plaintiff Agency', '000123'],
      ['DEFENDANT', 'First Defendant', '77'],
      ['DEFENDANT', 'Second Defendant', null],
    ]);
  });

  it('shows the authority among the defendants when it is the defendant', async () => {
    const user = userEvent.setup();
    serve();
    renderCases('/cases/new');
    await screen.findByRole('radio', { name: 'Defendant' });
    await user.click(screen.getByRole('radio', { name: 'Defendant' }));
    expect(within(screen.getByRole('region', { name: 'Defendants' })).getByText('The authority', { selector: 'strong' }))
      .toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Plaintiffs' })).queryByText('The authority', { selector: 'strong' }))
      .not.toBeInTheDocument();
  });

  it('does not flip or drop parties when the position changes, and asks for a review before saving', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderCases('/cases/new');
    await fillRequired(user);
    const group = screen.getByRole('group', { name: 'Add a party' });
    await user.selectOptions(within(group).getByLabelText('Side'), 'DEFENDANT');
    await user.type(within(group).getByLabelText('Name'), 'Opponent');
    await user.click(within(group).getByRole('button', { name: 'Add to the case' }));

    await user.click(screen.getByRole('radio', { name: 'Defendant' }));
    expect(screen.getByRole('alert')).toHaveTextContent('You changed the authority’s position');
    // The party kept its side; nothing was dropped.
    expect(within(screen.getByRole('region', { name: 'Defendants' })).getByText('Opponent')).toBeInTheDocument();

    await user.click(submit());
    expect(await screen.findByText('Review the parties after changing the authority’s position.')).toBeInTheDocument();
    expect(requests.some((r) => r.method === 'POST')).toBe(false);

    await user.click(screen.getByRole('button', { name: 'Swap the sides of all other parties' }));
    expect(within(screen.getByRole('region', { name: 'Plaintiffs' })).getByText('Opponent')).toBeInTheDocument();
    await user.click(submit());
    await screen.findByText('Case LD-2026-00001 created.');
    const body = intakeBody(requests);
    expect(body.authorityPosition).toBe('DEFENDANT');
    expect((body.parties as { side: string }[]).map((p) => p.side)).toEqual(['PLAINTIFF']);
  });

  it('uses a registered party found on the server', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderCases('/cases/new');
    await fillRequired(user);
    const group = screen.getByRole('group', { name: 'Add a party' });
    await user.click(within(group).getByRole('radio', { name: 'Registered party' }));
    const select = within(group).getByRole('combobox', { name: 'Registered party' });
    await within(select).findByRole('option', { name: /^Nile Trading Co\. \(/ });
    expect(within(select).queryByRole('option', { name: /The Authority/ })).not.toBeInTheDocument();
    await user.selectOptions(select, REGISTERED);
    await user.click(within(group).getByRole('button', { name: 'Add to the case' }));
    expect(within(screen.getByRole('region', { name: 'Defendants' })).getByText('Nile Trading Co.')).toBeInTheDocument();

    await user.click(submit());
    await screen.findByText('Case LD-2026-00001 created.');
    expect(intakeBody(requests).parties).toEqual([{ partyId: REGISTERED, side: 'DEFENDANT' }]);
  });

  it('shows the amount only for a financial impact and sends nothing for none', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderCases('/cases/new');
    await fillRequired(user);
    expect(screen.queryByRole('textbox', { name: /Financial impact amount/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: 'There is a financial impact' }));
    const amount = screen.getByRole('textbox', { name: /Financial impact amount \(KWD\)/ });
    expect(amount).toHaveAccessibleDescription('Greater than zero, up to three decimals.');
    for (const bad of ['', '0', '-5', '1.2345', 'abc']) {
      await user.clear(amount);
      if (bad !== '') await user.type(amount, bad);
      await user.click(submit());
      expect(await screen.findByText('Enter an amount greater than zero with at most three decimals.')).toBeInTheDocument();
    }
    expect(requests.some((r) => r.method === 'POST')).toBe(false);
    await user.clear(amount);
    await user.type(amount, '15250.500');
    await user.click(submit());
    await screen.findByText('Case LD-2026-00001 created.');
    expect(intakeBody(requests)).toMatchObject({ financialImpact: 'YES', financialAmount: '15250.500' });
  });

  it('asks before an entered amount is dropped by choosing no financial impact', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderCases('/cases/new');
    await fillRequired(user);
    await user.click(screen.getByRole('radio', { name: 'There is a financial impact' }));
    await user.type(screen.getByRole('textbox', { name: /Financial impact amount/ }), '900.250');

    await user.click(screen.getByRole('radio', { name: 'There is no financial impact' }));
    expect(screen.getByRole('alert')).toHaveTextContent('The amount you entered (900.25 KWD) will be removed');
    expect(screen.getByRole('radio', { name: 'There is a financial impact' })).toBeChecked();

    await user.click(screen.getByRole('button', { name: 'Keep the amount' }));
    expect(screen.getByRole('textbox', { name: /Financial impact amount/ })).toHaveValue('900.250');

    await user.click(screen.getByRole('radio', { name: 'There is no financial impact' }));
    await user.click(screen.getByRole('button', { name: 'Remove the amount' }));
    expect(screen.getByRole('radio', { name: 'There is no financial impact' })).toBeChecked();
    expect(screen.queryByRole('textbox', { name: /Financial impact amount/ })).not.toBeInTheDocument();

    await user.click(submit());
    await screen.findByText('Case LD-2026-00001 created.');
    expect(intakeBody(requests)).not.toHaveProperty('financialAmount');
    expect(intakeBody(requests).financialImpact).toBe('NO');
  });

  it('will not let the same person be specialist and alternate, and lists only the offered members', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderCases('/cases/new');
    const primary = await screen.findByRole('combobox', { name: /Specialist member/ });
    const alternate = screen.getByRole('combobox', { name: /Alternate member/ });
    await within(primary).findByRole('option', { name: 'Sara Specialist' });
    expect(within(alternate).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'No alternate member', 'Sara Specialist', 'Omar Alternate',
    ]);
    await user.selectOptions(primary, SPECIALIST);
    expect(within(alternate).getByRole('option', { name: 'Sara Specialist' })).toBeDisabled();
    await user.selectOptions(alternate, ALTERNATE);
    expect(within(primary).getByRole('option', { name: 'Omar Alternate' })).toBeDisabled();
    // Choosing the alternate as specialist clears the alternate rather than keeping a duplicate.
    await user.selectOptions(primary, ALTERNATE).catch(() => undefined);
    expect(primary).toHaveValue(SPECIALIST);
    expect(requests.filter((r) => r.url === '/api/v1/cases/members')).toHaveLength(1);
  });

  it('explains what the server refused, field by field and by rule', async () => {
    const user = userEvent.setup();
    const answers = [
      problem(400, { errors: [
        { field: 'automatedNo', code: 'NotBlank' },
        { field: 'firstInstance.courtCaseNumber', code: 'Size' },
        { field: 'financialAmount', code: 'Digits' },
        { field: 'alternateMemberId', code: 'SameAsPrimary' },
        { field: 'parties[0].partyId', code: 'OneOf' },
      ] }),
      problem(422, { rule: 'MEMBER_NOT_ELIGIBLE' }),
      problem(422, { rule: 'COURT_INACTIVE' }),
      problem(409),
      problem(403),
      problem(500),
    ];
    serve(() => answers.shift() ?? problem(500));
    renderCases('/cases/new');
    await fillRequired(user);

    await user.click(submit());
    expect(await screen.findByRole('alert')).toHaveTextContent('Please correct the fields marked below.');
    expect(screen.getByRole('textbox', { name: /Case automated number/ })).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('textbox', { name: /Case number at this stage/ })).toHaveAccessibleDescription(/Too long|too long|longer/i);
    expect(screen.getByText('The specialist and the alternate must be different people.')).toBeInTheDocument();
    expect(screen.getByText('Choose a registered party or enter a new one, not both.')).toBeInTheDocument();

    for (const text of [
      'The chosen member is not an active member of the legal department.',
      'The chosen court is no longer active. Choose another court.',
      'A number entered here is already used',
      'You do not have permission to do this.',
      'The case could not be saved. Try again later.',
    ]) {
      await user.click(submit());
      await waitFor(() => { expect(screen.getByRole('alert')).toHaveTextContent(text); });
    }
  });

  it('prevents a second submission while saving', async () => {
    const user = userEvent.setup();
    let release: (response: Response) => void = () => undefined;
    const { requests } = scriptedFetch((request) => {
      const url = new URL(request.url, 'http://localhost');
      if (request.method === 'POST') return new Promise<Response>((resolve) => { release = resolve; });
      if (url.pathname === '/api/v1/cases/members') return jsonResponse(MEMBERS);
      if (url.pathname === '/api/v1/courts') return page(COURTS);
      return page([]);
    });
    renderCases('/cases/new');
    await fillRequired(user);
    await user.click(submit());
    expect(await screen.findByRole('button', { name: 'Saving…' })).toBeDisabled();
    expect(requests.filter((r) => r.method === 'POST')).toHaveLength(1);
    release(problem(500));
  });
});

describe('the registration screen in Arabic', () => {
  it('uses the authority’s vocabulary, right to left, and registers', async () => {
    const user = userEvent.setup();
    const { requests } = serve();
    renderCases('/cases/new?lang=ar');
    expect(await screen.findByRole('heading', { level: 1, name: 'تسجيل قضية جديدة' })).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect((await screen.findAllByRole('heading', { level: 2 })).map((h) => h.textContent)).toEqual([
      'بيانات القضية', 'درجة التقاضي', 'موقف الهيئة والأطراف', 'أعضاء الإدارة القانونية', 'الأثر المالي',
    ]);
    expect(screen.getByRole('radio', { name: 'مدعي' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'مدعى عليه' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: /العضو المختص/ })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: /العضو البديل/ })).toBeInTheDocument();
    expect(screen.queryByText(/العمليات القانونية/)).not.toBeInTheDocument();

    await fillRequired(user, 'ar');
    await user.click(submit('ar'));
    await waitFor(() => { expect(intakeBody(requests)).toBeDefined(); });
    expect(intakeBody(requests)).toMatchObject({ automatedNo: '000123', authorityPosition: 'PLAINTIFF' });
  });

  it('shows the amount in Arabic with the dinar', async () => {
    const user = userEvent.setup();
    serve();
    renderCases('/cases/new?lang=ar');
    await screen.findByRole('radio', { name: 'يوجد أثر مالي' });
    await user.click(screen.getByRole('radio', { name: 'يوجد أثر مالي' }));
    expect(screen.getByRole('textbox', { name: /مبلغ الأثر المالي \(دينار كويتي\)/ })).toBeInTheDocument();
  });
});
