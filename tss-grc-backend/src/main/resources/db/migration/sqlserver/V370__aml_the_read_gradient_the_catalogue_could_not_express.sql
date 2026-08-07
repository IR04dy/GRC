-- V370 — F2-145(e) slice 5 prerequisite: the four AML actions its 14 guards need and V362 never created.
--
-- THIS ONE INVERTS THE PREVIOUS THREE. V367/V368 found a grant that was WRONG and revoked it; V369 found an
-- action that was MISSING and created it. Here the catalogue is neither wrong nor absent — it is INCOMPLETE.
-- Three of the six distinct name-sets in `SanctionsScreeningController` map onto V362's AML actions
-- *exactly*, which is worth stating plainly because it is the first time the classifier's output has
-- survived measurement unaltered:
--
--     guard set D {AML_OFFICER, COMPLIANCE_OFFICER}                        = AML_APPROVE holders  (exact)
--     guard set F {AML_OFFICER, COMPLIANCE_OFFICER, KYC_OFFICER,
--                  ONBOARDING_AGENT}                                       = AML_WRITE holders    (exact)
--     guard set A {those four + CISO, INTERNAL_AUDITOR, AUDITOR}           = AML_READ holders     (exact,
--                  once AUDIT_COMMITTEE_MEMBER is set aside — see below)
--
-- WHAT IS MISSING IS A GRADIENT, AND IT IS IN THE READ TIER — WHICH IS NOT WHERE THIS SLICE WAS QUEUED TO
-- LOOK. The plan recorded "the write tier splits three ways". It does, but so does the read tier, and the
-- read split is the one that carries regulatory weight. The three read sets differ *only* by which
-- front-line roles they admit:
--
--     A  lists + checks       : + KYC_OFFICER  + ONBOARDING_AGENT
--     B  a list's entries     : + KYC_OFFICER                        (no ONBOARDING_AGENT)
--     C  hit queue + dashboard:                                      (neither)
--
-- Read outward, that is a deliberate need-to-know gradient: an onboarding agent may see THAT a list exists
-- and screen against it, but not browse the sanctioned names inside it; and neither front-line role may see
-- the HIT QUEUE — the list of customers who matched. Under FATF Recommendation 21 and SAMA's AML/CTF rules,
-- disclosing that a customer has been flagged is the "tipping-off" offence. Collapsing A/B/C into one
-- AML:READ would hand the hit queue to every onboarding agent in the tenant. The gradient is the control.
--
-- ⚠⚠ AND HERE IS THE MEASUREMENT TRAP THAT WOULD HAVE HIDDEN ALL OF THAT. The standing discipline for this
-- programme is "measure the per-action population diff before you convert". Measured in dev today, that diff
-- is EMPTY:
--
--     A \ B  (loses ONBOARDING_AGENT) -> []
--     B \ C  (loses KYC_OFFICER)      -> []
--     A \ C                           -> []
--
-- Every set resolves to the same three users (auditor, compliance, so_officer). Not because the sets are
-- equivalent, but because AML_OFFICER, KYC_OFFICER and ONBOARDING_AGENT — the only roles that DIFFERENTIATE
-- them — have **0 active users** each. V362 seated those names; no tenant has staffed them yet.
--
-- **A zero population diff measured in a tenant that never provisioned the distinguishing roles is not
-- evidence that there is no distinction.** It is evidence that the instrument cannot see one. Had this slice
-- trusted the diff, it would have collapsed 14 guards onto 3 actions, reported "population-preserving,
-- live-verified", been telling the truth about dev, and shipped the tipping-off exposure to the first tenant
-- that ever hires an onboarding agent. The guard's own shape is the surviving evidence of intent when the
-- population is unstaffed, and it has to outrank the count.
--
-- WHAT THIS FILE DOES. Purely additive — no revocations, because no existing grant is wrong:
--   1. Catalogues AML:READ_ENTRIES, AML:READ_HITS, AML:MANAGE_LISTS and AML:REFRESH_LISTS.
--      (`ModulePermissionEvaluator` concatenates MODULE + '_' + ACTION and string-matches the result, so the
--       action vocabulary is open, not a fixed verb list. IAM:BREAK_GLASS and INTAKE:TRIAGE are the existing
--       precedents for a domain-specific action name.)
--   2. Grants each to exactly the roles its guard names, so all six sets keep their populations.
--   3. Corrects the AML_READ and AML_WRITE descriptions, which both describe a wider tier than their guards
--      confer: READ claims "screening hits" (set C, which excludes two of its roles) and WRITE claims
--      "Maintain sanctions lists" (set D, a strictly narrower audience). V368 showed a description agreeing
--      with a wrong grant while contradicting the guard; these agree with a right grant and still overstate
--      the scope.
--
-- ⚠ THE GHOST THIS CONVERSION DELETES, AND WHAT IT DOES *NOT* SETTLE. All seven read guards recite
-- AUDIT_COMMITTEE_MEMBER, which does not exist in iam.roles (16 occurrences platform-wide; these are 7 of
-- them). Converting the guard deletes the name — the third route F2-52 records — so this file grants it
-- nothing. The intent it recorded, "the audit committee can read the sanctions surface", is therefore
-- UNMET rather than satisfied: the platform models committee membership as a seat, not a role, which is
-- F2-26's territory. Per F2-52's rule, the honest move is to drop the dead leg and say so, not to
-- substitute a role that happens to exist.
--
-- ⚠ WHAT THIS DOES NOT FIX. AML:REFRESH_LISTS ends up granted to AML_OFFICER alone, which has 0 active
-- users — so it is reachable only via the platform administrator's in-code bypass. That is *already* true:
-- the guard is hasAnyRole('PLATFORM_ADMIN','AML_OFFICER'). Second instance of the shape V369 recorded, so it
-- is now a pattern rather than an anecdote: the conversion REVEALS an attrition narrowness, it does not
-- create one, and the fix is tenant provisioning under F2-52.
--
-- REVERSE RECIPE:
--   DELETE rp FROM iam.role_permissions rp
--     JOIN iam.permissions p ON p.id = rp.permission_id
--    WHERE p.module = 'AML' AND p.action IN ('READ_ENTRIES','READ_HITS','MANAGE_LISTS','REFRESH_LISTS');
--   DELETE FROM iam.permissions
--    WHERE module = 'AML' AND action IN ('READ_ENTRIES','READ_HITS','MANAGE_LISTS','REFRESH_LISTS');
--   -- (READ/WRITE descriptions: see V362 for the original text.)

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. The four actions the guards need ────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AML' AND action = N'READ_ENTRIES')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'AML', N'READ_ENTRIES', N'AML_READ_ENTRIES',
            N'Browse the named persons and entities recorded inside a sanctions list.');
