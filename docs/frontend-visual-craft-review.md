# TSS GRC Platform — Visual-Craft Review & Improvement Plan

> **HISTORIC — point-in-time snapshot (all files 2026-06-02). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

## Committee of GRC-tool design leaders · pixel/token-level polish (sizing · alignment · colour · type · spacing)

> **Third companion** to `frontend-design-review.md` (systemic findings) and `frontend-visualization-playbook.md` (per-page hero charts). This one is **granular visual craft**: relative component sizing & proportion, item positioning & alignment, colour matching/harmony, text (margins, alignment, font sizes, hierarchy), spacing rhythm, radius/elevation, page composition, and RTL/responsive precision.
> **Lens:** GRC SME + UI/UX visual designer + typography/colour/layout craftsperson. **Benchmarks:** ServiceNow IRM · OneTrust · AuditBoard · Drata · Diligent.
> **Date:** 2026‑06‑02 · **Method:** 5‑member craft panel reading the *current* `tss-grc-ui-v2` (post the 8 new viz primitives), citing exact files/tokens/px.

---

## 1. Executive summary

The **design‑token foundation is genuinely strong** — a warm‑cream + teal‑petrol palette with semantic `-bg/-border/-fg` quartets, a 6‑step `--risk-*` ramp, a 10‑step `--text-*` scale, a 4px `--sp-*` scale, motion/radius/shadow tokens, full dark mode, and logical‑property RTL. **Every craft problem is a *consumption* problem** — components and the newest hero primitives bypass the scale they were given, hardcode off‑scale values, or re‑implement the same control five different ways. The system rivals Drata; the **enforcement** does not.

Three things make this distinct from the prior reviews and worth fixing now:

1. **Real bugs, not just polish** — a detail page that renders broken, a flagship surface tinted the wrong brand colour, dead responsive CSS, a chart that encodes two severities with the same hex. These are listed first (§2) because they're cheap and visible.
2. **"Off‑scale" drift is pervasive and measurable** — fonts at `10.5 / 11.5 / 13.5 / 14.5 / 15px`, spacings at `9 / 14 / 18 / 22 / 36px`, radii at `5 / 7 / 10 / 11 / 2.5px` — none of which exist in the scale. This is the visual signature of "no token discipline was enforced."
3. **"Same role, many sizes"** — card titles at 13/14/15/18px, headline numbers at 18/22/24/30/36px, gauges at 96/104/108/172px, detail titles at 22 vs 26px, six chip systems, four pill‑tab implementations, five field renderers.

> **Strategic frame:** this is a **design‑QA + token‑enforcement pass**, not a redesign. The cheapest path to ServiceNow/AuditBoard/Drata‑grade consistency is to (a) fix the handful of bugs, (b) snap off‑scale values to tokens, and (c) collapse the duplicated controls onto one primitive each.

---

## 2. Design‑QA defects (concrete bugs — fix first)

These are not subjective; they render wrong or dead today.

