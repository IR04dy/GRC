# TSS GRC Platform — Visualization & Interaction Playbook
## Committee of GRC-Tool Design Leaders · page-by-page hero charts, diagrams, effects & high-end modern design

> **Companion to** `frontend-design-review.md`. Where the review says *what to fix*, this playbook says *what to build* — the **signature visualization, interactions, and premium treatment for every page**, benchmarked against the category leaders.
> **Stack reality (verified):** React + Vite + TS · Recharts (RadialBar, Area, Line, Bar, Scatter, Sankey, Radar, Treemap) + hand-rolled SVG (`components/viz/`: TimeSeries, HeatMap, Donut, BarChart3D, CoverageMatrix, RiskTable) · mature token system (`--primary #0E5E5E`, `--risk-vlow…crit` 6-step ramp, `--cat-1..8`, semantic `-bg/-border/-fg`, `--grad-hero/aurora`, motion `--dur-*`/`--ease-spring|out`, full dark theme) · Arabic-primary RTL. **Every hero below is buildable in this stack.**
> **Date:** 2026-06-02 · **Method:** 5-member expert panel, each owning a module cluster + a benchmark persona.

---

## The committee

| Member | Cluster | Benchmark leaders channeled |
|--------|---------|------------------------------|
| **Risk & Resilience** | ERM, Risk register/detail, KRI, appetite, BCM, incidents | RSA Archer · LogicGate · ServiceNow IRM · MetricStream |
| **Privacy & Data** | PDPL, DSR, breach, ROPA, DPIA, consent, data flows, classification, NDI | OneTrust · Securiti.ai · BigID · TrustArc |
| **Compliance & Audit** | Posture, obligations, controls, policies, findings, audit, IA, committee, evidence | AuditBoard · Workiva · MetricStream · Hyperproof |
| **Cyber & Third-Party** | Cyber, SOC, vulns, attack surface, TPRM, 3rd-party-cyber, 4th-party, outsourcing | ServiceNow SecOps · Drata · Vanta · ProcessUnity · Prevalent · BitSight |
| **Executive · AI · Modern-Craft (Chair)** | Executive cockpit, AI governance, GRC Expert, board pack + the platform-wide design language | Diligent · Tableau/Power BI · Linear/Vercel/Stripe · Credo AI · IBM watsonx.governance |

**Two unifying doctrines the committee agreed on:**
1. **Every regulatory deadline is a live, self-depleting ring/timer** (green→amber→red, with a relief "seal" on completion) — not a number in prose.
2. **Every compliance gate is encoded as a visual style** (a missing PDPL Art. 7 ref = dashed red arc; a vendor with no DPA = pulsing red node) so that **violations find the user**, not the other way around.

And one surface rule: **Cyber/SOC/crisis = dark "war-room"; everything else (board, privacy, audit) = light "boardroom."** The contrast is itself a navigation signal.

---

## Part 1 — The chart & diagram vocabulary

The full set of visualization types the committee prescribes, where each belongs, and how to build it. Reuse this as the platform's chart legend so the eye learns one grammar.

| Visualization | Primary use | Where (signature) | Build |
|---------------|-------------|-------------------|-------|
| **5×5 risk matrix w/ inherent→residual arrows** | risk posture, control effectiveness | ERM dash, Risk register/detail, New-risk | hand-SVG (`RiskMatrix5x5`) |
| **Radial gauge / arc-as-number** | single headline metric vs bands | Exec posture hero, appetite, BCM readiness, KRI health, MTTD/MTTR | hand-SVG / Recharts `RadialBar` |
| **Banded trend line (threshold zones)** | KRI vs appetite, drift vs threshold, SLA | KRI tab, AI drift, incident trend | `TimeSeries` + `bands`/`markers` |
| **Countdown ring / war-room timer** | regulatory deadline (live) | DSR 30-day, Breach 72h SDAIA, regulator countdowns | hand-SVG `SlaRing`/`CountdownDial` |
| **Aging funnel / heatmap (severity × age)** | overdue concentration | DSR aging, vuln aging, finding aging | flex segments / SVG grid |
| **Bowtie diagram** | causes→event→consequences + controls | Risk detail | hand-SVG |
| **Sankey (crosswalk / lineage / flow)** | test-once-satisfy-many; data lineage; alert funnel | Control detail, Data flows, GRC Expert master, SOC | Recharts `Sankey` / hand-SVG |
| **Force-directed network graph** | blast radius / dependency / concentration | Attack-surface, 4th-party, ROPA, sub-processor chain | shared hand-SVG `ForceGraph` |
| **Bubble quadrant (X×Y, size, color)** | risk-vs-criticality, materiality, audit universe | TPRM vendors, Outsourcing materiality, IA universe, BIA | Recharts `Scatter` |
| **Residency map (KSA-centric trust zones + arcs)** | cross-border data transfer | Data flows, PDPL dash, NDI | hand-SVG `ResidencyMap` |
| **Treemap** | concentration / sensitivity | Data classification, vendor/4th-party concentration | Recharts `Treemap` |
| **Radar / spider** | multi-axis posture | NCA framework coverage, control maturity, AIA risk | Recharts `Radar` |
| **Gantt / cadence swimlane timeline** | scheduling, lifecycle, recert cadence | Audit engagements, IA plan, AI lifecycle, report schedules, outsourcing lifecycle | hand-SVG `MiniGantt` |
| **Calendar heatmap** | freshness / collection over time | Evidence freshness, recert load | hand-SVG `FreshnessCalendar` |
| **SPC control chart (mean ± σ)** | process stability | Incident MTTR, SOC response time | Recharts `Line` + `ReferenceLine` |
| **Lifecycle funnel / stepper** | stage progression | AI register, DSR/breach status, campaign attestation | flex/SVG `LifecycleFunnel` + `WorkflowStepper` |
| **Disparity / diverging bars** | fairness, DPIA before/after | AI bias audit, DPIA mitigation | Recharts `Bar` |
| **Burndown / stacked area** | remediation vs target | Obligations, vuln SLA, findings | Recharts `Area` |
| **Grade ring + ridgeline** | continuous third-party rating | 3rd-party cyber ratings | hand-SVG + Recharts |

