#!/usr/bin/env python3
"""Injects the Arabic localization adapter (portal/localization) into an exported Floorplan map. Upstream stays untouched.

  localize_map.py <export .html> <output .html>
Idempotent for a given input; refuses an input that is not a Floorplan export. Standard library only."""
import base64
import hashlib
import re
import sys
from pathlib import Path

LOC = Path(__file__).resolve().parents[1] / "localization"


def main(src, dst):
    html = Path(src).read_text(encoding="utf-8")
    if "Floorplan" not in html or "</body>" not in html or "__FP_UNTRANSLATED" in html:
        sys.exit("not a fresh Floorplan export")
    d = (LOC / "ar.json").read_text(encoding="utf-8")
    js = (LOC / "adapter.js").read_text(encoding="utf-8").replace("/*DICT*/ {}", d.strip())
    css = (LOC / "adapter.css").read_text(encoding="utf-8")
    # The export ships its own hash-based CSP (no unsafe-inline scripts, connect-src 'none'); the adapter is allowed by
    # adding the hash of exactly this script, so the policy is not loosened for anything else.
    digest = base64.b64encode(hashlib.sha256(js.encode("utf-8")).digest()).decode()
    html, n = re.subn(r"(<meta http-equiv=\"Content-Security-Policy\"[^>]*script-src )", r"\1'sha256-" + digest + "' ", html, count=1)
    if n != 1:
        sys.exit("export has no script-src policy to extend; refusing to inject")
    html = html.replace("</head>", f"<style>{css}</style></head>", 1)
    html, n = re.subn(r"<html lang=\"[a-z-]+\"", '<html lang="ar" dir="rtl"', html, count=1)
    if n != 1:
        sys.exit("unexpected <html> tag")
    html = html.replace("</body>", f"<script>{js}</script></body>", 1)
    Path(dst).write_text(html, encoding="utf-8")
    print(f"localized map written: {dst}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(*sys.argv[1:])
