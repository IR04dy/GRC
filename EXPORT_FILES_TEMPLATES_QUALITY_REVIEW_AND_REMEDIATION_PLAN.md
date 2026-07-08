# Exported Files, Templates, Branding & RTL/LTR — Quality Review & Remediation Plan

**Platform:** TSS GRC (bilingual AR-primary / EN, multi-tenant, Saudi/MENA)
**Review date:** 2026-07-07 · **Reviewer role:** GRC product + enterprise-reporting + document-automation + Arabic-RTL + QA
**Method:** repository inspection (3 parallel read-only maps: backend endpoints/generators/libraries, frontend export actions, supporting infra/tests/RTL/naming/governance) **+ live validation against the running :8084 jar** (raw-byte download + file-signature + embedded-font analysis; harness at `scratchpad/rev/ex.py`). This is **review-only** — no implementation performed. `[LIVE]` marks a claim I verified by downloading the actual file today.

---

## 1. Executive summary

The export layer is **not demo-only** and **not a raw-database-dump**. It is a genuine, centralized document-generation engine: a shared OpenPDF theme (`reporting.PdfTheme`/`PdfFonts`) and a column-driven register engine (`RegisterExport`) are reused by ~13 generators across reporting, BCM, cyber, SOC-Ops, NDI, AI, sustainability, committees. Every advertised format is really produced — **`[LIVE]` 26 of 29 export endpoints returned valid, correctly-typed files today** (PDF `%PDF`, XLSX/DOCX/PPTX/ZIP `PK`, CSV/MD/HTML/JSON text), with cover pages, KPI strips, two-pass "Page X of Y" footers, classification banners, SHA-256 content-locks on ESG/annex, and a SHA-256 manifest inside the BCM evidence ZIP.

The system is held back by a **small number of high-impact, cross-cutting defects** — chiefly **Arabic**:

1. **The Arabic font is not bundled** and RTL is cosmetic outside one generator. `[LIVE]` the `?lang=ar` reporting/cyber PDFs embed **host-OS `ArialMT` + `Helvetica`** — Arabic glyphs are borrowed from `C:\Windows\Fonts\arial.ttf` on this dev box, so they render *here* but will degrade to DejaVu/Helvetica boxes on a fontless Linux/container prod host. Only `BoardPackService` sets `RUN_DIRECTION_RTL`; the other 9 PDF generators stay LTR (`?lang=ar` only swaps label strings, not layout).
2. **Excel is never RTL, never filtered, never conditionally-formatted** (repo-wide `setRightToLeft`/`setAutoFilter`/`SheetConditionalFormatting` = 0 hits); four hand-rolled header styles instead of one shared helper.
3. **Export governance is siloed** — no `ExportTemplate`/`BrandTheme`/`ExportJob`/`ExportAuditLog`/`ExportQualityCheck`; the client white-label theme never reaches the server engines (server PDFs are hardcoded teal).
4. **UX is one-click** — only Reports Centre offers format + AR/EN document-language choice; no template picker, no include-charts/evidence toggles, no preview, no cross-module export history.
5. **Discrete correctness gaps** — BCM evidence ZIP (the most sensitive dump) is guarded by `isAuthenticated()` only; CSV formula-injection is guarded in one generator but not NDI/annex CSV; `[LIVE]` sustainability-annex HTML/JSON return near-empty (7 bytes); SOCOps has a backend export endpoint but **no** frontend button; file names lack language/version.

**Verdict (detailed in §36): Partially usable, trending market-ready.** The engine is real and broadly consistent; it needs a focused **RTL/font hardening pass + Excel polish + governance/options/validation layer**, not a rebuild.

---

## 2. Modules reviewed

ERM/Risk · Changes · Compliance/NCA · PDPL/Privacy · SDAIA NDI (نضيء) · Sustainability/ESG/TCFD/IFRS-S2 · SCM · TPRM · Evidence · Policy/Document · CAPA/Corrective-Actions · Incident · Audit & Audit-Committee · Cybersecurity · SOC Operations · Reporting/Board-Pack · Committees · AI/Model Risk · Dashboards · Admin · Workflow · Calendar. (All 17 backend `module/*` packages + `module/sust` + `module/board_pack` inspected.)

---

## 3. Export inventory

### 3.1 Server-generated document exports (real files) — 28 endpoints

