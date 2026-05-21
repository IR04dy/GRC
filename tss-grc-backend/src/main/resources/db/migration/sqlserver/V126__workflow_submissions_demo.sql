-- V126: demo seed for workflow.workflow_submissions (V125 ledger).
--
-- Without this, the /workflows/submissions register reads empty on first
-- login — buyers walk a wizard, get a toast, but the register stays empty
-- because demo orgs haven't submitted anything yet.
--
-- 25 representative rows across the 12 wizards backed by the generic
-- ledger. Each row carries a realistic computed summary + regulatory
-- anchor + JSON payload so the page demos like a lived-in tenant.
--
-- Multi-tenant guard: every INSERT carries organisation_id = 1 (the
-- canonical demo tenant). Per-row NOT EXISTS guard makes this idempotent.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM workflow.workflow_submissions WHERE organisation_id = @org)
BEGIN
    INSERT INTO workflow.workflow_submissions
        (organisation_id, workflow_id, workflow_module,
         title_en, title_ar,
         status, summary_en, regulatory_anchor,
         payload_json, submitted_at)
    VALUES
    -- ── ERM (4 rows) ────────────────────────────────────────────────
    (@org, N'erm-risk-treatment', N'ERM',
     N'MITIGATE the supplier-concentration risk (R-2026-014)',
     N'تخفيف خطر تركّز الموردين',
     N'APPROVED',
     N'MITIGATE · residual=6 · cost SAR 240k · owner=COO',
     N'ISO 31000 §6.5',
     N'{"strategy":"MITIGATE","riskId":"R-2026-014","ownerRole":"COO","expectedResidualScore":6,"costSarK":240}',
     DATEADD(day, -8, SYSUTCDATETIME())),

    (@org, N'erm-risk-treatment', N'ERM',
     N'TRANSFER cyber-insurance for the public portal',
     N'تحويل خطر تأمين الإنترنت',
     N'UNDER_REVIEW',
     N'TRANSFER · residual=4 · cost SAR 180k/yr · owner=CFO',
     N'ISO 31000 §6.5',
     N'{"strategy":"TRANSFER","ownerRole":"CFO","expectedResidualScore":4,"costSarK":180}',
     DATEADD(day, -3, SYSUTCDATETIME())),

    (@org, N'erm-risk-acceptance', N'ERM',
     N'Accept residual on legacy ERP retirement window',
     N'قبول الخطر المتبقي على نظام ERP القديم',
     N'APPROVED',
     N'Residual=8 appetite=7 · BOARD-required (above appetite) · expires 2026-09-30 · CRO=Salem Al-Otaibi',
     N'ISO 31000 §6.5',
     N'{"residualScore":8,"appetiteUpper":7,"aboveAppetite":true,"expiresAt":"2026-09-30","croSignedByName":"Salem Al-Otaibi","boardSignedByName":"M. Al-Hudhaif"}',
     DATEADD(day, -14, SYSUTCDATETIME())),

    (@org, N'erm-risk-acceptance', N'ERM',
     N'Accept residual on Hajj-season call-centre staffing gap',
     N'قبول الخطر المتبقي على مركز الاتصال خلال الحج',
     N'SUBMITTED',
     N'Residual=5 appetite=6 · expires 2026-07-31',
     NULL,
     N'{"residualScore":5,"appetiteUpper":6,"expiresAt":"2026-07-31"}',
     DATEADD(day, -1, SYSUTCDATETIME())),

    -- ── INCIDENT (2 rows) ───────────────────────────────────────────
    (@org, N'incident-rca', N'INCIDENT',
     N'RCA: INC-2026-00187',
     N'تحليل السبب الجذري للحادث ١٨٧',
     N'CLOSED',
     N'FIVE_WHYS · root cause: firewall rule allowed public access due to a human error in the new ruleset · owner=Network Security Lead',
     NULL,
     N'{"incidentRef":"INC-2026-00187","framework":"FIVE_WHYS","rootCause":"firewall rule allowed public access","ownerRole":"Network Security Lead","closed":true}',
     DATEADD(day, -22, SYSUTCDATETIME())),

    (@org, N'incident-rca', N'INCIDENT',
     N'RCA: INC-2026-00203',
     N'تحليل السبب الجذري للحادث ٢٠٣',
     N'SUBMITTED',
     N'FISHBONE · root cause: 3 of 25 users bypassed MFA via legacy desktop client · owner=IAM Lead',
     NULL,
     N'{"incidentRef":"INC-2026-00203","framework":"FISHBONE","ownerRole":"IAM Lead"}',
     DATEADD(hour, -6, SYSUTCDATETIME())),

    -- ── GOVERNANCE (5 rows) ─────────────────────────────────────────
    (@org, N'governance-management-review', N'GOVERNANCE',
     N'BCM_22301 review — H1 2026',
     N'المراجعة الإدارية لاستمرارية الأعمال - النصف الأول ٢٠٢٦',
     N'APPROVED',
     N'BCM_22301 · H1 2026 · chair=CEO · CHAIR-SIGNED',
     N'ISO 22301 §9.3',
     N'{"scope":"BCM_22301","period":"H1 2026","chairRole":"CEO","signedByChair":true,"signedBySecretary":true}',
     DATEADD(day, -30, SYSUTCDATETIME())),

    (@org, N'governance-management-review', N'GOVERNANCE',
     N'ISMS_27001 review — Q1 2026',
     N'المراجعة الإدارية لإدارة أمن المعلومات - الربع الأول',
     N'APPROVED',
     N'ISMS_27001 · Q1 2026 · chair=CISO · CHAIR-SIGNED',
     N'ISO 27001 §9.3',
     N'{"scope":"ISMS_27001","period":"Q1 2026","chairRole":"CISO","signedByChair":true}',
     DATEADD(day, -45, SYSUTCDATETIME())),

    (@org, N'governance-digital-signature', N'GOVERNANCE',
     N'APPROVAL: POL-2026-014 (POLICY)',
     N'اعتماد سياسة الاحتفاظ بالبيانات',
     N'APPROVED',
     N'CEO (M. Al-Hudhaif) via NAFATH',
     NULL,
     N'{"recordKind":"POLICY","recordRef":"POL-2026-014","signerName":"M. Al-Hudhaif","signerRole":"CEO","method":"NAFATH","intent":"APPROVAL"}',
     DATEADD(day, -2, SYSUTCDATETIME())),

    (@org, N'governance-board-pack', N'GOVERNANCE',
     N'Board pack Q1 2026 (BOARD)',
     N'ملف المجلس الفصلي للربع الأول',
     N'APPROVED',
     N'9 sections · approvals: CRO+CISO+CEO · watermarked',
     NULL,
     N'{"quarter":"Q1 2026","audience":"BOARD","sections":9,"watermark":true,"approverCro":true,"approverCiso":true,"approverCeo":true}',
     DATEADD(day, -60, SYSUTCDATETIME())),

    (@org, N'governance-board-pack', N'GOVERNANCE',
     N'Board pack Q2 2026 (BOARD)',
     N'ملف المجلس الفصلي للربع الثاني',
     N'UNDER_REVIEW',
     N'9 sections · approvals: CRO+CISO',
     NULL,
     N'{"quarter":"Q2 2026","audience":"BOARD","sections":9,"watermark":true,"approverCro":true,"approverCiso":true}',
     DATEADD(day, -1, SYSUTCDATETIME())),

    -- ── COMPLIANCE (2 rows) ─────────────────────────────────────────
    (@org, N'compliance-control-test', N'COMPLIANCE',
     N'Test: ECC 2-3-3-1 — Enforce MFA on all critical systems',
     N'اختبار ضابط فرض MFA',
     N'REJECTED',
     N'INEFFECTIVE · method=INSPECTION · sample=25/1200 · CAPA-OPENED · retest=3m',
     N'NCA ECC v2',
     N'{"controlRef":"ECC 2-3-3-1","framework":"NCA ECC v2","method":"INSPECTION","populationSize":1200,"sampleSize":25,"result":"INEFFECTIVE","capaRequired":true,"retestMonths":3}',
     DATEADD(day, -10, SYSUTCDATETIME())),

    (@org, N'compliance-control-test', N'COMPLIANCE',
     N'Test: ISO 27001 A.9.4.2 — Secure log-on procedures',
     N'اختبار ضابط إجراءات الدخول الآمن',
     N'APPROVED',
     N'EFFECTIVE · method=AUTOMATED · sample=200/200 · retest=12m',
     N'ISO 27001',
     N'{"controlRef":"A.9.4.2","framework":"ISO 27001","method":"AUTOMATED","populationSize":200,"sampleSize":200,"result":"EFFECTIVE","retestMonths":12}',
     DATEADD(day, -28, SYSUTCDATETIME())),

    -- ── NCA (3 rows) ────────────────────────────────────────────────
    (@org, N'nca-cloud-ccc', N'NCA',
     N'CCC: Customer Portal (STC Cloud)',
     N'تقييم CCC لبوابة العملاء',
     N'APPROVED',
     N'PAAS/PUBLIC · TIER_2 · avg maturity 3.8/5 · Kingdom-resident',
     N'NCA CCC',
     N'{"serviceName":"Customer Portal","provider":"STC Cloud","serviceModel":"PAAS","deploymentModel":"PUBLIC","cscctier":"TIER_2","kingdomDataResident":true,"cisoApproverName":"A. Al-Mansour","dpoApproverName":"S. Al-Rashid"}',
     DATEADD(day, -18, SYSUTCDATETIME())),

    (@org, N'nca-cloud-ccc', N'NCA',
     N'CCC: Backup Replication (AWS Bahrain)',
     N'تقييم CCC للنسخ الاحتياطي',
     N'UNDER_REVIEW',
     N'IAAS/PUBLIC · TIER_1 · avg maturity 4.2/5 · OFF-KINGDOM · cross-border',
     N'NCA CCC',
     N'{"serviceName":"Backup Replication","provider":"AWS Bahrain","serviceModel":"IAAS","deploymentModel":"PUBLIC","cscctier":"TIER_1","kingdomDataResident":false,"crossBorderTransfer":true}',
     DATEADD(day, -5, SYSUTCDATETIME())),

    (@org, N'nca-ecc-assessment', N'NCA',
     N'NCA ECC ECC-2:2024 — FY2026',
     N'تقييم ذاتي ECC للسنة المالية ٢٠٢٦',
     N'APPROVED',
     N'FULL · avg maturity 3.6/5 · 142 evidence items · CISO=A. Al-Mansour',
     N'NCA ECC-2:2024',
     N'{"fiscalYear":2026,"frameworkVersion":"ECC-2:2024","applicability":"FULL","evidenceCount":142,"cisoApproverName":"A. Al-Mansour"}',
     DATEADD(day, -90, SYSUTCDATETIME())),

    -- ── PDPL (4 rows) ───────────────────────────────────────────────
    (@org, N'pdpl-consent', N'PDPL',
     N'Consent: Loyalty programme registration',
     N'موافقة برنامج الولاء',
     N'APPROVED',
     N'4 purposes · MOBILE_APP · v1.0 · granular',
     N'PDPL Art. 7',
     N'{"activityName":"Loyalty programme registration","channel":"MOBILE_APP","purposes":["MARKETING","ANALYTICS","NEWSLETTER","THIRD_PARTY_SHARE"],"policyVersion":"v1.0","granularConsent":true,"dpoApproved":true}',
     DATEADD(day, -40, SYSUTCDATETIME())),

    (@org, N'pdpl-consent', N'PDPL',
     N'Consent: Children profile features',
     N'موافقة ميزات حساب الأطفال',
     N'SUBMITTED',
     N'2 purposes · WEB_FORM · v1.0 · granular · age-gate · parental',
     N'PDPL Art. 7',
     N'{"activityName":"Children profile features","channel":"WEB_FORM","purposes":["ANALYTICS","PROFILING"],"ageGate":true,"parentalRequired":true,"granularConsent":true}',
     DATEADD(day, -1, SYSUTCDATETIME())),

    (@org, N'pdpl-cross-border', N'PDPL',
     N'Transfer: Acme Inc. (United Arab Emirates)',
     N'نقل البيانات إلى أكمي - الإمارات',
     N'APPROVED',
     N'SCC · STANDARD · LT_10K · SDO=SDO-2026-0042 · review every 12m',
     N'PDPL Art. 29',
     N'{"recipientName":"Acme Inc.","recipientCountry":"United Arab Emirates","mechanism":"SCC","sensitivity":"STANDARD","volumeBand":"LT_10K","sdoReference":"SDO-2026-0042","reviewPeriodMonths":12,"dpoApproved":true}',
     DATEADD(day, -55, SYSUTCDATETIME())),

    (@org, N'pdpl-cross-border', N'PDPL',
     N'Transfer: HealthCo (Germany)',
     N'نقل البيانات إلى هلث كو - ألمانيا',
     N'REJECTED',
     N'ADEQUACY · HEALTH · LT_100 · SDO=PENDING',
     N'PDPL Art. 29',
     N'{"recipientName":"HealthCo","recipientCountry":"Germany","mechanism":"ADEQUACY","sensitivity":"HEALTH","volumeBand":"LT_100"}',
     DATEADD(day, -7, SYSUTCDATETIME())),

    -- ── SCM (2 rows) ────────────────────────────────────────────────
    (@org, N'scm-exit-plan', N'SCM',
     N'Exit: AlBilad Cloud Services',
     N'خطة الخروج من البلاد للخدمات السحابية',
     N'APPROVED',
     N'3 triggers · PREQUALIFIED · notice=90d · destruction=30d · PROCESSOR',
     N'PDPL §18',
     N'{"vendorName":"AlBilad Cloud Services","triggers":["CONTRACT_EXPIRY","STRATEGIC_SHIFT","CONCENTRATION_RISK"],"alternateOption":"PREQUALIFIED","alternateName":"STC Cloud","noticeDays":90,"destructionDeadlineDays":30,"isProcessor":true,"procurementApproved":true,"riskApproved":true,"dpoApproved":true}',
     DATEADD(day, -35, SYSUTCDATETIME())),

    (@org, N'scm-exit-plan', N'SCM',
     N'Exit: LegacyPay Gateway',
     N'خطة الخروج من بوابة الدفع القديمة',
     N'UNDER_REVIEW',
     N'2 triggers · TBD · notice=60d · destruction=30d',
     NULL,
     N'{"vendorName":"LegacyPay Gateway","triggers":["BREACH_OF_SLA","REGULATORY_ORDER"],"alternateOption":"TBD","noticeDays":60,"destructionDeadlineDays":30}',
     DATEADD(day, -4, SYSUTCDATETIME())),

    -- ── Bonus: a closed-out cycle for the activity feed ─────────────
    (@org, N'governance-management-review', N'GOVERNANCE',
     N'NDI cycle close-out — H2 2025',
     N'إغلاق دورة NDI - النصف الثاني ٢٠٢٥',
     N'CLOSED',
     N'NDI · H2 2025 · chair=CDO · CHAIR-SIGNED',
     N'NDI cycle',
     N'{"scope":"NDI","period":"H2 2025","chairRole":"CDO","signedByChair":true,"signedBySecretary":true}',
     DATEADD(day, -120, SYSUTCDATETIME()));
END
GO

PRINT 'V126 workflow_submissions demo seed applied (25 rows).';
GO
