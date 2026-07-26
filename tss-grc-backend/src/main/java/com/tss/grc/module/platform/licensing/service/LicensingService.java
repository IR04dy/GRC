package com.tss.grc.module.platform.licensing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import com.tss.grc.module.platform.licensing.repository.OrganisationLicenseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Module licensing service — Framework Intersection F9.
 *
 * <p>Two consumers:
 * <ul>
 *   <li>{@code LicenseGuard} aspect checks
 *       {@link #isModuleEnabled(Long, String)} before letting a
 *       controller method run.</li>
 *   <li>The JWT factory calls {@link #enabledModulesFor(Long)} at
 *       login so the frontend can hide unlicensed sidebar
 *       entries without a round trip.</li>
 * </ul>
 *
 * <p>Cached via Caffeine — license rows don't change often and the
 * check sits on every authenticated request hot-path. Cache evicts
 * when an admin patches a license.
 */
@Service
@RequiredArgsConstructor
public class LicensingService {

    private static final Set<String> TIERS = Set.of("FREE", "PRO", "ENTERPRISE");

    private final OrganisationLicenseRepository repo;
    private final AuditLogService audit;

    /** Master list of canonical module codes — kept in sync with the V97 seed
     *  and any subsequent migration that introduces a new module (SOCOPS in
     *  V150). The {@link LicensingService#assign(...)} validation rejects
     *  unknown codes, so this list is the source of truth for "what modules
     *  can a platform admin entitle a tenant to". */
    public static final Set<String> KNOWN_MODULES = Set.of(
        "RISK", "BCM", "SCM", "CYBER", "PDPL", "TPRM",
        "POLICY", "NDI", "COMPLIANCE", "AUDIT_LC",
        "INCIDENT", "EVIDENCE", "REPORTING",
        "REGULATORY", "CONTROL_LIB", "POSTURE",
        "AUTOMATION", "AUDIT_LOG", "PLATFORM_ADMIN",
        "SOCOPS"
    );

    @Cacheable(value = "licenses", key = "#organisationId")
    @Transactional(readOnly = true)
    public Set<String> enabledModulesFor(Long organisationId) {
        LocalDate today = LocalDate.now();
        Set<String> out = new TreeSet<>();
        for (OrganisationLicense l : repo.findByOrganisationIdOrderByModuleCodeAsc(organisationId)) {
            if (Boolean.FALSE.equals(l.getEnabled())) continue;
            if (l.getValidFrom() != null && l.getValidFrom().isAfter(today)) continue;
            if (l.getValidTo()   != null && l.getValidTo().isBefore(today))  continue;
            out.add(l.getModuleCode());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public boolean isModuleEnabled(Long organisationId, String moduleCode) {
        if (moduleCode == null) return false;
        return enabledModulesFor(organisationId).contains(moduleCode);
    }

    @Transactional(readOnly = true)
    public List<OrganisationLicense> list() {
        return repo.findByOrganisationIdOrderByModuleCodeAsc(TenantContext.getCurrentTenant());
    }

    /**
     * Admin: upsert a license row.
     *
     * <p><b>F2-22b — the platform's one legitimate body-supplied tenant.</b> Granting a module
     * licence is inherently cross-tenant: a platform operator names the organisation being
     * licensed, so {@code organisationId} must come from the request and this is the only endpoint
     * on the platform that requires it (measured across all 214 entity-bound writes). Everything
     * else stamps the tenant from {@code TenantContext}.
     *
     * <p>Which is why the whole body runs inside {@link TenantContext#allowCrossTenantWrite()}:
     * {@code TenantWriteGuardInterceptor} gained an INSERT leg and it is ON in production and now
     * in dev, so a licence inserted for any org other than the caller's own is a cross-tenant
     * insert and must declare itself. Mutation-proven: with this scope removed, granting org 2 a
     * module it does not hold returns <b>403 cross-tenant-write</b>.
     *
     * <p>⚠ To be exact about what the scope does and does not rescue: the HTTP endpoint is
     * <em>already</em> single-tenant, because {@code LicensingController.upsert} overwrites the
     * body's {@code organisationId} with {@code TenantContext} before calling this. So today's API
     * traffic never needed the scope. It is here for the service as an API — W2-0's licence
     * backfill grants across orgs 2/3 — and the controller's stamp is itself a limitation on that
     * backfill, recorded as <b>F2-45</b>.
     */
    @CacheEvict(value = "licenses", key = "#body.organisationId")
    @Transactional
    public OrganisationLicense upsert(OrganisationLicense body) {
        try (var crossTenant = TenantContext.allowCrossTenantWrite()) {
            return upsertInternal(body);
        }
    }

    private OrganisationLicense upsertInternal(OrganisationLicense body) {
        if (body.getOrganisationId() == null)
            throw new IllegalArgumentException("organisationId is required");
        if (body.getModuleCode() == null || body.getModuleCode().isBlank())
            throw new IllegalArgumentException("moduleCode is required");
        if (!KNOWN_MODULES.contains(body.getModuleCode()))
            throw new IllegalArgumentException("Unknown module: " + body.getModuleCode());
        if (body.getTier() != null && !TIERS.contains(body.getTier()))
            throw new IllegalArgumentException("tier must be FREE / PRO / ENTERPRISE");

        OrganisationLicense saved = repo.findByOrganisationIdAndModuleCode(
                body.getOrganisationId(), body.getModuleCode())
            .map(existing -> {
                if (body.getTier()       != null) existing.setTier(body.getTier());
                if (body.getSeatCount()  != null) existing.setSeatCount(body.getSeatCount());
                if (body.getValidFrom()  != null) existing.setValidFrom(body.getValidFrom());
                if (body.getValidTo()    != null) existing.setValidTo(body.getValidTo());
                if (body.getEnabled()    != null) existing.setEnabled(body.getEnabled());
                if (body.getNotes()      != null) existing.setNotes(body.getNotes());
                existing.setUpdatedAt(java.time.LocalDateTime.now());
                return repo.save(existing);
            })
            .orElseGet(() -> {
                body.setId(null);
                if (body.getTier() == null) body.setTier("PRO");
                if (body.getValidFrom() == null) body.setValidFrom(LocalDate.now());
                if (body.getEnabled() == null) body.setEnabled(true);
                return repo.save(body);
            });

        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("CUSTOM")
            .entityType("OrganisationLicense")
            .entityId(saved.getId())
            .entityCode(saved.getModuleCode())
            .sourceModule("licensing")
            .severity("INFO")
            .message("License upserted: org=" + saved.getOrganisationId()
                     + " module=" + saved.getModuleCode()
                     + " tier=" + saved.getTier()
                     + " enabled=" + saved.getEnabled()));
        return saved;
    }

    @CacheEvict(value = "licenses", key = "#organisationId")
    @Transactional
    public void disable(Long organisationId, String moduleCode) {
        OrganisationLicense l = repo.findByOrganisationIdAndModuleCode(organisationId, moduleCode)
            .orElseThrow(() -> new EntityNotFoundException("License not found"));
        l.setEnabled(false);
        l.setUpdatedAt(java.time.LocalDateTime.now());
        repo.save(l);
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("CUSTOM")
            .entityType("OrganisationLicense")
            .entityId(l.getId())
            .entityCode(moduleCode)
            .sourceModule("licensing")
            .severity("WARN")
            .message("Module disabled: org=" + organisationId + " module=" + moduleCode));
    }
}
