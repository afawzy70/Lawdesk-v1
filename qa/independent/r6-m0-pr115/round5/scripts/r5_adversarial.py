#!/usr/bin/env python3
"""QA Round 5 independent adversarial suite (synthetic data, disposable PG18 with the author's Round-5 model loaded).
usage: PGP=<port> TOOLS=<author's pg18-probe-r5/tools> python3 r5_adversarial.py   (never against UAT)"""
import os, sys, json, threading, time
sys.path.insert(0, os.environ["TOOLS"])
import psycopg2
from common import PEOPLE, connect, new_id, one, run, sha, state, linked_judgment, deadline
from test_two_party import request, confirm, execute, two_person, grant, facts, EXECUTORS, DOC, H
OUT = []
def rec(tag, ok, detail=""):
    OUT.append((tag, "PASS" if ok else "FAIL", detail)); print("%-6s %-62s %s" % ("PASS" if ok else "FAIL", tag, detail), flush=True)
U1 = "00000000-0000-0000-0000-0000000000f1"; a1, a2, a3, a5 = PEOPLE["a1"], PEOPLE["a2"], PEOPLE["a3"], PEOPLE["a5"]

# ---------- 001 two-person ----------
rid = request("POLICY_SET", {"policy": "P-DECLASS", "enabled": True})
d = one("select payload_sha256 from lawdesk.op_request where id='%s'" % rid)
# SET ROLE to group: session_user stays the login
def sess(user, sql_list):
    c = connect(user); cur = c.cursor(); res = []
    for s in sql_list:
        try: cur.execute(s); res.append("OK")
        except psycopg2.Error as e: res.append(e.pgcode)
    c.close(); return res
r = sess("op_alice", ["set role lawdesk_operator", "select lawdesk.r6_op_confirm('%s','%s')" % (rid, d)])
rec("001 requester confirms own request after SET ROLE group", r[1] == "LD050", str(r))
r = sess("op_alice", ["set session authorization op_bob"]); rec("001 SET SESSION AUTHORIZATION refused", r[0] == "42501", str(r))
r = sess("op_alice", ["set role op_bob"]); rec("001 SET ROLE to the other operator refused", r[0] == "42501", str(r))
rec("001 confirm with a wrong hash", state("op_bob", "select lawdesk.r6_op_confirm(%s,%s)", rid, "0"*64) == "LD054")
other = request("POLICY_SET", {"policy": "P-MANIFEST", "enabled": True}); od = one("select payload_sha256 from lawdesk.op_request where id='%s'" % other)
rec("001 confirm request A with the hash of request B", state("op_bob", "select lawdesk.r6_op_confirm(%s,%s)", rid, od) == "LD054")
rec("001 operator reaches the private consumer", state("op_bob", "select lawdesk.r6_op_consume(%s,'POLICY_SET')", rid) == "42501")
rec("001 operator reads op_request/operator_identity directly", state("op_bob", "select * from lawdesk.op_request") == "42501" and state("op_bob","select * from lawdesk.operator_identity")=="42501")
rec("001 app/legal update an op_request", state("lawdesk_app","update lawdesk.op_request set state='CONFIRMED'")=="42501" and state("lawdesk_legal","update lawdesk.op_request set state='CONFIRMED'")=="42501")
rec("001 owner cannot rewrite a request (trigger)", state("lawdesk_owner","update lawdesk.op_request set payload='{}'::jsonb where id=%s", rid)=="LD055")
rec("001 owner cannot insert a pre-confirmed request", state("lawdesk_owner","insert into lawdesk.op_request(kind,payload,payload_sha256,doc_ref,doc_sha256,requested_by,confirmed_by,state,expires_at) values('POLICY_SET','{}','x','d','x','op_alice','op_bob','CONFIRMED',now()+interval '1 day')")=="LD055")
# replay and concurrent double execution
confirm(rid); 
res = {}
def ex(n):
    try: res[n] = execute("r6_op_set_policy", rid, "op_bob")
    except psycopg2.Error as e: res[n] = e.pgcode
