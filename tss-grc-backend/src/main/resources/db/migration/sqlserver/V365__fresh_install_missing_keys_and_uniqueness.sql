-- V365 — F2-153(2): the 46 foreign keys and 11 uniqueness guarantees a fresh install does not enforce.
--
-- THE DEFECT, and the FK half is why F2-153 is a P1. `schema_convergence.py` compares a Flyway-built
-- database against Python-bootstrapped dev. Measured 2026-08-07: dev holds **185** user-named foreign
-- keys and a fresh install **132**. None of the 53 missing is created by any migration.
--
--   iam.user_roles        FK_ur_user, FK_ur_role      — a grant can name a user or role that is gone
--   iam.role_permissions  FK_rp_role, FK_rp_perm      — a grant can name a permission that never existed
--
-- On a fresh install those four do not exist, so the authorisation tables have no referential
-- integrity at all: deleting a role leaves its grants behind, pointing at nothing, and nothing
-- objects. Every module's FKs are affected too (change_mgmt, ethics, bcm, compliance) — 46 after
-- exclusions.
--
-- ⚠ AND AN ELEVENTH-HOUR SECOND HALF. Auditing index cover for two of the FKs turned up that the
-- "missing index" behind them is a UNIQUE constraint, so the index axis is a correctness axis:
--
--   iam.users            UQ_username (organisation_id, username)  — TWO USERS, ONE USERNAME, ONE TENANT
--   iam.users            UQ_email    (organisation_id, email)
--   iam.roles            UQ_role_name (organisation_id, role_name)
--   iam.permissions      UQ_permission (module, action)            — two BCM:READ rows
--   platform.organisations UQ_org_short_code (short_code)
--   workflow.definitions / states / transitions, bcm.crisis_comms_templates,
--   change_mgmt.change_links, ethics.case_links
--
-- `UserRepository.findByUsername` returns an `Optional`. With two matching rows it does not return
-- the right one — it throws or picks arbitrarily, depending on the provider. The database constraint
-- is the only control, and a fresh install did not have it.
--
-- ⚠ MEASURED BEFORE WRITING THIS FILE, and the measurement is what makes it safe:
--
--     dev   TSS_GRC             185 FKs · 267 unique guarantees — holds all of these
--     prod  TSS_GRC_Deployment  181 FKs · 263 unique guarantees — holds all of these
--     built TSS_GRC_FULL_CHAIN  132 FKs · 257 unique guarantees — holds none of them
--
-- So every statement is a no-op in both real databases, and the audit that matters was run against
-- the one database in a fresh install's state: **0 orphan rows across all 46 FKs** and **0 duplicate
-- groups across all 11 uniqueness guarantees**, with every parent and referenced table present. No
-- `WITH NOCHECK`: SQL Server verifies the existing rows, which is the point.
--
-- ⚠ SEVEN FKs AND THREE UNIQUE CONSTRAINTS ARE DELIBERATELY NOT SHIPPED. Either end sitting in
-- F2-141's no-code set disqualifies them — `compliance.compliance_assessments`,
-- `compliance.compliance_findings`, `platform.business_units`, `risk.risk_indicators`,
-- `workflow.workflow_instances`, `workflow.workflow_tasks`, `platform.control_frameworks`,
-- `platform.lookup_tables`, `platform.translations`. V364 learned this the hard way: its first draft
-- constrained three such tables and `SchemaContractRatchetTest` failed the build. Constraining a
-- table nothing reads protects nothing. The exclusion is DERIVED from the committed register.
--
-- ⚠ AND A FALSE POSITIVE I ALMOST SHIPPED. Comparing unique indexes BY NAME reported 15 missing; one
-- was `UQ__nafath_t__85C600AEA2D5FA14` on `platform.nafath_transaction(transaction_id)` — a
-- system-generated name. dev, prod and the built database each have that constraint under a DIFFERENT
-- generated name, so it read as absent while being enforced everywhere. Re-comparing on the PROPERTY
-- (schema, table, ordered columns, filter) gives **14 missing, 11 shippable**. Third instrument in
-- this programme to need "compare the property, not the generated name" — the treatment DEFAULT
-- constraints already had, which I failed to carry across one axis.
--
-- DEFINITIONS ARE DEV'S OWN, read from sys.foreign_keys / sys.foreign_key_columns / sys.indexes,
-- including referential actions. Nothing here is composed by hand.
--
-- ORDER MATTERS: the uniqueness guarantees go first. A FOREIGN KEY needs a unique index on the
-- referenced columns, so a UNIQUE constraint this file adds may be the very thing a later FK requires.
--
-- REVERSE RECIPE: ALTER TABLE <schema>.<table> DROP CONSTRAINT <name>;  (for each below)

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Uniqueness guarantees (first: a FK may depend on one) ──────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('bcm.crisis_comms_templates') AND i.name = 'UQ_cct')
    ALTER TABLE bcm.crisis_comms_templates ADD CONSTRAINT UQ_cct UNIQUE ([organisation_id], [code], [version]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('change_mgmt.change_links') AND i.name = 'UQ_chlink_combo')
    ALTER TABLE change_mgmt.change_links ADD CONSTRAINT UQ_chlink_combo UNIQUE ([change_request_id], [entity_type], [entity_id], [link_type]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('ethics.case_links') AND i.name = 'UQ_ethics_link_combo')
    ALTER TABLE ethics.case_links ADD CONSTRAINT UQ_ethics_link_combo UNIQUE ([case_id], [entity_type], [entity_id], [link_type]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('iam.permissions') AND i.name = 'UQ_permission')
    ALTER TABLE iam.permissions ADD CONSTRAINT UQ_permission UNIQUE ([module], [action]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('iam.roles') AND i.name = 'UQ_role_name')
    ALTER TABLE iam.roles ADD CONSTRAINT UQ_role_name UNIQUE ([organisation_id], [role_name]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('iam.users') AND i.name = 'UQ_email')
    ALTER TABLE iam.users ADD CONSTRAINT UQ_email UNIQUE ([organisation_id], [email]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('iam.users') AND i.name = 'UQ_username')
    ALTER TABLE iam.users ADD CONSTRAINT UQ_username UNIQUE ([organisation_id], [username]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('platform.organisations') AND i.name = 'UQ_org_short_code')
    ALTER TABLE platform.organisations ADD CONSTRAINT UQ_org_short_code UNIQUE ([short_code]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('workflow.definitions') AND i.name = 'UQ_wf_def_org_code')
    ALTER TABLE workflow.definitions ADD CONSTRAINT UQ_wf_def_org_code UNIQUE ([organisation_id], [code], [version]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('workflow.states') AND i.name = 'UQ_wf_state')
    ALTER TABLE workflow.states ADD CONSTRAINT UQ_wf_state UNIQUE ([definition_id], [code]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
                WHERE i.object_id = OBJECT_ID('workflow.transitions') AND i.name = 'UQ_wf_tr')
    ALTER TABLE workflow.transitions ADD CONSTRAINT UQ_wf_tr UNIQUE ([definition_id], [from_state_id], [action_code]);
