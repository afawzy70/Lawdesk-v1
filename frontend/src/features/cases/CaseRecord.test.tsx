import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';
import { CaseDetailsFields } from './intake/CaseDetailsFields';
import { CaseDetailsView } from './CaseDetailsView';
import { CaseInvolvementsSection } from './CaseInvolvementsSection';
import { claimedAmountValid, detailsBody, detailsInputOf, EMPTY_DETAILS, type DetailsInput, type Involvement } from './api';
import { unitLabel } from './involvementLabel';

/** LD-050 sections 1 and 6: the case record beyond its numbers, and the administrative units a case concerns. */
const problem = (status: number) => () =>
  new Response(JSON.stringify({ status }), { status, headers: { 'Content-Type': 'application/problem+json' } });

const UNITS = [
  { id: 'a1', tree: 'AUTHORITY', parentId: null, level: 'SECTOR', nameAr: 'قطاع الخدمات المساندة', nameEn: 'Support services', active: true, validFrom: null, validTo: null, version: 0 },
  { id: 'a2', tree: 'AUTHORITY', parentId: 'a1', level: 'DEPARTMENT', nameAr: 'إدارة الشؤون الإدارية', nameEn: null, active: true, validFrom: null, validTo: null, version: 0 },
];
const PRIMARY: Involvement = {
  id: 'i1', caseId: 'c1', orgUnitId: 'a2', primary: true, sectorName: 'قطاع الخدمات المساندة',
  departmentName: 'إدارة الشؤون الإدارية', sectionName: null, contactName: 'سارة', contactInfo: null,
  referralDate: '2026-01-15', requestedFromUnit: 'صور الملف', notes: null, version: 0,
};
const SUPPORT: Involvement = { ...PRIMARY, id: 'i2', primary: false, contactName: null, referralDate: null, requestedFromUnit: null };

