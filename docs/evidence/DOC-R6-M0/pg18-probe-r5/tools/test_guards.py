"""QA-R6-M0-006: guards decide from OLD and NEW, protect by association, and cannot be walked around."""
import threading
import unittest

from common import PEOPLE, connect, deadline, linked_judgment, new_id, one, row, run, sha, state

EDITABLE = {"notes", "owner_id", "version", "updated_at", "updated_by"}
VALUES = {"uuid": "gen_random_uuid()", "date": "date '2031-01-01'", "integer": "999", "bigint": "999", "jsonb": "'{\"x\":1}'::jsonb",
          "timestamp with time zone": "timestamptz '2031-01-01'", "character varying": "'TEXT'"}


class Deadlines(unittest.TestCase):
    def setUp(self):
        self.case, self.proc, self.judg = linked_judgment()
        self.d = deadline(self.case, self.judg)

    def test_every_column_outside_the_allow_list_is_refused(self):
        columns = run("lawdesk_owner", "select column_name::text, data_type::text from information_schema.columns"
                      " where table_schema = 'lawdesk' and table_name = 'deadline' order by ordinal_position")
        self.assertGreater(len(columns), len(EDITABLE))
        before = row("deadline", self.d)
        for name, kind in columns:
            if name in EDITABLE:
                continue
            with self.subTest(column=name):
                self.assertEqual("LD002", state("lawdesk_app", "update lawdesk.deadline set %s = %s where id = '%s'" % (name, VALUES[kind], self.d)))
        self.assertEqual(before, row("deadline", self.d))

    def test_allowed_columns_stay_editable(self):
        self.assertEqual(1, run("lawdesk_app", "update lawdesk.deadline set notes = 'seen', owner_id = %s, version = version + 1,"
                                " updated_at = now(), updated_by = %s where id = %s", PEOPLE["a1"], PEOPLE["a1"], self.d))

    def test_evidence_association_alone_protects(self):
        d = deadline(self.case, self.judg, pointer=False)
        run("lawdesk_owner", "insert into lawdesk.deadline_calculation_evidence (deadline_id, inputs, output_due) values (%s, '{}', date '2026-08-01')", d)
        run("lawdesk_owner", "update lawdesk.deadline set source_type='TEXT', source_id=null, source_text='moved' where id=%s", d)
        self.assertEqual("LD002", state("lawdesk_app", "update lawdesk.deadline set due_on = date '2031-01-01' where id = %s", d))

    def test_insert_and_move_onto_a_linked_judgment_are_refused(self):
        a1 = PEOPLE["a1"]
        insert = ("insert into lawdesk.deadline (case_id, source_type, source_id, due_on, action, calc_status, created_by, updated_by)"
                  " values (%s, 'JUDGMENT', %s, date '2026-09-01', 'x', 'MANUAL', %s, %s)")
        self.assertEqual("LD002", state("lawdesk_app", insert, self.case, self.judg, a1, a1))
        free = new_id()
        run("lawdesk_app", "insert into lawdesk.deadline (id, case_id, source_type, source_text, due_on, action, calc_status, created_by, updated_by)"
            " values (%s, %s, 'TEXT', 'manual', date '2026-09-01', 'x', 'MANUAL', %s, %s)", free, self.case, a1, a1)
        self.assertEqual("LD002", state("lawdesk_app", "update lawdesk.deadline set source_type='JUDGMENT', source_id=%s, source_text=null"
                                        " where id=%s", self.judg, free))
        self.assertEqual(1, run("lawdesk_app", "update lawdesk.deadline set due_on = date '2026-10-01' where id = %s", free))
        self.assertEqual(1, run("lawdesk_app", "delete from lawdesk.deadline where id = %s", free))

    def test_upsert_cannot_rewrite_a_protected_row(self):
        before = row("deadline", self.d)
        got = state("lawdesk_app", "insert into lawdesk.deadline (id, case_id, source_type, source_text, due_on, action, calc_status, created_by,"
                    " updated_by) values (%s, %s, 'TEXT', 'x', date '2031-01-01', 'x', 'MANUAL', %s, %s)"
                    " on conflict (id) do update set due_on = excluded.due_on", self.d, self.case, PEOPLE["a1"], PEOPLE["a1"])
        self.assertEqual("LD002", got)
        self.assertEqual(before, row("deadline", self.d))

    def test_linked_judgment_pointer_delete_and_unlink(self):
        for sql in ("update lawdesk.judgment set appeal_deadline_id = null where id = %s", "delete from lawdesk.judgment where id = %s",
                    "update lawdesk.judgment set id = gen_random_uuid() where id = %s"):
            self.assertEqual("LD001", state("lawdesk_app", sql, self.judg), sql)
        self.assertEqual("42501", state("lawdesk_app", "delete from lawdesk.judgment_procedural where judgment_id = %s", self.judg))
        self.assertEqual("42501", state("lawdesk_app", "delete from lawdesk.judgment_party_deadline"))
        self.assertEqual(1, run("lawdesk_app", "update lawdesk.judgment set summary = 'legacy edit still allowed' where id = %s", self.judg))

    def test_unlinked_records_behave_as_before(self):
        case, _, judg = linked_judgment(linked=False)
        d = deadline(case, judg)
        self.assertEqual(1, run("lawdesk_app", "update lawdesk.deadline set due_on = date '2031-01-01' where id = %s", d))
        self.assertEqual(1, run("lawdesk_app", "update lawdesk.judgment set final = true, appeal_deadline_id = null where id = %s", judg))
        self.assertEqual(1, run("lawdesk_app", "delete from lawdesk.deadline where id = %s", d))