GO

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AML' AND action = N'READ_HITS')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'AML', N'READ_HITS', N'AML_READ_HITS',
            N'View the sanctions-hit queue and the screening dashboard — i.e. which customers matched. '
          + N'Withheld from front-line onboarding and KYC roles to keep tipping-off exposure narrow.');
GO

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AML' AND action = N'MANAGE_LISTS')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'AML', N'MANAGE_LISTS', N'AML_MANAGE_LISTS',
            N'Create a sanctions list, amend its metadata, and bulk-import its entries.');
GO

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AML' AND action = N'REFRESH_LISTS')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'AML', N'REFRESH_LISTS', N'AML_REFRESH_LISTS',
            N'Pull a sanctions list''s entries afresh from its upstream source.');
GO

-- ── 2. Granted to exactly the roles each guard names ───────────────────────────────────
-- Set B — a list's entries: set A minus ONBOARDING_AGENT.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM iam.roles r
 CROSS JOIN iam.permissions p
 WHERE p.module = N'AML' AND p.action = N'READ_ENTRIES'
   AND r.is_deleted = 0
   AND r.role_name IN (N'AML_OFFICER', N'COMPLIANCE_OFFICER', N'CISO', N'INTERNAL_AUDITOR',
                       N'KYC_OFFICER', N'AUDITOR')
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- Set C — hit queue and dashboard: set A minus ONBOARDING_AGENT and KYC_OFFICER.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM iam.roles r
 CROSS JOIN iam.permissions p
 WHERE p.module = N'AML' AND p.action = N'READ_HITS'
   AND r.is_deleted = 0
   AND r.role_name IN (N'AML_OFFICER', N'COMPLIANCE_OFFICER', N'CISO', N'INTERNAL_AUDITOR', N'AUDITOR')
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- Set D's list-curation half — create/amend/bulk-import.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM iam.roles r
 CROSS JOIN iam.permissions p
 WHERE p.module = N'AML' AND p.action = N'MANAGE_LISTS'
   AND r.is_deleted = 0
   AND r.role_name IN (N'AML_OFFICER', N'COMPLIANCE_OFFICER')
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- Set E — the narrowest guard in the controller: one role, no compliance officer.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM iam.roles r
 CROSS JOIN iam.permissions p
 WHERE p.module = N'AML' AND p.action = N'REFRESH_LISTS'
   AND r.is_deleted = 0
   AND r.role_name = N'AML_OFFICER'
   AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

