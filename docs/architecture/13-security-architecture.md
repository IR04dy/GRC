# 13 - Security Architecture

## Purpose

This document defines the comprehensive security architecture for the TSS GRC platform. A GRC platform manages sensitive regulatory, risk, and compliance data, making security a foundational requirement rather than an afterthought. This document covers defense-in-depth measures across all layers: network, transport, application, data, and operational.

---

## 1. Security Principles

1. **Defense in depth**: No single security control is relied upon; multiple overlapping controls at each layer
2. **Least privilege**: Users, services, and processes receive the minimum permissions needed
3. **Secure by default**: All features ship with secure defaults; insecure options require explicit configuration
4. **Fail closed**: Security failures result in access denial, not access grant
5. **Auditability**: Every security-relevant action is logged with sufficient detail for forensic investigation
6. **Separation of duties**: Critical operations require multi-person authorization

---

## 2. Authentication Security

### 2.1 Password Management

| Control | Implementation | Rationale |
|---|---|---|
| Hashing algorithm | BCrypt (strength 12) | Adaptive work factor, resistant to GPU attacks |
| Minimum length | 12 characters (configurable) | NIST SP 800-63B aligned |
| Password history | 12 previous passwords retained | Prevents cycling through passwords |
| Account lockout | 5 failed attempts, 30-min lockout | Brute-force protection |
| Password expiry | 90 days (configurable, can be disabled) | NCA ECC compliance |

### 2.2 LDAP/AD Integration Security

- Bind credentials stored as environment variables, never in configuration files or database
- LDAPS (LDAP over TLS) required for production deployments
- Service account used for LDAP binds has read-only permissions in AD
- LDAP connection timeout: 5 seconds (prevents hanging on network issues)
- Failed LDAP authentication does not reveal whether the user exists in AD

### 2.3 MFA Readiness

- TOTP-based MFA designed for Phase 2
- Database schema includes `mfa_enabled` and `mfa_secret` columns
- MFA secrets will be encrypted at application level (AES-256) before database storage
- Recovery codes planned for MFA enrollment

---

## 3. Authorization Security

### 3.1 RBAC with Module and BU Scoping

Detailed in [11-iam-architecture.md](11-iam-architecture.md). Key security controls:

- **Method-level enforcement**: `@PreAuthorize` on all service methods ensures permissions are checked regardless of entry point
- **URL-level enforcement**: Spring Security filter chain blocks unauthenticated access to all paths except login and static resources
- **Module-level gating**: Users cannot access modules their role does not include AND that are not licensed for the organization
- **BU-level data isolation**: Users scoped to a Business Unit cannot query, view, or modify data from other BUs
- **Permission caching**: Cached in session; invalidated when roles or permissions change

### 3.2 Segregation of Duties

Enforced through the permission model for sensitive operations:

| Operation | Required Roles | Control |
|---|---|---|
| User creation | ORG_ADMIN + second admin approval | Prevents unauthorized account creation |
| Role assignment | ORG_ADMIN + second admin approval | Prevents privilege escalation |
| Risk acceptance | ERM_MANAGER + approval workflow | Prevents unilateral risk acceptance |
| Audit report approval | AUDIT_MANAGER (cannot be same as author) | Prevents self-approval |
| Configuration change | SUPER_ADMIN with audit log | All changes tracked |

---

## 4. Encryption

### 4.1 Encryption in Transit

**Decision**: TLS 1.2+ mandatory for all connections.

| Connection | Protocol | Configuration |
|---|---|---|
| Browser to application | HTTPS (TLS 1.2+) | Terminated at load balancer or application server |
| Application to database | TDS with encryption | SQL Server encrypted connection required |
| Application to LDAP | LDAPS (TLS) | Required for production |
| Application to SMTP | STARTTLS or SMTPS | Required for production |

**HSTS configuration**:

