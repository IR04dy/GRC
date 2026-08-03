-- V346 — F2-94: normalise the one seeded NOTIFY/ESCALATE rule whose audience is named
-- under a key the F8 executor does not read.
--
-- BACKGROUND. AutomationActionExecutor resolved recipients from `role` / `to_role` only,
-- while 147 of the platform's 164 NOTIFY/ESCALATE rows name them under `roles`. The code
-- fix (this migration's companion commit) closes that by reading a single published set of
-- keys — `roles`, `role`, `to_role` — and AutomationRecipientKeyRatchetTest checks every
-- migration against that same set, so the vocabulary cannot drift open again.
--
-- That leaves exactly one row the code fix cannot reach: AR-148, seeded by V158, which
-- names its audience under `escalateTo` + `cc`. Those two keys appear on this single rule.
-- Teaching the executor to read them would reopen the vocabulary for one row, and worse,
-- invite the same treatment for `also` — which carries role names on AR-09/AR-12
-- (CONTROL_OWNER, OWNER_MANAGER) and *entity type* names on AR-02/AR-05/AR-20 (DRP,
-- NcaCriticalAssessment, PrivacyReassessment). A generous reader would broadcast to a
-- "role" named after an assessment type. So the data is normalised instead.
--
-- WHY NOT EDIT V158. It is already applied in dev and in production. Changing an applied
-- migration changes its Flyway checksum and fails validation on the deployed server
-- (F2-49's neighbourhood). Repairs go forward, never backward.
--
-- SCOPE, STATED HONESTLY. This makes AR-148's audience *readable*. It does not make it
-- *reachable*: LINE_MANAGER, COMMITTEE_CHAIR and COMPANY_SECRETARY are none of them rows in
-- iam.roles — 69 of the 84 role names addressed by automation rules are not (F2-52's
-- ghost-role shape, now measured in a third subsystem). Inventing those roles here would be
-- fabricating an access model, so it is deliberately not done; the companion commit instead
-- makes the gap visible through GET /api/v1/automation/notification-reachability and reports
-- it per fire in the audit row, which now distinguishes "nobody holds this role" from "this
-- is not a role".

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── AR-148: escalateTo + cc → roles, order preserved (escalation target first) ────────
-- Idempotent: only rewrites a row that still lacks a readable key, so a second apply is a
-- no-op. The non-canonical keys are dropped rather than kept alongside — leaving them would
-- preserve the impression that the engine reads them.
UPDATE platform.automation_rules
   SET action_json = N'{"type":"ESCALATE","roles":["LINE_MANAGER","COMMITTEE_CHAIR","COMPANY_SECRETARY"],"template":"ACTION_OVERDUE","channels":["EMAIL","SMS","INBOX"]}',
       updated_at  = SYSUTCDATETIME(),
       updated_by  = N'V346'
 WHERE rule_code = N'AR-148'
   AND action_json LIKE N'%"escalateTo"%'
   AND action_json NOT LIKE N'%"roles"%';
GO

-- ── AUTOMATION:READ — catalogue the permission the new report is guarded on ────────────
-- The catalogue held AUTOMATION WRITE and DELETE but no READ, so
-- hasPermission('AUTOMATION','READ') would have evaluated false for every principal except
-- PLATFORM_ADMIN, which bypasses in code. That is F2-53 exactly: an endpoint that looks
-- guarded and is in practice admin-only, because the permission it names does not exist.
-- Guard and catalogue entry ship together (the F2-23 lesson).
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AUTOMATION' AND action = N'READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'AUTOMATION', N'READ', N'AUTOMATION_READ',
            N'View automation rules, their fires, and notification reachability');
GO

-- Granted to exactly the roles that already administer automation — derived from the
-- existing WRITE/DELETE grants rather than invented. A role holding neither is unchanged:
-- widening assurance access (an auditor verifying that escalations deliver) is a tenant
-- policy call, not something a migration should decide.
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, newp.id
  FROM iam.role_permissions rp
  JOIN iam.permissions oldp ON oldp.id = rp.permission_id
                           AND oldp.module = N'AUTOMATION'
                           AND oldp.action IN (N'WRITE', N'DELETE')
 CROSS JOIN (SELECT id FROM iam.permissions
              WHERE module = N'AUTOMATION' AND action = N'READ') newp
 WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions x
                    WHERE x.role_id = rp.role_id AND x.permission_id = newp.id);
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- A migration that silently did nothing is the failure mode these guard against: the
-- purpose here is a property of the data, not the fact that a statement ran.
DECLARE @unreadable INT;

SELECT @unreadable = COUNT(*)
  FROM platform.automation_rules
 WHERE is_deleted = 0
   AND (action_json LIKE N'%"type":"NOTIFY"%' OR action_json LIKE N'%"type":"ESCALATE"%')
   AND action_json NOT LIKE N'%"roles"%'
   AND action_json NOT LIKE N'%"role"%'
   AND action_json NOT LIKE N'%"to_role"%'
   -- AR-149 addresses the action's assignee, a per-user recipient NOTIFY cannot express.
   -- Tracked as F2-97 and allow-listed in the ratchet rather than given a fabricated role.
   AND rule_code <> N'AR-149';

IF @unreadable > 0
BEGIN
    DECLARE @msg NVARCHAR(400) = FORMATMESSAGE(
        N'V346 failed: %d NOTIFY/ESCALATE rule(s) still name their audience under a key the '
      + N'F8 executor does not read, so they will fire and reach nobody. Normalise them onto '
      + N'"roles" here — do not widen AutomationActionExecutor.RECIPIENT_KEYS.', @unreadable);
    THROW 50000, @msg, 1;
END;
GO

-- AR-148 specifically: prove the rewrite landed rather than trusting @@ROWCOUNT, which is 0
-- on a legitimate second apply and 0 again if the WHERE clause silently stopped matching.
IF EXISTS (SELECT 1 FROM platform.automation_rules
            WHERE rule_code = N'AR-148' AND action_json NOT LIKE N'%"roles"%')
    THROW 50000, N'V346 failed: AR-148 still has no readable audience key.', 1;
GO

-- The permission must exist AND be reachable by a non-PLATFORM_ADMIN role, or the report is
-- admin-only and the guard is decorative.
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AUTOMATION' AND action = N'READ')
    THROW 50000, N'V346 failed: AUTOMATION:READ was not catalogued.', 1;
GO

-- THROW takes a literal or a variable, never a concatenation — building the message inline
-- is a syntax error, and a migration that fails to parse has verified nothing.
DECLARE @grants INT;
DECLARE @grantMsg NVARCHAR(400) =
    N'V346 failed: AUTOMATION:READ is catalogued but granted to no role, so '
  + N'/notification-reachability is PLATFORM_ADMIN-only (F2-53).';

SELECT @grants = COUNT(*)
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE p.module = N'AUTOMATION' AND p.action = N'READ';

IF @grants = 0
    THROW 50000, @grantMsg, 1;
GO
