import sys,re,psycopg2
u,db,f=sys.argv[1:4]
try:
    c=psycopg2.connect(host='127.0.0.1',port=int(__import__("os").environ.get("PGP","55432")),user=u,dbname=db); c.autocommit=True
except Exception as e:
    print('CONNECT-DENY as',u,str(e).splitlines()[0]); sys.exit()
for s in re.split(r'(?m)^;;\s*$',open(f).read()):
    t=s.strip()
    if not t: continue
    label=t.splitlines()[0]
    try:
        cur=c.cursor(); cur.execute(t)
        if cur.description:
            print('OK  ',label,'\n     ->',' || '.join(' | '.join(str(x) for x in r) for r in cur.fetchall()))
        else: print('OK  ',label)
    except Exception as e:
        print('DENY',label,'\n     ->',getattr(e,'pgcode',None),str(e).splitlines()[0])
