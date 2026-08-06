-- V266 — SOC-D: append-only forensic chain-of-custody ledger.
--
-- Custody was a single mutable column (forensics_artefact.current_custodian) —
-- overwritten on each transfer, so the chain could not be reconstructed in court.
-- This adds an immutable ledger: transfer-custody APPENDS one row (from→to, when,
-- who, hash) and never overwrites, so the full chain is replayable in order.
-- The artefact's current_custodian is kept as a denormalised pointer to the
-- latest ledger row.
--
-- Immutability is enforced at the DB level by an INSTEAD OF UPDATE, DELETE
-- trigger that raises an error — the ledger can only ever be inserted into.
-- Forward-only + idempotent (OBJECT_ID / sys.indexes guards); safe to re-run.

IF OBJECT_ID('socops.forensics_custody_event', 'U') IS NULL
CREATE TABLE socops.forensics_custody_event (
    id                BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_forensics_custody_event PRIMARY KEY,
    organisation_id   BIGINT        NOT NULL,
    artefact_id       BIGINT        NOT NULL,
    seq_no            INT           NOT NULL,
    event_type        NVARCHAR(30)  NOT NULL CONSTRAINT df_fce_event_type DEFAULT 'TRANSFER',
    from_custodian    NVARCHAR(255) NULL,
    to_custodian      NVARCHAR(255) NOT NULL,
    from_location     NVARCHAR(300) NULL,
    to_location       NVARCHAR(300) NULL,
    artefact_hash     NVARCHAR(128) NULL,          -- sha256 recorded at the handoff
    transferred_at    DATETIME2     NOT NULL,
    actor             NVARCHAR(255) NULL,          -- the authenticated handler who performed it
    notes             NVARCHAR(MAX) NULL,
    created_at        DATETIME2     NOT NULL CONSTRAINT df_fce_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_fce_artefact FOREIGN KEY (artefact_id) REFERENCES socops.forensics_artefact(id),
    CONSTRAINT ck_fce_event_type CHECK (event_type IN ('CAPTURE','TRANSFER','VERIFY','DISPOSE','RELEASE'))
);
GO

-- Ordered chain per artefact; the unique (org, artefact, seq) prevents a duplicate
-- or out-of-order sequence number sneaking in.
IF OBJECT_ID('socops.forensics_custody_event') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_fce_artefact_seq' AND object_id = OBJECT_ID('socops.forensics_custody_event'))
CREATE UNIQUE INDEX uq_fce_artefact_seq
    ON socops.forensics_custody_event (organisation_id, artefact_id, seq_no);
GO

IF OBJECT_ID('socops.forensics_custody_event') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_fce_artefact' AND object_id = OBJECT_ID('socops.forensics_custody_event'))
CREATE INDEX ix_fce_artefact
    ON socops.forensics_custody_event (artefact_id, seq_no);
GO

-- Append-only enforcement: block every UPDATE and DELETE at the database level so
-- the chain of custody is tamper-evident even against direct SQL. INSERTs are
-- unaffected (the trigger only intercepts UPDATE/DELETE).
IF OBJECT_ID('socops.trg_fce_append_only', 'TR') IS NOT NULL
    DROP TRIGGER socops.trg_fce_append_only;
GO
CREATE TRIGGER socops.trg_fce_append_only
ON socops.forensics_custody_event
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    RAISERROR('socops.forensics_custody_event is append-only (chain of custody): UPDATE and DELETE are not permitted.', 16, 1);
END
GO
