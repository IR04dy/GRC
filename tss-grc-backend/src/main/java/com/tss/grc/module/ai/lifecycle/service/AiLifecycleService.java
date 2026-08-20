package com.tss.grc.module.ai.lifecycle.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.lifecycle.dto.AiLifecycleDtos.*;
import com.tss.grc.module.ai.lifecycle.entity.*;
import com.tss.grc.module.ai.lifecycle.repository.*;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Sprint 31 AI/ML Model Lifecycle service. */
@Service
@Transactional
@RequiredArgsConstructor
public class AiLifecycleService {

    private static final Map<String, Set<String>> VAL_ALLOWED = Map.of(
            "SCHEDULED",               Set.of("IN_PROGRESS"),
            "IN_PROGRESS",             Set.of("PASSED", "PASSED_WITH_CONDITIONS", "FAILED"),
            "PASSED",                  Set.of(),
            "PASSED_WITH_CONDITIONS",  Set.of("PASSED"),
            "FAILED",                  Set.of("REMEDIATION"),
            "REMEDIATION",             Set.of("PASSED", "FAILED")
    );

    private static final Map<String, Set<String>> RET_ALLOWED = Map.of(
            "PROPOSED",    Set.of("APPROVED", "REJECTED", "ABANDONED"),
            "APPROVED",    Set.of("IN_PROGRESS", "ABANDONED"),
            "IN_PROGRESS", Set.of("DEPLOYED", "ABANDONED"),
            "DEPLOYED",    Set.of("ROLLED_BACK"),
            "ROLLED_BACK", Set.of(),
            "REJECTED",    Set.of(),
            "ABANDONED",   Set.of()
    );

    private static final Map<String, Set<String>> REC_ALLOWED = Map.of(
            "DUE",                          Set.of("IN_REVIEW", "EXPIRED"),
            "IN_REVIEW",                    Set.of("RECERTIFIED", "RECERTIFIED_WITH_CONDITIONS", "REVOKED"),
            "RECERTIFIED",                  Set.of(),
            "RECERTIFIED_WITH_CONDITIONS",  Set.of("RECERTIFIED"),
            "REVOKED",                      Set.of(),
            "EXPIRED",                      Set.of()
    );

    private final ModelValidationRepository validationRepo;
    private final RetrainingEventRepository retrainingRepo;
    private final RecertificationRepository recertRepo;
    private final AiLifecycleEventRepository eventRepo;
    private final ApplicationEventPublisher publisher;

    // ──────────────────────────────────────────────────── Validation

    public ValidationResponse createValidation(CreateValidationRequest req) {
        ModelValidation v = new ModelValidation();
        v.setOrganisationId(org());
        v.setModelId(req.modelId());
        v.setReference(req.reference());
        v.setValidationType(req.validationType());
        v.setValidatorUserId(req.validatorUserId());
        v.setValidatorOrgLabel(req.validatorOrgLabel());
        v.setScheduledAt(req.scheduledAt());
        v.setStatus("SCHEDULED");
        validationRepo.save(v);
        timeline(req.modelId(), v.getId(), null, null,
                "VALIDATION_SCHEDULED", "SCHEDULED",
                "Model validation scheduled.",
                "تم جدولة تحقق النموذج.");
        emit("MODEL_VALIDATION_SCHEDULED", "MODEL_VALIDATION", v.getId(),
                Map.of("modelId", v.getModelId(), "reference", v.getReference()));
        return toVal(v);
    }

