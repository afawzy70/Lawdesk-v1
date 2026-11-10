// Smoke + layout + accessibility run over the screens added or changed by WP1-WP10.
// Usage: see README.md. Environment: UX_URL, UX_CREDS_FILE, UX_CASES_FILE, UX_OUT, UX_ENGINES, UX_LOCALES, UX_SHOTS.
import fs from 'node:fs';
import path from 'node:path';
import pw from 'playwright';
import AxeBuilder from '@axe-core/playwright';

const url = process.env.UX_URL ?? 'http://127.0.0.1:4173';
if (!/^https?:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/.test(url)) throw new Error('refusing to run against a non-local URL');
const creds = JSON.parse(fs.readFileSync(process.env.UX_CREDS_FILE, 'utf8'));
const caseId = JSON.parse(fs.readFileSync(process.env.UX_CASES_FILE, 'utf8'))[0];
const out = process.env.UX_OUT ?? 'out';
const engines = (process.env.UX_ENGINES ?? 'chromium,firefox').split(',');
const locales = (process.env.UX_LOCALES ?? 'ar,en').split(',');
const shots = (process.env.UX_SHOTS ?? '').split(',').filter(Boolean);
fs.mkdirSync(out, { recursive: true });

// phone 390 and desktop 1366 were the r2 matrix; r3 adds tablet portrait 768x1024 and tablet landscape/small laptop 1024x768.
const ALL_WIDTHS = [['phone', 390, 844], ['tablet', 768, 1024], ['tablet-l', 1024, 768], ['desktop', 1366, 900]];
const wanted = (process.env.UX_WIDTHS ?? ALL_WIDTHS.map((w) => w[0]).join(',')).split(',');
const widths = ALL_WIDTHS.filter((w) => wanted.includes(w[0]));
const screens = [
  ['dashboard', '/'], ['cases', '/cases'], ['intake', '/cases/new'], ['case', `/cases/${caseId}`], ['case-edit', `/cases/${caseId}/edit`],
  ['library', '/library'], ['library-new', '/library/new'], ['library-reports', '/library/reports'], ['library-classifications', '/library/classifications'],
  ['search', '/search?q=SYNTH-DOC'], ['directives', '/directives'], ['templates', '/templates'],
  ['deadlines', '/deadlines'], ['deadline-settings', '/admin/deadlines'], ['tasks', '/tasks'], ['calendar', '/calendar'], ['hearings', '/hearings'],
  ['documents', '/documents'], ['experts', '/experts'], ['notifications', '/notifications'],
  ['archive', '/archive'], ['enforcement', '/enforcement'],
  ['reports', '/reports'], ['report-judgments', '/reports/judgments'], ['report-hearings', '/reports/hearings'], ['report-workload', '/reports/workload'],
  ['report-experts', '/reports/experts'], ['report-families', '/reports/families'], ['report-performance', '/reports/performance'], ['report-claims', '/reports/claim-results'],
  ['admin-users', '/admin'], ['admin-roles', '/admin/roles'], ['admin-org', '/admin/org'], ['admin-access', '/admin/access'], ['admin-lookups', '/admin/lookups'], ['admin-audit', '/admin/audit'],
  ['courts', '/courts'], ['assistant', '/assistant'], ['profile', '/profile'],
];

const IGNORED_CONSOLE = [/Failed to load resource/]; // 4xx on optional lookups are asserted by the API tests, not here

