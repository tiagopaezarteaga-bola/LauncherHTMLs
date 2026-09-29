import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class MiManejador(BaseHTTPRequestHandler):
    def do_GET(self):
        # Directorio donde está la app web (pasado desde Java)
        serve_dir = self.server.serve_dir
        
        # Resolver el archivo solicitado
        if self.path == '/':
            filepath = os.path.join(serve_dir, 'index.html')
        else:
            filepath = os.path.join(serve_dir, self.path.lstrip('/'))
            
        if os.path.exists(filepath) and os.path.isfile(filepath):
            self.send_response(200)
            
            # Adivinar tipo de contenido
            if filepath.endswith('.html'): self.send_header('Content-type', 'text/html; charset=utf-8')
            elif filepath.endswith('.js'): self.send_header('Content-type', 'application/javascript')
            elif filepath.endswith('.wasm'): self.send_header('Content-type', 'application/wasm')
            elif filepath.endswith('.css'): self.send_header('Content-type', 'text/css')
            else: self.send_header('Content-type', 'application/octet-stream')
            
           4 # CABECERAS MÁGICAS PARA WASM8 WASM MULTIHILO
            self.send_header('Cross-Origin-Opener-Policy', 'same-origin')
            self.send_header('Cross-Origin-Embedder-Policy', 'credentialless')
            self.end_headers()
            
            # Enviar archivo
            with open(filepath, 'rb') as f:
                self.wfile.write(f.read())
        else:
            self.send_error(404, "Archivo no encontrado")

def start_server(serve_dir=""):
    import socket
    puerto = 8000
    
    # Evitar error de puerto ocupado
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        if s.connect_ex(('localhost', puerto)) == 0:
            print("Servidor ya corriendo.")
            return

    servidor = ThreadingHTTPServer(('localhost', puerto), MiManejador)
    servidor.serve_dir = serve_dir # Guardar ruta en el objeto servidor
    print(f"Servidor corriendo en http://localhost:{puerto} sirviendo {serve_dir}")
    servidor.serve_forever()
def iniciar_servidor(serve_dir, port=8000):
    global SERVE_DIR
    SERVE_DIR = serve_dir
    
    import socket
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        if s.connect_ex(('localhost', port)) == 0:
            return # Ya corriendo
            
    servidor = ThreadingHTTPServer(('localhost', port), MiManejador)
    servidor.serve_forever()
