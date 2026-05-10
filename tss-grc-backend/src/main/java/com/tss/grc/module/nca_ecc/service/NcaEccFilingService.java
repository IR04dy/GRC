package com.tss.grc.module.nca_ecc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.nca_ecc.dto.NcaEccFilingDto;
import com.tss.grc.module.nca_ecc.entity.NcaEccFiling;
import com.tss.grc.module.nca_ecc.repository.NcaEccFilingRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NCA ECC annual filing service. State machine:
 *   DRAFT          → PEER_REVIEW, CANCELLED
 *   PEER_REVIEW    → CISO_APPROVAL, DRAFT (revisions), CANCELLED
 *   CISO_APPROVAL  → SUBMITTED, PEER_REVIEW (rework), CANCELLED
 *   SUBMITTED      → ACKNOWLEDGED
 *   ACKNOWLEDGED, CANCELLED → terminal
 */
@Service
@RequiredArgsConstructor
public class NcaEccFilingService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "DRAFT",         Set.of("PEER_REVIEW", "CANCELLED"),
        "PEER_REVIEW",   Set.of("CISO_APPROVAL", "DRAFT", "CANCELLED"),
        "CISO_APPROVAL", Set.of("SUBMITTED", "PEER_REVIEW", "CANCELLED"),
        "SUBMITTED",     Set.of("ACKNOWLEDGED")
    );

    private final NcaEccFilingRepository repo;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<NcaEccFilingDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByFiscalYearDesc(orgId).stream()
            .map(NcaEccFilingDto::from).collect(Collectors.toList());
    }

    public NcaEccFilingDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(NcaEccFilingDto::from)
            .orElseThrow(() -> new EntityNotFoundException("NCA ECC filing not found: " + externalId));
    }

    public NcaEccFilingDto byYear(Integer year) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndFiscalYear(orgId, year)
            .map(NcaEccFilingDto::from)
            .orElseThrow(() -> new EntityNotFoundException("NCA ECC filing not found for year: " + year));
    }

    @Transactional
    public NcaEccFilingDto create(NcaEccFilingDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        NcaEccFiling f = new NcaEccFiling();
        f.setExternalId(req.getId() != null ? req.getId() : String.format("NCA-%d-001", req.getFiscalYear()));
        f.setOrganisationId(orgId);
        f.setFiscalYear(req.getFiscalYear());
        f.setFrameworkVersion(req.getFrameworkVersion() != null ? req.getFrameworkVersion() : "ECC-2:2024");
        f.setTitleEn(req.getTitleEn()); f.setTitleAr(req.getTitleAr());
        applyScores(f, req);
        f.setResponsesJson(req.getResponsesJson());
        f.setGapSummaryEn(req.getGapSummaryEn()); f.setGapSummaryAr(req.getGapSummaryAr());
        f.setDraftedBy(req.getDraftedBy());
        f.setStatus("DRAFT");
        NcaEccFilingDto out = NcaEccFilingDto.from(repo.save(f));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "nca_ecc_filing", f.getExternalId(), out);
        return out;
    }

    private void applyScores(NcaEccFiling f, NcaEccFilingDto req) {
        f.setDomain1Score(req.getDomain1Score()); f.setDomain2Score(req.getDomain2Score());
        f.setDomain3Score(req.getDomain3Score()); f.setDomain4Score(req.getDomain4Score());
        f.setDomain5Score(req.getDomain5Score());
        Integer[] scores = { req.getDomain1Score(), req.getDomain2Score(), req.getDomain3Score(),
                              req.getDomain4Score(), req.getDomain5Score() };
        int sum = 0, count = 0;
        for (Integer s : scores) if (s != null) { sum += s; count++; }
        if (count > 0) f.setOverallScore(BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
    }

    @Transactional
    public NcaEccFilingDto transition(String externalId, NcaEccFilingDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        NcaEccFiling f = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("NCA ECC filing not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(f.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(f.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        f.setStatus(req.getStatus());
        switch (req.getStatus()) {
            case "PEER_REVIEW" -> {
                if (f.getPeerReviewStartedAt() == null) f.setPeerReviewStartedAt(now);
                if (req.getPeerReviewer() != null) f.setPeerReviewer(req.getPeerReviewer());
                if (req.getPeerReviewCommentsEn() != null) f.setPeerReviewCommentsEn(req.getPeerReviewCommentsEn());
            }
            case "CISO_APPROVAL" -> {
                f.setPeerReviewCompletedAt(now);
                f.setCisoApprovalAt(now);
                if (req.getCisoApprover() != null) f.setCisoApprover(req.getCisoApprover());
            }
            case "SUBMITTED" -> {
                f.setSubmittedAt(now);
                if (req.getSubmissionReference() != null) f.setSubmissionReference(req.getSubmissionReference());
            }
            case "ACKNOWLEDGED" -> f.setAcknowledgedAt(now);
            case "CANCELLED" -> {
                f.setCancelledAt(now);
                if (req.getCancellationReasonEn() != null) f.setCancellationReasonEn(req.getCancellationReasonEn());
            }
            default -> { /* DRAFT (revision) — no extra side-effects */ }
        }
        if (req.getLinkedCapaIds() != null) {
            f.setLinkedCapaIds(req.getLinkedCapaIds());
            f.setCapaCount(req.getLinkedCapaIds().split(",").length);
        }
        if (req.getLinkedEvidence() != null) f.setLinkedEvidence(req.getLinkedEvidence());

        NcaEccFilingDto out = NcaEccFilingDto.from(repo.save(f));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "nca_ecc_filing", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        NcaEccFiling f = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("NCA ECC filing not found: " + externalId));
        f.setIsDeleted((short) 1);
        repo.save(f);
    }
}
