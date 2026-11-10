# AI Gateway hardening — progress

> **HISTORIC — point-in-time record of the `fix/ai-gateway-hardening` branch (2026-11-05 to 2026-11-10). Not a statement of current state.**
> For what the gateway does now read [`README_AI_GATEWAY.md`](../README_AI_GATEWAY.md) and [`ai-gateway/README.md`](../ai-gateway/README.md). Kept for the decisions and their reasons.

Branch: `fix/ai-gateway-hardening` (base `2f38875d`). Source: the review of `README_AI_GATEWAY.md`.

## Constraints

- **No push, no merge** without the owner's explicit "merge".
- One commit per verified slice.
- **Never quote the old hardcoded default API key** — not in code, docs, tests or commit messages.
- Fixes **4** (HITL / citation validation), **5** (Arabic guardrails / Presidio) and **6** (residency) are **out of scope**.
  Do not edit `safety_filter.py`, `prompt_injection.py`, or `hallucination.py` (the cache gate only *reads*
  `HallucinationResult.is_hallucination`).
- Build: JDK 17 (`export JAVA_HOME="/c/Program Files/Java/jdk-17"`), full suite `mvn -o clean package`,
  read the `Tests run:` line and `BUILD SUCCESS`.

## Slices

| Slice | Fix | Scope | Status | Commit |
|---|---|---|---|---|
| A | 1 | Java client: LIVE failure throws `GATEWAY_UNAVAILABLE`; no raw error body in `GATEWAY_ERROR`; stub is labelled and fabricates nothing; `AiSuggestionService` fallback logged as WARN and documented honestly | done | `f135d04f` |
| B | 2 (config) + 8 | `enabled=false` and no default key; fail fast at startup when LIVE+enabled and no key; `log.error` on call-log write failure; atomic quota with in-flight reservation | done | `2a452ad4` |
| C | 2 (Python) | `gateway_server.py`: enforce master key, bind 127.0.0.1, CORS allowlist, remove fabricated mock, no `str(ex)` in 502, Gemini key out of the URL | done | `3ea34f5e` |
| D | 3 | Cache keyed on tenant + model + params; bounded LRU with TTL; never cache a hallucination-flagged response | done | `e4afe74b` |
| E | 7 + 8 | README made honest (fallback, Ollama, cache numbers); renumber V401 (kept, see Decisions); full JDK 17 suite | done | `c75275fc` |

## Decisions

- The policy-draft async worker already routes a thrown gateway exception to a failed outcome, so removing the
  silent stub fallback cannot strand a draft.
- `AiSuggestionService`'s heuristic fallback stays: its output is reviewed by a human before acceptance. What
  changes is that it is logged at WARN and documented as a heuristic, never as model output.
- `AiSuggestionService` had a second, silent fallback: a reply that parses but lacks the feature's keys (every
  STUB reply does) fell through to the heuristic with no log. Both paths now log at WARN.
- `AiSuggestion` rows do not record whether the gateway or the heuristic produced them; only the free-text
  rationale hints at it. The javadoc now says so. Storing the source needs a column and a migration, which is
  outside these slices.
- A STUB-mode policy draft now ends FAILED (the marker does not validate as a draft) instead of GENERATED with
  canned text.
- `AiGatewayProperties` bound `grc.ai-gateway`, but the YAML sits under `grc.integrations.ai-gateway` (like every
  other integration). Nothing in the YAML or the `AI_GATEWAY_*` env vars reached the class, so prod's
  `enabled: false` was ignored and the Java defaults (enabled, LIVE, old key) ran everywhere. The prefix is now
  `grc.integrations.ai-gateway`, and `AiGatewayConfigTest` binds the real `application.yml` through it.
- Quota reservation is in-memory, per instance (owner's choice, 2026-11-05, over a DB-backed `RESERVED` row
  plus `sp_getapplock`, which needed a migration). A call starts only while ledger total + in-flight reservations
  is below the quota; the estimate is prompt chars / 3 + `maxOutputTokens` (2048 when unset); the reservation is
  released after the call-log row commits. In a cluster each instance enforces separately, so a tenant can
  overshoot by about one call per instance.
