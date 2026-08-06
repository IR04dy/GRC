# 15 - Configuration and Branding Model
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines how the TSS GRC platform manages system configuration, per-organization settings, feature toggles, and visual branding. The configuration system must support multiple deployment models (SaaS and on-premises), runtime changes without restarts, and per-organization customization.

---

## 1. Configuration Architecture

### 1.1 Configuration Hierarchy

**Decision**: Three-level hierarchy with cascading overrides.

```
System Defaults (lowest priority)
  → Organization Overrides
    → User Preferences (highest priority)
```

**Rationale**: System defaults provide sensible out-of-the-box behavior. Organizations override settings to match their policies (password expiry, session timeout, branding). Users override presentational preferences (language, date format, items per page). This pattern is standard for multi-tenant SaaS and avoids forcing every organization to configure every setting.

**Resolution logic**:

```java
@Service
public class ConfigurationService {

    public String getValue(String key) {
        Long orgId = TenantContextHolder.get().orgId();
        Long userId = SecurityContextHelper.getCurrentUserId();

        // 1. User preference (highest priority)
        Optional<String> userPref = userPreferenceRepository
            .findByUserIdAndKey(userId, key);
        if (userPref.isPresent()) return userPref.get();

        // 2. Organization override
        Optional<String> orgConfig = orgConfigRepository
            .findByOrgIdAndKey(orgId, key);
        if (orgConfig.isPresent()) return orgConfig.get();

        // 3. System default
        return systemConfigRepository.findByKey(key)
            .map(SystemConfiguration::getValue)
            .orElseThrow(() -> new ConfigurationException("Missing config: " + key));
    }
}
```

### 1.2 Configuration Categories

| Category | Level | Examples |
|---|---|---|
| Security | System / Org | Password policy, session timeout, lockout threshold |
| Integration | Org | LDAP settings, SMTP settings |
| Modules | Org | Enabled modules, feature toggles |
| Workflows | Org | Approval requirements, notification triggers |
| Branding | Org | Logo, colors, application title |
| Display | Org / User | Default language, date format, items per page, timezone |
| Risk | Org | Likelihood scale, impact dimensions, matrix thresholds |
| Compliance | Org | Assessment scoring method, control effectiveness scale |

---

## 2. Database Schema

### 2.1 System Configuration

```sql
CREATE TABLE admin.system_configurations (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    config_key      NVARCHAR(200) NOT NULL UNIQUE,
    config_value    NVARCHAR(MAX) NOT NULL,    -- Plain value or JSON
    value_type      NVARCHAR(20) NOT NULL,     -- STRING, INTEGER, BOOLEAN, JSON, ENCRYPTED
    category        NVARCHAR(50) NOT NULL,     -- SECURITY, DISPLAY, MODULE, etc.
    description_en  NVARCHAR(500),
    description_ar  NVARCHAR(500),
    is_editable     BIT NOT NULL DEFAULT 1,    -- Some configs are read-only
    is_sensitive    BIT NOT NULL DEFAULT 0,    -- Encrypted values, masked in UI
    validation_rule NVARCHAR(500),             -- Regex or JSON schema for validation
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_by      BIGINT,
    rowversion      ROWVERSION
);

-- Seed examples
INSERT INTO admin.system_configurations (config_key, config_value, value_type, category, description_en) VALUES
('security.password.min_length', '12', 'INTEGER', 'SECURITY', 'Minimum password length'),
('security.password.require_uppercase', 'true', 'BOOLEAN', 'SECURITY', 'Require uppercase letter in password'),
('security.password.expiry_days', '90', 'INTEGER', 'SECURITY', 'Password expiry in days (0 = disabled)'),
('security.session.idle_timeout_minutes', '30', 'INTEGER', 'SECURITY', 'Session idle timeout in minutes'),
('security.session.max_concurrent', '1', 'INTEGER', 'SECURITY', 'Maximum concurrent sessions per user'),
('security.lockout.max_attempts', '5', 'INTEGER', 'SECURITY', 'Failed login attempts before lockout'),
('security.lockout.duration_minutes', '30', 'INTEGER', 'SECURITY', 'Account lockout duration in minutes'),
('display.default_language', 'ar', 'STRING', 'DISPLAY', 'Default language (ar/en)'),
('display.default_page_size', '20', 'INTEGER', 'DISPLAY', 'Default items per page'),
('display.date_format', 'yyyy-MM-dd', 'STRING', 'DISPLAY', 'Default date format'),
('risk.matrix_size', '5', 'INTEGER', 'RISK', 'Risk matrix dimensions (e.g., 5 = 5x5)');
```

