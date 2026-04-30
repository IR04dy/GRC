-- ============================================================================
-- TSS GRC Platform - Shared Table Indexes
-- Compatible: SQL Server 2019 Express Edition
-- Purpose: Performance indexes for all shared/platform tables
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- platform.organizations indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_organizations_parent
    ON platform.organizations (parent_org_id)
    WHERE is_deleted = 0;
GO

CREATE NONCLUSTERED INDEX IX_organizations_active
    ON platform.organizations (is_active, is_deleted)
    INCLUDE (code, name_en, name_ar, type);
GO

-- ============================================================================
-- platform.business_units indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_business_units_org
    ON platform.business_units (org_id)
    WHERE is_deleted = 0
    INCLUDE (code, name_en, name_ar, parent_bu_id);
GO

CREATE NONCLUSTERED INDEX IX_business_units_parent
    ON platform.business_units (parent_bu_id)
    WHERE is_deleted = 0;
GO

-- ============================================================================
-- iam.users indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_users_org
    ON iam.users (org_id)
    WHERE is_deleted = 0
    INCLUDE (username, email, display_name_en, display_name_ar, is_active, preferred_locale);
GO

CREATE NONCLUSTERED INDEX IX_users_bu
    ON iam.users (bu_id)
    WHERE is_deleted = 0 AND bu_id IS NOT NULL;
GO

CREATE NONCLUSTERED INDEX IX_users_active_login
    ON iam.users (is_active, is_locked)
    WHERE is_deleted = 0
    INCLUDE (username, email, password_hash, org_id, bu_id);
GO

-- ============================================================================
-- iam.user_roles indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_user_roles_user
    ON iam.user_roles (user_id, is_active)
    INCLUDE (role_id, org_id, bu_id);
GO

CREATE NONCLUSTERED INDEX IX_user_roles_role
    ON iam.user_roles (role_id, org_id)
    WHERE is_active = 1;
GO

-- ============================================================================
-- iam.role_permissions indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_role_permissions_role
    ON iam.role_permissions (role_id)
    INCLUDE (permission_id);
GO

CREATE NONCLUSTERED INDEX IX_role_permissions_permission
    ON iam.role_permissions (permission_id)
    INCLUDE (role_id);
GO

-- ============================================================================
-- platform.controls indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_controls_org_active
    ON platform.controls (org_id, is_active)
    WHERE is_deleted = 0
    INCLUDE (code, title_en, title_ar, control_type, implementation_status, effectiveness, owner_user_id);
GO

CREATE NONCLUSTERED INDEX IX_controls_owner
    ON platform.controls (owner_user_id)
    WHERE is_deleted = 0 AND owner_user_id IS NOT NULL;
GO

-- ============================================================================
-- platform.control_framework_mappings indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_cfm_control
    ON platform.control_framework_mappings (control_id)
    INCLUDE (framework_id, reference_code);
GO

CREATE NONCLUSTERED INDEX IX_cfm_framework
    ON platform.control_framework_mappings (framework_id)
    INCLUDE (control_id, reference_code);
GO

-- ============================================================================
-- workflow.workflow_instances indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_wfi_entity
    ON workflow.workflow_instances (entity_type, entity_id)
    INCLUDE (status, current_step_id, initiated_by);
GO

CREATE NONCLUSTERED INDEX IX_wfi_org_status
    ON workflow.workflow_instances (org_id, status)
    INCLUDE (workflow_def_id, entity_type, entity_id, initiated_at);
GO

-- ============================================================================
-- workflow.workflow_instance_steps indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_wfis_instance
    ON workflow.workflow_instance_steps (instance_id, status)
    INCLUDE (assigned_to, due_at, decision);
GO

-- Pending tasks by user (My Tasks inbox query)
CREATE NONCLUSTERED INDEX IX_wfis_assigned_pending
    ON workflow.workflow_instance_steps (assigned_to, status)
    WHERE status IN ('PENDING', 'IN_PROGRESS')
    INCLUDE (instance_id, step_def_id, due_at);
GO

-- ============================================================================
-- platform.attachments indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_attachments_entity
    ON platform.attachments (entity_type, entity_id)
    WHERE is_deleted = 0
    INCLUDE (file_name, original_file_name, mime_type, uploaded_by, uploaded_at);
GO

-- ============================================================================
-- platform.notifications indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_notifications_user_unread
    ON platform.notifications (user_id, is_read)
    WHERE is_read = 0
    INCLUDE (type, title_en, title_ar, created_at);
GO

CREATE NONCLUSTERED INDEX IX_notifications_user_created
    ON platform.notifications (user_id, created_at DESC);
GO

-- ============================================================================
-- audit.audit_logs indexes (critical for query performance on large table)
-- ============================================================================

-- Most common query: who did what to an entity
CREATE NONCLUSTERED INDEX IX_audit_entity
    ON audit.audit_logs (entity_type, entity_id, event_timestamp DESC)
    INCLUDE (user_id, username, action, module_code);
GO

-- User activity reports
CREATE NONCLUSTERED INDEX IX_audit_user_timestamp
    ON audit.audit_logs (user_id, event_timestamp DESC)
    INCLUDE (entity_type, entity_id, action, module_code)
    WHERE user_id IS NOT NULL;
GO

-- Date range queries (compliance reports, monthly audit reviews)
CREATE NONCLUSTERED INDEX IX_audit_timestamp
    ON audit.audit_logs (event_timestamp DESC)
    INCLUDE (user_id, username, org_id, module_code, entity_type, action);
GO

-- Org-level audit queries
CREATE NONCLUSTERED INDEX IX_audit_org_timestamp
    ON audit.audit_logs (org_id, event_timestamp DESC)
    WHERE org_id IS NOT NULL;
GO

-- Module-level activity
CREATE NONCLUSTERED INDEX IX_audit_module
    ON audit.audit_logs (module_code, event_timestamp DESC)
    WHERE module_code IS NOT NULL;
GO

-- ============================================================================
-- admin.licenses indexes
-- ============================================================================
CREATE NONCLUSTERED INDEX IX_licenses_org_active
    ON admin.licenses (org_id, is_active)
    INCLUDE (module_id, license_type, expires_at, max_users);
GO

CREATE NONCLUSTERED INDEX IX_licenses_module
    ON admin.licenses (module_id, is_active);
GO

PRINT 'Shared indexes created successfully.';
GO
