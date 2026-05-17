-- ============================================================================
-- TSS GRC — Phase B-1: Ethics & Compliance Cases (whistleblower + investigations)
-- ============================================================================
-- Hotline reports + investigations workflow modeled on NAVEX Global / industry-standard tooling
-- Ethics. Saudi Whistleblower Protection Law mandates a confidential channel
-- with named protection, anonymity option, audit trail of disposition, and
-- retaliation tracking. State machine:
--
--   INTAKE → TRIAGE → INVESTIGATION ┬─► SUBSTANTIATED → REMEDIATION → CLOSED
--                                   ├─► UNSUBSTANTIATED → CLOSED
--                                   └─► NO_VIOLATION    → CLOSED
--   Any state → ESCALATED (regulator notified) or DISMISSED.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'ethics')
    EXEC('CREATE SCHEMA ethics');
PRINT N'Schema ethics ensured.';

-- ── ethics.cases ───────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='ethics' AND t.name='cases')
BEGIN
    CREATE TABLE ethics.cases (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_ethics_case_extid DEFAULT NEWID(),
        organisation_id       BIGINT NOT NULL,
        case_code             NVARCHAR(50) NULL,                -- e.g. CASE-2025-0042
        title_en              NVARCHAR(500) NOT NULL,
        title_ar              NVARCHAR(500) NULL,
        category              NVARCHAR(50) NOT NULL,
        -- BRIBERY | KICKBACK | CONFLICT_OF_INTEREST | FRAUD | THEFT | HARASSMENT
        --   | DISCRIMINATION | RETALIATION | DATA_MISUSE | AML_SUSPICION
        --   | POLICY_VIOLATION | SAFETY | OTHER
        severity              NVARCHAR(20) NOT NULL CONSTRAINT DF_ethics_case_sev DEFAULT N'MEDIUM',
        -- LOW | MEDIUM | HIGH | CRITICAL
        intake_channel        NVARCHAR(40) NOT NULL CONSTRAINT DF_ethics_case_chan DEFAULT N'HOTLINE',
        -- HOTLINE | WEB_FORM | EMAIL | IN_PERSON | LETTER | REGULATOR
        -- Reporter identity — anonymous reporters get name=NULL
        reporter_name         NVARCHAR(255) NULL,
        reporter_email        NVARCHAR(255) NULL,
        reporter_employee_id  BIGINT NULL,
        is_anonymous          BIT NOT NULL CONSTRAINT DF_ethics_case_anon DEFAULT 0,
        -- Subject (the alleged wrongdoer) — stored separately so reporter
        -- protection rules apply
        subject_summary       NVARCHAR(MAX) NULL,
        -- Narrative
        description           NVARCHAR(MAX) NOT NULL,
        location              NVARCHAR(255) NULL,
        date_of_incident      DATE NULL,
        framework_id          BIGINT NULL,                       -- e.g. NAZAHA, ISO_37001, SAMA_AML
        clause_ref            NVARCHAR(100) NULL,
        -- Workflow state
        status                NVARCHAR(40) NOT NULL CONSTRAINT DF_ethics_case_status DEFAULT N'INTAKE',
        -- INTAKE | TRIAGE | INVESTIGATION | SUBSTANTIATED | UNSUBSTANTIATED
        --   | NO_VIOLATION | REMEDIATION | ESCALATED | DISMISSED | CLOSED
        triage_decision       NVARCHAR(30) NULL,                 -- ACCEPT | REJECT | NEEDS_INFO
        investigator_name     NVARCHAR(255) NULL,
        investigator_id       BIGINT NULL,
        committee_name        NVARCHAR(255) NULL,
        triaged_at            DATETIME2 NULL,
        investigation_started DATETIME2 NULL,
        investigation_ended   DATETIME2 NULL,
        outcome               NVARCHAR(40) NULL,
        -- DISCIPLINARY | TERMINATION | TRAINING | POLICY_UPDATE | REGULATOR_REPORT | NO_ACTION
        outcome_summary       NVARCHAR(MAX) NULL,
        regulator_notified    BIT NOT NULL CONSTRAINT DF_ethics_case_regnot DEFAULT 0,
        regulator_reference   NVARCHAR(100) NULL,
        retaliation_check_at  DATETIME2 NULL,
        retaliation_observed  BIT NULL,
        closure_date          DATE NULL,
        is_deleted            BIT NOT NULL CONSTRAINT DF_ethics_case_isdel DEFAULT 0,
        created_at            DATETIME2 NOT NULL CONSTRAINT DF_ethics_case_created DEFAULT SYSUTCDATETIME(),
        updated_at            DATETIME2 NULL,
        created_by            BIGINT NULL,
        updated_by            BIGINT NULL,
        CONSTRAINT FK_ethics_case_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id)
    );
    CREATE INDEX IX_ethics_case_org_status ON ethics.cases(organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_ethics_case_category   ON ethics.cases(organisation_id, category) WHERE is_deleted = 0;
    PRINT N'Created ethics.cases';
END
ELSE PRINT N'ethics.cases already exists';

-- ── ethics.case_actions (timeline of investigation steps) ───────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='ethics' AND t.name='case_actions')
BEGIN
    CREATE TABLE ethics.case_actions (
        id          BIGINT IDENTITY(1,1) PRIMARY KEY,
        case_id     BIGINT NOT NULL,
        action_type NVARCHAR(40) NOT NULL,
        -- INTERVIEW | EVIDENCE_COLLECTED | DOCUMENT_REVIEWED | FORENSIC_REQUEST
        --   | LEGAL_CONSULT | HR_REFERRAL | EXTERNAL_REFERRAL | NOTE
        title       NVARCHAR(500) NOT NULL,
        description NVARCHAR(MAX) NULL,
        action_at   DATETIME2 NOT NULL CONSTRAINT DF_ethics_act_at DEFAULT SYSUTCDATETIME(),
        actor_name  NVARCHAR(255) NULL,
        actor_id    BIGINT NULL,
        is_confidential BIT NOT NULL CONSTRAINT DF_ethics_act_conf DEFAULT 1,
        CONSTRAINT FK_ethics_act_case FOREIGN KEY (case_id) REFERENCES ethics.cases(id)
    );
    CREATE INDEX IX_ethics_act_case ON ethics.case_actions(case_id, action_at);
    PRINT N'Created ethics.case_actions';
