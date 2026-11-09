// Low-rate authenticated API baseline on an isolated stack with at least 20 synthetic cases.
// Each iteration logs in twice and samples the major read paths. Fifteen iterations cover
// all five report exports in CSV, XLSX and PDF once each; the matrix profile repeats them.
import { sleep } from 'k6';
import { credentials, get, signIn, signOut, target } from './lib/lawdesk.js';

const BASE = target();
const ACCOUNTS = credentials();
const REPORTS = ['cases', 'judgments', 'hearings', 'experts', 'workload'];
const FORMATS = ['csv', 'xlsx', 'pdf'];

export const options = {
  scenarios: { api: { executor: 'shared-iterations', vus: 1, iterations: 15, maxDuration: '20m' } },
  thresholds: { checks: ['rate>0.99'], http_req_failed: ['rate<0.01'] },
  maxRedirects: 0,
  noCookiesReset: true,
  summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function sample(path, name, endpointClass) {
  const response = get(BASE, path, name, endpointClass);
  sleep(0.4);
  return response;
}

export default function () {
  signIn(BASE, ACCOUNTS.lawyer);
  sample('/api/v1/me', 'session validation', 'auth');
  const list = sample('/api/v1/cases?size=20', 'case list', 'case_list');
  sample('/api/v1/cases?q=SYN&size=20', 'case search', 'case_list');
  sample('/api/v1/cases?status=ACTIVE&size=20', 'case filter', 'case_list');
  const items = list.status === 200 ? list.json('items') || [] : [];
  if (items.length) {
    const id = items[__ITER % items.length].id;
    sample(`/api/v1/cases/${id}`, 'case details', 'case_details');
    sample(`/api/v1/cases/${id}/parties`, 'case parties', 'case_details');
    sample(`/api/v1/cases/${id}/proceedings`, 'case proceedings', 'proceedings');
    sample(`/api/v1/cases/${id}/judgments`, 'case judgments', 'proceedings');
    sample(`/api/v1/cases/${id}/hearings`, 'case hearings', 'hearings');
    sample(`/api/v1/cases/${id}/documents`, 'document metadata', 'documents');
  }
  sample('/api/v1/hearings?size=20', 'hearing calendar', 'hearings');
  sample('/api/v1/tasks?assignee=me&size=20', 'my tasks', 'tasks');
  signOut(BASE);

  signIn(BASE, ACCOUNTS.manager);
  sample('/api/v1/me', 'manager session', 'auth');
  sample('/api/v1/reports/dashboard', 'dashboard', 'reports');
  for (const report of REPORTS) {
    sample(`/api/v1/reports/${report}?size=20`, `${report} report`, 'reports');
  }
  const report = REPORTS[__ITER % REPORTS.length];
  const format = FORMATS[Math.floor(__ITER / REPORTS.length) % FORMATS.length];
  sample(`/api/v1/reports/${report}/export?format=${format}&lang=en`, `${report} ${format} export`, 'exports');
  signOut(BASE);
}
