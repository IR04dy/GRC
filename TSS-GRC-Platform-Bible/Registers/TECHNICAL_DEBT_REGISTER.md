# Technical Debt Register

| ID | Debt | Type | Risk | Treatment |
|---|---|---|---|---|
| TD-001 | 734 cross-module imports and 14 bidirectional pairs | Architecture | High | dependency ratchet and owner contracts |
| TD-002 | kernel/platform package contains broad shared concerns | Architecture | High | strict kernel admission and commercial dependency ban |
| TD-003 | duplicate approval mechanisms | Domain | High | unified decision/authority contract |
| TD-004 | duplicate lifecycle modules and authorities | Domain/Data | High | canonical ownership ADRs and migration |
| TD-005 | frontend central router with 300+ routes | Frontend | High | package route registry |
| TD-006 | very large ESG/NDI and other pages | Frontend/UX | Medium-High | journey-based workspaces |
| TD-007 | central translation files near 6k lines | Localization | Medium | namespaced package bundles and glossary |
| TD-008 | package entitlement annotations concentrated in SOC Ops | Commercial/Security | Critical | universal interceptor and manifests |
| TD-009 | governance engine test gap | Quality | Critical | adversarial multi-principal suite |
| TD-010 | dev schema bootstrap differs from production Flyway path | Operations | Medium | production-like migration CI |
| TD-011 | SQL Server single-store reporting coupling | Data | Medium | read models and warehouse feed |
| TD-012 | legacy static frontend remains in repository | Maintainability | Low-Medium | archive after dependency check |
| TD-013 | some optional infrastructure affects health semantics historically | Operations | Medium | readiness versus liveness separation |
| TD-014 | mock/demo files remain near production pages | Product integrity | Medium | explicit demo-data provider and build check |
| TD-015 | main bundle chunk warning | Performance | Medium | route/package code splitting and budgets |

Debt has an owner, interest cost and exit criteria. “Deliberate” does not mean “untracked”.

