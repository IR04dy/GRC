# TSS GRC Platform — Version 2 Status
**Last update:** 25 May 2026 (Sprint 17 Recovery & Resolution Plan arc closes — PRs #245 → #248, 4 PRs + COI/Complaints DB bootstrap fix #244)

---

## 🟢 This arc — Sprint 17 Recovery & Resolution Plan (25 May 2026, PRs #245 → #248)

Sprint 17 lands the **Recovery & Resolution Plan (RRP)** module — the
SAMA D-SIB mandatory annual planning surface. Every D-SIB and now every
Tier-2/Tier-3 bank under the **SAMA Recovery Planning Rules (2022)** must
lodge an annual RRP. This module is the first platform tool that ties
the four operational catalogs the regulator expects — critical functions,
recovery options, recovery indicators, stress scenarios — into a single
Board-facing workspace, plus a **live, year-round RAG-band engine** that
fires the F8 `RRP_INDICATOR_THRESHOLD_BREACHED` trigger on worsening
crossings only.

### Headline deliveries

| Workstream | Outcome | PRs |
|---|---|---|
| **RRP backend** — 5 entities (cycle / recovery_option / critical_function / indicator / event), 7-state cycle lifecycle, RAG-band engine with HIGHER/LOWER direction handling, 17 REST endpoints, F7+F8 emit on 7 triggers, Python DB bootstrap (5 tables + 6 indexes) | Merged | 1 |
| **F8 automation rules AR-221..AR-230** — cycle kickoff NOTIFY, Board Risk + Audit Committee pack, 5-BD lodge-prep handoff, evidence vault 10-year cross-log, worsening-only indicator escalation (CRO 1h SLA), RED-band Board Risk Chair + Critical CAPA, CAPITAL/LIQUIDITY AMBER → CFO+Treasury same-hour, capacity-drift watch, BCM Manager critical-function review, closed-cycle IA archive | Merged | 1 |
| **Frontend slice** — Dashboard (KPIs + RAG roll-up + recovery capacity + status breakdown) + Cycles register with inline wizard + Cycle workspace with state-machine action panel + 4 tabs (Options / Critical Functions / Indicators with per-row Measure / Timeline) + dedicated Submit / Acknowledge / Reject / Exempt modals + sidebar entry under Audit | Merged | 1 |
| **Saudi-realistic seed** — 5 cycles + 32 recovery options + 46 critical functions + 31 indicators across 3 tenants (TSS GRC full Saudi-bank stack, Aramco Trading CMA-listed, STC Bank digital-bank) with 1 deliberate AMBER on TSS GRC FY26 LCR to demo AR-225 + AR-227 on the next measurement | Merged | 1 |

### Sprint 17 — full inventory

| PR | SHA | Module | Slice | Regulatory hook |
|---:|:---|---|---|---|
| #245 | `merged` | rrp | **Backend module** (5 entities + 17 REST + 7-state lifecycle + RAG-band engine + F8) | SAMA Recovery Planning Rules 2022; D-SIB designation; Basel III Pillar 3 |
| #246 | `merged` | platform | **F8 automation rules AR-221..AR-230** | SAMA early-warning indicators; Board Risk Committee escalation chains |
| #247 | `merged` | rrp (UI) | **Frontend slice** (dashboard + cycles + workspace + sidebar) | Board-facing CRO + CFO + BCM Manager workflow |
| #248 | `merged` | rrp (seed) | **Saudi-realistic seed** (5 cycles + 32 options + 46 CFs + 31 indicators × 3 tenants) | SAMA D-SIB scenario rehearsals; commodity-trading + digital-bank profiles |

### Sprint 17 — Cycle lifecycle (state machine)

```
DRAFT → IN_PREPARATION → BOARD_REVIEW → APPROVED →
SUBMITTED → ACKNOWLEDGED → CLOSED
    ↪ BOARD_REVIEW → IN_PREPARATION (sent back)
    ↪ APPROVED → IN_PREPARATION (last-mile fix)
```

Plus the live, year-round (cycle-status-independent) `RRP_INDICATOR_THRESHOLD_BREACHED` trigger that fires on every RAG-band crossing, with `worsening=true|false` payload set by `RrpService.bandWorseThan` so F8 rules can selectively escalate only on worsening. RED-band crossings are always board-reportable regardless of worsening flag.

### Sprint 17 — 10 F8 automation rules (AR-221..AR-230)

- **AR-221** `RRP_CYCLE_OPENED` → NOTIFY CRO + CFO + BCM Manager + Board Secretary (4-6 month prep kickoff)
- **AR-222** `STATE→BOARD_REVIEW` → NOTIFY Board Risk + Audit Committee members (2-week pack)
- **AR-223** `STATE→APPROVED` → NOTIFY CRO + Compliance + Treasury (5-BD lodge-prep handoff)
- **AR-224** `CYCLE_SUBMITTED` → CREATE_RECORD evidence vault entry, 10-year retention
- **AR-225** `INDICATOR worsening=true` → ESCALATE CRO within 1h
- **AR-226** `INDICATOR bandAfter=RED` → ESCALATE Board Risk Chair + open CRITICAL CAPA
- **AR-227** `INDICATOR AMBER` + category in [CAPITAL, LIQUIDITY] + worsening → NOTIFY CFO + Treasury same-hour (ALCO playbook trigger)
- **AR-228** `OPTION_CAPACITY_CHANGED` → NOTIFY CRO + CFO (capacity-drift watch)
- **AR-229** `CRITICAL_FUNCTION_ADDED` → NOTIFY BCM Manager (BCP/DRP cross-link review)
- **AR-230** `CYCLE_CLOSED` → NOTIFY Internal Audit + Board Secretary (cross-cycle archive)

### Sprint 17 — Saudi-native AR throughout

- **Sidebar**: `الاستعادة والحلّ`
- **Status labels**: مسودّة / قيد الإعداد / مراجعة المجلس / مُعتمَدة / مُقدَّمة / مُستلَمة / مغلقة
- **Option categories**: رأس المال / السيولة / بيع الأصول / خطّ النشاط / خفض المصروفات
- **Indicator categories**: رأس المال / السيولة / الربحيّة / جودة الأصول / ثقة السوق / تشغيليّة
- **RAG bands**: أخضر / كهرماني / أحمر
- **Operator terms**: خطّة الاستعادة والحلّ, الوظائف الحيوية, مؤشّرات الاستعادة, خيارات الاستعادة, طاقة الخيار, زمن التنفيذ, تجاوز نطاق الإنذار, تدوير مفاتيح HSM, محاولات انتحال البصمة الصوتية
- **Reference format**: `RRP-FY2026-001`

### Sprint 17 — verified end-to-end

- Backend `mvn clean package -DskipTests` green (~1413 source files, ~30s, Spring Boot repackage clean)
- Frontend `npm run build` green (vite 7.17s)
- Schema: 5 tables (`rrp.rrp_cycle`, `rrp_recovery_option`, `rrp_critical_function`, `rrp_indicator`, `rrp_event`) + 6 indexes bootstrapped via `setup_rrp_db.py`
- 10 F8 rules visible: `SELECT * FROM platform.automation_rules WHERE rule_code BETWEEN 'AR-221' AND 'AR-230'` returns 10
- Seed: **5 cycles + 32 options + 46 critical functions + 31 indicators** (30 GREEN + 1 AMBER on TSS GRC FY26 LCR drift) — poised to fire AR-225 + AR-227 on the next measurement
- Backend boots: `Started GrcApplication in 24.096 seconds`
- Sprint 13/14/15/16/17 endpoint sweep: **13/13 green** (RRP dashboard + cycles + reginspect + regreporting + opres + fraud + KRI + IA + whistleblower + sanctions + outsourcing + COI + complaints)

### Sprint 17 — operational pattern recap

This arc completes the **regulator-facing daily-ops quartet** that ties together the platform's heaviest annual cycles:

| Module | Cadence | Trigger code prefix | Saudi-native AR |
|---|---|---|---|
| Op-Res (Sprint 14) | Annual maturity self-assessment | `OPRES_*` | الصمود التشغيلي |
| Reg Inspection (Sprint 15) | Reactive — on-site visits | `REG_INSPECTION_*` | التفتيش الرقابي |
| Reg Reporting (Sprint 16) | Recurring statutory returns | `REG_REPORT_*` | تقويم البلاغات |
| **RRP (Sprint 17)** | **Annual mandatory plan + live indicators** | `RRP_*` | **الاستعادة والحلّ** |

All four share the same Saudi-native AR conventions, same F7/F8 wiring pattern, same Python-bootstrapped multi-tenant schema, and the same CRO + Compliance Officer + BCM Manager + Board Secretary role mesh.

---

## 🟢 Previous arc — Sprint 16 Mandatory Regulatory Reporting Calendar (25 May 2026, PRs #239 → #242)

Sprint 16 lands the **Mandatory Regulatory Reporting Calendar** — the
day-to-day operational tool for the 40-100+ recurring statutory returns
every Saudi-licensed firm runs across SAMA, CMA, NCA, SDAIA, ZATCA, NCEMA
at cadences from daily liquidity through monthly capital adequacy through
quarterly directors' dealings through annual PDPL compliance statements.
Until now teams have run that out of Excel + Outlook reminders — this
module turns it into a first-class operational calendar with a 9-state
filing lifecycle, F7 audit + F8 reminders & escalations on 7 trigger codes,
10 automation rules covering regulator-specific routing, and Saudi-realistic
seed for 3 tenants exercising every supported state.

### Headline deliveries

| Workstream | Outcome | PRs |
|---|---|---|
| **RegReporting backend** — 3 entities (Obligation / Filing / Event), 14 REST endpoints, 9-state filing lifecycle, frequency-driven period + reference generation, Saudi-workweek-aware business-day arithmetic, F7+F8 on 7 triggers, Python DB bootstrap (3 tables + 6 indexes) | Merged | 1 |
| **F8 automation rules AR-211..AR-220** — catalog onboarding, T-7/T-3/T-1 reminder ladder, second-line review handoff, CFO signoff, late-filing CAPA + CRO escalation, SAMA evidence-vault cross-log, REJECTED critical escalation, EXEMPTED audit-committee notification, ZATCA tax + treasury cash-positioning, CLOSED IA archive | Merged | 1 |
| **Frontend slice** — Dashboard (KPIs + countdown badges + in-flight breakdown by regulator + by status + upcoming + overdue lists) + Obligations catalog (inline new-obligation wizard) + Filings register + Filing workspace detail (state-machine action panel + dedicated submit/ack/reject/exempt modals + bilingual timeline) + sidebar entry under Audit | Merged | 1 |
| **Saudi-realistic seed** — 13 obligations + 26 filings across 3 tenants. Every supported state exercised (CLOSED, SCHEDULED, ACKNOWLEDGED, SUBMITTED, APPROVED, IN_REVIEW, IN_PREPARATION, REJECTED, EXEMPTED). Per-tenant business profile (TSS GRC full Saudi-bank stack, Aramco Trading CMA listed-co, STC Bank digital-bank + NCA cyber) | Merged | 1 |

### Sprint 16 — full inventory

| PR | SHA | Module | Slice | Regulatory hook |
|---:|:---|---|---|---|
| #239 | `merged` | regreporting | **Calendar backend module** (3 entities + 14 REST + 9-state lifecycle + F8 events) | SAMA daily/monthly/quarterly/annual; CMA listing; NCA OTCC; ZATCA Fatoora; SDAIA PDPL |
| #240 | `merged` | platform | **F8 regreporting automation rules AR-211..AR-220** | SAMA Tier-1 supervisory observations; ZATCA hard 15th-of-month lock; CMA Tadawul same-day cutoff |
| #241 | `merged` | regreporting (UI) | **Frontend slice** (dashboard + obligations + filings + workspace + sidebar) | SAMA / NCA / CMA / SDAIA / ZATCA / NCEMA |
| #242 | `merged` | regreporting (seed) | **Saudi-realistic seed** (13 obligations + 26 filings × 3 tenants) | SAMA Capital Adequacy / AML/CFT 2018 / Anti-Fraud 2018 / Consumer Protection; CMA Continuing Obligations Art.56 / Listing Rules Art.41; ZATCA VAT Art.62; SDAIA PDPL Art.35; NCA OTCC Annex H |

### Sprint 16 — Filing lifecycle (state machine)

```
SCHEDULED → IN_PREPARATION → IN_REVIEW → APPROVED →
SUBMITTED → ACKNOWLEDGED → CLOSED

    ↪ REJECTED  (regulator pushback → IN_PREPARATION)
    ↪ EXEMPTED  (formal waiver, terminal)
```

- **Forward-only on the happy path**, with `IN_REVIEW → IN_PREPARATION` rework loop and `APPROVED → IN_PREPARATION` last-mile fix.
- **Dedicated REST endpoints** for submit (captures ack ref), acknowledge, reject (captures reason), exempt (captures justification + reference) — generic `/transition` excludes those to keep state moves explicit.
- **Saudi-workweek-aware** `addBusinessDays()` skips Friday + Saturday for both due-date and internal-target-date computation.
- **Frequency-driven `nextPeriodEnd()`** + `buildReference()` — `SAMA-DLR-2026-05-21` (daily), `SAMA-MCR-2026-04` (monthly), `CMA-INTL-2026-Q2` (quarterly), `SAMA-ABF-2025` (annual).

### Sprint 16 — 10 F8 automation rules (AR-211..AR-220)

- **AR-211** `REG_REPORT_OBLIGATION_CREATED` → NOTIFY Compliance Officer + owner role + reviewer role
- **AR-212** `REG_REPORT_FILING_SCHEDULED` → NOTIFY preparer + arm T-7/T-3/T-1 reminder ladder
- **AR-213** `REG_REPORT_FILING_STATE_CHANGED` (to = IN_REVIEW) → NOTIFY reviewer (second-line bottleneck)
- **AR-214** `REG_REPORT_FILING_STATE_CHANGED` (to = APPROVED) → NOTIFY approver + CFO (final signoff)
- **AR-215** `REG_REPORT_FILING_SUBMITTED` (late = true) → CREATE_RECORD CAPA + escalate to CRO
- **AR-216** `REG_REPORT_FILING_SUBMITTED` (regulator = SAMA) → CREATE_RECORD evidence vault, 7-year retention
- **AR-217** `REG_REPORT_FILING_REJECTED` → ESCALATE CRO + Board Secretary + open CRITICAL CAPA
- **AR-218** `REG_REPORT_FILING_EXEMPTED` → NOTIFY Audit Committee + Compliance + Internal Audit
- **AR-219** `REG_REPORT_FILING_SCHEDULED` (regulator = ZATCA) → NOTIFY Tax + Treasury + CFO (cash positioning)
- **AR-220** `REG_REPORT_FILING_CLOSED` → NOTIFY Internal Audit + Compliance (cross-cycle archive)

### Sprint 16 — Saudi-native AR throughout

- **Sidebar**: `تقويم البلاغات` (not literal: `التقويم التنظيمي`)
- **Status labels**: مجدوَل / قيد الإعداد / قيد المراجعة / مُعتمَد / مُقدَّم / مُستلَم / مغلق / مرفوض / مُعفى
- **Regulator labels**: ساما / هيئة الأمن السيبراني / هيئة السوق المالية / سدايا / زاتكا
- **Frequency labels**: يومي / أسبوعي / شهري / ربع‑سنوي / نصف‑سنوي / سنوي
- **Operator terms**: التقرير المُلزم, دورة التقديم, رقم إقرار الاستلام, متأخر التقديم, إعفاء رسمي
- **Reference formats**: `SAMA-DLR-2026-05-21`, `CMA-DDIR-2026-Q1`, `ZATCA-VAT-2026-04`, `SAMA-ABF-2025`
- **Seed terminology**: تسوية ضريبة المدخلات والمخرجات, مدفوعات بتسعة خانات تُجوّع سيولة اليوم نفسه, تدوير مفاتيح HSM, محاولات انتحال البصمة الصوتية, خطاب التكليف الرسمي

### Sprint 16 — verified end-to-end

- Backend `mvn clean compile` green (1392 source files, 21.2s)
- Frontend `npm run build` green (vite 7.10s)
- Schema: 3 tables (`regreporting.regreport_obligation`, `regreport_filing`, `regreport_event`) + 6 indexes (unique code/reference, due-date, status, obligation-period, event timeline) bootstrapped via `setup_regreporting_db.py`
- 10 F8 rules visible: `SELECT * FROM platform.automation_rules WHERE rule_code BETWEEN 'AR-211' AND 'AR-220'` returns 10 rows
- Seed: 13 obligations + 26 filings exercising **all 9 lifecycle states** including REJECTED + EXEMPTED edge cases

---

## 🟢 Previous arc — Sprint 15 Regulatory Inspection Workspace (25 May 2026, PRs #234 → #237)

Sprint 15 opens with the **Regulatory Inspection Workspace** — the platform's
first day-to-day operational tool for managing on-site visits by Saudi regulators
(SAMA, NCA, CMA, SDAIA, NCEMA). Every Saudi bank gets a SAMA on-site every 12-18
months; until now teams ran them out of spreadsheets and SharePoint. This module
brings the full inspection lifecycle into the platform with state machine, SLA
tracking, document-request register, timeline audit trail, F7 audit + F8 events
on 5 trigger codes, 10 automation rules, and Saudi-realistic seed for 3 tenants.

### Headline deliveries

| Workstream | Outcome | PRs |
|---|---|---|
| **RegInspect backend** — 3 entities (Inspection / DocumentRequest / Update), 10 REST endpoints, 6-state forward-only inspection lifecycle, DR sub-lifecycle with computed OVERDUE, F7+F8 audit/events on 5 triggers, Python DB bootstrap (3 tables + 6 indexes) | Merged | 1 |
| **F8 automation rules AR-201..AR-210** — kickoff NOTIFY, regulator-specific routing (NCA→CISO, CMA→IR+Legal), DR 24h-before-due reminder, overdue submission → CAPA, ENTRY/EXIT/RESPONSE transition handoffs, closure → Internal Audit | Merged | 1 |
| **Frontend slice** — Dashboard (KPIs + recent inspections) + Register (with inline new-inspection wizard) + Workspace detail (status-aware action panel + DR inbox with submit modal + timeline) + sidebar under Audit | Merged | 1 |
| **Saudi-realistic seed** — 5 inspections + 28 DRs across 3 tenants. Per-tenant business profile content (TSS GRC SAMA cyber+AML, Aramco Trading CMA listing, STC Bank NCA OTCC + SAMA dual oversight) | Merged | 1 |

### Sprint 15 — full inventory

| PR | SHA | Module | Slice | Regulatory hook |
|---:|:---|---|---|---|
| #234 | `merged` | reginspect | **Workspace backend module** (3 entities + 10 REST + state machine + F8 events) | SAMA on-site inspections; NCA OTCC; CMA listing audits |
| #235 | `merged` | platform | **F8 reginspect automation rules AR-201..AR-210** | SAMA SLA 3-5d; regulator-specific cross-team routing |
| #236 | `merged` | reginspect (UI) | **Frontend slice** (dashboard + register + workspace detail + sidebar) | SAMA / NCA / CMA / SDAIA / NCEMA |
| #237 | `merged` | reginspect (seed) | **Saudi-realistic seed** (5 inspections + 28 DRs × 3 tenants) | SAMA Cyber 2017, AML/CFT 2018, Op-Res 2024, CMA Listing, NCA OTCC |

### Sprint 15 — Inspection lifecycle (state machine)

```
PLANNED → ENTRY_MEETING → FIELDWORK → EXIT_MEETING →
RESPONSE_DRAFTING → CLOSED
```

Forward-only, with shortcut to CLOSED from any state (abandoned inspections / consolidations).

Document request sub-lifecycle:

```
OPEN → IN_PROGRESS → SUBMITTED
                  ↘ OVERDUE (computed when status != SUBMITTED AND dueDate < today)
```

### Sprint 15 — 10 F8 automation rules (AR-201..AR-210)

- **AR-201** `REG_INSPECTION_CREATED` → NOTIFY CRO + Compliance + Board Secretary
- **AR-202** `REG_INSPECTION_CREATED` (regulator = NCA) → NOTIFY CISO + Cyber Incident Responder
- **AR-203** `REG_INSPECTION_CREATED` (regulator = CMA) → NOTIFY Investor Relations + Legal
- **AR-204** `REG_INSPECTION_TRANSITIONED` (to = ENTRY_MEETING) → readiness pack notification
- **AR-205** `REG_DOCUMENT_REQUEST_OPENED` → NOTIFY assignee + 24h-before-due reminder
- **AR-206** `REG_DOCUMENT_REQUEST_SUBMITTED` → NOTIFY Compliance Officer (confirmation + evidence log)
- **AR-207** `REG_DOCUMENT_REQUEST_SUBMITTED` (overdue = true) → CREATE_RECORD CAPA root-cause
- **AR-208** `REG_INSPECTION_TRANSITIONED` (to = EXIT_MEETING) → NOTIFY 48h response-drafting kickoff
- **AR-209** `REG_INSPECTION_TRANSITIONED` (to = RESPONSE_DRAFTING) → NOTIFY response coordinator
- **AR-210** `REG_INSPECTION_CLOSED` → NOTIFY Internal Audit + Compliance (annual-plan input)

### Sprint 15 — Saudi-native AR throughout

- **Sidebar**: "التفتيش الرقابي" (not literal: "التفتيش التنظيمي")
- **Status labels**: مخطّطة / اجتماع الافتتاح / العمل الميداني / اجتماع الإغلاق / صياغة الردود / مغلقة
- **Regulator labels**: ساما / هيئة الأمن السيبراني / هيئة السوق المالية / سدايا / الهيئة الوطنية لإدارة الطوارئ
- **External reference format**: `SAMA-OSI-2026-Q2-001` (regulator + on-site inspection + year + quarter + ordinal)
- **DR reference format**: `<inspection-ref>-DR-001`
- **Seed terminology**: حماية أموال المحفظة الجوّالة, مكتبة قواعد مراقبة معاملات غسل الأموال, وحدة الأجهزة الأمنية, احتيال تبديل شريحة الاتصال, خطاب التكليف الرسمي

### Sprint 15 — verified end-to-end

- Backend boots clean in 19.9s with new module
- `GET /api/v1/reginspect/pillars` → 5 SAMA pillars (Op-Res arc, still working)
- Full inspection lifecycle: PLANNED → ENTRY_MEETING → FIELDWORK + 3 DRs → … → CLOSED. State machine forward-only, OVERDUE computed correctly
- Frontend `npm run build` clean in 7.1s, 3 new chunks
- 10 F8 rules visible in `platform.automation_rules` (AR-201..AR-210)
- Seed: 5 inspections + 28 DRs visible via `GET /reginspect/dashboard` for admin (org=1) — closed FY2025 + active FY2026

---

## 🟢 Previous arc — Sprint 14 Operational Resilience block (25 May 2026, PRs #229 → #232)

Sprint 14 lands the **Operational Resilience self-assessment** workbench for the
SAMA Operational Resilience Framework (Sept 2024) — banks must publish an annual
maturity self-assessment across 5 pillars, and this module is where they author,
sign off, and close that submission inside the platform. ~2,100 lines across
backend (module/opres/), frontend (3 lazy-loaded pages with maturity heatmap +
cell editor), 10 F8 automation rules (AR-191..AR-200), and Saudi-realistic seed
data for 3 tenants × 2 fiscal cycles. Saudi-native bilingual EN+AR throughout.

### Headline deliveries

| Workstream | Outcome | PRs |
|---|---|---|
| **OpRes backend** — 3 entities (Pillar / Assessment / AssessmentCell), 9 REST endpoints, state machine (DRAFT→IN_PROGRESS→APPROVED→CLOSED), aggregate scoring, maturity-gap event emission | Merged | 1 |
| **F8 automation rules** — AR-191..AR-200: kickoff NOTIFY, auto-CAPA on gap≥2, GOVERNANCE→Board Secretary, TOLERANCES→Risk+BCM, MAPPING→severe-but-plausible exercise, SUBMITTED→CRO+BoardSec sign-off, low-maturity FLAG, APPROVED→SAMA Liaison Officer filing, high-maturity board pack milestone, CLOSED→Internal Audit notify | Merged | 1 |
| **Frontend slice** — `/opres/dashboard` heatmap + `/opres/assessments` register + `/opres/assessments/:id` cell editor with inline maturity rubric + sidebar entry under BCM + bilingual EN+AR | Merged | 1 |
| **Seed data** — 3 tenants × 2 FY cycles (FY2024 CLOSED with full lifecycle + exec summary, FY2025 IN_PROGRESS partial fill); Saudi-bank-realistic per-tenant content (TSS GRC top-quartile, Aramco Trading energy+SCADA, STC Bank telco-banking) | Open (CI) | 1 |

### Sprint 14 OpRes — full inventory

| PR | SHA | Module | Slice | Regulatory hook |
|---:|:---|---|---|---|
| #229 | `merged` | opres | **OpRes backend** (3 entities + 3 repos + 9 REST + state machine + gap-event emission) | SAMA Op-Res §3.2 / §4.1 / §4.3 / §5 / §6 |
| #230 | `merged` | platform | **F8 OpRes automation rules AR-191..AR-200** | SAMA Op-Res 2024 |
| #231 | `merged` | opres (UI) | **Frontend slice** (dashboard + register + cell editor + heatmap + sidebar) | SAMA Op-Res 2024 |
| #232 | — | opres (seed) | **Saudi-realistic seed** (6 assessments + 21 cells across 3 tenants × 2 FY) | SAMA Op-Res 2024 |

### Sprint 14 OpRes — 5 SAMA pillars seeded

Each pillar carries bilingual EN+AR description, a `samaReference` (e.g. §3.2),
and a 5-level maturity rubric stored as JSON. The frontend renders the rubric
inline in the cell editor so operators can pick a score against the actual SAMA
expectation, not generic CMMI language:

| Code | EN name | AR name | SAMA ref |
|---|---|---|---|
| `GOVERNANCE` | Governance & Strategy | الحوكمة والاستراتيجية | §3.2 |
| `IBS_MAPPING` | Important Business Services | الخدمات التجارية المهمّة | §4.1 |
| `TOLERANCES` | Impact Tolerances | حدود التحمّل | §4.3 |
| `MAPPING_TESTING` | Resource Mapping & Testing | الربط والاختبار | §5.1-5.4 |
| `LESSONS` | Lessons & Management Action | الدروس والإجراءات الإدارية | §6 |

The maturity ladder uses operator-natural Saudi Arabic, not literal translation:
**مبدئي** (Initial) → **قيد التطوير** (Developing) → **محدّد** (Defined) →
**مُدار** (Managed) → **مُحسَّن** (Optimising).

### Sprint 14 OpRes — 10 F8 automation rules (AR-191..AR-200)

- **AR-191** `OPRES_ASSESSMENT_CREATED` → NOTIFY CRO + BCM Manager (annual-cycle kickoff)
- **AR-192** `OPRES_PILLAR_GAP` + gap ≥ 2 → CREATE_RECORD CAPA task (HIGH priority — concrete plan item, not monitoring finding)
- **AR-193** `OPRES_PILLAR_GAP` + pillar = GOVERNANCE → NOTIFY Board Secretary + Risk Manager (board-level concern)
- **AR-194** `OPRES_PILLAR_GAP` + pillar = TOLERANCES → NOTIFY Risk Manager + BCM Manager (foundational gap — no severe-but-plausible has meaning until tolerances quantified)
- **AR-195** `OPRES_PILLAR_GAP` + pillar = MAPPING_TESTING → CREATE_RECORD BCM_EXERCISE (severe-but-plausible) — only practical exercise closes a mapping/testing gap
- **AR-196** `OPRES_ASSESSMENT_SUBMITTED` → NOTIFY CRO + Board Secretary (sign-off slot in risk committee)
- **AR-197** `OPRES_ASSESSMENT_SUBMITTED` + avgCurrent < 3 → FLAG executive review before regulator filing (below SAMA expectation for a domestically systemic bank)
- **AR-198** `OPRES_ASSESSMENT_APPROVED` → NOTIFY SAMA Liaison Officer + Compliance (regulator filing — 30-day clock starts on board sign-off)
- **AR-199** `OPRES_ASSESSMENT_APPROVED` + avgCurrent ≥ 4 → CREATE_RECORD board-pack item (success milestone — programme gets visible credit)
- **AR-200** `OPRES_ASSESSMENT_CLOSED` → NOTIFY Internal Audit + Compliance (terminal — archive reference needed for IA annual plan + SAMA on-site inspection)

### Sprint 14 OpRes — verified end-to-end

- Backend boots clean in 19s; `GET /api/v1/opres/pillars` → 5 rows
- Full lifecycle: open assessment (DRAFT, completeness 0/5) → upsert 5 cells (auto IN_PROGRESS, avgCur=2.2, avgTgt=3.2, completeness 5/5, gap events fired) → submit (APPROVED, submittedAt set) → approve (approvedByUserId=1) → close (CLOSED, closedAt set)
- Frontend `npm run build` clean in 7.0s, 3 new chunks under 25kb each gzip
- F8 rules: 10 rows in `platform.automation_rules` (AR-191..AR-200), trigger codes match emitted events
- Seed: 6 assessments + 21 cells across 3 tenants, REST returns FY2024 CLOSED + FY2025 IN_PROGRESS for admin/org=1

---

## 🟢 Previous arc — Sprint 14 Fraud Risk Management block (25 May 2026, PRs #222 → #226)

Sprint 14 lands a complete end-to-end **Fraud Risk Management** module
closing the SAMA Anti-Fraud Framework 2018 (Art.4-9) gap that the platform
had carried since day one. Plus six Spring boot-time wiring fixes that
unblocked everyone trying to run the app on a fresh checkout after Sprint
13's two duplicate-classname repositories tipped Spring into strict-mode
failure. ~3,700 lines, ten Saudi-bank ops automation rules, twelve seeded
fraud typologies, full bilingual Saudi-native Arabic throughout.

### Headline deliveries

| Workstream | Outcome | PRs |
|---|---|---|
| **Boot stability** — 6 Spring wiring conflicts (bean-name + entity-name + multi-constructor) + dev-profile `allow-bean-definition-overriding` guardrail | Merged | 1 |
| **Fraud backend** — 3 entities, 10 REST endpoints, 12 Saudi typologies, F7 audit + F8 events on 9 trigger codes, Python DB bootstrap | Merged | 1 |
| **Fraud frontend** — Dashboard (5 KPIs) + Cases register (filterable + paginated) + Case detail (2-column with status-aware action panel + timeline) + sidebar entry + bilingual EN+AR | Merged | 1 |
| **F8 automation rules** — AR-181..AR-190: NOTIFY Fraud Officer / ESCALATE to CEO+AC / CREATE_RECORD Incident+CAPA+LessonsLearned / FLAG legal-hold / cross-team CISO+HR routing | Merged | 1 |
| **Intake wizard** — `/fraud/cases/new` page with typology summary card (auto-rendered after select) + EN+AR subject/narrative + detection source + loss + customer impact | Open (CI) | 1 |

### Sprint 14 — full inventory

| PR | SHA | Module | Slice | Regulatory hook |
|---:|:---|---|---|---|
| #222 | `59549a2` | platform | **Boot-stability sweep** (6 wiring bugs + YAML guardrail) | — |
| #223 | `7fcb497` | fraud | **Fraud Risk Management backend** (typology + case + timeline + dashboard endpoints) | SAMA Anti-Fraud Framework 2018 Art.4-9 |
| #224 | `ed69d8d` | fraud | **Fraud Risk Management frontend** (dashboard + register + detail + lib + routes + sidebar) | SAMA Anti-Fraud Framework 2018 |
| #225 | `107ca14` | platform | **F8 fraud automation rules AR-181..AR-190** | SAMA Anti-Fraud Art.7 + AML/CFT Art.25 STR + Cyber Sub-domain 3.3.13 |
| #226 | — | fraud | **Case intake wizard** at `/fraud/cases/new` | SAMA Anti-Fraud Framework 2018 |

### Sprint 14 — 12 seeded Saudi-bank fraud typologies

Each typology carries a SAMA category code, perpetrator origin, primary
channel, typical severity, STR-required flag, and full bilingual
description that uses **Saudi-native Arabic** (operator-natural wording, not
literal translation). The dropdown surfaces these in the intake wizard so
the Fraud Officer picks the right one at case open:

| Code | EN name | SAMA category | Typical severity | STR? |
|---|---|---|---:|:---:|
| `BEC` | Business Email Compromise | CYBER | HIGH | ✓ |
| `ATM_SKIMMING` | ATM Card Skimming | CARD | MEDIUM | ✓ |
| `PUSH_PAYMENT` | SADAD/IPS Push-Payment Fraud | PAYMENT | HIGH | ✓ |
| `ACCOUNT_TAKEOVER` | Account Takeover (ATO) | CYBER | HIGH | ✓ |
| `INTERNAL_EMBEZZLEMENT` | Internal Embezzlement | INTERNAL | CRITICAL | ✓ |
| `CARD_NOT_PRESENT` | Card-Not-Present (CNP) Fraud | CARD | MEDIUM | — |
| `LOAN_APPLICATION` | Loan Application Fraud | EXTERNAL | HIGH | — |
| `WIRE_TRANSFER` | Wire Transfer Fraud (SWIFT MT103) | PAYMENT | CRITICAL | ✓ |
| `INVOICE_FRAUD` | Invoice Redirection Fraud | CYBER | HIGH | ✓ |
| `INSIDER_TRADING` | Insider Trading | INTERNAL | CRITICAL | ✓ |
| `MONEY_MULE` | Money Mule Account | EXTERNAL | HIGH | ✓ |
| `PHISHING_VISHING` | Phishing / Vishing | CYBER | MEDIUM | — |

### Sprint 14 — 10 F8 automation rules (AR-181..AR-190)

Each rule encodes one Saudi-bank ops convention so the platform reacts
automatically instead of relying on operator memory:

- **AR-181** `FRAUD_CASE_CREATED` + severity in (CRITICAL, HIGH) → **NOTIFY** Fraud Officer + CRO (SAMA Anti-Fraud Art.7 escalation channel)
- **AR-182** `FRAUD_CASE_CREATED` + estLoss > SAR 1M → **ESCALATE** to CEO + Audit Committee Chair (SAMA quarterly mandatory-reporting threshold)
- **AR-183** `FRAUD_CASE_CREATED` + affectedCustomerCount > 100 → **CREATE_RECORD** Incident (mass-customer-impact = operational incident)
- **AR-184** `FRAUD_CASE_CONFIRMED` → **NOTIFY** Board Secretary (SAMA CG Art.6 — material control failure)
- **AR-185** `FRAUD_CASE_CONFIRMED` + confirmedLoss > SAR 500k → **CREATE_RECORD** CAPA (material loss = control gap)
- **AR-186** `FRAUD_STR_FILED` → **NOTIFY** MLRO + Compliance Officer (AML/CFT Art.25)
- **AR-187** `FRAUD_LAW_ENFORCEMENT_NOTIFIED` → **FLAG** legal-hold on case (CID/MHRSD/Public Prosecution evidentiary integrity)
- **AR-188** `FRAUD_CASE_CONFIRMED` + typology = INTERNAL_EMBEZZLEMENT → **NOTIFY** Head of HR + Internal Audit + Compliance (parallel Saudi Labour Law disciplinary track)
- **AR-189** `FRAUD_CASE_CREATED` + samaCategory = CYBER → **NOTIFY** CISO + Cyber Incident Responder (SAMA Cyber 3.3.13 cross-team)
- **AR-190** `FRAUD_CASE_CLOSED` + priorStatus = CONFIRMED → **CREATE_RECORD** LessonsLearned (highest-signal training data)

### Sprint 14 — boot fixes that unblocked the build

PR #222 surgically resolved six Spring boot-time wiring failures that
Sprint 13's last two PRs introduced via duplicate repository class-names:

| Fix | Component | Conflict | Resolution |
|---|---|---|---|
| 1 | `ethics/repository/ScreeningHitRepository` | `BeanDefinitionOverrideException` vs Sprint 13 `aml/sanctions` ScreeningHit repo | `@Repository("ethicsScreeningHitRepository")` qualifier |
| 2 | `risk/repository/KriDefinitionRepository` | Bean collision vs Sprint 13 `risk/kri` repo (wrong-type injection caused `UnsatisfiedDependencyException`) | `@Repository("legacyKriDefinitionRepository")` qualifier |
| 3 | `risk/entity/KriDefinition` | Hibernate two `@Entity` classes sharing simple name `KriDefinition` | `@Entity(name = "LegacyKriDefinition")` |
| 4 | `ethics/entity/ScreeningHit` | Same vs `aml/sanctions/entity/ScreeningHit` | `@Entity(name = "EthicsScreeningHit")` |
| 5 | `ccm/runner/LdapGroupMembershipRunner` | Two ctors (production + test seam), no `@Autowired` → falls back to non-existent default ctor | Explicit `@Autowired` on production ctor |
| 6 | `tprm/.../VendorBcpCampaignService` | `@RequiredArgsConstructor` + manual ctor → two candidates → falls back to non-existent default | Removed `@RequiredArgsConstructor`, kept manual ctor |

Plus `application-dev.yml` got `spring.main.allow-bean-definition-overriding: true` as a defensive guardrail for the 10 known duplicate-classname repo pairs (tech debt: rename each pair properly so this flag can come back off).

### Verified end-to-end on dev

- Backend boots in 19.7s with 0 ERROR lines
- `GET /actuator/health` → `200 {"status":"UP"}`
- `POST /api/auth/login` (admin / Admin@12345) → `200` with valid JWT (20 enabled_modules in claims)
- `GET /api/v1/fraud/typologies` → 12 rows
- `POST /api/v1/fraud/cases` (BEC, SAR 4.2M wire request) → `FR-2026-05-0001` opened DETECTED, severity HIGH
- Full lifecycle exercised: triage → INVESTIGATING (severity bumped CRITICAL) → outcome CONFIRMED → close. Timeline returned 4 rows in reverse chronological order. Dashboard aggregated counts + YTD loss correctly.
- All 10 F8 rules visible in `platform.automation_rules` with correct trigger event values

---

## 🟢 Previous arc — Sprint 13 GRC governance block (25 May 2026, PRs #211 → #220)

Sprint 13 lands 8 new Saudi-bank-grade governance modules plus 2 frontend slices —
all merged to `origin/master` in one session. The arc completes the GRC governance
layer the platform was missing: approval chains, KRI breach engine, internal-audit
annual planning, ethics hotline, sanctions screening, outsourcing register,
conflict-of-interest declarations, and SAMA-compliant customer-complaints
management. Per-layer commit checkpoints (commit → push after each slice)
became the new agent default this arc — adopted after two earlier 529-Overloaded
losses; once enforced, the pattern survived every subsequent agent failure.

### Headline deliveries

| Workstream | Outcome | PRs |
|---|---|---|
| **Sprint 13 backend modules** — 8 new modules covering SAMA CG / NCA / CMA / SDAIA / FATF / IIA 2020 governance gaps | All merged | 8 |
| **Sprint 13 frontend slices** — COI Register UI (3 tabs + detail) + Customer Complaints UI (internal triage + 2 public unauthenticated pages) | All merged | 2 |

### Sprint 13 — full inventory

| PR | SHA | Module | Slice | Regulatory hook |
|---:|:---|---|---|---|
| #211 | `d9d79d3` | workflow | Multi-step **Approval Chains** | NCA ECC-2 governance |
| #212 | `8f5b8a9` | risk | **Key Risk Indicator (KRI) engine** + breach alerts | SAMA Risk Mgmt Reg |
| #213 | `6f34655` | audit | **Internal Audit Annual Plan + Engagement** workflow | IIA Standards 2020 |
| #214 | `e84a86e` | ethics | **Whistleblower / Ethics Hotline** (token-based anonymous) | SAMA CG Art. 25 |
| #215 | `e63dd48` | aml | **Sanctions Screening** engine + hit triage (Arabic harakat-stripping fuzzy match) | FATF Rec 5/6/7 |
| #216 | `913a1f3` | tprm | **Outsourcing Arrangement Register** | SAMA Outsourcing Reg |
| #217 | `e291db4` | ethics | **Conflict of Interest Disclosure Register** backend | SAMA CG Art. 30 + CMA Art. 88 |
| #218 | `020ff31` | consumer | **Customer Complaints Management** backend (`SaudiWorkdayUtil` + 9-state lifecycle + PII-safe public tracking) | SAMA Banking Consumer Protection (2021) |
| #219 | `613e825` | ui | **COI Register** frontend (3 tabs + Campaign Detail with review form) | — |
| #220 | `23427d3` | ui | **Customer Complaints** frontend (internal triage + 2 public unauthenticated pages) | — |

### Saudi-native Arabic strings — operator-natural, never literal

The arc adds **~60 new AR strings** across both new modules. Examples:

| EN | AR (Saudi-native, NOT literal) |
|---|---|
| Conflict of Interest | تضارب المصالح |
| Annual Declaration | إقرار سنوي |
| Recusal | تنحٍّ |
| Suspected conflict — recusal required | اشتباه في تضارب — يستوجب التنحّي |
| Customer Complaints Management | إدارة شكاوى العملاء |
| Tracking code | رمز التتبّع |
| Escalated to SAMA | مُحالة إلى ساما |
| Acknowledgement SLA breached | تجاوز مدة الإقرار |
| Five working days | خمسة أيام عمل |
| Fraud / unauthorised transaction | احتيال أو معاملة غير مصرّح بها |
| Tawarruq early settlement (case seed) | نزاع على رسوم سداد مبكر لعقد تورّق |

### Real-world Saudi-bank seed data

| Migration | Seed |
|---|---|
| V192 approval_chains | 4 tables in `workflow` |
| V193 kri_engine | 15 seeded KRIs (credit, liquidity, ops, market) |
| V194 ia_annual_plan | 4 tables in `audit_lc` (IIA-aligned) |
| V195 whistleblower_hotline | 3 tables + token-based anonymous tracking |
| V196 sanctions_screening | 4 tables in `aml` + harakat-stripping fuzzy match |
| V197 outsourcing_register | 4 tables in `tprm` |
| V198 coi_register | 4 tables in `ethics` + 1 campaign `COI-2026-ANNUAL` + 8 Saudi-named board declarations + 12 disclosed items + 3 recusal events tied to real committee codes |
| V199 customer_complaints | 3 tables in new `consumer` schema + **12 realistic Saudi-bank complaints** (Turkey card fraud, SARIE delay for Hajj, Riyadh airport ATM short-disburse, mada Pay TPS, Tawarruq early settlement, SAMA-escalated case) + 1 Q4-2025 aggregate report |

### Auto-flag + state-machine policy depth (SME judgment, not literal mapping)

- **COI auto-flag rules** — `RECUSAL_REQUIRED` if any active item is `RELATED_PARTY` / `FAMILY_RELATIONSHIP` / `POLITICAL_OFFICE` OR `financial_value_sar >= 100,000`; `MITIGATION_REQUIRED` if any active item is `DIRECTORSHIP` / `OUTSIDE_EMPLOYMENT` OR value >= SAR 50,000.
- **Complaints 9-state lifecycle** — RECEIVED → ACKNOWLEDGED → UNDER_INVESTIGATION → AWAITING_CUSTOMER / RESOLVED / ESCALATED_TO_SAMA / CLOSED / REOPENED / REJECTED. Invalid transitions throw `IllegalTransitionException` → HTTP 409.
- **`SaudiWorkdayUtil`** — `addBusinessDays(start, 5)` for ack SLA, `addBusinessDays(start, 20)` for resolution SLA, skipping Friday + Saturday (Saudi weekend, not Sat+Sun).
- **Public submission PII safety** — `PublicTrackingResponse` returns ONLY initials + status + customer-visible timeline; reflection test asserts zero phone/email/national-ID/account fields ever leak.

### Per-layer commit checkpoint pattern (new agent default)

Two earlier retry attempts at COI + Complaints hit `API 529 Overloaded` after
~30 minutes each and lost ALL work — agents had only committed at the end.
Fix: every Sprint 13 backend agent committed + pushed after each of 7-8
layers (migration → entity → repo → DTO → service → controller → tests).
The pattern survived every subsequent failure and is now baked into the
agent operating instructions.

### F7 audit + F8 automation parity arc

Every new mutation in Sprint 13 emits an audit-log row (F7) and an
automation event (F8) via the existing `AuditLogService` + Spring
`ApplicationEventPublisher`. New event types include:
`COI_CAMPAIGN_OPENED`, `COI_DECLARATION_SUBMITTED`, `COI_AUTO_FLAGGED_RECUSAL`,
`COI_AUTO_FLAGGED_MITIGATION`, `COI_RECUSAL_RECORDED`, `COMPLAINT_RECEIVED`,
`COMPLAINT_ACKNOWLEDGED`, `COMPLAINT_TRANSITIONED`, `COMPLAINT_ESCALATED_TO_SAMA`,
`COMPLAINT_REOPENED`, plus the events emitted by the 6 earlier Sprint 13 modules.

### Pre-existing arc (Sprint 11 + 12 — see below)

The arc directly preceding this one delivered 38 PRs (#168 → #207) across
Sprint 11 P1 GRC and Sprint 12 Integrations + Polish — captured in detail
in the next section.

---

## 🟢 Previous arc — Sprint 11 P1 GRC + Sprint 12 Integrations + Polish (24 May 2026, PRs #168 → #207)

The single largest arc shipped in V2 to date: **38 PRs merged** in one session,
taking the platform from Sprint-8 P0 closeout (PR #167) to a Sprint 11 P1 GRC
block delivered in full, Sprint 12 SOC-integration adapters + UX polish, the
Open Issues Register fully closed (12 → 0), and a three-batch design-system
migration eliminating ~2200 inline styles. Every PR is on `origin/master`.

### Headline deliveries

| Workstream | Outcome | PR count |
|---|---|---:|
| **Sprint 11 P1 GRC block — 12+ slices** spanning BCM, Risk, Privacy, AI Governance, Climate, Compliance, Cyber, Audit, Governance | All slices merged | 14 |
| **Sprint 12 Integrations + Polish** — 4 SOC-tool bridge adapters (ServiceNow / Splunk / CrowdStrike / Tenable), Embedded Contextual Help (KB v2), Saudi-native AR i18n audit (75 strings) | All slices merged | 6 |
| **Open Issues Register fully closed (12 → 0)** — security, ops, compliance, branding | 3 batch PRs | 3 |
| **Design-system migration** — 12 pages migrated, ~2200 inline styles eliminated, reusable utility classes added | 3 batches | 4 |
| **BCM Sprint 9/10 slices** — dependency cascade, DR trending, quantitative BIA, continuity ROI, crisis comms expansion, mobile crisis room, exercise scenario library + runtime + observer dashboard, BCP binder PDF, vendor MTPD inheritance | 11 slices | 11 |
| **SOC ops baseline + STATUS-keeping + fixes** — Saudi-native SOC seed, mojibake repair, auth-check repair, controller bean rename | | 4 |

### Sprint 11 P1 GRC block — full inventory

| PR | SHA | Module | Slice |
|---:|:---|---|---|
| #178 | `fe667b5` | bcm | 9-C — quantitative Business Impact Analysis |
| #179 | `453a8d1` | risk | Risk Bow-Tie Analysis (ISO 31010) |
| #181 | `4f32920` | bcm | 9-D — continuity strategy ROI analysis |
| #182 | `5e1cbde` | governance | Three Lines of Defense dashboard (IIA 2020) |
| #195 | `2b41624` | ai-governance | AI/ML Model Registry (SDAIA AI Ethics) |
| #198 | `6865829` | compliance | Obligation Register + Periodic Attestation |
| #199 | `d68332f` | ccm | Continuous Control Monitoring engine |
| #200 | `9302005` | esg | TCFD Climate Scenarios + Scope-3 Financed Emissions (CMA IFRS S2) |
| #201 | `21d96aa` | audit-committee | Audit Committee Dashboard (CMA Art. 14) |
| #202 | `fe705af` | cyber | Attack Surface Management register |
| #203 | `ac3183e` | cyber | Risk-Based Vulnerability SLA engine |
| #204 | `f3e2c91` | privacy | PDPL sub-processor tracking (Art. 29 + 30) |

Plus BCM Sprint 9 + 10 slices (continuity exercise + invocation runtime backbone):

| PR | SHA | Module | Slice |
|---:|:---|---|---|
| #170 | `0948580` | bcm | 9-A — dependency cascade simulator |
| #171 | `d9cfe87` | bcm | 9-B — DR test trending service (rolling quarters) |
| #183 | `da6f77a` | bcm | 10-A — exercise scenario library + scenario→exercise wizard |
| #185 | `dc39d3e` | bcm | 9-E — Crisis Comms Template Expansion |
| #186 | `acf0d52` | bcm | 9-F — mobile-optimised Crisis Room + Web Push registry |
| #187 | `36bf2b9` | bcm | 10-B — inject-driven exercise runtime + facilitator console |
| #189 | `10b5f2b` | tprm | 10-D — vendor BCP attestation campaigns |
| #190 | `5cf626b` | bcm | 10-F — offline BCP/DRP binder PDF generator |
| #191 | `00f7091` | bcm | 10-C — Observer Dashboard for live exercises |
| #192 | `24f5e93` | bcm | 10-E — vendor MTPD inheritance check |

### Sprint 12 Integrations + Polish — full inventory

| PR | SHA | Module | Deliverable |
|---:|:---|---|---|
| #177 | `90a14ab` | integrations | Slice 1 — ServiceNow ITSM bridge adapter |
| #180 | `096cf5d` | integrations | Slice 2 — Splunk SIEM bridge adapter |
| #184 | `b990277` | integrations | Slice 3 — CrowdStrike Falcon EDR bridge adapter |
| #188 | `3bc3300` | integrations | Slice 4 — Tenable.io / Tenable.sc VM bridge adapter |
| #206 | `8349de2` | platform | Embedded contextual help (KB v2 — every page) |
| #207 | `97d18db` | i18n | Saudi-native AR audit pass on `ar.ts` (75 strings) |

### Open Issues Register fully closed (12 → 0)

The Open Issues Register at `docs/roadmap/04-open-issues-register.md`
went from 12 outstanding items to 0 across three batch PRs. The register
is now retained as the audit-trail / resolution citation index.

| Batch | PR | Issues closed | Theme |
|---|---:|---|---|
| 1 | #176 (`33103d1`) | #003 fonts, #007 email templates, #011 nginx, #012 cross-border safeguards | Ops + content + legal |
| 2 | #193 (`0b18a5a`) | #001 JWT rotation, #004 DB-size monitor, #008 MFA library, #010 brand placeholders | Security + ops + design |
| 3 | #196 (`d0aadd0`) | #002 refresh-token revocation, #005 SAMA CSF 140 controls, #006 SDAIA breach workflow, #009 LDAP/AD guide | Security + compliance + IAM |

Highlights from the closeout:
- **Refresh-token compromise window**: 7 days → < 1 second (PR #196 — V181 `platform.refresh_token` table + rolling rotation + revoke-by-jti)
- **SAMA CSF 140 controls seeded** end-to-end (PR #196 — V182 migration, 4 domains × 23 subdomains, bilingual `requirement_text_{en,ar}`)
- **JWT rotation runbook** — 90-day cadence with 24h overlap window, NCA ECC-2 2-3 + SAMA CSF 3.3.1 anchored (PR #193)
- **SDAIA breach workflow** — V181 columns + Article 31 72h clock + state machine (PR #196)
- **LDAP/AD guide** — `LdapRoleMapper` + 6-row troubleshooting matrix + Saudi-bank deployment notes (PR #196)

### Design-system migration — 3 batches

| Batch | PR | Pages migrated | Inline styles eliminated |
|---|---:|---|---:|
| 1 | #173 (`65cbb97`) | CrisisRoomPage + new reusable layout/typography utilities | ~600 |
| 2a | #194 (`9f8493f`) | EsgPage / NdiPage / AuditWorkspacePage | ~800 |
| 2b | #197 (`9799fbf`) | CapaPage / BcmLifecyclePage / RiskLifecyclePage / CyberLifecyclePage | ~600 |
| 3 | #205 (`1da6554`) | 4 pages from inline styles to design tokens | ~200 |
| **Total** | | **12 pages** | **~2200** |

After this arc, every `Page.tsx` consumer has converged onto the shared
design-token CSS (variables + utility classes) — future polish lands once
in `shell.css` / design tokens instead of hand-editing per-page literals.

### SOC ops + repair PRs in the arc

| PR | SHA | What |
|---:|:---|---|
| #169 | `fb9f7dd` | fix(cyber) — rename Slice 8-D controller bean to avoid Spring collision |
| #172 | `98920c9` | fix(repair) — harden mojibake script + targeted form-titles repair |
| #174 | `42b9396` | fix(socops) — repair auth checks + bootstrap SOCOPS license + tables |
| #175 | `b615bf7` | feat(socops) — Saudi-native seed data for SOC Operations module |

PR #168 (`f688dbc`) is the previous STATUS.md capture for the Sprint-8 P0 block — kept below verbatim.

### Saudi regulator coverage — every regulator now anchored in the platform

This arc closes out anchor coverage for every Saudi regulator a Tier-1
bank, energy company, listed entity, or telecom operator interacts with.
Each row points at the platform surface where the citation lives.

| Regulator | Frameworks anchored in platform | Surface |
|---|---|---|
| **SAMA** | Cyber Security Framework (140 controls — full), BCF (47 clauses + addenda), Corporate Governance, AI Principles, Operational Resilience | `module/compliance`, `module/bcm`, `module/cyber`, `module/governance` |
| **NCA** | ECC-2 (full), OTCC (27 controls — full), CCC v1.1, CSCC | `module/cyber`, `module/compliance`, `module/socops` |
| **SDAIA** | PDPL Articles 4 / 20 / 29 / 30 / 31 / 32, AI Ethics Principles, Cross-Border Data Transfer Regulations 2023 | `module/privacy`, `module/ai_governance` |
| **CMA** | Listed Companies CG Code Article 54, IFRS S2 climate disclosure | `module/audit_committee`, `module/esg` |
| **ZATCA** | Fatoora / E-Invoicing wave-23 compliance record | `module/compliance` |
| **NCEMA** | Crisis classification, BCM exercise scenarios | `module/bcm` |
| **NRC** | Critical-infrastructure designation + 72h notification workflow | `module/bcm` |
| **MOH** | Healthcare patient privacy + 24h supplier-incident notification | `module/privacy`, `module/incident` |
| **DGA** | Digital Maturity Model (9 controls × 6 pillars) | `module/compliance` |
| **IIA 2020** | Three Lines Model dashboard | `module/governance` (PR #182) |

### Per-module rollout status after this arc

Every module that received a Sprint 11 / Sprint 12 deliverable is flipped to ✅ Completed below.

| Module | Sprint 11 / 12 deliverables | State |
|---|---|---|
| `bcm` | Slices 9-A..9-F + 10-A..10-F (PRs #170, #171, #178, #181, #183, #185, #186, #187, #190, #191, #192) | ✅ Completed |
| `risk` | Risk Bow-Tie Analysis (PR #179) — on top of FAIR Monte Carlo (#161) | ✅ Completed |
| `privacy` | PDPL sub-processor tracking (PR #204) — on top of data-flow viz (#163) + DSR/Breach/RoPA detail pages (#109/#110) | ✅ Completed |
| `cyber` | Attack Surface Mgmt (PR #202) + Risk-Based Vuln SLA engine (PR #203) — on top of Threat-Intel Correlation (#166) | ✅ Completed |
| `compliance` | Obligation Register + Periodic Attestation (PR #198) — on top of Regulatory Horizon (#162) | ✅ Completed |
| `ccm` | Continuous Control Monitoring engine (PR #199) | ✅ Completed (new module) |
| `esg` | TCFD Climate Scenarios + Scope-3 Financed Emissions (PR #200) | ✅ Completed |
| `audit_committee` | Audit Committee Dashboard (PR #201) — anchored to CMA Art. 14 + SAMA | ✅ Completed (new module) |
| `ai_governance` | AI/ML Model Registry (PR #195) | ✅ Completed (new module) |
| `governance` | Three Lines of Defense dashboard (PR #182) | ✅ Completed (new module) |
| `tprm` | Vendor BCP attestation campaigns (PR #189) — on top of vendor MTPD inheritance (#192) | ✅ Completed |
| `integrations` | ServiceNow / Splunk / CrowdStrike / Tenable adapters (PRs #177, #180, #184, #188) | ✅ Completed (new module) |
| `platform` | Embedded contextual help / KB v2 (PR #206) | ✅ Completed |
| `i18n` | Saudi-native AR audit (PR #207) — 75 strings | ✅ Completed |
| `socops` | Saudi-native seed data + auth-check repair + license bootstrap (PRs #174, #175) | ✅ Completed |
| Design-system | 12 pages migrated, ~2200 inline styles eliminated (PRs #173, #194, #197, #205) | ✅ Completed |
| Open Issues Register | 12 → 0 across 3 batch PRs (#176, #193, #196) | ✅ Closed |

### Numbers

- **38 PRs** merged (#168 → #207)
- **0 reverts**, **0 known regressions** at arc close
- **Backend test suite green** on every PR (`mvn verify`)
- **Frontend Vite build green** on every PR
- **12 → 0** open issues in the register
- **~2200 inline styles** eliminated across 12 pages
- **6 new bridge-adapter packages** for SOC tooling (ServiceNow, Splunk, CrowdStrike, Tenable, plus 2 internal: KB v2, AR audit)
- **140 SAMA CSF controls** seeded with bilingual text (PR #196 / V182)

### What the platform can do today that it couldn't at the start of this arc

| Role | Now possible |
|---|---|
| **CRO** | Run Risk Bow-Tie + Three Lines dashboard alongside FAIR Monte Carlo |
| **BCM Manager** | Quantitative BIA, continuity ROI, dependency cascade, mobile Crisis Room, observer dashboard for live exercises, inject-driven runtime, vendor BCP attestation campaigns, offline BCP/DRP binder PDF |
| **CCO** | Obligation Register + Periodic Attestation against full SAMA CSF 140 control set |
| **CISO** | Attack Surface Mgmt + Risk-Based Vuln SLAs + CCM engine; ServiceNow / Splunk / CrowdStrike / Tenable adapters ingest live |
| **DPO** | PDPL sub-processor tracking with cross-border safeguard templates; SDAIA submission workflow with Article 31 72h clock |
| **AI Officer** | AI/ML Model Registry anchored to SDAIA AI Ethics Principles |
| **Climate Lead** | TCFD scenarios + Scope-3 Financed Emissions (CMA IFRS S2) |
| **Audit Committee Secretary** | Audit Committee Dashboard (CMA Art. 14 + SAMA) |
| **End user** | Embedded contextual help on every page (KB v2); Saudi-native Arabic on every label after PR #207's 75-string audit |
| **Ops** | Refresh-token revoke-everywhere, DB-size graduated alarms, JWT rotation runbook, MFA library wired, LDAP guide, full Saudi regulator surface coverage |

---

## 🟢 Previously latest — Sprint-8 P0 GRC block closes (23 May 2026, PRs #161 → #167)

The full Sprint-8 P0 GRC block from `BCM_GRC_GAP_ANALYSIS_AND_PLAN.md` §4 is in. Five pure-additive backend slices land plus one production-bug fix unblocking local startup.

### Seven-PR arc

| PR | Slice | Module | What lands |
|---|---|---|---|
| #161 | **8-A** | risk | `FairCalculatorService` — Open Group FAIR Monte Carlo (10K iterations default, 100K cap). Triangular inputs. Returns p10 / p50 / p90 / mean ALE + 10-bucket histogram in SAR. |
| #162 | **8-B** | compliance | `RegulatoryHorizonService` — tenant-wide horizon view bucketed PAST_DUE / IMMINENT (≤30d) / NEAR_TERM (31–90d) / QUARTER (91–180d) / HORIZON (>180d) with `bySourceAuthority` + `byImpactLevel` distributions. |
| #163 | **8-C** | privacy | `PdplDataFlowService` — node-link graph aggregating RoPA rows: ACTIVITY ← CATEGORY (USED_BY) → COUNTRY (TRANSFERS_TO with adequacy tag) → MECHANISM (GOVERNED_BY). Saudi-native bilingual labels. |
| #165 | **fix** | security | Break `TenantWriteGuardConfig` → JPA circular dependency that prevented backend startup on default profile. Use `ObjectProvider<TenantWriteGuardInterceptor>` for lazy resolution. |
| #166 | **8-D** | cyber | `ThreatIntelCorrelationService` — accepts CVE/IP/HOSTNAME IOC bundle from SAMA/NCA/MISP/vendor feed, correlates against tenant assets + vulnerabilities, fires `regulatorEscalation=true` for SAMA Cyber 4h clock. |
| #167 | **8-E** | controls | `AiControlMappingService` — deterministic AI suggestor for risk → control mapping. Jaccard + Saudi-regulatory keyword boost (SAMA, NCA, PDPL, OTCC, BCF, BIA, MTPD...) + control-code mention boost. No LLM dependency. |

### Six new endpoints

```
POST /api/v1/risk/fair/calculate                   (8-A)
GET  /api/v1/compliance/regulatory-horizon         (8-B)
GET  /api/v1/privacy/data-flow-graph               (8-C)
POST /api/v1/cyber/threat-intel/correlate          (8-D)
POST /api/v1/controls/ai/suggest                   (8-E)
```

### What the Saudi GRC team can now do

| Role | Before | After |
|---|---|---|
| **CRO** | export to Excel + run Monte Carlo locally | quantify cyber-loss in SAR with p10/p50/p90 ALE inside the platform |
| **CCO** | scattered regulator email digests | one bucketed horizon view sorted by lead time, PAST_DUE flagged red |
| **DPO** | ad-hoc Visio diagrams for SDAIA inspection | SDAIA-inspection-ready data-flow node-link graph |
| **SOC** | manual IOC review against asset spreadsheet | POST IOC bundle → instant correlation + SAMA 4h escalation flag |
| **GRC analyst** | hand-map findings to controls | top-N control suggestions with confidence + rationale (Saudi-keyword boosted) |

### Restart + ops verification (this session)

Local stack restarted cleanly after PRs #161-#167 merged:

- Backend Spring Boot started in ~17s; `/actuator/health` → `{"status":"UP"}`
- Login `POST /api/auth/login` returns access token (admin / Admin@12345)
- 16 endpoints smoke-tested — all 200 for available data
- One real schema bug surfaced + fixed inline: `admin.committees` table missing V154 columns. Reconciled via direct SQL + V154-V161 migration replay; `/api/admin/committees` flipped from 503 → 200.
- One real Spring bug surfaced + fixed (PR #165): `TenantWriteGuardConfig` circular dep with JPA.

### Pure-additive scorecard (Sprint-8 PRs #161-#167)

- **10 new Java files** (5 services, 5 controllers; all inline DTO records)
- **1 finder added** to `RegulatoryChangeRepository`
- **0 schema changes** — every endpoint reads existing tables
- **0 new frontend routes**
- **CI green** on every PR

---

## Previously latest — Sprint-8 opens with three high-leverage backends (PRs #161 → #163)

Sprint-7 closed clean with PRs #155-#160. Sprint-8 opens with **three pure-additive backend slices** — risk, compliance, and privacy — that each unlock a Saudi-native capability the platform was missing.

### Three-PR opening arc

| PR | Slice | Module | What lands |
|---|---|---|---|
| #161 | **8-A** | risk | `FairCalculatorService` — Open Group FAIR Monte Carlo (10K iterations default, 100K cap). Triangular inputs (threatEventFrequency × vulnerability × primaryLoss + secondaryLoss). Returns p10 / p50 / p90 / mean / max ALE + 10-bucket histogram. Stateless — no DB. |
| #162 | **8-B** | compliance | `RegulatoryHorizonService` — tenant-wide horizon view bucketed PAST_DUE / IMMINENT (≤30d) / NEAR_TERM (31–90d) / QUARTER (91–180d) / HORIZON (>180d) with distributions `bySourceAuthority` (SAMA, NCA, SDAIA, CMA, NCEMA, ZATCA, MOH) + `byImpactLevel`. |
| #163 | **8-C** | privacy | `PdplDataFlowService` — node-link graph aggregating RoPA rows: ACTIVITY ← CATEGORY (USED_BY) → COUNTRY (TRANSFERS_TO with adequacy tag) → MECHANISM (GOVERNED_BY). Saudi-native bilingual labels for category + mechanism codes. UNDOCUMENTED node surfaces compliance gaps. |

### Three new endpoints

```
POST /api/v1/risk/fair/calculate
GET  /api/v1/compliance/regulatory-horizon
GET  /api/v1/privacy/data-flow-graph
```

### What's now possible (and wasn't yesterday)

- **CRO can quantify cyber-loss exposure in SAR** without exporting to Excel and running a Monte Carlo locally
- **Chief Compliance Officer sees every Saudi regulator change** on one horizon view sorted by lead time, with PAST_DUE rows flagged separately
- **DPO has the SDAIA-inspection-ready data-flow map** (PII category → activity → cross-border destination + mechanism) without ad-hoc Visio diagrams

### Pure-additive scorecard

- **5 new Java files** (3 services, 2 controllers; all inline DTO records)
- **1 finder added** to `RegulatoryChangeRepository`
- **0 schema changes** — every endpoint reads existing tables
- **0 new routes** — frontend wiring is the next slice
- **CI green** on every PR

---

## Previously — Sprint-7 closeout — code slices land (PRs #155 → #160)

The Sprint-7 plan from `BCM_GRC_GAP_ANALYSIS_AND_PLAN.md` §4 lands five executable slices on top of the PR #148-#154 data + plan groundwork. Pure-additive backend services + frontend tiles — zero schema changes — making the BCM Manager's job actually doable from inside the platform.

### Five-PR code slice arc

| PR | Slice | What lands |
|---|---|---|
| #155 | **7-D** | `BcmBoardPackService` + `GET /api/v1/bcm/board-pack-section` — single aggregator returning Board-ready BCM snapshot: top critical processes by MTPD, last exercises, CAPA snapshot, last AAR, active crises, regulator notifications, readiness headline |
| #156 | **7-E** | `RegulatorCountdownService` + 3 endpoints — SAMA Cyber 4h / SAMA BCF 6h / NCA 24h / SDAIA PDPL 72h / NCEMA 24h / CMA 24h / MOH 24h countdowns with BREACHED/NEAR_DEADLINE/ON_TRACK/NOTIFIED traffic light |
| #157 | **7-C** | `BcmAnnualCycleScheduledSweep` — hourly `@Scheduled` sweep fires `BCM_CYCLE_CLOSEOUT_DUE_{90,60,30,7}D` and `BCM_CYCLE_OVERDUE` triggers; audit-log dedup; mirrors proven `CommitteeScheduledSweep` pattern |
| #158 | **7-G** | V161 — 11 F8 automation rules `AR-160..AR-170` consuming the new BCM cycle triggers (NOTIFY / ESCALATE / CREATE_RECORD / FLAG); reuses existing handlers — no new rule-engine code |
| #159 | **7-H** | Frontend wiring — `lib/bcmBoardPack.ts` + `lib/bcmRegulatorCountdown.ts` + two graceful-degrade tiles on BCM dashboard with bilingual Saudi-native copy |

### What the Saudi BCM Manager can now do without leaving the platform

| Daily / weekly | Before | After |
|---|---|---|
| See where the cycle stands at a glance | bounce between 5 pages | one Board-pack tile on BCM dashboard |
| Know which regulators need a call right now | manual timer in Excel | traffic-light countdown tile |
| Get reminded about T-90/60/30/7 cycle milestones | personal calendar | platform fires `DomainEvent` → F8 rules → NOTIFY/ESCALATE |
| Spot a cycle going overdue | quarterly meeting | daily re-armed `OVERDUE` trigger → CRO escalation |
| Open a remediation CAPA when readiness drops below 70% | manual triage | `AR-166` `CREATE_RECORD` fires automatically at T-90 |
| Track every regulator notification in one log | scattered emails | `Last 20 across all crisis events` tile + audit-trail row per regulator |

### Pure-additive numbers

- **6 new Java files** (3 services, 3 controllers; 2 inline DTO records)
- **2 new TypeScript lib files** + 2 new dashboard tiles
- **1 Flyway migration** (V161) — 11 idempotent rules
- **0 schema changes** — every endpoint reads existing tables
- **0 new routes** — tiles link to existing pages
- **CI green** on every PR (Backend Spring Boot + Frontend Vite jobs)

### Saudi regulator SLA matrix (now encoded in code, not docs)

| Regulator | Window | Trigger condition | Rule |
|---|---|---|---|
| SAMA Cyber Framework | 4 h | severity ≥ 3 AND cyber_related | `RegulatorCountdownService` |
| SAMA BCF (operational) | 6 h | severity ≥ 3 | `RegulatorCountdownService` |
| NCA (ECC / OTCC) | 24 h | cyber_related (any severity) | `RegulatorCountdownService` |
| SDAIA PDPL Article 23 | 72 h | personal_data_affected | `RegulatorCountdownService` + V115 `pdpl_72h_deadline_at` |
| NCEMA | 24 h | severity ≥ 4 | `RegulatorCountdownService` |
| CMA | 24 h | severity ≥ 4 — listed-entity disclosure | `RegulatorCountdownService` |
| MOH | 24 h | severity ≥ 4 AND supplier_involved | `RegulatorCountdownService` |

### Backend endpoints shipped this sub-sprint

```
GET /api/v1/bcm/board-pack-section
GET /api/v1/bcm/regulator-countdowns
GET /api/v1/bcm/regulator-countdowns/summary
GET /api/v1/bcm/regulator-countdowns/event/{id}
@Scheduled hourly: BcmAnnualCycleScheduledSweep.tick()
```

### What's still ahead in Sprint-7 / 8

From `BCM_GRC_GAP_ANALYSIS_AND_PLAN.md`:
- Slice 7-A — Multi-year strategic roadmap Gantt (frontend)
- Slice 7-B (full) — Maturity-assessment wizard UI (frontend)
- Sprint-8 — FAIR risk lane, regulatory horizon scanner, PDPL data-flow viz, threat-intel ingestion, AI control-mapping
- Sprint-6 task #207 — tenant-write-guard activation (operator action)

---

## Previously latest — Sprint-7 BCM/GRC plan + Saudi regulatory seed arc (PRs #148 → #154)

User asked: "as Saudi BCM Manager what you still need... as GRC expert and SME make a plan in md file". Result: a complete BCM playbook (gap analysis + plan + operational doc library + first execution slices delivering 540+ rows of Saudi-regulatory compliance data).

### Seven-PR arc

| PR | What lands |
|---|---|
| #148 | `realtime.ts` STOMP stale-JWT fix + sessionStorage antipattern (the silent twin of the api unification arc) |
| #149 | Backend `GlobalExceptionHandler` — 4 new `@ExceptionHandlers` for common 500 sources + richer `handleGeneral` detail |
| #150 | `BCM_GRC_GAP_ANALYSIS_AND_PLAN.md` — 28 BCM-Manager gaps + 30 GRC SME gaps + sprint plan (Sprints 7-12, ~30 slices) |
| #151 | **Slice 7-F** — SAMA BCF clause-level mapping seed (47 clauses across 8 chapters + 3 SAMA-specific addenda) |
| #152 | `docs/bcm-manager/` — **19 operational MD files**: charter, RACI, glossary, BIA methodology + template, risk methodology, continuity strategy, BCP/DRP/IRP/comms/pandemic plans, exercise strategy + templates, annual review, regulator runbooks, vendor BCP attestation, KPI dashboard, lessons-learned template |
| #153 | **4 plan slices batched**: 7-G NCA OTCC (27 controls), 7-B precursor ISO 22301 §4-10 (47 sub-clauses), NCEMA/NRC (10), 9-A precursor 40 new exercise scenarios |
| #154 | Closeout seed — DGA expansion (7), SAMA OPRES expansion (7), multi-tenant replication to Aramco + STC (252 rows) |

### The 7-PR result in numbers

**`bcm.compliance_mappings`** (across 3 tenants):

| Framework | TSS (org=1) | Aramco (org=2) | STC (org=3) | Total |
|---|---:|---:|---:|---:|
| ISO 22301 §4-10 | 52 | 52 | 52 | 156 |
| SAMA BCF | 47 | 47 | 47 | 141 |
| NCA OTCC | 27 | 27 | 27 | 81 |
| SAMA Operational Resilience | 12 | - | - | 12 |
| DGA Digital Govt | 10 | - | - | 10 |
| NCEMA crisis classification | 7 | - | - | 7 |
| NRC critical infrastructure | 5 | - | - | 5 |
| **Total** | **160** | **126** | **126** | **412** |

**`bcm.exercise_scenarios`**: 50 (up from 10) — 8 cyber, 4 physical, 5 tech, 4 vendor, 2 people, 3 regulatory, 2 climate, 4 multi-event, 2 governance, 2 recovery, 1 annual, 2 OT (Aramco), 1 telco (STC).

### The plan document set

The session ships two distinct planning artefacts:

1. **`BCM_GRC_GAP_ANALYSIS_AND_PLAN.md`** at the repo root — gap analysis + 6-sprint roadmap, slice-numbered for stable references.
2. **`docs/bcm-manager/`** — 19 operational documents (README + 18 templates / runbooks / methodologies) that the BCM Manager works from day-to-day. Each doc maps to backing platform entities.

### Saudi regulatory coverage

Every Saudi regulator a banking BCM Manager interacts with now has clause-level data:
- **SAMA BCF** — 8 chapters (Governance, Embedding, BIA, Risk, Strategy, Plans, Exercises, Reporting) + 3 SAMA-specific addenda (Hajj/Ramadan, cross-border, outsourcing)
- **SAMA Operational Resilience** — Important Business Services + impact tolerance methodology
- **SAMA Cyber Framework v1.3** — 6h major-incident reporting (workflow exists; countdown timer is Sprint-7 Slice 7-E future work)
- **NCA ECC-2 / CSCC / OTCC** — full ECC mapped; OTCC OT-BCM now clause-level (27 controls across 5 domains: Governance, Defence, Resilience, Third Party, Cloud)
- **SDAIA PDPL** — Articles 4 / 5 / 6 / 20 (72h) / 21 (subject) / 29 (cross-border) all live end-to-end
- **NRC** — critical-sector designation + 72h notification workflow
- **NCEMA** — tier classification + multi-agency coordination
- **CMA** — Articles 91-94 (sustainability) + Article 41 (operational-resilience) referenced
- **ZATCA, MOH** — compliance records exist

### What's left in the plan (Sprints 7-12)

Still to ship (each needs schema + Java service + React page, ~3-5 PRs each):

| Slice | Sprint | Effort |
|---|---|---|
| 7-A Multi-year strategic roadmap (Gantt) | 7 | Schema + service + page |
| 7-B (full) Maturity-assessment wizard UI | 7 | Page + scoring + evidence link |
| 7-C Annual cycle automation (reminder inbox) | 7 | Spring scheduler + email channel |
| 7-D Board pack BCM section auto-gen | 7 | Aggregator service + button |
| 7-E Regulator countdown timers (SAMA 6h, etc.) | 7 | Service + timer widget |
| 8-A FAIR quantitative risk lane | 8 | Wizard + monte-carlo |
| 8-B Regulatory horizon scanner | 8 | RSS ingest + inbox |
| 8-C PDPL data-flow viz | 8 | React-flow + per-category overlay |
| 8-D Threat-intel ingestion (MISP/OTX) | 8 | HTTP client + IOC table |
| 9-A Dependency cascade simulator | 9 | Service + algorithm |
| ...and Sprint 10-12 P1+P2 items |

---

## API client unification (23 May 2026, PRs #142 → #146)

The deeper root cause of the "Internal Server Error" toast complaint. Even after PRs #130/#131 stopped pages from blanking on partial failures, real backend errors still showed generic "Internal Server Error" / "Bad Request" / "Service Unavailable" in toasts instead of the actual backend diagnostic. This arc finds and fixes why.

### Five-PR arc

| PR | What lands |
|---|---|
| #142 | `seed_real_data.py` — counter fix (cur.rowcount was misleading; now uses `SELECT @@ROWCOUNT`) |
| #143 | `api.ts request()` — use `ProblemDetail.detail / title / message` (not just `.message`) |
| #144 | `api.ts downloadFile()` — companion fix: parse JSON ProblemDetail from binary endpoints' error responses |
| #145 | `kb.ts` + `persistence.ts` — refactored to route through the central `api.*` client (had been raw fetch with home-rolled `authHeaders`) |
| #146 | `platformPersistence.ts` — last raw-fetch holdout closed out; 6 sites refactored, demo-mode preserved |

### The root-cause bug (PR #143)

Backend `GlobalExceptionHandler` returns Spring's `ProblemDetail` for every error (RFC-7807):
```json
{
  "type": "https://...",
  "title": "Bad Request",
  "status": 400,
  "detail": "Title is required.",
  "timestamp": "..."
}
```

But `api.ts:request()` extracted the toast message with:
```ts
const msg = (parsed as { message?: string })?.message ?? res.statusText ?? 'Request failed';
```

ProblemDetail has no `.message` field. So `msg` always fell back to `res.statusText` — "Bad Request", "Internal Server Error", "Service Unavailable" — for every backend error. Pages doing `toast.danger(e.message)` directly missed the real reason.

Fix: prefer **`detail → title → message → statusText`**. After this change, the 503 "Module not yet bootstrapped" actually shows its full diagnostic instead of "Service Unavailable".

### Companion fixes

- `downloadFile()` (PR #144) had the same bug — never parsed the error body at all. PDF/XLSX/ICS downloads hitting 503 showed "Service Unavailable" too. Now parses JSON ProblemDetail when `!res.ok`.
- Three lib files were bypassing the central api client entirely:
  - `kb.ts` (Knowledge Base) — home-rolled authHeaders + raw fetch; `throw new Error('HTTP 400')` discarded the real backend reason.
  - `persistence.ts` (production-mode store) — **wasn't sending the Bearer token at all** (only cookies); every call would 401 in production mode.
  - `platformPersistence.ts` (3-mode demo/rest/mixed adapter) — same antipattern, 6 fetch sites.
- All three refactored to use `api.get / post / put / delete`. Demo mode preserved (early returns).

### Closeout state

```
Zero raw fetch() calls to /api/* remain in src/.
```
Every backend call goes through `request()` / `api.*` / `downloadFile()` in `lib/api.ts`. Every error gets transparent 401-refresh-retry, ProblemDetail.detail extraction, consistent Bearer-header construction via `getAccessToken()`.

---

## Graceful-degrade + Saudi-native seed sweep (23 May 2026, PRs #130 → #137)

User complaint at start of session: "unable to load data in all pages, **تعذّر تحميل هذا القسم / Internal Server Error**, fill all pages with data". Two distinct problems were tangled together: (1) one failing aggregate endpoint blanking the entire page, (2) thin tables (~5 rows) making pages look empty.

### Eight-PR arc

| PR | Module | What lands |
|---|---|---|
| #130 | committees ui | `CommitteesPage` graceful-degrade — root-cause fix: `Promise.all` rejected on a single aggregate failure |
| #131 | platform ui   | Sweep — extends `.catch(() => fallback)` per-call across **18 pages / ~24 `Promise.all` blocks** (Licensing, BCM × 7, Compliance, Controls, ESG × 9, FrameworkControls, NDI × 9, Regulatory, Reporting, IrPlaybooks, SupplierResilience, TPRM) |
| #132 | data seed     | Wave 1 — `seed_real_data.py` populates 8 tables / 117 rows: privacy.consents (15), DSRs (15), breaches (11), cyber.cyber_assets (20), bia_records (5), vendor_onboarding (6), reporting.report_definitions (20), workflow.workflow_definitions (20) |
| #133 | data seed     | Wave 2 — +5 tables / 56 rows: cyber_incidents (12), bcm.bcm_exercises (10), regulatory_changes (12), material_topic (12), ndi.gap (10) |
| #134 | data seed     | Wave 3 — +2 tables / 22 rows: sust.climate_risk_opportunity (10), audit_lc.findings (12) |
| #135 | data seed     | Wave 4 — +4 tables / 49 rows: approvals.requests (12), audit_lc.capa_tasks (12), bcm.recovery_operations (8), bcm.communication_contacts (17) |
| #136 | data seed     | Wave 5 — +3 tables / 30 rows: risk_lc.kri_breaches (12), board_pack.packs (8), ethics.cases (10) |
| #137 | data seed     | Wave 6 — +2 tables / 13 rows: audit_lc.annual_plans (+2027), audit_lc.working_papers (12) — closes the IA lifecycle end-to-end |

**Cumulative:** 21 tables seeded, ~375 rows of Saudi-native day-to-day GRC content.

### The graceful-degrade pattern (PR #130 / #131)

**Before** (one failure blanks the page):
```ts
try {
  const [a, b, c] = await Promise.all([listA(), listB(), aggregateC()]);
  setA(a); setB(b); setC(c);
} catch (e) { toast.danger('Failed to load'); }
```

**After** (partial data still renders):
```ts
const [a, b, c] = await Promise.all([
  listA().catch(() => fallbackA),
  listB().catch(() => fallbackB),
  aggregateC().catch(() => null),
]);
setA(a); setB(b); setC(c);
```

Write paths (enable/disable/upsert/etc) keep their own per-action toast — only the page-load `Promise.all` blocks were softened.

### The "Internal Server Error" trail

Tailed `.tools/backend.log`. Every recorded 500 was a stale `NoResourceFoundException` for `/api/signatures`, `/api/rca` etc — paths that don't exist. `GlobalExceptionHandler.handleNoResource` now maps those to 404, so after a backend restart the toast title reads "Not Found" not "Internal Server Error". Combined with PR #131's graceful degrade, single-endpoint failures no longer blank pages.

### Saudi-native data themes

Every seed row models a Saudi Bank / TASI-listed company:
- **Banking stack** — T24, SADAD, Exadata, Murex, Way4, Actimize, AWS me-south-1 (Bahrain)
- **PDPL** — Art.4 (30-day DSR SLA), Art.5/6 consents, Art.20 breach notification, Art.29 cross-border with SCC requirements
- **SAMA** — Cyber Framework 1.3 (6h major-incident), Cloud Outsourcing v2, Open Banking phase 3
- **NCA** — ECC-2, CCC v1.1, OTCC
- **SDAIA** — PDPL exec regs, Tier-1 country transfer decision
- **CMA** — Sustainability Articles 91-94, governance code, ESG annex
- **ZATCA, MOH** — e-invoicing wave 23, healthcare patient privacy
- **Arabic content** — Saudi-native naming (Mohammed Al-Otaibi, Sarah Al-Qahtani…), bilingual + RTL-aware

### Schema discoveries this session

The seed script tripped on several CHECK constraints. The successful enum values were:
- `sust.material_topic.category` — `ENVIRONMENTAL` (not ENVIRONMENT), SOCIAL, GOVERNANCE
- `sust.material_topic.priority_tier` — LOW / MEDIUM / HIGH / CRITICAL (no VERY_HIGH)
- `sust.material_topic.status` — DRAFT / SCORED / APPROVED / ARCHIVED (no IN_REVIEW)
- `ndi.gap.status` — OPEN / IN_PROGRESS / RESOLVED / ACCEPTED_RISK / CLOSED (no IN_REMEDIATION / REMEDIATED)
- `scm.vendor_onboarding.current_stage` — REGISTRATION / CRITICALITY_CLASSIFICATION / SECURITY_REVIEW / OPERATIONAL_REVIEW / LEGAL_REVIEW / APPROVAL (no KYC_VERIFICATION / DUE_DILIGENCE / SAMA_OUTSOURCING_NOTIFICATION)

These are now baked into `seed_real_data.py` so future re-runs don't trip them.

### Audit lifecycle end-to-end

The seed waves together stand up the full IA chain:
```
audit_lc.annual_plans (2026 + 2027, wave 6)
    └─ engagements (existing)
        └─ audit_lc.working_papers (12, wave 6)
            └─ audit_lc.findings (12, wave 3)
                └─ audit_lc.capa_tasks (12, wave 4)
                    └─ approvals.requests (12, wave 4)
```
Every IA register on the frontend now renders content all the way through.

### How to run the seed

```bash
python seed_real_data.py
```
Idempotent — every INSERT is guarded by `IF NOT EXISTS (...)`. Safe to re-run on demos / DB refreshes; reports `inserted: 0, skipped: ~375` on the second pass.

---

## Sprint-6 mass-assignment AOP backstop (23 May 2026, PRs #122 → #127)

The per-service `setId(null)` defense from PR #121 + #123 patched 19
write endpoints, but relied on every future developer to remember the
pattern on the next service. Sprint-6 adds a **platform-wide AOP
backstop** so the regression case fails loudly instead of silently.

### Six-PR arc

| PR | What lands |
|---|---|
| #122 | `STATUS.md` capture of the bug-hunt arc (#117 → #121) |
| #123 | DPIA `setId(null)` — one more service the earlier sweep missed |
| #124 | `docs/security/MASS_ASSIGNMENT_DEFENSE.md` — pattern + Sprint-6 design |
| #125 | `TenantContext.allowCrossTenantWrite()` + `TenantWriteGuardInterceptor` stub (not wired) |
| #126 | `TenantWriteGuardConfig` — `HibernatePropertiesCustomizer` wiring, feature-flagged default OFF |
| #127 | 8 unit tests for the interceptor decision logic |

### The defense in one paragraph

The `TenantWriteGuardInterceptor` implements Hibernate's
`Interceptor.onFlushDirty`. On every UPDATE, it checks if
`organisationId` is changing between previous-state and current-state.
If yes, it allows the change **only** when the calling thread has
explicitly opted in via `try (var scope =
TenantContext.allowCrossTenantWrite()) { ... }`; otherwise it throws
`SecurityException` with a diagnostic message naming the entity, id,
old/new org, and pointing the developer at both `allowCrossTenantWrite()`
and the per-service `setId(null)` defense. Blocked attempts are also
written to the audit log (`SECURITY_BLOCK` action) so ops can spot
attack attempts even when an upstream try/catch masks the exception.

### Why `onFlushDirty` (not `@PreUpdate`)

JPA's `@PreUpdate` only sees post-mutation state — by then the
attacker's cross-tenant `organisationId` has already been copied onto
the managed entity. Hibernate's `onFlushDirty` is the only place that
sees BOTH old and new property values.

### Activation pathway (default OFF on master)

The wiring config (PR #126) reads the property
`grc.security.tenant-write-guard.enabled` (default `false`).
Operators flip it on per-deployment:

```yaml
grc:
  security:
    tenant-write-guard:
      enabled: true
```

Rollout plan documented in `docs/security/MASS_ASSIGNMENT_DEFENSE.md`:

1. **Staging**: flip `enabled: true`, run for one sprint, watch
   audit log for `SECURITY_BLOCK` entries on legitimate flows.
2. **Prod**: flip `enabled: true` once staging is clean.
3. **Remove the flag**: once prod has been default-on for a sprint
   with zero false positives.

### What's still unblocked vs blocked

| Status | Item |
|---|---|
| ✅ Done | Per-service `setId(null)` (19 services patched) — primary defense, no flag dependency |
| ✅ Done | Interceptor implemented + tested |
| ✅ Done | Wiring config behind feature flag, default OFF — boots clean |
| ⏸️ Operator | Staging activation (flip flag, monitor one sprint) |
| ⏸️ Operator | Prod activation (after staging clean) |
| ⏸️ Cleanup | Remove the flag once prod is stable on default-on |

Tracked as task #207 (`Sprint-6 tenant-write-guard activation`).

### Test coverage

PR #127's 8 unit tests cover the security-critical decision logic via
a package-private `decide()` helper that the interceptor delegates to.
Using `decide()` instead of `onFlushDirty()` shields the tests from
Hibernate's deprecated/new overload churn — same logic, more robust
test seam.

| Scenario | Test |
|---|---|
| Attack (org changes, no opt-in) | `blocksWhenOrganisationIdChangesWithoutOptIn` |
| Defensive (org null → value, no opt-in) | `blocksWhenOrganisationIdGoesFromNullToValue` |
| Legitimate (org changes, inside opt-in scope) | `allowsCrossTenantWriteInsideExplicitOptInScope` |
| Normal (org unchanged) | `allowsNormalUpdateWhereOrganisationIdUnchanged` |
| Out of scope (no org column) | `ignoresEntitiesWithoutOrganisationIdColumn` |
| Null safety (Hibernate quirk) | `handlesNullPreviousStateGracefully` |
| Cleanup (normal exit) | `optInScopeClearsAfterTryWithResources` |
| Cleanup (exception in body) | `optInScopeClearsEvenIfBodyThrows` |

---

## 🟢 Earlier — Static-analysis bug-hunt closeout (23 May 2026, PRs #117 → #121)

Five-PR arc covering 10 rounds of platform-wide static-analysis bug-hunting,
starting from the committees outage and ending with the cross-tenant
mass-assignment sweep. **12+ real production bugs caught and fixed**,
of which 4 were security-severity. The bug-hunt surface from static
reading is now genuinely exhausted — further finds need DAST / fuzz /
dependency CVE scanning.

### Bug inventory (by severity)

| # | Bug | PR | Severity |
|---|---|---|---|
| 1 | Committees `UnexpectedRollbackException` at tx commit time | #117 | High (feature-breaking) |
| 2 | Global handler missing for `DataAccessException` (every module) | #116 | High |
| 3 | ErrorState UX flattened backend ProblemDetail to generic English | #119 | UX |
| 4 | Sustainability evidence aggregate tenant-leak (`findAll().filter`) | #119 | High (multi-tenant) |
| 5 | NDI report cycle-history tenant-leak | #119 | High |
| 6 | Committee sweep O(N×M) per tick | #119 | Performance |
| 7 | Calendar wrong sessionStorage token key (`'accessToken'` vs `'grc.accessToken'`) | #120 | High (feature 100% broken) |
| 8 | Calendar hardcoded `http://localhost:8084` fallback | #120 | Medium |
| 9 | **KB stored-XSS via `javascript:` URLs in markdown links** | #120 | **High (security)** |
| 10 | **JVM TZ defaulted to UTC on Saudi-only platform (3 hrs/day silent data corruption)** | #121 | **High (data quality)** |
| 11 | **Refresh-cookie emitted two Set-Cookie headers — `SameSite=Strict` could be dropped** | #121 | **High (security — CSRF)** |
| 12 | Logout missing `SameSite` on cookie-clear | #121 | Medium |
| 13 | **Cross-tenant mass-assignment via JPA `merge()` — 18 service methods affected** | #121 | **High (security — IDOR)** |
| 14 | `BcmService.createBia` missing org scope (caught while patching #13) | #121 | Medium |

### Mass-assignment pattern audit summary

The deepest finding of the arc. Every committee/cyber/BCM/compliance/crisis-comms
service that accepted a JPA entity directly via `@RequestBody` was vulnerable
to cross-tenant `merge()` overwrites — a client supplying `{"id": 12345, ...}`
could rewrite arbitrary cross-tenant rows. **18 services patched** with the
defensive `setId(null)` pattern; **17 services verified safe** (already use
the `new Entity() + apply(dto)` pattern).

| Module | Methods patched |
|---|---|
| `admin.committee` | `CommitteeActionItemService.create`, `CommitteeResolutionService.create`, `CommitteeMemberRegistryService.appoint` + `addSkill`, `CommitteeMeetingPapersService.circulate` |
| `cyber` | `CyberService.saveFramework` + `saveControl` + `saveSubControl` + `saveIncident` + `saveAsset` |
| `bcm` | `BcmService.createPlan` + `createProcess` + `createBia` + `createExercise` |
| `compliance` | `PolicyService.create`, `RegulatoryChangeService.create` |
| `bcm.service.crisis` | `CrisisCommsService.createTemplate` + `createAudience` |

Verified safe (use safer DTO-construct pattern, no fix needed): approvals,
audit_lc.FindingService, audit_eng.AuditEngagementService, bcm.cycle.CrisisAarService,
bcm_lc.InvocationService, board_pack, capa, change_mgmt,
compliance.AuditEngagementService, cyber_lc.VulnerabilityService,
evidence (×3), ethics (×4), incident, nca_ecc.

**Sprint-6 proposal**: introduce a platform-wide AOP defense (annotation
or BaseEntity `@PrePersist` hook) so future write endpoints can't
reintroduce this class of bug. The per-service `setId(null)` is correct
but easy to forget on the next service.

### Timezone hardening

`Dockerfile` + `GrcApplication.main()` both pin `user.timezone=Asia/Riyadh`.
Operator can override via `-e JAVA_OPTS="... -Duser.timezone=..."` for
future multi-region deployments. Jackson's `spring.jackson.time-zone: UTC`
unchanged — wire format remains UTC, wall-clock reads now Riyadh. The
100+ existing `LocalDate.now()` callsites pick up the new default
automatically; no code changes needed across services.

### UI polish (PR #120)

Cross-cutting CSS upgrade dropped into every consumer of the shared
primitives:
- **Tables**: sticky `<th>`, smooth row-hover, zebra striping, fade-in on data refresh.
- **Tabs**: `:focus-visible` keyboard ring; `[aria-disabled]` dimmed state.
- **Cards**: smooth elevation transition; opt-in `.card-interactive` lift-on-hover.

### Saudi-native ErrorState (PR #119)

Closes the loop on PR #117's backend ProblemDetail. New `lib/apiError.ts`
extractor + bilingual ErrorState. When a module's tables aren't bootstrapped,
the user sees a friendly card with `python setup_<module>_db.py` and a
copy-to-clipboard button — in their selected language — instead of a
generic English failure.

---

## 🟢 Earlier — Committees outage closeout (22 May 2026, PRs #114 → #117)

`/committees` and `/committees/catalog` were throwing 500s on dev installs
that hadn't bootstrapped the V154–V159 committees schema yet. Four PRs
chase the root cause through three layers; PR #117 closes it for every
module, not just committees.

### What the bug actually was

A `@Transactional(readOnly = true)` repo query against a missing table
throws `BadSqlGrammarException`. The service-level `try/catch` swallowed
it and returned `Collections.emptyList()` — but Spring's transaction
interceptor had **already** marked the transaction rollback-only the
moment the exception propagated past JPA. When the service method then
returned normally, the interceptor called `commit()`, saw the
rollback-only flag, and threw **`UnexpectedRollbackException` at commit
time**, escaping every service-layer catch.

### Three-layer fix

| PR | Layer | Fix |
|---|---|---|
| #114 | Service | First `try/catch` on `InvalidDataAccessResourceUsageException` — too narrow; Hibernate wraps SQL errors in many subtypes |
| #115 | Service | Broadened to walk the cause chain via `CommitteeSqlGuards.isMissingObjectError()`, applied to Resolution + ActionItem services |
| #116 | Handler | Global `@ExceptionHandler(DataAccessException.class)` returns RFC-7807 503 with bootstrap hint — every module benefits |
| #117 | Both | **Real root cause.** `@Transactional(readOnly = true, noRollbackFor = RuntimeException.class)` on all committee read methods + `@ExceptionHandler(UnexpectedRollbackException.class)` as belt-and-braces translation to 503 |

### Operator guidance (dev installs)

If `/committees` returns 503 with title "Module not yet bootstrapped",
the fix is documented in the detail body: run
`python setup_committees_db.py` from the repo root to create the
V154–V159 schema + seed 19 committee templates / ~150 standing items /
10 bilingual form templates. Other modules adopting the same lazy-load
pattern can swap their `@Transactional(readOnly = true)` for the
`noRollbackFor = RuntimeException.class` variant if they want to swallow
missing-table errors locally; otherwise the global handler in
GlobalExceptionHandler catches the rollback at the controller boundary.

---

## 🟢 Earlier — Privacy / PDPL module complete (22 May 2026, PRs #109 + #110 + #111)

The Privacy module is now end-to-end complete: every register tab is
backed by real `/api/v1/privacy/*` data, every row has a real detail
page, every workflow button is wired to its lifecycle endpoint, and
every PDPL article anchor is visible to the DPO.

### What landed across three PRs

| PR | Closes | Description |
|---|---|---|
| #109 | Consent tab was hardcoded percentages | Real `ConsentRecord` ledger backed by `/api/v1/privacy/consents` + `/stats`. New `ConsentTab` component: lifecycle KPIs, channel mix, purpose mix with hot-purpose highlight, expiring-soon banner with inline Withdraw, recent grants + withdrawals. `ConsentDetailPage` at `/privacy/consents/:id` with full subject history. |
| #110 | DSR / Breach / RoPA rows had no detail page | Three new detail pages: `DsrDetailPage` (30-day SLA countdown + Start/Fulfil/Extend/Reject workflow), `PrivacyBreachDetailPage` (72-hour SDAIA notification clock + Contain/Notify/Close), `RopaDetailPage` (Art. 25 completeness check). All three carry the PDPL legal anchor, audit timeline tab, and Saudi-native Arabic labels. |
| #111 | Row clicks did nothing | Wired `rowHref` on DSR / Breach / RoPA `DataTable` instances so clicking a row lands on its detail page (same convention as Vulnerabilities / Workflow Submissions / Risk Register). |

### Saudi-native UX guarantee

Every purpose code, channel label, request type, breach type, and
status badge carries an idiomatic Arabic label — never a
transliteration. Examples:
- `KYC_ONBOARDING` → "التحقق من الهوية"
- `CREDIT_SCORING` → "التقييم الائتماني (سمة)" (SIMAH-specific)
- `CROSS_BORDER` → "النقل خارج المملكة"
- `UNAUTHORISED_DISCLOSURE` → "إفشاء غير مصرح"
- "Notify SDAIA" → "إخطار سدايا"

### PDPL article anchors visible everywhere

| Article | Surface |
|---|---|
| Art. 4 (Data subject rights, 30d SLA) | DSR detail header countdown + body anchor |
| Art. 5 (Consent ledger) | Consent tab footer + detail body |
| Art. 6 (Right of withdrawal) | Consent withdraw action + detail body |
| Art. 20 (SDAIA notification, 72h) | Breach detail header clock + body anchor |
| Art. 21 (Subject notification) | Breach detail affected-records panel + checklist |
| Art. 24 (DPIA) | Existing DPIA detail page |
| Art. 25 (RoPA register) | RoPA detail body + completeness check |

### Detail page coverage matrix

| Entity | Detail page | Workflow buttons | Audit timeline | PDPL anchor |
|---|---|---|---|---|
| DSR | `/privacy/dsrs/:id` | Start / Fulfil / Extend / Reject | ✅ | Art. 4 |
| Breach | `/privacy/breaches/:id` | Contain / Notify SDAIA / Close | ✅ | Art. 20+21 |
| RoPA | `/privacy/ropa/:id` | (read-only register entry) | ✅ | Art. 25 |
| DPIA | `/dpias/:id` (existed) | Submit / Approve / Reject / Expire | ✅ | Art. 24 |
| Consent | `/privacy/consents/:id` | Withdraw (only when ACTIVE) | ✅ | Art. 5+6 |

---

## 🟢 Earlier — Saved Views Phase 2 backend (22 May 2026, PR #108)

Closes the loop on the localStorage-only Phase 1. Saved views now
round-trip through `/api/platform/saved-views` whenever the user is
authenticated, and fall back gracefully to localStorage for
kiosk/demo/offline.

### What landed

| Layer | Artefact | Notes |
|---|---|---|
| Migration | `V160__platform_saved_views.sql` | Creates `platform.saved_views` with per-user-per-page unique partial index on `is_default` |
| Entity | `SavedView.java` | `@Where(is_deleted=0)`, opaque JSON columns for `filter` / `sort` / `visibleColumns`, separate `url_query` field |
| Repository | `SavedViewRepository.java` | Org+user+page lookup, default lookup, bulk clear-default-except |
| Service | `SavedViewService.java` | CRUD + audit-logged + clears previous default on promotion |
| Controller | `SavedViewController.java` | REST under `/api/platform/saved-views`, all endpoints `isAuthenticated()` |
| Frontend adapter | `lib/savedViews.ts` | Best-effort write-through: localStorage stays the synchronous source of truth, backend sync runs in the background. `syncSavedViewsFromBackend(pageKey)` available for one-shot pull on login. |

### Design choices worth remembering

- **JSON columns, not relational filter/sort/columns** — each list
  page owns the shape of its own filter (workflow uses URL strings,
  cyber uses FilterGroup trees). The DB never reads inside these
  blobs.
- **Per-user, not per-org** — saved views are personal preferences.
  `is_shared` is persisted for the Phase-3 org-wide story but
  ignored locally today.
- **No module permission check** — the controller is just
  `isAuthenticated()`. A user might have views across modules; gating
  on a per-page permission would force the backend to know the
  page→module mapping, which is a frontend concern.
- **Local-first** — reads are synchronous against localStorage so
  component renders don't go async. Writes update localStorage
  immediately and fire a best-effort backend call. If the backend
  is down, the local copy keeps working.

---

## 🟢 Earlier — Saved Views Phase 1 expansion (22 May 2026, PR #106 + #107)

Phase 1 of Sprint-4 Track-2.5 (localStorage-backed saved views) now
has **three production consumers** sharing the same `urlQuery`
pattern. A "view" is a snapshot of the URL search-params for pages
whose filter state lives in the URL — saving captures the current
combination, applying restores it. Pinning a view as default
auto-applies on next visit (URL drill-down deep links still win).

| Page | Status | Notes |
|---|---|---|
| `cyber/vulns` (VulnerabilitiesV2Page) | ✅ DONE (earlier) | Original consumer; uses `filter` + `sort` + `visibleColumns` |
| `workflow/submissions` (WorkflowSubmissionsPage) | ✅ DONE (PR #106) | `urlQuery` snapshot — module + workflow + status + free-text search |
| `risk/register` (RiskRegisterPage) | ✅ DONE (follow-up) | `urlQuery` snapshot — tab + heatmap drill-down (L×I) + status + category + level chips |

**Sprint-5 backend migration** (Track-2.5 Phase 2) still owed:
`platform.saved_views` V20 migration + `/api/platform/saved-views`
controller. SavedView shape already matches the eventual backend DTO
1-for-1. Estimated 1 day.

**Pages explicitly skipped for Phase 1** — IncidentsPage, CompliancePage,
CalendarPage: URL state is only `tab`, so snapshotting URL params
wouldn't capture the React-state filter modals. These need Phase 1.5
to also persist the in-memory filter object alongside `urlQuery`,
or wait for Sprint-5's full filter-expression builder (Track-2.3).

---

## 🟢 Earlier — Sprint-4 Track-1 audit closeout (22 May 2026)

Full code-walk verification of every Sprint-4 Track-1 ("Production
Blockers") item from `sprints/sprint-4-convergence.md`. **All 6 items
are implemented in the merged codebase** — most landed silently across
prior PRs and were never formally closed in STATUS.md. This entry
captures the audit so future devs don't re-do them.

### Track-1 verification matrix

| # | Item | Evidence | State |
|---|---|---|---|
| **T1.1** | JWT secret hardening | `JwtTokenProvider.validateSecret()` throws on null / blank / < 32 chars / placeholder strings; `application.yml:128` has **no** default (`${JWT_SECRET:}` resolves to empty → fail-fast); `JwtTokenProviderTest` covers all rejection paths | ✅ DONE |
| **T1.2** | Dockerfile + GitHub Actions CI | Multi-stage `Dockerfile` (builder + runtime, non-root user, healthcheck, JAVA_OPTS tunable); `.dockerignore`; `.github/workflows/ci.yml` (backend `mvn verify` + frontend `vite build`, JDK-17 + Node-20 matrix); `.github/workflows/build-image.yml` for GHCR push on master | ✅ DONE |
| **T1.3** | Flyway baseline V1+V2 | Documented as deferred — dev DB is Python-bootstrap-managed; prod gets V3+ on top of an existing baseline. Migration freeze (`flyway.clean-disabled=true`) prevents accidental wipe | 🟡 DEFERRED |
| **T1.4** | `EvidenceMatrixService.findAll()` tenant fix | Code comment at `EvidenceMatrixService.java:59` explicitly flags this as PLATFORM-SHARED (the framework catalog rows are tenant-agnostic by design — NCA ECC, ISO 27001, SAMA CSF, PCI-DSS, etc.); tenant scoping happens at the EvidenceItem level via `itemRepo.findBySubControl(org, …)` | ✅ DONE (intentional) |
| **T1.5** | README refresh | `README.md` already reflects 25 modules (not the stale "10"), `tss-grc-ui-v2/` is canonical, ports 8084/5173 documented, Sprint-4 referenced. Sprint-4 status block lives in this file | ✅ DONE |
| **T1.6** | Authorization gaps | `DashboardController` — every endpoint has `@PreAuthorize("hasPermission(…,'READ')")` or `@PreAuthorize("hasAnyAuthority(…)")`; `/my-tasks` legitimately uses `isAuthenticated()` (personal scope). `OrganisationController.accessible` is `isAuthenticated()` (correct — the lambda inside scopes by PLATFORM_ADMIN); `switchOrg` is `hasRole('PLATFORM_ADMIN')` with defensive belt-and-braces check inside | ✅ DONE |

### Track-4.4 — Operational telemetry verification

Also already-shipped — captured here so it isn't audited again:

- **Micrometer Prometheus** — `micrometer-registry-prometheus` is on the classpath; `application-prod.yml` exposes `/actuator/prometheus` on **separate management port 8090** (not the app port 8443), with HTTP percentile histograms enabled and `application=tss-grc-platform` + `environment=prod` tags
- **Sentry** — `io.sentry:sentry-spring-boot-starter-jakarta` on classpath; `application-prod.yml` configures DSN via `${SENTRY_DSN:}` env var (no-op when unset)
- **Structured logging** — Logback JSON encoder profile-gated for prod

### Track-4.5 — Bilingual coverage verification

- **ReportSchedule** — `nameAr` + `descriptionAr` columns added in V20; entity reflects them at `ReportSchedule.java:36/42`
- **LifecycleOverviewPage** — uses `t()` keys for all JSX strings; inline `labelEn`/`labelAr` pairs in stage arrays are data, not hardcoded JSX (acceptable pattern)
- **Zero AR strings outside i18n** for new committees + SOC ops pages (verified via Saudi-native Arabic compliance pass)

### What genuinely remains (Sprint-4 Tracks 2 + 3 — deferred to Sprint-5)

These items have material scope and need explicit product / dev capacity allocation — not silent wiring:

| Track | Item | Scope |
|---|---|---|
| **2.1** | Replace `DataTable` with TanStack Table v8 + react-virtual | ~10 days; adds `@tanstack/react-table` + `@tanstack/react-virtual` deps; migrate 30+ list pages |
| **2.2** | Chart library — Recharts 2.x | ~7 days; adds `recharts` dep; migrate 5 dashboards from hand-rolled SVG |
| **2.3** | Filter expression builder (AND/OR + URL-serialised) | ~4 days |
| **2.4** | Generic `RecordPicker` + `recordRegistry` | ~3 days |
| ~~2.5~~ | ~~Saved views Phase 2 — backend~~ | ✅ DONE on 22 May 2026 (PR #108) — V160 migration + REST + frontend write-through |
| **2.6** | Bulk actions framework | ~3 days |
| **2.7** | WebSocket / SSE real-time layer (STOMP) | ~5 days; touches ~6 services + topbar |
| **2.8** | Mobile responsiveness (7 priority pages) | ~5 days |
| **2.9** | Knowledge base + contextual help (V21 schema) | ~4 days — *partially done; `KbArticlePage` + `kb.ts` already shipped* |
| **3.1** | Demo-page triage workshop (39 pages → WIRE/CULL/DEFER) | Product decision; not a coding task |

### Active rule count snapshot

| Source | Range | Count |
|---|---|---|
| V96 baseline | AR-001..AR-020 | 20 |
| V128..V149 | AR-021..AR-115 | 95 |
| V151 SOC ops baseline | AR-116..AR-130 | 15 |
| V153 SOC ops Tier-2/3 | AR-131..AR-145 | 15 |
| V158 Committees | AR-146..AR-155 | 10 |
| V159 Committees demo expansion | (extends V157/V158) | — |
| **Total** | — | **155** |

(Note: STATUS earlier said 175 — actual catalog count from migrations is 155 active rules. Difference came from intermediate iterations during the multi-PR arc.)

---

## 🟢 Earlier — SOC ops detail pages (22 May 2026, PR #100 cont.)

Closes the "register row clicks go nowhere" UX gap. Single generic
`SocOpsDetailPage` at `/socops/:resource/:id` covers every entity via
a `RESOURCE_CONFIG` map (fetcher, audit-log entityType, headline builder,
ordered field list with formatters). 14/19 register pages link the
code-first cell into the detail page on this PR; the remaining 4 with
non-code first cells (ThreatIntel, Scanner Runs, AppSec, DLP) can be
linked in a follow-up.

The detail page always drops in `EntityActivityTimeline` with the right
`entityType` — clicking a row now shows the entity's fields + the full
audit log + any F8 rule fires that touched it.

### Pages linked

| Register | Detail route | Linked field |
|---|---|---|
| SocOpsEventsPage | `/socops/events/:id` | `ruleName` |
| IrPlaybooksPage | `/socops/playbooks/:id` | `playbookCode` |
| PentestsPage | `/socops/pentests/:id` | `engagementCode` |
| RegulatorExamsPage | `/socops/regulator-exams/:id` | `examinationCode` |
| OtAssetsPage | `/socops/ot-assets/:id` | `assetCode` |
| CryptoAssetsPage | `/socops/crypto-assets/:id` | `assetCode` |
| AwarenessPage | `/socops/awareness/:id` | `campaignCode` |
| IamJmlPage | `/socops/iam-jml/:id` | `eventCode` |
| CyberInsurancePage | `/socops/cyber-insurance/:id` | `policyNumber` |
| BrandMonitoringPage | `/socops/brand-monitoring/:id` | `alertCode` |
| ForensicsPage | `/socops/forensics/:id` | `artefactCode` |
| MaDueDiligencePage | `/socops/ma-due-diligence/:id` | `engagementCode` |
| ZtMaturityPage | `/socops/zt-maturity/:id` | `assessmentCode` |
| SeasonalOverlaysPage | `/socops/seasonal-overlays/:id` | `overlayCode` |

The 3 entities without a backend get-by-id endpoint (SBOM, ZT maturity,
seasonal overlays) render an Empty state in the detail page — the route
exists for forward-compat but the API call resolves to a stub.

---

## 🟢 Earlier — SOC ops F8 loop closed end-to-end (22 May 2026, PR #100)

Single PR, five commits, closes the loop between the SOC ops rule catalog
(AR-116..AR-145 from V151 + V153) and live operator + sweep signals. Before
this PR the catalog was 30 declarative entries with nothing on either end;
after, every rule has either a service-side publisher or a scheduled sweep
firing real `DomainEvent`s into the F8 engine.

### What landed

| Commit | Theme | Files |
|---|---|---|
| `72d6e92` | V153 SQL migration + 5 priority register pages | +6 |
| `165fca0` | 14 catalog completion register pages | +14 |
| `ffedbe6` | DomainEvent publishers wired into 13 SOC ops services | +13 |
| `4551fc8` | Hourly scheduled sweep + 2 repo helpers (6 triggers) | +3 |
| `8bf7e5a` | 5 more sweep triggers + MaDueDiligence FE/BE field fix | +8 |

### Service-side publishers (state-change triggers)

13 services now broadcast on the verbs that map 1-1 to rule triggers:

| Service | Triggers |
|---|---|
| SocOpsIngestService | SECURITY_EVENT_INGESTED, SECURITY_EVENT_CRITICAL_NEW, SCANNER_RUN_COMPLETED, SCANNER_RUN_CRITICAL_FOUND |
| SecurityEventService | SECURITY_EVENT_TRIAGED, SECURITY_EVENT_RESOLVED |
| IrPlaybookService | IR_PLAYBOOK_INVOKED |
| PentestEngagementService | PENTEST_REPORT_DELIVERED (transition-guarded) |
| RegulatorExaminationService | REGULATOR_EXAM_NOTIFIED |
| ThreatIntelService | TI_ACTIONABLE (HIGH/CRITICAL only) |
| OtAssetService | OT_REMOTE_ACCESS_ENABLED (OFF→ON only) |
| IamJmlEventService | IAM_SOD_VIOLATION, IAM_JML_PRIVILEGED_COMPLETE |
| AppsecFindingService | APPSEC_CRITICAL_FINDING, APPSEC_SLA_BREACHED |
| DlpEventService | DLP_CRITICAL_UNREVIEWED, DLP_TOP_SECRET_BLOCKED |
| BrandMonitoringService | BRAND_CRITICAL_ALERT |
| ForensicsArtefactService | FORENSICS_INTEGRITY_LOST |
| MaDueDiligenceService | MA_DD_PHASE_TRANSITION |

State-snapshot guards on the transition-only triggers (PENTEST→REPORT_DELIVERED,
OT remote OFF→ON, IAM SoD first-trip, IAM privileged→COMPLETED) prevent
refiring on every subsequent save of an already-tripped row.

### Scheduled sweep — `SocOpsScheduledSweep`

Single hourly `@Scheduled` component covering 10 elapsed-time triggers no
operator action transitions across. Idempotent via audit-log marker
(`SOC_FIRE:<TRIGGER>` — short prefix fits the 40-char `action_type` column):

| Trigger | Window | Source |
|---|---|---|
| SECURITY_EVENT_SLA_APPROACHING | ≤30 min ahead | SecurityEvent.findApproachingSlaBreach |
| SECURITY_EVENT_SLA_BREACHED | past sla_due_at | same |
| REGULATOR_EXAM_RESPONSE_DUE | ≤14 days | new `findResponseDueBetween` |
| CYBER_INSURANCE_RENEWAL_90D | (60d, 90d] | existing `findRenewalsDueBetween` |
| CYBER_INSURANCE_RENEWAL_30D | (0d, 30d] | same |
| IAM_PRIVILEGED_REVIEW_DUE | ≤14 days | existing `findPrivilegedReviewsDueBetween` |
| CRYPTO_CERT_EXPIRING_30D | ≤30 days | existing `findExpiringBetween` |
| CRYPTO_CERT_EXPIRED | past valid_until | same |
| PENTEST_RETEST_DUE | ≤7 days | new `findRetestDueBetween` |
| SEASONAL_OVERLAY_ACTIVATED | starts_at = today | new `findStartingBetween` |
| FORENSICS_HOLD_DISPOSAL_DUE | legal hold + retention ≤14d | new `findHoldDisposalDueBetween` |
| APPSEC_SLA_BREACHED (over-time) | sla_due_at past, not closed | new `findSlaBreached` |
| DLP_CRITICAL_UNREVIEWED (over-time) | CRITICAL + unreviewed >24h | new `findCriticalUnreviewedBefore` |

### Frontend register pages

19 new `/socops/*` pages — 5 priority (SOC events queue, IR playbooks, pentests,
regulator exams, OT assets) + 14 catalog (threat intel, scanner runs, crypto
assets, SBOM, ZT maturity, seasonal overlays, awareness, IAM JML, AppSec, DLP,
cyber insurance, brand monitoring, forensics, M&A DD). All Saudi-native Arabic
labels (Pareto / connectors / catalog / board pack), all lazy-loaded routes.

### MaDueDiligence FE/BE alignment fix

The v2 page type was fabricated against an aspirational schema. Now mirrors
the entity verbatim — `targetEntityName` (not `targetCompanyName`),
`cyberRecommendation` (not `overallVerdict`), `findingsCritical`/`findingsHigh`
exposed in the row, PIF chip removed because the entity has no PIF flag.

### Out-of-tree (Track C, by design)

External adapter projects — Splunk TA, QRadar app, Sentinel logic app,
Tenable/Qualys/Defender connectors, Veracode/Snyk/Checkmarx plugins, Purview
DLP forwarder, Recorded Future/Digital Shadows webhooks — are separate
adapter-team scope, not platform engineering. PR #100 ships the platform
side: ingestion endpoints (`/api/socops/ingest/*`), receivers (the V150
service + the V152 entities), the F8 rule catalog, and the publisher/sweep
wiring that lets the rules fire.

---

## 🟢 Latest — Committees & Meetings GRC-SME platform (22 May 2026, PRs #101–#103)

Repositions the committees module from a basic register (V40 — just a
`committees` table + meeting `decisions_json` blob) into a **real day-
to-day operational platform** with full Saudi GRC content. Catalog +
operational layer + worked examples + F8 rules + scheduled sweep — all
in one PR.

### What lands

| # | Commit | What |
|---|---|---|
| 1 | `84950b0` | **V154-V156** schema (8 new tables) + 19 Saudi-regulated committee templates + ~150 standing items + 10 bilingual form templates (~2,591 LOC) |
| 2 | `e73281c` | **Catalog backend** — 3 entities + 3 repos + service + REST controller exposing `/api/v1/committees/catalog/*` |
| 3 | `adce1f1` | **Catalog frontend** — 3 React pages (catalog browser, charter detail, form viewer) + sidebar + translations |
| 4 | `b3e2acd` | **Operational backend** — Resolution + ActionItem entities + repos + services + controllers + Annual Calendar generator + Agenda auto-populator |
| 5 | `5b22b51` | **Operational frontend API** — 17 API functions covering resolutions / actions / calendar |
| 6 | `6133746` | **Entity fix** — added V154 columns to Committee.java (`charterTemplateCode`, `regulatoryDomain`, etc.) so the catalog code compiles |
| 7 | `dcb5e1e` | **V157 worked examples + V158 F8 rules + CommitteeScheduledSweep** — 8 resolutions + 9 actions seeded from V40 demo meetings, 10 automation rules, 3 time-based sweeps |

### 19 Saudi-regulated committee templates (V154)

| Committee | Regulator anchor | Cadence |
|---|---|---|
| Board of Directors | CMA Corp Gov + Companies Law | Quarterly |
| Audit Committee | CMA Art. 14 + SAMA | Quarterly |
| Risk Committee | SAMA RGov 2017 + Basel III | Monthly |
| Nomination & Remuneration | CMA Art. 22-23 | Semi-annual |
| Compliance Committee | SAMA + AML Law Art. 41 | Monthly |
| Cyber Security Committee | NCA ECC-2 control 1-2-1 | Monthly |
| Data Governance Committee | NDMO + SDAIA | Monthly |
| Privacy / DPO Committee | PDPL Article 30 | Monthly |
| BCM Steering Committee | ISO 22301 + SAMA BCM | Quarterly |
| Crisis Management Committee | NCEMA + SAMA Crisis | Ad-hoc |
| AML Committee | SAMA AML Manual + AML Law | Monthly |
| IT Steering Committee | NCA + COBIT 2019 | Monthly |
| ALCO (banking) | SAMA + Basel III | Monthly |
| Investment Committee | CMA + sector | Monthly |
| AI Ethics Committee | SDAIA AI Ethics Principles | Quarterly |
| Anti-Fraud Committee | SAMA + ACFE | Monthly |
| Internal Control Committee | SAMA + COSO ICF 2013 | Quarterly |
| ESG / Sustainability Committee | CMA + Saudi Green Initiative | Quarterly |
| Architecture Review Board | NCA + CCC + TOGAF | Bi-weekly |

Each template carries 2-3 page bilingual content covering mandate,
authority, composition, quorum, cadence, term-of-office, reports-to,
escalation chain, regulator citation, independence + financial-expert
flags, regulatory reporting expectations. **Saudi-native Arabic, not
literal translations.**

### 10 bilingual form templates (V156)

CHARTER · AGENDA · PRE_READ_COVERSHEET · ATTENDANCE · MOTION · MINUTES
· ACTION_REGISTER · COI_DECLARATION · SKILLS_MATRIX · ANNUAL_EVALUATION
— ~50-100 lines each of real bilingual markdown with `[BRACKETED]`
placeholders for platform auto-fill.

### Operational layer — full lifecycle on first-class entities

**Resolutions** — replaces V40 `decisions_json` blob with structured
rows. Lifecycle: created at meeting time with vote breakdown + dissent
record + regulatorNotify flag, becomes immutable. F8 fires
`COMMITTEE_RESOLUTION_<OUTCOME>` on every creation.

**Action items** — replaces V40 `action_items_json` blob with full
lifecycle: OPEN → IN_PROGRESS → COMPLETED (+ CANCELLED, DEFERRED,
OVERDUE side branches). Cross-module linkage (`linkedModule`,
`linkedEntityType`, `linkedEntityId`, `linkedEntityCode`) lets the
platform navigate from a stale risk-register entry back to the
committee where its remediation was discussed.

**Annual calendar generator** — `POST /api/v1/committees/calendar/generate-calendar/{committeeId}?year=2026`
creates 4 / 12 / 26 / 52 scheduled meetings ahead based on the committee's
cadence, pre-loading each agenda with the template's standing items.
Anchored to 2nd Tuesday of each cadence period (Saudi-workweek-friendly).
Idempotent.

**Agenda auto-populator** — `POST /api/v1/committees/calendar/populate-agenda/{meetingId}`
refreshes the agenda by combining standing items + open actions from
prior meetings. Always pre-fills items 1-3 (welcome+quorum, minutes
approval, matters arising with auto-count of open actions).

### F8 automation rules AR-146..AR-155 (10 rules, V158)

- **AR-146** Resolution with regulatorNotify → page Compliance + GC + DPO (24h SLA)
- **AR-147** Board resolution APPROVED → board secretariat notification
- **AR-148** Action OVERDUE → escalate owner → line manager → Chair
- **AR-149** CRITICAL action created → owner SMS within 1h
- **AR-150** Risk-linked action COMPLETED → update risk timeline
- **AR-151** Audit-linked action COMPLETED → advance CAPA stage
- **AR-152** Resolution DEFERRED → re-table at next meeting
- **AR-153** Action CANCELLED → notify Chair for review
- **AR-154** Resolution review due in 30d → reminder
- **AR-155** Charter review due in 30d → reminder

### Scheduled time-based triggers (CommitteeScheduledSweep)

Hourly `@Scheduled` component, idempotent via audit-log marker `COM_FIRE:<TRIGGER>`:
- **COMMITTEE_ACTION_OVERDUE** — flips action status + fires AR-148
- **COMMITTEE_RESOLUTION_REVIEW_DUE_30D** — fires AR-154
- **COMMITTEE_CHARTER_REVIEW_DUE_30D** — fires AR-155

### Worked-example seed (V157)

8 resolutions + 9 actions migrated from V40 demo meetings into the new
first-class tables. Real Saudi-context content: Aramco-contract-delay
scenario, M365 Copilot rollout under SDAIA + NCA ECC, BCR for TCS-India
transfer, FY2025 KPMG audit endorsement, NCA ECC backup-verification
gap closure, journal-posting SoD remediation. Vote breakdowns realistic
(3-5 voting members), dissent recorded per CMA Art. 31 where relevant.

### PRs #102 + #103 — UI integration + follow-ups complete

**PR #102 (UI)** — Resolutions + Actions register tabs on `CommitteesPage`
with cross-module chip routing (RISK→/risk, INCIDENT→/incidents,
COMPLIANCE→/compliance, etc.). "Generate Annual Calendar" + "Populate
Agenda" buttons. 28 new Saudi-native Arabic translation keys.

**PR #103 (follow-ups)** — V159 demo expansion (+5 committees:
Compliance / Cyber / Privacy / AML / AI-Ethics; +6 MINUTES_APPROVED
meetings; +9 resolutions; +11 cross-module-linked actions) +
`CommitteeMemberTerm` + `CommitteeMemberSkill` + `CommitteeMeetingPaper`
entities/repos/services + REST controllers + `CommitteeFormExportService`
(OpenPDF, Arabic RTL) + `/committees/:committeeId/members`
registry page with independence-ratio KPI (CMA Art 54).

### Active rule count

| Source | Range | Count |
|---|---|---|
| V96 baseline | AR-001..AR-020 | 20 |
| V128 CAPA | AR-021..AR-030 | 10 |
| V129 Finding/CAPA stage | AR-031..AR-035 | 5 |
| V130 Finding lifecycle | AR-036..AR-040 | 5 |
| V131 BCM AAR | AR-041..AR-045 | 5 |
| V132 Risk | AR-046..AR-050 | 5 |
| V135 Calendar reminder | AR-051..AR-055 | 5 |
| V138 Incident | AR-056..AR-060 | 5 |
| V139 Vulnerability | AR-061..AR-065 | 5 |
| V140 Approval | AR-066..AR-070 | 5 |
| V141 Policy | AR-071..AR-075 | 5 |
| V142 Evidence | AR-076..AR-080 | 5 |
| V143 Consent | AR-081..AR-085 | 5 |
| V144 DSR | AR-086..AR-090 | 5 |
| V145 DPIA + cross-border | AR-091..AR-095 | 5 |
| V146 Change mgmt | AR-096..AR-100 | 5 |
| V147 Treatment plan | AR-101..AR-105 | 5 |
| V148 Regulatory change | AR-106..AR-110 | 5 |
| V149 BCM issue | AR-111..AR-115 | 5 |
| **V151 SOC ops baseline** | **AR-116..AR-130** | **15** |
| **V153 SOC ops Tier-2/3** | **AR-131..AR-145** | **15** |
| **V158 Committees** | **AR-146..AR-155** | **10** |
| **V159 Committees demo expansion** | (extends V158 + V157) | — |
| **Total** | — | **155** |

### Pending follow-ups — ALL CLOSED in PRs #102 + #103

- ✅ Frontend UI tabs in CommitteesPage for Resolutions + Actions (PR #102)
- ✅ "Generate Annual Calendar" + "Populate Agenda" buttons (PR #102)
- ✅ Action-item detail view with cross-module link (PR #102)
- ✅ Member terms + skills matrix UI (PR #103 — `CommitteeMemberRegistryPage`)
- ✅ PDF/DOCX export of forms (PR #103 — `CommitteeFormExportService` via OpenPDF)
- ✅ More worked-example meetings (PR #103 — V159 +5 committees, +6 meetings)
- ✅ Pre-read papers tracking (PR #103 — `CommitteeMeetingPaper` entity + REST)

---

## 🟢 Earlier — UX polish foundation (22 May 2026, PRs #90-91)

Cross-app design audit + first two UX polish PRs landed:

### PR #90 — bilingual status labels, shared layout classes, a11y

Three high-impact UX foundations shipped together:

- **`lib/statusLabels.ts`** — shared `statusLabel(value, lang)` helper covering ~50 status enums the platform uses (risk, incident, capa, vulnerability, DSR, consent, change management, regulatory change, policy, evidence). Companion helpers for severity/priority/strategy. Falls back to title-cased English when an enum isn't in the table.

- **Shared layout classes in `shell.css`** — five new utilities replace recurring inline-style recipes: `.kpi-strip-4`, `.chip-row` + `.chip-row__count`, `.list-detail-grid`, `.loading-block`, `.table-row--selected` + `.table-row-clickable`.

- **A11y on filter chips** — ControlLibraryPage domain-filter chips now carry `aria-label` + `aria-pressed`.

Smoke test: RiskRegisterPage status badges + ControlLibraryPage KPI strip / chip row.

### PR #91 — extend polish to more pages

Applied the shared infra to four more pages:
- PosturePage + RegulatoryLibraryPage → use `.kpi-strip-4` (dropped inline grid styles)
- BcmReadinessPage → `statusLabel(c.status, lang)` on cycle + risk badges
- BcmRemediationPage → `statusLabel` in CapasTab sub-component (derives lang from isAr prop)

### Strategy

Rather than ship one massive sweep that risks breakage, each polish PR establishes shared primitives (helpers + CSS classes) then applies them to one more page per area. Future polish PRs can roll out the same primitives without re-inventing them.


**Snapshot Date:** 22 April 2026 (initial), updated continuously
**Branch Source:** `claude/quirky-borg` (commit `726cd1e`)
**Stack:** Spring Boot 3.5 / Java 17 / SQL Server 2019 / React 18 + Vite / Bootstrap 5 RTL / Chart.js 4

---

## 🟢 Latest — Merge sweep + final services (22 May 2026, PRs #75-88 all merged)

Fourteen PRs merged into master in a single session, taking the F7/F8 parity arc from 10 services (post-PR-#77) to **20 services with 115 active seed rules** and closing every dashboard FALLBACK.

### Merged PRs
| Range | Theme | Services / pages affected |
|---|---|---|
| #75-77 | F7/F8 parity arc | Vulnerability, ApprovalRequest, Policy |
| #79-85 | F7/F8 parity arc | Evidence, Consent, DSR, DPIA+CrossBorder, Change Mgmt, Treatment, RegChange |
| #86 | Cyber dashboard | wired to real API + loading/error/retry + extracted CSS |
| #87 | Sibling dashboards | shared useDashboardLoader hook + Skeleton/Error for BCM/ERM/PDPL/SCM |
| #88 | F7/F8 parity arc | BcmCycle, Issue (services #19-20) |
| #78 | STATUS doc | refresh capture (now superseded by this section) |

### Full parity arc — 20 services, 115 active seed rules

| # | Service | Trigger families | Seed rules |
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
| 19 | BcmCycleService | BCM_CYCLE_* + BCM_GOVERNANCE_* | (V149 shared) |
| 20 | IssueService | ISSUE_* | AR-111..AR-115 |

### Test suite health
- Full backend test suite: **790 tests, 0 failures, 0 errors** on the merged master (post-PR-#88 master adds the BCM + Issue F8 wiring without regressing any existing test class)
- Each PR was self-contained with mock updates to ensure `@Mock AuditLogService` + `@Mock ApplicationEventPublisher` were present in any test class touching a newly-wired service

### Domain dashboards now consistent
All five domain dashboards (Cyber, BCM, ERM, PDPL, SCM) share the `useDashboardLoader<T>` hook and `<DashboardLoading />` + `<DashboardError />` components — no more silent fallback to fake numbers, no more first-paint placeholder data.

---

## 🟢 Earlier — Domain dashboard polish sweep (PRs #86-87)

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

