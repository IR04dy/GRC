-- V154 — Committees & Meetings GRC platform foundation.
--
-- Repositions the committees module from a basic register (V40) into a
-- real-world day-to-day governance platform. Adds:
--
--   admin.committee_templates          Saudi-regulated committee catalog —
--                                       19 templates spanning board, audit,
--                                       risk, compliance, cyber, BCM, AML,
--                                       data governance, AI ethics, ESG…
--   admin.committee_standing_items     Recurring agenda items pre-bound to
--                                       each committee template. The annual
--                                       calendar generator auto-populates
--                                       these into every meeting agenda
--                                       before the secretary even starts.
--   admin.committee_form_templates     Downloadable bilingual forms library
--                                       (agenda, attendance, motion, minutes,
--                                       action register, COI declaration,
--                                       skills matrix, annual evaluation).
--                                       Each form is anchored to a regulator
--                                       expectation so the SAMA examiner can
--                                       trace why each artefact exists.
--   admin.committee_meeting_papers     Pre-read papers tracked per meeting
--                                       with read receipts — closes the
--                                       "I never received the pack" gap
--                                       SAMA examiners ask about.
--   admin.committee_resolutions        First-class resolution rows (replaces
--                                       the V40 decisions_json blob) so
--                                       resolutions are queryable, exportable
--                                       to the board resolutions register,
--                                       and dependency-trackable from other
--                                       modules.
--   admin.committee_action_items       First-class action items (replaces
--                                       action_items_json) with explicit
--                                       owner + due date + cross-module link
--                                       (linkedModule + linkedEntityId).
--   admin.committee_member_terms       Term-of-office tracking — CMA + SAMA
--                                       expect documented appointment dates,
--                                       independence, and renewal limits.
--   admin.committee_member_skills      Skills matrix per member (financial
--                                       expert, cyber-security expertise,
--                                       independence flag, sector experience).
--                                       Drives the gap analysis the
--                                       Nomination Committee reviews.
--
-- Extends admin.committees with:
--   charter_template_code              FK to template (which template did
--                                       this committee instantiate from?)
--   parent_committee_id                Sub-committee delegation (e.g. Crisis
--                                       Sub-Committee under BCM Steering)
--   reports_to_committee_id            Upward reporting line for management
--                                       committees (e.g. Risk Committee
--                                       reports to Board)
--   regulatory_domain                  BANKING | INSURANCE | CMA | PUBLIC |
--                                       HEALTHCARE | GENERAL — drives which
--                                       templates are surfaced as relevant
--   regulator_anchors                  Multi-value string (SAMA, CMA, NCA,
--                                       PDPL, NDMO, SDAIA, ZATCA, ISO22301…)
--   next_charter_review_at             Annual charter review due-date
--                                       (CMA mandates yearly charter review)
--
-- The 19 templates cover the Saudi GRC committee universe documented in:
--   * CMA Corporate Governance Regulations
--   * SAMA Regulatory Governance Framework 2017
--   * SAMA Cyber-Security Framework
--   * NCA Essential Cyber-Security Controls (ECC-2) — control 1-2-1
--     mandates a Cyber-Security Committee
--   * PDPL Article 30 (Data Protection Officer / Privacy Committee)
--   * NDMO Data Governance Framework
--   * SDAIA AI Ethics Principles
--   * Anti-Money-Laundering Law Article 41
--   * SAMA Internal Control Guidelines
--   * ISO 22301 (BCM Steering)

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ═════════════════════════════════════════════════════════════════════
-- 1. Committee templates (the Saudi-regulated catalog)
-- ═════════════════════════════════════════════════════════════════════

