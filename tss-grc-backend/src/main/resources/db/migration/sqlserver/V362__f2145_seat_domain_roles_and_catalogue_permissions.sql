/*
 * V362 — F2-145 / ADR-017 D-14: seat the nine domain roles the guards already name, and catalogue the
 *        seventeen permissions their modules never had.
 *
 * THE DEFECT. Six Saudi/financial modules guard with hasAnyRole(...) lists that recite a domain role
 * existing in no row of iam.roles. Measured from the reflection dump (611 guard sites; a grep sees 3 of
 * 73 because these expressions span source lines): SHARIA_OFFICER 21 sites, SHARIA_AUDITOR 17,
 * ETHICS_OFFICER 22, OPEN_BANKING_OFFICER 15, AML_OFFICER 14, CREDIT_OFFICER 12,
 * CONSUMER_PROTECTION_OFFICER 9, KYC_OFFICER 6, ONBOARDING_AGENT 5. Every leg was permanently false, so
 * each module fell to whoever else was listed — COMPLIANCE_OFFICER in all six, and in `consumer`
 * COMPLIANCE_OFFICER plus the platform administrator and NOBODY ELSE on any of its 9 guards.
 *
 * WHY SEATING THE ROLE IS THE FIX AND NOT A WORKAROUND. hasAnyRole compares role NAMES. The moment
 * SHARIA_OFFICER exists and a user holds it, the guards that have always named it start working exactly
 * as written — no code change, no redeploy of the annotation. That is mechanism 1 of D-14. Roles here are
 * global (all 58 rows carry organisation_id NULL, is_system_role 1), so this is nine inserts rather than a
 * per-tenant provisioning exercise; that was measured before the mechanism was chosen.
 *
 * WHAT IS DELIBERATELY NOT HERE.
 *   - MODEL_OWNER (ai, 14 sites) is NOT seated. ai_governance.ml_model.model_owner_user_id exists and is
 *     populated on 25 of 25 rows, resolving to real iam.users. The platform already knows who owns each
 *     model, per model; hasAnyRole('MODEL_OWNER') asks whether the caller is in a job family of that name,
 *     which is a different question. Seating it would be LOOSER than the column: the owner of model A
 *     could act on model B. It becomes an entity-bound guard instead.
 *   - SHARIA_BOARD_CHAIR (4 sites) is NOT seated. sharia.sharia_scholar already holds the Sharia
 *     Supervisory Board — 5 rows, ssb_role in (CHAIRMAN, MEMBER, SECRETARY), with terms, credentials and
 *     COI declarations. A board chair is a term-limited SEAT held by a person who may be external, not a
 *     job family. The table has no user_id, which is F2-26's linkage gap; this stays a declared gap rather
 *     than a fabricated role.
 *   - CRO, CIO, AUDIT_COMMITTEE_MEMBER, BOARD_MEMBER are platform-wide governance identities, not any
 *     module's domain role. F2-52's own queue.
 *
 * THE GRANTS ARE DERIVED, NOT AUTHORED. Each (module, role, action) below comes from the guards
 * themselves: the action is the endpoint's HTTP VERB (GET -> READ; POST/PUT/PATCH/DELETE -> WRITE, or
 * APPROVE when the method is approve/reject/transition/decide/sign-shaped), and every grantee also gets
 * READ. ⚠ The first attempt classified by METHOD NAME and invented a grant:
 * WhistleblowerCaseController#updates is a @GetMapping whose name is the plural noun "updates", and a
 * ^update prefix rule read it as a write, which would have given ETHICS:WRITE to INTERNAL_AUDITOR on the
 * strength of a regex. The verb is the fact; the name is a hint.
 *
 * ⚠⚠ WHAT THIS MIGRATION DOES NOT ACHIEVE, AND THE ROW MUST NOT BE READ AS CLOSED.
 * F2-145 makes two claims: the named specialist cannot reach the module (ACCESS), and Sharia oversight is
 * not independent of compliance (INDEPENDENCE). This fixes the first. It does NOT fix the second, and the
 * grant matrix proves it: inside SHARIA, COMPLIANCE_OFFICER, SHARIA_OFFICER and SHARIA_AUDITOR all end
 * with READ+WRITE+APPROVE — three roles, one privilege set, which is not a separation of duties. Same in
 * AML (AML_OFFICER = COMPLIANCE_OFFICER) and CONSUMER. Narrowing COMPLIANCE_OFFICER out of Sharia
 * approval, and splitting officer from auditor, is a POLICY change that removes access somebody may be
 * using today — ADR-010's Consequences require a per-tenant impact query first. It is a separate decision
 * and it is recorded as such rather than smuggled in here.
 *
 * Idempotent: every insert is guarded on its natural key. Re-runnable. Verified at the end by re-reading
 * all three tables and THROWing, because a guarded insert that silently did nothing looks identical to
 * one that worked. Both id columns are IDENTITY, so no id is supplied.
 */

