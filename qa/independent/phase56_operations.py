"""Independent real HTTP/SQL tests for documents, experts and notes; QA-owned disposable DB only."""
import argparse,concurrent.futures as futures,hashlib,http.client,json,uuid
from pathlib import Path
from phase56_review import PhaseReview
from phase234_review import Client

class OperationReview(PhaseReview):
    def run(self,admin,appport):
        # Reuse existing fixture setup and browser lifecycle; dispatch is below.
        super().run(admin,appport)
    def req(self,id,method,path,body=None,status=200,client=None,rule=None):
        stories={'documents':'LD-026','experts':'LD-025','notes':'LD-029'}[self.args.kind]
        result=self.request(id,stories,client or self.admin,method,path,body,status,rule)
        self.results[-1]['test_data']={'method':method,'path':path,'body':body};self.save();return result
    def oracle(self,id,steps,expected,actual,ok):
        self.check(id,{'documents':'LD-026','experts':'LD-025','notes':'LD-029'}[self.args.kind],steps,expected,actual,ok)
    def raw(self,client,method,path,body=None,ctype=None,csrf=True):
        headers={}
        if method!='GET' and csrf:
            client.call('GET','/api/v1/auth/csrf');headers['X-XSRF-TOKEN']=client.cookies.get('XSRF-TOKEN','')
        headers['Cookie']='; '.join(k+'='+v for k,v in client.cookies.items())
        if ctype:headers['Content-Type']=ctype
        conn=http.client.HTTPConnection('127.0.0.1',self.appport,timeout=40);conn.request(method,path,body,headers);r=conn.getresponse();data=r.read();h={k.lower():v for k,v in r.getheaders()};status=r.status;conn.close();return status,data,h
    def upload(self,cid,content=b'%PDF-1.4\nQA synthetic\n%%EOF',filename='qa.pdf',media='application/pdf',client=None,title='QA document'):
        boundary='qa-'+uuid.uuid4().hex
        body=(f'--{boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\n{title}\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {media}\r\n\r\n').encode()+content+f'\r\n--{boundary}--\r\n'.encode()
        status,data,headers=self.raw(client or self.admin,'POST','/api/v1/cases/'+cid+'/documents',body,'multipart/form-data; boundary='+boundary)
        try:data=json.loads(data)
        except ValueError:data={'non_json_response':True}
        return status,data,headers
    def event_failure(self,prefix):
        self.sql(f"create function qa_event_fail() returns trigger language plpgsql as $$ begin if new.type like '{prefix}%' then raise exception 'QA injected failure'; end if; return new; end $$; create trigger qa_event_fail before insert on case_event for each row execute function qa_event_fail()")
    def clear_failure(self):self.sql('drop trigger qa_event_fail on case_event; drop function qa_event_fail()')
    def parallel(self,functions):
        with futures.ThreadPoolExecutor(max_workers=2) as pool:return list(pool.map(lambda f:f(),functions))
    def sibling(self):
        cl=Client(self.appport);cl.cookies=dict(self.admin.cookies);return cl
    def documents(self):
        c=self.case();self.browser_case=c;self.browser_proceeding=self.proceeding(c)
        path='/api/v1/cases/'+c['id']+'/documents'
        self.req('DOCUMENT-ANON','GET',path,status=401,client=Client(self.appport));self.req('DOCUMENT-DENIED','GET',path,status=403,client=self.none)
        s,b,_=self.upload(c['id'],client=self.none);self.oracle('DOCUMENT-WRITE-DENIED','POST multipart with no permission','403',s,s==403)
        self.sql("insert into app_role(code,name) values('QA_DOCUMENT_VIEW','QA document viewer'); insert into role_permission select id,'DOCUMENT_VIEW' from app_role where code='QA_DOCUMENT_VIEW'; insert into user_role select u.id,r.id from app_user u,app_role r where u.username='phase_none' and r.code='QA_DOCUMENT_VIEW'")
        self.req('DOCUMENT-VIEW-ONLY','GET',path,client=self.none)
        s,_,_=self.upload(c['id'],client=self.none);self.oracle('DOCUMENT-VIEW-ONLY-WRITE','View-only multipart upload','403',s,s==403)
        payload=b'%PDF-1.4\nQA synthetic\n%%EOF';s,d,_=self.upload(c['id'],payload,filename='../../qa.pdf')
        self.oracle('DOCUMENT-ATTACH','Upload synthetic PDF with traversal filename','201; clean filename,size,sha256; no content bytes in metadata',d,s==201 and d['fileName']=='qa.pdf' and d['sizeBytes']==len(payload) and d['sha256']==hashlib.sha256(payload).hexdigest() and 'content' not in d)
        docpath='/api/v1/documents/'+d['id'];s,b,h=self.raw(self.none,'GET',docpath+'/content')
        self.oracle('DOCUMENT-DOWNLOAD','Download through view-only account','Exact bytes, attachment,no-store,nosniff',dict(status=s,exact=b==payload,contentType=h.get('content-type'),disposition=h.get('content-disposition'),cache=h.get('cache-control')),s==200 and b==payload and h.get('content-type')=='application/pdf' and 'attachment' in h.get('content-disposition','') and 'no-store' in h.get('cache-control','') and h.get('x-content-type-options')=='nosniff')
        for name,data,media,title in [('EMPTY',b'','application/pdf','QA'),('MAGIC',b'<script>QA</script>','application/pdf','QA'),('MIME',payload,'text/html','QA'),('TITLE',payload,'application/pdf','x'*201)]:
            s,b,_=self.upload(c['id'],data,media=media,title=title);self.oracle('DOCUMENT-INVALID-'+name,'Upload '+name,'400',dict(status=s,body=b),s==400)
        s,b,_=self.upload('00000000-0000-4000-8000-000000000099');self.oracle('DOCUMENT-UNKNOWN-CASE','Upload unknown case','404',s,s==404)
        cap=self.case('QA PDF byte boundary');limit=10*1024*1024
        s,b,_=self.upload(cap['id'],b'%PDF-'+b'x'*(limit-5));self.oracle('DOCUMENT-EXACT-10MIB','Upload exactly10MiB','201 exact size',dict(status=s,size=b.get('sizeBytes')),s==201 and b.get('sizeBytes')==limit)
        s,b,_=self.upload(cap['id'],b'%PDF-'+b'x'*(limit-4));self.oracle('DOCUMENT-OVER-10MIB','Upload10MiB+1','400 no attachment',dict(status=s,body=b),s==400)
        for i in range(8):assert self.upload(c['id'],title='QA quota '+str(i))[0]==201
        def upload_race():return self.upload(c['id'],client=self.sibling())[0]
        statuses=sorted(self.parallel([upload_race,upload_race]));count=self.sql(f"select count(*) from case_document where case_id='{c['id']}'").stdout
        self.oracle('DOCUMENT-RACE-QUOTA','At9 attachments race2 uploads','201/422 and exactly10 persisted',dict(statuses=statuses,count=count),statuses==[201,422] and count=='10')
        self.req('DOCUMENT-DELETE-STALE','DELETE',docpath+'?version=-1',status=409)
        self.req('DOCUMENT-DELETE','DELETE',docpath+'?version=0',status=204)
        self.req('DOCUMENT-DELETED-CONTENT','GET',docpath+'/content',status=404)
        cascade=self.sql(f"select count(*) from case_document_content where document_id='{d['id']}'").stdout;self.oracle('DOCUMENT-DELETE-CASCADE','Query content after delete','0 orphan bytes',cascade,cascade=='0')
        s,_,_=self.upload(c['id']);self.oracle('DOCUMENT-QUOTA-FREED','Upload after deletion','201',s,s==201)
        rollback=self.case('QA document rollback');self.event_failure('DOCUMENT_');before=self.sql('select (select count(*) from case_document),(select count(*) from case_document_content),(select count(*) from case_event)').stdout
        s,_,_=self.upload(rollback['id']);after=self.sql('select (select count(*) from case_document),(select count(*) from case_document_content),(select count(*) from case_event)').stdout;self.clear_failure()
        self.oracle('DOCUMENT-ROLLBACK','Fail timeline insert, attempt upload, compare metadata/content/events','500; complete rollback',dict(status=s,unchanged=before==after),s==500 and before==after)
        r=self.sql(f"begin; update case_document set case_id='00000000-0000-4000-8000-000000000099' where case_id='{c['id']}'; rollback;",fail=True);self.oracle('DOCUMENT-FK','Try orphan case viaSQL transaction','FK rejects',r.returncode,r.returncode!=0)
        archived=self.case('QA archived attachments','ARCHIVED');s,b,_=self.upload(archived['id']);self.oracle('DOCUMENT-ARCHIVED','Upload archived case','422 CASE_ARCHIVED',dict(status=s,body=b),s==422 and b.get('rule')=='CASE_ARCHIVED')
        # Browser gets an open case with an available slot, not the full quota case.
        self.browser_case=rollback;self.browser_proceeding=self.proceeding(rollback)
    def notes(self):
        c=self.case();self.browser_case=c;self.browser_proceeding=self.proceeding(c);path='/api/v1/cases/'+c['id']+'/notes'
        self.req('NOTES-ANON','GET',path,status=401,client=Client(self.appport));self.req('NOTES-DENIED','POST',path,{},403,self.none)
        note=self.req('NOTE-ADD','POST',path,dict(body=' QA private note <script>alert(1)</script> '),201,self.staff);np='/api/v1/notes/'+note['id']
        self.oracle('NOTE-TRIM-OWNER','Inspect newly added note','Trimmed and editable for author',note,note['body']=='QA private note <script>alert(1)</script>' and note['editable'])
        read=self.req('NOTE-READ-OTHER','GET',path);self.oracle('NOTE-OTHER-NOT-EDITABLE','Admin reads staff note','editable=false',read,not read['items'][0]['editable'])
        self.req('NOTE-ADMIN-CANNOT-EDIT-OTHER','PUT',np,dict(body='QA altered',version=0),422,rule='NOTE_AUTHOR_ONLY')
        self.req('NOTE-ADMIN-CANNOT-DELETE-OTHER','DELETE',np+'?version=0',status=422,rule='NOTE_AUTHOR_ONLY')
        for body in ['', '   ','x'*4001]:self.req('NOTE-INVALID-'+str(len(body)),'POST',path,dict(body=body),400)
        boundary=self.req('NOTE-4000','POST',path,dict(body='x'*4000),201)
        self.req('NOTE-STALE','PUT',np,dict(body='QA edit',version=-1),409,self.staff)
        updated=self.req('NOTE-EDIT','PUT',np,dict(body='QA private revised',version=0),200,self.staff);self.oracle('NOTE-EDIT-MARKER','Read edited own note','version1 and edited flag',updated,updated['version']==1 and updated['edited'])
        text=self.sql(f"select string_agg(summary,'|') from case_event where case_id='{c['id']}' and type like 'NOTE_%'").stdout;self.oracle('NOTE-TIMELINE-PRIVACY','Inspect timeline after add/edit','No note bodies',{'body_found':'QA private' in text},'QA private' not in text)
        owned=self.req('NOTE-RACE-FIXTURE','POST',path,dict(body='QA race'),201)
        def edit(body):return self.sibling().call('PUT','/api/v1/notes/'+owned['id'],dict(body=body,version=0))[0]
        statuses=sorted(self.parallel([lambda:edit('QA first'),lambda:edit('QA second')]))
        self.oracle('NOTE-RACE','Two edits at version0','200/409',statuses,statuses==[200,409])
        self.event_failure('NOTE_');before=self.sql('select (select count(*) from case_note),(select count(*) from case_event)').stdout
        s=self.admin.call('POST',path,dict(body='QA rollback'))[0];after=self.sql('select (select count(*) from case_note),(select count(*) from case_event)').stdout
        self.oracle('NOTE-ADD-ROLLBACK','Fail timeline and add','500; note/events unchanged',dict(status=s,unchanged=before==after),s==500 and before==after)
        s=self.admin.call('DELETE','/api/v1/notes/'+boundary['id']+'?version=0')[0];after2=self.sql('select (select count(*) from case_note),(select count(*) from case_event)').stdout;self.clear_failure()
        self.oracle('NOTE-DELETE-ROLLBACK','Fail timeline and delete','500; note/events unchanged',dict(status=s,unchanged=before==after2),s==500 and before==after2)
        self.req('NOTE-DELETE','DELETE',np+'?version=1',status=204,client=self.staff)
        remaining=self.req('NOTE-POST-DELETE-READ','GET',path);self.oracle('NOTE-DELETE-PERSISTED','List notes afterdelete','Deleted ID absent',dict(ids=[x['id'] for x in remaining['items']]),all(x['id']!=note['id'] for x in remaining['items']))
        r=self.sql(f"begin; update case_note set case_id='00000000-0000-4000-8000-000000000099' where id='{boundary['id']}'; rollback;",fail=True);self.oracle('NOTE-FK','Direct orphan case update','Reject',r.returncode,r.returncode!=0)
        archived=self.case('QA archived notes','ARCHIVED');self.req('NOTE-ARCHIVED','POST','/api/v1/cases/'+archived['id']+'/notes',dict(body='QA'),422,rule='CASE_ARCHIVED')
    def experts(self):
        c=self.case();p=self.proceeding(c);self.browser_case=c;self.browser_proceeding=p
        self.req('EXPERT-ANON','GET','/api/v1/experts',status=401,client=Client(self.appport));self.req('EXPERT-DENIED','POST','/api/v1/experts',{},403,self.none)
        body=dict(name=' QA Expert عربي ',specialty='Engineering',office='QA Office',phone='+20 100 000 0000');e=self.req('EXPERT-CREATE','POST','/api/v1/experts',body,201)
        self.oracle('EXPERT-TRIM','Read registry entry','Normalized active version0',e,e['name']=='QA Expert عربي' and e['active'] and e['version']==0)
        for name,body in [('NAME',dict(name=' ',specialty='QA')),('PHONE',dict(name='QA',specialty='QA',phone='javascript:'))]:self.req('EXPERT-INVALID-'+name,'POST','/api/v1/experts',body,400)
        rb=dict(expertId=e['id'],referralDate='2026-01-03',mission='QA private expert mission',reportDueDate='2026-01-07');path='/api/v1/proceedings/'+p['id']+'/expert-referrals'
        for name,data in [('BEFORE-FILING',{**rb,'referralDate':'2026-01-01'}),('DUE-BEFORE',{**rb,'reportDueDate':'2026-01-02'})]:self.req('REFERRAL-'+name,'POST',path,data,422)
        r=self.req('REFERRAL-CREATE','POST',path,rb,201);rp='/api/v1/expert-referrals/'+r['id'];self.oracle('REFERRAL-CONSISTENCY','Read created referral','Same case/proceeding/expert',r,r['caseId']==c['id'] and r['proceedingId']==p['id'] and r['expertId']==e['id'])
        self.req('SESSION-BEFORE-REFERRAL','POST',rp+'/sessions',dict(sessionDate='2026-01-02'),422)
        r=self.req('SESSION-CREATE','POST',rp+'/sessions',dict(sessionDate='2026-01-05',sessionTime='09:30',location='QA room',notes='QA private session'),201)
        self.oracle('SESSION-PERSISTENCE','Read referral sessions','One exact session',r,len(r['sessions'])==1 and r['sessions'][0]['sessionDate']=='2026-01-05')
        self.req('REPORT-STALE','POST',rp+'/report',dict(reportSubmittedDate='2026-01-06',version=-1),409)
        done=self.req('REPORT-SUBMIT','POST',rp+'/report',dict(reportSubmittedDate='2026-01-06',version=r['version']))
        self.req('SESSION-CLOSED','POST',rp+'/sessions',dict(sessionDate='2026-01-07'),422,rule='REFERRAL_CLOSED')
        self.req('REFERRAL-TERMINAL','POST',rp+'/cancel',dict(version=done['version']),422,rule='REFERRAL_CLOSED')
        # Direct parent modification must preserve referral chronology too.
        self.req('REFERRAL-PARENT-CHRONOLOGY','PUT','/api/v1/proceedings/'+p['id'],dict(courtId=self.court['id'],courtCaseNumber=p['courtCaseNumber'],courtCaseYear=2026,filingDate='2026-01-04',version=p['version']),422)
        invalid=self.sql(f"select count(*) from expert_referral r join proceeding p on p.id=r.proceeding_id where r.id='{r['id']}' and r.referral_date<p.filing_date").stdout
        self.oracle('REFERRAL-CHRONOLOGY-PERSISTED','Read persisted parent/referral dates','0 invalid chronology',invalid,invalid=='0')
        other=self.case('QA expert other');mutation=self.sql(f"begin; update expert_referral set case_id='{other['id']}' where id='{r['id']}'; rollback;",fail=True);self.oracle('REFERRAL-DB-CASE-CONSISTENCY','Try mismatched case_id via SQL transaction','Database rejects case/proceeding mismatch',mutation.returncode,mutation.returncode!=0)
        pp=self.proceeding(other);url='/api/v1/proceedings/'+pp['id']+'/expert-referrals'
        rr=self.req('REFERRAL-RACE-FIXTURE','POST',url,rb,201)
        def outcome(suffix,body):return self.sibling().call('POST','/api/v1/expert-referrals/'+rr['id']+suffix,body)[0]
        statuses=sorted(self.parallel([lambda:outcome('/report',dict(reportSubmittedDate='2026-01-06',version=0)),lambda:outcome('/cancel',dict(version=0))]))
        self.oracle('REFERRAL-RACE-TERMINAL','Race report and cancel at same version','200/409',statuses,statuses==[200,409])
        self.event_failure('EXPERT_');before=self.sql('select (select count(*) from expert_referral),(select count(*) from case_event)').stdout;s=self.admin.call('POST',url,rb)[0];after=self.sql('select (select count(*) from expert_referral),(select count(*) from case_event)').stdout;self.clear_failure()
        self.oracle('REFERRAL-ROLLBACK','Fail timeline then create referral','500 and no persisted rows/events',dict(status=s,unchanged=before==after),s==500 and before==after)
        for round in range(6):
            racecase=self.case('QA expert chronology race '+str(round));racep=self.proceeding(racecase)
            def change(which):
                client=self.sibling()
                if which=='parent':return client.call('PUT','/api/v1/proceedings/'+racep['id'],dict(courtId=self.court['id'],courtCaseNumber=racep['courtCaseNumber'],courtCaseYear=2026,filingDate='2026-01-04',version=0))[0]
                return client.call('POST','/api/v1/proceedings/'+racep['id']+'/expert-referrals',rb)[0]
            statuses=self.parallel([lambda:change('parent'),lambda:change('referral')]);invalid=int(self.sql(f"select count(*) from expert_referral r join proceeding p on p.id=r.proceeding_id where p.id='{racep['id']}' and r.referral_date<p.filing_date").stdout)
            self.oracle('REFERRAL-CHRONOLOGY-RACE-'+str(round),'Race parent filing correction with earlier referral creation','One200/422 or422/201; no invalid persisted pair',dict(statuses=statuses,invalid_rows=invalid),statuses in [[200,422],[422,201]] and invalid==0)
        cancelled=self.case('QA cancelled expert history');cancelp=self.proceeding(cancelled)
        cr=self.req('REFERRAL-CANCELLED-FIXTURE','POST','/api/v1/proceedings/'+cancelp['id']+'/expert-referrals',rb,201)
        self.req('REFERRAL-CANCELLED-RESULT','POST','/api/v1/expert-referrals/'+cr['id']+'/cancel',dict(version=0))
        before=self.sql(f"select (select row_to_json(r)::text from expert_referral r where id='{cr['id']}'),(select json_agg(e order by e.id)::text from case_event e where case_id='{cancelled['id']}')").stdout
        self.req('REFERRAL-CANCELLED-PARENT','PUT','/api/v1/proceedings/'+cancelp['id'],dict(courtId=self.court['id'],courtCaseNumber=cancelp['courtCaseNumber'],courtCaseYear=2026,filingDate='2026-01-04',version=0),422)
        after=self.sql(f"select (select row_to_json(r)::text from expert_referral r where id='{cr['id']}'),(select json_agg(e order by e.id)::text from case_event e where case_id='{cancelled['id']}')").stdout
        self.oracle('REFERRAL-CANCELLED-HISTORY','Attempt to invalidate cancelled referral date','Referral and history unchanged',dict(unchanged=before==after),before==after)
        e=self.req('EXPERT-DEACTIVATE','PUT','/api/v1/experts/'+e['id'],dict(name=e['name'],specialty=e['specialty'],active=False,version=e['version']))
        self.req('EXPERT-INACTIVE-REFERRAL','POST',url,rb,422,rule='EXPERT_INACTIVE')
        events=self.sql("select string_agg(summary,'|') from case_event where type like 'EXPERT_%'").stdout;self.oracle('EXPERT-TIMELINE-PRIVACY','Inspect expert timeline summaries','Mission/session notes absent',{'private_text':'QA private' in events},'QA private' not in events)

if __name__=='__main__':
 p=argparse.ArgumentParser()
 for name in ['candidate','sha','output']:p.add_argument('--'+name,required=True)
 p.add_argument('--kind',choices=['documents','experts','notes'],required=True);p.add_argument('--browser',action='store_true')
 raise SystemExit(OperationReview(p.parse_args()).execute())
