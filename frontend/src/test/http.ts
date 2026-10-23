import { vi } from 'vitest';

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * A fetch mock whose requests stay pending until resolved through `resolvers`. With `honourAbort` (the
 * default) a request rejects with AbortError like the browser when its signal aborts; without it the
 * response can still arrive after the abort, which simulates a stale reply racing a newer request.
 */
export function pendingFetch({ honourAbort = true }: { honourAbort?: boolean } = {}) {
  const resolvers: ((response: Response) => void)[] = [];
  const fetchMock = vi.fn(
    (_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((resolve, reject) => {
        resolvers.push(resolve);
        if (!honourAbort) return;
        init?.signal?.addEventListener('abort', () => {
          reject(new DOMException('The operation was aborted.', 'AbortError'));
        });
      }),
  );
  return { fetchMock, resolvers };
}

/** The AbortSignal passed to the n-th fetch call. */
export function signalOf(fetchMock: ReturnType<typeof vi.fn>, call = 0): AbortSignal | undefined {
  const init = fetchMock.mock.calls[call]?.[1] as RequestInit | undefined;
  return init?.signal ?? undefined;
}
