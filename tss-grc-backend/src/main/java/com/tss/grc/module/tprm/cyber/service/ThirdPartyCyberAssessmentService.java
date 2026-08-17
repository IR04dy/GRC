package com.tss.grc.module.tprm.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.common.validation.ServerOwnedState;
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

    /**
     * The lifecycle vocabulary, mirroring the {@code CHECK} V378 adds. Categorical in the sense that
     * matters here — DRAFT → IN_REVIEW → APPROVED → RETIRED is a progression, but nothing sorts or
     * thresholds it, so it is a {@link ClosedVocabulary} and not an {@code OrderedVocabulary}.
     */
    static final ClosedVocabulary STATUS = ClosedVocabulary.of(
            "status", "CK_tpc_status", "DRAFT", "IN_REVIEW", "APPROVED", "RETIRED");
    static final String DRAFT = "DRAFT";

    /**
     * Inherent risk is its <b>own</b> four-token vocabulary and deliberately not the residual ladder:
     * they overlap only at LOW. Two risk vocabularies on one entity is what let the residual ladder go
     * undeclared for so long (F2-196's shape inside a single table), so both are named now.
     */
    private static final ClosedVocabulary INHERENT = ClosedVocabulary.of(
            "inherentCyberRisk", "CK_tpc_inherent_cyber_risk",
            "LOW", "MEDIUM", "HIGH", "CRITICAL");

    /**
     * Upper bound (exclusive) of every rung except the top, in {@link ResidualRiskTier} order.
     *
     * <p>Held as bands over the declared ladder rather than as a ternary chain naming the five tokens
     * again: the derivation and the ordering now read the same list, so a rung cannot be added to one
     * without the other. The static check below is what makes that a build failure rather than a
     * silent cap at whatever rung the bands run out on.
     */
    private static final int[] SCORE_BANDS = {25, 45, 65, 85};

    static {
        if (SCORE_BANDS.length != ResidualRiskTier.ladder().size() - 1) {
            throw new IllegalStateException(
                "the residual score bands " + java.util.Arrays.toString(SCORE_BANDS)
                + " must divide the ladder " + ResidualRiskTier.ladder()
                + " into exactly its own number of rungs");
        }
    }

    private final ThirdPartyCyberAssessmentRepository repo;

    @Transactional(readOnly = true)
    public Page<ThirdPartyCyberAssessment> list(Pageable p) {
        return repo.findRegisterByResidualRisk(TenantContext.getCurrentTenant(), p);
    }
    @Transactional(readOnly = true)
    public Page<ThirdPartyCyberAssessment> listByStatus(String status, Pageable p) {
        return repo.findRegisterByResidualRiskAndStatus(TenantContext.getCurrentTenant(), status, p);
    }
    @Transactional(readOnly = true)
    public ThirdPartyCyberAssessment get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("ThirdPartyCyberAssessment " + id));
    }

    @Transactional
    public ThirdPartyCyberAssessment create(ThirdPartyCyberAssessment req) {
        req.setInherentCyberRisk(INHERENT.require(req.getInherentCyberRisk()));
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setId(null);
        // F2-220's shape, tenth instance: `if (status == null) status = "DRAFT"` is a DEFAULT, not a
        // guard, so a caller could create an assessment already APPROVED — skipping submit() and
        // approve(), which are the only things that stamp assessedAt, approvedAt and approverName.
        // No create form exists on any screen, so nothing that works today sends a status (F2-182).
        req.setStatus(ServerOwnedState.requireInitial(
                req.getStatus(), DRAFT, "third-party cyber assessment"));
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

        String tier = tierForScore(score);
        // Floor: a CRITICAL-inherent vendor cannot fall below MODERATE. Expressed as an elevation over
        // the declared ladder rather than as a pair of string equalities, so it stays a floor if a rung
        // is ever inserted below MODERATE (F2-229: a derived value must never lower a stored judgement,
        // and a floor written as `equals` silently stops being one when the ladder changes).
        String floor = ResidualRiskTier.ladder().get(1);
        if (INHERENT.is(a.getInherentCyberRisk(), ResidualRiskTier.LADDER.top())
                && !ResidualRiskTier.atLeast(tier, floor)) {
            tier = floor;
        }
        a.setResidualRiskTier(tier);
    }

    /**
     * The rung {@code score} lands on — the count of bands it has passed, indexed into the declared
     * ladder. Deliberately not a ternary chain naming the five tokens a second time.
     */
    private static String tierForScore(double score) {
        int rung = 0;
        while (rung < SCORE_BANDS.length && score >= SCORE_BANDS[rung]) rung++;
        return ResidualRiskTier.ladder().get(rung);
    }
}
