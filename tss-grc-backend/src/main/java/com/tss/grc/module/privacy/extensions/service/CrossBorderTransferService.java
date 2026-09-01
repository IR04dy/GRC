package com.tss.grc.module.privacy.extensions.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.AssessRequest;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.CrossBorderCreate;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.RejectRequest;
import com.tss.grc.module.privacy.extensions.entity.CrossBorderTransfer;
import com.tss.grc.module.privacy.extensions.repository.CrossBorderTransferRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-border transfer lifecycle — PDPL Article 29.
 *
 * <pre>
 *   REQUESTED ──assess()──▶ ASSESSED
 *   ASSESSED  ──approve()─▶ APPROVED   (terminal until expiry)
 *             ──reject()──▶ REJECTED   (terminal; reason mandatory)
 *   APPROVED  ──expire()──▶ EXPIRED    (when expiry_date is past)
 * </pre>
 *
 * <p>Wired into F7 (audit) + F8 (automation). Cross-border transfers
 * are PDPL Article 29's adequacy-decision workflow — exporting
 * personal data outside the Kingdom requires either an SDO-approved
 * destination or one of the lawful safeguards (BCRs, contractual
 * clauses, explicit consent). Every approved transfer is a
 * regulator-watched record.
 *
 * <p>Events:
 *   TRANSFER_REQUESTED  — new transfer record opened
 *   TRANSFER_ASSESSED   — residency / safeguards check completed
 *   TRANSFER_APPROVED   — transfer can proceed (carries destinationCountry)
 *   TRANSFER_REJECTED   — denied
 *   TRANSFER_EXPIRED    — past expiryDate
 */
@Service
@RequiredArgsConstructor
public class CrossBorderTransferService {

    private final CrossBorderTransferRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ── F8 payload + emit ─────────────────────────────────────────────────────

    private Map<String, Object> payloadFor(CrossBorderTransfer t, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("transferId",          nullSafeStr(t.getId()));
        m.put("externalId",          nullSafeStr(t.getExternalId()));
        m.put("ropaCode",            nullSafeStr(t.getRopaCode()));
        m.put("destinationCountry",  nullSafeStr(t.getDestinationCountry()));
        m.put("destinationOrgName",  nullSafeStr(t.getDestinationOrgName()));
        m.put("destinationOrgRole",  nullSafeStr(t.getDestinationOrgRole()));
        m.put("status",              nullSafeStr(t.getStatus()));
        m.put("previousStatus",      nullSafeStr(previousStatus));
        m.put("transferMechanism",   nullSafeStr(t.getTransferMechanism()));
        m.put("dataSubjectsCountEst",nullSafeStr(t.getDataSubjectsCountEst()));
        m.put("expiryDate",          nullSafeStr(t.getExpiryDate()));
        return m;
    }

    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }
    private static String nullSafe(Object o)    { return o == null ? "<not provided>" : o.toString(); }

    private void emit(String trigger, CrossBorderTransfer t, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.getCurrentTenant(), trigger, "CrossBorderTransfer", t.getId(),
            "cbt:" + t.getExternalId(), payloadFor(t, previousStatus)));
    }

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
        t.setExternalId("CBT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        t.setRopaCode(body.ropaCode());
        t.setRopaTitleEn(body.ropaTitleEn());
        t.setDestinationCountry(body.destinationCountry().toUpperCase(Locale.ROOT));
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
        CrossBorderTransfer saved = repo.save(t);
        audit.log("CREATE", "CrossBorderTransfer", saved.getId(),
            "Cross-border transfer requested: " + nullSafe(saved.getExternalId())
                + " → " + nullSafe(saved.getDestinationCountry()) + " (PDPL Article 29)");
        emit("TRANSFER_REQUESTED", saved, null);
        return saved;
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
        CrossBorderTransfer saved = repo.save(t);
        audit.log("TRANSITION", "CrossBorderTransfer", saved.getId(),
            "Transfer assessed: outcome=" + nullSafe(saved.getResidencyCheckOutcome()));
        emit("TRANSFER_ASSESSED", saved, "REQUESTED");
        return saved;
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
        CrossBorderTransfer saved = repo.save(t);
        audit.log("TRANSITION", "CrossBorderTransfer", saved.getId(),
            "Transfer approved by " + nullSafe(approverName)
                + " to " + nullSafe(saved.getDestinationCountry()));
        emit("TRANSFER_APPROVED", saved, "ASSESSED");
        return saved;
    }

    @Transactional
    public CrossBorderTransfer reject(Long id, RejectRequest body) {
        CrossBorderTransfer t = get(id);
        if (!"REQUESTED".equals(t.getStatus()) && !"ASSESSED".equals(t.getStatus())) {
            throw new IllegalTransitionException(t.getStatus(), "REJECTED", Set.of("REQUESTED", "ASSESSED"));
        }
        String previousStatus = t.getStatus();
        t.setStatus("REJECTED");
        t.setRejectionReason(body.rejectionReason());
        CrossBorderTransfer saved = repo.save(t);
        audit.log("TRANSITION", "CrossBorderTransfer", saved.getId(),
            "Transfer rejected: " + nullSafe(saved.getRejectionReason()));
        emit("TRANSFER_REJECTED", saved, previousStatus);
        return saved;
    }

    @Transactional
    public CrossBorderTransfer expire(Long id) {
        CrossBorderTransfer t = get(id);
        if (!"APPROVED".equals(t.getStatus())) return t;
        t.setStatus("EXPIRED");
        CrossBorderTransfer saved = repo.save(t);
        audit.log("TRANSITION", "CrossBorderTransfer", saved.getId(),
            "Transfer expired (past expiry date " + nullSafe(saved.getExpiryDate()) + ")");
        emit("TRANSFER_EXPIRED", saved, "APPROVED");
        return saved;
    }
}
