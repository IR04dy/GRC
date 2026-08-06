# SDAIA breach-notification workflow (PDPL Article 31)
> **HISTORIC — point-in-time snapshot (all files 2026-05-24). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> Closes Open Issue **#006** — "PDPC notification API" in
> `docs/roadmap/04-open-issues-register.md`. Note: PDPC (Personal Data
> Protection Centre) was consolidated under SDAIA (Saudi Data & AI
> Authority) in the 2023 PDPL update. The notification recipient is now
> **SDAIA**, but the workflow described here is identical to what was
> previously documented for "PDPC".

## 1. Why this is a manual workflow

PDPL Article 31 and the Implementing Regulations Article 40 require
controllers to notify SDAIA of any personal-data breach that "may
prejudice personal data or violate the rights of data subjects" within
**72 hours** of becoming aware of the breach.

SDAIA does **not** publish a machine-readable API for breach
notification. Submissions are made manually through the SDAIA portal,
which means the TSS GRC platform cannot automate the submission itself —
but it can:

1. Track the 72-hour clock from breach discovery,
2. Carry the bilingual notification template that the DPO will paste
   into the SDAIA portal,
3. Record the outcome of the manual submission so the regulator-discharge
   action is auditable.

This document is the operations runbook for the DPO who actually performs
the submission.

## 2. Workflow on `privacy.data_breaches`

Three columns were added to the breach record (V181 migration):

| Column                       | Type            | Purpose |
|------------------------------|-----------------|---------|
| `sdaia_notification_status`  | VARCHAR(40)     | Workflow state machine — see §3 |
| `sdaia_submission_ref`       | NVARCHAR(255)   | Reference number returned by SDAIA portal acknowledgement |
| `sdaia_submitted_at`         | DATETIME2       | When the submission was posted — discharges the 72h clock |

A filtered index `IX_data_breaches_sdaia_pending` covers the CISO
dashboard query "what's still unreported past the 72h window?".

## 3. State machine

```
NOT_REPORTED  ──▶ IN_PREPARATION ──▶ SUBMITTED ──▶ ACKNOWLEDGED
       │              │                  │
       └──────────────┴──────────────────┘
              (any transition allowed except
               ACKNOWLEDGED is terminal)
```

- **NOT_REPORTED** — initial state. The breach has been discovered but
  no SDAIA submission has started yet.
- **IN_PREPARATION** — the DPO has begun assembling the notification
  package (impact assessment, affected-data inventory, mitigation
  measures). UI-only state — the 72-hour clock is still running.
- **SUBMITTED** — the DPO has posted the notification through the SDAIA
  portal. `sdaia_submitted_at` is stamped; the 72-hour clock is now
  discharged.
- **ACKNOWLEDGED** — SDAIA has returned a reference number / case ID,
  written into `sdaia_submission_ref`. Terminal state.

## 4. Submission channels

| Channel                       | URL / contact                                 | Notes |
|-------------------------------|-----------------------------------------------|-------|
| **Primary**                   | https://www.sdaia.gov.sa/ → "Data Protection" | Use the bilingual breach-notification form |
| **Backup (portal outage)**    | `dpo@sdaia.gov.sa` + cc the institution's SAMA / NCA contact | Attach a signed copy of the form |
| **Phone (acknowledgement chase)** | SDAIA general line — see the official site for current number | Only after 5 business days without acknowledgement |

> **Do not rely on the backup email channel for the 72-hour clock
> unless the portal is unreachable.** The portal is the regulator's
> preferred channel and produces an immediate acknowledgement reference.

## 5. Required notification content (PDPL Implementing Regulations Art. 40)

The notification must include:

1. **Nature of the breach** — categories of data subjects and categories
   of personal data affected, with approximate counts.
2. **Approximate number of records** — both total records and unique
   data subjects.
3. **DPO contact** — name, role, email, phone.
4. **Likely consequences** — assessment of the impact on data subjects
   (identity theft risk, financial loss risk, reputational risk).
5. **Measures taken or proposed** — containment, eradication, recovery,
   data-subject notification plan.

