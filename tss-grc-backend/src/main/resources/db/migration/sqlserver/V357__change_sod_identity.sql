-- V357 — CE-15: the identity CE-15's two remaining SoD gates have to compare, because today they
-- would compare team names to nothing.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE GAP, MEASURED 2026-08-05 IN DEV
-- ════════════════════════════════════════════════════════════════════════════════════════
-- §10 of the uplift plan lists two SoD gates as unbuilt: "PIR approver != change implementer" and
-- "condition satisfied_by != the condition's own owner". Both compare people. Measured, before
-- writing either:
--
--     change_requests.implementer_name ......... nvarchar, NO id column
--       4 distinct values, and 0 of 4 resolve to any user in iam.users:
--       'AppSec on-call', 'Cloud Migrations Squad', 'HR Comms', 'Identity Engineering Team'
--       -- they are TEAMS, not people. The gate's premise is wrong, not merely unenforceable.
--     change_requests.pir_reviewed_by ......... nvarchar, NO id column
--       set from strFromBody(body, "reviewer") -- a CLIENT-SUPPLIED string, in the same request
--       0 of 17 changes carry one at all
--     change_condition.owner_name ............. nvarchar, NO id column (0 rows in the table)
--     change_condition.closed_by_id ........... bigint, WRITTEN FROM THE PRINCIPAL (correct today)
--
-- So a gate over "PIR approver != implementer" compares two strings the same caller types, and a
-- gate over "satisfied_by != owner" compares a real user id to a free-text label. Building either
-- literally ships a control that cannot fire -- F2-95's shape ("33 configured requirements enforced
-- zero times") and F2-62's ("a control that can never come due").
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- ⚠ AND THE SAME MEASUREMENT FOUND A SHIPPED CONTROL WITH THE SAME HOLLOW CORE (F2-112)
-- ════════════════════════════════════════════════════════════════════════════════════════
-- `ChangeManagementService.assertNotSelfWaiver` is the self-review refusal behind the conditions
-- register: the requester or owner of a change may not waive a condition on its approval. It is
-- written correctly -- prefer ids, fall back to case-insensitive names. But in dev:
--
--     change_requests.requested_by_id .... NULL on 17 of 17
--     change_requests.change_owner_id .... NULL on 17 of 17
--     requester labels that resolve to a user ....  0 of 13  ('AppSec', 'CIO Office', 'SRE' ...)
--     owner labels that resolve to a user ........  0 of 15  ('IAM Team', 'CISO', 'CloudOps' ...)
--
-- The id columns exist and NOTHING WRITES THEM -- `create` never captures the authenticated
-- principal. So the gate always takes its name branch, and compares a username ('admin') against a
-- team name ('CloudOps'), which cannot match. The refusal is structurally unable to fire for any
-- change in either database. By contrast `change_approvals.approver_id` is populated on 28 of 31
-- rows, because THAT path stamps the principal -- the platform knows how; the change's own people
-- fields were never captured as identity.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- WHAT THIS MIGRATION DOES, AND WHAT IT DELIBERATELY DOES NOT
-- ════════════════════════════════════════════════════════════════════════════════════════
-- Two nullable columns, so the server can record identity it actually knows:
--
--     change_requests.pir_reviewed_by_id   -- the principal who recorded the review
--     change_condition.owner_id            -- the person accountable for the condition, when named
--
-- ⚠ NO BACKFILL. 17 changes carry NULL ids and free-text team labels, and there is no correct
-- mapping from 'CloudOps' to a user -- inventing one would fabricate the very attribution these
-- gates exist to check. Backfilling is a data change and the owner's call (F2-109 took the same
-- position on `change_code`). The service reports the uncovered population instead of guessing:
-- `GET /api/v1/changes/sod-coverage` publishes, per gate, how many records it can actually check.
--
-- ⚠ NO `requires_independent_closure` FLAG. §10 qualifies the second gate with "where the model
-- demands independence", and no such flag exists anywhere for a change model -- the only
-- independence columns on the platform are admin.committee_templates.independence_required,
-- admin.committee_member_terms.is_independent and four on compliance.findings. Rather than invent
-- one, the gate is unconditional: a condition its own owner can close is not a condition, which is
-- the same sentence assertNotSelfWaiver already uses for the waiver. The qualifier is recorded as
-- refuted in the plan, the way S6's two deferred columns were.
--
-- NOTHING CHANGES ON DEPLOY. Two nullable columns, no default, no data written.
-- Idempotent: safe to run twice (verified by running it twice against dev via pyodbc).

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

