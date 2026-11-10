# TSS GRC Platform — AI Gateway & Guardrails Architecture

**Complete Changelog, Architectural Overview, and Operational Guide**  
*Document Version: 1.1.0 — November 2026. Corrected by the `fix/ai-gateway-hardening` branch; see §7.*

---

## 1. Executive Summary

This document records the architectural transformation of the **TSS GRC Platform**'s artificial intelligence capabilities: moving from an embedded, vendor-locked monolithic LLM service to a decoupled, high-performance **Standalone AI Gateway with Saudi Regulatory Guardrails**, backed by **Z.ai GLM 4.7**.

### Key Business & Technical Outcomes:
1. **Decoupled Monolith**: The change deleted **1,815 lines** and added 3,030, most of them the Python gateway. 1,746 of the deleted lines were the old in-process multi-provider LLM client, its configuration, its tests and the slimmed-down `LlmGatewayService`. The backend now talks to a single OpenAI-compatible endpoint.
2. **Saudi Regulatory Guardrails**: Embedded automated enforcement for:
   * **Saudi Personal Data Protection Law (PDPL - Royal Decree M/19)**: Anonymizing Saudi National IDs, Iqamas, Mobile numbers, and Saudi IBANs before prompts reach external cloud infrastructure.
   * **Adversarial & Threat Defense**: HTTP 400 rejection, before any upstream call, for prompt injection, DAN jailbreaks, credential probes, and malware solicitations.
   * **Regulatory Hallucination Detection**: Flags citations outside known bounds (Saudi PDPL limited to 43 articles, NCA ECC to 5 domains). A flagged reply is still returned, with an `X-Guardrail-Hallucination-Risk` header; it is not cached.
3. **Reply Cache**: A repeated identical request from the same tenant is answered from the gateway's memory without an upstream call, so it spends no upstream tokens. In the hardening smoke run a HIT took 14 ms against 28 ms for the miss, which went to a local stub rather than a real model; with a real model the miss takes as long as the model call. The backend still counts the replayed reply's tokens against the tenant's daily quota. (Version 1.0 of this document quoted 10,885 ms down to 15 ms from a single run; that run is not reproduced here.)
4. **Architectural Invariant PERF-IO-003 Preserved**: Strict transactional isolation ensuring database connections are never held across external AI model inference.

---

## 2. High-Level Architecture (Before vs. After)

```
==========================================================================================
BEFORE: Embedded Monolithic Architecture (Vendor-Locked, Fragmented)
==========================================================================================

   TSS GRC Backend Monolith (Spring Boot 3.5)
   ┌────────────────────────────────────────────────────────────────────────┐
   │ AiSuggestionService  /  PolicyDraftService                             │
   │           │                                                            │
   │           ▼                                                            │
   │      LiveLlmClient (550 LOC) ──► Custom OpenAI HTTP client             │
   │           │                  ──► Custom Gemini HTTP client             │
   │           │                  ──► Custom GLM HTTP client                │
   │           │                  ──► Custom Anthropic HTTP client          │
   │           ▼                                                            │
   │      LlmProperties (20+ fragmented vendor keys, URLs, model toggles)   │
   └────────────────────────────────────────────────────────────────────────┘

==========================================================================================
AFTER: Decoupled Standalone AI Gateway Architecture (OpenAI-Standard, Guardrailed)
==========================================================================================

   TSS GRC Backend Monolith
   ┌─────────────────────────────────────────────────────────┐
   │ AiSuggestionService  /  PolicyDraftService              │
   │           │                                             │
   │           ▼                                             │
   │      AiGatewayService  (Quota checks & Telemetry)       │
   │           │                                             │
   │           ▼                                             │
   │      AiGatewayClient   (Standard /v1/chat/completions)  │
   └───────────┼─────────────────────────────────────────────┘
               │ HTTP POST 127.0.0.1:4000
               ▼
   ┌────────────────────────────────────────────────────────────────────────┐
   │ STANDALONE AI GATEWAY PROXY (ai-gateway/ :4000)                        │
   │                                                                        │
   │  [0] Bearer master key on every endpoint except /health                │
   │                                                                        │
   │  [1] Pre-Call Guardrails:                                              │
   │      • Safety & Threat Filter (Blocks Admin passwords, CBRN, Malware)  │
   │      • Prompt Injection Filter (Blocks overrides, DAN jailbreaks)     │
   │      • Saudi PDPL PII Masker (Redacts National ID, Iqama, IBAN, Phone) │
   │                                                                        │
   │  [2] Reply Cache (in memory, exact match per tenant, LRU + TTL)        │
   │      • Cache HIT ──► returned without an upstream call                 │
   │                                                                        │
   │  [3] Upstream Inference Router:                                        │
   │      • Primary: Z.ai GLM 4.7 (api.z.ai/api/paas/v4)                   │
   │      • Or Gemini / OpenAI by model name; no fallback, no Ollama        │
   │                                                                        │
   │  [4] Post-Call Guardrails:                                             │
   │      • Hallucination & Bound Checker (flags; reply still returned)     │
   │      • Context Grounding Overlap Scoring                               │
   └───────────────────────────────────┬────────────────────────────────────┘
                                       │
                                       ▼ Upstream API
                            ┌─────────────────────┐
                            │   Z.ai GLM 4.7      │
                            │ (glm-4.7 Live Model)│
                            └─────────────────────┘
```

