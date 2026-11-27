"""QA-R6-M0-001: every legal-effect operator operation needs a request confirmed by a second person, enforced inside the function."""
import json
import threading
import unittest

import psycopg2

from common import PEOPLE, connect, new_id, one, run, sha, state

DOC = "synthetic-doc"
H = "c" * 64
FACTS = ("select md5(concat((select string_agg(x::text, ',' order by x.id) from lawdesk.legal_approval x),"
         " (select string_agg(x::text, ',' order by x.id) from lawdesk.legal_authority_grant x),"
         " (select string_agg(x::text, ',' order by x.policy) from lawdesk.owner_policy x),"
         " (select string_agg(x::text, ',' order by x.id) from lawdesk.legal_bootstrap x)))")


def facts():
    return one(FACTS)


def request(kind, payload, who="op_alice"):
    return run(who, "select lawdesk.r6_op_request(%s, %s::jsonb, %s, %s)", kind, json.dumps(payload), DOC, H)[0][0]


def confirm(rid, who="op_bob"):
    digest = run(who, "select payload_sha256 from lawdesk.r6_op_list() where id = %s", rid)[0][0]
    run(who, "select lawdesk.r6_op_confirm(%s, %s)", rid, digest)


def execute(function, rid, who="op_bob"):
    return run(who, "select lawdesk." + function + "(%s)", rid)[0][0]


def two_person(kind, payload, function):
    rid = request(kind, payload)
    confirm(rid)
    return rid, execute(function, rid)


def grant(user, authority, scope_type="ALL", scope_ref=None):
    return {"user_id": user, "authority": authority, "scope_type": scope_type, "scope_ref": scope_ref}


EXECUTORS = {"LEGAL_APPROVAL_RECORD": "r6_op_record_legal_approval", "GATE_ACTIVATE": "r6_op_activate_gate",
             "LEGAL_APPROVAL_WITHDRAW": "r6_op_withdraw_legal_approval", "AUTHORITY_GRANT": "r6_op_grant_authority",
             "AUTHORITY_REVOKE": "r6_op_revoke_authority", "POLICY_SET": "r6_op_set_policy",
             "BOOTSTRAP_OPEN": "r6_op_bootstrap_open", "BOOTSTRAP_COMPLETE": "r6_op_bootstrap_complete",
             "BOOTSTRAP_REVOKE": "r6_op_bootstrap_revoke"}


def sample(kind):
    anything = new_id()
    return {"LEGAL_APPROVAL_RECORD": {"gate": "G-FINAL", "version": 900},
            "GATE_ACTIVATE": {"approval_id": anything, "gate": "G-FINAL", "version": 900},
            "LEGAL_APPROVAL_WITHDRAW": {"approval_id": anything, "gate": "G-FINAL", "version": 900},
            "AUTHORITY_GRANT": grant(PEOPLE["a5"], "COMPLAINT_APPROVE"),
            "AUTHORITY_REVOKE": {"user_id": PEOPLE["a5"], "authority": "COMPLAINT_APPROVE"},
            "POLICY_SET": {"policy": "P-DECLASS", "enabled": True},
            "BOOTSTRAP_OPEN": {"nominee1": PEOPLE["a1"], "nominee2": PEOPLE["a3"], "token1_sha256": sha("x"), "token2_sha256": sha("y")},
            "BOOTSTRAP_COMPLETE": {"bootstrap_id": anything}, "BOOTSTRAP_REVOKE": {"bootstrap_id": anything}}[kind]


