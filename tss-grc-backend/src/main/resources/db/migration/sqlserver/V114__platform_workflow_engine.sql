-- V114: Workflow-engine seed expansion + cleanup — buyer assessment §6.1.
--
-- IMPORTANT BACKSTORY: an earlier draft of this migration created a parallel
-- set of "platform.workflow_*" tables before discovering that a configurable
-- workflow engine already exists under the "workflow.*" schema (with
-- WorkflowEngineController + WorkflowEngineService + 5 entities + the
-- workflow.definitions / states / transitions / instances / action_log tables).
-- The existing engine only had 1 reference workflow (RISK_LIFECYCLE_V1) seeded,
-- which the buyer doc correctly flagged as "ship 5-10 reference workflows
-- pre-built so the platform feels usable from day one".
--
-- This migration:
--   1. Drops any platform.workflow_* tables (left over if V114 was applied
--      against an earlier draft) so the canonical engine in workflow.*
--      stays the single source of truth.
--   2. Seeds 5 additional reference workflows into the existing engine:
--        - WF-INCIDENT-NIST       Incident response (NIST 800-61)
--        - WF-VENDOR-ONBOARDING   Vendor onboarding
--        - WF-PDPL-BREACH         PDPL Article 23 breach notification
--        - WF-POLICY-LIFECYCLE    Policy lifecycle
--        - WF-GENERIC-APPROVAL    Reusable 4-step approval
--
-- Apply with: sqlcmd -f 65001 (UTF-8 codepage) — Arabic literals in
-- state names and definition descriptions need it.

SET NOCOUNT ON;
GO

-- ─── Step 1: clean up the abandoned platform.workflow_* tables ─────
IF OBJECT_ID('platform.workflow_instance_history','U') IS NOT NULL
    DROP TABLE platform.workflow_instance_history;
IF OBJECT_ID('platform.workflow_instances','U') IS NOT NULL
    DROP TABLE platform.workflow_instances;
IF OBJECT_ID('platform.workflow_actions','U') IS NOT NULL
    DROP TABLE platform.workflow_actions;
IF OBJECT_ID('platform.workflow_transitions','U') IS NOT NULL
    DROP TABLE platform.workflow_transitions;
IF OBJECT_ID('platform.workflow_states','U') IS NOT NULL
    DROP TABLE platform.workflow_states;
IF OBJECT_ID('platform.workflow_definitions','U') IS NOT NULL
    DROP TABLE platform.workflow_definitions;
GO

-- ─── Step 2: seed reference workflows into the existing engine ────
-- Each definition is created in workflow.definitions, then its states
-- and transitions inserted with FK lookups. Idempotent on code uniqueness.