---

## 3. Inventory of Changes

### A. New Subsystem Added: `ai-gateway/`

| File | Purpose |
|---|---|
| [`ai-gateway/gateway_server.py`](ai-gateway/gateway_server.py) | Multi-threaded HTTP gateway (`ThreadingHTTPServer`) implementing OpenAI `/v1/chat/completions`: Bearer master-key check, a per-tenant in-memory reply cache (bounded LRU with TTL), routing to **one** of Z.ai GLM, OpenAI or Gemini by model name (no fallback, no local-model route), and `/health` & `/metrics` endpoints. Python standard library only. |
| [`ai-gateway/config.yaml`](ai-gateway/config.yaml) | LiteLLM proxy configuration declaring model aliases (`grc-default`, `glm-4.7`, `grc-gemini`, `grc-openai`, `grc-local`), latency routing, a `grc-default` → `grc-gemini` → `grc-openai` fallback, and the guardrail callback. Its reply cache is off. `grc-local` (Ollama) is not in the fallback list. |
| [`ai-gateway/gateway_hook.py`](ai-gateway/gateway_hook.py) | LiteLLM `CustomLogger` callback running the same guardrail modules inside the LiteLLM proxy. It sets none of the `X-Cache` / `X-Guardrail-*` response headers. |
| [`ai-gateway/docker-compose.yml`](ai-gateway/docker-compose.yml) | Compose bundle: LiteLLM proxy (`ghcr.io/berriai/litellm`), Redis 7 and Microsoft Presidio, all published on 127.0.0.1. `config.yaml` does not use Redis, and compose does not pass `GLM_API_KEY` to LiteLLM yet. |
| [`ai-gateway/.env`](ai-gateway/.env) | Local gateway environment configuration (stores `GLM_API_KEY`, upstream base URLs, and master authentication key). |
| [`ai-gateway/requirements.txt`](ai-gateway/requirements.txt) | Python dependencies for running native LiteLLM and Redis in virtual environments. |
| [`ai-gateway/start_gateway.ps1`](ai-gateway/start_gateway.ps1) | PowerShell startup script with port collision detection, stale process cleanup, and daemon launch. Refuses to start without `GATEWAY_MASTER_KEY`; runs `py -3.10`. |
| [`ai-gateway/start_gateway.bat`](ai-gateway/start_gateway.bat) | Windows batch wrapper for starting the standalone server. Refuses to start without `GATEWAY_MASTER_KEY`. |
| [`ai-gateway/standalone_test.py`](ai-gateway/standalone_test.py) | 7-check smoke test client for a running gateway, and an interactive sandbox (`--chat`). Needs the master key. |
| [`ai-gateway/tests/test_gateway_server.py`](ai-gateway/tests/test_gateway_server.py) | 17 standard-library unit tests for `gateway_server.py` (added by the hardening branch). |
| [`ai-gateway/README.md`](ai-gateway/README.md) | Dedicated subsystem operational guide and API reference. |

