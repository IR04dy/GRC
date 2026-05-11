-- V54 — Supplier-specific resilience tests (gap-analysis item).
--
-- Distinct from the generic BCM exercise framework, this entity captures a
-- supplier-targeted resilience test: a tabletop, a failover drill to an
-- alternate, an extended SLA validation, an exit-plan rehearsal, or a joint
-- crisis simulation. Each test links to the affected vendor and to the
-- AlternateSupplier or ExitPlan being rehearsed (free-form refs).

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'supplier_resilience_tests' AND schema_id = SCHEMA_ID('scm'))
BEGIN
    CREATE TABLE scm.supplier_resilience_tests (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,
        test_code                NVARCHAR(40)  NULL,

        vendor_id                BIGINT        NULL,
        vendor_name              NVARCHAR(255) NOT NULL,
        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NULL,

        -- TABLETOP | FAILOVER_DRILL | EXIT_REHEARSAL | SLA_VALIDATION | JOINT_CRISIS_SIM
        test_type                NVARCHAR(30)  NOT NULL,
        -- DESKTOP | PARTIAL_INVOCATION | FULL_INVOCATION
        scenario_scope           NVARCHAR(30)  NOT NULL DEFAULT N'DESKTOP',

        scenario_description_en  NVARCHAR(MAX) NOT NULL,
        scenario_description_ar  NVARCHAR(MAX) NULL,
        success_criteria_en      NVARCHAR(MAX) NULL,
        participants             NVARCHAR(MAX) NULL,   -- comma-separated list of team / role names

        linked_alternate_id      BIGINT        NULL,
        linked_exit_plan_id      BIGINT        NULL,
        linked_bcm_plan_id       BIGINT        NULL,

        scheduled_for            DATETIME2     NULL,
        executed_at              DATETIME2     NULL,
        duration_minutes         INT           NULL,

        -- Outcome
        --   PASS | PASS_WITH_OBSERVATIONS | FAIL | NOT_RUN
        outcome                  NVARCHAR(25)  NULL,
        rto_target_minutes       INT           NULL,
        rto_actual_minutes       INT           NULL,
        observations_en          NVARCHAR(MAX) NULL,
        observations_ar          NVARCHAR(MAX) NULL,
        action_items_en          NVARCHAR(MAX) NULL,
        capa_refs                NVARCHAR(MAX) NULL,
        evidence_refs            NVARCHAR(MAX) NULL,

        next_test_due            DATE          NULL,
        facilitator              NVARCHAR(255) NULL,
        approver_name            NVARCHAR(255) NULL,
        approved_at              DATETIME2     NULL,

        -- SCHEDULED | IN_PROGRESS | COMPLETED | APPROVED | CANCELLED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'SCHEDULED',
        cancellation_reason      NVARCHAR(MAX) NULL,

        is_deleted               SMALLINT      NOT NULL DEFAULT 0,
        is_active                SMALLINT      NOT NULL DEFAULT 1,
        deleted_at               DATETIME2     NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by               BIGINT        NULL,
        row_version              BIGINT        NOT NULL DEFAULT 0
    );

    CREATE INDEX IX_srt_org_status   ON scm.supplier_resilience_tests (organisation_id, status, is_deleted);
    CREATE INDEX IX_srt_vendor       ON scm.supplier_resilience_tests (organisation_id, vendor_id);
    CREATE INDEX IX_srt_next_due     ON scm.supplier_resilience_tests (organisation_id, next_test_due);