### 2.2 Organization Configuration

```sql
CREATE TABLE admin.org_configurations (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES admin.organizations(id),
    config_key      NVARCHAR(200) NOT NULL,
    config_value    NVARCHAR(MAX) NOT NULL,
    value_type      NVARCHAR(20) NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_by      BIGINT,
    rowversion      ROWVERSION,
    CONSTRAINT uq_org_config UNIQUE (org_id, config_key)
);

CREATE INDEX ix_org_config_org ON admin.org_configurations(org_id);
```

### 2.3 User Preferences

```sql
CREATE TABLE admin.user_preferences (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES iam.users(id),
    pref_key        NVARCHAR(200) NOT NULL,
    pref_value      NVARCHAR(MAX) NOT NULL,
    updated_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    CONSTRAINT uq_user_pref UNIQUE (user_id, pref_key)
);

CREATE INDEX ix_user_pref_user ON admin.user_preferences(user_id);
```

---

## 3. Feature Toggles

### 3.1 Module-Level Activation

Controlled by the `licensed_modules` JSON array on the organization record:

```json
["erm", "compliance", "bcm", "audit", "reporting", "data_governance"]
```

**Enforcement points**:
- Navigation menu rendering (Thymeleaf `th:if`)
- URL access security (Spring Security filter)
- API endpoint access (method-level check)

### 3.2 Feature-Level Toggles

Sub-module features can be toggled independently via organization configuration:

```sql
-- Enable/disable specific features within a module
INSERT INTO admin.org_configurations (org_id, config_key, config_value, value_type) VALUES
(1, 'feature.erm.risk_quantification', 'false', 'BOOLEAN'),
(1, 'feature.erm.bow_tie_analysis', 'true', 'BOOLEAN'),
(1, 'feature.compliance.auto_scoring', 'true', 'BOOLEAN'),
(1, 'feature.bcm.exercise_management', 'false', 'BOOLEAN'),
(1, 'feature.audit.automated_sampling', 'false', 'BOOLEAN');
```

**Implementation**:

```java
@Component("featureToggle")
public class FeatureToggleChecker {

    private final ConfigurationService configService;

    public boolean isEnabled(String featureKey) {
        return configService.getBooleanValue("feature." + featureKey, false);
    }
}
```

```html
<!-- Thymeleaf usage -->
<div th:if="${@featureToggle.isEnabled('erm.bow_tie_analysis')}">
    <!-- Bow-tie analysis UI -->
</div>
```

---

## 4. Branding

### 4.1 Branding Elements

| Element | Storage | Default |
|---|---|---|
| Organization logo | File storage, URL in `admin.organizations.logo_url` | TSS logo |
| Primary color | `admin.organizations.primary_color` (hex) | #1a56db (TSS blue) |
| Secondary color | Org config: `branding.secondary_color` | Derived from primary |
| Application title | Org config: `branding.app_title` | "TSS GRC Platform" |
| Application title (AR) | Org config: `branding.app_title_ar` | "منصة حوكمة المخاطر والامتثال" |
| Favicon | File storage, org config: `branding.favicon_url` | TSS favicon |
| Login page background | Org config: `branding.login_background_url` | Default gradient |

