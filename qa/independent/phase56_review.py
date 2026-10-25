"""Independent Phase5/6 HTTP+PostgreSQL acceptance. Reuses QA's isolated jar/DB lifecycle.
Never targets UAT or an implementation workspace. No developer test counts are included.
"""
import argparse
import concurrent.futures as futures
import datetime as dt
import json
import os
import subprocess
from pathlib import Path
from urllib.parse import urlencode
from zoneinfo import ZoneInfo
from phase234_review import Review, Client, INITIAL, CHANGED, port


class PhaseReview(Review):
    def __init__(self,args):
        if (Path(args.output)/"results.json").exists():
            raise ValueError("Refusing to overwrite prior evidence; use a fresh output directory")
        super().__init__(args)

    def run(self, admin, appport):
        assert admin.login('qaadmin')[0] == 200
        assert admin.call('POST','/api/v1/me/password',dict(currentPassword=INITIAL,newPassword=CHANGED))[0] == 204
        self.admin, self.appport = admin, appport
        for name,role in [('phase_staff','LEGAL_STAFF'),('phase_manager','LEGAL_MANAGER'),('phase_none',None)]:
            self.sql(f"insert into app_user(username,email,display_name,password_hash,created_by,updated_by) select '{name}','{name}@example.invalid','QA {name}',password_hash,id,id from app_user where username='qaadmin'")
            if role:self.sql(f"insert into user_role select u.id,r.id from app_user u,app_role r where u.username='{name}' and r.code='{role}'")
        self.staff,self.manager,self.none=[Client(appport) for _ in range(3)]
        for client,name in [(self.staff,'phase_staff'),(self.manager,'phase_manager'),(self.none,'phase_none')]:assert client.login(name,CHANGED)[0]==200
        self.today=dt.datetime.now(ZoneInfo('Africa/Cairo')).date()
        self.counter=0
        self.court=self.request('FIXTURE-COURT','LD-018',admin,'POST','/api/v1/courts',dict(code='QA-PH56',nameAr='محكمة اختبار',nameEn='QA Court',level='FIRST_INSTANCE'),201)
        if self.args.kind=='hearings':self.hearings()
        elif self.args.kind=='tasks':self.tasks()
        elif self.args.kind=='hearing-chronology':self.hearing_chronology()
        elif self.args.kind in ['documents','experts','notes','formats']:getattr(self,self.args.kind)()
        else:self.reports()
        if self.args.browser:self.browser()

    def case(self,title='QA phase56',status=None):
        self.counter+=1
        c=self.admin.call('POST','/api/v1/cases',dict(referenceNo=f'QA56-{self.counter:03}',title=title,caseType='CIVIL',openedOn='2026-01-01'))
        assert c[0]==201,c[:2]
        value=c[1]
        for target in dict(ACTIVE=['ACTIVE'],SUSPENDED=['ACTIVE','SUSPENDED'],CLOSED=['CLOSED'],ARCHIVED=['CLOSED','ARCHIVED']).get(status,[]):
            s,value,_=self.admin.call('POST','/api/v1/cases/'+value['id']+'/status',dict(status=target,version=value['version'],reason='QA'))
            assert s==200
        return value

    def proceeding(self,c):
        self.counter+=1
        s,p,_=self.admin.call('POST','/api/v1/cases/'+c['id']+'/proceedings',dict(stage='FIRST_INSTANCE',courtId=self.court['id'],courtCaseNumber=f'QA{self.counter}',courtCaseYear=2026,filingDate='2026-01-02'))
        assert s==201,p
        return p

    def req(self,id,method,path,body=None,status=200,client=None,rule=None):
        # Preserve historical assertions on older heads; the integration candidate documents
        # normalization explicitly as an owner-review assumption (not closure of QAD-029).
        contract=(Path(self.args.candidate)/'docs/delivery/PHASE-5-ACCEPTANCE.md').read_text()
        normalized=status==400 and ('falls back to the defaults' in contract) and (id.startswith('CALENDAR-') or id.startswith('TASK-')) and any(q in path for q in ['page=-1','size=-1','page=bad','size=bad'])
        if normalized:status=200
        result=self.request(id,'LD-024' if self.args.kind in ['hearings','hearing-chronology'] else 'LD-027,LD-028' if self.args.kind=='tasks' else 'LD-030,LD-031,LD-032,LD-034',client or self.admin,method,path,body,status,rule)
        if normalized:
            self.oracle(id+'-NORMALIZED','Read normalized paging under proposed current contract','page0,size20; QAD-029 owner decision remains open',{'page':result.get('page'),'size':result.get('size')},result.get('page')==0 and result.get('size')==20)
        return result

    def oracle(self,id,steps,expected,actual,ok):
        self.check(id,'LD-024' if self.args.kind in ['hearings','hearing-chronology'] else 'LD-027,LD-028' if self.args.kind=='tasks' else 'LD-030,LD-031,LD-032,LD-034',steps,expected,actual,ok)

    def hearings(self):
        c=self.case();other=self.case('QA other');p=self.proceeding(c);self.browser_case=c;self.browser_proceeding=p
        path='/api/v1/proceedings/'+p['id']+'/hearings'
        body=dict(hearingDate='2026-01-03',hearingTime='09:30',purpose='PLEADING',notes=' QA private preparation ')
        self.req('HEARING-ANON','GET','/api/v1/hearings',status=401,client=Client(self.appport))
        self.req('HEARING-DENIED-READ','GET','/api/v1/hearings',status=403,client=self.none)
        self.req('HEARING-DENIED-INVALID','POST',path,{},403,self.none)
        # Real dedicated view-only role, independent of seeded staff roles.
        self.sql("insert into app_role(code,name) values('QA_HEARING_VIEW','QA view'); insert into role_permission select id,'HEARING_VIEW' from app_role where code='QA_HEARING_VIEW'; insert into user_role select u.id,r.id from app_user u,app_role r where u.username='phase_none' and r.code='QA_HEARING_VIEW'")
        self.req('HEARING-VIEW-ONLY','GET','/api/v1/hearings',client=self.none)
        self.req('HEARING-VIEW-ONLY-WRITE','POST',path,body,403,self.none)
        s,_,_=self.staff.call('POST',path,body,csrf=False)
        self.oracle('HEARING-CSRF','POST hearing without CSRF as authorized staff','403',s,s==403)
        audit=self.sql("select count(*) from audit_event where action='ACCESS_DENIED' and request_id='qa-phase234'").stdout.strip()
        self.oracle('HEARING-DENIAL-AUDITED','Read correlation-matched denial audit rows','At least one denial',audit,int(audit)>0)
        h=self.req('HEARING-SCHEDULE','POST',path,body,201,self.staff)
        self.oracle('HEARING-CASE-CONSISTENCY','Create hearing through proceeding; inspect case/court and normalized notes','Derived case/proceeding/court; scheduled/version0; notes trimmed',h,h['caseId']==c['id'] and h['proceedingId']==p['id'] and h['courtId']==self.court['id'] and h['status']=='SCHEDULED' and h['version']==0 and h['notes']=='QA private preparation')
        hp='/api/v1/hearings/'+h['id'];self.req('HEARING-DUPLICATE','POST',path,body,409)
        for key,data,status in [('REQUIRED',{},400),('BEFORE-FILING',{**body,'hearingDate':'2026-01-01'},422),('NOTES-2001',{**body,'notes':'x'*2001},400),('TIME',{**body,'hearingTime':'25:10'},400),('FAR-FUTURE',{**body,'hearingDate':self.today.replace(year=self.today.year+2).__add__(dt.timedelta(days=1)).isoformat()},400)]:self.req('HEARING-'+key,'POST',path,data,status)
        self.req('HEARING-UNKNOWN-PROCEEDING','POST','/api/v1/proceedings/00000000-0000-4000-8000-000000000099/hearings',body,404)
        self.req('HEARING-STALE','PUT',hp,{**body,'version':-1},409)
        edited=self.req('HEARING-RESCHEDULE','PUT',hp,{**body,'hearingDate':'2026-01-04','version':0},200)
        self.req('HEARING-CANCEL','POST',hp+'/result',dict(status='CANCELLED',decision='QA cancelled',version=edited['version']),200)
        replacement=self.req('CANCEL-FREES-DAY','POST',path,{**body,'hearingDate':'2026-01-04'},201)
        self.req('HEARING-TERMINAL','POST',hp+'/result',dict(status='HELD',version=2),422)
        replacementPath='/api/v1/hearings/'+replacement['id']
        self.req('HEARING-RESULT-NEXT-FORBIDDEN','POST',replacementPath+'/result',dict(status='HELD',version=0,nextHearingDate='2026-01-06'),400)
        adj=self.req('HEARING-ADJOURN','POST',replacementPath+'/result',dict(status='ADJOURNED',version=0,decision='QA private decision',nextHearingDate='2026-01-06'),200)
        child=self.req('HEARING-NEXT-READ','GET','/api/v1/hearings/'+adj['nextHearingId'])
        self.oracle('ADJOURN-SAME-PROCEEDING','Read adjourned parent and new child','Child same case/proceeding; reciprocal IDs; scheduled; inherited purpose',child,child['caseId']==c['id'] and child['proceedingId']==p['id'] and child['adjournedFromId']==replacement['id'] and child['purpose']==replacement['purpose'] and child['status']=='SCHEDULED')
        self.req('CHILD-CANNOT-MOVE-BACK','PUT','/api/v1/hearings/'+child['id'],{**body,'hearingDate':'2026-01-04','version':0},422)
        self.req('HEARING-HELD','POST','/api/v1/hearings/'+child['id']+'/result',dict(status='HELD',decision='QA held',version=0),200)
        future=self.req('HEARING-FUTURE-SCHEDULE','POST',path,{**body,'hearingDate':(self.today+dt.timedelta(days=1)).isoformat()},201)
        self.req('HEARING-FUTURE-CANNOT-HOLD','POST','/api/v1/hearings/'+future['id']+'/result',dict(status='HELD',version=0),422)
        for query,status in [('from=2026-01-03&to=2026-01-06',200),('from=2026-02-01&to=2026-01-01',400),('from=2026-01-01&to=2027-01-03',400),('from=bad',400),('page=-1',400)]:self.req('CALENDAR-'+query,'GET','/api/v1/hearings?'+query,status=status)
        for query in ['page=bad','size=-1','size=bad']:self.req('CALENDAR-'+query,'GET','/api/v1/hearings?'+query,status=400)
        count=self.sql('select count(*) from hearing').stdout.strip()
        for name,sql in [
            ('MISMATCH-INSERT',f"insert into hearing(case_id,proceeding_id,hearing_date,purpose,created_by,updated_by) select '{other['id']}','{p['id']}','2026-02-01','OTHER',id,id from app_user where username='qaadmin'"),
            ('MISMATCH-UPDATE',f"update hearing set case_id='{other['id']}' where id='{h['id']}'"),
            ('ORPHAN-PROCEEDING',f"update hearing set proceeding_id='00000000-0000-4000-8000-000000000099' where id='{h['id']}'"),
            ('ORPHAN-CONTINUATION',f"update hearing set adjourned_from_id='00000000-0000-4000-8000-000000000099' where id='{h['id']}'"),
            ('DELETE-PROCEEDING',f"delete from proceeding where id='{p['id']}'")]:
            r=self.sql(sql,fail=True);self.oracle('SQL-'+name,sql,'Constraint rejects mutation',{'returncode':r.returncode,'error':r.stderr[:300]},r.returncode!=0)
        self.oracle('SQL-REJECTIONS-ATOMIC','Read hearing count after rejected FK/check mutations','Count unchanged',self.sql('select count(*) from hearing').stdout.strip(),self.sql('select count(*) from hearing').stdout.strip()==count)
        # Fault injection confined to the disposable local DB, never UAT.
        rollback=self.req('ROLLBACK-FIXTURE','POST',path,{**body,'hearingDate':'2026-02-01'},201)
        occupied=self.req('ADJOURN-COLLISION-FIXTURE','POST',path,{**body,'hearingDate':'2026-02-02'},201)
        self.req('ADJOURN-DUPLICATE-DAY','POST','/api/v1/hearings/'+rollback['id']+'/result',dict(status='ADJOURNED',version=0,nextHearingDate='2026-02-02'),409)
        unchanged=self.admin.call('GET','/api/v1/hearings/'+rollback['id'])[1]
        self.oracle('ADJOURN-COLLISION-ATOMIC','Read parent after conflicting successor','SCHEDULED/version0/no next hearing',unchanged,unchanged['status']=='SCHEDULED' and unchanged['version']==0 and unchanged['nextHearingId'] is None)
        self.admin.call('POST','/api/v1/hearings/'+occupied['id']+'/result',dict(status='CANCELLED',version=0))
        before=self.sql(f"select status||':'||version,(select count(*) from hearing),(select count(*) from case_event) from hearing where id='{rollback['id']}'").stdout
        self.sql("create function qa_reject_hearing_event() returns trigger language plpgsql as $$ begin if new.type like 'HEARING_%' then raise exception 'QA injected failure'; end if; return new; end $$; create trigger qa_hearing_event before insert on case_event for each row execute function qa_reject_hearing_event()")
        failed=self.admin.call('POST','/api/v1/hearings/'+rollback['id']+'/result',dict(status='ADJOURNED',version=0,nextHearingDate='2026-02-02'))[0]
        after=self.sql(f"select status||':'||version,(select count(*) from hearing),(select count(*) from case_event) from hearing where id='{rollback['id']}'").stdout
        self.oracle('ADJOURN-EVENT-FAILURE-ROLLBACK','Inject rejecting HEARING event trigger; adjourn; compare parent/child/history','500; no parent result/version or child/history commit',{'status':failed,'before':before,'after':after},failed==500 and before==after)
        self.sql('drop trigger qa_hearing_event on case_event; drop function qa_reject_hearing_event()')
        # Two outcomes and two duplicate schedules from distinct real HTTP sessions.
        def concurrent(method,url,bodies):
            def send(b):
                cl=Client(self.appport);cl.cookies=dict(self.admin.cookies);return cl.call(method,url,b)[0]
            with futures.ThreadPoolExecutor(max_workers=2) as pool:return sorted(pool.map(send,bodies))
        statuses=concurrent('POST','/api/v1/hearings/'+rollback['id']+'/result',[dict(status='HELD',version=0),dict(status='CANCELLED',version=0)])
        self.oracle('RACE-RESULTS','Two simultaneous outcomes at version0','One200 and one409',statuses,statuses==[200,409])
        statuses=concurrent('POST',path,[{**body,'hearingDate':'2026-02-05'}]*2)
        self.oracle('RACE-DUPLICATE-DAY','Two simultaneous schedule requests same proceeding/day','One201 and one409',statuses,statuses==[201,409])
        eventtext=self.sql(f"select string_agg(summary,'|') from case_event where case_id='{c['id']}' and type like 'HEARING_%'").stdout
        self.oracle('TIMELINE-REDACTS-FREE-TEXT','Inspect hearing timeline summaries','No notes or decision text',{'private_text_present':'QA private' in eventtext},'QA private' not in eventtext)
        closed=self.case('QA closed','CLOSED');cp=self.proceeding(closed);self.req('CLOSED-HEARING-ALLOWED','POST','/api/v1/proceedings/'+cp['id']+'/hearings',body,201)
        ac=self.admin.call('POST','/api/v1/cases/'+closed['id']+'/status',dict(status='ARCHIVED',version=closed['version']))[1]
        self.req('ARCHIVED-HEARING-DENIED','POST','/api/v1/proceedings/'+cp['id']+'/hearings',{**body,'hearingDate':'2026-01-05'},422,rule='CASE_ARCHIVED')

    def reports(self):
        base='/api/v1/reports/dashboard'
        self.req('DASHBOARD-ANON','GET',base,status=401,client=Client(self.appport));self.req('DASHBOARD-DENIED','GET',base,status=403,client=self.none)
        self.req('DASHBOARD-DENIED-BAD-FILTER','GET',base+'?from=bad',status=403,client=self.none)
        empty=self.req('DASHBOARD-EMPTY','GET',base+'?from=2026-01-01&to=2026-01-31')
        if 'operations' in empty:
            self.oracle('DASHBOARD-ZEROS','Read empty DB dashboard with integrated operations ports','Every implemented count zero; no unavailable figures',empty,empty['cases']['total']==0 and empty['judgments']['recorded']==0 and empty['pending']==[] and set(empty['operations'])=={'hearingsThisWeek','hearingsAwaitingResult','expertReportsOverdue','overdueTasks','tasksDueSoon'} and all(v==0 for v in empty['operations'].values()))
        else:
            self.oracle('DASHBOARD-ZEROS','Read empty DB dashboard','Zero cases/judgments and explicit unavailable figures',empty,empty['cases']['total']==0 and empty['judgments']['recorded']==0 and set(empty['pending'])=={'HEARINGS_THIS_WEEK','EXPERT_REPORTS_DUE','OVERDUE_TASKS'})
        cases=[self.case('QA report '+s,s) for s in ['OPEN','ACTIVE','SUSPENDED','CLOSED','ARCHIVED']]
        cases.append(self.case('QA literal %_ marker'));p=self.proceeding(cases[0]);self.browser_case=cases[0];self.browser_proceeding=p
        self.admin.call('POST','/api/v1/proceedings/'+p['id']+'/judgment',dict(judgmentDate='2026-01-03',outcome='IN_FAVOUR',final=True))
        p2=self.proceeding(cases[1]);d=self.req('DASHBOARD-REAL','GET',base+'?from=2026-01-03&to=2026-01-03',client=self.manager)
        self.oracle('DASHBOARD-EXACT-COUNTS','Compare dashboard to six fixture cases/two proceedings/one judgment','cases6 open4 closed2; proceedings awaiting1; final judgment1',d,d['cases']['total']==6 and d['cases']['open']==4 and d['cases']['closed']==2 and d['proceedings']['awaitingJudgment']==1 and d['judgments']['recorded']==1 and d['judgments']['final']==1)
        for query,status in [('from=bad',400),('from=2026-02-01&to=2026-01-01',422),('from=2026-01-04&to=2026-01-04',200)]:self.req('DASHBOARD-PERIOD-'+query,'GET',base+'?'+query,status=status)
        if self.args.kind in ['judgments','exports']:self.judgments(p2)
        if self.args.kind not in ['inventory','judgments','exports']:return
        base='/api/v1/reports/cases';self.req('INVENTORY-ANON','GET',base,status=401,client=Client(self.appport));self.req('INVENTORY-DENIED-INVALID','GET',base+'?sort=bad',status=403,client=self.none)
        inv=self.req('INVENTORY-PAGED','GET',base+'?size=2&sort=referenceNo,asc')
        self.oracle('INVENTORY-SUMMARY-ALL-ROWS','Read page0 size2','2 items but total/summary6; open4 closed2',inv,len(inv['items'])==2 and inv['totalItems']==6 and inv['summary']['total']==6 and inv['summary']['open']==4 and inv['summary']['closed']==2)
        ids=[]
        for n in range(3):ids.extend(x['id'] for x in self.admin.call('GET',base+f'?size=2&page={n}&sort=title,asc')[1]['items'])
        self.oracle('INVENTORY-STABLE-PAGING','Read3 pages with tied titles','All6 unique case IDs once',ids,len(set(ids))==6 and set(ids)=={c['id'] for c in cases})
        query=urlencode({'q':'%_'});literal=self.req('INVENTORY-LITERAL-WILDCARDS','GET',base+'?'+query)
        self.oracle('LITERAL-FILTER-CORRECT','Search literal percent and underscore','Only marker case',literal['totalItems'],literal['totalItems']==1 and literal['items'][0]['id']==cases[-1]['id'])
        for key,query,count in [('CLOSED','statusGroup=CLOSED',2),('CONTRADICTION','statusGroup=CLOSED&status=ACTIVE',0),('FIRST-STAGE','stage=FIRST_INSTANCE',2),('UNKNOWN-COURT','courtId=00000000-0000-4000-8000-000000000099',0),('ONE-DAY','openedFrom=2026-01-01&openedTo=2026-01-01',6)]:
            r=self.req('INVENTORY-'+key,'GET',base+'?'+query);self.oracle('FILTER-'+key,'Apply '+query,f'Exactly{count} matches',r['totalItems'],r['totalItems']==count)
        for query,status in [('sort=title;drop,asc',400),('sort=title,sideways',400),('page=-1',400),('size=0',400),('stage=BAD',400),('courtId=bad',400),('openedFrom=2026-02-01&openedTo=2026-01-01',422)]:self.req('INVENTORY-INVALID-'+query,'GET',base+'?'+query,status=status)
        before=self.sql('select (select count(*) from case_event),(select count(*) from legal_case),(select count(*) from proceeding)').stdout
        a=self.admin.call('GET',base)[1];b=self.admin.call('GET',base)[1]
        after=self.sql('select (select count(*) from case_event),(select count(*) from legal_case),(select count(*) from proceeding)').stdout
        self.oracle('INVENTORY-READONLY-REPEAT','Repeated reads and DB counts','Identical response; no changed rows',{'same_response':a==b,'counts_before':before,'counts_after':after},a==b and before==after)

        if self.args.kind=='exports':self.exports()

    def hearing_chronology(self):
        c=self.case();p=self.proceeding(c)
        h=self.req('CHRONOLOGY-HEARING','POST','/api/v1/proceedings/'+p['id']+'/hearings',dict(hearingDate='2026-01-03',purpose='PLEADING'),201)
        def edit(proceeding,day):
            return dict(courtId=self.court['id'],courtCaseNumber=proceeding['courtCaseNumber'],courtCaseYear=2026,filingDate=day,version=proceeding['version'])
        def snapshot(pid):
            return self.sql(f"select row_to_json(p)::text,(select json_agg(h order by h.id)::text from hearing h where h.proceeding_id=p.id),(select json_agg(e order by e.id)::text from case_event e where e.case_id=p.case_id) from proceeding p where p.id='{pid}'").stdout
        before=snapshot(p['id'])
        self.req('CHRONOLOGY-PARENT-EDIT','PUT','/api/v1/proceedings/'+p['id'],edit(p,'2026-01-04'),422)
        rows=self.sql(f"select p.filing_date,h.hearing_date from hearing h join proceeding p on p.id=h.proceeding_id where h.id='{h['id']}'").stdout.strip()
        filing,hearing=rows.split('\t')
        self.oracle('CHRONOLOGY-PERSISTED','Read persisted proceeding/hearing pair after parent edit','No hearing before its proceeding filing date',dict(filingDate=filing,hearingDate=hearing),filing<=hearing)
        self.oracle('CHRONOLOGY-REJECTION-HISTORY','Compare full proceeding/hearing/history snapshot before/after invalid parent edit','No data or timeline change',dict(unchanged=before==snapshot(p['id'])),before==snapshot(p['id']))
        # Fresh fixture lets deeper regression continue even if the original defect survives.
        cc=self.case('QA chronology constraints');pp=self.proceeding(cc)
        hh=self.req('CHRONOLOGY-DB-FIXTURE','POST','/api/v1/proceedings/'+pp['id']+'/hearings',dict(hearingDate='2026-01-03',purpose='PLEADING'),201)
        other=self.case('QA other chronology case')
        for name,sql in [
            ('PARENT-DATE',f"update proceeding set filing_date='2026-01-04' where id='{pp['id']}'"),
            ('HEARING-DATE',f"update hearing set hearing_date='2026-01-01' where id='{hh['id']}'"),
            ('PARENT-CASE',f"update proceeding set case_id='{other['id']}' where id='{pp['id']}'"),
            ('CHILD-CASE',f"update hearing set case_id='{other['id']}' where id='{hh['id']}'")]:
            r=self.sql('begin; '+sql+'; rollback;',fail=True)
            self.oracle('CHRONOLOGY-SQL-'+name,'BEGIN; '+sql+'; ROLLBACK','Database rejects invariant violation; privileged SQL result separate from API',dict(rejected=r.returncode!=0,error=r.stderr[:350]),r.returncode!=0)
        self.req('CHRONOLOGY-SAME-DAY-EDIT','PUT','/api/v1/proceedings/'+pp['id'],edit(pp,'2026-01-03'),200)
        before=snapshot(pp['id'])
        self.req('CHRONOLOGY-CHILD-BEFORE-FILING','PUT','/api/v1/hearings/'+hh['id'],dict(hearingDate='2026-01-02',purpose='PLEADING',version=hh['version']),422)
        self.oracle('CHRONOLOGY-CHILD-ROLLBACK','Compare complete state after refused hearing reschedule','History and rows unchanged',dict(unchanged=before==snapshot(pp['id'])),before==snapshot(pp['id']))
        for round in range(6):
            racecase=self.case('QA chronology race '+str(round));racep=self.proceeding(racecase)
            def send(which):
                cl=Client(self.appport);cl.cookies=dict(self.admin.cookies)
                if which=='parent':return cl.call('PUT','/api/v1/proceedings/'+racep['id'],edit(racep,'2026-01-04'))[0]
                return cl.call('POST','/api/v1/proceedings/'+racep['id']+'/hearings',dict(hearingDate='2026-01-03',purpose='PLEADING'))[0]
            with futures.ThreadPoolExecutor(max_workers=2) as pool:statuses=list(pool.map(send,['parent','hearing']))
            invalid=int(self.sql(f"select count(*) from hearing h join proceeding p on h.proceeding_id=p.id where p.id='{racep['id']}' and h.hearing_date<p.filing_date").stdout)
            self.oracle('CHRONOLOGY-RACE-'+str(round),'Race filing-date edit with earlier hearing scheduling; inspect committed pair','One succeeds, incompatible other422; no invalid persisted chronology',dict(parent_status=statuses[0],hearing_status=statuses[1],invalid_rows=invalid),statuses in [[200,422],[422,201]] and invalid==0)

        cancelled_case=self.case('QA cancelled history');cp=self.proceeding(cancelled_case)
        ch=self.req('CHRONOLOGY-CANCELLED-FIXTURE','POST','/api/v1/proceedings/'+cp['id']+'/hearings',dict(hearingDate='2026-01-03',purpose='PLEADING'),201)
        self.req('CHRONOLOGY-CANCELLED-RESULT','POST','/api/v1/hearings/'+ch['id']+'/result',dict(status='CANCELLED',version=0))
        before=snapshot(cp['id']);self.req('CHRONOLOGY-CANCELLED-PARENT','PUT','/api/v1/proceedings/'+cp['id'],edit(cp,'2026-01-04'),422)
        self.oracle('CHRONOLOGY-CANCELLED-HISTORY','Try moving filing date after cancelled hearing','Cancelled row and history preserved',dict(unchanged=before==snapshot(cp['id'])),before==snapshot(cp['id']))
        for round in range(3):
            cc=self.case('QA reschedule race '+str(round));pp=self.proceeding(cc)
            hh=self.req('RESCHEDULE-RACE-FIXTURE-'+str(round),'POST','/api/v1/proceedings/'+pp['id']+'/hearings',dict(hearingDate='2026-01-05',purpose='PLEADING'),201)
            def change(which):
                cl=Client(self.appport);cl.cookies=dict(self.admin.cookies)
                if which=='parent':return cl.call('PUT','/api/v1/proceedings/'+pp['id'],edit(pp,'2026-01-04'))[0]
                return cl.call('PUT','/api/v1/hearings/'+hh['id'],dict(hearingDate='2026-01-03',purpose='PLEADING',version=0))[0]
            with futures.ThreadPoolExecutor(max_workers=2) as pool:statuses=list(pool.map(change,['parent','child']))
            invalid=int(self.sql(f"select count(*) from hearing h join proceeding p on p.id=h.proceeding_id where h.id='{hh['id']}' and h.hearing_date<p.filing_date").stdout)
            self.oracle('CHRONOLOGY-RACE-RESCHEDULE-'+str(round),'Race parent date correction and incompatible hearing reschedule','200/422 in either order; chronology intact',dict(statuses=statuses,invalid_rows=invalid),statuses in [[200,422],[422,200]] and invalid==0)

    def judgments(self,p2):
        base='/api/v1/reports/judgments';period='from=2026-01-01&to=2026-01-31'
        self.req('JUDGMENTS-ANON','GET',base,status=401,client=Client(self.appport))
        self.req('JUDGMENTS-DENIED','GET',base+'?from=bad',status=403,client=self.none)
        self.req('JUDGMENT-FIXTURE','POST','/api/v1/proceedings/'+p2['id']+'/judgment',dict(judgmentDate='2026-01-04',outcome='AGAINST',final=False),201)
        result=self.req('JUDGMENTS-PAGED','GET',base+'?'+period+'&size=1')
        self.oracle('JUDGMENTS-SUMMARY-ALL','Compare fixture2 judgments to page size1','One item; total2; final1; outcomes1+1; stage2',result,len(result['items'])==1 and result['totalItems']==2 and result['summary']['total']==2 and result['summary']['final']==1 and result['summary']['byOutcome']['IN_FAVOUR']==1 and result['summary']['byOutcome']['AGAINST']==1 and result['summary']['byStage']['FIRST_INSTANCE']==2)
        ids=[]
        for page in [0,1]:ids.extend(x['id'] for x in self.admin.call('GET',base+'?'+period+f'&size=1&page={page}')[1]['items'])
        self.oracle('JUDGMENTS-PAGE-UNIQUE','Read both pages','2 distinct judgments',ids,len(set(ids))==2)
        for name,q,count in [('FINAL','final=true',1),('NONFINAL','final=false',1),('OUTCOME','outcome=AGAINST',1),('MULTI','outcome=IN_FAVOUR&outcome=AGAINST',2),('STAGE','stage=APPEAL',0),('COURT','courtId='+self.court['id'],2),('UNKNOWN-COURT','courtId=00000000-0000-4000-8000-000000000099',0),('CONTRADICTION','final=true&outcome=AGAINST',0)]:
            r=self.req('JUDGMENTS-'+name,'GET',base+'?'+period+'&'+q)
            self.oracle('JUDGMENTS-FILTER-'+name,'Apply '+q,f'Total and summary {count}',r,r['totalItems']==count and r['summary']['total']==count)
        r=self.req('JUDGMENTS-INCLUSIVE-DATE','GET',base+'?from=2026-01-03&to=2026-01-03')
        self.oracle('JUDGMENTS-INCLUSIVE-DATE-ROWS','Single-day exact boundary','Only Jan3 judgment',r,r['totalItems']==1 and r['items'][0]['judgmentDate']=='2026-01-03')
        for q,status in [('page=-1',400),('page=bad',400),('size=0',400),('final=bad',400),('outcome=BAD',400),('stage=BAD',400),('courtId=bad',400),('sort=judgmentDate;drop,asc',400),('from=2026-02-01&to=2026-01-01',422)]:self.req('JUDGMENTS-INVALID-'+q,'GET',base+'?'+q,status=status)
        rows=self.admin.call('GET',base+'?'+period+'&sort=judgmentDate,asc')[1]['items']
        self.oracle('JUDGMENTS-ORDER-CASE-PRIVACY','Read ascending report and inspect fields','Ordered dates; correct case references; no reasoning/free-text payloads',rows,[r['judgmentDate'] for r in rows]==['2026-01-03','2026-01-04'] and all(r['caseReferenceNo'].startswith('QA56-') and 'reasoning' not in r for r in rows))

    def tasks(self):
        c=self.case();p=self.proceeding(c);self.browser_case=c;self.browser_proceeding=p
        aid=self.sql("select id from app_user where username='qaadmin'").stdout.strip()
        sid=self.sql("select id from app_user where username='phase_staff'").stdout.strip()
        base='/api/v1/tasks';body=dict(title=' QA independent task ',description='QA isolated task',caseId=c['id'],assigneeId=aid,dueDate=self.today.isoformat())
        self.req('TASK-ANON','GET',base,status=401,client=Client(self.appport));self.req('TASK-DENIED','POST',base,{},403,self.none)
        self.req('REMINDER-DENIED','GET',base+'/reminders',status=403,client=self.none)
        t=self.req('TASK-CREATE','POST',base,body,201,self.staff);path=base+'/'+t['id']
        self.oracle('TASK-CASE-DEFAULTS','Read created case task','Trimmed title; same case; normal/open; not overdue; dueSoon',t,t['title']=='QA independent task' and t['caseId']==c['id'] and t['priority']=='NORMAL' and t['status']=='OPEN' and not t['overdue'] and t['dueSoon'])
        for name,data,status in [('BLANK',{**body,'title':'   '},400),('LONG',{**body,'title':'x'*201},400),('PAST',{**body,'dueDate':(self.today-dt.timedelta(days=1)).isoformat()},400),('MISSING-CASE',{**body,'caseId':'00000000-0000-4000-8000-000000000099'},404),('MISSING-ASSIGNEE',{**body,'assigneeId':'00000000-0000-4000-8000-000000000099'},422)]:self.req('TASK-'+name,'POST',base,data,status)
        self.sql("update app_user set enabled=false where username='phase_none'")
        disabled=self.sql("select id from app_user where username='phase_none'").stdout.strip();self.req('TASK-DISABLED-ASSIGNEE','POST',base,{**body,'assigneeId':disabled},422)
        self.req('TASK-STALE','PUT',path,{**body,'version':-1},409)
        contract=(Path(self.args.candidate)/'docs/delivery/PHASE-5-ACCEPTANCE.md').read_text()
        if '`OPEN` → `DONE` directly' in contract:
            self.req('TASK-ILLEGAL-SAME-STATE','POST',path+'/status',dict(status='OPEN',version=0),422)
            direct=self.req('TASK-DIRECT-DONE-PROPOSED','POST',path+'/status',dict(status='DONE',version=0))
            self.oracle('TASK-DIRECT-COMPLETION','Proposed QAD-031 contract; owner decision still open','DONE with completion timestamp',direct,direct['status']=='DONE' and bool(direct['completedAt']))
        else:
            self.req('TASK-ILLEGAL-STATE','POST',path+'/status',dict(status='DONE',version=0),422)
        # Independent fixture for remaining lifecycle tests: a failed contract check must not cascade.
        t=self.req('TASK-LIFECYCLE-FIXTURE','POST',base,body,201);path=base+'/'+t['id']
        for status,version in [('IN_PROGRESS',0),('DONE',1),('OPEN',2)]:
            r=self.req('TASK-STATE-'+status,'POST',path+'/status',dict(status=status,version=version))
            self.oracle('TASK-COMPLETION-'+status,'Change status and read timestamp','completedAt iff DONE',r['completedAt'],bool(r['completedAt'])==(status=='DONE'))
        r=self.req('TASK-REASSIGN','PUT',path,{**body,'version':3,'assigneeId':sid})
        self.oracle('TASK-ASSIGNEE-CHANGED','Read reassignment','Staff id and version4',r,r['assigneeId']==sid and r['version']==4)
        def status_change(status):
            cl=Client(self.appport);cl.cookies=dict(self.admin.cookies);return cl.call('POST',path+'/status',dict(status=status,version=4))[0]
        with futures.ThreadPoolExecutor(max_workers=2) as pool:statuses=sorted(pool.map(status_change,['IN_PROGRESS','CANCELLED']))
        self.oracle('TASK-RACE','Two outcomes same version','200/409',statuses,statuses==[200,409])
        for q in ['due=bad','status=bad','assignee=bad']:self.req('TASK-INVALID-'+q,'GET',base+'?'+q,status=400)
        for q in ['page=-1','size=bad']:self.req('TASK-PAGING-'+q,'GET',base+'?'+q,status=400)
        self.sql("create function qa_reject_task_event() returns trigger language plpgsql as $$ begin if new.type like 'TASK_%' then raise exception 'QA injected failure'; end if; return new; end $$; create trigger qa_task_event before insert on case_event for each row execute function qa_reject_task_event()")
        before=self.sql('select (select count(*) from task),(select count(*) from case_event)').stdout
        s=self.admin.call('POST',base,body)[0];after=self.sql('select (select count(*) from task),(select count(*) from case_event)').stdout
        self.oracle('TASK-CREATE-ROLLBACK','Fail local timeline insert then create','500; task/event counts unchanged',dict(status=s,before=before,after=after),s==500 and before==after)
        self.sql('drop trigger qa_task_event on case_event; drop function qa_reject_task_event()')
        for name,sql in [('ORPHAN-CASE',f"update task set case_id='00000000-0000-4000-8000-000000000099' where id='{t['id']}'"),('COMPLETED-CHECK',f"update task set status='DONE',completed_at=null where id='{t['id']}'")]:
            r=self.sql(sql,fail=True);self.oracle('TASK-SQL-'+name,sql,'Reject by constraint',r.returncode,r.returncode!=0)
        # Past dates are simulated only in the disposable DB, to test current-clock reminders.
        for i,delta in enumerate([-1,0,3,4]):
            task=self.req('REMINDER-FIXTURE-'+str(i),'POST',base,{**body,'caseId':None,'title':'QA reminder '+str(i)},201)
            day=(self.today+dt.timedelta(days=delta)).isoformat();self.sql(f"update task set due_date='{day}' where id='{task['id']}'")
        r=self.req('REMINDERS-SELF','GET',base+'/reminders')
        self.oracle('REMINDERS-BOUNDARIES','Own fixtures yesterday/today/+3/+4; other user task excluded','overdue1, dueSoon2, three-day window',r,r['overdue']==1 and r['dueSoon']==2 and r['dueSoonDays']==3)
        self.req('TASK-ARCHIVE-CASE','POST','/api/v1/cases/'+c['id']+'/status',dict(status='CLOSED',version=0))
        self.req('TASK-ARCHIVE-CASE-2','POST','/api/v1/cases/'+c['id']+'/status',dict(status='ARCHIVED',version=1))
        self.req('TASK-ARCHIVED-CREATE','POST',base,body,422,rule='CASE_ARCHIVED')

    def exports(self):
        import csv,io,http.client
        base='/api/v1/reports/cases/export'
        self.req('EXPORT-ANON','GET',base+'?format=csv',status=401,client=Client(self.appport))
        self.sql("insert into app_role(code,name) values('QA_REPORT_VIEW','QA report viewer'); insert into role_permission select id,'REPORT_VIEW' from app_role where code='QA_REPORT_VIEW'; insert into user_role select u.id,r.id from app_user u,app_role r where u.username='phase_none' and r.code='QA_REPORT_VIEW'")
        self.req('EXPORT-VIEWER-READ','GET','/api/v1/reports/cases',client=self.none)
        self.req('EXPORT-VIEWER-DENIED','GET',base+'?format=csv',status=403,client=self.none)
        self.req('EXPORT-JUDGMENTS-VIEWER-DENIED','GET','/api/v1/reports/judgments/export?format=csv',status=403,client=self.none)
        for q,status in [('',400),('format=unsupported',400),('format=csv&lang=bad',400),('format=csv&sort=bad',400)]:self.req('EXPORT-INVALID-'+q,'GET',base+'?'+q,status=status)
        formula=self.case('=1+1');quoted=self.case('QA comma, "quoted" عربي')
        def raw(path):
            conn=http.client.HTTPConnection('127.0.0.1',self.appport,timeout=30)
            conn.request('GET',path,headers={'Cookie':'; '.join(k+'='+v for k,v in self.admin.cookies.items())})
            r=conn.getresponse();body=r.read();headers={k.lower():v for k,v in r.getheaders()};status=r.status;conn.close();return status,body,headers
        before=int(self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout)
        s,b,h=raw(base+'?format=csv&lang=en&size=1&page=9&sort=referenceNo,asc');rows=list(csv.reader(io.StringIO(b.decode('utf-8-sig'))))
        self.oracle('EXPORT-HEADERS','Download actual CSV','200 BOM UTF8 attachment/nosniff/no-store',dict(status=s,bom=b.startswith(b'\xef\xbb\xbf'),contentType=h.get('content-type'),disposition=h.get('content-disposition'),nosniff=h.get('x-content-type-options'),cache=h.get('cache-control')),s==200 and b.startswith(b'\xef\xbb\xbf') and 'text/csv' in h.get('content-type','') and 'attachment' in h.get('content-disposition','') and h.get('x-content-type-options')=='nosniff' and 'no-store' in h.get('cache-control',''))
        self.oracle('EXPORT-ALL-ROWS','Export with page9/size1 and parse CSV','Header plus8 rows regardless paging',len(rows),len(rows)==9)
        self.oracle('EXPORT-FORMULA-QUOTING','Parse exported formula title and comma/quote/Arabic fixture','Formula neutralized; literal quoting/Arabic preserved',dict(formula="'=1+1" in [r[1] for r in rows[1:]],quoted=quoted['title'] in [r[1] for r in rows[1:]]),"'=1+1" in [r[1] for r in rows[1:]] and quoted['title'] in [r[1] for r in rows[1:]])
        s,b,h=raw(base+'?format=csv&lang=ar&q=literal');ar=list(csv.reader(io.StringIO(b.decode('utf-8-sig'))))
        self.oracle('EXPORT-FILTER-ARABIC','Download Arabic filtered report','Header localized; one matching case',ar,s==200 and len(ar)==2 and ar[0]!=rows[0] and 'literal' in ar[1][1])
        s,b,h=raw('/api/v1/reports/judgments/export?format=csv&lang=en&from=2026-01-01&to=2026-01-31&final=true');jr=list(csv.reader(io.StringIO(b.decode('utf-8-sig'))))
        self.oracle('EXPORT-JUDGMENT-FILTER','Export final judgments only','Header+one final judgment',jr,s==200 and len(jr)==2 and jr[1][0]=='2026-01-03')
        audit=self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout
        self.oracle('EXPORT-AUDIT-ONCE','Count audit rows after three successful and preceding refused requests','Exactly3 successful exports audited',int(audit)-before,int(audit)-before==3)
        # Exact row cap via local fixtures only. No UAT bulk writes.
        self.sql("insert into legal_case(reference_no,title,case_type,opened_on,created_by,updated_by) select 'QA-CAP-'||g,'QA cap','CIVIL','2026-01-01',u.id,u.id from generate_series(1,9992) g cross join app_user u where u.username='qaadmin'")
        s,b,h=raw(base+'?format=csv');rowcount=len(list(csv.reader(io.StringIO(b.decode('utf-8-sig')))))
        self.oracle('EXPORT-CAP-10000','Export exactly10000 local fixture cases','200 and10000 data rows',dict(status=s,rows=rowcount-1),s==200 and rowcount==10001)
        self.case('QA one above limit');before=self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout
        self.req('EXPORT-CAP-10001','GET',base+'?format=csv',status=422,rule='EXPORT_TOO_LARGE')
        after=self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout
        self.oracle('EXPORT-REFUSAL-NOT-AUDITED-SUCCESS','Inspect audit after oversized refusal','No success export audit added',dict(before=before,after=after),before==after)
        # All documented CSV formula prefixes, including legacy values with leading controls.
        # Mutations are confined to this disposable QA DB; never load 10k rows into UAT.
        markers=['=1+1','+SUM(1,1)','-1+1','@SUM(1,1)','\t=1+1','\r=1+1']
        ids=self.sql("select id from legal_case where reference_no like 'QA56-%' order by reference_no limit 6").stdout.splitlines()
        for cid,marker in zip(ids,markers):self.sql(f"update legal_case set title=$qa${marker}$qa$ where id='{cid}'")
        s,b,h=raw(base+'?format=csv&lang=en&q=QA56');parsed=list(csv.reader(io.StringIO(b.decode('utf-8-sig'))))
        titles=[r[1] for r in parsed[1:]]
        self.oracle('EXPORT-FORMULA-ALL-PREFIXES','Export six real DB fixtures beginning =,+,-,@,TAB,CR','Every marker preserved with leading apostrophe; no unescaped cell',dict(status=s,protected=[("'"+m) in titles for m in markers]),s==200 and all(("'"+m) in titles for m in markers))
        self.sql("create function qa_csv_audit_fail() returns trigger language plpgsql as $$ begin if new.action='REPORT_EXPORT' then raise exception 'QA injected audit failure'; end if; return new; end $$; create trigger qa_csv_audit_fail before insert on audit_event for each row execute function qa_csv_audit_fail()")
        before=self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout
        s,b,h=raw(base+'?format=csv&q=QA56');after=self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout
        self.sql('drop trigger qa_csv_audit_fail on audit_event; drop function qa_csv_audit_fail()')
        self.oracle('CSV-AUDIT-FAIL-CLOSED','Inject local audit failure then request valid filtered CSV','500; no attachment and no success audit',dict(status=s,attachment=h.get('content-disposition'),audit_unchanged=before==after),s==500 and 'attachment' not in h.get('content-disposition','') and before==after)

    def browser(self):
        frontend=Path(self.args.candidate)/'frontend';uiport=port();log=(self.output/'vite.log').open('w')
        env=os.environ.copy();env['LAWDESK_API_ORIGIN']=f'http://127.0.0.1:{self.appport}'
        proc=subprocess.Popen(['node',str(frontend/'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port',str(uiport),'--strictPort'],cwd=frontend,env=env,stdout=log,stderr=subprocess.STDOUT)
        try:
            import time,urllib.request
            for _ in range(60):
                try:urllib.request.urlopen(f'http://127.0.0.1:{uiport}');break
                except OSError:time.sleep(.3)
            env.update(LAWDESK_LOCAL_QA_SECRET=CHANGED,LAWDESK_QA_CHROMIUM='/home/aiagent/.cache/ms-playwright/chromium_headless_shell-1248/chrome-headless-shell-linux64/chrome-headless-shell',LD_LIBRARY_PATH='/tmp/lawdesk-qa-browser-libs/usr/lib/x86_64-linux-gnu')
            subprocess.run(['node',str(Path(__file__).parent/'browser/phase56-local.mjs'),f'http://127.0.0.1:{uiport}',self.args.sha,self.args.kind,str(self.output/'browser'),self.browser_case['id'],self.browser_proceeding['id']],env=env,check=False)
        finally:proc.terminate();proc.wait(timeout=15);log.close()


if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.add_argument('--kind',choices=['hearings','hearing-chronology','dashboard','inventory','judgments','tasks','exports'],required=True);p.add_argument('--browser',action='store_true')
    raise SystemExit(PhaseReview(p.parse_args()).execute())
