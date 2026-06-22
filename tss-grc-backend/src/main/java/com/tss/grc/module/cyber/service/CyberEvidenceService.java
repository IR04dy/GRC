package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberEvidence;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cyber evidence locker.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberEvidenceService {

    private final CyberEvidenceRepository evidenceRepo;

    @Transactional(readOnly = true)
    public Page<CyberEvidence> getEvidence(Long orgId, Pageable p) {
        return evidenceRepo.findByOrganisationId(orgId, p);
    }

    @Transactional
    public CyberEvidence saveEvidence(CyberEvidence e, Long orgId) {
        e.setOrganisationId(orgId);
        e.setIsDeleted(false);
        return evidenceRepo.save(e);
    }

    @Transactional
    public CyberEvidence updateEvidence(Long id, CyberEvidence req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberEvidence existing = evidenceRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberEvidence not found: " + id));
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return evidenceRepo.save(req);
    }

    @Transactional
    public void deleteEvidence(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberEvidence e = evidenceRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberEvidence not found: " + id));
        e.setIsDeleted(true);
        evidenceRepo.save(e);
    }
}
