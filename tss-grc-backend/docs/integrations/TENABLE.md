# Tenable.io / Tenable.sc VM Bridge

Sprint 12 / slice 4 of the integrations work. The adapter is **bidirectional**:

1. **Pull** — pulls vulnerability findings from Tenable.io's
   `/vulns/export` endpoint (or Tenable.sc's `/rest/analysis` for on-prem)
   and materialises each new finding as a `cyber.vulnerabilities` row plus
   an `integration_external_link` mapping (`integration='TENABLE'`).
2. **Push** — records GRC-side risk decisions back to Tenable as
   recasted-severity / risk-accepted entries (audit-trail bidirectional)
   via the Tenable workbench endpoints.

Code lives under `com.tss.grc.module.integrations.tenable`. Mirrors the
ServiceNow + Splunk + CrowdStrike adapter packaging — see `SERVICENOW.md`,
`SPLUNK.md`, and `CROWDSTRIKE.md` for the siblings.

## Why this exists

Tenable is the dominant VM platform across Saudi banks (TSS Saudi Bank,
SNB, STC Bank) and Aramco. Without the bridge:

- The VM team is the integration — they read Tenable, log a risk
  acceptance in a spreadsheet, then re-key the decision into GRC for
  SAMA / NCA evidence. That's slow and reconciliation-prone (the
  spreadsheet drifts from Tenable within weeks).
- Automation rules in GRC (F8) can't react to scan findings because the
  finding lives behind Tenable's UI.

With the bridge the VM team stays in GRC: findings appear in the
vulnerabilities register with NCA-aligned SLA windows on insert, and the
"recast / accept-risk" decision sits one click away with a full audit
trail in `tenable_recast_log`.

## Modes

Two implementations of `TenableClient`; chosen at startup by
`grc.integrations.tenable.mode`:

| Mode | Class | Behaviour |
|------|-------|-----------|
| `STUB` (default) | `StubTenableClient` | In-memory simulator. Deterministic plugin ids `TEN-FND-{seq}`. Severities cycle CRITICAL / HIGH / MEDIUM / LOW / INFO so each batch contains one of every NCA-ECC2 bucket. CVSS v3 scores aligned with the cycle. Records every call. Default in dev/demo + unit tests. |
| `LIVE` | `LiveTenableClient` | Real HTTPS against `{base-url}`. `X-ApiKeys` header carrying the access/secret pair. Retries 5xx + connection errors with exponential back-off. Two flavours: Tenable.io (`platform=IO`) ships in this slice; Tenable.sc (`platform=SC`) live wiring is a Sprint 13 follow-up — SC users should stay on STUB until then. |

## Tenable.io vs Tenable.sc — which to pick?

| Tenable.io (cloud) | Tenable.sc (on-prem) |
|--------------------|----------------------|
| Hosted by Tenable in US / EU regions. | Hosted by you on a hardened appliance inside the KSA DC. |
| Faster to provision, no DC footprint. | Higher TCO, but full data sovereignty. |
| Cross-border data flow per PDPL Art. 29 (see below). | Data stays in KSA. |
| `platform: IO`, `base-url: https://cloud.tenable.com`. | `platform: SC`, `base-url: https://tenable-sc.example.sa`. |

**Saudi banks regulated by SAMA / NCA generally pick Tenable.sc** because
Tenable.io has no KSA-region datacenter and the cross-border review
required for IO is administratively heavier than just running SC on-prem.
Aramco and the larger telco-banking deployments (STC Bank, stc pay) have
operated SC on-prem for ≥ 4 years.

## PDPL Art. 29 — data residency note

Tenable.io currently does **not** have a KSA-region datacenter. Customer
vulnerability data flows to either US or EU regions depending on the
tenant's signup region. Under the PDPL (specifically Art. 29 on
cross-border transfers) this is permitted only if one of the following
applies:

- The recipient country's data-protection regime has been approved by
  SDAIA (the EU is approved as of 2025; the US is **not** approved).
- An explicit cross-border transfer assessment has been documented for
  the deployment, with named risk owner and reviewing officer.
- Explicit data-subject consent is captured (not realistic for
  enterprise scan telemetry).

In practice this means:

- **EU region IO** is acceptable for KSA banks after a documented
  cross-border review (this is the recommended cloud cell if Tenable.io
  is required).
- **US region IO** generally cannot be approved by SDAIA without
  exceptional justification.
- **Tenable.sc on-prem inside KSA** sidesteps the question entirely.

