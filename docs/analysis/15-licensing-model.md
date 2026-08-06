# 15. Licensing Model
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Document Information

| Field | Value |
|-------|-------|
| Document Title | TSS GRC Platform - Modular Licensing Model |
| Version | 1.0 |
| Classification | Internal - Confidential |
| Last Updated | 2026-04-05 |

## 1. Overview

The TSS GRC Platform uses a modular licensing model that allows customers to purchase and activate only the GRC modules they need. This document defines the licensing structure, enforcement mechanisms, validation approach, and database design for the licensing subsystem.

### Licensing Objectives

1. **Modular Flexibility**: Customers purchase only the modules they need and can expand over time.
2. **Predictable Revenue**: Subscription-based model with clear tier differentiation.
3. **Offline Capability**: License validation works without internet connectivity for on-premises deployments.
4. **Transparent Enforcement**: Clear user messaging when license limits are reached or features are unavailable.
5. **Auditability**: License usage is tracked for compliance and capacity planning.

---

## 2. Licensable Modules

### 2.1 Core Platform (Always Included)

The Core Platform is included with every license and provides foundational services that all modules depend on.

| Component | Description |
|-----------|-------------|
| **User Management** | User accounts, roles, permissions, authentication |
| **Organization Management** | Organization hierarchy, business units |
| **System Administration** | System settings, configuration, branding |
| **Workflow Engine** | Approval workflows, task management, notifications |
| **Reporting Engine** | Report generation framework, templates, export |
| **Dashboard Framework** | Dashboard layout, widget framework, chart rendering |
| **Audit Trail** | Audit logging, audit log viewer |
| **Notification Service** | Email notifications, in-app notifications |
| **Document Management** | File upload, storage, attachment management |
| **Master Data** | Lookup tables, reference data management |

### 2.2 GRC Modules (Independently Licensable)

| Module Code | Module Name | Description |
|-------------|-------------|-------------|
| `ERM` | Enterprise Risk Management | Risk register, risk assessments, risk treatment plans, risk heatmaps |
| `CYBER` | Cyber Risk Management | Cyber risk assessments, NCA ECC compliance, cyber threat tracking |
| `COMPLIANCE` | Compliance Management | Regulatory framework mapping, compliance assessments, gap analysis |
| `AUDIT` | Internal Audit Management | Audit planning, audit execution, findings tracking, audit reports |
| `POLICY` | Policy Management | Policy lifecycle, policy acknowledgment, version control |
| `INCIDENT` | Incident Management | Incident reporting, investigation, corrective actions |
| `BCP` | Business Continuity Planning | BIA, BCP plans, recovery strategies, testing schedules |
| `VENDOR` | Vendor Risk Management | Vendor assessments, due diligence, contract risk tracking |
| `ESG` | ESG & Sustainability | ESG metrics, sustainability reporting, Vision 2030 alignment |
| `STRATEGY` | Strategy & Performance | Strategic objectives, KPIs, balanced scorecard, OKRs |

### 2.3 Module Dependencies

```
Core Platform (always included)
├── Workflow Engine ──────── Used by: All modules
├── Reporting Engine ─────── Used by: All modules
├── Dashboard Framework ──── Used by: All modules
├── Notification Service ─── Used by: All modules
└── Audit Trail ──────────── Used by: All modules

Module Dependencies:
├── ERM: Standalone (no module dependencies)
├── CYBER: Standalone (optional integration with ERM)
├── COMPLIANCE: Standalone (optional integration with ERM)
├── AUDIT: Standalone (optional integration with ERM, COMPLIANCE)
├── POLICY: Standalone (optional integration with COMPLIANCE)
├── INCIDENT: Standalone (optional integration with ERM)
├── BCP: Standalone (optional integration with ERM)
├── VENDOR: Standalone (optional integration with ERM, COMPLIANCE)
├── ESG: Standalone (optional integration with STRATEGY)
└── STRATEGY: Standalone (no module dependencies)
```

**Dependency Rules:**
- Each GRC module can operate independently with only the Core Platform.
- Cross-module integrations (e.g., linking risks to controls, linking audit findings to risks) are activated only when both related modules are licensed.
- Cross-module navigation links and dashboard widgets for unlicensed modules are hidden from the UI.

