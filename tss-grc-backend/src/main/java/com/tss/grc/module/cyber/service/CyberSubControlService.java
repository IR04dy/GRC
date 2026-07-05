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
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return subControlRepo.save(req);
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
