package com.tss.grc.module.scm.fourthparty.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.fourthparty.dto.FourthPartyExposureDtos.CreateRequest;
import com.tss.grc.module.scm.fourthparty.dto.FourthPartyExposureDtos.UpdateRequest;
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
    public FourthPartyExposure create(CreateRequest req) {
        // Build a fresh entity from the DTO. Server-controlled fields (id,
        // organisationId, status, concentrationRisk, soft-delete, audit and
        // validation columns) are NOT taken from the client (SEC-FIN-004) —
        // they keep their entity defaults / are stamped here.
        FourthPartyExposure e = new FourthPartyExposure();
        e.setPrimaryVendorId(req.primaryVendorId());
        e.setPrimaryVendorName(req.primaryVendorName());
        e.setFourthPartyName(req.fourthPartyName());
        e.setFourthPartyCountry(req.fourthPartyCountry());
        e.setDependencyType(req.dependencyType());
        e.setDownstreamCriticality(req.downstreamCriticality());
        e.setServiceDescriptionEn(req.serviceDescriptionEn());
        e.setServiceDescriptionAr(req.serviceDescriptionAr());
        e.setDataCategoriesShared(req.dataCategoriesShared());
        e.setContractChainClauses(req.contractChainClauses());
        if (req.subContractingAllowed() != null) e.setSubContractingAllowed(req.subContractingAllowed());
        if (req.flowDownClausesPresent() != null) e.setFlowDownClausesPresent(req.flowDownClausesPresent());
        e.setMitigationNotesEn(req.mitigationNotesEn());
        e.setMitigationNotesAr(req.mitigationNotesAr());
        validate(e);
        e.setId(null);
        e.setOrganisationId(TenantContext.getCurrentTenant());
        e.setStatus("DISCOVERED");           // force initial workflow state
        e.setConcentrationRisk("LOW");        // derived by recomputeConcentration()
        e.setIsDeleted((short) 0);
        e.setIsActive((short) 1);
        FourthPartyExposure saved = repo.save(e);
        recomputeConcentration();
        return repo.findByIdAndOrganisationId(saved.getId(), TenantContext.getCurrentTenant())
                .orElse(saved);
    }

    @Transactional
    public FourthPartyExposure update(Long id, UpdateRequest req) {
        FourthPartyExposure existing = get(id);
        if (req.primaryVendorName() != null) existing.setPrimaryVendorName(req.primaryVendorName());
        if (req.fourthPartyName() != null) existing.setFourthPartyName(req.fourthPartyName());
        existing.setFourthPartyCountry(req.fourthPartyCountry());
        if (req.dependencyType() != null) existing.setDependencyType(req.dependencyType());
        if (req.downstreamCriticality() != null) existing.setDownstreamCriticality(req.downstreamCriticality());
        if (req.serviceDescriptionEn() != null) existing.setServiceDescriptionEn(req.serviceDescriptionEn());
        existing.setServiceDescriptionAr(req.serviceDescriptionAr());
        existing.setDataCategoriesShared(req.dataCategoriesShared());
        existing.setContractChainClauses(req.contractChainClauses());
        if (req.subContractingAllowed() != null) existing.setSubContractingAllowed(req.subContractingAllowed());
        if (req.flowDownClausesPresent() != null) existing.setFlowDownClausesPresent(req.flowDownClausesPresent());
        existing.setMitigationNotesEn(req.mitigationNotesEn());
        existing.setMitigationNotesAr(req.mitigationNotesAr());
        validate(existing);
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
