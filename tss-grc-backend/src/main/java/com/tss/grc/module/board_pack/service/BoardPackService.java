package com.tss.grc.module.board_pack.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.board_pack.dto.BoardPackDto;
import com.tss.grc.module.board_pack.entity.BoardPack;
import com.tss.grc.module.board_pack.repository.BoardPackRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
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
        p.setExternalId(req.getId() != null ? req.getId() : generateExternalId(req.getPeriodLabel()));
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
        p.setDraftedBy(req.getDraftedBy());
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
                if (req.getReviewedBy() != null) p.setReviewedBy(req.getReviewedBy());
                if (req.getApprovedBy() != null) p.setApprovedBy(req.getApprovedBy());
                if (req.getReviewCommentsEn() != null) p.setReviewCommentsEn(req.getReviewCommentsEn());
            }
            case "PUBLISHED" -> {
                p.setPublishedAt(now);
                if (req.getPublicationUrl() != null) p.setPublicationUrl(req.getPublicationUrl());
            }
            case "CANCELLED" -> {
                p.setCancelledAt(now);
                if (req.getCancellationReasonEn() != null) p.setCancellationReasonEn(req.getCancellationReasonEn());
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
        return String.format("BP-%d-%s",
            LocalDateTime.now().getYear(),
            safe.length() > 6 ? safe.substring(0, 6) : safe);
    }
}
