# Route reconciliation matrix — F2-07a

**Measured 2026-08-08** against `tss-grc-ui-v2/src/App.tsx`, `components/layout/Sidebar.tsx` and every
non-test source in `src/`. Regenerate with `Appendix/scripts/route_reconciliation.py`; **every figure
below is interpolated from that run, not transcribed.** Gated by
`tss-grc-ui-v2/src/__tests__/routeReachability.test.ts`, an independent TypeScript implementation that
agrees with this one and fails if a new orphan appears.

---

## 0. The headline: F2-07's population is 0 screens, not 144

[[F2-07]] is filed **P1 Critical** on the claim that *"roughly 144 routed screens are reachable only by
typing a URL"*, and that *"nobody can tell which is which"*. The second half was true — there was no
artefact. The first half is **not**.

| | Count |
|---|---:|
| Routes registered in `App.tsx` | **330** |
| − redirects (`<Navigate>`, `RedirectPreservingQuery`) — not screens | 9 |
| − bare routes: rendered without the authenticated shell, **by design** | 14 |
| **= authenticated screens** | **307** |
|   reachable from the **sidebar** | **183** |
|   reachable from a **link or a dashboard tile** only | **122** |
|   **no way in at all** | **0** |

**Why the finding was ~7× too large: navigation in this application has FOUR surfaces, and F2-07
counts one.** Besides `Sidebar.tsx` there are module-dashboard link tiles (`href:` props on `Kpi` and
action components), ordinary in-page links, registries such as `lib/workflowCatalog.ts` whose `route:`
field the workflow hub renders as `<Link to={w.route!}>` — and **`rowHref`**, `DataTable`'s per-row
navigation prop, which is how a list links its own detail screen (34 sites). A screen opened by a tile
on its module dashboard, or by clicking its row in a table, is reachable; it is simply not a menu item,
which for a detail screen is correct design rather than a defect.

⚠ **This does not vacate F2-07.** Its *other* half stands untouched and is the more serious one: a
**183-item sidebar is beyond navigable**, and the workspace IA in the recommendation is still
the right remedy. What changes is the size and shape of the remediation list — from *"classify 144
screens"* to *"add 5 missing links and take 0 product decisions"*, all 5 of which F2-163 has added.

---

## 1. How the measurement was arrived at — six passes, and pass 4 SHIPPED WRONG

Recorded because the intermediate answers were each plausible, and **the one that got written into a
finding, a register row and a CI gate was among the wrong ones.**

| Pass | Definition of "reachable" | Answer | What it got wrong |
|---|---|---:|---|
| 1 | a `Sidebar.tsx` entry | 139 | counted dashboard tiles and detail links as unreachable; missed 4 `RedirectPreservingQuery` redirects |
| 2 | + navigation **syntax** (`navigate()`, `to=`) | 53 | **19 false positives** — the `/workflows/*` wizards are linked through a *registry*, not link syntax |
| 3 | + **any** `'/…'` string literal | 6 | dismissed for false negatives (an entry in the `publicPaths.ts` auth allowlist is not a way in) — but **this was the closest answer, and tightening moved away from it** |
| 4 | navigational references only, allowlists named, bare set from `BARE_PREFIXES` | ~~18~~ | ⚠⚠ **SHIPPED, AND WRONG BY 13** — blind to `rowHref`, the prop that links a list row to its detail screen |
| 5 | + `rowHref`-shaped props and `useNavigate()` aliases | **5** | — |
| 6 | pass 5 cross-checked against an independent wide definition | **5** | both agree; 11 disagreements in between were all genuine `<Link to={`…`}>` and each was opened by hand |

🔑🔑 **The correction is the same error the finding it corrects was filed for.** F2-07 was ~7× too
large because it counted one navigation surface. Pass 4 counted three and missed the fourth — the
*dominant* one for detail screens. ⚙ **A plausible answer that confirms the story you are already
telling is where measurement stops.** All 18 were `:param` routes and the narrative was tidy
("detail screens absent from their own list"), so the 13 counter-examples were never opened one at a
time. Eighteen is a small enough population to adjudicate by hand; it was not.

