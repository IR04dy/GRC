package com.tss.grc.module.incident.rca.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.incident.rca.dto.RcaDtos.CreateRequest;
import com.tss.grc.module.incident.rca.dto.RcaDtos.RejectRequest;
import com.tss.grc.module.incident.rca.dto.RcaDtos.UpdateRequest;
import com.tss.grc.module.incident.rca.entity.RootCauseAnalysis;
import com.tss.grc.module.incident.rca.repository.RootCauseAnalysisRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * RCA lifecycle service.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT     ──submit()──▶ IN_REVIEW
 *   IN_REVIEW ──approve()─▶ APPROVED  (terminal)
 *             ──reject()──▶ REJECTED  (terminal)
 * </pre>
 *
 * <p>DRAFT permits free updates via {@link #update}; IN_REVIEW and
 * terminal states do not. Approval stamps the approver identity for
 * the audit trail; rejection stamps the reason.
 */
@Service
@RequiredArgsConstructor
public class RootCauseAnalysisService {

    private final RootCauseAnalysisRepository repo;

    @Autowired(required = false)
    private com.tss.grc.platform.realtime.BroadcastHelper broadcaster;

    // ─── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RootCauseAnalysis> list(Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public RootCauseAnalysis get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new EntityNotFoundException("RCA " + id));
    }

    @Transactional(readOnly = true)
    public java.util.List<RootCauseAnalysis> forEntity(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
                orgId, entityType, entityId);
    }

    // ─── Writes ───────────────────────────────────────────────────────

    @Transactional
    public RootCauseAnalysis create(CreateRequest body, Long createdBy) {
        Long orgId = TenantContext.getCurrentTenant();
        RootCauseAnalysis r = new RootCauseAnalysis();
        r.setOrganisationId(orgId);
        r.setExternalId("RCA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        r.setRelatedEntityType(body.relatedEntityType());
        r.setRelatedEntityId(body.relatedEntityId());
        r.setRelatedEntityLabel(body.relatedEntityLabel());
        r.setMethodology(body.methodology() == null ? "FIVE_WHYS" : body.methodology());
        r.setProblemStatementEn(body.problemStatementEn());
        r.setProblemStatementAr(body.problemStatementAr());
        r.setFiveWhysJson(body.fiveWhysJson());
        r.setContributingFactorsJson(body.contributingFactorsJson());
        r.setRootCauseEn(body.rootCauseEn());
        r.setRootCauseAr(body.rootCauseAr());
        r.setRecommendationsEn(body.recommendationsEn());
        r.setRecommendationsAr(body.recommendationsAr());
        r.setFacilitator(body.facilitator());
        r.setParticipants(body.participants());
        r.setConductedAt(body.conductedAt());
        r.setCapaRefs(body.capaRefs());
        r.setCreatedBy(createdBy);
        r.setStatus("DRAFT");

        RootCauseAnalysis saved = repo.save(r);
        broadcast("created", saved);
        return saved;
    }

    @Transactional
    public RootCauseAnalysis update(Long id, UpdateRequest body) {
        RootCauseAnalysis r = get(id);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), "DRAFT(edit)", Set.of("DRAFT"));
        }
        if (body.methodology() != null) r.setMethodology(body.methodology());
        if (body.problemStatementEn() != null) r.setProblemStatementEn(body.problemStatementEn());
        if (body.problemStatementAr() != null) r.setProblemStatementAr(body.problemStatementAr());
        if (body.fiveWhysJson() != null) r.setFiveWhysJson(body.fiveWhysJson());
        if (body.contributingFactorsJson() != null) r.setContributingFactorsJson(body.contributingFactorsJson());
        if (body.rootCauseEn() != null) r.setRootCauseEn(body.rootCauseEn());
        if (body.rootCauseAr() != null) r.setRootCauseAr(body.rootCauseAr());
        if (body.recommendationsEn() != null) r.setRecommendationsEn(body.recommendationsEn());
        if (body.recommendationsAr() != null) r.setRecommendationsAr(body.recommendationsAr());
        if (body.facilitator() != null) r.setFacilitator(body.facilitator());
        if (body.participants() != null) r.setParticipants(body.participants());
        if (body.conductedAt() != null) r.setConductedAt(body.conductedAt());
        if (body.capaRefs() != null) r.setCapaRefs(body.capaRefs());

        RootCauseAnalysis saved = repo.save(r);
        broadcast("updated", saved);
        return saved;
    }

    @Transactional
    public RootCauseAnalysis submit(Long id, Long userId) {
        RootCauseAnalysis r = get(id);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), "IN_REVIEW", Set.of("DRAFT"));
        }
        r.setStatus("IN_REVIEW");
        r.setSubmittedAt(LocalDateTime.now());
        r.setSubmittedBy(userId);
        RootCauseAnalysis saved = repo.save(r);
        broadcast("submitted", saved);
        return saved;
    }

    @Transactional
    public RootCauseAnalysis approve(Long id, Long userId, String approverName) {
        RootCauseAnalysis r = get(id);
        if (!"IN_REVIEW".equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), "APPROVED", Set.of("IN_REVIEW"));
        }
        r.setStatus("APPROVED");
        r.setApprovedAt(LocalDateTime.now());
        r.setApprovedBy(userId);
        r.setApproverName(approverName);
        RootCauseAnalysis saved = repo.save(r);
        broadcast("approved", saved);
        return saved;
    }

    @Transactional
    public RootCauseAnalysis reject(Long id, RejectRequest body) {
        RootCauseAnalysis r = get(id);
        if (!"IN_REVIEW".equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), "REJECTED", Set.of("IN_REVIEW"));
        }
        r.setStatus("REJECTED");
        r.setRejectionReason(body.rejectionReason());
        RootCauseAnalysis saved = repo.save(r);
        broadcast("rejected", saved);
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        RootCauseAnalysis r = get(id);
        r.setIsDeleted((short) 1);
        r.setDeletedAt(LocalDateTime.now());
        repo.save(r);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private void broadcast(String op, RootCauseAnalysis r) {
        if (broadcaster == null) return;
        broadcaster.entity(
                r.getOrganisationId(),
                "root_cause_analysis",
                r.getId(),
                java.util.Map.of("op", op, "status", r.getStatus()));
    }
}
