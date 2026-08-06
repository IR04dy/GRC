# Entity Relationships
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## 1. Purpose

This document defines all entity relationships across the TSS GRC platform modules. It covers intra-module relationships, cross-module foreign keys, and polymorphic association patterns used for workflow, audit, and attachment linking.

## 2. Relationship Conventions

- All foreign keys use `_id` suffix (e.g., `org_id`, `user_id`).
- Cross-schema foreign keys reference the target schema explicitly in constraints.
- Polymorphic relationships use `entity_type` (VARCHAR(100)) + `entity_id` (BIGINT) pairs.
- Junction/bridge tables use composite primary keys on both FK columns.
- Soft-delete cascading is handled at the application layer, not via database cascades.
- All relationships assume `org_id` tenant scoping unless stated otherwise.

---

## 3. Global Relationships (All Modules)

### 3.1 Organization Scoping

Every business table in every module carries `org_id` as a mandatory foreign key to `platform.organizations`.

| Source Schema | Source Entity | FK Column | Target Entity | Relationship | Description |
|---|---|---|---|---|---|
| risk | risks | org_id | platform.organizations | N:1 | Risk belongs to an organization |
| risk | risk_categories | org_id | platform.organizations | N:1 | Risk category scoped to organization |
| risk | risk_appetite_statements | org_id | platform.organizations | N:1 | Appetite defined per organization |
| cyber | incidents | org_id | platform.organizations | N:1 | Cyber incident belongs to organization |
| cyber | assets | org_id | platform.organizations | N:1 | Asset belongs to organization |
| bcm | critical_processes | org_id | platform.organizations | N:1 | Process belongs to organization |
| bcm | bcm_plans | org_id | platform.organizations | N:1 | BCM plan belongs to organization |
| bcm | business_impact_analyses | org_id | platform.organizations | N:1 | BIA belongs to organization |
| bcm | bcm_exercises | org_id | platform.organizations | N:1 | Exercise belongs to organization |
| bcm | bcm_incidents | org_id | platform.organizations | N:1 | BCM incident belongs to organization |
| privacy | data_inventory | org_id | platform.organizations | N:1 | Data inventory scoped to organization |
| privacy | processing_activities | org_id | platform.organizations | N:1 | Processing activity per organization |
| privacy | privacy_assessments | org_id | platform.organizations | N:1 | Privacy assessment per organization |
| privacy | consent_records | org_id | platform.organizations | N:1 | Consent record per organization |
| privacy | breach_records | org_id | platform.organizations | N:1 | Breach record per organization |
| privacy | ropa_entries | org_id | platform.organizations | N:1 | RoPA entry per organization |
| compliance | assessments | org_id | platform.organizations | N:1 | Assessment per organization |
| reporting | report_schedules | org_id | platform.organizations | N:1 | Schedule scoped to organization |
| reporting | report_executions | org_id | platform.organizations | N:1 | Execution scoped to organization |
| reporting | dashboard_configurations | org_id | platform.organizations | N:1 | Dashboard per organization |
| audit | audit_logs | org_id | platform.organizations | N:1 | Audit entry scoped to organization |
| iam | users | org_id | platform.organizations | N:1 | User belongs to organization |
| iam | roles | org_id | platform.organizations | N:1 | Role scoped to organization (nullable for system roles) |
| workflow | workflow_instances | org_id | platform.organizations | N:1 | Workflow instance per organization |

### 3.2 Business Unit Scoping

Tables that require departmental scoping carry `bu_id` referencing `platform.business_units`.

| Source Schema | Source Entity | FK Column | Target Entity | Relationship | Description |
|---|---|---|---|---|---|
| iam | users | bu_id | platform.business_units | N:1 | User's primary business unit |
| risk | risks | bu_id | platform.business_units | N:1 | Risk owned by business unit |
| cyber | incidents | bu_id | platform.business_units | N:1 | Incident in business unit scope |
| bcm | critical_processes | bu_id | platform.business_units | N:1 | Process belongs to business unit |
| privacy | data_inventory | bu_id | platform.business_units | N:1 | Data inventory per business unit |
| iam | user_roles | bu_id | platform.business_units | N:1 | Role assignment scoped to BU (nullable for org-wide) |

### 3.3 User References (created_by, updated_by, assigned_to)