IF OBJECT_ID(N'admin.committee_templates', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_templates (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        template_code            NVARCHAR(80)  NOT NULL,
        committee_type           NVARCHAR(30)  NOT NULL,    -- see check below
        regulatory_domain        NVARCHAR(40)  NOT NULL,    -- BANKING | INSURANCE | CMA | PUBLIC | GENERAL
        regulator_anchors        NVARCHAR(500) NULL,        -- comma-separated: SAMA, CMA, NCA, PDPL…

        name_en                  NVARCHAR(255) NOT NULL,
        name_ar                  NVARCHAR(255) NOT NULL,

        mandate_en               NVARCHAR(MAX) NOT NULL,    -- "Why this committee exists"
        mandate_ar               NVARCHAR(MAX) NOT NULL,

        authority_en             NVARCHAR(MAX) NOT NULL,    -- "What it can decide"
        authority_ar             NVARCHAR(MAX) NOT NULL,

        composition_en           NVARCHAR(MAX) NOT NULL,    -- expected member profile
        composition_ar           NVARCHAR(MAX) NOT NULL,

        quorum_pct               INT           NOT NULL,    -- e.g. 67
        cadence                  NVARCHAR(20)  NOT NULL,    -- WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY|SEMI_ANNUAL|ANNUAL|AD_HOC
        term_years               INT           NULL,        -- 3 for board members, NULL for management committees

        reports_to_template_code NVARCHAR(80)  NULL,        -- which template this one reports to (Risk → Board, ALCO → Risk…)
        escalation_en            NVARCHAR(MAX) NULL,        -- what gets escalated up
        escalation_ar            NVARCHAR(MAX) NULL,

        regulator_citation_en    NVARCHAR(MAX) NULL,        -- "CMA Corporate Governance Article 14 mandates…"
        regulator_citation_ar    NVARCHAR(MAX) NULL,

        independence_required    SMALLINT      NOT NULL DEFAULT 0,   -- chair must be independent
        financial_expert_required SMALLINT     NOT NULL DEFAULT 0,
        regulatory_reporting_en  NVARCHAR(MAX) NULL,        -- to whom + how often

        is_active                SMALLINT      NOT NULL DEFAULT 1,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT UQ_committee_template_code UNIQUE (template_code),
        CONSTRAINT CK_committee_template_type CHECK (committee_type IN (
            N'BOARD', N'EXEC_COMMITTEE', N'RISK_COMMITTEE', N'AUDIT_COMMITTEE',
            N'BCM_COMMITTEE', N'COMPLIANCE_COMMITTEE', N'TECH_COMMITTEE',
            N'NOMINATION_REMUNERATION', N'CYBER_SECURITY', N'DATA_GOVERNANCE',
            N'PRIVACY_DPO', N'CRISIS_MANAGEMENT', N'AML', N'IT_STEERING',
            N'ALCO', N'INVESTMENT', N'AI_ETHICS', N'ANTI_FRAUD',
            N'INTERNAL_CONTROL', N'ESG_SUSTAINABILITY', N'ARCH_REVIEW', N'OTHER')),
        CONSTRAINT CK_committee_template_domain CHECK (regulatory_domain IN (
            N'BANKING', N'INSURANCE', N'CMA', N'PUBLIC', N'HEALTHCARE', N'GENERAL')),
        CONSTRAINT CK_committee_template_cadence CHECK (cadence IN (
            N'WEEKLY', N'BIWEEKLY', N'MONTHLY', N'QUARTERLY',
            N'SEMI_ANNUAL', N'ANNUAL', N'AD_HOC'))
    );

    CREATE INDEX IX_committee_template_domain
        ON admin.committee_templates (regulatory_domain, committee_type);
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 2. Standing agenda items (recurring per template)
-- ═════════════════════════════════════════════════════════════════════

IF OBJECT_ID(N'admin.committee_standing_items', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_standing_items (
        id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        template_code       NVARCHAR(80)  NOT NULL,
        seq                 INT           NOT NULL,
        topic_en            NVARCHAR(500) NOT NULL,
        topic_ar            NVARCHAR(500) NOT NULL,
        presenter_role      NVARCHAR(120) NULL,
        minutes_allocated   INT           NOT NULL DEFAULT 15,
        is_mandatory        SMALLINT      NOT NULL DEFAULT 1,
        source_module       NVARCHAR(40)  NULL,    -- e.g. RISK, AUDIT, CYBER — drives auto-population
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,
        created_at          DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT FK_standing_item_template
            FOREIGN KEY (template_code) REFERENCES admin.committee_templates (template_code),
        CONSTRAINT UQ_standing_item_seq UNIQUE (template_code, seq)
    );

    CREATE INDEX IX_standing_item_template
        ON admin.committee_standing_items (template_code, seq);
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 3. Form templates (downloadable bilingual library)
-- ═════════════════════════════════════════════════════════════════════

IF OBJECT_ID(N'admin.committee_form_templates', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_form_templates (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        form_code            NVARCHAR(80)  NOT NULL,
        form_type            NVARCHAR(40)  NOT NULL,    -- AGENDA | ATTENDANCE | MOTION | MINUTES | ACTION_REGISTER | COI_DECLARATION | SKILLS_MATRIX | ANNUAL_EVALUATION
        applicable_to        NVARCHAR(500) NOT NULL,    -- comma-separated committee_type values (or 'ALL')
        regulator_anchors    NVARCHAR(500) NULL,
        title_en             NVARCHAR(255) NOT NULL,
        title_ar             NVARCHAR(255) NOT NULL,
        purpose_en           NVARCHAR(MAX) NOT NULL,
        purpose_ar           NVARCHAR(MAX) NOT NULL,
        content_md_en        NVARCHAR(MAX) NOT NULL,    -- full form body in Markdown
        content_md_ar        NVARCHAR(MAX) NOT NULL,
        completion_guide_en  NVARCHAR(MAX) NULL,        -- "How to fill this in"
        completion_guide_ar  NVARCHAR(MAX) NULL,
        version              NVARCHAR(20)  NOT NULL DEFAULT N'1.0',
        is_active            SMALLINT      NOT NULL DEFAULT 1,
        created_at           DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT UQ_form_template_code UNIQUE (form_code),
        CONSTRAINT CK_form_template_type CHECK (form_type IN (
            N'AGENDA', N'ATTENDANCE', N'MOTION', N'MINUTES',
            N'ACTION_REGISTER', N'COI_DECLARATION', N'SKILLS_MATRIX',
            N'ANNUAL_EVALUATION', N'PRE_READ_COVERSHEET', N'CHARTER'))
    );
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 4. Meeting papers (pre-read with read receipts)
-- ═════════════════════════════════════════════════════════════════════

IF OBJECT_ID(N'admin.committee_meeting_papers', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_meeting_papers (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT        NOT NULL,
        meeting_id           BIGINT        NOT NULL,
        paper_code           NVARCHAR(80)  NOT NULL,
        title_en             NVARCHAR(500) NOT NULL,
        title_ar             NVARCHAR(500) NULL,
        agenda_item_seq      INT           NULL,
        author_name          NVARCHAR(255) NULL,
        author_role          NVARCHAR(120) NULL,
        circulated_at        DATETIME2(3)  NULL,
        circulated_by        BIGINT        NULL,
        confidentiality      NVARCHAR(20)  NOT NULL DEFAULT N'INTERNAL',  -- PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED
        file_storage_ref     NVARCHAR(500) NULL,   -- DMS reference, not the file itself
        read_receipts_json   NVARCHAR(MAX) NULL,   -- [{userId, readAt}]
        late_paper           SMALLINT      NOT NULL DEFAULT 0,
        late_paper_reason    NVARCHAR(500) NULL,
        created_at           DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT CK_meeting_paper_confidentiality CHECK (confidentiality IN (
            N'PUBLIC', N'INTERNAL', N'CONFIDENTIAL', N'RESTRICTED'))
    );

    CREATE INDEX IX_meeting_paper_meeting
        ON admin.committee_meeting_papers (organisation_id, meeting_id, agenda_item_seq);
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 5. Resolutions (first-class — replaces decisions_json blob)
-- ═════════════════════════════════════════════════════════════════════

IF OBJECT_ID(N'admin.committee_resolutions', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_resolutions (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT        NOT NULL,
        meeting_id           BIGINT        NOT NULL,
        committee_id         BIGINT        NOT NULL,
        resolution_code      NVARCHAR(80)  NOT NULL,    -- RES-2026-Q1-BOARD-003
        seq                  INT           NOT NULL,
        agenda_item_seq      INT           NULL,
        title_en             NVARCHAR(500) NOT NULL,
        title_ar             NVARCHAR(500) NULL,
        body_en              NVARCHAR(MAX) NOT NULL,    -- the actual resolution text
        body_ar              NVARCHAR(MAX) NULL,
        proposed_by          NVARCHAR(255) NULL,
        seconded_by          NVARCHAR(255) NULL,
        vote_for             INT           NOT NULL DEFAULT 0,
        vote_against         INT           NOT NULL DEFAULT 0,
        vote_abstain         INT           NOT NULL DEFAULT 0,
        outcome              NVARCHAR(20)  NOT NULL,    -- APPROVED | REJECTED | DEFERRED | WITHDRAWN
        confidentiality      NVARCHAR(20)  NOT NULL DEFAULT N'INTERNAL',
        effective_from       DATE          NULL,
        review_required_by   DATE          NULL,
        regulator_notify     SMALLINT      NOT NULL DEFAULT 0,   -- some resolutions trigger CMA/SAMA notification
        dissent_notes_en     NVARCHAR(MAX) NULL,        -- record of dissenting opinions
        dissent_notes_ar     NVARCHAR(MAX) NULL,
        created_at           DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT UQ_resolution_code UNIQUE (organisation_id, resolution_code),
        CONSTRAINT CK_resolution_outcome CHECK (outcome IN (
            N'APPROVED', N'REJECTED', N'DEFERRED', N'WITHDRAWN')),
        CONSTRAINT CK_resolution_confidentiality CHECK (confidentiality IN (
            N'PUBLIC', N'INTERNAL', N'CONFIDENTIAL', N'RESTRICTED'))
    );

    CREATE INDEX IX_resolution_meeting
        ON admin.committee_resolutions (organisation_id, meeting_id, seq);
    CREATE INDEX IX_resolution_committee
        ON admin.committee_resolutions (organisation_id, committee_id, effective_from DESC);
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 6. Action items (first-class — replaces action_items_json blob)
-- ═════════════════════════════════════════════════════════════════════

IF OBJECT_ID(N'admin.committee_action_items', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_action_items (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT        NOT NULL,
        meeting_id           BIGINT        NOT NULL,
        committee_id         BIGINT        NOT NULL,
        action_code          NVARCHAR(80)  NOT NULL,    -- ACT-2026-Q1-RISK-007
        seq                  INT           NOT NULL,
        agenda_item_seq      INT           NULL,
        title_en             NVARCHAR(500) NOT NULL,
        title_ar             NVARCHAR(500) NULL,
        description_en       NVARCHAR(MAX) NULL,
        description_ar       NVARCHAR(MAX) NULL,
        owner_user_id        BIGINT        NULL,
        owner_name           NVARCHAR(255) NOT NULL,
        owner_role           NVARCHAR(120) NULL,
        due_date             DATE          NOT NULL,
        priority             NVARCHAR(20)  NOT NULL DEFAULT N'NORMAL',   -- LOW | NORMAL | HIGH | CRITICAL
        status               NVARCHAR(20)  NOT NULL DEFAULT N'OPEN',     -- OPEN | IN_PROGRESS | COMPLETED | OVERDUE | CANCELLED | DEFERRED
        completion_notes_en  NVARCHAR(MAX) NULL,
        completed_at         DATETIME2(3)  NULL,
        completed_by         BIGINT        NULL,

        -- Cross-module linkage — this action item came from / relates to
        -- another record (Risk-DEMO-001, FND-DEMO-002, CAPA-007, etc.)
        linked_module        NVARCHAR(40)  NULL,        -- RISK | AUDIT | CAPA | INCIDENT | POLICY | CYBER | BCM | …
        linked_entity_type   NVARCHAR(120) NULL,        -- Risk | AuditFinding | CapaTask | Incident | …
        linked_entity_id     BIGINT        NULL,
        linked_entity_code   NVARCHAR(80)  NULL,        -- denormalised for fast display

        carried_from_meeting_id BIGINT     NULL,        -- if rolled forward from previous meeting
        created_at           DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT UQ_action_item_code UNIQUE (organisation_id, action_code),
        CONSTRAINT CK_action_item_priority CHECK (priority IN (
            N'LOW', N'NORMAL', N'HIGH', N'CRITICAL')),
        CONSTRAINT CK_action_item_status CHECK (status IN (
            N'OPEN', N'IN_PROGRESS', N'COMPLETED', N'OVERDUE', N'CANCELLED', N'DEFERRED'))
    );

    CREATE INDEX IX_action_item_meeting
        ON admin.committee_action_items (organisation_id, meeting_id, seq);
    CREATE INDEX IX_action_item_open
        ON admin.committee_action_items (organisation_id, committee_id, status, due_date)
        WHERE status IN (N'OPEN', N'IN_PROGRESS', N'OVERDUE');
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 7. Member terms (term-of-office tracking)
-- ═════════════════════════════════════════════════════════════════════

IF OBJECT_ID(N'admin.committee_member_terms', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_member_terms (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT        NOT NULL,
        committee_id         BIGINT        NOT NULL,
        member_name          NVARCHAR(255) NOT NULL,
        member_user_id       BIGINT        NULL,
        role                 NVARCHAR(120) NOT NULL,    -- Chair | Vice-Chair | Member | Secretary | Standing Invitee | External Advisor
        is_voting_member     SMALLINT      NOT NULL DEFAULT 1,
        is_independent       SMALLINT      NOT NULL DEFAULT 0,   -- per CMA / SAMA independence test
        independence_basis_en NVARCHAR(500) NULL,                -- "No commercial relationship in last 3 years…"

        appointed_at         DATE          NOT NULL,
        appointed_by_reso    NVARCHAR(80)  NULL,        -- board resolution code that appointed them
        term_starts_at       DATE          NOT NULL,
        term_ends_at         DATE          NULL,        -- NULL for indefinite (management members)
        term_number          INT           NOT NULL DEFAULT 1,   -- 1, 2, 3 (CMA caps at 3 terms for non-execs)
        renewal_status       NVARCHAR(20)  NOT NULL DEFAULT N'ACTIVE',  -- ACTIVE | EXPIRED | RESIGNED | REMOVED | RENEWED

        end_reason           NVARCHAR(500) NULL,
        ended_at             DATETIME2(3)  NULL,

        created_at           DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT CK_member_term_renewal CHECK (renewal_status IN (
            N'ACTIVE', N'EXPIRED', N'RESIGNED', N'REMOVED', N'RENEWED'))
    );

    CREATE INDEX IX_member_term_committee
        ON admin.committee_member_terms (organisation_id, committee_id, renewal_status);
    CREATE INDEX IX_member_term_expiry
        ON admin.committee_member_terms (organisation_id, term_ends_at)
        WHERE renewal_status = N'ACTIVE' AND term_ends_at IS NOT NULL;
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 8. Member skills (skills matrix — drives gap analysis)
-- ═════════════════════════════════════════════════════════════════════

IF OBJECT_ID(N'admin.committee_member_skills', N'U') IS NULL
BEGIN
    CREATE TABLE admin.committee_member_skills (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT        NOT NULL,
        committee_id         BIGINT        NOT NULL,
        member_term_id       BIGINT        NULL,        -- FK to member_terms (NULL for ad-hoc external advisors)
        member_name          NVARCHAR(255) NOT NULL,
        skill_dimension      NVARCHAR(60)  NOT NULL,    -- FINANCIAL_EXPERT | CYBER_EXPERT | BCM_EXPERT | AML_EXPERT | LEGAL | INDUSTRY | DIGITAL | ESG | RISK_MGMT
        skill_level          NVARCHAR(20)  NOT NULL,    -- BASIC | INTERMEDIATE | EXPERT
        evidence_en          NVARCHAR(MAX) NULL,        -- "20-year career in audit at Big-4 firms" — CMA-style basis
        certification        NVARCHAR(255) NULL,        -- e.g. "CPA, CFA, CISA, CISSP"
        years_experience     INT           NULL,
        last_reviewed_at     DATE          NULL,
        created_at           DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT CK_member_skill_level CHECK (skill_level IN (
            N'BASIC', N'INTERMEDIATE', N'EXPERT'))
    );

    CREATE INDEX IX_member_skill_committee
        ON admin.committee_member_skills (organisation_id, committee_id, skill_dimension);
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 9. Extend admin.committees with template + reporting-line columns
-- ═════════════════════════════════════════════════════════════════════

IF COL_LENGTH('admin.committees', 'charter_template_code') IS NULL
BEGIN
    ALTER TABLE admin.committees ADD
        charter_template_code      NVARCHAR(80)  NULL,
        parent_committee_id        BIGINT        NULL,
        reports_to_committee_id    BIGINT        NULL,
        regulatory_domain          NVARCHAR(40)  NULL,
        regulator_anchors          NVARCHAR(500) NULL,
        next_charter_review_at     DATE          NULL,
        formation_resolution_code  NVARCHAR(80)  NULL,
        annual_calendar_locked_at  DATETIME2(3)  NULL;
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- ── Seed: 19 Saudi-regulated committee templates ──
--
-- Note on style: each template's mandate, authority, composition, etc. is
-- written as if a Saudi GRC consultant drafted the charter for a CMA-listed
-- joint-stock company. The strings are bilingual — Arabic side is Saudi
-- regulator-style language, not literal back-translation from English.
-- ═════════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM admin.committee_templates WHERE template_code = N'CTPL-BOARD')
BEGIN
INSERT INTO admin.committee_templates
    (template_code, committee_type, regulatory_domain, regulator_anchors,
     name_en, name_ar,
     mandate_en, mandate_ar,
     authority_en, authority_ar,
     composition_en, composition_ar,
     quorum_pct, cadence, term_years,
     reports_to_template_code, escalation_en, escalation_ar,
     regulator_citation_en, regulator_citation_ar,
     independence_required, financial_expert_required, regulatory_reporting_en)
VALUES

-- ─── 1. Board of Directors ─────────────────────────────────────────────
(N'CTPL-BOARD', N'BOARD', N'GENERAL', N'CMA,SAMA,Companies-Law',
 N'Board of Directors', N'مجلس الإدارة',
 N'Highest governance body of the company. Sets the strategic direction, approves the annual business plan and budget, sets the risk appetite, appoints (and dismisses) the CEO, oversees the executive team and the work of all standing committees. Acts as the ultimate steward of shareholder, regulator and societal interests.',
 N'الجهة العليا للحوكمة في الشركة. يحدد التوجه الاستراتيجي، يعتمد خطة العمل والميزانية السنوية، يحدد شهية المخاطر، يعيّن (ويُقيل) الرئيس التنفيذي، يشرف على الفريق التنفيذي وعمل جميع اللجان الدائمة. هو الراعي النهائي لمصالح المساهمين والجهات التنظيمية والمجتمع.',

 N'Approves strategy, annual budget, dividend, capital allocation, M&A above SAR 50m, executive appointments at C-suite level, risk appetite, related-party transactions, changes to the corporate-governance charter, and committee charters. Delegates day-to-day management to the CEO subject to a written authority matrix.',
 N'يعتمد الاستراتيجية، الميزانية السنوية، توزيع الأرباح، تخصيص رأس المال، عمليات الاندماج والاستحواذ التي تفوق 50 مليون ريال، التعيينات التنفيذية لكبار المسؤولين، شهية المخاطر، صفقات الأطراف ذات العلاقة، تعديلات ميثاق حوكمة الشركات وميثاق اللجان. يفوّض الإدارة اليومية للرئيس التنفيذي وفق مصفوفة صلاحيات مكتوبة.',

 N'Chairman + 6-9 non-executive directors. CMA Corporate Governance Article 16 requires at least one-third independent. Mix of skills must cover finance, sector expertise, risk, technology / cyber, governance, and ESG. Chairman must be a non-executive. CEO sits as the only executive director.',
 N'الرئيس + 6-9 أعضاء غير تنفيذيين. تشترط المادة 16 من لائحة حوكمة الشركات الصادرة عن هيئة السوق المالية ألا يقل عدد المستقلين عن ثلث الأعضاء. يجب أن تغطي مزيج المهارات التمويل، الخبرة القطاعية، المخاطر، التقنية / السيبرانية، الحوكمة، والاستدامة. يجب أن يكون الرئيس غير تنفيذي. يحضر الرئيس التنفيذي كعضو تنفيذي وحيد.',

 75, N'QUARTERLY', 3,
 NULL,
 N'Escalates only to the General Assembly (shareholders) for matters requiring shareholder vote under Companies Law — capital increase/decrease, dividend changes, dissolution, related-party transactions above thresholds, amendments to the company by-laws.',
 N'لا يتم التصعيد إلا إلى الجمعية العامة (المساهمين) في الأمور التي تتطلب تصويت المساهمين بموجب نظام الشركات — زيادة/تخفيض رأس المال، تغييرات توزيع الأرباح، الانحلال، صفقات الأطراف ذات العلاقة فوق الحدود، تعديل النظام الأساسي.',

 N'CMA Corporate Governance Regulations (Articles 16-26) + Saudi Companies Law (Articles 68-86) — frequency, composition, independence, and authority all derive from these.',
 N'لائحة حوكمة الشركات الصادرة عن هيئة السوق المالية (المواد 16-26) + نظام الشركات السعودي (المواد 68-86) — التواتر والتشكيل والاستقلالية والصلاحيات تستند إلى هذه المواد.',
 1, 1,
 N'Annual Corporate Governance Report to CMA (Article 95). Material decisions disclosed to Tadawul under CMA disclosure rules. Annual general assembly notice within 21 days.'),

-- ─── 2. Audit Committee ────────────────────────────────────────────────
(N'CTPL-AUDIT', N'AUDIT_COMMITTEE', N'CMA', N'CMA,SAMA,External-Audit',
 N'Audit Committee', N'لجنة المراجعة',
 N'Oversees the integrity of financial reporting, the independence and performance of the external auditor, the scope and effectiveness of internal audit, and the company''s system of internal controls. Custodian of the whistle-blower channel.',
 N'تشرف على نزاهة التقارير المالية، استقلالية وأداء المراجع الخارجي، نطاق وفاعلية المراجعة الداخلية، ونظام الرقابة الداخلية في الشركة. هي الحارس لقناة المبلّغين عن المخالفات.',

 N'Pre-approves the external auditor''s scope of work and fees, recommends auditor appointment/rotation to the General Assembly, approves the internal audit charter and annual plan, reviews FYE financial statements before Board sign-off, investigates whistle-blower disclosures, attests to NCA ECC and SAMA cyber evidence packages.',
 N'يعتمد مسبقاً نطاق وأتعاب المراجع الخارجي، يوصي بتعيين/تدوير المراجع للجمعية العامة، يعتمد ميثاق وخطة المراجعة الداخلية السنوية، يراجع القوائم المالية السنوية قبل اعتماد المجلس، يحقق في بلاغات المخالفات، يصدّق على حزم أدلة NCA ECC وضوابط ساما السيبرانية.',

 N'3-5 non-executive directors. CMA Article 14 requires the majority to be independent and at least one member with proven financial / accounting expertise (the "financial expert"). Internal Audit head and External Auditor attend as standing invitees but are not voting members.',
 N'3-5 أعضاء غير تنفيذيين. تشترط المادة 14 من لائحة هيئة السوق المالية أن تكون الأغلبية مستقلة وأن يتوفر في عضو واحد على الأقل خبرة محاسبية أو مالية مثبتة ("الخبير المالي"). يحضر رئيس المراجعة الداخلية والمراجع الخارجي كمدعوين دائمين دون حق التصويت.',

 67, N'QUARTERLY', 3,
 N'CTPL-BOARD',
 N'Material audit findings, management-letter points, regulator examinations, whistle-blower cases with sustained allegations, and disagreements with the external auditor that could not be resolved at committee level all escalate to the Board.',
 N'تُصعّد إلى المجلس: الملاحظات المراجعة الجوهرية، نقاط خطاب الإدارة، الفحوصات التنظيمية، بلاغات المخالفات المؤيدة، والخلافات مع المراجع الخارجي التي لم تُحل على مستوى اللجنة.',

 N'CMA Corporate Governance Regulations Article 14 mandates the existence, composition, and authority of this committee. SAMA Internal Control Guidelines further require a quarterly review of internal audit progress and management''s assertions.',
 N'تنص المادة 14 من لائحة حوكمة الشركات الصادرة عن هيئة السوق المالية على وجود هذه اللجنة وتشكيلها وصلاحياتها. كما تتطلب إرشادات الرقابة الداخلية الصادرة عن ساما مراجعة ربع سنوية لتقدم المراجعة الداخلية وتأكيدات الإدارة.',
 1, 1,
 N'Annual Audit Committee Report to General Assembly + CMA (Article 14). Quarterly briefing to Board. Reports of any whistleblower investigations summarised in the annual report.'),

-- ─── 3. Risk Committee ─────────────────────────────────────────────────
(N'CTPL-RISK', N'RISK_COMMITTEE', N'GENERAL', N'SAMA,CMA,Basel-III',
 N'Risk Management Committee', N'لجنة إدارة المخاطر',
 N'Owns the enterprise risk-management framework. Monthly review of the risk register, the KRI / KPI dashboard, and the active risk-acceptance pipeline. Recommends the risk appetite (and any breaches thereof) to the Board, sponsors scenario-analysis exercises, and ensures the three lines of defence are operating as designed.',
 N'تمتلك إطار إدارة المخاطر المؤسسي. تراجع شهرياً سجل المخاطر، لوحة مؤشرات المخاطر والأداء، وخط أنابيب قبول المخاطر النشط. توصي للمجلس بشهية المخاطر (وأي تجاوزات لها)، ترعى تمارين تحليل السيناريوهات، وتضمن أن خطوط الدفاع الثلاثة تعمل وفق التصميم.',

 N'Approves risk acceptances within delegated authority (typically up to 50% of single-loss appetite), recommends acceptance above that to the Board. Approves KRI thresholds, scenario-analysis findings, capital-adequacy-impact assessments (banks), risk-model assumptions, and major changes to the risk taxonomy.',
 N'يعتمد قبول المخاطر ضمن الصلاحيات المفوّضة (عادة حتى 50% من شهية الخسارة الفردية)، ويوصي للمجلس بالقبول فوق ذلك. يعتمد عتبات مؤشرات المخاطر، نتائج تحليل السيناريوهات، تقييمات أثر كفاية رأس المال (للبنوك)، افتراضات نماذج المخاطر، والتعديلات الجوهرية لتصنيف المخاطر.',

 N'Chair: Chief Risk Officer (CRO). Voting members: CFO, COO, CISO, General Counsel. Standing invitees: DPO, Head of Internal Audit, Chief Compliance Officer (non-voting to preserve independence of the second-line function from third-line oversight).',
 N'الرئيس: مدير المخاطر التنفيذي (CRO). أعضاء بحق التصويت: المدير المالي، مدير العمليات، مدير أمن المعلومات، المستشار العام. مدعوون دائمون: مسؤول حماية البيانات، رئيس المراجعة الداخلية، رئيس الالتزام (دون تصويت للحفاظ على استقلالية الخط الثاني عن إشراف الخط الثالث).',

 67, N'MONTHLY', NULL,
 N'CTPL-BOARD',
 N'Risk-appetite breaches above ESCALATION threshold (typically amber for 60+ days or red for any duration), risk acceptances above delegated authority, new emerging risks rated high/critical, model-validation failures, and any KRI breach requiring change to the risk appetite escalate to the Board.',
 N'تُصعّد إلى المجلس: تجاوزات شهية المخاطر فوق عتبة التصعيد (عادة كهرماني لمدة 60+ يوم أو أحمر لأي مدة)، قبول المخاطر فوق الصلاحية المفوّضة، المخاطر الناشئة الجديدة المصنفة عالية/حرجة، فشل التحقق من النماذج، وأي تجاوز لمؤشر المخاطر يتطلب تغيير الشهية.',

 N'SAMA Regulatory Governance Framework 2017 mandates a separate risk committee at the Board level for banks and insurers; CMA Corporate Governance Article 24 recommends a risk committee for all listed entities. Basel III requires risk-committee oversight of capital and liquidity (banks).',
 N'يفرض إطار الحوكمة التنظيمية الصادر عن ساما 2017 وجود لجنة مخاطر مستقلة على مستوى المجلس للبنوك وشركات التأمين؛ المادة 24 من لائحة هيئة السوق المالية توصي بلجنة مخاطر لجميع الكيانات المدرجة. يتطلب Basel III إشراف لجنة المخاطر على رأس المال والسيولة (للبنوك).',
 0, 0,
 N'Quarterly risk report to Board. Annual risk-appetite refresh. ICAAP / ILAAP filings to SAMA (banks). Material risk-acceptance disclosures to CMA when required by disclosure rules.'),

-- ─── 4. Nomination & Remuneration Committee ────────────────────────────
(N'CTPL-NOMREM', N'NOMINATION_REMUNERATION', N'CMA', N'CMA,Corporate-Governance',
 N'Nomination & Remuneration Committee', N'لجنة الترشيحات والمكافآت',
 N'Owns the Board composition and the executive remuneration policy. Conducts the annual skills-gap analysis, runs the director-nomination process for the General Assembly, sets executive pay (subject to Board approval), and oversees the CEO succession plan.',
 N'تملك تشكيل المجلس وسياسة مكافآت التنفيذيين. تجري التحليل السنوي لفجوة المهارات، تدير عملية ترشيح أعضاء المجلس للجمعية العامة، تحدد مكافآت التنفيذيين (بعد موافقة المجلس)، وتشرف على خطة خلافة الرئيس التنفيذي.',

 N'Approves the skills matrix, recommends director candidates to the General Assembly, approves the remuneration policy, sets executive bonuses within Board-approved bands, approves the succession plan, oversees the annual board evaluation process, and re-confirms director independence on a yearly basis.',
 N'تعتمد مصفوفة المهارات، توصي بمرشحي العضوية للجمعية العامة، تعتمد سياسة المكافآت، تحدد المكافآت التنفيذية ضمن نطاقات معتمدة من المجلس، تعتمد خطة الخلافة، تشرف على عملية التقييم السنوي للمجلس، وتعيد تأكيد استقلالية الأعضاء سنوياً.',

 N'3-4 non-executive directors. CMA Article 22 requires the majority to be independent and the chair to be independent. The CEO attends remuneration discussions for executive team but is recused from discussions about CEO compensation.',
 N'3-4 أعضاء غير تنفيذيين. تشترط المادة 22 من لائحة هيئة السوق المالية أن تكون الأغلبية مستقلة وأن يكون الرئيس مستقلاً. يحضر الرئيس التنفيذي مناقشات مكافآت الفريق التنفيذي ولكن يُستثنى من النقاشات المتعلقة بمكافأته.',

 50, N'SEMI_ANNUAL', 3,
 N'CTPL-BOARD',
 N'Material changes to the remuneration policy, all CEO-level compensation decisions, board-composition recommendations, and material gaps in the skills matrix escalate to the Board and (where required) to the General Assembly.',
 N'تُصعّد إلى المجلس وإلى الجمعية العامة عند الاقتضاء: التعديلات الجوهرية على سياسة المكافآت، جميع قرارات مكافأة الرئيس التنفيذي، توصيات تشكيل المجلس، والفجوات الجوهرية في مصفوفة المهارات.',

 N'CMA Corporate Governance Regulations Articles 22-23 mandate this committee, its composition, and its responsibilities. The annual Corporate Governance Report must disclose the remuneration policy and the work of the committee.',
 N'المادتان 22-23 من لائحة حوكمة الشركات الصادرة عن هيئة السوق المالية تفرضان هذه اللجنة، تشكيلها، ومسؤولياتها. يجب الإفصاح عن سياسة المكافآت وعمل اللجنة في التقرير السنوي لحوكمة الشركات.',
 1, 0,
 N'Annual Remuneration Report to CMA (disclosed in Annual Corporate Governance Report). Director nominations published with General Assembly notice. Material executive-compensation changes filed under Tadawul disclosure rules.'),

-- ─── 5. Compliance Committee ───────────────────────────────────────────
(N'CTPL-COMPLIANCE', N'COMPLIANCE_COMMITTEE', N'GENERAL', N'SAMA,AML-Law,CMA,PDPL',
 N'Compliance Committee', N'لجنة الالتزام',
 N'Owns the compliance programme. Monthly review of regulatory-change tracker, AML monitoring, conduct breaches, PEP/sanctions alerts, regulator correspondence, and training completion. Provides assurance to the Board that the company''s operations comply with applicable laws and regulations.',
 N'تملك برنامج الالتزام. تراجع شهرياً متتبع التغييرات التنظيمية، رقابة مكافحة غسل الأموال، مخالفات السلوك، تنبيهات الأشخاص المعرضين سياسياً والعقوبات، المراسلات التنظيمية، وإكمال التدريب. توفر تأكيداً للمجلس بأن عمليات الشركة تمتثل للقوانين واللوائح المعمول بها.',

 N'Approves the annual compliance plan, the AML risk-rating model, the regulator-engagement strategy, and material updates to KYC/CDD procedures. Reviews sustained whistle-blower findings and escalates them to the Audit Committee where they touch financial controls.',
 N'تعتمد خطة الالتزام السنوية، نموذج تصنيف مخاطر مكافحة غسل الأموال، استراتيجية التواصل مع الجهات التنظيمية، والتعديلات الجوهرية على إجراءات اعرف عميلك / العناية الواجبة. تراجع نتائج بلاغات المخالفات المؤيدة وتصعدها للجنة المراجعة عند تأثيرها على الضوابط المالية.',

 N'Chair: Chief Compliance Officer (CCO) or MLRO. Members: General Counsel, CRO, CISO, COO, Head of HR. The DPO attends for any PDPL-related items.',
 N'الرئيس: رئيس الالتزام (CCO) أو مسؤول الإبلاغ عن غسل الأموال. الأعضاء: المستشار العام، مدير المخاطر، مدير أمن المعلومات، مدير العمليات، رئيس الموارد البشرية. يحضر مسؤول حماية البيانات للبنود المتعلقة بنظام حماية البيانات الشخصية.',

 67, N'MONTHLY', NULL,
 N'CTPL-AUDIT',
 N'Material AML / sanctions cases, sustained whistle-blower findings touching financial controls, regulator examinations, and any matter that could trigger a regulatory enforcement action escalate to the Audit Committee.',
 N'تُصعّد إلى لجنة المراجعة: قضايا مكافحة غسل الأموال / العقوبات الجوهرية، نتائج بلاغات المخالفات المؤيدة المتعلقة بالضوابط المالية، الفحوصات التنظيمية، وأي أمر يمكن أن يؤدي إلى إجراء تنفيذي تنظيمي.',

 N'SAMA Compliance Manual + Anti-Money-Laundering Law Article 41 (mandates a senior compliance officer with direct access to senior management and the Board). For listed entities, CMA Corporate Governance Article 25 recommends a compliance committee.',
 N'دليل الالتزام الصادر عن ساما + المادة 41 من نظام مكافحة غسل الأموال (تفرض مسؤول التزام أول له صلاحية الوصول المباشر للإدارة العليا والمجلس). للكيانات المدرجة، توصي المادة 25 من لائحة هيئة السوق المالية بلجنة التزام.',
 0, 0,
 N'Annual compliance report to Board. STR (Suspicious Transaction Report) submissions to SAFIU monthly (banks). Material regulator correspondence summarised quarterly.'),

-- ─── 6. Cyber Security Committee ───────────────────────────────────────
(N'CTPL-CYBER', N'CYBER_SECURITY', N'GENERAL', N'NCA,SAMA-CSF,Saudi-CERT',
 N'Cyber Security Committee', N'لجنة الأمن السيبراني',
 N'NCA Essential Cyber-Security Controls (ECC-2) control 1-2-1 mandates the existence of this committee. Owns cyber-risk posture, the security operations programme, the incident-response readiness, and oversight of the CISO. Receives the SOC dashboard, threat-intelligence briefings, vulnerability programme status, and pentest results.',
 N'تشترط الضوابط الأساسية للأمن السيبراني (ECC-2) الصادرة عن الهيئة الوطنية للأمن السيبراني (الضابط 1-2-1) وجود هذه اللجنة. تملك وضع المخاطر السيبرانية، برنامج عمليات الأمن، جاهزية الاستجابة للحوادث، والإشراف على مدير أمن المعلومات. تتلقى لوحة مركز عمليات الأمن، إحاطات الاستخبارات، حالة برنامج الثغرات، ونتائج اختبارات الاختراق.',

 N'Approves the cyber-security strategy, the annual security budget, major cyber investments, the incident-response plan and playbooks, the cyber-insurance policy renewal, and material cyber risk acceptances. Calls cyber-crisis activations alongside the Crisis Management Committee.',
 N'تعتمد الاستراتيجية السيبرانية، الميزانية الأمنية السنوية، الاستثمارات السيبرانية الكبرى، خطة الاستجابة للحوادث وأدلتها، تجديد بوليصة التأمين السيبراني، وقبول المخاطر السيبرانية الجوهرية. تستدعي تفعيلات الأزمات السيبرانية بالتنسيق مع لجنة إدارة الأزمات.',

 N'Chair: CISO. Voting members: CIO, CRO, COO, General Counsel, Head of Internal Audit (for second-line independence preserved at audit). Standing invitees: DPO, Head of Internal Communications (crisis-comms readiness), business-line heads. External CISA / CISSP advisor optional.',
 N'الرئيس: مدير أمن المعلومات. أعضاء بحق التصويت: مدير تقنية المعلومات، مدير المخاطر، مدير العمليات، المستشار العام، رئيس المراجعة الداخلية (للحفاظ على استقلالية الخط الثاني). مدعوون دائمون: مسؤول حماية البيانات، رئيس الاتصال الداخلي (جاهزية اتصال الأزمات)، رؤساء خطوط الأعمال. مستشار خارجي CISA / CISSP اختياري.',

 67, N'MONTHLY', NULL,
 N'CTPL-RISK',
 N'P1/P2 cyber incidents, NCA / Saudi-CERT notifications, material control-failures in NCA ECC posture, cyber-insurance claims, and any cyber-risk acceptance above appetite escalate to the Risk Committee and (depending on severity) directly to the Board.',
 N'تُصعّد إلى لجنة المخاطر (وعند الحاجة مباشرة إلى المجلس): الحوادث السيبرانية من الدرجة P1/P2، إخطارات الهيئة الوطنية للأمن السيبراني / المركز السعودي للاستجابة لطوارئ الحاسب، فشل الضوابط الجوهرية في امتثال NCA ECC، مطالبات التأمين السيبراني، وأي قبول مخاطر سيبراني فوق الشهية.',

 N'NCA Essential Cyber-Security Controls (ECC-2) control 1-2-1: "Cyber-Security Committee shall be established to oversee cyber-security objectives, programmes, plans and strategies." SAMA Cyber-Security Framework requires the same for regulated entities.',
 N'الضوابط الأساسية للأمن السيبراني (ECC-2) الصادرة عن الهيئة الوطنية للأمن السيبراني (الضابط 1-2-1): "تُنشأ لجنة الأمن السيبراني للإشراف على أهداف وبرامج وخطط واستراتيجيات الأمن السيبراني." يفرض إطار الأمن السيبراني الصادر عن ساما المتطلب نفسه على الكيانات المنظمة.',
 0, 0,
 N'Material cyber incidents reported to Saudi-CERT (under SAMA/NCA timelines). Annual NCA ECC self-assessment + biennial third-party assessment. SAMA cyber-attestation evidence to Audit Committee.'),

-- ─── 7. Data Governance Committee ──────────────────────────────────────
(N'CTPL-DATAGOV', N'DATA_GOVERNANCE', N'GENERAL', N'NDMO,SDAIA,PDPL',
 N'Data Governance Committee', N'لجنة حوكمة البيانات',
 N'Owns the data-governance framework aligned to the NDMO Data Governance Framework and DAMA-DMBOK. Sets data-quality KPIs, approves the data-classification scheme (NDC alignment), oversees master and reference data, sponsors data-stewardship roles, and coordinates the NDMO assessment.',
 N'تملك إطار حوكمة البيانات المتوافق مع إطار حوكمة البيانات الصادر عن مكتب إدارة البيانات الوطني وDAMA-DMBOK. تحدد مؤشرات جودة البيانات، تعتمد مخطط تصنيف البيانات (متوافق مع NDC)، تشرف على البيانات الرئيسية والمرجعية، ترعى أدوار الإشراف على البيانات، وتنسق تقييم مكتب إدارة البيانات الوطني.',

 N'Approves the data-domain stewardship map, data-quality SLAs, master-data change-control, the data-classification policy, and cross-border data-transfer requests jointly with the Privacy Committee. Approves the NDMO assessment submission package.',
 N'تعتمد خريطة الإشراف على نطاقات البيانات، اتفاقيات مستوى خدمة جودة البيانات، التحكم في تغيير البيانات الرئيسية، سياسة تصنيف البيانات، وطلبات نقل البيانات عبر الحدود بالتنسيق مع لجنة الخصوصية. تعتمد حزمة التقديم لتقييم مكتب إدارة البيانات الوطني.',

 N'Chair: Chief Data Officer (CDO). Members: domain data owners (Finance, HR, Customer, Operations), CISO, DPO, CIO. Optional external SDAIA-certified data advisor.',
 N'الرئيس: مدير البيانات التنفيذي (CDO). الأعضاء: مالكو نطاقات البيانات (المالية، الموارد البشرية، العملاء، العمليات)، مدير أمن المعلومات، مسؤول حماية البيانات، مدير تقنية المعلومات. مستشار بيانات خارجي معتمد من SDAIA (اختياري).',

 67, N'MONTHLY', NULL,
 N'CTPL-RISK',
 N'Data-quality breaches affecting financial reporting, material classification disputes, cross-border transfer requests requiring PDPL Article 29 SDAIA approval, and any NDMO assessment finding escalate to the Risk Committee.',
 N'تُصعّد إلى لجنة المخاطر: انتهاكات جودة البيانات التي تؤثر على التقارير المالية، النزاعات الجوهرية حول التصنيف، طلبات النقل عبر الحدود التي تتطلب موافقة SDAIA بموجب المادة 29 من نظام حماية البيانات الشخصية، وأي ملاحظة من تقييم مكتب إدارة البيانات الوطني.',

 N'NDMO Data Governance Framework (Saudi National Data Management Office) + SDAIA Data Management & Personal Data Protection Standards.',
 N'إطار حوكمة البيانات الصادر عن مكتب إدارة البيانات الوطني + معايير إدارة البيانات وحماية البيانات الشخصية الصادرة عن الهيئة السعودية للبيانات والذكاء الاصطناعي.',
 0, 0,
 N'Annual NDMO assessment submission. Material classification or quality issues quarterly to Risk Committee.'),

-- ─── 8. Privacy / DPO Committee ────────────────────────────────────────
(N'CTPL-PRIVACY', N'PRIVACY_DPO', N'GENERAL', N'PDPL,SDAIA',
 N'Data Protection / Privacy Committee', N'لجنة حماية البيانات الشخصية',
 N'Owns PDPL compliance. Monthly review of DSR (data-subject request) pipeline, cross-border transfer approvals, DPIA outcomes, consent metrics, RoPA (Record of Processing Activities) updates, privacy-breach summaries, and vendor-processor reviews.',
 N'تملك الالتزام بنظام حماية البيانات الشخصية. تراجع شهرياً خط أنابيب طلبات أصحاب البيانات، اعتمادات النقل عبر الحدود، نتائج تقييمات أثر حماية البيانات، مؤشرات الموافقة، تحديثات سجل أنشطة المعالجة، ملخصات خروقات الخصوصية، ومراجعات معالجي البيانات الخارجيين.',

 N'Approves the privacy notice, DPIA outcomes for high-risk processing, cross-border transfer mechanisms (BCR, contractual clauses, or SDAIA approval), and the annual privacy training programme. Recommends notification of data breaches to SDAIA within statutory windows.',
 N'تعتمد إشعار الخصوصية، نتائج تقييم أثر حماية البيانات للمعالجة عالية الخطورة، آليات النقل عبر الحدود (القواعد الملزمة، الشروط التعاقدية، أو موافقة SDAIA)، وبرنامج التدريب السنوي للخصوصية. توصي بإخطار SDAIA بخروقات البيانات ضمن النوافذ القانونية.',

 N'Chair: Data Protection Officer (DPO). Members: General Counsel, CISO, Head of HR, Head of Marketing, Head of Customer Experience. Standing invitee: CDO (data-governance coordination).',
 N'الرئيس: مسؤول حماية البيانات. الأعضاء: المستشار العام، مدير أمن المعلومات، رئيس الموارد البشرية، رئيس التسويق، رئيس تجربة العميل. مدعو دائم: مدير البيانات (تنسيق حوكمة البيانات).',

 50, N'MONTHLY', NULL,
 N'CTPL-RISK',
 N'Material privacy breaches requiring SDAIA notification, sustained data-subject complaints, cross-border requests requiring Article 29 SDAIA approval, and breaches above the materiality threshold escalate to the Risk Committee and (if reputational) to the Board.',
 N'تُصعّد إلى لجنة المخاطر (وإلى المجلس إذا كان الأثر السمعي جوهرياً): خروقات الخصوصية الجوهرية التي تتطلب إخطار SDAIA، شكاوى أصحاب البيانات المؤيدة، طلبات النقل عبر الحدود التي تتطلب موافقة SDAIA بموجب المادة 29، والخروقات فوق عتبة الجوهرية.',

 N'Personal Data Protection Law (PDPL) Article 30 requires the appointment of a DPO for controllers processing personal data above scale/sensitivity thresholds. PDPL Implementing Regulations set the DPO''s mandate, independence, and reporting line.',
 N'تتطلب المادة 30 من نظام حماية البيانات الشخصية تعيين مسؤول حماية البيانات للمتحكمين الذين يعالجون بيانات شخصية فوق عتبات الحجم/الحساسية. تحدد اللائحة التنفيذية ولاية المسؤول واستقلاليته وتسلسل تقاريره.',
 0, 0,
 N'Annual privacy notice + RoPA refresh. Material breach notifications to SDAIA within PDPL-statutory windows. Annual privacy compliance report to Board.'),

-- ─── 9. BCM Steering Committee ─────────────────────────────────────────
(N'CTPL-BCM', N'BCM_COMMITTEE', N'GENERAL', N'ISO22301,SAMA-BCM,NCEMA',
 N'BCM Steering Committee', N'لجنة توجيه استمرارية الأعمال',
 N'Sponsors the business-continuity-management programme aligned to ISO 22301 and SAMA BCM Framework. Quarterly review of BIA outputs, BCP / DRP test results, exercise outcomes, and supplier-resilience posture. Acts as the executive sponsor of the BCM officer.',
 N'ترعى برنامج إدارة استمرارية الأعمال المتوافق مع ISO 22301 وإطار استمرارية الأعمال الصادر عن ساما. تراجع ربع سنوياً مخرجات تحليل أثر الأعمال، نتائج اختبارات BCP/DRP، نتائج التمارين، ووضع صمود الموردين. هي الراعي التنفيذي لمسؤول استمرارية الأعمال.',

 N'Approves the BCM policy and framework, BIAs for critical processes, RTO / RPO assumptions, the annual exercise calendar, DRP test scenarios + results, and crisis-activation criteria. Calls crisis activations alongside the Crisis Management Committee.',
 N'تعتمد سياسة وإطار استمرارية الأعمال، تحليلات أثر الأعمال للعمليات الحرجة، افتراضات RTO/RPO، تقويم التمارين السنوي، سيناريوهات اختبار DRP ونتائجها، ومعايير تفعيل الأزمات. تستدعي تفعيلات الأزمات بالتنسيق مع لجنة إدارة الأزمات.',

 N'Chair: COO. Voting members: CIO, CISO, CFO, Facilities Director. Secretary: BCM Officer. Standing invitees: business-line heads, key supplier liaisons.',
 N'الرئيس: مدير العمليات. أعضاء بحق التصويت: مدير تقنية المعلومات، مدير أمن المعلومات، المدير المالي، مدير المرافق. الأمين: مسؤول استمرارية الأعمال. مدعوون دائمون: رؤساء خطوط الأعمال، ضباط ارتباط الموردين الرئيسيين.',

 67, N'QUARTERLY', NULL,
 N'CTPL-RISK',
 N'DRP test failures, RTO / RPO breaches on critical processes, supplier insolvency or service-degradation above appetite, and crisis activations themselves all escalate to the Risk Committee and (depending on impact) to the Board.',
 N'تُصعّد إلى لجنة المخاطر (وعند الحاجة إلى المجلس): فشل اختبارات DRP، تجاوزات RTO/RPO على العمليات الحرجة، إفلاس المورد أو تدهور الخدمة فوق الشهية، وتفعيلات الأزمات نفسها.',

 N'ISO 22301:2019 Business Continuity Management Systems. SAMA BCM Framework. National Emergency Management Authority (NCEMA) Saudi business-continuity guidance.',
 N'ISO 22301:2019 أنظمة إدارة استمرارية الأعمال. إطار استمرارية الأعمال الصادر عن ساما. إرشادات استمرارية الأعمال السعودية الصادرة عن المركز الوطني لإدارة الطوارئ.',
 0, 0,
 N'Annual BCM programme report to Board. Material DRP test failures to Risk Committee within 30 days. Crisis activations notified to regulators per sector-specific rules.'),

-- ─── 10. Crisis Management Committee ───────────────────────────────────
(N'CTPL-CRISIS', N'CRISIS_MANAGEMENT', N'GENERAL', N'SAMA,NCA,NCEMA',
 N'Crisis Management Committee', N'لجنة إدارة الأزمات',
 N'Standing committee that activates on declaration of a crisis (cyber, BCM, regulatory, reputational, safety, or geopolitical). Owns the crisis playbooks, the regulator-notification timetable, the external-communications strategy, and the After-Action Review process. Convenes ad-hoc during a crisis; meets quarterly during normal operations for readiness review.',
 N'لجنة دائمة تُفعّل عند إعلان أزمة (سيبرانية، استمرارية أعمال، تنظيمية، سمعية، سلامة، أو جيوسياسية). تملك أدلة الأزمات، جدول إخطار الجهات التنظيمية، استراتيجية الاتصال الخارجي، وعملية المراجعة بعد الحدث. تجتمع بشكل طارئ أثناء الأزمة؛ تجتمع ربع سنوياً في الأوقات العادية لمراجعة الجاهزية.',

 N'During activation: takes operational command, approves regulator notifications, approves external statements, mobilises external advisors (PR, legal, technical specialists), authorises emergency spend, declares crisis closure. During readiness review: approves playbook updates, exercise schedules, lessons-learned actions.',
 N'أثناء التفعيل: تتولى القيادة التشغيلية، تعتمد إخطارات الجهات التنظيمية، تعتمد البيانات الخارجية، تحشد المستشارين الخارجيين (علاقات عامة، قانوني، خبراء تقنيين)، تخوّل الإنفاق الطارئ، تعلن إغلاق الأزمة. أثناء مراجعة الجاهزية: تعتمد تحديثات الأدلة، جداول التمارين، إجراءات الدروس المستفادة.',

 N'Chair: CEO (or COO when CEO is unavailable). Members: full C-suite + Head of Internal Communications + Head of Legal. Standing invitees during activation: external counsel, external PR firm. Special-skills invitees per crisis type (e.g. forensics firm for cyber, sector expert for BCM).',
 N'الرئيس: الرئيس التنفيذي (أو مدير العمليات في حال عدم توفر الرئيس التنفيذي). الأعضاء: كامل فريق المسؤولين التنفيذيين + رئيس الاتصال الداخلي + رئيس الشؤون القانونية. مدعوون دائمون أثناء التفعيل: مستشار خارجي، شركة علاقات عامة خارجية. مدعوون متخصصون حسب نوع الأزمة (مثل شركة الطب الشرعي للأزمات السيبرانية، خبير قطاعي لاستمرارية الأعمال).',

 0, N'AD_HOC', NULL,    -- no quorum during activation; quarterly readiness has 50%
 N'CTPL-BOARD',
 N'Crisis declarations are reported to the Board immediately. Regulator notifications follow sector-specific timetables (Saudi-CERT for cyber, SAMA for banking incidents above thresholds, CMA for material non-public information, SDAIA for personal-data breaches).',
 N'تُبلَّغ إعلانات الأزمات للمجلس فوراً. تتبع إخطارات الجهات التنظيمية جداول قطاعية محددة (المركز السعودي للاستجابة للحوادث السيبرانية، ساما للحوادث المصرفية فوق الحدود، هيئة السوق المالية للمعلومات الجوهرية غير المعلنة، SDAIA لخروقات البيانات الشخصية).',

 N'NCEMA (National Center for Emergency Management) Saudi crisis-management guidance. SAMA Crisis Management Framework. NCA Saudi-CERT crisis-notification timelines.',
 N'إرشادات المركز الوطني لإدارة الطوارئ السعودي. إطار إدارة الأزمات الصادر عن ساما. جداول الإخطار بالأزمات الصادرة عن المركز السعودي للاستجابة لطوارئ الحاسب (الهيئة الوطنية للأمن السيبراني).',
 0, 0,
 N'Immediate Board notification on activation. Regulator notifications per sector timelines. Public disclosure per CMA disclosure rules. Annual After-Action Review summary to Board.'),

-- ─── 11. AML Committee ─────────────────────────────────────────────────
(N'CTPL-AML', N'AML', N'BANKING', N'SAMA-AML,AML-Law,FATF',
 N'Anti-Money-Laundering Committee', N'لجنة مكافحة غسل الأموال',
 N'Owns the AML / CTF programme. Monthly review of STR pipeline + SAFIU submissions, sanctions-screening hits, PEP reviews, KYC remediation queues, customer risk re-rating, training compliance, and FATF / SAMA typology updates. Reports to the Compliance Committee or directly to the Audit Committee in regulated entities.',
 N'تملك برنامج مكافحة غسل الأموال وتمويل الإرهاب. تراجع شهرياً خط أنابيب بلاغات الاشتباه وتقديمات SAFIU، نتائج فحص العقوبات، مراجعات الأشخاص المعرضين سياسياً، طوابير معالجة اعرف عميلك، إعادة تصنيف مخاطر العملاء، الالتزام بالتدريب، وتحديثات FATF/ساما للأنماط.',

 N'Approves the AML risk-rating model, the customer-acceptance policy, the sanctions-screening solution and lists in use, material PEP / sanctions case dispositions, KYC remediation plans, and the annual AML training programme.',
 N'تعتمد نموذج تصنيف مخاطر غسل الأموال، سياسة قبول العملاء، حل فحص العقوبات والقوائم المستخدمة، الفصل في الحالات الجوهرية للأشخاص المعرضين سياسياً / العقوبات، خطط معالجة اعرف عميلك، وبرنامج التدريب السنوي.',

 N'Chair: MLRO (Money-Laundering Reporting Officer). Members: Chief Compliance Officer, Head of Operations, Head of IT, Head of HR, Internal Audit (non-voting observer).',
 N'الرئيس: مسؤول الإبلاغ عن غسل الأموال. الأعضاء: رئيس الالتزام، رئيس العمليات، رئيس تقنية المعلومات، رئيس الموارد البشرية، المراجعة الداخلية (مراقب دون تصويت).',

 67, N'MONTHLY', NULL,
 N'CTPL-COMPLIANCE',
 N'Material STR cases (e.g. politically-exposed persons with adverse media), sanctions-screening true-positives, sustained KYC backlogs, and any regulator investigation escalate to the Compliance Committee and from there to the Audit Committee / Board as the matter warrants.',
 N'تُصعّد إلى لجنة الالتزام (ومنها إلى لجنة المراجعة / المجلس حسب الحالة): قضايا الاشتباه الجوهرية (مثل الأشخاص المعرضين سياسياً مع إعلام سلبي)، نتائج فحص العقوبات الإيجابية الحقيقية، تراكمات اعرف عميلك المؤيدة، وأي تحقيق تنظيمي.',

 N'Anti-Money-Laundering Law (Royal Decree M/20) Article 41 mandates an AML officer with direct access to senior management. SAMA AML Manual prescribes the AML-committee composition for banks. FATF Recommendations 10-12 + 22 cover CDD and PEP screening.',
 N'يفرض نظام مكافحة غسل الأموال (المرسوم الملكي م/20) في المادة 41 مسؤول مكافحة غسل أموال له صلاحية الوصول المباشر للإدارة العليا. يحدد دليل ساما لمكافحة غسل الأموال تشكيل اللجنة للبنوك. توصيات FATF 10-12 و 22 تغطي العناية الواجبة وفحص الأشخاص المعرضين سياسياً.',
 0, 0,
 N'Monthly STR submissions to SAFIU (Saudi FIU). Annual AML report to Board + SAMA (banks). Material sanctions hits notified to SAMA within statutory windows.'),

-- ─── 12. IT Steering Committee ─────────────────────────────────────────
(N'CTPL-IT', N'IT_STEERING', N'GENERAL', N'NCA,COBIT',
 N'IT Steering Committee', N'لجنة توجيه تقنية المعلومات',
 N'Owns the IT strategy, the IT-investment portfolio, and the technology roadmap. Monthly review of major-project status, IT spend, major incidents, change-management major cases, cloud / SaaS portfolio, and tech-debt remediation. Reports to the Executive Committee or directly to the Board.',
 N'تملك استراتيجية تقنية المعلومات، حافظة استثمارات تقنية المعلومات، وخريطة طريق التقنية. تراجع شهرياً حالة المشاريع الكبرى، إنفاق تقنية المعلومات، الحوادث الكبرى، الحالات الكبرى لإدارة التغيير، حافظة السحابة / SaaS، ومعالجة الدين التقني.',

 N'Approves IT investments above the delegated CIO authority, major changes affecting more than one business line, cloud / SaaS commercial commitments above threshold, the annual IT plan, and material tech-debt write-offs.',
 N'تعتمد استثمارات تقنية المعلومات فوق صلاحية مدير تقنية المعلومات المفوّضة، التغييرات الكبرى التي تؤثر على أكثر من خط عمل، التزامات السحابة / SaaS التجارية فوق الحد، خطة تقنية المعلومات السنوية، وشطب الدين التقني الجوهري.',

 N'Chair: CIO. Members: CTO, CISO, CFO (for budget oversight), business-line heads, Head of PMO.',
 N'الرئيس: مدير تقنية المعلومات. الأعضاء: مدير التقنية، مدير أمن المعلومات، المدير المالي (للإشراف على الميزانية)، رؤساء خطوط الأعمال، رئيس مكتب إدارة المشاريع.',

 67, N'MONTHLY', NULL,
 N'CTPL-BOARD',
 N'P1 incidents, material project-budget overruns, cloud-commercial commitments above Board threshold, and any strategic tech decision affecting risk appetite escalate to the Board.',
 N'تُصعّد إلى المجلس: حوادث P1، تجاوزات ميزانية المشاريع الجوهرية، التزامات السحابة التجارية فوق حد المجلس، وأي قرار تقني استراتيجي يؤثر على شهية المخاطر.',

 N'NCA Essential Cyber-Security Controls (ECC-2) reference IT governance. COBIT 2019 frames the IT-governance practices that this committee typically implements.',
 N'الضوابط الأساسية للأمن السيبراني (ECC-2) الصادرة عن الهيئة الوطنية للأمن السيبراني تشير إلى حوكمة تقنية المعلومات. يحدد COBIT 2019 ممارسات حوكمة تقنية المعلومات التي تطبقها هذه اللجنة عادة.',
 0, 0,
 N'Quarterly IT-portfolio report to Board. Material project status to Audit Committee when project crosses materiality threshold.'),

-- ─── 13. ALCO (Asset & Liability Committee — banking) ──────────────────
(N'CTPL-ALCO', N'ALCO', N'BANKING', N'SAMA-Banking,Basel-III',
 N'Asset & Liability Committee (ALCO)', N'لجنة الأصول والخصوم',
 N'Banking-sector standing committee. Owns balance-sheet management, interest-rate-risk-in-the-banking-book (IRR-BB), liquidity, capital ratios, FX exposure, and concentration risk. Monthly review of LCR, NSFR, NIM, deposit / loan growth, and stress-test outcomes.',
 N'لجنة دائمة في القطاع المصرفي. تملك إدارة الميزانية العمومية، مخاطر معدل الفائدة في دفتر البنك، السيولة، نسب رأس المال، التعرض لسعر الصرف، ومخاطر التركز. تراجع شهرياً LCR وNSFR وهامش الفائدة وأرصدة الودائع والقروض ونتائج اختبارات الضغط.',

 N'Approves balance-sheet limits (e.g. loan-to-deposit ratio bands), the FTP (funds-transfer pricing) framework, hedging strategy, contingency-funding plan triggers, and stress-test scenarios within Board-approved appetite.',
 N'تعتمد حدود الميزانية العمومية (مثل نطاقات نسبة القروض إلى الودائع)، إطار تسعير نقل الأموال، استراتيجية التحوط، محفزات خطة التمويل الطارئ، وسيناريوهات اختبار الضغط ضمن الشهية المعتمدة من المجلس.',

 N'Chair: CFO. Members: Treasurer, CRO, Head of Markets, Head of Lending, Head of Retail / Wholesale (as relevant).',
 N'الرئيس: المدير المالي. الأعضاء: أمين الخزينة، مدير المخاطر، رئيس الأسواق، رئيس الإقراض، رئيس البنوك الأفراد / الجملة (حسب الحاجة).',

 67, N'MONTHLY', NULL,
 N'CTPL-RISK',
 N'LCR / NSFR breaches, capital-ratio movement above amber, FX or concentration-limit breaches, and stress-test outcomes triggering capital action escalate to the Risk Committee.',
 N'تُصعّد إلى لجنة المخاطر: انتهاكات LCR/NSFR، تحرك نسبة رأس المال فوق الكهرماني، انتهاكات حدود الصرف أو التركز، ونتائج اختبارات الضغط التي تحفز إجراء رأس المال.',

 N'SAMA banking guidelines on balance-sheet management. Basel III liquidity (LCR, NSFR) and IRR-BB requirements. ICAAP / ILAAP filings to SAMA depend on ALCO oversight.',
 N'إرشادات ساما المصرفية لإدارة الميزانية العمومية. متطلبات السيولة Basel III (LCR وNSFR) ومخاطر معدل الفائدة في دفتر البنك. تقديمات ICAAP/ILAAP إلى ساما تعتمد على إشراف ALCO.',
 0, 0,
 N'Monthly liquidity returns to SAMA. ICAAP / ILAAP annually. Material LCR / NSFR breaches notified to SAMA same-day.'),

-- ─── 14. Investment Committee ──────────────────────────────────────────
(N'CTPL-INVEST', N'INVESTMENT', N'GENERAL', N'CMA,Sector-Specific',
 N'Investment Committee', N'لجنة الاستثمار',
 N'Owns the investment portfolio (treasury, sector-specific). Monthly review of portfolio performance vs benchmark, new mandate approvals, asset-allocation drift, manager performance, concentration limits, and stewardship / proxy voting.',
 N'تملك حافظة الاستثمار (الخزينة، قطاعية). تراجع شهرياً أداء الحافظة مقابل المعيار، اعتمادات التفويض الجديدة، انحراف توزيع الأصول، أداء المديرين، حدود التركز، والإشراف / التصويت بالوكالة.',

 N'Approves new investment mandates, allocation shifts above threshold, manager hires / fires, concentration exceptions, voting policy. Ad-hoc convenes for material market events.',
 N'تعتمد التفويضات الاستثمارية الجديدة، تحويلات التوزيع فوق العتبة، تعيين / إنهاء المديرين، استثناءات التركز، سياسة التصويت. تُعقد بشكل طارئ للأحداث السوقية الجوهرية.',

 N'Chair: CIO-Investment (or equivalent). Members: CFO, CRO, Treasurer, independent investment expert (where applicable per sector rules).',
 N'الرئيس: مدير الاستثمار التنفيذي (أو ما يعادله). الأعضاء: المدير المالي، مدير المخاطر، أمين الخزينة، خبير استثمار مستقل (حيثما تنطبق القواعد القطاعية).',

 67, N'MONTHLY', NULL,
 N'CTPL-BOARD',
 N'Mandates exceeding Board-approved limits, manager terminations, concentration-limit breaches above appetite, and material portfolio underperformance escalate to the Board.',
 N'تُصعّد إلى المجلس: التفويضات التي تتجاوز الحدود المعتمدة من المجلس، إنهاء المديرين، انتهاكات حدود التركز فوق الشهية، وأداء الحافظة الجوهري الأقل من المتوقع.',

 N'CMA fund / asset-management regulations. Sector-specific guidance for insurers and pension funds.',
 N'لوائح إدارة الصناديق / الأصول الصادرة عن هيئة السوق المالية. إرشادات قطاعية لشركات التأمين وصناديق التقاعد.',
 0, 0,
 N'Sector-specific portfolio reporting to CMA / SAMA. Annual stewardship report.'),

-- ─── 15. AI Ethics Committee ───────────────────────────────────────────
(N'CTPL-AIETHICS', N'AI_ETHICS', N'GENERAL', N'SDAIA-AI-Ethics',
 N'AI Ethics Committee', N'لجنة أخلاقيات الذكاء الاصطناعي',
 N'Owns the AI / ML governance programme aligned to SDAIA AI Ethics Principles. Quarterly review of AI use-case pipeline, model risk inventory, bias / fairness reviews, explainability assessments, data lineage for training, and vendor AI procurements.',
 N'تملك برنامج حوكمة الذكاء الاصطناعي والتعلم الآلي المتوافق مع مبادئ أخلاقيات الذكاء الاصطناعي الصادرة عن SDAIA. تراجع ربع سنوياً خط أنابيب حالات الاستخدام، جرد مخاطر النماذج، مراجعات التحيز / العدالة، تقييمات قابلية التفسير، نسب البيانات للتدريب، ومشتريات الذكاء الاصطناعي من الموردين.',

 N'Approves new AI use-cases above the materiality threshold, the model-validation framework, fairness / bias remediation plans, AI-vendor procurement, and the SDAIA assessment submission.',
 N'تعتمد حالات استخدام الذكاء الاصطناعي الجديدة فوق عتبة الجوهرية، إطار التحقق من النماذج، خطط معالجة العدالة / التحيز، مشتريات موردي الذكاء الاصطناعي، وتقديم تقييم SDAIA.',

 N'Chair: Chief AI Officer / Head of Analytics. Members: CIO, CRO, CISO, DPO, General Counsel, external AI-ethics expert. Optional: business sponsor for the use-case under review.',
 N'الرئيس: رئيس الذكاء الاصطناعي التنفيذي / رئيس التحليلات. الأعضاء: مدير تقنية المعلومات، مدير المخاطر، مدير أمن المعلومات، مسؤول حماية البيانات، المستشار العام، خبير خارجي في أخلاقيات الذكاء الاصطناعي. اختياري: راعي الأعمال لحالة الاستخدام قيد المراجعة.',

 67, N'QUARTERLY', NULL,
 N'CTPL-RISK',
 N'High-risk AI use-cases (decisions affecting individuals materially), failures in bias / fairness gates, regulator engagement on AI matters, and any model failure with customer impact escalate to the Risk Committee.',
 N'تُصعّد إلى لجنة المخاطر: حالات استخدام الذكاء الاصطناعي عالية الخطورة (القرارات التي تؤثر جوهرياً على الأفراد)، فشل بوابات العدالة / التحيز، التواصل التنظيمي بشأن قضايا الذكاء الاصطناعي، وأي فشل نموذج له أثر على العميل.',

 N'SDAIA AI Ethics Principles (Saudi Data & AI Authority). Sector-specific guidance for high-risk applications (e.g. healthcare, financial services).',
 N'مبادئ أخلاقيات الذكاء الاصطناعي الصادرة عن الهيئة السعودية للبيانات والذكاء الاصطناعي. إرشادات قطاعية للتطبيقات عالية الخطورة (مثل الرعاية الصحية، الخدمات المالية).',
 0, 0,
 N'Annual SDAIA assessment. Material AI incident notifications per sector rules.'),

-- ─── 16. Anti-Fraud Committee ──────────────────────────────────────────
(N'CTPL-ANTIFRAUD', N'ANTI_FRAUD', N'GENERAL', N'SAMA,ACFE,Saudi-Criminal-Code',
 N'Anti-Fraud Committee', N'لجنة مكافحة الاحتيال',
 N'Owns the anti-fraud programme. Monthly review of active fraud cases (internal + external), loss summary, trends, whistle-blower cases touching fraud, sanctions, recoveries, training compliance, and process-gap remediation.',
 N'تملك برنامج مكافحة الاحتيال. تراجع شهرياً حالات الاحتيال النشطة (داخلية + خارجية)، ملخص الخسائر، الاتجاهات، بلاغات المخالفات المتعلقة بالاحتيال، العقوبات، التعافي، الالتزام بالتدريب، ومعالجة فجوات العمليات.',

 N'Approves fraud-case dispositions, recovery actions, sanctions on staff (with HR), changes to fraud-detection rules, the annual anti-fraud training programme.',
 N'تعتمد الفصل في حالات الاحتيال، إجراءات التعافي، العقوبات على الموظفين (بالتنسيق مع الموارد البشرية)، تغييرات قواعد كشف الاحتيال، وبرنامج التدريب السنوي لمكافحة الاحتيال.',

 N'Chair: Head of Fraud / Head of Forensics. Members: Internal Audit, Compliance, HR, Security, Legal, Operations.',
 N'الرئيس: رئيس مكافحة الاحتيال / رئيس الطب الشرعي. الأعضاء: المراجعة الداخلية، الالتزام، الموارد البشرية، الأمن، الشؤون القانونية، العمليات.',

 50, N'MONTHLY', NULL,
 N'CTPL-AUDIT',
 N'Material fraud cases (above threshold or involving senior staff), regulatory reporting requirements, and any case involving criminal referral escalate to the Audit Committee.',
 N'تُصعّد إلى لجنة المراجعة: حالات الاحتيال الجوهرية (فوق العتبة أو التي تشمل كبار الموظفين)، متطلبات التقارير التنظيمية، وأي حالة تتضمن إحالة جنائية.',

 N'SAMA anti-fraud guidelines + ACFE Fraud Examiner standards. Saudi Criminal Code on Fraud + Anti-Embezzlement Law. Material fraud cases reported per sector rules.',
 N'إرشادات ساما لمكافحة الاحتيال + معايير ACFE لفاحصي الاحتيال. نظام مكافحة الاحتيال السعودي + نظام مكافحة اختلاس الأموال العامة. تُبلَّغ حالات الاحتيال الجوهرية وفقاً للقواعد القطاعية.',
 0, 0,
 N'Annual anti-fraud report to Board. Material cases reported per regulator timelines.'),

-- ─── 17. Internal Control Committee ────────────────────────────────────
(N'CTPL-INTCTRL', N'INTERNAL_CONTROL', N'GENERAL', N'SAMA,COSO',
 N'Internal Control Committee', N'لجنة الرقابة الداخلية',
 N'Owns the internal-control framework aligned to COSO Internal Control - Integrated Framework (2013). Quarterly review of control test results, deficiency log, management''s assertions, process changes affecting controls, external auditor findings, and ICOFR (Internal Control over Financial Reporting) readiness.',
 N'تملك إطار الرقابة الداخلية المتوافق مع إطار COSO المتكامل للرقابة الداخلية (2013). تراجع ربع سنوياً نتائج اختبار الضوابط، سجل أوجه القصور، تأكيدات الإدارة، تغييرات العمليات التي تؤثر على الضوابط، ملاحظات المراجع الخارجي، وجاهزية الرقابة الداخلية على التقارير المالية.',

 N'Approves control-test scope, deficiency-remediation plans, management''s control assertion, ICOFR scoping changes, and SoD (segregation-of-duties) exceptions.',
 N'تعتمد نطاق اختبار الضوابط، خطط معالجة أوجه القصور، تأكيد الإدارة على الضوابط، تغييرات نطاق الرقابة الداخلية على التقارير المالية، واستثناءات فصل المهام.',

 N'Chair: Head of Internal Control. Members: Finance, Operations, IT, HR, Compliance, Internal Audit (non-voting).',
 N'الرئيس: رئيس الرقابة الداخلية. الأعضاء: المالية، العمليات، تقنية المعلومات، الموارد البشرية، الالتزام، المراجعة الداخلية (دون تصويت).',

 67, N'QUARTERLY', NULL,
 N'CTPL-AUDIT',
 N'Material control deficiencies, ICOFR scope changes, SoD exceptions above threshold, and any control failure with auditor implications escalate to the Audit Committee.',
 N'تُصعّد إلى لجنة المراجعة: أوجه القصور الجوهرية في الضوابط، تغييرات نطاق الرقابة الداخلية على التقارير المالية، استثناءات فصل المهام فوق العتبة، وأي فشل ضابط له آثار على المراجع.',

 N'SAMA Internal Control Guidelines. COSO Internal Control - Integrated Framework (2013). Where applicable, SOX-equivalent expectations for dual-listed entities.',
 N'إرشادات الرقابة الداخلية الصادرة عن ساما. إطار COSO المتكامل للرقابة الداخلية (2013). حيثما ينطبق، توقعات مكافئة لـ SOX للكيانات المدرجة المزدوجة.',
 0, 0,
 N'Annual control assertion to Audit Committee. ICOFR readiness report to External Auditor. Material control failures reported to Audit Committee within 30 days.'),

-- ─── 18. ESG / Sustainability Committee ────────────────────────────────
(N'CTPL-ESG', N'ESG_SUSTAINABILITY', N'CMA', N'CMA-ESG,Saudi-Green-Initiative,GRI,SASB',
 N'ESG / Sustainability Committee', N'لجنة الاستدامة والحوكمة البيئية والاجتماعية',
 N'Owns the ESG agenda. Quarterly review of carbon footprint, diversity stats, Saudization (Nitaqat) compliance, ESG ratings, stakeholder engagement, ESG reporting framework progress (GRI / SASB), and climate scenario analysis.',
 N'تملك أجندة الاستدامة. تراجع ربع سنوياً البصمة الكربونية، إحصاءات التنوع، الالتزام بالسعودة (نطاقات)، تصنيفات ESG، تواصل أصحاب المصلحة، تقدم إطار تقارير ESG (GRI/SASB)، وتحليل سيناريوهات المناخ.',

 N'Approves the ESG strategy, the annual sustainability report, climate-scenario assumptions, material ESG disclosures, and the ESG-rating engagement strategy.',
 N'تعتمد استراتيجية الاستدامة، تقرير الاستدامة السنوي، افتراضات سيناريوهات المناخ، الإفصاحات الجوهرية للاستدامة، واستراتيجية التعامل مع وكالات تصنيف الاستدامة.',

 N'Chair: Head of Sustainability / ESG. Members: CFO, CHRO, CRO, Investor Relations, external sustainability advisor (recommended).',
 N'الرئيس: رئيس الاستدامة / ESG. الأعضاء: المدير المالي، مدير الموارد البشرية التنفيذي، مدير المخاطر، علاقات المستثمرين، مستشار استدامة خارجي (موصى به).',

 50, N'QUARTERLY', NULL,
 N'CTPL-BOARD',
 N'Material ESG disclosures, climate-scenario outcomes affecting strategy, Saudization-band changes, and stakeholder activism escalate to the Board.',
 N'تُصعّد إلى المجلس: الإفصاحات الجوهرية للاستدامة، نتائج سيناريوهات المناخ التي تؤثر على الاستراتيجية، تغييرات نطاق السعودة، ونشاط أصحاب المصلحة.',

 N'CMA ESG Disclosure Rules (where applicable). Saudi Green Initiative + Vision 2030 sustainability pillars. GRI / SASB / ISSB standards for reporting framework selection.',
 N'قواعد إفصاح الاستدامة الصادرة عن هيئة السوق المالية (حيثما تنطبق). مبادرة السعودية الخضراء + ركائز الاستدامة في رؤية 2030. معايير GRI/SASB/ISSB لاختيار إطار التقارير.',
 0, 0,
 N'Annual sustainability report (CMA disclosure). Material ESG disclosures per Tadawul rules. Saudization status to Ministry of HR + Ministry of Investment.'),

-- ─── 19. Architecture Review Board ─────────────────────────────────────
(N'CTPL-ARB', N'ARCH_REVIEW', N'GENERAL', N'NCA,CCC,TOGAF',
 N'Architecture Review Board (ARB)', N'مجلس مراجعة المعمارية',
 N'Owns the enterprise-architecture standards. Bi-weekly review of architecture exceptions, major-system designs, tech-stack decisions, cloud-migration approvals (CCC-compliance), integration patterns, and standards updates.',
 N'يملك معايير المعمارية المؤسسية. يراجع كل أسبوعين الاستثناءات المعمارية، تصاميم الأنظمة الكبرى، قرارات حزمة التقنية، اعتمادات الترحيل السحابي (متوافق مع CCC)، أنماط التكامل، وتحديثات المعايير.',

 N'Approves the technology blueprint, deviation-from-standard requests, major-design approvals, cloud / SaaS architecture decisions (subject to NCA CCC compliance), and standards updates.',
 N'يعتمد المخطط التقني، طلبات الانحراف عن المعيار، اعتمادات التصاميم الكبرى، قرارات معمارية السحابة / SaaS (رهناً بالامتثال لـ NCA CCC)، وتحديثات المعايير.',

 N'Chair: Chief Architect. Members: CTO, CISO, PMO Lead, principal architects from each major business line.',
 N'الرئيس: كبير المهندسين المعماريين. الأعضاء: مدير التقنية، مدير أمن المعلومات، رئيس مكتب إدارة المشاريع، المهندسون الرئيسيون من كل خط عمل كبير.',

 50, N'BIWEEKLY', NULL,
 N'CTPL-IT',
 N'Architecture decisions affecting risk appetite, cloud-portfolio commitments above threshold, and any deviation from NCA / CCC mandatory baselines escalate to the IT Steering Committee.',
 N'تُصعّد إلى لجنة توجيه تقنية المعلومات: القرارات المعمارية التي تؤثر على شهية المخاطر، التزامات حافظة السحابة فوق العتبة، وأي انحراف عن خطوط الأساس الإلزامية لـ NCA / CCC.',

 N'NCA Essential Cyber-Security Controls (ECC-2) + Cloud Cyber-Security Controls (CCC). TOGAF for general architecture-governance practices.',
 N'الضوابط الأساسية للأمن السيبراني (ECC-2) + ضوابط الأمن السيبراني للحوسبة السحابية (CCC) الصادرة عن الهيئة الوطنية للأمن السيبراني. TOGAF لممارسات حوكمة المعمارية العامة.',
 0, 0,
 N'Bi-weekly summary to IT Steering. Material exceptions reported to CISO + Risk Committee.');
END
GO

PRINT N'V154 — admin.committee_templates seeded with 19 Saudi-regulated committees.';
GO
