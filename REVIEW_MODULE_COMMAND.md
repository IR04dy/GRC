# Review module: — expert & real-user violent review command (loop + ultracode)

> **الغرض** — أمر جاهز للتشغيل يراجع موديول الأمن السيبراني + عمليات SOC مراجعةَ **خبير GRC** و**مستخدم فعلي** معاً:
> يبني أولاً **قائمة المهام الكاملة** كما يتطلبها السوق السعودي (من أول مهمة إلى آخرها) ويتأكد أنها **منعكسة فعلاً في النظام**،
> **يصمّم كل عملية قبل اختبارها** ويقيس مطابقتها للواقع واحتياجات السوق، ثم **يختبر كل زر وتبويب ونموذج ونقطة API اختباراً عنيفاً بتنفيذ حقيقي** —
> ليكشف "الجزر المنفصلة" والواجهات الشكلية (فورم بلا رفع ملف، طلب أدلة بلا سير عمل/عدّاد تنازلي/تذكيرات…) —
> وينتهي بـ **خطة إصلاح** في ملف Markdown تُنفَّذ لاحقاً. المراجعة **قراءة واختبار فقط** — لا تعديل على كود المنتج في هذه المرحلة.

**Deliverable:** `CYBERSECURITY_SOCOPS_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (repo root) — same review → plan → implement-loop pattern used for ERM / ESG / Compliance / BCM.
**Evidence dir:** `review/cyber-socops/` (progress file + per-phase receipts).

---

## How to run

**Recurring loop (recommended — one phase per fire, ~9 fires total):**

```
/loop 30m <paste the ENTIRE command block below>
```

**Single deep session (no loop):** paste the block once, appending this line at the end:
`Override: run ALL phases back-to-back in this single session, still updating PROGRESS.md after each phase.`

When all phases are done the loop self-reports *"review complete — plan ready"* and idles; cancel it then.

---

## The command — copy the whole block

```text
Review module: CYBER + SOCOPS — ultracode — expert & real-user VIOLENT review (REVIEW-ONLY pass).