All business entities carry `created_by` and `updated_by` columns referencing `iam.users.id`. Entities with ownership or assignment carry additional user references.

| Source Schema | Source Entity | FK Column | Target Entity | Relationship | Description |
|---|---|---|---|---|---|
| risk | risks | owner_user_id | iam.users | N:1 | Risk owner |
| risk | risks | created_by | iam.users | N:1 | User who created the risk |
| risk | risks | updated_by | iam.users | N:1 | User who last updated the risk |
| risk | risk_assessments | assessed_by | iam.users | N:1 | Assessor |
| risk | risk_treatments | responsible_user_id | iam.users | N:1 | Treatment responsible person |
| cyber | incidents | reporter_user_id | iam.users | N:1 | Incident reporter |
| cyber | incidents | assigned_to | iam.users | N:1 | Assigned investigator |
| bcm | critical_processes | owner_user_id | iam.users | N:1 | Process owner |
| bcm | bcm_plans | owner_user_id | iam.users | N:1 | Plan owner |
| bcm | bcm_plans | approved_by | iam.users | N:1 | Plan approver |
| privacy | data_inventory | owner_user_id | iam.users | N:1 | Data owner |
| privacy | privacy_assessments | assessor_user_id | iam.users | N:1 | Privacy assessor |
| privacy | privacy_assessments | approved_by | iam.users | N:1 | Assessment approver |
| compliance | assessments | assessor_user_id | iam.users | N:1 | Compliance assessor |
| compliance | remediation_plans | responsible_user_id | iam.users | N:1 | Remediation owner |
| compliance | evidence_items | submitted_by | iam.users | N:1 | Evidence submitter |
| platform | controls | owner_user_id | iam.users | N:1 | Control owner |
| reporting | report_schedules | created_by | iam.users | N:1 | Schedule creator |
| reporting | dashboard_configurations | user_id | iam.users | N:1 | Dashboard owner |
| reporting | saved_filters | user_id | iam.users | N:1 | Filter owner |
| workflow | workflow_instances | initiated_by | iam.users | N:1 | Workflow initiator |
| workflow | tasks | assigned_to | iam.users | N:1 | Task assignee |
| audit | audit_logs | user_id | iam.users | N:1 | Acting user |
| risk | risk_appetite_statements | approved_by | iam.users | N:1 | Appetite approver |

---

## 4. Polymorphic Relationships

Polymorphic relationships allow a single table to reference any entity across the platform using `entity_type` + `entity_id` pairs. These are NOT enforced via database foreign keys but through application-level validation and check constraints on `entity_type`.

### 4.1 Workflow Instances

`workflow.workflow_instances` binds to any entity requiring workflow processing.

| entity_type Value | Target Schema | Target Entity | Example Use Case |
|---|---|---|---|
| `RISK` | risk | risks | Risk approval workflow |
| `RISK_TREATMENT` | risk | risk_treatments | Treatment approval |
| `CYBER_INCIDENT` | cyber | incidents | Incident response workflow |
| `PRIVACY_BREACH` | privacy | breach_records | Breach notification workflow |
| `PRIVACY_ASSESSMENT` | privacy | privacy_assessments | PIA/DPIA approval workflow |
| `COMPLIANCE_ASSESSMENT` | compliance | assessments | Assessment review workflow |
| `COMPLIANCE_FINDING` | compliance | findings | Finding remediation workflow |
| `REMEDIATION_PLAN` | compliance | remediation_plans | Remediation tracking |
| `BCM_PLAN` | bcm | bcm_plans | BCP approval workflow |
| `BCM_INCIDENT` | bcm | bcm_incidents | BCM incident response |
| `CONTROL` | platform | controls | Control review workflow |

**Table definition:**

```sql
CREATE TABLE workflow.workflow_instances (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES platform.organizations(id),
    definition_id   BIGINT NOT NULL REFERENCES workflow.workflow_definitions(id),
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       BIGINT NOT NULL,
    current_step_id BIGINT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    initiated_by    BIGINT NOT NULL REFERENCES iam.users(id),
    started_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    completed_at    DATETIME2 NULL,
    created_by      BIGINT NOT NULL REFERENCES iam.users(id),
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT NULL REFERENCES iam.users(id),
    updated_at      DATETIME2 NULL,
    CONSTRAINT CHK_workflow_entity_type CHECK (entity_type IN (
        'RISK','RISK_TREATMENT','CYBER_INCIDENT','PRIVACY_BREACH',
        'PRIVACY_ASSESSMENT','COMPLIANCE_ASSESSMENT','COMPLIANCE_FINDING',
        'REMEDIATION_PLAN','BCM_PLAN','BCM_INCIDENT','CONTROL'
    ))
);

CREATE INDEX IX_workflow_instances_entity
    ON workflow.workflow_instances(entity_type, entity_id);
```

