// QAD-027: real-browser check that the case screens do not overflow a narrow viewport.
//
// Not part of CI and not a dependency of the app: it needs Playwright and a Chromium that are already on the
// machine. Point PLAYWRIGHT_MODULE at an installed `playwright` package (default: resolve `playwright` normally).
//
//   npm run build && npx vite preview --port 4179 --strictPort &
//   PLAYWRIGHT_MODULE=/path/to/node_modules/playwright/index.mjs node scripts/mobile-layout-check.mjs http://localhost:4179
//
// Every API call is answered here with synthetic data (no backend, no real records), the way the unit tests stub
// fetch. For each screen, width and locale it prints whether document scrollWidth fits the viewport and exits 1
// on any overflow. With SCREENSHOT_DIR set it also saves a full-page screenshot of every check there.

const base = process.argv[2] ?? 'http://localhost:4179';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');

const CASE_ID = '3f2c8a8e-6b1d-4c3e-9a51-0d4b7e2f1a90';
const LONG = 'QA261024121816D9653B';
const at = '2026-10-24T12:18:16Z';
const actor = '00000000-0000-4000-8000-000000000001';

const PERMISSIONS = [
  'CASE_VIEW', 'CASE_CREATE', 'CASE_EDIT', 'CASE_DELETE', 'CASE_STATUS_CHANGE', 'PARTY_MANAGE',
  'COURT_VIEW', 'COURT_MANAGE', 'PROCEEDING_VIEW', 'PROCEEDING_MANAGE', 'JUDGMENT_VIEW', 'JUDGMENT_MANAGE',
];
const me = {
  id: actor, username: 'qa-admin', displayName: 'QA admin (synthetic test account)', email: 'qa@example.invalid',
  locale: 'en', roles: ['ADMIN'], permissions: PERMISSIONS, mustChangePassword: false,
};
const legalCase = {
  id: CASE_ID, referenceNo: LONG, title: `${LONG} edited`, caseType: 'CIVIL', subject: `${LONG} synthetic QA only`,
  openedOn: '2026-01-02', status: 'ACTIVE', version: 3, createdAt: at, createdBy: actor, updatedAt: at, updatedBy: actor,
};
const court = (n, level) => ({
  id: `00000000-0000-4000-8000-00000000010${String(n)}`, code: `${LONG}-${String(n)}`, nameAr: `${LONG} محكمة ${String(n)}`,
  nameEn: `${LONG} Court ${String(n)}`, level, governorate: `${LONG} governorate`, active: true, version: 0, createdAt: at, updatedAt: at,
});
const courts = [court(0, 'FIRST_INSTANCE'), court(1, 'APPEAL'), court(2, 'CASSATION')];
const proceeding = (n, stage, suffix, parent) => ({
  id: `00000000-0000-4000-8000-00000000020${String(n)}`, caseId: CASE_ID, stage, courtId: courts[n].id, courtCode: courts[n].code,
  courtNameAr: courts[n].nameAr, courtNameEn: courts[n].nameEn, courtCaseNumber: `${LONG}-${suffix}`, courtCaseYear: 2026,
  filingDate: `2026-01-0${String(2 * n + 2)}`, parentProceedingId: parent, hasJudgment: n < 2, version: 0, createdAt: at, updatedAt: at,
});
const proceedings = [
  proceeding(0, 'FIRST_INSTANCE', 'FI', null),
  proceeding(1, 'APPEAL', 'AP', '00000000-0000-4000-8000-000000000200'),
  proceeding(2, 'CASSATION', 'CA', '00000000-0000-4000-8000-000000000201'),
];
const judgments = proceedings.slice(0, 2).map((p, n) => ({
  id: `00000000-0000-4000-8000-00000000030${String(n)}`, proceedingId: p.id, caseId: CASE_ID, judgmentDate: `2026-01-0${String(2 * n + 3)}`,
  outcome: 'IN_FAVOUR', summary: LONG, final: false, version: 0, createdAt: at, updatedAt: at,
}));
const decisions = [{
  id: '00000000-0000-4000-8000-000000000400', proceedingId: proceedings[1].id, caseId: CASE_ID, decisionType: 'ENFORCEMENT_SUSPENSION',
  decisionDate: '2026-01-07', effect: 'GRANTED', summary: LONG, judgmentId: judgments[1].id, version: 0, createdAt: at, updatedAt: at,
}];
const party = { partyId: '00000000-0000-4000-8000-000000000500', kind: 'PERSON', name: `${LONG} طرف اختبار`, role: 'CLIENT', addedAt: at };
const events = [
  ['PARTY_ADDED', `${LONG} طرف اختبار added as CLIENT`],
  ['OTHER', `Cassation proceeding ${LONG}-CA/2026 at court ${LONG}-2`],
  ['CASE_CREATED', `Case ${LONG} created`],
].map(([type, summary], n) => ({ id: `00000000-0000-4000-8000-00000000060${String(n)}`, type, summary, occurredAt: at, actorId: actor }));
const page = (items) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1 });
const listItem = { id: CASE_ID, referenceNo: LONG, title: `${LONG} edited`, caseType: 'CIVIL', status: 'ACTIVE', openedOn: '2026-01-02', updatedAt: at };