| # | Severity | Defect | File | Fix |
|---|----------|--------|------|-----|
| **B1** | 🔴 broken page | `OutsourcingDetailPage` is built on **Tailwind utility classes that don't exist** in this project (`grid-cols-3`, `text-muted-foreground`, `lg:grid-cols-2`, `text-2xl`, `text-green-600`…). Key/value pairs stack instead of aligning; labels have no muted colour; the 2‑col card grid never forms; due‑diligence sub‑scores render identical ink; the composite score doesn't size up. | `pages/outsourcing/OutsourcingDetailPage.tsx` | Port to real tokens/CSS (DetailLayout + RailField + `--ink-3`). |
| **B2** | 🔴 wrong colour | `GrcExpertPage` uses **5 undefined CSS variables**: `--card-border` (×12 → borders inherit `currentColor`/ink), `--mono` (codes lose monospace), `--surface-0`, and `--primary-wash`/`--primary-rgb` → falls back to a hardcoded **blue `#2563eb`** so the *selected framework* tints blue while the brand is teal `#0E5E5E`. | `pages/expert/GrcExpertPage.tsx` | `--card-border`→`--border`, `--mono`→`--font-mono`, `--surface-0`→`--surface`, `--primary-wash`→`--primary-50`. |
| **B3** | 🔴 dead CSS | Mobile full‑screen modal rule targets `.modal-content`/`.drawer-content`, but `Modal.tsx` renders **`.modal-card`** — so the phone takeover **never applies**; an 880px `lg` modal keeps 24px gutters + rounded corners on a 360px screen. | `styles/breakpoints.css:121` | Selector → `.modal-card`; drop scrim padding at ≤639px. |
| **B4** | 🟠 colour collision | `DriftChart` maps `MINOR → --accent` and `MODERATE → --warning` — **both resolve to `#C8932A`** (identical in light *and* dark), so two severity steps are indistinguishable. | `components/viz/DriftChart.tsx:31-37` | `MINOR → --info` (or `--accent-300 #E5B547`). |
| **B5** | 🟠 dead animation | `Donut` transitions on `var(--dur-base)` — **no such token** (`--dur-micro/fast/normal/slow`), so segments snap instead of animating. | `components/viz/Donut.tsx:55` | → `var(--dur-normal)`. |
| **B6** | 🟠 responsive dead | `FormModal`'s grid is an inline `repeat(12,1fr)` with **no class**, but the mobile single‑column rule keys off `.form-12col` — so `span 6` fields stay half‑width (~170px) on a phone. | `components/ui/FormModal.tsx:136` | Add `className="form-12col"`. |
| **B7** | 🟠 RTL bug | `ExportMenu` dropdown is hardcoded `right:0` → opens off the wrong edge in RTL (reused on many registers). | `components/ui/ExportMenu.tsx:63` | `inset-inline-end:0`. |

---

## 3. Colour matching & harmony

The base palette harmonizes (analogous teal + complementary sand‑amber on warm cream). Defects are specific.

- **C1 [H] "Two reds."** `--risk-crit #B33C36` (heatmap/matrix) ≠ `--danger #C0413A` (badges, countdown, buttons). A `RiskMatrix5x5` crit cell, a `Badge tone="danger"`, and a `CountdownRing` overdue arc show **three different criticals in one viewport** — the exact drift `ragColors.ts` was meant to kill, reintroduced at the *token* layer. `--risk-vhigh #D87560` (coral) vs `--danger #C0413A` (true red) also read as different temperatures. → Anchor `--risk-crit` to `--danger`, or make the `--risk-*` ramp a single documented diverging scale.
- **C2 [H] Dark‑mode `--cat-1..8` is undefined.** The categorical palette is set only on `:root`; the `[data-theme="dark"]` block overrides risk/semantic/primary/accent but **not** `--cat-*`. So every Recharts series and every `CrosswalkFan` ribbon renders light‑mode hues on dark surfaces — `--cat-1 #0E5E5E` on dark `--surface` ≈ **1.4:1** (invisible). → Add a dark `--cat-*` block lifted ~2 steps (mirroring how `--primary` was lifted).
- **C3 [H] `RiskMatrix5x5` "improved" vector uses `--primary` (teal), clashing with sage low‑risk cells and with "green = success" everywhere else.** → Use `--success`; reserve `--primary` for brand chrome. Fixes the legend too.
- **C4 [H] ERM dashboard hardcodes hex chart colours** (`#dc2626/#ea580c/#ca8a04/#16a34a/#0d9488`) so its severity donut's red ≠ the token red of the KPI strip directly above and ≠ the `--risk-*` matrix on the same page. → Replace all with `--danger/--warning/--success/--primary` + `--risk-*`.
- **C5 [M] SVG text hardcodes `#fff` on a `--primary` fill** — fine in light mode (≈6.6:1) but in dark mode `--primary` flips to `#5FBCBC` and white‑on‑light‑teal ≈ **1.9:1** (fails). Affects `CrosswalkFan` control node, `RiskMatrix5x5` deep cells. → Use `var(--ink-0)` (inverts with theme).
- **C6 [L] Fallback‑literal drift** — `--success` fallbacks/keyframes use Tailwind `#16a34a` (real token `#3F8C5C`); four different danger fallbacks (`#ef4444/#d92d20/#c8362c/#C0413A`). Cosmetic until a var fails, but they diverge. → Standardize fallbacks to the real token hex.

