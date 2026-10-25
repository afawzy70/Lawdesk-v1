"""LD-040: the endpoint -> permission matrix is generated from the code (tools/security/endpoint_matrix.py), so the
security baseline can be reproduced on any commit (docs/security/SECURITY-BASELINE.md)."""

import io
import json
import sys
import tempfile
import textwrap
import unittest
from contextlib import redirect_stdout
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "tools" / "security"))

import endpoint_matrix  # noqa: E402

PERMISSION = """
package com.lawdesk.security;

public enum Permission {
    CASE_VIEW, CASE_EDIT, TASK_VIEW, REPORT_VIEW, ROLE_VIEW, USER_MANAGE;

    /** {@code @PreAuthorize} expressions: {@code @PreAuthorize(Permission.Codes.CASE_CREATE)}. */
    public static final class Codes {
        public static final String CASE_VIEW = "hasAuthority('CASE_VIEW')";
        public static final String CASE_EDIT = "hasAuthority('CASE_EDIT')";
        public static final String ROLE_VIEW = "hasAuthority('ROLE_VIEW')";
        public static final String USER_MANAGE = "hasAuthority('USER_MANAGE')";
    }
}
"""

CASES = """
package com.lawdesk.cases;

// @GetMapping("/commented-out") must be ignored, and so must "http://example.test" inside strings.
@RestController
@RequestMapping("/api/v1/cases")
class CaseController {

    private static final String URL = "http://example.test/*not a comment*/";

    @GetMapping
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    PageResponse<CaseListItem> search(@Valid @ModelAttribute CaseSearch search) {
        return null;
    }

    /* @DeleteMapping("/{id}") is a comment too. */
    @PutMapping(path = "/{id}")
    @PreAuthorize(Permission.Codes.CASE_EDIT)
    LegalCase update(@PathVariable UUID id, @RequestBody UpdateCaseRequest request) {
        return null;
    }

    @GetMapping(value = {"/{id}/timeline", "/{id}/history"})
    PageResponse<CaseEvent> timeline(@PathVariable UUID id) {
        return null;
    }
}
"""

ROLES = """
package com.lawdesk.users;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("isAuthenticated()")
class RoleController {

    private static final String READ = Permission.Codes.ROLE_VIEW + " or " + Permission.Codes.USER_MANAGE;

    @GetMapping
    @PreAuthorize(READ)
    List<RoleView> list() {
        return null;
    }

    @GetMapping("/mine")
    List<RoleView> mine() {
        return null;
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    @PreAuthorize("hasAuthority('ROLE_MANAGE') and #id != null")
    void delete(@PathVariable UUID id) {
    }
}

@RestController
class DirectoryController {
    static final String READ = "hasAnyAuthority('TASK_VIEW', 'REPORT_VIEW')";

    @GetMapping("/api/v1/users/directory")
    @PreAuthorize(READ)
    List<String> active() {
        return null;
    }
}
"""

AUTH = """
package com.lawdesk.security;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    /** Public: issues the XSRF-TOKEN cookie. */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        return null;
    }

    @PostMapping("/login")
    MeResponse login(@RequestBody LoginRequest body) {
        return null;
    }
}
"""

SECURITY = """
package com.lawdesk.platform;

class SecurityConfig {
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout"));
        return http.build();
    }
}
"""

SEED = """
insert into app_role (code, name, system) values ('ADMIN', 'Administrator', true), ('STAFF', 'Staff', true);
insert into role_permission (role_id, permission)
select r.id, p.permission from app_role r
join (values
    ('ADMIN', 'CASE_VIEW'), ('ADMIN', 'CASE_EDIT'), ('ADMIN', 'ROLE_VIEW'), ('ADMIN', 'ROLE_MANAGE'),
    ('STAFF', 'CASE_VIEW'), ('STAFF', 'TASK_VIEW')
) as p (role, permission) on p.role = r.code;
"""

