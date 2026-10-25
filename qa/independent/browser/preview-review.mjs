// Real Chromium review. The fixture server mirrors candidate CSP and uses synthetic 503 health.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(path.join(process.env.LAWDESK_QA_BROWSER_RUNTIME, 'package.json'));
const { chromium } = require('playwright');
const AxeBuilder = require('@axe-core/playwright').default;
const [sha, base, output] = process.argv.slice(2);
fs.mkdirSync(output, { recursive: true });
const results=[], observations=[];
const browser = await chromium.launch({ executablePath: process.env.LAWDESK_QA_CHROMIUM, headless: true });
const run = async (id, data, expected, steps, action) => {
  const row = { id, test_data:data, preconditions:['Built exact candidate SHA; isolated local QA SPA server; production CSP copied; synthetic unavailable health.'], steps, expected, status:'NOT_RUN' };
  try { const {actual,ok}=await action(); Object.assign(row,{status:ok?'PASS':'FAIL',actual}); if(!ok)row.failed_assertion=expected; }
  catch(e){ Object.assign(row,{status:'FAIL',actual:e.message}); }
  results.push(row);
  fs.writeFileSync(path.join(output,'partial-browser-results.json'),JSON.stringify({tested_sha:sha,status:'IN_PROGRESS',results},null,2)+'\n');
};
const routes=['/','/cases','/hearings','/judgments','/experts','/documents','/reports','/admin','/unknown-qa-route'];
for(const lang of ['en','ar']) {
  for(const width of [320,375,767,768,1024,1366]) {
    const context=await browser.newContext({viewport:{width,height:900},locale:lang==='ar'?'ar-EG':'en-GB',timezoneId:'Africa/Cairo'});
    const page=await context.newPage();
    const violations=[];page.on('console',m=>{if(m.type()==='error')violations.push(m.text());});
    await page.goto(`${base}/?lang=${lang}`,{waitUntil:'networkidle'});
    await run(`LAYOUT-${lang}-${width}`,{lang,width},'No document horizontal overflow; desktop table fits; visible preview banner.', ['Open dashboard at fixed viewport; measure rendered geometry.'],async()=>{
      const actual=await page.evaluate(()=>({doc:document.documentElement.scrollWidth,viewport:innerWidth,table:document.querySelector('.table-scroll').scrollWidth-document.querySelector('.table-scroll').clientWidth,dir:document.documentElement.dir,lang:document.documentElement.lang,banner:document.querySelector('.preview-banner').innerText}));
      return {actual,ok:actual.doc<=width+1&&(width<1024||actual.table<=1)&&actual.dir===(lang==='ar'?'rtl':'ltr')&&actual.banner.includes(lang==='ar'?'بيانات نموذجية':'sample data')};
    });
    await run(`SAMPLE-REGIONS-${lang}-${width}`,{lang,width},'All three data regions visibly labelled and accessibly described as sample.', ['Inspect every sample section and its aria-describedby target.'],async()=>{
      const actual=await page.locator('.sample-region').evaluateAll(els=>els.map(el=>({badge:el.querySelector('.sample-badge')?.textContent,description:document.getElementById(el.getAttribute('aria-describedby'))?.textContent})));
      const marker=lang==='ar'?'بيانات نموذجية':'Sample data';return {actual,ok:actual.length===3&&actual.every(x=>x.badge===marker&&x.description===marker)};
    });
    if(width<768){
      await run(`MENU-ESCAPE-TRIGGER-${lang}-${width}`,{lang,width},'Escape closes expanded mobile navigation even while trigger retains focus.', ['Click menu trigger (focus stays on trigger).','Press Escape and inspect aria-expanded and focus.'],async()=>{
        const button=page.locator('.menu-button');await button.click();await page.keyboard.press('Escape');const expanded=await button.getAttribute('aria-expanded');const focused=await button.evaluate(el=>el===document.activeElement);
        if(expanded==='true')await button.click();return {actual:{expanded,focused},ok:expanded==='false'&&focused};
      });
      await run(`MENU-ESCAPE-NAV-${lang}-${width}`,{lang,width},'Escape from nav link closes navigation and returns focus to menu button.', ['Open menu, focus first nav link, press Escape.'],async()=>{
        const button=page.locator('.menu-button');await button.click();await page.locator('nav a').first().focus();await page.keyboard.press('Escape');const actual={expanded:await button.getAttribute('aria-expanded'),focused:await button.evaluate(el=>el===document.activeElement)};return {actual,ok:actual.expanded==='false'&&actual.focused};
      });
    }
    if(width===375||width===1366){
      const axe=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze();
      await run(`AXE-${lang}-${width}`,{lang,width},'No automatically detected WCAG A/AA violations in current viewport.', ['Run axe against rendered dashboard; manual screen-reader review remains separate.'],async()=>({actual:axe.violations.map(v=>({id:v.id,impact:v.impact,description:v.description,nodes:v.nodes.map(n=>({target:n.target,failureSummary:n.failureSummary}))})),ok:axe.violations.length===0}));
      await page.screenshot({path:path.join(output,`${lang}-${width}.png`),fullPage:true});
    }
    if(width===1366){
      await run(`SKIP-LINK-${lang}`,{lang,width},'First Tab reveals skip link; Enter moves focus to main.', ['Reload; Tab once; press Enter; inspect activeElement.'],async()=>{
        await page.reload({waitUntil:'networkidle'});await page.keyboard.press('Tab');const first=await page.evaluate(()=>document.activeElement.className);await page.keyboard.press('Enter');const target=await page.evaluate(()=>document.activeElement.id);return {actual:{first,target},ok:first==='skip-link'&&target==='main-content'};
      });
      await run(`NO-SECRET-STORAGE-${lang}`,{lang},'No storage credentials, forms or working save/create/login actions.', ['Inspect forms, browser storage and rendered input controls.'],async()=>{
        const actual=await page.evaluate(()=>({local:localStorage.length,session:sessionStorage.length,forms:document.querySelectorAll('form,input,textarea,select').length,text:document.body.innerText}));
        return {actual:{...actual,text:actual.text.slice(0,150)},ok:actual.local===0&&actual.session===0&&actual.forms===0&&!/\b(save|create|login|sign in)\b/i.test(actual.text)};
      });
      for(const route of routes){
        await run(`ROUTE-${lang}-${route}`,{lang,route},'Preview notice and locale persist on each route; no invented working feature.', ['Open route with locale; inspect notice, title, controls and lang.'],async()=>{
          await page.goto(`${base}${route}?lang=${lang}`,{waitUntil:'networkidle'});const actual=await page.evaluate(()=>({title:document.title,lang:document.documentElement.lang,banner:document.querySelector('.preview-banner')?.innerText,inputs:document.querySelectorAll('form,input,textarea,select').length,main:document.querySelector('main')?.innerText}));
          return {actual,ok:actual.lang===lang&&actual.title.includes(lang==='ar'?'معاينة':'Preview')&&actual.banner?.includes(lang==='ar'?'بيانات نموذجية':'sample data')&&actual.inputs===0};
        });
      }
      await run(`LOCALE-RELOAD-${lang}`,{lang},'Language toggle persists in URL, reload and subsequent navigation.', ['On dashboard toggle language, reload and navigate to cases.'],async()=>{
        await page.goto(`${base}/?lang=${lang}`,{waitUntil:'networkidle'});await page.locator('.locale-toggle').click();const target=lang==='ar'?'en':'ar';await page.reload({waitUntil:'networkidle'});await page.locator('nav a[href^="/cases"]').click();const actual={url:page.url(),lang:await page.locator('html').getAttribute('lang')};return {actual,ok:actual.lang===target&&new URL(actual.url).searchParams.get('lang')===target};
      });
    }
    observations.push({lang,width,console_errors:violations});await context.close();
  }
}
await browser.close();
const report={tested_sha:sha,utc:new Date().toISOString(),environment:{playwright:require('playwright/package.json').version,axe:JSON.parse(fs.readFileSync(path.join(process.env.LAWDESK_QA_BROWSER_RUNTIME,'node_modules/@axe-core/playwright/package.json'),'utf8')).version,chromium_executable:process.env.LAWDESK_QA_CHROMIUM,server:'QA SPA fixture; production CSP mirrored; health synthetic503, not nginx or real API'},results,observations,counts:Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s=>[s,results.filter(x=>x.status===s).length]))};
fs.writeFileSync(path.join(output,'independent-browser-results.json'),JSON.stringify(report,null,2)+'\n');console.log(report.counts);process.exitCode=report.counts.FAIL?1:0;
