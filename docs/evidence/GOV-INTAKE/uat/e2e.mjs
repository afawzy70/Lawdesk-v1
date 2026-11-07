// LD-045 acceptance journey on the deployed UAT, in a real browser (Playwright/Chromium). Synthetic, marked data only.
// Credentials are read from the QA secrets directory and never printed. Run:
//   NODE_PATH=/tmp/lawdesk-qa-browser-runtime/node_modules node e2e.mjs
import { createRequire } from 'node:module';
import fs from 'node:fs';
const require = createRequire('/tmp/lawdesk-qa-browser-runtime/');
const { chromium } = require('playwright');

const BASE = 'https://lawdesk.167-86-90-174.sslip.io';
const SECRETS = '/srv/ai-workspaces/secrets/lawdesk-qa/';
const OUT = new URL('.', import.meta.url).pathname;
const stamp = Date.now().toString().slice(-8);
const results = [];
const problems = [];

const creds = (role) => JSON.parse(fs.readFileSync(`${SECRETS}${role}.json`, 'utf8'));
const check = (name, ok, detail = '') => { results.push({ name, ok: !!ok, detail }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' — ' + detail : ''}`); };

async function session(browser, role, viewport, lang) {
  const ctx = await browser.newContext({ viewport, locale: lang === 'ar' ? 'ar-KW' : 'en-GB' });
  const page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`[${role}] console: ${m.text().slice(0, 160)}`); });
  page.on('pageerror', (e) => problems.push(`[${role}] pageerror: ${String(e).slice(0, 160)}`));
  page.on('response', (r) => { if (r.status() >= 500) problems.push(`[${role}] ${r.status()} ${r.url().replace(BASE, '')}`); });
  await page.goto(`${BASE}/login?lang=${lang}`);
  const c = creds(role);
  await page.getByLabel(lang === 'ar' ? 'اسم المستخدم أو البريد الإلكتروني' : 'Username or email').fill(c.username);
  await page.getByLabel(lang === 'ar' ? 'كلمة المرور' : 'Password', { exact: true }).fill(c.password);
  await page.getByRole('button', { name: lang === 'ar' ? 'تسجيل الدخول' : 'Sign in' }).click();
  await page.getByRole('button', { name: lang === 'ar' ? 'تسجيل الخروج' : 'Sign out' }).waitFor({ timeout: 20000 });
  return { ctx, page };
}

const api = async (ctx, method, path, body) => {
  const xsrf = (await ctx.cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  const r = await ctx.request.fetch(`${BASE}${path}`, { method, headers: { 'X-XSRF-TOKEN': xsrf, 'Content-Type': 'application/json' }, data: body === undefined ? undefined : JSON.stringify(body) });
  let json = null; try { json = await r.json(); } catch { /* no body */ }
  return { status: r.status(), json };
};
const overflow = (page) => page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1);

const browser = await chromium.launch();
try {
  // ---- A: staff registers a case as the defendant, with parties on both sides ------------------------------------
  const staff = await session(browser, 'staff', { width: 1366, height: 900 }, 'en');
  const p = staff.page;
  await p.goto(`${BASE}/cases/new?lang=en`);
  await p.getByRole('heading', { name: 'Register a new case', level: 1 }).waitFor();
  check('A1 no legal-operations section on the registration screen', (await p.getByText(/Legal operations/i).count()) === 0);
  check('A2 sections in order', JSON.stringify(await p.locator('h2').allTextContents()) === JSON.stringify(['Case details', 'Litigation stage', 'The authority’s position and the parties', 'Legal department members', 'Financial impact']));
  await p.screenshot({ path: `${OUT}a1-register-en-1366.png`, fullPage: true });

  const automatedNo = `00${stamp}`;
  const title = `GOV-INTAKE-UAT synthetic ${stamp}`;
  await p.getByRole('textbox', { name: /Case automated number/ }).fill(automatedNo);
  await p.getByRole('textbox', { name: /Title/ }).fill(title);
  await p.getByRole('combobox', { name: /Case type/ }).selectOption('ADMINISTRATIVE');
  await p.getByRole('searchbox', { name: 'Search the courts' }).fill('QA261024121255760079');
  const court = p.getByRole('combobox', { name: /Court/ });
  await court.locator('option', { hasText: 'QA261024121255760079' }).first().waitFor({ state: 'attached' });
  await court.selectOption({ index: 1 });
  await p.getByRole('textbox', { name: /Case number at this stage/ }).fill(`GI${stamp}`);
  await p.getByRole('textbox', { name: /Year/ }).fill('2026');
  await p.getByLabel(/Filing date at the court/).fill('2026-10-01');
  await p.getByRole('radio', { name: 'Defendant' }).check();
  const add = p.getByRole('group', { name: 'Add a party' });
  await add.getByLabel('Side').selectOption('PLAINTIFF');
  await add.getByLabel('Name').fill(`GI claimant ${stamp}`);
  await add.getByLabel('Party automated number').fill(`000${stamp.slice(-4)}`);
  await add.getByRole('button', { name: 'Add to the case' }).click();
  await add.getByLabel('Side').selectOption('DEFENDANT');
  await add.getByLabel('Name').fill(`GI co-defendant ${stamp}`);
  await add.getByRole('button', { name: 'Add to the case' }).click();
  check('A3 authority listed among the defendants', await p.getByRole('region', { name: 'Defendants' }).getByText('The authority', { exact: true }).first().isVisible());
  check('A4 parties on both sides', await p.getByRole('region', { name: 'Plaintiffs' }).getByText(`GI claimant ${stamp}`).isVisible() && await p.getByRole('region', { name: 'Defendants' }).getByText(`GI co-defendant ${stamp}`).isVisible());
  const manager = creds('manager');
  const primary = p.getByRole('combobox', { name: /Specialist member/ });
  await primary.locator('option', { hasText: 'QA manager' }).waitFor({ state: 'attached' });
  await primary.selectOption({ label: 'QA manager (synthetic test account)' });
  const alt = p.getByRole('combobox', { name: /Alternate member/ });
  check('A5 the specialist cannot be chosen as the alternate', await alt.locator('option', { hasText: 'QA manager' }).isDisabled());
  await p.getByRole('radio', { name: 'There is a financial impact' }).check();
  await p.getByRole('textbox', { name: /Financial impact amount/ }).fill('1500.500');
  await p.screenshot({ path: `${OUT}a2-filled-en-1366.png`, fullPage: true });
  await p.getByRole('button', { name: 'Register the case' }).click();
  await p.waitForURL(/\/cases\/[0-9a-f-]{36}/, { timeout: 30000 });
  const caseId = p.url().match(/cases\/([0-9a-f-]{36})/)[1];
  await p.getByRole('heading', { level: 1, name: /^Case LD-/ }).waitFor({ timeout: 20000 });
  await p.getByText(automatedNo).first().waitFor({ timeout: 20000 });
  check('A6 registration succeeded and opens the case with a confirmation', await p.getByRole('status').filter({ hasText: 'created' }).first().isVisible());
  const facts = (await p.locator('dd').allTextContents()).map((t) => t.trim());
  check('A7 automated number kept as typed (leading zeros)', facts.includes(automatedNo), automatedNo);
  check('A8 position, impact, specialist shown', facts.includes('Defendant') && facts.some((f) => f.includes('1,500.500 KWD')) && facts.includes('QA manager (synthetic test account)'));
  check('A9 case automated number shown with the stage numbers', await p.getByText(new RegExp(`case automated number is ${automatedNo}`)).first().isVisible() && await p.getByText(`GI${stamp}`).first().isVisible());
  await p.screenshot({ path: `${OUT}a3-case-en-1366.png`, fullPage: true });

  // ---- B: an unassigned department member cannot change the case (screen and direct API) ---------------------------
  await p.goto(`${BASE}/cases/${caseId}/edit?lang=en`);
  await p.getByRole('textbox', { name: /Title/ }).fill(`${title} (edited by a non-member)`);
  await p.getByRole('button', { name: 'Save changes' }).click();
  await p.getByRole('alert').filter({ hasText: 'permission' }).waitFor({ timeout: 15000 });
  check('B1 the edit screen explains the refusal (403)', true);
  await p.screenshot({ path: `${OUT}b1-nonmember-edit-refused.png`, fullPage: true });
  const direct = await api(staff.ctx, 'POST', `/api/v1/cases/${caseId}/notes`, { body: 'direct API note' });
  check('B2 direct API write by the non-member is refused', direct.status === 403, String(direct.status));
  const read = await api(staff.ctx, 'GET', `/api/v1/cases/${caseId}`);
  check('B3 the non-member can still read the case', read.status === 200 && read.json.automatedNo === automatedNo);

  // ---- C: the specialist (a supervisor) edits; the financial amount is dropped only after confirmation --------------
  const mgr = await session(browser, 'manager', { width: 1366, height: 900 }, 'en');
  const m = mgr.page;
  await m.goto(`${BASE}/cases/${caseId}/edit?lang=en`);
  await m.getByRole('radio', { name: 'There is no financial impact' }).click();
  check('C1 choosing "no impact" asks before dropping the amount', await m.getByRole('alert').filter({ hasText: '1,500.5 KWD' }).isVisible());
  await m.screenshot({ path: `${OUT}c1-discard-confirm.png`, fullPage: true });
  await m.getByRole('button', { name: 'Remove the amount' }).click();
  await m.getByRole('button', { name: 'Save changes' }).click();
  await m.getByRole('status').filter({ hasText: 'Changes saved' }).waitFor({ timeout: 15000 });
  const afterNo = await api(mgr.ctx, 'GET', `/api/v1/cases/${caseId}`);
  check('C2 "no impact" keeps no amount', afterNo.json.financialImpact === 'NO' && afterNo.json.financialAmount == null);

  // ---- D: reassignment applies immediately -------------------------------------------------------------------------
  await m.goto(`${BASE}/cases/${caseId}?lang=en`);
  const section = m.getByRole('heading', { name: 'Assignment' }).locator('xpath=ancestor::section');
  await section.getByRole('combobox', { name: /Specialist member/ }).selectOption({ label: 'QA staff (synthetic test account)' });
  await section.getByRole('combobox', { name: /Alternate member/ }).selectOption({ label: 'QA manager (synthetic test account)' });
  await section.getByRole('button', { name: 'Save the assignment' }).click();
  await m.getByText('The assignment was saved.').waitFor({ timeout: 15000 });
  await m.screenshot({ path: `${OUT}d1-reassigned.png`, fullPage: true });
  const staffEdit = await api(staff.ctx, 'POST', `/api/v1/cases/${caseId}/notes`, { body: `note by the new specialist ${stamp}` });
  check('D1 the new specialist can add content at once', staffEdit.status === 201, String(staffEdit.status));
  const mgrEdit = await api(mgr.ctx, 'POST', `/api/v1/cases/${caseId}/notes`, { body: `note by the alternate ${stamp}` });
  check('D2 the alternate can add content', mgrEdit.status === 201, String(mgrEdit.status));
  const viewer = await session(browser, 'viewer', { width: 1366, height: 900 }, 'en');
  const vRead = await api(viewer.ctx, 'GET', `/api/v1/cases/${caseId}`);
  const vWrite = await api(viewer.ctx, 'PUT', `/api/v1/cases/${caseId}/assignment`, { primaryMemberId: '00000000-0000-4000-8000-000000000000', version: 0 });
  check('D3 the read-only user reads but cannot reassign', vRead.status === 200 && vWrite.status === 403, `${vRead.status}/${vWrite.status}`);

  // ---- E: a second stage keeps the case number and the first stage number --------------------------------------------
  const first = (await api(staff.ctx, 'GET', `/api/v1/cases/${caseId}/proceedings`)).json.items[0];
  const judged = await api(mgr.ctx, 'POST', `/api/v1/proceedings/${first.id}/judgment`, { judgmentDate: '2026-10-05', outcome: 'AGAINST', final: false, summary: 'synthetic' });
  const appealCourt = (await api(staff.ctx, 'GET', '/api/v1/courts?level=APPEAL&active=true&size=1')).json.items[0];
  const appeal = await api(staff.ctx, 'POST', `/api/v1/cases/${caseId}/proceedings`, { stage: 'APPEAL', courtId: appealCourt.id, courtCaseNumber: `AP${stamp}`, courtCaseYear: 2026, filingDate: '2026-10-10', parentProceedingId: first.id });
  check('E1 second stage with a different number', judged.status === 201 && appeal.status === 201, `${judged.status}/${appeal.status}`);
  await p.goto(`${BASE}/cases/${caseId}?lang=en`);
  await p.getByText(`AP${stamp}`).first().waitFor();
  check('E2 both stage numbers and the case number are on the case page', await p.getByText(`GI${stamp}`).first().isVisible() && await p.getByText(`AP${stamp}`).first().isVisible() && (await p.locator('dd').allTextContents()).map((t) => t.trim()).includes(automatedNo));
  await p.getByRole('button', { name: 'Record proceeding' }).click();
  check('E3 the stage form shows the automated number and does not ask for it', await p.getByText(new RegExp(`automated number ${automatedNo} applies`)).isVisible());
  await p.screenshot({ path: `${OUT}e1-stages-en.png`, fullPage: true });

  // ---- F: Arabic, RTL, phone width ----------------------------------------------------------------------------------
  const ar = await session(browser, 'staff', { width: 375, height: 800 }, 'ar');
  const a = ar.page;
  await a.goto(`${BASE}/cases/new?lang=ar`);
  await a.getByRole('heading', { name: 'تسجيل قضية جديدة', level: 1 }).waitFor();
  check('F1 Arabic page is right-to-left', (await a.evaluate(() => document.documentElement.dir)) === 'rtl');
  check('F2 no horizontal overflow on the phone (registration)', !(await overflow(a)));
  check('F3 Arabic section names', JSON.stringify(await a.locator('h2').allTextContents()) === JSON.stringify(['بيانات القضية', 'درجة التقاضي', 'موقف الهيئة والأطراف', 'أعضاء الإدارة القانونية', 'الأثر المالي']));
  await a.getByRole('radio', { name: 'يوجد أثر مالي', exact: true }).check();
  await a.screenshot({ path: `${OUT}f1-register-ar-375.png`, fullPage: true });
  await a.goto(`${BASE}/cases/${caseId}?lang=ar`);
  await a.getByRole('heading', { level: 1 }).waitFor();
  await a.getByText(automatedNo).first().waitFor();
  check('F4 no horizontal overflow on the phone (case page, Arabic)', !(await overflow(a)));
  check('F5 Arabic case page shows the labels', await a.getByText('الرقم الآلي للقضية', { exact: true }).first().isVisible() && await a.getByText('العضو المختص', { exact: true }).first().isVisible());
  await a.screenshot({ path: `${OUT}f2-case-ar-375.png`, fullPage: true });

  // ---- G: keyboard only ----------------------------------------------------------------------------------------------
  const k = staff.page;
  await k.goto(`${BASE}/cases/new?lang=en`);
  await k.getByRole('textbox', { name: /Case automated number/ }).focus();
  let reached = false;
  for (let i = 0; i < 60 && !reached; i += 1) { await k.keyboard.press('Tab'); reached = await k.evaluate(() => document.activeElement?.textContent?.trim() === 'Register the case'); }
  check('G1 the submit button is reachable with Tab alone', reached);
  await k.keyboard.press('Enter');
  check('G2 submitting with the keyboard on an empty form shows the field errors', await k.getByRole('alert').filter({ hasText: 'Please correct' }).isVisible());
} finally {
  fs.writeFileSync(`${OUT}results.json`, JSON.stringify({ stamp, results, problems }, null, 2));
  await browser.close();
}
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed; console/network problems: ${problems.length}`);
for (const line of problems) console.log('  ' + line);
process.exitCode = failed.length === 0 ? 0 : 1;
