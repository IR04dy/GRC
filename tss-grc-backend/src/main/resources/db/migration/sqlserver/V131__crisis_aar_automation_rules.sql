-- V131: F8 automation rules for CrisisAarService (bcm/cycle) events.
--
-- PR #53 normalized CrisisAarService into the canonical F8 shape. It
-- now publishes two DomainEvents on every mutation:
--
--   CRISIS_AAR_GENERATED   — AAR draft auto-built from a closed crisis
--   CRISIS_AAR_TRANSITIONED — DRAFT → UNDER_REVIEW → APPROVED
--                              → PUBLISHED (or APPROVED/PUBLISHED → RESCINDED)
--
-- These five seed rules wire the IIA Standard 2330 + ISO 22301 §10
-- "lessons learned must reach the executive" cadence through the F8
-- engine: at each state-machine step the right actor wakes up without
-- the crisis manager chasing them.
--
-- The payload (reportCode, crisisEventId, severity, status, titleEn,
-- previousStatus) is matched equality-only by the engine today
-- (AutomationRuleEngine.matches). All rules below use single-key or
-- AND-of-equality conditions.
--
-- AR-41..AR-45 picks up where V130 (FindingService AR-36..AR-40) left
-- off — fifth service to close the audit + automation loop. Only
-- RiskService remains (V132).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-41 ── SEV-1 AAR generated → notify executive sponsor immediately
-- SEV-1 = catastrophic. NCEMA AAR cadence puts the executive sponsor
-- (typically CEO or Crisis Steering Committee chair) in the loop the
-- moment the AAR is generated, not at the APPROVED step — they decide
-- whether the board needs an interim brief before the formal sign-off.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-41')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-41',
         N'SEV-1 AAR generated → notify executive sponsor',
         N'إنشاء تقرير ما بعد الحدث لأزمة كارثية → إشعار الراعي التنفيذي',
         N'When an AAR is generated for a SEV-1 (catastrophic) crisis '
         + N'event, the executive sponsor — usually the CEO or Crisis '
         + N'Steering Committee chair — must be told immediately so '
         + N'they can decide whether the board needs an interim brief '
         + N'before the formal AAR sign-off. NCEMA AAR cadence + ISO '
         + N'22301 §10.2 "top management commitment to corrective '
         + N'actions" both anchor here.',
         N'إشعار فوري للراعي التنفيذي عند إنشاء تقرير ما بعد الحدث لأزمة كارثية '
         + N'(درجة 1) — وفق دورة NCEMA و ISO 22301 §10.2 لإطلاع الإدارة '
         + N'العليا قبل اجتماع الاعتماد الرسمي.',
         N'CRISIS_AAR_GENERATED',
         N'{"severity":"1"}',
         N'{"type":"NOTIFY","role":"EXECUTIVE_SPONSOR","template":"AAR_SEV1_GENERATED"}',
         1, N'CUSTOM');
END

-- AR-42 ── AAR submitted for review → notify BCM champion
-- BCM champion (head of business continuity) is the gatekeeper between
-- DRAFT and APPROVED. They own the methodological review — did the
-- crisis manager capture all five root-cause dimensions (PEOPLE /
-- PROCESS / TECHNOLOGY / SUPPLIER / EXTERNAL) and are the corrective
-- actions actually linked to gaps / risks? Without this notification
-- the AAR sits at UNDER_REVIEW until someone notices.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-42')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-42',
         N'AAR submitted for review → notify BCM champion',
         N'تقديم تقرير ما بعد الحدث للمراجعة → إشعار قائد استمرارية الأعمال',
         N'When a crisis manager moves an AAR from DRAFT to '
         + N'UNDER_REVIEW, the BCM champion gets the methodological-'
         + N'review baton. They confirm all five root-cause dimensions '
         + N'(PEOPLE / PROCESS / TECHNOLOGY / SUPPLIER / EXTERNAL) are '
         + N'documented and that corrective actions point at real gaps. '
         + N'Without this nudge the AAR can sit unreviewed for weeks.',
         N'إشعار قائد استمرارية الأعمال عند انتقال التقرير من المسودة إلى المراجعة — '
         + N'لضمان توثيق جميع أبعاد التحليل الجذري والربط الفعلي بإجراءات تصحيحية.',
         N'CRISIS_AAR_TRANSITIONED',
         N'{"status":"UNDER_REVIEW","previousStatus":"DRAFT"}',
         N'{"type":"NOTIFY","role":"BCM_CHAMPION","template":"AAR_SUBMITTED_FOR_REVIEW"}',
         14, N'CUSTOM');
END