function bodyOf(fetchMock: ReturnType<typeof fakeApi>, method: string, path: string): unknown {
  const call = fetchMock.mock.calls.filter(([input, init]) => input === path && (init?.method ?? 'GET') === method).at(-1);
  if (call === undefined) throw new Error(`no ${method} ${path}`);
  return JSON.parse((call[1] as RequestInit).body as string);
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

describe('the details helpers', () => {
  it('accepts an empty amount or up to three decimals with a point or a comma', () => {
    expect(['', ' ', '12', '12.5', '12,500', '0.001'].every(claimedAmountValid)).toBe(true);
    expect(['1.2345', '-1', 'abc', '1,', '1234567890123'].some(claimedAmountValid)).toBe(false);
  });

  it('turns empty inputs into nulls and a comma into a point, and back', () => {
    expect(detailsBody(EMPTY_DETAILS)).toEqual({
      chamber: null, receivedOn: null, referringUnitId: null, referredBy: null, facts: null, claimedAmount: null,
      importance: null, internalNotes: null,
    });
    const filled: DetailsInput = { ...EMPTY_DETAILS, chamber: ' الدائرة 3 ', claimedAmount: '1250,5', importance: 'HIGH' };
    expect(detailsBody(filled)).toMatchObject({ chamber: 'الدائرة 3', claimedAmount: '1250.5', importance: 'HIGH' });
    expect(detailsInputOf(undefined)).toEqual(EMPTY_DETAILS);
    expect(detailsInputOf({ chamber: 'x', claimedAmount: '5.000', importance: 'LOW' })).toMatchObject({
      chamber: 'x', claimedAmount: '5.000', importance: 'LOW', facts: '',
    });
  });

  it('labels a unit from its recorded names, most general first', () => {
    expect(unitLabel(PRIMARY)).toBe('قطاع الخدمات المساندة / إدارة الشؤون الإدارية');
  });
});

function Harness({ error }: { error?: 'details.error.amount' }) {
  const [value, setValue] = useState(EMPTY_DETAILS);
  return (
    <>
      <CaseDetailsFields value={value} onChange={setValue} amountError={error} />
      <output data-testid="value">{JSON.stringify(value)}</output>
    </>
  );
}

function renderWith(node: React.ReactNode) {
  return render(
    <MemoryRouter>
      <LocaleProvider>{node}</LocaleProvider>
    </MemoryRouter>,
  );
}

describe('the details fields', () => {
  it('collects every value and offers the authority structure as referrers', async () => {
    vi.stubGlobal('fetch', fakeApi({ routes: { 'GET /api/v1/org-units?tree=AUTHORITY&includeInactive=false': () => jsonResponse(UNITS) } }));
    const user = userEvent.setup();
    renderWith(<Harness />);

    await screen.findByRole('option', { name: 'Support services' });
    await user.type(screen.getByLabelText('Chamber'), 'Third chamber');
    await user.type(screen.getByLabelText('Date received by the legal department'), '2026-01-20');
    await user.selectOptions(screen.getByLabelText('Referring unit'), 'a2');
    await user.type(screen.getByLabelText('Referred by (if outside the structure)'), 'Secretary general');
    await user.type(screen.getByLabelText('Subject and summary of the facts'), 'Facts');
    await user.type(screen.getByLabelText('Amount claimed (KWD)'), '12500.500');
    await user.selectOptions(screen.getByLabelText('Importance'), 'CRITICAL');
    await user.type(screen.getByLabelText('Internal notes'), 'Note');

    expect(JSON.parse(screen.getByTestId('value').textContent)).toEqual({
      chamber: 'Third chamber', receivedOn: '2026-01-20', referringUnitId: 'a2', referredBy: 'Secretary general',
      facts: 'Facts', claimedAmount: '12500.500', importance: 'CRITICAL', internalNotes: 'Note',
    });
  });

  it('shows an amount error and still works when the structure cannot be loaded', async () => {
    vi.stubGlobal('fetch', fakeApi({ routes: { 'GET /api/v1/org-units?tree=AUTHORITY&includeInactive=false': problem(500) } }));
    renderWith(<Harness error="details.error.amount" />);
    expect(await screen.findByText('Enter a number with up to three decimals, or leave it empty.')).toBeInTheDocument();
    expect(screen.getByLabelText('Amount claimed (KWD)')).toHaveAttribute('aria-invalid', 'true');
  });
});

describe('the details view', () => {
  it('shows only what was recorded, with the amount in dinars and fils', () => {
    renderWith(
      <CaseDetailsView
        details={{ chamber: 'الدائرة 3', receivedOn: '2026-01-20', claimedAmount: '12500.5', importance: 'HIGH', facts: 'f', internalNotes: null }}
      />,
    );
    const region = screen.getByRole('region', { name: 'Case record' });
    expect(region).toHaveTextContent('الدائرة 3');
    expect(region).toHaveTextContent('12,500.500 KWD');
    expect(region).toHaveTextContent('High');
    expect(region).not.toHaveTextContent('Internal notes');
  });

  it('says so when nothing is recorded', () => {
    renderWith(<CaseDetailsView details={undefined} />);
    expect(screen.getByText('No further details recorded.')).toBeInTheDocument();
  });
});

describe('the units a case concerns', () => {
  const routes = (extra: Record<string, () => Response> = {}) => ({
    'GET /api/v1/cases/c1/involvements': () => jsonResponse([PRIMARY, SUPPORT]),
    'GET /api/v1/org-units?tree=AUTHORITY&includeInactive=false': () => jsonResponse(UNITS),
    ...extra,
  });

  it('lists the units with the recorded names and adds one from the structure', async () => {
    const fetchMock = fakeApi({ routes: routes({ 'POST /api/v1/cases/c1/involvements': () => jsonResponse(SUPPORT, 201) }) });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderWith(<CaseInvolvementsSection caseId="c1" editable />);

    const list = await screen.findByRole('list');
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(list).toHaveTextContent('Primary');
    expect(list).toHaveTextContent('Supporting');

    await screen.findByRole('option', { name: 'Support services' });
    await user.selectOptions(screen.getByLabelText('Unit of the structure'), 'a2');
    expect(screen.queryByLabelText('Section')).not.toBeInTheDocument();
    await user.type(screen.getByLabelText('Employee in charge'), 'Khaled');
    await user.type(screen.getByLabelText('Contact details'), '1234');
    await user.type(screen.getByLabelText('Referral date'), '2026-02-01');
    await user.type(screen.getByLabelText('Required from the unit'), 'Records');
    await user.click(screen.getByRole('checkbox', { name: 'Primary unit' }));
    await user.click(screen.getByRole('button', { name: 'Add unit' }));
    expect(await screen.findByText('Unit added.')).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'POST', '/api/v1/cases/c1/involvements')).toMatchObject({
      orgUnitId: 'a2', primary: true, contactName: 'Khaled', contactInfo: '1234', referralDate: '2026-02-01',
      requestedFromUnit: 'Records', sectionName: null,
    });
  });

  it('adds a unit by typed names, refuses an empty one and explains a rule refusal', async () => {
    const fetchMock = fakeApi({ routes: routes({ 'POST /api/v1/cases/c1/involvements': problem(422) }) });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderWith(<CaseInvolvementsSection caseId="c1" editable />);

    await screen.findByRole('list');
    await user.click(screen.getByRole('button', { name: 'Add unit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a unit');

    await user.type(screen.getByLabelText('Sector'), 'قطاع');
    await user.type(screen.getByLabelText('Department'), 'إدارة');
    await user.type(screen.getByLabelText('Section'), 'قسم');
    await user.click(screen.getByRole('button', { name: 'Add unit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('primary unit already');
    expect(bodyOf(fetchMock, 'POST', '/api/v1/cases/c1/involvements')).toMatchObject({
      orgUnitId: null, sectorName: 'قطاع', departmentName: 'إدارة', sectionName: 'قسم',
    });
  });

  it('removes a unit and reports failures', async () => {
    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: routes({
          'DELETE /api/v1/cases/c1/involvements/i1': () => new Response(null, { status: 204 }),
          'DELETE /api/v1/cases/c1/involvements/i2': problem(500),
          'POST /api/v1/cases/c1/involvements': problem(500),
        }),
      }),
    );
    const user = userEvent.setup();
    renderWith(<CaseInvolvementsSection caseId="c1" editable />);

    const buttons = await screen.findAllByRole('button', { name: 'Remove' });
    await user.click(buttons[0] as HTMLElement);
    expect(await screen.findByText('Unit removed.')).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Remove' })[1] as HTMLElement);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or saved');
    await user.type(screen.getByLabelText('Sector'), 'x');
    await user.click(screen.getByRole('button', { name: 'Add unit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or saved');
  });

  it('is read-only for an archived case, says so when empty and reports a failed load', async () => {
    vi.stubGlobal('fetch', fakeApi({ routes: routes({ 'GET /api/v1/cases/c1/involvements': () => jsonResponse([]) }) }));
    const first = renderWith(<CaseInvolvementsSection caseId="c1" editable={false} />);
    expect(await screen.findByText('No unit recorded yet.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add unit' })).not.toBeInTheDocument();
    first.unmount();

    vi.stubGlobal('fetch', fakeApi({ routes: routes({ 'GET /api/v1/cases/c1/involvements': problem(500) }) }));
    renderWith(<CaseInvolvementsSection caseId="c1" editable={false} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or saved');
  });
});
