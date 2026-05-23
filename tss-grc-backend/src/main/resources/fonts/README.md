# Arabic PDF fonts

Closes Open Issue **#003** (Arabic PDF font bundling).

OpenPDF, the platform's PDF generator (used by Audit Evidence Package, Board Pack, BCP Binder, and committee form exports) cannot render Arabic text without an explicit font file bundled at runtime. This folder is the resource path the platform's PDF service loads fonts from.

## Required fonts

Add the following files to this folder. Both are released under the **SIL Open Font License 1.1**, permissive for embedded distribution.

| File | Family | License | Use |
|---|---|---|---|
| `NotoKufiArabic-Regular.ttf` | Noto Kufi Arabic | OFL 1.1 | Default Arabic body text |
| `NotoKufiArabic-Bold.ttf`    | Noto Kufi Arabic | OFL 1.1 | Headings / emphasis |
| `Amiri-Regular.ttf`          | Amiri            | OFL 1.1 | Optional — traditional Naskh style for letters / formal docs |
| `Amiri-Bold.ttf`             | Amiri            | OFL 1.1 | Optional bold pair |

The platform loader will pick **NotoKufiArabic-Regular.ttf** by default. Set `grc.pdf.arabic-font` in `application.yml` to override.

## Why these aren't in the repo by default

The `.ttf` binary files are ~2-3 MB each and are not committed to keep the repo small. Add them via the Operations runbook on first install:

```bash
# From inside the deployed JAR / classpath setup
curl -L -o NotoKufiArabic-Regular.ttf https://github.com/google/fonts/raw/main/ofl/notokufiarabic/NotoKufiArabic%5Bwght%5D.ttf
curl -L -o NotoKufiArabic-Bold.ttf     https://github.com/google/fonts/raw/main/ofl/notokufiarabic/NotoKufiArabic%5Bwght%5D.ttf  # variable font — same file, bold weight selected at render
curl -L -o Amiri-Regular.ttf           https://github.com/google/fonts/raw/main/ofl/amiri/Amiri-Regular.ttf
curl -L -o Amiri-Bold.ttf              https://github.com/google/fonts/raw/main/ofl/amiri/Amiri-Bold.ttf
```

After placing files here, restart the backend. The PDF service warms up the font registry at startup and logs `Arabic font registered: <path>` for each.

## License attribution

The OFL 1.1 requires that downstream users have access to the license text. The platform serves `/api/v1/licenses/fonts` which returns the OFL 1.1 text for any embedded font — wired in `module/platform/controller/LicensesController.java`.

## RTL handling notes

- Use `PdfWriter.setRunDirection(PdfWriter.RUN_DIRECTION_RTL)` for Arabic-only documents
- Use `RUN_DIRECTION_LTR` then per-paragraph override for mixed-direction
- Numbers + Latin codes (e.g. `SAR 12,500`) embed cleanly with the directional logic OpenPDF inherits from iText
- Always set the document language attribute (`document.addLanguage("ar")`) so screen readers + assistive tech route correctly
