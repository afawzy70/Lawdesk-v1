"""QAD039: exact 9999/10000/10001 expert records, real CSV/XLSX/PDF consumers.
All bulk fixtures are confined to QA's disposable database, never shared UAT.
"""
import argparse,csv,io,json,hashlib,re,time
import openpyxl,fitz
from phase56_operations import OperationReview

class ExpertExports(OperationReview):
    def notes(self):
        actor=self.sql("select id from app_user where username='qaadmin'").stdout
        def insert(start,end):self.sql(f"insert into expert(name,specialty,created_by,updated_by) select 'QA-FINAL-EXP-'||lpad(g::text,5,'0'),'QA محاسبة','{actor}','{actor}' from generate_series({start},{end})g")
        insert(1,9999);artifacts=self.output/'artifacts';artifacts.mkdir()
        for count in [9999,10000,10001]:
            if count>9999:insert(count,count)
            expected={f'QA-FINAL-EXP-{n:05}'for n in range(1,count+1)}
            before=int(self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout)
            for fmt in ['csv','xlsx','pdf']:
                path='/api/v1/reports/experts/export?format='+fmt+'&lang=ar'
                start=time.monotonic();status,body,headers=self.raw(self.admin,'GET',path);elapsed=round(time.monotonic()-start,3)
                if count>10000:
                    try:data=json.loads(body)
                    except ValueError:data={}
                    safe=not any(x in body for x in [b'IllegalArgumentException',b'com.lawdesk',b'stackTrace',b'jdbc:'])
                    self.check('QAD039-10001-'+fmt,'LD-033,LD-034','Insert10001 synthetic experts -> authorized Arabic '+fmt+' export -> inspect error/headers','422 EXPORT_TOO_LARGE; no attachment or sensitive exception details',{'status':status,'rule':data.get('rule'),'no_sensitive_details':safe,'attachment':headers.get('content-disposition'),'elapsed_seconds':elapsed},status==422 and data.get('rule')=='EXPORT_TOO_LARGE'and not headers.get('content-disposition')and safe)
                    continue
                file=artifacts/f'experts-{count}.{fmt}'
                if status==200:file.write_bytes(body)
                self.check(f'QAD039-{count}-{fmt}-RESPONSE','LD-033,LD-034',f'Exactly{count} synthetic experts -> authorized Arabic export','200 attachment with correct format',{'status':status,'bytes':len(body),'sha256':hashlib.sha256(body).hexdigest(),'elapsed_seconds':elapsed},status==200 and bool(headers.get('content-disposition')))
                if status!=200:continue
                if fmt=='csv':rows=list(csv.reader(io.StringIO(body.decode('utf-8-sig'))));names=[r[0]for r in rows[1:]];more={}
                elif fmt=='xlsx':
                    sheet=openpyxl.load_workbook(io.BytesIO(body),read_only=True).active;rows=list(sheet.values);names=[r[0]for r in rows[1:]];more={'streaming_rows':len(rows),'rtl':bool(openpyxl.load_workbook(io.BytesIO(body)).active.sheet_view.rightToLeft)}
                else:
                    pdf=fitz.open(stream=body,filetype='pdf');text=''.join(p.get_text()for p in pdf);names=re.findall(r'QA-FINAL-EXP-\d{5}',''.join(text.split()));more={'pages':len(pdf)};pdf[0].get_pixmap().save(str(artifacts/f'experts-{count}-page1.png'));pdf.close()
                self.check(f'QAD039-{count}-{fmt}-CONTENT','LD-033,LD-034','Independently parse/render actual export -> compare every synthetic identifier','Exact row count and complete unique name set',{'rows':len(names),'unique':len(set(names)),'missing':len(expected-set(names)),'unexpected':len(set(names)-expected),**more},len(names)==count and set(names)==expected and (fmt!='xlsx'or more['rtl']))
            delta=int(self.sql("select count(*) from audit_event where action='REPORT_EXPORT'").stdout)-before
            self.check('QAD039-AUDIT-'+str(count),'LD-011,LD-034','Compare audit rows around three export requests','3 successful exports audited, or0 if overlimit',delta,delta==(0 if count>10000 else 3))
        status,report,_=self.admin.call('GET','/api/v1/reports/experts')
        self.check('QAD039-SCREEN-10001','LD-033','Read on-screen expert report with10001 registry entries','200; all10001 names mapped in chunks; no500',{'status':status,'rows':len(report.get('items',[]))if isinstance(report,dict)else None},status==200 and len(report['items'])==10001 and {r['name']for r in report['items']}==expected)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ['candidate','sha','output']:p.add_argument('--'+n,required=True)
    p.set_defaults(kind='notes',browser=False)
    raise SystemExit(ExpertExports(p.parse_args()).execute())
