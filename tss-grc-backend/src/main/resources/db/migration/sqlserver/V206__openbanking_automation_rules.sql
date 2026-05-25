-- V206: F8 automation rules for Open Banking Compliance events.
--
-- Sprint 19 PR #2. Pairs with PR #255 (backend module) by seeding 10
-- rules consuming OB_* events emitted by ObService.
--
-- SAMA Open Banking Framework operational reality:
--   * TPP onboarding (PENDING_KYB → ACTIVE) is the first day customer
--     consents can start flowing — Compliance + CISO + OB Officer need
--     to know so monitoring dashboards switch from "expecting traffic"
--     to "live monitoring".
--   * TPP suspension/revocation auto-cascades consent revocation. CRO +
--     Compliance + Legal + IR get same-day brief because customers
--     receive in-app notifications + the press picks up TPP revocations
--     fast in the Saudi fintech market.
--   * Bulk consent revocation (≥100 in 1h) is a fraud signal — could mean
--     credential stuffing or a compromised TPP credential. CISO + Fraud
--     team get a pager-grade alert, not a digest notification.
--   * SAMA-reportable OB incidents have a 24h notification window per
--     OBF v2 §11.4. The platform arms a 24h countdown the moment
--     samaReportable=true is set.
--   * Certificate expiry: FAPI 2.0 mTLS certs must be rotated before
--     expiry or the TPP loses connectivity. 90/30/7-day ladder.
--   * Data-leak incidents cross PDPL Article 25 territory (72h SDAIA
--     notification). DPO + Compliance + CISO all pulled in.
--
-- AR-241..AR-250 — service #27 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-241 ─ TPP onboarded → start monitoring ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-241')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-241',
         N'TPP onboarded → notify Compliance + OB Officer + CISO + Investor Relations',
         N'تفعيل مزوّد خدمة → إشعار الالتزام ومسؤول المصرفية المفتوحة وأمن المعلومات وعلاقات المستثمرين',
         N'PENDING_KYB → ACTIVE is the moment customer consents start '
         + N'flowing. Compliance Officer needs the OB monitoring tab '
         + N'switched from "expecting traffic" to "live monitoring". CISO '
         + N'arms WAF + rate-limit alerting on the new fapi_client_id. '
         + N'OB Officer activates the TPP''s onboarding playbook. Investor '
         + N'Relations gets a heads-up because Saudi fintech press picks '
         + N'up bank-TPP partnerships quickly.',
         N'الانتقال من قيد التحقّق إلى نشط هو لحظة بدء تدفّق موافقات العملاء. '
         + N'مسؤول الالتزام يحتاج إلى تبديل لوحة المراقبة من «بانتظار حركة» '
         + N'إلى «مراقبة مباشرة». مدير أمن المعلومات يفعّل التنبيهات على رقم '
         + N'العميل FAPI الجديد. مسؤول المصرفية المفتوحة يفعّل خطة الإلحاق. '
         + N'علاقات المستثمرين تستلم تنبيهاً لأنّ صحافة التقنية المالية '
         + N'السعودية تلتقط شراكات البنوك بسرعة.',
         N'OB_TPP_ONBOARDED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_OFFICER","OPEN_BANKING_OFFICER","CISO","INVESTOR_RELATIONS"],"template":"OB_TPP_LIVE"}',
         2, N'CUSTOM');
END

-- ─── AR-242 ─ TPP suspended → Compliance + CISO + Legal brief ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-242')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-242',
         N'TPP suspended → notify Compliance + CISO + Legal Counsel within 1h',
         N'إيقاف مزوّد الخدمة → إشعار الالتزام وأمن المعلومات والمستشار القانوني خلال ساعة',
         N'TPP suspension auto-cascades consent revocation on every active '
         + N'consent — every customer using that TPP receives an in-app '
         + N'notification. Compliance + CISO + Legal need a 1h brief '
         + N'covering: why we suspended, how many consumers are affected, '
         + N'what the public-facing message is, whether the TPP has the '
         + N'right to challenge under SAMA OBF dispute mechanism.',
         N'إيقاف مزوّد الخدمة يؤدّي تلقائياً لسحب كل الموافقات النشطة — يستلم '
         + N'كل عميل يستخدم هذا المزوّد إشعاراً داخل التطبيق. الالتزام وأمن '
         + N'المعلومات والمستشار القانوني يحتاجون لإحاطة خلال ساعة تغطّي: '
         + N'لماذا أوقفنا، كم عميلاً تأثّر، الرسالة العلنية، حق المزوّد في '
         + N'الطعن وفق آلية النزاعات في إطار ساما.',
         N'OB_TPP_SUSPENDED',
         N'{}',
         N'{"type":"ESCALATE","roles":["COMPLIANCE_OFFICER","CISO","LEGAL_COUNSEL","INVESTOR_RELATIONS"],"template":"OB_TPP_SUSPENDED","reminderAt":"NOW_PLUS_1H"}',
         1, N'CUSTOM');
