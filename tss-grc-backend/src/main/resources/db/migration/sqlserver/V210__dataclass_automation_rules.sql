-- V210: F8 automation rules for Data Classification + NDMO events.
--
-- Sprint 32 PR #2. Hooks AR-281..AR-290 into events emitted by
-- DataclassService.
--
-- Regulatory anchors: NDMO Data Classification Policy (4-level scheme),
-- PDPL Art.4 (special-category), Art.6 (lawful basis), Art.7 (cross-
-- border transfer), SAMA Cloud Framework §4 (tier alignment),
-- NCA ECC-2 §4-1 + §4-2.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-281 — Asset registered → notify Data Steward + DPO
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-281')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-281',
        N'Data asset registered → notify steward + DPO',
        N'تسجيل أصل بيانات → إشعار المسؤول وحامي البيانات',
        N'A new data asset has 30 days to reach CLASSIFIED status. NDMO Data Classification Policy requires every asset has a custodian and a classification.',
        N'لأصل البيانات الجديد ٣٠ يوماً للوصول إلى حالة مُصنَّف. سياسة NDMO تستوجب لكل أصل مسؤولاً وتصنيفاً.',
        N'DATA_ASSET_REGISTERED', N'{}',
        N'{"type":"NOTIFY","roles":["DATA_STEWARD","DPO"],"template":"DATA_ASSET_30D"}',
        2, N'CUSTOM');

-- AR-282 — Asset classified TOP_SECRET/CONFIDENTIAL → notify CISO + DPO
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-282')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-282',
        N'TOP_SECRET / CONFIDENTIAL classification → notify CISO + DPO',
        N'تصنيف سرّي للغاية / سرّي → إشعار مسؤول أمن المعلومات والحامي',
        N'High-classification assets require additional safeguards: encryption at rest, masking, in-Kingdom residency (SAMA Cloud Framework §4 alignment).',
        N'الأصول عالية التصنيف تستوجب ضمانات إضافية: تشفير في الراحة، إخفاء، إقامة داخل المملكة (مواءمة مع §٤ من إطار ساما للحوسبة السحابية).',
        N'DATA_ASSET_CLASSIFIED', N'{"classification":{"in":["CONFIDENTIAL","TOP_SECRET"]}}',
        N'{"type":"NOTIFY","roles":["CISO","DPO","CIO"],"template":"DATA_CLASS_HIGH"}',
        1, N'CUSTOM');

-- AR-283 — Special-category data → DPO CREATE_RECORD evidence vault
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-283')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-283',
        N'PDPL Art.4 special-category data classified → DPO evidence vault record',
        N'تصنيف بيانات حسّاسة وفق المادة ٤ → سجل لدى الحامي في خزانة الأدلة',
        N'PDPL Article 4 special-category data (health, biometric, genetic, religious, racial, political, criminal) needs an explicit lawful-basis tag + DPIA reference + DPO sign-off in the evidence vault (7yr retention).',
        N'البيانات الحساسة وفق المادة ٤ من نظام حماية البيانات (الصحة، البصمات الحيوية، الوراثة، الدين، العرق، السياسة، الجريمة) تحتاج وسماً صريحاً للأساس القانوني، ومرجع DPIA، وموافقة الحامي في خزانة الأدلة بمدة احتفاظ ٧ سنوات.',
        N'DATA_ASSET_CLASSIFIED', N'{"specialCategory":{"ne":"NONE"}}',
        N'{"type":"CREATE_RECORD","entity":"EVIDENCE","retentionYears":7,"context":"PDPL_ART4_SPECIAL","alsoNotify":["DPO","CISO"]}',
        1, N'CUSTOM');

-- AR-284 — Asset reclassified UP → notify CISO + DPO + audit
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-284')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-284',
        N'Asset reclassified upward → notify CISO + DPO + audit',
        N'إعادة تصنيف الأصل لمستوى أعلى → إشعار مسؤول أمن المعلومات والحامي والمراجعة',
        N'Upward reclassification (e.g. INTERNAL → CONFIDENTIAL) means existing handling controls may now be insufficient. CISO + DPO triage controls; Internal Audit notified for the next cycle.',
        N'إعادة التصنيف لمستوى أعلى (مثلاً داخلي → سرّي) تعني أن ضوابط المعالجة الحالية قد لا تكون كافية. مسؤول أمن المعلومات والحامي يفحصان الضوابط؛ والتدقيق الداخلي يُخطر للدورة التالية.',
        N'DATA_ASSET_RECLASSIFIED_UP', N'{}',
        N'{"type":"NOTIFY","roles":["CISO","DPO","INTERNAL_AUDITOR"],"andCreate":{"entity":"CAPA","title":"Reclassified asset — uplift controls","priority":"MEDIUM","sourceModule":"DATACLASS"}}',
        2, N'CUSTOM');

-- AR-285 — Cross-border flow detected (source CONFIDENTIAL+) → DPO + PDPL Art.7 review
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-285')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-285',
        N'Cross-border flow of CONFIDENTIAL/TOP_SECRET → PDPL Art.7 review',
        N'تدفق عبر الحدود لبيانات سرّية/سرّية للغاية → مراجعة المادة ٧',
        N'PDPL Art.7 governs cross-border transfers of personal/sensitive data. Service-layer guard already blocks APPROVED promotion without a transfer ref; this rule pages the DPO + Compliance to author the transfer authorisation.',
        N'تنظّم المادة ٧ من نظام حماية البيانات نقل البيانات الشخصية/الحساسة عبر الحدود. الحارس على مستوى الخدمة يمنع الترقية إلى المعتمَد دون مرجع نقل؛ وهذه القاعدة تنبّه الحامي والالتزام لتأليف تفويض النقل.',
        N'DATA_FLOW_CROSS_BORDER_DETECTED', N'{}',
        N'{"type":"ESCALATE","roles":["DPO","COMPLIANCE_OFFICER","CISO"],"andCreate":{"entity":"CAPA","title":"Cross-border flow — author PDPL Art.7 authorisation","priority":"HIGH","sourceModule":"DATACLASS"}}',
        1, N'CUSTOM');

