"""Independent CD failure-injection review. ALL Docker, SSH, curl and gh transport
is replaced locally. Never execute this against a real host or shared proxy.
"""
import argparse,datetime,json,os,subprocess,tempfile,shutil
from pathlib import Path
import yaml

DOCKER_GATE='''#!/usr/bin/python3
import json,os,sys
args=sys.argv[1:]
with open(os.environ['QA_TRACE'],'a') as f:f.write(json.dumps({'tool':'docker','args':args,'tag':os.environ.get('LAWDESK_TAG')})+'\\n')
if args[:2]==['image','inspect']:
 if os.environ.get('QA_CORRECT_IMAGE')=='1':print(args[-1].rsplit(':',1)[-1]);sys.exit(0)
 if os.environ.get('QA_WRONG_IMAGE')=='1':
  print('b'*40)
  sys.exit(0)
 sys.exit(1)
if 'pg_dump' in args:
 if os.environ.get('QA_DUMP_FAIL')=='1':sys.exit(41)
 print('QA-SYNTHETIC-DUMP-FIXTURE-NOT-A-REAL-PG-ARCHIVE');sys.exit(0)
if 'up' in args and os.environ.get('LAWDESK_TAG')==os.environ.get('QA_FAIL_UP'):sys.exit(1)
sys.exit(0)
'''
PROXY_DOCKER='''#!/usr/bin/python3
import os,sys,json,shutil,subprocess
from pathlib import Path
r=Path(os.environ['QA_PROXY_ROOT']);a=sys.argv[1:]
with (r/'trace').open('a') as f:f.write(json.dumps(a)+'\\n')
def mapped(remote):
 name=remote.split(':',1)[-1].split('/')[-1]
 return r/({'Caddyfile':'live','Caddyfile.lawdesk-candidate':'candidate','Caddyfile.lawdesk-previous':'previous','Caddyfile.lawdesk-check':'check'}.get(name,name))
if a[0]=='ps':print('qa-proxy');sys.exit(0)
if a[:2]==['network','inspect']:sys.exit(0)
if a[0]=='inspect':print('qa-shared-edge');sys.exit(0)
if a[:2] in (['network','connect'],['network','disconnect']):sys.exit(0)
if a[0]=='cp':
 src,dst=a[1:3]
 if src.startswith('qa-proxy:'):shutil.copyfile(mapped(src),mapped(dst) if dst.startswith('qa-proxy:') else dst)
 else:
  if dst.endswith('/Caddyfile') and os.environ.get('QA_FAIL_PERSIST')=='1':sys.exit(42)
  shutil.copyfile(src,mapped(dst))
 sys.exit(0)
if a[:3]==['exec','qa-proxy','printenv']:print('qa.invalid');sys.exit(0)
if a[:4]==['exec','qa-proxy','caddy','adapt']:
 config=mapped(a[a.index('--config')+1]);env={**os.environ,'PREVIEW_DOMAIN':'qa.invalid'}
 sys.exit(subprocess.run([os.environ['QA_REAL_CADDY'],'adapt','--config',str(config),'--adapter','caddyfile'],env=env).returncode)
if a[:4]==['exec','qa-proxy','caddy','validate']:
 config=mapped(a[a.index('--config')+1]);env={**os.environ,'PREVIEW_DOMAIN':'qa.invalid'}
 sys.exit(subprocess.run([os.environ['QA_REAL_CADDY'],'adapt','--config',str(config),'--adapter','caddyfile'],env=env,stdout=subprocess.DEVNULL).returncode)
if a[:4]==['exec','qa-proxy','caddy','reload']:shutil.copyfile(mapped(a[a.index('--config')+1]),r/'active');sys.exit(0)
sys.exit(2)
'''
FAKE_CURL='''#!/usr/bin/python3
import os,sys
from pathlib import Path
with (Path(os.environ['QA_PROXY_ROOT'])/'curl-trace').open('a') as f:f.write(sys.argv[-1]+'\\n')
print('200',end='')
'''
FIXTURE_DEPLOY='''#!/bin/sh
printf 'deployed %s\\n' "$1" >> "$QA_DEPLOY_TRACE"
printf '%s\\n' "$1" > "$LAWDESK_STATE_DIR/current-release"
'''

def git(where,*args):
    return subprocess.check_output(['git',*args],cwd=where,text=True,stderr=subprocess.DEVNULL).strip()

