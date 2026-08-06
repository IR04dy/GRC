# Problem Statement
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
This document articulates the core problems that the TSS GRC Platform is designed to solve, identifying market gaps and pain points for target customers in Saudi Arabia and the broader Middle East region.

---

## Primary Problem

Organizations operating in Saudi Arabia face an increasingly complex governance, risk, and compliance landscape driven by:

- **National Cybersecurity Authority (NCA)** regulations and controls
- **Saudi Personal Data Protection Law (PDPL)** enacted in 2023
- **Vision 2030** digital transformation mandates requiring robust governance
- **Multiple overlapping international standards** (ISO 27001, ISO 22301, ISO 31000, ISO 27701)
- **Sector-specific regulatory requirements** from SAMA, CMA, CITC, and other bodies

Most organizations manage these requirements using **disconnected tools, spreadsheets, and manual processes**, leading to:

1. Fragmented risk visibility across departments
2. Duplicated effort in compliance assessments
3. Inability to demonstrate compliance posture to regulators in real time
4. Lack of unified reporting for executive decision-making
5. No traceability from risk to control to evidence

---

## Market Gaps

| Gap | Description |
|-----|-------------|
| **No unified Arabic-first GRC platform** | Existing global GRC tools treat Arabic as an afterthought; RTL support is poor or absent |
| **No NCA-aligned out-of-box framework** | Organizations must manually map NCA controls in generic tools |
| **Siloed risk domains** | Enterprise risk, cybersecurity risk, BCM, and privacy are managed in separate tools with no cross-visibility |
| **No shared control library** | Controls relevant to multiple frameworks (NCA, ISO 27001, PDPL) are duplicated across teams |
| **Limited local deployment options** | Many GRC tools are SaaS-only, creating data residency concerns for Saudi organizations |
| **High cost of ownership** | Global platforms require expensive licenses for the full suite even when only specific modules are needed |
| **Weak workflow support** | Most tools lack configurable approval workflows aligned with Saudi organizational hierarchies |
| **No Saudi PDPL-specific privacy module** | Privacy impact assessments and RoPA for PDPL are not natively supported |

---

## Customer Pain Points

### For CISOs and Risk Officers
- Cannot produce a unified risk dashboard across cyber, operational, and strategic risks
- Spend excessive time aggregating data from multiple systems for board reports
- Cannot trace a control deficiency to its impact on multiple compliance frameworks simultaneously

### For Compliance Officers
- Must manually maintain compliance evidence across ISO 27001, NCA, PDPL, and sector regulations
- Assessment cycles are manual, error-prone, and difficult to track
- Cannot demonstrate continuous compliance — only point-in-time

### For Privacy Officers (DPOs)
- No integrated tool for PDPL compliance including RoPA, PIAs, consent management, and breach notification
- Privacy risks are disconnected from the enterprise risk register

### For BCM Managers
- BIA and BCP processes are document-based, not system-managed
- BCM exercises and their outcomes are not tracked systematically
- BCM incidents are not linked to enterprise risk or cyber incidents

### For IT and Security Teams
- Vulnerability and threat data is siloed from risk assessments
- Cyber incidents are tracked separately from enterprise incident management
- Asset inventory and classification are disconnected from control assessments

### For Executive Leadership
- No single source of truth for organizational risk posture
- Compliance status across frameworks is not visible in real time
- Cannot make risk-informed decisions due to data fragmentation

---

## Consequences of Inaction

| Consequence | Impact |
|-------------|--------|
| Regulatory penalties | Non-compliance with NCA, PDPL, or sector regulations |
| Reputational damage | Inability to demonstrate governance maturity |
| Operational inefficiency | Duplicated manual effort across risk and compliance teams |
| Poor decision-making | Lack of integrated risk intelligence for leadership |
| Audit failures | Inability to produce evidence trails on demand |
| Incident mismanagement | Uncoordinated response to cross-domain incidents |

---

## How TSS GRC Platform Addresses These Problems

The platform is designed as a **modular, integrated, Arabic-first GRC solution** that:

1. **Unifies risk visibility** across ERM, cybersecurity, BCM, privacy, and compliance
2. **Provides a shared control library** mapped to NCA, ISO 27001, ISO 22301, ISO 31000, ISO 27701, and PDPL
3. **Supports native Arabic/English** with proper RTL/LTR rendering
4. **Enables modular licensing** so organizations pay only for what they need
5. **Offers configurable workflows** aligned with local organizational structures
6. **Supports both SaaS and on-premises** deployment for data residency requirements
7. **Delivers real-time dashboards and reporting** for executive and regulatory stakeholders
8. **Maintains full audit trails** for every action, decision, and evidence item
