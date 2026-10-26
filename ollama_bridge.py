
import json
import os
import secrets
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

HOST = "0.0.0.0"
PORT = 8765
MODEL = "gpt-oss:120b"
OLLAMA_URL = "https://ollama.com/v1/chat/completions"

API_KEY = os.environ.get("OLLAMA_API_KEY", "")
BRIDGE_TOKEN = os.environ.get("EDITH_BRIDGE_TOKEN", "")

if not API_KEY:
    raise SystemExit("Missing OLLAMA_API_KEY environment variable.")
if len(BRIDGE_TOKEN) < 32:
    raise SystemExit("Set EDITH_BRIDGE_TOKEN to a random secret of 32+ characters.")


class Handler(BaseHTTPRequestHandler):
    def respond(self, status, payload):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        # Avoid logging prompts, responses, or credentials.
        print("EDITH bridge:", fmt % args)

    def do_GET(self):
        if self.path != "/health":
            self.respond(404, {"error": "Not found"})
            return
        self.respond(200, {"ok": True, "model": MODEL})

    def do_POST(self):
        if self.path != "/chat":
            self.respond(404, {"error": "Not found"})
            return

        supplied = self.headers.get("X-Edith-Token", "")
        if not secrets.compare_digest(supplied, BRIDGE_TOKEN):
            self.respond(401, {"error": "Unauthorized bridge request"})
            return

        try:
            size = int(self.headers.get("Content-Length", "0"))
            if size < 1 or size > 64_000:
                self.respond(413, {"error": "Request must be under 64 KB"})
                return

            body = json.loads(self.rfile.read(size).decode("utf-8"))
            prompt = body.get("prompt", "")
            history = body.get("history", [])

            if not isinstance(prompt, str) or not prompt.strip():
                self.respond(400, {"error": "Prompt is required"})
                return
            if len(prompt) > 12000:
                self.respond(413, {"error": "Prompt is too long"})
                return
            if not isinstance(history, list) or len(history) > 20:
                self.respond(400, {"error": "Invalid conversation history"})
                return

            messages = [{
                "role": "system",
                "content": (
                    "You are EDITH, a helpful personal AI assistant. "
                    "Be clear, accurate, and concise."
                )
            }]

            for item in history:
                if not isinstance(item, dict):
                    continue
                role = item.get("role")
                content = item.get("content")
                if role in ("user", "assistant") and isinstance(content, str):
                    messages.append({
                        "role": role,
                        "content": content[:12000]
                    })

            messages.append({"role": "user", "content": prompt.strip()})

            payload = json.dumps({
                "model": MODEL,
                "messages": messages,
                "stream": False
            }).encode("utf-8")

            request = Request(
                OLLAMA_URL,
                data=payload,
                headers={
                    "Authorization": "Bearer " + API_KEY,
                    "Content-Type": "application/json"
                },
                method="POST"
            )

            # One request at a time per app executor; allow time for cloud inference.
            with urlopen(request, timeout=180) as response:
                result = json.loads(response.read().decode("utf-8"))

            answer = result["choices"][0]["message"]["content"]
            if not isinstance(answer, str) or not answer.strip():
                raise ValueError("Ollama returned an empty response")

            self.respond(200, {"answer": answer.strip(), "model": MODEL})

        except HTTPError as error:
            # Return a generic error; never expose provider headers or the API key.
            print("Ollama HTTP status:", error.code)
            self.respond(502, {
                "error": (
                    "Ollama rejected the request (HTTP "
                    + str(error.code)
                    + "). Check model access, quota, and API settings."
                )
            })
        except (URLError, TimeoutError):
            self.respond(504, {"error": "Ollama request timed out or is unreachable"})
        except (ValueError, KeyError, TypeError, json.JSONDecodeError):
            self.respond(502, {"error": "Invalid response or request data"})
        except Exception as error:
            print("Bridge error:", type(error).__name__)
            self.respond(500, {"error": "Bridge request failed"})


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print("EDITH Ollama bridge listening on port", PORT)
    print("Model:", MODEL)
    print("Keep this terminal open while testing. Press Ctrl+C to stop.")
    server.serve_forever()