class ReferentialActions(unittest.TestCase):
    def test_every_referential_write_into_a_guarded_table_is_known(self):
        """A new cascading or SET NULL/DEFAULT key into a guarded table fails here until its guard behaviour is proved."""
        rows = run("lawdesk_owner", "select conrelid::regclass::text, conname::text from pg_constraint where contype = 'f'"
                   " and connamespace = 'lawdesk'::regnamespace and (confdeltype in ('c','n','d') or confupdtype in ('c','n','d'))"
                   " and conrelid in ('lawdesk.deadline'::regclass, 'lawdesk.judgment'::regclass, 'lawdesk.legal_case'::regclass)")
        self.assertEqual([("lawdesk.deadline", "deadline_proof_document_id_fkey")], [(t if "." in t else "lawdesk." + t, c) for t, c in rows])

    def test_a_nested_write_by_the_owner_is_not_a_writer_write(self):
        defs = run("lawdesk_owner", "select proname::text, prosrc from pg_proc where proname in"
                   " ('r6_guard_deadline','r6_guard_judgment','r6_guard_case_confidentiality')")
        self.assertEqual(3, len(defs))
        for name, source in defs:
            self.assertIn("pg_trigger_depth() = 1", source, name)


class Compatibility(unittest.TestCase):
    def test_archive_purge_of_a_proof_document_of_an_unlinked_deadline_still_works(self):
        case, _, judg = linked_judgment(linked=False)
        d, doc = deadline(case, judg, pointer=False), new_id()
        run("lawdesk_owner", "insert into lawdesk.case_document (id, case_id, title, file_name, size_bytes, sha256, created_by)"
            " values (%s, %s, 'proof', 'p.pdf', 10, %s, %s)", doc, case, sha(doc), PEOPLE["a1"])
        run("lawdesk_app", "update lawdesk.deadline set status='COMPLETED', completed_on=date '2026-07-01', proof_document_id=%s where id=%s", doc, d)
        self.assertEqual(1, run("lawdesk_app", "delete from lawdesk.case_document where id=%s"
                                " and set_config('lawdesk.archive_purge','on',true)='on'", doc))
        self.assertTrue(one("select proof_document_id is null from lawdesk.deadline where id = '%s'" % d))