### 4.2 Audit Logs

`audit.audit_logs` records actions on any entity.

| entity_type Value | Target Schema | Target Entity |
|---|---|---|
| `USER` | iam | users |
| `ROLE` | iam | roles |
| `ORGANIZATION` | platform | organizations |
| `BUSINESS_UNIT` | platform | business_units |
| `RISK` | risk | risks |
| `RISK_ASSESSMENT` | risk | risk_assessments |
| `RISK_TREATMENT` | risk | risk_treatments |
| `CONTROL` | platform | controls |
| `CYBER_INCIDENT` | cyber | incidents |
| `ASSET` | cyber | assets |
| `BCM_PLAN` | bcm | bcm_plans |
| `BIA` | bcm | business_impact_analyses |
| `BCM_EXERCISE` | bcm | bcm_exercises |
| `BCM_INCIDENT` | bcm | bcm_incidents |
| `DATA_INVENTORY` | privacy | data_inventory |
| `PROCESSING_ACTIVITY` | privacy | processing_activities |
| `PRIVACY_ASSESSMENT` | privacy | privacy_assessments |
| `PRIVACY_BREACH` | privacy | breach_records |
| `CONSENT_RECORD` | privacy | consent_records |
| `COMPLIANCE_ASSESSMENT` | compliance | assessments |
| `COMPLIANCE_FINDING` | compliance | findings |
| `EVIDENCE` | compliance | evidence_items |
| `WORKFLOW_INSTANCE` | workflow | workflow_instances |
| `REPORT_DEFINITION` | reporting | report_definitions |

### 4.3 Attachments

`platform.attachments` and `platform.attachment_links` provide polymorphic file attachment.

```sql
CREATE TABLE platform.attachments (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES platform.organizations(id),
    file_name       NVARCHAR(500) NOT NULL,
    file_type       VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_path    NVARCHAR(1000) NOT NULL,
    checksum        VARCHAR(64) NULL,
    uploaded_by     BIGINT NOT NULL REFERENCES iam.users(id),
    uploaded_at     DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    is_deleted      BIT NOT NULL DEFAULT 0
);

CREATE TABLE platform.attachment_links (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    attachment_id   BIGINT NOT NULL REFERENCES platform.attachments(id),
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       BIGINT NOT NULL,
    created_by      BIGINT NOT NULL REFERENCES iam.users(id),
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE INDEX IX_attachment_links_entity
    ON platform.attachment_links(entity_type, entity_id);
```

### 4.4 Comments

`platform.comments` provides polymorphic commenting on any entity.

```sql
CREATE TABLE platform.comments (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES platform.organizations(id),
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       BIGINT NOT NULL,
    parent_id       BIGINT NULL REFERENCES platform.comments(id),
    comment_text    NVARCHAR(MAX) NOT NULL,
    created_by      BIGINT NOT NULL REFERENCES iam.users(id),
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT NULL REFERENCES iam.users(id),
    updated_at      DATETIME2 NULL,
    is_deleted      BIT NOT NULL DEFAULT 0
);

CREATE INDEX IX_comments_entity
    ON platform.comments(entity_type, entity_id);
```

### 4.5 Tags

`platform.tag_assignments` provides polymorphic tagging.

```sql
CREATE TABLE platform.tag_assignments (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    tag_id          BIGINT NOT NULL REFERENCES platform.tags(id),
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       BIGINT NOT NULL,
    created_by      BIGINT NOT NULL REFERENCES iam.users(id),
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_tag_assignment UNIQUE (tag_id, entity_type, entity_id)
);

CREATE INDEX IX_tag_assignments_entity
    ON platform.tag_assignments(entity_type, entity_id);
```

---

## 5. Control Cross-Module Relationships

The `platform.controls` entity is the central control library shared across modules.

