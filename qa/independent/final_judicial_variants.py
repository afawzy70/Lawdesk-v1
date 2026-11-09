"""Retained Phase4 planned variants, executed on a disposable local database only."""
import argparse
from phase56_operations import OperationReview

class Judicial(OperationReview):
    def notes(self):
        def counts():return self.sql('select (select count(*) from proceeding),(select count(*) from judgment),(select count(*) from special_decision),(select count(*) from case_event)').stdout
        def court(stage):
            s,c,_=self.admin.call('POST','/api/v1/courts',dict(code='QA-V-'+stage,nameAr='محكمة اختبار',nameEn='QA court',level=stage));assert s==201;return c
        def judgment(p):
            s,j,_=self.admin.call('POST','/api/v1/proceedings/'+p['id']+'/judgment',dict(judgmentDate='2026-01-03',outcome='IN_FAVOUR',final=False));assert s==201;return j
        c=self.case('QA inactive court');p=self.proceeding(c);path='/api/v1/courts/'+self.court['id']
        body={k:self.court[k]for k in ['code','nameAr','nameEn','level','version']};body['active']=False
        s,inactive,_=self.admin.call('PUT',path,body);assert s==200
        historical=self.admin.call('GET','/api/v1/proceedings/'+p['id'])
        before=counts();s,b,_=self.admin.call('POST','/api/v1/cases/'+c['id']+'/proceedings',dict(stage='FIRST_INSTANCE',courtId=self.court['id'],courtCaseNumber='QA inactive new',courtCaseYear=2026,filingDate='2026-01-02'))
        stale=self.admin.call('PUT',path,{**body,'active':True})[0]
        self.check('COURT-DEACTIVATED-HISTORY','LD-018,LD-019','Deactivate court → read existing proceeding → reject new assignment → stale court edit','Existing200; new422 COURT_INACTIVE; stale409; unchanged judicial state',{'existing_status':historical[0],'new_status':s,'rule':b.get('rule'),'stale_status':stale,'unchanged':counts()==before},historical[0]==200 and historical[1]['courtId']==self.court['id'] and s==422 and b.get('rule')=='COURT_INACTIVE' and stale==409 and before==counts())
        assert self.admin.call('PUT',path,{**body,'active':True,'version':inactive['version']})[0]==200
        c=self.case('QA proceeding event rollback');url='/api/v1/cases/'+c['id']+'/proceedings';body=dict(stage='FIRST_INSTANCE',courtId=self.court['id'],courtCaseNumber='QA rollback proceeding',courtCaseYear=2026,filingDate='2026-01-02')
        before=counts();self.event_failure('PROCEEDING_')
        try:s=self.admin.call('POST',url,body)[0]
        finally:self.clear_failure()
        unchanged=before==counts();retry=self.admin.call('POST',url,body)[0];events=int(self.sql(f"select count(*) from case_event where case_id='{c['id']}' and type='PROCEEDING_RECORDED'").stdout)
        self.check('PROCEEDING-EVENT-FAILURE-RETRY','LD-014,LD-019','Reject proceeding timeline INSERT → create → inspect rollback → remove fault → identical retry','500 with unchanged counts; retry201 and one timeline event',{'failure_status':s,'unchanged':unchanged,'retry':retry,'events':events},s==500 and unchanged and retry==201 and events==1)
        c=self.case('QA parent variants');first=self.proceeding(c);j=judgment(first);apcourt=court('APPEAL');ccourt=court('CASSATION')
        def appeal(c,first,number):
            s,a,_=self.admin.call('POST','/api/v1/cases/'+c['id']+'/proceedings',dict(stage='APPEAL',courtId=apcourt['id'],courtCaseNumber=number,courtCaseYear=2026,filingDate='2026-01-04',parentProceedingId=first['id']));assert s==201;return a
        a=appeal(c,first,'QA main appeal')
        other=self.case('QA foreign parent');op=self.proceeding(other);oj=judgment(op);oa=appeal(other,op,'QA foreign appeal')
        for label,parent,rule in [('WRONG-STAGE',first,'CASSATION_PARENT_NOT_APPEAL'),('NO-JUDGMENT',a,'CASSATION_REQUIRES_APPEAL_JUDGMENT'),('OTHER-CASE',oa,'PARENT_PROCEEDING_OTHER_CASE')]:
            before=counts();s,b,_=self.admin.call('POST','/api/v1/cases/'+c['id']+'/proceedings',dict(stage='CASSATION',courtId=ccourt['id'],courtCaseNumber='QA '+label,courtCaseYear=2026,filingDate='2026-01-06',parentProceedingId=parent['id']))
            self.check('CASSATION-INVALID-'+label,'LD-021','Submit invalid cassation parent → compare rows/history','422 '+rule+'; no mutation',{'status':s,'rule':b.get('rule'),'unchanged':before==counts()},s==422 and b.get('rule')==rule and before==counts())
        racecase=self.case('QA duplicate judgment');racep=self.proceeding(racecase);url='/api/v1/proceedings/'+racep['id']+'/judgment';body=dict(judgmentDate='2026-01-03',outcome='IN_FAVOUR',final=False)
        clients=[self.sibling(),self.sibling()];replies=self.parallel([lambda cl=cl:cl.call('POST',url,body)for cl in clients]);statuses=sorted(r[0]for r in replies);winner=next(r[1]for r in replies if r[0]==201)
        count=int(self.sql(f"select count(*) from judgment where proceeding_id='{racep['id']}'").stdout);events=int(self.sql(f"select count(*) from case_event where case_id='{racecase['id']}' and type='JUDGMENT_RECORDED'").stdout)
        before=counts();s,b,_=self.admin.call('PUT','/api/v1/judgments/'+winner['id'],{**body,'judgmentDate':'2026-01-01','version':winner['version']});after=self.admin.call('GET','/api/v1/judgments/'+winner['id'])[1]
        self.check('JUDGMENT-CONCURRENT-DUPLICATE','LD-022','Two sessions create same proceeding judgment concurrently → inspect uniqueness/event → invalid date edit','201/409; one judgment and event; invalid edit422 and unchanged row/history',{'statuses':statuses,'judgments':count,'events':events,'edit_status':s,'unchanged':after==winner and before==counts()},statuses==[201,409] and count==events==1 and s==422 and after==winner and before==counts())
        url='/api/v1/proceedings/'+first['id']+'/special-decisions';body=dict(decisionType='ENFORCEMENT_SUSPENSION',decisionDate='2026-01-04',effect='GRANTED',judgmentId=j['id'],summary='QA suspension')
        for label,change,rule in [('FOREIGN',{'judgmentId':oj['id']},'DECISION_JUDGMENT_OTHER_CASE'),('BEFORE-FILING',{'decisionDate':'2026-01-01'},'DECISION_BEFORE_FILING')]:
            before=counts();s,b,_=self.admin.call('POST',url,{**body,**change})
            self.check('SUSPENSION-'+label,'LD-023','Submit invalid decision reference/date → compare rows/events','422 '+rule+'; no mutation',{'status':s,'rule':b.get('rule'),'unchanged':before==counts()},s==422 and b.get('rule')==rule and before==counts())
        oldcase=self.admin.call('GET','/api/v1/cases/'+c['id'])[1];oldjudgment=self.admin.call('GET','/api/v1/judgments/'+j['id'])[1]
        s,decision,_=self.admin.call('POST',url,body);newcase=self.admin.call('GET','/api/v1/cases/'+c['id'])[1];newjudgment=self.admin.call('GET','/api/v1/judgments/'+j['id'])[1]
        event=int(self.sql(f"select count(*) from case_event where case_id='{c['id']}' and type='SPECIAL_DECISION_RECORDED'").stdout)
        self.check('SUSPENSION-VALID-INFORMATIONAL','LD-023','Create valid suspension → inspect effect, judgment and case','201 GRANTED; one event; case/judgment unchanged',{'status':s,'effect':decision.get('effect'),'events':event,'unchanged':oldcase==newcase and oldjudgment==newjudgment},s==201 and decision.get('effect')=='GRANTED' and event==1 and oldcase==newcase and oldjudgment==newjudgment)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(Judicial(p.parse_args()).execute())
