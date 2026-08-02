/* ============================================================================
   V345 — A17-D3: the ECAB retrospective-ratification SLA gets columns, a
          bounded per-tenant policy, an escalation destination and a
          grandfather clause.

   ADR-017 D-3 decided "5 business days, per-tenant overridable; the clock
   starts at implementation; breach escalates to the CISO and lands in the
   unauthorised-change register", then amended itself three times because as
   written it could not be built. This migration lands the amended version.
   Idempotent: safe to run twice (dev applies by hand via pyodbc, Flyway off).

   ── WHAT WAS MEASURED BEFORE WRITING THIS, AND WHAT IT CHANGED ─────────────
   Re-measured 2026-08-02 in dev (TSS_GRC) AND production (TSS_GRC_Deployment),
   because the ADR's amendment was written on a dev-only reading:

     * `change_mgmt.change_requests` — 43 columns, no ratification / SLA /
       escalation column of any kind. Confirmed identical in both databases.
     * `ck_change_status` — 14 values in both. `PENDING_RATIFICATION` is NOT
       among them, and this file does NOT add it: see "why no new status".
     * The unauthorised-change register (CE-11) has no table in either
       database. Amendment 1's interim escalation is therefore load-bearing,
       not a convenience.
     * EMERGENCY changes: exactly 1 row per database (id=3, CHG-2025-0003,
       org 1, status IMPLEMENTED). The ADR knew that much.

   ⚠ AND ONE THING THE ADR DID NOT KNOW, WHICH CHANGES THE DESIGN:
   that row's `implementation_started` and `implementation_ended` are **both
   NULL**. The ADR states the clock "does have a legitimate start — that half
   of the decision survives contact with the schema". True of the schema;
   false of the data. The columns are only ever written by the IMPLEMENTING /
   IMPLEMENTED transitions, and this row was seeded straight into IMPLEMENTED,
   so the sole record the feature would act on has no clock value at all.

   That matters because of how SQL Server evaluates the obvious breach
   predicate. `WHERE ratification_due_at < SYSUTCDATETIME()` against NULL is
   UNKNOWN, which filters the row OUT — so the row would be silently reported
   compliant forever. That is the F2-62 shape exactly: a control that answers
   "fine" because it cannot answer at all. The design consequence is that
   PENDING must be **unable** to exist without a due date, enforced below by
   `ck_change_ratification_clock`, and that the un-clockable legacy row is
   labelled `GRANDFATHERED` rather than left to fall through the predicate.

   ── WHY NO NEW `status` VALUE (X-2 / S5 stays untouched) ───────────────────
   X-2 reserves `PENDING_RATIFICATION` for `ck_change_status` and warns that
   shipping state code before the constraint is runtime-breaking. This file
   ships neither: ratification is modelled as an **orthogonal** column, not a
   lifecycle status, because an emergency change awaiting ratification is
   genuinely IMPLEMENTED *and* genuinely awaiting ratification. Folding both
   into one `status` column would have to discard one of the two facts, and
   the one it would discard — "this is live in production" — is the one the
   FSC, the calendar and the KPI pack all read. So D-3 does not need S5, and
   the plan's "with S8" gating on this row is looser than it looked.

   ── THE SIX STATES, AND WHY THERE ARE SIX ──────────────────────────────────
     NOT_REQUIRED             not an emergency change, or not yet ECAB-approved
     AWAITING_IMPLEMENTATION  ECAB-approved, not yet implementing. The clock
                              has NOT started — by decision, it starts at
                              implementation. Neither compliant nor breached.
     PENDING                  clock running; `ratification_due_at` is set and
                              NOT NULL (constraint below)
     RATIFIED / REJECTED      the CAB decided retrospectively
     GRANDFATHERED            pre-existing at migration time; never ran under
                              a clock, so no clock is retro-fabricated for it
                              (the DF-3/V322 precedent)

   Breach is DERIVED (`PENDING` and due < now), not a seventh state, so a late
   ratification does not need re-entry logic: the row moves to RATIFIED while
   `ratification_escalated_at` preserves that it breached on the way. "Ratified
   late" and "never breached" must not collapse into the same record.

   ── AMENDMENT 2: THE OVERRIDE IS BOUNDED IN THE DATABASE, NOT JUST THE API ─
   `ck_ratification_sla_bounds` refuses anything outside 1..10 business days.
   Ten is two weekly CAB cycles — an emergency change unratified after two
   consecutive CABs is an unauthorised change, not a slow one. The ceiling is
   in the CHECK and not only in a DTO annotation because the examiner question
   the ADR anticipated ("what stops a tenant choosing 365 days?") must have an
   answer that survives a direct UPDATE, a support script and a future admin
   screen nobody has written yet.
   ============================================================================ */

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ── 1. the per-tenant policy table ──────────────────────────────────────── */
IF OBJECT_ID('change_mgmt.change_ratification_policy', 'U') IS NULL
BEGIN
    CREATE TABLE change_mgmt.change_ratification_policy (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        sla_business_days   INT           NOT NULL CONSTRAINT df_ratify_sla_days DEFAULT 5,
        escalation_role     NVARCHAR(60)  NOT NULL CONSTRAINT df_ratify_esc_role DEFAULT N'CISO',
        notes_en            NVARCHAR(1000)    NULL,
        notes_ar            NVARCHAR(1000)    NULL,
        is_deleted          BIT           NOT NULL CONSTRAINT df_ratify_pol_del DEFAULT 0,
        created_at          DATETIME2     NOT NULL CONSTRAINT df_ratify_pol_cat DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT            NULL,
        updated_at          DATETIME2         NULL,
        updated_by          BIGINT            NULL,
        CONSTRAINT uq_ratify_policy_org UNIQUE (organisation_id),
        /* Amendment 2 — the ceiling lives here, where a direct UPDATE also hits it. */
        CONSTRAINT ck_ratification_sla_bounds CHECK (sla_business_days BETWEEN 1 AND 10)
    );
    PRINT 'V345: created change_mgmt.change_ratification_policy.';
