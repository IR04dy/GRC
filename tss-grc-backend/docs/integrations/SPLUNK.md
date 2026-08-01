# Splunk SIEM Bridge Integration

Sprint 12 / slice 2 of the integrations work. Pushes GRC events (SOC
`security_event`, findings, risks, CAPA tasks) into Splunk Enterprise
Security as HTTP Event Collector (HEC) events, and accepts Splunk
saved-search alert webhooks back into GRC as `socops.security_event`
rows.

Code lives under `com.tss.grc.module.integrations.splunk`. Mirrors
the ServiceNow adapter's package layout — see `SERVICENOW.md` for the
sibling.

## Why this exists

Saudi-regulated banks (TSS, STC Bank, Aramco) run Splunk Enterprise
Security as their authoritative SIEM. The GRC platform is the system
of record for NCA ECC-2 evidence and SLA tracking, but the SOC analyst
lives in Splunk. This bridge gives them a single console:

- GRC-side workflow events (SOC triage, finding closure, CAPA progress)
  appear in Splunk dashboards next to native log data.
- Splunk saved-search alerts auto-create GRC `security_event` rows with
  NCA ECC-2 SLA timers already set on insert.

## Modes

Two implementations of `SplunkClient` exist; the active one is chosen
at startup by `grc.integrations.splunk.mode`:

| Mode | Class | Behaviour |
|------|-------|-----------|
| `STUB` (default) | `StubSplunkClient` | Records every call in memory. Generates synthetic `SPLK-EVT-{org}-{seq}` event ids. Useful for dev/demo + unit tests. |
| `LIVE` | `LiveSplunkClient` | Real HTTP against `{hec-url}`. Authenticates via per-token `Authorization: Splunk <token>` header. Retries 5xx + connection errors with exponential back-off. |

## Switching from STUB to LIVE

1. Provision an HEC token in Splunk Web:
   `Settings → Data inputs → HTTP Event Collector → New Token`
   - Source type: `grc:event:json`
   - Index: `tss_grc` (or per-tenant index)
   - Disable indexer acknowledgement (or expect `ackId` in the response)

2. Set env vars:

   ```bash
   export SPLUNK_HEC_TOKEN="00112233-4455-6677-8899-aabbccddeeff"
   # Optional — verifies inbound webhook signatures
   export SPLUNK_WEBHOOK_SECRET="$(openssl rand -hex 32)"
   ```

3. Flip the profile config:

   ```yaml
   grc:
     integrations:
       splunk:
         enabled: true
         mode: LIVE
         hec-url: https://splunk.acme.sa:8088/services/collector/event
         default-index: tss_grc
         default-sourcetype: grc:event:json
   ```

4. Hit the status endpoint to confirm reachability:

   ```bash
   curl -H "Authorization: Bearer $JWT" \
     http://localhost:8084/api/v1/integrations/splunk/status
   ```

## Field mapping (GRC → Splunk HEC)

The HEC body envelope follows Splunk's "event" mode (not "raw"):

| GRC field            | HEC location                    | Notes                                      |
|----------------------|----------------------------------|--------------------------------------------|
| `eventTimeEpochSec`  | top-level `time`                 | Epoch seconds; defaults to "now"           |
| `host`               | top-level `host`                 | Originating asset hostname                 |
| `source`             | top-level `source`               | Defaults to `tss-grc-platform`             |
| `sourcetype`         | top-level `sourcetype`           | Defaults to `grc:event:json`               |
| `index`              | top-level `index`                | Defaults to `tss_grc`                      |
| `sourceModule`       | `event.grc_source_module`        | Forensic correlation back to GRC           |
| `sourceId`           | `event.grc_source_id`            | Forensic correlation back to GRC           |
| `event` (map)        | `event.*`                        | Free-form key/value payload                |

## Severity mapping (Splunk → NCA ECC-2)

When a Splunk alert webhook arrives, we normalise its severity into the
NCA ECC-2 SLA buckets used by `socops.security_event`. Splunk emits
severity as either a string or a numeric (1..6); we accept both:

| Splunk severity (string)              | Splunk severity (numeric) | NCA ECC-2 bucket | SLA window |
|---------------------------------------|---------------------------|-------------------|-----------:|
| `critical`, `severe`, `fatal`         | 5, 6                      | `CRITICAL`        | 1 hour     |
| `high`, `elevated`                    | 4                         | `HIGH`            | 4 hours    |
| `medium`, `moderate`, `warning`       | 3                         | `MEDIUM`          | 24 hours   |
| `low`, `info`, `informational`        | 1, 2                      | `LOW`             | 72 hours   |

