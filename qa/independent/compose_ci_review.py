"""Independent native Compose rendering and CI configuration probes; no daemon changes."""
import argparse,json,os,re,subprocess,datetime
from pathlib import Path
import yaml

def run(candidate,sha,output):
    root=Path(candidate);out=Path(output);out.mkdir(parents=True,exist_ok=True);rows=[]
    def check(id,expected,actual,ok):rows.append(dict(id=id,expected=expected,actual=actual,status='PASS' if ok else 'FAIL'))
    env={**os.environ,'LAWDESK_REGISTRY':'lawdesk-qa','LAWDESK_TAG':sha}
    cmd=['docker','compose','-p','lawdesk-qa-validation','-f',str(root/'deploy/compose.yaml'),'config','--format','json']
    rendered=subprocess.run(cmd,env=env,capture_output=True,text=True)
    check('COMPOSE-NATIVE-RENDER','Native CLI renders the exact candidate without daemon mutations.',{'exit':rendered.returncode,'stderr':rendered.stderr},rendered.returncode==0)
    if rendered.returncode==0:
        d=json.loads(rendered.stdout);services=d['services']
        check('COMPOSE-NO-HOST-PORTS','No service publishes a host port.',{k:v.get('ports',[]) for k,v in services.items()},all(not v.get('ports') for v in services.values()))
        net={k:list(services[k]['networks']) for k in services}
        check('COMPOSE-DB-API-ISOLATION','DB/backend use only internal network; only web has edge.',net,net['db']==['internal'] and net['backend']==['internal'] and 'edge' in net['web'] and d['networks']['internal']['internal'])
        check('COMPOSE-READONLY-APP','Backend and web read-only with writable tmpfs only.',{k:{'read_only':services[k].get('read_only'),'tmpfs':services[k].get('tmpfs')} for k in ['backend','web']},all(services[k].get('read_only') and '/tmp' in services[k].get('tmpfs',[]) for k in ['backend','web']))
        security={k:{'drop':v.get('cap_drop'),'add':v.get('cap_add',[]),'options':v.get('security_opt')} for k,v in services.items()}
        check('COMPOSE-HARDENING','All drop ALL and prohibit privilege gain; DB has explicit documented bootstrap capability exceptions.',security,all('ALL' in v.get('cap_drop',[]) and any('no-new-privileges' in x for x in v.get('security_opt',[])) for v in services.values()) and not services['backend'].get('cap_add') and not services['web'].get('cap_add'))
        images={k:v['image'] for k,v in services.items()}
        check('COMPOSE-IMMUTABLE-IMAGE-SELECTORS','DB digest-pinned; app selectors end with exact SHA.',images,'@sha256:' in images['db'] and images['backend'].endswith(':'+sha) and images['web'].endswith(':'+sha))
        bounds={k:{'memory':v.get('mem_limit'),'logging':v.get('logging'),'health':v.get('healthcheck')} for k,v in services.items()}
        check('COMPOSE-RESOURCE-LOG-HEALTH-BOUNDS','All have memory limits, bounded logs and health checks.',bounds,all(v.get('mem_limit') and v.get('healthcheck') and v.get('logging',{}).get('options',{}).get('max-size') and v.get('logging',{}).get('options',{}).get('max-file') for v in services.values()))
        (out/'compose-render.json').write_text(rendered.stdout)
    env.pop('LAWDESK_TAG');missing=subprocess.run(cmd,env=env,capture_output=True,text=True)
    check('COMPOSE-MISSING-RELEASE','Missing release selector rejects before runtime.',{'exit':missing.returncode,'stderr':missing.stderr},missing.returncode!=0 and 'LAWDESK_TAG' in missing.stderr)
    workflow=yaml.safe_load((root/'.github/workflows/ci.yml').read_text());jobs=workflow['jobs'];events=workflow.get('on',workflow.get(True,{}))
    check('CI-PR-TRIGGERS','PR jobs target develop/main with read-only token and no privileged PR event.',{'events':events,'permissions':workflow.get('permissions')},set(events['pull_request']['branches'])=={'develop','main'} and 'pull_request_target' not in events and workflow.get('permissions')=={'contents':'read'})
    actions=[s['uses'] for j in jobs.values() for s in j.get('steps',[]) if 'uses' in s]
    check('CI-ACTION-PINS','Every action uses a full immutable SHA.',actions,all(re.fullmatch(r'[^@]+@[0-9a-f]{40}',a) for a in actions))
    timeouts={}
    for name,job in jobs.items():
        if str(job.get('uses','')).startswith('./.github/workflows/'):
            callee=yaml.safe_load((root/job['uses'][2:]).read_text())
            timeouts[name]={k:v.get('timeout-minutes') for k,v in callee['jobs'].items()}
        else: timeouts[name]=job.get('timeout-minutes')
    flat=[v for value in timeouts.values() for v in (value.values() if isinstance(value,dict) else [value])]
    cancellation=workflow.get('concurrency',{}).get('cancel-in-progress')
    valid_cancel=cancellation is True or cancellation=="${{ github.event_name == 'pull_request' }}"
    check('CI-BOUNDED-CANCELLED-RUNS','Jobs including reusable-workflow callees bounded<=30min; PR supersession cancels, deployments may queue without cancellation.',{'timeouts':timeouts,'concurrency':workflow.get('concurrency')},all(isinstance(t,int) and 0<t<=30 for t in flat) and valid_cancel)
    check('CI-SIX-GATE-CONFIG','All six expected gates configured; this does not certify their execution or server enforcement.',list(jobs),{'policy','backend','backend-postgres-image','frontend','stack','secrets'}.issubset(jobs))
    report={'tested_sha':sha,'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Independent native render and static CI checks. No Docker daemon/container, role or deployment PASS is implied.','results':rows,'counts':{s:sum(r['status']==s for r in rows) for s in ['PASS','FAIL','BLOCKED']},'runtime_blockers':['Docker local socket permission denied, including escalated read-only retry.','No changes to socket permissions, daemon, shared proxy or other applications.']}
    (out/'independent-compose-ci-results.json').write_text(json.dumps(report,indent=2)+'\n');print(report['counts']);return int(report['counts']['FAIL']>0)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--candidate',required=True);p.add_argument('--sha',required=True);p.add_argument('--output',required=True);a=p.parse_args();raise SystemExit(run(a.candidate,a.sha,a.output))
