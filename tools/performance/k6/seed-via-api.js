// LD-040 synthetic-data seeding through the PUBLIC API only (docs/performance/PERFORMANCE-BASELINE.md §3.3). k6 v2.3.0.
//
// NOT FOR UAT. Isolated instance only (lib/lawdesk.js refuses UAT and Matbakhy). Every record is marked SYN. It is
// slow on purpose (bounded VUs): seeding is not a load test. For the full volumes (10 000 cases) prefer the isolated
// database path, tools/performance/db/seed-*.sql, on a throwaway database.
//
//   k6 run -e LAWDESK_K6_ISOLATED=yes -e BASE_URL=http://127.0.0.1:18080 \
//     -e CREDENTIALS_FILE=/path/synthetic-accounts.json -e CASES=500 -e FEATURES=hearings \
//     tools/performance/k6/seed-via-api.js
//
// Per case: 2 parties, 3 first-instance proceedings, an appeal of every second one, a judgment on 60 %, and (with
// FEATURES=hearings) two hearings per proceeding. The admin account creates the 60 synthetic courts once (setup()).
import { check, fail, sleep } from 'k6';
import { credentials, enabled, get, send, signIn, target } from './lib/lawdesk.js';

const BASE = target();
const ACCOUNTS = credentials();
const CASES = parseInt(__ENV.CASES || '100', 10);
const SEED_VUS = Math.min(4, Math.max(1, parseInt(__ENV.SEED_VUS || '4', 10)));

export const options = {
  scenarios: {
    seed: { executor: 'shared-iterations', vus: Math.min(SEED_VUS, CASES), iterations: CASES, maxDuration: '6h' },
  },
  noCookiesReset: true,
  thresholds: { checks: ['rate>0.99'] },
};

export function setup() {
  signIn(BASE, ACCOUNTS.admin);
  const existing = get(BASE, '/api/v1/courts?size=100', 'courts list', 'proceedings');
  const have = new Set((existing.json('items') || []).map((c) => c.code));
  for (let g = 1; g <= 60; g++) {
    const code = `SYN-${g}`;
    if (have.has(code)) {
      continue;
    }
    const level = g <= 30 ? 'FIRST_INSTANCE' : g <= 50 ? 'APPEAL' : 'CASSATION';
    send(BASE, 'POST', '/api/v1/courts', { code, nameAr: `محكمة تجريبية ${g}`, nameEn: `SYN court ${g}`, level },
      'court create', 'proceedings');
  }
  const courts = get(BASE, '/api/v1/courts?size=100', 'courts list', 'proceedings').json('items') || [];
  const byLevel = (level) => courts.filter((c) => c.level === level && c.code.startsWith('SYN-')).map((c) => c.id);
  return { firstInstance: byLevel('FIRST_INSTANCE'), appeal: byLevel('APPEAL') };
}

let signedIn = false;

export default function (courts) {
  if (!signedIn) {
    // Seeding writes judgments, so it needs JUDGMENT_MANAGE: the manager account.
    signIn(BASE, ACCOUNTS.manager);
    signedIn = true;
  }
  if (!courts.firstInstance.length || !courts.appeal.length) {
    fail('setup() found no synthetic courts');
  }
  const n = __ITER * 10 + __VU;
  const opened = new Date(Date.UTC(2015, 0, 1) + (n * 86400000 * 37) % (11 * 365 * 86400000));
  const openedOn = opened.toISOString().slice(0, 10);
  const created = send(BASE, 'POST', '/api/v1/cases', {
    title: `SYN case ${n}`, caseType: ['CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE'][n % 6],
    subject: 'SYN', openedOn,
  }, 'case create', 'case_details');
  if (!check(created, { 'case created': (r) => r.status === 201 })) {
    return;
  }
  const caseId = created.json('id');
  for (let k = 0; k < 2; k++) {
    const party = send(BASE, 'POST', '/api/v1/parties', { kind: 'ORGANIZATION', name: `SYN party ${n}-${k}` },
      'party create', 'case_details');
    if (party.status === 201) {
      send(BASE, 'POST', `/api/v1/cases/${caseId}/parties`, { partyId: party.json('id'), role: k ? 'OPPONENT' : 'CLIENT' },
        'case party link', 'case_details');
    }
  }
  const year = opened.getUTCFullYear();
  for (let k = 0; k < 3; k++) {
    const filing = new Date(opened.getTime() + k * 30 * 86400000).toISOString().slice(0, 10);
    const first = send(BASE, 'POST', `/api/v1/cases/${caseId}/proceedings`, {
      stage: 'FIRST_INSTANCE', courtId: courts.firstInstance[(n + k) % courts.firstInstance.length],
      courtCaseNumber: `SYN${n}-${k}`, courtCaseYear: year, filingDate: filing,
    }, 'proceeding create', 'proceedings');
    if (first.status !== 201) {
      continue;
    }
    const proceedings = [first.json('id')];
    if (k % 2 === 0) {
      const appeal = send(BASE, 'POST', `/api/v1/cases/${caseId}/proceedings`, {
        stage: 'APPEAL', courtId: courts.appeal[(n + k) % courts.appeal.length], courtCaseNumber: `SYN${n}-${k}-A`,
        courtCaseYear: year, filingDate: filing, parentProceedingId: first.json('id'),
      }, 'proceeding create', 'proceedings');
      if (appeal.status === 201) {
        proceedings.push(appeal.json('id'));
      }
    }
    if ((n + k) % 5 < 3) {
      send(BASE, 'POST', `/api/v1/proceedings/${first.json('id')}/judgment`, {
        judgmentDate: new Date(Math.min(Date.parse(filing) + 90 * 86400000, Date.now())).toISOString().slice(0, 10),
        outcome: ['IN_FAVOUR', 'AGAINST', 'PARTIAL', 'DISMISSED', 'OTHER'][(n + k) % 5],
        summary: 'SYN', final: false,
      }, 'judgment record', 'proceedings');
    }
    if (enabled('hearings')) {
      for (const id of proceedings) {
        for (let h = 1; h <= 2; h++) {
          const day = new Date(Date.now() + ((n * 7 + h * 11) % 300) * 86400000).toISOString().slice(0, 10);
          send(BASE, 'POST', `/api/v1/proceedings/${id}/hearings`, { hearingDate: day, purpose: 'PLEADING' },
            'hearing schedule', 'hearings');
        }
      }
    }
  }
  sleep(1);
}
