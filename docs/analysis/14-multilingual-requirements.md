# 14. Multilingual and Localization Requirements

## Document Information

| Field | Value |
|-------|-------|
| Document Title | TSS GRC Platform - Multilingual and Localization Requirements |
| Version | 1.0 |
| Classification | Internal |
| Last Updated | 2026-04-05 |

## 1. Overview

The TSS GRC Platform is designed for the Saudi Arabian market and must provide full bilingual support for Arabic (ar-SA) and English (en-US). This document defines all requirements related to language support, text direction, character encoding, date/number formatting, and the technical approach for implementing internationalization (i18n) and localization (l10n) across all layers of the application.

### Design Principles

1. **Arabic-First**: Arabic is the primary language for the target market. All features must work correctly in Arabic before English is considered complete.
2. **No Hard-Coded Strings**: All user-visible text must come from externalized resource bundles or database-stored translations.
3. **Consistent Experience**: Switching language must not degrade functionality, break layouts, or lose user context.
4. **Separation of Concerns**: Translation management is separate from application logic.

---

## 2. Language Support

### 2.1 Supported Locales

| Locale Code | Language | Script | Direction | Status |
|-------------|----------|--------|-----------|--------|
| `ar-SA` | Arabic (Saudi Arabia) | Arabic | RTL | Primary |
| `en-US` | English (United States) | Latin | LTR | Primary |

### 2.2 Locale Resolution Strategy

The platform determines the active locale using the following priority chain:

1. **Explicit user preference** (stored in `admin.users.preferred_locale` column)
2. **Session attribute** (set during locale switch, persists for the session duration)
3. **Organization default** (stored in `admin.organizations.default_locale` column)
4. **System default** (configured in `application.yml`, default: `ar-SA`)

### 2.3 Locale Switching Mechanism

| Aspect | Specification |
|--------|---------------|
| **UI Control** | A language toggle (flag icon or language abbreviation: ع / EN) is displayed in the global header bar, visible on every page including the login page. |
| **Switching Action** | Clicking the toggle sends a request to `/api/locale/switch?lang={locale}` which updates the session locale and (if authenticated) the user's stored preference. |
| **Page Behavior** | After switching, the current page reloads with the new locale applied. Form data in progress is not guaranteed to be preserved (user is warned if a form has unsaved changes). |
| **Spring Implementation** | `LocaleChangeInterceptor` intercepts the `lang` request parameter. `SessionLocaleResolver` stores the active locale in the HTTP session. A custom `LocaleChangeHandler` persists the preference to the database for authenticated users. |
| **Login Page** | The login page supports locale switching without authentication. The selected locale is carried into the authenticated session. |

---

## 3. RTL/LTR Layout Support

### 3.1 HTML Direction Attribute

The `<html>` element's `dir` and `lang` attributes are set dynamically based on the active locale:

```
Arabic:  <html dir="rtl" lang="ar">
English: <html dir="ltr" lang="en">
```

This is rendered by the Thymeleaf layout template using the active locale from the Spring context.

### 3.2 CSS Strategy for Bidirectional Layout

| Approach | Details |
|----------|---------|
| **CSS Logical Properties** | Use `margin-inline-start`, `padding-inline-end`, `border-inline-start`, `inset-inline-start` instead of physical `left`/`right` properties wherever possible. This provides automatic RTL mirroring without duplicate CSS rules. |
| **Bootstrap RTL** | Bootstrap 5 includes built-in RTL support. The platform loads `bootstrap.rtl.min.css` when the locale is Arabic and `bootstrap.min.css` when English. Thymeleaf conditionally includes the appropriate stylesheet. |
| **Custom CSS Overrides** | A `rtl-overrides.css` file contains RTL-specific adjustments for custom components that cannot be handled by logical properties alone. This file is loaded only in RTL mode. |
| **Icon Mirroring** | Directional icons (arrows, chevrons, navigation icons) are mirrored in RTL using CSS `transform: scaleX(-1)` or by using dedicated RTL icon variants. Non-directional icons (save, delete, settings) are not mirrored. |
| **Text Alignment** | Default text alignment follows the document direction (right-aligned in RTL, left-aligned in LTR). Explicit alignment overrides use logical values (`start`/`end`) rather than `left`/`right`. |

