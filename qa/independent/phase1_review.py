"""Independent Phase 1 assertions against an immutable, isolated PR snapshot.
Product baseline cases remain separate from these narrower assertion results.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def redact_headers(headers):
    result = dict(headers)
    for key,value in headers.items():
        if key.lower() == 'set-cookie':
            result[key] = re.sub(r'^([^=;]+)=([^;]*)',r'\1=[REDACTED]',value)
        elif key.lower() in ('authorization','proxy-authorization'):
            result[key] = '[REDACTED]'
    return result

class Review:
    def __init__(self, candidate, sha, output):
        self.candidate = Path(candidate).resolve(); self.sha = sha
        self.output = Path(output); self.output.mkdir(parents=True, exist_ok=True)
        self.results = []
        tree = subprocess.check_output(['git','ls-tree','-rz',sha],cwd=ROOT)
        for entry in tree.split(b'\0'):
            if not entry: continue
            metadata, name = entry.split(b'\t',1)
            mode, kind, blob = metadata.decode().split()
            if kind != 'blob': raise ValueError('Unexpected non-blob in candidate')
            data = (self.candidate/name.decode()).read_bytes()
            actual = hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
            if actual != blob: raise ValueError('Candidate differs from tested SHA: '+name.decode())
        module = importlib.util.spec_from_file_location('subject_policy',self.candidate/'tools/repo_policy.py')
        self.policy = importlib.util.module_from_spec(module); module.loader.exec_module(self.policy)

    def check(self, id, case, expected, action, data):
        row = dict(id=id, baseline_case_ids=case, preconditions=['Exact candidate tree checked against git object hashes.','Isolated QA snapshot; synthetic data only.'],test_data=data,steps=[action.__doc__ or id],expected=expected,status='NOT_RUN')
        try:
            actual, ok = action()
            row.update(actual=actual,status='PASS' if ok else 'FAIL')
            if not ok: row['failed_assertion']=expected
        except Exception as error:
            row.update(status='BLOCKED',actual=type(error).__name__)
        self.results.append(row)

    def policy_checks(self):
        for i,name in enumerate(['feature/a.lock','feature/a.','feature/a\n']):
            def action(name=name):
                "Compare Git's native ref validator with the implementation policy for the same name."
                native = subprocess.run(['git','check-ref-format','--branch',name],capture_output=True).returncode
                errors = self.policy.check_branch_name(name)
                return {'git_exit':native,'policy_errors':errors}, native != 0 and bool(errors)
            self.check(f'P1-BRANCH-INVALID-{i+1}',['QA-0003'],'Git-invalid name is rejected by policy',action,{'branch':name})
        for name in ['feature/LD-002-health','qa/phase-1-review','main','develop']:
            def action(name=name):
                "Validate a documented allowed branch."
                errors=self.policy.check_branch_name(name)
                return errors,not errors
            self.check('P1-BRANCH-VALID-'+name,['QA-0001'],'Allowed branch accepted',action,{'branch':name})
        for n in [71,72,73]:
            subject='feat(LD-001): '+'a'*(n-len('feat(LD-001): '))
            def action(subject=subject,n=n):
                "Validate Conventional Commit subject at 71/72/73 character boundary."
                errors=self.policy.check_commit_message(subject)
                return errors,bool(errors)==(n>72)
            self.check(f'P1-COMMIT-{n}',['QA-0003'],'Only >72 character subject rejected',action,{'subject':subject})
        for head,base,allowed in [('feature/a','develop',True),('qa/a','develop',True),('feature/a','main',False),('release/v1','main',True),('hotfix/a','main',True),('feature/a','unknown',False)]:
            def action(head=head,base=base,allowed=allowed):
                "Validate documented source and target branch flow."
                errors=self.policy.check_pull_request(head,base)
                return errors,bool(errors)!=allowed
            self.check(f'P1-FLOW-{head}-{base}',['QA-0001','QA-0005'],'PR flow follows documented policy',action,{'head':head,'base':base})

    def request(self, path, method='GET', headers=None):
        req=urllib.request.Request(self.base+path,headers=headers or {},method=method,data=b'{}' if method=='POST' else None)
        try: response=urllib.request.build_opener(NoRedirect()).open(req,timeout=5)
        except urllib.error.HTTPError as error: response=error
        with response:
            return {'status':response.code,'headers':redact_headers(dict(response.headers)),'body':response.read(65536).decode('utf-8')}

    def http_checks(self):
        for path in ['/actuator/health','/actuator/health/liveness','/actuator/health/readiness']:
            def action(path=path):
                "GET public health route without credentials; parse exact health state and inspect details."
                r=self.request(path); body=json.loads(r['body'])
                return r,r['status']==200 and body.get('status')=='UP' and not ({'components','details'} & set(body))
            self.check('P1-HEALTH-'+path,['QA-0009'],'200 UP; no components/details',action,{'path':path})
        def info():
            "GET public info; compare build fields to proposed LD-002 AC2 name/version-only allowlist."
            r=self.request('/actuator/info'); body=json.loads(r['body'])
            return r,r['status']==200 and set(body)=={'build'} and set(body['build'])=={'name','version'}
        self.check('P1-INFO-ALLOWLIST',['QA-0009'],'Only build.name and build.version are public (proposed AC2)',info,{'path':'/actuator/info'})
        for path in ['/api/v1/cases','/actuator/env','/actuator/beans','/actuator/configprops','/actuator/heapdump','/actuator/loggers','/actuator/mappings','/actuator/threaddump']:
            def action(path=path):
                "GET protected route as anonymous; assert exact 401 RFC9457 JSON, no redirect or internals."
                r=self.request(path); b=json.loads(r['body'])
                return r,r['status']==401 and b.get('status')==401 and b.get('type')=='about:blank' and r['headers'].get('Content-Type','').startswith('application/problem+json') and not any(s in r['body'] for s in ['trace','exception','org.springframework'])
            self.check('P1-ANON-'+path,['QA-0012','QA-0013'],'401 application/problem+json; no internals',action,{'path':path})
        for path in ['/api/v1/cases','/actuator/health']:
            def action(path=path):
                "POST without session or CSRF token; compare to proposed AC3 exact anonymous 401 rule."
                r=self.request(path,'POST'); return r,r['status']==401
            self.check('P1-POST-STATUS-'+path,['QA-0012'],'401 for unauthenticated other endpoint per proposed AC3 (CSRF 403 requires contract clarification)',action,{'method':'POST','path':path})
        required={'X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'none'; frame-ancestors 'none'"}
        for path in ['/actuator/health','/api/v1/cases','/actuator/health/does-not-exist','//actuator/health']:
            def action(path=path):
                "Request success, security denial, MVC 404 or firewall rejection and inspect baseline security headers."
                r=self.request(path); h={k.lower():v for k,v in r['headers'].items()}
                ok=all(h.get(k.lower())==v for k,v in required.items()) and 'no-store' in h.get('cache-control','')
                return r,ok
            self.check('P1-HEADERS-'+path,['QA-0349'],'All baseline headers including no-store on every response per proposed AC4',action,{'path':path})
        for value in ['a','a'*64,'a'*65,'bad id','QA._-9']:
            def action(value=value):
                "Send X-Request-Id at valid/invalid and 1/64/65 character boundaries."
                r=self.request('/actuator/health',headers={'X-Request-Id':value}); echoed=r['headers'].get('X-Request-Id')
                if re.fullmatch(r'[A-Za-z0-9._-]{1,64}',value): ok=echoed==value
                else:
                    try: uuid.UUID(echoed); ok=echoed!=value
                    except (ValueError,TypeError,AttributeError): ok=False
                return {'sent':value,'received':echoed,'status':r['status']},ok
            self.check('P1-REQUEST-ID-'+str(len(self.results)),['QA-0011'],'Valid id echoed; invalid/oversized id replaced by UUID',action,{'request_id':value})
        def concurrent():
            "Send 20 concurrent health requests without caller IDs; assert 20 distinct nonempty UUIDs."
            with ThreadPoolExecutor(max_workers=8) as pool: replies=list(pool.map(lambda _:self.request('/actuator/health'),range(20)))
            ids=[r['headers'].get('X-Request-Id') for r in replies]
            return {'statuses':[r['status'] for r in replies],'ids':ids},all(r['status']==200 for r in replies) and len(set(ids))==20 and all(ids)
        self.check('P1-CONCURRENT-IDS',['QA-0015'],'20 successful requests, 20 distinct ids',concurrent,{'clients':20})

    def execute(self, java, jar):
        self.policy_checks()
        with socket.socket() as sock:
            sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
        self.base=f'http://127.0.0.1:{port}'
        logpath=self.output/'runtime-redacted.log'
        raw=Path('/tmp')/('lawdesk-independent-'+uuid.uuid4().hex+'.log')
        command=[java,'-jar',str(Path(jar).resolve()),'--server.address=127.0.0.1',f'--server.port={port}']
        with raw.open('w') as log:
            process=subprocess.Popen(command,cwd=self.candidate,stdout=log,stderr=subprocess.STDOUT)
            try:
                ready=False
                for _ in range(100):
                    if process.poll() is not None: break
                    try:
                        if self.request('/actuator/health')['status']==200: ready=True; break
                    except (OSError,urllib.error.URLError): pass
                    time.sleep(.25)
                if ready: self.http_checks()
                else: self.results.append({'id':'P1-RUNTIME','status':'BLOCKED','actual':'No healthy QA server within 25 seconds'})
            finally:
                if process.poll() is None:
                    process.terminate()
                    try: process.wait(timeout=25)
                    except subprocess.TimeoutExpired: process.kill(); process.wait()
        rawtext=raw.read_text()
        sanitized=re.sub(r'(Using generated security password: )[^\\\s]+',r'\1[REDACTED]',rawtext)
        logpath.write_text(sanitized)
        report={'tested_sha':self.sha,'integration_base':'91551a3','utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'candidate_source':'git archive with every tracked blob verified','jar_sha256':hashlib.sha256(Path(jar).read_bytes()).hexdigest(),'environment':{'java':subprocess.check_output([java,'-version'],stderr=subprocess.STDOUT,text=True).splitlines()[0],'bind':'loopback ephemeral port','postgresql':'disposable PostgreSQL configured' if os.environ.get('SPRING_DATASOURCE_URL') else 'no datasource in this run'},'contract':'PR #2 docs/delivery/PHASE-1-ACCEPTANCE.md PROPOSED, not approved','results':self.results,'assertion_counts':{s:sum(r['status']==s for r in self.results) for s in ['PASS','FAIL','BLOCKED','NOT_RUN']},'baseline_case_promotion':'NONE: these are partial assertions under a proposed contract, not full-case passes'}
        (self.output/'independent-results.json').write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps(report['assertion_counts']))
        return 1 if report['assertion_counts']['FAIL'] else 2 if report['assertion_counts']['BLOCKED'] else 0

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--candidate',required=True); parser.add_argument('--sha',required=True); parser.add_argument('--java',required=True); parser.add_argument('--jar',required=True); parser.add_argument('--output',required=True)
    args=parser.parse_args(); raise SystemExit(Review(args.candidate,args.sha,args.output).execute(args.java,args.jar))
