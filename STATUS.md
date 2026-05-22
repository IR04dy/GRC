# TSS GRC Platform — Version 2 Status
**Last update:** 22 May 2026 (PRs #11-87 — F7/F8 parity arc + domain dashboard polish sweep)
**Snapshot Date:** 22 April 2026 (initial), updated continuously
**Branch Source:** `claude/quirky-borg` (commit `726cd1e`)
**Stack:** Spring Boot 3.5 / Java 17 / SQL Server 2019 / React 18 + Vite / Bootstrap 5 RTL / Chart.js 4

---

## 🟢 Latest — Domain dashboard polish sweep (22 May 2026, PRs #86-87)

User-visible cleanup of all 5 domain dashboards.

### PR #86 — `/cyber/dashboard` real API + loading/error states
- Backend: extended `CyberDashboardDto` with `overallScore`, `eccScore`, `csccScore`, `cccScore`, `otccScore`, `expiringEvidence60d`; `CyberService.getDashboard()` computes per-framework scores via case-insensitive prefix match on `frameworkName` ("ECC v2", "ECC-2" both count toward ECC)
- Frontend: replaced hardcoded `FALLBACK` constant with real `cyberApi.dashboard()` call, four-state lifecycle (idle/loading/error/ready), Skeleton during load + retry button on error
- CSS: extracted 6 inline `style={{...}}` objects to `shell.css` (`.cyber-dashboard__frameworks`, `.framework-row`, `.framework-row__bar`, `.framework-row__fill[data-tone=…]`)

### PR #87 — BCM/ERM/PDPL/SCM dashboards apply the same pattern
Shared infrastructure:
- New hook `useDashboardLoader<T>(loader)` in `lib/useDashboardLoader.ts` — three states (loading / ready / error) with typed `{ data, state, errMsg, retry }` return
- New render-only components `<DashboardLoading />` + `<DashboardError />` in `components/dashboard/DashboardLoadingState.tsx`
- New CSS classes `.dashboard-skeleton` + `.dashboard-error` in `shell.css`

Refactored 4 dashboards to use the shared infra:
- `/bcm/dashboard` — drops FALLBACK_STATS, calls `bcmApi.stats()` via hook
- `/risk/dashboard` (ERM) — drops FALLBACK, calls `dashboardApi.stats()` via hook
- `/privacy/dashboard` (PDPL) — drops FALLBACK, calls `privacyApi.stats()` via hook
- `/scm/dashboard` — drops FALLBACK, calls `scmApi.stats()` via hook

All 5 domain dashboards now share the same loading/error vocabulary. Two real bugs fixed:
1. First-paint showed fake numbers (1.24M consents, 12 critical suppliers, etc.) before the real API call resolved
2. Silent fallback on backend failure — toast was the only signal; the user kept seeing FALLBACK numbers as if they were real

Now the user always sees Skeleton → real data → or Error+Retry.

### Design audit findings
A focused review of the highest-traffic pages (Dashboard, Cyber, Privacy, Compliance, Incidents, Findings, CAPA) found:
- HIGH severity reported by the audit (missing loading states on Cyber + Compliance) was a **false positive** — both pages already have `.loading ? <LoadingRow />` checks at the data-table level
- MEDIUM: several inline `style={{...}}` literals could be extracted to CSS classes (~25 instances across 4 pages)
- LOW: a few hardcoded English strings in cell content (Privacy "SMS/Profiling/3rd-party" labels; Incidents "Lessons Learned" subtitle; Compliance status enum `.replace(/_/g, ' ')`)

These are tracked as follow-up polish — none are user-facing bugs.

---

---

## 🟢 Most recent — F7/F8 parity arc → 18 services (22 May 2026, PRs #74-85)

Twelve follow-up PRs extended the audit-log + automation-rule emission contract from 6 services (closed at AR-50/V132) to **18 services (closed at AR-110/V148, 110 active seed rules)**. The arc now covers the full GRC workflow backbone: workflow submissions, CAPA, findings, AAR, risk, incidents, vulnerability response, approvals, policy lifecycle, evidence, consent (PDPL), DSR (PDPL Article 4), DPIA (PDPL Article 25), cross-border transfer (PDPL Article 29), change management (CAB/emergency), treatment plans, and regulatory change tracking.

| PR | Service | Trigger families | Seed rules |
|---|---|---|---|
| **#74** | IncidentService | 4 INCIDENT_* | AR-56..AR-60 (V138) |
| **#75** | VulnerabilityService | 3 VULN_* | AR-61..AR-65 (V139) |
| **#76** | ApprovalRequestService | 5 APPROVAL_* | AR-66..AR-70 (V140) |
| **#77** | PolicyService | 11 POLICY_* + EXCEPTION_* + ATTESTATION_* | AR-71..AR-75 (V141) |
| **#79** | EvidenceItemService | 6 EVIDENCE_* | AR-76..AR-80 (V142) |
| **#80** | ConsentRecordService | 3 CONSENT_* (PDPL Art. 6) | AR-81..AR-85 (V143) |
| **#81** | DsrCaseService | 8 DSR_* (PDPL Art. 4 — 30-day SLA) | AR-86..AR-90 (V144) |
| **#82** | DpiaService + CrossBorderTransferService | 5 DPIA_* + 5 TRANSFER_* (PDPL Arts. 25 + 29) | AR-91..AR-95 (V145) |
| **#83** | ChangeManagementService | 9 CHANGE_* (ITIL CAB + emergency bypass) | AR-96..AR-100 (V146) — **100-rule milestone** |
| **#84** | TreatmentPlanService | 8 TREATMENT_* (board-commitment trail) | AR-101..AR-105 (V147) |
| **#85** | RegulatoryChangeService | 8 REG_CHANGE_* (compliance intake pipeline) | AR-106..AR-110 (V148) |

### Full parity arc — 18 services, 110 active seed rules

| # | Service | Key events | Seed rules |
|---|---|---|---|
| 1 | WorkflowSubmissionService | SUBMISSION_* | AR-26..AR-30 |
| 2 | CapaTaskService | CAPA_TASK_* | AR-31..AR-35 |
| 3 | compliance CapaWorkflow | FINDING_CAPA_STAGE_CHANGED | AR-36..AR-40 |
| 4 | FindingService (audit_lc) | FINDING_* | AR-41..AR-45 |
| 5 | CrisisAarService | AAR_* | AR-46..AR-50 |
| 6 | RiskService | RISK_* | (re-used range) |
| 7 | IncidentService | INCIDENT_* | AR-56..AR-60 |
| 8 | VulnerabilityService | VULN_* | AR-61..AR-65 |
| 9 | ApprovalRequestService | APPROVAL_* | AR-66..AR-70 |
| 10 | PolicyService | POLICY_* | AR-71..AR-75 |
| 11 | EvidenceItemService | EVIDENCE_* | AR-76..AR-80 |
| 12 | ConsentRecordService | CONSENT_* | AR-81..AR-85 |
| 13 | DsrCaseService | DSR_* | AR-86..AR-90 |
| 14 | DpiaService | DPIA_* | (V145 shared) |
| 15 | CrossBorderTransferService | TRANSFER_* | AR-91..AR-95 |
| 16 | ChangeManagementService | CHANGE_* | AR-96..AR-100 |
| 17 | TreatmentPlanService | TREATMENT_* | AR-101..AR-105 |
| 18 | RegulatoryChangeService | REG_CHANGE_* | AR-106..AR-110 |

### Regulatory anchor events

The arc now ships canonical triggers for every Saudi regulatory keystone:

- **PDPL Article 4** — `DSR_RECEIVED`, `DSR_EXTENDED`, `DSR_REJECTED`, `DSR_DELIVERED` (30-day SLA, regulator spot-checks)
- **PDPL Article 6** — `CONSENT_WITHDRAWN` (unconditional withdrawal right, DPO routing)
- **PDPL Article 25** — `DPIA_APPROVED` with `overallResidualRisk=HIGH` (prior-consultation trigger)
- **PDPL Article 29** — `TRANSFER_APPROVED`, `TRANSFER_REJECTED`, `TRANSFER_EXPIRED` (cross-border ledger)
- **NCA ECC §3.4 / §3.4.6** — `VULN_TRANSITIONED` with `status=RISK_ACCEPTED` (compensating controls + GC + CRO)
- **NCA ECC C5.4 / SP 800-61r2** — `INCIDENT_CREATED` with `severity=CRITICAL` (executive escalation)
- **ITIL v4 Change Enablement** — `CHANGE_EMERGENCY_APPROVED`, `CHANGE_FAILED` (CTO/CISO + incident management)

---

## 🟢 Earlier — F7/F8 parity arc → 12 services (PRs #74-80)

Six follow-up PRs extended the audit-log + automation-rule emission contract from 6 services (closed at AR-50/V132) to 12 services (closed at AR-85/V143). Each PR is self-contained: service wires `AuditLogService` (where not already wired) + `ApplicationEventPublisher`, emits canonical `DomainEvent`s on every mutation, ships a Flyway V-file with 5 bilingual EN+AR seed rules consuming those events, and extends the existing test class with emission-contract assertions.

| PR | Service | Events | Seed rules | Tests |
|---|---|---|---|---|
| **#74** | `IncidentService` | `INCIDENT_CREATED`, `INCIDENT_PHASE_CHANGED`, `INCIDENT_CLOSED`, `INCIDENT_REGULATOR_NOTIFIED` | AR-56..AR-60 (V138) | 12 → 16 |
| **#75** | `VulnerabilityService` (cyber_lc) | `VULN_CREATED`, `VULN_TRANSITIONED`, `VULN_CLOSED` | AR-61..AR-65 (V139) | 7 → 11 |
| **#76** | `ApprovalRequestService` | `APPROVAL_CREATED`, `APPROVAL_DECIDED`, `APPROVAL_DELEGATED`, `APPROVAL_EXPIRED`, `APPROVAL_TRANSITIONED` | AR-66..AR-70 (V140) | 6 → 10 |
| **#77** | `PolicyService` (module.policy) | `POLICY_PUBLISHED` (anchor) + 10 more across documents/exceptions/attestations | AR-71..AR-75 (V141) | 11 → 15 |
| **#79** | `EvidenceItemService` | `EVIDENCE_CREATED`, `EVIDENCE_SUBMITTED`, `EVIDENCE_APPROVED`, `EVIDENCE_REJECTED`, `EVIDENCE_TRANSITIONED`, `EVIDENCE_DELETED` | AR-76..AR-80 (V142) | 13 → 18 |
| **#80** | `ConsentRecordService` (privacy) | `CONSENT_GRANTED`, `CONSENT_WITHDRAWN`, `CONSENT_SUPERSEDED` | AR-81..AR-85 (V143) | 6 → 9 |

### Full parity arc — 12 services, 85 active seed rules

| # | Service | Key events | Seed rules |
|---|---|---|---|
| 1 | WorkflowSubmissionService | SUBMISSION_* | AR-26..AR-30 |
| 2 | CapaTaskService | CAPA_TASK_* | AR-31..AR-35 |
| 3 | compliance CapaWorkflow | FINDING_CAPA_STAGE_CHANGED | AR-36..AR-40 |
| 4 | FindingService (audit_lc) | FINDING_* | AR-41..AR-45 |
| 5 | CrisisAarService | AAR_* | AR-46..AR-50 |
| 6 | RiskService | RISK_* | (re-used range) |
| 7 | IncidentService | INCIDENT_* | AR-56..AR-60 |
| 8 | VulnerabilityService | VULN_* | AR-61..AR-65 |
| 9 | ApprovalRequestService | APPROVAL_* | AR-66..AR-70 |
| 10 | PolicyService | POLICY_* | AR-71..AR-75 |
| 11 | EvidenceItemService | EVIDENCE_* | AR-76..AR-80 |
| 12 | ConsentRecordService | CONSENT_* | AR-81..AR-85 |

Cross-regression check across the F8 parity-arc tests stayed green at every step. **PR #77 ships the compliance anchor (`POLICY_PUBLISHED`); PR #80 ships the PDPL anchor (`CONSENT_WITHDRAWN` → DPO escalation under PDPL Article 6's unconditional withdrawal right).**

---

## 🟢 Earlier — Visible-bug fix sweep (22 May 2026, PRs #71-72)

After the four-track sprint (PRs #63-70) landed, a live walkthrough surfaced two visible issues on the most-touched pages. Two follow-up PRs and a cross-module sweep closed them:

| PR | Merge | What |
|---|---|---|
| **#71** | `c48fd54` | **`/calendar` page polish** — Topbar title + sub were wrapping onto 5+ lines when the sub was long, overflowing the topbar height and bleeding into the PageHeader below. CSS-only fix in `shell.css`: `.topbar { overflow: hidden }`, first-child wrapper gets `min-width:0`, `.topbar-title` + `.topbar-sub` get `white-space:nowrap` + `text-overflow:ellipsis`. Same PR adds the **iCal download button** in the Calendar header that was missing — PR #63 shipped the endpoint but never surfaced the action. Now the button fetches the .ics for the active window+filter and drops it into Downloads. |
| **#72** | `f12cac1` | **NDI GapsTab Fragment key warning** — `<></>` Fragment wrapping the gap-row `<tr>` + expanded-CAPA `<tr>` had `key={g.id}` on the inner `<tr>` instead of on the Fragment itself. React reconciliation kept firing 'Each child in a list should have a unique key prop' on every Gaps tab render. Fix: import named `Fragment`, swap `<>` for `<Fragment key={g.id}>`, drop redundant inner `key`. |

### Cross-module audit (live walkthrough)

All 11 major module landing pages were navigated and screenshotted/inspected:

| Module | Route | Cards | Status |
|---|---|---|---|
| Dashboard | `/dashboard` | 28 | ✓ renders, no errors |
| CAPA Register | `/capa` | 4 | ✓ |
| Workflow Submissions | `/workflows/submissions` | 96 | ✓ |
| Enterprise Risk | `/risk` | 2 | ✓ |
| BCM | `/bcm` | 24 | ✓ |
| Compliance | `/compliance` | 8 | ✓ |
| TPRM | `/tprm` | 12 | ✓ |
| Cybersecurity | `/cyber` | 12 | ✓ |
| Privacy / PDPL | `/privacy` | 8 | ✓ |
| Findings | `/findings` | 28 | ✓ |
| Audit Workspace | `/audit-workspace` | 32 | ✓ |
| **NDI** | `/ndi` | 14 tabs | ✓ all populated |
| **Calendar** | `/calendar` | month/week/agenda | ✓ post-fix |
| **Notifications** | `/notifications` | inbox + badge | ✓ |

No "Failed to load" / "Coming soon" / "Placeholder" states surfaced. The only unreachable code path is `NdiPage.NotYetShipped`, which is the catch-all else-branch when `tab` matches none of the 14 known tabs — by construction it never renders.

### Sidebar route audit

Diff between sidebar `to:` values and `<Route path=...>` declarations: only the two parameterised authority routes (`/authority/HRSD`, `/authority/MOC`) didn't have exact matches — both are covered by the dynamic `/authority/:code` route. **Every sidebar entry points at a real page.**

---

## 🟢 Earlier — Calendar finishing + Notifications + Regulatory libraries + Cmd-K (22 May 2026)

After the Calendar foundation landed (PRs #60-62) a seven-PR sprint closed the remaining gaps across four tracks:

| Track | PR | Merge | What |
|---|---|---|---|
| **A1** Calendar | **#63** | `3d23ca8` | **iCal (.ics) export** at `GET /api/v1/calendar/events.ics` — buyers subscribe from Outlook (Internet Calendars) or Google (Add from URL). RFC-5545 compliant: VEVENT per row, bilingual SUMMARY (`EN — AR`), VALARM ladder (one per reminder stop), RRULE for recurrence (DAILY/WEEKLY/MONTHLY/QUARTERLY=MONTHLY;INTERVAL=3/YEARLY), severity→PRIORITY, X-TSS-ENTITY private prop. 8 unit tests. V134 holiday-seed DATE→DATETIME2 fix for the Eid blocks. |
| **A2** Calendar | **#64** | `61dba0a` | **Week view** — third view mode (month → week → agenda). 7 columns Sun→Sat (Saudi anchor), weekend tinting + holiday tint + today-border match the month grid. Chips show eyebrow type + time (if not all-day) + 2-line clamped title. URL state adds `?d` day anchor; prev/next shift 7 days. New `startOfSaudiWeek` / `buildWeekDays` / `weekRangeLabel` helpers. |
| **A3** Calendar | **#65** | `66b339b` | **Add-event modal — recurrence + reminder ladder editors.** Recurrence select (NONE/DAILY/WEEKLY/MONTHLY/QUARTERLY/ANNUAL) with conditional "Ends on" date picker. Reminder ladder swapped from free-text to 6 preset chips (Day-of/1d/3d/1w/2w/1m) toggleable on/off, with "Custom…" escape hatch for power users. Dedup + sort-desc on submit so `7,1,3` → `7,3,1`. |
| **A4** Calendar | **#66** | `c269af2` | **F8 reminder scheduler — Saudi-workday aware.** Hourly tick walks upcoming calendar events, parses ladder, computes `startsAt − daysBefore`, shifts BACKWARDS to preceding workday if weekend/holiday (don't swallow prep window), publishes `REMINDER_DUE` DomainEvent if fire date == today AND no prior audit row. **V135 seeds 5 rules** (AR-51..AR-55) per event type: AUDIT_DAY→audit team, REPORT_DEADLINE→compliance+filer, CAPA_DUE→ESCALATE owner+lead, DR_TEST→BCM coord+tech lead, catch-all `{}` →event owner (priority 9). 7 unit tests. |
| **B** Inbox | **#67** | `2afa0f4` | **Notification inbox** at `/notifications` — closes the other half of the F8 loop. The backend already wrote `platform.notifications` rows via NotificationService.broadcastToRole on every NOTIFY/ESCALATE; this PR is the page that puts them in front of a human. Bilingual title+body, ESCALATION badge with danger-toned unread dot, optimistic mark-read, "Mark all read", relative timestamps. **Sidebar entry under My Work with unread-count badge** wired to `/api/v1/notifications/unread-count`. |
| **C** Regs | **#68** | `6cb78a2` | **Saudi regulatory libraries** — V136 expands NCA ECC-2 (21 controls: domain headers + operational backbone of domain 2 + resilience/3rd-party/ICS sigs) and SAMA CSF (14 controls across all 4 domains). V137 expands PDPL 2023 (12 articles: 1, 2, 3, 5, 6, 12, 15, 19, 21, 25, 26, 30) and DGA DMM (9 controls across 6 pillars). All 56 rows bilingual EN+AR, `is_placeholder=0`, mandatory=1. `authority_documents.notes` stamped 'CORE_SEEDED 2026-05 — Cross-reference official PDF before audit use.' Per CLAUDE.md we don't claim regulatory completeness — these are paraphrased high-frequency controls, not the full 114/N-article import. |
| **D** Cmd-K | **#69** | `b512b8b` | **Cmd-K palette content refresh** — added 8 page entries the static index was missing: /calendar, /notifications, /workflows/submissions, /capa, /findings, /ndi, /admin/licensing, /workflow-definitions. Calendar keywords cover "outlook", "deadlines", "reminders", "hijri", "saudi workdays" so users find it however they think of it. |

### Sprint highlights

- **Calendar feature-complete.** Backend foundation, frontend Month/Week/Agenda, .ics export, recurrence + reminder editors, and F8 reminder scheduler all live. Subscribe-from-Outlook works end-to-end.
- **F8 loop fully closed.** Reminders fire on schedule via `CalendarReminderScheduler`, NOTIFY/ESCALATE actions land in `/notifications` for the right roles, sidebar badge tells the user something's waiting. End-to-end: calendar event with `reminder_days=7,1,0` → hourly tick → REMINDER_DUE → matching rule (AR-51..55) → NOTIFY/ESCALATE → notification row → sidebar badge → inbox page.
- **Regulatory catalog usable.** 56 real (non-placeholder) citations across the 4 major Saudi frameworks. Compliance Mapping and Control Coverage pages now resolve against canonical NCA/PDPL/SAMA/DGA codes their auditors will recognise.
- **Discovery covered.** Every new page surfaces via Cmd-K with multiple keyword paths.

### F8 reminder scheduler — V135 seed rules

| Rule | Trigger condition | Action | Priority | Purpose |
|---|---|---|---|---|
| AR-51 | `eventType=AUDIT_DAY` | NOTIFY auditor + auditee owner | 3 | Prep materials + evidence |
| AR-52 | `eventType=REPORT_DEADLINE` | NOTIFY compliance officer + filer | 2 | Regulatory filing lead time |
| AR-53 | `eventType=CAPA_DUE` | **ESCALATE** action owner + program lead | 1 | Missed CAPA → repeat finding |
| AR-54 | `eventType=DR_TEST` | NOTIFY BCM coord + tech lead | 3 | ISO 22301 §8.5 |
| AR-55 | `{}` (catch-all) | NOTIFY event owner | 9 | Fallback for other types |

### Regulatory library citation count (V136 + V137)

| Framework | Authority Doc Code | Real citations | Coverage |
|---|---|---|---|
| NCA ECC-2 | `NCA-ECC-V2` | 21 | Domain headers + Domain 2 backbone + 3-5 signatures |
| SAMA Cyber Security Framework | `SAMA-CYBER-FW` | 14 | All 4 domains + most-audited subdomains |
| PDPL 2023 | `PDPL-2023` | 12 | Articles 1, 2, 3, 5, 6, 12, 15, 19, 21, 25, 26, 30 |
| DGA Digital Maturity Model | `DGA-DIGITAL-MATURITY` | 9 | All 6 pillars + signature sub-controls |
| **Total** | | **56** | |

---

## 🟢 Earlier — Compliance Calendar + NDI integration (22 May 2026)

A Saudi-workdays-aware compliance calendar — Outlook-equivalent. Three PRs (#60-62) build the new module + connect it to NDI:

| PR | What |
|---|---|
| **#60** | Backend — V133 `platform.calendar_events` schema, V134 Saudi-holiday seed (2026: Founding Day, Eid Al-Fitr x4, Day of Arafah, Eid Al-Adha x3, National Day), `SaudiWorkdayCalendar` helper (Sun-Thu workweek, `isWorkday()`, `addBusinessDays()`, `nextWorkday()`), `CalendarService` aggregator that stitches user events + holidays + dated GRC entities (CapaTask, Finding, Risk attestation, Risk review), `/api/v1/calendar/events` REST surface, 9 unit tests on the workday math |
| **#61** | Frontend — `/calendar` page with Month grid + Agenda views, URL-backed state (`?y&m&v&types&hol&hijri`), Saudi weekend cell tinting, Hijri (Umm Al-Qura) dual-date via `Intl.DateTimeFormat`, public-holiday cell-spanning, color-coded chips (14 event types), click-through to source entity detail page, "Falls on weekend" warning chip, new-event modal, sidebar entry under My Work, code-split chunk 15.6 kB |
| **#62** | NDI integration — `NdiAssessmentCycle.periodEnd` added as the 5th aggregation source (REPORT_DEADLINE), click-through routes to `/ndi?tab=cycles&id=<x>`, accent-tinted callout strip on the NDI page header linking to `/calendar?types=REPORT_DEADLINE&v=agenda` |

### Calendar feature highlights

- **Saudi workdays everywhere** — server's `SaudiWorkdayCalendar` is the source of truth for workday math, used both by `/calendar` UI and the F8 reminder scheduler. A deadline on Friday triggers the reminder on the following Sunday — no 6 am weekend pages.
- **Aggregator never goes stale** — synthetic rows are stitched on each query from live entity tables. No duplicate calendar snapshot to maintain.
- **5 aggregation sources** — persisted `calendar_events` (user + holidays), CapaTask target dates, Finding remediation dates, Risk attestation + review dates, NDI cycle submission deadlines.
- **14 event types** with semantic color tones (red deadlines, amber attestations, blue meetings, purple audits/holidays, cyan reviews/DR tests, green training).
- **Hijri dual-display** via `Intl.DateTimeFormat` with `calendar=islamic-umalqura` — no library, no offset table.
- **URL-backed state** — every filter combination shareable as a link.

### Public holidays seeded (V134, 10 rows for 2026)

| Holiday | Date | Notes |
|---|---|---|
| Founding Day | 22 Feb 2026 | Fixed Gregorian |
| Eid Al-Fitr (Days 1-4) | 20-23 Mar 2026 | **Provisional** — moon sighting |
| Day of Arafah | 26 May 2026 | Provisional |
| Eid Al-Adha (Days 1-3) | 27-29 May 2026 | Provisional |
| Saudi National Day | 23 Sep 2026 | Fixed Gregorian |

Eid dates marked PROVISIONAL — Council of Senior Scholars announces final dates ~24h before based on moon sighting. Year-of-event refresh required before each new Gregorian year.

---

## 🟢 Earlier additions — audit + automation parity arc complete + UX polish (22 May 2026)

After PR #42 closed the CapaTaskService audit trail, **fourteen consecutive PRs (#43-56)** drove the platform from "some services emit forensic events" to **every audit-logged service emits canonical F8 events with seed rules consuming them**. Six services, twelve PRs in pairs (audit/F8 event emission → seed rules), plus one UX polish (#52) and one STATUS refresh (#43). Result: 50 seed rules (AR-1..AR-50) wired across 6 services. Every state change a buyer might care about now wakes up the right actor without manual chasing.

| PR | Merge | What |
|---|---|---|
| #43 | `b6e241f` | STATUS.md refresh — captured PRs #40-42 (sidebar CAPA badge + backend regression + CAPA audit trail) |
| #44 | `42a2ec3` | `CapaWorkflowService` audit-log (compliance CAPA stage machine: OPEN → IMPLEMENTING → OWNER_TESTED → INDEPENDENT_VERIFIED → SIGNED_OFF → CLOSED); transition rows use **result** as actionType (`OWNER_TEST_FAIL`) so a FAIL keeps stage=IMPLEMENTING but the timeline still colours rework distinctly from re-entry |
| #45 | `2207883` | `FindingDetailPage` History tab now wraps `EntityActivityTimeline` + `EntityAutomationRuns` above the legacy mock — first audit_lc detail page lit up, drop-in identical to AAR/Risk |
| #46 | `5dd08d8` | **F8 events from CapaTaskService** — `CAPA_TASK_CREATED` + `CAPA_TASK_REVIEWED` with `previousStatus` in payload; `nullSafe()` coercion guards against engine NPE on null fields |
| #47 | `2c9c108` | **V128 seed rules** — 5 rules (AR-26..AR-30) consuming CAPA events (CRITICAL CAPA → escalate, owner change → notify, overdue → re-route, CLOSED → milestone) |
| #48 | `9e01b58` | **F8 events from CapaWorkflowService** — single `FINDING_CAPA_STAGE_CHANGED` event from all 5 methods (vs. WorkflowSubmissionService's two-event split); `result` carried in payload so a FAIL rule matches separately from a PASS |
| #49 | `f372c6c` | **V129 seed rules** — 5 rules (AR-31..AR-35) consuming `FINDING_CAPA_STAGE_CHANGED` (PDPL stage transitions → DPO, owner FAIL → CAPA reopen, independent VERIFY → audit committee, CLOSED → ISO 19011 milestone) |
| #50 | `5f77c93` | **F8 events from FindingService (audit_lc)** — `FINDING_CREATED` + `FINDING_TRANSITIONED` with `previousStatus`; payload carries `engagementCode`, `severity`, `ownerRole`, `refNumber` so rules can match on any combination |
| #51 | `519a363` | **V130 seed rules** — 5 rules (AR-36..AR-40) consuming Finding events (CRITICAL → audit lead, MGMT_RESPONSE → engagement partner, IN_REMEDIATION → owner role, AWAITING_VERIFICATION → independent reviewer, CLOSED → IIA Standard 2330 milestone) |
| #52 | `b7c99e9` | **Submissions register polish** — active-filter chip cluster replaces "Clear all" pill, sticky bulk-action bar with `.is-active` brighten, title-link `chevron-right` affordance with RTL-safe `transform: scaleX(-1)` + `titleAr ?? titleEn` latent-bug fix |
| #53 | `ab4b894` | **F8 events from CrisisAarService** — normalized 4 distinct `CRISIS_AAR_<status>` events into single `CRISIS_AAR_TRANSITIONED` + condition_json; severity pulled lazily from parent CrisisEvent so rules like "AAR approved for SEV-1" can match |
| #54 | `e2c22cf` | **V131 seed rules** — 5 rules (AR-41..AR-45) consuming AAR events (SEV-1 → executive sponsor, DRAFT→UNDER_REVIEW → BCM champion, APPROVED → ISO 22301 §10 milestone, PUBLISHED → crisis manager handoff, RESCINDED → escalate audit lead + sponsor) |
| #55 | `a466e20` | **F8 events + explicit audit from RiskService** — last service to gain canonical audit + F8; 5 transition methods centralised through single `doTransition()` so future transitions can't accidentally skip either; payload carries `riskLevel`, `complianceFrameworkClause` for regulator-anchored routing |
| #56 | `ac42b06` | **V132 seed rules** — 5 rules (AR-46..AR-50) consuming Risk events (CRITICAL → escalate CRO + sponsor, PDPL-anchored → DPO, OPEN→ACCEPTED without review → SAMA §10.4 governance flag, MITIGATING → owner kickoff, CLOSED → ISO 31000 §6.5 milestone). **Closes the parity arc.** |

### Audit + F8 parity table — final state

| Service | Audit log | F8 events | Seed rules | Test count |
|---|---|---|---|---|
| WorkflowSubmissionService | ✓ #20 | ✓ #21 | V127 (AR-21..25) | 22 tests |
| CapaTaskService | ✓ #42 | ✓ #46 | V128 (AR-26..30) | 6+ tests |
| CapaWorkflowService | ✓ #44 | ✓ #48 | V129 (AR-31..35) | 4+ tests |
| FindingService | ✓ #32 | ✓ #50 | V130 (AR-36..40) | 6 tests |
| CrisisAarService | ✓ pre-existing | ✓ #53 | V131 (AR-41..45) | 14 tests |
| RiskService | ✓ #55 | ✓ #55 | V132 (AR-46..50) | 18+ tests |

**50 seed rules** total across 6 services. Every state change writes an `audit_log` row AND publishes a canonical `DomainEvent` that the F8 engine evaluates against active rules — buyers see both the **forensic timeline** (humans) and the **automation runs** (machines) on every detail page.

### Reusable forensic-timeline pattern — fully lit

`components/audit/EntityActivityTimeline.tsx` + `components/audit/EntityAutomationRuns.tsx` are a two-line drop-in on any detail page. After PR #45 the pattern hits its full set:

| Detail page | Entity type | Status |
|---|---|---|
| `/workflows/submissions/:id` | `WorkflowSubmission` | ✅ #24, #27 |
| `/bcm/aars/:id` | `CrisisAar` | ✅ #29 |
| `/risk/:id` (History tab) | `Risk` | ✅ #30 |
| `/findings/:id` (History tab) | `Finding` | ✅ **#45** |
| `/capa/:id` | `CapaTask` | service audit-logs + emits F8; detail page = next UX PR |

### Closure-milestone uniformity (AR-29 + AR-40 + AR-43 + AR-50)

Four closure-milestone audit rules now write a uniform shape to `platform.audit_log`. The quarterly NCEMA / SAMA / NCA-ECC filing template can build its "closed this quarter" sections from `audit_log` alone, with no per-service report queries:

| Rule | When | Milestone |
|---|---|---|
| AR-29 | CapaTask CLOSED | "CAPA closed" |
| AR-40 | Finding CLOSED | IIA Standard 2330 closure |
| AR-43 | AAR APPROVED | ISO 22301 §10 lessons learned |
| AR-50 | Risk CLOSED | ISO 31000 §6.5 closure |

### Submissions register polish (PR #52)

Three buyer-visible improvements on `/workflows/submissions`:

1. **Active-filter chips** — every applied filter renders as `[Module: PRIVACY ×] [Status: Open ×] [Search: "breach" ×]   Clear all`. Replaces the single "Clear all" pill from #34. Each chip's inline × clears just that filter; URL stays in sync.
2. **Sticky bulk-action bar** — `position: sticky; top: var(--header-h)`; an `.is-active` modifier (lighter background + soft shadow + primary border) brightens when any rows are selected so the action affordance never scrolls out of view.
3. **Title-link affordance** — chevron-right after every title nudges right on hover with underline; RTL flips the chevron via `transform: scaleX(-1)` and reverses the nudge direction. Also fixed a latent bug where `titleEn` was rendered unconditionally regardless of UI language — now `isAr ? (s.titleAr ?? s.titleEn) : s.titleEn`.

---

## 🟢 Earlier additions — thirty-two-PR wizard arc + reviewer-journey polish + sidebar inbox pattern (21 May 2026)

After PR #11 (41 commits) merged the §7 wizard catalog + Workflow Hub + bundle code-split, twenty follow-up PRs closed the persistence + UX + automation + AI + audit loop end-to-end, then extracted the forensic-timeline pattern into reusable cards that drop into any detail page. The next seven PRs (#32-38) polished the reviewer journey itself — server-side aggregation, Hub→register drill-down, sidebar inbox badge, bulk-review actions, stale-row signal, free-text search. The final three PRs (#40-42) consolidate: a backend regression test for the new aggregate, the sidebar inbox surface generalised to a pattern (workflow submissions + CAPA today, more in 3-line additions), and CAPA mutations now write to the forensic audit log so the reusable timeline card extends to corrective-action surfaces:

| PR | Merge | What |
|---|---|---|
| #11 | `9c141e1` | 24 wizards + Workflow Hub + WorkflowQuickLaunch chip strips + −53% gzip bundle code-split |
| #12 | `77b1b02` | 7 wizards wired to dedicated tables (BcmExercise, CyberIncident, VulnTriage, DSR, Breach, RoPA, SupplierOnboarding) |
| #13 | `a422f59` | V125 generic `workflow_submissions` ledger + new `module/workflow_submission/` backend + 12 remaining wizards wired (Risk Treatment/Acceptance, RCA, Mgmt Review, Signature, Control Test, Cloud CCC, NCA ECC, Consent, Cross-Border, Exit Plan, Board Pack) |
| #14 | `ca2f363` | `/workflows/submissions` register UI — filter by module + workflow id, status counts, JSON inspect, inline Approve/Reject/Close |
| #15 | `85a94f4` | 15-case `WorkflowSubmissionServiceTest` + V126 demo seed (25 representative rows) + sidebar entry "Workflow Submissions" |
| #16 | `cfdb564` | `ModuleSubmissionsPanel` — latest-5 panel on Privacy / BCM / SCM / Cyber / Risk / Compliance / Incidents pages |
| #17 | `62aeeec` | `DashboardSubmissionsCard` — status-counts strip + top-8 rows on `/dashboard` (first-login visibility) |
| #18 | `06eca6e` | `/workflows/submissions/:id` detail page — URL-shareable, full payload, review actions, copy-link, "open new" CTA |
| #19 | `7b7a0f4` | Filter-aware CSV export from the register (UTF-8 BOM, RFC-4180 quoting, filter-stamped filename) |
| #20 | `c3a01b7` | `AuditLogService` integration — every CREATE / APPROVE / REJECT / CLOSE / UNDER_REVIEW writes to `platform.audit_log` with cross-tenant defense |
| #21 | `f3af7f7` | F8 automation rules on submission events — V127 seeds AR-21..AR-25 (PDPL breach → NOTIFY DPO, NCA cyber-incident → NOTIFY CISO, risk acceptance → ESCALATE risk committee, cross-border → FLAG DPO review, board pack APPROVED → AUDIT milestone) |
| #22 | `afa5d98` | `/approvals` → `/workflows/submissions` cross-link in the page header + STATUS.md table refresh |
| #23 | `a4a8cb2` | AI review hints — 6th heuristic generator in `module/ai/`; per-wizard bilingual checklist + suggested decision; `AiReviewHintCard` on detail page |
| #24 | `e492cec` | Activity timeline on detail page — every audit-log row inline (CREATE / APPROVED / REJECTED / CLOSED / UNDER_REVIEW) with color-coded badges + actor (username · role) |
| #25 | `3036957` | `SubmissionAnalyticsCard` on the register — 3 KPIs (avg time-to-review, open count, active modules) + 3 lazy-loaded recharts (per-module bar, per-status donut, 30-day line); recomputes against current filter |
| #26 | `91feb05` | Per-wizard live submission counts on Hub cards — "3 submitted · 1 open" badges replace the static catalog |
| #27 | `5ffe91c` | F8 rule-run history on detail page — `AutomationRunsCard` shows which automation rules fired (paired with PR #24 timeline = humans + machines side-by-side) |
| #28 | `a529ab3` | STATUS.md refresh — captured PRs #22-27 + first end-to-end summary tables |
| #29 | `239d6d9` | Extracted timelines into reusable `components/audit/EntityActivityTimeline.tsx` + `EntityAutomationRuns.tsx`; dropped them onto `/bcm/aars/:id` (lights up the existing CrisisAarService audit-log calls automatically); WorkflowSubmissionDetailPage chunk shrunk 10.43→7.85 kB raw via the extraction |
| #30 | `a773a10` | Rolled the reusable cards into `RiskDetailPage` history tab — real audit_log rows + F8 rule fires + legacy mock history side-by-side; pattern now lives on 3 detail pages with 5+ more one-line-away once their services start audit-logging |
| #31 | `b623ca4` | STATUS.md refresh — captured PRs #28-30 + the reusable audit-timeline pattern subsection |
| #32 | `951e605` | FindingService audit-logging — every CREATE / TRANSITION / DELETE writes to `platform.audit_log`; FindingDto exposes numeric PK so the timeline card is one-line drop-in once an audit_lc Finding detail page lands |
| #33 | `e8e1fc3` | Server-side aggregation for Hub counts — new `GET /api/v1/workflow-submissions/counts/by-workflow`; single tenant-scoped JPQL `GROUP BY` replaces the client-side O(rows) aggregation; `WorkflowCount(workflowId,total,open,approved)` typed projection |
| #34 | `9f6ee26` | Hub count badges drill into pre-filtered Submissions register — `?module`, `?workflowId`, `?status` URL params (shareable, back-button-friendly); new status filter with `OPEN = SUBMITTED+UNDER_REVIEW`; Hub badge becomes a `role="link"` span that opens the register pre-scoped (+`&status=OPEN` when warning tone); "Clear all" pill |
| #35 | `848d09d` | Sidebar pending-reviews badge — accent-300 pill on "Workflow Submissions" entry shows `OPEN` count from the aggregate endpoint; refreshes on tab focus (`visibilitychange`); threaded through `NavRow` as optional `badge?` prop so adding CAPA/approvals badges later is one line |
| #36 | `dbd5b3c` | Bulk approve / reject / close — checkbox per OPEN row + sticky action bar; tri-state header checkbox (all/partial/none); `window.confirm` second-thought beat; `Promise.allSettled` so a single 409 doesn't abandon the rest; selection drops on reload + filter change |
| #37 | `279ea2f` | Stale-row highlighting — every OPEN row gains an age chip in the card header ("4d" info-blue → "12d · stale" danger-red past `STALE_DAYS = 7`); stats row gains a "Stale (7d+): N" pill; threshold centralised at top of file |
| #38 | `3d68bae` | Free-text search on the register — URL-backed `?q=...` substring across `titleEn/Ar`, `summaryEn/Ar`, `regulatoryAnchor`, `workflowId`, `workflowModule`, `reviewerNote`, **and** `payloadJson` so "breach" hits a payload field even when titleEn doesn't say it; plays with existing filters; selection drops on query change |
| #39 | `666337a` | STATUS.md refresh — captured PRs #31-38 + the reviewer-journey before/after table |
| #40 | `a91b6ac` | Backend regression test for `WorkflowSubmissionService#countsByWorkflowId` — five Mockito tests lock the JPQL projection (workflowId, total, open, approved), the `Number → long` cast that survives both SQL Server's `Integer` and `Long` returns, empty-result behaviour, tenant scoping across two tenants in one JVM, and the no-tenant guard; 24 + 5 = 29 tests in the suite |
| #41 | `150c19f` | Sidebar inbox pattern extends to CAPA — `GET /api/capa/tasks/counts` returns `{open, overdue}` (two cheap COUNTs, no row payload); `useInboxCounts` hook scales to N counters via `Promise.all` + per-counter `.catch`; `NavRow` takes `NavBadge { value, title, tone? }` so each counter carries its own tooltip + tone; overdue CAPAs invert to a danger-red pill with right invert in active-route state |
| #42 | `78c053b` | `CapaTaskService` audit-log — every create / transition / softDelete writes to `platform.audit_log` with the new state as `actionType` so timeline rows colour correctly (CLOSED→success, CANCELLED→neutral, IN_PROGRESS→info, AWAITING_VERIFICATION→warning); +3 new tests pin transition message shape, verifier capture on CLOSED, and the cross-tenant-404 no-audit guard |

### Wizard persistence state

**20 of 24 catalog wizards POST to a real backend on submit, audit-log every transition, fan out to the F8 automation engine, and offer AI-generated reviewer guidance on demand.** The 4 remaining (`evidence-request`, `capa-workflow`, `evidence-connectors`, `incident-triage`) are catalog entries that route to existing register pages.

### Reusable forensic-timeline pattern (PRs #29-30)

`components/audit/EntityActivityTimeline.tsx` + `components/audit/EntityAutomationRuns.tsx` are now a two-line drop-in on any detail page. Both take `{entityType, entityId}` and hide silently when empty, so a fresh entity with no audit history renders no phantom cards.

| Detail page | Entity type | Status |
|---|---|---|
| `/workflows/submissions/:id` | `WorkflowSubmission` | ✅ lit up (#24, #27) |
| `/bcm/aars/:id` | `CrisisAar` | ✅ lit up (#29) — CrisisAarService already audit-logs |
| `/risk/:id` (History tab) | `Risk` | ✅ lit up (#30) |
| `/findings/:id` | `Finding` | ✅ service audit-logs (#32); DTO now exposes `numericId` — page drop-in is two lines once a Finding detail page exists in v2 |
| `/capa/:id` (when built) | `CapaTask` | ✅ service audit-logs (#42); two-line JSX drop-in when v2 grows a CAPA detail surface |
| `/controls/:id`, `/policies/:id`, `/vendors/:id`, `/dpias/:id` | (services don't audit-log yet) | one-line away once they do |

The cards re-use the existing `GET /api/v1/audit-log/history` + `GET /api/v1/automation/runs/for-entity` endpoints — no schema or backend work needed per page. Adding the same pattern to a new module is two lines of JSX + (if missing) one constructor field + one `audit.log()` call in the service.

### Reviewer-journey polish (PRs #33-38)

Six PRs in three days collapsed the reviewer's path from "navigate around to find pending work" to "see the inbox, click, clear it":

| Before | After |
|---|---|
| Log in. No signal anywhere of pending work. Navigate to Submissions. Scroll. | Sidebar shows **"Workflow Submissions · 4"**. Click. |
| Submissions register loads every row to compute Hub badge counts (O(rows) per page-load). | Hub badges fed by a single tenant-scoped SQL `GROUP BY` aggregate. Register stays row-level for the list itself. |
| Hub badge says "1 open RoPA" — dead-end data; user must scroll the unfiltered register to find it. | Click the badge → register opens at `?workflowId=privacy-ropa&status=OPEN` already scoped to the row. URL is shareable + back-button-friendly. |
| 5 open submissions = 5 clicks, 5 modal beats. | Tick header checkbox → "Approve (5)" → confirm. `Promise.allSettled` reports per-row results. |
| Date strings ("Submitted 2026-05-09") — reviewer mentally subtracts to find oldest. | Age chip on every OPEN row turns danger-red after 7d (matches PDPL §18 / ECC SLA). Stats row counts the stale. |
| No way to find a remembered submission by keyword. | URL-backed `?q=...` substrings across title/summary/anchor/workflowId/payload. Pastes work. |

The wizard-submission surface is now production-feel for the day-to-day reviewer.

### Sidebar inbox pattern (PRs #35, #41)

The sidebar pending-reviews badge from PR #35 was deliberately threaded through `NavRow` as an optional `badge?` prop so adding more inbox counters later would compose. PR #41 cashed that bet — CAPA counts are now wired the same way, and the hook (`useInboxCounts`) scales to N counters via `Promise.all` + per-counter `.catch(() => null)` so one endpoint failing doesn't blank the rest.

| Sidebar entry | Counter source | Tone rule |
|---|---|---|
| Workflow Submissions | `GET /api/v1/workflow-submissions/counts/by-workflow` summed to `open` | Always accent-300 |
| CAPA | `GET /api/capa/tasks/counts` → `{open, overdue}` | accent-300 by default, **danger-red** if `overdue > 0` |

Adding a third badge (signatures inbox, approvals queue, whatever's next) is a one-PR, three-step pattern:
1. backend `*Counts` endpoint that returns a typed record (two-three cheap COUNTs)
2. add the field to `InboxCounts` + a line in `useInboxCounts.load()`
3. add a clause in `badgeFor(id)` for the target `NavRow.id`

### CAPA audit trail (PR #42)

`CapaTaskService` now writes to `platform.audit_log` on every mutation:

| Action | actionType | Message shape |
|---|---|---|
| `create()` | `CREATE` | "CAPA <id> opened from <origin> · owner=… · severity=… · target=…" |
| `transition()` | `<newStatus>` | "<id> OLD → NEW · verified by … (if CLOSED) · reason: … (if CANCELLED) · note: …" |
| `softDelete()` | `DELETE` | "<id> soft-deleted (status=<at-delete>)" |

The transition row uses the **new state** as `actionType` so the timeline card colours each transition correctly out of the box (CLOSED→success, CANCELLED→neutral, IN_PROGRESS→info, AWAITING_VERIFICATION→warning). The forensic story buyers see for workflow submissions, AAR, Risk, and Finding now extends to corrective + preventive actions.

### End-to-end surface visible to a buyer

| Surface | What appears |
|---|---|
| `/dashboard` | `DashboardSubmissionsCard` — status counts + top-8 rows + "View all (N)" |
| `/workflows` (Hub) | 24 wizard cards with **live** "N submitted · M open" badges (server-side aggregate) — **click badge → drill into pre-filtered register** |
| `/<module>` (×7) | `WorkflowQuickLaunch` chip strip + `ModuleSubmissionsPanel` (latest 5) |
| `/workflows/submissions` | Register + analytics card (3 KPIs + 3 charts) + free-text search + module/wizard/status filters (URL-backed) + stale-row age chips + bulk approve/reject/close + CSV export |
| `/workflows/submissions/:id` | Hero envelope → AI review hint → Activity timeline (audit log) → Automation runs (F8) → Review actions → Full payload |
| `/approvals` | Existing inbox + "Workflow submissions" header CTA |
| `/ai/suggestions` | Filter by 6 features (added `WORKFLOW_SUBMISSION_REVIEW`); accept/reject DRAFT rows |
| Sidebar | "Workflow Hub" + **"Workflow Submissions · N"** (live pending-reviews badge) + **"CAPA · M"** (live open badge, turns danger-red when any are past target date) under My Work |

### Backend characteristics

- **22 service tests** on `WorkflowSubmissionServiceTest` — validation, state machine, cross-tenant 404 + cross-tenant audit defense + cross-tenant event-publisher defense, audit-log entry shape, F8 event payload shape, null-coercion guard, decision-mapping
- **Cross-tenant guarantees**: every read/write goes through `TenantContext.getCurrentTenant()`; a 404 from another tenant writes **no audit row AND publishes no F8 event** under the requesting tenant (both verified)
- **F8 integration**: 5 seed rules (AR-21..AR-25) routed by `workflowId` / `status` / `regulatoryAnchor`; engine evaluates equality + contains against the payload Map; payload values null-coerced to `""` to prevent NPEs
- **AI module**: 6 heuristic generators in `module/ai/`, all persisting DRAFT `AiSuggestion` rows for human review; LLM-gateway swap is a single private-method substitution per heuristic, no schema or API change

### Bundle performance

| Metric | Value |
|---|---|
| Main bundle | 1,537 kB raw / 470 kB gzip (−53% from pre-split baseline of 1,002 kB gzip) |
| Wizard chunks | 18 × ~5-9 kB raw each, fetched on demand |
| Route chunks | 95+ total |
| Detail-page chunk | 10.43 kB raw / 3.87 kB gzip (absorbs hero + AI hint + audit timeline + automation runs + review controls + payload) |
| Submissions register chunk | 11.32 kB raw / 4.51 kB gzip (includes analytics card; recharts lazy-loaded as shared 397 kB bundle) |

Recharts already split as own 397 kB / 116 kB gzip bundle — shared with other chart-bearing pages, not a new cost.

## 🟢 Recent additions (PR #11 — 39 commits)

### Saudi-GRC buyer-assessment engines
| Migration | Tables / columns |
|---|---|
| **V113** | Cross-border + special-category fields on `processing_activities`; `risk.risk_controls` bridge; index on `evidence_items.review_due_date` |
| **V115** | `risk.risk_attestations` (quarterly cadence); `evidence.evidence_connectors`; `crisis_events.pdpl_72h_*`; `dsr_cases.sla_breached_at`; `risks.attestation_cadence_days` |
| **V116** | `ai.ai_suggestions`; `bcm.crisis_aar`; full 28-role Saudi-GRC catalog in `iam.roles` |
| **V117–V123** | ~1,100 demo rows across 33 tables (Saudi-context vendors, KSA threat intel, bilingual data) |
| **V124** | UNIQUE INDEX on `bcm.crisis_aar(organisation_id, report_code)` |

### New backend modules
- `module/ai/` — 5 heuristic generators with persisted DRAFT ledger + human review workflow (LLM gateway is a private-method swap away). Endpoints under `/api/v1/ai/*`.
- `module/bcm/cycle/CrisisAar*` — NCEMA After-Action Reports with auto-stitched narrative + state machine `DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED → RESCINDED`. Endpoints under `/api/v1/bcm/aars/*`.
- `module/platform/completion/BuyerGap*` — wraps the V115 sweep endpoints (DSR SLA, PDPL 72h, risk attestation, evidence freshness) + connector registry.

### New frontend
- **Workflow Hub** at `/workflows` — 35-workflow catalog grouped by module, filterable by status / search
- **24 wizards** AVAILABLE out of the 35-entry §7 catalog (69%). Every PARTIAL/PLANNED entry that has a regulator-facing output is now a real wizard:
  - **ERM** (4/4 ✓) — Risk Identification, Treatment, Acceptance, Attestation
  - **BCM** (7/7 ✓) — BIA, BCP, DRP, Crisis, Exercise, AAR, Management Review
  - **SCM** (3/3 ✓) — Supplier Onboarding, Vendor Risk, Exit Plan
  - **NCA** (4/4 ✓) — Cyber Incident, ECC Assessment, Vuln Triage, Cloud CCC
  - **PDPL** (6/6 ✓) — DPIA, DSR, Breach, Cross-Border, Consent, RoPA
  - **Cross-module** (4/x) — Incident RCA, Management Review (generic), Digital Signature, Board Pack, Control Test
  - Remaining 11 entries are register-page-only (e.g. `/capa`, `/policy-management`, `/connectors`) — not wizard-shaped
- `/buyer-gap` — live demo of every V115/V116 endpoint
- `/bcm/aars/:id` — bilingual AAR editor with state-machine actions
- `/ai/suggestions` — accept/reject AI drafts with reviewer note
- **Design-system primitives**: `useLoadable<T>()`, `<ErrorState>`, `<LoadableSection>`, `<ErrorBoundary>`, `<WizardShell>` (used by every wizard above)

### Verification
| Check | Result |
|---|---|
| `mvn test` | **673 / 673 pass** (+31 from this branch) |
| `tsc --noEmit` | **0 errors** |
| `vite build` | clean, ~6.5s, 95+ route chunks |
| `/actuator/health` | UP |
| All 56 page-level endpoints | 200 |
| Backend startup warnings | All silenced |

### Bundle performance (cumulative across both code-split commits)
| Metric | Pre-split | After wizards | After full split | Total Δ |
|---|---|---|---|---|
| Main bundle raw | 3,588 kB | 3,430 kB | **1,530 kB** | **−2,058 kB (−57%)** |
| Main bundle gzip | 1,002 kB | 964 kB | **468 kB** | **−534 kB (−53%)** |
| Off-route chunks | 0 | 20 | 95+ | each 5-40 kB |

Initial JS now under 500 kB gzipped — meaningful TTI gain on low-bandwidth Saudi data networks. Every off-route page (wizards, lifecycle, detail, portal, intake forms, admin, KB, framework packs, dependency graph, etc.) fetched on demand via React.lazy + Suspense.

### Module-page wizard chips (PR #11 D-side)
Every module landing page (`/privacy`, `/bcm`, `/scm`, `/cyber`, `/risk`, `/compliance`, `/incidents`) now surfaces a horizontal chip strip of all AVAILABLE wizards for that module — auto-synced to the catalog so future wizards appear without per-page edits. See `WorkflowQuickLaunch.tsx`.

### Notable bug fixes this PR
- **🔴 Cross-tenant FK leak** in `linkRiskControl` (would let A persist a bridge row pointing at B's risk_id)
- **🔴 Backend startup crash** — ambiguous `/api/v1/evidence/connectors` mapping between EvidenceMatrixController and the new BuyerGapController; resolved by routing V115 endpoints under `/connector-registry/*`
- DSR sweep dedup bug (`Boolean.FALSE.equals(null)` returned false → first-time overdue cases never fired the event)
- AAR sequence race + missing year scoping + `.size()` doing O(n) row hydration
- 47 pre-existing TS errors across BCM / Licensing / CrisisRoom / Bcp pages
- `Cannot find cache named 'licenses'` WARN loop on every login
- Evidence freshness sweep loaded everything in one batch (OOM risk at scale)
- `NoResourceFoundException` and `HttpRequestMethodNotSupportedException` mapped to 500 instead of 404 / 405

### Vendor / product-decision blocked
- Real digital-signature provider adapter (Nafath / Adobe Sign / DocuSign)
- AI module LLM gateway (heuristic generators ship today)
- Tier-3 #14 threat-intel live feed
- Tier-3 #17 supplier portal
- `PageImpl → VIA_DTO` migration (would break frontend response shape)

---

## Final Code Path
the final code in path : C:\AYMAN\Github\Claude-GRC\.claude\worktrees\quirky-borg

## Quick Start

```bash
# 1. Create DB schema (run once per module)
python setup_cyber_db.py
python setup_bcm_db.py

# 2. Start backend (port 8084)
java -jar tss-grc-backend/target/tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev

# 3. Start UI (port 8087)
cd tss-grc-ui && python server.py 8087

# 4. Open browser
http://localhost:8087/login.html
```

**Login credentials:**
| Username | Password | Role |
|----------|----------|------|
| admin | Admin@12345 | PLATFORM_ADMIN |
| riskmanager | Risk@12345 | RISK_MANAGER |
| auditor | Audit@12345 | AUDITOR |

**DB:** SQL Server 2019, database `TSS_GRC`, user `sa / tss.123`

---

## ✅ COMPLETED MODULES

### Sprint 1 — Core Platform (commit `4189d18`)
| Feature | Status |
|---------|--------|
| Spring Boot 3.5 project skeleton | ✅ |
| JWT authentication (login / refresh / logout) | ✅ |
| Role-based access control (PLATFORM_ADMIN, RISK_MANAGER, AUDITOR) | ✅ |
| Permission system per module (READ/WRITE) | ✅ |
| Multi-tenant architecture (organisation_id) | ✅ |
| SQL Server schema + seed data | ✅ |
| ERM — Risk Management module (full CRUD + heatmap) | ✅ |
| Incident Management module (basic) | ✅ |
| Dashboard (KPI cards, risk heatmap, compliance bar chart, trend chart) | ✅ |
| Login page (split-panel, RTL/LTR, Arabic/English) | ✅ |
| app.js global shell (sidebar + header + language switcher + notifications) | ✅ |
| translations.js (EN + AR for all modules) | ✅ |
| Swagger UI (dev profile, port 8084) | ✅ |
| User Management API + Admin UI | ✅ |

### Sprint 2 — GRC Modules (commits `0ab9401` → `2a70135`)
| Module | UI | Backend | DB |
|--------|----|---------|----|
| **Compliance** | ✅ | ✅ | ✅ |
| **Privacy (PDPL)** | ✅ full 9-tab module | ✅ | ✅ |
| **BCM (ISO 22301)** | ✅ full 9-tab module | ✅ | ✅ |
| **TPRM** | ✅ | ✅ | ✅ |

### Cyber Security Module (commit `726cd1e`) ← NEW IN V2
| Component | Status | Detail |
|-----------|--------|--------|
| **Sidebar menu entry** | ✅ | `nav.cyber` → `/cyber/index.html` |
| **EN + AR translations** | ✅ | All 10 tabs, KPIs, buttons, fields |
| **Backend entities (10)** | ✅ | CyberControl, CyberSubControl, CyberFramework, ControlAssessment, Vulnerability, VulnException, CyberIncident, CyberAsset, CyberEvidence, CyberActionPlan |
| **Repositories (10)** | ✅ | Full JPA repos with org-scoped queries |
| **CyberService** | ✅ | 541 lines — full business logic + 5 workflows |
| **CyberController** | ✅ | 427 lines — 50+ REST endpoints |
| **DB schema + seed** | ✅ | `setup_cyber_db.py` — 13 steps, 41 seed rows |
| **Tab 1: Overview** | ✅ | 8 KPI cards + recent tables + 2 Chart.js charts |
| **Tab 2: Controls Library** | ✅ | Table + Add/Edit modal + filter/search/pagination |
| **Tab 3: Sub-Controls** | ✅ | Table + filter by parent + Add/Edit modal |
| **Tab 4: Control Assessments** | ✅ | Table + Start Assessment + Approve + Convert Gap→CAPA |
| **Tab 5: Vulnerabilities** | ✅ | Table + 6-step workflow bar + Register/Edit + Escalate |
| **Tab 5: Exceptions** | ✅ | Table + 5-step workflow bar + Request/Edit + Approve |
| **Tab 6: Cyber Incidents** | ✅ | Table + 9-step response workflow + Register/Edit + Advance |
| **Tab 7: Assets & Systems** | ✅ | Full asset inventory + Add/Edit modal |
| **Tab 8: Evidence & Compliance** | ✅ | Evidence repo + Add/Edit + expiry indicators |
| **Tab 9: Action Plans (CAPA)** | ✅ | Table + progress bar + Add/Edit + Complete |
| **Tab 10: Reporting** | ✅ | 4 stat cards + 3 Chart.js charts |
| **RTL/LTR** | ✅ | `[dir=rtl]` CSS on all elements |
| **i18n** | ✅ | All `data-i18n` attrs wired |

#### Cyber Workflows Implemented
| Workflow | Steps |
|----------|-------|
| Control Assessment | DRAFT → IN_PROGRESS → UNDER_REVIEW → APPROVED → CLOSED |
| Vulnerability Management | REGISTERED → CLASSIFICATION → OWNER_ASSIGNMENT → REMEDIATION → VERIFICATION → CLOSED |
| Exception Management | REQUESTED → IMPACT_ASSESSMENT → COMPENSATING_CONTROLS → APPROVED → EXPIRED |
| Cyber Incident Response | DETECTION → LOGGING → TRIAGE → CONTAINMENT → INVESTIGATION → ERADICATION → RECOVERY → CLOSURE → LESSONS_LEARNED |
| Action Plan (CAPA) | OPEN → IN_PROGRESS → COMPLETED |

---

### SDAIA NDI / نضيء — National Data Index Module (commits `2c79a6e` → `25e996a`) ← LANDED 2026-05-17
| Slice | Migration | Tab | Status | Notes |
|-------|-----------|-----|--------|-------|
| 1 — Assessment Cycles      | V66 | Cycles      | ✅ | 13-state lifecycle, "one active per year" |
| 2 — Framework Library      | V67 | Framework   | ✅ | 20 placeholder items, JSON import endpoint |
| 3 — Maturity Assessment    | V68 | Maturity    | ✅ | CMMI 0-5, Rule 1 + 2 + D.Validation |
| 4 — Compliance Assessment  | V69 | Compliance  | ✅ | Dual-axis (substantive + lifecycle), Rules 3-5 |
| 5 — Operational Excellence | V70 | OpEx        | ✅ | 10 KPIs, Rule 13 direction-aware status |
| 6 — Data Governance Domains| V71 | Domains     | ✅ | 8 placeholder domains, cross-slice rollup |
| 7 — Evidence Repository    | V72 | Evidence    | ✅ | 8 artefacts, Rule 6 expiry auto-flip |
| 8 — Gaps + CAPA            | V73 | Gaps        | ✅ | 5 gaps + 7 CAPAs, Rules 7-9, auto-resolve |
| 9 — Improvement Plan       | V74 | Improvement | ✅ | PLAN-2026-01 IN_FLIGHT, Rules 10-12 |
| 10 — Submission Package    | V75 | Submission  | ✅ | 6-state workflow, Rules 15-16, SHA-256 manifest |
| 11 — Reports               | V76 | Reports     | ✅ | 12 saved reports + ad-hoc runner |
| 12 — Dashboard             | (none) | Dashboard | ✅ | 14-widget aggregate, single round trip |
| 13 — Settings              | V77 | Settings    | ✅ | 12 settings, 5 categories, typed values |
| 14 — Audit Log             | V78 | Audit       | ✅ | 15 actions, 3 severity levels, append-only |
| 15 — Tests + QA checklist  | (none) | (n/a)     | ✅ | 11 unit tests · docs/ndi/QA_CHECKLIST.md |

**NDI follow-ups (commits `24b3409` → `25e996a`)**
- **A — Auto-audit interceptor** (`24b3409`): `NdiAuditPublisher` wired into 8 services (cycle, maturity, compliance, evidence, gap, plan, domain, submission). Every state change emits an audit row automatically.
- **B — PDF/XLSX/CSV exports** (`bf97588`): `NdiReportExporter` (OpenPDF + Apache POI). `GET /api/ndi/reports/:id/export?format=…` returns the binary; UI has XLSX/PDF download buttons on each saved report + on the ad-hoc result.
- **C — Schedule executor** (`25e996a`): `NdiScheduler` runs the evidence expiry sweep every 6h and the report cron loop every 60s. Verified by setting `0 * * * * *` on RPT-001 and watching it auto-fire.

**Guardrail (Rule 0)** — Every domain / framework item / submission package seeded by V66-V78 carries `is_placeholder = 1` and the bilingual banner: *"Placeholder — requires official SDAIA/NDI control library import / عنصر مؤقت — يتطلب استيراد مكتبة الضوابط الرسمية من سدايا/نضيء"*. Flip `official_library_imported = true` in settings + each domain's `Mark official` action after the official library lands.

### Audit Management — `audit_lc` entities filled in (commit `0a36252`) ← LANDED 2026-05-17
V16 created 4 `audit_lc.*` tables but only `findings` was wired to Java. This commit added `AnnualPlan` / `WorkingPaper` / `CapaTask` entities + repositories + a combined `AuditLifecycleService` + `/api/audit-lc/{annual-plans, working-papers, capas}` REST endpoints. Frontend `lib/auditLifecycle.ts` swapped from localStorage mocks to real backend calls (commit `990e66a`) — `AuditLifecyclePage` consumes the same sync API but is now persistent.

### Policy Management — full module (commit `8098e43`) ← LANDED 2026-05-17
V79 migration: `policy.documents` + `policy.exceptions` + `policy.attestations` with 6/3/6 seeds. Three state machines (policy DRAFT → … → ARCHIVED, exception REQUESTED → … → REVOKED, attestation PENDING → ATTESTED|DECLINED). PUBLISHED-requires-effective-date guard, expiry sweeps, `/new-version` cloner, full UI at `/policy-management` with 4 tabs (Overview / Documents / Exceptions / Attestations).

### Cross-module integration (commit pending) ← LANDED 2026-05-17
NDI `gap.risk_id` and `compliance_assessment.risk_id` promoted from soft FK to validated ERM lookup. `NdiCrossLinkService` rejects non-existent risk ids on create/update with HTTP 400 + a clear message; `GET /api/ndi/gaps/{id}/linked-risk` and `GET /api/ndi/compliance/{id}/linked-risk` return a `LinkedRisk` enrichment record (id, externalId UUID, titleEn/Ar, status, sourceModule, inherentLikelihood/Impact, ownerId).

### TPRM Questionnaire Engine — V81 (commit pending) ← LANDED 2026-05-17
TPRM had basic Vendor + VendorAssessment CRUD but `risk_score` was a free-form decimal with no rubric behind it. V81 adds a deterministic questionnaire engine: `tprm.questionnaire_templates` + `_questions` + `_responses`, plus 5 new columns on `vendor_assessments` (workflow_status, template_id, answered_count, total_questions, computed_score_pct). Seed: one ACTIVE *Critical Vendor Cyber Due Diligence v1.0* template with 8 bilingual questions across 3 sections (Information Security, Privacy, Operational Resilience). Service implements full template lifecycle (DRAFT → ACTIVE → ARCHIVED, frozen-once-ACTIVE rule), per-vendor response upsert with on-save weighted-sum recompute, NA-with-justification, and a NOT_STARTED → IN_PROGRESS → COMPLETED → REVIEWED workflow on the assessment. Scoring math: posture% = Σ(scored×weight) / Σ(max×weight) × 100; risk% = 100 − posture%; rating banded against the template's three thresholds. Verified live: a strong-vendor walkthrough (mostly YES + 4-out-of-5 scale answers) computed 17.31% risk → LOW band, matching hand-calc to two decimals.

### Risk-Advanced (commit `e906c0f`) ← LANDED 2026-05-17
The base ERM module already had `risk.risk_assessments` + `risk.risk_treatments` tables (V15) and Java entities + JPA repos, but no service/controller layer. This commit fills them in.
- **`RiskAssessmentService`** — record periodic re-assessments (1..5 likelihood / impact scale). On every save the latest values promote to the parent Risk's `residual_likelihood`/`residual_impact`, which trips the DB-side computed `residual_score`. Correction endpoint mirrors only when the corrected row is still the most-recent assessment.
- **`RiskTreatmentService`** — ISO 31000 treatment types (AVOID / MITIGATE / TRANSFER / ACCEPT) with a PLANNED → IN_PROGRESS → COMPLETED state machine (+ CANCELLED escape). Rule T1: a Risk in TREATMENT status refuses removal of its last live treatment with a clear message.
- **V80 migration**: adds the `completed_at` column the original V15 migration omitted, plus a backfill that stamps existing COMPLETED rows.
- `RiskAdvancedController` exposes the new endpoints under `/api/v1/risk/risks/{id}/assessments` and `/api/v1/risk/risks/{id}/treatments`.
- The existing `RiskAppetiteService` + `KriEngineService` already covered the "appetite" leg of the spec; this commit completes the assessment + treatment legs. Workflow on the parent Risk (submit-for-review, approve, accept, close, reopen, incident-bump) was also already wired by `RiskController`.

---

## ❌ REMAINING — Sprint 2 Completion

### Modules Not Yet Built
All Sprint 2 modules now landed. Remaining items are polish / UI work (frontend for Policy Management, Risk-Advanced, and TPRM questionnaire engine all consume the new endpoints but don't have dedicated tabs yet).

### Gaps in Existing Modules
| Module | Missing |
|--------|---------|
| **Cyber** | File upload for evidence; real-time BCM handoff integration |
| **BCM** | Link BCM plans to cyber incidents; recovery testing results |
| **Privacy** | DPIA integration with risk module; consent analytics |
| **Compliance** | Gap analysis export (PDF/Excel); framework import |
| **Dashboard** | Cyber KPIs missing from main dashboard (links to `/cyber/`) |

---

## ❌ REMAINING — Sprint 3

| Feature | Priority |
|---------|----------|
| **MFA** (TOTP / SMS) | High |
| **Temporal tables** (full audit history on all entities) | High |
| **File upload service** (evidence attachments) | High |
| **Email notifications** (overdue alerts, workflow transitions) | High |
| **PDF/Excel export** (reports, risk register, audit) | High |
| **Licensing module** (org activation, module entitlements) | Medium |
| **Advanced analytics** (trend analysis, predictive risk) | Medium |
| **SSO / SAML integration** | Medium |
| **Production hardening** (rate limiting, WAF headers, secrets vault) | Medium |
| **Mobile-responsive polish** | Low |
| **Dark mode** | Low |

---

## Architecture Notes

```
tss-grc-backend/
  src/main/java/com/tss/grc/
    common/          — tenant context, permissions, base entities
    config/          — security, CORS, Swagger, cache
    module/
      risk/          ✅ Sprint 1
      incident/      ✅ Sprint 1
      compliance/    ✅ Sprint 2
      privacy/       ✅ Sprint 2
      bcm/           ✅ Sprint 2
      tprm/          ✅ Sprint 2
      cyber/         ✅ V2 NEW
      dashboard/     ✅
      admin/         ✅

tss-grc-ui/
  assets/css/        — style.css (CSS vars), rtl.css
  assets/js/         — app.js (shell), auth.js, api.js, i18n.js, translations.js, config.js
  dashboard.html     ✅
  risk/              ✅
  incidents/         ✅
  compliance/        ✅
  privacy/           ✅
  bcm/               ✅
  tprm/              ✅
  cyber/             ✅ V2 NEW
  admin/             ✅
```

### Key Design Patterns
- **Multi-tenant:** Every entity has `organisation_id`; every repo query filters by it via `TenantContext`
- **Soft delete:** `is_deleted` column; `@Where(clause="is_deleted=0")` on all entities
- **i18n:** `titleEn` / `titleAr` on all user-visible entities; frontend reads `I18n.current`
- **Auth:** Stateless JWT (access token in header) + HttpOnly refresh cookie; `@PreAuthorize("hasPermission('MODULE','READ')")`
- **Workflows:** Stored as `workflow_status` string; transitions enforced in Service layer
- **UI shell:** `app.js` injects sidebar + header into every page via `#app-shell` div

---

## How to Continue Development

### Adding a New Module (e.g., Data Governance)
1. Create `tss-grc-backend/src/main/java/com/tss/grc/module/datagovernance/`
   - `entity/` — JPA entities with `organisation_id`, `is_deleted`, bilingual fields
   - `repository/` — JPA repos with `findBy*OrganisationId` queries
   - `service/` — business logic + workflow transitions
   - `controller/` — `@RequestMapping("/api/v1/datagovernance")`, `@PreAuthorize`
2. Create `tss-grc-ui/datagovernance/index.html` — follow `cyber/index.html` pattern
3. Add to `app.js` `NAV_ITEMS` array
4. Add translations to `translations.js` (EN + AR blocks)
5. Create DB setup Python script following `setup_cyber_db.py` pattern
6. Run DB script, rebuild JAR with Maven

### Rebuilding the JAR (requires Maven)
```bash
cd tss-grc-backend
mvn clean package -DskipTests
# jar: target/tss-grc-platform-0.1.0-SNAPSHOT.jar
```

---

