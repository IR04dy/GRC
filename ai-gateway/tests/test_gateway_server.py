"""
Unit tests for gateway_server.py: master-key enforcement, no fabricated replies, no exception
text in 502s, Gemini key kept out of the URL, CORS allow-list, safe startup defaults, and the
reply cache (per tenant, keyed on model + parameters, bounded LRU with TTL, no flagged replies).

Standard library only. Run from the repository root:
  python -m unittest discover -s ai-gateway/tests -v
"""

import json
import os
import subprocess
import sys
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from unittest import mock

GATEWAY_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, GATEWAY_DIR)

import gateway_server  # noqa: E402

TEST_KEY = "test-master-key"
ALLOWED_ORIGIN = "https://grc.example"
UPSTREAM_ENV = ("GLM_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY")
GROUNDED_REPLY = "The NCA ECC governance domain covers cybersecurity governance."
# Cites an ECC domain and a PDPL article that do not exist, so the hallucination detector flags it.
FLAGGED_REPLY = "Apply NCA-ECC-9-1-1 and PDPL Article 199 to the governance domain."


def request(url, method="GET", body=None, headers=None):
    """Returns (status, parsed JSON body or None, headers), for success and HTTP errors alike."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None), resp.headers
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, (json.loads(raw) if raw else None), e.headers


class GatewayServerTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), gateway_server.GatewayRequestHandler)
        cls.base = f"http://127.0.0.1:{cls.server.server_address[1]}"
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        patches = [
            mock.patch.object(gateway_server, "MASTER_KEY", TEST_KEY),
            mock.patch.object(gateway_server, "CORS_ORIGINS", {ALLOWED_ORIGIN}),
            mock.patch.dict(os.environ, {}),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)
        for name in UPSTREAM_ENV:
            os.environ.pop(name, None)
        gateway_server.RESPONSE_CACHE.clear()

    def chat(self, key=TEST_KEY, content="Summarise the NCA ECC governance domain.", extra_headers=None,
             org=None, model="grc-default", **params):
        headers = {"Content-Type": "application/json"}
        if key is not None:
            headers["Authorization"] = f"Bearer {key}"
        if org is not None:
            headers["X-Organisation-Id"] = org
        headers.update(extra_headers or {})
        payload = {"model": model, "messages": [{"role": "user", "content": content}], **params}
        return request(f"{self.base}/v1/chat/completions", "POST", payload, headers)

    def fake_upstream(self, reply=GROUNDED_REPLY):
        """Stands in for GLM (every test model routes there); returns the payloads it was sent."""
        calls = []

        def upstream(payload, api_key, base_url):
            calls.append(payload)
            return {"id": "chatcmpl-test", "object": "chat.completion", "model": "glm-4.7",
                    "choices": [{"index": 0, "message": {"role": "assistant", "content": reply},
                                 "finish_reason": "stop"}],
                    "usage": {"prompt_tokens": 5, "completion_tokens": 7, "total_tokens": 12}}

        os.environ["GLM_API_KEY"] = "glm-test"
        patcher = mock.patch.object(gateway_server, "call_upstream_glm", upstream)
        patcher.start()
        self.addCleanup(patcher.stop)
        return calls

    # ── master key ───────────────────────────────────────────────────────────

    def test_chat_without_or_with_wrong_key_is_401(self):
        for key in (None, "wrong-key", ""):
            status, body, headers = self.chat(key=key)
            self.assertEqual(status, 401, key)
            self.assertEqual(body["error"]["type"], "authentication_error")
            self.assertEqual(headers.get("WWW-Authenticate"), "Bearer")

    def test_metrics_and_models_need_the_key_but_health_does_not(self):
        self.assertEqual(request(f"{self.base}/metrics")[0], 401)
        self.assertEqual(request(f"{self.base}/metrics/prometheus")[0], 401)
        self.assertEqual(request(f"{self.base}/v1/models")[0], 401)
        auth = {"Authorization": f"Bearer {TEST_KEY}"}
        self.assertEqual(request(f"{self.base}/metrics", headers=auth)[0], 200)
        self.assertEqual(request(f"{self.base}/health")[0], 200)

        prom_req = urllib.request.Request(f"{self.base}/metrics/prometheus", headers=auth)
        with urllib.request.urlopen(prom_req, timeout=5) as r:
            prom_text = r.read().decode("utf-8")
            self.assertEqual(r.status, 200)
            self.assertIn("ai_gateway_up 1", prom_text)
            self.assertIn("ai_gateway_requests_total", prom_text)

        with urllib.request.urlopen(f"{self.base}/dashboard", timeout=5) as r:
            dash_html = r.read().decode("utf-8")
            self.assertEqual(r.status, 200)
            self.assertIn("TSS GRC — AI Gateway", dash_html)
            self.assertIn(json.dumps(TEST_KEY), dash_html)

    def test_require_master_key_refuses_an_empty_key(self):
        with mock.patch.object(gateway_server, "MASTER_KEY", ""):
            with self.assertRaises(SystemExit):
                gateway_server.require_master_key()

    # ── no fabricated replies, no exception text ─────────────────────────────

    def test_no_upstream_answers_503_and_makes_nothing_up(self):
        status, body, headers = self.chat()

        self.assertEqual(status, 503)
        self.assertEqual(body["error"]["code"], "NO_UPSTREAM")
        self.assertNotIn("choices", body)
        self.assertEqual(headers.get("X-Guardrail-Pii-Masked"), "0")
        self.assertEqual(len(gateway_server.RESPONSE_CACHE), 0)

    def test_upstream_failure_502_keeps_exception_text_out(self):
        os.environ["GLM_API_KEY"] = "glm-test"
        boom = RuntimeError("upstream said secret-detail at https://upstream.example/?key=leak")
        with mock.patch.object(gateway_server, "call_upstream_glm", side_effect=boom):
            status, body, _ = self.chat()

        self.assertEqual(status, 502)
        self.assertEqual(body["error"]["message"], "Gateway upstream call failed.")
        self.assertNotIn("secret-detail", json.dumps(body))
        self.assertNotIn("leak", json.dumps(body))

    # ── Gemini key out of the URL ────────────────────────────────────────────

    def test_gemini_key_goes_in_a_header_not_the_url(self):
        captured = {}

        class FakeResponse:
            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

            def read(self):
                return json.dumps({"candidates": [{"content": {"parts": [{"text": "ok"}]}}]}).encode("utf-8")

        def fake_urlopen(req, timeout=None):
            captured["req"] = req
            return FakeResponse()

        with mock.patch.object(gateway_server.urllib.request, "urlopen", fake_urlopen):
            gateway_server.call_upstream_gemini(
                {"model": "gemini-1.5-flash", "messages": [{"role": "user", "content": "hi"}]}, "gemini-secret")

        req = captured["req"]
        self.assertNotIn("gemini-secret", req.full_url)
        self.assertNotIn("key=", req.full_url)
        self.assertEqual(req.get_header("X-goog-api-key"), "gemini-secret")

    # ── CORS allow-list ──────────────────────────────────────────────────────

    def test_cors_headers_only_for_an_allowed_origin(self):
        status, _, headers = request(f"{self.base}/v1/chat/completions", "OPTIONS",
                                     headers={"Origin": ALLOWED_ORIGIN})
        self.assertEqual(status, 204)
        self.assertEqual(headers.get("Access-Control-Allow-Origin"), ALLOWED_ORIGIN)

        status, _, headers = request(f"{self.base}/v1/chat/completions", "OPTIONS",
                                     headers={"Origin": "https://evil.example"})
        self.assertEqual(status, 204)
        self.assertIsNone(headers.get("Access-Control-Allow-Origin"))

        _, _, headers = self.chat(extra_headers={"Origin": "https://evil.example"})
        self.assertIsNone(headers.get("Access-Control-Allow-Origin"))

    # ── reply cache ──────────────────────────────────────────────────────────

    def test_cached_reply_is_served_only_to_the_same_tenant(self):
        calls = self.fake_upstream()

        status, first, headers = self.chat(org="101")
        self.assertEqual((status, headers.get("X-Cache")), (200, "MISS"))
        status, again, headers = self.chat(org="101")
        self.assertEqual((status, headers.get("X-Cache")), (200, "HIT"))
        self.assertEqual(again, first)
        _, _, headers = self.chat(org="202")
        self.assertEqual(headers.get("X-Cache"), "MISS")

        self.assertEqual(len(calls), 2)

    def test_cache_key_covers_model_exact_text_and_parameters(self):
        calls = self.fake_upstream()
        self.chat(org="101")

        variants = [
            {"model": "glm-4.7"},
            {"content": "summarise the nca ecc governance domain."},
            {"temperature": 0.9},
            {"max_tokens": 64},
            {"response_format": {"type": "json_object"}},
        ]
        for variant in variants:
            _, _, headers = self.chat(org="101", **variant)
            self.assertEqual(headers.get("X-Cache"), "MISS", variant)

        self.assertEqual(len(calls), 1 + len(variants))

    def test_request_that_names_no_tenant_is_not_cached(self):
        calls = self.fake_upstream()

        for _ in range(2):
            status, _, headers = self.chat()
            self.assertEqual((status, headers.get("X-Cache")), (200, "BYPASS"))

        self.assertEqual(len(calls), 2)
        self.assertEqual(len(gateway_server.RESPONSE_CACHE), 0)

    def test_hallucination_flagged_reply_is_never_cached(self):
        self.assertTrue(gateway_server.hallucination_detector.evaluate(
            FLAGGED_REPLY, "Summarise the NCA ECC governance domain.").is_hallucination)
        calls = self.fake_upstream(reply=FLAGGED_REPLY)

        for _ in range(2):
            status, _, headers = self.chat(org="101")
            self.assertEqual((status, headers.get("X-Cache")), (200, "MISS"))
            self.assertGreaterEqual(float(headers.get("X-Guardrail-Hallucination-Risk")), 0.5)

        self.assertEqual(len(calls), 2)
        self.assertEqual(len(gateway_server.RESPONSE_CACHE), 0)

    def test_server_keeps_at_most_max_entries(self):
        self.fake_upstream()
        with mock.patch.object(gateway_server, "RESPONSE_CACHE", gateway_server.ResponseCache(2, 60)):
            for n in range(3):
                self.chat(org="101", content=f"Summarise NCA ECC governance item {n}.")
            self.assertEqual(len(gateway_server.RESPONSE_CACHE), 2)


class ResponseCacheTest(unittest.TestCase):

    def setUp(self):
        self.now = 1000.0

    def cache(self, max_entries=2, ttl=60):
        return gateway_server.ResponseCache(max_entries, ttl, clock=lambda: self.now)

    def test_least_recently_used_entry_is_dropped_past_the_limit(self):
        cache = self.cache(max_entries=2)
        cache.put("a", {"n": 1})
        cache.put("b", {"n": 2})
        cache.get("a")  # "b" is now the least recently used
        cache.put("c", {"n": 3})

        self.assertEqual(len(cache), 2)
        self.assertIsNone(cache.get("b"))
        self.assertEqual(cache.get("a"), {"n": 1})
        self.assertEqual(cache.get("c"), {"n": 3})

    def test_entry_expires_ttl_after_it_was_stored_even_if_read_meanwhile(self):
        cache = self.cache(ttl=60)
        cache.put("a", {"n": 1})
        self.now += 59
        self.assertEqual(cache.get("a"), {"n": 1})
        self.now += 1

        self.assertIsNone(cache.get("a"))
        self.assertEqual(len(cache), 0)

    def test_zero_size_or_ttl_turns_caching_off(self):
        for cache in (self.cache(max_entries=0), self.cache(ttl=0)):
            cache.put("a", {"n": 1})
            self.assertIsNone(cache.get("a"))
            self.assertEqual(len(cache), 0)


class GatewayStartupTest(unittest.TestCase):
    """Runs the real module in a child process, so import-time defaults are exercised as shipped."""

    def clean_env(self):
        env = {k: v for k, v in os.environ.items()
               if k not in ("GATEWAY_MASTER_KEY", "GATEWAY_HOST", "GATEWAY_CORS_ORIGINS") + UPSTREAM_ENV}
        env["PYTHONIOENCODING"] = "utf-8"
        return env

    def test_default_bind_address_is_loopback(self):
        code = f"import sys; sys.path.insert(0, {GATEWAY_DIR!r}); import gateway_server as g; print(g.HOST)"
        out = subprocess.run([sys.executable, "-c", code], env=self.clean_env(),
                             capture_output=True, text=True, timeout=60, check=True)
        self.assertEqual(out.stdout.strip().splitlines()[-1], "127.0.0.1")

    def test_server_refuses_to_start_without_a_master_key(self):
        env = self.clean_env()
        env["GATEWAY_MASTER_KEY"] = ""
        env["GATEWAY_PORT"] = "0"
        proc = subprocess.run([sys.executable, os.path.join(GATEWAY_DIR, "gateway_server.py")],
                              env=env, capture_output=True, text=True, timeout=60)
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn("GATEWAY_MASTER_KEY is not set", proc.stderr)


if __name__ == "__main__":
    unittest.main()