class CatalogAudit(unittest.TestCase):
    def test_security_definer_functions(self):
        rows = run("lawdesk_owner", "select p.proname::text, pg_get_userbyid(p.proowner)::text, coalesce(array_to_string(p.proconfig, ';'), ''),"
                   " p.prosrc, exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a where a.grantee = 0)"
                   " from pg_proc p where p.pronamespace = 'lawdesk'::regnamespace and p.prosecdef and p.proname like 'r6\\_%%'")
        self.assertGreater(len(rows), 20)
        for name, owner, config, source, public in rows:
            with self.subTest(function=name):
                self.assertEqual("lawdesk_owner", owner)
                self.assertIn("search_path=pg_catalog, lawdesk, pg_temp", config)
                self.assertFalse(public, "PUBLIC may execute")
                self.assertNotRegex(source, r"(?i)\bexecute\s|format\(")

    def test_ownership_and_no_application_writes_on_protected_tables(self):
        self.assertEqual(0, one("select count(*) from pg_class where relnamespace = 'lawdesk'::regnamespace and pg_get_userbyid(relowner) <> 'lawdesk_owner'"))
        protected = ["operator_identity", "op_request", "legal_authority_grant", "legal_approval", "owner_policy", "legal_bootstrap",
                     "legal_bootstrap_nominee", "judgment_procedural", "deadline_calculation_evidence", "judgment_party_deadline",
                     "complaint", "complaint_access", "complaint_case_link", "referral_draft", "sensitive_permission"]
        for table in protected:
            for role in ("lawdesk_app", "lawdesk_legal", "lawdesk_dml", "lawdesk_operator"):
                with self.subTest(table=table, role=role):
                    self.assertFalse(one("select has_table_privilege('%s', 'lawdesk.%s', 'insert,update,delete,truncate,trigger,references')" % (role, table)))


class Races(unittest.TestCase):
    def blocked(self, work):
        result = []
        t = threading.Thread(target=lambda: result.append(work()))
        t.start()
        t.join(0.7)
        return t, result

    def test_legacy_insert_waits_for_a_concurrent_registration(self):
        case, _, judg = linked_judgment(linked=False)
        owner = connect("lawdesk_owner", autocommit=False)
        cur = owner.cursor()
        cur.execute("select 1 from lawdesk.judgment where id = %s for update", (judg,))
        cur.execute("insert into lawdesk.judgment_procedural (judgment_id) values (%s)", (judg,))
        t, result = self.blocked(lambda: state("lawdesk_app", "insert into lawdesk.deadline (case_id, source_type, source_id, due_on, action,"
                                               " calc_status, created_by, updated_by) values (%s, 'JUDGMENT', %s, date '2026-09-01', 'x',"
                                               " 'MANUAL', %s, %s)", case, judg, PEOPLE["a1"], PEOPLE["a1"]))
        self.assertTrue(t.is_alive(), "the legacy insert did not wait for the registration")
        owner.commit()
        owner.close()
        t.join(10)
        self.assertEqual(["LD002"], result)

    def test_legacy_update_waits_for_a_concurrent_adoption(self):
        case, _, judg = linked_judgment(linked=False)
        d = deadline(case, judg, pointer=False)
        owner = connect("lawdesk_owner", autocommit=False)
        cur = owner.cursor()
        cur.execute("select 1 from lawdesk.deadline where id = %s for update", (d,))
        cur.execute("insert into lawdesk.judgment_party_deadline (judgment_id, deadline_id, status) values (%s, %s, 'EFFECTIVE')", (judg, d))
        t, result = self.blocked(lambda: state("lawdesk_app", "update lawdesk.deadline set due_on = date '2031-01-01' where id = %s", d))
        self.assertTrue(t.is_alive())
        owner.commit()
        owner.close()
        t.join(10)
        self.assertEqual(["LD002"], result)
        self.assertEqual("2026-08-01", str(one("select due_on from lawdesk.deadline where id = '%s'" % d)))


if __name__ == "__main__":
    unittest.main()
