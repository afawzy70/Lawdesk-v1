import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../i18n/LocaleProvider';
import { PagedSelect, type PagedResult } from './PagedSelect';

/** QAD-044: a selector over a long list pages (and, where the API searches, searches) on the server. */

interface Item {
  id: string;
  name: string;
}
const ALL: Item[] = Array.from({ length: 250 }, (_, i) => ({ id: `i${String(i)}`, name: `Court ${String(i).padStart(3, '0')}` }));
ALL[205] = { id: 'i205', name: 'محكمة القاهرة الاقتصادية' };

function loader(source: Item[], size = 100) {
  return vi.fn((page: number, query: string): Promise<PagedResult<Item>> => {
    const hits = query === '' ? source : source.filter((i) => i.name.includes(query));
    return Promise.resolve({
      items: hits.slice(page * size, (page + 1) * size),
      totalItems: hits.length,
      totalPages: Math.ceil(hits.length / size),
    });
  });
}

function Harness({ load, server, onValue }: { load: ReturnType<typeof loader>; server: boolean; onValue?: (v: string) => void }) {
  const [value, setValue] = useState('');
  return (
    <>
      <label htmlFor="sel">Court</label>
      <PagedSelect<Item>
        selectProps={{ id: 'sel' }}
        value={value}
        onChange={(v) => {
          setValue(v);
          onValue?.(v);
        }}
        load={(page, query) => load(page, query)}
        optionLabel={(i) => i.name}
        serverSearch={server}
        searchLabel="Search courts"
        chooseLabel="Choose…"
      />
    </>
  );
}

function renderSelect(load: ReturnType<typeof loader>, server: boolean, lang: 'en' | 'ar' = 'en', onValue?: (v: string) => void) {
  return render(
    <MemoryRouter initialEntries={[`/?lang=${lang}`]}>
      <LocaleProvider>
        <Harness load={load} server={server} {...(onValue ? { onValue } : {})} />
      </LocaleProvider>
    </MemoryRouter>,
  );
}

const options = () => screen.getAllByRole('option').map((o) => o.textContent);

describe('PagedSelect', () => {
  it('shows the first page with the real total and loads the rest on request, never beyond the server pages', async () => {
    const user = userEvent.setup();
    const load = loader(ALL);
    renderSelect(load, true);
    await screen.findByRole('option', { name: 'Court 000' });
    expect(options()).toHaveLength(101);
    expect(screen.getByRole('status')).toHaveTextContent('Showing 100 of 250');

    await user.click(screen.getByRole('button', { name: 'Load more' }));
    await waitFor(() => { expect(options()).toHaveLength(201); });
    await user.click(screen.getByRole('button', { name: 'Load more' }));
    await waitFor(() => { expect(options()).toHaveLength(251); });
    expect(screen.getByRole('status')).toHaveTextContent('Showing 250 of 250');
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();
    expect(load.mock.calls.map(([p]) => p)).toEqual([0, 1, 2]);
  });

  it('is operable with the keyboard alone: search, then the list, then load more', async () => {
    const user = userEvent.setup();
    const picked = vi.fn();
    renderSelect(loader(ALL), true, 'en', picked);
    await screen.findByRole('option', { name: 'Court 000' });
    await user.tab();
    expect(screen.getByRole('searchbox', { name: 'Search courts' })).toHaveFocus();
    await user.tab();
    expect(screen.getByLabelText('Court')).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'Load more' })).toHaveFocus();
    await user.keyboard('{Enter}');
    await screen.findByRole('option', { name: 'Court 150' });
    await user.selectOptions(screen.getByLabelText('Court'), 'i150');
    expect(picked).toHaveBeenLastCalledWith('i150');
  });

  it('searches on the server in Arabic and keeps the chosen record while the search changes', async () => {
    const user = userEvent.setup();
    const load = loader(ALL);
    renderSelect(load, true, 'ar');
    await screen.findByRole('option', { name: 'Court 000' });
    await user.selectOptions(screen.getByLabelText('Court'), 'i3');

    await user.type(screen.getByRole('searchbox', { name: 'Search courts' }), 'القاهرة');
    await waitFor(() => { expect(options()).toContain('محكمة القاهرة الاقتصادية'); });
    expect(load).toHaveBeenLastCalledWith(0, 'القاهرة');
    expect(options()).toContain('Court 003');
    expect(screen.getByLabelText('Court')).toHaveValue('i3');
    await user.selectOptions(screen.getByLabelText('Court'), 'i205');
    expect(screen.getByLabelText('Court')).toHaveValue('i205');
  });

  it('filters the loaded records locally when the API cannot search, and says so when none match', async () => {
    const user = userEvent.setup();
    const load = loader(ALL);
    renderSelect(load, false);
    await screen.findByRole('option', { name: 'Court 000' });
    await user.type(screen.getByRole('searchbox', { name: 'Search courts' }), 'Court 042');
    expect(options()).toEqual(['Choose…', 'Court 042']);
    expect(load).toHaveBeenCalledTimes(1);
    await user.clear(screen.getByRole('searchbox', { name: 'Search courts' }));
    await user.type(screen.getByRole('searchbox', { name: 'Search courts' }), 'Court 200');
    expect(options()).toEqual(['Choose…']);
    expect(screen.getByRole('status')).toHaveTextContent('No match among the 100 loaded');
    expect(screen.getByRole('button', { name: 'Load more' })).toBeInTheDocument();
  });

  it('says the list could not be read instead of showing a short one', async () => {
    const load = vi.fn(() => Promise.reject(new Error('down')));
    render(
      <MemoryRouter>
        <LocaleProvider>
          <PagedSelect<Item>
            selectProps={{ id: 's' }} value="" onChange={() => undefined} load={load}
            optionLabel={(i) => i.name} serverSearch searchLabel="Search" chooseLabel="Choose…"
          />
        </LocaleProvider>
      </MemoryRouter>,
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be read');
  });
});
