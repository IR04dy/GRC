package com.tss.grc.module.risk.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskTreatment;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.repository.RiskTreatmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Risk Treatment service — Risk-Advanced module.
 *
 * <p>Manages formal treatment plans against an open risk. Treatment
 * type follows ISO 31000 vocabulary:
 * <ul>
 *   <li><b>AVOID</b> — eliminate the risk source (discontinue activity).</li>
 *   <li><b>MITIGATE</b> — reduce likelihood / impact via controls.</li>
 *   <li><b>TRANSFER</b> — share via insurance / contracts / third party.</li>
 *   <li><b>ACCEPT</b> — formally accept; document the rationale.</li>
 * </ul>
 *
 * <p>Status machine: PLANNED → IN_PROGRESS → COMPLETED. CANCELLED is
 * the escape valve for any non-terminal state.
 *
 * <p>Rule T1 — every Risk in TREATMENT status must have at least one
 * non-terminal treatment. Service surfaces an audit warning if the
 * caller is about to remove the last live treatment from such a risk.
 */
@Service
@RequiredArgsConstructor
public class RiskTreatmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskTreatmentService.class);

    private static final Set<String> TREATMENT_TYPES =
        Set.of("AVOID", "MITIGATE", "TRANSFER", "ACCEPT");
    private static final Set<String> TREATMENT_STATUSES =
        Set.of("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED");

    private final RiskTreatmentRepository treatments;
    private final RiskRepository risks;

    private Long org() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public List<RiskTreatment> listForRisk(Long riskId) {
        ensureRiskInTenant(riskId);
        return treatments.findByRiskIdOrderByCreatedAtDesc(riskId);
    }

    @Transactional(readOnly = true)
    public RiskTreatment get(Long id) {
        return treatments.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Risk treatment not found: " + id));
    }

    @Transactional
    public RiskTreatment create(RiskTreatment body) {
        if (body.getRiskId() == null)        throw new IllegalArgumentException("riskId is required");
        if (body.getTreatmentType() == null) throw new IllegalArgumentException("treatmentType is required");
        if (!TREATMENT_TYPES.contains(body.getTreatmentType()))
            throw new IllegalArgumentException("treatmentType must be one of " + TREATMENT_TYPES);
        if (body.getDescriptionEn() == null || body.getDescriptionEn().isBlank())
            throw new IllegalArgumentException("descriptionEn is required");
        ensureRiskInTenant(body.getRiskId());
        body.setId(null);
        if (body.getStatus() == null) body.setStatus("PLANNED");
        if (!TREATMENT_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid status: " + body.getStatus());
        RiskTreatment saved = treatments.save(body);
        log.info("[risk-treatment] created id={} risk={} type={} status={}",
            saved.getId(), saved.getRiskId(), saved.getTreatmentType(), saved.getStatus());
        return saved;
    }

    @Transactional
    public RiskTreatment update(Long id, RiskTreatment body) {
        RiskTreatment t = get(id);
        ensureRiskInTenant(t.getRiskId());
        // Type can't change once recorded — would invalidate the audit history.
        if (body.getDescriptionEn() != null) t.setDescriptionEn(body.getDescriptionEn());
        if (body.getOwnerId() != null)       t.setOwnerId(body.getOwnerId());
        if (body.getDueDate() != null)       t.setDueDate(body.getDueDate());
        return treatments.save(t);
    }

    @Transactional
    public RiskTreatment transition(Long id, String to) {
        RiskTreatment t = get(id);
        ensureRiskInTenant(t.getRiskId());
        if (!TREATMENT_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = t.getStatus() == null ? "PLANNED" : t.getStatus();
        boolean ok = switch (from) {
            case "PLANNED"     -> "IN_PROGRESS".equals(to) || "CANCELLED".equals(to);
            case "IN_PROGRESS" -> "COMPLETED".equals(to) || "PLANNED".equals(to) || "CANCELLED".equals(to);
            case "COMPLETED"   -> false;   // terminal
            case "CANCELLED"   -> false;   // terminal
            default            -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        t.setStatus(to);
        if ("COMPLETED".equals(to)) t.setCompletedAt(LocalDateTime.now());
        return treatments.save(t);
    }

    @Transactional
    public void delete(Long id) {
        RiskTreatment t = get(id);
        Risk risk = ensureRiskInTenant(t.getRiskId());
        // Rule T1 — if the parent risk is in TREATMENT and this would
        // remove its last non-terminal treatment, refuse with a clear msg.
        if ("TREATMENT".equals(risk.getStatus())) {
            long live = treatments.findByRiskIdOrderByCreatedAtDesc(t.getRiskId()).stream()
                .filter(x -> !Objects.equals(x.getId(), id))
                .filter(x -> !"COMPLETED".equals(x.getStatus()) && !"CANCELLED".equals(x.getStatus()))
                .count();
            if (live == 0)
                throw new IllegalStateException(
                    "Risk " + risk.getId() + " is in TREATMENT — cannot delete the last live treatment. "
                    + "Move the Risk to ACCEPTED or MONITORING first, or add another treatment.");
        }
        t.setIsDeleted(true);
        treatments.save(t);
    }

    private Risk ensureRiskInTenant(Long riskId) {
        Long o = org();
        Risk r = risks.findById(riskId)
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + riskId));
        if (!Objects.equals(r.getOrganisationId(), o))
            throw new IllegalArgumentException("Risk " + riskId + " belongs to another organisation");
        return r;
    }
}