```java
http
    .headers(headers -> headers
        .httpStrictTransportSecurity(hsts -> hsts
            .includeSubDomains(true)
            .maxAgeInSeconds(31536000)  // 1 year
            .preload(true)
        )
    );
```

### 4.2 Encryption at Rest

**Database-level**: SQL Server Transparent Data Encryption (TDE).

**Decision**: TDE for full database encryption; application-level AES-256 for specific sensitive fields.

**Rationale**: TDE provides transparent, zero-application-change encryption for the entire database at rest. It protects against physical media theft and unauthorized backup access. Application-level encryption adds a second layer for fields that must be protected even from database administrators.

**Application-level encrypted fields**:

| Table | Column | Reason |
|---|---|---|
| iam.users | mfa_secret | TOTP secret must not be readable by DBA |
| admin.org_configurations | smtp_password | Mail server credentials |
| admin.org_configurations | ldap_bind_password | Directory bind credentials |
| admin.system_configurations | encryption-related keys | Key material |

**Implementation**:

```java
@Component
public class FieldEncryptor {

    private final SecretKey key;  // Loaded from external key store

    public String encrypt(String plaintext) {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = generateSecureRandom(12);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        // Prepend IV to ciphertext for storage
        return Base64.getEncoder().encodeToString(concat(iv, encrypted));
    }

    public String decrypt(String ciphertext) {
        byte[] data = Base64.getDecoder().decode(ciphertext);
        byte[] iv = Arrays.copyOfRange(data, 0, 12);
        byte[] encrypted = Arrays.copyOfRange(data, 12, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
```

**JPA attribute converter**:

```java
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Autowired
    private FieldEncryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : encryptor.decrypt(dbData);
    }
}
```

---

## 5. Secure HTTP Headers

**Decision**: Strict security headers enforced on all responses.

```java
http
    .headers(headers -> headers
        .contentSecurityPolicy(csp -> csp
            .policyDirectives(
                "default-src 'self'; " +
                "script-src 'self' 'nonce-{random}'; " +  // Nonce-based CSP for inline scripts
                "style-src 'self' 'unsafe-inline'; " +     // Inline styles needed for Thymeleaf
                "img-src 'self' data:; " +
                "font-src 'self'; " +
                "frame-ancestors 'none'; " +
                "form-action 'self'"
            ))
        .frameOptions(frame -> frame.deny())
        .xssProtection(xss -> xss.headerValue(
            XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
        .contentTypeOptions(Customizer.withDefaults())  // X-Content-Type-Options: nosniff
        .referrerPolicy(referrer -> referrer
            .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
        .permissionsPolicy(permissions -> permissions
            .policy("camera=(), microphone=(), geolocation=()"))
    );
```

| Header | Value | Purpose |
|---|---|---|
| Content-Security-Policy | See above | Prevents XSS, injection, clickjacking |
| X-Frame-Options | DENY | Prevents clickjacking |
| X-Content-Type-Options | nosniff | Prevents MIME type sniffing |
| X-XSS-Protection | 1; mode=block | Legacy XSS filter (defense in depth) |
| Strict-Transport-Security | max-age=31536000 | Forces HTTPS |
| Referrer-Policy | strict-origin-when-cross-origin | Controls referrer leakage |
| Permissions-Policy | camera=(), microphone=() | Disables unnecessary browser APIs |

---

## 6. CSRF Protection

**Decision**: Spring Security CSRF tokens for all state-changing requests.

**Rationale**: The Thymeleaf server-rendered application uses form submissions and HTMX AJAX calls, both of which are vulnerable to CSRF attacks. Spring Security's synchronizer token pattern provides robust protection.

**Implementation**:

```html
<!-- Thymeleaf forms automatically include CSRF token -->
<form th:action="@{/erm/risks}" method="post">
    <!-- Hidden CSRF token field auto-injected by Spring Security -->
</form>

<!-- HTMX AJAX requests: CSRF token included via meta tag -->
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
```

