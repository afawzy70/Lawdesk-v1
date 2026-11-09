"""Extra independent concurrency/atomicity oracles for the current integrated case model."""
import argparse
import concurrent.futures
from phase234_review import Review, Client, INITIAL, CHANGED


class Integrity(Review):
    def run(self, admin, appport):
        assert admin.login('qaadmin')[0] == 200
        assert admin.call('POST','/api/v1/me/password',dict(currentPassword=INITIAL,newPassword=CHANGED))[0] == 204
        body=dict(title='QA atomicity',caseType='CIVIL',openedOn='2026-01-01')
        def create(_):
            client=Client(appport);client.cookies=dict(admin.cookies)
            return client.call('POST','/api/v1/cases',body)[:2]
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            created=list(pool.map(create,range(6)))
        refs=[b.get('referenceNo') for s,b in created if s==201]
        self.check('CONCURRENT-REFERENCE-NUMBERS','LD-012','Six parallel generated-reference creates','Six201; distinct consecutive generated references',{'statuses':[s for s,_ in created],'references':refs},len(refs)==6 and len(set(refs))==6 and sorted(int(r.rsplit('-',1)[1]) for r in refs)==list(range(1,7)),priority='P1')
        cid=created[0][1]['id']; path='/api/v1/cases/'+cid
        def update(title):
            c=Client(appport);c.cookies=dict(admin.cookies)
            return c.call('PUT',path,{**body,'title':title,'version':0})[0]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            statuses=list(pool.map(update,['First concurrent edit','Second concurrent edit']))
        self.check('CONCURRENT-OPTIMISTIC-LOCK','LD-013','Two different writes with version0 in parallel','Exactly one200 and one409',statuses,sorted(statuses)==[200,409],priority='P1')
        events=self.sql(f"select count(*) from case_event where case_id='{cid}' and type='CASE_UPDATED'").stdout.strip()
        self.check('CONCURRENT-TIMELINE-ATOMIC','LD-013,LD-014','Query event count after parallel writes','Exactly one CASE_UPDATED event',events,events=='1',priority='P1')
        before=self.sql('select count(*) from legal_case').stdout.strip()
        counter=self.sql('select sum(last_number) from case_reference_counter').stdout.strip()
        self.sql("create function qa_fail_event() returns trigger language plpgsql as $$ begin raise exception 'QA injected event failure'; end; $$; create trigger qa_event_failure before insert on case_event for each row execute function qa_fail_event()")
        status=admin.call('POST','/api/v1/cases',body)[0]
        after=self.sql('select count(*) from legal_case').stdout.strip()
        aftercounter=self.sql('select sum(last_number) from case_reference_counter').stdout.strip()
        self.check('TIMELINE-FAILURE-ROLLBACK','LD-012,LD-014','Inject rejecting timeline trigger in QA-only DB → create case → compare row and counter','Request fails; no case/counter mutation',{'status':status,'case_count_before':before,'case_count_after':after,'counter_before':counter,'counter_after':aftercounter},status>=400 and before==after and counter==aftercounter,priority='P1')
        self.sql('drop trigger qa_event_failure on case_event; drop function qa_fail_event()')
        nextcase=admin.call('POST','/api/v1/cases',body)
        self.check('REFERENCE-RECOVERY-AFTER-ROLLBACK','LD-012','Remove injected trigger → create again','201 with next gapless reference00007',{'status':nextcase[0],'reference':nextcase[1].get('referenceNo')},nextcase[0]==201 and nextcase[1].get('referenceNo','').endswith('-00007'))
        missing='00000000-0000-4000-8000-000000000099'
        r=self.sql(f"insert into case_event(case_id,type,summary,created_by) values('{missing}','QA','QA', '{missing}')",fail=True)
        self.check('SQL-TIMELINE-ORPHAN','LD-014','Insert timeline row for missing case directly','Foreign key rejects orphan',{'exit':r.returncode,'error':r.stderr[:250]},r.returncode!=0)


if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    raise SystemExit(Integrity(p.parse_args()).execute())