-- ─── 1. Generic 4-step approval ───────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM workflow.definitions WHERE code = N'WF-GENERIC-APPROVAL')
BEGIN
    DECLARE @defId BIGINT;
    INSERT INTO workflow.definitions (organisation_id, code, name_en, name_ar, target_module, target_entity, version, description_en, is_active)
    VALUES (1, N'WF-GENERIC-APPROVAL', N'Generic four-step approval', N'موافقة عامة من أربع خطوات',
            N'*', N'*', 1,
            N'Reusable approval flow: DRAFT → UNDER_REVIEW → APPROVED, with REJECTED short-circuit.', 1);
    SET @defId = SCOPE_IDENTITY();
    INSERT INTO workflow.states (definition_id, code, name_en, name_ar, is_initial, is_terminal, sort_order, ui_color) VALUES
      (@defId, N'DRAFT',        N'Draft',        N'مسودة',         1, 0, 1, N'#94a3b8'),
      (@defId, N'UNDER_REVIEW', N'Under review', N'قيد المراجعة',  0, 0, 2, N'#f59e0b'),
      (@defId, N'APPROVED',     N'Approved',     N'معتمد',         0, 1, 3, N'#10b981'),
      (@defId, N'REJECTED',     N'Rejected',     N'مرفوض',         0, 1, 4, N'#ef4444');

    DECLARE @s_d BIGINT, @s_r BIGINT, @s_a BIGINT, @s_x BIGINT;
    SELECT @s_d = id FROM workflow.states WHERE definition_id=@defId AND code='DRAFT';
    SELECT @s_r = id FROM workflow.states WHERE definition_id=@defId AND code='UNDER_REVIEW';
    SELECT @s_a = id FROM workflow.states WHERE definition_id=@defId AND code='APPROVED';
    SELECT @s_x = id FROM workflow.states WHERE definition_id=@defId AND code='REJECTED';
    INSERT INTO workflow.transitions (definition_id, from_state_id, to_state_id, action_code, action_name_en, action_name_ar, required_role, sla_hours, sort_order) VALUES
      (@defId, @s_d, @s_r, N'submit',  N'Submit for review',  N'إرسال للمراجعة',  N'OWNER',    72,   1),
      (@defId, @s_r, @s_a, N'approve', N'Approve',            N'اعتماد',          N'MANAGER',  48,   2),
      (@defId, @s_r, @s_x, N'reject',  N'Reject',             N'رفض',             N'MANAGER',  NULL, 3),
      (@defId, @s_r, @s_d, N'reopen',  N'Reopen for edits',   N'إعادة فتح للتحرير', N'MANAGER', NULL, 4);
END
GO

-- ─── 2. Incident response (NIST 800-61) ──────────────────────────
IF NOT EXISTS (SELECT 1 FROM workflow.definitions WHERE code = N'WF-INCIDENT-NIST')
BEGIN
    DECLARE @defId BIGINT;
    INSERT INTO workflow.definitions (organisation_id, code, name_en, name_ar, target_module, target_entity, version, description_en, is_active)
    VALUES (1, N'WF-INCIDENT-NIST', N'Incident response (NIST 800-61)', N'الاستجابة للحوادث (NIST 800-61)',
            N'INCIDENT', N'Incident', 1,
            N'Detect → analyse → contain → eradicate → recover → post-incident → closed.', 1);
    SET @defId = SCOPE_IDENTITY();
    INSERT INTO workflow.states (definition_id, code, name_en, name_ar, is_initial, is_terminal, sort_order, ui_color) VALUES
      (@defId, N'DETECTED',      N'Detected',       N'مُكتشف',         1, 0, 1, N'#ef4444'),
      (@defId, N'ANALYSING',     N'Analysing',      N'قيد التحليل',    0, 0, 2, N'#f59e0b'),
      (@defId, N'CONTAINING',    N'Containing',     N'الاحتواء',       0, 0, 3, N'#f59e0b'),
      (@defId, N'ERADICATING',   N'Eradicating',    N'الاستئصال',      0, 0, 4, N'#f59e0b'),
      (@defId, N'RECOVERING',    N'Recovering',     N'التعافي',        0, 0, 5, N'#3b82f6'),
      (@defId, N'POST_INCIDENT', N'Post-incident',  N'ما بعد الحادث',  0, 0, 6, N'#3b82f6'),
      (@defId, N'CLOSED',        N'Closed',         N'مغلق',           0, 1, 7, N'#10b981');

    DECLARE @i_de BIGINT,@i_an BIGINT,@i_co BIGINT,@i_er BIGINT,@i_re BIGINT,@i_po BIGINT,@i_cl BIGINT;
    SELECT @i_de = id FROM workflow.states WHERE definition_id=@defId AND code='DETECTED';
    SELECT @i_an = id FROM workflow.states WHERE definition_id=@defId AND code='ANALYSING';
    SELECT @i_co = id FROM workflow.states WHERE definition_id=@defId AND code='CONTAINING';
    SELECT @i_er = id FROM workflow.states WHERE definition_id=@defId AND code='ERADICATING';
    SELECT @i_re = id FROM workflow.states WHERE definition_id=@defId AND code='RECOVERING';
    SELECT @i_po = id FROM workflow.states WHERE definition_id=@defId AND code='POST_INCIDENT';
    SELECT @i_cl = id FROM workflow.states WHERE definition_id=@defId AND code='CLOSED';
    INSERT INTO workflow.transitions (definition_id, from_state_id, to_state_id, action_code, action_name_en, action_name_ar, required_role, sla_hours, sort_order) VALUES
      (@defId, @i_de, @i_an, N'analyse',  N'Analyse',          N'تحليل',         N'SOC_LEAD', 1,   1),
      (@defId, @i_an, @i_co, N'contain',  N'Contain',          N'احتواء',        N'SOC_LEAD', 2,   2),
      (@defId, @i_co, @i_er, N'eradicate',N'Eradicate',        N'استئصال',       N'IR_LEAD',  24,  3),
      (@defId, @i_er, @i_re, N'recover',  N'Recover',          N'تعافٍ',          N'IR_LEAD',  72,  4),
      (@defId, @i_re, @i_po, N'post',     N'Post-incident',    N'ما بعد الحادث', N'CISO',     168, 5),
      (@defId, @i_po, @i_cl, N'close',    N'Close',            N'إغلاق',         N'CISO',     NULL,6);