SET NOCOUNT ON;
GO

-- ── 1. Catalogue the 17 permissions ───────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'SHARIA' AND action = N'READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'SHARIA', N'READ', N'SHARIA_READ', N'View the Sharia register: scholars, resolutions, product approvals, findings.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'SHARIA' AND action = N'WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'SHARIA', N'WRITE', N'SHARIA_WRITE', N'Create and amend Sharia resolutions, product submissions and scholar records.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'SHARIA' AND action = N'APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'SHARIA', N'APPROVE', N'SHARIA_APPROVE', N'Transition Sharia audit findings and non-compliance events to a decided state.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'ETHICS' AND action = N'READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'ETHICS', N'READ', N'ETHICS_READ', N'View conflict-of-interest declarations, whistleblower cases and recusals.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'ETHICS' AND action = N'WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'ETHICS', N'WRITE', N'ETHICS_WRITE', N'Manage COI campaigns, case updates and recusal records.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'ETHICS' AND action = N'APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'ETHICS', N'APPROVE', N'ETHICS_APPROVE', N'Decide a conflict-of-interest declaration''s outcome (cleared / mitigation / recusal / escalated).');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'OPENBANKING' AND action = N'READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'OPENBANKING', N'READ', N'OPENBANKING_READ', N'View the Open Banking TPP register, consents, API calls and incidents.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'OPENBANKING' AND action = N'WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'OPENBANKING', N'WRITE', N'OPENBANKING_WRITE', N'Register TPPs, record consents and raise Open Banking incidents.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'OPENBANKING' AND action = N'APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'OPENBANKING', N'APPROVE', N'OPENBANKING_APPROVE', N'Transition Open Banking incidents and consent decisions.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AML' AND action = N'READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'AML', N'READ', N'AML_READ', N'View sanctions lists, screening checks and screening hits.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AML' AND action = N'WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'AML', N'WRITE', N'AML_WRITE', N'Maintain sanctions lists and initiate screening checks.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AML' AND action = N'APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'AML', N'APPROVE', N'AML_APPROVE', N'Dispose of screening hits and clear or escalate AML alerts.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'CPTY' AND action = N'READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'CPTY', N'READ', N'CPTY_READ', N'View counterparties, credit ratings, rating actions and large-exposure limits.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'CPTY' AND action = N'WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'CPTY', N'WRITE', N'CPTY_WRITE', N'Maintain counterparty records, credit ratings and exposure limits.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'CONSUMER' AND action = N'READ')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'CONSUMER', N'READ', N'CONSUMER_READ', N'View the consumer-complaint register and aggregate reports.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'CONSUMER' AND action = N'WRITE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'CONSUMER', N'WRITE', N'CONSUMER_WRITE', N'Record and update consumer complaints.');
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'CONSUMER' AND action = N'APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES (N'CONSUMER', N'APPROVE', N'CONSUMER_APPROVE', N'Close or escalate consumer complaints under the SAMA response window.');
GO

