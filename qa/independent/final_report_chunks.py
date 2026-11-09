"""Independent >10000 case summary oracle for the changed report batching path.
Only disposable local PostgreSQL; no shared UAT bulk inserts.
"""
import argparse
from phase56_operations import OperationReview

class ReportChunks(OperationReview):
    def notes(self):
        actor=self.sql("select id from app_user where username='qaadmin'").stdout
        self.sql(f"insert into legal_case(reference_no,title,case_type,opened_on,created_by,updated_by) select 'QA-CHUNK-'||g,'QA chunk','CIVIL','2026-01-01','{actor}','{actor}' from generate_series(1,10001) g")
        self.sql(f"insert into proceeding(case_id,stage,court_id,court_case_number,court_case_year,filing_date,created_by,updated_by) select id,'FIRST_INSTANCE','{self.court['id']}',reference_no,2026,'2026-01-02','{actor}','{actor}' from legal_case where reference_no like 'QA-CHUNK-%' and split_part(reference_no,'-',3)::int<=5001")
        for label,query,total,first,none in [('ALL','?q=QA-CHUNK-',10001,5001,5000),('STAGE','?q=QA-CHUNK-&stage=FIRST_INSTANCE',5001,5001,0),('COURT','?q=QA-CHUNK-&courtId='+self.court['id'],5001,5001,0)]:
            status,body,_=self.admin.call('GET','/api/v1/reports/cases'+query+'&size=1')
            summary=body.get('summary',{});stages=summary.get('byCurrentStage',summary.get('byStage',{}))
            self.check('CASE-CHUNKS-'+label,'LD-031','Create10001 owned local cases;5001 with proceedings -> request summary '+label,'200; exact total/stage/NONE across port chunk boundary',{'status':status,'summary':summary,'totalItems':body.get('totalItems')},status==200 and body.get('totalItems')==total and summary.get('total')==total and stages.get('FIRST_INSTANCE')==first and stages.get('NONE')==none)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(ReportChunks(p.parse_args()).execute())