END
ELSE PRINT N'ethics.case_actions already exists';

-- ── ethics.case_links (cross-module: link cases to risks/findings/changes) ──
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='ethics' AND t.name='case_links')
BEGIN
    CREATE TABLE ethics.case_links (
        id          BIGINT IDENTITY(1,1) PRIMARY KEY,
        case_id     BIGINT NOT NULL,
        entity_type NVARCHAR(40) NOT NULL,
        -- RISK | AUDIT_FINDING | CONTROL | POLICY | CHANGE_REQUEST | INCIDENT
        --   | VENDOR | EVIDENCE | DPIA
        entity_id   BIGINT NOT NULL,
        link_type   NVARCHAR(30) NOT NULL CONSTRAINT DF_ethics_link_type DEFAULT N'RELATES_TO',
        -- RELATES_TO | RAISED_FROM | RESULTED_IN | REMEDIATED_BY
        notes       NVARCHAR(MAX) NULL,
        created_at  DATETIME2 NOT NULL CONSTRAINT DF_ethics_link_created DEFAULT SYSUTCDATETIME(),
        is_deleted  BIT NOT NULL CONSTRAINT DF_ethics_link_isdel DEFAULT 0,
        CONSTRAINT FK_ethics_link_case FOREIGN KEY (case_id) REFERENCES ethics.cases(id),
        CONSTRAINT UQ_ethics_link_combo UNIQUE (case_id, entity_type, entity_id, link_type)
    );
    CREATE INDEX IX_ethics_link_case   ON ethics.case_links(case_id) WHERE is_deleted = 0;
    CREATE INDEX IX_ethics_link_target ON ethics.case_links(entity_type, entity_id) WHERE is_deleted = 0;
    PRINT N'Created ethics.case_links';
END
ELSE PRINT N'ethics.case_links already exists';

-- ── Seed: 4 representative cases (Saudi context) ───────────────────────────
DECLARE @org BIGINT = 1;
DECLARE @fNAZ BIGINT = (SELECT id FROM compliance.frameworks WHERE code='NAZAHA');
DECLARE @fISO BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_37001');
DECLARE @fSAMA BIGINT = (SELECT id FROM compliance.frameworks WHERE code='SAMA_AML');
DECLARE @fMOCI BIGINT = (SELECT id FROM compliance.frameworks WHERE code='MOCI_COC');

INSERT INTO ethics.cases
  (organisation_id, case_code, title_en, title_ar, category, severity, intake_channel,
   reporter_name, is_anonymous, subject_summary, description, location, date_of_incident,
   framework_id, clause_ref, status, triage_decision, investigator_name, committee_name,
   triaged_at, investigation_started, outcome, outcome_summary, regulator_notified)
SELECT @org, v.code, v.en, v.ar, v.cat, v.sev, v.ch, v.rep, v.anon, v.subj, v.dsc,
       v.loc, v.doi, v.fw, v.cls, v.st, v.td, v.inv, v.cm, v.tat, v.ist, v.outc, v.outs, v.reg