⚙ **What could not have caught it, and this bounds what the two techniques are worth.** The gate's
non-vacuity check passed — it saw 300+ link targets. Mutation testing the gate passed 7/7.
**Mutation proves a gate notices changes to itself; it cannot prove the gate's definition matches the
world.** What caught it was opening the first list page the finding accused, before writing a link
into it. Two defences are now in place: a **witness assertion** per fragile pattern (a route reachable
only via `rowHref`, and one only via a `nav()` alias — losing either pattern now fails the run instead
of inflating the orphan count), and **two implementations with deliberately different definitions**
that must agree.

Three further transferable lessons:

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

## 2. Orphaned screens today: **0**

**Zero.** Every parameterised and static screen in the application is reachable from the sidebar, a dashboard tile, an in-page link, a registry or its list row. The five that were not are in §2a; the thirteen that only appeared to be are in §2b.

### 2a. The 5 that were real — 2,338 lines, now linked

| Route | Component | Lines | Verdict | Reached from |
|---|---|---:|---|---|
| `/bcm/exercises/run/:runId/facilitate` | `FacilitatorConsolePage` | 769 | **REAL - FIXED** | 🔴 `ExerciseRunsPage` - the run list that did not exist |
| `/bcm/exercises/run/:runId/observe` | `ObserverDashboardPage` | 742 | **REAL - FIXED** | 🔴 `ExerciseRunsPage` |
| `/bcm/exercises/run/:runId/participate` | `ParticipantViewPage` | 351 | **REAL - FIXED** | 🔴 `ExerciseRunsPage` |
| `/workflows/erm/risk-treatment/:riskId` | `RiskTreatmentWizard` | 252 | **REAL - FIXED** | `RiskDetailPage` - the only path writing `risk_lc.treatment_plans` |
| `/workflows/erm/risk-acceptance/:riskId` | `RiskAcceptanceWizard` | 224 | **REAL - FIXED** | `RiskDetailPage` - the only path writing a formal acceptance |

🔴 **The BCM exercise runtime was the sharpest item, because of what happened to it two days before
the measurement.** `FacilitatorConsolePage` (769), `ObserverDashboardPage` (742) and
`ParticipantViewPage` (351) — 1,862 lines — had **no textual reference anywhere in `src/`**, not merely
no link: `listRunsForExercise` and `startRun` were imported by those three pages and nothing else, so
**no screen listed a run**. [[F2-161]] fixed *who may drive an exercise run* on these exact screens on
2026-08-06, converting the guard so a facilitator could reach their own run instead of only a platform
administrator. ⚙ **The authorisation was corrected on three screens that had no entry point.** Two
instruments looked at the same code from opposite ends and neither could see what the other saw: a
guard audit cannot tell that nothing routes to the page, and a navigation audit cannot tell the guard
was wrong. `ExerciseRunsPage` is now their list, reached from the exercise row in `/bcm?tab=exercises`.

🔴 **The other two were worse than unreachable — they were the *governed* path.**
`/workflows/erm/risk-{treatment,acceptance}/:riskId` are the only UI routes that write a canonical
`risk_lc.treatment_plans` row and a formal, time-boxed, CRO-approved acceptance. `RiskService.close`
**requires** such a plan before a HIGH or over-appetite risk may close. Unbound, both wizards save a
generic inbox draft and toast *"open from a risk to record it"* — **which nothing in the UI let the
user do.** Both are now launched from `RiskDetailPage`. ⚠ The asymmetry that remains is filed as
**F2-164**: the plain *Accept* button on the same page is a bare status transition at `RISK:WRITE` and
it **exempts** the risk from the close gate, so the reachable path bypasses the control the
unreachable one enforced.

### 2b. The 13 that were this script's own false positives

Every one is linked from exactly the list the finding said should link it — by `rowHref`, which the
first pass could not see. Kept in the record because a wrong measurement that is quietly deleted
teaches nothing.

