package com.tss.grc.module.risk.scenario.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.CreateRequest;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.UpdateRequest;
import com.tss.grc.module.risk.scenario.entity.RiskScenario;
import com.tss.grc.module.risk.scenario.repository.RiskScenarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle service for {@link RiskScenario}.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT ──approve()──▶ APPROVED
 *         ──reject()──▶ REJECTED   (reason required)
 *         ──archive()─▶ ARCHIVED   (reason required)
 *
 *   APPROVED ──archive()─▶ ARCHIVED
 * </pre>
 *
 * <p>REJECTED is terminal; ARCHIVED is terminal. Editing is allowed only
 * while DRAFT — once APPROVED the model is locked for audit.
 */
@Service
@RequiredArgsConstructor
public class RiskScenarioService {

    private static final Set<String> ALLOWED_TYPES = Set.of("MONTE_CARLO", "STRESS_TEST", "WHAT_IF");

    private final RiskScenarioRepository repo;

    @Autowired(required = false)
    private com.tss.grc.platform.realtime.BroadcastHelper broadcaster;

    // ─── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RiskScenario> list(Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<RiskScenario> listByStatus(String status, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(orgId, status, pageable);
    }

    @Transactional(readOnly = true)
    public RiskScenario get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new EntityNotFoundException("RiskScenario " + id));
    }

    @Transactional(readOnly = true)
    public List<RiskScenario> forRisk(Long riskId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(orgId, riskId);
    }

    // ─── Writes ───────────────────────────────────────────────────────

    @Transactional
    public RiskScenario create(CreateRequest req) {
        if (!ALLOWED_TYPES.contains(req.scenarioType())) {
            throw new IllegalArgumentException(
                    "Unknown scenarioType '" + req.scenarioType() + "'; expected one of " + ALLOWED_TYPES);
        }
        Long orgId = TenantContext.getCurrentTenant();
        RiskScenario s = new RiskScenario();
        s.setOrganisationId(orgId);
        s.setScenarioCode(generateScenarioCode());
        s.setExternalId(generateExternalId());

        s.setRiskId(req.riskId());
        s.setRiskCode(req.riskCode());
        s.setRiskTitleEn(req.riskTitleEn());

        s.setTitleEn(req.titleEn());
        s.setTitleAr(req.titleAr());
        s.setDescriptionEn(req.descriptionEn());
        s.setDescriptionAr(req.descriptionAr());

        s.setScenarioType(req.scenarioType());
        s.setHorizonMonths(req.horizonMonths() != null ? req.horizonMonths() : 12);
        s.setCurrency(req.currency() != null ? req.currency() : "SAR");

        s.setInputJson(req.inputJson());
        s.setResultJson(req.resultJson());
        s.setExpectedLoss(req.expectedLoss());
        s.setP95Loss(req.p95Loss());
        s.setP99Loss(req.p99Loss());

        s.setAssumptionsEn(req.assumptionsEn());
        s.setAssumptionsAr(req.assumptionsAr());
        s.setMethodologyNotesEn(req.methodologyNotesEn());
        s.setMethodologyNotesAr(req.methodologyNotesAr());

        s.setStatus("DRAFT");
        RiskScenario saved = repo.save(s);
        broadcast("created", saved);
        return saved;
    }

    @Transactional
    public RiskScenario update(Long id, UpdateRequest req) {
        RiskScenario s = get(id);
        if (!"DRAFT".equals(s.getStatus())) {
            throw new IllegalTransitionException(s.getStatus(), "DRAFT", Set.of("DRAFT"));
        }
        if (req.titleEn() != null) s.setTitleEn(req.titleEn());
        if (req.titleAr() != null) s.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null) s.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null) s.setDescriptionAr(req.descriptionAr());
        if (req.inputJson() != null) s.setInputJson(req.inputJson());
        if (req.resultJson() != null) s.setResultJson(req.resultJson());
        if (req.expectedLoss() != null) s.setExpectedLoss(req.expectedLoss());
        if (req.p95Loss() != null) s.setP95Loss(req.p95Loss());
        if (req.p99Loss() != null) s.setP99Loss(req.p99Loss());
        if (req.assumptionsEn() != null) s.setAssumptionsEn(req.assumptionsEn());
        if (req.assumptionsAr() != null) s.setAssumptionsAr(req.assumptionsAr());
        if (req.methodologyNotesEn() != null) s.setMethodologyNotesEn(req.methodologyNotesEn());
        if (req.methodologyNotesAr() != null) s.setMethodologyNotesAr(req.methodologyNotesAr());
        if (req.horizonMonths() != null) s.setHorizonMonths(req.horizonMonths());
        if (req.currency() != null) s.setCurrency(req.currency());
        broadcast("updated", s);
        return s;
    }

    @Transactional
    public RiskScenario approve(Long id, String approverName, Long approverId) {
        RiskScenario s = get(id);
        if (!"DRAFT".equals(s.getStatus())) {
            throw new IllegalTransitionException(s.getStatus(), "APPROVED", Set.of("DRAFT"));
        }
        s.setStatus("APPROVED");
        s.setApprovedAt(LocalDateTime.now());
        s.setApprovedBy(approverId);
        s.setApproverName(approverName);
        broadcast("approved", s);
        return s;
    }

    @Transactional
    public RiskScenario reject(Long id, String reason) {
        RiskScenario s = get(id);
        if (!"DRAFT".equals(s.getStatus())) {
            throw new IllegalTransitionException(s.getStatus(), "REJECTED", Set.of("DRAFT"));
        }
        s.setStatus("REJECTED");
        s.setRejectionReason(reason);
        broadcast("rejected", s);
        return s;
    }

    @Transactional
    public RiskScenario archive(Long id, String reason, Long actorId) {
        RiskScenario s = get(id);
        if ("ARCHIVED".equals(s.getStatus()) || "REJECTED".equals(s.getStatus())) {
            throw new IllegalTransitionException(s.getStatus(), "ARCHIVED", Set.of("DRAFT", "APPROVED"));
        }
        s.setStatus("ARCHIVED");
        s.setArchivedAt(LocalDateTime.now());
        s.setArchivedBy(actorId);
        s.setArchiveReason(reason);
        broadcast("archived", s);
        return s;
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /**
     * Mint a human-readable scenario code: SCN-{year}-{6 random hex}.
     * Collisions are vanishingly rare at this scale; we just retry once.
     */
    private String generateScenarioCode() {
        String code = "SCN-" + Year.now().getValue() + "-" + shortHex();
        Long orgId = TenantContext.getCurrentTenant();
        if (repo.findByOrganisationIdAndScenarioCode(orgId, code).isPresent()) {
            code = "SCN-" + Year.now().getValue() + "-" + shortHex();
        }
        return code;
    }

    private String generateExternalId() { return "SCN_" + UUID.randomUUID().toString().substring(0, 12); }

    private String shortHex() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private void broadcast(String event, RiskScenario s) {
        if (broadcaster != null) {
            broadcaster.entity(s.getOrganisationId(), "risk_scenario", s.getId(),
                    java.util.Map.of("event", event, "code", s.getScenarioCode(), "status", s.getStatus()));
        }
    }
}
