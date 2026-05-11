-- V33 — Demo seed for the new Saudi GRC modules.
--
-- The five tables shipped in V30 / V31 / V32 (digital signatures, RCA,
-- cross-border transfers, disposal certificates, privacy notices) had
-- no seed data, so every new page rendered an empty state. This
-- migration loads realistic Saudi-flavored examples in mixed lifecycle
-- states + cross-links them to existing cyber.incidents and
-- privacy.processing_activities rows so the modules feel connected.
--
-- Idempotent: each insert is guarded by NOT EXISTS on a natural key.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @org_id BIGINT = 1; -- TSS (default tenant)

-- ─── 1. Digital signature transactions ─────────────────────────────
-- Eight transactions across REQUESTED / SIGNED / REJECTED / EXPIRED
-- covering the canonical use cases in §28: board pack approval, risk
-- acceptance, BCP sign-off, supplier attestation, NCA compliance
-- attestation, regulatory evidence packet, policy approval, cross-
-- border transfer approval.
IF OBJECT_ID(N'signature.digital_signature_transactions', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM signature.digital_signature_transactions WHERE external_id = N'DSIG-DEMO-01')
BEGIN
    INSERT INTO signature.digital_signature_transactions
        (organisation_id, external_id, related_entity_type, related_entity_id, related_entity_label,
         signer_user_id, signer_name, signer_email, signer_role,
         initiator_user_id, initiator_name, signature_provider, purpose, purpose_ar,
         status, signed_document_hash, certificate_reference, signature_timestamp,
         rejection_reason, expiry_date)
    VALUES
        (@org_id, N'DSIG-DEMO-01', N'board_pack', 12, N'Board Pack 2026-Q1',
         1, N'Chairman of the Board', N'chair@tss.sa', N'BOARD_CHAIR',
         1, N'admin', N'INTERNAL',
         N'Board acknowledgement of Q1 2026 GRC report (top risks + appetite breaches + incident summary)',
         N'إقرار مجلس الإدارة بتقرير الحوكمة والمخاطر للربع الأول من 2026 (أهم المخاطر، تجاوزات الشهية، ملخص الحوادث)',
         N'SIGNED', N'a3f1c92d8e7b4f5e6a1d2c3b4e5f6789012345678901234567890123456789ab',
         N'cert:internal:bp-2026q1', DATEADD(DAY, -5, SYSUTCDATETIME()), NULL, NULL),
        (@org_id, N'DSIG-DEMO-02', N'risk_acceptance', 4, N'RSK-2026-004 — accept above appetite',
         1, N'Chief Risk Officer', N'cro@tss.sa', N'CRO',
         1, N'admin', N'INTERNAL',
         N'Risk acceptance for RSK-2026-004: 18-month exposure above amber appetite pending Aramco contract migration',
         N'قبول المخاطر للسجل رقم 004: تعرّض لمدة 18 شهراً فوق الشهية الكهرمانية في انتظار ترحيل عقد أرامكو',
         N'REQUESTED', NULL, NULL, NULL, NULL, DATEADD(DAY, 7, SYSUTCDATETIME())),
        (@org_id, N'DSIG-DEMO-03', N'bcm_plan', 3, N'BCP — HR systems',
         1, N'BCM Officer', N'bcm@tss.sa', N'BCM_OFFICER',
         1, N'admin', N'INTERNAL',
         N'Annual sign-off on the HR-systems business continuity plan; RTO 4h / RPO 1h validated',
         N'الاعتماد السنوي لخطة استمرارية أعمال أنظمة الموارد البشرية; زمن استعادة 4 ساعات / نقطة استعادة 1 ساعة',
         N'SIGNED', N'b4f2d83a9c6e5f7a8b9c1d2e3f4a5b6c7890123456789012345678901234abcd',
         N'cert:internal:bcp-hr-2026', DATEADD(DAY, -12, SYSUTCDATETIME()), NULL, NULL),
        (@org_id, N'DSIG-DEMO-04', N'supplier_attestation', 7, N'AWS — annual security attestation',
         1, N'Procurement Lead', N'procurement@tss.sa', N'PROCUREMENT_LEAD',
         1, N'admin', N'EXTERNAL',
         N'AWS annual SOC 2 + ISO 27001 attestation re-confirmation for FY2026',
         N'إعادة تأكيد شهادتي SOC 2 و ISO 27001 السنويتين من مزود AWS للسنة المالية 2026',
         N'SIGNED', N'c5d3e94b0a7f6e8d9c1d2e3f4a5b6c7d8901234567890123456789012345bcde',
         N'cert:aws:attestation-2026', DATEADD(DAY, -30, SYSUTCDATETIME()), NULL, NULL),
        (@org_id, N'DSIG-DEMO-05', N'nca_compliance_attestation', 1, N'NCA ECC self-assessment 2026',
         1, N'Chief Information Security Officer', N'ciso@tss.sa', N'CISO',
         1, N'admin', N'INTERNAL',
         N'CISO attestation of completeness and accuracy of NCA ECC 2024-version self-assessment',
         N'إقرار رئيس أمن المعلومات باكتمال ودقة تقييم ضوابط الأمن السيبراني الأساسية NCA ECC إصدار 2024',
         N'REQUESTED', NULL, NULL, NULL, NULL, DATEADD(DAY, 14, SYSUTCDATETIME())),
        (@org_id, N'DSIG-DEMO-06', N'regulatory_evidence_packet', 2, N'PDPL Art. 35 quarterly packet',
         1, N'Data Protection Officer', N'dpo@tss.sa', N'DPO',
         1, N'admin', N'INTERNAL',
         N'DPO confirmation that the PDPL Art. 35 quarterly evidence packet is complete and submission-ready',
         N'تأكيد مسؤول حماية البيانات أن حزمة الأدلة الفصلية للمادة 35 من نظام حماية البيانات مكتملة وجاهزة للتقديم',
         N'REJECTED', NULL, NULL, NULL,
         N'Two privacy-by-design control test results missing; resubmit after Q2 control attestation cycle.', NULL),
        (@org_id, N'DSIG-DEMO-07', N'policy_approval', 5, N'Acceptable Use Policy v3',
         1, N'Chief Executive Officer', N'ceo@tss.sa', N'CEO',
         1, N'admin', N'INTERNAL',
         N'CEO approval of Acceptable Use Policy v3 (BYOD provisions + AI usage guardrails added)',
         N'اعتماد الرئيس التنفيذي لسياسة الاستخدام المقبول الإصدار 3 (إضافة أحكام إحضار الجهاز الشخصي وضوابط الذكاء الاصطناعي)',
         N'SIGNED', N'd6e4f0a5c1b8a7f9d0e1f2c3a4b5c6d7e890123456789012345678901234cdef',
         N'cert:internal:aup-v3', DATEADD(DAY, -45, SYSUTCDATETIME()), NULL, NULL),
        (@org_id, N'DSIG-DEMO-08', N'cross_border_transfer', 1, N'AWS US — analytics workload',
         1, N'Data Protection Officer', N'dpo@tss.sa', N'DPO',
         1, N'admin', N'INTERNAL',
         N'Cross-border transfer approval CBT-2026-001 (employee analytics → AWS us-east-1, SCC-based)',
         N'اعتماد نقل البيانات العابر للحدود CBT-2026-001 (تحليلات الموظفين إلى AWS us-east-1، عبر البنود التعاقدية القياسية)',
         N'EXPIRED', NULL, NULL, NULL, NULL, DATEADD(DAY, -1, SYSUTCDATETIME()));
