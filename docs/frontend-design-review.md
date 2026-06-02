# TSS GRC Platform — Front-End Design Review & Improvement Plan

> **Scope:** `tss-grc-ui-v2` (React 18 + Vite + TypeScript), the bilingual (Arabic‑primary / English), RTL‑first GRC web application.
> **Lens:** GRC subject‑matter expert + UI/UX designer + data‑visualization specialist + accessibility & RTL architect.
> **Nature:** Front‑end **design** only — visual language, information design, interaction, motion, visualization, accessibility, RTL/bilingual. No backend or business‑logic changes.
> **Date:** 2026‑06‑02 · **Method:** 6 parallel domain audits across the full component tree + live DOM/token verification (304 routes, ~110 nav items, ~43 detail pages, ~70+ register pages).
>
> **Companion documents:**
> - [`frontend-visualization-playbook.md`](./frontend-visualization-playbook.md) — a committee prescribing, **page by page**, the signature charts/diagrams, interactive effects, and high‑end modern treatment. (This review says *what to fix*; the playbook says *what to build*.)
> - [`frontend-visual-craft-review.md`](./frontend-visual-craft-review.md) — a committee's **pixel/token‑level craft** audit (component sizing & proportion, alignment, colour matching, typography, spacing rhythm, RTL/responsive precision), with concrete design‑QA bugs and a token‑enforcement roadmap. (*How it's polished.*)

---

## 1. Executive summary

The platform sits on a **genuinely excellent design foundation** that is **inconsistently consumed**. The token system (`styles/tokens.css`), the RTL strategy (logical CSS properties), the information architecture (15 role‑owner nav groups modelled on ServiceNow IRM / OneTrust / Archer), and several flagship surfaces (Executive dashboard hero, Risk register, Policy detail page) are world‑class. But around that core, the product has accumulated **"three of everything"**: three dashboard architectures, four table engines (two of them unstyled), three form systems plus a bespoke `inputStyle` copied into 43+ files, three header components, three pill‑tab implementations, and 38 local copies of the status‑colour map.

The result: **the foundation can carry a best‑in‑class product, but today the platform does not feel like one product.** A "Critical" risk is rendered in three different reds; a "Closed" badge is grey on one page and green on another; five of six module dashboards are text‑lists rather than charts; the lifecycle Stepper/Timeline components exist but are used by **zero** detail pages; and the mobile Topbar clips the logout button.

**The good news:** almost every issue is a *consumption* problem, not a *capability* gap. The highest‑leverage work is **consolidation onto the systems that already exist** — which is cheaper and lower‑risk than building new ones, and compounds across the whole app.

### The five systemic themes

| # | Theme | One‑line | Evidence |
|---|-------|----------|----------|
| **T1** | **Tokens exist but are bypassed** | Charts/pages hardcode off‑brand hex/rgba; phantom token names (`--radius-*`, `--dur-md`) silently no‑op | `--cat-1`=`#0E5E5E` (on‑brand) but charts use `#2563eb/#ec4899`; `--radius-md`/`--dur-md` resolve to *nothing* (verified live) |
| **T2** | **"Three of everything" fragmentation** | Parallel implementations of dashboards, tables, forms, headers, tabs, tone‑maps | 3 dashboard architectures · 4 table engines · 3 form systems · 38 local tone maps |
| **T3** | **Status / severity colour is not single‑sourced** | "Four different criticals"; CLOSED grey↔green; HIGH red↔amber | `--danger`=`#C0413A` ≠ `--risk-crit`=`#B33C36`; `lib/labels.ts` exists but bypassed |
| **T4** | **Workflow & visualization are under‑shown** | Lifecycle = one badge; 5/6 dashboards are text not charts; charts lack a11y & colour‑blind safety | `Stepper`/`Timeline` imported by 0 detail pages; ~5 of ~40 dashboard widgets are charts |
| **T5** | **Mobile, RTL edges & a11y are unfinished** | Topbar clips controls on phones; some pages English‑locked; no skip‑link / `aria-current`; forms have no inline validation | Topbar `overflow:hidden`, 0 `@media`; `Input` has no `aria-invalid` |

> **Strategic recommendation:** treat this as a **"design‑system hardening"** programme, not a redesign. Phase 1 makes the existing tokens and components the *only* way to build; Phases 2–3 then layer visualization, workflow visibility, motion, and accessibility on top of a now‑consistent base.

---

## 2. What is already excellent (preserve and propagate)

A fair review starts with credit — these are assets to **extend**, not replace:

