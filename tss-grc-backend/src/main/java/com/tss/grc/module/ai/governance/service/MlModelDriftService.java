package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationResponse;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.entity.MlModelDriftObservation;
import com.tss.grc.module.ai.governance.repository.MlModelDriftObservationRepository;
import com.tss.grc.module.intake.spi.SystemIntakePort;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *   <li>An intake submission of kind {@code RISK_ISSUE} is filed
 *       automatically through {@link SystemIntakePort} and its id linked
 *       back to the drift row ({@code triggered_issue_id}).</li>
 *   <li>The parent model's lifecycle stage is flipped to
 *       {@code REVISION} via {@link MlModelService#flipToRevisionDueToCriticalDrift}.</li>
 * </ul>
 *
 * <p>F8 event {@code MODEL_DRIFT_OBSERVED} is emitted on every record,
 * with severity in the payload so automation rules can subscribe to the
 * subset they care about.
 *
 * <p>⚠ <b>F2-175 — that first bullet used to be a lie in five specific ways.</b> This class built the
 * {@code IntakeRecord} itself and called {@code IntakeRecordRepository.save()}, so it consulted none
 * of the routing catalogue, minted its own reference codes, hand-escaped its own JSON (wrongly, in a
 * way that made the record unpromotable), and wrote no history. It now states the facts and lets
 * intake own the record. The reason nobody noticed for as long as it existed: dev holds 26 drift
 * observations, 2 of them CRITICAL, and {@code triggered_issue_id} is NULL on all 26 — they were
 * seeded by {@code seed_gov_ai.py}, so <b>this code path had never once run.</b>
 */
@Service
@RequiredArgsConstructor
public class MlModelDriftService {

    /**
     * F2-179 — {@code CK_ml_drift_sev}, mirrored so the escalation trigger below can be asked as a
     * case-sensitive Java question of a value that has been normalised first.
     *
     * <p>The column's collation is case-<b>insensitive</b>, so it accepted {@code 'critical'},
     * stored it verbatim, and {@code "CRITICAL".equals(req.severity())} was then false: the
     * observation was recorded, looked entirely valid, filed no issue, and left the model
     * {@code IN_PRODUCTION}. Nothing anywhere reported a problem. Reproduced live (201, {@code
     * triggered_issue_id} NULL, model unchanged) before this was written.
     */
    static final ClosedVocabulary SEVERITY = ClosedVocabulary.of(
        "severity", "CK_ml_drift_sev", "INFO", "MINOR", "MODERATE", "MAJOR", "CRITICAL");

    /** {@code CK_ml_drift_type} — same shape, same column, no trigger hanging off it today. */
    static final ClosedVocabulary OBSERVATION_TYPE = ClosedVocabulary.of(
        "observationType", "CK_ml_drift_type",
        "DATA_DRIFT", "CONCEPT_DRIFT", "PERFORMANCE_DEGRADATION", "FAIRNESS_REGRESSION");

    /**
     * The single band that escalates.
     *
     * <p>⚙ Named rather than inlined because <b>whether {@code MAJOR} should escalate too</b> is the
     * one genuine decision in F2-179, and it is a governance call about AI post-market monitoring
     * (EU AI Act Art. 72 / SDAIA) rather than a casing bug. Deliberately left as the owner's: fixing
     * the casing must not smuggle a policy change in under a typo fix. If the answer is yes, this
     * constant becomes a set and {@code MlModelDriftServiceTest} says so in one place.
     */
    static final String ESCALATING_SEVERITY = "CRITICAL";

    private final MlModelDriftObservationRepository driftRepo;
    private final MlModelService modelService;
    private final SystemIntakePort intake;
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

        // F2-179 — normalise BEFORE anything reads these back. The DTO constrains severity only by
        // @Size(max = 20) and the CHECK cannot tell 'critical' from 'CRITICAL', so the boundary is
        // the only place the two artefacts can be made to agree.
        String observationType = OBSERVATION_TYPE.require(req.observationType());
        String severity = SEVERITY.require(req.severity());

        MlModelDriftObservation d = new MlModelDriftObservation();
        d.setOrganisationId(orgId);
        d.setModelId(m.getId());
        d.setObservedAt(req.observedAt() == null ? LocalDateTime.now() : req.observedAt());
        d.setObservationType(observationType);
        d.setSeverity(severity);
        d.setMetricName(req.metricName());
        d.setMetricValue(req.metricValue());
        d.setBaselineValue(req.baselineValue());
        d.setDriftThreshold(req.driftThreshold());
        d.setNotesEn(req.notesEn());
        d.setNotesAr(req.notesAr());

        // ── CRITICAL severity → file the Issue FIRST, then save the observation exactly once. ──
        //
        // ⚠⚠ The order here is the ROWVERSION landmine, and it bit during F2-175's live probe. The old
        // shape was save → open issue → mutate → save AGAIN, and it worked only because nothing between
        // the two saves forced a flush: the issue was built and inserted in this very persistence
        // context, so Hibernate could still batch the observation's INSERT and UPDATE together. Routing
        // the issue through @Transactional SystemIntakePort puts a real transactional boundary between
        // them, the insert flushes on its own, and the second save's
        // `UPDATE … WHERE row_version = <the value this session last saw>` matches zero rows →
        // StaleObjectStateException → HTTP 500, on a governance escalation.
        //
        // 🔑 So a latent double-save is not a style problem; it is a defect waiting for somebody to add a
        // transactional call between the two writes. CLAUDE.md's rule — "native first, mutate + save
        // once" — generalises: whatever else has to happen, do it BEFORE the single save. Nothing in the
        // issue payload needs the observation's id, so there was never a reason to save twice.
        //
        // ⚠ No unit test could have caught this. The repositories are mocked, and a mock has no
        // ROWVERSION — `verify(driftRepo, times(1)).save(...)` in MlModelDriftServiceTest is the pin,
        // and it exists because the live probe found what the green suite could not.
        String issueNote = "";
        if (ESCALATING_SEVERITY.equals(severity)) {
            SystemIntakePort.Receipt issue = openCriticalDriftIssue(orgId, m, d);
            d.setTriggeredIssueId(issue.id());
            // The receipt exists so the audit line can name the issue. The old path logged only the
            // observation, so a CRITICAL drift left no audit record of the escalation it raised.
            issueNote = " · issue " + issue.referenceCode() + " → " + issue.assignedTo();
        }

        d = driftRepo.save(d);

        if (!issueNote.isEmpty()) {
            modelService.flipToRevisionDueToCriticalDrift(m,
                    "Critical drift on " + d.getMetricName() + " (" + d.getObservationType() + ")");
        }

        // ⚠ The normalised values, not req.*(): an audit line reading "Drift DATA_DRIFT/critical"
        // over a row storing CRITICAL is a ledger that disagrees with the record it describes.
        audit.log("CREATE", "MlModelDriftObservation", d.getId(),
                "Drift " + observationType + "/" + severity
                        + " on " + m.getModelCode() + " · " + req.metricName() + "=" + req.metricValue()
                        + issueNote);
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
     * File an intake submission (kind {@code RISK_ISSUE}) for a CRITICAL drift event.
     *
     * <p>This method now only states the <b>facts of the drift</b>. Everything that used to be decided
     * here — the reference code, the SLA, the assignee, the status, the history, and how the payload is
     * escaped — belongs to the catalogue and to {@code IntakeService}, which is the whole of F2-175.
     * Three things it got wrong while it owned them, all measured:
     *
     * <ul>
     *   <li>it routed to {@code RISK_MANAGER}, a <em>role name</em> lifted from this module's own
     *       {@code hasAnyRole} guard, where the catalogue routes {@code RISK_ISSUE} to the stakeholder
     *       code {@code RISK_OFFICER};</li>
     *   <li>it minted {@code ISS-…} codes, a third prefix letter and a pair belonging to no
     *       catalogue entry;</li>
     *   <li>its hand-rolled escaper emitted invalid JSON for any C0 control character but
     *       {@code \n}/{@code \r} — so a model title pasted out of a spreadsheet produced an issue
     *       that could never be promoted into the risk register.</li>
     * </ul>
     *
     * <p>{@code severity} is passed explicitly: a CRITICAL drift raises a HIGH issue, not a CRITICAL
     * one, because the severity of the finding and the severity of the metric excursion are different
     * judgements — kept as it was rather than quietly re-graded.
     */
    private SystemIntakePort.Receipt openCriticalDriftIssue(Long orgId, MlModel m,
                                                           MlModelDriftObservation d) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trigger",         "AI_MODEL_CRITICAL_DRIFT");
        payload.put("modelCode",       m.getModelCode());
        payload.put("modelTitleEn",    m.getTitleEn());
        payload.put("observationType", d.getObservationType());
        payload.put("metricName",      d.getMetricName());
        payload.put("metricValue",     d.getMetricValue());
        payload.put("baselineValue",   d.getBaselineValue());
        payload.put("driftThreshold",  d.getDriftThreshold());
        payload.put("notesEn",         d.getNotesEn());
        payload.put("notesAr",         d.getNotesAr());

        return intake.fileSystemSubmission(new SystemIntakePort.SystemSubmission(
                orgId, "RISK_ISSUE", "API_INGESTION", "HIGH",
                "AI Governance — drift monitor", payload));
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
