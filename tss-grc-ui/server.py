#!/usr/bin/env python3
"""
TSS GRC Platform — Static file server
Usage:  py server.py          (serves on http://localhost:8086)
        py server.py 8090     (custom port)
"""
import sys, os, http.server, socketserver

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8086
ROOT = os.path.dirname(os.path.abspath(__file__))   # tss-grc-ui directory

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def end_headers(self):
        # Allow CORS for API calls during development
        self.send_header('Access-Control-Allow-Origin', '*')
        # Disable caching during development
        self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
        self.send_header('Pragma', 'no-cache')
        super().end_headers()

    def log_message(self, fmt, *args):
        print(f"  {self.address_string()} {fmt % args}")

os.chdir(ROOT)
with socketserver.TCPServer(("", PORT), Handler) as httpd:
    httpd.allow_reuse_address = True
    print(f"\n  TSS GRC UI  ->  http://localhost:{PORT}")
    print(f"  Root: {ROOT}")
    print(f"  Press Ctrl+C to stop\n")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n  Server stopped.")
