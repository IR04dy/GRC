-- V371 — F2-145(e) slice 6 (the last module in the queue): the three ETHICS actions its 26 guards need,
--        and the two grants that would have quietly destroyed a control if the guards had been mapped onto
--        the catalogue as it stands.
--
-- A CATALOGUE THAT HAS NEVER BEEN CONSULTED IS NOT EVIDENCE OF ANYTHING. ETHICS:READ, ETHICS:WRITE and
-- ETHICS:APPROVE have existed for months with grants attached, and `grep -r "hasPermission('ETHICS'"` over
-- the whole of src/main returns **zero**. Their descriptions name COI declarations, whistleblower cases and
-- recusals, so they were written FOR these controllers and never wired to them. That inverts the discipline
-- this programme has used five times: for aml/cpty/sharia/openbanking the catalogue's grants were the thing
-- to preserve. Here they confer nothing, so the GUARD is the only ground truth, and this file makes the
-- catalogue faithful to the guards — which it can do by both adding and revoking without changing one
-- person's real access.
--
-- ⚠⚠ THE SAME TRAP AS V370, ONE MODULE LATER, AND THIS TIME IT HIDES AN INDEPENDENCE CONTROL RATHER THAN A
-- DISCLOSURE ONE. Four distinct role-sets guard the 26 sites. Measured as USERS in dev, three of them are
-- identical:
--
--     COI register    {ETHICS_OFFICER, BOARD_SECRETARY, COMPLIANCE_OFFICER}   -> {admin, compliance}
--     WB case read    {ETHICS_OFFICER, COMPLIANCE_OFFICER, INTERNAL_AUDITOR}  -> {admin, auditor, compliance}
--     WB triage       {ETHICS_OFFICER, COMPLIANCE_OFFICER}                    -> {admin, compliance}
--     retaliation     {COMPLIANCE_OFFICER}                                    -> {admin, compliance}
--
-- ETHICS_OFFICER and BOARD_SECRETARY have 0 active users, so the COI tier, the triage tier and the
-- retaliation tier are indistinguishable by headcount. Mapping all three onto ETHICS:WRITE — which its own
-- description invites, and which no user-population diff could have objected to — would have granted
-- retaliation disposition to ETHICS_OFFICER and BOARD_SECRETARY. **The ethics officer who ran the
-- whistleblower investigation would then be able to validate, uphold or dismiss the retaliation complaint
-- brought about that investigation.** That is the same self-review defect F2-145(b) shipped V367 to remove
-- from `sharia` three slices ago: the auditor may not approve what they audit. Trusting the counts here
-- would have re-introduced it in a different module, in the same programme, with a green measurement.
--
-- And in the opposite direction: ETHICS:READ's holders include BOARD_SECRETARY, whose guard set covers the
-- COI register but NOT the whistleblower hotline. A single ETHICS:READ across both would put a board
-- secretary inside hotline case files that may name — or be about — the board. WhistleblowerCaseController's
-- read list excludes them deliberately; the shape says so and the headcount cannot.
--
-- WHAT THIS FILE DOES
--   1. Catalogues ETHICS:READ_CASES  — the hotline case file and the retaliation register (read).
--                 ETHICS:TRIAGE      — triage / investigate / close a hotline case.
--                 ETHICS:PROTECT     — dispose of a retaliation complaint (validate / uphold / dismiss).
--      (`ModulePermissionEvaluator` concatenates MODULE + '_' + ACTION and string-matches, so the action
--       vocabulary is open. IAM:BREAK_GLASS, INTAKE:TRIAGE and V370's AML:READ_HITS are the precedents.)
--   2. Grants each to exactly the roles its guard names.
--   3. REVOKES ETHICS:READ from INTERNAL_AUDITOR. Internal audit is on the hotline read list and is NOT on
--      the COI-register list, and after this file ETHICS:READ *is* the COI register. The grant confers
--      nothing today (no guard consults it), and internal audit keeps the hotline through READ_CASES, so
--      nobody loses access that exists. An internal auditor who cannot read the COI register may well be a
--      defect — auditing a Nazaha COI process means reading its declarations — but widening is an owner's
--      decision and is filed, not smuggled in under a conversion.
--   4. Corrects the READ and WRITE descriptions, which both claim the hotline that now belongs to
--      READ_CASES and TRIAGE.
--
-- AUDIT_COMMITTEE_MEMBER, named in both whistleblower read guards, has no row in iam.roles and never has.
-- Per F2-52 the honest move is to drop the dead leg rather than launder it into a grant; the intent — the
-- audit committee reading the hotline — needs committee membership to be a seat (F2-26), not a role name.
-- Same disposition as slice 5 reached for the same ghost.
--
-- Idempotent: every statement is existence-guarded, and the self-checks assert the resulting PROPERTY, so a
-- second application is a no-op that still verifies.

-- ── 1. The three actions the guards need ───────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'ETHICS' AND action = N'READ_CASES')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'ETHICS', N'READ_CASES', N'ETHICS_READ_CASES',
            N'Read whistleblower hotline cases, investigation updates and retaliation complaints. Separate '
          + N'from ETHICS:READ because the hotline is confidential to ethics, compliance and internal audit '
          + N'— a board secretary curates the COI register but is not inside case files that may concern '
          + N'the board.');
