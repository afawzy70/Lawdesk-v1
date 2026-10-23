/** How long the shell waits for `/actuator/health` before treating the API as unavailable. */
export const HEALTH_TIMEOUT_MS = 5000;

export const HEALTH_PATH = '/actuator/health';

export type ApiHealth = 'online' | 'unavailable';

function isUp(body: unknown): boolean {
  return typeof body === 'object' && body !== null && (body as { status?: unknown }).status === 'UP';
}

/**
 * Checks backend health. Never throws and never exposes response bodies or error details:
 * any non-2xx status, network failure, timeout, abort or unexpected payload is reported as `unavailable`.
 */
export async function fetchApiHealth(signal?: AbortSignal): Promise<ApiHealth> {
  if (signal?.aborted) {
    return 'unavailable';
  }

  const controller = new AbortController();
  const abort = () => {
    controller.abort();
  };
  const timer = setTimeout(abort, HEALTH_TIMEOUT_MS);
  signal?.addEventListener('abort', abort, { once: true });

  try {
    const response = await fetch(HEALTH_PATH, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
      signal: controller.signal,
    });
    if (!response.ok || response.redirected) {
      return 'unavailable';
    }
    return isUp(await response.json()) ? 'online' : 'unavailable';
  } catch {
    return 'unavailable';
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}