---

## Part 2 — Page-by-page playbook

Each entry: **Signature** (the one hero) · **Support** (2–4 secondary) · **Interact** · **Modern** (premium treatment) · **Benchmark** (match/beat) · **★ Hero moment**.

### 2.0 Executive & Cross-Module Cockpit

**Executive Dashboard** — `pages/dashboard/ExecutiveDashboard.tsx`
- **Signature:** **Posture-Hero radial arc with count-up** — the 56px score becomes a 240° gauge segmented into RAG bands; needle/arc sweeps 0→value, numeral counts up in sync.
- **Support:** KPI ribbon (staggered entrance, count-up, animated microbars) · risk heatmap (cell hover-lift + drill) · 12-week trend with appetite-ceiling threshold band · framework readiness as bullet-bars (actual vs target marker).
- **Interact:** every tile/cell/bar deep-links with query params; hover reveals an `ArrowUpRight` "open" glyph (universal drill signal).
- **Modern:** keep the gradient slab, add a tasteful `--grad-aurora` wash (≤60%, top-trailing); the canonical **non-draggable** layout is correct for the C-suite — opinionated > configurable.
- **Benchmark:** *Diligent gives one board number; ServiceNow gives a dense strip — beat both with arc-as-number.*
- **★ Hero moment:** one ~1s choreography on login — arc sweeps, numeral counts up, KPI tiles cascade in beneath. (Reduced-motion → snap to final.)
- **Honesty fix (from the review):** wire the KPI deltas + AttentionStrip to real data or honest empty states before the "All figures live" badge is credible.

---

### 2.1 Risk & Operational Resilience

**ERM Dashboard** — `pages/dashboards/ErmDashboardPage.tsx`
- **Signature:** **5×5 inherent→residual heatmap with movement arrows** — each cell shows a count + a vector from the inherent to the residual cell; arrow direction/length = control effectiveness made visible.
- **Support:** severity donut (center count-up) · KRI health half-gauge · 12-week open-vs-critical line with dashed appetite ceiling · top-8 category diverging bar.
- **Interact:** inherent↔residual toggle animates arrows collapsing/extending; cell hover → top-3 risks tooltip; category bar selection dims non-matching cells (cross-filter).
- **Modern:** staggered tile reveal; cells get inner-shadow + luminous risk-ramp border; arrows draw via `stroke-dashoffset`; glass card headers.
- **Benchmark:** *Archer/MetricStream ship a static residual heatmap — beat with the animated inherent→residual vector field.*
- **★ Hero moment:** the **arrow storm** animating in — a field of vectors pointing down-left toward green, any *deteriorating* (up-right) arrow pulsing red.

**Risk Register (+ Heatmap/KRI/Appetite tabs)** — `pages/risk/RiskRegisterPage.tsx`
- **Signature (register):** **inherent→residual delta bullet-bar** per row (track 0–25, inherent marker, residual marker, colored delta) — control gaps (no movement) jump out down the column. Plus a **residual-distribution histogram** above the table doubling as a band filter.
- **Signature (KRI tab):** **KRI line with green/amber/red appetite bands** + breach points (promote the 60px sparkline to a banded trend).
- **Signature (Appetite tab):** **risk-appetite radial gauge** per statement (exposure vs tolerance, true overshoot past 100%) + **what-if tolerance slider** that recolors every gauge live.
- **Interact:** histogram bar → `level=` filter; KRI breach dot → detail with red ripple; appetite what-if slider drag → gauges flip green→red in unison.
- **★ Hero moment:** dragging the what-if slider and watching a row of gauges flip red as tolerance tightens.
- **Benchmark:** *ServiceNow IRM executive feel on appetite — beat on interactivity (live what-if).*

**Risk Detail** — `pages/risk/RiskDetailPage.tsx`
- **Signature:** **Risk Bowtie** — causes → threat event (knot) → consequences, preventive controls on left ropes, mitigating/recovery on right, each colored by effectiveness. Category-defining for web GRC.
- **Support:** inherent→residual mini-matrix locator · treatment burndown / control-effectiveness gauge · velocity sparkline · status stepper rail.
- **Interact:** hover rope → control detail; "remove this control" what-if → residual dot springs to a new cell on the locator.
- **★ Hero moment:** the **bowtie assembling itself** — ropes drawing outward from the central knot, controls fading in.
- **Benchmark:** *MetricStream offers bowtie only in heavyweight desktop — ship an interactive what-if bowtie in the browser.*

**New Risk Modal** — `pages/risk/NewRiskModal.tsx`
- **Signature:** **live 5×5 placement preview** — picking L/I lights the target cell and the score pill recomputes with a count-up; the matrix is also an input (click a cell to set L/I).
- **★ Hero moment:** the cell **igniting** critical-red as impact is pushed to 5.

