import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SERVE_DIR = ""

class MiManejador(BaseHTTPRequestHandler):
    def do_GET(self):
        if not SERVE_DIR:
            self.send_error(500, "Directorio no configurado")
            return

        path = self.path
        if path == '/':
            path = '/index.html'

        filepath = os.path.join(SERVE_DIR, path.lstrip('/'))
        
        if not os.path.isfile(filepath):
            self.send_error(404, "Archivo no encontrado")
            return

        self.send_response(200)
        self.send_header('Cross-Origin-Opener-Policy', 'same-origin')
        self.send_header('Cross-Origin-Embedder-Policy', 'credentialless')
        
        import mimetypes
        mimetype, _ = mimetypes.guess_type(filepath)
        if mimetype:
            self.send_header('Content-type', mimetype)
        else:
            self.send_header('Content-type', 'application/octet-stream')
        
        self.end_headers()
        with open(filepath, 'rb') as f:
            self.wfile.write(f.read())

def iniciar_servidor(serve_dir, port=8000):
    global SERVE_DIR
    SERVE_DIR = serve_dir
    
    import socket
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        if s.connect_ex(('localhost', port)) == 0:
            return # Ya corriendo
            
    servidor = ThreadingHTTPServer(('localhost', port), MiManejador)
    servidor.serve_forever()