| Module | Endpoint | Format | Generator | Guard |
|---|---|---|---|---|
| Reporting | `GET /api/v1/reporting/risk-register.{pdf,xlsx,docx}` | PDF·XLSX·DOCX | `ExportService`/`DocxExportService` | `RISK:READ` |
| Reporting | `GET /api/v1/reporting/findings-aging.{pdf,xlsx,docx}` | PDF·XLSX·DOCX | same | `COMPLIANCE:READ` |
| Reporting | `GET /api/v1/reporting/kri-dashboard.{pdf,xlsx,docx}` | PDF·XLSX·DOCX | same | `RISK:READ` |
| Reporting | `GET /api/v1/reporting/board-pack.pdf` | PDF (RTL-correct) | `BoardPackService` | `REPORTING\|RISK:READ` |
| Reporting | `GET /api/v1/reporting/board-pack.pptx` | PPTX | `BoardPackPptxService` | same |
| Cyber | `GET /api/v1/cyber/export?register=&format=&lang=` | XLSX·PDF | `CyberExportService`→`RegisterExport` | `CYBER:READ` |
| SOC-Ops | `GET /api/v1/socops/events/export?format=` | XLSX·PDF | `SocOpsExportService`→`RegisterExport` | role-gated |
| NDI | `GET /api/ndi/reports/{id}/export` · `/export-adhoc` | PDF·XLSX·CSV | `NdiReportExporter` | `NDI:READ` |
| BCM | `GET /api/v1/bcm/plans/{id}/export.pdf` | PDF | `BcpPlanPdfExporter` | `BCM:READ` |
| BCM | `GET /api/v1/bcm/crisis-events/{id}/export.pdf` | PDF | `CrisisEventPdfExporter` | `BCM:READ` |
| BCM | `POST /api/v1/bcm/plans/{id}/binder` + `GET /api/v1/bcm/binders/{id}/download` | PDF | `BcpBinderPdfBuilder` | role-gated |
| BCM | `GET /api/v1/bcm/cycles/{id}/evidence-package` | **ZIP** | `BcmEvidencePackageService` | ⚠ `isAuthenticated()` |
| AI | `GET /api/v1/ai/models/export?format=&…filters` | XLSX·PDF | `AiRegistryExportService` | role-gated |
| Committees | `GET /api/v1/committees/catalog/forms/{code}/export?format=pdf` | PDF | `CommitteeFormExportService` | `ADMIN:READ` |
| Sust | `GET /api/v1/sustainability/report/{id}/export.{pdf,xlsx}` | PDF·XLSX | `ClimateReportExportService` | `SUST:READ` |
| Sust | `GET /api/sustainability/reports/{id}/export/{md,html,json,csv}` · `.../export.pdf` | MD·HTML·JSON·CSV·PDF | `AnnexExportService` | `SUST:READ` |
| Platform | `GET /api/v1/calendar/events.ics` | ICS | `CalendarService` | `PLATFORM:READ` |
| Attachment | `GET /api/v1/attachments/{id}/download` | stored binary | `AttachmentService` | `EVIDENCE:READ` |

### 3.2 Client-side exports (browser-generated) — ~30 actions

- **Real client files** via `clientExport.ts` (`downloadCsv`/`downloadJson`, UTF-8-BOM CSV, `stampFilename`): CAPA, Cyber (tab CSV), TPRM, SCM, Incidents, Evidence, Privacy, BCM, Workflow (defs+submissions), Admin. Evidence-pack **JSON** (Policy, Compliance-obligation). Calendar **ICS**. CSV-import templates.
- **Browser print-to-PDF** (`packetPrint.ts` + `window.print()`, no file unless user "Save as PDF"): Board-Pack page, NCA-ECC, Audit-Committee dashboard, and the BCM/Cyber/Audit "lifecycle" pages.
- **`.txt` "regulator packets"** from local/seed data: SAMA-OPRES, NCA-Haseen, NCEMA, AC-Packet, ESG-Annex.

### 3.3 Libraries (`tss-grc-backend/pom.xml`)
Apache POI **5.4.0** (XSSF/XWPF/XSLF → xlsx/docx/pptx), OpenPDF **1.3.34** (all PDF), `commons-compress` 1.27.1 (ZIP), `spring-boot-starter-mail` (scheduled delivery). No docx4j/iText/Flying-Saucer/wkhtmltopdf/puppeteer.

