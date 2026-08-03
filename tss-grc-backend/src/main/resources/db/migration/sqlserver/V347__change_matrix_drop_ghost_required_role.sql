-- V347 — F2-95 / S6b-ii: remove the ghost role `CTO` from the change authorisation matrix, so the
-- required-roles gate can be enforced without refusing what nobody could ever satisfy.
--
-- THE DECISION (owner-delegated, taken 2026-08-03): strip `CTO`, do not create it.
--
--   `required_roles` names `CTO` on 6 of 36 matrix rows, identically in dev and production —
--   so this is OUR seed asserting a requirement, not a customer's configuration. `CTO` is not a
--   row in `iam.roles` in either database: F2-52's ghost-role shape, fourth instance.
--
--   Creating the role to satisfy the seed would fabricate an access model — a role with no persona,
--   no grants and no holder, which is the dead control F2-62 was about. Removing it from the rows
--   corrects the assertion instead. The requirement that survives on those rows
--   (`CHANGE_MANAGER,CISO`) is the one the platform can express.
--
-- WHY THIS MATTERS FOR THE GATE. S6b-ii refuses a CAB approval when a required role is MISSING —
-- checkable in this tenant and not held by any approver. It deliberately does NOT refuse for a role
-- nothing could satisfy (`NOT_A_ROLE`, or a real role with no active holder), because a control
-- whose first act refuses everything with no action available to the approver is F2-92's shape. With
-- `CTO` gone, `NOT_A_ROLE` no longer occurs in seeded configuration at all, so the enforceable and
-- the unenforceable halves stop overlapping.
--
-- NOT DONE HERE, DELIBERATELY: `CHANGE_MANAGER` stays on all 33 rows even though no active user in
-- either database holds it. It is a real, catalogued role — the tenant's remedy is to grant it, and
-- deleting the requirement would quietly lower a control the tenant configured. It surfaces as
-- NO_ACTIVE_HOLDER in `GET /api/v1/changes/{id}/authorisation` and never blocks.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── Strip CTO wherever it appears in the CSV, in any position ─────────────────────────
-- Three positions to handle so the result never contains a doubled or trailing comma:
-- ",CTO" (last/middle), "CTO," (first), and "CTO" alone (only value).
UPDATE change_mgmt.change_authorisation_matrix
   SET required_roles =
       LTRIM(RTRIM(
         REPLACE(REPLACE(REPLACE(',' + REPLACE(required_roles, ' ', '') + ',',
                 ',CTO,', ','),          -- remove the token with both delimiters
                 ',,', ','),             -- collapse any delimiter pair left behind
                 ' ', '')
       ))
       -- NB: this table has no updated_at/updated_by (columns: id, organisation_id, change_type,
       -- risk_level, gate, min_approvals, required_roles, notes_en, notes_ar, is_deleted,
       -- created_at). Do not add an audit stamp here — the change is recorded by this migration's
       -- version, and inventing columns to hold provenance is a schema change in disguise.
 WHERE ISNULL(is_deleted, 0) = 0
   AND ',' + REPLACE(ISNULL(required_roles, ''), ' ', '') + ',' LIKE '%,CTO,%';
GO

-- Tidy the leading/trailing delimiters the wrapper above introduces.
UPDATE change_mgmt.change_authorisation_matrix
   SET required_roles = LTRIM(RTRIM(
         CASE WHEN LEFT(required_roles, 1) = ',' THEN STUFF(required_roles, 1, 1, '') ELSE required_roles END))
 WHERE ISNULL(is_deleted, 0) = 0 AND LEFT(ISNULL(required_roles, ''), 1) = ',';
GO

UPDATE change_mgmt.change_authorisation_matrix
   SET required_roles = LTRIM(RTRIM(LEFT(required_roles, LEN(required_roles) - 1)))
 WHERE ISNULL(is_deleted, 0) = 0
   AND LEN(ISNULL(required_roles, '')) > 0
   AND RIGHT(required_roles, 1) = ',';
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The purpose is a property of the data, not that a statement ran. A migration that silently
-- did nothing is the failure mode these exist to catch.
DECLARE @ghostRows INT;
DECLARE @ghostMsg NVARCHAR(400);

IF EXISTS (SELECT 1 FROM change_mgmt.change_authorisation_matrix
            WHERE ISNULL(is_deleted, 0) = 0
              AND ',' + REPLACE(ISNULL(required_roles, ''), ' ', '') + ',' LIKE '%,CTO,%')
    THROW 50000, N'V347 failed: CTO is still named in change_authorisation_matrix.required_roles.', 1;
GO

-- Stronger, and the one worth keeping: NO required role may name something absent from the role
-- catalogue. This is the invariant that makes the gate enforceable — every remaining requirement is
-- at least *expressible*, whether or not anyone currently holds it.
DECLARE @unknown INT;
DECLARE @unknownMsg NVARCHAR(500) =
    N'V347 failed: change_authorisation_matrix.required_roles names one or more roles that do not '
  + N'exist in iam.roles. Either catalogue the role or remove it from the matrix row — do NOT '
  + N'invent a role to satisfy a seed (F2-52/F2-95).';

;WITH tokens AS (
    SELECT m.id, m.organisation_id, LTRIM(RTRIM(v.value)) AS role_name
      FROM change_mgmt.change_authorisation_matrix m
     CROSS APPLY STRING_SPLIT(ISNULL(m.required_roles, ''), ',') v
     WHERE ISNULL(m.is_deleted, 0) = 0 AND LTRIM(RTRIM(v.value)) <> ''
)
SELECT @unknown = COUNT(*)
  FROM tokens t
 WHERE NOT EXISTS (
        SELECT 1 FROM iam.roles r
         WHERE r.is_deleted = 0
           AND UPPER(r.role_name) COLLATE DATABASE_DEFAULT
             = UPPER(t.role_name) COLLATE DATABASE_DEFAULT
           AND (r.organisation_id = t.organisation_id OR r.organisation_id IS NULL));

IF @unknown > 0
    THROW 50000, @unknownMsg, 1;
GO

-- And the rows must not have been emptied by the string surgery: the requirement that remains is
-- the point of the exercise.
-- THROW takes a literal or a variable, never a concatenation — the third time this trap has been
-- hit in one session (V346 twice). A migration that fails to parse has verified nothing.
DECLARE @emptiedMsg NVARCHAR(400) =
    N'V347 failed: a NORMAL HIGH/CRITICAL row lost its required_roles entirely — the CSV surgery '
  + N'removed more than CTO.';

IF EXISTS (SELECT 1 FROM change_mgmt.change_authorisation_matrix
            WHERE ISNULL(is_deleted, 0) = 0
              AND LTRIM(RTRIM(ISNULL(required_roles, ''))) = ''
              AND change_type = N'NORMAL' AND risk_level IN (N'HIGH', N'CRITICAL'))
    THROW 50000, @emptiedMsg, 1;
GO
