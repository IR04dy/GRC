# AI Transformation

## Current assessment

The repository contains more AI-governance substance than a superficial review suggests: suggestion records, human accept/reject behaviour, model inventory, audit, drift, lifecycle, validation and recertification surfaces. The opportunity is credible. The risk is attaching AI to unreliable identities, duplicated objects or stale evidence.

## Strategy

AI is a horizontal capability governed by the AI & Model Governance package. Domain packages opt into named use cases. There is no ungoverned universal chatbot with unrestricted database access.

## Priority use cases

| Use case | Value | Human control |
|---|---|---|
| Regulatory change summarisation | faster triage | compliance reviewer validates source/applicability |
| Obligation-to-control mapping | reduce duplicate mapping | mapper accepts/edits with rationale |
| Evidence classification and reuse suggestion | reduce audit fatigue | control owner/reviewer confirms scope and freshness |
| Risk narrative and scenario draft | improve consistency | risk manager owns final assessment |
| Control-test anomaly detection | focus testing | tester investigates; model does not determine effectiveness |
| Audit planning insights | connect risk/change/incidents | CAE owns plan |
| Third-party questionnaire assistance | faster due diligence | vendor and reviewer attest answers |
| Arabic/English drafting | accelerate bilingual content | human review for material/legal text |
| Executive briefing | concise linked summary | every assertion cites platform objects/evidence |
| Case summarisation | investigator efficiency | restricted access; no identity exposure outside case |

## Prohibited autonomous actions

- approving or signing;
- accepting risk;
- closing a finding, incident or privacy request;
- determining legal/regulatory compliance;
- deciding whether regulator/data-subject notification is required;
- changing permission or package entitlement;
- sending external/regulator communication;
- deleting or reclassifying evidence.

## AI gateway

All model calls pass through a governed gateway that applies:

- tenant and user authorization;
- use-case allowlist;
- data-class and residency policy;
- prompt template/version;
- redaction and minimisation;
- provider/model allowlist;
- token/cost quotas;
- response validation;
- citation retrieval;
- audit and evaluation sampling;
- kill switch.

## Retrieval architecture

Retrieval uses tenant- and permission-filtered indexes. Chunk metadata includes tenant, package, object, version, language, classification, valid period and source. Authorization is evaluated before retrieval and again before response assembly.

## Output contract

Every material suggestion stores:

- use case and input object references;
- model/provider/version;
- prompt template version;
- source citations;
- confidence/calibration where meaningful;
- generated output;
- limitation notice;
- actor who accepted, edited or rejected;
- final human-authored result;
- timestamps and tenant.

## Evaluation

Maintain Arabic and English golden sets for each use case. Measure:

- factual grounding and citation correctness;
- omission of material exceptions;
- hallucination;
- Arabic legal/domain terminology;
- bias/fairness where people are affected;
- sensitive-data leakage;
- prompt injection resilience;
- task time saved;
- human override and rejection rate.

Production deployment requires threshold, owner and rollback. Drift or incident can suspend one use case without disabling all AI.

## Agent roadmap

### Stage 1 — Copilot

Read-only summaries, drafting, search and mapping suggestions.

### Stage 2 — Guarded task agent

Creates draft work items through public commands; human reviews before commit.

### Stage 3 — Bounded automation

Executes low-risk reversible actions under explicit policy, such as reminder scheduling or evidence-request drafting.

### Stage 4 — Multi-agent assurance

Specialist agents propose cross-domain impacts while a policy engine and accountable human control final actions. This stage is contingent on stable package contracts and evidence graph maturity.

## AI backlog gates

No use case enters production without DPIA/privacy review where applicable, threat model, evaluation set, human fallback, model register entry, cost limit, auditability and incident runbook.

