// Limited read-only Chromium interaction retest on synthetic cases.
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('../../frontend/e2e-wp/node_modules/playwright');
const host = 'lawdesk.167-86-90-174.sslip.io';
const base = `https://${host}`;
const credentials = JSON.parse(fs.readFileSync('/srv/ai-workspaces/secrets/lawdesk-qa/manager.json', 'utf8'));
const browser = await chromium.launch({ headless: true, args: [`--host-resolver-rules=MAP ${host} 167.86.90.174`] });
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
const problems = [];
page.on('pageerror', e => problems.push({ kind: 'page', message: String(e).split('\n')[0].slice(0, 130) }));
page.on('console', m => { if (m.type() === 'error') problems.push({ kind: 'console', message: m.text().split('\n')[0].slice(0, 130) }); });
const result = {};
try {
  await page.goto(`${base}/login?lang=ar`);
  await page.locator('#login').fill(credentials.username);
  await page.locator('#password').fill(credentials.password);
  await page.locator('button[type=submit]').click();
  await page.waitForSelector('nav, aside, header');
  // The login page may probe an anonymous session and receive an expected 401.
  problems.length = 0;
  result.applicationShaBefore = await page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
  await page.goto(`${base}/cases?lang=ar`);
  await page.waitForSelector('h1');
  const filter = page.locator('input[type=search]').last();
  await filter.fill('SYNTH-DOC');
  await page.locator('button[type=submit]').last().click();
  await page.waitForTimeout(500);
  result.filterValue = await filter.inputValue();
  result.syntheticMarkerVisible = (await page.locator('main').innerText()).includes('SYNTH-DOC');
  result.caseLinkCount = await page.locator('main a[href*="/cases/"]').count();
  result.filterUrlHasQuery = new URL(page.url()).searchParams.toString().includes('SYNTH-DOC');
  await filter.focus();
  await page.keyboard.press('Tab');
  result.focusAfterTab = await page.evaluate(() => document.activeElement?.tagName ?? null);
  await page.getByRole('button', { name: 'مسح عوامل التصفية' }).click();
  result.filterCleared = (await filter.inputValue()) === '';
  await page.getByRole('button', { name: 'English' }).click();
  result.languageAfterSwitch = await page.evaluate(() => ({ lang: document.documentElement.lang,
    dir: document.documentElement.dir, overflowX: document.documentElement.scrollWidth - window.innerWidth }));
  result.applicationShaAfter = await page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
  result.consoleProblems = problems;
  result.pass = result.applicationShaBefore === result.applicationShaAfter && result.filterValue === 'SYNTH-DOC'
    && result.syntheticMarkerVisible && result.filterCleared && result.languageAfterSwitch.lang === 'en'
    && result.languageAfterSwitch.dir === 'ltr' && result.languageAfterSwitch.overflowX <= 1 && !problems.length;
  fs.writeFileSync(new URL('./browser-uat-interactions-result.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ pass: result.pass, caseLinkCount: result.caseLinkCount,
    filterUrlHasQuery: result.filterUrlHasQuery, focusAfterTab: result.focusAfterTab }));
  if (!result.pass) process.exitCode = 1;
} finally {
  await browser.close();
}
