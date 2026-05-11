package com.tss.grc.module.risk.emerging.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.emerging.dto.EmergingRiskDtos.*;
import com.tss.grc.module.risk.emerging.entity.EmergingRisk;
import com.tss.grc.module.risk.emerging.repository.EmergingRiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmergingRiskService {

    private static final Set<String> ACTIVE = Set.of("HORIZON", "MONITORING");

    private final EmergingRiskRepository repo;

    @Transactional(readOnly = true)
    public Page<EmergingRisk> list(String status, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        return status == null
                ? repo.findByOrganisationIdOrderByCreatedAtDesc(org, pageable)
                : repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(org, status, pageable);
    }

    @Transactional(readOnly = true)
    public EmergingRisk get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("EmergingRisk " + id));
    }

    @Transactional
    public EmergingRisk create(CreateRequest req) {
        EmergingRisk e = new EmergingRisk();
        e.setOrganisationId(TenantContext.getCurrentTenant());
        e.setRiskCode(generateCode());
        e.setExternalId("ERX_" + UUID.randomUUID().toString().substring(0, 12));
        e.setTitleEn(req.titleEn());
        e.setTitleAr(req.titleAr());
        e.setDescriptionEn(req.descriptionEn());
        e.setDescriptionAr(req.descriptionAr());
        e.setCategory(req.category());
        if (req.source() != null) e.setSource(req.source());
        e.setSourceUrl(req.sourceUrl());
        e.setSourceCitation(req.sourceCitation());
        e.setHorizon(req.horizon());
        if (req.velocity() != null) e.setVelocity(req.velocity());
        if (req.signalStrength() != null) e.setSignalStrength(req.signalStrength());
        e.setEstimatedLikelihood(req.estimatedLikelihood());
        e.setEstimatedImpact(req.estimatedImpact());
        e.setMitigationThoughtsEn(req.mitigationThoughtsEn());
        e.setMitigationThoughtsAr(req.mitigationThoughtsAr());
        e.setOwner(req.owner());
        e.setTags(req.tags());
        e.setStatus("HORIZON");
        return repo.save(e);
    }

    @Transactional
    public EmergingRisk update(Long id, UpdateRequest req) {
        EmergingRisk e = get(id);
        if (!ACTIVE.contains(e.getStatus())) {
            throw new IllegalTransitionException(e.getStatus(), "EDIT", ACTIVE);
        }
        if (req.titleEn() != null) e.setTitleEn(req.titleEn());
        if (req.titleAr() != null) e.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null) e.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null) e.setDescriptionAr(req.descriptionAr());
        if (req.category() != null) e.setCategory(req.category());
        if (req.source() != null) e.setSource(req.source());
        if (req.sourceUrl() != null) e.setSourceUrl(req.sourceUrl());
        if (req.sourceCitation() != null) e.setSourceCitation(req.sourceCitation());
        if (req.horizon() != null) e.setHorizon(req.horizon());
        if (req.velocity() != null) e.setVelocity(req.velocity());
        if (req.signalStrength() != null) e.setSignalStrength(req.signalStrength());
        if (req.estimatedLikelihood() != null) e.setEstimatedLikelihood(req.estimatedLikelihood());
        if (req.estimatedImpact() != null) e.setEstimatedImpact(req.estimatedImpact());
        if (req.mitigationThoughtsEn() != null) e.setMitigationThoughtsEn(req.mitigationThoughtsEn());
        if (req.mitigationThoughtsAr() != null) e.setMitigationThoughtsAr(req.mitigationThoughtsAr());
        if (req.owner() != null) e.setOwner(req.owner());
        if (req.tags() != null) e.setTags(req.tags());
        if (req.nextReviewDue() != null) e.setNextReviewDue(req.nextReviewDue());
        return e;
    }

    @Transactional
    public EmergingRisk recordReview(Long id, ReviewRequest req, Long reviewerId) {
        EmergingRisk e = get(id);
        if (!ACTIVE.contains(e.getStatus())) {
            throw new IllegalTransitionException(e.getStatus(), "REVIEW", ACTIVE);
        }
        e.setLastReviewedAt(LocalDateTime.now());
        e.setLastReviewedBy(reviewerId);
        e.setLastReviewNotes(req.notes());
        if (req.nextReviewDue() != null) e.setNextReviewDue(req.nextReviewDue());
        if ("HORIZON".equals(e.getStatus())) e.setStatus("MONITORING");
        return e;
    }

    @Transactional
    public EmergingRisk escalate(Long id, EscalateRequest req, Long actorId) {
        EmergingRisk e = get(id);
        if (!ACTIVE.contains(e.getStatus())) {
            throw new IllegalTransitionException(e.getStatus(), "ESCALATED", ACTIVE);
        }
        e.setStatus("ESCALATED");
        e.setEscalatedAt(LocalDateTime.now());
        e.setEscalatedBy(actorId);
        e.setLinkedRiskCode(req.linkedRiskCode());
        return e;
    }

    @Transactional
    public EmergingRisk mature(Long id) {
        EmergingRisk e = get(id);
        if (!ACTIVE.contains(e.getStatus())) {
            throw new IllegalTransitionException(e.getStatus(), "MATURED", ACTIVE);
        }
        e.setStatus("MATURED");
        e.setMaturedAt(LocalDateTime.now());
        return e;
    }

    @Transactional
    public EmergingRisk dismiss(Long id, DismissRequest req) {
        EmergingRisk e = get(id);
        if (!ACTIVE.contains(e.getStatus())) {
            throw new IllegalTransitionException(e.getStatus(), "DISMISSED", ACTIVE);
        }
        e.setStatus("DISMISSED");
        e.setDismissedAt(LocalDateTime.now());
        e.setDismissReason(req.reason());
        return e;
    }

    private String generateCode() {
        String code = "ERX-" + Year.now().getValue() + "-" + shortHex();
        Long org = TenantContext.getCurrentTenant();
        if (repo.findByOrganisationIdAndRiskCode(org, code).isPresent()) {
            code = "ERX-" + Year.now().getValue() + "-" + shortHex();
        }
        return code;
    }

    private String shortHex() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
