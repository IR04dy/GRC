# 05 — RTL & Localization Requirements

## Architecture

- **String keys** — every UI string is a key referenced via
  `t('key')`; the dictionaries live in
  `tss-grc-ui-v2/src/translations/{en,ar}.ts`. **Never** inline a
  user-visible string in EN-only.
- **Direction** — root `<html dir="ltr|rtl">` flipped at runtime by
  the `useI18n()` hook when `lang === 'ar'`.
- **Bootstrap RTL** — loaded conditionally; layout flips
  automatically (margins, padding, flex order).
- **Tabular numerals** — Arabic copy uses Eastern Arabic numerals
  for narrative text but Western (0123…) for IDs, codes, and
  measurements (matches Saudi government conventions).
- **Bilingual data** — bilingual columns (`*_en` and `*_ar`) on
  every domain entity. The frontend picks based on `lang`.

## Saudi-native AR style guide (per MEMORY.md)

Use natural Saudi-Arabic phrasing rather than literal translations:

| Concept | ❌ Literal | ✅ Saudi-native |
|---|---|---|
| Resilience | الصمود | الجاهزية |
| Connectors / Linkages | الموصلات | الترابطات |
| Pareto analysis | تحليل باريتو | تحليل النسبة الأهم |
| Catalog | كاتالوج | الفهرس |
| Board pack | حزمة المجلس | ملف الجلسة |
| Disclosure | الإفصاح | الإقرار |

(The full running list of corrections is in `MEMORY.md`. Translators
add to it as they review new strings.)

## Hijri date support

- `HijriDatePicker.tsx` component for date inputs.
- Dual columns where regulator returns require Hijri: e.g.
  `authority_documents.effective_date_g` (Gregorian) +
  `effective_date_h` (Hijri string).
- `<HijriDate value={…} />` display component for read views.

## Iconography

- Lucide icons used throughout; direction-sensitive icons (chevron,
  arrow) flip via the `DirectionalArrow` component which reads
  `lang` and renders the mirrored variant on AR.

## Forms

- Labels above inputs (RTL/LTR friendly).
- Error messages bilingual; service layer returns both `messageEn`
  and `messageAr` when available, frontend picks.

## Numerals

| Context | Format |
|---|---|
| IDs, codes (RISK-CYB-0042) | Western only |
| Currency (SAR) | Western with thousand-separator commas |
| Percentages | Western with `%` suffix |
| Narrative bodies in AR | Locale-default (Eastern OK) |
| Tables of numeric KPIs | Western (improves scan-ability) |

## Translation status flag (Authority Documents)

The `translation_status` column lets QA gate AR quality
incrementally:

| Value | Meaning | UI badge |
|---|---|---|
| `EN_ONLY` | No AR yet | danger |
| `AR_AUTO` | Machine-translated; needs review | warning |
| `AR_HUMAN_REVIEWED` | Saudi-native AR confirmed | info |
| `AR_OFFICIAL` | AR is the official text from the regulator | success |

## Testing

QA checklist items per `docs/*/QA_CHECKLIST.md` always include:

- [ ] RTL layout flips correctly on every tab.
- [ ] Bilingual columns populated on every record.
- [ ] AR strings follow Saudi-native style per MEMORY.md.

---

**See also:** `lib/i18n.tsx`, `translations/{en,ar}.ts`, `MEMORY.md`.
