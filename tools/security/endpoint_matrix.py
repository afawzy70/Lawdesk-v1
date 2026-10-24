#!/usr/bin/env python3
"""Endpoint -> permission matrix of the LawDesk backend, generated from the code (LD-040).

Reads every Spring MVC controller under backend/src/main/java, the @PreAuthorize rule of each handler (method level,
else class level, with String constants and Permission.Codes resolved), the public matchers and the logout URL of the
filter chain (SecurityConfig), and the default roles seeded by the Flyway migrations. Standard library only; it parses
source text, so it sees what is written, not what Spring builds at runtime.

Usage:
  endpoint_matrix.py [--root REPO] [--format markdown|json] [--check]

Statuses:
  PUBLIC         permitted to anyone by the filter chain
  PERMISSION     @PreAuthorize names authorities
  AUTHENTICATED  @PreAuthorize is isAuthenticated() only (any signed-in user)
  NEEDS_REVIEW   the rule refers to arguments or beans; it is decided at invocation, review it by hand
  FILTER         handled by a security filter (logout), not by a controller
  NO_RULE        a controller handler without @PreAuthorize that the filter chain does not make public

Exit codes: 0, or 1 with --check when an endpoint is NO_RULE; 2 usage error.
"""

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path

JAVA_ROOT = Path("backend/src/main/java")
MIGRATIONS = Path("backend/src/main/resources/db/migration")

MAPPINGS = {"GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT", "PatchMapping": "PATCH",
            "DeleteMapping": "DELETE", "RequestMapping": None}
CONTROLLERS = ("RestController", "Controller")


@dataclass
class Endpoint:
    method: str
    path: str
    rule: str | None
    status: str
    access: dict = field(default_factory=dict)
    file: str = ""
    line: int = 0


@dataclass
class Matrix:
    endpoints: list
    roles: dict
    fallback: str


# --- Java source handling -------------------------------------------------------------------------------------------

def blank_comments(text):
    """The text with comments replaced by spaces (newlines kept), so offsets and line numbers stay valid."""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end < 0 else end + 3
            out.append(text[i:end])
            i = end
        elif c in "\"'":
            j = i + 1
            while j < n and text[j] != c and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
        elif text.startswith("//", i):
            end = text.find("\n", i)
            end = n if end < 0 else end
            out.append(" " * (end - i))
            i = end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = n if end < 0 else end + 2
            out.append(re.sub(r"[^\n]", " ", text[i:end]))
            i = end
        else:
            out.append(c)
            i += 1
    return "".join(out)


def closing(text, start, open_char="(", close_char=")"):
    """Index just after the bracket that closes the one at {@code start}, skipping string literals."""
    depth, i, n = 0, start, len(text)
    while i < n:
        c = text[i]
        if c in "\"'":
            j = i + 1
            while j < n and text[j] != c:
                j += 2 if text[j] == "\\" else 1
            i = j + 1
            continue
        if c == open_char:
            depth += 1
        elif c == close_char:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


_ANNOTATION = re.compile(r"@([A-Za-z_][\w.]*)")
_STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')


def annotations_before_declarations(code):
    """Yields (annotations, declaration, offset) for every run of annotations; annotations are (name, args, offset)."""
    i, n = 0, len(code)
    run = []
    while i < n:
        m = _ANNOTATION.search(code, i)
        if not m:
            break
        if run and code[run[-1][3]:m.start()].strip():
            # Text between two annotations: the previous run ended at a declaration.
            yield from _flush(code, run)
            run = []
        name = m.group(1).split(".")[-1]
        j = m.end()
        k = j
        while k < n and code[k] in " \t\r\n":
            k += 1
        args = None
        if k < n and code[k] == "(" and name != "interface":
            end = closing(code, k)
            args = code[k + 1:end - 1]
            j = end
        run.append((name, args, m.start(), j))
        i = j
    if run:
        yield from _flush(code, run)


def _flush(code, run):
    end = run[-1][3]
    stop = len(code)
    for ch in "{;=":
        pos = code.find(ch, end)
        if 0 <= pos < stop:
            stop = pos
    declaration = code[end:stop]
    yield [(name, args, offset) for name, args, offset, _ in run], declaration, run[0][2]


