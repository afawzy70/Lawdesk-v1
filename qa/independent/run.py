"""Run contract-bound API/SQL probes. Exit 2 = incomplete; 1 = failed; 0 = verified.
No default application routes, credentials, database or expected statuses are invented.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import urllib.error
import urllib.parse

ROOT = Path(__file__).resolve().parents[2]

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def assert_http(status, body, headers, probe):
    assert status == probe['expected_status'], f"status: expected {probe['expected_status']}, got {status}"
    for forbidden in probe.get('forbidden_body_strings', []):
        assert forbidden not in body, 'forbidden response marker present'
    for key, expected in probe.get('expected_json_fields', {}).items():
        assert json.loads(body)[key] == expected, f'JSON field mismatch: {key}'
    for key, expected in probe.get('expected_headers', {}).items():
        assert headers.get(key) == expected, f'header mismatch: {key}'


def http(probe, base):
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme not in ('http','https') or parsed.hostname not in ('localhost','127.0.0.1','::1'):
        raise ValueError('Only explicit loopback QA targets supported by foundation')
    path = probe['path']
    if not path.startswith('/') or path.startswith('//') or '#' in path:
        raise ValueError('probe path must be an absolute application path')
    headers = {}
    for header, env_name in probe.get('header_env', {}).items():
        headers[header] = os.environ[env_name]
    payload = probe.get('json_body')
    if payload is not None:
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(base.rstrip('/')+path, data=None if payload is None else json.dumps(payload).encode(), headers=headers, method=probe['method'])
    try:
        response = urllib.request.build_opener(NoRedirect()).open(request, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status = response.code
        body = response.read(1048577)
        assert len(body) <= 1048576, 'response exceeds QA capture limit'
        assert_http(status, body.decode('utf-8'), response.headers, probe)
    return 'exact status and configured body/header assertions satisfied; response not persisted'


def sql(probe):
    # Credentials inherited by psql; never dump connection strings or query rows.
    if os.environ.get('PGHOST') not in ('localhost','127.0.0.1','::1') or not os.environ.get('PGDATABASE','').startswith('lawdesk_qa_'):
        raise ValueError('PGHOST must be loopback and PGDATABASE must start lawdesk_qa_')
    statement = probe['sql'].strip()
    if not statement.lower().startswith('select ') or ';' in statement:
        raise ValueError('Only a single SELECT assertion is accepted')
    completed = subprocess.run(['psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-c','BEGIN READ ONLY; '+statement+'; ROLLBACK;'], capture_output=True, text=True, timeout=20)
    assert completed.returncode == 0, 'psql failed (query output suppressed; inspect sanitized local diagnostics)'
    rows = [x for x in completed.stdout.strip().splitlines() if x not in ('BEGIN','ROLLBACK')]
    assert rows == [str(probe['expected_scalar'])], 'SQL scalar assertion failed'
    return 'read-only scalar assertion satisfied'


def run(contract_path, output):
    sha = subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    dirty = bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip())
    cases = json.loads((ROOT/'qa/specifications/refined-cases.json').read_text())['test_cases']
    report = dict(tested_sha=sha, dirty_worktree=dirty, utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), environment={'python':os.sys.version.split()[0], 'target':'loopback QA only'}, product_results=[{'id':c['id'],'status':'BLOCKED','reason':'No approved executable contract/implementation bound to this case.'} for c in cases], probes=[])
    if contract_path:
        raw = Path(contract_path).read_bytes(); contract = json.loads(raw)
        report['contract_sha256'] = hashlib.sha256(raw).hexdigest()
        if contract.get('tested_sha') != sha or not contract.get('approval_reference'):
            raise ValueError('Contract must name current tested_sha and approval_reference')
        known = {c['id'] for c in cases}
        for probe in contract.get('probes',[]):
            if not probe.get('case_ids') or not set(probe['case_ids']).issubset(known):
                raise ValueError('Probe requires known case_ids')
            result = {'id':probe['id'],'case_ids':probe['case_ids'],'status':'NOT_RUN'}
            try:
                if probe['kind'] == 'http':
                    detail = http(probe,os.environ['LAWDESK_QA_BASE_URL'])
                elif probe['kind'] == 'sql':
                    detail = sql(probe)
                else:
                    raise ValueError('Unknown probe kind')
                result.update(status='PASS',detail=detail)
            except (KeyError,FileNotFoundError) as error:
                result.update(status='BLOCKED',detail='Missing configured dependency: '+str(error))
            except Exception as error:
                # No body, request secrets or driver connection details in report.
                result.update(status='FAIL',detail=str(error) if isinstance(error, AssertionError) else type(error).__name__)
            report['probes'].append(result)
        # A probe passing is not evidence that every manual assertion in a case passed.
        for case in report['product_results']:
            related = [p for p in report['probes'] if case['id'] in p['case_ids']]
            if any(p['status']=='FAIL' for p in related):
                case.update(status='FAIL',reason='Linked executable probe failed; full case remains unverified.')
    report['counts'] = {s:sum(c['status']==s for c in report['product_results']) for s in ('PASS','FAIL','BLOCKED','NOT_RUN')}
    Path(output).write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'tested_sha':sha,'product_counts':report['counts'],'probe_count':len(report['probes'])}))
    return 1 if report['counts']['FAIL'] else 2 if report['counts']['BLOCKED'] or report['counts']['NOT_RUN'] else 0

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--contract')
    parser.add_argument('--output', default='qa/evidence/product-results.json')
    args = parser.parse_args()
    raise SystemExit(run(args.contract,args.output))
