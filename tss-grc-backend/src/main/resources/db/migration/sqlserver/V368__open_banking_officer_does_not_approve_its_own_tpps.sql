-- V368 — F2-145(e) slice 2 prerequisite: the Open Banking officer does not approve the TPPs it operates.
--
-- WHY A MIGRATION HAS TO COME FIRST. F2-145(e) converts `hasAnyRole` guards to
-- `hasPermission(MODULE, ACTION)`. ADR-010's amendment says a conversion must grant the **intended**
-- population, so before touching `ObController` the two populations were measured per action against
-- `iam.role_permissions`. READ and WRITE matched exactly. APPROVE did not:
--
--     guard APPROVE_GUARD  -> PLATFORM_ADMIN, COMPLIANCE_OFFICER, CRO   (CRO is seated nowhere: a ghost)
--     catalogue APPROVE    -> COMPLIANCE_OFFICER, OPEN_BANKING_OFFICER
--                             ^^^^^^^^^^^^^^^^^^^^ named on no APPROVE guard in the module
--
-- Converting without this file would hand `OPEN_BANKING_OFFICER` three authorities the controller has
-- never given it: `POST /tpps` (register a Third-Party Provider), `POST /tpps/{id}/transition` (KYB
-- approval, suspension, revocation) and `POST /incidents/{id}/notify-sama`. That is not a tidy-up, it is
-- a new authorization, and it collapses the module's only separation of duties: the officer who grants
-- customer consents to a TPP and records its API calls would also be the officer who admits that TPP.
-- SAMA's Open Banking Framework treats TPP admission as a control gate, which is presumably why the
-- controller put even `createTpp` on the APPROVE tier rather than WRITE — an unusual choice that only
-- makes sense as "admission is a compliance decision, not the operator's".
--
-- SO WHICH ARTEFACT IS WRONG — THE GUARD OR THE CATALOGUE? The catalogue. V362 states its grants are
-- DERIVED, not authored: the action comes from the endpoint's HTTP verb, "or APPROVE when the method is
-- approve/reject/transition/decide/sign-shaped". That classifier reads the METHOD, and never reads the
-- TIER the guard actually assigns. `ObController` has two transition-shaped methods on its WRITE tier —
-- `transitionIncident` and `revokeConsent` — so the classifier promoted that whole tier's population to
-- APPROVE. `OPEN_BANKING_OFFICER` is on the WRITE guard; that is the entire provenance of the grant.
--
-- ⚠ THIS IS THE SAME DEFECT V367 FIXED, IN A SECOND MODULE, AND IT IS NOT THE LAST. V367 revoked
-- `SHARIA:APPROVE` from `SHARIA_AUDITOR` for what looked like a one-off SoD slip. It was not a slip; it
-- was the classifier's shape. Measured across all six V362 modules on 2026-08-07, comparing each
-- catalogued grant to the guard tier that would consult it:
--
--     SHARIA       SHARIA_AUDITOR got APPROVE            -> caught, revoked by V367
--     OPENBANKING  OPEN_BANKING_OFFICER got APPROVE      -> this file
--     CPTY         CFO got WRITE (guard: read + approve)  -> filed; CPTY has NO APPROVE permission
--                                                            catalogued at all, so its APPROVE tier has
--                                                            nothing to convert to. Needs a decision,
--                                                            not a revoke.
--     AML          KYC_OFFICER + ONBOARDING_AGENT got WRITE. Derived from ONE endpoint they are named on
--                  (`performScreening`, a POST). AML has 6 distinct guard name-sets over 14 sites; a
--                  single AML:WRITE cannot express them, so converting as-is would let a KYC officer
--                  create sanctions lists and review hits. Needs more actions, not a revoke.
--     CONSUMER     clean — one flat name-set on all 9 endpoints, and the catalogue grants exactly it.
--     ETHICS       BOARD_SECRETARY holds APPROVE+WRITE (consistent inside `coi`, absent from the
--                  whistleblower TRIAGE guard); and the TRIAGE tier has no TRIAGE permission catalogued.
--
-- Four of six modules would change population on conversion. Only sharia's instance had been noticed.
-- The lesson worth keeping: a catalogue derived from guards is not the same as a catalogue faithful to
-- them, and "derived" was the word that made it feel already-checked.
--
-- ⚠ ZERO IMPACT, MEASURED. `OPEN_BANKING_OFFICER` is seated (V362) and held by **0 active users** in
-- dev; prod has not applied V362 yet (deployed head V344, V345–V368 pending), so it holds no OPENBANKING
-- grants at all. Nobody loses anything today. After this file `OPENBANKING:APPROVE` is reachable by
-- COMPLIANCE_OFFICER alone — one approver, not zero, so this is separation and not the lockout shape
-- S6b-ii and F2-92 rejected. A tenant wanting a second approver seats CRO (F2-52's queue) or grants
-- OPENBANKING:APPROVE deliberately; either is a provisioning act with an owner, which is the point.
--
-- ⚠ WHAT THIS FILE DOES NOT FIX, DELIBERATELY. Nothing enforces maker != checker on the APPROVE tier:
-- `ObService.transitionTpp` stamps an actor but never compares it to the creator, so COMPLIANCE_OFFICER
-- can register a TPP and approve its own KYB. Revoking a grant cannot fix that — it needs a server-side
-- 409 like the platform's other four-eyes gates. Filed in F2-145, not smuggled in here.
--
-- THE DESCRIPTIONS ARE CORRECTED TOO, because they are the classifier's belief written down and they
-- will mislead the next reader exactly as they misled the grant: WRITE claimed "Register TPPs" (that is
-- APPROVE) and APPROVE claimed "Transition Open Banking incidents and consent decisions" (those are
-- WRITE). A permission description that contradicts its guard is a comment that lies about code.
--
-- REVERSE RECIPE:
--   INSERT INTO iam.role_permissions (role_id, permission_id)
--   SELECT r.id, p.id FROM iam.roles r, iam.permissions p
--    WHERE r.role_name = 'OPEN_BANKING_OFFICER' AND p.module = 'OPENBANKING' AND p.action = 'APPROVE';
--   -- (descriptions: see V362 lines 78-86 for the original text.)

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── The revocation ────────────────────────────────────────────────────────────────────
DELETE rp
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = 'OPEN_BANKING_OFFICER'
   AND p.module = 'OPENBANKING'
   AND p.action = 'APPROVE';
GO

-- ── The descriptions, realigned with the tiers the controller actually assigns ─────────
UPDATE iam.permissions
   SET description_en = N'Admit and de-admit Third-Party Providers (register, KYB approval, suspend, '
                      + N'revoke) and record the SAMA incident notification.'
 WHERE module = 'OPENBANKING' AND action = 'APPROVE';
GO

UPDATE iam.permissions
   SET description_en = N'Maintain TPP records, grant and revoke customer consents, record regulated API '
                      + N'calls, and raise and transition Open Banking incidents.'
 WHERE module = 'OPENBANKING' AND action = 'WRITE';
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
DECLARE @stillApproves INT;
DECLARE @stillMsg NVARCHAR(400) =
    N'V368 failed: OPEN_BANKING_OFFICER still holds OPENBANKING:APPROVE, so converting ObController '
  + N'would grant it TPP admission and SAMA notification authority the guards never gave it.';
SELECT @stillApproves = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = 'OPEN_BANKING_OFFICER' AND p.module = 'OPENBANKING' AND p.action = 'APPROVE';
IF @stillApproves > 0 THROW 50000, @stillMsg, 1;
GO

-- The officer must KEEP read and write: consents, API calls and incidents are its job. Taking those
-- would be a lockout dressed as a control. This also fails loudly if V362 has not run, which is the
-- honest outcome for a file whose prerequisite is absent (V367 proved that the hard way on a database
-- stopped at V320).
DECLARE @canWork INT;
DECLARE @canWorkMsg NVARCHAR(400) =
    N'V368 failed: OPEN_BANKING_OFFICER does not hold OPENBANKING READ and WRITE. Either the revocation '
  + N'over-reached, or V362 has not been applied to this database.';
SELECT @canWork = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = 'OPEN_BANKING_OFFICER' AND p.module = 'OPENBANKING' AND p.action IN ('READ', 'WRITE');
IF @canWork < 2 THROW 50000, @canWorkMsg, 1;
GO

-- OPENBANKING:APPROVE must still be reachable by somebody, or this separates duties by emptying the
-- module. COMPLIANCE_OFFICER is the remaining holder.
DECLARE @approvers INT;
DECLARE @approversMsg NVARCHAR(400) =
    N'V368 failed: no role holds OPENBANKING:APPROVE any more. A module nobody can admit a TPP in is '
  + N'not an improvement over one where the operator admits its own.';
SELECT @approvers = COUNT(DISTINCT rp.role_id)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE p.module = 'OPENBANKING' AND p.action = 'APPROVE';
IF @approvers = 0 THROW 50000, @approversMsg, 1;
GO

-- And the corrected descriptions must actually be in place — an UPDATE that matched no row reports
-- success just as loudly as one that worked.
DECLARE @stale INT;
DECLARE @staleMsg NVARCHAR(400) =
    N'V368 failed: the OPENBANKING APPROVE/WRITE descriptions still describe the classifier''s tiers '
  + N'rather than the controller''s. A permission description that contradicts its guard misleads.';
SELECT @stale = COUNT(*)
  FROM iam.permissions
 WHERE module = 'OPENBANKING'
   AND ((action = 'APPROVE' AND description_en NOT LIKE N'%KYB approval%')
     OR (action = 'WRITE'   AND description_en NOT LIKE N'%grant and revoke customer consents%'));
IF @stale > 0 THROW 50000, @staleMsg, 1;
GO