class EveryKindNeedsTwoPeople(unittest.TestCase):
    def test_every_kind_refuses_the_single_operator_paths(self):
        for kind, function in EXECUTORS.items():
            with self.subTest(kind=kind):
                before = facts()
                rid = request(kind, sample(kind))
                call = "select lawdesk." + function + "(%s)"
                self.assertEqual("LD052", state("op_alice", call, rid), "unconfirmed request executed by its requester")
                self.assertEqual("LD052", state("op_bob", call, rid), "unconfirmed request executed by another operator")
                digest = one("select payload_sha256 from lawdesk.op_request where id = '%s'" % rid)
                self.assertEqual("LD050", state("op_alice", "select lawdesk.r6_op_confirm(%s, %s)", rid, digest))
                self.assertEqual("LD057", state("op_carol", "select lawdesk.r6_op_confirm(%s, %s)", rid, digest))
                self.assertEqual("LD054", state("op_bob", "select lawdesk.r6_op_confirm(%s, %s)", rid, "0" * 64))
                self.assertEqual(before, facts())
                run("op_alice", "select lawdesk.r6_op_cancel(%s)", rid)

    def test_wrong_kind_cannot_be_executed_by_another_executor(self):
        before = facts()
        rid = request("AUTHORITY_REVOKE", sample("AUTHORITY_REVOKE"))
        confirm(rid)
        for kind, function in EXECUTORS.items():
            if kind != "AUTHORITY_REVOKE":
                self.assertEqual("LD052", state("op_bob", "select lawdesk." + function + "(%s)", rid), function)
        self.assertEqual(before, facts())
        run("op_alice", "select lawdesk.r6_op_cancel(%s)", rid)

    def test_payload_shape_is_validated(self):
        bad = [("NOPE", {}), ("POLICY_SET", {"policy": "P-DECLASS", "enabled": "true"}), ("POLICY_SET", {"policy": "P-OTHER", "enabled": True}),
               ("POLICY_SET", {"policy": "P-DECLASS", "enabled": True, "extra": 1}), ("POLICY_SET", {"policy": "P-DECLASS"}),
               ("LEGAL_APPROVAL_RECORD", {"gate": "G-NOPE", "version": 1}), ("LEGAL_APPROVAL_RECORD", {"gate": "G-FINAL", "version": 1.5}),
               ("AUTHORITY_GRANT", grant("not-a-uuid", "COMPLAINT_APPROVE")), ("AUTHORITY_GRANT", grant(PEOPLE["a5"], "ROOT")),
               ("AUTHORITY_GRANT", grant(PEOPLE["a5"], "LEGAL_AUTHORITY_CUSTODIAN")),
               ("AUTHORITY_GRANT", grant(PEOPLE["a5"], "COMPLAINT_VIEW", "UNIT", None)), ("AUTHORITY_GRANT", [1, 2])]
        for kind, payload in bad:
            with self.subTest(payload=payload):
                self.assertEqual("LD053", state("op_alice", "select lawdesk.r6_op_request(%s, %s::jsonb, %s, %s)", kind, json.dumps(payload), DOC, H))
        self.assertEqual("LD053", state("op_alice", "select lawdesk.r6_op_request('POLICY_SET', %s::jsonb, '', %s)",
                                        json.dumps(sample("POLICY_SET")), H))

    def test_unregistered_disabled_and_same_person_logins(self):
        self.assertEqual("LD057", state("op_carol", "select lawdesk.r6_op_request('POLICY_SET', %s::jsonb, %s, %s)",
                                        json.dumps(sample("POLICY_SET")), DOC, H))
        self.assertEqual("23505", state("lawdesk_owner", "insert into lawdesk.operator_identity (login, person_ref) values ('op_carol','synthetic-person-1')"),
                         "one person must not hold two registered logins")
        rid = request("POLICY_SET", sample("POLICY_SET"))
        confirm(rid, "op_dave")
        run("lawdesk_owner", "update lawdesk.operator_identity set active = false where login = 'op_dave'")
        try:
            before = facts()
            self.assertEqual("LD057", state("op_alice", "select lawdesk.r6_op_set_policy(%s)", rid), "a disabled confirmer's confirmation was used")
            self.assertEqual(before, facts())
        finally:
            run("lawdesk_owner", "update lawdesk.operator_identity set active = true where login = 'op_dave'")
            run("op_alice", "select lawdesk.r6_op_cancel(%s)", rid)

    def test_application_credentials_reach_no_operator_function_or_table(self):
        rows = run("lawdesk_owner", "select r.rolname, p.proname from pg_proc p, pg_roles r where p.pronamespace = 'lawdesk'::regnamespace"
                   " and (p.proname like 'r6\\_op\\_%%' or p.proname = 'r6_has_grant') and r.rolname in ('lawdesk_app','lawdesk_legal','lawdesk_dml')"
                   " and has_function_privilege(r.rolname, p.oid, 'execute')")
        self.assertEqual([], rows)
        for user in ("lawdesk_app", "lawdesk_legal", "op_alice"):
            for table in ("op_request", "operator_identity", "legal_bootstrap_nominee"):
                self.assertEqual("42501", state(user, "select * from lawdesk." + table), user + " " + table)
        self.assertEqual("42501", state("op_alice", "select lawdesk.r6_op_consume(gen_random_uuid(), 'POLICY_SET')"))