- **Token architecture** — `styles/tokens.css` ("Eye‑Comfort Enterprise" warm‑cream + teal‑petrol) is a mature, well‑documented system: full surface/ink ramps, semantic `-bg/-border/base/-fg` quartets, a dedicated 6‑step `--risk-*` heat ramp, categorical `--cat-1..8`, gradients, motion + spacing + radius scales, and a complete `[data-theme="dark"]` redefinition with runtime per‑token user overrides.
- **RTL via logical properties** — `shell.css` uses `margin-inline-*`, `inset-inline-*`, `border-inline-*` throughout; the shell flips with `dir` essentially for free. `.mono` uses `unicode-bidi: isolate; direction: ltr` so IDs like `INC‑0042` don't reorder. Arabic‑primary default verified live (`dir=rtl`, `lang=ar`).
- **Information architecture** — the 15‑group role‑owner sidebar (`Sidebar.tsx`) with pinned Workspace, collapsible groups, true parent/child nesting, badge counters, and three layers of persisted state (collapse / expand / scroll).
- **Flagship surfaces** — the Executive dashboard **PostureHero** (56px composite score on a gradient with delta chip), the **Risk register** (`RiskRegisterPage.tsx`: saved views, filter modal + removable chips, KPI strip, per‑row workflow actions), and **Policy detail** (`PolicyDetailPage.tsx`: `DetailHeader` + `DetailLayout` + lifecycle strip + version history + signature panel).
- **The `DataTable` (`.risk-table`) component** — sticky headers, `color-mix` zebra, keyboard `Enter`‑to‑open, RTL‑safe mono cells.
- **The `WizardShell` family** — ~25 workflow wizards with step rail, per‑step validation gating, auto‑save hooks, and a real review/confirm step.
- **`SearchableSelect`** — full keyboard model, inline create‑new, logical‑property RTL.
- **Reduced‑motion discipline** — a global `prefers-reduced-motion` reset, and `:focus-visible` rings with documented WCAG‑AA contrast fixes.

The job below is to make these the **default and only** path.

---

## 3. Findings & recommendations by area

Each recommendation is tagged **[H]** High / **[M]** Medium / **[L]** Low impact. File paths are under `tss-grc-ui-v2/src/`.

### A. Design system & tokens

**Findings**
- **A1 [T1]** Phantom token fallbacks: `components.css` references `--radius-md/-lg/-sm`, `--dur-md`, `--dur-base` — **none exist** (real names are `--r-md`, `--dur-normal`). Verified live: `--radius-md` and `--dur-md` resolve to nothing, so every such rule silently falls through to its hardcoded literal and the token never drives anything.
- **A2** KPI‑flash keyframes and `BarChart3D` floor hardcode `rgba(22,163,74,…)` / `#15201D` instead of `--success` / an ink token → wrong in dark mode.
- **A3** Form primitives are thin: `Input`/`Select`/`Textarea` have **no error / invalid / required / helper** state wired in, despite `.input-hint.is-error` existing in CSS. `Select` is a raw native `<select>` with no custom chevron and a different height from `SearchableSelect`.
- **A4** A global `html, html *` colour/bg transition (for theme‑switch smoothness) eases **every** element — including live‑updating dashboard numbers.

**Recommendations**
- **[H] Fix phantom tokens** — rename `--radius-*`→`--r-*`, `--dur-md`/`--dur-base`→`--dur-normal` across `components.css`/`viz`, so the system actually drives radius/motion. *(A1)*
- **[H] Add a first‑class field‑error vocabulary** to `Input`/`Select`/`Textarea`: `error?`, `required?`, `hint?` props that set `aria-invalid`, a danger border, a required asterisk from `.input-label`, and render the existing `.input-hint.is-error` slot. *(A3 — unblocks all form work, see G.)*
- **[M] Tokenize remaining hardcoded colours** in keyframes / `BarChart3D` floor (`--success`, a new `--bar-floor`). *(A2)*
- **[M] Promote `Select`** to a styled trigger (custom `ChevronDown`, matched 38px height, same focus ring) or route everything through `SearchableSelect`.
- **[L] Scope the theme transition** to a transient `.theme-transition` class instead of `html *`. *(A4)*

### B. Data visualization (priority area)

**Findings**
- **B1 [T1/T3]** **Three competing chart‑colour sources**, and the canonical one is dead: (1) `--cat-1..8` tokens (on‑brand — `--cat-1`=`#0E5E5E`, verified) are referenced essentially **nowhere**; (2) a hardcoded `PALETTE` in `charts/useChartTheme.ts` with off‑brand generic hex (`#2563eb` cobalt, `#ec4899` pink) drives **every** Recharts chart; (3) per‑page raw hex (`ErmDashboardPage.tsx` severity pies `#dc2626/#ea580c/…`). 27 raw‑hex chart‑colour sites across 10 files.
- **B2 [T3]** Risk‑score→severity thresholds are **duplicated with different breakpoints** in `viz/HeatMap.tsx` (`colorFor`) and `viz/RiskTable.tsx` (`scoreBg`): a score of 12 is "med" in one and "vhigh" in the other; `RiskTable` never returns `--risk-vlow`.
- **B3 [T4]** **Charts have zero accessibility** — no `role`, `aria-label`, `<title>` on any `components/charts/` Recharts chart; empty fallback is a bare `'—'` / `'...'` string. (The hand‑rolled `viz/TimeSeries` *does* have `role="img" aria-label` — the standard is known, just not applied.)
- **B4 [T4]** **Colour‑only encoding** — risk/compliance status is hue alone (green/amber/red) everywhere; collapses for ~8% of male users (deuteranopia). No secondary channel (glyph, label, pattern).
- **B5** Recharts loading = `'...'`, empty = `'—'`, while a polished `Skeleton`/`EmptyState` system exists and is used by tables — so a loading chart looks broken next to a shimmering table.
- **B6** `CoverageMatrix` and ERM `HeatMatrix` hardcode `rgba()` heat ramps that don't equal the tokens and don't adapt to dark mode. Tooltips hardcode `borderRadius:6`/`fontSize:12`; bars have square tops; grid draws both axes (`3 3` dashed) — busy on dense dashboards.