GO

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'ETHICS' AND action = N'TRIAGE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'ETHICS', N'TRIAGE', N'ETHICS_TRIAGE',
            N'Triage a hotline case, open and update an investigation, close it, and link it to a '
          + N'corrective action.');
GO

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'ETHICS' AND action = N'PROTECT')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'ETHICS', N'PROTECT', N'ETHICS_PROTECT',
            N'Dispose of a retaliation complaint: validate, uphold or dismiss. Held apart from '
          + N'ETHICS:TRIAGE because a retaliation claim is usually ABOUT the handling of the underlying '
          + N'case, so whoever investigated it must not rule on the claim. Grant to an independent '
          + N'protector.');
GO

-- ── 2. Granted to exactly the roles each guard names ───────────────────────────────────
-- The hotline read list, minus the AUDIT_COMMITTEE_MEMBER ghost. Note BOARD_SECRETARY is absent and that
-- absence is the point.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM iam.roles r
 CROSS JOIN iam.permissions p
 WHERE p.module = N'ETHICS' AND p.action = N'READ_CASES'
   AND r.is_deleted = 0
   AND r.role_name IN (N'ETHICS_OFFICER', N'COMPLIANCE_OFFICER', N'INTERNAL_AUDITOR')
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- Triage: the hotline read list minus internal audit, which reads the case file without directing it.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM iam.roles r
 CROSS JOIN iam.permissions p
 WHERE p.module = N'ETHICS' AND p.action = N'TRIAGE'
   AND r.is_deleted = 0
   AND r.role_name IN (N'ETHICS_OFFICER', N'COMPLIANCE_OFFICER')
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- The narrowest guard in the module: one role, and specifically NOT the ethics officer.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM iam.roles r
 CROSS JOIN iam.permissions p
 WHERE p.module = N'ETHICS' AND p.action = N'PROTECT'
   AND r.is_deleted = 0
   AND r.role_name = N'COMPLIANCE_OFFICER'
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- ── 3. The one revocation: ETHICS:READ is the COI register, and internal audit is not on its list ───────
DELETE rp
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
  JOIN iam.roles r ON r.id = rp.role_id
 WHERE p.module = N'ETHICS' AND p.action = N'READ'
   AND r.role_name = N'INTERNAL_AUDITOR';
GO

-- ── 4. Descriptions that claim the hotline their tier does not carry ────────────────────
UPDATE iam.permissions
   SET description_en = N'View the conflict-of-interest register: declaration campaigns, declarations and '
                      + N'recusal events. Whistleblower cases are ETHICS:READ_CASES.'
 WHERE module = N'ETHICS' AND action = N'READ';
