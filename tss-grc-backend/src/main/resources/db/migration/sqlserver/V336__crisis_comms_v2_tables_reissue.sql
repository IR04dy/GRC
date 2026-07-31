/* ============================================================================
   V336 — crisis-comms v2 was shipped and then never given its tables. This is
          V172, re-issued above the Flyway baseline so it can actually run.

   F2-64. Idempotent: V172's own IF NOT EXISTS guards make it safe anywhere,
   which is precisely why re-issuing it is the right fix rather than a rewrite.

   ── WHY A CAPABILITY WAS DEAD IN BOTH DATABASES ────────────────────────────
   `bcm.crisis_comms_cascade` and `bcm.crisis_comms_template_v2` existed in
   NEITHER `TSS_GRC` nor `TSS_GRC_Deployment`, while the platform shipped, on top
   of them: two JPA entities, `CrisisCommsDispatchService`,
   `CrisisCommsTemplateService`, two controllers at `/api/v1/bcm/comms/v2*`, and
   THREE routed pages reached from the sidebar. The reason is structural, not a
   coding mistake:

     * dev never runs Flyway (the schema is Python-bootstrapped), so V172 was
       never executed here; and
     * prod was restored from a dev dump and baselined at `FLYWAY_BASELINE_VERSION
       = 292`, so Flyway records every migration numbered at or below 292 as
       already applied — whether or not it ever ran.

   V172 < 292, so it will never execute in either place. **The file is
   well-formed, correctly numbered and unique, which is exactly why F2-49's
   `MigrationFilenameRatchetTest` cannot see it.** Only comparing a migration's
   stated intent against the schema that exists finds this class — which is what
   `Appendix/scripts/schema_drift.py` now does, and how this was found.

   ── ACTIVATE, NOT RETIRE, AND THE REASON IS NOT SENTIMENT ─────────────────
   The alternative disposition was deleting three pages, a nav entry and two
   controllers. Activating wins on evidence: the code was written deliberately and
   completely, V172 carries **thirteen seeded notification templates** mapped to
   real Saudi obligations (SAMA SCRO, NCA ECC-2, SDAIA PDPL art. 31/32, CMA
   insider disclosure, plus customer/staff cascades), and the only thing wrong was
   a baseline accident. Deleting working, regulator-mapped code to tidy up a
   Flyway artefact destroys value that took real effort to create.

   ── HOW THIS FILE WAS PRODUCED ────────────────────────────────────────────
   The body below is V172's, copied programmatically rather than by hand, so no
   transcription drift is possible. **V172 must NOT be deleted**: it is the
   historical record for any environment that did apply it, and removing it would
   break `flyway:validate` there. Two files, one effect, guarded by IF NOT EXISTS
   — the standard remedy for a baseline-skipped migration.

   ── SHIPPED WITH IT (code, same slice) ────────────────────────────────────
   The nine endpoints were guarded by role names, and **two of the roles do not
   exist in `iam.roles`** (`CRISIS_COMMANDER`, `COMMS_LEAD`) — the F2-52 ghost-role
   class, where a role guard gets no PLATFORM_ADMIN bypass and simply never
   matches. They now use catalogued permissions, per F2-05's adopted direction:
   reads → `BCM:READ` (17 roles), template create/update → `BCM:WRITE` (10),
   archive → `BCM:DELETE` (4), and both **dispatch** and template **transition** →
   `BCM:APPROVE` (5). Dispatch is deliberately the strictest: it transmits a
   notification to a regulator, customers or staff, and that is an approval-grade
   act, not a write.
   ============================================================================ */

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('bcm') IS NULL EXEC('CREATE SCHEMA bcm');
GO

-- ─── crisis_comms_template_v2 ──────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'crisis_comms_template_v2' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.crisis_comms_template_v2 (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        template_code               VARCHAR(64)     NOT NULL,

        title_en                    NVARCHAR(255)   NOT NULL,
        title_ar                    NVARCHAR(255)   NOT NULL,

        -- 7-audience SAMA BCF Annex B catalogue.
        audience                    VARCHAR(40)     NOT NULL,
        -- BOARD | REGULATOR | CUSTOMERS | STAFF | PARTNERS | MEDIA | PUBLIC

        -- 10-scenario taxonomy.
        scenario_category           VARCHAR(40)     NOT NULL,
        -- RANSOMWARE | DATA_BREACH | OUTAGE | NATURAL_DISASTER | OT_INCIDENT
        -- | REGULATORY_NOTIFICATION | MEDIA_RESPONSE | RECOVERY
        -- | LESSONS_LEARNED | GENERIC_MAJOR

        severity_band               VARCHAR(20)     NOT NULL DEFAULT 'HIGH',
        -- CRITICAL | HIGH | MEDIUM | INFORMATIONAL

        subject_en                  NVARCHAR(255)   NOT NULL,
        subject_ar                  NVARCHAR(255)   NOT NULL,
        body_en                     NVARCHAR(MAX)   NOT NULL,
        body_ar                     NVARCHAR(MAX)   NOT NULL,

        -- 160-char short variants for SMS / WhatsApp blast. NULL when the
        -- template is email-only (long-form regulatory submissions etc.).
        sms_body_en                 NVARCHAR(500)   NULL,
        sms_body_ar                 NVARCHAR(500)   NULL,

        -- JSON array describing required placeholders + types — the
        -- dispatch wizard renders these as form inputs.
        placeholders_json           NVARCHAR(MAX)   NULL,

        -- Citation to the regulatory anchor this template helps satisfy.
        regulatory_anchor           VARCHAR(500)    NULL,

        -- Approval state machine.
        approval_status             VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | IN_REVIEW | APPROVED_BY_LEGAL | APPROVED_BY_CISO | RETIRED

        approved_by_legal_at        DATETIME2       NULL,
        approved_by_legal_name      NVARCHAR(255)   NULL,
        approved_by_ciso_at         DATETIME2       NULL,
        approved_by_ciso_name       NVARCHAR(255)   NULL,

        -- Standard audit + soft-delete + rowversion.
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION
    );

    -- Unique template_code per tenant.
    CREATE UNIQUE INDEX UX_ccv2_org_code
        ON bcm.crisis_comms_template_v2(organisation_id, template_code)
        WHERE is_deleted = 0;

    -- Filter index for the dispatch picker — "show me APPROVED templates
    -- for CUSTOMERS × DATA_BREACH".
    CREATE INDEX IX_ccv2_org_aud_scenario_status
        ON bcm.crisis_comms_template_v2(organisation_id, audience, scenario_category, approval_status)
        WHERE is_deleted = 0;
END
GO

-- ─── crisis_comms_cascade ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'crisis_comms_cascade' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.crisis_comms_cascade (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Soft FK to bcm.crisis_events.id (cycle module owns that table).
        crisis_event_id             BIGINT          NOT NULL,
        template_id                 BIGINT          NOT NULL,

        channel                     VARCHAR(20)     NOT NULL,
        -- EMAIL | SMS | WHATSAPP | TEAMS | VOICE_BLAST

        audience                    VARCHAR(40)     NULL,
        -- snapshot of the targeted audience at dispatch time

        rendered_subject            NVARCHAR(500)   NULL,
        rendered_body               NVARCHAR(MAX)   NULL,

        recipient_count             INT             NOT NULL DEFAULT 0,
        success_count               INT             NOT NULL DEFAULT 0,
        failure_count               INT             NOT NULL DEFAULT 0,

        dispatched_by_user_id       BIGINT          NULL,
        dispatched_by_name          NVARCHAR(255)   NULL,
        dispatched_at               DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        completed_at                DATETIME2       NULL,

        status                      VARCHAR(20)     NOT NULL DEFAULT 'QUEUED',
        -- QUEUED | DISPATCHING | COMPLETED | FAILED | PARTIAL

        response_payload            NVARCHAR(MAX)   NULL,

        -- Audit + soft-delete + rowversion.
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        row_version                 ROWVERSION,

        CONSTRAINT FK_ccc_template
            FOREIGN KEY (template_id) REFERENCES bcm.crisis_comms_template_v2(id)
    );

    -- "Show me all cascades for this crisis event" — the Crisis Room
    -- dispatch timeline.
    CREATE INDEX IX_ccc_org_event
        ON bcm.crisis_comms_cascade(organisation_id, crisis_event_id, dispatched_at DESC)
        WHERE is_deleted = 0;

    -- "Show me cascades by status" — operations dashboard.
    CREATE INDEX IX_ccc_org_status
        ON bcm.crisis_comms_cascade(organisation_id, status)
        WHERE is_deleted = 0;
END
GO

-- ─── Seed 20 templates (idempotent — guarded by template_code) ────
--
-- Pattern: ~3 templates per audience covering early-incident / mid-
-- incident / recovery, plus 4-template REGULATOR pack (SAMA / NCA /
-- SDAIA / CMA). All bilingual with Saudi-native AR phrasing. The
-- approval_status is APPROVED_BY_CISO for the seed so they show up
-- in the dispatch picker immediately.
--
-- Placeholders are described as JSON so the dispatch wizard can render
-- typed form inputs:
--   [{"key":"incident_code","label_en":"Incident code","label_ar":"رمز الحادث","type":"text","required":true}, ...]

DECLARE @org BIGINT = 1;
DECLARE @now DATETIME2 = SYSUTCDATETIME();