### 4.2 CSS Custom Properties

**Decision**: CSS custom properties (variables) for theming, overridable per organization.

**Rationale**: CSS custom properties cascade naturally, are supported by all modern browsers, and can be overridden at any level. This allows a single CSS bundle to serve all organizations with per-org color customization injected at runtime via a `<style>` block.

**Default theme** (in `styles/theme.css`):

```css
:root {
    /* Primary palette */
    --tss-primary: #1a56db;
    --tss-primary-hover: #1e429f;
    --tss-primary-light: #e1effe;
    --tss-primary-dark: #1e3a8a;

    /* Secondary palette */
    --tss-secondary: #6b7280;
    --tss-secondary-hover: #4b5563;

    /* Status colors */
    --tss-success: #059669;
    --tss-warning: #d97706;
    --tss-danger: #dc2626;
    --tss-info: #2563eb;

    /* Risk level colors */
    --tss-risk-very-high: #dc2626;
    --tss-risk-high: #ea580c;
    --tss-risk-medium: #d97706;
    --tss-risk-low: #16a34a;
    --tss-risk-very-low: #059669;

    /* Layout */
    --tss-sidebar-bg: #111827;
    --tss-sidebar-text: #d1d5db;
    --tss-header-bg: #ffffff;
    --tss-body-bg: #f3f4f6;
    --tss-card-bg: #ffffff;

    /* Typography */
    --tss-font-family-en: 'Inter', sans-serif;
    --tss-font-family-ar: 'Noto Sans Arabic', sans-serif;
    --tss-font-size-base: 0.875rem;

    /* Borders and shadows */
    --tss-border-color: #e5e7eb;
    --tss-border-radius: 0.5rem;
    --tss-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}
```

**Per-organization override** (injected in layout template):

```html
<head>
    <link rel="stylesheet" th:href="@{/css/theme.css}"/>
    <!-- Organization-specific overrides -->
    <style th:if="${orgBranding != null}">
        :root {
            --tss-primary: [[${orgBranding.primaryColor}]];
            --tss-primary-hover: [[${orgBranding.primaryHoverColor}]];
            --tss-primary-light: [[${orgBranding.primaryLightColor}]];
        }
    </style>
</head>
```

### 4.3 Logo Management

```java
@PostMapping("/admin/branding/logo")
@PreAuthorize("hasPermission('admin:config:update')")
public String uploadLogo(@RequestParam("logo") MultipartFile file,
                          RedirectAttributes redirect) {
    // Validate: PNG/JPG, max 500KB, max 400x200px
    brandingService.validateLogo(file);
    String logoUrl = fileStorageService.store(file, "branding/logos");
    organizationService.updateLogo(TenantContextHolder.get().orgId(), logoUrl);
    redirect.addFlashAttribute("success", "Logo updated");
    return "redirect:/admin/branding";
}
```

---

## 5. Runtime Configuration

### 5.1 Change Without Restart

**Decision**: Configuration changes stored in the database take effect without application restart, with cache-based refresh.

**Rationale**: GRC platform administrators need to adjust settings (session timeout, password policy, branding) without downtime. Database-backed configuration with short-lived cache enables this.

### 5.2 Caching Strategy

**Decision**: Caffeine cache with time-based expiry for configuration values.

```java
@Configuration
public class ConfigCacheConfig {

    @Bean
    public Cache<String, String> systemConfigCache() {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    }

    @Bean
    public Cache<String, String> orgConfigCache() {
        return Caffeine.newBuilder()
            .maximumSize(2000)          // ~200 keys per org * 10 orgs
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    }
}
```

**Cache key format**: `{org_id}:{config_key}` for org-level, `{config_key}` for system-level.

### 5.3 Explicit Cache Invalidation

When an admin saves a configuration change, the cache is explicitly invalidated:

