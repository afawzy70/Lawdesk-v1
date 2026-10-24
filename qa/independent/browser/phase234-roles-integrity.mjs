// Independent live role matrix and judicial integrity. Credentials stay in memory; only synthetic owned writes.
import fs from 'node:fs';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
const require = createRequire('/tmp/lawdesk-qa-browser-runtime/package.json');
const { chromium } = require('playwright');
const [base, sha, output] = process.argv.slice(2);
if (base !== 'https://lawdesk.167-86-90-174.sslip.io' || !/^[a-f0-9]{40}$/.test(sha || '') || !output) throw Error('Approved UAT URL, full SHA and evidence directory required');
fs.mkdirSync(output, { recursive: true });
if (fs.existsSync(output + '/results.json')) throw Error('Never overwrite evidence');
const prefix = 'QA' + Date.now().toString(36).toUpperCase() + crypto.randomBytes(3).toString('hex').toUpperCase();
const started = new Date().toISOString(), results = [], fixtures = {}, secrets = [], contexts = {}, credentials = {};
let serial = 0, browser, c, first, firstJudgment, appeal, appealJudgment, disposable, resetTemporary;
const courts = {};
const assert = (ok, message) => { if (!ok) throw Error(message); };
class Blocked extends Error {}
const need = (value, message) => { if (!value) throw new Blocked(message); return value; };
const redact = value => { let s = JSON.stringify(value); for (const secret of secrets) s = s.split(secret).join('[REDACTED]'); return JSON.parse(s); };
const pw = () => { const value = crypto.randomBytes(24).toString('base64url') + '!9a'; secrets.push(value); return value; };
const save = () => fs.writeFileSync(output + '/results.json', JSON.stringify(redact({ started, updated: new Date().toISOString(), base,
  expected_deployed_sha: sha, backend_runtime_sha_attested: false, prefix,
  scope: 'Independent live browser/API; no developer tests; persistent API reads assess integrity, no direct PostgreSQL access',
  counts: Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s => [s, results.filter(r => r.status === s).length])), fixtures, results }), null, 2));
const own = (kind, value) => { need(value?.id, 'Owned fixture creation failed'); (fixtures[kind] ??= []).push(value.id); save(); return value; };
const id = (kind, value) => { need(value?.id && fixtures[kind]?.includes(value.id), 'Missing owned ' + kind); return value.id; };
async function check(name, stories, steps, expected, fn, priority='P1') {
  const row = { id: name, story_ids: stories.split(','), priority, preconditions: ['Approved QA accounts, verified TLS, no real-record mutations', 'All write targets created by this execution'],
    test_data: { prefix, dates: '2026-01-02..2026-01-10' }, steps: steps.split(' -> '), expected_results: expected, status: 'NOT_RUN' };
  results.push(row); save();
  try { row.actual = (await fn()) ?? 'All assertions satisfied'; row.status = 'PASS'; }
  catch(e) { row.status = e instanceof Blocked ? 'BLOCKED' : 'FAIL'; row.actual = redact(String(e.message).split('\n')[0]); row.triage_required = row.status === 'FAIL'; }
  save();
}
async function call(ctx, method, path, body, expected=200) {
  need(ctx, 'Role session unavailable');
  const headers = { 'X-Request-Id': prefix + '-' + (++serial) };
  if (method !== 'GET') {
    await ctx.request.get(base + '/api/v1/auth/csrf');
    const cookie = (await ctx.cookies(base)).find(c => c.name === 'XSRF-TOKEN');
    need(cookie, 'CSRF cookie unavailable'); headers['X-XSRF-TOKEN'] = decodeURIComponent(cookie.value);
  }
  const r = await ctx.request.fetch(base + path, { method, headers, ...(body === undefined ? {} : {data:body}) });
  let data = null; try { data = await r.json(); } catch {}
  const result = { status: r.status(), data, requestId: headers['X-Request-Id'] };
  if (expected !== null) assert(r.status() === expected, `${method} ${path}: expected ${expected}, actual ${r.status()}, rule=${data?.rule ?? 'none'}`);
  return result;
}
const api = async (...args) => (await call(...args)).data;
const casePath = value => '/api/v1/cases/' + id('cases', value);
const newCase = async (ctx, suffix) => own('cases', await api(ctx, 'POST', '/api/v1/cases', { referenceNo: prefix + '-' + suffix, title: prefix + ' ' + suffix, caseType:'CIVIL', openedOn:'2026-01-02' },201));
const procBody = (stage, number, date, parent) => ({ stage, courtId:id('courts',courts[stage]), courtCaseNumber: prefix + '-' + number, courtCaseYear:2026, filingDate:date, parentProceedingId:parent?.id ?? null });
const proc = async (ctx, legalCase, stage, number, date, parent) => own('proceedings',await api(ctx,'POST',casePath(legalCase)+'/proceedings',procBody(stage,number,date,parent),201));
const judgmentBody = date => ({ judgmentDate:date,outcome:'IN_FAVOUR',summary:prefix,final:false });
const judge = async (ctx,p,date) => own('judgments',await api(ctx,'POST','/api/v1/proceedings/'+id('proceedings',p)+'/judgment',judgmentBody(date),201));

