# Localization and Multilingual Storage Strategy
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines how Arabic and English text data is stored in SQL Server 2019 to support full bilingual functionality including proper collation, sorting, and full-text search.

---

## Database Collation

**Selected Collation:** `Latin1_General_100_CI_AS_SC_UTF8`

| Property | Value | Meaning |
|----------|-------|---------|
| CI | Case Insensitive | Searches are case-insensitive |
| AS | Accent Sensitive | Arabic diacritics (harakat) differentiated |
| SC | Supplementary Characters | Emoji and extended Unicode supported |
| UTF8 | UTF-8 encoding | Efficient storage; Arabic characters stored correctly |

Set at database creation:
```sql
CREATE DATABASE TSS_GRC
COLLATE Latin1_General_100_CI_AS_SC_UTF8;
```

All NVARCHAR columns inherit this collation automatically.

---

## Text Storage Approach

### Approach 1: Column Pairs (Primary — for structured/master data)

Each text field has two columns: `_en` and `_ar` suffix.

```sql
name_en         NVARCHAR(200)  NOT NULL,
name_ar         NVARCHAR(200)  NULL,      -- NULL allowed if not yet translated
description_en  NVARCHAR(MAX)  NULL,
description_ar  NVARCHAR(MAX)  NULL,
```

**Used for:** organizations, business_units, roles, permissions, modules, controls, lookup_values, risk_categories, workflow_definitions, report_definitions, notification_templates.

**Application resolution:**
```java
public String getLocalizedName(String locale) {
    if ("ar".equals(locale) && nameAr != null && !nameAr.isBlank()) {
        return nameAr;
    }
    return nameEn; // fallback to English
}
```

**Pros:** Simple queries, no joins, easy reporting
**Cons:** Adding a 3rd language requires schema migration

### Approach 2: Translation Table (Supplementary — for user-generated content)

`platform.translations` table stores dynamic translations.

```sql
CREATE TABLE platform.translations (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    entity_type     NVARCHAR(100) NOT NULL,
    entity_id       BIGINT        NOT NULL,
    field_name      NVARCHAR(100) NOT NULL,
    locale          NVARCHAR(10)  NOT NULL,  -- 'en', 'ar'
    translated_text NVARCHAR(MAX) NOT NULL,
    created_by      BIGINT        NULL,
    created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_translations UNIQUE (entity_type, entity_id, field_name, locale)
);
```

**Used for:** User-entered rich text fields (risk descriptions, finding notes, recommendation text) where on-the-fly translation may be added later.

---

## Data Type Rules

| Type | SQL Type | Notes |
|------|---------|-------|
| Short text (names, titles) | `NVARCHAR(200)` | Max 200 chars, indexed |
| Medium text (descriptions) | `NVARCHAR(1000)` | |
| Long text (body, notes) | `NVARCHAR(MAX)` | Up to 2GB; not indexable for full-text without FTS |
| Codes/identifiers | `NVARCHAR(50)` | Avoid VARCHAR — use NVARCHAR even for ASCII codes |
| Fixed locale code | `NVARCHAR(10)` | 'en', 'ar', 'en-US', 'ar-SA' |

**Rule:** NEVER use `VARCHAR` — always use `NVARCHAR` for all text columns without exception.

---

## Date and Calendar

- All dates stored in **Gregorian calendar** as `DATETIME2` or `DATE`
- Hijri calendar display is handled at the **application/UI layer** using Java `HijrahDate`
- User preference for Hijri vs Gregorian stored in `iam.users.preferred_locale`
- Frontend JavaScript library (e.g., Intl.DateTimeFormat) formats dates per locale

---

## Number and Currency Formatting

- Numbers stored as `DECIMAL(18,4)` or `BIGINT` — no formatting in database
- Display formatting (Arabic-Indic numerals, SAR currency symbol) handled at UI layer
- Java: `NumberFormat.getInstance(new Locale("ar", "SA"))`

---

## Full-Text Search Readiness

SQL Server 2019 **Express Edition** does **not** include Full-Text Search (FTS) catalog.

For search functionality in Phase 1:
- Use `LIKE N'%' + @searchTerm + N'%'` for simple search (acceptable for < 100K records)
- Use `CONTAINS` only if upgrading to SQL Server Standard/Enterprise in future
- Design search queries to use indexed prefix searches where possible: `LIKE @searchTerm + N'%'`

Application-level search using Spring Data JPA Specification:
```java
Specification<Risk> titleContains(String term) {
    return (root, query, cb) ->
        cb.or(
            cb.like(root.get("titleEn"), "%" + term + "%"),
            cb.like(root.get("titleAr"), "%" + term + "%")
        );
}
```

---

## Arabic Text in Reports/Exports

| Format | Arabic Support |
|--------|---------------|
| Excel (.xlsx) | Automatic — Excel handles RTL natively |
| PDF | Requires embedded Arabic font (Amiri or Noto Naskh Arabic) + Bidi text support via OpenPDF |
| Word (.docx) | Apache POI supports RTL paragraph direction via `CTPPrChange` |

Arabic font files (`.ttf`) included in `/src/main/resources/fonts/` for PDF generation.