-- ── 1. the PIR reviewer's identity ───────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'change_mgmt' AND table_name = 'change_requests'
                 AND column_name = 'pir_reviewed_by_id')
BEGIN
    ALTER TABLE change_mgmt.change_requests ADD pir_reviewed_by_id BIGINT NULL;
    PRINT 'V357: added change_requests.pir_reviewed_by_id';
END
ELSE
    PRINT 'V357: change_requests.pir_reviewed_by_id already present — skipping';
GO

-- ── 2. the condition owner's identity ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'change_mgmt' AND table_name = 'change_condition'
                 AND column_name = 'owner_id')
BEGIN
    ALTER TABLE change_mgmt.change_condition ADD owner_id BIGINT NULL;
    PRINT 'V357: added change_condition.owner_id';
END
ELSE
    PRINT 'V357: change_condition.owner_id already present — skipping';
GO

-- ════════════════════════════════════════════════════════════════════════════════════════
-- SELF-CHECKS — each THROWs, so a half-applied migration cannot pass as applied
-- ════════════════════════════════════════════════════════════════════════════════════════

-- 1. both columns exist, and are nullable BIGINT.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'change_mgmt' AND table_name = 'change_requests'
                 AND column_name = 'pir_reviewed_by_id'
                 AND data_type = 'bigint' AND is_nullable = 'YES')
    THROW 51357, 'V357 self-check 1a: change_requests.pir_reviewed_by_id is missing or not a nullable BIGINT', 1;
GO

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'change_mgmt' AND table_name = 'change_condition'
                 AND column_name = 'owner_id'
                 AND data_type = 'bigint' AND is_nullable = 'YES')
    THROW 51357, 'V357 self-check 1b: change_condition.owner_id is missing or not a nullable BIGINT', 1;
GO

-- 2. NOT NULL would be wrong and must stay wrong: 17 existing changes have no reviewer and no
--    correct id to give them, so a NOT NULL column would make this migration unappliable and a
--    DEFAULT would fabricate attribution. Asserted so a later "tidy-up" cannot quietly add either.
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE table_schema = 'change_mgmt' AND table_name = 'change_requests'
             AND column_name = 'pir_reviewed_by_id' AND column_default IS NOT NULL)
    THROW 51357, 'V357 self-check 2: pir_reviewed_by_id must have no DEFAULT — a default id is invented attribution', 1;
GO

-- 3. NO ROW WAS WRITTEN. The whole point: this migration adds capacity, never attribution.
IF EXISTS (SELECT 1 FROM change_mgmt.change_requests WHERE pir_reviewed_by_id IS NOT NULL)
    THROW 51357, 'V357 self-check 3: a pir_reviewed_by_id was populated — this migration must write no attribution', 1;
GO

IF EXISTS (SELECT 1 FROM change_mgmt.change_condition WHERE owner_id IS NOT NULL)
    THROW 51357, 'V357 self-check 4: a condition owner_id was populated — this migration must write no attribution', 1;
GO

-- 4. the columns the gates read on the OTHER side still exist, or the gates compare nothing.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'change_mgmt' AND table_name = 'change_requests'
                 AND column_name = 'implementer_name')
    THROW 51357, 'V357 self-check 5: change_requests.implementer_name is gone — the PIR gate has nothing to compare', 1;
GO

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'change_mgmt' AND table_name = 'change_condition'
                 AND column_name = 'owner_name')
    THROW 51357, 'V357 self-check 6: change_condition.owner_name is gone — the closure gate has nothing to compare', 1;
GO

-- 5. `ck_cc_closure` must still demand an author on a closed condition. The satisfaction gate is
--    built on the closer being a real principal; if that constraint were relaxed, the gate would be
--    comparing the owner against nobody.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID('change_mgmt.change_condition')
                 AND name = 'ck_cc_closure')
    THROW 51357, 'V357 self-check 7: ck_cc_closure is gone — a condition could be closed by nobody, and the SoD gate would compare against nothing', 1;
GO

PRINT 'V357 OK: SoD identity columns present, no attribution written, both comparison sides intact';
