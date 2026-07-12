# Sustainability Module — Dual-Expert Review & Green-Finance Uplift Plan
# مراجعة موديول الاستدامة وخطة التطوير — الصكوك الخضراء والمحفظة الخضراء

**Status:** ✅ **IMPLEMENTED (backend complete, G0–G4) — merged to master 2026-07-11** · **Date:** 2026-07-09 · **Lenses:** Sustainability SME + Saudi GRC SME

---

## §0 Implementation status (2026-07-11, master @ 25006609 merge)

| Phase | Shipped | Commit | Migrations |
|---|---|---|---|
| **G0** Foundation | REG-1..11 obligation register (12 bilingual rows) · esg_metric_reading seed closure (26 defs → readings) | `3702c827` | V285 |
| **G1** Green Sukuk | instruments/projects/UoP ledger · §4.2 math (over-allocation hard block incl. PROPOSED, allocation_ratio, refinancing share) · maker-checker (proposer≠approver 409) · Shariah gate (sukuk needs approval ref) · SUST_APPROVE catalogued | `1f4b76a9` | V286 |
| **G2** Reporting & impact | project_share (capped 1) · attributed impact · intensity · allocation reports (frozen figures, publish needs APPROVE) · Green-Finance evidence ZIP (branded README.pdf AR/EN + 3 XLSX + SHA-256 manifest) | `5344066e` | — |
| **G3** Portfolio & credits | taxonomy_entries (ICMA/CCE 10-row seed) · portfolio_assets w/ tagging SoD · GAR · category mix · weighted-DQ (PCAF rollup) · carbon credits (retire needs certificate + purpose) | `c8a30e13` | V287, V288 |
| **G4** Regulatory horizon | esg_targets + linear trajectory (5% band) → idempotent auto-CAPA · ifrs_s_requirements (14 seeded, coveragePct) · env_permits + expiry sweep → EXPIRED + CRITICAL CAPA · cbam_lines (volume × tCO₂e/t, byGroup) · ck_ca_source widened (ESG_TARGET, ENV_PERMIT) | `58343b97` | V289–V292 |

Every slice live-probed (G4 final: 28/28); suite 2248/0/0; ratchet 62. Migration numbers shifted
V285–V291(plan) → V285–V292(actual); the planned materiality-axis change was already delivered
pre-plan by S-Q (V257). Decisions taken as recommended: ICMA+CCE default taxonomy; Shariah as a
status field; FI + corporate scopes both built.

**Remaining (NOT yet implemented):**
1. **§6 UI — the entire new frontend family**: Green Finance tabs (Instruments/UoP/Impact/Reports),
   Green Portfolio, Targets & Transition, Carbon Credits, IFRS S1/S2 Readiness, Env Compliance, and
   the 7 new ESG-dashboard KPIs. All capabilities are API-complete and live-verified but API-only.
2. **G0 reading-required blocking rule** — a metric referenced by a disclosure/target without
   readings should block approval (seed closure done; the enforcement rule is not).
3. **§5.1 `financed_emissions_record.asset_ref`** per-asset linkage column (portfolio
   financed-emissions rollup currently computes from records without asset joins).
