package com.tss.grc.module.risk.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.dto.RiskTreatmentRequest;
import com.tss.grc.module.risk.dto.RiskTreatmentUpdateRequest;
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
        RiskTreatment t = treatments.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Risk treatment not found: " + id));
        // Tenant guard (SEC-RISK-001): a treatment whose parent risk is not in
        // this org is invisible — fail with a 404 so ids cannot be enumerated
        // across tenants. Mirrors the scoping already done by update/transition/delete.
        ensureRiskInTenant(t.getRiskId());
        return t;
    }

    /**
     * Create a treatment (SEC-RISK-002). Builds the entity from an allow-listed
     * DTO; {@code id}/{@code externalId} are server-generated and {@code status}
     * is forced to PLANNED — the client cannot pre-set status or completedAt.
     */
    @Transactional
    public RiskTreatment create(Long riskId, RiskTreatmentRequest req) {
        if (riskId == null)                 throw new IllegalArgumentException("riskId is required");
        if (req.treatmentType() == null)    throw new IllegalArgumentException("treatmentType is required");
        if (!TREATMENT_TYPES.contains(req.treatmentType()))
            throw new IllegalArgumentException("treatmentType must be one of " + TREATMENT_TYPES);
        if (req.descriptionEn() == null || req.descriptionEn().isBlank())
            throw new IllegalArgumentException("descriptionEn is required");
        ensureRiskInTenant(riskId);

        RiskTreatment body = new RiskTreatment();
        body.setRiskId(riskId);
        body.setTreatmentType(req.treatmentType());
        body.setDescriptionEn(req.descriptionEn());
        body.setDescriptionAr(req.descriptionAr());
        body.setOwnerId(req.ownerId());
        body.setDueDate(req.dueDate());
        body.setStatus("PLANNED");  // initial status forced server-side
        RiskTreatment saved = treatments.save(body);
        log.info("[risk-treatment] created id={} risk={} type={} status={}",
            saved.getId(), saved.getRiskId(), saved.getTreatmentType(), saved.getStatus());
        return saved;
    }

    /**
     * Update a treatment (SEC-RISK-002). Only the allow-listed fields on the DTO
     * are copied; {@code status}, {@code completedAt}, {@code riskId} and the
     * immutable {@code treatmentType} cannot be mass-assigned through update.
     */
    @Transactional
    public RiskTreatment update(Long id, RiskTreatmentUpdateRequest req) {
        RiskTreatment t = get(id);
        ensureRiskInTenant(t.getRiskId());
        // Type can't change once recorded — would invalidate the audit history.
        if (req.descriptionEn() != null) t.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null) t.setDescriptionAr(req.descriptionAr());
        if (req.ownerId() != null)       t.setOwnerId(req.ownerId());
        if (req.dueDate() != null)       t.setDueDate(req.dueDate());
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
            // SEC-RISK-001: do NOT reveal that the risk exists in another tenant —
            // a "belongs to another organisation" 400 would confirm the id and
            // enable cross-tenant enumeration. Treat it as not-found (404).
            throw new EntityNotFoundException("Risk not found: " + riskId);
        return r;
    }
}
