-- V196 — Sprint 13: Sanctions Screening engine + hit triage workflow.
--
-- Every Saudi bank must screen customer onboarding + transaction
-- counterparties against:
--   1. OFAC SDN (US Treasury) — ~16,000 individuals/entities
--   2. UN Consolidated List   — ~700 entries (Resolutions 1267, 1373, 1718, 2231…)
--   3. EU Consolidated List   — ~5,000 entries
--   4. SAMA Local Terror List — ~150 KSA-issued terror designations
--   5. KSA National Watchlist — ~80 internal designations
--
-- Today this is a black-box vendor service (FircoSoft, ComplyAdvantage,
-- Refinitiv) producing 1,000+ daily hits with a 95%+ false-positive rate
-- and triage in Excel. This slice introduces an in-platform screening +
-- hit-triage workflow that the GRC team owns; the vendor service plugs
-- in as a SCREENING_SOURCE = INTEGRATION_VENDOR adapter.
--
-- Four tables under a new aml schema:
--
--   aml.sanctions_list        — one row per list (org_id NULL = platform-global)
--   aml.sanctions_list_entry  — denormalised entries pulled from each list
--   aml.screening_check       — one row per subject screened (customer, txn…)
--   aml.screening_hit         — one row per potential/confirmed match
--
-- Regulatory anchors:
--   - FATF Recommendation 5 (Terrorist Financing — list screening)
--   - FATF Recommendations 6 + 7 (Targeted Financial Sanctions — UN
--     Security Council resolutions on terrorism and proliferation)
--   - SAMA AML/CFT Rules 2018 §3.4 — explicit list-screening obligation
--   - NCA OPSEC — terrorism-financing list screening as a documented control
--   - SAFIU (Saudi Arabia Financial Intelligence Unit) guidance on STR + sanctions
--
-- Pre-seed (idempotent, IF NOT EXISTS guards):
--   - 5 sanctions lists (org_id NULL = platform-global)
--   - 12 demo list entries — all [DEMO]-prefixed to make clear they are
--     training data, NOT real designations
--   - 8 demo screening checks for org=1: 5 NO_HIT, 2 POTENTIAL_HIT, 1 CONFIRMED_HIT
--   - 3 hits across the 2 POTENTIAL_HIT and 1 CONFIRMED_HIT checks

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('aml') IS NULL EXEC('CREATE SCHEMA aml');
GO

-- ─── sanctions_list ───────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'sanctions_list' AND schema_id = SCHEMA_ID('aml'))
BEGIN
    CREATE TABLE aml.sanctions_list (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        -- NULL = platform-global list (OFAC/UN/EU), org-scoped lists allowed
        organisation_id                 BIGINT          NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        list_code                       VARCHAR(64)     NOT NULL,
        title_en                        NVARCHAR(255)   NOT NULL,
        title_ar                        NVARCHAR(255)   NOT NULL,

        source_url                      NVARCHAR(500)   NULL,
        -- UN | OFAC | EU | SAMA | KSA_NATIONAL | FATF | OTHER
        source_type                     VARCHAR(40)     NOT NULL,

        last_refreshed_at               DATETIME2       NULL,
        -- DAILY | WEEKLY | MONTHLY | ON_DEMAND
        refresh_frequency               VARCHAR(20)     NOT NULL DEFAULT 'DAILY',

        entry_count                     INT             NOT NULL DEFAULT 0,
        is_active                       BIT             NOT NULL DEFAULT 1,

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION
    );

    -- list_code unique across active rows (org-NULL global lists share the namespace
    -- with org-scoped lists — we enforce uniqueness on (org_id, list_code) so an
    -- org can have its own internal list code that collides with a global code).
    -- Filtered to is_deleted = 0 so a soft-deleted list can be recreated.
    CREATE UNIQUE INDEX UX_sanctions_list_org_code
        ON aml.sanctions_list(organisation_id, list_code)
        WHERE is_deleted = 0;

    CREATE INDEX IX_sanctions_list_active
        ON aml.sanctions_list(is_active)
        WHERE is_deleted = 0;

    CREATE INDEX IX_sanctions_list_source_type
        ON aml.sanctions_list(source_type)
        WHERE is_deleted = 0;
END
GO

