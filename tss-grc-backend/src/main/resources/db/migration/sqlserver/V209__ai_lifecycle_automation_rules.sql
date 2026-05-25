-- V209: F8 automation rules for AI/ML Model Lifecycle events.
--
-- Sprint 31 PR #2. Pairs with PR #268 (backend) by seeding 10 rules
-- consuming MODEL_VALIDATION_*, MODEL_RETRAINING_*, MODEL_RECERTIFICATION_*
-- events emitted by AiLifecycleService.
--
-- Regulatory anchors:
--   * SDAIA AI Ethics Principles (2023) — eight principles
--   * PDPL Article 4 — automated decision-making safeguards
--   * SAMA Cyber Security Framework §3.4 — model-risk management
--   * EU AI Act risk-tier alignment (CRITICAL/HIGH/MEDIUM/LOW)
--   * NCA ECC-2 (2023) §3-4 — secure development for ML pipelines
--
-- AR-271..AR-280.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-271 — Validation scheduled (CRITICAL/HIGH) → notify Validator + DPO + CRO
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-271')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-271',
        N'Model validation scheduled → notify validator + DPO + CRO',
        N'جدولة تحقق نموذج → إشعار المدقق وحامي البيانات ومدير المخاطر',
        N'New independent validation event opens. Validator must complete in 30 days for CRITICAL/HIGH, 60 days for MEDIUM, 90 days for LOW.',
        N'حدث تحقق مستقل جديد يُفتح. على المدقق الإنجاز خلال ٣٠ يوماً للنماذج الحرجة/العالية، و٦٠ يوماً للمتوسطة، و٩٠ يوماً للمنخفضة.',
        N'MODEL_VALIDATION_SCHEDULED', N'{}',
        N'{"type":"NOTIFY","roles":["MODEL_VALIDATOR","DPO","CRO"],"template":"VALIDATION_KICKOFF"}',
        2, N'CUSTOM');

-- AR-272 — Validation FAILED → CRITICAL CAPA + CISO/DPO/CRO escalate
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-272')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-272',
        N'Validation FAILED → ESCALATE + CRITICAL CAPA',
        N'فشل التحقق → تصعيد + إجراء تصحيحي حرج',
        N'A failed validation event blocks any new production deployment of the model. CISO + DPO + CRO must triage immediately; a CRITICAL CAPA tracks remediation + revalidation.',
        N'فشل التحقق يمنع أي نشر إنتاجي جديد للنموذج. مسؤول أمن المعلومات وحامي البيانات ومدير المخاطر يقومون بالفرز فوراً؛ إجراء تصحيحي حرج يتابع العلاج وإعادة التحقق.',
        N'MODEL_VALIDATION_FAILED', N'{}',
        N'{"type":"ESCALATE","roles":["CISO","DPO","CRO","MODEL_VALIDATOR"],"andCreate":{"entity":"CAPA","title":"Model validation failed — remediate","priority":"CRITICAL","sourceModule":"AI_LIFECYCLE"}}',
        1, N'CUSTOM');

-- AR-273 — PASSED_WITH_CONDITIONS → MEDIUM CAPA tracking conditions
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-273')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-273',
        N'Validation PASSED_WITH_CONDITIONS → MEDIUM CAPA',
        N'تحقق بشروط → إجراء تصحيحي متوسط',
        N'Conditional pass means deploy is allowed but specific remediation must complete within the validation cycle. CAPA tracks each condition with target close date.',
        N'الإجازة المشروطة تعني السماح بالنشر مع وجوب إكمال علاج محدد خلال دورة التحقق. الإجراء التصحيحي يتابع كل شرط بموعد إغلاق محدد.',
        N'MODEL_VALIDATION_PASSED_WITH_CONDITIONS', N'{}',
        N'{"type":"NOTIFY","roles":["MODEL_VALIDATOR","DPO"],"andCreate":{"entity":"CAPA","title":"Validation conditions — complete remediation","priority":"MEDIUM","sourceModule":"AI_LIFECYCLE"}}',
        2, N'CUSTOM');

