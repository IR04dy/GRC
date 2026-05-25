-- V205: F8 automation rules for Counterparty Credit Rating Registry events.
--
-- Sprint 18 PR #2. Pairs with PR #250 (backend module) by seeding 10
-- rules consuming COUNTERPARTY_* and LARGE_EXPOSURE_LIMIT_* events
-- emitted by CptyService.
--
-- SAMA Credit Risk Management Standards reality:
--   * A downgrade is operational news within the hour — the CRO + the
--     Relationship Manager + the Credit Committee Chair need to know
--     because IFRS 9 staging may shift (Stage 1 → 2) and ECL provisioning
--     re-runs immediately.
--   * Default is the most material credit event a bank can face. CRO + CFO
--     + Audit Committee Chair get same-day pages; a CRITICAL CAPA captures
--     the recovery plan + Stage 3 staging adjustments.
--   * Sovereign / GRE downgrades (Aramco, SABIC, PIF, REDF) move
--     differently — the Board Risk Committee Chair must see them because
--     the bank's whole sovereign-portfolio risk weight changes.
--   * Large-exposure limit movement: AMBER is "capacity room watch", RED
--     is "freeze new credit", BREACH is SAMA-reportable single-name >25%
--     Tier 1 — same-day SAMA Liaison notification + CRITICAL CAPA.
--   * External-agency-driven downgrades (S&P / Moody's / Fitch / CARE)
--     are market-watching events — the regulatory horizon scanner +
--     external-comms team need the heads-up before the wire picks it up.
--
-- AR-231..AR-240 — service #26 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-231 ─ Counterparty registered → notify Credit + RM ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-231')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-231',
         N'Counterparty registered → notify Credit Officer + Relationship Manager',
         N'تسجيل طرف مقابل → إشعار مسؤول الائتمان ومدير العلاقة',
         N'A new counterparty entering the register signals the start of '
         + N'a credit relationship. The Credit Officer owns the rating; '
         + N'the Relationship Manager owns the commercial conversation. '
         + N'Both need the workspace pinned from day one so KYC, rating, '
         + N'and limit work proceeds in parallel.',
         N'دخول طرف مقابل جديد إلى السجلّ يبدأ علاقة ائتمانية. مسؤول الائتمان '
         + N'يملك التصنيف، ومدير العلاقة يملك المحادثة التجارية. كلاهما '
         + N'يحتاج لتثبيت المساحة منذ اليوم الأول حتى تجري أعمال «اعرف عميلك» '
         + N'والتصنيف والحدّ بالتوازي.',
         N'COUNTERPARTY_REGISTERED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CREDIT_OFFICER","RISK_MANAGER","RELATIONSHIP_MANAGER"],"template":"CPTY_ONBOARDING_KICKOFF"}',
         2, N'CUSTOM');
END

-- ─── AR-232 ─ Rating downgraded → escalate CRO + RM + Credit Committee ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-232')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-232',
         N'Counterparty downgraded → escalate CRO + RM + Credit Committee Chair within 1h',
         N'خفض تصنيف طرف مقابل → تصعيد لمدير المخاطر ومدير العلاقة ورئيس لجنة الائتمان خلال ساعة',
         N'A downgrade is operational news within the hour. IFRS 9 staging '
         + N'may shift (Stage 1 → Stage 2) and ECL provisioning re-runs '
         + N'immediately. The CRO needs to know to brief the next Risk '
         + N'Committee. The Relationship Manager needs to manage commercial '
         + N'expectations. The Credit Committee Chair sets the next-window '
         + N'agenda.',
         N'خفض التصنيف خبر تشغيلي خلال ساعة. قد تتغيّر مرحلة IFRS 9 (من 1 '
         + N'إلى 2) وتُعاد حسابات الخسائر الائتمانية المتوقّعة فوراً. مدير '
         + N'المخاطر يحتاج للعلم لإحاطة لجنة المخاطر القادمة. مدير العلاقة '
         + N'يدير التوقّعات التجارية. رئيس لجنة الائتمان يضع أجندة النافذة '
         + N'القادمة.',
         N'COUNTERPARTY_RATING_DOWNGRADED',
         N'{}',
         N'{"type":"ESCALATE","roles":["RISK_MANAGER","RELATIONSHIP_MANAGER","CREDIT_COMMITTEE_CHAIR"],"template":"CPTY_DOWNGRADE","reminderAt":"NOW_PLUS_1H"}',
         1, N'CUSTOM');
END

