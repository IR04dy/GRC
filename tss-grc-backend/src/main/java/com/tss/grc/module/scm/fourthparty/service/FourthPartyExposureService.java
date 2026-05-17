package com.tss.grc.module.scm.fourthparty.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import com.tss.grc.module.scm.fourthparty.repository.FourthPartyExposureRepository;
import com.tss.grc.module.tprm.automation.TprmAutomationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service surface for {@link FourthPartyExposure}.
 *
 * <p>{@code concentration_risk} is derived: when the same fourth party appears
 * behind ≥3 primaries it's auto-elevated to HIGH; ≥2 → MEDIUM; otherwise LOW.
 * Re-runs every time we save.
 */
@Service
@RequiredArgsConstructor
public class FourthPartyExposureService {

    private static final Set<String> DEPENDENCY_TYPES = Set.of(
            "DATA_PROCESSING", "INFRASTRUCTURE", "SOFTWARE", "LOGISTICS", "FINANCIAL", "SUPPORT");
    private static final Set<String> CRIT = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES = Set.of("DISCOVERED", "VALIDATED", "MITIGATED", "RETIRED");

    private final FourthPartyExposureRepository repo;
    @Autowired(required = false) private TprmAutomationService automation;

    @Transactional(readOnly = true)
    public Page<FourthPartyExposure> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByConcentrationRiskDescDownstreamCriticalityDesc(
                TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<FourthPartyExposure> listByStatus(String status, Pageable pageable) {
        return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), status, pageable);
    }

    @Transactional(readOnly = true)
    public FourthPartyExposure get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("FourthPartyExposure " + id));
    }

    @Transactional
    public FourthPartyExposure create(FourthPartyExposure req) {
        validate(req);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setId(null);
        FourthPartyExposure saved = repo.save(req);
        recomputeConcentration();
        return repo.findByIdAndOrganisationId(saved.getId(), TenantContext.getCurrentTenant())
                .orElse(saved);
    }

    @Transactional
    public FourthPartyExposure update(Long id, FourthPartyExposure req) {
        validate(req);
        FourthPartyExposure existing = get(id);
        existing.setPrimaryVendorName(req.getPrimaryVendorName());
        existing.setFourthPartyName(req.getFourthPartyName());
        existing.setFourthPartyCountry(req.getFourthPartyCountry());
        existing.setDependencyType(req.getDependencyType());
        existing.setDownstreamCriticality(req.getDownstreamCriticality());
        existing.setServiceDescriptionEn(req.getServiceDescriptionEn());
        existing.setServiceDescriptionAr(req.getServiceDescriptionAr());
        existing.setDataCategoriesShared(req.getDataCategoriesShared());
        existing.setContractChainClauses(req.getContractChainClauses());
        existing.setSubContractingAllowed(req.getSubContractingAllowed());
        existing.setFlowDownClausesPresent(req.getFlowDownClausesPresent());
        existing.setMitigationNotesEn(req.getMitigationNotesEn());
        existing.setMitigationNotesAr(req.getMitigationNotesAr());
        FourthPartyExposure saved = repo.save(existing);
        recomputeConcentration();
        return repo.findByIdAndOrganisationId(saved.getId(), TenantContext.getCurrentTenant())
                .orElse(saved);
    }

    @Transactional
    public FourthPartyExposure validate(Long id, String validatorName) {
        FourthPartyExposure ex = get(id);
        ex.setStatus("VALIDATED");
        ex.setLastValidatedAt(LocalDateTime.now());
        ex.setValidatorName(validatorName);
        return repo.save(ex);
    }

    @Transactional
    public FourthPartyExposure mitigate(Long id, String notesEn) {
        FourthPartyExposure ex = get(id);
        ex.setStatus("MITIGATED");
        ex.setMitigationNotesEn(notesEn);
        return repo.save(ex);
    }

    @Transactional
    public FourthPartyExposure retire(Long id) {
        FourthPartyExposure ex = get(id);
        ex.setStatus("RETIRED");
        return repo.save(ex);
    }

    private void validate(FourthPartyExposure r) {
        if (r.getDependencyType() == null || !DEPENDENCY_TYPES.contains(r.getDependencyType()))
            throw new IllegalArgumentException("dependencyType must be one of " + DEPENDENCY_TYPES);
        if (r.getDownstreamCriticality() == null || !CRIT.contains(r.getDownstreamCriticality()))
            throw new IllegalArgumentException("downstreamCriticality must be one of " + CRIT);
        if (r.getStatus() != null && !STATUSES.contains(r.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
    }

    /** Recompute concentration tier across the whole tenant. */
    private void recomputeConcentration() {
        Long org = TenantContext.getCurrentTenant();
        List<Object[]> rows = repo.concentrationCounts(org);
        Map<String, Long> counts = new HashMap<>();
        for (Object[] r : rows) counts.put((String) r[0], ((Number) r[1]).longValue());

        Page<FourthPartyExposure> all = repo.findByOrganisationIdOrderByConcentrationRiskDescDownstreamCriticalityDesc(
                org, Pageable.unpaged());
        for (FourthPartyExposure ex : all) {
            long n = counts.getOrDefault(ex.getFourthPartyName(), 1L);
            String tier = n >= 3 ? "HIGH" : n == 2 ? "MEDIUM" : "LOW";
            String previous = ex.getConcentrationRisk();
            if (!tier.equals(previous)) {
                ex.setConcentrationRisk(tier);
                FourthPartyExposure saved = repo.save(ex);
                // Enhancement E: only fire the auto-CAPA on the rising edge —
                // when concentration is newly elevated to HIGH.
                if ("HIGH".equals(tier) && !"HIGH".equals(previous) && automation != null) {
                    automation.onFourthPartyHighConcentration(saved);
                }
            }
        }
    }
}