### B. New Saudi Regulatory Guardrails Subsystem: `ai-gateway/guardrails/`

| File | Guardrail Functionality |
|---|---|
| [`ai-gateway/guardrails/__init__.py`](ai-gateway/guardrails/__init__.py) | Package initialization exporting all guardrail modules. |
| [`ai-gateway/guardrails/pii_masker.py`](ai-gateway/guardrails/pii_masker.py) | **Saudi PDPL Compliance**: In-place regex redaction for Saudi National ID (`1xxxxxxxxx`), Saudi Iqama (`2xxxxxxxxx`), Saudi Mobile (`+9665...`, `05...`), Saudi IBAN (`SA...`), Emails, IPv4, and Luhn-validated Credit Cards. Emits `X-Guardrail-Pii-Masked` count header. |
| [`ai-gateway/guardrails/prompt_injection.py`](ai-gateway/guardrails/prompt_injection.py) | **Adversarial Protection**: Blocks instruction overrides (`"ignore previous instructions"`), persona hijacking (DAN, Developer Mode), system prompt extraction, and ChatML/delimiter spoofing. Rejects with HTTP 400 (`PROMPT_INJECTION_DETECTED`). |
| [`ai-gateway/guardrails/safety_filter.py`](ai-gateway/guardrails/safety_filter.py) | **Enterprise Perimeter Defense**: Blocks administrative password inquiries (`"what is the admin password"`), CBRN/nuclear topics, and ransomware/malware authoring. Rejects with HTTP 400. |
| [`ai-gateway/guardrails/hallucination.py`](ai-gateway/guardrails/hallucination.py) | **Regulatory Grounding**: Enforces strict bounds on citations (e.g. Saudi PDPL has max 43 articles, NCA ECC has max 5 domains). Evaluates lexical overlap against source context. |

### C. Backend Additions: `com.tss.grc.module.integrations.aigateway`

| File | Purpose |
|---|---|
| [`AiGatewayProperties.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/aigateway/config/AiGatewayProperties.java) | Typed Spring properties bean mapped to `grc.integrations.ai-gateway.*` (enabled, mode, baseUrl, model, apiKey, timeoutSeconds, tenantDailyTokenQuota). Defaults: disabled, no API key. (Version 1.0 bound `grc.ai-gateway.*`, so the YAML block and the `AI_GATEWAY_*` variables were ignored until the hardening branch.) |
| [`AiGatewayConfig.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/aigateway/config/AiGatewayConfig.java) | Creates the `aiGatewayRestClient` bean with connection and read timeouts, and stops startup when the gateway is enabled in LIVE mode without an API key. |
| [`AiGatewayClient.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/aigateway/client/AiGatewayClient.java) | Sends OpenAI-style chat completions to the gateway with `X-Organisation-Id` and `X-Feature-Code`, and logs the `X-Cache` / `X-Guardrail-*` headers. In LIVE mode a failed call throws (`GATEWAY_UNAVAILABLE`, `GATEWAY_ERROR`); it never falls back to canned text. STUB mode calls nothing and returns a reply marked as a stub. |
| [`AiGatewayService.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/aigateway/service/AiGatewayService.java) | Facade enforcing the tenant daily token quota, with an in-memory reservation for calls in progress (per backend instance), and logging non-sensitive telemetry into `platform.llm_call_log` via `REQUIRES_NEW` transactions. A failed log write is logged at ERROR. |
| [`AiGatewayServiceTest.java`](tss-grc-backend/src/test/java/com/tss/grc/module/integrations/aigateway/service/AiGatewayServiceTest.java) | Unit tests covering gateway dispatch, quota enforcement including concurrent calls, and transaction boundaries. |
| [`AiGatewayClientTest.java`](tss-grc-backend/src/test/java/com/tss/grc/module/integrations/aigateway/client/AiGatewayClientTest.java), [`AiGatewayConfigTest.java`](tss-grc-backend/src/test/java/com/tss/grc/module/integrations/aigateway/config/AiGatewayConfigTest.java) | Added by the hardening branch: LIVE failures throw, STUB calls nothing, safe defaults, the startup check, and `application.yml` binding to the properties prefix. |
| [`test_llm_keys.ps1`](test_llm_keys.ps1) | PowerShell utility for validating upstream LLM vendor keys. |

