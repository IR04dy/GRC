package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cyber controls register.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberControlService {

    private final CyberControlRepository controlRepo;

    // PERF-PG-004: hard cap for the unpaginated "/all" dropdown dumps.
    private static final int DROPDOWN_CAP = 500;

    @Transactional(readOnly = true)
    public Page<CyberControl> getControls(Long orgId, Pageable p) {
        return controlRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public List<CyberControl> getAllControls(Long orgId) {
        // PERF-PG-004: cap the dropdown dump (see DROPDOWN_CAP).
        return controlRepo.findByOrganisationId(orgId, PageRequest.of(0, DROPDOWN_CAP)).getContent();
    }

    @Transactional(readOnly = true)
    public CyberControl getControl(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        return controlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberControl not found: " + id));
    }

    @Transactional
    public CyberControl saveControl(CyberControl c, Long orgId) {
        // Mass-assignment defense — see CyberFrameworkService.saveFramework().
        c.setId(null);
        c.setOrganisationId(orgId);
        c.setIsDeleted(false);
        return controlRepo.save(c);
    }

    @Transactional
    public CyberControl updateControl(Long id, CyberControl req) {
        CyberControl existing = getControl(id);
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return controlRepo.save(req);
    }

    @Transactional
    public void deleteControl(Long id) {
        CyberControl c = getControl(id);
        c.setIsDeleted(true);
        controlRepo.save(c);
    }
}
