-- SSO + Nafath — Sprint 6 production hardening (final).
--
-- One row per (tenant, provider-kind) for SSO config; one row per
-- Nafath login attempt for the transaction state machine.

CREATE TABLE sso_provider_config (
    id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    organisation_id          BIGINT               NOT NULL,
    kind                     NVARCHAR(32)         NOT NULL, -- NAFATH / SAML / OIDC
    configured               BIT                  NOT NULL DEFAULT 0,
    display_name_en          NVARCHAR(200)        NULL,
    display_name_ar          NVARCHAR(200)        NULL,
    nafath_app_id            NVARCHAR(100)        NULL,
    saml_metadata_url        NVARCHAR(500)        NULL,
    saml_issuer_entity_id    NVARCHAR(500)        NULL,
    oidc_issuer_url          NVARCHAR(500)        NULL,
    last_login_at            NVARCHAR(50)         NULL,
    is_deleted               SMALLINT             NOT NULL DEFAULT 0,
    deleted_at               DATETIME2            NULL,
    created_at               DATETIME2            NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by               NVARCHAR(100)        NULL,
    updated_at               DATETIME2            NULL,
    updated_by               NVARCHAR(100)        NULL,
    version                  BIGINT               NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_sso_org_kind
    ON sso_provider_config (organisation_id, kind)
    WHERE is_deleted = 0;

CREATE INDEX idx_sso_org
    ON sso_provider_config (organisation_id);

-- Nafath transaction log.

CREATE TABLE nafath_transaction (
    id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    organisation_id          BIGINT               NOT NULL,
    transaction_id           NVARCHAR(200)        NOT NULL UNIQUE,
    national_id_hash         NVARCHAR(64)         NOT NULL,
    random_number            INT                  NOT NULL,
    status                   NVARCHAR(20)         NOT NULL, -- PENDING / APPROVED / REJECTED / EXPIRED
    started_at               NVARCHAR(50)         NOT NULL,
    decided_at               NVARCHAR(50)         NULL,
    expires_in_seconds       INT                  NOT NULL,
    client_ip                NVARCHAR(64)         NULL,
    user_agent               NVARCHAR(500)        NULL,
    is_deleted               SMALLINT             NOT NULL DEFAULT 0,
    deleted_at               DATETIME2            NULL,
    created_at               DATETIME2            NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by               NVARCHAR(100)        NULL,
    updated_at               DATETIME2            NULL,
    updated_by               NVARCHAR(100)        NULL,
    version                  BIGINT               NOT NULL DEFAULT 0
);

CREATE INDEX idx_nafath_org ON nafath_transaction (organisation_id);
CREATE INDEX idx_nafath_started ON nafath_transaction (started_at);