GO

UPDATE iam.permissions
   SET description_en = N'Manage the conflict-of-interest register: create and transition declaration '
                      + N'campaigns, and record recusal events. Hotline case updates are ETHICS:TRIAGE.'
 WHERE module = N'ETHICS' AND action = N'WRITE';
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
DECLARE @created INT;
DECLARE @createdMsg NVARCHAR(400) =
    N'V371 failed: ETHICS does not carry all three new actions (READ_CASES, TRIAGE, PROTECT). Without them '
  + N'the 26 ethics guards have nothing faithful to point at, and mapping them onto ETHICS:WRITE lets the '
  + N'ethics officer who ran an investigation rule on the retaliation claim about it.';
SELECT @created = COUNT(*)
  FROM iam.permissions
 WHERE module = N'ETHICS' AND action IN (N'READ_CASES', N'TRIAGE', N'PROTECT');
IF @created <> 3 THROW 50000, @createdMsg, 1;
GO

DECLARE @grants INT;
DECLARE @grantsMsg NVARCHAR(400) =
    N'V371 failed: the new ETHICS actions are not granted to exactly the roles their guards named '
  + N'(READ_CASES 3, TRIAGE 2, PROTECT 1). A conversion that changes the population is not a conversion.';
SELECT @grants = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE p.module = N'ETHICS' AND p.action IN (N'READ_CASES', N'TRIAGE', N'PROTECT');
IF @grants <> 6 THROW 50000, @grantsMsg, 1;
GO

-- Containment: the hotline tiers nest. Nobody directs a case they may not read, and nobody rules on a
-- retaliation claim without being able to direct the case it arises from. Containment ALONE is satisfied by
-- three identical grants, which is exactly the collapse the header argues against, so strictness follows.
DECLARE @leak INT;
DECLARE @leakMsg NVARCHAR(400) =
    N'V371 failed: the hotline tiers do not nest — a role holds ETHICS:TRIAGE without ETHICS:READ_CASES, or '
  + N'ETHICS:PROTECT without ETHICS:TRIAGE. Directing or ruling on a case you cannot read is not a tier, '
  + N'it is a gap.';
SELECT @leak = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE p.module = N'ETHICS' AND p.action IN (N'TRIAGE', N'PROTECT')
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp2
                     JOIN iam.permissions p2 ON p2.id = rp2.permission_id
                    WHERE rp2.role_id = rp.role_id AND p2.module = N'ETHICS'
                      AND p2.action = CASE WHEN p.action = N'TRIAGE' THEN N'READ_CASES' ELSE N'TRIAGE' END);
IF @leak <> 0 THROW 50000, @leakMsg, 1;
GO

DECLARE @strict INT;
DECLARE @strictMsg NVARCHAR(400) =
    N'V371 failed: READ_CASES > TRIAGE > PROTECT is not strict. Equal populations would satisfy the '
  + N'containment check above while expressing no gradient at all — which is the collapse this migration '
  + N'exists to prevent.';
SELECT @strict = CASE WHEN
        (SELECT COUNT(*) FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
          WHERE p.module = N'ETHICS' AND p.action = N'READ_CASES')
      > (SELECT COUNT(*) FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
          WHERE p.module = N'ETHICS' AND p.action = N'TRIAGE')
    AND (SELECT COUNT(*) FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
          WHERE p.module = N'ETHICS' AND p.action = N'TRIAGE')
      > (SELECT COUNT(*) FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
          WHERE p.module = N'ETHICS' AND p.action = N'PROTECT')
    THEN 1 ELSE 0 END;
IF @strict <> 1 THROW 50000, @strictMsg, 1;
GO

