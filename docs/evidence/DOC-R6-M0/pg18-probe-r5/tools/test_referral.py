"""QA-R6-M0-009: the writer creates the RESTRICTED case itself, in the caller's transaction; nothing pre-existing can be linked."""
import json
import threading
import unittest

import psycopg2

from common import PEOPLE, connect, new_id, one, run, state
from test_two_party import grant, two_person

UNIT1 = "00000000-0000-0000-0000-0000000000f1"
OPEN = "select case_id::text, reference_no, replayed from lawdesk.r6_open_referral_case(%s, %s, %s)"
INTAKE = {"title": "referred", "case_type": "CRIMINAL", "opened_on": "2026-11-01", "authority_position": "PLAINTIFF", "financial_impact": "NO"}
COUNTS = ("select (select count(*) from lawdesk.legal_case), (select count(*) from lawdesk.complaint_case_link),"
          " (select coalesce(sum(last_number), 0) from lawdesk.case_reference_counter),"
          " (select string_agg(status, ',' order by id) from lawdesk.complaint)")


def counts():
    return run("lawdesk_owner", COUNTS)[0]


def ensure(user, authority):
    if not one("select count(*) from lawdesk.legal_authority_grant where user_id = '%s' and authority = '%s' and state = 'ACTIVE'" % (user, authority)):
        two_person("AUTHORITY_GRANT", grant(user, authority, "UNIT", UNIT1), "r6_op_grant_authority")


def policy(on):
    two_person("POLICY_SET", {"policy": "P-MANIFEST", "enabled": on}, "r6_op_set_policy")


def draft(missing=(), approved=True, intake=None, prepared_by=PEOPLE["a3"], approved_by=PEOPLE["a2"]):
    cid = new_id()
    run("lawdesk_owner", "insert into lawdesk.complaint (id, responsible_user_id, owning_unit_id) values (%s, %s, %s)", cid, PEOPLE["a2"], UNIT1)
    run("lawdesk_owner", "insert into lawdesk.referral_draft values (%s, %s, %s, %s, %s, %s)", cid, list(missing), approved,
        json.dumps(INTAKE if intake is None else intake), prepared_by, approved_by)
    return cid