---

## 4. Current export quality assessment `[LIVE]`

Downloaded every reachable server export today; 26/29 valid:

| Result | Exports |
|---|---|
| ✅ Valid file, correct signature + reasonable size | risk-register/findings/kri `.pdf`(≈48–51 KB)·`.xlsx`(6–9 KB)·`.docx`(4–5 KB); board-pack `.pdf` EN 62 KB / **AR 72 KB** / `.pptx` 35 KB; cyber incidents+vulns `.xlsx`, cyber `.pdf`; AI `.xlsx`+`.pdf`; ESG report `.pdf`+`.xlsx`; BCM plan `.pdf` 54 KB; BCM evidence **`.zip` 31 KB**; crisis `.pdf` 46 KB; NDI `.xlsx` |
| ⚠ Generated but **near-empty** | sustainability-annex `SR-2027-03` → `.html` **7 B**, `.json` **7 B**, `.md` 190 B (report-state or serialization gap — see §23) |

Positive: real cover pages, KPI strips, "Page X of Y" footers, classification banners, Hijri+Gregorian dates, SHA-256 locks (ESG/annex/binder), evidence-ZIP MANIFEST with per-file SHA-256. Reporting filenames are dated (`risk-register-2026-07-07.pdf`); cyber/AI/ESG filenames are not.

---

## 5. File-format standards (target)

| Format | Purpose | Audience | Mandatory structure |
|---|---|---|---|
| **PDF** | Formal, print/archive, regulator/board | Board, auditor, regulator | Cover · metadata block · (TOC if long) · scope/filters · body · styled tables · charts · findings/gaps/CAPA · evidence refs · approval block · header/footer + page numbers · classification |
| **DOCX** | Editable governance docs | Policy owner, auditor | Cover · document-control table · version history · review/approval table · distribution list · TOC · styled headings/tables · RTL/LTR paragraph styles · header/footer · signature page |
| **XLSX** | Analytical registers | Analyst, auditor | ReadMe/cover sheet · data-dictionary sheet · main data sheet (frozen header, **auto-filter**, styled header, auto width, **severity/status conditional formatting**) · summary/dashboard sheet · **RTL sheet for AR** · locale date/number formats · metadata |
| **CSV** | Machine-readable | Integration, Excel-AR | UTF-8 **+ BOM**, clean headers, RFC-4180 escaping, **formula-injection neutralized** (`=+-@`), language-aware headers |
| **JSON** | API/integration | System | `metadata{}` block (export version, lang, filters, generated-at, org, count) + `data[]`; no unauthorized internal fields |
| **MD** | Lightweight readable | Analyst | Title · metadata · headings · tables · references · AR-direction note |
| **HTML** | Printable/interchange | Any | Branded, print CSS, RTL/LTR, tables, charts, metadata |
| **ZIP (evidence/audit)** | Package | Auditor/regulator | Cover · index · requirement mapping · files · audit-trail · gaps/CAPA · `manifest.json` (checksums) |

---

## 6. Branding & visual identity standard

The house style already exists in `PdfFonts` (`BRAND_PRIMARY` teal `#094242`, `BRAND_ACCENT` gold `#C9A977`, semantic traffic-lights) and `PdfTheme` primitives. **Target:** promote these to theme **tokens** (`brand.primary/secondary/accent/neutral`, `semantic.success/warning/critical/info/muted`) resolved from a per-tenant `BrandTheme` (logo + palette) so the *server* engines honor the same white-label the client already stores (`whiteLabel.ts`). Elements to standardize: platform + org logo placeholders, header/footer, section divider, table-header, status badge, risk-severity color logic, approval box, evidence-reference style. Keep it formal/government-grade — not playful, not raw.

---

## 7. Arabic RTL & English LTR standard

**Required per export:** correct document direction, table direction (first logical column on the right for AR), text alignment, section numbering, bullet indentation, header/footer direction, page-number placement, **Excel sheet direction**, chart labels, locale dates/numbers, and **no disconnected Arabic letters**.

