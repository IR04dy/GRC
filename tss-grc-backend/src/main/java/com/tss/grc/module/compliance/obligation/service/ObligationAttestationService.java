package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
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
    /** F2-385 — recompute the parent campaign's status rollups after a child attestation changes
     *  (record / review), so stats()/CampaignResponse do not report frozen counts (0% completion)
     *  mid-campaign. Shared component to avoid a service→service cycle (see ObligationCampaignRollups). */
    private final ObligationCampaignRollups rollups;

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
        // F2-224 — the attester is the authenticated principal, never a body field. An
        // obligation attestation is the artefact recording that a NAMED person asserted the
        // organisation met a regulatory obligation for a period; the server stamping attestedAt
        // from its own clock below is it declaring that it is the recorder of that act.
        //
        // This REFUSES rather than storing null, and that is not a new obligation on callers:
        // AttestationRecordRequest already carried @NotNull @Positive on the field, so the
        // server demanded an attribution it never verified (F2-223's annual-plan shape). The
        // frontend was sending the literal 1 with the comment "wired via JWT claim in v2", so
        // every attestation on the tenant was filed against user 1 whoever was signed in.
        Long attester = CurrentActor.userId();
        if (attester == null) {
            throw new IllegalStateException(
                "No authenticated principal to record as the attester of obligation "
                + req.obligationId() + ". An attestation with no attester is not an attestation.");
        }
        a.setAttestationStatus(req.attestationStatus());
        a.setAttestedByUserId(attester);
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

        // F2-385 — recompute the parent campaign's status rollups now the child status changed.
        // No-op for an ad-hoc attestation (null campaignId). Same transaction, so the count sees
        // the row just saved above; stats() reads these stored columns, so without this the
        // campaign reported 0% completion until it was closed.
        rollups.recompute(orgId, saved.getCampaignId());

        // Roll the parent obligation's next_due_date forward when the
        // attestation is genuine (ATTESTED / ATTESTED_WITH_EXCEPTIONS).
        if ("ATTESTED".equals(saved.getAttestationStatus())
                || "ATTESTED_WITH_EXCEPTIONS".equals(saved.getAttestationStatus())) {
            // F2-224 — read the attester off the SAVED ROW, never off the request: the roll
            // forward records who moved the obligation's next due date, and if that disagreed
            // with the row it would be a second, unverified attribution of the same act.
            obligationService.rollForwardAfterAttestation(
                saved.getObligationId(), saved.getAttestedByUserId(), now);
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
        // F2-224 — the reviewer is the authenticated principal. Same reasoning as
        // recordAttestation above, and the same pre-existing @NotNull on the request field, so
        // refusing cannot break a caller who is supplying one today. Note the two routes share
        // roles (COMPLIANCE_OFFICER, CISO, PLATFORM_ADMIN), so an attester can still review
        // their own attestation — but until both ends were stamped from the principal the
        // platform could not even SEE that, because both were whatever the caller typed.
        Long reviewer = CurrentActor.userId();
        if (reviewer == null) {
            throw new IllegalStateException(
                "No authenticated principal to record as the reviewer of attestation " + id
                + ". A review with no reviewer is not a review.");
        }
        a.setReviewerDecision(req.reviewerDecision());
        a.setReviewerUserId(reviewer);
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
        // F2-385 — recompute the parent campaign's rollups: REQUEST_CHANGES moved this row back to
        // IN_PROGRESS, which changes the campaign's attested/in-progress counts (no-op if ad-hoc).
        rollups.recompute(org(), saved.getCampaignId());
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
        return flipToOverdue(due);
    }

    /**
     * Org-wide overdue sweep — flips every DUE / IN_PROGRESS cycle past the
     * cutoff to OVERDUE and emits OBLIGATION_OVERDUE. Called daily by the
     * compliance-deadline scheduler (the campaign-scoped variant only covers
     * campaign-materialised rows; this also catches ad-hoc / no-campaign cycles).
     */
    @Transactional
    public int sweepOverdueForOrg(java.time.LocalDate cutoff) {
        List<ObligationAttestation> due = repo
            .findByOrganisationIdAndAttestationStatusInAndDueDateBeforeAndIsDeletedFalse(
                org(), List.of("DUE", "IN_PROGRESS"), cutoff);
        return flipToOverdue(due);
    }

    /**
     * The single OVERDUE writer for this entity — F2-191.
     *
     * <p>Both sweeps had their own copy of this loop, and neither consulted {@link #guardAttest}. Two
     * consequences: the guard's own table did not permit OVERDUE at all (so the lifecycle a reader
     * consults said this could not happen, while it happened daily), and {@code emit} was passed the
     * literal {@code "DUE"} as the previous status for rows that may have been {@code IN_PROGRESS} —
     * so the F8 event told a compliance owner the cycle had never been started when it had.
     *
     * <p>A row the table forbids is <b>skipped and logged</b>, not thrown on: a batch that aborts
     * half-way through a daily sweep leaves an arbitrary subset escalated, which is worse than one
     * unescalated row and much harder to notice.
     */
    private int flipToOverdue(List<ObligationAttestation> candidates) {
        LocalDateTime now = LocalDateTime.now();
        int flipped = 0;
        for (ObligationAttestation a : candidates) {
            String from = a.getAttestationStatus();
            if (!mayAttest(from, "OVERDUE")) {
                log.warn("Obligation attestation {} is past due in status {}, which the lifecycle does "
                        + "not allow to become OVERDUE — not escalated", a.getId(), from);
                continue;
            }
            a.setAttestationStatus("OVERDUE");
            a.setUpdatedAt(now);
            repo.save(a);
            emit("OBLIGATION_OVERDUE", a, from);
            flipped++;
        }
        return flipped;
    }

    // ─── Guards ─────────────────────────────────────────────────────

    /**
     * The per-cycle lifecycle, as one table read by every writer.
     *
     * <p>F2-191 — this was a {@code switch} whose arms lumped {@code DUE}, {@code OVERDUE} and
     * {@code IN_PROGRESS} together and allowed none of them to become <b>OVERDUE</b>, while the class
     * javadoc above says {@code DUE → … | OVERDUE} and {@code IN_PROGRESS → … | OVERDUE}, and
     * <b>three</b> writers wrote OVERDUE without ever calling the guard
     * ({@link #markOverdueForCampaign}, {@link #sweepOverdueForOrg} and
     * {@code ObligationCampaignService}'s escalation step). Three artefacts, three answers.
     *
     * <p>The javadoc was right — an attestation past its due date is overdue — so the table now says
     * exactly what the prose says, and {@link #mayAttest} is the one predicate all four callers use.
     * {@code OVERDUE → OVERDUE} stays absent deliberately: re-flipping a row that is already overdue
     * is not a state change, and the sweeps skip it rather than declaring a self-loop.
     */
    static final Map<String, Set<String>> ATTEST_TRANSITIONS = Map.of(
        "DUE",         Set.of("IN_PROGRESS", "ATTESTED", "ATTESTED_WITH_EXCEPTIONS", "NOT_APPLICABLE", "OVERDUE"),
        "IN_PROGRESS", Set.of("ATTESTED", "ATTESTED_WITH_EXCEPTIONS", "NOT_APPLICABLE", "OVERDUE"),
        "OVERDUE",     Set.of("IN_PROGRESS", "ATTESTED", "ATTESTED_WITH_EXCEPTIONS", "NOT_APPLICABLE"),
        // Re-attesting an already-attested cycle is allowed (e.g. reviewer requested changes), so the
        // owner can flip ATTESTED → ATTESTED_WITH_EXCEPTIONS or back during the review window.
        "ATTESTED",    Set.of("IN_PROGRESS", "ATTESTED", "ATTESTED_WITH_EXCEPTIONS", "NOT_APPLICABLE"),
        "ATTESTED_WITH_EXCEPTIONS",
                       Set.of("IN_PROGRESS", "ATTESTED", "ATTESTED_WITH_EXCEPTIONS", "NOT_APPLICABLE"),
        // Terminal by design.
        //
        // ⚠ A first draft of this comment claimed the campaign escalation could flip a NOT_APPLICABLE
        // cycle to OVERDUE. It cannot: escalateOverdue's candidate query filters to DUE/IN_PROGRESS.
        // The claim came from reading a status filter in a DIFFERENT method of that class. Left here
        // as a correction rather than deleted, because F2-188's lesson is that fluent prose in an
        // authoritative-looking place reads as verification — including when it is mine and wrong.
        "NOT_APPLICABLE", Set.of());

    /** Legality without the throw, so a sweep can skip a row instead of aborting the batch. */
    static boolean mayAttest(String from, String to) {
        return ATTEST_TRANSITIONS.getOrDefault(Objects.requireNonNullElse(from, ""), Set.of())
            .contains(to);
    }

    static void guardAttest(String from, String to) {
        if (!ATTEST_STATES.contains(to)) {
            throw new IllegalArgumentException("Unknown attestation status: " + to);
        }
        if (!mayAttest(from, to)) {
            throw new IllegalTransitionException(from, to,
                ATTEST_TRANSITIONS.getOrDefault(Objects.requireNonNullElse(from, ""), Set.of()));
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