-- ─── BOARD ───────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-BRD-CRITICAL-NOTICE-001')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-BRD-CRITICAL-NOTICE-001',
    'Board — critical incident first notice',
    N'مجلس الإدارة — الإشعار الأولي بحادث حرج',
    'BOARD', 'GENERIC_MAJOR', 'CRITICAL',
    'URGENT — Critical incident under management — {{incident_code}}',
    N'عاجل — حادث حرج تحت الإدارة — {{incident_code}}',
    'Honourable Board Member,\n\nA Severity-{{severity_level}} incident ({{incident_code}}) is currently under crisis-team management. Initial assessment: {{summary}}. Impacted services: {{affected_services}}.\n\nThe Crisis Management Team has been activated at {{activation_time_ksa}} KSA and is operating under the approved BCP. Next executive update at {{next_update_ksa}} KSA. Full sitrep will be delivered at the scheduled emergency convening if Severity remains at this level for more than {{escalation_hours}} hours.\n\nFor immediate enquiries please contact the Crisis Manager — {{crisis_manager_name}} ({{contact_phone}}).\n\nRegards,\nGroup Chief Risk Officer',
    N'سعادة عضو مجلس الإدارة المحترم،\n\nيُحاط مجلسكم الموقّر بأنّ حادثاً بمستوى الخطورة {{severity_level}} ({{incident_code}}) يقع حالياً تحت إدارة فريق الأزمات. التقييم الأولي: {{summary}}. الخدمات المتأثرة: {{affected_services}}.\n\nتمّ تفعيل فريق إدارة الأزمات في تمام الساعة {{activation_time_ksa}} بتوقيت المملكة، وتسير الأمور وفق خطة استمرارية الأعمال المعتمدة. التحديث التنفيذي القادم في {{next_update_ksa}} بتوقيت المملكة، وسيُسلَّم تقرير الموقف الكامل في اجتماع المجلس الطارئ في حال استمرّ مستوى الخطورة لأكثر من {{escalation_hours}} ساعة.\n\nللاستفسارات العاجلة يُرجى التواصل مع قائد الأزمة الأستاذ/ {{crisis_manager_name}} ({{contact_phone}}).\n\nمع وافر التحية،\nرئيس إدارة المخاطر للمجموعة',
    'BOARD ALERT: Critical incident {{incident_code}} active. Crisis team engaged. Next update {{next_update_ksa}}.',
    N'تنبيه للمجلس: حادث حرج {{incident_code}} نشِط. فريق الأزمة منعقد. التحديث القادم {{next_update_ksa}}.',
    N'[{"key":"incident_code","label_en":"Incident code","label_ar":"رمز الحادث","type":"text","required":true},{"key":"severity_level","label_en":"Severity (1-5)","label_ar":"مستوى الخطورة (1-5)","type":"number","required":true},{"key":"summary","label_en":"Initial summary","label_ar":"الملخص الأولي","type":"textarea","required":true},{"key":"affected_services","label_en":"Affected services","label_ar":"الخدمات المتأثرة","type":"text","required":true},{"key":"activation_time_ksa","label_en":"Activation time (KSA)","label_ar":"وقت التفعيل (السعودية)","type":"datetime","required":true},{"key":"next_update_ksa","label_en":"Next update (KSA)","label_ar":"التحديث القادم (السعودية)","type":"datetime","required":true},{"key":"escalation_hours","label_en":"Escalation hours","label_ar":"ساعات التصعيد","type":"number","required":true},{"key":"crisis_manager_name","label_en":"Crisis Manager","label_ar":"قائد الأزمة","type":"text","required":true},{"key":"contact_phone","label_en":"Contact phone","label_ar":"هاتف التواصل","type":"text","required":true}]',
    'SAMA BCF Annex B §4 — Board first-notification window; ISO 22301 §8.4.3',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-BRD-SITREP-002')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-BRD-SITREP-002',
    'Board — mid-incident situation report',
    N'مجلس الإدارة — تقرير موقف خلال الحادث',
    'BOARD', 'GENERIC_MAJOR', 'HIGH',
    'Sitrep #{{sitrep_number}} — {{incident_code}}',
    N'تقرير موقف رقم {{sitrep_number}} — {{incident_code}}',
    'Honourable Board Member,\n\nThis is Situation Report #{{sitrep_number}} for incident {{incident_code}}.\n\nCurrent posture: {{current_posture}}. Actions taken in the last reporting period: {{actions_taken}}. Outstanding decisions awaiting board direction: {{pending_decisions}}.\n\nEstimated time to service restoration: {{eta_restored_at}} KSA. Regulator notifications: {{regulator_status}}. Material financial exposure to date: {{financial_exposure_sar}} SAR.\n\nNext sitrep at {{next_sitrep_time_ksa}} KSA.\n\nRegards,\nGroup Chief Risk Officer',
    N'سعادة عضو مجلس الإدارة المحترم،\n\nهذا تقرير الموقف رقم {{sitrep_number}} للحادث {{incident_code}}.\n\nالوضع الحالي: {{current_posture}}. الإجراءات المُتَّخذة خلال الفترة الأخيرة: {{actions_taken}}. القرارات المُعلَّقة بانتظار توجيه المجلس: {{pending_decisions}}.\n\nالوقت المُقدَّر لاستعادة الخدمة: {{eta_restored_at}} بتوقيت المملكة. إشعارات الجهات التنظيمية: {{regulator_status}}. الانكشاف المالي حتى الآن: {{financial_exposure_sar}} ريال سعودي.\n\nالتقرير القادم في {{next_sitrep_time_ksa}} بتوقيت المملكة.\n\nمع وافر التحية،\nرئيس إدارة المخاطر للمجموعة',
    NULL, NULL,
    N'[{"key":"sitrep_number","type":"number","required":true},{"key":"incident_code","type":"text","required":true},{"key":"current_posture","type":"textarea","required":true},{"key":"actions_taken","type":"textarea","required":true},{"key":"pending_decisions","type":"textarea","required":false},{"key":"eta_restored_at","type":"datetime","required":false},{"key":"regulator_status","type":"text","required":true},{"key":"financial_exposure_sar","type":"number","required":false},{"key":"next_sitrep_time_ksa","type":"datetime","required":true}]',
    'SAMA BCF Annex B §4.2 — Periodic Board situation reports',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-BRD-EMRG-CONVENING-003')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-BRD-EMRG-CONVENING-003',
    'Board — emergency convening notice',
    N'مجلس الإدارة — دعوة لاجتماع طارئ',
    'BOARD', 'GENERIC_MAJOR', 'CRITICAL',
    'EMERGENCY BOARD MEETING — {{incident_code}} — {{meeting_time_ksa}}',
    N'اجتماع طارئ لمجلس الإدارة — {{incident_code}} — {{meeting_time_ksa}}',
    'Honourable Board Member,\n\nThe Chairperson has called an emergency Board meeting in response to incident {{incident_code}}.\n\nDate / Time: {{meeting_time_ksa}} KSA\nLocation: {{meeting_location}}\nDial-in (encrypted): {{secure_link}}\nQuorum required: {{quorum_required}} of {{quorum_total}}\n\nAgenda: (1) Incident briefing by Crisis Manager; (2) Material exposure update from Group CFO; (3) Public-disclosure direction; (4) Authorisation of extraordinary expenditure as needed.\n\nBoard secretariat will distribute pre-read materials no later than two hours before the meeting. Please confirm attendance to {{secretariat_email}}.\n\nRegards,\nBoard Secretariat',
    N'سعادة عضو مجلس الإدارة المحترم،\n\nاستجابةً للحادث {{incident_code}}، دعا سعادة رئيس مجلس الإدارة لعقد اجتماع طارئ للمجلس.\n\nالتاريخ والوقت: {{meeting_time_ksa}} بتوقيت المملكة\nالموقع: {{meeting_location}}\nرابط الاتصال المُؤمَّن: {{secure_link}}\nالنصاب المطلوب: {{quorum_required}} من أصل {{quorum_total}}\n\nجدول الأعمال: (١) إحاطة من قائد الأزمة عن الحادث؛ (٢) تحديث الانكشاف المادي من الرئيس المالي للمجموعة؛ (٣) توجيهات الإفصاح العام؛ (٤) اعتماد المصروفات الاستثنائية عند الحاجة.\n\nستوزّع أمانة المجلس الوثائق التحضيرية قبل ساعتين على الأقل من موعد الاجتماع. يُرجى تأكيد الحضور إلى {{secretariat_email}}.\n\nمع وافر التحية،\nأمانة المجلس',
    'URGENT: Emergency Board meeting {{meeting_time_ksa}} KSA. Dial-in: {{secure_link}}. Confirm attendance.',
    N'عاجل: اجتماع طارئ للمجلس {{meeting_time_ksa}} بتوقيت المملكة. رابط الاتصال: {{secure_link}}. يُرجى تأكيد الحضور.',
    N'[{"key":"incident_code","type":"text","required":true},{"key":"meeting_time_ksa","type":"datetime","required":true},{"key":"meeting_location","type":"text","required":true},{"key":"secure_link","type":"text","required":false},{"key":"quorum_required","type":"number","required":true},{"key":"quorum_total","type":"number","required":true},{"key":"secretariat_email","type":"text","required":true}]',
    'CMA Corporate Governance Regulations Art.30 — emergency Board procedures',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

