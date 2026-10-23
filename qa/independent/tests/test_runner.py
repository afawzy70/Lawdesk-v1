import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('runner', Path(__file__).resolve().parents[1]/'run.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

class SecurityOracleTests(unittest.TestCase):
    def test_redirect_cannot_pass_as_api_denial(self):
        with self.assertRaises(AssertionError):
            runner.assert_http(302, '', {}, {'expected_status':401})

    def test_hidden_record_leak_fails_even_when_status_matches(self):
        with self.assertRaises(AssertionError):
            runner.assert_http(403, 'hidden-case-B', {}, {'expected_status':403,'forbidden_body_strings':['hidden-case-B']})

    def test_wrong_field_value_fails(self):
        with self.assertRaises(AssertionError):
            runner.assert_http(200, '{"status":"DOWN"}', {}, {'expected_status':200,'expected_json_fields':{'status':'UP'}})

    def test_external_target_rejected_before_request(self):
        with self.assertRaises(ValueError):
            runner.http({'path':'/','method':'GET'}, 'https://example.com')

if __name__ == '__main__':
    unittest.main()
