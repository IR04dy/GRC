-- V105: F8 rules for the new BCM events shipped by the audit follow-up sprints.
--
-- Rules added:
--   BCM-T5  — DR test failed → corrective action (closes audit Tier-1 #4)
--   BCM-T6  — Crisis severity ≥4 → escalate to executive sponsor (spec §7.16)
--   BCM-T7  — Crisis with personal-data → PDPL placeholder (spec §7.15)
--   BCM-T8  — Crisis cyber-linked → flag for cyber module
--   BCM-T9  — Crisis supplier-involved → flag for SCM module
--   BCM-T10 — BIA SOVEREIGN criticality → require board sign-off
--   BCM-T11 — BIA approved → notify BCM manager
--
-- All ride the same F8 engine and emit through the new CREATE_RECORD /
-- NOTIFY / ESCALATE / FLAG handlers shipped in PR #8.

IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = 1 AND rule_code = 'BCM-T5')
BEGIN
    INSERT INTO platform.automation_rules
      (organisation_id, rule_code, name_en, name_ar, description_en,
       trigger_event, condition_json, action_json, priority, source)
    VALUES
      (1, 'BCM-T5',
       'DR test failed → corrective action',
       N'فشل اختبار التعافي → إجراء تصحيحي',
       'When a DRP test outcome is FAILED or GAP, auto-create a BCM CAPA so the DR plan is repaired before the next test window. Closes audit Tier-1 #4 (spec §7 rule 9).',
       'BCM_DR_TEST_FAILED',
       NULL,
       '{"type":"CREATE_RECORD","entity":"BcmCorrectiveAction","fields":{"priority":"HIGH","title_template":"DR test failed: %plan_code%","source_hint":"DR_TEST"}}',
       10, 'BCM_MODULE'),

      (1, 'BCM-T6',
       'Crisis severity ≥ 4 → escalate to executive sponsor',
       N'شدة الأزمة ≥ 4 → تصعيد للراعي التنفيذي',
       'Per spec §J: level 4/5 crisis events (CRISIS / NATIONAL_EMERGENCY) require executive notification.',
       'BCM_CRISIS_HIGH_SEVERITY',
       NULL,
       '{"type":"ESCALATE","to_role":"EXECUTIVE_SPONSOR","title":"High-severity crisis activated","priority":"CRITICAL"}',
       5, 'BCM_MODULE'),

      (1, 'BCM-T7',
       'Crisis with personal-data → PDPL breach placeholder',
       N'أزمة مع بيانات شخصية → خرق PDPL',
       'When a crisis event flags personal_data_affected, raise a PDPL breach placeholder so the privacy team can investigate per spec §7.15.',
       'BCM_CRISIS_PDPL_BREACH',
       NULL,
       '{"type":"CREATE_RECORD","entity":"DataBreach","fields":{"source":"BCM_CRISIS","severity":"HIGH"}}',
       5, 'BCM_MODULE'),

      (1, 'BCM-T8',
       'Crisis cyber-linked → flag for cyber module',
       N'أزمة مرتبطة بالأمن السيبراني → علم',
       'When a crisis event is flagged cyber_related, notify the cybersecurity team so they can correlate with any open incident.',
       'BCM_CRISIS_CYBER_LINKED',
       NULL,
       '{"type":"NOTIFY","role":"CYBERSECURITY_MANAGER","title":"BCM crisis cyber-linked"}',
       10, 'BCM_MODULE'),

      (1, 'BCM-T9',
       'Crisis supplier-involved → flag for SCM module',
       N'أزمة تشمل مورّداً → علم',
       'Notify the supplier owner role so the SCM team can review the supplier risk profile.',
       'BCM_CRISIS_SUPPLIER_LINKED',
       NULL,
       '{"type":"NOTIFY","role":"SUPPLIER_OWNER","title":"BCM crisis supplier-involved"}',
       10, 'BCM_MODULE'),

      (1, 'BCM-T10',
       'BIA SOVEREIGN criticality → require board sign-off',
       N'تحليل أثر سيادي → موافقة مجلس الإدارة',
       'When an approved BIA classifies as SOVEREIGN criticality (national_sovereign dimension ≥ 4), escalate to the board secretary so the next board pack carries the matter.',
       'BIA_CRITICALITY_SOVEREIGN',
       NULL,
       '{"type":"ESCALATE","to_role":"BOARD_SECRETARY","title":"Sovereign-tier BIA awaiting board sign-off","priority":"CRITICAL"}',
       5, 'BCM_MODULE'),

      (1, 'BCM-T11',
       'BIA approved → notify BCM manager',
       N'تحليل أثر معتمد → إشعار',
       'Inbox heads-up to the BCM manager so they can schedule the follow-on BCP/DRP work for the approved BIA.',
       'BIA_APPROVED',
       NULL,
       '{"type":"NOTIFY","role":"BCM_MANAGER","title":"New BIA approved — ready for BCP/DRP"}',
       20, 'BCM_MODULE');
END
GO
