-- ============================================================================
-- TSS GRC — Phase B-2: Ethics & Compliance Disclosures
-- ============================================================================
-- Conflict of Interest declarations + Gifts & Hospitality register +
-- Outside Business Activities. Single table with discriminator. Saudi
-- regulators (Nazaha, MoCI) and CMA-listed entities mandate annual filings.
--
--   DRAFT → SUBMITTED → REVIEW → APPROVED|REJECTED|RECUSED|MITIGATED
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='ethics' AND t.name='disclosures')
BEGIN
    CREATE TABLE ethics.disclosures (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id         UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_disc_extid DEFAULT NEWID(),
        organisation_id     BIGINT NOT NULL,
        disclosure_code     NVARCHAR(50) NULL,         -- e.g. DISC-2025-0042
        disclosure_type     NVARCHAR(40) NOT NULL,
        -- CONFLICT_OF_INTEREST | GIFT_RECEIVED | GIFT_GIVEN | HOSPITALITY
        --   | OUTSIDE_BUSINESS | RELATED_PARTY | POLITICAL_DONATION | CHARITABLE
        title_en            NVARCHAR(500) NOT NULL,
        title_ar            NVARCHAR(500) NULL,
        description         NVARCHAR(MAX) NOT NULL,
        -- Discloser identity
        discloser_name      NVARCHAR(255) NOT NULL,
        discloser_email     NVARCHAR(255) NULL,
        discloser_employee_id BIGINT NULL,
        discloser_department NVARCHAR(255) NULL,
        discloser_role      NVARCHAR(255) NULL,
        -- Counter-party (gift-giver, third party, related party)
        counterparty_name   NVARCHAR(500) NULL,
        counterparty_relationship NVARCHAR(100) NULL,
        -- Gift / hospitality specifics
        monetary_value      DECIMAL(18,2) NULL,
        currency            NVARCHAR(3) NULL CONSTRAINT DF_disc_ccy DEFAULT N'SAR',
        occurred_on         DATE NULL,
        -- Workflow
        status              NVARCHAR(30) NOT NULL CONSTRAINT DF_disc_status DEFAULT N'DRAFT',
        -- DRAFT | SUBMITTED | REVIEW | APPROVED | REJECTED | RECUSED | MITIGATED
        decision            NVARCHAR(40) NULL,
        decision_notes      NVARCHAR(MAX) NULL,
        decided_by_name     NVARCHAR(255) NULL,
        decided_at          DATETIME2 NULL,
        framework_id        BIGINT NULL,
        clause_ref          NVARCHAR(100) NULL,
        is_deleted          BIT NOT NULL CONSTRAINT DF_disc_isdel DEFAULT 0,
        created_at          DATETIME2 NOT NULL CONSTRAINT DF_disc_created DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2 NULL,
        created_by          BIGINT NULL,
        updated_by          BIGINT NULL,
        CONSTRAINT FK_disc_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id)
    );
    CREATE INDEX IX_disc_org_status ON ethics.disclosures(organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_disc_org_type   ON ethics.disclosures(organisation_id, disclosure_type) WHERE is_deleted = 0;
    PRINT N'Created ethics.disclosures';
END
ELSE PRINT N'ethics.disclosures already exists';

-- ── Seed: representative disclosures (Saudi context) ──────────────────────
DECLARE @org BIGINT = 1;
DECLARE @fNAZ  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='NAZAHA');
DECLARE @fMOCI BIGINT = (SELECT id FROM compliance.frameworks WHERE code='MOCI_COC');
DECLARE @fISO37 BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_37001');

INSERT INTO ethics.disclosures
  (organisation_id, disclosure_code, disclosure_type, title_en, title_ar, description,
   discloser_name, discloser_department, discloser_role,
   counterparty_name, counterparty_relationship,
   monetary_value, currency, occurred_on,
   status, decision, decision_notes, decided_by_name, decided_at,
   framework_id, clause_ref)
SELECT @org, v.code, v.dt, v.en, v.ar, v.dsc,
       v.who, v.dept, v.role, v.cp, v.cprel,
       v.val, v.ccy, v.occ, v.st, v.dec, v.dnotes, v.decby, v.decat,
       v.fw, v.cls
