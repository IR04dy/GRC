-- V339: F2-54 — platform.audit_log records a ROLE where it claimed to record one, and says how
--       it knows.
--
-- The defect, measured on BOTH databases rather than inferred:
--
--   TSS_GRC             6,065 rows · 16 distinct user_role values · ZERO of them role names
--   TSS_GRC_Deployment  3,989 rows · 12 distinct user_role values · ZERO of them role names
--
-- 13 of the 16 are PERMISSIONS (BCM_APPROVE on 1,665 dev rows / 868 prod rows, EVIDENCE_DELETE,
-- SCM_READ, COMPLIANCE_DELETE, IAM_DELETE …) and 3 are ROLE_-prefixed authorities. The cause was
-- `auth.getAuthorities().iterator().next()` over a HashSet that mixes roles and permissions, so
-- the value was both wrong AND non-deterministic — `admin` appears under six different values,
-- `so_officer` under four.
--
-- Why that is more than a mislabelled column: this platform sells a SAMA/NCA/CMA-grade audit
-- trail. An examiner reconciling "who did this" against the role matrix got an answer that was
-- not merely wrong but DIFFERENTLY WRONG EACH TIME, which is worse — two rows that disagree are
-- indistinguishable from a genuine role change between them.
--
-- ─── This migration labels history. It does NOT rewrite it. ────────────────────────────────────
--
-- A permission does not determine which role the actor held (BCM_APPROVE is reachable from
-- several), so any back-fill of "real" roles would be fabrication — and an audit ledger that
-- edits its own past to look clean is worth less than one that admits a gap. Same precedent as
-- V322's `tally_source`: label every row, flip none.
--
--   user_role IS NOT NULL  ->  LEGACY_AUTHORITY_SAMPLE   an arbitrary authority, usually a
--                                                        permission. DO NOT reconcile against
--                                                        the role matrix.
--   user_role IS NULL      ->  LEGACY_UNRECORDED         the old code could not distinguish "no
--                                                        principal" from "principal with no
--                                                        authorities", so neither can this label,
--                                                        and it says so instead of guessing.
--
-- ⚠ No CHECK constraint, deliberately. The ck_ca_source landmine is that a CHECK over a
-- value-set the code keeps extending turns every new label into a 500 — and here a rejected
-- INSERT is a LOST AUDIT ROW, which is strictly worse than an unrecognised label. The label set
-- is pinned by AuditLogServiceTest instead (AuditLogService.USER_ROLE_SOURCES).
--
-- Idempotent: safe to run twice (dev applies it by hand, Flyway off).

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('platform.audit_log') AND name = 'user_role_source')
BEGIN
    ALTER TABLE platform.audit_log ADD user_role_source NVARCHAR(40) NULL;
END
GO

-- Back-fill only rows that predate the fix, i.e. rows with no label yet. Written as two
-- statements so the two facts stay distinguishable in the ledger forever.
UPDATE platform.audit_log
   SET user_role_source = 'LEGACY_AUTHORITY_SAMPLE'
 WHERE user_role_source IS NULL AND user_role IS NOT NULL;
GO

UPDATE platform.audit_log
   SET user_role_source = 'LEGACY_UNRECORDED'
 WHERE user_role_source IS NULL AND user_role IS NULL;
GO

-- An operator reading the ledger filters on this column; without an index the "show me every
-- row whose actor role is trustworthy" query scans the whole table.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'ix_audit_log_user_role_source'
                 AND object_id = OBJECT_ID('platform.audit_log'))
BEGIN
    CREATE INDEX ix_audit_log_user_role_source
        ON platform.audit_log (organisation_id, user_role_source);
END
GO

-- ─── Verification: THROW rather than print, so a silent failure cannot pass for success ────────
DECLARE @unlabelled INT = (SELECT COUNT(*) FROM platform.audit_log WHERE user_role_source IS NULL);
IF @unlabelled > 0
    THROW 51339, 'V339: audit_log rows left unlabelled — every row must state its role provenance', 1;

-- ⚠ The anti-vacuity check this file wanted here — "THROW if audit_log is empty, or 0 unlabelled
-- rows is the reading of an EMPTY table rather than a clean one" — was written, then removed, and
-- the reason belongs on the record: on a fresh production database Flyway runs this against an
-- empty ledger, where empty is the CORRECT state. A non-emptiness THROW would have made every new
-- install fail on migration 339 — the exact failure mode the empty-DB spine (F2-70/F2-72) exists
-- to catch, authored into the migration that is meant to be careful.
--
-- So the anti-vacuity claim is made where it can be made truthfully: the live probe asserts the
-- label count against dev's 6,065 real rows and prod's 3,989. A migration can only assert what is
-- true of every database it will ever meet, including an empty one.
GO