try {
  browser = await chromium.launch({headless:true,executablePath:process.env.LAWDESK_QA_CHROMIUM});
  for (const role of ['admin','manager','staff','viewer']) {
    await check('ROLE-LOGIN-'+role,'LD-007,LD-010','Read approved credential internally -> Browser login -> GET /me','200; approved identity and role; no forced password change',async()=>{
      const path = '/srv/ai-workspaces/secrets/lawdesk-qa/' + role + '.json'; const stat = fs.statSync(path);
      need(stat.uid === process.getuid() && !(stat.mode & 0o077), 'Credential file permissions not approved');
      const cred = JSON.parse(fs.readFileSync(path,'utf8')); secrets.push(cred.password); credentials[role]=cred;
      const ctx = contexts[role] = await browser.newContext(); const p = await ctx.newPage(); p.setDefaultTimeout(15000);
      await p.goto(base+'/cases?lang=en&q='+prefix);
      await p.getByLabel('Username or email',{exact:true}).fill(cred.username);
      await p.getByLabel('Password',{exact:true}).fill(cred.password);
      const response = p.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login')&&r.request().method()==='POST');
      await p.getByRole('button',{name:'Sign in',exact:true}).click(); assert((await response).status()===200,'Browser login status differs');
      const me=await api(ctx,'GET','/api/v1/me');
      const expectedRole={admin:'ADMIN',manager:'LEGAL_MANAGER',staff:'LEGAL_STAFF',viewer:'QA_READ_ONLY'}[role];
      assert(me.username===cred.username && me.roles.includes(expectedRole) && !me.mustChangePassword,'Account contract differs');
      return {role:expectedRole,permissions:me.permissions};
    });
  }
  const admin=contexts.admin;
  await check('FIXTURE-CASE-COURTS','LD-012,LD-018','Create owned case -> Create each owned court level','Case and courts201',async()=>{
    c=await newCase(admin,'BASE');
    for (const [i,stage] of ['FIRST_INSTANCE','APPEAL','CASSATION'].entries()) courts[stage]=own('courts',await api(admin,'POST','/api/v1/courts',{code:prefix+'-'+i,nameAr:prefix+' محكمة '+i,nameEn:prefix+' Court '+i,level:stage},201));
  });
  for (const role of ['admin','manager','staff','viewer']) {
    const ctx=contexts[role], adminLike=role==='admin', manager=role==='manager', viewer=role==='viewer';
    await check('MATRIX-READ-'+role,'LD-009,LD-010,LD-011,LD-012,LD-018','GET own case/court -> GET users filtered QA -> GET own-run audit -> GET roles','Case/court200 all; users/audit200 admin+manager and403 staff+viewer; roles200 admin only',async()=>{
      await api(ctx,'GET',casePath(c)); await api(ctx,'GET','/api/v1/courts/'+id('courts',courts.FIRST_INSTANCE));
      await api(ctx,'GET','/api/v1/users?q='+prefix,undefined,adminLike||manager?200:403);
      await api(ctx,'GET','/api/v1/audit?actor='+encodeURIComponent(credentials[role].username)+'&from='+encodeURIComponent(started),undefined,adminLike||manager?200:403);
      await api(ctx,'GET','/api/v1/roles',undefined,adminLike?200:403);
    });
    await check('MATRIX-CASE-WRITE-'+role,'LD-010,LD-012,LD-013,LD-017','Create unique own case -> Edit own case -> Attempt state change','Create/edit allowed admin+manager+staff; viewer403; status change admin+manager only',async()=>{
      if(viewer){ await api(ctx,'POST','/api/v1/cases',{referenceNo:prefix+'-VIEW',title:prefix,caseType:'CIVIL',openedOn:'2026-01-02'},403); }
      else {
        let ownCase=await newCase(ctx,role.toUpperCase());
        ownCase=await api(ctx,'PUT',casePath(ownCase),{title:prefix+' edited '+role,caseType:'CIVIL',openedOn:'2026-01-02',version:ownCase.version});
        await api(ctx,'POST',casePath(ownCase)+'/status',{status:'ACTIVE',reason:prefix,version:ownCase.version},adminLike||manager?200:403);
      }
    });
    await check('MATRIX-COURT-WRITE-'+role,'LD-010,LD-018','POST unique valid court -> For denied role repeat malformed payload','201 admin+manager;403 staff+viewer regardless of payload validity',async()=>{
      const data=await api(ctx,'POST','/api/v1/courts',{code:prefix+'-'+role.toUpperCase(),nameAr:prefix,nameEn:prefix,level:'FIRST_INSTANCE'},adminLike||manager?201:403);
      if(adminLike||manager)own('courts',data);else await api(ctx,'POST','/api/v1/courts',{},403);
    });
    await check('MATRIX-PROCEEDING-JUDGMENT-'+role,'LD-010,LD-019,LD-022','Create proceeding on owned case -> Record judgment if proceeding created','Proceeding201 admin+manager+staff/403 viewer; judgment201 admin+manager/403 staff',async()=>{
      if(viewer) await api(ctx,'POST',casePath(c)+'/proceedings',procBody('FIRST_INSTANCE','DENY','2026-01-02'),403);
      else {
        const p=await proc(ctx,c,'FIRST_INSTANCE',role,'2026-01-02');
        if(adminLike||manager)await judge(ctx,p,'2026-01-03');
        else await api(ctx,'POST','/api/v1/proceedings/'+p.id+'/judgment',judgmentBody('2026-01-03'),403);
      }
    });
    if(!adminLike)await check('MATRIX-ADMIN-DENIED-'+role,'LD-009,LD-010','POST role -> POST user with valid synthetic data','403 for manager/staff/viewer; no privilege escalation',async()=>{
      await api(ctx,'POST','/api/v1/roles',{code:prefix+'_'+role.toUpperCase(),name:prefix,permissions:['ROLE_MANAGE']},403);
      await api(ctx,'POST','/api/v1/users',{username:prefix.toLowerCase()+role,email:prefix.toLowerCase()+role+'@example.invalid',displayName:prefix,locale:'en',roles:['ADMIN'],temporaryPassword:pw()},403);
    });
  }
  await check('JUDICIAL-BASE-CHAIN','LD-019,LD-020,LD-022','Create owned first-instance Jan2 -> Judgment Jan3 -> Appeal Jan4 -> Judgment Jan5','All201; chain same case',async()=>{
    first=await proc(admin,c,'FIRST_INSTANCE','CHAIN','2026-01-02'); firstJudgment=await judge(admin,first,'2026-01-03');
    appeal=await proc(admin,c,'APPEAL','AP','2026-01-04',first); appealJudgment=await judge(admin,appeal,'2026-01-05');
  });
  await check('JUDICIAL-INVALID-ATOMIC','LD-019,LD-020,LD-021','Read own proceedings/history -> Wrong level, future filing, cross-case parent, challenge before judgment -> Reread','400/422 rejects leave proceeding/history counts unchanged',async()=>{
    id('judgments',firstJudgment);const path=casePath(c);
    const before=await api(admin,'GET',path+'/proceedings?size=100'); const history=await api(admin,'GET',path+'/timeline?size=100');
    await api(admin,'POST',path+'/proceedings',{...procBody('FIRST_INSTANCE','BAD','2026-01-02'),courtId:courts.APPEAL.id},422);
    await api(admin,'POST',path+'/proceedings',procBody('FIRST_INSTANCE','FUT','2099-01-02'),400);
    await api(admin,'POST',path+'/proceedings',procBody('APPEAL','EARLY','2026-01-02',first),422);
    const other=await newCase(admin,'OTHER');
    await api(admin,'POST',casePath(other)+'/proceedings',procBody('APPEAL','CROSS','2026-01-04',first),422);
    const after=await api(admin,'GET',path+'/proceedings?size=100');const afterHistory=await api(admin,'GET',path+'/timeline?size=100');
    assert(before.totalItems===after.totalItems && history.totalItems===afterHistory.totalItems,'Rejected judicial operations left persisted side effects');
    return {proceedingsBefore:before.totalItems,proceedingsAfter:after.totalItems,historyBefore:history.totalItems,historyAfter:afterHistory.totalItems};
  });
  await check('CASSATION-CHRONOLOGY','LD-021,LD-022','Create cassation Jan6 -> Move appeal judgment to Jan10 -> Read judgment','422; original date/version retained',async()=>{
    id('judgments',appealJudgment); await proc(admin,c,'CASSATION','CA','2026-01-06',appeal);
    await api(admin,'PUT','/api/v1/judgments/'+appealJudgment.id,{...judgmentBody('2026-01-10'),version:appealJudgment.version},422);
    const after=await api(admin,'GET','/api/v1/judgments/'+appealJudgment.id);
    assert(after.judgmentDate==='2026-01-05'&&after.version===appealJudgment.version,'Rejected appeal judgment edit persisted');
  });
  for(const challengeStage of ['APPEAL','CASSATION'])for(let round=0;round<3;round++) {
    await check('RACE-'+challengeStage+'-'+round,'LD-020,LD-021,LD-022','Create fresh owned parent judgment -> Concurrently move judgment Jan10 and create challenge before Jan10 -> Read both','Exactly one incompatible write accepted; no committed challenge predates parent judgment',async()=>{
      const rc=await newCase(admin,'R'+challengeStage.slice(0,2)+round);
      const raceKey=challengeStage.slice(0,2)+round;
      let parent=await proc(admin,rc,'FIRST_INSTANCE','RFI'+raceKey,'2026-01-02'); let j=await judge(admin,parent,'2026-01-03');
      if(challengeStage==='CASSATION'){ parent=await proc(admin,rc,'APPEAL','RAP'+raceKey,'2026-01-04',parent);j=await judge(admin,parent,'2026-01-05'); }
      const filing=challengeStage==='APPEAL'?'2026-01-04':'2026-01-06';
      const [edit,challenge]=await Promise.all([
        call(admin,'PUT','/api/v1/judgments/'+j.id,{...judgmentBody('2026-01-10'),version:j.version},null),
        call(contexts.manager,'POST',casePath(rc)+'/proceedings',procBody(challengeStage,'RC'+round,filing,parent),null)
      ]);
      if(challenge.status===201)own('proceedings',challenge.data);
      const after=await api(admin,'GET','/api/v1/judgments/'+j.id);const children=(await api(admin,'GET',casePath(rc)+'/proceedings?size=100')).items.filter(p=>p.parentProceedingId===parent.id);
      assert((edit.status===200&&challenge.status===422)||(edit.status===422&&challenge.status===201),'Unexpected race status pair '+edit.status+'/'+challenge.status);
      assert(children.every(p=>p.filingDate>=after.judgmentDate),'Committed challenge predates parent judgment');
      return {edit_status:edit.status,challenge_status:challenge.status,parent_judgment_date:after.judgmentDate,challenge_dates:children.map(p=>p.filingDate)};
    });
  }
  await check('PASSWORD-POSITIVE-BOUNDARIES','LD-007,LD-008,LD-009','Create disposable QA user -> Force change -> Set 72 ASCII bytes -> Set36 Arabic chars -> Login after each -> Reset password','Accepted exact boundaries authenticate; reset forces password change and blocks protected resources per current contract',async()=>{
    const initial=pw(), normal=pw(), ascii=crypto.randomBytes(36).toString('hex'), arabic=Array.from(crypto.randomBytes(36),b=>String.fromCharCode(0x0621+(b%26))).join('');secrets.push(ascii,arabic);
    disposable=own('users',await api(admin,'POST','/api/v1/users',{username:prefix.toLowerCase()+'pw',email:prefix.toLowerCase()+'pw@example.invalid',displayName:prefix+' password fixture',locale:'en',roles:['QA_READ_ONLY'],temporaryPassword:initial},201));
    const ctx=contexts.disposable=await browser.newContext();await api(ctx,'POST','/api/v1/auth/login',{login:disposable.username,password:initial});
    let current=initial;
    for(const next of [normal,ascii,arabic]){
      await api(ctx,'POST','/api/v1/me/password',{currentPassword:current,newPassword:next},204);current=next;
      const fresh=await browser.newContext();try {await api(fresh,'POST','/api/v1/auth/login',{login:disposable.username,password:current});await api(fresh,'POST','/api/v1/auth/logout',undefined,204);}finally{await fresh.close();}
    }
    resetTemporary=pw();
    await api(admin,'POST','/api/v1/users/'+disposable.id+'/password-reset',{temporaryPassword:resetTemporary},204);
    const me=await api(ctx,'GET','/api/v1/me');assert(me.mustChangePassword,'Existing session did not observe forced-change flag');
    const denial=await api(ctx,'GET',casePath(c),undefined,403);assert(denial.rule==='PASSWORD_CHANGE_REQUIRED','Forced-change restriction differs');
    const u=await api(admin,'GET','/api/v1/users/'+disposable.id);assert(u.mustChangePassword,'Reset did not force password change');
    return {ascii_bytes:72,arabic_chars:36,arabic_bytes:Buffer.byteLength(arabic),old_session_protected_resource_status:403};
  });
  await check('SECURITY-PRE-RESET-SESSION','LD-007,LD-008,LD-009','Preserve pre-reset session A -> Admin resets password -> Login fresh session B with new temporary password -> Complete forced change in B -> Read own QA case through old session A',
    'Security expectation: pre-reset session cannot resume access after recovery; current acceptance criteria do not explicitly specify session revocation',async()=>{
      need(resetTemporary,'Password reset fixture missing');id('users',disposable);
      const recovered=contexts.recovered=await browser.newContext();
      await api(recovered,'POST','/api/v1/auth/login',{login:disposable.username,password:resetTemporary});
      await api(recovered,'POST','/api/v1/me/password',{currentPassword:resetTemporary,newPassword:pw()},204);
      const old=await call(contexts.disposable,'GET',casePath(c),undefined,null);
      fs.writeFileSync(output+'/pre-reset-session.json',JSON.stringify({user_id:disposable.id,case_id:c.id,recovery_completed:true,pre_reset_session_status:old.status,credential_material_recorded:false},null,2));
      assert(old.status===401,'Pre-reset session resumed protected case access after recovery: HTTP '+old.status);
    });
  await check('BACKEND-IDENTITY-DIAGNOSTIC','LD-002,LD-006','GET /actuator/info with approved admin session -> Inspect only non-sensitive git/build fields','Exact running backend git SHA exposed by documented safe mechanism, otherwise BLOCKED',async()=>{
    const r=await call(admin,'GET','/actuator/info',undefined,null);
    const commit=r.data?.git?.commit?.id?.full ?? r.data?.git?.commit?.id;
    if(r.status!==200||typeof commit!=='string')throw new Blocked('Approved /actuator/info exposes no commit identity; HTTP '+r.status+'; deployment workflow provenance is separate');
    assert(commit===sha,'Runtime SHA differs from candidate');return {status:r.status,git_commit:commit};
  });
} finally {
  if(disposable?.id&&contexts.admin){try{await api(contexts.admin,'PUT','/api/v1/users/'+id('users',disposable)+'/enabled',{enabled:false});}catch{results.push({id:'CLEANUP-DISPOSABLE',status:'BLOCKED',actual:'Could not disable newly created disposable user; inspect owned fixture manifest'});}}
  for(const ctx of Object.values(contexts)){try{await api(ctx,'POST','/api/v1/auth/logout',undefined,204);}catch{}await ctx.close();}
  if(browser)await browser.close();save();
  process.exitCode=results.some(r=>r.status==='FAIL')?1:results.some(r=>r.status==='BLOCKED')?2:0;
}
