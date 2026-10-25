"""Resolve retained acceptance gaps using owned disposable PostgreSQL and a real browser."""
import argparse, json, os, subprocess, time, urllib.request, concurrent.futures
from pathlib import Path
from phase56_review import PhaseReview
from phase234_review import Client, INITIAL, CHANGED, port

class Edges(PhaseReview):
    def execute(self):
        result=super().execute()
        browser=json.loads((self.output/'browser/results.json').read_text())
        if browser['counts']['FAIL']:return 1
        if browser['counts']['BLOCKED'] or browser['counts']['NOT_RUN']:return 2
        return result

    def parallel(self, calls):
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(calls)) as pool:
            return list(pool.map(lambda call:call(),calls))

    def notes(self):
        c=self.case('QA high cardinality')
        self.sql("insert into court(code,name_ar,name_en,level,created_by,updated_by) select 'QA-EDGE-'||lpad(n::text,3,'0'),'محكمة اختبار '||n,'QA Court '||n,'FIRST_INSTANCE',u.id,u.id from generate_series(1,101) n,app_user u where u.username='qaadmin'")
        first=self.admin.call('GET','/api/v1/courts?active=true&level=FIRST_INSTANCE&size=100&sort=code,asc')[1]
        second=self.admin.call('GET','/api/v1/courts?active=true&level=FIRST_INSTANCE&size=100&page=1&sort=code,asc')[1]
        target=next(x for x in second['items'] if x['code']=='QA-EDGE-101')
        self.check('COURTS-SECOND-PAGE-API','LD-018,LD-019','Seed 101 synthetic active courts plus fixture court → read both API pages','102 total; target QA-EDGE-101 reachable on page1',{'total':first['totalItems'],'page0':len(first['items']),'page1':len(second['items']),'target_id':target['id']},first['totalItems']==102 and len(first['items'])==100 and len(second['items'])==2)
        self.sql(f"insert into proceeding(case_id,stage,court_id,court_case_number,court_case_year,filing_date,created_by,updated_by) select '{c['id']}','FIRST_INSTANCE','{self.court['id']}','QA-P-'||lpad(n::text,3,'0'),2026,date '2026-01-01'+n,u.id,u.id from generate_series(1,101) n,app_user u where u.username='qaadmin'")
        for resource in ['proceedings']:
            page=self.admin.call('GET','/api/v1/cases/'+c['id']+'/'+resource+'?page=1&size=100')[1]
            self.check('JUDICIAL-SECOND-PAGE-API','LD-019,LD-024,LD-025','Seed 101 chronological synthetic proceedings → read page1','101 total; remaining proceeding reachable via API',{'total':page['totalItems'],'page1_count':len(page['items'])},page['totalItems']==101 and len(page['items'])==1)
        frontend=Path(self.args.candidate)/'frontend';uiport=port()
        env=os.environ.copy();env.update(LAWDESK_API_ORIGIN=f'http://127.0.0.1:{self.appport}',LAWDESK_LOCAL_QA_SECRET=CHANGED,LAWDESK_QA_CHROMIUM='/home/aiagent/.cache/ms-playwright/chromium_headless_shell-1248/chrome-headless-shell-linux64/chrome-headless-shell',LD_LIBRARY_PATH='/tmp/lawdesk-qa-browser-libs/usr/lib/x86_64-linux-gnu')
        with (self.output/'vite.log').open('w') as log:
            proc=subprocess.Popen(['node',str(frontend/'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port',str(uiport),'--strictPort'],cwd=frontend,env=env,stdout=log,stderr=subprocess.STDOUT)
            try:
                for _ in range(90):
                    try:urllib.request.urlopen(f'http://127.0.0.1:{uiport}');break
                    except OSError:time.sleep(.3)
                subprocess.run(['node',str(Path(__file__).parent/'browser/judicial-cardinality.mjs'),f'http://127.0.0.1:{uiport}',self.args.sha,str(self.output/'browser'),c['id'],target['id']],env=env,check=False)
            finally:proc.terminate();proc.wait(timeout=15)
        # Stable independent operator, not one of the two ADMIN targets. Local synthetic users only.
        status,role,_=self.admin.call('POST','/api/v1/roles',{'code':'QA_EDGE_OPERATOR','name':'QA operator','permissions':['USER_VIEW','USER_MANAGE','ROLE_VIEW','ROLE_MANAGE']});assert status==201
        def user(name,roles):
            s,u,_=self.admin.call('POST','/api/v1/users',{'username':name,'email':name+'@example.invalid','displayName':name,'locale':'en','roles':roles,'temporaryPassword':INITIAL});assert s==201
            return u
        operator=user('qa_edge_operator',['QA_EDGE_OPERATOR']);other=user('qa_edge_admin',['ADMIN'])
        primary=self.admin.call('GET','/api/v1/me')[1]
        login=Client(self.appport);assert login.login(operator['username'])[0]==200
        assert login.call('POST','/api/v1/me/password',{'currentPassword':INITIAL,'newPassword':CHANGED})[0]==204
        for mode in ['DISABLE','ROLES','MIXED']:
            for uid in [primary['id'],other['id']]:
                self.sql(f"update app_user set enabled=true where id='{uid}';insert into user_role select '{uid}',id from app_role where code='ADMIN' on conflict do nothing")
            clients=[Client(self.appport),Client(self.appport)]
            for cl in clients:assert cl.login(operator['username'],CHANGED)[0]==200
            def change(cl,uid,remove):return cl.call('PUT','/api/v1/users/'+uid+('/roles'if remove else '/enabled'),{'roles':['LEGAL_STAFF']}if remove else {'enabled':False})
            before=int(self.sql("select count(*) from audit_event where action in ('USER_DISABLE','USER_ROLES_CHANGE') and outcome='SUCCESS'").stdout)
            replies=self.parallel([lambda:change(clients[0],primary['id'],mode=='ROLES'),lambda:change(clients[1],other['id'],mode!='DISABLE')])
            after=int(self.sql("select count(*) from audit_event where action in ('USER_DISABLE','USER_ROLES_CHANGE') and outcome='SUCCESS'").stdout)
            remaining=int(self.sql("select count(*) from app_user u join user_role ur on ur.user_id=u.id join app_role r on r.id=ur.role_id where u.enabled and r.code='ADMIN'").stdout)
            statuses=sorted(r[0] for r in replies);denied=[r[1].get('rule')for r in replies if r[0]==422]
            self.check('LAST-ADMIN-CONCURRENT-'+mode,'LD-009,LD-011','Two operator sessions concurrently remove/disable distinct last two ADMIN holders → query committed rows and audit','200/422 LAST_ADMIN; one enabled ADMIN; one committed success audit',{'statuses':statuses,'denied_rules':denied,'enabled_admins':remaining,'success_delta':after-before},statuses==[200,422] and denied==['LAST_ADMIN'] and remaining==1 and after-before==1)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(Edges(p.parse_args()).execute())
