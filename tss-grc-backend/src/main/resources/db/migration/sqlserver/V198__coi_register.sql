-- V198 — Sprint 13: Conflict-of-Interest (COI) Disclosure Register.
--
-- SAMA Corporate Governance Regulations Article 30 and CMA Listed-
-- Companies CG Code Article 88 require board members, senior
-- executives, and committee members to declare conflicts of interest
-- annually and ad-hoc whenever a new conflict arises. The Ethics
-- Officer + Board Secretariat run the annual cycle and maintain a
-- recusal register that ties back to specific meeting agenda items.
--
-- Most Saudi banks track this in a spreadsheet circulated by email
-- before the annual board pack is assembled. This migration gives
-- the bank an in-platform register that:
--
--   1. Spawns a campaign per fiscal year, with target counts,
--      open/close windows, and scope (BOARD / EXEC / ALL_STAFF /
--      HIGH_RISK_ROLES).
--
--   2. Lets each in-scope user submit a declaration listing every
--      disclosed item — directorship, shareholding, related-party
--      transaction, gift, outside employment, political office,
--      family relationship, or "other". Each item carries the
--      SAR value (when monetisable), the counterparty's bilingual
--      name, and a mitigation action.
--
--   3. Auto-flags the declaration on submission against the
--      bank's COI rule set:
--        - any RELATED_PARTY / FAMILY_RELATIONSHIP / POLITICAL_OFFICE
--          → RECUSAL_REQUIRED
--        - any item with value >= SAR 100,000              → RECUSAL_REQUIRED
--        - any DIRECTORSHIP / OUTSIDE_EMPLOYMENT            → MITIGATION_REQUIRED
--        - any item with value >= SAR 50,000                → MITIGATION_REQUIRED
--        - otherwise PENDING (awaits ethics-officer review)
--
--   4. Records recusal events against specific meetings + agenda
--      items, with an explicit `recorded_in_minutes` flag the
--      Board Secretariat ticks once the official minutes confirm
--      the recusal was applied.
--
-- Four tables, all under the existing `ethics` schema:
--
--   ethics.coi_declaration_campaign  — annual / ad-hoc cycle
--   ethics.coi_declaration           — one row per in-scope user
--   ethics.coi_disclosed_item        — line items inside a declaration
--   ethics.coi_recusal_event         — recusals applied at specific meetings
--
-- All tables: org-scoped, soft-delete filtered, ROWVERSION-backed.
-- Filtered indexes — QUOTED_IDENTIFIER ON required.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema (already created by V195) ────────────────────────────
IF SCHEMA_ID(N'ethics') IS NULL EXEC(N'CREATE SCHEMA ethics');
GO

