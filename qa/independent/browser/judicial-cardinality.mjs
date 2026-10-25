// Exact archived frontend -> real local API/PostgreSQL; no browser routing mocks.
import fs from 'node:fs';import {createRequire} from 'node:module';
const require=createRequire('/tmp/lawdesk-qa-browser-runtime/package.json');const {chromium}=require('playwright');
const [base,sha,out,caseId,courtId]=process.argv.slice(2);
if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base))throw Error('Only disposable loopback targets');
fs.mkdirSync(out,{recursive:true});if(fs.existsSync(out+'/results.json'))throw Error('Preserve evidence');
const results=[],secret=process.env.LAWDESK_LOCAL_QA_SECRET;
const save=()=>fs.writeFileSync(out+'/results.json',JSON.stringify({tested_sha:sha,scope:'Independent real browser with 102 synthetic active courts and 101 proceedings; local only',counts:Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s=>[s,results.filter(r=>r.status===s).length])),results},null,2));
const browser=await chromium.launch({headless:true,executablePath:process.env.LAWDESK_QA_CHROMIUM});
try{
const context=await browser.newContext();const page=await context.newPage();page.setDefaultTimeout(15000);
await page.goto(base+'/cases/'+caseId+'?lang=en');await page.getByLabel('Username or email',{exact:true}).fill('qaadmin');await page.getByLabel('Password',{exact:true}).fill(secret);
const login=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login'));await page.getByRole('button',{name:'Sign in',exact:true}).click();if((await login).status()!==200)throw Error('Local fixture login failed');
for(const lang of (process.env.LAWDESK_QA_CARDINALITY_REPORT_ONLY?[]:['en','ar'])){
 const row={id:'JUDICIAL-COURT-CHOICE-101-'+lang,story_ids:['LD-018','LD-019'],priority:'P1',preconditions:['102 active FIRST_INSTANCE courts in isolated DB','Real candidate frontend/API and local synthetic ADMIN'],test_data:{caseId,courtId,code:'QA-EDGE-101',locale:lang},steps:['Read page1 of courts API and confirm target exists','Open owned case and Record proceeding form','Inspect all court options and any search/page control','Select QA-EDGE-101'],expected_results:'All active courts reachable; second-page court selectable',status:'NOT_RUN'};results.push(row);
 try{
  await page.goto(base+'/cases/'+caseId+'?lang='+lang);
  const label=lang==='en'?'Record proceeding':'تسجيل دعوى';
  await page.getByRole('button',{name:label,exact:true}).click();
  const form=page.locator('form.judicial-form');
  const selects=form.locator('select');
  const court=selects.nth(1);await court.locator('option').nth(1).waitFor({state:'attached'});
  let pagesLoaded=0;while(!(await court.locator('option[value="'+courtId+'"]').count())&&await form.locator('.paged-select button').count()&&pagesLoaded<5){await form.locator('.paged-select button').click();pagesLoaded++;await page.waitForLoadState('networkidle');}
  const values=await court.locator('option').evaluateAll(nodes=>nodes.map(n=>n.value).filter(Boolean));
  const body=await (await context.request.get(base+'/api/v1/courts?active=true&level=FIRST_INSTANCE&size=100&page=1&sort=code,asc')).json();
  await form.screenshot({path:out+'/court-options-'+lang+'.png'});
  row.actual={api_total:body.totalItems,api_target_present:body.items.some(x=>x.id===courtId),loaded_additional_pages:pagesLoaded,rendered_court_choices:values.length,target_selectable:values.includes(courtId),form_search_controls:await form.locator('input[type="search"]').count(),form_buttons:await form.locator('button').allTextContents()};
  row.status=values.includes(courtId)?'PASS':'FAIL';
 }catch(e){row.status='BLOCKED';row.actual=String(e.message).split('\n')[0].split(secret).join('[REDACTED]');}
 save();
}
for(const lang of (process.env.LAWDESK_QA_CARDINALITY_REPORT_ONLY?[]:['en','ar']))for(const scope of ['ladder','hearing','expert']){
 const row={id:'JUDICIAL-PROCEEDING-101-'+scope+'-'+lang,story_ids:scope==='ladder'?['LD-019']:scope==='hearing'?['LD-024']:['LD-025'],priority:'P1',preconditions:['101 synthetic first-instance proceedings in owned case','Real local browser/API/PostgreSQL'],test_data:{caseId,locale:lang},steps:['GET page1 of owned case proceedings','Reload case screen','Read judicial ladder or open hearing/expert form','Inspect whether second-page proceeding is reachable'],expected_results:'Every stored proceeding remains reachable in the ladder and action selectors',status:'NOT_RUN'};results.push(row);
 try{
  const api=await (await context.request.get(base+'/api/v1/cases/'+caseId+'/proceedings?page=1&size=100')).json();const target=api.items[0];if(api.totalItems!==101||!target)throw Error('High-cardinality fixture absent');
  await page.goto(base+'/cases/'+caseId+'?lang='+lang);await page.locator('.judicial-stage').first().waitFor();
  let visible=false,count;
  if(scope==='ladder'){count=await page.locator('.judicial-stage').count();visible=(await page.locator('.judicial-ladder').innerText()).includes(target.courtCaseNumber);}
  else{
   const button=scope==='hearing'?(lang==='en'?'Schedule hearing':'تحديد جلسة'):(lang==='en'?'Refer to an expert':'إحالة إلى خبير');
   await page.getByRole('button',{name:button,exact:true}).click();
   const form=page.getByRole('form',{name:button,exact:true});const select=form.locator('select').first();await select.locator('option').first().waitFor({state:'attached'});
   const values=await select.locator('option').evaluateAll(nodes=>nodes.map(n=>n.value).filter(Boolean));count=values.length;visible=values.includes(target.id);
  }
  // A viewport screenshot avoids publishing a needlessly huge 100-row full-page image.
  await page.screenshot({path:out+'/'+scope+'-'+lang+'.png'});
  row.actual={api_total:api.totalItems,target_id:target.id,target_number:target.courtCaseNumber,rendered_count:count,target_reachable:visible};row.status=visible?'PASS':'FAIL';
 }catch(e){row.status='BLOCKED';row.actual=String(e.message).split('\n')[0].split(secret).join('[REDACTED]');}
 save();
}
if(process.env.LAWDESK_QA_CARDINALITY_REPORT_ONLY)for(const lang of ['en','ar'])for(const report of ['cases','judgments']){
 const row={id:'REPORT-COURT-101-'+report+'-'+lang,story_ids:[report==='cases'?'LD-031':'LD-032'],priority:'P1',preconditions:['102 active synthetic courts; isolated PostgreSQL and exact candidate frontend'],test_data:{courtId,locale:lang,report},steps:['Read second courts API page ordered by code','Open '+(report==='cases'?'/reports':'/reports/judgments'),'Inspect courtId filter options and available search/page controls'],expected_results:'Every valid court reachable in report filters',status:'NOT_RUN'};results.push(row);
 try{const api=await(await context.request.get(base+'/api/v1/courts?page=1&size=100&sort=code,asc')).json();if(!api.items.some(c=>c.id===courtId))throw Error('Fixture target not in second page');await page.goto(base+(report==='cases'?'/reports':'/reports/judgments')+'?lang='+lang);const select=page.locator('select[name="courtId"]');await select.locator('option').nth(1).waitFor({state:'attached'});const values=await select.locator('option').evaluateAll(nodes=>nodes.map(n=>n.value).filter(Boolean));row.actual={api_total:api.totalItems,target_id:courtId,rendered_count:values.length,target_selectable:values.includes(courtId)};await page.screenshot({path:out+'/report-'+report+'-'+lang+'.png'});row.status=values.includes(courtId)?'PASS':'FAIL';}catch(e){row.status='BLOCKED';row.actual=String(e.message).split('\n')[0].split(secret).join('[REDACTED]');}save();
}
}finally{await browser.close();save();}
