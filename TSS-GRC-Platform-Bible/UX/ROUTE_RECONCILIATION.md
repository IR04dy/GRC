# Route reconciliation matrix — F2-07a

**Measured 2026-08-08** against `tss-grc-ui-v2/src/App.tsx`, `components/layout/Sidebar.tsx` and every
non-test source in `src/`. Regenerate with `Appendix/scripts/route_reconciliation.py`; **every figure
below is interpolated from that run, not transcribed.** Gated by
`tss-grc-ui-v2/src/__tests__/routeReachability.test.ts`, an independent TypeScript implementation that
agrees with this one and fails if a new orphan appears.

---

## 0. The headline: F2-07's population is 18 screens, not 144

[[F2-07]] is filed **P1 Critical** on the claim that *"roughly 144 routed screens are reachable only by
typing a URL"*, and that *"nobody can tell which is which"*. The second half was true — there was no
artefact. The first half is **not**.

| | Count |
|---|---:|
| Routes registered in `App.tsx` | **329** |
| − redirects (`<Navigate>`, `RedirectPreservingQuery`) — not screens | 9 |
| − bare routes: rendered without the authenticated shell, **by design** | 14 |
| **= authenticated screens** | **306** |
|   reachable from the **sidebar** | **183** |
|   reachable from a **link or a dashboard tile** only | **103** |
|   **no way in at all** | **18** |

**Why the finding was ~7× too large: navigation in this application has three surfaces, and F2-07
counts one.** Besides `Sidebar.tsx` there are module-dashboard link tiles (`href:` props on `Kpi` and
action components) and ordinary in-page links — plus registries such as `lib/workflowCatalog.ts`, whose
`route:` field the workflow hub renders as `<Link to={w.route!}>`. A screen opened by a tile on its
module dashboard is reachable; it is simply not a menu item, which for a detail screen is correct
design rather than a defect.

⚠ **This does not vacate F2-07.** Its *other* half stands untouched and is the more serious one: a
**183-item sidebar is beyond navigable**, and the workspace IA in the recommendation is still
the right remedy. What changes is the size and shape of the remediation list — from *"classify 144
screens"* to *"add 18 missing links and take 0 product decisions"*.

---

## 1. How the measurement was arrived at — four passes, four answers

Recorded because the intermediate answers were each plausible, and three of them were wrong.

| Pass | Definition of "reachable" | Answer | What it got wrong |
|---|---|---:|---|
| 1 | a `Sidebar.tsx` entry | 139 | counted dashboard tiles and detail links as unreachable; missed 4 `RedirectPreservingQuery` redirects |
| 2 | + navigation **syntax** (`navigate()`, `to=`) | 53 | **19 false positives** — the `/workflows/*` wizards are linked through a *registry*, not link syntax |
| 3 | + **any** `'/…'` string literal | 6 | **false negatives** — an entry in the `publicPaths.ts` auth allowlist is not a way in |
| 4 | navigational references only, allowlists named and excluded, bare set read from `BARE_PREFIXES` | **18** | — |

Three transferable lessons:

* 🔑 **A registry-driven link is invisible to a scan for link syntax.** Pass 2 reported every workflow
  wizard as stranded; `lib/workflowCatalog.ts` holds the path as data and `WorkflowHubPage.tsx` renders
  a `<Link>` over it. That was a third of the answer.
* 🔑 **When the code already declares the classification you are inferring, infer nothing.** A
  hand-written pattern for "anonymous by design" missed two ~400-line screens
  (`/ethics/whistleblower/submit`, `/bcm/crisis/mobile`). `lib/publicPaths.ts` declares the set as
  `BARE_PREFIXES` — 9 prefixes — and is the authority used here.
* ⚠ **The same file can be authoritative for one question and misleading for another.**
  `publicPaths.ts` is excluded as a *navigation* source (an allowlist is not a link) and trusted as the
  *anonymous* authority. Both treatments are named in the script rather than left to a glob.

➕ A fourth lesson came from mutation-testing the gate: `/BARE_PREFIXES[^=]*=\s*\[/` **still matched
after the declaration was renamed away**, because line 3 of `publicPaths.ts` is a comment mentioning
the name. **A textual scan cannot tell a declaration from a mention** — the pattern is anchored on
`export const` in both implementations.

---

## 2. The 18 orphaned screens — 9,253 lines with no way in

