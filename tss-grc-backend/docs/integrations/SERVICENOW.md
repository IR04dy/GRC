# ServiceNow ITSM Integration

First slice of Sprint 12 integrations work. Pushes GRC events (cyber
incidents, audit findings, CAPA tasks, compliance gaps) into ServiceNow
as `incident` records, and accepts state-change callbacks back into GRC
via a webhook.

Code lives under `com.tss.grc.module.integrations.servicenow`.

## Modes

Two implementations of `ServiceNowClient` exist; the active one is
chosen at startup by `grc.integrations.servicenow.mode`:

| Mode | Class | Behaviour |
|------|-------|-----------|
| `STUB` (default) | `StubServiceNowClient` | Records every call in memory. Generates synthetic `INC00100xx` numbers. Useful for dev/demo + unit tests. |
| `LIVE` | `LiveServiceNowClient` | Real HTTP against `{base-url}/api/now/table/incident`. Authenticates via OAuth client-credentials or HTTP Basic. Retries 5xx + connection errors. |

## Switching from STUB to LIVE

1. Set env vars:

   ```bash
   export SERVICENOW_CLIENT_ID="..."
   export SERVICENOW_CLIENT_SECRET="..."
   export SERVICENOW_TOKEN_URL="https://acme.service-now.com/oauth_token.do"
   # (or for HTTP Basic)
   export SERVICENOW_USERNAME="integration_user"
   export SERVICENOW_PASSWORD="..."
   ```

2. Edit the active profile (or override via env):

   ```yaml
   grc:
     integrations:
       servicenow:
         enabled: true
         mode: LIVE
         base-url: https://acme.service-now.com
         auth:
           type: OAUTH2_CLIENT_CREDENTIALS   # or BASIC
   ```

3. Hit the smoke endpoints (admin / CISO only):

   ```bash
   curl -H "Authorization: Bearer $JWT" http://localhost:8084/api/v1/integrations/servicenow/health
   curl -H "Authorization: Bearer $JWT" http://localhost:8084/api/v1/integrations/servicenow/config
   ```

## Field mapping (GRC → ServiceNow)

| GRC field            | ServiceNow column   | Notes                                      |
|----------------------|---------------------|--------------------------------------------|
| `shortDescription`   | `short_description` | Truncated to 160 chars                     |
| `description`        | `description`       | Long-form, no truncation                   |
| `category`           | `category`          | Defaults to `Security` if omitted          |
| `assignmentGroup`    | `assignment_group`  | Free text — must match a SNow group name   |
| `callerEmail`        | `caller_id`         | Resolved by SNow's user lookup             |
| (severity-mapped)    | `impact`            | See severity table below                   |
| (severity-mapped)    | `urgency`           | See severity table below                   |
| `grcSourceModule`    | `u_grc_module`      | Custom field for forensic correlation      |
| `grcSourceId`        | `u_grc_id`          | Custom field for forensic correlation      |
| `customFields[u_*]`  | (verbatim)          | Any `u_*` custom column                    |

## Severity → impact/urgency

ServiceNow priority is computed by SNow from `impact × urgency`. We set
both explicitly:

| GRC severity | impact | urgency |
|--------------|-------:|--------:|
| CRITICAL     |      1 |       1 |
| HIGH         |      2 |       2 |
| MEDIUM       |      3 |       2 |
| LOW          |      3 |       3 |

Unknown / null defaults to LOW (3, 3).

## Outbound — sample curl

The admin `test-push` endpoint exercises the full outbound pipeline. In
STUB mode it returns a synthetic `INC00100xx` number; in LIVE mode it
hits ServiceNow for real.

```bash
curl -X POST http://localhost:8084/api/v1/integrations/servicenow/test-push \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "grcSourceModule": "CYBER_INCIDENT",
    "grcSourceId": "CYB-2026-0042",
    "shortDescription": "Suspicious lateral movement on Exadata host",
    "description": "Detected SMB scanning from 10.40.2.18 against banking-core subnet.",
    "severity": "CRITICAL",
    "category": "Security",
    "assignmentGroup": "SOC Tier 2",
    "callerEmail": "soc@tss-bank.local",
    "customFields": { "u_business_unit": "Retail Banking" }
  }'
```

Inspect what the stub saw:

```bash
curl -H "Authorization: Bearer $JWT" \
  http://localhost:8084/api/v1/integrations/servicenow/stub/recorded-calls
```

## Inbound — webhook callback

ServiceNow's outbound REST step posts state changes to:

```
POST /api/v1/integrations/servicenow/webhook/incident-updated
```

Expected body (subset of fields used):

```json
{ "number": "INC0010001", "state": "Resolved" }
```

### Auth on the webhook

The platform does not yet have a dedicated machine-to-machine auth
scheme. Until one lands, the webhook is guarded by
`hasAnyRole('PLATFORM_ADMIN','SOC_AUTOMATION','INTEGRATION_SERVICE')`.
Provision a dedicated SNow integration service account on the GRC side,
grant it one of those roles, and have ServiceNow authenticate as that
user when calling the webhook.

A shared HMAC header (`X-ServiceNow-Signature`) is the planned upgrade —
tracked as a follow-up.

### What the webhook does

1. Locates the `platform.integration_external_link` row by `(integration='SERVICENOW', external_number=<INC number>)`.
2. Updates `last_known_state` and `last_pulled_at`.
3. Publishes an F8 `DomainEvent` with trigger `SNOW_INCIDENT_UPDATED` so
   automation rules can react — e.g. "when SNow state = Closed, close
   the GRC incident". This is intentionally rule-driven so the cross-
   module updates don't need cyber/audit/capa code changes here.

## Database

A single mapping table backs the integration — `platform.integration_external_link`
(V163). One row per `(org, integration, grc_module, grc_record_id)`.
This is generic: future Splunk / CrowdStrike / Tenable adapters will
reuse it with `integration='SPLUNK'` etc.
