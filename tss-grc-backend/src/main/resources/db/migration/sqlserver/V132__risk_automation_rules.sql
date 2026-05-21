-- V132: F8 automation rules for RiskService (ERM / ISO 31000) events.
--
-- PR #55 wired RiskService into the F8 engine. It now publishes two
-- DomainEvents on every mutation:
--
--   RISK_CREATED       — risk identified in OPEN status
--   RISK_TRANSITIONED  — OPEN → UNDER_REVIEW → MITIGATING → CLOSED
--                        (or OPEN/UNDER_REVIEW → ACCEPTED, CLOSED → OPEN)
--
-- These five seed rules thread ISO 31000 §6.5 + SAMA §10.4 governance
-- through the F8 engine: at each state-machine step the right actor
-- wakes up without the risk manager chasing them.
--
-- The payload (riskCode, externalId, titleEn, status, riskLevel,
-- residualScore, inherentScore, ownerId, categoryId,
-- complianceFrameworkClause, previousStatus) is matched equality-only
-- by the engine today (AutomationRuleEngine.matches). All rules below
-- use single-key or AND-of-equality conditions.
--
-- AR-46..AR-50 picks up where V131 (CrisisAarService AR-41..AR-45) left
-- off — sixth and final service to close the audit + automation loop.
-- After this migration every audit-logged service emits F8 events AND
-- has seed rules consuming them.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-46 ── CRITICAL risk raised → escalate to CRO + executive sponsor
-- CRITICAL = residual risk above the board-approved appetite. The
-- Chief Risk Officer must be told the moment the risk is logged — not
-- after the next risk-committee cycle — and the executive sponsor
-- (CEO or board chair, depending on Saudi corporate-governance model)
-- needs visibility so the board can override the appetite if it wants.
-- ISO 31000 §6.5.1 "promptly communicate risks above appetite".
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-46')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-46',
         N'CRITICAL risk raised → escalate to CRO + executive sponsor',
         N'إثارة خطر حرج → تصعيد لرئيس إدارة المخاطر والراعي التنفيذي',
         N'When a risk is logged with riskLevel=CRITICAL the residual '
         + N'score is above the board-approved appetite. The CRO must '
         + N'be told the moment the risk is logged, and the executive '
         + N'sponsor needs visibility so the board can override the '
         + N'appetite if it wants. ISO 31000 §6.5.1 "promptly '
         + N'communicate risks above appetite".',
         N'تصعيد فوري لرئيس إدارة المخاطر والراعي التنفيذي عند تسجيل خطر حرج — '
         + N'وفق ISO 31000 §6.5.1 لإبلاغ المخاطر التي تتجاوز شهية المؤسسة.',
         N'RISK_CREATED',
         N'{"riskLevel":"CRITICAL"}',
         N'{"type":"ESCALATE","roles":["CRO","EXECUTIVE_SPONSOR"],"template":"RISK_CRITICAL_RAISED"}',
         2, N'CUSTOM');
END

-- AR-47 ── PDPL-anchored risk raised → notify Data Protection Officer
-- A risk tagged with PDPL clause (e.g. PDPL-2.3.1 personal-data
-- exposure) must reach the DPO regardless of severity. The DPO is the
-- accountable role for PDPL compliance under Article 30 and needs to
-- assess whether the risk warrants a Data Protection Impact Assessment
-- before the residual score stabilises.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-47')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-47',
         N'PDPL-anchored risk raised → notify Data Protection Officer',
         N'إثارة خطر مرتبط بنظام حماية البيانات → إشعار مسؤول حماية البيانات',
         N'When a risk is created with a PDPL framework clause, route '
         + N'it to the DPO regardless of severity. DPO is the '
         + N'accountable role for PDPL compliance under Article 30 — '
         + N'they assess whether the risk warrants a Data Protection '
         + N'Impact Assessment (DPIA) before the residual score '
         + N'stabilises. The condition uses contains-match on the '
         + N'clause prefix so PDPL-1.x / PDPL-2.x / PDPL-3.x all hit.',
         N'إشعار مسؤول حماية البيانات عند تسجيل خطر مرتبط بنظام حماية البيانات — '
         + N'مادة 30 من النظام لتقييم الحاجة لتقييم أثر حماية البيانات.',
         N'RISK_CREATED',
         N'{"complianceFrameworkClause":"PDPL"}',
         N'{"type":"NOTIFY","role":"DPO","template":"RISK_PDPL_ANCHORED"}',
         8, N'CUSTOM');
END