class Referral(unittest.TestCase):
    actor = PEOPLE["a2"]

    @classmethod
    def setUpClass(cls):
        ensure(cls.actor, "COMPLAINT_APPROVE")
        ensure(cls.actor, "COMPLAINT_VIEW")
        policy(True)
        run("lawdesk_owner", "create table if not exists lawdesk.probe_case_writes (seq bigserial, case_id uuid, op text, confidentiality text)")
        run("lawdesk_owner", "revoke all on lawdesk.probe_case_writes from lawdesk_dml")
        run("lawdesk_owner", "create or replace function lawdesk.probe_log_case() returns trigger language plpgsql security definer"
            " set search_path = pg_catalog, lawdesk, pg_temp as $f$ begin insert into lawdesk.probe_case_writes (case_id, op, confidentiality)"
            " values (new.id, tg_op, new.confidentiality); return new; end $f$")
        run("lawdesk_owner", "drop trigger if exists probe_log_case on lawdesk.legal_case")
        run("lawdesk_owner", "create trigger probe_log_case after insert or update on lawdesk.legal_case for each row execute function lawdesk.probe_log_case()")

    @classmethod
    def tearDownClass(cls):
        run("lawdesk_owner", "drop trigger probe_log_case on lawdesk.legal_case")
        policy(False)

    def open(self, complaint, key=None, actor=None, user="lawdesk_legal"):
        return run(user, OPEN, complaint, actor or self.actor, key or new_id())[0]

    def code(self, complaint, key=None, actor=None):
        return state("lawdesk_legal", OPEN, complaint, actor or self.actor, key or new_id())

    def test_one_new_restricted_case_in_the_callers_transaction(self):
        complaint = draft()
        before = counts()
        c = connect("lawdesk_legal", autocommit=False)
        cur = c.cursor()
        cur.execute(OPEN, (complaint, self.actor, new_id()))
        case, ref, replayed = cur.fetchone()
        cur.execute("select txid_current(), (select xmin::text::bigint from lawdesk.legal_case where id = %s)", (case,))
        txid, xmin = cur.fetchone()
        self.assertEqual(0, one("select count(*) from lawdesk.legal_case where id = '%s'" % case), "visible before commit")
        # the ordinary intake repositories continue on the same connection and the same transaction
        cur.execute("update lawdesk.legal_case set primary_member_id = %s where id = %s", (self.actor, case))
        cur.execute("insert into lawdesk.case_event (case_id, type, summary, created_by) values (%s, 'CASE_CREATED', %s, %s)",
                    (case, "Case " + ref + " created", self.actor))
        c.commit()
        c.close()
        self.assertFalse(replayed)
        self.assertEqual(txid % 2 ** 32, xmin)
        self.assertRegex(ref, r"^LD-\d{4}-\d{5}$")
        self.assertEqual(before[0] + 1, counts()[0])
        self.assertEqual([(case,)], run("lawdesk_owner", "select case_id::text from lawdesk.complaint_case_link where complaint_id = %s", complaint))
        writes = run("lawdesk_owner", "select op, confidentiality from lawdesk.probe_case_writes where case_id = %s order by seq", case)
        self.assertEqual(("INSERT", "RESTRICTED"), writes[0])
        self.assertEqual({"RESTRICTED"}, {w[1] for w in writes}, "the case was NORMAL at some point")
        self.assertEqual("REFERRED", one("select status from lawdesk.complaint where id = '%s'" % complaint))
        self.assertTrue(one("select count(*) > 0 from lawdesk.search_case_doc where case_id = '%s'" % case), "legacy search trigger ran")
        for user in ("lawdesk_app", "lawdesk_legal"):
            self.assertEqual("LD010", state(user, "update lawdesk.legal_case set confidentiality = 'NORMAL' where id = %s", case))

    def test_numbering_shares_the_ordinary_intake_counter(self):
        year = one("select extract(year from (now() at time zone 'UTC'))::int")
        upsert = ("insert into lawdesk.case_reference_counter (year, last_number) values (%s, 1) on conflict (year)"
                  " do update set last_number = case_reference_counter.last_number + 1 returning last_number")   # CaseRepository
        ordinary = run("lawdesk_app", upsert, year)[0][0]
        self.assertEqual("LD-%d-%05d" % (year, ordinary + 1), self.open(draft())[1])
        self.assertEqual(ordinary + 2, run("lawdesk_app", upsert, year)[0][0])

    def test_rollback_after_the_writer_leaves_nothing(self):
        complaint = draft()
        before = counts()
        c = connect("lawdesk_legal", autocommit=False)
        cur = c.cursor()
        cur.execute(OPEN, (complaint, self.actor, new_id()))
        case = cur.fetchone()[0]
        with self.assertRaises(psycopg2.Error):   # a later intake step fails
            cur.execute("insert into lawdesk.case_party (case_id, party_id, role, created_by) values (%s, %s, 'CLIENT', %s)",
                        (case, new_id(), self.actor))
        c.rollback()
        c.close()
        self.assertEqual(before, counts())

    def test_every_refusal_writes_nothing(self):
        incomplete = dict(INTAKE)
        del incomplete["authority_position"]
        cases = {"missing field listed": (draft(missing=["opposing party"]), "LD024"), "manifest unapproved": (draft(approved=False), "LD024"),
                 "intake incomplete": (draft(intake=incomplete), "LD024"), "preparer approved it": (draft(prepared_by=PEOPLE["a2"]), "LD024"),
                 "approver without authority": (draft(approved_by=PEOPLE["a5"]), "LD024"), "unknown complaint": (new_id(), "LD020"),
                 "bad value": (draft(intake=dict(INTAKE, case_type="NOPE")), "23514")}
        before = counts()
        for name, (complaint, expected) in cases.items():
            with self.subTest(name):
                self.assertEqual(expected, self.code(complaint))
        complete = draft()
        before = counts()
        self.assertEqual("LD020", self.code(complete, actor=PEOPLE["a9"]), "actor who cannot see the complaint")
        self.assertEqual("LD024", state("lawdesk_legal", "select * from lawdesk.r6_open_referral_case(%s, %s, null)", complete, self.actor))
        policy(False)
        try:
            self.assertEqual("LD023", self.code(complete))
        finally:
            policy(True)
        for user in ("lawdesk_app", "op_alice"):
            self.assertEqual("42501", state(user, OPEN, complete, self.actor, new_id()), user)
        self.assertEqual(before, counts())

    def test_retry_returns_the_same_case_and_nothing_else_does(self):
        complaint, key = draft(), new_id()
        first = self.open(complaint, key)
        before = counts()
        self.assertEqual((first[0], first[1], True), self.open(complaint, key))
        self.assertEqual("LD021", self.code(complaint), "another key")
        two_person("AUTHORITY_GRANT", grant(PEOPLE["a5"], "COMPLAINT_VIEW"), "r6_op_grant_authority")
        self.assertEqual("LD005", self.code(complaint, key, actor=PEOPLE["a5"]), "same key, another actor without authority")
        self.assertEqual(before, counts())

    def concurrent(self, calls):
        """Both transactions are open at once; the first commits only after the second has had time to block."""
        results = [None, None]
        first = connect("lawdesk_legal", autocommit=False)
        cur = first.cursor()
        cur.execute(OPEN, calls[0])
        results[0] = cur.fetchone()

        def second():
            try:
                results[1] = run("lawdesk_legal", OPEN, *calls[1])[0]
            except psycopg2.Error as e:
                results[1] = e.pgcode

        t = threading.Thread(target=second)
        t.start()
        t.join(0.7)
        blocked = t.is_alive()
        first.commit()
        first.close()
        t.join(10)
        return blocked, results

    def test_concurrent_referrals(self):
        complaint, cases_before = draft(), counts()[0]
        blocked, results = self.concurrent([(complaint, self.actor, new_id()), (complaint, self.actor, new_id())])
        self.assertTrue(blocked)
        self.assertEqual("LD021", results[1])
        self.assertEqual(cases_before + 1, counts()[0])
        complaint, key, cases_before = draft(), new_id(), counts()[0]
        blocked, results = self.concurrent([(complaint, self.actor, key), (complaint, self.actor, key)])
        self.assertEqual((results[0][0], True), (results[1][0], results[1][2]), "duplicate submission: one case")
        self.assertEqual(cases_before + 1, counts()[0])
        blocked, results = self.concurrent([(draft(), self.actor, new_id()), (draft(), self.actor, new_id())])
        self.assertTrue(blocked, "numbering serialises on the counter row")
        self.assertNotEqual(results[0][1], results[1][1])
        self.assertEqual(int(results[0][1][-5:]) + 1, int(results[1][1][-5:]), "gapless")

    def test_the_link_cannot_be_written_or_moved_by_any_application_role(self):
        case = self.open(draft())[0]
        for user in ("lawdesk_app", "lawdesk_legal", "op_alice"):
            for sql in ("update lawdesk.complaint_case_link set case_id = gen_random_uuid()", "delete from lawdesk.complaint_case_link",
                        "insert into lawdesk.complaint_case_link (complaint_id, case_id) values (gen_random_uuid(), gen_random_uuid())"):
                self.assertEqual("42501", state(user, sql), user)
        self.assertEqual("23503", state("lawdesk_app", "update lawdesk.legal_case set id = gen_random_uuid() where id = %s", case))
        self.assertIn(state("lawdesk_app", "delete from lawdesk.legal_case where id = %s", case), ("23503", "23001"))


if __name__ == "__main__":
    unittest.main()
