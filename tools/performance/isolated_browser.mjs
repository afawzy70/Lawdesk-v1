// Sequential authenticated browser sample for an isolated loopback LawDesk stack only.
// Usage: LAWDESK_PERF_PLAYWRIGHT_RUNTIME=/path/to/playwright/runtime \
//        LAWDESK_PERF_CREDENTIALS_FILE=/private/perf_accounts.json node isolated_browser.mjs
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';

const BASE = process.env.LAWDESK_PERF_BROWSER_BASE || 'http://127.0.0.1:18080';
if (BASE !== 'http://127.0.0.1:18080') throw new Error('isolated browser target must be loopback:18080');
const credentialsPath = process.env.LAWDESK_PERF_CREDENTIALS_FILE;
if (!credentialsPath) throw new Error('LAWDESK_PERF_CREDENTIALS_FILE is required');
const account = JSON.parse(fs.readFileSync(credentialsPath, 'utf8')).accounts.manager;
if (!account?.username || !account?.password || !account.username.startsWith('perf.')) {
  throw new Error('private synthetic manager account is required');
}
const runtime = process.env.LAWDESK_PERF_PLAYWRIGHT_RUNTIME || process.cwd();
const require = createRequire(path.join(runtime, 'package.json'));
const { chromium } = require('playwright');
const browser = await chromium.launch({headless: true,
  ...(process.env.LAWDESK_PERF_CHROMIUM ? {executablePath: process.env.LAWDESK_PERF_CHROMIUM} : {})});
const results = [];
try {
  for (const [viewportName, viewport, isMobile] of [
    ['desktop', {width: 1366, height: 768}, false],
    ['mobile', {width: 375, height: 812}, true],
  ]) {
    const context = await browser.newContext({viewport, isMobile, deviceScaleFactor: isMobile ? 2 : 1});
    await context.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== BASE) await route.abort();
      else await route.continue();
    });
    const csrf = await context.request.get(`${BASE}/api/v1/auth/csrf`);
    if (csrf.status() !== 200) throw new Error('isolated CSRF endpoint refused');
    const xsrf = (await context.cookies(BASE)).find(cookie => cookie.name === 'XSRF-TOKEN')?.value;
    if (!xsrf) throw new Error('isolated CSRF cookie missing');
    const login = await context.request.post(`${BASE}/api/v1/auth/login`, {
      data: {login: account.username, password: account.password},
      headers: {'X-XSRF-TOKEN': xsrf},
    });
    if (login.status() !== 200) throw new Error(`isolated manager login failed (${login.status()})`);
    const cases = await context.request.get(`${BASE}/api/v1/cases?size=1`);
    if (cases.status() !== 200) throw new Error('isolated case lookup failed');
    const firstCase = (await cases.json()).items?.[0]?.id;
    if (!firstCase) throw new Error('isolated synthetic dataset has no cases');
    const page = await context.newPage();
    await page.addInitScript(() => {
      window.__phase8 = {lcp: null, cls: 0, interactions: [], longTasks: []};
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) window.__phase8.lcp = entry.startTime;
      }).observe({type: 'largest-contentful-paint', buffered: true});
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) if (!entry.hadRecentInput) window.__phase8.cls += entry.value;
      }).observe({type: 'layout-shift', buffered: true});
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) if (entry.interactionId) window.__phase8.interactions.push(entry.duration);
      }).observe({type: 'event', buffered: true, durationThreshold: 16});
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) window.__phase8.longTasks.push(entry.duration);
      }).observe({type: 'longtask', buffered: true});
    });
    for (const [name, route] of [
      ['dashboard', '/?lang=en'], ['cases', '/cases?lang=en'],
      ['case details', `/cases/${firstCase}?lang=en`], ['hearings', '/hearings?lang=en'],
      ['tasks', '/tasks?lang=en'], ['reports', '/reports?lang=en'],
    ]) {
      for (const pass of ['first', 'repeat']) {
        const response = await page.goto(BASE + route, {waitUntil: 'domcontentloaded', timeout: 20000});
        await page.waitForTimeout(1250);
        const metrics = await page.evaluate(() => {
          const navigation = performance.getEntriesByType('navigation')[0];
          const fcp = performance.getEntriesByName('first-contentful-paint')[0];
          const resources = performance.getEntriesByType('resource');
          return {ttfb_ms: navigation?.responseStart ?? null, fcp_ms: fcp?.startTime ?? null,
            lcp_ms: window.__phase8.lcp, cls: window.__phase8.cls,
            long_task_count: window.__phase8.longTasks.length,
            long_task_total_ms: window.__phase8.longTasks.reduce((sum, duration) => sum + duration, 0),
            resource_count: resources.length,
            transfer_bytes_reported: resources.reduce((sum, resource) => sum + resource.transferSize, 0)};
        });
        const link = page.locator('a[href^="/cases"]').first();
        let syntheticInteractionMs = null;
        if (await link.count() && await link.isVisible()) {
          await link.click();
          await page.waitForTimeout(300);
          syntheticInteractionMs = await page.evaluate(() =>
            window.__phase8.interactions.length ? Math.max(...window.__phase8.interactions) : null);
        }
        results.push({viewport: viewportName, page: name, pass, status: response.status(),
          synthetic_interaction_duration_ms: syntheticInteractionMs, ...metrics});
      }
    }
    await context.close();
  }
  console.log(JSON.stringify({observed_at_utc: new Date().toISOString(), scope: 'isolated authenticated pages',
    browser: browser.version(), base: BASE, results}, null, 2));
} finally {
  await browser.close();
}