-- AR-43 ── AAR approved → write ISO 22301 §10 closure milestone
-- Sign-off is the moment the AAR's lessons become committed corrective
-- actions. Pairs with AR-29 (CapaTask CLOSED) and AR-40 (Finding
-- CLOSED): the quarterly NCEMA / SAMA / NCA-ECC filing template reads
-- all three milestone types to build the "AARs approved this quarter"
-- + "lessons converted to CAPAs" sections without separate queries.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-43')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-43',
         N'AAR approved → ISO 22301 §10 closure milestone',
         N'اعتماد التقرير → معلَم إغلاق ISO 22301 §10',
         N'When an AAR transitions to APPROVED, write an ISO 22301 '
         + N'§10.2 milestone into platform.audit_log. Pairs with '
         + N'AR-29 (CapaTask CLOSED) and AR-40 (Finding CLOSED) — the '
         + N'quarterly NCEMA / SAMA / NCA-ECC filing template reads '
         + N'all three milestone types to build the "AARs approved '
         + N'this quarter" + "lessons converted to CAPAs" sections '
         + N'without separate report queries.',
         N'كتابة معلَم ISO 22301 §10 عند اعتماد التقرير — مع AR-29 و AR-40 '
         + N'تُكمل صورة "التقارير المعتمدة" لتقارير NCEMA / SAMA / NCA-ECC الربعية.',
         N'CRISIS_AAR_TRANSITIONED',
         N'{"status":"APPROVED"}',
         N'{"type":"AUDIT","severity":"INFO","message":"AAR approved — ISO 22301 §10 lessons-learned milestone"}',
         18, N'CUSTOM');
END

-- AR-44 ── AAR published → notify crisis manager (post-publish handoff)
-- Once PUBLISHED the AAR is visible to all internal stakeholders. The
-- crisis manager needs the explicit cue so they know to: (a) trigger
-- the cross-functional walk-through session, (b) hand over CAPA
-- ownership to the action-owner role, (c) close the crisis room channel.
-- Without this, "published" can pass without the handoff happening.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-44')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-44',
         N'AAR published → notify crisis manager for handoff',
         N'نشر التقرير → إشعار مدير الأزمة لتسليم المهام',
         N'When an AAR reaches PUBLISHED status the crisis manager '
         + N'needs an explicit handoff cue: schedule the cross-'
         + N'functional walk-through, transfer CAPA ownership to the '
         + N'action-owner role, and close the crisis room channel. '
         + N'Without this nudge "published" can pass without the '
         + N'handoff happening, and the lessons stay in the report '
         + N'rather than in the people who need to act on them.',
         N'إشعار مدير الأزمة عند نشر التقرير — لجدولة جلسة المراجعة المشتركة '
         + N'وتسليم إجراءات المعالجة وإغلاق قناة غرفة الأزمة.',
         N'CRISIS_AAR_TRANSITIONED',
         N'{"status":"PUBLISHED"}',
         N'{"type":"NOTIFY","role":"CRISIS_MANAGER","template":"AAR_PUBLISHED_HANDOFF"}',
         15, N'CUSTOM');
END

-- AR-45 ── AAR rescinded → escalate to audit lead + executive sponsor
-- RESCINDED means a published AAR was withdrawn — material defect
-- found after publication. This is a high-severity event regardless of
-- crisis severity: it implies the original lessons-learned record was
-- wrong, downstream CAPAs may have been built on bad assumptions, and
-- the regulator (NCEMA / SAMA / NCA) may need a correction filing.
-- Escalate to both the audit lead (for an independent review of the
-- defect) and the executive sponsor (so the board hears it from us
-- first, not the regulator).
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-45')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-45',
         N'AAR rescinded → escalate to audit lead + executive sponsor',
         N'سحب التقرير → تصعيد لقائد التدقيق والراعي التنفيذي',
         N'When an AAR transitions to RESCINDED a published lessons-'
         + N'learned record has been withdrawn — material defect '
         + N'discovered after publication. Downstream CAPAs may have '
         + N'been built on bad assumptions; regulators (NCEMA / SAMA '
         + N'/ NCA) may need a correction filing. Escalate to the '
         + N'audit lead (independent review of the defect) and the '
         + N'executive sponsor (board hears it from us first, not from '
         + N'the regulator).',
         N'تصعيد عند سحب تقرير منشور — لقائد التدقيق للمراجعة المستقلة وللراعي '
         + N'التنفيذي لإبلاغ مجلس الإدارة قبل الجهات التنظيمية.',
         N'CRISIS_AAR_TRANSITIONED',
         N'{"status":"RESCINDED"}',
         N'{"type":"ESCALATE","roles":["AUDIT_LEAD","EXECUTIVE_SPONSOR"],"template":"AAR_RESCINDED_MATERIAL_DEFECT"}',
         3, N'CUSTOM');
END
GO

PRINT 'V131 crisis_aar automation rules (AR-41..AR-45) seeded.';
GO