Treat the choice as a deployment-time decision. The `platform: IO|SC`
property does not in itself enforce residency — it just selects the API
shape. The residency review belongs in the deployment record.

## Switching from STUB to LIVE

### 1. Create a Tenable API key pair

In the Tenable.io console:
**Settings → My Account → API Keys → Generate**

Required permissions on the user that owns the key:

| Permission                | Why                                                |
|---------------------------|----------------------------------------------------|
| `VULN.VIEW`               | Poll `/vulns/export` / read findings               |
| `SCAN.VIEW`               | Resolve asset / plugin context for findings        |
| `RECAST.MANAGE`           | Run recast and accept-risk endpoints               |

Save the **accessKey** + **secretKey** immediately — Tenable only shows
the secret once.

For Tenable.sc the equivalent is **Users → New API Key** under an
account with the `Vulnerability Manager` role plus the `Accept Risk` and
`Recast Risk` permissions enabled.

### 2. Pick a platform + base URL

```yaml
# Tenable.io (cloud)
platform: IO
base-url: "https://cloud.tenable.com"

# Tenable.sc (on-prem) — substitute your appliance hostname
platform: SC
base-url: "https://tenable-sc.yourbank.sa"
```

For SC appliances using a private CA, install the CA in the JVM
truststore of the GRC deployment (e.g. via `cacerts` import) so TLS
handshakes succeed.

### 3. Set env vars

```bash
export TENABLE_ACCESS_KEY="abc123def456..."
export TENABLE_SECRET_KEY="..."   # never commit
```

### 4. Flip the profile config

```yaml
grc:
  integrations:
    tenable:
      enabled: true
      mode: LIVE
      platform: IO                                  # or SC
      base-url: "https://cloud.tenable.com"
      polling-enabled: false                        # leave OFF until manual /poll validates
      polling-interval-seconds: 900
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
   `cyber.vulnerabilities` for the result.
3. Once confirmed, flip `polling-enabled: true` so the fixed-delay loop
   runs every `polling-interval-seconds`.

### 5. Confirm reachability

```bash
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/tenable/status
```

Expected body:

```json
{
  "enabled": true,
  "pollingEnabled": false,
  "mode": "LIVE",
  "platform": "IO",
  "baseUrl": "https://cloud.tenable.com",
  "healthy": true,
  "lastSuccessfulPollAt": null,
  "lastRecastAt": null
}
```

## Severity mapping

Tenable emits either a label (CRITICAL/HIGH/MEDIUM/LOW/INFO) or a CVSS
v3 base score 0.0..10.0. We honour the label first; on missing label we
fall back to CVSS:

| Tenable label / CVSS v3 range | GRC severity | NCA-ECC2 SLA |
|------------------------------|--------------|--------------|
| `CRITICAL` or CVSS v3 ≥ 9.0  | CRITICAL     | 1 hour       |
| `HIGH`     or CVSS v3 7.0..8.9 | HIGH         | 4 hours      |
| `MEDIUM`   or CVSS v3 4.0..6.9 | MEDIUM       | 24 hours     |
| `LOW`      or CVSS v3 0.1..3.9 | LOW          | 72 hours     |
| `INFO`     or CVSS v3 == 0.0  | INFO         | (no SLA)     |
| missing both                  | MEDIUM       | 24 hours     |

Mirrors Splunk's and CrowdStrike's "missing → MEDIUM" default so a
missing severity is never silently queued at LOW.

## Polling cadence — why 15 min?

We default to **`polling-interval-seconds: 900`** (15 minutes), not 60s
like CrowdStrike. Tenable findings are **aggregated scan results**, not
streaming events:

- A typical bank runs authenticated VM scans every 24 hours; ad-hoc
  scans push new findings two or three times a day at most.
- The Tenable.io `/vulns/export` API rate-limits at 1 export per minute
  per tenant — a 60s loop would hit that ceiling and immediately back
  off, wasting API budget without surfacing new data.
- 15-minute latency is well inside the SAMA / NCA SLA windows (1h for
  CRITICAL is the tightest).

If a bank's risk register demands faster pickup (e.g. a Sprint 13
follow-up: streaming Tenable Web App Scanning), revisit the cadence on
that slice. For traditional infrastructure VM scans, 15 min is the
right default.

## REST endpoints

| Method | Path                                                | Roles                                                                  |
|--------|-----------------------------------------------------|------------------------------------------------------------------------|
| GET    | `/api/v1/integrations/tenable/status`               | PLATFORM_ADMIN, CISO, RISK_MANAGER, SOC_ANALYST, SOC_TIER_2, SOC_AUTOMATION, INCIDENT_COMMANDER, BOARD_MEMBER |
| GET    | `/api/v1/integrations/tenable/config`               | PLATFORM_ADMIN, CISO                                                   |
| POST   | `/api/v1/integrations/tenable/poll`                 | PLATFORM_ADMIN, SOC_AUTOMATION                                         |
| POST   | `/api/v1/integrations/tenable/recast/{pluginId}`    | CISO, RISK_MANAGER, PLATFORM_ADMIN                                     |
| POST   | `/api/v1/integrations/tenable/accept-risk/{pluginId}`| CISO, RISK_MANAGER, PLATFORM_ADMIN, BOARD_MEMBER                       |
| POST   | `/api/v1/integrations/tenable/remove-recast/{pluginId}` | CISO, PLATFORM_ADMIN                                                |
| GET    | `/api/v1/integrations/tenable/stub/recorded-calls`  | PLATFORM_ADMIN, CISO                                                   |

The `accept-risk` endpoint includes `BOARD_MEMBER` because under SAMA
CSF residual-risk decisions sit with the board (or its delegated
committee). The `remove-recast` endpoint is narrower (CISO /
PLATFORM_ADMIN only) — the "revert to Tenable default severity"
decision should not be undone by anyone outside security leadership.

## Smoke-test curls

Set `$JWT` to a valid access token first
(e.g. `curl -X POST http://localhost:8084/api/v1/auth/login ...`).

