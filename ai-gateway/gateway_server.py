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
from collections import OrderedDict, deque
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
METRICS_LOCK = threading.Lock()
RECENT_REQUESTS: "deque[dict]" = deque(maxlen=50)
METRICS = {
    "total_requests": 0,
    "cache_hits": 0,
    "cache_misses": 0,
    "pii_masked_count": 0,
    "injections_blocked": 0,
    "safety_violations_blocked": 0,
    "hallucinations_detected": 0,
    "errors": 0,
    "total_latency_ms": 0,
    "total_prompt_tokens": 0,
    "total_completion_tokens": 0,
    "uptime_start": time.time()
}


def record_request_event(
    *,
    tenant: str,
    feature: str,
    model: str,
    status: int,
    cache: str,
    latency_ms: int,
    prompt_tokens: int = 0,
    completion_tokens: int = 0,
    pii_masked: int = 0,
    hallucination_score: float = 0.0,
    finish_reason: str = "",
    note: str = ""
):
    with METRICS_LOCK:
        METRICS["total_latency_ms"] += max(0, latency_ms)
        METRICS["total_prompt_tokens"] += max(0, prompt_tokens)
        METRICS["total_completion_tokens"] += max(0, completion_tokens)
        RECENT_REQUESTS.appendleft({
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "tenant": tenant or "-",
            "feature": feature or "-",
            "model": model or "-",
            "status": status,
            "cache": cache,
            "latency_ms": latency_ms,
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "pii_masked": pii_masked,
            "hallucination_score": hallucination_score,
            "finish_reason": finish_reason,
            "note": note
        })


def build_prometheus_metrics() -> str:
    uptime = round(time.time() - METRICS["uptime_start"], 2)
    denom = METRICS["cache_hits"] + METRICS["cache_misses"]
    hit_ratio = round(METRICS["cache_hits"] / denom, 4) if denom > 0 else 0.0
    lines = [
        "# HELP ai_gateway_up Whether the TSS GRC AI Gateway is up (1 = healthy).",
        "# TYPE ai_gateway_up gauge",
        "ai_gateway_up 1",
        "# HELP ai_gateway_uptime_seconds Gateway process uptime in seconds.",
        "# TYPE ai_gateway_uptime_seconds gauge",
        f"ai_gateway_uptime_seconds {uptime}",
        "# HELP ai_gateway_requests_total Total chat completion requests received.",
        "# TYPE ai_gateway_requests_total counter",
        f"ai_gateway_requests_total {METRICS['total_requests']}",
        "# HELP ai_gateway_cache_hits_total Total reply cache hits.",
        "# TYPE ai_gateway_cache_hits_total counter",
        f"ai_gateway_cache_hits_total {METRICS['cache_hits']}",
        "# HELP ai_gateway_cache_misses_total Total reply cache misses.",
        "# TYPE ai_gateway_cache_misses_total counter",
        f"ai_gateway_cache_misses_total {METRICS['cache_misses']}",
        "# HELP ai_gateway_cache_hit_ratio Ratio of cache hits over total cacheable lookups (0.0 to 1.0).",
        "# TYPE ai_gateway_cache_hit_ratio gauge",
        f"ai_gateway_cache_hit_ratio {hit_ratio}",
        "# HELP ai_gateway_cache_entries Current number of active entries in the LRU reply cache.",
        "# TYPE ai_gateway_cache_entries gauge",
        f"ai_gateway_cache_entries {len(RESPONSE_CACHE)}",
        "# HELP ai_gateway_cache_max_entries Configured maximum entries in the LRU reply cache.",
        "# TYPE ai_gateway_cache_max_entries gauge",
        f"ai_gateway_cache_max_entries {RESPONSE_CACHE.max_entries}",
        "# HELP ai_gateway_pii_masked_total Total Saudi PDPL PII entities masked before upstream calls.",
        "# TYPE ai_gateway_pii_masked_total counter",
        f"ai_gateway_pii_masked_total {METRICS['pii_masked_count']}",
        "# HELP ai_gateway_prompt_injections_blocked_total Total prompt injection / jailbreak attempts blocked.",
        "# TYPE ai_gateway_prompt_injections_blocked_total counter",
        f"ai_gateway_prompt_injections_blocked_total {METRICS['injections_blocked']}",
        "# HELP ai_gateway_safety_violations_blocked_total Total safety & credential probe violations blocked.",
        "# TYPE ai_gateway_safety_violations_blocked_total counter",
        f"ai_gateway_safety_violations_blocked_total {METRICS.get('safety_violations_blocked', 0)}",
        "# HELP ai_gateway_hallucinations_detected_total Total responses flagged by post-call hallucination bounds checker.",
        "# TYPE ai_gateway_hallucinations_detected_total counter",
        f"ai_gateway_hallucinations_detected_total {METRICS['hallucinations_detected']}",
        "# HELP ai_gateway_errors_total Total gateway or upstream errors.",
        "# TYPE ai_gateway_errors_total counter",
        f"ai_gateway_errors_total {METRICS['errors']}",
        "# HELP ai_gateway_latency_milliseconds_sum Cumulative request processing latency in milliseconds.",
        "# TYPE ai_gateway_latency_milliseconds_sum counter",
        f"ai_gateway_latency_milliseconds_sum {METRICS.get('total_latency_ms', 0)}",
        "# HELP ai_gateway_tokens_prompt_total Cumulative prompt tokens reported by upstream responses.",
        "# TYPE ai_gateway_tokens_prompt_total counter",
        f"ai_gateway_tokens_prompt_total {METRICS.get('total_prompt_tokens', 0)}",
        "# HELP ai_gateway_tokens_completion_total Cumulative completion tokens reported by upstream responses.",
        "# TYPE ai_gateway_tokens_completion_total counter",
        f"ai_gateway_tokens_completion_total {METRICS.get('total_completion_tokens', 0)}",
        ""
    ]
    return "\n".join(lines)


