# 11 — Acceptance Criteria

Format: `Given / When / Then`. Each criterion maps to a Mockito
test that locks it; the test ID is in parentheses.

## Risk module

### Rule A1 — likelihood/impact ∈ [1,5]

- **Given** a risk with id 100,
  **When** I post `{likelihood: 6, impact: 3}`,
  **Then** the response is 400 with message "likelihood must be 1..5".
  (`RiskAssessmentServiceTest.record_likelihoodOutOfRangeRejected`)

### Rule A2 — residual promotes to parent risk

- **Given** a risk with residualLikelihood=2, residualImpact=2,
  **When** I record an assessment of {3, 4},
  **Then** the parent risk's residual columns become (3, 4) AND the
  residual_score (computed) becomes 12.
  (`RiskAssessmentServiceTest.record_promotesResidualToParentRisk`)

### Rule T1 — last live treatment refusal

- **Given** a risk in TREATMENT status with one IN_PROGRESS treatment,
  **When** I delete that treatment,
  **Then** the response is 400 with "cannot delete the last live treatment".
  (`RiskTreatmentServiceTest.delete_lastLiveTreatmentOfRiskInTreatmentRefused`)

## Policy module

### Effective date guard

- **Given** a policy in APPROVED with effective_date=null,
  **When** I transition to PUBLISHED,
  **Then** the response is 400 with "effective_date is required".
  (`PolicyServiceTest.policy_publishedRequiresEffectiveDate`)

### Delete published refusal

- **Given** a policy in PUBLISHED,
  **When** I DELETE it,
  **Then** the response is 400 with "Cannot delete a PUBLISHED policy".
  (`PolicyServiceTest.policy_deletePublishedRefused`)

## TPRM Questionnaire (V81)

### Template activation requires questions

- **Given** a template in DRAFT with zero questions,
  **When** I transition to ACTIVE,
  **Then** the response is 400 with "no questions".
  (`QuestionnaireServiceTest.activate_emptyTemplateRefused`)

### Scoring math

- **Given** a template with question Q1 weight=1.0 SCALE_1_5 (max 5),
  **When** I answer Q1 with value "5",
  **Then** posture% = 100, risk% = 0, riskRating = LOW.
  (`QuestionnaireServiceTest.upsertResponse_computesScoreAndRating`)

## Authority Documents (F1)

### Duplicate (code, version) rejected

- **Given** an existing NCA-ECC-V2 v2.0 document,
  **When** I POST another with the same code+version,
  **Then** the response is 400 with "already exists".
  (`AuthorityDocumentServiceTest.create_duplicateCodeVersionRejected`)

### Supersession requires target

- **Given** an ACTIVE document,
  **When** I transition to SUPERSEDED without supersededById,
  **Then** the response is 400 with "supersededById is required".
  (`AuthorityDocumentServiceTest.transition_supersededRequiresTarget`)

## Control Library (F2)

### Mapping requires rationale

- **Given** a valid control + citation,
  **When** I POST a mapping with empty rationaleEn,
  **Then** the response is 400 with "rationaleEn is required".
  (`ControlLibraryServiceTest.createMapping_requiresRationale`)

### Risk-control link duplicate rejected

- **Given** a (risk, control) link exists,
  **When** I POST another with the same pair,
  **Then** the response is 400 with "already exists".
  (`ControlLibraryServiceTest.linkRisk_duplicateRejected`)

## Compliance Posture (F5+F6)

### Coverage math: mapped vs gap

- **Given** a doc with citations 1-1 (mapped FULL) + 1-2 (mapped
  PARTIAL) + 1-3 (no mapping),
  **When** I GET /authority-documents/:id/coverage,
  **Then** totalCitations=3, mappedCitations=2, gapCitations=1,
  fullCoverage=1, partialCoverage=1, coveragePct=66.7.
  (`CompliancePostureServiceTest.coverage_countsMappedAndGapCorrectly`)

## Audit Log (F7)

### Defaults

- **Given** a new audit event with no severity,
  **When** I post it,
  **Then** the saved row's severity is INFO and organisationId is the
  current tenant.
  (`AuditLogServiceTest.recordEvent_defaultsSeverityToInfo`)

## Automation Rules (F8)

### Condition match fires action

- **Given** rule AR-04 enabled with condition `affects_personal_data=true`,
  **When** a DomainEvent INCIDENT_CREATED arrives with payload
  `{affects_personal_data: true, incident_type: 'CYBER'}`,
  **Then** the engine fires the action, records SUCCESS run, emits a
  RULE_FIRED audit row, and bumps fireCount.
  (`AutomationRuleEngineTest.onEvent_matchingConditionFiresAndRecordsSuccess`)

### Condition mismatch is skipped, no audit row

- **Given** same rule,
  **When** payload `affects_personal_data=false`,
  **Then** SKIPPED run is recorded, no action executed, no rule update,
  no audit row.
  (`AutomationRuleEngineTest.onEvent_conditionMissesRecordsSkipped`)

## Licensing (F9)

### Validity-window filtering

- **Given** a tenant with RISK enabled+active, BCM enabled but expired,
  CYBER disabled, PDPL enabled+future-start, TPRM enabled+no-end,
  **When** I GET /me/enabled,
  **Then** enabled_modules = ["RISK", "TPRM"] (case-sensitive).
  (`LicensingServiceTest.enabledModulesFor_skipsDisabledAndOutOfDate`)

### Unknown module rejected

- **Given** moduleCode="FAKE",
  **When** I POST /modules,
  **Then** 400 with "Unknown module".
  (`LicensingServiceTest.upsert_rejectsUnknownModule`)

## Cross-cutting

### Tenant isolation

- **Given** records in tenant 1 and tenant 7,
  **When** a user from tenant 7 lists any resource,
  **Then** they never see a tenant-1 row.

### Soft delete

- **Given** a soft-deleted record,
  **When** any service does a normal read,
  **Then** the record is filtered out by `@Where(clause = "is_deleted = 0")`.

---

**See also:** Mockito test classes under
`tss-grc-backend/src/test/java/com/tss/grc/...`.