-- ─── 1. COI declaration campaign ────────────────────────────────────
IF OBJECT_ID(N'ethics.coi_declaration_campaign', N'U') IS NULL
BEGIN
    CREATE TABLE ethics.coi_declaration_campaign (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,

        -- Human-readable code, e.g. COI-2026-ANNUAL. Unique per tenant.
        code                     NVARCHAR(32)  NOT NULL,

        title_en                 NVARCHAR(256) NOT NULL,
        title_ar                 NVARCHAR(256) NOT NULL,

        -- Fiscal year the cycle covers — drives the report rollups.
        fiscal_year              INT           NOT NULL,

        -- Submission window — declarations submitted outside the
        -- window are flagged as late but still accepted.
        opens_on                 DATE          NOT NULL,
        closes_on                DATE          NOT NULL,

        -- Lifecycle:
        --   DRAFT     — being prepared by Ethics Officer / Board Sec
        --   OPEN      — accepting submissions
        --   CLOSED    — window passed; reviews in flight
        --   ARCHIVED  — terminal: cycle reported and locked
        status                   NVARCHAR(32)  NOT NULL DEFAULT N'DRAFT',

        -- Scope of who must declare:
        --   BOARD              — directors only
        --   EXEC               — C-suite + EVPs
        --   ALL_STAFF          — bank-wide annual attestation
        --   HIGH_RISK_ROLES    — procurement / credit / trading desks
        scope                    NVARCHAR(64)  NOT NULL DEFAULT N'BOARD',

        -- Tracking counters — kept in-sync by the service layer.
        target_user_count        INT           NOT NULL DEFAULT 0,
        declared_user_count      INT           NOT NULL DEFAULT 0,

        -- Audit + soft-delete.
        is_deleted               BIT           NOT NULL DEFAULT 0,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NULL,
        updated_by               BIGINT        NULL,
        row_version              ROWVERSION,

        CONSTRAINT CK_coi_camp_status CHECK (status IN
            (N'DRAFT', N'OPEN', N'CLOSED', N'ARCHIVED')),
        CONSTRAINT CK_coi_camp_scope CHECK (scope IN
            (N'BOARD', N'EXEC', N'ALL_STAFF', N'HIGH_RISK_ROLES'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_coi_camp_code'
               AND object_id = OBJECT_ID(N'ethics.coi_declaration_campaign'))
    CREATE UNIQUE INDEX UQ_coi_camp_code ON ethics.coi_declaration_campaign
        (organisation_id, code) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_coi_camp_status'
               AND object_id = OBJECT_ID(N'ethics.coi_declaration_campaign'))
    CREATE INDEX IX_coi_camp_status ON ethics.coi_declaration_campaign
        (organisation_id, status, fiscal_year DESC) WHERE is_deleted = 0;
GO

-- ─── 2. COI declaration ─────────────────────────────────────────────
IF OBJECT_ID(N'ethics.coi_declaration', N'U') IS NULL
BEGIN
    CREATE TABLE ethics.coi_declaration (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,

        campaign_id              BIGINT NOT NULL,

        -- Declaring user. user_id is a soft FK (the IAM module may
        -- be loaded separately); employee_no + bilingual name are
        -- captured at submission so the row is self-describing on
        -- the Board Secretariat's offline report.
        user_id                  BIGINT        NULL,
        employee_no              NVARCHAR(64)  NULL,
        full_name_en             NVARCHAR(256) NOT NULL,
        full_name_ar             NVARCHAR(256) NOT NULL,
        position_title_en        NVARCHAR(256) NULL,
        position_title_ar        NVARCHAR(256) NULL,
        department_en            NVARCHAR(128) NULL,
        department_ar            NVARCHAR(128) NULL,

        declaration_date         DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        -- Top-level flags set by the declarant; the line items
        -- elaborate.
        has_conflict             BIT           NOT NULL DEFAULT 0,
        has_potential_conflict   BIT           NOT NULL DEFAULT 0,

        -- The attestation text the declarant agreed to at submit.
        attestation_text         NVARCHAR(MAX) NULL,

        submitted_on             DATETIME2(3)  NULL,

        -- Ethics-officer review.
        reviewed_by              BIGINT        NULL,
        reviewed_on              DATETIME2(3)  NULL,

        -- Outcome:
        --   PENDING              — submitted, awaits ethics-officer review
        --   CLEARED              — no further action
        --   MITIGATION_REQUIRED  — declarant must implement a stated control
        --   RECUSAL_REQUIRED     — declarant must recuse from related items
        --   ESCALATED            — referred to Ethics Committee / Board
        review_outcome           NVARCHAR(32)  NOT NULL DEFAULT N'PENDING',

        review_notes_en          NVARCHAR(MAX) NULL,
        review_notes_ar          NVARCHAR(MAX) NULL,

        -- Audit + soft-delete.
        is_deleted               BIT           NOT NULL DEFAULT 0,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NULL,
        updated_by               BIGINT        NULL,
        row_version              ROWVERSION,

        CONSTRAINT FK_coi_decl_campaign FOREIGN KEY (campaign_id)
            REFERENCES ethics.coi_declaration_campaign(id),
        CONSTRAINT CK_coi_decl_outcome CHECK (review_outcome IN
            (N'PENDING', N'CLEARED', N'MITIGATION_REQUIRED',
             N'RECUSAL_REQUIRED', N'ESCALATED'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_coi_decl_campaign'
               AND object_id = OBJECT_ID(N'ethics.coi_declaration'))
    CREATE INDEX IX_coi_decl_campaign ON ethics.coi_declaration
        (organisation_id, campaign_id) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_coi_decl_user'
               AND object_id = OBJECT_ID(N'ethics.coi_declaration'))
    CREATE INDEX IX_coi_decl_user ON ethics.coi_declaration
        (organisation_id, user_id) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_coi_decl_outcome'
               AND object_id = OBJECT_ID(N'ethics.coi_declaration'))
    CREATE INDEX IX_coi_decl_outcome ON ethics.coi_declaration
        (organisation_id, review_outcome) WHERE is_deleted = 0;
GO

-- ─── 3. COI disclosed item (line items inside a declaration) ────────
IF OBJECT_ID(N'ethics.coi_disclosed_item', N'U') IS NULL
BEGIN
    CREATE TABLE ethics.coi_disclosed_item (
        id                          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        declaration_id              BIGINT NOT NULL,

        -- Item type — drives the auto-flag rules in CoiService:
        --   DIRECTORSHIP         — board / advisory seat in another entity
        --   SHAREHOLDING         — equity / Tadawul holding
        --   RELATED_PARTY        — transaction with a related party
        --   GIFT_HOSPITALITY     — gift / event / hospitality received
        --   OUTSIDE_EMPLOYMENT   — paid role outside the bank
        --   POLITICAL_OFFICE     — elected / appointed public role
        --   FAMILY_RELATIONSHIP  — family member at bank counterparty
        --   OTHER                — anything else; ethics officer reviews
        item_type                   NVARCHAR(64)  NOT NULL,

        counterparty_en             NVARCHAR(256) NOT NULL,
        counterparty_ar             NVARCHAR(256) NOT NULL,

        relationship_description_en NVARCHAR(MAX) NULL,
        relationship_description_ar NVARCHAR(MAX) NULL,

        -- When monetisable. Always SAR (currency intentionally
        -- not parameterised — declarations are domestic).
        financial_value_sar         DECIMAL(18,2) NULL,

        start_date                  DATE          NULL,
        end_date                    DATE          NULL,

        is_active                   BIT           NOT NULL DEFAULT 1,

        mitigation_action_en        NVARCHAR(MAX) NULL,
        mitigation_action_ar        NVARCHAR(MAX) NULL,

        -- Audit + soft-delete.
        is_deleted                  BIT           NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2(3)  NULL,
        deleted_by                  BIGINT        NULL,
        created_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT        NULL,
        updated_at                  DATETIME2(3)  NULL,
        updated_by                  BIGINT        NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_coi_item_decl FOREIGN KEY (declaration_id)
            REFERENCES ethics.coi_declaration(id),
        CONSTRAINT CK_coi_item_type CHECK (item_type IN
            (N'DIRECTORSHIP', N'SHAREHOLDING', N'RELATED_PARTY',
             N'GIFT_HOSPITALITY', N'OUTSIDE_EMPLOYMENT', N'POLITICAL_OFFICE',
             N'FAMILY_RELATIONSHIP', N'OTHER'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_coi_item_decl'
               AND object_id = OBJECT_ID(N'ethics.coi_disclosed_item'))
    CREATE INDEX IX_coi_item_decl ON ethics.coi_disclosed_item
        (declaration_id, is_active) WHERE is_deleted = 0;
GO

-- ─── 4. COI recusal event ───────────────────────────────────────────
IF OBJECT_ID(N'ethics.coi_recusal_event', N'U') IS NULL
BEGIN
    CREATE TABLE ethics.coi_recusal_event (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,

        -- Optional FK to the declaration this recusal traces back to.
        -- Nullable because a recusal may be recorded ad-hoc without
        -- a prior declaration on file (urgent board meeting).
        declaration_id           BIGINT        NULL,

        user_id                  BIGINT        NOT NULL,

        -- Meeting reference, e.g. BOD-2026-Q1-03, RC-2026-02.
        meeting_reference        NVARCHAR(128) NOT NULL,

        -- Committee code (BOD, AC, RC, CC, NRC, EC, etc.).
        committee_code           NVARCHAR(32)  NULL,

        recusal_date             DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        recusal_reason_en        NVARCHAR(MAX) NOT NULL,
        recusal_reason_ar        NVARCHAR(MAX) NOT NULL,

        -- Pipe-delimited list of agenda-item references the user
        -- recused from, e.g. "3.2|4.1|5".
        agenda_items             NVARCHAR(MAX) NULL,

        -- Board Secretariat confirms the official minutes record
        -- the recusal.
        recorded_in_minutes      BIT           NOT NULL DEFAULT 1,

        -- Audit + soft-delete.
        is_deleted               BIT           NOT NULL DEFAULT 0,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NULL,
        updated_by               BIGINT        NULL,
        row_version              ROWVERSION,

        CONSTRAINT FK_coi_recusal_decl FOREIGN KEY (declaration_id)
            REFERENCES ethics.coi_declaration(id)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_coi_recusal_user'
               AND object_id = OBJECT_ID(N'ethics.coi_recusal_event'))
    CREATE INDEX IX_coi_recusal_user ON ethics.coi_recusal_event
        (organisation_id, user_id, recusal_date DESC) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_coi_recusal_date'
               AND object_id = OBJECT_ID(N'ethics.coi_recusal_event'))
    CREATE INDEX IX_coi_recusal_date ON ethics.coi_recusal_event
        (organisation_id, recusal_date DESC) WHERE is_deleted = 0;
GO

-- ─── 5. Seed: COI-2026-ANNUAL campaign for org 1 (TSS Bank) ─────────
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration_campaign
               WHERE organisation_id = 1 AND code = N'COI-2026-ANNUAL' AND is_deleted = 0)
INSERT INTO ethics.coi_declaration_campaign
    (organisation_id, code, title_en, title_ar, fiscal_year,
     opens_on, closes_on, status, scope,
     target_user_count, declared_user_count)
VALUES
    (1, N'COI-2026-ANNUAL',
     N'COI Annual Cycle FY2026',
     N'دورة إقرار تضارب المصالح السنوية 2026',
     2026, '2026-01-15', '2026-02-28', N'OPEN', N'BOARD',
     25, 8);
GO

-- ─── 6. Seed declarations (8 board members, mixed outcomes) ─────────

-- Declaration 1 — Ahmad Al-Otaibi — Board Member — CLEARED
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration d
               JOIN ethics.coi_declaration_campaign c ON c.id = d.campaign_id
               WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL'
                 AND d.employee_no = N'BOD-001' AND d.is_deleted = 0)
