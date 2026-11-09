import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../../test/http';
import { listSourceOptions, parseLeadDays } from './api';

describe('parseLeadDays', () => {
  it('reads whole days separated by commas, Arabic commas or spaces', () => {
    expect(parseLeadDays('7, 3  1')).toEqual([7, 3, 1]);
    expect(parseLeadDays('٧')).toBeNull();
    expect(parseLeadDays('')).toEqual([]);
    expect(parseLeadDays('0،30')).toEqual([0, 30]);
  });

  it('refuses what is not a number of days', () => {
    expect(parseLeadDays('7, x')).toBeNull();
    expect(parseLeadDays('366')).toBeNull();
    expect(parseLeadDays('1.5')).toBeNull();
    expect(parseLeadDays('-1')).toBeNull();
  });
});

describe('listSourceOptions', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('is empty for free text and reads nothing', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    expect(await listSourceOptions('c1', 'TEXT', new AbortController().signal)).toEqual([]);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('names each kind of source neutrally', async () => {
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
      const path = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      const row = path.includes('judgments') ? { id: 'a', judgmentDate: '2026-03-01' } : path.includes('hearings')
        ? { id: 'b', hearingDate: '2026-02-01' } : path.includes('proceedings') ? { id: 'c', courtCaseNumber: '7', courtCaseYear: 2026 }
          : { id: 'd', title: 'Collect' };
      return Promise.resolve(jsonResponse({ items: [row], page: 0, size: 100, totalItems: 1, totalPages: 1 }));
    }));
    const signal = new AbortController().signal;
    expect(await listSourceOptions('c1', 'JUDGMENT', signal)).toEqual([{ id: 'a', label: '2026-03-01', isDate: true }]);
    expect(await listSourceOptions('c1', 'HEARING', signal)).toEqual([{ id: 'b', label: '2026-02-01', isDate: true }]);
    expect(await listSourceOptions('c1', 'PROCEEDING', signal)).toEqual([{ id: 'c', label: '7/2026', isDate: false }]);
    expect(await listSourceOptions('c1', 'TASK', signal)).toEqual([{ id: 'd', label: 'Collect', isDate: false }]);
  });
});