def executable(path,body):path.write_text(body);path.chmod(0o755)

class Review:
    def __init__(self,candidate,sha,out):
        self.candidate=Path(candidate).resolve();self.sha=sha;self.out=Path(out);self.out.mkdir(parents=True,exist_ok=True);self.results=[]
        for name in ['deploy/host/lawdesk-deploy-gate','deploy/host/bootstrap.sh','deploy/scripts/deploy.sh','deploy/proxy/attach.sh','deploy/proxy/lib.sh','.github/workflows/deploy-staging.yml']:
            if (self.candidate/name).read_bytes()!=subprocess.check_output(['git','show',sha+':'+name]):raise ValueError('Candidate differs: '+name)
    def record(self,id,expected,actual,ok,story='LD-006',priority='P0'):
        self.results.append({'id':id,'story_ids':[story],'priority':priority,'preconditions':['Exact candidate files; all transport replaced with QA doubles; temporary files only.'],'test_data':{'candidate_sha':self.sha,'fixture':id},'steps':['Run cd_review.py against the exact snapshot; inspect recorded transport and state.'],'expected_results':[expected],'actual':actual,'status':'PASS' if ok else 'FAIL'})
    def execute(self):
        with tempfile.TemporaryDirectory(prefix='lawdesk-qa-cd-') as tmp:
            root=Path(tmp);bin=root/'bin';bin.mkdir();trace=root/'trace';trace.touch();deploytrace=root/'deploy-trace';deploytrace.touch()
            executable(bin/'docker',DOCKER_GATE)
            # Disposable HOME belongs only to the child fixture, never the real user.
            env={'PATH':str(bin)+':/usr/bin:/bin','HOME':str(root/'user-home'),'LAWDESK_HOME':str(root/'lawdesk'),'QA_TRACE':str(trace),'QA_DEPLOY_TRACE':str(deploytrace)}
            Path(env['HOME']).mkdir();home=Path(env['LAWDESK_HOME']);home.mkdir()
            origin=root/'origin.git';work=root/'work'
            git(root,'init','-q','--bare','-b','develop',str(origin));git(root,'clone','-q',str(origin),str(work))
            git(work,'config','user.name','Independent QA fixture');git(work,'config','user.email','qa@example.invalid')
            for p in ['deploy/scripts','backend','frontend']:(work/p).mkdir(parents=True,exist_ok=True)
            executable(work/'deploy/scripts/deploy.sh',FIXTURE_DEPLOY)
            executable(work/'deploy/scripts/rollback.sh','#!/bin/sh\nexit 0\n')
            (work/'backend/Dockerfile').write_text('FROM scratch\n');(work/'frontend/Dockerfile').write_text('FROM scratch\n')
            git(work,'add','.');git(work,'commit','-q','-m','test: synthetic gate release');merged=git(work,'rev-parse','HEAD');git(work,'push','-q','origin','HEAD:develop')
            git(root,'clone','-q','--bare',str(origin),str(home/'repo.git'))
            gate=self.candidate/'deploy/host/lawdesk-deploy-gate'
            for i,request in enumerate(['deploy '+merged+'; status','deploy '+merged+'\nstatus','deploy $(id)','status extra']):
                trace.write_text('');r=subprocess.run(['bash',str(gate)],env={**env,'SSH_ORIGINAL_COMMAND':request},capture_output=True,text=True,timeout=15)
                self.record('SSH-REJECT-'+str(i+1),'Reject command injection/extra request before any Docker call.',{'request':request,'exit':r.returncode,'trace':trace.read_text()},r.returncode==2 and not trace.read_text())
            r=subprocess.run(['bash',str(gate)],env={**env,'SSH_ORIGINAL_COMMAND':'status'},capture_output=True,text=True,timeout=15)
            self.record('SSH-STATUS-CONTROL','Read-only status succeeds with no Docker commands.',{'exit':r.returncode,'stdout':r.stdout},r.returncode==0 and 'current=' in r.stdout)
            git(work,'checkout','-q','-b','feature/unmerged');(work/'unmerged').write_text('QA');git(work,'add','.');git(work,'commit','-q','-m','test: unmerged fixture');unmerged=git(work,'rev-parse','HEAD');git(work,'push','-q','origin','feature/unmerged')
            trace.write_text('');r=subprocess.run(['bash',str(gate)],env={**env,'SSH_ORIGINAL_COMMAND':'deploy '+unmerged},capture_output=True,text=True,timeout=15)
            self.record('SSH-UNMERGED-SHA','Reject real Git commit not on fresh origin/develop.',{'exit':r.returncode,'stderr':r.stderr,'docker_trace':trace.read_text()},r.returncode==5 and not trace.read_text())
            trace.write_text('');r=subprocess.run(['bash',str(gate)],env={**env,'SSH_ORIGINAL_COMMAND':'deploy '+merged,'QA_WRONG_IMAGE':'1'},capture_output=True,text=True,timeout=15)
            calls=[json.loads(x) for x in trace.read_text().splitlines()];deployed=deploytrace.read_text()
            self.record('CACHED-IMAGE-WRONG-REVISION','Refuse cached images labelled with another commit, or rebuild and verify correct image before deployment.',{'requested':merged,'cached_image_revision':'b'*40,'exit':r.returncode,'deploy_invoked':bool(deployed),'docker_calls':calls},r.returncode!=0 or (any(c['args'][0]=='build' for c in calls) and bool(deployed)))
            trace.write_text('');deploytrace.write_text('')
            r=subprocess.run(['bash',str(gate)],env={**env,'SSH_ORIGINAL_COMMAND':'deploy '+merged,'QA_CORRECT_IMAGE':'1'},capture_output=True,text=True,timeout=15)
            self.record('CACHED-IMAGE-CORRECT-CONTROL','Correct cached revision can deploy the requested merged commit; gate must not reject everything.',{'exit':r.returncode,'deploy_trace':deploytrace.read_text(),'docker_trace':trace.read_text()},r.returncode==0 and ('deployed '+merged) in deploytrace.read_text())
            # Actual workflow validation script with an API fixture whose CI state is FAILED.
            executable(bin/'gh','''#!/bin/sh
printf '%s\\n' "$*" >> "$QA_GH_TRACE"
case "$*" in
 *compare*) echo identical;;
 *check-runs*)
  printf '%s\\n' 'Repository policy' 'Backend (Java 21, embedded PostgreSQL 18.6)' 'Backend against postgres:18.6 image' 'Frontend (Node 22)' 'Staging stack (images + compose smoke)' 'Secret scan (gitleaks 8.30.1)'
  [ "${QA_CI_STATE:-failure}" = success ] && echo 'Develop guard (QA-cleared merge)'
  exit 0;;
 *) exit 99;; esac
''')
            gha=root/'gh-trace';gha.touch();outputs=root/'outputs';workflow=yaml.safe_load((self.candidate/'.github/workflows/deploy-staging.yml').read_text());steps=workflow['jobs']['deploy']['steps'];validation=next(x['run'] for x in steps if x.get('id')=='release')
            r=subprocess.run(['bash','-euo','pipefail','-c',validation],env={**env,'SHA':merged,'EVENT':'workflow_dispatch','REF':'refs/heads/develop','REPO':'qa/fixture','GH_TOKEN':'QA-FAKE-NOT-A-CREDENTIAL','GITHUB_OUTPUT':str(outputs),'QA_GH_TRACE':str(gha)},capture_output=True,text=True,timeout=15)
            self.record('MANUAL-DISPATCH-FAILED-CI','Fail closed when requested merged SHA has no successful exact-SHA CI evidence.',{'fixture_ci':'FAILURE','exit':r.returncode,'api_calls':gha.read_text(),'workflow_output':outputs.read_text() if outputs.exists() else ''},r.returncode!=0)
            gha.write_text('');outputs.write_text('')
            r=subprocess.run(['bash','-euo','pipefail','-c',validation],env={**env,'SHA':merged,'EVENT':'workflow_dispatch','REF':'refs/heads/develop','REPO':'qa/fixture','GH_TOKEN':'QA-FAKE-NOT-A-CREDENTIAL','GITHUB_OUTPUT':str(outputs),'QA_GH_TRACE':str(gha),'QA_CI_STATE':'success'},capture_output=True,text=True,timeout=15)
            self.record('MANUAL-DISPATCH-POSITIVE-CONTROL','All required exact-SHA gates success allows validation to complete.',{'exit':r.returncode,'api_calls':gha.read_text(),'outputs':outputs.read_text(),'stderr':r.stderr},r.returncode==0 and 'skip=false' in outputs.read_text() and 'check-runs' in gha.read_text())
            ci=yaml.safe_load((self.candidate/'.github/workflows/ci.yml').read_text());caller=ci['jobs']['deploy-staging']
            good=set(caller.get('needs',[]))=={'policy','backend','backend-postgres-image','frontend','stack','secrets','develop-guard'} and all(x in caller.get('if','') for x in ["github.event_name == 'push'","refs/heads/develop","afawzy70/Lawdesk-v1"])
            self.record('AUTOMATIC-CD-GATES','Automatic deploy caller requires all six gates plus develop-guard and only trusted develop push.',caller,good,story='LD-005')
            # Bootstrap existing unrestricted CI key must not silently remain unrestricted.
            ssh=Path(env['HOME'])/'.ssh';ssh.mkdir()
            subprocess.run(['ssh-keygen','-q','-t','ed25519','-N','','-C','qa-ci','-f',str(root/'ci-key')],check=True)
            public=(root/'ci-key.pub').read_text();key=root/'ci.pub';key.write_text(public);(ssh/'authorized_keys').write_text(public)
            (ssh/'lawdesk_github_ro').write_text('QA-NOT-A-REAL-PRIVATE-KEY');(ssh/'lawdesk_github_ro.pub').write_text(public);hostkey=root/'host.pub';hostkey.write_text(public)
            r=subprocess.run(['bash',str(self.candidate/'deploy/host/bootstrap.sh'),str(key)],env={**env,'LAWDESK_SSH_HOST_KEY':str(hostkey)},capture_output=True,text=True,timeout=15)
            entries=(ssh/'authorized_keys').read_text();unrestricted=any(line.split()[:2]==public.split()[:2] for line in entries.splitlines())
            self.record('BOOTSTRAP-EXISTING-UNRESTRICTED-KEY','Refuse an existing unrestricted matching CI key or constrain it without leaving an unrestricted authorization.',{'exit':r.returncode,'authorized_keys':entries,'unrestricted_entry_remains':unrestricted},r.returncode!=0 or not unrestricted)
            # Backup preflight and retention exercise the real deploy script; dumps are synthetic transport bytes.
            state=root/'state';state.mkdir();backups=root/'backups';backups.mkdir();a='a'*40;b='b'*40
            for id,extra in [('BACKUP-FAIL-CLOSED',{'QA_DUMP_FAIL':'1'}),('BACKUP-PERMISSIONS',{}),('BACKUP-RETENTION',{})]:
                (state/'current-release').write_text(a+'\n');trace.write_text('')
                if id=='BACKUP-RETENTION':
                    for n in range(12):
                        f=backups/f'old-{n:02d}.dump';f.write_text('QA old dump');os.utime(f,(1000+n,1000+n))
                    (backups/'keep.txt').write_text('unrelated QA note')
                r=subprocess.run(['bash',str(self.candidate/'deploy/scripts/deploy.sh'),b],env={**env,'LAWDESK_REGISTRY':'qa-local','LAWDESK_STATE_DIR':str(state),'LAWDESK_BACKUP_DIR':str(backups),**extra},capture_output=True,text=True,timeout=15)
                calls=[json.loads(x) for x in trace.read_text().splitlines()];files=list(backups.glob('*.dump'))
                actual={'exit':r.returncode,'current':(state/'current-release').read_text().strip(),'dump_count':len(files),'partial_count':len(list(backups.glob('*.partial'))),'file_modes':[oct(f.stat().st_mode&0o777) for f in files],'docker_calls':calls}
                if id=='BACKUP-FAIL-CLOSED':ok=r.returncode!=0 and not any('up' in x['args'] for x in calls) and actual['current']==a and actual['partial_count']==0;expected='Backup failure refuses deployment, removes partial, retains running release.'
                elif id=='BACKUP-PERMISSIONS':ok=r.returncode==0 and bool(files) and all((f.stat().st_mode&0o777)==0o600 for f in files);expected='Backup output written with0600 and successful dump then deployment. Not a pg_restore proof.'
                else:ok=r.returncode==0 and len(files)==10 and (backups/'keep.txt').exists();expected='Retain newest10 dumps without deleting unrelated files.'
                self.record(id,expected,actual,ok)
            # Every rollback command including smoke must use the target release's manifest.
            releases=root/'releases'
            for tag in [a,b]:
                d=releases/tag/'deploy/scripts';d.mkdir(parents=True);(d.parent/'compose.yaml').write_text('QA fixture manifest '+tag)
                for name in ['deploy.sh','smoke.sh','rollback.sh']:shutil.copy(self.candidate/'deploy/scripts'/name,d/name)
            (state/'current-release').write_text(a+'\n');trace.write_text('')
            r=subprocess.run(['bash',str(releases/b/'deploy/scripts/deploy.sh'),b],env={**env,'LAWDESK_REGISTRY':'qa-local','LAWDESK_STATE_DIR':str(state),'LAWDESK_RELEASES_DIR':str(releases),'QA_FAIL_UP':b},capture_output=True,text=True,timeout=15)
            calls=[json.loads(x) for x in trace.read_text().splitlines()];rollback=[c for c in calls if c['tag']==a];bad=[c for c in rollback if '-f' in c['args'] and c['args'][c['args'].index('-f')+1]!=str(releases/a/'deploy/compose.yaml')]
            self.record('ROLLBACK-SMOKE-TARGET-MANIFEST','Rollback up and smoke both use previous release own Compose manifest.',{'exit':r.returncode,'rollback_calls':rollback,'wrong_manifest_calls':bad},bool(rollback) and not bad)
            self.proxy_cases(root,bin,env)
        report={'tested_sha':self.sha,'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Independent local shell/API/Git failure injection only; no SSH session, real Docker, shared host or Matbakhy contacted.','results':self.results,'counts':{s:sum(r['status']==s for r in self.results) for s in ['PASS','FAIL','BLOCKED','NOT_RUN']}}
        (self.out/'independent-cd-results.json').write_text(json.dumps(report,indent=2)+'\n');print(report['counts']);return int(report['counts']['FAIL']>0)
    def proxy_cases(self,root,bin,env):
        executable(bin/'docker',PROXY_DOCKER);executable(bin/'curl',FAKE_CURL)
        for id,original,extra in [('PROXY-PERSIST-AFTER-RELOAD-FAIL','app.{$PREVIEW_DOMAIN} {\n respond "QA app"\n}\n',{'QA_FAIL_PERSIST':'1'}),('PROXY-NOMINAL-CONTROL','app.qa.invalid {\n respond \"QA app\"\n}\napi.qa.invalid {\n respond \"QA api\"\n}\n',{}),('PROXY-UNBALANCED-MARKER','app.qa.invalid {\n respond "QA app"\n}\n# BEGIN lawdesk-staging\napi.qa.invalid {\n respond "QA api"\n}\n',{})]:
            proxy=root/id;proxy.mkdir();(proxy/'live').write_text(original);(proxy/'active').write_text(original)
            r=subprocess.run(['bash',str(self.candidate/'deploy/proxy/attach.sh')],env={**env,'QA_PROXY_ROOT':str(proxy),'LAWDESK_PROXY_BACKUP_DIR':str(proxy/'backups'),'QA_REAL_CADDY':os.environ['LAWDESK_QA_CADDY'],**extra},capture_output=True,text=True,timeout=15)
            active=(proxy/'active').read_text();persisted=(proxy/'live').read_text();trace=(proxy/'trace').read_text()
            if id=='PROXY-NOMINAL-CONTROL':
                ok=r.returncode==0 and 'app.qa.invalid' in active and 'api.qa.invalid' in active and 'lawdesk.qa.invalid' in active and active==persisted;expected='Valid host baseline attaches only LawDesk and keeps active/persisted configuration equal.'
            elif id.endswith('FAIL'):ok=r.returncode!=0 and active==original and persisted==original;expected='Persistence failure after reload restores both active and on-disk original configuration.'
            else:ok=r.returncode!=0 and active==original and persisted==original;expected='Unbalanced LawDesk markers fail closed before dropping any existing host definitions.'
            self.record(id,expected,{'exit':r.returncode,'active_changed':active!=original,'persisted_changed':persisted!=original,'original':original,'active':active,'stderr':r.stderr,'docker_calls':trace,'host_probe_calls':(proxy/'curl-trace').read_text() if (proxy/'curl-trace').exists() else ''},ok)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--candidate',required=True);p.add_argument('--sha',required=True);p.add_argument('--output',required=True);a=p.parse_args();raise SystemExit(Review(a.candidate,a.sha,a.output).execute())
