# Export `.txt` → Formatted-Document Remediation Plan

**Goal:** every export that currently produces a plain **`.txt`** file becomes a properly-formatted
document — branded PDF (default), or Word/Excel where the content warrants it — reusing the
"previous specifications": tenant **BrandTheme** (logo / primary colour / org name), **bilingual EN/AR
+ RTL**, **classification banner**, the **`ExportNaming`** filename convention, and (server-side) the
**export audit trail**.

Scope discovered by a platform-wide sweep (`grep .txt / text/plain` + a content-mapping pass). Eight
`.txt` producers exist: six on the frontend (five regulatory packets + MFA recovery codes) and two
inside the backend BCM evidence ZIP.

---

## Inventory & target format

| # | Export (current `.txt`) | Where | Data | Target format | Approach |
|---|---|---|---|---|---|
| 1 | Audit-Committee packet `AC-Packet-Q#.txt` | FE `AuditLifecyclePage` | client (mock/localStorage) | **Branded PDF** (structured) | Upgrade `packetPrint` renderer |
| 2 | SAMA OpRes filing `SAMA-OPRES-Q#.txt` | FE `BcmLifecyclePage` | client | **Branded PDF** | Upgrade `packetPrint` |
| 3 | NCA Haseen filing `NCA-Haseen-YYYY.txt` | FE `CyberLifecyclePage` | client | **Branded PDF** | Upgrade `packetPrint` |
| 4 | NCEMA crisis notice `NCEMA-{id}.txt` | FE `CrisisManagementPage` | client | **Branded PDF** | Wire `packetPrint` (no PDF today) |
| 5 | ESG annex `ESG-Annex-YYYY.txt` | FE `EsgPage` | client | **Branded PDF** | Wire `packetPrint` (no PDF today) |
| 6 | MFA recovery codes `tss-mfa-recovery-*.txt` | FE `MfaSetupPage` | client, **security-sensitive** | **Branded print card (PDF)**, keep `.txt` | Client-only; codes must NOT round-trip to server |
| 7 | BCM evidence `cover.txt` (in ZIP) | BE `BcmEvidencePackageService` | server DB | **Branded README.pdf** | Reuse `RegulatorPackageService` PdfTheme pattern |
| 8 | BCM evidence `MANIFEST.txt` (in ZIP) | BE `BcmEvidencePackageService` | server | **Keep `.txt`** (+ hashes echoed on README.pdf) | `sha256sum -c` must stay machine-verifiable |

**Deliberately left as-is:** sust annex `.md` export (markdown is an intentional raw-format option, not `.txt`);
GHG import accepts `text/plain` (an *input*, not an export).

---

## Key facts that shape the approach

1. **A branded client-PDF renderer already exists** — `tss-grc-ui-v2/src/lib/packetPrint.ts`
   `printPacket(doc, lang)` opens a print window with A4 print-CSS: logo (`brand.logoDataUrl`),
   `brand.primaryHex`, tagline, bilingual/RTL direction, eyebrow, classification banner, per-section
   layout, footer. It already supports **structured** `PacketSection[]` with HTML bodies (incl. tables).
2. **But callers under-use it** — Audit/SAMA/Haseen call `textToSection('Full packet', …, rawText)`,
   dumping the entire monospace packet into ONE `white-space: pre-wrap` section. Crisis/ESG/MFA have
   **no PDF path at all**. So the fix is mostly: *render structure*, and *wire the missing buttons*.
3. **Packet text has a regular grammar** — a divider band, a bilingual title, a `key: value` header
   block, numbered sections `N. ENGLISH HEADING · العنوان العربي`, `EN:`/`AR:` line pairs, and a
   footer. This parses cleanly into `PacketSection[]` + light per-section structure.
4. **Packet data is 100 % client-side** (mock/localStorage/consts). True server-side binaries would
   require POSTing the assembled payload — deferred to the optional Phase 3.
5. **BCM cover/manifest are server-side with real tenant data** — convert exactly like the new
   `RegulatorPackageService` README.pdf (PdfTheme + BrandTheme), keeping the manifest verifiable.

---

## Phases

### Phase 1 — Structured branded packet PDFs (client) — *primary conversion*
1. **`packetPrint.ts`**: add `packetTextToDocument(...)` / `packetTextToSections(text)` that parses the
   packet grammar into `PacketSection[]` and renders each body as light HTML (definition rows for
   `key: value`, monospace blocks for aligned count tables, EN/AR pairs stacked). Improve print CSS
   (section page-break, bilingual section headers). Keep `textToSection` for back-compat.
