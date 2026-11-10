// QA-RT-008: deployed UI with synthetic unread counts; no UAT notification data is changed.
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('../../frontend/e2e-wp/node_modules/playwright');
const { default: AxeBuilder } = require('../../frontend/e2e-wp/node_modules/@axe-core/playwright');

const host = 'lawdesk.167-86-90-174.sslip.io';
const base = `https://${host}`;
const expectedSha = '18822a83bff8e2a38b83db4370674a9f69919d81';
const credentials = JSON.parse(fs.readFileSync('/srv/ai-workspaces/secrets/lawdesk-qa/manager.json', 'utf8'));
const browser = await chromium.launch({ headless: true, args: [`--host-resolver-rules=MAP ${host} 167.86.90.174`] });
const rows = [];
try {
  for (const locale of ['ar', 'en']) {
    for (const [width, height] of [[390, 844], [1366, 900]]) {
      const context = await browser.newContext({ locale, viewport: { width, height } });
      const page = await context.newPage();
      await page.goto(`${base}/login?lang=${locale}`, { waitUntil: 'domcontentloaded' });
      await page.locator('#login').fill(credentials.username);
      await page.locator('#password').fill(credentials.password);
      await page.locator('button[type=submit]').click();
      await page.waitForSelector('nav, aside, header');
      const before = await page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
      if (before !== expectedSha) throw new Error(`unexpected release ${before}`);
      let syntheticCount = null;
      await page.route('**/api/v1/notifications/unread-count', route => {
        if (syntheticCount === 'error') return route.fulfill({ status: 500, contentType: 'application/problem+json', body: '{"title":"synthetic failure"}' });
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ unread: syntheticCount }) });
      });
      for (const count of [0, 3, 250, 'error']) {
        syntheticCount = count;
        await page.goto(`${base}/cases?lang=${locale}`, { waitUntil: 'domcontentloaded' });
        await page.waitForSelector('.wp3-bell');
        await page.waitForTimeout(250);
        const bell = page.locator('.wp3-bell');
        const badge = bell.locator('.wp3-bell-count');
        const visibleBadge = await badge.count() ? (await badge.textContent())?.trim() ?? '' : '';
        const accessibleName = (await bell.getAttribute('aria-label')) ?? '';
        const expectedBadge = count === 250 ? '99+' : count === 3 ? visibleBadge : '';
        const check = count === 0 || count === 'error'
          ? visibleBadge === '' && accessibleName.length > 0
          : visibleBadge.length > 0 && visibleBadge === expectedBadge && accessibleName.includes(visibleBadge)
            && (count !== 3 || /[٣3]/.test(visibleBadge));
        const axe = await new AxeBuilder({ page }).include('.wp3-bell').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
        const after = await page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
        rows.push({ locale, width, syntheticUnread: count, visibleBadge, accessibleName,
          badgeContainedInName: check, axeViolationIds: axe.violations.map(v => v.id),
          applicationShaBefore: before, applicationShaAfter: after });
      }
      await context.close();
    }
  }
} finally { await browser.close(); }
const result = { applicationSha: expectedSha, rows,
  summary: { rows: rows.length, failed: rows.filter(r => !r.badgeContainedInName || r.axeViolationIds.length ||
    r.applicationShaBefore !== r.applicationShaAfter).length,
    note: 'Unread counts and one 500 were intercepted in the browser; UAT data was not altered.' } };
fs.writeFileSync(new URL('./bell-matrix-window2.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result.summary));
if (result.summary.failed) process.exitCode = 1;
