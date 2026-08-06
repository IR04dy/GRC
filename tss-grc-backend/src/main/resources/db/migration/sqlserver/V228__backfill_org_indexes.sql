-- ⚠ F2-77 (2026-08-06): every guard here now also requires the TABLE to exist.
-- As written each batch asked only whether the INDEX existed:
--     IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = ... AND object_id = OBJECT_ID(t))
-- When t does not exist OBJECT_ID(t) is NULL, so object_id = NULL is never true, NOT EXISTS is
-- TRUE, and the batch proceeds to CREATE INDEX on a table that is not there -> error 1088.
-- The guard tested the wrong half of its own precondition.
--
-- This is what blocked the chain at V228: compliance.compliance_assessments is created by NO
-- migration (F2-140 - three live tables with no DDL and no JPA entity). Whether those tables
-- should exist is an owner decision; an index backfill failing because an optional table is
-- absent is a defect either way, so this fix is independent of that decision.
-- ============================================================================
-- V228 -- backfill organisation_id indexes on tenant tables  (audit PERF-IX-001)
--
-- 67 multi-tenant tables carry organisation_id but had no index leading with it,
-- so every org-scoped query (the platform's dominant access pattern -- see the
-- TenantContext per-query filter) was a full clustered-index scan. This backfills
-- a nonclustered index leading with organisation_id on each, extended with
-- is_deleted where present so the ubiquitous "WHERE organisation_id = ? AND
-- is_deleted = 0" predicate is fully covered by the index key.
--
-- Generated from the live schema (tables already carrying an org-leading index
-- -- ~298 of the 365 tenant tables, incl. the V225 hot-table backfill -- are
-- intentionally skipped). Fully idempotent: each statement is guarded by
-- IF NOT EXISTS on the index name, so it is a safe no-op where already present.
-- Non-filtered to avoid the filtered-index QUOTED_IDENTIFIER DML requirement.
-- ============================================================================

IF OBJECT_ID('aml.sanctions_list_entry') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_sanctions_list_entry_org' AND object_id = OBJECT_ID('aml.sanctions_list_entry'))
    CREATE NONCLUSTERED INDEX ix_sanctions_list_entry_org ON aml.sanctions_list_entry (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.bcm_exercises') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bcm_exercises_org' AND object_id = OBJECT_ID('bcm.bcm_exercises'))
    CREATE NONCLUSTERED INDEX ix_bcm_exercises_org ON bcm.bcm_exercises (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.bcm_plans') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bcm_plans_org' AND object_id = OBJECT_ID('bcm.bcm_plans'))
    CREATE NONCLUSTERED INDEX ix_bcm_plans_org ON bcm.bcm_plans (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.bcp_plans') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bcp_plans_org' AND object_id = OBJECT_ID('bcm.bcp_plans'))
    CREATE NONCLUSTERED INDEX ix_bcp_plans_org ON bcm.bcp_plans (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.bcp_sections') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bcp_sections_org' AND object_id = OBJECT_ID('bcm.bcp_sections'))
    CREATE NONCLUSTERED INDEX ix_bcp_sections_org ON bcm.bcp_sections (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.bcp_versions') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bcp_versions_org' AND object_id = OBJECT_ID('bcm.bcp_versions'))
    CREATE NONCLUSTERED INDEX ix_bcp_versions_org ON bcm.bcp_versions (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.bia_assessments') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bia_assessments_org' AND object_id = OBJECT_ID('bcm.bia_assessments'))
    CREATE NONCLUSTERED INDEX ix_bia_assessments_org ON bcm.bia_assessments (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.bia_records') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bia_records_org' AND object_id = OBJECT_ID('bcm.bia_records'))
    CREATE NONCLUSTERED INDEX ix_bia_records_org ON bcm.bia_records (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.compliance_mappings') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_compliance_mappings_org' AND object_id = OBJECT_ID('bcm.compliance_mappings'))
    CREATE NONCLUSTERED INDEX ix_compliance_mappings_org ON bcm.compliance_mappings (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.crisis_comms_audiences') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_crisis_comms_audiences_org' AND object_id = OBJECT_ID('bcm.crisis_comms_audiences'))
    CREATE NONCLUSTERED INDEX ix_crisis_comms_audiences_org ON bcm.crisis_comms_audiences (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.crisis_decisions') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_crisis_decisions_org' AND object_id = OBJECT_ID('bcm.crisis_decisions'))
    CREATE NONCLUSTERED INDEX ix_crisis_decisions_org ON bcm.crisis_decisions (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.crisis_regulatory_notifications') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_crisis_regulatory_notifications_org' AND object_id = OBJECT_ID('bcm.crisis_regulatory_notifications'))
    CREATE NONCLUSTERED INDEX ix_crisis_regulatory_notifications_org ON bcm.crisis_regulatory_notifications (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.crisis_situation_reports') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_crisis_situation_reports_org' AND object_id = OBJECT_ID('bcm.crisis_situation_reports'))
    CREATE NONCLUSTERED INDEX ix_crisis_situation_reports_org ON bcm.crisis_situation_reports (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.critical_processes') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_critical_processes_org' AND object_id = OBJECT_ID('bcm.critical_processes'))
    CREATE NONCLUSTERED INDEX ix_critical_processes_org ON bcm.critical_processes (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.critical_resources') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_critical_resources_org' AND object_id = OBJECT_ID('bcm.critical_resources'))
    CREATE NONCLUSTERED INDEX ix_critical_resources_org ON bcm.critical_resources (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.exercises') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_exercises_org' AND object_id = OBJECT_ID('bcm.exercises'))
    CREATE NONCLUSTERED INDEX ix_exercises_org ON bcm.exercises (organisation_id, is_deleted);