END
GO

-- ─── 3. Vendor onboarding ────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM workflow.definitions WHERE code = N'WF-VENDOR-ONBOARDING')
BEGIN
    DECLARE @defId BIGINT;
    INSERT INTO workflow.definitions (organisation_id, code, name_en, name_ar, target_module, target_entity, version, description_en, is_active)
    VALUES (1, N'WF-VENDOR-ONBOARDING', N'Vendor onboarding', N'إدماج المورّدين',
            N'SCM', N'Vendor', 1,
            N'Intake → tiering → due diligence → security review → contract → approved.', 1);
    SET @defId = SCOPE_IDENTITY();
    INSERT INTO workflow.states (definition_id, code, name_en, name_ar, is_initial, is_terminal, sort_order, ui_color) VALUES
      (@defId, N'INTAKE',          N'Intake',           N'استلام',            1, 0, 1, N'#94a3b8'),
      (@defId, N'TIERING',         N'Tiering',          N'تصنيف',             0, 0, 2, N'#3b82f6'),
      (@defId, N'DUE_DILIGENCE',   N'Due diligence',    N'العناية الواجبة',   0, 0, 3, N'#f59e0b'),
      (@defId, N'SECURITY_REVIEW', N'Security review',  N'مراجعة أمنية',      0, 0, 4, N'#f59e0b'),
      (@defId, N'CONTRACT',        N'Contract',         N'عقد',               0, 0, 5, N'#3b82f6'),
      (@defId, N'APPROVED',        N'Approved',         N'معتمد',             0, 1, 6, N'#10b981'),
      (@defId, N'REJECTED',        N'Rejected',         N'مرفوض',             0, 1, 7, N'#ef4444');

    DECLARE @v_in BIGINT,@v_ti BIGINT,@v_dd BIGINT,@v_sr BIGINT,@v_co2 BIGINT,@v_ap BIGINT,@v_re2 BIGINT;
    SELECT @v_in  = id FROM workflow.states WHERE definition_id=@defId AND code='INTAKE';
    SELECT @v_ti  = id FROM workflow.states WHERE definition_id=@defId AND code='TIERING';
    SELECT @v_dd  = id FROM workflow.states WHERE definition_id=@defId AND code='DUE_DILIGENCE';
    SELECT @v_sr  = id FROM workflow.states WHERE definition_id=@defId AND code='SECURITY_REVIEW';
    SELECT @v_co2 = id FROM workflow.states WHERE definition_id=@defId AND code='CONTRACT';
    SELECT @v_ap  = id FROM workflow.states WHERE definition_id=@defId AND code='APPROVED';
    SELECT @v_re2 = id FROM workflow.states WHERE definition_id=@defId AND code='REJECTED';
    INSERT INTO workflow.transitions (definition_id, from_state_id, to_state_id, action_code, action_name_en, action_name_ar, required_role, sla_hours, sort_order) VALUES
      (@defId, @v_in,  @v_ti,  N'classify',  N'Classify tier',      N'تصنيف الفئة',       N'SCM_MANAGER', 48,  1),
      (@defId, @v_ti,  @v_dd,  N'diligence', N'Begin due diligence',N'بدء العناية الواجبة', N'SCM_MANAGER', 168, 2),
      (@defId, @v_dd,  @v_sr,  N'sec_review',N'Security review',    N'مراجعة أمنية',      N'CISO',        168, 3),
      (@defId, @v_sr,  @v_co2, N'contract',  N'Begin contract',     N'بدء التعاقد',       N'LEGAL',       168, 4),
      (@defId, @v_co2, @v_ap,  N'approve',   N'Approve',            N'اعتماد',            N'CFO',         NULL,5),
      (@defId, @v_sr,  @v_re2, N'reject',    N'Reject',             N'رفض',               N'CISO',        NULL,6);