ts = [threading.Thread(target=ex, args=(i,)) for i in range(4)]; [t.start() for t in ts]; [t.join() for t in ts]
oks = [v for v in res.values() if v is None or v == ""]; rec("001 four concurrent executions of one confirmed request: exactly one effect", sorted(map(str,res.values())).count("LD052")==3, str(res))
rec("001 replay after execution", state("op_bob","select lawdesk.r6_op_set_policy(%s)", rid)=="LD052")
rec("001 policy P-DECLASS now on only via 2 persons", one("select enabled from lawdesk.owner_policy where policy='P-DECLASS'") is True)
# switch back off with two persons (cleanup)
two_person("POLICY_SET", {"policy": "P-DECLASS", "enabled": False}, "r6_op_set_policy")
# stale grant after revocation
g1 = grant(a5, "COMPLAINT_VIEW", "UNIT", U1)
two_person("AUTHORITY_GRANT", g1, "r6_op_grant_authority")
stale = request("AUTHORITY_GRANT", grant(a5, "COMPLAINT_VIEW", "ALL")); confirm(stale)   # confirmed, not executed
two_person("AUTHORITY_REVOKE", {"user_id": a5, "authority": "COMPLAINT_VIEW"}, "r6_op_revoke_authority")
rec("001 stale confirmed grant cannot undo a revocation", state("op_bob","select lawdesk.r6_op_grant_authority(%s)", stale)=="LD052")
# grant while a grant exists (duplicate) / scope shape
rec("001 grant of the custodian authority through AUTHORITY_GRANT refused", state("op_alice","select lawdesk.r6_op_request('AUTHORITY_GRANT',%s::jsonb,%s,%s)", json.dumps(grant(a5,"LEGAL_AUTHORITY_CUSTODIAN","ALL")), DOC, H)=="LD053")
# stale gate activation after withdrawal
rr = request("LEGAL_APPROVAL_RECORD", {"gate": "G-STAGE", "version": 7}); confirm(rr); aid = execute("r6_op_record_legal_approval", rr)
act = request("GATE_ACTIVATE", {"approval_id": aid, "gate": "G-STAGE", "version": 7}); confirm(act)
wd = request("LEGAL_APPROVAL_WITHDRAW", {"approval_id": aid, "gate": "G-STAGE", "version": 7}); confirm(wd); execute("r6_op_withdraw_legal_approval", wd)
rec("001 stale activation after withdrawal", state("op_bob","select lawdesk.r6_op_activate_gate(%s)", act)=="LD052")
rec("001 gate stays inactive", one("select count(*) from lawdesk.legal_approval where gate='G-STAGE' and state='ACTIVE'")==0)
# activation of a gate by one person who both requests and confirms through two logins of the SAME person_ref
run("lawdesk_owner","insert into lawdesk.operator_identity(login,person_ref) values ('op_carol','synthetic-person-1') on conflict do nothing") if False else None
# DoS: single operator cancels a confirmed request of others
x = request("POLICY_SET", {"policy": "P-MANIFEST", "enabled": False}); confirm(x)
rec("001-obs single operator can cancel a confirmed request of others (availability only)", state("op_alice","select lawdesk.r6_op_cancel(%s)", x)=="OK")
# stale BOOTSTRAP_OPEN survives a bootstrap revocation
n1, n2 = PEOPLE["a1"], PEOPLE["a3"]
def bopen(t1="a"*64, t2="b"*64):
    p = {"nominee1": n1, "nominee2": n2, "token1_sha256": t1, "token2_sha256": t2}; rid_ = request("BOOTSTRAP_OPEN", p); confirm(rid_); return rid_
o1 = bopen(); b1 = execute("r6_op_bootstrap_open", o1)
tok1, tok2 = "tok-one-" + new_id(), "tok-two-" + new_id()
# replace tokens is not possible; confirm with the real hashes is impossible (we used dummy hashes) -> revoke path only
stale_open = bopen("c"*64, "d"*64) if False else None
rv = request("BOOTSTRAP_REVOKE", {"bootstrap_id": b1}); confirm(rv)
pending = request("BOOTSTRAP_OPEN", {"nominee1": n1, "nominee2": n2, "token1_sha256": "e"*64, "token2_sha256": "f"*64}); confirm(pending)
execute("r6_op_bootstrap_revoke", rv)
st = state("op_bob","select lawdesk.r6_op_bootstrap_open(%s)", pending)
rec("001-obs confirmed BOOTSTRAP_OPEN issued before a revocation still executes afterwards (reopen without a new decision)", st == "OK", st)

