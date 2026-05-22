-- V151: F8 automation rules for SOC Operations events.
--
-- Rule set AR-116..AR-130 — wires the new socops events into the existing
-- F8 automation engine so a Saudi CISO actually sees something happen
-- after the V150 schema lands. Without these rules the SOC operations
-- tables fill up but never trigger downstream alerting.
--
-- Trigger events fired by the V150 services + the future schedule sweeps:
--   SECURITY_EVENT_INGESTED        — new SIEM event ingested
--   SECURITY_EVENT_CRITICAL_NEW    — new CRITICAL-severity SIEM event
--   SECURITY_EVENT_SLA_APPROACHING — sla_due_at - now() < 30 min
--   SECURITY_EVENT_SLA_BREACHED    — sla_due_at < now() AND status NOT in terminal
--   SECURITY_EVENT_TRIAGED         — analyst started triage
--   SECURITY_EVENT_RESOLVED        — closed (resolved or false-positive)
--   SCANNER_RUN_COMPLETED          — vulnerability scan completed
--   SCANNER_RUN_CRITICAL_FOUND     — scan completed and findings_critical > 0
--   IR_PLAYBOOK_INVOKED            — playbook clone or invocation
--   PENTEST_REPORT_DELIVERED       — pentest status → REPORT_DELIVERED
--   PENTEST_RETEST_DUE             — retest_due_at < now() + 7 days
--   CRYPTO_CERT_EXPIRING_30D       — sweep finds certs expiring in ≤30d
--   CRYPTO_CERT_EXPIRED            — sweep finds certs past valid_until
--   REGULATOR_EXAM_NOTIFIED        — new regulator examination row
--   REGULATOR_EXAM_RESPONSE_DUE    — response_due_at < now() + 14 days
--   SEASONAL_OVERLAY_ACTIVATED     — entered active window (Hajj/Ramadan etc.)
--   TI_ACTIONABLE                  — new threat-intel record with HIGH severity
--
-- Targets the demo tenant (org=1). Customers replicate via the V150 seed
-- pattern (organisation_licenses entitlement → rule clone).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-116 ── New CRITICAL SIEM event → page CISO + SOC manager immediately
-- NCA ECC-2 mandates 1-hour acknowledgement for critical incidents. A
-- silent queue is the failure mode SAMA examiners ask about — page out
-- the moment a CRITICAL alert lands so the clock starts visibly.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-116')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-116',
         N'CRITICAL SIEM event → page CISO + SOC manager',
         N'حدث SIEM حرج → تنبيه فوري لرئيس أمن المعلومات ومدير المركز',
         N'NCA ECC-2 mandates a 1-hour acknowledgement window for '
         + N'CRITICAL incidents. A silent SOC queue is the failure mode '
         + N'SAMA examiners ask about — page the CISO and SOC manager '
         + N'the moment a CRITICAL alert lands so the clock starts visibly.',
         N'تشترط ECC-2 الإقرار خلال ساعة من الحدث الحرج. '
         + N'يرسل التنبيه فور وصول الحدث لرئيس أمن المعلومات ومدير المركز.',
         N'SECURITY_EVENT_CRITICAL_NEW',
         N'{}',
         N'{"type":"NOTIFY","roles":["CISO","SOC_MANAGER","IR_LEAD"],"channels":["EMAIL","SMS","INBOX"],"template":"SOCOPS_CRITICAL_EVENT"}',
         1, N'CUSTOM');
END

-- AR-117 ── Event SLA approaching → escalate to SOC manager
-- 30 minutes before SLA expiry — last-chance window to either resolve
-- the event or formally extend with manager approval. Without this
-- nudge the queue silently breaches and shows up red on the dashboard.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-117')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-117',
         N'SIEM event SLA approaching → escalate to SOC manager',
         N'اقتراب SLA حدث SIEM → تصعيد لمدير المركز',
         N'30 minutes before the NCA-mandated SLA expiry, escalate to '
         + N'the SOC manager — last-chance window to either resolve or '
         + N'formally extend with management approval.',
         N'قبل ٣٠ دقيقة من انتهاء SLA يصعد الحدث لمدير المركز — فرصة أخيرة '
         + N'لإغلاق الحدث أو تمديده بموافقة الإدارة.',
         N'SECURITY_EVENT_SLA_APPROACHING',
         N'{}',
         N'{"type":"ESCALATE","roles":["SOC_MANAGER","IR_LEAD"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_SLA_WARNING"}',
         2, N'CUSTOM');
END

