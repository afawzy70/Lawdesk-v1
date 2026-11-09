"""Real Flyway V405->407 replay over synthetic existing rows in disposable PostgreSQL.
The initial QA lifecycle migrates latest. Only in this disposable database, remove
exact V406/V407 objects/history to reconstruct V405 before running real Flyway.
"""
import argparse,subprocess,tempfile,zipfile,re
from pathlib import Path
from phase56_review import PhaseReview
from phase234_review import JAVA

class Upgrade(PhaseReview):
    def hearing_chronology(self):
        c=self.case();p=self.proceeding(c)
        def post(path,body):
            status,b,_=self.admin.call('POST',path,body);assert status==201;return b
        h=post('/api/v1/proceedings/'+p['id']+'/hearings',{'hearingDate':'2026-01-03','purpose':'PLEADING'})
        e=post('/api/v1/experts',{'name':'QA migration expert','specialty':'QA'})
        r=post('/api/v1/proceedings/'+p['id']+'/expert-referrals',{'expertId':e['id'],'referralDate':'2026-01-03'})
        post('/api/v1/expert-referrals/'+r['id']+'/sessions',{'sessionDate':'2026-01-04'})
        def snapshot():return self.sql("select (select json_agg(x order by id) from proceeding x),(select json_agg(x order by id) from hearing x),(select json_agg(x order by id) from expert_referral x),(select json_agg(x order by id) from expert_session x),(select json_agg(x order by id) from case_event x)").stdout
        before=snapshot()
        baseline=self.args.baseline
        if baseline=='407':
            migrations=Path(self.args.candidate)/'backend/src/main/resources/db/migration'
            for version in ['406','407']:
                text=next(migrations.glob('V'+version+'__*.sql')).read_text()
                for function in re.findall(r'create (?:or replace )?function .*?\$\$;',text,re.S):
                    self.sql(function.replace('create function','create or replace function',1))
            self.sql("delete from flyway_schema_history where version='408'")
            expected_migrations=1
        else:
            self.sql("drop function hearing_dates_are_consistent() cascade;drop function proceeding_keeps_dependent_dates() cascade;drop function expert_referral_dates_are_consistent() cascade;drop function expert_session_follows_referral() cascade;delete from flyway_schema_history where version in ('406','407','408')")
            expected_migrations=3 if list((Path(self.args.candidate)/'backend/src/main/resources/db/migration').glob('V408__*'))else 2
        head=self.sql('select version from flyway_schema_history order by installed_rank desc limit 1').stdout
        self.oracle('UPGRADE-V'+baseline+'-PRECONDITION','Reconstruct V'+baseline+' definitions/history in disposable DB; preserve populated rows','V'+baseline+' and unchanged valid existing records',{'version':head,'unchanged':snapshot()==before},head==baseline and snapshot()==before)
        with tempfile.TemporaryDirectory(prefix='lawdesk-qa-flyway-')as temp:
            root=Path(temp);libs=root/'lib';libs.mkdir()
            with zipfile.ZipFile(Path(self.args.candidate)/'backend/target/lawdesk-backend-0.0.1-SNAPSHOT.jar')as jar:
                for name in jar.namelist():
                    if name.startswith('BOOT-INF/lib/')and name.endswith('.jar'):(libs/Path(name).name).write_bytes(jar.read(name))
            source=root/'UpgradeProbe.java';source.write_text('import org.flywaydb.core.Flyway; public class UpgradeProbe { public static void main(String[] a) { var f=Flyway.configure().dataSource(a[0],"lawdesk_qa","").locations("filesystem:"+a[1]).load();var r=f.migrate();System.out.println("QA_MIGRATIONS="+r.migrationsExecuted); } }')
            classpath=str(libs/'*');subprocess.run([str(Path(JAVA).with_name('javac')),'-cp',classpath,str(source)],check=True,capture_output=True)
            result=subprocess.run([JAVA,'-cp',str(root)+':'+classpath,'UpgradeProbe',f'jdbc:postgresql://127.0.0.1:{self.dbport}/postgres',str(Path(self.args.candidate)/'backend/src/main/resources/db/migration')],capture_output=True,text=True,timeout=60)
            (self.output/'flyway-upgrade.txt').write_text(result.stdout+result.stderr)
        self.oracle('UPGRADE-REAL-FLYWAY','Run real packaged Flyway with canonical migration SQL over V'+baseline+' state',str(expected_migrations)+' migration(s) applied; all rows/history unchanged',{'exit_code':result.returncode,'expected_migrations':expected_migrations,'actual_matches':('QA_MIGRATIONS='+str(expected_migrations))in result.stdout,'unchanged':snapshot()==before},result.returncode==0 and ('QA_MIGRATIONS='+str(expected_migrations))in result.stdout and snapshot()==before)
        for name,sql in [('HEARING',f"update hearing set hearing_date='2026-01-01' where id='{h['id']}'"),('REFERRAL',f"update expert_referral set referral_date='2026-01-01' where id='{r['id']}'")]:
            outcome=self.sql('begin;'+sql+';rollback;',fail=True)
            self.oracle('UPGRADE-GUARD-'+name,'After migration, attempt incompatible new write','Reject; pre-existing records preserved',{'rejected':outcome.returncode!=0,'unchanged':snapshot()==before},outcome.returncode!=0 and snapshot()==before)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.add_argument('--baseline',choices=['405','407'],default='405')
    p.set_defaults(kind='hearing-chronology',browser=False)
    raise SystemExit(Upgrade(p.parse_args()).execute())
