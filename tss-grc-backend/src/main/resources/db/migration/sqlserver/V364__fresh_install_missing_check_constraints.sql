-- V364 — F2-153(1): 29 of the 32 value-domain CHECK constraints a fresh install does not enforce.
--
-- THE DEFECT. `schema_convergence.py` compares a Flyway-built database against Python-bootstrapped
-- dev. Extended to constraint bodies on 2026-08-07 it found dev holding **416** user-named CHECK
-- constraints and a fresh install **385**. Thirty-two are missing, and **none of them is explained by
-- the chain's stopping point** — they are not created by a later migration, they are not created by
-- any migration. A fresh install therefore accepts values the application's own enums do not contain:
-- an SCM vendor with `criticality = 'BANANA'`, a BCM exercise with `status = 'PENDING'`, a workflow
-- instance in a state the engine cannot process.
--
-- WHY THE MIGRATION SET NEVER HAD THEM. They come from the Python bootstrap (`setup_*_db.py`), which
-- creates each table together with its constraints, while the migration set's equivalent CREATE
-- omitted them. Same origin as F2-140's three tables and F2-146's 27 columns: dev is built by one
-- authority and a customer's schema by another, and only what someone thought to write twice exists
-- in both.
--
-- ⚠ THREE OF THE THIRTY-TWO HAVE A DIFFERENT AND MORE INSTRUCTIVE CAUSE. `V1__core_schemas.sql:745`
-- creates `cyber.incidents` with `severity NVARCHAR(20) NOT NULL` and no value CHECKs.
-- `V4__incident_module.sql:16` creates it AGAIN — wrapped in
-- `IF NOT EXISTS (… TABLE_NAME = 'incidents')` — and *that* version carries CK_incidents_severity,
-- _status and _category. On a fresh install V1 wins, V4's guard skips, and every inline CONSTRAINT in
-- the skipped CREATE goes with it. **An idempotency guard protects the statement and discards
-- everything the statement carried.** Any guarded re-CREATE of an existing table does this.
--
-- ⚠ MEASURED BEFORE WRITING THIS FILE (2026-08-07), and the measurement is what makes it safe:
--
--     dev   TSS_GRC             416 user-named CHECKs — holds all 32
--     prod  TSS_GRC_Deployment  398 user-named CHECKs — holds all 32
--     built TSS_GRC_FULL_CHAIN  385 user-named CHECKs — holds none of the 32
--
-- So this migration is a **no-op in both real databases** (every statement is guarded on the
-- constraint's absence) and changes only a fresh install. And the audit that matters was run against
-- the one database actually in that state: **0 of the 32 are violated by the built database's data**,
-- every parent table is present, and the five `cyber.incidents` and eight `scm.vendors` rows it
-- carries satisfy their constraints. No `WITH NOCHECK` anywhere — SQL Server verifies the existing
-- rows, which is the point.
--
-- ⚠ AND A CORRECTION TO F2-153's OWN REMEDY NOTE. That row said the 29 non-guard-skipped CHECKs
-- "need a data audit first", reasoning that adding a constraint over violating data fails. True in
-- general, and irrelevant here: the only databases that would gain a constraint are ones that lack
-- it, and prod does not lack any of them. Generalising the hazard without asking which database
-- would be in that state made a mechanical change look blocked.
--
-- ⚠ THE DEFINITIONS ARE DEV'S OWN, read from `sys.check_constraints.definition` and not composed by
-- hand. A hand-written CHECK that differs from dev's would be a new divergence wearing a fix's
-- clothes — which is the whole failure mode this file exists to close.
--
-- SCOPE. CHECK constraints only. The 53 missing FOREIGN KEYS are the next increment: they need
-- column lists and referential actions extracted, and an index decision, so they are a separate
-- reviewed change rather than a bigger generated one. DEFAULT constraints are deliberately out of
-- scope — 1,910 of dev's 2,259 are system-named, so there is nothing stable to compare or recreate.
--
--
-- ⚠ THREE OF THE THIRTY-TWO ARE DELIBERATELY NOT SHIPPED, and my own gate is why. The first draft of
-- this file constrained all 32 and `SchemaContractRatchetTest.
-- noMigrationAboveTheBaselineWritesToATableTheApplicationCannotRead` failed it: `risk.risk_indicators`
-- (LEGACY), `workflow.workflow_instances` and `workflow.workflow_tasks` (SEED_ONLY) are three of
-- F2-141's 36 tables with **no JPA mapping and no Java reference**. That gate's message is the
-- instruction — *"if the data matters the table needs a mapping first; if it does not, do not ship
-- it"* — and constraining a table nothing reads protects nothing.
--
-- ⚠ The workflow pair is a NEAR-NAME trap, and the register is right. The live engine maps
-- `workflow.instances`, `workflow.definitions`, `workflow.states`, `workflow.transitions` and
-- `workflow.action_log`; the dead tables are `workflow.workflow_instances` and
-- `workflow.workflow_tasks` — the same schema with a `workflow_` prefix. Three generations exist
-- (`platform.workflow_*` dropped by V114, `workflow.workflow_*` dead, `workflow.*` live), which is
-- F2-148's shape: a predecessor sharing its successor's name. The exclusion is DERIVED from the
-- committed register rather than hand-listed, so this migration and that gate cannot disagree.
--
-- So: 29 constraints, not 32.
--
-- REVERSE RECIPE: for each constraint below,
--   ALTER TABLE <schema>.<table> DROP CONSTRAINT <name>;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'bcm' AND t.name = 'exercises' AND cc.name = 'CK_bcm_exer_score')
    ALTER TABLE bcm.exercises ADD CONSTRAINT CK_bcm_exer_score CHECK ([score] IS NULL OR [score]>=(0) AND [score]<=(100));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'bcm' AND t.name = 'exercises' AND cc.name = 'CK_bcm_exer_status')
    ALTER TABLE bcm.exercises ADD CONSTRAINT CK_bcm_exer_status CHECK ([status]='CANCELLED' OR [status]='COMPLETED' OR [status]='IN_PROGRESS' OR [status]='SCHEDULED');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'bcm' AND t.name = 'exercises' AND cc.name = 'CK_bcm_exer_type')
    ALTER TABLE bcm.exercises ADD CONSTRAINT CK_bcm_exer_type CHECK ([exercise_type]='TECHNICAL_RECOVERY' OR [exercise_type]='COMMUNICATION' OR [exercise_type]='FULL' OR [exercise_type]='FUNCTIONAL' OR [exercise_type]='TABLETOP');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'bcm' AND t.name = 'process_dependencies' AND cc.name = 'CK_pdep_no_self')
    ALTER TABLE bcm.process_dependencies ADD CONSTRAINT CK_pdep_no_self CHECK ([process_id]<>[depends_on_process_id]);
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'cyber' AND t.name = 'incidents' AND cc.name = 'CK_incidents_category')
    ALTER TABLE cyber.incidents ADD CONSTRAINT CK_incidents_category CHECK ([category] IS NULL OR ([category]='OTHER' OR [category]='ACCESS_CONTROL' OR [category]='INFRASTRUCTURE' OR [category]='DATA_BREACH' OR [category]='CYBER'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'cyber' AND t.name = 'incidents' AND cc.name = 'CK_incidents_severity')
    ALTER TABLE cyber.incidents ADD CONSTRAINT CK_incidents_severity CHECK ([severity]='LOW' OR [severity]='MEDIUM' OR [severity]='HIGH' OR [severity]='CRITICAL');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'cyber' AND t.name = 'incidents' AND cc.name = 'CK_incidents_status')
    ALTER TABLE cyber.incidents ADD CONSTRAINT CK_incidents_status CHECK ([status]='CLOSED' OR [status]='RESOLVED' OR [status]='INVESTIGATING' OR [status]='OPEN');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'risk' AND t.name = 'risk_treatments' AND cc.name = 'CK_rt_type')
    ALTER TABLE risk.risk_treatments ADD CONSTRAINT CK_rt_type CHECK ([treatment_type]='AVOID' OR [treatment_type]='ACCEPT' OR [treatment_type]='TRANSFER' OR [treatment_type]='MITIGATE');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'risk' AND t.name = 'risks' AND cc.name = 'CK_risks_source')
    ALTER TABLE risk.risks ADD CONSTRAINT CK_risks_source CHECK ([source_module]='COMPLIANCE' OR [source_module]='PRIVACY' OR [source_module]='BCM' OR [source_module]='CYBER' OR [source_module]='ERM');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'contracts' AND cc.name = 'CK_scm_contract_status')
    ALTER TABLE scm.contracts ADD CONSTRAINT CK_scm_contract_status CHECK ([status]='UNDER_REVIEW' OR [status]='DRAFT' OR [status]='TERMINATED' OR [status]='EXPIRED' OR [status]='ACTIVE');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'contracts' AND cc.name = 'CK_scm_contract_type')
    ALTER TABLE scm.contracts ADD CONSTRAINT CK_scm_contract_type CHECK ([contract_type]='OTHER' OR [contract_type]='FRAMEWORK' OR [contract_type]='NDA' OR [contract_type]='SUPPLY' OR [contract_type]='SERVICE');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'dependencies' AND cc.name = 'CK_scm_dep_level')
    ALTER TABLE scm.dependencies ADD CONSTRAINT CK_scm_dep_level CHECK ([dependency_level]='LOW' OR [dependency_level]='MEDIUM' OR [dependency_level]='HIGH');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'dependencies' AND cc.name = 'CK_scm_dep_status')
    ALTER TABLE scm.dependencies ADD CONSTRAINT CK_scm_dep_status CHECK ([status]='AT_RISK' OR [status]='INACTIVE' OR [status]='ACTIVE');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'dependencies' AND cc.name = 'CK_scm_dep_type')
    ALTER TABLE scm.dependencies ADD CONSTRAINT CK_scm_dep_type CHECK ([dependency_type]='EXCLUSIVE' OR [dependency_type]='SHARED' OR [dependency_type]='NON_CRITICAL' OR [dependency_type]='CRITICAL');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'slas' AND cc.name = 'CK_scm_sla_freq')
    ALTER TABLE scm.slas ADD CONSTRAINT CK_scm_sla_freq CHECK ([measurement_frequency]='QUARTERLY' OR [measurement_frequency]='MONTHLY' OR [measurement_frequency]='WEEKLY' OR [measurement_frequency]='DAILY');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'slas' AND cc.name = 'CK_scm_sla_status')
    ALTER TABLE scm.slas ADD CONSTRAINT CK_scm_sla_status CHECK ([status]='NOT_MEASURED' OR [status]='AT_RISK' OR [status]='BREACHED' OR [status]='MET');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'supplier_incidents' AND cc.name = 'CK_scm_inc_impact')
    ALTER TABLE scm.supplier_incidents ADD CONSTRAINT CK_scm_inc_impact CHECK ([impact]='LOW' OR [impact]='MEDIUM' OR [impact]='HIGH' OR [impact]='CRITICAL');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'supplier_incidents' AND cc.name = 'CK_scm_inc_status')
    ALTER TABLE scm.supplier_incidents ADD CONSTRAINT CK_scm_inc_status CHECK ([status]='CLOSED' OR [status]='ESCALATED' OR [status]='RESOLVED' OR [status]='IN_PROGRESS' OR [status]='OPEN');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'supplier_incidents' AND cc.name = 'CK_scm_inc_type')
    ALTER TABLE scm.supplier_incidents ADD CONSTRAINT CK_scm_inc_type CHECK ([incident_type]='OTHER' OR [incident_type]='SLA_BREACH' OR [incident_type]='QUALITY' OR [incident_type]='DELAY' OR [incident_type]='BREACH' OR [incident_type]='SERVICE_DISRUPTION');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendor_assessments' AND cc.name = 'CK_scm_assess_rating')
    ALTER TABLE scm.vendor_assessments ADD CONSTRAINT CK_scm_assess_rating CHECK ([risk_rating]='LOW' OR [risk_rating]='MEDIUM' OR [risk_rating]='HIGH' OR [risk_rating]='CRITICAL');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendor_assessments' AND cc.name = 'CK_scm_assess_status')
    ALTER TABLE scm.vendor_assessments ADD CONSTRAINT CK_scm_assess_status CHECK ([status]='ESCALATED' OR [status]='COMPLETED' OR [status]='IN_PROGRESS' OR [status]='PLANNED');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendor_assessments' AND cc.name = 'CK_scm_assess_type')
    ALTER TABLE scm.vendor_assessments ADD CONSTRAINT CK_scm_assess_type CHECK ([assessment_type]='TRIGGERED' OR [assessment_type]='PERIODIC' OR [assessment_type]='INITIAL');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendor_onboarding' AND cc.name = 'CK_scm_ob_stage')
    ALTER TABLE scm.vendor_onboarding ADD CONSTRAINT CK_scm_ob_stage CHECK ([current_stage]='APPROVAL' OR [current_stage]='LEGAL_REVIEW' OR [current_stage]='OPERATIONAL_REVIEW' OR [current_stage]='SECURITY_REVIEW' OR [current_stage]='CRITICALITY_CLASSIFICATION' OR [current_stage]='REGISTRATION');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendor_onboarding' AND cc.name = 'CK_scm_ob_status')
    ALTER TABLE scm.vendor_onboarding ADD CONSTRAINT CK_scm_ob_status CHECK ([status]='ON_HOLD' OR [status]='REJECTED' OR [status]='COMPLETED' OR [status]='IN_PROGRESS');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendors' AND cc.name = 'CK_scm_vendor_category')
    ALTER TABLE scm.vendors ADD CONSTRAINT CK_scm_vendor_category CHECK ([category]='OTHER' OR [category]='CONSULTING' OR [category]='UTILITIES' OR [category]='FINANCIAL' OR [category]='MANUFACTURING' OR [category]='LOGISTICS' OR [category]='IT_SERVICES');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendors' AND cc.name = 'CK_scm_vendor_criticality')
    ALTER TABLE scm.vendors ADD CONSTRAINT CK_scm_vendor_criticality CHECK ([criticality]='LOW' OR [criticality]='MEDIUM' OR [criticality]='HIGH' OR [criticality]='CRITICAL');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendors' AND cc.name = 'CK_scm_vendor_ob_status')
    ALTER TABLE scm.vendors ADD CONSTRAINT CK_scm_vendor_ob_status CHECK ([onboarding_status]='REJECTED' OR [onboarding_status]='COMPLETED' OR [onboarding_status]='IN_PROGRESS' OR [onboarding_status]='PENDING');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendors' AND cc.name = 'CK_scm_vendor_risk_level')
    ALTER TABLE scm.vendors ADD CONSTRAINT CK_scm_vendor_risk_level CHECK ([risk_level]='LOW' OR [risk_level]='MEDIUM' OR [risk_level]='HIGH');
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'scm' AND t.name = 'vendors' AND cc.name = 'CK_scm_vendor_status')
    ALTER TABLE scm.vendors ADD CONSTRAINT CK_scm_vendor_status CHECK ([status]='EXITED' OR [status]='UNDER_REVIEW' OR [status]='BLACKLISTED' OR [status]='INACTIVE' OR [status]='ACTIVE');
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
DECLARE @present INT;
DECLARE @presentMsg NVARCHAR(400) =
    N'V364 failed: fewer than 29 of the named CHECK constraints exist. A fresh install still accepts '
  + N'values the application enums do not contain.';