---

## 4. Typography craft

The `--text-*` scale (11→41) and `.text-*` utilities are well‑formed; components ignore them.

- **T1 [H] Off‑scale font sizes** that aren't on the ladder: `13.5` (`.sb-title`), `14.5` (`.module-name`), `11.5` (score‑pill, login‑hint, settings‑hint, ai‑json, topbar‑sub), `12.5` (`.cf-label`), `10.5` (`.step-label`, `.sb-badge`), `15` (`.placeholder h4`). Half‑pixel sizes are the clearest "no scale" tell. → Snap to tokens; **promote `--text-3xs: 10px`** (10px appears 8+ times: heatmap axis, step labels, sidebar sections, badges).
- **T2 [H] Same role, different sizes.** Card/section titles render at `13px` (`.card-title`), `14px` (`.wf-hub-card-title`), `15px` (`.placeholder h4`), `18px` (`.modal-header h3`, error card). Headline numbers at `18/22/24/30/36px` across `Kpi`, Executive `KpiTile`, AI stat, ERM `AppetiteTile`/`MiniStat`. Detail titles at `22px` (`DetailHeader`) vs `26px` (`PageHeader`). → One token per role: in‑card title `--text-md (14)`; `--num-hero (36)` + `--num-tile (24)`; one detail‑title size (22–24).
- **T3 [H] `Kpi` vs stat‑tile value use different clamps** (`clamp(24,4vw,36)` vs `clamp(24,3vw,32)`) → a Kpi and a stat‑tile in the same row resolve to a visible 4px headline mismatch when they're meant to align. → One shared clamp.
- **T4 [M] RTL uppercase/letter‑spacing fix applied to only 2 of ~12 capped‑label classes.** `.kpi-label`/`.stat-tile-label` correctly drop `text-transform` + tracking in Arabic; but `.risk-table th`, `.heatmap-axis`, `.wf-label`, `.input-label`, `.step-label`, and the SVG axis labels do not — Arabic renders with broken inter‑letter spacing + a no‑op uppercase. → One rule: `[dir="rtl"] .label-caps { letter-spacing:0; text-transform:none }`, applied to all capped labels.
- **T5 [M] `tabular-nums` is inconsistent.** Present on `.kpi-value`/gauge numerals; **missing** on `.score-pill`, `.heatmap-count`, `.bar-val`, `.maturity`, and some `DriftChart` axis labels. → Add `font-variant-numeric: tabular-nums` to `.mono` + every numeric cell so columns don't jitter.
- **T6 [M] Line‑height tokens unused** — `--leading-*` exist but components hardcode `1.2/1.3/1.35/1.55/1.6/1.8/1.9`. → Map to tokens; add `--leading-relaxed: 1.8` if the cyber line‑lists need it.
- **T7 [L] Negative tracking inconsistent** — large numerals/headlines use `-0.01/-0.02/-0.025/-0.03em` in five places. → Standardize on `--tracking-tight (-0.02)` for headlines, one value for large numerals.

---

## 5. Spacing & rhythm

The 4px `--sp-*` scale is used in newer CSS but bypassed in core components → two spacing dialects.

