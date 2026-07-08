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

### Phase 5 (OPTIONAL, on request) — true server-side binaries (word / excel)
10. For packets that benefit from an editable/tabular binary (SAMA OpRes RTO table, Haseen per-domain
    maturity, ESG emissions), add POST endpoints accepting the client payload and generating **PDF via
    PdfTheme**, **DOCX via DocxExportService**, **XLSX via XlsxStyler** — all with `ExportNaming`
    filenames + the export audit trail. Bigger lift (DTOs + services + FE POST wiring); implement only
    if the print-to-PDF of Phases 1–2 is judged insufficient.

---

## Verification standard (per the previous specs)
- **FE:** `tsc --noEmit` + `vite build` clean; browser preview — open the generated PDF window and
  assert real sections, brand colour/logo, bilingual/RTL, classification.
- **BE:** `mvn test` (JDK 17) + rebuild/restart + live download; unzip and assert `README.pdf` = `%PDF`,
  manifest integrity, ExportNaming filename, and an `action=EXPORT` audit row.
- Commit per slice on `feat/export-txt-to-formats`. **No merge to master until explicitly told.**

## Residuals / decisions to confirm
- Keep or drop the raw `.txt` secondary button per packet (Crisis keeps it for portal upload; others
  likely drop). Default: **PDF primary, `.txt` kept only where a machine/portal consumer needs it.**
- Phase 5 (docx/xlsx binaries) is opt-in — Phases 1–3 already convert every `.txt` to a formatted
  branded document.