### 3.3 Layout Component Mirroring

| Component | LTR Behavior | RTL Behavior |
|-----------|-------------|-------------|
| **Sidebar Navigation** | Left side of the screen | Right side of the screen |
| **Breadcrumbs** | Left-to-right with `>` separators | Right-to-left with `<` separators |
| **Form Labels** | Left-aligned, before input | Right-aligned, before input (visual right) |
| **Data Tables** | Left-aligned columns (default) | Right-aligned columns (default) |
| **Pagination** | Previous (left), Next (right) | Previous (right), Next (left) |
| **Action Buttons** | Right-aligned (Submit, Cancel) | Left-aligned (Submit, Cancel) |
| **Progress Bars** | Fill from left to right | Fill from right to left |
| **Toasts/Notifications** | Appear from top-right | Appear from top-left |
| **Dropdown Menus** | Open to the right | Open to the left |

### 3.4 Mixed-Direction Content

| Scenario | Handling |
|----------|----------|
| **Arabic text with English terms** | Handled by Unicode Bidirectional Algorithm (Bidi). No special markup needed for inline foreign terms. |
| **English field values in Arabic form** | Input fields use `dir="auto"` to automatically detect content direction. |
| **Code or technical identifiers** | Wrapped in `<bdi>` or `<span dir="ltr">` to prevent Bidi reordering of technical strings (e.g., email addresses, URLs, reference numbers). |
| **Numeric values** | Numeric values in Arabic context are displayed as Western Arabic numerals (0-9) by default, with future support for Eastern Arabic numerals (see Section 7). |

---

## 4. Unicode Requirements

### 4.1 Database Layer

| Requirement | Specification |
|-------------|---------------|
| **Character Data Types** | All text columns use `nvarchar(n)` or `nvarchar(MAX)` to support Unicode (UCS-2/UTF-16 encoding in SQL Server). `varchar` is only used for ASCII-only system columns (e.g., `status_code`, `locale_code`). |
| **Collation** | Database default collation: `Arabic_CI_AS` for Arabic-aware sorting and comparison. Alternative: `Latin1_General_100_CI_AS_SC_UTF8` for UTF-8 native support with supplementary character awareness. Collation is set at database creation and documented. |
| **String Length** | Column sizes account for the fact that `nvarchar` characters may require 2-4 bytes. Application-level validation matches database column size constraints. |
| **Full-Text Search** | SQL Server full-text indexes use the Arabic word breaker for Arabic content and the English word breaker for English content. Language-specific stoplists are configured. |

### 4.2 Application Layer

| Requirement | Specification |
|-------------|---------------|
| **JVM Encoding** | JVM runs with `-Dfile.encoding=UTF-8` to ensure consistent character handling. |
| **HTTP Responses** | All HTTP responses include `Content-Type: text/html; charset=UTF-8` (or appropriate MIME type with UTF-8 charset). |
| **JDBC Connection** | SQL Server JDBC driver inherently supports Unicode with `nvarchar`. The connection string includes `sendStringParametersAsUnicode=true` (default). |
| **File I/O** | All file read/write operations explicitly specify UTF-8 encoding. CSV imports/exports use UTF-8 with BOM for Excel compatibility. |
| **Logging** | Log files use UTF-8 encoding to correctly record Arabic text in log messages. |
| **JSON Responses** | REST API responses use UTF-8 encoding. Arabic characters are not escaped in JSON output (Jackson configured with `JsonGenerator.Feature.WRITE_UTF8` default). |

---

## 5. Translation Strategy

### 5.1 Static Labels (Message Bundles)

Static text (UI labels, button text, menu items, validation messages, system messages) is managed through Spring MessageSource properties files.

