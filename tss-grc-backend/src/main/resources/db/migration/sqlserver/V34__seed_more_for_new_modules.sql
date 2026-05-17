-- V34 — Top-up seed so every tab/state filter on the new module pages
-- shows at least one row.
--
-- After V33 the only empty surface left was the RCA "REJECTED" tab; we
-- also add a couple more REQUESTED signatures so the inbox feels alive
-- and one ARCHIVED privacy notice variant. Idempotent: each insert is
-- guarded by NOT EXISTS on its external_id natural key.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

-- (Organisation id = 1 inlined; GO resets DECLARE scope so we can't use @org_id across batches.)

-- ─── 1. RCA — add a REJECTED record so that tab is no longer empty ──
IF OBJECT_ID(N'cyber.root_cause_analyses', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM cyber.root_cause_analyses WHERE external_id = N'RCA-DEMO-07')
BEGIN
    INSERT INTO cyber.root_cause_analyses
        (organisation_id, external_id, related_entity_type, related_entity_id, related_entity_label,
         methodology, status,
         problem_statement_en, problem_statement_ar,
         five_whys_json, contributing_factors_json,
         root_cause_en, root_cause_ar,
         recommendations_en, recommendations_ar,
         facilitator, participants, conducted_at,
         submitted_by, submitted_at,
         approved_by, approver_name, approved_at,
         rejection_reason)
    VALUES
        (1, N'RCA-DEMO-07', N'incident', 7, N'INC-2026-0021 — single-source phishing email',
         N'FIVE_WHYS', N'REJECTED',
         N'On 2026-04-19 a phishing email passed Microsoft 365 filters and one user clicked the link. The endpoint was isolated within 14 minutes; no credentials or data were exfiltrated.',
         N'في 2026-04-19 نجح بريد تصيّد في تجاوز مرشحات مايكروسوفت 365 وضغط مستخدم واحد على الرابط. تم عزل الجهاز خلال 14 دقيقة دون تسريب بيانات أو اعتمادات.',
         N'[{"why":"User clicked the link","because":"Sender name matched an internal exec"},{"why":"Mimicked exec","because":"Public LinkedIn data"},{"why":"Filter passed it","because":"Domain reputation was clean (newly registered)"},{"why":"No banner","because":"External-sender banner not on this mailbox group"},{"why":"Group missed","because":"Manual onboarding skipped step"}]',
         N'{"people":["Onboarding gap"],"process":["Manual step skipped"],"technology":["Domain reputation only"],"environment":["Newly registered domains under-flagged"]}',
         N'Manual rather than scripted mailbox onboarding meant the external-sender banner was never enabled for this group.',
         N'كان تهيئة صناديق البريد لهذه المجموعة يدوية بدلاً من البرمجة الآلية، فلم يتم تفعيل تنبيه المرسل الخارجي.',
         N'1) Automate mailbox onboarding with Terraform module. 2) Tighten Mimecast newly-registered-domain rule to quarantine for 24h. 3) Run targeted phishing awareness module for affected team.',
         N'1) أتمتة تهيئة صناديق البريد بوحدة Terraform. 2) تشديد قاعدة Mimecast للنطاقات الجديدة (حجر 24 ساعة). 3) تشغيل تدريب وعي تصيّد موجه للفريق المتأثر.',
         N'Reem Al-Mutairi (SOC Lead)',
         N'Reem Al-Mutairi; Khalid Al-Otaibi; Layla Al-Saleh',
         DATEADD(DAY, -10, SYSUTCDATETIME()),
         2, DATEADD(DAY, -7, SYSUTCDATETIME()),
         1, N'admin', DATEADD(DAY, -4, SYSUTCDATETIME()),
         N'Insufficient evidence — provide Mimecast policy snapshot before the rule-change recommendation can be approved. Resubmit with attachments by 2026-05-20.');
END
GO

-- ─── 2. Two more REQUESTED signatures so the inbox is non-trivial ──
IF OBJECT_ID(N'signature.digital_signature_transactions', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM signature.digital_signature_transactions WHERE external_id = N'DSIG-DEMO-10')
BEGIN
    INSERT INTO signature.digital_signature_transactions
        (organisation_id, external_id, related_entity_type, related_entity_id, related_entity_label,
         signer_user_id, signer_name, signer_email, signer_role,
         initiator_user_id, initiator_name, signature_provider, purpose, purpose_ar,
         status, expiry_date)
    VALUES
        (1, N'DSIG-DEMO-10', N'policy', 7, N'POL — Acceptable use (v3.1)',
         1, N'Head of Compliance', N'compliance@tss.sa', N'COMPLIANCE_HEAD',
         1, N'admin', N'INTERNAL',
         N'Acceptable-use policy v3.1 sign-off ahead of company-wide attestation campaign launch on 2026-06-01',
         N'اعتماد سياسة الاستخدام المقبول الإصدار 3.1 قبل إطلاق حملة الإقرار على مستوى الشركة في 1 يونيو 2026',
         N'REQUESTED', DATEADD(DAY, 10, SYSUTCDATETIME())),
        (1, N'DSIG-DEMO-11', N'supplier_attestation', 22, N'TPRM — Solutions by STC annual attestation',
         1, N'Vendor Risk Manager', N'vrm@tss.sa', N'VRM',
         1, N'admin', N'INTERNAL',
         N'Annual TPRM attestation for Solutions by STC (managed-services provider) — SOC2 Type II + NCA controls confirmed',
         N'الإقرار السنوي لـ TPRM لشركة الحلول من STC - تأكيد ضوابط NCA و SOC2 Type II',
         N'REQUESTED', DATEADD(DAY, 14, SYSUTCDATETIME()));
END
GO

-- ─── 3. One ARCHIVED privacy notice variant + a freshly-PUBLISHED one ─
IF OBJECT_ID(N'privacy.privacy_notices', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM privacy.privacy_notices WHERE notice_code = N'PN-CUST-V0')
BEGIN
    INSERT INTO privacy.privacy_notices
        (organisation_id, notice_code, version, title_en, title_ar,
         purpose_en, purpose_ar, body_en, body_ar,
         linked_ropa_codes, status, published_at, archived_at, archive_reason_en)
    VALUES
        (1, N'PN-CUST-V0', 0,
         N'Customer notice — legacy (pre-PDPL 2024)',
         N'إشعار العملاء - النسخة القديمة (ما قبل PDPL 2024)',
         N'Archived customer notice covering pre-2024 KYC data collection',
         N'إشعار محفوظ يغطي جمع بيانات اعرف عميلك قبل 2024',
         N'(Archived) This notice describes data processing under the pre-2024 PDPL transitional regime…',
         N'(محفوظ) يصف هذا الإشعار معالجة البيانات في إطار المرحلة الانتقالية لنظام حماية البيانات الشخصية قبل 2024…',
         N'ROPA-2026-001', N'ARCHIVED',
         DATEADD(MONTH, -18, SYSUTCDATETIME()),
         DATEADD(MONTH, -6, SYSUTCDATETIME()),
         N'Superseded by PN-CUST v1 after PDPL 2024 alignment update');
END
GO

-- ─── 4. A REQUESTED cross-border transfer so all 4 status filters cover the workflow ─
IF OBJECT_ID(N'privacy.cross_border_transfers', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM privacy.cross_border_transfers WHERE external_id = N'CBT-DEMO-09')
BEGIN
    INSERT INTO privacy.cross_border_transfers
        (organisation_id, external_id, ropa_code, ropa_title_en,
         source_country, destination_country,
         destination_org_name, destination_org_role,
         data_categories, data_subjects_count_est, transfer_mechanism,
         safeguards_en, safeguards_ar,
         justification_en, justification_ar,
         status)
    VALUES
        (1, N'CBT-DEMO-09', N'ROPA-2026-007', N'Employee payroll processing',
         N'SAU', N'IND',
         N'TCS Saudi Arabia — payroll BPO', N'PROCESSOR',
         N'EMPLOYEE_PII,SALARY', 1450,
         N'BCR',
         N'Standard contractual clauses + intra-group transfer agreement; data resident in TCS India SOC2-Type-II data centre',
         N'بنود تعاقدية معيارية + اتفاقية تحويل داخل المجموعة - البيانات مقيمة في مركز بيانات TCS الهند المعتمد SOC2 Type II',
         N'Monthly payroll outsourcing — TCS India processes on behalf of TCS Saudi (intra-group); SCC + adequacy review pending SDAIA decision',
         N'تعهيد رواتب شهري - تعالج TCS الهند البيانات نيابة عن TCS السعودية (داخل المجموعة) - بنود تعاقدية معيارية في انتظار قرار سدايا',
         N'REQUESTED');
END
GO

PRINT N'V34 demo seed applied — every tab on Signatures / RCA / Data lifecycle now has rows.';
GO