END

-- ─── AR-243 ─ TPP revoked → top-line escalate + Critical CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-243')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-243',
         N'TPP revoked → escalate CRO + CFO + CISO + Compliance + IR + CRITICAL CAPA',
         N'إلغاء مزوّد الخدمة → تصعيد لمدير المخاطر والمالي وأمن المعلومات والالتزام والعلاقات + إجراء تصحيحي حرج',
         N'TPP revocation is the most material action under SAMA OBF — it''s '
         + N'permanent, it''s announced publicly to SAMA''s Trust Framework '
         + N'directory, and it triggers customer reissuance to alternative '
         + N'TPPs. CRITICAL CAPA captures the migration plan, the SAMA '
         + N'notification, and the post-mortem.',
         N'إلغاء مزوّد الخدمة هو الإجراء الأكثر جوهرية في إطار ساما — دائم، '
         + N'يُعلن في دليل الثقة لدى ساما، ويستدعي إعادة توجيه العملاء لمزوّدين '
         + N'بدلاء. يلتقط الإجراء التصحيحي الحرج خطة التحوّل وإبلاغ ساما '
         + N'وتحليل السبب الجذري.',
         N'OB_TPP_REVOKED',
         N'{}',
         N'{"type":"ESCALATE","roles":["RISK_MANAGER","CFO","CISO","COMPLIANCE_OFFICER","INVESTOR_RELATIONS","LEGAL_COUNSEL"],"andCreate":{"entity":"CAPA","title":"TPP revocation — customer migration + SAMA notification","ownerRole":"COMPLIANCE_OFFICER","priority":"CRITICAL","sourceModule":"OPENBANKING"}}',
         1, N'CUSTOM');
END

-- ─── AR-244 ─ Consent granted → evidence vault cross-log ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-244')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-244',
         N'Consent granted → cross-log to evidence vault (PDPL Article 6 lawful basis)',
         N'منح موافقة → إيداع في خزانة الأدلّة (الأساس النظامي للمعالجة وفق نظام حماية البيانات)',
         N'Every customer consent is the lawful-basis evidence under PDPL '
         + N'Article 6 for the data the TPP will pull. The Compliance vault '
         + N'cross-logs the consent with scope + customer hash + grant '
         + N'timestamp so future PDPL Subject-Access-Requests can pull '
         + N'consent provenance instantly.',
         N'كل موافقة عميل هي دليل الأساس النظامي وفق المادة 6 من نظام حماية '
         + N'البيانات الشخصية للبيانات التي سيسحبها المزوّد. خزانة الالتزام '
         + N'تودع الموافقة مع النطاق وبصمة العميل ووقت المنح لتسهيل تلبية '
         + N'طلبات أصحاب البيانات مستقبلاً.',
         N'OB_CONSENT_GRANTED',
         N'{}',
         N'{"type":"CREATE_RECORD","entity":"EVIDENCE_ITEM","title":"OB consent — PDPL lawful basis","ownerRole":"DPO","retentionYears":7,"sourceModule":"OPENBANKING"}',
         3, N'CUSTOM');
END

-- ─── AR-245 ─ Bulk revocation fraud signal → CISO + Fraud + CRITICAL CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-245')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-245',
         N'Bulk consent revocation (≥100 in 1h) → escalate CISO + Fraud + CRITICAL CAPA',
         N'سحب جماعي للموافقات (≥100 خلال ساعة) → تصعيد لأمن المعلومات وفريق الاحتيال + إجراء تصحيحي حرج',
         N'A sudden spike in revocations is rarely organic. The two most '
         + N'common causes are (a) the TPP got hacked and customers are '
         + N'killing their links proactively, or (b) credential-stuffing '
         + N'against the bank''s consent-management UI. CISO + Fraud both '
         + N'need pager-grade alerts. A CRITICAL CAPA captures the forensic '
         + N'review + whether to push a customer comms blast.',
         N'الارتفاع المفاجئ في عمليات السحب نادراً ما يكون عضويّاً. السببان '
         + N'الأكثر شيوعاً: (أ) اختراق المزوّد فيبادر العملاء بقطع الروابط، '
         + N'أو (ب) هجوم تجريب بيانات اعتماد على واجهة إدارة الموافقات لدى '
         + N'البنك. مدير أمن المعلومات وفريق الاحتيال يحتاجان لتنبيه فوري. '
         + N'الإجراء التصحيحي الحرج يلتقط المراجعة الجنائية وقرار إرسال '
         + N'بلاغ شامل للعملاء.',
         N'OB_CONSENT_BULK_REVOCATION',
         N'{}',
         N'{"type":"ESCALATE","roles":["CISO","FRAUD_INVESTIGATOR","COMPLIANCE_OFFICER","OPEN_BANKING_OFFICER"],"andCreate":{"entity":"CAPA","title":"Bulk consent revocation — forensic + comms decision","ownerRole":"CISO","priority":"CRITICAL","sourceModule":"OPENBANKING"}}',
         1, N'CUSTOM');
