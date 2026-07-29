# UX Redesign Blueprint

**Date:** 2026-07-25 · **Evidence:** `Appendix/MEASURED_BASELINE.md` §5–§5.4 ·
**Complements** `UX/UX_ARCHITECTURE.md`, `UX/DESIGN_SYSTEM_SPECIFICATION.md`,
`UX/ARABIC_AND_SAUDI_UX_STANDARD.md`, `UX/PERSONA_JOURNEYS.md` — **corrects them where measurement disagrees.**

---

## 1. The finding that changes the brief

The review brief asks for "a complete UX redesign". **Measurement does not support that, and this
document declines to recommend it.**

| What was measured | Result | Implication |
|---|---|---|
| Pages importing shared components | **327 of 329 (99%)** | A design system exists **and is genuinely adopted** |
| Component library | **144 files** (`ui/` 56, `viz/` 14, `layout/` 11 …) | Primitives exist: `Badge` 314×, `Shell` 296×, `Card` 293×, `PageHeader` 273×, `Button` 244×, `Kpi` 170×, `DataTable` 73× |
| Design tokens | `styles/tokens.css` 15 KB, `components.css` 87 KB | Themeable centrally |
| Arabic dictionary parity | `en.ts` **5,677 keys** / `ar.ts` **5,677 keys** | Arabic is a **first-class locale**, not a stub |
| RTL engineering | `<html dir>`, deliberate `dir="ltr"` islands for hex/numeric tokens, `[dir="rtl"]` table CSS, chart-axis reversal | Evidence of real RTL craft, not a stylesheet flip |

**A redesign would throw away the platform's strongest frontend asset.** 99% component adoption means
visual change is a **token-and-primitive** exercise: restyle `Card`, `Badge`, `Button`, `PageHeader`,
`DataTable`, `Kpi` and 329 screens change together.

**The real UX crisis is structural, not visual:**

| Defect | Evidence | Severity |
|---|---|---|
| **Navigation cannot reach the product** | 328 routes vs **184** sidebar entries → **~144 screens URL-only** | **P1** |
| **Taxonomy has drifted** | **114** page folders for 58 modules; `pages/dashboard` *and* `pages/dashboards`; `pages/detail` holds 10 orphan screens | **P1** |
| **The first three surfaces lie** | Global search, executive dashboard, board pack are mock-fed | **P0** |
| **Bilingualism is split** | `t()` in 163 files vs inline `lang === 'ar'` in **431**; **439** hardcoded English strings; **49 pages (15%) contain no Arabic** | **P1** |
| **Two unreviewable monoliths** | `EsgPage.tsx` **5,583** lines, `NdiPage.tsx` **4,970** — 7.7% of page code | **P2** |

> **Redesign the information architecture. Keep the design system. Fix the truthfulness.**

---

## 2. Information architecture — the redesign

### 2.1 Diagnosis

A 184-item sidebar is past the point of navigability, and it still exposes only **56%** of routed
screens. Competitors converged on 8–15 top-level surfaces: ServiceNow (workspaces + unified search),
Archer (solution areas), OneTrust (module-scoped IA), AuditBoard (a handful of named products). None
exposes a flat menu of this length — not because they have less capability, but because **menu length is
not how large products are navigated.**

### 2.2 Target: 12 workspaces

Derived from the SKU catalogue (`Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md` §6), so navigation,
licensing and the datasheet share one taxonomy:

| # | Workspace | Modules behind it |
|---:|---|---|
| 1 | **My Work** | Cross-module tasks, approvals, attestations, my requests |
| 2 | **Risk** | `risk`, `risk_lc`, `ccm` |
| 3 | **Compliance & Policy** | `compliance`, `policy`, `controlmap`, `regchange`, `nca_ecc` |
| 4 | **Internal Audit** | `audit_lc`, `audit_eng`, `audit_committee` |
| 5 | **Cyber & SOC** | `cyber`, `cyber_lc`, `socops`, `incident` |
| 6 | **Resilience** | `bcm`, `bcm_lc`, `opres` |
| 7 | **Privacy** | `privacy`, `privacy_lc`, `data_officer`, `dataclass` |
| 8 | **Third Party & Supply Chain** | `tprm`, `scm`, `outsourcing`, `cpty` |
| 9 | **Sustainability** | `sust` |
| 10 | **Ethics & Conduct** | `ethics`, `consumer` |
| 11 | **Financial-Sector Regulatory** | `openbanking`, `rrp`, `regreporting`, `reginspect`, `sharia`, `aml`, `fraud` |
| 12 | **Governance & Administration** | committees, `governance`, `board_pack`, `admin`, `iam`, `ai` |
| 13 | **Change Enablement** | `change_mgmt` (CAB, change calendar / freeze windows, decision register) |

