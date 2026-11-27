#!/usr/bin/env python3
"""Renders docs/design/r6/LD-DOC-R6-01-requirements-register.md from r6_register_data.py (documents only)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import r6_register_data as data  # noqa: E402

OUT = Path(__file__).resolve().parents[2] / "docs" / "design" / "r6" / "LD-DOC-R6-01-requirements-register.md"


def cell(text):
    return text.replace("|", "\\|").replace("\n", " ")


def render():
    out = ["# LD-DOC-R6-01 — Requirements register and traceability (M0)", "",
           "Generated from `tools/docs/r6_register_data.py` by `tools/docs/r6_register_build.py`; edit the data file, not this page.", "",
           "**Reading rule (M0 is documentation only).** *Existing baseline* = behaviour or schema read in the develop tree at `18822a8` (file cited). "
           "*Proposed R6* = design for review, **not built**. Nothing in R6 is built; every row is \"Not started\". "
           "Source: `البرنامج.docx`, 209 numbered paragraphs (`¶`) in `docs/evidence/DOC-R6-M0/source-extract.txt`; provenance and digest in `LD-DOC-R6-00`. "
           "Decisions `D-nn` are in `LD-DOC-R6-04`; legal gates `G-*` and the authoritative data model in `LD-DOC-R6-05`.", "",
           "**Verification state of every row:** " + data.VERIFICATION, ""]
    for prefix, title in data.M.items():
        rows = [r for r in data.ROWS if r[0].startswith(prefix + "-")]
        out += ["## " + title, "",
                "| ID | Source | Expected behaviour (source) | Existing baseline | Proposed R6 | Dependency / legal status |", "|---|---|---|---|---|---|"]
        for r in rows:
            out.append("| %s | %s | %s | %s | %s | %s |" % (r[0], r[1], cell(r[2]), cell(r[3]), cell(r[4]), cell(r[7])))
        out += ["", "| ID | Acceptance criteria | Test IDs |", "|---|---|---|"]
        for r in rows:
            tests = "<br>".join("`%s` %s" % (t, cell(d)) for t, d in r[6])
            out.append("| %s | %s | %s |" % (r[0], cell(r[5]), tests))
        out.append("")
    out += ["## Cross-cutting tests (referenced from 03, 04 and 05)", "", "| Test ID | Proves |", "|---|---|"]
    out += ["| `%s` | %s |" % (t, cell(d)) for t, d in data.CROSS]
    out += [""]
    return "\n".join(out)


if __name__ == "__main__":
    OUT.write_text(render())
    print("wrote", OUT, "rows", len(data.ROWS), "tests", sum(len(r[6]) for r in data.ROWS) + len(data.CROSS))
