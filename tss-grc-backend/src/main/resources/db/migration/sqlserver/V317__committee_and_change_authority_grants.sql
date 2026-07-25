-- =====================================================================
-- V317 — Committee voting rights (D-7 / ADR-014) and change authority (D-8 / ADR-015).
-- ---------------------------------------------------------------------
-- TWO GUARDS THAT NOBODY COULD SATISFY, AND ONE PERSONA THAT DID NOT EXIST.
--
-- D-7 / ADR-014 — the committee subsystem was unreachable.
--   Measured before this migration:
--     ADMIN:READ    held by 0 of 56 roles
--     ADMIN:WRITE   held by 0 of 56 roles
--     ADMIN:EXPORT  held by 0 of 56 roles
--     ADMIN:APPROVE DID NOT EXIST in iam.permissions
--   Every one of the 59 mapped methods across the 7 committee controllers is guarded by
--   ADMIN:READ or ADMIN:WRITE, so the whole board-governance subsystem — quorum, recusal,
--   e-signed minutes, gate->committee routing, board packs — was reachable only by
--   PLATFORM_ADMIN via the evaluator's code bypass. BOARD_SECRETARY, a seeded system role
--   that exists for precisely this module, could not open it.
--
--   Two consequences of that, both real defects:
--     (a) DF-3's on-behalf check (CommitteeResolutionController#mayRecordOnBehalf) tested for
--         the granted authority ADMIN_APPROVE — a permission that did not exist and therefore
--         could never be granted. The secretary-minutes-a-show-of-hands path was
--         PLATFORM_ADMIN-only in practice while its Javadoc claimed a tenant-controlled split.
--     (b) ADMIN:WRITE gated BOTH "appoint a committee member term" AND "cast a vote", so any
--         principal who could vote could appoint themselves to any committee's roster —
--         privilege escalation straight into quorum, undercutting the recusal and quorum
--         controls it sat next to.
--
--   The fix decomposes one over-loaded grant into three capabilities:
--     ADMIN:READ    (21 roles) — see committees, meetings, resolutions, minutes
--     ADMIN:WRITE   ( 3 roles) — SECRETARIAT: constitute committees, appoint/end member terms,
--                                edit the gate map, agenda, papers, minutes; and record a vote
--                                on another member's behalf
--     ADMIN:APPROVE (15 roles) — cast or record a committee vote, approve minutes  [NEW]
--     ADMIN:EXPORT  ( 7 roles) — committee packs and registers
--   Membership of admin.committee_member_terms still confers the vote; the grant only opens the
--   door. A holder of ADMIN:APPROVE who does not sit on the committee still gets 400.
--
--   NOTE: ADMIN:EXPORT currently gates NO endpoint (0 occurrences in @PreAuthorize). It is
--   granted here for the committee-pack export that Wave 2 adds. Stated rather than left to
--   look like an oversight.
--
-- D-8 / ADR-015 — the CAB was three roles, none of them operational.
--   DF-4 correctly moved the CAB-vote guard from CHANGE:WRITE (41 of 56 roles — not a CAB) to
--   CHANGE:APPROVE, which was held by exactly 3: GRC_ADMINISTRATOR, ORG_ADMIN, RISK_MANAGER.
--   That is a governance-function CAB, not an operational one: SERVICE_OWNER and CISO both
--   exist, both hold CHANGE:WRITE, and neither could approve — and the catalogue contained NO
--   change-manager role at all, though ITIL 4 makes it the default change authority and
--   NCA ECC / SAMA CSF expect a recorded security sign-off on changes.
--
--   The guard stays at CHANGE:APPROVE (do not widen it back to WRITE). This migration fixes the
--   GRANT: creates CHANGE_MANAGER and takes CHANGE:APPROVE from 3 roles to 9 — the realistic
--   CAB composition. Safe to widen because DF-4's provenance controls carry the load: identity
--   from the principal, SoD on the resolved user id, and V316's filtered unique index enforcing
--   one vote per approver per change in the database.
--
-- Deliberately NOT granted either capability: VIEWER, every *_INGEST service account, and
-- EMERGENCY_RESPONDER (break-glass must not silently acquire governance or change authority).
-- Read-only and external personas (AUDITOR, EXTERNAL_AUDITOR, REGULATOR_VIEWER) receive
-- ADMIN:READ — a regulator asking to see the decision trail is the point — but never APPROVE.
--
-- Grants join on role_name, so they apply to every tenant's role with that name (the V272
-- idiom). CHANGE_MANAGER is seeded organisation_id NULL — a cross-tenant system role, matching
-- ORG_ADMIN / RISK_MANAGER / COMPLIANCE_OFFICER.
--
-- Idempotent; safe to re-run. Applied to dev twice via pyodbc (Flyway off in dev).
-- Reverse recipe (grants only; leave the role and the permission, both harmless):
--   DELETE rp FROM iam.role_permissions rp
--     JOIN iam.permissions p ON p.id = rp.permission_id
--     JOIN iam.roles r ON r.id = rp.role_id
--    WHERE (p.module = 'ADMIN')
--       OR (p.module = 'CHANGE' AND p.action = 'APPROVE'
--           AND r.role_name IN ('CHANGE_MANAGER','SERVICE_OWNER','CISO','CYBER_MANAGER',
--                               'COMPLIANCE_OFFICER','EXECUTIVE_REVIEWER'));
-- =====================================================================