    public ValidationResponse transitionValidation(Long id, TransitionValidationRequest req) {
        ModelValidation v = loadVal(id);
        validate(VAL_ALLOWED, v.getStatus(), req.toStatus(), "Validation");
        v.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "IN_PROGRESS" -> v.setStartedAt(now);
            case "PASSED", "PASSED_WITH_CONDITIONS", "FAILED" -> {
                v.setCompletedAt(now);
                if (req.metricsJson() != null)      v.setMetricsJson(req.metricsJson());
                if (req.ethicsScoresJson() != null) v.setEthicsScoresJson(req.ethicsScoresJson());
                if (req.fairnessDisparityPct() != null) v.setFairnessDisparityPct(req.fairnessDisparityPct());
                if (req.findingsEn() != null)   v.setFindingsEn(req.findingsEn());
                if (req.findingsAr() != null)   v.setFindingsAr(req.findingsAr());
                if (req.conditionsEn() != null) v.setConditionsEn(req.conditionsEn());
                if (req.conditionsAr() != null) v.setConditionsAr(req.conditionsAr());
                if (req.linkedCapaId() != null) v.setLinkedCapaId(req.linkedCapaId());
            }
            default -> { /* no-op */ }
        }
        validationRepo.save(v);
        timeline(v.getModelId(), v.getId(), null, null,
                "VALIDATION_" + req.toStatus(), req.toStatus(),
                req.findingsEn(), req.findingsAr());
        emit("MODEL_VALIDATION_" + req.toStatus(), "MODEL_VALIDATION", v.getId(),
                Map.of("modelId", v.getModelId(),
                        "fairnessDisparityPct",
                        v.getFairnessDisparityPct() == null ? "0" : v.getFairnessDisparityPct().toString()));
        return toVal(v);
    }

    public List<ValidationResponse> listValidations(Long modelId) {
        if (modelId != null) {
            return validationRepo.findByOrganisationIdAndModelIdOrderByCreatedAtDesc(org(), modelId)
                    .stream().map(AiLifecycleService::toVal).toList();
        }
        return validationRepo.findByOrganisationIdOrderByCreatedAtDesc(org())
                .stream().map(AiLifecycleService::toVal).toList();
    }

    // ─────────────────────────────────────────────────── Retraining

    public RetrainingResponse createRetraining(CreateRetrainingRequest req) {
        RetrainingEvent r = new RetrainingEvent();
        r.setOrganisationId(org());
        r.setModelId(req.modelId());
        r.setReference(req.reference());
        r.setTriggerReason(req.triggerReason());
        r.setRationaleEn(req.rationaleEn());
        r.setRationaleAr(req.rationaleAr());
        r.setFromVersionLabel(req.fromVersionLabel());
        r.setToVersionLabel(req.toVersionLabel());
        r.setOwnerUserId(req.ownerUserId());
        r.setStatus("PROPOSED");
        retrainingRepo.save(r);
        timeline(req.modelId(), null, r.getId(), null,
                "RETRAINING_PROPOSED", "PROPOSED",
                "Retraining cycle proposed (" + req.triggerReason() + ").",
                "اقتراح دورة إعادة تدريب (" + req.triggerReason() + ").");
        emit("MODEL_RETRAINING_PROPOSED", "RETRAINING_EVENT", r.getId(),
                Map.of("modelId", r.getModelId(),
                        "triggerReason", req.triggerReason() == null ? "" : req.triggerReason()));
        return toRet(r);
    }

    public RetrainingResponse transitionRetraining(Long id, TransitionRetrainingRequest req) {
        RetrainingEvent r = loadRet(id);
        validate(RET_ALLOWED, r.getStatus(), req.toStatus(), "Retraining");
        r.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "APPROVED"    -> r.setApprovedAt(now);
            case "IN_PROGRESS" -> r.setStartedAt(now);
            case "DEPLOYED" -> {
                r.setDeployedAt(LocalDate.now());
                if (req.postDeployMetricsJson() != null) r.setPostDeployMetricsJson(req.postDeployMetricsJson());
            }
            case "ROLLED_BACK" -> {
                r.setRolledBackAt(LocalDate.now());
                r.setRollbackReasonEn(req.rollbackReasonEn());
                r.setRollbackReasonAr(req.rollbackReasonAr());
            }
            default -> { /* no-op */ }
        }
        retrainingRepo.save(r);
        timeline(r.getModelId(), null, r.getId(), null,
                "RETRAINING_" + req.toStatus(), req.toStatus(),
                req.rollbackReasonEn(), req.rollbackReasonAr());
        emit("MODEL_RETRAINING_" + req.toStatus(), "RETRAINING_EVENT", r.getId(),
                Map.of("modelId", r.getModelId()));
        return toRet(r);
    }

    public List<RetrainingResponse> listRetrainings(Long modelId) {
        if (modelId != null) {
            return retrainingRepo.findByOrganisationIdAndModelIdOrderByCreatedAtDesc(org(), modelId)
                    .stream().map(AiLifecycleService::toRet).toList();
        }
        return retrainingRepo.findByOrganisationIdOrderByCreatedAtDesc(org())
                .stream().map(AiLifecycleService::toRet).toList();
    }

    // ──────────────────────────────────────────────── Recertification

    public RecertificationResponse createRecertification(CreateRecertificationRequest req) {
        Recertification c = new Recertification();
        c.setOrganisationId(org());
        c.setModelId(req.modelId());
        c.setReference(req.reference());
        c.setCycleLabel(req.cycleLabel());
        c.setDueDate(req.dueDate());
        c.setStatus("DUE");
        recertRepo.save(c);
        timeline(req.modelId(), null, null, c.getId(),
                "RECERTIFICATION_DUE", "DUE",
                "Recertification cycle " + req.cycleLabel() + " queued.",
                "إدراج دورة إعادة الاعتماد " + req.cycleLabel() + ".");
        return toRec(c);
    }

    public RecertificationResponse transitionRecertification(Long id, TransitionRecertificationRequest req) {
        Recertification c = loadRec(id);
        validate(REC_ALLOWED, c.getStatus(), req.toStatus(), "Recertification");
        c.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "IN_REVIEW" -> c.setStartedAt(now);
            case "EXPIRED" -> {
                recordDecision(c, now);
                if (req.decisionRationaleEn() != null) c.setDecisionRationaleEn(req.decisionRationaleEn());
                if (req.decisionRationaleAr() != null) c.setDecisionRationaleAr(req.decisionRationaleAr());
            }
            case "RECERTIFIED", "RECERTIFIED_WITH_CONDITIONS", "REVOKED" -> {
                recordDecision(c, now);
                if (req.decisionRationaleEn() != null) c.setDecisionRationaleEn(req.decisionRationaleEn());
                if (req.decisionRationaleAr() != null) c.setDecisionRationaleAr(req.decisionRationaleAr());
                if (req.conditionsEn() != null) c.setConditionsEn(req.conditionsEn());
                if (req.conditionsAr() != null) c.setConditionsAr(req.conditionsAr());
                if (req.nextDueDate() != null) c.setNextDueDate(req.nextDueDate());
                if (req.linkedValidationId() != null) c.setLinkedValidationId(req.linkedValidationId());
            }
            default -> { /* no-op */ }
        }
        recertRepo.save(c);
        timeline(c.getModelId(), null, null, c.getId(),
                "RECERTIFICATION_" + req.toStatus(), req.toStatus(),
                req.decisionRationaleEn(), req.decisionRationaleAr());
        emit("MODEL_RECERTIFICATION_" + req.toStatus(), "RECERTIFICATION", c.getId(),
                Map.of("modelId", c.getModelId()));
        return toRec(c);
    }

    public List<RecertificationResponse> listRecertifications(Long modelId) {
        if (modelId != null) {
            return recertRepo.findByOrganisationIdAndModelIdOrderByCreatedAtDesc(org(), modelId)
                    .stream().map(AiLifecycleService::toRec).toList();
        }
        return recertRepo.findByOrganisationIdOrderByCreatedAtDesc(org())
                .stream().map(AiLifecycleService::toRec).toList();
    }

    // ───────────────────────────────────────────────────── Timeline

    public List<EventResponse> timeline(Long modelId) {
        return eventRepo.findByOrganisationIdAndModelIdOrderByOccurredAtDesc(org(), modelId)
                .stream().map(AiLifecycleService::toEvent).toList();
    }

    // ──────────────────────────────────────────────────── Dashboard

    public DashboardResponse dashboard() {
        Long o = org();
        List<ModelValidation> allVal = validationRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        List<RetrainingEvent> allRet = retrainingRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        List<Recertification> allRec = recertRepo.findByOrganisationIdOrderByCreatedAtDesc(o);

        long valScheduled = allVal.stream().filter(v -> "SCHEDULED".equals(v.getStatus())).count();
        long valInProgress = allVal.stream().filter(v -> "IN_PROGRESS".equals(v.getStatus())).count();
        long valFailOrCond = allVal.stream()
                .filter(v -> "FAILED".equals(v.getStatus())
                          || "PASSED_WITH_CONDITIONS".equals(v.getStatus())
                          || "REMEDIATION".equals(v.getStatus())).count();
        long retInProgress = allRet.stream()
                .filter(r -> "APPROVED".equals(r.getStatus()) || "IN_PROGRESS".equals(r.getStatus())).count();
        LocalDate q0 = LocalDate.now().minusDays(90);
        long retDeployedQ = allRet.stream()
                .filter(r -> "DEPLOYED".equals(r.getStatus()))
                .filter(r -> r.getDeployedAt() != null && !r.getDeployedAt().isBefore(q0)).count();
        long retRolledBack = retrainingRepo.countByOrganisationIdAndStatus(o, "ROLLED_BACK");

        LocalDate today = LocalDate.now();
        long recOverdue = recertRepo.findOverdue(o, today).size();
        long recDue90 = allRec.stream()
                .filter(r -> "DUE".equals(r.getStatus()))
                .filter(r -> r.getDueDate() != null
                        && !r.getDueDate().isBefore(today)
                        && !r.getDueDate().isAfter(today.plusDays(90))).count();

        Map<String, Long> valByStatus = allVal.stream()
                .collect(Collectors.groupingBy(ModelValidation::getStatus, Collectors.counting()));
        Map<String, Long> retByStatus = allRet.stream()
                .collect(Collectors.groupingBy(RetrainingEvent::getStatus, Collectors.counting()));
        Map<String, Long> recByStatus = allRec.stream()
                .collect(Collectors.groupingBy(Recertification::getStatus, Collectors.counting()));

        List<ValidationResponse> recentVal = allVal.stream().limit(8)
                .map(AiLifecycleService::toVal).toList();
        List<RetrainingResponse> recentRet = allRet.stream().limit(8)
                .map(AiLifecycleService::toRet).toList();
        List<RecertificationResponse> overdueRec = recertRepo.findOverdue(o, today)
                .stream().limit(10).map(AiLifecycleService::toRec).toList();

        return new DashboardResponse(
                valScheduled, valInProgress, valFailOrCond,
                retInProgress, retDeployedQ, retRolledBack,
                recOverdue, recDue90,
                valByStatus, retByStatus, recByStatus,
                recentVal, recentRet, overdueRec);
    }

    // ──────────────────────────────────────────────────── Internals

    private ModelValidation loadVal(Long id) {
        return validationRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Validation " + id + " not found"));
    }

    private RetrainingEvent loadRet(Long id) {
        return retrainingRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Retraining " + id + " not found"));
    }

    private Recertification loadRec(Long id) {
        return recertRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Recertification " + id + " not found"));
    }

    /**
     * F2-256 — the ONE writer of the {@code (decided_at, approver_user_id)} pair.
     *
     * <p>Before this, {@code decidedAt} was stamped from the server's own clock in two switch
     * branches and {@code approverUserId} was written <b>nowhere in the platform</b> — not from the
     * request either, since neither {@code CreateRecertificationRequest} nor
     * {@code TransitionRecertificationRequest} declares the field. So an ISO/IEC 42001
     * model-recertification decision recorded <i>when</i> it was made and never <i>who</i> made it.
     * Measured identically in dev and prod when this was written: 4 of 13 rows carried
     * {@code decided_at}, <b>2 of them with no approver at all</b>, and the 5 rows that do carry one
     * were written by {@code seed_real_data.py}. Every decision taken through the API from now on
     * would have joined that bucket.
     *
     * <p>🔑 <b>The correct artefact is {@code DataclassService#transitionException}</b>, which has
     * the identical private {@code currentUserId()} helper and writes
     * {@code e.setApproverUserId(currentUserId())} beside its own {@code setApprovedAt(now())}. This
     * service has that same helper three lines below, feeding the timeline event's actor — so the
     * mechanism was present, used for the event, and not used for the row.
     *
     * <p><b>Why both branches, including EXPIRED.</b> Whatever the target state, {@code decidedAt}
     * asserts <i>a decision was recorded at this moment</i> and the register has exactly one column
     * for who recorded it. Leaving EXPIRED and REVOKED blank would reproduce the defect on the two
     * states an assurance reader asks about hardest — a certification allowed to lapse, and one
     * withdrawn. The per-state nuance is not lost: {@code timeline(...)} writes an event carrying
     * both the event type and the actor, so the row-level column is the summary of a trail that
     * already distinguishes them.
     *
     * <p><b>Why it refuses rather than storing null.</b> {@code CurrentActor.requireUserId} is
     * F2-236's primitive and its javadoc states the rule: where a service stamps the clock itself it
     * has declared that <i>it</i> is the recorder of the act, so a decision it cannot attribute is
     * not a decision. The route is {@code @PreAuthorize}-guarded, so a principal is always present
     * in production; refusing makes the hazard ABSENT rather than latent (F2-213) instead of
     * silently writing the null this method exists to stop.
     *
     * <p><b>The assignment reading is deliberately not served here.</b> Two live {@code DUE} rows
     * carry an approver with no {@code decided_at}, i.e. the seed used the column as <i>who is due
     * to approve</i>. Nothing in the platform can make that assignment (no request field, no picker),
     * and adding one is a product decision — filed. Once {@code decidedAt} is stamped the column is
     * an attribution, so a decision overwrites any prior assignment, which is F2-236's window rule.
     */
    private static void recordDecision(Recertification c, LocalDateTime now) {
        c.setDecidedAt(now);
        c.setApproverUserId(CurrentActor.requireUserId("a recertification decision"));
    }

    private static void validate(Map<String, Set<String>> allowed, String from, String to, String label) {
        if (!allowed.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException(label + " cannot transition " + from + " → " + to);
        }
    }

    private void timeline(Long modelId, Long valId, Long retId, Long recId,
                          String eventType, String statusAfter,
                          String noteEn, String noteAr) {
        AiLifecycleEvent e = new AiLifecycleEvent();
        e.setOrganisationId(org());
        e.setModelId(modelId);
        e.setValidationId(valId);
        e.setRetrainingId(retId);
        e.setRecertificationId(recId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setActorName(currentActorName());
        e.setStatusAfter(statusAfter);
        e.setNoteEn(noteEn);
        e.setNoteAr(noteAr);
        eventRepo.save(e);
    }

    private void emit(String trigger, String entityType, Long entityId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, null,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    /**
     * F2-257 — the display half of the actor pair, from the same primitive as {@link
     * #currentUserId()}.
     *
     * <p>Deliberately {@code username()} and not {@code requireUsername(...)}: the id half is
     * nullable and comes from the same place, so both halves are populated together or absent
     * together. Refusing here would let a timeline write abort the business operation it exists to
     * record.
     */
    private static String currentActorName() {
        return com.tss.grc.common.security.CurrentActor.username();
    }

    private static Long currentUserId() {
        return CurrentActor.userId();
    }

    // ────────────────────────────────────────────────────── Mappers

    private static ValidationResponse toVal(ModelValidation v) {
        return new ValidationResponse(
                v.getId(), v.getModelId(), v.getReference(), v.getStatus(),
                v.getValidationType(), v.getValidatorUserId(), v.getValidatorOrgLabel(),
                v.getScheduledAt(), v.getStartedAt(), v.getCompletedAt(),
                v.getMetricsJson(), v.getEthicsScoresJson(), v.getFairnessDisparityPct(),
                v.getFindingsEn(), v.getFindingsAr(),
                v.getConditionsEn(), v.getConditionsAr(), v.getLinkedCapaId());
    }

    private static RetrainingResponse toRet(RetrainingEvent r) {
        return new RetrainingResponse(
                r.getId(), r.getModelId(), r.getReference(), r.getStatus(),
                r.getTriggerReason(), r.getRationaleEn(), r.getRationaleAr(),
                r.getFromVersionLabel(), r.getToVersionLabel(),
                r.getApprovedAt(), r.getStartedAt(),
                r.getDeployedAt(), r.getRolledBackAt(),
                r.getPostDeployMetricsJson(),
                r.getRollbackReasonEn(), r.getRollbackReasonAr(),
                r.getOwnerUserId());
    }

    private static RecertificationResponse toRec(Recertification c) {
        return new RecertificationResponse(
                c.getId(), c.getModelId(), c.getReference(), c.getCycleLabel(), c.getStatus(),
                c.getDueDate(), c.getStartedAt(), c.getDecidedAt(),
                c.getNextDueDate(),
                c.getDecisionRationaleEn(), c.getDecisionRationaleAr(),
                c.getConditionsEn(), c.getConditionsAr(),
                c.getApproverUserId(), c.getLinkedValidationId());
    }

    private static EventResponse toEvent(AiLifecycleEvent e) {
        return new EventResponse(
                e.getId(), e.getModelId(), e.getValidationId(), e.getRetrainingId(), e.getRecertificationId(),
                e.getOccurredAt(), e.getEventType(),
                e.getActorUserId(), e.getActorName(), e.getStatusAfter(),
                e.getNoteEn(), e.getNoteAr());
    }
}
