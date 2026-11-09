"""LD-DOC restore drill: the guards that keep it away from every database but its own throwaway cluster, and the pure helpers."""

import argparse
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

DRILL_DIR = Path(__file__).resolve().parents[1] / "restore-drill"
sys.path.insert(0, str(DRILL_DIR))

import restore_drill  # noqa: E402


def namespace(workdir, **extra):
    values = dict(workdir=str(workdir), port=45678, app_port=45679, server_bin="/x", client_bin="/y", server_lib="", client_lib="",
                  jar="/z.jar", java="java", scale=1, locale="C.UTF-8", app_timeout=1, download_sample=1, skip_api=False,
                  evidence="", keep=False)
    values.update(extra)
    return argparse.Namespace(**values)


class DatabaseNameTest(unittest.TestCase):
    def test_accepts_only_drill_names(self):
        for name in ("lawdesk_drill_src", "lawdesk_drill_restored_j", "lawdesk_drill_a1"):
            self.assertEqual(restore_drill.check_database_name(name), name)

    def test_refuses_everything_else(self):
        for name in ("lawdesk", "postgres", "lawdesk_staging", "lawdesk_drill_", "LAWDESK_DRILL_X", "lawdesk_drill_x;drop", "lawdesk-drill-x",
                     "lawdesk_drill_" + "a" * 40, "x_lawdesk_drill_src", ""):
            with self.subTest(name=name), self.assertRaises(restore_drill.Refused):
                restore_drill.check_database_name(name)


class EnvironmentTest(unittest.TestCase):
    def test_refuses_inherited_connection_settings(self):
        for key in ("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_PASSWORD", "PGHOST", "PGPASSWORD", "PGSERVICE", "DATABASE_URL"):
            with self.subTest(key=key), self.assertRaises(restore_drill.Refused):
                restore_drill.check_environment({key: "anything", "PATH": "/usr/bin"})

    def test_accepts_a_clean_environment(self):
        restore_drill.check_environment({"PATH": "/usr/bin", "HOME": "/home/x", "JAVA_HOME": "/jdk"})


class PortTest(unittest.TestCase):
    def test_refuses_the_well_known_database_ports_and_low_ports(self):
        for port in (5432, 5433, 6432, 80, 1023, 70000):
            with self.subTest(port=port), self.assertRaises(restore_drill.Refused):
                restore_drill.check_port(port)

    def test_zero_picks_a_free_high_port(self):
        port = restore_drill.check_port(0)
        self.assertGreaterEqual(port, 1024)
        self.assertNotIn(port, (5432, 5433, 6432))


class WorkdirTest(unittest.TestCase):
    def test_accepts_a_new_or_empty_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(restore_drill.check_workdir(Path(tmp) / "new"), (Path(tmp) / "new").resolve())
            (Path(tmp) / "empty").mkdir()
            restore_drill.check_workdir(Path(tmp) / "empty")

    def test_refuses_a_directory_the_drill_did_not_create(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "keep.txt").write_text("someone's files")
            with self.assertRaises(restore_drill.Refused):
                restore_drill.check_workdir(tmp)

    def test_accepts_its_own_earlier_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / restore_drill.MARKER).write_text("")
            (Path(tmp) / "pgdata").mkdir()
            (Path(tmp) / "pgdata" / "PG_VERSION").write_text("18")
            (Path(tmp) / "pgdata" / restore_drill.MARKER).write_text("")
            restore_drill.check_workdir(tmp)

    def test_refuses_a_foreign_data_directory_even_inside_a_marked_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / restore_drill.MARKER).write_text("")
            (Path(tmp) / "pgdata").mkdir()
            (Path(tmp) / "pgdata" / "PG_VERSION").write_text("18")
            with self.assertRaises(restore_drill.Refused):
                restore_drill.check_workdir(tmp)

    def test_refuses_broad_locations(self):
        for path in ("/", str(Path.home()), str(restore_drill.REPO), "/tmp"):
            with self.subTest(path=path), self.assertRaises(restore_drill.Refused):
                restore_drill.check_workdir(path)

    def test_loopback_only(self):
        restore_drill.check_loopback("127.0.0.1")
        for host in ("167.86.90.174", "db", "lawdesk.167-86-90-174.sslip.io", "0.0.0.0"):
            with self.subTest(host=host), self.assertRaises(restore_drill.Refused):
                restore_drill.check_loopback(host)


