import importlib.util
from pathlib import Path
import unittest
spec = importlib.util.spec_from_file_location('phase1',Path(__file__).resolve().parents[1]/'phase1_review.py')
phase1 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(phase1)

class EvidenceRedactionTests(unittest.TestCase):
    def test_session_cookie_is_redacted_but_security_flags_remain(self):
        actual = phase1.redact_headers({'Set-Cookie':'JSESSIONID=QA-SECRET; Path=/; HttpOnly','Content-Type':'application/json'})
        self.assertEqual('JSESSIONID=[REDACTED]; Path=/; HttpOnly',actual['Set-Cookie'])
        self.assertEqual('application/json',actual['Content-Type'])

    def test_authorization_response_is_never_saved(self):
        self.assertEqual('[REDACTED]',phase1.redact_headers({'Authorization':'Bearer QA-SECRET'})['Authorization'])
