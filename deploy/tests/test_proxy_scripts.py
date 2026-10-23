"""Shared-proxy route for LawDesk (attach.sh / detach.sh), driven through a fake `docker` and `curl`.

The proxy belongs to Matbakhy. The owner allowed one narrowly scoped change: back up and validate the live Caddyfile,
append only the LawDesk site, join the proxy to `lawdesk-edge`, and reload in place. Never stop, restart or recreate
the container, and never alter an existing Matbakhy line. These tests hold the scripts to exactly that.
"""

import json
import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

PROXY_DIR = Path(__file__).resolve().parents[1] / "proxy"
ATTACH = PROXY_DIR / "attach.sh"
DETACH = PROXY_DIR / "detach.sh"
HOST = "lawdesk.167-86-90-174.sslip.io"

ORIGINAL = textwrap.dedent("""\
    {
    \temail {$ACME_EMAIL}
    }

    app.{$PREVIEW_DOMAIN} {
    \trespond "matbakhy app"
    }

    api.{$PREVIEW_DOMAIN} {
    \treverse_proxy api:8080
    }
    """)

# State lives in FAKE_ROOT: container/<path> is the container filesystem, networks.json the attached networks,
# calls.log every docker call, statuses.json the HTTP status curl reports per host (optionally per phase).
FAKE_DOCKER = textwrap.dedent("""\
    #!/usr/bin/env python3
    import json, os, shutil, sys
    root = os.environ["FAKE_ROOT"]
    args = sys.argv[1:]
    with open(os.path.join(root, "calls.log"), "a") as f:
        f.write(" ".join(args).replace("\\n", " ") + "\\n")
    def path_in(p):
        return os.path.join(root, "container", p.lstrip("/"))
    nets_file = os.path.join(root, "networks.json")
    nets = json.load(open(nets_file))
    if args[:1] == ["ps"]:
        for c in os.environ.get("FAKE_CONTAINERS", "c0ffee").split():
            print(c)
    elif args[:1] == ["cp"]:
        src, dst = args[1], args[2]
        if src.startswith("c0ffee:"):
            shutil.copyfile(path_in(src.split(":", 1)[1]), dst)
        else:
            if os.environ.get("FAKE_FAIL_CP_TO") and dst.endswith(os.environ["FAKE_FAIL_CP_TO"]):
                count = os.path.join(root, "failed-cp")
                failures = len(open(count).read()) if os.path.exists(count) else 0
                if failures < int(os.environ.get("FAKE_FAIL_CP_TIMES", "1")):
                    open(count, "a").write("x")
                    sys.exit(1)
            shutil.copyfile(src, path_in(dst.split(":", 1)[1]))
    elif args[:2] == ["network", "connect"]:
        nets.append(args[2]); json.dump(nets, open(nets_file, "w"))
    elif args[:2] == ["network", "disconnect"]:
        nets.remove(args[2]); json.dump(nets, open(nets_file, "w"))
    elif args[:1] == ["inspect"]:
        print(" ".join(nets) if "Networks" in " ".join(args) else "{}")
    elif args[:2] == ["network", "inspect"]:
        sys.exit(0 if os.environ.get("FAKE_EDGE_EXISTS", "1") == "1" else 1)
    elif args[:1] == ["exec"]:
        rest = args[2:]
        if rest[:2] == ["printenv", "PREVIEW_DOMAIN"]:
            print("167-86-90-174.sslip.io")
        elif rest[:2] == ["caddy", "validate"]:
            config = open(path_in(rest[rest.index("--config") + 1])).read()
            sys.exit(1 if "INVALID" in config or os.environ.get("FAKE_FAIL_VALIDATE") else 0)
        elif rest[:2] == ["caddy", "adapt"]:
            # Like Caddy: one route per site block, its addresses in match[].host, env placeholders expanded.
            config = open(path_in(rest[rest.index("--config") + 1])).read()
            routes, depth = [], 0
            for line in config.splitlines():
                text = line.split("#", 1)[0].strip()
                if depth == 0 and text.endswith("{") and text != "{" and not text.startswith("("):  # snippets
                    hosts = text[:-1].replace("{$PREVIEW_DOMAIN}", "167-86-90-174.sslip.io").replace(",", " ").split()
                    routes.append({"match": [{"host": hosts}], "handle": [{"handler": "subroute"}]})
                depth += text.count("{") - text.count("}")
            print(json.dumps({"apps": {"http": {"servers": {"srv0": {"listen": [":443"], "routes": routes}}}}}))
        elif rest[:2] == ["caddy", "reload"]:
            if os.environ.get("FAKE_FAIL_RELOAD"):
                sys.exit(1)
            shutil.copyfile(path_in(rest[rest.index("--config") + 1]), os.path.join(root, "active.caddy"))
            open(os.path.join(root, "reloads"), "a").write("x")
        else:
            sys.exit(3)
    else:
        sys.exit(3)
    """)

