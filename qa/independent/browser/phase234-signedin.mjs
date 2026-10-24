// Real authenticated UAT. No mocks, deployment, bootstrap credential discovery or customer-record writes.
// Credentials: approved owner-only JSON file {"username": ..., "password": ...}, supplied via
// LAWDESK_QA_CREDENTIAL_FILE. Never pass secrets as command arguments. See SIGNED-IN-UAT.md.
import fs from 'node:fs';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
const require = createRequire('/tmp/lawdesk-qa-browser-runtime/package.json');
const { chromium } = require('playwright');
const [base, sha, output] = process.argv.slice(2);
if (base !== 'https://lawdesk.167-86-90-174.sslip.io' || !/^[a-f0-9]{40}$/.test(sha || '') || !output) {
  throw new Error('Require approved UAT URL, full baseline SHA and new evidence directory');
}
fs.mkdirSync(output, { recursive: true });
if (fs.existsSync(output + '/results.json')) throw new Error('Refusing to overwrite previous execution');
const prefix = 'QA' + new Date().toISOString().replace(/\D/g, '').slice(2, 14) + crypto.randomBytes(3).toString('hex').toUpperCase();
const started = new Date().toISOString();
const tests = [], owned = {}, secrets = [];
const password = () => { const p = crypto.randomBytes(24).toString('base64url') + '!9a'; secrets.push(p); return p; };
const temporary = password(), changed = password();
let admin, viewer, page, browser, primary, role, user, legalCase, first, appeal, judgment, appealJudgment;
let authenticated = false;
const courts = {};
class Blocked extends Error {}
function demand(value, why) { if (!value) throw new Blocked(why); return value; }
function assert(value, message) { if (!value) throw new Error(message); }
function safe(value) {
  let text = JSON.stringify(value);
  for (const secret of secrets) text = text.split(secret).join('[REDACTED]');
  return JSON.parse(text);
}
function save() {
  fs.writeFileSync(output + '/results.json', JSON.stringify(safe({ started, updated: new Date().toISOString(),
    target: base, baseline_sha: sha, backend_runtime_sha_attested: false,
    deployment_identity: 'Compare separate frontend digest and deployment-job evidence; baseline is not backend runtime attestation',
    prefix, scope: 'Independent signed-in Chromium and live HTTP; no developer tests counted',
    counts: Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s => [s, tests.filter(t => t.status === s).length])),
    owned_fixture_ids: owned, tests: tests.map(({ run, ...t }) => t) }), null, 2) + '\n');
}
function test(id, stories, steps, expected, run, dependencies = []) {
  tests.push({ id: 'SIGNED-' + id, story_ids: stories.split(','), priority: 'P1',
    preconditions: ['Exact deployment identity checked separately', 'Approved dedicated QA account; valid HTTPS/CSRF',
      'Mutate only resources created with this execution prefix', ...dependencies],
    test_data: { prefix, dates: '2026-01-02 through 2026-01-10', credentials: 'Generated in memory; never evidence' },
    steps: steps.split(' -> '), expected_results: expected, status: 'NOT_RUN', run });
}
function own(kind, entity) {
  demand(entity?.id, 'Creation did not supply resource ID');
  (owned[kind] ??= []).push(entity.id); save(); return entity;
}
function ownId(kind, entity) {
  demand(entity?.id && owned[kind]?.includes(entity.id), 'Missing owned ' + kind + ' fixture'); return entity.id;
}
async function api(context, method, path, body, expected = 200, csrf = true) {
  demand(context, 'Required authenticated context unavailable');
  const headers = { 'X-Request-Id': prefix };
  if (method !== 'GET' && csrf) {
    await context.request.get(base + '/api/v1/auth/csrf');
    const token = (await context.cookies(base)).find(c => c.name === 'XSRF-TOKEN')?.value;
    demand(token, 'CSRF cookie unavailable'); headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
  }
  const response = await context.request.fetch(base + path, { method, headers, ...(body === undefined ? {} : { data: body }) });
  let data; try { data = await response.json(); } catch { data = null; }
  // Never retain raw response or request body: they can contain authentication or unrelated personal data.
  assert(response.status() === expected, `${method} ${path}: expected ${expected}, received ${response.status()}; rule=${data?.rule ?? 'none'}`);
  return data;
}
const casePath = () => '/api/v1/cases/' + ownId('cases', legalCase);
const userPath = () => '/api/v1/users/' + ownId('users', user);
async function screenshot(name, target = page) {
  // No screenshots of credentials, broad user lists or unrelated legal records.
  if (await target.locator('input[type="password"]').count()) throw new Blocked('Screenshot excluded: password form present');
  await target.screenshot({ path: output + '/' + name + '.png', fullPage: true });
}