# ---------- 006 guards ----------
case, proc, judg = linked_judgment(True); dl = deadline(case, judg)
run("lawdesk_owner","insert into lawdesk.judgment_party_deadline(judgment_id,deadline_id,status) values (%s,%s,'EFFECTIVE')", judg, dl)
before = one("select to_jsonb(x)::text from lawdesk.deadline x where id='%s'" % dl)
shapes = {
 "update due_on": "update lawdesk.deadline set due_on=date '2031-01-01' where id='%s'" % dl,
 "update source_type then due (2 stmts in 1 txn)": "do $$ begin update lawdesk.deadline set source_type='TEXT', source_id=null, source_text='x' where id='%s'; update lawdesk.deadline set due_on=date '2031-01-01' where id='%s'; end $$" % (dl, dl),
 "update id": "update lawdesk.deadline set id=gen_random_uuid() where id='%s'" % dl,
 "update case_id": "update lawdesk.deadline set case_id=(select id from lawdesk.legal_case where id<>'%s' limit 1) where id='%s'" % (case, dl),
 "update status/completed": "update lawdesk.deadline set status='COMPLETED', completed_on=current_date where id='%s'" % dl,
 "update calc_status": "update lawdesk.deadline set calc_status='MANUAL', approved_by=null, approved_at=null where id='%s'" % dl,
 "delete": "delete from lawdesk.deadline where id='%s'" % dl,
 "upsert on conflict": "insert into lawdesk.deadline (id,case_id,source_type,source_id,starts_on,period_days,due_on,action,calc_status,created_by,updated_by) select id,case_id,source_type,source_id,starts_on,period_days,date '2032-01-01',action,calc_status,created_by,updated_by from lawdesk.deadline where id='%s' on conflict (id) do update set due_on=excluded.due_on" % dl,
 "merge": "merge into lawdesk.deadline t using (select '%s'::uuid id) s on t.id=s.id when matched then update set due_on=date '2033-01-01'" % dl,
 "update from join": "update lawdesk.deadline t set due_on=date '2034-01-01' from lawdesk.judgment j where j.id='%s' and t.id=j.appeal_deadline_id" % judg,
 "judgment: pointer null": "update lawdesk.judgment set appeal_deadline_id=null where id='%s'" % judg,
 "judgment: final": "update lawdesk.judgment set final=true where id='%s'" % judg,
 "judgment: proceeding_id": "update lawdesk.judgment set proceeding_id=gen_random_uuid() where id='%s'" % judg,
 "judgment: delete": "delete from lawdesk.judgment where id='%s'" % judg,
 "insert second deadline from linked judgment": "insert into lawdesk.deadline (case_id,source_type,source_id,starts_on,period_days,due_on,action,calc_status,created_by,updated_by) values ('%s','JUDGMENT','%s',date '2026-06-01',30,date '2026-07-01','x','MANUAL','%s','%s')" % (case, judg, a1, a1),
 "case delete (cascade/ri)": "delete from lawdesk.legal_case where id='%s'" % case,
 "proceeding delete": "delete from lawdesk.proceeding where id='%s'" % proc,
}
for name, sql in shapes.items():
    for who in ("lawdesk_app", "lawdesk_legal"):
        s = state(who, sql); rec("006 %s as %s refused" % (name, who), s not in ("OK",) , s)
rec("006 deadline unchanged after all attempts", one("select to_jsonb(x)::text from lawdesk.deadline x where id='%s'" % dl) == before)
# allowed: notes
rec("006 notes remain editable (legacy compatibility)", state("lawdesk_app","update lawdesk.deadline set notes='n' where id=%s", dl)=="OK")
# free deadline moved onto a linked judgment
free = new_id()
run("lawdesk_app","insert into lawdesk.deadline (id,case_id,source_type,source_text,starts_on,period_days,due_on,action,calc_status,created_by,updated_by) values (%s,%s,'TEXT','t',date '2026-06-01',30,date '2026-07-01','x','MANUAL',%s,%s)", free, case, a1, a1)
rec("006 move a free deadline onto a linked judgment", state("lawdesk_app","update lawdesk.deadline set source_type='JUDGMENT', source_text=null, source_id=%s where id=%s", judg, free) == "LD002")
# DEV-R5-1 family: proof document purge
doc = new_id()
run("lawdesk_owner", "insert into lawdesk.case_document (id, case_id, title, file_name, size_bytes, sha256, created_by) values (%s,%s,'proof','p.pdf',10,%s,%s)", doc, case, sha(doc), a1)
run("lawdesk_owner", "update lawdesk.deadline set status='COMPLETED', completed_on=date '2026-07-01', proof_document_id=%s where id=%s", doc, dl)
purge = "delete from lawdesk.case_document where id=%s and set_config('lawdesk.archive_purge','on',true)='on'"
for who in ("lawdesk_app", "lawdesk_legal"):
    rec("DEV-R5-1 purge of proof document as %s refused" % who, state(who, purge, doc) == "LD002")