**BCM Dashboard** — `pages/dashboards/BcmDashboardPage.tsx`
- **Signature:** **RTO-vs-Actual recovery timeline** — per critical service, RTO target marker vs last-exercise actual recovery, gap colored met/breached. (Currently a 2-line `<ul>` — the biggest upgrade on the page.)
- **Support:** **BIA/BCP/DRP completion as three concentric "activity rings"** · exercise-cadence cumulative area · **regulator-countdown dials** (live ticking) · gaps/CAPA stacked bar.
- **Modern:** Apple-Watch-style rings sweep to value on mount; countdown dials tick sub-second; RTO bars grow from zero staggered.
- **★ Hero moment:** the three completion rings filling in concert, then the RTO timeline snapping in with red breach bars flagged.
- **Benchmark:** *Archer BCM shows RTO as a number — beat with the ranked per-service RTO-vs-actual gap.*

**BCM sub-pages** — `pages/bcm/*`: **BIA** → criticality-vs-RTO quadrant; **Exercises** → cadence line w/ pass-fail markers + target band; **Plans** → service×plan-type coverage/freshness matrix; **Crisis Room** → dark **live countdown wall** + affected-service status board (`LivePulse`); **Board Pack** → print-clean resilience scorecard.

**Incidents** — `pages/incidents/IncidentsPage.tsx`
- **Signature:** **Incident MTTR SPC control chart** (mean + ±3σ limits; breaches flagged) — replaces the fake "4.2h" KPI with a defensible process view.
- **Support:** severity-stacked area over time · NIST-phase funnel · regulator-notification SLA dial strip (promoted from the modal).
- **★ Hero moment:** an MTTR point **punching above the upper control limit** with a red ping + auto-tooltip naming the culprit incidents.
- **Benchmark:** *ServiceNow shows MTTR as a number — beat with SPC (normal variation vs real breach).*

---

### 2.2 Privacy & Data

**PDPL Dashboard** — `pages/dashboards/PdplDashboardPage.tsx`
- **Signature:** **privacy posture cockpit** — DSR 30-day SLA-health gauge (left) + cross-border residency donut↔map (center) + Breach 72h exposure ring (right), the two regulatory clocks as the literal hero.
- **Support:** DSR+breach trend (gradient area) · PIA coverage stacked bar · consent momentum twin-spark · processor/retention alert chips (pulse only when >0).
- **Modern:** glassmorphic hero band; countdown rings emit a halo that intensifies green→amber→red as deadlines near; RTL Arabic-Indic numerals.
- **★ Hero moment:** hero objects assemble in sequence; any breach inside its 72h window pulses its red halo once — "the platform is watching the clock for you."
- **Benchmark:** *OneTrust splits DSR aging and breach onto separate tabs — beat by unifying both clocks on the landing view.*

**DSR Register** — `pages/dataOfficer/DsrPage.tsx`
- **Signature:** **30-day SLA aging funnel** (>20d · 10–20 · 3–10 · <72h · OVERDUE) + **per-row live countdown rings** (28px donut depleting vs 720h, amber <72h, red overdue).
- **Support:** request-type donut · channel intake bar (SDAIA_PORTAL highlighted) · workflow-stage Sankey.
- **Interact:** funnel bucket → filter table; ring hover → "Due … · extendable +60d (Art. 9)."
- **★ Hero moment:** the aging funnel animates in; any OVERDUE segment snaps wider with a red flash.
- **Benchmark:** *OneTrust DSAR uses a due-date pill — beat with a proportional depleting ring + clickable aging funnel.*

**Breach Register** — `pages/dataOfficer/BreachPage.tsx`
- **Signature:** **72-hour SDAIA war-room countdown timer** (large, live `HH:MM:SS`, amber<12h/red overdue) on a dark card + exposure summary (affected-subjects odometer, special-category Art. 4 badge, cross-border flag).
- **Support:** severity×cause heatmap · notification timeline w/ 72h window band (overshoot = red) · affected-subjects bars · status funnel.
- **Modern:** dark war-room card; seconds in tabular-nums odometer; border "breathes" red under 12h; **green shield seal stamps** on notification.
- **★ Hero moment:** inside the final 12h the timer auto-pins the breach, border breathes red, odometer spins — a "drop everything" alarm — then the shield seal as payoff.
- **Benchmark:** *OneTrust shows deadlines as days/hours in a list — beat with a seconds-resolution SDAIA timer + one-click notify.*

**Data Flows (cross-border)** — `pages/dataclass/DataclassFlowsPage.tsx`
- **Signature:** **KSA-centric residency map** — concentric trust zones (In-Kingdom core → GCC → Adequate → Non-adequate), each flow an animated arc colored by adequacy; **arc style = Art. 7 status** (dashed + red pulse = missing `pdplArt7TransferRef`).
- **Support:** personal-data lineage Sankey · adequacy donut · Art. 7 compliance gauge · transfer-mode chips.
- **★ Hero moment:** arcs draw outward from the KSA core; any arc missing its Art. 7 ref finishes with a red pulse — "everything leaving the Kingdom, and what's not legally gated."
- **Benchmark:** *Securiti maps flows on a world map — beat with KSA-centric PDPL trust zones + Art. 7 gate baked into arc styling.*

**Data Classification** — `pages/dataclass/DataclassAssetsPage.tsx`
- **Signature:** **sensitivity treemap** keyed to `--cat-1..8` (tile = asset, area = volume, color = classification). **Support:** level bar (PDPL Art. 4 special-category split) · asset→flow linkage badge · "dark data" coverage gauge. **★ Hero:** tiles cascade in, most-sensitive `--cat-8` last with an outline pulse. *(BigID benchmark.)*

