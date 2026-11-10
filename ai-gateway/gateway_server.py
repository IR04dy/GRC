"""
Standalone AI Gateway Server for TSS GRC Platform
- OpenAI-compatible API on port 4000 (POST /v1/chat/completions and POST /chat/completions)
- In-memory reply cache: exact match on tenant (X-Organisation-Id) + model + the whole masked
  request, bounded LRU with TTL. Replies flagged as likely hallucinations are never cached, and
  requests that name no tenant are not cached at all.
- Open-Source Guardrails:
    * Pre-Call: Prompt Injection & Jailbreak Defense (Blocks with HTTP 400)
    * Pre-Call: PII Masking & Redaction (Saudi PDPL National ID, Iqama, Phone, IBAN, Email)
    * Post-Call: Hallucination Detection (GRC control validation & fact overlap)
- Upstream Forwarding (Z.ai GLM, OpenAI, Gemini). With no upstream configured the gateway
  answers HTTP 503; it never makes up a reply.
- Telemetry & Metrics (GET /metrics, GET /health)
- Every endpoint except /health needs "Authorization: Bearer <GATEWAY_MASTER_KEY>". The server
  will not start without that key, listens on 127.0.0.1 unless GATEWAY_HOST says otherwise,
  and sends CORS headers only to origins listed in GATEWAY_CORS_ORIGINS.
"""

import hmac
import json
import logging
import os
import sys
import time
import hashlib
import re
import threading
from collections import OrderedDict
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from typing import Dict, Any, Optional
import urllib.request
import urllib.error

# Ensure guardrails package is loadable
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def load_dotenv():
    """Load key-value pairs from .env into os.environ if not already present."""
    env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if os.path.exists(env_path):
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    k = k.strip()
                    v = v.strip().strip("'\"")
                    if k and k not in os.environ:
                        os.environ[k] = v


load_dotenv()

from guardrails.pii_masker import PiiMasker
from guardrails.prompt_injection import PromptInjectionDetector
from guardrails.hallucination import HallucinationDetector
from guardrails.safety_filter import SafetyGuardrail

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] [AIGateway] %(message)s")
logger = logging.getLogger("AIGateway")

PORT = int(os.environ.get("GATEWAY_PORT", "4000"))
HOST = os.environ.get("GATEWAY_HOST", "127.0.0.1")
# No default: the server refuses to start without a key (see require_master_key).
MASTER_KEY = os.environ.get("GATEWAY_MASTER_KEY", "").strip()
# Browser origins allowed to call the gateway, comma-separated. Empty: no CORS headers at all.
CORS_ORIGINS = {o.strip() for o in os.environ.get("GATEWAY_CORS_ORIGINS", "").split(",") if o.strip()}
CACHE_TTL_SECONDS = int(os.environ.get("GATEWAY_CACHE_TTL", "3600"))
# Most replies kept at once; past this the least recently used one is dropped. 0 turns the cache off.
CACHE_MAX_ENTRIES = int(os.environ.get("GATEWAY_CACHE_MAX_ENTRIES", "1000"))


class ResponseCache:
    """Thread-safe LRU of upstream replies.

    An entry expires ttl_seconds after it was stored (a hit does not extend it). Past max_entries
    the least recently used entry is dropped. A size or TTL of 0 turns caching off.
    """

    def __init__(self, max_entries: int, ttl_seconds: float, clock=time.monotonic):
        self.max_entries = max_entries
        self.ttl_seconds = ttl_seconds
        self._clock = clock
        self._entries: "OrderedDict[str, tuple]" = OrderedDict()
        self._lock = threading.Lock()

    def get(self, key: str) -> Optional[dict]:
        with self._lock:
            entry = self._entries.get(key)
            if entry is None:
                return None
            stored_at, response = entry
            if self._clock() - stored_at >= self.ttl_seconds:
                del self._entries[key]
                return None
            self._entries.move_to_end(key)
            return response

    def put(self, key: str, response: dict):
        if self.max_entries <= 0 or self.ttl_seconds <= 0:
            return
        with self._lock:
            self._entries[key] = (self._clock(), response)
            self._entries.move_to_end(key)
            while len(self._entries) > self.max_entries:
                self._entries.popitem(last=False)

    def clear(self):
        with self._lock:
            self._entries.clear()

    def __len__(self):
        with self._lock:
            return len(self._entries)


