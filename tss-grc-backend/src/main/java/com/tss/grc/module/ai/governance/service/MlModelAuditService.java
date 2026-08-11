package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
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
import java.util.Set;
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
 *
 * <p><b>F2-182 — both side-effect conditions were case-sensitive comparisons against a
 * case-insensitive column.</b> {@code audit_type} and {@code status} are constrained by
 * {@code CK_ml_audit_type} / {@code CK_ml_audit_status}, on a
 * {@code Latin1_General_100_CI_AS_SC_UTF8} collation — so {@code auditType: "fairness"} with
 * {@code status: "passed"} satisfied the bean validation ({@code @NotBlank @Size} only),
 * satisfied both CHECKs, was stored verbatim in lower case, and then matched neither
 * {@code "FAIRNESS".equals(…)} nor {@code positiveStatus}. The audit row looked entirely valid
 * and <b>{@code has_fairness_audit} stayed false</b> — an EU AI Act Art. 9/15 and SDAIA
 * post-market attribute recorded as absent for an audit that had happened. The same casing also
 * hid the row from {@link #getLatestByType}, which queries the column directly.
 *
 * <p>Both vocabularies are now declared against the constraints they mirror, so
 * {@code ClosedVocabularyPinTest} checks them against the migration SQL by reflection, and the
 * side effects read the <b>normalised</b> values rather than the request's.
 */
@Service
@RequiredArgsConstructor
public class MlModelAuditService {

    /**
     * The audit disciplines. {@code AIA} is the Saudi AI-impact assessment added by V224 — which
     * dropped and re-added the constraint, so the vocabulary is read from the last definition in
     * version order, not the first.
     */
    static final ClosedVocabulary AUDIT_TYPE = ClosedVocabulary.of(
            "auditType", "CK_ml_audit_type",
            "PERFORMANCE", "SECURITY", "PRIVACY", "ROBUSTNESS", "EXPLAINABILITY",
            "BIAS", "FAIRNESS", "AIA");

    static final ClosedVocabulary AUDIT_STATUS = ClosedVocabulary.of(
            "status", "CK_ml_audit_status",
            "PASSED", "PASSED_WITH_CONDITIONS", "FAILED", "REMEDIATED");

    /** The two disciplines that satisfy a compliance attribute on the parent model. */
    static final String AUDIT_TYPE_FAIRNESS = "FAIRNESS";
    static final String AUDIT_TYPE_EXPLAINABILITY = "EXPLAINABILITY";

    /**
     * The outcomes that let an audit satisfy a compliance attribute. Named because it is a
     * governance judgement, not a casing detail: {@code REMEDIATED} counts because the finding was
     * closed, and whether that should still require a re-test is the owner's call — the F2-179
     * {@code ESCALATING_SEVERITY} pattern.
     */
    static final Set<String> SATISFYING_STATUSES =
            Set.of("PASSED", "PASSED_WITH_CONDITIONS", "REMEDIATED");

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

        // F2-182 — normalise before anything reads the value back, so the column, the two side-effect
        // conditions below and the frontend's label map all agree on one spelling.
        String auditType = AUDIT_TYPE.require(req.auditType());
        String status = AUDIT_STATUS.require(req.status());

        MlModelAudit a = new MlModelAudit();
        a.setOrganisationId(orgId);
        a.setModelId(m.getId());
        a.setAuditType(auditType);
        a.setAuditDate(req.auditDate() == null ? LocalDate.now() : req.auditDate());
        a.setAuditorUserId(req.auditorUserId());
        a.setAuditorExternalParty(req.auditorExternalParty());
        a.setMethodologyEn(req.methodologyEn());
        a.setMethodologyAr(req.methodologyAr());
        a.setFindingsEn(req.findingsEn());
        a.setFindingsAr(req.findingsAr());
        a.setStatus(status);
        a.setMetricsJson(req.metricsJson());
        a.setCapaTaskId(req.capaTaskId());
        a = auditRepo.save(a);

        // Side effects on parent compliance flags — compare the NORMALISED values, never req.*().
        // Plain equals, not ClosedVocabulary#is: is() exists for reading back a row that predates the
        // writer being closed, and its javadoc says so. These values were normalised three statements
        // ago, so needing is() here would mean the write above had not closed anything. What guards
        // the literals instead is that they are named constants, asserted against AUDIT_TYPE's tokens
        // by MlModelAuditVocabularyTest — a token the column cannot hold is always false (F2-177).
        boolean satisfying = SATISFYING_STATUSES.contains(status);
        if (AUDIT_TYPE_FAIRNESS.equals(auditType) && satisfying) {
            m.setHasFairnessAudit(true);
            m.setLastFairnessAuditDate(a.getAuditDate());
        }
        if (AUDIT_TYPE_EXPLAINABILITY.equals(auditType) && satisfying) {
            m.setHasExplainabilityDoc(true);
        }

        audit.log("CREATE", "MlModelAudit", a.getId(),
                "Audit " + auditType + " · " + status
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
