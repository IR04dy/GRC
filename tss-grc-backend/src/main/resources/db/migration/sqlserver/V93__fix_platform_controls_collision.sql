-- V93: Repair the platform.controls schema collision between V1 and V92.
--
-- V1 created platform.controls as an early sprint-0 scaffold (4 cols,
-- no JPA entity behind it). V92 introduced the unified-library schema
-- under the same table name; its IF NOT EXISTS guard meant the proper
-- schema was silently skipped on databases where V1 had already run,
-- and the V92 seed INSERTs failed against the wrong columns.
--
-- This migration detects the V1 stub (marked by the `framework_id`
-- column, which the V92 schema does NOT have), drops it, re-creates
-- the table with the V92 schema, and re-runs the V92 seed. All seed
-- inserts are guarded by NOT EXISTS so this is safe to apply on any
-- starting state.
--
-- After V93, platform.controls is guaranteed to have the V92 unified
-- library schema. F3+F4 (V94) then copies legacy module controls
-- into it.

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('platform.controls')
      AND name = 'framework_id'
)
BEGIN
    PRINT 'V93: dropping V1 stub platform.controls (has framework_id column)';
    -- V92's control_mappings.control_id has an FK to platform.controls.
    -- Drop the FK constraint first so we can drop the stub table.
    DECLARE @fk NVARCHAR(200);
    SELECT @fk = name FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID('platform.control_mappings')
      AND referenced_object_id = OBJECT_ID('platform.controls');
    IF @fk IS NOT NULL EXEC('ALTER TABLE platform.control_mappings DROP CONSTRAINT ' + @fk);

    DECLARE @fk2 NVARCHAR(200);
    SELECT @fk2 = name FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID('platform.control_assessments')
      AND referenced_object_id = OBJECT_ID('platform.controls');
    IF @fk2 IS NOT NULL EXEC('ALTER TABLE platform.control_assessments DROP CONSTRAINT ' + @fk2);

    DECLARE @fk3 NVARCHAR(200);
    SELECT @fk3 = name FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID('platform.risk_control_links')
      AND referenced_object_id = OBJECT_ID('platform.controls');
    IF @fk3 IS NOT NULL EXEC('ALTER TABLE platform.risk_control_links DROP CONSTRAINT ' + @fk3);

    DROP TABLE platform.controls;
END
GO

-- Re-create with V92 unified library schema.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'controls' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    PRINT 'V93: creating platform.controls with unified library schema';
    CREATE TABLE platform.controls (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id    BIGINT          NOT NULL,
        external_id        UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        control_code       NVARCHAR(60)    NOT NULL,
        version            NVARCHAR(20)    NOT NULL DEFAULT '1.0',
        domain             NVARCHAR(40)    NOT NULL,
        objective_id       BIGINT          NULL,

        title_en           NVARCHAR(255)   NOT NULL,
        title_ar           NVARCHAR(255)   NULL,
        description_en     NVARCHAR(MAX)   NULL,
        description_ar     NVARCHAR(MAX)   NULL,

        control_type       NVARCHAR(20)    NOT NULL DEFAULT 'PREVENTIVE',
        automation_level   NVARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
        frequency          NVARCHAR(20)    NULL,
        owner_user_id      BIGINT          NULL,
        status             NVARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',

        legacy_source_module NVARCHAR(40)  NULL,
        legacy_id            BIGINT        NULL,

        is_deleted         BIT             NOT NULL DEFAULT 0,
        created_at         DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by         NVARCHAR(100)   NULL,
        updated_at         DATETIME2(0)    NULL,
        updated_by         NVARCHAR(100)   NULL,

        CONSTRAINT uq_control_org_code_version UNIQUE (organisation_id, control_code, version),
        CONSTRAINT ck_control_type   CHECK (control_type IN ('PREVENTIVE','DETECTIVE','CORRECTIVE','DETERRENT','RECOVERY')),
        CONSTRAINT ck_control_auto   CHECK (automation_level IN ('MANUAL','SEMI','AUTOMATED')),
        CONSTRAINT ck_control_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
        CONSTRAINT fk_control_objective FOREIGN KEY (objective_id) REFERENCES platform.control_objectives(id)
    );
    CREATE INDEX ix_control_org_domain   ON platform.controls(organisation_id, domain);
    CREATE INDEX ix_control_org_status   ON platform.controls(organisation_id, status);
    CREATE INDEX ix_control_objective    ON platform.controls(objective_id);
    CREATE INDEX ix_control_legacy       ON platform.controls(legacy_source_module, legacy_id);