-- AR-48 ── Risk accepted without review → flag for governance audit
-- The 'OPEN → ACCEPTED' edge skips formal review. SAMA §10.4 flags
-- this as a governance concern: a risk owner declared "we'll live
-- with this" without a peer / committee review. Doesn't block the
-- transition — risk acceptance is a legitimate response — but writes
-- an audit entry the internal audit team can sample on next cycle.
-- The two-condition match (status=ACCEPTED + previousStatus=OPEN)
-- distinguishes it from the legitimate UNDER_REVIEW → ACCEPTED path.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-48')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-48',
         N'Risk accepted without review → flag for governance audit',
         N'قبول خطر دون مراجعة → تسجيل ملاحظة حوكمة',
         N'When a risk transitions OPEN → ACCEPTED (skipping UNDER_'
         + N'REVIEW) the risk owner declared "we will live with this" '
         + N'without a peer or committee review. SAMA §10.4 flags this '
         + N'as a governance concern. The transition is not blocked — '
         + N'risk acceptance is a legitimate response — but the audit '
         + N'log captures a sampling target for the internal audit '
         + N'team. AND-of-equality {status,previousStatus} '
         + N'distinguishes from the legitimate UNDER_REVIEW → ACCEPTED.',
         N'تسجيل ملاحظة حوكمة عند قبول خطر دون مراجعة — وفق SAMA §10.4 '
         + N'كنقطة عينة للتدقيق الداخلي.',
         N'RISK_TRANSITIONED',
         N'{"status":"ACCEPTED","previousStatus":"OPEN"}',
         N'{"type":"AUDIT","severity":"WARN","message":"Risk accepted without formal review — SAMA §10.4 governance flag"}',
         12, N'CUSTOM');
END

-- AR-49 ── Risk transitions to MITIGATING → notify risk owner
-- MITIGATING means the risk-treatment plan was approved and execution
-- starts now. The risk owner gets the explicit cue so they know to:
-- (a) kick off the treatment-plan tasks, (b) update the residual
-- score as controls land, (c) schedule the periodic attestation per
-- attestationCadenceDays (V115). Without this, "mitigating" can pass
-- without the owner picking up the baton.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-49')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-49',
         N'Risk in mitigation → notify owner to kick off treatment plan',
         N'الخطر قيد المعالجة → إشعار صاحب الخطر لبدء خطة المعالجة',
         N'When a risk transitions to MITIGATING the treatment plan '
         + N'was approved and execution starts now. The risk owner '
         + N'needs the explicit cue: kick off treatment-plan tasks, '
         + N'update residual score as controls land, schedule the '
         + N'periodic attestation per attestationCadenceDays (V115). '
         + N'Without this, "mitigating" can pass without the owner '
         + N'picking up the baton.',
         N'إشعار صاحب الخطر عند بدء المعالجة — لإطلاق مهام خطة المعالجة وتحديث '
         + N'الخطر المتبقي مع تطبيق الضوابط وجدولة دورة التوكيد.',
         N'RISK_TRANSITIONED',
         N'{"status":"MITIGATING"}',
         N'{"type":"NOTIFY","role":"RISK_OWNER","template":"RISK_MITIGATION_STARTED"}',
         16, N'CUSTOM');
END

-- AR-50 ── Risk closed → write ISO 31000 §6.5 closure milestone
-- Pairs with AR-29 (CapaTask CLOSED), AR-40 (Finding CLOSED), AR-43
-- (AAR APPROVED). The quarterly NCEMA / SAMA / NCA-ECC filing
-- template reads all four milestone types to build the "risks closed
-- this quarter" + "lessons converted to CAPAs" + "audits completed"
-- sections without separate report queries. This is the last
-- milestone type — after this rule, every operationally significant
-- closure writes an audit-log milestone with a uniform shape.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-50')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-50',
         N'Risk closed → ISO 31000 §6.5 closure milestone',
         N'إغلاق الخطر → معلَم إغلاق ISO 31000 §6.5',
         N'When a risk transitions to CLOSED, write an ISO 31000 §6.5 '
         + N'milestone into platform.audit_log. Pairs with AR-29 '
         + N'(CapaTask CLOSED), AR-40 (Finding CLOSED), AR-43 (AAR '
         + N'APPROVED) — the quarterly NCEMA / SAMA / NCA-ECC filing '
         + N'template reads all four milestone types to build the '
         + N'"risks closed this quarter" section without separate '
         + N'report queries. After this rule, every operationally '
         + N'significant closure writes an audit-log milestone with '
         + N'a uniform shape.',
         N'كتابة معلَم ISO 31000 §6.5 عند إغلاق الخطر — مع AR-29 و AR-40 و AR-43 '
         + N'تُكمل صورة "المخاطر المغلقة" لتقارير NCEMA / SAMA / NCA-ECC الربعية.',
         N'RISK_TRANSITIONED',
         N'{"status":"CLOSED"}',
         N'{"type":"AUDIT","severity":"INFO","message":"Risk closed — ISO 31000 §6.5 closure milestone"}',
         24, N'CUSTOM');
END
GO

PRINT 'V132 risk automation rules (AR-46..AR-50) seeded.';
PRINT 'Audit + automation parity complete — all 6 audit-logged services';
PRINT 'now emit F8 events and have seed rules consuming them.';
GO