-- AR-274 — Fairness disparity > 10% → CRITICAL escalate (SDAIA Fairness principle)
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-274')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-274',
        N'Validation fairness disparity > 10% → CRITICAL ESCALATE (SDAIA Fairness)',
        N'تفاوت العدالة > ١٠٪ → تصعيد حرج (مبدأ العدالة SDAIA)',
        N'SDAIA AI Ethics Principles require demographic disparities below acceptable thresholds. A disparity over 10% between protected subgroups triggers escalation to CRO + DPO + Sharia Officer (where applicable) for bias mitigation review.',
        N'مبادئ أخلاقيات الذكاء الاصطناعي SDAIA تستوجب أن تكون الفروق الديموغرافية دون الحدود المقبولة. تجاوز التفاوت ١٠٪ بين الفئات المحمية يفعّل التصعيد لمدير المخاطر والحامي ومسؤول الشريعة (إن ينطبق) لمراجعة التخفيف من التحيّز.',
        N'MODEL_VALIDATION_PASSED_WITH_CONDITIONS', N'{"fairnessDisparityPct":{"gt":10.0}}',
        N'{"type":"ESCALATE","roles":["CRO","DPO","SHARIA_OFFICER","MODEL_VALIDATOR"],"andCreate":{"entity":"CAPA","title":"Fairness disparity remediation","priority":"CRITICAL","sourceModule":"AI_LIFECYCLE"}}',
        1, N'CUSTOM');

-- AR-275 — Retraining triggered by DRIFT → fast-track APPROVED gate
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-275')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-275',
        N'Retraining triggered by DRIFT → fast-track approval ladder',
        N'إعادة تدريب بسبب الانحراف → سلّم اعتماد متسارع',
        N'Drift-triggered retraining is operationally urgent: model accuracy has materially degraded. Owner gets T-3-day reminder ladder to push from PROPOSED to APPROVED; CISO + DPO are auto-notified.',
        N'إعادة التدريب بدافع الانحراف عاجلة تشغيلياً: تدهور دقة النموذج. يحصل المالك على سلّم تذكير ٣ أيام للنقل من المقترَح إلى المعتمَد؛ ومسؤول أمن المعلومات وحامي البيانات يُخطَران تلقائياً.',
        N'MODEL_RETRAINING_PROPOSED', N'{"triggerReason":{"eq":"DRIFT"}}',
        N'{"type":"ESCALATE","roles":["MODEL_OWNER","CISO","DPO"],"reminderLadder":["T-3D","T-1D","T-0"],"template":"DRIFT_RETRAIN"}',
        1, N'CUSTOM');

-- AR-276 — Retraining DEPLOYED → CREATE_RECORD 7yr (audit + EU AI Act traceability)
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-276')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-276',
        N'Retraining DEPLOYED → evidence vault record (7yr)',
        N'نشر إعادة التدريب → سجل في خزانة الأدلة (٧ سنوات)',
        N'Every deployment is captured as evidence with pre/post-metrics, version label, owner, approver, and validation reference. Supports SAMA on-site review + EU AI Act traceability.',
        N'كل نشر يُلتقط كدليل مع المقاييس قبل/بعد، وملصق الإصدار، والمالك، والمعتمد، ومرجع التحقق. يدعم الزيارة الميدانية لساما وقابلية التتبع وفق قانون الذكاء الاصطناعي الأوروبي.',
        N'MODEL_RETRAINING_DEPLOYED', N'{}',
        N'{"type":"CREATE_RECORD","entity":"EVIDENCE","retentionYears":7,"context":"MODEL_DEPLOYMENT"}',
        2, N'CUSTOM');

-- AR-277 — Retraining ROLLED_BACK → CRITICAL escalate + post-mortem
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-277')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-277',
        N'Retraining ROLLED_BACK → CRITICAL post-mortem CAPA + CRO',
        N'تراجع عن إعادة التدريب → تشريح حرج بعد الحدث + مدير المخاطر',
        N'A production rollback is a model-risk incident. CISO + DPO + CRO triage; post-mortem CAPA captures what failed (drift, regression, fairness, ops bug).',
        N'التراجع الإنتاجي حادث في مخاطر النموذج. مسؤول أمن المعلومات وحامي البيانات ومدير المخاطر يقومون بالفرز؛ تشريح بعد الحدث يلتقط ما فشل (انحراف، ارتداد، عدالة، خلل تشغيلي).',
        N'MODEL_RETRAINING_ROLLED_BACK', N'{}',
        N'{"type":"ESCALATE","roles":["CISO","DPO","CRO","MODEL_OWNER"],"andCreate":{"entity":"CAPA","title":"Model rollback post-mortem","priority":"CRITICAL","sourceModule":"AI_LIFECYCLE"}}',
        1, N'CUSTOM');