⚠ **Row 13 is not cosmetic.** `change_mgmt` sat inside row 12 while the owner's direction is that Change
is not under Risk and not an admin function. Because navigation here is **generated from the module
registry**, leaving it in row 12 would have made Wave 4 silently delete CE-0's sidebar group and re-file
Change under an administrative catch-all — a documented plan reversing a shipped decision, with no code
change to notice. Amended in the same commit as CE-0 (2026-07-29), which is the only reason the two agree.
Row 13 lists the CAB and decision register as *contents*, not as shipped screens: they land with S7/CE-13,
and the sidebar deliberately does not link them until they resolve.

**Rules.** Each workspace has one landing page (KPIs → registers → my items → what needs me).
Navigation is **generated** from the module registry filtered by entitlement (F2-01, F2-04), so an
unlicensed module cannot appear and a route with no registry entry cannot exist. Depth comes from
in-workspace tabs and detail routes, never from sidebar length. Sidebar shows only workspaces the user
has permission *and* entitlement for — for a three-module customer that is 3–4 items, not 184.

### 2.3 Sequence — reconcile before redesigning

**Step 1 (2–3 days, do first).** Generate `UX/ROUTE_RECONCILIATION.md`: every one of the 328 routes ×
{sidebar entry? · module owner? · real API or mock? · last-touched}. Classify all ~144 URL-only screens
as **promote / merge / retire**. This is the highest information value per hour available in the UX
programme: it converts "we don't know what half our app is" into a decision list, and may retire dozens
of screens — shrinking the surface the redesign has to cover.

**Step 2.** Add the CI test: every route has a nav path or an explicit `detailOnly: true`. Prevents
recurrence permanently.

**Step 3.** Build the 12 workspace landing pages. Existing pages move under them unchanged.

**Step 4.** Retire the flat `Sidebar.tsx` route list in favour of registry-driven navigation.

---

## 3. Arabic & Saudi UX standard

### 3.1 Keep

- Per-locale lazy dictionary loading (`PERF-FE-002`) — correct and performance-aware.
- 5,677-key parity between `en` and `ar`.
- `dir="ltr"` islands for LTR-by-nature tokens (hex colours, numerals with units, IBANs, IPs) — a subtle
  detail most teams miss. Document it as a rule; it currently exists as scattered good instinct.
- RTL-native exports (shaping, mirroring, bundled Amiri font across 22 generators) — **a genuine moat**.

### 3.2 Fix

| Rule | Current | Target | Enforcement |
|---|---|---|---|
| **One translation mechanism** | `t()` in 163 files; inline `lang === 'ar'` in **431** | `t()` everywhere in `pages/**`; inline branch allowed only inside `components/` where layout genuinely differs | ESLint rule as a **ratchet** |
| **No hardcoded user-visible strings** | **271** JSX text nodes + **168** `label`/`title`/`placeholder` | 0 | ESLint: no bare Latin literal in JSX text or those props |
| **Every page reachable in Arabic** | **49 pages (15%)** contain no Arabic | 0 | Arabic-coverage CI report per module |
| **Bilingual data** | Backend already mandates `*_en` + `*_ar` | Client-side gate before submit so Arabic-mandatory fields fail in the form, not in a 400 | Form-level validation |

### 3.3 Saudi-specific UX rules (currently informal — make them explicit)

| Rule | Why |
|---|---|
| **Hijri + Gregorian dates together**, Gregorian authoritative for audit timestamps | Saudi business practice; regulator submissions cite both |
| **Arabic-Indic vs Western numerals** — one consistent choice per locale, and **never** in identifiers, amounts or evidence hashes | Mixed numerals in a financial figure is a data-integrity smell to an auditor |
| **Names**: full Arabic name field alongside transliteration; never derive one from the other | PDPL identity accuracy; also why pronoun/gender must never be inferred from a name |
| **Regulator terminology** must match the Arabic used in the source regulation (NCA/SAMA/SDAIA/DGA wording), not a generic translation | An acceptance committee reads its own regulation's terms |
| **Government acceptance artefacts** (exports, minutes, board packs) must be Arabic-primary with correct RTL and e-signature detail | These leave the building |

---

## 4. Truthful UI — a design principle, not a bug fix

**Principle.** *The interface must never present data it did not receive from the system of record.*
Three states, always distinguishable: **has data** · **no data yet** (empty state + the action that
creates data) · **cannot load** (error + retry + what failed).