**Recommendations**
- **[H] Point Recharts at the tokens.** Make `useChartTheme.ts` read `--cat-1..8` at runtime (it already reads other tokens via `getComputedStyle`) and delete the hardcoded `PALETTE`. **One file re‑brands and dark‑mode‑fixes every chart.** *(B1)*
- **[H] One severity colour helper.** `riskColorForScore(score)` + `severityToken(level)` driven by `--risk-*` / `--danger|warning|success`, used by HeatMap, RiskTable, severity pies, KPIs, badges. Kills "four criticals" and the threshold mismatch. *(B1/B2/T3)*
- **[H] Colour‑blind‑safe severity** — add a secondary channel: short label/glyph inside heatmap cells ("Crit"/"High"), leading icon or pattern in donut/pie legend swatches, shape token on status badges. Validate the `--risk-*` ramp under deuteranopia (the green→amber step is weakest). *(B4)*
- **[H] Chart accessibility** — wrap each chart in `role="img"` + a data‑summary `aria-label` (or an off‑screen `<table>` alternative); replicate the `TimeSeries` pattern; give empty/loading states real accessible names. *(B3)*
- **[M] Token‑driven loading/empty** — Suspense fallback = a `Skeleton` block sized to the chart; no‑data = `EmptyState`/`InlineEmpty`. *(B5)*
- **[M] Chart styling pass** — centralize the tooltip on `--surface-1`/`--border`/`--r-sm`/`--shadow-2`; rounded bar tops (`radius:[4,4,0,0]`); horizontal‑only gridlines; hide Y axis line; ticks to `--ink-4`; `tnum` on axis/legend numerals. *(B6)*
- **[M] Rebuild `CoverageMatrix`/`HeatMatrix` heat** with `color-mix(in oklab, var(--risk-…) N%, transparent)` so cells track tokens + dark mode, and converge on one heat scale + legend.
- **[L] Add enter animation + hover highlight** to Recharts (`--dur-normal`, active point/bar) so they match the lively SVG viz.

### C. Navigation, shell & information architecture

**Findings**
- **C1 [T5]** **The sidebar doesn't scale to ~110 items / 15 groups** (249 nav nodes verified live). Most groups default‑expanded → a very long scroll; no filter‑as‑you‑type, no "collapse all," no density mode, no favourites/pinning.
- **C2** No "contains‑active" marker on collapsed group headers — in a long scroll the user can't tell which section they're in.
- **C3** NDI deep‑link children use `/ndi?tab=…`; `NavLink` matches on path only, so those 8 children **never** show active state and `aria-current` is wrong for the sub‑tree.
- **C4 [T5]** **Topbar has zero responsive rules.** Fixed 320px search + a 9‑control right cluster with `flex-shrink:0` inside `overflow:hidden` → on a phone the right‑side controls (theme, lang, avatar, **logout**) are clipped and unreachable. `.page-header` likewise never reflows (title + actions collide on narrow screens).
- **C5** Breadcrumbs are detail‑page‑only (`DetailHeader`); the ~70 list/register pages (via `PageHeader`) have no "you are here" trail.
- **C6** Accessibility structure: sidebar is a bare `<aside>` (no `<nav role>`/`aria-label`), `.page` is a `<div>` not `<main>`, no `aria-current="page"`, **no skip‑to‑content link** (keyboard users tab through ~110 links every navigation). Sidebar 9px uppercase group labels (`--sb-text-3` on `--sb-bg`) are ~3:1 — likely fails WCAG AA.
- **C7** `ExportMenu.tsx` dropdown is hardcoded `right:0` (should be `inset-inline-end`) → opens off the wrong edge in RTL; reused on many register pages.

**Recommendations**
- **[H] Make the sidebar scale** — filter‑as‑you‑type, default‑collapsed‑except‑active, "contains‑active" group marker, optional favourites/pinned group. Single biggest nav win at this scale. *(C1/C2)*
- **[H] Responsive Topbar + page‑header** — below `--bp-md`, collapse search to an icon (it already opens the command palette) and move overflow controls into a "more" menu so logout never clips; let `.page-header` wrap actions under the title. *(C4)*
- **[H] Skip‑to‑content link** + make `.page` a `<main>` and the sidebar a `<nav aria-label>`; add `aria-current="page"` to active `NavLink`. *(C6)*
- **[M] Fix RTL `ExportMenu`** (`right:0`→`inset-inline-end:0`) and the query‑string active‑match for NDI children. *(C3/C7)*
- **[M] Extend breadcrumbs** to list pages via an optional `PageHeader` `breadcrumbs` prop. *(C5)*
- **[M] Raise sidebar label contrast** to WCAG AA; add a dedicated high‑contrast `:focus-visible` ring tuned for the dark sidebar. *(C6)*

