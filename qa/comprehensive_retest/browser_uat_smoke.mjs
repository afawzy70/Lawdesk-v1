// Read-only UAT smoke on synthetic data; no screenshots or credentials persisted.
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('../../frontend/e2e-wp/node_modules/playwright');
const { default: AxeBuilder } = require('../../frontend/e2e-wp/node_modules/@axe-core/playwright');

const host = 'lawdesk.167-86-90-174.sslip.io';
const base = `https://${host}`;
const credentials = JSON.parse(fs.readFileSync('/srv/ai-workspaces/secrets/lawdesk-qa/manager.json', 'utf8'));
const browser = await chromium.launch({ headless: true,
  args: [`--host-resolver-rules=MAP ${host} 167.86.90.174`] });
const results = [];
const output = new URL('./browser-uat-result.json', import.meta.url);
for (const locale of ['ar', 'en']) {
  for (const [widthName, width, height] of [['phone', 390, 844], ['desktop', 1366, 900]]) {
    const context = await browser.newContext({ viewport: { width, height }, locale });
    const page = await context.newPage();
    const errors = [];
    const network = [];
    page.on('pageerror', e => errors.push(String(e).split('\n')[0].slice(0, 140)));
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text().split('\n')[0].slice(0, 140)); });
    page.on('response', r => { if (r.status() >= 400) {
      const u = new URL(r.url());
      network.push({ status: r.status(), path: u.pathname.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, '{id}') });
    }});
    const releaseBefore = await (async () => {
      await page.goto(`${base}/login?lang=${locale}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
      await page.locator('#login').fill(credentials.username);
      await page.locator('#password').fill(credentials.password);
      await page.locator('button[type=submit]').click();
      await page.waitForSelector('nav, aside, header', { timeout: 30000 });
      return page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
    })();
    const syntheticCase = await page.evaluate(async () => {
      const data = await (await fetch('/api/v1/cases?q=SYNTH-DOC&size=1')).json();
      return data.items?.[0]?.id ?? null;
    });
    const routes = [['dashboard', '/'], ['cases', '/cases'], ['library', '/library'],
                    ['search', '/search?q=SYNTH-DOC'], ['tasks', '/tasks'], ['calendar', '/calendar'],
                    ['reports', '/reports']];
    if (syntheticCase) routes.splice(2, 0, ['case', `/cases/${syntheticCase}`]);
    for (const [screen, route] of routes) {
      errors.length = 0; network.length = 0;
      const separator = route.includes('?') ? '&' : '?';
      const rec = { engine: 'chromium', locale, width: widthName, screen,
                    applicationShaBefore: releaseBefore };
      try {
        await page.goto(`${base}${route}${separator}lang=${locale}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
        await page.waitForSelector('h1', { timeout: 15000 });
        await page.waitForTimeout(350);
        Object.assign(rec, await page.evaluate(() => ({
          lang: document.documentElement.lang, dir: document.documentElement.dir,
          overflowX: document.documentElement.scrollWidth - window.innerWidth,
          headings: document.querySelectorAll('h1').length,
          focused: document.activeElement?.tagName ?? null,
        })));
        if (screen === 'cases' || screen === 'case') {
          const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
          rec.axeViolations = axe.violations.map(v => ({ id: v.id, impact: v.impact, nodes: v.nodes.length }));
        }
      } catch (e) {
        rec.fatal = String(e).split('\n')[0].slice(0, 160);
      }
      rec.consoleErrors = [...errors];
      rec.networkErrors = [...network];
      rec.applicationShaAfter = await page.evaluate(async () => {
        try { return (await (await fetch('/api/v1/release')).json()).release; } catch { return 'unavailable'; }
      });
      results.push(rec);
      fs.writeFileSync(output, JSON.stringify(results, null, 2) + '\n');
    }
    await context.close();
  }
}
await browser.close();
console.log(JSON.stringify({ rows: results.length, fatal: results.filter(r => r.fatal).length,
  unexpectedDirection: results.filter(r => r.dir !== (r.locale === 'ar' ? 'rtl' : 'ltr')).length,
  horizontalOverflow: results.filter(r => r.overflowX > 1).length,
  axeViolations: results.reduce((n, r) => n + (r.axeViolations?.length ?? 0), 0),
  releaseChanged: results.filter(r => r.applicationShaBefore !== r.applicationShaAfter).length }));