| Source Entity | Junction Table | FK to Control | FK to Source | Relationship | Description |
|---|---|---|---|---|---|
| risk.risks | risk.risk_controls | control_id | risk_id | M:N | Controls mitigating a risk |
| cyber.assets | cyber.cyber_controls | control_id | asset_id | M:N | Controls protecting an asset |
| compliance.assessments | compliance.assessment_controls | control_id | assessment_id | M:N | Controls evaluated in assessment |
| compliance.evidence_items | -- | control_id | -- | N:1 | Evidence linked to a control |
| platform.control_framework_mappings | -- | control_id | framework_id | M:N | Control mapped to framework reference |

### 5.1 risk.risk_controls

```sql
CREATE TABLE risk.risk_controls (
    risk_id             BIGINT NOT NULL REFERENCES risk.risks(id),
    control_id          BIGINT NOT NULL REFERENCES platform.controls(id),
    effectiveness_rating VARCHAR(50) NULL,
    notes               NVARCHAR(1000) NULL,
    created_by          BIGINT NOT NULL REFERENCES iam.users(id),
    created_at          DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_risk_controls PRIMARY KEY (risk_id, control_id)
);
```

### 5.2 cyber.cyber_controls

```sql
CREATE TABLE cyber.cyber_controls (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    asset_id        BIGINT NULL REFERENCES cyber.assets(id),
    assessment_id   BIGINT NULL,
    control_id      BIGINT NOT NULL REFERENCES platform.controls(id),
    implementation_status VARCHAR(50) NOT NULL,
    effectiveness   VARCHAR(50) NULL,
    notes           NVARCHAR(1000) NULL,
    created_by      BIGINT NOT NULL REFERENCES iam.users(id),
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);
```

### 5.3 compliance.assessment_controls

```sql
CREATE TABLE compliance.assessment_controls (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    assessment_id       BIGINT NOT NULL REFERENCES compliance.assessments(id),
    control_id          BIGINT NOT NULL REFERENCES platform.controls(id),
    compliance_status   VARCHAR(50) NOT NULL DEFAULT 'NOT_ASSESSED',
    score               DECIMAL(5,2) NULL,
    evidence_notes      NVARCHAR(MAX) NULL,
    assessor_notes      NVARCHAR(MAX) NULL,
    created_by          BIGINT NOT NULL REFERENCES iam.users(id),
    created_at          DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT NULL REFERENCES iam.users(id),
    updated_at          DATETIME2 NULL,
    CONSTRAINT UQ_assessment_control UNIQUE (assessment_id, control_id)
);
```

---

## 6. Cross-Module Entity Linkages

These relationships connect entities across different domain modules, enabling integrated GRC views.

### 6.1 Cyber Incidents to Enterprise Risks

```
cyber.incidents.linked_risk_id → risk.risks.id (N:1)
```

When a cyber incident has enterprise risk implications, it is linked to the risk register.

### 6.2 Privacy Breaches to Cyber Incidents and Risks

```
privacy.breach_records.linked_risk_id          → risk.risks.id (N:1)
privacy.breach_records.linked_cyber_incident_id → cyber.incidents.id (N:1)
```

Privacy breaches originating from cyber incidents maintain traceability to both the incident and the resulting enterprise risk.

### 6.3 BCM Business Impact Analyses to Risks

```
bcm.business_impact_analyses.linked_risks → JSON array of risk.risks.id values
```

BIA records reference multiple risks via a JSON column. Application-level validation ensures referential integrity.

### 6.4 Risks from Multiple Modules

```
risk.risks.source_module    → VARCHAR: 'ERM', 'CYBER', 'BCM', 'PRIVACY'
risk.risks.source_entity_id → BIGINT (polymorphic reference)
```

Risks can originate from any module. The `source_module` + `source_entity_id` pattern tracks provenance.

### 6.5 Compliance Frameworks to Control Frameworks

```
compliance.compliance_frameworks.id ←→ platform.control_frameworks.id (logical reference)
```

Compliance frameworks reference the control framework library for control-to-requirement mapping.

---

## 7. Complete Relationship Matrix