SELECT @present = COUNT(*)
  FROM sys.check_constraints cc
 WHERE cc.is_system_named = 0
   AND cc.name IN (
     'CK_bcm_exer_score','CK_bcm_exer_status','CK_bcm_exer_type','CK_incidents_category',
     'CK_incidents_severity','CK_incidents_status','CK_pdep_no_self','CK_risks_source',
     'CK_rt_type','CK_scm_assess_rating','CK_scm_assess_status','CK_scm_assess_type',
     'CK_scm_contract_status','CK_scm_contract_type','CK_scm_dep_level','CK_scm_dep_status',
     'CK_scm_dep_type','CK_scm_inc_impact','CK_scm_inc_status','CK_scm_inc_type',
     'CK_scm_ob_stage','CK_scm_ob_status','CK_scm_sla_freq','CK_scm_sla_status',
     'CK_scm_vendor_category','CK_scm_vendor_criticality','CK_scm_vendor_ob_status','CK_scm_vendor_risk_level',
     'CK_scm_vendor_status');
IF @present < 29 THROW 50000, @presentMsg, 1;
GO

-- Every one of them must be TRUSTED. `ADD CONSTRAINT` verifies the existing rows and leaves
-- is_not_trusted = 0; `WITH NOCHECK` would leave the constraint in place and the data unchecked —
-- a control that covers the next row and not the ones already there. If a later edit adds NOCHECK to
-- get past a violation, this fires rather than letting the constraint look enforced.
DECLARE @untrusted INT;
DECLARE @untrustedMsg NVARCHAR(400) =
    N'V364 failed: one or more of the CHECK constraints it adds is NOT TRUSTED, so the rows already '
  + N'in the table were never verified against it. Fix the data, do not add WITH NOCHECK.';
