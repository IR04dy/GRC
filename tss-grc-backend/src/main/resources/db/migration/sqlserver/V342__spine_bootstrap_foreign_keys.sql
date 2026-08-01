-- V342: F2-70 — foreign keys for the tables created by V341.
--
-- GENERATED. Do not hand-edit; re-run the generator (see V341).
--
-- Separate from the CREATE TABLEs so declaration order never matters: every table on both sides
-- of every constraint below exists by the time this file runs, including the ones that reference
-- each other. Emitting FKs inline would make this migration order-sensitive for no benefit.
--
-- Idempotent: safe to run twice.


-- compliance.controls -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('compliance.controls', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_controls_framework' AND parent_object_id = OBJECT_ID('compliance.controls'))
    ALTER TABLE [compliance].[controls] ADD CONSTRAINT [FK_controls_framework] FOREIGN KEY ([framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- compliance.findings -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('compliance.findings', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_findings_framework' AND parent_object_id = OBJECT_ID('compliance.findings'))
    ALTER TABLE [compliance].[findings] ADD CONSTRAINT [FK_findings_framework] FOREIGN KEY ([framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- compliance.policy_framework_mappings -> compliance.frameworks
IF OBJECT_ID('compliance.policy_framework_mappings', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_pfm_framework' AND parent_object_id = OBJECT_ID('compliance.policy_framework_mappings'))
    ALTER TABLE [compliance].[policy_framework_mappings] ADD CONSTRAINT [FK_pfm_framework] FOREIGN KEY ([framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- compliance.policy_framework_mappings -> compliance.policies
IF OBJECT_ID('compliance.policy_framework_mappings', 'U') IS NOT NULL AND OBJECT_ID('compliance.policies', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_pfm_policy' AND parent_object_id = OBJECT_ID('compliance.policy_framework_mappings'))
    ALTER TABLE [compliance].[policy_framework_mappings] ADD CONSTRAINT [FK_pfm_policy] FOREIGN KEY ([policy_id]) REFERENCES [compliance].[policies] ([id]);
GO
-- ethics.aml_alerts -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('ethics.aml_alerts', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_aml_framework' AND parent_object_id = OBJECT_ID('ethics.aml_alerts'))
    ALTER TABLE [ethics].[aml_alerts] ADD CONSTRAINT [FK_aml_framework] FOREIGN KEY ([framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- ethics.cases -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('ethics.cases', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ethics_case_framework' AND parent_object_id = OBJECT_ID('ethics.cases'))
    ALTER TABLE [ethics].[cases] ADD CONSTRAINT [FK_ethics_case_framework] FOREIGN KEY ([framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- ethics.disclosures -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('ethics.disclosures', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_disc_framework' AND parent_object_id = OBJECT_ID('ethics.disclosures'))
    ALTER TABLE [ethics].[disclosures] ADD CONSTRAINT [FK_disc_framework] FOREIGN KEY ([framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- ethics.kyc_subjects -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('ethics.kyc_subjects', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_kyc_subj_framework' AND parent_object_id = OBJECT_ID('ethics.kyc_subjects'))
    ALTER TABLE [ethics].[kyc_subjects] ADD CONSTRAINT [FK_kyc_subj_framework] FOREIGN KEY ([framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- evidence.evidence_versions -> evidence.evidence_items
IF OBJECT_ID('evidence.evidence_versions', 'U') IS NOT NULL AND OBJECT_ID('evidence.evidence_items', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_evver_evidence' AND parent_object_id = OBJECT_ID('evidence.evidence_versions'))
    ALTER TABLE [evidence].[evidence_versions] ADD CONSTRAINT [FK_evver_evidence] FOREIGN KEY ([evidence_id]) REFERENCES [evidence].[evidence_items] ([id]);
GO
-- fraud.fraud_case -> fraud.fraud_typology
IF OBJECT_ID('fraud.fraud_case', 'U') IS NOT NULL AND OBJECT_ID('fraud.fraud_typology', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_fraud_case_typology' AND parent_object_id = OBJECT_ID('fraud.fraud_case'))
    ALTER TABLE [fraud].[fraud_case] ADD CONSTRAINT [fk_fraud_case_typology] FOREIGN KEY ([typology_id]) REFERENCES [fraud].[fraud_typology] ([id]);
GO
-- fraud.fraud_case_update -> fraud.fraud_case
IF OBJECT_ID('fraud.fraud_case_update', 'U') IS NOT NULL AND OBJECT_ID('fraud.fraud_case', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_fraud_upd_case' AND parent_object_id = OBJECT_ID('fraud.fraud_case_update'))
    ALTER TABLE [fraud].[fraud_case_update] ADD CONSTRAINT [fk_fraud_upd_case] FOREIGN KEY ([case_id]) REFERENCES [fraud].[fraud_case] ([id]);
GO
-- opres.opres_assessment_cell -> opres.opres_assessment
IF OBJECT_ID('opres.opres_assessment_cell', 'U') IS NOT NULL AND OBJECT_ID('opres.opres_assessment', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_opres_c_assessment' AND parent_object_id = OBJECT_ID('opres.opres_assessment_cell'))
    ALTER TABLE [opres].[opres_assessment_cell] ADD CONSTRAINT [fk_opres_c_assessment] FOREIGN KEY ([assessment_id]) REFERENCES [opres].[opres_assessment] ([id]);
GO
-- opres.opres_assessment_cell -> opres.opres_pillar
IF OBJECT_ID('opres.opres_assessment_cell', 'U') IS NOT NULL AND OBJECT_ID('opres.opres_pillar', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_opres_c_pillar' AND parent_object_id = OBJECT_ID('opres.opres_assessment_cell'))
    ALTER TABLE [opres].[opres_assessment_cell] ADD CONSTRAINT [fk_opres_c_pillar] FOREIGN KEY ([pillar_id]) REFERENCES [opres].[opres_pillar] ([id]);
GO
-- privacy.dpia -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('privacy.dpia', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_dpia_framework' AND parent_object_id = OBJECT_ID('privacy.dpia'))
    ALTER TABLE [privacy].[dpia] ADD CONSTRAINT [FK_dpia_framework] FOREIGN KEY ([framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- risk.risks -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('risk.risks', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_risk_compfw' AND parent_object_id = OBJECT_ID('risk.risks'))
    ALTER TABLE [risk].[risks] ADD CONSTRAINT [FK_risk_compfw] FOREIGN KEY ([compliance_framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- risk.risks -> compliance.frameworks   [INBOUND: parent is created by an existing migration, this FK existed only in the bootstrap]
IF OBJECT_ID('risk.risks', 'U') IS NOT NULL AND OBJECT_ID('compliance.frameworks', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_risk_riskfw' AND parent_object_id = OBJECT_ID('risk.risks'))
    ALTER TABLE [risk].[risks] ADD CONSTRAINT [FK_risk_riskfw] FOREIGN KEY ([risk_framework_id]) REFERENCES [compliance].[frameworks] ([id]);
GO
-- Verification: every FK whose BOTH endpoints exist must now be present.
-- Counted by NAME, because the parents are no longer all spine tables and
-- counting by parent table would silently miss the inbound half. Conditioned
-- on endpoint existence, because on a database built from this pair of files
-- alone the inbound parents legitimately do not exist yet — asserting a flat
-- count would fail the very empty-database build this migration exists for.
DECLARE @expected INT = (SELECT COUNT(*) FROM (VALUES
        ('FK_controls_framework', 'compliance.controls', 'compliance.frameworks'),
        ('FK_findings_framework', 'compliance.findings', 'compliance.frameworks'),
        ('FK_pfm_framework', 'compliance.policy_framework_mappings', 'compliance.frameworks'),
        ('FK_pfm_policy', 'compliance.policy_framework_mappings', 'compliance.policies'),
        ('FK_aml_framework', 'ethics.aml_alerts', 'compliance.frameworks'),
        ('FK_ethics_case_framework', 'ethics.cases', 'compliance.frameworks'),
        ('FK_disc_framework', 'ethics.disclosures', 'compliance.frameworks'),
        ('FK_kyc_subj_framework', 'ethics.kyc_subjects', 'compliance.frameworks'),
        ('FK_evver_evidence', 'evidence.evidence_versions', 'evidence.evidence_items'),
        ('fk_fraud_case_typology', 'fraud.fraud_case', 'fraud.fraud_typology'),
        ('fk_fraud_upd_case', 'fraud.fraud_case_update', 'fraud.fraud_case'),
        ('fk_opres_c_assessment', 'opres.opres_assessment_cell', 'opres.opres_assessment'),
        ('fk_opres_c_pillar', 'opres.opres_assessment_cell', 'opres.opres_pillar'),
        ('FK_dpia_framework', 'privacy.dpia', 'compliance.frameworks'),
        ('FK_risk_compfw', 'risk.risks', 'compliance.frameworks'),
        ('FK_risk_riskfw', 'risk.risks', 'compliance.frameworks')
    ) AS x(fk, parent, ref)
    WHERE OBJECT_ID(x.parent, 'U') IS NOT NULL
      AND OBJECT_ID(x.ref, 'U') IS NOT NULL);
DECLARE @actual INT = (SELECT COUNT(*) FROM sys.foreign_keys
    WHERE name IN ('FK_controls_framework', 'FK_findings_framework', 'FK_pfm_framework', 'FK_pfm_policy', 'FK_aml_framework', 'FK_ethics_case_framework', 'FK_disc_framework', 'FK_kyc_subj_framework', 'FK_evver_evidence', 'fk_fraud_case_typology', 'fk_fraud_upd_case', 'fk_opres_c_assessment', 'fk_opres_c_pillar', 'FK_dpia_framework', 'FK_risk_compfw', 'FK_risk_riskfw'));
IF @actual < @expected
    THROW 51342, 'V342: a spine foreign key is missing though both its tables exist', 1;
GO
