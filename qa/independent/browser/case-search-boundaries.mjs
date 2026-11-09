// Close the retained literal-search/reload variant using only newly owned synthetic UAT cases.
import fs from 'node:fs';import crypto from 'node:crypto';import {createRequire}from'node:module';
const require=createRequire('/tmp/lawdesk-qa-browser-runtime/package.json');const {chromium}=require('playwright');
const [sha,out]=process.argv.slice(2),base='https://lawdesk.167-86-90-174.sslip.io';
if(!/^[a-f0-9]{40}$/.test(sha||''))throw Error('Exact SHA required');fs.mkdirSync(out,{recursive:true});if(fs.existsSync(out+'/results.json'))throw Error('Preserve evidence');
const prefix='QASEARCH'+crypto.randomBytes(5).toString('hex').toUpperCase(),results=[],owned=[];
const save=()=>fs.writeFileSync(out+'/results.json',JSON.stringify({tested_sha:sha,prefix,owned,scope:'Independent authenticated UAT browser/API; unique synthetic records',counts:Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s=>[s,results.filter(r=>r.status===s).length])),results},null,2));
const browser=await chromium.launch({headless:true,executablePath:process.env.LAWDESK_QA_CHROMIUM});
let secret='';
async function check(id,steps,expected,fn){const row={id,story_ids:['LD-015'],priority:'P1',preconditions:['Approved QA admin; TLS; exact attested runtime','Only current-prefix synthetic writes'],test_data:{prefix},steps,expected_results:expected,status:'NOT_RUN'};results.push(row);try{row.actual=await fn();row.status='PASS';}catch(e){row.status='FAIL';row.actual=String(e.message).split('\n')[0].split(secret).join('[REDACTED]');}save();}
try{
 const file='/srv/ai-workspaces/secrets/lawdesk-qa/admin.json',stat=fs.statSync(file);if(stat.uid!==process.getuid()||stat.mode&0o077)throw Error('Credential protection mismatch');const cred=JSON.parse(fs.readFileSync(file));secret=cred.password;
 const context=await browser.newContext();const page=await context.newPage();page.setDefaultTimeout(15000);
 async function post(path,body,expected){await context.request.get(base+'/api/v1/auth/csrf');const token=(await context.cookies(base)).find(c=>c.name==='XSRF-TOKEN')?.value;const r=await context.request.post(base+path,{data:body,headers:{'X-XSRF-TOKEN':decodeURIComponent(token)}});if(r.status()!==expected)throw Error('POST '+path+' status '+r.status());return r.json();}
 await post('/api/v1/auth/login',{login:cred.username,password:cred.password},200);
 async function release(){const r=await context.request.get(base+'/api/v1/release');const d=await r.json();if(r.status()!==200||d.release!==sha)throw Error('Runtime mismatch');return {release:d.release};}
 await check('SEARCH-RELEASE-START',['Authenticate','GET release'],'Exact runtime SHA',release);
 const cases={};for(const [i,marker]of ['%','_','OTHER'].entries()){cases[marker]=await post('/api/v1/cases',{referenceNo:prefix+'-'+i,title:prefix+marker+' قضية اختبار',caseType:'CIVIL',openedOn:'2026-01-02'},201);owned.push(cases[marker].id);save();}
 for(const lang of ['en','ar'])for(const marker of ['%','_'])await check('SEARCH-LITERAL-RELOAD-'+lang+'-'+marker.charCodeAt(0),['Open case search with owned prefix','Submit literal '+marker+' with OPEN and CIVIL filters','Inspect only expected case','Reload URL and verify identical filters/results'],'One exact owned match; literal query and filters survive reload',async()=>{
  await page.goto(base+'/cases?q='+prefix+'&lang='+lang);const form=page.locator('form.case-filters');await form.locator('input[name="q"]').fill(prefix+marker);await form.locator('select[name="status"]').selectOption('OPEN');await form.locator('select[name="caseType"]').selectOption('CIVIL');const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/cases'&&new URL(r.url()).searchParams.get('q')===prefix+marker);await form.locator('button[type="submit"]').click();if((await response).status()!==200)throw Error('Search response failed');
  const link=page.locator('tbody a[href*="/cases/'+cases[marker].id+'"]');await link.waitFor();
  await page.waitForFunction(()=>document.querySelectorAll('tbody tr').length===1);
  const before=new URL(page.url());if(before.searchParams.get('q')!==prefix+marker||before.searchParams.get('status')!=='OPEN'||before.searchParams.get('caseType')!=='CIVIL')throw Error('URL filter mismatch');
  await page.reload();await link.waitFor();if(await page.locator('tbody tr').count()!==1||await form.locator('input[name="q"]').inputValue()!==prefix+marker)throw Error('Reload changed query/result');
  await page.screenshot({path:out+'/search-'+lang+'-'+marker.charCodeAt(0)+'.png',fullPage:true});return {rows:1,expected_case:cases[marker].id,literal_preserved:true,filters_preserved:true};
 });
 await check('SEARCH-RELEASE-END',['GET release after browser checks'],'Exact runtime SHA',release);
}finally{await browser.close();save();process.exitCode=results.some(r=>r.status==='FAIL')?1:0;}