-- ── Part 1: catalogue ADMIN:APPROVE ──────────────────────────────────────────
-- permission_name follows the V272 convention MODULE_ACTION, which is also the granted
-- authority string ModulePermissionEvaluator matches on (module + "_" + action).
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT v.module, v.action, v.module + '_' + v.action, v.descr
FROM (VALUES
    ('ADMIN', 'APPROVE', 'Cast or record a committee vote and approve committee minutes')
) v(module, action, descr)
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p
                  WHERE p.module = v.module AND p.action = v.action);
GO

-- ── Part 2: the missing ITIL change-authority persona ────────────────────────
-- organisation_id NULL = cross-tenant system role. Bilingual display names are mandatory
-- (display_name_ar is NOT NULL); is_active and is_deleted take their column defaults.
INSERT INTO iam.roles (external_id, organisation_id, role_name, display_name_en, display_name_ar,
                       description_en, is_system_role)
SELECT NEWID(), NULL, v.rn, v.en, v.ar, v.de, 1
FROM (VALUES
  ('CHANGE_MANAGER', N'Change Manager', N'مدير التغيير',
   N'ITIL change authority: chairs the CAB, authorises normal changes, owns the change schedule.')
) v(rn, en, ar, de)
WHERE NOT EXISTS (SELECT 1 FROM iam.roles r WHERE r.role_name = v.rn AND r.is_deleted = 0);
GO

