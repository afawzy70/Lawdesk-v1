"""New independent deployment failure probes, never connect to Docker.
Fake transport executes the REAL inner smoke shell and records exact commands.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import subprocess
import tempfile

FAKE_DOCKER = '''#!/usr/bin/python3
import os,subprocess,sys,json
args=sys.argv[1:]
with open(os.environ['QA_DOCKER_CALLS'],'a') as f: f.write(json.dumps({'args':args,'tag':os.environ.get('LAWDESK_TAG')})+'\\n')
if 'exec' in args:
    at=args.index('exec'); tail=args[at+1:]
    if '-euc' in tail:
        command=tail[-1]
        sys.exit(subprocess.run(['sh','-euc',command],env=os.environ).returncode)
if 'up' in args and os.environ.get('LAWDESK_TAG')==os.environ.get('QA_FAIL_TAG'):sys.exit(1)
sys.exit(0)
'''
FAKE_WGET = '''#!/usr/bin/python3
import os,sys
url=sys.argv[-1]
if url.endswith('/actuator/health/readiness'):
    print('{"status":"'+os.environ.get('QA_READINESS','UP')+'"}');sys.exit(0)
if url.endswith('/api/v1/cases'):
    status=int(os.environ.get('QA_API_STATUS','401'))
    print('HTTP/1.1 '+str(status)+' QA',file=sys.stderr);sys.exit(0 if status==200 else 8)
if url.endswith('/actuator/env'):
    print('HTTP/1.1 404 QA',file=sys.stderr);sys.exit(8)
sys.exit(0)
'''

def run(candidate,sha,output):
    candidate=Path(candidate);output=Path(output);output.mkdir(parents=True,exist_ok=True)
    scripts=candidate/'deploy/scripts'
    for name in ['smoke.sh','deploy.sh','rollback.sh']:
        actual=(scripts/name).read_bytes()
        expected=subprocess.check_output(['git','show',sha+':deploy/scripts/'+name])
        if actual!=expected:raise ValueError('Candidate script differs from tested SHA')
    rows=[]
    with tempfile.TemporaryDirectory(prefix='lawdesk-qa-script-') as temp:
        root=Path(temp);bin=root/'bin';bin.mkdir();state=root/'state';state.mkdir();calls=root/'docker-calls.jsonl';calls.touch()
        for name,code in [('docker',FAKE_DOCKER),('wget',FAKE_WGET)]:
            p=bin/name;p.write_text(code);p.chmod(0o755)
        env={'PATH':str(bin)+':/usr/bin:/bin','HOME':str(root),'LAWDESK_REGISTRY':'lawdesk-qa','LAWDESK_TAG':sha,'LAWDESK_STATE_DIR':str(state),'QA_DOCKER_CALLS':str(calls)}
        for id,extra,expected in [('SMOKE-CONTROL',{},0),('SMOKE-READINESS-DOWN',{'QA_READINESS':'DOWN'},'nonzero'),('SMOKE-UNAUTHORIZED-API-200',{'QA_API_STATUS':'200'},'nonzero')]:
            r=subprocess.run(['bash',str(scripts/'smoke.sh')],env={**env,**extra},capture_output=True,text=True,timeout=15)
            rows.append(dict(id=id,baseline_case_ids=['QA-0046','QA-0048'],preconditions=['Docker and wget are QA transport doubles; real candidate smoke shell executes unchanged.'],test_data=extra,steps=['Execute deploy/scripts/smoke.sh with controlled response fixture.'],expected_exit=expected,actual_exit=r.returncode,stdout=r.stdout,stderr=r.stderr,status='PASS' if (r.returncode!=0 if expected=='nonzero' else r.returncode==expected) else 'FAIL'))
        current='b'*40;previous='a'*40
        (state/'current-release').write_text(current+'\n');(state/'previous-release').write_text(previous+'\n');calls.write_text('')
        r=subprocess.run(['bash',str(scripts/'deploy.sh'),current],env={**env,'QA_FAIL_TAG':current},capture_output=True,text=True,timeout=15)
        trace=[json.loads(line) for line in calls.read_text().splitlines()]
        rolled_back=any(row['tag']==previous and 'up' in row['args'] for row in trace)
        rows.append(dict(id='REDEPLOY-CURRENT-FAILS',baseline_case_ids=['QA-0046'],preconditions=['QA-only state directory; real script; Docker calls recorded, never executed.'],test_data={'current':current,'previous':previous,'requested':current,'failed_up':current},steps=['Seed current B and previous A.','Redeploy B and force its up --wait to fail.','Inspect whether previous A is started.'],expected='Exit1 after restoring healthy previous A; no lost release state.',actual={'exit':r.returncode,'current':(state/'current-release').read_text().strip(),'rolled_back':rolled_back,'stderr':r.stderr,'docker_calls':trace},status='PASS' if r.returncode==1 and rolled_back else 'FAIL'))
    report={'tested_sha':sha,'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Independent failure injection against unchanged real shell scripts; NOT real-container or deployed rollback verification.','results':rows,'counts':{s:sum(r['status']==s for r in rows) for s in ['PASS','FAIL','BLOCKED']}}
    (output/'independent-deployment-edges.json').write_text(json.dumps(report,indent=2)+'\n');print(report['counts']);return int(report['counts']['FAIL']>0)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--candidate',required=True);p.add_argument('--sha',required=True);p.add_argument('--output',required=True);a=p.parse_args();raise SystemExit(run(a.candidate,a.sha,a.output))