```bash
# Status — checks kill-switch, mode, platform, last poll/action timestamps
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/tenable/status

# Manual poll — pulls findings from Tenable, writes cyber.vulnerabilities rows
curl -X POST -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/tenable/poll

# Recast severity — must include a justification of at least 30 chars
curl -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
        "newSeverity": "LOW",
        "justification": "Recasted because the affected asset sits in an isolated test VLAN — see CHANGE-2026-887",
        "assetUuid": "abc123-de45-67ab-cd89-1234567890ef"
      }' \
  http://localhost:8084/api/v1/integrations/tenable/recast/19506

# Accept risk — board-authorisable; expires_at is a hard requirement in
# most KSA banks (open-ended accepts get questioned in SAMA audits)
curl -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
        "justification": "Accepted by CISO until vendor patch ships in Q3 — tracked as RISK-2026-114",
        "expiresAt": "2026-09-30T23:59:59",
        "assetUuid": null
      }' \
  http://localhost:8084/api/v1/integrations/tenable/accept-risk/19506

# Remove recast — CISO / PLATFORM_ADMIN only
curl -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"assetUuid": "abc123-de45-67ab-cd89-1234567890ef"}' \
  http://localhost:8084/api/v1/integrations/tenable/remove-recast/19506

# STUB-only — inspect what the simulator has seen
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/tenable/stub/recorded-calls
```

## Audit + automation

Every mutation emits **F7 + F8**:

| Trigger                              | When fired                                          | Payload keys                                                                       |
|--------------------------------------|-----------------------------------------------------|------------------------------------------------------------------------------------|
| `TENABLE_FINDING_INGESTED`           | New finding → cyber.vulnerabilities row             | `severity`, `tenablePluginId`, `tenableAssetUuid`, `hostname`, `cveId`, `cvssV3Score` |
| `TENABLE_RECAST_APPLIED`             | `recastSeverity()` succeeded                        | `tenablePluginId`, `tenableAssetUuid`, `newSeverity`, `justification`, `tenableRuleId` |
| `TENABLE_RISK_ACCEPTED`              | `acceptRisk()` succeeded                            | (same shape + `expiresAt`)                                                         |
| `TENABLE_RECAST_REMOVED`             | `removeRecast()` succeeded                          | (same shape, no new severity)                                                      |

Wire the F8 trigger into Automation Rules to drive workflows like
"auto-open a SAMA reportable incident on `TENABLE_FINDING_INGESTED`
when severity=CRITICAL and the asset is in the PCI zone".

## Tables (V176)

- `platform.tenable_finding_log` — one row per Tenable finding observed.
  Unique constraint on `(organisation_id, tenable_asset_uuid,
  tenable_plugin_id)`.
- `platform.tenable_recast_log` — one row per recast / accept-risk /
  remove-recast attempt. Carries `requested_by_user_id` and
  `justification` for NCA-ECC2 traceability.

The generic `platform.integration_external_link` (V163) is reused with
`integration='TENABLE'`.

---

# واجهة Tenable VM — ملاحظات عربية

