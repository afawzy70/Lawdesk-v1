// Isolated contract/transfer sample: every report in every export format exactly once.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { credentials, signIn, target } from './lib/lawdesk.js';

const BASE = target();
const ACCOUNT = credentials().manager;
const sizes = new Trend('export_response_bytes');
export const options = { vus: 1, iterations: 1, maxRedirects: 0, noCookiesReset: true,
  summaryTrendStats: ['min', 'med', 'p(95)', 'p(99)', 'max'] };

export default function () {
  signIn(BASE, ACCOUNT);
  const today = new Date().toISOString().slice(0, 10);
  const twoWeeks = new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10);
  for (const kind of ['cases', 'judgments', 'hearings', 'experts', 'workload']) {
    const filter = kind === 'cases' ? '&q=SYN-000' : kind === 'judgments' ? '&from=2025-01-01&to=2025-12-31' :
      kind === 'hearings' ? `&from=${today}&to=${twoWeeks}` : '';
    for (const format of ['csv', 'xlsx', 'pdf']) {
      const name = `${kind} ${format} export`;
      const result = http.get(`${BASE}/api/v1/reports/${kind}/export?format=${format}&lang=en${filter}`,
        { responseType: 'binary', headers: { Accept: '*/*' }, tags: { name, endpoint_class: 'exports' } });
      check(result, { [`${name} 200`]: (r) => r.status === 200 });
      sizes.add(result.body ? result.body.byteLength : 0, { name });
      sleep(1);
    }
  }
}