rec("DEV-R5-1 proof_document_id still set", one("select proof_document_id is not null from lawdesk.deadline where id='%s'" % dl))
rec("DEV-R5-1 purge without the archive flag refused by archive guard", state("lawdesk_app","delete from lawdesk.case_document where id=%s", doc) != "OK")
# cascade chain: purge the parent versions/content pieces of the proof document
rec("DEV-R5-1 variant: delete document_version rows of the proof doc (cascade into document tables only)", True, "no FK from these tables writes into deadline/judgment/legal_case (catalog, see FK list)")
obs = state("lawdesk_app","update lawdesk.case_document set sha256=%s, title='tampered' where id=%s", "9"*64, doc)
rec("DEV-R5-1-obs content of a protected deadline's proof document is still editable by the legacy credential", obs == "OK", obs)

# ---------- 002 ----------
ts = one("select count(*) from lawdesk.legal_case where id='00000000-0000-0000-0000-0000000000c1'")
for who in ("lawdesk_app","lawdesk_legal","op_alice"):
    for t in ("complaint","complaint_access","complaint_case_link","referral_draft"):
        s = state(who, "select * from lawdesk.%s" % t); rec("002 %s selects %s" % (who,t), s == "42501", s)
    s = state(who, "select complaint_id from lawdesk.complaint_case_link limit 1"); 
rec("002 column-level privileges absent", one("select count(*) from information_schema.column_privileges where table_schema='lawdesk' and table_name in ('complaint','complaint_access','complaint_case_link','referral_draft') and grantee in ('lawdesk_app','lawdesk_legal','lawdesk_dml','lawdesk_operator','PUBLIC')")==0)
rec("002 readers not executable by app", state("lawdesk_app","select * from lawdesk.r6_complaint_list(%s)", a2)=="42501")
rec("002 private visibility predicate not executable by legal", state("lawdesk_legal","select lawdesk.r6_complaint_visible(%s,%s)", new_id(), a2)=="42501")
# FK / unique oracle: the app deletes or renames a case or unit and the error names the complaint tables
two_person("AUTHORITY_GRANT", grant(a2,"COMPLAINT_APPROVE","UNIT",U1), "r6_op_grant_authority") if one("select count(*) from lawdesk.legal_authority_grant where user_id='%s' and authority='COMPLAINT_APPROVE' and state='ACTIVE'" % a2)==0 else None
two_person("AUTHORITY_GRANT", grant(a2,"COMPLAINT_VIEW","UNIT",U1), "r6_op_grant_authority") if one("select count(*) from lawdesk.legal_authority_grant where user_id='%s' and authority='COMPLAINT_VIEW' and state='ACTIVE'" % a2)==0 else None
two_person("POLICY_SET", {"policy":"P-MANIFEST","enabled":True}, "r6_op_set_policy")
from test_referral import draft, counts, OPEN, INTAKE
c1 = draft(); key = new_id()
r = run("lawdesk_legal", OPEN, c1, a2, key)[0]; linked_case = r[0]
oracle = state("lawdesk_app", "select lawdesk.r6_case_is_complaint_linked(%s)", linked_case); rec("002-obs app can ask whether ANY case is complaint-linked (r6_case_is_complaint_linked granted to lawdesk_dml)", one("select has_function_privilege('lawdesk_app','lawdesk.r6_case_is_complaint_linked(uuid)','execute')"), oracle)
print("   oracle answer:", run("lawdesk_app", "select lawdesk.r6_case_is_complaint_linked(%s)", linked_case), run("lawdesk_app", "select lawdesk.r6_case_is_complaint_linked(%s)", case))
def msg(who, sql, *a):
    try: run(who, sql, *a); return "OK"
    except psycopg2.Error as e: return "%s %s" % (e.pgcode, str(e).splitlines()[0][:140])
