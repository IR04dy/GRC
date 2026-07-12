-- V295 (green-finance plan roadmap closure — §0 item 10 + decisions #4/#5):
--  a) ghg_emission_record.scope3_category — the GHG Protocol's 15 Scope-3 value-chain
--     categories (1 Purchased goods … 15 Investments); NULL for S1/S2 rows.
--  b) organisation_settings.carbon_offset_cap_pct — decision #4: the retired-offset
--     cap (% of disclosed gross reduction) becomes tenant-configurable (default 10).
--  c) REG-1 obligation — decision #5: SOCPA's IFRS S1/S2 phase dates are a moving
--     regulatory position; the obligation text now mandates verifying the current
--     dates against SOCPA's official announcements each reporting cycle instead of
--     baking a possibly-stale date into the platform.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sust.ghg_emission_record') AND name = 'scope3_category')
    ALTER TABLE sust.ghg_emission_record ADD scope3_category TINYINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_ghg_scope3_category')
    ALTER TABLE sust.ghg_emission_record WITH CHECK ADD CONSTRAINT ck_ghg_scope3_category
        CHECK (scope3_category IS NULL OR scope3_category BETWEEN 1 AND 15);

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('sust.organisation_settings') AND name = 'carbon_offset_cap_pct')
    ALTER TABLE sust.organisation_settings ADD carbon_offset_cap_pct DECIMAL(5,2) NOT NULL DEFAULT 10;

UPDATE compliance.obligation SET
    description_en = 'Prepare sustainability (S1) and climate (S2) disclosures per the ISSB standards as endorsed by SOCPA. '
        + 'SOCPA applies the standards on a PHASED timeline that it updates by official announcement — verify the current '
        + 'phase dates and entity scoping at socpa.org.sa before each reporting cycle (platform reminder: this obligation''s '
        + 'annual frequency exists for that verification). Readiness evidence: the IFRS S1/S2 checklist + assurance-lineage '
        + 'view in the Sustainability module.',
    description_ar = N'إعداد إفصاحات الاستدامة (S1) والمناخ (S2) وفق معايير ISSB كما اعتمدتها الهيئة السعودية للمراجعين والمحاسبين. '
        + N'تطبَّق المعايير وفق جدول مرحلي تحدّثه الهيئة بإعلانات رسمية — يجب التحقق من مواعيد المراحل ونطاق المنشآت من '
        + N'socpa.org.sa قبل كل دورة إفصاح (تكرار هذا الالتزام سنوي لهذا الغرض). أدلة الجاهزية: قائمة IFRS S1/S2 وشاشة '
        + N'تتبع الأدلة في وحدة الاستدامة.'
WHERE obligation_code = 'OBL-ESG-2026-001'
  AND CHARINDEX('verify the current', description_en) = 0;