-- ─── REGULATOR (4 templates) ─────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-REG-SAMA-SCRO-004')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-REG-SAMA-SCRO-004',
    'SAMA SCRO — initial cyber/operational incident notification',
    N'البنك المركزي السعودي SCRO — إشعار أولي بحادث تشغيلي/سيبراني',
    'REGULATOR', 'REGULATORY_NOTIFICATION', 'CRITICAL',
    'Initial incident notification to SAMA SCRO — {{licensed_entity_name}} — {{incident_code}}',
    N'إشعار أولي بحادث إلى مركز SAMA SCRO — {{licensed_entity_name}} — {{incident_code}}',
    'To: SAMA Saudi Cyber Reporting and Operational notification unit\nFrom: {{licensed_entity_name}} (CIBS code {{cibs_code}})\nDate: {{notification_date_ksa}} KSA\nSubject: Initial notification of operational/cyber incident\n\nIn accordance with the SAMA Cyber Security Framework v1.1 Domain 3.3.6 (Incident Reporting) and the Operational Resilience Framework §5.3, we hereby notify SAMA of the following incident:\n\nIncident reference: {{incident_code}}\nDetected at: {{detection_time_ksa}} KSA\nIncident category: {{incident_category}}\nServices impacted: {{affected_services}}\nCustomer impact: {{customer_impact_summary}}\nInitial root-cause hypothesis: {{initial_hypothesis}}\nContainment status: {{containment_status}}\n\nFull SCRO incident report will follow within the regulatory window. The Crisis Manager — {{crisis_manager_name}} ({{contact_phone}}) — is the designated point of contact.\n\nRegards,\n{{ciso_name}}\nChief Information Security Officer\n{{licensed_entity_name}}',
    N'إلى: مركز البنك المركزي السعودي للإبلاغ السيبراني والتشغيلي (SCRO)\nمن: {{licensed_entity_name}} (رمز CIBS {{cibs_code}})\nالتاريخ: {{notification_date_ksa}} بتوقيت المملكة\nالموضوع: إشعار أولي بحادث تشغيلي/سيبراني\n\nبناءً على إطار الأمن السيبراني الصادر من البنك المركزي السعودي الإصدار 1.1 المجال 3.3.6 (الإبلاغ عن الحوادث)، وإطار المرونة التشغيلية §5.3، نتقدم بإشعاركم بالحادث التالي:\n\nرمز الحادث: {{incident_code}}\nوقت الاكتشاف: {{detection_time_ksa}} بتوقيت المملكة\nتصنيف الحادث: {{incident_category}}\nالخدمات المتأثرة: {{affected_services}}\nأثر العميل: {{customer_impact_summary}}\nالفرضية الأولية لجذر السبب: {{initial_hypothesis}}\nحالة الاحتواء: {{containment_status}}\n\nسيلي ذلك تقرير الحادث الكامل إلى SCRO خلال النافذة التنظيمية المُحددة. قائد الأزمة الأستاذ/ {{crisis_manager_name}} ({{contact_phone}}) هو نقطة الاتصال المُعيَّنة.\n\nمع وافر التحية،\n{{ciso_name}}\nرئيس أمن المعلومات\n{{licensed_entity_name}}',
    NULL, NULL,
    N'[{"key":"licensed_entity_name","type":"text","required":true},{"key":"cibs_code","type":"text","required":true},{"key":"notification_date_ksa","type":"datetime","required":true},{"key":"incident_code","type":"text","required":true},{"key":"detection_time_ksa","type":"datetime","required":true},{"key":"incident_category","type":"text","required":true},{"key":"affected_services","type":"text","required":true},{"key":"customer_impact_summary","type":"textarea","required":true},{"key":"initial_hypothesis","type":"textarea","required":true},{"key":"containment_status","type":"text","required":true},{"key":"crisis_manager_name","type":"text","required":true},{"key":"contact_phone","type":"text","required":true},{"key":"ciso_name","type":"text","required":true}]',
    'SAMA Cyber Security Framework v1.1 §3.3.6; SAMA Operational Resilience Framework §5.3',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-REG-NCA-ECC2-005')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-REG-NCA-ECC2-005',
    'NCA — ECC-2 cybersecurity incident notification',
    N'الهيئة الوطنية للأمن السيبراني — إشعار حادث وفق ECC-2',
    'REGULATOR', 'REGULATORY_NOTIFICATION', 'CRITICAL',
    'NCA ECC-2 incident notification — {{incident_code}}',
    N'إشعار حادث وفق ECC-2 إلى الهيئة الوطنية للأمن السيبراني — {{incident_code}}',
    'To: Saudi National Cybersecurity Authority (NCA) — Incident Reporting Desk\nFrom: {{entity_name}} (NCA registration {{nca_reg_no}})\nDate: {{notification_date_ksa}} KSA\nSubject: ECC-2 Article 2.7.4 cybersecurity incident notification\n\nThis is a cybersecurity incident notification under the Essential Cybersecurity Controls (ECC-2:2024) Article 2.7.4 — Cybersecurity Incident and Threat Management.\n\nIncident reference: {{incident_code}}\nClassification: {{nca_classification}}\nDetected at: {{detection_time_ksa}} KSA\nIncident vector: {{attack_vector}}\nIndicators of compromise: {{ioc_summary}}\nAffected critical national infrastructure assets: {{cni_impact}}\nCurrent containment level: {{containment_status}}\nEstimated recovery: {{eta_recovery_ksa}} KSA\n\nWe will provide a full root-cause and lessons-learned report within ECC-2 prescribed timelines. The technical liaison is {{tech_liaison_name}} ({{tech_liaison_email}}).\n\nRegards,\n{{ciso_name}}\nCISO, {{entity_name}}',
    N'إلى: الهيئة الوطنية للأمن السيبراني — مكتب الإبلاغ عن الحوادث\nمن: {{entity_name}} (رقم التسجيل {{nca_reg_no}})\nالتاريخ: {{notification_date_ksa}} بتوقيت المملكة\nالموضوع: إشعار حادث سيبراني وفق المادة 2.7.4 من ECC-2\n\nهذا إشعار بحادث سيبراني بناءً على ضوابط الأمن السيبراني الأساسية ECC-2:2024 المادة 2.7.4 — إدارة الحوادث والتهديدات السيبرانية.\n\nرمز الحادث: {{incident_code}}\nالتصنيف: {{nca_classification}}\nوقت الاكتشاف: {{detection_time_ksa}} بتوقيت المملكة\nمسار الهجوم: {{attack_vector}}\nمؤشرات الاختراق: {{ioc_summary}}\nأصول البنية التحتية الوطنية الحرجة المتأثرة: {{cni_impact}}\nمستوى الاحتواء الحالي: {{containment_status}}\nالاسترداد المتوقع: {{eta_recovery_ksa}} بتوقيت المملكة\n\nسنقدم تقرير جذر السبب والدروس المستفادة الكامل ضمن المهل المحددة في ECC-2. الاتصال الفني الأستاذ/ {{tech_liaison_name}} ({{tech_liaison_email}}).\n\nمع وافر التحية،\n{{ciso_name}}\nرئيس أمن المعلومات، {{entity_name}}',
    NULL, NULL,
    N'[{"key":"entity_name","type":"text","required":true},{"key":"nca_reg_no","type":"text","required":true},{"key":"notification_date_ksa","type":"datetime","required":true},{"key":"incident_code","type":"text","required":true},{"key":"nca_classification","type":"text","required":true},{"key":"detection_time_ksa","type":"datetime","required":true},{"key":"attack_vector","type":"text","required":true},{"key":"ioc_summary","type":"textarea","required":true},{"key":"cni_impact","type":"textarea","required":true},{"key":"containment_status","type":"text","required":true},{"key":"eta_recovery_ksa","type":"datetime","required":false},{"key":"tech_liaison_name","type":"text","required":true},{"key":"tech_liaison_email","type":"text","required":true},{"key":"ciso_name","type":"text","required":true}]',
    'NCA ECC-2:2024 Article 2.7.4 — Cybersecurity Incident and Threat Management',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-REG-SDAIA-PDPL31-006')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-REG-SDAIA-PDPL31-006',
    'SDAIA — PDPL Art.31 personal-data breach notification (72-hour)',
    N'سدايا — إشعار خرق بيانات شخصية وفق المادة 31 (خلال 72 ساعة)',
    'REGULATOR', 'DATA_BREACH', 'CRITICAL',
    'PDPL Art.31 personal-data breach notification — {{controller_name}} — {{incident_code}}',
    N'إشعار خرق بيانات شخصية وفق المادة 31 من نظام حماية البيانات الشخصية — {{controller_name}} — {{incident_code}}',
    'To: Saudi Data and Artificial Intelligence Authority (SDAIA) — Data Protection Office\nFrom: {{controller_name}} (controller registration {{controller_reg_no}})\nDate: {{notification_date_ksa}} KSA\nSubject: Notification of personal-data breach under PDPL Article 31\n\nThis is a personal-data breach notification submitted within 72 hours of detection in accordance with the Personal Data Protection Law (PDPL) and its Implementing Regulations Article 31.\n\nIncident reference: {{incident_code}}\nDetection time: {{detection_time_ksa}} KSA\nNature of breach: {{breach_nature}}\nCategories of data subjects affected: {{data_subject_categories}}\nApproximate count of affected data subjects: {{affected_count}}\nCategories of personal data involved: {{data_categories}} (including sensitive: {{sensitive_yn}})\nLikely consequences: {{likely_consequences}}\nContainment measures: {{containment_measures}}\nMitigation in progress: {{mitigation_actions}}\n\nData-subject notification under PDPL Article 32 {{ds_notification_status}}. The designated Data Protection Officer is {{dpo_name}} ({{dpo_email}}).\n\nRegards,\n{{dpo_name}}\nData Protection Officer\n{{controller_name}}',
    N'إلى: الهيئة السعودية للبيانات والذكاء الاصطناعي (سدايا) — مكتب حماية البيانات\nمن: {{controller_name}} (رقم تسجيل المتحكم {{controller_reg_no}})\nالتاريخ: {{notification_date_ksa}} بتوقيت المملكة\nالموضوع: إشعار خرق بيانات شخصية وفق المادة 31 من نظام حماية البيانات الشخصية\n\nهذا إشعار بخرق بيانات شخصية مُقدَّم خلال 72 ساعة من اكتشافه وفق نظام حماية البيانات الشخصية ولائحته التنفيذية، المادة 31.\n\nرمز الحادث: {{incident_code}}\nوقت الاكتشاف: {{detection_time_ksa}} بتوقيت المملكة\nطبيعة الخرق: {{breach_nature}}\nفئات أصحاب البيانات المتأثرين: {{data_subject_categories}}\nالعدد التقريبي لأصحاب البيانات المتأثرين: {{affected_count}}\nفئات البيانات الشخصية المعنية: {{data_categories}} (تشمل بيانات حساسة: {{sensitive_yn}})\nالعواقب المحتملة: {{likely_consequences}}\nإجراءات الاحتواء: {{containment_measures}}\nالإجراءات التخفيفية الجارية: {{mitigation_actions}}\n\nإشعار أصحاب البيانات وفق المادة 32: {{ds_notification_status}}. مسؤول حماية البيانات المُعيَّن هو الأستاذ/ {{dpo_name}} ({{dpo_email}}).\n\nمع وافر التحية،\n{{dpo_name}}\nمسؤول حماية البيانات\n{{controller_name}}',
    NULL, NULL,
    N'[{"key":"controller_name","type":"text","required":true},{"key":"controller_reg_no","type":"text","required":true},{"key":"notification_date_ksa","type":"datetime","required":true},{"key":"incident_code","type":"text","required":true},{"key":"detection_time_ksa","type":"datetime","required":true},{"key":"breach_nature","type":"textarea","required":true},{"key":"data_subject_categories","type":"text","required":true},{"key":"affected_count","type":"number","required":true},{"key":"data_categories","type":"text","required":true},{"key":"sensitive_yn","type":"text","required":true},{"key":"likely_consequences","type":"textarea","required":true},{"key":"containment_measures","type":"textarea","required":true},{"key":"mitigation_actions","type":"textarea","required":true},{"key":"ds_notification_status","type":"text","required":true},{"key":"dpo_name","type":"text","required":true},{"key":"dpo_email","type":"text","required":true}]',
    'PDPL Art.31 + Implementing Regulations Art.31 — 72-hour breach notification to SDAIA',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-REG-CMA-INSIDER-007')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-REG-CMA-INSIDER-007',
    'CMA — insider-information disclosure (material event)',
    N'هيئة السوق المالية — إفصاح عن معلومة جوهرية',
    'REGULATOR', 'REGULATORY_NOTIFICATION', 'HIGH',
    'CMA insider-information disclosure — {{listed_entity}} — {{incident_code}}',
    N'إفصاح عن معلومة جوهرية إلى هيئة السوق المالية — {{listed_entity}} — {{incident_code}}',
    'To: Capital Market Authority (CMA) — Listed Companies Department\nFrom: {{listed_entity}} (Tadawul symbol {{ticker_symbol}})\nDate: {{disclosure_date_ksa}} KSA\nSubject: Disclosure of material event under CMA Listing Rules Art.83\n\nIn accordance with the CMA Listing Rules Article 83 (Disclosure of Material Information), {{listed_entity}} hereby discloses the following material event:\n\nNature of event: {{event_nature}}\nDate of event: {{event_date_ksa}} KSA\nFinancial implications: {{financial_implications}}\nOperational implications: {{operational_implications}}\nManagement response: {{management_response}}\nExpected impact on operations: {{expected_impact}}\n\nThe disclosure is being made via the Tadawul disclosure portal simultaneously with this notification. Investor enquiries to {{ir_contact_name}} ({{ir_email}}).\n\nRegards,\n{{ceo_name}}\nChief Executive Officer\n{{listed_entity}}',
    N'إلى: هيئة السوق المالية — إدارة الشركات المُدرَجة\nمن: {{listed_entity}} (الرمز في تداول {{ticker_symbol}})\nالتاريخ: {{disclosure_date_ksa}} بتوقيت المملكة\nالموضوع: إفصاح عن حدث جوهري وفق المادة 83 من قواعد الإدراج\n\nاستناداً إلى المادة 83 من قواعد الإدراج الصادرة عن هيئة السوق المالية (الإفصاح عن المعلومات الجوهرية)، تفصح شركة {{listed_entity}} عن الحدث الجوهري التالي:\n\nطبيعة الحدث: {{event_nature}}\nتاريخ الحدث: {{event_date_ksa}} بتوقيت المملكة\nالآثار المالية: {{financial_implications}}\nالآثار التشغيلية: {{operational_implications}}\nاستجابة الإدارة: {{management_response}}\nالأثر المتوقع على الأعمال: {{expected_impact}}\n\nيُنشر الإفصاح على نظام تداول للإفصاحات بالتزامن مع هذا الإشعار. استفسارات المستثمرين إلى الأستاذ/ {{ir_contact_name}} ({{ir_email}}).\n\nمع وافر التحية،\n{{ceo_name}}\nالرئيس التنفيذي\n{{listed_entity}}',
    NULL, NULL,
    N'[{"key":"listed_entity","type":"text","required":true},{"key":"ticker_symbol","type":"text","required":true},{"key":"disclosure_date_ksa","type":"datetime","required":true},{"key":"incident_code","type":"text","required":true},{"key":"event_nature","type":"text","required":true},{"key":"event_date_ksa","type":"datetime","required":true},{"key":"financial_implications","type":"textarea","required":true},{"key":"operational_implications","type":"textarea","required":true},{"key":"management_response","type":"textarea","required":true},{"key":"expected_impact","type":"textarea","required":true},{"key":"ir_contact_name","type":"text","required":true},{"key":"ir_email","type":"text","required":true},{"key":"ceo_name","type":"text","required":true}]',
    'CMA Listing Rules Art.83 — Disclosure of material information',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