END
GO

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM scm.supplier_resilience_tests WHERE organisation_id = @org)
BEGIN
    INSERT INTO scm.supplier_resilience_tests
      (organisation_id, test_code, vendor_name, title_en, title_ar, test_type, scenario_scope,
       scenario_description_en, scenario_description_ar, success_criteria_en, participants,
       scheduled_for, executed_at, duration_minutes,
       outcome, rto_target_minutes, rto_actual_minutes,
       observations_en, action_items_en, evidence_refs,
       next_test_due, facilitator, approver_name, approved_at, status)
    VALUES
      (@org, N'SRT-2026-001', N'AWS', N'AWS eu-west-1 region failure tabletop',
       N'تمرين سيناريو فشل منطقة AWS أيرلندا',
       N'TABLETOP', N'DESKTOP',
       N'Simulate a full eu-west-1 outage lasting 6 hours. Walk through detection, customer comms, traffic redirection to eu-west-2, and decision to either ride out or invoke DR.',
       N'محاكاة انقطاع كامل لمنطقة eu-west-1 لمدة 6 ساعات والمشي خلال خطوات الاكتشاف والتواصل وإعادة توجيه الحركة.',
       N'(1) DR decision reached within 30 min; (2) customer comms drafted within 1h; (3) traffic redirected within 2h.',
       N'CISO, Head of Infra, Head of CX, BCM Officer, Comms Lead',
       '2026-03-15T09:00:00', '2026-03-15T09:00:00', 180,
       N'PASS_WITH_OBSERVATIONS', 120, 145,
       N'Decision reached in 35 min (target 30). Customer comms drafted in 50 min. Traffic redirect plan exercised on a sandbox account — DNS TTL longer than expected.',
       N'1. Reduce production CDN DNS TTL from 3600 to 300 seconds. 2. Pre-stage CX outage email in marketing tool.',
       N'EV-2026-SRT-001 (after-action report)',
       '2026-09-15', N'Head of Infra — Hani Al-Zahrani', N'CISO', '2026-03-22T10:00:00', N'APPROVED'),

      (@org, N'SRT-2026-002', N'Solutions by STC', N'STC managed-cloud failover drill',
       N'تمرين تبديل الفشل للسحابة المُدارة من STC',
       N'FAILOVER_DRILL', N'PARTIAL_INVOCATION',
       N'Live failover of one non-production workload from STC primary cloud to the warm-standby (Mobily) provisioned in V36 alternate-supplier register. Workload runs on standby for 4h, then fails back.',
       N'تبديل فشل حي لعبء عمل غير إنتاجي إلى Mobily الاحتياطي وتشغيله لمدة 4 ساعات ثم العودة.',
       N'(1) Workload reachable on standby ≤ 30 min; (2) data freshness ≤ 15 min; (3) fail back clean.',
       N'Head of Infra, STC TAM, Mobily SE, BCM Officer',
       '2026-04-08T08:00:00', '2026-04-08T08:00:00', 360,
       N'PASS', 30, 25,
       N'Workload up on standby in 25 min. Data freshness 8 min. Fail-back uneventful. Mobily integration validated end-to-end.',
       N'None — clean run. Schedule annual repeat.',
       N'EV-2026-SRT-002 (failover console screenshots + after-action report)',
       '2027-04-08', N'BCM Officer', N'CIO', '2026-04-15T11:00:00', N'APPROVED'),

      (@org, N'SRT-2026-003', N'TCS Saudi Arabia', N'TCS payroll BPO — exit plan rehearsal',
       N'تمرين خطة الخروج لخدمات BPO الرواتب من TCS',
       N'EXIT_REHEARSAL', N'DESKTOP',
       N'90-day exit-plan walk through: data extraction format, parallel-run with NextPay (alternate), HR cutover comms, regulator notifications.',
       N'استعراض خطة خروج 90 يومًا تشمل استخراج البيانات، تشغيل متوازي مع NextPay، تواصل الموارد البشرية، وإشعارات الجهات التنظيمية.',
       N'(1) Exit milestones validated; (2) data-extract format produces complete records; (3) HR cutover plan signed off.',
       N'COO, CHRO, CFO, DPO, TCS Account Manager',
       '2026-05-20T10:00:00', '2026-05-20T10:00:00', 240,
       N'PASS_WITH_OBSERVATIONS', NULL, NULL,
       N'Data extract format works for active employees but legacy terminated-employee records require a separate query path. Cutover sequencing acceptable.',
       N'Add a "terminated-employee historical extract" runbook to the exit plan. Schedule a live extract test with NextPay in Q4.',
       N'EV-2026-SRT-003 (exit-plan walkthrough minutes)',
       '2026-11-20', N'COO — Khalid Al-Anazi', N'CEO', '2026-05-27T14:00:00', N'APPROVED'),

      (@org, N'SRT-2026-004', N'Workday Ltd', N'Workday HCM data-loss restoration test',
       N'اختبار استعادة Workday من فقدان البيانات',
       N'SLA_VALIDATION', N'PARTIAL_INVOCATION',
       N'Validate Workday''s contractual RPO of 1 hour by initiating a sandbox restore from yesterday''s backup and comparing record counts vs the live tenant.',
       N'التحقق من RPO التعاقدي البالغ ساعة واحدة عبر استعادة sandbox من النسخ الاحتياطي.',
       N'(1) Restore initiated by Workday within 4h of ticket; (2) restored data RPO ≤ 1h; (3) record-count delta ≤ 0.01%.',
       N'Head of HRIS, Workday CSM, BCM Officer',
       '2026-06-10T09:00:00', NULL, NULL,
       N'NOT_RUN', 60, NULL,
       NULL, NULL, NULL,
       NULL, N'Head of HRIS', NULL, NULL, N'SCHEDULED'),

      (@org, N'SRT-2026-005', N'Stripe Payments Europe', N'Stripe payment outage tabletop',
       N'تمرين سيناريو انقطاع Stripe',
       N'TABLETOP', N'DESKTOP',
       N'Simulate a 2-hour Stripe outage during peak Eid Al-Fitr promotion. Walk through fallback to direct bank settlement, customer comms, ops escalation.',
       N'محاكاة انقطاع Stripe لمدة ساعتين خلال ذروة العروض، والمشي خلال خطوات الرجوع للتسوية المصرفية المباشرة.',
       N'(1) Fallback method identified within 15 min; (2) customer-facing message live within 30 min.',
       N'CFO, Head of Payments, Head of Customer Ops',
       '2026-07-05T11:00:00', NULL, NULL,
       NULL, 30, NULL,
       NULL, NULL, NULL,
       NULL, N'CFO', NULL, NULL, N'IN_PROGRESS'),

      (@org, N'SRT-2026-006', N'OpenAI LLC', N'OpenAI API outage joint crisis simulation',
       N'محاكاة أزمة مشتركة لانقطاع OpenAI',
       N'JOINT_CRISIS_SIM', N'PARTIAL_INVOCATION',
       N'Simulate a 24-hour OpenAI global outage. Joint exercise with Azure OpenAI Service team — switch all copilot calls to Azure-hosted endpoint, validate downstream UX.',
       N'محاكاة انقطاع عالمي لـ OpenAI لمدة 24 ساعة والتحويل إلى نقاط نهاية Azure.',
       N'(1) Cutover to Azure endpoint ≤ 30 min; (2) copilot UX unchanged for users.',
       N'CISO, Head of Platform, Azure Account Team, Compliance Lead',
       '2026-08-22T10:00:00', '2026-08-22T10:00:00', 480,
       N'FAIL', 30, 95,
       N'Azure endpoint cutover took 95 min — well over target. Root cause: hardcoded API key in two services; feature-flag fallback was untested.',
       N'1. Move all LLM credentials to centralised secret store. 2. Add feature-flag-driven endpoint switching to every LLM client. 3. Re-test in Q4.',
       N'EV-2026-SRT-006 (after-action + replay logs)',
       '2026-11-22', N'CISO — Ahmed Al-Otaibi', N'CIO', '2026-08-29T16:00:00', N'APPROVED'),

      (@org, N'SRT-2026-007', N'HubSpot Germany GmbH', N'Cancelled: HubSpot infrastructure failover test',
       N'مُلغى: تمرين تبديل الفشل لـ HubSpot',
       N'FAILOVER_DRILL', N'PARTIAL_INVOCATION',
       N'Planned exercise to validate HubSpot regional failover from EU to US tenant; cancelled because HubSpot does not contractually offer cross-region failover for our plan.',
       N'تمرين مخطّط لإثبات تبديل الفشل بين منطقتي EU وUS؛ مُلغى لعدم توفّر التغطية التعاقدية.',
       N'(1) Failover triggered; (2) data parity validated.',
       N'CMO, HubSpot CSM',
       '2026-09-15T10:00:00', NULL, NULL,
       NULL, NULL, NULL,
       NULL, NULL, NULL,
       NULL, NULL, NULL, NULL, N'CANCELLED'),

      (@org, N'SRT-2026-008', N'Multi-vendor — SOC managed-services partners', N'Joint SOC-MSSP cyber incident tabletop',
       N'تمرين سيناريو حوادث سيبراني مع شركاء SOC',
       N'JOINT_CRISIS_SIM', N'DESKTOP',
       N'Joint tabletop with our two MSSP partners (DXC + Help AG) simulating a ransomware detection at 02:00 KSA time. Validate handoff between in-house SOC, external MSSPs, and CSIRT.',
       N'تمرين مع شريكَي MSSP لمحاكاة كشف برنامج فدية في الساعة الثانية صباحاً.',
       N'(1) Detection-to-MSSP-confirmation ≤ 15 min; (2) CSIRT bridge convened ≤ 30 min.',
       N'CISO, SOC Manager, DXC TAM, Help AG SOC Lead, CSIRT lead',
       '2026-10-12T02:00:00', NULL, NULL,
       NULL, NULL, NULL,
       NULL, NULL, NULL,
       NULL, N'SOC Manager — Reem Al-Mutairi', NULL, NULL, N'SCHEDULED');
END
GO
