"""PR58 changed-list regression: valid synthetic records in disposable PostgreSQL only."""
import argparse,os,subprocess,time,urllib.request,json
from pathlib import Path
from phase56_review import PhaseReview
from phase234_review import CHANGED,port
class LongLists(PhaseReview):
 def notes(self):
  c=self.case('QA P7 long lists');p=self.proceeding(c);uid=self.admin.call('GET','/api/v1/me')[1]['id'];cid=c['id'];pid=p['id']
  self.sql(f"insert into expert(name,specialty,created_by,updated_by)select 'QA-X-'||lpad(n::text,3,'0'),'QA specialty','{uid}','{uid}'from generate_series(1,101)n")
  self.sql(f"insert into case_note(case_id,body,created_by,updated_by,created_at)select '{cid}','QA-N-'||lpad(n::text,3,'0'),'{uid}','{uid}',now()+n*interval '1 second'from generate_series(1,101)n")
  self.sql(f"insert into task(case_id,title,assignee_id,created_by,updated_by)select '{cid}','QA-T-'||lpad(n::text,3,'0'),'{uid}','{uid}','{uid}'from generate_series(1,101)n")
  self.sql(f"insert into hearing(case_id,proceeding_id,hearing_date,purpose,notes,created_by,updated_by)select '{cid}','{pid}',date '2026-01-02'+n,'OTHER','QA-H-'||lpad(n::text,3,'0'),'{uid}','{uid}'from generate_series(1,101)n")
  self.sql(f"insert into expert_referral(case_id,proceeding_id,expert_id,referral_date,mission,created_by,updated_by)select '{cid}','{pid}',id,date '2026-01-03','QA-R-'||right(name,3),'{uid}','{uid}'from expert where name like 'QA-X-%'")
  frontend=Path(self.args.candidate)/'frontend';uiport=port();env=os.environ.copy();env.update(LAWDESK_API_ORIGIN=f'http://127.0.0.1:{self.appport}',LAWDESK_LOCAL_QA_SECRET=CHANGED,LAWDESK_QA_CHROMIUM='/home/aiagent/.cache/ms-playwright/chromium_headless_shell-1248/chrome-headless-shell-linux64/chrome-headless-shell',LD_LIBRARY_PATH='/tmp/lawdesk-qa-browser-libs/usr/lib/x86_64-linux-gnu')
  with (self.output/'vite.log').open('w')as log:
   proc=subprocess.Popen(['node',str(frontend/'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port',str(uiport),'--strictPort'],cwd=frontend,env=env,stdout=log,stderr=subprocess.STDOUT)
   try:
    for _ in range(90):
     try:urllib.request.urlopen(f'http://127.0.0.1:{uiport}');break
     except OSError:time.sleep(.3)
    subprocess.run(['node',str(Path(__file__).parent/'browser/phase7-longlists.mjs'),f'http://127.0.0.1:{uiport}',self.args.sha,str(self.output/'browser'),cid],env=env,check=False)
   finally:proc.terminate();proc.wait(timeout=15)
if __name__=='__main__':
 p=argparse.ArgumentParser()
 for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
 p.set_defaults(kind='notes',browser=False);a=p.parse_args();result=LongLists(a).execute();d=json.loads((Path(a.output)/'browser/results.json').read_text());raise SystemExit(1 if d['counts']['FAIL']else result)
