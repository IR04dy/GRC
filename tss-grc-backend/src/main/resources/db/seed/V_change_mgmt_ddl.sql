-- ============================================================================
-- TSS GRC — Change Management module (ITIL v4 Change Enablement)
-- ============================================================================
-- High-end process design with full lifecycle, CAB approvals, implementation
-- tasks, post-implementation review, generic cross-module links to risks,
-- controls, incidents, vendors, BCM plans, audit findings, policies, assets.
--
-- State machine:
--   DRAFT ─► SUBMITTED ─► IMPACT_ASSESSED ─► CAB_PENDING ┬─► CAB_APPROVED
--                                                       └─► CAB_REJECTED
--   CAB_APPROVED ─► SCHEDULED ─► IMPLEMENTING ─► IMPLEMENTED ─► POST_REVIEW
--                                                                     └─► CLOSED
--   Emergency:  DRAFT ─► EMERGENCY_APPROVED ─► IMPLEMENTING ─► … (post-review)
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'change_mgmt')
    EXEC('CREATE SCHEMA change_mgmt');
PRINT N'Schema change_mgmt ensured.';

-- ── change_requests ────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='change_mgmt' AND t.name='change_requests')
BEGIN
    CREATE TABLE change_mgmt.change_requests (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_chreq_extid DEFAULT NEWID(),
        organisation_id       BIGINT NOT NULL,
        change_code           NVARCHAR(50) NULL,                    -- e.g. CHG-2025-0042
        title_en              NVARCHAR(500) NOT NULL,
        title_ar              NVARCHAR(500) NULL,
        description_en        NVARCHAR(MAX) NULL,
        description_ar        NVARCHAR(MAX) NULL,
        change_type           NVARCHAR(30) NOT NULL CONSTRAINT DF_chreq_type DEFAULT N'NORMAL',
        -- STANDARD (pre-approved) | NORMAL (CAB review) | EMERGENCY (expedited)
        category              NVARCHAR(40) NULL,
        -- INFRASTRUCTURE | APPLICATION | NETWORK | SECURITY | POLICY | PROCESS
        --   | COMPLIANCE_REMEDIATION | EMERGENCY_FIX | DATA | THIRD_PARTY
        risk_level            NVARCHAR(20) NULL,                    -- LOW | MEDIUM | HIGH | CRITICAL
        impact_summary        NVARCHAR(MAX) NULL,
        rollback_plan         NVARCHAR(MAX) NULL,
        test_plan             NVARCHAR(MAX) NULL,
        business_justification NVARCHAR(MAX) NULL,
        requested_by_id       BIGINT NULL,
        requested_by_name     NVARCHAR(255) NULL,
        change_owner_id       BIGINT NULL,
        change_owner_name     NVARCHAR(255) NULL,
        implementer_name      NVARCHAR(255) NULL,
        requested_date        DATE NULL,
        scheduled_start       DATETIME2 NULL,
        scheduled_end         DATETIME2 NULL,
        implementation_started DATETIME2 NULL,
        implementation_ended   DATETIME2 NULL,
        post_review_date      DATE NULL,
        closure_date          DATE NULL,
        status                NVARCHAR(40) NOT NULL CONSTRAINT DF_chreq_status DEFAULT N'DRAFT',
        cab_decision_at       DATETIME2 NULL,
        cab_decision_notes    NVARCHAR(MAX) NULL,
        outcome               NVARCHAR(30) NULL,
        -- SUCCESS | PARTIAL_SUCCESS | FAILED | ROLLED_BACK
        is_deleted            BIT NOT NULL CONSTRAINT DF_chreq_isdel DEFAULT 0,
        created_at            DATETIME2 NOT NULL CONSTRAINT DF_chreq_created DEFAULT SYSUTCDATETIME(),
        updated_at            DATETIME2 NULL,
        created_by            BIGINT NULL,
        updated_by            BIGINT NULL
    );
    CREATE INDEX IX_chreq_org_status ON change_mgmt.change_requests(organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_chreq_org_type   ON change_mgmt.change_requests(organisation_id, change_type) WHERE is_deleted = 0;
    PRINT N'Created change_mgmt.change_requests';
END
ELSE PRINT N'change_mgmt.change_requests already exists';

-- ── change_approvals (CAB voting log) ───────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='change_mgmt' AND t.name='change_approvals')
BEGIN
    CREATE TABLE change_mgmt.change_approvals (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        change_request_id BIGINT NOT NULL,
        approver_id       BIGINT NULL,
        approver_name     NVARCHAR(255) NOT NULL,
        approver_role     NVARCHAR(100) NULL,        -- Change Manager / CAB Member / CISO / Business Owner
        decision          NVARCHAR(30) NOT NULL,     -- APPROVE | REJECT | DEFER | ABSTAIN
        comments          NVARCHAR(MAX) NULL,
        decided_at        DATETIME2 NOT NULL CONSTRAINT DF_chapp_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_chapp_req FOREIGN KEY (change_request_id) REFERENCES change_mgmt.change_requests(id)
    );
    CREATE INDEX IX_chapp_req ON change_mgmt.change_approvals(change_request_id);
    PRINT N'Created change_mgmt.change_approvals';
