"""Run the shipped C# executable against a local mock API (no player required)."""
import json
import queue
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

snapshot = {"state": "playing", "position": 12000, "track": {
    "id": "smoke", "title": "Smoke Test", "artists": [{"name": "Artist"}], "duration": 180000}}
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        assert self.path == "/api/now-playing", self.path
        data = json.dumps(snapshot).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def log_message(self, *args): pass

server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
exe = Path(__file__).resolve().parents[2] / "Assets/AudioService/GetMusicStatus.exe"
lines = queue.Queue()
with tempfile.TemporaryDirectory(prefix="splayer-smoke-") as work:
    process = subprocess.Popen([str(exe), "--platform", "splayer-next", "--splayer-next-port",
        str(server.server_port)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, text=True, encoding="utf-8-sig", cwd=work)
    def collect():
        for line in process.stdout: lines.put(line.strip())
    threading.Thread(target=collect, daemon=True).start()
    def wait_for(expected):
        end = time.monotonic() + 12
        while time.monotonic() < end:
            if process.poll() is not None:
                raise AssertionError(f"Detector exited {process.returncode}: {process.stderr.read()}")
            try: line = lines.get(timeout=.25)
            except queue.Empty: continue
            if line == expected: return
        raise AssertionError(f"Did not receive {expected!r}")
    try:
        wait_for("Playing")
        wait_for("Smoke Test - Artist")
        wait_for("Progress:12|180")
        snapshot["state"] = "paused"
        wait_for("Paused")
        snapshot["position"] = 90000
        wait_for("Progress:90|180")
        snapshot["state"] = "stopped"
        wait_for("None")
        snapshot["state"] = "playing"
        wait_for("Playing")
        server.shutdown(); server.server_close()
        wait_for("None")
        print("PASS: shipped detector startup, custom port, metadata, pause, seek, stop, resume, disconnect")
    finally:
        if process.poll() is None:
            process.stdin.close()
            try: process.wait(timeout=5)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
        server.server_close()
