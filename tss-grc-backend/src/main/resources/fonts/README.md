# Arabic PDF fonts

OpenPDF (the platform's PDF generator — used by every PDF export: reporting/board-pack, BCP
binder, crisis/plan, cyber/SOC, NDI, sustainability, committee forms, evidence packages) needs a
TrueType font with Arabic glyphs to render Arabic PDFs. **No Arabic TTF is committed to this repo
by default** (OFL redistribution is a per-deployment licensing decision, and the binaries are
2–3 MB each). Until one is provided, `PdfFonts` falls back to an OS font or Helvetica — on a
fontless Linux/container host, Arabic PDF text renders as boxes.

## How `PdfFonts` resolves the Arabic font (precedence)

Implemented in `module/reporting/service/PdfFonts.java#loadBase()`:

1. **Ops override** — `-Dgrc.pdf.arabic-font=/abs/path/Font.ttf` (JVM system property) or the
   `GRC_PDF_ARABIC_FONT` environment variable. If set and the file exists it wins over everything.
   This is the recommended production wiring: mount a licensed/OSS Arabic TTF on the host and point
   the property at it — no rebuild needed.
2. **Bundled classpath font** — the first of these present under `resources/fonts/` is used:
   `NotoNaskhArabic-Regular.ttf`, `NotoKufiArabic-Regular.ttf`, `NotoSansArabic-Regular.ttf`,
   `Amiri-Regular.ttf`.
3. **OS-installed font** — `C:/Windows/Fonts/arial.ttf` (full Arabic) or DejaVuSans (partial Arabic).
4. **Helvetica** — Latin-only last resort; Arabic renders as boxes (logged at `ERROR`).

The loader logs which font it settled on at startup (`PDF Arabic font: …`), and warns/errors when
it falls back — check the boot log to confirm your intended font loaded.

## To enable proper Arabic PDFs

Pick **one**:

- **Bundle (self-contained build):** drop an OFL Arabic TTF into this folder under one of the names
  in step 2 above (e.g. `NotoNaskhArabic-Regular.ttf`). It ships inside the JAR and needs no config.
- **Host font (no rebuild):** install/mount an Arabic TTF on the server and set
  `-Dgrc.pdf.arabic-font=/path/to/Font.ttf` (or `GRC_PDF_ARABIC_FONT`).

Recommended OFL families (SIL Open Font License 1.1 — permissive for embedding): **Noto Naskh
Arabic** (modern Naskh, best for body text), **Noto Kufi Arabic**, or **Amiri** (traditional Naskh).
Obtain the `.ttf` from the family's official OFL distribution and keep its `OFL.txt` alongside it in
the deployment to satisfy the license's attribution requirement.

## RTL handling notes

- For Arabic documents set `PdfWriter.setRunDirection(PdfWriter.RUN_DIRECTION_RTL)` on the writer and
  `PdfPCell.setRunDirection(RUN_DIRECTION_RTL)` on table cells (see `BoardPackService` for the
  reference implementation; rolling this out to the other generators is tracked in the export
  remediation plan as P0-2).
- Latin codes/numbers (e.g. `SAR 12,500`) embed cleanly under RTL via the bidi logic OpenPDF
  inherits from iText.

> Note: a bundled font alone fixes glyph *shaping*; full right-to-left *layout* additionally requires
> the run-direction calls above. Both are needed for correct Arabic output.
