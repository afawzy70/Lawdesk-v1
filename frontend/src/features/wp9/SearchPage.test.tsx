import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { jsonResponse } from '../../test/http';
import { SearchPage } from './SearchPage';
import { hit, mount, problem, serve, type Request } from './testSupport';

/** LD-057, requirements section 28: the unified search page. */

function result(groups: unknown[], extra: object = {}) {
  return jsonResponse({ query: 'unpaid', total: groups.length, searchedTypes: ['CASE', 'PARTY'], groups, ...extra });
}

const CASES = { type: 'CASE', total: 7, page: 0, size: 5, items: [hit(), hit({ id: 'h2', title: 'Second case', snippet: null, matchedIn: ['TITLE', 'NOT_A_PART'], caseReferenceNo: 'LD-2026-00002', date: null })] };
const LIBRARY = { type: 'LIBRARY', total: 1, page: 0, size: 5, items: [hit({ id: 'j1', type: 'LIBRARY', caseId: null, caseReferenceNo: null, title: 'A judgment on supply', detail: 'Court of Cassation', matchedIn: ['SUBJECT'] })] };
const PARTIES = { type: 'PARTY', total: 1, page: 0, size: 5, items: [hit({ id: 'p1', type: 'PARTY', title: 'Nile Trading', matchedIn: ['NAME'], caseCount: 3, snippet: 'Nile Trading', date: null })] };
const NOTES = { type: 'NOTE', total: 1, page: 0, size: 5, items: [hit({ id: 'n1', type: 'NOTE', caseId: null, caseReferenceNo: null, matchedIn: [] })] };

const search = (r: Request) => r.method === 'GET' && r.path === '/api/v1/search';

describe('the search page', () => {
  it('shows nothing until something is searched for', () => {
    const { requests } = serve(() => undefined);
    mount(<SearchPage />, ['CASE_VIEW'], '/search');
    expect(screen.getByRole('heading', { name: 'Search' })).toBeInTheDocument();
    expect(requests).toHaveLength(0);
  });

  it('searches the words of the address and shows the results grouped by type, each opening its record', async () => {
    const { requests } = serve((r) => (search(r) ? result([CASES, PARTIES, LIBRARY, NOTES]) : undefined));
    mount(<SearchPage />, ['CASE_VIEW'], '/search?q=unpaid');
    const groups = await screen.findAllByRole('region');
    expect(groups.map((g) => within(g).getByRole('heading', { level: 2 }).textContent)).toEqual(['Cases (7)', 'Parties (1)', 'Library judgments (1)', 'Notes (1)']);
    expect(requests.find(search)?.query.get('q')).toBe('unpaid');
    expect(requests.find(search)?.query.get('size')).toBe('5');
    expect(screen.getByText('4 result(s) you may see')).toBeInTheDocument();
    expect(screen.getByText('Searched: Cases، Parties')).toBeInTheDocument();
    const [cases, parties, library, notes] = groups as [HTMLElement, HTMLElement, HTMLElement, HTMLElement];
    const first = within(cases).getAllByRole('listitem')[0] as HTMLElement;
    expect(first).toHaveTextContent('unpaid invoices of the supplier');
    expect(first).toHaveTextContent('Found in: subject');
    expect(first).toHaveTextContent('01/03/2026');
    expect(within(first).getByRole('link')).toHaveAttribute('href', expect.stringContaining('/cases/c0000000-0000-4000-8000-000000000001'));
    // An unrecognised part is shown as it came; a hit without parts shows no list of them.
    expect(within(cases).getAllByRole('listitem')[1]).toHaveTextContent('Found in: title، NOT_A_PART');
    expect(within(parties).getByRole('listitem')).toHaveTextContent('on 3 case(s) you may see');
    expect(within(library).getByRole('link')).toHaveAttribute('href', expect.stringContaining('/library/j1'));
    expect(within(notes).queryByRole('link')).not.toBeInTheDocument();
    expect(within(notes).getByRole('listitem')).not.toHaveTextContent('Found in');
  });

  it('shows more of one group by asking for its next page', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => {
      if (!search(r)) return undefined;
      return r.query.get('page') === '1' ? result([{ ...CASES, page: 1, items: [hit({ id: 'h9', title: 'Eighth case' })] }]) : result([CASES]);
    });
    mount(<SearchPage />, ['CASE_VIEW'], '/search?q=unpaid');
    await user.click(await screen.findByRole('button', { name: 'Show more Cases' }));
    expect(await screen.findByText('Eighth case — LD-2026-00001')).toBeInTheDocument();
    const next = requests.filter(search).at(-1);
    expect(next?.query.get('types')).toBe('CASE');
    expect(next?.query.get('page')).toBe('1');
  });

  it('sends the chosen types, dispute type and dates', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (search(r) ? result([CASES]) : undefined));
    mount(<SearchPage />, ['CASE_VIEW'], '/search?q=unpaid');
    await screen.findAllByRole('region');
    const form = screen.getByRole('search', { name: 'Search form' });
    await user.click(within(form).getByLabelText('Hearings'));
    await user.click(within(form).getByLabelText('Notes'));
    await user.click(within(form).getByLabelText('Hearings'));
    await user.selectOptions(within(form).getByLabelText('Dispute type'), 'Labour');
    await user.type(within(form).getByLabelText('From date'), '2026-01-01');
    await user.type(within(form).getByLabelText('To date'), '2026-02-01');
    await waitFor(() => {
      const last = requests.filter(search).at(-1);
      expect(Object.fromEntries(last?.query ?? [])).toMatchObject({ q: 'unpaid', types: 'NOTE', caseType: 'LABOR', from: '2026-01-01', to: '2026-02-01' });
    });
  });

  it('takes new words from the form into the address', async () => {
    const user = userEvent.setup();
    const { requests } = serve((r) => (search(r) ? result([]) : undefined));
    mount(<SearchPage />, ['CASE_VIEW'], '/search?q=old');
    expect(await screen.findByText('Nothing found.')).toBeInTheDocument();
    const box = screen.getByLabelText('Words to search for');
    expect(box).toHaveValue('old');
    await user.clear(box);
    await user.type(box, 'contract');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => { expect(requests.filter(search).at(-1)?.query.get('q')).toBe('contract'); });
    // Clearing the words returns to the empty page.
    await user.clear(await screen.findByDisplayValue('contract'));
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => { expect(screen.queryByText('Nothing found.')).not.toBeInTheDocument(); });
  });

  it('reports a failed search and a failure to show more, and refuses someone who may not search', async () => {
    serve((r) => (search(r) ? problem(500) : undefined));
    const first = mount(<SearchPage />, ['CASE_VIEW'], '/search?q=unpaid');
    expect(await screen.findByRole('alert')).toHaveTextContent('search failed');
    first.unmount();
    serve((r) => (search(r) ? problem(403) : undefined));
    mount(<SearchPage />, [], '/search?q=unpaid');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });

  it('reports a failure to show more of a group', async () => {
    const user = userEvent.setup();
    serve((r) => (search(r) ? (r.query.get('page') === '1' ? problem(500) : result([CASES])) : undefined));
    mount(<SearchPage />, ['CASE_VIEW'], '/search?q=unpaid');
    await user.click(await screen.findByRole('button', { name: 'Show more Cases' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('search failed');
  });
});
