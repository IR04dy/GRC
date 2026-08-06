# 05 — Screen and UI Requirements
> **HISTORIC — point-in-time snapshot (all files 2026-05-11). Not a statement of current state.**
> ⚠ **This is PACK A of two generations, and neither supersedes the other** — finding **F2-135**. PACK B (`docs/Saudi-GRC-Platform-Integration/`, 2026-05-18) covers 16 shared sections more deeply, but **dropped sections 16–18 of this pack entirely** (AI features, frontend requirements, Excel workbook — 1,010 lines found only here) and reused the number `16` for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Page-level requirements for every UI surface. Every page lives under
`tss-grc-ui-v2/src/pages/{slug}/` (React + Vite + bilingual `Shell`
component with RTL/LTR auto-switch).

**Shared conventions:**

- All pages use `Shell` → `PageHeader` → KPI grid → `Tabs` → primary `Card`
  with table.
- Empty state via `<EmptyState>` with `Icon`, `title`, `sub`.
- Filters via `<FilterModal>`; saved views via `<SavedViewSelector>`.
- Create modals via `<FormModal>` with `FieldDef[]` schema.
- Validation: required fields enforced by `FormModal` + server-side bean
  validation; toast on `ApiError`.
- Bilingual: every label sourced from `translations/en.ts` + `ar.ts`;
  every record shows the `_ar` field when `lang === 'ar'`.
- RTL: when `lang === 'ar'`, root sets `dir="rtl"`; layout uses logical
  CSS properties (`marginInlineStart` etc.) throughout.

---

## Executive Dashboard
- **EN/AR**: Executive Dashboard | لوحة التحكم التنفيذية
- **Path**: `/dashboard`
- **Purpose**: at-a-glance view of platform state with widget customisation.
- **Sections**: page-header + customise toolbar + add-widget chip strip +
  widget grid.
- **Widgets**: Saudi posture, open intakes, overdue reassessments, CAB
  queue, critical risks, **TPRM snapshot** (new), heatmap, risks by
  severity, frameworks, activity feed.
- **Buttons**: Refresh, Customise, Reset layout, Board Pack PDF/PPTX,
  ExportMenu (PDF/XLSX).
- **Roles**: every authenticated user (filtered view based on role).
- **Validation**: none; refresh & customise are idempotent.
- **Empty state**: per-widget — falls back to demo data if not
  authenticated.
- **RTL**: full RTL flip; PDF export uses `RtlFontConfig` for Arabic.

## Regulatory Library
- **EN/AR**: Regulatory Library | المكتبة التنظيمية
- **Path**: `/compliance` (tab `frameworks`)
- **Purpose**: browse all activated frameworks + their requirement + control
  mapping.
- **Tabs**: Frameworks, Requirements, Crosswalk, Scorecard, Continuous
  Monitoring (CCM).
- **Fields**: framework name, regulator, version, control count,
  applicability, effective range.

## Risk Register
- **EN/AR**: Risk Register | سجل المخاطر
- **Path**: `/risk`
- **Purpose**: enterprise risk register with filtering, heatmap drill, KRI
  breach feed.
- **Tabs**: All, Critical, High, Medium, Low, Closed, Heatmap.
- **Buttons**: New Risk, Bulk Actions, Export.
- **Fields**: code, title, category, inherent, residual, owner, status,
  appetite-band, last reviewed.
- **Drill-in**: row → `/risk/:id` → assessment, treatment, KRIs, evidence.

## Risk Assessment Form
- **EN/AR**: Risk Assessment | تقييم المخاطر
- **Path**: `/risk/:id` (Assessment tab) or `/risk?new=1`
- **Fields**: likelihood (1-5), impact (1-5), inherent score (auto),
  residual score (after controls), treatment, owner, target date.
- **Validation**: likelihood + impact required; numeric range.

## Risk Treatment Plan
- **EN/AR**: Treatment Plan | خطة المعالجة
- **Path**: `/risk/:id` (Treatment tab)
- **Workflow actions**: Submit treatment, Approve, Implement, Verify.

## Risk Appetite Dashboard
- **EN/AR**: Risk Appetite | شهية المخاطر
- **Path**: `/risk` (Appetite tab) + `AppetiteBreachFeed` component on
  detail pages.

## BIA Register
- **EN/AR**: BIA Register | سجل تحليل أثر الأعمال
- **Path**: `/bcm` (BIA tab)
- **Fields**: service, MTPD, RTO, RPO, MBCO, last reviewed, status.

