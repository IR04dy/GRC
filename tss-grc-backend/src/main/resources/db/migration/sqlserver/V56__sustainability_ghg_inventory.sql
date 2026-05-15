-- V56 — GHG Emission Inventory (Sustainability module · vertical slice 2)
--
-- Per-record GHG inventory keyed on (reporting_year, scope, source).
-- Replaces the static mock-data display on the existing /esg overview
-- with a queryable register that supports the GHG-Protocol verification
-- workflow + Rule 1 (Scope 3 < 70% coverage → Partial disclosure).
--
-- Status machine:
--   DRAFT → SUBMITTED → VERIFIED → APPROVED  (forward only)
--   any → ARCHIVED                            (retire)
--
-- Workflow rules enforced in the service layer:
--   Rule 1  — if Scope 3 coverage_percent < 70, dashboard surfaces a
--             "Partial Scope-3 disclosure" gap. Aggregate is computed
--             by the GET /aggregate endpoint, not by a column.
--   Rule 2  — if verified = 1 then evidence_ref is required. The
--             POST /:id/verify endpoint refuses without it.
--
-- Seed: 9 records — 3 Scope 1 (fleet diesel, generators, refrigerants),
-- 2 Scope 2 (Riyadh + Jeddah grid electricity), 4 Scope 3 (business
-- travel, employee commute, purchased goods, capital goods). Numbers
-- align with the existing /esg overview's CURRENT_YEAR_RECORD so the
-- two views agree.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'ghg_emission_record' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.ghg_emission_record (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,
        record_code              NVARCHAR(40)  NULL,

        reporting_year           SMALLINT      NOT NULL,
        scope                    TINYINT       NOT NULL,            -- 1, 2, 3
        category                 NVARCHAR(60)  NOT NULL,            -- e.g. STATIONARY_COMBUSTION, MOBILE_COMBUSTION, PURCHASED_ELECTRICITY, BUSINESS_TRAVEL...

        source_name_en           NVARCHAR(255) NOT NULL,
        source_name_ar           NVARCHAR(255) NULL,

        business_unit            NVARCHAR(120) NULL,
        facility                 NVARCHAR(120) NULL,

        -- Raw activity input + emission factor → emission_value_tco2e is
        -- the authoritative value (service may recompute on save).
        activity_data            DECIMAL(20,4) NULL,
        activity_unit            NVARCHAR(40)  NULL,   -- L, kWh, km, kg, tonne
        emission_factor          DECIMAL(20,6) NULL,
        emission_factor_unit     NVARCHAR(40)  NULL,   -- kgCO2e/L, tCO2e/MWh, ...
        emission_value_tco2e     DECIMAL(18,2) NOT NULL,

        coverage_percent         DECIMAL(5,2)  NOT NULL DEFAULT 100,   -- Scope-3 typically < 100

        verified                 BIT           NOT NULL DEFAULT 0,
        verification_provider    NVARCHAR(120) NULL,
        verification_date        DATE          NULL,

        methodology              NVARCHAR(120) NULL,                   -- e.g. "GHG Protocol Corporate Standard"
        notes                    NVARCHAR(MAX) NULL,

        evidence_ref             NVARCHAR(255) NULL,
        owner_name               NVARCHAR(255) NULL,
        owner_email              NVARCHAR(255) NULL,

        -- DRAFT | SUBMITTED | VERIFIED | APPROVED | ARCHIVED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        submitted_at             DATETIME2     NULL,
        approved_at              DATETIME2     NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               NVARCHAR(100) NULL,
        updated_at               DATETIME2     NULL,
        updated_by               NVARCHAR(100) NULL,

        CONSTRAINT ck_ghg_scope    CHECK (scope IN (1, 2, 3)),
        CONSTRAINT ck_ghg_coverage CHECK (coverage_percent BETWEEN 0 AND 100),
        CONSTRAINT ck_ghg_value    CHECK (emission_value_tco2e >= 0),
        CONSTRAINT ck_ghg_status   CHECK (status IN (N'DRAFT', N'SUBMITTED', N'VERIFIED', N'APPROVED', N'ARCHIVED'))
    );

    CREATE INDEX ix_ghg_org_year       ON sust.ghg_emission_record(organisation_id, reporting_year);
    CREATE INDEX ix_ghg_org_year_scope ON sust.ghg_emission_record(organisation_id, reporting_year, scope);
    CREATE INDEX ix_ghg_org_status     ON sust.ghg_emission_record(organisation_id, status);
END
GO

-- ─── Seed — 9 records aligned to the existing /esg overview totals ──
-- Targets: Scope 1 ≈ 14,220 tCO2e · Scope 2 ≈ 38,910 tCO2e ·
--          Scope 3 ≈ 142,480 tCO2e (67% coverage)

