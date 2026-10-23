"""Builds the LawDesk candidate from a copy of the real shared Caddyfile with attach.sh (fake docker transport whose
`caddy validate`/`caddy reload` call the real Caddy 2.10.2 binary), then adapts the result to JSON."""
import json, os, shutil, subprocess, sys, tempfile, textwrap
from pathlib import Path
repo = Path(sys.argv[1]); caddy = sys.argv[2]; source = sys.argv[3]; out = Path(sys.argv[4])
sys.path.insert(0, str(repo / "deploy" / "tests"))
import test_proxy_scripts as t
root = Path(tempfile.mkdtemp())
(root / "container/etc/caddy").mkdir(parents=True)
shutil.copyfile(source, root / "container/etc/caddy/Caddyfile")
(root / "networks.json").write_text('["matbakhy-preview_edge"]'); (root / "statuses.json").write_text("{}")
(root / "bin").mkdir()
docker = t.FAKE_DOCKER.replace('sys.exit(1 if "INVALID" in config or os.environ.get("FAKE_FAIL_VALIDATE") else 0)',
    'sys.exit(subprocess.run([os.environ["REAL_CADDY"], "validate", "--config", path_in(rest[rest.index("--config") + 1]), "--adapter", "caddyfile"]).returncode)').replace("import json, os, shutil, sys", "import json, os, shutil, subprocess, sys")
for name, body in [("docker", docker), ("curl", t.FAKE_CURL)]:
    (root / "bin" / name).write_text(body); (root / "bin" / name).chmod(0o755)
env = {"PATH": f"{root/'bin'}:/usr/bin:/bin", "HOME": str(root), "FAKE_ROOT": str(root), "REAL_CADDY": caddy,
       "LAWDESK_PROXY_BACKUP_DIR": str(root / "backups"), "PREVIEW_DOMAIN": "167-86-90-174.sslip.io",
       "ACME_EMAIL": "ops@example.invalid", "CUSTOMER_ALLOWED_IPS": "203.0.113.0/24", "STAFF_ALLOWED_IPS": "203.0.113.0/24",
       "XDG_DATA_HOME": str(root / "xdg"), "XDG_CONFIG_HOME": str(root / "xdgc")}
r = subprocess.run(["bash", str(repo / "deploy/proxy/attach.sh")], env=env, capture_output=True, text=True)
print("attach exit", r.returncode); print(r.stdout); print(r.stderr[-3000:])
live = (root / "container/etc/caddy/Caddyfile").read_text()
out.write_text(live)
a = subprocess.run([caddy, "adapt", "--config", str(out), "--adapter", "caddyfile"], env=env, capture_output=True, text=True)
print("adapt exit", a.returncode, a.stderr[-500:])
cfg = json.loads(a.stdout)
for srv in cfg["apps"]["http"]["servers"].values():
    for route in srv["routes"]:
        hosts = [h for m in route.get("match", []) for h in m.get("host", [])]
        print("site:", hosts)