SEED_LATER = """
insert into role_permission (role_id, permission)
select r.id, p.permission from app_role r
join (values ('STAFF', 'REPORT_VIEW')) as p (role, permission) on p.role = r.code
on conflict do nothing;
"""


def write_tree(root):
    java = root / "backend" / "src" / "main" / "java" / "com" / "lawdesk"
    files = {
        java / "security" / "Permission.java": PERMISSION,
        java / "cases" / "CaseController.java": CASES,
        java / "users" / "RoleController.java": ROLES,
        java / "security" / "AuthController.java": AUTH,
        java / "platform" / "SecurityConfig.java": SECURITY,
    }
    migrations = root / "backend" / "src" / "main" / "resources" / "db" / "migration"
    files[migrations / "V100__LD-007_app_user.sql"] = SEED
    files[migrations / "V190__LD-009_more.sql"] = SEED_LATER
    for path, text in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(textwrap.dedent(text), encoding="utf-8")


class FixtureTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        write_tree(self.root)
        self.matrix = endpoint_matrix.build(self.root)
        self.by_key = {(row.method, row.path): row for row in self.matrix.endpoints}

    def tearDown(self):
        self.tmp.cleanup()

    def row(self, method, path):
        self.assertIn((method, path), self.by_key, sorted(self.by_key))
        return self.by_key[(method, path)]

    def test_class_and_method_paths_are_joined_and_comments_ignored(self):
        self.assertEqual("hasAuthority('CASE_VIEW')", self.row("GET", "/api/v1/cases").rule)
        self.assertEqual("hasAuthority('CASE_EDIT')", self.row("PUT", "/api/v1/cases/{id}").rule)
        paths = {path for _, path in self.by_key}
        self.assertNotIn("/api/v1/cases/commented-out", paths)
        self.assertNotIn("/commented-out", paths)
        self.assertNotIn(("DELETE", "/api/v1/cases/{id}"), self.by_key)

    def test_source_location_points_at_the_mapping_annotation(self):
        row = self.row("GET", "/api/v1/cases")
        self.assertEqual("backend/src/main/java/com/lawdesk/cases/CaseController.java", row.file)
        line = (self.root / row.file).read_text(encoding="utf-8").splitlines()[row.line - 1]
        self.assertIn("@GetMapping", line)

    def test_an_endpoint_without_a_rule_is_flagged_for_every_path(self):
        for path in ("/api/v1/cases/{id}/timeline", "/api/v1/cases/{id}/history"):
            with self.subTest(path=path):
                row = self.row("GET", path)
                self.assertEqual("NO_RULE", row.status)
                self.assertIsNone(row.rule)

    def test_constants_and_concatenation_are_resolved(self):
        self.assertEqual("hasAuthority('ROLE_VIEW') or hasAuthority('USER_MANAGE')", self.row("GET", "/api/v1/roles").rule)
        self.assertEqual("hasAnyAuthority('TASK_VIEW', 'REPORT_VIEW')", self.row("GET", "/api/v1/users/directory").rule)

    def test_a_class_level_rule_applies_when_the_method_has_none(self):
        row = self.row("GET", "/api/v1/roles/mine")
        self.assertEqual("isAuthenticated()", row.rule)
        self.assertEqual("AUTHENTICATED", row.status)

    def test_request_mapping_with_a_method_attribute(self):
        row = self.row("DELETE", "/api/v1/roles/{id}")
        self.assertEqual("NEEDS_REVIEW", row.status)  # refers to an argument: decided at invocation only

    def test_filter_chain_permit_all_makes_endpoints_public(self):
        self.assertEqual("PUBLIC", self.row("GET", "/api/v1/auth/csrf").status)
        self.assertEqual("PUBLIC", self.row("POST", "/api/v1/auth/login").status)
        health = self.row("GET", "/actuator/health/**")
        self.assertEqual("PUBLIC", health.status)
        self.assertTrue(health.file.endswith("SecurityConfig.java"))
        self.assertEqual("authenticated", self.matrix.fallback)

    def test_the_logout_filter_is_listed(self):
        self.assertEqual("FILTER", self.row("POST", "/api/v1/auth/logout").status)

    def test_default_roles_come_from_every_seed_migration(self):
        self.assertEqual({"ADMIN": {"CASE_VIEW", "CASE_EDIT", "ROLE_VIEW", "ROLE_MANAGE"},
                          "STAFF": {"CASE_VIEW", "TASK_VIEW", "REPORT_VIEW"}}, self.matrix.roles)

    def test_access_per_default_role(self):
        self.assertEqual({"ADMIN": True, "STAFF": False}, self.row("PUT", "/api/v1/cases/{id}").access)
        self.assertEqual({"ADMIN": True, "STAFF": False}, self.row("GET", "/api/v1/roles").access)
        self.assertEqual({"ADMIN": False, "STAFF": True}, self.row("GET", "/api/v1/users/directory").access)
        self.assertEqual({"ADMIN": True, "STAFF": True}, self.row("GET", "/api/v1/roles/mine").access)
        self.assertEqual({"ADMIN": None, "STAFF": None}, self.row("DELETE", "/api/v1/roles/{id}").access)

    def test_markdown_and_check_mode(self):
        out = io.StringIO()
        with redirect_stdout(out):
            code = endpoint_matrix.main(["--root", str(self.root), "--check"])
        self.assertEqual(1, code)  # the timeline endpoints have no rule
        text = out.getvalue()
        self.assertIn("| GET | `/api/v1/cases/{id}/timeline` |", text)
        self.assertIn("NO_RULE", text)

    def test_json_output(self):
        out = io.StringIO()
        with redirect_stdout(out):
            self.assertEqual(0, endpoint_matrix.main(["--root", str(self.root), "--format", "json"]))
        data = json.loads(out.getvalue())
        self.assertEqual("authenticated", data["fallback"])
        self.assertIn({"method": "GET", "path": "/api/v1/cases", "rule": "hasAuthority('CASE_VIEW')",
                       "status": "PERMISSION", "access": {"ADMIN": True, "STAFF": True},
                       "file": "backend/src/main/java/com/lawdesk/cases/CaseController.java", "line": 11},
                      data["endpoints"])