### D. Dashboards

**Findings**
- **D1 [T2]** **Three dashboard architectures**: ERM uses `PageHeader`+raw `Card`+Recharts; Cyber/PDPL/SCM/BCM/SOC use `DomainDashboardShell`; the Executive page is a third bespoke system. They don't feel like one product (different chart libs, heatmaps, KPI tiles).
- **D2 [T4]** **Visualization is under‑used on 5 of 6 module dashboards** — Cyber/PDPL/SCM/BCM/SOC are dominated by `text-xs` line‑lists + badges; ~5 of ~40 widgets are real charts. SOC Ops (11 widgets, MTTD/MTTR) has the highest information value and the lowest visual encoding.
- **D3** **Mock data masquerading as live** on the flagship: hardcoded KPI deltas (`{value:-2}`), a fully static `AttentionStrip` (top risks / deadlines / activity), and a hardcoded "Aymen/أيمن" greeting — all under an **"All figures live"** badge. A credibility risk on a regulator‑facing tool.
- **D4** The shared `dash-grid` is a rigid 2‑col `1.4fr/1fr`; BCM's 8 and SOC's 11 heterogeneous cards get ragged bottoms with no masonry/row‑alignment; no intermediate 3‑col.
- **D5** `tonePct` RAG thresholds re‑declared in Cyber/BCM (85/60/30) and inlined differently in ERM (80/60) and PDPL/SCM — no single RAG source. The host `DashboardPage` flashes `FALLBACK_STATS` (42 risks, 87%) with no skeleton while the real fetch resolves.

**Recommendations**
- **[H] Unify on one dashboard architecture** — migrate ERM onto `DomainDashboardShell`, or (better) promote the Executive `KpiTile` (microbar + sparkline + tone stripe) into the shared shell so all seven consume one system. *(D1)*
- **[H] Replace fabricated "live" content** with the data already fetched: compute KPI deltas from the existing `kpiTrend` series; make `AttentionStrip` consume real endpoints or render an honest empty state; pull the greeting from auth; drop "All figures live" until true. *(D3)*
- **[H] Turn inherently‑visual metrics into charts** — SOC MTTD/MTTR sparkgauges + severity donut + SLA burndown; BCM BIA/BCP/DRP/RTO as a radial‑gauge cluster; PDPL DSR‑30‑day & breach‑72h as countdown rings; Cyber vuln‑aging stacked bar. (`TimeSeries` already supports an unused `threshold` line — ideal for KRI/SLA.) *(D2)*
- **[H] One RAG helper** `ragTone(value, kind)` imported everywhere. *(D5)*
- **[M] Make `dash-grid` content‑aware** — `repeat(auto-fit, minmax(320px,1fr))` + size hints (sm/md/lg) + row alignment; add a skeleton to the flagship host. *(D4/D5)*
- **[L] Add per‑dashboard export** in the unused `DomainDashboardShell` `headerAction` slot (SOC for SAMA, BCM for NCEMA); staggered entry motion; count‑up hero. Fix the Arabic typo "كموماً"→"كمومياً" (SOC crypto widget).

### E. Registers / lists / tables

**Findings**
- **E1 [T2]** **Four table engines:** the good `DataTable`/`.risk-table` (~70 pages); raw `<table class="grc-table">` (Compliance) and `<table class="tbl">` (~20 SOC pages) — **both have no CSS rule at all**, rendering as default browser tables; and a modern `DataTableV2` (TanStack, virtualized, sortable, selectable) used on **1 page**.
- **E2 [T3]** **38 files redefine local `STATUS_TONE`/`SEVERITY_TONE`** that conflict with `lib/labels.ts` and each other: CLOSED is neutral on Risk, success on Changes; HIGH is red in `labels.ts`, amber on Risk/SOC.
- **E3 [T5]** **No responsive story for the dominant table** — the "tables→cards" rule targets only `.dt2-row` (1 page), and even that is **broken** (relies on `td::before{content:attr(data-label)}` but `DataTableV2` never emits `data-label`). The 70 `.risk-table` pages have no card mode and no horizontal‑scroll wrapper.
- **E4** `DataTable` renders **all rows, no pagination**; lists fetch `0,100` and silently truncate with no "100 of N." Pagination/result‑count appears in three formats; loading appears in three treatments; empty states in three components (one renders a bare em‑dash; `EmptyState` default copy is "Coming soon"); only SOC offers a Retry.
- **E5** `BulkActionsBar` exists but only 6 pages use it; the 70 `DataTable` pages have **no row selection** at all — no bulk re‑assign / close / export‑selection on the daily registers.
- **E6** Filtering splits across `FilterModal` + removable chips (good, Risk/Evidence) vs bare inline `<select>` (Compliance) vs pill‑buttons (SOC). Row‑action strips render 4–5 conditional workflow buttons inline at 0.55 opacity, bloating the action column to ~230px.

