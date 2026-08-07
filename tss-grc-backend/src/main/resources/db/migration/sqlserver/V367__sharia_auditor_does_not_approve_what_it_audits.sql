-- V367 — F2-145(b): the Sharia auditor may no longer approve what it audits.
--
-- WHAT F2-145 FOUND. V362 seated nine domain roles and catalogued 17 permissions for six Saudi /
-- financial modules, and the grant matrix it produced put `COMPLIANCE_OFFICER`, `SHARIA_OFFICER` and
-- `SHARIA_AUDITOR` all on `SHARIA` READ+WRITE+APPROVE — three roles and one privilege set, which is
-- not a separation of duties. An audit function that can approve the thing it audits has no
-- independence, and Sharia governance is precisely where independence is the control.
--
-- This file does the one part of that which is safe and unambiguous: it revokes `SHARIA:APPROVE` from
-- `SHARIA_AUDITOR`, leaving READ + WRITE so the auditor can still record findings.
--
-- ⚠ IT IS CURRENTLY INERT, AND SAYING SO IS THE POINT. Measured 2026-08-07: **no guard anywhere
-- consults `SHARIA`, `AML` or `CONSUMER` permissions** — `grep hasPermission('SHARIA'` returns zero
-- hits, and `ShariaController` authorises entirely by `hasAnyRole`, which never reaches
-- `ModulePermissionEvaluator`. So this revocation changes no request today. It becomes load-bearing
-- the moment F2-05 converts those guards to `hasPermission`, and ADR-010's amendment says a conversion
-- must grant the **intended** population rather than the current one — so the catalogue has to be right
-- BEFORE the conversion, not after. Shipping it now is what makes the conversion safe by default
-- instead of by memory.
--
-- ⚠ ZERO IMPACT, MEASURED: `SHARIA_AUDITOR` is held by **0 active users** in dev and prod. Nobody
-- loses anything. (Prod holds no `SHARIA` grants at all yet — V362 is among the pending set.)
--
-- ⚠ WHAT THIS FILE DELIBERATELY DOES NOT DO, AND WHY IT CANNOT. F2-145's other independence claim is
-- to revoke `SHARIA:APPROVE` / `AML:APPROVE` / `CONSUMER:APPROVE` from `COMPLIANCE_OFFICER`. ADR-010's
-- Consequences require a per-tenant impact query first; it was run, and the answer is that the
-- revocation is blocked by arithmetic rather than by policy:
--
--     SHARIA:APPROVE    reachable by 1 active user — `compliance`, org 1, via COMPLIANCE_OFFICER only
--     AML:APPROVE       same single user, same single path
--     CONSUMER:APPROVE  same single user, same single path
--
-- `SHARIA_OFFICER`, `AML_OFFICER` and `CONSUMER_PROTECTION_OFFICER` are seated roles that **no active
-- user holds**, so revoking compliance's APPROVE would take each module's approver population from one
-- to **zero** — a control whose first act refuses everything with no action available to the user,
-- which is exactly the shape S6b-ii and F2-92 established as unacceptable. That revocation must be
-- paired with assigning the domain roles to real people, which is a tenant-provisioning act and not a
-- migration. Recorded in F2-145, not smuggled in here.
--
-- ⚠ AND A GENERALISATION OF MINE THAT FAILED, WORTH RECORDING BECAUSE THE FAILURE IS THE RESULT. I
-- asked whether "an audit role that can approve what it writes" is a platform-wide shape deserving a
-- control rather than a one-off deserving this migration. Measured: 74 role x module pairs hold both
-- WRITE and APPROVE (normal — a domain officer usually does both), of which **4** have an audit-shaped
-- name, and **3 of those 4 are `EXECUTIVE_REVIEWER`** — whose entire purpose is to approve. A keyword
-- heuristic ("AUDIT", "REVIEWER") for a semantic property produced **three false positives out of
-- four**. The real population is one: `SHARIA_AUDITOR`. So this is a single-case fix and no control is
-- warranted — the same lesson as reciting role names: a name is not a meaning.
--
-- ⚠ THIS FILE DEPENDS ON V362, AND ITS SELF-CHECK FAILS LOUDLY IF V362 HAS NOT RUN. V362 is what
-- seats `SHARIA_AUDITOR` and catalogues the `SHARIA` permissions, so on any database that has applied
-- migrations in order — a fresh install running V1→V367, or the deployed server applying V345→V367 —
-- the role holds READ+WRITE+APPROVE by the time this file runs and ends with READ+WRITE. Flyway
-- guarantees that order.
--
-- It was proven the hard way: applying this file to `TSS_GRC_FULL_CHAIN`, an artefact that stopped at
-- V320 and therefore never ran V362, fails self-check 2 — `SHARIA_AUDITOR` holds nothing there, so the
-- "the auditor must keep READ and WRITE" assertion fires. That is the check working. A migration whose
-- prerequisite is absent should stop and say which invariant broke, not quietly delete nothing and
-- report success. The wrong thing in that experiment was the target database, not the check.
--
-- REVERSE RECIPE:
--   INSERT INTO iam.role_permissions (role_id, permission_id)
--   SELECT r.id, p.id FROM iam.roles r, iam.permissions p
--    WHERE r.role_name = 'SHARIA_AUDITOR' AND p.module = 'SHARIA' AND p.action = 'APPROVE';

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── The revocation ────────────────────────────────────────────────────────────────────
DELETE rp
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = 'SHARIA_AUDITOR'
   AND p.module = 'SHARIA'
   AND p.action = 'APPROVE';
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
DECLARE @stillApproves INT;
DECLARE @stillMsg NVARCHAR(400) =
    N'V367 failed: SHARIA_AUDITOR still holds SHARIA:APPROVE, so the audit function can still approve '
  + N'what it audits.';
SELECT @stillApproves = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = 'SHARIA_AUDITOR' AND p.module = 'SHARIA' AND p.action = 'APPROVE';
IF @stillApproves > 0 THROW 50000, @stillMsg, 1;
GO

-- The auditor must KEEP read and write. Revoking APPROVE is independence; revoking the ability to
-- record a finding would be a lockout dressed as a control — F2-52's attrition shape, self-inflicted.
DECLARE @canWork INT;
DECLARE @canWorkMsg NVARCHAR(400) =
    N'V367 failed: SHARIA_AUDITOR no longer holds SHARIA READ and WRITE. An auditor that cannot record '
  + N'a finding is locked out, not independent.';
SELECT @canWork = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.roles r       ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE r.role_name = 'SHARIA_AUDITOR' AND p.module = 'SHARIA' AND p.action IN ('READ', 'WRITE');
IF @canWork < 2 THROW 50000, @canWorkMsg, 1;
GO

-- And SHARIA:APPROVE must still be reachable by somebody, or this migration has emptied the module
-- rather than separating it. The single current holder is COMPLIANCE_OFFICER — whose own revocation is
-- the blocked half recorded in the header, and this check is why it stays blocked.
DECLARE @approvers INT;
DECLARE @approversMsg NVARCHAR(400) =
    N'V367 failed: no role holds SHARIA:APPROVE any more. Separating duties must leave somebody able '
  + N'to discharge them — a module nobody can approve in is not an improvement.';
SELECT @approvers = COUNT(DISTINCT rp.role_id)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE p.module = 'SHARIA' AND p.action = 'APPROVE';
IF @approvers = 0 THROW 50000, @approversMsg, 1;
GO