class RuleEvaluationTest(unittest.TestCase):
    def test_expressions(self):
        grant = {"A", "B"}
        cases = {
            "hasAuthority('A')": True,
            "hasAuthority('C')": False,
            "hasAnyAuthority('C', 'B')": True,
            "hasAuthority('A') and hasAuthority('C')": False,
            "hasAuthority('C') or (hasAuthority('A') and !hasAuthority('C'))": True,
            "isAuthenticated()": True,
            "permitAll()": True,
            "denyAll()": False,
            "@bean.check(#id)": None,
        }
        for expression, expected in cases.items():
            with self.subTest(expression=expression):
                self.assertEqual(expected, endpoint_matrix.evaluate(expression, grant))


class RepositoryTest(unittest.TestCase):
    """architecture/CONTRACTS.md §3: every endpoint declares its rule; only the sign-in endpoints are public."""

    def test_every_endpoint_on_this_commit_has_an_explicit_rule(self):
        matrix = endpoint_matrix.build(REPO_ROOT)
        self.assertGreater(len(matrix.endpoints), 10)
        self.assertEqual([], [f"{row.method} {row.path}" for row in matrix.endpoints if row.status == "NO_RULE"])

    def test_only_the_sign_in_and_health_endpoints_are_public(self):
        matrix = endpoint_matrix.build(REPO_ROOT)
        public = {(row.method, row.path) for row in matrix.endpoints if row.status == "PUBLIC"}
        allowed = {("GET", "/api/v1/auth/csrf"), ("POST", "/api/v1/auth/login"), ("GET", "/actuator/health"),
                   ("GET", "/actuator/health/**"), ("GET", "/actuator/info")}
        self.assertLessEqual(public, allowed)
        self.assertEqual("authenticated", matrix.fallback)


if __name__ == "__main__":
    unittest.main()
