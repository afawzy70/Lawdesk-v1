"""Additional V406/V407 independent probes; disposable database ONLY.
Tests committed SQL writes with a non-owner, including an adjournment write skew.
"""
import argparse
import concurrent.futures
import pg8000.dbapi
from phase56_review import PhaseReview


class DatabaseAcceptance(PhaseReview):
    def hearing_chronology(self):
        self.sql('create role qa_integrity_writer login nosuperuser nocreatedb nocreaterole; grant usage on schema public to qa_integrity_writer; grant select,update on proceeding,hearing,expert_referral,expert_session to qa_integrity_writer')
        def connect():
            cn=pg8000.dbapi.connect(user='qa_integrity_writer',host='127.0.0.1',port=self.dbport,database='postgres')
            cn.cursor().execute("set statement_timeout='3000ms'");cn.commit();return cn
        c=self.case();p=self.proceeding(c)
        s,e,_=self.admin.call('POST','/api/v1/experts',{'name':'QA SQL expert','specialty':'QA'});assert s==201
        s,r,_=self.admin.call('POST','/api/v1/proceedings/'+p['id']+'/expert-referrals',{'expertId':e['id'],'referralDate':'2026-01-03','reportDueDate':'2026-01-09'});assert s==201
        s,r,_=self.admin.call('POST','/api/v1/expert-referrals/'+r['id']+'/sessions',{'sessionDate':'2026-01-05'});assert s==201
        session=r['sessions'][0]
        snapshot=lambda:self.sql("select (select json_agg(x) from proceeding x),(select json_agg(x) from expert_referral x),(select json_agg(x) from expert_session x),(select json_agg(x) from case_event x)").stdout
        for name,sql in [
            ('REFERRAL-BEFORE-FILING',f"update expert_referral set referral_date='2026-01-01' where id='{r['id']}'"),
            ('FILING-AFTER-REFERRAL',f"update proceeding set filing_date='2026-01-04' where id='{p['id']}'"),
            ('SESSION-BEFORE-REFERRAL',f"update expert_session set session_date='2026-01-02' where id='{session['id']}'"),
            ('REFERRAL-AFTER-SESSION',f"update expert_referral set referral_date='2026-01-06' where id='{r['id']}'")]:
            before=snapshot();cn=connect();rejected=False;code=None
            try:cn.cursor().execute(sql);cn.commit()
            except pg8000.dbapi.DatabaseError as ex:
                rejected=True;code=ex.args[0].get('C');cn.rollback()
            finally:cn.close()
            self.check('V407-COMMIT-'+name,'LD-025,LD-019',sql+' -> COMMIT -> Compare all relevant rows and timeline','SQLSTATE23514 and unchanged rows/history',{'rejected':rejected,'sqlstate':code,'unchanged':before==snapshot()},rejected and code=='23514' and before==snapshot())
        # Sequential counterexample is refused first, then two ordinary READ COMMITTED
        # transactions make individually valid edits against different old snapshots.
        cc=self.case('QA adjournment database race');pr=self.proceeding(cc)
        s,h,_=self.admin.call('POST','/api/v1/proceedings/'+pr['id']+'/hearings',{'hearingDate':'2026-01-03','purpose':'PLEADING'});assert s==201
        s,h,_=self.admin.call('POST','/api/v1/hearings/'+h['id']+'/result',{'status':'ADJOURNED','version':0,'nextHearingDate':'2026-01-06'});assert s==200
        child=h['nextHearingId'];bad=self.sql(f"begin; update hearing set hearing_date='2026-01-07' where id='{h['id']}'; rollback",fail=True)
        self.check('V406-ADJOURNMENT-SEQUENTIAL','LD-024','ParentJan3 childJan6 -> set parentJan7','Database rejects reversed chain',{'rejected':bad.returncode!=0},bad.returncode!=0)
        first,second=connect(),connect();status={};blocked=False
        try:
            first.cursor().execute(f"update hearing set hearing_date='2026-01-05' where id='{h['id']}'")
            # This statement may block on a correct implementation. The bounded timeout
            # is recorded as a safe serialization, not treated as invalid data acceptance.
            try:
                second.cursor().execute(f"update hearing set hearing_date='2026-01-04' where id='{child}'")
                second.commit();status['child']='committed'
            except pg8000.dbapi.DatabaseError as ex:
                status['child']=ex.args[0].get('C');second.rollback();blocked=True
            first.commit();status['parent']='committed'
        finally:first.close();second.close()
        dates=self.sql(f"select a.hearing_date,b.hearing_date from hearing a join hearing b on b.adjourned_from_id=a.id where a.id='{h['id']}'").stdout
        invalid=int(self.sql(f"select count(*) from hearing a join hearing b on b.adjourned_from_id=a.id where a.id='{h['id']}' and b.hearing_date<=a.hearing_date").stdout)
        self.check('V406-ADJOURNMENT-CONCURRENT-COMMIT','LD-024','T1 BEGIN; parentJan3->Jan5 (do not commit) -> T2 BEGIN; childJan6->Jan4; COMMIT -> T1 COMMIT -> fresh joined SELECT','No committed child on/before parent; refuse or serialize competing changes',{'transactions':status,'dates':dates,'invalid_pairs':invalid},invalid==0)
        versions=self.sql("select version,success from flyway_schema_history where version in ('400','401','402','403','404','405','406','407') order by installed_rank").stdout
        self.check('V400-V407-MIGRATION-HISTORY','LD-006,LD-024,LD-025','Start real candidate with clean PostgreSQL -> inspect Flyway history','Eight successful Phase5 migrations',versions,len(versions.splitlines())==8 and all(x.endswith('\tt')for x in versions.splitlines()))


if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='hearing-chronology',browser=False)
    raise SystemExit(DatabaseAcceptance(p.parse_args()).execute())
