"""Focused QA of the new expert report identity port; actual JDBC/DB, no developer tests."""
import argparse,json,subprocess,tempfile,zipfile
from pathlib import Path
from phase56_operations import OperationReview
from phase234_review import JAVA

class ExpertPortReview(OperationReview):
    def experts(self):
        if self.args.regression:super().experts()
        a=self.req('PORT-ACTIVE-FIXTURE','POST','/api/v1/experts',dict(name="QA خبير %_ ' literal",specialty='QA Engineering',office='QA excluded office',phone='1234567'),201)
        b=self.req('PORT-INACTIVE-FIXTURE','POST','/api/v1/experts',dict(name='QA inactive expert',specialty='QA Accounting'),201)
        self.req('PORT-DEACTIVATE-FIXTURE','PUT','/api/v1/experts/'+b['id'],dict(name=b['name'],specialty=b['specialty'],active=False,version=0))
        snapshot=lambda:self.sql('select json_agg(e order by e.id)::text from expert e').stdout
        before=snapshot()
        with tempfile.TemporaryDirectory(prefix='lawdesk-qa-port-')as tmp:
            root=Path(tmp);jar=Path(self.args.candidate)/'backend/target/lawdesk-backend-0.0.1-SNAPSHOT.jar'
            with zipfile.ZipFile(jar)as archive:
                for name in archive.namelist():
                    if name.startswith(('BOOT-INF/classes/','BOOT-INF/lib/')):archive.extract(name,root)
            cp=str(root/'BOOT-INF/classes')+':'+str(root/'BOOT-INF/lib/*')
            source=Path(__file__).parent/'java/ExpertReadPortProbe.java'
            subprocess.run([str(Path(JAVA).with_name('javac')),'-cp',cp,'-d',str(root),str(source)],check=True,capture_output=True)
            r=subprocess.run([JAVA,'-cp',str(root)+':'+cp,'com.lawdesk.experts.ExpertReadPortProbe',str(self.dbport),a['id'],b['id']],check=True,capture_output=True,text=True)
            rows=json.loads(next(x for x in reversed(r.stdout.splitlines())if x.startswith('[{')))
            for row in rows:self.check('EXPERT-PORT-'+row['id'],'LD-025,LD-033','Call actual candidate ExpertReportQueries.experts with isolated persisted fixtures → inspect returned projection',row['expected'],row['actual'],row['ok'])
        self.check('EXPERT-PORT-READONLY','LD-025,LD-033','Compare complete expert table before/after all port calls','No changed rows',dict(unchanged=before==snapshot()),before==snapshot())

if __name__=='__main__':
 p=argparse.ArgumentParser()
 for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
 p.add_argument('--regression',action='store_true');args=p.parse_args();args.kind='experts';args.browser=False
 raise SystemExit(ExpertPortReview(args).execute())