**Current vs target (the core gap):**
| Surface | Current | Target |
|---|---|---|
| PDF run-direction | RTL only in `BoardPackService`; 9 other generators LTR | RTL run-direction + right-align for all when `lang=ar` (shared `PdfTheme` helper) |
| PDF Arabic font | **host-OS Arial** (unbundled) `[LIVE]`; falls to DejaVu/Helvetica on Linux | **bundle** an OSS Arabic TTF (Noto Naskh/Kufi Arabic or Amiri) on the classpath; make it `@Value`-configurable |
| DOCX | ✅ real `w:bidi` + right-align (`DocxExportService.applyBidi`) | keep |
| PPTX | text right-aligned for AR, but **tables hardcode LEFT** | flip table cells too |
| Excel | **no RTL anywhere** | `sheet.setRightToLeft(true)` for AR exports |

---

## 8. PDF template requirements
Cover page · title · org/platform branding · classification · generated date (Hijri+Greg) · generated-by · version · executive summary · TOC (long reports) · scope/filters · body · styled tables · charts · key findings · gaps + corrective actions · evidence references · approval/signature block · appendix · header/footer · page numbers. **Quality gates:** opens; Arabic shaped **and RTL**; tables don't overflow; controlled page breaks; footers on every page; consistent colors; metadata present. Template types: executive, board, audit-evidence, compliance-status, risk-register, BCM-readiness, cyber-compliance, SOC-incident, PDPL-breach, ESG-annual, NDI-submission.

## 9. Word/DOCX template requirements
Cover · document-control table · version history · review/approval table · distribution list · TOC · styled headings · numbered sections · styled tables · AR-RTL + EN-LTR paragraph styles · header/footer · page numbers · appendices · signature page. **Gates:** opens in Word; styles (not manual formatting); TOC field; AR direction; readable tables; AR bullets/numbering; header/footer direction; doc metadata. (Base exists in `DocxExportService`; needs the control/version/approval tables + TOC field.)

## 10. Excel/XLSX template requirements
Cover/ReadMe sheet · data-dictionary sheet · main data sheet · summary/dashboard sheet · **filters enabled** · frozen header · styled header · auto widths · **conditional formatting (severity/status)** · data validation · formulas (already in reporting XLSX) · **RTL sheet for AR** · locale date/number · severity/status colors · hyperlinks to source · metadata. **Gates:** opens; clear sheet names; AR sheet RTL; filters work; readable columns; formatted numbers/dates; no leaked system fields; summary totals match record count.

## 11. CSV / JSON / Markdown / HTML requirements
- **CSV:** UTF-8 + BOM; RFC-4180; **neutralize `=+-@` leading cells**; language-aware headers; data-dictionary when packaged. (`clientExport.ts` already does BOM+RFC-4180; server `NdiReportExporter`/`AnnexExportService` CSV need the injection guard that `BcmEvidencePackageService` already has.)
- **JSON:** stable schema, `metadata` + `data`, export version/lang/filters/generated-date, no unauthorized internal fields.
- **MD:** title, metadata, headings, tables, references, AR note.
- **HTML:** responsive + print CSS, RTL/LTR, branding, tables, charts, metadata. (`packetPrint.ts` client template already does this; the server annex HTML needs content — §23.)

## 12. Evidence package requirements
Cover file · evidence index · requirement/control mapping · evidence files · evidence metadata/owner/approval/expiry · review comments · audit-trail extract · gaps + corrective actions · package metadata · **integrity/checksum manifest**. Recommended layout `/evidence-package/{00_README, 01_Evidence_Index.xlsx, 02_Requirement_Mapping.xlsx, 03_Evidence_Files/, 04_Audit_Trail.xlsx, 05_Gaps_And_Actions.xlsx, manifest.json}`. **Current:** `BcmEvidencePackageService` ZIP already ships cover.txt + 5 XLSX + audit_log.csv + MANIFEST.txt (SHA-256) — close to target, but **omits the actual evidence file binaries** (deferred T1.4b) and uses `.txt`/`MANIFEST.txt` rather than a README.pdf + `manifest.json`.

## 13. Audit package requirements
Audit scope · period · framework mapping · control status · evidence index · evidence files · exceptions · corrective actions · management approval · export metadata. **Current:** no dedicated cross-module audit-package builder (the BCM evidence ZIP is the closest); risk "audit-package" is JSON data only `[LIVE]`.