The design system already has `EmptyState` (109×) and `InlineEmpty` (133×) — the components exist and
the three critical surfaces simply do not use them.

**Enforcement:**

1. CI: no import matching `/mock/i` outside `**/*.test.*`.
2. **Backend-down acceptance test** — with the API stopped, every register shows an error state and
   **zero rows**. This is the test that makes the principle real; it is currently absent.
3. Loading skeletons must never render plausible placeholder *content* — shapes only.
4. Any figure on an executive surface must be traceable to an endpoint; a dashboard tile with no
   endpoint does not ship.

Priority order (blast radius, not count): `lib/searchIndex.ts` (7 mock imports — global search),
`pages/dashboard/widgets.tsx` (4 — executive dashboard), `lib/boardPack.ts` (3 — board pack). These are
the three surfaces an executive or acceptance committee opens first.

---

## 5. Persona journeys — the five that decide deals

`UX/PERSONA_JOURNEYS.md` (102 lines) covers personas thinly. What follows is the **decision-critical
path** for each buyer persona, with the measured gap on that path.

### 5.1 CEO / Board member — *"is the organisation safe?"*
Login → **Executive workspace** → top risks, control effectiveness, open regulatory obligations,
incidents → drill into one risk → see owner, treatment, evidence → export board pack (Arabic, RTL).
**Gap:** the dashboard and board pack are mock-fed (F2-02) — the single most damaging defect on the most
senior journey. **Target:** every tile traceable to an endpoint; board pack assembled only from
persisted records with a generation timestamp and a data-completeness note.

### 5.2 Risk Manager — *"is my register current and defensible?"*
My Work → overdue assessments → open risk → update assessment with control effectiveness → residual
recalculates → attach evidence → route for approval → KRI breach alerts.
**Gap:** `risk` is well tested (147 tests) — this journey is the platform's strongest. **Target:**
make it the reference demo, and the template for the other eleven workspaces.

### 5.3 Internal Auditor — *"can I test a control and evidence my opinion?"*
Audit workspace → engagement → programme → test a control → sample → finding → CAPA → follow-up →
audit-committee reporting.
**Gap:** `audit_lc` has 58 tests, but `audit_eng` (0 tests) and `audit_committee` (0 tests, 8 peer
dependencies) sit on the same journey. **Target:** Tier-1 test floor across all three — this is
AuditBoard's home category and the comparison will be made explicitly.

### 5.4 Compliance Officer — *"which obligations am I failing, and what do I do today?"*
Compliance workspace → framework (NCA ECC / SAMA CSF / PDPL) → control coverage heat map → gaps →
assessment campaign → attestations → remediation → regulator submission.
**Gap:** `nca_ecc` has **0 tests** and `controlmap` writes into the `evidence` schema. The most-cited
Saudi framework sits on the least-assured code.

### 5.5 DPO / Data Officer (PDPL) — *"can I answer a DSR in time and prove it?"*
Privacy workspace → RoPA → DPIA → **DSR intake → identity verification → fulfilment within statutory
period** → breach assessment → 72-hour notification.
**Gap:** `data_officer` — **2,373 LoC, 37 endpoints, 1 entity, 0 tests**. A statutory-deadline workflow
with no automated proof it works. **Target:** highest-priority Tier-1 floor after `outsourcing`; a
missed statutory deadline is a regulatory event for the customer, caused by the platform.

### 5.6 Cross-persona: the acceptance committee
A government acceptance committee does not follow a persona journey — it **samples**. It picks a
control, asks to see it enforced, asks for the evidence, and asks who approved it. The platform answers
this well where the committee engine is involved (quorum, recusal, e-signed minutes, audit ledger) and
answers it badly where a control is *configured* rather than *coded* (F2-14: `committee_gate_map` was
empty in every tenant while the matrix demanded it). **Target:** the Control Health surface (F2-14) —
turn "which of our controls are actually in force?" into a screen. No competitor ships this.

---

## 6. Forms, wizards, tables

Measured: `FormModal` 55×, `DataTable` 73×, `AttachmentsField` 42× — real reuse, and the wizard
inventory (`DsrFulfilmentWizard`, `ControlTestWizard`, `VulnerabilityTriageWizard`, `BcmExerciseWizard`,
`SupplierOnboardingWizard`) shows the pattern is established. These same wizards are the **worst
offenders for hardcoded English** (15, 14, 13, 11, 9 strings respectively) — high-value screens with
the weakest localisation.