| Aspect | Specification |
|--------|---------------|
| **File Structure** | `messages.properties` (default/fallback, English), `messages_ar.properties` (Arabic), `messages_en.properties` (English). |
| **Key Convention** | Hierarchical dot notation: `{module}.{page}.{element}.{attribute}`. Example: `risk.register.form.title`, `common.button.save`, `validation.field.required`. |
| **Thymeleaf Usage** | `th:text="#{risk.register.form.title}"` for text content. `th:placeholder="#{common.search.placeholder}"` for attributes. |
| **Organization** | One main bundle for common/shared labels. Module-specific bundles: `messages-risk.properties`, `messages-compliance.properties`, etc. |
| **Fallback** | If a key is missing in the locale-specific file, the default file (`messages.properties`) is used. Missing keys are logged at WARN level in development. |
| **Encoding** | Properties files use UTF-8 encoding (Spring Boot default in 2.x+, no native2ascii required). |

**Example Message Bundle Structure:**

```
# messages_en.properties
common.app.title=TSS GRC Platform
common.button.save=Save
common.button.cancel=Cancel
common.button.delete=Delete
common.button.export=Export
common.table.actions=Actions
common.table.nodata=No records found
common.validation.required={0} is required
common.validation.maxlength={0} must not exceed {1} characters

risk.register.title=Risk Register
risk.register.form.title=Risk Details
risk.register.field.name=Risk Name
risk.register.field.description=Description
risk.register.field.category=Risk Category
risk.register.field.likelihood=Likelihood
risk.register.field.impact=Impact
```

```
# messages_ar.properties
common.app.title=منصة TSS لإدارة المخاطر والامتثال
common.button.save=حفظ
common.button.cancel=إلغاء
common.button.delete=حذف
common.button.export=تصدير
common.table.actions=الإجراءات
common.table.nodata=لا توجد سجلات
common.validation.required={0} مطلوب
common.validation.maxlength={0} يجب ألا يتجاوز {1} حرفاً

risk.register.title=سجل المخاطر
risk.register.form.title=تفاصيل المخاطر
risk.register.field.name=اسم المخاطر
risk.register.field.description=الوصف
risk.register.field.category=فئة المخاطر
risk.register.field.likelihood=الاحتمالية
risk.register.field.impact=الأثر
```

### 5.2 Dynamic Content (Database Translations)

Dynamic content (user-created data such as risk names, control descriptions, policy titles) supports bilingual storage through paired columns.

| Aspect | Specification |
|--------|---------------|
| **Column Naming Convention** | Translatable text columns have `_en` and `_ar` suffixes: `name_en`, `name_ar`, `description_en`, `description_ar`. |
| **Display Logic** | The service layer returns the value matching the active locale. If the active locale's value is null or empty, the other locale's value is returned as fallback. |
| **Form Input** | Create/edit forms present both language fields (tabbed or side-by-side). The primary locale field is required; the secondary locale field is optional but encouraged. |
| **Entity Design** | JPA entities include both `_en` and `_ar` fields. A `@Transient` method or `getLocalizedName(Locale locale)` returns the appropriate value. |
| **Search** | Search queries match against both `_en` and `_ar` columns using OR conditions. |
| **Sorting** | List views sort by the column matching the active locale. |

**Example Entity Pattern:**

```
Database columns:
  name_en NVARCHAR(200) NOT NULL,
  name_ar NVARCHAR(200) NULL,
  description_en NVARCHAR(MAX) NULL,
  description_ar NVARCHAR(MAX) NULL

JPA Entity:
  @Column(name = "name_en", nullable = false, length = 200)
  private String nameEn;

  @Column(name = "name_ar", length = 200)
  private String nameAr;

  @Transient
  public String getDisplayName(String locale) {
      if ("ar".equals(locale)) {
          return (nameAr != null && !nameAr.isEmpty()) ? nameAr : nameEn;
      }
      return (nameEn != null && !nameEn.isEmpty()) ? nameEn : nameAr;
  }
```

### 5.3 Reference/Lookup Data

Lookup tables (categories, statuses, rating scales, likelihood levels) use the same `_en`/`_ar` column pattern.