# Global In-Memory Cache and Metrics State
RESPONSE_CACHE = ResponseCache(CACHE_MAX_ENTRIES, CACHE_TTL_SECONDS)
METRICS = {
    "total_requests": 0,
    "cache_hits": 0,
    "cache_misses": 0,
    "pii_masked_count": 0,
    "injections_blocked": 0,
    "safety_violations_blocked": 0,
    "hallucinations_detected": 0,
    "errors": 0,
    "uptime_start": time.time()
}

# Initialize Guardrails
pii_masker = PiiMasker()
injection_detector = PromptInjectionDetector(block_threshold=0.80)
safety_guardrail = SafetyGuardrail()
hallucination_detector = HallucinationDetector(hallucination_threshold=0.65)


def generate_cache_key(tenant: str, model: str, payload: dict) -> str:
    """SHA-256 over the tenant, the model and the whole (already PII-masked) request body.

    Every request field is in the key, so a reply is reused only for the same tenant, model, exact
    message text and parameters (temperature, max_tokens, response_format, ...).
    """
    canonical = json.dumps({"tenant": tenant, "model": model, "request": payload},
                           sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def call_upstream_glm(payload: dict, api_key: str, base_url: str = "https://api.z.ai/api/paas/v4") -> dict:
    """Forward request to Z.ai / Zhipu AI GLM (OpenAI-compatible)."""
    base = base_url.rstrip("/")
    url = f"{base}/chat/completions" if not base.endswith("/chat/completions") else base

    # Default to glm-4.7 if model is generic or unset
    model = payload.get("model", "glm-4.7")
    if model in ("grc-default", "default", "glm", "gpt-4o-mini"):
        model = "glm-4.7"

    glm_payload = dict(payload)
    glm_payload["model"] = model
    # Set reasonable default output ceiling if unset
    if "max_tokens" not in glm_payload or not glm_payload["max_tokens"]:
        glm_payload["max_tokens"] = 1024
    # Disable internal chain-of-thought to prevent 120s timeout on heavy text
    glm_payload["thinking"] = {"type": "disabled"}

    data_bytes = json.dumps(glm_payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data_bytes,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}"
        },
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def call_upstream_openai(payload: dict, api_key: str) -> dict:
    """Forward request to upstream OpenAI."""
    url = "https://api.openai.com/v1/chat/completions"
    data_bytes = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data_bytes,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}"
        },
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=45) as resp:
        return json.loads(resp.read().decode("utf-8"))


def call_upstream_gemini(payload: dict, api_key: str) -> dict:
    """Translate payload and forward to Google Gemini."""
    model = payload.get("model", "gemini-1.5-flash")
    if "/" in model:
        model = model.split("/")[-1]
    # The key goes in a header, not the query string: URLs end up in logs and tracebacks.
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

    contents = []
    system_instruction = None
    for m in payload.get("messages", []):
        role = m.get("role")
        content = m.get("content", "")
        if role == "system":
            system_instruction = {"parts": [{"text": content}]}
        else:
            gemini_role = "model" if role == "assistant" else "user"
            contents.append({"role": gemini_role, "parts": [{"text": content}]})

    gemini_body: Dict[str, Any] = {"contents": contents}
    if system_instruction:
        gemini_body["systemInstruction"] = system_instruction
    
    gen_cfg: Dict[str, Any] = {}
    if "temperature" in payload:
        gen_cfg["temperature"] = payload["temperature"]
    if "max_tokens" in payload:
        gen_cfg["maxOutputTokens"] = payload["max_tokens"]
    if payload.get("response_format", {}).get("type") == "json_object":
        gen_cfg["responseMimeType"] = "application/json"
    if gen_cfg:
        gemini_body["generationConfig"] = gen_cfg

    data_bytes = json.dumps(gemini_body).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data_bytes,
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=45) as resp:
        raw = json.loads(resp.read().decode("utf-8"))

    # Convert Gemini response to OpenAI format
    text = ""
    finish_reason = "stop"
    candidates = raw.get("candidates", [])
    if candidates:
        first = candidates[0]
        finish_reason = first.get("finishReason", "stop").lower()
        parts = first.get("content", {}).get("parts", [])
        text = "".join([p.get("text", "") for p in parts if "text" in p])

    usage = raw.get("usageMetadata", {})
    return {
        "id": f"chatcmpl-{int(time.time()*1000)}",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": model,
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": text},
                "finish_reason": finish_reason
            }
        ],
        "usage": {
            "prompt_tokens": usage.get("promptTokenCount", 10),
            "completion_tokens": usage.get("candidatesTokenCount", 20),
            "total_tokens": usage.get("totalTokenCount", 30)
        }
    }