## 14. Regulatory submission package requirements
Professional cover · requirement/control mapping · gaps/CAPA with owners + due dates · AR or EN per recipient · classification · approval. **Current:** NDI submission-package (S-G/S-I in ESG), NCA-ECC (client CSV + print), PDPL breach (client JSON) — each partial; none assembled as a single branded regulator ZIP.

## 15. Export file-naming convention (target)
`[Org]_[Module]_[ExportType]_[Scope]_[Lang]_[YYYYMMDD]_[vN].[ext]` — e.g. `TSS_ERM_RiskRegister_All_AR_20260707_v1.xlsx`. **Current `[LIVE]`:** reporting sets dated names (`risk-register-2026-07-07.pdf`) but **no lang/version**; cyber/AI set undated (`cyber-incidents.xlsx`); ESG uses report code (`CLIM-2025-v1.pdf`). No central naming utility (server inlines per-controller; client uses `stampFilename`). **Action:** one shared `ExportNaming` helper (server + mirror client) producing the full convention incl. language code.

## 16. Export metadata standard
**Required:** org · platform · module · title · export type · period/cycle · scope/filter summary · generated-by · generated-at · language · classification · version · approval status · source record count · data-source note · confidentiality notice. **Optional:** reviewed-by · approved-by · signature status · evidence-package id · framework/control-library/risk-methodology versions · distribution list. Most PDFs carry ~half of these via `coverHero`/`metadataGrid`; **record count, scope/filter summary, and language are inconsistently present** — standardize a `ReportMetadata` builder fed into every generator.

---

## 17. Template registry design
Introduce `ExportTemplate` (module, export_type, format, language, direction, version, config_json, brand_theme_id, is_default/active) + a resolver so a module export names a template code instead of hardcoding layout. Phase-1 can back it by the existing generator classes (a template = generator + theme + options), phase-2 makes layout data-driven. Pairs with `BrandTheme` (§26).

## 18. Global export engine recommendation
A true engine largely **already exists** (reporting module). Recommendation: **formalize + extend it**, don't rebuild:
1. `PdfTheme`/`PdfFonts` → add RTL helper + bundled Arabic font + tenant `BrandTheme` resolver.
2. New shared `XlsxStyler` (header style, freeze, **auto-filter**, **conditional formatting**, **RTL**) to replace the 4 hand-rolled copies.
3. `ReportMetadata` + `ExportNaming` shared builders.
4. `ExportJob`/`ExportAuditLog`/`ExportQualityCheck` for on-demand exports (mirroring the scheduled `ReportSchedule`/`ReportRun`).
5. `ExportQualityValidator` (open/parse the produced bytes; assert signature, page count, Arabic-glyph presence, sheet RTL, record-count match).

## 19. Current gaps by module
- **SOC-Ops:** backend export endpoint exists; **no FE button** (Agent B: zero export surface in 27 SOCOps pages).
- **Evidence module:** no export/download of its own; the "evidence package" is a BCM feature — Evidence module offers only a client CSV of the register.
- **Audit/Audit-Committee:** print-to-PDF + `.txt` packets from mock data; no server audit-package.
- **Privacy/PDPL & NCA:** regulator artifacts are client CSV/JSON/print, not branded server PDFs/ZIPs.
- **Risk/Compliance/KRI/Cyber/AI/BCM/NDI/ESG/Committees:** real server exports (strong).
- **Admin/Board-Pack page:** render from mock data (`mockAdmin`, `boardPack.ts`).

## 20. Current gaps by file format
- **PDF:** Arabic font unbundled + RTL cosmetic outside board-pack (critical).
- **XLSX:** no RTL, no auto-filter, no conditional formatting; 4 duplicate header styles; `autoSizeColumn` scale/headless risk.
- **DOCX:** solid RTL, but missing document-control/version/approval tables + TOC field.
- **PPTX:** tables hardcoded LTR.
- **CSV:** injection guard only in BCM evidence CSV, not NDI/annex.
- **HTML/JSON (annex):** `[LIVE]` near-empty output for at least one report.
- **ZIP:** evidence binaries omitted; `manifest.json` is `.txt`.

## 21. Current gaps by language direction
1. Arabic PDF layout LTR in 9/10 generators. 2. Arabic font not bundled (prod portability). 3. Excel never RTL. 4. PPTX tables never RTL. 5. Filenames never encode language. 6. Compliance export picks language from `navigator.language` (inconsistent with UI/doc-lang).