END
GO

-- ─── 4. PDPL Article 23 breach notification ──────────────────────
IF NOT EXISTS (SELECT 1 FROM workflow.definitions WHERE code = N'WF-PDPL-BREACH')
BEGIN
    DECLARE @defId BIGINT;
    INSERT INTO workflow.definitions (organisation_id, code, name_en, name_ar, target_module, target_entity, version, description_en, is_active)
    VALUES (1, N'WF-PDPL-BREACH', N'PDPL Article 23 breach notification', N'إشعار الخرق وفق المادة 23 من PDPL',
            N'PRIVACY', N'CrisisEvent', 1,
            N'72-hour notification workflow per Saudi PDPL Article 23.', 1);
    SET @defId = SCOPE_IDENTITY();
    INSERT INTO workflow.states (definition_id, code, name_en, name_ar, is_initial, is_terminal, sort_order, ui_color) VALUES
      (@defId, N'DETECTED',           N'Detected',                N'مُكتشف',                 1, 0, 1, N'#ef4444'),
      (@defId, N'ASSESSING',          N'Assessing reportability', N'تقييم وجوب الإبلاغ',    0, 0, 2, N'#f59e0b'),
      (@defId, N'AUTHORITY_NOTIFIED', N'Authority notified',      N'تم إشعار الجهة',         0, 0, 3, N'#3b82f6'),
      (@defId, N'SUBJECTS_NOTIFIED',  N'Data subjects notified',  N'تم إشعار أصحاب البيانات', 0, 0, 4, N'#3b82f6'),
      (@defId, N'CLOSED',             N'Closed',                  N'مغلق',                   0, 1, 5, N'#10b981');

    DECLARE @p_de BIGINT,@p_as BIGINT,@p_an BIGINT,@p_sn BIGINT,@p_cl BIGINT;
    SELECT @p_de = id FROM workflow.states WHERE definition_id=@defId AND code='DETECTED';
    SELECT @p_as = id FROM workflow.states WHERE definition_id=@defId AND code='ASSESSING';
    SELECT @p_an = id FROM workflow.states WHERE definition_id=@defId AND code='AUTHORITY_NOTIFIED';
    SELECT @p_sn = id FROM workflow.states WHERE definition_id=@defId AND code='SUBJECTS_NOTIFIED';
    SELECT @p_cl = id FROM workflow.states WHERE definition_id=@defId AND code='CLOSED';
    INSERT INTO workflow.transitions (definition_id, from_state_id, to_state_id, action_code, action_name_en, action_name_ar, required_role, sla_hours, sort_order) VALUES
      (@defId, @p_de, @p_as, N'assess',           N'Assess reportability', N'تقييم الإبلاغ',        N'DPO', 4,   1),
      (@defId, @p_as, @p_an, N'notify_authority', N'Notify authority',     N'إشعار الجهة',          N'DPO', 72,  2),
      (@defId, @p_an, @p_sn, N'notify_subjects',  N'Notify data subjects', N'إشعار أصحاب البيانات', N'DPO', 168, 3),
      (@defId, @p_sn, @p_cl, N'close',            N'Close',                N'إغلاق',                N'DPO', NULL,4);
