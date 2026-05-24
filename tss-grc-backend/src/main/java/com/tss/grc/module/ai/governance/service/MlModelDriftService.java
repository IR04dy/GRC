package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationResponse;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.entity.MlModelDriftObservation;
import com.tss.grc.module.ai.governance.repository.MlModelDriftObservationRepository;
import com.tss.grc.module.intake.entity.IntakeRecord;
import com.tss.grc.module.intake.repository.IntakeRecordRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records and queries post-market drift observations for {@link MlModel}s.
 *
 * <p>Two notable side-effects when a {@code CRITICAL} severity
 * observation is recorded:
 * <ul>
 *   <li>An {@link IntakeRecord} of kind {@code RISK_ISSUE} is opened
 *       automatically and its id linked back to the drift row
 *       ({@code triggered_issue_id}).</li>
 *   <li>The parent model's lifecycle stage is flipped to
 *       {@code REVISION} via {@link MlModelService#flipToRevisionDueToCriticalDrift}.</li>
 * </ul>
 *
 * <p>F8 event {@code MODEL_DRIFT_OBSERVED} is emitted on every record,
 * with severity in the payload so automation rules can subscribe to the
 * subset they care about.
 */
@Service
@RequiredArgsConstructor
public class MlModelDriftService {

    private static final SecureRandom RNG = new SecureRandom();

    private final MlModelDriftObservationRepository driftRepo;
    private final MlModelService modelService;
    private final IntakeRecordRepository intakeRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DriftObservationResponse> list(UUID modelExternalId) {
        Long orgId = TenantContext.getCurrentTenant();
        MlModel m = modelService.mustGet(modelExternalId);
        return driftRepo
                .findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByObservedAtDesc(orgId, m.getId())
                .stream()
                .map(DriftObservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DriftObservationResponse> listUnacknowledged() {
        Long orgId = TenantContext.getCurrentTenant();
        return driftRepo
                .findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(orgId)
                .stream()
                .map(DriftObservationResponse::from)
                .toList();
    }

    // ─── Writes ───────────────────────────────────────────────────

    @Transactional
    public DriftObservationResponse record(UUID modelExternalId, DriftObservationRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        MlModel m = modelService.mustGet(modelExternalId);

        MlModelDriftObservation d = new MlModelDriftObservation();
        d.setOrganisationId(orgId);
        d.setModelId(m.getId());
        d.setObservedAt(req.observedAt() == null ? LocalDateTime.now() : req.observedAt());
        d.setObservationType(req.observationType());
        d.setSeverity(req.severity());
        d.setMetricName(req.metricName());
        d.setMetricValue(req.metricValue());
        d.setBaselineValue(req.baselineValue());
        d.setDriftThreshold(req.driftThreshold());
        d.setNotesEn(req.notesEn());
        d.setNotesAr(req.notesAr());
        d = driftRepo.save(d);

        // CRITICAL severity → auto-open Issue + flip model to REVISION.
        if ("CRITICAL".equals(req.severity())) {
            IntakeRecord issue = openCriticalDriftIssue(orgId, m, d);
            d.setTriggeredIssueId(issue.getId());
            d = driftRepo.save(d);
            modelService.flipToRevisionDueToCriticalDrift(m,
                    "Critical drift on " + d.getMetricName() + " (" + d.getObservationType() + ")");
        }

        audit.log("CREATE", "MlModelDriftObservation", d.getId(),
                "Drift " + req.observationType() + "/" + req.severity()
                        + " on " + m.getModelCode() + " · " + req.metricName() + "=" + req.metricValue());
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "MODEL_DRIFT_OBSERVED",
                "MlModelDriftObservation", d.getId(), m.getModelCode(),
                payload(m, d)));
        return DriftObservationResponse.from(d);
    }

    @Transactional
    public DriftObservationResponse acknowledge(UUID modelExternalId, UUID obsExternalId, Long byUserId) {
        Long orgId = TenantContext.getCurrentTenant();
        // Verify the model is in tenant scope (defensive — controllers already path-bind).
        modelService.mustGet(modelExternalId);
        MlModelDriftObservation d = driftRepo
                .findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, obsExternalId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Drift observation not found: " + obsExternalId));
        d.setIsAcknowledged(true);
        d.setAcknowledgedAt(LocalDateTime.now());
        d.setAcknowledgedByUserId(byUserId);
        d = driftRepo.save(d);
        audit.log("UPDATE", "MlModelDriftObservation", d.getId(),
                "Drift observation acknowledged");
        return DriftObservationResponse.from(d);
    }

    /**
     * Open an intake-record "Issue" (kind {@code RISK_ISSUE}) for a
     * CRITICAL drift event. Routes per the same shape the intake
     * substrate uses elsewhere — SLA defaults to 14 days, channel
     * API_INGESTION (this is the system speaking, not a human form).
     */
    private IntakeRecord openCriticalDriftIssue(Long orgId, MlModel m, MlModelDriftObservation d) {
        IntakeRecord r = new IntakeRecord();
        r.setOrganisationId(orgId);
        r.setReferenceCode(generateIntakeRef());
        r.setKind("RISK_ISSUE");
        r.setStatus("SUBMITTED");
        r.setChannel("API_INGESTION");
        r.setSubmitterIsAnonymous(false);
        r.setSubmitterName("AI Governance — drift monitor");
        r.setAssignedTo(safeRole("RISK_MANAGER"));
        r.setSlaDueAt(LocalDateTime.now().plusDays(14));
        r.setSeverity("HIGH");
        r.setAcknowledgedAt(LocalDateTime.now());
        r.setLocale("en");
        String payload = "{"
                + "\"trigger\":\"AI_MODEL_CRITICAL_DRIFT\","
                + "\"modelCode\":\"" + esc(m.getModelCode()) + "\","
                + "\"modelTitleEn\":\"" + esc(m.getTitleEn()) + "\","
                + "\"observationType\":\"" + esc(d.getObservationType()) + "\","
                + "\"metricName\":\"" + esc(d.getMetricName()) + "\","
                + "\"metricValue\":" + (d.getMetricValue() == null ? "null" : d.getMetricValue().toPlainString()) + ","
                + "\"baselineValue\":" + (d.getBaselineValue() == null ? "null" : d.getBaselineValue().toPlainString()) + ","
                + "\"driftThreshold\":" + (d.getDriftThreshold() == null ? "null" : d.getDriftThreshold().toPlainString()) + ","
                + "\"notesEn\":\"" + esc(d.getNotesEn()) + "\""
                + "}";
        r.setPayload(payload);
        return intakeRepo.save(r);
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private static String safeRole(String role) { return role; }

    private static String generateIntakeRef() {
        // ISS-{epoch36}-{4chars} — short, sortable, very low collision odds.
        long now = System.currentTimeMillis();
        int rand = RNG.nextInt(0x10000);
        return String.format("ISS-%s-%04X", Long.toString(now, 36).toUpperCase(), rand);
    }

    private static Map<String, Object> payload(MlModel m, MlModelDriftObservation d) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("modelCode",       m.getModelCode() == null ? "" : m.getModelCode());
        p.put("modelId",         m.getId() == null ? "" : String.valueOf(m.getId()));
        p.put("observationType", d.getObservationType() == null ? "" : d.getObservationType());
        p.put("severity",        d.getSeverity() == null ? "" : d.getSeverity());
        p.put("metricName",      d.getMetricName() == null ? "" : d.getMetricName());
        p.put("metricValue",     d.getMetricValue() == null ? "" : d.getMetricValue().toPlainString());
        p.put("baseline",        d.getBaselineValue() == null ? "" : d.getBaselineValue().toPlainString());
        p.put("threshold",       d.getDriftThreshold() == null ? "" : d.getDriftThreshold().toPlainString());
        p.put("triggeredIssue",  d.getTriggeredIssueId() != null);
        return p;
    }
}
