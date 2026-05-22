-- V145: F8 automation rules for DpiaService + CrossBorderTransferService.
--
-- Two PDPL workflows in one V-file because they share a common
-- target audience (DPO + privacy program lead) and share regulator
-- visibility (Saudi Data Office reviews both DPIA outcomes and
-- cross-border transfer adequacy decisions).
--
-- DpiaService publishes (PDPL Article 25 — Data Protection Impact
-- Assessment for any new high-risk processing activity):
--
--   DPIA_DRAFTED       — new assessment opened
--   DPIA_SUBMITTED     — DRAFT → IN_REVIEW
--   DPIA_APPROVED      — IN_REVIEW → APPROVED
--   DPIA_REJECTED      — IN_REVIEW → REJECTED
--   DPIA_EXPIRED       — APPROVED → EXPIRED (next-review past)
--
-- CrossBorderTransferService publishes (PDPL Article 29 — adequacy
-- decision for personal-data export outside the Kingdom):
--
--   TRANSFER_REQUESTED  — new transfer record
--   TRANSFER_ASSESSED   — residency/safeguards check completed
--   TRANSFER_APPROVED   — transfer can proceed
--   TRANSFER_REJECTED   — denied
--   TRANSFER_EXPIRED    — past expiryDate
--
-- AR-91..AR-95 — services #14 + #15 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-91 ── HIGH residual-risk DPIA approved → escalate to CRO + GC
-- An approved DPIA with HIGH overall residual risk means the
-- mitigations couldn't bring the processing activity below the
-- HIGH threshold. PDPL Article 25 says HIGH-residual processing
-- needs prior consultation with the regulator — CRO accepts the
-- residual against appetite; GC sizes any external-liability
-- implications before processing begins.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-91')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-91',
         N'HIGH-residual DPIA approved → escalate CRO + GC + DPO',
         N'اعتماد تقييم أثر بمخاطر عالية → تصعيد لرئيس إدارة المخاطر والمستشار العام',
         N'A DPIA approved with HIGH overall residual risk means '
         + N'mitigations couldn''t bring the processing below HIGH. '
         + N'PDPL Article 25 requires prior consultation with the '
         + N'regulator for HIGH-residual processing — CRO accepts '
         + N'the residual against appetite, GC sizes external '
         + N'liability before processing begins.',
         N'اعتماد تقييم أثر يبقى معه أثر متبقي مرتفع يستوجب التشاور المسبق مع الجهة التنظيمية وفق المادة 25 — '
         + N'يصل رئيس إدارة المخاطر لاعتماد المخاطرة المتبقية والمستشار العام لتقدير المسؤولية الخارجية.',
         N'DPIA_APPROVED',
         N'{"overallResidualRisk":"HIGH"}',
         N'{"type":"ESCALATE","roles":["CHIEF_RISK_OFFICER","GENERAL_COUNSEL","DPO"],"template":"DPIA_HIGH_RESIDUAL_APPROVED"}',
         1, N'CUSTOM');
END

-- AR-92 ── DPIA expired → notify DPO + processing owner for reassessment
-- PDPL Article 25 expects DPIAs to be refreshed when processing
-- materially changes, but in practice the "next review date" date
-- is the routine refresh trigger. DPO + processing owner kick off
-- a re-draft before the EXPIRED state turns into a control gap on
-- the privacy posture dashboard.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-92')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-92',
         N'DPIA expired → DPO + processing owner kick off re-draft',
         N'انتهاء صلاحية تقييم الأثر → بدء إعادة التقييم',
         N'PDPL Article 25 expects DPIAs to refresh when processing '
         + N'materially changes; the next-review-date is the routine '
         + N'refresh trigger. DPO + processing owner kick off the '
         + N're-draft before EXPIRED becomes a control gap on the '
         + N'privacy posture dashboard.',
         N'تتطلب المادة 25 تحديث تقييم الأثر عند تغيّر المعالجة جوهرياً — '
         + N'يصل مسؤول حماية البيانات ومالك المعالجة لبدء إعادة التقييم قبل تحولها لفجوة.',
         N'DPIA_EXPIRED',
         N'{}',
         N'{"type":"NOTIFY","roles":["DPO","PROCESSING_OWNER"],"template":"DPIA_EXPIRED"}',
         3, N'CUSTOM');
