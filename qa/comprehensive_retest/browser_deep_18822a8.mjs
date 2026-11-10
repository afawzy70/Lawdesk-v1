// Independent read-only panel completeness and navigation probe on a known synthetic WP2 case.
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('../../frontend/e2e-wp/node_modules/playwright');

const host = 'lawdesk.167-86-90-174.sslip.io';
const base = `https://${host}`;
const expectedSha = '18822a83bff8e2a38b83db4370674a9f69919d81';
const run = JSON.parse(fs.readFileSync(new URL('./runs-03ff08e/wp2.json', import.meta.url), 'utf8'));
const marker = run.journey.match(/\((\d+)\)$/)?.[1];
if (!marker) throw new Error('missing synthetic WP2 marker');
const credentials = JSON.parse(fs.readFileSync('/srv/ai-workspaces/secrets/lawdesk-qa/manager.json', 'utf8'));
const output = new URL('./browser-deep-18822a8.json', import.meta.url);
const browser = await chromium.launch({ headless: true, args: [`--host-resolver-rules=MAP ${host} 167.86.90.174`] });
const rows = [];
const safePath = url => new URL(url).pathname.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, '{id}');

async function verify(locale, width, height) {
  const context = await browser.newContext({ viewport: { width, height }, locale });
  const page = await context.newPage();
  const errors = [];
  let phase = 'login';
  page.on('pageerror', () => errors.push({ phase, kind: 'pageerror' }));
  page.on('console', msg => { if (msg.type() === 'error') errors.push({ phase, kind: 'console-error' }); });
  page.on('response', response => {
    if (response.status() >= 400 && new URL(response.url()).origin === base)
      errors.push({ phase, kind: 'http', status: response.status(), path: safePath(response.url()) });
  });
  page.on('requestfailed', request => {
    if (new URL(request.url()).origin === base)
      errors.push({ phase, kind: 'request-failed', path: safePath(request.url()), reason: request.failure()?.errorText ?? 'unknown' });
  });
  await page.goto(`${base}/login?lang=${locale}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#login').fill(credentials.username);
  await page.locator('#password').fill(credentials.password);
  await page.locator('button[type=submit]').click();
  await page.waitForSelector('nav, aside, header', { timeout: 30000 });
  const before = await page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
  if (before !== expectedSha) throw new Error(`unexpected release ${before}`);
  errors.length = 0;

  // Read-only baseline from the server, obtained before opening the full case workspace.
  phase = 'baseline';
  const baseline = await page.evaluate(async marker => {
    async function get(path) {
      const response = await fetch(path);
      if (!response.ok) throw new Error(`${path.split('?')[0]} ${response.status}`);
      return response.json();
    }
    const listing = await get(`/api/v1/cases?q=${encodeURIComponent(marker)}&size=20`);
    const item = listing.items.find(c => c.title?.includes(marker));
    if (!item) throw new Error('known synthetic WP2 case missing');
    const id = item.id;
    const paths = ['claims', 'defenses', 'documents', 'proceedings', 'judgments', 'summary', 'brief'];
    const values = {};
    for (const name of paths) values[name] = await get(`/api/v1/cases/${id}/${name}`);
    const legalCase = await get(`/api/v1/cases/${id}`);
    const dashboard = await get('/api/v1/reports/dashboard');
    return { id, referenceNo: legalCase.referenceNo, title: legalCase.title, ...values, dashboard };
  }, marker);
  const expected = {
    claims: baseline.claims.length, defenses: baseline.defenses.length, documents: baseline.documents.items.length,
    proceedings: baseline.proceedings.items.length, judgments: baseline.judgments.items.length,
    briefRequests: baseline.brief.requests.items.length, briefDefenses: baseline.brief.defenses.items.length,
    dashboardCases: baseline.dashboard.cases.total, dashboardIndicators: baseline.dashboard.indicators?.length ?? 0,
  };
  const checks = [];
  const check = (name, ok, observed) => checks.push({ name, ok: Boolean(ok), observed });
  check('known synthetic case has three claims', expected.claims === 3, expected.claims);
  check('known synthetic case has two defenses', expected.defenses >= 2, expected.defenses);
  check('known synthetic case has documents', expected.documents >= 3, expected.documents);
  check('known synthetic case has proceedings and judgments', expected.proceedings >= 2 && expected.judgments >= 2,
    [expected.proceedings, expected.judgments]);

  async function caseView(label) {
    phase = label;
    await page.goto(`${base}/cases/${baseline.id}?lang=${locale}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForFunction(reference => document.querySelector('h1')?.textContent?.includes(reference),
      baseline.referenceNo, { timeout: 30000 });
    await page.waitForFunction(text => document.body.innerText.includes(text), baseline.claims[0].text, { timeout: 30000 });
    await page.waitForLoadState('networkidle', { timeout: 30000 });
    const text = await page.locator('body').innerText();
    const rendered = {
      claims: baseline.claims.filter(x => text.includes(x.text)).length,
      defenses: baseline.defenses.filter(x => text.includes(x.title)).length,
      documents: baseline.documents.items.filter(x => text.includes(x.title)).length,
      summaryTerms: await page.locator('section.wp3-summary dl dt').count(),
      proceedingCards: await page.locator('article.judicial-stage').count(),
      visibleLoadErrors: await page.locator('.ops-load-error, .judicial-alert, .rpt-alert').count(),
      visibleLoading: await page.locator('p[role="status"]').filter({ hasText: /loading|جار|تحميل/i }).count(),
    };
    check(`${label} renders all claims`, rendered.claims === expected.claims, rendered.claims);
    check(`${label} renders all defenses`, rendered.defenses === expected.defenses, rendered.defenses);
    check(`${label} renders all documents`, rendered.documents === expected.documents, rendered.documents);
    check(`${label} renders summary facts`, rendered.summaryTerms >= 5, rendered.summaryTerms);
    check(`${label} renders proceedings`, rendered.proceedingCards >= expected.proceedings, rendered.proceedingCards);
    check(`${label} has no hidden load error`, rendered.visibleLoadErrors === 0, rendered.visibleLoadErrors);
    check(`${label} leaves no loading state`, rendered.visibleLoading === 0, rendered.visibleLoading);
    return rendered;
  }

  const first = await caseView('case-open');
  phase = 'brief-load';
  await page.locator('.case-brief button').first().click();
  await page.waitForSelector('.case-brief-section', { timeout: 30000 });
  const brief = {
    sections: await page.locator('.case-brief-section').count(),
    requests: await page.locator('.case-brief-section').nth(5).locator('.case-brief-entry').count(),
    defenses: await page.locator('.case-brief-section').nth(7).locator('.case-brief-entry').count(),
    failed: await page.locator('.case-brief .case-alert').count(),
  };
  check('brief has all sections', brief.sections === 18, brief.sections);
  check('brief request data is not empty', brief.requests >= expected.briefRequests && expected.briefRequests >= 3, brief.requests);
  check('brief defense data is not empty', brief.defenses >= expected.briefDefenses && expected.briefDefenses >= 2, brief.defenses);
  check('brief has no load error', brief.failed === 0, brief.failed);

  phase = 'reload';
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForFunction(reference => document.querySelector('h1')?.textContent?.includes(reference),
    baseline.referenceNo, { timeout: 30000 });
  await page.waitForFunction(text => document.body.innerText.includes(text), baseline.claims[0].text, { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 });
  check('reload retains known claim', (await page.locator('body').innerText()).includes(baseline.claims[0].text), true);
  phase = 'navigation';
  await page.goto(`${base}/cases?lang=${locale}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForSelector('h1', { timeout: 30000 });
  await page.goBack({ waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForFunction(reference => document.querySelector('h1')?.textContent?.includes(reference),
    baseline.referenceNo, { timeout: 30000 });
  check('back navigation restores case', (await page.locator('h1').first().innerText()).includes(baseline.referenceNo), true);
  phase = 'locale-toggle';
  await page.locator('.locale-toggle').click();
  const otherDir = locale === 'ar' ? 'ltr' : 'rtl';
  await page.waitForFunction(dir => document.documentElement.dir === dir, otherDir, { timeout: 10000 });
  check('language switch retains case data', (await page.locator('body').innerText()).includes(baseline.claims[0].text), true);

  phase = 'dashboard';
  await page.goto(`${base}/?lang=${locale}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForSelector('.rpt-dashboard .rpt-panel', { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 });
  const dashboard = {
    panelCount: await page.locator('.rpt-dashboard .rpt-panel').count(),
    caseTiles: await page.locator('.rpt-dashboard .rpt-panel').first().locator('.kpi-value').allTextContents(),
    statusCounts: await page.locator('.rpt-dashboard .rpt-panel').nth(1).locator('.rpt-count-value').allTextContents(),
    typeCounts: await page.locator('.rpt-dashboard .rpt-panel').nth(2).locator('.rpt-count-value').allTextContents(),
    stageCounts: await page.locator('.rpt-dashboard .rpt-panel').nth(3).locator('.rpt-count-value').allTextContents(),
    judgmentTiles: await page.locator('.rpt-dashboard .rpt-panel').nth(4).locator('.kpi-value').allTextContents(),
    judgmentCounts: await page.locator('.rpt-dashboard .rpt-panel').nth(4).locator('.rpt-count-value').allTextContents(),
    operationTiles: await page.locator('.rpt-dashboard .rpt-panel').nth(5).locator('.kpi-value').allTextContents(),
    indicatorValues: await page.locator('.wp8-indicator-value').allTextContents(),
    errors: await page.locator('.rpt-alert').count(),
    loading: await page.locator('p[role="status"]').filter({ hasText: /loading|جار|تحميل/i }).count(),
  };
  const number = value => Number(String(value).replace(/[٠-٩]/g, c => String(c.charCodeAt(0) - 0x660))
    .replace(/[۰-۹]/g, c => String(c.charCodeAt(0) - 0x6f0)).replace(/[^0-9]/g, ''));
  const tileValues = dashboard.caseTiles.map(number);
  check('dashboard has all panels', dashboard.panelCount >= 4, dashboard.panelCount);
  check('dashboard case totals match API', tileValues.length >= 3 && tileValues[0] === baseline.dashboard.cases.open
    && tileValues[1] === baseline.dashboard.cases.closed && tileValues[2] === baseline.dashboard.cases.total, tileValues);
  const equalNumbers = (actual, wanted) => JSON.stringify(actual.map(number)) === JSON.stringify(wanted);
  check('dashboard status list matches API', equalNumbers(dashboard.statusCounts,
    ['OPEN', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'ARCHIVED'].map(k => baseline.dashboard.cases.byStatus[k])), dashboard.statusCounts.map(number));
  check('dashboard case types match API', equalNumbers(dashboard.typeCounts,
    ['CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE', 'CONSTITUTIONAL', 'OTHER']
      .map(k => baseline.dashboard.cases.byType[k])), dashboard.typeCounts.map(number));
  check('dashboard stage rows are complete', dashboard.stageCounts.length === baseline.dashboard.proceedings.byStage.length + 1,
    dashboard.stageCounts.length);
  check('dashboard judgment tiles match API', equalNumbers(dashboard.judgmentTiles,
    [baseline.dashboard.judgments.recorded, baseline.dashboard.judgments.final]), dashboard.judgmentTiles.map(number));
  check('dashboard judgment outcomes match API', equalNumbers(dashboard.judgmentCounts,
    ['IN_FAVOUR', 'AGAINST', 'PARTIAL', 'DISMISSED', 'OTHER'].map(k => baseline.dashboard.judgments.byOutcome[k])),
    dashboard.judgmentCounts.map(number));
  check('dashboard operation tiles match API', equalNumbers(dashboard.operationTiles,
    ['hearingsThisWeek', 'hearingsAwaitingResult', 'expertReportsOverdue', 'overdueTasks', 'tasksDueSoon']
      .map(k => baseline.dashboard.operations[k])), dashboard.operationTiles.map(number));
  check('dashboard indicator rows match API', dashboard.indicatorValues.length === expected.dashboardIndicators
    && dashboard.indicatorValues.every((value, i) => baseline.dashboard.indicators[i].count === null
      ? value.trim() === '—' : number(value) === baseline.dashboard.indicators[i].count),
    dashboard.indicatorValues.length);
  check('dashboard has no load error', dashboard.errors === 0, dashboard.errors);
  check('dashboard leaves no loading state', dashboard.loading === 0, dashboard.loading);

  const after = await page.evaluate(async () => (await (await fetch('/api/v1/release')).json()).release);
  const record = { locale, width, applicationShaBefore: before, applicationShaAfter: after,
    expected, first, brief, dashboard, checks, errors };
  rows.push(record);
  fs.writeFileSync(output, JSON.stringify(rows, null, 2) + '\n');
  await context.close();
}

try {
  await verify('ar', 1366, 900);
  await verify('en', 390, 844);
} finally {
  await browser.close();
}
console.log(JSON.stringify({ rows: rows.length, failedChecks: rows.flatMap(r => r.checks).filter(c => !c.ok),
  networkErrors: rows.flatMap(r => r.errors).filter(e => e.kind === 'http' && e.status >= 500).length,
  canceled: rows.flatMap(r => r.errors).filter(e => e.kind === 'request-failed').length,
  releaseChanged: rows.filter(r => r.applicationShaBefore !== r.applicationShaAfter).length }));
