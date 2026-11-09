import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { jsonResponse } from '../../test/http';
import type { LegalCase } from './api';
import { CaseStatusForm } from './CaseStatusForm';

/** Closing a case with open items (section 33): the items are shown and closing needs an explicit confirmation. */

const CASE = { id: 'c1', status: 'ACTIVE', version: 3 } as unknown as LegalCase;
const problem = (status: number, body: object) => jsonResponse(body, status);

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

function setup(responder: (url: string, body: unknown) => Response) {
  const calls: { url: string; body: unknown }[] = [];
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : null;
    calls.push({ url, body });
    return Promise.resolve(responder(url, body));
  }));
  const onChanged = vi.fn();
  render(
    <MemoryRouter>
      <LocaleProvider>
        <CaseStatusForm legalCase={CASE} onChanged={onChanged} onReload={vi.fn()} />
      </LocaleProvider>
    </MemoryRouter>,
  );
  return { calls, onChanged };
}

describe('closing a case with open items', () => {
  it('lists the open items, then closes only after the confirmation', async () => {
    const user = userEvent.setup();
    const { calls, onChanged } = setup((url, body) => {
      if (url.endsWith('/closing-check')) return jsonResponse({ findings: [{ code: 'UNFINISHED_SETTLEMENT', count: 1 }, { code: 'OPEN_TASK', count: 2 }] });
      const acknowledged = (body as { acknowledgeOpenItems?: boolean } | null)?.acknowledgeOpenItems === true;
      return acknowledged ? jsonResponse({}) : problem(422, { rule: 'CLOSING_OPEN_ITEMS_NOT_ACKNOWLEDGED' });
    });
    await user.selectOptions(screen.getByLabelText('New status'), 'CLOSED');
    await user.click(screen.getByRole('button', { name: 'Change status' }));
    const alert = await screen.findByRole('alert', { name: /open items/ });
    expect(alert).toHaveTextContent('1 settlement(s) not yet executed');
    expect(alert).toHaveTextContent('2 open task(s)');
    expect(onChanged).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Close the case anyway' }));
    await waitFor(() => { expect(onChanged).toHaveBeenCalled(); });
    expect(calls.at(-1)?.body).toMatchObject({ status: 'CLOSED', acknowledgeOpenItems: true });
  });

  it('shows a plain failure when the open items cannot be read', async () => {
    const user = userEvent.setup();
    setup((url) => (url.endsWith('/closing-check') ? problem(500, {}) : problem(422, { rule: 'CLOSING_OPEN_ITEMS_NOT_ACKNOWLEDGED' })));
    await user.selectOptions(screen.getByLabelText('New status'), 'CLOSED');
    await user.click(screen.getByRole('button', { name: 'Change status' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Close the case anyway' })).not.toBeInTheDocument();
  });
});
