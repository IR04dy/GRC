package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationRecordRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationReviewRequest;
import com.tss.grc.module.compliance.obligation.entity.ObligationAttestation;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import com.tss.grc.module.compliance.obligation.repository.ObligationRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
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

/**
 * Sprint 11 — Per-cycle obligation attestation service.
 *
 * <p>Handles the officer-side submission ("I attest that the May 2026
 * SAMA monthly CAR return was filed on day 12 of June, evidence
 * attached") and the reviewer-side outcome ("Audit Committee
 * accepted").
 *
 * <h3>Per-cycle state machine</h3>
 * <pre>
 *   DUE                       → IN_PROGRESS | ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE | OVERDUE
 *   OVERDUE                   → IN_PROGRESS | ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE
 *   IN_PROGRESS               → ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE | OVERDUE
 *   ATTESTED                  (reviewer can ACCEPT / REJECT / REQUEST_CHANGES)
 *   ATTESTED_WITH_EXCEPTIONS  (reviewer can ACCEPT / REJECT / REQUEST_CHANGES)
 *   NOT_APPLICABLE            (terminal)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ObligationAttestationService {

    private static final Logger log = LoggerFactory.getLogger(ObligationAttestationService.class);

    public static final Set<String> ATTEST_STATES = Set.of(
        "DUE", "OVERDUE", "IN_PROGRESS", "ATTESTED", "ATTESTED_WITH_EXCEPTIONS", "NOT_APPLICABLE");

    public static final Set<String> REVIEWER_DECISIONS = Set.of(
        "ACCEPTED", "REJECTED", "REQUEST_CHANGES");

    private final ObligationAttestationRepository repo;
    private final ObligationRepository obligationRepo;
    private final ObligationService obligationService;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttestationResponse> list() {
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByDueDateAsc(org())
            .stream().map(ObligationAttestationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AttestationResponse> listForObligation(Long obligationId) {
        return repo.findByOrganisationIdAndObligationIdAndIsDeletedFalseOrderByDueDateDesc(org(), obligationId)
            .stream().map(ObligationAttestationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AttestationResponse> listForCampaign(Long campaignId) {
        return repo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDueDateAsc(org(), campaignId)
            .stream().map(ObligationAttestationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AttestationResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    // ─── Writes ─────────────────────────────────────────────────────

    /**
     * Record an attestation. Two paths:
     *
     *   - If a campaign materialised the row already (DUE / OVERDUE /
     *     IN_PROGRESS), this finds it by obligation_id + period_label +
     *     campaign_id and updates it.
     *   - Otherwise creates a new ad-hoc attestation row (campaign_id
     *     null — owner-initiated, ON_EVENT obligation, retro entry).
     */
    @Transactional
    public AttestationResponse recordAttestation(AttestationRecordRequest req) {
        Long orgId = org();
        if (!obligationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.obligationId()).isPresent()) {
            throw new EntityNotFoundException("Obligation not found: " + req.obligationId());
        }

        // Try to find an existing materialised row to update; otherwise create.
        ObligationAttestation a = findExistingMaterialised(orgId, req)
            .orElseGet(() -> {
                ObligationAttestation fresh = new ObligationAttestation();
                fresh.setOrganisationId(orgId);
                fresh.setObligationId(req.obligationId());
                fresh.setCampaignId(req.campaignId());
                fresh.setPeriodLabel(req.periodLabel());
                fresh.setPeriodStart(req.periodStart());
                fresh.setPeriodEnd(req.periodEnd());
                fresh.setDueDate(req.dueDate());
                fresh.setAttestationStatus("DUE");
                return fresh;
            });

        String from = a.getAttestationStatus();
        guardAttest(from, req.attestationStatus());

        LocalDateTime now = LocalDateTime.now();
        a.setAttestationStatus(req.attestationStatus());
        a.setAttestedByUserId(req.attestedByUserId());
        a.setAttestedAt(now);
        a.setAttestationTextEn(req.attestationTextEn());
        a.setAttestationTextAr(req.attestationTextAr());
        a.setEvidenceRefs(req.evidenceRefs());
        // Preserve exception text only when the status is the with-exceptions variant
        // so a later edit that flips back to ATTESTED clears it cleanly. (Caller can
        // still pass NULL to clear when they intentionally want it dropped.)
        if ("ATTESTED_WITH_EXCEPTIONS".equals(req.attestationStatus())) {
            a.setExceptionsNotedEn(req.exceptionsNotedEn());
            a.setExceptionsNotedAr(req.exceptionsNotedAr());
        } else if ("ATTESTED".equals(req.attestationStatus())) {
            a.setExceptionsNotedEn(null);
            a.setExceptionsNotedAr(null);
        }
        a.setUpdatedAt(now);

        ObligationAttestation saved = repo.save(a);

        // Roll the parent obligation's next_due_date forward when the
        // attestation is genuine (ATTESTED / ATTESTED_WITH_EXCEPTIONS).
        if ("ATTESTED".equals(saved.getAttestationStatus())
                || "ATTESTED_WITH_EXCEPTIONS".equals(saved.getAttestationStatus())) {
            obligationService.rollForwardAfterAttestation(
                saved.getObligationId(), req.attestedByUserId(), now);
        }

        audit.log("ATTEST", "ObligationAttestation", saved.getId(),
            "Attestation recorded: obligation " + saved.getObligationId()
                + ", period " + (saved.getPeriodLabel() != null ? saved.getPeriodLabel() : "(ad-hoc)")
                + " — " + saved.getAttestationStatus());
        emit("OBLIGATION_ATTESTED", saved, from);
        return toResponse(saved);
    }

    @Transactional
    public AttestationResponse reviewAttestation(Long id, AttestationReviewRequest req) {
        if (!REVIEWER_DECISIONS.contains(req.reviewerDecision())) {
            throw new IllegalArgumentException("Invalid reviewerDecision: " + req.reviewerDecision());
        }
        ObligationAttestation a = loadOrThrow(id);
        if (!isReviewable(a.getAttestationStatus())) {
            throw new IllegalTransitionException(a.getAttestationStatus(), "REVIEWED",
                Set.of("ATTESTED", "ATTESTED_WITH_EXCEPTIONS"));
        }

        LocalDateTime now = LocalDateTime.now();
        a.setReviewerDecision(req.reviewerDecision());
        a.setReviewerUserId(req.reviewerUserId());
        a.setReviewedAt(now);
        a.setReviewerCommentEn(req.reviewerCommentEn());
        a.setReviewerCommentAr(req.reviewerCommentAr());
        a.setUpdatedAt(now);

        // REQUEST_CHANGES moves the row back to IN_PROGRESS so the
        // owner can re-submit; ACCEPTED / REJECTED leave the status
        // alone (the lifecycle ended with the original attestation
        // event, only reviewer_decision is added).
        if ("REQUEST_CHANGES".equals(req.reviewerDecision())) {
            a.setAttestationStatus("IN_PROGRESS");
        }

        ObligationAttestation saved = repo.save(a);
        audit.log("REVIEW", "ObligationAttestation", saved.getId(),
            "Attestation reviewed: obligation " + saved.getObligationId()
                + ", decision " + saved.getReviewerDecision()
                + (req.reviewerCommentEn() != null ? " — " + req.reviewerCommentEn() : ""));
        emit("OBLIGATION_ATTESTATION_REVIEWED", saved, null);
        return toResponse(saved);
    }

    /**
     * Bulk-flip due rows past their due_date to OVERDUE. Called by the
     * campaign service's escalation step and by the periodic sweep.
     */
    @Transactional
    public int markOverdueForCampaign(Long campaignId, java.time.LocalDate cutoff) {
        List<ObligationAttestation> due = repo
            .findByOrganisationIdAndCampaignIdAndAttestationStatusInAndDueDateBeforeAndIsDeletedFalse(
                org(), campaignId, List.of("DUE", "IN_PROGRESS"), cutoff);
        LocalDateTime now = LocalDateTime.now();
        for (ObligationAttestation a : due) {
            a.setAttestationStatus("OVERDUE");
            a.setUpdatedAt(now);
            repo.save(a);
            emit("OBLIGATION_OVERDUE", a, "DUE");
        }
        return due.size();
    }

    // ─── Guards ─────────────────────────────────────────────────────

    static void guardAttest(String from, String to) {
        if (!ATTEST_STATES.contains(to)) {
            throw new IllegalArgumentException("Unknown attestation status: " + to);
        }
        Set<String> allowed = switch (Objects.requireNonNullElse(from, "")) {
            case "DUE", "OVERDUE", "IN_PROGRESS" ->
                Set.of("IN_PROGRESS", "ATTESTED", "ATTESTED_WITH_EXCEPTIONS", "NOT_APPLICABLE");
            // Re-attesting an already-attested cycle is allowed (e.g.
            // reviewer requested changes); we accept the same set so the
            // owner can flip ATTESTED → ATTESTED_WITH_EXCEPTIONS or vice
            // versa during the review window.
            case "ATTESTED", "ATTESTED_WITH_EXCEPTIONS" ->
                Set.of("IN_PROGRESS", "ATTESTED", "ATTESTED_WITH_EXCEPTIONS", "NOT_APPLICABLE");
            default -> Set.of();
        };
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    static boolean isReviewable(String status) {
        return "ATTESTED".equals(status) || "ATTESTED_WITH_EXCEPTIONS".equals(status);
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private ObligationAttestation loadOrThrow(Long id) {
        return repo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Attestation not found: " + id));
    }

    private java.util.Optional<ObligationAttestation> findExistingMaterialised(Long orgId, AttestationRecordRequest req) {
        if (req.campaignId() == null || req.periodLabel() == null) return java.util.Optional.empty();
        // Reuse the campaign-scoped listing and pick the one matching the period_label.
        return repo
            .findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDueDateAsc(orgId, req.campaignId())
            .stream()
            .filter(x -> Objects.equals(x.getObligationId(), req.obligationId())
                && Objects.equals(x.getPeriodLabel(), req.periodLabel()))
            .findFirst();
    }

    private void emit(String trigger, ObligationAttestation a, String previousStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("attestationId", String.valueOf(a.getId()));
        payload.put("obligationId", String.valueOf(a.getObligationId()));
        payload.put("campaignId", a.getCampaignId() == null ? "" : String.valueOf(a.getCampaignId()));
        payload.put("periodLabel", a.getPeriodLabel() == null ? "" : a.getPeriodLabel());
        payload.put("status", a.getAttestationStatus());
        payload.put("previousStatus", previousStatus == null ? "" : previousStatus);
        payload.put("reviewerDecision", a.getReviewerDecision() == null ? "" : a.getReviewerDecision());
        publisher.publishEvent(new DomainEvent(
            a.getOrganisationId(), trigger, "ObligationAttestation", a.getId(),
            "obligation-attestation:" + a.getId(), payload));
    }

    static AttestationResponse toResponse(ObligationAttestation a) {
        return new AttestationResponse(
            a.getId(), a.getExternalId(), a.getOrganisationId(),
            a.getObligationId(), a.getCampaignId(),
            a.getPeriodLabel(), a.getPeriodStart(), a.getPeriodEnd(), a.getDueDate(),
            a.getAttestationStatus(),
            a.getAttestedByUserId(), a.getAttestedAt(),
            a.getAttestationTextEn(), a.getAttestationTextAr(),
            a.getEvidenceRefs(),
            a.getExceptionsNotedEn(), a.getExceptionsNotedAr(),
            a.getReviewerUserId(), a.getReviewedAt(),
            a.getReviewerDecision(),
            a.getReviewerCommentEn(), a.getReviewerCommentAr(),
            a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