INSERT INTO ethics.coi_declaration
    (organisation_id, campaign_id, user_id, employee_no,
     full_name_en, full_name_ar, position_title_en, position_title_ar,
     department_en, department_ar,
     declaration_date, has_conflict, has_potential_conflict,
     attestation_text, submitted_on, review_outcome,
     review_notes_en, review_notes_ar, reviewed_on)
SELECT 1, c.id, 1001, N'BOD-001',
       N'Ahmad Abdullah Al-Otaibi', N'أحمد بن عبدالله العتيبي',
       N'Board Member', N'عضو مجلس الإدارة',
       N'Board of Directors', N'مجلس الإدارة',
       DATEADD(day, -10, SYSUTCDATETIME()), 0, 0,
       N'I attest that the information disclosed above is complete and accurate to the best of my knowledge, and I undertake to update this declaration promptly should any new conflict arise during the fiscal year.',
       DATEADD(day, -10, SYSUTCDATETIME()), N'CLEARED',
       N'No material conflict identified. Standard annual attestation.',
       N'لا يوجد تضارب جوهري. إقرار سنوي اعتيادي.',
       DATEADD(day, -8, SYSUTCDATETIME())
FROM ethics.coi_declaration_campaign c
WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL' AND c.is_deleted = 0;
GO

-- Declaration 2 — Noura Al-Qahtani — Audit Committee Chair — MITIGATION_REQUIRED
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration d
               JOIN ethics.coi_declaration_campaign c ON c.id = d.campaign_id
               WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL'
                 AND d.employee_no = N'BOD-002' AND d.is_deleted = 0)
INSERT INTO ethics.coi_declaration
    (organisation_id, campaign_id, user_id, employee_no,
     full_name_en, full_name_ar, position_title_en, position_title_ar,
     department_en, department_ar,
     declaration_date, has_conflict, has_potential_conflict,
     attestation_text, submitted_on, review_outcome,
     review_notes_en, review_notes_ar, reviewed_on)
