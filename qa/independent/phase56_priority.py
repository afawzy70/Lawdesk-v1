"""Focused follow-up only: QAD-032 missing rollback/DB probes and bounded loopback timing.
Reuses existing QA lifecycle. Never targets UAT, containers, or implementation workspaces.
"""
import argparse,json,math,os,threading,time
from pathlib import Path
import pg8000.dbapi
from phase56_review import PhaseReview

class PriorityReview(PhaseReview):
    def hearing_chronology(self):
        if self.args.mode=='performance':return self.performance()
        c=self.case('QA parent rollback');p=self.proceeding(c)
        status,h,_=self.admin.call('POST','/api/v1/proceedings/'+p['id']+'/hearings',dict(hearingDate='2026-01-03',purpose='PLEADING'))
        assert status==201
        def snapshot():return self.sql(f"select row_to_json(p)::text,(select json_agg(h order by h.id)::text from hearing h where h.proceeding_id=p.id),(select json_agg(e order by e.id)::text from case_event e where e.case_id=p.case_id) from proceeding p where p.id='{p['id']}'").stdout
        before=snapshot()
        self.sql("create function qa_parent_write_fail() returns trigger language plpgsql as $$ begin raise exception 'QA injected after-update failure'; end $$; create trigger qa_parent_write_fail after update on proceeding for each row execute function qa_parent_write_fail()")
        try:
            status=self.admin.call('PUT','/api/v1/proceedings/'+p['id'],dict(courtId=self.court['id'],courtCaseNumber=p['courtCaseNumber'],courtCaseYear=2026,filingDate='2026-01-03',version=p['version']))[0]
            self.oracle('PARENT-UPDATE-DB-FAILURE-ROLLBACK','After-update trigger raises in disposable DB; submit otherwise valid same-day filing correction; compare full rows/history','500; complete transaction rollback',dict(status=status,unchanged=before==snapshot()),status==500 and before==snapshot())
        finally:self.sql('drop trigger qa_parent_write_fail on proceeding; drop function qa_parent_write_fail()')
        self.sql('create role qa_limited_writer login nosuperuser nocreatedb nocreaterole; grant usage on schema public to qa_limited_writer; grant select,update on proceeding,hearing to qa_limited_writer')
        flags=self.sql("select rolsuper,rolcreatedb,rolcreaterole from pg_roles where rolname='qa_limited_writer'").stdout
        self.oracle('DB-WRITER-PRIVILEGES','Inspect dedicated QA SQL role','Not superuser; cannot create DB or roles',flags,flags=='f\tf\tf')
        for name in ['PARENT-DATE','HEARING-DATE','PARENT-CASE']:
            cc=self.case('QA limited writer '+name);pp=self.proceeding(cc);other=self.case('QA limited other '+name)
            s,hh,_=self.admin.call('POST','/api/v1/proceedings/'+pp['id']+'/hearings',dict(hearingDate='2026-01-03',purpose='PLEADING'));assert s==201
            sql={ 'PARENT-DATE':f"update proceeding set filing_date='2026-01-04' where id='{pp['id']}'",'HEARING-DATE':f"update hearing set hearing_date='2026-01-01' where id='{hh['id']}'",'PARENT-CASE':f"update proceeding set case_id='{other['id']}' where id='{pp['id']}'"}[name]
            conn=pg8000.dbapi.connect(user='qa_limited_writer',host='127.0.0.1',port=self.dbport,database='postgres');rejected=False;state=None
            try:conn.cursor().execute(sql);conn.commit()
            except pg8000.dbapi.DatabaseError as e:
                rejected=True;state=e.args[0].get('C') if isinstance(e.args[0],dict) else 'UNKNOWN';conn.rollback()
            finally:conn.close()
            invalid=int(self.sql(f"select count(*) from hearing h join proceeding p on p.id=h.proceeding_id where h.id='{hh['id']}' and (h.hearing_date<p.filing_date or h.case_id<>p.case_id)").stdout)
            self.oracle('LIMITED-WRITER-COMMIT-'+name,sql+'; COMMIT; then query joined hearing/proceeding in a new connection','Database rejects invalid write even at commit; no invalid pair',dict(rejected=rejected,sqlstate=state,committed_invalid_rows=invalid),rejected and invalid==0)
        # This head intentionally has no expert module. No expert PASS is assigned to PR33.
        self.results.append(dict(id='PR33-EXPERT-REFERRAL-INTEGRATION',story_ids=['LD-019','LD-025'],priority='P1',preconditions=['Expert referral implementation integrated with proceeding date guard'],test_data={'filing':'2026-01-02','referral':'2026-01-03','new_filing':'2026-01-04'},steps=['Create expert/referral','Correct parent filing date concurrently','Read persisted chronology and history'],expected_results='Reject incompatible writes; preserve history',status='BLOCKED',actual='Expert implementation is absent from PR33. Existing independent PR43 and cumulative evidence covers implemented expert integration separately.'))
        self.save()

    def performance(self):
        # One local client, thirty measured requests per endpoint, >=100ms spacing.
        # This is a small diagnostic baseline, not a load/stress test or SLO acceptance.
        for n in range(20):
            c=self.case('QA timing '+str(n));p=self.proceeding(c)
            assert self.admin.call('POST','/api/v1/proceedings/'+p['id']+'/hearings',dict(hearingDate='2026-01-03',purpose='PLEADING'))[0]==201
        children=Path(f'/proc/{os.getpid()}/task/{os.getpid()}/children').read_text().split()
        jvms=[int(pid)for pid in children if b'lawdesk-backend' in Path('/proc/'+pid+'/cmdline').read_bytes()]
        assert len(jvms)==1;pid=jvms[0];ticks=os.sysconf('SC_CLK_TCK');samples=[];stop=threading.Event()
        def sample():
            while not stop.is_set():
                stat=Path(f'/proc/{pid}/stat').read_text().split();rss=int(stat[23])*os.sysconf('SC_PAGE_SIZE')
                samples.append({'time':time.monotonic(),'cpu_s':(int(stat[13])+int(stat[14]))/ticks,'rss_bytes':rss});stop.wait(.2)
        thread=threading.Thread(target=sample);thread.start();metrics=[]
        try:
            for name,path in [('cases','/api/v1/cases?size=20'),('hearings','/api/v1/hearings?from=2026-01-01&to=2026-01-31&size=20'),('dashboard','/api/v1/reports/dashboard?from=2026-01-01&to=2026-01-31')]:
                for _ in range(2):assert self.admin.call('GET',path)[0]==200;time.sleep(.1)
                timings=[];statuses=[];connections=[];start=time.monotonic()
                for n in range(30):
                    t=time.perf_counter();s,body,_=self.admin.call('GET',path);timings.append((time.perf_counter()-t)*1000);statuses.append(s)
                    if n%10==0:connections.append(int(self.sql("select count(*) from pg_stat_activity where datname='postgres'").stdout))
                    if s!=200:break
                    time.sleep(.1)
                elapsed=time.monotonic()-start;ordered=sorted(timings)
                def q(f):return round(ordered[max(0,math.ceil(len(ordered)*f)-1)],3)
                metrics.append(dict(endpoint=name,samples=len(timings),p50_ms=q(.5),p95_ms=q(.95),p99_ms=q(.99),throughput_rps=round(len(timings)/elapsed,3),error_rate=sum(s!=200 for s in statuses)/len(statuses),observed_db_connections=connections,wall_seconds=round(elapsed,3)))
                self.oracle('BASELINE-HTTP-'+name,'Single client;2warmups and30GETs >=100ms apart; capture latency/status only','30 HTTP200; no latency acceptance threshold claimed',dict(samples=len(statuses),non200=sum(s!=200 for s in statuses)),len(statuses)==30 and all(s==200 for s in statuses))
        finally:stop.set();thread.join()
        conn=pg8000.dbapi.connect(user='lawdesk_qa',host='127.0.0.1',port=self.dbport,database='postgres');cur=conn.cursor();cur.execute("explain (analyze,buffers,format json) select id,case_id,proceeding_id,hearing_date from hearing where hearing_date between '2026-01-01' and '2026-01-31' order by hearing_date,hearing_time nulls first,created_at,id limit 20");plan=cur.fetchone()[0];conn.close()
        (self.output/'hearing-query-plan.json').write_text(json.dumps(plan,indent=2))
        elapsed=samples[-1]['time']-samples[0]['time'];cpu=samples[-1]['cpu_s']-samples[0]['cpu_s']
        data=dict(tested_sha=self.args.sha,scope='Exploratory loopback baseline only; one client; no UAT/shared-host stress; no production SLO',dataset={'cases':20,'proceedings':20,'hearings':20},measurements=metrics,jvm={'cpu_seconds':round(cpu,3),'one_core_utilization_percent':round(100*cpu/elapsed,3),'sampled_rss_max_bytes':max(s['rss_bytes'] for s in samples),'sample_interval_seconds':.2},limitations=['30 samples yield unstable tail percentiles','Local jar/JVM, not deployment container','Throughput includes deliberate pacing; not saturation throughput','RSS sampled, not guaranteed peak; connection counts include QA observer','Small dataset cannot predict production query plans','DB process CPU and independent cgroup accounting not captured'])
        (self.output/'performance-baseline.json').write_text(json.dumps(data,indent=2)+'\n')

if __name__=='__main__':
 p=argparse.ArgumentParser()
 for name in ['candidate','sha','output']:p.add_argument('--'+name,required=True)
 p.add_argument('--mode',choices=['delta','performance'],required=True)
 args=p.parse_args();args.kind='hearing-chronology';args.browser=False
 raise SystemExit(PriorityReview(args).execute())
