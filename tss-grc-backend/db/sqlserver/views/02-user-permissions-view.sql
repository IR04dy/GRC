-- ============================================================================
-- TSS GRC Platform - User Permissions View
-- Compatible: SQL Server 2019 Express Edition
-- Purpose: Flattened view of user permissions for authorization queries
-- ============================================================================

USE TSS_GRC;
GO

CREATE OR ALTER VIEW reporting.vw_user_permissions
AS
SELECT
    u.id                    AS user_id,
    u.username,
    u.email,
    u.display_name_en,
    u.org_id                AS user_org_id,
    u.bu_id                 AS user_bu_id,
    ur.org_id               AS role_org_id,
    ur.bu_id                AS role_bu_id,
    r.id                    AS role_id,
    r.code                  AS role_code,
    r.name_en               AS role_name,
    r.module_code           AS role_module,
    p.id                    AS permission_id,
    p.code                  AS permission_code,
    p.module_code           AS permission_module,
    p.resource_type,
    p.action,
    ur.valid_from,
    ur.valid_to
FROM iam.users u
INNER JOIN iam.user_roles ur
    ON u.id = ur.user_id
    AND ur.is_active = 1
    AND (ur.valid_to IS NULL OR ur.valid_to > SYSUTCDATETIME())
INNER JOIN iam.roles r
    ON ur.role_id = r.id
    AND r.is_active = 1
    AND r.is_deleted = 0
INNER JOIN iam.role_permissions rp
    ON r.id = rp.role_id
INNER JOIN iam.permissions p
    ON rp.permission_id = p.id
    AND p.is_active = 1
WHERE
    u.is_active = 1
    AND u.is_deleted = 0
    AND u.is_locked = 0;
GO

-- ============================================================================
-- Active Controls with Framework Mappings View
-- ============================================================================
CREATE OR ALTER VIEW reporting.vw_active_controls
AS
SELECT
    c.id                    AS control_id,
    c.external_id,
    c.org_id,
    c.code,
    c.title_en,
    c.title_ar,
    c.control_type,
    c.control_category,
    c.implementation_status,
    c.effectiveness,
    c.owner_user_id,
    u.display_name_en       AS owner_name_en,
    u.display_name_ar       AS owner_name_ar,
    c.review_frequency,
    c.last_reviewed_at,
    c.next_review_at,
    -- Framework mappings (comma-separated)
    cf_list.framework_codes,
    cf_list.framework_references
FROM platform.controls c
LEFT JOIN iam.users u
    ON c.owner_user_id = u.id AND u.is_deleted = 0
OUTER APPLY (
    SELECT
        STRING_AGG(cf.code, ', ')           AS framework_codes,
        STRING_AGG(cfm.reference_code, ', ') AS framework_references
    FROM platform.control_framework_mappings cfm
    INNER JOIN platform.control_frameworks cf ON cfm.framework_id = cf.id
    WHERE cfm.control_id = c.id
) cf_list
WHERE c.is_deleted = 0 AND c.is_active = 1;
GO

-- ============================================================================
-- Incident Summary View (for dashboards)
-- ============================================================================
CREATE OR ALTER VIEW reporting.vw_incident_summary
AS
SELECT
    i.org_id,
    YEAR(i.detected_at)     AS incident_year,
    MONTH(i.detected_at)    AS incident_month,
    i.incident_type,
    i.severity,
    i.status,
    COUNT(*)                AS incident_count
FROM cyber.incidents i
WHERE i.is_deleted = 0
    AND i.detected_at IS NOT NULL
GROUP BY
    i.org_id,
    YEAR(i.detected_at),
    MONTH(i.detected_at),
    i.incident_type,
    i.severity,
    i.status;
GO

PRINT 'Additional views created successfully.';
GO