-- AR-118 ── Event SLA breached → CISO + audit-log flag
-- An SLA breach is itself an audit finding under NCA ECC-2 and SAMA CSF.
-- Page the CISO + flag in the audit log so the next regulator examination
-- can quantify the breach rate.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-118')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-118',
         N'SIEM event SLA BREACHED → CISO + audit flag',
         N'تجاوز SLA لحدث SIEM → CISO وعلامة تدقيق',
         N'An SLA breach is itself an audit finding under NCA ECC-2 and '
         + N'SAMA CSF. Page the CISO and flag in the audit log so the '
         + N'next regulator examination can quantify the breach rate.',
         N'تجاوز SLA يُسجل كملاحظة تدقيق وفق ECC-2 وSAMA CSF. '
         + N'يُبلَّغ CISO وتُسجَّل العلامة لاستخدامها في تفتيش الجهة التنظيمية.',
         N'SECURITY_EVENT_SLA_BREACHED',
         N'{}',
         N'{"type":"FLAG","severity":"HIGH","roles":["CISO","AUDIT_COMMITTEE"],"template":"SOCOPS_SLA_BREACH"}',
         1, N'CUSTOM');
END

-- AR-119 ── Scanner run found CRITICAL vulns → file vulnerability records
-- New CRITICAL finding from a scanner run = potential exposure now. Auto
-- create a vulnerability record so the existing cyber lifecycle picks it
-- up (NCA mandates 30-day remediation for CRITICAL).
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-119')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-119',
         N'Scanner found CRITICAL vulnerabilities → notify cyber team',
         N'الماسح وجد ثغرات حرجة → إشعار فريق الأمن السيبراني',
         N'New CRITICAL findings from a vulnerability scan trigger the '
         + N'NCA 30-day remediation clock. Notify the cyber team and '
         + N'cross-link to the existing vulnerability lifecycle.',
         N'الثغرات الحرجة الجديدة تبدأ مهلة المعالجة (٣٠ يوماً) — '
         + N'يُنبَّه فريق السيبراني وتُربَط بمنظومة دورة الثغرات.',
         N'SCANNER_RUN_CRITICAL_FOUND',
         N'{}',
         N'{"type":"NOTIFY","roles":["CYBER_LEAD","CISO","ASSET_OWNER"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_SCANNER_CRITICAL"}',
         2, N'CUSTOM');
END

-- AR-120 ── New high-severity threat intel → SOC review queue
-- HIGH-confidence + HIGH-severity TI bulletins (Saudi-CERT, NCA, FS-ISAC)
-- need same-day SOC review so we either block the IOC or document why
-- it doesn't apply to our environment.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-120')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-120',
         N'High-severity threat intel → SOC review queue',
         N'استخبارات تهديد عالية الخطورة → قائمة مراجعة المركز',
         N'HIGH-severity TI (Saudi-CERT, NCA, FS-ISAC) needs same-day SOC '
         + N'review — either block the IOC or document why it doesn''t '
         + N'apply to our environment.',
         N'استخبارات التهديد عالية الخطورة تحتاج مراجعة في نفس اليوم — '
         + N'إما حظر المؤشر أو توثيق عدم انطباقه على بيئتنا.',
         N'TI_ACTIONABLE',
         N'{}',
         N'{"type":"NOTIFY","roles":["SOC_ANALYST_L2","SOC_MANAGER"],"channels":["INBOX"],"template":"SOCOPS_TI_ACTIONABLE"}',
         3, N'CUSTOM');
END

-- AR-121 ── Pentest report delivered → remediation owner + retest scheduler
-- A delivered pentest report kicks off the 90-day remediation clock.
-- Owner gets the findings; retest_due_at gets seeded on the engagement.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-121')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-121',
         N'Pentest report delivered → remediation owner notified',
         N'تسليم تقرير اختبار الاختراق → إشعار مالك المعالجة',
         N'A delivered pentest report starts the remediation clock. '
         + N'Owner gets the findings; retest cycle gets seeded.',
         N'تسليم تقرير الاختبار يبدأ مهلة المعالجة — يُبلَّغ المالك وتُجَدول إعادة الاختبار.',
         N'PENTEST_REPORT_DELIVERED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CISO","CYBER_LEAD","ASSET_OWNER"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_PENTEST_REPORT"}',
         3, N'CUSTOM');
END

