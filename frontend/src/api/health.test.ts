import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, pendingFetch, signalOf } from '../test/http';
import { HEALTH_TIMEOUT_MS, fetchApiHealth } from './health';

afterEach(() => {
  vi.useRealTimers();
});

describe('fetchApiHealth', () => {
  it('requests GET /actuator/health as JSON without sending credentials cross-origin', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ status: 'UP' }));
    vi.stubGlobal('fetch', fetchMock);

    await fetchApiHealth();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/actuator/health');
    expect(init.method).toBe('GET');
    expect(init.credentials).toBe('same-origin');
    expect(new Headers(init.headers).get('Accept')).toBe('application/json');
    expect(init.signal).toBeInstanceOf(AbortSignal);
  });

  it('reports online when the API answers {"status":"UP"}', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'UP' })));
    await expect(fetchApiHealth()).resolves.toBe('online');
  });

  it.each([
    ['DOWN', { status: 'DOWN' }],
    ['OUT_OF_SERVICE', { status: 'OUT_OF_SERVICE' }],
    ['missing status', { components: {} }],
    ['non-string status', { status: true }],
    ['null body', null],
    ['array body', ['UP']],
  ])('reports unavailable for a 200 response with %s', async (_label, body) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(body)));
    await expect(fetchApiHealth()).resolves.toBe('unavailable');
  });

  it.each([301, 401, 403, 404, 500, 503])('reports unavailable for HTTP %i', async (status) => {
    // Spring returns 503 with {"status":"DOWN"} when unhealthy; other errors may carry stack traces.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ status: 'UP', trace: 'java.lang.IllegalStateException' }, status)),
    );
    await expect(fetchApiHealth()).resolves.toBe('unavailable');
  });

  it('reports unavailable on a network error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(fetchApiHealth()).resolves.toBe('unavailable');
  });

  it('reports unavailable for malformed JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('<html>502 Bad Gateway</html>', {
          status: 200,
          headers: { 'Content-Type': 'text/html' },
        }),
      ),
    );
    await expect(fetchApiHealth()).resolves.toBe('unavailable');
  });

  it(`aborts and reports unavailable when the API does not answer within ${String(HEALTH_TIMEOUT_MS)} ms`, async () => {
    vi.useFakeTimers();
    const { fetchMock } = pendingFetch();
    vi.stubGlobal('fetch', fetchMock);

    const result = fetchApiHealth();
    await vi.advanceTimersByTimeAsync(HEALTH_TIMEOUT_MS - 1);
    expect(signalOf(fetchMock)?.aborted).toBe(false);

    await vi.advanceTimersByTimeAsync(1);
    expect(signalOf(fetchMock)?.aborted).toBe(true);
    await expect(result).resolves.toBe('unavailable');
  });

  it('uses a timeout of about five seconds', () => {
    expect(HEALTH_TIMEOUT_MS).toBe(5000);
  });

  it('aborts the request when the caller signal aborts and clears its timer', async () => {
    vi.useFakeTimers();
    const { fetchMock } = pendingFetch();
    vi.stubGlobal('fetch', fetchMock);
    const caller = new AbortController();

    const result = fetchApiHealth(caller.signal);
    caller.abort();

    await expect(result).resolves.toBe('unavailable');
    expect(signalOf(fetchMock)?.aborted).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('does not call the API when the caller signal is already aborted', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const caller = new AbortController();
    caller.abort();

    await expect(fetchApiHealth(caller.signal)).resolves.toBe('unavailable');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('clears its timeout after a successful response', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'UP' })));

    await expect(fetchApiHealth()).resolves.toBe('online');
    expect(vi.getTimerCount()).toBe(0);
  });
});
