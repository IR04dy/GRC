-- V94: One-way migration of legacy per-module controls into the
-- unified library — Framework Intersection plan slices F3 + F4.
--
-- Two sources today:
--   cyber.cyber_controls       → domain='CYBER',      legacy_source_module='cyber'
--   compliance.controls        → domain='COMPLIANCE', legacy_source_module='compliance'
--
-- NDI does NOT have a "controls" table — its compliance_assessments
-- are results against the regulator's framework_items, not platform
-- controls — so there's nothing to copy from there. Future slices may
-- promote NDI framework_items into authority_document_citations
-- instead (which is the right home for them).
--
-- Idempotent: each insert is guarded by NOT EXISTS on the
-- (organisation_id, legacy_source_module, legacy_id) tuple. Re-running
-- this migration is a no-op.
--
-- This is a one-way SEED — legacy modules continue reading from their
-- own tables. The unified library now contains copies, which gives
-- the /controls/library page and the Compliance Posture Hub (F6) the
-- complete catalog they need. A future slice can retire the legacy
-- reads module-by-module.

-- ─── Cyber → unified library ──────────────────────────────────────
IF OBJECT_ID('cyber.cyber_controls','U') IS NOT NULL
BEGIN
    INSERT INTO platform.controls
        (organisation_id, control_code, version, domain, title_en, title_ar,
         description_en, control_type, automation_level, frequency, status,
         legacy_source_module, legacy_id)
    SELECT
        cc.organisation_id,
        -- Use control_number when present, otherwise fall back to a
        -- generated code so the unique constraint never collides.
        'CYBER-' + ISNULL(NULLIF(LTRIM(RTRIM(cc.control_number)), ''),
                          'LEGACY-' + CAST(cc.id AS NVARCHAR(20))),
        '1.0',
        'CYBER',
        ISNULL(NULLIF(LTRIM(RTRIM(cc.title_en)), ''), 'Untitled cyber control'),
        cc.title_ar,
        cc.procedure_en,
        -- Map cyber.control_type → platform enum. 'PREVENTIVE' is the
        -- documented default; unknown values fall back to the default
        -- so the CHECK constraint never fires.
        CASE UPPER(LTRIM(RTRIM(ISNULL(cc.control_type, ''))))
            WHEN 'PREVENTIVE'  THEN 'PREVENTIVE'
            WHEN 'DETECTIVE'   THEN 'DETECTIVE'
            WHEN 'CORRECTIVE'  THEN 'CORRECTIVE'
            WHEN 'DETERRENT'   THEN 'DETERRENT'
            WHEN 'RECOVERY'    THEN 'RECOVERY'
            ELSE 'PREVENTIVE'
        END,
        'MANUAL',
        -- Map review_frequency → platform frequency. Anything we don't
        -- recognise stays NULL (it's optional in the unified schema).
        CASE UPPER(LTRIM(RTRIM(ISNULL(cc.review_frequency, ''))))
            WHEN 'CONTINUOUS' THEN 'CONTINUOUS'
            WHEN 'DAILY'      THEN 'DAILY'
            WHEN 'WEEKLY'     THEN 'WEEKLY'
            WHEN 'MONTHLY'    THEN 'MONTHLY'
            WHEN 'QUARTERLY'  THEN 'QUARTERLY'
            WHEN 'ANNUAL'     THEN 'ANNUAL'
            WHEN 'ANNUALLY'   THEN 'ANNUAL'
            WHEN 'ON_EVENT'   THEN 'ON_EVENT'
            ELSE NULL
        END,
        CASE UPPER(LTRIM(RTRIM(ISNULL(cc.status, ''))))
            WHEN 'ACTIVE'   THEN 'ACTIVE'
            WHEN 'RETIRED'  THEN 'RETIRED'
            WHEN 'DRAFT'    THEN 'DRAFT'
            ELSE 'ACTIVE'
        END,
        'cyber',
        CAST(cc.id AS BIGINT)
    FROM cyber.cyber_controls cc
    WHERE cc.is_deleted = 0
      AND NOT EXISTS (
        SELECT 1 FROM platform.controls pc
        WHERE pc.organisation_id = cc.organisation_id
          AND pc.legacy_source_module = 'cyber'
          AND pc.legacy_id = CAST(cc.id AS BIGINT)
      );
    PRINT 'V94: migrated cyber controls into platform.controls';
END
GO

-- ─── Compliance → unified library ─────────────────────────────────
IF OBJECT_ID('compliance.controls','U') IS NOT NULL
BEGIN
    INSERT INTO platform.controls
        (organisation_id, control_code, version, domain, title_en, title_ar,
         description_en, control_type, automation_level, status,
         legacy_source_module, legacy_id)
    SELECT
        comp.organisation_id,
        'COMP-' + ISNULL(NULLIF(LTRIM(RTRIM(comp.code)), ''),
                         'LEGACY-' + CAST(comp.id AS NVARCHAR(20))),
        '1.0',
        'COMPLIANCE',
        ISNULL(NULLIF(LTRIM(RTRIM(comp.name_en)), ''), 'Untitled compliance control'),
        comp.name_ar,
        comp.description,
        'PREVENTIVE',
        'MANUAL',
        -- compliance.controls.status enum is IMPLEMENTED/PARTIAL/PLANNED/NOT_APPLICABLE.
        -- These describe assessment outcome, not control lifecycle — every
        -- migrated row lands as ACTIVE in the unified library; assessment
        -- outcome belongs in platform.control_assessments instead.
        'ACTIVE',
        'compliance',
        comp.id
    FROM compliance.controls comp
    WHERE comp.is_deleted = 0
      AND NOT EXISTS (
        SELECT 1 FROM platform.controls pc
        WHERE pc.organisation_id = comp.organisation_id
          AND pc.legacy_source_module = 'compliance'
          AND pc.legacy_id = comp.id
      );
    PRINT 'V94: migrated compliance controls into platform.controls';
END
GO