-- AR-122 ── Pentest retest due soon → CISO + remediation owner
-- SAMA examiners specifically ask "what was the gap between pentest
-- delivery and retest closure?" If the retest_due_at is approaching and
-- the pentest is still in REMEDIATION, surface to the CISO.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-122')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-122',
         N'Pentest retest due → CISO escalation',
         N'استحقاق إعادة اختبار الاختراق → تصعيد لـ CISO',
         N'SAMA examiners ask specifically about the gap between pentest '
         + N'delivery and retest closure. Surface ageing engagements to '
         + N'the CISO before they become regulator findings.',
         N'يسأل مفتشو ساما عن الفجوة بين تسليم التقرير وإعادة الاختبار — '
         + N'يُصعَّد للـ CISO قبل أن تصبح ملاحظة تنظيمية.',
         N'PENTEST_RETEST_DUE',
         N'{}',
         N'{"type":"ESCALATE","roles":["CISO","CYBER_LEAD"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_PENTEST_RETEST"}',
         2, N'CUSTOM');
END

-- AR-123 ── Cert expiring in ≤30d → owner + NOC immediate notify
-- Expired TLS / signing certs are an automatic NCA + SAMA control
-- deficiency — and they cause customer-visible outages. 30-day window
-- gives the owner time to procure and rotate before the failure.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-123')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-123',
         N'Cert expiring in ≤30d → owner + NOC',
         N'انتهاء شهادة خلال ٣٠ يوماً → المالك ومركز التشغيل',
         N'Expired certs are an automatic NCA + SAMA control deficiency '
         + N'and cause customer outages. 30-day window gives the owner '
         + N'time to procure and rotate before failure.',
         N'انتهاء الشهادات يُعَدّ خللاً ضابطاً تلقائياً ويسبب انقطاعات — '
         + N'نافذة ٣٠ يوماً تكفي للتجديد قبل العطل.',
         N'CRYPTO_CERT_EXPIRING_30D',
         N'{}',
         N'{"type":"NOTIFY","roles":["CRYPTO_OWNER","NOC","CISO"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_CERT_EXPIRING"}',
         2, N'CUSTOM');
END

-- AR-124 ── Cert already expired → CISO + audit-log flag
-- Cert past valid_until is a live control gap. Page CISO + flag for
-- audit-log so the next external audit can show the dwell time.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-124')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-124',
         N'Cert EXPIRED → CISO + audit flag',
         N'انتهت شهادة → CISO وعلامة تدقيق',
         N'Cert past valid_until is a live control gap. Page CISO and '
         + N'flag for audit log so the next external audit can show the '
         + N'dwell time.',
         N'الشهادة المنتهية فجوة ضابط حية — يُبلَّغ CISO وتُسجَّل '
         + N'العلامة لإظهار مدة البقاء في التدقيق التالي.',
         N'CRYPTO_CERT_EXPIRED',
         N'{}',
         N'{"type":"FLAG","severity":"HIGH","roles":["CISO","AUDIT_COMMITTEE","CRYPTO_OWNER"],"template":"SOCOPS_CERT_EXPIRED"}',
         1, N'CUSTOM');
END

-- AR-125 ── Regulator notified an examination → CISO + Compliance + Legal
-- The clock starts the moment the regulator notifies. Three roles
-- must engage same-day: CISO (technical scope), Compliance Officer
-- (regulator liaison), Legal Counsel (privilege management).
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-125')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-125',
         N'Regulator examination notified → CISO + Compliance + Legal',
         N'إخطار تفتيش جهة تنظيمية → CISO والامتثال والقانونية',
         N'The clock starts when the regulator notifies. CISO (technical '
         + N'scope), Compliance Officer (regulator liaison), and Legal '
         + N'Counsel (privilege management) all engage same-day.',
         N'تبدأ المهلة فور إخطار الجهة التنظيمية — يتدخل CISO والامتثال والقانونية في نفس اليوم.',
         N'REGULATOR_EXAM_NOTIFIED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CISO","COMPLIANCE_OFFICER","LEGAL_COUNSEL","CEO"],"channels":["EMAIL","SMS","INBOX"],"template":"SOCOPS_REGULATOR_NOTIFIED"}',
         1, N'CUSTOM');
END

-- AR-126 ── Regulator response due ≤14d → escalate to Compliance + CISO
-- A late response to a regulator is itself a finding. 14-day warning
-- forces an internal review before the formal submission.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-126')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-126',
         N'Regulator response due ≤14d → escalate',
         N'استحقاق رد الجهة التنظيمية خلال ١٤ يوماً → تصعيد',
         N'Late response to a regulator is itself a finding. 14-day '
         + N'warning forces internal review before formal submission.',
         N'تأخر الرد على الجهة التنظيمية يُعَدّ ملاحظة بحد ذاته — '
         + N'تنبيه ١٤ يوماً يُلزِم بالمراجعة قبل التقديم الرسمي.',
         N'REGULATOR_EXAM_RESPONSE_DUE',
         N'{}',
         N'{"type":"ESCALATE","roles":["COMPLIANCE_OFFICER","CISO","CEO"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_REGULATOR_RESPONSE_DUE"}',
         1, N'CUSTOM');