-- ─── CUSTOMERS (3 templates) ─────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-CUS-OUTAGE-008')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-CUS-OUTAGE-008',
    'Customers — service interruption advisory',
    N'العملاء — إعلان عن انقطاع في الخدمة',
    'CUSTOMERS', 'OUTAGE', 'HIGH',
    'Service update — {{service_name}}',
    N'تحديث بخصوص خدمة {{service_name}}',
    'Dear valued customer,\n\nWe are currently experiencing a service interruption affecting {{service_name}}. Our teams are working to restore the service as soon as possible.\n\nExpected restoration: {{eta_restored_at}} KSA\nAlternative channel meanwhile: {{alternative_channel}}\nFor urgent assistance: {{contact_phone}}\n\nWe sincerely apologise for the inconvenience and appreciate your patience. We will update you the moment the service is fully restored.\n\nThank you for being our customer,\n{{entity_name}} Customer Care',
    N'عزيزي العميل،\n\nنود إعلامكم بوجود انقطاع حالي في خدمة {{service_name}}. تعمل فِرَقنا التقنية على استعادة الخدمة في أقرب وقت ممكن.\n\nالوقت المتوقع للاستعادة: {{eta_restored_at}} بتوقيت المملكة\nالقناة البديلة في الوقت الراهن: {{alternative_channel}}\nللمساعدة العاجلة: {{ar_contact_phone}}\n\nنعتذر بصدق عن الإزعاج، ونشكر لكم تفهّمكم وصبركم. سنوافيكم بتحديث فور عودة الخدمة بشكل طبيعي.\n\nشكراً لثقتكم،\nخدمة عملاء {{entity_name}}',
    '{{entity_name}}: {{service_name}} is temporarily unavailable. ETA {{eta_restored_at}} KSA. Alt: {{alternative_channel}}. Sorry for the inconvenience.',
    N'{{entity_name}}: خدمة {{service_name}} غير متاحة مؤقتاً. الوقت المتوقع {{eta_restored_at}} بتوقيت المملكة. البديل: {{alternative_channel}}. نعتذر عن الإزعاج.',
    N'[{"key":"service_name","type":"text","required":true},{"key":"eta_restored_at","type":"datetime","required":true},{"key":"alternative_channel","type":"text","required":true},{"key":"contact_phone","type":"text","required":true},{"key":"ar_contact_phone","type":"text","required":true},{"key":"entity_name","type":"text","required":true}]',
    'SAMA Consumer Protection Principles §5 — customer notification of service disruption',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-CUS-PDPL32-BREACH-009')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-CUS-PDPL32-BREACH-009',
    'Affected customer — PDPL Art.32 personal-data breach notification',
    N'العميل المتأثر — إشعار خرق بيانات شخصية وفق المادة 32',
    'CUSTOMERS', 'DATA_BREACH', 'CRITICAL',
    'Important — notification regarding your personal data — {{entity_name}}',
    N'مهم — إشعار يخصّ بياناتك الشخصية — {{entity_name}}',
    'Dear customer,\n\nWe write to inform you, in accordance with Article 32 of the Personal Data Protection Law (PDPL) and its Implementing Regulations, that a security incident occurred on {{incident_date_ksa}} KSA which has affected some of your personal data held by {{entity_name}}.\n\nWhat happened: {{breach_summary_short}}\nWhich data of yours may have been affected: {{your_data_affected}}\nWhich data was NOT affected: {{data_not_affected}}\n\nWhat we have done:\n - We contained the incident within {{containment_hours}} hours of detection.\n - We notified the Saudi Data and Artificial Intelligence Authority (SDAIA) within the regulatory 72-hour window.\n - We engaged independent forensic experts and we are working closely with the relevant authorities.\n\nWhat you can do:\n - Stay alert to suspicious messages and do not share verification codes (OTP / رمز التحقق) with anyone.\n - Change the password for your {{entity_name}} account at the earliest opportunity.\n - Contact our dedicated support line {{dpo_phone}} if you notice any suspicious activity.\n\nWe sincerely apologise. Protecting your data is a responsibility we take with utmost seriousness, and we are taking every measure to ensure this does not happen again.\n\n{{dpo_name}}\nData Protection Officer, {{entity_name}}',
    N'عزيزي العميل،\n\nنُبلغكم — وفق المادة 32 من نظام حماية البيانات الشخصية ولائحته التنفيذية — بوقوع حادثٍ أمني بتاريخ {{incident_date_ksa}} بتوقيت المملكة، أثَّر على بعض بياناتكم الشخصية المحفوظة لدى {{entity_name}}.\n\nما الذي حدث: {{breach_summary_short_ar}}\nأيُّ بياناتكم قد تكون تأثَّرت: {{your_data_affected_ar}}\nأيُّ بياناتٍ لم تتأثَّر: {{data_not_affected_ar}}\n\nالإجراءات التي اتَّخذناها:\n - تمّ احتواء الحادث خلال {{containment_hours}} ساعة من اكتشافه.\n - تمّ إشعار الهيئة السعودية للبيانات والذكاء الاصطناعي (سدايا) خلال نافذة الـ 72 ساعة النظامية.\n - تعاقدنا مع خبراء تحليل جنائي رقمي مستقلّين ونعمل بشكل وثيق مع الجهات المختصة.\n\nما الذي يمكنكم فعله:\n - كونوا حذرين من أيّ رسائل مشبوهة، ولا تشاركوا رمز التحقق (OTP) مع أيّ شخص.\n - يُرجى تغيير كلمة المرور لحسابكم في {{entity_name}} في أقرب فرصة.\n - تواصلوا مع خط الدعم المخصَّص {{dpo_phone}} في حال ملاحظة أيّ نشاط مشبوه.\n\nنعتذر بصدق. حماية بياناتكم مسؤولية نأخذها بأقصى درجات الجدية، ونتَّخذ كافة التدابير لضمان عدم تكرار ما حدث.\n\n{{dpo_name}}\nمسؤول حماية البيانات، {{entity_name}}',
    NULL, NULL,
    N'[{"key":"entity_name","type":"text","required":true},{"key":"incident_date_ksa","type":"datetime","required":true},{"key":"breach_summary_short","type":"textarea","required":true},{"key":"breach_summary_short_ar","type":"textarea","required":true},{"key":"your_data_affected","type":"text","required":true},{"key":"your_data_affected_ar","type":"text","required":true},{"key":"data_not_affected","type":"text","required":true},{"key":"data_not_affected_ar","type":"text","required":true},{"key":"containment_hours","type":"number","required":true},{"key":"dpo_phone","type":"text","required":true},{"key":"dpo_name","type":"text","required":true}]',
    'PDPL Art.32 + Implementing Regulations Art.32 — notification to affected data subjects',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-CUS-RECOVERY-010')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-CUS-RECOVERY-010',
    'Customers — service restoration announcement',
    N'العملاء — إعلان عودة الخدمة',
    'CUSTOMERS', 'RECOVERY', 'MEDIUM',
    'Service restored — {{service_name}}',
    N'عادت الخدمة إلى العمل بشكل طبيعي — {{service_name}}',
    'Dear valued customer,\n\nWe are pleased to inform you that {{service_name}} is now fully restored and operating normally as of {{restored_at_ksa}} KSA.\n\nIf you encounter any residual issues, please reach our 24/7 support team on {{contact_phone}} or visit {{support_portal}}.\n\nWe deeply appreciate your patience throughout the interruption. Your trust in {{entity_name}} means a great deal to us.\n\nThank you for being our customer,\n{{entity_name}} Customer Care',
    N'عزيزي العميل،\n\nيسعدنا إبلاغكم بأنّ خدمة {{service_name}} عادت إلى العمل بشكل طبيعي اعتباراً من {{restored_at_ksa}} بتوقيت المملكة.\n\nفي حال واجهتكم أيّ مشاكل متبقية، تواصلوا مع فريق الدعم على مدار الساعة عبر {{ar_contact_phone}} أو من خلال {{support_portal}}.\n\nنُقدِّر صبركم خلال فترة الانقطاع، وثقتكم في {{entity_name}} تعني لنا الكثير.\n\nشكراً لثقتكم،\nخدمة عملاء {{entity_name}}',
    '{{entity_name}}: {{service_name}} restored as of {{restored_at_ksa}} KSA. Thank you for your patience.',
    N'{{entity_name}}: عادت خدمة {{service_name}} إلى العمل بشكل طبيعي اعتباراً من {{restored_at_ksa}} بتوقيت المملكة. شكراً لصبركم.',
    N'[{"key":"service_name","type":"text","required":true},{"key":"restored_at_ksa","type":"datetime","required":true},{"key":"contact_phone","type":"text","required":true},{"key":"ar_contact_phone","type":"text","required":true},{"key":"support_portal","type":"text","required":true},{"key":"entity_name","type":"text","required":true}]',
    'ISO 22301 §8.4.5 — customer recovery communication',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