END
ELSE
    PRINT 'V345: change_mgmt.change_ratification_policy already present — skipped.';
GO

/* Seed the ADR default for every live tenant. Idempotent on organisation_id. */
INSERT INTO change_mgmt.change_ratification_policy
       (organisation_id, sla_business_days, escalation_role, notes_en, notes_ar)
SELECT o.id, 5, N'CISO',
       N'ADR-017 D-3 default: emergency changes must be ratified by the CAB within 5 business days of implementation start. Maximum permitted by ck_ratification_sla_bounds is 10 (two weekly CAB cycles).',
       N'الافتراضي حسب ADR-017 D-3: يجب إقرار التغييرات الطارئة من لجنة استشارية للتغيير خلال 5 أيام عمل من بدء التنفيذ. الحد الأقصى المسموح 10 أيام عمل (دورتا لجنة أسبوعية).'
FROM platform.organisations o
WHERE ISNULL(o.is_deleted, 0) = 0
  AND NOT EXISTS (SELECT 1 FROM change_mgmt.change_ratification_policy p
                  WHERE p.organisation_id = o.id);
GO

/* ── 2. the ratification columns on the change record ────────────────────── */
IF COL_LENGTH('change_mgmt.change_requests', 'ratification_state') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD ratification_state NVARCHAR(24) NOT NULL
        CONSTRAINT df_chg_ratify_state DEFAULT N'NOT_REQUIRED';
GO
IF COL_LENGTH('change_mgmt.change_requests', 'ratification_due_at') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD ratification_due_at DATETIME2 NULL;
GO
IF COL_LENGTH('change_mgmt.change_requests', 'ratified_at') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD ratified_at DATETIME2 NULL;
GO
IF COL_LENGTH('change_mgmt.change_requests', 'ratified_by_id') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD ratified_by_id BIGINT NULL;
GO
IF COL_LENGTH('change_mgmt.change_requests', 'ratified_by_name') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD ratified_by_name NVARCHAR(255) NULL;
GO
IF COL_LENGTH('change_mgmt.change_requests', 'ratification_notes') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD ratification_notes NVARCHAR(MAX) NULL;
GO
IF COL_LENGTH('change_mgmt.change_requests', 'ratification_escalated_at') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD ratification_escalated_at DATETIME2 NULL;
GO

