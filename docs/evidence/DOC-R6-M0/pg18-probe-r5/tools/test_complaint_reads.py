"""QA-R6-M0-002: complaint data is reachable only through owner-owned read functions that apply the visibility rule."""
import unittest

import psycopg2

from common import PEOPLE, new_id, one, run, state
from test_two_party import grant, two_person

UNIT1, UNIT2 = "00000000-0000-0000-0000-0000000000f1", "00000000-0000-0000-0000-0000000000f2"


def user(enabled=True):
    uid = new_id()
    run("lawdesk_owner", "insert into lawdesk.app_user (id, username, email, display_name, password_hash, enabled, created_by, updated_by)"
        " values (%s, %s, %s, 'synthetic', 'x', %s, %s, %s)", uid, "u_" + uid[:18], uid[:18] + "@example.invalid", enabled,
        PEOPLE["a1"], PEOPLE["a1"])
    return uid


def complaint(responsible=None, unit=UNIT1):
    cid = new_id()
    run("lawdesk_owner", "insert into lawdesk.complaint (id, responsible_user_id, owning_unit_id, reference) values (%s, %s, %s, 'SYN')",
        cid, responsible, unit)
    return cid


def view(uid, scope_type="UNIT", scope_ref=UNIT2):
    two_person("AUTHORITY_GRANT", grant(uid, "COMPLAINT_VIEW", scope_type, scope_ref), "r6_op_grant_authority")


def listed(actor):
    return {str(r[0]) for r in run("lawdesk_legal", "select id from lawdesk.r6_complaint_list(%s)", actor)}


def message(sql, *args):
    try:
        run("lawdesk_legal", sql, *args)
    except psycopg2.Error as e:
        return e.pgcode, str(e).splitlines()[0]
    return "OK", ""


class Visibility(unittest.TestCase):
    def test_visibility_matrix(self):
        researcher, listed_user, unit_user, all_user, no_grant, outsider = user(), user(), user(), user(), user(), user()
        c = complaint(responsible=researcher)
        view(researcher)                          # grant scoped to another unit: sees it only as the responsible researcher
        view(listed_user)
        run("lawdesk_owner", "insert into lawdesk.complaint_access values (%s, %s)", c, listed_user)
        view(unit_user, "UNIT", UNIT1)
        view(all_user, "ALL", None)
        view(outsider)                            # grant, but not responsible, not listed, other unit
        run("lawdesk_owner", "insert into lawdesk.complaint_access values (%s, %s)", c, no_grant)  # listed but without the authority
        for actor, expected in ((researcher, True), (listed_user, True), (unit_user, True), (all_user, True),
                                (no_grant, False), (outsider, False), (new_id(), False), (None, False)):
            with self.subTest(actor=actor, expected=expected):
                self.assertEqual(expected, c in listed(actor))

    def test_hidden_and_unknown_are_indistinguishable(self):
        c, nobody = complaint(), user()
        for function in ("r6_complaint_get", "r6_complaint_case", "r6_referral_draft_get"):
            with self.subTest(function=function):
                args = "(%s, %s)"
                hidden = message("select lawdesk." + function + args, nobody, c)
                unknown = message("select lawdesk." + function + args, nobody, new_id())
                self.assertEqual(("LD020", hidden[1]), hidden)
                self.assertEqual(hidden, unknown)
        self.assertEqual([(None,)], run("lawdesk_legal", "select lawdesk.r6_case_complaint(%s, '00000000-0000-0000-0000-0000000000c1')", nobody))

    def test_revoked_and_disabled_actors_see_nothing(self):
        researcher, disabled = user(), user(enabled=False)
        c, d = complaint(responsible=researcher), complaint(responsible=disabled)
        view(researcher)
        view(disabled, "ALL", None)
        self.assertIn(c, listed(researcher))
        self.assertNotIn(d, listed(disabled))
        two_person("AUTHORITY_REVOKE", {"user_id": researcher, "authority": "COMPLAINT_VIEW"}, "r6_op_revoke_authority")
        self.assertNotIn(c, listed(researcher))

    def test_only_the_legal_credential_may_call_the_readers(self):
        for who in ("lawdesk_app", "op_alice"):
            for call in ("r6_complaint_list(null)", "r6_complaint_get(null, null)", "r6_complaint_case(null, null)",
                         "r6_case_complaint(null, null)", "r6_referral_draft_get(null, null)", "r6_complaint_visible(null, null)"):
                with self.subTest(who=who, call=call):
                    self.assertEqual("42501", state(who, "select lawdesk." + call))
        self.assertEqual("42501", state("lawdesk_legal", "select lawdesk.r6_complaint_visible(null, null)"), "membership oracle")


class StatedResidual(unittest.TestCase):
    def test_RESIDUAL_the_actor_id_is_supplied_by_the_caller(self):
        """NOT a protection: documents what remains. A holder of the legal credential can name any authorised user, and can find
        one, because grants are readable. LD-DOC-R6-08 §3 records this for the owner's decision (D-09, PENDING)."""
        researcher = user()
        c = complaint(responsible=researcher)
        view(researcher)
        self.assertTrue(run("lawdesk_legal", "select 1 from lawdesk.legal_authority_grant where user_id = %s", researcher))
        self.assertIn(c, listed(researcher))


if __name__ == "__main__":
    unittest.main()
