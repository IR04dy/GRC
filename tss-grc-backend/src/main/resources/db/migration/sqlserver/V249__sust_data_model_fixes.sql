-- =====================================================================
-- V249 — Sustainability data-model fixes (epic S-R).
-- ---------------------------------------------------------------------
-- (1) tcfd_sector_impact.transition_risk_score / physical_risk_score are
--     DECIMAL(4,2) but the DTO validates @DecimalMax("100.0") and the CHECK
--     allows <= 100 — a submitted 100.00 passes validation then overflows on
--     persist (DECIMAL(4,2) max is 99.99). Widen to DECIMAL(5,2).
-- (2) material_topic.topic_code / esg_metric.metric_code had no unique
--     constraint → duplicate business codes possible per tenant. Add filtered
--     unique indexes (live rows only).
-- (3) esg_metric_reading — new append-only time-series history table so a
--     metric's readings are retained (record-reading previously overwrote
--     current_value with no history).
-- (4) FK_tcfd_sector_scn was ON DELETE NO_ACTION → orphaned sector rows if a
--     scenario is hard-deleted. Recreate with ON DELETE CASCADE.
-- Idempotent guards throughout.
-- =====================================================================

-- (1) widen the score columns (data-preserving — widening precision).
ALTER TABLE sust.tcfd_sector_impact ALTER COLUMN transition_risk_score DECIMAL(5,2) NOT NULL;
GO
ALTER TABLE sust.tcfd_sector_impact ALTER COLUMN physical_risk_score DECIMAL(5,2) NOT NULL;
GO

-- (2) unique business codes per tenant (filtered on live, non-null rows).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_mt_org_topic_code' AND object_id = OBJECT_ID('sust.material_topic'))
    CREATE UNIQUE INDEX uq_mt_org_topic_code ON sust.material_topic(organisation_id, topic_code)
        WHERE is_deleted = 0 AND topic_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_esg_m_org_metric_code' AND object_id = OBJECT_ID('sust.esg_metric'))
    CREATE UNIQUE INDEX uq_esg_m_org_metric_code ON sust.esg_metric(organisation_id, metric_code)
        WHERE is_deleted = 0 AND metric_code IS NOT NULL;
GO

-- (3) ESG metric reading history (append-only).
IF OBJECT_ID('sust.esg_metric_reading', 'U') IS NULL
BEGIN
    CREATE TABLE sust.esg_metric_reading (
        id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_esg_metric_reading PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        metric_id       BIGINT        NOT NULL,
        reading_value   DECIMAL(20,4) NOT NULL,
        as_of           DATE          NOT NULL,
        notes           NVARCHAR(1000) NULL,
        is_deleted      BIT           NOT NULL CONSTRAINT df_emr_is_deleted DEFAULT 0,
        created_at      DATETIME2(3)  NOT NULL CONSTRAINT df_emr_created_at DEFAULT sysutcdatetime(),
        created_by      NVARCHAR(100) NULL,
        CONSTRAINT fk_emr_metric FOREIGN KEY (metric_id)
            REFERENCES sust.esg_metric(id) ON DELETE CASCADE
    );
    CREATE INDEX ix_emr_metric ON sust.esg_metric_reading(organisation_id, metric_id, as_of DESC);
END
GO

-- (4) sector-impact FK → ON DELETE CASCADE (was NO_ACTION).
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_tcfd_sector_scn' AND parent_object_id = OBJECT_ID('sust.tcfd_sector_impact'))
    ALTER TABLE sust.tcfd_sector_impact DROP CONSTRAINT FK_tcfd_sector_scn;
GO
ALTER TABLE sust.tcfd_sector_impact ADD CONSTRAINT FK_tcfd_sector_scn
    FOREIGN KEY (scenario_id) REFERENCES sust.tcfd_climate_scenario(id) ON DELETE CASCADE;
GO