| Table Example | Columns |
|---------------|---------|
| `core.risk_categories` | `id`, `code`, `name_en`, `name_ar`, `description_en`, `description_ar`, `sort_order`, `is_active` |
| `core.likelihood_levels` | `id`, `code`, `name_en`, `name_ar`, `value`, `color_code`, `sort_order` |
| `core.impact_levels` | `id`, `code`, `name_en`, `name_ar`, `value`, `color_code`, `sort_order` |

Lookup data is seeded with both English and Arabic values during initial deployment.

---

## 6. Date and Calendar Support

### 6.1 Date Storage

| Aspect | Specification |
|--------|---------------|
| **Storage Format** | All dates are stored as Gregorian dates using SQL Server `date` (date-only) or `datetime2(0)` (date-time) types. |
| **Timezone** | Date-time values are stored in UTC. The application converts to/from the user's timezone (default: `Asia/Riyadh`, AST/UTC+3) for display. |
| **JPA Mapping** | Java `LocalDate` for date-only fields, `LocalDateTime` for date-time fields. `ZonedDateTime` is used at the controller layer for timezone-aware input/output. |

### 6.2 Date Display Formatting

| Locale | Short Format | Medium Format | Long Format |
|--------|-------------|---------------|-------------|
| `en-US` | `MM/dd/yyyy` | `MMM dd, yyyy` | `MMMM dd, yyyy` |
| `ar-SA` | `dd/MM/yyyy` | `dd MMM yyyy` | `dd MMMM yyyy` |

Date formatting uses `java.time.format.DateTimeFormatter` with locale-specific patterns defined in application configuration.

### 6.3 Hijri Calendar Readiness

| Aspect | Specification |
|--------|---------------|
| **Current Scope** | Hijri calendar display is not in the initial release scope but the architecture supports future addition. |
| **Conversion Approach** | Java `java.time.chrono.HijrahChronology` provides built-in Hijri-Gregorian conversion. |
| **Display Strategy** | When enabled, Hijri dates will be displayed alongside Gregorian dates (e.g., "15/09/1447 H (14/03/2026)"). |
| **Date Picker** | Date picker component will support a Hijri mode toggle. A JavaScript Hijri date library (e.g., moment-hijri or a custom implementation) will handle client-side conversion. |
| **Storage** | Dates continue to be stored as Gregorian. Hijri representation is computed at display time only. |
| **Configuration** | Hijri display is controlled by a feature toggle (`feature.hijri_calendar.enabled`) and a user preference. |

### 6.4 Date Picker Component

| Requirement | Specification |
|-------------|---------------|
| **Library** | A date picker that supports RTL layout and Arabic month/day names (e.g., Flatpickr with Arabic locale, or Bootstrap Datepicker with RTL). |
| **Locale Awareness** | Month names, day names, and first day of week adjust to the active locale. Saudi week starts on Sunday. |
| **Manual Input** | Users can type dates manually in the locale-appropriate format. Input parsing handles both formats gracefully. |
| **Range Selection** | Date range pickers (for report filters, etc.) support RTL start-to-end visual flow. |

---

## 7. Number and Currency Formatting

### 7.1 Number Formatting

| Locale | Decimal Separator | Thousands Separator | Example |
|--------|-------------------|---------------------|---------|
| `en-US` | `.` (period) | `,` (comma) | `1,234,567.89` |
| `ar-SA` | `٫` (Arabic decimal) or `.` | `٬` (Arabic thousands) or `,` | `١٬٢٣٤٬٥٦٧٫٨٩` or `1,234,567.89` |

**Implementation Note:** The default Arabic number display uses Western Arabic numerals (0-9) which are widely understood in Saudi Arabia. Support for Eastern Arabic numerals (Arabic-Indic: ٠١٢٣٤٥٦٧٨٩) is a configurable option controlled by `feature.arabic_indic_numerals.enabled`.

Number formatting uses `java.text.NumberFormat` with the active locale.

### 7.2 Currency Formatting