END
ELSE PRINT N'change_mgmt.change_approvals already exists';

-- ── change_tasks (implementation steps) ─────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='change_mgmt' AND t.name='change_tasks')
BEGIN
    CREATE TABLE change_mgmt.change_tasks (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        change_request_id BIGINT NOT NULL,
        sort_order        INT NOT NULL CONSTRAINT DF_chtask_sort DEFAULT 0,
        title_en          NVARCHAR(500) NOT NULL,
        title_ar          NVARCHAR(500) NULL,
        owner_name        NVARCHAR(255) NULL,
        status            NVARCHAR(30) NOT NULL CONSTRAINT DF_chtask_status DEFAULT N'PENDING',
        -- PENDING | IN_PROGRESS | DONE | BLOCKED | SKIPPED
        due_at            DATETIME2 NULL,
        completed_at      DATETIME2 NULL,
        notes             NVARCHAR(MAX) NULL,
        CONSTRAINT FK_chtask_req FOREIGN KEY (change_request_id) REFERENCES change_mgmt.change_requests(id)
    );
    CREATE INDEX IX_chtask_req ON change_mgmt.change_tasks(change_request_id);
    PRINT N'Created change_mgmt.change_tasks';
END
ELSE PRINT N'change_mgmt.change_tasks already exists';

-- ── change_links (generic cross-module integration) ─────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='change_mgmt' AND t.name='change_links')
BEGIN
    CREATE TABLE change_mgmt.change_links (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        change_request_id BIGINT NOT NULL,
        entity_type       NVARCHAR(40) NOT NULL,
        -- RISK | CONTROL | INCIDENT | VENDOR | BCM_PLAN | AUDIT_FINDING | POLICY
        --   | REGULATORY_CHANGE | DPIA | KRI | ASSET | EVIDENCE
        entity_id         BIGINT NOT NULL,
        link_type         NVARCHAR(30) NOT NULL CONSTRAINT DF_chlink_type DEFAULT N'AFFECTS',
        -- AFFECTS | TRIGGERED_BY | MITIGATES | TARGETS | REMEDIATES | DEPENDS_ON | INFORMS
        notes             NVARCHAR(MAX) NULL,
        created_at        DATETIME2 NOT NULL CONSTRAINT DF_chlink_created DEFAULT SYSUTCDATETIME(),
        is_deleted        BIT NOT NULL CONSTRAINT DF_chlink_isdel DEFAULT 0,
        CONSTRAINT FK_chlink_req FOREIGN KEY (change_request_id) REFERENCES change_mgmt.change_requests(id),
        CONSTRAINT UQ_chlink_combo UNIQUE (change_request_id, entity_type, entity_id, link_type)
    );
    CREATE INDEX IX_chlink_req ON change_mgmt.change_links(change_request_id) WHERE is_deleted = 0;
    CREATE INDEX IX_chlink_target ON change_mgmt.change_links(entity_type, entity_id) WHERE is_deleted = 0;
    PRINT N'Created change_mgmt.change_links';
END
ELSE PRINT N'change_mgmt.change_links already exists';

-- ── change_comments (collaboration thread) ──────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='change_mgmt' AND t.name='change_comments')
BEGIN
    CREATE TABLE change_mgmt.change_comments (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        change_request_id BIGINT NOT NULL,
        author_id         BIGINT NULL,
        author_name       NVARCHAR(255) NOT NULL,
        body              NVARCHAR(MAX) NOT NULL,
        created_at        DATETIME2 NOT NULL CONSTRAINT DF_chcom_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_chcom_req FOREIGN KEY (change_request_id) REFERENCES change_mgmt.change_requests(id)
    );
    CREATE INDEX IX_chcom_req ON change_mgmt.change_comments(change_request_id);
    PRINT N'Created change_mgmt.change_comments';