test('LOGIN', 'LD-007', 'Open /cases/new?lang=en -> Enter approved credentials -> Submit -> GET /me',
  'Browser authenticates; dedicated account has required test permissions; primary account is unchanged', async () => {
    await page.goto(base + '/cases/new?lang=en');
    await page.getByLabel('Username or email', { exact: true }).fill(primary.username);
    await page.getByLabel('Password', { exact: true }).fill(primary.password);
    const response = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    assert((await response).status() === 200, 'Approved-account browser login did not return 200');
    const me = await api(admin, 'GET', '/api/v1/me');
    demand(!me.mustChangePassword, 'Primary QA account requires owner-managed password change');
    const required = ['USER_VIEW','USER_MANAGE','ROLE_VIEW','ROLE_MANAGE','AUDIT_VIEW','CASE_VIEW','CASE_CREATE','CASE_EDIT','CASE_STATUS_CHANGE','PARTY_MANAGE','COURT_VIEW','COURT_MANAGE','PROCEEDING_MANAGE','PROCEEDING_VIEW','JUDGMENT_VIEW','JUDGMENT_MANAGE'];
    demand(required.every(p => me.permissions.includes(p)), 'Approved account lacks required test permissions');
    authenticated = true;
    return { login_status: 200, required_permissions_present: true };
  });
test('SESSION', 'LD-007,LD-010', 'Inspect session cookie metadata -> Inspect browser storage for session/password markers',
  'JSESSIONID is Secure/HttpOnly/SameSite=Lax; credentials absent from browser storage', async () => {
    const cookie = (await admin.cookies(base)).find(c => c.name === 'JSESSIONID');
    assert(cookie?.secure && cookie.httpOnly && cookie.sameSite === 'Lax', 'Session cookie attributes differ');
    const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
    assert(!secrets.some(s => storage.includes(s)) && !storage.includes(cookie.value), 'Secret persisted in browser storage');
    return { secure: true, httpOnly: true, sameSite: 'Lax', storage_leak: false };
  });
test('ROLE-CREATE', 'LD-010', 'POST /roles with unique QA code and CASE_VIEW permission -> GET created role',
  '201 then 200; exact permission set; role is not system-owned', async () => {
    role = own('roles', await api(admin, 'POST', '/api/v1/roles', { code: prefix + '_VIEW', name: prefix + ' viewer', permissions: ['CASE_VIEW'] }, 201));
    const read = await api(admin, 'GET', '/api/v1/roles/' + ownId('roles', role));
    assert(read.permissions.length === 1 && read.permissions[0] === 'CASE_VIEW' && !read.system, 'Role permission round trip differs');
  });
test('USER-CREATE', 'LD-009', 'POST /users with unique QA username/email and owned viewer role -> GET created user',
  '201; enabled user requires password change; only own role assigned', async () => {
    demand(role, 'Owned viewer role unavailable');
    user = own('users', await api(admin, 'POST', '/api/v1/users', { username: prefix.toLowerCase(), email: prefix.toLowerCase() + '@example.invalid',
      displayName: prefix + ' مستخدم اختبار', locale: 'en', roles: [role.code], temporaryPassword: temporary }, 201));
    assert(user.enabled && user.mustChangePassword && user.roles.includes(role.code), 'New user flags/roles differ');
  });
test('USER-NULL-ROLE', 'LD-009', 'POST unique second QA user with roles:[null]',
  '400 rather than 500; QAD-025 retest', async () => {
    await api(admin, 'POST', '/api/v1/users', { username: prefix.toLowerCase() + 'null', email: prefix.toLowerCase() + 'null@example.invalid',
      displayName: prefix, locale: 'en', roles: [null], temporaryPassword: temporary }, 400);
  });
