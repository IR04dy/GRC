package com.tss.grc.module.tprm.outsourcing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DueDiligenceCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DueDiligenceResponse;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingDueDiligence;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingDueDiligenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 13 — Outsourcing due-diligence record service.
 *
 * <p>Composite score is the simple average of the six sub-scores
 * rounded to 2 decimal places. The recommendation is a free-form
 * field set by the assessor; this service does NOT auto-derive it
 * because two arrangements with identical composite scores can have
 * different recommendations once you factor in materiality, country,
 * and recent monitoring events.
 */
@Service
@RequiredArgsConstructor
public class OutsourcingDueDiligenceService {

    private final OutsourcingDueDiligenceRepository ddRepo;
    private final OutsourcingArrangementRepository arrangementRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    @Transactional(readOnly = true)
    public List<DueDiligenceResponse> listForArrangement(Long arrangementId) {
        return ddRepo.findByOrganisationIdAndArrangementIdAndIsDeletedFalseOrderByAssessmentDateDesc(
                org(), arrangementId)
            .stream().map(OutsourcingDueDiligenceService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DueDiligenceResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional
    public DueDiligenceResponse record(DueDiligenceCreateRequest req) {
        Long orgId = org();
        OutsourcingArrangement arr = arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.arrangementId())
            .orElseThrow(() -> new EntityNotFoundException("Arrangement not found: " + req.arrangementId()));

        OutsourcingDueDiligence dd = new OutsourcingDueDiligence();
        dd.setOrganisationId(orgId);
        dd.setArrangementId(arr.getId());
        dd.setAssessmentDate(req.assessmentDate());
        dd.setAssessorUserId(req.assessorUserId());
        dd.setFinancialHealthScore(req.financialHealthScore());
        dd.setOperationalCapabilityScore(req.operationalCapabilityScore());
        dd.setCybersecurityScore(req.cybersecurityScore());
        dd.setBcmScore(req.bcmScore());
        dd.setPdplComplianceScore(req.pdplComplianceScore());
        dd.setReputationScore(req.reputationScore());
        dd.setCompositeScore(computeComposite(
            req.financialHealthScore(), req.operationalCapabilityScore(),
            req.cybersecurityScore(), req.bcmScore(),
            req.pdplComplianceScore(), req.reputationScore()
        ));
        dd.setRecommendation(req.recommendation());
        dd.setRationaleEn(req.rationaleEn());
        dd.setRationaleAr(req.rationaleAr());

        OutsourcingDueDiligence saved = ddRepo.save(dd);
        audit.log("CREATE", "OutsourcingDueDiligence", saved.getId(),
            "Due diligence recorded for arrangement " + arr.getArrangementCode()
                + " — composite " + saved.getCompositeScore() + " (" + saved.getRecommendation() + ")");

        Map<String, Object> payload = new HashMap<>();
        payload.put("arrangementId", String.valueOf(arr.getId()));
        payload.put("arrangementCode", arr.getArrangementCode());
        payload.put("ddId", String.valueOf(saved.getId()));
        payload.put("compositeScore", String.valueOf(saved.getCompositeScore()));
        payload.put("recommendation", saved.getRecommendation());
        publisher.publishEvent(new DomainEvent(
            orgId, "OUTSOURCING_DD_COMPLETED", "OutsourcingDueDiligence", saved.getId(),
            "outsourcing-dd:" + saved.getId(), payload));

        return toResponse(saved);
    }

    /**
     * Average of the six sub-scores, rounded HALF_UP to 2dp.
     */
    public static BigDecimal computeComposite(int financial, int operational, int cyber,
                                                int bcm, int pdpl, int reputation) {
        BigDecimal sum = BigDecimal.valueOf((long) financial + operational + cyber + bcm + pdpl + reputation);
        return sum.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
    }

    private OutsourcingDueDiligence loadOrThrow(Long id) {
        return ddRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Due diligence not found: " + id));
    }

    static DueDiligenceResponse toResponse(OutsourcingDueDiligence dd) {
        return new DueDiligenceResponse(
            dd.getId(), dd.getExternalId(), dd.getOrganisationId(),
            dd.getArrangementId(), dd.getAssessmentDate(), dd.getAssessorUserId(),
            dd.getFinancialHealthScore(), dd.getOperationalCapabilityScore(),
            dd.getCybersecurityScore(), dd.getBcmScore(),
            dd.getPdplComplianceScore(), dd.getReputationScore(),
            dd.getCompositeScore(), dd.getRecommendation(),
            dd.getRationaleEn(), dd.getRationaleAr(),
            dd.getCreatedAt(), dd.getUpdatedAt()
        );
    }
}