END
GO

-- Restore the FKs that V93 may have dropped above (idempotent — guards
-- against double-creation).
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('platform.control_mappings')
      AND referenced_object_id = OBJECT_ID('platform.controls'))
BEGIN
    ALTER TABLE platform.control_mappings
    ADD CONSTRAINT fk_mapping_control FOREIGN KEY (control_id) REFERENCES platform.controls(id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('platform.control_assessments')
      AND referenced_object_id = OBJECT_ID('platform.controls'))
BEGIN
    ALTER TABLE platform.control_assessments
    ADD CONSTRAINT fk_ca_control FOREIGN KEY (control_id) REFERENCES platform.controls(id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('platform.risk_control_links')
      AND referenced_object_id = OBJECT_ID('platform.controls'))
BEGIN
    ALTER TABLE platform.risk_control_links
    ADD CONSTRAINT fk_rcl_control FOREIGN KEY (control_id) REFERENCES platform.controls(id);
END
GO

-- Re-run V92's control seed (NOT EXISTS guards make this idempotent).
DECLARE @objCyberGov BIGINT = (SELECT id FROM platform.control_objectives
                                WHERE organisation_id = 1 AND objective_code = 'OBJ-CYBER-GOV');

IF @objCyberGov IS NOT NULL AND NOT EXISTS
   (SELECT 1 FROM platform.controls WHERE organisation_id = 1 AND control_code = 'C-CYBER-001')
BEGIN
    INSERT INTO platform.controls
      (organisation_id, control_code, domain, objective_id, title_en, title_ar, description_en, description_ar,
       control_type, automation_level, frequency, status)
    VALUES
      (1, 'C-CYBER-001', 'CYBER', @objCyberGov,
       'Cybersecurity Strategy approved annually',
       N'اعتماد استراتيجية الأمن السيبراني سنوياً',
       'Documented cybersecurity strategy signed off by executive leadership each year.',
       N'استراتيجية أمن سيبراني موثّقة معتمدة من الإدارة العليا كل سنة.',
       'PREVENTIVE','MANUAL','ANNUAL','ACTIVE'),
      (1, 'C-CYBER-002', 'CYBER', @objCyberGov,
       'Cybersecurity policy library reviewed annually',
       N'مراجعة مكتبة سياسات الأمن السيبراني سنوياً',
       'Policy library covering the ECC 1-2 control families, reviewed on an annual cadence.',
       N'مكتبة السياسات التي تغطي عائلات ضوابط ECC 1-2 وتراجَع سنوياً.',
       'PREVENTIVE','MANUAL','ANNUAL','ACTIVE'),
      (1, 'C-IAM-014', 'CYBER', NULL,
       'Privileged access reviewed quarterly',
       N'مراجعة الصلاحيات المميّزة فصلياً',
       'Privileged accounts inventory reconciled with HR roster every quarter; deviations raise a CAPA.',
       N'مطابقة قائمة الحسابات المميّزة مع كشف الموارد البشرية كل ثلاثة أشهر؛ أي انحراف يفتح إجراء تصحيحي.',
       'DETECTIVE','SEMI','QUARTERLY','ACTIVE'),
      (1, 'C-ASSET-001', 'CYBER', NULL,
       'Asset inventory completeness',
       N'اكتمال جرد الأصول',
       'CMDB completeness ≥ 95% for production systems; weekly automated reconciliation.',
       N'اكتمال قاعدة الأصول لا يقل عن 95% للأنظمة الإنتاجية مع مطابقة آلية أسبوعية.',
       'DETECTIVE','AUTOMATED','WEEKLY','ACTIVE'),
      (1, 'C-BCM-003', 'BCM', NULL,
       'Critical service BIA refreshed annually',
       N'تحديث تحليل أثر الأعمال للخدمات الحساسة سنوياً',
       'BIA for every critical service refreshed and re-approved annually.',
       N'تحديث BIA لكل خدمة حساسة واعتماده سنوياً.',
       'PREVENTIVE','MANUAL','ANNUAL','ACTIVE'),
      (1, 'C-BCM-005', 'BCM', NULL,
       'Annual DR exercise with documented results',
       N'تمرين تعافٍ سنوي بنتائج موثّقة',
       'Annual disaster-recovery exercise; results documented and lessons-learned actions raised.',
       N'تمرين تعافٍ سنوي وتوثيق النتائج وفتح إجراءات الدروس المستفادة.',
       'CORRECTIVE','MANUAL','ANNUAL','ACTIVE'),
      (1, 'C-PRIV-002', 'PRIVACY', NULL,
       'PIA required for high-risk processing',
       N'إجراء تقييم أثر الخصوصية للمعالجات عالية الخطورة',
       'Privacy Impact Assessment completed before any high-risk personal-data processing goes live.',
       N'إنجاز تقييم أثر الخصوصية قبل تشغيل أي معالجة عالية الخطورة للبيانات الشخصية.',
       'PREVENTIVE','MANUAL','ON_EVENT','ACTIVE'),
      (1, 'C-TPRM-004', 'TPRM', NULL,
       'Vendor cyber due-diligence questionnaire before onboarding',
       N'استبيان الحرص السيبراني قبل اعتماد المورّد',
       'Vendor cybersecurity questionnaire completed and scored before contract sign-off.',
       N'إكمال استبيان الأمن السيبراني للمورّد وتقييمه قبل توقيع العقد.',
       'PREVENTIVE','SEMI','ON_EVENT','ACTIVE');