class ContentIsImmutableAndSingleUse(unittest.TestCase):
    def setUp(self):
        self.rid = request("AUTHORITY_GRANT", grant(PEOPLE["a5"], "COMPLAINT_CREATE"))
        confirm(self.rid)

    def tearDown(self):
        state("op_alice", "select lawdesk.r6_op_cancel(%s)", self.rid)

    def test_nobody_edits_a_confirmed_request(self):
        edit = "update lawdesk.op_request set payload = jsonb_set(payload, '{authority}', '\"COMPLAINT_APPROVE\"') where id = %s"
        for user in ("lawdesk_app", "lawdesk_legal", "op_alice", "op_bob"):
            self.assertEqual("42501", state(user, edit, self.rid), user)
        self.assertEqual("LD055", state("lawdesk_owner", edit, self.rid))
        self.assertEqual("LD055", state("lawdesk_owner", "update lawdesk.op_request set state = 'OPEN' where id = %s", self.rid))
        self.assertEqual("LD055", state("lawdesk_owner", "update lawdesk.op_request set confirmed_by = 'op_dave' where id = %s", self.rid))
        self.assertEqual("LD055", state("lawdesk_owner", "delete from lawdesk.op_request where id = %s", self.rid))
        self.assertEqual("LD055", state("lawdesk_owner", "insert into lawdesk.op_request (kind, payload, payload_sha256, doc_ref, doc_sha256,"
                                        " requested_by, expires_at, confirmed_by, state) values ('POLICY_SET','{}','x','x','x','op_alice', now(),"
                                        " 'op_bob', 'CONFIRMED')"))

    def test_content_that_no_longer_matches_its_hash_is_not_executed(self):
        # simulate a tamper below the application roles: the owner disables the trigger, edits, re-enables
        before = facts()
        c = connect("lawdesk_owner", autocommit=False)
        cur = c.cursor()
        cur.execute("alter table lawdesk.op_request disable trigger r6_op_request_immutable")
        cur.execute("update lawdesk.op_request set payload = jsonb_set(payload, '{authority}', '\"COMPLAINT_APPROVE\"') where id = %s", (self.rid,))
        cur.execute("alter table lawdesk.op_request enable trigger r6_op_request_immutable")
        c.commit()
        c.close()
        self.assertEqual("LD054", state("op_bob", "select lawdesk.r6_op_grant_authority(%s)", self.rid))
        self.assertEqual(before, facts())

    def test_expired_request(self):
        run("lawdesk_owner", "alter table lawdesk.op_request disable trigger r6_op_request_immutable")
        run("lawdesk_owner", "update lawdesk.op_request set expires_at = now() - interval '1 second' where id = %s", self.rid)
        run("lawdesk_owner", "alter table lawdesk.op_request enable trigger r6_op_request_immutable")
        self.assertEqual("LD056", state("op_bob", "select lawdesk.r6_op_grant_authority(%s)", self.rid))

    def test_concurrent_double_execution_has_one_effect(self):
        results = []
        first = connect("op_alice", autocommit=False)
        cur = first.cursor()
        cur.execute("select lawdesk.r6_op_grant_authority(%s)", (self.rid,))
        other = threading.Thread(target=lambda: results.append(state("op_bob", "select lawdesk.r6_op_grant_authority(%s)", self.rid)))
        other.start()
        other.join(0.5)
        self.assertTrue(other.is_alive(), "the second execution should wait on the request row")
        first.commit()
        first.close()
        other.join(10)
        self.assertEqual(["LD052"], results)
        self.assertEqual(1, one("select count(*) from lawdesk.legal_authority_grant where request_id = '%s'" % self.rid))
        two_person("AUTHORITY_REVOKE", {"user_id": PEOPLE["a5"], "authority": "COMPLAINT_CREATE"}, "r6_op_revoke_authority")