MODULE   = Cybersecurity (module/cyber — 5 controllers: CyberController, CloudServiceController,
           AttackSurfaceController, ThreatIntelController, VulnerabilitySlaController; /cyber workspace = 13 tabs)
         + SOC Operations (module/socops — 20 aggregates in 6 bands; /api/v1/socops/*; /socops pages).
MAPS     = docs/CYBERSECURITY_MODULE_MAP.md + docs/SOC_OPERATIONS_MODULE_MAP.md
           — treat the maps as CLAIMS to verify against code / DB / the LIVE app, never as truth.
PLAN-OUT = CYBERSECURITY_SOCOPS_MODULE_REVIEW_AND_REMEDIATION_PLAN.md (repo root).
PROGRESS = review/cyber-socops/PROGRESS.md — read it FIRST every fire; do the NEXT pending phase;
           ONE phase per fire; update PROGRESS and report before ending the turn.
           If PROGRESS shows all phases done: report "review complete — plan at PLAN-OUT" and stop.

PERSONA — act as BOTH at once:
 (1) Senior GRC/cyber platform consultant (ServiceNow SecOps/IRM, RSA Archer, MetricStream rollouts for
     Saudi banks; NCA ECC-2:2024 + CSCC, SAMA CSF, ISO 27001:2022 Annex A, ISO 27035, NIST CSF 2.0 +
     SP 800-61r3, PDPL context).
 (2) The daily user (CISO / SOC analyst / GRC officer) who clicks EVERY button and expects REAL work to
     happen: assignment to other departments, due dates with countdown, periodic reminders, escalation on
     breach, file attachments, approvals, notifications, audit trail — not a demo façade.

MISSION — answer with receipts: is this a REAL operating system for security governance, or disconnected
islands / display-only screens? The user already observed façade symptoms — treat them as SEED FINDINGS:
verify them first, then hunt the same pattern across every form and flow in scope:
  S1. Create-evidence / create-policy popup forms offer NO file-upload field.
  S2. "Request evidence" does NOT open a workflow: no request task routed to another department, no
      assignee, no due date with countdown, no periodic reminders, no escalation, no fulfil→attach→approve.

PHASES (one per fire):

P0  EXPERT REFERENCE MODEL — WITHOUT looking at the app: write the complete end-to-end task inventory a
    Saudi-market cyber-GRC + SOC module must support, first task to last, grouped by capability
    (governance & framework controls; assessments→gaps→CAPA; policy lifecycle; evidence lifecycle incl.
    request/collect/review/approve; asset & attack surface; vulnerability & SLA; threat intel; incident
    response E2E; SOC monitoring & triage; forensics; awareness & exercises; reporting/KPIs/board).
    For EVERY process: actors & RACI, trigger, steps, states, inputs/outputs, cross-department
    interactions, SLA/countdown/reminder/escalation expectations, attachments, approvals, notifications,
    audit receipts, reports. Source = the frameworks above + market-leader behaviour.
    → review/cyber-socops/00-reference-model.md

P1  COVERAGE MATRIX — map every P0 process onto the system (MAPS → code → DB → API). Verdict per process:
    REAL / PARTIAL / FAÇADE (screen or endpoint exists but no working flow behind it) / MISSING.
    Then the REVERSE sweep: every tab, link, button, popup form and endpoint listed in the two MAPS'
    catalogues must appear in the matrix — no orphans; anything the app exposes that P0 lacks becomes a
    row too. → review/cyber-socops/01-coverage-matrix.md

P2–P7  DESIGN-THEN-VIOLENT-TEST — one wave per fire; every tab/endpoint belongs to exactly ONE wave:
    W1 governance: framework controls, assessments→gaps→CAPA, policies + evidence create/request
       (seed findings S1 + S2 live here — test them first)
    W2 incident response E2E: cyber 9-step incident lifecycle + socops security-event triage→resolve +
       IR playbook clone→invoke
    W3 vulnerabilities: registry + 6-step lifecycle, SLA engine (formula, WARNING/BREACHED states),
       exceptions, ASM findings, shadow IT
    W4 SOC ingest & ops: SIEM/scanner/DLP/AppSec/SBOM/IAM-JML ingest roles, forensics chain-of-custody,
       threat intel
    W5 remaining socops bands: brand protection & takedown, M&A due-diligence, insurance/pentest/
       privileged-access reviews, scheduled sweeps
    W6 cross-cutting: dashboards & KPIs, exports (Excel + Arabic PDF), i18n AR/EN + RTL (titleEn/titleAr
       both populated), RBAC, tenant isolation, notifications & scheduler, cyber↔evidence/risk/compliance
       bridges (F8 events actually consumed, not just published)
    Per wave, strictly in this order:
    a) DESIGN FIRST — write the expected behaviour per P0: the full user journey with the receipt
       expected at each step (state change, DB row, notification, audit entry). Judge match-to-reality
       and market fit BEFORE touching the app.
    b) EXECUTE EVERYTHING live and violently:
       • every endpoint with a real JWT (login admin/Admin@12345 → :8084): happy path AND invalid
         payloads AND forbidden-role AND cross-tenant IDOR probes (org-spoofed ids);
       • every UI tab / link / button / popup on :5173: form-field completeness (file upload wherever
         evidence/policy/attachment is implied!), submit → DB persistence proven via pyodbc, the promised
         workflow actually opening (assignment / due date / countdown / periodic reminders / escalation),
         notifications dispatched, F7 audit rows written, F8 events observed, state machines rejecting
         illegal transitions, lists reflecting the change.
    c) VERIFY LIVE, never by code-reading alone — schedulers (SocOpsScheduledSweep, SLA/reminder jobs)
       must be PROVEN running via logs/last-run rows. Past incident in this repo: a request-scoped bean
       silently killed all schedulers while the code looked fine.
    d) RECORD verbatim receipts (HTTP status + response snippet, SQL row, UI snapshot ref) →
       review/cyber-socops/1N-wave-<name>.md, with verdict REAL/PARTIAL/FAÇADE/MISSING + severity +
       UX notes written as the daily user.
    Test-data hygiene: every row you create is prefixed [REVIEW-TEST]; soft-delete them at wave end.
    NO destructive DB ops (UPDATE/DELETE/TRUNCATE/DROP touching non-[REVIEW-TEST] data) — ever, without
    my explicit confirmation. INSERTing a role grant to get past a role gate is allowed (log it in
    PROGRESS — the gate blocking even seeded admins is itself a finding).

P8  CONSOLIDATE → write PLAN-OUT with exactly these sections:
    1. Executive verdict (Usable / Partially-usable / Demo-grade) + an "islands map" showing exactly
       where process continuity breaks between screens/modules.
    2. Findings register: ID, severity (Critical/High/Medium/Low), type (Bug / Façade / Missing
       capability / Integration gap / UX / RBAC / i18n / Data-quality), evidence pointer. No
       Critical/High enters the register without a reproducible receipt — adversarially re-verify each
       one (ultracode verifiers) before it lands.
    3. Remediation epics CY-A, CY-B, …: problem → evidence → target design (the exact P0 process it
       realizes) → implementation outline (BE/FE/DB; new forward-only migration VNNN — NEVER edit an
       applied migration) → acceptance criteria incl. the exact live test that must pass → effort S/M/L.
    4. §implementation-order: dependency-ordered so a later implement-loop runs ONE epic per fire.
    PLAN MANDATE — the plan MUST do all three, not just patch:
      (a) FIX what exists and is broken;
      (b) ADD what the reference model requires and the app lacks — e.g. a real evidence-request
          workflow: request → assign department/owner → due date + countdown → periodic reminders →
          escalation on breach → fulfil with file upload → review/approve → full audit trail;
      (c) REDESIGN existing flows where analysis shows material improvement — every redesign epic must
          state why the current design fails the reference model and what measurably improves.
    Then STOP. Review-only: no product-code changes, no implementation, and no commit/merge/push of
    anything unless I explicitly say so (review artifacts may sit uncommitted in the working tree).

ENVIRONMENT & KNOWN FACTS (do not re-derive; do not misreport):
 - Backend :8084 (Spring Boot, dev profile). Restart recipe: STOP the :8084 process FIRST, then
   mvn -q -DskipTests package, then start the jar with JWT_SECRET / DB_PASSWORD taken from the
   environment — never print secrets in output or files.
 - Frontend :5173 is a DURABLE Vite process — never kill or restart it. If preview MCP can't attach,
   verify UI via claude-in-chrome MCP, HTTP-fetching the compiled modules, plus API + DB receipts.
 - DB: SQL Server TSS_GRC via pyodbc (ODBC Driver 17, localhost, sa, password from env). Dev tenant
   org_id = 1. Flyway is OFF in dev — schema changes are proposals for the plan, not to apply now.
 - Dev RBAC catalogue is known-thin (CYBER effectively admin-only; socops gates on hasAnyRole(...);
   machine-ingest roles may be unseeded). Thinness is a finding domain, not a blocker.
 - ultracode: fan each phase out with workflows/subagents (parallel per-endpoint testers, per-tab
   checkers, adversarial verifiers for every Critical/High) and synthesize centrally. IF subagent or
   workflow spawning fails in this environment (known model-availability issue), degrade to inline
   self-execution without stalling the phase.
 - If the stack is down, bring it up per the recipe first and note it in PROGRESS.

END OF EVERY FIRE, report in chat: phase completed, headline verdicts with receipts, running
FAÇADE/MISSING count, and the next pending phase.
```

---

## Phase → artifact map

| Fire | Phase | Output |
|---|---|---|
| 1 | **P0** Expert reference model (market-required task list, first→last) | `review/cyber-socops/00-reference-model.md` |
| 2 | **P1** Coverage matrix (REAL / PARTIAL / FAÇADE / MISSING, both directions) | `review/cyber-socops/01-coverage-matrix.md` |
| 3–8 | **P2–P7** Design-then-violent-test waves W1–W6 | `review/cyber-socops/1N-wave-*.md` |
| 9 | **P8** Consolidate: verdict + islands map + findings + epics + order | `CYBERSECURITY_SOCOPS_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` |

## Guardrails baked into the command

- **Review-only** — no product code changes, no implementation; the fix work happens later from the plan (its own implement-loop, one epic per fire).
- **No commit / merge / push** of anything unless explicitly ordered — review artifacts stay uncommitted in the working tree.
- **Design-before-test** — every wave writes the expected market-grade behaviour first, then tests against it; verdicts are per-process (REAL / PARTIAL / FAÇADE / MISSING), not vibes — every Critical/High needs a reproducible receipt and an adversarial re-verify.
- **Everything executed, nothing assumed** — all 5 cyber controllers + 13 tabs + all 20 socops aggregates; schedulers proven running live (logs/last-run rows), never assumed from code.
- **Data hygiene** — test rows prefixed `[REVIEW-TEST]`, soft-deleted at wave end; no destructive DB ops on real data, ever, without explicit confirmation; secrets only from env, never printed.
- **Durable stack respected** — never kill the :5173 Vite; backend restarts only via the stop-port-first recipe.
- **Fails soft** — if ultracode subagents can't spawn (known env issue), the phase runs inline instead of stalling.

## Reusing this command for another module

Change only these five things, keep everything else verbatim:

1. `MODULE =` — the module's packages, controllers, page routes.
2. `MAPS =` — that module's map doc(s) (write the map first if it doesn't exist — same pattern as the two cyber/socops maps).
3. `PLAN-OUT =` — `<MODULE>_MODULE_REVIEW_AND_REMEDIATION_PLAN.md`.
4. `PROGRESS` dir — `review/<module>/`.
5. Wave list W1–W6 + epic prefix (`CY-` → e.g. `TP-` for TPRM, `PR-` for Privacy).