| Aspect | Specification |
|--------|---------------|
| **Default Currency** | Saudi Riyal (SAR / ر.س) |
| **English Display** | `SAR 1,234.56` or `1,234.56 SAR` |
| **Arabic Display** | `١٬٢٣٤٫٥٦ ر.س` or `1,234.56 ر.س` |
| **Decimal Precision** | 2 decimal places for currency values |
| **Storage** | Currency amounts stored as `DECIMAL(18,2)` in SQL Server |
| **Formatting** | `java.text.NumberFormat.getCurrencyInstance(locale)` with SAR currency code |

### 7.3 Percentage Formatting

| Locale | Format | Example |
|--------|--------|---------|
| `en-US` | `n%` | `75.5%` |
| `ar-SA` | `%n` or `n%` | `%٧٥٫٥` or `75.5%` |

---

## 8. Thymeleaf i18n Integration

### 8.1 Template Internationalization

| Feature | Implementation |
|---------|----------------|
| **Text Content** | `th:text="#{message.key}"` for localized text output. |
| **Attribute Values** | `th:placeholder="#{placeholder.key}"`, `th:title="#{tooltip.key}"` for localized attributes. |
| **Parameterized Messages** | `th:text="#{message.key(${param1}, ${param2})}"` for messages with dynamic values. |
| **Conditional by Locale** | `th:if="${#locale.language == 'ar'}"` for locale-specific rendering. |
| **Direction Attribute** | `th:attr="dir=${#locale.language == 'ar' ? 'rtl' : 'ltr'}"` on the `<html>` element. |
| **CSS Conditional Loading** | Thymeleaf fragment conditionally loads RTL or LTR stylesheets based on locale. |
| **Number Formatting** | `th:text="${#numbers.formatDecimal(value, 0, 'COMMA', 2, 'POINT')}"` or custom format utility. |
| **Date Formatting** | `th:text="${#temporals.format(date, 'dd/MM/yyyy')}"` with locale-aware patterns. |

### 8.2 Layout Template Structure

The base layout template (`layout.html`) handles all locale-dependent configurations:

```
<!-- Pseudocode for layout structure -->
<html th:attr="dir=${#locale.language == 'ar' ? 'rtl' : 'ltr'},
               lang=${#locale.language}">
<head>
    <!-- RTL stylesheet loaded conditionally -->
    <link th:if="${#locale.language == 'ar'}"
          rel="stylesheet" href="/css/bootstrap.rtl.min.css"/>
    <link th:if="${#locale.language != 'ar'}"
          rel="stylesheet" href="/css/bootstrap.min.css"/>
    <!-- RTL overrides for custom styles -->
    <link th:if="${#locale.language == 'ar'}"
          rel="stylesheet" href="/css/rtl-overrides.css"/>
</head>
<body>
    <!-- Language toggle in header -->
    <!-- Page content -->
    <!-- JavaScript locale variable for client-side formatting -->
    <script th:inline="javascript">
        var APP_LOCALE = [[${#locale.language}]];
        var APP_DIR = [[${#locale.language == 'ar' ? 'rtl' : 'ltr'}]];
    </script>
</body>
</html>
```

### 8.3 JavaScript Localization

| Aspect | Specification |
|--------|---------------|
| **Locale Variable** | A global JavaScript variable `APP_LOCALE` is set from the server-side locale for client-side formatting decisions. |
| **Client-Side Messages** | Frequently used client-side messages (validation errors, confirmation dialogs) are output as a JSON object in the page template, populated from server-side message bundles. |
| **DataTables** | DataTables library is configured with locale-specific language packs (Arabic/English) for pagination, search, and info text. |
| **Chart Labels** | Chart.js or equivalent renders labels and tooltips in the active locale. |
| **Date Picker** | Date picker locale is set from `APP_LOCALE` on initialization. |

---

## 9. Database Storage Specifications

### 9.1 Column Type Standards