END
GO

-- Re-run V92's mappings seed against the re-created controls.
DECLARE @ecc BIGINT = (SELECT id FROM platform.authority_documents
                       WHERE organisation_id = 1 AND code = 'NCA-ECC-V2');
DECLARE @cit11 BIGINT = (SELECT id FROM platform.authority_document_citations
                         WHERE authority_document_id = @ecc AND citation_code = '1-1');
DECLARE @cit12 BIGINT = (SELECT id FROM platform.authority_document_citations
                         WHERE authority_document_id = @ecc AND citation_code = '1-2');
DECLARE @cit22 BIGINT = (SELECT id FROM platform.authority_document_citations
                         WHERE authority_document_id = @ecc AND citation_code = '2-2');
DECLARE @cit21 BIGINT = (SELECT id FROM platform.authority_document_citations
                         WHERE authority_document_id = @ecc AND citation_code = '2-1');
DECLARE @cit4 BIGINT  = (SELECT id FROM platform.authority_document_citations
                         WHERE authority_document_id = @ecc AND citation_code = '4');

DECLARE @cCyber1 BIGINT = (SELECT id FROM platform.controls WHERE organisation_id = 1 AND control_code = 'C-CYBER-001');
DECLARE @cCyber2 BIGINT = (SELECT id FROM platform.controls WHERE organisation_id = 1 AND control_code = 'C-CYBER-002');
DECLARE @cIam    BIGINT = (SELECT id FROM platform.controls WHERE organisation_id = 1 AND control_code = 'C-IAM-014');
DECLARE @cAsset  BIGINT = (SELECT id FROM platform.controls WHERE organisation_id = 1 AND control_code = 'C-ASSET-001');
DECLARE @cTprm   BIGINT = (SELECT id FROM platform.controls WHERE organisation_id = 1 AND control_code = 'C-TPRM-004');

IF @cit11 IS NOT NULL AND @cCyber1 IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM platform.control_mappings WHERE control_id = @cCyber1 AND citation_id = @cit11)
BEGIN
    INSERT INTO platform.control_mappings (organisation_id, control_id, citation_id, coverage, rationale_en)
    VALUES
      (1, @cCyber1, @cit11, 'FULL',
        'Annual strategy approval directly satisfies ECC 1-1 Cybersecurity Strategy requirement.'),
      (1, @cCyber2, @cit12, 'FULL',
        'Policy library covers ECC 1-2 Cybersecurity Policies and Procedures.'),
      (1, @cIam,    @cit22, 'FULL',
        'Quarterly privileged-access review satisfies ECC 2-2 Identity and Access Management.'),
      (1, @cAsset,  @cit21, 'PARTIAL',
        'CMDB completeness covers ECC 2-1 Asset Management partially; physical-asset reconciliation handled in facilities programme.'),
      (1, @cTprm,   @cit4,  'FULL',
        'Vendor cyber DD questionnaire satisfies ECC 4 Third-Party Cybersecurity.');
END
GO