END

-- AR-93 ── Cross-border transfer approved → audit committee record
-- Every approved cross-border transfer is a regulator-watched
-- record — the Saudi Data Office maintains a register of cross-
-- border data flows and may request records during examinations.
-- Audit committee milestone keeps the running cross-border ledger
-- current; DPO records the transfer in the audit-ready inventory.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-93')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-93',
         N'Cross-border transfer APPROVED → audit committee + DPO record',
         N'اعتماد نقل البيانات خارج المملكة → تسجيل لدى لجنة التدقيق ومسؤول حماية البيانات',
         N'Every approved cross-border transfer is regulator-watched. '
         + N'Saudi Data Office maintains a register of cross-border '
         + N'flows and may request records during examinations. '
         + N'Audit committee milestone keeps the ledger current; '
         + N'DPO records in the audit-ready inventory.',
         N'كل نقل بيانات خارج المملكة قابل لتدقيق مكتب البيانات السعودي — '
         + N'يُسجَّل اعتماد النقل لدى لجنة التدقيق ومسؤول حماية البيانات في السجل الجاهز للتدقيق.',
         N'TRANSFER_APPROVED',
         N'{}',
         N'{"type":"NOTIFY","roles":["AUDIT_COMMITTEE","DPO"],"template":"TRANSFER_APPROVED"}',
         3, N'CUSTOM');
END

-- AR-94 ── Cross-border transfer REJECTED → notify requester + GC
-- A rejection means a planned data flow was blocked at the
-- adequacy check. Requester (the business owner who initiated)
-- needs to know to find an alternative (in-Kingdom processing,
-- different vendor, additional safeguards); GC reviews if the
-- rejection arose from a contractual obligation that now needs
-- renegotiation.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-94')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-94',
         N'Cross-border transfer REJECTED → notify requester + GC',
         N'رفض نقل البيانات خارج المملكة → إشعار المُقدم والمستشار العام',
         N'A rejection blocks a planned data flow at the adequacy '
         + N'check. Requester finds an alternative (in-Kingdom '
         + N'processing, different vendor, additional safeguards); '
         + N'GC reviews if the rejection conflicts with a '
         + N'contractual obligation that needs renegotiation.',
         N'رفض النقل يوقف التدفق المخطط — '
         + N'يبحث المُقدم عن بديل، ويراجع المستشار العام إن كان الرفض يستوجب إعادة تفاوض على عقد قائم.',
         N'TRANSFER_REJECTED',
         N'{}',
         N'{"type":"ESCALATE","roles":["TRANSFER_REQUESTER","GENERAL_COUNSEL","DPO"],"template":"TRANSFER_REJECTED"}',
         2, N'CUSTOM');
END

-- AR-95 ── Cross-border transfer EXPIRED → renew or formally close
-- An EXPIRED transfer means the time-bound approval lapsed. If
-- the data flow is still active, the business needs to either
-- renew the approval (new assessment + new approval) or formally
-- stop the flow. DPO + requester are notified together so the
-- decision happens before the flow becomes an unauthorised
-- transfer.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-95')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-95',
         N'Cross-border transfer EXPIRED → renew or formally close',
         N'انتهاء صلاحية نقل البيانات → التجديد أو الإغلاق الرسمي',
         N'EXPIRED means the time-bound approval lapsed. If the '
         + N'flow is still active, the business renews (new '
         + N'assessment + new approval) or formally stops the '
         + N'flow. DPO + requester decide before the flow becomes '
         + N'an unauthorised transfer.',
         N'انتهاء صلاحية الاعتماد لا يوقف التدفق آلياً — '
         + N'يقرر مسؤول حماية البيانات والمُقدم التجديد أو الإيقاف الرسمي قبل أن يصبح النقل غير مصرح به.',
         N'TRANSFER_EXPIRED',
         N'{}',
         N'{"type":"ESCALATE","roles":["DPO","TRANSFER_REQUESTER"],"template":"TRANSFER_EXPIRED"}',
         2, N'CUSTOM');
END
GO

PRINT 'V145 seeded 5 DPIA + cross-border automation rules (AR-91..AR-95).';
GO