-- ─── sanctions_list_entry ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'sanctions_list_entry' AND schema_id = SCHEMA_ID('aml'))
BEGIN
    CREATE TABLE aml.sanctions_list_entry (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        -- NULL = inherits the list's tenancy (global)
        organisation_id                 BIGINT          NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        list_id                         BIGINT          NOT NULL,
        entry_external_id               NVARCHAR(64)    NOT NULL,  -- e.g. OFAC unique-id

        -- INDIVIDUAL | ENTITY | VESSEL | AIRCRAFT
        entity_type                     VARCHAR(20)     NOT NULL,

        primary_name_en                 NVARCHAR(500)   NOT NULL,
        primary_name_ar                 NVARCHAR(500)   NULL,

        -- JSON arrays: ["alias 1", "alias 2", ...]
        aliases_json                    NVARCHAR(MAX)   NULL,

        date_of_birth                   DATE            NULL,
        country                         NVARCHAR(100)   NULL,

        -- JSON: [{"type":"passport","country":"SY","number":"…"}, ...]
        id_documents_json               NVARCHAR(MAX)   NULL,

        listing_date                    DATE            NULL,
        listed_by                       NVARCHAR(255)   NULL,
        listing_reason_en               NVARCHAR(1000)  NULL,
        listing_reason_ar               NVARCHAR(1000)  NULL,

        -- STRONG_MATCH_REQUIRED | REVIEW_REQUIRED
        severity                        VARCHAR(30)     NOT NULL DEFAULT 'REVIEW_REQUIRED',

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_sanctions_entry_list
            FOREIGN KEY (list_id) REFERENCES aml.sanctions_list(id)
    );

    -- Entry external IDs unique per (list, entry_external_id).
    CREATE UNIQUE INDEX UX_sanctions_entry_list_extid
        ON aml.sanctions_list_entry(list_id, entry_external_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_sanctions_entry_list
        ON aml.sanctions_list_entry(list_id)
        WHERE is_deleted = 0;

    -- Name lookup index — primary_name_en is the most-screened field
    CREATE INDEX IX_sanctions_entry_name_en
        ON aml.sanctions_list_entry(primary_name_en)
        WHERE is_deleted = 0;
END
GO

-- ─── screening_check ──────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'screening_check' AND schema_id = SCHEMA_ID('aml'))
BEGIN
    CREATE TABLE aml.screening_check (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        check_code                      NVARCHAR(64)    NOT NULL,  -- e.g. SCR-2026-001234

        -- CUSTOMER_ONBOARDING | TRANSACTION_COUNTERPARTY | BENEFICIAL_OWNER |
        -- EMPLOYEE_KYC | VENDOR_KYC | AD_HOC
        subject_type                    VARCHAR(40)     NOT NULL,

        -- Free-text FK soft-link (customer/transaction ID) — kept as a string
        -- so it can reference any subject across modules without FKs.
        subject_reference               NVARCHAR(255)   NULL,

        subject_name_en                 NVARCHAR(500)   NOT NULL,
        subject_name_ar                 NVARCHAR(500)   NULL,
        subject_dob                     DATE            NULL,
        subject_country                 NVARCHAR(100)   NULL,
        subject_id_document             NVARCHAR(255)   NULL,

        check_at                        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),

        -- MANUAL | REAL_TIME_API | BATCH_FILE | INTEGRATION_VENDOR
        check_source                    VARCHAR(40)     NOT NULL DEFAULT 'MANUAL',

        -- Comma-separated list_code values (e.g. "OFAC-SDN,UN-CONSOLIDATED")
        lists_screened_codes            NVARCHAR(500)   NULL,

        -- NO_HIT | POTENTIAL_HIT | CONFIRMED_HIT
        result                          VARCHAR(20)     NOT NULL DEFAULT 'NO_HIT',

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION
    );

    CREATE UNIQUE INDEX UX_screening_check_org_code
        ON aml.screening_check(organisation_id, check_code)
        WHERE is_deleted = 0;

    CREATE INDEX IX_screening_check_org_at
        ON aml.screening_check(organisation_id, check_at)
        WHERE is_deleted = 0;

    CREATE INDEX IX_screening_check_org_result
        ON aml.screening_check(organisation_id, result)
        WHERE is_deleted = 0;

    CREATE INDEX IX_screening_check_org_subjectref
        ON aml.screening_check(organisation_id, subject_reference)
        WHERE is_deleted = 0;
END
GO

