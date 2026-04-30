-- ============================================================================
-- TSS GRC Platform - Unified Risk View
-- Compatible: SQL Server 2019 Express Edition
-- Purpose: Aggregates all risks from all modules into a single queryable view
-- ============================================================================

USE TSS_GRC;
GO

CREATE OR ALTER VIEW reporting.vw_unified_risk_summary
AS
SELECT
    r.id                    AS risk_id,
    r.external_id,
    r.org_id,
    r.bu_id,
    r.code                  AS risk_code,
    r.title_en,
    r.title_ar,
    r.risk_type,
    r.source_module,
    r.source_entity_id,
    r.likelihood,
    r.impact,
    r.inherent_score,
    r.residual_likelihood,
    r.residual_impact,
    r.residual_score,
    r.risk_level,
    r.status,
    r.treatment_strategy,
    r.owner_user_id,
    u.display_name_en       AS owner_name_en,
    u.display_name_ar       AS owner_name_ar,
    rc.name_en              AS category_name_en,
    rc.name_ar              AS category_name_ar,
    r.review_date,
    r.created_at,
    r.updated_at,
    -- Derived: days since last review
    DATEDIFF(DAY, r.last_reviewed_at, SYSUTCDATETIME()) AS days_since_review,
    -- Derived: risk score change indicator
    CASE
        WHEN r.residual_score < r.inherent_score THEN 'REDUCED'
        WHEN r.residual_score = r.inherent_score THEN 'UNCHANGED'
        WHEN r.residual_score > r.inherent_score THEN 'INCREASED'
        ELSE 'NOT_ASSESSED'
    END AS risk_trend,
    -- Organization info
    o.name_en               AS org_name_en,
    o.name_ar               AS org_name_ar,
    -- BU info
    bu.name_en              AS bu_name_en,
    bu.name_ar              AS bu_name_ar
FROM risk.risks r
LEFT JOIN iam.users u
    ON r.owner_user_id = u.id AND u.is_deleted = 0
LEFT JOIN risk.risk_categories rc
    ON r.category_id = rc.id AND rc.is_deleted = 0
LEFT JOIN platform.organizations o
    ON r.org_id = o.id AND o.is_deleted = 0
LEFT JOIN platform.business_units bu
    ON r.bu_id = bu.id AND bu.is_deleted = 0
WHERE r.is_deleted = 0;
GO

-- ============================================================================
-- Compliance Status View
-- ============================================================================
CREATE OR ALTER VIEW reporting.vw_compliance_status
AS
SELECT
    a.id                    AS assessment_id,
    a.org_id,
    cf.code                 AS framework_code,
    cf.name_en              AS framework_name_en,
    cf.name_ar              AS framework_name_ar,
    a.title                 AS assessment_title,
    a.status                AS assessment_status,
    a.overall_score,
    a.compliance_percentage,
    a.assessment_date,
    a.period_start,
    a.period_end,
    -- Control counts
    COUNT(ac.id)            AS total_controls,
    SUM(CASE WHEN ac.compliance_status = 'COMPLIANT' THEN 1 ELSE 0 END)
                            AS compliant_count,
    SUM(CASE WHEN ac.compliance_status = 'PARTIALLY_COMPLIANT' THEN 1 ELSE 0 END)
                            AS partial_count,
    SUM(CASE WHEN ac.compliance_status = 'NON_COMPLIANT' THEN 1 ELSE 0 END)
                            AS non_compliant_count,
    SUM(CASE WHEN ac.compliance_status = 'NOT_APPLICABLE' THEN 1 ELSE 0 END)
                            AS not_applicable_count,
    SUM(CASE WHEN ac.compliance_status = 'NOT_ASSESSED' THEN 1 ELSE 0 END)
                            AS not_assessed_count,
    -- Finding counts
    COUNT(DISTINCT f.id)    AS total_findings,
    SUM(CASE WHEN f.severity = 'CRITICAL' THEN 1 ELSE 0 END)
                            AS critical_findings,
    SUM(CASE WHEN f.severity = 'HIGH' THEN 1 ELSE 0 END)
                            AS high_findings,
    -- Organization
    o.name_en               AS org_name_en
FROM compliance.assessments a
INNER JOIN platform.control_frameworks cf
    ON a.framework_id = cf.id
LEFT JOIN compliance.assessment_controls ac
    ON a.id = ac.assessment_id
LEFT JOIN compliance.findings f
    ON a.id = f.assessment_id AND f.is_deleted = 0
LEFT JOIN platform.organizations o
    ON a.org_id = o.id AND o.is_deleted = 0
WHERE a.is_deleted = 0
GROUP BY
    a.id, a.org_id, cf.code, cf.name_en, cf.name_ar,
    a.title, a.status, a.overall_score, a.compliance_percentage,
    a.assessment_date, a.period_start, a.period_end, o.name_en;
GO

PRINT 'Reporting views created successfully.';
GO
