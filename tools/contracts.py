"""Checks for the shared contracts of the parallel workstreams (architecture/CONTRACTS.md §2, §3).

Used by tools/tests/test_contracts.py, which runs in the CI policy gate.
"""

import re
from pathlib import Path

PERMISSION_ENUM = Path("backend/src/main/java/com/lawdesk/security/Permission.java")
PERMISSION_TS = Path("frontend/src/api/permissions.ts")
MIGRATIONS = Path("backend/src/main/resources/db/migration")

# Flyway version range of each phase, and the stories of that phase (product/DELIVERY-PLAN.md).
RANGES = {2: (100, 199, range(7, 12)), 3: (200, 299, range(12, 18)), 4: (300, 399, range(18, 24)),
          5: (400, 499, range(24, 30)), 6: (500, 599, range(30, 35)),
          # The comprehensive requirements programme (docs/design/LD-DOC-06): LD-050 core and access, LD-051 claims and
          # judgments, LD-052 library, LD-053 hearings and risk, LD-054 deadlines, LD-055 enforcement and archive,
          # LD-056 tasks and notifications, LD-057 settlements and search, LD-058 reports, LD-059 assistant.
          7: (600, 999, range(50, 60))}

_MIGRATION_RE = re.compile(r"V(?P<version>\d+)__(?:LD-(?P<story>\d{3})_)?[a-z0-9_]+\.sql")


def java_permissions(root):
    text = (root / PERMISSION_ENUM).read_text(encoding="utf-8")
    start = text.index("public enum Permission {") + len("public enum Permission {")
    body = text[start:text.index(";", start)]
    return [name.strip() for name in body.split(",") if name.strip()]


def ts_permissions(root):
    text = (root / PERMISSION_TS).read_text(encoding="utf-8")
    body = text[text.index("PERMISSIONS = ["):text.index("] as const")]
    return re.findall(r"'([A-Z_]+)'", body)


def migration_names(root):
    return sorted(path.name for path in (root / MIGRATIONS).glob("*.sql"))


def migration_errors(names):
    errors = []
    seen = {}
    for name in names:
        match = _MIGRATION_RE.fullmatch(name)
        if not match:
            errors.append(f"{name}: expected V<n>__LD-0xx_<what>.sql (lowercase words)")
            continue
        version = int(match["version"])
        if version in seen:
            errors.append(f"version {version} is used by {seen[version]} and {name}")
        seen[version] = name
        if version < 100:
            continue
        if match["story"] is None:
            errors.append(f"{name}: migrations from V100 on must name their story (V<n>__LD-0xx_...)")
            continue
        story = int(match["story"])
        owner = next((phase for phase, (_, _, stories) in RANGES.items() if story in stories), None)
        if owner is None:
            errors.append(f"{name}: LD-{story:03d} has no reserved migration range yet")
            continue
        low, high, _ = RANGES[owner]
        if not low <= version <= high:
            errors.append(f"{name}: LD-{story:03d} belongs to phase {owner}, whose range is V{low}-V{high}")
    return errors
