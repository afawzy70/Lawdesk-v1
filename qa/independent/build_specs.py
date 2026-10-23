"""Preserve baseline IDs; emit proposed test designs, never approved requirements."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def build():
    stories = json.loads((ROOT/'product/stories.json').read_text())['stories']
    baseline = json.loads((ROOT/'qa/test-cases.json').read_text())['test_cases']
    profiles = {}
    for line in (ROOT/'qa/independent/story-profiles.txt').read_text().splitlines():
        sid, action, data, oracle, gaps, edge = line.split('|')
        profiles[sid] = dict(action=action, data=data, oracle=oracle, decisions=gaps, edge=edge)
    cases = []
    for old in baseline[:336]:
        sid = old['story_id']; p = profiles[sid]; n = (int(old['id'][3:])-1)%8
        variants = [
            ([p['action'], 'Reload authoritative record/output and compare each fixture value.'], [p['oracle']]),
            (['Enumerate mandatory inputs/evidence from signed contract.', 'Omit each mandatory item independently, then submit whitespace and null separately.', 'Retry with the complete valid fixture.'], ['Each missing item has the approved field-specific rejection; no partial effect.', p['oracle']]),
            (['Run these edge fixtures independently: '+p['edge'], 'For every contract limit L, test L-1, L, L+1; record exact values and results.', 'Compare state before/after each rejected operation.'], ['Boundary matches signed rule; invalid input cannot corrupt state.', p['oracle']]),
            (['Run the operation without session/credential or protected repository access.', 'Repeat with expired/replayed credential and inspect returned data and side effects.'], ['Protected operation denied using documented transport status; no confidential data or mutation.', 'Explicitly public surfaces follow approved exposure policy.']),
            (['Run as qa.no-permission then qa.readonly, including direct request bypassing UI.', 'Substitute out-of-scope record IDs in path, query and body where applicable.', 'For repository/operations stories use insufficient repository/operator privileges.'], ['Permission and scope enforced at authoritative boundary; no partial writes or privileged data.']),
            (['Snapshot affected records, relationship counts and external effects.', p['action'], 'At an approved QA fault point interrupt a multi-step write and retry readback; for read-only operations compare state before/after.'], ['Transaction is atomic or documented compensation completes; no orphan rows, files or duplicate external effects.', 'Read-only operations do not change business state.']),
            (['Use two independent clients synchronized at the same starting state.', 'Execute '+p['edge'], 'Repeat identical input after timeout; inspect persisted output and history.'], ['Approved conflict/idempotency semantics hold; no silent overwrite, duplicate effect or scope leak.', p['oracle']]),
            ([p['action'], 'Inject documented timeout, validation rejection, unauthenticated and forbidden responses in the QA client/environment.', 'Retry once and verify focus, displayed state and authoritative record; for non-UI stories inspect operator/CI output instead.'], ['Loading/error/success are distinguishable and actionable; no raw secret/stack trace; retry follows approved semantics.', p['oracle']])
        ]
        steps, expected = variants[n]
        cases.append(dict(id=old['id'], story_ids=[sid], original_scenario=old['scenario'],
            title=p['action']+' — '+old['scenario'], priority='P0' if n in (3,4,5,6) else 'P1',
            preconditions=['Build and dependency versions recorded at exact tested SHA.', 'Disposable LawDesk-only environment; synthetic fixtures; no production/shared proxy writes.', 'Signed story contract resolves: '+p['decisions'], 'Seed qa.allowed, qa.no-permission, qa.readonly and out-of-scope record B where applicable.'],
            test_data={'story_fixture':p['data'], 'edge_fixture':p['edge'], 'run_id':'QA-<sha12>-<case-id>-<attempt>'},
            steps=steps, expected_results=expected,
            acceptance_trace=[sid+'#AC-'+str(1 if n in (0,7) else 2 if n in (1,2,5,6) else 3)],
            level='repository/operations' if sid in ['LD-001','LD-005','LD-006','LD-039','LD-041','LD-042'] else old['level'],
            execution_status='BLOCKED', design_status='PROPOSED_REQUIRES_CONTRACT',
            blockers=['No product implementation at baseline SHA.', 'Unresolved story contract: '+p['decisions']],
            automation_status='PLANNED', evidence_required=['tested SHA and environment','fixture values and seed digest','command/request/UI actions','actual response and state comparison','redacted assertion/log evidence']))
    cross = json.loads((ROOT/'qa/independent/security-designs.json').read_text())
    for old, c in zip(baseline[336:], cross):
        c.update(id=old['id'], original_scenario=old['scenario'], priority='P0', execution_status='BLOCKED', design_status='PROPOSED_REQUIRES_CONTRACT', automation_status='PLANNED', blockers=['No product implementation or approved security contract.'], acceptance_trace=[sid+'#AC-3' for sid in c['story_ids']], evidence_required=['tested SHA and environment','redacted request/response','state before/after','failed assertion or control evidence'])
        cases.append(c)
    (ROOT/'qa/specifications/refined-cases.json').write_text(json.dumps({'schema':'independent-qa-v1','baseline_sha':'8ff061cf59c1bdd2e2ef41c2cc6fbc53acc0af4d','approval':'PROPOSED; baseline documents unchanged','count':len(cases),'test_cases':cases},ensure_ascii=False,indent=2)+'\n')
    return stories, profiles

if __name__ == '__main__':
    build()