-- ─── screening_hit ────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'screening_hit' AND schema_id = SCHEMA_ID('aml'))
BEGIN
    CREATE TABLE aml.screening_hit (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        external_id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        check_id                        BIGINT          NOT NULL,
        list_entry_id                   BIGINT          NOT NULL,

        -- 0.00 - 100.00 (fuzzy-match score, see SanctionsScreeningService)
        match_score                     DECIMAL(5,2)    NOT NULL,

        -- EXACT_NAME | FUZZY_NAME | ALIAS | DOB_MATCH | DOCUMENT_MATCH |
        -- COUNTRY_MATCH | COMPOSITE
        match_type                      VARCHAR(40)     NOT NULL,

        -- PENDING_REVIEW | TRUE_POSITIVE | FALSE_POSITIVE | ESCALATED |
        -- BLOCK_RECOMMENDED | RELEASE_RECOMMENDED | CLOSED
        status                          VARCHAR(20)     NOT NULL DEFAULT 'PENDING_REVIEW',

        reviewer_user_id                BIGINT          NULL,
        reviewed_at                     DATETIME2       NULL,

        disposition_en                  NVARCHAR(2000)  NULL,
        disposition_ar                  NVARCHAR(2000)  NULL,

        sla_breach                      BIT             NOT NULL DEFAULT 0,

        escalated_to_user_id            BIGINT          NULL,
        escalated_at                    DATETIME2       NULL,

        capa_task_id                    BIGINT          NULL,

        created_by_user_id              BIGINT          NULL,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        is_deleted                      BIT             NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2       NULL,
        deleted_by                      BIGINT          NULL,
        row_version                     ROWVERSION,

        CONSTRAINT FK_screening_hit_check
            FOREIGN KEY (check_id) REFERENCES aml.screening_check(id),
        CONSTRAINT FK_screening_hit_entry
            FOREIGN KEY (list_entry_id) REFERENCES aml.sanctions_list_entry(id)
    );

    CREATE INDEX IX_screening_hit_check
        ON aml.screening_hit(organisation_id, check_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_screening_hit_status
        ON aml.screening_hit(organisation_id, status)
        WHERE is_deleted = 0;

    CREATE INDEX IX_screening_hit_sla_breach
        ON aml.screening_hit(organisation_id, sla_breach)
        WHERE is_deleted = 0 AND sla_breach = 1;

    CREATE INDEX IX_screening_hit_entry
        ON aml.screening_hit(list_entry_id)
        WHERE is_deleted = 0;
END
GO

-- ─── Pre-seed: 5 sanctions lists (platform-global, organisation_id NULL) ─

-- OFAC SDN
IF NOT EXISTS (SELECT 1 FROM aml.sanctions_list WHERE list_code = 'OFAC-SDN' AND organisation_id IS NULL)
BEGIN
    INSERT INTO aml.sanctions_list
        (organisation_id, list_code, title_en, title_ar, source_url, source_type,
         refresh_frequency, entry_count, is_active, last_refreshed_at)
    VALUES
        (NULL, 'OFAC-SDN',
         'OFAC Specially Designated Nationals (SDN) List',
         N'قائمة الأشخاص المُعيَّنين بصفة خاصّة (OFAC SDN) — وزارة الخزانة الأمريكية',
         'https://placeholder.example/ofac/sdn.json',
         'OFAC', 'DAILY', 16000, 1, '2026-05-25 06:00:00');
END
GO

-- UN Consolidated
IF NOT EXISTS (SELECT 1 FROM aml.sanctions_list WHERE list_code = 'UN-CONSOLIDATED' AND organisation_id IS NULL)
BEGIN
    INSERT INTO aml.sanctions_list
        (organisation_id, list_code, title_en, title_ar, source_url, source_type,
         refresh_frequency, entry_count, is_active, last_refreshed_at)
    VALUES
        (NULL, 'UN-CONSOLIDATED',
         'UN Consolidated Sanctions List (Security Council)',
         N'قائمة عقوبات الأمم المتحدة الموحّدة (مجلس الأمن)',
         'https://placeholder.example/un/consolidated.xml',
         'UN', 'DAILY', 700, 1, '2026-05-25 06:00:00');
END
GO

-- EU Consolidated
IF NOT EXISTS (SELECT 1 FROM aml.sanctions_list WHERE list_code = 'EU-CONSOLIDATED' AND organisation_id IS NULL)
BEGIN
    INSERT INTO aml.sanctions_list
        (organisation_id, list_code, title_en, title_ar, source_url, source_type,
         refresh_frequency, entry_count, is_active, last_refreshed_at)
    VALUES
        (NULL, 'EU-CONSOLIDATED',
         'EU Consolidated Financial Sanctions List',
         N'قائمة العقوبات المالية الأوروبية الموحّدة',
         'https://placeholder.example/eu/fsf.xml',
         'EU', 'DAILY', 5000, 1, '2026-05-25 06:00:00');
END
GO

-- SAMA Local Terror
IF NOT EXISTS (SELECT 1 FROM aml.sanctions_list WHERE list_code = 'SAMA-LOCAL-TERROR' AND organisation_id IS NULL)
BEGIN
    INSERT INTO aml.sanctions_list
        (organisation_id, list_code, title_en, title_ar, source_url, source_type,
         refresh_frequency, entry_count, is_active, last_refreshed_at)
    VALUES
        (NULL, 'SAMA-LOCAL-TERROR',
         'SAMA Local Terrorism Financing List',
         N'قائمة الإرهاب المحلّية لساما (تمويل الإرهاب)',
         'https://placeholder.example/sama/local-terror.xlsx',
         'SAMA', 'WEEKLY', 150, 1, '2026-05-25 06:00:00');
END
GO

-- KSA National Watchlist
IF NOT EXISTS (SELECT 1 FROM aml.sanctions_list WHERE list_code = 'KSA-WATCHLIST' AND organisation_id IS NULL)
BEGIN
    INSERT INTO aml.sanctions_list
        (organisation_id, list_code, title_en, title_ar, source_url, source_type,
         refresh_frequency, entry_count, is_active, last_refreshed_at)
    VALUES
        (NULL, 'KSA-WATCHLIST',
         'KSA National Watchlist (Presidency of State Security)',
         N'قائمة المراقبة الوطنية السعودية (رئاسة أمن الدولة)',
         'https://placeholder.example/ksa/watchlist.pdf',
         'KSA_NATIONAL', 'ON_DEMAND', 80, 1, '2026-05-25 06:00:00');
END
GO

-- ─── Pre-seed: 12 demo list entries (all [DEMO]-prefixed training data) ──

-- UN-CONSOLIDATED entries
DECLARE @un_id BIGINT = (SELECT id FROM aml.sanctions_list WHERE list_code = 'UN-CONSOLIDATED' AND organisation_id IS NULL);

IF NOT EXISTS (SELECT 1 FROM aml.sanctions_list_entry WHERE list_id = @un_id AND entry_external_id = 'UN-DEMO-001')
BEGIN
    INSERT INTO aml.sanctions_list_entry
        (organisation_id, list_id, entry_external_id, entity_type,
         primary_name_en, primary_name_ar, aliases_json,
         date_of_birth, country, id_documents_json,
         listing_date, listed_by, listing_reason_en, listing_reason_ar, severity)
    VALUES
        (NULL, @un_id, 'UN-DEMO-001', 'INDIVIDUAL',
         '[DEMO] Abdul Rahman Al-Hakim',
         N'[تجريبي] عبدالرحمن الحكيم',
         '["Abu Rahman","عبد الرحمن","A.R. Al-Hakim"]',
         '1972-04-15', 'SY',
         '[{"type":"passport","country":"SY","number":"DEMO-12345"}]',
         '2014-08-15', 'UN Security Council Resolution 1267 Committee',
         'Listed for association with designated terrorist organisation (DEMO)',
         N'مُدرَج لارتباطه بتنظيم إرهابي مُعيَّن (تجريبي)',
         'STRONG_MATCH_REQUIRED'),

        (NULL, @un_id, 'UN-DEMO-002', 'INDIVIDUAL',
         '[DEMO] Mohammed Saeed Al-Khalifa',
         N'[تجريبي] محمد سعيد الخليفة',
         '["M.S. Al-Khalifa","محمد الخليفة","Saeed Mohammed"]',
         '1968-09-22', 'IQ',
         NULL,
         '2015-03-10', 'UN Security Council Resolution 1267 Committee',
         'Financial facilitator for designated organisation (DEMO)',
         N'مُيسِّر مالي لتنظيم مُعيَّن (تجريبي)',
         'STRONG_MATCH_REQUIRED'),

        (NULL, @un_id, 'UN-DEMO-003', 'ENTITY',
         '[DEMO] Northern Trading Co. Ltd',
         N'[تجريبي] شركة الشمال للتجارة المحدودة',
         '["Northern Trade Ltd","شركة الشمال"]',
         NULL, 'AE',
         '[{"type":"trade_license","country":"AE","number":"DEMO-LIC-9988"}]',
         '2016-06-20', 'UN Security Council Resolution 1718 Committee',
         'Sanctions evasion via trade-based money laundering (DEMO)',
         N'تهرّب من العقوبات عبر غسل الأموال القائم على التجارة (تجريبي)',
         'STRONG_MATCH_REQUIRED'),

        (NULL, @un_id, 'UN-DEMO-004', 'VESSEL',
         '[DEMO] MV Northern Star',
         N'[تجريبي] السفينة نجمة الشمال',
         '["Northern Star","نجمة الشمال"]',
         NULL, NULL,
         '[{"type":"imo","number":"DEMO-IMO-1234567"}]',
         '2018-11-05', 'UN Security Council Resolution 2231 Committee',
         'Used in sanctions-evasion oil-cargo transfers (DEMO)',
         N'استُخدمت في عمليات نقل النفط للتحايل على العقوبات (تجريبي)',
         'REVIEW_REQUIRED');
END
GO

-- OFAC-SDN entries
DECLARE @ofac_id BIGINT = (SELECT id FROM aml.sanctions_list WHERE list_code = 'OFAC-SDN' AND organisation_id IS NULL);

IF NOT EXISTS (SELECT 1 FROM aml.sanctions_list_entry WHERE list_id = @ofac_id AND entry_external_id = 'OFAC-DEMO-101')
BEGIN
    INSERT INTO aml.sanctions_list_entry
        (organisation_id, list_id, entry_external_id, entity_type,
         primary_name_en, primary_name_ar, aliases_json,
         date_of_birth, country, id_documents_json,
         listing_date, listed_by, listing_reason_en, listing_reason_ar, severity)
    VALUES
        (NULL, @ofac_id, 'OFAC-DEMO-101', 'INDIVIDUAL',
         '[DEMO] Ali Hassan Mahmoud',
         N'[تجريبي] علي حسن محمود',
         '["A.H. Mahmoud","علي محمود","Hassan Ali"]',
         '1980-01-12', 'IR',
         '[{"type":"passport","country":"IR","number":"DEMO-OFAC-77"}]',
         '2019-04-22', 'US Treasury OFAC',
         'SDGT — Specially Designated Global Terrorist (DEMO)',
         N'إرهابي عالمي مُعيَّن بصفة خاصّة (تجريبي)',
         'STRONG_MATCH_REQUIRED'),

        (NULL, @ofac_id, 'OFAC-DEMO-102', 'ENTITY',
         '[DEMO] Sahel Petroleum Trading FZE',
         N'[تجريبي] الساحل لتجارة النفط',
         '["Sahel Oil","الساحل للنفط"]',
         NULL, 'AE',
         NULL,
         '2020-08-14', 'US Treasury OFAC',
         'Sanctions-evasion oil sales for designated regime (DEMO)',
         N'مبيعات نفط للتحايل على العقوبات لصالح نظام مُعيَّن (تجريبي)',
         'STRONG_MATCH_REQUIRED'),

        (NULL, @ofac_id, 'OFAC-DEMO-103', 'INDIVIDUAL',
         '[DEMO] Karim Al-Zayed',
         N'[تجريبي] كريم الزايد',
         '["K. Al-Zayed","الزايد"]',
         '1975-07-30', 'LB',
         NULL,
         '2021-02-09', 'US Treasury OFAC',
         'SDNT — drug-trafficking organisation (DEMO)',
         N'منظمة الاتجار بالمخدرات (تجريبي)',
         'REVIEW_REQUIRED'),

        (NULL, @ofac_id, 'OFAC-DEMO-104', 'INDIVIDUAL',
         '[DEMO] Yusuf Ibrahim',
         N'[تجريبي] يوسف إبراهيم',
         '["Y. Ibrahim","يوسف"]',
         '1985-11-18', 'YE',
         NULL,
         '2022-05-17', 'US Treasury OFAC',
         'SDGT — terrorist financing intermediary (DEMO)',
         N'وسيط في تمويل الإرهاب (تجريبي)',
         'REVIEW_REQUIRED');
END
GO

-- SAMA-LOCAL-TERROR entries
DECLARE @sama_id BIGINT = (SELECT id FROM aml.sanctions_list WHERE list_code = 'SAMA-LOCAL-TERROR' AND organisation_id IS NULL);

IF NOT EXISTS (SELECT 1 FROM aml.sanctions_list_entry WHERE list_id = @sama_id AND entry_external_id = 'SAMA-DEMO-201')
BEGIN
    INSERT INTO aml.sanctions_list_entry
        (organisation_id, list_id, entry_external_id, entity_type,
         primary_name_en, primary_name_ar, aliases_json,
         date_of_birth, country, id_documents_json,
         listing_date, listed_by, listing_reason_en, listing_reason_ar, severity)
    VALUES
        (NULL, @sama_id, 'SAMA-DEMO-201', 'INDIVIDUAL',
         '[DEMO] Saud Al-Qahtani',
         N'[تجريبي] سعود القحطاني',
         '["S. Al-Qahtani","القحطاني"]',
         '1979-03-25', 'SA',
         '[{"type":"national_id","country":"SA","number":"DEMO-1010101010"}]',
         '2020-01-15', 'SAMA / Presidency of State Security',
         'Designated under KSA Anti-Terrorism Law Article 33 (DEMO)',
         N'مُعيَّن وفق نظام مكافحة الإرهاب السعودي المادة 33 (تجريبي)',
         'STRONG_MATCH_REQUIRED'),

        (NULL, @sama_id, 'SAMA-DEMO-202', 'INDIVIDUAL',
         '[DEMO] Fahad Al-Otaibi',
         N'[تجريبي] فهد العتيبي',
         '["F. Al-Otaibi","العتيبي"]',
         '1983-12-08', 'SA',
         '[{"type":"national_id","country":"SA","number":"DEMO-2020202020"}]',
         '2021-09-12', 'SAMA / Presidency of State Security',
         'Financing of designated organisation, KSA AT Law (DEMO)',
         N'تمويل تنظيم مُعيَّن وفق نظام مكافحة الإرهاب (تجريبي)',
         'STRONG_MATCH_REQUIRED'),

        (NULL, @sama_id, 'SAMA-DEMO-203', 'ENTITY',
         '[DEMO] Jeddah Charitable Foundation (Unlicensed)',
         N'[تجريبي] مؤسسة جدة الخيرية (غير مُرخَّصة)',
         '["JCF","جدة الخيرية"]',
         NULL, 'SA',
         '[{"type":"commercial_register","country":"SA","number":"DEMO-CR-3030"}]',
         '2022-04-20', 'SAMA / Presidency of State Security',
         'Unlicensed charity diverting funds to designated org (DEMO)',
         N'جمعية خيرية غير مُرخَّصة تحوّل أموالاً لتنظيم مُعيَّن (تجريبي)',
         'REVIEW_REQUIRED'),

        (NULL, @sama_id, 'SAMA-DEMO-204', 'INDIVIDUAL',
         '[DEMO] Bandar Al-Shammari',
         N'[تجريبي] بندر الشمري',
         '["B. Al-Shammari","الشمري"]',
         '1990-06-14', 'SA',
         NULL,
         '2023-11-30', 'SAMA / Presidency of State Security',
         'Recruiter for designated organisation (DEMO)',
         N'مُجنِّد لتنظيم مُعيَّن (تجريبي)',
         'REVIEW_REQUIRED');
END
GO

-- ─── Pre-seed: 8 screening checks for org=1 ────────────────────────
--
-- Mix: 5 NO_HIT (customer onboardings, clean), 2 POTENTIAL_HIT (under review),
-- 1 CONFIRMED_HIT (escalated with CAPA link).

-- 5 NO_HIT checks
IF NOT EXISTS (SELECT 1 FROM aml.screening_check WHERE organisation_id = 1 AND check_code = 'SCR-2026-000001')
BEGIN
    INSERT INTO aml.screening_check
        (organisation_id, check_code, subject_type, subject_reference,
         subject_name_en, subject_name_ar, subject_dob, subject_country, subject_id_document,
         check_at, check_source, lists_screened_codes, result)
    VALUES
        (1, 'SCR-2026-000001', 'CUSTOMER_ONBOARDING', 'CUST-100001',
         'Ahmed Al-Saud', N'أحمد آل سعود',
         '1985-05-10', 'SA', 'NID-1234567890',
         '2026-05-20 09:15:00', 'REAL_TIME_API', 'OFAC-SDN,UN-CONSOLIDATED,EU-CONSOLIDATED,SAMA-LOCAL-TERROR,KSA-WATCHLIST', 'NO_HIT'),

        (1, 'SCR-2026-000002', 'CUSTOMER_ONBOARDING', 'CUST-100002',
         'Salem Al-Ghamdi', N'سالم الغامدي',
         '1978-11-22', 'SA', 'NID-2345678901',
         '2026-05-20 10:30:00', 'REAL_TIME_API', 'OFAC-SDN,UN-CONSOLIDATED,EU-CONSOLIDATED,SAMA-LOCAL-TERROR,KSA-WATCHLIST', 'NO_HIT'),

        (1, 'SCR-2026-000003', 'CUSTOMER_ONBOARDING', 'CUST-100003',
         'Noura Al-Dosari', N'نورة الدوسري',
         '1992-02-14', 'SA', 'NID-3456789012',
         '2026-05-21 08:45:00', 'REAL_TIME_API', 'OFAC-SDN,UN-CONSOLIDATED,EU-CONSOLIDATED,SAMA-LOCAL-TERROR,KSA-WATCHLIST', 'NO_HIT'),

        (1, 'SCR-2026-000004', 'VENDOR_KYC', 'VEN-50012',
         'Riyadh Logistics LLC', N'الرياض للخدمات اللوجستية المحدودة',
         NULL, 'SA', 'CR-7050099876',
         '2026-05-22 14:20:00', 'MANUAL', 'OFAC-SDN,UN-CONSOLIDATED,EU-CONSOLIDATED,SAMA-LOCAL-TERROR', 'NO_HIT'),

        (1, 'SCR-2026-000005', 'BENEFICIAL_OWNER', 'BO-9988',
         'Khalid Al-Harbi', N'خالد الحربي',
         '1980-08-30', 'SA', 'NID-4567890123',
         '2026-05-23 11:00:00', 'MANUAL', 'OFAC-SDN,UN-CONSOLIDATED,SAMA-LOCAL-TERROR,KSA-WATCHLIST', 'NO_HIT');
END
GO

-- 2 POTENTIAL_HIT checks + 1 CONFIRMED_HIT check
IF NOT EXISTS (SELECT 1 FROM aml.screening_check WHERE organisation_id = 1 AND check_code = 'SCR-2026-000006')
BEGIN
    INSERT INTO aml.screening_check
        (organisation_id, check_code, subject_type, subject_reference,
         subject_name_en, subject_name_ar, subject_dob, subject_country, subject_id_document,
         check_at, check_source, lists_screened_codes, result)
    VALUES
        (1, 'SCR-2026-000006', 'CUSTOMER_ONBOARDING', 'CUST-100099',
         'Ali Hassan Mahmoud', N'علي حسن محمود',
         '1981-03-05', 'JO', 'PP-JO-998877',
         '2026-05-24 16:45:00', 'REAL_TIME_API', 'OFAC-SDN,UN-CONSOLIDATED,EU-CONSOLIDATED,SAMA-LOCAL-TERROR,KSA-WATCHLIST', 'POTENTIAL_HIT'),

        (1, 'SCR-2026-000007', 'TRANSACTION_COUNTERPARTY', 'TXN-7788001',
         'Northern Star Shipping', N'سفن نجمة الشمال',
         NULL, 'AE', 'IMO-7654321',
         '2026-05-25 09:00:00', 'REAL_TIME_API', 'OFAC-SDN,UN-CONSOLIDATED,EU-CONSOLIDATED', 'POTENTIAL_HIT'),

        (1, 'SCR-2026-000008', 'CUSTOMER_ONBOARDING', 'CUST-100123',
         'Saud Al-Qahtani', N'سعود القحطاني',
         '1979-03-25', 'SA', 'NID-1010101010',
         '2026-05-25 10:30:00', 'MANUAL', 'SAMA-LOCAL-TERROR,KSA-WATCHLIST', 'CONFIRMED_HIT');
END
GO

-- ─── Pre-seed: 3 hits ──────────────────────────────────────────────

-- Hit 1: SCR-2026-000006 (Ali Hassan Mahmoud) → OFAC-DEMO-101 (POTENTIAL, PENDING_REVIEW)
DECLARE @chk6 BIGINT = (SELECT id FROM aml.screening_check WHERE organisation_id = 1 AND check_code = 'SCR-2026-000006');
DECLARE @ofac_id2 BIGINT = (SELECT id FROM aml.sanctions_list WHERE list_code = 'OFAC-SDN' AND organisation_id IS NULL);
DECLARE @entry_ofac101 BIGINT = (SELECT id FROM aml.sanctions_list_entry WHERE list_id = @ofac_id2 AND entry_external_id = 'OFAC-DEMO-101');

IF @chk6 IS NOT NULL AND @entry_ofac101 IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM aml.screening_hit WHERE check_id = @chk6 AND list_entry_id = @entry_ofac101)
BEGIN
    INSERT INTO aml.screening_hit
        (organisation_id, check_id, list_entry_id, match_score, match_type, status)
    VALUES
        (1, @chk6, @entry_ofac101, 92.00, 'FUZZY_NAME', 'PENDING_REVIEW');