**Recommendations**
- **[H] Converge on one table engine.** Target `DataTableV2`; minimum viable near‑term fix = **delete `.grc-table`/`.tbl` usage** and route every register through the styled `DataTable`. Removes the "unstyled browser table" class of pages at low cost. *(E1)*
- **[H] Single status/severity source** — delete the 38 local maps, import only `lib/labels.ts`, ship `<StatusBadge>` / `<SeverityChip>` wrappers so drift can't return. Reconcile CLOSED and HIGH globally first. *(E2)*
- **[H] Responsive table** — horizontal‑scroll wrapper + sticky first column + `< --bp-sm` card mode; emit `data-label` from column headers (fixes the broken card mode). *(E3)*
- **[M] Default to `SkeletonRow` loading; one pagination + result‑count footer; one `EmptyState` (register‑appropriate copy, not "Coming soon"); one tokenized error banner with Retry.** *(E4)*
- **[M] Turn on selection + `BulkActionsBar`** on the high‑volume registers (Risk, Incidents, Evidence, Obligations, Vendors). *(E5)*
- **[M] One filtering paradigm** (`FilterModal` + chips) app‑wide; move secondary row actions into a `⋯` overflow menu. *(E6)*
- **[L] Add a density toggle** (`DataTableV2` already supports compact/standard/comfortable); codify numeric right‑align + `tnum` in the component instead of per‑cell inline styles.

### F. Detail & workflow pages