### D. Backend Modifications

| File | Nature of Changes |
|---|---|
| [`AiSuggestionService.java`](tss-grc-backend/src/main/java/com/tss/grc/module/ai/service/AiSuggestionService.java) | Replaced dependency on old `LlmGatewayService` with `AiGatewayService`. All 6 AI methods (`suggestRiskDescription`, `suggestBiaRtoRpo`, `reviewEvidenceQuality`, `draftIncidentRootCause`, `draftBoardSummary`, `suggestWorkflowSubmissionReview`) now dispatch to the gateway. When the gateway is off, refuses the call or fails, each falls back to a built-in heuristic and logs a WARN; the saved suggestion does not record whether the model or the heuristic produced it. |
| [`PolicyDraftService.java`](tss-grc-backend/src/main/java/com/tss/grc/module/policy/drafting/service/PolicyDraftService.java) | Rewired bilingual policy drafting to dispatch through `AiGatewayService`. |
| [`PolicyDraftAsyncWorker.java`](tss-grc-backend/src/main/java/com/tss/grc/module/policy/drafting/service/PolicyDraftAsyncWorker.java) | Rewired asynchronous policy drafting and the secondary repair pass to use `AiGatewayService`. |
| [`LlmGatewayService.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/service/LlmGatewayService.java) | Stripped out 500+ lines of vendor-specific plumbing and kept as a `@Deprecated` delegate to `AiGatewayService`. Nothing called it, so the hardening branch deleted it. |
| [`application.yml`](tss-grc-backend/src/main/resources/application.yml) & [`application-prod.yml`](tss-grc-backend/src/main/resources/application-prod.yml) | Replaced 60+ lines of fragmented vendor configuration blocks (`openai.*`, `gemini.*`, `claude.*`) with a single `grc.integrations.ai-gateway` configuration block (off by default; see §5). |
| [`external.properties.template`](tss-grc-backend/external.properties.template) | Updated external configuration template with `AI_GATEWAY_*` parameters. |
| [`AiSuggestionServiceTest.java`](tss-grc-backend/src/test/java/com/tss/grc/module/ai/service/AiSuggestionServiceTest.java) | Updated mocks from `LlmGatewayService` to `AiGatewayService`. |
| [`PolicyDraftServiceTest.java`](tss-grc-backend/src/test/java/com/tss/grc/module/policy/drafting/service/PolicyDraftServiceTest.java) | Updated test fixtures and mocks to use `AiGatewayService`. |

### E. Deleted Legacy Code (9 Files, 1,530 Lines)

The following files belonging to the earlier in-process multi-provider LLM gateway (built 30/10–31/10) were completely deleted:
* `tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/client/LiveLlmClient.java` (550 LOC)
* `tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/client/LlmClient.java`
* `tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/client/StubLlmClient.java`
* `tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/config/LlmIntegrationConfig.java`
* `tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/config/LlmProperties.java`
* `tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/sanitize/PromptSanitizer.java`
* `tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/sanitize/NoOpPromptSanitizer.java`
* `tss-grc-backend/src/test/java/com/tss/grc/module/integrations/llm/client/LiveLlmClientTest.java`
* `tss-grc-backend/src/test/java/com/tss/grc/module/integrations/llm/service/LlmGatewayServiceTest.java`

---

## 4. UI Services Powered by the AI Gateway

All 6 AI endpoints defined under `/api/v1/ai` in [`AiSuggestionController`](tss-grc-backend/src/main/java/com/tss/grc/module/ai/controller/AiSuggestionController.java) are integrated into the React frontend (`tss-grc-ui-v2`). They reach the model only when `AI_GATEWAY_ENABLED=true` (off by default) and the gateway has an upstream key; otherwise they return the heuristic draft described in §3D:

| # | Endpoint | UI Screen / Route | Frontend Trigger | Model Functionality |
|---|---|---|---|---|
| **1** | `POST /api/v1/ai/risk-description` | `/workflows/erm/risk-identification` | Button: *"Draft with AI"* (`ملء بالذكاء الاصطناعي`) | Generates ERM risk statement, bilingual EN/AR text, causes, and consequences. |
| **2** | `POST /api/v1/ai/bia-rto-rpo` | `/bcm/bia` | Button: *"AI suggest recovery targets"* (`اقتراح أهداف التعافي`) | Analyzes service criticality against NCEMA/SAMA standards; proposes RTO, RPO, and MTPD. |
| **3** | `POST /api/v1/ai/evidence-quality` | `/evidence` | Table Action: Sparkles icon (*"AI quality check"*) | Scores defensibility of audit evidence, detects control linkage gaps, and suggests fixes. |
| **4** | `POST /api/v1/ai/incident-root-cause` | `/workflows/nca/cyber-incident` | Button: *"Draft with AI"* (`مسودة بالذكاء الاصطناعي`) | Performs 5-Whys root cause analysis on cyber crisis events for CISO review. |
| **5** | `POST /api/v1/ai/board-summary` | `/buyer-gap` | Button: *"Generate board summary"* | Aggregates open risks, overdue CAPAs, and compliance % into an executive board briefing. |
| **6** | `POST /api/v1/ai/workflow-submission-review` | `/workflows/submissions/:id` | Button: *"Generate review hint"* (`توليد الإرشاد`) | Pre-approval compliance check generating a bilingual checklist and suggested decision (`APPROVE`/`REJECT`). |

### Human-in-the-Loop Governance Queue
* **Route**: `/ai/suggestions` ([`AiSuggestionsPage.tsx`](tss-grc-ui-v2/src/pages/ai/AiSuggestionsPage.tsx))
* In accordance with Saudi GRC principle §27, **no AI output ever mutates production records directly**. Reviewers inspect the full input/output JSON and click **Accept** (`قبول`) or **Reject** (`رفض`) with audit notes.

---

## 5. Operational Runbook

[`ai-gateway/README.md`](ai-gateway/README.md) has the full configuration tables, routing rules and cache behaviour.

### Running the Gateway Daemon
The gateway listens on 127.0.0.1:4000 and will not start without a master key:

```powershell
# In PowerShell:
$env:GATEWAY_MASTER_KEY = "<the backend's AI_GATEWAY_API_KEY value>"
$env:GLM_API_KEY = "<your Z.ai key>"
cd ai-gateway
python gateway_server.py
```
Or use the startup script, which also refuses to start without `GATEWAY_MASTER_KEY` and runs `py -3.10`:
```powershell
cd ai-gateway
.\start_gateway.ps1
```
With no upstream key set, chat requests get HTTP 503 `NO_UPSTREAM`. There is no fallback between providers and no local-model (Ollama) route in `gateway_server.py`.

### Pointing the Backend at the Gateway
```powershell
$env:AI_GATEWAY_ENABLED = "true"          # off by default, including the prod profile
$env:AI_GATEWAY_API_KEY = "<same value as GATEWAY_MASTER_KEY>"
# optional: AI_GATEWAY_BASE_URL (default http://localhost:4000/v1), AI_GATEWAY_MODEL (default grc-default)
```
Enabled in LIVE mode without `AI_GATEWAY_API_KEY`, the backend refuses to start.

### Running Automated Verification Tests
```powershell
# Unit tests for gateway_server.py (standard library only):
python -m unittest discover -s ai-gateway/tests -v

# 7-check smoke test against a running gateway (checks 2 and 3 are skipped without an upstream):
$env:GATEWAY_API_KEY = "<master key>"
python ai-gateway\standalone_test.py

# Interactive chat sandbox:
python ai-gateway\standalone_test.py --chat
```

### Health & Telemetry Probes
```bash
# Gateway Health Check (no key)
curl http://127.0.0.1:4000/health

# Gateway Metrics & Guardrail Counters (key required)
curl -H "Authorization: Bearer <master key>" http://127.0.0.1:4000/metrics
```

### Switching to Native LiteLLM Proxy
```powershell
# 1. Install LiteLLM proxy
python -m pip install "litellm[proxy]"

# 2. Run LiteLLM using the existing config & guardrail hook (needs GATEWAY_MASTER_KEY and GLM_API_KEY)
litellm --config ai-gateway/config.yaml --port 4000
```
Both servers expose the same OpenAI `/v1/chat/completions` API on port 4000, so the backend needs no change. They are not identical, though: LiteLLM falls back from GLM to Gemini and OpenAI, its reply cache is off, and its hook sets none of the `X-Cache` / `X-Guardrail-*` headers.

---

## 6. Verification & Compilation Status

* **Java Monolith**: Version 1.0 reported a `javac 18.0.1.1` compile only. The hardening branch runs the full suite, `mvn -o clean package` on JDK 17: **5,272 tests, 0 failures, 0 errors, `BUILD SUCCESS`** (2026-11-10).
* **Gateway**: `ai-gateway/tests` 17/17 on Python 3.11. `standalone_test.py` against a running gateway with a local stub upstream: 7/7.
* **Architectural Invariant `PERF-IO-003`**: Preserved. Outbound inference does not hold open database transactions.
* **Upstream Connectivity**: Version 1.0 reported a live run against **Z.ai GLM 4.7** (`https://api.z.ai/api/paas/v4`). The hardening branch did not call a real model.

---

## 7. Hardening (`fix/ai-gateway-hardening`, November 2026)

The review of version 1.0 found the gateway open to anyone who could reach it, a backend configuration that never took effect, made-up replies, and a cache shared across tenants. Fixed, one commit per slice ([`docs/AI_GATEWAY_HARDENING_PROGRESS.md`](docs/AI_GATEWAY_HARDENING_PROGRESS.md) has the details):

| Slice | What changed |
|---|---|
| A | The Java client throws on a LIVE failure instead of returning canned text; STUB replies are labelled; the heuristic fallback in `AiSuggestionService` is logged at WARN. |
| B | The backend is off by default with no default API key; the properties prefix matches the YAML; startup fails when enabled in LIVE mode without a key; the tenant quota reserves tokens for calls in progress. |
| C | `gateway_server.py` requires the master key, listens on 127.0.0.1, sends CORS headers only to allow-listed origins, answers 503 instead of making up replies, and keeps the Gemini key out of URLs. |
| D | The reply cache is per tenant, keyed on the model and the whole request, bounded (LRU + TTL), and never stores a hallucination-flagged reply. LiteLLM's cache is off. |
| E | This document and `ai-gateway/README.md` corrected; the old default key removed from the last file that held it. |

The old default API key is still in git history and was pushed with version 1.0, so treat it as exposed and rotate it wherever it is still accepted.