The bilingual `breach-alert.{en,ar}.html` template shipped via PR #176
(Issue #007) covers the data-subject notification side of this — the
DPO sends that template to affected individuals **after** the SDAIA
submission is acknowledged. Re-use its content blocks as the basis for
the SDAIA portal submission.

## 6. API surface (this platform)

```
GET  /api/v1/privacy/breaches/{id}/sdaia-submission
       → { sdaiaNotificationStatus, sdaiaSubmissionRef,
           sdaiaSubmittedAt, pdplArticle31DeadlineAt }

POST /api/v1/privacy/breaches/{id}/sdaia-submission
       Body: { status, submissionRef, submittedAt }
       Auth: DPO / CISO / PLATFORM_ADMIN
       → Updated DataBreach row
       Side effects: F7 audit "BREACH_REPORTED_TO_SDAIA",
                     F8 trigger "BREACH_REPORTED_TO_SDAIA"
```

Status values accepted: `NOT_REPORTED`, `IN_PREPARATION`, `SUBMITTED`
(default), `ACKNOWLEDGED`.

When `submittedAt` is null and the target status is `SUBMITTED`, the
service stamps the current UTC time — equivalent to the DPO clicking
"I have just submitted this through the SDAIA portal" in the UI.

`ACKNOWLEDGED` is terminal — once SDAIA returns a reference number,
the breach record can no longer move back to an earlier state through
this endpoint (only via the privacy admin path with appropriate
overrides).

## 7. F8 automation hooks

The `BREACH_REPORTED_TO_SDAIA` trigger fans out via the platform's
Automation Rule Engine. Suggested rules to wire up per tenant:

| Rule                               | Trigger                       | Action                              |
|------------------------------------|-------------------------------|-------------------------------------|
| Brief CISO + CEO                   | `BREACH_REPORTED_TO_SDAIA`    | Email + Slack to executive group    |
| Open lessons-learned task          | `BREACH_REPORTED_TO_SDAIA`    | Create CAPA task with 30-day SLA    |
| Cross-post to SAMA bridge          | `BREACH_REPORTED_TO_SDAIA` (financial-sector tenants) | Auto-create a SAMA cyber-incident notification draft |

## 8. Saudi-native compliance notes

- **Working hours and the 72-hour clock.** The 72 hours run on wall-clock
  time, not business hours. A Thursday-night breach must be reported by
  Sunday night even though the regulator's office is closed for the
  weekend. The portal accepts after-hours submissions and stamps them
  immediately.
- **SAMA cross-notification.** For financial-sector tenants, SAMA's
  cyber incident notification rules apply in parallel (typically 2
  hours for material incidents). Do not assume SDAIA submission
  discharges the SAMA notification — it does not.
- **NCA cross-notification.** For Critical National Infrastructure
  operators, the NCA HAWIYAH portal is a separate channel. Same
  principle: SDAIA discharge does not discharge NCA.
- **Translation.** The SDAIA portal supports Arabic and English. Saudi
  banks typically submit in Arabic; multinational subsidiaries
  typically submit in English with an Arabic executive summary. The
  bilingual template lets the DPO pick at submission time.

## 9. Operations playbook

1. **Breach discovered** — DPO opens the record on `/privacy/breaches/{id}`
   and confirms `pdpc_notification_required = true`.
2. **State → IN_PREPARATION** — DPO assembles the notification package
   using the §5 checklist.
3. **Submit to SDAIA** — DPO logs into the SDAIA portal, pastes the
   bilingual content, attaches supporting evidence.
4. **State → SUBMITTED** — DPO records the submission in TSS GRC:
   `POST /api/v1/privacy/breaches/{id}/sdaia-submission` with
   `status=SUBMITTED` and `submittedAt` set to the portal-displayed
   submission time. F8 trigger fires.
5. **State → ACKNOWLEDGED** — When SDAIA emails the reference number,
   DPO updates the record: `POST .../sdaia-submission` with
   `status=ACKNOWLEDGED` and `submissionRef=<reference number>`.
6. **Audit close-out** — Internal Audit traces the F7 audit row chain
   to confirm the 72-hour clock was discharged before the deadline.

## 10. Related artefacts

- `tss-grc-backend/src/main/java/com/tss/grc/module/privacy/entity/DataBreach.java`
  — entity carrying the SDAIA columns
- `tss-grc-backend/src/main/java/com/tss/grc/module/privacy/service/PrivacyService.java`
  — `recordSdaiaSubmission` + `getSdaiaStatus` methods
- `tss-grc-backend/src/main/resources/db/migration/sqlserver/V181__refresh_token_store_and_sdaia_breach.sql`
  — adds the three SDAIA columns + filtered index
- `tss-grc-backend/src/main/resources/templates/email/breach-alert.{en,ar}.html`
  — bilingual breach-notification template (shipped PR #176)
- `docs/roadmap/04-open-issues-register.md` — Open Issue #006 entry