## 22. Critical design & formatting gaps
1. **Arabic font not bundled** → prod Arabic PDFs degrade to boxes (highest impact). 2. **RTL cosmetic** outside board-pack. 3. **BCM evidence ZIP** guarded by `isAuthenticated()` only — most sensitive dump, weakest guard. 4. **CSV formula-injection** in NDI/annex CSV.

## 23. Data-completeness gaps
- `[LIVE]` **sustainability-annex `export/html` & `export/json` return 7 bytes** (near-empty) for `SR-2027-03` while `export/md` returns 190 B — either the report has no in-year register data or `AnnexGeneratorService.html()/json()` under-serialize; **must reproduce on a data-populated report + add a content-length quality check**.
- Evidence ZIP omits evidence file binaries (deferred).
- Reporting server exports take **no scope/filter param** — always a full snapshot (record count not surfaced in the file).
- Board-Pack **page** and Admin export read mock data, not live.

## 24. Testing gaps
Only **6** test files touch generated bytes, almost all **magic-byte-only** (`%PDF`/`PK` first bytes); **no test opens a PDF with `PdfReader` or an XLSX with `WorkbookFactory`**, and **no test asserts Arabic renders**. ~13 production generators (`ExportService`, `DocxExportService`, `BoardPackService`, `BoardPackPptxService`, `NdiReportExporter`, `ClimateReportExportService`, `AnnexExportService`, `AiRegistryExportService`, `CommitteeFormExportService`, `SocOpsExportService`, `RegisterExport`, `CrisisEventPdfExporter`, `BcpPlanPdfExporter`) have **no byte-level test**. No frontend test exercises a download.

---

## 25. Proposed enhancement decision table

| # | Enhancement | Decision | Priority | Rationale |
|---|---|---|---|---|
| 1 | Bundle OSS Arabic TTF (Noto Naskh/Kufi or Amiri) + `@Value grc.pdf.arabic-font` + fix README | **ADD** | **P0** | Prod Arabic PDFs otherwise boxes; single highest-impact gap |
| 2 | RTL run-direction + right-align helper in `PdfTheme`, adopt in all generators for `lang=ar` | **GLOBALIZE** | **P0** | RTL currently cosmetic outside board-pack |
| 3 | Harden `BcmEvidencePackageController` guard (`BCM:READ`/role) | **IMPROVE** | **P0** | Most sensitive dump, weakest guard (authz) |
| 4 | CSV injection guard in `NdiReportExporter`/`AnnexExportService` | **IMPROVE** | **P0** | Regulator opens CSV in Excel → formula executes |
| 5 | Fix/verify annex HTML/JSON content + add content-length quality check | **IMPROVE** | **P1** | `[LIVE]` near-empty output |
| 6 | Shared `XlsxStyler`: freeze + **auto-filter** + **conditional format** + **RTL** | **GLOBALIZE** | **P1** | 4 duplicate styles; no filter/RTL/CF anywhere |
| 7 | `ReportMetadata` + `ExportNaming` shared builders (lang+version+date) | **GLOBALIZE** | **P1** | Metadata/naming inconsistent |
| 8 | Byte-level export test harness (parse PDF/XLSX; assert Arabic-glyph + sheet-RTL + record-count) | **ADD** | **P1** | Coverage is magic-byte only |
| 9 | SOC-Ops FE export button (endpoint exists) | **ADD** | **P1** | Backend ready, FE missing |
| 10 | `BrandTheme` (server) + propagate tenant white-label to engines | **ADD** | **P2** | Client theme never reaches server PDFs |
| 11 | Export-options modal (format, AR/EN, scope, include charts/evidence/cover) reusing `ExportMenu` | **IMPROVE** | **P2** | One-click only today |
| 12 | `ExportJob`/`ExportAuditLog` for on-demand exports + history UI | **ADD** | **P2** | Only scheduled reports logged |
| 13 | Regulatory/audit package builder (README.pdf + index.xlsx + files + manifest.json) | **ADD** | **P2** | No unified regulator ZIP; evidence binaries omitted |
| 14 | PPTX table RTL; DOCX control/version/approval tables + TOC field | **IMPROVE** | **P2** | Format completeness |
| 15 | Bound row caps / replace `autoSizeColumn` for large tenants | **IMPROVE** | **P3** | Scale/headless risk |
| 16 | Retire `.txt` "regulator packets" → branded PDF/ZIP; wire mock-data exports to live | **IMPROVE** | **P3** | Regulator packets shipping as plain text |
| 17 | Unify 6 client download mechanisms behind one helper | **MERGE** | **P3** | Maintainability |
| 18 | `ExportTemplate` registry + picker | **DEFER** | **P3** | Valuable but large; generators suffice near-term |