/* ── 3. WHO decided. This is not scope creep; it is what makes the control
        enforceable ──────────────────────────────────────────────────────────
   D-3's whole purpose is a second pair of eyes over an emergency change that
   went live without one. So the ratifier must not be the person who
   emergency-approved it. Measured: `change_requests` records `cab_decision_at`
   and `cab_decision_notes` and **nowhere records who decided**. The CAB
   decision's author exists only in the audit ledger, so the SoD test D-3
   needs had nothing to compare against — and separately, an examiner asking
   "who authorised this emergency change?" gets the answer from a log, not from
   the record. Filed as F2-93; fixed here because D-3 cannot be enforced
   without it.

   These are NOT back-filled from the audit ledger. The ledger holds usernames,
   not the identity the SoD test compares, and inferring one from the other
   would manufacture provenance for a decision nobody can now attest to. Legacy
   rows keep NULL, which is honest, and GRANDFATHERED (below) is what stops
   that NULL from being read as "no conflict". */
IF COL_LENGTH('change_mgmt.change_requests', 'cab_decision_by_id') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD cab_decision_by_id BIGINT NULL;
GO
IF COL_LENGTH('change_mgmt.change_requests', 'cab_decision_by_name') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD cab_decision_by_name NVARCHAR(255) NULL;
GO

/* ── 4. the two constraints that make the states total ───────────────────── */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_change_ratification_state')
    ALTER TABLE change_mgmt.change_requests ADD CONSTRAINT ck_change_ratification_state
        CHECK (ratification_state IN (
            N'NOT_REQUIRED', N'AWAITING_IMPLEMENTATION', N'PENDING',
            N'RATIFIED', N'REJECTED', N'GRANDFATHERED'));
GO

/* PENDING cannot exist without a due date. This is the constraint that keeps
   the breach sweep's predicate total — see the NULL-clock note in the header.
   Written as an implication (NOT PENDING *or* due-date present) so every other
   state is free to leave the column NULL. */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_change_ratification_clock')
    ALTER TABLE change_mgmt.change_requests ADD CONSTRAINT ck_change_ratification_clock
        CHECK (ratification_state <> N'PENDING' OR ratification_due_at IS NOT NULL);
GO

/* ── 5. AMENDMENT 3 — grandfather what predates the clock ────────────────── */
DECLARE @emergencyTotal INT, @grandfathered INT;
SET @emergencyTotal = (
    SELECT COUNT(*) FROM change_mgmt.change_requests
    WHERE UPPER(ISNULL(change_type, N'')) = N'EMERGENCY' AND ISNULL(is_deleted, 0) = 0);

/* Only rows that already reached (or passed) emergency approval. A DRAFT
   emergency change has not been authorised yet, so it is not unratified — it
   is unraised, and it will arm normally when it is approved. */
UPDATE change_mgmt.change_requests
   SET ratification_state = N'GRANDFATHERED',
       ratification_notes = CONCAT(
           N'[V345 GRANDFATHERED] Pre-dates the A17-D3 ratification clock. ',
           N'Status at migration: ', ISNULL(status, N'?'),
           N'; implementation_started: ', ISNULL(CONVERT(NVARCHAR(30), implementation_started, 126), N'(never recorded)'),
           N'. No ratification SLA is asserted retrospectively and no escalation will fire for this row. ',
           N'Ratify it deliberately if the governance record needs closing.')
 WHERE UPPER(ISNULL(change_type, N'')) = N'EMERGENCY'
   AND ISNULL(is_deleted, 0) = 0
   AND ratification_state = N'NOT_REQUIRED'          -- idempotency: second pass matches nothing
   AND status NOT IN (N'DRAFT', N'SUBMITTED', N'CANCELLED');

SET @grandfathered = @@ROWCOUNT;
PRINT CONCAT('V345: ', @emergencyTotal, ' EMERGENCY change(s) in this database; ',
             @grandfathered, ' grandfathered this pass.');
GO

