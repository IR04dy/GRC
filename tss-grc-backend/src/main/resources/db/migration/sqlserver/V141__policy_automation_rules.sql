-- V141: F8 automation rules for PolicyService events.
--
-- Policy publication is the anchor event for the compliance posture
-- arc — attestation campaigns, framework mapping refresh, regulator
-- alignment reports all key on POLICY_PUBLISHED. Wiring it into F8
-- lets those downstream workflows kick off automatically the moment
-- a new policy version goes live.
--
-- PolicyService publishes:
--
--   POLICY_CREATED                  — new policy/version row added
--   POLICY_PUBLISHED                — went live (payload carries
--                                     policyCode + version + category)
--   POLICY_EXPIRED                  — natural expiry or sweep
--   POLICY_TRANSITIONED             — generic state change
--   POLICY_EXCEPTION_REQUESTED      — exception filed
--   POLICY_EXCEPTION_APPROVED       — exception sanctioned
--                                     (carries riskLevel, expiryDate)
--   POLICY_EXCEPTION_REJECTED       — exception denied
--   POLICY_EXCEPTION_EXPIRED        — exception expiry sweep
--   POLICY_ATTESTATION_ASSIGNED     — user got an attestation task
--   POLICY_ATTESTED                 — user signed off
--   POLICY_ATTESTATION_DECLINED     — user refused
--
-- AR-71..AR-75 — service #10 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-71 ── Policy PUBLISHED → notify policy owner + compliance officer
-- Publication is the moment the policy becomes binding. The owner
-- needs to know it's live (they own the implementation rollout) and
-- the compliance officer needs to fan out attestation tasks. We
-- don't auto-CREATE_RECORD attestation tasks here — that's a manual
-- decision in the v1 design (some policies attest org-wide, some
-- only to a department) — but the notification is the trigger.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-71')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-71',
         N'Policy PUBLISHED → notify owner + compliance officer',
         N'نشر سياسة → إشعار مالك السياسة ومسؤول الالتزام',
         N'Publication is the moment a policy becomes binding. The '
         + N'owner manages the implementation rollout; the compliance '
         + N'officer fans out attestation tasks (org-wide vs. '
         + N'department scope is a manual decision in v1, so we '
         + N'notify rather than auto-create here).',
         N'يصبح إنفاذ السياسة لازماً عند النشر — يصل الإشعار للمالك (لمتابعة التطبيق) ومسؤول الالتزام (لتوزيع مهام الإقرار).',
         N'POLICY_PUBLISHED',
         N'{}',
         N'{"type":"NOTIFY","roles":["POLICY_OWNER","COMPLIANCE_OFFICER"],"template":"POLICY_PUBLISHED"}',
         2, N'CUSTOM');
END

-- AR-72 ── Policy EXPIRED → escalate to policy owner for renewal
-- An expired policy is a control gap that the owner must close
-- immediately — either re-publish a new version or formally archive
-- the policy if it's no longer needed. The compliance officer joins
-- the escalation to ensure the gap is logged against the relevant
-- framework controls until the renewal lands.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-72')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-72',
         N'Policy EXPIRED → escalate owner + compliance officer for renewal',
         N'انتهاء صلاحية السياسة → تصعيد للمالك ومسؤول الالتزام للتجديد',
         N'An expired policy is a live control gap. Owner must '
         + N'either publish a new version or formally archive the '
         + N'row. Compliance officer logs the gap against the '
         + N'relevant framework controls until renewal lands.',
         N'انتهاء صلاحية السياسة فجوة في الضوابط — يصعَّد للمالك للتجديد ومسؤول الالتزام لتسجيل الفجوة لحين النشر الجديد.',
         N'POLICY_EXPIRED',
         N'{}',
         N'{"type":"ESCALATE","roles":["POLICY_OWNER","COMPLIANCE_OFFICER"],"template":"POLICY_EXPIRED"}',
         2, N'CUSTOM');
END