GO

IF OBJECT_ID('bcm.recovery_operations') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_recovery_operations_org' AND object_id = OBJECT_ID('bcm.recovery_operations'))
    CREATE NONCLUSTERED INDEX ix_recovery_operations_org ON bcm.recovery_operations (organisation_id, is_deleted);
GO

IF OBJECT_ID('compliance.compliance_assessments') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_compliance_assessments_org' AND object_id = OBJECT_ID('compliance.compliance_assessments'))
    CREATE NONCLUSTERED INDEX ix_compliance_assessments_org ON compliance.compliance_assessments (organisation_id, is_deleted);
GO

IF OBJECT_ID('compliance.evidence_items') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_evidence_items_org' AND object_id = OBJECT_ID('compliance.evidence_items'))
    CREATE NONCLUSTERED INDEX ix_evidence_items_org ON compliance.evidence_items (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.control_assessments') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_control_assessments_org' AND object_id = OBJECT_ID('cyber.control_assessments'))
    CREATE NONCLUSTERED INDEX ix_control_assessments_org ON cyber.control_assessments (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.cyber_action_plans') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cyber_action_plans_org' AND object_id = OBJECT_ID('cyber.cyber_action_plans'))
    CREATE NONCLUSTERED INDEX ix_cyber_action_plans_org ON cyber.cyber_action_plans (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.cyber_assets') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cyber_assets_org' AND object_id = OBJECT_ID('cyber.cyber_assets'))
    CREATE NONCLUSTERED INDEX ix_cyber_assets_org ON cyber.cyber_assets (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.cyber_controls') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cyber_controls_org' AND object_id = OBJECT_ID('cyber.cyber_controls'))
    CREATE NONCLUSTERED INDEX ix_cyber_controls_org ON cyber.cyber_controls (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.cyber_evidence') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cyber_evidence_org' AND object_id = OBJECT_ID('cyber.cyber_evidence'))
    CREATE NONCLUSTERED INDEX ix_cyber_evidence_org ON cyber.cyber_evidence (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.cyber_frameworks') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cyber_frameworks_org' AND object_id = OBJECT_ID('cyber.cyber_frameworks'))
    CREATE NONCLUSTERED INDEX ix_cyber_frameworks_org ON cyber.cyber_frameworks (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.cyber_incidents') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cyber_incidents_org' AND object_id = OBJECT_ID('cyber.cyber_incidents'))
    CREATE NONCLUSTERED INDEX ix_cyber_incidents_org ON cyber.cyber_incidents (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.cyber_sub_controls') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cyber_sub_controls_org' AND object_id = OBJECT_ID('cyber.cyber_sub_controls'))
    CREATE NONCLUSTERED INDEX ix_cyber_sub_controls_org ON cyber.cyber_sub_controls (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.vuln_exceptions') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_vuln_exceptions_org' AND object_id = OBJECT_ID('cyber.vuln_exceptions'))
    CREATE NONCLUSTERED INDEX ix_vuln_exceptions_org ON cyber.vuln_exceptions (organisation_id, is_deleted);