/* ── 6. AMENDMENT 1 — the interim escalation destination ─────────────────────
   CE-11's unauthorised-change register does not exist, so breach escalates via
   the F8 automation engine to the CISO plus an audit-ledger event. When CE-11
   lands the register becomes an ADDITIONAL sink, not a replacement.

   `CISO` was verified to be a real row in `iam.roles` in both databases before
   it was written here — global (organisation_id IS NULL), so every tenant has
   a holder. F2-52 measured that 40 of 74 role names recited in guards do not
   exist; an escalation aimed at a ghost role is an escalation into a void, and
   this rule is the entire consequence half of the control.

   ⚠ THE KEY IS `to_role`, SINGULAR, AND THAT IS NOT A STYLE CHOICE (F2-94).
   This rule was first written `"roles":["CISO"]`, copying V305 and every other
   seeded NOTIFY/ESCALATE rule in the platform. The live probe then found the
   engine recording `ESCALATE (no role configured — audit-only)`:
   `AutomationActionExecutor.handleNotify` reads `to_role` (ESCALATE) or `role`
   (NOTIFY) — **singular string keys** — and never looks at `roles`. Its own
   javadoc says the singular naming "match[es] the conventional naming in the
   seed rules"; measured, it matches 12 of 164 of them.

   So `"roles":[…]` means the escalation fires, is audited, and reaches nobody.
   Measured across both databases: **126 of 164 rule codes in dev and 125 of 161
   in prod** are in that state, and `platform.audit_log` already holds **278
   dev / 164 prod** rows saying `no role configured` — the platform has been
   reporting this for months. Filed as **F2-94**, which needs its own slice
   (the better fix is almost certainly in the executor, since the seed rules
   plainly intend multi-role fan-out and one code change beats 150 data rows —
   but switching it on would start delivering ~150 rules' worth of
   never-before-sent notifications, which is an operational decision).

   D-3 does not wait for that. It names one role, so the singular key is a
   faithful fit, and the self-check below asserts the key the executor actually
   reads rather than the key the convention suggests. */
;WITH r (rule_code, name_en, name_ar, description_en, description_ar,
         trigger_event, condition_json, action_json, priority) AS (
    SELECT * FROM (VALUES
        (N'CHG-RATIFY-BREACH',
         N'Emergency change unratified past SLA → escalate CISO',
         N'تغيير طارئ غير مُقَر بعد المدة المحددة → تصعيد لرئيس أمن المعلومات',
         N'ADR-017 D-3: an emergency change implemented without retrospective CAB ratification inside the tenant SLA is an unauthorised change. SAMA CSF requires post-implementation review of emergency changes and NCA ECC requires documented approval, so the breach escalates to the CISO rather than colouring a cell.',
         N'حسب ADR-017 D-3 يُعد التغيير الطارئ المنفَّذ دون إقرار لاحق من لجنة التغيير خلال مدة المؤسسة تغييراً غير مُصرَّح به، ويُصعَّد لرئيس أمن المعلومات.',
         N'CHANGE_RATIFICATION_BREACHED', N'{}',
         N'{"type":"ESCALATE","to_role":"CISO","title":"Unratified emergency change past SLA","title_ar":"تغيير طارئ غير مُقَر بعد المدة المحددة","template":"CHANGE_RATIFICATION_BREACHED"}', 1)
    ) v (rule_code, name_en, name_ar, description_en, description_ar,
         trigger_event, condition_json, action_json, priority)
)
INSERT INTO platform.automation_rules
    (organisation_id, rule_code, name_en, name_ar, description_en, description_ar,
     trigger_event, condition_json, action_json, priority, source)
SELECT o.id, r.rule_code, r.name_en, r.name_ar, r.description_en, r.description_ar,
       r.trigger_event, r.condition_json, r.action_json, r.priority, N'CUSTOM'
FROM r
CROSS JOIN platform.organisations o
WHERE ISNULL(o.is_deleted, 0) = 0
  AND NOT EXISTS (SELECT 1 FROM platform.automation_rules ar
                  WHERE ar.organisation_id = o.id AND ar.rule_code = r.rule_code);
GO

/* Repair, not just insert: the INSERT above is idempotent on (org, rule_code), so a
   database that already received the first cut of this rule (with the unread plural
   `roles` key) would keep it forever. Rewrite the action for THIS rule code only —
   scoped deliberately, because repairing all 126 affected codes is F2-94's slice and
   would silently switch on ~150 rules' worth of notifications nobody has consented to. */
