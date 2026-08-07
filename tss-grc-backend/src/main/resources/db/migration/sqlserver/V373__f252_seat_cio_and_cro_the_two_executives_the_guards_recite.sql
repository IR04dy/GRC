/*
 * V373 — F2-52: seat CIO and CRO, the two executive identities 79 guard sites already recite.
 *
 * THE DEFECT. hasAnyRole('X') tests for the granted authority ROLE_X. CIO and CRO match no row in
 * iam.roles (67 active, measured 2026-08-07 in dev), so every leg naming them is permanently false and
 * the guard is strictly tighter than it reads. Measured population: CIO on 41 @PreAuthorize sites and
 * CRO on 38, across five controllers — ai/AiLifecycleController, dataclass/DataclassController,
 * outsourcing/OutsourcingController, regreporting/RegReportingController, rrp/RrpController — plus four
 * role lists in attachment/AttachmentAccessPolicy (CPTY_READ, AI_LC_READ, DATACLASS_READ, RRP_CYCLE).
 *
 * ⚠ THE SHARP CASE. RrpController#submit is hasAnyRole('PLATFORM_ADMIN','CRO','CFO'). CRO does not
 * exist, so the only satisfiable non-admin leg is CFO — which has 0 users. Submitting a recovery and
 * resolution plan, a SAMA-regulated filing, is therefore reachable by the platform administrator and
 * nobody else, and no tenant administrator can change that: you cannot assign a role that is not in the
 * catalogue. AiLifecycleController#transition (PLATFORM_ADMIN|CRO|CIO|DPO) is the same shape.
 *
 * WHY SEATING IS THE MECHANISM HERE. Three remedies exist for a ghost role name and this is the only one
 * available for these two. (1) Convert the guard to hasPermission — F2-145(e)'s route, which deletes
 * every name a guard recites. Unavailable: DATACLASS, OUTSOURCING, REGREPORTING and RRP have NO
 * catalogued permissions at all (measured against iam.permissions), so converting means first
 * cataloguing four new modules, which is a V362-scale decision about who may do what and not a name fix.
 * (2) Bind to a column, per ADR-017 D-14 — there is no cio_user_id or cro_user_id anywhere in the
 * schema; these are job families, not per-record owners. (3) Seat the role: hasAnyRole compares NAMES,
 * so the moment CIO exists and a user holds it the 79 sites work exactly as written, with no annotation
 * edit and no redeploy. V362 chose (3) for nine domain roles on this reasoning and explicitly routed
 * "CRO, CIO, AUDIT_COMMITTEE_MEMBER, BOARD_MEMBER ... F2-52's own queue" here.
 *
 * WHY THESE TWO AND NOT THE OTHER SIX. CFO ("Chief Financial Officer") and CISO are already seated
 * global system roles, and CIO/CRO sit LITERALLY BESIDE THEM in the same expressions — CFO|CRO in
 * RegReportingController#submitFiling and RrpController#submit, CISO|CIO in DataclassController. An
 * executive pair where one half was catalogued and the other was not is the strongest available evidence
 * of intent. The remaining six ghosts are NOT job families and are deliberately left:
 *   - MODEL_OWNER (14 sites) and FACILITATOR (18) are ENTITY-BOUND. ai_governance.ml_model
 *     .model_owner_user_id is populated 25/25 and bcm.exercise_run.facilitator_user_id 4/4 — the
 *     platform already knows who owns each model and who facilitates each run. Seating them would be
 *     LOOSER than the column: the facilitator of exercise A could drive exercise B. Blocked on the
 *     ownership/SpEL mechanism F2-145(c) also needs, not on a decision.
 *   - AUDIT_COMMITTEE_CHAIR (6), AUDIT_COMMITTEE_MEMBER (3), AUDIT_DIRECTOR (3) are committee SEATS.
 *     F2-26 shipped the roster->user linkage, so the data now exists; what does not exist is a SpEL
 *     function asking "is the caller a seated member of this committee". Those guards are asking the
 *     wrong question, not misspelling the answer.
 *   - PRIVACY_OFFICER (5) is a misspelling and is corrected in the same commit as this file, in source:
 *     SubprocessorController.READ_GUARD and AttachmentAccessPolicy's SUBPROCESSOR entry now name the
 *     real PRIVACY_MANAGER. Check 4 below proves that role exists, because a guard repointed at a role
 *     a fresh install does not create is V372's defect all over again.
 *
 * NO PERMISSIONS ARE GRANTED, AND THAT IS DELIBERATE. hasAnyRole never consults iam.role_permissions, so
 * all 79 sites become satisfiable with zero grants; and of the five modules involved only AI is
 * catalogued at all, where AiLifecycleController is role-guarded anyway, so any grant would be inert.
 * Granting more would be authoring an access policy from a role's job title — V372's measured lesson.
 * ⚠ The consequence is real and is filed rather than fixed: a user holding only CIO or CRO passes these
 * 79 guards and holds no module permission, so the permission-driven parts of the SPA show them nothing.
 * Which permissions these two executives should hold is a provisioning decision for the platform owner.
 *
 * Idempotent: guarded on role_name, re-runnable. Verified at the end by re-reading iam.roles and
 * THROWing, because a guarded insert that silently did nothing looks identical to one that worked.
 * ⚠ Flyway runs this once, so these checks pin the state a fresh install STARTS in and nothing after.
 */