END;
GO

-- ─── 2. Cross-border transfers ─────────────────────────────────────
DECLARE @org_id BIGINT = 1;
IF OBJECT_ID(N'privacy.cross_border_transfers', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM privacy.cross_border_transfers WHERE external_id = N'CBT-DEMO-01')
BEGIN
    INSERT INTO privacy.cross_border_transfers
        (organisation_id, external_id, ropa_code, ropa_title_en,
         source_country, destination_country, destination_org_name, destination_org_role,
         data_categories, data_subjects_count_est,
         transfer_mechanism, safeguards_en, safeguards_ar,
         justification_en, justification_ar,
         residency_check_at, residency_check_by, residency_check_outcome,
         status, rejection_reason, approved_at, approver_name,
         effective_from, expiry_date)
    VALUES
        (@org_id, N'CBT-DEMO-01', N'ROPA-2026-002', N'Marketing Analytics',
         N'SAU', N'USA', N'AWS Inc. (us-east-1 region)', N'PROCESSOR',
         N'CONTACT,BEHAVIORAL,DEMOGRAPHIC', 145000,
         N'SCC', N'AES-256 at rest, TLS 1.3 in transit, EU-style SCC clauses 2021/914',
         N'تشفير AES-256 للبيانات الساكنة، TLS 1.3 للبيانات المتنقلة، شروط تعاقدية قياسية بصيغة 2021/914',
         N'Hosting customer-journey analytics workload; KSA region currently unavailable for the required ML model size',
         N'استضافة عبء تحليلات رحلة العميل؛ المنطقة السعودية غير متاحة حالياً لحجم نموذج التعلم المطلوب',
         DATEADD(DAY, -20, SYSUTCDATETIME()), N'DPO Office', N'PASS_WITH_CONDITIONS',
         N'APPROVED', NULL, DATEADD(DAY, -18, SYSUTCDATETIME()), N'Chief Privacy Officer',
         DATEADD(DAY, -15, SYSUTCDATETIME()), DATEADD(MONTH, 11, SYSUTCDATETIME())),
        (@org_id, N'CBT-DEMO-02', N'ROPA-2026-005', N'Supplier Due Diligence',
         N'SAU', N'GBR', N'Dow Jones Risk & Compliance', N'PROCESSOR',
         N'IDENTITY,FINANCIAL', 8500,
         N'ADEQUACY', N'UK on SDAIA adequacy whitelist as of 2025-Q3',
         N'المملكة المتحدة ضمن قائمة الكفاية المعتمدة من هيئة سدايا في الربع الثالث 2025',
         N'KYC/sanctions screening against Dow Jones global database; UK adequacy decision in force',
         N'فرز اعتماد العملاء وفحص العقوبات من قاعدة Dow Jones العالمية؛ قرار الكفاية للمملكة المتحدة سارٍ',
         DATEADD(DAY, -90, SYSUTCDATETIME()), N'DPO Office', N'PASS',
         N'APPROVED', NULL, DATEADD(DAY, -88, SYSUTCDATETIME()), N'Chief Privacy Officer',
         DATEADD(DAY, -85, SYSUTCDATETIME()), DATEADD(MONTH, 23, SYSUTCDATETIME())),
        (@org_id, N'CBT-DEMO-03', N'ROPA-2026-003', N'Employee HR Records',
         N'SAU', N'IRL', N'Workday Ltd (Dublin DC)', N'PROCESSOR',
         N'IDENTITY,EMPLOYMENT,PAYROLL,HEALTH', 2400,
         N'BCR', N'Workday EU BCR approved 2023; SCC backup; HR-only access via SSO + MFA',
         N'قواعد الشركة الملزمة لـ Workday EU معتمدة 2023، نسخة احتياطية بالبنود التعاقدية، وصول الموارد البشرية فقط عبر تسجيل الدخول الموحد + التحقق الثنائي',
         N'Saudi Aramco-acquired HRIS migration; Workday platform mandated by group standard',
         N'ترحيل نظام معلومات الموارد البشرية للجهة المستحوذ عليها من أرامكو السعودية؛ منصة Workday إلزامية وفق معيار المجموعة',
         DATEADD(DAY, -45, SYSUTCDATETIME()), N'DPO Office', N'PASS',
         N'APPROVED', NULL, DATEADD(DAY, -40, SYSUTCDATETIME()), N'Chief Privacy Officer',
         DATEADD(DAY, -38, SYSUTCDATETIME()), DATEADD(MONTH, 35, SYSUTCDATETIME())),
        (@org_id, N'CBT-DEMO-04', N'ROPA-2026-001', N'Customer KYC Processing',
         N'SAU', N'USA', N'OpenAI LLC (azure-eastus)', N'PROCESSOR',
         N'CONTACT,IDENTITY,FREE_TEXT', 12000,
         N'EXPLICIT_CONSENT', NULL, NULL,
         N'Pilot of AI-assisted KYC document parsing; explicit subject consent at form submission; pseudonymisation before transfer',
         N'تجربة تحليل وثائق اعتماد العملاء بمساعدة الذكاء الاصطناعي؛ موافقة صريحة من صاحب البيانات عند الإرسال؛ إخفاء الهوية قبل النقل',
         DATEADD(DAY, -7, SYSUTCDATETIME()), N'DPO Office', N'FAIL',
         N'REJECTED', N'OpenAI sub-processor list includes Microsoft US — chains a second cross-border hop without subject re-consent. Reapply with EU-region endpoint.',
         NULL, NULL, NULL, NULL),
        (@org_id, N'CBT-DEMO-05', N'ROPA-2026-004', N'Fraud Detection Monitoring',
         N'SAU', N'NLD', N'Stripe Payments Europe Ltd', N'PROCESSOR',
         N'FINANCIAL,IDENTITY,DEVICE', 380000,
         N'SCC', N'EU-style SCC 2021/914 module 2, tokenisation of PAN before transfer',
         N'بنود تعاقدية أوروبية قياسية 2021/914 الوحدة الثانية، ترميز رقم البطاقة قبل النقل',
         N'PCI-compliant fraud screening; Stripe Radar model trained on global merchant data — KSA-only training set unavailable',
         N'فرز احتيال متوافق مع PCI؛ نموذج Stripe Radar مدرَّب على بيانات تجار عالمية — مجموعة تدريب سعودية فقط غير متاحة',
         NULL, NULL, NULL,
         N'ASSESSED', NULL, NULL, NULL,
         NULL, DATEADD(MONTH, 12, SYSUTCDATETIME())),
        (@org_id, N'CBT-DEMO-06', N'ROPA-2026-002', N'Marketing Analytics',
         N'SAU', N'DEU', N'HubSpot Germany GmbH', N'PROCESSOR',
         N'CONTACT,BEHAVIORAL', 95000,
         N'SCC', NULL, NULL,
         N'Campaign automation + lead enrichment; SCC-based transfer to HubSpot EU instance',
         N'أتمتة الحملات وإثراء العملاء المحتملين؛ نقل وفق البنود التعاقدية القياسية لخادم HubSpot الأوروبي',
         NULL, NULL, NULL,
         N'REQUESTED', NULL, NULL, NULL, NULL, NULL);
