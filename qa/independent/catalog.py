"""Offline QA design validation; passing this does NOT verify the product."""
import json
from pathlib import Path


def validate_case(case, story_ids):
    errors = []
    for field in ('id','story_ids','title','preconditions','test_data','steps','expected_results','priority','execution_status','acceptance_trace'):
        if not case.get(field):
            errors.append(f"{case.get('id')}: missing {field}")
    if not set(case.get('story_ids', [])).issubset(story_ids):
        errors.append(f"{case.get('id')}: unknown story")
    if case.get('execution_status') not in ('NOT_RUN','BLOCKED','PASS','FAIL'):
        errors.append(f"{case.get('id')}: invalid status")
    if case.get('execution_status') == 'PASS' and not case.get('execution_evidence'):
        errors.append(f"{case.get('id')}: PASS without execution evidence")
    return errors


def validate(root):
    root = Path(root)
    stories = json.loads((root/'product/stories.json').read_text())['stories']
    original = json.loads((root/'qa/test-cases.json').read_text())['test_cases']
    refined = json.loads((root/'qa/specifications/refined-cases.json').read_text())['test_cases']
    ids = {s['id'] for s in stories}
    errors = []
    if len(stories) != 42 or len(original) != 351 or len(refined) != 351:
        errors.append('baseline counts changed; review explicitly')
    refined_ids = [c['id'] for c in refined]
    if len(set(refined_ids)) != len(refined_ids):
        errors.append('duplicate refined case IDs')
    if set(refined_ids) != {c['id'] for c in original}:
        errors.append('baseline cases missing or unexpected IDs')
    for case in refined:
        errors.extend(validate_case(case, ids))
    if set().union(*(set(c['story_ids']) for c in refined)) != ids:
        errors.append('uncovered story')
    return errors

if __name__ == '__main__':
    errors = validate(Path(__file__).resolve().parents[2])
    print(json.dumps({'scope':'QA design validation ONLY','errors':errors},indent=2))
    raise SystemExit(bool(errors))
