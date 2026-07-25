# Product Backlog

## Priority model

- **P0:** blocks trustworthy decisions, tenant safety, packaging or acceptance.
- **P1:** required for market-ready core packages and flagship UX.
- **P2:** differentiates and scales after foundations.

Effort uses engineering team-weeks and includes design/test unless noted.

## Epic A — Governance trust boundary

| ID | Story | Acceptance | Priority | Effort | Depends |
|---|---|---|---|---:|---|
| A-01 | Bind change vote to authenticated actor | request cannot supply actor; duplicate DB constraint; forged/self-vote tests | P0 | 1 | — |
| A-02 | Replace generic approval transitions | named commands and state-aware permissions; invalid path tests | P0 | 2 | A-01 |
| A-03 | Implement emergency ratification | ECAB authority, expiry, CAB ratification and PIR | P0 | 1.5 | A-02 |
| A-04 | Snapshot committee membership | effective term/role snapshot at meeting | P0 | 1 | — |
| A-05 | Compute quorum server-side | attendance reconciled to snapshot; client quorum ignored | P0 | 1.5 | A-04 |
| A-06 | Bind committee vote to member/user ID | one eligible vote; COI/recusal; dissent retained | P0 | 1.5 | A-04 |
| A-07 | Enforce minutes/signature SoD | preparer cannot independently approve; hash verified | P0 | 1 | A-05 |
| A-08 | Add governance adversarial suite | forged actor, fake quorum, duplicates, concurrency, tenant | P0 | 2 | A-01..A-07 |

## Epic B — Commercial package foundation

| ID | Story | Acceptance | Priority | Effort | Depends |
|---|---|---|---|---:|---|
| B-01 | Create package manifest schema | versioned schema covers dependency, route, permission, data and lifecycle | P0 | 1 | ADR-002 |
| B-02 | Build universal entitlement interceptor | unlicensed controller/export/job denied server-side | P0 | 2 | B-01 |
| B-03 | Generate navigation from manifest | only installed/permitted packages appear; deep links denied safely | P1 | 2 | B-01 |
| B-04 | Package health/dependency API | admin sees missing/incompatible dependencies | P1 | 1 | B-01 |
| B-05 | Kernel-only boot test | app starts and migrates with zero commercial packages | P1 | 2 | B-02 |
| B-06 | Disable/read-only policy | disabled package behaviour and data retention proven | P1 | 2 | B-04 |
| B-07 | Package upgrade contract | N-1 upgrade and rollback/forward-fix evidence | P1 | 2 | B-01 |
| B-08 | Package usage/entitlement audit | activation/change/access events retained | P1 | 1 | B-02 |

## Epic C — Architecture boundaries

| ID | Story | Acceptance | Priority | Effort | Depends |
|---|---|---|---|---:|---|
| C-01 | Add dependency ratchet | build fails above baseline or for new forbidden pair | P0 | 1 | — |
| C-02 | Define kernel API | identity, tenancy, audit, workflow, evidence and notification interfaces | P1 | 3 | ADR-001 |
| C-03 | Publish object ownership catalogue | every canonical object has owner and consumers | P1 | 2 | DR-002..DR-005 |
| C-04 | Remove peer repository imports from lighthouse package | zero forbidden imports | P1 | 4 | C-02,C-03 |
| C-05 | Add transactional outbox | crash-safe, idempotent dispatch and metrics | P1 | 3 | ADR-004 |
| C-06 | Build cross-domain read-model service | dashboards avoid peer repositories; freshness shown | P1 | 4 | C-05 |
| C-07 | Consolidate approval contracts | common actor/authority/decision model and adapters | P1 | 4 | A-08,C-02 |
| C-08 | Consolidate issue/CAPA authority | one owner and typed source contracts | P1 | 4 | C-03 |

## Epic D — UX and Arabic

| ID | Story | Acceptance | Priority | Effort | Depends |
|---|---|---|---|---:|---|
| D-01 | Build role-based experience shells | navigation changes by persona and entitlement | P1 | 3 | B-03 |
| D-02 | Build unified My Work | approvals/tasks/evidence/deadlines with stable source links | P1 | 4 | C-07 |
| D-03 | Create object workspace pattern | identity, next action, evidence, decisions and history | P1 | 2 | — |
| D-04 | Create guided decision page | authority, SoD, evidence, alternatives, rationale | P0 | 2 | A-02,A-06 |
| D-05 | Create bilingual field contract | material fields, translation status and errors | P0 | 2 | — |
| D-06 | Implement Arabic search normalization | variants/digits/acronyms tested without changing source | P1 | 3 | Data design |
| D-07 | Split Change workspace | journey routes replace large modal/tab implementation | P1 | 3 | D-03,D-04 |
| D-08 | Split NDI 15-tab page | cycle/evidence/gap/submission workspaces | P1 | 4 | D-03 |
| D-09 | Split ESG workspace and bundle | route budgets and calculation lineage | P1 | 5 | D-03 |
| D-10 | Independent WCAG 2.2 AA audit | five journeys in Arabic/English; findings closed | P1 | 5 | D-01..D-05 |
| D-11 | Accessible executive metrics | chart/table parity and metric lineage | P1 | 3 | C-06 |
| D-12 | Govern Arabic terminology | versioned glossary and SME workflow | P1 | 1 then ongoing | Saudi office |

