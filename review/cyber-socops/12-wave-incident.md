# P3 / W2 — Incident Response wave
### cyber 9-step incident lifecycle · socops event triage · IR playbook clone→invoke

Live tests on `[REVIEW-TEST]` data (all cleaned; counts verified back to baseline). Receipts:
`scratchpad/rev/w2_receipts.txt`. Grades: REAL / PARTIAL / FAÇADE / MISSING.

---

## C1 — Cyber incident 9-step lifecycle → **PARTIAL**

**Designed (P0 §C1):** DETECTION→…→LESSONS_LEARNED with per-severity SLA, **regulatory notification
timers (NCA 4h / SAMA / PDPL 72h)**, evidence attach, cross-dept tasks, closure approval, auto-CAPA.

**Live receipts:**
- Workflow = hardcoded linear list: `DETECTION, LOGGING, TRIAGE, CONTAINMENT, INVESTIGATION, ERADICATION, RECOVERY, CLOSURE, LESSONS_LEARNED`.
- `POST /cyber/incidents` → status `DETECTION`. `start-response` ×5 advanced `LOGGING→TRIAGE→CONTAINMENT→INVESTIGATION→ERADICATION` — pure **index+1** bump. Broadcasts realtime (F8) ✓, audits ✓.
- The only notification-related field is a `notificationSent` **boolean**. **No regulatory-timer columns** (no `nca_notify_due`, no 4h/72h deadline, no countdown, no escalation). The Saudi-market-critical regulator clock is absent.
- **No auto-CAPA:** action-plans created from this incident = **0**. Reaching LESSONS_LEARNED does not raise a corrective action; `lessons_learned` is a free-text field.
- `bcm_handoff` boolean exists but nothing computes/enforces a BCM handoff.

**Verdict: PARTIAL.** Real state machine + realtime + audit, but no clock, no regulatory timers, no
per-step tasks/evidence gating, no auto-CAPA. Misses the Saudi bar (regulator timers) and OS-2.

---

## C2 — IR playbook clone → invoke → **FAÇADE (invoke is a counter)**

**Designed:** invoke a playbook → its ordered steps become **assigned, clocked tasks** the SOC executes.

**Live receipts:**
- `cloneFromTemplate` is real — copies the template header + all steps into the tenant, sets `ACTIVE`, audits. Steps carry `responsibleRole`, `estimatedMinutes`, `isCritical` — ready to become tasks.
- `POST /playbooks/{id}/invoke` → `invocationCount 0 → 1`, fires `IR_PLAYBOOK_INVOKED` event, audits `"Invocation count incremented"`. **Re-reading steps after invoke: unchanged; no work items created.**
- **There is no playbook run / task / instance table** in `socops` (only `scanner_run`). `invoke` has nowhere to persist executable work — it cannot spawn tasks.

**Verdict: FAÇADE.** Invoke increments a counter and emits an event; the playbook never becomes
actionable work. Textbook "display without a real backend process."
*(Data-safety: the clone returned a pre-existing tenant playbook id=12 from 2026-05-23 — not mine; I
did **not** delete it and restored its `invocation_count` back to 0.)*

---

## C3 — SOC security-event triage → resolve → **PARTIAL (broken promotion; but has a clock)**

**Designed:** SIEM event → triage (TP/FP) → **promote true-positive to an incident** → resolve.

**Live receipts:**
- `POST /siem-events` (correct `SiemEventIngestRequest`) → 200, event `status=NEW`, **`slaDueAt` set to
  eventTime+1h** — a real triage **SLA clock** (better than cyber incidents, which have none).
- `POST /events/{id}/triage` → `status=TRIAGED` (+ optional assignee), audits, broadcasts. `incidentId` stays **null**.
- `POST /events/{id}/resolve` → `status=RESOLVED` (or FALSE_POSITIVE), audits, broadcasts. `incidentId` still **null**.
- `cyber_incidents` count unchanged → **triage/resolve never creates or links an incident.** The
  `security_event.incident_id` FK column exists but **no code path ever sets it**.

**Verdict: PARTIAL / severed integration.** The event lifecycle works and has an SLA clock, but the
SOC spine — escalating a true-positive alert into a managed cyber incident — is missing. SIEM events
and cyber incidents are two islands with a dead FK between them.

---

## ARCH-1 — Two parallel incident systems → **confirmed (High, integration/data)**

| Table | Rows | Schema | Used by |
|---|---|---|---|
| `cyber.cyber_incidents` | 24 | rich, bilingual (`title_en/title_ar`), `workflow_status` 9-step, `notification_sent`, `bcm_handoff`, `lessons_learned` | `/api/v1/cyber/incidents` (the workspace) |
| `cyber.incidents` | 20 | simpler, **English-only** (`title`, `severity`, `category`, `status`, `assignee`, `discovery_date`, `resolution_date`, `description`) | not exposed by the cyber controller — legacy/orphan |

Two different incident data models in the same schema, 44 rows split across them. `cyber.incidents`
(20 rows) is not served by the cyber API — a legacy island or a competing model. Continuity risk +
data confusion. (Cross-check in W6 whether any other module reads `cyber.incidents`.)

---

## What works (credit where due)
- Realtime broadcast (F8/`BroadcastHelper`) fires on incident advance, event triage/resolve, playbook invoke.
- F7 audit logging present on all these transitions.
- SOC events have a real SLA clock (`sla_due_at`), and `SocOpsScheduledSweep` exists (scheduler proof deferred to W5).
- Tenant scoping via `findByIdAndOrganisationId` on incident reads (SEC-CYB-001).

## W2 tallies
- **FAÇADE: 1** (C2 playbook invoke) · **PARTIAL: 2** (C1 incident, C3 event) · **High finding: 1** (ARCH-1 duplicate incident tables).
- Recurring theme continues: **the clock and cross-object routing are the systemic gap** — present in
  vuln-SLA and SOC-event SLA, absent from incidents, CAPA, evidence, playbook execution.
- Running totals (W1+W2): **FAÇADE 2** (evidence, playbook-invoke) · **MISSING 1** (policy in cyber) ·
  **PARTIAL 5** · **High findings 3** (RBAC×2, ARCH-1) · **Medium 4**.