SELECT 1, c.id, 1002, N'BOD-002',
       N'Noura Khaled Al-Qahtani', N'نورة بنت خالد القحطاني',
       N'Audit Committee Chair', N'رئيسة لجنة المراجعة',
       N'Audit Committee', N'لجنة المراجعة',
       DATEADD(day, -9, SYSUTCDATETIME()), 0, 1,
       N'I attest that the information disclosed above is complete and accurate.',
       DATEADD(day, -9, SYSUTCDATETIME()), N'MITIGATION_REQUIRED',
       N'Directorship at family group requires mitigation: declarant to abstain from any audit-committee item involving the named entity.',
       N'تستلزم العضوية في مجلس إدارة المجموعة العائلية تدبيراً للتخفيف: تمتنع المُقِرّة عن التصويت على أي بند في لجنة المراجعة يخصّ الجهة المذكورة.',
       DATEADD(day, -7, SYSUTCDATETIME())
FROM ethics.coi_declaration_campaign c
WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL' AND c.is_deleted = 0;
GO

-- Declaration 3 — Fahd Al-Dosari — Risk Committee Member — RECUSAL_REQUIRED
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration d
               JOIN ethics.coi_declaration_campaign c ON c.id = d.campaign_id
               WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL'
                 AND d.employee_no = N'BOD-003' AND d.is_deleted = 0)
INSERT INTO ethics.coi_declaration
    (organisation_id, campaign_id, user_id, employee_no,
     full_name_en, full_name_ar, position_title_en, position_title_ar,
     department_en, department_ar,
     declaration_date, has_conflict, has_potential_conflict,
     attestation_text, submitted_on, review_outcome,
     review_notes_en, review_notes_ar, reviewed_on)
SELECT 1, c.id, 1003, N'BOD-003',
       N'Fahd Salman Al-Dosari', N'فهد بن سلمان الدوسري',
       N'Risk Committee Member', N'عضو لجنة المخاطر',
       N'Risk Committee', N'لجنة المخاطر',
       DATEADD(day, -8, SYSUTCDATETIME()), 1, 1,
       N'I attest that the information disclosed above is complete and accurate.',
       DATEADD(day, -8, SYSUTCDATETIME()), N'RECUSAL_REQUIRED',
       N'Related-party transaction disclosed with a family-owned IT vendor — declarant must recuse on all RC items involving the vendor for the fiscal year.',
       N'تمّ الإفصاح عن معاملة طرف ذي علاقة مع مورّد تقنية معلومات مملوك للعائلة — يجب على المُقِرّ التنحّي عن أي بند في لجنة المخاطر يتعلق بهذا المورّد خلال السنة المالية.',
       DATEADD(day, -6, SYSUTCDATETIME())
FROM ethics.coi_declaration_campaign c
WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL' AND c.is_deleted = 0;
GO

-- Declaration 4 — Lama Al-Harbi — CRO — CLEARED
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration d
               JOIN ethics.coi_declaration_campaign c ON c.id = d.campaign_id
               WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL'
                 AND d.employee_no = N'EXEC-001' AND d.is_deleted = 0)
INSERT INTO ethics.coi_declaration
    (organisation_id, campaign_id, user_id, employee_no,
     full_name_en, full_name_ar, position_title_en, position_title_ar,
     department_en, department_ar,
     declaration_date, has_conflict, has_potential_conflict,
     attestation_text, submitted_on, review_outcome,
     review_notes_en, review_notes_ar, reviewed_on)
SELECT 1, c.id, 1004, N'EXEC-001',
       N'Lama Majed Al-Harbi', N'لمى بنت ماجد الحربي',
       N'Chief Risk Officer', N'الرئيسة التنفيذية للمخاطر',
       N'Risk Management', N'إدارة المخاطر',
       DATEADD(day, -10, SYSUTCDATETIME()), 0, 0,
       N'I attest that the information disclosed above is complete and accurate.',
       DATEADD(day, -10, SYSUTCDATETIME()), N'CLEARED',
       N'No conflicts disclosed; small Tadawul holdings below the 1% materiality threshold do not require mitigation.',
       N'لا تضارب مُفصَح عنه؛ الحيازات في تداول دون عتبة الجوهرية 1٪ ولا تستوجب أي تدبير.',
       DATEADD(day, -8, SYSUTCDATETIME())
FROM ethics.coi_declaration_campaign c
WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL' AND c.is_deleted = 0;
GO

-- Declaration 5 — Sultan Al-Zahrani — Independent Director — MITIGATION_REQUIRED
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration d
               JOIN ethics.coi_declaration_campaign c ON c.id = d.campaign_id
               WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL'
                 AND d.employee_no = N'BOD-004' AND d.is_deleted = 0)
INSERT INTO ethics.coi_declaration
    (organisation_id, campaign_id, user_id, employee_no,
     full_name_en, full_name_ar, position_title_en, position_title_ar,
     department_en, department_ar,
     declaration_date, has_conflict, has_potential_conflict,
     attestation_text, submitted_on, review_outcome,
     review_notes_en, review_notes_ar, reviewed_on)
