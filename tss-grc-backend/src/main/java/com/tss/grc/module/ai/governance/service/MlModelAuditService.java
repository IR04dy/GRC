package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelAuditRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelAuditResponse;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.entity.MlModelAudit;
import com.tss.grc.module.ai.governance.repository.MlModelAuditRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Records and queries audit results against an {@link MlModel}.
 *
 * <p>An "audit" here is a fairness / bias / explainability / robustness /
 * privacy / security / performance test run with PASS or FAIL outcome —
 * one row per (model, type, date). The {@code metrics_json} field carries
 * the actual numeric outputs (e.g. {@code demographic_parity_diff},
 * {@code equal_opportunity_diff}, {@code accuracy}, {@code f1_score})
 * so they can be charted over time.
 *
 * <p>Side effects:
 * <ul>
 *   <li>FAIRNESS audit with status PASSED or PASSED_WITH_CONDITIONS
 *       sets {@code has_fairness_audit = true} and stamps
 *       {@code last_fairness_audit_date} on the parent model.</li>
 *   <li>EXPLAINABILITY audit with status PASSED or PASSED_WITH_CONDITIONS
 *       sets {@code has_explainability_doc = true}.</li>
 *   <li>FAILED audit logs F8 {@code MODEL_AUDIT_RECORDED} with
 *       {@code status=FAILED} so downstream automation can open a
 *       CAPA / Issue automatically — caller may also pass
 *       {@code capaTaskId} explicitly if a CAPA is already queued.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MlModelAuditService {

    private final MlModelAuditRepository auditRepo;
    private final MlModelService modelService;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MlModelAuditResponse> listAudits(UUID modelExternalId) {
        Long orgId = TenantContext.getCurrentTenant();
        MlModel m = modelService.mustGet(modelExternalId);
        return auditRepo
                .findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByAuditDateDesc(orgId, m.getId())
                .stream()
                .map(MlModelAuditResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<MlModelAuditResponse> getLatestByType(UUID modelExternalId, String auditType) {
        Long orgId = TenantContext.getCurrentTenant();
        MlModel m = modelService.mustGet(modelExternalId);
        return auditRepo
                .findTopByOrganisationIdAndModelIdAndAuditTypeAndIsDeletedFalseOrderByAuditDateDesc(
                        orgId, m.getId(), auditType)
                .map(MlModelAuditResponse::from);
    }

    // ─── Writes ───────────────────────────────────────────────────

    @Transactional
    public MlModelAuditResponse recordAudit(UUID modelExternalId, MlModelAuditRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        MlModel m = modelService.mustGet(modelExternalId);

        MlModelAudit a = new MlModelAudit();
        a.setOrganisationId(orgId);
        a.setModelId(m.getId());
        a.setAuditType(req.auditType());
        a.setAuditDate(req.auditDate() == null ? LocalDate.now() : req.auditDate());
        a.setAuditorUserId(req.auditorUserId());
        a.setAuditorExternalParty(req.auditorExternalParty());
        a.setMethodologyEn(req.methodologyEn());
        a.setMethodologyAr(req.methodologyAr());
        a.setFindingsEn(req.findingsEn());
        a.setFindingsAr(req.findingsAr());
        a.setStatus(req.status());
        a.setMetricsJson(req.metricsJson());
        a.setCapaTaskId(req.capaTaskId());
        a = auditRepo.save(a);

        // Side effects on parent compliance flags.
        boolean positiveStatus = "PASSED".equals(req.status())
                || "PASSED_WITH_CONDITIONS".equals(req.status())
                || "REMEDIATED".equals(req.status());
        if ("FAIRNESS".equals(req.auditType()) && positiveStatus) {
            m.setHasFairnessAudit(true);
            m.setLastFairnessAuditDate(a.getAuditDate());
        }
        if ("EXPLAINABILITY".equals(req.auditType()) && positiveStatus) {
            m.setHasExplainabilityDoc(true);
        }

        audit.log("CREATE", "MlModelAudit", a.getId(),
                "Audit " + req.auditType() + " · " + req.status()
                        + " · model=" + m.getModelCode());
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "MODEL_AUDIT_RECORDED",
                "MlModelAudit", a.getId(), m.getModelCode(),
                payload(m, a)));
        return MlModelAuditResponse.from(a);
    }

    private static Map<String, Object> payload(MlModel m, MlModelAudit a) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("modelCode",   safe(m.getModelCode()));
        p.put("modelId",     m.getId() == null ? "" : String.valueOf(m.getId()));
        p.put("auditType",   safe(a.getAuditType()));
        p.put("status",      safe(a.getStatus()));
        p.put("auditDate",   a.getAuditDate() == null ? "" : a.getAuditDate().toString());
        p.put("hasCapa",     a.getCapaTaskId() != null);
        p.put("riskTier",    safe(m.getRiskTier()));
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
