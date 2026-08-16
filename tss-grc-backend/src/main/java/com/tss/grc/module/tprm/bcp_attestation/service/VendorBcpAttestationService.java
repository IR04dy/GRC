package com.tss.grc.module.tprm.bcp_attestation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationResponse;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationReviewRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationSubmitRequest;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpCampaign;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpAttestationRepository;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpCampaignRepository;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Slice 10-D — Per-row vendor BCP attestation service.
 *
 * <p>Handles the vendor-side submission (token-authenticated via the
 * portal) and the TPRM Lead's review outcomes. Each mutation writes an
 * F7 audit row and emits an F8 event so the lifecycle is traceable.
 *
 * <h3>Per-row state machine</h3>
 * <pre>
 *   PENDING_INVITE  → INVITED (driven by campaign open)
 *   INVITED         → REMINDED | SUBMITTED | NA_OUT_OF_SCOPE | OVERDUE
 *   REMINDED        → SUBMITTED | NA_OUT_OF_SCOPE | OVERDUE | ESCALATED
 *   OVERDUE         → SUBMITTED | NA_OUT_OF_SCOPE | ESCALATED
 *   SUBMITTED       → ACCEPTED | REJECTED | NA_OUT_OF_SCOPE
 *   ACCEPTED        (terminal)
 *   REJECTED        → INVITED  (re-invite allowed)
 *   NA_OUT_OF_SCOPE (terminal)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class VendorBcpAttestationService {

    private static final Logger log = LoggerFactory.getLogger(VendorBcpAttestationService.class);

    private final VendorBcpAttestationRepository attestationRepo;
    private final VendorBcpCampaignRepository campaignRepo;
    private final VendorBcpCampaignService campaignService;
    private final VendorRepository vendorRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttestationResponse> listForCampaign(Long campaignId) {
        return attestationRepo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByCreatedAtAsc(
                org(), campaignId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AttestationResponse> listForCampaignByStatus(Long campaignId, String status) {
        return attestationRepo.findByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
                org(), campaignId, status)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AttestationResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    /**
     * Token-authenticated read used by the vendor portal stub. Returns the
     * attestation regardless of tenant context — the token IS the credential.
     */
    @Transactional(readOnly = true)
    public AttestationResponse getByToken(UUID token) {
        VendorBcpAttestation a = attestationRepo.findBySubmissionTokenAndIsDeletedFalse(token)
            .orElseThrow(() -> new EntityNotFoundException("Invalid submission token"));
        return toResponse(a);
    }

    // ─── Vendor-side submission ─────────────────────────────────────

    /**
     * Submit attestation by attestation id (TPRM Lead manual entry path).
     */
    @Transactional
    public AttestationResponse submitAttestation(Long attestationId, AttestationSubmitRequest req) {
        VendorBcpAttestation a = loadOrThrow(attestationId);
        return doSubmit(a, req);
    }

    /**
     * Submit attestation by token (vendor portal path — no tenant context
     * required; the token resolves the row).
     */
    @Transactional
    public AttestationResponse submitByToken(UUID token, AttestationSubmitRequest req) {
        VendorBcpAttestation a = attestationRepo.findBySubmissionTokenAndIsDeletedFalse(token)
            .orElseThrow(() -> new EntityNotFoundException("Invalid submission token"));
        return doSubmit(a, req);
    }

    private AttestationResponse doSubmit(VendorBcpAttestation a, AttestationSubmitRequest req) {
        String from = a.getStatus();
        guardSubmit(from);

        LocalDateTime now = LocalDateTime.now();
        a.setRtoHoursAttested(req.rtoHoursAttested());
        a.setMtpdHoursAttested(req.mtpdHoursAttested());
        a.setHasTestedBcp(req.hasTestedBcp());
        a.setLastBcpTestDate(req.lastBcpTestDate());
        a.setLastDrTestDate(req.lastDrTestDate());
        a.setSubprocessorDependencies(req.subprocessorDependencies());
        a.setEvidenceFilePath(req.evidenceFilePath());
        a.setEvidenceFileName(req.evidenceFileName());
        a.setSubmittedByContactEmail(req.submittedByContactEmail());
        a.setSubmittedByContactName(req.submittedByContactName());
        a.setSubmittedAt(now);
        a.setUpdatedAt(now);

        // Breach detection — attested > contractual SLA snapshot.
        a.setRtoBreach(a.getContractualRtoHours() != null
            && req.rtoHoursAttested() != null
            && req.rtoHoursAttested() > a.getContractualRtoHours());
        a.setMtpdBreach(a.getContractualMtpdHours() != null
            && req.mtpdHoursAttested() != null
            && req.mtpdHoursAttested() > a.getContractualMtpdHours());

        a.setStatus("SUBMITTED");
        VendorBcpAttestation saved = attestationRepo.save(a);

        // Update campaign-level rollups for the dashboard. (Cross-row
        // counts so a re-submission doesn't double-count.)
        VendorBcpCampaign c = campaignRepo.findById(saved.getCampaignId()).orElse(null);
        if (c != null) campaignService.recomputeRollups(c);

        audit.log("TRANSITION", "VendorBcpAttestation", saved.getId(),
            "Attestation submitted: vendor " + saved.getVendorId()
                + ", campaign " + saved.getCampaignId() + " (RTO=" + saved.getRtoHoursAttested()
                + "h, MTPD=" + saved.getMtpdHoursAttested() + "h"
                + (saved.getRtoBreach() ? ", RTO BREACH" : "")
                + (saved.getMtpdBreach() ? ", MTPD BREACH" : "") + ")");
        emit("VENDOR_BCP_ATTESTATION_SUBMITTED", saved, from);
        return toResponse(saved);
    }

    // ─── Reviewer outcomes ──────────────────────────────────────────

    @Transactional
    public AttestationResponse acceptAttestation(Long id, AttestationReviewRequest req) {
        return transitionAttestation(id, "ACCEPTED", req,
            "VENDOR_BCP_ATTESTATION_ACCEPTED",
            "Attestation accepted");
    }

    @Transactional
    public AttestationResponse rejectAttestation(Long id, AttestationReviewRequest req) {
        return transitionAttestation(id, "REJECTED", req,
            "VENDOR_BCP_ATTESTATION_REJECTED",
            "Attestation rejected");
    }

    @Transactional
    public AttestationResponse markOutOfScope(Long id, AttestationReviewRequest req) {
        return transitionAttestation(id, "NA_OUT_OF_SCOPE", req,
            "VENDOR_BCP_ATTESTATION_NA",
            "Attestation marked NOT APPLICABLE / OUT OF SCOPE");
    }

    private AttestationResponse transitionAttestation(Long id, String to, AttestationReviewRequest req,
                                                       String triggerName, String auditPrefix) {
        VendorBcpAttestation a = loadOrThrow(id);
        String from = a.getStatus();
        guardReview(from, to);

        LocalDateTime now = LocalDateTime.now();
        a.setStatus(to);
        if (req != null) {
            a.setReviewerCommentEn(req.reviewerCommentEn());
            a.setReviewerCommentAr(req.reviewerCommentAr());
        }
        // F2-224 — the reviewer is the authenticated principal, never a body field. All three
        // routes reaching here (accept / reject / mark-out-of-scope) are guarded by
        // TPRM:WRITE, so a principal always exists; the frontend was asking the operator to
        // TYPE a reviewer name into a prompt placeholdered with an invented officer.
        //
        // ⚠ Do NOT copy this to doSubmit(). That method's other caller is submitByToken, whose
        // route is @PreAuthorize("permitAll()") — the vendor portal, where the submitter is an
        // external contact and there is no principal to resolve at all. The two halves of this
        // class genuinely differ, which is why only one of them was closed.
        a.setReviewedByName(CurrentActor.username());
        a.setReviewedByUserId(CurrentActor.userId());
        a.setReviewedAt(now);
        a.setUpdatedAt(now);

        VendorBcpAttestation saved = attestationRepo.save(a);

        VendorBcpCampaign c = campaignRepo.findById(saved.getCampaignId()).orElse(null);
        if (c != null) campaignService.recomputeRollups(c);

        audit.log("TRANSITION", "VendorBcpAttestation", saved.getId(),
            auditPrefix + ": vendor " + saved.getVendorId() + ", campaign " + saved.getCampaignId()
                + (req != null && req.reviewerCommentEn() != null ? " — " + req.reviewerCommentEn() : ""));
        emit(triggerName, saved, from);
        return toResponse(saved);
    }

    // ─── Guards ─────────────────────────────────────────────────────

    private static void guardSubmit(String from) {
        Set<String> allowed = Set.of("INVITED", "REMINDED", "OVERDUE", "REJECTED");
        if (!allowed.contains(Objects.requireNonNullElse(from, ""))) {
            throw new IllegalTransitionException(from, "SUBMITTED", allowed);
        }
    }

    static void guardReview(String from, String to) {
        Set<String> allowed = switch (Objects.requireNonNullElse(from, "")) {
            case "SUBMITTED"      -> Set.of("ACCEPTED", "REJECTED", "NA_OUT_OF_SCOPE");
            // NA can be marked from any non-terminal state by the reviewer.
            case "INVITED", "REMINDED", "OVERDUE" -> Set.of("NA_OUT_OF_SCOPE");
            default                -> Set.of();
        };
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    // ─── Internal helpers ───────────────────────────────────────────

    private VendorBcpAttestation loadOrThrow(Long id) {
        return attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Attestation not found: " + id));
    }

    private void emit(String trigger, VendorBcpAttestation a, String previousStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("attestationId", String.valueOf(a.getId()));
        payload.put("campaignId", String.valueOf(a.getCampaignId()));
        payload.put("vendorId", String.valueOf(a.getVendorId()));
        payload.put("status", a.getStatus());
        payload.put("previousStatus", previousStatus == null ? "" : previousStatus);
        payload.put("rtoHoursAttested", String.valueOf(a.getRtoHoursAttested()));
        payload.put("mtpdHoursAttested", String.valueOf(a.getMtpdHoursAttested()));
        payload.put("rtoBreach", String.valueOf(a.getRtoBreach()));
        payload.put("mtpdBreach", String.valueOf(a.getMtpdBreach()));
        payload.put("hasTestedBcp", String.valueOf(a.getHasTestedBcp()));
        publisher.publishEvent(new DomainEvent(
            a.getOrganisationId(), trigger, "VendorBcpAttestation", a.getId(),
            "vendor-bcp-attestation:" + a.getId(), payload));
    }

    private AttestationResponse toResponse(VendorBcpAttestation a) {
        Vendor v = vendorRepo.findById(a.getVendorId()).orElse(null);
        return new AttestationResponse(
            a.getId(), a.getExternalId(), a.getOrganisationId(),
            a.getCampaignId(), a.getVendorId(),
            v != null ? v.getVendorName() : null,
            v != null ? v.getVendorNameAr() : null,
            a.getSubmissionToken(),
            a.getInvitedAt(), a.getFirstReminderAt(), a.getSecondReminderAt(),
            a.getEscalatedAt(), a.getOverdueAt(),
            a.getSubmittedAt(), a.getSubmittedByContactEmail(), a.getSubmittedByContactName(),
            a.getRtoHoursAttested(), a.getMtpdHoursAttested(),
            a.getContractualRtoHours(), a.getContractualMtpdHours(),
            a.getRtoBreach(), a.getMtpdBreach(),
            a.getHasTestedBcp(), a.getLastBcpTestDate(), a.getLastDrTestDate(),
            a.getSubprocessorDependencies(),
            a.getEvidenceFilePath(), a.getEvidenceFileName(),
            a.getStatus(),
            a.getReviewerCommentEn(), a.getReviewerCommentAr(),
            a.getReviewedByUserId(), a.getReviewedByName(), a.getReviewedAt(),
            a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
