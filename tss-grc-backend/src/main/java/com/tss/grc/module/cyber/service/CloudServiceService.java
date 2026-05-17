package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CloudService;
import com.tss.grc.module.cyber.repository.CloudServiceRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final Set<String> DEPLOYMENT_MODELS = Set.of("PUBLIC", "PRIVATE", "HYBRID", "COMMUNITY");
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
        for (String s : STATUSES)      out.put("status_" + s.toLowerCase(), repo.countByOrganisationIdAndStatus(org, s));
        for (String c : CRITICALITIES) out.put("crit_"   + c.toLowerCase(), repo.countByOrganisationIdAndCriticality(org, c));
        out.put("ksa_resident", repo.countByOrganisationIdAndKingdomDataResident(org, true));
        out.put("off_kingdom",  repo.countByOrganisationIdAndKingdomDataResident(org, false));
        return out;
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
