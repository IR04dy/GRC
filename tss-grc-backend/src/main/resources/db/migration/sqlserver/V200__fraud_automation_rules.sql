-- V200: F8 automation rules for Fraud Risk Management events.
--
-- Sprint 14 PR #4. Pairs with PR #223 (backend module) by seeding
-- 10 rules that consume the FRAUD_CASE_* events emitted by
-- FraudService.
--
-- Saudi-bank fraud ops reality:
--   * High-severity cases need the Fraud Officer + CRO on alert
--     instantly — SAMA Anti-Fraud Framework 2018 Art.7 mandates
--     a documented escalation channel for "material" fraud.
--   * Losses above SAR 1M typically meet SAMA's quarterly
--     mandatory-reporting bar — board sees it, CEO signs the
--     return. Trigger CEO + Audit Committee notification when
--     the case is even opened so the regulator pack starts
--     getting drafted early.
--   * Customer-impact > 100 is a de-facto operational incident
--     in any retail bank — auto-open an incident so the cyber
--     ops team can mobilise (this case may have a cyber
--     control failure root cause).
--   * MLRO needs to see every STR_FILED because the SAFIU
--     follow-up correspondence flows through the MLRO's queue.
--   * INTERNAL_EMBEZZLEMENT confirms always trigger a parallel
--     HR investigation track (SAMA Cyber 3.3.13 + Saudi Labour
--     Law disciplinary procedures).
--   * Cyber-category fraud (BEC, ATO, phishing → ATO chain)
--     gets the CISO + cyber ops on the case alongside the
--     fraud team — they often share root cause (credential
--     compromise, MFA bypass, etc.).
--
-- AR-181..AR-190 — service #21 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-181 ─ Critical/High severity case → notify Fraud Officer + CRO ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-181')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-181',
         N'Fraud case opened with HIGH/CRITICAL severity → notify Fraud Officer + CRO',
         N'فتح قضية احتيال بدرجة عالية أو حرجة → إشعار مسؤول الاحتيال ومدير المخاطر',
         N'High and critical fraud cases need the Fraud Officer and Chief Risk '
         + N'Officer on alert from the moment the case is opened — SAMA Anti-Fraud '
         + N'Framework Art.7 mandates a documented escalation channel for material '
         + N'fraud. The case may still be in DETECTED, but the clock starts now.',
         N'تتطلب قضايا الاحتيال العالية والحرجة تنبيه مسؤول الاحتيال ومدير المخاطر '
         + N'منذ لحظة فتح القضية — تشترط المادة 7 من إطار مكافحة الاحتيال 2018 لدى ساما '
         + N'وجود قناة تصعيد موثّقة للاحتيال الجوهري. القضية قد تكون لا تزال في حالة '
         + N'الرصد، لكن الساعة بدأت.',
         N'FRAUD_CASE_CREATED',
         N'{"severity":{"in":["CRITICAL","HIGH"]}}',
         N'{"type":"NOTIFY","roles":["FRAUD_OFFICER","RISK_MANAGER"],"template":"FRAUD_HIGH_SEVERITY_DETECTED"}',
         1, N'CUSTOM');
END

-- ─── AR-182 ─ Estimated loss > SAR 1M → escalate to CEO + Audit Committee ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-182')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-182',
         N'Fraud case opened with estimated loss > SAR 1M → escalate to CEO + Audit Committee',
         N'فتح قضية احتيال بخسارة مقدّرة تتجاوز مليون ريال → تصعيد إلى الرئيس التنفيذي ولجنة المراجعة',
         N'Estimated loss above SAR 1M typically meets SAMA quarterly mandatory '
         + N'reporting thresholds. The CEO must approve the regulator return, and '
         + N'the Audit Committee discusses it at the next quarterly meeting. '
         + N'Escalating at case-open lets the regulator pack get drafted while '
         + N'investigation is still underway.',
         N'الخسارة المقدّرة فوق مليون ريال تستوفي عادةً حدود الإبلاغ الإلزامي الفصلي '
         + N'لدى البنك المركزي السعودي. الرئيس التنفيذي يعتمد التقرير الرقابي، ولجنة '
         + N'المراجعة تناقشه في اجتماعها الفصلي. التصعيد عند فتح القضية يتيح إعداد '
         + N'مسودة التقرير الرقابي بالتوازي مع سير التحقيق.',
         N'FRAUD_CASE_CREATED',
         N'{"estimatedLossSar":{">":1000000}}',
         N'{"type":"ESCALATE","roles":["CEO","AUDIT_COMMITTEE_CHAIR"],"template":"FRAUD_MATERIAL_LOSS_ESCALATION","slaDays":3}',
         1, N'CUSTOM');
END

-- ─── AR-183 ─ Customer impact > 100 → auto-open Incident ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-183')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-183',
         N'Fraud case with > 100 affected customers → auto-open Incident',
         N'قضية احتيال تطال أكثر من 100 عميل → فتح حدث تشغيلي تلقائياً',
         N'A retail-banking fraud touching more than 100 customers is an '
         + N'operational incident by any reasonable definition — cyber ops, comms, '
         + N'and customer-care all need to mobilise. The platform creates an '
         + N'Incident automatically and links it back to the fraud case so '
         + N'both threads stay synchronised.',
         N'الاحتيال في الخدمات المصرفية للأفراد الذي يطال أكثر من 100 عميل يُعدّ '
         + N'حدثاً تشغيلياً وفق أي تعريف معقول — تحتاج عمليات السيبراني والاتصال '
         + N'وخدمة العملاء جميعاً للتعبئة. تنشئ المنصة حدثاً تشغيلياً تلقائياً '
         + N'وتربطه بقضية الاحتيال لإبقاء المسارين متزامنين.',
         N'FRAUD_CASE_CREATED',
         N'{"affectedCustomerCount":{">":100}}',
         N'{"type":"CREATE_RECORD","entityType":"Incident","linkBack":"FraudCase","severity":"HIGH","template":"FRAUD_MASS_CUSTOMER_IMPACT"}',
         2, N'CUSTOM');
END

-- ─── AR-184 ─ Case CONFIRMED → notify Board Secretary (for board pack) ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-184')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-184',
         N'Fraud case CONFIRMED → notify Board Secretary for board pack inclusion',
         N'تأكيد قضية احتيال → إشعار أمين سر مجلس الإدارة لإدراجها في حزمة المجلس',
         N'Every confirmed fraud case must be referenced in the quarterly board '
         + N'pack — SAMA Corporate Governance Art.6 requires the board to be '
         + N'informed of material control failures. The Board Secretary needs '
         + N'the case summary, confirmed loss, and root cause to draft the pack '
         + N'section by the meeting cut-off.',
         N'يجب الإشارة إلى كل قضية احتيال مؤكَّدة في حزمة المجلس الفصلية — تشترط '
         + N'المادة 6 من حوكمة الشركات لدى البنك المركزي السعودي إبلاغ المجلس '
         + N'بإخفاقات الضوابط الجوهرية. يحتاج أمين السر إلى ملخّص القضية والخسارة '
         + N'المؤكَّدة والسبب الجذري لإعداد القسم قبل موعد الاجتماع.',
         N'FRAUD_CASE_CONFIRMED',
         N'{}',
         N'{"type":"NOTIFY","roles":["BOARD_SECRETARY"],"template":"FRAUD_CONFIRMED_BOARD_PACK"}',
         2, N'CUSTOM');
END

-- ─── AR-185 ─ Case CONFIRMED + loss > SAR 500k → create CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-185')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-185',
         N'Fraud CONFIRMED with confirmed loss > SAR 500k → auto-create CAPA',
         N'تأكيد احتيال بخسارة مؤكَّدة تتجاوز 500 ألف ريال → إنشاء إجراء تصحيحي تلقائياً',
         N'A confirmed loss above SAR 500k almost always implies a control gap '
         + N'that allowed the fraud to succeed — the call-back threshold was too '
         + N'high, the velocity check was disabled for that customer segment, the '
         + N'staff lacked typology training. Auto-creating a CAPA forces the fraud '
         + N'team to own remediation rather than treating the case as closed at '
         + N'closure.',
         N'الخسارة المؤكَّدة التي تتجاوز 500 ألف ريال تنطوي دائماً تقريباً على ثغرة '
         + N'في الضوابط أتاحت نجاح الاحتيال — حد إعادة الاتصال كان مرتفعاً جداً، '
         + N'فحص السرعة كان معطّلاً لشريحة العملاء، الموظفون يفتقرون لتدريب على '
         + N'الأنماط. الإنشاء التلقائي لإجراء تصحيحي يُلزم فريق الاحتيال '
         + N'بمسؤولية المعالجة.',
         N'FRAUD_CASE_CONFIRMED',
         N'{"confirmedLossSar":{">":500000}}',
         N'{"type":"CREATE_RECORD","entityType":"CapaTask","linkBack":"FraudCase","severity":"HIGH","template":"FRAUD_CONFIRMED_CAPA"}',
         1, N'CUSTOM');
END

-- ─── AR-186 ─ STR filed → notify MLRO + Compliance Officer ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-186')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-186',
         N'STR filed on fraud case → notify MLRO + Compliance Officer',
         N'إيداع بلاغ معاملة مشبوهة → إشعار مسؤول الإبلاغ ومسؤول الالتزام',
         N'SAFIU STR follow-up correspondence flows through the MLRO queue, and '
         + N'the Compliance Officer needs visibility for regulator-relationship '
         + N'tracking. AML/CFT Art.25 mandates the MLRO as the single accountable '
         + N'party for the STR; F8 keeps both inboxes in sync without requiring '
         + N'the Fraud Officer to remember to CC them.',
         N'تتدفّق مراسلات متابعة بلاغ معاملة مشبوهة (SAFIU) عبر صندوق وارد مسؤول '
         + N'الإبلاغ، ويحتاج مسؤول الالتزام للاطّلاع لمتابعة العلاقة مع الجهات '
         + N'الرقابية. تُلزم المادة 25 من نظام مكافحة غسل الأموال مسؤول الإبلاغ '
         + N'باعتباره الشخص الوحيد المساءل عن البلاغ؛ يحافظ المحرّك على تزامن '
         + N'الصندوقين دون الاعتماد على ذاكرة مسؤول الاحتيال.',
         N'FRAUD_STR_FILED',
         N'{}',
         N'{"type":"NOTIFY","roles":["MLRO","COMPLIANCE_OFFICER"],"template":"FRAUD_STR_FILED"}',
         2, N'CUSTOM');
END

-- ─── AR-187 ─ Law enforcement notified → flag legal hold ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-187')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-187',
         N'Law enforcement notified → flag legal hold on case',
         N'إبلاغ جهة إنفاذ القانون → وضع علامة حجز قانوني على القضية',
         N'Once CID / MHRSD / the Public Prosecution is engaged, every artefact '
         + N'on the case becomes evidence — staff cannot delete records, and the '
         + N'investigator must coordinate every external comms with legal counsel. '
         + N'The legal-hold flag turns on read-only mode for the case so the '
         + N'platform itself enforces evidentiary integrity.',
         N'بمجرّد إشراك المباحث أو وزارة الموارد البشرية أو النيابة العامة، '
         + N'يصبح كل أثر في القضية دليلاً — لا يستطيع الموظفون حذف السجلات، '
         + N'ويجب على المحقّق تنسيق كل اتصال خارجي مع المستشار القانوني. تُفعّل '
         + N'علامة الحجز القانوني وضع القراءة فقط على القضية لتفرض المنصة سلامة '
         + N'الأدلة بنفسها.',
         N'FRAUD_LAW_ENFORCEMENT_NOTIFIED',
         N'{}',
         N'{"type":"FLAG","flagCode":"LEGAL_HOLD","entityType":"FraudCase","template":"FRAUD_LAW_ENFORCEMENT_HOLD"}',
         1, N'CUSTOM');
END