GO

IF OBJECT_ID('cyber.vulnerabilities') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_vulnerabilities_org' AND object_id = OBJECT_ID('cyber.vulnerabilities'))
    CREATE NONCLUSTERED INDEX ix_vulnerabilities_org ON cyber.vulnerabilities (organisation_id, is_deleted);
GO

IF OBJECT_ID('data_officer.catalog_lineage') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_catalog_lineage_org' AND object_id = OBJECT_ID('data_officer.catalog_lineage'))
    CREATE NONCLUSTERED INDEX ix_catalog_lineage_org ON data_officer.catalog_lineage (organisation_id, is_deleted);
GO

IF OBJECT_ID('data_officer.quality_run') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_quality_run_org' AND object_id = OBJECT_ID('data_officer.quality_run'))
    CREATE NONCLUSTERED INDEX ix_quality_run_org ON data_officer.quality_run (organisation_id, is_deleted);
GO

IF OBJECT_ID('evidence.evidence_links') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_evidence_links_org' AND object_id = OBJECT_ID('evidence.evidence_links'))
    CREATE NONCLUSTERED INDEX ix_evidence_links_org ON evidence.evidence_links (organisation_id, is_deleted);
GO

IF OBJECT_ID('evidence.evidence_requests') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_evidence_requests_org' AND object_id = OBJECT_ID('evidence.evidence_requests'))
    CREATE NONCLUSTERED INDEX ix_evidence_requests_org ON evidence.evidence_requests (organisation_id, is_deleted);
GO

IF OBJECT_ID('evidence.evidence_reviews') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_evidence_reviews_org' AND object_id = OBJECT_ID('evidence.evidence_reviews'))
    CREATE NONCLUSTERED INDEX ix_evidence_reviews_org ON evidence.evidence_reviews (organisation_id, is_deleted);
GO

IF OBJECT_ID('ndi.improvement_plan_item') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_improvement_plan_item_org' AND object_id = OBJECT_ID('ndi.improvement_plan_item'))
    CREATE NONCLUSTERED INDEX ix_improvement_plan_item_org ON ndi.improvement_plan_item (organisation_id, is_deleted);
GO

IF OBJECT_ID('ndi.operational_excellence_kpi') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_operational_excellence_kpi_org' AND object_id = OBJECT_ID('ndi.operational_excellence_kpi'))
    CREATE NONCLUSTERED INDEX ix_operational_excellence_kpi_org ON ndi.operational_excellence_kpi (organisation_id, is_deleted);
GO

IF OBJECT_ID('platform.attachments') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_attachments_org' AND object_id = OBJECT_ID('platform.attachments'))
    CREATE NONCLUSTERED INDEX ix_attachments_org ON platform.attachments (organisation_id, is_deleted);
GO

IF OBJECT_ID('platform.business_units') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_business_units_org' AND object_id = OBJECT_ID('platform.business_units'))
    CREATE NONCLUSTERED INDEX ix_business_units_org ON platform.business_units (organisation_id, is_deleted);
GO

IF OBJECT_ID('platform.departments') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_departments_org' AND object_id = OBJECT_ID('platform.departments'))
    CREATE NONCLUSTERED INDEX ix_departments_org ON platform.departments (organisation_id, is_deleted);
GO

