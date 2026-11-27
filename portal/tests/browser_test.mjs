// Real-browser acceptance test. Usage: NODE_PATH=<dir containing playwright-core> node browser_test.mjs <dist dir>
// Serves <dist> on loopback with the headers parsed from portal/deploy/nginx.conf + snippets (same CSP as production),
// then checks desktop and iPhone-size rendering. Exit 1 on any failure.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright-core');
const dist = path.resolve(process.argv[2]);
const deploy = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../deploy');
const read = (p) => fs.readFileSync(path.join(deploy, p), 'utf8');
const addHeaders = (txt) => [...txt.matchAll(/add_header\s+(\S+)\s+"((?:[^"\\]|\\.)*)"\s+always;/g)].map((m) => [m[1], m[2]]);
const base = addHeaders(read('snippets/security-headers-base.conf'));
const std = addHeaders(read('snippets/security-headers.conf'));
const mapCsp = addHeaders(read('nginx.conf').split('location = /map/lawdesk-map.html')[1].split('}')[0]).find(([k]) => k === 'Content-Security-Policy')[1];
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.json': 'application/json' };
const server = http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split('?')[0]);
  if (p.endsWith('/')) p += 'index.html';
  const f = path.join(dist, p);
  if (!f.startsWith(dist) || !fs.existsSync(f) || !fs.statSync(f).isFile()) { res.writeHead(404); return res.end('not found'); }
  const h = { 'content-type': types[path.extname(f)] || 'application/octet-stream' };
  const isMap = p === '/map/lawdesk-map.html';
  for (const [k, v] of isMap ? [...base, ['Content-Security-Policy', mapCsp]] : [...base, ...std.filter(([k2]) => k2 === 'Content-Security-Policy')]) h[k] = v;
  res.writeHead(200, h); fs.createReadStream(f).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const origin = `http://127.0.0.1:${server.address().port}`;
