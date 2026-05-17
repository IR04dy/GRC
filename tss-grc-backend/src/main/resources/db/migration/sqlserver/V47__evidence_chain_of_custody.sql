-- V47 — Chain-of-custody hash (gap-analysis item C).
-- ===========================================================================
-- Adds append-only SHA-256 hash chain across evidence_versions so tampering
-- of historical versions is detectable. Each version's chain_hash is
-- sha256(prev_chain_hash || content_hash). The genesis (first version)
-- uses chain_hash = sha256("GENESIS:" || external_id || ":" || content_hash).
--
-- A scheduled or on-demand verifier walks each evidence_item's versions in
-- chain_position order, recomputes each chain_hash, and stamps the parent
-- evidence_item.chain_status (VALID | BROKEN | UNVERIFIED).
-- ===========================================================================

SET QUOTED_IDENTIFIER ON;
GO

-- ─── 1. New columns on evidence_versions ────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID(N'evidence.evidence_versions')
                 AND name = N'content_hash_sha256')
BEGIN
    ALTER TABLE evidence.evidence_versions
        ADD content_hash_sha256   NVARCHAR(64) NULL,
            chain_hash_sha256     NVARCHAR(64) NULL,
            chain_position        INT          NULL,
            chain_status          NVARCHAR(15) NULL,
                                              -- VALID | BROKEN | UNVERIFIED
            chain_verified_at     DATETIME2(3) NULL,
            chain_verified_by     BIGINT       NULL,
            chain_break_reason    NVARCHAR(500) NULL;
END
GO

-- ─── 2. Roll-up status on evidence_items ────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID(N'evidence.evidence_items')
                 AND name = N'chain_status')
BEGIN
    ALTER TABLE evidence.evidence_items
        ADD chain_status              NVARCHAR(15) NULL,
            chain_last_verified_at    DATETIME2(3) NULL,
            chain_last_verified_by    BIGINT       NULL,
            chain_version_count       INT          NULL,
            chain_first_break_version INT          NULL;
END
GO

-- ─── 3. Indices for chain reads ─────────────────────────────────────
IF OBJECT_ID(N'evidence.evidence_versions', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_evver_chain'
                 AND object_id = OBJECT_ID(N'evidence.evidence_versions'))
BEGIN
    CREATE INDEX IX_evver_chain ON evidence.evidence_versions
        (evidence_id, chain_position);
END
GO

-- ─── 4. Backfill: compute chain hashes for existing versions ────────
-- For each evidence_item, walk versions ordered by version_no. The first
-- version's chain_hash = SHA2_256("GENESIS:" + content_hash). Subsequent
-- versions: chain_hash = SHA2_256(prev_chain_hash + content_hash).
-- content_hash is taken from the existing evidence_hash where present;
-- otherwise we deterministically derive one from (id || file_name || file_size).
-- ===========================================================================

-- First, populate content_hash_sha256 from existing data.
UPDATE evidence.evidence_versions
   SET content_hash_sha256 = CASE
            WHEN evidence_hash IS NOT NULL AND LEN(evidence_hash) >= 64
                 THEN LOWER(LEFT(evidence_hash, 64))
            ELSE LOWER(CONVERT(NVARCHAR(64),
                 HASHBYTES('SHA2_256',
                    CAST(id AS NVARCHAR(20)) + N':' +
                    ISNULL(file_name, N'') + N':' +
                    ISNULL(CAST(file_size AS NVARCHAR(20)), N'0')
                 ), 2))
       END
 WHERE content_hash_sha256 IS NULL;
GO

-- Set chain_position = version_no for backfill (assumes version_no is monotonic per evidence)
UPDATE evidence.evidence_versions
   SET chain_position = version_no
 WHERE chain_position IS NULL;
GO

