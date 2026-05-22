-- V153: F8 automation rules for the Tier-2 + Tier-3 SOC operations expansion
-- (V152 schema). Rule set AR-131..AR-145 — wires the new OT / IAM JML /
-- AppSec / DLP / Cyber-Insurance / Brand-Monitoring / Forensics / M&A
-- DD events into the existing F8 engine.
--
-- Trigger events fired by the V152 services + scheduled sweeps:
--   OT_REMOTE_ACCESS_ENABLED      — OT asset toggled remote_access_allowed
--   IAM_SOD_VIOLATION             — JML row with sod_conflict_flag = 1
--   IAM_PRIVILEGED_REVIEW_DUE     — privileged role next_review_at ≤ 30d
--   IAM_JML_PRIVILEGED_COMPLETE   — JML row COMPLETED for privileged role
--   APPSEC_CRITICAL_FINDING       — appsec_finding ingested as CRITICAL
--   APPSEC_SLA_BREACHED           — finding past nca_sla_due_at, not closed
--   DLP_CRITICAL_UNREVIEWED       — DLP event CRITICAL severity + >24h
--   DLP_TOP_SECRET_BLOCKED        — DLP event blocked TOP_SECRET data
--   CYBER_INSURANCE_RENEWAL_90D   — policy renewal_at within 90d
--   CYBER_INSURANCE_RENEWAL_30D   — policy renewal_at within 30d
--   BRAND_CRITICAL_ALERT          — brand_monitoring severity CRITICAL
--   FORENSICS_INTEGRITY_LOST      — forensics_artefact integrity_status COMPROMISED
--   FORENSICS_HOLD_DISPOSAL_DUE   — legal hold disposal_due_at ≤ 30d
--   MA_DD_PHASE_TRANSITION        — ma_due_diligence phase advanced
--   OT_PATCH_WINDOW_APPROACHING   — OT next maintenance window ≤ 14d
--
-- Targets the demo tenant (org=1). Customers replicate via the V150 seed
-- pattern (organisation_licenses entitlement → rule clone).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-131 ── OT remote access enabled → CISO + OT lead notify
-- Saudi OTCC + Aramco standards treat OT remote sessions as a high-risk
-- event. Every toggle must be visible to the CISO and OT lead so the
-- session can be paired with an authorised maintenance window.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-131')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-131',
         N'OT remote access enabled → CISO + OT lead',
         N'تفعيل وصول عن بُعد للتشغيل → CISO وقائد التشغيل',
         N'OTCC and Aramco standards treat OT remote sessions as a '
         + N'high-risk event. Every toggle must be visible to CISO + OT '
         + N'lead so the session is paired with an authorised window.',
         N'تتعامل OTCC ومعايير أرامكو مع جلسات الوصول عن بُعد للتشغيل كحدث عالي الخطورة. '
         + N'كل تفعيل يجب أن يكون مرئياً لـ CISO وقائد التشغيل ومرتبطاً بنافذة صيانة معتمدة.',
         N'OT_REMOTE_ACCESS_ENABLED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CISO","OT_LEAD","SOC_MANAGER"],"channels":["EMAIL","SMS","INBOX"],"template":"SOCOPS_OT_REMOTE_ACCESS"}',
         1, N'CUSTOM');
END

-- AR-132 ── IAM SoD violation detected → CISO + audit committee (P1)
-- Segregation of Duties conflicts are a SAMA examination favourite. Page
-- CISO + audit committee the moment one is detected so the finding is
-- triaged before the next regulator scan.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-132')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-132',
         N'IAM SoD violation detected → CISO + audit committee',
         N'مخالفة فصل المهام → CISO ولجنة التدقيق',
         N'Segregation of Duties conflicts are a SAMA examination '
         + N'favourite. Page CISO + audit committee the moment one is '
         + N'detected so the finding is triaged before the next scan.',
         N'تعارض فصل المهام من أبرز ملاحظات تفتيش ساما — '
         + N'يُبلَّغ CISO ولجنة التدقيق فور الاكتشاف لمعالجته قبل التفتيش القادم.',
         N'IAM_SOD_VIOLATION',
         N'{}',
         N'{"type":"FLAG","severity":"HIGH","roles":["CISO","AUDIT_COMMITTEE","IAM_LEAD"],"template":"SOCOPS_IAM_SOD"}',
         1, N'CUSTOM');