def class_ranges(code):
    """[(name, start, end)] of every class, record, enum or interface body."""
    ranges = []
    for m in re.finditer(r"\b(?:class|record|enum|interface)\s+([A-Za-z_]\w*)", code):
        brace = code.find("{", m.end())
        if brace < 0:
            continue
        ranges.append((m.group(1), m.start(), closing(code, brace, "{", "}")))
    return ranges


def enclosing_class(ranges, offset):
    inner = None
    for name, start, end in ranges:
        if start <= offset < end and (inner is None or start >= inner[1]):
            inner = (name, start, end)
    return inner


_CONSTANT = re.compile(r"\bstatic\s+final\s+String\s+([A-Z_][A-Z0-9_]*)\s*=\s*([^;]+);")


def raw_constants(code):
    """[(class name, constant name, expression)] of the String constants in a file."""
    ranges = class_ranges(code)
    found = []
    for m in _CONSTANT.finditer(code):
        owner = enclosing_class(ranges, m.start())
        found.append((owner[0] if owner else "", m.group(1), m.group(2).strip()))
    return found


class Constants:
    def __init__(self):
        self.qualified = {}  # (class, NAME) -> expression
        self.local = {}  # (file, class, NAME) -> expression
        self.owners = {}  # (class, NAME) -> file

    def add(self, file, owner, name, expression):
        self.qualified[(owner, name)] = expression
        self.local[(file, owner, name)] = expression
        self.owners[(owner, name)] = file

    def resolve(self, expression, file, owner="", depth=0):
        """The String value of a constant expression: literals, constants and '+' concatenation; None if unknown."""
        if depth > 10:
            return None
        parts = split_concatenation(expression)
        values = []
        for part in parts:
            part = part.strip()
            literal = re.fullmatch(r'"((?:[^"\\]|\\.)*)"', part)
            if literal:
                values.append(bytes(literal.group(1), "utf-8").decode("unicode_escape"))
                continue
            names = part.split(".")
            if not all(re.fullmatch(r"[A-Za-z_]\w*", name) for name in names):
                return None
            if len(names) == 1:
                context = (file, owner)
                target = self.local.get((file, owner, names[0]))
                if target is None:  # a constant of another class in the same file
                    candidates = [(k[1], v) for k, v in self.local.items() if k[0] == file and k[2] == names[0]]
                    if len(candidates) != 1:
                        return None
                    context = (file, candidates[0][0])
                    target = candidates[0][1]
            else:
                target = self.qualified.get((names[-2], names[-1]))
                context = (self.owners.get((names[-2], names[-1]), file), names[-2])
            if target is None:
                return None
            value = self.resolve(target, context[0], context[1], depth + 1)
            if value is None:
                return None
            values.append(value)
        return "".join(values)


def split_concatenation(expression):
    parts, current, i, n = [], [], 0, len(expression)
    while i < n:
        c = expression[i]
        if c == '"':
            m = _STRING.match(expression, i)
            end = m.end() if m else n
            current.append(expression[i:end])
            i = end
            continue
        if c == "+":
            parts.append("".join(current))
            current = []
        else:
            current.append(c)
        i += 1
    parts.append("".join(current))
    return parts


def mapping_paths(args, constants, file, owner=""):
    """The paths named by a mapping annotation's arguments ([""] when it names none)."""
    if args is None or not args.strip():
        return [""]
    named = re.search(r"\b(?:value|path)\s*=\s*", args)
    if named:
        rest = args[named.end():].lstrip()
        if rest.startswith("{"):
            body = rest[1:closing(rest, 0, "{", "}") - 1]
        else:
            body = re.split(r",\s*\w+\s*=", rest)[0]
    elif re.match(r"\s*\w+\s*=", args):
        return [""]  # only other attributes, such as method = ...
    else:
        body = args.strip()
        if body.startswith("{"):
            body = body[1:closing(body, 0, "{", "}") - 1]
    paths = []
    for item in split_top_level(body):
        value = constants.resolve(item, file, owner)
        if value is not None:
            paths.append(value)
    return paths or [""]