IF NOT EXISTS (SELECT 1 FROM sust.ghg_emission_record WHERE organisation_id = 1)
BEGIN
    INSERT INTO sust.ghg_emission_record (
        organisation_id, reporting_year, scope, category, record_code,
        source_name_en, source_name_ar,
        business_unit, facility,
        activity_data, activity_unit, emission_factor, emission_factor_unit,
        emission_value_tco2e, coverage_percent,
        verified, verification_provider, verification_date, methodology,
        evidence_ref, owner_name, owner_email, status, submitted_at, approved_at
    )
    VALUES
    -- Scope 1 — direct emissions ----------------------------------------
    (1, 2025, 1, N'MOBILE_COMBUSTION',     N'GHG-S1-001',
     N'Fleet diesel — light commercial vehicles',
     N'وقود الديزل — أسطول المركبات الخفيفة',
     N'Logistics', N'Riyadh-HQ',
     1840000, N'L', 2.687500, N'kgCO2e/L',
     4945.00, 100.0,
     1, N'Bureau Veritas KSA', '2026-02-12', N'GHG Protocol Corporate Standard',
     N'BV-Verif-2025-S1-Fleet.pdf', N'Khalid Al-Otaibi', N'k.alotaibi@tss.sa',
     N'APPROVED', '2026-01-28', '2026-02-18'),

    (1, 2025, 1, N'STATIONARY_COMBUSTION', N'GHG-S1-002',
     N'Backup diesel generators — data centres',
     N'مولّدات احتياطية تعمل بالديزل — مراكز البيانات',
     N'IT Operations', N'Riyadh-DC + Jeddah-DC',
     2100000, N'L', 2.687500, N'kgCO2e/L',
     5644.00, 100.0,
     1, N'Bureau Veritas KSA', '2026-02-12', N'GHG Protocol Corporate Standard',
     N'BV-Verif-2025-S1-Gen.pdf', N'Lina Al-Harbi', N'l.alharbi@tss.sa',
     N'APPROVED', '2026-01-28', '2026-02-18'),

    (1, 2025, 1, N'FUGITIVE',              N'GHG-S1-003',
     N'Refrigerant top-up — HVAC R-410A',
     N'إعادة شحن غاز التبريد — تكييف R-410A',
     N'Facilities', N'All offices',
     1850, N'kg', 2.088000, N'tCO2e/kg',
     3631.00, 100.0,
     1, N'Bureau Veritas KSA', '2026-02-12', N'GHG Protocol Corporate Standard',
     N'BV-Verif-2025-S1-Refr.pdf', N'Khalid Al-Otaibi', N'k.alotaibi@tss.sa',
     N'APPROVED', '2026-01-28', '2026-02-18'),

    -- Scope 2 — indirect grid electricity --------------------------------
    (1, 2025, 2, N'PURCHASED_ELECTRICITY', N'GHG-S2-001',
     N'Grid electricity — Riyadh operations',
     N'كهرباء الشبكة — عمليات الرياض',
     N'All Riyadh BUs', N'Riyadh-HQ + Riyadh-DC',
     56800, N'MWh', 0.487000, N'tCO2e/MWh',
     27662.00, 100.0,
     1, N'KPMG Lower Gulf', '2026-02-20', N'GHG Protocol Scope 2 — Location-based',
     N'KPMG-Verif-2025-S2-Riyadh.pdf', N'Lina Al-Harbi', N'l.alharbi@tss.sa',
     N'APPROVED', '2026-01-30', '2026-02-22'),

    (1, 2025, 2, N'PURCHASED_ELECTRICITY', N'GHG-S2-002',
     N'Grid electricity — Jeddah operations',
     N'كهرباء الشبكة — عمليات جدة',
     N'All Jeddah BUs', N'Jeddah-DC + Jeddah-Office',
     23100, N'MWh', 0.487000, N'tCO2e/MWh',
     11248.00, 100.0,
     1, N'KPMG Lower Gulf', '2026-02-20', N'GHG Protocol Scope 2 — Location-based',
     N'KPMG-Verif-2025-S2-Jeddah.pdf', N'Lina Al-Harbi', N'l.alharbi@tss.sa',
     N'APPROVED', '2026-01-30', '2026-02-22'),

    -- Scope 3 — value-chain emissions (partial coverage = 67%) -----------
    (1, 2025, 3, N'PURCHASED_GOODS_SERVICES', N'GHG-S3-001',
     N'Purchased ICT goods — servers, networking, devices',
     N'مشتريات تقنية المعلومات — خوادم وأجهزة الشبكات',
     N'IT Operations', NULL,
     NULL, NULL, NULL, NULL,
     86420.00, 72.0,
     0, NULL, NULL, N'GHG Protocol Scope 3 — Spend-based',
     N'Procurement-spend-2025.xlsx', N'Sara Al-Zahrani', N's.alzahrani@tss.sa',
     N'SUBMITTED', '2026-03-04', NULL),

    (1, 2025, 3, N'BUSINESS_TRAVEL',           N'GHG-S3-002',
     N'Business travel — air + ground',
     N'سفر الأعمال — جوي وبري',
     N'All BUs', NULL,
     12400000, N'km', 0.001540, N'kgCO2e/passenger-km',
     19100.00, 89.0,
     0, NULL, NULL, N'DEFRA 2025 travel emission factors',
     N'Travel-system-export-2025.csv', N'Sara Al-Zahrani', N's.alzahrani@tss.sa',
     N'SUBMITTED', '2026-03-04', NULL),

    (1, 2025, 3, N'EMPLOYEE_COMMUTING',        N'GHG-S3-003',
     N'Employee commute — survey-based estimate',
     N'تنقّل الموظفين — تقدير من الاستبيان',
     N'HR', NULL,
     NULL, NULL, NULL, NULL,
     18260.00, 54.0,
     0, NULL, NULL, N'Survey + DEFRA factors',
     N'HR-Commute-Survey-2025.pdf', N'Mohammed Al-Sulaiman', N'm.alsulaiman@tss.sa',
     N'DRAFT', NULL, NULL),

    (1, 2025, 3, N'CAPITAL_GOODS',             N'GHG-S3-004',
     N'Capital goods — buildings + DC expansion',
     N'السلع الرأسمالية — المباني وتوسعة مركز البيانات',
     N'Finance', NULL,
     NULL, NULL, NULL, NULL,
     18700.00, 48.0,
     0, NULL, NULL, N'GHG Protocol Scope 3 — Hybrid method',
     N'Capex-register-2025.xlsx', N'Khalid Al-Otaibi', N'k.alotaibi@tss.sa',
     N'DRAFT', NULL, NULL);
END
GO