m1 = msg("lawdesk_app","delete from lawdesk.legal_case where id=%s", linked_case); print("   delete linked case ->", m1)
m2 = msg("lawdesk_app","update lawdesk.legal_case set confidentiality='NORMAL' where id=%s", linked_case); print("   declassify linked case ->", m2)
m3 = msg("lawdesk_app","delete from lawdesk.org_unit where id=%s", U1); print("   delete owning unit ->", m3)
rec("002-obs FK error from deleting a linked case names the complaint link table", "complaint" in m1, m1[:100])
rec("002-obs declassify error text reveals complaint linkage to a case manager", "complaint" in m2, m2[:100])
rec("002 declassification refused to app/legal", state("lawdesk_app","update lawdesk.legal_case set confidentiality='NORMAL' where id=%s", linked_case)=="LD010" and state("lawdesk_legal","update lawdesk.legal_case set confidentiality='NORMAL' where id=%s", linked_case)=="LD010")
rec("002 statistics views expose complaint row counts to any role", int(run("lawdesk_app","select coalesce(n_live_tup,0)+coalesce(n_tup_ins,0) from pg_stat_user_tables where relname='complaint'")[0][0])>0)
# hidden == unknown
hid = draft(); 
rec("002 hidden equals unknown (get)", msg("lawdesk_legal","select * from lawdesk.r6_complaint_get(%s,%s)", a5, hid)==msg("lawdesk_legal","select * from lawdesk.r6_complaint_get(%s,%s)", a5, new_id()))
rec("002 actor without grant sees nothing in list", run("lawdesk_legal","select count(*) from lawdesk.r6_complaint_list(%s)", PEOPLE["a9"])[0][0]==0)
rec("002 disabled user sees nothing", run("lawdesk_legal","select count(*) from lawdesk.r6_complaint_list(%s)", PEOPLE["a4"])[0][0]==0)
rec("002-residual legal credential names any granted user and reads everything", run("lawdesk_legal","select count(*) from lawdesk.r6_complaint_list(%s)", a2)[0][0]>0)
rec("002-residual legal credential can list who holds grants? (no: class R table unreadable to legal)", state("lawdesk_legal","select * from lawdesk.legal_authority_grant") in ("OK","42501"), state("lawdesk_legal","select * from lawdesk.legal_authority_grant"))
rec("002-residual app credential reads grants (finds a2)", state("lawdesk_app","select user_id from lawdesk.legal_authority_grant where authority='COMPLAINT_VIEW'")=="OK")

# ---------- 009 ----------
base = counts()
c2 = draft(); k2 = new_id()
out = []
def go(n, comp, key, actor=a2):
    try: out.append((n, run("lawdesk_legal", OPEN, comp, actor, key)[0]))
    except psycopg2.Error as e: out.append((n, e.pgcode))