-- AR-278 — Recertification overdue → T+0/T+15/T+30 ladder
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-278')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-278',
        N'Recertification overdue → T+0 / T+15 / T+30 reminder ladder',
        N'تأخّر إعادة الاعتماد → سلّم تذكير T+0 / T+15 / T+30',
        N'Annual recertification past due date is a SAMA-on-site red flag for CRITICAL/HIGH-tier models. Ladder pages model owner immediately, CRO at +15d, Board Risk at +30d.',
        N'تأخّر إعادة الاعتماد السنوية عن موعدها علامة حمراء عند الزيارة الميدانية لساما للنماذج الحرجة/العالية. يُخطر السلّم المالك فوراً، ومدير المخاطر بعد ١٥ يوماً، ومخاطر المجلس بعد ٣٠ يوماً.',
        N'MODEL_RECERTIFICATION_DUE', N'{}',
        N'{"type":"ESCALATE","schedule":"DAILY_AT_08","reminderLadder":["T+0","T+15D","T+30D"],"roles":["MODEL_OWNER","CRO","BOARD_RISK_CHAIR"]}',
        1, N'CUSTOM');

-- AR-279 — Recertification REVOKED → suspend production + CRO escalate
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-279')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-279',
        N'Recertification REVOKED → suspend production + CRO',
        N'إلغاء إعادة الاعتماد → إيقاف الإنتاج + مدير المخاطر',
        N'A revoked recertification means the model must come out of production. CRO + CISO + CIO get same-day notification; a CRITICAL CAPA tracks decommission or major-revision path.',
        N'إلغاء إعادة الاعتماد يعني خروج النموذج من الإنتاج. مدير المخاطر ومسؤول أمن المعلومات ورئيس التقنية يُخطَرون في اليوم نفسه؛ إجراء تصحيحي حرج يتابع مسار التقاعد أو المراجعة الكبرى.',
        N'MODEL_RECERTIFICATION_REVOKED', N'{}',
        N'{"type":"ESCALATE","roles":["CRO","CISO","CIO","MODEL_OWNER"],"andCreate":{"entity":"CAPA","title":"Model recertification revoked — decommission or revise","priority":"CRITICAL","sourceModule":"AI_LIFECYCLE"}}',
        1, N'CUSTOM');

-- AR-280 — Annual SDAIA ethics attestation due (recurring T-30/T-7/T-0)
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = @org AND rule_code = N'AR-280')
INSERT INTO platform.automation_rules (organisation_id, rule_code, name_en, name_ar, description_en, description_ar, trigger_event, condition_json, action_json, priority, source)
VALUES (@org, N'AR-280',
        N'Annual SDAIA ethics attestation due → reminder ladder',
        N'استحقاق التأكيد الأخلاقي السنوي SDAIA → سلّم تذكير',
        N'For every CRITICAL/HIGH-tier model, the bank attests annually to the eight SDAIA AI Ethics Principles. Reminder ladder T-30/T-7/T-0 pages model owner + DPO + CRO; CREATE_RECORD on completion for the next external audit.',
        N'لكل نموذج حرج/عالي، يقرّ البنك سنوياً بمبادئ أخلاقيات الذكاء الاصطناعي SDAIA الثمانية. سلّم التذكير T-30/T-7/T-0 يُخطر المالك والحامي ومدير المخاطر؛ ويُنشأ سجلّ عند الإتمام للمراجعة الخارجية القادمة.',
        N'MODEL_ANNUAL_ETHICS_ATTESTATION_DUE', N'{}',
        N'{"type":"NOTIFY","schedule":"ANNUAL","reminderLadder":["T-30D","T-7D","T-0"],"roles":["MODEL_OWNER","DPO","CRO"],"andCreate":{"entity":"EVIDENCE","context":"SDAIA_ETHICS_ATTESTATION","retentionYears":7}}',
        2, N'CUSTOM');

GO