Unknown / missing severity defaults to `MEDIUM` (rather than `LOW`) so
the SOC analyst sees the row in the active queue and re-ranks during
triage — preferable to a silent 72h SLA on something that may actually
be urgent.

## Who may call which endpoint

ADR-017 D-8 / **V343**: the admin surface authorises on the `INTEGRATIONS`
module, not on role names.

| Method | Path                    | Authorised by                                                      |
|--------|-------------------------|--------------------------------------------------------------------|
| GET    | `/splunk/status`        | **`INTEGRATIONS:READ`** *(was PLATFORM_ADMIN, CISO, SOC_ANALYST, SOC_AUTOMATION — the last two do not exist in `iam.roles`)* |
| GET    | `/splunk/config`        | **`INTEGRATIONS:READ`** — HEC token is masked                       |
| POST   | `/splunk/test-push`     | **`INTEGRATIONS:WRITE`** *(was `hasRole('PLATFORM_ADMIN')` — the persona that owns the integration could not smoke-test it)* |
| POST   | `/splunk/security-event/{id}` | **`INTEGRATIONS:WRITE`** — egress of platform data to a third party |
| GET    | `/splunk/stub/recorded-calls` | **`INTEGRATIONS:WRITE`** — returns frozen request payloads     |
| POST   | `/splunk/webhook`       | **`CYBER:WRITE`** — ingest; `SIEM_INGEST` holds it (F2-05, see below) |

`INTEGRATIONS:READ` → `ORG_ADMIN`, `GRC_ADMINISTRATOR`, `CISO`, `CYBER_MANAGER`.
`INTEGRATIONS:WRITE` → `ORG_ADMIN`, `CISO`. Both are rows in
`iam.role_permissions`, so re-granting them is a role-editor change rather than
a release. Note that `security-event/{id}` is **egress** and therefore sits on
`INTEGRATIONS:WRITE` while `/webhook` is **ingest** and stays on `CYBER:WRITE` —
the direction of travel, not the transport, decides the tier.

## Outbound — sample curl

The `test-push` endpoint exercises the full outbound pipeline. In STUB
mode it returns a synthetic `SPLK-EVT-1-1` event id; in LIVE mode it
hits Splunk for real.

```bash
curl -X POST http://localhost:8084/api/v1/integrations/splunk/test-push \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceModule": "SECURITY_EVENT",
    "sourceId": "SIEM-2026-00042",
    "host": "edge-vpn-01",
    "source": "tss-grc-platform",
    "sourcetype": "grc:event:json",
    "index": "tss_grc",
    "eventTimeEpochSec": 1716440040,
    "event": {
      "severity": "HIGH",
      "rule_name": "Edge VPN brute-force",
      "source_ip": "10.40.2.18",
      "destination_host": "banking-core-edge",
      "username": "svc.edge-vpn",
      "asset": "VPN-EDGE-01"
    }
  }'
```

To push a pre-existing SOC event:

```bash
curl -X POST -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/splunk/security-event/42
```

Inspect what the stub recorded (STUB mode only):

```bash
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/splunk/stub/recorded-calls
```

## Inbound — alert webhook

Configure Splunk's "Webhook" alert action to POST to:

```
POST /api/v1/integrations/splunk/webhook
```