| Standard | Rule |
|---|---|
| **Bilingual pairs** | Every `*_en` field renders beside its `*_ar`; Arabic-mandatory fields blocked client-side before submit |
| **Error surfacing** | Keep `problemMessage()` folding RFC-7807 `errors` into field-named toasts — good practice already in place |
| **Destructive actions** | Named confirmation (type the record name), never a bare `confirm()` |
| **Four-eyes** | Where the server enforces a 409 SoD gate, the UI must **say so before submission** — "you cannot approve your own request" beats a 409 after the fact |
| **Tables** | Server-side pagination/sort/filter for every register (untested at volume — F2-17); sticky headers; column choice persisted per user |
| **Wizards** | Resumable, with saved drafts; no wizard longer than 5 steps without a progress save |
| **Page size** | No new page over 800 lines; existing offenders may only shrink (ratchet) |

---

## 7. Accessibility — measured, with one serious open violation

**Corrected.** An earlier draft of this section stated that no accessibility run had been performed.
That was wrong: the tracked `audit/` evidence vault (2026-06-18 @ `3ca91bf`) contains a real
accessibility assessment using **Playwright 1.49 + `@axe-core/playwright`** and **Lighthouse**.

| Measure | Result |
|---|---:|
| Lighthouse `/login` — **accessibility** | **100** |
| axe-core violations on `/login` | **0** |
| Pages scanned by axe | **10** |
| Authenticated-page violation | **`color-contrast` — WCAG 1.4.3, severity *serious*** |
| Instances | **68** |
| Lighthouse `/login` — best-practices · SEO | 77 · 82 |
| Lighthouse `/login` — performance | 38 ⚠ *measured against the Vite **dev** server; not representative of a production build — treat as unmeasured* |

**So the real position is better than "unmeasured" and worse than "conformant".** The login surface is
clean and scores 100. The authenticated application carries **68 instances of a serious colour-contrast
failure** — the single most common WCAG failure in enterprise dashboards, and the one that most affects
users reading dense risk and compliance tables.

**Why this is cheap to fix, and why that matters.** Contrast is a **token** problem, and this platform
has central tokens (`styles/tokens.css`, 15 KB) with 99% component adoption (§1). 68 reported instances
almost certainly trace to a small number of token pairs — muted text on tinted card backgrounds, badge
foregrounds, disabled states. Fixing the tokens fixes every instance at once. This is the highest
accessibility return per hour available anywhere in the frontend.

**Recommendation.** (1) Extract the 68 instances from `audit/findings/dyn-a11y.json`, group by
token pair, and fix at the token level — not per component. (2) Re-run the existing axe harness to
confirm zero. (3) Wire `@axe-core/playwright` into CI over the 12 workspace landing pages plus the top
20 registers, as a **ratchet** (violations may only fall). (4) Extend beyond contrast to focus
management, keyboard reachability and screen-reader labelling — none of which the current 10-page sweep
reports on, so they remain genuinely unmeasured. (5) Then issue a WCAG 2.1 AA conformance statement:
DGA digital-accessibility expectations make it a procurement question for Saudi government buyers, and
the evidence to support one is now within reach.

**Honest RFP answer today:** "axe-core and Lighthouse are in our audit pipeline; the login surface passes
with an accessibility score of 100 and zero violations; one serious colour-contrast issue is open across
authenticated pages and is being remediated at the design-token level. A formal AA conformance statement
is not yet issued."

---

## 8. UX work plan

| Wave | Work | Effort | Value |
|---|---|---|---|
| **Now** | Route reconciliation matrix · route↔nav CI test · de-mock the 3 executive surfaces · backend-down acceptance test | S (~1 wk) | Converts unknowns into decisions; removes the trust risk |
| **1** | i18n lint ratchets · migrate 439 hardcoded strings · Arabic coverage report · fix the 49 zero-Arabic pages | S–M | Makes the primary differentiator measurable |
| **2** | 12 workspace landing pages · registry+entitlement-driven navigation · retire the flat sidebar | M–L | The IA redesign |
| **3** | Split `EsgPage` (5,583) then `NdiPage` (4,970, after its characterisation tests) · page-size ratchet · remaining 26 mock sites | M | Velocity, deep-linking, hygiene |
| **4** | `axe-core` AA pass · Control Health surface · server-side pagination at volume | M | Procurement answers |

**What is deliberately not in this plan:** a visual redesign, a component-library replacement, and a
CSS-framework migration. The evidence says these would consume the budget the IA and truthfulness work
needs, and would discard a 99%-adopted design system that is doing its job.