| Route | Component | Lines | Class | Note |
|---|---|---:|---|---|
| `/ai/models/:externalId` | `AiModelDetailPage` | 952 | **PROMOTE** | `/ai/models` list route exists; link the row |
| `/rrp/cycles/:id` | `RrpCycleDetailPage` | 790 | **PROMOTE** | `/rrp/cycles` list route exists; link the row |
| `/bcm/exercises/run/:runId/facilitate` | `FacilitatorConsolePage` | 769 | **PROMOTE** | 🔴 see the F2-161 note in §2 |
| `/bcm/exercises/run/:runId/observe` | `ObserverDashboardPage` | 742 | **PROMOTE** | 🔴 see the F2-161 note in §2 |
| `/cpty/counterparties/:id` | `CptyCounterpartyDetailPage` | 710 | **PROMOTE** | link from the counterparty list |
| `/complaints/:id` | `ComplaintDetailPage` | 632 | **PROMOTE** | `/complaints` list route exists; link the row |
| `/cyber/attack-surface/assets/:externalId` | `AttackSurfaceAssetDetailPage` | 556 | **PROMOTE** | link from the attack-surface table |
| `/regreporting/filings/:id` | `RegReportingFilingDetailPage` | 512 | **PROMOTE** | link from the filings register |
| `/opres/assessments/:id` | `OpResAssessmentDetailPage` | 488 | **PROMOTE** | link from the assessments list |
| `/privacy/subprocessors/:externalId` | `SubprocessorDetailPage` | 474 | **PROMOTE** | sidebar has `/privacy/subprocessors`; link the row |
| `/reginspect/inspections/:id` | `RegInspectWorkspaceDetailPage` | 449 | **PROMOTE** | link from the inspections workspace |
| `/ethics/whistleblower/cases/:id` | `WhistleblowerCaseDetailPage` | 432 | **PROMOTE** | link from the case register |
| `/bcm/exercises/run/:runId/participate` | `ParticipantViewPage` | 351 | **PROMOTE** | 🔴 see the F2-161 note in §2 |
| `/privacy/dsrs/:id` | `DsrDetailPage` | 334 | **PROMOTE** | ⚠ no list ROUTE - the list is a `/privacy` tab |
| `/privacy/breaches/:id` | `PrivacyBreachDetailPage` | 332 | **PROMOTE** | ⚠ no list route - link from the `/privacy` breach tab |
| `/privacy/ropa/:id` | `RopaDetailPage` | 254 | **PROMOTE** | ⚠ no list route - link from the `/privacy` RoPA tab |
| `/workflows/erm/risk-treatment/:riskId` | `RiskTreatmentWizard` | 252 | **PROMOTE** | pre-bound variant of a catalogued wizard; link from the risk register |
| `/workflows/erm/risk-acceptance/:riskId` | `RiskAcceptanceWizard` | 224 | **PROMOTE** | pre-bound variant of a catalogued wizard; link from the risk register |

**18 PROMOTE** — all but two are `:param` **detail** screens whose list exists and does not link
to them. That is a materially different defect from *"absent from the sidebar"*, and cheaper: one link
each, not an IA redesign.

🔴 **The BCM exercise runtime is the sharpest item, because of what happened to it two days before
this measurement.** `FacilitatorConsolePage` (769), `ObserverDashboardPage` (742) and
`ParticipantViewPage` (351) — 1,862 lines — have **no way in from anywhere in the UI**. [[F2-161]] fixed
*who may drive an exercise run* on these exact screens on 2026-08-06, converting the guard so a
facilitator could reach their own run instead of only a platform administrator. ⚙ **The authorisation
was corrected on three screens that have no entry point.** Two instruments looked at the same code from
opposite ends and neither could see what the other saw: a guard audit cannot tell that nothing routes
to the page, and a navigation audit cannot tell the guard was wrong.

---

## 3. The 103 link-only screens are reachable — here is what reaches them

This is the population F2-07 counts as URL-only. Top sources:

| Navigation surface | Screens it reaches |
|---|---:|
| `lib/workflowCatalog.ts` | 22 |
| `lib/notifications.ts` | 9 |
| `pages/socops/SocOpsDashboardPage.tsx` | 6 |
| `pages/socops/SocOpsDetailPage.tsx` | 6 |
| `pages/sharia/ShariaDashboardPage.tsx` | 5 |
| `pages/bcm/BcmWorkspacePage.tsx` | 4 |
| `pages/ethics/EthicsPage.tsx` | 4 |
| `lib/approvals.ts` | 4 |
| `lib/searchIndex.ts` | 4 |
| `pages/knowledge-base/KbEditorPage.tsx` | 3 |
| `pages/audit-committee/AuditCommitteeDashboardPage.tsx` | 3 |
| `pages/committees/CommitteeCatalogPage.tsx` | 3 |

`lib/workflowCatalog.ts` alone accounts for 22 — the F8 wizard estate, launched from the workflow
hub. Any future reconciliation must treat a registry as a navigation surface.

---

## 4. What this changes in the register

* [[F2-07]] — the *"144 reachable only by URL"* figure is **superseded by 18**; the
  183-item sidebar and the workspace-IA recommendation are unaffected.
* [[F2-07a]] — **delivered**: this matrix, the generator, and the CI gate.
* [[F2-07b]] — already landed 8 socops pages into the sidebar. Its inline comment cites
  `/socops/awareness` as *"in the nav"*; it is in the socops **dashboard**, not the sidebar. Loose
  wording rather than a defect — recorded so the next reader does not re-open it.
* [[F2-161]] — cross-reference the runtime entry-point gap in §2.