let failures = 0;
const check = (name, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${ok ? '' : ' ' + extra}`); if (!ok) failures++; };
const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
for (const [label, vp] of [['desktop', { width: 1280, height: 800 }], ['iphone', { width: 390, height: 844, isMobile: true, hasTouch: true, deviceScaleFactor: 3 }]]) {
  const ctx = await browser.newContext({ viewport: vp, isMobile: !!vp.isMobile, hasTouch: !!vp.hasTouch, locale: 'ar' });
  const page = await ctx.newPage();
  const external = []; const errors = []; const csp = [];
  page.on('request', (r) => { if (!r.url().startsWith(origin) && !r.url().startsWith('blob:') && !r.url().startsWith('data:')) external.push(r.url()); });
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); if (/Content Security Policy/i.test(m.text())) csp.push(m.text().slice(0, 140)); });
  await page.goto(origin + '/index.html');
  check(`${label}: html lang=ar dir=rtl`, (await page.getAttribute('html', 'lang')) === 'ar' && (await page.getAttribute('html', 'dir')) === 'rtl');
  check(`${label}: home has an Arabic h1`, /[؀-ۿ]/.test(await page.textContent('h1')));
  check(`${label}: no horizontal scroll (home)`, await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
  const sidebarSide = await page.evaluate(() => { const n = document.getElementById('side'); const r = n.getBoundingClientRect(); return { right: r.right, w: innerWidth, shown: getComputedStyle(n).display !== 'none' }; });
  if (label === 'desktop') check('desktop: sidebar is on the right (RTL)', sidebarSide.shown && sidebarSide.right > sidebarSide.w / 2);
  else { await page.click('#menu'); check('iphone: menu button opens the categorized sidebar', await page.isVisible('#side')); }
  // search by Arabic name (typed with and without diacritics/alef variants)
  await page.fill('#q', 'قضيه');
  await page.waitForSelector('#results a', { timeout: 4000 }).catch(() => {});
  const hits = await page.$$eval('#results a', (a) => a.map((x) => x.textContent));
  check(`${label}: Arabic search finds features`, hits.length > 0, JSON.stringify(hits));
  // feature walkthrough
  await page.goto(origin + '/features/cases-register.html');
  check(`${label}: breadcrumb present`, (await page.$$('.crumbs a')).length >= 1);
  const total = await page.$$eval('.step', (s) => s.length);
  check(`${label}: walkthrough has steps`, total >= 3, String(total));
  await page.click('[data-next]');
  check(`${label}: next advances the progress bar`, (await page.getAttribute('[role=progressbar]', 'aria-valuenow')) === '2');
  await page.focus('.stepper'); await page.keyboard.press('ArrowRight');
  check(`${label}: keyboard arrow moves back (RTL: right = previous)`, (await page.getAttribute('[role=progressbar]', 'aria-valuenow')) === '1');
  check(`${label}: status badge shown`, (await page.$$('.badge')).length >= 1);
  check(`${label}: no horizontal scroll (feature)`, await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
  check(`${label}: endpoint paths render LTR`, await page.evaluate(() => [...document.querySelectorAll('code.ltr')].every((c) => getComputedStyle(c).direction === 'ltr')));
  // technical chain page
  await page.goto(origin + '/features/auth-login.html');
  check(`${label}: technical chain drawn where documented`, (await page.$$('.flow .node')).length >= 3);
  // theme toggle
  await page.click('#theme');
  check(`${label}: theme toggle sets data-theme`, ['dark', 'light'].includes(await page.getAttribute('html', 'data-theme')));
  // planned page
  await page.goto(origin + '/planned.html');
  check(`${label}: planned page distinguishes R5/R6`, /R5/.test(await page.textContent('main')) && /R6/.test(await page.textContent('main')) && (await page.$$('.st-IN_DEVELOPMENT, .st-PROPOSED')).length >= 2);
  // map page: iframe renders the real Floorplan graph
  await page.goto(origin + '/map/index.html');
  const frame = await (await page.waitForSelector('iframe.map')).contentFrame();
  await frame.waitForSelector('svg, canvas, .card, .node', { timeout: 20000 }).catch(() => {});
  const mapText = await frame.evaluate(() => document.body.innerText.length).catch(() => 0);
  check(`${label}: Floorplan map renders inside the shell`, mapText > 200, String(mapText));
  // Full Arabic localization of the viewer: open every menu, walk the tour, search, select cards; nothing English may remain.
  const act = async (fn) => { try { await fn(); } catch { /* an absent control fails the checks below */ } await page.waitForTimeout(200); };
  for (let i = 0; i < 6; i++) await act(() => frame.evaluate(() => { const b = [...document.querySelectorAll('button')].find((x) => /^(التالي)$/.test(x.textContent.trim())); b && b.click(); }));
  for (const id of ['#screens-btn', '#flows-btn', '#filters-btn', '#tour-btn', '#search-btn', '#theme-toggle', '#btn-panel']) {
    await act(() => frame.evaluate((sel) => document.querySelector(sel) && document.querySelector(sel).click(), id));
    await act(() => frame.evaluate(() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))));
  }
  await act(() => frame.evaluate(() => { document.querySelectorAll('.card, [data-id]').forEach((e, i) => { if (i < 8) { e.click(); e.dispatchEvent(new MouseEvent('dblclick', { bubbles: true })); } }); }));
  const info = await frame.evaluate(() => ({ dir: document.documentElement.dir, lang: document.documentElement.lang, left: Object.keys(window.__FP_UNTRANSLATED || {}), flows: document.querySelectorAll('#flowmenu .r, #flowmenu button').length,
    visibleLatinButtons: [...document.querySelectorAll('button, header span')].filter((e) => { const r = e.getBoundingClientRect(); return r.width > 0 && /^[A-Za-z][A-Za-z ]{3,}$/.test((e.textContent || '').trim()) && !/Floorplan|PostgreSQL/.test(e.textContent); }).map((e) => e.textContent.trim()) }));
  check(`${label}: viewer is lang=ar dir=rtl`, info.dir === 'rtl' && info.lang === 'ar', JSON.stringify(info));
  check(`${label}: viewer shows no untranslated English`, info.left.length === 0 && info.visibleLatinButtons.length === 0, JSON.stringify([info.left, info.visibleLatinButtons]).slice(0, 400));
  check(`${label}: viewer offers Arabic guided flows`, info.flows >= 3, String(info.flows));
  check(`${label}: no external requests`, external.length === 0, external.join(' '));
  check(`${label}: no unexpected console errors`, errors.filter((e) => !/Failed to load resource.*404/.test(e)).length === 0, errors.slice(0, 3).join(' | '));
  if (csp.length) console.log(`INFO ${label}: CSP console messages: ${csp.length} e.g. ${csp[0]}`);
  await ctx.close();
}
await browser.close(); server.close();
console.log(failures ? `${failures} FAILED` : 'ALL PASSED');
process.exit(failures ? 1 : 0);