### 7.1 Platform Core Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| platform.organizations | platform.organizations | 1:N | parent_org_id | Organization hierarchy (self-referencing) |
| platform.business_units | platform.organizations | N:1 | org_id | BU belongs to organization |
| platform.business_units | platform.business_units | 1:N | parent_bu_id | BU hierarchy (self-referencing) |
| platform.business_units | iam.users | N:1 | head_user_id | BU head/manager |
| platform.lookup_values | platform.lookup_categories | N:1 | category_id | Value belongs to category |
| platform.controls | platform.organizations | N:1 | org_id | Control scoped to organization |
| platform.controls | iam.users | N:1 | owner_user_id | Control owner |
| platform.control_framework_domains | platform.control_frameworks | N:1 | framework_id | Domain belongs to framework |
| platform.control_framework_mappings | platform.controls | N:1 | control_id | Mapping references control |
| platform.control_framework_mappings | platform.control_frameworks | N:1 | framework_id | Mapping references framework |
| platform.attachment_links | platform.attachments | N:1 | attachment_id | Link references attachment |

### 7.2 IAM Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| iam.users | platform.organizations | N:1 | org_id | User belongs to organization |
| iam.users | platform.business_units | N:1 | bu_id | User's primary business unit |
| iam.user_roles | iam.users | N:1 | user_id | Assignment references user |
| iam.user_roles | iam.roles | N:1 | role_id | Assignment references role |
| iam.user_roles | platform.organizations | N:1 | org_id | Role scoped to organization |
| iam.user_roles | platform.business_units | N:1 | bu_id | Role scoped to BU (nullable) |
| iam.role_permissions | iam.roles | N:1 | role_id | Permission assigned to role |
| iam.role_permissions | iam.permissions | N:1 | permission_id | Role has permission |

### 7.3 Workflow Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| workflow.workflow_instances | workflow.workflow_definitions | N:1 | definition_id | Instance of definition |
| workflow.workflow_instances | platform.organizations | N:1 | org_id | Instance scoped to org |
| workflow.workflow_steps | workflow.workflow_definitions | N:1 | definition_id | Step belongs to definition |
| workflow.workflow_transitions | workflow.workflow_steps | N:1 | from_step_id | Transition source |
| workflow.workflow_transitions | workflow.workflow_steps | N:1 | to_step_id | Transition target |
| workflow.tasks | workflow.workflow_instances | N:1 | instance_id | Task within instance |
| workflow.tasks | workflow.workflow_steps | N:1 | step_id | Task for a step |
| workflow.tasks | iam.users | N:1 | assigned_to | Task assignee |
| workflow.approval_decisions | workflow.tasks | N:1 | task_id | Decision on a task |
| workflow.approval_decisions | iam.users | N:1 | decided_by | Decision maker |

### 7.4 Risk Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| risk.risks | platform.organizations | N:1 | org_id | Risk in organization |
| risk.risks | platform.business_units | N:1 | bu_id | Risk in business unit |
| risk.risks | risk.risk_categories | N:1 | category_id | Risk classified by category |
| risk.risks | iam.users | N:1 | owner_user_id | Risk owner |
| risk.risk_categories | risk.risk_categories | 1:N | parent_id | Category hierarchy |
| risk.risk_assessments | risk.risks | N:1 | risk_id | Assessment of a risk |
| risk.risk_assessments | iam.users | N:1 | assessed_by | Assessor |
| risk.risk_treatments | risk.risks | N:1 | risk_id | Treatment for a risk |
| risk.risk_treatments | iam.users | N:1 | responsible_user_id | Treatment owner |
| risk.risk_controls | risk.risks | N:1 | risk_id | Risk-control link (risk side) |
| risk.risk_controls | platform.controls | N:1 | control_id | Risk-control link (control side) |
| risk.risk_appetite_statements | platform.organizations | N:1 | org_id | Appetite per org |
| risk.risk_appetite_statements | risk.risk_categories | N:1 | category_id | Appetite per category |
| risk.risk_indicators | risk.risks | N:1 | risk_id | KRI for a risk |

### 7.5 Cyber Security Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| cyber.incidents | platform.organizations | N:1 | org_id | Incident in organization |
| cyber.incidents | platform.business_units | N:1 | bu_id | Incident in business unit |
| cyber.incidents | iam.users | N:1 | reporter_user_id | Incident reporter |
| cyber.incidents | iam.users | N:1 | assigned_to | Assigned investigator |
| cyber.incidents | risk.risks | N:1 | linked_risk_id | Linked enterprise risk |
| cyber.cyber_controls | platform.controls | N:1 | control_id | Control reference |
| cyber.cyber_controls | cyber.assets | N:1 | asset_id | Control on asset |

