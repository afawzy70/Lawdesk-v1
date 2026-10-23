"""Own a disposable PostgreSQL cluster for jar-level HTTP review; never use shared DBs."""
import argparse
import os
from pathlib import Path
import socket
import subprocess
import tempfile
from phase1_review import Review

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for name in ['candidate','sha','java','jar','output','pg-bin']: parser.add_argument('--'+name,required=True)
    args=parser.parse_args(); pg=Path(args.pg_bin).resolve()
    with tempfile.TemporaryDirectory(prefix='lawdesk-independent-pg-') as directory:
        root=Path(directory); data=root/'data'
        with socket.socket() as sock:
            sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
        subprocess.run([str(pg/'initdb'),'-D',str(data),'-A','trust','-U','lawdesk_qa','--no-locale','-E','UTF8'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
        subprocess.run([str(pg/'pg_ctl'),'-D',str(data),'-l',str(root/'postgres.log'),'-o',f'-h 127.0.0.1 -p {port} -k {root}','-w','start'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
        try:
            # Cluster is exclusively owned by this test and destroyed afterward.
            os.environ['SPRING_DATASOURCE_URL']=f'jdbc:postgresql://127.0.0.1:{port}/postgres'
            os.environ['SPRING_DATASOURCE_USERNAME']='lawdesk_qa'
            os.environ['SPRING_DATASOURCE_PASSWORD']=''
            def stop():
                subprocess.run([str(pg/'pg_ctl'),'-D',str(data),'-m','fast','-w','stop'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
            def start():
                subprocess.run([str(pg/'pg_ctl'),'-D',str(data),'-l',str(root/'postgres.log'),'-o',f'-h 127.0.0.1 -p {port} -k {root}','-w','start'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
            code=Review(args.candidate,args.sha,args.output).execute(args.java,args.jar,(stop,start))
        finally:
            subprocess.run([str(pg/'pg_ctl'),'-D',str(data),'-m','fast','-w','stop'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
    raise SystemExit(code)