**Findings**
- **F1 [T4]** **Lifecycle is almost never visualized.** `Stepper` and `Timeline` are imported by **zero** detail pages; the only true state‑machine visuals (`LifecyclePipeline`, `PolicyLifecycleStrip`) appear on list/kanban pages and exactly **one** detail page (`PolicyDetailPage`). A 6‑stage AI‑model lifecycle shows only a single badge; the position in the sequence and "what's next" is invisible — the core GRC question goes unanswered.
- **F2 [T2]** **Three "detail page" species:** (1) gold‑standard `DetailHeader`+`DetailLayout`+rail (~12 pages); (2) `PageHeader`+stacked cards with **no breadcrumb/back** (~26 pages); (3) **no detail page at all** — Sprint 31/32 lifecycle records are operated from an inline `<select>` form that toggles open *above the table* (no record URL, no header, no history).
- **F3** History is inconsistent: the reusable `EntityActivityTimeline` is a flat 3‑column grid (no dots/line) despite `Timeline` existing; `RiskDetailPage` stacks three different history surfaces; Sprint 31/32 records show none.
- **F4** Action hierarchy is muddled — `RiskDetailPage` renders every transition as `primary` (Submit/Approve/Accept/Close/Reopen look equally weighty); treatment transitions are bare `→ STATUS` ghost buttons with the same weight as Delete.
- **F5 [T5]** **Bilingual leaks** — `PrivacyBreachDetailPage` and `WorkflowSubmissionDetailPage` render `titleEn`/`descriptionEn` even in Arabic mode (violates the project's bilingual rule on a SDAIA‑facing record). **Native `window.prompt`/`window.confirm`** are used for the most regulatorily significant actions (DSR SLA‑extension, DSR reject, SDAIA breach‑notify) instead of the app's `useDialog`/`ConfirmDialog`.
- **F6** Each Pattern‑2 page re‑defines its own `DataPair`/`Field`/`Row` key‑value renderer (label widths/casing drift); tabs are local `useState`, not URL‑addressable (can't deep‑link or refresh onto a tab).

**Recommendations**
- **[H] A record‑level workflow stepper everywhere a record has a state machine.** Generalize `PolicyLifecycleStrip` into `components/ui/WorkflowStatusStepper` (`stages[] + current`, passed/current/future with the checkmark treatment from `Stepper.tsx`), placed as a full‑width strip under the detail header. Wire into AI model, outsourcing, DSR, breach, fraud + Sprint 31/32 records. **Biggest perceived‑quality + usability win for a GRC tool.** *(F1)*
- **[H] Give Sprint 31/32 lifecycle records real detail pages** (`DetailLayout` rail + tabs) and move the "Move/Classify" inline‑`<select>` forms into a `Modal` from the header. *(F2)*
- **[H] Standardize on `DetailHeader`** for all detail pages (breadcrumb + back + single status placement + single actions zone); retire `PageHeader` from detail use. *(F2)*
- **[M] One action‑hierarchy rule** — single most‑likely transition = `primary`, alternates = `secondary`, destructive = `danger`, navigation = `ghost`; label transitions "Advance to X," visually separate Delete. *(F4)*
- **[M] One history surface** — re‑render `EntityActivityTimeline` with the dot‑and‑line `Timeline`; delete bespoke history renderers. *(F3)*
- **[M] Fix bilingual leaks** (select `*Ar` when `lang==='ar'`) and replace `window.prompt`/`confirm` with `useDialog`/`ConfirmDialog`. *(F5)*
- **[M] One shared `DetailField`/`RailField`**; **[L]** make tabs URL‑addressable (`?tab=`); standardize RTL icon flipping via the existing `DirectionalArrow`. *(F6)*

### G. Forms, wizards & modals

**Findings**
- **G1 [T5]** **No inline validation anywhere.** `Input`/`Select`/`Textarea` and `.input` have no error variant / `aria-invalid`; `FormModal` `FieldDef` has no `error`. Validation = HTML‑`required` + a toast on submit. **No error summary, no scroll‑to‑first‑error, no per‑field message** in any form across the app.
- **G2 [T2]** **Bespoke `inputStyle` in 43+ files** (`border-radius:4; padding:6; border:1px solid --card-border`) ships a second input visual language next to the design‑system `.input` — the entire Sprint 30/31/32 surface is on the wrong one. Required asterisks are hand‑concatenated three different ways.
- **G3** "Create record" UX splits: real `Modal`/`FormModal` (Risk, Evidence) vs an inline `<Card>` that toggles open above the table (Sprint 30/31/32) — no focus trap, no Esc, no scrim. `Modal` caps at `lg`=880px, too tight for compound `EntityDocsModal` (attachments + control mapping + readiness), whose nested `position:absolute` dropdown (z‑20) can paint under sibling modal content.
- **G4** Wizard validation shows one footer hint, not the specific failing field; `onSaveDraft` is often a no‑op toast ("Draft kept in memory") that's lost on refresh; the step rail's forward‑lock traps quick edits from the Review step.
- **G5** Design‑system `Input` never sets `dir`, so an English field (`titleEn`) on an Arabic page renders right‑aligned LTR (mixed‑direction misalignment). `number` fields coerce empty → `0` (can't represent "unset").

**Recommendations**
- **[H] Inline validation system** — `error`/`helper` on `FieldDef` + the primitives (see A3); render per‑field messages, an error summary at the top of the modal, and scroll‑to‑first‑invalid on submit. Highest‑leverage form change. *(G1)*
- **[H] Kill the bespoke `inputStyle`; migrate 43+ pages to `.input`/`FormModal`** — start with the newest, most‑visible (outsourcing, dataclass, aiLifecycle, GrcExpert). Collapses two input languages into one. *(G2)*
- **[H] Publish a "which form engine" rule** — simple create/edit → `FormModal`; ≥3 steps → `WizardShell`; document/control attach → `EntityDocsModal` — so each sprint stops reinventing `inputStyle`.
- **[M] Unify "create" on `Modal`/`FormModal`** (focus‑trap/Esc/scrim); add an `xl`/full‑screen `Modal` size and raise nested‑popover z‑index to match `SearchableSelect`. *(G3)*
- **[M] Per‑field wizard errors** (`validate()` returns `Record<key,string>`); make `onSaveDraft` persist to `localStorage` or remove it; allow review‑step jump‑to‑step. *(G4)*
- **[M] `dir`‑aware bilingual fields** (`contentDir` on `FieldDef`); fix `number` empty‑coercion. *(G5)*

### H. Specialized surfaces

**Findings & recommendations**
- **GRC Expert (`GrcExpertPage`)** — the most prototype‑y of the "polished" surfaces: hand‑rolled inline‑style KPI tiles, family tabs (`borderRadius:20`), accordions, and `opacity:0.6/0.5/0.45` muting instead of `--ink-3`. **[H]** Refactor onto `Kpi`/`Tabs`/`Card`; swap opacity muting for ink tokens.
- **Pill‑tabs duplication** — GrcExpert family tabs, `PosturePage` family chips, and `EvidencePage` framework sub‑tabs are **three implementations** of the same control. **[H]** Extract one `PillTabs`/`FilterChips` and adopt in all three + AI register filters.
- **Posture heatmap (`PosturePage`)** — strong information design (row=document, col=domain, click‑to‑drill) but a hardcoded green `rgba()` ramp, no legend, no max‑value key, silent cap at 6, not colour‑blind safe. **[M]** Add a min→max legend + colour‑blind‑safe scale + numeric/pattern cue.
- **Login (`LoginPage`)** — genuinely polished split hero/form, but the password field is **pre‑filled with the real admin credential** (`useState('Admin@12345')`) — a security smell that would embarrass in any screenshot — and there's no show/hide toggle. **[M]** Remove the pre‑filled secret; add a password visibility toggle.
- **AI register/detail** — detail page is on‑system and the "gate‑missing reason on a disabled transition button" is an **excellent** pattern to propagate; the register's create/audit modals still use the `inputStyle` anti‑pattern. **[L]** Move them to `FormModal`.

### I. RTL & bilingual polish
- **[H]** Fix `ExportMenu` `right:0`→`inset-inline-end` (widely reused). **[M]** Sweep ~17 TSX files for physical `marginLeft/Right`/`left/right` on icon spacers → `margin-inline-*`. **[M]** Refactor `OrgSwitcher`'s `lang`‑branching inline styles to logical properties. **[M]** Fix English‑locked detail pages (PrivacyBreach, WorkflowSubmission). **[L]** Audit `format.ts` callers pass `lang` (default `'en'` renders Latin digits on Arabic pages); consider a `useFormat()` hook.

### J. Responsive & mobile
- **[H]** Responsive Topbar + page‑header (see C4). **[H]** Responsive table (see E3). **[M]** Establish **one** shared mobile pattern for standard pages (the polished `mobile.css` is currently scoped only to `/crisis/mobile`). **[M]** Verify `DomainDashboardShell`'s inline `repeat(N,1fr)` KPI grid actually collapses under the `breakpoints.css` override. **[L]** Reconcile the double scroll container (`.page` + `.sb`) for smoother touch scrolling.

### K. Accessibility (cross‑cutting)
- **[H]** Skip‑to‑content link; `<main>`/`<nav>` landmarks; `aria-current="page"` (C6). **[H]** Chart accessibility (B3). **[H]** Colour‑blind‑safe severity (B4). **[H]** Inline form validation + `aria-invalid` (G1). **[M]** Sidebar label contrast to WCAG AA; high‑contrast `:focus-visible` on dark sidebar. **[M]** Replace `transition: all` on `.sb-item`/`.icon-btn` with explicit properties.

### L. Motion & micro‑interactions
- **[M]** Chart enter animation + hover highlight (parity with the lively SVG viz). **[L]** Staggered fade/translate‑in on KPI rows and cards (tokens `--dur-fast`/`--ease-spring` already exist); count‑up on the hero posture score; unify the "see all"/"view register" hover‑arrow affordance (currently `DomainDashboardShell` uses a bare `<a>` full‑reload, not `Link`).

---

## 4. Prioritized roadmap

Sequenced so each phase de‑risks the next. Phase 1 is mostly **consolidation onto existing systems** — low risk, high compounding return.

### Phase 1 — Foundation hardening & consolidation (highest ROI)
> *Goal: make the existing tokens & components the only way to build. Mostly deletions and re‑pointing — little net‑new UI.*

| Item | Area | Impact |
|------|------|--------|
| Point Recharts at `--cat-*`; delete hardcoded `PALETTE` | B1 | **H** |
| One `severityToken`/`riskColorForScore` + one `ragTone` helper, used everywhere | B2, D5, E2 | **H** |
| Single `<StatusBadge>`/`<SeverityChip>`; delete 38 local tone maps | E2, T3 | **H** |
| Fix phantom tokens (`--radius-*`, `--dur-md`) | A1 | **H** |
| Field‑error vocabulary on `Input`/`Select`/`Textarea` + `FormModal` | A3, G1 | **H** |
| Delete `.grc-table`/`.tbl`; route through styled `DataTable` | E1 | **H** |
| Skip‑link + `<main>`/`<nav>` landmarks + `aria-current` | C6 | **H** |
| Responsive Topbar + page‑header (logout never clips) | C4 | **H** |
| Fix `ExportMenu` RTL `right:0` | C7, I | **M** |
| Publish "which form engine / which table / which header" decision rules | T2 | **M** |

### Phase 2 — Visualization & workflow visibility (the "best‑in‑class cockpit" leap)
| Item | Area | Impact |
|------|------|--------|
| Unify on one dashboard architecture (shared shell + rich KPI tile) | D1 | **H** |
| Replace fabricated "live" content with real data / honest empty states | D3 | **H** |
| Convert text‑list dashboard widgets to charts (SOC, BCM, PDPL, Cyber) | D2 | **H** |
| Record‑level `WorkflowStatusStepper` on every state‑machine detail page | F1 | **H** |
| Real detail pages for Sprint 31/32 lifecycle records | F2 | **H** |
| Standardize all detail pages on `DetailHeader`; one action hierarchy | F2, F4 | **H** |
| Colour‑blind‑safe severity encoding + chart accessibility | B3, B4 | **H** |
| Migrate bespoke `inputStyle` (43+ files) to `.input`/`FormModal` | G2 | **H** |
| Responsive table (card mode + sticky first col) | E3 | **H** |

### Phase 3 — Interaction polish, density & accessibility finish
| Item | Area | Impact |
|------|------|--------|
| Token‑driven chart loading/empty + tooltip/axis styling pass | B5, B6 | **M** |
| Sidebar scale (filter, collapse‑by‑default, contains‑active, favourites) | C1, C2 | **H** |
| Selection + `BulkActionsBar` + density toggle on high‑volume registers | E5 | **M** |
| One filtering paradigm; row‑action overflow `⋯` menu | E6 | **M** |
| One history surface (`Timeline`); URL‑addressable tabs | F3, F6 | **M** |
| Per‑field wizard errors; persist drafts; `dir`‑aware fields | G4, G5 | **M** |
| Refactor GrcExpert + extract `PillTabs`/`FilterChips`; Posture legend | H | **H/M** |
| Login: remove pre‑filled secret + add show/hide | H | **M** |
| One shared mobile pattern app‑wide | J | **M** |
| Motion pass (chart enter, staggered cards, hero count‑up) | L | **L** |
| RTL sweep (physical margins, OrgSwitcher, English‑locked pages); native dialogs → `useDialog` | I, F5 | **M** |

---

## 5. Quick wins (high impact ÷ low effort — do first)

These are mostly one‑ or few‑file changes with outsized, app‑wide effect:

1. **Re‑point Recharts to `--cat-*` tokens** — *one file* (`useChartTheme.ts`) re‑brands & dark‑mode‑fixes every chart. *(B1)*
2. **Fix phantom token names** — find/replace `--radius-*`→`--r-*`, `--dur-md`→`--dur-normal`. *(A1)*
3. **`ExportMenu` `right:0`→`inset-inline-end`** — fixes a visible RTL bug on many register pages. *(C7)*
4. **Skip‑to‑content link** — one component, large a11y return with a 110‑item sidebar. *(C6)*
5. **Remove the pre‑filled admin password** on Login. *(H)*
6. **`EmptyState` default copy** "Coming soon" → register‑appropriate text. *(E4)*
7. **Drop the "All figures live" badge** until the hardcoded deltas/AttentionStrip are real. *(D3)*
8. **Fix the Arabic typo** "كموماً"→"كمومياً" (SOC crypto widget). *(D)*
9. **Delete `.grc-table`/`.tbl` usage** → route Compliance/SOC tables through the styled `DataTable`. *(E1)*
10. **Single `ragTone`/`severityToken` helper** + `<StatusBadge>` — stops the "four criticals" drift at the source. *(B2/E2)*

---

## 6. Design principles to prevent regression

Adopt these as the standing "definition of done" for any new UI so the fragmentation doesn't re‑accumulate:

1. **Tokens or it doesn't ship.** No raw hex/rgba or px in components — every colour, radius, space, duration is a token. (Add a lint rule against hardcoded hex in `components/`/`pages/`.)
2. **One component per job.** One table, one form engine, one modal, one tab, one status badge, one detail header, one dashboard shell. New variants extend the shared component, never fork it.
3. **Status colour is single‑sourced.** All status/severity/RAG colour flows through one helper + `lib/labels.ts`.
4. **Every record shows its lifecycle.** Any state‑machine entity renders a stepper, not just a badge.
5. **A metric that can be a chart, is a chart.** Text‑list KPIs are a fallback, not the default.
6. **Bilingual + RTL by construction.** Logical properties only; never single‑language user strings; native `window.*` dialogs are banned in favour of `useDialog`.
7. **Accessible by default.** Landmarks, `aria-current`, labelled charts, `aria-invalid` on errors, colour never the sole encoder, AA contrast.
8. **Responsive is not optional.** Every page validated at `mobile`/`tablet`/`desktop`; no control may clip.

---

### Appendix — key files
- **Tokens / CSS:** `styles/tokens.css`, `styles/globals.css`, `styles/components.css`, `styles/shell.css`, `styles/breakpoints.css`, `styles/mobile.css`
- **Charts / viz:** `components/charts/useChartTheme.ts`, `_recharts-bundle.tsx`, `LineChart/BarChart/PieChart.tsx`, `components/viz/{HeatMap,RiskTable,CoverageMatrix,Donut,TimeSeries,BarChart3D}.tsx`
- **Shell / nav:** `components/layout/{Sidebar,Topbar,Shell,PageHeader,DetailHeader,DetailLayout,DetailRail,Breadcrumb}.tsx`, `components/dashboard/DomainDashboardShell.tsx`
- **Tables / lists:** `components/ui/DataTable.tsx`, `components/ui/data-table/DataTableV2.tsx`, `FilterModal.tsx`, `BulkActionsBar.tsx`, `EmptyState.tsx`, `Skeleton.tsx`, `RowActions.tsx`, `lib/labels.ts`
- **Forms / modals:** `components/ui/{FormModal,Modal,Input,Select,SearchableSelect,Textarea,AttachmentsField,ControlMappingPicker,EvidenceReadinessPanel,SignaturePanel,EntityDocsModal}.tsx`, `components/workflow/WizardShell.tsx`, `components/ui/{Stepper,Timeline}.tsx`
- **Exemplars to emulate:** `pages/risk/RiskRegisterPage.tsx`, `pages/detail/PolicyDetailPage.tsx`, `pages/dashboard/ExecutiveDashboard.tsx`
- **Worst offenders to fix first:** `pages/compliance/ObligationsPage.tsx` (`.grc-table`), `pages/socops/*` (`.tbl`, text‑only), `pages/aiLifecycle/*` & `pages/dataclass/*` (no detail page, inline‑`<select>` transitions), `pages/expert/GrcExpertPage.tsx` (inline styles), `pages/outsourcing/*` (bespoke `inputStyle`)

---

*Prepared from a 6‑track parallel audit of the full `tss-grc-ui-v2` component tree with live token/DOM verification. All recommendations are front‑end design only; no backend or business‑logic change is implied.*
