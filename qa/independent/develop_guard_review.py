"""Independent procedural-guard fixtures; never posts a QA verdict or calls GitHub."""
import argparse,copy,importlib.util,json,datetime
from pathlib import Path

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--candidate',required=True);p.add_argument('--sha',required=True);p.add_argument('--output',required=True);a=p.parse_args()
    spec=importlib.util.spec_from_file_location('candidate_guard',Path(a.candidate)/'tools/develop_guard.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    merge='a'*40;head='b'*40;before='c'*40;date='2026-10-23T10:00:00Z'
    comment={'created_at':'2026-10-23T09:00:00Z','updated_at':'2026-10-23T09:00:00Z','body':'QA-VERDICT: PASS '+head}
    pull={'number':100,'base':{'ref':'develop'},'head':{'sha':head},'merge_commit_sha':merge,'merged_at':date}
    rows=[]
    scenarios=[('VALID-CLEARED-MERGE',True,False,[comment],True,True),('DIRECT-PUSH',False,False,[comment],True,False),('FORCE-PUSH',True,True,[comment],True,False),('NO-VERDICT',True,False,[],True,False),('QUOTED-VERDICT',True,False,[{**comment,'body':'> '+comment['body']}],True,False),('FENCED-VERDICT',True,False,[{**comment,'body':'```\n'+comment['body']+'\n```'}],True,False),('POST-MERGE-EDIT',True,False,[{**comment,'updated_at':'2026-10-23T11:00:00Z'}],True,False),('LATER-FAIL',True,False,[comment,{**comment,'body':'QA-VERDICT: FAIL '+head}],True,False),('UNGUARDED-PARENT',True,False,[comment],False,False),('EXPLICIT-RECOVERY',True,False,[{**comment,'body':comment['body']+'\nQA-VERDICT: RECOVER '+before}],False,True)]
    for id,associated,forced,comments,parent_guarded,expected in scenarios:
        def api(path):return {'parents':[{'sha':before}]}
        def api_list(path,items='.[]'):
            if path.endswith('/pulls'):return [copy.deepcopy(pull)] if associated else []
            if '/comments?' in path:return copy.deepcopy(comments)
            if '/check-runs?' in path:return [{'name':m.GUARD_CHECK,'conclusion':'success','app':{'slug':'github-actions'}}] if parent_guarded else []
            raise AssertionError('Unexpected API path '+path)
        m.api=api;m.api_list=api_list;m.exists=lambda path:True
        errors=m.check('qa/fixture',merge,before,forced)
        ok=(not errors)==expected
        rows.append({'id':id,'story_ids':['LD-001','LD-005'],'priority':'P0','preconditions':['Exact candidate guard imported; GitHub API fixtures only; no actual verdict publication.'],'test_data':{'associated_pr':associated,'forced':forced,'parent_guarded':parent_guarded,'expected_accept':expected},'steps':['Execute actual guard check with named comments/history fixture.'],'expected_results':['Accept' if expected else 'Reject'],'actual_errors':errors,'status':'PASS' if ok else 'FAIL'})
    out=Path(a.output);out.mkdir(parents=True,exist_ok=True);report={'tested_sha':a.sha,'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Procedural guard only, not server-side branch protection or independent actor authentication.','results':rows,'counts':{s:sum(r['status']==s for r in rows) for s in ['PASS','FAIL']}};(out/'independent-develop-guard.json').write_text(json.dumps(report,indent=2)+'\n');print(report['counts'])
