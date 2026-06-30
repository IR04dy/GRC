-- V240 — Evidence-request comment thread (plan task §12.9).
--
-- Until now an evidence request carried only a single free-text `notes` blob —
-- no collaborative review trail. Reviewers and owners need a running comment
-- thread on a request: clarify scope, explain a rejection, confirm a
-- resubmission. This adds an append-only comment table keyed to a request,
-- reusing the standard BaseEntity audit columns. Soft-deleted via is_deleted
-- (matches the @Where(is_deleted = 0) entity filter used everywhere).

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'evidence' AND t.name = 'evidence_request_comments')
BEGIN
    CREATE TABLE evidence.evidence_request_comments (
        id              BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        request_id      BIGINT        NOT NULL,
        author_id       BIGINT        NULL,
        body            NVARCHAR(MAX) NOT NULL,
        created_at      DATETIME2     NOT NULL CONSTRAINT df_evreq_comment_created DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2     NULL,
        created_by      BIGINT        NULL,
        updated_by      BIGINT        NULL,
        is_deleted      BIT           NOT NULL CONSTRAINT df_evreq_comment_deleted DEFAULT 0,
        deleted_at      DATETIME2     NULL,
        deleted_by      BIGINT        NULL,
        row_version     ROWVERSION
    );

    CREATE INDEX ix_evreq_comment_request
        ON evidence.evidence_request_comments (organisation_id, request_id)
        WHERE is_deleted = 0;
END
GO