END
GO

-- Hit 2: SCR-2026-000007 (Northern Star Shipping) → UN-DEMO-004 (MV Northern Star)
DECLARE @chk7 BIGINT = (SELECT id FROM aml.screening_check WHERE organisation_id = 1 AND check_code = 'SCR-2026-000007');
DECLARE @un_id2 BIGINT = (SELECT id FROM aml.sanctions_list WHERE list_code = 'UN-CONSOLIDATED' AND organisation_id IS NULL);
DECLARE @entry_un4 BIGINT = (SELECT id FROM aml.sanctions_list_entry WHERE list_id = @un_id2 AND entry_external_id = 'UN-DEMO-004');

IF @chk7 IS NOT NULL AND @entry_un4 IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM aml.screening_hit WHERE check_id = @chk7 AND list_entry_id = @entry_un4)
BEGIN
    INSERT INTO aml.screening_hit
        (organisation_id, check_id, list_entry_id, match_score, match_type, status)
    VALUES
        (1, @chk7, @entry_un4, 85.00, 'FUZZY_NAME', 'PENDING_REVIEW');
END
GO

-- Hit 3: SCR-2026-000008 (Saud Al-Qahtani) → SAMA-DEMO-201 — CONFIRMED, ESCALATED with CAPA
DECLARE @chk8 BIGINT = (SELECT id FROM aml.screening_check WHERE organisation_id = 1 AND check_code = 'SCR-2026-000008');
DECLARE @sama_id2 BIGINT = (SELECT id FROM aml.sanctions_list WHERE list_code = 'SAMA-LOCAL-TERROR' AND organisation_id IS NULL);
DECLARE @entry_sama201 BIGINT = (SELECT id FROM aml.sanctions_list_entry WHERE list_id = @sama_id2 AND entry_external_id = 'SAMA-DEMO-201');

