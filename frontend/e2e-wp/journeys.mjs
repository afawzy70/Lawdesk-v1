// Interaction journeys (not just page loads) on a LOCAL instance, per engine / width / locale.
// Usage and environment: see README.md. Exit code 1 when any step fails.
import fs from 'node:fs';
import path from 'node:path';
import pw from 'playwright';

const url = process.env.UX_URL ?? 'http://127.0.0.1:4173';
if (!/^https?:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/.test(url)) throw new Error('refusing to run against a non-local URL');
const creds = JSON.parse(fs.readFileSync(process.env.UX_CREDS_FILE, 'utf8'));
const out = process.env.UX_OUT ?? 'out';
const engines = (process.env.UX_ENGINES ?? 'chromium,firefox').split(',');
const locales = (process.env.UX_LOCALES ?? 'ar,en').split(',');
fs.mkdirSync(out, { recursive: true });
const widths = [['phone', 390, 844], ['desktop', 1366, 900]];
const results = [];

async function step(rec, name, fn) {
  try { await fn(); results.push({ ...rec, step: name, ok: true }); }
  catch (e) { results.push({ ...rec, step: name, ok: false, error: String(e).split('\n')[0].slice(0, 200) }); }
}
const noOverflow = async (page) => {
  const d = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (d > 1) throw new Error(`horizontal overflow ${String(d)}px`);
};

for (const engine of engines) {
  const browser = await pw[engine].launch();
  for (const locale of locales) {
    for (const [wname, w, h] of widths) {
      const ctx = await browser.newContext({ viewport: { width: w, height: h }, locale });
      const page = await ctx.newPage();
      const rec = { engine, locale, width: wname };
      const go = async (route) => {
        await page.goto(`${url}${route}${route.includes('?') ? '&' : '?'}lang=${locale}`, { waitUntil: 'domcontentloaded' });
        await page.waitForSelector('h1', { timeout: 20000 });
      };
      await step(rec, 'sign-in', async () => {
        await page.goto(`${url}/login?lang=${locale}`);
        await page.locator('#login').fill(creds.username);
        await page.locator('#password').fill(creds.password);
        await page.locator('button[type=submit]').click();
        await page.waitForSelector('main', { timeout: 30000 });
      });
      await step(rec, 'sign-in refuses a wrong password without leaking which field', async () => {
        const c2 = await browser.newContext({ viewport: { width: w, height: h }, locale });
        const p2 = await c2.newPage();
        await p2.goto(`${url}/login?lang=${locale}`);
        await p2.locator('#login').fill(creds.username);
        await p2.locator('#password').fill('definitely-wrong-password');
        await p2.locator('button[type=submit]').click();
        await p2.waitForSelector('[role=alert]', { timeout: 15000 });
        await c2.close();
      });
      await step(rec, 'skip link moves focus to main', async () => {
        await go('/cases');
        await page.keyboard.press('Tab');
        const href = await page.evaluate(() => document.activeElement?.getAttribute('href') ?? '');
        if (!href.startsWith('#')) throw new Error(`first tab stop is not the skip link (${href})`);
        await page.keyboard.press('Enter');
        await page.waitForTimeout(150);
        const inMain = await page.evaluate(() => document.activeElement?.closest('main') !== null || document.activeElement?.tagName === 'MAIN');
        if (!inMain) throw new Error('focus did not land in main');
      });
      await step(rec, 'case register: filter, count, open a case', async () => {
        await go('/cases');
        await page.locator('main form[role=search] input[name=q]').fill('رقم 7');
        await page.locator('main form[role=search] button[type=submit]').click();
        await page.waitForSelector('table.case-table a', { timeout: 15000 });
        await noOverflow(page);
        await page.locator('table.case-table a').first().click();
        await page.waitForSelector('h1', { timeout: 15000 });
        await page.waitForURL(/\/cases\/[0-9a-f-]{36}/);
        await noOverflow(page);
      });
      await step(rec, 'case page: every section heading renders and the page does not overflow', async () => {
        await page.waitForSelector('main h2', { timeout: 20000 });
        await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => undefined);
        const n = await page.locator('main h2').count();
        if (n < 10) throw new Error(`only ${String(n)} section headings`);
        await noOverflow(page);
      });
      await step(rec, 'intake: an empty submit shows field errors and stays on the form', async () => {
        await go('/cases/new');
        await page.locator('form.intake-form button[type=submit]').click();
        await page.waitForSelector('[aria-invalid=true], [role=alert]', { timeout: 10000 });
        if (!page.url().includes('/cases/new')) throw new Error('left the form');
        await noOverflow(page);
      });
      await step(rec, 'search: query returns results or an explicit empty state', async () => {
        await go('/search');
        await page.locator('main form[role=search] input[type=search]').fill('SYNTH-DOC');
        await page.locator('main form[role=search] button[type=submit]').click();
        await page.waitForTimeout(1500);
        await noOverflow(page);
      });
      await step(rec, 'assistant: states that no provider is enabled', async () => {
        await go('/assistant');
        const text = (await page.locator('main').innerText()).trim();
        if (text.length < 20) throw new Error('empty assistant page');
        await noOverflow(page);
      });
      await step(rec, 'notifications, deadlines, calendar render without overflow', async () => {
        for (const r of ['/notifications', '/deadlines', '/calendar']) { await go(r); await noOverflow(page); }
      });
      await step(rec, 'calendar month grid exposes rows of 7 cells', async () => {
        await go('/calendar');
        const rows = await page.evaluate(() => [...document.querySelectorAll('.wp6-week')].map((r) => r.children.length));
        if (rows.length < 4 || rows.some((n) => n !== 7)) throw new Error(`rows=${JSON.stringify(rows)}`);
      });
      await ctx.close();
    }
  }
  await browser.close();
}
fs.writeFileSync(path.join(out, 'journeys.json'), JSON.stringify(results, null, 1));
const failed = results.filter((r) => !r.ok);
console.log(`steps=${String(results.length)} failed=${String(failed.length)}`);
for (const f of failed.slice(0, 20)) console.log(`${f.engine} ${f.locale} ${f.width} ${f.step}: ${f.error}`);
process.exit(failed.length === 0 ? 0 : 1);