---

## 26. Data-model changes
Add (SQL Server, forward migrations): `BrandTheme` (tenant palette/logo/fonts, is_default), `ExportTemplate` (module/type/format/language/config_json/brand_theme_id), `ExportJob` (module/type/format/language/template_id/requested_by/at/status/filters_json/scope/file_name/record_count/validation_status/file_ref/completed_at), `ExportAuditLog` (export_job_id/user/action/ts/details), `ExportQualityCheck` (export_job_id/check_name/type/status/severity/message). Reuse the pattern of existing `reporting.report_schedules`/`report_runs` and `bcp_binder_export` (artifact registry).

## 27. API changes
`GET/POST /api/exports/templates`(+`/{id}`,`/activate`,`/set-default`); `GET/POST/PUT /api/exports/themes`(+`/set-default`); `POST /api/exports/jobs`, `GET /api/exports/jobs[/{id}]`, `/{id}/download`, `/{id}/regenerate`, `/{id}/validate`; `POST /api/exports/preview`; `POST /api/modules/{module}/export` + `GET .../export-options`; `GET /api/exports/jobs/{id}/quality-checks` + `/run-quality-checks`. (Existing per-module export endpoints remain; the jobs API wraps them for governed/critical exports.)

## 28. UI changes
Reusable **Export dialog** (format · AR/EN doc-language · template · scope/filter confirmation · include evidence/charts/audit-trail/CAPA/exec-summary/cover/approval · classification · **file-name preview** · optional preview · generate/download · save-to-history) — generalize `components/ui/ExportMenu.tsx` + the Reports-Centre language toggle. Add SOC-Ops export button. Add a cross-module **Export History** screen (generalize `ReportSchedulesPage` run-history + binder history).

## 29. Template design changes
Promote `PdfFonts` palette to `BrandTheme` tokens; add `PdfTheme.rtl(...)`; bundle Arabic font; shared `XlsxStyler`; DOCX control/version/approval tables + TOC field; PPTX table RTL; README.pdf + `manifest.json` for packages.

## 30. Export-validation changes
`ExportQualityValidator` runs post-generation: signature check; PDF opens via `PdfReader` + page-count > 0 + (for AR) an embedded Arabic-capable font is present and used; XLSX opens via `WorkbookFactory` + sheet RTL for AR + summary totals == record count; CSV BOM + no unescaped injection; ZIP has index + manifest with valid checksums; filename matches convention. Persist results to `ExportQualityCheck`.

## 31. Automated testing plan
Byte-level suite (JUnit) per generator: PDF → `PdfReader` opens, page-count, cover text, footer present, **AR font embedded & Arabic codepoints present**; XLSX → `WorkbookFactory` opens, header frozen, **auto-filter set**, **sheet RTL for AR**, totals match; DOCX → open + `w:bidi` for AR + TOC field; PPTX → open + slide count + AR table align; CSV → BOM + injection-neutralized; ZIP → entries + manifest checksums verify; JSON → schema + metadata; filename convention. Frontend vitest: each export button calls the right endpoint/format/lang and passes active filters. Add a live smoke (the `scratchpad/rev/ex.py` harness) to CI-style regression.

## 32. Manual QA checklist
Per exported file: opens · meaningful name · correct language + direction · correct template/branding/colors/fonts(+fallback) · metadata · scope · records · totals · charts · tables · page breaks · headers/footers · approvals · evidence refs · audit-trail refs · classification · version · **no broken Arabic** · no table overflow · no raw unstyled dump (unless CSV/JSON by design). Do this in **both** AR and EN, and once on a **Linux/container** host to catch the font-fallback.