| Column Purpose | SQL Server Type | Notes |
|----------------|----------------|-------|
| User-entered text (short) | `nvarchar(n)` | n = max expected length. Common sizes: 100, 200, 500. |
| User-entered text (long) | `nvarchar(MAX)` | For descriptions, notes, rich text content. |
| System codes | `varchar(50)` | ASCII-only identifiers: status codes, type codes, locale codes. |
| Email addresses | `nvarchar(255)` | Unicode to support international domain names. |
| File names | `nvarchar(500)` | Unicode to support Arabic file names. |
| Translatable name (English) | `nvarchar(200)` | Paired with `_ar` column. |
| Translatable name (Arabic) | `nvarchar(200)` | Paired with `_en` column. |
| Translatable description (English) | `nvarchar(MAX)` | Paired with `_ar` column. |
| Translatable description (Arabic) | `nvarchar(MAX)` | Paired with `_en` column. |

### 9.2 Locale-Aware Collation

| Aspect | Specification |
|--------|---------------|
| **Database Collation** | `Arabic_CI_AS` (Arabic, Case-Insensitive, Accent-Sensitive) as the database default. |
| **Column-Level Override** | Specific columns requiring different collation behavior can specify collation at the column level. |
| **Sorting Behavior** | Arabic characters sort according to Arabic linguistic rules. English characters sort using standard Latin order. Mixed-language sorting follows the database collation rules. |
| **Comparison Behavior** | String comparisons are case-insensitive by default (CI). Accent marks are distinguished (AS). |
| **LIKE Queries** | `LIKE` patterns work correctly with Arabic text under the configured collation. Wildcard `%` matches Arabic character sequences. |

### 9.3 Full-Text Search Configuration

| Aspect | Specification |
|--------|---------------|
| **Catalog** | A full-text catalog is created for searchable entities. |
| **Language** | Full-text indexes specify `LANGUAGE 'Arabic'` for Arabic columns and `LANGUAGE 'English'` for English columns. |
| **Stoplists** | Custom stoplists are configured for Arabic (common Arabic stop words) and English. |
| **Search Queries** | Application search uses `CONTAINS` or `FREETEXT` predicates across both `_en` and `_ar` columns. |

---

## 10. PDF/Excel Export RTL Support

### 10.1 PDF Export

| Aspect | Specification |
|--------|---------------|
| **Library** | Flying Saucer (xhtmlrenderer) with iText for PDF generation from Thymeleaf-rendered HTML, or Apache PDFBox for template-based generation. |
| **Arabic Font** | Embedded Arabic font (e.g., Amiri, Noto Sans Arabic) bundled with the application. Font supports Arabic ligatures, diacritics, and shaping. |
| **RTL Layout** | PDF templates use the same RTL CSS as web pages. Flying Saucer respects `direction: rtl` CSS property. |
| **Mixed Direction** | Documents with both Arabic and English text use appropriate Bidi handling in the PDF renderer. |
| **Headers/Footers** | Report headers and footers adjust alignment based on the active locale (organization name right-aligned in RTL). |
| **Tables** | Table columns are ordered RTL when the locale is Arabic (first column on the right). |

### 10.2 Excel Export

| Aspect | Specification |
|--------|---------------|
| **Library** | Apache POI (XSSF for .xlsx format). |
| **Sheet Direction** | Worksheet direction is set to RTL for Arabic exports using `sheet.setRightToLeft(true)`. |
| **Cell Alignment** | Default cell alignment follows the sheet direction. |
| **Arabic Font** | Cell styles use an Arabic-compatible font (e.g., Arial, Tahoma, or Calibri which support Arabic). |
| **Column Order** | Column order is reversed in RTL mode (first data column appears on the right). |
| **Header Row** | Column headers are set in the active locale language. |
| **Encoding** | UTF-8 BOM is included in CSV exports for proper Arabic display in Excel. |

### 10.3 Word Export

| Aspect | Specification |
|--------|---------------|
| **Library** | Apache POI (XWPF for .docx format) or docx4j. |
| **Document Direction** | Document-level RTL setting for Arabic exports. |
| **Paragraph Direction** | Each paragraph has `bidi` attribute set according to content direction. |
| **Arabic Font** | Styles use Arabic-compatible fonts with proper font embedding. |

---

## 11. Email Templates

### 11.1 Bilingual Notification Strategy