-- AR-73 ── Policy exception APPROVED (HIGH risk) → notify CRO + GC
-- A HIGH-risk policy exception is a deliberate residual-risk
-- decision (e.g. "this vendor can run unpatched libraries until
-- contract renewal"). NCA ECC requires documented compensating
-- controls + executive sign-off. CRO accepts the residual exposure
-- against the appetite; GC sizes external-liability implications.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-73')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-73',
         N'HIGH-risk policy exception APPROVED → escalate CRO + GC',
         N'اعتماد استثناء سياسة عالي المخاطر → تصعيد لرئيس إدارة المخاطر والمستشار العام',
         N'A HIGH-risk policy exception is a deliberate '
         + N'residual-risk decision (e.g. allowing an unpatched '
         + N'library until contract renewal). NCA ECC demands '
         + N'documented compensating controls + executive sign-off — '
         + N'CRO owns the residual exposure against appetite; GC '
         + N'sizes external-liability implications.',
         N'اعتماد استثناء عالي المخاطر قرار تنفيذي يستوجب ضوابط بديلة موثقة — '
         + N'يصل رئيس إدارة المخاطر لاعتماد المخاطر المتبقية والمستشار العام لتقييم المسؤولية القانونية.',
         N'POLICY_EXCEPTION_APPROVED',
         N'{"riskLevel":"HIGH"}',
         N'{"type":"ESCALATE","roles":["CHIEF_RISK_OFFICER","GENERAL_COUNSEL"],"template":"POLICY_EXCEPTION_APPROVED_HIGH"}',
         1, N'CUSTOM');
END

-- AR-74 ── Policy ATTESTED → audit committee KPI feed
-- Each attestation completion feeds the audit committee's
-- "attestation coverage" KPI (% of users attested per policy
-- campaign). A milestone notification on commit keeps the
-- dashboard current — the audit committee tracks this as a
-- compliance-maturity metric, not as an exception alert.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-74')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-74',
         N'Policy attestation completed → audit committee KPI feed',
         N'إتمام إقرار سياسة → تغذية مؤشر لجنة التدقيق',
         N'Each attestation completion feeds the audit committee''s '
         + N'attestation-coverage KPI (% of users attested per policy '
         + N'campaign). Milestone-style notification keeps the '
         + N'compliance-maturity dashboard current.',
         N'يساهم كل إقرار في احتساب مؤشر تغطية الإقرارات لدى لجنة التدقيق — يُسجَّل معلم لتحديث المؤشر فوراً.',
         N'POLICY_ATTESTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["AUDIT_COMMITTEE"],"template":"POLICY_ATTESTED_KPI"}',
         5, N'CUSTOM');
END

-- AR-75 ── Policy attestation DECLINED → escalate to manager + DPO
-- A declined attestation is a strong signal — either the user
-- doesn't understand the policy (training gap) or genuinely
-- disagrees with it (policy review may be needed). Their manager
-- triages first; the DPO joins if the policy touches personal-data
-- handling (decline-route is often the early indicator of a PDPL
-- compliance gap).
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-75')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-75',
         N'Policy attestation DECLINED → escalate manager + DPO',
         N'رفض إقرار السياسة → تصعيد للمدير المباشر ومسؤول حماية البيانات',
         N'A declined attestation is a strong signal — either a '
         + N'training gap (user doesn''t understand the policy) or '
         + N'real disagreement (policy may need review). Manager '
         + N'triages first; DPO joins if the policy touches '
         + N'personal-data handling, since decline-route is often '
         + N'the earliest indicator of a PDPL compliance gap.',
         N'رفض الإقرار مؤشر مبكر — يصعَّد للمدير المباشر للمراجعة، ولمسؤول حماية البيانات إن مست السياسة بيانات شخصية.',
         N'POLICY_ATTESTATION_DECLINED',
         N'{}',
         N'{"type":"ESCALATE","roles":["USER_MANAGER","DPO"],"template":"POLICY_ATTESTATION_DECLINED"}',
         3, N'CUSTOM');
END
GO

PRINT 'V141 seeded 5 policy automation rules (AR-71..AR-75).';
GO
