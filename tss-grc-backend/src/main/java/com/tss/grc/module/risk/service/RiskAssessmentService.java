package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskAssessment;
import com.tss.grc.module.risk.repository.RiskAssessmentRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Risk Assessment service — Risk-Advanced module.
 *
 * <p>Captures periodic re-assessments of a Risk's likelihood/impact
 * (typically every cycle / on incident bump / on control-effectiveness
 * change). Every assessment becomes the new "residual" snapshot on the
 * parent Risk row — keeps the heatmap accurate without forcing
 * consumers to join risk_assessments separately.
 *
 * <p>Rule A1 — likelihood and impact constrained to 1..5 (standard
 * heatmap scale).
 * <p>Rule A2 — recording an assessment promotes its likelihood/impact
 * into the parent Risk's residual_* columns and recomputes
 * residual_score = likelihood × impact in the DB (computed column).
 */
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentService.class);

    private final RiskAssessmentRepository assessments;
    private final RiskRepository risks;

    private Long org() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public List<RiskAssessment> listForRisk(Long riskId) {
        ensureRiskInTenant(riskId);
        return assessments.findByRiskIdOrderByAssessmentDateDesc(riskId);
    }

    @Transactional(readOnly = true)
    public RiskAssessment get(Long id) {
        return assessments.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Risk assessment not found: " + id));
    }

    @Transactional
    public RiskAssessment record(RiskAssessment body) {
        if (body.getRiskId() == null) throw new IllegalArgumentException("riskId is required");
        validateScale(body.getLikelihood(), "likelihood");
        validateScale(body.getImpact(), "impact");
        Risk risk = ensureRiskInTenant(body.getRiskId());
        body.setId(null);
        if (body.getAssessmentDate() == null) body.setAssessmentDate(LocalDate.now());
        RiskAssessment saved = assessments.save(body);

        // Rule A2: promote to parent Risk's residual snapshot.
        risk.setResidualLikelihood(body.getLikelihood());
        risk.setResidualImpact(body.getImpact());
        // residual_score is a computed column on the DB side, no manual set.
        risks.save(risk);

        log.info("[risk-assessment] recorded id={} risk={} L={} I={} → residual updated",
            saved.getId(), saved.getRiskId(), saved.getLikelihood(), saved.getImpact());
        return saved;
    }

    /** Risk owner / risk manager can correct the last assessment within the same day. */
    @Transactional
    public RiskAssessment correct(Long id, Integer likelihood, Integer impact, String notesEn) {
        RiskAssessment a = get(id);
        Risk risk = ensureRiskInTenant(a.getRiskId());
        if (likelihood != null) { validateScale(likelihood, "likelihood"); a.setLikelihood(likelihood); }
        if (impact != null)     { validateScale(impact, "impact");         a.setImpact(impact); }
        if (notesEn != null)    a.setAssessmentNotesEn(notesEn);
        RiskAssessment saved = assessments.save(a);
        // Mirror to parent Risk only if this is still the most-recent assessment.
        List<RiskAssessment> latest = assessments.findByRiskIdOrderByAssessmentDateDesc(a.getRiskId());
        if (!latest.isEmpty() && Objects.equals(latest.get(0).getId(), id)) {
            risk.setResidualLikelihood(saved.getLikelihood());
            risk.setResidualImpact(saved.getImpact());
            risks.save(risk);
        }
        return saved;
    }

    private Risk ensureRiskInTenant(Long riskId) {
        Long o = org();
        Risk r = risks.findById(riskId)
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + riskId));
        if (!Objects.equals(r.getOrganisationId(), o))
            throw new IllegalArgumentException("Risk " + riskId + " belongs to another organisation");
        return r;
    }

    private void validateScale(Integer v, String fieldName) {
        if (v == null) throw new IllegalArgumentException(fieldName + " is required");
        if (v < 1 || v > 5) throw new IllegalArgumentException(fieldName + " must be 1..5, got " + v);
    }
}