- Gateway with no matching upstream answers 503 `NO_UPSTREAM` (the backend maps it to `GATEWAY_ERROR`). The
  503 still carries `X-Guardrail-Pii-Masked`, since masking ran. `grc-default` routes only to GLM, so with only
  an OpenAI or Gemini key it now gets 503 where the mock used to answer.
- `/health` stays open for monitors; `/metrics` and `/models` need the master key like `/chat/completions`.
- The LiteLLM path (`docker-compose.yml` + `config.yaml`) got the same treatment: master key from
  `GATEWAY_MASTER_KEY` (compose refuses to start without it), and the gateway, Redis and Presidio ports are
  published on 127.0.0.1 only.
- Cache key: SHA-256 over the tenant (`X-Organisation-Id`), the model and the whole request body as canonical
  JSON, after PII masking. The old key lowercased and trimmed the text and ignored every parameter but
  temperature, so "Approve" and "approve", or a JSON-mode and a plain request, shared one reply, across tenants.
  Masking runs first, so two requests that differ only in masked PII share a reply within a tenant; the
  upstream only ever sees the masked text, so that is the reply either would get.
- A request with no `X-Organisation-Id` is not cached at all (`X-Cache: BYPASS`). The backend always sends
  one; `standalone_test.py` now sends `standalone-test` (override with `GATEWAY_TEST_TENANT`).
- Bounds: `GATEWAY_CACHE_MAX_ENTRIES` (default 1000, least recently used dropped first, 0 turns the cache
  off) and `GATEWAY_CACHE_TTL` (default 3600 s, counted from when the reply was stored; a hit does not
  extend it). The bound is an entry count, not bytes.
- A reply with `is_hallucination` set is returned once, with its `X-Guardrail-Hallucination-Risk` header, and
  not stored. The gate only reads the flag; `hallucination.py` is unchanged.
- LiteLLM path: its reply cache is now off in `config.yaml`. Its key comes from the request parameters (the
  tenant is a header), and the hook checks for hallucinations only after the call, with no way to keep the
  reply out of LiteLLM's cache.
- **V401 is not renumbered** (2026-11-10). The collision is with `D:\AYMAN\GRC` (`Ayman-Server/Claude-GRC-2`),
  which stops at V400 on every branch and worktree, so its next migration will also be V401. No number picked
  now is safe: Flyway runs with `out-of-order: false` and `validate-on-migrate: true`, so a version below one
  already applied fails validation, and V402 collides as soon as that tree writes two more migrations.
  Whoever merges the two trees renames this file to the next free version of the merged line at that point.
  The rename touches the four figures `MeasuredBaselineFreshnessTest` checks (`CLAUDE.md`,
  `MEASURED_BASELINE.md`, `baseline_census.py`, the `UNIFIED_IMPLEMENTATION_PLAN.md` ledger), the V401
  mentions in `LlmCallLog.java`, `PolicyAiDraft.java` and `policyDrafts.ts`, and the THROW codes 57401-57405.
  It is safe then too: Flyway is off in dev, and per `CLAUDE.md` the deployed server is at V344, so no
  `flyway_schema_history` row records V401.
- README numbers come from git and the smoke runs, not from version 1.0: the original change deleted 1,815
  lines and added 3,030; the 9 deleted legacy files held 1,530; a cache HIT took 14 ms against 28 ms for a
  miss to a local stub. The 10,885 ms figure and the live GLM run are kept only as "reported by version 1.0".
- `/v1/models` no longer lists `ollama/llama3`: `gateway_server.py` has no route for it.
- The first full run (5,270 tests) had 4 failures. Running those four tests on a worktree of base `2f38875d`
  showed which were inherited:
  - **This branch:** `GovernanceDocumentIntegrityTest` (every `docs/` Markdown needs a `**HISTORIC` banner
    unless listed as maintained; this progress doc is a finished record once slice E lands, so it got the
    banner) and `SingletonMutableStateContractTest` (slice B's `inFlightTokens` and `quotaLocks`, now declared
    `CONCURRENT` with the reasons).
  - **Inherited from base:** `ProxyAdviceInterceptableContractTest` found `AiSuggestionService#persist`
    marked `@Transactional` but reached only by self-calls, so since `b8a9cd7b` the suggestion and its audit
    row committed separately. Fixed rather than added to the census: `persist` now opens a
    `TransactionTemplate`. `DarkServiceMethodTest` found `LlmGatewayService`'s two methods uncalled; the
    `@Deprecated` delegate had no caller anywhere, so it is deleted.