SELECT 1, c.id, 1005, N'BOD-004',
       N'Sultan Abdulaziz Al-Zahrani', N'سلطان بن عبدالعزيز الزهراني',
       N'Independent Director', N'عضو مستقل',
       N'Board of Directors', N'مجلس الإدارة',
       DATEADD(day, -7, SYSUTCDATETIME()), 0, 1,
       N'I attest that the information disclosed above is complete and accurate.',
       DATEADD(day, -7, SYSUTCDATETIME()), N'MITIGATION_REQUIRED',
       N'Outside employment as a part-time Shariah advisor at a non-banking entity — disclosed and not in conflict, but quarterly re-attestation required.',
       N'عمل خارجي بوصفه مستشاراً شرعياً بدوام جزئي لدى جهة غير مصرفية — تمّ الإفصاح ولا يوجد تضارب، إلا أنه يُطلب إعادة الإقرار ربع سنوياً.',
       DATEADD(day, -5, SYSUTCDATETIME())
FROM ethics.coi_declaration_campaign c
WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL' AND c.is_deleted = 0;
GO

-- Declaration 6 — Reem Al-Ghamdi — Compliance Committee — CLEARED
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration d
               JOIN ethics.coi_declaration_campaign c ON c.id = d.campaign_id
               WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL'
                 AND d.employee_no = N'BOD-005' AND d.is_deleted = 0)
INSERT INTO ethics.coi_declaration
    (organisation_id, campaign_id, user_id, employee_no,
     full_name_en, full_name_ar, position_title_en, position_title_ar,
     department_en, department_ar,
     declaration_date, has_conflict, has_potential_conflict,
     attestation_text, submitted_on, review_outcome,
     review_notes_en, review_notes_ar, reviewed_on)
SELECT 1, c.id, 1006, N'BOD-005',
       N'Reem Saad Al-Ghamdi', N'ريم بنت سعد الغامدي',
       N'Compliance Committee Member', N'عضوة لجنة الالتزام',
       N'Compliance Committee', N'لجنة الالتزام',
       DATEADD(day, -6, SYSUTCDATETIME()), 0, 0,
       N'I attest that the information disclosed above is complete and accurate.',
       DATEADD(day, -6, SYSUTCDATETIME()), N'CLEARED',
       N'Eid gift from a long-standing vendor declared and returned per the bank''s gifts policy.',
       N'هدية عيد من مورّد طويل العلاقة — تمّ الإفصاح عنها وإرجاعها وفق سياسة الهدايا.',
       DATEADD(day, -4, SYSUTCDATETIME())
FROM ethics.coi_declaration_campaign c
WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL' AND c.is_deleted = 0;
GO

-- Declaration 7 — Badr Al-Shammari — Board Member — RECUSAL_REQUIRED
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration d
               JOIN ethics.coi_declaration_campaign c ON c.id = d.campaign_id
               WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL'
                 AND d.employee_no = N'BOD-006' AND d.is_deleted = 0)
INSERT INTO ethics.coi_declaration
    (organisation_id, campaign_id, user_id, employee_no,
     full_name_en, full_name_ar, position_title_en, position_title_ar,
     department_en, department_ar,
     declaration_date, has_conflict, has_potential_conflict,
     attestation_text, submitted_on, review_outcome,
     review_notes_en, review_notes_ar, reviewed_on)
SELECT 1, c.id, 1007, N'BOD-006',
       N'Badr Mohammed Al-Shammari', N'بدر بن محمد الشمري',
       N'Board Member', N'عضو مجلس الإدارة',
       N'Board of Directors', N'مجلس الإدارة',
       DATEADD(day, -5, SYSUTCDATETIME()), 1, 1,
       N'I attest that the information disclosed above is complete and accurate.',
       DATEADD(day, -5, SYSUTCDATETIME()), N'RECUSAL_REQUIRED',
       N'Political office (municipal council seat) requires recusal on any board matter involving public-sector counterparties for the term of the office.',
       N'المنصب العام (عضوية المجلس البلدي) يستوجب التنحّي عن أي بند يتعلق بأطراف من القطاع العام طوال مدة العضوية.',
       DATEADD(day, -3, SYSUTCDATETIME())
FROM ethics.coi_declaration_campaign c
WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL' AND c.is_deleted = 0;
GO

-- Declaration 8 — Munira Al-Subaiei — Audit Committee — CLEARED
IF NOT EXISTS (SELECT 1 FROM ethics.coi_declaration d
               JOIN ethics.coi_declaration_campaign c ON c.id = d.campaign_id
               WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL'
                 AND d.employee_no = N'BOD-007' AND d.is_deleted = 0)
INSERT INTO ethics.coi_declaration
    (organisation_id, campaign_id, user_id, employee_no,
     full_name_en, full_name_ar, position_title_en, position_title_ar,
     department_en, department_ar,
     declaration_date, has_conflict, has_potential_conflict,
     attestation_text, submitted_on, review_outcome,
     review_notes_en, review_notes_ar, reviewed_on)
SELECT 1, c.id, 1008, N'BOD-007',
       N'Munira Abdulrahman Al-Subaiei', N'منيرة بنت عبدالرحمن السبيعي',
       N'Audit Committee Member', N'عضوة لجنة المراجعة',
       N'Audit Committee', N'لجنة المراجعة',
       DATEADD(day, -4, SYSUTCDATETIME()), 0, 0,
       N'I attest that the information disclosed above is complete and accurate.',
       DATEADD(day, -4, SYSUTCDATETIME()), N'CLEARED',
       N'No conflicts. Spouse''s shareholdings in unrelated listed companies are immaterial and disclosed for completeness.',
       N'لا توجد تضاربات. حيازات الزوج في شركات مدرجة غير مرتبطة بالبنك تعتبر غير جوهرية وأُفصِح عنها استكمالاً للسجل.',
       DATEADD(day, -2, SYSUTCDATETIME())