async function measure(page) {
  return page.evaluate(() => {
    const vw = window.innerWidth;
    const overflowX = document.documentElement.scrollWidth - vw;
    const wide = [];
    for (const el of document.body.querySelectorAll('*')) {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && (r.right > vw + 1 || r.left < -1)) {
        let p = el.parentElement, scroller = false;
        while (p && p !== document.body) {
          const o = getComputedStyle(p).overflowX;
          if (o === 'auto' || o === 'scroll' || o === 'hidden') { scroller = true; break; }
          p = p.parentElement;
        }
        if (!scroller) wide.push(`${el.tagName.toLowerCase()}${el.className && typeof el.className === 'string' ? '.' + el.className.split(' ')[0] : ''}`);
      }
    }
    const clipped = [];
    for (const el of document.body.querySelectorAll('h1,h2,h3,th,td,a,button,label,span,p,li')) {
      const cs = getComputedStyle(el);
      if (el.scrollWidth > el.clientWidth + 2 && el.clientWidth > 0 && cs.overflowX === 'hidden' && !el.title && !el.getAttribute('aria-label')) {
        clipped.push(`${el.tagName.toLowerCase()}:"${(el.textContent ?? '').trim().slice(0, 40)}"`);
      }
    }
    // An action (link/button/field) whose box lies partly outside an ancestor that clips its overflow (overflow hidden/clip): it is
    // cut off and cannot be reached, unlike a scroll region (auto/scroll) where it can be scrolled to.
    const clippedActions = [];
    for (const el of document.body.querySelectorAll('a[href],button,input:not([type=hidden]),select,textarea,[role=button]')) {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0 || getComputedStyle(el).visibility === 'hidden') continue;
      if (r.width <= 1 && r.height <= 1) continue; // visually hidden (skip link label, sr-only)
      for (let p = el.parentElement; p && p !== document.documentElement; p = p.parentElement) {
        const cs = getComputedStyle(p);
        if (cs.overflowX === 'hidden' || cs.overflowX === 'clip' || cs.overflowY === 'hidden' || cs.overflowY === 'clip') {
          const pr = p.getBoundingClientRect();
          if (pr.width > 0 && (r.right > pr.right + 1 || r.left < pr.left - 1) && (cs.overflowX === 'hidden' || cs.overflowX === 'clip')) {
            clippedActions.push(`${el.tagName.toLowerCase()}:"${(el.textContent ?? el.getAttribute('aria-label') ?? '').trim().slice(0, 30)}" in ${p.tagName.toLowerCase()}.${String(p.className).split(' ')[0]}`);
            break;
          }
        }
      }
    }
    // A word broken across lines inside a cell, header, link or button (r3, found by eye on the tablet screenshots: the case register
    // squeezed its reference and type columns until "LD-2026-00024" and "نوع القضية" broke mid-word). Checked from tablet width up, where
    // table layouts apply; the phone card layout breaks long references on purpose.
    const brokenWords = [];
    if (vw >= 768) {
      let inspected = 0;
      for (const el of document.body.querySelectorAll('th,td,a,button,label,dt,dd')) {
        if (inspected > 1500 || brokenWords.length >= 8) break;
        const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
        for (let node = walker.nextNode(); node; node = walker.nextNode()) {
          const text = node.textContent ?? '';
          const re = /[^\s]{4,}/g;
          for (let m = re.exec(text); m; m = re.exec(text)) {
            inspected += 1;
            const range = document.createRange();
            range.setStart(node, m.index);
            range.setEnd(node, m.index + m[0].length);
            const rects = [...range.getClientRects()].filter((r) => r.width > 0);
            const tops = new Set(rects.map((r) => Math.round(r.top / 4)));
            if (/^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(m[0])) continue; // opaque identifiers (UUIDs) may wrap
            if (m[0].includes('-') && /\s/.test(text.trim())) continue; // a hyphenated word inside a sentence may wrap at its hyphen ("SYNTH-DOC court")
            if (tops.size > 1 && getComputedStyle(el).visibility !== 'hidden' && el.getBoundingClientRect().width > 1) {
              brokenWords.push(`${el.tagName.toLowerCase()}:"${m[0].slice(0, 30)}"`);
            }
          }
        }
      }
    }
    const tiny = [];
    for (const el of document.body.querySelectorAll('a,button,input,select,textarea,[role=button]')) {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.height > 0 && (r.height < 24 || r.width < 24) && getComputedStyle(el).visibility !== 'hidden') tiny.push(`${el.tagName.toLowerCase()}:"${(el.textContent ?? el.getAttribute('aria-label') ?? '').trim().slice(0, 30)}"`);
    }
    return { overflowX, wide: [...new Set(wide)].slice(0, 8), clipped: [...new Set(clipped)].slice(0, 8), clippedActions: [...new Set(clippedActions)].slice(0, 8), brokenWords: [...new Set(brokenWords)].slice(0, 8), tiny: [...new Set(tiny)].slice(0, 8),
      dir: document.documentElement.dir, lang: document.documentElement.lang, h1: document.querySelectorAll('h1').length };
  });
}

const results = [];
for (const engineName of engines) {
  const browser = await pw[engineName].launch();
  for (const locale of locales) {
    for (const [wname, w, h] of widths) {
      const ctx = await browser.newContext({ viewport: { width: w, height: h }, locale });
      const page = await ctx.newPage();
      const errors = [];
      page.on('pageerror', (e) => errors.push(`pageerror ${String(e).slice(0, 160)}`));
      page.on('console', (m) => { if (m.type() === 'error' && !IGNORED_CONSOLE.some((r) => r.test(m.text()))) errors.push(`console ${m.text().slice(0, 160)}`); });
      await page.goto(`${url}/login?lang=${locale}`);
      await page.locator('#login').fill(creds.username);
      await page.locator('#password').fill(creds.password);
      await page.locator('button[type=submit]').click();
      await page.waitForSelector('nav, aside, header', { timeout: 30000 });
      for (const [name, route] of screens) {
        errors.length = 0;
        const sep = route.includes('?') ? '&' : '?';
        const rec = { engine: engineName, locale, width: wname, screen: name, route };
        try {
          await page.goto(`${url}${route}${sep}lang=${locale}`, { waitUntil: 'domcontentloaded' });
          await page.waitForSelector('h1', { timeout: 20000 });
          await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => undefined);
          await page.waitForTimeout(300);
          Object.assign(rec, await measure(page));
          if (engineName === 'chromium' || process.env.UX_AXE_ALL !== '0') { // r3: axe on every engine by default (UX_AXE_ALL=0: Chromium only)
            const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice']).analyze();
            rec.axe = axe.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length, sample: v.nodes[0]?.target?.join(' ').slice(0, 120) ?? '' }));
          }
          if (shots.includes(name) && locale === 'ar') {
            await page.screenshot({ path: path.join(out, `${engineName}-${wname}-${name}.png`), fullPage: false });
          }
        } catch (e) {
          rec.fatal = String(e).slice(0, 200);
        }
        rec.errors = [...errors];
        results.push(rec);
      }
      await ctx.close();
    }
  }
  await browser.close();
}
fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify(results, null, 1));
const bad = (r) => r.fatal || r.errors?.length || r.overflowX > 1 || r.wide?.length || r.clippedActions?.length || r.brokenWords?.length || r.axe?.length;
console.log(`rows=${results.length} withFindings=${results.filter(bad).length}`);
