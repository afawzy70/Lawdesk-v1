"""Shared contracts for parallel workstreams (architecture/CONTRACTS.md): one permission list on both sides, and
Flyway versions inside the range of the phase that owns the story."""

import re
import sys
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "tools"))

import contracts  # noqa: E402


class PermissionListTest(unittest.TestCase):
    def test_frontend_mirrors_the_backend_permission_enum(self):
        self.assertEqual(contracts.java_permissions(REPO_ROOT), contracts.ts_permissions(REPO_ROOT))
        self.assertIn("CASE_CREATE", contracts.java_permissions(REPO_ROOT))


class MigrationRangeTest(unittest.TestCase):
    def test_the_repository_migrations_follow_the_ranges(self):
        self.assertEqual([], contracts.migration_errors(contracts.migration_names(REPO_ROOT)))

    def test_shared_versions_below_100_need_no_story(self):
        self.assertEqual([], contracts.migration_errors(["V1__app_release.sql", "V2__app_release_version_visible.sql"]))

    def test_each_phase_uses_its_own_range(self):
        good = ["V100__LD-007_app_user.sql", "V200__LD-012_legal_case.sql", "V305__LD-022_judgment.sql",
                "V400__LD-024_hearing.sql", "V499__LD-029_note.sql", "V500__LD-030_report_index.sql",
                "V599__LD-034_export.sql"]
        self.assertEqual([], contracts.migration_errors(good))

    def test_versions_outside_the_story_phase_range_are_refused(self):
        for name in ["V150__LD-012_legal_case.sql", "V250__LD-018_court.sql", "V399__LD-009_roles.sql",
                     "V500__LD-024_hearing.sql", "V450__LD-031_case_report.sql", "V600__LD-035_locale.sql"]:
            with self.subTest(name=name):
                self.assertTrue(contracts.migration_errors([name]), name)

    def test_migrations_of_a_workstream_must_name_their_story(self):
        self.assertTrue(contracts.migration_errors(["V101__users.sql"]))

    def test_a_version_used_twice_is_refused(self):
        errors = contracts.migration_errors(["V200__LD-012_legal_case.sql", "V200__LD-013_case_edit.sql"])
        self.assertTrue(any("200" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