نظام **Tenable** هو منصة إدارة الثغرات (VM) السائدة في البنوك السعودية
(TSS، البنك الأهلي، STC Bank) وأرامكو. هذا المحوّل يوفّر:

1. **سحب**: استعلام دوري عن `‎/vulns/export‎` وتحويل كل ثغرة جديدة إلى
   صفّ في `cyber.vulnerabilities` مع ربط جدول `integration_external_link`
   (الجدول العام من V163).
2. **دفع**: تسجيل قرارات GRC في Tenable عبر إعادة تصنيف الشدّة
   (`recastSeverity‎`)، أو قبول الخطر (`acceptRisk‎`)، أو إلغاء
   إعادة التصنيف (`removeRecast‎`).

### نقاط مهمة للالتزام التنظيمي

- **اختيار Tenable.io vs Tenable.sc**: لا تتوفّر منطقة Tenable.io داخل
  المملكة العربية السعودية حتى الآن. البنوك الخاضعة لإشراف ساما
  والهيئة الوطنية للأمن السيبراني (NCA) تختار عادةً Tenable.sc
  المُستضاف داخل مركز البيانات المحلي للحفاظ على سيادة البيانات وفق
  المادة 29 من نظام حماية البيانات الشخصية (PDPL). يستخدم خيار
  `platform: IO` للسحابة، و `platform: SC` للنسخة المحلية.
- **PDPL المادة 29**: نقل البيانات إلى خارج المملكة عبر Tenable.io
  يتطلّب مراجعة موثّقة لتدفّق البيانات. منطقة الاتحاد الأوروبي مقبولة
  لدى هيئة سدايا اعتباراً من 2025؛ أمّا منطقة الولايات المتّحدة
  فتحتاج مبرّراً استثنائياً. الحلّ الأبسط: Tenable.sc محلياً.
- **مصفوفة SLA**: تتبع مصفوفة NCA-ECC2:
  - حرج (CRITICAL): ساعة واحدة
  - عالي (HIGH): 4 ساعات
  - متوسط (MEDIUM): 24 ساعة
  - منخفض (LOW): 72 ساعة
  - معلوماتي (INFO): لا يُحسب
- **مفتاحان منفصلان للتشغيل**: `enabled` للتفعيل العام و
  `polling-enabled` لحلقة الاستعلام الدورية. الفصل متعمّد — يبدأ
  المشغّل بالتفعيل الكلّي مع إيقاف الحلقة، يجرّب الاستعلام اليدوي مرة
  واحدة عبر `POST /poll`، ثم يفعّل الحلقة الدورية (الافتراضي 15 دقيقة
  لأن نتائج Tenable مُجمَّعة من فحوصات وليست أحداثاً مباشرة).
- **التتبع الكامل**: كل عملية إعادة تصنيف أو قبول خطر تُسجَّل في
  `tenable_recast_log` مع `requested_by_user_id` المستخرج من
  `SecurityContext` والمبرّر النصي (لا يقلّ عن 30 حرفاً) — وهذا
  المبرّر هو الدليل المُعتمد في تقارير ساما وNCA وISO 27001.

### اختبار الدخان عبر curl

تأكد أوّلاً من تسجيل الدخول للحصول على `$JWT`:

```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@12345"}' \
  http://localhost:8084/api/v1/auth/login
```

ثم تحقّق من الحالة:

```bash
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/tenable/status
```

استدعاء يدوي للاستعلام:

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/tenable/poll
```

إعادة تصنيف شدّة ثغرة (يتطلّب مبرّراً ≥ 30 حرفاً):

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
        "newSeverity": "LOW",
        "justification": "تمّ تخفيض التصنيف لأن الأصل في شبكة اختبار معزولة - CHANGE-2026-887",
        "assetUuid": "abc123-de45-67ab-cd89-1234567890ef"
      }' \
  http://localhost:8084/api/v1/integrations/tenable/recast/19506
```

قبول الخطر (CISO / RISK_MANAGER / PLATFORM_ADMIN / BOARD_MEMBER):

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
        "justification": "وافق CISO على القبول حتى نزول رقعة المورّد - RISK-2026-114",
        "expiresAt": "2026-09-30T23:59:59"
      }' \
  http://localhost:8084/api/v1/integrations/tenable/accept-risk/19506
```

إلغاء إعادة التصنيف (CISO / PLATFORM_ADMIN فقط):

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{}' \
  http://localhost:8084/api/v1/integrations/tenable/remove-recast/19506
```
