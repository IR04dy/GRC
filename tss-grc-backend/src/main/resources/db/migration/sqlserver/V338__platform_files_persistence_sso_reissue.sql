/* ============================================================================
   V338 — four more capabilities that were shipped and never given their tables.
          V12/V13/V14, re-issued above the Flyway baseline, schema-qualified and
          idempotent (the originals were neither).

   F2-71. Second-model review of the F2-68 audit found these; my own scanner had
   missed them, and the reason it missed them is the finding's twin.

   ── WHAT WAS ABSENT, AND WHAT SITS ON TOP OF IT ────────────────────────────
   `platform.file_metadata`, `platform.persistence_row`,
   `platform.sso_provider_config` and `platform.nafath_transaction` existed in
   **no schema of either database**. Above them, shipped and reachable:

     * `FileUploadController`  → `POST/GET/DELETE /api/v1/files`
     * `PersistenceController` → `/api/v1/persistence/{key}`
     * FE adapters `lib/fileUpload.ts` (used by the shared `FileUploadField`)
       and `lib/persistence.ts`
     * the SSO/Nafath entities — Saudi national-identity login, which the
       commercial review calls the largest unrecorded gap in the product

   Cause, identical to F2-64: the DDL is V12/V13/V14, production is baselined at
   `FLYWAY_BASELINE_VERSION = 292` so anything at or below it is recorded as
   applied whether it ran or not, and dev never runs Flyway at all. Authored
   2026-05-08. **Three months, four tables, two live controllers, no table.**

   ── WHY NOBODY NOTICED, WHICH IS ITS OWN LESSON ────────────────────────────
   `lib/persistence.ts` says it plainly: *"Falls back to localStorage if API
   unreachable."* The frontend swallowed the failure and kept working, so the
   defect had no symptom. That is the W2-1b fallback pattern hiding a real break —
   the same shape as the demo-data fallback, and a reason to be suspicious of any
   graceful degradation that never reports.

   ── AND WHY THE SCANNER MISSED IT ──────────────────────────────────────────
   `table_xref.py` required a `@Table` annotation to carry BOTH `name=` and
   `schema=`, and these four declare only `name=` — they resolve through
   `spring.jpa.properties.hibernate.default_schema: platform`. So the audit that
   exists to find exactly this class reported `MAPPED_NO_TABLE = 0` when the true
   answer was 4. **The tool built to catch F2-64 had F2-64's blind spot.** Both
   the scanner and `SchemaContractRatchetTest` now resolve the default schema, and
   the ratchet fails if this recurs.

   ── NOT A COPY OF V12–V14, UNLIKE V336 ─────────────────────────────────────
   V336 could re-issue V172 verbatim because V172 was already idempotent and
   schema-qualified. These three are neither: zero `IF NOT EXISTS` guards, and
   every object unqualified (`CREATE TABLE persistence_row`), which leaves the
   target schema to whatever default the running connection happens to have. That
   is a second latent hazard — the DDL and the JPA mapping each depend on a
   default, and nothing forces them to agree. So the DDL below is rewritten:
   explicitly `platform.`, every object guarded, safe to run twice.

   ⚠ AND THE COLUMNS COME FROM THE ENTITIES, NOT FROM V12–V14. The first draft of
   this migration copied their DDL and the live probe rejected it immediately:
   `Invalid column name 'deleted_by'`. Those files predate the present
   `BaseEntity` by three months — they declare `created_by`/`updated_by` as
   NVARCHAR(100), `is_deleted` as SMALLINT and a `version` BIGINT, while BaseEntity
   now requires `created_by`/`updated_by`/`deleted_by` BIGINT, `is_deleted` BIT and
   `row_version` ROWVERSION. **Re-issuing a stale migration reproduces a stale
   schema**, which is a different defect wearing the first one's clothes. V336 got
   away with a verbatim copy because V172 still matched its entities; that is luck,
   not method. The column lists below were read off the `@Column` annotations.

   ➕ This is also the first live evidence of the column-drift plane nothing yet
   measures: a table can exist, be mapped, and still 500 on one column. No scanner
   here compares `@Column` sets against `sys.columns` — filed as F2-72.

   ⚠ V12/V13/V14 must NOT be deleted — they are the historical record for any
   environment that applied them.
   ============================================================================ */

