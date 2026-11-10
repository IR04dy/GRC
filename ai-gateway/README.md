# TSS GRC AI Gateway & Guardrails Subsystem

An OpenAI-compatible gateway for the **TSS GRC Platform**. It checks each request with the guardrails below,
masks PII, sends the request to one upstream model (Z.ai GLM, OpenAI or Google Gemini) and checks the reply for
fabricated GRC citations. The guardrails target Saudi regulatory mandates (**PDPL**, **NCA ECC/CSCC**, **SAMA CSF**).

It runs in one of two ways:

- **`gateway_server.py`**: Python standard library only. This is the server the backend and the tests in this
  folder are written against.
- **LiteLLM proxy**: `config.yaml` + `gateway_hook.py`, run with the `litellm` CLI or `docker-compose.yml`.

Where the two differ, this guide says so.

---

## Request Flow (`gateway_server.py`)

```
tss-grc-backend (AiGatewayService -> AiGatewayClient)        standalone_test.py
                  |                                                  |
                  +------------------------+-------------------------+
                                           |  POST /v1/chat/completions
                                           |  Authorization: Bearer <GATEWAY_MASTER_KEY>
                                           |  X-Organisation-Id: <tenant>
                                           v
TSS GRC AI Gateway (127.0.0.1:4000)
  0. Master key checked on every endpoint except GET /health (HTTP 401 otherwise)
  1. Pre-call: safety filter and prompt-injection filter (HTTP 400), then PII masking
  2. Reply cache: exact match per tenant; a HIT is returned here
  3. Upstream call to ONE provider: Z.ai GLM, OpenAI or Google Gemini
       no provider for the request: HTTP 503 NO_UPSTREAM; upstream error: HTTP 502
  4. Post-call: hallucination and citation check (a flagged reply is returned, never cached)
  5. Telemetry: GET /health (open), GET /metrics and GET /v1/models (key required)
```

---

## Routing and Fallback

`gateway_server.py` sends each request to exactly one upstream, picked from the model name and the keys that are
set:

| Model in the request | Goes to | Needs |
|---|---|---|
| `grc-default`, or a name containing `glm` | Z.ai GLM (`glm-4.7`) | `GLM_API_KEY` |
| any other name, when `GLM_API_KEY` is the only upstream key set | Z.ai GLM | `GLM_API_KEY` |
| a name containing `gemini` | Google Gemini | `GEMINI_API_KEY` |
| a name containing `gpt` or `openai` | OpenAI | `OPENAI_API_KEY` |

- **There is no fallback.** If the chosen upstream fails, the gateway answers HTTP 502. If no row matches, it
  answers HTTP 503 `NO_UPSTREAM`. It never makes up a reply. `grc-default` with only an OpenAI or Gemini key
  set gets 503.
- **There is no local-model route.** `gateway_server.py` cannot call Ollama, vLLM or any other local server.
- **LiteLLM differs:** `config.yaml` routes `grc-default` to GLM and falls back to `grc-gemini`, then
  `grc-openai`. It also declares `grc-local` (Ollama), but that model is not in the fallback list. Under Docker,
  its `api_base` of `localhost:11434` points at the container itself, not the host.

---

## Reply Cache (`gateway_server.py`)

- **Key:** exact match on the tenant (`X-Organisation-Id`), the model and the whole request body after PII
  masking. A different temperature, `max_tokens`, `response_format` or letter case is a different entry.
- **No tenant:** a request without `X-Organisation-Id` is neither served from the cache nor stored in it, and
  gets `X-Cache: BYPASS`.
- **Bounds:** at most `GATEWAY_CACHE_MAX_ENTRIES` replies (default 1000; the least recently used goes first).
  Each reply expires `GATEWAY_CACHE_TTL` seconds after it was stored (default 3600; a hit does not extend it).
  Setting either to 0 turns the cache off.
- **Flagged replies:** a reply the hallucination check flags is returned with its
  `X-Guardrail-Hallucination-Risk` header and never cached.
- **Storage:** the cache lives in process memory. It is lost on restart and not shared between gateway
  instances. Redis is not used.
