-- V306 (CH-17): classification-driven change authorisation matrix.
--
-- Until now change authorisation was a single flat global — grc.change
-- .cab-min-approvals (default 1) applied to every change regardless of type
-- or risk, and the FE's rich (type x risk) classification computed at intake
-- was discarded. This table persists, per tenant, the authorisation required
-- at each (change_type x risk_level) cell:
--   * gate           — AUTO | CAB | ECAB (the routing tier)
--   * min_approvals  — recorded APPROVE votes required to reach CAB_APPROVED
--   * required_roles — CSV of roles that SHOULD cover the approval (advisory)
--
-- ChangeManagementService reads the matching row to drive the CAB_APPROVED
-- quorum gate (replacing the flat @Value), falling back to the config default
-- if no row is found. Seeded for EVERY organisation. Idempotent on
-- (organisation_id, change_type, risk_level).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'change_mgmt' AND t.name = 'change_authorisation_matrix')
BEGIN
    CREATE TABLE change_mgmt.change_authorisation_matrix (
        id               BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id  BIGINT       NOT NULL,
        change_type      NVARCHAR(20) NOT NULL,   -- STANDARD | NORMAL | EMERGENCY
        risk_level       NVARCHAR(20) NOT NULL,   -- LOW | MEDIUM | HIGH | CRITICAL
        gate             NVARCHAR(10) NOT NULL,   -- AUTO | CAB | ECAB
        min_approvals    INT          NOT NULL CONSTRAINT DF_cam_min DEFAULT (1),
        required_roles   NVARCHAR(400) NULL,      -- CSV, advisory role coverage
        notes_en         NVARCHAR(MAX) NULL,
        notes_ar         NVARCHAR(MAX) NULL,
        is_deleted       BIT          NOT NULL CONSTRAINT DF_cam_del DEFAULT (0),
        created_at       DATETIME2    NOT NULL CONSTRAINT DF_cam_created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT CK_cam_type  CHECK (change_type IN ('STANDARD','NORMAL','EMERGENCY')),
        CONSTRAINT CK_cam_risk  CHECK (risk_level  IN ('LOW','MEDIUM','HIGH','CRITICAL')),
        CONSTRAINT CK_cam_gate  CHECK (gate        IN ('AUTO','CAB','ECAB'))
    );
    CREATE UNIQUE INDEX UX_cam_org_type_risk
        ON change_mgmt.change_authorisation_matrix (organisation_id, change_type, risk_level)
        WHERE is_deleted = 0;
END
GO

;WITH m (change_type, risk_level, gate, min_approvals, required_roles, notes_en, notes_ar) AS (
    SELECT * FROM (VALUES
        -- STANDARD: pre-authorised change model. LOW is auto (no CAB); higher
        -- risk standard changes still need a light CAB confirmation.
        (N'STANDARD', N'LOW',      N'AUTO', 0, N'',                          N'Pre-authorised standard change — no CAB vote required.', N'تغيير قياسي معتمد مسبقًا — لا يتطلب تصويت لجنة.'),
        (N'STANDARD', N'MEDIUM',   N'CAB',  1, N'CHANGE_MANAGER',            N'Standard change, medium risk — one CAB confirmation.',   N'تغيير قياسي متوسط الخطورة — تأكيد لجنة واحد.'),
        (N'STANDARD', N'HIGH',     N'CAB',  1, N'CHANGE_MANAGER',            N'Standard change, high risk — CAB confirmation.',         N'تغيير قياسي عالي الخطورة — تأكيد لجنة.'),
        (N'STANDARD', N'CRITICAL', N'CAB',  2, N'CHANGE_MANAGER,CISO',       N'Critical standard change — two approvals incl. CISO.',   N'تغيير قياسي حرج — موافقتان تشملان رئيس الأمن.'),
        -- NORMAL: the default authorised path through CAB.
        (N'NORMAL',   N'LOW',      N'CAB',  1, N'CHANGE_MANAGER',            N'Normal change, low risk — one CAB approval.',            N'تغيير عادي منخفض الخطورة — موافقة لجنة واحدة.'),
        (N'NORMAL',   N'MEDIUM',   N'CAB',  1, N'CHANGE_MANAGER',            N'Normal change, medium risk — one CAB approval.',         N'تغيير عادي متوسط الخطورة — موافقة لجنة واحدة.'),
        (N'NORMAL',   N'HIGH',     N'ECAB', 2, N'CHANGE_MANAGER,CISO',       N'High-risk normal change — two approvals incl. CISO.',    N'تغيير عادي عالي الخطورة — موافقتان تشملان رئيس الأمن.'),
        (N'NORMAL',   N'CRITICAL', N'ECAB', 2, N'CHANGE_MANAGER,CISO,CTO',   N'Critical change — CAB quorum incl. CISO + CTO.',         N'تغيير حرج — نصاب لجنة يشمل رئيس الأمن والتقنية.'),
        -- EMERGENCY: fast-path (E-CAB); matrix rows are the post-hoc
        -- ratification expectation should an emergency route through CAB.
        (N'EMERGENCY',N'LOW',      N'ECAB', 1, N'CHANGE_MANAGER',            N'Emergency change — one approval / post-hoc ratification.', N'تغيير طارئ — موافقة واحدة / تصديق لاحق.'),
        (N'EMERGENCY',N'MEDIUM',   N'ECAB', 1, N'CHANGE_MANAGER',            N'Emergency change — one approval / post-hoc ratification.', N'تغيير طارئ — موافقة واحدة / تصديق لاحق.'),
        (N'EMERGENCY',N'HIGH',     N'ECAB', 2, N'CHANGE_MANAGER,CISO',       N'High-risk emergency — two approvals incl. CISO.',        N'تغيير طارئ عالي الخطورة — موافقتان تشملان رئيس الأمن.'),
        (N'EMERGENCY',N'CRITICAL', N'ECAB', 2, N'CHANGE_MANAGER,CISO,CTO',   N'Critical emergency — quorum incl. CISO + CTO.',          N'تغيير طارئ حرج — نصاب يشمل رئيس الأمن والتقنية.')
    ) v (change_type, risk_level, gate, min_approvals, required_roles, notes_en, notes_ar)
)
INSERT INTO change_mgmt.change_authorisation_matrix
    (organisation_id, change_type, risk_level, gate, min_approvals, required_roles, notes_en, notes_ar)
SELECT o.id, m.change_type, m.risk_level, m.gate, m.min_approvals,
       NULLIF(m.required_roles, N''), m.notes_en, m.notes_ar
FROM m
CROSS JOIN platform.organisations o
WHERE ISNULL(o.is_deleted, 0) = 0
  AND NOT EXISTS (SELECT 1 FROM change_mgmt.change_authorisation_matrix cam
                  WHERE cam.organisation_id = o.id
                    AND cam.change_type = m.change_type
                    AND cam.risk_level  = m.risk_level);
GO

PRINT 'V306: created change_mgmt.change_authorisation_matrix + seeded 12 cells per tenant.';
GO