- **S1 [H] Core components hardcode off‑scale padding** — `.btn 8px 14px` (14 not on scale), `.badge 3px 9px`, `.card-header 14px 18px`, `.card-body 16px 18px`, `.kpi 20px 36px 18px`, `.modal-header 18px 22px`. The recurring `9/14/18/22/36` are the spacing equivalent of off‑scale fonts and create subtly different gutters between a Card, a Modal, and a KPI that should share one. → Re‑base to `--sp-*` (`btn → sp-4 sp-6`, `card-header → sp-5 sp-6`, KPI inline‑pad → `sp-9 (32)` not 36).
- **S2 [H] Card‑family padding is inconsistent across 6 variants** (`.card` 16/18, `.module-card` 18, `.kpi` 20/36/18, `.wf-hub-card` 16, `.preset-card` 14, `.event-list-item` 8/10). Left text edges don't align between sibling tiles. → One `--card-pad-x/--card-pad-y`; KPI's deliberate wide inline‑pad → on‑scale `sp-9`.
- **S3 [M] `.kpi-grid` is defined twice** (shell.css gap 16 vs components.css gap 12) — cascade order decides the winner. → Delete the duplicate; one section gap (`sp-7 = 20`).
- **S4 [M] Off‑scale flex/grid gaps** — `5 / 14 / 18px`; viz legends use `gap:12` (ResidencyMap) vs `gap:14` (RiskMatrix) for the same legend row. → Legend rows `sp-5 (12)` uniformly.

---

## 6. Radius & elevation

- **R1 [H] Viz primitives hardcode off‑scale SVG `rx`** — `RiskMatrix5x5` cell `rx={7}`, `CrosswalkFan` node `rx={10}`/chip `rx={4}`, `FreshnessCalendar` `rx={2.5}`; legend swatches use radius 2 / 3 / 999 across four primitives. → Pick scale‑aligned literals (cells/chips `4`, nodes `8`, swatches one value) and document the SVG→`--r-*` mapping.
- **R2 [M] CSS one‑off radii** where a token exists — `11px` (`.sb-mark`), `5px` (`.preset-swatch`, `.preset-sidebar-strip`), plus `999px` literals ×5 where `--r-pill` is right there. → Snap to tokens.
- **R3 [M] ~10 bespoke shadows bypass `--shadow-*`**, one (`.wf-bulkbar`) uses an **off‑palette slate tint `rgba(15,23,42…)`** (system tints with ink `rgba(21,32,29)`); `.wf-hub-card:hover` is a hand‑rolled `--shadow-hover` at a different opacity. → Route card‑hover to `--shadow-hover`, others to `--shadow-2`; keep only brand‑glow exceptions (`.sb-mark`, `.avatar`).
- **R4 [L] `.preset-card.active` reuses `--focus-ring` as a resting border‑glow** — muddies the keyboard‑focus affordance. → Use `0 0 0 2px var(--primary)`; keep `--focus-ring` for `:focus-visible` only.

---

## 7. Component proportion