-- ─── AR-233 ─ Counterparty DEFAULT → top-line escalation + CRITICAL CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-233')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-233',
         N'Counterparty defaulted → escalate CRO + CFO + Audit Committee + CRITICAL CAPA for Stage 3 staging',
         N'تعثّر طرف مقابل → تصعيد لمدير المخاطر والمالي ولجنة المراجعة + إجراء تصحيحي حرج لمرحلة 3',
         N'Default is the most material credit event the bank can face. CRO + '
         + N'CFO + Audit Committee Chair get same-day pages. The CAPA captures '
         + N'the recovery plan, the IFRS 9 Stage 3 staging adjustment, the '
         + N'specific provisions resubmission, the legal-action timeline, and '
         + N'the SAMA notification when the exposure crosses the materiality '
         + N'threshold.',
         N'التعثّر أكثر الأحداث الائتمانية مادّيةً يواجهها البنك. مدير المخاطر '
         + N'والمالي ورئيس لجنة المراجعة يُبلَّغون في اليوم نفسه. يلتقط الإجراء '
         + N'التصحيحي خطّة الاسترداد وتعديل المرحلة 3 وفقاً لـIFRS 9 وإعادة '
         + N'تقديم المخصّصات المحدّدة وخطّ الزمن القانوني وتبليغ ساما عند '
         + N'تجاوز التعرّض حدّ المادّية.',
         N'COUNTERPARTY_DEFAULTED',
         N'{}',
         N'{"type":"ESCALATE","roles":["RISK_MANAGER","CFO","AUDIT_COMMITTEE_CHAIR","BOARD_RISK_COMMITTEE_CHAIR"],"andCreate":{"entity":"CAPA","title":"Counterparty default — recovery plan + Stage 3 staging","ownerRole":"RISK_MANAGER","priority":"CRITICAL","sourceModule":"CPTY"}}',
         1, N'CUSTOM');
END

-- ─── AR-234 ─ Watchlist negative → weekly review reminder ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-234')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-234',
         N'Counterparty added to negative watchlist → notify RM + arm weekly review reminder',
         N'إضافة طرف مقابل لقائمة المراقبة السلبية → إشعار مدير العلاقة وتذكير مراجعة أسبوعي',
         N'A negative watchlist add is an early warning — not yet a '
         + N'downgrade. The Relationship Manager needs to refresh commercial '
         + N'intel; Credit Risk needs to refresh the rating package. Weekly '
         + N'reviews continue until the counterparty either recovers '
         + N'(watchlist removed) or is downgraded.',
         N'الإضافة إلى قائمة المراقبة السلبية إنذار مبكّر — وليس خفض تصنيف بعد. '
         + N'يحتاج مدير العلاقة لتحديث الاستخبارات التجارية، وفريق مخاطر '
         + N'الائتمان لتحديث حزمة التصنيف. تستمرّ المراجعات الأسبوعية حتى '
         + N'يتعافى الطرف (تُرفع المراقبة) أو يُخفَّض تصنيفه.',
         N'COUNTERPARTY_WATCHLIST_NEGATIVE',
         N'{}',
         N'{"type":"NOTIFY","roles":["RELATIONSHIP_MANAGER","CREDIT_OFFICER","RISK_MANAGER"],"template":"CPTY_WATCHLIST_NEGATIVE","reminderAt":"WEEKLY"}',
         2, N'CUSTOM');
END

-- ─── AR-235 ─ Sovereign/GRE downgrade → Board Risk Chair ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-235')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-235',
         N'Sovereign/GRE downgrade → notify Board Risk Committee Chair (systemic)',
         N'خفض تصنيف سيادي أو جهة حكومية → إشعار رئيس لجنة المخاطر بالمجلس (نظامي)',
         N'Saudi sovereign and Government-Related Entity (Aramco, SABIC, PIF, '
         + N'REDF) downgrades move differently. The whole sovereign-portfolio '
         + N'risk weight changes; capital adequacy may move 50bps+ overnight; '
         + N'the Board Risk Committee Chair must see this within the day so '
         + N'next-week capital reporting reflects the new posture.',
         N'تختلف عمليات خفض التصنيف السيادي السعودي وكذا الجهات الحكومية '
         + N'(أرامكو، سابك، PIF، صندوق التنمية العقاري). يتغيّر وزن مخاطر '
         + N'المحفظة السيادية كلّياً، وقد تتحرّك كفاية رأس المال 50 نقطة أساس '
         + N'وأكثر في ليلة واحدة؛ يجب أن يطّلع رئيس لجنة المخاطر في اليوم '
         + N'نفسه حتى يعكس تقرير رأس المال الأسبوع القادم الوضع الجديد.',
         N'COUNTERPARTY_RATING_DOWNGRADED',
         N'{"sovereignFlag":{"eq":"true"},"greFlag":{"eq":"true"}}',
         N'{"type":"ESCALATE","roles":["BOARD_RISK_COMMITTEE_CHAIR","RISK_MANAGER","CFO"],"template":"CPTY_SOVEREIGN_DOWNGRADE"}',
         1, N'CUSTOM');
