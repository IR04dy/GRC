-- V353 — S8a / CE-9: a change may carry evidence. `CK_ev_links_module` 9 → 10 (+ CHANGE).
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE GAP, MEASURED 2026-08-04 IN BOTH DATABASES
-- ════════════════════════════════════════════════════════════════════════════════════════
-- G-9 is the only **Critical** left in S8: "No evidence/attachment integration at all — test
-- results, approvals, back-out proof are free text; ISO 20000/9001 documented-information not
-- satisfiable."
--
-- The platform's evidence subsystem is not missing — it is thriving, and change is the module that
-- never joined it:
--
--     evidence.evidence_items ......... 78 LIVE rows, identical in both databases —
--                                       29 APPROVED, 29 EXPIRED, 15 PENDING_REVIEW, 4 DRAFT,
--                                       1 REJECTED. So fewer than 2 in 5 are APPROVED.
--     evidence.evidence_versions ...... 60      evidence.evidence_reviews ..... 19
--     evidence.evidence_links ......... 21 rows across EIGHT modules —
--                                       RISK, SUSTAINABILITY, PRIVACY, COMPLIANCE, AUDIT,
--                                       CYBER, INCIDENT, BCM
--     CK_ev_links_module .............. 9 permitted values, and **CHANGE is not one of them**
--
-- ⚠ CORRECTED BEFORE COMMIT. An earlier draft of this header said "104 rows (34 APPROVED, 29
-- PENDING_REVIEW…)" — that count included the 25 **soft-deleted** rows, because it was taken with a
-- bare COUNT(*) instead of `WHERE is_deleted = 0`. The platform's own top landmine list warns that
-- native SQL must filter `is_deleted`, and a census is native SQL. The corrected figures make the
-- design argument below *stronger*, not weaker: 49 of 78 live items are NOT approved.
--
-- So `rollback_plan`, `test_plan` and the CAB's own approval rationale are NVARCHAR(MAX) prose on
-- `change_requests`, while every other governance domain attaches hashed, versioned, four-eyes
-- reviewed artefacts. An auditor asking "show me the back-out proof for CHG-2025-0002" gets a
-- paragraph somebody typed.
--
-- ⚠ WHY THIS NEEDS A MIGRATION AND S7d DID NOT. The same question was asked one slice earlier and
-- answered the other way: `capa.tasks.origin_type` carries **no CHECK**, so pointing the PIR
-- follow-up at the Base CAPA tracker cost nothing. `evidence.evidence_links.linked_module` **is** a
-- closed CHECK (9 values), so joining the subsystem genuinely requires widening it. Measured, not
-- assumed — and the difference is the reason "does this need a migration?" is a question to ask per
-- store rather than per slice.
--
-- ⚠ NOT A NEW LINK TABLE. `change_mgmt.change_links` already exists and already carries an
-- `EVIDENCE` entity_type option in the frontend's vocabulary — and using it would have been the
-- mistake: it is an *un-hashed, un-reviewed, free-form* pointer with no versioning, no expiry and no
-- four-eyes review, which is precisely the deficiency G-9 describes. Evidence has to live in the
-- evidence subsystem or the word means nothing.
--
-- NOTHING CHANGES ON DEPLOY. Widening a CHECK cannot move a row: measured **0** links with
-- `linked_module = 'CHANGE'` in either database (the value was impossible until now), and no row is
-- rewritten here.
--
-- REVERSE RECIPE: ALTER TABLE evidence.evidence_links DROP CONSTRAINT CK_ev_links_module;
--                 ALTER TABLE evidence.evidence_links WITH CHECK ADD CONSTRAINT CK_ev_links_module
--                     CHECK (linked_module IN ('RISK','COMPLIANCE','AUDIT','CYBER','BCM','PRIVACY',
--                                              'TPRM','SUSTAINABILITY','INCIDENT'));
--                 (only safe once every CHANGE-linked row is gone)
-- ════════════════════════════════════════════════════════════════════════════════════════

SET NOCOUNT ON;

IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'CK_ev_links_module'
             AND parent_object_id = OBJECT_ID('evidence.evidence_links'))
    ALTER TABLE evidence.evidence_links DROP CONSTRAINT CK_ev_links_module;