def split_top_level(body):
    items, current, depth, in_string = [], [], 0, False
    i = 0
    while i < len(body):
        c = body[i]
        if c == '"' :
            m = _STRING.match(body, i)
            end = m.end() if m else len(body)
            current.append(body[i:end])
            i = end
            continue
        if c in "({":
            depth += 1
        elif c in ")}":
            depth -= 1
        if c == "," and depth == 0:
            items.append("".join(current))
            current = []
        else:
            current.append(c)
        i += 1
    if "".join(current).strip():
        items.append("".join(current))
    return [item.strip() for item in items if item.strip()]


def request_methods(args):
    if not args:
        return ["ANY"]
    found = re.findall(r"RequestMethod\.([A-Z]+)", args)
    return found or ["ANY"]


def join_paths(base, path):
    if not base:
        joined = path
    elif not path:
        joined = base
    else:
        joined = base.rstrip("/") + "/" + path.lstrip("/")
    if not joined.startswith("/"):
        joined = "/" + joined
    return joined


# --- filter chain and roles -----------------------------------------------------------------------------------------

def filter_chain(java_files, root):
    """(public matchers [(method, pattern, file, line)], logout [(url, file, line)], fallback)."""
    public, logout, fallback = [], [], "authenticated"
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        if "authorizeHttpRequests" not in text and "logoutUrl" not in text:
            continue
        code = blank_comments(text)
        rel = path.relative_to(root).as_posix()
        for m in re.finditer(r"\.requestMatchers\(", code):
            args_end = closing(code, m.end() - 1)
            following = code[args_end:args_end + 40]
            if not re.match(r"\s*\.permitAll\(\)", following):
                continue
            args = code[m.end():args_end - 1]
            method = re.search(r"HttpMethod\.([A-Z]+)", args)
            line = code.count("\n", 0, m.start()) + 1
            for pattern in _STRING.findall(args):
                public.append((method.group(1) if method else "ANY", pattern, rel, line))
        for m in re.finditer(r"\.logoutUrl\(\s*\"([^\"]+)\"\s*\)", code):
            logout.append((m.group(1), rel, code.count("\n", 0, m.start()) + 1))
        if re.search(r"\.anyRequest\(\)\s*\.permitAll\(\)", code):
            fallback = "permitAll"
        elif re.search(r"\.anyRequest\(\)\s*\.denyAll\(\)", code):
            fallback = "denyAll"
    return public, logout, fallback


def pattern_matches(pattern, path):
    regex = ""
    i = 0
    while i < len(pattern):
        if pattern.startswith("/**", i):
            regex += "(?:/.*)?"
            i += 3
        elif pattern.startswith("**", i):
            regex += ".*"
            i += 2
        elif pattern[i] == "*":
            regex += "[^/]*"
            i += 1
        elif pattern[i] == "{":
            end = pattern.find("}", i)
            regex += "[^/]+"
            i = end + 1 if end > 0 else len(pattern)
        else:
            regex += re.escape(pattern[i])
            i += 1
    return re.fullmatch(regex, path) is not None


def default_roles(root):
    """{role: {permissions}} from the role_permission seeds of every migration, in version order."""
    roles = {}
    directory = root / MIGRATIONS
    if not directory.is_dir():
        return roles

    def version(path):
        m = re.match(r"V(\d+)", path.name)
        return int(m.group(1)) if m else 0

    for path in sorted(directory.glob("V*.sql"), key=version):
        sql = path.read_text(encoding="utf-8")
        for m in re.finditer(r"insert\s+into\s+role_permission\b(.*?);", sql, re.IGNORECASE | re.DOTALL):
            for role, permission in re.findall(r"\(\s*'([A-Z_]+)'\s*,\s*'([A-Z_]+)'\s*\)", m.group(1)):
                roles.setdefault(role, set()).add(permission)
    return roles


# --- rule evaluation ------------------------------------------------------------------------------------------------

