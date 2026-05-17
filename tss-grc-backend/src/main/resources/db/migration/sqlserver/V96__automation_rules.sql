-- V96: Automation Rules engine — Framework Intersection plan F8.
--
-- Saudi GRC prompt §14 lists 20 automation rules (when X happens
-- do Y). Today those rules live inside service code (e.g. NDI's
-- Rule 7 "completing all CAPAs auto-resolves the gap"). Moving them
-- into data lets QA / compliance officers see the rules without
-- reading Java, change them without a deployment, and audit them
-- through a single inspectable table.
--
-- Two tables:
--   automation_rules     — the rule definitions
--   automation_rule_runs — the fire history (one row per rule × event)
--
-- The rule engine itself ships in the F8 service slice.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'automation_rules' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.automation_rules (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT          NOT NULL,
        external_id       UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        rule_code         NVARCHAR(40)    NOT NULL,   -- 'AR-04', 'AR-12' (matches prompt §14 numbering)
        name_en           NVARCHAR(200)   NOT NULL,
        name_ar           NVARCHAR(200)   NULL,
        description_en    NVARCHAR(MAX)   NULL,
        description_ar    NVARCHAR(MAX)   NULL,

        trigger_event     NVARCHAR(80)    NOT NULL,
        -- e.g. 'INCIDENT_CREATED', 'EVIDENCE_EXPIRED',
        -- 'RISK_APPETITE_BREACHED', 'BIA_RTO_GAP', 'POLICY_UPDATED'

        -- Condition is a small JSON object. The engine evaluates only
        -- the equality + contains operators for now; richer DSL can
        -- come later. Example: {"severity":"CRITICAL"} or
        -- {"affects_personal_data":true}.
        condition_json    NVARCHAR(MAX)   NULL,

        -- Action is JSON describing what to do. Action types the
        -- engine understands today (see AutomationRuleEngine):
        --   { "type": "CREATE_RECORD", "entity": "DPIA", "from": "ProcessingActivity" }
        --   { "type": "NOTIFY",        "role":  "PRIVACY_MANAGER",  "template": "DSR_SLA_NEAR" }
        --   { "type": "ESCALATE",      "to_role": "RISK_COMMITTEE", "after_minutes": 1440 }
        --   { "type": "AUDIT",         "severity": "WARN", "message": "..." }
        --   { "type": "FLAG",          "entity_field": "needs_review", "to": true }
        action_json       NVARCHAR(MAX)   NOT NULL,

        priority          INT             NOT NULL DEFAULT 50,
        enabled           BIT             NOT NULL DEFAULT 1,
        audit_required    BIT             NOT NULL DEFAULT 1,
        source            NVARCHAR(40)    NOT NULL DEFAULT 'PROMPT_14',
        -- PROMPT_14 (the 20 rules from §14)
        -- CUSTOM    (org-defined)
        -- SYSTEM    (platform default)

        last_fired_at     DATETIME2(0)    NULL,
        fire_count        BIGINT          NOT NULL DEFAULT 0,

        is_deleted        BIT             NOT NULL DEFAULT 0,
        created_at        DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by        NVARCHAR(100)   NULL,
        updated_at        DATETIME2(0)    NULL,
        updated_by        NVARCHAR(100)   NULL,

        CONSTRAINT uq_rule_org_code UNIQUE (organisation_id, rule_code)
    );
    CREATE INDEX ix_rule_org_trigger ON platform.automation_rules(organisation_id, trigger_event);
    CREATE INDEX ix_rule_enabled     ON platform.automation_rules(organisation_id, enabled);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'automation_rule_runs' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.automation_rule_runs (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT          NOT NULL,
        rule_id           BIGINT          NOT NULL,

        fired_at          DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        trigger_event     NVARCHAR(80)    NOT NULL,
        entity_type       NVARCHAR(120)   NULL,
        entity_id         BIGINT          NULL,
        correlation_id    NVARCHAR(60)    NULL,

        outcome           NVARCHAR(20)    NOT NULL DEFAULT 'SUCCESS',
        -- SUCCESS | FAILED | SKIPPED (condition didn't match)
        error_message     NVARCHAR(MAX)   NULL,
        result_summary    NVARCHAR(MAX)   NULL,

        CONSTRAINT fk_rrun_rule FOREIGN KEY (rule_id) REFERENCES platform.automation_rules(id),
        CONSTRAINT ck_rrun_outcome CHECK (outcome IN ('SUCCESS','FAILED','SKIPPED'))
    );
    CREATE INDEX ix_rrun_org_time ON platform.automation_rule_runs(organisation_id, fired_at DESC);
    CREATE INDEX ix_rrun_rule     ON platform.automation_rule_runs(rule_id, fired_at DESC);
END
GO

-- ─── Seed the 20 rules from Saudi GRC prompt §14 ──────────────────
-- Each rule maps cleanly to a TRIGGER_EVENT the engine listens for.
-- All start enabled=1, audit_required=1, source='PROMPT_14'.

IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = 1 AND rule_code = 'AR-01')
BEGIN
    INSERT INTO platform.automation_rules
      (organisation_id, rule_code, name_en, name_ar, description_en,
       trigger_event, condition_json, action_json, priority, source)
    VALUES
      (1, 'AR-01',
       'New system with personal data → create PDPL ROPA + PIA',
       N'نظام جديد يعالج بيانات شخصية → فتح سجل المعالجة وتقييم أثر الخصوصية',
       'When a new system goes live and contains personal data, automatically open a PDPL processing activity (ROPA) and trigger a Privacy Impact Assessment.',
       'ASSET_CREATED',
       '{"contains_personal_data":true}',
       '{"type":"CREATE_RECORD","entity":"ProcessingActivity","then":{"type":"CREATE_RECORD","entity":"DPIA"}}',
       10, 'PROMPT_14'),

      (1, 'AR-02',
       'Critical system → create BIA + DRP + NCA critical system assessment',
       N'نظام حساس → فتح تحليل أثر الأعمال وخطة التعافي وتقييم NCA',
       'When an asset is classified critical, automatically create a BIA, a DRP record, and an NCA critical-systems assessment.',
       'ASSET_CLASSIFIED',
       '{"criticality":"CRITICAL"}',
       '{"type":"CREATE_RECORD","entity":"BIA","also":["DRP","NcaCriticalAssessment"]}',
       10, 'PROMPT_14'),

      (1, 'AR-03',
       'Vendor processes personal data → trigger processor due diligence',
       N'مورّد يعالج بيانات شخصية → بدء العناية الواجبة كمعالج',
       'When a vendor is flagged as processing personal data, automatically trigger the processor due-diligence workflow.',
       'VENDOR_CLASSIFIED',
       '{"processes_personal_data":true}',
       '{"type":"CREATE_RECORD","entity":"ProcessorAssessment"}',
       10, 'PROMPT_14'),

      (1, 'AR-04',
       'Cyber incident affecting personal data → 72-hour PDPL breach timer',
       N'حادث سيبراني يمسّ بيانات شخصية → تشغيل عدّاد 72 ساعة لإشعار PDPL',
       'When a cyber incident is flagged as affecting personal data, open a PDPL data-breach case and start the 72-hour notification timer.',
       'INCIDENT_CREATED',
       '{"affects_personal_data":true,"incident_type":"CYBER"}',
       '{"type":"CREATE_RECORD","entity":"DataBreach","then":{"type":"ESCALATE","to_role":"DPO","after_minutes":4320}}',
       5, 'PROMPT_14'),

      (1, 'AR-05',
       'Cloud service backs critical service → CCC + DRP + supplier resilience review',
       N'خدمة سحابية تدعم خدمة حساسة → تقييم CCC وخطة تعافٍ ومراجعة صمود المورّد',
       'When a cloud service is mapped to a critical business service, trigger NCA CCC assessment + DRP review + supplier resilience review.',
       'CLOUD_SERVICE_LINKED',
       '{"backs_critical_service":true}',
       '{"type":"CREATE_RECORD","entity":"CccAssessment","also":["DrpReview","SupplierResilienceReview"]}',
       10, 'PROMPT_14'),

      (1, 'AR-06',
       'Risk exceeds appetite → escalate to risk committee',
       N'خطر يتجاوز شهية المخاطر → تصعيد إلى لجنة المخاطر',
       'When a risk''s residual score exceeds the configured appetite, escalate it to the risk committee.',
       'RISK_APPETITE_BREACHED',
       NULL,
       '{"type":"ESCALATE","to_role":"RISK_COMMITTEE","after_minutes":0}',
       5, 'PROMPT_14'),

      (1, 'AR-07',
       'BIA RTO < current recovery capability → gap + treatment plan',
       N'هدف زمن التعافي أقل من القدرة الحالية → فتح فجوة وخطة معالجة',
       'When a BIA''s required RTO is shorter than the current recovery capability, create a gap and a treatment plan.',
       'BIA_UPDATED',
       '{"rto_gap":true}',
       '{"type":"CREATE_RECORD","entity":"Gap","then":{"type":"CREATE_RECORD","entity":"TreatmentPlan"}}',
       20, 'PROMPT_14'),

      (1, 'AR-08',
       'Supplier missing valid BCP evidence → supplier CAPA',
       N'مورّد بدون دليل خطة استمرارية ساري → فتح إجراء تصحيحي',
       'When a supplier has no valid BCP evidence on file, open a supplier corrective action.',
       'SUPPLIER_EVIDENCE_GAP',
       '{"evidence_type":"BCP"}',
       '{"type":"CREATE_RECORD","entity":"CapaTask","template":"SUPPLIER_BCP_GAP"}',
       15, 'PROMPT_14'),

      (1, 'AR-09',
       'NCA evidence expired → notify evidence owner + control owner',
       N'دليل NCA منتهي الصلاحية → إشعار مالك الدليل ومالك الضابط',
       'When an evidence item linked to an NCA control expires, notify the evidence owner and the control owner.',
       'EVIDENCE_EXPIRED',
       '{"framework":"NCA"}',
       '{"type":"NOTIFY","role":"EVIDENCE_OWNER","also":["CONTROL_OWNER"],"template":"NCA_EVIDENCE_EXPIRED"}',
       20, 'PROMPT_14'),

      (1, 'AR-10',
       'Data subject request SLA breach → escalate to privacy manager',
       N'تجاوز اتفاقية مستوى الخدمة لطلب صاحب البيانات → تصعيد لمدير الخصوصية',
       'When a data-subject request approaches or breaches its SLA, escalate to the privacy manager.',
       'DSR_SLA_NEAR',
       NULL,
       '{"type":"ESCALATE","to_role":"PRIVACY_MANAGER","after_minutes":0}',
       10, 'PROMPT_14'),

      (1, 'AR-11',
       'Risk acceptance expired → reopen risk review',
       N'انتهاء قبول الخطر → إعادة فتح مراجعة الخطر',
       'When an approved risk acceptance reaches its expiry, reopen the risk for review.',
       'RISK_ACCEPTANCE_EXPIRED',
       NULL,
       '{"type":"CREATE_RECORD","entity":"RiskReview","then":{"type":"AUDIT","severity":"WARN","message":"acceptance expired"}}',
       20, 'PROMPT_14'),

      (1, 'AR-12',
       'CAPA overdue → escalate to owner + manager',
       N'إجراء تصحيحي متأخّر → تصعيد للمسؤول والمدير',
       'When a corrective action is overdue, escalate to the owner and the owner''s manager.',
       'CAPA_OVERDUE',
       NULL,
       '{"type":"ESCALATE","to_role":"OWNER","also":["OWNER_MANAGER"],"after_minutes":0}',
       15, 'PROMPT_14'),

      (1, 'AR-13',
       'Vulnerability SLA breach → escalate to cybersecurity manager',
       N'تجاوز اتفاقية مستوى الخدمة لمعالجة الثغرة → تصعيد لمدير الأمن السيبراني',
       'When a vulnerability remediation SLA is breached, escalate to the cybersecurity manager.',
       'VULN_SLA_BREACH',
       NULL,
       '{"type":"ESCALATE","to_role":"CYBERSECURITY_MANAGER","after_minutes":0}',
       10, 'PROMPT_14'),

      (1, 'AR-14',
       'Repeated supplier SLA breaches → update supplier risk score',
       N'تكرار خرق اتفاقية مستوى الخدمة → تحديث درجة مخاطر المورّد',
       'When a supplier breaches its SLA repeatedly within a window, raise the supplier''s risk score.',
       'SUPPLIER_SLA_REPEATED',
       NULL,
       '{"type":"FLAG","entity_field":"risk_score","increment":1}',
       20, 'PROMPT_14'),

      (1, 'AR-15',
       'Policy updated → trigger employee attestation campaign',
       N'تحديث سياسة → بدء حملة إقرار الموظفين',
       'When a policy is updated and republished, trigger an attestation campaign for all in-scope employees.',
       'POLICY_PUBLISHED',
       NULL,
       '{"type":"CREATE_RECORD","entity":"AttestationCampaign"}',
       15, 'PROMPT_14'),

      (1, 'AR-16',
       'Board approval required → create digital signature transaction',
       N'يلزم اعتماد مجلس الإدارة → إنشاء معاملة توقيع رقمي',
       'When a record reaches a state that requires board-level approval, automatically create a digital-signature transaction.',
       'BOARD_APPROVAL_REQUIRED',
       NULL,
       '{"type":"CREATE_RECORD","entity":"DigitalSignatureTransaction"}',
       5, 'PROMPT_14'),

      (1, 'AR-17',
       'Evidence rejected → reopen the related control gap',
       N'رفض دليل → إعادة فتح فجوة الضابط ذات الصلة',
       'When evidence is rejected during review, reopen any control gap it was meant to close.',
       'EVIDENCE_REJECTED',
       NULL,
       '{"type":"FLAG","entity_field":"status","to":"OPEN","entity":"Gap"}',
       20, 'PROMPT_14'),

      (1, 'AR-18',
       'Critical incident → activate crisis workflow',
       N'حادث حرج → تفعيل سير عمل الأزمة',
       'When an incident is classified critical, activate the crisis-management workflow.',
       'INCIDENT_CLASSIFIED',
       '{"severity":"CRITICAL"}',
       '{"type":"CREATE_RECORD","entity":"CrisisEvent","then":{"type":"AUDIT","severity":"ERROR","message":"crisis workflow auto-activated"}}',
       1, 'PROMPT_14'),

      (1, 'AR-19',
       'Cross-border transfer requested → trigger PDPL transfer assessment',
       N'طلب نقل بيانات خارج المملكة → تشغيل تقييم النقل الدولي',
       'When a cross-border data transfer is requested, trigger the PDPL transfer assessment workflow.',
       'CROSS_BORDER_REQUEST',
       NULL,
       '{"type":"CREATE_RECORD","entity":"CrossBorderTransferAssessment"}',
       10, 'PROMPT_14'),

      (1, 'AR-20',
       'Cloud provider region change → data residency + privacy re-assessment',
       N'تغيير منطقة استضافة مزوّد سحابي → إعادة تقييم الإقامة وقياس الخصوصية',
       'When a cloud provider changes its hosting region, re-trigger data residency + privacy assessments.',
       'CLOUD_REGION_CHANGED',
       NULL,
       '{"type":"CREATE_RECORD","entity":"DataResidencyReview","also":["PrivacyReassessment"]}',
       10, 'PROMPT_14');
END
GO
