"""Regression attacks, model-agnostic: each test is an operation that must be REFUSED with protected records unchanged.
They run against the Round-4 model (expected: they fail, the attack succeeds) and the Round-5 model (expected: all pass).
Only legacy tables, catalog facts and the Round-4 entry points are used, so a Round-4 success cannot hide behind a new name."""
import unittest

import psycopg2

from common import PEOPLE, connect, deadline, linked_judgment, new_id, one, row, run, sha, state

REFUSED_BY_PRIVILEGE = ("42501", "42883")  # no privilege, or the single-person entry point no longer exists


class QaFinding001SingleOperator(unittest.TestCase):
    def refused(self, sql, probe, *args):
        before = one(probe)
        got = state("op_alice", sql, *args)
        self.assertNotEqual("OK", got, "one operator alone performed a legal-effect operation: " + sql)
        self.assertEqual(before, one(probe), "protected state changed")

    def test_policy_by_one_operator(self):
        self.refused("select lawdesk.r6_op_set_policy('P-DECLASS', true)",
                     "select md5(string_agg(policy || enabled, ',' order by policy)) from lawdesk.owner_policy")

    def test_authority_grant_by_one_operator(self):
        self.refused("select lawdesk.r6_op_grant_authority('00000000-0000-0000-0000-0000000000a5','COMPLAINT_APPROVE')",
                     "select count(*) from lawdesk.legal_authority_grant")

    def test_gate_approval_by_one_operator(self):
        self.refused("select lawdesk.r6_op_record_legal_approval('G-FINAL', 1)", "select count(*) from lawdesk.legal_approval")

    def test_every_operator_function_beyond_request_handling_takes_only_a_request_id(self):
        rows = run("lawdesk_owner", "select p.proname, pg_get_function_identity_arguments(p.oid) from pg_proc p"
                   " where p.pronamespace = 'lawdesk'::regnamespace and p.prosecdef and has_function_privilege('lawdesk_operator', p.oid, 'execute')"
                   " and p.proname not in ('r6_op_request', 'r6_op_confirm', 'r6_op_cancel', 'r6_op_list', 'r6_op_facts', 'r6_case_is_complaint_linked')")
        bad = [(n, a) for n, a in rows if a != "p_request uuid"]
        self.assertTrue(rows, "no operator executor found")
        self.assertEqual([], bad, "operator functions that do not execute a confirmed request")

    def test_shared_operator_group_cannot_log_in(self):
        self.assertFalse(one("select rolcanlogin from pg_roles where rolname = 'lawdesk_operator'"))


class QaFinding002ComplaintReads(unittest.TestCase):
    def test_legal_credential_cannot_select_confidential_tables(self):
        for table in ("complaint", "complaint_case_link", "referral_draft"):
            with self.subTest(table=table):
                self.assertEqual("42501", state("lawdesk_legal", "select * from lawdesk." + table))

    def test_runtime_credential_cannot_select_confidential_tables(self):
        for table in ("complaint", "complaint_case_link", "referral_draft"):
            with self.subTest(table=table):
                self.assertEqual("42501", state("lawdesk_app", "select * from lawdesk." + table))

    def test_no_column_or_table_select_on_any_confidential_table(self):
        rows = run("lawdesk_owner", "select r.rolname, c.relname from pg_class c, pg_roles r where c.relnamespace = 'lawdesk'::regnamespace"
                   " and c.relkind in ('r','v','m') and (c.relname like 'complaint%%' or c.relname = 'referral_draft')"
                   " and r.rolname in ('lawdesk_app','lawdesk_legal','lawdesk_dml','lawdesk_operator')"
                   " and (has_table_privilege(r.rolname, c.oid, 'select') or has_any_column_privilege(r.rolname, c.oid, 'select'))")
        self.assertEqual([], rows)