END

-- AR-133 ── Privileged access review due ≤30d → owner + manager
-- SAMA CSF and NCA ECC-2 mandate quarterly privileged-access reviews.
-- 30-day warning gives the owner + line manager time to certify or
-- revoke before the period lapses.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-133')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-133',
         N'Privileged access review due ≤30d → owner + manager',
         N'مراجعة الصلاحيات الحرجة خلال ٣٠ يوماً → المالك والمدير',
         N'SAMA CSF and NCA ECC-2 mandate quarterly privileged-access '
         + N'reviews. 30-day warning gives owner + line manager time '
         + N'to certify or revoke before the period lapses.',
         N'تشترط ساما والـECC-2 مراجعة دورية ربع سنوية للصلاحيات الحرجة — '
         + N'تنبيه ٣٠ يوماً يكفي للتصديق أو الإلغاء قبل انتهاء الفترة.',
         N'IAM_PRIVILEGED_REVIEW_DUE',
         N'{}',
         N'{"type":"NOTIFY","roles":["IAM_LEAD","LINE_MANAGER","CISO"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_IAM_REVIEW_DUE"}',
         2, N'CUSTOM');
END

-- AR-134 ── AppSec CRITICAL finding → APPSEC lead + 30-day SLA clock
-- New CRITICAL SAST/DAST/IAST/SCA finding starts the NCA-aligned 30-day
-- remediation SLA. The lead gets notified + the SLA timer is started
-- via the nca_sla_due_at column at ingest time.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-134')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-134',
         N'AppSec CRITICAL finding → APPSEC lead + SLA clock',
         N'ثغرة تطبيقات حرجة → قائد أمن التطبيقات وبدء SLA',
         N'New CRITICAL SAST/DAST/IAST/SCA finding starts the NCA-aligned '
         + N'30-day remediation SLA. The lead is notified and the SLA '
         + N'timer (nca_sla_due_at) is started at ingest.',
         N'ثغرة CRITICAL جديدة في الكود تبدأ مهلة المعالجة المتوائمة مع NCA (٣٠ يوماً). '
         + N'يُبلَّغ قائد أمن التطبيقات وتبدأ مهلة SLA منذ الاستلام.',
         N'APPSEC_CRITICAL_FINDING',
         N'{}',
         N'{"type":"NOTIFY","roles":["APPSEC_LEAD","CISO","DEV_LEAD"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_APPSEC_CRITICAL"}',
         1, N'CUSTOM');
END

-- AR-135 ── AppSec finding SLA breached → CISO + audit-log flag
-- A breached AppSec SLA is a documented control deficiency. Page CISO
-- + flag for audit log so external auditors can quantify dwell time.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-135')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-135',
         N'AppSec SLA BREACHED → CISO + audit flag',
         N'تجاوز SLA أمن التطبيقات → CISO وعلامة تدقيق',
         N'A breached AppSec SLA is a documented control deficiency. '
         + N'Page CISO and flag for audit log so external auditors can '
         + N'quantify dwell time on critical findings.',
         N'تجاوز SLA أمن التطبيقات يُعَدّ خللاً ضابطاً موثقاً — '
         + N'يُبلَّغ CISO وتُسجَّل العلامة لقياس مدة البقاء في التدقيق الخارجي.',
         N'APPSEC_SLA_BREACHED',
         N'{}',
         N'{"type":"FLAG","severity":"HIGH","roles":["CISO","APPSEC_LEAD","AUDIT_COMMITTEE"],"template":"SOCOPS_APPSEC_BREACH"}',
         1, N'CUSTOM');
END

