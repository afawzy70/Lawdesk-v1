"""Quality gates for the portal (standard library only). Run: python3 -I -m unittest discover -s portal/tests -p 'test_*.py'
The build tests need a built dist; they build one into a temp dir from the current checkout."""
import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

PORTAL = Path(__file__).resolve().parents[1]
REPO = PORTAL.parent
sys.path.insert(0, str(PORTAL / "tools"))
import validate_content as vc  # noqa: E402

ARABIC = re.compile(r"[؀-ۿ]")
SECRETS = re.compile(r"(?i)(password\s*[:=]\s*\S{6,}|BEGIN [A-Z ]*PRIVATE KEY|AKIA[0-9A-Z]{16}|eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}|postgres://\S+:\S+@|ghp_[A-Za-z0-9]{20,})")


def features():
    return {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in (PORTAL / "content/features").glob("*.json")}


class Content(unittest.TestCase):
    def test_every_feature_validates_against_the_repository(self):
        cats = {c["id"] for c in json.loads((PORTAL / "content/categories.json").read_text(encoding="utf-8"))}
        problems = []
        for p in sorted((PORTAL / "content/features").glob("*.json")):
            problems += vc.check(REPO, p, cats)
        self.assertEqual(problems, [])

    def test_every_feature_has_a_workflow_with_evidence_on_every_step(self):
        for fid, f in features().items():
            self.assertGreaterEqual(len(f["steps"]), 3, fid)
            for s in f["steps"]:
                self.assertTrue(s["evidence"], f"{fid} step {s['n']}")

    def test_the_main_domains_are_indexed(self):
        ids = set(features())
        for need in ["auth-login", "users-admin", "roles-permissions", "case-access-confidentiality", "cases-register",
                     "parties", "courts", "proceedings-stages", "hearings", "claims-judgments", "deadlines", "tasks", "calendar",
                     "notifications", "documents-ocr", "search", "enforcement", "settlements", "library", "reports-dashboard", "audit"]:
            self.assertIn(need, ids)

    def test_no_feature_claims_uat_verification_without_evidence(self):
        for fid, f in features().items():
            if f["status"] == "VERIFIED_ON_UAT":
                self.assertIn("UAT", f["verification_ar"], fid)

    def test_planned_items_are_never_implemented(self):
        for p in (PORTAL / "planned").glob("*.json"):
            d = json.loads(p.read_text(encoding="utf-8"))
            self.assertIn(d["status"], {"IN_DEVELOPMENT", "PROPOSED", "BLOCKED"})
            self.assertRegex(d["sha"], r"^[0-9a-f]{40}$")
            self.assertTrue(ARABIC.search(d["qa_ar"]))

    def test_validator_rejects_a_step_without_evidence_and_non_arabic_text(self):
        f = dict(next(iter(features().values())))
        f = json.loads(json.dumps(f))
        f["steps"][0]["evidence"] = []
        f["name_ar"] = "English only"
        with tempfile.TemporaryDirectory() as t:
            c = Path(t) / "features"
            c.mkdir()
            (c / f"{f['id']}.json").write_text(json.dumps(f, ensure_ascii=False), encoding="utf-8")
            problems = vc.check(REPO, c / f"{f['id']}.json", {f["category"]})
        self.assertTrue(any("needs at least 1 evidence" in p for p in problems))
        self.assertTrue(any("name_ar has no Arabic" in p for p in problems))

    def test_validator_rejects_an_evidence_range_that_does_not_exist(self):
        f = json.loads(json.dumps(next(iter(features().values()))))
        f["steps"][0]["evidence"] = [{"path": "backend/pom.xml", "lines": "99999-100000"}]
        with tempfile.TemporaryDirectory() as t:
            fp = Path(t) / f"{f['id']}.json"
            fp.write_text(json.dumps(f, ensure_ascii=False), encoding="utf-8")
            problems = vc.check(REPO, fp, {f["category"]})
        self.assertTrue(any("outside" in p for p in problems))