-- ── 3. Descriptions that claim more than their guards confer ────────────────────────────
UPDATE iam.permissions
   SET description_en = N'View sanctions list metadata and the screening-check record. Entries and hits are '
                      + N'separate actions.'
 WHERE module = N'AML' AND action = N'READ';
GO

UPDATE iam.permissions
   SET description_en = N'Initiate a screening check against the sanctions lists. List maintenance is a '
                      + N'separate action.'
 WHERE module = N'AML' AND action = N'WRITE';
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
DECLARE @created INT;
DECLARE @createdMsg NVARCHAR(400) =
    N'V370 failed: AML does not carry all four new actions (READ_ENTRIES, READ_HITS, MANAGE_LISTS, '
  + N'REFRESH_LISTS). Without them SanctionsScreeningController''s 14 guards have nothing faithful to '
  + N'point at, and collapsing them onto AML:READ hands the sanctions-hit queue to onboarding agents.';
SELECT @created = COUNT(*)
  FROM iam.permissions
 WHERE module = N'AML'
   AND action IN (N'READ_ENTRIES', N'READ_HITS', N'MANAGE_LISTS', N'REFRESH_LISTS');
IF @created <> 4 THROW 50000, @createdMsg, 1;
GO

-- Each new action must have exactly the holders its guard names — 6 / 5 / 2 / 1.
DECLARE @grants INT;
DECLARE @grantsMsg NVARCHAR(400) =
    N'V370 failed: the new AML actions do not have exactly 6/5/2/1 role holders (READ_ENTRIES, READ_HITS, '
  + N'MANAGE_LISTS, REFRESH_LISTS). A grant count that drifts from the guard''s name list is the defect '
  + N'V368 and V369 were written to correct, reintroduced on the additive path.';
SELECT @grants = COUNT(*)
  FROM (SELECT p.action, COUNT(DISTINCT rp.role_id) AS n
          FROM iam.permissions p
          JOIN iam.role_permissions rp ON rp.permission_id = p.id
         WHERE p.module = N'AML'
           AND p.action IN (N'READ_ENTRIES', N'READ_HITS', N'MANAGE_LISTS', N'REFRESH_LISTS')
         GROUP BY p.action) t
 WHERE (t.action = N'READ_ENTRIES'  AND t.n = 6)
    OR (t.action = N'READ_HITS'     AND t.n = 5)
    OR (t.action = N'MANAGE_LISTS'  AND t.n = 2)
    OR (t.action = N'REFRESH_LISTS' AND t.n = 1);
IF @grants <> 4 THROW 50000, @grantsMsg, 1;
GO

-- THE GRADIENT ITSELF, asserted as containment rather than as three counts. READ ⊋ READ_ENTRIES ⊋ READ_HITS:
-- nobody may read entries without reading lists, and nobody may read the hit queue without reading entries.
-- Three separate counts could each be right while the sets overlapped wrongly; this cannot.
DECLARE @leak INT;
DECLARE @leakMsg NVARCHAR(400) =
    N'V370 failed: the AML read gradient is not nested. Some role holds READ_ENTRIES without READ, or '
  + N'READ_HITS without READ_ENTRIES. The three read tiers exist to keep the hit queue narrower than the '
  + N'list surface; unnested, they are three unrelated grants wearing a gradient''s name.';