### 7.6 BCM Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| bcm.critical_processes | platform.organizations | N:1 | org_id | Process in organization |
| bcm.critical_processes | platform.business_units | N:1 | bu_id | Process in business unit |
| bcm.critical_processes | iam.users | N:1 | owner_user_id | Process owner |
| bcm.process_dependencies | bcm.critical_processes | N:1 | process_id | Dependent process |
| bcm.process_dependencies | bcm.critical_processes | N:1 | depends_on_process_id | Dependency target |
| bcm.business_impact_analyses | platform.organizations | N:1 | org_id | BIA in organization |
| bcm.business_impact_analyses | bcm.critical_processes | N:1 | process_id | BIA for a process |
| bcm.business_impact_analyses | iam.users | N:1 | analyst_user_id | BIA analyst |
| bcm.bcm_plans | platform.organizations | N:1 | org_id | Plan in organization |
| bcm.bcm_plans | iam.users | N:1 | owner_user_id | Plan owner |
| bcm.recovery_strategies | bcm.bcm_plans | N:1 | plan_id | Strategy in plan |
| bcm.recovery_strategies | bcm.critical_processes | N:1 | process_id | Strategy for process |
| bcm.bcm_exercises | platform.organizations | N:1 | org_id | Exercise in organization |
| bcm.bcm_exercises | bcm.bcm_plans | N:1 | plan_id | Exercise tests a plan |
| bcm.bcm_incidents | platform.organizations | N:1 | org_id | Incident in organization |
| bcm.bcm_incidents | bcm.bcm_plans | N:1 | activated_plan_id | Activated plan |

### 7.7 Privacy Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| privacy.data_inventory | platform.organizations | N:1 | org_id | Inventory in organization |
| privacy.data_inventory | platform.business_units | N:1 | bu_id | Inventory in business unit |
| privacy.data_inventory | iam.users | N:1 | owner_user_id | Data owner |
| privacy.processing_activities | platform.organizations | N:1 | org_id | Activity in organization |
| privacy.processing_activities | privacy.data_inventory | N:1 | data_inventory_id | Activity processes data |
| privacy.privacy_assessments | platform.organizations | N:1 | org_id | Assessment in organization |
| privacy.privacy_assessments | privacy.processing_activities | N:1 | processing_activity_id | Assessment of activity |
| privacy.privacy_assessments | iam.users | N:1 | assessor_user_id | Assessor |
| privacy.consent_records | platform.organizations | N:1 | org_id | Consent in organization |
| privacy.consent_records | platform.attachments | N:1 | evidence_attachment_id | Consent evidence |
| privacy.breach_records | platform.organizations | N:1 | org_id | Breach in organization |
| privacy.breach_records | risk.risks | N:1 | linked_risk_id | Linked enterprise risk |
| privacy.breach_records | cyber.incidents | N:1 | linked_cyber_incident_id | Linked cyber incident |
| privacy.ropa_entries | platform.organizations | N:1 | org_id | RoPA in organization |
| privacy.ropa_entries | privacy.processing_activities | N:1 | processing_activity_id | RoPA for activity |

### 7.8 Compliance Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| compliance.assessments | platform.organizations | N:1 | org_id | Assessment in organization |
| compliance.assessments | compliance.compliance_frameworks | N:1 | framework_id | Assessment against framework |
| compliance.assessments | iam.users | N:1 | assessor_user_id | Assessor |
| compliance.assessment_controls | compliance.assessments | N:1 | assessment_id | Control in assessment |
| compliance.assessment_controls | platform.controls | N:1 | control_id | Referenced control |
| compliance.findings | compliance.assessments | N:1 | assessment_id | Finding from assessment |
| compliance.findings | platform.controls | N:1 | control_id | Finding against control |
| compliance.gaps | compliance.findings | N:1 | finding_id | Gap from finding |
| compliance.remediation_plans | compliance.gaps | N:1 | gap_id | Remediation for gap |
| compliance.remediation_plans | iam.users | N:1 | responsible_user_id | Remediation owner |
| compliance.evidence_items | compliance.assessments | N:1 | assessment_id | Evidence for assessment |
| compliance.evidence_items | compliance.findings | N:1 | finding_id | Evidence for finding |
| compliance.evidence_items | platform.controls | N:1 | control_id | Evidence for control |
| compliance.evidence_items | platform.attachments | N:1 | attachment_id | Evidence file |
| compliance.evidence_items | iam.users | N:1 | submitted_by | Evidence submitter |

