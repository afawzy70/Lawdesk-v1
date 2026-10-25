"""Boundary validation for each NEW report's row guard; disposable PostgreSQL only."""
import argparse,csv,io,json,time
from phase56_operations import OperationReview

class ExportLimits(OperationReview):
    def notes(self):
        c=self.case();p=self.proceeding(c);actor=self.sql("select id from app_user where username='qaadmin'").stdout
        inserts={
            'hearings':f"insert into hearing(case_id,proceeding_id,hearing_date,purpose,status,result_recorded_at,result_recorded_by,created_by,updated_by) select '{c['id']}','{p['id']}','2026-01-03','PLEADING','CANCELLED',now(),'{actor}','{actor}','{actor}' from generate_series(1,{{count}})",
            'workload':f"insert into task(title,assignee_id,created_by,updated_by) select 'QA export cap',gen_random_uuid(),'{actor}','{actor}' from generate_series(1,{{count}})",
            'experts':f"insert into expert(name,specialty,created_by,updated_by) select 'QA export cap '||g,'QA','{actor}','{actor}' from generate_series(1,{{count}}) g"}
        for report,statement in inserts.items():
            self.sql(statement.format(count=10000))
            base='/api/v1/reports/'+report+'/export?from=2026-01-01&to=2026-01-31&format='
            started=time.monotonic();status,body,headers=self.raw(self.admin,'GET',base+'csv');elapsed=time.monotonic()-started
            rows=len(list(csv.reader(io.StringIO(body.decode('utf-8-sig')))))-1 if status==200 else None
            self.check('NEW-EXPORT-CAP10000-'+report,'LD-032,LD-033,LD-034','Insert exactly10000 local report rows -> requestCSV -> independently parse','200 and10000data rows',{'status':status,'rows':rows,'elapsed_seconds':round(elapsed,3)},status==200 and rows==10000)
            self.sql(statement.format(count=1));before=self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout
            for fmt in ['csv','xlsx','pdf']:
                s,b,h=self.raw(self.admin,'GET',base+fmt)
                try:rule=json.loads(b).get('rule')
                except ValueError:rule=None
                self.check('NEW-EXPORT-CAP10001-'+report+'-'+fmt,'LD-032,LD-033,LD-034','Add10001st record -> request '+fmt,'422 EXPORT_TOO_LARGE; no attachment',{'status':s,'rule':rule,'attachment':h.get('content-disposition')},s==422 and rule=='EXPORT_TOO_LARGE' and not h.get('content-disposition'))
            after=self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout
            self.check('NEW-EXPORT-CAP-NO-AUDIT-'+report,'LD-011,LD-034','Compare export audit count around three refused exports','No successful-export audit on refusal',{'before':before,'after':after},before==after)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(ExportLimits(p.parse_args()).execute())