4. **§5.2 climate_overlay** — TCFD scenario-loss % applied per portfolio segment.
5. **§4.2 ESTIMATE flag** on impact figures (actuals-only today).
6. **§5.3 offset policy rule** — retired credits offsetting capped/gated on gross-reduction
   disclosure (open decision #4, proposed 10% cap) — ledger built, policy rule not enforced.
7. **§5.4 dedicated S1/S2 gap export artifact** (summary endpoint exists; not in the ZIP).
8. **§5.5 interim milestones** on targets (annual actuals only).
9. **§5.6 violations record type** → CAPA (permits + expiry surveillance done; SUSPENDED status
   covers part of it).
10. Roadmap (never phased): ISAE 3410 assurance-lineage completeness view; GHG Scope-3
    15-category split; allocation-report packet.xlsx/.docx endpoints (reports ship inside the ZIP).

---
**Scope:** review of the as-built `module/sust` (19 DB tables, verified live) + the uplift required for
production-readiness in the Saudi market, including the regulatory register (التوجيهات والقوانين واللوائح),
**Green Sukuk & their calculations (الصكوك الخضراء وحسابها)**, and the **Green Portfolio (المحفظة الخضراء)**.

---

## ملخص تنفيذي (Arabic executive summary)

موديول الاستدامة الحالي **أنضج مما يُشاع**: محاسبة الانبعاثات (نطاق ١/٢/٣ مع أعلام التحقق)، الانبعاثات
المموَّلة بمنهجية على شكل PCAF (معامل الإسناد + درجة جودة البيانات ١–٥)، مصفوفة الأهمية النسبية، سيناريوهات
TCFD مع الأثر القطاعي، التزامات مبادرة السعودية الخضراء، إفصاحات المواد ٩١–٩٤، استبيانات الموردين، الأدلة
مع مسار الاعتماد، الإجراءات التصحيحية التلقائية، وحِزم تصدير ثنائية اللغة (PDF/Excel/Word). **الفجوات
الجوهرية** لجاهزية السوق السعودي هي: (١) **سجل أدوات التمويل المستدام — الصكوك الخضراء** مع دفتر استخدام
المتحصلات وتقارير التخصيص والأثر وحساباتها؛ (٢) **المحفظة الخضراء** — وسم الأصول وفق تصنيف أخضر قابل
للتهيئة ونسبة الأصول الخضراء (GAR)؛ (٣) **سجل تنظيمي سعودي** يربط المتطلبات (SOCPA/IFRS S1-S2، هيئة
السوق المالية، تداول، المركز الوطني للرقابة على الالتزام البيئي، موان، كفاءة، CBAM الأوروبي) بالضوابط
والأدلة؛ (٤) أرصدة الكربون الطوعية (RVCMC)؛ (٥) سجل المستهدفات وخطة التحوّل (صافي الصفر ٢٠٦٠)؛
(٦) اكتمال قراءات المؤشرات (٢٦ تعريفاً بلا قراءات حالياً) وجاهزية التأكيد الخارجي (ISAE 3410).
الخطة أدناه تصمم كل ذلك على البنية القائمة (سير العمل، الأدلة، التصدير، RBAC) — تنفيذ مرحلي G0→G4.

---

## 1. Review method & as-built inventory (verified 2026-07-09)

Verified against code (`module/sust`: 14 submodules, 20 entities) and the live dev DB (19 `sust.*`
tables, seeded). History: the 18-epic remediation (S-A…S-R, migrations V248–V257) closed the module's
original criticals (RBAC, evidence upload, auto-CAPA, computed scenario loss, ERM link) — this review
therefore focuses on **capability expansion**, not defect repair.

| Capability (as-built) | Evidence | SME verdict |
|---|---|---|
| GHG accounting S1/S2/S3 + verification flags + S3 coverage % | `ghg_emission_record` (40 rows), `emission_factor` | **Good** — GHG-Protocol-aligned basics; S3 is one line, not 15 categories |
| **Financed emissions** (asset class AR/EN, outstanding SAR mn, attribution factor, DQ score 1–5, methodology AR/EN, lifecycle) + targets (baseline/target year, %) | `financed_emissions_record` (8), `financed_emissions_target` (2) | **Very good** — PCAF-shaped; missing portfolio linkage + weighted-DQ rollup |
| Materiality matrix (stakeholder importance × business impact) | `material_topic` (28) | **Good** — upgrade axes to double materiality (impact ↔ financial) |
| TCFD: items (85), scenarios (8) w/ computed loss, sector impacts (20), climate risks/opps (20), climate report PDF | `tcfd_*`, `climate_*` | **Good** — direct runway to IFRS S2 |
| SGI commitments (17) · CMA Arts 91–94 (91 disclosures) · vendor ESG (5) | `sgi_commitment`, `cma_disclosure`, `vendor_esg_survey` | **Good** — Saudi-native anchoring already present |
| Evidence w/ approval workflow · auto-CAPA (28) · annex/report exports (PDF/XLSX/DOCX, AR/EN) · dashboard · settings | `evidence`, `corrective_action`, `sustainability_report` | **Good** — reuse as-is for new objects |
| Generic ESG metrics | `esg_metric` (26 defs) / `esg_metric_reading` (**0 rows**) | **Defect-adjacent**: definitions without readings = un-evidenceable disclosures |
| Green finance (sukuk/bonds/loans), use-of-proceeds, green portfolio/taxonomy, carbon credits, org-wide targets/transition plan, IFRS S1/S2 checklist, CBAM | — | **Absent** (confirmed backend + FE) |

**RBAC/platform posture (GRC lens):** SUST READ/WRITE/APPROVE catalogued and granted
(SUSTAINABILITY_OFFICER manages, GRC_ADMINISTRATOR approves); workflow engine adopted; every export
audit-logged; bilingual/RTL/Hijri machinery available. New objects below inherit all of this.

---

## 2. Saudi regulatory register (التوجيهات والقوانين واللوائح) — to be data, not documentation

Ship as seeded rows in the **compliance module** (obligation → control → evidence), each bilingual,
linked to the sust objects that satisfy it. Verify exact effective dates at implementation time.

| # | Instrument (المرجع) | Issuer | Obligation shape | Platform hook |
|---|---|---|---|---|
| REG-1 | **IFRS S1/S2 as endorsed by SOCPA** (phased application later this decade — confirm current dates) | SOCPA | Sustainability + climate disclosures for in-scope entities | New S1/S2 requirement checklist (§5.4) fed by TCFD/GHG/targets data |
| REG-2 | **CMA Corporate Governance Regulations, Arts 91–94** | CMA | Board-approved policies incl. social/environmental | Already modelled (`cma_disclosure`) — link into register |
| REG-3 | **Saudi Exchange ESG Disclosure Guidelines** (voluntary) | Tadawul | Recommended ESG metrics for listed cos | Map `esg_metric` codes to the guideline set |
| REG-4 | **Saudi Green Initiative / net-zero 2060 + CCE framework** (278 mtpa CO₂e by 2030, 50 % renewable power share 2030) | SGI/state | National targets entities align to | `sgi_commitment` (exists) + new org target register (§5.5) |
| REG-5 | **Environment Law + NCEC** (المركز الوطني للرقابة على الالتزام البيئي) | MEWA/NCEC | Environmental permits, monitoring, violations | New env-permit register w/ expiry + violation→CAPA (§5.6) |
| REG-6 | **MWAN** waste regs (موان) · **SEEC** energy efficiency (كفاءة) · national water strategy | MWAN/SEEC | Operational env metrics & programs | Activate `esg_metric` categories ENERGY/WATER/WASTE with readings |
| REG-7 | **Sovereign Green Financing Framework** (MoF/NDMC) + **ICMA Green Bond Principles / Green Sukuk practice** + **AAOIFI** sukuk standards | MoF · ICMA · AAOIFI | Use-of-proceeds discipline, external review (SPO), annual allocation + impact reporting until full allocation | **Green instrument register (§4)** |
| REG-8 | **CMA sukuk/offering rules** for issuances | CMA | Offering + continuing obligations for listed sukuk | Instrument register fields (listing, prospectus ref) |
| REG-9 | **SAMA climate-risk supervisory direction** (NGFS member; expectations emerging) | SAMA | Climate stress/scenario readiness for FIs | TCFD scenarios (exist) + portfolio overlay (§5.2) |
| REG-10 | **EU CBAM** (definitive regime from 2026) — Saudi exporters of aluminium/steel/cement/fertilisers/hydrogen | EU (extraterritorial) | Embedded-emissions reporting per export line | CBAM exposure screen (§5.7) |
| REG-11 | **RVCMC** voluntary carbon market (سوق الكربون الطوعي الإقليمي) | RVCMC | Credit purchase/retirement integrity | Carbon-credit ledger (§5.3) |

---

## 3. Gap analysis — the two lenses

**Sustainability-expert lens:** no instrument-level green finance; no portfolio taxonomy/GAR; S3 not
category-split (GHG Protocol 15 categories); metrics have no readings (assurance blocker); no org-wide
target/transition trajectory; no carbon-credit ledger (offset claims currently unverifiable); TCFD
present but no IFRS S1/S2 gap checklist; no ISAE 3410-ready assurance pack (evidence exists per record
but no metric→evidence lineage completeness view).

**Saudi-GRC lens:** regulations live in people's heads, not in the obligation register (REG-1…11 above);
no maker-checker on allocation/impact reports (these go to investors/regulators — must be 4-eyes);
Shariah dimension of sukuk absent (structure, Shariah board sign-off — ties to the existing `sharia`
module bootstrap); no SoD between green-tagging (1st line) and tag approval (2nd line); no NCEC permit
expiry surveillance; evidence packs exist but no dedicated **Green-Finance investor/regulator pack**.

---

## 4. NEW — Green Sukuk & sustainable-finance register (الصكوك الخضراء) `sust/finance`

### 4.1 Objects
- **`sust.green_instruments`** — the issuance register: code, type (GREEN_SUKUK / GREEN_BOND /
  SUSTAINABILITY_SUKUK / GREEN_LOAN / SLL), bilingual name, ISIN/listing, issue & maturity dates
  (Gregorian + Hijri display), amount (SAR mn) + currency, framework reference (sovereign GFF / own
  framework), **Shariah structure** (MUDARABA/WAKALA/IJARA/HYBRID) + Shariah-board approval ref,
  external reviewer + SPO rating, ICMA GBP category set, coupon/profit rate, status lifecycle
  (DRAFT → SHARIAH_REVIEW → APPROVED → ISSUED → REPORTING → MATURED), lookback-period months.
- **`sust.green_projects`** — eligible projects: bilingual name, ICMA/CCE category (RENEWABLE_ENERGY,
  ENERGY_EFFICIENCY, CLEAN_TRANSPORT, GREEN_BUILDINGS, WATER, WASTE, CCUS…), total cost, expected +
  actual impact metrics (tCO₂e avoided/yr, MWh renewable/yr, m³ water saved/yr…), evidence links.
- **`sust.instrument_allocations`** — the **use-of-proceeds ledger**: instrument → project, amount,
  date, NEW_FINANCING vs REFINANCING flag, status (maker-checker: PROPOSED → APPROVED).
- **`sust.allocation_reports`** — the annual Allocation + Impact report snapshot (period, totals,
  narrative AR/EN, approver, published flag) — exported via the packet stack.

### 4.2 The calculations (حسابها) — engine rules, all persisted per period
```
net_proceeds            = amount − issuance_costs
allocated(i)            = Σ approved allocations of instrument i
allocation_ratio(i)     = allocated(i) / net_proceeds(i)                    → target 100 % within lookback
unallocated(i)          = net_proceeds(i) − allocated(i)                    → disclosed + temp-investment note
refinancing_share(i)    = Σ REFINANCING allocations / allocated(i)          → GBP disclosure
project_share(i,p)      = allocation(i,p) / total_project_cost(p)           → pro-rata attribution
impact(i, metric)       = Σ_p project_share(i,p) × actual_impact(p, metric) → attributed, no double counting
impact_intensity(i)     = impact(i, tCO2e_avoided) / (allocated(i) in SAR mn)
portfolio_green_impact  = Σ_instruments impact(i)                            (dedup by project)
```
Validation rules: Σ allocations ≤ net proceeds (hard); allocation to a non-eligible category blocked;
impact only counts **approved** allocations with **actual** (not expected) metrics unless flagged
ESTIMATE; every figure traceable to an evidence item (assurance lineage).

### 4.3 Controls (GRC lens)
Maker-checker on every allocation and on report publication (workflow engine, module key SUST);
proposer ≠ approver (SoD rule added to the RBAC governance pack); Shariah sign-off state required
before ISSUED for sukuk types; every state change audit-logged with before/after JSON; reports
exported only via the branded, audit-logged export stack.

---

## 5. NEW — Green Portfolio (المحفظة الخضراء) & remaining capabilities

### 5.1 `sust.portfolio_assets` — green tagging over a configurable taxonomy
Asset/exposure rows (or references to TPRM/asset registers where they exist): bilingual name, sector,
exposure (SAR mn), **taxonomy tag** (versioned `sust.taxonomy_entries` — ships with ICMA + CCE
categories; swappable the day an official Saudi green taxonomy lands), eligibility status
(ELIGIBLE / TRANSITION / INELIGIBLE / UNASSESSED), tag proposer + approver (SoD), evidence link.

### 5.2 Portfolio calculations
```
GAR (green asset ratio)     = Σ ELIGIBLE exposure / Σ total assessed exposure
category_mix                = Σ exposure per taxonomy category
financed_emissions(P)       = Σ attribution_factor × asset emissions      (records exist — link them to assets)
weighted_DQ(P)              = Σ (DQ_score × exposure) / Σ exposure         (PCAF data-quality rollup)
climate_overlay             = existing TCFD scenario loss % applied per portfolio segment
trajectory                  = financed_emissions(P, year) vs financed_emissions_target (exists)
```

### 5.3 Carbon credits (RVCMC) — `sust.carbon_credits`
Purchase/retirement ledger: registry + serial range, vintage, project type/country, quantity (tCO₂e),
price, retirement date + purpose (which SGI/target claim), evidence (registry certificate).
Rule: retired credits may **offset** only after gross-reduction disclosure (no green-washing nets).

### 5.4 IFRS S1/S2 readiness checklist — `sust.ifrs_s_requirements`
Seeded requirement list (S1 governance/strategy/risk/metrics + S2 climate incl. Scope 3 and
industry metrics) with per-requirement status (COVERED-BY → link to TCFD item / GHG record / target /
metric), gap flag, owner. Output: S1/S2 gap report (export pack) — the SOCPA-readiness artefact.

### 5.5 Targets & transition plan — `sust.esg_targets`
Org-wide register (beyond the existing financed-emissions targets): scope (GHG S1/S2/S3, energy,
water, waste, renewables share), baseline year/value, target year/value, SGI-alignment flag,
interim milestones, annual actuals → trajectory chart (on-track/off-track drives auto-CAPA, reusing
the existing CAPA engine).

### 5.6 Environmental compliance ops — `sust.env_permits`
NCEC/MEWA permits: number, facility, issue/expiry (Hijri+Gregorian), conditions, monitoring
obligations, violations → auto-CAPA; expiry surveillance via the existing scheduler pattern
(off-request-safe). MWAN/SEEC/water metrics = activate `esg_metric` ENERGY/WATER/WASTE categories
**with readings** (close the 0-readings defect: readings mandatory for any metric referenced by a
disclosure or target).

### 5.7 CBAM exposure screen — `sust.cbam_lines`
Per exported product line: CN code group (aluminium/steel/cement/fertilisers/hydrogen/electricity),
annual volume, embedded emissions (from GHG records or supplier data), EU importer, reporting period —
produces the data the EU importer will demand; flags carbon-cost exposure.

---

## 6. UI map (extends the ESG page family, bilingual/RTL, Hijri where dated)

```
Sustainability
├─ (existing) Dashboard · GHG · SGI · CMA 91-94 · TCFD · Climate · Materiality · Vendors · Evidence · CAPA · Annex
├─ Green Finance (NEW)
│   ├─ Instruments (سجل الصكوك والأدوات)   — register + lifecycle + Shariah state
│   ├─ Use of Proceeds (استخدام المتحصلات) — allocation ledger + unallocated tracker
│   ├─ Impact (الأثر)                      — per-instrument attributed impact + intensity
│   └─ Reports (تقارير التخصيص والأثر)     — annual packs (PDF/XLSX, AR/EN, maker-checker)
├─ Green Portfolio (NEW) (المحفظة الخضراء) — taxonomy tagging · GAR · financed-emissions rollup · climate overlay
├─ Targets & Transition (NEW)              — trajectory vs SGI/net-zero-2060
├─ Carbon Credits (NEW)                    — RVCMC ledger + retirement claims
├─ IFRS S1/S2 Readiness (NEW)              — requirement checklist + gap export
└─ Env Compliance (NEW)                    — NCEC permits · MWAN/SEEC/water metrics + readings
```
KPIs added to the ESG dashboard: allocation %, GAR, weighted DQ, credits retired, S1/S2 coverage %,
permits expiring ≤90 d, target trajectory status.

---

## 7. Data model & migrations (forward-only; dev via pyodbc; **V285–V291** — V280–V284 reserved by the RBAC plan)

| Mig | Content |
|---|---|
| V285 | `green_instruments`, `green_projects`, `instrument_allocations`, `allocation_reports` |
| V286 | `taxonomy_entries` (+ICMA/CCE seed), `portfolio_assets` (+link `financed_emissions_record.asset_ref`) |
| V287 | `carbon_credits` |
| V288 | `esg_targets`; `material_topic` axis rename to double-materiality semantics (impact/financial) |
| V289 | `ifrs_s_requirements` + seed checklist |
| V290 | `env_permits`; `esg_metric` ENERGY/WATER/WASTE seed + reading-required flag |
| V291 | `cbam_lines`; compliance-module obligation seed for REG-1…REG-11 |

All tables: BaseEntity conventions (org-scoped, `is_deleted`, ROWVERSION, bilingual text columns).
API under `/api/v1/sustainability/finance|portfolio|credits|targets|ifrs|env|cbam` — reads
`SUST READ`, writes `SUST WRITE`, approvals/publication `SUST APPROVE` (ratchet-compliant), every
mutation audited. Exports reuse `packet.xlsx/.docx` + a new **Green-Finance evidence ZIP**
(README.pdf + Allocation.xlsx + Impact.xlsx + Portfolio.xlsx + manifest SHA-256) via the
RegulatorPackageService pattern.

## 8. Phased delivery & verification gates

| Phase | Scope | Verify gate (house standard) |
|---|---|---|
| **G0 Foundation** | REG register seed (V291 part) · metric readings closure (0→seeded, reading-required rule) · materiality axis upgrade | mvn test + live: obligation rows visible in compliance; metric with disclosure but no reading blocks approval |
| **G1 Green Sukuk** | V285 + instruments/UoP/calculations + maker-checker + Shariah state | live: over-allocation blocked; proposer≠approver enforced; allocation_ratio math proven against a seeded instrument |
| **G2 Reporting & impact** | impact attribution + allocation/impact report + Green-Finance ZIP (AR/EN) | ZIP validated (manifest SHA-256, Amiri Arabic PDF); report publish requires APPROVE; export audit row |
| **G3 Portfolio & credits** | V286/V287 + taxonomy/GAR + financed-emissions linkage + credits ledger | GAR + weighted-DQ recompute proven; credit retirement requires certificate evidence |
| **G4 Regulatory horizon** | V288–V291 remainder: targets/transition + IFRS S1/S2 checklist + NCEC permits + CBAM | trajectory→auto-CAPA fires on off-track; permit-expiry sweep (off-request-safe); S1/S2 gap export |

Per-slice: commit on `feat/esg-green-finance-*`; **no merge without explicit "merge"**; test rows
tagged `[REVIEW-TEST]` (CHARINDEX cleanup); destructive DB ops need explicit user OK.

## 9. Open decisions (قرارات مطلوبة)
1. Green taxonomy default: ship ICMA+CCE configurable set (recommended) or wait for an official Saudi taxonomy?
2. Are tenants FIs (portfolio/PCAF/GAR = core) or corporates (CBAM/NCEC = core)? Phases G3/G4 order swaps accordingly.
3. Shariah sign-off: integrate with the existing `sharia` module bootstrap or a status field only (proposed: status field in G1, deep link later)?
4. Carbon-credit offset policy: cap offsets as % of gross target (proposed 10 %) — tenant-configurable?
5. IFRS S1/S2 effective-date tracking: confirm SOCPA's current phase dates at G4 build time.

## 10. مسرد (Glossary)
الصكوك الخضراء = Green Sukuk · استخدام المتحصلات = Use of Proceeds · تقرير التخصيص = Allocation Report ·
تقرير الأثر = Impact Report · المحفظة الخضراء = Green Portfolio · نسبة الأصول الخضراء = Green Asset Ratio (GAR) ·
الانبعاثات المموَّلة = Financed Emissions (PCAF) · الأهمية النسبية المزدوجة = Double Materiality ·
أرصدة الكربون = Carbon Credits (RVCMC) · خطة التحوّل = Transition Plan · الاقتصاد الكربوني الدائري = CCE
