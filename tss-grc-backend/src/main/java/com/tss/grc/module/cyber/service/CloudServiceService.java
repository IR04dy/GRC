package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CloudService;
import com.tss.grc.module.cyber.repository.CloudServiceRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cloud service inventory CRUD — basic catalogue management.
 *
 * <p>Validation enforces the enums on {@code serviceModel}, {@code deploymentModel},
 * {@code criticality} and {@code status}. Other fields are free-form so each tenant
 * can codify its own cyber/privacy taxonomy without backend changes.
 */
@Service
@RequiredArgsConstructor
public class CloudServiceService {

    private static final Set<String> SERVICE_MODELS    = Set.of("IAAS", "PAAS", "SAAS", "FAAS", "DBAAS");
    // GOV_PRIVATE is the Saudi government private cloud (the National Government Cloud / SDAIA
    // NDMO platforms). F2-238 measured it stored on 3 of the 15 live rows in BOTH databases while
    // this Set refused it and `cyber.cloud_services` carries no CHECK constraint at all — so the
    // only narrowing was wrong about what the register holds (F2-180, inverted).
    //
    // ⚠ IT WAS THE RESET THAT HID THIS. update() ends with validate(existing), and until F2-238 the
    // never-firing null-guard overwrote deploymentModel with the entity initialiser "PUBLIC" before
    // validate() ever saw the stored value. Preserving GOV_PRIVATE — the correct behaviour — is
    // therefore what makes validate() reachable for it, so declaring the token here is not a
    // separate tidy-up: omit it and the fix below turns a silent reset into a 400 on the National
    // Government Cloud (F2-203 — fixing one defect detonates the next).
    private static final Set<String> DEPLOYMENT_MODELS =
        Set.of("PUBLIC", "PRIVATE", "HYBRID", "COMMUNITY", "GOV_PRIVATE");
    private static final Set<String> CRITICALITIES     = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES          = Set.of("ACTIVE", "UNDER_REVIEW", "DEPROVISIONED", "ARCHIVED");

