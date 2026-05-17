# 04 — Entity Relationships

ASCII ER diagrams for the spine + headline cross-cuts. Full ER PDF
can be auto-generated from the JPA metamodel — see
`tss-grc-backend/pom.xml` for the schemacrawler plugin (optional).

## Framework Intersection spine (F1 + F2)

```
                  ┌──────────────────────────────────┐
                  │ platform.authority_documents     │
                  │ (NCA-ECC-V2, PDPL-2023, …)       │
                  └────────────┬─────────────────────┘
                               │ 1 .. N
                               ▼
                  ┌──────────────────────────────────┐
                  │ platform.authority_document_     │
                  │  citations                       │
                  │ (2-1-3, A.5.7, Art-14, …)        │
                  └────────────┬─────────────────────┘
                               │ N .. N (via control_mappings)
                               ▼
   ┌──────────────────────────────────────────────────────────┐
   │ platform.control_mappings                                │
   │ control_id, citation_id, coverage, rationale_*           │
   └────────────┬─────────────────────────────────────────────┘
                │ N .. 1
                ▼
                  ┌──────────────────────────────────┐
                  │ platform.controls                │
                  │ (the spine: C-IAM-014, C-BCM-003)│
                  └────┬─────────────────┬───────────┘
              N .. 1 │              1 .. N│
                     ▼                    ▼
          ┌───────────────────┐   ┌───────────────────────┐
          │ control_objectives│   │ control_assessments   │
          │ (groups)          │   │ (per cycle)           │
          └───────────────────┘   └───────────────────────┘

   Risk-Control Matrix (review R1):
   ┌──────────────────────────────────┐
   │ platform.risk_control_links      │
   │ risk_id ──── control_id          │
   │ link_type (MITIGATES/DETECTS/…)  │
   └──────────────────────────────────┘
            │             │
            ▼             ▼
    risk.risks      platform.controls
```

## Risk module (sprint 1-2 + Risk-Advanced)

```
risk.risk_categories ──1─N─→ risk.risks
                                ├──1─N─→ risk.risk_assessments
                                ├──1─N─→ risk.risk_treatments
                                ├──1─N─→ risk.kri_definitions ──1─N─→ risk.kri_readings
                                └──1─N─→ risk.risk_scenarios

risk.risk_appetites (org-level thresholds)
```

## BCM module

```
bcm.business_services ──1─N─→ bcm.bia_records ──1─N─→ bcm.bcm_plans
                                                     └─→ bcm.dr_plans
bcm.bcm_plans ──1─N─→ bcm.exercises ──1─N─→ bcm.crisis_events
                                            └──1─N─→ bcm.crisis_comms_logs
```

## Privacy module

```
privacy.processing_activities ──1─N─→ privacy.dpia_records
                              ──1─N─→ privacy.cross_border_transfers
                              ──1─N─→ privacy.data_breaches
                              ──1─N─→ privacy.processor_assessments
privacy.privacy_notices ──1─N─→ privacy.consent_records (🔜 entity #45)
privacy.dsr_cases (per request)
```

## TPRM + SCM

```
tprm.vendors ──1─N─→ tprm.vendor_assessments ──1─1─→ tprm.questionnaire_templates
                                              ──1─N─→ tprm.questionnaire_responses
tprm.questionnaire_templates ──1─N─→ tprm.questionnaire_questions

scm.suppliers ──1─N─→ scm.contracts
              ──1─N─→ scm.dependencies
              ──1─N─→ scm.supplier_resilience_tests
              ──1─N─→ scm.exit_plans
```

## Cyber

```
cyber.frameworks ──1─N─→ cyber.cyber_controls ──1─N─→ cyber.cyber_sub_controls
cyber.cyber_controls ──1─N─→ cyber.assessments
cyber.assets ──1─N─→ cyber.vulnerabilities ──1─N─→ cyber.vuln_exceptions
                                          ──1─N─→ cyber.cyber_incidents
```

Note: cyber.cyber_controls also lives mirrored in `platform.controls`
post-V94 with `legacy_source_module='cyber'` so the unified library
sees everything (see F3+F4).

## NDI (SDAIA "نضيء")

```
ndi.assessment_cycles ──1─N─→ ndi.maturity_assessments
                       ──1─N─→ ndi.compliance_assessments
                       ──1─N─→ ndi.opex_kpis
                       ──1─N─→ ndi.gaps ──1─N─→ ndi.corrective_actions
                       ──1─N─→ ndi.improvement_plans ──1─N─→ ndi.plan_items
                       ──1─N─→ ndi.evidence_repository
                       ──1─N─→ ndi.submission_packages
                       ──1─N─→ ndi.reports
                       ──1─N─→ ndi.audit_events
ndi.framework_library (shared)
ndi.governance_domains (shared)
ndi.settings (shared)
```

## Cross-cutting

```
platform.notifications ──N─1─→ iam.users (recipient)
platform.audit_log     ──N─1─→ <any entity> (entityType + entityId soft FK)
platform.automation_rule_runs ──N─1─→ platform.automation_rules
platform.organisation_licenses ──N─1─→ platform.organisations
```

## Inheritance / shared base

```
common.BaseEntity (Lombok @Getter/@Setter)
   ├── id, externalId, organisationId
   ├── isDeleted, createdAt, createdBy, updatedAt, updatedBy
   └── @Where(clause = "is_deleted = 0")
```

Every domain entity extends `BaseEntity` either explicitly or by
inlining the same 7 columns.

---

**See also:** Flyway migrations V1-V97 in
`tss-grc-backend/src/main/resources/db/migration/sqlserver/` — they
are the unambiguous source of truth for the actual constraint set.