```javascript
// HTMX configuration to include CSRF header on all requests
document.body.addEventListener('htmx:configRequest', function(evt) {
    evt.detail.headers['X-CSRF-TOKEN'] =
        document.querySelector('meta[name="_csrf"]').content;
});
```

**API endpoints** (`/api/v1/**`): CSRF disabled for stateless API consumers using JWT (Phase 2). Session-based API access retains CSRF protection.

---

## 7. Input Validation and Injection Prevention

### 7.1 Bean Validation

All input DTOs validated using Jakarta Bean Validation:

```java
public record CreateRiskRequest(
    @NotBlank @Size(max = 500) String titleEn,
    @NotBlank @Size(max = 500) String titleAr,
    @Size(max = 4000) String descriptionEn,
    @Size(max = 4000) String descriptionAr,
    @NotNull RiskLevel riskLevel,
    @NotNull Long categoryId,
    @NotNull Long ownerId
) {}
```

### 7.2 XSS Prevention

**Decision**: Output encoding by default (Thymeleaf) plus input sanitization for rich text fields.

| Layer | Control |
|---|---|
| Thymeleaf output | `th:text` escapes HTML by default; `th:utext` used only for pre-sanitized content |
| Rich text input | OWASP Java HTML Sanitizer for fields that accept formatted text |
| JSON output | Jackson escapes HTML characters in JSON responses |
| CSP header | Nonce-based script policy prevents inline script injection |

```java
// HTML sanitization for rich text fields
@Component
public class HtmlSanitizer {
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
        .allowCommonBlockElements()
        .allowCommonInlineFormattingElements()
        .allowElements("a")
        .allowAttributes("href").onElements("a")
        .requireRelNofollowOnLinks()
        .toFactory();

    public String sanitize(String input) {
        return input == null ? null : POLICY.sanitize(input);
    }
}
```

### 7.3 SQL Injection Prevention

**Decision**: Parameterized queries exclusively via JPA/Hibernate. No string concatenation in queries.

| Layer | Control |
|---|---|
| JPA repositories | Spring Data methods and JPQL with named parameters |
| Native queries | `@Query(nativeQuery = true)` with `:paramName` binding |
| Specifications | Criteria API (type-safe, no string interpolation) |
| Stored procedures | Parameterized `@Procedure` calls |

**Prohibited**: Direct `String.format()` or concatenation in any SQL/JPQL/HQL query. Enforced via code review checklist and static analysis.

### 7.4 Path Traversal Prevention

```java
// File upload path validation
public Path resolveSecureFilePath(String filename) {
    String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    Path resolved = uploadDirectory.resolve(sanitized).normalize();
    if (!resolved.startsWith(uploadDirectory)) {
        throw new SecurityException("Path traversal attempt detected");
    }
    return resolved;
}
```

---

## 8. File Upload Security

| Control | Implementation | Rationale |
|---|---|---|
| File type validation | Whitelist of allowed MIME types and extensions | Prevents executable upload |
| File size limit | 10 MB default per file, configurable | Prevents storage abuse |
| Filename sanitization | Strip special characters, generate UUID-based names | Prevents path traversal |
| Storage isolation | Upload directory outside web root | Prevents direct URL access |
| Virus scanning | Integration point for ClamAV or commercial scanner | Malware detection |
| Content-Disposition | `attachment` header on downloads | Prevents browser rendering of uploaded content |

**Allowed file types**:

```java
private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
    "pdf", "doc", "docx", "xls", "xlsx", "csv", "png", "jpg", "jpeg", "txt"
);

private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    "image/png",
    "image/jpeg",
    "text/plain"
);
```

---

## 9. Session Security

Detailed in [11-iam-architecture.md](11-iam-architecture.md). Summary of security controls:

| Control | Configuration |
|---|---|
| Cookie flags | HttpOnly, Secure, SameSite=Strict |
| Session fixation | Migrate session on authentication |
| Idle timeout | 30 minutes (configurable) |
| Absolute timeout | 12 hours (configurable) |
| Concurrent sessions | 1 per user (configurable) |
| Session ID entropy | Spring Security default (SecureRandom, 128-bit) |