| Aspect | Specification |
|--------|---------------|
| **Template Engine** | Thymeleaf is used for email template rendering with the same message bundle infrastructure as web pages. |
| **Recipient Locale** | Email locale is determined by the recipient user's stored locale preference. |
| **Template Files** | Each email notification type has two template files: `{template}_en.html` and `{template}_ar.html`. Alternatively, a single template uses Thymeleaf locale switching. |
| **Email Direction** | HTML emails include `dir="rtl"` for Arabic recipients and `dir="ltr"` for English recipients. |
| **Subject Line** | Email subjects are localized using the message bundle. |
| **Fallback** | If the recipient's preferred locale template is not available, the system default locale template is used. |

### 11.2 Email Template Types

| Template | Description | Locale-Specific Content |
|----------|-------------|------------------------|
| `welcome` | New user welcome email | Greeting, instructions, login link |
| `password-reset` | Password reset link | Instructions, security notice |
| `task-assigned` | Workflow task assignment notification | Task details, action link, due date |
| `task-reminder` | Overdue task reminder | Task details, escalation warning |
| `approval-request` | Approval workflow notification | Item details, approve/reject links |
| `status-change` | Entity status change notification | Entity details, old/new status |
| `report-ready` | Async report generation complete | Report name, download link |

### 11.3 Email Content Structure

```
Email Structure:
├── Organization logo (from branding config)
├── Email header (localized title)
├── Body content (localized, parameterized)
├── Action button (localized label, e.g., "View Details" / "عرض التفاصيل")
├── Footer (organization name, no-reply notice, localized)
└── Direction: RTL for Arabic, LTR for English
```

---

## 12. Validation Messages

### 12.1 Localized Validation Errors

| Aspect | Specification |
|--------|---------------|
| **Bean Validation** | JSR 380 (Bean Validation 2.0) annotations use message keys: `@NotBlank(message = "{validation.field.required}")`. |
| **Message Interpolation** | `ValidationMessages.properties` and `ValidationMessages_ar.properties` provide localized validation messages. |
| **Field Name Substitution** | Validation messages include the field's localized label: "Risk Name is required" / "اسم المخاطر مطلوب". |
| **Client-Side Messages** | JavaScript validation messages are loaded from the same message source, output as a JSON object in the page. |
| **Error Display** | Validation errors are displayed below the relevant form field, following the text direction of the active locale. |
| **Summary Errors** | A validation summary at the top of the form lists all errors, localized and linking to the relevant field. |

### 12.2 Standard Validation Message Keys

| Key | English | Arabic |
|-----|---------|--------|
| `validation.required` | `{0} is required` | `{0} مطلوب` |
| `validation.maxlength` | `{0} must not exceed {1} characters` | `{0} يجب ألا يتجاوز {1} حرفاً` |
| `validation.minlength` | `{0} must be at least {1} characters` | `{0} يجب أن يكون على الأقل {1} أحرف` |
| `validation.email` | `Please enter a valid email address` | `يرجى إدخال عنوان بريد إلكتروني صحيح` |
| `validation.number` | `Please enter a valid number` | `يرجى إدخال رقم صحيح` |
| `validation.date` | `Please enter a valid date` | `يرجى إدخال تاريخ صحيح` |
| `validation.range` | `{0} must be between {1} and {2}` | `{0} يجب أن يكون بين {1} و {2}` |
| `validation.unique` | `{0} already exists` | `{0} موجود بالفعل` |
| `validation.filesize` | `File size must not exceed {0}` | `حجم الملف يجب ألا يتجاوز {0}` |
| `validation.filetype` | `File type not allowed. Allowed types: {0}` | `نوع الملف غير مسموح. الأنواع المسموحة: {0}` |

---

## 13. User Locale Preference

### 13.1 Database Storage

| Column | Table | Type | Description |
|--------|-------|------|-------------|
| `preferred_locale` | `admin.users` | `varchar(10)` | User's preferred locale code (e.g., `ar-SA`, `en-US`). |
| `preferred_timezone` | `admin.users` | `varchar(50)` | User's preferred timezone (default: `Asia/Riyadh`). |
| `preferred_date_format` | `admin.users` | `varchar(20)` | Override date format (null = use locale default). |
| `preferred_number_format` | `admin.users` | `varchar(20)` | Number format preference: `western` or `arabic_indic`. |

