import os,sys,threading,time
sys.path.insert(0,os.environ["TOOLS"])
import psycopg2
from common import connect,new_id,PEOPLE,linked_judgment,run
a1=PEOPLE["a1"]
def trial(lock):
    case,proc,judg=linked_judgment(False)
    t1=connect("lawdesk_owner",autocommit=False); c1=t1.cursor(); res={}
    if lock: c1.execute("select 1 from lawdesk.judgment where id=%s for update",(judg,))
    c1.execute("insert into lawdesk.judgment_procedural(judgment_id) values (%s)",(judg,))
    def legacy():
        t=time.time()
        try:
            run("lawdesk_app","insert into lawdesk.deadline (case_id,source_type,source_id,starts_on,period_days,due_on,action,calc_status,created_by,updated_by) values (%s,'JUDGMENT',%s,date '2026-06-01',30,date '2026-07-01','x','MANUAL',%s,%s)",case,judg,a1,a1); res['r']='INSERTED'
        except psycopg2.Error as e: res['r']=e.pgcode
        res['waited']=round(time.time()-t,1)
    th=threading.Thread(target=legacy); th.start(); time.sleep(2); t1.commit(); th.join()
    return res
print("registration WITH judgment FOR UPDATE ->",trial(True))
print("registration WITHOUT the lock         ->",trial(False))
