# TSS GRC AI Gateway & Guardrails Subsystem

An enterprise AI Gateway proxy and guardrails engine designed for the **TSS GRC Platform**. Provides OpenAI-compatible endpoint abstraction, caching (in-memory and Redis), and open-source guardrails tailored for Saudi regulatory mandates (**PDPL**, **NCA ECC/CSCC**, **SAMA CSF**).

---

## Architecture Overview

```
                                      ┌─────────────────────────────────────────────────────┐
                                      │              TSS GRC AI Gateway (:4000)             │
                                      │                                                     │
┌───────────────────────────────┐     │  1. Pre-Call Guardrails:                            │      ┌─────────────────────────┐
│       tss-grc-backend         │     │     * Prompt Injection / Jailbreak Filter (HTTP 400)│      │ Upstream LLM Providers: │
│    (LlmGatewayService /       │     │     * PII Masker (Saudi PDPL National ID, IBAN, etc)│      │  - Google Gemini        │
│       LiveLlmClient)          │────►│                                                     │─────►│  - OpenAI               │
└───────────────────────────────┘     │  2. High-Performance Cache (In-Memory / Redis)      │      │  - Local (Ollama/vLLM)  │
                                      │                                                     │      └─────────────────────────┘
┌───────────────────────────────┐     │  3. Post-Call Guardrails:                           │
│   Standalone Test Client      │     │     * Hallucination & Grounding Evaluator           │
│   (standalone_test.py)        │────►│     * GRC Regulatory Control Validator              │
└───────────────────────────────┘     │                                                     │
                                      │  4. Telemetry & Metrics (GET /metrics, /health)     │
                                      └─────────────────────────────────────────────────────┘
```

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

---

## Quick Start & Execution

### Option A: Standalone Gateway Server (Zero Dependencies)
Run directly on Python 3.10 without Docker or external package installations:
```powershell
# In PowerShell:
cd ai-gateway
.\start_gateway.ps1
```
Or directly:
```bash
py -3.10 ai-gateway/gateway_server.py
```
* **OpenAI API**: `http://localhost:4000/v1/chat/completions`
* **Health Check**: `http://localhost:4000/health`
* **Live Metrics**: `http://localhost:4000/metrics`

### Option B: Native LiteLLM Proxy via CLI
If `litellm` is installed in your python environment:
```bash
py -3.10 -m pip install litellm
litellm --config ai-gateway/config.yaml --port 4000
```

### Option C: Production Docker Compose (LiteLLM + Redis + Presidio)
When Docker is available:
```bash
cd ai-gateway
docker compose up -d
```

---

## Standalone Testing Application

A test client is included to verify all gateway capabilities and guardrails:

```bash
# Run the 6-test automated verification suite
py -3.10 ai-gateway/standalone_test.py
```

**Automated Tests Verified:**
1. **Health & Metrics Probe**: Checks `/health` and `/metrics`.
2. **Basic Chat Completion**: Confirms OpenAI JSON schema response.
3. **High-Performance Cache**: Verifies `X-Cache: HIT` and near-zero latency on repeated requests.
4. **Saudi PDPL PII Guardrail**: Verifies masking of National ID, Mobile, Email, and IBAN.
5. **Prompt Injection Defense**: Verifies HTTP 400 rejection on override and DAN attacks.
6. **Hallucination Detection**: Verifies detection of fabricated controls (`NCA-ECC-99-8-1`, `PDPL Article 199`).

### Interactive Sandbox Mode
To manually experiment with prompts and test guardrail behaviors in real-time:
```bash
py -3.10 ai-gateway/standalone_test.py --chat
```

---

## Connecting TSS GRC Backend to the AI Gateway

To route all AI requests from `AiSuggestionService` and `PolicyDraftService` through the Gateway:

### 1. Via Environment Variables
When launching the backend:
```bash
export LLM_MODE=LIVE
export LLM_PROVIDER=OPENAI_COMPATIBLE
export LLM_BASE_URL=http://localhost:4000/v1
export LLM_MODEL=grc-default
export LLM_API_KEY=sk-tss-grc-gateway-master-key
```

### 2. Via `application-dev.yml`
```yaml
grc:
  integrations:
    llm:
      enabled: true
      mode: LIVE
      provider: OPENAI_COMPATIBLE
      base-url: http://localhost:4000/v1
      model: grc-default
      api-key: sk-tss-grc-gateway-master-key
```
The existing `LiveLlmClient` will automatically dispatch to `http://localhost:4000/v1/chat/completions`.