FAKE_CURL = textwrap.dedent("""\
    #!/usr/bin/env python3
    import json, os, sys
    root = os.environ["FAKE_ROOT"]
    args = sys.argv[1:]
    url = args[-1]
    host = url.split("/")[2]
    statuses = json.load(open(os.path.join(root, "statuses.json")))
    reloads = len(open(os.path.join(root, "reloads")).read()) if os.path.exists(os.path.join(root, "reloads")) else 0
    value = statuses.get(host, "200")
    if isinstance(value, list):
        value = value[min(reloads, len(value) - 1)]
    sys.stdout.write(value)
    """)


class ProxyTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        caddy_dir = self.root / "container" / "etc" / "caddy"
        caddy_dir.mkdir(parents=True)
        (caddy_dir / "Caddyfile").write_text(ORIGINAL, encoding="utf-8")
        (self.root / "networks.json").write_text(json.dumps(["matbakhy-preview_edge"]), encoding="utf-8")
        (self.root / "statuses.json").write_text("{}", encoding="utf-8")
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name, body in [("docker", FAKE_DOCKER), ("curl", FAKE_CURL)]:
            (bin_dir / name).write_text(body, encoding="utf-8")
            (bin_dir / name).chmod(0o755)
        self.backups = self.root / "proxy-backups"
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": str(self.root), "FAKE_ROOT": str(self.root),
                    "LAWDESK_PROXY_BACKUP_DIR": str(self.backups)}

    def tearDown(self):
        self.tmp.cleanup()

    def run_script(self, script, *args, **extra):
        return subprocess.run(["bash", str(script), *args], env={**self.env, **extra}, capture_output=True,
                              text=True, check=False, timeout=60)

    def live(self):
        return (self.root / "container" / "etc" / "caddy" / "Caddyfile").read_text(encoding="utf-8")

    def active(self):
        path = self.root / "active.caddy"
        return path.read_text(encoding="utf-8") if path.exists() else None

    def networks(self):
        return json.loads((self.root / "networks.json").read_text(encoding="utf-8"))

    def calls(self):
        path = self.root / "calls.log"
        return path.read_text(encoding="utf-8").splitlines() if path.exists() else []

    def set_live(self, text):
        (self.root / "container" / "etc" / "caddy" / "Caddyfile").write_text(text, encoding="utf-8")

    def set_statuses(self, statuses):
        (self.root / "statuses.json").write_text(json.dumps(statuses), encoding="utf-8")


