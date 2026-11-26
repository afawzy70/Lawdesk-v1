#!/usr/bin/env python3
"""LD-DOC-R6 M0: verifies the numbered extract against the ORIGINAL docx (provenance check; changes nothing).
usage: python3 verify_source.py <path-to-البرنامج.docx>
Checks: sha256 equals the digest recorded in docs/design/r6/LD-DOC-R6-00-README.md; every non-empty paragraph of word/document.xml
(body and tables), in order, equals the extract line by line; no text exists in headers, footers, footnotes, endnotes or comments
that the extract lacks; prints the page/word metadata of docProps/app.xml and the package parts. Exit 0 = identical."""
import hashlib
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def paragraphs(xml):
    out = []
    for p in re.findall(r"<w:p[ >].*?</w:p>", xml, flags=re.S):
        t = "".join(re.findall(r"<w:t[^>]*>(.*?)</w:t>", p, flags=re.S))
        if t.strip():
            out.append(t)
    return out


def main(path):
    raw = Path(path).read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    readme = (ROOT / "docs/design/r6/LD-DOC-R6-00-README.md").read_text()
    recorded = re.search(r"SHA-256 of the original DOCX\D+([0-9a-f]{64})", readme)
    problems = []
    if not recorded or recorded.group(1) != digest:
        problems.append("sha256 %s does not match the digest recorded in LD-DOC-R6-00 (%s)" % (digest, recorded.group(1) if recorded else "none"))
    z = zipfile.ZipFile(path)
    body = paragraphs(z.read("word/document.xml").decode())
    extract = []
    for line in (ROOT / "docs/evidence/DOC-R6-M0/source-extract.txt").read_text().splitlines():
        m = re.match(r"\s*(\d+)\t(.*)", line)
        if m:
            extract.append(m.group(2))
    if body != extract:
        for i, (a, b) in enumerate(zip(body, extract), 1):
            if a != b:
                problems.append("paragraph %d differs" % i)
                break
        if len(body) != len(extract):
            problems.append("paragraph count differs: docx %d, extract %d" % (len(body), len(extract)))
    side = [n for n in z.namelist() if re.match(r"word/(header|footer|footnotes|endnotes|comments)\d*\.xml", n)]
    for n in side:
        extra = [t for t in paragraphs(z.read(n).decode()) if t not in extract]
        if extra:
            problems.append("%s holds text absent from the extract: %s" % (n, extra[:2]))
    app = z.read("docProps/app.xml").decode() if "docProps/app.xml" in z.namelist() else ""
    print("sha256", digest, "bytes", len(raw))
    print("paragraphs docx=%d extract=%d side parts=%s media=%d" % (len(body), len(extract), side, len([n for n in z.namelist() if "media/" in n])))
    print("docProps/app.xml:", re.findall(r"<(Pages|Words|Characters|Application)>([^<]+)", app))
    print("NOTE: the page count is the application's stored metadata; page boundaries are not verifiable without rendering.")
    for p in problems:
        print("PROBLEM", p)
    print("IDENTICAL" if not problems else "DIFFERENT")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