SELECT @leak =
    (SELECT COUNT(*) FROM iam.role_permissions rpe
       JOIN iam.permissions pe ON pe.id = rpe.permission_id
      WHERE pe.module = N'AML' AND pe.action = N'READ_ENTRIES'
        AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp2
                          JOIN iam.permissions p2 ON p2.id = rp2.permission_id
                         WHERE p2.module = N'AML' AND p2.action = N'READ'
                           AND rp2.role_id = rpe.role_id))
  + (SELECT COUNT(*) FROM iam.role_permissions rph
       JOIN iam.permissions ph ON ph.id = rph.permission_id
      WHERE ph.module = N'AML' AND ph.action = N'READ_HITS'
        AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp3
                          JOIN iam.permissions p3 ON p3.id = rp3.permission_id
                         WHERE p3.module = N'AML' AND p3.action = N'READ_ENTRIES'
                           AND rp3.role_id = rph.role_id));
IF @leak > 0 THROW 50000, @leakMsg, 1;
GO

-- The gradient must also be STRICT. Equal populations would satisfy containment while expressing nothing,
-- which is precisely the state the zero population diff in dev made look acceptable.
DECLARE @strict INT;
DECLARE @strictMsg NVARCHAR(400) =
    N'V370 failed: the three AML read tiers do not have strictly decreasing populations (expected 7 > 6 > '
  + N'5). Containment alone is satisfied by three identical grants, and an unstaffed tenant cannot tell '
  + N'the difference — which is the measurement trap this migration exists to record.';
SELECT @strict = COUNT(*)
  FROM (SELECT p.action, COUNT(DISTINCT rp.role_id) AS n
          FROM iam.permissions p
          JOIN iam.role_permissions rp ON rp.permission_id = p.id
         WHERE p.module = N'AML' AND p.action IN (N'READ', N'READ_ENTRIES', N'READ_HITS')
         GROUP BY p.action) t
 WHERE (t.action = N'READ' AND t.n = 7)
    OR (t.action = N'READ_ENTRIES' AND t.n = 6)
    OR (t.action = N'READ_HITS' AND t.n = 5);
IF @strict <> 3 THROW 50000, @strictMsg, 1;
GO

-- Purely additive means the three pre-existing populations are UNTOUCHED. An additive migration that
-- quietly moved one of them would look identical in the new-action checks above.
DECLARE @preexisting INT;
DECLARE @preexistingMsg NVARCHAR(400) =
    N'V370 failed: an existing AML population changed. Expected AML_READ 7, AML_WRITE 4, AML_APPROVE 2 — '
  + N'the three sets V362 got exactly right. This migration is additive and must alter none of them.';
SELECT @preexisting = COUNT(*)
  FROM (SELECT p.action, COUNT(DISTINCT rp.role_id) AS n
          FROM iam.permissions p
          JOIN iam.role_permissions rp ON rp.permission_id = p.id
         WHERE p.module = N'AML' AND p.action IN (N'READ', N'WRITE', N'APPROVE')
         GROUP BY p.action) t
 WHERE (t.action = N'READ' AND t.n = 7)
    OR (t.action = N'WRITE' AND t.n = 4)
    OR (t.action = N'APPROVE' AND t.n = 2);
IF @preexisting <> 3 THROW 50000, @preexistingMsg, 1;
GO

-- And the corrected descriptions must be in place: an UPDATE that matched no row reports success just as
-- loudly as one that worked.
DECLARE @stale INT;
DECLARE @staleMsg NVARCHAR(400) =
    N'V370 failed: an AML description still claims a wider tier than its guard confers — READ still says '
  + N'"screening hits" (a separate, narrower action) or WRITE still says "Maintain sanctions lists" (the '
  + N'MANAGE_LISTS audience). A description that overstates its scope misleads whoever grants it.';
SELECT @stale = COUNT(*)
  FROM iam.permissions
 WHERE module = N'AML'
   AND (   (action = N'READ'  AND description_en NOT LIKE N'%separate actions%')
        OR (action = N'WRITE' AND description_en NOT LIKE N'%separate action%'));
IF @stale > 0 THROW 50000, @staleMsg, 1;
GO