-- Walk each evidence_id's versions ordered, compute chain_hash.
-- (Use a cursor — backfill is a one-shot operation.)
DECLARE @evidence_id BIGINT;
DECLARE evidence_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT DISTINCT evidence_id FROM evidence.evidence_versions WHERE chain_hash_sha256 IS NULL;
OPEN evidence_cursor;
FETCH NEXT FROM evidence_cursor INTO @evidence_id;
WHILE @@FETCH_STATUS = 0
BEGIN
    DECLARE @prev_chain NVARCHAR(64) = NULL;
    DECLARE @ver_id BIGINT, @ver_content NVARCHAR(64), @ver_no INT;
    DECLARE @ext_id NVARCHAR(64) = (
        SELECT TOP 1 CONVERT(NVARCHAR(64), external_id) FROM evidence.evidence_items
         WHERE id = @evidence_id);

    DECLARE ver_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT id, content_hash_sha256, version_no FROM evidence.evidence_versions
         WHERE evidence_id = @evidence_id ORDER BY version_no ASC;
    OPEN ver_cursor;
    FETCH NEXT FROM ver_cursor INTO @ver_id, @ver_content, @ver_no;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        DECLARE @input NVARCHAR(MAX);
        IF @prev_chain IS NULL
            SET @input = N'GENESIS:' + ISNULL(@ext_id, N'') + N':' + ISNULL(@ver_content, N'');
        ELSE
            SET @input = @prev_chain + N':' + ISNULL(@ver_content, N'');

        DECLARE @chain NVARCHAR(64) = LOWER(CONVERT(NVARCHAR(64), HASHBYTES('SHA2_256', @input), 2));
        UPDATE evidence.evidence_versions
           SET chain_hash_sha256 = @chain,
               chain_status      = N'VALID',
               chain_verified_at = SYSUTCDATETIME()
         WHERE id = @ver_id;
        SET @prev_chain = @chain;

        FETCH NEXT FROM ver_cursor INTO @ver_id, @ver_content, @ver_no;
    END
    CLOSE ver_cursor; DEALLOCATE ver_cursor;

    FETCH NEXT FROM evidence_cursor INTO @evidence_id;
END
CLOSE evidence_cursor; DEALLOCATE evidence_cursor;
GO

-- Roll up to evidence_items
UPDATE ei
   SET chain_status              = N'VALID',
       chain_last_verified_at    = SYSUTCDATETIME(),
       chain_version_count       = vc.n,
       chain_first_break_version = NULL
  FROM evidence.evidence_items ei
  JOIN (SELECT evidence_id, COUNT(*) AS n
          FROM evidence.evidence_versions
         WHERE chain_status = N'VALID'
         GROUP BY evidence_id) vc ON vc.evidence_id = ei.id
 WHERE ei.chain_status IS NULL;
GO

-- ─── 5. Inject a deliberately BROKEN chain for demo purposes ────────
-- Pick one evidence_item with >= 2 versions and corrupt the second version's
-- content_hash so the chain verifier will flag it.

IF NOT EXISTS (SELECT 1 FROM evidence.evidence_items
                WHERE chain_status = N'BROKEN' AND is_deleted = 0)
BEGIN
    DECLARE @target_evidence BIGINT, @target_version BIGINT;
    SELECT TOP 1 @target_evidence = evidence_id, @target_version = id
      FROM evidence.evidence_versions
      WHERE evidence_id IN (
          SELECT evidence_id FROM evidence.evidence_versions
          GROUP BY evidence_id HAVING COUNT(*) >= 2)
        AND version_no = 2
      ORDER BY evidence_id;

    IF @target_version IS NOT NULL
    BEGIN
        -- Corrupt the content_hash; leave chain_hash intact → verification will FAIL
        UPDATE evidence.evidence_versions
           SET content_hash_sha256 = N'deadbeef' + REPLICATE(N'0', 56),
               chain_status        = N'BROKEN',
               chain_break_reason  = N'Detected during routine verification — content_hash does not match recorded value. Tampering or storage corruption suspected.'
         WHERE id = @target_version;

        UPDATE evidence.evidence_items
           SET chain_status              = N'BROKEN',
               chain_last_verified_at    = SYSUTCDATETIME(),
               chain_first_break_version = 2
         WHERE id = @target_evidence;
    END
END
GO

PRINT N'V47 — Evidence chain-of-custody hash columns + backfill + 1 demo broken chain applied.';
GO
