// Approved non-sensitive UAT runtime identity. Never serialize credentials, cookies or CSRF values.
import fs from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire('/tmp/lawdesk-qa-browser-runtime/package.json');
const {request}=require('playwright');
const [sha,out]=process.argv.slice(2),base='https://lawdesk.167-86-90-174.sslip.io',results=[];
if(!/^[a-f0-9]{40}$/.test(sha||''))throw Error('Exact SHA required');
fs.mkdirSync(out,{recursive:true});
if(fs.existsSync(out+'/results.json'))throw Error('Refusing evidence overwrite');
function save(){fs.writeFileSync(out+'/results.json',JSON.stringify({utc:new Date().toISOString(),expected_deployed_sha:sha,results,counts:Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s=>[s,results.filter(r=>r.status===s).length]))},null,2));}
function record(row){results.push({...row,story_ids:['LD-006','LD-007','LD-010'],priority:'P1',preconditions:['Verified TLS; approved QA account'],test_data:{expected_sha:sha},steps:['Use the indicated approved role, or a fresh anonymous context','GET /api/v1/release','Compare exact SHA and single safe response field'],expected_results:row.id==='RELEASE-ANONYMOUS'?{status:401}:{release:sha,status:200,fields:['release']}});save();}
const anon=await request.newContext({baseURL:base});
try{const r=await anon.get('/api/v1/release');record({id:'RELEASE-ANONYMOUS',status:r.status()===401?'PASS':'FAIL',http_status:r.status()});}
catch{record({id:'RELEASE-ANONYMOUS',status:'BLOCKED',reason:'Network request did not complete'});}
finally{await anon.dispose();}
for(const role of ['admin','manager','staff','viewer']){
 const f='/srv/ai-workspaces/secrets/lawdesk-qa/'+role+'.json',st=fs.statSync(f);
 if(st.uid!==process.getuid()||(st.mode&0o077))throw Error('Credential mode mismatch');
 const credential=JSON.parse(fs.readFileSync(f));
 const ctx=await request.newContext({baseURL:base});let signedIn=false;
 async function post(path,data){
  const csrf=await ctx.get('/api/v1/auth/csrf');
  const token=(await ctx.storageState()).cookies.find(x=>x.name==='XSRF-TOKEN');
  if(csrf.status()!==204||!token)throw Error('CSRF precondition unavailable; HTTP '+csrf.status());
  return ctx.post(path,{data,headers:{'X-XSRF-TOKEN':decodeURIComponent(token.value)}});
 }
 try{
  const login=await post('/api/v1/auth/login',{login:credential.username,password:credential.password});
  if(login.status()!==200){record({id:'RELEASE-'+role,status:login.status()>=500||login.status()===429?'BLOCKED':'FAIL',login_http_status:login.status(),reason:'Approved authentication did not succeed; release unverified'});continue;}
  signedIn=true;
  const r=await ctx.get('/api/v1/release');
  if(r.status()!==200){record({id:'RELEASE-'+role,status:r.status()>=500||r.status()===429?'BLOCKED':'FAIL',http_status:r.status()});continue;}
  const b=await r.json();
  record({id:'RELEASE-'+role,status:b.release===sha&&Object.keys(b).length===1?'PASS':'FAIL',http_status:r.status(),release:/^[a-f0-9]{40}$/.test(b.release)?b.release:'INVALID_RELEASE_VALUE',field_names:Object.keys(b)});
 }catch(e){record({id:'RELEASE-'+role,status:'BLOCKED',reason:String(e.message).startsWith('CSRF precondition')?e.message:'Network or response precondition did not complete; no sensitive diagnostic saved'});}
 finally{
  if(signedIn){try{const r=await post('/api/v1/auth/logout');if(r.status()!==204)results.at(-1).cleanup_http_status=r.status();}catch{results.at(-1).cleanup_warning='Logout unavailable; local context disposed';}}
  await ctx.dispose();save();
 }
}
console.log(JSON.stringify({counts:Object.fromEntries(['PASS','FAIL','BLOCKED','NOT_RUN'].map(s=>[s,results.filter(r=>r.status===s).length])),observed_releases:[...new Set(results.map(r=>r.release).filter(Boolean))]}));
process.exitCode=results.some(r=>r.status!=='PASS')?1:0;