2. Repoint **Audit / SAMA / Haseen** "Print PDF" to the structured parser.
3. Make **PDF the primary action**; keep a secondary "raw `.txt`" only where a portal upload needs it
   (Crisis → Haseen portal note; else drop the `.txt` button or relabel).
4. **Verify** in the browser preview: open a packet PDF window, assert real sections + branding + RTL.

### Phase 2 — Wire the two packets with no PDF (client)
5. **Crisis (NCEMA)** and **ESG annex**: add a branded "Download PDF" action using `printPacket` +
   the structured parser (bilingual, classification "CONFIDENTIAL — REGULATOR" / appropriate).
6. **Verify** each in the browser.

### Phase 3 — BCM evidence cover → branded PDF (backend) — *real-data conversion*
7. **`BcmEvidencePackageService`**: replace `cover.txt` with `README.pdf` built via `PdfTheme`
   (cover hero + KPI/metadata + contents list + integrity note) and the tenant `BrandTheme`, exactly
   like `RegulatorPackageService`. Keep `MANIFEST.txt` (echo the same hashes on the README for humans).
8. **Verify live**: rebuild + restart + download the BCM evidence ZIP; assert `README.pdf` starts
   `%PDF`, manifest still lists sha256 lines, `sha256sum -c` semantics intact.

### Phase 4 — MFA recovery card (client, optional)
9. Offer a branded print "recovery card" (logo + codes grid + single-use warning) in addition to the
   existing `.txt`. **Client-only** — recovery codes must never be POSTed to the server.

### Phase 5 — true server-side XLSX binaries (the "excel" deliverable) — ✅ DONE
10. **`POST /api/v1/reporting/packet.xlsx`** (`TabularExportRequest` → `TabularExportService`) turns
    client-assembled `{module, exportType, lang, sheets[]}` into a branded workbook: XlsxStyler house
    treatment (brand header via tenant BrandTheme + freeze + auto-filter + RTL + status
    conditional-formatting), `ExportNaming` filename, auto-audited. Cell values are literal strings
    (no formula-injection surface). Wired into the three named tabular packets — **ESG annex**
    (`buildEsgAnnexSheets`), **SAMA OpRes** (`buildOpresSheets`), **NCA Haseen** (`buildHaseenSheets`) —
    each with an "Export Excel" button beside the PDF + raw `.txt`. `downloadFile()` generalized to
    POST+JSON. Verified: unit tests + live POST (branded xlsx w/ autoFilter/RTL/CF/brand fill) + FE
    dynamic-import payload checks.
11. **DOCX binaries + AC/NCEMA XLSX — ✅ DONE (master @ 1f0a7225).** `POST /reporting/packet.docx`
    (`TabularExportService.docx`, reusing DocxExportService's XWPF helpers + tenant BrandTheme + RTL)
    with "Export Word" on all five packets; `buildAcSheets`/`buildNcemaSheets` + "Export Excel" on the
    Audit-Committee and NCEMA packets. Every packet now exports PDF · Excel · Word · raw .txt. Both
    `packet.xlsx`/`packet.docx` guarded `hasPermission('REPORTING','READ')`.
12. **Also bundled:** `Amiri-Regular.ttf` (SIL OFL 1.1) in `resources/fonts/` so Arabic PDFs render on
    a fontless host without config.

---

## Verification standard (per the previous specs)
- **FE:** `tsc --noEmit` + `vite build` clean; browser preview — open the generated PDF window and
  assert real sections, brand colour/logo, bilingual/RTL, classification.
- **BE:** `mvn test` (JDK 17) + rebuild/restart + live download; unzip and assert `README.pdf` = `%PDF`,
  manifest integrity, ExportNaming filename, and an `action=EXPORT` audit row.
- Commit per slice on `feat/export-txt-to-formats`. **No merge to master until explicitly told.**

## Status — COMPLETE (master @ 1f0a7225, 2026-07-09)
All phases shipped and merged; backend `mvn test` green (2242 tests), FE `tsc` + `vite build` clean.
Every `.txt` export is now a branded, bilingual/RTL formatted document, and each regulatory packet
offers **PDF · Excel · Word · raw .txt** (the raw `.txt` retained where a machine/portal consumer
needs it, e.g. Crisis → Haseen portal). No open items on the export track.