class Deployment(unittest.TestCase):
    def test_compose_publishes_no_host_port_and_is_hardened(self):
        c = (PORTAL / "deploy/compose.yaml").read_text(encoding="utf-8")
        self.assertNotRegex(c, r"(?m)^\s*ports:")
        for need in ["read_only: true", "no-new-privileges:true", "cap_drop:", "external: true", "floorplan-edge"]:
            self.assertIn(need, c)

    def test_nginx_serves_static_only_with_security_headers(self):
        n = (PORTAL / "deploy/nginx.conf").read_text(encoding="utf-8")
        for need in ["autoindex off", "server_tokens off", "return 405", "connect-src 'none'", "noindex"]:
            self.assertIn(need, n + (PORTAL / "deploy/snippets/security-headers-base.conf").read_text(encoding="utf-8"))
        self.assertNotIn("proxy_pass", n)
        h = (PORTAL / "deploy/snippets/security-headers.conf").read_text(encoding="utf-8")
        self.assertIn("default-src 'none'", h)
        self.assertNotIn("unsafe-inline", h)

    def test_attach_is_private_by_default_and_does_not_touch_other_sites(self):
        a = (PORTAL / "deploy/proxy/attach.sh").read_text(encoding="utf-8")
        self.assertIn("private by default", a)
        self.assertIn("basic_auth", a)
        self.assertIn("candidate would change another site", a)
        self.assertNotIn("docker restart", a)
        self.assertNotIn("docker compose", a)

    def test_the_portal_does_not_call_lawdesk_apis(self):
        for p in list((PORTAL / "assets").glob("*")) + [PORTAL / "tools/build_portal.py"]:
            self.assertNotRegex(p.read_text(encoding="utf-8"), r"/api/v1|lawdesk\.\d+-\d+", p.name)


class Build(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls.tmp.name) / "dist"
        sha = subprocess.run(["git", "rev-parse", "HEAD"], cwd=REPO, capture_output=True, text=True, check=True).stdout.strip()
        r = subprocess.run([sys.executable, "-I", str(PORTAL / "tools/build_portal.py"), "--sha", sha, "--out", str(cls.out)],
                           capture_output=True, text=True)
        assert r.returncode == 0, r.stderr
        cls.sha = sha

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_every_feature_has_a_page_and_a_search_entry(self):
        idx = json.loads((self.out / "search-index.json").read_text(encoding="utf-8"))
        urls = {e["url"] for e in idx}
        for fid in features():
            self.assertTrue((self.out / "features" / f"{fid}.html").is_file(), fid)
            self.assertIn(f"features/{fid}.html", urls)

    def test_pages_are_arabic_rtl_and_not_indexable(self):
        for p in self.out.rglob("*.html"):
            t = p.read_text(encoding="utf-8")
            self.assertIn('lang="ar" dir="rtl"', t, p.name)
            self.assertIn("noindex", t, p.name)

    def test_no_inline_style_or_external_resource_in_pages(self):
        for p in self.out.rglob("*.html"):
            t = p.read_text(encoding="utf-8")
            self.assertNotRegex(t, r'\sstyle="', p.name)
            self.assertNotRegex(t, r'(src|href)="https?://(?!github\.com/afawzy70/Lawdesk-v1/)', p.name)

    def test_no_secrets_or_environment_files_in_the_output(self):
        for p in self.out.rglob("*"):
            if p.is_file():
                self.assertNotRegex(p.name, r"^\.env", p.name)
                self.assertIsNone(SECRETS.search(p.read_text(encoding="utf-8", errors="ignore")), p.name)

    def test_build_records_the_source_sha_everywhere(self):
        b = json.loads((self.out / "build.json").read_text(encoding="utf-8"))
        self.assertEqual(b["source_sha"], self.sha)
        self.assertIn(self.sha, (self.out / "index.html").read_text(encoding="utf-8"))

    def test_public_audience_omits_file_paths(self):
        pub = self.out.parent / "pub"
        subprocess.run([sys.executable, "-I", str(PORTAL / "tools/build_portal.py"), "--sha", self.sha, "--out", str(pub), "--audience", "public"],
                       check=True, capture_output=True)
        page = (pub / "features/auth-login.html").read_text(encoding="utf-8")
        self.assertNotIn("backend/src/main", page)
        self.assertNotIn("github.com/afawzy70", page)


if __name__ == "__main__":
    unittest.main()
