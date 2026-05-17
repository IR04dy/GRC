package com.tss.grc.module.tprm.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.cyber.entity.ThirdPartyCyberAssessment;
import com.tss.grc.module.tprm.cyber.repository.ThirdPartyCyberAssessmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle service for {@link ThirdPartyCyberAssessment}.
 *
 * <p>Risk score derivation (0-100, higher = worse):
 *   <ul>
 *     <li>External posture: contributes (100 - score) × 0.3</li>
 *     <li>Questionnaire:    contributes (100 - score) × 0.3</li>
 *     <li>Recent breach:    +20 if breach in last 24 months</li>
 *     <li>Critical vulns:   +5 per open critical CVE (capped at 25)</li>
 *     <li>MFA off:          +10</li>
 *     <li>No recent pentest: +10</li>
 *   </ul>
 *   Residual tier = LOW(<25) / MODERATE(<45) / ELEVATED(<65) / HIGH(<85) / CRITICAL(≥85),
 *   capped upward by inherent risk so a CRITICAL-inherent vendor never drops below MODERATE.
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyCyberAssessmentService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "IN_REVIEW", "APPROVED", "RETIRED");
    private static final Set<String> INHERENT = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final ThirdPartyCyberAssessmentRepository repo;

    @Transactional(readOnly = true)
    public Page<ThirdPartyCyberAssessment> list(Pageable p) {
        return repo.findByOrganisationIdOrderByResidualRiskTierDescIdDesc(TenantContext.getCurrentTenant(), p);
    }
    @Transactional(readOnly = true)
    public Page<ThirdPartyCyberAssessment> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByResidualRiskTierDescIdDesc(TenantContext.getCurrentTenant(), status, p);
    }
    @Transactional(readOnly = true)
    public ThirdPartyCyberAssessment get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("ThirdPartyCyberAssessment " + id));
    }

    @Transactional
    public ThirdPartyCyberAssessment create(ThirdPartyCyberAssessment req) {
        if (!INHERENT.contains(req.getInherentCyberRisk()))
            throw new IllegalArgumentException("inherentCyberRisk must be one of " + INHERENT);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setId(null);
        if (req.getStatus() == null) req.setStatus("DRAFT");
        recompute(req);
        return repo.save(req);
    }

    @Transactional
    public ThirdPartyCyberAssessment recordPosture(Long id, Map<String, Object> body) {
        ThirdPartyCyberAssessment a = get(id);
        if (body.get("externalPostureScore") instanceof Number n) a.setExternalPostureScore(n.intValue());
        if (body.get("questionnaireScore") instanceof Number n)   a.setQuestionnaireScore(n.intValue());
        if (body.get("criticalVulnsOpen") instanceof Number n)    a.setCriticalVulnsOpen(n.intValue());
        if (body.get("patchSlaCompliancePct") instanceof Number n) a.setPatchSlaCompliancePct(n.intValue());
        if (body.get("breachHistory24m") instanceof Boolean b)    a.setBreachHistory24m(b ? (short) 1 : (short) 0);
        if (body.get("mfaEnforced") instanceof Boolean b)         a.setMfaEnforced(b ? (short) 1 : (short) 0);
        if (body.get("penTestWithin12m") instanceof Boolean b)    a.setPenTestWithin12m(b ? (short) 1 : (short) 0);
        a.setQuestionnaireCompletedAt(LocalDateTime.now());
        recompute(a);
        return repo.save(a);
    }

    @Transactional
    public ThirdPartyCyberAssessment submit(Long id) {
        ThirdPartyCyberAssessment a = get(id);
        a.setStatus("IN_REVIEW");
        a.setAssessedAt(LocalDateTime.now());
        return repo.save(a);
    }

    @Transactional
    public ThirdPartyCyberAssessment approve(Long id, String approverName) {
        ThirdPartyCyberAssessment a = get(id);
        a.setStatus("APPROVED");
        a.setApprovedAt(LocalDateTime.now());
        a.setApproverName(approverName);
        return repo.save(a);
    }

    @Transactional
    public ThirdPartyCyberAssessment retire(Long id) {
        ThirdPartyCyberAssessment a = get(id);
        a.setStatus("RETIRED");
        return repo.save(a);
    }

    private void recompute(ThirdPartyCyberAssessment a) {
        double score = 0;
        if (a.getExternalPostureScore() != null) score += (100 - a.getExternalPostureScore()) * 0.3;
        if (a.getQuestionnaireScore()   != null) score += (100 - a.getQuestionnaireScore())   * 0.3;
        if (a.getBreachHistory24m() != null && a.getBreachHistory24m() == 1) score += 20;
        if (a.getCriticalVulnsOpen() != null) score += Math.min(25, a.getCriticalVulnsOpen() * 5.0);
        if (a.getMfaEnforced() == null || a.getMfaEnforced() == 0)        score += 10;
        if (a.getPenTestWithin12m() == null || a.getPenTestWithin12m() == 0) score += 10;
        if (score > 100) score = 100;
        a.setRiskScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));

        String tier = score < 25 ? "LOW"
                    : score < 45 ? "MODERATE"
                    : score < 65 ? "ELEVATED"
                    : score < 85 ? "HIGH"
                                 : "CRITICAL";
        // floor: a CRITICAL-inherent vendor cannot fall below MODERATE
        if ("CRITICAL".equals(a.getInherentCyberRisk()) && "LOW".equals(tier)) tier = "MODERATE";
        a.setResidualRiskTier(tier);
    }
}
