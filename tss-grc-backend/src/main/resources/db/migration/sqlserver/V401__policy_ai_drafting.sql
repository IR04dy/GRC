-- V401 — LLM Gateway Call Log (platform.llm_call_log) + AI Policy Drafting Register (policy.ai_drafts)
--
-- Supports provider-agnostic LLM policy drafting (local Ollama/vLLM/LM Studio, Gemini,
-- OpenAI-compatible, Azure OpenAI, Anthropic) with:
--   1. Per-tenant token metering and daily quota enforcement (platform.llm_call_log).
--   2. Asynchronous draft generation with human-in-the-loop review, edit, accept (into
--      PolicyManagementService#create at DRAFT so C-6 SoD and publish gates hold), or reject.

IF SCHEMA_ID('platform') IS NULL EXEC('CREATE SCHEMA platform');
GO

IF SCHEMA_ID('policy') IS NULL EXEC('CREATE SCHEMA policy');
GO

-- ── 1. platform.llm_call_log ───────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
               WHERE s.name = 'platform' AND t.name = 'llm_call_log')
BEGIN
    CREATE TABLE platform.llm_call_log (
        id                      BIGINT IDENTITY(1,1) NOT NULL,
        organisation_id         BIGINT               NOT NULL,
        feature_code            NVARCHAR(60)         NOT NULL,
        prompt_template_id      NVARCHAR(80)         NOT NULL,
        prompt_template_version NVARCHAR(20)         NOT NULL,
        provider                NVARCHAR(40)         NOT NULL,
        model                   NVARCHAR(120)        NOT NULL,
        prompt_sha256           CHAR(64)             NOT NULL,
        input_tokens            INT                  NOT NULL CONSTRAINT df_llm_call_in_tok DEFAULT 0,
        output_tokens           INT                  NOT NULL CONSTRAINT df_llm_call_out_tok DEFAULT 0,
        latency_ms              BIGINT               NOT NULL CONSTRAINT df_llm_call_lat DEFAULT 0,
        status                  NVARCHAR(20)         NOT NULL,
        finish_reason           NVARCHAR(60)         NULL,
        error_message           NVARCHAR(2000)       NULL,
        created_by              BIGINT               NULL,
        created_at              DATETIME2(7)         NOT NULL CONSTRAINT df_llm_call_created DEFAULT SYSUTCDATETIME(),
        updated_by              BIGINT               NULL,
        updated_at              DATETIME2(7)         NULL,
        is_deleted              BIT                  NOT NULL CONSTRAINT df_llm_call_del DEFAULT 0,
        deleted_at              DATETIME2(7)         NULL,
        deleted_by              BIGINT               NULL,
        row_version             ROWVERSION           NOT NULL,

        CONSTRAINT PK_llm_call_log PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_llm_call_log_status CHECK (
            status IN ('SUCCEEDED', 'FAILED', 'QUOTA_EXCEEDED', 'DISABLED'))
    );
END
GO

IF OBJECT_ID('platform.llm_call_log') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = 'ix_llm_call_log_org_created'
                     AND object_id = OBJECT_ID('platform.llm_call_log'))
    CREATE INDEX ix_llm_call_log_org_created
        ON platform.llm_call_log (organisation_id, created_at DESC)
        WHERE is_deleted = 0;
GO

-- ── 2. policy.ai_drafts ────────────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
               WHERE s.name = 'policy' AND t.name = 'ai_drafts')
BEGIN
    CREATE TABLE policy.ai_drafts (
        id                      BIGINT IDENTITY(1,1) NOT NULL,
        organisation_id         BIGINT               NOT NULL,
        external_id             NVARCHAR(64)         NOT NULL,
        status                  NVARCHAR(20)         NOT NULL,
        policy_type             NVARCHAR(60)         NOT NULL,
        category                NVARCHAR(60)         NOT NULL,
        tone                    NVARCHAR(40)         NOT NULL,
        request_json            NVARCHAR(MAX)        NOT NULL,
        output_json             NVARCHAR(MAX)        NULL,
        edited_output_json      NVARCHAR(MAX)        NULL,
        prompt_template_id      NVARCHAR(80)         NOT NULL,
        prompt_template_version NVARCHAR(20)         NOT NULL,
        provider                NVARCHAR(40)         NULL,
        model                   NVARCHAR(120)        NULL,
        input_tokens            INT                  NOT NULL CONSTRAINT df_pol_ai_in_tok DEFAULT 0,
        output_tokens           INT                  NOT NULL CONSTRAINT df_pol_ai_out_tok DEFAULT 0,
        latency_ms              BIGINT               NOT NULL CONSTRAINT df_pol_ai_lat DEFAULT 0,
        failure_reason          NVARCHAR(2000)       NULL,
        hallucinated_citations  NVARCHAR(MAX)        NULL,
        accepted_policy_id      BIGINT               NULL,
        requested_by            NVARCHAR(100)        NOT NULL,
        requested_by_user_id    BIGINT               NULL,
        reviewed_by             NVARCHAR(100)        NULL,
        reviewed_at             DATETIME2(7)         NULL,
        reviewer_note           NVARCHAR(1000)       NULL,
        created_by              BIGINT               NULL,
        created_at              DATETIME2(7)         NOT NULL CONSTRAINT df_pol_ai_created DEFAULT SYSUTCDATETIME(),
        updated_by              BIGINT               NULL,
        updated_at              DATETIME2(7)         NULL,
        is_deleted              BIT                  NOT NULL CONSTRAINT df_pol_ai_del DEFAULT 0,
        deleted_at              DATETIME2(7)         NULL,
        deleted_by              BIGINT               NULL,
        row_version             ROWVERSION           NOT NULL,

        CONSTRAINT PK_policy_ai_drafts PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_policy_ai_drafts_status CHECK (
            status IN ('REQUESTED', 'GENERATING', 'GENERATED', 'FAILED', 'ACCEPTED', 'REJECTED')),
        CONSTRAINT FK_policy_ai_drafts_accepted_policy
            FOREIGN KEY (accepted_policy_id) REFERENCES policy.documents(id)
    );
