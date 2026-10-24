"""Independent real-HTTP/SQL tests. Owns only disposable loopback PostgreSQL and JVM.

No Docker, deployment, UAT writes or application source changes. Each assertion
includes a reproducible specification. Credentials/cookies are never evidence.
"""
import argparse
import concurrent.futures
import datetime
import http.client
from http.cookies import SimpleCookie
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import uuid
import sys
sys.path.insert(0, "/tmp/lawdesk-qa-python")
import pg8000.dbapi

PG = Path('/tmp/embedded-pg/PG-59e8d331a924ec3d78c088ab74e1aede/bin')
JAVA = '/home/aiagent/tools/jdk-21.0.12.1+1/bin/java'
INITIAL = 'QA-initial-synthetic-2026!'
CHANGED = 'QA-changed-synthetic-2026!'


def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


class Client:
    def __init__(self, p):
        self.port, self.cookies = p, {}

    def call(self, method, path, body=None, csrf=True):
        if method != 'GET' and csrf:
            self.call('GET', '/api/v1/auth/csrf')
        headers = {'Content-Type': 'application/json', 'X-Request-Id': 'qa-phase234'}
        if self.cookies:
            headers['Cookie'] = '; '.join(k+'='+v for k, v in self.cookies.items())
        if method != 'GET' and csrf and 'XSRF-TOKEN' in self.cookies:
            headers['X-XSRF-TOKEN'] = self.cookies['XSRF-TOKEN']
        conn = http.client.HTTPConnection('127.0.0.1', self.port, timeout=12)
        conn.request(method, path, None if body is None else json.dumps(body), headers)
        r = conn.getresponse()
        h = r.getheaders()
        for k, v in h:
            if k.lower() == 'set-cookie':
                c = SimpleCookie(); c.load(v)
                for name, value in c.items():
                    if value.value:
                        self.cookies[name] = value.value
                    else:
                        self.cookies.pop(name, None)
        raw = r.read().decode('utf-8', 'replace'); status = r.status; conn.close()
        try:
            data = json.loads(raw)
        except ValueError:
            data = raw[:300]
        return status, data, h

    def login(self, name, password=INITIAL):
        return self.call('POST', '/api/v1/auth/login', {'login': name, 'password': password})


