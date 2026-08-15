package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberSubControl;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberSubControlRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cyber sub-controls register.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberSubControlService {

    private final CyberSubControlRepository subControlRepo;
    /** CY-H — verify the parent {@code control_id} references a real control in this tenant. */
    private final CyberControlRepository controlRepo;

    @Transactional(readOnly = true)
    public Page<CyberSubControl> getSubControls(Long orgId, Pageable p) {
        return subControlRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public Page<CyberSubControl> getSubControlsByControl(Long orgId, Long controlId, Pageable p) {
        return subControlRepo.findByOrganisationIdAndControlId(orgId, controlId, p);
    }

    @Transactional
    public CyberSubControl saveSubControl(CyberSubControl s, Long orgId) {
        // CY-H — title + parent control are NOT NULL; reject a bad body with 400.
        s.setTitleEn(CyberInputValidation.requireText(s.getTitleEn(), "titleEn"));
        CyberInputValidation.requireId(s.getControlId(), "controlId");
        if (controlRepo.findByIdAndOrganisationId(s.getControlId(), orgId).isEmpty()) {
            throw new IllegalArgumentException(
                    "controlId " + s.getControlId() + " does not reference an existing control");
        }
        // Mass-assignment defense — see CyberFrameworkService.saveFramework().
        s.setId(null);
        s.setOrganisationId(orgId);
        s.setIsDeleted(false);
        return subControlRepo.save(s);
    }

    @Transactional
    public CyberSubControl updateSubControl(Long id, CyberSubControl req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberSubControl existing = subControlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberSubControl not found: " + id));
        applyEditable(existing, req);
        return subControlRepo.save(existing);
    }

    /**
     * F2-213 — the fields an operator can actually edit through this endpoint.
     *
     * Persisting the REQUEST OBJECT nulled `descriptionAr`, which `CyberSubControlRequest`
     * does not declare — so editing an NCA ECC sub-control through the English form deleted
     * its Arabic description, in a platform whose primary language is Arabic (house rule 7).
     * Preserved here; offering it means adding the input, which is filed, not invented.
     */
    private void applyEditable(CyberSubControl existing, CyberSubControl req) {
        existing.setControlId(req.getControlId());
        existing.setSubControlNumber(req.getSubControlNumber());
        existing.setTitleEn(req.getTitleEn());
        existing.setTitleAr(req.getTitleAr());
        existing.setDescriptionEn(req.getDescriptionEn());
        existing.setControlOwner(req.getControlOwner());
        existing.setImplementationStatus(req.getImplementationStatus());
        existing.setMaturityLevel(req.getMaturityLevel());
        existing.setEvidenceRequired(req.getEvidenceRequired());
    }

    @Transactional
    public void deleteSubControl(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberSubControl s = subControlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberSubControl not found: " + id));
        s.setIsDeleted(true);
        subControlRepo.save(s);
    }
}
