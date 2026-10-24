// Actual browser submissions supplement API UAT. Mutates only this run's synthetic resources.
import fs from 'node:fs';
import crypto from 'node:crypto';
import {createRequire} from 'node:module';
const require=createRequire('/tmp/lawdesk-qa-browser-runtime/package.json');
const {chromium}=require('playwright');
const [base,sha,out]=process.argv.slice(2);
if(base!=='https://lawdesk.167-86-90-174.sslip.io'||!/^[a-f0-9]{40}$/.test(sha||'')||!out)throw Error('Approved target/SHA/output required');
fs.mkdirSync(out,{recursive:true});if(fs.existsSync(out+'/results.json'))throw Error('Refusing evidence overwrite');
const prefix='QAUI'+crypto.randomBytes(5).toString('hex').toUpperCase(),results=[],owned={},secrets=[];
let browser,ctx,page,role,user,c,oldSession,resetSession,resetPassword,runtimeRelease;const courts={},proceedings={},judgments={};
const randomPassword=()=>{const x=crypto.randomBytes(25).toString('base64url')+'!9';secrets.push(x);return x;};
const temporary=randomPassword();
const assert=(v,m)=>{if(!v)throw Error(m);};
class Blocked extends Error{}
const need=(v,m)=>{if(!v)throw new Blocked(m);return v;};
const safe=value=>{let s=JSON.stringify(value);for(const secret of secrets)s=s.split(secret).join('[REDACTED]');return JSON.parse(s);};
const save=()=>fs.writeFileSync(out+'/results.json',JSON.stringify(safe({expected_deployed_sha:sha,backend_runtime_sha_attested:runtimeRelease===sha,observed_backend_runtime_sha:runtimeRelease,utc:new Date().toISOString(),prefix,scope:'Independent live browser form submissions; real API persistence checks',owned,counts:Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s=>[s,results.filter(r=>r.status===s).length])),results}),null,2));
const own=(kind,data)=>{need(data?.id,'Missing new '+kind+' ID');(owned[kind]??=[]).push(data.id);save();return data;};
const id=(kind,data)=>{need(data?.id&&owned[kind]?.includes(data.id),'Missing owned '+kind+' fixture');return data.id;};
async function check(key,stories,steps,expected,fn){
 const row={id:'FORM-'+key,story_ids:stories.split(','),priority:'P1',preconditions:['Approved QA administrator; verified HTTPS; owned synthetic data only'],test_data:{prefix},steps:steps.split(' -> '),expected_results:expected,status:'NOT_RUN'};results.push(row);save();
 try{row.actual=(await fn())??'All specified assertions satisfied';row.status='PASS';}catch(e){row.status=e instanceof Blocked?'BLOCKED':'FAIL';row.actual=safe(String(e.message).split('\n')[0]);row.triage_required=row.status==='FAIL';}
 save();
}
async function api(method,path,body,expected=200,context=ctx){
 const headers={};if(method!=='GET'){await context.request.get(base+'/api/v1/auth/csrf');const cookie=(await context.cookies(base)).find(c=>c.name==='XSRF-TOKEN');need(cookie,'CSRF missing');headers['X-XSRF-TOKEN']=decodeURIComponent(cookie.value);}
 const r=await context.request.fetch(base+path,{method,headers,...(body===undefined?{}:{data:body})});
 assert(r.status()===expected,method+' '+path+': expected '+expected+' actual '+r.status());try{return await r.json();}catch{return null;}
}
async function submit(path,method,button,expected=201){const response=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()===method);await button.click();const r=await response;assert(r.status()===expected,method+' '+path+' returned '+r.status());try{return await r.json();}catch{return null;}}
const field=(scope,label)=>scope.getByLabel(label,{exact:true});
async function snap(name,p=page){need(!(await p.locator('input[type=password]').count()),'Password form excluded from screenshots');await p.screenshot({path:out+'/'+name+'.png',fullPage:true});}
async function login(p,cred){await p.goto(base+'/cases/new?lang=en');await field(p,'Username or email').fill(cred.username);await field(p,'Password').fill(cred.password);const r=p.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login')&&r.request().method()==='POST');await p.getByRole('button',{name:'Sign in',exact:true}).click();assert((await r).status()===200,'Browser login failed');}
try{
 const path=process.env.LAWDESK_QA_CREDENTIAL_FILE;need(path,'Approved credential path missing');const st=fs.statSync(path);need(st.uid===process.getuid()&&!(st.mode&0o077),'Credential permission check failed');const cred=JSON.parse(fs.readFileSync(path,'utf8'));secrets.push(cred.password);
 browser=await chromium.launch({headless:true,executablePath:process.env.LAWDESK_QA_CHROMIUM});ctx=await browser.newContext();page=await ctx.newPage();page.setDefaultTimeout(15000);
 await check('LOGIN','LD-007','Browser login with approved admin','200; administrator session',async()=>{await login(page,cred);runtimeRelease=(await api('GET','/api/v1/release')).release;assert(runtimeRelease===sha,'Runtime release mismatch');assert((await api('GET','/api/v1/me')).roles.includes('ADMIN'),'Administrator role missing');});
 await check('ROLE-CREATE-EDIT','LD-009,LD-010','Open new role -> Enter unique code/name -> Check View cases -> Create -> Edit name and save','Role201 and edit200; exact persisted permission set',async()=>{
  await page.goto(base+'/admin/roles/new?lang=en');await field(page,'Code').fill(prefix);await field(page,'Name').fill(prefix+' role');await page.getByRole('checkbox',{name:'View cases',exact:true}).check();
  role=own('roles',await submit('/api/v1/roles','POST',page.getByRole('button',{name:'Create role',exact:true})));
  await page.waitForURL('**/admin/roles/'+role.id+'?lang=en');await field(page,'Name').fill(prefix+' updated role');
  role=await submit('/api/v1/roles/'+role.id,'PUT',page.getByRole('button',{name:'Save role',exact:true}),200);
  const read=await api('GET','/api/v1/roles/'+id('roles',role));assert(read.name===prefix+' updated role'&&read.permissions.join(',')==='CASE_VIEW','Role UI persistence differs');await snap('role-created-edited');
 });
 await check('USER-CREATE-EDIT','LD-009','Open new user -> Fill unique identity and generated temporary password -> Assign owned role -> Create -> Edit display name','201/200; owned role and edited display name persisted',async()=>{
  id('roles',role);await page.goto(base+'/admin/users/new?lang=en');await field(page,'Username').fill(prefix.toLowerCase());await field(page,'Email').fill(prefix.toLowerCase()+'@example.invalid');await field(page,'Display name').fill(prefix+' user');await field(page,'Language').selectOption('en');await page.getByRole('checkbox',{name:role.name,exact:true}).check();await field(page,'Temporary password').fill(temporary);
  user=own('users',await submit('/api/v1/users','POST',page.getByRole('button',{name:'Create user',exact:true})));
  await page.waitForURL('**/admin/users/'+user.id+'?lang=en');await field(page,'Display name').fill(prefix+' updated user');
  user=await submit('/api/v1/users/'+user.id,'PUT',page.getByRole('button',{name:'Save details',exact:true}),200);
  assert(user.displayName===prefix+' updated user'&&user.roles.includes(role.code),'User UI persistence differs');
 });
 await check('CASE-CREATE','LD-012','Open new case -> Fill synthetic title/reference/date -> Create','201 and owned case detail route',async()=>{
  await page.goto(base+'/cases/new?lang=en');await field(page,'Reference number').fill(prefix);await field(page,'Title').fill(prefix+' قضية اختبار');await field(page,'Case type').selectOption('CIVIL');await field(page,'Opened on').fill('2026-01-02');
  c=own('cases',await submit('/api/v1/cases','POST',page.getByRole('button',{name:'Create case',exact:true})));await page.waitForURL('**/cases/'+c.id+'?lang=en');
 });
 await check('PARTY-STATUS','LD-014,LD-016,LD-017','Own case -> New synthetic person -> Add as client -> Change OPEN to ACTIVE -> Inspect timeline','Party linkage201; status200; timeline persisted',async()=>{
  id('cases',c);const f=page.getByRole('form',{name:'New party',exact:true});await field(f,'Name').fill(prefix+' طرف');await field(f,'Role').selectOption('CLIENT');
  const partyResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/parties'&&r.request().method()==='POST');
  await submit('/api/v1/cases/'+c.id+'/parties','POST',f.getByRole('button',{name:'Add party',exact:true}));const r=await partyResponse;assert(r.status()===201,'Party create failed');own('parties',await r.json());
  const statusForm=page.getByRole('form',{name:'Change status',exact:true});await field(statusForm,'New status').selectOption('ACTIVE');await field(statusForm,'Reason').fill(prefix);
  c=await submit('/api/v1/cases/'+c.id+'/status','POST',statusForm.getByRole('button',{name:'Change status',exact:true}),200);
  const events=await api('GET','/api/v1/cases/'+c.id+'/timeline');assert(events.items.some(e=>e.type==='CASE_STATUS_CHANGED'),'Timeline status event missing');await snap('case-party-status');
 });
 for(const [i,stage] of ['FIRST_INSTANCE','APPEAL','CASSATION'].entries())await check('COURT-'+stage,'LD-018','Open courts -> New court -> Fill unique code, Arabic/English names and level -> Save','201; active court persisted',async()=>{
  await page.goto(base+'/courts?lang=en');await page.getByRole('button',{name:'New court',exact:true}).click();const f=page.locator('form.court-form');
  await field(f,'Code').fill(prefix+'-'+i);await field(f,'Arabic name').fill(prefix+' محكمة '+i);await field(f,'English name').fill(prefix+' Court '+i);await field(f,'Level').selectOption(stage);
  courts[stage]=own('courts',await submit('/api/v1/courts','POST',f.getByRole('button',{name:'Save',exact:true})));assert(courts[stage].level===stage&&courts[stage].active,'Court persistence differs');
 });
 for(const [i,stage] of ['FIRST_INSTANCE','APPEAL','CASSATION'].entries()){
  await check('PROCEEDING-'+stage,['LD-019','LD-020','LD-021'][i],'Own case -> Record proceeding -> Choose owned court/parent -> Fill number/year/date -> Save','201; correct same-case stage/parent; row rendered',async()=>{
   id('cases',c);id('courts',courts[stage]);await page.goto(base+'/cases/'+c.id+'?lang=en');await page.getByRole('button',{name:'Record proceeding',exact:true}).click();const f=page.getByRole('form',{name:'Record proceeding',exact:true});
   await field(f,'Stage').selectOption(stage);await field(f,'Court').selectOption(courts[stage].id);await field(f,'Case number at the court').fill(prefix+'-'+i);await field(f,'Year').fill('2026');await field(f,'Filing date').fill(['2026-01-02','2026-01-04','2026-01-06'][i]);
   if(i>0){const parent=proceedings[['FIRST_INSTANCE','APPEAL'][i-1]];id('proceedings',parent);await field(f,'Proceeding attacked').selectOption(parent.id);}
   proceedings[stage]=own('proceedings',await submit('/api/v1/cases/'+c.id+'/proceedings','POST',f.getByRole('button',{name:'Save',exact:true})));
   await page.getByRole('heading',{name:new RegExp(prefix+'-'+i)}).waitFor();await snap('proceeding-'+stage);
  });
  if(i<2)await check('JUDGMENT-'+stage,'LD-022','Locate owned proceeding -> Record judgment -> Fill date/outcome/summary -> Save','201; judgment visible on same case',async()=>{
   const p=proceedings[stage];id('proceedings',p);const article=page.getByRole('article',{name:new RegExp(prefix+'-'+i)});await article.getByRole('button',{name:'Record judgment',exact:true}).click();const f=page.getByRole('form',{name:new RegExp('Record judgment for '+prefix+'-'+i)});
   await field(f,'Judgment date').fill(i===0?'2026-01-03':'2026-01-05');await field(f,'Outcome').selectOption('IN_FAVOUR');await field(f,'Summary').fill(prefix+' حكم اختبار');
   judgments[stage]=own('judgments',await submit('/api/v1/proceedings/'+p.id+'/judgment','POST',f.getByRole('button',{name:'Save',exact:true})));
  });
 }
 await check('SPECIAL-DECISION','LD-023','Own appeal -> Record special decision -> Choose suspension, judgment and Jan7 -> Save','201; owned proceeding/judgment linked',async()=>{
  const p=proceedings.APPEAL;id('proceedings',p);id('judgments',judgments.APPEAL);const article=page.getByRole('article',{name:new RegExp(prefix+'-1')});await article.getByRole('button',{name:'Record special decision',exact:true}).click();const f=page.getByRole('form',{name:new RegExp('Record special decision for '+prefix+'-1')});
  await field(f,'Decision type').selectOption('ENFORCEMENT_SUSPENSION');await field(f,'Decision date').fill('2026-01-07');await field(f,'Effect').selectOption('GRANTED');await field(f,'Judgment concerned').selectOption(judgments.APPEAL.id);await field(f,'Summary').fill(prefix+' قرار اختبار');
  own('decisions',await submit('/api/v1/proceedings/'+p.id+'/special-decisions','POST',f.getByRole('button',{name:'Save',exact:true})));await snap('judicial-full-chain');
 });
 await check('PASSWORD-RESET-UI','LD-008,LD-009','Login disposable user in session A -> Complete forced change -> Admin resets through user form','UI reset204; old session revoked with401',async()=>{
  id('users',user);oldSession=await browser.newContext();const p=await oldSession.newPage();await login(p,{username:user.username,password:temporary});const newPassword=randomPassword();
  await field(p,'Current password').fill(temporary);await field(p,'New password').fill(newPassword);await field(p,'Repeat the new password').fill(newPassword);const response=p.waitForResponse(r=>r.url().endsWith('/api/v1/me/password'));await p.getByRole('button',{name:'Change password',exact:true}).click();assert((await response).status()===204,'Forced change failed');
  await page.goto(base+'/admin/users/'+user.id+'?lang=en');resetPassword=randomPassword();await field(page,'New temporary password').fill(resetPassword);await submit('/api/v1/users/'+user.id+'/password-reset','POST',page.getByRole('button',{name:'Reset password',exact:true}),204);
  await api('GET','/api/v1/cases/'+id('cases',c),undefined,401,oldSession);
 });
 await check('RESET-SESSION-REPLAY','LD-007,LD-008,LD-009','Fresh session B logs in using reset password -> Completes forced change -> Old session A opens owned case without reauthentication','Old session remains invalid after recovery, according to revised session-revocation contract',async()=>{
  need(resetPassword&&oldSession,'Reset fixture missing');resetSession=await browser.newContext();const p=await resetSession.newPage();await login(p,{username:user.username,password:resetPassword});await field(p,'Current password').fill(resetPassword);await field(p,'New password').fill(randomPassword());const next=await field(p,'New password').inputValue();await field(p,'Repeat the new password').fill(next);const response=p.waitForResponse(r=>r.url().endsWith('/api/v1/me/password'));await p.getByRole('button',{name:'Change password',exact:true}).click();assert((await response).status()===204,'Recovery change failed');
  const oldPage=await oldSession.newPage();await oldPage.goto(base+'/cases/'+c.id+'?lang=en');const r=await oldSession.request.get(base+'/api/v1/cases/'+c.id);if(r.status()===200){await oldPage.getByRole('heading',{name:new RegExp(prefix)}).first().waitFor();await snap('FAIL-pre-reset-session-replay',oldPage);}
  assert(r.status()===401,'Pre-reset browser session still reads own case after recovery: '+r.status());
 });
}catch(e){results.push({id:'FORM-SETUP',status:'BLOCKED',actual:'Runtime or credential setup unavailable; no secret details recorded'});}
finally{
 if(ctx&&user?.id&&owned.users?.includes(user.id)){try{await api('PUT','/api/v1/users/'+user.id+'/enabled',{enabled:false});}catch{results.push({id:'FORM-CLEANUP',status:'BLOCKED',actual:'Owned disposable user quarantine failed'});}}
 for(const context of [oldSession,resetSession,ctx].filter(Boolean)){try{await api('POST','/api/v1/auth/logout',undefined,204,context);}catch{}await context.close();}
 if(browser)await browser.close();save();process.exitCode=results.some(r=>r.status==='FAIL')?1:results.some(r=>r.status==='BLOCKED')?2:0;
}