IF OBJECT_ID('platform.notifications') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_notifications_org' AND object_id = OBJECT_ID('platform.notifications'))
    CREATE NONCLUSTERED INDEX ix_notifications_org ON platform.notifications (organisation_id);
GO

IF OBJECT_ID('privacy.consents') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_consents_org' AND object_id = OBJECT_ID('privacy.consents'))
    CREATE NONCLUSTERED INDEX ix_consents_org ON privacy.consents (organisation_id, is_deleted);
GO

IF OBJECT_ID('privacy.data_flows') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_data_flows_org' AND object_id = OBJECT_ID('privacy.data_flows'))
    CREATE NONCLUSTERED INDEX ix_data_flows_org ON privacy.data_flows (organisation_id, is_deleted);
GO

IF OBJECT_ID('privacy.data_subject_requests') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_data_subject_requests_org' AND object_id = OBJECT_ID('privacy.data_subject_requests'))
    CREATE NONCLUSTERED INDEX ix_data_subject_requests_org ON privacy.data_subject_requests (organisation_id, is_deleted);
GO

IF OBJECT_ID('privacy.privacy_actions') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_privacy_actions_org' AND object_id = OBJECT_ID('privacy.privacy_actions'))
    CREATE NONCLUSTERED INDEX ix_privacy_actions_org ON privacy.privacy_actions (organisation_id, is_deleted);
GO

IF OBJECT_ID('privacy.privacy_assessments') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_privacy_assessments_org' AND object_id = OBJECT_ID('privacy.privacy_assessments'))
    CREATE NONCLUSTERED INDEX ix_privacy_assessments_org ON privacy.privacy_assessments (organisation_id, is_deleted);
GO

IF OBJECT_ID('privacy.processing_activities') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_processing_activities_org' AND object_id = OBJECT_ID('privacy.processing_activities'))
    CREATE NONCLUSTERED INDEX ix_processing_activities_org ON privacy.processing_activities (organisation_id, is_deleted);
GO

IF OBJECT_ID('reporting.report_definitions') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_report_definitions_org' AND object_id = OBJECT_ID('reporting.report_definitions'))
    CREATE NONCLUSTERED INDEX ix_report_definitions_org ON reporting.report_definitions (organisation_id, is_deleted);
GO

IF OBJECT_ID('reporting.report_executions') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_report_executions_org' AND object_id = OBJECT_ID('reporting.report_executions'))
    CREATE NONCLUSTERED INDEX ix_report_executions_org ON reporting.report_executions (organisation_id);
GO

IF OBJECT_ID('reporting.report_runs') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_report_runs_org' AND object_id = OBJECT_ID('reporting.report_runs'))
    CREATE NONCLUSTERED INDEX ix_report_runs_org ON reporting.report_runs (organisation_id);
GO

IF OBJECT_ID('risk.bowtie_consequence') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bowtie_consequence_org' AND object_id = OBJECT_ID('risk.bowtie_consequence'))
    CREATE NONCLUSTERED INDEX ix_bowtie_consequence_org ON risk.bowtie_consequence (organisation_id, is_deleted);
GO

IF OBJECT_ID('risk.bowtie_mitigating_control') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bowtie_mitigating_control_org' AND object_id = OBJECT_ID('risk.bowtie_mitigating_control'))
    CREATE NONCLUSTERED INDEX ix_bowtie_mitigating_control_org ON risk.bowtie_mitigating_control (organisation_id, is_deleted);
GO

IF OBJECT_ID('risk.bowtie_preventive_control') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bowtie_preventive_control_org' AND object_id = OBJECT_ID('risk.bowtie_preventive_control'))
    CREATE NONCLUSTERED INDEX ix_bowtie_preventive_control_org ON risk.bowtie_preventive_control (organisation_id, is_deleted);
GO

IF OBJECT_ID('risk.bowtie_threat') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_bowtie_threat_org' AND object_id = OBJECT_ID('risk.bowtie_threat'))
    CREATE NONCLUSTERED INDEX ix_bowtie_threat_org ON risk.bowtie_threat (organisation_id, is_deleted);
GO