## 33. Implementation backlog (grouped)
- **P0 (correctness/security/Arabic):** #1 bundle Arabic font, #2 RTL helper + adopt, #3 evidence-ZIP guard, #4 CSV injection guard.
- **P1 (quality core):** #5 annex content + check, #6 `XlsxStyler` (filter/CF/RTL), #7 metadata+naming builders, #8 byte-level test harness, #9 SOC-Ops FE button.
- **P2 (governance/UX):** #10 `BrandTheme` propagation, #11 export-options modal, #12 `ExportJob`/audit + history UI, #13 regulator/audit package builder, #14 PPTX/DOCX completeness.
- **P3 (polish):** #15 scale, #16 retire `.txt` packets/mock exports, #17 unify client helpers, #18 template registry+picker.

## 34. Recommended implementation order
1. **P0 correctness pass** (bundle Arabic font → adopt RTL helper across generators → evidence-ZIP guard → CSV injection). 2. **Byte-level test harness** (locks the P0 gains, incl. AR-glyph + sheet-RTL assertions). 3. **Shared XLSX styler + metadata/naming builders** (visible quality lift, low risk). 4. **Annex content fix + SOC-Ops FE button.** 5. **BrandTheme propagation + export-options modal.** 6. **ExportJob/audit + history UI.** 7. **Regulator/audit package builder.** 8. **Polish (PPTX/DOCX, scale, retire `.txt`, template registry).** Ship P0+tests first; run the full byte-level + AR/EN + Linux-host QA after each wave.

## 35. Risks if not fixed
- **Arabic PDFs render as boxes in prod** (Linux/container) → unusable for the AR-primary Saudi audience and regulator submissions; reputational + compliance risk.
- **RTL-cosmetic reports** look wrong to Arabic board/regulator readers even when glyphs render.
- **Evidence-ZIP authz gap** → any authenticated user across the tenant can pull the full BIA/risk/strategy/CAPA/audit-log dump.
- **CSV injection** → formula execution in a regulator's Excel from a "compliance" export.
- **No byte-level tests** → silent regressions (a broken generator returns 200 with a corrupt/empty file — already seen with the annex).
- **No export governance** → no audit trail / who-exported-what for sensitive downloads; weak in a Saudi regulatory audit.

## 36. Final recommendation
**Partially usable, trending market-ready.** This is a real enterprise document-generation capability — a shared themed engine, every format genuinely produced (`[LIVE]` 26/29 valid today), strong PDF metadata/branding, SHA-256 integrity on the sensitive artifacts. It is **not** demo-only and does **not** need a rebuild. To reach unambiguously market-ready, execute the focused path: **(1) fix Arabic — bundle the font + make RTL real across generators (P0); (2) close the two security/correctness gaps (evidence-ZIP guard, CSV injection); (3) add the byte-level + AR/EN + Linux-host test harness; (4) globalize XLSX styling + metadata/naming; (5) layer governance (BrandTheme propagation, ExportJob/audit, export-options UI) and package builders.** Most of this is additive on top of the existing engine.

---

### Review stats
- **Modules reviewed:** 22 functional areas (all backend `module/*` + sust + board_pack).
- **Export actions inventoried:** ~28 server document-export endpoints + ~30 client CSV/JSON/TXT/print actions.
- **Generators/"templates" reviewed:** 15 backend generator classes + shared `PdfTheme`/`PdfFonts`/`RegisterExport` + 1 client HTML print template + print CSS (NCA/board-pack). Formal template entities: **0**.
- **Governance entities present:** 0 of 5 target (`ExportTemplate`/`BrandTheme`/`ExportJob`/`ExportAuditLog`/`ExportQualityCheck`); adjacent `report_schedules`/`report_runs`/`bcp_binder_export` exist.
- **RTL/LTR issues:** 6 (PDF layout LTR ×9 generators; Arabic font unbundled; Excel no RTL; PPTX tables LTR; filenames no lang; compliance lang from `navigator.language`).
- **Critical export-quality issues:** 4 (Arabic font unbundled; RTL cosmetic; evidence-ZIP authz; CSV injection) + 1 correctness (annex near-empty `[LIVE]`).
- **Live validation:** 26/29 endpoints returned valid, correctly-typed files; 3 near-empty (annex); Arabic PDFs embed host-Arial (not bundled) `[LIVE]`.

**Next command to start implementation (P0 correctness pass):**
`implement P0 export fixes: bundle Arabic font + RTL helper across generators + harden BCM evidence-ZIP guard + CSV injection guard, then the byte-level test harness`