_TOKEN = re.compile(r"\s*(?:(?P<call>hasAuthority|hasAnyAuthority|hasRole|hasAnyRole|isAuthenticated|"
                    r"isFullyAuthenticated|permitAll|denyAll|isAnonymous)\s*\((?P<args>[^()]*)\)|"
                    r"(?P<op>and|or|not|&&|\|\||!)|(?P<paren>[()]))")


def evaluate(expression, authorities, authenticated=True):
    """True/False for a signed-in user holding {@code authorities}; None when the rule cannot be decided statically."""
    tokens = []
    pos = 0
    expression = expression.strip()
    while pos < len(expression):
        m = _TOKEN.match(expression, pos)
        if not m or m.end() == pos:
            return None
        pos = m.end()
        if m.group("call"):
            name = m.group("call")
            values = re.findall(r"'([^']*)'", m.group("args"))
            if name == "hasAuthority":
                tokens.append(values[0] in authorities if values else False)
            elif name == "hasAnyAuthority":
                tokens.append(any(v in authorities for v in values))
            elif name == "hasRole":
                tokens.append(("ROLE_" + values[0]) in authorities if values else False)
            elif name == "hasAnyRole":
                tokens.append(any(("ROLE_" + v) in authorities for v in values))
            elif name in ("isAuthenticated", "isFullyAuthenticated"):
                tokens.append(authenticated)
            elif name == "permitAll":
                tokens.append(True)
            elif name == "denyAll":
                tokens.append(False)
            elif name == "isAnonymous":
                tokens.append(not authenticated)
        elif m.group("op"):
            tokens.append({"and": "and", "&&": "and", "or": "or", "||": "or", "not": "not", "!": "not"}[m.group("op")])
        else:
            tokens.append(m.group("paren"))
    try:
        value, rest = _parse_or(tokens)
    except (IndexError, ValueError):
        return None
    return value if not rest else None


def _parse_or(tokens):
    value, tokens = _parse_and(tokens)
    while tokens and tokens[0] == "or":
        right, tokens = _parse_and(tokens[1:])
        value = value or right
    return value, tokens


def _parse_and(tokens):
    value, tokens = _parse_not(tokens)
    while tokens and tokens[0] == "and":
        right, tokens = _parse_not(tokens[1:])
        value = value and right
    return value, tokens


def _parse_not(tokens):
    if tokens[0] == "not":
        value, rest = _parse_not(tokens[1:])
        return not value, rest
    if tokens[0] == "(":
        value, rest = _parse_or(tokens[1:])
        if not rest or rest[0] != ")":
            raise ValueError("unbalanced")
        return value, rest[1:]
    if isinstance(tokens[0], bool):
        return tokens[0], tokens[1:]
    raise ValueError(tokens[0])


def status_of(rule):
    if rule is None:
        return "NO_RULE"
    if "#" in rule or "@" in rule or evaluate(rule, set()) is None:
        return "NEEDS_REVIEW"
    if re.search(r"has(Any)?(Authority|Role)\(", rule):
        return "PERMISSION"
    if evaluate(rule, set()) is True:
        return "AUTHENTICATED"
    return "PERMISSION"


# --- the matrix -----------------------------------------------------------------------------------------------------

