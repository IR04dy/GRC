# Saudi GRC Platform — Implementation Package

This folder mirrors the layout specified in the Saudi GRC platform
implementation prompt §4. Every document here is grounded in the
running codebase — see the cross-references at the top of each file
for the Java packages, migrations, and frontend pages that implement
the contract.

## How to read this package

- **§1-3 — product framing** (`01-Product-Blueprint`,
  `02-Module-Architecture`, `03-Workflow-Catalogue`) — what the
  platform does, organised by business capability.
- **§4 — data model** (`04-Data-Model`) — entities, API objects,
  ER diagrams referencing the actual JPA classes.
- **§5-6 — UI + evidence** (`05-UI-Screens`, `06-Evidence-Library`) —
  screens, navigation, evidence library structure.
- **§7 — regulatory mapping** (`07-Regulatory-Mapping`) — every
  authority document the platform tracks (NCA, PDPL, SAMA, DGA, NRC,
  ISO, SOC 2) with placeholder IDs flagged.
- **§8 — access control** (`08-RBAC`) — role matrix + approval matrix.
- **§9 — automation** (`09-Automation-Rules`) — the 20 rules from
  prompt §14, now seeded as data in `platform.automation_rules`.
- **§10 — reports + dashboards** (`10-Reports-Dashboards`).
- **§11-12 — backlog + handoff** (`11-Implementation-Backlog`,
  `12-Developer-Handoff`).
- **§13 — API spec + payloads** (`13-API-Specification`).
- **§14 — UAT test cases** (`14-Test-Cases`).
- **§15 — sample seed data** (`15-Sample-Data`).
- **§16 — open assumptions** (`16-Assumptions`).

## Status legend

Every section uses the same tags so readers know what is real vs.
what is placeholder:

| Tag | Meaning |
|---|---|
| ✅ **Built** | Implemented in the codebase; QA checklist exists. |
| 🟡 **Partial** | Scaffolded but missing pieces called out inline. |
| 🟦 **Placeholder** | Catalogued (e.g. NCA citation seeded) but the official text/control IDs are not yet imported. UI banner warns the user. |
| 🔜 **Planned** | On the backlog; not yet started. |

## Regulatory completeness disclaimer

Per the implementation prompt: the platform does NOT claim
regulatory completeness. NCA ECC v2 / CSCC / CCC / OTCC / DCC / TCC,
PDPL, SAMA frameworks, DGA, NRC, and the ISO suite are seeded with
**placeholder** citations until the official control libraries are
imported. The `platform.authority_documents.is_placeholder` flag and
the `translation_status` enum (`EN_ONLY / AR_AUTO / AR_HUMAN_REVIEWED
/ AR_OFFICIAL`) carry that state through the whole stack — the UI
banner stays lit until QA flips the flag.

## Mapping back to source

| Section | Primary source |
|---|---|
| 02 Module Architecture | `tss-grc-backend/src/main/java/com/tss/grc/module/` (35 modules) |
| 03 Workflow Catalogue | service classes + `docs/grc/grc-3-sprint-plan.md` |
| 04 Data Model | `@Entity` classes + Flyway migrations V1-V97 |
| 05 UI Screens | `tss-grc-ui-v2/src/pages/` |
| 06 Evidence Library | `module/evidence/` + `module/*/evidence` |
| 07 Regulatory Mapping | `platform.authority_documents` + `platform.control_mappings` (F1+F2) |
| 08 RBAC | `module/iam/` + `module/approvals/` |
| 09 Automation Rules | `platform.automation_rules` (V96, F8) — 20 rules seeded |
| 13 API Specification | SpringDoc OpenAPI at `/swagger-ui.html` |
| 14 Test Cases | Mockito test suite + `docs/*/QA_CHECKLIST.md` |

---

Last refreshed: alongside the Framework Intersection plan F10 slice.