END
GO

-- ─── 5. Policy lifecycle ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM workflow.definitions WHERE code = N'WF-POLICY-LIFECYCLE')
BEGIN
    DECLARE @defId BIGINT;
    INSERT INTO workflow.definitions (organisation_id, code, name_en, name_ar, target_module, target_entity, version, description_en, is_active)
    VALUES (1, N'WF-POLICY-LIFECYCLE', N'Policy lifecycle', N'دورة حياة السياسات',
            N'POLICY', N'Policy', 1,
            N'Draft → legal review → executive approval → published → review due → retired.', 1);
    SET @defId = SCOPE_IDENTITY();
    INSERT INTO workflow.states (definition_id, code, name_en, name_ar, is_initial, is_terminal, sort_order, ui_color) VALUES
      (@defId, N'DRAFT',         N'Draft',         N'مسودة',           1, 0, 1, N'#94a3b8'),
      (@defId, N'LEGAL_REVIEW',  N'Legal review',  N'مراجعة قانونية', 0, 0, 2, N'#f59e0b'),
      (@defId, N'EXEC_APPROVAL', N'Exec approval', N'اعتماد تنفيذي',  0, 0, 3, N'#f59e0b'),
      (@defId, N'PUBLISHED',     N'Published',     N'منشور',          0, 0, 4, N'#10b981'),
      (@defId, N'REVIEW_DUE',    N'Review due',    N'مراجعة مستحقّة',  0, 0, 5, N'#f59e0b'),
      (@defId, N'RETIRED',       N'Retired',       N'متقاعد',          0, 1, 6, N'#94a3b8');

    DECLARE @y_dr BIGINT,@y_lr BIGINT,@y_ea BIGINT,@y_pu BIGINT,@y_rd BIGINT,@y_rt BIGINT;
    SELECT @y_dr = id FROM workflow.states WHERE definition_id=@defId AND code='DRAFT';
    SELECT @y_lr = id FROM workflow.states WHERE definition_id=@defId AND code='LEGAL_REVIEW';
    SELECT @y_ea = id FROM workflow.states WHERE definition_id=@defId AND code='EXEC_APPROVAL';
    SELECT @y_pu = id FROM workflow.states WHERE definition_id=@defId AND code='PUBLISHED';
    SELECT @y_rd = id FROM workflow.states WHERE definition_id=@defId AND code='REVIEW_DUE';
    SELECT @y_rt = id FROM workflow.states WHERE definition_id=@defId AND code='RETIRED';
    INSERT INTO workflow.transitions (definition_id, from_state_id, to_state_id, action_code, action_name_en, action_name_ar, required_role, sla_hours, sort_order) VALUES
      (@defId, @y_dr, @y_lr, N'submit_legal', N'Submit to Legal',     N'إرسال للقانوني',     N'POLICY_OWNER',  168, 1),
      (@defId, @y_lr, @y_ea, N'submit_exec',  N'Submit to Exec',      N'إرسال للتنفيذي',     N'LEGAL',         168, 2),
      (@defId, @y_ea, @y_pu, N'publish',      N'Publish',             N'نشر',                N'EXECUTIVE',     NULL,3),
      (@defId, @y_pu, @y_rd, N'review_due',   N'Mark review due',     N'وسم مراجعة مستحقّة',  N'POLICY_OWNER',  NULL,4),
      (@defId, @y_rd, @y_rt, N'retire',       N'Retire',              N'تقاعد',              N'POLICY_OWNER',  NULL,5);
END
GO

PRINT 'V114 — workflow engine cleanup + 5 reference workflows seeded into workflow.* schema.';
GO