### 7.9 Reporting Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| reporting.report_schedules | reporting.report_definitions | N:1 | report_def_id | Schedule for a report |
| reporting.report_schedules | platform.organizations | N:1 | org_id | Schedule per org |
| reporting.report_executions | reporting.report_schedules | N:1 | schedule_id | Execution of schedule |
| reporting.report_executions | reporting.report_definitions | N:1 | report_def_id | Execution of report |
| reporting.dashboard_configurations | platform.organizations | N:1 | org_id | Dashboard per org |
| reporting.dashboard_configurations | iam.users | N:1 | user_id | Dashboard owner |
| reporting.saved_filters | iam.users | N:1 | user_id | Filter owner |

### 7.10 Audit Relationships

| Source Entity | Target Entity | Type | FK Column | Description |
|---|---|---|---|---|
| audit.audit_logs | iam.users | N:1 | user_id | Acting user |
| audit.audit_logs | platform.organizations | N:1 | org_id | Organization scope |

---

## 8. Entity Relationship Diagram (Textual)

```
                         ┌─────────────────────┐
                         │ platform.organizations│
                         │  (org hierarchy)      │
                         └──────┬───┬──────┬────┘
                      org_id    │   │      │   org_id
              ┌─────────────────┘   │      └─────────────────┐
              │                     │                        │
              ▼                     ▼                        ▼
    ┌─────────────────┐  ┌──────────────────┐    ┌──────────────────┐
    │   iam.users     │  │platform.controls │    │platform.bus_units│
    │                 │  │                  │    │                  │
    └──┬──┬──┬──┬─────┘  └──┬──────┬───┬───┘    └──────────────────┘
       │  │  │  │            │      │   │
       │  │  │  │     ┌──────┘      │   └──────────┐
       │  │  │  │     ▼             ▼              ▼
       │  │  │  │  risk.risk    cyber.cyber    compliance.
       │  │  │  │  _controls    _controls      assessment_
       │  │  │  │                               controls
       │  │  │  │
       │  │  │  └──────► workflow.tasks (assigned_to)
       │  │  └─────────► audit.audit_logs (user_id)
       │  └────────────► risk.risks (owner_user_id)
       └───────────────► cyber.incidents (reporter_user_id)

    ┌───────────────┐     ┌───────────────┐     ┌───────────────┐
    │  risk.risks   │◄────│cyber.incidents│     │privacy.breach │
    │               │     │ linked_risk_id│     │  _records     │
    │               │◄────┤               │◄────│linked_cyber_  │
    │               │     └───────────────┘     │incident_id    │
    │               │◄──────────────────────────│linked_risk_id │
    └───────┬───────┘                           └───────────────┘
            │ source_module + source_entity_id
            │ (polymorphic provenance)
            ▼
    ┌───────────────────────────────┐
    │ Originating modules:          │
    │ ERM, CYBER, BCM, PRIVACY     │
    └───────────────────────────────┘

    ┌───────────────────────────────────────────────────┐
    │          Polymorphic Patterns                      │
    │  (entity_type + entity_id on target table)        │
    │                                                    │
    │  workflow.workflow_instances ──► any entity         │
    │  audit.audit_logs           ──► any entity         │
    │  platform.attachment_links  ──► any entity         │
    │  platform.comments          ──► any entity         │
    │  platform.tag_assignments   ──► any entity         │
    └───────────────────────────────────────────────────┘
```

---

## 9. Referential Integrity Strategy

| Pattern | Enforcement | Notes |
|---|---|---|
| Direct FK (same schema) | Database-level FK constraint | Standard referential integrity |
| Cross-schema FK | Database-level FK constraint | Explicit schema qualification in constraint |
| Polymorphic (entity_type + entity_id) | Application-level + CHECK constraint on entity_type | No DB-level FK; validated in service layer |
| JSON array references (e.g., linked_risks) | Application-level only | Validated during write operations |
| Soft-delete cascade | Application-level | Dependent records soft-deleted by service logic |
| Temporal history | SQL Server system-versioned tables | Automatic history; no manual FK management |