-- The two controls the user counts could not see, asserted by name because they are the reason this file
-- exists. Both are absences, and an absence is what a population diff is worst at noticing.
DECLARE @independence INT;
DECLARE @independenceMsg NVARCHAR(400) =
    N'V371 failed: a role that can triage a hotline case also holds ETHICS:PROTECT. Retaliation disposition '
  + N'must stay with an independent protector — the same self-review defect V367 removed from sharia. If '
  + N'this is intended, it is a decision about independence and needs a migration that says so.';
SELECT @independence = COUNT(*)
  FROM iam.roles r
 WHERE EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = r.id AND p.module = N'ETHICS' AND p.action = N'PROTECT')
   AND r.role_name <> N'COMPLIANCE_OFFICER';
IF @independence <> 0 THROW 50000, @independenceMsg, 1;
GO

DECLARE @hotline INT;
DECLARE @hotlineMsg NVARCHAR(400) =
    N'V371 failed: BOARD_SECRETARY holds a hotline action (READ_CASES / TRIAGE / PROTECT). The board '
  + N'secretary curates the COI register; whistleblower case files may name or concern the board, which is '
  + N'why WhistleblowerCaseController''s read list omits that role.';
SELECT @hotline = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
  JOIN iam.roles r ON r.id = rp.role_id
 WHERE p.module = N'ETHICS' AND p.action IN (N'READ_CASES', N'TRIAGE', N'PROTECT')
   AND r.role_name = N'BOARD_SECRETARY';
IF @hotline <> 0 THROW 50000, @hotlineMsg, 1;
GO

-- The COI tier after the one revocation: the three roles CoiController names, no more and no fewer.
DECLARE @coi INT;
DECLARE @coiMsg NVARCHAR(400) =
    N'V371 failed: ETHICS:READ / WRITE / APPROVE are not held by exactly ETHICS_OFFICER, BOARD_SECRETARY '
  + N'and COMPLIANCE_OFFICER (3 each, 9 rows). Those three actions now guard CoiController''s 11 endpoints, '
  + N'so any other holder is access the role guard did not confer.';
SELECT @coi = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
  JOIN iam.roles r ON r.id = rp.role_id
 WHERE p.module = N'ETHICS' AND p.action IN (N'READ', N'WRITE', N'APPROVE')
   AND r.role_name IN (N'ETHICS_OFFICER', N'BOARD_SECRETARY', N'COMPLIANCE_OFFICER');
IF @coi <> 9 THROW 50000, @coiMsg, 1;
GO

DECLARE @extra INT;
DECLARE @extraMsg NVARCHAR(400) =
    N'V371 failed: some role outside {ETHICS_OFFICER, BOARD_SECRETARY, COMPLIANCE_OFFICER} still holds an '
  + N'ETHICS COI action. INTERNAL_AUDITOR''s ETHICS:READ was revoked deliberately (it reads the hotline via '
  + N'READ_CASES, and the COI register''s guard never listed it); anything else here is unexplained.';
SELECT @extra = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
  JOIN iam.roles r ON r.id = rp.role_id
 WHERE p.module = N'ETHICS' AND p.action IN (N'READ', N'WRITE', N'APPROVE')
   AND r.role_name NOT IN (N'ETHICS_OFFICER', N'BOARD_SECRETARY', N'COMPLIANCE_OFFICER');
IF @extra <> 0 THROW 50000, @extraMsg, 1;
GO

DECLARE @stale INT;
DECLARE @staleMsg NVARCHAR(400) =
    N'V371 failed: the ETHICS:READ / ETHICS:WRITE descriptions still advertise whistleblower cases. A '
  + N'description that names a tier it no longer confers is how V368''s wrong grant survived review.';
SELECT @stale = COUNT(*)
  FROM iam.permissions
 WHERE module = N'ETHICS' AND action IN (N'READ', N'WRITE')
   AND (description_en LIKE N'%whistleblower cases and recusals%'
     OR description_en LIKE N'%case updates and recusal records%');
IF @stale <> 0 THROW 50000, @staleMsg, 1;
GO