## Epic E — Data and evidence

| ID | Story | Acceptance | Priority | Effort | Depends |
|---|---|---|---|---:|---|
| E-01 | Implement evidence graph MVP | typed links with scope/freshness/provenance | P1 | 4 | C-03 |
| E-02 | Enforce immutable evidence versions | decisions point to checksum/version | P0 | 2 | — |
| E-03 | Add file quarantine and malware scan | unsafe files never become available | P0 | 2 | infrastructure |
| E-04 | Implement classification policy | field/object/export handling by class | P1 | 3 | — |
| E-05 | Implement retention/legal hold | deletion preflight, hold and certificate | P1 | 5 | C-03 |
| E-06 | Build governed metric catalogue | definition, owner, denominator, lineage, freshness | P1 | 3 | C-06 |
| E-07 | Package data export/removal | complete manifest, reconciliation and signed report | P1 | 3 | B-06,E-05 |
| E-08 | Production-like migration CI | Flyway path, N-1 data and restore compatibility | P0 | 2 | — |

## Epic F — Security and operations

| ID | Story | Acceptance | Priority | Effort | Depends |
|---|---|---|---|---:|---|
| F-01 | Correct Change export permission | `EXPORT` required; row/field filters and audit | P0 | 0.5 | — |
| F-02 | Two-tenant assurance suite | CRUD/search/export/file/cache/job/event/ws negatives | P0 | 3 | — |
| F-03 | Case-level restricted access | whistleblower/fraud/breach need-to-know tests | P1 | 3 | E-04 |
| F-04 | Privileged support access | approval, reason, expiry, masking and audit | P1 | 2 | — |
| F-05 | SSO/MFA reference profiles | OIDC/SAML/SCIM and step-up acceptance | P1 | 3 | customer IdP |
| F-06 | Production workload benchmark | agreed volumes, P95, resource and bottleneck report | P1 | 3 | target infra |
| F-07 | Witnessed backup/restore | RPO/RTO and application compatibility proven | P0 | 2 | target infra |
| F-08 | Upgrade/rollback rehearsal | immutable artifacts, migration and rollback/forward-fix | P1 | 2 | E-08 |
| F-09 | Independent penetration test | business logic/API/public portal scope; Critical/High closed | P1 | external | P0 closure |
| F-10 | Release evidence pack automation | artifact, tests, migrations, SBOM, findings and probes | P1 | 2 | CI |

## Epic G — Saudi content and market

| ID | Story | Acceptance | Priority | Effort | Depends |
|---|---|---|---|---:|---|
| G-01 | Establish Saudi Content Office | named owners, review and update SLA | P0 | executive | — |
| G-02 | Build content-pack model | official source/version/applicability/activation | P1 | 4 | G-01 |
| G-03 | Revalidate NCA ECC pack | official current controls, Arabic source and evidence mapping | P1 | SME 3 | G-02 |
| G-04 | Revalidate SAMA packs | CSF/outsourcing/cloud/resilience scope and workflows | P1 | SME 5 | G-02 |
| G-05 | Revalidate PDPL pack | rights, DPIA, breach, transfer and processor workflows | P1 | SME 4 | G-02 |
| G-06 | DGA acceptance pack | beneficiary, Arabic and accessibility evidence | P1 | 3 | D-10 |
| G-07 | Lighthouse customer programme | three pilots with signed success metrics | P1 | ongoing | core GA |
| G-08 | Sales outcome demos | five persona stories and evidence scripts | P1 | 2 | flagship UX |
| G-09 | Pricing research | Saudi interviews, procurement analysis and willingness-to-pay | P1 | product 4 | package map |
| G-10 | Partner enablement | implementation certification and reference architectures | P2 | 6 | GA |

## Epic H — AI

| ID | Story | Acceptance | Priority | Effort | Depends |
|---|---|---|---|---:|---|
| H-01 | AI gateway | allowlist, residency, redaction, audit, cost and kill switch | P1 | 4 | policy |
| H-02 | Tenant/permission-filtered retrieval | leakage and prompt-injection tests | P1 | 4 | E-01,H-01 |
| H-03 | Regulatory-change copilot | cited draft, human accept/edit/reject and evaluation | P2 | 3 | G-02 |
| H-04 | Evidence reuse copilot | scope/freshness-aware suggestions and evaluation | P2 | 3 | E-01 |
| H-05 | Arabic executive brief | citations, terminology test and no unsupported claims | P2 | 3 | E-06 |
| H-06 | AI incident/drift operations | suspend/revert and notification | P1 | 2 | H-01 |

## Definition of done

A backlog item is not done until source, automated tests, live/prod-like proof, documentation, telemetry and security/UX acceptance appropriate to its risk are attached.

