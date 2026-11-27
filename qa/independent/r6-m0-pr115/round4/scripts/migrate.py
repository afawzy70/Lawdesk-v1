import sys,re,glob,os,psycopg2
d=sys.argv[1]
fs=sorted(glob.glob(d+'/V*.sql'),key=lambda f:[int(x) for x in re.match(r'V([\d.]+)__',os.path.basename(f)).group(1).split('.')])
c=psycopg2.connect(host='127.0.0.1',port=int(__import__("os").environ.get("PGP","55432")),user='lawdesk_app',dbname='lawdesk',options='-c search_path=lawdesk'); c.autocommit=False
for f in fs:
    try:
        c.cursor().execute(open(f).read()); c.commit()
    except Exception as e:
        print('FAIL',f,e); sys.exit(1)
print('applied',len(fs))