class QaFinding006DeadlineGuard(unittest.TestCase):
    def setUp(self):
        self.case, self.proc, self.judg = linked_judgment()
        self.d = deadline(self.case, self.judg)

    def assert_refused(self, sql, *args, user="lawdesk_app"):
        before = row("deadline", self.d)
        got = state(user, sql, *args)
        self.assertEqual(before, row("deadline", self.d), "protected deadline changed (%s)" % got)
        self.assertNotEqual("OK", got)

    def test_qa_rewrite_to_text_source_and_new_due_date(self):
        for user in ("lawdesk_app", "lawdesk_legal"):
            self.assert_refused("update lawdesk.deadline set source_type='TEXT', source_id=null, source_text='x',"
                                " due_on=date '2031-01-01' where id=%s", self.d, user=user)

    def test_qa_change_source_type(self):
        self.assert_refused("update lawdesk.deadline set source_type='HEARING', source_id=%s where id=%s", new_id(), self.d)

    def test_qa_change_source_id(self):
        self.assert_refused("update lawdesk.deadline set source_id=gen_random_uuid(), due_on=date '2031-01-01' where id=%s", self.d)

    def test_two_statements_in_one_transaction(self):
        c = connect("lawdesk_app", autocommit=False)
        before = row("deadline", self.d)
        try:
            cur = c.cursor()
            cur.execute("update lawdesk.deadline set source_type='TEXT', source_id=null, source_text='x' where id=%s", (self.d,))
            cur.execute("update lawdesk.deadline set due_on=date '2031-01-01' where id=%s", (self.d,))
            c.commit()
        except psycopg2.Error:
            c.rollback()
        finally:
            c.close()
        self.assertEqual(before, row("deadline", self.d))

    def test_protected_through_judgment_pointer_only(self):
        run("lawdesk_owner", "update lawdesk.deadline set source_type='TEXT', source_id=null, source_text='moved' where id=%s", self.d)
        self.assert_refused("update lawdesk.deadline set due_on=date '2031-01-01' where id=%s", self.d)
        self.assert_refused("delete from lawdesk.deadline where id=%s", self.d)

    def test_protected_through_party_deadline_association_only(self):
        d = deadline(self.case, self.judg, pointer=False)
        run("lawdesk_owner", "insert into lawdesk.judgment_party_deadline (judgment_id, deadline_id, status) values (%s, %s, 'EFFECTIVE')",
            self.judg, d)
        run("lawdesk_owner", "update lawdesk.deadline set source_type='TEXT', source_id=null, source_text='moved' where id=%s", d)
        self.d = d
        self.assert_refused("update lawdesk.deadline set due_on=date '2031-01-01' where id=%s", d)

    def test_referential_action_cannot_erase_completion_proof(self):
        doc = new_id()
        run("lawdesk_owner", "insert into lawdesk.case_document (id, case_id, title, file_name, size_bytes, sha256, created_by)"
            " values (%s, %s, 'proof', 'p.pdf', 10, %s, %s)", doc, self.case, sha(doc), PEOPLE["a1"])
        run("lawdesk_owner", "update lawdesk.deadline set status='COMPLETED', completed_on=date '2026-07-01', proof_document_id=%s"
            " where id=%s", doc, self.d)
        self.assert_refused("delete from lawdesk.case_document where id=%s"
                            " and set_config('lawdesk.archive_purge','on',true)='on'", doc)

    def test_linked_judgment_cannot_be_moved_to_another_proceeding(self):
        _, other_proc, other_judg = linked_judgment(linked=False)
        run("lawdesk_owner", "delete from lawdesk.judgment where id=%s", other_judg)
        before = row("judgment", self.judg)
        got = state("lawdesk_app", "update lawdesk.judgment set proceeding_id=%s where id=%s", other_proc, self.judg)
        self.assertEqual(before, row("judgment", self.judg), got)


class QaFinding009ReferralProvenance(unittest.TestCase):
    def test_pre_existing_case_cannot_be_linked_by_forging_created_at(self):
        old, complaint, a1 = new_id(), new_id(), PEOPLE["a1"]
        run("lawdesk_app", "insert into lawdesk.legal_case (id, reference_no, title, case_type, opened_on, created_by, updated_by,"
            " created_at) values (%s, %s, 'PRE-EXISTING', 'CIVIL', date '2020-01-01', %s, %s, now() - interval '400 days')",
            old, "LD-OLD-" + old[:13], a1, a1)
        run("lawdesk_owner", "insert into lawdesk.complaint (id) values (%s)", complaint)
        run("lawdesk_owner", "insert into lawdesk.referral_draft values (%s, '{}', true)", complaint)
        run("lawdesk_owner", "update lawdesk.owner_policy set enabled = true where policy = 'P-MANIFEST'")
        # every other precondition of a referral is satisfied, so that only the case provenance can refuse it
        granted = run("lawdesk_owner", "insert into lawdesk.legal_authority_grant (user_id, authority) values (%s, 'COMPLAINT_APPROVE')"
                      " returning id::text", PEOPLE["a2"])[0][0]
        self.addCleanup(run, "lawdesk_owner", "delete from lawdesk.legal_authority_grant where id = %s", granted)
        c = connect("lawdesk_legal", autocommit=False)
        try:
            cur = c.cursor()
            cur.execute("update lawdesk.legal_case set confidentiality='RESTRICTED', created_at=transaction_timestamp() where id=%s", (old,))
            cur.execute("select lawdesk.r6_complete_referral(%s, %s, %s)", (complaint, old, PEOPLE["a2"]))
            c.commit()
        except psycopg2.Error:
            c.rollback()
        finally:
            c.close()
            run("lawdesk_owner", "update lawdesk.owner_policy set enabled = false where policy = 'P-MANIFEST'")
        self.assertEqual(0, one("select count(*) from lawdesk.complaint_case_link where case_id = '%s'" % old))

    def test_no_application_function_accepts_a_case_id_and_writes_the_link(self):
        rows = run("lawdesk_owner", "select p.proname, pg_get_function_identity_arguments(p.oid) from pg_proc p"
                   " where p.pronamespace = 'lawdesk'::regnamespace and p.prosrc ~* 'insert into lawdesk.complaint_case_link'"
                   " and (has_function_privilege('lawdesk_legal', p.oid, 'execute') or has_function_privilege('lawdesk_app', p.oid, 'execute'))")
        self.assertTrue(rows, "no referral writer found")
        self.assertEqual([], [r for r in rows if "p_case" in r[1]])


if __name__ == "__main__":
    unittest.main()