UPDATE platform.automation_rules
   SET action_json = N'{"type":"ESCALATE","to_role":"CISO","title":"Unratified emergency change past SLA","title_ar":"تغيير طارئ غير مُقَر بعد المدة المحددة","template":"CHANGE_RATIFICATION_BREACHED"}',
       updated_at  = SYSUTCDATETIME()
 WHERE rule_code = N'CHG-RATIFY-BREACH'
   AND action_json NOT LIKE N'%"to_role"%';
GO

/* ── 7. self-checks — THROW rather than report a green migration ─────────── */
DECLARE @orgs INT = (SELECT COUNT(*) FROM platform.organisations WHERE ISNULL(is_deleted, 0) = 0);
DECLARE @policies INT = (SELECT COUNT(*) FROM change_mgmt.change_ratification_policy WHERE is_deleted = 0);
DECLARE @rules INT = (SELECT COUNT(*) FROM platform.automation_rules
                      WHERE rule_code = N'CHG-RATIFY-BREACH' AND ISNULL(is_deleted, 0) = 0);

IF @policies < @orgs
    THROW 51345, 'V345: at least one live organisation has no ratification policy row, so its emergency changes would arm with no SLA at all. Fail rather than ship a per-tenant control that some tenants silently lack.', 1;

IF @rules < @orgs
    THROW 51345, 'V345: the CHG-RATIFY-BREACH automation rule is missing for at least one organisation. Breach escalation is the entire consequence half of ADR-017 D-3 — without a rule consumer the sweep publishes into a void and the SLA becomes the report the decision explicitly says it must not be.', 1;

/* F2-94's lesson, as an assertion rather than a comment: the rule must carry the key
   AutomationActionExecutor actually reads, not the key the seed convention suggests.
   The first cut of this file used "roles":[…] and the engine logged
   'ESCALATE (no role configured — audit-only)' — a rule that fires, audits, and
   reaches nobody. A rule row is not evidence of a delivery path. */
IF EXISTS (SELECT 1 FROM platform.automation_rules
           WHERE rule_code = N'CHG-RATIFY-BREACH'
             AND (action_json NOT LIKE N'%"to_role"%' OR action_json NOT LIKE N'%CISO%'))
    THROW 51345, 'V345: CHG-RATIFY-BREACH does not carry a "to_role" naming CISO. AutomationActionExecutor.handleNotify reads to_role (ESCALATE) or role (NOTIFY) — both singular strings — and ignores the plural "roles" array used by the seed rules. With the wrong key the escalation fires, writes an audit row, and notifies nobody: exactly the report-not-a-control outcome ADR-017 D-3 forbids.', 1;

/* The reason ck_change_ratification_clock exists — assert it actually bites. */
IF EXISTS (SELECT 1 FROM change_mgmt.change_requests
           WHERE ratification_state = N'PENDING' AND ratification_due_at IS NULL)
    THROW 51345, 'V345: a change is PENDING ratification with no due date. NULL fails the breach predicate silently, so the row would be reported compliant forever.', 1;

/* Anti-vacuity, in the direction that actually failed elsewhere: the bound has
   to be enforced, not merely declared. If a future edit drops the CHECK, this
   catches it on the next run of the migration. */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_ratification_sla_bounds')
    THROW 51345, 'V345: ck_ratification_sla_bounds is absent, so a tenant can set a 365-day ratification SLA by direct UPDATE. The bound is the answer to the examiner question ADR-017 D-3 amendment 2 anticipated; a DTO annotation alone does not survive a support script.', 1;

DECLARE @grandTotal INT = (SELECT COUNT(*) FROM change_mgmt.change_requests
                           WHERE ratification_state = N'GRANDFATHERED');
DECLARE @sla INT = (SELECT TOP 1 sla_business_days FROM change_mgmt.change_ratification_policy
                    WHERE organisation_id = 1);

PRINT CONCAT('V345 OK — ', @policies, ' ratification policy row(s) for ', @orgs,
             ' organisation(s) (org 1 SLA = ', @sla, ' business days, ceiling 10); ',
             @rules, ' CHG-RATIFY-BREACH rule(s) seeded; ',
             @grandTotal, ' change(s) grandfathered in total; ',
             'PENDING-without-due-date invariant holds.');
GO