class AttachTest(ProxyTestCase):
    def test_appends_only_the_lawdesk_site_and_keeps_every_matbakhy_line(self):
        result = self.run_script(ATTACH)

        self.assertEqual(0, result.returncode, result.stderr)
        live = self.live()
        self.assertTrue(live.startswith(ORIGINAL), live)
        added = live[len(ORIGINAL):]
        self.assertIn("# BEGIN lawdesk-staging", added)
        self.assertIn(f"{HOST} {{", added)
        self.assertIn("reverse_proxy lawdesk-web:8080", added)
        self.assertIn("# END lawdesk-staging", added)
        self.assertEqual(live, self.active(), "the running config must be the persisted one")

    def test_backs_up_the_live_caddyfile_before_any_change(self):
        self.run_script(ATTACH)

        backups = list(self.backups.glob("*/Caddyfile"))
        self.assertEqual(1, len(backups))
        self.assertEqual(ORIGINAL, backups[0].read_text(encoding="utf-8"))
        calls = self.calls()
        first_change = next(i for i, c in enumerate(calls) if c.startswith(("network connect", "exec c0ffee caddy")))
        backup_copy = next(i for i, c in enumerate(calls) if c.startswith("cp c0ffee:/etc/caddy/Caddyfile "))
        self.assertLess(backup_copy, first_change)

    def test_validates_the_candidate_before_reloading_and_joins_the_edge_network(self):
        self.run_script(ATTACH)

        calls = self.calls()
        validate = next(i for i, c in enumerate(calls) if "caddy validate" in c)
        reload = next(i for i, c in enumerate(calls) if "caddy reload" in c)
        self.assertLess(validate, reload)
        self.assertIn("lawdesk-edge", self.networks())

    def test_never_restarts_stops_or_recreates_the_proxy(self):
        self.run_script(ATTACH)
        self.run_script(DETACH)

        for call in self.calls():
            with self.subTest(call=call):
                words = call.split()
                self.assertIn(words[0], {"ps", "cp", "exec", "inspect", "network"})
                if words[0] == "network":
                    self.assertIn(words[1], {"connect", "disconnect", "inspect"})
                    self.assertEqual("lawdesk-edge", words[2])
                if words[0] == "exec":
                    self.assertIn(words[2:4], [["printenv", "PREVIEW_DOMAIN"], ["caddy", "validate"],
                                               ["caddy", "adapt"], ["caddy", "reload"]])
        for script in [ATTACH, DETACH]:
            text = script.read_text(encoding="utf-8")
            for forbidden in ["docker compose", "docker restart", "docker stop", "docker rm", "--force"]:
                with self.subTest(script=script.name, forbidden=forbidden):
                    self.assertNotIn(forbidden, text)

    def test_running_twice_keeps_a_single_lawdesk_site(self):
        self.run_script(ATTACH)
        result = self.run_script(ATTACH)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, self.live().count("# BEGIN lawdesk-staging"))
        self.assertTrue(self.live().startswith(ORIGINAL))
        self.assertEqual(1, self.networks().count("lawdesk-edge"))

    def test_invalid_candidate_changes_nothing(self):
        result = self.run_script(ATTACH, FAKE_FAIL_VALIDATE="1")

        self.assertEqual(1, result.returncode)
        self.assertIn("validation failed", result.stderr)
        self.assertEqual(ORIGINAL, self.live())
        self.assertIsNone(self.active())
        self.assertNotIn("lawdesk-edge", self.networks())

    def test_failed_reload_keeps_the_old_config_and_disconnects(self):
        result = self.run_script(ATTACH, FAKE_FAIL_RELOAD="1")

        self.assertEqual(1, result.returncode)
        self.assertEqual(ORIGINAL, self.live())
        self.assertNotIn("lawdesk-edge", self.networks())

    def test_matbakhy_hosts_answering_differently_after_reload_trigger_automatic_restore(self):
        self.set_statuses({"app.167-86-90-174.sslip.io": ["200", "502"]})

        result = self.run_script(ATTACH)

        self.assertEqual(1, result.returncode)
        self.assertIn("app.167-86-90-174.sslip.io", result.stderr)
        self.assertEqual(ORIGINAL, self.live())
        self.assertEqual(ORIGINAL, self.active())
        self.assertNotIn("lawdesk-edge", self.networks())

    def test_checks_every_matbakhy_host_before_and_after(self):
        self.run_script(ATTACH)

        statuses = self.root / "statuses.json"
        self.assertTrue(statuses.exists())
        output = self.run_script(ATTACH).stdout
        for host in ["app.167-86-90-174.sslip.io", "api.167-86-90-174.sslip.io"]:
            self.assertIn(host, output)

    def test_refuses_when_the_proxy_container_is_ambiguous_or_missing(self):
        for containers in ["", "c0ffee deadbeef"]:
            with self.subTest(containers=containers):
                result = self.run_script(ATTACH, FAKE_CONTAINERS=containers)
                self.assertEqual(2, result.returncode)
                self.assertEqual(ORIGINAL, self.live())

    def test_refuses_without_the_lawdesk_edge_network(self):
        result = self.run_script(ATTACH, FAKE_EDGE_EXISTS="0")

        self.assertEqual(2, result.returncode)
        self.assertIn("lawdesk-edge", result.stderr)
        self.assertEqual(ORIGINAL, self.live())

    def test_rejects_unsafe_hostname_and_allow_list_values(self):
        for extra in [{"LAWDESK_HOSTNAME": "evil.example.com {\n respond 1"}, {"LAWDESK_HOSTNAME": "app.x"},
                      {"LAWDESK_ALLOWED_IPS": "1.2.3.4 }"}]:
            with self.subTest(extra=extra):
                result = self.run_script(ATTACH, **extra)
                self.assertEqual(2, result.returncode)
                self.assertEqual(ORIGINAL, self.live())

    def test_failed_persistence_after_a_successful_reload_restores_the_running_configuration(self):
        # QAD-019: the reload made the candidate active, then writing it to the live path failed.
        result = self.run_script(ATTACH, FAKE_FAIL_CP_TO="/etc/caddy/Caddyfile")

        self.assertEqual(1, result.returncode)
        self.assertEqual(ORIGINAL, self.live())
        self.assertEqual(ORIGINAL, self.active(), "the proxy must not keep running the unpersisted candidate")
        self.assertNotIn("lawdesk-edge", self.networks())
        self.assertNotIn("RESTORE FAILED", result.stderr)

    def test_unbalanced_or_repeated_markers_are_refused_before_any_change(self):
        # QAD-020: a BEGIN without END would make strip_block drop every later site.
        tail = "\napi2.example.invalid {\n\trespond \"later\"\n}\n"
        for label, marked in {
            "begin without end": ORIGINAL + "# BEGIN lawdesk-staging\n" + tail,
            "end without begin": ORIGINAL + "# END lawdesk-staging\n" + tail,
            "end before begin": ORIGINAL + "# END lawdesk-staging\n# BEGIN lawdesk-staging\n" + tail,
            "two blocks": ORIGINAL + ("# BEGIN lawdesk-staging\n# END lawdesk-staging\n" * 2) + tail,
            "indented marker": ORIGINAL + "  # BEGIN lawdesk-staging\n" + tail,
        }.items():
            with self.subTest(label):
                (self.root / "calls.log").unlink(missing_ok=True)
                self.set_live(marked)

                result = self.run_script(ATTACH)

                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertIn("marker", result.stderr)
                self.assertEqual(marked, self.live())
                self.assertIsNone(self.active())
                self.assertFalse(any(c.startswith(("network connect", "exec c0ffee caddy reload")) for c in self.calls()))

    def test_literal_host_sites_are_part_of_the_baseline(self):
        self.set_live(ORIGINAL + "\nstatus.example.invalid {\n\trespond \"ok\"\n}\n")
        self.set_statuses({"status.example.invalid": ["200", "502"]})

        result = self.run_script(ATTACH)

        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn("status.example.invalid", result.stderr)
        self.assertNotIn("# BEGIN lawdesk-staging", self.active())

    def test_a_foreign_site_inside_the_lawdesk_markers_is_never_removed(self):
        # Belt and braces behind the marker check: only LawDesk hosts may disappear, and the adapted candidate must
        # serve every remaining host plus LawDesk.
        marked = (ORIGINAL + "\n# BEGIN lawdesk-staging\nlawdesk.old.invalid {\n}\nshop.example.invalid {\n}\n"
                  "# END lawdesk-staging\n")
        self.set_live(marked)

        result = self.run_script(ATTACH)

        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("shop.example.invalid", result.stderr)
        self.assertEqual(marked, self.live())
        self.assertIsNone(self.active())

    def test_an_earlier_lawdesk_site_is_replaced(self):
        self.set_live(ORIGINAL + "# BEGIN lawdesk-staging\nlawdesk.old.invalid {\n}\n# END lawdesk-staging\n")

        result = self.run_script(ATTACH)

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("lawdesk.old.invalid", self.live())
        self.assertIn(f"{HOST} {{", self.active())

    def test_refuses_without_a_complete_matbakhy_baseline(self):
        for label, statuses in {"unreachable host": {"api.167-86-90-174.sslip.io": "000"}}.items():
            with self.subTest(label):
                self.set_statuses(statuses)
                result = self.run_script(ATTACH)
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertIn("baseline", result.stderr)
                self.assertIsNone(self.active())
        self.set_statuses({})
        self.set_live("{\n\temail x@example.invalid\n}\n")
        result = self.run_script(ATTACH)
        self.assertEqual(2, result.returncode)
        self.assertIn("baseline", result.stderr)

    def test_optional_allow_list_restricts_the_lawdesk_site(self):
        result = self.run_script(ATTACH, LAWDESK_ALLOWED_IPS="203.0.113.7/32 2001:db8::/32")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("not remote_ip 203.0.113.7/32 2001:db8::/32", self.live())


