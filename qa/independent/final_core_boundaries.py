"""Remaining independent case acceptance boundaries, disposable PostgreSQL only."""
import argparse
from urllib.parse import quote
from phase56_review import PhaseReview

class Core(PhaseReview):
    def notes(self):
        def totals():return self.sql('select (select count(*) from legal_case),(select count(*) from case_event)').stdout
        for field,length,expected in [('title',200,201),('title',201,400),('subject',4000,201),('subject',4001,400)]:
            body=dict(title='QA boundary',caseType='CIVIL',openedOn='2026-01-01');body[field]='ع'*length
            before=totals();s,data,_=self.admin.call('POST','/api/v1/cases',body);after=totals()
            ok=s==expected and (len(data[field])==length if expected==201 else before==after)
            self.check('CASE-BOUNDARY-'+field+'-'+str(length),'LD-012,LD-013','Create case at/above field limit → compare persisted state and event counts',str(expected)+'; exact accepted length or no mutation',{'status':s,'input_length':length,'unchanged':before==after},ok)
        c=self.case('QA core');path='/api/v1/cases/'+c['id']
        n=int(self.sql(f"select count(*) from case_event where case_id='{c['id']}'").stdout)
        s,data,_=self.admin.call('PUT',path,dict(title=c['title'],caseType=c['caseType'],openedOn=c['openedOn'],subject='   ',version=c['version']))
        self.check('CASE-NOOP-NULL-NORMALIZATION','LD-013,LD-014','PUT identical fields with whitespace optional subject → read row/events','200; null subject; same version and no extra event',{'status':s,'subject':data.get('subject'),'version':data.get('version'),'events':self.sql(f"select count(*) from case_event where case_id='{c['id']}'").stdout},s==200 and data['subject'] is None and data['version']==c['version'] and int(self.sql(f"select count(*) from case_event where case_id='{c['id']}'").stdout)==n)
        for target in ['CLOSED','ARCHIVED']:
            c=self.admin.call('POST',path+'/status',dict(status=target,version=c['version']))[1];before=totals()
            s=self.admin.call('PUT',path,dict(title='QA prohibited',caseType=c['caseType'],openedOn=c['openedOn'],version=c['version']))[0]
            now=self.admin.call('GET',path)[1]
            self.check('CASE-READONLY-'+target,'LD-013,LD-017','Transition owned case to '+target+' → attempt current-version edit','422; persisted fields/version and history unchanged',{'status':s,'unchanged':now==c and before==totals()},s==422 and now==c and before==totals())
        c=self.case('QA timeline boundary');path='/api/v1/cases/'+c['id']
        self.sql(f"insert into case_event(case_id,type,summary,occurred_at,created_by) select '{c['id']}','QA_BOUNDARY','QA event '||n,timestamptz '2026-01-02T00:00:00Z',u.id from generate_series(1,101)n,app_user u where u.username='qaadmin'")
        pages=[self.admin.call('GET',path+'/timeline?page='+str(i)+'&size=100')[1]for i in [0,1,0,1]]
        ids=[[r['id']for r in p['items']]for p in pages];expected=set(self.sql(f"select id from case_event where case_id='{c['id']}'").stdout.splitlines())
        self.check('TIMELINE-STABLE-PAGING-102','LD-014','Read 102 events including101 equal timestamps via pages0/1 twice → compare SQL IDs','Stable order; exact union; no duplicate or lost records',{'totals':[p['totalItems']for p in pages],'sizes':[len(i)for i in ids],'stable':ids[:2]==ids[2:],'exact_ids':set(ids[0]+ids[1])==expected,'disjoint':not set(ids[0])&set(ids[1])},ids[:2]==ids[2:] and set(ids[0]+ids[1])==expected and not set(ids[0])&set(ids[1]))
        for marker in ['%','_']:
            owned=[self.case('QA literal '+marker+' عنوان'),self.case('QA literal other عنوان')]
            data=self.admin.call('GET','/api/v1/cases?q='+quote('QA literal '+marker,safe=''))[1]
            self.check('CASE-LITERAL-SEARCH-'+str(ord(marker)),'LD-015','Search literal wildcard character through real API','Only literal title matched',{'total':data['totalItems'],'ids':[x['id']for x in data['items']]},data['totalItems']==1 and data['items'][0]['id']==owned[0]['id'])
        tie=[self.case('QA ties')for _ in range(3)]
        pages=[self.admin.call('GET','/api/v1/cases?q=QA%20ties&sort=title,asc&size=1&page='+str(i))[1]for i in [0,1,2,0,1,2]]
        ids=[p['items'][0]['id']for p in pages]
        self.check('CASE-SORT-TIES','LD-015','Page identical title sort keys twice','Stable id tie-break; exact set',{'stable':ids[:3]==ids[3:],'unique':len(set(ids[:3]))},ids[:3]==ids[3:] and set(ids[:3])=={x['id']for x in tie})
        s,party,_=self.admin.call('POST','/api/v1/parties',dict(kind='PERSON',name='QA owned party',nationalId='29901010000001'));assert s==201
        for label,body,status in [('DUPLICATE',dict(kind='PERSON',name='QA duplicate',nationalId='29901010000001'),409),('ORGANIZATION',dict(kind='ORGANIZATION',name='QA mismatch',nationalId='29901010000002'),422),('PERSON',dict(kind='PERSON',name='QA mismatch',registrationNo='QA-REG'),422)]:
            before=self.sql('select count(*) from party').stdout
            s=self.admin.call('POST','/api/v1/parties',body)[0];after=self.sql('select count(*) from party').stdout
            self.check('PARTY-INTEGRITY-'+label,'LD-016','Attempt duplicate/mismatched party → compare stored count',str(status)+'; no inserted party',{'status':s,'unchanged':before==after},s==status and before==after)
        c=self.case('QA archived unlink');path='/api/v1/cases/'+c['id'];assert self.admin.call('POST',path+'/parties',dict(partyId=party['id'],role='CLIENT'))[0]==201
        for state in ['CLOSED','ARCHIVED']:c=self.admin.call('POST',path+'/status',dict(status=state,version=c['version']))[1]
        before=totals();s=self.admin.call('DELETE',path+'/parties/'+party['id'])[0];rows=self.admin.call('GET',path+'/parties')[1]
        self.check('ARCHIVED-PARTY-UNLINK-ROLLBACK','LD-014,LD-016','Attempt unlink from archived case → inspect relationship/history','422; relationship and history retained',{'status':s,'relationships':len(rows),'history_unchanged':before==totals()},s==422 and len(rows)==1 and before==totals())
        for length,status in [(501,400),(500,200)]:
            c=self.case('QA reopening','CLOSED');path='/api/v1/cases/'+c['id'];before=totals()
            s,data,_=self.admin.call('POST',path+'/status',dict(status='ACTIVE',reason='ع'*length,version=c['version']))
            self.check('REOPEN-REASON-'+str(length),'LD-017','Reopen CLOSED with boundary reason → read committed status/history',str(status)+'; reject oversized reason atomically',{'status':s,'unchanged':before==totals()},s==status and (data.get('status')=='ACTIVE' if status==200 else before==totals()))

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(Core(p.parse_args()).execute())