## BIA Assessment Form
- **EN/AR**: BIA Assessment | نموذج تحليل أثر الأعمال
- **Sections**: Service scoping → impact dimensions → dependencies → RTO/RPO
  set.

## Critical Services Register
- **EN/AR**: Critical Services | الخدمات الحرجة
- **Path**: `/bcm` (Services tab).

## BCP Builder
- **EN/AR**: BCP Builder | بناء خطة الاستمرارية
- **Path**: `/bcm` (BCP tab) + `/bcm-lifecycle` lifecycle pipeline.

## DRP Builder
- **EN/AR**: DR Plans | خطط التعافي من الكوارث
- **Path**: `/dr-plans` (V41).
- **Fields**: system, site type, RTO/RPO, failover steps JSON, failback
  steps JSON, last tested, next due, outcome.
- **Actions**: New, Approve, Reject, Activate, Archive, Record Test.

## Crisis Room Dashboard
- **EN/AR**: Crisis Room | غرفة الأزمات
- **Path**: `/crisis-management`.

## Supplier Register
- **EN/AR**: Supplier Register | سجل الموردين
- **Path**: `/scm` (Vendors tab) + `/tprm`.

## Supplier Risk Assessment
- **EN/AR**: Vendor Risk Assessment | تقييم مخاطر المورد
- **Path**: `/tprm` (Reassessment + Onboarding tabs).

## Dependency Map
- **EN/AR**: Dependency Map | خريطة التبعيات
- **Path**: `/bcm-dependency`.

## Supply Chain Map
- **EN/AR**: Supply Chain Map | خريطة سلسلة الإمداد
- **Path**: `/scm` + `/fourth-party` for sub-tier.

## NCA Compliance Dashboard
- **EN/AR**: NCA Compliance | الالتزام بـ NCA
- **Path**: `/cyber` (NCA tab) + `/nca-ecc-export`.

## NCA Control Assessment
- **EN/AR**: NCA Controls | ضوابط NCA
- **Path**: `/framework-controls` (filter by NCA framework).
- **Tabs**: All, ECC, CSCC, CCC, OTCC, SAMA, ISO 27001, ISO 22301, PCI DSS.

## Asset Register
- **EN/AR**: Asset Register | سجل الأصول
- **Path**: `/assets`.

## Critical Systems Register
- Subset of Asset Register filtered to `classification = CRITICAL`.

## Vulnerability Register
- **EN/AR**: Vulnerabilities | الثغرات
- **Path**: `/cyber/vulns/v2`.

## Cyber Incident Register
- **EN/AR**: Cyber Incidents | الحوادث السيبرانية
- **Path**: `/incidents` + `/cyber` (Incidents tab).

## PDPL Dashboard
- **EN/AR**: PDPL Dashboard | لوحة PDPL
- **Path**: `/privacy`.

## Processing Activity Register (ROPA)
- **Path**: `/ropa`.
- **Detail panel**: per-ROPA PDPL workflow chips (notices / CBT / disposal)
  with live counts.

## PIA Form
- **Path**: `/privacy` (PIA tab) + `/detail/dpia/:id`.

## Data Subject Request Register
- **Path**: `/privacy` (DSR tab) + `/portal/dsr` (public form).

## Consent Register
- **Path**: `/privacy` (Consent tab).

## Cross-Border Transfer Register
- **Path**: `/data-lifecycle` (CBT tab).

## Data Breach Register
- **Path**: `/privacy` (Breach tab).

## Evidence Repository
- **Path**: `/evidence` + `/evidence-chain` (chain-of-custody view).

## Corrective Action Register
- **Path**: `/capa`.

## Board Report Generator
- **Path**: `/board-pack`.
- **Actions**: PDF, PPTX, both bilingual.
- **Signature**: required (Chairman) at finalisation.

## Regulatory Evidence Package Generator
- **Path**: `/nca-ecc-export` + on-demand per-regulator export.

## Administration and Workflow Configuration
- **Paths**: `/admin`, `/workflows`, `/workflow-versions`,
  `/notifications-channels`, `/sso`, `/mfa`, `/white-label`, `/settings`,
  `/onboarding`.

---

## New screens (this doc set)
- `/fourth-party` — Fourth-party exposure register
- `/data-sharing` — PDPL data-sharing agreements
- `/processor-assessments` — PDPL Art. 28 due diligence
- `/third-party-cyber` — Third-party cyber risk assessment
- `/resilience-tests` — Supplier resilience tests
- `/tprm` → enhanced with consolidated TPRM overview component
- `/dashboard` → enhanced with TPRM snapshot widget