test('PASSWORD-UI', 'LD-007,LD-008', 'Open separate browser context -> Login newly created QA user -> Change forced temporary password using browser -> GET /me',
  'Password change 204; forced-change flag clears; only disposable user changed', async () => {
    ownId('users', user); viewer = await browser.newContext(); const p = await viewer.newPage();
    await p.goto(base + '/cases?lang=en');
    await p.getByLabel('Username or email', { exact: true }).fill(user.username);
    await p.getByLabel('Password', { exact: true }).fill(temporary);
    await p.getByRole('button', { name: 'Sign in', exact: true }).click();
    await p.getByLabel('Current password', { exact: true }).fill(temporary);
    await p.getByLabel('New password', { exact: true }).fill(changed);
    await p.getByLabel('Repeat the new password', { exact: true }).fill(changed);
    const response = p.waitForResponse(r => r.url().endsWith('/api/v1/me/password') && r.request().method() === 'POST');
    await p.getByRole('button', { name: 'Change password', exact: true }).click();
    assert((await response).status() === 204, 'Browser password change did not return 204');
    assert(!(await api(viewer, 'GET', '/api/v1/me')).mustChangePassword, 'Forced-change flag persisted');
  });
test('PASSWORD-BYTE-BOUNDARY', 'LD-008', 'On disposable QA user submit 73 ASCII bytes then 37 Arabic characters -> Read /me',
  'Both return 400, never 500; QAD-022 negative boundary retest; current password remains usable', async () => {
    for (const p of ['a'.repeat(73), 'س'.repeat(37)]) {
      secrets.push(p); await api(viewer, 'POST', '/api/v1/me/password', { currentPassword: changed, newPassword: p }, 400);
    }
    const check = await browser.newContext();
    try { await api(check, 'POST', '/api/v1/auth/login', { login: user.username, password: changed }); }
    finally { await check.close(); }
  });
test('DENIED', 'LD-009,LD-010,LD-011,LD-018', 'As CASE_VIEW-only user GET users/audit -> POST courts with malformed and valid body',
  '403 for every denied operation, including invalid input; QAD-026 retest', async () => {
    for (const p of ['/api/v1/users','/api/v1/audit']) await api(viewer, 'GET', p, undefined, 403);
    await api(viewer, 'POST', '/api/v1/courts', {}, 403);
    await api(viewer, 'POST', '/api/v1/courts', { code: prefix + '-DENIED', nameAr: prefix, nameEn: prefix, level: 'FIRST_INSTANCE' }, 403);
  });
test('CSRF', 'LD-010', 'With valid admin session submit unique QA case without X-XSRF-TOKEN',
  '403 and no created case', async () => {
    await api(admin, 'POST', '/api/v1/cases', { referenceNo: prefix + '-CSRF', title: prefix, caseType: 'CIVIL', openedOn: '2026-01-02' }, 403, false);
    const found = await api(admin, 'GET', '/api/v1/cases?q=' + prefix + '-CSRF');
    assert(found.items.length === 0, 'Denied CSRF request persisted a case');
  });
