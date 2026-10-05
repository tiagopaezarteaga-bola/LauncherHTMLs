import http.server
import socketserver
import os
import sys


class Handler(http.server.SimpleHTTPRequestHandler):

    def end_headers(self):
        self.send_header('Cross-Origin-Opener-Policy', 'same-origin')
        self.send_header('Cross-Origin-Embedder-Policy', 'credentialless')
        super().end_headers()

    def do_POST(self):
        try:
            length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(length)
            filename = self.path.lstrip('/')
            filename = os.path.basename(filename)
            if not filename:
                filename = 'upload.bin'
            with open(filename, 'wb') as f:
                f.write(body)
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain')
            self.end_headers()
            self.wfile.write(b'OK')
        except Exception as e:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(str(e).encode())

    def log_message(self, format, *args):
        pass


class ReusableServer(socketserver.TCPServer):
    allow_reuse_address = True


_httpd = None


def main(port, directory):
    global _httpd
    port = int(port)
    directory = str(directory)
    if not os.path.isdir(directory):
        print('ERROR_DIR_NOT_FOUND:' + directory)
        sys.stdout.flush()
        return
    try:
        os.chdir(directory)
    except Exception as e:
        print('ERROR_CHDIR:' + str(e))
        sys.stdout.flush()
        return
    try:
        _httpd = ReusableServer(('', port), Handler)
        print('SERVER_READY')
        sys.stdout.flush()
        _httpd.serve_forever()
    except Exception as e:
        print('ERROR_SERVER:' + str(e))
        sys.stdout.flush()


def stop():
    global _httpd
    if _httpd is not None:
        try:
            _httpd.shutdown()
        except Exception:
            pass
        try:
            _httpd.server_close()
        except Exception:
            pass
        _httpd = None
    print('SERVER_STOPPED')
    sys.stdout.flush()
