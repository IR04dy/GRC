package com.tss.grc.module.privacy.extensions.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.AssessRequest;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.CrossBorderCreate;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.RejectRequest;
import com.tss.grc.module.privacy.extensions.entity.CrossBorderTransfer;
import com.tss.grc.module.privacy.extensions.repository.CrossBorderTransferRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-border transfer lifecycle.
 *
 * <pre>
 *   REQUESTED ──assess()──▶ ASSESSED
 *   ASSESSED  ──approve()─▶ APPROVED   (terminal until expiry)
 *             ──reject()──▶ REJECTED   (terminal; reason mandatory)
 *   APPROVED  ──expire()──▶ EXPIRED    (when expiry_date is past)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class CrossBorderTransferService {

    private final CrossBorderTransferRepository repo;

    @Transactional(readOnly = true)
    public Page<CrossBorderTransfer> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public CrossBorderTransfer get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("CrossBorderTransfer " + id));
    }

    @Transactional
    public CrossBorderTransfer create(CrossBorderCreate body, Long createdBy) {
        CrossBorderTransfer t = new CrossBorderTransfer();
        t.setOrganisationId(TenantContext.getCurrentTenant());
        t.setExternalId("CBT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setRopaCode(body.ropaCode());
        t.setRopaTitleEn(body.ropaTitleEn());
        t.setDestinationCountry(body.destinationCountry().toUpperCase());
        t.setDestinationOrgName(body.destinationOrgName());
        t.setDestinationOrgRole(body.destinationOrgRole());
        t.setDataCategories(body.dataCategories());
        t.setDataSubjectsCountEst(body.dataSubjectsCountEst());
        t.setTransferMechanism(body.transferMechanism());
        t.setSafeguardsEn(body.safeguardsEn());
        t.setSafeguardsAr(body.safeguardsAr());
        t.setJustificationEn(body.justificationEn());
        t.setJustificationAr(body.justificationAr());
        t.setEffectiveFrom(body.effectiveFrom());
        t.setExpiryDate(body.expiryDate());
        t.setCreatedBy(createdBy);
        t.setStatus("REQUESTED");
        return repo.save(t);
    }

    @Transactional
    public CrossBorderTransfer assess(Long id, AssessRequest body) {
        CrossBorderTransfer t = get(id);
        if (!"REQUESTED".equals(t.getStatus())) {
            throw new IllegalTransitionException(t.getStatus(), "ASSESSED", Set.of("REQUESTED"));
        }
        t.setStatus("ASSESSED");
        t.setResidencyCheckAt(body.residencyCheckAt() != null ? body.residencyCheckAt() : LocalDateTime.now());
        t.setResidencyCheckBy(body.residencyCheckBy());
        t.setResidencyCheckOutcome(body.residencyCheckOutcome());
        return repo.save(t);
    }

    @Transactional
    public CrossBorderTransfer approve(Long id, Long approverId, String approverName) {
        CrossBorderTransfer t = get(id);
        if (!"ASSESSED".equals(t.getStatus())) {
            throw new IllegalTransitionException(t.getStatus(), "APPROVED", Set.of("ASSESSED"));
        }
        t.setStatus("APPROVED");
        t.setApprovedAt(LocalDateTime.now());
        t.setApprovedBy(approverId);
        t.setApproverName(approverName);
        return repo.save(t);
    }

    @Transactional
    public CrossBorderTransfer reject(Long id, RejectRequest body) {
        CrossBorderTransfer t = get(id);
        if (!"REQUESTED".equals(t.getStatus()) && !"ASSESSED".equals(t.getStatus())) {
            throw new IllegalTransitionException(t.getStatus(), "REJECTED", Set.of("REQUESTED", "ASSESSED"));
        }
        t.setStatus("REJECTED");
        t.setRejectionReason(body.rejectionReason());
        return repo.save(t);
    }

    @Transactional
    public CrossBorderTransfer expire(Long id) {
        CrossBorderTransfer t = get(id);
        if (!"APPROVED".equals(t.getStatus())) return t;
        t.setStatus("EXPIRED");
        return repo.save(t);
    }
}