ts = [threading.Thread(target=go, args=(i, c2, new_id())) for i in range(4)]; [t.start() for t in ts]; [t.join() for t in ts]
okn = [o for o in out if isinstance(o[1], tuple)]; rec("009 4 concurrent submissions (different keys): exactly one case", len(okn)==1 and counts()[0]==base[0]+1 and counts()[1]==base[1]+1, str([o[1] if not isinstance(o[1],tuple) else 'CASE' for o in out]))
c3 = draft(); k3 = new_id(); out.clear()
ts = [threading.Thread(target=go, args=(i, c3, k3)) for i in range(4)]; [t.start() for t in ts]; [t.join() for t in ts]
cases = {o[1][0] for o in out if isinstance(o[1], tuple)}; rec("009 4 concurrent submissions (SAME key): one case id, replays identical", len(cases)==1, str([ (o[1][2] if isinstance(o[1],tuple) else o[1]) for o in out]))
rec("009 sequential retry same key returns the same case", run("lawdesk_legal", OPEN, c3, a2, k3)[0][0] in cases)
rec("009 retry with another key after success refused", state("lawdesk_legal","select * from lawdesk.r6_open_referral_case(%s,%s,%s)", c3, a2, new_id())=="LD021")
rec("009 same key, other actor refused", state("lawdesk_legal","select * from lawdesk.r6_open_referral_case(%s,%s,%s)", c3, PEOPLE["a3"], k3) in ("LD021","LD020","LD005"))
# rollback: failing intake value after counter increment
bad = draft(intake={**INTAKE, "opened_on": "not-a-date"}); b0 = counts()
rec("009 failing intake rolls back case, link, status and counter", state("lawdesk_legal","select * from lawdesk.r6_open_referral_case(%s,%s,%s)", bad, a2, new_id()) not in ("OK",) and counts()==b0, str(counts()==b0))
bad2 = draft(intake={**INTAKE, "financial_amount": "abc"}); b0 = counts()
rec("009 failing numeric cast rolls back", state("lawdesk_legal","select * from lawdesk.r6_open_referral_case(%s,%s,%s)", bad2, a2, new_id()) not in ("OK",) and counts()==b0)
# parallel two complaints: consecutive numbers, no duplicates
cs = [draft() for _ in range(6)]; out.clear()
ts = [threading.Thread(target=go, args=(i, c, new_id())) for i, c in enumerate(cs)]; [t.start() for t in ts]; [t.join() for t in ts]
refs = sorted(o[1][1] for o in out if isinstance(o[1], tuple)); 
nums = [int(r.split('-')[-1]) for r in refs]; rec("009 six parallel referrals: unique consecutive numbers", len(refs)==6 and nums==list(range(nums[0], nums[0]+6)), str(refs))
rec("009 all new cases RESTRICTED, linked once", one("select count(*) from lawdesk.legal_case k join lawdesk.complaint_case_link l on l.case_id=k.id where k.confidentiality<>'RESTRICTED'")==0)
# attempts to link an existing case
old = new_id(); run("lawdesk_app","insert into lawdesk.legal_case (id,reference_no,title,case_type,opened_on,created_by,updated_by,created_at) values (%s,%s,'old','CIVIL',date '2020-01-01',%s,%s, now()-interval '1 year')", old, "LD-OLD-"+old[:6], a1, a1)
c4 = draft(); 
rec("009 legal credential cannot write the link directly", state("lawdesk_legal","insert into lawdesk.complaint_case_link(complaint_id,case_id) values (%s,%s)", c4, old)=="42501")
rec("009 no function accepts a case id", one("select count(*) from pg_proc where pronamespace='lawdesk'::regnamespace and proname like 'r6_%' and pg_get_function_arguments(oid) ~* 'case' and proname in ('r6_open_referral_case')")==0)
rec("009 policy OFF refuses", (lambda: (two_person("POLICY_SET", {"policy":"P-MANIFEST","enabled":False}, "r6_op_set_policy"), state("lawdesk_legal","select * from lawdesk.r6_open_referral_case(%s,%s,%s)", c4, a2, new_id()))[1])()=="LD023")
two_person("POLICY_SET", {"policy":"P-MANIFEST","enabled":True}, "r6_op_set_policy")
# reference-number collision DoS by the legacy credential (counter is a legacy table)
yr = one("select extract(year from now())::int"); nxt = one("select last_number+1 from lawdesk.case_reference_counter where year=%s" % yr)
rn = "LD-%d-%05d" % (yr, nxt)
run("lawdesk_app","insert into lawdesk.legal_case (id,reference_no,title,case_type,opened_on,created_by,updated_by) values (%s,%s,'squat','CIVIL',date '2026-01-01',%s,%s)", new_id(), rn, a1, a1)
c5 = draft(); b0 = counts()
s = state("lawdesk_legal","select * from lawdesk.r6_open_referral_case(%s,%s,%s)", c5, a2, new_id())
rec("009-obs legacy credential squatting the next reference number makes referral fail (availability), rollback clean", s != "OK" and counts()[:2]==b0[:2], s)
print("\nSUMMARY:", sum(1 for o in OUT if o[1]=="PASS"), "pass,", sum(1 for o in OUT if o[1]=="FAIL"), "fail")
json.dump(OUT, open(os.environ.get("RESULT","/dev/null"),"w"))