def require_master_key():
    """Refuse to start without a master key: an unauthenticated gateway spends upstream credit for anyone."""
    if not MASTER_KEY:
        raise SystemExit("GATEWAY_MASTER_KEY is not set. Set it to the backend's AI_GATEWAY_API_KEY value.")


class GatewayRequestHandler(BaseHTTPRequestHandler):

    def _cors_headers(self) -> dict:
        """CORS headers for an allow-listed Origin only; anything else gets none, so browsers block it."""
        origin = self.headers.get("Origin")
        if not origin or origin not in CORS_ORIGINS:
            return {}
        return {
            "Access-Control-Allow-Origin": origin,
            "Vary": "Origin",
            "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Organisation-Id, X-Feature-Code",
            "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        }

    def _authorized(self) -> bool:
        scheme, _, token = self.headers.get("Authorization", "").partition(" ")
        if not MASTER_KEY or scheme.lower() != "bearer":
            return False
        return hmac.compare_digest(token.strip().encode("utf-8"), MASTER_KEY.encode("utf-8"))

    def _reject_unauthorized(self):
        self._send_json(401, {
            "error": {"message": "Missing or invalid gateway API key.", "type": "authentication_error"}
        }, headers={"WWW-Authenticate": "Bearer"})

    def _send_json(self, status: int, data: dict, headers: Optional[dict] = None):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        for k, v in {**self._cors_headers(), **(headers or {})}.items():
            self.send_header(k, str(v))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        for k, v in self._cors_headers().items():
            self.send_header(k, v)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        if self.path in ("/health", "/healthz"):
            self._send_json(200, {
                "status": "healthy",
                "service": "TSS GRC AI Gateway",
                "cache_size": len(RESPONSE_CACHE),
                "uptime_seconds": round(time.time() - METRICS["uptime_start"], 1)
            })
        elif not self._authorized():
            self._reject_unauthorized()
        elif self.path in ("/metrics", "/v1/metrics"):
            hit_rate = 0.0
            if (METRICS["cache_hits"] + METRICS["cache_misses"]) > 0:
                hit_rate = round(METRICS["cache_hits"] / (METRICS["cache_hits"] + METRICS["cache_misses"]) * 100, 2)
            self._send_json(200, {
                **METRICS,
                "cache_hit_rate_pct": hit_rate,
                "active_cache_entries": len(RESPONSE_CACHE),
                "cache_max_entries": RESPONSE_CACHE.max_entries,
                "cache_ttl_seconds": RESPONSE_CACHE.ttl_seconds,
                "guardrails": {
                    "pii_masking_enabled": True,
                    "prompt_injection_defense_enabled": True,
                    "hallucination_detection_enabled": True
                }
            })
        elif self.path in ("/v1/models", "/models"):
            self._send_json(200, {
                "object": "list",
                "data": [
                    {"id": "grc-default", "object": "model", "owned_by": "tss-grc"},
                    {"id": "gpt-4o-mini", "object": "model", "owned_by": "openai"},
                    {"id": "gemini-1.5-flash", "object": "model", "owned_by": "google"}
                    # No local model: this server has no Ollama/vLLM route, so it does not advertise one.
                ]
            })
        else:
            self._send_json(404, {"error": "Not Found"})

    def do_POST(self):
        METRICS["total_requests"] += 1
        start_time = time.time()

        if self.path not in ("/chat/completions", "/v1/chat/completions"):
            self._send_json(404, {"error": "Endpoint not found. Use /chat/completions or /v1/chat/completions"})
            return

        if not self._authorized():
            self._reject_unauthorized()
            return

        # 1. Read & Parse Payload
        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body_bytes = self.rfile.read(content_length)
            payload = json.loads(body_bytes.decode("utf-8"))
        except Exception as e:
            METRICS["errors"] += 1
            self._send_json(400, {"error": {"message": f"Malformed JSON: {str(e)}", "type": "invalid_request_error"}})
            return

        model = payload.get("model", "grc-default")
        messages = payload.get("messages", [])

        # 2. GUARDRAIL 1: SAFETY, CREDENTIAL PROTECTION, & PROMPT INJECTION DEFENSE (Pre-Call)
        for msg in messages:
            content = msg.get("content", "")
            role = msg.get("role", "user")
            if role in ("user", "assistant") and content:
                # Check 1a: Credential Probe & Threat Safety Filter
                safety_res = safety_guardrail.evaluate(content)
                if not safety_res.is_safe:
                    METRICS["safety_violations_blocked"] = METRICS.get("safety_violations_blocked", 0) + 1
                    logger.warning(f"BLOCKED: Safety Violation ({safety_res.violation_category}): {[v.pattern_name for v in safety_res.violations]}")
                    self._send_json(400, {
                        "error": {
                            "message": safety_res.rejection_message,
                            "type": "guardrail_violation",
                            "code": safety_res.violation_category,
                            "risk_score": safety_res.risk_score,
                            "violations": [v.pattern_name for v in safety_res.violations],
                            "details": [v.description for v in safety_res.violations]
                        }
                    })
                    return

                # Check 1b: Adversarial Prompt Injection & Jailbreak
                inj_result = injection_detector.detect(content)
                if inj_result.action == "BLOCK":
                    METRICS["injections_blocked"] += 1
                    logger.warning(f"BLOCKED: Prompt Injection detected (score={inj_result.risk_score}): {[v.pattern_name for v in inj_result.violations]}")
                    self._send_json(400, {
                        "error": {
                            "message": "Request was blocked by enterprise GRC security guardrails (Prompt Injection Detected).",
                            "type": "guardrail_violation",
                            "code": "PROMPT_INJECTION_DETECTED",
                            "risk_score": inj_result.risk_score,
                            "violations": [v.pattern_name for v in inj_result.violations]
                        }
                    })
                    return

        # 3. GUARDRAIL 2: PII MASKING & REDACTION (Pre-Call)
        pii_masked_in_request = 0
        original_user_text = ""
        for msg in messages:
            content = msg.get("content", "")
            if msg.get("role") == "user":
                original_user_text = content
            if content:
                mask_result = pii_masker.mask(content, placeholder_style="TAG")
                if mask_result.has_pii:
                    msg["content"] = mask_result.masked_text
                    pii_masked_in_request += len(mask_result.entities_detected)

        if pii_masked_in_request > 0:
            METRICS["pii_masked_count"] += pii_masked_in_request
            logger.info(f"PII Redacted: {pii_masked_in_request} sensitive items masked before LLM call.")

        # 4. CACHE LOOKUP: exact match on tenant + model + the whole masked request. A request that
        #    names no tenant (X-Organisation-Id) is neither served from nor stored in the cache.
        tenant = self.headers.get("X-Organisation-Id", "").strip()
        cache_key = generate_cache_key(tenant, model, payload) if tenant else None
        cached = RESPONSE_CACHE.get(cache_key) if cache_key else None
        if cached is not None:
            METRICS["cache_hits"] += 1
            duration_ms = int((time.time() - start_time) * 1000)
            logger.info(f"CACHE HIT [key={cache_key[:8]}] - Served in {duration_ms}ms")
            self._send_json(200, cached, headers={
                "X-Cache": "HIT",
                "X-Latency-Ms": duration_ms,
                "X-Guardrail-Pii-Masked": pii_masked_in_request
            })
            return

        if cache_key:
            METRICS["cache_misses"] += 1

        # 5. EXECUTION: Upstream Call. No matching upstream means 503, never a made-up reply.
        response_data: Optional[dict] = None
        glm_key = os.environ.get("GLM_API_KEY", "")
        glm_base_url = os.environ.get("GLM_BASE_URL", "https://api.z.ai/api/paas/v4")
        openai_key = os.environ.get("OPENAI_API_KEY", "")
        gemini_key = os.environ.get("GEMINI_API_KEY", "")

        try:
            if glm_key and ("glm" in model.lower() or model == "grc-default" or (not openai_key and not gemini_key)):
                logger.info(f"Forwarding to upstream Z.ai GLM (model={model}, baseUrl={glm_base_url})")
                response_data = call_upstream_glm(payload, glm_key, glm_base_url)
            elif "gemini" in model.lower() and gemini_key:
                logger.info(f"Forwarding to upstream Google Gemini (model={model})")
                response_data = call_upstream_gemini(payload, gemini_key)
            elif openai_key and ("gpt" in model.lower() or "openai" in model.lower()):
                logger.info(f"Forwarding to upstream OpenAI (model={model})")
                response_data = call_upstream_openai(payload, openai_key)
        except Exception as ex:
            METRICS["errors"] += 1
            # Details stay in the log: the exception text can carry upstream bodies or URLs.
            logger.error(f"Upstream inference failure: {ex}", exc_info=True)
            self._send_json(502, {
                "error": {
                    "message": "Gateway upstream call failed.",
                    "type": "gateway_upstream_error"
                }
            })
            return

        if response_data is None:
            METRICS["errors"] += 1
            logger.error(f"No upstream configured for model={model}; answering 503")
            self._send_json(503, {
                "error": {
                    "message": "No upstream model is configured for this request "
                               "(set GLM_API_KEY, OPENAI_API_KEY or GEMINI_API_KEY).",
                    "type": "no_upstream_configured",
                    "code": "NO_UPSTREAM"
                }
            }, headers={"X-Guardrail-Pii-Masked": pii_masked_in_request})
            return

        # 6. GUARDRAIL 3: POST-CALL HALLUCINATION & GROUNDING CHECK
        generated_text = ""
        if response_data.get("choices") and len(response_data["choices"]) > 0:
            generated_text = response_data["choices"][0].get("message", {}).get("content", "")

        hal_result = hallucination_detector.evaluate(generated_text, original_user_text)
        if hal_result.is_hallucination:
            METRICS["hallucinations_detected"] += 1
            logger.warning(f"Hallucination detected (score={hal_result.hallucination_score}) in response: {hal_result.warnings}")

        # 7. SAVE TO CACHE. A reply flagged as a likely hallucination goes back once, with its risk
        #    header, and is never replayed from the cache.
        key_label = cache_key[:8] if cache_key else "-"
        if cache_key and hal_result.is_hallucination:
            logger.warning(f"NOT CACHED [key={key_label}]: reply flagged as a likely hallucination")
        elif cache_key:
            RESPONSE_CACHE.put(cache_key, response_data)

        duration_ms = int((time.time() - start_time) * 1000)
        logger.info(f"PROCESSED [key={key_label}] in {duration_ms}ms (PII Masked: {pii_masked_in_request}, Hal-Score: {hal_result.hallucination_score})")

        self._send_json(200, response_data, headers={
            "X-Cache": "MISS" if cache_key else "BYPASS",
            "X-Latency-Ms": duration_ms,
            "X-Guardrail-Pii-Masked": pii_masked_in_request,
            "X-Guardrail-Hallucination-Risk": hal_result.hallucination_score
        })


def run_server():
    require_master_key()
    server = ThreadingHTTPServer((HOST, PORT), GatewayRequestHandler)
    prov = "Z.ai GLM (glm-4.7) [LIVE]" if os.environ.get("GLM_API_KEY") else ("OpenAI [LIVE]" if os.environ.get("OPENAI_API_KEY") else ("Gemini [LIVE]" if os.environ.get("GEMINI_API_KEY") else "none (requests get HTTP 503)"))
    print(f"============================================================")
    print(f"  TSS GRC AI Gateway Running on http://{HOST}:{PORT}")
    print(f"  CORS Origins    : {', '.join(sorted(CORS_ORIGINS)) or 'none'}")
    print(f"  Live Provider   : {prov}")
    print(f"  OpenAI Endpoint : http://127.0.0.1:{PORT}/v1/chat/completions")
    print(f"  Health Check    : http://127.0.0.1:{PORT}/health")
    print(f"  Metrics Telemetry: http://127.0.0.1:{PORT}/metrics")
    print(f"  Guardrails Active: PII Masking, Prompt Injection, Safety, Hallucination")
    print(f"============================================================")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down AI Gateway...")
        server.server_close()


if __name__ == "__main__":
    run_server()