-- ── 2. Seat the 9 domain roles (global system roles; bilingual display names are mandatory) ───
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'SHARIA_OFFICER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'SHARIA_OFFICER', N'Sharia Compliance Officer', N'مسؤول الالتزام الشرعي', N'Institution''s internal Sharia-compliance function; owns the Sharia register, resolutions and product approvals.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'SHARIA_AUDITOR')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'SHARIA_AUDITOR', N'Sharia Auditor', N'مراجع شرعي', N'Internal Sharia audit function; raises and transitions Sharia audit findings and non-compliance events.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'ETHICS_OFFICER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'ETHICS_OFFICER', N'Ethics Officer', N'مسؤول الأخلاقيات', N'Owns the ethics programme — conflict-of-interest campaigns, whistleblower cases and retaliation complaints.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'OPEN_BANKING_OFFICER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'OPEN_BANKING_OFFICER', N'Open Banking Officer', N'مسؤول الخدمات المصرفية المفتوحة', N'Accountable for SAMA Open Banking obligations: TPP register, consents, API incidents.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'AML_OFFICER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'AML_OFFICER', N'AML Officer', N'مسؤول مكافحة غسل الأموال', N'Money-laundering reporting officer; owns sanctions screening and alert disposition.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'KYC_OFFICER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'KYC_OFFICER', N'KYC Officer', N'مسؤول اعرف عميلك', N'Performs know-your-customer due diligence and records screening outcomes.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'ONBOARDING_AGENT')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'ONBOARDING_AGENT', N'Onboarding Agent', N'موظف فتح الحسابات', N'Front-line customer onboarding; initiates screening checks and captures subject data.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'CONSUMER_PROTECTION_OFFICER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'CONSUMER_PROTECTION_OFFICER', N'Consumer Protection Officer', N'مسؤول حماية العملاء', N'Accountable for SAMA consumer-protection obligations and the complaint register.', 1, 1, 0);
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'CREDIT_OFFICER')
    INSERT INTO iam.roles (role_name, display_name_en, display_name_ar, description_en,
                           is_system_role, is_active, is_privileged)
    VALUES (N'CREDIT_OFFICER', N'Credit Officer', N'مسؤول الائتمان', N'Maintains counterparty credit ratings, rating actions and large-exposure limits.', 1, 1, 0);
GO

-- ── 3. Grants, derived from the guards (see header) ───────────────────────────────────────────
--    NEW = a role this migration seats; the rest already existed and keep exactly what their
--    guards allow today, so no existing user gains or loses anything.

-- SHARIA
--   COMPLIANCE_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'SHARIA' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'SHARIA' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'SHARIA' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'SHARIA' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'SHARIA' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'SHARIA' AND p.action = N'APPROVE';
--   INTERNAL_AUDITOR -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'READ';
--   RISK_MANAGER -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'RISK_MANAGER' AND p.module = N'SHARIA' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'RISK_MANAGER' AND p.module = N'SHARIA' AND p.action = N'READ';
--   NEW SHARIA_AUDITOR -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'SHARIA_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'SHARIA_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'SHARIA_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'SHARIA_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'SHARIA_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'SHARIA_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'APPROVE';
--   NEW SHARIA_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'SHARIA_OFFICER' AND p.module = N'SHARIA' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'SHARIA_OFFICER' AND p.module = N'SHARIA' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'SHARIA_OFFICER' AND p.module = N'SHARIA' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'SHARIA_OFFICER' AND p.module = N'SHARIA' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'SHARIA_OFFICER' AND p.module = N'SHARIA' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'SHARIA_OFFICER' AND p.module = N'SHARIA' AND p.action = N'APPROVE';