GO

ALTER TABLE evidence.evidence_links WITH CHECK ADD CONSTRAINT CK_ev_links_module
    CHECK (linked_module IN (N'RISK', N'COMPLIANCE', N'AUDIT', N'CYBER', N'BCM', N'PRIVACY',
                             N'TPRM', N'SUSTAINABILITY', N'INCIDENT',
                             -- S8a / CE-9: change enablement joins the subsystem.
                             N'CHANGE'));
GO

-- ════════════════════════════════════════════════════════════════════════════════════════
-- SELF-CHECKS — every one THROWs. Applied twice to dev via pyodbc (Flyway is off in dev), so
-- re-running this file must be a no-op.
-- ════════════════════════════════════════════════════════════════════════════════════════

-- 1. The constraint exists and permits CHANGE.
DECLARE @def NVARCHAR(MAX) = (SELECT definition FROM sys.check_constraints
                              WHERE name = 'CK_ev_links_module'
                                AND parent_object_id = OBJECT_ID('evidence.evidence_links'));
IF @def IS NULL
    THROW 53001, 'V353: CK_ev_links_module is missing — the link table lost its vocabulary guard.', 1;
IF @def NOT LIKE '%CHANGE%'
    THROW 53002, 'V353: CK_ev_links_module still refuses CHANGE, so a change cannot carry evidence.', 1;
GO

-- 2. And it did not lose any of the nine that were already there. Widening a vocabulary by
--    drop-and-recreate is how a value silently disappears (the V349 lesson: the recreate must list
--    every prior value, and only a check like this proves it did).
DECLARE @def2 NVARCHAR(MAX) = (SELECT definition FROM sys.check_constraints
                               WHERE name = 'CK_ev_links_module'
                                 AND parent_object_id = OBJECT_ID('evidence.evidence_links'));
DECLARE @missing NVARCHAR(400) = N'';
SELECT @missing = @missing + m.n + N' '
FROM (VALUES ('RISK'), ('COMPLIANCE'), ('AUDIT'), ('CYBER'), ('BCM'), ('PRIVACY'), ('TPRM'),
             ('SUSTAINABILITY'), ('INCIDENT')) AS m(n)
WHERE @def2 NOT LIKE '%' + m.n + '%';
IF LEN(@missing) > 0
    THROW 53003, 'V353: the recreated CK_ev_links_module dropped a module that was permitted before.', 1;
GO

-- 3. Every existing link still satisfies the constraint. WITH CHECK above would have failed the
--    ALTER, but assert it explicitly: a NOCHECK slip is invisible until the next insert.
IF EXISTS (SELECT 1 FROM evidence.evidence_links
           WHERE linked_module NOT IN (N'RISK', N'COMPLIANCE', N'AUDIT', N'CYBER', N'BCM',
                                       N'PRIVACY', N'TPRM', N'SUSTAINABILITY', N'INCIDENT',
                                       N'CHANGE'))
    THROW 53004, 'V353: an existing evidence link carries a module the constraint forbids.', 1;
GO

-- 4. The constraint is trusted (not NOCHECK), so SQL Server will actually enforce it.
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'CK_ev_links_module'
             AND parent_object_id = OBJECT_ID('evidence.evidence_links')
             AND (is_not_trusted = 1 OR is_disabled = 1))
    THROW 53005, 'V353: CK_ev_links_module is untrusted or disabled — it would not be enforced.', 1;
GO

-- 5. Nothing was backfilled. Evidence is attached by a person performing a governance act; a
--    migration that invented links would put unreviewed prose behind the word "evidence", which is
--    the deficiency G-9 describes rather than its remedy.
DECLARE @msg6 NVARCHAR(700) = N'V353: rows already carry linked_module = CHANGE. This migration '
    + N'must not backfill: evidence is attached by a person, and inventing links would put '
    + N'unreviewed prose behind the word "evidence".';
IF EXISTS (SELECT 1 FROM evidence.evidence_links WHERE linked_module = N'CHANGE'
             AND created_at < '2026-08-04')
    THROW 53006, @msg6, 1;
GO

PRINT 'V353 OK — CK_ev_links_module 9 -> 10 (+CHANGE); no rows moved, none backfilled.';
GO
