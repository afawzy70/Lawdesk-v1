"""QA-owned SPA fixture server. Mirrors candidate CSP; health is a controlled 503.
This is browser/visual evidence, NOT nginx, real API or real-container verification.
"""
import argparse
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from pathlib import Path
import re
from urllib.parse import urlsplit

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--candidate',required=True);parser.add_argument('--port',type=int,required=True);a=parser.parse_args()
    candidate=Path(a.candidate).resolve();dist=candidate/'frontend/dist'
    match=re.search(r'add_header Content-Security-Policy "([^"]+)"', (candidate/'frontend/nginx.conf').read_text())
    class Handler(SimpleHTTPRequestHandler):
        def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(dist),**kwargs)
        def end_headers(self):
            if match:self.send_header('Content-Security-Policy',match.group(1))
            self.send_header('X-Content-Type-Options','nosniff');self.send_header('X-Frame-Options','DENY');self.send_header('Referrer-Policy','no-referrer');super().end_headers()
        def do_GET(self):
            path=urlsplit(self.path).path
            if path.startswith('/actuator/') or path.startswith('/api/'):
                self.send_response(503);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(b'{"status":"DOWN"}');return
            if not path.startswith('/assets/') and not path.endswith(('.ico','.svg','.png')):self.path='/index.html'
            super().do_GET()
        def log_message(self,*args):pass
    ThreadingHTTPServer(('127.0.0.1',a.port),Handler).serve_forever()