    private final CloudServiceRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public List<CloudService> list() {
        return repo.findByOrganisationIdOrderByServiceNameAsc(TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public CloudService get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CloudService not found: " + id));
    }

    @Transactional
    public CloudService create(CloudService body) {
        applyCreateDefaults(body);
        validate(body);
        body.setId(null);
        body.setOrganisationId(TenantContext.getCurrentTenant());
        CloudService saved = repo.save(body);
        audit.log("CREATE", "CloudService", saved.getId(),
            "Cloud service registered: " + saved.getServiceName() + " (" + saved.getProvider() + ")");
        return saved;
    }

    @Transactional
    public CloudService update(Long id, CloudService patch) {
        CloudService existing = get(id);
        if (patch.getServiceName()         != null) existing.setServiceName(patch.getServiceName());
        if (patch.getProvider()            != null) existing.setProvider(patch.getProvider());
        if (patch.getServiceModel()        != null) existing.setServiceModel(patch.getServiceModel());
        if (patch.getDeploymentModel()     != null) existing.setDeploymentModel(patch.getDeploymentModel());
        if (patch.getDataClassification()  != null) existing.setDataClassification(patch.getDataClassification());
        if (patch.getResidencyRegion()     != null) existing.setResidencyRegion(patch.getResidencyRegion());
        if (patch.getKingdomDataResident() != null) existing.setKingdomDataResident(patch.getKingdomDataResident());
        if (patch.getCsccTier()            != null) existing.setCsccTier(patch.getCsccTier());
        if (patch.getContractId()          != null) existing.setContractId(patch.getContractId());
        if (patch.getOwnerUserId()         != null) existing.setOwnerUserId(patch.getOwnerUserId());
        if (patch.getCriticality()         != null) existing.setCriticality(patch.getCriticality());
        if (patch.getSsoEnabled()          != null) existing.setSsoEnabled(patch.getSsoEnabled());
        if (patch.getMfaEnforced()         != null) existing.setMfaEnforced(patch.getMfaEnforced());
        if (patch.getEncryptionAtRest()    != null) existing.setEncryptionAtRest(patch.getEncryptionAtRest());
        if (patch.getEncryptionInTransit() != null) existing.setEncryptionInTransit(patch.getEncryptionInTransit());
        if (patch.getStatus()              != null) existing.setStatus(patch.getStatus());
        if (patch.getLastReviewDate()      != null) existing.setLastReviewDate(patch.getLastReviewDate());
        if (patch.getNextReviewDate()      != null) existing.setNextReviewDate(patch.getNextReviewDate());
        if (patch.getNotes()               != null) existing.setNotes(patch.getNotes());
        validate(existing);
        CloudService saved = repo.save(existing);
        audit.log("UPDATE", "CloudService", saved.getId(),
            "Cloud service updated: " + saved.getServiceName());
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Long> out = new HashMap<>();
        for (String s : STATUSES)      out.put("status_" + s.toLowerCase(Locale.ROOT), repo.countByOrganisationIdAndStatus(org, s));
        for (String c : CRITICALITIES) out.put("crit_"   + c.toLowerCase(Locale.ROOT), repo.countByOrganisationIdAndCriticality(org, c));
        out.put("ksa_resident", repo.countByOrganisationIdAndKingdomDataResident(org, true));
        out.put("off_kingdom",  repo.countByOrganisationIdAndKingdomDataResident(org, false));
        return out;
    }

    /**
     * The nine server-owned defaults that used to live as field initialisers on {@link CloudService}.
     *
     * <p><b>F2-238.</b> They moved here because an initialiser is what an omitted key deserialises
     * to, so {@code if (patch.getSsoEnabled() != null)} in {@link #update} could never be false and
     * a PATCH that named only {@code status} rewrote eight other columns. Measured in dev and prod
     * (identical, 15 live rows): SSO, MFA and encryption-at-rest were true on <b>14 of 15</b>,
     * kingdom-data-residency on <b>10 of 15</b>, and the only write the register's screen offers is
     * {@code update(id, { status })}.
     *
     * <p>Applied on the create path only, so the entity carries no default a partial update can
     * mistake for a value the caller supplied. Every field here is NOT NULL in the schema, so this
     * method is what keeps a create legal — do not inline it into {@code validate}, which also runs
     * on update, where filling a null would be the defect all over again.
     */
    private void applyCreateDefaults(CloudService c) {
        if (c.getServiceModel()        == null) c.setServiceModel("SAAS");
        if (c.getDeploymentModel()     == null) c.setDeploymentModel("PUBLIC");
        if (c.getCriticality()         == null) c.setCriticality("MEDIUM");
        if (c.getStatus()              == null) c.setStatus("ACTIVE");
        if (c.getKingdomDataResident() == null) c.setKingdomDataResident(false);
        if (c.getSsoEnabled()          == null) c.setSsoEnabled(false);
        if (c.getMfaEnforced()         == null) c.setMfaEnforced(false);
        if (c.getEncryptionAtRest()    == null) c.setEncryptionAtRest(false);
        // ⚠ true, unlike its four siblings. That asymmetry is why the reset was the dangerous
        // direction here: every other flag reset to false LOST an assurance, but this one reset to
        // true would MANUFACTURE one — a service recorded as not encrypting in transit becoming one
        // that does. 0 of 15 rows hold false today, so it is the latent half of the same defect.
        if (c.getEncryptionInTransit() == null) c.setEncryptionInTransit(true);
    }

    private void validate(CloudService c) {
        if (c.getServiceName() == null || c.getServiceName().isBlank())
            throw new IllegalArgumentException("serviceName is required");
        if (c.getProvider() == null || c.getProvider().isBlank())
            throw new IllegalArgumentException("provider is required");
        if (c.getServiceModel() != null && !SERVICE_MODELS.contains(c.getServiceModel()))
            throw new IllegalArgumentException("serviceModel must be one of " + SERVICE_MODELS);
        if (c.getDeploymentModel() != null && !DEPLOYMENT_MODELS.contains(c.getDeploymentModel()))
            throw new IllegalArgumentException("deploymentModel must be one of " + DEPLOYMENT_MODELS);
        if (c.getCriticality() != null && !CRITICALITIES.contains(c.getCriticality()))
            throw new IllegalArgumentException("criticality must be one of " + CRITICALITIES);
        if (c.getStatus() != null && !STATUSES.contains(c.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
    }
}
