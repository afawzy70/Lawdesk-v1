"""Bounded report/writer overlap in disposable PostgreSQL; no shared-host load."""
import argparse,threading,time
from phase56_operations import OperationReview

class Snapshot(OperationReview):
    def notes(self):
        actor=self.sql("select id from app_user where username='qaadmin'").stdout
        self.sql(f"insert into legal_case(reference_no,title,case_type,opened_on,created_by,updated_by) select 'QA-SNAPSHOT-'||g,'QA snapshot','CIVIL','2026-01-01','{actor}','{actor}' from generate_series(1,20) g")
        stop=threading.Event();commits=[];errors=[]
        def writer():
            for i in range(60):
                if stop.is_set():break
                try:
                    self.sql("update legal_case set status='"+('ACTIVE'if i%2 else 'OPEN')+"' where reference_no like 'QA-SNAPSHOT-%'")
                    commits.append(time.monotonic())
                except Exception as e:errors.append(type(e).__name__);break
                time.sleep(.05)
        thread=threading.Thread(target=writer);thread.start();observations=[]
        try:
            for _ in range(10):
                start=time.monotonic();s,b,_=self.admin.call('GET','/api/v1/reports/cases?q=QA-SNAPSHOT-&size=100');end=time.monotonic()
                statuses={r['status']for r in b.get('items',[])};summary=b.get('summary',{})
                consistent=s==200 and len(b.get('items',[]))==20 and len(statuses)==1 and summary.get('total')==20 and summary.get('byStatus',{}).get(next(iter(statuses),''))==20
                observations.append({'status':s,'consistent':consistent,'writer_commit_during_request':any(start<=t<=end for t in commits)})
        finally:stop.set();thread.join(timeout=10)
        self.check('REPORT-SNAPSHOT-WRITER-RACE','LD-031','20 synthetic cases -> atomically toggle all statuses in concurrent local transactions -> read ten complete report pages/summaries','Every request returns one internally consistent committed status snapshot; observed write/read overlap; no worker errors',{'observations':observations,'writer_commits':len(commits),'worker_errors':errors},not errors and all(x['consistent']for x in observations)and any(x['writer_commit_during_request']for x in observations))

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(Snapshot(p.parse_args()).execute())
