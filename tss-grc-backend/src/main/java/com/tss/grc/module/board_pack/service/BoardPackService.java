package com.tss.grc.module.board_pack.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.board_pack.dto.BoardPackDto;
import com.tss.grc.module.board_pack.entity.BoardPack;
import com.tss.grc.module.board_pack.repository.BoardPackRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.common.security.CurrentActor;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Board pack service. State machine:
 *   DRAFT     → IN_REVIEW, CANCELLED
 *   IN_REVIEW → APPROVED, DRAFT (revisions), CANCELLED
 *   APPROVED  → PUBLISHED
 *   PUBLISHED, CANCELLED → terminal
 *
 * Explicit bean name avoids collision with legacy
 * {@code com.tss.grc.module.reporting.service.BoardPackService}.
 */
@Service("boardPackBoardPackService")
@RequiredArgsConstructor
public class BoardPackService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "DRAFT",     Set.of("IN_REVIEW", "CANCELLED"),
        "IN_REVIEW", Set.of("APPROVED", "DRAFT", "CANCELLED"),
        "APPROVED",  Set.of("PUBLISHED")
    );

    private final BoardPackRepository repo;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<BoardPackDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByPeriodStartDesc(orgId).stream()
            .map(BoardPackDto::from).collect(Collectors.toList());
    }

    public BoardPackDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(BoardPackDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Board pack not found: " + externalId));
    }

    @Transactional
    public BoardPackDto create(BoardPackDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        BoardPack p = new BoardPack();
        // F2-208 — swept. See ClientSuppliedReferenceRatchetTest. A board-pack reference names
        // the papers a board resolution was taken on.
        p.setExternalId(generateExternalId(req.getPeriodLabel()));
        p.setOrganisationId(orgId);
        p.setTitleEn(req.getTitleEn()); p.setTitleAr(req.getTitleAr());
        p.setPeriodLabel(req.getPeriodLabel());
        p.setPeriodStart(req.getPeriodStart()); p.setPeriodEnd(req.getPeriodEnd());
        p.setAudience(req.getAudience());
        p.setRiskSummaryJson(req.getRiskSummaryJson());
        p.setAppetiteBreachesJson(req.getAppetiteBreachesJson());
        p.setAuditEngagementsJson(req.getAuditEngagementsJson());
        p.setIncidentsJson(req.getIncidentsJson());
        p.setPdplSummaryJson(req.getPdplSummaryJson());
        p.setCcmSummaryJson(req.getCcmSummaryJson());
        p.setSlaSummaryJson(req.getSlaSummaryJson());
        p.setCustomSectionsJson(req.getCustomSectionsJson());
        // F2-05 slice 5: the drafter is the authenticated principal, never the request body.
        // F2-22b(vi) did this for reviewedBy/approvedBy on transition() below and stopped there,
        // so create() still trusted req.getDraftedBy() — proven by creating a pack that claimed
        // draftedBy="IMPERSONATED-admin". Same helper, same reasoning: a board pack drafted by
        // nobody, or by whoever the caller named, is not a governance record.
        p.setDraftedBy(actorOrFail(p.getExternalId()));
        p.setStatus("DRAFT");
        BoardPackDto out = BoardPackDto.from(repo.save(p));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "board_pack", p.getExternalId(), out);
        return out;
    }

    @Transactional
    public BoardPackDto transition(String externalId, BoardPackDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        BoardPack p = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Board pack not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(p.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        p.setStatus(req.getStatus());
        switch (req.getStatus()) {
            case "IN_REVIEW" -> p.setSubmittedForReviewAt(now);
            case "APPROVED"  -> {
                p.setReviewedAt(now); p.setApprovedAt(now);
                // F2-22b(vi): a board pack's reviewer and approver are the authenticated
                // principal. Measured before the fix: only 2 of 9 APPROVED/PUBLISHED packs
                // named an approver at all, because no UI sends these fields — so the
                // governance record that goes to the board was both forgeable and usually empty.
                String actor = actorOrFail(p.getExternalId());
                p.setReviewedBy(actor);
                p.setApprovedBy(actor);
                if (req.getReviewCommentsEn() != null) p.setReviewCommentsEn(req.getReviewCommentsEn());
            }
            case "PUBLISHED" -> {
                p.setPublishedAt(now);
                if (req.getPublicationUrl() != null) p.setPublicationUrl(req.getPublicationUrl());
            }
            case "CANCELLED" -> {
                p.setCancelledAt(now);
                if (req.getCancellationReasonEn() != null) p.setCancellationReasonEn(req.getCancellationReasonEn());
                // F2-275 - the Arabic half of the same reason, under its OWN null guard so an
                // omitted Arabic reason leaves the stored one alone rather than clearing it.
                if (req.getCancellationReasonAr() != null) p.setCancellationReasonAr(req.getCancellationReasonAr());
            }
            default -> { /* DRAFT (revision) — no extra side-effects */ }
        }
        BoardPackDto out = BoardPackDto.from(repo.save(p));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "board_pack", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        BoardPack p = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Board pack not found: " + externalId));
        p.setIsDeleted((short) 1);
        repo.save(p);
    }

    private String generateExternalId(String periodLabel) {
        String safe = periodLabel == null ? "X" : periodLabel.replaceAll("[^A-Za-z0-9]", "");
        return String.format(Locale.ROOT, "BP-%d-%s",
            LocalDateTime.now().getYear(),
            safe.length() > 6 ? safe.substring(0, 6) : safe);
    }

    /**
     * F2-22b(vi) — who reviewed and approved a board pack, from the security context.
     *
     * <p>Refuses rather than recording an anonymous approval: this is the governance artefact the
     * board itself reads, and "approved by nobody" is what 7 of the 9 approved packs already said.
     *
     * <p>F2-05 slice 5 also routes {@link #create}'s drafter through here, so the message no
     * longer says "approver" — the same helper now stamps three fields and must not describe
     * itself as one of them.
     */
    private String actorOrFail(String externalId) {
        String actor = CurrentActor.username();
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException(
                "No authenticated principal to attribute on board pack " + externalId
                + ". A governance record attributed to nobody is not a governance record.");
        }
        return actor;
    }
}
