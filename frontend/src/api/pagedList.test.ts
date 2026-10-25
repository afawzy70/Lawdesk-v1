import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../test/http';
import { listAll, MAX_LIST_PAGES } from './pagedList';

/** QAD-044: a list is read page by page up to the contract's size cap; it is never silently cut short. */

const items = (from: number, count: number) => Array.from({ length: count }, (_, i) => ({ id: `r${String(from + i)}` }));

function serve(total: number) {
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://x');
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');
    const from = page * size;
    return Promise.resolve(
      jsonResponse({
        items: items(from, Math.max(0, Math.min(size, total - from))),
        page,
        size,
        totalItems: total,
        totalPages: Math.ceil(total / size),
      }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const urls = (fetchMock: ReturnType<typeof serve>) => fetchMock.mock.calls.map(([input]) => (typeof input === 'string' ? input : ''));

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('listAll', () => {
  it('reads one page when everything fits', async () => {
    const fetchMock = serve(100);
    const all = await listAll<{ id: string }>('/api/v1/things', { sort: 'code,asc' }, new AbortController().signal);
    expect(all).toHaveLength(100);
    expect(urls(fetchMock)).toEqual(['/api/v1/things?page=0&size=100&sort=code%2Casc']);
  });

  it('reads the 101st record from the second page, in order and without duplicates', async () => {
    const fetchMock = serve(101);
    const all = await listAll<{ id: string }>('/api/v1/things', {}, new AbortController().signal);
    expect(all).toHaveLength(101);
    expect(all[100]?.id).toBe('r100');
    expect(new Set(all.map((r) => r.id)).size).toBe(101);
    expect(urls(fetchMock)).toEqual(['/api/v1/things?page=0&size=100', '/api/v1/things?page=1&size=100']);
  });

  it('follows three pages', async () => {
    serve(250);
    expect(await listAll('/api/v1/things', {}, new AbortController().signal)).toHaveLength(250);
  });

  it('refuses a list too long to read rather than truncating it', async () => {
    serve((MAX_LIST_PAGES + 1) * 100);
    await expect(listAll('/api/v1/things', {}, new AbortController().signal)).rejects.toThrow(/too long/i);
  });
});
