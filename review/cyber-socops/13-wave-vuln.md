# P4 / W3 — Vulnerability & SLA wave
### vuln registry + 6-step · SLA engine (formula, states, escalation) · exceptions · ASM/shadow-IT

Live tests; SLA seed **snapshotted and restored** (mutable cols back to original). Receipts:
`scratchpad/rev/w3_receipts.txt`. Grades: REAL / PARTIAL / FAÇADE / MISSING.

---

## B3 — Vulnerability SLA engine → **REAL logic + DORMANT scheduler** (the strongest flow, one critical caveat)

**Designed (P0 §B3):** severity→base SLA, accelerate by exposure/criticality, countdown, WARNING→BREACHED,
escalation + reminders, exception path. Saudi bar: NCA ECC-2-12 hours.

**Live receipts — the engine LOGIC is genuinely well-built and Saudi-native:**
- Policy `VLN-SLA-2026-DEFAULT-SAUDI`, bilingual, explicitly aligned to **NCA ECC-2 control 2-12**
  (CRITICAL 1h, HIGH 4h, MEDIUM 24h, LOW 72h), accelerated for public-facing / business-critical assets.
- `POST /vuln-sla/recompute` → `{touched: 9}`. The formula is **correct**:
  | Finding | sev/exposure/crit | base→ | effective→ |
  |---|---|---|---|
  | #7 | CRITICAL/PUBLIC/CRITICAL | 72→**1.0** | **0.25** (floor = 0.25×base) |
  | #8 | HIGH/INTERNAL/HIGH | →**4.0** | **3.0** |
  | #9 | HIGH/PARTNER/HIGH | →**4.0** | **2.25** (partner accelerates) |
  | #12 | CRITICAL/RESTRICTED/CRITICAL | 1.0 | 0.75 |
  (Terminal findings REMEDIATED/ACCEPTED_RISK correctly skipped — 11−2=9 touched.)
- `POST /vuln-sla/escalation/run` → `{warningsEmitted: 0, breachesEmitted: 7}` and stamped
  `last_escalation_at` on 7 findings (0→7). The escalation logic works and audits (`ESCALATION_BREACH`).
- Status model real: OPEN / WARNING / BREACHED / REMEDIATED / ACCEPTED_RISK / FALSE_POSITIVE (dashboard reflects all).

**The caveat (High):** *before* my manual calls, **`last_escalation_at` was NULL for all 11 findings and
`last_recomputed_at` was 2026-06-27** — a week stale — while 2 findings sat BREACHED. If the 5-minute
`@Scheduled VulnerabilitySlaScheduler` were live in the running process it would have stamped/escalated
them. It hasn't. **Strong evidence the escalation scheduler is dormant** (matches the known
request-scoped-bean landmine that silently kills schedulers). Definitive confirmation = wait one 5-min
interval and re-check `last_escalation_at` (folded into **W5 scheduler-proof**).

**Verdict: the SLA engine is the closest thing to REAL in the module — correct, Saudi-aligned formula +
working escalation logic — but it is only driven when a human POSTs the endpoint; the automation that
should make the clock tick is (apparently) not running.** So operationally it degrades to PARTIAL until
the scheduler is proven. Also: the **seed SLA hours are stale** (72h base on a CRITICAL) because nothing
has recomputed since Jun 27 — same dormancy symptom.

---

## B3b — Vulnerability 6-step lifecycle → **PARTIAL**
- `POST /cyber/vulnerabilities` → `workflowStatus=REGISTERED`. `escalate` ×6 advanced
  `REGISTERED→CLASSIFICATION→OWNER_ASSIGNMENT→REMEDIATION→VERIFICATION→CLOSED` (then stays CLOSED).
- Linear index-bump (same pattern as incidents). No actual owner assignment at OWNER_ASSIGNMENT (no
  assignee field set), no evidence gating, no per-step clock (the SLA engine is a *separate* object).
- **Verdict: PARTIAL** — real linear state machine; missing the assignment/clock substance.

## B3c — Vuln exceptions → **PARTIAL**
- `POST /cyber/exceptions` (REQUESTED) → 200. `POST /exceptions/{id}/approve` → 200.
- Approve returned **no `expiryDate`** — exceptions do not appear to be **time-boxed / auto-expiring**
  (P0 requires a time-boxed exception that re-opens the vuln on expiry). Confirm field naming in W-verify.
- **Verdict: PARTIAL** — request→approve works; time-box + auto-expire re-open not evidenced.

## B2 — ASM findings & shadow-IT → **NOT FULLY TESTED + VAL finding**
- Could not create `[REVIEW-TEST]` rows to exercise transitions: `POST /asm/assets` → **400** (validation),
  `POST /asm/findings` → **400**, `POST /asm/shadow-it` → **500 Internal Server Error** (unvalidated bad input,
  like the evidence 500 — a VAL gap).
- The transition/sanction/block endpoints + `ALLOWED_TRANSITIONS` maps exist (per catalogue + code) but
  their state-machine behaviour is **unconfirmed live** this wave (create-payload shapes needed).
- **Verdict: DEFERRED** (re-test with correct DTOs in W-verify) + **VAL-3**: `POST /asm/shadow-it` 500s on bad input.

---

## W3 findings
| ID | Finding | Severity | Receipt |
|---|---|---|---|
| SCHED-1 | **Vuln-SLA escalation scheduler dormant** — `last_escalation_at` NULL for all findings, last recompute 2026-06-27, breached findings never auto-escalated. The clock doesn't tick without a manual POST. | **High** | w3 SNAP/ESCALATE |
| DATA-1 | Seed SLA hours stale/un-recomputed (CRITICAL showing 72h base until manual recompute). | Medium | w3 FORMULA |
| B3c-1 | Vuln exceptions not visibly time-boxed (no expiry on approve). | Medium | w3 EXC |
| VAL-3 | `POST /asm/shadow-it` returns 500 (not 400) on invalid input. | Medium | w3 ASM |

## Credit
The SLA engine is real, correct, and Saudi-native (NCA ECC-2-12) — the best-engineered flow reviewed so
far. Its only gap is operational (dormant scheduler + stale seed), not design. Fixing the scheduler
elevates B3 to **REAL**.

## Running totals (W1–W3)
- **FAÇADE 2** (evidence, playbook-invoke) · **MISSING 1** (policy in cyber) · **PARTIAL 8** ·
  **REAL-logic-but-dormant 1** (SLA engine) · **High findings 4** (RBAC×2, ARCH-1, SCHED-1) ·
  **Medium ~8** (validation×3, i18n, dup tables, stale SLA, exception time-box, …).
- Systemic theme, now very clear: **"the clock" (OS-2) and cross-object routing are the module's
  pervasive gap** — the one place it's fully built (vuln-SLA) isn't running.