END
ELSE PRINT N'change_mgmt.change_comments already exists';

-- ── Seed: 4 representative changes with realistic Saudi context ─────────────
DECLARE @org BIGINT = 1;

INSERT INTO change_mgmt.change_requests
  (organisation_id, change_code, title_en, title_ar,
   description_en, change_type, category, risk_level,
   impact_summary, rollback_plan, test_plan, business_justification,
   requested_by_name, change_owner_name, implementer_name,
   requested_date, scheduled_start, scheduled_end, status, outcome)
SELECT @org, v.code, v.en, v.ar, v.dsc, v.tp, v.cat, v.risk,
       v.imp, v.rb, v.tst, v.bj, v.req, v.own, v.impl,
       v.rdate, v.sst, v.sen, v.st, v.outc
FROM (VALUES
  (N'CHG-2025-0001',
   N'NCA ECC v2.0 — enable MFA on all privileged AD accounts',
   N'تفعيل المصادقة الثنائية على جميع الحسابات الإدارية في الدليل النشط',
   N'Roll out Microsoft Authenticator MFA enforcement to all Domain Admins, Tier-0 service accounts and break-glass accounts to satisfy NCA ECC v2.0 §2-4-3-1.',
   N'NORMAL', N'SECURITY', N'HIGH',
   N'Affects 47 privileged identities. Sign-in latency +1-3s on first auth per session.',
   N'Disable MFA conditional access policy via PIM-controlled emergency override; documented in MS playbook PB-IAM-014.',
   N'Pilot with 3 senior admins for 5 business days, validate sign-ins, then phase per OU.',
   N'NCA ECC §2-4-3-1 + SAMA CSF 3.2.5 require MFA for all privileged access. Audit finding F-2025-0142 references this gap.',
   N'CISO Office', N'Head of IAM', N'Identity Engineering Team',
   '2025-01-12', '2025-01-22 02:00', '2025-01-22 06:00', N'CLOSED', N'SUCCESS'),
  (N'CHG-2025-0002',
   N'Migrate core banking to KSA-resident region (Riyadh AZ-2)',
   N'نقل النواة المصرفية إلى منطقة المملكة (الرياض - منطقة التوافر الثانية)',
   N'Re-provision the core banking application tier and read-replica from EU-WEST-1 to KSA-CENTRAL-1 (AZ-2) to satisfy SAMA data-residency Article 11.',
   N'NORMAL', N'INFRASTRUCTURE', N'CRITICAL',
   N'4-hour planned downtime window. Tier-1 services unavailable. Estimated 28K customer-facing transactions deferred.',
   N'Failback DNS + restore application tier to EU-WEST-1 from previous-night snapshot. RTO 90 minutes per BCM plan BCM-CB-002.',
   N'Two full DR rehearsals against KSA-CENTRAL-1 staging plus one full data integrity reconciliation against the EU master.',
   N'SAMA Cyber Security Framework 1.4 + recently-clarified PDPL Implementing Regulations Art. 29 require KSA residency for customer data.',
   N'CIO Office', N'Cloud Platforms Lead', N'Cloud Migrations Squad',
   '2025-02-04', '2025-03-15 23:00', '2025-03-16 03:00', N'IMPLEMENTING', NULL),
  (N'CHG-2025-0003',
   N'Patch CVE-2025-0149 — Apache Tomcat RCE on customer portal',
   N'ترقيع ثغرة CVE-2025-0149 على بوابة العملاء',
   N'Emergency patch for actively-exploited Apache Tomcat 10.1.x RCE vulnerability on the customer-facing portal cluster.',
   N'EMERGENCY', N'SECURITY', N'CRITICAL',
   N'Brief portal restart per node, rolling deployment behind load balancer. No expected outage.',
   N'Roll back to previous container image revision tomcat-10.1.18.',
   N'Pre-prod smoke test executed before approval; canary deploy 1 of 6 nodes; monitor 30 min before fleet rollout.',
   N'NCA cybersecurity advisory ALR-2025-021 issued 2025-04-28 mandates patch within 72 hours.',
   N'SOC L3 — Mansour Al-Qahtani', N'CISO', N'AppSec on-call',
   '2025-04-29', '2025-04-29 22:00', '2025-04-30 02:00', N'IMPLEMENTED', N'SUCCESS'),
  (N'CHG-2025-0004',
   N'Update Acceptable Use Policy for AI tools (Vision 2030 alignment)',
   N'تحديث سياسة الاستخدام المقبول لأدوات الذكاء الاصطناعي',
   N'Republish the Acceptable Use Policy with new sections governing employee use of generative-AI tools, in line with SDAIA AI Ethics Principles and Vision 2030 digital-economy guidance.',
   N'STANDARD', N'POLICY', N'LOW',
   N'No system change. Communications + attestation campaign for all 1,247 employees.',
   N'Reinstate v3.2 of the AUP if the new version creates unworkable operational constraints.',
   N'Legal review + works-council consultation completed. SDAIA principles cross-checked.',
   N'SDAIA AI Ethics + PDPL Implementing Regulations + new HRSD circular HR-2025-014.',
   N'Group Legal', N'Chief People Officer', N'HR Comms',
   '2025-04-15', '2025-05-10 09:00', '2025-05-10 17:00', N'CAB_PENDING', NULL)
) AS v(code, en, ar, dsc, tp, cat, risk, imp, rb, tst, bj, req, own, impl, rdate, sst, sen, st, outc)
WHERE NOT EXISTS (SELECT 1 FROM change_mgmt.change_requests c
                  WHERE c.organisation_id=@org AND c.change_code=v.code AND c.is_deleted=0);