class OwnClusterTest(unittest.TestCase):
    def drill(self, tmp):
        return restore_drill.Drill(namespace(Path(tmp) / "work"))

    def test_creating_or_dropping_a_database_requires_the_drill_data_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            drill = self.drill(tmp)
            with mock.patch.object(drill, "scalar", return_value="/var/lib/postgresql/data"):
                with self.assertRaises(restore_drill.Refused):
                    drill.assert_own_cluster("lawdesk_drill_src")
                with self.assertRaises(restore_drill.Refused):
                    drill.create_database("lawdesk_drill_src")
            with mock.patch.object(drill, "scalar", return_value=str(drill.pgdata)):
                drill.assert_own_cluster("lawdesk_drill_src")

    def test_a_production_name_is_refused_before_any_connection(self):
        with tempfile.TemporaryDirectory() as tmp:
            drill = self.drill(tmp)
            with mock.patch.object(drill, "scalar") as scalar:
                with self.assertRaises(restore_drill.Refused):
                    drill.create_database("lawdesk")
                scalar.assert_not_called()


class RoleScriptTest(unittest.TestCase):
    def test_reuses_the_deployed_script_with_only_the_name_and_the_password_replaced(self):
        with tempfile.TemporaryDirectory() as tmp:
            drill = restore_drill.Drill(namespace(Path(tmp) / "work"))
            drill.work.mkdir(parents=True)
            path = drill.role_script("lawdesk_drill_src")
            text = path.read_text()
            self.assertNotIn("/run/secrets", text)
            self.assertIn(drill.app_password, text)
            self.assertEqual(oct(path.stat().st_mode & 0o777), "0o600")
            original = (restore_drill.REPO / "deploy" / "postgres" / "app-role.sql").read_text()
            # Every statement of the original is still there once the substitutions are undone.
            undone = (text.replace("lawdesk_drill_src", "lawdesk").replace("'%s'" % drill.app_password, "`cat /run/secrets/db_app_password`"))
            self.assertEqual(undone.replace("\\set app_password `cat /run/secrets/db_app_password`", "").split(), original.replace(
                "\\set app_password `cat /run/secrets/db_app_password`", "").split())
            self.assertIn("REVOKE ALL ON SCHEMA public FROM PUBLIC", text)
            self.assertIn("ALTER ROLE lawdesk_app SET search_path = lawdesk", text)


class CompareTest(unittest.TestCase):
    def test_reports_every_difference_and_nothing_for_equal_values(self):
        same = {"a": {"rows": 3, "digest": "x"}, "b": [1, 2]}
        self.assertEqual(restore_drill.compare("x", same, {"a": {"rows": 3, "digest": "x"}, "b": [1, 2]}), [])
        diffs = restore_drill.compare("x", same, {"a": {"rows": 4, "digest": "x"}, "c": 1})
        self.assertEqual(len(diffs), 3)
        self.assertTrue(any("a.rows" in d for d in diffs))
        self.assertTrue(any("only in before" in d for d in diffs))
        self.assertTrue(any("only in after" in d for d in diffs))


class MainRefusalTest(unittest.TestCase):
    def test_exit_code_two_when_the_environment_points_at_a_database(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {"SPRING_DATASOURCE_URL": "jdbc:postgresql://db/lawdesk"}):
            code = restore_drill.main(["--workdir", str(Path(tmp) / "w"), "--server-bin", "/x", "--client-bin", "/y", "--jar", "/z.jar"])
            self.assertEqual(code, 2)
            self.assertFalse((Path(tmp) / "w").exists())

    def test_exit_code_two_for_a_non_empty_foreign_workdir(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "file").write_text("x")
            with mock.patch.dict(os.environ, {}, clear=False):
                for key in restore_drill.FORBIDDEN_ENV:
                    os.environ.pop(key, None)
                code = restore_drill.main(["--workdir", tmp, "--server-bin", "/x", "--client-bin", "/y", "--jar", "/z.jar"])
            self.assertEqual(code, 2)


if __name__ == "__main__":
    unittest.main()
