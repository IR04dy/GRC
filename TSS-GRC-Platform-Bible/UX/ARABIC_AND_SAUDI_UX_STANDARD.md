# Arabic and Saudi UX Standard

## Principle

Arabic is the primary product language for Saudi deployments. It must feel authored in Arabic, not mirrored from English.

## Language and content

- Use Modern Standard Arabic with a governed Saudi GRC glossary.
- Avoid literal translations of English technical idioms.
- Display the Arabic legal/regulatory term first and retain the English acronym where commonly used: `تقييم أثر حماية البيانات (DPIA)`.
- Record translation status: `AUTHORED`, `REVIEWED`, `MACHINE_DRAFT`, `NOT_REQUIRED`.
- Legally material notices and decisions require human-reviewed Arabic.
- Do not silently fall back to English; label missing translation and provide a controlled fallback.

## Directionality

- Page layout mirrors in RTL.
- Numbers, email, URLs, control IDs, hashes and code remain LTR within isolated spans.
- Tables keep the primary identity column nearest the reading start.
- Timeline progression mirrors only where it represents reading flow; chronological charts retain a clearly labelled axis.
- Directional icons must be semantic, not mechanically mirrored.
- Mixed Arabic/English text is tested for Unicode bidi isolation.

## Dates and time

- Store UTC instants; display Saudi local time.
- Show timezone on deadlines and regulator clocks.
- Support Gregorian as the operational default and Hijri as an optional parallel display where required.
- Never convert a legally binding due date without showing the authoritative calendar/source.
- Use absolute dates in decisions; relative labels such as “tomorrow” are supplementary.

## Numbers

- Tenant-selectable Arabic-Indic or Western digits.
- Risk formulas, financial amounts and scientific units retain unambiguous formatting.
- Currency includes `SAR` or `ر.س` and thousands separators.
- Percentages and decimal direction are tested in RTL.

## Saudi organisational patterns

- Support formal delegation, acting roles and committee membership terms.
- Decision screens show Arabic name, job title and effective authority.
- Organisation structures support sectors, departments, branches and legal entities.
- Public portals minimise identity exposure and provide Saudi contact/channel expectations.
- Notifications account for local working calendars and configurable Ramadan/Eid schedules without embedding religious/calendar assumptions in code.

## Search

Arabic search must handle:

- alef and hamza variants;
- ya/alef maqsura;
- ta marbuta;
- optional diacritics;
- Arabic/Western digits;
- bilingual synonyms and acronyms;
- control and regulation identifiers.

Search normalization is for retrieval only. Original evidence text is never altered.

## Forms and validation

- Arabic field appears first in Arabic mode.
- Pair bilingual fields visually and show completeness status.
- Error messages name the field and corrective action.
- Required markers are announced to assistive technology.
- Avoid placeholders as labels.
- Long Arabic rationale uses comfortable line length and leading.

## Exports

- Arabic PDF uses embedded licensed fonts and correct shaping.
- Tables and headers use RTL correctly.
- Every export states language, generation time, tenant, scope, filters and data freshness.
- Bilingual board/regulator packs support parallel sections rather than unstable side-by-side squeezing.

## Terminology governance

The Product Council owns a versioned glossary. A translation change that alters regulatory meaning requires legal/SME review and release notes.

## Test matrix

Test on Arabic Windows/Chrome/Edge, common mobile viewport for crisis/public journeys, keyboard-only use, NVDA Arabic, 200% zoom, mixed-direction copy/paste, PDF/Excel export and printing.