IF @chk8 IS NOT NULL AND @entry_sama201 IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM aml.screening_hit WHERE check_id = @chk8 AND list_entry_id = @entry_sama201)
BEGIN
    INSERT INTO aml.screening_hit
        (organisation_id, check_id, list_entry_id, match_score, match_type, status,
         reviewer_user_id, reviewed_at, disposition_en, disposition_ar,
         escalated_to_user_id, escalated_at)
    VALUES
        (1, @chk8, @entry_sama201, 100.00, 'COMPOSITE', 'TRUE_POSITIVE',
         42, '2026-05-25 10:45:00',
         'Exact name + DOB + national ID match against SAMA-LOCAL-TERROR. Customer onboarding rejected; SAFIU notification filed; account application blocked.',
         N'تطابق تامّ للاسم وتاريخ الميلاد ورقم الهوية الوطنية مع قائمة الإرهاب المحلّية لساما. تمّ رفض استقبال العميل وإبلاغ إدارة التحريات المالية وحجب طلب الحساب.',
         99, '2026-05-25 10:48:00');
END
GO

-- Update entry_count on the seeded lists so the dashboard reflects reality
UPDATE aml.sanctions_list SET entry_count =
    (SELECT COUNT(*) FROM aml.sanctions_list_entry e WHERE e.list_id = aml.sanctions_list.id AND e.is_deleted = 0)
    + CASE list_code
        -- The platform-global list counts that prompt seeds; the demo entries
        -- listed here are a tiny subset, so we preserve the realistic figures
        -- as seeded above instead of overwriting them with the count(*).
        WHEN 'OFAC-SDN'         THEN 16000 - (SELECT COUNT(*) FROM aml.sanctions_list_entry WHERE list_id = aml.sanctions_list.id AND is_deleted = 0)
        WHEN 'UN-CONSOLIDATED'  THEN  700 - (SELECT COUNT(*) FROM aml.sanctions_list_entry WHERE list_id = aml.sanctions_list.id AND is_deleted = 0)
        WHEN 'EU-CONSOLIDATED'  THEN 5000 - (SELECT COUNT(*) FROM aml.sanctions_list_entry WHERE list_id = aml.sanctions_list.id AND is_deleted = 0)
        WHEN 'SAMA-LOCAL-TERROR' THEN  150 - (SELECT COUNT(*) FROM aml.sanctions_list_entry WHERE list_id = aml.sanctions_list.id AND is_deleted = 0)
        WHEN 'KSA-WATCHLIST'    THEN   80 - (SELECT COUNT(*) FROM aml.sanctions_list_entry WHERE list_id = aml.sanctions_list.id AND is_deleted = 0)
        ELSE 0
      END
WHERE organisation_id IS NULL AND list_code IN ('OFAC-SDN','UN-CONSOLIDATED','EU-CONSOLIDATED','SAMA-LOCAL-TERROR','KSA-WATCHLIST');
GO