| Route | Component | Lines | Verdict | The link that was always there |
|---|---|---:|---|---|
| `/ai/models/:externalId` | `AiModelDetailPage` | 952 | **FALSE POSITIVE** | `rowHref` on `AiModelRegisterPage:367` - linked all along |
| `/rrp/cycles/:id` | `RrpCycleDetailPage` | 790 | **FALSE POSITIVE** | `rowHref` on `RrpCyclesPage:228` **and** `RrpDashboardPage:215` |
| `/cpty/counterparties/:id` | `CptyCounterpartyDetailPage` | 710 | **FALSE POSITIVE** | `rowHref` on `CptyCounterpartiesPage:263` + `CptyDashboardPage:285` |
| `/complaints/:id` | `ComplaintDetailPage` | 632 | **FALSE POSITIVE** | `rowHref` on `ComplaintsRegisterPage:218` |
| `/cyber/attack-surface/assets/:externalId` | `AttackSurfaceAssetDetailPage` | 556 | **FALSE POSITIVE** | `rowHref` on `AttackSurfacePage:519` |
| `/regreporting/filings/:id` | `RegReportingFilingDetailPage` | 512 | **FALSE POSITIVE** | `rowHref` on `RegReportingFilingsPage:146` + the dashboard, twice |
| `/opres/assessments/:id` | `OpResAssessmentDetailPage` | 488 | **FALSE POSITIVE** | `rowHref` on `OpResAssessmentsPage:238`; `nav(...)` on the dashboard |
| `/privacy/subprocessors/:externalId` | `SubprocessorDetailPage` | 474 | **FALSE POSITIVE** | `rowHref` on `SubprocessorsPage:235` |
| `/reginspect/inspections/:id` | `RegInspectWorkspaceDetailPage` | 449 | **FALSE POSITIVE** | `rowHref` on `RegInspectInspectionsPage:296` + the dashboard |
| `/ethics/whistleblower/cases/:id` | `WhistleblowerCaseDetailPage` | 432 | **FALSE POSITIVE** | `rowHref` on `WhistleblowerCasesPage:229` |
| `/privacy/dsrs/:id` | `DsrDetailPage` | 334 | **FALSE POSITIVE** | `rowHref` on `PrivacyPage:384` - the tab table DOES link it |
| `/privacy/breaches/:id` | `PrivacyBreachDetailPage` | 332 | **FALSE POSITIVE** | `rowHref` on `PrivacyPage:402` |
| `/privacy/ropa/:id` | `RopaDetailPage` | 254 | **FALSE POSITIVE** | `rowHref` on `PrivacyPage:414` |

---

## 3. The 122 link-only screens are reachable — here is what reaches them

This is the population F2-07 counts as URL-only. Top sources:

| Navigation surface | Screens it reaches |
|---|---:|
| `lib/workflowCatalog.ts` | 22 |
| `lib/notifications.ts` | 9 |
| `pages/socops/SocOpsDashboardPage.tsx` | 6 |
| `pages/socops/SocOpsDetailPage.tsx` | 6 |
| `pages/sharia/ShariaDashboardPage.tsx` | 5 |
| `pages/privacy/PrivacyPage.tsx` | 5 |
| `pages/bcm/BcmWorkspacePage.tsx` | 4 |
| `pages/ethics/EthicsPage.tsx` | 4 |
| `pages/openbanking/ObDashboardPage.tsx` | 4 |
| `lib/approvals.ts` | 4 |
| `lib/searchIndex.ts` | 4 |
| `pages/knowledge-base/KbEditorPage.tsx` | 3 |

`lib/workflowCatalog.ts` alone accounts for 22 — the F8 wizard estate, launched from the workflow
hub. Any future reconciliation must treat a registry as a navigation surface.

---

## 4. What this changes in the register

* [[F2-07]] — the *"144 reachable only by URL"* figure is **superseded by 5**, now **0**; the
  183-item sidebar and the workspace-IA recommendation are unaffected and remain open.
* [[F2-07a]] — **delivered**: this matrix, the generator, and the CI gate.
* [[F2-07b]] — already landed 8 socops pages into the sidebar. Its inline comment cites
  `/socops/awareness` as *"in the nav"*; it is in the socops **dashboard**, not the sidebar. Loose
  wording rather than a defect — recorded so the next reader does not re-open it.
* [[F2-163]] — **filed at 18, corrected to 5, and closed.** ⚠ Read §1 before quoting any figure from
  this document: the finding shipped with a headline that this script's own patterns had inflated by
  13, into a register row and a CI gate, and it was caught by opening a file rather than by any test.
* [[F2-164]] — **filed by this slice, not fixed.** Making the treatment/acceptance wizards reachable
  exposed that the *reachable* `Accept` button bypasses the very close gate the newly-reachable
  wizard satisfies. A navigation fix surfaced an authorisation defect; see §2a.
* [[F2-161]] — cross-reference the runtime entry-point gap in §2a.
