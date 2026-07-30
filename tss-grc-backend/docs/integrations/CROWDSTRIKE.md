# CrowdStrike Falcon EDR Bridge

Sprint 12 / slice 3 of the integrations work. The adapter is **bidirectional**:

1. **Pull** — polls Falcon's `/detects` API on a fixed-delay schedule and
   materialises each new detection as a `socops.security_event` row plus an
   `integration_external_link` mapping (`integration='CROWDSTRIKE'`).
2. **Push** — issues host-containment actions to Falcon
   (`/devices/entities/devices-actions/v2`) when an analyst decides an
   endpoint needs to be isolated.

Code lives under `com.tss.grc.module.integrations.crowdstrike`. Mirrors the
ServiceNow + Splunk adapter packaging — see `SERVICENOW.md` and
`SPLUNK.md` for the siblings.

## Why this exists

CrowdStrike Falcon is the dominant endpoint-detection platform across Saudi
banks (TSS Saudi Bank, STC Bank), Aramco, and the broader GCC enterprise
market. Without the bridge:

- The SOC analyst is the integration — they read Falcon, decide
  containment in Falcon, then re-key the event into GRC for SLA tracking.
  That's slow and error-prone (NCA ECC-2 1-hour CRITICAL SLA does not
  leave room for re-keying).
- Automation rules in GRC (F8) can't react to EDR detections because the
  detection lives behind Falcon's UI.

With the bridge the analyst stays in GRC: detections appear in the SOC
triage queue with NCA-aligned SLA timers on insert, and the "isolate
host" decision sits one click away with a full audit trail in
`crowdstrike_action_log`.

## Modes

Two implementations of `CrowdStrikeClient`; chosen at startup by
`grc.integrations.crowdstrike.mode`:

| Mode | Class | Behaviour |
|------|-------|-----------|
| `STUB` (default) | `StubCrowdStrikeClient` | In-memory simulator. Deterministic detection ids `ldt:aid-fake-host-{n}:{seq}`. Severities cycle 95 / 75 / 55 / 25 so each batch contains one of every NCA-ECC2 bucket. Records every call. Default in dev/demo + unit tests. |
| `LIVE` | `LiveCrowdStrikeClient` | Real HTTPS against `{base-url}`. OAuth2 bearer token from `CrowdStrikeOAuthTokenManager`. Retries 5xx + connection errors with exponential back-off; one-shot OAuth refresh on 401. |

## Switching from STUB to LIVE

### 1. Create a Falcon API client

In the Falcon console:
**Support and resources → API clients and keys → Add new API client**

Required scopes:

| Scope                         | Why                                                 |
|-------------------------------|-----------------------------------------------------|
| `Detections (Read)`           | Poll `/detects/queries/detects/v1`                  |
| `Detections (Write)`          | Future — bulk-update detection status from GRC      |
| `Hosts (Write)`               | Run `/devices/entities/devices-actions/v2` actions  |
| `Sensor download (Read)`      | Cheap probe used by `isHealthy()` health-check      |

Save the `client_id` + `client_secret` immediately — Falcon only shows the
secret once.

### 2. Pick a cloud region

| Cloud | Base URL                                      | Use when                                                                                       |
|-------|-----------------------------------------------|------------------------------------------------------------------------------------------------|
| US-1  | `https://api.crowdstrike.com`                 | Default Falcon cloud — KSA tenants generally do **not** use this (cross-border data flow).     |
| US-2  | `https://api.us-2.crowdstrike.com`            | Alternate US region — same cross-border concern.                                               |
| **EU-1** | `https://api.eu-1.crowdstrike.com`         | **KSA-resident default.** Frankfurt — keeps customer telemetry inside EU/EEA (PDPL Art. 29 compatible after a documented data-flow review). |
| Gov   | `https://api.laggar.gcw.crowdstrike.com`      | US gov tenants — not used in KSA deployments.                                                  |

EU-1 has been accepted by Saudi NDMO for SAMA-supervised workloads where
US-1 would cross the PDPL Art. 29 cross-border boundary. Pick EU-1
unless you have a documented exception for US-1 / US-2.

### 3. Set env vars

```bash
export CROWDSTRIKE_CLIENT_ID="abc123def456..."
export CROWDSTRIKE_CLIENT_SECRET="..."   # never commit
```

### 4. Flip the profile config

```yaml
grc:
  integrations:
    crowdstrike:
      enabled: true
      mode: LIVE
      base-url: https://api.eu-1.crowdstrike.com
      polling-enabled: false   # leave OFF until manual /poll validates
      polling-interval-seconds: 60
      retry:
        max-attempts: 3
        initial-delay-ms: 500
        backoff-multiplier: 2.0
      timeout-ms: 5000
```