END;
GO

-- ─── 3. Disposal certificates ──────────────────────────────────────
DECLARE @org_id BIGINT = 1;
IF OBJECT_ID(N'privacy.disposal_certificates', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM privacy.disposal_certificates WHERE external_id = N'DC-DEMO-01')
BEGIN
    INSERT INTO privacy.disposal_certificates
        (organisation_id, external_id, related_entity_type, related_entity_id, related_entity_label,
         retention_rule_ref, scope_description_en, scope_description_ar, records_count_est,
         disposal_method, method_details_en,
         status, scheduled_date,
         executed_at, executor_name, verified_at, verifier_name,
         evidence_ref, owner_role, owner_name)
    VALUES
        (@org_id, N'DC-DEMO-01', N'ropa', N'ROPA-2026-001', N'Customer KYC Processing',
         N'RET-CUST-7Y', N'KYC dossiers of customer accounts inactive for more than 7 years (closed accounts only)',
         N'ملفات اعتماد العملاء للحسابات غير النشطة لأكثر من 7 سنوات (الحسابات المغلقة فقط)', 4720,
         N'ANONYMIZE', N'PII identifier columns hashed with SHA-256 + per-row salt; behavioural data retained for analytics',
         N'VERIFIED', DATEADD(DAY, -60, SYSUTCDATETIME()),
         DATEADD(DAY, -55, SYSUTCDATETIME()), N'Data Engineering Lead',
         DATEADD(DAY, -50, SYSUTCDATETIME()), N'Chief Privacy Officer',
         N'EV-2026-DC-001', N'DPO', N'Sarah Al-Mansour'),
        (@org_id, N'DC-DEMO-02', N'ropa', N'ROPA-2026-002', N'Marketing Analytics',
         N'RET-MKT-2Y', N'Marketing leads who did not convert within 24 months and have not opted in to ongoing communications',
         N'العملاء المحتملون الذين لم يُحوَّلوا خلال 24 شهراً ولم يوافقوا على التواصل المستمر', 18300,
         N'DELETE', N'Hard delete from operational store; downstream warehouse rows cascade via dbt model',
         N'COMPLETED', DATEADD(DAY, -10, SYSUTCDATETIME()),
         DATEADD(DAY, -8, SYSUTCDATETIME()), N'Marketing Ops Lead',
         NULL, NULL,
         N'EV-2026-DC-002', N'DPO', N'Sarah Al-Mansour'),
        (@org_id, N'DC-DEMO-03', N'data_asset', N'ASSET-LOGS-FW-01', N'Firewall syslog (perimeter)',
         N'RET-LOG-1Y', N'Perimeter firewall syslog older than 12 months (security retention rule)',
         N'سجلات جدار الحماية المحيطي الأقدم من 12 شهراً (وفق قاعدة احتفاظ الأمن)', 1850000,
         N'DESTROY', N'Disk-level shred on the S3 Glacier archive segments older than 365 days; AWS KMS keys revoked',
         N'IN_PROGRESS', DATEADD(DAY, -2, SYSUTCDATETIME()),
         NULL, NULL, NULL, NULL,
         NULL, N'CISO', N'Ahmed Al-Otaibi'),
        (@org_id, N'DC-DEMO-04', N'ropa', N'ROPA-2026-005', N'Supplier Due Diligence',
         N'RET-SUP-5Y', N'Onboarding dossiers of suppliers whose contracts ended more than 5 years ago',
         N'ملفات إدخال الموردين الذين انتهت عقودهم منذ أكثر من 5 سنوات', 320,
         N'ARCHIVE', N'Move to cold storage with legal-hold release flag; read-only for procurement audit',
         N'SCHEDULED', DATEADD(DAY, 14, SYSUTCDATETIME()),
         NULL, NULL, NULL, NULL,
         NULL, N'Procurement Lead', N'Layla Al-Harbi'),
        (@org_id, N'DC-DEMO-05', N'ropa', N'ROPA-2026-003', N'Employee HR Records',
         N'RET-HR-10Y', N'Personnel files of employees who left more than 10 years ago — KSA labour law retention complete',
         N'ملفات الموظفين الذين تركوا العمل منذ أكثر من 10 سنوات — اكتمال الاحتفاظ وفق نظام العمل السعودي', 87,
         N'DELETE', N'Cancelled — pending pension dispute hold from HRSD',
         N'CANCELLED', DATEADD(DAY, -5, SYSUTCDATETIME()),
         NULL, NULL, NULL, NULL,
         NULL, N'CHRO Office', N'Mohammed Al-Qahtani');
