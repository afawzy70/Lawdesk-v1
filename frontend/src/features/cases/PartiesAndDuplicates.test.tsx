import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { fakeApi } from '../../test/api';
import { jsonResponse } from '../../test/http';
import { DuplicateWarning } from './DuplicateWarning';
import { PartyCases } from './PartyCases';
import { checkDuplicates, type IntakeInput } from './api';

/** LD-050 sections 2 and 3: the cases of a party, and the duplicate warning. */
const problem = (status: number) => () =>
  new Response(JSON.stringify({ status }), { status, headers: { 'Content-Type': 'application/problem+json' } });
const wrap = (node: React.ReactNode) =>
  render(
    <MemoryRouter>
      <LocaleProvider>{node}</LocaleProvider>
    </MemoryRouter>,
  );
beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok; path=/';
});

describe('the cases of a party', () => {
  it('lists the other cases the viewer may see', async () => {
    vi.stubGlobal(
      'fetch',
      fakeApi({
        routes: {
          'GET /api/v1/parties/p1/cases': () =>
            jsonResponse([
              { caseId: 'c1', referenceNo: 'LD-1', title: 'This one', status: 'OPEN', role: 'OPPONENT', side: 'PLAINTIFF' },
              { caseId: 'c9', referenceNo: 'LD-9', title: 'Another case', status: 'OPEN', role: 'OPPONENT', side: null },
            ]),
        },
      }),
    );
    const user = userEvent.setup();
    wrap(<PartyCases partyId="p1" currentCaseId="c1" />);
    await user.click(screen.getByRole('button', { name: 'Show its cases' }));
    const list = await screen.findByRole('list', { name: 'Cases of this party' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(1);
    expect(list).toHaveTextContent('LD-9');
  });

  it('says when there is none and when loading fails', async () => {
    vi.stubGlobal('fetch', fakeApi({ routes: { 'GET /api/v1/parties/p1/cases': () => jsonResponse([]) } }));
    const user = userEvent.setup();
    const first = wrap(<PartyCases partyId="p1" currentCaseId="c1" />);
    await user.click(screen.getByRole('button', { name: 'Show its cases' }));
    expect(await screen.findByText('No other case you may see.')).toBeInTheDocument();
    first.unmount();

    vi.stubGlobal('fetch', fakeApi({ routes: { 'GET /api/v1/parties/p1/cases': problem(500) } }));
    wrap(<PartyCases partyId="p1" currentCaseId="c1" />);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Show its cases' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
  });
});

describe('the duplicate warning', () => {
  const result = {
    visible: [{ caseId: 'c9', referenceNo: 'LD-9', title: 'Ministry v. Nile', kind: 'EXACT' as const, reasons: ['COURT_CASE_NUMBER', 'TITLE'] }],
    hidden: 2,
  };

  const firstHit = result.visible[0] as (typeof result.visible)[number];

  it('shows what matched and how many cases are hidden, and asks for the registrar’s decision', async () => {
    const onConfirmed = vi.fn();
    const onNote = vi.fn();
    const user = userEvent.setup();
    const { rerender } = wrap(
      <DuplicateWarning result={result} confirmed={false} note="" needConfirmation onConfirmed={onConfirmed} onNote={onNote} />,
    );
    expect(screen.getByRole('link', { name: /LD-9/ })).toBeInTheDocument();
    expect(screen.getByText(/Same court case number/)).toHaveTextContent('court case number');
    expect(screen.getByText('2 more similar case(s) exist that you are not allowed to see.')).toBeInTheDocument();
    expect(screen.getByText('Confirm that the case is a different one to register it.')).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox'));
    expect(onConfirmed).toHaveBeenCalledWith(true);

    rerender(
      <MemoryRouter>
        <LocaleProvider>
          <DuplicateWarning
            result={{ visible: [{ ...firstHit, kind: 'SIMILAR' }], hidden: 0 }}
            confirmed
            note=""
            needConfirmation={false}
            onConfirmed={onConfirmed}
            onNote={onNote}
          />
        </LocaleProvider>
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText('Why it is different (kept with the registration)'), 'a');
    expect(onNote).toHaveBeenCalledWith('a');
    expect(screen.queryByText(/not allowed to see/)).not.toBeInTheDocument();
  });

  it('asks the server with what the form holds', async () => {
    const fetchMock = fakeApi({ routes: { 'GET /api/v1/cases/duplicate-check?automatedNo=000123&courtId=co1&courtCaseNumber=4471&courtCaseYear=2026&title=T': () => jsonResponse(result) } });
    vi.stubGlobal('fetch', fetchMock);
    const input = { automatedNo: '000123', courtId: 'co1', courtCaseNumber: '4471', courtCaseYear: '2026', title: 'T' } as IntakeInput;
    expect(await checkDuplicates(input)).toEqual(result);
    const empty = { automatedNo: '', courtId: '', courtCaseNumber: '', courtCaseYear: '', title: '' } as IntakeInput;
    vi.stubGlobal('fetch', fakeApi({ routes: { 'GET /api/v1/cases/duplicate-check?': () => jsonResponse({ visible: [], hidden: 0 }) } }));
    expect(await checkDuplicates(empty)).toEqual({ visible: [], hidden: 0 });
  });
});