- **P1 [H] Six pill/chip systems.** `.badge` (3/9, 11px) + `.tab-count` (10px) + `.wf-filter-chip` (12px) + `.sb-badge` (10.5px) + `.score-pill` (11.5px) + `.maturity` (11px) — six pill indicators at 10/10.5/11/11.5/12px and four paddings. A `Badge`, a `.maturity`, and a `.score-pill` in one table row don't share height or text size. → Extract `--chip-pad / --chip-font / --chip-radius`; provide a `-sm` (micro) variant for in‑cell tags/counters.
- **P2 [H] Gauge sizes diverge** — RadialGauge hero `172/15`, BCM rings `108/10`, Cyber framework `96/9`, PDPL DSR `96/9`, CountdownRing `168/13`. The "same" ring shows at 96 vs 108 between Cyber and BCM; sibling ring primitives differ by 1px stroke at the same size. Cyber's 96px caption (fixed 9px) is nearly illegible. → One `--gauge-md (108/10)` + `--gauge-hero (172/15)`; align ring stroke across RadialGauge/CountdownRing.
- **P3 [M] Icon‑button ladder undocumented** — `38` (`.btn-icon`/`.icon-btn`, good) vs `28` (`.row-action-btn`, markers) vs a `24` outlier (`.sb-item-toggle`). → Formalize `--touch-lg (38)` / `--touch-sm (28)`; bump the 24 toggle to 28. (Note: the global coarse‑pointer 44px rule over‑inflates inline 28px controls — see §12.)
- **P4 [M] Button padding/icon ramp uneven** — `.btn` vertical step 6→8→11 (2 then 3); icon sizes 14/16/**17** (17 is an odd icon size). → icons 14/16/18; `.btn-lg` font `--text-md → --text-lg` so large reads large.

---

## 8. Page composition & alignment (dashboards)

- **D1 [H] `.dash-grid` is a fixed `1.4fr 1fr` two‑column grid** that every domain dashboard dumps 5–11 heterogeneous widgets into (a 200px chart beside a 3‑line text card), with no row‑span/masonry → **severely ragged card bottoms** on every row. The single biggest "not ServiceNow" tell. → `repeat(auto-fit, minmax(320px,1fr))` + per‑widget size hints (sm/md/lg) + row alignment.
- **D2 [H] Three dashboard construction systems** — Executive (bespoke, high‑craft), the five `DomainDashboardShell` dashboards, and ERM (a third hand‑rolled system with hand‑built card headers at `12/14` padding vs the `Card` component's `14/18`, and hardcoded hex). → Converge ERM onto `Card`/shell; hold one column ratio down each page.
- **D3 [M] KPI ribbon is ragged** — Executive `KpiTile` has no `min-height`, and only tiles 1 & 4 carry a microbar, so tiles 2 & 3 are visibly shorter. → `minHeight: 132` on the tile.
- **D4 [M] Hero gauge under‑weighted** — the Executive posture gauge (172, ~45px numeral) competes with a 28px greeting `h1`; the hero has no `min-height` so it collapses to a banner on short captions. → `size={200}` (or drop greeting to 22px) + hero `min-height`.
- **D5 [M] RAG band breakpoints disagree** — gauges band at 60/85 (Cyber, BCM) vs 70/90 (PDPL) vs `tonePct` 30/60/85 (KPIs). A 50% framework shows an amber KPI but a red gauge. → One RAG breakpoint set everywhere.
- **D6 [L] SCM/SOC are all‑text** — SCM's LineChart (1.4fr) beside a 90px text card is the most extreme ragged pairing; SOC has 11 text cards, no viz. The primitives exist (`BubbleQuadrant`, gauges, `CountdownRing`) — under‑deployed.

---

## 9. Registers / lists / tables craft

- **L1 [H] `.grc-table` (30 files, Compliance/NDI/TPRM/…) and `.tbl` (SOC/Pentests) have NO CSS** — they render as **UA‑default tables** (serif ~16px, no padding/zebra/hover/sticky), patched with per‑cell inline `fontSize:13/11/10`. Pentests financial/findings data drops to **10px**, below the legibility floor. → Define `.grc-table`/`.tbl` as `.risk-table` equivalents (one CSS block re‑skins ~30 pages) or migrate to `<DataTable>`.
- **L2 [M] Numerics center‑aligned, not end‑aligned, and missing `tabular-nums`** — Risk Inherent/Residual use `align:'center'`; Pentests cost is left‑aligned 10px mono. → `align:'end'` + `tabular-nums` for numeric/score/currency columns (one `.num` cell class).
- **L3 [M] Badges don't use the `--risk-*` ramp** — severity badges fall back to the coarse 4‑tone semantic set, losing the vhigh/crit distinction the heatmap/score‑pills express. → `badge-risk-*` variants driven by `--risk-*`.
- **L4 [M] `<select>` height ≠ button height** in toolbars — Obligations filter selects (`4px 8px` ≈26px) are shorter than the 36–40px buttons beside them → ragged filter baseline. → One `.input/.select` height (36px) everywhere; one `.result-count` style (mono 12px, end‑aligned); `ExportMenu` on every register.
- **L5 [M] Row‑action column crowded** — an OPEN risk row renders 5 buttons (~160px) in a 200px column. → Keep edit/delete inline; workflow transitions → `⋯` overflow. Raise resting opacity 0.55→0.7 and force opaque on `@media (hover:none)`.
- **L6 [L] Empty/loading inconsistency** — Pentests renders a bare `—` as its empty state and hand‑rolled "Loading…" text; `SkeletonRow`/`EmptyState` exist but are unused on these pages.

---

## 10. Detail & workflow pages craft

- **DT1 [H] Status‑badge has three homes** — title‑row badge (Policy/Risk/Control), the `actions` slot next to a button (AI Model), and a separate chip card below the header (Outsourcing). The most‑scanned attribute on a record page should sit by the title (ServiceNow/AuditBoard convention). → One home: the title‑row badge slot.
- **DT2 [H] Two tab components** — Pattern‑1 `.tab` (teal segmented pill, 12px/700, shadow, `--primary-700`) vs AI Model's bespoke `.aimd-tabs` (underline, 13px/400→600, `--accent`). Different size, weight, active mechanic, colour, count‑chip radius. → One tab component (recommend the underline record‑page convention); route AI Model through `<Tabs>`.
- **DT3 [H] Five field renderers** — `RailField` (grid 110/1fr, dashed), plus four local `Field`/`DataPair` copies with labels at 10/11/12px, **stacked vs inline** layouts, and no/solid/dashed separators. Policy's and Control's `Field` are byte‑identical copies. → One `<Field variant="stacked|inline">` primitive; delete the four copies. Widen the RailField label column `110→120–130px` (or `minmax`) so uppercased Arabic labels don't wrap against single‑line values.
- **DT4 [H] AI Model action hierarchy is inverted** — the destructive Decommission is a `ghost`, and the forward lifecycle transitions are ghost buttons buried in the Overview tab, so the primary path has **no primary button**. → Decommission → `danger`; surface the next stage as a header `primary` (match Pattern‑1).
- **DT5 [M] `DetailHeader` actions top‑align** (`flex-start`) → buttons float ~14px above the title baseline. → `alignSelf:center` on the actions cluster.
- **DT6 [M] `DriftChart` sits in a hand‑styled bordered `<div>` inside the tab Card** → double border/nested container; and its fixed 520px width leaves a gutter (a time‑series should stretch). → Render in a `<Card>` like `CrosswalkFan`; make width fluid.
- **DT7 [M] Three back‑affordances** (DetailHeader arrow vs `.aimd-back` text button vs Outsourcing "Back" action) and two eyebrow treatments (11px sans grey vs 10px mono teal). → One back‑affordance (the DetailHeader arrow), one eyebrow.

---

## 11. Forms, modals, wizards craft

- **F1 [H] Required‑asterisk is invisible** — system `Input/Select/Textarea` never render a `*`; three other conventions exist (`.wf-required`, baked into the label string, hardcoded `<span>`). → Render `*` from `.input-label` via the `required` prop; one convention.
- **F2 [H] Bespoke forms break field‑height parity** — the GrcExpert dialog's local `inputStyle` (`6px 8px`, ~28px) puts a `<select>` and an `<input>` side‑by‑side that don't align in height, and both are ~12px shorter than the 40px system `.input`. → Use system `Input/Select/Textarea` (height parity + asterisk + focus ring + RTL for free).
- **F3 [H] Modal footer not pinned robustly** — no `flex-shrink:0` on header/footer; no scroll‑shadow cue when body content clips. → `flex-shrink:0` + footer scroll shadow.
- **F4 [M] Form vertical gap & gutter inconsistent** — modal `14px`, wizard `8px`, dialog `12px`; FormModal column gutter `12px` vs OneTrust's 16–24. Two grid models (12‑col explicit vs auto‑fit). → One vertical rhythm (14px) + one gutter (16px); keep 12‑col for FormModal.
- **F5 [M] Wizard content padding (16px) tighter than modal body (20px)**; the horizontal stepper at ≤768px keeps full‑size cards (`width:100%`) instead of a thin progress strip. → Wizard content `20/24`; compact horizontal stepper (`width:auto`, hide sub, 24px marker). Add a "step n / total" caption.
- **F6 [M] SearchableSelect popover can clip** inside a scrolling modal body (absolute, no portal/flip). → Portal + collision flip, or open‑upward for bottom rows.

---

## 12. Specialized surfaces, pill‑tabs, RTL & responsive

- **SP1 [H] One pill/segmented control, not four.** System `.tab` (radius 6) vs GrcExpert family tabs (radius **20**, the odd one) vs Posture chips (999) vs Evidence sub‑tabs (pill) — four paddings, four radii, three active‑text colours. → Extract one `.pill-tab`/`.is-active` (`padding:6px 14px; radius:--r-pill; font:600 13px; active{ bg:--primary; color:--ink-on-primary }`); replace all.
- **SP2 [H] GrcExpert `opacity` muting** (`0.5/0.6/0.7` ×20) instead of `--ink-2/--ink-3` — fades borders too, and contrast on `--surface-2` likely fails AA (the exact lesson the KPI‑label fix already documented). → Replace with ink tokens. Also: use the system `<Kpi>` (like PosturePage) instead of the bespoke smaller `KpiTile`.
- **SP3 [M] Posture heatmap is a second cell system** — inline pills with a hardcoded green `rgba(46,156,94, 0.2+n*0.12)` ramp (tops ~0.92 with no text‑contrast compensation) and **no legend**. → `color-mix(in srgb, var(--success) X%, transparent)`, add a 3–4 swatch legend, flip text to white above ~0.6 alpha, `title=` on truncated `<th>`.
- **SP4 [M] Touch‑target rule over‑inflates inline controls** — the global `pointer:coarse { min 44×44 }` blows up the 28px attachment doc‑type `<select>`/remove‑`<button>` and in‑row icon buttons on tablets. → Exclude `.btn-sm`/inline selects/in‑row icon buttons.
- **SP5 [M] Two responsive dead‑ends** — `FormModal` grid (B6) and GrcExpert's inline `280px 1fr` body (no breakpoint → horizontal overflow < 600px). → `className="form-12col"` and `className="rail-layout"` respectively (both rules already exist).
- **SP6 [L] RTL discipline is genuinely good** — logical properties throughout; the only offenders are the unstyled `ExportMenu right:0` (B7) and the capped‑label tracking (T4). Login `.login-error` uses a hardcoded `#ECCFCC` border (→ `--danger-border`).

---

## 13. Tokens to add / fix (the enabling layer)

Most fixes above resolve to a small set of token additions + a few corrections:

| Token | Purpose | Note |
|-------|---------|------|
| `--text-3xs: 10px` | the 10px tier used 8+ times | snaps `10.5` off‑scale sizes |
| `--num-hero: 36px` · `--num-tile: 24px` | the two headline‑number sizes | collapses 18/22/24/30/36 |
| `--gauge-hero: 172/15` · `--gauge-md: 108/10` | one gauge system | fixes Cyber 96 vs BCM 108 |
| `--chip-pad / --chip-font (11) / --chip-radius` + `-sm` | one chip system | collapses 6 pill variants |
| `--card-pad-x / --card-pad-y` | one card padding | collapses 14/16/18/20 |
| dark `--cat-1..8` block | dark‑mode chart series | fixes invisible ribbons/lines |
| `--leading-relaxed: 1.8` | the line‑list line‑height | retires hardcoded 1.8/1.9 |
| `--touch-lg: 38` · `--touch-sm: 28` | icon‑button ladder | documents 38/28 |
| **fix** `--risk-crit → align to --danger` | one "critical" red | the two‑reds fix |
| **add** `--dur-base`? no — **fix call sites** to `--dur-normal` | — | B5 |
| **define** `--card-border`, `--mono`, `--primary-wash`, `--surface-0` *or* fix call sites | — | B2 (prefer fixing call sites to existing tokens) |

Plus shared components to extract: **`<Field variant>`**, **`<PillTabs>`/`.pill-tab`**, **`<Chip>`/`.filter-chip`**, and `.grc-table`/`.tbl` → `.risk-table` rules.

---

## 14. Prioritized roadmap

### Phase 1 — Bug fixes + token corrections (1–2 days, mostly find‑replace)
B1–B7 (broken page, undefined tokens, dead CSS, colour collisions) · fix `--risk-crit`/dark `--cat-*` · snap off‑scale fonts/spacing/radii to tokens (add `--text-3xs`) · `tabular-nums` on numeric cells · the RTL capped‑label rule. **Highest visible return per hour.**

### Phase 2 — Consolidate duplicated controls
One `.pill-tab` (4→1) · one `<Field>` (5→1) · one tab component (route AI Model through `<Tabs>`) · one chip system (6→1) · `.grc-table`/`.tbl` → styled · one gauge size system · one headline‑number token · GrcExpert/Posture/Login onto system primitives.

### Phase 3 — Composition & responsive polish
`.dash-grid` → auto‑fit + size hints (kills ragged bottoms) · KPI ribbon `min-height` · unify RAG band breakpoints · status‑badge home + action hierarchy on detail pages · sticky‑footer + form gutter rhythm · responsive dead‑ends (`form-12col`, `rail-layout`) · touch‑target exclusions · Posture heatmap legend.

---

## 15. Benchmark scorecard

| Leader | Their craft signature | Our gap |
|--------|----------------------|---------|
| **ServiceNow IRM** | uniform card heights per row; one record‑title size; status by the title | `.dash-grid` ragged bottoms; 22 vs 26 titles; status in 3 homes |
| **AuditBoard** | one field‑row + one table everywhere; right‑aligned tabular numerics | 5 field renderers; unstyled `.grc-table`/`.tbl`; centered numerics |
| **Drata / Vanta** | one chip height, one elevation tier, every page through the same form/tab primitives | 6 chips, 4 pill‑tabs, bespoke GrcExpert dialog |
| **OneTrust** | dark‑mode parity of data‑viz colour; required marked at the label; heatmap legend | missing dark `--cat-*`; invisible asterisk; Posture has no legend |
| **Diligent** | one clear focal metric, proportioned hero | hero gauge under‑weighted vs greeting; SOC/SCM all‑text |

**The encouraging part:** the panel found **no need to change the palette, the scale, or the RTL strategy** — those are already at or above benchmark. Every fix is a *consumption* change (snap to a token, delete a duplicate, fix a call site). Phase 1 alone closes the bugs and most of the visible drift.

---

## 16. Anti‑regression checklist (design QA "definition of done")

For any new/edited UI:
1. **No off‑scale values** — every font on `--text-*`, space on `--sp-*`, radius on `--r-*`, duration on `--dur-*`, colour on a token (lint against raw hex/px in `components/`/`pages/`).
2. **One component per job** — use `<Field>`, `<PillTabs>`, `<Chip>`, `<Tabs>`, `<DataTable>`, `<Kpi>`, `Input/Select/Textarea`; never re‑implement.
3. **Status colour single‑sourced** — `--risk-*`/semantic via the helper; one "critical" red, one "success" green.
4. **Numerals are `tabular-nums`; numeric columns end‑aligned.**
5. **Required marked at the label; field heights equal; modal/wizard gutters on the rhythm token.**
6. **RTL by construction** — logical properties only; capped labels drop tracking/uppercase in Arabic; verify at `mobile/tablet/desktop` with no clipping.
7. **Dark mode verified** — especially chart series (`--cat-*`) and SVG text on brand fills.

---

*Prepared by a 5‑member committee of GRC‑tool design leaders. Granular front‑end visual craft only; no backend or feature change implied. Companions: `frontend-design-review.md`, `frontend-visualization-playbook.md`.*