-- ─── STAFF (3 templates) ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-STF-STANDDOWN-011')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-STF-STANDDOWN-011',
    'Staff — all-staff incident stand-down',
    N'الموظفون — إعلان عام بحالة احتواء الحادث',
    'STAFF', 'GENERIC_MAJOR', 'HIGH',
    'All-staff — incident {{incident_code}} — action required',
    N'إلى جميع الزملاء — حادث {{incident_code}} — إجراءات مطلوبة',
    'Dear colleagues,\n\nWe are managing an active operational incident ({{incident_code}}). To allow our crisis team and recovery responders to focus, the following measures take effect immediately:\n\n - Do NOT speak with media, customers via personal channels, or external parties about this matter. All external comms come from {{comms_lead_name}}.\n - Do NOT share screenshots, logs or internal details outside the company — including on social media.\n - If you are NOT part of the Crisis Management Team, please continue with your regular work unless your line manager tells you otherwise.\n - If you ARE part of the Crisis Team, please report to {{crisis_room_location}} or join the Teams bridge {{teams_bridge_link}}.\n\nQuestions? Contact your line manager first; HR are also available on {{hr_phone}} for personal concerns.\n\nThank you for your professionalism.\n{{ceo_name}}\nCEO',
    N'إخوتي وأخواتي الزملاء،\n\nنحن نتعامل حالياً مع حادث تشغيلي نشِط ({{incident_code}}). لتمكين فريق إدارة الأزمة وفِرَق الاستعادة من العمل بتركيز، تُطبَّق الإجراءات التالية فوراً:\n\n - يُمنع منعاً باتاً التحدّث إلى وسائل الإعلام، أو إلى العملاء عبر القنوات الشخصية، أو إلى أيّ طرف خارجي حول هذا الموضوع. جميع الاتصالات الخارجية تصدر عن الأستاذ/ {{comms_lead_name}}.\n - يُمنع مشاركة أيّ لقطات شاشة أو سجلات أو تفاصيل داخلية خارج الشركة — بما في ذلك على منصات التواصل الاجتماعي.\n - إن لم تكونوا ضمن فريق إدارة الأزمة، يُرجى مواصلة عملكم المعتاد ما لم يطلب منكم رؤساؤكم خلاف ذلك.\n - أمّا أعضاء فريق الأزمة، فيُرجى الحضور إلى {{crisis_room_location}} أو الانضمام إلى اجتماع Teams عبر الرابط {{teams_bridge_link}}.\n\nأيّ استفسار يُرجى توجيهه لرؤسائكم المباشرين أولاً؛ وإدارة الموارد البشرية متاحة على {{hr_phone}} للمسائل الشخصية.\n\nشكراً لاحترافيّتكم.\n{{ceo_name}}\nالرئيس التنفيذي',
    'All-staff: incident {{incident_code}} active. No external comms. Crisis team report to {{crisis_room_location}}.',
    N'إلى جميع الزملاء: حادث {{incident_code}} نشِط. ممنوع التواصل الخارجي. فريق الأزمة إلى {{crisis_room_location}}.',
    N'[{"key":"incident_code","type":"text","required":true},{"key":"comms_lead_name","type":"text","required":true},{"key":"crisis_room_location","type":"text","required":true},{"key":"teams_bridge_link","type":"text","required":true},{"key":"hr_phone","type":"text","required":true},{"key":"ceo_name","type":"text","required":true}]',
    'ISO 22301 §8.4.3 — internal staff communication during a disruption',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-STF-DEPT-RECOVERY-012')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-STF-DEPT-RECOVERY-012',
    'Staff — department-specific recovery instructions',
    N'الموظفون — تعليمات الاستعادة الخاصة بالإدارة',
    'STAFF', 'RECOVERY', 'MEDIUM',
    'Recovery instructions — {{department_name}}',
    N'تعليمات الاستعادة — {{department_name}}',
    'Dear {{department_name}} team,\n\nThe Crisis Management Team has cleared {{service_name}} for limited recovery as of {{recovery_window_ksa}} KSA. Please follow the steps below carefully — these supersede your usual BAU procedure for the next {{recovery_duration_hours}} hours.\n\nRecovery procedure:\n1. {{step_1}}\n2. {{step_2}}\n3. {{step_3}}\n\nKnown limitations during recovery: {{known_limitations}}\nReport any anomalies to {{escalation_contact}} immediately.\nDo NOT bypass the recovery procedure; the temporary workaround in your runbook does not apply during this window.\n\nThank you,\n{{department_head_name}}\n{{department_name}} Lead',
    N'إلى زميلاتي وزملائي في إدارة {{department_name}}،\n\nاعتمد فريق إدارة الأزمة الاستعادة المحدودة لخدمة {{service_name}} اعتباراً من {{recovery_window_ksa}} بتوقيت المملكة. يُرجى اتّباع الخطوات أدناه بدقّة — هذه التعليمات تحلّ محلّ الإجراء المعتاد لمدة {{recovery_duration_hours}} ساعة قادمة.\n\nإجراء الاستعادة:\n1. {{step_1_ar}}\n2. {{step_2_ar}}\n3. {{step_3_ar}}\n\nالقيود المعلومة خلال الاستعادة: {{known_limitations_ar}}\nالإبلاغ عن أيّ خلل إلى {{escalation_contact}} فوراً.\nيُمنع تجاوز إجراء الاستعادة؛ الحل المؤقت في كتيّب الإجراءات لا ينطبق خلال هذه النافذة.\n\nشاكراً لكم،\n{{department_head_name}}\nمدير إدارة {{department_name}}',
    NULL, NULL,
    N'[{"key":"department_name","type":"text","required":true},{"key":"service_name","type":"text","required":true},{"key":"recovery_window_ksa","type":"datetime","required":true},{"key":"recovery_duration_hours","type":"number","required":true},{"key":"step_1","type":"text","required":true},{"key":"step_1_ar","type":"text","required":true},{"key":"step_2","type":"text","required":true},{"key":"step_2_ar","type":"text","required":true},{"key":"step_3","type":"text","required":false},{"key":"step_3_ar","type":"text","required":false},{"key":"known_limitations","type":"text","required":false},{"key":"known_limitations_ar","type":"text","required":false},{"key":"escalation_contact","type":"text","required":true},{"key":"department_head_name","type":"text","required":true}]',
    'ISO 22301 §8.4.5 — staged service restoration runbooks',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-STF-DEBRIEF-013')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-STF-DEBRIEF-013',
    'Staff — post-incident debrief invitation',
    N'الموظفون — دعوة لجلسة دروس مستفادة بعد الحادث',
    'STAFF', 'LESSONS_LEARNED', 'INFORMATIONAL',
    'You are invited — incident debrief — {{incident_code}}',
    N'دعوة لجلسة دروس مستفادة بعد الحادث — {{incident_code}}',
    'Dear colleagues,\n\nThank you for your professionalism during the recent incident. To capture lessons learned and improve our response next time, you are invited to a post-incident debrief:\n\nDate / time: {{debrief_time_ksa}} KSA\nLocation / link: {{debrief_location}}\nFacilitator: {{facilitator_name}}\nDuration: approximately {{duration_minutes}} minutes\n\nThe session is "no-blame": we focus on the system, not the people. We invite you to come prepared with at least one thing that worked well, one thing that did not, and one specific suggestion for improvement.\n\nWe will publish the lessons-learned report and the resulting CAPA tasks within two weeks of the debrief.\n\nThank you for your contribution,\n{{bcm_lead_name}}\nBCM Lead',
    N'إخوتي وأخواتي الزملاء،\n\nشكراً لاحترافيّتكم خلال الحادث الأخير. لاستخلاص الدروس المستفادة وتحسين استجابتنا في المستقبل، نتشرف بدعوتكم لحضور جلسة دروس مستفادة بعد الحادث:\n\nالتاريخ والوقت: {{debrief_time_ksa}} بتوقيت المملكة\nالموقع / الرابط: {{debrief_location}}\nالميسِّر: الأستاذ/ {{facilitator_name}}\nالمدة: حوالي {{duration_minutes}} دقيقة\n\nالجلسة "بلا لوم": نُركّز على تحسين النظام لا على الأشخاص. نتطلَّع منكم الحضور مع: ما الذي سار جيداً، ما الذي لم يسر كذلك، واقتراح واحد محدَّد للتحسين.\n\nسننشر تقرير الدروس المستفادة وقائمة الإجراءات التصحيحية والوقائية الناتجة خلال أسبوعين من الجلسة.\n\nشاكرين مساهمتكم،\n{{bcm_lead_name}}\nرئيس استمرارية الأعمال',
    NULL, NULL,
    N'[{"key":"incident_code","type":"text","required":true},{"key":"debrief_time_ksa","type":"datetime","required":true},{"key":"debrief_location","type":"text","required":true},{"key":"facilitator_name","type":"text","required":true},{"key":"duration_minutes","type":"number","required":true},{"key":"bcm_lead_name","type":"text","required":true}]',
    'ISO 22301 §9.1.2 — post-incident review',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