END;
GO

-- ─── 4. Privacy notices ────────────────────────────────────────────
DECLARE @org_id BIGINT = 1;
IF OBJECT_ID(N'privacy.privacy_notices', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM privacy.privacy_notices WHERE notice_code = N'PN-EMPLOYEE' AND organisation_id = @org_id)
BEGIN
    -- Employee privacy notice — v1 (archived) + v2 (published, current)
    INSERT INTO privacy.privacy_notices
        (organisation_id, notice_code, version, title_en, title_ar, purpose_en, purpose_ar,
         data_categories, recipients, retention_period_en, retention_period_ar,
         dpo_contact, lawful_basis, body_en, body_ar, linked_ropa_codes,
         status, effective_from, effective_to, published_at, published_by,
         archived_at, archived_by, archive_reason_en)
    VALUES
        (@org_id, N'PN-EMPLOYEE', 1,
         N'Employee Privacy Notice', N'إشعار خصوصية الموظفين',
         N'Inform employees how TSS processes their personal data through the HR lifecycle',
         N'إبلاغ الموظفين بكيفية معالجة TSS لبياناتهم الشخصية خلال دورة حياة الموارد البشرية',
         N'IDENTITY,CONTACT,EMPLOYMENT,PAYROLL,PERFORMANCE',
         N'HR department, Payroll team, Line managers, GOSI, ZATCA',
         N'Active employment + 10 years post-termination (KSA Labour Law Art. 224)',
         N'مدة العمل + 10 سنوات بعد انتهاء العلاقة (المادة 224 من نظام العمل السعودي)',
         N'dpo@tss.sa', N'CONTRACT',
         N'TSS Group processes employee personal data to operate the employment relationship, comply with Saudi labour law, GOSI, ZATCA and HRSD obligations, and run internal HR analytics. Categories include identity, contact, employment history, payroll, GOSI registration, and performance reviews. Data is shared with the HR team, payroll providers, and Saudi regulators as required. You have the right to access, correct, and (where applicable) erase your data — contact dpo@tss.sa.',
         N'تعالج مجموعة TSS البيانات الشخصية للموظفين لإدارة علاقة العمل والامتثال لنظام العمل السعودي والتأمينات الاجتماعية والزكاة وضوابط الموارد البشرية وإجراء تحليلات الموارد البشرية الداخلية. تشمل الفئات الهوية، التواصل، السجل الوظيفي، الرواتب، تسجيل التأمينات، تقييم الأداء. تُشارَك البيانات مع فريق الموارد البشرية ومزودي الرواتب والجهات التنظيمية السعودية عند الحاجة. لك الحق في الوصول والتصحيح والمحو (حيث ينطبق) — تواصل عبر dpo@tss.sa.',
         N'ROPA-2026-003',
         N'ARCHIVED', DATEADD(YEAR, -2, SYSUTCDATETIME()), DATEADD(MONTH, -6, SYSUTCDATETIME()),
         DATEADD(YEAR, -2, SYSUTCDATETIME()), 1,
         DATEADD(MONTH, -6, SYSUTCDATETIME()), 1, N'Superseded by v2 (added AI-assisted performance review provisions)'),
        (@org_id, N'PN-EMPLOYEE', 2,
         N'Employee Privacy Notice (v2)', N'إشعار خصوصية الموظفين (الإصدار الثاني)',
         N'Inform employees how TSS processes their personal data through the HR lifecycle, including AI-assisted performance review',
         N'إبلاغ الموظفين بكيفية معالجة TSS لبياناتهم الشخصية خلال دورة حياة الموارد البشرية بما في ذلك تقييم الأداء بمساعدة الذكاء الاصطناعي',
         N'IDENTITY,CONTACT,EMPLOYMENT,PAYROLL,PERFORMANCE,AI_INFERENCES',
         N'HR department, Payroll team, Line managers, GOSI, ZATCA, AI vendor (anonymised only)',
         N'Active employment + 10 years post-termination (KSA Labour Law Art. 224)',
         N'مدة العمل + 10 سنوات بعد انتهاء العلاقة (المادة 224 من نظام العمل السعودي)',
         N'dpo@tss.sa', N'CONTRACT',
         N'TSS Group processes employee personal data to operate the employment relationship, comply with Saudi labour law, GOSI, ZATCA and HRSD obligations, run internal HR analytics, and (new in v2) generate AI-assisted performance review drafts which are always human-reviewed before they reach you. You have the right to opt out of AI-assisted features.',
         N'تعالج مجموعة TSS البيانات الشخصية للموظفين لإدارة علاقة العمل والامتثال لنظام العمل السعودي والتأمينات الاجتماعية والزكاة وضوابط الموارد البشرية، وإجراء تحليلات الموارد البشرية الداخلية، (جديد في الإصدار الثاني) توليد مسوّدات تقييم الأداء بمساعدة الذكاء الاصطناعي مع مراجعة بشرية إلزامية قبل الوصول إليك. لك الحق في الانسحاب من ميزات الذكاء الاصطناعي.',
         N'ROPA-2026-003',
         N'PUBLISHED', DATEADD(MONTH, -6, SYSUTCDATETIME()), NULL,
         DATEADD(MONTH, -6, SYSUTCDATETIME()), 1, NULL, NULL, NULL);

    -- Customer privacy notice — single published version.
    INSERT INTO privacy.privacy_notices
        (organisation_id, notice_code, version, title_en, title_ar, purpose_en,
         data_categories, recipients, retention_period_en, dpo_contact, lawful_basis,
         body_en, body_ar, linked_ropa_codes, status, effective_from, published_at, published_by)
    VALUES
        (@org_id, N'PN-CUSTOMER', 1,
         N'Customer Privacy Notice', N'إشعار خصوصية العملاء',
         N'Inform customers how TSS processes their personal data when they sign up, transact, and receive support',
         N'IDENTITY,CONTACT,FINANCIAL,DEVICE,BEHAVIORAL',
         N'Customer Success, Billing, Anti-fraud team, Saudi regulators (when required)',
         N'Account active + 7 years after account closure (PDPL Art. 18 + KSA AML retention)',
         N'dpo@tss.sa', N'CONTRACT',
         N'TSS Group processes customer personal data to operate your account, process transactions, screen for fraud, and support you. Categories include identity, contact, financial, device fingerprint, and behavioural analytics. We do not sell your data. Cross-border transfers happen only under SCC, BCR, or adequacy decisions — see our cross-border transfer register for the current list. You have full PDPL rights (access, correction, erasure, portability, objection) — exercise them via dpo@tss.sa.',
         N'تعالج مجموعة TSS البيانات الشخصية للعملاء لإدارة حسابك وتنفيذ المعاملات والكشف عن الاحتيال وتقديم الدعم. الفئات تشمل الهوية، التواصل، المالية، بصمة الجهاز، التحليل السلوكي. نحن لا نبيع بياناتك. تتم عمليات النقل عبر الحدود فقط وفق البنود التعاقدية القياسية، أو القواعد الملزمة، أو قرارات الكفاية — راجع سجل النقل العابر للحدود للقائمة الحالية. لديك كامل الحقوق وفق نظام حماية البيانات الشخصية (الوصول، التصحيح، المحو، النقل، الاعتراض) — يمكنك ممارستها عبر dpo@tss.sa.',
         N'ROPA-2026-001,ROPA-2026-002,ROPA-2026-004',
         N'PUBLISHED', DATEADD(MONTH, -8, SYSUTCDATETIME()),
         DATEADD(MONTH, -8, SYSUTCDATETIME()), 1);

    -- Recruitment privacy notice — v1 DRAFT (not yet published).
    INSERT INTO privacy.privacy_notices
        (organisation_id, notice_code, version, title_en, title_ar, purpose_en,
         data_categories, recipients, retention_period_en, dpo_contact, lawful_basis,
         body_en, body_ar, linked_ropa_codes, status)
    VALUES
        (@org_id, N'PN-RECRUIT', 1,
         N'Recruitment Candidate Privacy Notice', N'إشعار خصوصية المرشحين للتوظيف',
         N'Inform job candidates how TSS handles application data, interview notes, and assessment scores',
         N'IDENTITY,CONTACT,EMPLOYMENT_HISTORY,ASSESSMENT_SCORES',
         N'HR recruiting team, Hiring managers, Background-check vendor',
         N'18 months after final decision (PDPL Art. 18 retention principle) — extended if candidate joins talent pool with consent',
         N'dpo@tss.sa', N'LEGITIMATE_INTEREST',
         N'TSS Group processes candidate personal data to evaluate suitability for open roles, conduct interviews, and complete pre-employment screening. Categories include identity, contact, employment history, references, and assessment scores. Data is shared with the recruiting team, hiring managers, and (with explicit consent) third-party background-check vendors. We delete candidate data within 18 months of the final decision unless you opt in to our talent pool.',
         N'تعالج مجموعة TSS البيانات الشخصية للمرشحين لتقييم ملاءمتهم للوظائف المفتوحة وإجراء المقابلات وإكمال فحص ما قبل التوظيف. الفئات تشمل الهوية، التواصل، السجل الوظيفي، التزكيات، نتائج التقييم. تُشارَك البيانات مع فريق التوظيف ومديري التوظيف ومزودي الفحص الخارجيين (بموافقة صريحة). نحذف بيانات المرشحين خلال 18 شهراً من القرار النهائي ما لم توافق على الانضمام إلى مجموعة المواهب لدينا.',
         NULL, N'DRAFT');