END

-- ─── AR-246 ─ Critical incident → 1h escalation ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-246')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-246',
         N'OB incident severity=CRITICAL → escalate CISO + CRO + Compliance + IR within 1h',
         N'حادثة المصرفية المفتوحة بدرجة حرجة → تصعيد لأمن المعلومات والمخاطر والالتزام والاتصالات خلال ساعة',
         N'A CRITICAL severity OB incident — data leak, mass scope creep, '
         + N'TPP-on-TPP fraud — needs a 1h CSIRT-style activation. CISO '
         + N'owns the technical response, CRO frames the risk posture, '
         + N'Compliance handles regulator coordination, Investor Relations '
         + N'prepares any external statement that may be needed.',
         N'الحادثة الحرجة في المصرفية المفتوحة — تسرّب بيانات، تجاوز نطاق '
         + N'جماعي، احتيال بين المزوّدين — تستدعي تفعيل فريق الاستجابة خلال '
         + N'ساعة. مدير أمن المعلومات يقود الاستجابة الفنّية، مدير المخاطر '
         + N'يصوغ موقف المخاطر، الالتزام ينسّق مع الجهة الرقابية، علاقات '
         + N'المستثمرين يجهّز أي بيان خارجي قد يلزم.',
         N'OB_INCIDENT_RAISED',
         N'{"severity":{"eq":"CRITICAL"}}',
         N'{"type":"ESCALATE","roles":["CISO","RISK_MANAGER","COMPLIANCE_OFFICER","INVESTOR_RELATIONS","CRO"],"template":"OB_CRITICAL_INCIDENT","reminderAt":"NOW_PLUS_1H"}',
         1, N'CUSTOM');
END

-- ─── AR-247 ─ SAMA-reportable incident → 24h notification window ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-247')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-247',
         N'SAMA-reportable OB incident → 24h notification countdown + Board Secretary',
         N'حادثة قابلة للتبليغ لساما → عدّاد تنازلي 24 ساعة وأمين سرّ المجلس',
         N'SAMA Open Banking Framework v2 §11.4 requires incidents '
         + N'materially affecting the OB service to be reported to SAMA''s '
         + N'OB unit within 24 hours of detection. The 24h reminder arms '
         + N'the moment samaReportable=true is set. Compliance Officer + '
         + N'Board Secretary + OB Officer all pulled in so the notification '
         + N'package is drafted in parallel with the incident response.',
         N'يستوجب إطار المصرفية المفتوحة لدى ساما الإصدار الثاني §11.4 '
         + N'الإبلاغ عن الحوادث المؤثّرة جوهرياً على خدمة المصرفية المفتوحة '
         + N'خلال 24 ساعة من الاكتشاف. يُفعّل التذكير اللحظة التي يُحدَّد '
         + N'فيها العلم بأنّ الحادثة قابلة للتبليغ. مسؤول الالتزام وأمين السرّ '
         + N'ومسؤول المصرفية المفتوحة يُسحَبون معاً ليُعَدّ الإبلاغ بالتوازي '
         + N'مع الاستجابة.',
         N'OB_INCIDENT_RAISED',
         N'{"samaReportable":{"eq":"true"}}',
         N'{"type":"ESCALATE","roles":["COMPLIANCE_OFFICER","BOARD_SECRETARY","OPEN_BANKING_OFFICER","CISO"],"template":"OB_SAMA_REPORTABLE","reminderAt":"NOW_PLUS_24H"}',
         1, N'CUSTOM');
END