SELECT @untrusted = COUNT(*)
  FROM sys.check_constraints cc
 WHERE cc.is_not_trusted = 1
   AND cc.name IN (
     'CK_bcm_exer_score','CK_bcm_exer_status','CK_bcm_exer_type','CK_incidents_category',
     'CK_incidents_severity','CK_incidents_status','CK_pdep_no_self','CK_risks_source',
     'CK_rt_type','CK_scm_assess_rating','CK_scm_assess_status','CK_scm_assess_type',
     'CK_scm_contract_status','CK_scm_contract_type','CK_scm_dep_level','CK_scm_dep_status',
     'CK_scm_dep_type','CK_scm_inc_impact','CK_scm_inc_status','CK_scm_inc_type',
     'CK_scm_ob_stage','CK_scm_ob_status','CK_scm_sla_freq','CK_scm_sla_status',
     'CK_scm_vendor_category','CK_scm_vendor_criticality','CK_scm_vendor_ob_status','CK_scm_vendor_risk_level',
     'CK_scm_vendor_status');
IF @untrusted > 0 THROW 50000, @untrustedMsg, 1;
GO

-- And none may be disabled. A disabled CHECK is F2-62's dead-flag shape: present in the catalogue,
-- enforcing nothing, and invisible unless someone asks this exact question.
DECLARE @disabled INT;
DECLARE @disabledMsg NVARCHAR(400) =
    N'V364 failed: one or more of the CHECK constraints it adds is DISABLED. A disabled constraint '
  + N'is in the catalogue and enforces nothing.';
SELECT @disabled = COUNT(*)
  FROM sys.check_constraints cc
 WHERE cc.is_disabled = 1
   AND cc.name IN (
     'CK_bcm_exer_score','CK_bcm_exer_status','CK_bcm_exer_type','CK_incidents_category',
     'CK_incidents_severity','CK_incidents_status','CK_pdep_no_self','CK_risks_source',
     'CK_rt_type','CK_scm_assess_rating','CK_scm_assess_status','CK_scm_assess_type',
     'CK_scm_contract_status','CK_scm_contract_type','CK_scm_dep_level','CK_scm_dep_status',
     'CK_scm_dep_type','CK_scm_inc_impact','CK_scm_inc_status','CK_scm_inc_type',
     'CK_scm_ob_stage','CK_scm_ob_status','CK_scm_sla_freq','CK_scm_sla_status',
     'CK_scm_vendor_category','CK_scm_vendor_criticality','CK_scm_vendor_ob_status','CK_scm_vendor_risk_level',
     'CK_scm_vendor_status');
IF @disabled > 0 THROW 50000, @disabledMsg, 1;
GO