The master `enabled` flag and the loop's `polling-enabled` flag are
deliberately independent. Operators should:

1. Enable the adapter (`enabled: true`, `polling-enabled: false`).
2. Trigger one manual poll via `POST /poll` and inspect
   `socops.security_event` for the result.
3. Once confirmed, flip `polling-enabled: true` so the fixed-delay loop
   runs every `polling-interval-seconds`.

### 5. Confirm reachability

```bash
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/crowdstrike/status
```

Expected body:

```json
{
  "enabled": true,
  "pollingEnabled": false,
  "mode": "LIVE",
  "baseUrl": "https://api.eu-1.crowdstrike.com",
  "healthy": true,
  "lastSuccessfulPollAt": null,
  "lastActionAt": null
}
```

## Severity mapping

Falcon emits a 1..100 severity. GRC uses NCA-ECC2 buckets everywhere:

| Falcon range | GRC severity | NCA-ECC2 SLA |
|--------------|--------------|--------------|
| 90..100      | CRITICAL     | 1 hour       |
| 70..89       | HIGH         | 4 hours      |
| 40..69       | MEDIUM       | 24 hours     |
| 1..39        | LOW          | 72 hours     |
| missing/null | MEDIUM       | 24 hours     |

Mirrors Splunk's "missing → MEDIUM" default so a missing severity is
never silently queued at LOW.

## Polling vs streaming

v1 of this adapter **polls**. CrowdStrike also offers a Streaming API
(SSE-style) at `/sensors/entities/datafeed/v2`, which is lower-latency
and more efficient at high event rates. We deliberately picked polling
for the first slice because:

- Polling is stateless on our side — failure recovery is "wait for the
  next interval", no resume cursor.
- Streaming requires a per-instance long-lived connection that needs
  separate health-checking and reconnection logic.
- Saudi-bank SOC volumes (≈ 200–2000 detections / day per tenant) are
  comfortably within polling reach at the 60s default interval.

A streaming adapter is a future slice — track in the backlog under
"CrowdStrike streaming receiver".

## Multi-tenant Falcon CID (follow-up)

v1 assumes **one Falcon Customer ID (CID) per GRC deployment**. Multi-tenant
deployments where each GRC tenant maps to its own Falcon CID need:

- Per-tenant `client_id` / `client_secret`.
- Per-tenant `base-url` (different KSA banks may use different Falcon
  clouds for sovereignty reasons).
- A token cache keyed by `(tenant, cid)` rather than just by JVM.

The simplest path is a per-tenant `CrowdStrikeTenantConfig` table read
on poll. Tracked as a separate sprint — out of scope for slice 3.

## REST endpoints

| Method | Path                                                | Roles                                                     |
|--------|-----------------------------------------------------|-----------------------------------------------------------|
| GET    | `/api/v1/integrations/crowdstrike/status`           | PLATFORM_ADMIN, CISO, SOC_ANALYST, SOC_TIER_2, SOC_AUTOMATION, INCIDENT_COMMANDER |
| GET    | `/api/v1/integrations/crowdstrike/config`           | PLATFORM_ADMIN, CISO                                      |
| POST   | `/api/v1/integrations/crowdstrike/poll`             | **`CYBER:WRITE`** *(was PLATFORM_ADMIN, SOC_AUTOMATION — SOC_AUTOMATION does not exist, so this was admin-only; F2-05)* |
| POST   | `/api/v1/integrations/crowdstrike/contain/{hostId}` | SOC_TIER_2, INCIDENT_COMMANDER, CISO, PLATFORM_ADMIN      |
| POST   | `/api/v1/integrations/crowdstrike/lift/{hostId}`    | CISO, PLATFORM_ADMIN                                      |
| GET    | `/api/v1/integrations/crowdstrike/stub/recorded-calls` | PLATFORM_ADMIN, CISO                                    |

The `lift` endpoint is intentionally narrower than `contain`: the
restore-from-isolation decision sits with security leadership, not Tier
2 analysts.

## Smoke-test curls

Set `$JWT` to a valid access token first
(e.g. `curl -X POST http://localhost:8084/api/v1/auth/login ...`).

```bash
# Status — checks kill-switch, mode, last poll/action timestamps
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/crowdstrike/status

# Manual poll — pulls detections from Falcon, writes security_event rows
curl -X POST -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/crowdstrike/poll

# Contain a host — must include a justification reason for the audit trail
curl -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Ransomware confirmed on PCI server via Falcon detection ldt:..."}' \
  http://localhost:8084/api/v1/integrations/crowdstrike/contain/<aid>

# Lift containment — CISO / PLATFORM_ADMIN only
curl -X POST -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/crowdstrike/lift/<aid>

# STUB-only — inspect what the simulator has seen
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/crowdstrike/stub/recorded-calls
```

