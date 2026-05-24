package com.tss.grc.module.aml.sanctions.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitEscalateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitReviewRequest;
import com.tss.grc.module.aml.sanctions.entity.ScreeningCheck;
import com.tss.grc.module.aml.sanctions.entity.ScreeningHit;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListEntryRepository;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningCheckRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningHitRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 13 — Sanctions hit triage service.
 *
 * <h3>Status machine</h3>
 *
 * <pre>
 *   PENDING_REVIEW → TRUE_POSITIVE
 *                  | FALSE_POSITIVE
 *                  | ESCALATED
 *
 *   ESCALATED      → TRUE_POSITIVE
 *                  | FALSE_POSITIVE
 *                  | BLOCK_RECOMMENDED
 *                  | RELEASE_RECOMMENDED
 *
 *   TRUE_POSITIVE       → CLOSED | BLOCK_RECOMMENDED
 *   FALSE_POSITIVE      → CLOSED | RELEASE_RECOMMENDED
 *   BLOCK_RECOMMENDED   → CLOSED
 *   RELEASE_RECOMMENDED → CLOSED
 *   CLOSED              → (terminal)
 * </pre>
 *
 * <h3>SLA breach detection</h3>
 *
 * <p>A hit in {@code PENDING_REVIEW} more than 24 hours after
 * {@code created_at} is overdue. {@link #detectSlaBreachesForOrg} runs
 * cross-tenant from the scheduler — it flips {@code sla_breach=1} on
 * every overdue hit and fires {@code SANCTIONS_HIT_SLA_BREACHED}. Once
 * tripped, the flag stays set even if the reviewer closes the hit
 * afterwards (so the audit committee can see the operational miss).
 *
 * <h3>Escalation</h3>
 *
 * <p>{@link #escalate} sets {@code status = ESCALATED}, stamps
 * {@code escalated_to_user_id} and {@code escalated_at}, and fires
 * {@code SANCTIONS_HIT_ESCALATED}. A subsequent {@link #reviewHit}
 * call from the senior reviewer moves the hit to TRUE_POSITIVE /
 * FALSE_POSITIVE / BLOCK_RECOMMENDED / RELEASE_RECOMMENDED.
 *
 * <h3>F8 events emitted</h3>
 *
 * <p>{@code SANCTIONS_HIT_REVIEWED}, {@code SANCTIONS_HIT_ESCALATED},
 * {@code SANCTIONS_HIT_SLA_BREACHED}, {@code SANCTIONS_HIT_CLOSED}.
 */
@Service
@RequiredArgsConstructor
public class SanctionsHitService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsHitService.class);

    /** SLA: a hit in PENDING_REVIEW for more than 24 h is overdue. */
    public static final Duration SLA_REVIEW_WINDOW = Duration.ofHours(24);

    /** Forward-only status transitions for hits. */
    static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "PENDING_REVIEW",     Set.of("TRUE_POSITIVE", "FALSE_POSITIVE", "ESCALATED", "CLOSED"),
        "ESCALATED",          Set.of("TRUE_POSITIVE", "FALSE_POSITIVE",
                                     "BLOCK_RECOMMENDED", "RELEASE_RECOMMENDED", "CLOSED"),
        "TRUE_POSITIVE",      Set.of("BLOCK_RECOMMENDED", "CLOSED"),
        "FALSE_POSITIVE",     Set.of("RELEASE_RECOMMENDED", "CLOSED"),
        "BLOCK_RECOMMENDED",  Set.of("CLOSED"),
        "RELEASE_RECOMMENDED",Set.of("CLOSED"),
        "CLOSED",             Set.of()
    );

    /** Final statuses that propagate up to the parent check. */
    private static final Set<String> CONFIRMED_HIT_STATUSES = Set.of(
        "TRUE_POSITIVE", "BLOCK_RECOMMENDED"
    );

    private final ScreeningHitRepository hitRepo;
    private final ScreeningCheckRepository checkRepo;
    private final SanctionsListEntryRepository entryRepo;
    private final SanctionsListRepository listRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    /** Clock used for SLA-breach math. Default is system UTC; tests can override via setter. */
    private Clock clock = Clock.system(ZoneId.of("UTC"));

    /** Test-only seam — swap in a fixed clock to drive SLA-breach assertions. */
    public void setClock(Clock clock) { this.clock = clock; }

    private Long org() { return TenantContext.requireOrganisationId(); }

    // ─── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HitResponse> listByStatus(String status) {
        Long orgId = org();
        return hitRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(orgId, status)
            .stream().map(this::toResponse).toList();
    }

    // ─── Writes ───────────────────────────────────────────────────────

    @Transactional
    public HitResponse reviewHit(Long hitId, HitReviewRequest req) {
        ScreeningHit h = loadOrThrow(hitId);
        String from = h.getStatus();
        String to = req.newStatus();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
        h.setStatus(to);
        h.setReviewerUserId(req.reviewerUserId());
        h.setReviewedAt(LocalDateTime.now(clock));
        if (req.dispositionEn() != null) h.setDispositionEn(req.dispositionEn());
        if (req.dispositionAr() != null) h.setDispositionAr(req.dispositionAr());
        if (req.capaTaskId() != null) h.setCapaTaskId(req.capaTaskId());
        h.setUpdatedAt(LocalDateTime.now(clock));
        ScreeningHit saved = hitRepo.save(h);

        audit.log("REVIEW", "ScreeningHit", saved.getId(),
            "Hit " + saved.getId() + " " + from + " -> " + to
                + (req.reviewerUserId() != null ? " by user=" + req.reviewerUserId() : ""));

        // Promote the parent check's result when we confirm a true positive
        if (CONFIRMED_HIT_STATUSES.contains(to)) {
            checkRepo.findByOrganisationIdAndIdAndIsDeletedFalse(saved.getOrganisationId(), saved.getCheckId())
                .ifPresent(c -> {
                    if (!"CONFIRMED_HIT".equals(c.getResult())) {
                        c.setResult("CONFIRMED_HIT");
                        c.setUpdatedAt(LocalDateTime.now(clock));
                        checkRepo.save(c);
                    }
                });
        }

        // F8 events
        emitReviewed(saved, from, to);
        if ("CLOSED".equals(to)) {
            emitClosed(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public HitResponse escalate(Long hitId, HitEscalateRequest req) {
        ScreeningHit h = loadOrThrow(hitId);
        String from = h.getStatus();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains("ESCALATED")) {
            throw new IllegalTransitionException(from, "ESCALATED", allowed);
        }
        h.setStatus("ESCALATED");
        h.setEscalatedToUserId(req.escalatedToUserId());
        h.setEscalatedAt(LocalDateTime.now(clock));
        if (req.reasonEn() != null) h.setDispositionEn(req.reasonEn());
        if (req.reasonAr() != null) h.setDispositionAr(req.reasonAr());
        h.setUpdatedAt(LocalDateTime.now(clock));
        ScreeningHit saved = hitRepo.save(h);

        audit.log("ESCALATE", "ScreeningHit", saved.getId(),
            "Hit " + saved.getId() + " escalated to user=" + req.escalatedToUserId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("hitId", String.valueOf(saved.getId()));
        payload.put("checkId", String.valueOf(saved.getCheckId()));
        payload.put("fromStatus", from);
        payload.put("escalatedToUserId", String.valueOf(saved.getEscalatedToUserId()));
        payload.put("matchScore", saved.getMatchScore().toPlainString());
        publisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "SANCTIONS_HIT_ESCALATED",
            "ScreeningHit", saved.getId(),
            "sanctions-hit:" + saved.getId(), payload));
        return toResponse(saved);
    }

    /**
     * SLA-breach sweeper for a single tenant — flips
     * {@code sla_breach=1} on every {@code PENDING_REVIEW} hit older
     * than 24h. Returns the count of hits newly marked breached.
     */
    @Transactional
    public int detectSlaBreachesForOrg(Long orgId) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(SLA_REVIEW_WINDOW);
        List<ScreeningHit> overdue = hitRepo
            .findByOrganisationIdAndStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
                orgId, "PENDING_REVIEW", cutoff);
        int breached = 0;
        for (ScreeningHit h : overdue) {
            h.setSlaBreach(true);
            h.setUpdatedAt(LocalDateTime.now(clock));
            hitRepo.save(h);
            breached++;

            Map<String, Object> payload = new HashMap<>();
            payload.put("hitId", String.valueOf(h.getId()));
            payload.put("checkId", String.valueOf(h.getCheckId()));
            payload.put("ageHours", String.valueOf(Duration.between(h.getCreatedAt(), LocalDateTime.now(clock)).toHours()));
            publisher.publishEvent(new DomainEvent(
                h.getOrganisationId(), "SANCTIONS_HIT_SLA_BREACHED",
                "ScreeningHit", h.getId(),
                "sanctions-hit:" + h.getId(), payload));
        }
        if (breached > 0) {
            audit.log("SLA_BREACH", "ScreeningHit", null,
                "SLA breach swept: " + breached + " hit(s) marked breached for org=" + orgId);
        }
        return breached;
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private ScreeningHit loadOrThrow(Long id) {
        return hitRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Screening hit not found: " + id));
    }

    HitResponse toResponse(ScreeningHit h) {
        // Materialise the matched entry's name + list code for the UI
        String listCode = null, nameEn = null, nameAr = null;
        var entryOpt = entryRepo.findById(h.getListEntryId());
        if (entryOpt.isPresent()) {
            var e = entryOpt.get();
            nameEn = e.getPrimaryNameEn();
            nameAr = e.getPrimaryNameAr();
            listCode = listRepo.findById(e.getListId()).map(l -> l.getListCode()).orElse(null);
        }
        return new HitResponse(
            h.getId(), h.getExternalId(), h.getCheckId(), h.getListEntryId(),
            listCode, nameEn, nameAr,
            h.getMatchScore(), h.getMatchType(), h.getStatus(),
            h.getReviewerUserId(), h.getReviewedAt(),
            h.getDispositionEn(), h.getDispositionAr(),
            h.getSlaBreach(),
            h.getEscalatedToUserId(), h.getEscalatedAt(),
            h.getCapaTaskId(), h.getCreatedAt()
        );
    }

    void emitReviewed(ScreeningHit h, String fromStatus, String toStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hitId", String.valueOf(h.getId()));
        payload.put("checkId", String.valueOf(h.getCheckId()));
        payload.put("fromStatus", fromStatus);
        payload.put("toStatus", toStatus);
        payload.put("reviewerUserId", String.valueOf(h.getReviewerUserId()));
        payload.put("capaTaskId", String.valueOf(h.getCapaTaskId()));
        publisher.publishEvent(new DomainEvent(
            h.getOrganisationId(), "SANCTIONS_HIT_REVIEWED",
            "ScreeningHit", h.getId(),
            "sanctions-hit:" + h.getId(), payload));
    }

    void emitClosed(ScreeningHit h) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hitId", String.valueOf(h.getId()));
        payload.put("checkId", String.valueOf(h.getCheckId()));
        publisher.publishEvent(new DomainEvent(
            h.getOrganisationId(), "SANCTIONS_HIT_CLOSED",
            "ScreeningHit", h.getId(),
            "sanctions-hit:" + h.getId(), payload));
    }
}