END

-- AR-127 ── Saudi seasonal overlay activated → CISO + risk team
-- Hajj / Umrah / Ramadan / National Day windows reshape the threat
-- profile. The F8 scheduled job activates the overlay; the rule fires
-- so the CISO and risk team confirm tightened KRIs are in place.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-127')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-127',
         N'Seasonal overlay activated → CISO + risk team',
         N'تفعيل نافذة موسمية → CISO وفريق المخاطر',
         N'Hajj / Umrah / Ramadan / National Day windows reshape the '
         + N'threat profile. Overlay activation triggers a CISO + risk '
         + N'team confirmation that tightened KRIs are in place.',
         N'الفترات الموسمية (الحج / العمرة / رمضان / اليوم الوطني) تغير ملف التهديد — '
         + N'يؤكد CISO وفريق المخاطر شد عتبات KRI.',
         N'SEASONAL_OVERLAY_ACTIVATED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CISO","RISK_MANAGER","SOC_MANAGER"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_SEASONAL_ACTIVATED"}',
         2, N'CUSTOM');
END

-- AR-128 ── IR playbook invoked → SOC manager visibility + AAR trigger
-- Invoking an IR playbook means a real incident is in progress. Manager
-- visibility + auto-create an AAR row in the BCM module so the lessons-
-- learned phase doesn't get skipped after the heat of response cools.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-128')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-128',
         N'IR playbook invoked → SOC manager + AAR scheduled',
         N'تفعيل دليل الاستجابة → مدير المركز وجدولة AAR',
         N'Invoking a playbook means a real incident is in progress. '
         + N'Manager visibility + auto-create an AAR row in BCM so the '
         + N'lessons-learned phase doesn''t get skipped.',
         N'تفعيل الدليل يعني وجود حادث فعلي — يُبلَّغ المدير وتُنشأ '
         + N'مسودة تقرير ما بعد الحدث تلقائياً.',
         N'IR_PLAYBOOK_INVOKED',
         N'{}',
         N'{"type":"CREATE_RECORD","target":"bcm_aar","template":"FROM_PLAYBOOK_INVOCATION","roles":["SOC_MANAGER","IR_LEAD"]}',
         3, N'CUSTOM');
END

-- AR-129 ── Awareness campaign repeat-offender → HR escalation
-- Users who click on phishing simulations more than once need a
-- targeted intervention. NCA-aligned awareness programmes track
-- repeat-offender count and escalate to HR for mandatory training
-- before disciplinary review.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-129')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-129',
         N'Awareness repeat-offender → HR escalation',
         N'متكرر فشل التوعية → تصعيد للموارد البشرية',
         N'Users who click phishing simulations more than once need a '
         + N'targeted intervention. NCA-aligned programmes track repeat '
         + N'offenders and escalate to HR before disciplinary review.',
         N'المستخدمون الذين ينقرون على التصيد المحاكي أكثر من مرة '
         + N'يحتاجون تدخلاً مستهدفاً — يصعد للموارد البشرية قبل المراجعة التأديبية.',
         N'AWARENESS_REPEAT_OFFENDER',
         N'{}',
         N'{"type":"NOTIFY","roles":["HR_LEAD","CISO","LINE_MANAGER"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_AWARENESS_REPEAT"}',
         3, N'CUSTOM');
END

-- AR-130 ── SIEM event resolved → record MTTR + close audit timeline
-- Terminal-state event triggers MTTR calculation update + audit log
-- closure entry. Dashboard MTTR metric refreshes on next request.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-130')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-130',
         N'SIEM event resolved → record MTTR + audit-log closure',
         N'حدث SIEM مغلق → تسجيل MTTR وإغلاق سجل التدقيق',
         N'Terminal-state event triggers MTTR calculation update + '
         + N'audit-log closure entry. Dashboard MTTR metric refreshes '
         + N'on next request.',
         N'إغلاق الحدث يحدّث متوسط زمن الاستجابة (MTTR) '
         + N'ويُسجِّل إدخال إغلاق في سجل التدقيق.',
         N'SECURITY_EVENT_RESOLVED',
         N'{}',
         N'{"type":"FLAG","severity":"INFO","template":"SOCOPS_EVENT_RESOLVED"}',
         5, N'CUSTOM');
END
GO
