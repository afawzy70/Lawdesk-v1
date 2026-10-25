"""QAD-037: the web container's nginx must let a 10 MiB PDF through, on the upload route only.

nginx refuses a body above `client_max_body_size` (default 1 MiB) with 413 before the backend sees it. The backend
accepts files up to 10 MiB in requests up to 11 MiB (spring.servlet.multipart in application.yaml). The web container
is read-only with a RAM-backed /tmp and a 64 MiB limit, so the route must stream the body instead of buffering it.
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
NGINX = (REPO_ROOT / "frontend" / "nginx.conf").read_text(encoding="utf-8")
APPLICATION = (REPO_ROOT / "backend" / "src" / "main" / "resources" / "application.yaml").read_text(encoding="utf-8")
UPLOAD_PATH = "/api/v1/cases/0b8f5a1e-3c3a-4b53-9a4e-0c6d7e8f9a10/documents"


def blocks():
    """The `location` blocks of nginx.conf as (modifier, pattern, body); nginx.conf has no nested braces inside them."""
    return [(m["mod"] or "", m["pattern"], m["body"])
            for m in re.finditer(r"location\s+(?P<mod>[=~*]*)\s*(?P<pattern>\S+)\s*\{(?P<body>[^}]*)\}", NGINX)]


def megabytes(value):
    number, unit = re.fullmatch(r"(\d+)([kKmM]?)", value).groups()
    return int(number) * {"": 1, "k": 1024, "m": 1024**2}[unit.lower()]


def upload_location():
    found = [b for b in blocks() if b[0].startswith("~") and "documents" in b[1]]
    return found[0] if found else None


class UploadRouteTest(unittest.TestCase):
    def test_there_is_a_dedicated_location_for_the_document_upload(self):
        self.assertIsNotNone(upload_location(), "nginx.conf has no regex location for /api/v1/cases/{id}/documents")

    def test_the_route_matches_the_upload_and_nothing_else(self):
        _, pattern, _ = upload_location()
        regex = re.compile(pattern)
        self.assertTrue(regex.search(UPLOAD_PATH))
        for other in ["/api/v1/cases", "/api/v1/cases/x/documents/y", "/api/v1/documents", "/api/v1/cases/x/notes",
                      "/api/v1/cases/x/documents/y/content", "/x" + UPLOAD_PATH]:
            with self.subTest(path=other):
                self.assertIsNone(regex.search(other), other)

    def test_it_allows_a_ten_mib_file_plus_multipart_overhead_exactly_as_the_backend_does(self):
        _, _, body = upload_location()
        limit = megabytes(re.search(r"client_max_body_size\s+(\S+?);", body).group(1))
        self.assertGreater(limit, 10 * 1024**2 + 64 * 1024, "10 MiB of file plus headers and boundaries must fit")
        backend = megabytes(re.search(r"max-request-size:\s*(\d+)MB", APPLICATION).group(1) + "m")
        self.assertEqual(backend, limit, "the proxy and the backend must agree on the request allowance")

    def test_the_body_is_streamed_not_buffered_because_tmp_is_ram_in_a_64_mib_container(self):
        _, _, body = upload_location()
        self.assertRegex(body, r"proxy_request_buffering\s+off;")
        # With HTTP/1.0 towards the backend nginx buffers a chunked body anyway, whatever the directive says.
        self.assertRegex(body, r"proxy_http_version\s+1\.1;")

    def test_it_forwards_exactly_what_the_generic_api_location_forwards(self):
        upload = upload_location()[2]
        generic = next(b for b in blocks() if b[1] == "/api/")[2]
        wanted = {re.sub(r"\s+", " ", h) for h in re.findall(r"proxy_set_header[^;]*;", generic)}
        self.assertEqual(wanted, {re.sub(r"\s+", " ", h) for h in re.findall(r"proxy_set_header[^;]*;", upload)})
        self.assertIn("proxy_pass $backend;", upload)

    def test_it_defines_no_add_header_so_the_server_level_security_headers_still_apply(self):
        # nginx drops inherited add_header directives in any location that defines one of its own.
        self.assertNotIn("add_header", upload_location()[2])


class EverythingElseKeepsTheSmallLimitTest(unittest.TestCase):
    def test_no_other_location_and_not_the_server_raises_the_body_limit(self):
        upload = upload_location()
        for mod, pattern, body in blocks():
            if (mod, pattern, body) == upload:
                continue
            with self.subTest(location=pattern):
                self.assertNotIn("client_max_body_size", body)
        outside = re.sub(r"location\s+[^{]*\{[^}]*\}", "", NGINX)
        self.assertNotIn("client_max_body_size", outside)


if __name__ == "__main__":
    unittest.main()