-- ETHICS
--   BOARD_SECRETARY -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'BOARD_SECRETARY' AND p.module = N'ETHICS' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'BOARD_SECRETARY' AND p.module = N'ETHICS' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'BOARD_SECRETARY' AND p.module = N'ETHICS' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'BOARD_SECRETARY' AND p.module = N'ETHICS' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'BOARD_SECRETARY' AND p.module = N'ETHICS' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'BOARD_SECRETARY' AND p.module = N'ETHICS' AND p.action = N'APPROVE';
--   COMPLIANCE_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'ETHICS' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'ETHICS' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'ETHICS' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'ETHICS' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'ETHICS' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'ETHICS' AND p.action = N'APPROVE';
--   NEW ETHICS_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'ETHICS_OFFICER' AND p.module = N'ETHICS' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'ETHICS_OFFICER' AND p.module = N'ETHICS' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'ETHICS_OFFICER' AND p.module = N'ETHICS' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'ETHICS_OFFICER' AND p.module = N'ETHICS' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'ETHICS_OFFICER' AND p.module = N'ETHICS' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'ETHICS_OFFICER' AND p.module = N'ETHICS' AND p.action = N'APPROVE';
--   INTERNAL_AUDITOR -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'ETHICS' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'ETHICS' AND p.action = N'READ';

-- OPENBANKING
--   CISO -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CISO' AND p.module = N'OPENBANKING' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CISO' AND p.module = N'OPENBANKING' AND p.action = N'READ';
--   COMPLIANCE_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'APPROVE';
--   INTERNAL_AUDITOR -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'OPENBANKING' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'OPENBANKING' AND p.action = N'READ';
--   NEW OPEN_BANKING_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'OPEN_BANKING_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'OPEN_BANKING_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'OPEN_BANKING_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'OPEN_BANKING_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'OPEN_BANKING_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'OPEN_BANKING_OFFICER' AND p.module = N'OPENBANKING' AND p.action = N'APPROVE';
--   RISK_MANAGER -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'RISK_MANAGER' AND p.module = N'OPENBANKING' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'RISK_MANAGER' AND p.module = N'OPENBANKING' AND p.action = N'READ';

-- AML
--   NEW AML_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'AML_OFFICER' AND p.module = N'AML' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'AML_OFFICER' AND p.module = N'AML' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'AML_OFFICER' AND p.module = N'AML' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'AML_OFFICER' AND p.module = N'AML' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'AML_OFFICER' AND p.module = N'AML' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'AML_OFFICER' AND p.module = N'AML' AND p.action = N'APPROVE';
--   AUDITOR -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'AUDITOR' AND p.module = N'AML' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'AUDITOR' AND p.module = N'AML' AND p.action = N'READ';
--   CISO -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CISO' AND p.module = N'AML' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CISO' AND p.module = N'AML' AND p.action = N'READ';
--   COMPLIANCE_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'AML' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'AML' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'AML' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'AML' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'AML' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'AML' AND p.action = N'APPROVE';
--   INTERNAL_AUDITOR -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'AML' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'AML' AND p.action = N'READ';
--   NEW KYC_OFFICER -> READ,WRITE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'KYC_OFFICER' AND p.module = N'AML' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'KYC_OFFICER' AND p.module = N'AML' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'KYC_OFFICER' AND p.module = N'AML' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'KYC_OFFICER' AND p.module = N'AML' AND p.action = N'WRITE';
--   NEW ONBOARDING_AGENT -> READ,WRITE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'ONBOARDING_AGENT' AND p.module = N'AML' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'ONBOARDING_AGENT' AND p.module = N'AML' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'ONBOARDING_AGENT' AND p.module = N'AML' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'ONBOARDING_AGENT' AND p.module = N'AML' AND p.action = N'WRITE';