---

## 10. Secrets Management

### 10.1 Configuration Hierarchy

**Decision**: Externalized configuration with environment variables for all secrets.

| Secret Type | Storage | Access Method |
|---|---|---|
| Database credentials | Environment variable | `${DB_PASSWORD}` in application.yml |
| LDAP bind password | Environment variable | `${LDAP_BIND_PASSWORD}` |
| SMTP credentials | Environment variable | `${SMTP_PASSWORD}` |
| Encryption keys | External key file or Vault | Loaded at startup |
| API keys (future) | Environment variable or Vault | `${API_SECRET_KEY}` |

### 10.2 Spring Vault Readiness

**Phase 1**: Environment variables and external property files.

**Phase 2**: Spring Cloud Vault integration for centralized secret management.

```yaml
# Future Vault configuration
spring:
  cloud:
    vault:
      uri: https://vault.internal:8200
      authentication: approle
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
```

### 10.3 Key Management

| Concern | Approach |
|---|---|
| Encryption key storage | External file with restricted OS permissions (600) |
| Key rotation | Application supports key versioning; new data encrypted with current key, old data re-encrypted on access |
| Key backup | Separate from database backup; documented recovery procedure |
| Key access | Only application service account; no DBA access |

---

## 11. Audit and Monitoring

### 11.1 Security Event Logging

All security events written to `audit.audit_logs` (immutable, append-only from application perspective):

| Event Category | Events | Logged Details |
|---|---|---|
| Authentication | Login success/failure, logout, lockout | User, IP, user-agent, result |
| Authorization | Permission denied, role change | User, resource, action, result |
| Data access | Sensitive data viewed, exported | User, entity type, entity ID |
| Administration | User CRUD, config change, role assignment | Admin user, target, before/after values |
| Security | CSRF violation, session hijack attempt | Request details, IP, headers |

### 11.2 Privileged Access Monitoring

Actions by users with `SUPER_ADMIN` or `ORG_ADMIN` roles receive enhanced logging:

- All data access logged (not just modifications)
- Configuration changes include before/after values
- Login from new IP addresses flagged
- Bulk operations (export, import) logged with record counts

### 11.3 Approval Workflows

**Decision**: Sensitive operations require approval by a second authorized user.

| Operation | Approver Role | Workflow |
|---|---|---|
| New user creation | ORG_ADMIN (different from creator) | Creator submits, approver activates |
| Role assignment change | ORG_ADMIN (different from requester) | Requester submits, approver confirms |
| Risk acceptance | ERM_MANAGER or higher (not the risk owner) | Owner submits, manager approves |
| System configuration change | SUPER_ADMIN | Logged with before/after; optional second approval |

---

## 12. Database Security

### 12.1 Application Database Access

| Control | Implementation |
|---|---|
| Connection account | Dedicated service account, not sa or dbo |
| Permissions | Minimum required: SELECT, INSERT, UPDATE, DELETE on application schemas |
| Schema DDL | Separate migration account for schema changes (Flyway) |
| No direct user access | End users never connect directly to the database |
| Connection encryption | TDS encryption required (`encrypt=true` in connection string) |

### 12.2 Tenant Data Isolation

**Decision**: Row-level filtering by `org_id` enforced at the repository layer.

```java
// TenantContext set by filter on every request
@Component
public class TenantFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                     FilterChain chain) {
        Long orgId = resolveOrgId(SecurityContextHolder.getContext().getAuthentication());
        TenantContextHolder.set(orgId);
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContextHolder.clear();
        }
    }
}

// Enforced on all queries via Spring Data Specification
public static Specification<Risk> inCurrentTenant() {
    return (root, query, cb) ->
        cb.equal(root.get("orgId"), TenantContextHolder.get());
}
```

