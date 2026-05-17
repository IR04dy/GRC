-- V47a — Inject a second version + a BROKEN chain for chain-of-custody demo.
-- All current evidence items have only 1 version, so the previous V47
-- broken-chain inject was a no-op. Here we (a) add v2 to one evidence item
-- so its chain has > 1 link, and (b) corrupt v2's content_hash so the
-- verifier flags the chain as BROKEN.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @target_evidence BIGINT;
SELECT TOP 1 @target_evidence = id FROM evidence.evidence_items
  WHERE is_deleted = 0 AND chain_status = N'VALID'
  ORDER BY id ASC;

IF @target_evidence IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM evidence.evidence_versions
                 WHERE evidence_id = @target_evidence AND version_no = 2)
BEGIN
    -- Snapshot v1 hash for chaining
    DECLARE @v1_chain NVARCHAR(64) = (
        SELECT chain_hash_sha256 FROM evidence.evidence_versions
         WHERE evidence_id = @target_evidence AND version_no = 1);

    -- Insert a clean v2
    DECLARE @v2_content NVARCHAR(64) = LOWER(CONVERT(NVARCHAR(64),
        HASHBYTES('SHA2_256',
            N'v2-content-' + CAST(@target_evidence AS NVARCHAR(20)) +
            CAST(SYSUTCDATETIME() AS NVARCHAR(40))),
        2));
    DECLARE @v2_chain NVARCHAR(64) = LOWER(CONVERT(NVARCHAR(64),
        HASHBYTES('SHA2_256', @v1_chain + N':' + @v2_content), 2));

    INSERT INTO evidence.evidence_versions
        (evidence_id, version_no, title_en, description_en,
         evidence_hash, previous_hash,
         file_name, file_path, file_size, change_summary,
         snapshotted_at, snapshotted_by,
         content_hash_sha256, chain_hash_sha256, chain_position,
         chain_status, chain_verified_at)
    VALUES
        (@target_evidence, 2,
         N'v2 — content refreshed',
         N'Quarterly evidence refresh; content updated; chain recorded.',
         @v2_content, @v1_chain,
         N'evidence-v2.pdf', N'/blob/evidence-v2.pdf', 245876,
         N'Routine refresh — same control coverage, updated metadata.',
         DATEADD(DAY, -7, SYSUTCDATETIME()), 1,
         @v2_content, @v2_chain, 2,
         N'VALID', SYSUTCDATETIME());

    UPDATE evidence.evidence_items
       SET current_version_no  = 2,
           chain_version_count = 2,
           chain_last_verified_at = SYSUTCDATETIME()
     WHERE id = @target_evidence;

    -- Insert a third version that is then corrupted so the chain breaks at v3
    DECLARE @v3_content_real NVARCHAR(64) = LOWER(CONVERT(NVARCHAR(64),
        HASHBYTES('SHA2_256',
            N'v3-original-' + CAST(@target_evidence AS NVARCHAR(20))),
        2));
    DECLARE @v3_chain_recorded NVARCHAR(64) = LOWER(CONVERT(NVARCHAR(64),
        HASHBYTES('SHA2_256', @v2_chain + N':' + @v3_content_real), 2));

    -- Insert v3 with the *recorded* chain hash matching the original content...
    INSERT INTO evidence.evidence_versions
        (evidence_id, version_no, title_en, description_en,
         evidence_hash, previous_hash,
         file_name, file_path, file_size, change_summary,
         snapshotted_at, snapshotted_by,
         content_hash_sha256, chain_hash_sha256, chain_position,
         chain_status, chain_verified_at, chain_break_reason)
    VALUES
        (@target_evidence, 3,
         N'v3 — tampered content (demo)',
         N'Demo: simulating storage corruption — content_hash on disk no longer matches the recorded chain_hash.',
         @v3_content_real, @v2_chain,
         N'evidence-v3.pdf', N'/blob/evidence-v3.pdf', 250112,
         N'(Demo) content tampered after snapshot — verifier should flag.',
         DATEADD(DAY, -2, SYSUTCDATETIME()), 1,
         -- ...but corrupt the stored content_hash so the verifier detects mismatch:
         N'deadbeef' + REPLICATE(N'0', 56),
         @v3_chain_recorded, 3,
         N'BROKEN', SYSUTCDATETIME(),
         N'Recorded chain_hash does not match SHA2_256(previous_chain || content_hash). Content tampered or storage corrupted after snapshot.');

    UPDATE evidence.evidence_items
       SET current_version_no       = 3,
           chain_version_count      = 3,
           chain_status             = N'BROKEN',
           chain_first_break_version = 3,
           chain_last_verified_at   = SYSUTCDATETIME()
     WHERE id = @target_evidence;
END
GO

PRINT N'V47a — added v2 (VALID) and v3 (BROKEN demo) to one evidence item.';
GO
