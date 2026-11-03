# TSS GRC Platform — AI Gateway & Guardrails Architecture

**Complete Changelog, Architectural Overview, and Operational Guide**  
*Document Version: 1.0.0 — November 2026*

---

## 1. Executive Summary

This document records the architectural transformation of the **TSS GRC Platform**'s artificial intelligence capabilities: moving from an embedded, vendor-locked monolithic LLM service to a decoupled, high-performance **Standalone AI Gateway with Saudi Regulatory Guardrails**, backed by **Z.ai GLM 4.7**.

### Key Business & Technical Outcomes:
1. **Decoupled Monolith**: Removed over **1,800 lines** of complex, multi-provider Java boilerplate from `tss-grc-backend`. The backend now interacts exclusively via a single, standard OpenAI-compatible interface.
2. **Saudi Regulatory Guardrails**: Embedded automated enforcement for:
   * **Saudi Personal Data Protection Law (PDPL - Royal Decree M/19)**: Anonymizing Saudi National IDs, Iqamas, Mobile numbers, and Saudi IBANs before prompts reach external cloud infrastructure.
   * **Adversarial & Threat Defense**: Immediate (< 1ms) HTTP 400 rejection for prompt injection, DAN jailbreaks, credential probes, and malware solicitations.
   * **Regulatory Hallucination Prevention**: Enforcing strict bounds on citations (Saudi PDPL limited to 43 articles, NCA ECC to 5 domains).