## Audit + automation

Every ingest emits **F7 + F8**:

| Trigger                              | When fired                                          | Payload keys                                                                       |
|--------------------------------------|-----------------------------------------------------|------------------------------------------------------------------------------------|
| `CROWDSTRIKE_DETECTION_INGESTED`     | New detection → security_event row                  | `severity`, `falconDetectionId`, `falconHostId`, `hostname`, `mitreAttackId`       |
| `CROWDSTRIKE_HOST_CONTAINED`         | `containHost()` succeeded                            | `falconHostId`, `actionType`, `reason`, `falconResourceId`, `attemptCount`         |
| `CROWDSTRIKE_CONTAINMENT_LIFTED`     | `liftContainment()` succeeded                       | (same shape)                                                                       |
| `CROWDSTRIKE_HOST_HIDDEN`            | `hideHost()` succeeded                              | (same shape)                                                                       |

Wire the F8 trigger into Automation Rules to drive workflows like
"auto-open a SAMA reportable incident on CRITICAL Falcon detection
inside the PCI zone".

## Tables (V170)

- `platform.crowdstrike_detection_log` — one row per Falcon detection
  observed. Unique constraint on `(organisation_id, falcon_detection_id)`.
- `platform.crowdstrike_action_log` — one row per containment attempt.
  Carries `requested_by_user_id` for NCA-ECC2 traceability.

The generic `platform.integration_external_link` (V163) is reused with
`integration='CROWDSTRIKE'`.

---

# واجهة CrowdStrike Falcon — ملاحظات عربية

نظام **CrowdStrike Falcon** هو منصة الكشف والاستجابة في الأجهزة الطرفية
(EDR) السائدة في البنوك السعودية (TSS، STC Bank) وأرامكو. هذا المحوّل
يوفّر:

1. **سحب**: استعلام دوري عن `‎/detects‎` وتحويل كل اكتشاف جديد إلى
   `socops.security_event` مع ربط جدول `integration_external_link`
   (الجدول العام من V163).
2. **دفع**: عزل المضيف عبر
   `‎/devices/entities/devices-actions/v2‎` بعد قرار محلل SOC.

### نقاط مهمة للالتزام التنظيمي

- **EU-1 الافتراضي**: تمّت اعتماد المنطقة الأوروبية EU-1 (فرانكفورت)
  افتراضياً لإبقاء البيانات داخل المنطقة الاقتصادية الأوروبية تماشياً
  مع المادة 29 من نظام حماية البيانات الشخصية السعودي (PDPL). تجنّب
  US-1 (الافتراضي عند CrowdStrike) إلا بعد مراجعة موثّقة لتدفق
  البيانات.
- **مصفوفة SLA**: تتبع مصفوفة الهيئة الوطنية للأمن السيبراني NCA-ECC2:
  - حرج (CRITICAL): ساعة واحدة
  - عالي (HIGH): 4 ساعات
  - متوسط (MEDIUM): 24 ساعة
  - منخفض (LOW): 72 ساعة
- **مفتاحان منفصلان للتشغيل**: `enabled` للتفعيل العام و
  `polling-enabled` لحلقة الاستعلام الدورية. الفصل متعمّد — يبدأ
  المشغّل بالتفعيل الكلّي مع إيقاف الحلقة، يجرّب الاستعلام اليدوي مرة
  عبر `POST /poll`، ثم يفعّل الحلقة الدورية.
- **التتبع الكامل**: كل عملية احتواء/رفع تُسجَّل في
  `crowdstrike_action_log` مع `requested_by_user_id` المستخرج من
  `SecurityContext` — وهذا هو الحقل المُعتمد لإثبات "مَن أصدر القرار"
  في تقارير NCA و SAMA.

### اختبار الدخان عبر curl

تأكد أوّلاً من تسجيل الدخول للحصول على `$JWT`:

```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@12345"}' \
  http://localhost:8084/api/v1/auth/login
```

ثم جرّب الحالة:

```bash
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/crowdstrike/status
```

استدعاء يدوي للاستعلام:

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/crowdstrike/poll
```

عزل مضيف (يتطلّب سبباً مُسجَّلاً للتدقيق):

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"reason":"اكتشاف فدية على خادم PCI"}' \
  http://localhost:8084/api/v1/integrations/crowdstrike/contain/<aid>
```

رفع العزل (CISO أو PLATFORM_ADMIN فقط):

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/crowdstrike/lift/<aid>
```