PRINT N'Seeded 4 sample change requests.';

-- ── Seed: implementation tasks for the migration change ─────────────────────
DECLARE @migrationId BIGINT = (SELECT TOP 1 id FROM change_mgmt.change_requests
                               WHERE change_code='CHG-2025-0002' AND is_deleted=0);
IF @migrationId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM change_mgmt.change_tasks WHERE change_request_id = @migrationId)
BEGIN
    INSERT INTO change_mgmt.change_tasks (change_request_id, sort_order, title_en, title_ar, owner_name, status)
    VALUES
      (@migrationId, 10, N'Snapshot EU-WEST-1 master + verify integrity', N'أخذ لقطة من النسخة الأم في الاتحاد الأوروبي والتحقق', N'DBA Lead', N'DONE'),
      (@migrationId, 20, N'Bring up KSA-CENTRAL-1 read-replica',           N'تشغيل نسخة القراءة في منطقة المملكة',                  N'Cloud Eng',  N'DONE'),
      (@migrationId, 30, N'Cut DNS to KSA region',                         N'تحويل الـ DNS إلى منطقة المملكة',                       N'Network Ops',N'IN_PROGRESS'),
      (@migrationId, 40, N'Promote KSA replica to master',                 N'ترقية نسخة المملكة لتكون الأم',                         N'DBA Lead',   N'PENDING'),
      (@migrationId, 50, N'Run reconciliation report + business sign-off', N'تشغيل تقرير التسوية والحصول على موافقة الأعمال',        N'Change Mgr', N'PENDING');
    PRINT N'Seeded migration tasks.';
END

-- ── Seed: CAB approvals for the policy update ──────────────────────────────
DECLARE @policyId BIGINT = (SELECT TOP 1 id FROM change_mgmt.change_requests
                            WHERE change_code='CHG-2025-0004' AND is_deleted=0);
IF @policyId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM change_mgmt.change_approvals WHERE change_request_id = @policyId)
BEGIN
    INSERT INTO change_mgmt.change_approvals (change_request_id, approver_name, approver_role, decision, comments)
    VALUES
      (@policyId, N'Group Legal Director', N'Legal',          N'APPROVE', N'Wording aligns with SDAIA principles. Approved.'),
      (@policyId, N'CISO',                  N'Security',       N'APPROVE', N'No objections from security perspective.'),
      (@policyId, N'Head of HR',            N'Business Owner', N'DEFER',   N'Need to confirm consultation with works council before approval.');
    PRINT N'Seeded sample CAB approvals.';
END

-- ── Result summary ──────────────────────────────────────────────────────────
SELECT
    N'Change Management DDL complete' AS status,
    (SELECT COUNT(*) FROM change_mgmt.change_requests WHERE is_deleted=0) AS changes,
    (SELECT COUNT(*) FROM change_mgmt.change_tasks)    AS tasks,
    (SELECT COUNT(*) FROM change_mgmt.change_approvals) AS approvals,
    (SELECT COUNT(*) FROM change_mgmt.change_links WHERE is_deleted=0) AS links;
GO