-- AR-136 ── DLP CRITICAL event unreviewed >24h → escalate to DLP lead
-- DLP events that sit unreviewed for >24h indicate either an overloaded
-- analyst pool or a process gap. Escalate to the DLP reviewer lead so
-- the queue stays current.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-136')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-136',
         N'DLP CRITICAL event unreviewed >24h → escalate',
         N'حدث DLP حرج بدون مراجعة >٢٤س → تصعيد',
         N'DLP events unreviewed for >24h indicate either an overloaded '
         + N'analyst pool or a process gap. Escalate to the DLP lead.',
         N'أحداث DLP الحرجة بدون مراجعة لأكثر من ٢٤ ساعة تشير لإغراق المحللين أو فجوة عملية — '
         + N'تُصعَّد لقائد DLP لإبقاء القائمة محدّثة.',
         N'DLP_CRITICAL_UNREVIEWED',
         N'{}',
         N'{"type":"ESCALATE","roles":["DLP_REVIEWER","CISO","SOC_MANAGER"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_DLP_STALE"}',
         2, N'CUSTOM');
END

-- AR-137 ── Cyber insurance renewal ≤90d → CISO + risk team
-- Cyber insurance renewals require ~60-90 days of broker prep:
-- updated controls evidence, claims history, network-security
-- questionnaire. 90-day warning lets the risk team start early.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-137')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-137',
         N'Cyber insurance renewal ≤90d → CISO + risk team',
         N'تجديد التأمين السيبراني خلال ٩٠ يوماً → CISO وفريق المخاطر',
         N'Cyber insurance renewals require ~90 days of broker prep: '
         + N'updated controls evidence, claims history, network-security '
         + N'questionnaire. 90-day warning lets the risk team start early.',
         N'يستغرق تجديد التأمين السيبراني نحو ٩٠ يوماً من إعداد الوسيط (دليل الضوابط، سجل المطالبات، استبيان الأمن). '
         + N'تنبيه ٩٠ يوماً يسمح لفريق المخاطر بالبدء مبكراً.',
         N'CYBER_INSURANCE_RENEWAL_90D',
         N'{}',
         N'{"type":"NOTIFY","roles":["CISO","RISK_MANAGER","FINANCE_LEAD"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_INSURANCE_RENEWAL_90D"}',
         3, N'CUSTOM');
END

-- AR-138 ── Cyber insurance renewal ≤30d → escalate to CFO + CEO
-- 30 days out is the last realistic window to negotiate coverage. If
-- the renewal is still open, page the CFO + CEO so the executive
-- committee can prioritise broker engagement.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-138')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-138',
         N'Cyber insurance renewal ≤30d → escalate to CFO + CEO',
         N'تجديد التأمين السيبراني خلال ٣٠ يوماً → تصعيد للـCFO وCEO',
         N'30 days out is the last realistic window to negotiate '
         + N'coverage. Escalate to CFO + CEO so the executive committee '
         + N'can prioritise broker engagement.',
         N'٣٠ يوماً هي النافذة الواقعية الأخيرة للتفاوض — '
         + N'يُصعَّد للـ CFO وCEO لإعطاء الأولوية للتعاقد مع الوسيط.',
         N'CYBER_INSURANCE_RENEWAL_30D',
         N'{}',
         N'{"type":"ESCALATE","roles":["CFO","CEO","CISO","RISK_MANAGER"],"channels":["EMAIL","SMS","INBOX"],"template":"SOCOPS_INSURANCE_RENEWAL_30D"}',
         1, N'CUSTOM');
END

-- AR-139 ── Brand CRITICAL alert → SOC + legal (takedown path)
-- Phishing kit hosting our brand, leaked credentials on Telegram,
-- spoof domain registered — CRITICAL brand alerts need same-day SOC
-- review and legal counsel to drive the takedown request.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-139')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-139',
         N'Brand CRITICAL alert → SOC + legal (takedown)',
         N'تنبيه حرج للعلامة → المركز والقانونية (إزالة)',
         N'Phishing kits hosting our brand, leaked credentials on '
         + N'Telegram, spoof domains — CRITICAL brand alerts need '
         + N'same-day SOC review and legal counsel to drive takedown.',
         N'مجموعات تصيد باسم العلامة، تسريبات بيانات على Telegram، نطاقات مزيفة — '
         + N'التنبيهات الحرجة تحتاج مراجعة فورية من المركز والقانونية لإطلاق طلب الإزالة.',
         N'BRAND_CRITICAL_ALERT',
         N'{}',
         N'{"type":"NOTIFY","roles":["SECURITY_ANALYST","CISO","LEGAL_COUNSEL","BRAND_MONITORING"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_BRAND_CRITICAL"}',
         1, N'CUSTOM');
