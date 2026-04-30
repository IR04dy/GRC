-- ============================================================================
-- TSS GRC Platform - Compliance & Audit Management Module Tables
-- Schema: compliance
-- Compatible: SQL Server 2019 Express Edition
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- compliance.compliance_frameworks
-- Extends platform.control_frameworks with compliance-specific attributes
-- ============================================================================
CREATE TABLE compliance.compliance_frameworks (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    framework_id        BIGINT                  NOT NULL,  -- FK to platform.control_frameworks
    org_id              BIGINT                  NOT NULL,
    is_mandatory        BIT                     NOT NULL DEFAULT 0,
    regulatory_body     NVARCHAR(200)           NULL,
    compliance_deadline DATE                    NULL,
    is_active           BIT                     NOT NULL DEFAULT 1,
    notes               NVARCHAR(MAX)           NULL,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_comp_frameworks PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_comp_framework_org UNIQUE (framework_id, org_id),
    CONSTRAINT FK_cf_framework FOREIGN KEY (framework_id)
        REFERENCES platform.control_frameworks(id),
    CONSTRAINT FK_cf_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id)
);
GO

-- ============================================================================
-- compliance.assessments
-- ============================================================================
CREATE TABLE compliance.assessments (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id              BIGINT                  NOT NULL,
    framework_id        BIGINT                  NOT NULL,
    title               NVARCHAR(500)           NOT NULL,
    description         NVARCHAR(MAX)           NULL,
    scope               NVARCHAR(MAX)           NULL,
    assessor_user_id    BIGINT                  NULL,
    assessment_date     DATE                    NULL,
    due_date            DATE                    NULL,
    status              NVARCHAR(50)            NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT CK_assess_status CHECK (status IN (
            'DRAFT','IN_PROGRESS','UNDER_REVIEW','APPROVED','ARCHIVED')),
    overall_score       DECIMAL(5,2)            NULL,
    overall_status      NVARCHAR(50)            NULL
        CONSTRAINT CK_assess_ostatus CHECK (overall_status IN (
            'COMPLIANT','PARTIALLY_COMPLIANT','NON_COMPLIANT','NOT_APPLICABLE')),
    compliance_percentage DECIMAL(5,2)          NULL,
    period_start        DATE                    NULL,
    period_end          DATE                    NULL,
    approved_by         BIGINT                  NULL,
    approved_at         DATETIME2(7)            NULL,
    notes               NVARCHAR(MAX)           NULL,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    deleted_by          BIGINT                  NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_assessments PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_assess_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_assess_framework FOREIGN KEY (framework_id)
        REFERENCES platform.control_frameworks(id),
    CONSTRAINT FK_assess_assessor FOREIGN KEY (assessor_user_id) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- compliance.assessment_controls
-- Per-control compliance status within an assessment
-- ============================================================================
CREATE TABLE compliance.assessment_controls (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    assessment_id       BIGINT                  NOT NULL,
    control_id          BIGINT                  NOT NULL,
    compliance_status   NVARCHAR(50)            NOT NULL DEFAULT 'NOT_ASSESSED'
        CONSTRAINT CK_ac_status CHECK (compliance_status IN (
            'COMPLIANT','PARTIALLY_COMPLIANT','NON_COMPLIANT',
            'NOT_APPLICABLE','NOT_ASSESSED')),
    score               DECIMAL(5,2)            NULL,
    evidence_notes      NVARCHAR(MAX)           NULL,
    assessor_notes      NVARCHAR(MAX)           NULL,
    assessed_by         BIGINT                  NULL,
    assessed_at         DATETIME2(7)            NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_assessment_controls PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_ac_assessment_control UNIQUE (assessment_id, control_id),
    CONSTRAINT FK_ac_assessment FOREIGN KEY (assessment_id) REFERENCES compliance.assessments(id),
    CONSTRAINT FK_ac_control FOREIGN KEY (control_id) REFERENCES platform.controls(id),
    CONSTRAINT FK_ac_assessor FOREIGN KEY (assessed_by) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- compliance.findings
-- ============================================================================
CREATE TABLE compliance.findings (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    external_id         UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    assessment_id       BIGINT                  NOT NULL,
    assessment_control_id BIGINT                NULL,
    control_id          BIGINT                  NULL,
    title               NVARCHAR(500)           NOT NULL,
    description         NVARCHAR(MAX)           NULL,
    finding_type        NVARCHAR(50)            NOT NULL DEFAULT 'GAP'
        CONSTRAINT CK_find_type CHECK (finding_type IN (
            'GAP','OBSERVATION','DEFICIENCY','MAJOR_NONCONFORMITY','MINOR_NONCONFORMITY')),
    severity            NVARCHAR(50)            NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT CK_find_severity CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    status              NVARCHAR(50)            NOT NULL DEFAULT 'OPEN'
        CONSTRAINT CK_find_status CHECK (status IN (
            'OPEN','IN_REMEDIATION','RESOLVED','ACCEPTED','CLOSED')),
    evidence_required   BIT                     NOT NULL DEFAULT 1,
    identified_by       BIGINT                  NULL,
    identified_at       DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    target_closure_date DATE                    NULL,
    actual_closure_date DATE                    NULL,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    row_version         ROWVERSION              NOT NULL,
    CONSTRAINT PK_findings PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_find_assessment FOREIGN KEY (assessment_id) REFERENCES compliance.assessments(id),
    CONSTRAINT FK_find_ac FOREIGN KEY (assessment_control_id)
        REFERENCES compliance.assessment_controls(id),
    CONSTRAINT FK_find_control FOREIGN KEY (control_id) REFERENCES platform.controls(id)
);
GO

-- ============================================================================
-- compliance.gaps
-- ============================================================================
CREATE TABLE compliance.gaps (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    finding_id              BIGINT                  NOT NULL,
    gap_description         NVARCHAR(MAX)           NOT NULL,
    impact                  NVARCHAR(MAX)           NULL,
    root_cause              NVARCHAR(MAX)           NULL,
    recommended_action      NVARCHAR(MAX)           NULL,
    priority                NVARCHAR(50)            NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT CK_gap_priority CHECK (priority IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(7)            NULL,
    CONSTRAINT PK_gaps PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_gap_finding FOREIGN KEY (finding_id) REFERENCES compliance.findings(id)
);
GO

-- ============================================================================
-- compliance.remediation_plans
-- ============================================================================
CREATE TABLE compliance.remediation_plans (
    id                          BIGINT IDENTITY(1,1)    NOT NULL,
    external_id                 UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    gap_id                      BIGINT                  NOT NULL,
    title                       NVARCHAR(500)           NOT NULL,
    description                 NVARCHAR(MAX)           NULL,
    responsible_user_id         BIGINT                  NULL,
    target_date                 DATE                    NULL,
    actual_completion_date      DATE                    NULL,
    status                      NVARCHAR(50)            NOT NULL DEFAULT 'PLANNED'
        CONSTRAINT CK_rp_status CHECK (status IN (
            'PLANNED','IN_PROGRESS','COMPLETED','CANCELLED','OVERDUE')),
    priority                    NVARCHAR(50)            NOT NULL DEFAULT 'MEDIUM',
    estimated_effort            NVARCHAR(100)           NULL,
    cost_estimate               DECIMAL(18,4)           NULL,
    progress_notes              NVARCHAR(MAX)           NULL,
    created_by                  BIGINT                  NULL,
    created_at                  DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by                  BIGINT                  NULL,
    updated_at                  DATETIME2(7)            NULL,
    is_deleted                  BIT                     NOT NULL DEFAULT 0,
    deleted_at                  DATETIME2(7)            NULL,
    row_version                 ROWVERSION              NOT NULL,
    CONSTRAINT PK_remediation_plans PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_rp_gap FOREIGN KEY (gap_id) REFERENCES compliance.gaps(id),
    CONSTRAINT FK_rp_responsible FOREIGN KEY (responsible_user_id) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- compliance.evidence_items
-- ============================================================================
CREATE TABLE compliance.evidence_items (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    assessment_id           BIGINT                  NOT NULL,
    finding_id              BIGINT                  NULL,
    assessment_control_id   BIGINT                  NULL,
    title                   NVARCHAR(500)           NOT NULL,
    description             NVARCHAR(MAX)           NULL,
    evidence_type           NVARCHAR(50)            NOT NULL DEFAULT 'DOCUMENT'
        CONSTRAINT CK_ev_type CHECK (evidence_type IN (
            'DOCUMENT','SCREENSHOT','LOG','POLICY','PROCEDURE',
            'CERTIFICATE','TEST_RESULT','INTERVIEW_NOTE','OTHER')),
    attachment_id           BIGINT                  NULL,
    url                     NVARCHAR(1000)          NULL,
    submitted_by            BIGINT                  NULL,
    submitted_at            DATETIME2(7)            NULL,
    reviewed_by             BIGINT                  NULL,
    reviewed_at             DATETIME2(7)            NULL,
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'PENDING'
        CONSTRAINT CK_ev_status CHECK (status IN (
            'PENDING','UNDER_REVIEW','ACCEPTED','REJECTED','EXPIRED')),
    rejection_reason        NVARCHAR(MAX)           NULL,
    expiry_date             DATE                    NULL,
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_evidence_items PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_ev_assessment FOREIGN KEY (assessment_id) REFERENCES compliance.assessments(id),
    CONSTRAINT FK_ev_finding FOREIGN KEY (finding_id) REFERENCES compliance.findings(id),
    CONSTRAINT FK_ev_attachment FOREIGN KEY (attachment_id) REFERENCES platform.attachments(id),
    CONSTRAINT FK_ev_submitted FOREIGN KEY (submitted_by) REFERENCES iam.users(id)
);
GO

PRINT 'Compliance module tables created successfully.';
GO