- **Cost:** a HIT skips the upstream call, so it spends no upstream tokens, and it returns as fast as the gateway
  itself (14 ms in the hardening smoke run, against 28 ms for the miss, which went to a local stub rather than a
  real model). A HIT replays the original reply's `usage` block, though, and the backend counts those tokens
  against the tenant's daily quota.
- **LiteLLM:** the reply cache is off in `config.yaml`. It cannot key on the tenant header or keep a flagged
  reply out; the file explains why.

---

## Guardrails Details

### 1. PII Masking & Redaction (Saudi PDPL Compliance)
* **Saudi National ID**: 10 digits starting with `1` &rarr; `<SAUDI_NATIONAL_ID>`
* **Saudi Iqama (Resident ID)**: 10 digits starting with `2` &rarr; `<SAUDI_IQAMA>`
* **Saudi Mobile Numbers**: `+9665...`, `009665...`, `05...` &rarr; `<SAUDI_PHONE>`
* **Saudi IBAN**: 24-character standard starting with `SA` &rarr; `<SAUDI_IBAN>`
* **Email & IP Addresses**: RFC 5322 emails & IPv4 &rarr; `<EMAIL_ADDRESS>`, `<IP_ADDRESS>`
* **Credit Cards**: Luhn-validated credit card numbers &rarr; `<CREDIT_CARD>`
* *Integration*: Uses built-in regex engine with zero external dependencies; bridges seamlessly to **Microsoft Presidio** if installed.

### 2. Prompt Injection & Jailbreak Defense
* **Instruction Overrides**: Intercepts phrases like `"ignore previous instructions"`, `"disregard initial directives"`.
* **Persona Hijacking**: Detects DAN (Do Anything Now), Developer Mode bypasses, and ethical constraint stripping.
* **System Prompt Leaking**: Detects attempts to print or exfiltrate hidden system prompts.
* **Delimiter & ChatML Spoofing**: Blocks `<|im_start|>`, `[INST]`, `<<SYS>>`, and markdown fence hijacking.
* *Enforcement*: Rejects hostile prompts with **HTTP 400 Bad Request** (`PROMPT_INJECTION_DETECTED`).

### 3. Grounding & Hallucination Detection
* **GRC Citation Validation**: Validates regulatory identifiers against known bounds:
  - NCA ECC (Essential Cybersecurity Controls: maximum 5 subdomains)
  - Saudi PDPL (Personal Data Protection Law: strictly 43 articles)
  - NCA CSCC / CCC / SAMA CSF / ISO 27001
* **Context Overlap Score**: Measures lexical/entity overlap against reference context.
* **Fabricated Metrics**: Flags unreferenced statistics, percentages, and monetary claims absent in the grounding text.
* *Scope*: `gateway_server.py` grounds the reply against the last user message only, not the system prompt.

---

## Configuration

`gateway_server.py` reads these environment variables. It also reads `ai-gateway/.env` (gitignored) at startup,
which never overrides a variable that is already set.

| Variable | Default | Meaning |
|---|---|---|
| `GATEWAY_MASTER_KEY` | none, required | The Bearer key callers must send. Use the same value as the backend's `AI_GATEWAY_API_KEY`. The server will not start without it. |
| `GATEWAY_HOST` | `127.0.0.1` | Bind address. |
| `GATEWAY_PORT` | `4000` | Port. |
| `GATEWAY_CORS_ORIGINS` | none | Comma-separated browser origins that get CORS headers. Others get none. |
| `GATEWAY_CACHE_TTL` | `3600` | Seconds a cached reply lives; 0 turns the cache off. |
| `GATEWAY_CACHE_MAX_ENTRIES` | `1000` | Most cached replies; 0 turns the cache off. |
| `GLM_API_KEY`, `GLM_BASE_URL` | none, `https://api.z.ai/api/paas/v4` | Z.ai GLM upstream. |
| `OPENAI_API_KEY` | none | OpenAI upstream. |
| `GEMINI_API_KEY` | none | Google Gemini upstream (sent in the `x-goog-api-key` header). |

---

## Quick Start

### Option A: `gateway_server.py`
Python 3.10 or later, no packages to install:
```powershell
# In PowerShell:
$env:GATEWAY_MASTER_KEY = "<the backend's AI_GATEWAY_API_KEY value>"
$env:GLM_API_KEY = "<your Z.ai key>"
cd ai-gateway
python gateway_server.py
```
`start_gateway.ps1` and `start_gateway.bat` do the same and refuse to start without `GATEWAY_MASTER_KEY`. They
run `py -3.10`, so that Python version must be installed.