FROM ethics.coi_declaration_campaign c
WHERE c.organisation_id = 1 AND c.code = N'COI-2026-ANNUAL' AND c.is_deleted = 0;
GO

-- ─── 7. Seed disclosed items (12 across the declarations) ───────────

-- Item 1 — Noura Al-Qahtani: directorship in family group
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-002' AND i.item_type = N'DIRECTORSHIP'
                 AND i.counterparty_en LIKE N'Al-Qahtani Holding%' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'DIRECTORSHIP',
       N'Al-Qahtani Holding Company',
       N'شركة القحطاني القابضة',
       N'Non-executive director on the family holding company''s board since 2019. Holding company does not transact with the bank as a primary line, but holds 4.3% in a downstream entity that occasionally engages the bank for trade finance.',
       N'عضوة غير تنفيذية في مجلس إدارة الشركة العائلية القابضة منذ 2019. لا تتعامل القابضة مع البنك بشكل رئيسي، إلا أنها تملك 4.3٪ في كيان فرعي يتعامل أحياناً مع البنك في تمويل التجارة.',
       NULL, '2019-03-01', 1,
       N'Abstain from any audit-committee item involving the downstream entity for the fiscal year.',
       N'الامتناع عن التصويت على أي بند في لجنة المراجعة يخص الكيان الفرعي خلال السنة المالية.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-002' AND d.is_deleted = 0;
GO

-- Item 2 — Noura Al-Qahtani: Tadawul shareholding > 5%
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-002' AND i.item_type = N'SHAREHOLDING'
                 AND i.counterparty_en LIKE N'%Industrial Investment%' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'SHAREHOLDING',
       N'Saudi Industrial Investment Group (Tadawul: 2250)',
       N'مجموعة الاستثمار الصناعي السعودية (تداول: 2250)',
       N'Direct ownership of 5.4% of issued shares since 2016 — above Tadawul''s 5% disclosable-stake threshold.',
       N'ملكية مباشرة لنسبة 5.4٪ من الأسهم المُصدَرة منذ 2016 — تتجاوز عتبة الإفصاح المفروضة من السوق المالية (5٪).',
       62500000.00, '2016-06-15', 1,
       N'Annual Tadawul disclosure on file. Abstain from any bank transaction with the issuer.',
       N'إفصاح تداول السنوي مودَع. الامتناع عن أي معاملة مصرفية تخص المُصدِر.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-002' AND d.is_deleted = 0;
GO

-- Item 3 — Fahd Al-Dosari: related-party transaction with family IT vendor
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-003' AND i.item_type = N'RELATED_PARTY' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'RELATED_PARTY',
       N'Al-Dosari Tech Solutions',
       N'شركة الدوسري لحلول التقنية',
       N'IT services vendor owned by the declarant''s brother — provides managed-services support to the bank''s branch ATM estate under a contract awarded before the declarant''s appointment to the board.',
       N'مورّد خدمات تقنية مملوك لأخ المُقِرّ — يقدّم خدمات إدارة الدعم لأجهزة الصراف الآلي في فروع البنك بموجب عقد مُبرم قبل تعيين المُقِرّ في مجلس الإدارة.',
       2400000.00, '2023-09-01', 1,
       N'Recuse from all risk-committee items touching the vendor for the fiscal year; renewal of the contract must follow the related-party procurement protocol.',
       N'التنحّي عن أي بند في لجنة المخاطر يخصّ المورّد خلال السنة المالية؛ يُلتزم بآلية المشتريات لأطراف ذات علاقة عند تجديد العقد.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-003' AND d.is_deleted = 0;
GO

-- Item 4 — Fahd Al-Dosari: shareholding in the same vendor entity
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-003' AND i.item_type = N'SHAREHOLDING'
                 AND i.counterparty_en LIKE N'Al-Dosari Tech%' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'SHAREHOLDING',
       N'Al-Dosari Tech Solutions',
       N'شركة الدوسري لحلول التقنية',
       N'2% silent stake acquired in 2023 alongside other family members. No board role in the vendor company.',
       N'حصة صامتة بنسبة 2٪ تمّ اقتناؤها في 2023 ضمن مساهمة عائلية. لا يوجد دور في مجلس إدارة الشركة المورّدة.',
       250000.00, '2023-09-15', 1,
       N'Disclosed annually; recusal already applied via the related-party item above.',
       N'يُفصَح عنها سنوياً؛ التنحّي مطبّق فعلاً عبر بند الطرف ذي العلاقة الوارد أعلاه.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-003' AND d.is_deleted = 0;
GO

-- Item 5 — Sultan Al-Zahrani: outside employment as Shariah advisor
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-004' AND i.item_type = N'OUTSIDE_EMPLOYMENT' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'OUTSIDE_EMPLOYMENT',
       N'Al-Madinah Islamic Endowment',
       N'وقف المدينة الإسلامي',
       N'Part-time Shariah advisor — 4 hours per month, reviewing waqf investment products. The endowment does not transact with the bank.',
       N'مستشار شرعي بدوام جزئي — أربع ساعات شهرياً لمراجعة منتجات استثمار الوقف. لا تتعامل الجهة مع البنك.',
       60000.00, '2024-01-01', 1,
       N'Time-commitment kept under the 6-hr/month threshold; activity disclosed quarterly to the Nominations & Remuneration Committee.',
       N'الالتزام الزمني دون عتبة الست ساعات شهرياً؛ يُفصَح عن النشاط ربع سنوياً للجنة الترشيحات والمكافآت.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-004' AND d.is_deleted = 0;
