# 05 — Navigation Menu
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Source: `tss-grc-ui-v2/src/components/layout/Sidebar.tsx`. The
sidebar is built from a flat array of groups; each group is gated by
the JWT `enabled_modules` claim (F9).

## Top-level groups + items

```
1. Executive
   1.1 /dashboard               Executive Dashboard

2. Risk
   2.1 /risk                    Risk Register
   2.2 /risk-scenarios          Risk Scenarios
   2.3 /emerging-risk           Emerging Risk
   2.4 /kri (inside /risk)      KRIs

3. Resilience
   3.1 /bcm                     BCM Workspace
   3.2 /bcm-dependency          Dependency Graph
   3.3 /dr-plans                DR Plans
   3.4 /crisis                  Crisis Room
   3.5 /committees              Committees

4. Supply Chain
   4.1 /scm                     Supplier Register
   4.2 /tprm                    Third-Party Risk
   4.3 /contract-clauses        Contract Clauses

5. Cyber
   5.1 /cyber                   Cyber Workspace
   5.2 /cyber/vulns/v2          Vulnerabilities

6. Privacy
   6.1 /privacy                 PDPL Workspace
   6.2 /data-lifecycle          Data Lifecycle
   6.3 /data-sharing            Data Sharing

7. Compliance & Policy
   7.1 /compliance              Compliance Workspace
   7.2 /regulatory/library      Regulatory Library (F1)        ← new
   7.3 /controls/library        Control Library (F2)           ← new
   7.4 /compliance/posture      Compliance Posture (F6)        ← new
   7.5 /framework-packs         Framework Packs
   7.6 /policy-templates        Policy Templates
   7.7 /policy-management       Policy Management
   7.8 /regulatory-change       Regulatory Change
   7.9 /control-crosswalk       Control Crosswalk (legacy)
   7.10 /evidence               Evidence Repository
   7.11 /attestations           Attestation Campaigns
   7.12 /ethics                 Ethics
   7.13 /esg                    ESG / Sustainability
   7.14 /ndi                    NDI / نضيء

8. Audit
   8.1 /audit-workspace         Audit Workspace
   8.2 /audit-lifecycle         Audit Lifecycle
   8.3 /evidence-chain          Evidence Chain
   8.4 /nca-ecc-export          NCA ECC Filing
   8.5 /admin/audit-log         Audit Log (F7)                  ← unified

9. Incidents
   9.1 /incidents               Incident Triage
   9.2 /rca                     Root Cause Analysis

10. Knowledge
    10.1 /knowledge-base        Knowledge Base
    10.2 /board-pack            Board Pack
    10.3 /intake                Intake

11. Administration
    11.1 /admin                 Users / Roles / Permissions
    11.2 /admin/licensing       Module Licensing (F9 admin UI) 🔜 frontend follow-up
    11.3 /admin/automation      Automation Rules (F8 admin UI) 🔜 frontend follow-up
    11.4 /settings              Settings
```

## Behaviour

- **Active state.** `NavLink` from React Router; the active item gets
  a left-edge accent + bold weight.
- **Collapse.** A single accordion per group remembers state in
  `localStorage` so users return to their layout.
- **RTL.** Bootstrap RTL stylesheet is loaded conditionally; the
  whole sidebar flips when `lang === 'ar'`. Iconography is mirror-
  safe (`DirectionalArrow` component for the few directional icons).
- **License gating** (F9). When the JWT `enabled_modules` claim
  doesn't include the item's `moduleCode`, the item is hidden. The
  whole group is hidden when every child item is gated.

## Naming conventions

- Group titles use translation keys (`navResilience`,
  `navCompliancePolicy`, …); per-item labels use shorter keys
  (`bcmSidebar`, `regLibrarySidebar`, …).
- Saudi-native Arabic labels per `MEMORY.md` — for example
  *Resilience* renders as «الجاهزية» rather than the literal
  «الصمود».

## Mobile

Sidebar collapses to a 56 px-wide rail at < 980 px and converts to
a full-screen drawer on tap.

---

**See also:** `Sidebar.tsx` (single source of truth), `translations/en.ts`,
`translations/ar.ts`.
