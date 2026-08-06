-- V340: F2-31 — tamper-evidence for platform.audit_log.
--
-- Committee minutes carry a SHA-256 signature. The audit ledger — which every control in this
-- platform cites as its proof, and which F2-21 showed will faithfully corroborate a forgery — had
-- nothing at all: no chaining, no WORM, no protection from administrative deletion. NCA ECC 2-12
-- requires event-log protection and >=12-month retention; ISO 27001 A.8.15 and SAMA CSF agree.
--
-- ─── What this control does, stated here as well as in the code ───────────────────────────────
--
-- Each row is sealed with a MAC over its own content chained to the previous sealed row of the SAME
-- tenant. That DETECTS: modification of a sealed row, deletion of a row from the middle of a chain,
-- and insertion of a fabricated row between sealed rows. AuditChainService.verifyTenant names the
-- first broken id.
--
-- It does NOT detect truncation of the newest rows — a shorter chain is still internally valid, and
-- nothing stored inside the same database can fix that; it needs an external anchor. And unkeyed it
-- does not resist an actor with database write access at all.
--
-- ⚠ On this deployment the DBA, platform admin and deployment operator are the same person on the
-- same SQL box. Against THAT actor this is deterrence and evidence-of-effort, not prevention, and
-- the plan's caveat on F2-31 said so before a line was written. What closes the gap is key custody
-- held by someone other than the operator (grc.audit.integrity-key lives in external.properties,
-- NOT in this database — so a database-only compromise cannot forge a chain) plus an off-box
-- append-only sink. The first is configuration the customer controls; the second is not in this
-- slice and is not claimed.
--
-- Idempotent: safe to run twice (dev applies it by hand, Flyway off).

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('platform.audit_log') AND name = 'row_hash')
BEGIN
    ALTER TABLE platform.audit_log ADD
        row_hash   CHAR(64)     NULL,   -- hex SHA-256 / HMAC-SHA256
        prev_hash  VARCHAR(64)  NULL,   -- previous row_hash, or the literal 'GENESIS'
        hash_alg   VARCHAR(20)  NULL,   -- HMAC-SHA256 | SHA-256 — so a key rotation reads as one
        sealed_at  DATETIME2    NULL;
END
GO

-- The sealing pass asks "which tenants have unsealed rows, oldest id first"; verification asks
-- "give me this tenant's sealed rows in id order". Both are covered by one filtered index plus the
-- ordering key.
IF OBJECT_ID('platform.audit_log') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_audit_log_unsealed' AND object_id = OBJECT_ID('platform.audit_log'))
BEGIN
    CREATE INDEX ix_audit_log_unsealed
        ON platform.audit_log (organisation_id, id)
        WHERE row_hash IS NULL;
END
GO

IF OBJECT_ID('platform.audit_log') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_audit_log_sealed' AND object_id = OBJECT_ID('platform.audit_log'))
BEGIN
    CREATE INDEX ix_audit_log_sealed
        ON platform.audit_log (organisation_id, id)
        INCLUDE (row_hash, prev_hash, hash_alg)
        WHERE row_hash IS NOT NULL;
END
GO

-- ─── No back-fill, deliberately ───────────────────────────────────────────────────────────────
--
-- Pre-V340 rows are left UNSEALED rather than sealed retroactively, and the reason is the whole
-- point of the finding: sealing history now would produce a chain that verifies cleanly over rows
-- that were never protected — a green tick asserting an integrity guarantee that did not exist for
-- those rows. That is a forged assurance, which is exactly what F2-21 demonstrated this ledger would
-- corroborate if asked. The first sealing pass will chain them from that moment forward, and
-- verifyTenant reports "sealedRows" and "unsealedRows" separately so the untested span stays
-- visible instead of being absorbed into a verdict. Same rule as V339 and V322: label, never
-- fabricate.

-- ─── Verification: shape only, because content claims must hold on an empty database too ──────
DECLARE @cols INT = (SELECT COUNT(*) FROM sys.columns
                     WHERE object_id = OBJECT_ID('platform.audit_log')
                       AND name IN ('row_hash','prev_hash','hash_alg','sealed_at'));
IF @cols <> 4
    THROW 51340, 'V340: audit_log is missing one of the four integrity columns', 1;

DECLARE @idx INT = (SELECT COUNT(*) FROM sys.indexes
                    WHERE object_id = OBJECT_ID('platform.audit_log')
                      AND name IN ('ix_audit_log_unsealed','ix_audit_log_sealed'));
IF @idx <> 2
    THROW 51340, 'V340: audit_log is missing one of the two sealing indexes', 1;
GO