class Review:
    def __init__(self, args):
        self.args, self.results = args, []
        self.output = Path(args.output); self.output.mkdir(parents=True, exist_ok=True)

    def check(self, id, stories, steps, expected, actual, good, priority='P1'):
        self.results.append(dict(id=id, story_ids=stories.split(','), priority=priority,
            preconditions=['Exact canonical git archive at tested_sha; migrated disposable PostgreSQL18.6; real jar on loopback',
                           'Synthetic bootstrap administrator and isolated test roles; no UAT credentials'],
            test_data={'namespace': 'QA synthetic', 'fixture': id}, steps=steps.split(' → '),
            expected_results=expected, actual=actual, status='PASS' if good else 'FAIL'))
        self.save()

    def save(self):
        counts = {s: sum(x['status'] == s for x in self.results) for s in ['PASS','FAIL','BLOCKED','NOT_RUN']}
        (self.output/'results.json').write_text(json.dumps(dict(tested_sha=self.args.sha,
            utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), candidate=self.args.candidate,
            scope='Independent API+PostgreSQL; real jar; no developer tests counted', counts=counts,
            results=self.results), ensure_ascii=False, indent=2)+'\n')

    def sql(self, text, fail=False):
        try:
            conn=pg8000.dbapi.connect(user='lawdesk_qa',host='127.0.0.1',port=self.dbport,database='postgres')
            conn.autocommit=True
            cur=conn.cursor(); cur.execute(text)
            rows=cur.fetchall() if cur.description else []
            out='\n'.join('\t'.join('t' if v is True else 'f' if v is False else str(v) if v is not None else '' for v in row) for row in rows)
            conn.close()
            return subprocess.CompletedProcess([],0,out,'')
        except Exception as e:
            if not fail:raise RuntimeError(str(e)[:500]) from e
            return subprocess.CompletedProcess([],1,'',str(e))

    def request(self, id, stories, client, method, path, body, status, rule=None):
        s, b, h = client.call(method,path,body)
        self.check(id, stories, f'{method} {path} → assert response',
            {'status':status, 'rule':rule}, {'status':s,'body':b},
            s == status and (rule is None or isinstance(b,dict) and b.get('rule') == rule))
        return b

    def execute(self):
        with tempfile.TemporaryDirectory(prefix='lawdesk-phase234-qa-') as tmp:
            root=Path(tmp); self.dbport=port(); appport=port(); data=root/'pg'
            subprocess.run([str(PG/'initdb'),'-D',str(data),'-A','trust','-U','lawdesk_qa','--no-locale','-E','UTF8'],check=True,stdout=subprocess.DEVNULL)
            subprocess.run([str(PG/'pg_ctl'),'-D',str(data),'-l',str(root/'pg.log'),'-o',f'-h 127.0.0.1 -p {self.dbport} -k {root}','-w','start'],check=True,stdout=subprocess.DEVNULL)
            proc=None
            try:
                secret=root/'bootstrap';secret.write_text(INITIAL);secret.chmod(0o600)
                env=os.environ.copy();env.update(SPRING_DATASOURCE_URL=f'jdbc:postgresql://127.0.0.1:{self.dbport}/postgres',SPRING_DATASOURCE_USERNAME='lawdesk_qa',SPRING_DATASOURCE_PASSWORD='',LAWDESK_RELEASE=self.args.sha)
                log=(self.output/'application.log').open('w')
                proc=subprocess.Popen([JAVA,'-jar',str(Path(self.args.candidate)/'backend/target/lawdesk-backend-0.0.1-SNAPSHOT.jar'),f'--server.port={appport}','--server.address=127.0.0.1','--lawdesk.bootstrap.admin.username=qaadmin','--lawdesk.bootstrap.admin.email=qaadmin@example.invalid',f'--lawdesk.bootstrap.admin.password-file={secret}'],env=env,stdout=log,stderr=subprocess.STDOUT)
                admin=Client(appport)
                for _ in range(180):
                    if proc.poll() is not None: raise RuntimeError('jar exited; inspect application.log')
                    try:
                        if admin.call('GET','/actuator/health/readiness')[0]==200 and self.sql('select count(*) from app_user').stdout.strip()=='1':break
                    except (OSError,RuntimeError):pass
                    time.sleep(.5)
                else:raise RuntimeError('startup timed out')
                self.run(admin,appport)
            except Exception as e:
                self.results.append(dict(id='FIXTURE-EXECUTION',status='BLOCKED',reason=str(e)))
                self.save()
                raise
            finally:
                if proc:
                    proc.terminate()
                    try:proc.wait(timeout=25)
                    except subprocess.TimeoutExpired:proc.kill();proc.wait()
                    log.close()
                subprocess.run([str(PG/'pg_ctl'),'-D',str(data),'-m','fast','-w','stop'],stdout=subprocess.DEVNULL,check=True)
        print(self.args.sha, {s:sum(x['status']==s for x in self.results) for s in ['PASS','FAIL','BLOCKED','NOT_RUN']})
        return int(any(x['status'] != 'PASS' for x in self.results))

    def run(self, admin, appport):
        anon=Client(appport)
        for path in ['/api/v1/me','/api/v1/cases','/api/v1/courts','/api/v1/users','/api/v1/audit']:
            self.request('ANON-'+path.rsplit('/',1)[1],'LD-007,LD-010',anon,'GET',path,None,401)
        s,b,h=admin.login(' QAADMIN ')
        cookies=[v for k,v in h if k.lower()=='set-cookie' and v.startswith('JSESSIONID=')]
        self.check('LOGIN-USERNAME','LD-007','Fetch CSRF → login with trimmed uppercase username','200; forced-change flag',{'status':s,'mustChangePassword':b.get('mustChangePassword')},s==200 and b.get('mustChangePassword') is True)
        self.check('SESSION-COOKIE','LD-007','Login → inspect Set-Cookie attributes','HttpOnly Secure SameSite=Lax',{'attributes': [v.split(';',1)[1] for v in cookies]},bool(cookies) and all(x in cookies[0] for x in ['HttpOnly','Secure','SameSite=Lax']))
        self.request('FORCED-CHANGE','LD-008',admin,'GET','/api/v1/cases',None,403,'PASSWORD_CHANGE_REQUIRED')
        self.request('CHANGE-PASSWORD','LD-008',admin,'POST','/api/v1/me/password',dict(currentPassword=INITIAL,newPassword=CHANGED),204)
        self.check('BCRYPT-STORAGE','LD-008','Change password → query stored encoding','Delegating bcrypt cost12; no plaintext',self.sql("select password_hash like '{bcrypt}$2%$12$%' from app_user where username='qaadmin'").stdout.strip(),self.sql("select password_hash like '{bcrypt}$2%$12$%' from app_user where username='qaadmin'").stdout.strip()=='t')
        # Seed users via SQL only in this disposable DB. Copy hash to avoid another hashing implementation.
        for name,role in [('staff','LEGAL_STAFF'),('manager','LEGAL_MANAGER'),('reader',None),('disabled',None),('locktest',None)]:
            self.sql(f"insert into app_user(username,email,display_name,password_hash,created_by,updated_by,enabled) select '{name}','{name}@example.invalid','QA {name}',password_hash,id,id,{'false' if name=='disabled' else 'true'} from app_user where username='qaadmin'")
            if role:self.sql(f"insert into user_role select u.id,r.id from app_user u,app_role r where u.username='{name}' and r.code='{role}'")
        staff=Client(appport);reader=Client(appport);manager=Client(appport)
        for c,n in [(staff,'staff'),(reader,'reader'),(manager,'manager')]:
            s,_,_=c.login(n,CHANGED);self.check('LOGIN-'+n,'LD-007','Login seeded role user','200',s,s==200)
        email=Client(appport);s,_,_=email.login(' QAADMIN@EXAMPLE.INVALID ',CHANGED)
        self.check('LOGIN-EMAIL','LD-007','Login trimmed case-insensitive email','200',s,s==200)
        unknown=Client(appport).login('does-not-exist',CHANGED);disabled=Client(appport).login('disabled',CHANGED)
        self.check('UNIFORM-REFUSAL','LD-007','Unknown and disabled login → compare problems','Identical401 bodies',{'unknown':unknown[:2],'disabled':disabled[:2]},unknown[0]==disabled[0]==401 and unknown[1]==disabled[1])
        self.request('READER-DENIED','LD-010',reader,'GET','/api/v1/cases/00000000-0000-4000-8000-000000000001',None,403)
        self.request('STAFF-COURT-VALID-WRITE-DENIED','LD-010,LD-018',staff,'POST','/api/v1/courts',dict(code='DENIED',nameAr='اختبار',nameEn='QA',level='FIRST_INSTANCE'),403)
        self.request('STAFF-COURT-WRITE-DENIED','LD-010,LD-018',staff,'POST','/api/v1/courts',{},403)
        s,_,_=staff.call('POST','/api/v1/cases',{},csrf=False)
        self.check('CSRF-REQUIRED','LD-007,LD-010','Authenticated POST without X-XSRF-TOKEN','403',s,s==403)
        case=dict(title=' QA قضية أمان ',caseType='CIVIL',openedOn='2026-01-01',subject='synthetic')
        created=self.request('CASE-CREATE','LD-012',staff,'POST','/api/v1/cases',case,201)
        cid=created['id'];base='/api/v1/cases/'+cid
        self.check('CASE-NORMALIZED','LD-012','Create case → inspect defaults and actor','Trimmed title; OPEN/version0; actor from session',created,created.get('title')==case['title'].strip() and created.get('status')=='OPEN' and created.get('version')==0)
        before=self.sql('select count(*) from legal_case').stdout.strip()
        self.request('CASE-BLANK','LD-012',staff,'POST','/api/v1/cases',{**case,'title':'  '},400)
        self.request('CASE-FUTURE','LD-012',staff,'POST','/api/v1/cases',{**case,'openedOn':'2099-01-01'},400)
        self.request('CASE-RESERVED','LD-012',staff,'POST','/api/v1/cases',{**case,'referenceNo':'LD-2026-99999'},422,'REFERENCE_NO_RESERVED')
        self.check('CASE-INVALID-ATOMIC','LD-012','Count cases before and after invalid requests','Count unchanged',self.sql('select count(*) from legal_case').stdout.strip(),self.sql('select count(*) from legal_case').stdout.strip()==before)
        self.request('CASE-NO-PERMISSION','LD-010,LD-012',reader,'POST','/api/v1/cases',case,403)
        custom={**case,'referenceNo':'QA-DUP'}
        self.request('CASE-CUSTOM','LD-012',staff,'POST','/api/v1/cases',custom,201)
        self.request('CASE-CASEFOLD-DUPLICATE','LD-012',staff,'POST','/api/v1/cases',{**custom,'referenceNo':'qa-dup'},409)
        self.request('CASE-UNKNOWN','LD-012',staff,'GET','/api/v1/cases/not-a-uuid',None,404)
        # Branch-specific availability is detected from source, never presumed implemented.
        src=Path(self.args.candidate)/'backend/src/main/java/com/lawdesk'
        if (src/'cases/CaseSearch.java').exists():
            self.request('CASE-SEARCH','LD-015',staff,'GET','/api/v1/cases?q='+__import__('urllib.parse',fromlist=['quote']).quote('قضية'),None,200)
            self.request('CASE-PAGE-NEGATIVE','LD-015',staff,'GET','/api/v1/cases?page=-1',None,400)
            self.request('CASE-SORT-INJECTION','LD-015',staff,'GET','/api/v1/cases?sort=title%3Bdrop,asc',None,400)
            edit={**case,'title':'Edited QA','version':0}
            self.request('CASE-EDIT','LD-013',staff,'PUT',base,edit,200)
            self.request('CASE-STALE','LD-013',staff,'PUT',base,edit,409)
        courtids={}
        for level in ['FIRST_INSTANCE','APPEAL','CASSATION']:
            court=self.request('COURT-'+level,'LD-018',admin,'POST','/api/v1/courts',dict(code='QA-'+level.replace('_','-'),nameAr='محكمة اختبار',nameEn='QA Court',level=level),201)
            courtids[level]=court['id']
        self.request('COURT-DUPLICATE','LD-018',admin,'POST','/api/v1/courts',dict(code='qa-first-instance',nameAr='محكمة',nameEn='Court',level='FIRST_INSTANCE'),409)
        # Direct SQL negatives prove actual database constraints, not mocked repositories.
        for id,sql in [('SQL-CASE-BLANK',f"update legal_case set title='   ' where id='{cid}'"),('SQL-CASE-ENUM',f"update legal_case set case_type='INVALID' where id='{cid}'"),('SQL-COURT-LEVEL',f"update court set level='INVALID' where id='{courtids['APPEAL']}'"),('SQL-AUDIT-APPEND-ONLY','update audit_event set action=\'CHANGED\'')]:
            r=self.sql(sql,fail=True);self.check(id,'LD-011,LD-012,LD-018',sql,'Database rejects; nonzero exit',{'exit':r.returncode,'error':r.stderr[:250]},r.returncode!=0)
        if (src/'cases/PartyController.java').exists():self.parties(admin,staff,cid)
        if (src/'proceedings/ProceedingController.java').exists():self.judicial(admin,staff,reader,cid,courtids)
        if (src/'users/UserController.java').exists():self.users(admin,staff,appport)
        # Session reloading: change permission and enabled state directly to isolate authentication from admin UI.
        self.sql("delete from user_role where user_id=(select id from app_user where username='staff')")
        self.request('ROLE-REVOCATION','LD-007,LD-010',staff,'GET','/api/v1/cases/00000000-0000-4000-8000-000000000001',None,403)
        self.sql("update app_user set enabled=false where username='staff'")
        self.request('DISABLE-SESSION','LD-007',staff,'GET','/api/v1/me',None,401)
        locker=Client(appport)
        for _ in range(5):locker.login('locktest','wrong-password')
        s,_,_=locker.login('locktest',CHANGED)
        self.check('LOCKOUT-FIVE','LD-008','Five wrong passwords → correct password','401; locked_until future',{'status':s},s==401 and self.sql("select locked_until>now() from app_user where username='locktest'").stdout.strip()=='t')
        self.request('LOGOUT','LD-007',email,'POST','/api/v1/auth/logout',None,204)
        self.request('LOGOUT-SESSION-INVALID','LD-007',email,'GET','/api/v1/me',None,401)
        audit=self.sql("select count(*) from audit_event where action='ACCESS_DENIED' and request_id='qa-phase234'").stdout.strip()
        self.check('AUDIT-DENIAL','LD-011','Deny protected operation → query correlated audit','At least one ACCESS_DENIED',audit,int(audit)>0)
        self.request('PASSWORD-73-CHAR-BOUNDARY','LD-008',admin,'POST','/api/v1/me/password',dict(currentPassword=CHANGED,newPassword='Q'*73),204)

    def parties(self,admin,staff,cid):
        base='/api/v1/cases/'+cid
        p=self.request('PARTY-CREATE','LD-016',staff,'POST','/api/v1/parties',dict(kind='PERSON',name='QA synthetic party',nationalId='12345678901234'),201)
        link=dict(partyId=p['id'],role='CLIENT')
        self.request('PARTY-LINK','LD-016',staff,'POST',base+'/parties',link,201)
        self.request('PARTY-DUPLICATE-LINK','LD-016',staff,'POST',base+'/parties',link,409)
        self.request('TIMELINE','LD-014',staff,'GET',base+'/timeline',None,200)
        current=admin.call('GET',base)[1]
        self.request('STATUS-INVALID','LD-017',admin,'POST',base+'/status',dict(status='ARCHIVED',version=current['version']),422)
        current=self.request('STATUS-ACTIVE','LD-017',admin,'POST',base+'/status',dict(status='ACTIVE',version=current['version']),200)
        self.request('STAFF-STATUS-DENIED','LD-017,LD-010',staff,'POST',base+'/status',dict(status='CLOSED',version=current['version']),403)

    def judicial(self,admin,staff,reader,cid,courts):
        base='/api/v1/cases/'+cid+'/proceedings'
        body=dict(stage='FIRST_INSTANCE',courtId=courts['FIRST_INSTANCE'],courtCaseNumber='100',courtCaseYear=2026,filingDate='2026-01-02')
        first=self.request('FIRST-INSTANCE','LD-019',staff,'POST',base,body,201)
        self.request('PROCEEDING-DUPLICATE','LD-019',staff,'POST',base,body,409)
        self.request('PROCEEDING-READER-DENIED','LD-010,LD-019',reader,'GET',base,None,403)
        appeal={**body,'stage':'APPEAL','courtId':courts['APPEAL'],'parentProceedingId':first['id']}
        self.request('APPEAL-WITHOUT-JUDGMENT','LD-020',staff,'POST',base,appeal,422,'APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT')
        judgment=dict(judgmentDate='2026-01-03',outcome='IN_FAVOUR',summary='QA synthetic judgment',final=False)
        self.request('STAFF-JUDGMENT-DENIED','LD-010,LD-022',staff,'POST','/api/v1/proceedings/'+first['id']+'/judgment',judgment,403)
        j=self.request('JUDGMENT','LD-022',admin,'POST','/api/v1/proceedings/'+first['id']+'/judgment',judgment,201)
        self.request('APPEAL-BEFORE-JUDGMENT','LD-020',staff,'POST',base,appeal,422,'FILED_BEFORE_PARENT_JUDGMENT')
        appeal['filingDate']='2026-01-04'
        ap=self.request('APPEAL','LD-020',staff,'POST',base,appeal,201)
        cass={**appeal,'stage':'CASSATION','courtId':courts['CASSATION'],'parentProceedingId':ap['id'],'filingDate':'2026-01-06'}
        self.request('CASSATION-NO-JUDGMENT','LD-021',staff,'POST',base,cass,422,'CASSATION_REQUIRES_APPEAL_JUDGMENT')
        self.request('APPEAL-JUDGMENT','LD-022',admin,'POST','/api/v1/proceedings/'+ap['id']+'/judgment',{**judgment,'judgmentDate':'2026-01-05'},201)
        self.request('CASSATION','LD-021',staff,'POST',base,cass,201)
        other=self.request('JUDICIAL-SECOND-CASE','LD-012',staff,'POST','/api/v1/cases',dict(title='other',caseType='CIVIL',openedOn='2026-01-01'),201)
        self.request('PARENT-OTHER-CASE','LD-020',staff,'POST','/api/v1/cases/'+other['id']+'/proceedings',{**appeal,'courtCaseNumber':'other'},422,'PARENT_PROCEEDING_OTHER_CASE')
        if (Path(self.args.candidate)/'backend/src/main/java/com/lawdesk/proceedings/SpecialDecisionController.java').exists():
            decision=dict(decisionType='ENFORCEMENT_SUSPENSION',decisionDate='2026-01-07',effect='GRANTED',summary='synthetic')
            path='/api/v1/proceedings/'+first['id']+'/special-decisions'
            self.request('SUSPENSION-NEEDS-JUDGMENT','LD-023',admin,'POST',path,decision,422)
            self.request('SUSPENSION','LD-023',admin,'POST',path,{**decision,'judgmentId':j['id']},201)
        # A pre-existing appeal must not be made chronologically impossible by editing its parent's judgment.
        self.request('PARENT-JUDGMENT-MOVED-AFTER-APPEAL','LD-020,LD-022',admin,'PUT','/api/v1/judgments/'+j['id'],{**judgment,'judgmentDate':'2026-01-10','version':j['version']},422)

    def users(self,admin,staff,appport):
        self.request('USER-LIST-DENIED','LD-009,LD-010',staff,'GET','/api/v1/users',None,403)
        self.request('USER-LIST','LD-009',admin,'GET','/api/v1/users',None,200)
        self.request('USER-SEARCH','LD-009',admin,'GET','/api/v1/users?q=qaadmin',None,200)
        user=dict(username='newuser',email='newuser@example.invalid',displayName='QA new user',locale='en',roles=['LEGAL_STAFF'],temporaryPassword=INITIAL)
        u=self.request('USER-CREATE','LD-009',admin,'POST','/api/v1/users',user,201)
        self.request('USER-DUPLICATE','LD-009',admin,'POST','/api/v1/users',{**user,'username':'NEWUSER'},409)
        self.request('USER-NULL-ROLE','LD-009',admin,'POST','/api/v1/users',{**user,'username':'badrole','email':'badrole@example.invalid','roles':[None]},400)
        self.request('USER-SELF-DISABLE','LD-009',admin,'PUT','/api/v1/users/'+self.sql("select id from app_user where username='qaadmin'").stdout.strip()+'/enabled',dict(enabled=False),422,'SELF_CHANGE')
        self.request('USER-STALE','LD-009',admin,'PUT','/api/v1/users/'+u['id'],dict(email=user['email'],displayName='Changed',locale='en',version=-1),409)


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    for name in ['candidate','sha','output']:parser.add_argument('--'+name,required=True)
    raise SystemExit(Review(parser.parse_args()).execute())