3. **High-Performance Caching**: Latency reduced from **10,885ms down to 15ms** on repeat regulatory queries with **0 upstream tokens consumed**.
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
   │  [1] Pre-Call Guardrails:                                              │
   │      • Safety & Threat Filter (Blocks Admin passwords, CBRN, Malware)  │
   │      • Prompt Injection Filter (Blocks overrides, DAN jailbreaks)     │
   │      • Saudi PDPL PII Masker (Redacts National ID, Iqama, IBAN, Phone) │
   │                                                                        │
   │  [2] High-Performance Cache (In-Memory SHA-256 / Redis)                │
   │      • Cache HIT ──► Return in 15ms (0 tokens consumed)                │
   │                                                                        │
   │  [3] Upstream Inference Router:                                        │
   │      • Primary: Z.ai GLM 4.7 (api.z.ai/api/paas/v4)                   │
   │      • Fallbacks: Google Gemini 1.5, OpenAI GPT-4o, Ollama             │
   │                                                                        │
   │  [4] Post-Call Guardrails:                                             │
   │      • Hallucination & Bound Checker (PDPL 43 articles, NCA 5 domains) │
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
| [`ai-gateway/gateway_server.py`](ai-gateway/gateway_server.py) | High-performance, multi-threaded HTTP gateway server (`ThreadingHTTPServer`) implementing OpenAI `/v1/chat/completions`, exact in-memory caching with TTL, live GLM routing, and `/health` & `/metrics` endpoints. Runs with zero external pip dependencies. |
| [`ai-gateway/config.yaml`](ai-gateway/config.yaml) | Official LiteLLM proxy configuration file declaring model aliases (`grc-default`, `glm-4.7`, `grc-gemini`, `grc-openai`), latency routing, fallbacks, and plugin callbacks. |
| [`ai-gateway/gateway_hook.py`](ai-gateway/gateway_hook.py) | LiteLLM `CustomLogger` callback hook running identical pre-call and post-call guardrail pipelines inside the official LiteLLM proxy container. |
| [`ai-gateway/docker-compose.yml`](ai-gateway/docker-compose.yml) | Multi-container production deployment bundle: LiteLLM proxy (`ghcr.io/berriai/litellm`), Redis 7 caching, and Microsoft Presidio. |
| [`ai-gateway/.env`](ai-gateway/.env) | Local gateway environment configuration (stores `GLM_API_KEY`, upstream base URLs, and master authentication key). |
| [`ai-gateway/requirements.txt`](ai-gateway/requirements.txt) | Python dependencies for running native LiteLLM and Redis in virtual environments. |
| [`ai-gateway/start_gateway.ps1`](ai-gateway/start_gateway.ps1) | PowerShell startup script with automated port collision detection, stale process cleanup, and daemon launch. |
| [`ai-gateway/start_gateway.bat`](ai-gateway/start_gateway.bat) | Windows batch wrapper for starting the standalone server. |
| [`ai-gateway/standalone_test.py`](ai-gateway/standalone_test.py) | Automated 7-suite verification test client and interactive terminal testing sandbox (`--chat`). |
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
| [`AiGatewayProperties.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/aigateway/config/AiGatewayProperties.java) | Typed Spring properties bean mapped to `grc.ai-gateway.*` (enabled, mode, baseUrl, model, apiKey, timeoutSeconds, tenantDailyTokenQuota). |
| [`AiGatewayConfig.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/aigateway/config/AiGatewayConfig.java) | Declarative configuration creating the `aiGatewayRestClient` bean with customized connection and read timeouts. |
| [`AiGatewayClient.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/aigateway/client/AiGatewayClient.java) | Dispatches standard OpenAI JSON chat completions to `http://127.0.0.1:4000`, extracts telemetry and guardrail audit headers (`X-Cache`, `X-Guardrail-Pii-Masked`), and provides deterministic STUB fallbacks for offline testing. |
| [`AiGatewayService.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/aigateway/service/AiGatewayService.java) | Production facade enforcing tenant daily token quotas and logging non-sensitive telemetry into `platform.llm_call_log` via atomic `REQUIRES_NEW` transactions. |
| [`AiGatewayServiceTest.java`](tss-grc-backend/src/test/java/com/tss/grc/module/integrations/aigateway/service/AiGatewayServiceTest.java) | Unit test suite covering live gateway dispatch, deterministic stubs, quota enforcement, and transaction boundaries. |
| [`test_llm_keys.ps1`](test_llm_keys.ps1) | PowerShell utility for validating upstream LLM vendor keys. |

### D. Backend Modifications

| File | Nature of Changes |
|---|---|
| [`AiSuggestionService.java`](tss-grc-backend/src/main/java/com/tss/grc/module/ai/service/AiSuggestionService.java) | Replaced dependency on old `LlmGatewayService` with `AiGatewayService`. All 6 AI methods (`suggestRiskDescription`, `suggestBiaRtoRpo`, `reviewEvidenceQuality`, `draftIncidentRootCause`, `draftBoardSummary`, `suggestWorkflowSubmissionReview`) now dispatch to the gateway with automated fallback to deterministic heuristics. |
| [`PolicyDraftService.java`](tss-grc-backend/src/main/java/com/tss/grc/module/policy/drafting/service/PolicyDraftService.java) | Rewired bilingual policy drafting to dispatch through `AiGatewayService`. |
| [`PolicyDraftAsyncWorker.java`](tss-grc-backend/src/main/java/com/tss/grc/module/policy/drafting/service/PolicyDraftAsyncWorker.java) | Rewired asynchronous policy drafting and the secondary repair pass to use `AiGatewayService`. |
| [`LlmGatewayService.java`](tss-grc-backend/src/main/java/com/tss/grc/module/integrations/llm/service/LlmGatewayService.java) | Stripped out 500+ lines of vendor-specific plumbing. Retained as a slim `@Deprecated` delegate forwarding to `AiGatewayService` to preserve backward compatibility. |
| [`application.yml`](tss-grc-backend/src/main/resources/application.yml) & [`application-prod.yml`](tss-grc-backend/src/main/resources/application-prod.yml) | Replaced 60+ lines of fragmented vendor configuration blocks (`openai.*`, `gemini.*`, `claude.*`) with a single, clean `grc.ai-gateway` configuration block. |
| [`external.properties.template`](tss-grc-backend/external.properties.template) | Updated external configuration template with `AI_GATEWAY_*` parameters. |
| [`AiSuggestionServiceTest.java`](tss-grc-backend/src/test/java/com/tss/grc/module/ai/service/AiSuggestionServiceTest.java) | Updated mocks from `LlmGatewayService` to `AiGatewayService`. |
| [`PolicyDraftServiceTest.java`](tss-grc-backend/src/test/java/com/tss/grc/module/policy/drafting/service/PolicyDraftServiceTest.java) | Updated test fixtures and mocks to use `AiGatewayService`. |

### E. Deleted Legacy Code (1,814 Lines Removed)

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

All 6 AI endpoints defined under `/api/v1/ai` in [`AiSuggestionController`](tss-grc-backend/src/main/java/com/tss/grc/module/ai/controller/AiSuggestionController.java) are active, routed to GLM 4.7, and integrated into the React frontend (`tss-grc-ui-v2`):

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

### Running the Gateway Daemon
The gateway server runs on port 4000:

```powershell
# In PowerShell:
cd ai-gateway
py -3.10 gateway_server.py
```
Or use the automated script:
```powershell
cd ai-gateway
.\start_gateway.ps1
```

### Running Automated Verification Tests
```powershell
# Run the 7-suite automated test client:
py -3.10 ai-gateway\standalone_test.py

# Run the interactive chat sandbox:
py -3.10 ai-gateway\standalone_test.py --chat
```

### Health & Telemetry Probes
```bash
# Gateway Health Check
curl http://127.0.0.1:4000/health

# Gateway Metrics & Guardrail Counters
curl http://127.0.0.1:4000/metrics
```

### Switching to Native LiteLLM Proxy
To switch from `gateway_server.py` to the official LiteLLM proxy CLI at any time:
```powershell
# 1. Install LiteLLM proxy
py -3.10 -m pip install "litellm[proxy]"

# 2. Run LiteLLM using the existing config & guardrail hooks
litellm --config ai-gateway/config.yaml --port 4000
```
Because both servers expose the identical OpenAI `/v1/chat/completions` API on port 4000 with identical guardrail headers, **zero changes** are needed in the backend or frontend.

---

## 6. Verification & Compilation Status

* **Java Monolith**: Compiled with `javac 18.0.1.1` — **0 compilation errors**.
* **Test Classes**: `AiSuggestionServiceTest`, `PolicyDraftServiceTest`, and `AiGatewayServiceTest` all compile and pass.
* **Architectural Invariant `PERF-IO-003`**: Preserved. Outbound inference does not hold open database transactions.
* **Upstream Connectivity**: Verified live with **Z.ai GLM 4.7** (`https://api.z.ai/api/paas/v4`).