---

## 3. License Types

### 3.1 User-Based License Types

| License Type | Description | Use Case |
|--------------|-------------|----------|
| **Named User** | License assigned to a specific named user. Each user who accesses the system requires a named license. Cannot be shared between users. | Organizations with a defined, stable set of GRC users. |
| **Concurrent User** | License based on the maximum number of simultaneous active sessions. Users share a pool of licenses; any user can access the system as long as a license is available. | Organizations with a large user base where not all users access the system simultaneously. |
| **Enterprise (Unlimited)** | Unlimited users within the licensed organization. No per-user restrictions. | Large enterprises with broad GRC participation across departments. |

### 3.2 License Type Comparison

| Aspect | Named User | Concurrent User | Enterprise |
|--------|-----------|----------------|------------|
| **User Limit** | Fixed count of named users | Fixed count of simultaneous sessions | Unlimited |
| **User Assignment** | Each license is assigned to a specific user | Any user can use an available license | No assignment needed |
| **Cost Efficiency** | Lower per-user cost for dedicated users | Lower total cost if usage is distributed | Highest total cost, lowest per-user cost at scale |
| **Tracking** | Track assigned vs. available licenses | Track active sessions vs. license count | Track total users for capacity planning |
| **Over-Limit Behavior** | New user creation blocked when limit reached | Login queued or denied when all concurrent licenses in use | No limit enforcement |

---

## 4. Subscription Tiers

### 4.1 Tier Definitions

| Tier | Target Market | Module Access | User Limit | Features |
|------|--------------|---------------|------------|----------|
| **Essential** | Small to mid-size organizations starting their GRC journey | Up to 2 GRC modules | Up to 25 named users or 10 concurrent users | Standard reporting, basic dashboards, email notifications, single organization |
| **Professional** | Mid-size to large organizations with established GRC programs | Up to 5 GRC modules | Up to 100 named users or 50 concurrent users | Advanced reporting, custom dashboards, workflow customization, multi-business-unit support, API access |
| **Enterprise** | Large enterprises and government entities with comprehensive GRC needs | All GRC modules (10) | Unlimited users | All Professional features plus: multi-organization support, advanced analytics, custom integrations, dedicated support, SLA guarantees, white-labeling options |

### 4.2 Tier Feature Matrix

| Feature | Essential | Professional | Enterprise |
|---------|-----------|-------------|------------|
| GRC Modules | Up to 2 | Up to 5 | All 10 |
| Named Users | 25 | 100 | Unlimited |
| Organizations | 1 | 1 | Multiple |
| Business Units | Up to 5 | Up to 20 | Unlimited |
| Custom Workflows | Basic (2-step) | Advanced (multi-step, parallel) | Advanced + custom scripts |
| Report Templates | Standard templates | Standard + custom | Standard + custom + scheduled |
| Dashboard Widgets | Standard widgets | Standard + custom | Standard + custom + embedded |
| API Access | No | Yes (rate-limited) | Yes (higher rate limits) |
| Data Export | PDF, Excel | PDF, Excel, Word | PDF, Excel, Word + bulk export |
| Email Notifications | Basic | Advanced (templates, scheduling) | Advanced + SMS readiness |
| Audit Log Retention | 1 year | 3 years | 7 years (configurable) |
| Support Level | Standard (email) | Priority (email + phone) | Dedicated account manager |
| Branding Customization | Logo only | Logo + colors | Full white-label |
| SSO Integration | No | SAML 2.0 | SAML 2.0 + OAuth 2.0 + LDAP |

---

## 5. Feature Gating Mechanism

### 5.1 Feature Gate Architecture

Feature gating is implemented at three levels:

| Level | Mechanism | Description |
|-------|-----------|-------------|
| **Module Level** | Module activation flag | Entire module is enabled or disabled. Controls navigation menu visibility, URL access, and API availability. |
| **Feature Level** | Feature toggle per module | Individual features within a module are enabled or disabled based on subscription tier. |
| **Limit Level** | Numeric limit check | Quantitative limits (user count, business unit count, report frequency) are enforced at runtime. |

### 5.2 Module Activation Flow

```
1. Application starts
2. LicenseService loads active license from database
3. For each module:
   a. Check if module is included in the active license
   b. If licensed: register module's Spring beans, register navigation entries, enable API endpoints
   c. If not licensed: skip module registration, navigation entries are not created, API endpoints return 403
4. ModuleRegistry maintains the list of active modules
5. UI templates check ModuleRegistry to conditionally render cross-module links
```

### 5.3 Feature Toggle Integration

```
Feature Check Flow:
1. UI element renders → Thymeleaf checks: featureService.isEnabled('MODULE.FEATURE')
2. Controller endpoint called → @PreAuthorize checks: featureService.isEnabled('MODULE.FEATURE')
3. Service method called → Programmatic check: featureService.requireEnabled('MODULE.FEATURE')

Feature Resolution:
1. Check admin.license_features for the module+feature combination
2. If explicitly enabled/disabled → use that value
3. If not found → check the subscription tier's default feature set
4. If still not found → default to disabled (fail-safe)
```

### 5.4 User Limit Enforcement

| License Type | Enforcement Point | Behavior |
|--------------|-------------------|----------|
| **Named User** | User creation / activation | When the named user limit is reached, new user creation is blocked. Admin receives a warning at 90% capacity. Deactivated users do not count toward the limit. |
| **Concurrent User** | Login authentication | When all concurrent licenses are in use, new login attempts are denied with a message indicating the system is at capacity. Active sessions are counted by unexpired session records. |
| **Enterprise** | No enforcement | User creation is unrestricted. Usage is tracked for reporting only. |

---

## 6. License Validation Approach

### 6.1 License Key Structure

The license is represented as a digitally signed JSON payload encoded in Base64.

| Field | Type | Description |
|-------|------|-------------|
| `licenseId` | UUID | Unique license identifier |
| `customerId` | UUID | Customer/tenant identifier |
| `customerName` | String | Customer organization name |
| `productCode` | String | `TSS-GRC` |
| `productVersion` | String | Major version compatibility (e.g., `1.x`) |
| `tier` | Enum | `ESSENTIAL`, `PROFESSIONAL`, `ENTERPRISE` |
| `licenseType` | Enum | `NAMED_USER`, `CONCURRENT_USER`, `ENTERPRISE` |
| `userLimit` | Integer | Maximum users (null for unlimited) |
| `modules` | String[] | Array of licensed module codes (e.g., `["ERM", "COMPLIANCE", "AUDIT"]`) |
| `issuedAt` | DateTime | License issuance date |
| `validFrom` | Date | License validity start date |
| `validUntil` | Date | License expiration date |
| `gracePeriodDays` | Integer | Grace period after expiration (default: 30) |
| `features` | Map | Feature-level overrides (key: feature code, value: enabled/disabled) |
| `signature` | String | RSA-2048 digital signature of the license payload |

### 6.2 Offline Validation (On-Premises)

| Aspect | Specification |
|--------|---------------|
| **Signing** | License payload is signed by TSS using an RSA-2048 private key. The application contains the corresponding public key. |
| **Validation** | On startup and periodically (daily), the application validates the license signature, expiration date, and product version compatibility. |
| **No Phone-Home** | On-premises deployments do not require internet connectivity for license validation. All validation is performed locally using the embedded public key. |
| **Tampering Protection** | License data is stored encrypted in the database. The signature prevents modification of license terms. |
| **Clock Skew** | The application uses server system time for expiration checks. Significant backward clock changes (> 24 hours) trigger a license warning. |

### 6.3 License Installation Process

```
1. TSS generates a license key (signed JSON payload, Base64 encoded)
2. Customer receives the license key (email, portal download, or USB delivery)
3. Administrator navigates to System Administration → License Management
4. Administrator pastes or uploads the license key
5. Application validates the signature and payload
6. If valid: license is stored in admin.licenses table, modules are activated
7. If invalid: error message indicates the specific issue (expired, wrong product, tampered)
8. Application restarts module activation based on the new license
```

---

## 7. Grace Period Handling

### 7.1 Expiration Timeline

```
Timeline:
├── License Valid Period ─────────────────────────────────────────┤
│   Full functionality. No warnings.                              │
├── 30 days before expiration ───────────────────────────────────┤
│   Warning banner in UI. Admin email notification weekly.        │
├── 14 days before expiration ───────────────────────────────────┤
│   Warning banner in UI. Admin email notification daily.         │
├── License Expiration Date ─────────────────────────────────────┤
│   Grace period starts. Warning banner (non-dismissible).        │
│   Functionality unchanged during grace period.                  │
├── Grace Period (default: 30 days) ─────────────────────────────┤
│   Full functionality maintained.                                │
│   Persistent warning banner. Daily admin emails.                │
│   Audit log records grace period usage.                         │
├── Grace Period Expiration ─────────────────────────────────────┤
│   System enters read-only mode.                                 │
│   Users can view data but cannot create or modify records.      │
│   Admin can still manage license (to apply renewal).            │
│   Export functionality remains available for data portability.   │
└── 90 days post-expiration ─────────────────────────────────────┤
    No further changes. System remains in read-only mode.
    Data is preserved. No automatic data deletion.
```

### 7.2 Grace Period Behavior

| Phase | UI Behavior | Functional Behavior | Notifications |
|-------|-------------|--------------------|----|
| **Pre-Expiration (30 days)** | Dismissible yellow warning banner for admins | Full functionality | Weekly email to license admin |
| **Pre-Expiration (14 days)** | Dismissible orange warning banner for admins | Full functionality | Daily email to license admin |
| **Grace Period** | Non-dismissible red banner for all users | Full functionality | Daily email to license admin, weekly email to all admins |
| **Post-Grace (Read-Only)** | Blocking red banner on all pages | Read-only mode: view, search, export only. Create/edit/delete disabled. | Daily email to license admin |

---

## 8. License Tracking and Usage Reporting

### 8.1 Usage Metrics Collected

| Metric | Description | Collection Frequency |
|--------|-------------|---------------------|
| `active_named_users` | Count of active (non-deactivated) user accounts | Daily snapshot |
| `peak_concurrent_sessions` | Maximum simultaneous active sessions in the period | Hourly peak, daily maximum |
| `module_usage_counts` | Number of active records per module | Daily snapshot |
| `login_count` | Total logins in the period | Aggregated daily |
| `report_generation_count` | Number of reports generated per module | Aggregated daily |
| `api_call_count` | Number of API calls per module (if API is licensed) | Aggregated hourly |
| `storage_usage_mb` | Total file storage used | Daily snapshot |

### 8.2 Usage Dashboard

| Feature | Description |
|---------|-------------|
| **License Overview** | Displays license type, tier, expiration date, modules licensed, user limit vs. current count. |
| **Usage Trends** | Charts showing user count, login activity, and module usage over time (30/60/90 days). |
| **Capacity Alerts** | Visual indicators when approaching user limits (80%, 90%, 100%). |
| **Module Utilization** | Per-module record counts and activity metrics to justify module value. |
| **Export** | Usage report exportable as PDF or Excel for internal compliance or procurement review. |

### 8.3 Database Tables for Usage Tracking

```sql
-- License usage snapshots
CREATE TABLE admin.license_usage_snapshots (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    snapshot_date            DATE NOT NULL,
    active_named_users       INT NOT NULL,
    peak_concurrent_sessions INT NOT NULL,
    total_logins             INT NOT NULL DEFAULT 0,
    storage_usage_mb         DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at               DATETIME2(0) NOT NULL DEFAULT GETUTCDATE()
);

-- Per-module usage snapshots
CREATE TABLE admin.module_usage_snapshots (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    snapshot_date            DATE NOT NULL,
    module_code              VARCHAR(20) NOT NULL,
    active_record_count      INT NOT NULL DEFAULT 0,
    reports_generated        INT NOT NULL DEFAULT 0,
    api_calls                INT NOT NULL DEFAULT 0,
    created_at               DATETIME2(0) NOT NULL DEFAULT GETUTCDATE()
);
```

---

## 9. Pricing Model Structure

### 9.1 Pricing Dimensions

> **Note:** This section defines the pricing model structure, not actual prices. Specific pricing is determined by the TSS commercial team.

| Dimension | Description |
|-----------|-------------|
| **Base Platform Fee** | Annual subscription for the Core Platform (included in all tiers). |
| **Per-Module Fee** | Annual subscription per activated GRC module. Pricing varies by module complexity. |
| **Per-User Fee** | Additional per-user charge above the tier's included user count. |
| **Tier Uplift** | Professional and Enterprise tiers include a percentage uplift over Essential for enhanced features. |
| **Implementation Fee** | One-time fee for initial deployment, configuration, and data migration (not licensing, but often bundled). |
| **Support Fee** | Annual support and maintenance fee (typically included in subscription or as a percentage of license value). |

### 9.2 Module Pricing Tiers

Modules are categorized by complexity for pricing purposes:

| Category | Modules | Relative Price Level |
|----------|---------|---------------------|
| **Standard** | Policy, Incident | Base module price |
| **Advanced** | ERM, Compliance, Audit, BCP | 1.5x base module price |
| **Premium** | Cyber Risk, Vendor Risk, ESG, Strategy | 2x base module price |

### 9.3 Discount Structures

| Discount Type | Description |
|---------------|-------------|
| **Bundle Discount** | Pre-defined bundles (see Section 10) receive a discount vs. individual module pricing. |
| **Volume Discount** | Discounts for purchasing 5+ modules or 100+ users. |
| **Multi-Year Discount** | Discounts for 2-year or 3-year subscription commitments. |
| **Government Discount** | Special pricing for Saudi government entities. |
| **Early Adopter** | Introductory pricing for initial customers. |

---

## 10. Bundle Options

### 10.1 Pre-Defined Bundles

| Bundle Name | Included Modules | Target Customer |
|-------------|-----------------|-----------------|
| **Risk Suite** | ERM + Cyber Risk Management + Incident Management | Organizations focused on risk management and cyber security |
| **Compliance Suite** | Compliance Management + Policy Management + Audit Management | Organizations focused on regulatory compliance and audit |
| **Operational Excellence Suite** | ERM + Compliance + Audit + Incident + Policy | Comprehensive operational GRC needs |
| **Full GRC Suite** | All 10 modules | Enterprise-wide GRC program implementation |
| **Vision 2030 Suite** | Strategy & Performance + ESG & Sustainability + ERM | Organizations aligning with Saudi Vision 2030 initiatives |

### 10.2 Bundle Pricing Principle

Bundles are priced at a 15-25% discount compared to the sum of individual module prices (exact discount determined by commercial team). Bundles are presented as a single line item in the license.

### 10.3 Bundle Customization

Customers can:
- Start with a bundle and add individual modules later.
- Start with individual modules and upgrade to a bundle (credit for existing module licenses).
- Mix bundles with individual modules (no double-licensing for overlapping modules).

---

## 11. Add-On Modules Concept

### 11.1 Add-On Types

Add-ons extend the functionality of licensed modules without being full GRC modules themselves.

| Add-On Code | Add-On Name | Prerequisite Module | Description |
|-------------|-------------|-------------------|-------------|
| `ADV-ANALYTICS` | Advanced Analytics | Any module | Advanced data analytics, trend analysis, predictive indicators |
| `INTEGRATION-HUB` | Integration Hub | Core Platform | Pre-built connectors for ERP, HR systems, ticketing systems |
| `MOBILE-APP` | Mobile Application | Any module | Native mobile app for task approvals, incident reporting, dashboards |
| `ADV-WORKFLOW` | Advanced Workflow | Core Platform | Complex workflow designer, conditional routing, SLA management |
| `DOC-MGMT-ADV` | Advanced Document Management | Core Platform | Version control, document collaboration, OCR, digital signatures |
| `REGULATORY-FEED` | Regulatory Update Feed | Compliance | Automated regulatory change tracking for Saudi regulations |
| `TRAINING` | Training & Awareness | Any module | Training module assignments, completion tracking, certification |

### 11.2 Add-On Licensing

- Add-ons are licensed separately from GRC modules.
- Add-ons require their prerequisite module(s) to be licensed.
- Add-ons follow the same subscription model (annual) and tier restrictions.
- Some add-ons are only available at Professional tier and above.

---

## 12. License Enforcement in Code

### 12.1 Spring-Based Module Activation

**Module Configuration Pattern:**

```
Each GRC module is a Spring @Configuration class annotated with
@ConditionalOnModuleLicensed("MODULE_CODE"). This custom condition
checks the LicenseService at application startup to determine if the
module should be activated.

If the module is not licensed:
- Its @Configuration class is not loaded
- Its Spring beans are not created
- Its controller endpoints are not registered
- Its Thymeleaf templates are not accessible
- Its navigation entries are not added to the menu

If the module is licensed:
- Full Spring context for the module is loaded
- All endpoints, services, and repositories are available
- Navigation entries appear in the menu
- Dashboard widgets for the module are registered
```

### 12.2 Key Service Interfaces

**LicenseService:**

| Method | Description |
|--------|-------------|
| `getLicenseInfo()` | Returns the current license details (tier, type, expiration, modules). |
| `isModuleLicensed(String moduleCode)` | Returns true if the specified module is included in the active license. |
| `isFeatureEnabled(String featureCode)` | Returns true if the specified feature is enabled (module licensed + tier permits + not toggled off). |
| `getUserLimit()` | Returns the maximum user count for the active license (null for unlimited). |
| `getCurrentUserCount()` | Returns the current active user count. |
| `isInGracePeriod()` | Returns true if the license has expired but is within the grace period. |
| `isReadOnlyMode()` | Returns true if the license has expired beyond the grace period. |
| `getLicenseWarnings()` | Returns a list of active license warnings (approaching expiry, capacity limits, grace period). |
| `validateLicenseKey(String licenseKey)` | Validates a new license key and returns the parsed license info or validation errors. |
| `activateLicense(String licenseKey)` | Activates a new license key, updating the database and refreshing module activation. |

**ModuleRegistry:**

| Method | Description |
|--------|-------------|
| `getActiveModules()` | Returns the list of currently active module codes. |
| `isModuleActive(String moduleCode)` | Returns true if the module is active (licensed and loaded). |
| `getModuleInfo(String moduleCode)` | Returns metadata about a module (name, version, description, status). |
| `getNavigationEntries()` | Returns the merged navigation entries from all active modules. |
| `getDashboardWidgets()` | Returns the dashboard widget definitions from all active modules. |

### 12.3 Security Integration

```
License-Aware Security Flow:

1. Authentication Filter:
   - Check if system is in read-only mode (post-grace-period)
   - If read-only: set a flag in the security context

2. Authorization:
   - @PreAuthorize expressions can reference license state
   - Example: @PreAuthorize("@licenseService.isModuleLicensed('ERM')")
   - Write operations check: @PreAuthorize("!@licenseService.isReadOnlyMode()")

3. Request Interceptor:
   - LicenseWarningInterceptor adds warning messages to the model
   - Warnings are displayed in the UI header
   - Read-only mode adds a model attribute that disables all form submissions

4. API Layer:
   - API requests to unlicensed modules return HTTP 403 with:
     { "error": "MODULE_NOT_LICENSED", "message": "...", "module": "ERM" }
   - API write requests in read-only mode return HTTP 403 with:
     { "error": "LICENSE_EXPIRED", "message": "..." }
```

---

## 13. Database Design

### 13.1 License Tables

```sql
-- Core license storage
CREATE TABLE admin.licenses (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_id          UNIQUEIDENTIFIER NOT NULL UNIQUE,
    customer_id         UNIQUEIDENTIFIER NOT NULL,
    customer_name       NVARCHAR(200) NOT NULL,
    product_code        VARCHAR(20) NOT NULL DEFAULT 'TSS-GRC',
    product_version     VARCHAR(10) NOT NULL,
    tier                VARCHAR(20) NOT NULL,           -- ESSENTIAL, PROFESSIONAL, ENTERPRISE
    license_type        VARCHAR(20) NOT NULL,           -- NAMED_USER, CONCURRENT_USER, ENTERPRISE
    user_limit          INT NULL,                       -- NULL = unlimited
    issued_at           DATETIME2(0) NOT NULL,
    valid_from          DATE NOT NULL,
    valid_until         DATE NOT NULL,
    grace_period_days   INT NOT NULL DEFAULT 30,
    license_key_hash    VARCHAR(64) NOT NULL,           -- SHA-256 hash of the license key
    license_data        NVARCHAR(MAX) NOT NULL,          -- Encrypted full license payload
    is_active           BIT NOT NULL DEFAULT 1,
    activated_at        DATETIME2(0) NULL,
    activated_by        BIGINT NULL,
    created_at          DATETIME2(0) NOT NULL DEFAULT GETUTCDATE(),
    updated_at          DATETIME2(0) NOT NULL DEFAULT GETUTCDATE(),

    CONSTRAINT CHK_license_tier CHECK (tier IN ('ESSENTIAL', 'PROFESSIONAL', 'ENTERPRISE')),
    CONSTRAINT CHK_license_type CHECK (license_type IN ('NAMED_USER', 'CONCURRENT_USER', 'ENTERPRISE'))
);

-- Licensed modules per license
CREATE TABLE admin.license_modules (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_id          BIGINT NOT NULL,
    module_code         VARCHAR(20) NOT NULL,
    is_active           BIT NOT NULL DEFAULT 1,
    activated_at        DATETIME2(0) NULL,

    CONSTRAINT FK_license_modules_license FOREIGN KEY (license_id) REFERENCES admin.licenses(id),
    CONSTRAINT UQ_license_module UNIQUE (license_id, module_code)
);

-- Feature-level license controls
CREATE TABLE admin.license_features (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_id          BIGINT NOT NULL,
    feature_code        VARCHAR(50) NOT NULL,           -- e.g., 'ERM.ADVANCED_ANALYTICS', 'CORE.API_ACCESS'
    is_enabled          BIT NOT NULL DEFAULT 1,
    limit_value         INT NULL,                       -- Numeric limit for quantitative features

    CONSTRAINT FK_license_features_license FOREIGN KEY (license_id) REFERENCES admin.licenses(id),
    CONSTRAINT UQ_license_feature UNIQUE (license_id, feature_code)
);

-- License activation history
CREATE TABLE admin.license_history (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_id          BIGINT NOT NULL,
    action              VARCHAR(20) NOT NULL,           -- ACTIVATED, DEACTIVATED, RENEWED, UPGRADED, EXPIRED
    action_details      NVARCHAR(MAX) NULL,
    performed_by        BIGINT NULL,
    performed_at        DATETIME2(0) NOT NULL DEFAULT GETUTCDATE(),

    CONSTRAINT FK_license_history_license FOREIGN KEY (license_id) REFERENCES admin.licenses(id)
);

-- Named user license assignments (for NAMED_USER type)
CREATE TABLE admin.license_user_assignments (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_id          BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    assigned_at         DATETIME2(0) NOT NULL DEFAULT GETUTCDATE(),
    assigned_by         BIGINT NOT NULL,
    revoked_at          DATETIME2(0) NULL,
    revoked_by          BIGINT NULL,

    CONSTRAINT FK_license_user_license FOREIGN KEY (license_id) REFERENCES admin.licenses(id),
    CONSTRAINT FK_license_user_user FOREIGN KEY (user_id) REFERENCES admin.users(id)
);
```

### 13.2 Feature Code Registry

| Feature Code | Description | Tier Availability |
|--------------|-------------|-------------------|
| `CORE.API_ACCESS` | REST API access for external integration | Professional, Enterprise |
| `CORE.SSO_SAML` | SAML 2.0 SSO integration | Professional, Enterprise |
| `CORE.SSO_OAUTH` | OAuth 2.0 SSO integration | Enterprise |
| `CORE.SSO_LDAP` | LDAP/AD integration | Enterprise |
| `CORE.MULTI_ORG` | Multi-organization support | Enterprise |
| `CORE.CUSTOM_WORKFLOWS` | Advanced workflow configuration | Professional, Enterprise |
| `CORE.SCHEDULED_REPORTS` | Automated report scheduling | Enterprise |
| `CORE.WHITE_LABEL` | Full branding customization | Enterprise |
| `CORE.BULK_EXPORT` | Bulk data export functionality | Professional, Enterprise |
| `*.CUSTOM_FIELDS` | Custom fields on module entities | Professional, Enterprise |
| `*.ADVANCED_DASHBOARDS` | Custom dashboard widgets | Professional, Enterprise |

