# SQL Server Express 10 GB cap monitor

Closes Open Issue **#004** — defines the threshold strategy, escalation path, and operator response when a TSS GRC database approaches the SQL Server Express 10 GB per-database limit.

| Regulatory mapping | Control |
|---|---|
| **NCA ECC-2** | Control 2-13 — Capacity management |
| **SAMA CSF** | 3.3.13 — Capacity & performance monitoring |
| **ISO 27001 A.12.1.3** | Capacity management |

---

## 1. Background

The default deployment uses **SQL Server 2019 Express** (free edition) to keep small-tenant TCO low. Express has a **hard 10 GB per-database storage cap**. Reaching it doesn't crash the server — but every INSERT/UPDATE thereafter fails with error `1105: Could not allocate space`. The platform effectively becomes read-only until an operator intervenes.

Realistic growth profile (measured from `seed_real_data.py` baseline + 6 months of audit-log accumulation in dev):

| Tenant size | Initial | After 6 months | After 18 months |
|---|---:|---:|---:|
| Light (single org, < 50 users) | ~0.4 GB | ~1.1 GB | ~2.4 GB |
| Medium (3 orgs, ~250 users) | ~1.0 GB | ~2.8 GB | ~6.0 GB |
| Heavy (5+ orgs, 500+ users, full SocOps) | ~1.5 GB | ~4.2 GB | **8.7 GB ⚠** |

For Heavy customers, we expect to cross the WARN threshold somewhere between months 12–18. The **upgrade path** is to SQL Server Standard Edition (paid, no cap) or to migrate to Azure SQL — operations is responsible for the actual move; this monitor's job is to give them enough lead time.

---

## 2. Threshold strategy

Three graduated thresholds, each emitting the same F8 trigger (`DB_SIZE_THRESHOLD_BREACHED`) with a `severity` payload field. Defaults (configurable via env vars):

| Severity | Default | % of 10 GB cap | Operator action |
|---|---:|---:|---|
| **WARN**  | 7 168 MB |  70 % | Email DBA; in-app banner for `PLATFORM_ADMIN` users; CAPA task created (informational). Plan the upgrade. |
| **ALERT** | 9 216 MB |  90 % | WARN actions + Slack `#grc-ops` page + Sentry breadcrumb. Begin executing the upgrade plan now — you have ~30 days. |
| **HALT**  | 9 728 MB |  95 % | ALERT actions + the platform sets the `READ_ONLY_MODE` flag — write endpoints respond with `HTTP 507 Insufficient Storage` and a bilingual message. |

### Why 95 % halt and not 100 %?

The 10 GB Express cap measures **data-file size**, but the engine can refuse allocation a little under the boundary depending on auto-grow chunks and free-page bitmap fragmentation. Empirically, write failures can begin as early as **9.95 GB**. A 95 % defensive halt gives operations a **~500 MB cushion** to:

- Drop legacy indexes
- Vacuum the audit log retention window (default 7 years — usually only the last 30 days are hot)
- Move a non-critical tenant to a separate database
- Take an emergency snapshot before the move

without write availability failing mid-operation.

### Why "halt writes" rather than "halt everything"?

GRC platforms are read-heavy. Read-only mode keeps dashboards usable for executives, lets the audit-log evidence remain accessible during the upgrade window, and limits the operator pressure to **clearing space**, not **restoring service from scratch**.

---

## 3. Configuration

In `application.yml` under the `grc.health.db-size` namespace:

```yaml
grc:
  health:
    db-size:
      warn-mb: ${GRC_DB_SIZE_WARN_MB:7168}        # 70% of 10 GB Express cap
      alert-mb: ${GRC_DB_SIZE_ALERT_MB:9216}       # 90%
      halt-mb: ${GRC_DB_SIZE_HALT_MB:9728}         # 95% — defensive cushion
      check-cron: ${GRC_DB_SIZE_CHECK_CRON:0 0 */6 * * *}   # every 6h on the hour
```

Overrides via env var only — these are operational tunables, not per-tenant. The constructor refuses to start the application with non-ascending thresholds (`warn < alert < halt` must hold).

### Customer with Standard/Enterprise edition (no cap)

Set all three thresholds to absurdly high values to effectively disable the monitor:

```bash
GRC_DB_SIZE_WARN_MB=1048576    # 1 TB
GRC_DB_SIZE_ALERT_MB=2097152
GRC_DB_SIZE_HALT_MB=3145728
```

A clean disable flag (`grc.health.db-size.enabled=false`) is a Sprint 13 backlog item — issue #004 deliberately ships with the conservative default so the new monitor doesn't silently disable itself.

---

## 4. F8 event payload

Trigger: `DB_SIZE_THRESHOLD_BREACHED`. `organisationId` is **null** — the cap is platform-wide, not per-tenant. Payload:

```json
{
  "severity":    "WARN" | "ALERT" | "HALT",
  "sizeMb":      8512,
  "thresholdMb": 7168,
  "capMb":       10240,
  "pctOfCap":    83.13,
  "warnMb":      7168,
  "alertMb":     9216,
  "haltMb":      9728
}
```

The full threshold config is repeated in the payload so AutomationRule authors can write rules like *"if severity = ALERT and pctOfCap > 92, page the on-call"* without re-reading app config.

### Recommended Automation Rules (V96 seed candidates)