END

-- ─── AR-236 ─ Large-exposure AMBER → notify Credit Officer + RM ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-236')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-236',
         N'Large-exposure limit AMBER → notify Credit Officer + Relationship Manager (capacity room watch)',
         N'حدّ التعرّض في النطاق الكهرماني → إشعار مسؤول الائتمان ومدير العلاقة (مراقبة مساحة السقف)',
         N'AMBER (70-90% utilised) is "capacity room watch" — new draw downs '
         + N'are still allowed but the Credit Officer needs to pre-clear with '
         + N'Credit Risk and the RM needs to manage client expectations. '
         + N'Goal: avoid hitting RED.',
         N'النطاق الكهرماني (70-90% مستخدم) يعني «مراقبة مساحة السقف» — لا '
         + N'تزال السحوبات الجديدة مسموحة، لكن مسؤول الائتمان يحتاج لموافقة '
         + N'مسبقة من فريق مخاطر الائتمان ومدير العلاقة يحتاج لإدارة توقّعات '
         + N'العميل. الهدف: تجنّب الوصول إلى الأحمر.',
         N'LARGE_EXPOSURE_LIMIT_AMBER',
         N'{"worsening":{"eq":"true"}}',
         N'{"type":"NOTIFY","roles":["CREDIT_OFFICER","RELATIONSHIP_MANAGER","RISK_MANAGER"],"template":"LIMIT_AMBER"}',
         2, N'CUSTOM');
END

-- ─── AR-237 ─ Large-exposure RED → CRO escalate + freeze ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-237')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-237',
         N'Large-exposure limit RED → escalate CRO + freeze new credit',
         N'حدّ التعرّض في النطاق الأحمر → تصعيد لمدير المخاطر وتجميد ائتمان جديد',
         N'RED (>90% utilised) means no new credit until either the limit is '
         + N'revised upward (with Credit Committee approval) or utilisation '
         + N'drops via repayment / collateral / paydown. CRO escalation makes '
         + N'this visible at the next ALCO/Credit Committee. Front-office '
         + N'sales must back off new exposure.',
         N'الأحمر (أكثر من 90% مستخدم) يعني لا ائتمان جديد إلى أن يُرفع الحدّ '
         + N'(باعتماد لجنة الائتمان) أو يهبط الاستخدام عبر التسديد أو '
         + N'الضمانات أو السداد الجزئي. تصعيد مدير المخاطر يجعل الحدث ظاهراً '
         + N'في اجتماع ALCO ولجنة الائتمان القادمة. على فريق المبيعات '
         + N'الأمامي التراجع عن أي تعرّض جديد.',
         N'LARGE_EXPOSURE_LIMIT_RED',
         N'{"worsening":{"eq":"true"}}',
         N'{"type":"ESCALATE","roles":["RISK_MANAGER","CREDIT_COMMITTEE_CHAIR","HEAD_CORPORATE_BANKING"],"template":"LIMIT_RED_FREEZE"}',
         1, N'CUSTOM');
END

-- ─── AR-238 ─ BREACH → CRO + CFO + Compliance + SAMA Liaison + CRITICAL CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-238')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-238',
         N'Large-exposure BREACH (>100%) → top-line escalate + SAMA notification + CRITICAL CAPA',
         N'تجاوز حدّ التعرّض الكبير (أكثر من 100%) → تصعيد أعلى + إبلاغ ساما + إجراء تصحيحي حرج',
         N'BREACH = utilised > 100% of approved limit. Under SAMA''s large-'
         + N'exposure framework this is reportable to SAMA on the same '
         + N'business day. CRO + CFO + Compliance + the SAMA Liaison + '
         + N'Internal Audit are all on the page. The CRITICAL CAPA captures '
         + N'the remediation path (sell-down, hedge, transfer to a SPV, or '
         + N'limit re-approval with Board sign-off).',
         N'التجاوز يعني أن المستخدم تجاوز 100% من الحدّ المُعتمَد. وفق إطار '
         + N'التعرّضات الكبيرة لدى ساما، يلزم الإبلاغ في يوم العمل نفسه. '
         + N'مدير المخاطر والمالي والالتزام وأخصائي علاقة ساما والمراجعة '
         + N'الداخلية جميعهم على الإشعار. يلتقط الإجراء التصحيحي الحرج '
         + N'مسار العلاج (التخفيض، التحوّط، النقل لكيان خاصّ، أو إعادة '
         + N'الاعتماد بتوقيع المجلس).',
         N'LARGE_EXPOSURE_LIMIT_BREACH',
         N'{}',
         N'{"type":"ESCALATE","roles":["RISK_MANAGER","CFO","COMPLIANCE_OFFICER","INTERNAL_AUDITOR","BOARD_RISK_COMMITTEE_CHAIR"],"andCreate":{"entity":"CAPA","title":"Large-exposure breach — SAMA notification + remediation","ownerRole":"RISK_MANAGER","priority":"CRITICAL","sourceModule":"CPTY"}}',
         1, N'CUSTOM');
END

-- ─── AR-239 ─ Group-scope AMBER+ → CRO aggregate visibility ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-239')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-239',
         N'Group-scope limit AMBER/RED → notify CRO for aggregate-risk visibility',
         N'حدّ على مستوى المجموعة كهرماني أو أحمر → إشعار مدير المخاطر لرؤية المخاطر التجميعية',
         N'A group-scope limit aggregates exposure across all legal entities '
         + N'sharing a parent (e.g. Aramco Trading + Saudi Aramco + Luberef → '
         + N'ARAMCO_GROUP). Group-level capacity tightening is a different '
         + N'story than single-counterparty — it means the bank''s exposure '
         + N'to a whole economic group is approaching ceiling. CRO needs '
         + N'aggregate visibility regardless of which underlying entity '
         + N'pushed it.',
         N'الحدّ على مستوى المجموعة يجمع التعرّضات عبر كل الكيانات القانونية '
         + N'التي تشترك في شركة أم (مثلاً أرامكو للتجارة + أرامكو السعودية + '
         + N'لوبريف → مجموعة أرامكو). تضييق السعة على مستوى المجموعة قصّة '
         + N'مختلفة عن طرف مقابل واحد — يعني أن تعرّض البنك لمجموعة اقتصادية '
         + N'كاملة يقترب من السقف. يحتاج مدير المخاطر لرؤية تجميعية بصرف '
         + N'النظر عن أي كيان دفعها.',
         N'LARGE_EXPOSURE_LIMIT_AMBER',
         N'{"scope":{"eq":"GROUP"}}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","CFO","HEAD_CORPORATE_BANKING"],"template":"GROUP_LIMIT_AMBER"}',
         2, N'CUSTOM');
END

-- ─── AR-240 ─ External-agency-driven downgrade → reg comms cross-link ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-240')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-240',
         N'External-agency downgrade → cross-link to regulatory horizon scanner + external-comms team',
         N'خفض تصنيف من وكالة خارجية → ربط مع ماسح الأفق التنظيمي وفريق الاتصالات الخارجية',
         N'External agency-driven downgrades (S&P, Moody''s, Fitch, CARE Saudi) '
         + N'are market-watching events. The wire picks them up; analysts '
         + N'call; depositors notice. The regulatory horizon scanner + the '
         + N'external-comms team need the heads-up before the news reaches '
         + N'the broader market so the bank''s pre-prepared statement is '
         + N'ready, and the Investor Relations team can field calls with the '
         + N'official line.',
         N'خفض التصنيف من وكالة خارجية (S&P، Moody''s، Fitch، CARE السعودية) '
         + N'حدث ترصده الأسواق. تنشره الوكالات، يتّصل المحلّلون، ينتبه '
         + N'المودِعون. يحتاج ماسح الأفق التنظيمي وفريق الاتصالات الخارجية '
         + N'لتنبيه مسبق قبل وصول الخبر للسوق الأوسع حتى يكون البيان '
         + N'الجاهز للبنك مُعدّاً، ويتمكّن فريق علاقات المستثمرين من الردّ '
         + N'على الاتصالات وفق الرسالة الرسمية.',
         N'COUNTERPARTY_RATING_DOWNGRADED',
         N'{"triggerSource":{"eq":"EXTERNAL_AGENCY"}}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_OFFICER","INVESTOR_RELATIONS","CRO","CFO","LEGAL_COUNSEL"],"template":"EXTERNAL_DOWNGRADE_COMMS"}',
         2, N'CUSTOM');
END
GO
