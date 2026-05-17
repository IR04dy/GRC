# 09 — Automation Rules

The 20 rules from the implementation prompt §14 are now **data**,
seeded in `platform.automation_rules` (V96). Each rule is
inspectable via `GET /api/v1/automation/rules` and editable via
`PATCH /api/v1/automation/rules/:id`. The engine
(`AutomationRuleEngine`, F8) listens to `DomainEvent` broadcasts
from any module and dispatches matching rules asynchronously.

## Architecture (F8)

```
domain service → ApplicationEventPublisher.publishEvent(DomainEvent.of(...))
                                                              │
                                                              ▼
                                                  AutomationRuleEngine
                                                  ├── find enabled rules for triggerEvent
                                                  ├── conditionMatches(condition_json, payload)
                                                  ├── AutomationActionExecutor.execute(rule, event)
                                                  ├── record AutomationRuleRun (SUCCESS|SKIPPED|FAILED)
                                                  └── audit_log.recordEvent (action=RULE_FIRED)
```

## The 20 rules (seeded source = PROMPT_14)

| Code | Trigger | Condition | Action | Priority |
|---|---|---|---|---|
| AR-01 | `ASSET_CREATED` | `contains_personal_data=true` | CREATE_RECORD ProcessingActivity + DPIA | 10 |
| AR-02 | `ASSET_CLASSIFIED` | `criticality=CRITICAL` | CREATE_RECORD BIA + DRP + NCA assessment | 10 |
| AR-03 | `VENDOR_CLASSIFIED` | `processes_personal_data=true` | CREATE_RECORD ProcessorAssessment | 10 |
| AR-04 | `INCIDENT_CREATED` | `affects_personal_data=true, incident_type=CYBER` | CREATE_RECORD DataBreach + ESCALATE DPO (72h) | 5 |
| AR-05 | `CLOUD_SERVICE_LINKED` | `backs_critical_service=true` | CREATE_RECORD CccAssessment + DrpReview + SupplierResilienceReview | 10 |
| AR-06 | `RISK_APPETITE_BREACHED` | — | ESCALATE Risk Committee | 5 |
| AR-07 | `BIA_UPDATED` | `rto_gap=true` | CREATE_RECORD Gap + TreatmentPlan | 20 |
| AR-08 | `SUPPLIER_EVIDENCE_GAP` | `evidence_type=BCP` | CREATE_RECORD CapaTask (SUPPLIER_BCP_GAP) | 15 |
| AR-09 | `EVIDENCE_EXPIRED` | `framework=NCA` | NOTIFY EvidenceOwner + ControlOwner | 20 |
| AR-10 | `DSR_SLA_NEAR` | — | ESCALATE Privacy Manager | 10 |
| AR-11 | `RISK_ACCEPTANCE_EXPIRED` | — | CREATE_RECORD RiskReview + AUDIT WARN | 20 |
| AR-12 | `CAPA_OVERDUE` | — | ESCALATE Owner + Owner's Manager | 15 |
| AR-13 | `VULN_SLA_BREACH` | — | ESCALATE Cybersecurity Manager | 10 |
| AR-14 | `SUPPLIER_SLA_REPEATED` | — | FLAG risk_score increment | 20 |
| AR-15 | `POLICY_PUBLISHED` | — | CREATE_RECORD AttestationCampaign | 15 |
| AR-16 | `BOARD_APPROVAL_REQUIRED` | — | CREATE_RECORD DigitalSignatureTransaction | 5 |
| AR-17 | `EVIDENCE_REJECTED` | — | FLAG gap.status=OPEN | 20 |
| AR-18 | `INCIDENT_CLASSIFIED` | `severity=CRITICAL` | CREATE_RECORD CrisisEvent + AUDIT ERROR | 1 |
| AR-19 | `CROSS_BORDER_REQUEST` | — | CREATE_RECORD CrossBorderTransferAssessment | 10 |
| AR-20 | `CLOUD_REGION_CHANGED` | — | CREATE_RECORD DataResidencyReview + PrivacyReassessment | 10 |

## How to publish an event from a service

```java
@Service
@RequiredArgsConstructor
class IncidentService {
  private final ApplicationEventPublisher events;

  @Transactional
  public Incident classify(Long id, String severity) {
    Incident i = repo.findById(id).orElseThrow();
    i.setSeverity(severity);
    repo.save(i);

    events.publishEvent(DomainEvent.of(
        i.getOrganisationId(),
        "INCIDENT_CLASSIFIED",
        "Incident",
        i.getId(),
        Map.of("severity", severity,
               "incident_type", i.getIncidentType(),
               "affects_personal_data", i.getAffectsPersonalData())));
    return i;
  }
}
```

## Condition DSL (today)

JSON object. Every key in the object must equal-match the payload
value. Supported value types:

| Expected | Matches |
|---|---|
| `true` / `false` | payload value coerced from `true`/`"true"`/`1`/`"yes"` etc. |
| `"CRITICAL"` | exact case-sensitive string |
| `42` | numeric equality |

Future extensions (deferred): comparison operators (`>`, `<`,
`contains`, regex), boolean composition (`any` / `all`), date
windowing.

## Action types (today)

| Type | What it does today | Future side-effect |
|---|---|---|
| `AUDIT` | Writes audit row with given severity + message | (already complete) |
| `CREATE_RECORD` | Records a stub audit row | Module-side handler creates the record |
| `NOTIFY` | Records a stub audit row | Notification fan-out |
| `ESCALATE` | Records a stub audit row | Reassign workflow task to escalation role |
| `FLAG` | Records a stub audit row | Mutate entity field via reflection helper |

## Admin operations

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/automation/rules` | list (no filter) |
| `GET /api/v1/automation/rules/:id` | one rule + last 50 runs |
| `PATCH /api/v1/automation/rules/:id` | enable/disable, edit name/desc/priority/condition/action |
| `DELETE /api/v1/automation/rules/:id` | soft-delete |
| `GET /api/v1/automation/runs?page&size` | recent fire history |
| `GET /api/v1/automation/stats` | success/skipped/failed counts |

## Auditability

Every fire emits one audit row in F7 (`platform.audit_log`) with
`action_type='RULE_FIRED'`, the rule code in `entity_code`, the
event's correlation_id, and severity escalated to ERROR if the
action failed. QA / compliance officers can trace any rule fire
end-to-end without reading code.

## Adding a custom rule

```
POST /api/v1/automation/rules (admin-only)
{
  "ruleCode": "CR-001",
  "nameEn": "Auto-flag risks linked to >3 incidents in 30d",
  "triggerEvent": "INCIDENT_CREATED",
  "conditionJson": null,
  "actionJson": "{\"type\":\"AUDIT\",\"severity\":\"WARN\",\"message\":\"check link\"}",
  "priority": 30,
  "source": "CUSTOM"
}
```

---

**See also:** `09_Notification_Rules.md`, `09_Escalation_Rules.md`,
`platform.automation_rules` V96 migration.