DASHBOARD_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<title>TSS GRC — AI Gateway & Guardrails Telemetry</title>
<style>
  :root {
    --bg: #0b0f17;
    --panel: #111827;
    --panel-2: #1e293b;
    --border: #263248;
    --ink: #f1f5f9;
    --muted: #94a3b8;
    --green: #10b981;
    --blue: #3b82f6;
    --amber: #f59e0b;
    --red: #ef4444;
    --purple: #8b5cf6;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: var(--bg); color: var(--ink); padding: 20px 28px;
  }
  .top {
    display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap;
    gap: 12px; padding-bottom: 16px; border-bottom: 1px solid var(--border); margin-bottom: 20px;
  }
  .title { display: flex; align-items: center; gap: 12px; }
  .dot { width: 11px; height: 11px; border-radius: 50%; background: var(--green); box-shadow: 0 0 10px var(--green); }
  .dot.down { background: var(--red); box-shadow: 0 0 10px var(--red); }
  h1 { font-size: 19px; margin: 0; font-weight: 700; letter-spacing: -0.01em; }
  .sub { font-size: 12px; color: var(--muted); }
  .controls { display: flex; gap: 8px; align-items: center; }
  input, button {
    background: var(--panel); color: var(--ink); border: 1px solid var(--border);
    padding: 7px 12px; border-radius: 6px; font-size: 12px;
  }
  button { cursor: pointer; font-weight: 600; }
  button:hover { background: var(--panel-2); }
  .grid {
    display: grid; grid-template-columns: repeat(auto-fit, minmax(195px, 1fr));
    gap: 14px; margin-bottom: 20px;
  }
  .card {
    background: var(--panel); border: 1px solid var(--border); border-radius: 10px;
    padding: 15px 16px;
  }
  .card .k { font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
  .card .v { font-size: 26px; font-weight: 700; margin: 6px 0 4px; font-variant-numeric: tabular-nums; }
  .card .m { font-size: 11px; color: var(--muted); }
  .row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px; }
  @media (max-width: 900px) { .row-2 { grid-template-columns: 1fr; } }
  .bar-row { margin: 10px 0; }
  .bar-head { display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 4px; }
  .bar-track { height: 8px; background: var(--panel-2); border-radius: 99px; overflow: hidden; }
  .bar-fill { height: 100%; border-radius: 99px; transition: width 0.3s ease; }
  table { width: 100%; border-collapse: collapse; font-size: 12px; }
  th, td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--border); font-variant-numeric: tabular-nums; }
  th { color: var(--muted); font-size: 11px; text-transform: uppercase; font-weight: 600; }
  .pill {
    display: inline-block; padding: 2px 8px; border-radius: 99px; font-size: 10px; font-weight: 700;
  }
  .pill.ok { background: rgba(16,185,129,0.16); color: #34d399; }
  .pill.hit { background: rgba(59,130,246,0.18); color: #60a5fa; }
  .pill.warn { background: rgba(245,158,11,0.18); color: #fbbf24; }
  .pill.err { background: rgba(239,68,68,0.18); color: #f87171; }
  .err-banner {
    background: rgba(239,68,68,0.14); border: 1px solid var(--red); color: #fecaca;
    padding: 10px 14px; border-radius: 8px; margin-bottom: 16px; font-size: 12px; display: none;
  }
</style>
</head>
<body>
  <div class="top">
    <div class="title">
      <div id="statusDot" class="dot"></div>
      <div>
        <h1>TSS GRC — AI Gateway & Saudi Guardrails Live Monitor</h1>
        <div class="sub" id="subtitle">127.0.0.1:4000 · OpenAI-Compatible Proxy · Prometheus /metrics/prometheus ready</div>
      </div>
    </div>
    <div class="controls">
      <input type="password" id="apiKeyInput" placeholder="Gateway Master Key (Bearer)" style="width:230px" />
      <button onclick="saveKeyAndRefresh()">Connect / Refresh</button>
      <button id="autoBtn" onclick="toggleAuto()">Auto: ON (3s)</button>
    </div>
  </div>

  <div id="errBanner" class="err-banner"></div>

  <div class="grid">
    <div class="card">
      <div class="k">Gateway Status</div>
      <div class="v" id="vStatus" style="color:var(--green)">HEALTHY</div>
      <div class="m" id="vUptime">Uptime: --</div>
    </div>
    <div class="card">
      <div class="k">Total Requests</div>
      <div class="v" id="vTotalReq">0</div>
      <div class="m" id="vAvgLat">Avg Latency: 0 ms</div>
    </div>
    <div class="card">
      <div class="k">Reply Cache Hit Rate</div>
      <div class="v" id="vCacheRate" style="color:var(--blue)">0%</div>
      <div class="m" id="vCacheSub">0 hits / 0 misses · 0/1000 entries</div>
    </div>
    <div class="card">
      <div class="k">Token Throughput</div>
      <div class="v" id="vTokens" style="color:var(--purple)">0</div>
      <div class="m" id="vTokensSub">0 prompt · 0 completion</div>
    </div>
    <div class="card">
      <div class="k">PDPL PII Masked</div>
      <div class="v" id="vPii" style="color:var(--amber)">0</div>
      <div class="m">National ID, Iqama, IBAN, Phone</div>
    </div>
    <div class="card">
      <div class="k">Threats & Hallucinations</div>
      <div class="v" id="vBlocked" style="color:var(--red)">0</div>
      <div class="m" id="vBlockedSub">0 inj · 0 safety · 0 hal · 0 err</div>
    </div>
  </div>

  <div class="row-2">
    <div class="card">
      <div class="k" style="margin-bottom:10px">Saudi Regulatory Guardrails Breakdown</div>
      <div class="bar-row">
        <div class="bar-head"><span>Saudi PDPL PII Items Redacted</span><strong id="bPii">0</strong></div>
        <div class="bar-track"><div id="fPii" class="bar-fill" style="width:0%;background:var(--amber)"></div></div>
      </div>
      <div class="bar-row">
        <div class="bar-head"><span>Prompt Injection / DAN Jailbreaks Blocked</span><strong id="bInj">0</strong></div>
        <div class="bar-track"><div id="fInj" class="bar-fill" style="width:0%;background:var(--red)"></div></div>
      </div>
      <div class="bar-row">
        <div class="bar-head"><span>Safety & Credential Probes Blocked</span><strong id="bSaf">0</strong></div>
        <div class="bar-track"><div id="fSaf" class="bar-fill" style="width:0%;background:var(--red)"></div></div>
      </div>
      <div class="bar-row">
        <div class="bar-head"><span>Post-Call Hallucination Risk Flagged (PDPL/ECC/SAMA)</span><strong id="bHal">0</strong></div>
        <div class="bar-track"><div id="fHal" class="bar-fill" style="width:0%;background:var(--purple)"></div></div>
      </div>
    </div>

    <div class="card">
      <div class="k" style="margin-bottom:10px">Cache & Endpoint Configuration</div>
      <table>
        <tbody>
          <tr><td>Active LRU Cache Entries</td><td id="cEntries">0 / 1000</td></tr>
          <tr><td>Cache Entry TTL</td><td id="cTtl">3600s</td></tr>
          <tr><td>Prometheus Scrape Endpoint</td><td><code>GET /metrics/prometheus</code></td></tr>
          <tr><td>JSON Telemetry Endpoint</td><td><code>GET /metrics</code></td></tr>
          <tr><td>Health Probe</td><td><code>GET /health</code></td></tr>
        </tbody>
      </table>
    </div>
  </div>

  <div class="card">
    <div class="k" style="margin-bottom:12px">Recent Gateway Requests (Live Ring Buffer)</div>
    <div style="overflow-x:auto">
      <table>
        <thead>
          <tr>
            <th>Time (UTC)</th>
            <th>Tenant</th>
            <th>Feature</th>
            <th>Model</th>
            <th>HTTP</th>
            <th>Cache</th>
            <th>Tokens (In/Out)</th>
            <th>PII Masked</th>
            <th>Hal Score</th>
            <th>Latency</th>
            <th>Finish / Note</th>
          </tr>
        </thead>
        <tbody id="reqTable">
          <tr><td colspan="11" style="color:var(--muted);text-align:center;padding:18px">Loading live gateway telemetry…</td></tr>
        </tbody>
      </table>
    </div>
  </div>

<script>
  const defaultServerKey = __DEFAULT_GATEWAY_KEY__;
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.get('key')) {
    sessionStorage.setItem('gw_key', urlParams.get('key'));
    window.history.replaceState({}, document.title, window.location.pathname);
  }
  const input = document.getElementById('apiKeyInput');
  input.value = sessionStorage.getItem('gw_key') || defaultServerKey || '';
  if (input.value && !sessionStorage.getItem('gw_key')) {
    sessionStorage.setItem('gw_key', input.value);
  }
  let autoTimer = null;
  let autoOn = true;

  function saveKeyAndRefresh() {
    sessionStorage.setItem('gw_key', input.value.trim());
    refresh();
  }
  function toggleAuto() {
    autoOn = !autoOn;
    document.getElementById('autoBtn').textContent = autoOn ? 'Auto: ON (3s)' : 'Auto: OFF';
  }
  async function refresh() {
    const errEl = document.getElementById('errBanner');
    try {
      const hRes = await fetch('/health');
      const h = await hRes.json();
      document.getElementById('vStatus').textContent = (h.status || 'healthy').toUpperCase();
      document.getElementById('vUptime').textContent = 'Uptime: ' + h.uptime_seconds + 's';
      document.getElementById('statusDot').className = 'dot';
    } catch (e) {
      document.getElementById('vStatus').textContent = 'DOWN';
      document.getElementById('statusDot').className = 'dot down';
    }
    let key = (sessionStorage.getItem('gw_key') || input.value || defaultServerKey || '').trim();
    if (!key) {
      errEl.style.display = 'block';
      errEl.textContent = 'Enter your GATEWAY_MASTER_KEY above (from ai-gateway/.env) to load authenticated /metrics telemetry.';
      return;
    }
    try {
      let mRes = await fetch('/metrics', { headers: { 'Authorization': 'Bearer ' + key } });
      if (mRes.status === 401 && defaultServerKey && key !== defaultServerKey) {
        key = defaultServerKey;
        input.value = defaultServerKey;
        sessionStorage.setItem('gw_key', defaultServerKey);
        mRes = await fetch('/metrics', { headers: { 'Authorization': 'Bearer ' + key } });
      }
      if (mRes.status === 401) {
        errEl.style.display = 'block';
        errEl.textContent = 'HTTP 401 Unauthorized: Invalid Gateway Master Key.';
        return;
      }
      errEl.style.display = 'none';
      const m = await mRes.json();
      document.getElementById('vTotalReq').textContent = m.total_requests ?? 0;
      document.getElementById('vAvgLat').textContent = 'Avg Latency: ' + (m.avg_latency_ms ?? 0) + ' ms';
      document.getElementById('vCacheRate').textContent = (m.cache_hit_rate_pct ?? 0) + '%';
      document.getElementById('vCacheSub').textContent =
        (m.cache_hits ?? 0) + ' hits / ' + (m.cache_misses ?? 0) + ' misses · ' +
        (m.active_cache_entries ?? 0) + '/' + (m.cache_max_entries ?? 1000) + ' entries';
      const pTok = m.total_prompt_tokens ?? 0;
      const cTok = m.total_completion_tokens ?? 0;
      document.getElementById('vTokens').textContent = (pTok + cTok).toLocaleString();
      document.getElementById('vTokensSub').textContent = pTok.toLocaleString() + ' prompt · ' + cTok.toLocaleString() + ' completion';
      document.getElementById('vPii').textContent = m.pii_masked_count ?? 0;
      const inj = m.injections_blocked ?? 0;
      const saf = m.safety_violations_blocked ?? 0;
      const hal = m.hallucinations_detected ?? 0;
      const err = m.errors ?? 0;
      document.getElementById('vBlocked').textContent = inj + saf + hal;
      document.getElementById('vBlockedSub').textContent = inj + ' inj · ' + saf + ' safety · ' + hal + ' hal · ' + err + ' err';

      document.getElementById('bPii').textContent = m.pii_masked_count ?? 0;
      document.getElementById('bInj').textContent = inj;
      document.getElementById('bSaf').textContent = saf;
      document.getElementById('bHal').textContent = hal;
      const maxG = Math.max(1, m.pii_masked_count || 0, inj, saf, hal);
      document.getElementById('fPii').style.width = Math.min(100, ((m.pii_masked_count || 0) / maxG) * 100) + '%';
      document.getElementById('fInj').style.width = Math.min(100, (inj / maxG) * 100) + '%';
      document.getElementById('fSaf').style.width = Math.min(100, (saf / maxG) * 100) + '%';
      document.getElementById('fHal').style.width = Math.min(100, (hal / maxG) * 100) + '%';

      document.getElementById('cEntries').textContent = (m.active_cache_entries ?? 0) + ' / ' + (m.cache_max_entries ?? 1000);
      document.getElementById('cTtl').textContent = (m.cache_ttl_seconds ?? 3600) + 's';

      const rows = m.recent_requests || [];
      const tbody = document.getElementById('reqTable');
      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="11" style="color:var(--muted);text-align:center;padding:18px">No requests recorded since gateway startup.</td></tr>';
      } else {
        tbody.innerHTML = rows.map(r => {
          const stClass = r.status === 200 ? 'ok' : (r.status === 400 ? 'warn' : 'err');
          const caClass = r.cache === 'HIT' ? 'hit' : 'ok';
          return `<tr>
            <td>${r.timestamp}</td>
            <td>${r.tenant}</td>
            <td>${r.feature}</td>
            <td>${r.model}</td>
            <td><span class="pill ${stClass}">${r.status}</span></td>
            <td><span class="pill ${caClass}">${r.cache}</span></td>
            <td>${r.prompt_tokens} / ${r.completion_tokens}</td>
            <td>${r.pii_masked}</td>
            <td>${r.hallucination_score}</td>
            <td>${r.latency_ms} ms</td>
            <td>${r.finish_reason || r.note || '-'}</td>
          </tr>`;
        }).join('');
      }
    } catch (e) {
      errEl.style.display = 'block';
      errEl.textContent = 'Failed to fetch /metrics: ' + e.message;
    }
  }
  refresh();
  setInterval(() => { if (autoOn) refresh(); }, 3000);
</script>
</body>
</html>
"""

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
    with urllib.request.urlopen(req, timeout=180) as resp:
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

    def _send_text(self, status: int, text: str, content_type: str = "text/plain; charset=utf-8"):
        body = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        for k, v in self._cors_headers().items():
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
        clean_path = self.path.split("?", 1)[0]
        if clean_path in ("/health", "/healthz"):
            self._send_json(200, {
                "status": "healthy",
                "service": "TSS GRC AI Gateway",
                "cache_size": len(RESPONSE_CACHE),
                "uptime_seconds": round(time.time() - METRICS["uptime_start"], 1)
            })
        elif clean_path in ("/dashboard", "/v1/dashboard"):
            rendered = DASHBOARD_HTML.replace("__DEFAULT_GATEWAY_KEY__", json.dumps(MASTER_KEY or ""))
            self._send_text(200, rendered, "text/html; charset=utf-8")
        elif not self._authorized():
            self._reject_unauthorized()
        elif clean_path in ("/metrics", "/v1/metrics"):
            hit_rate = 0.0
            if (METRICS["cache_hits"] + METRICS["cache_misses"]) > 0:
                hit_rate = round(METRICS["cache_hits"] / (METRICS["cache_hits"] + METRICS["cache_misses"]) * 100, 2)
            total_req = max(1, METRICS["total_requests"])
            avg_lat = round(METRICS.get("total_latency_ms", 0) / total_req, 1) if METRICS["total_requests"] > 0 else 0.0
            with METRICS_LOCK:
                recent_snapshot = list(RECENT_REQUESTS)
            self._send_json(200, {
                **METRICS,
                "avg_latency_ms": avg_lat,
                "cache_hit_rate_pct": hit_rate,
                "active_cache_entries": len(RESPONSE_CACHE),
                "cache_max_entries": RESPONSE_CACHE.max_entries,
                "cache_ttl_seconds": RESPONSE_CACHE.ttl_seconds,
                "guardrails": {
                    "pii_masking_enabled": True,
                    "prompt_injection_defense_enabled": True,
                    "hallucination_detection_enabled": True
                },
                "recent_requests": recent_snapshot
            })
        elif clean_path in ("/metrics/prometheus", "/v1/metrics/prometheus"):
            self._send_text(200, build_prometheus_metrics(), "text/plain; version=0.0.4; charset=utf-8")
        elif clean_path in ("/v1/models", "/models"):
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
        tenant_hdr = self.headers.get("X-Organisation-Id", "").strip()
        feature_hdr = self.headers.get("X-Feature-Code", "").strip()

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
                    record_request_event(
                        tenant=tenant_hdr, feature=feature_hdr, model=model, status=400, cache="BYPASS",
                        latency_ms=int((time.time() - start_time) * 1000), note=safety_res.violation_category
                    )
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
                    record_request_event(
                        tenant=tenant_hdr, feature=feature_hdr, model=model, status=400, cache="BYPASS",
                        latency_ms=int((time.time() - start_time) * 1000), note="PROMPT_INJECTION_DETECTED"
                    )
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
        tenant = tenant_hdr
        cache_key = generate_cache_key(tenant, model, payload) if tenant else None
        cached = RESPONSE_CACHE.get(cache_key) if cache_key else None
        if cached is not None:
            METRICS["cache_hits"] += 1
            duration_ms = int((time.time() - start_time) * 1000)
            logger.info(f"CACHE HIT [key={cache_key[:8]}] - Served in {duration_ms}ms")
            c_usage = cached.get("usage", {}) if isinstance(cached, dict) else {}
            record_request_event(
                tenant=tenant, feature=feature_hdr, model=model, status=200, cache="HIT",
                latency_ms=duration_ms,
                prompt_tokens=int(c_usage.get("prompt_tokens", 0) or 0),
                completion_tokens=int(c_usage.get("completion_tokens", 0) or 0),
                pii_masked=pii_masked_in_request, finish_reason="cache_hit"
            )
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
            record_request_event(
                tenant=tenant, feature=feature_hdr, model=model, status=502, cache="MISS" if cache_key else "BYPASS",
                latency_ms=int((time.time() - start_time) * 1000), pii_masked=pii_masked_in_request,
                note="gateway_upstream_error"
            )
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
            record_request_event(
                tenant=tenant, feature=feature_hdr, model=model, status=503, cache="BYPASS",
                latency_ms=int((time.time() - start_time) * 1000), pii_masked=pii_masked_in_request,
                note="NO_UPSTREAM"
            )
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
        finish_reason = "stop"
        if response_data.get("choices") and len(response_data["choices"]) > 0:
            first_choice = response_data["choices"][0]
            generated_text = first_choice.get("message", {}).get("content", "")
            finish_reason = str(first_choice.get("finish_reason", "stop") or "stop")

        hal_result = hallucination_detector.evaluate(generated_text, original_user_text)
        if hal_result.is_hallucination:
            METRICS["hallucinations_detected"] += 1
            logger.warning(f"Hallucination detected (score={hal_result.hallucination_score}) in response: {hal_result.warnings}")

        # 7. SAVE TO CACHE. A reply flagged as a likely hallucination or truncated by length goes back
        #    once, with its risk header, and is never replayed from the cache.
        key_label = cache_key[:8] if cache_key else "-"
        if cache_key and hal_result.is_hallucination:
            logger.warning(f"NOT CACHED [key={key_label}]: reply flagged as a likely hallucination")
        elif cache_key and finish_reason == "length":
            logger.warning(f"NOT CACHED [key={key_label}]: reply was truncated (finish_reason=length)")
        elif cache_key:
            RESPONSE_CACHE.put(cache_key, response_data)

        duration_ms = int((time.time() - start_time) * 1000)
        usage = response_data.get("usage", {}) if isinstance(response_data, dict) else {}
        in_tok = int(usage.get("prompt_tokens", 0) or 0)
        out_tok = int(usage.get("completion_tokens", 0) or 0)
        record_request_event(
            tenant=tenant, feature=feature_hdr, model=model, status=200,
            cache="MISS" if cache_key else "BYPASS", latency_ms=duration_ms,
            prompt_tokens=in_tok, completion_tokens=out_tok,
            pii_masked=pii_masked_in_request, hallucination_score=hal_result.hallucination_score,
            finish_reason=finish_reason
        )
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
    print(f"  Live Dashboard  : http://127.0.0.1:{PORT}/dashboard")
    print(f"  Health Check    : http://127.0.0.1:{PORT}/health")
    print(f"  Metrics Telemetry: http://127.0.0.1:{PORT}/metrics")
    print(f"  Prometheus Exp  : http://127.0.0.1:{PORT}/metrics/prometheus")
    print(f"  Guardrails Active: PII Masking, Prompt Injection, Safety, Hallucination")
    print(f"============================================================")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down AI Gateway...")
        server.server_close()


if __name__ == "__main__":
    run_server()
