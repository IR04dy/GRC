package com.tss.grc.module.ndi.integration;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Cross-module lookup helpers for the NDI module.
 *
 * <p>NDI tables reserve {@code risk_id}, {@code corrective_action_id},
 * and {@code evidence_ref} columns as soft FKs. This service is the
 * single place where those soft links are validated against their real
 * targets (ERM risk, audit_lc CAPA, platform evidence) — keeps the
 * tenant scoping consistent and avoids each NDI service needing its
 * own ERM/Audit/Evidence imports.
 */
@Service
public class NdiCrossLinkService {

    private final RiskRepository risks;

    public NdiCrossLinkService(RiskRepository risks) { this.risks = risks; }

    public record LinkedRisk(
        Long id,
        String externalId,
        String titleEn,
        String titleAr,
        String status,
        String sourceModule,
        Integer inherentLikelihood,
        Integer inherentImpact,
        Long ownerId
    ) {
        public static LinkedRisk from(Risk r) {
            return new LinkedRisk(
                r.getId(),
                r.getExternalId() == null ? null : r.getExternalId().toString(),
                r.getTitleEn(),
                r.getTitleAr(),
                r.getStatus(),
                r.getSourceModule(),
                r.getInherentLikelihood(),
                r.getInherentImpact(),
                r.getOwnerId());
        }
    }

    /**
     * Fetch the ERM risk row pointed to by an NDI gap's risk_id (or compliance
     * row's risk_id). Returns null when the id is null. Throws if the id is
     * non-null but doesn't resolve to a tenant-scoped Risk — that's a
     * soft-FK integrity violation, surface it loudly to the caller.
     */
    @Transactional(readOnly = true)
    public LinkedRisk getLinkedRisk(Long riskId) {
        if (riskId == null) return null;
        Long org = TenantContext.getCurrentTenant();
        return risks.findById(riskId)
            .filter(r -> Objects.equals(r.getOrganisationId(), org))
            .map(LinkedRisk::from)
            .orElseThrow(() -> new IllegalArgumentException(
                "Risk " + riskId + " not found in this organisation (broken NDI soft FK)"));
    }

    /**
     * Validate that risk_id (if non-null) points to a real ERM risk row.
     * Use from NDI service write paths before persisting.
     */
    @Transactional(readOnly = true)
    public void validateRiskExists(Long riskId) {
        if (riskId == null) return;
        Long org = TenantContext.getCurrentTenant();
        Optional<Risk> r = risks.findById(riskId);
        if (r.isEmpty() || !Objects.equals(r.get().getOrganisationId(), org))
            throw new IllegalArgumentException("risk_id " + riskId + " does not exist or belongs to another organisation");
    }
}