GO

-- Item 6 — Reem Al-Ghamdi: Eid gift > SAR 1000 from vendor (declared + returned)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-005' AND i.item_type = N'GIFT_HOSPITALITY' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'GIFT_HOSPITALITY',
       N'Al-Faisal Trading Establishment',
       N'مؤسسة الفيصل التجارية',
       N'Eid gift basket received from a long-standing vendor — estimated value SAR 1,800. Gift returned in line with the bank''s gifts & hospitality policy (threshold SAR 500).',
       N'سلّة هدايا عيد مُستلَمة من مورّد طويل العلاقة — قيمتها التقديرية 1,800 ريال. أُعيدت الهدية وفق سياسة الهدايا والضيافة بالبنك (الحد 500 ريال).',
       1800.00, '2026-01-05', 0,
       N'Gift returned, log entry filed in gifts register on 8 January 2026.',
       N'أُعيدت الهدية وقُيّدت في سجل الهدايا بتاريخ 8 يناير 2026.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-005' AND d.is_deleted = 0;
GO

-- Item 7 — Badr Al-Shammari: political office (municipal council)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-006' AND i.item_type = N'POLITICAL_OFFICE' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, end_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'POLITICAL_OFFICE',
       N'Riyadh Municipal Council — District 3',
       N'المجلس البلدي بمدينة الرياض — الدائرة الثالثة',
       N'Elected to the municipal council in December 2024. Term: 4 years. The council oversees municipal contracts in District 3, including SME credit programs the bank participates in.',
       N'انتُخب لعضوية المجلس البلدي في ديسمبر 2024. مدّة العضوية أربع سنوات. يشرف المجلس على عقود البلدية في الدائرة الثالثة، ومنها برامج تمويل المنشآت الصغيرة التي يشارك فيها البنك.',
       NULL, '2024-12-01', '2028-12-01', 1,
       N'Recuse on any board agenda item involving public-sector counterparties or municipal District 3 SME-credit programs for the duration of the term.',
       N'التنحّي عن أي بند في مجلس الإدارة يتعلق بأطراف من القطاع العام أو ببرامج تمويل المنشآت الصغيرة في الدائرة الثالثة طوال مدة العضوية.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-006' AND d.is_deleted = 0;
GO

-- Item 8 — Badr Al-Shammari: family relationship (brother-in-law at vendor)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-006' AND i.item_type = N'FAMILY_RELATIONSHIP' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'FAMILY_RELATIONSHIP',
       N'Al-Shammari Construction Co.',
       N'شركة الشمري للمقاولات',
       N'Declarant''s brother-in-law holds 35% in a construction contractor that bid (unsuccessfully) on the bank''s 2025 branch-refurbishment tender. No active engagement.',
       N'يملك زوج شقيقة المُقِرّ نسبة 35٪ في شركة مقاولات تقدّمت بعرض غير ناجح لمناقصة تجديد فروع البنك لعام 2025. لا يوجد ارتباط نشط.',
       NULL, '2025-04-01', 1,
       N'Recuse on any future tender or related-party transaction involving the entity.',
       N'التنحّي عن أي مناقصة لاحقة أو معاملة طرف ذي علاقة تخص الشركة.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-006' AND d.is_deleted = 0;
GO

-- Item 9 — Ahmad Al-Otaibi: small Tadawul shareholding (immaterial)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-001' AND i.item_type = N'SHAREHOLDING' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'SHAREHOLDING',
       N'Diversified Tadawul portfolio (no single >1%)',
       N'محفظة متنوعة في تداول (لا تتجاوز حصة واحدة 1٪)',
       N'Personal investment portfolio held with the bank''s wealth-management arm. No single holding above the 1% materiality threshold.',
       N'محفظة استثمار شخصية مُدارة عبر ذراع البنك لإدارة الثروات. لا تتجاوز أي حصة منفردة عتبة الجوهرية 1٪.',
       180000.00, '2018-04-01', 1,
       N'No mitigation required — below materiality.',
       N'لا حاجة إلى تدبير تخفيف — تحت حد الجوهرية.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-001' AND d.is_deleted = 0;
GO

-- Item 10 — Lama Al-Harbi (CRO): immaterial Tadawul shares
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'EXEC-001' AND i.item_type = N'SHAREHOLDING' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'SHAREHOLDING',
       N'Saudi Aramco (Tadawul: 2222) — 0.0001%',
       N'أرامكو السعودية (تداول: 2222) — 0.0001٪',
       N'IPO-allocation residual holding from 2019, held outside the bank.',
       N'حصّة متبقّية من تخصيص الاكتتاب العام 2019 محفوظة خارج البنك.',
       45000.00, '2019-12-11', 1,
       N'No mitigation required — well below materiality.',
       N'لا حاجة إلى تدبير تخفيف — تحت حد الجوهرية بكثير.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'EXEC-001' AND d.is_deleted = 0;
GO

-- Item 11 — Munira Al-Subaiei: spouse shareholdings (other)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-007' AND i.item_type = N'OTHER' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'OTHER',
       N'Spouse — multiple Tadawul holdings',
       N'الزوج — حيازات متعددة في تداول',
       N'Spouse holds positions in three Tadawul-listed companies, none of which transact with the bank as principals. Disclosed for completeness.',
       N'يمتلك الزوج مراكز في ثلاث شركات مدرجة في تداول، لا يتعامل أيٌّ منها مع البنك بصفة أصيل. أُفصِح عنها استكمالاً للسجل.',
       320000.00, '2020-01-01', 1,
       N'No mitigation required; spouse positions reviewed annually.',
       N'لا حاجة إلى تدبير تخفيف؛ تُراجَع مراكز الزوج سنوياً.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-007' AND d.is_deleted = 0;