IF OBJECT_ID('risk.risk_categories') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_risk_categories_org' AND object_id = OBJECT_ID('risk.risk_categories'))
    CREATE NONCLUSTERED INDEX ix_risk_categories_org ON risk.risk_categories (organisation_id, is_deleted);
GO

IF OBJECT_ID('risk.risk_indicators') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_risk_indicators_org' AND object_id = OBJECT_ID('risk.risk_indicators'))
    CREATE NONCLUSTERED INDEX ix_risk_indicators_org ON risk.risk_indicators (organisation_id, is_deleted);
GO

IF OBJECT_ID('scm.contracts') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_contracts_org' AND object_id = OBJECT_ID('scm.contracts'))
    CREATE NONCLUSTERED INDEX ix_contracts_org ON scm.contracts (organisation_id, is_deleted);
GO

IF OBJECT_ID('scm.dependencies') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_dependencies_org' AND object_id = OBJECT_ID('scm.dependencies'))
    CREATE NONCLUSTERED INDEX ix_dependencies_org ON scm.dependencies (organisation_id, is_deleted);
GO

IF OBJECT_ID('scm.slas') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_slas_org' AND object_id = OBJECT_ID('scm.slas'))
    CREATE NONCLUSTERED INDEX ix_slas_org ON scm.slas (organisation_id, is_deleted);
GO

IF OBJECT_ID('scm.supplier_incidents') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_supplier_incidents_org' AND object_id = OBJECT_ID('scm.supplier_incidents'))
    CREATE NONCLUSTERED INDEX ix_supplier_incidents_org ON scm.supplier_incidents (organisation_id, is_deleted);
GO

IF OBJECT_ID('scm.vendor_assessments') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_vendor_assessments_org' AND object_id = OBJECT_ID('scm.vendor_assessments'))
    CREATE NONCLUSTERED INDEX ix_vendor_assessments_org ON scm.vendor_assessments (organisation_id, is_deleted);
GO

IF OBJECT_ID('scm.vendor_onboarding') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_vendor_onboarding_org' AND object_id = OBJECT_ID('scm.vendor_onboarding'))
    CREATE NONCLUSTERED INDEX ix_vendor_onboarding_org ON scm.vendor_onboarding (organisation_id, is_deleted);
GO

IF OBJECT_ID('socops.ir_playbook_step') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_ir_playbook_step_org' AND object_id = OBJECT_ID('socops.ir_playbook_step'))
    CREATE NONCLUSTERED INDEX ix_ir_playbook_step_org ON socops.ir_playbook_step (organisation_id, is_deleted);
GO

IF OBJECT_ID('tprm.questionnaire_questions') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_questionnaire_questions_org' AND object_id = OBJECT_ID('tprm.questionnaire_questions'))
    CREATE NONCLUSTERED INDEX ix_questionnaire_questions_org ON tprm.questionnaire_questions (organisation_id, is_deleted);
GO

IF OBJECT_ID('tprm.questionnaire_responses') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_questionnaire_responses_org' AND object_id = OBJECT_ID('tprm.questionnaire_responses'))
    CREATE NONCLUSTERED INDEX ix_questionnaire_responses_org ON tprm.questionnaire_responses (organisation_id, is_deleted);
GO

IF OBJECT_ID('workflow.instances') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_instances_org' AND object_id = OBJECT_ID('workflow.instances'))
    CREATE NONCLUSTERED INDEX ix_instances_org ON workflow.instances (organisation_id);
GO

IF OBJECT_ID('workflow.workflow_definitions') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_workflow_definitions_org' AND object_id = OBJECT_ID('workflow.workflow_definitions'))
    CREATE NONCLUSTERED INDEX ix_workflow_definitions_org ON workflow.workflow_definitions (organisation_id);
GO

IF OBJECT_ID('workflow.workflow_instances') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_workflow_instances_org' AND object_id = OBJECT_ID('workflow.workflow_instances'))
    CREATE NONCLUSTERED INDEX ix_workflow_instances_org ON workflow.workflow_instances (organisation_id);
GO
