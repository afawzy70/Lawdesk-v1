"""Owner decisions: invalid pagination400 and OPEN->DONE with truthful atomic audit."""
import argparse,json
from phase56_operations import OperationReview
from phase234_review import Client

class OwnerPolicy(OperationReview):
    def notes(self):
        c=self.case();p=self.proceeding(c)
        paths=['/users','/audit','/courts','/experts','/hearings','/documents','/tasks','/cases',f"/cases/{c['id']}/hearings",f"/cases/{c['id']}/notes",f"/cases/{c['id']}/timeline",f"/cases/{c['id']}/proceedings",f"/cases/{c['id']}/judgments",f"/cases/{c['id']}/special-decisions",'/reports/cases','/reports/judgments','/reports/hearings']
        for index,path in enumerate(paths):
            base='/api/v1'+path;results={}
            for q in ['page=-1','size=0','size=-1','page=bad','size=1.5','page=2147483648']:
                status,b,_=self.admin.call('GET',base+'?'+q);results[q]={'status':status,'fields':[x.get('field')for x in b.get('errors',[])]if isinstance(b,dict)else []}
            self.check('PAGING-INVALID-'+str(index),'LD-009,LD-011,LD-015,LD-018,LD-024,LD-025,LD-027,LD-029,LD-031,LD-032','GET '+base+' with each negative/zero/noninteger/overflow query','Every malformed parameter400; never500',{'path':base,'responses':results},all(x['status']==400 for x in results.values()))
            omitted=self.admin.call('GET',base);large=self.admin.call('GET',base+'?size=101')
            self.check('PAGING-DEFAULT-CAP-'+str(index),'LD-009,LD-015,LD-031','GET '+base+' without page/size then size101','Default page0/size20; valid size101 capped100',{'path':base,'omitted_status':omitted[0],'page':omitted[1].get('page'),'size':omitted[1].get('size'),'large_status':large[0],'large_size':large[1].get('size')},omitted[0]==200 and omitted[1].get('page')==0 and omitted[1].get('size')==20 and large[0]==200 and large[1].get('size')==100)
            denied=self.none.call('GET',base+'?page=bad')[0];anon=Client(self.appport).call('GET',base+'?page=bad')[0]
            self.check('PAGING-AUTH-FIRST-'+str(index),'LD-010','Malformed '+base+' query as no-permission and anonymous clients','403/401 before binding',{'path':base,'denied':denied,'anonymous':anon},denied==403 and anon==401)
        blank={q:self.admin.call('GET','/api/v1/tasks?'+q)[0]for q in ['page=','size=','page=%20%20']}
        self.results.append({'id':'PAGING-EMPTY-OWNER-CLARIFICATION','story_ids':['LD-015','LD-027'],'priority':'P2','preconditions':['Current remediation; owner clarification requested'],'test_data':{'queries':list(blank)},'steps':['Send explicitly empty/whitespace pagination values','Record actual result separately from agreed invalid nonempty cases'],'expected_results':'Pending owner choice:400 or defaults; do not silently approve additional empty-value exception','actual':blank,'status':'NOT_RUN'});self.save()
        actor=self.sql("select id from app_user where username='qaadmin'").stdout
        def task():
            status,t,_=self.admin.call('POST','/api/v1/tasks',{'title':'QA confidential task title','description':'QA private description','caseId':c['id'],'assigneeId':actor});assert status==201;return t
        t=task();path='/api/v1/tasks/'+t['id'];before=int(self.sql("select count(*) from audit_event where action='TASK_STATUS_CHANGED'").stdout)
        status,done,_=self.admin.call('POST',path+'/status',{'status':'DONE','version':0})
        audit=self.admin.call('GET','/api/v1/audit?action=TASK_STATUS_CHANGED')[1]['items'];mine=[r for r in audit if r['targetId']==t['id']]
        self.check('TASK-DIRECT-DONE-AUDIT','LD-027,LD-011','Create OPEN task -> directly DONE -> inspect task/event/security audit','200 DONE/completedAt; exactly one SUCCESS audit with actor, TASK id, OPEN->DONE and no free text',{'status':status,'task_status':done.get('status'),'completed':bool(done.get('completedAt')),'audit':mine},status==200 and done['status']=='DONE'and bool(done['completedAt'])and len(mine)==1 and mine[0]['reason']=='OPEN->DONE'and mine[0]['outcome']=='SUCCESS'and mine[0]['actorId']==actor and 'confidential'not in json.dumps(mine))
        for label,body,expected in [('REPEAT',{'status':'DONE','version':done['version']},422),('STALE',{'status':'OPEN','version':-1},409)]:
            status=self.admin.call('POST',path+'/status',body)[0];count=int(self.sql("select count(*) from audit_event where action='TASK_STATUS_CHANGED'").stdout)
            self.check('TASK-REFUSED-NO-SUCCESS-AUDIT-'+label,'LD-027,LD-011','Refused '+label+' transition -> count task success events','Expected refusal; only original success event',{'status':status,'delta':count-before},status==expected and count-before==1)
        t=task();path='/api/v1/tasks/'+t['id'];snapshot=lambda:self.sql(f"select row_to_json(t),(select json_agg(e order by id)from case_event e where case_id=t.case_id) from task t where id='{t['id']}'").stdout
        original=snapshot();before=self.sql(f"select count(*) from audit_event where action='TASK_STATUS_CHANGED' and target_id='{t['id']}'").stdout
        self.sql("create function qa_task_commit_failure() returns trigger language plpgsql as $$ begin raise exception 'QA deferred commit failure'; end $$;create constraint trigger qa_task_commit_failure after update on task deferrable initially deferred for each row execute function qa_task_commit_failure()")
        try:status=self.admin.call('POST',path+'/status',{'status':'DONE','version':0})[0]
        finally:self.sql('drop trigger qa_task_commit_failure on task;drop function qa_task_commit_failure()')
        after=self.sql(f"select count(*) from audit_event where action='TASK_STATUS_CHANGED' and target_id='{t['id']}'").stdout
        self.check('TASK-COMMIT-ROLLBACK-AUDIT-ATOMICITY','LD-027,LD-011','Install disposable deferred task trigger that fails at COMMIT -> OPEN->DONE -> query task/history/audit after rollback','500; task/history unchanged; no successful status audit for rolled-back write',{'http_status':status,'task_history_unchanged':original==snapshot(),'success_audit_before':before,'success_audit_after':after},status==500 and original==snapshot()and before==after)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(OwnerPolicy(p.parse_args()).execute())
