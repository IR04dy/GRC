# Assumptions and Constraints
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
This document captures the technical, business, and regulatory assumptions and constraints that guide the architecture and design of the TSS GRC Platform.

---

## Assumptions

### Business Assumptions

| ID | Assumption | Impact if Wrong |
|----|-----------|-----------------|
| BA-01 | TSS will sell the platform primarily to medium and large enterprises in Saudi Arabia | Product design, language priority, and compliance focus would need adjustment |
| BA-02 | Customers will require modular licensing — not all organizations need all modules | Licensing architecture would need redesign if bundled-only |
| BA-03 | Arabic language support is a competitive differentiator, not optional | Removing Arabic would simplify development but lose market positioning |
| BA-04 | Customers will have in-house IT teams capable of on-premises deployment | If not, managed service/SaaS becomes the primary model sooner |
| BA-05 | The Saudi GRC market will continue to grow driven by Vision 2030 and NCA mandates | Market shrinkage would affect ROI projections |
| BA-06 | Customers expect a web-based application accessible via modern browsers | Desktop-only or thick-client expectations would change architecture |
| BA-07 | Initial deployments will serve single organizations (not multi-tenant SaaS) | Multi-tenant SaaS readiness is designed in but not the first deployment mode |

### Technical Assumptions

| ID | Assumption | Impact if Wrong |
|----|-----------|-----------------|
| TA-01 | Java 17+ and Spring Boot 3.5 are stable and supported for enterprise use | Framework instability would require migration effort |
| TA-02 | SQL Server 2019 is available in customer environments | May need to support SQL Server 2022 or Azure SQL |
| TA-03 | Customers have Windows Server or Linux environments for deployment | May need containerization sooner than planned |
| TA-04 | LDAP/Active Directory is the dominant identity provider in target organizations | If SAML/OIDC is primary, SSO integration priority changes |
| TA-05 | Server-side rendering (Thymeleaf) is acceptable for Phase 1 UI | If SPA is required from day one, frontend architecture changes significantly |
| TA-06 | NetBeans remains a viable IDE for enterprise Java development | IntelliJ/Eclipse compatibility should be maintained as fallback |
| TA-07 | Email (SMTP) is the primary notification channel | If SMS/WhatsApp is primary, notification architecture priority shifts |
| TA-08 | File storage will initially be local/NAS; object storage (S3/Azure Blob) is future | If cloud storage is required immediately, storage abstraction is critical |

### Regulatory Assumptions

| ID | Assumption | Impact if Wrong |
|----|-----------|-----------------|
| RA-01 | NCA Essential Cybersecurity Controls (ECC) will remain the primary cyber framework in Saudi Arabia | New framework versions may require control library updates |
| RA-02 | Saudi PDPL regulations will stabilize and enforcement will increase | Reduced enforcement would lower privacy module demand |
| RA-03 | Data residency within Saudi Arabia will be required for government and regulated sectors | If not required, cloud-only SaaS becomes more viable |
| RA-04 | ISO standards (27001, 22301, 31000, 27701) will remain relevant compliance targets | Framework changes are managed through the configurable control library |

---

## Constraints

### Technical Constraints

| ID | Constraint | Rationale |
|----|-----------|-----------|
| TC-01 | **Microsoft SQL Server 2019** is the target RDBMS | Customer environment standardization; no PostgreSQL or MySQL in Phase 1 |
| TC-02 | **Java 17+** is the minimum Java version | Spring Boot 3.5 requirement; long-term support |
| TC-03 | **Spring Boot 3.5** is the application framework | Enterprise Java standard with strong ecosystem |
| TC-04 | **Maven** is the build tool | NetBeans compatibility and enterprise standard |
| TC-05 | **Server-side rendered HTML** for Phase 1 frontend | Reduces frontend complexity; aligns with Spring Boot ecosystem |
| TC-06 | **Modular monolith** architecture for Phase 1 | Reduces operational complexity while maintaining modularity |
| TC-07 | **Single database with multiple schemas** | Simplifies deployment and cross-module queries while preserving logical separation |
| TC-08 | **nvarchar** for all text columns | Required for Arabic/English Unicode support |
| TC-09 | **Flyway** for database migrations | Version-controlled, repeatable database changes |

### Business Constraints

| ID | Constraint | Rationale |
|----|-----------|-----------|
| BC-01 | Platform must support Arabic and English from day one | Core market requirement; cannot be retrofitted easily |
| BC-02 | Modules must be independently licensable | Business model requirement for flexible pricing |
| BC-03 | Platform must be deployable on-premises | Data residency and customer preference requirements |
| BC-04 | All actions must be auditable | Regulatory and enterprise governance requirement |
| BC-05 | The platform must not claim formal certification | Certification is the customer's responsibility |
| BC-06 | Phased delivery is required | Full platform cannot be built in a single release |

### Regulatory Constraints

| ID | Constraint | Rationale |
|----|-----------|-----------|
| RC-01 | Data at rest must be encryptable | NCA and PDPL requirements |
| RC-02 | Data in transit must use TLS 1.2+ | Security best practice and regulatory expectation |
| RC-03 | Access control must support segregation of duties | Governance and audit requirements |
| RC-04 | Audit logs must be tamper-resistant | Regulatory evidence integrity requirement |
| RC-05 | Personal data must be identifiable and manageable | PDPL right-to-access and right-to-erasure requirements |
| RC-06 | Session management must meet enterprise security standards | Timeout, concurrent session limits, secure cookies |

### Resource Constraints

| ID | Constraint | Rationale |
|----|-----------|-----------|
| RSC-01 | Development team size is assumed to be small-to-medium (5-15 developers) | Influences architecture simplicity decisions |
| RSC-02 | Team is assumed to have Java/Spring Boot expertise | Influences technology choices |
| RSC-03 | SQL Server DBA expertise is assumed to be available | Required for database tuning and operations |

---

## Dependency Assumptions

| Dependency | Assumption | Mitigation |
|-----------|-----------|------------|
| SMTP server | Available in customer environment | Document SMTP configuration requirements |
| LDAP/AD | Available for user authentication integration | Provide local authentication as fallback |
| File system / NAS | Available for document storage | Abstract storage layer for future cloud migration |
| SSL certificates | Customer provides for HTTPS | Document TLS requirements |
| Network infrastructure | Customer provides load balancing and DNS | Document network architecture requirements |
