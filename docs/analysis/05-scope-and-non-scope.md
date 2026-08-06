# Scope and Non-Scope
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
This document defines the boundaries of the TSS GRC Platform — what is included in the product scope and what is explicitly excluded.

---

## In Scope

### Platform Capabilities

| Area | Scope |
|------|-------|
| **Modular GRC Platform** | 10 integrated modules covering risk, cybersecurity, BCM, privacy, compliance, data governance, workflow, reporting, licensing, and administration |
| **Multilingual Support** | Full Arabic and English support with RTL/LTR layout switching |
| **Role-Based Access Control** | Fine-grained RBAC with module-based authorization |
| **Workflow Engine** | Configurable approval and review workflows with escalation and SLA tracking |
| **Audit Trail** | Complete audit logging of all system actions with tamper-proof storage |
| **Reporting & Dashboards** | Built-in dashboards, heatmaps, charts, and export to PDF/Excel/Word |
| **Unified Risk View** | Cross-module risk aggregation and visualization |
| **Shared Control Library** | Controls mapped to multiple frameworks (NCA, ISO 27001, ISO 22301, PDPL, ISO 31000) |
| **Modular Licensing** | Per-module licensing with subscription management |
| **Multi-Organization** | Support for multiple organizations/tenants with data isolation |
| **File/Evidence Management** | Attachment and evidence storage with metadata tracking |
| **Notification System** | Email and in-app notifications with templates |
| **Configuration Management** | Runtime configuration, branding, and feature toggles |

### Modules In Scope

| # | Module | Scope |
|---|--------|-------|
| 1 | Enterprise Risk Management | Risk register, assessments, treatments, appetite, heatmaps |
| 2 | Cybersecurity Risk & Compliance | Asset inventory, vulnerabilities, threats, cyber incidents, NCA alignment |
| 3 | Business Continuity Management | BIA, BCP, recovery strategies, exercises, dependency mapping |
| 4 | Data Governance / Data Office | Data inventory, classification, quality rules, lineage, stewardship |
| 5 | Privacy & Personal Data Protection | PIA/DPIA, RoPA, consent, breach management, PDPL alignment |
| 6 | Compliance & Audit Management | Framework assessments, findings, gaps, remediation, evidence |
| 7 | Workflow & Case Management | Workflow definitions, approval chains, case tracking |
| 8 | Reporting & Analytics | Dashboards, reports, scheduled reports, export |
| 9 | Licensing & Subscription | Module licensing, subscription management, feature gating |
| 10 | Administration & Configuration | System settings, user management, organization setup, branding |

### Technical Scope

| Area | Scope |
|------|-------|
| **Backend** | Java 17+, Spring Boot 3.5, REST APIs |
| **Frontend** | Server-side rendered HTML templates (Thymeleaf) |
| **Database** | Microsoft SQL Server 2019 |
| **Authentication** | Spring Security with RBAC, LDAP/AD integration readiness |
| **Migration** | Flyway for database versioning |
| **Build** | Maven, NetBeans-compatible |
| **Deployment** | On-premises and SaaS-ready architecture |

### Compliance Framework Awareness

The platform is designed to help organizations align with:
- Saudi NCA Essential Cybersecurity Controls (ECC)
- Saudi NCA Critical Systems Cybersecurity Controls (CSCC)
- ISO 27001:2022 Information Security Management
- ISO 27701:2019 Privacy Information Management
- ISO 22301:2019 Business Continuity Management
- ISO 31000:2018 Risk Management
- Saudi Personal Data Protection Law (PDPL)

---

## Out of Scope

### Explicitly Excluded

| Area | Exclusion | Rationale |
|------|-----------|-----------|
| **Formal certification** | The platform does not claim ISO or NCA certification | Certification belongs to the implementing organization, not the tool |
| **Automated vulnerability scanning** | No built-in scanner; integrates with external scanners | Specialized tools (Nessus, Qualys, etc.) handle this better |
| **SIEM integration (Phase 1)** | No real-time SIEM event ingestion initially | Can be added in later phases via API |
| **Penetration testing tools** | No pen-test execution capability | Out of domain for GRC |
| **Full ERP integration** | No direct SAP/Oracle ERP connectors | Can be built as custom integrations later |
| **Mobile native apps** | No iOS/Android native applications | Responsive web is sufficient for Phase 1 |
| **AI/ML risk prediction** | No machine learning models for risk scoring | Can be added in future phases |
| **Custom report designer** | No drag-and-drop report builder in Phase 1 | Pre-built reports and dashboards first |
| **Third-party risk management** | No vendor/supplier risk module | Can be added as a future module |
| **IT asset discovery** | No network scanning or auto-discovery | Relies on manual or imported asset data |
| **Custom workflow BPMN designer** | No visual BPMN editor | Workflows configured via admin UI, not visual modeling |
| **Blockchain audit trail** | No blockchain-based immutability | SQL Server temporal tables provide sufficient audit |
| **Multi-cloud deployment orchestration** | No Kubernetes/container orchestration in Phase 1 | Traditional deployment first |
| **Real-time collaboration** | No simultaneous multi-user document editing | Standard form-based data entry |
| **Public-facing portal** | No external stakeholder self-service portal | Internal platform only |

### Deferred to Future Phases

| Item | Target Phase |
|------|-------------|
| SSO with SAML/OIDC | Phase 2-3 |
| SIEM event ingestion API | Phase 4 |
| Third-party risk management module | Post Phase 10 |
| Mobile-optimized views | Phase 8+ |
| AI-powered risk recommendations | Post Phase 10 |
| Custom report builder | Phase 9 |
| API marketplace for integrations | Post Phase 10 |
| Multi-region SaaS deployment | Phase 10 |

---

## Scope Management

- Scope changes must be evaluated against the phased implementation roadmap
- New module requests should follow the established modular architecture patterns
- Integration requests should use the defined API architecture
- Any scope additions must consider impact on database schema, licensing model, and RBAC