**Data Officer Dashboard** — `pages/dataOfficer/DataOfficerDashboardPage.tsx`
- **Signature:** **cross-module obligations deadline rail** — DSR + breach + retention + consent-expiry pills positioned by time-to-deadline, urgency-ramped. *(Securiti PrivacyOps benchmark — beat OneTrust's module silos by triaging by legal deadline.)*

**Privacy detail pages:** **DSR detail** → lifecycle stepper + live 30-day SLA ring (+ Art. 9 extension on the ring); **Breach detail** → 72h SDAIA timeline with overshoot band + shield seal; **ROPA detail** → **processing-activity network graph** (controller↔processor↔category↔purpose↔destination, Art. 7-deficient nodes pulse red); **DPIA detail** → **before/after residual-risk paired bars** + likelihood×impact scatter with dots animating down-left; **Consent detail** → opt-in/withdrawal funnel + consent-receipt timeline; **Sub-processors** → supply-chain tier graph (no-DPA nodes glow red).

**NDI / National Data (SDAIA/NDMO)** — `pages/ndi/NdiPage.tsx`
- **Signature:** **NDMO classification treemap + in-Kingdom residency gauge** (4-tier Top-Secret/Secret/Restricted/Public). No commercial benchmark — **category-defining**; Top-Secret/Secret non-resident tiles pulse red.

---

### 2.3 Compliance & Audit

> Cross-cutting rule: **one shared legend grammar** — coverage/freshness/maturity all map onto the same green→amber→red ramp so a partner can read any tile cold.

**CompliancePage** — `pages/compliance/CompliancePage.tsx`
- **Signature:** **framework-ring cluster** — 4–6 concentric radial gauges (NCA ECC, SAMA CSF, PDPL, BCM, ISO 27001), each filled to coverage %, with a 90-day sparkline tucked under each. **Interact:** click a ring → cross-filter the page (others dim to 30%). **★ Hero:** rings sweep 0→value in a staggered cascade, center % counting up — "vitals monitor coming online." *(Hyperproof health-rings benchmark.)*

**PosturePage** — `pages/compliance/PosturePage.tsx`
- **Signature:** **authority × domain coverage heatmap** — upgrade the existing HTML-table heatmap to the tokenized `--risk`/coverage ramp + per-cell mini-fill bar (keep it an HTML table for free RTL). **Interact:** cell → citation-level gap list (document×domain atomic unit); family chip cross-filter. **★ Hero:** switching family filter re-tweens every cell's color (FLIP) — "NCA prep → SAMA prep" recolors the matrix. *(AuditBoard benchmark — beat with cell-atomic drill.)*

**Obligations** — `pages/compliance/ObligationsPage.tsx`: **Signature** obligation-fulfillment **burndown stacked-area** with a "today" line + regulator-deadline markers + projected-completion dashed line (green if before deadline). **★ Hero:** live "days to next regulatory deadline" counter with extrapolated completion. *(Workiva benchmark.)*

**Obligation Campaigns** — `ObligationCampaignsPage.tsx`: **Signature** response funnel (Sent→Opened→Responded→Approved) + completion thermometers; **★ Hero** a campaign crossing 100% does a success shimmer + promotes to a "Complete" lane. *(MetricStream.)*

**Obligation Detail** — `ObligationDetailPage.tsx`: **Signature** obligation lineage graph (citation → controls → evidence, link thickness = freshness; stale = dashed red). **★ Hero:** hover the obligation node → the fulfilled path lights end-to-end; stale evidence flares amber. *(AuditBoard linkage.)*

**Control Detail** — `pages/detail/ControlDetailPage.tsx`
- **Signature:** **framework crosswalk Sankey** — one control → every authority citation it satisfies (NCA/SAMA/ISO/PDPL), family-colored ribbons. The visual embodiment of **"test once, satisfy many."**
- **Support:** control-maturity radar (design/implementation/evidence/testing/monitoring) · finding-history strip · evidence-freshness gauge.
- **★ Hero moment:** a large **`×11` reuse-multiplier badge** over the control node that, on hover, fans out all 11 citation ribbons in a staggered cascade — "this control justifies the platform."
- **Benchmark:** *No competitor renders this well — category-defining differentiator.*

**Policy Detail** → lifecycle timeline + attestation coverage ring (overdue review refuses to look green). **Finding Detail** → **aging waterfall** (days-in-each-state, SLA-breach zones hatched red) + remediation burndown; **★ Hero** the "days open vs SLA" bar turns red and pulses on breach. *(AuditBoard.)*

**Audit Workspace** — `pages/audit/AuditWorkspacePage.tsx`: **Signature** **engagement Gantt swimlanes** (Planning→Fieldwork→Reporting→Follow-up, today-line, milestone diamonds) + a coverage heatmap (universe × period) beneath. **Interact:** drag-to-reschedule, zoom quarter↔week. **★ Hero:** year→week zoom glides with a fluid scale transition — "flight-deck planning board." *(AuditBoard — beat with drag + coupled coverage.)*

**Engagement Detail** → phase pipeline with severity-stacked finding chips per phase. **IA Universe** — `pages/audit-ia/IaUniversePage.tsx`: **Signature** **risk-bubble matrix** (likelihood×impact, bubble size = audit hours, **color = last-audit recency** so "high-risk + stale" jumps out); **★ Hero** a "Plan from risk" button flies the top-N risky/stale bubbles into the plan panel. *(MetricStream.)* **IA Plan** → annual Gantt + capacity-utilization heatstrip (over-allocation flares red on drag). **IA Engagement** → workpaper completion matrix (prepared vs reviewed lens). **Audit Committee** → restrained RAG assurance matrix + findings-trend (print-first, one-click board pack).

**EvidencePage** — `pages/evidence/EvidencePage.tsx`
- **Signature:** **evidence-freshness calendar-heatmap** (GitHub-style, fresh `--success` → expired `--danger`) + **expiry timeline**.
- **Support:** coverage-by-framework banded bars · collection funnel · stale/expired tiles.
- **★ Hero moment:** the visible **"cold streak"** where collection lapsed, plus a floating "47 items expire in 14 days" alert that sweeps those cells into a renewal worklist.
- **Benchmark:** *Hyperproof shows freshness as a red-badge list — beat by making the lapse visible as a cold gap.*

**GRC Expert master crosswalk** (cross-cut): the Control-detail Sankey at portfolio scale — control families → authority families, with a **"Show gaps only"** toggle that collapses the healthy web to just the broken red ribbons (the org's compliance debt, laid bare).

---

### 2.4 Cyber & Third-Party

> **Cyber = dark SOC war-room** (`--soc-bg #0A1414`, emissive glow). **Third-party/outsourcing = light boardroom.**

**Cyber Dashboard** — `pages/dashboards/CyberDashboardPage.tsx`
- **Signature:** **NCA ECC/CSCC/CCC/OTCC coverage radar** — two overlaid polygons per framework (assessed vs compliant); the gap is a glowing crescent. **Support:** incident-trend gradient area w/ SLA reference line + anomaly dots · gap→remediation funnel · evidence-expiry gauge. **★ Hero:** four framework polygons animate in sequence; overall-score conic ring counts up with a `--soc-glow` pulse. *(Drata/Vanta spider — beat with assessed-vs-compliant crescent.)*

**SOC Ops Dashboard** — `pages/socops/SocOpsDashboardPage.tsx`
- **Signature:** **MTTD/MTTR twin radial gauges + SPC control chart** (UCL/LCL ±3σ, out-of-control points red). **Support:** live SOC pulse strip · alert funnel · severity stacked-area (24h) · analyst workload heat-row.
- **Modern:** full dark war-room; needle motion-blur; CRT scanline on the pulse strip.
- **★ Hero moment:** a live **"heartbeat" line** across the top — each SOC event injects a glowing severity-colored pulse rippling toward the inline-start; a CRITICAL flares and recomputes MTTR with a count-up.
- **Benchmark:** *ServiceNow/Splunk show MTTR as a number — beat with the SPC overlay.*

**Vulnerabilities V2** — `pages/cyber/VulnerabilitiesV2Page.tsx`
- **Signature:** **vuln-aging heatmap** (severity × age bucket 0–7/8–30/31–60/61–90/90+; SLA-breach cells hatched red). **Support:** SLA burndown stacked-area w/ committed-remediation target line · severity donut (keep its `onPointClick` cross-filter) · CVSS histogram w/ draggable threshold. **★ Hero:** the heatmap "ignites" diagonally; any CRITICAL-90+ cell locks into a steady red glow + count-up badge. *(ServiceNow VR — beat with click-to-cross-filter into the same TanStack register.)*

**Attack-Surface Asset Detail** — `pages/cyber/AttackSurfaceAssetDetailPage.tsx`
- **Signature:** **attack-surface blast-radius graph** (asset center → services/ports, CVEs, dependencies, linked vendor). **★ Hero:** graph "explodes" outward, then edges light one-by-one tracing the highest-severity path from internet-facing entry to crown-jewel target. *(Fuses cyber + third-party on one canvas — beats SecurityScorecard's flat lists.)*

**TPRM / Vendors** — `pages/tprm/TprmPage.tsx`: **Signature** **vendor risk-vs-criticality bubble quadrant** (size = spend, **color = assessment freshness** so stale-but-critical stands out). **★ Hero:** bubbles rise from the X-axis into risk altitude; the "manage closely" quadrant ignites with a count-up. *(ProcessUnity/Prevalent — beat by encoding freshness.)*

**Third-Party Cyber** — `pages/third-party-cyber/ThirdPartyCyberPage.tsx`: **Signature** vendor **security-grade ring wall (A–F)** + portfolio **grade-distribution ridgeline**. **★ Hero:** the ridgeline "settles like sediment"; if portfolio mean drops below B the whole thing shifts danger-ward. *(BitSight/SecurityScorecard — beat with the portfolio distribution as hero.)*

**Vendor BCP Campaign** — `pages/tprm/VendorBcpCampaignsPage.tsx`: **Signature** campaign response radial + attestation chiclet grid + **criticality-weighted non-responder treemap** (chase the big ones first). **★ Hero:** landed attestations flip in sequence, completion-% counts up, crossing 80% triggers a celebratory glow sweep.

**Fourth-Party Exposure** — `pages/fourth-party/FourthPartyPage.tsx`
- **Signature:** **4th-party dependency network** (primaries → 4th parties → shared services) that **auto-highlights fan-in concentration** (one cloud/KYC provider behind half the supply chain) with a red halo. **Support:** concentration treemap · country-exposure bar · validation funnel.
- **★ Hero moment:** every edge converging on an over-concentrated node lights up at once, the node swells with a red halo + "N vendors depend on this one party."
- **Benchmark:** *Most tools stop at 3rd party — beat the field by making 4th-party concentration a first-class hero (SAMA supply-chain scrutiny).*

**Outsourcing Dashboard** — `pages/outsourcing/OutsourcingDashboardPage.tsx`: **Signature** **SAMA materiality matrix** (materiality × criticality bubbles; the MATERIAL×CRITICAL "SAMA-notification" quadrant tinted + hatched). **Support:** in-Kingdom residency donut · cloud-classification (T1–T4) bar · exit-plan readiness gauge + overdue-review strip. **★ Hero:** arrangements rain into the matrix; the SAMA-notification quadrant illuminates with its count snapping up. *(Category-defining — no mainstream vendor does SAMA materiality natively.)* **Outsourcing detail** → lifecycle timeline + obligation-compliance ring (exit-plan-tested as a first-class gate).

---

### 2.5 AI Governance

**AI Model Register** — `pages/ai/AiModelRegisterPage.tsx`
- **Signature:** **AI single-pane command grid** — lifecycle funnel (Development→Validation→Approved→Production→Decommissioned) + governance-gap chips (no fairness audit / no oversight / cross-border no basis / no signed AIA). **Support:** risk-tier donut (EU AI Act) · "high-risk × no signed AIA" red alert tile · framework coverage matrix · register table w/ drift-status pulse dots. **★ Hero:** funnel fills left-to-right, counts land, any red gap chip attention-pulses. *(Credo AI + Holistic AI — beat by fusing funnel + donut + coverage matrix on one pane.)*

**AI Model Detail** — `pages/ai/AiModelDetailPage.tsx`
- **Signature (Overview):** a proper **Model Card** hero with a **gate-readiness checklist** visualizing `approvalGateMissing()` as a stepper (blocked transitions become a lit "what's missing" panel, not a tooltip).
- **Signature (Drift tab):** **drift line with threshold bands** + breach markers (unacknowledged = pulsing ring; click → acknowledge).
- **Support:** **bias/fairness disparity bars** (per protected group vs the four-fifths 0.8 line) · **AIA risk radar** · lifecycle pipeline rail · provenance lineage chip-flow.
- **Modern:** model-card panel uses glass-on-`--grad-hero` to mark it the regulator-export artifact; drift areas use soft gradient fills.
- **★ Hero moment:** open Drift on a model with an active breach → the line draws on, the band fades in, the breach marker pulses red exactly where the metric crossed.
- **Benchmark:** *watsonx (drift) + Credo AI (model card/radar) + Holistic AI (bias) — out-integrate all three on one page.*

**AI Lifecycle** — `pages/aiLifecycle/*`: **Signature** **recertification/retraining cadence timeline** (swimlane per model, validation/retrain/recert markers, "today" line, overdue red zone). **Support:** status funnels · due-soon/overdue tiles · recert calendar heat-strip. **★ Hero:** swimlanes slide in, the "now" line drops in last, overdue markers pulse. *(ServiceNow model-ops worklist — beat with the time dimension.)*

---

### 2.6 GRC Expert & Reporting

**GRC Expert** — `pages/expert/GrcExpertPage.tsx`
- **Signature:** **cited "Expert Answer" canvas** — structured answer cards with inline citation chips (deep-link to controls/evidence/regulations) + a confidence/coverage meter. Never a raw chat bubble; always a sourced artifact.
- **Support:** suggested-action chips ("draft the CAPA," "generate evidence request") · source/evidence rail w/ relevance bars · streaming token shimmer.
- **Modern:** glass command bar; aurora behind the empty composer; answer cards rise+fade stagger.
- **★ Hero moment:** ask → skeleton-shimmer answer appears → text streams in and **citation chips "pop" in sequence** as each source resolves.
- **Benchmark:** *Generic tools bolt on a chatbot — beat with a cited-answer canvas (every claim links to its source).*

**Reporting / Board Pack** — `pages/reporting/*`
- **Signature (a):** **board-pack scrollytelling one-pager** — a live, paginated A4/letter preview that scrolls as a narrative (posture → top risks → readiness → AI → deadlines), each section snap-revealing; reuses dashboard viz at print scale. **Signature (b):** **regulator-aware schedule cadence rail** (SAMA H1 / NCA self-assessment / PDPL ROPA / Nitaqat Q2, color-coded by regulator with countdown chips).
- **Support:** export-format cards with live thumbnails (PDF/DOCX/XLSX/PPTX) · distribution map · run-history sparklines · drag-to-reorder section palette w/ live page count.
- **Modern:** "paper on desk" page card (`--shadow-3`); **fully mirrored RTL board pack** (cover, page numbers, section flow) — a native bilingual pack, not a flipped LTR one.
- **★ Hero moment:** "Preview board pack" → pages **assemble top-to-bottom** as live charts draw themselves into the document, then Export.
- **Benchmark:** *Diligent's board pack is the gold standard but static — beat with live-data scrollytelling that exports pixel-faithful (OpenPDF Arabic-RTL engine already in the backend).*

---

## Part 3 — The Modern Design Language (platform-wide)

The "high-end, fancy, modern — but regulator-credible" system. **Restraint is the luxury signal; when in doubt, do less, perfectly.** Every value maps to existing tokens.

**(a) Motion & choreography.** A small named vocabulary on existing tokens (`--dur-micro/fast/normal/slow`, `--ease-spring/out`):
- *Entrance stagger* — children fade+rise 8px, 40–60ms apart, cap at ~8 items / ~400ms (beyond that animate the container once).
- *Count-up* — hero numerals lerp 0→value ~600ms via `requestAnimationFrame`, with `tabular-nums` so width doesn't jitter.
- *Draw-on* — SVG via `stroke-dasharray` (`pathLength="1"`); area fills fade in *after* the stroke completes.
- *FLIP* — re-sort/filter/re-order animate the delta with `transform`, never `top/left`.
- *Rule:* one orchestrated sequence per page load, then the page is calm. **Reduced-motion → snap to final** (non-negotiable, regulator-facing).
- **Do:** stagger a KPI row in once. **Don't:** count-up a number on every background poll (only on first mount / user action; otherwise cross-fade the digit).

**(b) Depth & elevation.** Use the shadow ladder (`--shadow-1` rest / `-2` hover / `-3` modal & board-pack page); promote the repeated inline KPI hover shadow to a `--shadow-hover` token. **Glassmorphism selectively** — command bar, sticky filter bars, the model-card panel — never on dense tables/KPI tiles, always with a 1px hairline. **Aurora/gradient only behind brand heroes** (≤60%, top-trailing), never on chart data series.

**(c) Dark "command-center" mode.** Make dark the *default* for monitoring surfaces (SOC, crisis/BCM, AI drift, TV/wallboard). Depth comes from **inner glow + hairline borders**, not heavy drops; emissive accents (lifted `--danger`/`--primary`) for breaches/live pulses. **Don't** force dark on the board pack (it prints light).

**(d) Data-ink & chart craft.** Maximize data-ink, kill chartjunk (no 3D/shadows on data; `BarChart3D` reserved for one deliberate hero bar). **One accent per chart** — single-series `--primary`, categorical `--cat-1..8`, severity `--risk-*`/semantic; never mix categorical + semantic in one chart. Direct-label > legend where space allows; `tabular-nums` on all ticks/values. **Reference lines carry meaning** (appetite ceiling, drift threshold, four-fifths rule — dashed, labeled, toned). **Honest empties** — never fabricate a curve; flat-line at the current value with an honest label. Soft area gradients under trend lines, crisp full-opacity line.

**(e) Micro-interactions.** Card hover = `translateY(-1px)` + `--shadow-2` via a shared `.card--interactive` (not per-component inline). Focus = the tokenized `--focus-ring`, never bare `outline:none`. Drill affordance = a hover `ArrowUpRight` (universal "this navigates"). **Live pulse means exactly one thing** — "this datum is live/unresolved" (unacked drift, breached KRI, the now-line); overuse destroys the signal; reduced-motion → static dot. Optimistic feedback with a brief `--success` flash, reconcile on the server, toast on failure.

**(f) Loading choreography.** **Skeletons, not spinners**, for known shapes — `--surface-3` blocks with a shimmer sweep, matching the final layout (CLS = 0). Progressive reveal: shell → KPIs → charts. GRC Expert streams (token shimmer + citation chips resolving), never a blocking spinner.

**(g) Empty-state art.** Purposeful & branded — a lucide icon in a tinted `--primary-soft` chip + one-line "what this is" + a primary CTA ("Record the first fairness audit"). Distinguish *demo-mode* ("Sign in to view") from *genuinely empty* ("Nothing here yet" + create-first action). A faint one-color aurora/line-art motif behind the empty composer — tasteful, no illustration library.

**(h) Typography & numerals.** Hero numerals `--text-4xl/5xl`, weight 800, `--tracking-tight`. **`tabular-nums` on every metric/column/axis/count-up** (add a `.tnum` utility). Mono (`--font-mono`) for IDs/timestamps/raw metric values so they read as *data*. Eyebrow labels uppercase `--text-2xs` + `--tracking-wide`; three weights max per view.

**(i) RTL-premium.** Arabic is *primary*, not a flipped afterthought — the credibility test. **Logical properties everywhere** (`margin-inline`, `inset-inline`, `border-inline-start`); enforce via lint. **Charts mirror intentionally** — time axes flow right-to-left (newest at the inline-start), bar/funnel growth flips, now-line + overdue zones mirror — *but* numerals, ID codes, and Latin framework names (ISO 27001, NCA ECC) stay LTR-isolated (`unicode-bidi: isolate`) so they don't garble. Directional icons (arrows/chevrons/trend) flip; brand/status icons (shield, bot) do not. **Mirror the board pack fully** — the premium bilingual signal.
- **Do:** flip the time axis + arrow icons in RTL. **Don't:** flip the posture numeral, ISO codes, or neutral logos.

---

## Part 4 — Reusable primitive catalog (build once, theme per page)

The committee independently converged on the same small kit. Build these and every hero above is assembly, not re-invention. All consume `--risk`/`--cat`/semantic tokens so the legend grammar stays uniform.

| Primitive | Powers | Notes |
|-----------|--------|-------|
| **`<RiskMatrix5x5 vectors residual onCellClick/>`** | ERM dash, register heatmap, risk-detail locator, new-risk preview | optional inherent→residual arrows |
| **`<RadialGauge value bands needle/>`** | exec posture, appetite, BCM rings, KRI/MTTD/MTTR, coverage | also concentric "activity rings" |
| **`<BandedTrend series thresholds breaches/>`** | KRI, AI drift, SLA lines | extend `TimeSeries` w/ `bands`/`markers` |
| **`<CountdownDial deadline live seal/>` / `<SlaRing>`** | DSR, breach 72h, regulator countdowns, crisis wall | green→amber→red + relief seal |
| **`<ForceGraph nodes edges onExpand/>`** | attack-surface, 4th-party, ROPA, sub-processor chain | one sim + spring settle + RTL mirror |
| **`<CrosswalkSankey/>`** | control crosswalk, data lineage, alert funnel | Recharts `Sankey` / hand-SVG hero traces |
| **`<MiniGantt/CadenceTimeline/>`** | audit eng., IA plan, AI lifecycle, report schedules, outsourcing | swimlanes + today-line |
| **`<FreshnessCalendar/>`** | evidence freshness, recert load | GitHub-style heat |
| **`<BubbleQuadrant/>`** | TPRM, outsourcing materiality, IA universe, BIA | Recharts `Scatter` |
| **`<ResidencyMap zones flows/>`** | data flows, PDPL, NDI | KSA concentric zones + Art. 7 arcs |
| **`<Treemap ramp/>`** | classification, vendor/4th-party concentration | Recharts `Treemap` |
| **`<RadarChart/>`** | framework coverage, control maturity, AIA | Recharts `Radar` |
| **`<ControlChart mean sigma/>`** | incident MTTR, SOC response | Recharts + `ReferenceLine` |
| **`<LifecycleFunnel/>` + `<WorkflowStepper/>`** | AI register, DSR/breach status, campaigns, every detail page | also fixes the review's "lifecycle = one badge" gap |
| **`<DisparityBars/>`** | AI fairness, DPIA before/after | four-fifths reference line |
| **Motion kit:** `useCountUp()`, `useStaggerEntrance()`, draw-on hook, `<Skeleton/Shimmer>` | platform-wide | all reduced-motion gated |

**Token additions to propose:** `--shadow-hover`, `--pulse`/`--shimmer` keyframes, `.tnum` + `.card--interactive` utilities, the `--soc-*` dark war-room set.

---

## Part 5 — Benchmark scorecard (where we match / beat the leaders)

| Leader | Their signature | Our move |
|--------|-----------------|----------|
| **RSA Archer / MetricStream** | static 5×5 residual heatmap | **animated inherent→residual vector field** |
| **ServiceNow IRM** | exec KPI strip, MTTR number | posture arc-as-number; **MTTR as SPC control chart** |
| **LogicGate** | clean static heatmap | shared animated cell + what-if appetite |
| **OneTrust** | DSR due-date pills, separate breach tab | **unified DSR/breach clocks**; depleting SLA rings; SDAIA 72h war-room |
| **Securiti.ai** | world-map flows | **KSA-centric PDPL trust-zone map w/ Art. 7-gated arcs** |
| **BigID** | sensitivity discovery heatmap | classification treemap linked to downstream transfer risk |
| **AuditBoard** | framework heatmap (row-level), Gantt | **cell-atomic drill**; drag-reschedule Gantt + coupled coverage |
| **Hyperproof** | health rings, freshness list | live rings; **freshness calendar that shows the cold streak** |
| **Workiva** | obligation lists w/ dates | **burndown vs regulator deadlines** |
| **Drata / Vanta** | control-coverage spider | **assessed-vs-compliant crescent radar** |
| **ServiceNow SecOps / Splunk** | MTTR widgets | **SPC + live heartbeat war-room** |
| **BitSight / SecurityScorecard** | per-vendor grade | **portfolio grade ridgeline** |
| **ProcessUnity / Prevalent** | risk-vs-criticality matrix, Nth-party | **freshness-encoded bubbles; 4th-party concentration halo** |
| **Credo AI / watsonx / Holistic AI** | model card / drift / bias (separate) | **all three on one model page + live gate** |
| **Diligent** | static board pack | **live-data scrollytelling pack, pixel-faithful RTL export** |
| **SAMA materiality / NDMO national-data** | *(no native commercial tool)* | **category-defining** Saudi-native heroes |

---

## Part 6 — Phased build roadmap

> Build the **shared primitives first** (Part 4) — most heroes are then assembly. Sequence by visual ROI ÷ effort.

**Phase A — Foundations + the three platform-defining heroes**
1. Motion kit + `Skeleton/Shimmer` + `.tnum`/`.card--interactive` + `--shadow-hover` (everything else rides on these).
2. **`RiskMatrix5x5` with inherent→residual arrows** → ERM dash + register + risk detail (the platform's signature glyph).
3. **`RadialGauge`/posture arc + count-up** → Executive hero (the daily first-impression).
4. **Control crosswalk Sankey** → Control detail ("test once, satisfy many" — the category statement).

**Phase B — Regulatory clocks & monitoring (the "alive" layer)**
5. `SlaRing`/`CountdownDial` → DSR aging, **Breach 72h SDAIA war-room**, BCM/incident regulator countdowns.
6. `BandedTrend` + `ControlChart` → KRI bands, **AI drift bands**, **incident MTTR SPC**.
7. Dark SOC war-room surface + live heartbeat → SOC Ops.

**Phase C — Graphs, maps & matrices (the differentiators)**
8. `ForceGraph` → attack-surface blast-radius + **4th-party concentration** + ROPA.
9. `ResidencyMap` (KSA trust zones + Art. 7 arcs) → Data flows + PDPL + NDI.
10. `BubbleQuadrant` → TPRM risk-vs-criticality + **SAMA outsourcing materiality** + IA universe.
11. `FreshnessCalendar` → Evidence; `MiniGantt`/`CadenceTimeline` → Audit + AI lifecycle + report schedules.

**Phase D — AI governance depth, board pack & polish**
12. AI model-card + gate checklist + `DisparityBars` + AIA `Radar`.
13. Board-pack scrollytelling preview + RTL export.
14. GRC Expert cited-answer canvas.
15. Empty-state art pass + final RTL-premium sweep.

---

### Closing note
The platform already has the rare hard part: a coherent token system, hand-rolled SVG flexibility, real data behind most surfaces, and an Arabic-primary backbone. This playbook is the **craft layer** — a named hero per page, two doctrines (live deadline clocks; visually-encoded gates), a shared primitive kit, and a restrained modern-design language — that takes it from "good, consistent GRC tool" to a **regulator-facing product that out-visualizes Archer, OneTrust, AuditBoard, ServiceNow, and Diligent on their own signature views**, while owning the Saudi-native ones (SAMA materiality, SDAIA 72h, NDMO residency) that no incumbent has.

*Prepared by a 5-member committee of GRC-tool design leaders. Front-end design & visualization only; no backend change implied. Companion to `frontend-design-review.md`.*
