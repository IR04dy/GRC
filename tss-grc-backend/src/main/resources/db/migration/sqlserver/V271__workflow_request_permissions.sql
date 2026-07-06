-- V271 (WF-5): WORKFLOW module permission catalogue.
--
-- The workflow request engine's §6 API surface moves from isAuthenticated() to
-- hasPermission('WORKFLOW','READ'|'WRITE') to satisfy the WS2 authorization ratchet
-- (ControllerAuthorizationRatchetTest — no unguarded write endpoints).
--
-- The engine is an inbox primitive: ANY user can be addressed as a recipient and must
-- be able to view + act on requests routed to them. Both permissions are therefore
-- granted to every existing role in every tenant; the REAL gate is the per-request
-- actor authorization enforced row-level in WorkflowRequestService (actionable
-- recipient of the current step, reviewer/approver identity, initiator-only
-- close/reopen, PLATFORM_ADMIN bypass). Tenants that want to exclude a role from the
-- engine can revoke these grants in the role admin screen.

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'WORKFLOW_READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('WORKFLOW', 'READ', 'WORKFLOW_READ',
            'View workflow requests, the personal inbox and request timelines');

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE permission_name = 'WORKFLOW_WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('WORKFLOW', 'WRITE', 'WORKFLOW_WRITE',
            'Create and act on workflow requests (per-request actor checks still apply)');

-- Grant both to every non-deleted role across all tenants (idempotent).
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam.roles r
CROSS JOIN iam.permissions p
WHERE p.permission_name IN ('WORKFLOW_READ', 'WORKFLOW_WRITE')
  AND r.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);
