// LD-040 shared helpers for the LawDesk k6 scenarios (k6 v2.3.0, docs/performance/PERFORMANCE-BASELINE.md §3).
//
// SAFETY: these scripts are for an ISOLATED, resource-bounded LawDesk instance with synthetic data only. They refuse
// to start against the shared Contabo UAT host or anything Matbakhy, and need LAWDESK_K6_ISOLATED=yes as the
// operator's explicit statement that the target is isolated (§6). Credentials come from a JSON file whose path is
// given in CREDENTIALS_FILE; they are never logged.
import http from 'k6/http';
import { check, fail } from 'k6';

// Hosts that must never receive load: the shared UAT host (sslip.io names of 167.86.90.174) and Matbakhy.
const FORBIDDEN = [/167[-.]86[-.]90[-.]174/, /sslip\.io/i, /matbakhy/i];

export function target() {
  const base = (__ENV.BASE_URL || '').replace(/\/+$/, '');
  if (!/^https?:\/\/[^/]+/.test(base)) {
    fail('BASE_URL is required, e.g. BASE_URL=http://127.0.0.1:18080');
  }
  if (FORBIDDEN.some((pattern) => pattern.test(base))) {
    fail(`refusing ${base}: shared UAT and Matbakhy hosts never receive load tests (PERFORMANCE-BASELINE §6)`);
  }
  if (__ENV.LAWDESK_K6_ISOLATED !== 'yes') {
    fail('set LAWDESK_K6_ISOLATED=yes only for an isolated, resource-bounded target with synthetic data');
  }
  return base;
}

// Reads {"accounts": {"lawyer": {"username", "password"}, "staff": {...}, "manager": {...}, "admin": {...}}}.
// Must be called from the init context (k6 open()).
export function credentials() {
  const path = __ENV.CREDENTIALS_FILE;
  if (!path) {
    fail('CREDENTIALS_FILE is required (a JSON file of synthetic test accounts; never real users)');
  }
  const parsed = JSON.parse(open(path));
  return parsed.accounts || parsed;
}

function xsrf(base) {
  const jar = http.cookieJar();
  const cookies = jar.cookiesForURL(base);
  return cookies['XSRF-TOKEN'] ? cookies['XSRF-TOKEN'][0] : undefined;
}

// A signed-in session for this VU; k6 keeps one cookie jar per VU, so JSESSIONID and XSRF-TOKEN persist.
export function signIn(base, account) {
  http.get(`${base}/api/v1/auth/csrf`, { tags: { name: 'auth csrf', endpoint_class: 'auth' } });
  const response = http.post(`${base}/api/v1/auth/login`,
    JSON.stringify({ login: account.username, password: account.password }),
    {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf(base) || '' },
      tags: { name: 'auth login', endpoint_class: 'auth' },
    });
  if (!check(response, { 'signed in': (r) => r.status === 200 })) {
    fail(`sign-in refused for a synthetic account (status ${response.status})`);
  }
  // The token rotates at sign-in; fetch the new one for later writes.
  http.get(`${base}/api/v1/auth/csrf`, { tags: { name: 'auth csrf', endpoint_class: 'auth' } });
}

export function get(base, path, name, endpointClass) {
  const response = http.get(`${base}${path}`, {
    headers: { Accept: 'application/json' },
    tags: { name, endpoint_class: endpointClass },
  });
  check(response, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return response;
}

export function send(base, method, path, body, name, endpointClass) {
  const response = http.request(method, `${base}${path}`, body === undefined ? null : JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-XSRF-TOKEN': xsrf(base) || '' },
    tags: { name, endpoint_class: endpointClass },
  });
  check(response, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return response;
}

export function upload(base, path, fileName, bytes, name) {
  const response = http.post(`${base}${path}`, {
    title: `SYN ${fileName}`,
    file: http.file(bytes, fileName, 'application/pdf'),
  }, {
    headers: { 'X-XSRF-TOKEN': xsrf(base) || '' },
    tags: { name, endpoint_class: 'documents' },
  });
  check(response, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return response;
}

export function signOut(base) {
  http.post(`${base}/api/v1/auth/logout`, null, {
    headers: { 'X-XSRF-TOKEN': xsrf(base) || '' },
    tags: { name: 'auth logout', endpoint_class: 'auth' },
  });
}

// Phase 5/6 endpoints exist only once their pull requests are merged; enable them with FEATURES=hearings,documents,...
export function enabled(feature) {
  return (__ENV.FEATURES || '').split(',').map((f) => f.trim()).includes(feature);
}

export function pick(items) {
  return items[Math.floor(Math.random() * items.length)];
}

// Initial targets (ASSUMPTIONS for owner review, PERFORMANCE-BASELINE §2): p95/p99 in ms per endpoint class.
export const TARGETS = {
  auth: [800, 1500],
  case_list: [500, 1000],
  case_details: [300, 600],
  proceedings: [300, 600],
  hearings: [400, 800],
  documents: [1500, 3000],
  reports: [1500, 3000],
  exports: [5000, 10000],
};

export function thresholds(abortOnFail) {
  const result = {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail, delayAbortEval: '1m' }],
    checks: ['rate>0.99'],
  };
  for (const [endpointClass, [p95, p99]] of Object.entries(TARGETS)) {
    result[`http_req_duration{endpoint_class:${endpointClass}}`] = [
      `p(95)<${p95}`,
      // Abort when an endpoint class is twice as slow as its p99 target: the target is overloaded (§6).
      { threshold: `p(99)<${p99 * 2}`, abortOnFail, delayAbortEval: '1m' },
    ];
  }
  return result;
}
