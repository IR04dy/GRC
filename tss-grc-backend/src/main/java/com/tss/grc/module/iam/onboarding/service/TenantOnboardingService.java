package com.tss.grc.module.iam.onboarding.service;

import com.tss.grc.common.exception.DuplicateResourceException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.dto.UserRequest;
import com.tss.grc.module.iam.dto.UserResponse;
import com.tss.grc.module.iam.service.UserService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.licensing.SkuRegistry;
import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import com.tss.grc.module.platform.licensing.repository.OrganisationLicenseRepository;
import com.tss.grc.module.iam.onboarding.dto.TenantOnboardingDtos.OnboardTenantRequest;
import com.tss.grc.module.iam.onboarding.dto.TenantOnboardingDtos.OnboardTenantResponse;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F2-81 — the one transactional operation that brings a tenant into existence.
 *
 * <h3>What was missing</h3>
 * Everything about multi-tenancy existed <i>except the entry</i>: {@code TenantContext}, per-query
 * {@code organisation_id} filtering, an organisation switcher and a write-guard interceptor all work.
 * But {@code OrganisationController} had no {@code POST} other than {@code switch/{id}}, and
 * <b>nothing in the codebase called {@code save} on {@code OrganisationRepository}</b> — measured
 * again before writing this: 20 files reference the repository and 0 write through it. Organisations
 * 1–3 exist because a Python bootstrap inserted them, and each new tenant's licence rows were
 * hand-written into whichever migration happened to be open.
 *
 * <p>The tenant model was built from the inside out, and the workaround is what kept the gap
 * invisible: dev never needed an onboarding path because the bootstrap made the tenants the demo
 * required. A platform whose tenant count can only be changed by a DBA with {@code sa} is not
 * multi-tenant in the commercial sense, whatever the code does with {@code organisation_id}.
 *
 * <h3>Why it is one transaction</h3>
 * F2-81's recommendation is explicit that <b>a half-provisioned tenant is worse than none</b>, and
 * the reason is F2-04: now that ~2,138 endpoints are entitlement-annotated, an organisation without
 * licence rows is fail-closed on everything, which arrives as a support incident that looks like a
 * platform outage rather than as a missing step. An organisation without an administrator is a tenant
 * nobody can log into. So organisation, licences and first administrator either all land or none do.
 *
 * <h3>What it deliberately does not do</h3>
 * <ul>
 *   <li><b>No roles are created.</b> Every row in {@code iam.roles} carries
 *       {@code organisation_id IS NULL} — roles are global — so the new tenant sees them already.
 *       Creating per-tenant copies would have been speculative work that made the grant ambiguous.</li>
 *   <li><b>No branding row is created.</b> {@code BrandThemeService} resolves an absent theme to
 *       platform defaults field by field. F2-81's recommendation lists "branding defaults" as a step;
 *       measured, that step is already a no-op, and writing a row of defaults would only create
 *       something to drift from them.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantOnboardingService {

    /**
     * Every ratified SKU, derived from {@link SkuRegistry} — the homing map plus the ADR-018 R6
     * carve-outs.
     *
     * <p>Deliberately <b>derived and not listed</b>. {@code licence_coverage.py} records that the
     * ratified set already has three transcriptions which the suite asserts against each other; a
     * fourth here would be a new authority for the same fact, and F2-01's whole subject is what that
     * costs. Verified equal to that script's {@code RATIFIED} at 15 entries, and
     * {@code TenantOnboardingServiceTest} re-asserts the derivation so a new SKU is licensed to new
     * tenants automatically rather than by remembering to edit this class.
     */
    static Set<String> ratifiedSkus() {
        Set<String> out = new TreeSet<>(SkuRegistry.PACKAGE_TO_SKU.values());
        out.addAll(SkuRegistry.CARVE_OUTS.values());
        return out;
    }

    /** The tier a newly onboarded tenant's licence rows carry until an operator changes them. */
    static final String DEFAULT_TIER = "ENTERPRISE";

    private final OrganisationRepository organisations;
    private final OrganisationLicenseRepository licences;
    private final UserService users;
    private final AuditLogService audit;

    @Transactional
    public OnboardTenantResponse onboard(OnboardTenantRequest req) {
        String code = req.shortCode().trim().toUpperCase(Locale.ROOT);

        // Pre-flight the unique index rather than letting it surface as a 500 at flush. F2-88's
        // precedent: a duplicate code is a 409 that names the field, so the operator can fix the
        // one thing that is wrong instead of reading a constraint name.
        if (organisations.findByShortCode(code).isPresent()) {
            throw new DuplicateResourceException("shortCode",
                "An organisation with short code '" + code + "' already exists. The short code is "
                + "the public identifier a tenant's portal links carry, so it must be unique.");
        }

        // Every write below targets the NEW organisation while TenantContext still holds the host
        // operator's. In prod the tenant write-guard interceptor would reject that, so the whole
        // provisioning runs inside an explicit cross-tenant scope — the same mechanism
        // LicensingService.upsert uses, and for the same reason (F2-45).
        try (var scope = TenantContext.allowCrossTenantWrite()) {
            Organisation org = new Organisation();
            org.setNameEn(req.nameEn().trim());
            org.setNameAr(req.nameAr().trim());
            org.setShortCode(code);
            if (req.countryCode() != null && !req.countryCode().isBlank()) {
                org.setCountryCode(req.countryCode().trim().toUpperCase(Locale.ROOT));
            }
            if (req.subscriptionTier() != null && !req.subscriptionTier().isBlank()) {
                org.setSubscriptionTier(req.subscriptionTier().trim().toUpperCase(Locale.ROOT));
            }
            if (req.industrySector() != null && !req.industrySector().isBlank()) {
                org.setIndustrySector(req.industrySector().trim());
            }
            org.setIsActive(Boolean.TRUE);
            Organisation saved = organisations.save(org);
            Long orgId = saved.getId();

            List<String> skus = List.copyOf(ratifiedSkus());
            LocalDate today = LocalDate.now();
            for (String sku : skus) {
                OrganisationLicense row = new OrganisationLicense();
                row.setOrganisationId(orgId);
                row.setModuleCode(sku);
                // tier / validFrom / enabled are set explicitly and never left to a field
                // initialiser — OrganisationLicense's own javadoc records why: defaults there once
                // let a PATCH of `notes` silently re-enable a disabled module and reset its start
                // date. A provisioning path must state all three.
                row.setTier(DEFAULT_TIER);
                row.setValidFrom(today);
                row.setEnabled(Boolean.TRUE);
                row.setNotes("Provisioned by tenant onboarding (F2-81).");
                licences.save(row);
            }

            UserResponse admin = users.createIn(orgId, new UserRequest(
                req.administrator().username().trim(),
                req.administrator().email().trim(),
                req.administrator().password(),
                req.administrator().fullNameEn().trim(),
                req.administrator().fullNameAr(),
                "Platform Administrator",
                Boolean.TRUE, Boolean.TRUE,
                Set.of("ORG_ADMIN"), null));

            // Written inside the transaction on purpose: if any step above rolls back, the claim
            // that a tenant was onboarded rolls back with it. An audit row for a tenant that does
            // not exist is worse than no audit row.
            //
            // ⚠ This row lands in the HOST OPERATOR's audit trail, not the new tenant's, because
            // AuditLogService derives organisation_id from TenantContext and the context here is
            // still the operator's. Measured, not assumed: the live probe found it under
            // organisation_id = 1 with entity_id = the new org.
            //
            // That is the right trail for F2-81's purpose — the finding's complaint is that
            // provisioning by ad-hoc SQL is not an auditable *operator* process, and this is the
            // operator's change-control record. But it does mean the new tenant's own
            // (organisation-scoped) audit view contains nothing about its creation, so a customer
            // asking "who set this up, and when" cannot answer it from their own trail. Recorded as
            // a follow-on rather than fixed here: writing a second row into the new tenant needs an
            // org-explicit overload on AuditLogService, and inventing one as a side effect of this
            // slice is how a shared service acquires a second contract nobody reviewed.
            audit.log("TENANT_ONBOARDED", "Organisation", orgId,
                "Onboarded '" + saved.getNameEn() + "' (" + code + ") with " + skus.size()
                + " licensed SKUs and first administrator '" + admin.username() + "'");

            log.info("[onboarding] organisation {} ({}) created with {} licence rows, admin user {}",
                orgId, code, skus.size(), admin.id());

            return new OnboardTenantResponse(orgId, code, saved.getNameEn(), saved.getNameAr(),
                admin.id(), admin.username(), skus, skus.size(),
                "Public portal links for this tenant must carry ?org=" + code);
        }
    }
}
