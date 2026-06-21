-- ============================================================================
-- V229 — foreign-key + cyber status supporting indexes
--        (audit PERF-IX-005, PERF-IX-004)
--
-- PERF-IX-005: SQL Server does not auto-index FOREIGN KEY columns, so every
--   parent->child join and every parent-side cascade/integrity check on an
--   unindexed FK column scans the child table. This backfills a nonclustered
--   index LEADING WITH THE FK COLUMN for each FK column that is not already the
--   leading key of some index. Generated from the live schema (sys.foreign_key_
--   columns minus sys.index_columns key_ordinal=1); org-leading indexes from
--   V225/V228 do NOT back an FK join, so these are complementary, not duplicate.
--
-- PERF-IX-004: cyber.* tables are filtered by status / workflow_status in the
--   service-layer state machines but carry no supporting index (setup_cyber_db.py
--   created only the clustered PK). This adds a (organisation_id, <status>)
--   composite per tenant-scoped cyber table that has a status column.
--
-- All statements are idempotent (IF NOT EXISTS on the index name) and the
-- indexes are deliberately NON-filtered, matching V225/V227/V228 — this avoids
-- the filtered-index QUOTED_IDENTIFIER-ON requirement on every later DML.
--
-- SET QUOTED_IDENTIFIER ON is required at index-creation time because some target
-- tables (e.g. ai_governance.ml_model_drift_observation) carry computed columns —
-- SQL Server refuses to create ANY index on such a table unless QUOTED_IDENTIFIER
-- is ON (Msg 1934). Flyway's JDBC connection already defaults it ON; we set it
-- explicitly here so the script is also correct under sqlcmd (which defaults OFF).
-- The setting persists for the whole session, so it covers every batch below.
-- ============================================================================
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ---- PERF-IX-005 : foreign-key column indexes ----
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_ml_model_drift_observation_model_id' AND object_id=OBJECT_ID('ai_governance.ml_model_drift_observation'))
    CREATE NONCLUSTERED INDEX ix_ml_model_drift_observation_model_id ON ai_governance.ml_model_drift_observation (model_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_screening_hit_check_id' AND object_id=OBJECT_ID('aml.screening_hit'))
    CREATE NONCLUSTERED INDEX ix_screening_hit_check_id ON aml.screening_hit (check_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_dashboard_attestation_snapshot_id' AND object_id=OBJECT_ID('audit_committee.dashboard_attestation'))
    CREATE NONCLUSTERED INDEX ix_dashboard_attestation_snapshot_id ON audit_committee.dashboard_attestation (snapshot_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_ia_audit_universe_entry_plan_id' AND object_id=OBJECT_ID('audit_lc.ia_audit_universe_entry'))
    CREATE NONCLUSTERED INDEX ix_ia_audit_universe_entry_plan_id ON audit_lc.ia_audit_universe_entry (plan_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_ia_engagement_plan_id' AND object_id=OBJECT_ID('audit_lc.ia_engagement'))
    CREATE NONCLUSTERED INDEX ix_ia_engagement_plan_id ON audit_lc.ia_engagement (plan_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_ia_engagement_universe_entry_id' AND object_id=OBJECT_ID('audit_lc.ia_engagement'))
    CREATE NONCLUSTERED INDEX ix_ia_engagement_universe_entry_id ON audit_lc.ia_engagement (universe_entry_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_ia_engagement_milestone_engagement_id' AND object_id=OBJECT_ID('audit_lc.ia_engagement_milestone'))
    CREATE NONCLUSTERED INDEX ix_ia_engagement_milestone_engagement_id ON audit_lc.ia_engagement_milestone (engagement_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_bia_records_process_id' AND object_id=OBJECT_ID('bcm.bia_records'))
    CREATE NONCLUSTERED INDEX ix_bia_records_process_id ON bcm.bia_records (process_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_crisis_comms_distributions_audience_id' AND object_id=OBJECT_ID('bcm.crisis_comms_distributions'))
    CREATE NONCLUSTERED INDEX ix_crisis_comms_distributions_audience_id ON bcm.crisis_comms_distributions (audience_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_crisis_comms_distributions_template_id' AND object_id=OBJECT_ID('bcm.crisis_comms_distributions'))
    CREATE NONCLUSTERED INDEX ix_crisis_comms_distributions_template_id ON bcm.crisis_comms_distributions (template_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_exercise_observation_exercise_run_id' AND object_id=OBJECT_ID('bcm.exercise_observation'))
    CREATE NONCLUSTERED INDEX ix_exercise_observation_exercise_run_id ON bcm.exercise_observation (exercise_run_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_exercise_observation_related_inject_id' AND object_id=OBJECT_ID('bcm.exercise_observation'))
    CREATE NONCLUSTERED INDEX ix_exercise_observation_related_inject_id ON bcm.exercise_observation (related_inject_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_exercise_run_exercise_id' AND object_id=OBJECT_ID('bcm.exercise_run'))
    CREATE NONCLUSTERED INDEX ix_exercise_run_exercise_id ON bcm.exercise_run (exercise_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_exercise_run_inject_exercise_run_id' AND object_id=OBJECT_ID('bcm.exercise_run_inject'))
    CREATE NONCLUSTERED INDEX ix_exercise_run_inject_exercise_run_id ON bcm.exercise_run_inject (exercise_run_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_exercise_run_inject_source_inject_id' AND object_id=OBJECT_ID('bcm.exercise_run_inject'))
    CREATE NONCLUSTERED INDEX ix_exercise_run_inject_source_inject_id ON bcm.exercise_run_inject (source_inject_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_exercise_run_response_run_inject_id' AND object_id=OBJECT_ID('bcm.exercise_run_response'))
    CREATE NONCLUSTERED INDEX ix_exercise_run_response_run_inject_id ON bcm.exercise_run_response (run_inject_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_exercises_plan_id' AND object_id=OBJECT_ID('bcm.exercises'))
    CREATE NONCLUSTERED INDEX ix_exercises_plan_id ON bcm.exercises (plan_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_mtpd_inheritance_finding_assessment_id' AND object_id=OBJECT_ID('bcm.mtpd_inheritance_finding'))
    CREATE NONCLUSTERED INDEX ix_mtpd_inheritance_finding_assessment_id ON bcm.mtpd_inheritance_finding (assessment_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_process_dependencies_process_id' AND object_id=OBJECT_ID('bcm.process_dependencies'))
    CREATE NONCLUSTERED INDEX ix_process_dependencies_process_id ON bcm.process_dependencies (process_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_strategy_roi_analysis_bia_quantitative_id' AND object_id=OBJECT_ID('bcm.strategy_roi_analysis'))
    CREATE NONCLUSTERED INDEX ix_strategy_roi_analysis_bia_quantitative_id ON bcm.strategy_roi_analysis (bia_quantitative_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_control_test_run_test_definition_id' AND object_id=OBJECT_ID('ccm.control_test_run'))
    CREATE NONCLUSTERED INDEX ix_control_test_run_test_definition_id ON ccm.control_test_run (test_definition_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_compliance_assessments_framework_id' AND object_id=OBJECT_ID('compliance.compliance_assessments'))
    CREATE NONCLUSTERED INDEX ix_compliance_assessments_framework_id ON compliance.compliance_assessments (framework_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_compliance_findings_assessment_id' AND object_id=OBJECT_ID('compliance.compliance_findings'))
    CREATE NONCLUSTERED INDEX ix_compliance_findings_assessment_id ON compliance.compliance_findings (assessment_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_obligation_attestation_campaign_id' AND object_id=OBJECT_ID('compliance.obligation_attestation'))
    CREATE NONCLUSTERED INDEX ix_obligation_attestation_campaign_id ON compliance.obligation_attestation (campaign_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_attack_surface_finding_asset_id' AND object_id=OBJECT_ID('cyber.attack_surface_finding'))
    CREATE NONCLUSTERED INDEX ix_attack_surface_finding_asset_id ON cyber.attack_surface_finding (asset_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_aml_alerts_framework_id' AND object_id=OBJECT_ID('ethics.aml_alerts'))
    CREATE NONCLUSTERED INDEX ix_aml_alerts_framework_id ON ethics.aml_alerts (framework_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_aml_alerts_subject_id' AND object_id=OBJECT_ID('ethics.aml_alerts'))
    CREATE NONCLUSTERED INDEX ix_aml_alerts_subject_id ON ethics.aml_alerts (subject_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_cases_framework_id' AND object_id=OBJECT_ID('ethics.cases'))
    CREATE NONCLUSTERED INDEX ix_cases_framework_id ON ethics.cases (framework_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_coi_declaration_campaign_id' AND object_id=OBJECT_ID('ethics.coi_declaration'))
    CREATE NONCLUSTERED INDEX ix_coi_declaration_campaign_id ON ethics.coi_declaration (campaign_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_coi_recusal_event_declaration_id' AND object_id=OBJECT_ID('ethics.coi_recusal_event'))
    CREATE NONCLUSTERED INDEX ix_coi_recusal_event_declaration_id ON ethics.coi_recusal_event (declaration_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_disclosures_framework_id' AND object_id=OBJECT_ID('ethics.disclosures'))
    CREATE NONCLUSTERED INDEX ix_disclosures_framework_id ON ethics.disclosures (framework_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_kyc_subjects_framework_id' AND object_id=OBJECT_ID('ethics.kyc_subjects'))
    CREATE NONCLUSTERED INDEX ix_kyc_subjects_framework_id ON ethics.kyc_subjects (framework_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_whistleblower_case_update_case_id' AND object_id=OBJECT_ID('ethics.whistleblower_case_update'))
    CREATE NONCLUSTERED INDEX ix_whistleblower_case_update_case_id ON ethics.whistleblower_case_update (case_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_whistleblower_retaliation_complaint_related_case_id' AND object_id=OBJECT_ID('ethics.whistleblower_retaliation_complaint'))
    CREATE NONCLUSTERED INDEX ix_whistleblower_retaliation_complaint_related_case_id ON ethics.whistleblower_retaliation_complaint (related_case_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_evidence_requests_sub_control_id' AND object_id=OBJECT_ID('evidence.evidence_requests'))
    CREATE NONCLUSTERED INDEX ix_evidence_requests_sub_control_id ON evidence.evidence_requests (sub_control_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_evidence_reviews_evidence_id' AND object_id=OBJECT_ID('evidence.evidence_reviews'))
    CREATE NONCLUSTERED INDEX ix_evidence_reviews_evidence_id ON evidence.evidence_reviews (evidence_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_fraud_case_typology_id' AND object_id=OBJECT_ID('fraud.fraud_case'))
    CREATE NONCLUSTERED INDEX ix_fraud_case_typology_id ON fraud.fraud_case (typology_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_fraud_case_update_case_id' AND object_id=OBJECT_ID('fraud.fraud_case_update'))
    CREATE NONCLUSTERED INDEX ix_fraud_case_update_case_id ON fraud.fraud_case_update (case_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_role_permissions_permission_id' AND object_id=OBJECT_ID('iam.role_permissions'))
    CREATE NONCLUSTERED INDEX ix_role_permissions_permission_id ON iam.role_permissions (permission_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_user_roles_role_id' AND object_id=OBJECT_ID('iam.user_roles'))
    CREATE NONCLUSTERED INDEX ix_user_roles_role_id ON iam.user_roles (role_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_opres_assessment_cell_pillar_id' AND object_id=OBJECT_ID('opres.opres_assessment_cell'))
    CREATE NONCLUSTERED INDEX ix_opres_assessment_cell_pillar_id ON opres.opres_assessment_cell (pillar_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_audit_log_parent_audit_id' AND object_id=OBJECT_ID('platform.audit_log'))
    CREATE NONCLUSTERED INDEX ix_audit_log_parent_audit_id ON platform.audit_log (parent_audit_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_authority_documents_superseded_by_id' AND object_id=OBJECT_ID('platform.authority_documents'))
    CREATE NONCLUSTERED INDEX ix_authority_documents_superseded_by_id ON platform.authority_documents (superseded_by_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_business_units_parent_id' AND object_id=OBJECT_ID('platform.business_units'))
    CREATE NONCLUSTERED INDEX ix_business_units_parent_id ON platform.business_units (parent_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_crowdstrike_action_log_security_event_id' AND object_id=OBJECT_ID('platform.crowdstrike_action_log'))
    CREATE NONCLUSTERED INDEX ix_crowdstrike_action_log_security_event_id ON platform.crowdstrike_action_log (security_event_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_crowdstrike_detection_log_security_event_id' AND object_id=OBJECT_ID('platform.crowdstrike_detection_log'))
    CREATE NONCLUSTERED INDEX ix_crowdstrike_detection_log_security_event_id ON platform.crowdstrike_detection_log (security_event_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_subprocessor_parent_processor_id' AND object_id=OBJECT_ID('privacy.subprocessor'))
    CREATE NONCLUSTERED INDEX ix_subprocessor_parent_processor_id ON privacy.subprocessor (parent_processor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_subprocessor_event_subprocessor_id' AND object_id=OBJECT_ID('privacy.subprocessor_event'))
    CREATE NONCLUSTERED INDEX ix_subprocessor_event_subprocessor_id ON privacy.subprocessor_event (subprocessor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_bowtie_mitigating_control_bowtie_id' AND object_id=OBJECT_ID('risk.bowtie_mitigating_control'))
    CREATE NONCLUSTERED INDEX ix_bowtie_mitigating_control_bowtie_id ON risk.bowtie_mitigating_control (bowtie_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_bowtie_preventive_control_bowtie_id' AND object_id=OBJECT_ID('risk.bowtie_preventive_control'))
    CREATE NONCLUSTERED INDEX ix_bowtie_preventive_control_bowtie_id ON risk.bowtie_preventive_control (bowtie_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_kri_breach_kri_id' AND object_id=OBJECT_ID('risk.kri_breach'))
    CREATE NONCLUSTERED INDEX ix_kri_breach_kri_id ON risk.kri_breach (kri_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_kri_breach_measurement_id' AND object_id=OBJECT_ID('risk.kri_breach'))
    CREATE NONCLUSTERED INDEX ix_kri_breach_measurement_id ON risk.kri_breach (measurement_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_kri_breach_events_reading_id' AND object_id=OBJECT_ID('risk.kri_breach_events'))
    CREATE NONCLUSTERED INDEX ix_kri_breach_events_reading_id ON risk.kri_breach_events (reading_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_kri_measurement_kri_id' AND object_id=OBJECT_ID('risk.kri_measurement'))
    CREATE NONCLUSTERED INDEX ix_kri_measurement_kri_id ON risk.kri_measurement (kri_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_risk_assessments_risk_id' AND object_id=OBJECT_ID('risk.risk_assessments'))
    CREATE NONCLUSTERED INDEX ix_risk_assessments_risk_id ON risk.risk_assessments (risk_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_risk_categories_parent_id' AND object_id=OBJECT_ID('risk.risk_categories'))
    CREATE NONCLUSTERED INDEX ix_risk_categories_parent_id ON risk.risk_categories (parent_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_risk_indicators_risk_id' AND object_id=OBJECT_ID('risk.risk_indicators'))
    CREATE NONCLUSTERED INDEX ix_risk_indicators_risk_id ON risk.risk_indicators (risk_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_risk_treatments_risk_id' AND object_id=OBJECT_ID('risk.risk_treatments'))
    CREATE NONCLUSTERED INDEX ix_risk_treatments_risk_id ON risk.risk_treatments (risk_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_risks_category_id' AND object_id=OBJECT_ID('risk.risks'))
    CREATE NONCLUSTERED INDEX ix_risks_category_id ON risk.risks (category_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_dependencies_alternative_vendor_id' AND object_id=OBJECT_ID('scm.dependencies'))
    CREATE NONCLUSTERED INDEX ix_dependencies_alternative_vendor_id ON scm.dependencies (alternative_vendor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_slas_contract_id' AND object_id=OBJECT_ID('scm.slas'))
    CREATE NONCLUSTERED INDEX ix_slas_contract_id ON scm.slas (contract_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_sustainability_report_reporting_year_id' AND object_id=OBJECT_ID('sust.sustainability_report'))
    CREATE NONCLUSTERED INDEX ix_sustainability_report_reporting_year_id ON sust.sustainability_report (reporting_year_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_outsourcing_arrangement_primary_vendor_id' AND object_id=OBJECT_ID('tprm.outsourcing_arrangement'))
    CREATE NONCLUSTERED INDEX ix_outsourcing_arrangement_primary_vendor_id ON tprm.outsourcing_arrangement (primary_vendor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_outsourcing_due_diligence_arrangement_id' AND object_id=OBJECT_ID('tprm.outsourcing_due_diligence'))
    CREATE NONCLUSTERED INDEX ix_outsourcing_due_diligence_arrangement_id ON tprm.outsourcing_due_diligence (arrangement_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_outsourcing_exit_plan_arrangement_id' AND object_id=OBJECT_ID('tprm.outsourcing_exit_plan'))
    CREATE NONCLUSTERED INDEX ix_outsourcing_exit_plan_arrangement_id ON tprm.outsourcing_exit_plan (arrangement_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_outsourcing_monitoring_event_arrangement_id' AND object_id=OBJECT_ID('tprm.outsourcing_monitoring_event'))
    CREATE NONCLUSTERED INDEX ix_outsourcing_monitoring_event_arrangement_id ON tprm.outsourcing_monitoring_event (arrangement_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_vendor_bcp_attestation_vendor_id' AND object_id=OBJECT_ID('tprm.vendor_bcp_attestation'))
    CREATE NONCLUSTERED INDEX ix_vendor_bcp_attestation_vendor_id ON tprm.vendor_bcp_attestation (vendor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_approval_chain_action_step_id' AND object_id=OBJECT_ID('workflow.approval_chain_action'))
    CREATE NONCLUSTERED INDEX ix_approval_chain_action_step_id ON workflow.approval_chain_action (step_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_approval_chain_instance_template_id' AND object_id=OBJECT_ID('workflow.approval_chain_instance'))
    CREATE NONCLUSTERED INDEX ix_approval_chain_instance_template_id ON workflow.approval_chain_instance (template_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_instances_current_state_id' AND object_id=OBJECT_ID('workflow.instances'))
    CREATE NONCLUSTERED INDEX ix_instances_current_state_id ON workflow.instances (current_state_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_instances_definition_id' AND object_id=OBJECT_ID('workflow.instances'))
    CREATE NONCLUSTERED INDEX ix_instances_definition_id ON workflow.instances (definition_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_transitions_from_state_id' AND object_id=OBJECT_ID('workflow.transitions'))
    CREATE NONCLUSTERED INDEX ix_transitions_from_state_id ON workflow.transitions (from_state_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_transitions_to_state_id' AND object_id=OBJECT_ID('workflow.transitions'))
    CREATE NONCLUSTERED INDEX ix_transitions_to_state_id ON workflow.transitions (to_state_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_workflow_instances_definition_id' AND object_id=OBJECT_ID('workflow.workflow_instances'))
    CREATE NONCLUSTERED INDEX ix_workflow_instances_definition_id ON workflow.workflow_instances (definition_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_workflow_tasks_instance_id' AND object_id=OBJECT_ID('workflow.workflow_tasks'))
    CREATE NONCLUSTERED INDEX ix_workflow_tasks_instance_id ON workflow.workflow_tasks (instance_id);
GO

-- ---- PERF-IX-004 : cyber status / workflow_status indexes ----
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_attack_surface_finding_org_status' AND object_id=OBJECT_ID('cyber.attack_surface_finding'))
    CREATE NONCLUSTERED INDEX ix_attack_surface_finding_org_status ON cyber.attack_surface_finding (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_attack_surface_shadow_it_org_status' AND object_id=OBJECT_ID('cyber.attack_surface_shadow_it'))
    CREATE NONCLUSTERED INDEX ix_attack_surface_shadow_it_org_status ON cyber.attack_surface_shadow_it (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_cloud_services_org_status' AND object_id=OBJECT_ID('cyber.cloud_services'))
    CREATE NONCLUSTERED INDEX ix_cloud_services_org_status ON cyber.cloud_services (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_control_assessments_org_status' AND object_id=OBJECT_ID('cyber.control_assessments'))
    CREATE NONCLUSTERED INDEX ix_control_assessments_org_status ON cyber.control_assessments (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_cyber_action_plans_org_wfstatus' AND object_id=OBJECT_ID('cyber.cyber_action_plans'))
    CREATE NONCLUSTERED INDEX ix_cyber_action_plans_org_wfstatus ON cyber.cyber_action_plans (organisation_id, workflow_status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_cyber_assets_org_status' AND object_id=OBJECT_ID('cyber.cyber_assets'))
    CREATE NONCLUSTERED INDEX ix_cyber_assets_org_status ON cyber.cyber_assets (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_cyber_controls_org_status' AND object_id=OBJECT_ID('cyber.cyber_controls'))
    CREATE NONCLUSTERED INDEX ix_cyber_controls_org_status ON cyber.cyber_controls (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_cyber_evidence_org_status' AND object_id=OBJECT_ID('cyber.cyber_evidence'))
    CREATE NONCLUSTERED INDEX ix_cyber_evidence_org_status ON cyber.cyber_evidence (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_cyber_incidents_org_wfstatus' AND object_id=OBJECT_ID('cyber.cyber_incidents'))
    CREATE NONCLUSTERED INDEX ix_cyber_incidents_org_wfstatus ON cyber.cyber_incidents (organisation_id, workflow_status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_incidents_org_status' AND object_id=OBJECT_ID('cyber.incidents'))
    CREATE NONCLUSTERED INDEX ix_incidents_org_status ON cyber.incidents (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_root_cause_analyses_org_status' AND object_id=OBJECT_ID('cyber.root_cause_analyses'))
    CREATE NONCLUSTERED INDEX ix_root_cause_analyses_org_status ON cyber.root_cause_analyses (organisation_id, status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_vuln_exceptions_org_wfstatus' AND object_id=OBJECT_ID('cyber.vuln_exceptions'))
    CREATE NONCLUSTERED INDEX ix_vuln_exceptions_org_wfstatus ON cyber.vuln_exceptions (organisation_id, workflow_status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_vulnerabilities_org_wfstatus' AND object_id=OBJECT_ID('cyber.vulnerabilities'))
    CREATE NONCLUSTERED INDEX ix_vulnerabilities_org_wfstatus ON cyber.vulnerabilities (organisation_id, workflow_status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_vulnerability_sla_finding_org_status' AND object_id=OBJECT_ID('cyber.vulnerability_sla_finding'))
    CREATE NONCLUSTERED INDEX ix_vulnerability_sla_finding_org_status ON cyber.vulnerability_sla_finding (organisation_id, status);
GO