END;
GO

-- ─── 5. Root Cause Analyses ────────────────────────────────────────
-- Linked to actual cyber.incidents rows.
DECLARE @org_id BIGINT = 1;
DECLARE @inc_phish BIGINT, @inc_vpn BIGINT, @inc_malware BIGINT, @inc_unenc BIGINT, @inc_sql BIGINT, @inc_ransom BIGINT;
SELECT @inc_phish   = id FROM cyber.incidents WHERE organisation_id = @org_id AND title LIKE N'Phishing Email%' AND is_deleted = 0;
SELECT @inc_vpn     = id FROM cyber.incidents WHERE organisation_id = @org_id AND title LIKE N'Unauthorized Access%VPN%' AND is_deleted = 0;
SELECT @inc_malware = id FROM cyber.incidents WHERE organisation_id = @org_id AND title LIKE N'Malware on Workstation%' AND is_deleted = 0;
SELECT @inc_unenc   = id FROM cyber.incidents WHERE organisation_id = @org_id AND title LIKE N'Unencrypted Customer Data%' AND is_deleted = 0;
SELECT @inc_sql     = id FROM cyber.incidents WHERE organisation_id = @org_id AND title LIKE N'Web Application SQL%' AND is_deleted = 0;
SELECT @inc_ransom  = id FROM cyber.incidents WHERE organisation_id = @org_id AND title LIKE N'Ransomware attempt%' AND is_deleted = 0;