END

-- AR-140 ── Forensics integrity COMPROMISED → CISO + legal counsel (P1)
-- Chain-of-custody integrity break is the single most damaging finding
-- in a forensics review — evidence becomes inadmissible. Page CISO +
-- legal counsel immediately so the impact can be assessed before any
-- regulator or court action.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-140')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-140',
         N'Forensics integrity COMPROMISED → CISO + legal (P1)',
         N'سلامة الأدلة الجنائية مخترقة → CISO والقانونية (P1)',
         N'Chain-of-custody integrity break is the single most damaging '
         + N'finding in a forensics review — evidence becomes '
         + N'inadmissible. Page CISO + legal counsel immediately.',
         N'كسر سلسلة الحفظ الجنائية هو أخطر ملاحظة في المراجعة — '
         + N'تصبح الأدلة غير مقبولة قضائياً. يُبلَّغ CISO والقانونية فوراً.',
         N'FORENSICS_INTEGRITY_LOST',
         N'{}',
         N'{"type":"FLAG","severity":"CRITICAL","roles":["CISO","LEGAL_COUNSEL","FORENSICS_LEAD","AUDIT_COMMITTEE"],"template":"SOCOPS_FORENSICS_INTEGRITY"}',
         1, N'CUSTOM');
END

-- AR-141 ── Forensics legal hold approaching disposal date → legal counsel
-- Items under legal hold approaching their disposal date need an
-- explicit "release or extend" decision from legal counsel. Without
-- the warning, sensitive evidence is either prematurely destroyed or
-- retained indefinitely.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-141')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-141',
         N'Legal hold disposal ≤30d → legal counsel review',
         N'إغلاق حجز قانوني خلال ٣٠ يوماً → مراجعة القانونية',
         N'Items under legal hold approaching disposal need an explicit '
         + N'"release or extend" decision from legal counsel. Otherwise '
         + N'evidence is prematurely destroyed or retained indefinitely.',
         N'الأدلة تحت الحجز القانوني المقتربة من تاريخ الإتلاف تحتاج قراراً صريحاً (إفراج أو تمديد) من القانونية — '
         + N'وإلا يُتلَف الدليل قبل أوانه أو يُحتفظ به بلا حدود.',
         N'FORENSICS_HOLD_DISPOSAL_DUE',
         N'{}',
         N'{"type":"NOTIFY","roles":["LEGAL_COUNSEL","FORENSICS_LEAD","CISO"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_HOLD_DISPOSAL"}',
         2, N'CUSTOM');
END

-- AR-142 ── M&A DD phase transition → MA_LEAD + CISO visibility
-- M&A due diligence phase advances (PLANNING → DISCOVERY → ASSESSMENT
-- → FINDINGS → INTEGRATION) need MA lead + CISO sign-off so the
-- security posture verdict reaches the deal team at every milestone.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-142')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-142',
         N'M&A DD phase transition → MA lead + CISO',
         N'تحول مرحلة العناية الواجبة (M&A) → MA Lead وCISO',
         N'M&A due diligence phase advances need MA lead + CISO '
         + N'sign-off so the security posture verdict reaches the deal '
         + N'team at every milestone (PLANNING → … → INTEGRATION).',
         N'تحولات مراحل العناية الواجبة تحتاج اعتماد MA Lead وCISO لإبلاغ فريق الصفقة بنتيجة الوضع الأمني في كل مرحلة.',
         N'MA_DD_PHASE_TRANSITION',
         N'{}',
         N'{"type":"NOTIFY","roles":["MA_LEAD","CISO","RISK_MANAGER","LEGAL_COUNSEL"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_MA_PHASE"}',
         3, N'CUSTOM');