class DetachTest(ProxyTestCase):
    def test_restores_the_backup_reloads_and_leaves_the_edge_network(self):
        self.run_script(ATTACH)

        result = self.run_script(DETACH)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(ORIGINAL, self.live())
        self.assertEqual(ORIGINAL, self.active())
        self.assertNotIn("lawdesk-edge", self.networks())

    def test_removes_only_the_lawdesk_block_when_the_live_file_changed_since(self):
        self.run_script(ATTACH)
        caddyfile = self.root / "container" / "etc" / "caddy" / "Caddyfile"
        caddyfile.write_text("# newer matbakhy line\n" + caddyfile.read_text(encoding="utf-8"), encoding="utf-8")

        result = self.run_script(DETACH)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("# newer matbakhy line\n" + ORIGINAL, self.live())

    def test_failed_persistence_restores_the_running_configuration(self):
        self.run_script(ATTACH)
        attached = self.live()

        result = self.run_script(DETACH, FAKE_FAIL_CP_TO="/etc/caddy/Caddyfile")

        self.assertEqual(1, result.returncode)
        self.assertEqual(attached, self.live())
        self.assertEqual(attached, self.active())

    def test_refuses_unbalanced_markers(self):
        broken = ORIGINAL + "# BEGIN lawdesk-staging\nlawdesk.x {\n}\n"
        self.set_live(broken)

        result = self.run_script(DETACH)

        self.assertEqual(2, result.returncode)
        self.assertEqual(broken, self.live())
        self.assertIsNone(self.active())

    def test_without_a_lawdesk_site_there_is_nothing_to_do(self):
        result = self.run_script(DETACH)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(ORIGINAL, self.live())
        self.assertFalse(any("caddy reload" in c for c in self.calls()))


if __name__ == "__main__":
    unittest.main()