IF OBJECT_ID(N'cyber.root_cause_analyses', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM cyber.root_cause_analyses WHERE external_id = N'RCA-DEMO-01')
BEGIN
    INSERT INTO cyber.root_cause_analyses
        (organisation_id, external_id, related_entity_type, related_entity_id, related_entity_label,
         methodology, problem_statement_en, problem_statement_ar,
         five_whys_json, contributing_factors_json,
         root_cause_en, root_cause_ar, recommendations_en, recommendations_ar,
         facilitator, participants, conducted_at, capa_refs,
         status, submitted_at, submitted_by, approved_at, approved_by, approver_name, rejection_reason)
    VALUES
        (@org_id, N'RCA-DEMO-01', N'incident', ISNULL(@inc_phish, 1), N'INC-2026-001 — Phishing email campaign',
         N'FIVE_WHYS',
         N'A targeted phishing campaign reached 47 employees, of whom 3 entered credentials on the cloned login page before the email gateway updated its blocklist.',
         N'وصلت حملة تصيّد موجَّهة إلى 47 موظفاً، أدخل 3 منهم بيانات اعتمادهم في صفحة الدخول المستنسخة قبل تحديث القائمة السوداء لبوابة البريد.',
         N'[{"question":"Why did 3 employees submit credentials?","answer":"The cloned page mimicked the corporate SSO with a near-identical domain (tss-sa.co vs tss.sa)."},{"question":"Why did the email reach those employees?","answer":"The email gateway''s look-alike-domain rule was disabled in April 2026 during a tuning pass that was never reverted."},{"question":"Why was the tuning pass not reverted?","answer":"No follow-up ticket was created and the change-management process did not flag it as a missing rollback."},{"question":"Why did change management not flag it?","answer":"Tuning changes are exempt from the standard CR template — they bypass the rollback-required gate."},{"question":"Why are tuning changes exempt?","answer":"Legacy convention from 2022 when tuning changes were considered low-risk. The threat landscape has shifted; the exemption is now stale."}]',
         N'[{"category":"PROCESS","factor":"CR template exempts tuning changes from rollback-required gate"},{"category":"TECHNOLOGY","factor":"Look-alike-domain rule was disabled on the email gateway"},{"category":"PEOPLE","factor":"Lack of recent phishing awareness training — 6 months overdue for those 3 employees"}]',
         N'Stale exemption in the change-management process let a security-relevant control be disabled without a rollback gate. Combined with lapsed phishing awareness training, this opened the window for credential capture.',
         N'استثناء قديم في عملية إدارة التغيير سمح بتعطيل ضابط أمني دون بوابة استرجاع. مع انقضاء صلاحية التدريب التوعوي حول التصيّد، فُتحت نافذة لاصطياد بيانات الاعتماد.',
         N'1) Remove the tuning-change exemption from the CR template; require rollback plan for all security-relevant changes. 2) Re-enable the look-alike-domain rule on the email gateway. 3) Issue mandatory phishing awareness refresher within 30 days for any employee whose last training is >90 days old.',
         N'1) إزالة استثناء تغييرات الضبط من قالب طلب التغيير؛ اشتراط خطة استرجاع لكل التغييرات ذات الصلة بالأمن. 2) إعادة تفعيل قاعدة النطاقات المتشابهة في بوابة البريد. 3) إصدار تدريب توعوي إلزامي خلال 30 يوماً لأي موظف لم يحصل على تدريب منذ أكثر من 90 يوماً.',
         N'CISO Office (Ahmed Al-Otaibi)',
         N'Ahmed Al-Otaibi (CISO), Sarah Al-Mansour (DPO), Khalid Al-Shehri (CR Manager), Layla Al-Harbi (HR L&D)',
         DATEADD(DAY, -10, SYSUTCDATETIME()),
         N'CAPA-1024,CAPA-1025,CAPA-1026',
         N'APPROVED', DATEADD(DAY, -7, SYSUTCDATETIME()), 1,
         DATEADD(DAY, -5, SYSUTCDATETIME()), 1, N'Chief Risk Officer', NULL),

        (@org_id, N'RCA-DEMO-02', N'incident', ISNULL(@inc_vpn, 2), N'INC-2026-002 — VPN brute-force attempt',
         N'FISHBONE',
         N'A botnet attempted ~14,000 VPN logins over 72 hours from 1,800 unique IPs before the IDS auto-blocked the source AS.',
         N'حاول روبوت إجراء حوالي 14,000 محاولة دخول إلى الشبكة الخاصة الافتراضية خلال 72 ساعة من 1,800 عنوان فريد قبل أن يحظر نظام كشف التسلل المصدر تلقائياً.',
         NULL,
         N'[{"category":"PROCESS","factor":"No IP-rate-limit policy on the VPN concentrator"},{"category":"TECHNOLOGY","factor":"IDS auto-block threshold set at 24h instead of recommended 4h"},{"category":"ENVIRONMENT","factor":"VPN endpoint exposed to public IPv4 (corporate network is now mostly IPv6-only)"},{"category":"PEOPLE","factor":"No security operations on-call between 02:00 and 06:00 KSA time on weekends"}]',
         N'The VPN concentrator was deployed in 2019 under the assumption that the perimeter firewall would shield it; the firewall ruleset was relaxed in 2024 to support a partner integration and the VPN compensating control was never re-tightened.',
         N'تم نشر مركّز الشبكة الخاصة الافتراضية في 2019 بافتراض أن جدار الحماية المحيطي سيحميه؛ تم تخفيف قواعد جدار الحماية في 2024 لدعم تكامل شريك ولم يتم إعادة تشديد الضابط التعويضي.',
         N'1) Enable IP-rate-limit (5 attempts / 60s / IP) on the VPN concentrator. 2) Lower IDS auto-block threshold from 24h → 4h. 3) Move the VPN endpoint behind the WAF instead of public IPv4. 4) Document weekend on-call rotation for SOC L2.',
         N'1) تفعيل تقييد عدد المحاولات (5 محاولات/60 ث/عنوان) في مركّز الشبكة. 2) تخفيض عتبة الحظر التلقائي في نظام كشف التسلل من 24 ساعة إلى 4 ساعات. 3) نقل نقطة الوصول خلف جدار حماية تطبيقات الويب بدل الـ IPv4 العام. 4) توثيق مناوبة عطلة نهاية الأسبوع لمستوى L2 لمركز عمليات الأمن.',
         N'SOC Lead', N'Khalid Al-Shehri (SOC Lead), Ahmed Al-Otaibi (CISO), Hassan Al-Dosari (Network Architect)',
         DATEADD(DAY, -15, SYSUTCDATETIME()), N'CAPA-1031,CAPA-1032',
         N'IN_REVIEW', DATEADD(DAY, -3, SYSUTCDATETIME()), 1, NULL, NULL, NULL, NULL),

        (@org_id, N'RCA-DEMO-03', N'incident', ISNULL(@inc_unenc, 4), N'INC-2026-004 — Unencrypted customer data on share',
         N'PARETO',
         N'Internal audit found 23 spreadsheets containing customer PII on a shared drive without encryption-at-rest, accessible to 140 users beyond business-need.',
         N'اكتشفت المراجعة الداخلية 23 جدولاً يحتوي على بيانات شخصية للعملاء على قرص مشترك دون تشفير، متاحة لـ 140 مستخدماً خارج الحاجة الفعلية.',
         NULL,
         N'[{"factor":"Manual export to Excel for ad-hoc reporting","frequency":17},{"factor":"Permissions inherited from parent folder","frequency":12},{"factor":"No DLP rule on shared-drive uploads","frequency":8},{"factor":"Lack of awareness of customer-data sensitivity tier","frequency":5}]',
         N'Top contributor (80% Pareto cut): manual Excel export workflow circumvents the data warehouse''s row-level security. Secondary: shared-drive permissions inherited too broadly.',
         N'المساهم الأعلى (قطع 80% باريتو): سير عمل التصدير اليدوي إلى Excel يتجاوز الأمن على مستوى الصف في مستودع البيانات. الثانوي: صلاحيات القرص المشترك مَوروثة بشكل واسع جداً.',
         N'1) Roll out a sanctioned reporting tool (Tableau or Power BI) with row-level security; deprecate the Excel export path. 2) Run a DLP scan on the shared drive monthly. 3) Re-baseline shared-drive permissions; remove inheritance for folders containing customer data.',
         N'1) طرح أداة تقارير معتمدة (Tableau أو Power BI) مع أمن على مستوى الصف؛ إيقاف مسار التصدير إلى Excel. 2) تشغيل فحص حماية البيانات شهرياً على القرص المشترك. 3) إعادة ضبط صلاحيات القرص المشترك؛ إزالة الوراثة للمجلدات التي تحتوي بيانات العملاء.',
         N'DPO Office (Sarah Al-Mansour)',
         N'Sarah Al-Mansour (DPO), Mohammed Al-Qahtani (Data Engineering), Khalid Al-Shehri (Internal Audit)',
         DATEADD(DAY, -20, SYSUTCDATETIME()), N'CAPA-1040',
         N'IN_REVIEW', DATEADD(DAY, -2, SYSUTCDATETIME()), 1, NULL, NULL, NULL, NULL),

        (@org_id, N'RCA-DEMO-04', N'incident', ISNULL(@inc_ransom, 11), N'INC-2026-011 — Ransomware blocked at endpoint',
         N'FIVE_WHYS',
         N'CrowdStrike Falcon blocked a ransomware payload on a finance department workstation. The payload was delivered via a USB device plugged in over the weekend.',
         N'حجبت CrowdStrike Falcon حمولة فدية على محطة عمل في قسم المالية. وصلت الحمولة عبر جهاز USB موصول خلال نهاية الأسبوع.',
         N'[{"question":"Why was the USB allowed?","answer":"USB mass-storage class is not yet blocked at the endpoint for the finance team."},{"question":"Why is finance not in the USB-block scope?","answer":"They are paid via a legacy biometric thumb-drive for SAMA-mandated dual-control transfers."},{"question":"Why is the legacy biometric thumb-drive still in use?","answer":"The replacement YubiKey programme is paused pending SAMA approval of the new control."},{"question":"Why is SAMA approval pending?","answer":"Approval submitted Q4 2025 — currently in queue with SAMA cyber-control team."},{"question":"Why can''t we deploy partial mitigation?","answer":"We can — and didn''t. A whitelist policy permitting only the biometric vendor''s VID/PID would scope the exception narrowly without waiting for SAMA."}]',
         NULL,
         N'Scope of the USB-block exception was overly broad (whole finance department) while a narrower whitelist (vendor VID/PID) would have closed the gap without disrupting the SAMA-mandated workflow.',
         N'نطاق استثناء حظر USB كان واسعاً (قسم المالية بأكمله) بينما قائمة بيضاء أضيق (معرف بائع الجهاز) كانت كافية لسد الفجوة دون تعطيل سير العمل الإلزامي من ساما.',
         N'1) Tighten USB exception to a VID/PID allow-list for the biometric thumb-drive only. 2) Accelerate the YubiKey rollout once SAMA approves. 3) Add a CrowdStrike custom IOA for the specific ransomware family detected.',
         N'1) تشديد استثناء USB إلى قائمة سماح بمعرف البائع للقرص البيومتري فقط. 2) تسريع نشر YubiKey بعد موافقة ساما. 3) إضافة مؤشر هجوم مخصص في CrowdStrike لعائلة الفدية المكتشفة.',
         N'CISO Office', N'Ahmed Al-Otaibi (CISO), Mohammed Al-Qahtani (Finance IT), SOC L2',
         DATEADD(DAY, -25, SYSUTCDATETIME()), N'CAPA-1051,CAPA-1052',
         N'APPROVED', DATEADD(DAY, -22, SYSUTCDATETIME()), 1,
         DATEADD(DAY, -20, SYSUTCDATETIME()), 1, N'Chief Risk Officer', NULL),

        (@org_id, N'RCA-DEMO-05', N'incident', ISNULL(@inc_sql, 5), N'INC-2026-005 — SQLi attempt blocked by WAF',
         N'OTHER',
         N'Cloudflare WAF blocked 2,400 SQL injection probes against the customer portal login over 6 hours. No data exfiltrated. Investigation focuses on whether legacy login flow had an unfiltered code path.',
         N'حجب جدار حماية تطبيقات Cloudflare 2,400 محاولة حقن SQL ضد تسجيل دخول بوابة العملاء خلال 6 ساعات. لا تسرب للبيانات. التحقيق يركّز على ما إذا كان مسار التسجيل القديم يحتوي على فرع غير مفلتر.',
         NULL, NULL,
         N'Investigation still in progress — preliminary finding: the legacy login flow (deprecated but still routable) used a string-concatenation SQL builder. The new login flow uses parameterised queries.',
         N'التحقيق لا يزال جارياً — الاستنتاج الأولي: مسار التسجيل القديم (موقوف ولكن لا يزال قابلاً للتوجيه) كان يستخدم بناء SQL بالدمج النصي. المسار الجديد يستخدم استعلامات مُعَلَّمة.',
         N'1) Remove legacy login route entirely. 2) Add SAST rule against raw SQL string concatenation in CI. 3) Confirm WAF managed-rule set for OWASP CRS is on latest version.',
         N'1) إزالة مسار التسجيل القديم بالكامل. 2) إضافة قاعدة SAST ضد دمج SQL النصي في CI. 3) التأكد من أن مجموعة قواعد OWASP CRS في جدار الحماية بأحدث إصدار.',
         N'AppSec Lead', N'AppSec, Web Platform team', DATEADD(DAY, -1, SYSUTCDATETIME()), NULL,
         N'DRAFT', NULL, NULL, NULL, NULL, NULL, NULL);
END;
GO

PRINT N'V33 demo seed applied — digital signatures, cross-border transfers, disposal certificates, privacy notices, RCAs.';
GO
