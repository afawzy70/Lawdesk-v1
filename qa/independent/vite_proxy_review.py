"""Repeat the existing Vite AC9 probe against a QA-owned unavailable endpoint."""
import argparse,json,os,socket,subprocess,tempfile,time,urllib.request,urllib.error
from pathlib import Path

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--candidate',required=True);p.add_argument('--sha',required=True);p.add_argument('--output',required=True);a=p.parse_args();root=Path(a.candidate)/'frontend';out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
    with socket.socket() as backend,socket.socket() as frontend,tempfile.TemporaryFile(mode='w+') as log:
        backend.bind(('127.0.0.1',0));front_port=None;frontend.bind(('127.0.0.1',0));front_port=frontend.getsockname()[1];frontend.close()
        env={**os.environ,'LAWDESK_API_ORIGIN':f'http://127.0.0.1:{backend.getsockname()[1]}'}
        process=subprocess.Popen(['./node_modules/.bin/vite','--host','127.0.0.1','--port',str(front_port),'--strictPort'],cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
        try:
            for _ in range(60):
                try:rootstatus=urllib.request.urlopen(f'http://127.0.0.1:{front_port}/',timeout=1).status;break
                except (OSError,urllib.error.URLError):time.sleep(.1)
            else:raise RuntimeError('QA Vite did not start')
            try:status=urllib.request.urlopen(f'http://127.0.0.1:{front_port}/actuator/health',timeout=5).status
            except urllib.error.HTTPError as e:status=e.code
            rows=[]
            for id,actual,expected in [('VITE-SHELL',rootstatus,200),('VITE-MISSING-BACKEND-CONTRACT',status,502)]:
                rows.append({'id':id,'story_ids':['LD-003'],'priority':'P1','preconditions':['Pinned frontend installed; owned non-listening backend socket.'],'test_data':{'backend':'owned loopback socket'},'steps':['Start candidate Vite with explicit backend override; GET named resource.'],'expected_results':[f'HTTP{expected}'],'actual':actual,'status':'PASS' if actual==expected else 'FAIL'})
            (out/'independent-vite-proxy.json').write_text(json.dumps({'tested_sha':a.sha,'scope':'Real Vite; no unrelated backend contacted.','results':rows},indent=2)+'\n');print([(r['id'],r['status']) for r in rows])
        finally:process.terminate();process.wait(timeout=10)