function answer(path) {
  if (path === '/actuator/health') return { status: 'UP' };
  if (path === '/api/v1/me') return me;
  if (path === '/api/v1/cases') return page([listItem, { ...listItem, id: '3f2c8a8e-6b1d-4c3e-9a51-0d4b7e2f1a91' }]);
  if (path === `/api/v1/cases/${CASE_ID}`) return legalCase;
  if (path === `/api/v1/cases/${CASE_ID}/parties`) return [party];
  if (path === `/api/v1/cases/${CASE_ID}/timeline`) return page(events);
  if (path === `/api/v1/cases/${CASE_ID}/proceedings`) return page(proceedings);
  if (path === `/api/v1/cases/${CASE_ID}/judgments`) return page(judgments);
  if (path === `/api/v1/cases/${CASE_ID}/special-decisions`) return page(decisions);
  if (path === '/api/v1/courts') return page(courts);
  return undefined;
}

const screens = [
  { name: 'case-details', path: `/cases/${CASE_ID}`, ready: 'table' },
  { name: 'case-list', path: '/cases', ready: 'table' },
  { name: 'case-edit', path: `/cases/${CASE_ID}/edit`, ready: 'form' },
  { name: 'courts', path: '/courts', ready: 'table' },
  // The proceeding form opened inside the details page.
  { name: 'proceeding-form', path: `/cases/${CASE_ID}`, ready: 'table', open: /Record proceeding|تسجيل/ },
];
const widths = [320, 375, 1366];
const locales = ['en', 'ar'];

const browser = await chromium.launch();
let failures = 0;
try {
  for (const width of widths) {
    for (const lang of locales) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      await context.route('**/*', async (route) => {
        const url = new URL(route.request().url());
        if (!url.pathname.startsWith('/api/') && !url.pathname.startsWith('/actuator/')) return route.continue();
        if (url.pathname === '/api/v1/auth/csrf') return route.fulfill({ status: 204 });
        const body = answer(url.pathname);
        if (body === undefined) return route.fulfill({ status: 404, contentType: 'application/problem+json', body: '{"status":404}' });
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
      });
      const tab = await context.newPage();
      for (const screen of screens) {
        await tab.goto(`${base}${screen.path}?lang=${lang}`);
        await tab.locator(screen.ready).first().waitFor();
        if (screen.name === 'case-details') await tab.locator('.judicial-stage').first().waitFor();
        if (screen.open !== undefined) {
          await tab.locator('.judicial-stage').first().waitFor();
          await tab.locator('.case-proceedings > button').first().click();
          await tab.locator('.judicial-form').first().waitFor();
        }
        const m = await tab.evaluate(() => {
          const targets = [...document.querySelectorAll('main button')].filter((b) => b.offsetParent !== null);
          const small = targets.filter((b) => {
            const r = b.getBoundingClientRect();
            return r.width < 24 || r.height < 24;
          }).length;
          // Elements past the viewport edge that no scroll container holds: what widens the page.
          // A scroller clips a descendant only if it is, or lies inside, that descendant's containing block, so an
          // absolutely positioned element escapes every scroller below its nearest positioned ancestor.
          const contained = (e) => {
            let escaping = getComputedStyle(e).position === 'absolute';
            for (let a = e.parentElement; a !== null && a !== document.body; a = a.parentElement) {
              const style = getComputedStyle(a);
              if (!escaping && style.overflowX !== 'visible') return true;
              if (escaping && style.position !== 'static') escaping = false;
              if (!escaping && style.overflowX !== 'visible') return true;
            }
            return false;
          };
          // Past the viewport edge itself, or text running out of its own box (a long unbroken reference).
          const candidates = [...document.querySelectorAll('body *')].filter((e) => {
            const r = e.getBoundingClientRect();
            const outside = r.width > 0 && (r.right > window.innerWidth + 0.5 || r.left < -0.5);
            const spills = e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).overflowX === 'visible';
            return (outside || spills) && !contained(e);
          });
          const offenders = candidates
            .filter((e) => !candidates.some((other) => other !== e && e.contains(other)))
            .slice(0, 5)
            .map((e) => `${e.tagName.toLowerCase()}${e.className === '' ? '' : `.${String(e.className).replace(/ /g, '.')}`}`);
          // Row actions of the parties table must be on screen without scrolling the table.
          const remove = [...document.querySelectorAll('.case-table button')].map((b) => b.getBoundingClientRect());
          const offscreen = remove.filter((r) => r.left < 0 || r.right > window.innerWidth).length;
          return {
            offenders,
            offscreen,
            dir: document.documentElement.dir,
            innerWidth: window.innerWidth,
            scrollWidth: document.documentElement.scrollWidth,
            buttons: targets.length,
            smallButtons: small,
          };
        });
        if (process.env.SCREENSHOT_DIR !== undefined) {
          await tab.screenshot({ path: `${process.env.SCREENSHOT_DIR}/${screen.name}-${lang}-${String(width)}.png`, fullPage: true });
        }
        const ok =
          m.scrollWidth <= m.innerWidth && m.smallButtons === 0 && m.offscreen === 0 && m.dir === (lang === 'ar' ? 'rtl' : 'ltr');
        if (!ok) failures += 1;
        console.log(
          `${ok ? 'PASS' : 'FAIL'} ${screen.name} width=${String(width)} lang=${lang} dir=${m.dir} ` +
            `innerWidth=${String(m.innerWidth)} scrollWidth=${String(m.scrollWidth)} buttons=${String(m.buttons)} under24px=${String(m.smallButtons)} ` +
            `rowActionsOffscreen=${String(m.offscreen)}${m.offenders.length === 0 ? '' : ` widenedBy=${m.offenders.join(',')}`}`,
        );
      }
      await context.close();
    }
  }
} finally {
  await browser.close();
}
console.log(failures === 0 ? 'RESULT: PASS (no horizontal overflow)' : `RESULT: FAIL (${String(failures)} checks)`);
process.exit(failures === 0 ? 0 : 1);