FROM (VALUES
  (N'CASE-2025-0001',
   N'Suspected kickback in vendor onboarding (Riyadh)',
   N'اشتباه بعمولة غير مشروعة في قبول مورّد (الرياض)',
   N'KICKBACK', N'HIGH', N'HOTLINE',
   NULL, 1,
   N'Procurement specialist (anonymized) and an external vendor representative.',
   N'Hotline reporter alleges that a procurement specialist accepted a 50,000 SAR cash gift in exchange for fast-tracking the onboarding of a new IT vendor. Vendor was added to AVL outside the standard committee review.',
   N'Riyadh HQ — Procurement Department', '2025-04-12',
   @fNAZ, N'Nazaha §4-2',
   N'INVESTIGATION', N'ACCEPT', N'Internal Audit — Mr. Saleh Al-Dossary',
   N'Disciplinary & Ethics Committee',
   '2025-04-15 09:00', '2025-04-18 10:00',
   NULL, NULL, 0),
  (N'CASE-2025-0002',
   N'Possible AML alert: structuring patterns in corporate account',
   N'تنبيه غسل أموال محتمل: أنماط هيكلة في حساب شركة',
   N'AML_SUSPICION', N'CRITICAL', N'EMAIL',
   N'AML Compliance Analyst — Layla Al-Anazi', 0,
   N'Corporate banking client (account masked).',
   N'Transaction monitoring flagged repeated cash deposits of SAR 49,500–49,900 across 11 branches in 8 business days. Pattern matches FATF structuring typology. Forwarded for review and likely STR filing.',
   N'Multiple branches — automated monitoring', '2025-04-20',
   @fSAMA, N'SAMA AML §3-2-7',
   N'TRIAGE', N'ACCEPT', N'Financial Crime Unit',
   N'AML Steering Committee',
   '2025-04-22 14:30', NULL,
   NULL, NULL, 0),
  (N'CASE-2025-0003',
   N'Conflict of interest — manager hiring relative without disclosure',
   N'تضارب مصالح — مدير قام بتوظيف قريب دون إفصاح',
   N'CONFLICT_OF_INTEREST', N'MEDIUM', N'WEB_FORM',
   N'Anonymous web submission', 1,
   N'Department head, Logistics.',
   N'Web-form submission states that a department manager hired their cousin into a junior analyst role without filing the required conflict-of-interest disclosure. References Code of Conduct §3.2.',
   N'Jeddah Operations — Logistics', '2025-03-30',
   @fMOCI, N'MoCI CoC §3.2',
   N'SUBSTANTIATED', N'ACCEPT', N'HR Investigations Lead',
   N'HR Disciplinary Committee',
   '2025-04-01 11:00', '2025-04-03 09:00',
   N'POLICY_UPDATE', N'Disclosure filed retroactively. Hire reviewed and approved by exec; manager attended COI training.', 0),
  (N'CASE-2025-0004',
   N'Alleged retaliation against earlier whistleblower',
   N'ادعاء بانتقام ضد مبلّغ سابق',
   N'RETALIATION', N'CRITICAL', N'IN_PERSON',
   NULL, 1,
   N'Senior manager, Finance.',
   N'In-person disclosure to the DPO that the original reporter of CASE-2025-0001 was reassigned to a remote site one week after filing. Reviewing whether this constitutes prohibited retaliation under the Saudi Whistleblower Protection Law.',
   N'Riyadh HQ — Finance Department', '2025-04-25',
   @fNAZ, N'Whistleblower Protection Law Art. 5',
   N'INTAKE', NULL, N'Legal — Group General Counsel',
   N'Board Audit Committee',
   NULL, NULL,
   NULL, NULL, 1)
) AS v(code, en, ar, cat, sev, ch, rep, anon, subj, dsc, loc, doi, fw, cls, st, td, inv, cm, tat, ist, outc, outs, reg)
WHERE NOT EXISTS (SELECT 1 FROM ethics.cases c WHERE c.organisation_id=@org AND c.case_code=v.code AND c.is_deleted=0);

PRINT N'Seeded 4 sample ethics cases.';

-- ── Seed: action timeline for the substantiated COI case ─────────────────
DECLARE @coiId BIGINT = (SELECT TOP 1 id FROM ethics.cases WHERE case_code='CASE-2025-0003' AND is_deleted=0);
IF @coiId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ethics.case_actions WHERE case_id=@coiId)
BEGIN
    INSERT INTO ethics.case_actions (case_id, action_type, title, description, actor_name)
    VALUES
      (@coiId, N'INTERVIEW',          N'Interview with reporting party',  N'30-min phone interview confirming details from web form.',                 N'HR Investigations Lead'),
      (@coiId, N'DOCUMENT_REVIEWED',  N'HRIS hiring records pulled',      N'Cross-checked hiring approval chain — no COI declaration on file.',         N'HR Investigations Lead'),
      (@coiId, N'INTERVIEW',          N'Interview with subject (manager)', N'Manager confirmed family relationship; cited oversight, not concealment.', N'HR Investigations Lead'),
      (@coiId, N'HR_REFERRAL',        N'Referred to Disciplinary Committee', N'Recommendation: COI training + retroactive disclosure.',                   N'HR Investigations Lead'),
      (@coiId, N'NOTE',               N'Outcome agreed and case closed',  N'Disclosure filed; manager completed COI training Apr-08.',                  N'HR Disciplinary Committee');
END

-- ── Result summary ──────────────────────────────────────────────────────
SELECT
    N'Phase B-1 — ethics cases DDL complete' AS status,
    (SELECT COUNT(*) FROM ethics.cases WHERE is_deleted=0)        AS cases,
    (SELECT COUNT(*) FROM ethics.case_actions)                    AS actions,
    (SELECT COUNT(*) FROM ethics.case_links WHERE is_deleted=0)   AS links;
GO