| Rule name | Trigger | Condition | Actions |
|---|---|---|---|
| DB capacity — WARN | `DB_SIZE_THRESHOLD_BREACHED` | `severity == "WARN"` | Email `dba@`; in-app banner for `PLATFORM_ADMIN`; create CAPA task severity LOW |
| DB capacity — ALERT | `DB_SIZE_THRESHOLD_BREACHED` | `severity == "ALERT"` | Email `dba@` + `ops-lead@`; Slack `#grc-ops`; create CAPA task severity HIGH; Sentry breadcrumb |
| DB capacity — HALT | `DB_SIZE_THRESHOLD_BREACHED` | `severity == "HALT"` | All ALERT actions + set `platform.flags.read_only_mode=true`; SMS on-call |

These rules are not seeded by issue #004 (they're a customer-specific Operations follow-up).

---

## 5. HTTP 507 read-only response (HALT-band fallback)

When the `READ_ONLY_MODE` flag is on, write endpoints (POST/PUT/PATCH/DELETE except `/api/auth/logout`) return:

```json
HTTP/1.1 507 Insufficient Storage
Content-Type: application/json; charset=utf-8

{
  "error":     "DB_CAPACITY_EXCEEDED",
  "messageEn": "The platform is temporarily in read-only mode due to database capacity. Please contact your platform administrator.",
  "messageAr": "المنصة في وضع القراءة فقط مؤقتاً بسبب امتلاء سعة قاعدة البيانات. يُرجى التواصل مع مسؤول المنصة."
}
```

The Arabic wording uses **منصة** (platform, not system) and **سعة** (capacity, not size) — consistent with the rest of the AR translations. (The flag enforcement and the response handler are not part of issue #004; they're a follow-up that consumes the F8 event.)

---

## 6. Operator playbook

### When you receive a WARN

1. Acknowledge the CAPA task within 1 business day.
2. Run the size-by-table report (saved view "Platform — Top tables by row count" in the Admin module).
3. Identify the biggest growth contributors. Common culprits:
   - `platform.audit_log` (default retention 7 years — usually the #1)
   - `socops.security_event` (SIEM bridge — turn off the Splunk poll if not used)
   - `evidence.file` (large attachments — move to file-system storage)
4. Estimate time-to-ALERT at the current growth rate. Plan the upgrade.

### When you receive an ALERT

1. Acknowledge within 1 hour.
2. Page the DBA on-call.
3. Execute the runbook step "Archive audit log older than retention floor" (drops the table from ~3 GB to ~80 MB in most deployments).
4. If still > 90 % after the archive: begin the Standard Edition migration. ETA: 4–8 hours of downtime.

### When you receive a HALT

1. Write endpoints are now failing — users will notice within minutes.
2. **Communicate** — post the status-page incident, send the bilingual email template `database-capacity-halt.{en,ar}.html` (lives in `tss-grc-backend/src/main/resources/templates/email/`, follow-up to issue #007).
3. Execute the emergency drop list (see §7).
4. Once `sizeMb` drops below the HALT threshold, **manually** clear the `READ_ONLY_MODE` flag (it does **not** auto-clear — operations should confirm root-cause is addressed before re-enabling writes).

---

## 7. Emergency drop list (for HALT response)

In **strict order** — drop the cheapest, lowest-value rows first:

1. `DELETE FROM platform.audit_log WHERE created_at < DATEADD(year, -1, GETUTCDATE())` — audit log older than 1 year (retain 1 year hot, archive the rest to compressed CSV in S3/Blob)
2. `DELETE FROM socops.security_event WHERE received_at < DATEADD(month, -3, GETUTCDATE())` — SIEM event noise > 3 months
3. `DELETE FROM platform.calendar_event WHERE starts_at < DATEADD(year, -2, GETUTCDATE()) AND is_completed = 1` — closed calendar events > 2 years
4. `EXEC sp_clean_db_free_space @dbname = 'TSS_GRC'` — release internal fragmentation
5. `DBCC SHRINKFILE (TSS_GRC_data, 7000)` — force the data file to shrink

After each step, re-run the size query and stop as soon as `sizeMb < HALT - 200` (200 MB cushion above the HALT clear line).

---

## 8. Implementation references

- `tss-grc-backend/src/main/java/com/tss/grc/module/platform/health/DbSizeMonitorJob.java`
  - `@Scheduled(cron = "0 0 */6 * * *")` — runs every 6 hours
  - Emits F8 trigger `DB_SIZE_THRESHOLD_BREACHED`
  - Writes a `DB_SIZE_<SEVERITY>` audit row per emission
- `tss-grc-backend/src/test/java/com/tss/grc/module/platform/health/DbSizeMonitorJobTest.java`
  - Threshold classification, payload shape, audit-failure resilience
- `tss-grc-backend/src/main/resources/application.yml` — `grc.health.db-size.*`

---

## 9. Known follow-ups

| Item | Tracked in |
|---|---|
| `READ_ONLY_MODE` flag enforcement on write endpoints + HTTP 507 response | Sprint 13 backlog |
| Per-table size report saved view | Admin module backlog |
| Automated archive job for `platform.audit_log` to compressed cold storage | Sprint 14 backlog |
| `db-size.enabled=false` clean-disable flag for Standard/Enterprise customers | Sprint 13 backlog |
