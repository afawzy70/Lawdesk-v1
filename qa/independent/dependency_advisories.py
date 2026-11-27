"""Read-only SCA: identify public Maven coordinates inside built runtime JARs, query OSV.
No project code, credentials, environment or customer data is sent.
"""
import argparse,datetime,io,json,urllib.request,zipfile,hashlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--jar',required=True);p.add_argument('--sha',required=True);p.add_argument('--output',required=True);a=p.parse_args()
target=Path(a.output)
if target.exists():raise RuntimeError('Preserve previous evidence')
coordinates=[];unknown=[]
with zipfile.ZipFile(a.jar)as app:
    for name in app.namelist():
        if not name.startswith('BOOT-INF/lib/')or not name.endswith('.jar'):continue
        found=[]
        with zipfile.ZipFile(io.BytesIO(app.read(name)))as lib:
            for prop in lib.namelist():
                if prop.startswith('META-INF/maven/')and prop.endswith('/pom.properties'):
                    props=dict(line.split('=',1)for line in lib.read(prop).decode().splitlines()if '='in line and not line.startswith('#'))
                    if all(key in props for key in ['groupId','artifactId','version']):found.append({'name':props['groupId']+':'+props['artifactId'],'version':props['version']})
        if not found:
            # Exact byte identity against an existing public Maven-cache artifact gives
            # coordinates for JARs that omit pom.properties (not a filename guess).
            digest=hashlib.sha256(app.read(name)).digest()
            cache=Path('/tmp/lawdesk-qa-maven')
            for artifact in cache.rglob(name.rsplit('/',1)[-1]):
                if hashlib.sha256(artifact.read_bytes()).digest()==digest:
                    parts=artifact.relative_to(cache).parts
                    found.append({'name':'.'.join(parts[:-3])+':'+parts[-3],'version':parts[-2]});break
        if found:coordinates+=found
        else:unknown.append(name.rsplit('/',1)[-1])
coordinates=list({(x['name'],x['version']):x for x in coordinates}.values())
report={'tested_sha':a.sha,'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Runtime JAR embedded Maven pom.properties; advisory match, not exploitability proof','coordinates':coordinates,'unidentified_runtime_jars':unknown,'results':[]}
try:
    request=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=json.dumps({'queries':[{'package':{'ecosystem':'Maven','name':x['name']},'version':x['version']}for x in coordinates]}).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=45)as r:result=json.load(r)
    for coordinate,row in zip(coordinates,result['results']):report['results'].append({**coordinate,'advisories':row.get('vulns',[])})
    report['status']='COMPLETED_WITH_COVERAGE_LIMIT';report['matched_coordinates']=sum(bool(r['advisories'])for r in report['results'])
except Exception as e:report['status']='BLOCKED';report['reason']=type(e).__name__
target.write_text(json.dumps(report,indent=2));print(json.dumps({k:report[k]for k in ['status','matched_coordinates']if k in report}))