-- ─── PARTNERS (2 templates) ──────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-PRT-SLA-EXC-014')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-PRT-SLA-EXC-014',
    'Partners — vendor SLA exception notice',
    N'الشركاء — إشعار استثناء من اتفاقية مستوى الخدمة',
    'PARTNERS', 'OUTAGE', 'HIGH',
    'SLA exception notice — {{partner_name}} — {{incident_code}}',
    N'إشعار استثناء من اتفاقية مستوى الخدمة — {{partner_name}} — {{incident_code}}',
    'Dear {{partner_contact_name}},\n\nWe are writing to inform you that, due to operational incident {{incident_code}} which commenced at {{incident_start_ksa}} KSA, the service levels in our agreement (reference {{contract_reference}}) cannot be met during the period {{exception_window_start_ksa}} to {{exception_window_end_ksa}} KSA.\n\nAffected SLA clauses: {{affected_sla_clauses}}\nExpected restoration: {{eta_restored_at}} KSA\nWorkaround/alternative: {{partner_workaround}}\n\nWe invoke the force-majeure / business-continuity provisions of our agreement. Detailed loss-event documentation will be provided within the contractual reporting window so that any service-credit calculation can be finalised.\n\nFor immediate coordination, please contact our partner-management lead {{pm_lead_name}} ({{pm_lead_email}} / {{pm_lead_phone}}).\n\nKind regards,\n{{vp_partnerships_name}}\nVice President — Partnerships\n{{entity_name}}',
    N'سعادة الأستاذ/ {{partner_contact_name}} المحترم،\n\nنفيدكم بأنّه — نتيجةً للحادث التشغيلي {{incident_code}} الذي بدأ في تمام الساعة {{incident_start_ksa}} بتوقيت المملكة — لن نتمكَّن من الالتزام بمستويات الخدمة المنصوص عليها في اتفاقيّتنا (مرجع العقد {{contract_reference}}) خلال الفترة من {{exception_window_start_ksa}} إلى {{exception_window_end_ksa}} بتوقيت المملكة.\n\nبنود اتفاقية مستوى الخدمة المتأثرة: {{affected_sla_clauses}}\nالاستعادة المتوقعة: {{eta_restored_at}} بتوقيت المملكة\nالحل البديل: {{partner_workaround}}\n\nنستند إلى أحكام القوة القاهرة واستمرارية الأعمال الواردة في اتفاقيّتنا. سنُقدِّم وثائق حدث الخسارة التفصيلية خلال نافذة الإبلاغ التعاقدية بحيث يمكن استكمال أيّ احتساب لرصيد الخدمة.\n\nللتنسيق الفوري، يُرجى التواصل مع مدير العلاقة من جانبنا الأستاذ/ {{pm_lead_name}} ({{pm_lead_email}} / {{pm_lead_phone}}).\n\nمع وافر التحية،\n{{vp_partnerships_name}}\nنائب الرئيس للشراكات\n{{entity_name}}',
    NULL, NULL,
    N'[{"key":"partner_name","type":"text","required":true},{"key":"partner_contact_name","type":"text","required":true},{"key":"incident_code","type":"text","required":true},{"key":"incident_start_ksa","type":"datetime","required":true},{"key":"contract_reference","type":"text","required":true},{"key":"exception_window_start_ksa","type":"datetime","required":true},{"key":"exception_window_end_ksa","type":"datetime","required":true},{"key":"affected_sla_clauses","type":"text","required":true},{"key":"eta_restored_at","type":"datetime","required":true},{"key":"partner_workaround","type":"text","required":false},{"key":"pm_lead_name","type":"text","required":true},{"key":"pm_lead_email","type":"text","required":true},{"key":"pm_lead_phone","type":"text","required":true},{"key":"vp_partnerships_name","type":"text","required":true},{"key":"entity_name","type":"text","required":true}]',
    'NCA ECC-2 §2.10 — Third-party cybersecurity / supply-chain communications',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-PRT-MUTUAL-AID-015')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-PRT-MUTUAL-AID-015',
    'Partners — supply-chain mutual aid request',
    N'الشركاء — طلب مساندة من سلسلة الإمداد',
    'PARTNERS', 'GENERIC_MAJOR', 'HIGH',
    'Mutual-aid request — {{partner_name}} — {{incident_code}}',
    N'طلب مساندة من سلسلة الإمداد — {{partner_name}} — {{incident_code}}',
    'Dear {{partner_contact_name}},\n\nIn line with the mutual-aid clauses of our master services agreement (ref {{contract_reference}}), we are formally requesting your assistance to mitigate the impact of incident {{incident_code}}.\n\nSpecific assistance requested: {{assistance_requested}}\nDuration: {{assistance_window_hours}} hours starting {{assistance_start_ksa}} KSA\nCommercial framework: per master agreement {{commercial_terms_clause}}\nDesignated joint coordinator: {{joint_coordinator_name}} ({{joint_coordinator_email}})\n\nA pre-authorised purchase order will follow within the same business hour to enable your team to mobilise. We sincerely value the long-standing partnership between {{entity_name}} and {{partner_name}}.\n\nKindly confirm capability and ETA to {{joint_coordinator_email}} at your earliest.\n\nKind regards,\n{{coo_name}}\nChief Operating Officer\n{{entity_name}}',
    N'سعادة الأستاذ/ {{partner_contact_name}} المحترم،\n\nاستناداً إلى بنود المساندة المتبادلة في اتفاقية الخدمات الرئيسية بيننا (مرجع العقد {{contract_reference}})، نتقدَّم إليكم بطلب رسمي للمساعدة في تخفيف أثر الحادث {{incident_code}}.\n\nالمساعدة المطلوبة تحديداً: {{assistance_requested}}\nالمدة: {{assistance_window_hours}} ساعة، تبدأ من {{assistance_start_ksa}} بتوقيت المملكة\nالإطار التجاري: وفق الاتفاقية الرئيسية، البند {{commercial_terms_clause}}\nالمنسِّق المُعيَّن من الطرفين: الأستاذ/ {{joint_coordinator_name}} ({{joint_coordinator_email}})\n\nسيُصدر أمر شراء معتمَد مُسبقاً خلال الساعة لتمكين فريقكم من التحرّك. نُقدِّر عالياً الشراكة الممتدة بين {{entity_name}} و {{partner_name}}.\n\nيُرجى تأكيد القدرة والوقت المتوقَّع للوصول إلى {{joint_coordinator_email}} في أقرب وقت ممكن.\n\nمع وافر التحية،\n{{coo_name}}\nالرئيس التنفيذي للعمليات\n{{entity_name}}',
    NULL, NULL,
    N'[{"key":"partner_name","type":"text","required":true},{"key":"partner_contact_name","type":"text","required":true},{"key":"incident_code","type":"text","required":true},{"key":"contract_reference","type":"text","required":true},{"key":"assistance_requested","type":"textarea","required":true},{"key":"assistance_window_hours","type":"number","required":true},{"key":"assistance_start_ksa","type":"datetime","required":true},{"key":"commercial_terms_clause","type":"text","required":true},{"key":"joint_coordinator_name","type":"text","required":true},{"key":"joint_coordinator_email","type":"text","required":true},{"key":"coo_name","type":"text","required":true},{"key":"entity_name","type":"text","required":true}]',
    'SAMA Operational Resilience Framework §5.4 — third-party mutual aid',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