## Notes

- Sources are CRLF in the working tree. `sed -i` in Git Bash rewrites a file as LF; edit with a tool that keeps
  the line endings.

## Slice A result

- `AiGatewayClient`: LIVE transport failure throws `GATEWAY_UNAVAILABLE`; `GATEWAY_ERROR` carries the status
  only and the body is logged by length; STUB returns `STUB_JSON` / `STUB_TEXT`, provider `STUB`, 0/0 tokens.
- No test or frontend code referenced the old stub provider name or its token counts; nothing to fix there.
- `AiSuggestionService`: six fallbacks log at WARN on a thrown call and on an unusable reply; class javadoc and
  `AiSuggestion` javadoc rewritten.
- New `AiGatewayClientTest` (4 cases: LIVE transport failure, LIVE HTTP 500 keeps the body out of the message,
  STUB JSON, STUB text; the STUB cases assert no HTTP call is made).
- `mvn -o test -Dtest=AiGatewayClientTest,AiGatewayServiceTest,AiSuggestionServiceTest` on JDK 17:
  4 + 4 + 15 tests, 0 failures, 0 errors. The full suite is left for slice E.

## Slice B result

- Defaults: `enabled=false`, no API key, in `AiGatewayProperties`, `application.yml` and
  `external.properties.template`. No file under `tss-grc-backend/` holds the old key any more.
- `AiGatewayConfig.checkStartup()` throws at startup when enabled + LIVE + no key; the startup log line now says
  whether a key is set (never the key).
- `AiGatewayService`: a failed call-log write logs at ERROR with org, feature, status and token count; atomic
  in-flight quota reservation as described under Decisions.
- Tests: new `AiGatewayConfigTest` (4), `AiGatewayServiceTest` 4 -> 8 (concurrent call refused on a stale total,
  reservation released on failure and success, estimate, call-log failure logged at ERROR).
- Checked that the YAML-binding and concurrency tests fail against the old prefix / no reservation, then
  restored.
- `mvn -o test` for AiGatewayConfigTest, AiGatewayServiceTest, AiGatewayClientTest, AiSuggestionServiceTest,
  PolicyDraftServiceTest on JDK 17: 4 + 8 + 4 + 15 + 3 = 34 tests, 0 failures, `BUILD SUCCESS`.
- Operator impact: the `AI_GATEWAY_*` env vars now take effect. To use the gateway, set `AI_GATEWAY_ENABLED=true`
  and `AI_GATEWAY_API_KEY`; LIVE + enabled without a key stops startup.

## Slice C result

- `gateway_server.py`: Bearer master key (constant-time compare) on everything but `/health`; no default key and
  the server exits at startup without one; binds `GATEWAY_HOST`, default 127.0.0.1; CORS headers only for
  origins in `GATEWAY_CORS_ORIGINS` (default none); fabricated mock removed (503 instead); 502 says
  "Gateway upstream call failed." with details in the log only; Gemini key sent as `x-goog-api-key`.
- Old key removed from `config.yaml`, `start_gateway.bat`, `start_gateway.ps1` (which also printed it) and
  `standalone_test.py`. Only `ai-gateway/README.md` still holds it (slice E).
- `standalone_test.py`: sends the key to `/metrics`, reports SKIPPED (not FAILED) when there is no upstream.
- New `ai-gateway/tests/test_gateway_server.py` (stdlib unittest, 9 tests: 401s, open health, 503 with nothing
  made up, 502 without exception text, Gemini header, CORS allow-list, refuses empty key, default bind
  127.0.0.1, process exits without a key). Run: `python -m unittest discover -s ai-gateway/tests -v`.
  9/9 pass on Python 3.11. Against the original gateway every test fails or errors for the expected reason.
- Smoke run (`standalone_test.py` against a real gateway on port 4555, no upstream): 5/5 passed, 2 skipped.

## Slice D result

- `gateway_server.py`: new `ResponseCache` (thread-safe `OrderedDict` LRU with TTL) replaces the unbounded
  `CACHE_STORE` dict; `generate_cache_key(tenant, model, payload)`; no tenant means `X-Cache: BYPASS`;
  flagged replies are not stored. `/metrics` also reports `cache_max_entries` and `cache_ttl_seconds`.
