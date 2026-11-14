// Four sequential, anonymous, GET-only navigations on public UAT. No load test.
import { createRequire } from 'node:module';
import path from 'node:path';

const runtime = process.env.LAWDESK_PERF_PLAYWRIGHT_RUNTIME || process.cwd();
const require = createRequire(path.join(runtime, 'package.json'));
const { chromium } = require('playwright');
const HOST = 'lawdesk.167-86-90-174.sslip.io';
const URL = `https://${HOST}/`;
const results = [];
const browser = await chromium.launch({headless: true,
  ...(process.env.LAWDESK_PERF_CHROMIUM ? {executablePath: process.env.LAWDESK_PERF_CHROMIUM} : {})});
try {
  for (const [name, viewport, isMobile] of [
    ['desktop', {width: 1366, height: 768}, false],
    ['mobile', {width: 375, height: 812}, true],
  ]) {
    const context = await browser.newContext({viewport, isMobile, deviceScaleFactor: isMobile ? 2 : 1});
    await context.route('**/*', async route => {
      const request = route.request();
      if (request.method() !== 'GET' || new globalThis.URL(request.url()).hostname !== HOST) {
        await route.abort();
      } else {
        await route.continue();
      }
    });
    const page = await context.newPage();
    await page.addInitScript(() => {
      window.__phase8 = {lcp: null, cls: 0, longTasks: []};
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) window.__phase8.lcp = entry.startTime;
      }).observe({type: 'largest-contentful-paint', buffered: true});
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) if (!entry.hadRecentInput) window.__phase8.cls += entry.value;
      }).observe({type: 'layout-shift', buffered: true});
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) window.__phase8.longTasks.push(entry.duration);
      }).observe({type: 'longtask', buffered: true});
    });
    // Routing enforces GET-only/host-only and disables the browser HTTP cache; this is a repeat navigation.
    for (const pass of ['first', 'repeat']) {
      const response = await page.goto(URL, {waitUntil: 'domcontentloaded', timeout: 15000});
      await page.waitForTimeout(1250);
      const performance = await page.evaluate(() => {
        const nav = performance.getEntriesByType('navigation')[0];
        const fcp = performance.getEntriesByName('first-contentful-paint')[0];
        const resources = performance.getEntriesByType('resource');
        return {ttfb_ms: nav?.responseStart ?? null, dom_content_loaded_ms: nav?.domContentLoadedEventEnd ?? null,
          load_event_ms: nav?.loadEventEnd ?? null, fcp_ms: fcp?.startTime ?? null,
          lcp_ms: window.__phase8.lcp, cls: window.__phase8.cls,
          long_task_count: window.__phase8.longTasks.length,
          long_task_total_ms: window.__phase8.longTasks.reduce((a,b) => a+b, 0),
          resource_count: resources.length,
          transfer_bytes_reported: resources.reduce((a,r) => a+r.transferSize, 0)};
      });
      results.push({viewport: name, pass, status: response.status(), ...performance});
      await page.waitForTimeout(1000);
    }
    await context.close();
  }
  console.log(JSON.stringify({observed_at_utc: new Date().toISOString(), scope: 'anonymous public login shell',
    browser: browser.version(), no_credentials_or_writes: true, results}, null, 2));
} finally {
  await browser.close();
}