* **OpenAI API**: `http://127.0.0.1:4000/v1/chat/completions`
* **Health Check**: `http://127.0.0.1:4000/health` (no key)
* **Live Metrics**: `http://127.0.0.1:4000/metrics` (send the key)

### Option B: LiteLLM Proxy via CLI
```bash
pip install "litellm[proxy]"
litellm --config ai-gateway/config.yaml --port 4000
```
`GATEWAY_MASTER_KEY` and `GLM_API_KEY` must be set. `gateway_hook.py` runs the same guardrails, but it sets none of
the `X-Cache` or `X-Guardrail-*` response headers, so the backend logs them as empty. The reply cache is off.

### Option C: Docker Compose (LiteLLM + Redis + Presidio)
```bash
cd ai-gateway
docker compose up -d
```
Compose refuses to start without `GATEWAY_MASTER_KEY` and publishes every port on 127.0.0.1 only.
`docker-compose.yml` does not pass `GLM_API_KEY` to the LiteLLM container yet, so `grc-default` cannot reach GLM
there. `config.yaml` does not use the Redis container.

---

## Testing

### Unit tests
Standard library only, run from the repository root:
```bash
python -m unittest discover -s ai-gateway/tests -v
```
17 tests: the master key, 503 and 502 replies, the Gemini key header, the CORS allow-list, startup defaults and the
reply cache.

### Smoke test against a running gateway
```bash
# GATEWAY_URL defaults to http://127.0.0.1:4000
GATEWAY_API_KEY="<master key>" python ai-gateway/standalone_test.py
```
Seven checks:
1. **Health & Metrics Probe**: checks `/health` and `/metrics`.
2. **Basic Chat Completion**: confirms an OpenAI-shaped reply.
3. **Cache**: expects `X-Cache: HIT` on a repeated request.
4. **Saudi PDPL PII Guardrail**: expects National ID, mobile, email and IBAN to be masked.
5. **Prompt Injection Defense**: expects HTTP 400 on override and DAN attacks.
6. **Safety & Threat Guardrail**: expects HTTP 400 on credential probes and prohibited threats.
7. **Hallucination Detection**: runs the detector in process, not through the gateway, on fabricated controls
   (`NCA ECC-99-8-1`, `PDPL Article 199`).

Checks 2 and 3 report SKIPPED when the gateway has no upstream. Requests carry `X-Organisation-Id: standalone-test`
(override with `GATEWAY_TEST_TENANT`), since requests without a tenant are not cached.

### Interactive Sandbox Mode
```bash
GATEWAY_API_KEY="<master key>" python ai-gateway/standalone_test.py --chat
```

---

## Connecting the TSS GRC Backend

The backend calls the gateway through `AiGatewayService` and `AiGatewayClient`. They are configured under
`grc.integrations.ai-gateway` in `application.yml`:

| Variable | Default | Meaning |
|---|---|---|
| `AI_GATEWAY_ENABLED` | `false` | Off by default, and off in the prod profile. |
| `AI_GATEWAY_MODE` | `LIVE` | `LIVE` calls the gateway. `STUB` calls nothing and returns a reply marked as a stub. |
| `AI_GATEWAY_BASE_URL` | `http://localhost:4000/v1` | Gateway URL. |
| `AI_GATEWAY_MODEL` | `grc-default` | Model name sent to the gateway. |
| `AI_GATEWAY_API_KEY` | none | Must equal `GATEWAY_MASTER_KEY`. Enabled + `LIVE` without it stops backend startup. |
| `AI_GATEWAY_TIMEOUT_SECONDS` | `60` | Timeout for one gateway call. |
| `AI_GATEWAY_DAILY_QUOTA` | `500000` | Tokens per tenant per 24 hours; 0 turns the check off. |

Each call sends `X-Organisation-Id` and `X-Feature-Code`. When the gateway is off, refuses the call or fails:

- **AI suggestions** (`/api/v1/ai/*`) fall back to a built-in heuristic and log a WARN. The saved suggestion
  does not record whether the model or the heuristic produced it.
- **Policy drafts** end `FAILED`.
