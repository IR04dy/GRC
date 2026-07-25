# Module Independence Matrix

## Current versus target

| Package | Baseline level | Kernel dependency | Required peer | Optional peers | Owned data | Separation priority |
|---|---:|---|---|---|---|---|
| Kernel | L1–L2 | — | none | none | identity, workflow, evidence, audit, config | P0 cleanse |
| ERM | L1–L2 | yes | none | Compliance, Governance | risk schemas | lighthouse |
| Compliance | L1–L2 | yes | none | Policy, ERM, Audit | obligations/controls | lighthouse |
| Policy | L2 | yes | none | Compliance | policy | P1 |
| Audit | L1 | yes | none | ERM, Compliance, Governance | audit | P1 consolidate |
| Change | L1 | yes | none | Governance, Cyber, BCM, TPRM | change | P0 security; P1 split |
| Governance | L1 | yes | none | all decision consumers | committee/decision | P0 security |
| Cyber GRC | L2 | yes | none | Compliance, ERM, Privacy | cyber | P1 |
| SOC Ops | L2–L3 | yes | none | Cyber GRC | socops | entitlement reference |
| BCM | L2 | yes | none | ERM, TPRM | bcm | P1 |
| Privacy | L2 | yes | none | TPRM, Data Gov | privacy | lighthouse |
| Data Gov/NDI | L1 | yes | none | Privacy | ndi/data | P1 consolidate |
| TPRM | L1–L2 | yes | none | BCM, Privacy, Cyber | party/vendor | P1 consolidate |
| SAMA Resilience | L1 | yes | none | TPRM, BCM | outsourcing/opres | P1 |
| ESG | L2 | yes | none | ERM, Governance | sust | P2 |
| Access Governance | L2 | yes | none | Cyber | iam | P1 |
| Ethics | L2 | yes | none | Governance | ethics/cases | P1 security |
| Regulatory Affairs | L2 | yes | none | Compliance, Governance | reg* | P2 |
| Saudi FS | L1 | yes | none | ERM, Governance | vertical schemas | P2 validate |
| AI Governance | L2 | yes | none | Data Gov | ai model | P2 |

No commercial package has a mandatory peer dependency in the target. Where a process benefits from another package, it uses an optional contract and degrades gracefully.

## Interaction matrix

`Q = synchronous public query · E = event · R = read projection · — = no direct dependency`

| Consumer → Provider | ERM | Compliance | Governance | Cyber | BCM | Privacy | TPRM | Data |
|---|---|---|---|---|---|---|---|---|
| ERM | — | Q/E control effectiveness | Q decision | E exposure | E service impact | E privacy risk | E concentration | R |
| Compliance | E risk context | — | Q decision | E controls | E evidence | E obligations | E due diligence | R |
| Audit | Q/R | Q/R | Q decision | Q/R | Q/R | restricted Q | Q/R | R |
| Change | Q impact | Q impact | Q decision | Q impact | Q impact | Q impact | Q impact | R asset |
| Governance | R brief | R brief | — | R brief | R brief | restricted R | R brief | R |
| BCM | E exposure | R controls | Q decision | Q asset | — | — | Q dependency | Q asset |
| Privacy | E risk | Q obligation | Q decision | E breach | Q continuity | — | Q processor | Q asset |
| TPRM | E enterprise risk | Q controls | Q decision | Q cyber | Q resilience | Q privacy | — | Q party data |

This matrix is a target contract map. Current direct imports must be catalogued and reduced against it.

## Independence score calculation

- 20% no forbidden source dependencies;
- 15% owned schema/repositories;
- 15% public API/event contracts;
- 15% server entitlement;
- 10% manifest/routes/permissions;
- 10% optional-dependency degradation;
- 10% install/upgrade/disable tests;
- 5% data export/removal policy.

L3 requires at least 70% and no Critical failure.

