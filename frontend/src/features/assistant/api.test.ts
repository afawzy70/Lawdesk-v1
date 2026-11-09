import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../../test/http';
import { ask, getStatus, listIntegrations, listOutputs, reviewOutput } from './api';

describe('the assistant API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('calls the documented paths', async () => {
    const calls: { url: string; method: string; body: unknown }[] = [];
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ url: typeof input === 'string' ? input : 'other', method: init?.method ?? 'GET', body: typeof init?.body === 'string' ? JSON.parse(init.body) : null });
      return Promise.resolve(jsonResponse([]));
    }));
    await getStatus();
    await getStatus(new AbortController().signal);
    await ask({ task: 'QUESTION', question: 'q' });
    await listOutputs('a b');
    await listOutputs('c', new AbortController().signal);
    await reviewOutput('o 1', 'DISCARDED');
    await listIntegrations();
    await listIntegrations(new AbortController().signal);
    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual([
      'GET /api/v1/assistant/status',
      'GET /api/v1/assistant/status',
      'POST /api/v1/assistant/ask',
      'GET /api/v1/assistant/outputs?caseId=a%20b',
      'GET /api/v1/assistant/outputs?caseId=c',
      'POST /api/v1/assistant/outputs/o%201/review',
      'GET /api/v1/integrations',
      'GET /api/v1/integrations',
    ]);
    expect(calls[2]?.body).toEqual({ task: 'QUESTION', question: 'q' });
    expect(calls[5]?.body).toEqual({ decision: 'DISCARDED' });
  });
});
