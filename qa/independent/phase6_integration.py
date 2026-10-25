"""Independent Phase6 operations reports with exact synthetic SQL reconciliation.
No production/UAT access. Reuses QA's existing disposable PostgreSQL/JAR lifecycle.
"""
import argparse,csv,datetime as dt,io,json,subprocess,os,time,urllib.request
from pathlib import Path
import openpyxl,fitz
from phase56_operations import OperationReview
from phase234_review import Client,CHANGED,port

class IntegrationReview(OperationReview):
    def oracle(self,id,steps,expected,actual,ok):self.check(id,'LD-030,LD-032,LD-033,LD-034',steps,expected,actual,ok)
    def notes(self):
        c=self.case('=1+1');p=self.proceeding(c);self.browser_case=c;self.browser_proceeding=p
        def post(path,body,expected=201):
            s,b,_=self.admin.call('POST',path,body);assert s==expected,(path,s,b);return b
        hp='/api/v1/proceedings/'+p['id']+'/hearings'
        for date,status in [('2026-01-03','SCHEDULED'),('2026-01-04','HELD'),('2026-01-05','CANCELLED'),(self.today.isoformat(),'SCHEDULED'),((self.today+dt.timedelta(days=6)).isoformat(),'SCHEDULED'),((self.today+dt.timedelta(days=7)).isoformat(),'SCHEDULED')]:
            h=post(hp,{'hearingDate':date,'purpose':'PLEADING'})
            if status!='SCHEDULED':post('/api/v1/hearings/'+h['id']+'/result',{'status':status,'version':0},200)
        sid=self.sql("select id from app_user where username='phase_staff'").stdout
        for n,status in enumerate(['OPEN','IN_PROGRESS','DONE','CANCELLED']):
            t=post('/api/v1/tasks',{'title':'QA report task '+str(n),'caseId':c['id'],'assigneeId':sid,'dueDate':self.today.isoformat()})
            if status=='DONE':post('/api/v1/tasks/'+t['id']+'/status',{'status':'IN_PROGRESS','version':0},200)
            if status!='OPEN':post('/api/v1/tasks/'+t['id']+'/status',{'status':status,'version':1 if status=='DONE'else 0},200)
            if n==0:self.sql(f"update task set due_date='{self.today-dt.timedelta(days=1)}' where id='{t['id']}'")
        e1=post('/api/v1/experts',{'name':'=2+2','specialty':'QA محاسبة'});e2=post('/api/v1/experts',{'name':'QA inactive','specialty':'QA'})
        for n,status in enumerate(['OPEN','REPORT_SUBMITTED','CANCELLED']):
            r=post('/api/v1/proceedings/'+p['id']+'/expert-referrals',{'expertId':e1['id']if n<2 else e2['id'],'referralDate':'2026-01-03','reportDueDate':'2026-01-07'})
            if status=='REPORT_SUBMITTED':post('/api/v1/expert-referrals/'+r['id']+'/report',{'reportSubmittedDate':'2026-01-06','version':0},200)
            if status=='CANCELLED':post('/api/v1/expert-referrals/'+r['id']+'/cancel',{'version':0},200)
        self.sql(f"update expert set active=false where id='{e2['id']}'")
        def get(name,query='',client=None):
            s,b,_=(client or self.admin).call('GET','/api/v1/reports/'+name+query);assert s==200,(name,s,b);return b
        period='?from=2026-01-01&to='+(self.today+dt.timedelta(days=7)).isoformat()
        paths={'hearings':period,'workload':'','experts':''}
        for name,q in paths.items():
            for label,client,status in [('ANON',Client(self.appport),401),('NONE',self.none,403),('STAFF',self.staff,403),('MANAGER',self.manager,200)]:
                actual=client.call('GET','/api/v1/reports/'+name+q)[0]
                self.oracle('OPERATIONS-AUTH-'+name+'-'+label,'GET report as '+label,str(status),actual,actual==status)
        d=get('dashboard');expected={'hearingsThisWeek':2,'hearingsAwaitingResult':1,'expertReportsOverdue':1,'overdueTasks':1,'tasksDueSoon':1}
        self.oracle('DASHBOARD-OPERATIONS-EXACT','Construct 6 hearings/4 tasks/3 referrals -> read dashboard',expected,d['operations'],d['operations']==expected and d['pending']==[])
        database=self.sql(f"select (select count(*) from hearing where status='SCHEDULED' and hearing_date between '{self.today}' and '{self.today+dt.timedelta(days=6)}'),(select count(*) from hearing where status='SCHEDULED' and hearing_date<'{self.today}'),(select count(*) from expert_referral where status='OPEN' and report_due_date<'{self.today}'),(select count(*) from task where status in ('OPEN','IN_PROGRESS') and due_date<'{self.today}')").stdout
        self.oracle('DASHBOARD-INDEPENDENT-SQL','Direct aggregate SQL over known fixture records','2,1,1,1',database,database=='2\t1\t1\t1')
        hearing=get('hearings',period+'&size=2');summary=hearing['summary']
        self.oracle('HEARINGS-SUMMARY','Read page size2 over six records','page2items total6 summary6 scheduled4 held1 cancelled1 awaiting1',hearing,len(hearing['items'])==2 and hearing['totalItems']==6 and summary['total']==6 and summary['byStatus']['SCHEDULED']==4 and summary['byStatus']['HELD']==1 and summary['byStatus']['CANCELLED']==1 and summary['awaitingResult']==1)
        rows=[]
        for n in range(3):rows+=get('hearings',period+f'&page={n}&size=2')['items']
        self.oracle('HEARINGS-PAGING-LINKAGE','Read all3pages; inspect order/enrichment','6 unique sorted rows; same synthetic case and court; private notes absent',rows,len({r['id']for r in rows})==6 and [r['hearingDate']for r in rows]==sorted(r['hearingDate']for r in rows) and all(r['caseId']==c['id']and r['court']['id']==self.court['id']and 'notes'not in r for r in rows))
        filtered=get('hearings',period+'&status=HELD&status=CANCELLED')
        self.oracle('HEARINGS-STATUS-FILTER','Select held+cancelled','2 rows, awaiting0',filtered,filtered['totalItems']==2 and filtered['summary']['awaitingResult']==0)
        for q in ['?page=-1','?size=0','?sort=hearingDate,desc','?status=BAD','?from=bad']:
            status=self.admin.call('GET','/api/v1/reports/hearings'+q)[0];self.oracle('HEARINGS-VALIDATION-'+q,'GET '+q,'400',status,status==400)
        workload=get('workload');w=workload['items'];summary=workload['summary']
        self.oracle('WORKLOAD-EXACT','Read workload against4known tasks','One staff row OPEN1 IN_PROGRESS1 overdue1; status1 each; dueSoon1',workload,len(w)==1 and w[0]['assigneeId']==sid and w[0]['open']==1 and w[0]['inProgress']==1 and w[0]['overdue']==1 and all(summary['byStatus'][s]==1 for s in ['OPEN','IN_PROGRESS','DONE','CANCELLED'])and summary['dueSoon']==1)
        experts=get('experts');ex={x['expertId']:x for x in experts['items']}
        self.oracle('EXPERTS-EXACT-INACTIVE','Read registry workload including inactive expert','2 rows; first open1 overdue1; inactive0; total overdue1',experts,len(ex)==2 and ex[e1['id']]['open']==1 and ex[e1['id']]['overdue']==1 and not ex[e2['id']]['active']and ex[e2['id']]['open']==0 and experts['summary']['overdueReports']==1)
        self.sql("insert into app_role(code,name) values('QA_REPORT_ONLY','QA reports');insert into role_permission select id,'REPORT_VIEW' from app_role where code='QA_REPORT_ONLY';insert into user_role select u.id,r.id from app_user u,app_role r where u.username='phase_none' and r.code='QA_REPORT_ONLY'")
        before=int(self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout);artifacts=self.output/'artifacts';artifacts.mkdir()
        for name,q in paths.items():
            separator='&'if q else '?'
            denied=self.none.call('GET','/api/v1/reports/'+name+'/export'+q+separator+'format=csv')[0]
            self.oracle('EXPORT-REPORT-VIEW-ONLY-'+name,'REPORT_VIEW without REPORT_EXPORT requests CSV','403',denied,denied==403)
            for fmt in ['csv','xlsx','pdf']:
                for lang in ['en','ar']:
                    path='/api/v1/reports/'+name+'/export'+q+separator+'format='+fmt+'&lang='+lang
                    status,data,headers=self.raw(self.admin,'GET',path);file=artifacts/(name+'-'+lang+'.'+fmt)
                    if status==200:file.write_bytes(data)
                    expected_rows={'hearings':6,'workload':1,'experts':2}[name];actual={'status':status,'bytes':len(data)};ok=status==200
                    if status==200 and fmt=='csv':
                        parsed=list(csv.reader(io.StringIO(data.decode('utf-8-sig'))));actual['rows']=len(parsed)-1;actual['formula_safe']=not any(cell.startswith(('=','+','@'))for row in parsed[1:]for cell in row);ok=ok and actual['rows']==expected_rows and actual['formula_safe']
                    if status==200 and fmt=='xlsx':
                        sheet=openpyxl.load_workbook(io.BytesIO(data)).active;actual.update(rows=sheet.max_row-1,rtl=bool(sheet.sheet_view.rightToLeft),formulas=sum(c.data_type=='f'for row in sheet for c in row));ok=ok and actual['rows']==expected_rows and actual['rtl']==(lang=='ar')and actual['formulas']==0
                    if status==200 and fmt=='pdf':
                        pdf=fitz.open(stream=data,filetype='pdf');actual['pages']=len(pdf);ok=ok and len(pdf)>0 and b'/JavaScript'not in data;pdf[0].get_pixmap().save(str(artifacts/(name+'-'+lang+'.png')));pdf.close()
                    self.oracle('OPERATIONS-EXPORT-'+name+'-'+fmt+'-'+lang,'Export known fixtures; independently parse artifact','200; correct record count for CSV/XLSX; localized workbook; safe formulas; parseable PDF',actual,ok)
        after=int(self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout)
        self.oracle('OPERATIONS-EXPORT-AUDIT','Count successful exports vs denied requests','18 new export audit rows',after-before,after-before==18)
        # Phase 6 browser checks run against these same database fixtures.
        if self.args.ui:self.ui(period)

    def ui(self,period):
        frontend=Path(self.args.candidate)/'frontend';uiport=port();env=os.environ.copy();env['LAWDESK_API_ORIGIN']=f'http://127.0.0.1:{self.appport}'
        with (self.output/'vite.log').open('w')as log:
            proc=subprocess.Popen(['node',str(frontend/'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port',str(uiport),'--strictPort'],cwd=frontend,env=env,stdout=log,stderr=subprocess.STDOUT)
            try:
                for _ in range(60):
                    try:urllib.request.urlopen(f'http://127.0.0.1:{uiport}');break
                    except OSError:time.sleep(.3)
                env.update(LAWDESK_LOCAL_QA_SECRET=CHANGED,LAWDESK_QA_CHROMIUM='/home/aiagent/.cache/ms-playwright/chromium_headless_shell-1248/chrome-headless-shell-linux64/chrome-headless-shell',LD_LIBRARY_PATH='/tmp/lawdesk-qa-browser-libs/usr/lib/x86_64-linux-gnu')
                subprocess.run(['node',str(Path(__file__).parent/'browser/phase6-integration.mjs'),f'http://127.0.0.1:{uiport}',self.args.sha,str(self.output/'browser'),period],env=env,check=False)
            finally:proc.terminate();proc.wait(timeout=15)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.add_argument('--ui',action='store_true');p.set_defaults(kind='notes',browser=False)
    raise SystemExit(IntegrationReview(p.parse_args()).execute())
