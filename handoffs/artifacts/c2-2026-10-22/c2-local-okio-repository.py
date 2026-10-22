from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import hashlib
import json
import signal
import sys
import threading

artifact_root = Path('/tmp/c2-okio-3.4.0.3')
prefix = '/com/squareup/okio/okio-jvm/3.4.0.3/'
artifacts = {}
for suffix in ('jar', 'pom'):
    name = 'okio-jvm-3.4.0.3.' + suffix
    data = (artifact_root / name).read_bytes()
    artifacts[prefix + name] = data
    artifacts[prefix + name + '.sha1'] = hashlib.sha1(data).hexdigest().encode()
    artifacts[prefix + name + '.sha256'] = hashlib.sha256(data).hexdigest().encode()
assert hashlib.sha256(artifacts[prefix + 'okio-jvm-3.4.0.3.jar']).hexdigest() == '3c4ceee561cdaa08f28214b911d5201b7d8ba540c647745e8ff93eebcccabd07'

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.respond(True)
    def do_HEAD(self):
        self.respond(False)
    def respond(self, body):
        if self.path.startswith(prefix):
            data = artifacts.get(self.path)
            if data is None:
                self.send_error(404, 'No local artifact at ' + self.path)
                return
            etag = '"' + hashlib.sha256(data).hexdigest() + '"'
            if self.headers.get('If-None-Match') == etag:
                self.send_response(304)
                self.send_header('ETag', etag)
                self.end_headers()
                return
            self.send_response(200)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Content-Type', 'application/octet-stream')
            self.send_header('ETag', etag)
            self.end_headers()
            if body:
                self.wfile.write(data)
        else:
            self.send_response(307)
            self.send_header('Location', 'https://kotlin.directory' + self.path)
            self.send_header('Content-Length', '0')
            self.end_headers()
    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)

server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
server.daemon_threads = True
port = server.server_port
Path('/tmp/c2-local-okio-repository-port').write_text(str(port))
Path('/tmp/c2-local-okio-mirrors.properties').write_text(
    'c2local.from=https://kotlin.directory;https://kotlin.directory/\n'
    + 'c2local.to=http://127.0.0.1:' + str(port) + '\n'
    + 'c2local.type=maven\n'
)
print(json.dumps({'port': port, 'coordinate': 'com.squareup.okio:okio-jvm:3.4.0.3'}), flush=True)

def stop(signum, frame):
    threading.Thread(target=server.shutdown, daemon=True).start()
signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)
try:
    server.serve_forever()
finally:
    server.server_close()
