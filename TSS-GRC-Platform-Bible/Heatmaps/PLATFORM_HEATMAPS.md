# Platform Heatmaps

## Scale

`1 Critical / absent · 2 weak · 3 developing · 4 strong · 5 leading`

Scores are provisional and require the evidence request to lift confidence.

## Product-package maturity

| Package | Domain | Architecture | Security | UX | Saudi | Commercial | Overall |
|---|---:|---:|---:|---:|---:|---:|---:|
| Platform Kernel | 4 | 2 | 3 | 3 | 3 | 2 | 2.8 |
| ERM | 4 | 3 | 3 | 3 | 4 | 3 | 3.3 |
| Compliance | 4 | 3 | 3 | 3 | 4 | 3 | 3.3 |
| Policy | 4 | 3 | 3 | 3 | 4 | 3 | 3.3 |
| Internal Audit | 3 | 2 | 2 | 3 | 3 | 2 | 2.5 |
| Change Enablement | 3 | 2 | 1 | 2 | 3 | 1 | 2.0 |
| Governance/Committees | 4 | 2 | 1 | 2 | 4 | 1 | 2.3 |
| Cyber GRC | 4 | 3 | 3 | 3 | 5 | 3 | 3.5 |
| SOC Ops | 4 | 3 | 3 | 3 | 4 | 3 | 3.3 |
| BCM/Crisis | 5 | 3 | 3 | 3 | 5 | 3 | 3.7 |
| Privacy/PDPL | 4 | 3 | 3 | 3 | 5 | 3 | 3.5 |
| Data Governance/NDI | 3 | 2 | 2 | 2 | 4 | 2 | 2.5 |
| TPRM/SCM | 4 | 2 | 3 | 3 | 4 | 2 | 3.0 |
| SAMA Resilience | 3 | 2 | 2 | 3 | 5 | 2 | 2.8 |
| ESG/Sustainable Finance | 5 | 3 | 3 | 2 | 4 | 3 | 3.3 |
| Access Governance | 4 | 3 | 3 | 3 | 4 | 3 | 3.3 |
| Ethics/Fraud/Complaints | 3 | 3 | 2 | 3 | 3 | 2 | 2.7 |
| Regulatory Affairs | 3 | 3 | 2 | 3 | 4 | 2 | 2.8 |
| Saudi Financial Services | 3 | 2 | 2 | 3 | 4 | 2 | 2.7 |
| AI/Model Governance | 4 | 3 | 3 | 3 | 3 | 3 | 3.2 |

## Acceptance heatmap

| Acceptance dimension | Score | Blocking evidence |
|---|---:|---|
| Functional breadth | 4.5 | customer UAT and real operating data |
| Decision integrity | 1.5 | F-001 to F-003 |
| Tenant isolation | 3.0 | full negative matrix |
| Package independence | 1.5 | universal entitlement and boundary tests |
| Arabic completeness | 3.0 | persona tests and bilingual validation |
| Accessibility | 2.0 | independent WCAG 2.2 AA report |
| Regulatory content | 3.0 | official version lifecycle and SME approval |
| Performance/scalability | 2.0 | production-like benchmark |
| Backup/DR | 2.5 | witnessed restore/failover |
| Security assurance | 2.5 | penetration test and abuse-case closure |
| Sales enablement | 2.5 | outcome demos, pricing and references |

## Risk concentration

| Risk | Likelihood | Impact | Exposure | Treatment |
|---|---|---|---|---|
| invalid governance decision | High | Critical | Extreme | P0 immediate |
| unlicensed module access | High | High | Extreme | P0 package kernel |
| cross-tenant leak | Medium | Critical | High | negative assurance suite |
| stale regulatory content | Medium | High | High | Saudi Content Office |
| low user adoption | High | High | Extreme | role shells and flagship UX |
| modularisation programme stalls | Medium | High | High | lighthouse package and ratchet |
| microservice rewrite distraction | Medium | High | High | ADR-001 governance |
| public/sensitive case disclosure | Medium | Critical | High | case-level security |
| production recovery failure | Unknown | Critical | High | witnessed DR |
| AI data leakage/hallucination | Medium | High | High | gateway and evaluation |