-- ── Part 3: committee capabilities (D-7 / ADR-014) ──────────────────────────
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    -- ADMIN:READ — governance visibility (21). Includes the assurance and oversight
    -- personas: an external auditor or regulator asking to see the committee's decision
    -- trail is a first-class Saudi use case, and a subsystem nobody can open cannot answer it.
    ('BOARD_SECRETARY','ADMIN','READ'),
    ('GRC_ADMINISTRATOR','ADMIN','READ'),
    ('ORG_ADMIN','ADMIN','READ'),
    ('CISO','ADMIN','READ'),
    ('CFO','ADMIN','READ'),
    ('COMPLIANCE_OFFICER','ADMIN','READ'),
    ('DPO','ADMIN','READ'),
    ('RISK_MANAGER','ADMIN','READ'),
    ('BCM_MANAGER','ADMIN','READ'),
    ('CYBER_MANAGER','ADMIN','READ'),
    ('PRIVACY_MANAGER','ADMIN','READ'),
    ('LEGAL_COUNSEL','ADMIN','READ'),
    ('EXECUTIVE_REVIEWER','ADMIN','READ'),
    ('INTERNAL_AUDITOR','ADMIN','READ'),
    ('EXTERNAL_AUDITOR','ADMIN','READ'),
    ('AUDITOR','ADMIN','READ'),
    ('REGULATOR_VIEWER','ADMIN','READ'),
    ('SUSTAINABILITY_OFFICER','ADMIN','READ'),
    ('SUPPLIER_MANAGER','ADMIN','READ'),
    ('CRISIS_MANAGER','ADMIN','READ'),
    ('FINANCE_LEAD','ADMIN','READ'),

    -- ADMIN:WRITE — SECRETARIAT ONLY (3). This grant appoints member terms, so it must stay
    -- narrow: it is also the on-behalf vote-recording capability. Anyone holding it can shape
    -- the roster, which is why it is not given to the voting population.
    ('BOARD_SECRETARY','ADMIN','WRITE'),
    ('GRC_ADMINISTRATOR','ADMIN','WRITE'),
    ('ORG_ADMIN','ADMIN','WRITE'),

    -- ADMIN:APPROVE — cast or record a committee vote (16). Committee-eligible executives.
    -- The roster check in CommitteeResolutionService is the real restriction; this is the door.
    ('BOARD_SECRETARY','ADMIN','APPROVE'),
    ('GRC_ADMINISTRATOR','ADMIN','APPROVE'),
    ('ORG_ADMIN','ADMIN','APPROVE'),
    ('CISO','ADMIN','APPROVE'),
    ('CFO','ADMIN','APPROVE'),
    ('COMPLIANCE_OFFICER','ADMIN','APPROVE'),
    ('DPO','ADMIN','APPROVE'),
    ('RISK_MANAGER','ADMIN','APPROVE'),
    ('BCM_MANAGER','ADMIN','APPROVE'),
    ('CYBER_MANAGER','ADMIN','APPROVE'),
    ('PRIVACY_MANAGER','ADMIN','APPROVE'),
    ('LEGAL_COUNSEL','ADMIN','APPROVE'),
    ('EXECUTIVE_REVIEWER','ADMIN','APPROVE'),
    -- INTERNAL_AUDITOR deliberately absent: IIA Standard 1110 organisational
    -- independence. The chief audit executive reports TO a governance committee and
    -- attends it; they are not a voting member of it. READ + EXPORT give full
    -- visibility, which is exactly what independence requires — and a tenant whose
    -- charter does seat its CAE can grant APPROVE itself.
    ('SUSTAINABILITY_OFFICER','ADMIN','APPROVE'),
    ('SUPPLIER_MANAGER','ADMIN','APPROVE'),

    -- ADMIN:EXPORT — committee packs, minutes, resolution registers (7). Gates no endpoint
    -- today; granted ahead of the Wave 2 committee-pack export.
    ('BOARD_SECRETARY','ADMIN','EXPORT'),
    ('GRC_ADMINISTRATOR','ADMIN','EXPORT'),
    ('ORG_ADMIN','ADMIN','EXPORT'),
    ('INTERNAL_AUDITOR','ADMIN','EXPORT'),
    ('EXTERNAL_AUDITOR','ADMIN','EXPORT'),
    ('EXECUTIVE_REVIEWER','ADMIN','EXPORT'),
    ('COMPLIANCE_OFFICER','ADMIN','EXPORT'),

    -- ── Part 4: change authority (D-8 / ADR-015) ────────────────────────────
    -- The new ITIL persona gets the full change surface.
    ('CHANGE_MANAGER','CHANGE','READ'),
    ('CHANGE_MANAGER','CHANGE','WRITE'),
    ('CHANGE_MANAGER','CHANGE','APPROVE'),
    ('CHANGE_MANAGER','CHANGE','EXPORT'),

    -- CHANGE:APPROVE 3 -> 9: the realistic CAB. SERVICE_OWNER is ITIL's mandatory
    -- participant; CISO/CYBER_MANAGER carry the NCA ECC / SAMA CSF security sign-off;
    -- COMPLIANCE_OFFICER is regulated-entity representation; EXECUTIVE_REVIEWER ratifies
    -- emergency changes until per-change-type authority lands (F2-13c).
    ('SERVICE_OWNER','CHANGE','APPROVE'),
    ('CISO','CHANGE','APPROVE'),
    ('CYBER_MANAGER','CHANGE','APPROVE'),
    ('COMPLIANCE_OFFICER','CHANGE','APPROVE'),
    ('EXECUTIVE_REVIEWER','CHANGE','APPROVE')
) m(role_name, module, action)
JOIN iam.roles r ON r.role_name = m.role_name AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = m.module AND p.action = m.action
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO
