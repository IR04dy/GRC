# 14 - Tenant and Organization Model
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines the multi-tenancy strategy and organizational hierarchy for the TSS GRC platform. It covers how tenant isolation is achieved, how the organization hierarchy structures data visibility, and how the platform evolves from single-tenant to multi-tenant deployment.

---

## 1. Tenancy Strategy

### 1.1 Phase 1: Single-Tenant Deployment

**Decision**: Phase 1 deploys as a single-tenant application, one installation per customer.

**Rationale**: On-premises government deployments require dedicated infrastructure by policy. Designing for multi-tenancy from day one while deploying single-tenant ensures the data model is ready for SaaS without incurring the operational complexity of shared infrastructure before it is needed.

**What this means**:
- Each customer gets a separate application instance and database
- The `org_id` column exists on all tables from the start (set to a single organization's ID)
- All queries include `org_id` filtering from day one
- No cross-tenant concerns to manage in Phase 1

### 1.2 Future: Multi-Tenant SaaS

**Decision**: Shared application instance with row-level data isolation using `org_id`.

**Rationale**: Schema-per-tenant and database-per-tenant approaches offer stronger isolation but create operational overhead (hundreds of schemas to migrate, connection pool management per tenant). Row-level isolation with `org_id` is sufficient for commercial SaaS customers, is simplest to operate, and is the most common pattern for B2B SaaS platforms.

| Approach | Isolation | Operational Cost | Chosen |
|---|---|---|---|
| Database-per-tenant | Strongest | High (separate backups, migrations, connections) | No |
| Schema-per-tenant | Strong | Medium (shared DB, separate schemas, per-schema migration) | No |
| Row-level (org_id) | Sufficient | Low (single schema, single migration, filtered queries) | Yes |

**Multi-tenant activation checklist** (for Phase 2+):
- Tenant-aware connection routing (optional, for large tenants requiring dedicated resources)
- Tenant provisioning API and admin workflow
- Per-tenant resource quotas (storage, user count)
- Tenant-specific backup extraction
- Cross-tenant reporting for TSS platform operations team

---

## 2. Organization Hierarchy

### 2.1 Three-Level Structure

**Decision**: Organization, Business Unit, Department. Three levels, fixed depth.

**Rationale**: Saudi enterprise and government organizations commonly structure into these three levels. A fixed three-level hierarchy is simpler to implement, query, and display than an arbitrary-depth tree. If a customer has fewer levels, they simply omit the unused level (e.g., no departments). If deeper nesting is needed, departments can represent the lowest relevant level.

```
Organization (Tenant)
├── Business Unit A
│   ├── Department A1
│   ├── Department A2
│   └── Department A3
├── Business Unit B
│   ├── Department B1
│   └── Department B2
└── Business Unit C
    └── Department C1
```

### 2.2 Database Schema

```sql
-- Organizations (tenants)
CREATE TABLE admin.organizations (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    code            NVARCHAR(50) NOT NULL UNIQUE,
    name_ar         NVARCHAR(300) NOT NULL,
    name_en         NVARCHAR(300) NOT NULL,
    logo_url        NVARCHAR(500),
    primary_color   NVARCHAR(7),           -- Hex color for branding
    status          NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, SUSPENDED, DEACTIVATED
    license_type    NVARCHAR(50),          -- BASIC, PROFESSIONAL, ENTERPRISE
    licensed_modules NVARCHAR(MAX),        -- JSON array of enabled module codes
    max_users       INT,
    subscription_start DATE,
    subscription_end   DATE,
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    rowversion      ROWVERSION
);

-- Business Units
CREATE TABLE admin.business_units (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES admin.organizations(id),
    code            NVARCHAR(50) NOT NULL,
    name_ar         NVARCHAR(300) NOT NULL,
    name_en         NVARCHAR(300) NOT NULL,
    description_ar  NVARCHAR(1000),
    description_en  NVARCHAR(1000),
    head_user_id    BIGINT,               -- BU head (user reference)
    is_active       BIT NOT NULL DEFAULT 1,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    rowversion      ROWVERSION,
    CONSTRAINT uq_bu_code_org UNIQUE (org_id, code)
);

-- Departments
CREATE TABLE admin.departments (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES admin.organizations(id),
    business_unit_id BIGINT NOT NULL REFERENCES admin.business_units(id),
    code            NVARCHAR(50) NOT NULL,
    name_ar         NVARCHAR(300) NOT NULL,
    name_en         NVARCHAR(300) NOT NULL,
    description_ar  NVARCHAR(1000),
    description_en  NVARCHAR(1000),
    head_user_id    BIGINT,               -- Department head (user reference)
    is_active       BIT NOT NULL DEFAULT 1,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    rowversion      ROWVERSION,
    CONSTRAINT uq_dept_code_org UNIQUE (org_id, code)
);

-- Indexes for hierarchy queries
CREATE INDEX ix_bu_org ON admin.business_units(org_id);
CREATE INDEX ix_dept_org_bu ON admin.departments(org_id, business_unit_id);
```

---

## 3. Data Scoping

### 3.1 org_id on All Tenant-Scoped Tables

**Decision**: Every table that contains tenant-specific data includes an `org_id` column with a foreign key to `admin.organizations`.

**Exceptions**: System-level reference data shared across all tenants (e.g., framework definitions like NCA ECC, ISO 27001 controls) lives in tables without `org_id`. Organization-specific customizations of these reference data use a separate table with `org_id`.

**Example**: Risk table scoping.

```sql
CREATE TABLE erm.risks (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES admin.organizations(id),
    business_unit_id BIGINT REFERENCES admin.business_units(id),
    department_id   BIGINT REFERENCES admin.departments(id),
    -- ... other columns ...
    CONSTRAINT fk_risk_org FOREIGN KEY (org_id) REFERENCES admin.organizations(id)
);

-- Filtered index for tenant queries
CREATE INDEX ix_risks_org ON erm.risks(org_id) INCLUDE (status, risk_level);
```

### 3.2 BU-Level Data Visibility

Users can be restricted to view data only within their assigned Business Unit(s).

**Visibility rules**:

| User Scope | Data Visible | Implementation |
|---|---|---|
| Org-wide role (BU = null) | All data in the organization | Query filters on `org_id` only |
| BU-scoped role | Data in assigned BU(s) only | Query filters on `org_id` AND `business_unit_id` |
| Department-scoped (future) | Data in assigned department only | Additional `department_id` filter |

### 3.3 Cross-BU Roles

Certain executive roles require visibility across all Business Units:

| Role | Scope | Use Case |
|---|---|---|
| CISO | All BUs, security modules | Organization-wide cybersecurity oversight |
| CRO | All BUs, risk modules | Enterprise risk management oversight |
| SUPER_ADMIN | All BUs, all modules | Platform administration |
| ORG_ADMIN | All BUs, admin module | Organization administration |

These roles are assigned with `business_unit_id = NULL` in the `iam.user_roles` table, indicating org-wide scope.

---

## 4. Tenant Context Enforcement

### 4.1 TenantContext in ThreadLocal

**Decision**: `TenantContext` stored in `ThreadLocal`, set by a servlet filter on every request, used by all repository queries.

**Rationale**: ThreadLocal provides request-scoped tenant context without passing `org_id` through every method signature. It is the standard pattern for tenant isolation in Java web applications and integrates with Spring's request lifecycle.

```java
public class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext get() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException("TenantContext not initialized for current request");
        }
        return ctx;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

public record TenantContext(
    Long orgId,
    List<Long> accessibleBusinessUnitIds,  // null = all BUs
    String orgCode
) {
    public boolean hasOrgWideAccess() {
        return accessibleBusinessUnitIds == null;
    }
}
```

### 4.2 Servlet Filter

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)  // After security filter
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof TssUserDetails user) {
                TenantContext context = new TenantContext(
                    user.getOrgId(),
                    user.getAccessibleBusinessUnitIds(),
                    user.getOrgCode()
                );
                TenantContextHolder.set(context);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/login") ||
               path.startsWith("/css") ||
               path.startsWith("/js") ||
               path.startsWith("/actuator/health");
    }
}
```

### 4.3 Repository-Level Enforcement

**Option A: Spring Data Specifications (chosen for Phase 1)**

```java
public class TenantSpecifications {

    public static <T> Specification<T> inCurrentTenant() {
        return (root, query, cb) -> {
            TenantContext ctx = TenantContextHolder.get();
            return cb.equal(root.get("orgId"), ctx.orgId());
        };
    }

    public static <T> Specification<T> inAccessibleBusinessUnits() {
        return (root, query, cb) -> {
            TenantContext ctx = TenantContextHolder.get();
            if (ctx.hasOrgWideAccess()) {
                return cb.equal(root.get("orgId"), ctx.orgId());
            }
            return cb.and(
                cb.equal(root.get("orgId"), ctx.orgId()),
                root.get("businessUnitId").in(ctx.accessibleBusinessUnitIds())
            );
        };
    }
}
```

**Decision**: Specifications over Hibernate Filters.

**Rationale**: Hibernate Filters (`@Filter`) are session-scoped and require careful session management. They silently fail if not enabled, which can lead to data leaks in edge cases. Specifications are explicit, composable, and fail loudly if not applied. The trade-off is that developers must remember to apply them, which is mitigated by base service classes and code review.

**Option B: Hibernate Filters (considered, reserved for Phase 2)**

```java
// If Hibernate Filters are added later for additional safety
@Entity
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "orgId", type = Long.class))
@Filter(name = "tenantFilter", condition = "org_id = :orgId")
public class Risk { ... }
```

### 4.4 Base Service Pattern

All module services extend a base class that enforces tenant context:

```java
public abstract class TenantAwareService {

    protected Long getCurrentOrgId() {
        return TenantContextHolder.get().orgId();
    }

    protected <T> Specification<T> withTenantFilter() {
        return TenantSpecifications.inCurrentTenant();
    }

    protected <T> Specification<T> withTenantAndBuFilter() {
        return TenantSpecifications.inAccessibleBusinessUnits();
    }

    protected void verifyTenantAccess(Long entityOrgId) {
        if (!entityOrgId.equals(getCurrentOrgId())) {
            throw new AccessDeniedException("Cross-tenant access denied");
        }
    }
}
```

---

## 5. Organization Onboarding

### 5.1 Onboarding Workflow

```
1. TSS admin creates organization record
   → Sets org code, name (AR/EN), license type, enabled modules

2. System seeds master data for the organization
   → Default roles (ERM_MANAGER, COMPLIANCE_ASSESSOR, etc.)
   → Default risk categories, likelihood/impact scales
   → Default workflow templates
   → Compliance framework mappings

3. TSS admin creates first ORG_ADMIN user for the organization

4. ORG_ADMIN completes setup
   → Creates Business Units and Departments
   → Creates users, assigns roles
   → Configures organization-specific settings (LDAP, SMTP, branding)
   → Imports existing data (risks, assets, controls)
```

### 5.2 Master Data Seeding

Each new organization receives a copy of system-level master data that they can customize:

| Data Category | Examples | Customizable |
|---|---|---|
| Risk categories | Strategic, Operational, Financial, Compliance, Cybersecurity | Yes - add, rename, deactivate |
| Likelihood scale | 1-5 with labels | Yes - labels, descriptions |
| Impact scale | 1-5 with labels per dimension | Yes - labels, descriptions, dimensions |
| Risk matrix | 5x5 default | Yes - colors, thresholds |
| Control categories | Preventive, Detective, Corrective | Yes - add, rename |
| Asset types | Hardware, Software, Data, People, Process | Yes - add, rename |
| Workflow templates | Risk approval, assessment review | Yes - customize steps |

### 5.3 Seed Data Implementation

```java
@Service
public class OrganizationSeedService {

    @Transactional
    public void seedOrganization(Long orgId) {
        seedDefaultRoles(orgId);
        seedRiskCategories(orgId);
        seedLikelihoodScale(orgId);
        seedImpactScale(orgId);
        seedRiskMatrix(orgId);
        seedControlCategories(orgId);
        seedAssetTypes(orgId);
        seedWorkflowTemplates(orgId);
    }

    private void seedDefaultRoles(Long orgId) {
        // Create predefined roles with standard permissions
        List<RoleSeed> seeds = List.of(
            new RoleSeed("ERM_MANAGER", "erm", ermManagerPermissions()),
            new RoleSeed("ERM_ANALYST", "erm", ermAnalystPermissions()),
            new RoleSeed("COMPLIANCE_MANAGER", "compliance", complianceManagerPermissions()),
            // ... etc.
        );
        seeds.forEach(seed -> roleRepository.save(seed.toEntity(orgId)));
    }
}
```

---

## 6. Tenant Configuration

### 6.1 Per-Organization Settings

Stored in `admin.org_configurations` (see [15-configuration-branding-model.md](15-configuration-branding-model.md)):

| Category | Settings |
|---|---|
| Security | Password policy, session timeout, lockout threshold |
| LDAP | Server URL, base DN, search filter, bind credentials |
| SMTP | Mail server, port, credentials, from address |
| Branding | Logo, primary color, application title |
| Modules | Enabled modules, feature toggles |
| Workflows | Approval requirements, notification preferences |
| Localization | Default language, date format, number format |

### 6.2 Module Licensing

Organizations are licensed for specific modules. The `licensed_modules` field on `admin.organizations` contains a JSON array of enabled module codes:

```json
["erm", "compliance", "bcm", "audit", "reporting"]
```

**Enforcement**:
- Navigation menu only shows licensed modules
- URL access to unlicensed modules returns 403
- API access to unlicensed module endpoints returns 403
- License check happens at the module access layer (before permission check)

---

## 7. Isolation Verification

### 7.1 Integration Tests

**Decision**: Dedicated integration tests verify that tenant isolation cannot be bypassed.

```java
@SpringBootTest
@Transactional
class TenantIsolationTest {

    @Test
    void shouldNotReturnDataFromOtherTenant() {
        // Setup: Create risks for Org A and Org B
        Risk orgARisk = createRisk(orgA.getId(), "Org A Risk");
        Risk orgBRisk = createRisk(orgB.getId(), "Org B Risk");

        // Act: Query as Org A user
        TenantContextHolder.set(new TenantContext(orgA.getId(), null, "ORG_A"));
        List<Risk> results = riskService.findAll(Pageable.unpaged()).getContent();

        // Assert: Only Org A data returned
        assertThat(results).extracting(Risk::getOrgId)
            .containsOnly(orgA.getId());
        assertThat(results).extracting(Risk::getId)
            .doesNotContain(orgBRisk.getId());
    }

    @Test
    void shouldPreventCrossTenantEntityAccess() {
        Risk orgBRisk = createRisk(orgB.getId(), "Org B Risk");

        TenantContextHolder.set(new TenantContext(orgA.getId(), null, "ORG_A"));

        assertThatThrownBy(() -> riskService.findById(orgBRisk.getId()))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldEnforceBuScopingForRestrictedUsers() {
        Risk buARisk = createRisk(orgA.getId(), buA.getId(), "BU A Risk");
        Risk buBRisk = createRisk(orgA.getId(), buB.getId(), "BU B Risk");

        // User scoped to BU A only
        TenantContextHolder.set(new TenantContext(orgA.getId(), List.of(buA.getId()), "ORG_A"));
        List<Risk> results = riskService.findAll(Pageable.unpaged()).getContent();

        assertThat(results).extracting(Risk::getBusinessUnitId)
            .containsOnly(buA.getId());
    }
}
```

### 7.2 Automated Verification

- Tenant isolation tests run as part of the CI/CD pipeline on every build
- Any test failure blocks the build
- Test coverage includes: query operations, single-entity access, update operations, delete operations, export operations

---

## 8. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Isolation strategy | Row-level (org_id) | Schema-per-tenant, DB-per-tenant | Simplest operations, sufficient isolation for B2B SaaS |
| Org hierarchy | Fixed 3-level (Org/BU/Dept) | Arbitrary depth tree | Simpler queries, covers 95% of Saudi org structures |
| Tenant context | ThreadLocal | Request attribute, method parameter | Clean API, request-scoped, standard Java pattern |
| Query filtering | Spring Data Specifications | Hibernate Filters, AOP | Explicit, composable, fail-loud, testable |
| Master data | Seeded copy per org | Shared reference with org overrides | Allows full customization per org |
| Module licensing | JSON array on org record | Separate license table | Simple, sufficient for module-level licensing |
