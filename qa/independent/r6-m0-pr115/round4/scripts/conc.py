import psycopg2,threading,time,uuid
U='00000000-0000-0000-0000-0000000000a1';A='00000000-0000-0000-0000-0000000000a2'
def conn(u='lawdesk_legal'):
    c=psycopg2.connect(host='127.0.0.1',port=int(__import__('os').environ.get('PGP','55432')),user=u,dbname='lawdesk'); c.autocommit=False; return c
def ref(name,comp,case,delay,res,hold=0):
    c=conn(); cur=c.cursor()
    try:
        cur.execute("insert into lawdesk.legal_case (id,reference_no,title,case_type,opened_on,confidentiality,created_by,updated_by) values (%s,%s,'t','CIVIL',current_date,'RESTRICTED',%s,%s)",(case,'LD-C-'+case[-6:],U,U))
        time.sleep(delay)
        cur.execute("select lawdesk.r6_complete_referral(%s,%s,%s)",(comp,case,A)); time.sleep(hold); c.commit(); res[name]='COMMIT ok'
    except Exception as e:
        c.rollback(); res[name]='ROLLBACK %s %s'%(e.pgcode,str(e).splitlines()[0][:60])
# S-a: same complaint, two concurrent referrals each with own new case
res={}
c1='00000000-0000-0000-0000-0000000fa001';c2='00000000-0000-0000-0000-0000000fa002'
comp='00000000-0000-0000-0000-00000000f401'
t=[threading.Thread(target=ref,args=('T1',comp,c1,0.2,res,0.5)),threading.Thread(target=ref,args=('T2',comp,c2,0.3,res,0))]
[x.start() for x in t];[x.join() for x in t];print('S-a same complaint x2:',res)
o=conn('lawdesk_owner').cursor(); o.execute("select (select count(*) from lawdesk.complaint_case_link where complaint_id=%s),(select count(*) from lawdesk.legal_case where id in (%s,%s))",(comp,c1,c2)); print('  links for complaint, surviving cases:',o.fetchall())
# S-b: two complaints, SAME pre-existing-style case id (unique(case_id)) -- both insert same case id? use shared case created by T0 committed in-txn impossible; instead test two complaints -> one case via forged created_at
c=conn();cur=c.cursor()
cs='00000000-0000-0000-0000-0000000fb001'
cur.execute("insert into lawdesk.legal_case (id,reference_no,title,case_type,opened_on,confidentiality,created_by,updated_by) values (%s,'LD-C-FB1','shared','CIVIL',current_date,'RESTRICTED',%s,%s)",(cs,U,U)); c.commit()
res={}
def forge(name,comp):
    cc=conn();q=cc.cursor()
    try:
        q.execute("update lawdesk.legal_case set created_at=transaction_timestamp() where id=%s",(cs,))
        q.execute("select lawdesk.r6_complete_referral(%s,%s,%s)",(comp,cs,A)); cc.commit(); res[name]='COMMIT ok'
    except Exception as e: cc.rollback(); res[name]='ROLLBACK %s'%(str(e).splitlines()[0][:70])
t=[threading.Thread(target=forge,args=('F1','00000000-0000-0000-0000-00000000f401')),threading.Thread(target=forge,args=('F2','00000000-0000-0000-0000-00000000f402'))]
[x.start() for x in t];[x.join() for x in t];print('S-b two complaints one pre-existing case (forged created_at):',res)
# S-c: deadlock: legacy order child->parent vs inverted parent->child on real proceeding rows
o2=conn('lawdesk_owner');q=o2.cursor()
P1='00000000-0000-0000-0000-0000000000b1';P2='00000000-0000-0000-0000-0000000000b9'
q.execute("insert into lawdesk.proceeding (id,case_id,stage,court_id,court_case_number,court_case_year,filing_date,parent_proceeding_id,created_by,updated_by) values (%s,'00000000-0000-0000-0000-0000000000c1','APPEAL','00000000-0000-0000-0000-0000000000d1','299',2026,date '2026-07-01',%s,%s,%s) on conflict do nothing",(P2,P1,U,U)); o2.commit()
def order(name,first,second,res):
    cc=conn('lawdesk_app');q=cc.cursor()
    try:
        q.execute("select 1 from lawdesk.proceeding where id=%s for update",(first,)); time.sleep(0.6)
        q.execute("select 1 from lawdesk.proceeding where id=%s for update",(second,)); cc.commit(); res[name]='ok'
    except Exception as e: cc.rollback(); res[name]='%s %s'%(e.pgcode,str(e).splitlines()[0][:40])
res={}
t=[threading.Thread(target=order,args=('legacy child->parent',P2,P1,res)),threading.Thread(target=order,args=('R3 parent->child',P1,P2,res))]
[x.start() for x in t];[x.join() for x in t];print('S-c inverted order:',res)
res={}
t=[threading.Thread(target=order,args=('legacy child->parent',P2,P1,res)),threading.Thread(target=order,args=('R4 child->parent',P2,P1,res))]
[x.start() for x in t];[x.join() for x in t];print('S-c unified order:',res)
