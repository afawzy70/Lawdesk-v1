"""QAD038 independent lock/wait/COMMIT/deadlock verification. Disposable PG ONLY."""
import argparse,concurrent.futures,time
import pg8000.dbapi
from phase5_database_acceptance import DatabaseAcceptance

class FinalIntegrity(DatabaseAcceptance):
    def connect(self):
        c=pg8000.dbapi.connect(user='qa_integrity_writer',host='127.0.0.1',port=self.dbport,database='postgres')
        c.cursor().execute("set statement_timeout='8000ms'");c.commit();return c

    def command(self,c,sql):
        try:c.cursor().execute(sql);c.commit();return 'COMMITTED'
        except pg8000.dbapi.DatabaseError as e:c.rollback();return e.args[0].get('C')

    def wait_for_lock(self,pid):
        end=time.monotonic()+3
        while time.monotonic()<end:
            if self.sql(f"select coalesce(wait_event_type,'') from pg_stat_activity where pid={pid}").stdout=='Lock':return True
            time.sleep(.025)
        return False

    def serialized(self,name,first_sql,second_sql,invalid_sql,history_sql):
        a,b=self.connect(),self.connect();before=self.sql(history_sql).stdout
        cur=b.cursor();cur.execute('select pg_backend_pid()');pid=cur.fetchone()[0];b.commit()
        try:
            a.cursor().execute(first_sql)
            with concurrent.futures.ThreadPoolExecutor(max_workers=1)as pool:
                f=pool.submit(self.command,b,second_sql);waited=self.wait_for_lock(pid);a.commit();outcome=f.result(timeout=10)
            invalid=int(self.sql(invalid_sql).stdout)
            self.check(name,'LD-019,LD-024,LD-025',first_sql+'; do not commit -> concurrent '+second_sql+' -> observe pg_stat_activity Lock -> first COMMIT -> second completes -> inspect rows/history','Second waits then23514; first committed; zero invalid pairs; history unchanged',{'observed_lock_wait':waited,'second_result':outcome,'invalid_rows':invalid,'history_unchanged':before==self.sql(history_sql).stdout},waited and outcome=='23514' and invalid==0 and before==self.sql(history_sql).stdout)
        finally:a.rollback();b.rollback();a.close();b.close()

    def chain(self):
        c=self.case('QA V408 chain');p=self.proceeding(c)
        s,h,_=self.admin.call('POST','/api/v1/proceedings/'+p['id']+'/hearings',{'hearingDate':'2026-01-03','purpose':'PLEADING'});assert s==201
        s,h,_=self.admin.call('POST','/api/v1/hearings/'+h['id']+'/result',{'status':'ADJOURNED','version':0,'nextHearingDate':'2026-01-08'});assert s==200
        return c,p,h,h['nextHearingId']

    def hearing_chronology(self):
        super().hearing_chronology()
        for first in ['parent','successor']:
            c,p,h,ch=self.chain();a=f"update hearing set hearing_date='2026-01-06' where id='{h['id']}'";b=f"update hearing set hearing_date='2026-01-05' where id='{ch}'"
            self.serialized('V408-HEARING-'+first.upper()+'-FIRST',a if first=='parent'else b,b if first=='parent'else a,f"select count(*) from hearing p join hearing c on c.adjourned_from_id=p.id where p.id='{h['id']}' and c.hearing_date<=p.hearing_date",f"select json_agg(e order by id) from case_event e where case_id='{c['id']}'")
        s,e,_=self.admin.call('POST','/api/v1/experts',{'name':'QA V408 referral','specialty':'QA'});assert s==201
        for first in ['referral','session']:
            c=self.case('QA V408 referral race');p=self.proceeding(c)
            s,r,_=self.admin.call('POST','/api/v1/proceedings/'+p['id']+'/expert-referrals',{'expertId':e['id'],'referralDate':'2026-01-03'});assert s==201
            s,r,_=self.admin.call('POST','/api/v1/expert-referrals/'+r['id']+'/sessions',{'sessionDate':'2026-01-08'});assert s==201
            session=r['sessions'][0]['id'];a=f"update expert_referral set referral_date='2026-01-06' where id='{r['id']}'";b=f"update expert_session set session_date='2026-01-05' where id='{session}'"
            self.serialized('V408-EXPERT-'+first.upper()+'-FIRST',a if first=='referral'else b,b if first=='referral'else a,f"select count(*) from expert_session s join expert_referral r on r.id=s.referral_id where r.id='{r['id']}' and s.session_date<r.referral_date",f"select json_agg(e order by id) from case_event e where case_id='{c['id']}'")
        c,p,h,ch=self.chain();a,b=self.connect(),self.connect();mark='QA tentative loser marker'
        try:
            a.cursor().execute(f"select id from hearing where id='{ch}' for update")
            a.cursor().execute(f"update hearing set notes='{mark}' where id='{ch}'")
            b.cursor().execute(f"select id from proceeding where id='{p['id']}' for no key update")
            b.cursor().execute(f"update proceeding set court_case_number='{mark}' where id='{p['id']}'")
            with concurrent.futures.ThreadPoolExecutor(max_workers=2)as pool:
                fa=pool.submit(self.command,a,f"update hearing set hearing_date='2026-01-06' where id='{ch}'")
                fb=pool.submit(self.command,b,f"update hearing set hearing_date='2026-01-07' where id='{ch}'")
                outcomes=[fa.result(timeout=12),fb.result(timeout=12)]
            notes=self.sql(f"select coalesce(notes,'') from hearing where id='{ch}'").stdout;number=self.sql(f"select court_case_number from proceeding where id='{p['id']}'").stdout
            rollback=(notes!=mark if outcomes[0]=='40P01'else number!=mark)
            invalid=int(self.sql(f"select count(*) from hearing p join hearing c on c.adjourned_from_id=p.id where p.id='{h['id']}' and c.hearing_date<=p.hearing_date").stdout)
            self.check('V408-DEADLOCK-ATOMICITY','LD-019,LD-024','A locks child and changes note -> B locks proceeding and changes number -> A waits for proceeding while B waits for child -> inspect deadlock victim and all writes','One40P01, oneCOMMITTED; victim earlier write rolled back; no invalid chronology',{'outcomes':outcomes,'victim_prior_write_rolled_back':rollback,'invalid_pairs':invalid},sorted(outcomes)==['40P01','COMMITTED']and rollback and invalid==0)
        finally:a.rollback();b.rollback();a.close();b.close()
        # Record an explicit preservation check on the migration now actually installed.
        migration=self.sql("select success from flyway_schema_history where version='408'").stdout
        self.check('V408-INSTALLED','LD-006,LD-024','Inspect actual migrated PostgreSQL history','V408 success',migration,migration=='t')

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='hearing-chronology',browser=False)
    raise SystemExit(FinalIntegrity(p.parse_args()).execute())
