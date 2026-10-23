import { StrictMode } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { jsonResponse, pendingFetch, signalOf } from '../test/http';
import { ApiStatus } from './ApiStatus';

describe('ApiStatus', () => {
  it('is a polite live status region that starts in a checking state', () => {
    vi.stubGlobal('fetch', pendingFetch().fetchMock);

    render(<ApiStatus />);

    const status = screen.getByRole('status');
    expect(status).toHaveAttribute('aria-live', 'polite');
    expect(status).toHaveTextContent('Checking API…');
  });

  it('shows "API online" when the health check reports UP', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'UP' })));

    render(<ApiStatus />);

    expect(await screen.findByText('API online')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveAttribute('data-state', 'online');
  });

  it('shows "API unavailable" without exposing the error body', async () => {
    const leaky = {
      status: 'DOWN',
      error: 'Internal Server Error',
      trace: 'org.postgresql.util.PSQLException: password authentication failed for user "lawdesk"',
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(leaky, 503)));

    const { container } = render(<ApiStatus />);

    expect(await screen.findByText('API unavailable')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveAttribute('data-state', 'unavailable');
    expect(container).not.toHaveTextContent(/PSQLException|password|Internal Server Error|503/);
  });

  it('shows "API unavailable" on a network error without exposing the error message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch: ECONNREFUSED 10.0.0.5:8080')));

    const { container } = render(<ApiStatus />);

    expect(await screen.findByText('API unavailable')).toBeInTheDocument();
    expect(container).not.toHaveTextContent(/ECONNREFUSED|10\.0\.0\.5|Failed to fetch/);
  });

  it('aborts the in-flight request when unmounted', () => {
    const { fetchMock } = pendingFetch();
    vi.stubGlobal('fetch', fetchMock);

    const { unmount } = render(<ApiStatus />);
    expect(signalOf(fetchMock)?.aborted).toBe(false);
    unmount();

    expect(signalOf(fetchMock)?.aborted).toBe(true);
  });

  it('under StrictMode double-mount, aborts the first request and only the live request sets state', async () => {
    const { fetchMock, resolvers } = pendingFetch({ honourAbort: false });
    vi.stubGlobal('fetch', fetchMock);
    const consoleError = vi.spyOn(console, 'error');

    render(
      <StrictMode>
        <ApiStatus />
      </StrictMode>,
    );

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(signalOf(fetchMock, 0)?.aborted).toBe(true);
    expect(signalOf(fetchMock, 1)?.aborted).toBe(false);

    // The live request answers DOWN; the stale (aborted) one answering UP later must not win the race.
    resolvers[1]?.(jsonResponse({ status: 'DOWN' }, 503));
    expect(await screen.findByText('API unavailable')).toBeInTheDocument();
    resolvers[0]?.(jsonResponse({ status: 'UP' }));
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(screen.getByRole('status')).toHaveTextContent('API unavailable');
    expect(consoleError).not.toHaveBeenCalled();
  });
});
