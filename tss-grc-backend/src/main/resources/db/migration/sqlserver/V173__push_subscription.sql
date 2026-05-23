-- V173 — Slice 9-F: Web Push subscription registry for mobile Crisis Room.
--
-- Stores PushManager subscription endpoints registered by browsers (mobile
-- Safari, Chrome Android, etc.) so the platform can fan out server-initiated
-- Web Push notifications when a CRITICAL crisis is declared.
--
-- v1 of the backend service writes / lists / unregisters subscriptions and
-- emits F7 audit + F8 domain events on every mutation. Actual Web Push
-- body-signing (VAPID + ECDH on the p256dh + auth keys) lands in Sprint 12;
-- the {@code dispatchToUserIds} method is currently a stub that records the
-- intent in the audit log so downstream rules can chain on it.
--
-- Multi-tenancy: every row carries organisation_id so a cross-tenant push
-- can never accidentally be issued. The (user_id, endpoint) uniqueness is
-- partial — only enforced for live rows — which lets users re-subscribe
-- on the same device after a soft-delete without UQ collisions.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('platform') IS NULL EXEC('CREATE SCHEMA platform');
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'web_push_subscription' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.web_push_subscription (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Subscriber — the authenticated user that registered the device.
        -- Foreign key kept soft (no FK constraint) so user delete cascades
        -- through the orchestration layer rather than at the SQL layer.
        user_id             BIGINT          NOT NULL,

        -- Web Push protocol fields (RFC 8030 + RFC 8291).
        --   endpoint:    push service URL produced by PushManager.subscribe()
        --   p256dh_key:  base64url-encoded user public key for ECDH
        --   auth_key:    base64url-encoded auth secret (16 random bytes)
        -- Per spec endpoint can be up to several KB so we use NVARCHAR(MAX).
        endpoint            NVARCHAR(MAX)   NOT NULL,
        p256dh_key          NVARCHAR(255)   NOT NULL,
        auth_key            NVARCHAR(255)   NOT NULL,

        -- Human-friendly label set at registration time so the "active
        -- devices" UI can show "iPhone 15 — Safari" instead of an opaque
        -- endpoint URL. Optional; falls back to user-agent in the UI.
        device_label        NVARCHAR(255)   NULL,

        enabled             BIT             NOT NULL DEFAULT 1,
        last_used_at        DATETIME2       NULL,

        -- Standard audit / soft-delete / rowversion
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NULL,
        created_by          BIGINT          NULL,
        updated_by          BIGINT          NULL,
        is_deleted          BIT             NOT NULL DEFAULT 0,
        deleted_at          DATETIME2       NULL,
        deleted_by          BIGINT          NULL,
        row_version         ROWVERSION
    );

    -- Uniqueness on (user_id, endpoint) is enforced at the service layer
    -- via {@code findByOrganisationIdAndUserIdAndEndpoint(...)} which
    -- performs an upsert in {@code WebPushService.register(...)}.
    -- We don't add a partial UQ index here because endpoint is
    -- NVARCHAR(MAX) — SQL Server can't put MAX columns in a key, and
    -- the workarounds (CHECKSUM/HASHBYTES on a persisted computed
    -- column) bring their own quirks (SET QUOTED_IDENTIFIER persistence,
    -- collision handling) that aren't worth it for a v1 stub. A lookup
    -- index on (user_id) is good enough for the read path.

    CREATE INDEX IX_web_push_sub_org_user
        ON platform.web_push_subscription(organisation_id, user_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_web_push_sub_org_enabled
        ON platform.web_push_subscription(organisation_id, enabled)
        WHERE is_deleted = 0;
END
GO