-- ─── MEDIA (3 templates) ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-MED-HOLDING-016')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-MED-HOLDING-016',
    'Media — holding statement (no operational details)',
    N'الإعلام — بيان تمهيدي (بدون تفاصيل تشغيلية)',
    'MEDIA', 'MEDIA_RESPONSE', 'HIGH',
    'Statement from {{entity_name}}',
    N'بيان صادر من {{entity_name}}',
    '{{entity_name}} is aware of reports concerning an operational matter affecting some of our services as of {{disruption_time_ksa}} KSA on {{disruption_date_ksa}}.\n\nOur Crisis Management Team has been engaged and is working closely with the relevant authorities. The integrity and protection of our customers data remains our highest priority.\n\nWe will share verified information through our official channels as soon as it becomes available. For media enquiries: {{press_office_email}} or {{press_office_phone}}.\n\nWe appreciate the patience of our customers and our partners.',
    N'تتابع {{entity_name}} ما تمَّ تداوله بخصوص حادث تشغيلي أثَّر على بعض خدماتنا اعتباراً من {{disruption_time_ksa}} بتوقيت المملكة بتاريخ {{disruption_date_ksa}}.\n\nفريق إدارة الأزمة منعقد ويعمل بشكل وثيق مع الجهات المختصة. تبقى سلامة بيانات عملائنا وحمايتها على رأس أولوياتنا.\n\nسنُشارك المعلومات الموثَّقة عبر قنواتنا الرسمية فور توفّرها. للاستفسارات الإعلامية: {{press_office_email}} أو {{press_office_phone}}.\n\nنُقدِّر صبر عملائنا وشركائنا.',
    NULL, NULL,
    N'[{"key":"entity_name","type":"text","required":true},{"key":"disruption_time_ksa","type":"text","required":true},{"key":"disruption_date_ksa","type":"text","required":true},{"key":"press_office_email","type":"text","required":true},{"key":"press_office_phone","type":"text","required":true}]',
    'CMA Listing Rules Art.83 + GCMA media-protocol guidance — holding statement',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-MED-PRESS-CONFIRMED-017')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-MED-PRESS-CONFIRMED-017',
    'Media — confirmed incident press statement',
    N'الإعلام — بيان رسمي بعد التحقق من الحادث',
    'MEDIA', 'MEDIA_RESPONSE', 'HIGH',
    'Press statement — {{entity_name}} — {{incident_code}}',
    N'بيان رسمي — {{entity_name}} — {{incident_code}}',
    '{{entity_name}} confirms that, on {{incident_date_ksa}} KSA, a {{incident_type_short}} incident took place affecting {{affected_services_short}}.\n\nWhat we know:\n - {{fact_1}}\n - {{fact_2}}\n - {{fact_3}}\n\nWhat we have done:\n - Engaged our Crisis Management Team within minutes of detection.\n - Notified the relevant regulatory authorities in line with our obligations.\n - Engaged independent forensic and recovery experts.\n\nWhat we are doing now:\n - {{next_step_1}}\n - {{next_step_2}}\n\nFor customers: any action you need to take is described on {{customer_advisory_url}} and in our direct notification to affected accounts.\nFor media: spokesperson available — {{spokesperson_name}}, {{spokesperson_title}}, via {{press_office_email}} / {{press_office_phone}}.\n\nWe will issue our next update at {{next_update_ksa}} KSA.',
    N'تؤكد {{entity_name}} وقوع حادث {{incident_type_short_ar}} بتاريخ {{incident_date_ksa}} بتوقيت المملكة، أثَّر على {{affected_services_short_ar}}.\n\nما نعرفه:\n - {{fact_1_ar}}\n - {{fact_2_ar}}\n - {{fact_3_ar}}\n\nالإجراءات المُتَّخذة:\n - تفعيل فريق إدارة الأزمة خلال دقائق من الاكتشاف.\n - إشعار الجهات التنظيمية المختصة وفق التزاماتنا.\n - الاستعانة بخبراء مستقلّين في التحليل الجنائي والاستعادة.\n\nالإجراءات الجارية حالياً:\n - {{next_step_1_ar}}\n - {{next_step_2_ar}}\n\nللعملاء: الإجراءات المطلوبة منكم موضَّحة في {{customer_advisory_url}} وفي إشعارنا المباشر للحسابات المتأثرة.\nللإعلاميين: المتحدث الرسمي الأستاذ/ {{spokesperson_name}}، {{spokesperson_title}}، عبر {{press_office_email}} / {{press_office_phone}}.\n\nسنُصدر التحديث القادم في {{next_update_ksa}} بتوقيت المملكة.',
    NULL, NULL,
    N'[{"key":"entity_name","type":"text","required":true},{"key":"incident_code","type":"text","required":true},{"key":"incident_date_ksa","type":"datetime","required":true},{"key":"incident_type_short","type":"text","required":true},{"key":"incident_type_short_ar","type":"text","required":true},{"key":"affected_services_short","type":"text","required":true},{"key":"affected_services_short_ar","type":"text","required":true},{"key":"fact_1","type":"text","required":true},{"key":"fact_1_ar","type":"text","required":true},{"key":"fact_2","type":"text","required":true},{"key":"fact_2_ar","type":"text","required":true},{"key":"fact_3","type":"text","required":false},{"key":"fact_3_ar","type":"text","required":false},{"key":"next_step_1","type":"text","required":true},{"key":"next_step_1_ar","type":"text","required":true},{"key":"next_step_2","type":"text","required":false},{"key":"next_step_2_ar","type":"text","required":false},{"key":"customer_advisory_url","type":"text","required":true},{"key":"spokesperson_name","type":"text","required":true},{"key":"spokesperson_title","type":"text","required":true},{"key":"press_office_email","type":"text","required":true},{"key":"press_office_phone","type":"text","required":true},{"key":"next_update_ksa","type":"datetime","required":true}]',
    'CMA Listing Rules Art.83 — material event disclosure',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-MED-RESOLVED-018')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-MED-RESOLVED-018',
    'Media — resolution / lessons-learned briefing',
    N'الإعلام — بيان حلّ الحادث والدروس المستفادة',
    'MEDIA', 'LESSONS_LEARNED', 'MEDIUM',
    'Resolution and lessons learned — {{entity_name}} — {{incident_code}}',
    N'بيان حلّ الحادث والدروس المستفادة — {{entity_name}} — {{incident_code}}',
    '{{entity_name}} has fully resolved the operational incident that affected {{affected_services_short}} between {{incident_start_ksa}} and {{restored_at_ksa}} KSA.\n\nWhat caused it: {{root_cause_public_summary}}\nWhat we have already corrected: {{immediate_corrections}}\nWhat we will improve over the next 90 days: {{ninety_day_commitments}}\n\nWe sincerely apologise to all customers who were inconvenienced. Their trust is what we are working to deserve every day.\n\nWe will publish a more detailed post-incident report on {{post_incident_report_url}} by {{report_due_date_ksa}} KSA.\n\nFor media enquiries: {{press_office_email}} / {{press_office_phone}}.',
    N'استكملت {{entity_name}} الحلّ الكامل للحادث التشغيلي الذي أثَّر على {{affected_services_short_ar}} في الفترة بين {{incident_start_ksa}} و {{restored_at_ksa}} بتوقيت المملكة.\n\nالسبب: {{root_cause_public_summary_ar}}\nالإجراءات التصحيحية المُتَّخذة: {{immediate_corrections_ar}}\nالتحسينات المُلتزَم بها خلال 90 يوماً قادمة: {{ninety_day_commitments_ar}}\n\nنعتذر بصدق لكافة العملاء الذين تأثَّروا بالحادث، وثقتهم هي ما نسعى لاستحقاقه كل يوم.\n\nسنُصدر تقريراً أكثر تفصيلاً ما بعد الحادث على {{post_incident_report_url}} بحلول {{report_due_date_ksa}} بتوقيت المملكة.\n\nللاستفسارات الإعلامية: {{press_office_email}} / {{press_office_phone}}.',
    NULL, NULL,
    N'[{"key":"entity_name","type":"text","required":true},{"key":"incident_code","type":"text","required":true},{"key":"affected_services_short","type":"text","required":true},{"key":"affected_services_short_ar","type":"text","required":true},{"key":"incident_start_ksa","type":"datetime","required":true},{"key":"restored_at_ksa","type":"datetime","required":true},{"key":"root_cause_public_summary","type":"textarea","required":true},{"key":"root_cause_public_summary_ar","type":"textarea","required":true},{"key":"immediate_corrections","type":"textarea","required":true},{"key":"immediate_corrections_ar","type":"textarea","required":true},{"key":"ninety_day_commitments","type":"textarea","required":true},{"key":"ninety_day_commitments_ar","type":"textarea","required":true},{"key":"post_incident_report_url","type":"text","required":true},{"key":"report_due_date_ksa","type":"datetime","required":true},{"key":"press_office_email","type":"text","required":true},{"key":"press_office_phone","type":"text","required":true}]',
    'ISO 22301 §9.1.2 + CMA disclosure guidance — public post-incident communication',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