SET NOCOUNT ON;

/* ── 1. platform.persistence_row (backs lib/persistence.ts) ──────────────── */
IF OBJECT_ID('platform.persistence_row', 'U') IS NULL
BEGIN
    CREATE TABLE platform.persistence_row (
        id                 BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id    BIGINT               NOT NULL,
        store_key          NVARCHAR(200)        NOT NULL,
        row_seq            BIGINT               NOT NULL,
        payload            NVARCHAR(MAX)        NOT NULL,
        source_origin      NVARCHAR(32)         NULL,
        is_deleted         BIT                  NOT NULL DEFAULT 0,
        deleted_at         DATETIME2            NULL,
        deleted_by         BIGINT               NULL,
        created_at         DATETIME2            NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by         BIGINT               NULL,
        updated_at         DATETIME2            NULL,
        updated_by         BIGINT               NULL,
        row_version        ROWVERSION           NOT NULL
    );
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_persistence_org_key'
               AND object_id = OBJECT_ID('platform.persistence_row'))
    CREATE INDEX idx_persistence_org_key ON platform.persistence_row (organisation_id, store_key)
        WHERE is_deleted = 0;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_persistence_created'
               AND object_id = OBJECT_ID('platform.persistence_row'))
    CREATE INDEX idx_persistence_created ON platform.persistence_row (created_at);

/* ── 2. platform.file_metadata (backs lib/fileUpload.ts) ─────────────────── */
IF OBJECT_ID('platform.file_metadata', 'U') IS NULL
BEGIN
    CREATE TABLE platform.file_metadata (
        id                 BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id    BIGINT               NOT NULL,
        attached_to        NVARCHAR(200)        NOT NULL,
        filename           NVARCHAR(255)        NOT NULL,
        mime               NVARCHAR(100)        NOT NULL,
        size_bytes         BIGINT               NOT NULL,
        sha256             NVARCHAR(64)         NOT NULL,
        storage_ref        NVARCHAR(500)        NULL,
        scan_status        NVARCHAR(32)         NOT NULL,
        scan_reason        NVARCHAR(500)        NULL,
        uploaded_at        NVARCHAR(50)         NOT NULL,
        uploaded_by        NVARCHAR(200)        NOT NULL,
        cr_number          NVARCHAR(32)         NULL,
        residency          NVARCHAR(16)         NOT NULL,
        retention_days     INT                  NOT NULL,
        is_deleted         BIT                  NOT NULL DEFAULT 0,
        deleted_at         DATETIME2            NULL,
        deleted_by         BIGINT               NULL,
        created_at         DATETIME2            NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by         BIGINT               NULL,
        updated_at         DATETIME2            NULL,
        updated_by         BIGINT               NULL,
        row_version        ROWVERSION           NOT NULL
    );
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_file_org_attached'
               AND object_id = OBJECT_ID('platform.file_metadata'))
    CREATE INDEX idx_file_org_attached ON platform.file_metadata (organisation_id, attached_to)
        WHERE is_deleted = 0;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_file_org_sha'
               AND object_id = OBJECT_ID('platform.file_metadata'))
    CREATE INDEX idx_file_org_sha ON platform.file_metadata (organisation_id, sha256)
        WHERE is_deleted = 0;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_file_uploaded'
               AND object_id = OBJECT_ID('platform.file_metadata'))
    CREATE INDEX idx_file_uploaded ON platform.file_metadata (uploaded_at);