SET NOCOUNT ON;
GO

-- ── 1. Seat the two executives (global system roles; bilingual display names are mandatory) ────
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'CIO')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'CIO', N'Chief Information Officer', N'رئيس تقنية المعلومات', N'Accountable executive for technology: AI/ML lifecycle gates, data classification and cloud/outsourcing arrangements.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'CRO')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'CRO', N'Chief Risk Officer', N'رئيس إدارة المخاطر', N'Accountable executive for risk: recovery and resolution planning, regulatory filings and outsourcing risk decisions.', 1, 1, 0);
GO

-- ── 2. Self-checks ─────────────────────────────────────────────────────────────────────────────
DECLARE @cio INT = (SELECT COUNT(*) FROM iam.roles
                     WHERE role_name = N'CIO' AND is_deleted = 0 AND is_active = 1
                       AND is_system_role = 1 AND organisation_id IS NULL);
IF @cio <> 1
    THROW 51373, 'V373 check 1: CIO is not seated as an active global system role. 41 guard sites recite it and every one of them is a permanently false leg until it exists.', 1;

DECLARE @cro INT = (SELECT COUNT(*) FROM iam.roles
                     WHERE role_name = N'CRO' AND is_deleted = 0 AND is_active = 1
                       AND is_system_role = 1 AND organisation_id IS NULL);
IF @cro <> 1
    THROW 51373, 'V373 check 2: CRO is not seated as an active global system role. RrpController#submit is PLATFORM_ADMIN|CRO|CFO, so without it a SAMA recovery-plan filing is administrator-only.', 1;

-- Check 3 — ANTI-VACUITY. The case for seating these two is that their halves are already catalogued
-- and sit beside them in the same expressions. If CFO or CISO ever leaves iam.roles, that argument is
-- gone and so is the guard's meaning; a check that only re-read its own two rows would not notice.
DECLARE @pair INT = (SELECT COUNT(*) FROM iam.roles
                      WHERE role_name IN (N'CFO', N'CISO', N'DPO') AND is_deleted = 0);
IF @pair <> 3
    THROW 51373, 'V373 check 3: one of CFO / CISO / DPO is missing. Those are the seated executives CIO and CRO are guarded alongside; seating a half-pair was the defect this file fixes, so losing the other half re-creates it in the opposite direction.', 1;

-- Check 4 — the cross-artefact one. This commit repoints SubprocessorController.READ_GUARD and
-- AttachmentAccessPolicy's SUBPROCESSOR entry from the non-existent PRIVACY_OFFICER to PRIVACY_MANAGER.
-- A guard naming a role the migration chain does not create is exactly F2-154's defect, so the artefact
-- that fixes the guard is the right place to prove the target exists.
DECLARE @pm INT = (SELECT COUNT(*) FROM iam.roles WHERE role_name = N'PRIVACY_MANAGER' AND is_deleted = 0);
IF @pm <> 1
    THROW 51373, 'V373 check 4: PRIVACY_MANAGER is absent. SubprocessorController.READ_GUARD now names it in place of the non-existent PRIVACY_OFFICER; if this role is missing the correction restores nothing and the register stays 7 roles wide.', 1;

-- Check 5 — proves the correction in check 4 was NEEDED, in the artefact rather than in a commit
-- message. If PRIVACY_OFFICER is ever legitimately introduced as a role, delete this check in the same
-- migration that introduces it — do not seat it while the guards name PRIVACY_MANAGER, or the register
-- silently answers to two names for one job.
IF EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'PRIVACY_OFFICER' AND is_deleted = 0)
    THROW 51373, 'V373 check 5: PRIVACY_OFFICER exists as a role. F2-52 corrected the sub-processor guards to PRIVACY_MANAGER on the measured basis that PRIVACY_OFFICER matches no row; re-check which name the guards should use before allowing both.', 1;

PRINT 'V373: CIO and CRO seated; 79 guard sites and 4 attachment role lists are now satisfiable.';
GO