Expected payload (Splunk's native shape — fields we read):

```json
{
  "search_name": "TSS - Edge VPN brute-force",
  "result_id": "scheduler__admin__search__..._at_1716440040_3",
  "result": {
    "host": "edge-vpn-01",
    "severity": "high",
    "signature": "Repeated 401 on edge VPN",
    "_time": "2026-05-23T08:14:00.000+03:00"
  }
}
```

The webhook:

1. Verifies the optional `X-Splunk-Signature` HMAC header (when
   `webhook-shared-secret` is configured).
2. Maps Splunk severity → NCA ECC-2 bucket (table above).
3. Composes a deduplicating `externalId` from `result_id` (or
   `search_name + _time` as fallback).
4. Inserts a row in `socops.security_event` with `source_system='SPLUNK'`
   and `status='NEW'`. SLA timer set on insert.
5. Writes an `integration_external_link` row with
   `integration='SPLUNK'`, `grc_module='SECURITY_EVENT'`.
6. Emits an F8 `SPLUNK_ALERT_RECEIVED` domain event so automation rules
   can react (auto-assign, auto-create incident, escalate).

### Auth on the webhook

Two layers — both must pass:

1. **Permission guard.** The endpoint is annotated
   `@PreAuthorize("hasPermission('CYBER','WRITE')")`.
   Splunk's webhook action must authenticate as a GRC service account whose
   role holds `CYBER:WRITE` — **`SIEM_INGEST` is the seeded machine role for
   exactly this feed** and already holds it. Same model as ServiceNow.

   > **Corrected 2026-07-30 (F2-05 / F2-52).** This previously read
   > `hasAnyRole('PLATFORM_ADMIN','SOC_AUTOMATION','INTEGRATION_SERVICE')` and
   > told you to grant one of those roles. **Neither `SOC_AUTOMATION` nor
   > `INTEGRATION_SERVICE` exists in `iam.roles`** — so the only grantable name
   > was `PLATFORM_ADMIN`, and following this page meant handing your SIEM a
   > platform-administrator token. Meanwhile `SIEM_INGEST` — provisioned by the
   > platform for SIEM ingestion — was refused by this endpoint.
2. **Optional HMAC.** When `grc.integrations.splunk.webhook-shared-secret`
   is non-empty, the request must carry
   `X-Splunk-Signature: <hex-HMAC-SHA256-of-raw-body>`. Otherwise the
   header is ignored. Use this in production; skip in dev STUB mode.

### Inbound smoke-test curl

```bash
curl -X POST http://localhost:8084/api/v1/integrations/splunk/webhook \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "search_name": "TSS - Edge VPN brute-force",
    "result_id": "rid-smoke-001",
    "result": {
      "host": "edge-vpn-01",
      "severity": "high",
      "signature": "Repeated 401 on edge VPN",
      "_time": "2026-05-23T08:14:00.000+03:00"
    }
  }'
```

Response on success:

```json
{
  "received": true,
  "duplicate": false,
  "securityEventId": 101,
  "severity": "HIGH",
  "externalId": "rid-smoke-001",
  "at": "2026-05-23T08:14:02"
}
```

Replay the same `result_id` to see `duplicate=true`.

## Database

Two tables back this integration:

- `platform.integration_external_link` (V163) — generic mapping row
  shared with ServiceNow. Splunk rows set `integration='SPLUNK'`.
- `platform.splunk_hec_outbound_log` (V165) — one row per HEC POST
  attempt, with `http_status`, `response_time_ms`, `attempt_count`,
  `error_message`. Indexed by `(organisation_id, source_module, source_id)`
  for per-record audit timelines and by `(organisation_id, http_status)`
  for failure aggregation.

## F7 + F8 events emitted

| Event                     | When                                | Payload keys                                                      |
|---------------------------|-------------------------------------|-------------------------------------------------------------------|
| `SPLUNK_EVENT_PUSHED`     | Successful outbound HEC push        | `splunkEventId`, `hecIndex`, `sourceModule`, `sourceId`, `attemptCount` |
| `SPLUNK_ALERT_RECEIVED`   | Inbound webhook → new SecurityEvent | `severity`, `searchName`, `host`, `externalId`                    |

---

## دليل الإعداد (عربي)

جسر تكامل سبلانك يخدم بنوك تطبق ضوابط الهيئة الوطنية للأمن السيبراني
ECC-2. الجسر يدفع أحداث منصة GRC إلى Splunk عبر HEC، ويستقبل تنبيهات
Splunk المحفوظة كصفوف security_event على جانب GRC.

### تفعيل الجسر

1. أنشئ HEC Token في Splunk Web ضمن
   `الإعدادات → مدخلات البيانات → HTTP Event Collector`.
2. عيّن متغير البيئة `SPLUNK_HEC_TOKEN`.
3. حدّث الإعدادات في الملف النشط:

   ```yaml
   grc:
     integrations:
       splunk:
         enabled: true
         mode: LIVE
         hec-url: https://splunk.acme.sa:8088/services/collector/event
   ```

### فترات SLA حسب ECC-2

| الخطورة     | المهلة الزمنية |
|-------------|----------------|
| حرجة        | ساعة واحدة     |
| عالية       | 4 ساعات        |
| متوسطة      | 24 ساعة        |
| منخفضة      | 72 ساعة        |

عند ورود تنبيه من Splunk، يتم تطبيق فترة SLA تلقائيًا اعتمادًا على
خطورة الحدث المُستلَمة.