FROM (VALUES
  (N'DISC-2025-0001', N'CONFLICT_OF_INTEREST',
   N'Spouse employed at competing IT vendor',
   N'إفصاح: زوجة موظف تعمل لدى مورّد منافس',
   N'Senior IT Architect declares that their spouse joined a competing systems-integrator that bids on our infrastructure tenders. Requesting recusal from any tender evaluations involving that vendor for 12 months.',
   N'Mr. Ahmed Al-Sulaimani', N'IT Architecture', N'Senior IT Architect',
   N'TechSphere Saudi Arabia', N'Spouse employer',
   NULL, N'SAR', NULL,
   N'APPROVED', N'RECUSED',
   N'Recusal accepted. CIO + Procurement notified to exclude discloser from any TechSphere tender for 12 months.',
   N'Group Legal Director', '2025-04-12 11:00',
   @fMOCI, N'MoCI CoC §3.2'),
  (N'DISC-2025-0002', N'GIFT_RECEIVED',
   N'Eid Al-Fitr corporate gift basket from key supplier',
   N'هدية عيد الفطر من مورّد رئيسي',
   N'Received an Eid Al-Fitr gift basket (dates, chocolates, branded mug) from a long-standing print services supplier. Estimated value SAR 350. Within the SAR 500 ceremonial-gift threshold but disclosing per policy.',
   N'Ms. Fatimah Al-Harbi', N'Procurement', N'Procurement Specialist',
   N'Riyadh Print Solutions', N'Active supplier',
   350.00, N'SAR', '2025-04-08',
   N'APPROVED', N'KEEP',
   N'Within SAR 500 ceremonial threshold. Approved to retain. No reciprocity expected.',
   N'Procurement Lead', '2025-04-09 14:30',
   @fISO37, N'ISO 37001 §7.5'),
  (N'DISC-2025-0003', N'HOSPITALITY',
   N'Vendor-paid attendance at Cairo conference (proposed)',
   N'دعوة من مورّد لحضور مؤتمر القاهرة',
   N'IT supplier offered to cover registration + 3 nights hotel for the Middle East CIO Summit in Cairo (estimated value USD 4,200). Disclosing prior to acceptance.',
   N'Mr. Tariq Al-Ghamdi', N'Information Technology', N'CIO',
   N'CloudWave Middle East', N'Strategic IT vendor',
   15750.00, N'SAR', '2025-05-20',
   N'REVIEW', NULL, NULL, NULL, NULL,
   @fNAZ, N'Nazaha §6-1'),
  (N'DISC-2025-0004', N'OUTSIDE_BUSINESS',
   N'Annual disclosure: outside directorship at family business',
   N'إفصاح سنوي: عضوية مجلس إدارة منشأة عائلية',
   N'Annual update of declared outside directorship at a family-owned trading company. No overlap with employer business lines.',
   N'Eng. Hassan Al-Qassem', N'Operations', N'VP Operations',
   N'Al-Qassem Trading Co.', N'Family-owned',
   NULL, N'SAR', '2025-01-31',
   N'APPROVED', N'KEEP',
   N'No business overlap; disclosure logged. Re-review in Jan 2026.',
   N'Board Audit Committee', '2025-02-04 10:00',
   @fMOCI, N'MoCI CoC §4.1'),
  (N'DISC-2025-0005', N'GIFT_GIVEN',
   N'Branded notebooks for SAMA on-site visit',
   N'هدايا للوفد الزائر من البنك المركزي',
   N'Provided 8 branded leather notebooks (~SAR 200/each) to SAMA examiners during on-site visit, in line with policy ceremonial-gifts schedule.',
   N'Ms. Reem Al-Otaibi', N'Compliance', N'Head of Regulatory Affairs',
   N'SAMA Examination Team', N'Regulator',
   1600.00, N'SAR', '2025-03-22',
   N'APPROVED', N'KEEP',
   N'Within ceremonial threshold for regulator engagements. Logged for transparency.',
   N'Chief Compliance Officer', '2025-03-23 09:00',
   @fISO37, N'ISO 37001 §7.5')
) AS v(code, dt, en, ar, dsc, who, dept, role, cp, cprel, val, ccy, occ, st, dec, dnotes, decby, decat, fw, cls)
WHERE NOT EXISTS (SELECT 1 FROM ethics.disclosures d WHERE d.organisation_id=@org AND d.disclosure_code=v.code AND d.is_deleted=0);

PRINT N'Seeded 5 sample disclosures.';

SELECT
    N'Phase B-2 disclosures DDL complete' AS status,
    (SELECT COUNT(*) FROM ethics.disclosures WHERE is_deleted=0) AS disclosures;
GO