-- CPTY
--   CFO -> READ,WRITE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CFO' AND p.module = N'CPTY' AND p.action = N'WRITE';
--   COMPLIANCE_OFFICER -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'CPTY' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'CPTY' AND p.action = N'READ';
--   NEW CREDIT_OFFICER -> READ,WRITE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CREDIT_OFFICER' AND p.module = N'CPTY' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CREDIT_OFFICER' AND p.module = N'CPTY' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CREDIT_OFFICER' AND p.module = N'CPTY' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CREDIT_OFFICER' AND p.module = N'CPTY' AND p.action = N'WRITE';
--   INTERNAL_AUDITOR -> READ
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'CPTY' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'INTERNAL_AUDITOR' AND p.module = N'CPTY' AND p.action = N'READ';
--   RISK_MANAGER -> READ,WRITE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'RISK_MANAGER' AND p.module = N'CPTY' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'RISK_MANAGER' AND p.module = N'CPTY' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'RISK_MANAGER' AND p.module = N'CPTY' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'RISK_MANAGER' AND p.module = N'CPTY' AND p.action = N'WRITE';

-- CONSUMER
--   COMPLIANCE_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'COMPLIANCE_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'APPROVE';
--   NEW CONSUMER_PROTECTION_OFFICER -> READ,WRITE,APPROVE
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CONSUMER_PROTECTION_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'READ')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CONSUMER_PROTECTION_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'READ';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CONSUMER_PROTECTION_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'WRITE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CONSUMER_PROTECTION_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'WRITE';
IF NOT EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE r.role_name = N'CONSUMER_PROTECTION_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'APPROVE')
    INSERT INTO iam.role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM iam.roles r CROSS JOIN iam.permissions p
     WHERE r.role_name = N'CONSUMER_PROTECTION_OFFICER' AND p.module = N'CONSUMER' AND p.action = N'APPROVE';
GO

-- ── 4. Verification — a guarded insert that did nothing must not pass for one that worked ─────
DECLARE @perms INT, @roles INT, @grants INT, @msg NVARCHAR(400);

SELECT @perms = COUNT(*) FROM iam.permissions
 WHERE module IN (N'SHARIA', N'ETHICS', N'OPENBANKING', N'AML', N'CPTY', N'CONSUMER');
IF @perms <> 17
BEGIN
    SET @msg = N'V362: expected 17 permissions across the six modules, found ' + CAST(@perms AS NVARCHAR(10));
    THROW 61362, @msg, 1;
END

SELECT @roles = COUNT(*) FROM iam.roles WHERE role_name IN (N'SHARIA_OFFICER', N'SHARIA_AUDITOR', N'ETHICS_OFFICER', N'OPEN_BANKING_OFFICER', N'AML_OFFICER', N'KYC_OFFICER', N'ONBOARDING_AGENT', N'CONSUMER_PROTECTION_OFFICER', N'CREDIT_OFFICER');
IF @roles <> 9
BEGIN
    SET @msg = N'V362: expected 9 seated roles, found ' + CAST(@roles AS NVARCHAR(10));
    THROW 61363, @msg, 1;
END

-- Every seated role must be able to reach its module, or the whole point is missed.
SELECT @grants = COUNT(*) FROM iam.role_permissions rp
  JOIN iam.roles r ON r.id = rp.role_id
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE p.module IN (N'SHARIA', N'ETHICS', N'OPENBANKING', N'AML', N'CPTY', N'CONSUMER');
IF @grants <> 57
BEGIN
    SET @msg = N'V362: expected 57 grants on the six modules, found ' + CAST(@grants AS NVARCHAR(10));
    THROW 61364, @msg, 1;
END

-- A seated role with no permission is a role that changes nothing — the F2-13b shape inverted.
IF EXISTS (SELECT 1 FROM iam.roles r WHERE r.role_name IN (N'SHARIA_OFFICER', N'SHARIA_AUDITOR', N'ETHICS_OFFICER', N'OPEN_BANKING_OFFICER', N'AML_OFFICER', N'KYC_OFFICER', N'ONBOARDING_AGENT', N'CONSUMER_PROTECTION_OFFICER', N'CREDIT_OFFICER')
             AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp WHERE rp.role_id = r.id))
BEGIN
    THROW 61365, N'V362: a seated role holds no permission at all', 1;
END

PRINT 'V362 OK: 17 permissions, 9 roles, 57 grants across SHARIA/ETHICS/OPENBANKING/AML/CPTY/CONSUMER';
GO