-- ─── AR-188 ─ INTERNAL_EMBEZZLEMENT confirmed → HR parallel track ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-188')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-188',
         N'Internal embezzlement CONFIRMED → notify Head of HR + Internal Audit',
         N'تأكيد اختلاس داخلي → إشعار رئيس الموارد البشرية والتدقيق الداخلي',
         N'Confirmed internal embezzlement triggers a parallel HR investigation '
         + N'track — Saudi Labour Law disciplinary procedures + clawback of '
         + N'incentives + termination paperwork all start from HR. Internal Audit '
         + N'also needs to validate that the control-design assessment for the '
         + N'breached role flagged this risk in the last quarter.',
         N'يستوجب الاختلاس الداخلي المؤكَّد فتح مسار تحقيق موازٍ من الموارد '
         + N'البشرية — تبدأ إجراءات نظام العمل التأديبية واسترداد الحوافز '
         + N'وأوراق الإنهاء جميعها من الموارد البشرية. يحتاج التدقيق الداخلي '
         + N'أيضاً إلى التحقّق من أن تقييم تصميم الضوابط للدور المخالف رصد '
         + N'هذه المخاطرة في الربع الماضي.',
         N'FRAUD_CASE_CONFIRMED',
         N'{"typologyCode":"INTERNAL_EMBEZZLEMENT"}',
         N'{"type":"NOTIFY","roles":["HEAD_OF_HR","INTERNAL_AUDITOR","COMPLIANCE_OFFICER"],"template":"FRAUD_INTERNAL_EMBEZZLEMENT_HR"}',
         1, N'CUSTOM');
END

-- ─── AR-189 ─ Cyber-category fraud → notify CISO ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-189')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-189',
         N'Cyber-category fraud case → notify CISO (cross-team investigation)',
         N'قضية احتيال من فئة سيبرانية → إشعار رئيس أمن المعلومات (تحقيق متعدّد الفرق)',
         N'Cyber-category fraud (BEC, account takeover, phishing/vishing → ATO '
         + N'chain) typically has a credential-compromise or MFA-bypass root '
         + N'cause that lives in the CISO''s domain. SAMA Cyber Sub-domain 3.3.13 '
         + N'expects fraud and cyber teams to investigate jointly. The CISO '
         + N'gets the case at open-time so the cyber forensics work runs '
         + N'in parallel with the fraud-side triage.',
         N'الاحتيال السيبراني (BEC، الاستيلاء على الحساب، سلسلة التصيّد إلى الاستيلاء) '
         + N'لديه عادةً سبب جذري في اختراق بيانات الاعتماد أو تجاوز المصادقة '
         + N'المتعدّدة العوامل، وهي مجالات رئيس أمن المعلومات. يتوقّع القسم 3.3.13 '
         + N'من إطار الأمن السيبراني لدى البنك المركزي السعودي أن يحقّق فريقا '
         + N'الاحتيال والسيبراني معاً.',
         N'FRAUD_CASE_CREATED',
         N'{"samaCategoryCode":"CYBER"}',
         N'{"type":"NOTIFY","roles":["CISO","CYBER_INCIDENT_RESPONDER"],"template":"FRAUD_CYBER_CROSS_TEAM"}',
         2, N'CUSTOM');
END

-- ─── AR-190 ─ Case closed from CONFIRMED → lessons-learned record ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-190')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-190',
         N'Fraud case closed from CONFIRMED state → create lessons-learned record',
         N'إغلاق قضية احتيال من حالة مؤكَّدة → إنشاء سجل دروس مستفادة',
         N'A confirmed-then-closed fraud case is the highest-signal training data '
         + N'a bank has — every closure should produce a lessons-learned record '
         + N'so the typology library, control library, and staff-awareness '
         + N'training programmes all get updated. Without F8 forcing the '
         + N'record creation, the lesson lives only in the closure notes and '
         + N'gets lost in two years.',
         N'قضية الاحتيال المؤكَّدة المغلقة هي أعلى بيانات تدريب جودةً لدى البنك — '
         + N'يجب أن ينتج كل إغلاق سجل دروس مستفادة لتحديث مكتبة الأنماط ومكتبة '
         + N'الضوابط وبرامج توعية الموظفين. بدون فرض المحرّك إنشاء السجل، يبقى '
         + N'الدرس في ملاحظات الإغلاق فقط ويُنسى خلال عامين.',
         N'FRAUD_CASE_CLOSED',
         N'{"priorStatus":"CONFIRMED"}',
         N'{"type":"CREATE_RECORD","entityType":"LessonsLearned","linkBack":"FraudCase","template":"FRAUD_LESSONS_LEARNED"}',
         3, N'CUSTOM');
END