```java
@Service
public class ConfigurationService {

    public void updateOrgConfig(String key, String value) {
        OrgConfiguration config = orgConfigRepository
            .findByOrgIdAndKey(TenantContextHolder.get().orgId(), key)
            .orElse(new OrgConfiguration());
        config.setConfigValue(value);
        orgConfigRepository.save(config);

        // Invalidate cache for this key
        orgConfigCache.invalidate(TenantContextHolder.get().orgId() + ":" + key);

        // Audit log
        auditService.log(AuditAction.CONFIG_UPDATED, "config_key", key);
    }
}
```

---

## 6. Configuration UI

### 6.1 Admin Settings Interface

The platform provides a settings interface organized by category:

```
/admin/settings
├── /security         → Password policy, session, lockout
├── /integration      → LDAP, SMTP configuration
├── /branding         → Logo, colors, titles
├── /modules          → Enable/disable modules and features
├── /workflows        → Approval settings, notification preferences
├── /localization     → Language, date/number formats
└── /risk-settings    → Risk matrix, scales, categories
```

### 6.2 Settings Form Pattern

```java
@Controller
@RequestMapping("/admin/settings/security")
@PreAuthorize("hasPermission('admin:config:update')")
public class SecuritySettingsController {

    @GetMapping
    public String show(Model model) {
        model.addAttribute("settings", configService.getSecuritySettings());
        return "admin/settings/security";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute SecuritySettingsForm form,
                        BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) return "admin/settings/security";

        configService.updateSecuritySettings(form);
        redirect.addFlashAttribute("success", true);
        return "redirect:/admin/settings/security";
    }
}
```

### 6.3 Sensitive Configuration Display

Settings marked as `is_sensitive = true` are:
- Displayed as masked values (`****`) in the UI
- Only writable, not readable through the settings API
- Stored encrypted in the database (see [13-security-architecture.md](13-security-architecture.md))
- Changes logged in audit trail without revealing the value

---

## 7. Environment-Specific Configuration

### 7.1 Spring Profiles

**Decision**: Spring profiles for environment-specific configuration.

| Profile | Purpose | Key Differences |
|---|---|---|
| `dev` | Local development | H2 or local SQL Server, debug logging, Swagger enabled, relaxed security |
| `test` | Automated testing | In-memory database, mock external services |
| `staging` | Pre-production | Production-like config, Swagger enabled, test data |
| `prod` | Production | Strict security, Swagger disabled, optimized logging, TLS required |

### 7.2 Profile-Specific Properties

```yaml
# application.yml (shared)
spring:
  application:
    name: tss-grc

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=tss_dev;encrypt=false
  thymeleaf:
    cache: false
  jpa:
    show-sql: true

springdoc:
  swagger-ui:
    enabled: true

logging:
  level:
    com.tss: DEBUG
    org.springframework.security: DEBUG

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=${DB_NAME};encrypt=true;trustServerCertificate=false
  thymeleaf:
    cache: true
  jpa:
    show-sql: false

springdoc:
  swagger-ui:
    enabled: false

management:
  endpoints:
    web:
      exposure:
        include: health,metrics

logging:
  level:
    root: WARN
    com.tss: INFO
```

---

## 8. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Config storage | Database tables | Property files, Spring Cloud Config | Runtime-editable without restart, per-org support, admin UI |
| Config hierarchy | System → Org → User | Flat, two-level | Covers all deployment models with sensible defaults |
| Caching | Caffeine with 5-min TTL | No cache, Redis | Fast local cache; 5-min TTL balances freshness with performance |
| Theming | CSS custom properties | Sass compilation, theme CSS files | Runtime-overridable, no build step, single CSS bundle |
| Feature toggles | Database config keys | LaunchDarkly, FF4j | No external dependency; sufficient for module/feature toggles |
| Environment config | Spring profiles | Custom env detection | Spring Boot native, well-documented, widely understood |
| Sensitive config | Encrypted in DB + env vars | Vault only | Works on-prem without Vault; Vault-ready for SaaS |
