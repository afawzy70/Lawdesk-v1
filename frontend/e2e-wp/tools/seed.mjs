// Seeds a LOCAL throwaway instance (never UAT) with SYNTH-DOC data and a manager account holding every permission and
// the department-wide scope. Reads the bootstrap admin password from UX_ADMIN_PW_FILE; writes the manager credentials
// (random, local only) to UX_CREDS_FILE (mode 600, outside the repo). Prints no secrets.
import fs from 'node:fs';
import crypto from 'node:crypto';

const base = process.env.UX_BASE ?? 'http://localhost:8080';
const adminPw = fs.readFileSync(process.env.UX_ADMIN_PW_FILE, 'utf8').trim();

class Session {
  cookies = new Map();
  async call(method, path, body) {
    const headers = { 'content-type': 'application/json', cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ') };
    const xsrf = this.cookies.get('XSRF-TOKEN');
    if (method !== 'GET' && xsrf) headers['x-xsrf-token'] = decodeURIComponent(xsrf);
    const r = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    for (const c of r.headers.getSetCookie()) {
      const [kv] = c.split(';');
      const i = kv.indexOf('=');
      this.cookies.set(kv.slice(0, i), kv.slice(i + 1));
    }
    const text = await r.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* empty */ }
    return { status: r.status, body: json };
  }
  async login(login, password) {
    await this.call('GET', '/api/v1/auth/csrf');
    return this.call('POST', '/api/v1/auth/login', { login, password });
  }
}

const rnd = () => crypto.randomBytes(9).toString('base64url') + 'aA1!';
const must = (label, r, ok = [200, 201, 204]) => {
  if (!ok.includes(r.status)) throw new Error(`${label}: HTTP ${r.status} ${JSON.stringify(r.body)?.slice(0, 300)}`);
  return r.body;
};

let mgr2;
let user;
if (fs.existsSync(process.env.UX_CREDS_FILE)) {
  // Re-run: the manager already exists (the bootstrap password was rotated on the first run).
  const creds = JSON.parse(fs.readFileSync(process.env.UX_CREDS_FILE, 'utf8'));
  mgr2 = new Session();
  must('mgr login', await mgr2.login(creds.username, creds.password));
  user = must('me', await mgr2.call('GET', '/api/v1/me'));
} else {
  const admin = new Session();
  let r = await admin.login('ux.admin', adminPw);
  let adminPass = adminPw;
  if (r.status === 200) {
    const me = (await admin.call('GET', '/api/v1/me')).body;
    if (me === null || me.permissions === undefined) {
      // password change required
    }
  }
  const changed = rnd();
  r = await admin.call('POST', '/api/v1/me/password', { currentPassword: adminPass, newPassword: changed });
  if (r.status !== 204 && r.status !== 200) throw new Error(`admin password change: ${r.status} ${JSON.stringify(r.body)}`);
  adminPass = changed;
  const admin2 = new Session();
  must('admin login', await admin2.login('ux.admin', adminPass));
  const me = must('me', await admin2.call('GET', '/api/v1/me'));

  const mgrPw = rnd();
  const mgrTmp = rnd();
  must('role', await admin2.call('POST', '/api/v1/roles', { code: 'UXALL', name: 'SYNTH-DOC all permissions', permissions: me.permissions.filter((p) => p !== 'ADMIN') }), [200, 201, 409]);
  const roleList = await admin2.call('GET', '/api/v1/roles');
  user = must('user', await admin2.call('POST', '/api/v1/users', { username: 'ux.mgr', email: 'ux.mgr@example.test', displayName: 'SYNTH-DOC مدير اختبار', locale: 'ar', roles: ['UXALL', 'LEGAL_MANAGER'], temporaryPassword: mgrTmp }));
  must('scope', await admin2.call('POST', '/api/v1/access-scopes', { subjectType: 'ROLE', subjectRef: 'UXALL', scopeType: 'ALL', scopeRef: null }));
  const mgr = new Session();
  must('mgr login', await mgr.login('ux.mgr', mgrTmp));
  must('mgr pw', await mgr.call('POST', '/api/v1/me/password', { currentPassword: mgrTmp, newPassword: mgrPw }));
  mgr2 = new Session();
  must('mgr relogin', await mgr2.login('ux.mgr', mgrPw));

  fs.writeFileSync(process.env.UX_CREDS_FILE, JSON.stringify({ username: 'ux.mgr', password: mgrPw }), { mode: 0o600 });


}

// --- synthetic content -------------------------------------------------------------------------------------------
const stamp = Date.now().toString(36);
const dept = must('dept', await mgr2.call('POST', '/api/v1/org-units', { tree: 'LEGAL', parentId: null, level: 'DEPARTMENT', nameAr: `الإدارة القانونية SYNTH-DOC ${stamp}` }), [200, 201]);
const unit = must('section', await mgr2.call('POST', '/api/v1/org-units', { tree: 'LEGAL', parentId: dept.id, level: 'SECTION', nameAr: `قسم القضايا SYNTH-DOC ${stamp}` }), [200, 201]);
must('place', await mgr2.call('PUT', `/api/v1/users/${user.id}/org-unit`, { orgUnitId: unit.id }));
const court = must('court', await mgr2.call('POST', '/api/v1/courts', { code: 'SYN-UX', nameAr: 'محكمة اختبارية SYNTH-DOC', nameEn: 'Synthetic court', level: 'FIRST_INSTANCE' }), [200, 201, 409]);
const courts = must('courts', await mgr2.call('GET', '/api/v1/courts?size=100'));
const courtId = courts.items.find((c) => c.level === 'FIRST_INSTANCE').id;
const today = new Date().toISOString().slice(0, 10);
const ids = [];
for (let i = 1; i <= 24; i++) {
  const res = await mgr2.call('POST', '/api/v1/cases/intake', {
    automatedNo: `UX-AUTO-${i}`, title: `SYNTH-DOC قضية اختبارية رقم ${i} بعنوان طويل نسبيًا لاختبار القص في الجداول والبطاقات`,
    caseType: i % 2 ? 'ADMINISTRATIVE' : 'CIVIL', subject: 'SYNTH-DOC: طعن اختباري على قرار إداري', openedOn: today,
    firstInstance: { courtId, courtCaseNumber: String(9000 + i), courtCaseYear: 2026, filingDate: today },
    authorityPosition: 'DEFENDANT',
    parties: [{ newParty: { kind: 'PERSON', name: `مدعٍ اختباري ${i}`, nationalId: null }, side: 'PLAINTIFF' }],
    primaryMemberId: user.id, alternateMemberId: null, financialImpact: 'YES', financialAmount: '1500.250',
    details: { chamber: 'الدائرة الإدارية الثالثة', receivedOn: today, facts: 'ملخص وقائع اختباري', claimedAmount: '12500.500', importance: 'HIGH', internalNotes: 'بيانات اصطناعية' },
  });
  if (res.status === 201) ids.push(res.body.id);
  else if (i === 1) throw new Error(`intake: ${res.status} ${JSON.stringify(res.body).slice(0, 300)}`);
}
fs.writeFileSync(process.env.UX_CASES_FILE, JSON.stringify(ids));
console.log(`seeded ${ids.length} cases`);