END

-- AR-143 ── OT patching window approaching ≤14d → OT lead + asset owner
-- OT patching windows are scarce (often quarterly during shutdowns).
-- 14-day warning gives the OT lead time to prepare change tickets,
-- rollback plans, and stakeholder approvals.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-143')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-143',
         N'OT patching window ≤14d → OT lead + asset owner',
         N'نافذة ترقيع التشغيل خلال ١٤ يوماً → قائد التشغيل ومالك الأصل',
         N'OT patching windows are scarce — often quarterly during '
         + N'shutdowns. 14-day warning gives the OT lead time to '
         + N'prepare change tickets, rollback plans, and approvals.',
         N'نوافذ ترقيع أنظمة التشغيل نادرة وعادة ربع سنوية أثناء التوقف — '
         + N'تنبيه ١٤ يوماً يكفي لإعداد طلبات التغيير وخطط التراجع والاعتمادات.',
         N'OT_PATCH_WINDOW_APPROACHING',
         N'{}',
         N'{"type":"NOTIFY","roles":["OT_LEAD","ASSET_OWNER","CHANGE_MANAGER"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_OT_PATCH_WINDOW"}',
         3, N'CUSTOM');
END

-- AR-144 ── IAM JML COMPLETED for privileged role → manager review queue
-- A completed joiner-mover-leaver event for a privileged role needs
-- the line manager to verify entitlements before the change becomes
-- "business as usual." Adds a row to the manager's review queue.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-144')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-144',
         N'IAM JML COMPLETED (privileged) → manager review',
         N'إتمام JML للصلاحيات الحرجة → مراجعة المدير',
         N'A completed JML event for a privileged role needs the line '
         + N'manager to verify entitlements before the change becomes '
         + N'"business as usual." Adds to the manager review queue.',
         N'إتمام حدث JML لصلاحية حرجة يستوجب من المدير المباشر التحقق من الاستحقاقات قبل اعتبار التغيير ساري المفعول — '
         + N'يُضاف لقائمة مراجعات المدير.',
         N'IAM_JML_PRIVILEGED_COMPLETE',
         N'{}',
         N'{"type":"NOTIFY","roles":["LINE_MANAGER","IAM_LEAD","CISO"],"channels":["EMAIL","INBOX"],"template":"SOCOPS_JML_REVIEW"}',
         2, N'CUSTOM');
END

-- AR-145 ── DLP TOP_SECRET data blocked → CISO + DPO (PDPL Article 5)
-- A DLP block on TOP_SECRET (or PDPL "Sensitive Personal Data") is a
-- live PDPL Article 5 / NDC top-tier event. Page CISO + DPO immediately
-- so the incident is logged within the 72-hour SDAIA notification window.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-145')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-145',
         N'DLP TOP_SECRET blocked → CISO + DPO (PDPL Art. 5)',
         N'حظر DLP لبيانات سرية للغاية → CISO وDPO (PDPL م.٥)',
         N'A DLP block on TOP_SECRET (or PDPL Sensitive Personal Data) '
         + N'is a live PDPL Article 5 / NDC top-tier event. Page CISO '
         + N'+ DPO so the incident is logged within the 72-hour SDAIA '
         + N'notification window.',
         N'حظر DLP للبيانات السرية للغاية (أو الشخصية الحساسة PDPL) حدث حي ضمن المادة ٥ من PDPL وأعلى تصنيف NDC — '
         + N'يُبلَّغ CISO وDPO لتسجيل الحادث ضمن نافذة الإخطار ٧٢ ساعة لسدايا.',
         N'DLP_TOP_SECRET_BLOCKED',
         N'{}',
         N'{"type":"FLAG","severity":"CRITICAL","roles":["CISO","DPO","LEGAL_COUNSEL","AUDIT_COMMITTEE"],"template":"SOCOPS_DLP_TOP_SECRET"}',
         1, N'CUSTOM');
END
GO