def build(root):
    root = Path(root)
    java_files = sorted((root / JAVA_ROOT).rglob("*.java"))
    constants = Constants()
    codes = {}
    for path in java_files:
        rel = path.relative_to(root).as_posix()
        code = blank_comments(path.read_text(encoding="utf-8"))
        codes[rel] = code
        for owner, name, expression in raw_constants(code):
            constants.add(rel, owner, name, expression)

    endpoints = []
    for rel, code in codes.items():
        ranges = class_ranges(code)
        classes = {}  # class name -> (is controller, base paths, class rule)
        for annotations, declaration, offset in annotations_before_declarations(code):
            names = {name for name, _, _ in annotations}
            declared = re.search(r"\b(?:class|record|interface)\s+([A-Za-z_]\w*)", declaration)
            if declared and not re.search(r"\(", declaration.split(declared.group(0))[0]):
                base = [""]
                rule = None
                for name, args, _ in annotations:
                    if name == "RequestMapping":
                        base = mapping_paths(args, constants, rel, declared.group(1))
                    if name == "PreAuthorize":
                        rule = constants.resolve(args or "", rel, declared.group(1))
                classes[declared.group(1)] = (bool(names & set(CONTROLLERS)), base, rule)
                continue
            mapping = [(name, args, at) for name, args, at in annotations if name in MAPPINGS]
            if not mapping:
                continue
            owner = enclosing_class(ranges, offset)
            if owner is None or owner[0] not in classes or not classes[owner[0]][0]:
                continue
            _, bases, class_rule = classes[owner[0]]
            rule = class_rule
            for name, args, _ in annotations:
                if name == "PreAuthorize":
                    rule = constants.resolve(args or "", rel, owner[0])
                    if rule is None:
                        rule = "<unresolved: %s>" % (args or "").strip()
            for name, args, at in mapping:
                methods = [MAPPINGS[name]] if MAPPINGS[name] else request_methods(args)
                line = code.count("\n", 0, at) + 1
                for base in bases:
                    for sub in mapping_paths(args, constants, rel, owner[0]):
                        for method in methods:
                            endpoints.append(Endpoint(method, join_paths(base, sub), rule, status_of(rule),
                                                      file=rel, line=line))

    public, logout, fallback = filter_chain([root / rel for rel in codes], root)
    matched = set()
    for row in endpoints:
        for method, pattern, _, _ in public:
            if method in ("ANY", row.method) and pattern_matches(pattern, row.path):
                row.status = "PUBLIC"
                matched.add((method, pattern))
    for method, pattern, rel, line in public:
        if (method, pattern) not in matched:
            endpoints.append(Endpoint(method, pattern, "permitAll() (filter chain)", "PUBLIC", file=rel, line=line))
    for url, rel, line in logout:
        endpoints.append(Endpoint("POST", url, "LogoutFilter (CSRF token required)", "FILTER", file=rel, line=line))

    roles = default_roles(root)
    for row in endpoints:
        for role, granted in sorted(roles.items()):
            if row.status in ("PUBLIC", "FILTER"):
                row.access[role] = True
            elif row.rule is None:
                row.access[role] = fallback != "denyAll"
            elif row.rule.startswith("<unresolved"):
                row.access[role] = None
            else:
                row.access[role] = evaluate(row.rule, granted)
    endpoints.sort(key=lambda r: (r.path, ["GET", "POST", "PUT", "PATCH", "DELETE"].index(r.method)
                                  if r.method in ("GET", "POST", "PUT", "PATCH", "DELETE") else 9))
    return Matrix(endpoints, roles, fallback)


def markdown(matrix):
    roles = sorted(matrix.roles)
    mark = {True: "yes", False: "-", None: "?"}
    lines = ["| Method | Path | Rule | Status | " + " | ".join(roles) + " | Source |",
             "|---|---|---|---|" + "---|" * len(roles) + "---|"]
    for row in matrix.endpoints:
        rule = "`%s`" % row.rule if row.rule else "(none)"
        cells = " | ".join(mark[row.access.get(role)] for role in roles)
        lines.append(f"| {row.method} | `{row.path}` | {rule} | {row.status} | {cells} | `{row.file}:{row.line}` |")
    counts = {}
    for row in matrix.endpoints:
        counts[row.status] = counts.get(row.status, 0) + 1
    lines.append("")
    lines.append("Fallback for requests no rule names: `%s`. Endpoints: %d (%s)." % (
        matrix.fallback, len(matrix.endpoints), ", ".join(f"{k} {v}" for k, v in sorted(counts.items()))))
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--format", choices=("markdown", "json"), default="markdown")
    parser.add_argument("--check", action="store_true", help="exit 1 when an endpoint has no rule")
    args = parser.parse_args(argv)
    matrix = build(Path(args.root))
    if args.format == "json":
        print(json.dumps({"fallback": matrix.fallback,
                          "roles": {role: sorted(perms) for role, perms in sorted(matrix.roles.items())},
                          "endpoints": [asdict(row) for row in matrix.endpoints]}, indent=1, ensure_ascii=False))
    else:
        print(markdown(matrix))
    if args.check and any(row.status == "NO_RULE" for row in matrix.endpoints):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