GO

-- Item 12 — Reem Al-Ghamdi: related-party (cousin at a regulator-adjacent firm)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_disclosed_item i
               JOIN ethics.coi_declaration d ON d.id = i.declaration_id
               WHERE d.employee_no = N'BOD-005' AND i.item_type = N'FAMILY_RELATIONSHIP' AND i.is_deleted = 0)
INSERT INTO ethics.coi_disclosed_item
    (declaration_id, item_type, counterparty_en, counterparty_ar,
     relationship_description_en, relationship_description_ar,
     financial_value_sar, start_date, is_active,
     mitigation_action_en, mitigation_action_ar)
SELECT d.id, N'FAMILY_RELATIONSHIP',
       N'Cousin — Senior Compliance Consultant at peer bank',
       N'ابن العم — مستشار التزام في بنك مماثل',
       N'A first cousin works as a senior compliance consultant at a peer Saudi bank. No information-sharing relationship. Disclosed for completeness.',
       N'يعمل ابن عمّ المُقِرّة مستشار التزام في بنك سعودي مماثل. لا توجد علاقة لتبادل المعلومات. أُفصِح عنها استكمالاً للسجل.',
       NULL, '2021-06-01', 1,
       N'No mitigation; standard non-disclosure obligations apply.',
       N'لا حاجة إلى تدبير تخفيف؛ تُطبَّق التزامات السرّية الاعتيادية.'
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-005' AND d.is_deleted = 0;
GO

-- ─── 8. Seed recusal events ─────────────────────────────────────────

-- Recusal 1 — Badr Al-Shammari at BOD-2026-Q1-03 (credit facility, related family member)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_recusal_event r
               WHERE r.organisation_id = 1 AND r.meeting_reference = N'BOD-2026-Q1-03'
                 AND r.user_id = 1007 AND r.is_deleted = 0)
INSERT INTO ethics.coi_recusal_event
    (organisation_id, declaration_id, user_id,
     meeting_reference, committee_code, recusal_date,
     recusal_reason_en, recusal_reason_ar,
     agenda_items, recorded_in_minutes)
SELECT 1, d.id, 1007,
       N'BOD-2026-Q1-03', N'BOD', DATEADD(day, -2, SYSUTCDATETIME()),
       N'Credit-facility request submitted by a related family member through a non-banking SME — declarant recused before agenda item 4.2 to preserve independence of the credit decision.',
       N'طلب تسهيل ائتماني تقدّم به قريب عائلي عبر منشأة صغيرة غير مصرفية — تنحّى المُقِرّ قبل تناول البند 4.2 حفاظاً على استقلالية قرار الائتمان.',
       N'4.2', 1
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-006' AND d.is_deleted = 0;
GO

-- Recusal 2 — Fahd Al-Dosari at RC-2026-02 (operational risk on family IT vendor)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_recusal_event r
               WHERE r.organisation_id = 1 AND r.meeting_reference = N'RC-2026-02'
                 AND r.user_id = 1003 AND r.is_deleted = 0)
INSERT INTO ethics.coi_recusal_event
    (organisation_id, declaration_id, user_id,
     meeting_reference, committee_code, recusal_date,
     recusal_reason_en, recusal_reason_ar,
     agenda_items, recorded_in_minutes)
SELECT 1, d.id, 1003,
       N'RC-2026-02', N'RC', DATEADD(day, -5, SYSUTCDATETIME()),
       N'Operational-risk review of an ATM-managed-services incident at branches in the Eastern Region — vendor is a related party of the declarant; full recusal applied.',
       N'مراجعة مخاطر تشغيلية لحادث في خدمات إدارة الصراف الآلي بفروع المنطقة الشرقية — المورّد طرف ذو علاقة بالمُقِرّ؛ طُبّق التنحّي الكامل.',
       N'3.1|3.2|3.3', 1
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-003' AND d.is_deleted = 0;
GO

-- Recusal 3 — Noura Al-Qahtani at CC-2026-01 (compliance review of related shareholder)
IF NOT EXISTS (SELECT 1 FROM ethics.coi_recusal_event r
               WHERE r.organisation_id = 1 AND r.meeting_reference = N'CC-2026-01'
                 AND r.user_id = 1002 AND r.is_deleted = 0)
INSERT INTO ethics.coi_recusal_event
    (organisation_id, declaration_id, user_id,
     meeting_reference, committee_code, recusal_date,
     recusal_reason_en, recusal_reason_ar,
     agenda_items, recorded_in_minutes)
SELECT 1, d.id, 1002,
       N'CC-2026-01', N'CC', DATEADD(day, -7, SYSUTCDATETIME()),
       N'Compliance Committee review of a sanctions-screening hit involving a downstream entity in which the declarant''s family holding owns 4.3% — recusal applied for the duration of the agenda item.',
       N'مراجعة لجنة الالتزام لإشارة فحص عقوبات تخصّ كياناً تملك القابضة العائلية للمُقِرّة فيه 4.3٪ — طُبّق التنحّي طوال مدة البند.',
       N'2.4', 1
FROM ethics.coi_declaration d
WHERE d.employee_no = N'BOD-002' AND d.is_deleted = 0;
GO
