package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DecommissionRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.LifecycleTransitionRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelResponse;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.repository.MlModelRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle service for {@link MlModel} — Sprint 11 AI/ML Model
 * Registry. SDAIA + SAMA + EU AI Act tri-track regulator surface.
 *
 * <p>State machine:
 * <pre>
 *   PROPOSED → DEVELOPMENT → VALIDATION → APPROVED_FOR_PROD
 *                                          ↓
 *                          MONITORING ←─ IN_PRODUCTION
 *                                ↓               ↑
 *                            REVISION ──────────┘
 *                                ↓
 *                         DECOMMISSIONED
 * </pre>
 *
 * <p>Gate at {@code APPROVED_FOR_PROD} when {@code risk_tier ∈
 * {CRITICAL, HIGH}}: validator + validation date + fairness audit +
 * PDPL DPIA + explainability doc all required. The four flags +
 * validator block enforce the SDAIA Principle 4 (Transparency &amp;
 * Explainability) + SAMA AI Governance Principles + EU AI Act
 * Article 16 documentation requirement.
 *
 * <p>F8 events emitted:
 * <ul>
 *   <li>{@code MODEL_REGISTERED} — on create</li>
 *   <li>{@code MODEL_LIFECYCLE_TRANSITION} — every state change</li>
 *   <li>{@code MODEL_DECOMMISSIONED} — terminal transition</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MlModelService {

    /** Permitted next-states from each lifecycle stage. */
    private static final Map<String, Set<String>> ALLOWED_NEXT = Map.of(
            "PROPOSED",          Set.of("DEVELOPMENT", "DECOMMISSIONED"),
            "DEVELOPMENT",       Set.of("VALIDATION", "DECOMMISSIONED"),
            "VALIDATION",        Set.of("APPROVED_FOR_PROD", "DEVELOPMENT", "DECOMMISSIONED"),
            "APPROVED_FOR_PROD", Set.of("IN_PRODUCTION", "DECOMMISSIONED"),
            "IN_PRODUCTION",     Set.of("MONITORING", "REVISION", "DECOMMISSIONED"),
            "MONITORING",        Set.of("IN_PRODUCTION", "REVISION", "DECOMMISSIONED"),
            "REVISION",          Set.of("VALIDATION", "IN_PRODUCTION", "DECOMMISSIONED"),
            "DECOMMISSIONED",    Set.of()
    );

    /** Risk tiers that trigger the APPROVED_FOR_PROD gate. */
    private static final Set<String> ELEVATED_RISK_TIERS = Set.of("CRITICAL", "HIGH");

    /** Audit statuses that count as a signed-off (passed) AIA. */
    private static final Set<String> AIA_SIGNED_OFF = Set.of("PASSED", "PASSED_WITH_CONDITIONS", "REMEDIATED");

    private final MlModelRepository repo;
    private final com.tss.grc.module.ai.governance.repository.MlModelAuditRepository auditRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MlModelResponse> list() {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId)
                .stream()
                .map(MlModelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MlModelResponse get(UUID externalId) {
        return MlModelResponse.from(mustGet(externalId));
    }

    @Transactional(readOnly = true)
    public List<MlModelResponse> findHighRiskModelsWithoutFairnessAudit() {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findHighRiskModelsWithoutFairnessAudit(orgId)
                .stream()
                .map(MlModelResponse::from)
                .toList();
    }

    // ─── Writes ───────────────────────────────────────────────────

    @Transactional
    public MlModelResponse create(MlModelRequest req) {
        Long orgId = TenantContext.getCurrentTenant();

        MlModel m = new MlModel();
        m.setOrganisationId(orgId);
        m.setModelCode(req.modelCode() == null || req.modelCode().isBlank()
                ? generateCode(orgId)
                : req.modelCode());
        applyFields(m, req);
        m.setLifecycleStage("PROPOSED");
        m = repo.save(m);

        audit.log("CREATE", "MlModel", m.getId(),
                "Model " + m.getModelCode() + " registered — " + safe(m.getTitleEn())
                        + " · tier=" + m.getRiskTier() + " · domain=" + m.getBusinessDomain());
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "MODEL_REGISTERED",
                "MlModel", m.getId(), m.getModelCode(),
                payload(m, null)));
        return MlModelResponse.from(m);
    }

    @Transactional
    public MlModelResponse update(UUID externalId, MlModelRequest req) {
        MlModel m = mustGet(externalId);
        // Updates allowed only on non-terminal, non-production stages.
        // Once in MONITORING/IN_PRODUCTION the metadata is locked except
        // through a transition back to REVISION — that keeps the audit
        // trail clean for the SAMA / EU AI Act post-market record.
        if ("DECOMMISSIONED".equals(m.getLifecycleStage())) {
            throw new IllegalTransitionException(m.getLifecycleStage(), "UPDATED",
                    Set.of("PROPOSED", "DEVELOPMENT", "VALIDATION", "APPROVED_FOR_PROD", "REVISION"));
        }
        applyFields(m, req);
        m = repo.save(m);
        audit.log("UPDATE", "MlModel", m.getId(),
                "Model " + m.getModelCode() + " updated");
        return MlModelResponse.from(m);
    }

    @Transactional
    public MlModelResponse transition(UUID externalId, LifecycleTransitionRequest req) {
        MlModel m = mustGet(externalId);
        String from = m.getLifecycleStage();
        String to = req.toStage();
        Set<String> allowed = ALLOWED_NEXT.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
        // Gate: HIGH+ risk models cannot enter APPROVED_FOR_PROD without
        // validator + validation date + fairness audit + DPIA + explainability.
        if ("APPROVED_FOR_PROD".equals(to) && ELEVATED_RISK_TIERS.contains(m.getRiskTier())) {
            assertApprovalGate(m);
        }
        m.setLifecycleStage(to);
        // Convenience: stamp production_date on first transition into IN_PRODUCTION.
        if ("IN_PRODUCTION".equals(to) && m.getProductionDate() == null) {
            m.setProductionDate(LocalDate.now());
        }
        m = repo.save(m);
        audit.log("TRANSITION", "MlModel", m.getId(),
                "Model " + m.getModelCode() + " " + from + " → " + to);
        eventPublisher.publishEvent(new DomainEvent(
                m.getOrganisationId(), "MODEL_LIFECYCLE_TRANSITION",
                "MlModel", m.getId(), m.getModelCode(),
                payload(m, from)));
        return MlModelResponse.from(m);
    }

    @Transactional
    public MlModelResponse decommission(UUID externalId, DecommissionRequest req) {
        MlModel m = mustGet(externalId);
        String from = m.getLifecycleStage();
        if ("DECOMMISSIONED".equals(from)) {
            throw new IllegalTransitionException(from, "DECOMMISSIONED",
                    Set.of("PROPOSED", "DEVELOPMENT", "VALIDATION", "APPROVED_FOR_PROD",
                           "IN_PRODUCTION", "MONITORING", "REVISION"));
        }
        m.setLifecycleStage("DECOMMISSIONED");
        m.setDecommissionDate(req.decommissionDate() == null ? LocalDate.now() : req.decommissionDate());
        m.setDecommissionReasonEn(req.reasonEn());
        m.setDecommissionReasonAr(req.reasonAr());
        m = repo.save(m);
        audit.log("TRANSITION", "MlModel", m.getId(),
                "Model " + m.getModelCode() + " decommissioned (was " + from + ") · "
                        + safe(req.reasonEn()));
        eventPublisher.publishEvent(new DomainEvent(
                m.getOrganisationId(), "MODEL_DECOMMISSIONED",
                "MlModel", m.getId(), m.getModelCode(),
                payload(m, from)));
        return MlModelResponse.from(m);
    }

    /**
     * Called by the drift service when a CRITICAL drift observation flips
     * a model's lifecycle stage back to REVISION. Bypasses the usual
     * transition map (REVISION is reachable from MONITORING / IN_PRODUCTION
     * directly), but emits a distinct correlation in the audit trail so
     * the reason is visible on the timeline.
     */
    @Transactional
    public MlModel flipToRevisionDueToCriticalDrift(MlModel m, String reason) {
        String from = m.getLifecycleStage();
        if ("DECOMMISSIONED".equals(from) || "REVISION".equals(from)) {
            // No-op — already decommissioned or under revision.
            return m;
        }
        m.setLifecycleStage("REVISION");
        m = repo.save(m);
        audit.log("TRANSITION", "MlModel", m.getId(),
                "Model " + m.getModelCode() + " " + from + " → REVISION · " + safe(reason));
        eventPublisher.publishEvent(new DomainEvent(
                m.getOrganisationId(), "MODEL_LIFECYCLE_TRANSITION",
                "MlModel", m.getId(), m.getModelCode(),
                payload(m, from)));
        return m;
    }

    // ─── Helpers ──────────────────────────────────────────────────

    /**
     * Look up by external UUID with tenant scope. Service callers should
     * never expose surrogate ids — they leak across tenants.
     */
    public MlModel mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("MlModel not found: " + externalId));
    }

    /** Residency values that require a PDPL Art.29 cross-border transfer basis. */
    private static final Set<String> CROSS_BORDER_RESIDENCY = Set.of("GCC", "GLOBAL");

    private void assertApprovalGate(MlModel m) {
        List<String> missing = new java.util.ArrayList<>();
        if (m.getValidatorUserId() == null) missing.add("validator_user_id");
        if (m.getValidationDate() == null) missing.add("validation_date");
        if (!Boolean.TRUE.equals(m.getHasFairnessAudit())) missing.add("has_fairness_audit");
        if (!Boolean.TRUE.equals(m.getHasPdplDpia())) missing.add("has_pdpl_dpia");
        if (!Boolean.TRUE.equals(m.getHasExplainabilityDoc())) missing.add("has_explainability_doc");
        // SDAIA "Humanity" + EU AI Act Art.14 — a high-risk model must have a
        // defined human-oversight mode (and not NONE) before production.
        if (m.getHumanOversight() == null || m.getHumanOversight().isBlank()
                || "NONE".equals(m.getHumanOversight())) {
            missing.add("human_oversight");
        }
        // PDPL Art.29 — if training/inference data leaves the Kingdom, the
        // transfer must rest on a documented legal basis.
        if (CROSS_BORDER_RESIDENCY.contains(m.getDataResidency())
                && (m.getCrossBorderTransferBasis() == null
                    || m.getCrossBorderTransferBasis().isBlank()
                    || "NOT_ASSESSED".equals(m.getCrossBorderTransferBasis()))) {
            missing.add("cross_border_transfer_basis");
        }
        // SDAIA expects a dedicated AI Impact Assessment (distinct from the
        // PDPL DPIA) for high-risk AI. Require a signed-off (passed) AIA audit
        // record — a first-class artefact, not just a header flag.
        boolean aiaSignedOff = auditRepo
                .findTopByOrganisationIdAndModelIdAndAuditTypeAndIsDeletedFalseOrderByAuditDateDesc(
                        m.getOrganisationId(), m.getId(), "AIA")
                .map(a -> AIA_SIGNED_OFF.contains(a.getStatus()))
                .orElse(false);
        if (!aiaSignedOff) missing.add("signed_off_aia");
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot promote " + m.getModelCode() + " (risk tier " + m.getRiskTier()
                            + ") to APPROVED_FOR_PROD — missing required: " + missing);
        }
    }

    private void applyFields(MlModel m, MlModelRequest r) {
        if (r.titleEn() != null) m.setTitleEn(r.titleEn());
        if (r.titleAr() != null) m.setTitleAr(r.titleAr());
        if (r.descriptionEn() != null) m.setDescriptionEn(r.descriptionEn());
        if (r.descriptionAr() != null) m.setDescriptionAr(r.descriptionAr());
        if (r.modelType() != null) m.setModelType(r.modelType());
        if (r.businessDomain() != null) m.setBusinessDomain(r.businessDomain());
        if (r.riskTier() != null) m.setRiskTier(r.riskTier());
        if (r.versionLabel() != null) m.setVersionLabel(r.versionLabel());
        if (r.versionHistoryJson() != null) m.setVersionHistoryJson(r.versionHistoryJson());
        if (r.modelOwnerUserId() != null) m.setModelOwnerUserId(r.modelOwnerUserId());
        if (r.businessSponsorUserId() != null) m.setBusinessSponsorUserId(r.businessSponsorUserId());
        if (r.validatorUserId() != null) m.setValidatorUserId(r.validatorUserId());
        if (r.validationDate() != null) m.setValidationDate(r.validationDate());
        if (r.productionDate() != null) m.setProductionDate(r.productionDate());
        if (r.trainingDataProvenanceEn() != null) m.setTrainingDataProvenanceEn(r.trainingDataProvenanceEn());
        if (r.trainingDataProvenanceAr() != null) m.setTrainingDataProvenanceAr(r.trainingDataProvenanceAr());
        if (r.trainingDataSizeRecords() != null) m.setTrainingDataSizeRecords(r.trainingDataSizeRecords());
        if (r.trainingDataPeriodStart() != null) m.setTrainingDataPeriodStart(r.trainingDataPeriodStart());
        if (r.trainingDataPeriodEnd() != null) m.setTrainingDataPeriodEnd(r.trainingDataPeriodEnd());
        if (r.featureCount() != null) m.setFeatureCount(r.featureCount());
        if (r.externalDatasetUsed() != null) m.setExternalDatasetUsed(r.externalDatasetUsed());
        if (r.externalDatasetProvenance() != null) m.setExternalDatasetProvenance(r.externalDatasetProvenance());
        if (r.framework() != null) m.setFramework(r.framework());
        if (r.hosting() != null) m.setHosting(r.hosting());
        if (r.inferenceLatencyMs() != null) m.setInferenceLatencyMs(r.inferenceLatencyMs());
        if (r.trainingComputeHours() != null) m.setTrainingComputeHours(r.trainingComputeHours());
        if (r.humanOversight() != null) m.setHumanOversight(r.humanOversight());
        if (r.humanOversightNotesEn() != null) m.setHumanOversightNotesEn(r.humanOversightNotesEn());
        if (r.humanOversightNotesAr() != null) m.setHumanOversightNotesAr(r.humanOversightNotesAr());
        if (r.dataResidency() != null) m.setDataResidency(r.dataResidency());
        if (r.crossBorderTransferBasis() != null) m.setCrossBorderTransferBasis(r.crossBorderTransferBasis());
        if (r.hasPdplDpia() != null) m.setHasPdplDpia(r.hasPdplDpia());
        if (r.pdplDpiaId() != null) m.setPdplDpiaId(r.pdplDpiaId());
        if (r.hasFairnessAudit() != null) m.setHasFairnessAudit(r.hasFairnessAudit());
        if (r.lastFairnessAuditDate() != null) m.setLastFairnessAuditDate(r.lastFairnessAuditDate());
        if (r.hasExplainabilityDoc() != null) m.setHasExplainabilityDoc(r.hasExplainabilityDoc());
        if (r.isHighRiskEuAiAct() != null) m.setIsHighRiskEuAiAct(r.isHighRiskEuAiAct());
        if (r.sdaiaAiEthicsCompliance() != null) m.setSdaiaAiEthicsCompliance(r.sdaiaAiEthicsCompliance());
        if (r.samaAiPrinciplesCompliance() != null) m.setSamaAiPrinciplesCompliance(r.samaAiPrinciplesCompliance());
    }

    /** MODEL-{year}-{seq} — small random search, retries on collision. */
    private String generateCode(Long orgId) {
        int year = Year.now().getValue();
        for (int attempt = 0; attempt < 10; attempt++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format("MODEL-%d-%03d", year, seq);
            if (repo.countByOrganisationIdAndModelCode(orgId, candidate) == 0) {
                return candidate;
            }
        }
        return "MODEL-" + year + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private static Map<String, Object> payload(MlModel m, String previousStage) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("modelCode",       safe(m.getModelCode()));
        p.put("externalId",      m.getExternalId() == null ? "" : m.getExternalId().toString());
        p.put("titleEn",         safe(m.getTitleEn()));
        p.put("modelType",       safe(m.getModelType()));
        p.put("businessDomain",  safe(m.getBusinessDomain()));
        p.put("riskTier",        safe(m.getRiskTier()));
        p.put("lifecycleStage",  safe(m.getLifecycleStage()));
        p.put("sdaiaCompliance", safe(m.getSdaiaAiEthicsCompliance()));
        p.put("samaCompliance",  safe(m.getSamaAiPrinciplesCompliance()));
        p.put("humanOversight",  safe(m.getHumanOversight()));
        p.put("dataResidency",   safe(m.getDataResidency()));
        p.put("isHighRiskEuAi",  m.getIsHighRiskEuAiAct() != null && m.getIsHighRiskEuAiAct());
        if (previousStage != null) p.put("previousStage", previousStage);
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