-- ─── PUBLIC (2 templates) ───────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-PUB-PSA-019')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-PUB-PSA-019',
    'Public — service announcement during a major outage',
    N'إعلان عام للجمهور خلال انقطاع رئيسي',
    'PUBLIC', 'OUTAGE', 'HIGH',
    'Public service announcement — {{entity_name}}',
    N'إعلان عام للجمهور — {{entity_name}}',
    'Public service announcement\n\n{{entity_name}} would like to inform the public that the following services are temporarily unavailable:\n{{affected_services_list}}\n\nWe expect the services to be fully restored by {{eta_restored_at}} KSA.\n\nFor urgent needs, alternative channels are available at {{alternative_channels_list}}. Customer support remains available on {{contact_phone}} (English) / {{ar_contact_phone}} (Arabic).\n\nWe appreciate the public patience and apologise for the inconvenience.',
    N'إعلان عام للجمهور\n\nتُحيط {{entity_name}} الجمهور الكريم علماً بأنّ الخدمات التالية غير متاحة مؤقتاً:\n{{affected_services_list_ar}}\n\nيُتوقَّع عودة الخدمات إلى العمل بشكل طبيعي بحلول {{eta_restored_at}} بتوقيت المملكة.\n\nللحاجات العاجلة، تتوفَّر قنوات بديلة على {{alternative_channels_list_ar}}. مركز الدعم متاح على {{contact_phone}} (إنجليزي) / {{ar_contact_phone}} (عربي).\n\nنُقدِّر صبر الجمهور الكريم، ونعتذر عن الإزعاج.',
    'PSA: {{entity_name}} — {{affected_services_short}} unavailable. ETA {{eta_restored_at}} KSA. Alt: {{alternative_short}}.',
    N'إعلان عام: {{entity_name}} — {{affected_services_short_ar}} غير متاح. الوقت المتوقع {{eta_restored_at}} بتوقيت المملكة. البديل: {{alternative_short_ar}}.',
    N'[{"key":"entity_name","type":"text","required":true},{"key":"affected_services_list","type":"textarea","required":true},{"key":"affected_services_list_ar","type":"textarea","required":true},{"key":"affected_services_short","type":"text","required":true},{"key":"affected_services_short_ar","type":"text","required":true},{"key":"eta_restored_at","type":"datetime","required":true},{"key":"alternative_channels_list","type":"text","required":true},{"key":"alternative_channels_list_ar","type":"text","required":true},{"key":"alternative_short","type":"text","required":true},{"key":"alternative_short_ar","type":"text","required":true},{"key":"contact_phone","type":"text","required":true},{"key":"ar_contact_phone","type":"text","required":true}]',
    'SAMA Consumer Protection Principles + GCMA media guidance — public announcements',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_template_v2 WHERE organisation_id = @org AND template_code = 'CCT-PUB-RECOVERY-020')
INSERT INTO bcm.crisis_comms_template_v2 (
    organisation_id, template_code, title_en, title_ar,
    audience, scenario_category, severity_band,
    subject_en, subject_ar, body_en, body_ar,
    sms_body_en, sms_body_ar,
    placeholders_json, regulatory_anchor,
    approval_status, approved_by_legal_at, approved_by_legal_name,
    approved_by_ciso_at, approved_by_ciso_name
) VALUES (
    @org, 'CCT-PUB-RECOVERY-020',
    'Public — recovery declaration',
    N'إعلان عام بعودة الخدمات إلى العمل',
    'PUBLIC', 'RECOVERY', 'INFORMATIONAL',
    'All services restored — {{entity_name}}',
    N'عادت كل الخدمات إلى العمل بشكل طبيعي — {{entity_name}}',
    '{{entity_name}} confirms that all services affected by the recent disruption are now fully restored as of {{restored_at_ksa}} KSA.\n\nWe thank the public, our customers and our partners for their patience throughout the incident. A detailed post-incident report will be published on {{post_incident_report_url}} by {{report_due_date_ksa}} KSA.\n\nFor any residual issues please contact our 24/7 support line on {{contact_phone}} or visit {{support_portal}}.\n\nWith appreciation,\n{{entity_name}}',
    N'تؤكد {{entity_name}} أنّ جميع الخدمات التي تأثَّرت بالانقطاع الأخير عادت إلى العمل بشكل طبيعي اعتباراً من {{restored_at_ksa}} بتوقيت المملكة.\n\nنشكر الجمهور الكريم والعملاء والشركاء على صبرهم خلال الحادث. سيُنشر تقرير تفصيلي ما بعد الحادث على {{post_incident_report_url}} بحلول {{report_due_date_ksa}} بتوقيت المملكة.\n\nلأيّ مشاكل متبقّية يُرجى التواصل مع خط الدعم على مدار الساعة عبر {{ar_contact_phone}} أو من خلال {{support_portal}}.\n\nمع وافر التقدير،\n{{entity_name}}',
    '{{entity_name}}: all services restored {{restored_at_ksa}} KSA. Thank you for your patience.',
    N'{{entity_name}}: عادت جميع الخدمات للعمل {{restored_at_ksa}} بتوقيت المملكة. شكراً لصبركم.',
    N'[{"key":"entity_name","type":"text","required":true},{"key":"restored_at_ksa","type":"datetime","required":true},{"key":"post_incident_report_url","type":"text","required":true},{"key":"report_due_date_ksa","type":"datetime","required":true},{"key":"contact_phone","type":"text","required":true},{"key":"ar_contact_phone","type":"text","required":true},{"key":"support_portal","type":"text","required":true}]',
    'ISO 22301 §8.4.5 — public recovery declaration',
    'APPROVED_BY_CISO', @now, 'Legal Counsel — Mohammed Al-Otaibi',
    @now, 'CISO — Saad Al-Harthy'
);
GO
