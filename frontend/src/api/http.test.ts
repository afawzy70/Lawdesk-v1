import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './http';

/** architecture/CONTRACTS.md §3, §6: every API call goes through this client. */
function respond(status: number, body?: unknown, contentType = 'application/json') {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { 'Content-Type': contentType },
  });
}

describe('api client', () => {
  const fetchMock = vi.fn<typeof fetch>();
  const call = (index: number) => fetchMock.mock.calls[index] as [string, RequestInit];

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('sends the session cookie and asks for JSON', async () => {
    fetchMock.mockResolvedValue(respond(200, { ok: true }));

    await expect(api('/api/v1/me')).resolves.toEqual({ ok: true });

    const [path, init] = call(0);
    expect(path).toBe('/api/v1/me');
    expect(init.credentials).toBe('same-origin');
    expect(new Headers(init.headers).get('Accept')).toContain('application/json');
  });

  it('echoes the CSRF cookie on unsafe requests only', async () => {
    document.cookie = 'XSRF-TOKEN=abc%2D123; path=/';
    fetchMock.mockImplementation(() => Promise.resolve(respond(200, {})));

    await api('/api/v1/cases', { method: 'POST', body: { title: 'x' } });
    await api('/api/v1/cases');

    const post = new Headers(call(0)[1].headers);
    expect(post.get('X-XSRF-TOKEN')).toBe('abc-123');
    expect(post.get('Content-Type')).toBe('application/json');
    expect(call(0)[1].body).toBe('{"title":"x"}');
    expect(new Headers(call(1)[1].headers).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('sends no CSRF header when there is no token yet', async () => {
    fetchMock.mockResolvedValue(respond(204));

    await expect(api('/api/v1/auth/logout', { method: 'POST' })).resolves.toBeUndefined();

    expect(new Headers(call(0)[1].headers).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('turns problems into ApiError with status, field errors and rule', async () => {
    const problem = { status: 400, title: 'Bad Request', errors: [{ field: 'title', code: 'NotBlank' }] };
    fetchMock.mockResolvedValue(respond(400, problem, 'application/problem+json'));

    const error = await api('/api/v1/cases', { method: 'POST', body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).problem?.errors).toEqual([{ field: 'title', code: 'NotBlank' }]);
  });

  it('reports a non-JSON failure without its body', async () => {
    fetchMock.mockResolvedValue(new Response('<html>proxy error</html>', { status: 502 }));

    const error = (await api('/api/v1/cases').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.problem).toBeNull();
    expect(error.message).not.toContain('proxy error');
  });

  it('ignores other cookies and a malformed problem body', async () => {
    document.cookie = 'theme=dark; path=/';
    fetchMock.mockResolvedValue(new Response('{', { status: 409, headers: { 'Content-Type': 'application/problem+json' } }));

    const error = (await api('/api/v1/cases', { method: 'DELETE' }).catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(409);
    expect(error.problem).toBeNull();
    expect(new Headers(call(0)[1].headers).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('reports a network failure as status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = (await api('/api/v1/cases').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
  });
});