END
GO

IF OBJECT_ID('policy.ai_drafts') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = 'ux_policy_ai_drafts_org_ext'
                     AND object_id = OBJECT_ID('policy.ai_drafts'))
    CREATE UNIQUE INDEX ux_policy_ai_drafts_org_ext
        ON policy.ai_drafts (organisation_id, external_id)
        WHERE is_deleted = 0;
GO

IF OBJECT_ID('policy.ai_drafts') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = 'ix_policy_ai_drafts_org_created'
                     AND object_id = OBJECT_ID('policy.ai_drafts'))
    CREATE INDEX ix_policy_ai_drafts_org_created
        ON policy.ai_drafts (organisation_id, created_at DESC)
        WHERE is_deleted = 0;
GO

-- ══════════════════════════════════════════════════════════════════════════════════════════
-- SELF-CHECKS
-- ══════════════════════════════════════════════════════════════════════════════════════════

DECLARE @m1 NVARCHAR(MAX) = '';
SELECT @m1 = @m1 + c.n + ' '
FROM (VALUES ('id'), ('organisation_id'), ('feature_code'), ('prompt_template_id'),
             ('prompt_template_version'), ('provider'), ('model'), ('prompt_sha256'),
             ('input_tokens'), ('output_tokens'), ('latency_ms'), ('status'),
             ('finish_reason'), ('error_message'), ('created_by'), ('created_at'),
             ('updated_by'), ('updated_at'), ('is_deleted'), ('deleted_at'),
             ('deleted_by'), ('row_version')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA = 'platform' AND TABLE_NAME = 'llm_call_log'
                    AND COLUMN_NAME = c.n);
IF LEN(@m1) > 0
    THROW 57401, 'V401: platform.llm_call_log is missing mapped columns.', 1;
GO

DECLARE @m2 NVARCHAR(MAX) = '';
SELECT @m2 = @m2 + c.n + ' '
FROM (VALUES ('id'), ('organisation_id'), ('external_id'), ('status'), ('policy_type'),
             ('category'), ('tone'), ('request_json'), ('output_json'), ('edited_output_json'),
             ('prompt_template_id'), ('prompt_template_version'), ('provider'), ('model'),
             ('input_tokens'), ('output_tokens'), ('latency_ms'), ('failure_reason'),
             ('hallucinated_citations'), ('accepted_policy_id'), ('requested_by'),
             ('requested_by_user_id'), ('reviewed_by'), ('reviewed_at'), ('reviewer_note'),
             ('created_by'), ('created_at'), ('updated_by'), ('updated_at'),
             ('is_deleted'), ('deleted_at'), ('deleted_by'), ('row_version')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA = 'policy' AND TABLE_NAME = 'ai_drafts'
                    AND COLUMN_NAME = c.n);
IF LEN(@m2) > 0
    THROW 57402, 'V401: policy.ai_drafts is missing mapped columns.', 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'CK_llm_call_log_status'
                 AND parent_object_id = OBJECT_ID('platform.llm_call_log')
                 AND is_not_trusted = 0 AND is_disabled = 0)
    THROW 57403, 'V401: CK_llm_call_log_status is missing or untrusted.', 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'CK_policy_ai_drafts_status'
                 AND parent_object_id = OBJECT_ID('policy.ai_drafts')
                 AND is_not_trusted = 0 AND is_disabled = 0)
    THROW 57404, 'V401: CK_policy_ai_drafts_status is missing or untrusted.', 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name = 'FK_policy_ai_drafts_accepted_policy'
                 AND parent_object_id = OBJECT_ID('policy.ai_drafts')
                 AND is_not_trusted = 0 AND is_disabled = 0)
    THROW 57405, 'V401: FK_policy_ai_drafts_accepted_policy is missing or untrusted.', 1;
GO
