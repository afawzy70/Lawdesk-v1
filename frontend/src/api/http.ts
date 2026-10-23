/**
 * The one HTTP client for the LawDesk API (architecture/CONTRACTS.md §3, §6): sends the session cookie, echoes the
 * CSRF cookie on unsafe requests and turns RFC 9457 problems into {@link ApiError}. Response bodies of non-JSON
 * failures are never surfaced.
 */
export interface FieldError {
  field: string;
  code: string;
}

export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  errors?: FieldError[];
  rule?: string;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem | null,
  ) {
    super(status === 0 ? 'API unreachable' : `API request failed with status ${String(status)}`);
    this.name = 'ApiError';
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
}

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

function csrfToken(): string | null {
  for (const part of document.cookie.split(';')) {
    const [name, ...value] = part.trim().split('=');
    if (name === CSRF_COOKIE && value.length > 0 && value.join('=') !== '') {
      return decodeURIComponent(value.join('='));
    }
  }
  return null;
}

function isJson(response: Response): boolean {
  return /\bjson\b/.test(response.headers.get('Content-Type') ?? '');
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers = new Headers({ Accept: 'application/json, application/problem+json' });
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  const token = method === 'GET' ? null : csrfToken();
  if (token !== null) {
    headers.set(CSRF_HEADER, token);
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? null : JSON.stringify(options.body),
      signal: options.signal ?? null,
    });
  } catch {
    throw new ApiError(0, null);
  }

  if (!response.ok) {
    const problem = isJson(response) ? ((await response.json().catch(() => null)) as Problem | null) : null;
    throw new ApiError(response.status, problem);
  }
  if (response.status === 204 || !isJson(response)) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
