"""Runs the real attach.sh against a COPY of a Caddyfile, with a fake docker transport whose `caddy validate` and
`caddy adapt` run the real Caddy binary (placeholders expanded from the env below) and whose curl answers 200.
Usage: real-caddy-attach-check.py <repo> <caddy binary> <Caddyfile copy> <label>. Never contacts Docker or a proxy."""
import json, os, shutil, subprocess, sys, tempfile
from pathlib import Path
repo = Path(sys.argv[1]); caddy = sys.argv[2]; source = sys.argv[3]; label = sys.argv[4]
sys.path.insert(0, str(repo / "deploy" / "tests"))
import test_proxy_scripts as t
root = Path(tempfile.mkdtemp())
(root / "container/etc/caddy").mkdir(parents=True)
shutil.copyfile(source, root / "container/etc/caddy/Caddyfile")
(root / "networks.json").write_text('["matbakhy-preview_edge"]'); (root / "statuses.json").write_text("{}")
(root / "bin").mkdir()
real = """    elif rest[:2] in (["caddy", "validate"], ["caddy", "adapt"]):
        r = subprocess.run([os.environ["REAL_CADDY"], rest[1], "--config", path_in(rest[rest.index("--config") + 1]),
                            "--adapter", "caddyfile"], capture_output=True, text=True)
        sys.stdout.write(r.stdout); sys.exit(r.returncode)
"""
docker = t.FAKE_DOCKER.replace("import json, os, shutil, sys", "import json, os, shutil, subprocess, sys")
for verb in ["validate", "adapt"]:
    marker = f'    elif rest[:2] == ["caddy", "{verb}"]:'
    assert docker.count(marker) == 1, marker  # the fake branches must be replaced, or this check proves nothing
    docker = docker.replace(marker, (real if verb == "validate" else "") + "    elif False:")
assert "REAL_CADDY" in docker
for name, body in [("docker", docker), ("curl", t.FAKE_CURL)]:
    (root / "bin" / name).write_text(body); (root / "bin" / name).chmod(0o755)
env = {"PATH": f"{root/'bin'}:/usr/bin:/bin", "HOME": str(root), "FAKE_ROOT": str(root), "REAL_CADDY": caddy,
       "LAWDESK_PROXY_BACKUP_DIR": str(root / "backups"), "PREVIEW_DOMAIN": "167-86-90-174.sslip.io",
       "ACME_EMAIL": "ops@example.invalid", "CUSTOMER_ALLOWED_IPS": "203.0.113.0/24", "STAFF_ALLOWED_IPS": "203.0.113.0/24",
       "XDG_DATA_HOME": str(root / "xdg"), "XDG_CONFIG_HOME": str(root / "xdgc")}
r = subprocess.run(["bash", str(repo / "deploy/proxy/attach.sh")], env=env, capture_output=True, text=True)
print(f"== {label}: attach exit {r.returncode}")
print(r.stdout.replace(str(root), "<tmp>")); print(r.stderr.replace(str(root), "<tmp>")[-2000:])
live = root / "container/etc/caddy/Caddyfile"
print("live Caddyfile unchanged:", live.read_text() == Path(source).read_text())
print("active (reloaded) config:", "none" if not (root / "active.caddy").exists() else
      ("LawDesk block present" if "# BEGIN lawdesk-staging" in (root / "active.caddy").read_text() else "no LawDesk block"))
