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
        // CY-H — title_en is NOT NULL; reject a blank/empty body with 400 not a DB 500.
        c.setTitleEn(CyberInputValidation.requireText(c.getTitleEn(), "titleEn"));
        // Mass-assignment defense — see CyberFrameworkService.saveFramework().
        c.setId(null);
        c.setOrganisationId(orgId);
        c.setIsDeleted(false);
        return controlRepo.save(c);
    }

    @Transactional
    /**
     * F2-213 — the fields an operator can actually edit through this endpoint.
     *
     * ⚠ This is the one member of the family that was NOT losing data: `CyberControlRequest`
     * happens to declare all thirteen editable columns, so nothing the client omits was in
     * fact omitted. It was converted anyway, because persisting the REQUEST OBJECT leaves
     * the hazard latent rather than absent — the day somebody adds a column to
     * `CyberControl` without adding it to the frontend type, every edit starts erasing it,
     * silently. Closing a harmless drift is cheaper than the reader who later has to work
     * out why four siblings are one shape and this one another.
     */
    private void applyEditable(CyberControl existing, CyberControl req) {
        existing.setFrameworkId(req.getFrameworkId());
        existing.setFrameworkName(req.getFrameworkName());
        existing.setDomain(req.getDomain());
        existing.setControlNumber(req.getControlNumber());
        existing.setTitleEn(req.getTitleEn());
        existing.setTitleAr(req.getTitleAr());
        existing.setProcedureEn(req.getProcedureEn());
        existing.setControlType(req.getControlType());
        existing.setControlOwner(req.getControlOwner());
        existing.setReviewFrequency(req.getReviewFrequency());
        existing.setMaturityLevel(req.getMaturityLevel());
        existing.setImplementationStatus(req.getImplementationStatus());
        existing.setStatus(req.getStatus());
    }

    public CyberControl updateControl(Long id, CyberControl req) {
        CyberControl existing = getControl(id);
        applyEditable(existing, req);
        return controlRepo.save(existing);
    }

    @Transactional
    public void deleteControl(Long id) {
        CyberControl c = getControl(id);
        c.setIsDeleted(true);
        controlRepo.save(c);
    }
}