**Integration test verification**: Tests confirm that queries for Org A never return data belonging to Org B.

---

## 13. OWASP Top 10 Mitigation Map

| OWASP Category | TSS Mitigation |
|---|---|
| A01: Broken Access Control | RBAC + method-level security + BU scoping + tenant isolation |
| A02: Cryptographic Failures | TLS 1.2+, TDE, AES-256 field encryption, BCrypt passwords |
| A03: Injection | Parameterized queries (JPA), input validation, HTML sanitization |
| A04: Insecure Design | Threat modeling, approval workflows, principle of least privilege |
| A05: Security Misconfiguration | Secure defaults, CSP headers, disabled debug in production, Spring profiles |
| A06: Vulnerable Components | Dependency scanning (OWASP Dependency-Check), regular updates |
| A07: Authentication Failures | BCrypt, lockout policy, session management, MFA readiness |
| A08: Software and Data Integrity Failures | Signed artifacts, Flyway-managed schema migrations, input validation |
| A09: Security Logging and Monitoring | Comprehensive audit logging, security event alerting readiness |
| A10: Server-Side Request Forgery | No user-controlled URLs in server-side requests; webhook URLs admin-configured only |

---

## 14. Deployment Security Considerations

### 14.1 SaaS Deployment

| Concern | Approach |
|---|---|
| Tenant isolation | Row-level org_id filtering, tested via integration tests |
| Shared infrastructure | Single application instance, shared database with schema/row-level separation |
| Data residency | Saudi-region hosting; data does not leave the region |
| Backup isolation | Per-tenant backup extraction capability |
| Vulnerability management | TSS-managed patching schedule, automated vulnerability scanning |

### 14.2 On-Premises Deployment

| Concern | Approach |
|---|---|
| Infrastructure | Customer-managed servers, network, firewalls |
| Hardening guide | TSS provides deployment hardening guide covering OS, JVM, SQL Server, network |
| Network segmentation | Recommended: application tier in DMZ, database in internal zone |
| Patching | TSS provides security patches; customer applies |
| Monitoring | Customer integrates health endpoints with their monitoring tools |

### 14.3 Hardening Checklist

Production deployments should verify:

- [ ] TLS 1.2+ configured on load balancer/application server
- [ ] SQL Server TDE enabled
- [ ] Debug endpoints disabled (`management.endpoints.web.exposure.include=health`)
- [ ] Swagger UI disabled in production
- [ ] Default credentials changed
- [ ] LDAP connection uses LDAPS
- [ ] SMTP connection uses STARTTLS/SMTPS
- [ ] Upload directory outside web root with restricted permissions
- [ ] Application runs as non-root/non-Administrator user
- [ ] Database connection uses minimum-privilege account
- [ ] Log files have restricted read permissions
- [ ] Encryption key file has restricted permissions (600)
- [ ] Environment variables for secrets (not in config files)
- [ ] HSTS header enabled
- [ ] CSP header configured

---

## 15. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Field encryption | AES-256-GCM | ChaCha20, RSA | Industry standard, hardware acceleration, GCM provides authentication |
| TLS version | 1.2+ minimum | 1.3 only | 1.2 maintains compatibility with older enterprise clients; 1.3 preferred when supported |
| CSP strategy | Nonce-based for scripts | Hash-based, unsafe-inline | Nonce-based is most secure for server-rendered apps with inline scripts |
| CSRF protection | Synchronizer token | Double-submit cookie | More secure; Spring Security native support |
| Tenant isolation | Row-level org_id | Schema-per-tenant, DB-per-tenant | Simplest to implement and operate; sufficient for Phase 1 data volumes |
| Secret storage | Environment variables | Vault, AWS Secrets Manager | Simplest for Phase 1; Vault-ready for Phase 2 |
| Dependency scanning | OWASP Dependency-Check | Snyk, WhiteSource | Free, open-source, integrates with Maven/Gradle build |
