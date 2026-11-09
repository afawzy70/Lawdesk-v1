// Copy into frontend/src/qa/ in an isolated candidate; imports target REAL app modules.
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { ApiStatus } from '../components/ApiStatus';
import { App } from '../App';
import { fetchApiHealth } from '../api/health';

afterEach(() => { window.history.replaceState({}, '', '/'); });
const response = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});

describe('Independent LD-003 negative and UI assertions', () => {
  test.each([401, 403, 404, 500, 503])('QA-0024: HTTP %s cannot render a false online state', async status => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(status, { status: 'UP', detail: 'QA-PRIVATE-MARKER' })));
    render(<ApiStatus />);
    expect(await screen.findByText('API unavailable')).toBeInTheDocument();
    expect(screen.queryByText('API online')).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain('QA-PRIVATE-MARKER');
  });

  test.each([{}, { status: null }, { status: 'DOWN' }, { status: ['UP'] }])('QA-0018/19: malformed health %j is unavailable', async body => {
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response(200,body)));
    expect(await fetchApiHealth()).toBe('unavailable');
  });

  test('QA-0023: unmount aborts actual fetch signal', () => {
    let signal: AbortSignal | undefined;
    vi.stubGlobal('fetch',vi.fn((_url: string, init: RequestInit) => {
      signal=init.signal as AbortSignal;
      return new Promise<Response>(() => {});
    }));
    const view=render(<ApiStatus />);
    expect(signal?.aborted).toBe(false);
    view.unmount();
    expect(signal?.aborted).toBe(true);
  });

  test('QA-0017/19: unknown route recovers inside one accessible shell', async () => {
    window.history.replaceState({},'', '/qa-unknown-route');
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response(200,{status:'UP'})));
    render(<App locale="ar-EG" />);
    expect(screen.getAllByRole('main')).toHaveLength(1);
    expect(screen.getByRole('heading',{name:'Page not found'})).toBeInTheDocument();
    expect(screen.getByRole('link',{name:'Go to the home page'})).toHaveAttribute('href','/');
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(await screen.findByText('API online')).toBeInTheDocument();
  });
});
