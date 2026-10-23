import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]

class CatalogTests(unittest.TestCase):
    def test_all_baseline_cases_are_detailed_and_traceable(self):
        spec = importlib.util.spec_from_file_location('catalog', ROOT / 'qa/independent/catalog.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual([], module.validate(ROOT))

    def test_validator_rejects_missing_steps_and_unknown_story(self):
        spec = importlib.util.spec_from_file_location('catalog', ROOT / 'qa/independent/catalog.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        errors = module.validate_case({'id': 'QA-X', 'story_ids': ['LD-999'], 'steps': []}, {'LD-001'})
        self.assertTrue(any('steps' in e for e in errors))
        self.assertTrue(any('story' in e for e in errors))

if __name__ == '__main__':
    unittest.main()