/* ── 3. platform.sso_provider_config ─────────────────────────────────────── */
IF OBJECT_ID('platform.sso_provider_config', 'U') IS NULL
BEGIN
    CREATE TABLE platform.sso_provider_config (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT               NOT NULL,
        kind                     NVARCHAR(32)         NOT NULL,   -- NAFATH / SAML / OIDC
        configured               BIT                  NOT NULL DEFAULT 0,
        display_name_en          NVARCHAR(200)        NULL,
        display_name_ar          NVARCHAR(200)        NULL,
        nafath_app_id            NVARCHAR(100)        NULL,
        saml_metadata_url        NVARCHAR(500)        NULL,
        saml_issuer_entity_id    NVARCHAR(500)        NULL,
        oidc_issuer_url          NVARCHAR(500)        NULL,
        last_login_at            NVARCHAR(50)         NULL,
        is_deleted         BIT                  NOT NULL DEFAULT 0,
        deleted_at         DATETIME2            NULL,
        deleted_by         BIGINT               NULL,
        created_at         DATETIME2            NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by         BIGINT               NULL,
        updated_at         DATETIME2            NULL,
        updated_by         BIGINT               NULL,
        row_version        ROWVERSION           NOT NULL
    );
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_sso_org_kind'
               AND object_id = OBJECT_ID('platform.sso_provider_config'))
    CREATE UNIQUE INDEX uq_sso_org_kind ON platform.sso_provider_config (organisation_id, kind)
        WHERE is_deleted = 0;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_sso_org'
               AND object_id = OBJECT_ID('platform.sso_provider_config'))
    CREATE INDEX idx_sso_org ON platform.sso_provider_config (organisation_id);

/* ── 4. platform.nafath_transaction ──────────────────────────────────────── */
IF OBJECT_ID('platform.nafath_transaction', 'U') IS NULL
BEGIN
    CREATE TABLE platform.nafath_transaction (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT               NOT NULL,
        transaction_id           NVARCHAR(200)        NOT NULL UNIQUE,
        national_id_hash         NVARCHAR(64)         NOT NULL,
        random_number            INT                  NOT NULL,
        status                   NVARCHAR(20)         NOT NULL,   -- PENDING/APPROVED/REJECTED/EXPIRED
        started_at               NVARCHAR(50)         NOT NULL,
        decided_at               NVARCHAR(50)         NULL,
        expires_in_seconds       INT                  NOT NULL,
        client_ip                NVARCHAR(64)         NULL,
        user_agent               NVARCHAR(500)        NULL,
        is_deleted         BIT                  NOT NULL DEFAULT 0,
        deleted_at         DATETIME2            NULL,
        deleted_by         BIGINT               NULL,
        created_at         DATETIME2            NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by         BIGINT               NULL,
        updated_at         DATETIME2            NULL,
        updated_by         BIGINT               NULL,
        row_version        ROWVERSION           NOT NULL
    );
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_nafath_org'
               AND object_id = OBJECT_ID('platform.nafath_transaction'))
    CREATE INDEX idx_nafath_org ON platform.nafath_transaction (organisation_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_nafath_started'
               AND object_id = OBJECT_ID('platform.nafath_transaction'))
    CREATE INDEX idx_nafath_started ON platform.nafath_transaction (started_at);

/* ── 5. All four must exist, in `platform`, where the mappings resolve. ───── */
DECLARE @missing NVARCHAR(500) = NULL;

SELECT @missing = COALESCE(@missing + N', ', N'') + n
  FROM (VALUES ('persistence_row'), ('file_metadata'),
               ('sso_provider_config'), ('nafath_transaction')) AS t(n)
 WHERE OBJECT_ID('platform.' + n, 'U') IS NULL;

IF @missing IS NOT NULL
BEGIN
    DECLARE @msg NVARCHAR(1000) =
        N'V338: still missing after the CREATEs: platform.' + @missing
        + N'. These four tables are what FileUploadController, PersistenceController and the '
        + N'SSO/Nafath entities read and write; without them every call answers 503 '
        + N'db-object-missing.';
    THROW 51338, @msg, 1;
END

/* ── 6. Anti-vacuity in the other direction: the schema they land in must be
        the one the JPA mappings resolve to. If `platform` ever stops being
        `hibernate.default_schema`, creating tables here is busywork and the
        entities will look elsewhere. ─────────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'platform')
BEGIN
    THROW 51338, 'V338: schema [platform] does not exist, so hibernate.default_schema cannot resolve and these tables would be unreachable wherever they were created.', 1;
END