-- AR-286 — Flow approved → CREATE_RECORD evidence + register
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-286')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-286',
        N'Data flow approved → evidence vault record (5yr) + register entry',
        N'اعتماد تدفّق بيانات → سجل في خزانة الأدلة (٥ سنوات) + قيد بالسجل',
        N'Approved flows are entered into the master flow register with source / destination / classification / lawful basis / DPIA + Art.7 references. Audit trail captured in the evidence vault.',
        N'تُدرَج التدفقات المعتمدة في سجل التدفقات الرئيسي بالمصدر والوجهة والتصنيف والأساس القانوني ومراجع DPIA والمادة ٧. ويُلتقط مسار التدقيق في خزانة الأدلة.',
        N'DATA_FLOW_APPROVED', N'{}',
        N'{"type":"CREATE_RECORD","entity":"EVIDENCE","retentionYears":5,"context":"DATA_FLOW_APPROVED"}',
        2, N'CUSTOM');

-- AR-287 — Exception requested → DPO + CISO triage SLA 5 days
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-287')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-287',
        N'Data exception requested → 5-day DPO/CISO triage',
        N'طلب استثناء بيانات → فرز خلال ٥ أيام من الحامي ومسؤول أمن المعلومات',
        N'Exception requests need bounded turnaround. T-3D / T-1D / T-0 reminder ladder; CAPA opens if not decided by SLA. Compensating-controls section is mandatory.',
        N'طلبات الاستثناء تحتاج زمن استجابة محدد. سلّم تذكير T-3D / T-1D / T-0؛ ويُفتح إجراء تصحيحي إن لم يُحسم خلال المدة. خانة الضوابط التعويضية إلزامية.',
        N'DATA_EXCEPTION_REQUESTED', N'{}',
        N'{"type":"ESCALATE","roles":["DPO","CISO"],"reminderLadder":["T-3D","T-1D","T-0"],"slaDays":5}',
        1, N'CUSTOM');

-- AR-288 — Exception APPROVED → CREATE_RECORD 7yr + auto-expire date
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-288')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-288',
        N'Exception approved → evidence vault record (7yr) + auto-expire reminder',
        N'اعتماد استثناء → سجل في خزانة الأدلة (٧ سنوات) + تذكير انتهاء صلاحية',
        N'Every approved exception is auditable. CREATE_RECORD captures the compensating controls + valid_until; a reminder fires T-30 / T-7 / T-0 before validUntil so review/renewal/revoke is intentional.',
        N'كل استثناء معتمد قابل للتدقيق. ينشئ السجل ضوابط التعويض و«صالح حتى»؛ ويُطلق تذكير T-30 / T-7 / T-0 قبل انتهاء الصلاحية لضمان المراجعة/التجديد/الإلغاء بقصد.',
        N'DATA_EXCEPTION_APPROVED', N'{}',
        N'{"type":"CREATE_RECORD","entity":"EVIDENCE","retentionYears":7,"context":"DATA_EXCEPTION","reminderLadder":["T-30D","T-7D","T-0"]}',
        2, N'CUSTOM');

-- AR-289 — Asset review due → notify steward + DPO
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-289')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-289',
        N'Data asset review due → notify steward + DPO',
        N'استحقاق مراجعة أصل البيانات → إشعار المسؤول وحامي البيانات',
        N'Classification needs periodic refresh — typically annual for CONFIDENTIAL/TOP_SECRET, biennial for INTERNAL. Reminder fires from nextReviewDue.',
        N'يحتاج التصنيف تحديثاً دورياً — عادةً سنوياً للسرّي/السرّي للغاية، وكلّ سنتين للداخلي. يُطلق التذكير من تاريخ المراجعة التالي.',
        N'DATA_REVIEW_DUE', N'{}',
        N'{"type":"NOTIFY","roles":["DATA_STEWARD","DPO"],"template":"DATA_REVIEW"}',
        2, N'CUSTOM');

-- AR-290 — Exception revoked / expired → CRITICAL escalate if asset still IN_USE
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-290')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-290',
        N'Exception revoked / expired → CRITICAL escalate if asset still in use',
        N'إلغاء/انتهاء صلاحية استثناء → تصعيد حرج إذا كان الأصل ما زال قيد الاستخدام',
        N'When an exception expires or is revoked, the underlying control gap is open again. If the asset/flow is still IN_USE/ACTIVE, escalate CISO + DPO + asset owner immediately to either remediate the control or refile a new exception.',
        N'حين تنتهي صلاحية الاستثناء أو يُلغى، تعود فجوة الضابط مفتوحة. إذا كان الأصل/التدفق لا يزال قيد الاستخدام/نشطاً، يُصعَّد فوراً لمسؤول أمن المعلومات والحامي ومالك الأصل لإصلاح الضابط أو إعادة تقديم استثناء جديد.',
        N'DATA_EXCEPTION_EXPIRED', N'{}',
        N'{"type":"ESCALATE","roles":["CISO","DPO","DATA_OWNER"],"andCreate":{"entity":"CAPA","title":"Exception lapsed — remediate or refile","priority":"CRITICAL","sourceModule":"DATACLASS"}}',
        1, N'CUSTOM');

GO
