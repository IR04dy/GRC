-- ============================================================================
-- TSS GRC Platform - Module Table Indexes
-- Compatible: SQL Server 2019 Express Edition
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- risk module indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_risks_org_status
    ON risk.risks (org_id, status, risk_level)
    WHERE is_deleted = 0
    INCLUDE (code, title_en, title_ar, source_module, inherent_score, residual_score, owner_user_id);
GO

CREATE NONCLUSTERED INDEX IX_risks_source
    ON risk.risks (source_module, source_entity_id)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_risks_owner
    ON risk.risks (owner_user_id, status)
    WHERE is_deleted = 0 AND owner_user_id IS NOT NULL;
GO

CREATE NONCLUSTERED INDEX IX_risk_assessments_risk
    ON risk.risk_assessments (risk_id, assessment_date DESC)
    INCLUDE (likelihood, impact, score, risk_level);
GO

CREATE NONCLUSTERED INDEX IX_risk_treatments_risk_status
    ON risk.risk_treatments (risk_id, status)
    WHERE is_deleted = 0;
GO

-- ============================================================================
-- cyber module indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_assets_org_type
    ON cyber.assets (org_id, asset_type, criticality)
    WHERE is_deleted = 0
    INCLUDE (code, name_en, classification, status);
GO

CREATE NONCLUSTERED INDEX IX_vulnerabilities_asset
    ON cyber.vulnerabilities (asset_id, severity, status)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_vulnerabilities_org_status
    ON cyber.vulnerabilities (org_id, status, severity)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_incidents_org_status
    ON cyber.incidents (org_id, status, severity)
    WHERE is_deleted = 0
    INCLUDE (code, title, incident_type, detected_at, assigned_to);
GO

CREATE NONCLUSTERED INDEX IX_incidents_detected
    ON cyber.incidents (org_id, detected_at DESC)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_incidents_linked_risk
    ON cyber.incidents (linked_risk_id)
    WHERE is_deleted = 0 AND linked_risk_id IS NOT NULL;
GO

-- ============================================================================
-- bcm module indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_critical_processes_org
    ON bcm.critical_processes (org_id, status)
    WHERE is_deleted = 0
    INCLUDE (code, name_en, priority, rto_hours, rpo_hours);
GO

CREATE NONCLUSTERED INDEX IX_bcm_plans_org_status
    ON bcm.bcm_plans (org_id, status)
    WHERE is_deleted = 0
    INCLUDE (code, name_en, plan_type, version, review_date);
GO

CREATE NONCLUSTERED INDEX IX_bcm_exercises_org_date
    ON bcm.bcm_exercises (org_id, scheduled_date)
    WHERE is_deleted = 0;
GO

-- ============================================================================
-- privacy module indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_privacy_assessments_org
    ON privacy.privacy_assessments (org_id, assessment_type, status)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_breach_records_org_status
    ON privacy.breach_records (org_id, status, severity)
    WHERE is_deleted = 0
    INCLUDE (code, title, detected_at, linked_risk_id);
GO

CREATE NONCLUSTERED INDEX IX_breach_records_detected
    ON privacy.breach_records (org_id, detected_at DESC)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_processing_activities_org
    ON privacy.processing_activities (org_id, status)
    WHERE is_deleted = 0;
GO

-- ============================================================================
-- compliance module indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_assessments_org_framework
    ON compliance.assessments (org_id, framework_id, status)
    WHERE is_deleted = 0
    INCLUDE (title, overall_score, compliance_percentage, assessment_date);
GO

CREATE NONCLUSTERED INDEX IX_assessment_controls_assessment
    ON compliance.assessment_controls (assessment_id, compliance_status)
    INCLUDE (control_id, score);
GO

CREATE NONCLUSTERED INDEX IX_findings_assessment_status
    ON compliance.findings (assessment_id, status, severity)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_remediation_plans_status
    ON compliance.remediation_plans (responsible_user_id, status, target_date)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_evidence_items_assessment
    ON compliance.evidence_items (assessment_id, status)
    WHERE is_deleted = 0;
GO

-- ============================================================================
-- reporting module indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_report_executions_org_status
    ON reporting.report_executions (org_id, status, executed_at DESC)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_report_executions_requested_by
    ON reporting.report_executions (requested_by, executed_at DESC)
    WHERE is_deleted = 0;
GO

PRINT 'Module indexes created successfully.';
GO
