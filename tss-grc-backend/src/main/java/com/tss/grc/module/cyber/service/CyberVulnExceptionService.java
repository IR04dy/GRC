package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.VulnException;
import com.tss.grc.module.cyber.repository.VulnExceptionRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Vulnerability exceptions (risk-accept / deferral) + approval.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberVulnExceptionService {

    private final VulnExceptionRepository exceptionRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    @Transactional(readOnly = true)
    public Page<VulnException> getExceptions(Long orgId, Pageable p) {
        return exceptionRepo.findByOrganisationId(orgId, p);
    }

    @Transactional
    public VulnException saveException(VulnException e, Long orgId) {
        // CY-H — title_en is NOT NULL; reject a blank/empty body with 400 not a DB 500.
        e.setTitleEn(CyberInputValidation.requireText(e.getTitleEn(), "titleEn"));
        e.setOrganisationId(orgId);
        e.setIsDeleted(false);
        return exceptionRepo.save(e);
    }

    @Transactional
    public VulnException approveException(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        VulnException e = exceptionRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("VulnException not found: " + id));
        e.setWorkflowStatus("APPROVED");
        e.setApprovedAt(LocalDate.now());
        VulnException saved = exceptionRepo.save(e);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "vuln_exception", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public VulnException updateException(Long id, VulnException req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        VulnException existing = exceptionRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("VulnException not found: " + id));
        applyEditable(existing, req);
        return exceptionRepo.save(existing);
    }

    /**
     * F2-213 — the fields an operator can actually edit through this endpoint.
     *
     * Persisting the REQUEST OBJECT nulled `approvedAt` — the timestamp recording when a
     * vulnerability exception (a formal decision to accept a known exposure) was approved.
     * `CyberExceptionRequest` cannot carry it, so any later edit of the exception erased the
     * evidence of its own approval while leaving `approverName` and `workflowStatus` saying it
     * had been approved. The stamp belongs to the approve path.
     */
    private void applyEditable(VulnException existing, VulnException req) {
        existing.setVulnerabilityId(req.getVulnerabilityId());
        existing.setTitleEn(req.getTitleEn());
        existing.setExceptionReason(req.getExceptionReason());
        existing.setWorkflowStatus(req.getWorkflowStatus());
        existing.setOwnerName(req.getOwnerName());
        existing.setApproverName(req.getApproverName());
        existing.setCompensatingControls(req.getCompensatingControls());
        existing.setImpactAssessment(req.getImpactAssessment());
        existing.setExpiryDate(req.getExpiryDate());
    }

    @Transactional
    public void deleteException(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        VulnException e = exceptionRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("VulnException not found: " + id));
        e.setIsDeleted(true);
        exceptionRepo.save(e);
    }
}
