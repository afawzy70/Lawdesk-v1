// Read-only UAT browser/API cross-phase regression. Never signs in, mutates data or deploys.
import { createRequire } from 'node:module';
import fs from 'node:fs';
import crypto from 'node:crypto';
const require = createRequire('/tmp/lawdesk-qa-browser-runtime/package.json');
const { chromium } = require('playwright');
const axePath = require.resolve('axe-core/axe.min.js');
const [base, sha, output] = process.argv.slice(2);
fs.mkdirSync(output, { recursive: true });
const results = [];
function check(id, stories, expected, actual, ok, steps) {
  results.push({ id, story_ids: stories.split(','), priority: 'P1',
    preconditions: ['UAT over verified TLS; anonymous browser; no credentials supplied'],
    test_data: { base, viewport: '375/1366', locale: 'ar/en' }, steps,
    expected_results: expected, actual, status: ok ? 'PASS' : 'FAIL' });
  fs.writeFileSync(output+'/results.json', JSON.stringify({ utc: new Date().toISOString(),
    expected_develop_sha: sha, deployment_identity: 'workflow-reported; frontend asset compared separately; no authenticated backend release attestation',
    scope: 'Independent read-only Chromium + live HTTP; authenticated journeys BLOCKED separately',
    counts: Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s=>[s,results.filter(x=>x.status===s).length])), results }, null, 2));
}
const browser = await chromium.launch({ headless: true, executablePath: process.env.LAWDESK_QA_CHROMIUM });
try {
  const context = await browser.newContext(); const page = await context.newPage();
  for (const [path, status] of [['/actuator/health/readiness',200],['/api/v1/me',401],['/api/v1/cases',401],['/api/v1/courts',401],['/api/v1/users',401],['/api/v1/audit',401],['/api/v1/cases/00000000-0000-4000-8000-000000000001/proceedings',401],['/actuator/env',404]]) {
    const r = await context.request.get(base+path);
    check('UAT-HTTP-'+path, 'LD-002,LD-007,LD-010', status, r.status(), r.status()===status, ['GET '+path,'Assert status without credentials']);
  }
  for (const locale of ['en','ar']) for (const width of [375,1366]) {
    await page.setViewportSize({ width, height: 900 });
    const response=await page.goto(base+'/cases?lang='+locale);
    await page.locator('input[type=password]').waitFor();
    const dom = await page.evaluate(() => ({ lang: document.documentElement.lang, dir: document.documentElement.dir,
      overflow: document.documentElement.scrollWidth>innerWidth, passwordType: document.querySelector('input[type=password]')?.getAttribute('autocomplete'),
      caseDataVisible: !!document.querySelector('table'), hasUatLabel: /UAT/.test(document.body.innerText) }));
    check(`UAT-LOGIN-${locale}-${width}`, 'LD-007,LD-035,LD-036', 'Signed-out login; correct lang/dir; no horizontal overflow or case table', dom,
      response.status()===200 && dom.lang===locale && dom.dir===(locale==='ar'?'rtl':'ltr') && !dom.overflow && !dom.caseDataVisible,
      ['Open /cases?lang='+locale,'Inspect login at '+width+'px']);
    await page.screenshot({ path: `${output}/login-${locale}-${width}.png`, fullPage: true });
  }
  await page.evaluate(fs.readFileSync(axePath, 'utf8'));
  const accessibility=await page.evaluate(async()=>await window.axe.run(document,{ runOnly: {type:'tag',values:['wcag2a','wcag2aa','wcag21aa']} }));
  const violations=accessibility.violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.map(n=>n.target)}));
  check('UAT-LOGIN-AXE', 'LD-007,LD-037', 'No automated WCAG A/AA violations on login', violations, violations.length===0, ['Run axe on anonymous Arabic login']);
  const root=await context.request.get(base+'/'); const html=await root.text();
  const entry=html.match(/src="(\/assets\/[^\"]+\.js)"/)[1]; const asset=await context.request.get(base+entry);
  const digest=crypto.createHash('sha256').update(await asset.body()).digest('hex');
  fs.writeFileSync(output+'/frontend-identity.json',JSON.stringify({entry,sha256:digest,expected_develop_sha:sha,observed_at:new Date().toISOString()},null,2));
} finally { await browser.close(); }