### 13.2 Preference Management

| Aspect | Specification |
|--------|---------------|
| **Settings Page** | User profile/settings page includes a "Language & Regional Settings" section. |
| **Available Options** | Language: Arabic, English. Timezone: selectable from a list (default: Asia/Riyadh). Date format: locale default or explicit format selection. |
| **Immediate Effect** | Language change takes effect immediately (page reload). Other regional settings take effect on next page load. |
| **Admin Override** | Organization administrators can set the default locale for their organization. This applies to new users and users who have not set a preference. |

---

## 14. Translation Management Process

### 14.1 Workflow for Adding New Translatable Content

| Step | Action | Responsibility |
|------|--------|---------------|
| 1 | Developer adds new message key to `messages_en.properties` with English text. | Developer |
| 2 | Developer adds the same key to `messages_ar.properties` with a placeholder or machine-translated value. | Developer |
| 3 | Translation task is created for the Arabic text. | Project Manager |
| 4 | Translator provides the Arabic translation. | Translator |
| 5 | Arabic translation is updated in `messages_ar.properties`. | Developer |
| 6 | Both translations are reviewed in context (UI review). | QA |

### 14.2 Translation Completeness Tracking

| Aspect | Specification |
|--------|---------------|
| **Completeness Check** | A build-time check verifies that all keys in `messages_en.properties` exist in `messages_ar.properties` and vice versa. |
| **Missing Key Behavior** | Missing keys fall back to the default locale file. In development, missing keys are logged at WARN level. |
| **Coverage Report** | A translation coverage report can be generated showing the percentage of translated keys per module. |

---

## 15. Testing Requirements for Localization

### 15.1 Automated Testing

| Test Type | Scope | Tool |
|-----------|-------|------|
| **Message Key Completeness** | All keys present in both locale files | Custom Maven plugin or unit test |
| **RTL Layout Regression** | Visual comparison of key pages in RTL vs LTR | Screenshot comparison (BackstopJS or similar) |
| **Unicode Data Round-Trip** | Arabic text persisted and retrieved correctly | Integration tests with Arabic test data |
| **Date Formatting** | Dates display correctly per locale | Unit tests for date formatting utilities |
| **Number Formatting** | Numbers and currency display correctly per locale | Unit tests for number formatting utilities |
| **Export RTL** | PDF and Excel exports render Arabic correctly | Integration tests generating test exports |

### 15.2 Manual Testing Checklist

| Area | Verification |
|------|-------------|
| Layout mirroring | Sidebar, navigation, forms mirror correctly in RTL |
| Text alignment | All text aligns appropriately in both directions |
| Form functionality | Forms work correctly with Arabic input |
| Search | Search returns results for Arabic queries |
| Sorting | List views sort Arabic text correctly |
| Data tables | Tables display correctly in RTL with proper column alignment |
| Modals and dialogs | Modal positioning and content direction are correct |
| Notifications | Toast messages appear in correct position and direction |
| Print/export | Printed and exported content maintains correct direction |
| Mixed content | Pages with both Arabic and English content display correctly |

---

## 16. Implementation Summary

| Layer | Technology | i18n Mechanism |
|-------|-----------|---------------|
| **Presentation** | Thymeleaf | `#{message.key}`, conditional CSS, `dir` attribute |
| **Controller** | Spring MVC | `LocaleChangeInterceptor`, `SessionLocaleResolver` |
| **Service** | Spring | `MessageSource` injection for programmatic message access |
| **Validation** | Bean Validation | `ValidationMessages_xx.properties` |
| **Database** | SQL Server | `nvarchar` columns, `_en`/`_ar` pairs, Arabic collation |
| **Export (PDF)** | Flying Saucer | Arabic font embedding, RTL CSS |
| **Export (Excel)** | Apache POI | `setRightToLeft(true)`, Arabic font styles |
| **Email** | Thymeleaf | Locale-specific templates, `dir` in HTML email |
| **JavaScript** | Client-side | `APP_LOCALE` variable, localized message JSON object |