-- ─── AR-248 ─ API abuse → CISO + OB Officer + Fraud + CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-248')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-248',
         N'API abuse detected → notify CISO + OB Officer + Fraud + open CAPA',
         N'كشف إساءة استخدام للواجهة → إشعار أمن المعلومات والمصرفية المفتوحة وفريق الاحتيال + فتح إجراء تصحيحي',
         N'API abuse signals — rate-limit breach, scope creep (TPP using '
         + N'broader scopes than consent allows), after-hours pattern '
         + N'(unusual TPP traffic outside its declared business hours) — '
         + N'are caught by the in-line WAF + behavioural analytics. The '
         + N'platform fires this trigger so CISO and OB Officer can decide '
         + N'whether to suspend the TPP, throttle, or open a dialogue.',
         N'إشارات إساءة استخدام الواجهة — تجاوز الحد، تجاوز النطاق (استخدام '
         + N'نطاق أوسع ممّا تسمح به الموافقة)، نمط خارج ساعات العمل — تُلتقَط '
         + N'بواسطة جدار حماية الواجهات والتحليلات السلوكية. تُطلق المنصّة '
         + N'هذا التنبيه ليقرّر أمن المعلومات والمصرفية المفتوحة الإيقاف أم '
         + N'التقييد أم فتح حوار.',
         N'OB_API_ABUSE',
         N'{}',
         N'{"type":"NOTIFY","roles":["CISO","OPEN_BANKING_OFFICER","FRAUD_INVESTIGATOR"],"andCreate":{"entity":"CAPA","title":"OB API abuse — investigate + decide on TPP action","ownerRole":"CISO","priority":"HIGH","sourceModule":"OPENBANKING"}}',
         2, N'CUSTOM');
END

-- ─── AR-249 ─ FAPI cert expiring → 90/30/7-day reminder ladder ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-249')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-249',
         N'FAPI 2.0 certificate expiring → 90/30/7-day reminder ladder to OB Officer + TPP AM',
         N'انتهاء صلاحية شهادة FAPI 2.0 → سلسلة تذكيرات 90 و30 و7 أيام لمسؤول المصرفية المفتوحة ومدير علاقة المزوّد',
         N'FAPI 2.0 mTLS certs must be rotated before expiry or the TPP '
         + N'loses connectivity at midnight UTC on expiry day. The 90-day '
         + N'reminder triggers operational planning; the 30-day reminder '
         + N'starts the rotation; the 7-day reminder is the hard-stop '
         + N'alert.',
         N'يجب تدوير شهادات mTLS لـFAPI 2.0 قبل انتهاء الصلاحية وإلا فقَدَ '
         + N'المزوّد الاتصال عند منتصف ليلة الانتهاء. تذكير 90 يوماً يُفعّل '
         + N'التخطيط التشغيلي؛ تذكير 30 يوماً يبدأ التدوير؛ تذكير 7 أيام هو '
         + N'إنذار التوقّف الإجباري.',
         N'OB_CERT_EXPIRING',
         N'{}',
         N'{"type":"NOTIFY","roles":["OPEN_BANKING_OFFICER","ACCOUNT_MANAGER","CISO"],"template":"OB_CERT_EXPIRING","reminderLadder":"T-90,T-30,T-7"}',
         2, N'CUSTOM');
END

-- ─── AR-250 ─ Data-leak incident → DPO + PDPL 72h cross-link ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-250')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-250',
         N'OB data-leak incident → escalate DPO + Compliance + CISO + CRITICAL CAPA (PDPL Art.25 72h)',
         N'حادثة تسرّب بيانات في المصرفية المفتوحة → تصعيد لمسؤول حماية البيانات والالتزام وأمن المعلومات + إجراء تصحيحي حرج (المادة 25 من نظام حماية البيانات — 72 ساعة)',
         N'A data-leak OB incident crosses into PDPL Article 25 territory — '
         + N'breach notification to SDAIA within 72 hours. The DPO leads '
         + N'the PDPL notification track; Compliance owns the SAMA OBF '
         + N'track (24h per OBF v2); CISO owns containment. A CRITICAL '
         + N'CAPA holds both notification tracks + the customer comms '
         + N'decision.',
         N'تسرّب البيانات في المصرفية المفتوحة يتجاوز نظام حماية البيانات '
         + N'الشخصية (المادة 25 — الإبلاغ إلى سدايا خلال 72 ساعة). يقود '
         + N'مسؤول حماية البيانات إبلاغ سدايا؛ الالتزام يقود إبلاغ ساما (24 '
         + N'ساعة)؛ أمن المعلومات يقود الاحتواء. الإجراء التصحيحي الحرج '
         + N'يحتضن كلا مساري الإبلاغ وقرار اتّصال العملاء.',
         N'OB_INCIDENT_RAISED',
         N'{"incidentType":{"eq":"DATA_LEAK"}}',
         N'{"type":"ESCALATE","roles":["DPO","COMPLIANCE_OFFICER","CISO","CRO","LEGAL_COUNSEL"],"andCreate":{"entity":"CAPA","title":"OB data leak — PDPL 72h + SAMA 24h notifications","ownerRole":"DPO","priority":"CRITICAL","sourceModule":"OPENBANKING"}}',
         1, N'CUSTOM');
END
GO
