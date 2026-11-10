// Read-only diagnosis of WebKit CSP console errors; no case data or credentials saved.
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { webkit } = require('../../frontend/e2e-wp/node_modules/playwright');
const host = 'lawdesk.167-86-90-174.sslip.io';
const base = `https://${host}`;
const credentials = JSON.parse(fs.readFileSync('/srv/ai-workspaces/secrets/lawdesk-qa/manager.json', 'utf8'));
const browser = await webkit.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'en' });
const page = await context.newPage();
const errors = [];
let screen = 'login';
page.on('console', msg => {
  if (msg.type() !== 'error') return;
  const loc = msg.location();
  errors.push({ screen, message: msg.text().slice(0, 140),
    source: loc.url ? new URL(loc.url).pathname.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, '{id}') : '',
    line: loc.lineNumber });
});
await page.goto(`${base}/login?lang=en`, { waitUntil: 'domcontentloaded' });
await page.locator('#login').fill(credentials.username);
await page.locator('#password').fill(credentials.password);
await page.locator('button[type=submit]').click();
await page.waitForSelector('nav, aside, header');
const before = await page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
const rows = [];
for (const [name, path] of [['dashboard', '/'], ['cases', '/cases'], ['library', '/library'], ['search', '/search?q=SYNTH-DOC']]) {
  screen = name;
  const prior = errors.length;
  await page.goto(`${base}${path}`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('h1');
  await page.waitForTimeout(500);
  rows.push({ screen: name, errors: errors.slice(prior).length,
    styleElements: await page.locator('style').count(), inlineStyleElements: await page.locator('[style]').count(),
    stylesheets: await page.evaluate(() => document.styleSheets.length),
    firstInlineMargin: await page.evaluate(() => {
      const node = document.querySelector('[style]');
      return node ? { declared: node.style.marginInlineEnd, computed: getComputedStyle(node).marginInlineEnd } : null;
    }) });
}
const after = await page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
const result = { applicationShaBefore: before, applicationShaAfter: after, rows,
  sources: [...new Set(errors.map(e => e.source))],
  messages: [...new Set(errors.map(e => e.message))],
  errorCount: errors.length, errorLocations: errors.slice(0, 15).map(({screen,source,line}) => ({screen,source,line})) };
fs.writeFileSync(new URL('./webkit-csp-18822a8.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
await browser.close();
console.log(JSON.stringify({ rows: rows.length, errorCount: errors.length, sources: result.sources }));
