package com.tss.grc.module.platform.licensing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import com.tss.grc.module.platform.licensing.repository.OrganisationLicenseRepository;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final OrganisationRepository organisations;
    private final AuditLogService audit;

    /**
     * <b>The 15 product SKUs ratified by ADR-018 — what the platform may sell.</b> Every one of
     * them is now held by every organisation, granted administratively by <b>V344</b> (W2-0's
     * grandfather backfill), because {@code LicenseGuardAspect} fails closed and two of the three
     * tenants held one licence row each.
     *
     * <p>The catalogue is {@code TSS-GRC-Platform-Bible/Product/SKU_CATALOGUE.md}. Do not add a
     * code here without amending that file and ADR-018 in the same commit — {@code
     * SkuCatalogueRatchetTest} fails if you do. {@code @RequireModule} may only name a code from
     * <em>this</em> set: naming a retired one is a guard no tenant can satisfy.
     */
    public static final Set<String> SELLABLE_MODULES = Set.of(
        "RISK", "COMPLIANCE", "AUDIT_LC", "CYBER", "SOCOPS", "BCM", "PDPL", "TPRM",
        "ESG", "ETHICS", "SAUDI_FS", "DATA_AI_GOV", "BOARD", "ACCESS_GOV", "CHANGE"
    );

    /**
     * <b>Retired codes, kept only because licence rows name them.</b> Each maps to where ADR-018 §5
     * sent it. Five merged into a sibling SKU; seven were de-licensed into the mandatory Base
     * Platform, which cannot be bought because every tenant has it (ADR-018 R4).
     *
     * <p>These stay assignable for <em>existing</em> rows and are refused for <em>new</em> ones —
     * see {@link #upsertInternal}. That asymmetry is the point: an operator must be able to wind a
     * legacy entitlement down (change its tier, set {@code validTo}, disable it) and must not be
     * able to sell another one. Deleting the codes outright would make org 1's seven Base rows
     * unadministrable, and ADR-018 R2 forbids renaming a code a row already carries.
     */
    public static final Map<String, String> RETIRED_DESTINATION = Map.ofEntries(
        Map.entry("POLICY",         "COMPLIANCE"),
        Map.entry("REGULATORY",     "COMPLIANCE"),
        Map.entry("SCM",            "TPRM"),
        Map.entry("INCIDENT",       "SOCOPS"),
        Map.entry("NDI",            "DATA_AI_GOV"),
        Map.entry("EVIDENCE",       "(Base)"),
        Map.entry("REPORTING",      "(Base)"),
        Map.entry("AUDIT_LOG",      "(Base)"),
        Map.entry("PLATFORM_ADMIN", "(Base)"),
        Map.entry("CONTROL_LIB",    "(Base)"),
        Map.entry("POSTURE",        "(Base)"),
        Map.entry("AUTOMATION",     "(Base)")
    );

    /**
     * Every code the platform will accept on a licence row at all — {@link #SELLABLE_MODULES} plus
     * {@link #RETIRED_DESTINATION}'s keys. This is a <em>validation</em> set, not a catalogue.
     *
     * <p>⚠ It used to be a flat set of 20 whose Javadoc called itself "the source of truth". It was
     * not, and the split above is why: seven of the 20 priced capabilities belonging to the
     * mandatory Base, and seven sellable packages — including {@code ndi} at 7,717 LoC — had no code
     * at all. That was <b>F2-35</b>, and while the set stayed flat, "what may be assigned" and "what
     * may be sold" were the same string and could not disagree out loud.
     */
    public static final Set<String> KNOWN_MODULES = Stream.concat(
            SELLABLE_MODULES.stream(), RETIRED_DESTINATION.keySet().stream())
        .collect(Collectors.toUnmodifiableSet());

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
     * Resolve which organisation a licensing write targets — <b>F2-45</b>.
     *
     * <p>{@code null} means "the organisation I am switched into", which keeps every existing caller
     * working. A value means that organisation, and it must exist.
     *
     * <p>⚠ <b>What this replaces was not a refusal, it was a silent misdirection.</b>
     * {@code LicensingController.upsert} used to overwrite the body's {@code organisationId} with
     * {@code TenantContext} before calling {@link #upsert}. A platform operator posting
     * {@code {"organisationId": 2, "moduleCode": "RISK"\}} got <b>200 OK and a row for organisation
     * 1</b> — live-verified. The operator's belief that tenant 2 was licensed was wrong, and nothing
     * in the response said so. A 403 would have been safe; succeeding against the wrong tenant is
     * the failure mode that gets discovered by a customer.
     *
     * <p>Honouring the body is safe because <b>licensing is the platform's one legitimate
     * body-supplied tenant</b> (F2-22b measured this across all 214 entity-bound writes): granting a
     * module is inherently cross-tenant, the only callers are {@code PLATFORM_ADMIN} — the host
     * operator, not a tenant persona — and {@link #upsert} already runs inside
     * {@code TenantContext.allowCrossTenantWrite()} so the production write-guard interceptor sees a
     * declared cross-tenant insert rather than a leak.
     *
     * <p>The existence check is the part that is easy to skip and matters most: without it a typo
     * writes licence rows for organisation 999, which no tenant will ever read and no screen will
     * ever show. There is no {@code platform.organisations} foreign key on
     * {@code organisation_licenses} to catch it.
     *
     * <p>Deliberately <em>not</em> added: a cross-tenant licence <em>read</em> endpoint. A
     * {@code PLATFORM_ADMIN} already reads any tenant's licences by switching into it
     * ({@code POST /api/v1/platform/organisations/switch/{id\}}), so a new endpoint would add a
     * second role-guarded site — and the F2-05 ratchet counts those, correctly.
     */
    @Transactional(readOnly = true)
    public Long resolveLicensingTarget(Long requestedOrganisationId) {
        if (requestedOrganisationId == null) {
            Long current = TenantContext.getCurrentTenant();
            if (current == null)
                throw new IllegalArgumentException(
                    "No organisation in scope and none supplied — name organisationId explicitly.");
            return current;
        }
        if (!organisations.existsById(requestedOrganisationId))
            throw new EntityNotFoundException(
                "Organisation " + requestedOrganisationId + " does not exist. Licence rows carry no "
                + "foreign key to platform.organisations, so a wrong id would be written and never read.");
        return requestedOrganisationId;
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

        var existingRow = repo.findByOrganisationIdAndModuleCode(
            body.getOrganisationId(), body.getModuleCode());

        // A retired code may be wound DOWN, never sold. Editing an existing row (re-tiering it,
        // setting validTo, disabling it) is how a legacy entitlement is retired in an orderly way;
        // creating a new one would sell a SKU the catalogue has already retired, which is the
        // pricing error F2-35 found. Checked here rather than in the controller so the service-as-API
        // path — which is what a backfill migration's successor would use — obeys the same rule.
        if (existingRow.isEmpty() && RETIRED_DESTINATION.containsKey(body.getModuleCode())) {
            String dest = RETIRED_DESTINATION.get(body.getModuleCode());
            throw new IllegalArgumentException(
                "Module " + body.getModuleCode() + " is retired by ADR-018 and cannot be newly licensed. "
                + ("(Base)".equals(dest)
                    ? "It is part of the mandatory Base Platform, which every tenant already has and "
                      + "which is not licensable (R4) — there is nothing to grant."
                    : "Grant " + dest + " instead, which is the SKU it merged into (§5).")
                + " Existing rows for this code may still be edited or disabled.");
        }

        OrganisationLicense saved = existingRow
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
            .severity("WARN")
            .message("Module disabled: org=" + organisationId + " module=" + moduleCode));
    }
}
