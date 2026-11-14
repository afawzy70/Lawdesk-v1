// Additional browser security probes; actual application modules are unchanged.
import fs from 'node:fs';import path from 'node:path';import {createRequire} from 'node:module';
const require=createRequire(path.join(process.env.LAWDESK_QA_BROWSER_RUNTIME,'package.json'));const {chromium}=require('playwright');
const [sha,base,output]=process.argv.slice(2);fs.mkdirSync(output,{recursive:true});const results=[],diagnostics=[];
const browser=await chromium.launch({executablePath:process.env.LAWDESK_QA_CHROMIUM,headless:true});
for(const lang of ['en','ar']){
 const context=await browser.newContext({viewport:{width:375,height:900}});const page=await context.newPage();const requests=[];
 page.on('request',r=>requests.push({method:r.method(),origin:new URL(r.url()).origin,path:new URL(r.url()).pathname}));
 const check=async(id,expected,steps,action)=>{try{const {actual,ok}=await action();results.push({id,expected,steps,actual,status:ok?'PASS':'FAIL'});}catch(e){results.push({id,expected,steps,actual:e.message,status:'FAIL'});}};
 await check('UNTRUSTED-LOCALE-'+lang,'Untrusted locale markup falls back to English without executing or reflecting HTML.', ['Open URL with encoded SVG-onload locale and a real browser.'],async()=>{
  await page.goto(`${base}/?lang=${encodeURIComponent('<svg onload="window.__qaXss=1">')}&qaLocale=${lang}`,{waitUntil:'networkidle'});
  const actual=await page.evaluate(()=>({lang:document.documentElement.lang,xss:window.__qaXss??null,reflected:document.body.innerText.includes('<svg')}));return{actual,ok:actual.lang==='en'&&actual.xss===null&&!actual.reflected};
 });
 await check('PRIVATE-ERROR-'+lang,'401 health marker is never rendered; localized unavailable label remains.', ['Intercept health with synthetic private marker and401.','Open actual preview and inspect status/body.'],async()=>{
  await page.route('**/actuator/health',r=>r.fulfill({status:401,contentType:'application/json',body:JSON.stringify({status:'DOWN',secret:'QA-PRIVATE-MARKER'})}));
  await page.goto(`${base}/?lang=${lang}`,{waitUntil:'networkidle'});const actual={status:await page.locator('[role="status"]').innerText(),leak:await page.locator('body').innerText().then(x=>x.includes('QA-PRIVATE-MARKER'))};return{actual,ok:!actual.leak&&actual.status===(lang==='ar'?'الواجهة البرمجية غير متاحة':'API unavailable')};
 });
 await check('READONLY-SAME-ORIGIN-'+lang,'Preview makes only same-origin GET requests; no feature mutation.', ['Inspect every browser request generated in the two sessions.'],async()=>({actual:requests,ok:requests.every(r=>r.method==='GET'&&r.origin===new URL(base).origin)}));
 diagnostics.push({lang,table:await page.locator('.table-scroll').evaluate(el=>{el.focus();return{tabindex:el.getAttribute('tabindex'),programmaticFocus:document.activeElement===el,scrollWidth:el.scrollWidth,clientWidth:el.clientWidth};})});await context.close();
}
const version=await browser.version();await browser.close();const report={tested_sha:sha,utc:new Date().toISOString(),browser:version,scope:'Chromium security probes; CSP mirrored on QA fixture; health401 synthetic; not authentication or real-container verification.',results,diagnostics,counts:Object.fromEntries(['PASS','FAIL'].map(s=>[s,results.filter(r=>r.status===s).length]))};fs.writeFileSync(path.join(output,'independent-browser-security.json'),JSON.stringify(report,null,2)+'\n');console.log(report.counts);process.exitCode=report.counts.FAIL?1:0;
