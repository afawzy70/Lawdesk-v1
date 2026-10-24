import { describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../../test/http';
import { caseQuery, createCase, getCase, searchCases, updateCase } from './api';

function stubFetch(body: unknown = {}, status = 200) {
  const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body, status));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function call(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  return { url, init, body: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : null };
}

describe('caseQuery', () => {
  it('sends only the filters that are set, and page only after the first', () => {
    expect(caseQuery({})).toBe('');
    expect(caseQuery({ q: '  ', page: 0 })).toBe('');
    expect(caseQuery({ q: ' nile ', status: 'OPEN', caseType: 'CIVIL', page: 2 })).toBe(
      '?q=nile&status=OPEN&caseType=CIVIL&page=2',
    );
    expect(caseQuery({ q: '100% & co' })).toBe('?q=100%25+%26+co');
  });
});

describe('case API calls', () => {
  it('searches the case list with the query string', async () => {
    const page = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 };
    const fetchMock = stubFetch(page);

    await expect(searchCases({ q: 'delta' })).resolves.toEqual(page);

    expect(call(fetchMock).url).toBe('/api/v1/cases?q=delta');
    expect(call(fetchMock).init.method).toBe('GET');
  });

  it('reads one case by id, escaping the path segment', async () => {
    const fetchMock = stubFetch({ id: 'a/b' });

    await getCase('a/b');

    expect(call(fetchMock).url).toBe('/api/v1/cases/a%2Fb');
  });

  it('creates a case, sending empty choices as null so the server reports them as required', async () => {
    const fetchMock = stubFetch({ id: '1' }, 201);

    await createCase({ referenceNo: '', title: 'T', caseType: '', subject: '', openedOn: '' });

    const { url, init, body } = call(fetchMock);
    expect(url).toBe('/api/v1/cases');
    expect(init.method).toBe('POST');
    expect(body).toEqual({ referenceNo: null, title: 'T', caseType: null, subject: null, openedOn: null });
  });

  it('updates a case with the version it was read at', async () => {
    const fetchMock = stubFetch({ id: '1' });

    await updateCase('1', { title: 'T', caseType: 'LABOR', subject: 'S', openedOn: '2026-01-05' }, 3);

    const { url, init, body } = call(fetchMock);
    expect(url).toBe('/api/v1/cases/1');
    expect(init.method).toBe('PUT');
    expect(body).toEqual({ title: 'T', caseType: 'LABOR', subject: 'S', openedOn: '2026-01-05', version: 3 });
  });
});
