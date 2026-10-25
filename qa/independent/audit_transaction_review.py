"""Independent audit/business atomicity and durable security-event tests.
Fault injection ONLY in disposable PostgreSQL; no credential/hash/session evidence.
"""
import argparse,json
from phase56_operations import OperationReview
from phase234_review import Client,INITIAL,CHANGED

class AuditTransactions(OperationReview):
    def count(self,action,outcome='SUCCESS',target=None):
        return int(self.sql("select count(*) from audit_event where action='"+action+"' and outcome='"+outcome+"'"+(" and target_id='"+target+"'"if target else '')).stdout)

    def fault(self,table,event,condition='true',deferred=True):
        self.sql("create function qa_audit_tx_fault() returns trigger language plpgsql as $$ begin raise exception 'QA transaction audit fault'; end $$")
        clause='create constraint trigger'if deferred else 'create trigger'
        when='after'if deferred else 'before'
        timing='deferrable initially deferred'if deferred else ''
        self.sql(f"{clause} qa_audit_tx_fault {when} {event} on {table} {timing} for each row when ({condition}) execute function qa_audit_tx_fault()")
        self.fault_table=table

    def clear(self):
        self.sql(f'drop trigger qa_audit_tx_fault on {self.fault_table};drop function qa_audit_tx_fault()')

    def user(self,name):
        s,u,_=self.admin.call('POST','/api/v1/users',{'username':name,'email':name+'@example.invalid','displayName':'QA audit user','locale':'en','roles':['LEGAL_STAFF'],'temporaryPassword':INITIAL})
        assert s==201
        return u

    def user_snapshot(self,id):
        # Full row includes password hash internally, but only equality boolean enters evidence.
        return self.sql(f"select row_to_json(u),(select json_agg(role_id order by role_id) from user_role where user_id=u.id) from app_user u where id='{id}'").stdout

    def role_snapshot(self,id):
        return self.sql(f"select row_to_json(r),(select json_agg(permission order by permission) from role_permission where role_id=r.id) from app_role r where id='{id}'").stdout

    def failure_probe(self,label,actions,call,table,event,condition,snapshot,deferred=True):
        before=snapshot();counts={a:self.count(a)for a in actions}
        self.fault(table,event,condition,deferred)
        try:status=call()[0]
        finally:self.clear()
        delta={a:self.count(a)-n for a,n in counts.items()};unchanged=before==snapshot()
        self.check(label,'LD-008,LD-009,LD-010,LD-011','Snapshot owned business state/audit -> install '+('deferred COMMIT'if deferred else 'immediate dependent-write')+' failure on '+table+' -> authenticated operation -> remove fault -> compare committed state','500; business state unchanged; no SUCCESS events for rolled-back operation',{'http_status':status,'business_snapshot_unchanged':unchanged,'success_audit_delta':delta},status==500 and unchanged and all(n==0 for n in delta.values()))

    def notes(self):
        # One non-QA bootstrap fixture is local only; every mutated account below is owned synthetic.
        u=self.user('qa_audit_target');uid=u['id'];path='/api/v1/users/'+uid
        cases=[
            ('USER-UPDATE',['USER_UPDATE'],'PUT',path,{'email':u['email'],'displayName':'QA modified','locale':'ar','version':0}),
            ('USER-DISABLE',['USER_DISABLE'],'PUT',path+'/enabled',{'enabled':False}),
            ('USER-ROLES',['USER_ROLES_CHANGE'],'PUT',path+'/roles',{'roles':['LEGAL_MANAGER']}),
            ('PASSWORD-RESET',['PASSWORD_RESET'],'POST',path+'/password-reset',{'temporaryPassword':CHANGED})]
        for label,actions,method,url,body in cases:
            self.failure_probe('AUDIT-COMMIT-'+label,actions,lambda m=method,p=url,b=body:self.admin.call(m,p,b),'app_user','update',f"NEW.id='{uid}'",lambda:self.user_snapshot(uid))
        self.sql(f"update app_user set enabled=false where id='{uid}'")
        self.failure_probe('AUDIT-COMMIT-USER-ENABLE',['USER_ENABLE'],lambda:self.admin.call('PUT',path+'/enabled',{'enabled':True}),'app_user','update',f"NEW.id='{uid}'",lambda:self.user_snapshot(uid))
        self.sql(f"update app_user set enabled=true where id='{uid}'")
        self.failure_probe('AUDIT-COMMIT-USER-CREATE',['USER_CREATE'],lambda:self.admin.call('POST','/api/v1/users',{'username':'qa_audit_failed_create','email':'qa_audit_failed_create@example.invalid','displayName':'QA failed create','locale':'en','roles':['LEGAL_STAFF'],'temporaryPassword':INITIAL}),'app_user','insert',"NEW.username='qa_audit_failed_create'",lambda:self.sql("select count(*) from app_user where username='qa_audit_failed_create'").stdout)
        s,role,_=self.admin.call('POST','/api/v1/roles',{'code':'QA_AUDIT_ROLE','name':'QA audit role','permissions':['CASE_VIEW']});assert s==201
        rid=role['id'];rp='/api/v1/roles/'+rid
        self.failure_probe('AUDIT-COMMIT-ROLE-CREATE',['ROLE_CREATE'],lambda:self.admin.call('POST','/api/v1/roles',{'code':'QA_AUDIT_FAILED','name':'QA failed role','permissions':['CASE_VIEW']}),'app_role','insert',"NEW.code='QA_AUDIT_FAILED'",lambda:self.sql("select count(*) from app_role where code='QA_AUDIT_FAILED'").stdout)
        for label,perms in [('NAME',['CASE_VIEW']),('PERMISSIONS',['CASE_VIEW','CASE_EDIT'])]:
            actions=['ROLE_UPDATE']+(['ROLE_PERMISSIONS_CHANGE']if label=='PERMISSIONS'else [])
            self.failure_probe('AUDIT-COMMIT-ROLE-'+label,actions,lambda ps=perms:self.admin.call('PUT',rp,{'name':'QA revised','permissions':ps,'version':0}),'app_role','update',f"NEW.id='{rid}'",lambda:self.role_snapshot(rid))
        self.failure_probe('AUDIT-ROLE-DEPENDENT-WRITE-ROLLBACK',['ROLE_UPDATE','ROLE_PERMISSIONS_CHANGE'],lambda:self.admin.call('PUT',rp,{'name':'QA revised','permissions':['CASE_VIEW','CASE_EDIT'],'version':0}),'role_permission','insert',f"NEW.role_id='{rid}'",lambda:self.role_snapshot(rid),False)
        # Positive controls: committed changes produce exactly one event of each required action.
        for label,actions,method,url,body in cases:
            current=self.admin.call('GET',path)[1]
            if 'version'in body:body={**body,'version':current['version']}
            counts={a:self.count(a)for a in actions};s=self.admin.call(method,url,body)[0]
            delta={a:self.count(a)-n for a,n in counts.items()}
            self.check('AUDIT-COMMITTED-'+label,'LD-008,LD-009,LD-011',method+' owned user operation without fault -> inspect event delta','Expected success and exactly one audit per committed operation',{'status':s,'delta':delta},s==(204 if label=='PASSWORD-RESET'else 200)and all(n==1 for n in delta.values()))
        counts={a:self.count(a)for a in ['ROLE_UPDATE','ROLE_PERMISSIONS_CHANGE']}
        s=self.admin.call('PUT',rp,{'name':'QA committed role','permissions':['CASE_VIEW','CASE_EDIT'],'version':0})[0]
        delta={a:self.count(a)-n for a,n in counts.items()}
        self.check('AUDIT-COMMITTED-ROLE','LD-010,LD-011','Commit role name/permissions update -> compare audit','200; one ROLE_UPDATE and one ROLE_PERMISSIONS_CHANGE',{'status':s,'delta':delta},s==200 and all(n==1 for n in delta.values()))
        # Probe business-COMMIT and audit-write failure points independently.
        pwuser=self.user('qa_audit_password');pwuid=pwuser['id'];client=Client(self.appport);assert client.login(pwuser['username'])[0]==200
        before=self.user_snapshot(pwuid);n=self.count('PASSWORD_CHANGE','FAILURE',pwuid)
        s=client.call('POST','/api/v1/me/password',{'currentPassword':CHANGED,'newPassword':CHANGED})[0]
        self.check('AUDIT-PASSWORD-FAILED-ATTEMPT-DURABLE','LD-008,LD-011','Wrong current password -> rejected request -> read FAILURE event','400; password state unchanged; one durable BAD_CURRENT_PASSWORD FAILURE',{'status':s,'unchanged':before==self.user_snapshot(pwuid),'failure_delta':self.count('PASSWORD_CHANGE','FAILURE',pwuid)-n},s==400 and before==self.user_snapshot(pwuid)and self.count('PASSWORD_CHANGE','FAILURE',pwuid)-n==1)
        self.failure_probe('AUDIT-PASSWORD-DB-COMMIT-FAILURE',['PASSWORD_CHANGE'],lambda:client.call('POST','/api/v1/me/password',{'currentPassword':INITIAL,'newPassword':CHANGED}),'app_user','update',f"NEW.id='{pwuid}'",lambda:self.user_snapshot(pwuid))
        before=self.user_snapshot(pwuid);n=self.count('PASSWORD_CHANGE','SUCCESS',pwuid)
        self.fault('audit_event','insert',"NEW.action='PASSWORD_CHANGE' AND NEW.outcome='SUCCESS'",False)
        try:s=client.call('POST','/api/v1/me/password',{'currentPassword':INITIAL,'newPassword':CHANGED})[0]
        finally:self.clear()
        unchanged=before==self.user_snapshot(pwuid);delta=self.count('PASSWORD_CHANGE','SUCCESS',pwuid)-n
        fresh=Client(self.appport);new_works=fresh.login(pwuser['username'],CHANGED)[0]==200
        self.check('AUDIT-PASSWORD-AUDIT-FAILURE-ATOMICITY','LD-008,LD-011','Reject only PASSWORD_CHANGE SUCCESS audit insert -> change own password -> inspect DB and authenticate new password','Failed audited operation must not leave committed unaudited password change',{'status':s,'business_snapshot_unchanged':unchanged,'success_audit_delta':delta,'new_password_authenticates':new_works},s==500 and unchanged and delta==0 and not new_works)
        if self.args.extended:
            old_session=client.call('GET','/api/v1/me')[0]
            old_login=Client(self.appport).login(pwuser['username'],INITIAL)[0]
            self.check('AUDIT-PASSWORD-FAILURE-SESSION-PRESERVED','LD-007,LD-008,LD-011','After DB and audit failures -> use preexisting session -> login with old password','Original session200;old password200;new password rejected',{'old_session':old_session,'old_password_login':old_login,'new_password_rejected':not new_works},old_session==200 and old_login==200 and not new_works)
        # Normal committed password change and independent security denial events must remain observable.
        known=CHANGED if new_works else INITIAL;next_password=CHANGED+'-next'
        if not new_works:assert fresh.login(pwuser['username'],INITIAL)[0]==200
        n=self.count('PASSWORD_CHANGE','SUCCESS',pwuid);s=fresh.call('POST','/api/v1/me/password',{'currentPassword':known,'newPassword':next_password})[0]
        self.check('AUDIT-PASSWORD-COMMITTED','LD-008,LD-011','Valid own password change with functioning audit','204; exactly one SUCCESS',{'status':s,'delta':self.count('PASSWORD_CHANGE','SUCCESS',pwuid)-n},s==204 and self.count('PASSWORD_CHANGE','SUCCESS',pwuid)-n==1)
        if self.args.extended:
            n=self.count('SESSION_REVOKED','DENIED',pwuid);loser=client.call('GET','/api/v1/me')[0];kept=fresh.call('GET','/api/v1/me')[0]
            self.check('AUDIT-PASSWORD-COMMIT-REVOKES-OTHER-SESSION','LD-007,LD-008,LD-011','After successful password retry -> old session protected GET -> winning session GET','Old401 with durable SESSION_REVOKED/DENIED once;winning200',{'old_session':loser,'winning_session':kept,'denied_delta':self.count('SESSION_REVOKED','DENIED',pwuid)-n},loser==401 and kept==200 and self.count('SESSION_REVOKED','DENIED',pwuid)-n==1)
        n=self.count('LOGIN','FAILURE');s=Client(self.appport).login('qa_unknown_audit',INITIAL)[0]
        self.check('AUDIT-LOGIN-FAILURE-DURABLE','LD-007,LD-011','Unknown login request ->401 -> read audit','401 and one durable FAILURE independent of refused request',{'status':s,'failure_delta':self.count('LOGIN','FAILURE')-n},s==401 and self.count('LOGIN','FAILURE')-n==1)
        n=self.count('ACCESS_DENIED','DENIED');s=self.none.call('POST','/api/v1/users',{})[0]
        self.check('AUDIT-ACCESS-DENIAL-DURABLE','LD-010,LD-011','No-permission client POST users ->403 -> read audit','403 and durable DENIED; do not roll back security evidence',{'status':s,'denied_delta':self.count('ACCESS_DENIED','DENIED')-n},s==403 and self.count('ACCESS_DENIED','DENIED')-n==1)


        if self.args.extended:self.extended()

    def extended(self):
        def retry(label,actions,call,snapshot,expected):
            before=snapshot();counts={a:self.count(a)for a in actions}
            literals=','.join("'"+a+"'"for a in actions)
            self.fault('audit_event','insert',"NEW.outcome='SUCCESS' AND NEW.action IN ("+literals+")",False)
            try:status=call()[0]
            finally:self.clear()
            delta={a:self.count(a)-n for a,n in counts.items()};unchanged=before==snapshot()
            self.check('AUDIT-INSERT-ROLLBACK-'+label,'LD-008,LD-009,LD-010,LD-011','Refuse only matching SUCCESS audit insert -> execute owned administration operation -> inspect business and audit','500; full business snapshot unchanged; zero SUCCESS events',{'status':status,'unchanged':unchanged,'delta':delta},status==500 and unchanged and all(n==0 for n in delta.values()))
            status=call()[0];delta={a:self.count(a)-n for a,n in counts.items()}
            self.check('AUDIT-RETRY-COMMIT-'+label,'LD-008,LD-009,LD-010,LD-011','Remove audit fault -> retry same operation -> compare persisted business and audit','Successful commit; business changed; exactly one SUCCESS per required action',{'status':status,'business_changed':before!=snapshot(),'delta':delta},status==expected and before!=snapshot()and all(n==1 for n in delta.values()))
        for label,action in [('UPDATE','USER_UPDATE'),('DISABLE','USER_DISABLE'),('ENABLE','USER_ENABLE'),('ROLES','USER_ROLES_CHANGE'),('RESET','PASSWORD_RESET')]:
            u=self.user('qa_retry_'+label.lower());id=u['id'];path='/api/v1/users/'+id
            if label=='ENABLE':self.sql(f"update app_user set enabled=false where id='{id}'")
            method='PUT';url=path;expected=200
            if label=='UPDATE':body={'email':u['email'],'displayName':'QA retried update','locale':'ar','version':0}
            elif label in ['DISABLE','ENABLE']:url+='/enabled';body={'enabled':label=='ENABLE'}
            elif label=='ROLES':url+='/roles';body={'roles':['LEGAL_MANAGER']}
            else:method='POST';url+='/password-reset';body={'temporaryPassword':CHANGED};expected=204
            retry('USER-'+label,[action],lambda m=method,p=url,b=body:self.admin.call(m,p,b),lambda id=id:self.user_snapshot(id),expected)
        retry('USER-CREATE',['USER_CREATE'],lambda:self.admin.call('POST','/api/v1/users',{'username':'qa_retry_create','email':'qa_retry_create@example.invalid','displayName':'QA retry create','locale':'en','roles':['LEGAL_STAFF'],'temporaryPassword':INITIAL}),lambda:self.sql("select count(*) from app_user where username='qa_retry_create'").stdout,201)
        retry('ROLE-CREATE',['ROLE_CREATE'],lambda:self.admin.call('POST','/api/v1/roles',{'code':'QA_RETRY_CREATE','name':'QA retry create','permissions':['CASE_VIEW']}),lambda:self.sql("select count(*) from app_role where code='QA_RETRY_CREATE'").stdout,201)
        for label,permissions,actions in [('NAME',['CASE_VIEW'],['ROLE_UPDATE']),('PERMISSIONS',['CASE_VIEW','CASE_EDIT'],['ROLE_UPDATE','ROLE_PERMISSIONS_CHANGE'])]:
            status,r,_=self.admin.call('POST','/api/v1/roles',{'code':'QA_RETRY_'+label,'name':'QA initial role','permissions':['CASE_VIEW']});assert status==201
            rid=r['id'];rp='/api/v1/roles/'+rid;body={'name':'QA retry role','permissions':permissions,'version':0}
            retry('ROLE-'+label,actions,lambda p=rp,b=body:self.admin.call('PUT',p,b),lambda id=rid:self.role_snapshot(id),200)
        # Optimistic-concurrency losers must not emit phantom SUCCESS; a subsequent fresh-version edit records one more.
        for kind in ['USER','ROLE']:
            if kind=='USER':
                target=self.user('qa_race_user');path='/api/v1/users/'+target['id'];body={'email':target['email'],'displayName':'QA concurrent','locale':'en','version':0};actions=['USER_UPDATE']
            else:
                status,target,_=self.admin.call('POST','/api/v1/roles',{'code':'QA_RACE_ROLE','name':'QA initial','permissions':['CASE_VIEW']});assert status==201
                path='/api/v1/roles/'+target['id'];body={'name':'QA concurrent','permissions':['CASE_VIEW','CASE_EDIT'],'version':0};actions=['ROLE_UPDATE','ROLE_PERMISSIONS_CHANGE']
            counts={a:self.count(a)for a in actions};clients=[self.sibling(),self.sibling()]
            statuses=sorted(self.parallel([lambda cl=cl,p=path,b=body:cl.call('PUT',p,b)[0]for cl in clients]));delta={a:self.count(a)-n for a,n in counts.items()}
            self.check('AUDIT-CONCURRENT-'+kind,'LD-009,LD-010,LD-011','Two sessions PUT same owned row/version concurrently -> read events','200/409; exactly one SUCCESS for each committed action',{'statuses':statuses,'delta':delta},statuses==[200,409]and all(n==1 for n in delta.values()))
            if kind=='ROLE':body={**body,'permissions':['CASE_VIEW'],'name':'QA fresh version'}
            else:body={**body,'displayName':'QA fresh version'}
            version=self.admin.call('GET',path)[1]['version'];status=self.admin.call('PUT',path,{**body,'version':version})[0];delta={a:self.count(a)-n for a,n in counts.items()}
            self.check('AUDIT-CONCURRENT-RETRY-'+kind,'LD-009,LD-010,LD-011','Retry loser using fresh version and a distinct real edit','200; total two SUCCESS events per action for two commits',{'status':status,'delta':delta},status==200 and all(n==2 for n in delta.values()))
        u=self.user('qa_password_race');uid=u['id'];clients=[Client(self.appport),Client(self.appport)]
        for cl in clients:assert cl.login(u['username'])[0]==200
        before=self.count('PASSWORD_CHANGE','SUCCESS',uid)
        passwords=[CHANGED+'-one',CHANGED+'-two']
        statuses=self.parallel([lambda cl=cl,pw=pw:cl.call('POST','/api/v1/me/password',{'currentPassword':INITIAL,'newPassword':pw})[0]for cl,pw in zip(clients,passwords)])
        valid=[Client(self.appport).login(u['username'],pw)[0]for pw in passwords]
        self.check('AUDIT-CONCURRENT-PASSWORD','LD-007,LD-008,LD-011','Two authenticated sessions change same credential version concurrently -> try both new passwords','One204, other401or400; exactly one credential valid and oneSUCCESS',{'statuses':statuses,'login_statuses':valid,'success_delta':self.count('PASSWORD_CHANGE','SUCCESS',uid)-before},statuses.count(204)==1 and all(s in [204,401,400]for s in statuses)and valid.count(200)==1 and self.count('PASSWORD_CHANGE','SUCCESS',uid)-before==1)
        bootstrap=self.sql("select count(*) from audit_event a join app_user u on u.id::text=a.target_id where u.username='qaadmin' and a.action='USER_CREATE' and a.reason='BOOTSTRAP_ADMIN' and a.outcome='SUCCESS'").stdout
        self.check('AUDIT-BOOTSTRAP-COMMITTED','LD-008,LD-011','Inspect original real cold-start bootstrap user and event','One persisted bootstrap admin with one matching SUCCESS',{'events':bootstrap},bootstrap=='1')

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.add_argument('--extended',action='store_true')
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(AuditTransactions(p.parse_args()).execute())