test('CASE-CREATE-UI', 'LD-012', 'Open /cases/new -> Fill unique reference/title/type/date -> Create case -> GET returned ID',
  'Browser creates case with 201; details route and persisted OPEN/version0 match', async () => {
    await page.goto(base + '/cases/new?lang=en');
    await page.getByLabel('Reference number', { exact: true }).fill(prefix);
    await page.getByLabel('Title', { exact: true }).fill(prefix + ' قضية اختبار');
    await page.getByLabel('Case type', { exact: true }).selectOption('CIVIL');
    await page.getByLabel('Subject', { exact: true }).fill(prefix + ' synthetic QA only');
    await page.getByLabel('Opened on', { exact: true }).fill('2026-01-02');
    const response = page.waitForResponse(r => r.url().endsWith('/api/v1/cases') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Create case', exact: true }).click();
    const r = await response; assert(r.status() === 201, 'Browser case create did not return 201');
    legalCase = own('cases', await r.json());
    await page.waitForURL('**/cases/' + legalCase.id + '?lang=en');
    const c = await api(admin, 'GET', casePath());
    assert(c.status === 'OPEN' && c.version === 0 && c.referenceNo === prefix, 'Created case contract differs');
    await screenshot('case-created-en');
  });
test('CASE-EDIT-UI', 'LD-013', 'Open own case edit page -> Update title -> Save -> GET own case',
  'UI saves changed title and increments version once', async () => {
    const before = await api(admin, 'GET', casePath());
    await page.goto(base + '/cases/' + legalCase.id + '/edit?lang=en');
    await page.getByLabel('Title', { exact: true }).fill(prefix + ' edited');
    const response = page.waitForResponse(r => r.url().endsWith(casePath()) && r.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Save changes', exact: true }).click();
    assert((await response).status() === 200, 'Browser edit did not return 200');
    const after = await api(admin, 'GET', casePath());
    assert(after.title === prefix + ' edited' && after.version === before.version + 1, 'Edit round trip differs');
  });
test('CASE-STALE', 'LD-013', 'GET own case -> PUT valid fields with version -1 relative to current -> GET case again',
  '409; title/version unchanged', async () => {
    const c = await api(admin, 'GET', casePath());
    await api(admin, 'PUT', casePath(), { title: prefix + ' stale', caseType: c.caseType, subject: c.subject, openedOn: c.openedOn, version: c.version - 1 }, 409);
    const after = await api(admin, 'GET', casePath()); assert(after.title === c.title && after.version === c.version, 'Rejected stale update changed data');
  });
test('CASE-SEARCH-UI', 'LD-015', 'Open /cases -> Search unique reference -> Inspect matching link',
  'Own case appears on mounted case list; QAD-023 retest', async () => {
    ownId('cases', legalCase); await page.goto(base + '/cases?q=' + prefix + '&lang=en');
    await page.getByRole('searchbox', { name: 'Search by reference or title' }).fill(prefix);
    await page.getByRole('button', { name: 'Search', exact: true }).click();
    await page.locator('a[href*="/cases/' + legalCase.id + '"]').first().waitFor();
    await screenshot('case-search-en');
  });
test('PARTY', 'LD-016', 'Create synthetic PERSON without national identifier -> Link to own case as CLIENT -> Repeat link -> Read case parties',
  '201/201/409; exactly one link to owned party', async () => {
    const party = own('parties', await api(admin, 'POST', '/api/v1/parties', { kind: 'PERSON', name: prefix + ' طرف اختبار' }, 201));
    await api(admin, 'POST', casePath() + '/parties', { partyId: party.id, role: 'CLIENT' }, 201);
    await api(admin, 'POST', casePath() + '/parties', { partyId: party.id, role: 'CLIENT' }, 409);
    const parties = await api(admin, 'GET', casePath() + '/parties');
    assert(parties.filter(p => p.partyId === party.id).length === 1, 'Party linkage count differs');
  });
test('STATUS-HISTORY', 'LD-014,LD-017', 'Read own case -> OPEN to ACTIVE -> Reject ACTIVE to ARCHIVED -> Read case and timeline',
  '200/422; rejected transition preserves version; timeline includes status change', async () => {
    let c = await api(admin, 'GET', casePath());
    c = await api(admin, 'POST', casePath() + '/status', { status: 'ACTIVE', reason: prefix, version: c.version });
    await api(admin, 'POST', casePath() + '/status', { status: 'ARCHIVED', reason: prefix, version: c.version }, 422);
    const after = await api(admin, 'GET', casePath());
    assert(after.status === 'ACTIVE' && after.version === c.version, 'Invalid transition changed case');
    const events = await api(admin, 'GET', casePath() + '/timeline');
    assert(events.items.some(e => e.type === 'CASE_STATUS_CHANGED'), 'Status history event missing');
  });
test('COURTS', 'LD-018', 'Create three unique QA courts at FIRST_INSTANCE/APPEAL/CASSATION levels -> Read each',
  '201 for each; active and correct level persisted', async () => {
    for (const [i, level] of ['FIRST_INSTANCE','APPEAL','CASSATION'].entries()) {
      courts[level] = own('courts', await api(admin, 'POST', '/api/v1/courts', { code: prefix + '-' + i, nameAr: prefix + ' محكمة اختبار ' + i, nameEn: prefix + ' Court ' + i, level }, 201));
      const c = await api(admin, 'GET', '/api/v1/courts/' + courts[level].id);
      assert(c.active && c.level === level, 'Court round trip differs');
    }
  });
async function proceeding(stage, date, parent) {
  demand(courts[stage], 'Owned court missing');
  return own('proceedings', await api(admin, 'POST', casePath() + '/proceedings', { stage, courtId: courts[stage].id,
    courtCaseNumber: prefix + '-' + {FIRST_INSTANCE:'FI',APPEAL:'AP',CASSATION:'CA'}[stage], courtCaseYear: 2026, filingDate: date, parentProceedingId: parent?.id ?? null }, 201));
}
async function judge(p, date) {
  return own('judgments', await api(admin, 'POST', '/api/v1/proceedings/' + ownId('proceedings', p) + '/judgment',
    { judgmentDate: date, outcome: 'IN_FAVOUR', summary: prefix, final: false }, 201));
}
test('FIRST-INSTANCE', 'LD-019', 'POST first-instance proceeding for own case and court dated Jan2',
  '201; own case and court linked', async () => { first = await proceeding('FIRST_INSTANCE', '2026-01-02'); });
test('JUDGMENT', 'LD-022', 'POST own first-instance judgment dated Jan3',
  '201; judgment tied to owned proceeding', async () => { judgment = await judge(first, '2026-01-03'); });
test('APPEAL', 'LD-020', 'POST own appeal dated Jan4 against judged first-instance proceeding',
  '201; appeal points to same-case first-instance parent', async () => {
    ownId('judgments', judgment); appeal = await proceeding('APPEAL', '2026-01-04', first);
  });
test('PARENT-CHRONOLOGY', 'LD-020,LD-022', 'With existing Jan4 appeal PUT parent judgment date Jan10/version0 -> GET judgment',
  '422 JUDGMENT_AFTER_CHALLENGE_FILING; date/version unchanged; QAD-024 retest', async () => {
    ownId('proceedings', appeal); const path = '/api/v1/judgments/' + ownId('judgments', judgment);
    const before = await api(admin, 'GET', path);
    const denied = await api(admin, 'PUT', path, { judgmentDate: '2026-01-10', outcome: 'IN_FAVOUR', summary: prefix, final: false, version: before.version }, 422);
    assert(denied.rule === 'JUDGMENT_AFTER_CHALLENGE_FILING', 'Unexpected chronology rejection rule');
    const after = await api(admin, 'GET', path);
    assert(after.judgmentDate === before.judgmentDate && after.version === before.version, 'Rejected chronology edit changed judgment');
  });
test('CASSATION', 'LD-021,LD-022', 'Record appeal judgment Jan5 -> Create cassation Jan6 targeting appeal',
  '201/201; complete first-instance/appeal/cassation chain', async () => {
    appealJudgment = await judge(appeal, '2026-01-05'); await proceeding('CASSATION', '2026-01-06', appeal);
  });
test('SPECIAL-DECISION', 'LD-023', 'POST enforcement suspension Jan7 on owned appeal referencing its judgment',
  '201; decision associated with own proceeding and judgment', async () => {
    ownId('judgments', appealJudgment);
    own('decisions', await api(admin, 'POST', '/api/v1/proceedings/' + ownId('proceedings', appeal) + '/special-decisions',
      { decisionType: 'ENFORCEMENT_SUSPENSION', decisionDate: '2026-01-07', effect: 'GRANTED', summary: prefix, judgmentId: appealJudgment.id }, 201));
  });
test('JUDICIAL-UI', 'LD-019,LD-020,LD-021,LD-022,LD-023', 'Open own case detail -> Inspect proceedings section and all three unique court case numbers',
  'Real React case page renders all three persisted stages', async () => {
    await page.goto(base + '/cases/' + ownId('cases', legalCase) + '?lang=en');
    await page.getByRole('heading', { name: 'Judicial stages', exact: true }).waitFor();
    for (const stage of ['FI','AP','CA']) await page.getByRole('heading', { name: new RegExp(prefix + '-' + stage) }).waitFor();
    await screenshot('case-judicial-en');
  });
test('USER-UPDATE', 'LD-009', 'Read owned user -> Update display name using current version -> Repeat stale version',
  '200 then409; details persisted', async () => {
    const u = await api(admin, 'GET', userPath());
    const changes = { email: u.email, displayName: prefix + ' edited', locale: 'en', version: u.version };
    const updated = await api(admin, 'PUT', userPath(), changes);
    assert(updated.displayName === changes.displayName, 'User display name did not persist');
    await api(admin, 'PUT', userPath(), changes, 409);
  });
test('ROLE-REVOKE', 'LD-010', 'Update owned role to empty permissions -> GET own case through existing viewer session -> Restore CASE_VIEW',
  'Existing session immediately403; restoring permission allows200', async () => {
    const path = '/api/v1/roles/' + ownId('roles', role);
    let r = await api(admin, 'GET', path);
    r = await api(admin, 'PUT', path, { name: r.name, permissions: [], version: r.version });
    await api(viewer, 'GET', casePath(), undefined, 403);
    await api(admin, 'PUT', path, { name: r.name, permissions: ['CASE_VIEW'], version: r.version });
    await api(viewer, 'GET', casePath());
  });
test('AUDIT', 'LD-011', 'GET audit filtered to owned viewer username/run start/action ACCESS_DENIED',
  'At least one denied event from this run; actor filter respected; no passwords/session material', async () => {
    ownId('users', user);
    const events = await api(admin, 'GET', '/api/v1/audit?actor=' + encodeURIComponent(user.username) + '&action=ACCESS_DENIED&from=' + encodeURIComponent(started));
    assert(events.items.length > 0 && events.items.every(e => e.actorUsername === user.username && e.action === 'ACCESS_DENIED'), 'Audit actor/action results differ');
    const text = JSON.stringify(events);
    assert(!secrets.some(s => text.includes(s)) && !/passwordHash|JSESSIONID|XSRF-TOKEN/.test(text), 'Audit contains sensitive data');
    return { matching_events: events.items.length };
  });
test('ASSIGN-ROLES', 'LD-009,LD-010', 'Remove roles from owned user -> Read own case in existing viewer session -> Restore owned role',
  '200/403/200; authority refresh takes effect without a new login', async () => {
    ownId('roles', role);
    await api(admin, 'PUT', userPath() + '/roles', { roles: [] });
    await api(viewer, 'GET', casePath(), undefined, 403);
    await api(admin, 'PUT', userPath() + '/roles', { roles: [role.code] });
    await api(viewer, 'GET', casePath());
  });
test('ROLE-UI', 'LD-010', 'Open owned role detail page -> Read code and CASE_VIEW selection',
  'Role detail UI displays exact persisted code and checked View cases permission', async () => {
    await page.goto(base + '/admin/roles/' + ownId('roles', role) + '?lang=en');
    await page.getByLabel('Name', { exact: true }).waitFor();
    assert(await page.getByRole('checkbox', { name: 'View cases', exact: true }).isChecked(), 'Role permission checkbox differs');
    assert((await page.getByLabel('Name', { exact: true }).inputValue()) === prefix + ' viewer', 'Role UI name differs');
    await screenshot('role-own-en');
  });
test('AUDIT-UI', 'LD-011', 'Open audit -> Filter to owned username and ACCESS_DENIED -> Verify filtered rows',
  'Filtered audit table contains own username and matching denied events; screenshot excludes unrelated rows', async () => {
    ownId('users', user);
    await page.goto(base + '/admin/audit?lang=en');
    await page.getByLabel('User', { exact: true }).fill(user.username);
    await page.getByLabel('Action', { exact: true }).selectOption('ACCESS_DENIED');
    const response = page.waitForResponse(r => r.url().includes('/api/v1/audit?') && new URL(r.url()).searchParams.get('actor') === user.username);
    await page.getByRole('button', { name: 'Filter', exact: true }).click();
    const r = await response; assert(r.status() === 200, 'Audit filter response differs');
    const data = await r.json(); assert(data.items.length > 0 && data.items.every(e => e.actorUsername === user.username), 'Audit UI filter data differs');
    await page.waitForFunction(name => {
      const rows = Array.from(document.querySelectorAll('tbody tr'));
      return rows.length > 0 && rows.every(r => r.textContent.includes(name));
    }, user.username);
    await screenshot('audit-own-en');
  });
for (const lang of ['en','ar']) for (const width of [375,1366]) {
  test('LAYOUT-' + lang + '-' + width, 'LD-012,LD-019,LD-035,LD-036,LD-037',
    'Open own case detail in ' + lang + ' at ' + width + 'px -> Inspect direction/overflow -> Screenshot -> axe A/AA',
    'Correct language/direction; no page overflow; judicial content present; no automated A/AA violations', async () => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(base + '/cases/' + ownId('cases', legalCase) + '?lang=' + lang);
      await page.getByText(prefix + ' edited', { exact: true }).first().waitFor();
      const dom = await page.evaluate(() => ({ lang: document.documentElement.lang, dir: document.documentElement.dir, viewport: innerWidth, documentWidth: document.documentElement.scrollWidth, overflow: document.documentElement.scrollWidth > innerWidth,
        overflowElements: [...document.querySelectorAll('main *')].filter(e => { const r=e.getBoundingClientRect(); return r.right>innerWidth+1 || r.left < -1; }).map(e=>({tag:e.tagName,className:e.className,width:e.getBoundingClientRect().width})) }));
      await screenshot('case-' + lang + '-' + width);
      fs.writeFileSync(output + '/layout-' + lang + '-' + width + '.json', JSON.stringify(dom, null, 2));
      await page.evaluate(fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8'));
      const violations = await page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a','wcag2aa','wcag21aa'] } })).violations.map(v => ({ id: v.id, impact: v.impact, targets: v.nodes.map(n => n.target) })));
      fs.writeFileSync(output + '/axe-' + lang + '-' + width + '.json', JSON.stringify(violations, null, 2));
      assert(dom.lang === lang && dom.dir === (lang === 'ar' ? 'rtl' : 'ltr') && !dom.overflow, 'Locale/direction/viewport assertion failed: ' + JSON.stringify(dom));
      assert(violations.length === 0, 'Automated accessibility violations; see axe evidence'); return dom;
    });
}
test('DISABLE', 'LD-009,LD-010', 'Disable only owned disposable user -> GET /me using existing viewer session',
  '200 disable; old session401; primary account unchanged', async () => {
    await api(admin, 'PUT', userPath() + '/enabled', { enabled: false });
    await api(viewer, 'GET', '/api/v1/me', undefined, 401);
  });
test('LOGOUT-UI', 'LD-007', 'Switch primary browser to English own case -> Click Sign out -> GET /me',
  'Logout204; session401; browser returns to sign-in', async () => {
    await page.goto(base + '/cases/' + ownId('cases', legalCase) + '?lang=en');
    const response = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/logout'));
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    assert((await response).status() === 204, 'Browser logout did not return 204');
    await page.getByRole('button', { name: 'Sign in', exact: true }).waitFor();
    await api(admin, 'GET', '/api/v1/me', undefined, 401);
  });

save();
const credentialFile = process.env.LAWDESK_QA_CREDENTIAL_FILE;
if (!credentialFile) {
  for (const t of tests) { t.status = 'BLOCKED'; t.actual = 'Approved dedicated QA credential handoff unavailable; no authenticated requests or mutations attempted'; }
  save(); process.exitCode = 2;
} else {
  try {
    const stat = fs.statSync(credentialFile);
    demand(stat.isFile() && stat.uid === process.getuid() && !(stat.mode & 0o077), 'Credential file must be owner-only and owned by current QA user');
    primary = JSON.parse(fs.readFileSync(credentialFile, 'utf8'));
    demand(typeof primary.username === 'string' && typeof primary.password === 'string' && primary.password.length > 0, 'Credential file schema requires username/password');
    secrets.push(primary.password);
    browser = await chromium.launch({ headless: true, executablePath: process.env.LAWDESK_QA_CHROMIUM });
    admin = await browser.newContext(); page = await admin.newPage(); page.setDefaultTimeout(15000);
    for (const t of tests) {
      try {
        if (t.id !== 'SIGNED-LOGIN') demand(authenticated, 'Approved account could not establish required test session');
        t.actual = (await t.run()) ?? 'All specified assertions satisfied'; t.status = 'PASS';
      } catch (e) {
        t.status = e instanceof Blocked ? 'BLOCKED' : 'FAIL';
        // Playwright errors can echo input values; only the first line survives and all known secrets are redacted.
        t.actual = safe(String(e.message).split('\n')[0]);
        t.triage_required = !(e instanceof Blocked);
      }
      save();
    }
  } catch (e) {
    for (const t of tests.filter(t => t.status === 'NOT_RUN')) { t.status = 'BLOCKED'; t.actual = 'Credential/runtime setup unavailable; inspect locally without exposing secrets'; }
    save();
  } finally {
    // Best-effort fixture quarantine and logout; never touches an account not created in this execution.
    if (authenticated && user?.id && owned.users?.includes(user.id)) {
      try { await api(admin, 'PUT', userPath() + '/enabled', { enabled: false }); }
      catch { /* May already be logged out. Explicit disable result remains in report. */ }
    }
    if (admin) { try { await api(admin, 'POST', '/api/v1/auth/logout', undefined, 204); } catch {} }
    if (viewer) { try { await api(viewer, 'POST', '/api/v1/auth/logout', undefined, 204); } catch {} }
    if (browser) await browser.close();
    save();
    process.exitCode = tests.some(t => t.status === 'FAIL') ? 1 : tests.some(t => t.status !== 'PASS') ? 2 : 0;
  }
}