GO

-- ── 2. Foreign keys ─────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('bcm.bia_records') AND fk.name = 'FK_bia_process')
    ALTER TABLE bcm.bia_records ADD CONSTRAINT FK_bia_process FOREIGN KEY ([process_id])
        REFERENCES bcm.critical_processes ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('bcm.crisis_comms_distributions') AND fk.name = 'FK_ccd_audience')
    ALTER TABLE bcm.crisis_comms_distributions ADD CONSTRAINT FK_ccd_audience FOREIGN KEY ([audience_id])
        REFERENCES bcm.crisis_comms_audiences ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('bcm.crisis_comms_distributions') AND fk.name = 'FK_ccd_template')
    ALTER TABLE bcm.crisis_comms_distributions ADD CONSTRAINT FK_ccd_template FOREIGN KEY ([template_id])
        REFERENCES bcm.crisis_comms_templates ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('bcm.exercises') AND fk.name = 'FK_bcm_exer_plan')
    ALTER TABLE bcm.exercises ADD CONSTRAINT FK_bcm_exer_plan FOREIGN KEY ([plan_id])
        REFERENCES bcm.bcm_plans ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('bcm.process_dependencies') AND fk.name = 'FK_pdep_proc')
    ALTER TABLE bcm.process_dependencies ADD CONSTRAINT FK_pdep_proc FOREIGN KEY ([process_id])
        REFERENCES bcm.critical_processes ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('bcm.process_dependencies') AND fk.name = 'FK_pdep_upstream')
    ALTER TABLE bcm.process_dependencies ADD CONSTRAINT FK_pdep_upstream FOREIGN KEY ([depends_on_process_id])
        REFERENCES bcm.critical_processes ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('change_mgmt.change_approvals') AND fk.name = 'FK_chapp_req')
    ALTER TABLE change_mgmt.change_approvals ADD CONSTRAINT FK_chapp_req FOREIGN KEY ([change_request_id])
        REFERENCES change_mgmt.change_requests ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('change_mgmt.change_comments') AND fk.name = 'FK_chcom_req')
    ALTER TABLE change_mgmt.change_comments ADD CONSTRAINT FK_chcom_req FOREIGN KEY ([change_request_id])
        REFERENCES change_mgmt.change_requests ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('change_mgmt.change_links') AND fk.name = 'FK_chlink_req')
    ALTER TABLE change_mgmt.change_links ADD CONSTRAINT FK_chlink_req FOREIGN KEY ([change_request_id])
        REFERENCES change_mgmt.change_requests ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('change_mgmt.change_tasks') AND fk.name = 'FK_chtask_req')
    ALTER TABLE change_mgmt.change_tasks ADD CONSTRAINT FK_chtask_req FOREIGN KEY ([change_request_id])
        REFERENCES change_mgmt.change_requests ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('ethics.aml_alerts') AND fk.name = 'FK_aml_subject')
    ALTER TABLE ethics.aml_alerts ADD CONSTRAINT FK_aml_subject FOREIGN KEY ([subject_id])
        REFERENCES ethics.kyc_subjects ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('ethics.case_actions') AND fk.name = 'FK_ethics_act_case')
    ALTER TABLE ethics.case_actions ADD CONSTRAINT FK_ethics_act_case FOREIGN KEY ([case_id])
        REFERENCES ethics.cases ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('ethics.case_links') AND fk.name = 'FK_ethics_link_case')
    ALTER TABLE ethics.case_links ADD CONSTRAINT FK_ethics_link_case FOREIGN KEY ([case_id])
        REFERENCES ethics.cases ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('ethics.screening_hits') AND fk.name = 'FK_kyc_hit_subj')
    ALTER TABLE ethics.screening_hits ADD CONSTRAINT FK_kyc_hit_subj FOREIGN KEY ([subject_id])
        REFERENCES ethics.kyc_subjects ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('iam.role_permissions') AND fk.name = 'FK_rp_perm')
    ALTER TABLE iam.role_permissions ADD CONSTRAINT FK_rp_perm FOREIGN KEY ([permission_id])
        REFERENCES iam.permissions ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('iam.role_permissions') AND fk.name = 'FK_rp_role')
    ALTER TABLE iam.role_permissions ADD CONSTRAINT FK_rp_role FOREIGN KEY ([role_id])
        REFERENCES iam.roles ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('iam.user_roles') AND fk.name = 'FK_ur_role')
    ALTER TABLE iam.user_roles ADD CONSTRAINT FK_ur_role FOREIGN KEY ([role_id])
        REFERENCES iam.roles ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('iam.user_roles') AND fk.name = 'FK_ur_user')
    ALTER TABLE iam.user_roles ADD CONSTRAINT FK_ur_user FOREIGN KEY ([user_id])
        REFERENCES iam.users ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('iam.users') AND fk.name = 'FK_user_org')
    ALTER TABLE iam.users ADD CONSTRAINT FK_user_org FOREIGN KEY ([organisation_id])
        REFERENCES platform.organisations ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('platform.attachments') AND fk.name = 'FK_att_org')
    ALTER TABLE platform.attachments ADD CONSTRAINT FK_att_org FOREIGN KEY ([organisation_id])
        REFERENCES platform.organisations ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('platform.notifications') AND fk.name = 'FK_notif_recipient')
    ALTER TABLE platform.notifications ADD CONSTRAINT FK_notif_recipient FOREIGN KEY ([recipient_id])
        REFERENCES iam.users ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('privacy.dpia_mitigation') AND fk.name = 'FK_dpiam_dpia')
    ALTER TABLE privacy.dpia_mitigation ADD CONSTRAINT FK_dpiam_dpia FOREIGN KEY ([dpia_id])
        REFERENCES privacy.dpia ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('privacy.dpia_question') AND fk.name = 'FK_dpiaq_dpia')
    ALTER TABLE privacy.dpia_question ADD CONSTRAINT FK_dpiaq_dpia FOREIGN KEY ([dpia_id])
        REFERENCES privacy.dpia ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('privacy.dpia_review') AND fk.name = 'FK_dpiar_dpia')
    ALTER TABLE privacy.dpia_review ADD CONSTRAINT FK_dpiar_dpia FOREIGN KEY ([dpia_id])
        REFERENCES privacy.dpia ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('reporting.report_runs') AND fk.name = 'FK_rrun_sched')
    ALTER TABLE reporting.report_runs ADD CONSTRAINT FK_rrun_sched FOREIGN KEY ([schedule_id])
        REFERENCES reporting.report_schedules ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('risk.appetite_breach_events') AND fk.name = 'FK_appb_app')
    ALTER TABLE risk.appetite_breach_events ADD CONSTRAINT FK_appb_app FOREIGN KEY ([appetite_id])
        REFERENCES risk.risk_appetite_statements ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('risk.risk_assessments') AND fk.name = 'FK_ra_risk')
    ALTER TABLE risk.risk_assessments ADD CONSTRAINT FK_ra_risk FOREIGN KEY ([risk_id])
        REFERENCES risk.risks ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('risk.risk_categories') AND fk.name = 'FK_rcat_parent')
    ALTER TABLE risk.risk_categories ADD CONSTRAINT FK_rcat_parent FOREIGN KEY ([parent_id])
        REFERENCES risk.risk_categories ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('risk.risk_treatments') AND fk.name = 'FK_rt_risk')
    ALTER TABLE risk.risk_treatments ADD CONSTRAINT FK_rt_risk FOREIGN KEY ([risk_id])
        REFERENCES risk.risks ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('risk.risks') AND fk.name = 'FK_risks_category')
    ALTER TABLE risk.risks ADD CONSTRAINT FK_risks_category FOREIGN KEY ([category_id])
        REFERENCES risk.risk_categories ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('risk.risks') AND fk.name = 'FK_risks_org')
    ALTER TABLE risk.risks ADD CONSTRAINT FK_risks_org FOREIGN KEY ([organisation_id])
        REFERENCES platform.organisations ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('scm.contracts') AND fk.name = 'FK_scm_contract_vendor')
    ALTER TABLE scm.contracts ADD CONSTRAINT FK_scm_contract_vendor FOREIGN KEY ([vendor_id])
        REFERENCES scm.vendors ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('scm.dependencies') AND fk.name = 'FK_scm_dep_alt_vendor')
    ALTER TABLE scm.dependencies ADD CONSTRAINT FK_scm_dep_alt_vendor FOREIGN KEY ([alternative_vendor_id])
        REFERENCES scm.vendors ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('scm.dependencies') AND fk.name = 'FK_scm_dep_vendor')
    ALTER TABLE scm.dependencies ADD CONSTRAINT FK_scm_dep_vendor FOREIGN KEY ([vendor_id])
        REFERENCES scm.vendors ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('scm.slas') AND fk.name = 'FK_scm_sla_contract')
    ALTER TABLE scm.slas ADD CONSTRAINT FK_scm_sla_contract FOREIGN KEY ([contract_id])
        REFERENCES scm.contracts ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('scm.slas') AND fk.name = 'FK_scm_sla_vendor')
    ALTER TABLE scm.slas ADD CONSTRAINT FK_scm_sla_vendor FOREIGN KEY ([vendor_id])
        REFERENCES scm.vendors ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('scm.supplier_incidents') AND fk.name = 'FK_scm_inc_vendor')
    ALTER TABLE scm.supplier_incidents ADD CONSTRAINT FK_scm_inc_vendor FOREIGN KEY ([vendor_id])
        REFERENCES scm.vendors ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('scm.vendor_assessments') AND fk.name = 'FK_scm_assess_vendor')
    ALTER TABLE scm.vendor_assessments ADD CONSTRAINT FK_scm_assess_vendor FOREIGN KEY ([vendor_id])
        REFERENCES scm.vendors ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('scm.vendor_onboarding') AND fk.name = 'FK_scm_ob_vendor')
    ALTER TABLE scm.vendor_onboarding ADD CONSTRAINT FK_scm_ob_vendor FOREIGN KEY ([vendor_id])
        REFERENCES scm.vendors ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('workflow.action_log') AND fk.name = 'FK_wf_log_inst')
    ALTER TABLE workflow.action_log ADD CONSTRAINT FK_wf_log_inst FOREIGN KEY ([instance_id])
        REFERENCES workflow.instances ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('workflow.instances') AND fk.name = 'FK_wf_inst_def')
    ALTER TABLE workflow.instances ADD CONSTRAINT FK_wf_inst_def FOREIGN KEY ([definition_id])
        REFERENCES workflow.definitions ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('workflow.instances') AND fk.name = 'FK_wf_inst_st')
    ALTER TABLE workflow.instances ADD CONSTRAINT FK_wf_inst_st FOREIGN KEY ([current_state_id])
        REFERENCES workflow.states ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('workflow.states') AND fk.name = 'FK_wf_state_def')
    ALTER TABLE workflow.states ADD CONSTRAINT FK_wf_state_def FOREIGN KEY ([definition_id])
        REFERENCES workflow.definitions ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('workflow.transitions') AND fk.name = 'FK_wf_tr_def')
    ALTER TABLE workflow.transitions ADD CONSTRAINT FK_wf_tr_def FOREIGN KEY ([definition_id])
        REFERENCES workflow.definitions ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('workflow.transitions') AND fk.name = 'FK_wf_tr_from')
    ALTER TABLE workflow.transitions ADD CONSTRAINT FK_wf_tr_from FOREIGN KEY ([from_state_id])
        REFERENCES workflow.states ([id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk
                WHERE fk.parent_object_id = OBJECT_ID('workflow.transitions') AND fk.name = 'FK_wf_tr_to')
    ALTER TABLE workflow.transitions ADD CONSTRAINT FK_wf_tr_to FOREIGN KEY ([to_state_id])
        REFERENCES workflow.states ([id]);
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
DECLARE @uq INT;
DECLARE @uqMsg NVARCHAR(400) =
    N'V365 failed: fewer than 11 of the named UNIQUE constraints exist. A fresh install can still '
  + N'hold two users with the same username in one tenant.';
SELECT @uq = COUNT(*) FROM sys.indexes i
 WHERE i.is_unique = 1 AND i.name IN (
   'UQ_cct','UQ_chlink_combo','UQ_ethics_link_combo','UQ_permission','UQ_role_name',
   'UQ_email','UQ_username','UQ_org_short_code','UQ_wf_def_org_code','UQ_wf_state','UQ_wf_tr');
IF @uq < 11 THROW 50000, @uqMsg, 1;
GO

DECLARE @fk INT;
DECLARE @fkMsg NVARCHAR(400) =
    N'V365 failed: fewer than 46 of the named foreign keys exist. On a fresh install the '
  + N'authorisation tables would still accept a grant naming a role or permission that is gone.';
SELECT @fk = COUNT(*) FROM sys.foreign_keys fk WHERE fk.is_system_named = 0 AND fk.name IN (
  'FK_aml_subject','FK_appb_app','FK_att_org','FK_bcm_exer_plan',
  'FK_bia_process','FK_ccd_audience','FK_ccd_template','FK_chapp_req',
  'FK_chcom_req','FK_chlink_req','FK_chtask_req','FK_dpiam_dpia',
  'FK_dpiaq_dpia','FK_dpiar_dpia','FK_ethics_act_case','FK_ethics_link_case',
  'FK_kyc_hit_subj','FK_notif_recipient','FK_pdep_proc','FK_pdep_upstream',
  'FK_ra_risk','FK_rcat_parent','FK_risks_category','FK_risks_org',
  'FK_rp_perm','FK_rp_role','FK_rrun_sched','FK_rt_risk',
  'FK_scm_assess_vendor','FK_scm_contract_vendor','FK_scm_dep_alt_vendor','FK_scm_dep_vendor',
  'FK_scm_inc_vendor','FK_scm_ob_vendor','FK_scm_sla_contract','FK_scm_sla_vendor',
  'FK_ur_role','FK_ur_user','FK_user_org','FK_wf_inst_def',
  'FK_wf_inst_st','FK_wf_log_inst','FK_wf_state_def','FK_wf_tr_def',
  'FK_wf_tr_from','FK_wf_tr_to');
IF @fk < 46 THROW 50000, @fkMsg, 1;
GO

-- The four that make this a P1 must be there by name, so a partial application cannot pass the
-- count check above by adding forty-six of something else.
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ur_user')
    THROW 50000, N'V365 failed: iam.user_roles has no FK to iam.users.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ur_role')
    THROW 50000, N'V365 failed: iam.user_roles has no FK to iam.roles.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_rp_role')
    THROW 50000, N'V365 failed: iam.role_permissions has no FK to iam.roles.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_rp_perm')
    THROW 50000, N'V365 failed: iam.role_permissions has no FK to iam.permissions.', 1;
GO

-- Nothing may be untrusted or disabled. `ADD CONSTRAINT` verifies the rows already present; a later
-- WITH NOCHECK or NOCHECK CONSTRAINT would leave the object in the catalogue enforcing nothing for
-- the data that is already there — F2-62's dead-flag shape, one object class over.
DECLARE @bad INT;
DECLARE @badMsg NVARCHAR(400) =
    N'V365 failed: a foreign key it adds is disabled or not trusted, so the rows already in the '
  + N'table were never verified. Fix the data, do not add WITH NOCHECK.';
SELECT @bad = COUNT(*) FROM sys.foreign_keys fk
 WHERE (fk.is_disabled = 1 OR fk.is_not_trusted = 1)
   AND fk.name IN ('FK_ur_user','FK_ur_role','FK_rp_role','FK_rp_perm');
IF @bad > 0 THROW 50000, @badMsg, 1;
GO