- `config.yaml`: LiteLLM `cache: false` (reason in the file).
- `standalone_test.py`: sends `X-Organisation-Id`.
- Tests: `ai-gateway/tests/test_gateway_server.py` 9 -> 17 (same tenant HIT and other tenant MISS; model,
  exact text, temperature, `max_tokens` and `response_format` each change the key; no tenant is not cached;
  a flagged reply is never cached; the server keeps at most `max_entries`; LRU order; TTL from storage;
  size or TTL 0 turns caching off). 17/17 pass on Python 3.11.
- Against the slice C gateway all 8 new tests fail or error for the expected reason (4 HIT where a MISS or
  BYPASS was due, 4 with no `ResponseCache`); the 9 earlier tests still pass there.
- Smoke runs: no upstream, 5/5 passed and 2 skipped; with a local fake GLM upstream (`GLM_BASE_URL`), 7/7
  passed, the second identical request was a HIT and the upstream was called 3 times, not 4.

## Slice E result

- `README_AI_GATEWAY.md` (1.0.0 -> 1.1.0): no fallback and no Ollama route in `gateway_server.py` (LiteLLM's
  real fallback stated separately); cache numbers replaced with measured ones and the quota caveat; prefix
  `grc.integrations.ai-gateway`; backend off by default and what callers get then (heuristic for suggestions,
  FAILED for policy drafts); runbook with the master key, `AI_GATEWAY_*` variables and an authenticated
  `/metrics`; LiteLLM no longer claimed to send identical headers; verification section rewritten; new §7
  summarising slices A-E.
- `ai-gateway/README.md` rewritten: request flow, routing table, cache rules, configuration tables for the
  gateway and the backend, test commands. The old key is gone from it, so no tracked file holds the key any
  more (it is still in git history).
- `gateway_server.py`: `ollama/llama3` dropped from `/v1/models`. `ai-gateway/tests` still 17/17.
- Backend: `AiSuggestionService#persist` runs in a `TransactionTemplate`; `LlmGatewayService` deleted;
  `SingletonMutableStateContractTest` declares the two quota maps; this doc carries the HISTORIC banner.
  `AiSuggestionServiceTest` 15 -> 17 (save and audit commit in one transaction; an audit failure rolls the
  save back). Both new cases fail against the previous `persist`.
- Full suite, `mvn -o clean package` on JDK 17.0.12: `Tests run: 5272, Failures: 0, Errors: 0, Skipped: 0`,
  `BUILD SUCCESS` (2026-11-10). The first run, before the fixes above, was 5,270 with 4 failures.

## Open notes for the owner (not fixed, outside the slices)

- **Rotate the old default API key.** It is still in git history, and 9 files on `origin/main` hold it
  (counted 2026-11-10), so treat it as exposed wherever it is still accepted.

- `call_upstream_gemini` fills in usage 10/20/30 when Gemini omits it; those numbers reach the backend quota.
- `docker-compose.yml` does not pass `GLM_API_KEY` to LiteLLM, yet `grc-default` in `config.yaml` uses it.
- LiteLLM runs with `--detailed_debug`, which logs request bodies (prompts). Not checked whether that logging
  happens before or after the hook masks PII.
- The start scripts run `py -3.10`; `start_gateway.ps1` detects a Python command and then ignores it.
- A cache HIT replays the stored reply's `usage` block, so the backend counts tokens against the tenant's
  quota that no upstream spent.
- An empty reply (a content filter, or `finish_reason` length with no text) is cached like any other.
- The hallucination check grounds a reply against the last user message only, not the system prompt, so a
  reply that cites controls given only in the system prompt scores as ungrounded. Since slice D such a
  reply, once flagged, is also not cached. (Grounding is fix 4, out of scope.)
- The Redis service in `docker-compose.yml` is not used by `config.yaml` (the cache was `local`, now off).

- The Redis comments in `docker-compose.yml` and `requirements.txt` still promise a "semantic" cache that does
  not exist.

## Next

All five slices are done; nothing is pushed or merged. Left for the owner: the V401 rename at merge time (see
Decisions), the open notes above, and "merge" when ready. (The slice E SHA in the table is added after its
commit and is the only uncommitted change.)