---

## 14. License Status API

### 14.1 Internal API Endpoints

| Endpoint | Method | Description | Access |
|----------|--------|-------------|--------|
| `/api/v1/admin/license` | GET | Get current license summary | Admin role |
| `/api/v1/admin/license/details` | GET | Get detailed license info including modules and features | Admin role |
| `/api/v1/admin/license/activate` | POST | Activate a new license key | System Admin role |
| `/api/v1/admin/license/validate` | POST | Validate a license key without activating | Admin role |
| `/api/v1/admin/license/usage` | GET | Get current usage metrics | Admin role |
| `/api/v1/admin/license/usage/history` | GET | Get usage history for a date range | Admin role |
| `/api/v1/admin/license/modules` | GET | Get list of licensed modules with status | Authenticated user |
| `/api/v1/admin/license/warnings` | GET | Get active license warnings | Authenticated user |

### 14.2 License Status Response Structure

```json
{
    "licenseId": "uuid",
    "tier": "PROFESSIONAL",
    "licenseType": "NAMED_USER",
    "status": "ACTIVE",
    "validFrom": "2026-01-01",
    "validUntil": "2026-12-31",
    "daysRemaining": 271,
    "gracePeriodDays": 30,
    "userLimit": 100,
    "currentUserCount": 67,
    "userCapacityPercent": 67,
    "modules": [
        { "code": "ERM", "name": "Enterprise Risk Management", "active": true },
        { "code": "COMPLIANCE", "name": "Compliance Management", "active": true },
        { "code": "AUDIT", "name": "Internal Audit Management", "active": true }
    ],
    "warnings": [],
    "readOnlyMode": false
}
```

---

## 15. License Upgrade and Renewal Process

### 15.1 Upgrade Paths

| From | To | Process |
|------|-----|---------|
| Essential | Professional | New license key issued. Additional modules and features activated. No data migration needed. |
| Professional | Enterprise | New license key issued. All modules unlocked. Multi-org support enabled. |
| Named User | Concurrent User | New license key issued. User assignments cleared. Concurrent session tracking activated. |
| Add Module | (any tier) | Updated license key with additional module(s). New module navigation appears after activation. |

### 15.2 Renewal Process

```
1. License approaching expiration (30 days)
2. TSS account manager contacts customer for renewal
3. Renewal terms agreed (same or upgraded tier/modules)
4. New license key generated with updated validity dates
5. Customer administrator activates the new license key
6. Previous license is automatically deactivated
7. No service interruption (grace period bridges any gap)
8. License history records the renewal event
```

---

## 16. Implementation Considerations

### 16.1 Security of License Data

| Concern | Mitigation |
|---------|------------|
| **Key Tampering** | RSA-2048 digital signature prevents modification of license terms. |
| **Key Sharing** | License key is bound to `customerId`. The application validates this against the tenant configuration. |
| **Clock Manipulation** | Warning logged if system clock moves backward significantly. Future: NTP check on startup. |
| **Database Access** | License data is stored encrypted. Raw license key is not stored (only hash and encrypted payload). |
| **Reverse Engineering** | Public key in application code only allows verification, not key generation. Private key is held only by TSS. |

### 16.2 Performance Impact

| Aspect | Mitigation |
|--------|------------|
| **License Check Frequency** | License status is cached in memory with a 5-minute refresh cycle. Not checked on every request. |
| **Module Activation** | Module beans are conditionally loaded at startup. No runtime overhead for licensed module checks beyond cache lookups. |
| **Feature Toggle Checks** | Feature states are cached. Cache is refreshed on license change or on a scheduled interval. |
| **Usage Tracking** | Usage metrics are collected via scheduled jobs, not on each request. Counters use in-memory aggregation with periodic database flush. |