class AuthorisedPaths(unittest.TestCase):
    def test_gate_record_activate_withdraw_and_stale_activation(self):
        _, approval = two_person("LEGAL_APPROVAL_RECORD", {"gate": "G-REOPEN", "version": 1}, "r6_op_record_legal_approval")
        payload = {"approval_id": approval, "gate": "G-REOPEN", "version": 1}
        self.assertEqual("RECORDED", one("select state from lawdesk.legal_approval where id = '%s'" % approval), "recording is not activation")
        stale = request("GATE_ACTIVATE", payload)
        confirm(stale)
        two_person("LEGAL_APPROVAL_WITHDRAW", payload, "r6_op_withdraw_legal_approval")
        self.assertEqual("LD052", state("op_bob", "select lawdesk.r6_op_activate_gate(%s)", stale), "activation survived the withdrawal")
        self.assertEqual("WITHDRAWN", one("select state from lawdesk.legal_approval where id = '%s'" % approval))

    def test_activation_must_name_the_recorded_approval(self):
        _, approval = two_person("LEGAL_APPROVAL_RECORD", {"gate": "G-STAGE", "version": 1}, "r6_op_record_legal_approval")
        rid = request("GATE_ACTIVATE", {"approval_id": approval, "gate": "G-ALERT", "version": 1})
        confirm(rid)
        self.assertEqual("LD059", state("op_bob", "select lawdesk.r6_op_activate_gate(%s)", rid))
        self.assertEqual("RECORDED", one("select state from lawdesk.legal_approval where id = '%s'" % approval))

    def test_revocation_cancels_a_pending_grant(self):
        two_person("AUTHORITY_GRANT", grant(PEOPLE["a5"], "COMPLAINT_EDIT"), "r6_op_grant_authority")
        stale = request("AUTHORITY_GRANT", grant(PEOPLE["a5"], "COMPLAINT_EDIT"))
        confirm(stale)
        two_person("AUTHORITY_REVOKE", {"user_id": PEOPLE["a5"], "authority": "COMPLAINT_EDIT"}, "r6_op_revoke_authority")
        self.assertEqual("LD052", state("op_bob", "select lawdesk.r6_op_grant_authority(%s)", stale), "a pending grant undid the revocation")
        self.assertEqual(0, one("select count(*) from lawdesk.legal_authority_grant where user_id = '%s' and authority = 'COMPLAINT_EDIT'"
                                " and state = 'ACTIVE'" % PEOPLE["a5"]))

    def test_policy_on_then_off(self):
        two_person("POLICY_SET", {"policy": "P-DECLASS", "enabled": True}, "r6_op_set_policy")
        self.assertTrue(one("select enabled from lawdesk.owner_policy where policy = 'P-DECLASS'"))
        two_person("POLICY_SET", {"policy": "P-DECLASS", "enabled": False}, "r6_op_set_policy")
        self.assertFalse(one("select enabled from lawdesk.owner_policy where policy = 'P-DECLASS'"))

    def test_a_policy_change_cancels_the_other_pending_decisions(self):
        stale = request("POLICY_SET", {"policy": "P-DECLASS", "enabled": True})
        confirm(stale)
        two_person("POLICY_SET", {"policy": "P-DECLASS", "enabled": False}, "r6_op_set_policy")
        self.assertEqual("LD052", state("op_bob", "select lawdesk.r6_op_set_policy(%s)", stale), "an older confirmed switch-on survived")
        self.assertFalse(one("select enabled from lawdesk.owner_policy where policy = 'P-DECLASS'"))

    def test_bootstrap_revocation_cancels_a_pending_completion(self):
        _, boot = two_person("BOOTSTRAP_OPEN", {"nominee1": PEOPLE["a1"], "nominee2": PEOPLE["a3"], "token1_sha256": sha(new_id()),
                                                "token2_sha256": sha(new_id())}, "r6_op_bootstrap_open")
        stale = request("BOOTSTRAP_COMPLETE", {"bootstrap_id": boot})
        confirm(stale)
        two_person("BOOTSTRAP_REVOKE", {"bootstrap_id": boot}, "r6_op_bootstrap_revoke")
        self.assertEqual("LD052", state("op_bob", "select lawdesk.r6_op_bootstrap_complete(%s)", stale))

    def test_bootstrap_full_ceremony_and_revocation(self):
        n1, n2 = PEOPLE["a1"], PEOPLE["a3"]
        t1, t2 = new_id(), new_id()
        opening = {"nominee1": n1, "nominee2": n2, "token1_sha256": sha(t1), "token2_sha256": sha(t2)}
        _, boot = two_person("BOOTSTRAP_OPEN", opening, "r6_op_bootstrap_open")
        complete = request("BOOTSTRAP_COMPLETE", {"bootstrap_id": boot})
        confirm(complete)
        self.assertEqual("LD058", state("op_bob", "select lawdesk.r6_op_bootstrap_complete(%s)", complete), "completed without nominees")
        check = lambda token, user: run("lawdesk_legal", "select lawdesk.r6_bootstrap_confirm(%s, %s)", token, user)[0][0]
        self.assertFalse(check("wrong", n1))
        self.assertEqual(1, one("select attempts from lawdesk.legal_bootstrap_nominee where bootstrap_id = '%s' and user_id = '%s'" % (boot, n1)))
        self.assertFalse(check(t1, n2), "a token confirms only its own nominee")
        self.assertTrue(check(t1, n1))
        self.assertFalse(check(t1, n1), "single use")
        self.assertEqual("LD058", state("op_bob", "select lawdesk.r6_op_bootstrap_complete(%s)", complete), "completed with one nominee")
        self.assertEqual(0, one("select count(*) from lawdesk.legal_authority_grant where authority = 'LEGAL_AUTHORITY_CUSTODIAN' and state = 'ACTIVE'"))
        self.assertTrue(check(t2, n2))
        execute("r6_op_bootstrap_complete", complete)
        grants = run("lawdesk_owner", "select user_id::text, approved_by_user::text from lawdesk.legal_authority_grant where bootstrap_id = %s"
                     " and state = 'ACTIVE' order by 1", boot)
        self.assertEqual(sorted([(n1, n2), (n2, n1)]), grants, "cross-approved custodians")
        again = request("BOOTSTRAP_OPEN", dict(opening, token1_sha256=sha("p"), token2_sha256=sha("q")))
        confirm(again)
        self.assertEqual("LD059", state("op_bob", "select lawdesk.r6_op_bootstrap_open(%s)", again), "added custodians next to existing ones")
        run("op_alice", "select lawdesk.r6_op_cancel(%s)", again)
        two_person("BOOTSTRAP_REVOKE", {"bootstrap_id": boot}, "r6_op_bootstrap_revoke")
        self.assertEqual(0, one("select count(*) from lawdesk.legal_authority_grant where authority = 'LEGAL_AUTHORITY_CUSTODIAN' and state = 'ACTIVE'"))

    def test_expired_bootstrap_token(self):
        n1, n2, token = PEOPLE["a1"], PEOPLE["a3"], new_id()
        _, boot = two_person("BOOTSTRAP_OPEN", {"nominee1": n1, "nominee2": n2, "token1_sha256": sha(token), "token2_sha256": sha(token + "2")},
                             "r6_op_bootstrap_open")
        try:
            run("lawdesk_owner", "update lawdesk.legal_bootstrap_nominee set expires_at = now() - interval '1 second' where bootstrap_id = %s", boot)
            self.assertFalse(run("lawdesk_legal", "select lawdesk.r6_bootstrap_confirm(%s, %s)", token, n1)[0][0])
        finally:
            two_person("BOOTSTRAP_REVOKE", {"bootstrap_id": boot}, "r6_op_bootstrap_revoke")


if __name__ == "__main__":
    unittest.main()
